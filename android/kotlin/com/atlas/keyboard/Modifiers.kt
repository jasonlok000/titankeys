package com.atlas.keyboard

import android.view.KeyEvent

/**
 * Sticky Shift, Alt and Sym. One press applies to the next character, a second press within
 * [DOUBLE_PRESS_MILLIS] locks it, and a third press releases it. Holding the key works as usual.
 * Only one of them is on at a time. Sym types nothing itself: it opens the emoji on the strip.
 */
class Modifiers {
    enum class Level { Off, Once, Locked }
    enum class Key { Shift, Alt, Sym }

    var shift = Level.Off
        private set
    var alt = Level.Off
        private set
    var sym = Level.Off
        private set
    private val last = LongArray(Key.entries.size)
    private val held = HashSet<Int>()
    private val usedWhileHeld = HashSet<Int>()

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
        Key.Sym -> sym
    }

    /** Pressing Shift, Alt or Sym on the touchpad or the keyboard. */
    fun press(key: Key, time: Long) {
        val next = when (level(key)) {
            Level.Off -> Level.Once
            Level.Once -> if (time - last[key.ordinal] < DOUBLE_PRESS_MILLIS) Level.Locked else Level.Off
            Level.Locked -> Level.Off
        }
        shift = if (key == Key.Shift) next else Level.Off
        alt = if (key == Key.Alt) next else Level.Off
        sym = if (key == Key.Sym) next else Level.Off
        last[key.ordinal] = time
    }

    fun key(event: KeyEvent) {
        val key = keyOf(event.keyCode) ?: return
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (!held.add(event.keyCode)) return
            usedWhileHeld -= event.keyCode
            press(key, event.eventTime)
        } else if (event.action == KeyEvent.ACTION_UP) {
            held -= event.keyCode
            // Holding the modifier for a character releases it with the key, like a regular keyboard.
            if (usedWhileHeld.remove(event.keyCode) && held.none { keyOf(it) == key } && level(key) == Level.Once) {
                when (key) {
                    Key.Shift -> shift = Level.Off
                    Key.Alt -> alt = Level.Off
                    Key.Sym -> sym = Level.Off
                }
            }
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
        if (sym == Level.Once) sym = Level.Off
    }

    /** A command or shortcut used a physically held modifier, leaving sticky state alone. */
    fun useHeld() {
        usedWhileHeld += held
    }

    fun isHeld(code: Int) = code in held

    /** A held key did a different job, such as a long hold on Sym. Its key turns off and it stops counting as held. */
    fun cancel(code: Int) {
        when (keyOf(code) ?: return) {
            Key.Shift -> shift = Level.Off
            Key.Alt -> alt = Level.Off
            Key.Sym -> sym = Level.Off
        }
        held -= code
        usedWhileHeld -= code
    }

    fun clear() {
        shift = Level.Off
        alt = Level.Off
        sym = Level.Off
        held.clear()
        usedWhileHeld.clear()
    }

    companion object {
        const val DOUBLE_PRESS_MILLIS = 300L

        private fun keyOf(code: Int) = when (code) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> Key.Shift
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> Key.Alt
            KeyEvent.KEYCODE_SYM -> Key.Sym
            else -> null
        }

        fun isModifier(code: Int) = keyOf(code) != null
    }
}
