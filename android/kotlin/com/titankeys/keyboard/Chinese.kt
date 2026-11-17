package com.titankeys.keyboard

/**
 * Chinese on the bar (docs/languages.md, "Chinese on the bar"): where the choices sit in the row and in the bigger menu,
 * which keys pick them, the marks Rime types, and when the typed letters are offered as an English word. No Android here, so
 * the JVM checks (tests/chinese) and the renders use these same rules.
 */
object ChineseRow {
    /** Q's column: the input method's mark. */
    const val MARK = 0
    /** The choices' columns, W to O. */
    const val FIRST = 1
    const val LAST = 8
    /** P's column: no choice, only the "more" cue. */
    const val MORE = 9
    /** The cue at P when the engine has more choices than the row shows. */
    const val MORE_CUE = "⋯"
    /** The rows of letters whose keys pick the choice over them, with Shift, a hold or a flick: the top two, as the word menu's. */
    const val ROWS = 2
    /** How many choices the row asks the engine for: at most one a key. */
    const val ASK = LAST - FIRST + 1

    /**
     * How wide [text] is, in half keys: a Chinese character (or any wide one: kana, Hangul, full-width marks, emoji) counts
     * two, anything else one.
     */
    fun units(text: String): Int {
        var units = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            units += if (wide(cp)) 2 else 1
            i += Character.charCount(cp)
        }
        return units
    }

    private fun wide(cp: Int): Boolean {
        if (cp in 0x1100..0x115F || cp in 0x2E80..0xA4CF || cp in 0xAC00..0xD7A3 || cp in 0xF900..0xFAFF ||
            cp in 0xFE30..0xFE4F || cp in 0xFF00..0xFF60 || cp in 0xFFE0..0xFFE6 || cp >= 0x1F000) return true
        return false
    }

    /** How many keys [text] takes: one for one or two characters, two for three or four (屌你老母), and so on. */
    fun keys(text: String): Int = maxOf(1, (units(text) + 3) / 4)

    /**
     * Each choice's key columns, from [first] to [last], in the engine's order, each as wide as it needs; null for a choice
     * that doesn't fit the room left, which waits for the bigger menu while shorter ones after it still take the row (a
     * Japanese sentence is wide, and its parts come after it). A first choice wider than the whole row takes the whole row,
     * its text shrunk to fit.
     */
    fun place(choices: List<String>, first: Int = FIRST, last: Int = LAST): List<IntRange?> {
        return placed(choices, first, last)
    }

    /**
     * Whether the engine's [listed] choices go past the row as [place] fits them, so the bigger menu has more to show: Shift
     * and a swipe up open it only then (owner, 2026-11-17: "disable shift to expand the bar if there's nothing besides the
     * main bar").
     */
    fun more(listed: List<String>): Boolean = listed.size > place(listed.take(ASK)).count { it != null }

    private fun placed(choices: List<String>, first: Int, last: Int): List<IntRange?> {
        val placed = ArrayList<IntRange?>()
        var at = first
        for (choice in choices) {
            val keys = if (at == first) minOf(keys(choice), last - first + 1) else keys(choice)
            if (at + keys - 1 > last) {
                placed += null
                continue
            }
            placed += at until at + keys
            at += keys
        }
        return placed
    }

    /** The choice over key column [column], given each choice's columns (null for one not in the row), or -1. */
    fun choiceAt(column: Int, columns: List<IntRange?>) = columns.indexOfFirst { it != null && column in it }

    /** The column of letter [key] in the rows that pick, or -1: Q and A and P are columns too, but have no choice. */
    fun column(key: String) = Picker.column(key, ROWS)
}

/**
 * The bigger menu that holding P opens: the choices over the three letter rows, W to O, S to L and Z to M, each as wide as it
 * needs, a page at a time. P shows the page; Enter and Backspace turn the pages, as on the Sym panel.
 */
object ChineseMenu {
    /** Each row's letter columns: the Sym panel's places (Layout.panelLetters). */
    val ROWS = listOf(1..8, 1..8, 1..7)

    /** A choice on a page: its place in the whole list, its row, and its columns. */
    data class Cell(val index: Int, val row: Int, val columns: IntRange)

    /** One page: its cells, from the top row's left. */
    data class Page(val cells: List<Cell>) {
        val first get() = cells.firstOrNull()?.index ?: 0
        val end get() = (cells.lastOrNull()?.index ?: -1) + 1
    }

    /**
     * One page from choice [start] on: rows filled left to right, top to bottom, each choice as wide as it needs. A choice too
     * wide for what's left of a row starts the next row; one wider than a whole row is cut to the row. Null when [start] is
     * past the list.
     */
    fun page(choices: List<String>, start: Int): Page? {
        if (start !in choices.indices) return null
        val cells = ArrayList<Cell>()
        var index = start
        for ((row, columns) in ROWS.withIndex()) {
            var at = columns.first
            while (index < choices.size) {
                val keys = minOf(ChineseRow.keys(choices[index]), columns.last - columns.first + 1)
                if (at + keys - 1 > columns.last) break
                cells += Cell(index, row, at until at + keys)
                at += keys
                index++
            }
        }
        return Page(cells)
    }

    /** Where each page starts, for every page [choices] fill. */
    fun starts(choices: List<String>): List<Int> {
        val starts = ArrayList<Int>()
        var start = 0
        while (start < choices.size) {
            starts += start
            start = page(choices, start)?.end ?: break
        }
        return starts
    }

    /** The choice that key [key] picks on [page], or -1. */
    fun choice(page: Page, key: String): Int {
        for ((row, letters) in Layout.rows.take(ROWS.size).withIndex()) {
            val column = letters.indexOf(key)
            if (column < 0) continue
            return page.cells.firstOrNull { it.row == row && column in it.columns }?.index ?: -1
        }
        return -1
    }

