package com.atlas.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import kotlin.math.hypot

/**
 * The six feels, one meaning each, from lightest to heaviest. Haptics are only for what the thumb can't feel or see, so a
 * physical key press never buzzes. See docs/haptics.md.
 */
enum class Feel(val priority: Int) {
    /** Moving: a swipe crossing keys, the cursor passing a letter, a mark moving to another key. A texture tick. */
    Moving(0),
    /** A step: a word, a space, a page, a group. A tick. */
    Step(1),
    /** Done: a word saved, a setting switched, steering armed. A click. */
    Done(2),
    /** Locked: Shift or Alt locked, selecting armed, a clip pinned. A double click. */
    Locked(2),
    /** Gone: a delete flick, a word forgotten, the clipboard cleared, the cursor at an end of the text. A heavy click. */
    Gone(3),
    /** No: nothing happened, such as nothing left to delete. Three short pulses, used for nothing else. */
    No(3),
}

/** The groups on the Haptics settings page, in its order. */
enum class HapticGroup { Swipe, Delete, Space, Changes, Bar, Modifiers, Panels, Other }

/**
 * Every action that plays a feel, each with its own switch in Settings > Haptics. [id] is stored, so it never changes.
 * The labels are on the settings page, app/settings/haptics.tsx.
 */
enum class Haptic(val id: String, val group: HapticGroup, val feel: Feel) {
    SwipeStart("swipe_start", HapticGroup.Swipe, Feel.Moving),
    SwipeKey("swipe_key", HapticGroup.Swipe, Feel.Moving),
    /** A light touch on the touchpad typed a key, which a key press would have clicked. */
    TapKey("tap_key", HapticGroup.Swipe, Feel.Step),

    DeleteFlick("delete_flick", HapticGroup.Delete, Feel.Gone),
    DeleteWord("delete_word", HapticGroup.Delete, Feel.Step),
    DeleteNothing("delete_nothing", HapticGroup.Delete, Feel.No),

    Steer("steer", HapticGroup.Space, Feel.Done),
    Select("select", HapticGroup.Space, Feel.Locked),
    /** A double or triple tap on Space picked the word at the cursor. It is selected, so it feels like selecting. */
    WordPicked("word_picked", HapticGroup.Space, Feel.Locked),
    CursorLetter("cursor_letter", HapticGroup.Space, Feel.Moving),
    CursorSpace("cursor_space", HapticGroup.Space, Feel.Step),
    SelectionWord("selection_word", HapticGroup.Space, Feel.Step),
    CursorEnd("cursor_end", HapticGroup.Space, Feel.Gone),

    /** Autocorrect changed a word, or shorthand was spelled out, which goes the same way. */
    Autocorrect("autocorrect", HapticGroup.Changes, Feel.Step),
    HeldSymbol("held_symbol", HapticGroup.Changes, Feel.Done),
    /** A :name: became its emoji, or a snippet's shortcut its text. */
    Expansion("expansion", HapticGroup.Changes, Feel.Step),

    BarWord("bar_word", HapticGroup.Bar, Feel.Step),
    MenuOpen("menu_open", HapticGroup.Bar, Feel.Done),
    MenuMark("menu_mark", HapticGroup.Bar, Feel.Moving),
    /** Save, or Undo after Forget. */
    WordSaved("word_saved", HapticGroup.Bar, Feel.Done),
    WordForgotten("word_forgotten", HapticGroup.Bar, Feel.Gone),
    /** Save went wrong and the word isn't saved. */
    WordNotSaved("word_not_saved", HapticGroup.Bar, Feel.No),
    ClipOffer("clip_offer", HapticGroup.Bar, Feel.Done),
    /** A key on exact typing's bar, or Type clip. */
    ExactKey("exact_key", HapticGroup.Bar, Feel.Done),
    /** A long press locked a chip on exact typing's bar. */
    ExactLock("exact_lock", HapticGroup.Bar, Feel.Locked),

    ModifierLocked("modifier_locked", HapticGroup.Modifiers, Feel.Locked),
    ModifierUnlocked("modifier_unlocked", HapticGroup.Modifiers, Feel.Step),

