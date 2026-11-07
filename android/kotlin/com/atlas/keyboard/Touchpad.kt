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

    /** In Sliding Mode 1 the firmware delivers the touchpad to apps as screen touches, still from its own device. */
    fun accepts(event: MotionEvent) = event.isFromSource(InputDevice.SOURCE_TOUCHPAD) || event.device?.name == DEVICE

    /** Firmware gesture keys accompany swipes; they are not physical key presses. */
    fun isGestureKey(event: KeyEvent) = event.device?.name == DEVICE

    fun samples(event: MotionEvent, alignment: TouchAlignment = Calibration.selected()): List<Sample> {
        if (event.historySize >= Swipe.MAX_POINTS || event.eventTime < 0 || event.eventTime > SystemClock.uptimeMillis())
            return listOf(Sample(Float.NaN, Float.NaN, event.eventTime))
        if (event.actionMasked == MotionEvent.ACTION_DOWN) offset = firmwareOffset(event)
        val x = range(event, MotionEvent.AXIS_X, 1079f)
        val y = range(event, MotionEvent.AXIS_Y, 748.75f)
        val shiftX = event.rawX - event.x
        val shiftY = event.rawY - event.y - offset
        fun sample(rawX: Float, rawY: Float, time: Long): Sample {
            val padX = (rawX - x.first) / x.second
            val padY = (rawY - y.first) / y.second
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

    private fun systemProperty(name: String): String? = try {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
    } catch (error: ReflectiveOperationException) {
        null
    }

    private companion object {
        const val DEVICE = "touchPad"
    }
}
