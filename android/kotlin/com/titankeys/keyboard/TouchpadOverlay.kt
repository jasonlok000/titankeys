package com.titankeys.keyboard

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
 * notification is such a window, so the Back service puts a second overlay above it, made with [type] for accessibility
 * overlays. See [BackService.touchpad].
 */
class TouchpadOverlay(
    private val context: Context,
    private val type: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    private val title: String = TITLE,
    private val touchpad: (MotionEvent) -> Boolean,
) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    val up: Boolean get() = view != null

    fun show() {
        if (view != null) return
        view = added()
    }

    /**
     * Puts the window on top of the others of its kind again. Android stacks windows of one kind by when they came, and the
     * firmware gives the touchpad to the topmost window titled "InputMethod": another app's leftover window of the same kind
     * added later would take it (2.1 left some on the owner's phone, 2026-11-16). The new window comes first and the old one goes
     * after, so the touchpad is never without one, and never two are left.
     */
    fun raise() {
        val old = view ?: return
        val fresh = added() ?: return
        view = fresh
        try {
            windows.removeViewImmediate(old)
        } catch (error: RuntimeException) {
            Log.w("TitanKeys", "Could not remove the old touchpad overlay $title", error)
        }
    }

    /** A new 1×1 window that sends the touchpad's events to [touchpad], or null where Android refuses it. */
    private fun added(): View? {
        if (type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY && !Settings.canDrawOverlays(context)) return null
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
            title = this@TouchpadOverlay.title
        }
        return try {
            windows.addView(catcher, params)
            catcher
        } catch (error: RuntimeException) {
            Log.w("TitanKeys", "Could not add the touchpad overlay $title", error)
            null
        }
    }

    fun hide() {
        val current = view ?: return
        view = null
        try {
            windows.removeViewImmediate(current)
        } catch (error: RuntimeException) {
            Log.w("TitanKeys", "Could not remove the touchpad overlay $title", error)
        }
    }

    companion object {
        const val TITLE = "TitanKeysInputMethodTouchpad"
    }
}