    PanelMark("panel_mark", HapticGroup.Panels, Feel.Moving),
    PanelPage("panel_page", HapticGroup.Panels, Feel.Step),
    PanelGroup("panel_group", HapticGroup.Panels, Feel.Step),
    QuickSetting("quick_setting", HapticGroup.Panels, Feel.Done),
    ClipPasted("clip_pasted", HapticGroup.Panels, Feel.Done),
    ClipPinned("clip_pinned", HapticGroup.Panels, Feel.Locked),
    ClipUnpinned("clip_unpinned", HapticGroup.Panels, Feel.Step),
    ClipboardCleared("clipboard_cleared", HapticGroup.Panels, Feel.Gone),

    BugRecording("bug_recording", HapticGroup.Other, Feel.Done),
    /** Three ticks rising into a click: see [HapticPalette.celebration]. It ends in a click, so it counts as done. */
    GuideCelebration("guide_celebration", HapticGroup.Other, Feel.Done);

    /** Its switch in the "swipe_haptics" preferences. */
    val key get() = "haptic_$id"

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** How each [Feel] plays. It has no Android parts, so the checks can read it. */
object HapticPalette {
    /** Buzzes and gaps, starting with a buzz, in ms, and each buzz's strength from 1 to 255 where a phone can set it. */
    data class Buzzes(val timings: List<Long>, val strengths: List<Int>) {
        val millis get() = timings.sum()
    }

    /**
     * One of the phone's ready-made effects ([effect] is a VibrationEffect.EFFECT_ id), with [fallback] for a phone that
     * lacks it. The Titan 2 Elite has the five effects and no strength control, so there each feel is its own effect.
     */
    data class Pattern(val effect: Int?, val fallback: Buzzes) {
        /** How long the feel lasts, for the rule that nothing cuts into a playing feel. */
        val millis get() = fallback.millis
    }

    // The VibrationEffect ids, as plain numbers so this needs no Android to run.
    const val EFFECT_CLICK = 0
    const val EFFECT_DOUBLE_CLICK = 1
    const val EFFECT_TICK = 2
    const val EFFECT_HEAVY_CLICK = 5
    const val EFFECT_TEXTURE_TICK = 21

    /** "No": three 12 ms pulses, 50 ms apart. Nothing else feels like it. */
    const val NO_PULSE_MILLIS = 12L
    const val NO_GAP_MILLIS = 50L

    fun of(feel: Feel): Pattern = when (feel) {
        Feel.Moving -> Pattern(EFFECT_TEXTURE_TICK, Buzzes(listOf(10), listOf(80)))
        Feel.Step -> Pattern(EFFECT_TICK, Buzzes(listOf(16), listOf(140)))
        Feel.Done -> Pattern(EFFECT_CLICK, Buzzes(listOf(20), listOf(220)))
        Feel.Locked -> Pattern(EFFECT_DOUBLE_CLICK, Buzzes(listOf(15, 40, 15), listOf(220, 0, 220)))
        Feel.Gone -> Pattern(EFFECT_HEAVY_CLICK, Buzzes(listOf(45), listOf(255)))
        Feel.No -> Pattern(null, Buzzes(listOf(NO_PULSE_MILLIS, NO_GAP_MILLIS, NO_PULSE_MILLIS, NO_GAP_MILLIS, NO_PULSE_MILLIS),
            listOf(255, 0, 255, 0, 255)))
    }

    /** One beat of the guide's celebration: [feel] plays [atMillis] after the start, at [scale] where a phone can compose. */
    data class Beat(val atMillis: Long, val feel: Feel, val scale: Float)

    /**
     * The guide's celebration: three ticks rising into a click. The Titan can't make a tick stronger, so there the rise is only
     * the click at the end. A phone that can compose effects gets ticks that really grow.
     */
    val celebration = listOf(Beat(0, Feel.Step, .4f), Beat(70, Feel.Step, .6f), Beat(140, Feel.Step, .8f), Beat(210, Feel.Done, 1f))
    val celebrationMillis get() = celebration.last().let { it.atMillis + of(it.feel).millis }
}

/**
 * Which haptics play: the master switch, then each haptic's own. [read] gives a stored switch, or the default it is given.
 * Every switch is on until it is turned off. It has no Android parts, so the checks can run it.
 */
class HapticSwitches(private val read: (String, Boolean) -> Boolean) {
    val master get() = read(MASTER, true)

