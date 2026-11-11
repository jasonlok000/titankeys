package com.atlas.keyboard

import android.view.KeyEvent

/**
 * Sticky Shift and Alt, and the Sym panel. One press of Shift or Alt applies to the next character. A second press within
 * [DOUBLE_PRESS_MILLIS] leaves the key on for exactly that next character, or with [doublePressLocks] locks it until a
 * third press. A slower second press turns the key off. A key held for a character ends with the release, and a push that typed
 * nothing stays on for the next key, however long it lasted. Shift and Alt follow the same rules.
 * Each press of Sym moves the panel to its next step, and the panel stays open after Sym is let go.
 * Only one of them is on at a time. While the panel is open, Alt opens the quick settings page and goes back from it.
 *
 * Ctrl is separate from those: it has no sticky state and applies only while it is held.
 */
class Modifiers {
    enum class Level { Off, Once, Locked }
    enum class Key { Shift, Alt }
    enum class Panel { Closed, Symbols, Emoji, Settings, Clipboard }
    /** What happens to a push of Shift or Alt in an app that latches them by itself. */
    enum class AppPush { Pass, Hide, HideAndClear }

    var shift = Level.Off
        private set
    var alt = Level.Off
        private set
    var panel = Panel.Closed
        private set
    /** The page that Alt goes back to from the quick settings page. */
    private var beforeSettings = Panel.Symbols
    private val last = LongArray(Key.entries.size)
    /** Each physical modifier that is down, with the time it was pressed. */
    private val held = HashMap<Int, Long>()
    private val usedWhileHeld = HashSet<Int>()
    private var symHeld = false
    /** Each key that counts as Ctrl and is down. Key Mapper's Ctrl and fn together are still one Ctrl. */
    private val ctrlKeys = HashSet<Int>()
    /** The Shift and Alt keys whose current press the app must not see. */
    private val hiddenFromApp = HashSet<Int>()

    /** fn counts as a Ctrl key, for the Use fn as Ctrl setting. */
    var fnIsCtrl = false

    /** A quick second press locks Shift or Alt, for the setting of the same name. */
    var doublePressLocks = false

    /** A key that counts as Ctrl is down. The modifier bar shows this at once. */
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

    /** A finger holds the physical key down now, as opposed to a push that has ended and still applies. */
    fun holding(key: Key) = held.keys.any { keyOf(it) == key }

    /** Pressing Shift or Alt on the touchpad or the keyboard. With the panel open, Alt is the quick settings key. */
    fun press(key: Key, time: Long) {
        if (key == Key.Alt && panel != Panel.Closed) {
            toggleSettings()
            return
        }
        val level = level(key)
        val quick = time - last[key.ordinal] < DOUBLE_PRESS_MILLIS
        val next = when (level) {
            Level.Off -> Level.Once
            Level.Once -> if (!quick) Level.Off else if (doublePressLocks) Level.Locked else Level.Once
            Level.Locked -> Level.Off
        }
        shift = if (key == Key.Shift) next else Level.Off
        alt = if (key == Key.Alt) next else Level.Off
        panel = Panel.Closed
        // A quick second press that stays on ends the double press, so the next press turns the key off.
        last[key.ordinal] = if (level == Level.Once && next == Level.Once) time - DOUBLE_PRESS_MILLIS else time
    }

    /**
     * A push of Shift or Alt in an app that takes key presses before the keyboard and latches them by itself, as Android's
     * own text fields do. A second push locks there at any speed, so the keyboard follows Shift and Alt from here as well
     * and tells which pushes the app can see. Only the first press and a lock or unlock that this keyboard also does pass.
     */
    fun appPush(code: Int, time: Long, repeat: Int): AppPush {
        val key = keyOf(code) ?: return AppPush.Pass
        if (repeat > 0) return if (code in hiddenFromApp) AppPush.Hide else AppPush.Pass
        val before = level(key)
        down(code, time)
        val after = level(key)
        val push = when {
            before != Level.Once || after == Level.Locked -> AppPush.Pass
            after == Level.Once -> AppPush.Hide
            else -> AppPush.HideAndClear
        }
        if (push != AppPush.Pass) hiddenFromApp += code
        return push
    }

    /** The release of Shift or Alt in such an app. Returns true if its press was hidden, so that the app must not see this either. */
    fun appRelease(code: Int, time: Long): Boolean {
        up(code, time)
        return hiddenFromApp.remove(code)
    }

    /** From the symbols or emoji to the quick settings page, or back to the page that it came from. The clipboard goes back too. */
    fun toggleSettings() {
        if (panel == Panel.Closed) return
        if (panel == Panel.Settings || panel == Panel.Clipboard) panel = beforeSettings
        else {
            beforeSettings = panel
            panel = Panel.Settings
        }
    }

    /** Pressing Sym moves the panel on: symbols, then emoji, then closed. From the quick settings page it closes the panel. */
    fun pressSym() {
        shift = Level.Off
        alt = Level.Off
        panel = when (panel) {
            Panel.Closed -> Panel.Symbols
            Panel.Symbols -> Panel.Emoji
            Panel.Emoji, Panel.Settings, Panel.Clipboard -> Panel.Closed
        }
    }

