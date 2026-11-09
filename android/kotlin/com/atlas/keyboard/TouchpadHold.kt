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
 * While the status is on, the firmware's letter shortcuts and its space bar to home stop, because they need the status off.
 *
 * This class holds the decisions, so tests can reach them. The keyboard makes the Android calls.
 */
class TouchpadHold {
    /** The keyboard reported the status and nothing has hidden the window since. */
    var held = false
        private set

    private var lastShown = NEVER

    /** How long to wait before the next report: a short wait, and never sooner than [MIN_GAP_MILLIS] after the last one. */
    fun delay(now: Long): Long = max(SETTLE_MILLIS, lastShown + MIN_GAP_MILLIS - now)

    fun shown(now: Long) {
        held = true
        lastShown = now
    }

    fun hidden() {
        held = false
    }

    /** What to do with one Back event while the status is held. */
    enum class Back {
        /** Do not keep the event. The app gets it. */
        ToApp,
        /** Keep the event. Nothing else happens yet. */
        Keep,
        /** Keep the event, hide the window and send a system back. */
        KeepAndHide,
    }

    companion object {
        private const val NEVER = Long.MIN_VALUE / 2

        /** Android often sends several hides together, so this waits until they have stopped. */
        const val SETTLE_MILLIS = 120L

        /** Stops a hide that keeps coming back from turning into a fast loop. */
        const val MIN_GAP_MILLIS = 300L

        /**
         * True if the status should stay on now. It needs no field and the overlay that catches the events. The Back service must be
         * on too, because without it a Back press in an app on Android's newer back handling would be lost.
         */
        fun wanted(settingOn: Boolean, editing: Boolean, overlayUp: Boolean, backServiceOn: Boolean) =
            settingOn && !editing && overlayUp && backServiceOn

        /**
         * Android keeps a back handler for a shown keyboard in front of the app's. A push of a real key still reaches the app the
         * usual way, so the keyboard stays out of it. The handler reaches the keyboard as a Back event from no device, and there
         * the push would end, so the window hides, which removes the handler, and a system back goes on to the app.
         */
        fun backStep(fromKey: Boolean, down: Boolean): Back = when {
            fromKey -> Back.ToApp
            down -> Back.Keep
            else -> Back.KeepAndHide
        }
    }
}
