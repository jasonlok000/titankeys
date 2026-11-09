package com.atlas.keyboard

import android.app.Activity
import android.view.KeyEvent
import android.view.Window

/**
 * Reports a letter or Space pressed on the physical keyboard while a screen waits for one. With [anyKey]
 * it reports every key except the ones that leave the screen.
 */
class KeyCapture(private val activity: Activity, private val pressed: (code: Int) -> Unit) {
    companion object {
        /** Set while a screen waits for keys. Atlas sees keys before the app does, so it offers them here first. */
        var waiting: ((KeyEvent) -> Boolean)? = null
        private val LEAVING_KEYS = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH)
    }

    private val touchpad = Touchpad(activity.display!!)
    private var original: Window.Callback? = null
    private var installed: Window.Callback? = null
    private var activeToken: Any? = null
    var anyKey = false

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
        val wanted = if (anyKey) code !in LEAVING_KEYS else code == KeyEvent.KEYCODE_SPACE || code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z
        if (!wanted) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) pressed(code)
        return true
    }
}
