package com.titankeys.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Surface
import kotlin.math.hypot

/**
 * TitanKeys Debug only (tools/debug.sh adds it): the touch simulator's touchpad (tools/dev/touch). It builds the touchpad events a
 * finger would make, with the real touchpad's device and ranges, and hands them to the keyboard's window as the firmware does
 * (TouchpadFeedHook), each at its time. So everything from Touchpad.accepts on runs as for a finger: samples and calibration,
 * contacts, swipes and joining, delete flicks, Space taps, hover and the trail. Only adb can send to it: it needs the DUMP
 * permission that the shell holds.
 *
 *   adb shell am broadcast -n com.titankeys.debug/com.titankeys.keyboard.TouchFeed --es events "D 0 480 360 2;M 9.5 482 358 3;U 19"
 *
 * events: one per frame, separated by ";": "D ms x y size" a finger lands, "M ms x y size" it moves, "U ms" it lifts. x and y are
 * the touchpad's own steps (0 to 1079 across, 0 to 599 down), size its contact size (1 to 255), ms the time from the start.
 * --el delay MS starts that much later, to line up with keys played meanwhile on the keyboard. The answer says what was queued.
 */
class TouchFeed : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.packageName.endsWith(".debug")) return
        val dispatch = TouchpadFeedHook.dispatch ?: return answer("no keyboard: make TitanKeys Debug the keyboard, in a field")
        val device = touchpad(context) ?: return answer("no device called $DEVICE")
        val frames = parse(intent.getStringExtra("events").orEmpty()) ?: return answer("bad events")
        if (frames.isEmpty() || frames.last().millis > MAX_MILLIS) return answer("no events, or longer than ${MAX_MILLIS / 1000} s")
        val xRange = device.getMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_TOUCHPAD)
        val yRange = device.getMotionRange(MotionEvent.AXIS_Y, InputDevice.SOURCE_TOUCHPAD)
        // The real device's positions are its steps scaled by its ranges (Y is 0 to 748.75 for 600 steps on the Titan 2).
        val xScale = (xRange?.range ?: RAW_MAX_X) / RAW_MAX_X
        val yScale = (yRange?.range ?: RAW_MAX_Y) / RAW_MAX_Y
        val diagonal = context.resources.displayMetrics.let { hypot(it.widthPixels.toFloat(), it.heightPixels.toFloat()) }
        // The firmware's own shift, which Touchpad.firmwareOffset takes off again: real events carry it, so these do too.
        val yOffset = firmwareOffset(context)
        val start = SystemClock.uptimeMillis() + intent.getLongExtra("delay", 0L).coerceIn(0L, MAX_MILLIS)
        val main = Handler(Looper.getMainLooper())
        var downTime = 0L
        var last: Frame? = null
        for (frame in frames) {
            main.postAtTime({
                val now = SystemClock.uptimeMillis()
                val action = when (frame.kind) {
                    'D' -> { downTime = now; MotionEvent.ACTION_DOWN }
                    'M' -> MotionEvent.ACTION_MOVE
                    else -> MotionEvent.ACTION_UP
                }
                val at = if (frame.kind == 'U') last ?: frame else frame
                if (frame.kind != 'U') last = frame
                val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
                val coords = MotionEvent.PointerCoords().apply {
                    x = at.x * xScale
                    y = at.y * yScale + yOffset
                    pressure = 1f
                    size = at.size / 255f
                    touchMajor = at.size / 255f * diagonal
                    touchMinor = touchMajor
                }
                val event = MotionEvent.obtain(downTime, now, action, 1, arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f,
                    device.id, 0, InputDevice.SOURCE_TOUCHPAD, 0)
                try {
                    dispatch(event)
                } catch (error: RuntimeException) {
                    Log.w(TAG, "touch feed: the keyboard refused an event", error)
                } finally {
                    event.recycle()
                }
            }, start + frame.millis.toLong())
        }
        answer("queued ${frames.size} frames, ${frames.last().millis} ms, on device ${device.id}")
    }

    private fun answer(text: String) {
        Log.d(TAG, "touch feed: $text")
        resultData = text
    }

    private class Frame(val kind: Char, val millis: Double, val x: Float, val y: Float, val size: Int)

    private fun parse(text: String): List<Frame>? = try {
        text.split(';').filter { it.isNotBlank() }.map { part ->
            val bits = part.trim().split(' ')
            when (bits[0]) {
                "D", "M" -> Frame(bits[0][0], bits[1].toDouble(), bits[2].toFloat().coerceIn(0f, RAW_MAX_X),
                    bits[3].toFloat().coerceIn(0f, RAW_MAX_Y), bits[4].toInt().coerceIn(1, 255))
                "U" -> Frame('U', bits[1].toDouble(), 0f, 0f, 1)
                else -> throw IllegalArgumentException(part)
            }
        }.also { frames -> require(frames.zipWithNext().all { (a, b) -> b.millis >= a.millis }) }
    } catch (error: RuntimeException) {
        null
    }

    /**
     * In Sliding Mode 1 the Titan 2's input reader shifts portrait touchpad positions down by a fixed amount, which
     * Touchpad.firmwareOffset takes off again; the same rule here, so a built event lands where a finger's would.
     */
    private fun firmwareOffset(context: Context): Float {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return 0f
        val forceScroll = try {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
                .invoke(null, "persist.sys.touchpad_forcescroll") as String
        } catch (error: ReflectiveOperationException) {
            null
        }
        if (Build.DEVICE != "Titan_2" || display.rotation != Surface.ROTATION_0 || forceScroll != "yes") return 0f
        return when (display.mode.physicalHeight) {
            1200 -> 240f
            1440 -> 360f
            else -> 0f
        }
    }

    private fun touchpad(context: Context): InputDevice? {
        val input = context.getSystemService(InputManager::class.java)
        return input.inputDeviceIds.asSequence().mapNotNull(input::getInputDevice).firstOrNull { it.name == DEVICE }
    }

    private companion object {
        const val TAG = "TitanKeysDebug"
        const val DEVICE = "touchPad"
        const val RAW_MAX_X = 1079f
        const val RAW_MAX_Y = 599f
        const val MAX_MILLIS = 60_000L
    }
}
