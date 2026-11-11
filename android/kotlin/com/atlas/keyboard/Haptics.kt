package com.atlas.keyboard

import android.content.Context
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import kotlin.math.hypot

/** Swipe feels, in the units the original haptics settings used. */
enum class HapticPreset(val id: String, duration: Int, strength: Int, interval: Int, spacing: Int) {
    Normal("normal", 8, 3, 4, 7),
    Strong("strong", 10, 5, 4, 7);

    val durationMillis = duration * 2L
    val amplitude = strength * 255 / 10
    val intervalMillis = maxOf(interval * 10L, durationMillis + 5)
    /** Travel between ticks, in key widths. */
    val spacing = spacing / 10f

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Normal
    }
}

/**
 * The keyboard's feels, one meaning each, so a gesture can be told by touch alone. See docs/haptics.md for the owner's
 * table of which trigger plays which feel.
 */
enum class Feel {
    /** Movement over keys: swipe ticks, the cursor passing a letter. The lightest. */
    Texture,
    /** A step or boundary: the cursor passing a space, a selection growing by a word, a new Sym page. */
    Tick,
    /** Done, accepted: a word picked, a clip pasted, a word saved, steering starts. */
    Confirm,
    /** Something now stays, selected or locked: selecting starts, Shift or Alt locked, a clip pinned. */
    Stays,
    /** Changed into something else: a held letter became its symbol, autocorrect changed a word. Short, then longer. */
    Changed,
    /** Put back: autocorrect undone, a forgotten word brought back. Longer, then short. */
    Restored,
    /** Something removed: a delete flick, a word forgotten or blocked, the clipboard cleared. */
    Removed,
    /** Can't do that: nothing left to delete, a word that can't be saved. A long buzz, used for nothing else. */
    Refused,
}

/** How each [Feel] plays. It has no Android parts, so the checks can read it. */
object HapticPalette {
    /** Buzzes and gaps, starting with a buzz, in ms, and each buzz's strength from 1 to 255 where a phone can set it. */
    data class Buzzes(val timings: List<Long>, val strengths: List<Int>) {
        val millis get() = timings.sum()
    }

    /**
     * One of the phone's ready-made effects ([effect] is a VibrationEffect.EFFECT_ id), with [fallback] for a phone that
     * lacks it, and [rise] (or a fall) where a phone can compose a short then a longer tap. The Titan 2 Elite has the five
     * effects and no strength control, so there the effects and the timing of [fallback] make each feel distinct.
     */
    data class Pattern(val effect: Int?, val fallback: Buzzes, val composed: Composed? = null) {
        /** How long the feel lasts, for the rule that nothing cuts into a playing feel. */
        val millis get() = fallback.millis
    }

    /** Two composition primitives with a gap, for phones that can compose effects. */
    data class Composed(val first: Int, val second: Int, val gapMillis: Int)

    // The VibrationEffect ids, as plain numbers so this needs no Android to run.
    const val EFFECT_CLICK = 0
    const val EFFECT_DOUBLE_CLICK = 1
    const val EFFECT_TICK = 2
    const val EFFECT_HEAVY_CLICK = 5
    const val EFFECT_TEXTURE_TICK = 21
    const val PRIMITIVE_CLICK = 1
    const val PRIMITIVE_TICK = 7

    /** The gap in a pair, and its two buzzes: short then long rises, long then short falls. */
    private const val PAIR_GAP = 45L
    private const val PAIR_SHORT = 15L
    private const val PAIR_LONG = 30L

