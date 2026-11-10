package com.atlas.keyboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Sends back the way the system does. Android shows the keyboard with its own back handler in front of
 * the app's, so a back swipe reaches Atlas instead of the app. A back key from a keyboard skips the
 * stage where apps on Android's newer back handling listen for it, so only a system back reaches them.
 * The service cannot read the screen. It receives no events, except the key events that [watchKeys] asks for in the
 * one app that needs them and while the Sym panel is open.
 *
 * It also keeps the touchpad above the notification shade while the keyboard wants it. See [touchpad].
 */
class BackService : AccessibilityService() {
    private var filtering = false
    private var window: TouchpadOverlay? = null
    /** The screen is off or the lock screen shows, where the touchpad goes where it went before. */
    private var locked = false
    private var debuggable = false

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> locked = true
                Intent.ACTION_SCREEN_ON -> locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
                Intent.ACTION_USER_PRESENT -> locked = false
            }
            updateTouchpad()
        }
    }

    override fun onServiceConnected() {
        current = this
        // The Debug build is the same app under its own package name, and Android does not mark it debuggable.
        debuggable = packageName.endsWith(".debug")
        locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        window = TouchpadOverlay(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, HIGH_TITLE) { event ->
            // For tests on the phone: adb logcat -s AtlasTouchpad shows which gestures this window got.
            if (debuggable && event.actionMasked == MotionEvent.ACTION_DOWN) Log.i(LOG_TAG, "touchpad down at the window above the shade")
            touchpad?.invoke(event) == true
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screen, filter, RECEIVER_NOT_EXPORTED)
        filterKeys(watcher != null)
        updateTouchpad()
        // Turned on from the guide's setup: Accessibility opens one page over another, so the guide comes back from here.
        GuideActivity.accessibilityOn(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun stop() {
        if (current === this) current = null
        window?.hide()
        window = null
        try {
            unregisterReceiver(screen)
        } catch (error: IllegalArgumentException) {
            // Already gone, or the service never connected.
        }
    }

    private fun updateTouchpad() {
        val window = window ?: return
        if (touchpad != null && !locked) window.show() else window.hide()
        if (debuggable) Log.i(LOG_TAG, "window above the shade ${if (window.up) "up" else "down"}, locked=$locked")
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
        /** The firmware sends the touchpad only to a window whose title contains "InputMethod". */
        private const val HIGH_TITLE = "AtlasInputMethodTouchpadHigh"
        private const val LOG_TAG = "AtlasTouchpad"

        @Volatile private var current: BackService? = null
        @Volatile private var watcher: ((KeyEvent) -> Boolean)? = null
        @Volatile private var touchpad: ((MotionEvent) -> Boolean)? = null

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

        /**
         * The firmware gives a touchpad gesture to the first window from the top whose title contains "InputMethod", unless a
         * window above it would take a screen touch at the gesture's place. The touchpad covers the top 62% of the screen. A
         * pop-up notification makes the shade's window touchable over the top rows, and the keyboard's own [TouchpadOverlay] is
         * an app overlay under the shade, so a swipe that started there went to the notification. An accessibility overlay
         * sits above the shade, so while [handler] is set, this service keeps a 1×1 window there that sends the touchpad to it.
         * Null takes the window away.
         *
         * The window stays while the shade is pulled down too, so the touchpad keeps typing and the screen scrolls the shade.
         * It goes on the lock screen, where the touchpad goes where it went before. Returns false when the service is off;
         * the keyboard's own overlay still catches the touchpad then, below the shade. The handler runs on the main thread.
         */
        fun touchpad(handler: ((MotionEvent) -> Boolean)?): Boolean {
            if (handler !== touchpad) {
                touchpad = handler
                current?.updateTouchpad()
            }
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
