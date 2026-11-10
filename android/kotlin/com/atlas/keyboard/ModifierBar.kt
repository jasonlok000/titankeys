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
         * The bar for the modifiers as they are now, or null if none is on. A modifier shows from the moment that it goes down,
         * and it stays while a finger holds it.
         *
         * [hover] is the key under a resting finger, or an empty string. The touchpad reports nothing while a physical key other
         * than Shift is held, so with Ctrl there is normally no such key, and the bar lists the shortcuts.
         * [symbol] gives what Alt types on a letter key.
         */
        fun of(modifiers: Modifiers, hover: String, symbol: (String) -> String?): ModifierBar? {
            val alt = modifiers.alt != Modifiers.Level.Off
            val chips = listOfNotNull(
                CTRL.takeIf { modifiers.ctrlHeld },
                when (modifiers.shift) {
                    Modifiers.Level.Off -> null
                    Modifiers.Level.Once -> SHIFT
                    Modifiers.Level.Locked -> CAPS
                },
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
