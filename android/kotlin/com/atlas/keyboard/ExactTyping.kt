package com.atlas.keyboard

import android.content.Context
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
 * Views that are no text field, such as Termux's terminal, report no class of text. AnyDesk's session view reports no type and
 * no options at all, the same as a window with no field, so in a listed app every start counts. Outside the list such views
 * keep what they had before: the keys go to the app untouched, with no words.
 *
 * The decisions are here, apart from Android, so tests can reach them. The keyboard sends the keys.
 */
object ExactTyping {
    /**
     * The apps on the list until the user changes it: terminals, remote desktops and game streaming, where every key must reach
     * the app or the computer behind it as it is. Each package name was checked on its Google Play listing, and RustDesk's,
     * which has none, in the project's own code and on F-Droid (2026-11-10). The user can remove any of them.
     */
    val DEFAULT_APPS: Set<String> = linkedSetOf(
        // Terminals and SSH: Termux, ConnectBot, Termius.
        "com.termux", "org.connectbot", "com.server.auditor.ssh.client",
        // Remote desktops: AnyDesk, TeamViewer, Windows App (Microsoft's Remote Desktop), Chrome Remote Desktop, RustDesk,
        // RealVNC Viewer, bVNC and aRDP in their free and Pro versions.
        "com.anydesk.anydeskandroid", "com.teamviewer.teamviewer.market.mobile", "com.microsoft.rdc.androidx",
        "com.google.chromeremotedesktop", "com.carriez.flutter_hbb", "com.realvnc.viewer.android",
        "com.iiordanov.freebVNC", "com.iiordanov.bVNC", "com.iiordanov.freeaRDP", "com.iiordanov.aRDP",
        // Game streaming: Moonlight, Parsec.
        "com.limelight", "tv.parsec.client",
    )

    private const val APPS = "exact_apps"
    private const val CURSOR = "exact_touchpad_cursor"

    private fun preferences(context: Context) = context.getSharedPreferences("typing_settings", Context.MODE_PRIVATE)

    /** The apps with exact typing. */
    fun apps(context: Context): Set<String> = preferences(context).getStringSet(APPS, null)?.toSet() ?: DEFAULT_APPS

    fun apps(context: Context, apps: Set<String>) {
        preferences(context).edit().putStringSet(APPS, HashSet(apps)).apply()
    }

    /**
     * The touchpad moves the cursor with the arrow keys in exact typing. It is off until the user turns it on, so that a finger
     * on the keys sends nothing to a terminal or a remote computer.
     */
    fun touchpadCursor(context: Context): Boolean = preferences(context).getBoolean(CURSOR, false)

    fun touchpadCursor(context: Context, on: Boolean) {
        preferences(context).edit().putBoolean(CURSOR, on).apply()
    }

    /** Whether a field start gets exact typing: any start in an app on the list. */
    fun applies(packageName: String?, apps: Set<String>): Boolean = packageName != null && packageName in apps

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
     * as a remote desktop does, sees the capital or the shortcut too. A text field lets go of a Shift that a key used, so it
     * latches nothing. The phone's own Shift and Alt keys never reach the app: the keyboard keeps them for one key or a lock.
     */
    fun wrappers(meta: Int): List<Int> {
        val keys = ArrayList<Int>(3)
        if (meta and KeyEvent.META_SHIFT_MASK != 0) keys += KeyEvent.KEYCODE_SHIFT_LEFT
        if (meta and KeyEvent.META_CTRL_MASK != 0) keys += KeyEvent.KEYCODE_CTRL_LEFT
        if (meta and KeyEvent.META_ALT_MASK != 0) keys += KeyEvent.KEYCODE_ALT_LEFT
        return keys
    }

    /** One key event to send: a press or a release of [code] with [meta]. */
    data class Event(val down: Boolean, val code: Int, val meta: Int, val repeat: Int = 0)

    /**
     * What one key does: [text] to put in, or else [events] to send. Either way the key uses up a Shift or Alt that was pushed
     * one time, also a shortcut such as Alt and Tab, so that a one-time Alt never stays on for the key after.
     */
    data class Plan(val text: String?, val events: List<Event>)

    /**
     * What a key does in exact typing. [meta] holds the key's own meta state and the Shift and Alt that the keyboard keeps, and
     * [charFor] gives the key's character with a meta state. Shift, Alt and Sym themselves never come here: the keyboard keeps
     * them, so the app sees none of their own presses.
     */
    fun plan(code: Int, repeat: Int, meta: Int, charFor: (Int) -> Int): Plan {
        symbol(meta, charFor(meta), charFor(meta and KeyEvent.META_ALT_MASK.inv()))?.let { return Plan(it, emptyList()) }
        val wrappers = wrappers(meta)
        val events = wrappers.map { Event(true, it, meta) } + Event(true, code, meta, repeat) + Event(false, code, meta) +
            wrappers.asReversed().map { Event(false, it, meta) }
        return Plan(null, events)
    }

    /** A held letter went to the app as a key, so a Backspace key takes it back before its symbol goes in. */
    val takeBack = listOf(Event(true, KeyEvent.KEYCODE_DEL, 0), Event(false, KeyEvent.KEYCODE_DEL, 0))

    /** Puts [packageName] first in the apps where a field started recently, newest first and at most [MAX_RECENT_APPS]. */
    fun recentApps(recent: List<String>, packageName: String): List<String> =
        (listOf(packageName) + recent.filter { it != packageName }).take(MAX_RECENT_APPS)

    /** Apps that Settings offers to add to the list. */
    const val MAX_RECENT_APPS = 12
}