    /** P's text: the page and how many there are ("2/5"), or the page and "›" while more may follow. Empty with one page. */
    fun position(page: Int, pages: Int, more: Boolean): String = when {
        page == 0 && pages <= 1 && !more -> ""
        more -> "${page + 1}›"
        else -> "${page + 1}/$pages"
    }
}

/** The keys of a Chinese input method, apart from the letters. */
object ChineseKeys {
    /** Whether [method] is Chinese: one of Rime's input methods. */
    fun isChinese(method: InputMethod) = method.engine == ENGINE
    /** Rime's engine name in im.json, as RimeEngine.ENGINE. */
    const val ENGINE = "rime"
    /** The engines whose input methods use this bar and its keys: Chinese's Rime and Japanese's Mozc (MozcEngine.ENGINE). */
    val BAR_ENGINES = setOf(ENGINE, "mozc")
    /** Whether [method] types through the bar of choices: Chinese and Japanese. The English word is Chinese's alone. */
    fun usesBar(method: InputMethod) = method.engine in BAR_ENGINES

    /**
     * What was picked of a code, from Rime's [preedit] once nothing is pending: its text without spaces, or empty while any of
     * it is still letters ([input]'s), as with nothing picked.
     */
    fun pickedText(preedit: String?, input: String): String {
        val text = preedit.orEmpty().filterNot { it.isWhitespace() }
        return if (text.isEmpty() || text.any { it in 'a'..'z' || it in 'A'..'Z' } || text == input) "" else text
    }

    /** [text] less its last character, a whole code point (an emoji or a rare character is two chars). */
    fun dropLastCharacter(text: String): String =
        if (text.isEmpty()) "" else text.substring(0, text.offsetByCodePoints(text.length, -1))

    /** Rime's option for plain (ASCII) punctuation, the setting's. */
    const val PLAIN_PUNCTUATION = "ascii_punct"

    /** A key that types punctuation: printable ASCII that isn't a letter, a digit or a space. */
    fun isPunctuation(char: Char) = char in '!'..'~' && !char.isLetterOrDigit()

    /**
     * The marks as the variant writes them: Hong Kong and Taiwan quote with 「」 and 『』, where Rime's punctuator gives the
     * mainland's “” and ‘’. Simplified keeps them.
     */
    fun marks(text: String, variant: String?): String {
        if (variant != "zh_hk" && variant != "zh_tw") return text
        if (text.none { it in "“”‘’" }) return text
        return text.map { when (it) { '“' -> '「'; '”' -> '」'; '‘' -> '『'; '’' -> '』'; else -> it } }.joinToString("")
    }

    /** What a swipe on the keys does while an engine's choices are on the bar. */
    enum class Swipe { OpenMenu, NextPage, PageBefore, Nothing }

    /**
     * A swipe on the touchpad with choices on the bar, a page turn's length or more ([Contact.PAGE_KEYS], which Contact asks of
     * every paging swipe): up opens the bigger menu (owner, 2026-11-17: "swiping up would expand the word list"), and with the
     * menu open, up and down turn its pages, as on the Sym panel. Mostly sideways, or less than half a row up or down, is
     * nothing: a slip, or a swipe meant for something else. [across] and [down] are in keys and rows; up is negative.
     */
    fun swipe(across: Float, down: Float, menuOpen: Boolean): Swipe {
        if (kotlin.math.abs(down) * 10 < kotlin.math.abs(across) * 4 || kotlin.math.abs(down) < SWIPE_ROWS) return Swipe.Nothing
        return when {
            menuOpen -> if (down < 0f) Swipe.NextPage else Swipe.PageBefore
            down < 0f -> Swipe.OpenMenu
            else -> Swipe.Nothing
        }
    }

    /** How far up or down a swipe on the choices must go, in rows: half a page turn's [Contact.PAGE_KEYS] (.45 keys). */
    const val SWIPE_ROWS = .45f / 2f
}

/**
 * The typed letters as an English word, offered after Rime's first choice, never as the first, and only when they're a word in
 * English's list and Rime can't read them as whole syllables. Rime reads nearly any letters through abbreviations ("hello" is
 * he l lo, 嚱嘍囉), so the test is Rime's own split of the code: a piece that isn't a whole syllable, or letters left
 * unread, or no choices at all.
 */
object EnglishOffer {
    /** Where the word goes in the row: second. */
    const val PLACE = 1

    /** One-letter syllables: Cantonese's 唔 (m) and 吳 (ng is two), Mandarin's and Cantonese's vowels alone. */
    private val SINGLES = setOf("a", "e", "o", "m", "n")

    /**
     * Whether to offer [typed] (the letters as typed) as an English word. [split] is Rime's spelling of the code, its pieces
     * apart (he l lo); [partial] is true when Rime's choices cover only part of the code; [choices] how many Rime has.
     */
    fun offers(typed: String, split: String, partial: Boolean, choices: Int, isWord: (String) -> Boolean): Boolean {
        if (typed.length < 2 || typed.any { it !in 'a'..'z' }) return false
        if (!isWord(typed)) return false
        return choices == 0 || partial || !wholeSyllables(split)
    }

    /** True when every piece of Rime's split is a whole syllable: no lone letter but a one-letter syllable. */
    fun wholeSyllables(split: String): Boolean {
        val pieces = split.split(' ', '\'').filter { it.isNotEmpty() }
        if (pieces.isEmpty()) return false
        return pieces.none { it.length == 1 && it.lowercase() !in SINGLES }
    }

    /** The row's choices with the word in [PLACE], or as they are. */
    fun withWord(choices: List<String>, word: String?): List<String> =
        if (word == null) choices else choices.toMutableList().apply { add(minOf(PLACE, size), word) }
}
