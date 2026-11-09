package com.atlas.keyboard

import android.app.Activity
import android.view.MotionEvent
import android.view.Window
import com.atlas.engine.Calibration

/** Plays swipe vibration from the activity window without needing a text field. */
class TouchpadPreview(private val activity: Activity) {
    private val touchpad = Touchpad(activity.display!!)
    private val haptics = Haptics(activity)
    private val contact = Contact()
    private var original: Window.Callback? = null
    private var installed: Window.Callback? = null
    private var activeToken: Any? = null

    fun start() {
        if (original != null) return
        Calibration.init(activity)
        val callback = activity.window.callback
        original = callback
        val token = Any()
        activeToken = token
        installed = TouchpadWindowCallback(callback) { event ->
            (activeToken === token && touchpad.accepts(event)).also { if (it) motion(event) }
        }
        activity.window.callback = installed
    }

    fun stop() {
        original?.let { if (activity.window.callback === installed) activity.window.callback = it }
        original = null
        installed = null
        activeToken = null
        contact.cancelActive()
    }

    private fun motion(event: MotionEvent) {
        val samples = touchpad.samples(event)
        when (touchpad.action(event)) {
            MotionEvent.ACTION_DOWN -> {
                activity.window.decorView.requestUnbufferedDispatch(event)
                val first = samples.firstOrNull() ?: return
                contact.down(first)
                haptics.begin(first)
            }
            MotionEvent.ACTION_MOVE -> {
                contact.move(samples)
                if (contact.travel != null) haptics.move(samples)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> contact.cancelActive()
        }
    }
}
