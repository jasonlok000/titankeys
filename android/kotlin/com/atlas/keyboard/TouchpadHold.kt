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
 * While the status is on, the firmware's letter shortcuts and its space bar to home stop, because they need the status off.
 *
 * This class holds the decisions, so tests can reach them. The keyboard makes the Android calls.
 */
class TouchpadHold {
    /** The keyboard reported the status and nothing has turned it off since. */
    var held = false
        private set

    private var backPassed = NEVER

    /** How long to wait before the next report: not at all, unless a Back has only just gone on to the app. */
    fun delay(now: Long): Long = max(0L, backPassed + BACK_MILLIS - now)

    fun shown() {
        held = true
    }

    fun hidden() {
        held = false
    }

    /** The keyboard ended the status at [now] to let a Back go on to the app. */
    fun backPassed(now: Long) {
        backPassed = now
    }

    /** What to do with one Back event while the status is held. */
    enum class Back {
        /** Do not keep the event. The app gets it. */
        ToApp,
        /** Keep the event. Nothing else happens yet. */
        Keep,
        /** Keep the event, end the status and send a system back. */
        KeepAndHide,
    }

    companion object {
        private const val NEVER = Long.MIN_VALUE / 2

        /**
         * How long the status waits after it ended for Back. The next report puts Android's Back handler in front of the app's
         * again, so the system back should reach the app first. On the phone it left about 10 ms after the hide. If an app is
         * slower, that Back reaches the handler and comes back here as one more [Back.KeepAndHide], so one Back is still one.
         */
        const val BACK_MILLIS = 100L

        /**
         * True if the status should stay on now. It needs no field and the overlay that catches the events. The Back service must be
         * on too, because without it a Back press in an app on Android's newer back handling would be lost.
         */
        fun wanted(settingOn: Boolean, editing: Boolean, overlayUp: Boolean, backServiceOn: Boolean) =
            settingOn && !editing && overlayUp && backServiceOn

        /** True if a hide from Android must leave the status on. With no window up there is no status to keep. */
        fun keepsStatus(wanted: Boolean, windowUp: Boolean) = wanted && windowUp

        /**
         * Android keeps a back handler for a shown keyboard in front of the app's. A push of a real key still reaches the app the
         * usual way, so the keyboard stays out of it. The handler reaches the keyboard as a Back event from no device, and there
         * the push would end, so the status ends too, which removes the handler, and a system back goes on to the app.
         */
        fun backStep(fromKey: Boolean, down: Boolean): Back = when {
            fromKey -> Back.ToApp
            down -> Back.Keep
            else -> Back.KeepAndHide
        }
    }
}
