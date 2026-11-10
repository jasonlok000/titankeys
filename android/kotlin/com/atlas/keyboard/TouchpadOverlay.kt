package com.atlas.keyboard

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * The firmware gives touchpad events to the topmost window whose title contains "InputMethod".
 * An app's text selection popups sit above the keyboard's own window and would take them, so this
 * invisible overlay sits above every app window to keep the touchpad with the keyboard.
 *
 * With the setting that keeps the touchpad to text fields, it also stays up when no field is active. The firmware then
 * sends touchpad events here only because [TouchpadHold] keeps the keyboard's reported status on, and they stop here.
 *
 * The firmware also gives a gesture to any window above this one that a screen touch at the same place would hit. A pop-up
 * notification is such a window, so the Back service puts a second overlay above it. See [HighTouchpad]. That one is made
 * with [type] for accessibility overlays, and it also hears [outside] whenever the screen is touched.
 */
class TouchpadOverlay(
    private val context: Context,
    private val type: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    private val title: String = TITLE,
    private val outside: ((MotionEvent) -> Unit)? = null,
    private val touchpad: (MotionEvent) -> Boolean,
) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    val up: Boolean get() = view != null

    fun show() {
        if (view != null) return
        if (type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY && !Settings.canDrawOverlays(context)) return
        val catcher = object : View(context) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    outside?.invoke(event)
                    return false
                }
                return touchpad(event)
            }
            override fun dispatchGenericMotionEvent(event: MotionEvent) = touchpad(event)
        }
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (outside != null) flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        val params = WindowManager.LayoutParams(1, 1, type, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            // Below the obscuring limit, so touches on apps under this pixel are never blocked.
            alpha = .5f
            title = this@TouchpadOverlay.title
        }
        try {
            windows.addView(catcher, params)
            view = catcher
        } catch (error: RuntimeException) {
            Log.w("Atlas", "Could not add the touchpad overlay $title", error)
        }
    }

    fun hide() {
        val current = view ?: return
        view = null
        try {
            windows.removeViewImmediate(current)
        } catch (error: RuntimeException) {
            Log.w("Atlas", "Could not remove the touchpad overlay $title", error)
        }
    }

    companion object {
        const val TITLE = "AtlasInputMethodTouchpad"
    }
}