    fun of(feel: Feel): Pattern = when (feel) {
        Feel.Texture -> Pattern(EFFECT_TEXTURE_TICK, Buzzes(listOf(10), listOf(80)))
        Feel.Tick -> Pattern(EFFECT_TICK, Buzzes(listOf(16), listOf(140)))
        Feel.Confirm -> Pattern(EFFECT_CLICK, Buzzes(listOf(20), listOf(220)))
        Feel.Stays -> Pattern(EFFECT_DOUBLE_CLICK, Buzzes(listOf(15, 40, 15), listOf(220, 0, 220)))
        Feel.Changed -> Pattern(null, Buzzes(listOf(PAIR_SHORT, PAIR_GAP, PAIR_LONG), listOf(110, 0, 255)),
            Composed(PRIMITIVE_TICK, PRIMITIVE_CLICK, PAIR_GAP.toInt()))
        Feel.Restored -> Pattern(null, Buzzes(listOf(PAIR_LONG, PAIR_GAP, PAIR_SHORT), listOf(255, 0, 110)),
            Composed(PRIMITIVE_CLICK, PRIMITIVE_TICK, PAIR_GAP.toInt()))
        Feel.Removed -> Pattern(EFFECT_HEAVY_CLICK, Buzzes(listOf(45), listOf(255)))
        Feel.Refused -> Pattern(null, Buzzes(listOf(80), listOf(255)))
    }

    /** The swipe tick: the lightest feel for Normal, a tick for Strong, as the Titan can't make a tick stronger. */
    fun swipeTick(preset: HapticPreset) = if (preset == HapticPreset.Strong) Feel.Tick else Feel.Texture

    /** Feels that steps and movement play, which may come close together. The others are confirmations. */
    fun isMotion(feel: Feel) = feel == Feel.Texture || feel == Feel.Tick

    /** Two confirmations closer than this play only the first, so a fast run of actions stays calm. */
    const val CONFIRM_SPACING_MILLIS = 80L
}

/** Plays the keyboard's feels ([Feel]), only while the Vibration setting and Android's touch feedback are on. */
class Haptics(private val context: Context) {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private var preset = HapticPreset.Normal
    private var enabled = true
    /** Nothing cuts into a feel that is still playing. */
    private var busyUntil = 0L
    private var lastConfirm = Long.MIN_VALUE / 2
    private val ticks = SwipeTicks()

    fun texture() = play(Feel.Texture)
    fun tick() = play(Feel.Tick)
    fun confirm() = play(Feel.Confirm)
    fun stays() = play(Feel.Stays)
    fun changed() = play(Feel.Changed)
    fun restored() = play(Feel.Restored)
    fun removed() = play(Feel.Removed)
    fun refused() = play(Feel.Refused)

    fun play(feel: Feel) {
        if (!enabled(context)) return
        perform(feel)
    }

    /** A finished part of the interactive guide: two ticks, the second one stronger. */
    fun guideChapter() = waveform(longArrayOf(0, 16, 45, 24), intArrayOf(0, 170, 0, 255))

    /** The end of the interactive guide: ticks that get stronger, then one long pulse. */
    fun guideFinale() = waveform(longArrayOf(0, 12, 60, 14, 60, 18, 60, 60), intArrayOf(0, 90, 0, 140, 0, 200, 0, 255))

