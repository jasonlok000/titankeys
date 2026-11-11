package com.atlas.keyboard

/**
 * When the Back service's touchpad window stands above the notification shade. See [BackService.touchpad].
 *
 * The lock state is read from Android each time, not followed through broadcasts. The unlock broadcast comes from the lock screen,
 * which is not the system itself, so a receiver for Android's own broadcasts never hears it. A lock followed that way stayed on
 * after the first unlock, and the window never came back, which let pop-up notifications take the touchpad again.
 *
 * This holds the decision, so tests can reach it. The service makes the Android calls.
 */
object HighTouchpad {
    /** True if the window should be up: the keyboard [wanted] it, the screen is on, and the lock screen is not showing. */
    fun up(wanted: Boolean, screenOn: Boolean, lockScreenShows: Boolean) = wanted && screenOn && !lockScreenShows
}
