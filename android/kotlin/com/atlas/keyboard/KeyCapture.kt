package com.atlas.keyboard

import android.app.Activity
import android.view.KeyEvent
import android.view.Window

/** Reports a letter, Space or Backspace pressed on the physical keyboard while a screen waits for one. */
class KeyCapture(private val activity: Activity, private val pressed: (code: Int) -> Unit) {
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
        BackService.filterKeys()
    }

    fun stop() {
        original?.let { if (activity.window.callback === installed) activity.window.callback = it }
        original = null
        installed = null
        activeToken = null
        waiting = null
        BackService.filterKeys()
    }

    private fun key(event: KeyEvent): Boolean {
        if (touchpad.isGestureKey(event)) return false
        val code = event.keyCode
        val wanted = code == KeyEvent.KEYCODE_SPACE || code == KeyEvent.KEYCODE_DEL || code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z
        if (!wanted) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) pressed(code)
        return true
    }
}
