package com.atlas.keyboard

import android.view.KeyEvent

/**
 * The keys that the keyboard takes from an app that would take them before the keyboard hears them: Smart Launcher's search
 * field, where the launcher typed its own letters, so TitanKeys' fixes, Shift + Backspace and the Sym key never ran there (owner,
 * 2026-11-13). A press is taken whole: its repeats and its release go with it, even once the field has closed, and a release
 * whose press the app had stays the app's.
 *
 * Only the keys that TitanKeys types or handles are taken ([typing]). Every other key goes on to the app and Android: the square
 * Recents key stopped working in Smart Launcher when every key but the system ones was taken (owner, 2026-11-13), and Back,
 * Home, volume and the arrows go on the same way.
 */
class TakenKeys {
    private val down = HashSet<Int>()

    /** Whether a taken key is still down, so its release must still be heard. */
    val holding get() = down.isNotEmpty()

    /** Whether the keyboard takes this press ([pressed]) or release. [taking] is whether it takes new presses now. */
    fun takes(code: Int, pressed: Boolean, taking: Boolean): Boolean {
        if (!pressed) return down.remove(code)
        if (code in down) return true
        if (!taking || !typing(code)) return false
        down += code
        return true
    }

    fun clear() = down.clear()

    companion object {
        /** The keys that TitanKeys types or handles: letters, digits, marks, Space, Enter, Backspace and the modifiers. */
        fun typing(code: Int) = code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z || code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
            code in TYPING

        private val TYPING = setOf(
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_FUNCTION,
            KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_SEMICOLON,
            KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_BACKSLASH, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS,
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_AT, KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_GRAVE,
            KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET,
        )
    }
}
