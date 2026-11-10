package com.atlas.keyboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
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
 * one app that needs them and while the Sym panel is open, and, while the keyboard wants the touchpad, the moments when the
 * notification shade appears and goes. See [HighTouchpad].
 */
class BackService : AccessibilityService() {
    private var filtering = false
    private var watchingShade = false
    private val high = HighTouchpad()
    private var window: TouchpadOverlay? = null
    /** The screen is off or the lock screen shows, which keeps the touchpad as it was before the window above the shade. */
    private var locked = false
    private var debuggable = false

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    locked = true
                    high.reset()
                }
                Intent.ACTION_SCREEN_ON -> locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
                Intent.ACTION_USER_PRESENT -> locked = false
            }
            note("screen ${intent.action?.substringAfterLast('.')} locked=$locked")
            updateTouchpad()
        }
    }

    override fun onServiceConnected() {
        current = this
        // The Debug build is the same app under its own package name, and Android does not mark it debuggable.
        debuggable = packageName.endsWith(".debug")
        locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        window = TouchpadOverlay(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, HIGH_TITLE, ::screenTouched) { event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) note("touchpad down at the window above the shade")
            touchpad?.invoke(event) == true
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screen, filter, RECEIVER_NOT_EXPORTED)
        applyInfo()
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

    /** Only window changes from the shade arrive here, and only while the keyboard wants the touchpad. See [applyInfo]. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val shade = event.packageName?.toString() == SYSTEM_UI
        // DIAGNOSTIC for #28, Debug build only: what this firmware sends. Other apps' events show no text.
        note("event ${AccessibilityEvent.eventTypeToString(event.eventType)} package=${event.packageName} class=${event.className}" +
            " changes=0x${Integer.toHexString(event.contentChangeTypes)} windowChanges=0x${Integer.toHexString(event.windowChanges)}" +
            " window=${event.windowId}" + if (shade) " text=${event.text}" else "")
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !shade) return
        high.panelChanged(event.contentChangeTypes, SystemClock.uptimeMillis())
        updateTouchpad()
    }

    /** Every screen touch outside the window reaches it, without its place. The touchpad's own gestures do not count. */
    private fun screenTouched(event: MotionEvent) {
        note("outside source=0x${Integer.toHexString(event.source)} device=${event.device?.name} x=${event.x} y=${event.y}")
        if (Touchpad.fromTouchpad(event)) return
        high.touched(SystemClock.uptimeMillis())
        note("screen touched")
        updateTouchpad()
    }

    private fun updateTouchpad() {
        val window = window ?: return
        val up = high.up(touchpad != null, locked)
        if (up == window.up) return
        if (up) window.show() else window.hide()
        note("window above the shade ${if (window.up) "up" else "down"}: shown=${high.panelShown} pulled=${high.pulled} locked=$locked")
    }

    private fun touchpadChanged() {
        // Nothing is heard from the shade while the touchpad is not wanted, so what was known about it may be out of date.
        if (touchpad == null) high.reset()
        applyInfo()
        updateTouchpad()
    }

    /** For tests on the phone: adb logcat -s AtlasShade. Debug builds only. */
    private fun note(message: String) {
        if (debuggable) Log.i(SHADE_TAG, message)
    }

    override fun onInterrupt() = Unit

    /** The keyboard asks for this only while it needs it. True keeps the key from the app. */
    override fun onKeyEvent(event: KeyEvent?) = event != null && watcher?.invoke(event) == true

    /**
     * Android sends every key on the phone through a service that asks for them, so this is on only while it is needed.
     * The shade's window changes are heard only while the keyboard wants the touchpad, and from no other app.
     */
    private fun applyInfo() {
        val keys = watcher != null
        val shade = touchpad != null
        if (keys == filtering && shade == watchingShade) return
        val info = serviceInfo ?: return
        info.flags = if (keys) info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            else info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        info.eventTypes = if (!shade) 0 else if (debuggable) DIAGNOSTIC_EVENTS else AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.packageNames = if (shade && !debuggable) arrayOf(SYSTEM_UI) else null
        // DIAGNOSTIC for #28, Debug build only: whether window focus changes arrive without access to the screen's content.
        info.flags = if (shade && debuggable) info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            else info.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS.inv()
        serviceInfo = info
        filtering = keys
        watchingShade = shade
    }

    companion object {
        /** The firmware sends the touchpad only to a window whose title contains "InputMethod". */
        private const val HIGH_TITLE = "AtlasInputMethodTouchpadHigh"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val SHADE_TAG = "AtlasShade"
        private const val DIAGNOSTIC_EVENTS = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED

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
            current?.applyInfo()
            return current != null
        }

        /**
         * Keeps the touchpad with [handler] above the notification shade, with null to stop. See [HighTouchpad]. Returns false
         * when the service is off; the keyboard's own overlay still catches the touchpad then, below the shade.
         * The handler runs on the main thread.
         */
        fun touchpad(handler: ((MotionEvent) -> Boolean)?): Boolean {
            if (handler !== touchpad) {
                touchpad = handler
                current?.touchpadChanged()
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
