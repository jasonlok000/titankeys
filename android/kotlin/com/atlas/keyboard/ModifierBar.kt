package com.atlas.keyboard

/**
 * What the strip shows of the modifiers: which are on, and what they do to a key. [held] names the chips whose key a finger holds
 * down now, which show filled. A key that was pushed and let go shows as an outline.
 *
 * Shift, Alt and Ctrl that you press or hold take the whole bar, in the place of the words, with what the key under a resting
 * finger types, or Ctrl's shortcuts. The one exception is the Shift that comes on by itself at a sentence start: the suggestions
 * stay, in their places, and the strip only marks it with a small capital in its corner. That is [autoCapital], and then
 * [replacesWords] is false, unless a finger rests on a letter, which shows the whole bar as always.
 */
data class ModifierBar(
    val chips: List<String>,
    val hint: String,
    val held: Set<String> = emptySet(),
    val replacesWords: Boolean = true,
    val autoCapital: Boolean = false,
) {
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
            val shift = when (modifiers.shift) {
                Modifiers.Level.Off -> null
                Modifiers.Level.Once -> SHIFT
                Modifiers.Level.Locked -> CAPS
            }
            val chips = listOfNotNull(CTRL.takeIf { modifiers.ctrlHeld }, shift, ALT.takeIf { alt })
            if (chips.isEmpty()) return null
            val held = setOfNotNull(
                CTRL.takeIf { modifiers.ctrlHeld },
                shift?.takeIf { modifiers.holding(Modifiers.Key.Shift) },
                ALT.takeIf { alt && modifiers.holding(Modifiers.Key.Alt) },
            )
            val hint = when {
                modifiers.ctrlHeld -> Modifiers.CTRL_ACTIONS[hover]?.let { "$hover $it" } ?: SHORTCUTS
                !Layout.isLetter(hover) -> ""
                alt -> symbol(hover).orEmpty()
                else -> hover
            }
            // Only Shift is on, and only because a sentence starts here: the words stay, with a small mark.
            val automatic = modifiers.autoShifted && shift == SHIFT && !alt && !modifiers.ctrlHeld && held.isEmpty()
            return ModifierBar(chips, hint, held, replacesWords = !automatic || Layout.isLetter(hover), autoCapital = automatic)
        }
    }
}