    fun on(haptic: Haptic) = read(haptic.key, true)

    fun allows(haptic: Haptic) = master && on(haptic)

    companion object {
        /** The master switch's key. It is the old Vibration setting's, so turning that off before still counts. */
        const val MASTER = "enabled"
    }
}

/**
 * Keeps fast runs of feels calm. It has no Android parts, so the checks can run it with their own [clock].
 * - Within [WINDOW_MILLIS] of the last feel that played, only the first plays.
 * - Except that a feel of a higher [Feel.priority] still plays, as gone and no beat a step, which beats moving.
 * - A feel never cuts one that is still playing, so even that higher feel waits for the first to finish, or doesn't play.
 */
class HapticGate(private val clock: () -> Long) {
    private var lastAt = Long.MIN_VALUE / 2
    private var lastPriority = -1
    private var busyUntil = Long.MIN_VALUE / 2

    /** Whether [feel], lasting [millis], plays now. If it does, it is the last feel from now on. */
    fun admit(feel: Feel, millis: Long = HapticPalette.of(feel).millis): Boolean {
        val now = clock()
        if (now < busyUntil) return false
        // Moving keeps its own pace, a tick per key crossed, as the owner kept it. It never waits for another moving
        // tick, but it never cuts or blocks a heavier feel either: it plays only outside a heavier feel's window, and
        // leaves the window as it was, so a heavier feel right after it still plays.
        if (feel == Feel.Moving) return now - lastAt >= WINDOW_MILLIS || lastPriority <= feel.priority
        if (now - lastAt < WINDOW_MILLIS && feel.priority <= lastPriority) return false
        lastAt = now
        lastPriority = feel.priority
        busyUntil = now + millis
        return true
    }

    companion object {
        const val WINDOW_MILLIS = 80L
    }
}

/**
 * What the cursor plays as it steps while steering. A space or a line end is a step between words, and every other
 * character is moving. Punctuation gets no feel of its own: it almost always sits next to a space, whose tick already marks
 * the end of the word. At the start or end of the text the cursor can't move, so it plays gone, once each time it gets there.
 */
class CursorSteps {
    private var atEnd = false

    /** A new steer: the next end it reaches plays again. */
    fun reset() {
        atEnd = false
    }

    /** The haptic for a step past [next], the character it crosses, or null for an end of the text. */
    fun step(next: Char?): Haptic? {
        if (next == null) {
            if (atEnd) return null
            atEnd = true
            return Haptic.CursorEnd
        }
        atEnd = false
        return if (next.isWhitespace()) Haptic.CursorSpace else Haptic.CursorLetter
    }
}

/** Plays the keyboard's haptics ([Haptic]), only while its switch, the master switch and Android's touch feedback are on. */
class Haptics(private val context: Context) {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val switches = switches(context)
    private val gate = HapticGate(SystemClock::uptimeMillis)
    private val ticks = SwipeTicks()
    private val main = Handler(Looper.getMainLooper())

    fun play(haptic: Haptic) {
        if (!switches.allows(haptic) || !gate.admit(haptic.feel)) return
        vibrate(effect(HapticPalette.of(haptic.feel)))
    }

    /** A finished part of the interactive guide: three ticks rising into a click. */
    fun celebrate() {
        if (!switches.allows(Haptic.GuideCelebration)) return
        if (!gate.admit(Haptic.GuideCelebration.feel, HapticPalette.celebrationMillis)) return
        val beats = HapticPalette.celebration
        if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            val composition = VibrationEffect.startComposition()
            var at = 0L
            for (beat in beats) {
                val primitive = if (beat.feel == Feel.Done) VibrationEffect.Composition.PRIMITIVE_CLICK else VibrationEffect.Composition.PRIMITIVE_TICK
                composition.addPrimitive(primitive, beat.scale, (beat.atMillis - at).toInt())
                at = beat.atMillis
            }
            vibrate(composition.compose())
            return
        }
        for (beat in beats) main.postDelayed({ vibrate(effect(HapticPalette.of(beat.feel))) }, beat.atMillis)
    }

