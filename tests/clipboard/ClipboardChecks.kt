package com.atlas.keyboard

import android.view.KeyEvent
import com.atlas.keyboard.Modifiers.Panel

/** Clipboard history (#46): up to twenty clips, an hour each unless pinned, Clear all, and its page in the Sym panel. */
fun main() {
    val failures = ArrayList<String>()
    fun same(actual: Any?, expected: Any?, message: String) {
        if (actual != expected) failures += "$message: expected $expected, got $actual"
    }
    val hour = ClipboardHistory.KEEP_MILLIS

    val history = ClipboardHistory()
    same(history.add("first", 0), true, "A copy is kept")
    same(history.add("second", 10), true, "A second copy is kept")
    same(history.clips.map { it.text }, listOf("second", "first"), "The newest clip comes first")
    same(history.add("   ", 20), false, "A blank copy is not kept")
    same(history.add("x".repeat(ClipboardHistory.MAX_CHARS + 1), 20), false, "A very long copy stays in Android's clipboard only")
    history.add("first", 30)
    same(history.clips.map { it.text }, listOf("first", "second"), "Copying a clip again moves it to the front, once")

    history.togglePin(1)
    same(history.clips[1].pinned, true, "A clip can be pinned")
    history.add("second", 40)
    same(history.clips[0], ClipboardHistory.Clip("second", 40, pinned = true), "A pinned clip that is copied again keeps its pin")
    same(history.expire(40 + hour + 1), true, "Clips go after an hour")
    same(history.clips.map { it.text }, listOf("second"), "A pinned clip stays after an hour")
    same(history.expire(40 + hour + 1), false, "Nothing more goes at the same time")

    val full = ClipboardHistory()
    for (i in 1..ClipboardHistory.LIMIT + 5) full.add("clip $i", i.toLong())
    same(full.clips.size, ClipboardHistory.LIMIT, "The history holds at most twenty clips")
    same(full.clips.first().text, "clip ${ClipboardHistory.LIMIT + 5}", "The newest of many stays")
    same(full.clips.last().text, "clip 6", "The oldest of many go first")
    same(ClipboardHistory.LIMIT <= Layout.panelLetters.size, true, "Every clip has a letter key in the panel")
    full.togglePin(full.clips.lastIndex)
    full.add("one more", 100)
    same(full.clips.last().text, "clip 6", "A pinned clip is not pushed out by new ones")
    same(full.clips.any { it.text == "clip 7" }, false, "The oldest clip that is not pinned makes room")
    val pinnedOnly = ClipboardHistory((1..ClipboardHistory.LIMIT).map { ClipboardHistory.Clip("pin $it", it.toLong(), pinned = true) })
    same(pinnedOnly.add("new", 200), false, "When every place is pinned, a new clip is not kept")

    same(full.clear(), true, "Clear all removes clips")
    same(full.clips.map { it.text }, listOf("clip 6"), "Clear all keeps the pinned clips")
    same(ClipboardHistory().clear(), false, "Clear all on nothing changes nothing")

    val odd = ClipboardHistory()
    odd.add("line one\nline\ttwo \\ end", 5)
    odd.add("plain", 6)
    odd.togglePin(0)
    val restored = ClipboardHistory.decode(odd.encode())
    same(restored.clips, odd.clips, "Clips with line breaks, tabs and backslashes come back as they were")
    same(ClipboardHistory.decode("broken\n12\t2\tbad\n\n7\t0\tgood").clips, listOf(ClipboardHistory.Clip("good", 7)),
        "Damaged lines are left out")

    same(ClipboardHistory.preview(" one\n two\tthree "), "one two three", "The preview is one line")
    same(ClipboardHistory.preview("abcdefghij", 5), "abcd…", "The strip cuts a long clip to fit one line")

    // The clipboard page: six clips at a time, two to a row, each over half a row of keys.
    same(listOf("Q", "T", "Y", "P", "A", "G", "H", "L", "Z", "V", "B", "M").map(ClipboardHistory::slot),
        listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5), "Each half row of letters is under one clip")
    same(listOf(Layout.ALT, Layout.SYM, Layout.BACKSPACE, Layout.ENTER, Layout.SPACE).map(ClipboardHistory::slot),
        listOf(null, null, null, null, null), "Alt, Sym, Backspace, Enter and Space are no clip")
    for (slot in 0 until ClipboardHistory.PER_PAGE)
        same(ClipboardHistory.slot(ClipboardHistory.keyFor(slot)), slot, "A touch on clip $slot pastes what its keys paste")
    same(listOf(0, 1, 6, 7, 20).map(ClipboardHistory::pageCount), listOf(1, 1, 1, 2, 4), "Six clips to a page, and one page when empty")

    // The page in the Sym panel.
    same(QuickSetting.on("X"), QuickSetting.Clipboard, "X opens the clipboard from the quick settings page")
    same(QuickSetting.entries.map { it.key }.toSet().size, QuickSetting.entries.size, "Each quick setting has its own key")
    val modifiers = Modifiers()
    modifiers.pressSym()
    modifiers.toggleSettings()
    modifiers.openClipboard()
    same(modifiers.panel, Panel.Clipboard, "The clipboard opens from the quick settings page")
    modifiers.toggleSettings()
    same(modifiers.panel, Panel.Symbols, "Alt goes back from the clipboard to the symbols")
    modifiers.openClipboard()
    same(modifiers.panel, Panel.Clipboard, "A on the symbols opens the clipboard too")
    modifiers.toggleSettings()
    same(modifiers.panel, Panel.Symbols, "Alt goes back from there to the symbols")
    modifiers.pressSym()
    modifiers.openClipboard()
    modifiers.toggleSettings()
    same(modifiers.panel, Panel.Emoji, "From the emoji, the clipboard goes back to the emoji")
    modifiers.closePanel()
    modifiers.openClipboard()
    same(modifiers.panel, Panel.Closed, "With the panel closed, nothing opens")
    modifiers.pressSym()
    modifiers.toggleSettings()
    modifiers.openClipboard()
    modifiers.pressSym()
    same(modifiers.panel, Panel.Closed, "Sym closes the clipboard")
    for (code in listOf(KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ENTER))
        same(Modifiers.panelScroll(code, Panel.Clipboard), 0f, "Key $code turns no page on the clipboard and never reaches the text")

    if (failures.isNotEmpty()) {
        failures.forEach(::println)
        throw AssertionError("${failures.size} clipboard check(s) failed")
    }
    println("Clipboard checks passed")
}
