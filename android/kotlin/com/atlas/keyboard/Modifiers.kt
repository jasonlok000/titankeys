package com.atlas.keyboard

import android.view.KeyEvent

/**
 * Sticky Shift and Alt, and the Sym panel. One press of Shift or Alt applies to the next character, and a second press
 * turns it off. With [doublePressLocks], a second press within [DOUBLE_PRESS_MILLIS] locks it instead, and a third press
 * releases it. Holding the key works as usual.
 * Each press of Sym moves the panel to its next step, and the panel stays open after Sym is let go.
 * Only one of them is on at a time.
 *
 * Ctrl is separate from those: it has no sticky state and applies only while it is held. Once it has been held for
 * [CTRL_OVERLAY_MILLIS], the Ctrl overlay shows until the last Ctrl key is let go.
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
    /** Each key that counts as Ctrl and is down. Key Mapper's Ctrl and fn together are still one Ctrl. */
    private val ctrlKeys = HashSet<Int>()

    /** fn counts as a Ctrl key, for the Use fn as Ctrl setting. */
    var fnIsCtrl = false

    /** A quick second press locks Shift or Alt, for the setting of the same name. */
    var doublePressLocks = false

    /** Ctrl has been held long enough to show what its shortcuts do. */
    var ctrlOverlay = false
        private set

    /** When the Ctrl overlay shows if Ctrl stays down, or null if it is not waiting to show. */
    var ctrlOverlayAt: Long? = null
        private set

    val ctrlHeld get() = ctrlKeys.isNotEmpty()

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
            Level.Once -> if (doublePressLocks && time - last[key.ordinal] < DOUBLE_PRESS_MILLIS) Level.Locked else Level.Off
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
        closeCtrlOverlay()
    }

    private fun closeCtrlOverlay() {
        ctrlOverlay = false
        ctrlOverlayAt = null
    }

    /** A key pushed before the overlay shows is a quick shortcut, so the overlay waits again and does not flash. */
    fun keyWithCtrl(time: Long) {
        if (ctrlOverlayAt != null) ctrlOverlayAt = time + CTRL_OVERLAY_MILLIS
    }

    /** The overlay's timer has run. It shows if the time has come and Ctrl is still down. */
    fun ctrlWaitOver(now: Long) {
        val due = ctrlOverlayAt ?: return
        if (now < due) return
        ctrlOverlayAt = null
        ctrlOverlay = true
    }

    fun isCtrl(code: Int) = code == KeyEvent.KEYCODE_CTRL_LEFT || code == KeyEvent.KEYCODE_CTRL_RIGHT || fnIsCtrl && code == FN

    fun key(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) down(event.keyCode, event.eventTime)
        else if (event.action == KeyEvent.ACTION_UP) up(event.keyCode, event.eventTime)
    }

    fun down(code: Int, time: Long) {
        if (isCtrl(code)) {
            if (ctrlKeys.isEmpty()) ctrlOverlayAt = time + CTRL_OVERLAY_MILLIS
            ctrlKeys += code
            return
        }
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
        if (isCtrl(code)) {
            if (ctrlKeys.remove(code) && ctrlKeys.isEmpty()) closeCtrlOverlay()
            return
        }
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

    /** An app can restart its field after a shortcut. With [keepCtrl], Ctrl stays down through that so that another can follow. */
    fun clear(keepCtrl: Boolean = false) {
        shift = Level.Off
        alt = Level.Off
        panel = Panel.Closed
        held.clear()
        usedWhileHeld.clear()
        symHeld = false
        if (keepCtrl) return
        ctrlKeys.clear()
        closeCtrlOverlay()
    }

    /**
     * A field has started. Ctrl stays down through a restart only when the field can still be typed in. The keyboard
     * does not follow Ctrl in a field that can't, so it would miss the release and leave Ctrl on for every later key.
     */
    fun fieldStarted(restarting: Boolean, editing: Boolean) = clear(keepCtrl = restarting && editing)

    companion object {
        const val DOUBLE_PRESS_MILLIS = 300L
        /** Shift down for at least this long was held, not pressed. It matches an Android long press. */
        const val HOLD_MILLIS = 400L
        /** How long Ctrl stays down before the overlay shows, so a quick shortcut does not flash it. */
        const val CTRL_OVERLAY_MILLIS = 200L
        /** The key code that the Use fn as Ctrl setting listens for. */
        const val FN = KeyEvent.KEYCODE_FUNCTION

        /** What Ctrl does with each letter that the keyboard treats as a field shortcut. */
        val CTRL_ACTIONS = mapOf("A" to "Select all", "C" to "Copy", "X" to "Cut", "V" to "Paste", "Z" to "Undo", "Y" to "Redo")

        /** The meta state of a key pushed while Ctrl is held. Ctrl takes the place of fn, so the key acts as a held shortcut does. */
        fun withCtrl(meta: Int) = meta and KeyEvent.META_FUNCTION_ON.inv() or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON

        private fun keyOf(code: Int) = when (code) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> Key.Shift
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> Key.Alt
            else -> null
        }

        fun isModifier(code: Int) = code == KeyEvent.KEYCODE_SYM || keyOf(code) != null
    }
}