    fun begin(sample: Sample?) = ticks.begin(sample)

    fun move(samples: List<Sample>) {
        ticks.move(samples)?.let(::play)
    }

    /** The phone's own effect where it has it, and otherwise the timed buzzes. */
    private fun effect(pattern: HapticPalette.Pattern): VibrationEffect {
        val id = pattern.effect
        if (id != null && vibrator.areAllEffectsSupported(id) != Vibrator.VIBRATION_EFFECT_SUPPORT_NO)
            return VibrationEffect.createPredefined(id)
        val buzzes = pattern.fallback
        val timings = LongArray(buzzes.timings.size + 1) { if (it == 0) 0 else buzzes.timings[it - 1] }
        val strengths = IntArray(timings.size) { index ->
            val strength = if (index == 0) 0 else buzzes.strengths[index - 1]
            if (strength == 0) 0 else if (vibrator.hasAmplitudeControl()) strength else VibrationEffect.DEFAULT_AMPLITUDE
        }
        return VibrationEffect.createWaveform(timings, strengths, -1)
    }

    private fun vibrate(effect: VibrationEffect) {
        if (!vibrator.hasVibrator()) return
        if (Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
        // Hardware feedback keeps ticking while another app is in the foreground.
        vibrator.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK).build())
    }

    companion object {
        private fun preferences(context: Context) = context.getSharedPreferences("swipe_haptics", Context.MODE_PRIVATE)

        /** The switches as they are stored now, read again on each use, so a change in Settings counts at once. */
        fun switches(context: Context): HapticSwitches {
            val preferences = preferences(context)
            return HapticSwitches { key, default -> preferences.getBoolean(key, default) }
        }

        /** The master switch: All haptics in Settings, and Vibration on the quick settings page. */
        fun enabled(context: Context) = switches(context).master

        fun enabled(context: Context, on: Boolean) {
            preferences(context).edit().putBoolean(HapticSwitches.MASTER, on).apply()
        }

        /** Each haptic's own switch, by its id. */
        fun toggles(context: Context): Map<String, Boolean> {
            val switches = switches(context)
            return Haptic.entries.associate { it.id to switches.on(it) }
        }

        fun toggle(context: Context, id: String, on: Boolean) {
            val haptic = Haptic.of(id) ?: return
            preferences(context).edit().putBoolean(haptic.key, on).apply()
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

    /**
     * What these new points of the contact play, at most once: [Haptic.SwipeStart] when the contact first goes as far as a
     * swipe, [Haptic.SwipeKey] for each later tick, or null.
     */
    fun move(samples: List<Sample>): Haptic? {
        val start = origin ?: return null
        var ticked: Haptic? = null
        for (sample in samples) {
            last?.let { travel += distance(it, sample) }
            last = sample
            if (!Layout.isLetter(Layout.keyAt(sample.x, sample.y))) {
                travel = 0f
                continue
            }
            // The first tick waits until the contact has gone far enough to be a swipe, so a finger that only brushes the
            // keys, or slides a little while pushing one, stays quiet (#30). A slow start waits no longer: the owner felt the
            // first ticks go missing (Nov 11). The phantom-buzz numbers are in the swipe baseline's phantom-ticks report.
            val first = lastTick < 0
            val ready = if (first) distance(start, sample) >= Contact.SWIPE_KEYS
                else travel >= SPACING_KEYS && sample.time - lastTick >= INTERVAL_MILLIS
            if (!ready || ticked != null) continue
            lastTick = sample.time
            travel = 0f
            ticked = if (first) Haptic.SwipeStart else Haptic.SwipeKey
        }
        return ticked
    }

    private fun distance(a: Sample, b: Sample) = hypot((b.x - a.x) * 10, (b.y - a.y) * 4)

    companion object {
        /** Travel between ticks, in key widths. */
        const val SPACING_KEYS = .7f
        /** The least time between ticks. */
        const val INTERVAL_MILLIS = 40L
    }
}
