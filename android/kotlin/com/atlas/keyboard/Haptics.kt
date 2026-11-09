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
    /** A changed word: two ticks long at twice the strength, then one tick at tick strength, so it fades where a tick just stops. */
    val wordTimings = longArrayOf(durationMillis * 2, durationMillis)
    val wordAmplitudes = intArrayOf(amplitude * 2, amplitude)

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Normal
    }
}

/** Ticks during swipes, and confirms accepted tap keys and changed words. */
class Haptics(private val context: Context) {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private var preset = HapticPreset.Normal
    private var enabled = true
    private var lastTick = -1L
    private var clickUntil = 0L
    private var travel = 0f
    private var origin: Sample? = null
    private var last: Sample? = null

    fun tap() {
        if (enabled(context)) pulse(HapticPreset.Strong)
    }

    /** Confirms a word picked on the strip or changed by autocorrect. */
    fun wordChanged() {
        if (!enabled(context)) return
        val preset = preset(context)
        val amplitudes = if (vibrator.hasAmplitudeControl()) preset.wordAmplitudes
            else intArrayOf(VibrationEffect.DEFAULT_AMPLITUDE, VibrationEffect.DEFAULT_AMPLITUDE)
        play(VibrationEffect.createWaveform(preset.wordTimings, amplitudes, -1))
    }

    /** Confirms a new page or group in the Sym panel, with the same feel as a changed word. */
    fun pageChanged() = wordChanged()

    /** Confirms a push on the quick settings page. It comes after the change, so turning vibration off is silent. */
    fun settingChanged() = wordChanged()

    /** A finished part of the interactive guide: two ticks, the second one stronger. */
    fun guideChapter() = waveform(longArrayOf(0, 16, 45, 24), intArrayOf(0, 170, 0, 255))

    /** The end of the interactive guide: ticks that get stronger, then one long pulse. */
    fun guideFinale() = waveform(longArrayOf(0, 12, 60, 14, 60, 18, 60, 60), intArrayOf(0, 90, 0, 140, 0, 200, 0, 255))

    private fun waveform(timings: LongArray, amplitudes: IntArray) {
        if (!enabled(context)) return
        val strengths = if (vibrator.hasAmplitudeControl()) amplitudes
            else IntArray(amplitudes.size) { if (amplitudes[it] == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE }
        play(VibrationEffect.createWaveform(timings, strengths, -1))
    }

    fun begin(sample: Sample?) {
        preset = preset(context)
        enabled = enabled(context)
        lastTick = -1
        travel = 0f
        origin = sample
        last = sample
    }

    fun move(samples: List<Sample>) {
        val start = origin ?: return
        if (!enabled) return
        var ticked = false
        for (sample in samples) {
            last?.let { travel += distance(it, sample) }
            last = sample
            if (!Layout.isLetter(Layout.keyAt(sample.x, sample.y))) {
                travel = 0f
                continue
            }
            // The first pulse follows movement, so a resting finger stays quiet.
            val ready = if (lastTick < 0) distance(start, sample) >= .2f
                else travel >= preset.spacing && sample.time - lastTick >= preset.intervalMillis
            if (!ready || ticked) continue
            lastTick = sample.time
            travel = 0f
            ticked = true
            pulse(preset)
        }
    }

    private fun distance(a: Sample, b: Sample) = hypot((b.x - a.x) * 10, (b.y - a.y) * 4)

    /** A swipe tick at full strength when cursor mode starts. */
    fun click() {
        if (!enabled(context) || !vibrator.hasVibrator()) return
        if (Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
        val amplitude = if (vibrator.hasAmplitudeControl()) 255 else VibrationEffect.DEFAULT_AMPLITUDE
        clickUntil = SystemClock.uptimeMillis() + CLICK_MILLIS
        vibrator.vibrate(VibrationEffect.createOneShot(CLICK_MILLIS, amplitude),
            VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK).build())
    }

    fun pulse(preset: HapticPreset = this.preset) {
        val amplitude = if (vibrator.hasAmplitudeControl()) preset.amplitude else VibrationEffect.DEFAULT_AMPLITUDE
        play(VibrationEffect.createOneShot(preset.durationMillis, amplitude))
    }

    private fun play(effect: VibrationEffect) {
        if (!vibrator.hasVibrator()) return
        // A new vibration would cut the click short, so ticks wait until it has played.
        if (SystemClock.uptimeMillis() < clickUntil) return
        if (Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
        // Hardware feedback keeps ticking while another app is in the foreground.
        vibrator.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK).build())
    }

    companion object {
        /** As short as a swipe tick, so it feels the same, only at full strength. */
        private const val CLICK_MILLIS = 20L

        private fun preferences(context: Context) = context.getSharedPreferences("swipe_haptics", Context.MODE_PRIVATE)

        fun preset(context: Context) = HapticPreset.of(preferences(context).getString("haptic_preset_v1", null))

        fun enabled(context: Context) = preferences(context).getBoolean("enabled", true)

        fun select(context: Context, preset: HapticPreset, enabled: Boolean) {
            preferences(context).edit().putString("haptic_preset_v1", preset.id).putBoolean("enabled", enabled).apply()
        }
    }
}
