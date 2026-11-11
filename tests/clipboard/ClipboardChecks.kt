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
    same(full.clips.size, ClipboardHistory.LIMIT, "The history holds at most twenty clips that are not pinned")
    same(full.clips.first().text, "clip ${ClipboardHistory.LIMIT + 5}", "The newest of many stays")
    same(full.clips.last().text, "clip 6", "The oldest of many go first")
    same(full.togglePin(full.clips.lastIndex), true, "A clip can be pinned")
    full.add("one more", 100)
    same(full.clips.any { it.text == "clip 7" }, true, "A pin frees a place, so one more clip fits")
    full.add("two more", 101)
    same(full.clips.last().text, "clip 6", "A pinned clip is not pushed out by new ones")
    same(full.clips.any { it.text == "clip 7" }, false, "The oldest clip that is not pinned makes room")
    same(full.togglePin(99), false, "A place with no clip pins nothing")

    same(full.clear(), true, "Clear all removes clips")
    same(full.clips.map { it.text }, listOf("clip 6"), "Clear all keeps the pinned clips")

    val pins = ClipboardHistory()
    for (i in 1..30) {
        pins.add("pin $i", i.toLong())
        pins.togglePin(0)
    }
    for (i in 1..ClipboardHistory.LIMIT + 3) pins.add("clip $i", 100L + i)
    same(pins.clips.count { it.pinned }, 30, "Pins have no limit")
    same(pins.clips.count { !it.pinned }, ClipboardHistory.LIMIT, "Pins do not take the places of new clips")
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
    // The offer on the bar: the start and the last word of a long clip. Here each character is 1 wide.
    val width = { text: String -> text.length.toFloat() }
    same(ClipboardHistory.ends("For the record I will be late tonight", 99f, width), "For the record I will be late tonight", "A clip that fits shows whole")
    same(ClipboardHistory.ends("For the record I will be late tonight", 24f, width), "For the record … tonight", "A long clip shows its start and its last word")
    same(ClipboardHistory.ends("For the record I will be late tonight", 13f, width), "For … tonight", "With less room, fewer words at the start")
    same(ClipboardHistory.ends("Forever and tonight", 11f, width), "Fo… tonight", "A first word too long for the room keeps its start")
    same(ClipboardHistory.ends("extraordinarily", 8f, width), "extraor…", "One long word keeps its start")
    same(ClipboardHistory.ends("For the record I will be late tonight", 24f, width).length <= 24, true, "The cut clip fits the room")

    // The clipboard page: six wide fields, two to a row, inside the inner columns.
    same(listOf("W", "T", "Y", "O", "S", "G", "H", "L", "Z", "V", "B", "M").map(ClipboardHistory::slot),
        listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5), "Each half row of inner letters is under one clip")
    same(listOf("Q", "A", "P", Layout.ALT, Layout.SYM, Layout.BACKSPACE, Layout.ENTER, Layout.SPACE).map(ClipboardHistory::slot),
        List(8) { null }, "The outer columns, Sym and Space are no clip, as the touchpad reads the edges poorly")
    for (slot in 0 until ClipboardHistory.PER_PAGE)
        same(ClipboardHistory.slot(ClipboardHistory.keyFor(slot)), slot, "A touch on clip $slot pastes what its keys paste")
    same((0..2).map { row -> (0..1).map { ClipboardHistory.width(row, it) } }, listOf(listOf(4, 4), listOf(4, 4), listOf(4, 3)),
        "Each field is four keys wide, but the last stops at Sym")
    same((0..2).map { row -> (0..1).map { ClipboardHistory.firstColumn(row, it) } }, listOf(listOf(1, 5), listOf(1, 5), listOf(1, 5)),
        "No field starts in the outer column")

    // Clipboard and Pinned, in pages of six, as the symbols and emoji have groups and pages.
    run {
        val clips = (1..14).map { ClipboardHistory.Clip("clip $it", it.toLong(), pinned = it % 5 == 0) }
        val pages = ClipboardHistory.Pages()
        same(pages.pageCount(clips), 3, "Fourteen clips take three pages of six")
        same(pages.index(clips, 0), 0, "The first field holds the newest clip")
        same(pages.swipe(clips, 0f, -1f), true, "A swipe up turns to the next page")
        // Clips that aren't pinned come first, and the pins after them: the seventh of those is at index 7, past the pin at 4.
        same(pages.index(clips, 0), 7, "The second page starts at the seventh clip that isn't pinned")
        same(pages.items(clips).takeLast(2), listOf(4, 9), "The pins come after the rest")
        pages.swipe(clips, 0f, -1f)
        same(pages.index(clips, 2), null, "The last page has empty fields")
        same(pages.swipe(clips, 0f, -1f), true, "The pages wrap round, as on the symbols")
        same(pages.page, 0, "After the last page comes the first")
        same(pages.swipe(clips, -1f, 0f), true, "A swipe sideways changes the group")
        same(pages.group, ClipboardHistory.PINNED, "Pinned is the second group")
        same((0 until 3).map { pages.index(clips, it) }, listOf(4, 9, null), "Pinned holds only the pinned clips")
        same(pages.pageCount(clips), 1, "Two pins take one page")
        same(pages.swipe(clips, 0f, -1f), false, "With one page a swipe up changes nothing")
        same(pages.open(ClipboardHistory.RECENT), true, "A group can be opened by its place on the strip")
        same(pages.open(ClipboardHistory.RECENT), false, "Opening the open group changes nothing")
        same(pages.groupAt(.2f) to pages.groupAt(.8f), ClipboardHistory.RECENT to ClipboardHistory.PINNED, "The strip is half Clipboard, half Pinned")
        same(pages.groupUnder(.3f, .6f), ClipboardHistory.RECENT, "A key over the open group keeps it")
        pages.swipe(clips, 0f, -1f)
        pages.swipe(clips, 0f, -1f)
        pages.fit(clips.take(3))
        same(pages.page, 0, "When clips go, the page stays inside the group")
        pages.reset()
        same(pages.group to pages.page, ClipboardHistory.RECENT to 0, "The clipboard opens at the first page of Clipboard")
    }

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
