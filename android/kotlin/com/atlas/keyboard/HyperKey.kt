package com.atlas.keyboard

import android.view.KeyEvent

/**
 * Hyper plus a letter opens that letter's app. Hold Hyper and push the letter, or tap Hyper and push the
 * letter within [TAP_MILLIS]. In a text box only holding works, so a slip while typing cannot eat a letter.
 * Hyper itself never reaches apps, and a letter that Hyper takes never types, even with no app set.
 */
class HyperKey {
    sealed interface Action {
        /** Not a shortcut key: the app or keyboard gets it. */
        data object Pass : Action
        /** Kept from apps, with nothing else to do. */
        data object Take : Action
        /** Hyper is ready for a letter. */
        data object Ready : Action
        /** Kept from apps: open the app for this letter, if it has one. */
        data class Open(val letter: Char) : Action
    }

    private var held = false
    /** The letter was pushed while Hyper was held, so letting go of Hyper does not make it ready again. */
    private var usedWhileHeld = false
    /** This press of Hyper cancelled a tap, so letting go does nothing. */
    private var cancelling = false
    private var tappedUntil = Long.MIN_VALUE
    private val taken = HashSet<Int>()

    fun key(hyper: Int, code: Int, down: Boolean, repeat: Int, time: Long, typing: Boolean): Action {
        if (code == hyper) return hyper(down, repeat, time, typing)
        if (code !in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            if (down) tappedUntil = Long.MIN_VALUE
            return Action.Pass
        }
        if (!down) return if (taken.remove(code)) Action.Take else Action.Pass
        if (repeat > 0) return if (code in taken) Action.Take else Action.Pass
        val tapped = time <= tappedUntil && !typing
        tappedUntil = Long.MIN_VALUE
        if (!held && !tapped) return Action.Pass
        if (held) usedWhileHeld = true
        taken += code
        return Action.Open('A' + (code - KeyEvent.KEYCODE_A))
    }

    private fun hyper(down: Boolean, repeat: Int, time: Long, typing: Boolean): Action {
        if (down) {
            if (repeat > 0 || held) return Action.Take
            held = true
            usedWhileHeld = false
            if (time <= tappedUntil) {
                tappedUntil = Long.MIN_VALUE
                cancelling = true
                return Action.Take
            }
            return Action.Ready
        }
        held = false
        val tap = !usedWhileHeld && !cancelling && !typing
        cancelling = false
        if (tap) tappedUntil = time + TAP_MILLIS
        return Action.Take
    }

    /** Forgets held keys, for when key events stop arriving. */
    fun reset() {
        held = false
        usedWhileHeld = false
        cancelling = false
        tappedUntil = Long.MIN_VALUE
        taken.clear()
    }

    companion object {
        /** Time from letting go of a tapped Hyper to the letter. Vim and leader keys use the same. */
        const val TAP_MILLIS = 1000L
        /** The Titan's Sym key when Shortcut keys does not set it to Programmable key. */
        private const val TITAN_SYM = 342

        /** Why this key cannot be Hyper, or null when it can. */
        fun problem(code: Int): String? = when (code) {
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z, in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
                "This key types text. Choose a different key."
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_TAB ->
                "This key types text. Choose a different key."
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> "Shift types capitals. Choose a different key."
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> "Alt types symbols. Choose a different key."
            KeyEvent.KEYCODE_SYM, TITAN_SYM -> "Sym shows emoji. Choose a different key."
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT ->
                "This key starts shortcuts. Choose a different key."
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH ->
                "This key moves between apps. Choose a different key."
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER ->
                "This key controls the phone. Choose a different key."
            else -> null
        }
    }
}
