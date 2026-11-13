package com.atlas.keyboard

/**
 * The items on the quick settings page of the Sym panel, each on a letter of its name that is easy to remember. Q, A and P stay
 * empty, because the touchpad does not read the outer columns well. A [label] fits one line on its key at any system font size.
 * A [title] is the setting's name in Settings, which shows in the strip at the bottom of the panel.
 */
enum class QuickSetting(val key: String, val label: String, val title: String) {
    Autocorrect("F", "Fix", "Fix typos as you type"),
    SwipeTyping("S", "Swipe", "Swipe to type words"),
    SuggestionBar("W", "Words", "Show word suggestions"),
    AutoCapitalise("C", "Caps", "Capital letter to start a sentence"),
    AutoPunctuation("D", "Dot", "Double Space types a full stop"),
    Vibration("V", "Vib", "Haptics"),
    HoldSymbols("H", "Hold", "Hold a letter for its symbol"),
    CursorControl("M", "Move", "Move the cursor from Space"),
    DoublePressLocks("L", "Lock", "Press twice to lock Shift and Alt"),
    /** On is the phone's own Scroll assistant outside text fields, the other way round from the setting that it changes. */
    ScrollAssistant("R", "Scrl", "Scroll assistant outside text fields"),
    OpenSettings("O", "Open", "Open TitanKeys settings"),
    /** X as in cut and copy. It opens the clipboard page, and is no setting. */
    Clipboard("X", "Clip", "Clipboard"),
    ChangeKeyboard("K", "Kbd", "Change keyboard");

    companion object {
        /** The item on a letter key, or null if the key is blank. */
        fun on(key: String) = entries.firstOrNull { it.key == key }
    }
}
