package com.titankeys.keyboard

/**
 * What the phone's Shortcut keys page has for the Sym or fn key, judged for TitanKeys (#116). Pure, so tests/key-check runs it
 * alone; [PhoneChecks.keyCheck] reads the phone.
 *
 * Each key has a mode (Programmable key, Shortcut settings) and, under Programmable key, a function picked from a list. Read
 * on the owner's phone (2026-11-24), Settings.System `<key>_programmable_key_function`:
 *
 * | Value | Function |
 * |-------|----------|
 * | 0     | None (fn sends KEYCODE_FUNCTION, which TitanKeys treats as Ctrl) |
 * | 1     | Ctrl key |
 * | 2     | Sym key  |
 * | 5     | Tab key  |
 * | 7     | Shift key |
 * | 8     | Alt key  |
 *
 * Other values aren't recorded yet: they count as wrong, named generically.
 */
internal data class KeyCheck(
    /** What's wrong, as the bar and Setup name it ("fn types Sym"), or null when the key is right. */
    val problem: String?,
) {
    val ok get() = problem == null

    companion object {
        const val NONE = 0
        const val CTRL = 1
        const val SYM = 2
        const val TAB = 5
        const val SHIFT = 7
        const val ALT = 8

        /** The paths on the Shortcut keys page, as every screen names them. */
        const val SYM_PATH = "Sym key > Programmable key > Sym key"
        const val FN_PATH = "fn key > Programmable key > Ctrl key"

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** The bar's warning is due at most once a day: never warned, a day or more since [last], or the clock went back. */
        fun warningDue(last: Long?, now: Long) = last == null || now - last >= DAY_MILLIS || now < last

        /** The function's name on the phone's list, or null for a value not recorded yet. */
        fun functionName(function: Int) = when (function) {
            NONE -> "nothing"
            CTRL -> "Ctrl"
            SYM -> "Sym"
            TAB -> "Tab"
            SHIFT -> "Shift"
            ALT -> "Alt"
            else -> null
        }

        /**
         * [sym] true for the Sym key, false for fn. [programmable]: the mode is Programmable key. [function]: the function's value,
         * or null when it can't be read, and then the mode alone decides. Sym is right on Sym key; fn on Ctrl key or None.
         */
        fun of(sym: Boolean, programmable: Boolean, function: Int?): KeyCheck {
            val key = if (sym) "Sym" else "fn"
            if (!programmable) return KeyCheck("$key isn't a Programmable key")
            if (function == null) return KeyCheck(null)
            val right = if (sym) function == SYM else function == CTRL || function == NONE
            if (right) return KeyCheck(null)
            val name = functionName(function)
            return KeyCheck(if (name == null) "$key types another key" else "$key types $name")
        }
    }
}
