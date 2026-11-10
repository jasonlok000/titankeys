package com.atlas.keyboard

/**
 * Decides when the Back service's touchpad window stands above the notification shade.
 *
 * The firmware gives a touchpad gesture to the first window from the top whose title contains "InputMethod", unless a
 * window above it would take a screen touch at the gesture's place. The touchpad covers the top 62% of the screen. A pop-up
 * notification makes the shade's window touchable over the top rows, so a swipe that started there went to the notification.
 * The keyboard's own overlay is an app overlay, which sits under the shade. An accessibility overlay sits above it, so the
 * Back service adds one while the keyboard wants the touchpad.
 *
 * When you pull the shade down yourself, the shade gets the touchpad, so the window goes away. The Back service hears the
 * shade's panel appear and go, but a pop-up notification shows the same panel. A pull starts with a finger on the screen and a
 * pop-up does not, so a panel that appears just after a screen touch, or a touch while the panel shows, counts as a pull.
 * The lock screen also gets the touchpad, as it did before.
 *
 * This class holds the decisions, so tests can reach them. The Back service makes the Android calls.
 */
class HighTouchpad {
    /** The shade's panel is on the screen: pulled down, a pop-up notification, or the lock screen. */
    var panelShown = false
        private set

    /** The panel showed because of a touch on the screen, so the shade was pulled down on purpose. */
    var pulled = false
        private set

    private var touchedAt = NEVER

    /** A finger landed on the screen, outside the keyboard's own windows. */
    fun touched(now: Long) {
        touchedAt = now
        if (panelShown) pulled = true
    }

    /** One window change from the shade, with Android's content change types. */
    fun panelChanged(changes: Int, now: Long) {
        when {
            changes and PANE_DISAPPEARED != 0 -> {
                panelShown = false
                pulled = false
            }
            changes and (PANE_APPEARED or PANE_TITLE) != 0 -> {
                if (!panelShown && now - touchedAt in 0..PULL_MILLIS) pulled = true
                panelShown = true
            }
        }
    }

    /** A fresh start, such as the screen going off. */
    fun reset() {
        panelShown = false
        pulled = false
        touchedAt = NEVER
    }

    /** True if the window should be up now. */
    fun up(wanted: Boolean, locked: Boolean) = wanted && !locked && !pulled

    companion object {
        private const val NEVER = Long.MIN_VALUE / 2

        /** How soon after a screen touch the panel must appear to count as pulled down. */
        const val PULL_MILLIS = 1000L

        /** From android.view.accessibility.AccessibilityEvent, so that this class can be tested without Android. */
        const val PANE_TITLE = 0x8
        const val PANE_APPEARED = 0x10
        const val PANE_DISAPPEARED = 0x20
    }
}
