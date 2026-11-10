package com.atlas.keyboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Sends back the way the system does. Android shows the keyboard with its own back handler in front of
 * the app's, so a back swipe reaches Atlas instead of the app. A back key from a keyboard skips the
 * stage where apps on Android's newer back handling listen for it, so only a system back reaches them.
 * The service cannot read the screen. It receives no events, except the key events that [watchKeys] asks for in the
 * one app that needs them and while the Sym panel is open.
 */
class BackService : AccessibilityService() {
    private var filtering = false

    override fun onServiceConnected() {
        current = this
        filterKeys(watcher != null)
        // Turned on from the guide's setup: Accessibility opens one page over another, so the guide comes back from here.
        GuideActivity.accessibilityOn(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (current === this) current = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** The keyboard asks for this only while it needs it. True keeps the key from the app. */
    override fun onKeyEvent(event: KeyEvent?) = event != null && watcher?.invoke(event) == true

    /** Android sends every key on the phone through a service that asks for them, so this is on only while it is needed. */
    private fun filterKeys(on: Boolean) {
        if (on == filtering) return
        val info = serviceInfo ?: return
        info.flags = if (on) info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            else info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        serviceInfo = info
        filtering = on
    }

    companion object {
        @Volatile private var current: BackService? = null
        @Volatile private var watcher: ((KeyEvent) -> Boolean)? = null

        /** Sends a system back, or returns false when the service is off. */
        fun back(): Boolean = current?.performGlobalAction(GLOBAL_ACTION_BACK) == true

        /**
         * Starts or stops hearing key events before apps do, with null to stop. Returns false when the service is off.
         * The watcher runs on the main thread, and every key waits for its answer, so it must not do slow work.
         */
        fun watchKeys(watch: ((KeyEvent) -> Boolean)?): Boolean {
            watcher = watch
            current?.filterKeys(watch != null)
            return current != null
        }

        fun enabled(context: Context): Boolean {
            val component = ComponentName(context, BackService::class.java)
            val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return services.split(':').any { ComponentName.unflattenFromString(it) == component }
        }
    }
}
