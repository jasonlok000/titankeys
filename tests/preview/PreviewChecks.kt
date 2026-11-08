package com.atlas.keyboard

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.EditText

class PreviewChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            val activity = startActivitySync(Intent().setClassName(targetContext.packageName, "com.vandam.ink.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            waitForPreview(activity)
            swipe("first", .2f)
            tap(860f, 255f)
            waitFor("Settings did not open") { !homeEditor(activity) }
            closeSettings(activity)
            swipe("returned", .6f)
            result.putString("stream", "PASS preview shows a new swipe after returning from Settings")
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
    private fun homeEditor(activity: Activity): Boolean {
        fun editor(view: View): Boolean = when (view) {
            is EditText -> view.hasFocus() && view.hint?.toString() == "Swipe a word on the keyboard"
            is ViewGroup -> (0 until view.childCount).any { editor(view.getChildAt(it)) }
            else -> false
        }
        var ready = false
        runOnMainSync { ready = activity.hasWindowFocus() && editor(activity.window.decorView) }
        return ready
    }
    /** Settings redraws when its data loads, and a tap during that redraw is lost, so try the close button again. */
    private fun closeSettings(activity: Activity) {
        repeat(3) {
            tap(970f, 190f)
            if (runCatching { waitFor("", tries = 20) { homeEditor(activity) } }.isSuccess) return
        }
        error("Closing Settings did not return to the preview editor")
    }
    private fun waitFor(failure: String, tries: Int = 50, condition: () -> Boolean) {
        repeat(tries) {
            if (condition()) { waitForIdleSync(); return }
            SystemClock.sleep(100)
        }
        error(failure)
    }
    private fun waitForPreview(activity: Activity) = waitFor("Preview editor did not become ready") { homeEditor(activity) }
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
        for (py in 750 until 1048) for (px in 92 until 982) {
            if (before.getPixel(px, py) != after.getPixel(px, py)) changed++
        }
        before.recycle(); after.recycle()
        sendStatus(0, Bundle().apply { putString("stream", "$name preview: $changed changed trail pixels\n") })
        check(changed > 100) { "$name preview did not show a new swipe: $changed changed pixels" }
    }
}
