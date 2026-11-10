package com.atlas.keyboard

import kotlin.math.max

/**
 * Keeps the keyboard's reported status on when no text field is active, for the setting that keeps the touchpad to text fields.
 *
 * The Titan firmware reads the status before it looks at any window. With the status off and Scroll assistant on, it changes a
 * touchpad event into a screen touch at once, and that scrolls the app, so no window can stop the event. With the status on, it
 * sends the event to the keyboard's overlay, which drops it. Android writes the status only when a keyboard shows or hides its
 * window, so the keyboard calls showWindow(false). It then hides the window's dialog in the same tick, the way Android removes
 * a hidden keyboard's surface. No app and no accessibility service sees a keyboard, and nothing takes space on the screen.
 *
 * Android hides a keyboard for many reasons, and each hide used to turn the status off until the next report, which also fell to
 * the hide that Android sends just after a report. A real log had the status off for 420 ms at each close, so a scroll got through.
 * So while the status is wanted, the keyboard only takes the window off the screen when Android asks for a hide, and the status
 * stays on. It stops only for Back, as [backStep] says, or when it is no longer wanted.
 *
 * While the status is on, Android gives Back to the keyboard first, and a Back sent on to the app after that came too late for
 * some apps. So Back takes two pushes: the first lets go of the touchpad and stays on the screen, and the second goes to the app
 * the usual way. The hold comes back once that Back has gone through, or when another app or a text field starts.
 *
 * While the status is on, the firmware's letter shortcuts and its space bar to home stop, because they need the status off.
 *
 * This class holds the decisions, so tests can reach them. The keyboard makes the Android calls.
 */
class TouchpadHold {
    /** The keyboard reported the status and nothing has turned it off since. */
    var held = false
        private set

    /** The first push of Back let go of the touchpad, and the hold waits for the second push to go through. */
    var lettingGo = false
        private set
    /** The package of the app that Back let go in. */
    private var letGoIn: String? = null
    private var backPassed = NEVER

    /** How long to wait before the next report: not at all, unless a Back has only just gone on to the app. */
    fun delay(now: Long): Long = max(0L, backPassed + BACK_MILLIS - now)

    fun shown() {
        held = true
    }

    fun hidden() {
        held = false
    }

    /** The first push of Back in [app] let go of the touchpad. */
    fun letGo(app: String?) {
        lettingGo = true
        letGoIn = app
    }

    /** The second push of Back went on to the app at [now]. The hold comes back once the app has had it. */
    fun backWentThrough(now: Long) {
        if (!lettingGo) return
        lettingGo = false
        backPassed = now
    }

    /** Input started in [app], with a text field if [editing]. A field or another app ends the wait for the second push. */
    fun started(app: String?, editing: Boolean) {
        if (editing || app != letGoIn) lettingGo = false
    }

    /** The screen came on, which is a fresh start. */
    fun screenOn() {
        lettingGo = false
    }

    /** What to do with one Back event while the status is held. */
    enum class Back {
        /** Do not keep the event. The app gets it. */
        ToApp,
        /** Keep the event. Nothing else happens yet. */
        Keep,
        /** Keep the event and let go of the touchpad. The next push of Back goes to the app. */
        KeepAndLetGo,
    }

    companion object {
        private const val NEVER = Long.MIN_VALUE / 2

        /**
         * How long the status waits after the second push of Back went through. The next report puts Android's Back handler in
         * front of the app's again, so the app must have had its Back first.
         */
        const val BACK_MILLIS = 300L

        /** From android.view.KeyCharacterMap and android.view.KeyEvent, so that this class can be tested without Android. */
        private const val NO_DEVICE = -1
        private const val FLAG_VIRTUAL_HARD_KEY = 0x40

        /**
         * True if the status should stay on now. It needs no field and the overlay that catches the events, and not a first push
         * of Back that let go. The Back service must be on too, because it hears the second push of Back go through.
         */
        fun wanted(settingOn: Boolean, editing: Boolean, overlayUp: Boolean, backServiceOn: Boolean, lettingGo: Boolean = false) =
            settingOn && !editing && overlayUp && backServiceOn && !lettingGo

        /** True if a hide from Android must leave the status on. With no window up there is no status to keep. */
        fun keepsStatus(wanted: Boolean, windowUp: Boolean) = wanted && windowUp

        /**
         * True for the Back events that Android's handler for a shown keyboard makes. A system back also has no device, and it comes
         * past the keyboard on its way to the app, so only the flag that the handler alone sets tells them apart.
         */
        fun fromHandler(deviceId: Int, flags: Int) = deviceId == NO_DEVICE && flags and FLAG_VIRTUAL_HARD_KEY != 0

        /**
         * Android keeps a back handler for a shown keyboard in front of the app's. Every other Back event, such as a push of a real
         * key or a system back, goes on to the app the usual way. A push reaches the handler only in an app on the newer back
         * handling. There the push would end, so the status ends, which removes the handler, and the next push goes to the app.
         */
        fun backStep(fromHandler: Boolean, down: Boolean): Back = when {
            !fromHandler -> Back.ToApp
            down -> Back.Keep
            else -> Back.KeepAndLetGo
        }
    }
}
