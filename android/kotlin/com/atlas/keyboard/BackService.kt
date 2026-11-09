package com.atlas.keyboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Sends back the way the system does. Android shows the keyboard with its own back handler in front of
 * the app's, so a back swipe reaches Atlas instead of the app. A back key from a keyboard skips the
 * stage where apps on Android's newer back handling listen for it, so only a system back reaches them.
 *
 * With a hyper key set, it also sees keys before apps do, so Hyper and a letter open an app anywhere.
 * It asks for keys only then, because each key waits for Atlas while it does. It cannot read the screen.
 */
class BackService : AccessibilityService() {
    private val hyper = HyperKey()
    private val haptics by lazy { Haptics(this) }

    override fun onServiceConnected() {
        current = this
        filterKeys()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (current === this) current = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (KeyCapture.waiting?.invoke(event) == true) return true
        val code = AppShortcuts.hyperKey(this)
        if (code == AppShortcuts.NONE || event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val action = hyper.key(code, event.keyCode, event.action == KeyEvent.ACTION_DOWN, event.repeatCount, event.eventTime)
        if (action == HyperKey.Action.Pass) return false
        // The keyboard still learns of the press, so the finger on the touchpad does not type.
        AtlasIme.keyTaken(event)
        when (action) {
            HyperKey.Action.Ready -> if (Haptics.enabled(this)) haptics.pulse(Haptics.preset(this))
            is HyperKey.Action.Open -> try {
                if (AppShortcuts.open(this, action.letter)) haptics.tap()
            } catch (error: RuntimeException) {
                Log.w("Atlas", "Could not open the app for ${action.letter}", error)
            }
            else -> Unit
        }
        return true
    }

    private fun filterKeys() {
        val info = serviceInfo ?: return
        val wanted = KeyCapture.waiting != null || AppShortcuts.hyperKey(this) != AppShortcuts.NONE
        val flags = if (wanted) info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            else info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        if (flags == info.flags) return
        hyper.reset()
        info.flags = flags
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile private var current: BackService? = null

        /** Sends a system back, or returns false when the service is off. */
        fun back(): Boolean = current?.performGlobalAction(GLOBAL_ACTION_BACK) == true

        /** Asks for keys only while a hyper key is set or a screen waits for a key. */
        fun filterKeys() {
            current?.filterKeys()
        }

        fun enabled(context: Context): Boolean {
            val component = ComponentName(context, BackService::class.java)
            val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return services.split(':').any { ComponentName.unflattenFromString(it) == component }
        }
    }
}
