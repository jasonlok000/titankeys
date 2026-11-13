package com.atlas.keyboard

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView

/**
 * Checks that the playground, the app's front page, draws a swipe on its keys, and does so again after a trip to Settings,
 * when the front page opens a new playground.
 */
class PreviewChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            var monitor = addMonitor(GuideActivity::class.java.name, null, false)
            startActivitySync(Intent().setClassName(targetContext.packageName, "com.vandam.ink.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            var playground = waitForMonitorWithTimeout(monitor, OPEN_MILLIS) ?: error("The playground did not open")
            removeMonitor(monitor)
            waitFor("The playground's field did not become ready") { ready(playground) }
            swipe("first", .2f)
            monitor = addMonitor(GuideActivity::class.java.name, null, false)
            tapText(playground, "Settings")
            waitFor("Settings did not open") { playground.isFinishing || playground.isDestroyed }
            closeSettings()
            playground = waitForMonitorWithTimeout(monitor, OPEN_MILLIS) ?: error("Closing Settings did not bring back the playground")
            removeMonitor(monitor)
            waitFor("The playground's field did not become ready again") { ready(playground) }
            swipe("returned", .6f)
            result.putString("stream", "PASS the playground shows a new swipe after returning from Settings")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            val image = uiAutomation.takeScreenshot()!!
            java.io.File(targetContext.getExternalFilesDir(null), "preview-failure.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            result.putString("stream", "FAIL " + error.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun ready(activity: Activity): Boolean {
        fun editor(view: View): Boolean = when (view) {
            is EditText -> view.hasFocus() && view.hint?.toString() == HINT
            is ViewGroup -> (0 until view.childCount).any { editor(view.getChildAt(it)) }
            else -> false
        }
        var ready = false
        runOnMainSync { ready = activity.hasWindowFocus() && editor(activity.window.decorView) }
        return ready
    }

    /** Taps the middle of the first view in [activity] that shows [text]. */
    private fun tapText(activity: Activity, text: String) {
        var centre: Pair<Float, Float>? = null
        fun find(view: View): Boolean {
            if (view is TextView && view.text?.toString() == text && view.isShown) {
                val at = IntArray(2)
                view.getLocationOnScreen(at)
                centre = at[0] + view.width / 2f to at[1] + view.height / 2f
                return true
            }
            return view is ViewGroup && (0 until view.childCount).any { find(view.getChildAt(it)) }
        }
        runOnMainSync { find(activity.window.decorView) }
        val (x, y) = centre ?: error("Nothing in the playground shows $text")
        tap(x, y)
    }

    /** Settings redraws when its data loads, and a tap during that redraw is lost, so try the close button again. */
    private fun closeSettings() {
        repeat(3) {
            tap(970f, 190f)
            SystemClock.sleep(1500)
            if (uiAutomation.rootInActiveWindow?.packageName == targetContext.packageName &&
                uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Playground")?.isNotEmpty() == true) return
        }
    }

    private fun waitFor(failure: String, tries: Int = 50, condition: () -> Boolean) {
        repeat(tries) {
            if (condition()) { waitForIdleSync(); return }
            SystemClock.sleep(100)
        }
        error(failure)
    }

    private fun tap(x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        sendPointerSync(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0))
        sendPointerSync(MotionEvent.obtain(time, time + 30, MotionEvent.ACTION_UP, x, y, 0))
        SystemClock.sleep(500)
    }

    private fun swipe(name: String, y: Float) {
        val before = uiAutomation.takeScreenshot()!!
        runOnMainSync {
            val now = SystemClock.uptimeMillis()
            Trace.touch(listOf(Sample(.1f, y, now), Sample(.5f, y, now + 40), Sample(.9f, y, now + 80)), true)
        }
        SystemClock.sleep(400)
        val after = uiAutomation.takeScreenshot()!!
        var changed = 0
        // The drawing of the keys, between the field and the strip.
        for (py in 790 until 1090) for (px in 40 until 1040) {
            if (before.getPixel(px, py) != after.getPixel(px, py)) changed++
        }
        before.recycle(); after.recycle()
        sendStatus(0, Bundle().apply { putString("stream", "$name preview: $changed changed trail pixels\n") })
        check(changed > 100) { "$name preview did not show a new swipe: $changed changed pixels" }
    }

    private companion object {
        const val HINT = "Type here. Or swipe."
        const val OPEN_MILLIS = 15_000L
    }
}
