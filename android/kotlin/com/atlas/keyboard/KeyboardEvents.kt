package com.atlas.keyboard

import java.util.concurrent.CopyOnWriteArraySet

/** Something that the keyboard just did, in terms that a screen can follow. It holds nothing that needs Android. */
sealed interface KeyboardEvent {
    /** A swipe read as these words, best first. */
    data class Recognised(val words: List<String>) : KeyboardEvent

    /** A word was picked from the strip. */
    data class Chosen(val word: String) : KeyboardEvent

    /** A flick up on the keys picked the word at this place on the strip. A [Chosen] for the word follows. */
    data class FlickPicked(val index: Int) : KeyboardEvent

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

    /** Two quick taps on the bottom row selected the word at the cursor. */
    data object DoubleTapped : KeyboardEvent

    /** A finger that tapped the bottom row started to steer the cursor. After three taps it selects whole [words]. */
    data class CursorStarted(val words: Boolean) : KeyboardEvent

    /** The finger's path across the keys so far. A screen makes this from [Trace]. */
    data class Moved(val keys: Float, val lifted: Boolean) : KeyboardEvent

    /** The text or selection of the screen's own field changed. The screen that owns the field sends this. */
    data class FieldChanged(val text: String, val start: Int, val end: Int) : KeyboardEvent
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