    /**
     * To the clipboard, from A on the symbols or emoji, or X on the quick settings page. Alt then goes back to the symbols or
     * emoji, as from the quick settings.
     */
    fun openClipboard() {
        when (panel) {
            Panel.Settings -> panel = Panel.Clipboard
            Panel.Symbols, Panel.Emoji -> {
                beforeSettings = panel
                panel = Panel.Clipboard
            }
            Panel.Closed, Panel.Clipboard -> Unit
        }
    }

    /** Back to normal typing, after an item is picked or the keyboard is hidden. */
    fun closePanel() {
        panel = Panel.Closed
    }

    /** Puts the panel at [target] at once, for the guide that sets up a step. Shift and Alt go off, as a push of Sym does. */
    fun openPanel(target: Panel) {
        shift = Level.Off
        alt = Level.Off
        if (target == Panel.Settings) beforeSettings = Panel.Symbols
        panel = target
    }

    fun isCtrl(code: Int) = code == KeyEvent.KEYCODE_CTRL_LEFT || code == KeyEvent.KEYCODE_CTRL_RIGHT || fnIsCtrl && code == FN

    fun key(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) down(event.keyCode, event.eventTime)
        else if (event.action == KeyEvent.ACTION_UP) up(event.keyCode, event.eventTime)
    }

    fun down(code: Int, time: Long) {
        if (isCtrl(code)) {
            ctrlKeys += code
            return
        }
        if (code == KeyEvent.KEYCODE_SYM) {
            if (!symHeld) pressSym()
            symHeld = true
            return
        }
        val key = keyOf(code) ?: return
        val earlier = held[code]
        if (earlier == time) return
        // A new press of a key that is still down means its release was lost. That press ends now, so this one counts.
        if (earlier != null) up(code, time)
        held[code] = time
        usedWhileHeld -= code
        press(key, time)
    }

    fun up(code: Int, time: Long) {
        if (isCtrl(code)) {
            ctrlKeys.remove(code)
            return
        }
        if (code == KeyEvent.KEYCODE_SYM) symHeld = false
        val key = keyOf(code) ?: return
        held.remove(code) ?: return
        val used = usedWhileHeld.remove(code)
        if (held.keys.any { keyOf(it) == key }) return
        // Holding the modifier for a character releases it with the key, like a regular keyboard. A push that typed
        // nothing stays on, so a slow push of the key is still one push, and a lock stays locked.
        if (used && level(key) == Level.Once) when (key) {
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
        hiddenFromApp.clear()
        symHeld = false
        if (keepCtrl) return
        ctrlKeys.clear()
    }

    /**
     * A field has started. Ctrl stays down through a restart only when the field can still be typed in. The keyboard
     * does not follow Ctrl in a field that can't, so it would miss the release and leave Ctrl on for every later key.
     */
    fun fieldStarted(restarting: Boolean, editing: Boolean) = clear(keepCtrl = restarting && editing)

    companion object {
        const val DOUBLE_PRESS_MILLIS = 300L
        /** The key code that the Use fn as Ctrl setting listens for. */
        const val FN = KeyEvent.KEYCODE_FUNCTION

        /** What Ctrl does with each letter that the keyboard treats as a field shortcut. */
        val CTRL_ACTIONS = mapOf("A" to "Select all", "C" to "Copy", "X" to "Cut", "V" to "Paste", "Z" to "Undo", "Y" to "Redo")

        /**
         * What a key does to the pages of the open [panel], in rows down. On the symbol and emoji pages Backspace does what a swipe
         * down does, and Enter what a swipe up does. On the quick settings page they return 0, which changes nothing. Returns null
         * for other keys and for a closed panel. These two keys never reach the text while the panel is open.
         */
        fun panelScroll(code: Int, panel: Panel): Float? {
            val rows = when (code) {
                KeyEvent.KEYCODE_DEL -> 1f
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> -1f
                else -> return null
            }
            return when (panel) {
                Panel.Closed -> null
                Panel.Symbols, Panel.Emoji -> rows
                Panel.Settings, Panel.Clipboard -> 0f
            }
        }

        /** The meta state of a key pushed while Ctrl is held. Ctrl takes the place of fn, so the key acts as a held shortcut does. */
        fun withCtrl(meta: Int) = meta and KeyEvent.META_FUNCTION_ON.inv() or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON

        private fun keyOf(code: Int) = when (code) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> Key.Shift
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> Key.Alt
            else -> null
        }

        fun isModifier(code: Int) = code == KeyEvent.KEYCODE_SYM || keyOf(code) != null

        fun isShiftOrAlt(code: Int) = keyOf(code) != null

        /** Dropped typing must not take a Shift, Alt or Sym press with it, or their state would no longer match the keys. */
        fun survivesDiscard(code: Int, down: Boolean) = !down || isModifier(code)
    }
}
