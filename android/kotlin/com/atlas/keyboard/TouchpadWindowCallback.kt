package com.atlas.keyboard

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MotionEvent
import android.view.Window

/** Lets [touchpad] and [keys] take events at the window, before view dispatch drops them. */
class TouchpadWindowCallback(
    private val window: Window.Callback,
    private val keys: (KeyEvent) -> Boolean = { false },
    private val touchpad: (MotionEvent) -> Boolean,
) : Window.Callback by window {
    override fun dispatchKeyEvent(event: KeyEvent) = keys(event) || window.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent) = touchpad(event) || window.dispatchGenericMotionEvent(event)

    override fun dispatchTouchEvent(event: MotionEvent) = touchpad(event) || window.dispatchTouchEvent(event)

    // Kotlin delegation skips Java default methods, so these are passed on by hand.
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>?, menu: Menu?, deviceId: Int) =
        window.onProvideKeyboardShortcuts(data, menu, deviceId)

    override fun onPointerCaptureChanged(hasCapture: Boolean) = window.onPointerCaptureChanged(hasCapture)
}
