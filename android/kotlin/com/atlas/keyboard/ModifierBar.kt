package com.atlas.keyboard

/** What the bar in the place of the suggestion strip shows: which modifiers are on, and what they do to a key. */
data class ModifierBar(val chips: List<String>, val hint: String) {
    companion object {
        const val CTRL = "Ctrl"
        const val SHIFT = "Shift"
        const val CAPS = "Caps"
        const val ALT = "Alt"
        val ALL = listOf(CTRL, SHIFT, CAPS, ALT)

        /** What Ctrl's shortcuts do, in few enough words for one line. */
        val SHORTCUTS = Modifiers.CTRL_ACTIONS.entries.joinToString("  ") { (key, action) -> "$key ${action.removePrefix("Select ").lowercase()}" }

        /**
         * The bar for the modifiers as they are now, or null if none is on. A Shift or Alt that a finger holds down is left out,
         * so typing a capital with Shift held does not replace the words for a moment.
         *
         * [hover] is the key under a resting finger, or an empty string. The touchpad reports nothing while a physical key other
         * than Shift is held, so with Ctrl there is normally no such key, and the bar lists the shortcuts.
         * [symbol] gives what Alt types on a letter key.
         */
        fun of(modifiers: Modifiers, hover: String, symbol: (String) -> String?): ModifierBar? {
            val shift = modifiers.sticky(Modifiers.Key.Shift)
            val alt = modifiers.sticky(Modifiers.Key.Alt)
            val chips = listOfNotNull(
                CTRL.takeIf { modifiers.ctrlHeld },
                if (!shift) null else if (modifiers.shift == Modifiers.Level.Locked) CAPS else SHIFT,
                ALT.takeIf { alt },
            )
            if (chips.isEmpty()) return null
            val hint = when {
                modifiers.ctrlHeld -> Modifiers.CTRL_ACTIONS[hover]?.let { "$hover $it" } ?: SHORTCUTS
                !Layout.isLetter(hover) -> ""
                alt -> symbol(hover).orEmpty()
                else -> hover
            }
            return ModifierBar(chips, hint)
        }
    }
}