    private fun waveform(timings: LongArray, amplitudes: IntArray) {
        if (!enabled(context)) return
        val strengths = if (vibrator.hasAmplitudeControl()) amplitudes
            else IntArray(amplitudes.size) { if (amplitudes[it] == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE }
        vibrate(VibrationEffect.createWaveform(timings, strengths, -1))
    }

    fun begin(sample: Sample?) {
        preset = preset(context)
        enabled = enabled(context)
        ticks.begin(sample)
    }

    fun move(samples: List<Sample>) {
        if (enabled && ticks.move(samples, preset)) perform(HapticPalette.swipeTick(preset))
    }

    private fun perform(feel: Feel) {
        val now = SystemClock.uptimeMillis()
        if (now < busyUntil) return
        if (!HapticPalette.isMotion(feel)) {
            if (now - lastConfirm < HapticPalette.CONFIRM_SPACING_MILLIS) return
            lastConfirm = now
        }
        val pattern = HapticPalette.of(feel)
        if (!vibrate(effect(pattern))) return
        busyUntil = now + pattern.millis
    }

    /** The phone's own effect where it has it, a composed pair where it can, and otherwise the timed buzzes. */
    private fun effect(pattern: HapticPalette.Pattern): VibrationEffect {
        val id = pattern.effect
        if (id != null && vibrator.areAllEffectsSupported(id) != Vibrator.VIBRATION_EFFECT_SUPPORT_NO)
            return VibrationEffect.createPredefined(id)
        val composed = pattern.composed
        if (composed != null && vibrator.areAllPrimitivesSupported(composed.first, composed.second))
            return VibrationEffect.startComposition()
                .addPrimitive(composed.first, .6f)
                .addPrimitive(composed.second, 1f, composed.gapMillis)
                .compose()
        val buzzes = pattern.fallback
        val timings = LongArray(buzzes.timings.size + 1) { if (it == 0) 0 else buzzes.timings[it - 1] }
        val strengths = IntArray(timings.size) { index ->
            val strength = if (index == 0) 0 else buzzes.strengths[index - 1]
            if (strength == 0) 0 else if (vibrator.hasAmplitudeControl()) strength else VibrationEffect.DEFAULT_AMPLITUDE
        }
        return VibrationEffect.createWaveform(timings, strengths, -1)
    }

    private fun vibrate(effect: VibrationEffect): Boolean {
        if (!vibrator.hasVibrator()) return false
        if (Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return false
        // Hardware feedback keeps ticking while another app is in the foreground.
        vibrator.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK).build())
        return true
    }

    companion object {
        private fun preferences(context: Context) = context.getSharedPreferences("swipe_haptics", Context.MODE_PRIVATE)

        fun preset(context: Context) = HapticPreset.of(preferences(context).getString("haptic_preset_v1", null))

        fun enabled(context: Context) = preferences(context).getBoolean("enabled", true)

        fun select(context: Context, preset: HapticPreset, enabled: Boolean) {
            preferences(context).edit().putString("haptic_preset_v1", preset.id).putBoolean("enabled", enabled).apply()
        }
    }
}

/** When a swipe ticks. It has no Android parts, so the touchpad checks can run it. */
class SwipeTicks {
    private var lastTick = -1L
    private var travel = 0f
    private var origin: Sample? = null
    private var last: Sample? = null

    fun begin(sample: Sample?) {
        lastTick = -1
        travel = 0f
        origin = sample
        last = sample
    }

    /** Whether these new points of the contact tick, at most once. */
    fun move(samples: List<Sample>, preset: HapticPreset): Boolean {
        val start = origin ?: return false
        var ticked = false
        for (sample in samples) {
            last?.let { travel += distance(it, sample) }
            last = sample
            if (!Layout.isLetter(Layout.keyAt(sample.x, sample.y))) {
                travel = 0f
                continue
            }
            // The first tick waits until the contact has gone far enough to be a swipe, so a finger that only brushes the
            // keys, or slides a little while pushing one, stays quiet (#30). A finger that sets off slowly, as from resting
            // on a key, has to go further: most phantom buzzes in the owner's logs start that way, and real swipes rarely.
            val ready = if (lastTick < 0) distance(start, sample) >= firstTickKeys(sample.time - start.time)
                else travel >= preset.spacing && sample.time - lastTick >= preset.intervalMillis
            if (!ready || ticked) continue
            lastTick = sample.time
            travel = 0f
            ticked = true
        }
        return ticked
    }

    private fun distance(a: Sample, b: Sample) = hypot((b.x - a.x) * 10, (b.y - a.y) * 4)

    companion object {
        /**
         * How quickly a real swipe gets to a swipe's distance. In raw-log-17, 90% of swipes and flicks that vibrated were
         * there within 144 ms of landing, and three in four phantom buzzes took longer (median 200 ms).
         */
        const val FAST_START_MILLIS = 160L
        /** How far a slow start must go before its first tick: the owner's two keys, which the logs bear out. */
        const val SLOW_START_KEYS = 2f

        /** The distance from landing at which the first tick plays, [millis] after landing. */
        fun firstTickKeys(millis: Long) = if (millis <= FAST_START_MILLIS) Contact.SWIPE_KEYS else SLOW_START_KEYS
    }
}
