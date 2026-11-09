package com.atlas.keyboard

import android.view.KeyEvent

/**
 * Sticky Shift and Alt, and the Sym panel. One press of Shift or Alt applies to the next character, a second press
 * within [DOUBLE_PRESS_MILLIS] locks it, and a third press releases it. Holding the key works as usual.
 * Each press of Sym moves the panel to its next step, and the panel stays open after Sym is let go.
 * Only one of them is on at a time.
 */
class Modifiers {
    enum class Level { Off, Once, Locked }
    enum class Key { Shift, Alt }
    enum class Panel { Closed, Symbols, Emoji }

    var shift = Level.Off
        private set
    var alt = Level.Off
        private set
    var panel = Panel.Closed
        private set
    private val last = LongArray(Key.entries.size)
    /** Each physical modifier that is down, with the time it was pressed. */
    private val held = HashMap<Int, Long>()
    private val usedWhileHeld = HashSet<Int>()
    private var symHeld = false

    val metaState: Int
        get() {
            var meta = 0
            if (shift != Level.Off) meta = meta or KeyEvent.META_SHIFT_ON
            if (shift == Level.Locked) meta = meta or KeyEvent.META_CAPS_LOCK_ON
            if (alt != Level.Off) meta = meta or KeyEvent.META_ALT_ON
            return KeyEvent.normalizeMetaState(meta)
        }

    private fun level(key: Key) = when (key) {
        Key.Shift -> shift
        Key.Alt -> alt
    }

    /** Pressing Shift or Alt on the touchpad or the keyboard. */
    fun press(key: Key, time: Long) {
        val next = when (level(key)) {
            Level.Off -> Level.Once
            Level.Once -> if (time - last[key.ordinal] < DOUBLE_PRESS_MILLIS) Level.Locked else Level.Off
            Level.Locked -> Level.Off
        }
        shift = if (key == Key.Shift) next else Level.Off
        alt = if (key == Key.Alt) next else Level.Off
        panel = Panel.Closed
        last[key.ordinal] = time
    }

    /** Pressing Sym moves the panel on: symbols, then emoji, then closed. */
    private fun pressSym() {
        shift = Level.Off
        alt = Level.Off
        panel = when (panel) {
            Panel.Closed -> Panel.Symbols
            Panel.Symbols -> Panel.Emoji
            Panel.Emoji -> Panel.Closed
        }
    }

    /** Back to normal typing, after an item is picked or the keyboard is hidden. */
    fun closePanel() {
        panel = Panel.Closed
    }

    fun key(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) down(event.keyCode, event.eventTime)
        else if (event.action == KeyEvent.ACTION_UP) up(event.keyCode, event.eventTime)
    }

    fun down(code: Int, time: Long) {
        if (code == KeyEvent.KEYCODE_SYM) {
            if (!symHeld) pressSym()
            symHeld = true
            return
        }
        val key = keyOf(code) ?: return
        if (code in held) return
        held[code] = time
        usedWhileHeld -= code
        press(key, time)
    }

    fun up(code: Int, time: Long) {
        if (code == KeyEvent.KEYCODE_SYM) symHeld = false
        val key = keyOf(code) ?: return
        val pressedAt = held.remove(code) ?: return
        val used = usedWhileHeld.remove(code)
        if (held.keys.any { keyOf(it) == key }) return
        // Shift only applies while it is held, even when nothing used it: a swipe during the hold is
        // dropped, and a hold that starts as a double press must not stay locked. The time, not the use,
        // decides a lock, so a letter that overlaps a quick double press does not undo it. Alt stays on
        // after a long press, so the next key still types the symbol printed on it.
        val heldDown = key == Key.Shift && time - pressedAt >= HOLD_MILLIS
        // Holding the modifier for a character releases it with the key, like a regular keyboard.
        val releases = when (level(key)) {
            Level.Once -> used || heldDown
            Level.Locked -> heldDown
            Level.Off -> false
        }
        if (releases) when (key) {
            Key.Shift -> shift = Level.Off
            Key.Alt -> alt = Level.Off
        }
    }

    /** A character was typed with the current modifiers. */
    fun consume() {
        if (held.isNotEmpty()) {
            useHeld()
            return
        }
        if (shift == Level.Once) shift = Level.Off
        if (alt == Level.Once) alt = Level.Off
    }

    /** A command or shortcut used a physically held modifier, leaving sticky state alone. */
    fun useHeld() {
        usedWhileHeld += held.keys
    }

    fun isHeld(code: Int) = code in held || code == KeyEvent.KEYCODE_SYM && symHeld

    fun clear() {
        shift = Level.Off
        alt = Level.Off
        panel = Panel.Closed
        held.clear()
        usedWhileHeld.clear()
        symHeld = false
    }

    companion object {
        const val DOUBLE_PRESS_MILLIS = 300L
        /** Shift down for at least this long was held, not pressed. It matches an Android long press. */
        const val HOLD_MILLIS = 400L

        private fun keyOf(code: Int) = when (code) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> Key.Shift
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> Key.Alt
            else -> null
        }

        fun isModifier(code: Int) = code == KeyEvent.KEYCODE_SYM || keyOf(code) != null
    }
}
