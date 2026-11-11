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
 * invisible overlay sits above every app window to keep the touchpad with the keyboard. A window above it
 * still takes a touch that starts inside it, such as a notification banner, unless the overlay is of
 * [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY], which only an accessibility service can add.
 */
class TouchpadOverlay(
    private val context: Context,
    private val type: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    private val touchpad: (MotionEvent) -> Boolean,
) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show() {
        if (view != null || type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY && !Settings.canDrawOverlays(context)) return
        val catcher = object : View(context) {
            override fun dispatchTouchEvent(event: MotionEvent) = touchpad(event)
            override fun dispatchGenericMotionEvent(event: MotionEvent) = touchpad(event)
        }
        val params = WindowManager.LayoutParams(1, 1, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            // Below the obscuring limit, so touches on apps under this pixel are never blocked.
            alpha = .5f
            title = TITLE
        }
        try {
            windows.addView(catcher, params)
            view = catcher
        } catch (error: RuntimeException) {
            Log.w("Atlas", "Could not add the touchpad overlay", error)
        }
    }

    fun hide() {
        val current = view ?: return
        view = null
        try {
            windows.removeViewImmediate(current)
        } catch (error: RuntimeException) {
            Log.w("Atlas", "Could not remove the touchpad overlay", error)
        }
    }

    private companion object {
        const val TITLE = "AtlasInputMethodTouchpad"
    }
}
