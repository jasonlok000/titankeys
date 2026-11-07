package com.atlas.keyboard

import android.app.Activity
import android.view.KeyEvent
import android.view.Window

/** Reports a letter or Space pressed on the physical keyboard while a screen waits for one. */
class KeyCapture(private val activity: Activity, private val pressed: (String) -> Unit) {
    companion object {
        /** Set while a screen waits for keys. Atlas sees keys before the app does, so it offers them here first. */
        var waiting: ((KeyEvent) -> Boolean)? = null
    }

    private val touchpad = Touchpad(activity.display!!)
    private var original: Window.Callback? = null
    private var installed: Window.Callback? = null
    private var activeToken: Any? = null

    fun start() {
        if (original != null) return
        val callback = activity.window.callback
        original = callback
        val token = Any()
        activeToken = token
        val keys = { event: KeyEvent -> activeToken === token && key(event) }
        waiting = keys
        installed = TouchpadWindowCallback(callback, keys) { false }
        activity.window.callback = installed
    }

    fun stop() {
        original?.let { if (activity.window.callback === installed) activity.window.callback = it }
        original = null
        installed = null
        activeToken = null
        waiting = null
    }

    private fun key(event: KeyEvent): Boolean {
        if (touchpad.isGestureKey(event)) return false
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_SPACE -> " "
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (event.keyCode - KeyEvent.KEYCODE_A)).toString()
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) pressed(key)
        return true
    }
}
