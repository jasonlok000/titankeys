package com.atlas.keyboard

import android.view.KeyEvent

/**
 * Sticky Shift and Alt. One press applies to the next character, a second press within
 * [DOUBLE_PRESS_MILLIS] locks it, and a third press releases it. Holding the key works as usual.
 */
class Modifiers {
    enum class Level { Off, Once, Locked }

    var shift = Level.Off
        private set
    var alt = Level.Off
        private set
    private var lastShift = 0L
    private var lastAlt = 0L
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

    /** Pressing Shift or Alt on the touchpad or the keyboard. */
    fun press(isShift: Boolean, time: Long) {
        val level = if (isShift) shift else alt
        val last = if (isShift) lastShift else lastAlt
        val next = when (level) {
            Level.Off -> Level.Once
            Level.Once -> if (time - last < DOUBLE_PRESS_MILLIS) Level.Locked else Level.Off
            Level.Locked -> Level.Off
        }
        if (isShift) {
            shift = next; alt = Level.Off; lastShift = time
        } else {
            alt = next; shift = Level.Off; lastAlt = time
        }
    }

    fun key(event: KeyEvent) {
        val isShift = event.keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || event.keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (!held.add(event.keyCode)) return
            usedWhileHeld -= event.keyCode
            press(isShift, event.eventTime)
        } else if (event.action == KeyEvent.ACTION_UP) {
            held -= event.keyCode
            // Holding the modifier for a character releases it with the key, like a regular keyboard.
            if (usedWhileHeld.remove(event.keyCode)) {
                val sameModifierHeld = held.any { code ->
                    (code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_SHIFT_RIGHT) == isShift
                }
                if (!sameModifierHeld) {
                    if (isShift && shift == Level.Once) shift = Level.Off
                    if (!isShift && alt == Level.Once) alt = Level.Off
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
    }

    /** A command or shortcut used a physically held modifier, leaving sticky state alone. */
    fun useHeld() {
        usedWhileHeld += held
    }

    fun isHeld(code: Int) = code in held

    fun clear() {
        shift = Level.Off
        alt = Level.Off
        held.clear()
        usedWhileHeld.clear()
    }

    companion object {
        const val DOUBLE_PRESS_MILLIS = 300L

        fun isModifier(code: Int) = code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_SHIFT_RIGHT
            || code == KeyEvent.KEYCODE_ALT_LEFT || code == KeyEvent.KEYCODE_ALT_RIGHT
    }
}
