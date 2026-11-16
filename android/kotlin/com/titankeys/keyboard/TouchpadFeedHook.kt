package com.titankeys.keyboard

import android.view.MotionEvent

/**
 * TitanKeys Debug only: where the touch simulator's feed (android/debug/recorder/TouchFeed.kt, which only Debug compiles) hands
 * touchpad events it builds, the same way the firmware hands real ones to the keyboard's window, so everything from
 * [Touchpad.accepts] on runs as for a finger. The keyboard sets it only in TitanKeys Debug; in TitanKeys it stays null, and nothing
 * there reads it.
 */
internal object TouchpadFeedHook {
    @Volatile
    var dispatch: ((MotionEvent) -> Boolean)? = null
}
