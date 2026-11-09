package com.atlas.keyboard

import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import com.atlas.engine.Calibration
import com.atlas.engine.Swipe
import com.atlas.engine.TouchAlignment
import kotlin.math.abs
import kotlin.math.roundToInt

/** A touchpad position from 0 to 1 on each axis, with its event time in milliseconds. */
data class Sample(val x: Float, val y: Float, val time: Long) {
    val valid: Boolean get() = x.isFinite() && y.isFinite() && time >= 0
        && x in -Swipe.MAX_ABS_COORDINATE..Swipe.MAX_ABS_COORDINATE
        && y in -Swipe.MAX_ABS_COORDINATE..Swipe.MAX_ABS_COORDINATE
}

/**
 * Reads the keyboard touchpad from Android motion events. This is the only place that knows how the
 * Titan firmware reports the touchpad; everything after it works in plain touchpad coordinates.
 */
class Touchpad(private val display: Display) {
    private var offset = 0f
    private var landscapeScale = false
    private var onStep = 0
    private var lastChecked = Float.NaN

    /** In Sliding Mode 1 the firmware delivers the touchpad to apps as screen touches, still from its own device. */
    fun accepts(event: MotionEvent) = event.isFromSource(InputDevice.SOURCE_TOUCHPAD) || event.device?.name == DEVICE

    /** The event's action, with a second finger reported as a cancel, until all fingers lift. */
    fun action(event: MotionEvent): Int =
        if (event.actionMasked != MotionEvent.ACTION_UP && twoFingers(event)) MotionEvent.ACTION_CANCEL else event.actionMasked

    /**
     * The touchpad reports one point, even for two fingers, so Android never sees a second pointer. Two fingers
     * touch a larger area than one: one finger reaches a raw touch size of 5 on a long swipe, two fingers reach 6 to 8.
     */
    private fun twoFingers(event: MotionEvent): Boolean {
        if (event.device?.name != DEVICE) return false
        // Android scales the raw size by the average of the two position scales (1.125 on the Titan 2).
        val scale = (range(event, MotionEvent.AXIS_X, X_RANGE).second / RAW_MAX_X
            + range(event, MotionEvent.AXIS_Y, Y_RANGE).second / RAW_MAX_Y) / 2
        // Half a step below the limit allows for rounding.
        val limit = (TWO_FINGER_SIZE - .5f) * scale
        if (event.getTouchMajor(0) >= limit) return true
        return (0 until event.historySize).any { event.getHistoricalTouchMajor(0, it) >= limit }
    }

    /** Firmware gesture keys accompany swipes; they are not physical key presses. */
    fun isGestureKey(event: KeyEvent) = event.device?.name == DEVICE

    fun samples(event: MotionEvent, alignment: TouchAlignment = Calibration.selected()): List<Sample> {
        if (event.historySize >= Swipe.MAX_POINTS || event.eventTime < 0 || event.eventTime > SystemClock.uptimeMillis())
            return listOf(Sample(Float.NaN, Float.NaN, event.eventTime))
        if (event.actionMasked == MotionEvent.ACTION_DOWN) offset = firmwareOffset(event)
        val x = range(event, MotionEvent.AXIS_X, X_RANGE)
        val y = range(event, MotionEvent.AXIS_Y, Y_RANGE)
        val shiftX = event.rawX - event.x
        val shiftY = event.rawY - event.y - offset
        val scales = firmwareScales(event)
        if (scales != null) {
            for (i in 0 until event.historySize) checkScale(event.getHistoricalY(i) + shiftY, scales.first)
            // Moves can carry a resampled position, which is not a whole raw step.
            if (event.actionMasked != MotionEvent.ACTION_MOVE) checkScale(event.rawY - offset, scales.first)
        }
        fun sample(rawX: Float, rawY: Float, time: Long): Sample {
            val padX = (rawX - x.first) / x.second
            val padY = if (scales == null) (rawY - y.first) / y.second
                else rawY / (if (landscapeScale) scales.second else scales.first) / RAW_MAX_Y
            return Sample(alignment.x(padX, padY), alignment.y(padY), time)
        }
        val samples = ArrayList<Sample>(event.historySize + 1)
        for (i in 0 until event.historySize)
            samples += sample(event.getHistoricalX(i) + shiftX, event.getHistoricalY(i) + shiftY, event.getHistoricalEventTime(i))
        if (event.actionMasked != MotionEvent.ACTION_CANCEL) samples += sample(event.rawX, event.rawY - offset, event.eventTime)
        return samples
    }

    private fun range(event: MotionEvent, axis: Int, fallback: Float): Pair<Float, Float> {
        val range = event.device?.let { it.getMotionRange(axis, event.source) ?: it.getMotionRange(axis) }
        return if (range != null && range.min.isFinite() && range.range.isFinite() && range.range > 0)
            range.min to range.range else 0f to fallback
    }

    /**
     * In Sliding Mode 1 the firmware's input reader shifts portrait touchpad positions down by a fixed
     * amount (libinputreader TouchInputMapper::cookPointerData, unchanged from V02.00.04 to V02.00.06).
     */
    private fun firmwareOffset(event: MotionEvent): Float {
        if (Build.DEVICE != "Titan_2" || event.device?.name != DEVICE || display.rotation != Surface.ROTATION_0
            || systemProperty("persist.sys.touchpad_forcescroll") != "yes") return 0f
        return when (display.mode.physicalHeight) {
            1200 -> 240f
            1440 -> 360f
            else -> 0f
        }
    }

    /**
     * After the display returns to portrait, the Titan 2 firmware can keep scaling touchpad height for
     * the landscape display until its input configuration next refreshes, such as when the screen turns
     * off. libinputreader TouchInputMapper::configureInputDevice saves a height from the display size
     * before updating that size, and computeInputTransforms scales portrait touchpad height by it.
     * Returns the portrait and landscape scales from raw touchpad steps to positions.
     */
    private fun firmwareScales(event: MotionEvent): Pair<Float, Float>? {
        if (Build.DEVICE != "Titan_2" || event.device?.name != DEVICE || display.rotation != Surface.ROTATION_0) return null
        fun scale(height: Int) = (height - height / 3 - 50) / RAW_HEIGHT
        return scale(display.mode.physicalHeight) to scale(display.mode.physicalWidth)
    }

    /**
     * The touchpad reports whole raw steps, so with the portrait scale every position lands on a step.
     * A position between steps means the landscape scale is in use. Distinct on-step positions in a row
     * mean it has been refreshed; a landscape-scaled position lands on a step only by chance, and the
     * next one between steps switches back.
     */
    private fun checkScale(position: Float, portrait: Float) {
        val steps = position / portrait
        if (abs(steps - steps.roundToInt()) > .01f) {
            landscapeScale = true
            onStep = 0
        } else if (position != lastChecked && ++onStep >= ON_STEP_TO_CLEAR) landscapeScale = false
        lastChecked = position
    }

    private fun systemProperty(name: String): String? = try {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
    } catch (error: ReflectiveOperationException) {
        null
    }

    private companion object {
        const val DEVICE = "touchPad"
        const val RAW_HEIGHT = 600f
        const val RAW_MAX_Y = RAW_HEIGHT - 1
        const val ON_STEP_TO_CLEAR = 2
        const val RAW_MAX_X = 1079f
        /** The touchpad ranges Android reports, for a device that does not give them. */
        const val X_RANGE = 1079f
        const val Y_RANGE = 748.75f
        const val TWO_FINGER_SIZE = 6
    }
}
