package com.titankeys.keyboard

import android.view.KeyEvent

/**
 * TitanKeys Debug only: where the touch simulator's key feed (android/debug/recorder/KeyFeed.kt, which only Debug compiles) hands
 * key events it builds as the phone's own keyboard would send them, Shift's meta state and all, to the keyboard's own key
 * handling. The keys of the injector's twin of the keyboard reach Android as a second keyboard, and its Shift took the field's
 * focus on the Titan 2 Elite; these never leave the keyboard. The keyboard sets it only in TitanKeys Debug; in TitanKeys it
 * stays null, and nothing there reads it.
 */
internal object KeyFeedHook {
    @Volatile
    var dispatch: ((KeyEvent) -> Boolean)? = null

    /** Clear only unfinished feed presses, identified by key code and original down time. */
    var cancelHeld: ((Map<Int, Long>) -> Unit)? = null
}
