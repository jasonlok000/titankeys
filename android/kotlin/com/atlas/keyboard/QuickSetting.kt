package com.atlas.keyboard

/**
 * The items on the quick settings page of the Sym panel, each on the letter key that it is named after. A [label] fits one
 * line on its key at any system font size. A [title] is the name that shows at the top of the panel.
 */
enum class QuickSetting(val key: String, val label: String, val title: String) {
    Autocorrect("A", "Auto", "Autocorrect"),
    SwipeTyping("S", "Swipe", "Swipe typing"),
    SuggestionBar("W", "Bar", "Suggestion bar"),
    AutoCapitalise("C", "Caps", "Auto-capitalisation"),
    AutoPunctuation("P", "Punc", "Auto-punctuation"),
    Vibration("V", "Vib", "Vibration"),
    HoldSymbols("H", "Hold", "Hold for symbols"),
    CursorControl("M", "Move", "Cursor control"),
    DoublePressLocks("L", "Lock", "Two pushes lock Shift and Alt"),
    TouchpadOnlyInFields("T", "Pad", "Touchpad only in text fields"),
    OpenSettings("O", "Open", "Open TitanKeys settings");

    companion object {
        /** The item on a letter key, or null if the key is blank. */
        fun on(key: String) = entries.firstOrNull { it.key == key }
    }
}
