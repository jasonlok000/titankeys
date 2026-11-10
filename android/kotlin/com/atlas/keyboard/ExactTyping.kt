package com.atlas.keyboard

import android.text.InputType
import android.view.KeyEvent

/**
 * Exact typing (issue 45), for terminals, remote desktops and code editors, where text must reach the app as it was typed.
 * In the apps on the list, TitanKeys fixes nothing, adds no capital letters or full stops, shows no suggestions and learns
 * nothing. Each key goes to the app as a key event, with Shift, Alt and Ctrl on it, also fn when it counts as Ctrl.
 *
 * Only a symbol that Alt types goes as text. The phone prints its symbols on the letters, and an app such as a terminal reads
 * Alt with a letter as a shortcut, so the symbol would not arrive any other way. Alt with a key that has no symbol, such as Tab,
 * stays a shortcut.
 *
 * Views that are no text field, such as Termux's terminal, report no class of text. Outside the list they keep what they had
 * before: the keys go to the app untouched, with no words. A window with no field at all reports neither a type nor options.
 *
 * The decisions are here, apart from Android, so tests can reach them. The keyboard sends the keys.
 */
object ExactTyping {
    /**
     * Whether a field start gets exact typing. It needs an app on the list, and a field: a window with no field sends a start
     * with no type and no options, and that stays as it is.
     */
    fun applies(inputType: Int, imeOptions: Int, packageName: String?, apps: Set<String>): Boolean =
        packageName != null && packageName in apps && (inputType != InputType.TYPE_NULL || imeOptions != 0)

    /**
     * The text that a key sends in place of itself: the symbol that Alt types, or null to send the key. [typed] is the key's
     * character with its whole meta state, and [plain] the same without Alt. With Ctrl or Meta, the key is always a shortcut.
     */
    fun symbol(meta: Int, typed: Int, plain: Int): String? {
        if (meta and KeyEvent.META_ALT_MASK == 0 || meta and (KeyEvent.META_CTRL_MASK or KeyEvent.META_META_MASK) != 0) return null
        if (typed <= 0 || typed == plain || !Character.isValidCodePoint(typed) || Character.isISOControl(typed)) return null
        return String(Character.toChars(typed))
    }

    /**
     * The modifier keys that go down before a key and up after it, so that an app that follows the modifier keys themselves,
     * as a remote desktop does, sees the shortcut too. Shift is left out: its meta state is enough for a capital letter, and a
     * text field would latch a Shift that it sees on its own.
     */
    fun wrappers(meta: Int): List<Int> {
        val keys = ArrayList<Int>(2)
        if (meta and KeyEvent.META_CTRL_MASK != 0) keys += KeyEvent.KEYCODE_CTRL_LEFT
        if (meta and KeyEvent.META_ALT_MASK != 0) keys += KeyEvent.KEYCODE_ALT_LEFT
        return keys
    }

    /** Puts [packageName] first in the apps where a field started recently, newest first and at most [MAX_RECENT_APPS]. */
    fun recentApps(recent: List<String>, packageName: String): List<String> =
        (listOf(packageName) + recent.filter { it != packageName }).take(MAX_RECENT_APPS)

    /** Apps that Settings offers to add to the list. */
    const val MAX_RECENT_APPS = 12
}
