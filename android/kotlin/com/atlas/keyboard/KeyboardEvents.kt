package com.atlas.keyboard

import java.util.concurrent.CopyOnWriteArraySet

/** Something that the keyboard just did, in terms that a screen can follow. It holds nothing that needs Android. */
sealed interface KeyboardEvent {
    /** A swipe read as these words, best first. */
    data class Recognised(val words: List<String>) : KeyboardEvent

    /** A word was picked from the strip. */
    data class Chosen(val word: String) : KeyboardEvent

    /** A delete flick stayed down, so it keeps deleting: the word that it deletes first follows as a [DeletedWord]. */
    data object DeleteHoldStarted : KeyboardEvent

    /** A delete flick deleted a word. [held] is true for each word after the first one, while the finger stays on the keys. */
    data class DeletedWord(val held: Boolean) : KeyboardEvent

    /** A light touch on a key, which may also be part of a double tap. */
    data class Tapped(val key: String) : KeyboardEvent

    /** A physical key went down, with Android's key code. */
    data class Pushed(val code: Int) : KeyboardEvent

    /** Shift, Alt, the Sym panel and the Ctrl overlay as they are now. Sent each time any of them may have changed. */
    data class ModifierState(
        val shift: Modifiers.Level,
        val alt: Modifiers.Level,
        val panel: Modifiers.Panel,
        val ctrlOverlay: Boolean,
    ) : KeyboardEvent

    /** A key stayed down, so its letter became this symbol. */
    data class HeldSymbol(val symbol: String) : KeyboardEvent

    /** A finger rests on this key of the Sym panel. */
    data class Resting(val key: String) : KeyboardEvent

    /** A swipe on the open panel, in keys across and rows down. [moved] is true if a different page now shows. */
    data class PanelSwiped(val across: Float, val down: Float, val moved: Boolean) : KeyboardEvent

    /** A different group of emoji opened, by a finger on the bottom row, a key or a touch on the strip of groups. */
    data class GroupPicked(val group: Int) : KeyboardEvent

    /** A key on the quick settings page was used. [on] is the setting as it is now. */
    data class QuickSettingUsed(val item: QuickSetting, val on: Boolean) : KeyboardEvent

    /** Quick taps on the bottom row picked a word: two on Space pick the word at the cursor, three the word in front of it. */
    data class DoubleTapped(val taps: Int) : KeyboardEvent

    /** Two quick taps above Space that both lifted, which pick nothing there. Only in the guide's field. */
    data object TwoTapsAbove : KeyboardEvent

    /** A key went up: only Shift, Alt and letters, and only in the guide's field, so the guide can tell a press that let go from a hold. */
    data class Released(val code: Int) : KeyboardEvent

    /** A key on the Sym panel with nothing on it was pressed. Only in the guide's field. */
    data object EmptyKey : KeyboardEvent

    /** The guide's slide locks [lesson], a [GuideLesson]'s name, so what was tried did nothing. Only in the guide's field. */
    data class Blocked(val lesson: String) : KeyboardEvent

    /** Backspace right after a typo fix put the typed word back. */
    data object Restored : KeyboardEvent

    /** A finger that tapped the bottom row started to steer the cursor. After three taps it selects whole [words]. */
    data class CursorStarted(val words: Boolean) : KeyboardEvent

    /** The finger's path across the keys so far. A screen makes this from [Trace]. */
    data class Moved(val keys: Float, val lifted: Boolean) : KeyboardEvent

    /** The text or selection of the screen's own field changed. The screen that owns the field sends this. */
    data class FieldChanged(val text: String, val start: Int, val end: Int) : KeyboardEvent

    /** Something was copied to the clipboard. The screen that owns the field sends this, as only it may read the clipboard. */
    data class Copied(val text: String) : KeyboardEvent

    /**
     * Autocorrect changed [typed] into [word]. A change of capitals alone is no typo fixed: see [isRealFix].
     */
    data class Fixed(val typed: String, val word: String) : KeyboardEvent {
        val isRealFix get() = !typed.filter(Char::isLetter).equals(word.filter(Char::isLetter), ignoreCase = true)
    }

    /** A word on the strip was held, and its menu opened. */
    data class WordMenu(val word: String) : KeyboardEvent

    /** A button of the word menu was picked, by a touch or a key: Save, Forget or Back, by its name. */
    data class MenuPicked(val action: String) : KeyboardEvent

    /**
     * What each key of the open Sym panel shows, by the name of the key under it in [Layout]: a symbol, an emoji, a setting's
     * label or a short mark for an icon. Keys that show nothing are left out. Sent each time the panel draws itself.
     */
    data class PanelFace(val panel: Modifiers.Panel, val labels: Map<String, String>) : KeyboardEvent
}

/**
 * Tells a screen what the keyboard does, in the keyboard's own process. Like [Trace], it publishes only while a screen
 * listens, so each hook is a cheap check in normal typing and builds no event.
 */
object KeyboardEvents {
    @PublishedApi internal val listeners = CopyOnWriteArraySet<(KeyboardEvent) -> Unit>()

    /** Follows the events until the returned function is called. */
    fun listen(listener: (KeyboardEvent) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    val listening get() = listeners.isNotEmpty()

    /** Builds and sends the event, but only if a screen listens. */
    inline fun publish(event: () -> KeyboardEvent) {
        if (listeners.isEmpty()) return
        val built = event()
        for (listener in listeners) listener(built)
    }
}
