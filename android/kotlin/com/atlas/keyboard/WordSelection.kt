package com.atlas.keyboard

/** Decides which text the touchpad gestures select. Positions are indexes in the text, and a range is its start and end. */
object WordSelection {
    private fun isWordCharacter(character: Char) = Character.isLetterOrDigit(character) || character == '\'' || character == '’'

    /**
     * The word at the selection, grown to whole words. A cursor that touches no word but sits just after a word
     * and one space takes that word and the space. Returns null between words.
     */
    fun at(text: CharSequence, selectionStart: Int, selectionEnd: Int): Pair<Int, Int>? {
        val start = skip(text, selectionStart, -1, word = true)
        val end = skip(text, selectionEnd, 1, word = true)
        if (start != end) return start to end
        if (start < 2 || text[start - 1] != ' ' || !isWordCharacter(text[start - 2])) return null
        return skip(text, start - 1, -1, word = true) to end
    }

    /**
     * The word at the selection, or else the word in front of the cursor: the last word before it, past any spaces and marks
     * such as a full stop or a comma, on the same line. Returns null where no word comes before the cursor on its line.
     */
    fun atOrBefore(text: CharSequence, selectionStart: Int, selectionEnd: Int): Pair<Int, Int>? {
        at(text, selectionStart, selectionEnd)?.let { return it }
        var end = selectionStart
        while (end > 0 && !isWordCharacter(text[end - 1]) && text[end - 1] != '\n') end--
        if (end == 0 || !isWordCharacter(text[end - 1])) return null
        return skip(text, end, -1, word = true) to end
    }

    /**
     * True where a double or triple tap would find a word to pick: [inFront] for a triple tap, see [atOrBefore]. An empty
     * field, a cursor between spaces with nothing to take, or only marks have none, and the taps then do nothing.
     */
    fun anyToPick(text: CharSequence, selectionStart: Int, selectionEnd: Int, inFront: Boolean) =
        (if (inFront) atOrBefore(text, selectionStart, selectionEnd) else at(text, selectionStart, selectionEnd)) != null

    /** True where steering has text to move through: anything at all before or after the cursor. */
    fun anyToSteer(before: CharSequence, after: CharSequence) = before.isNotEmpty() || after.isNotEmpty()

    /**
     * Where a double space puts the cursor when text from [start] to [end] is selected: back where it was before the select,
     * [before], when that is known and inside the selection, or else at the selection's end.
     */
    fun collapseTo(start: Int, end: Int, before: Int?) = before?.takeIf { it in start..end } ?: end

    /**
     * What the bar shows for a selection of [length] characters whose text is [text]: the text alone, without the spaces and
     * marks at its ends, and with each run of spaces as one, so a word selected with a space or a comma next to it still shows as
     * that word. Null with nothing selected, or when the field doesn't give the text: then the bar shows the suggestions.
     */
    fun shown(text: CharSequence?, length: Int): String? {
        if (length <= 0 || text.isNullOrEmpty()) return null
        val cut = if (text.length > MAX_SHOWN) text.subSequence(0, MAX_SHOWN) else text
        val trimmed = cut.toString().trim { !it.isLetterOrDigit() }.replace(SPACES, " ")
        return trimmed.ifEmpty { null }
    }

    /** One word: letters, and apostrophes between them, as in "Lok" or "don't". Only one word opens its menu. */
    fun isWord(text: String) = text.length <= MAX_WORD && WORD.matches(text)

    /** The bar shows no more of a selection than this; it shrinks to fit, then loses its end. */
    private const val MAX_SHOWN = 200
    /** A selection longer than this is no single word. */
    private const val MAX_WORD = 32
    private val WORD = Regex("\\p{L}+(['’]\\p{L}+)*")
    private val SPACES = Regex("\\s+")

    /**
     * The selection after one word of a word drag towards [direction], -1 or 1. It grows by a word. If it has already
     * grown the other way past the [anchorStart] to [anchorEnd] that the drag began with, it gives back a word instead.
     */
    fun extend(text: CharSequence, start: Int, end: Int, anchorStart: Int, anchorEnd: Int, direction: Int): Pair<Int, Int> = when {
        direction < 0 && end > anchorEnd -> start to maxOf(anchorEnd, overWord(text, end, direction))
        direction > 0 && start < anchorStart -> minOf(anchorStart, overWord(text, start, direction)) to end
        direction < 0 -> toWord(text, start, direction) to end
        else -> start to toWord(text, end, direction)
    }

    /** The far end of the next word towards [direction], or [from] if there is no other word that way. */
    private fun toWord(text: CharSequence, from: Int, direction: Int): Int {
        val word = skip(text, from, direction, word = false)
        val after = skip(text, word, direction, word = true)
        return if (after == word) from else after
    }

    /** Where [from] ends up after going back over its word and the gap before it. */
    private fun overWord(text: CharSequence, from: Int, direction: Int) =
        skip(text, skip(text, from, direction, word = true), direction, word = false)

    /** Goes from [from] towards [direction] over word characters, or over the characters between words. */
    private fun skip(text: CharSequence, from: Int, direction: Int, word: Boolean): Int {
        var at = from
        while (true) {
            val next = if (direction < 0) at - 1 else at
            if (next !in text.indices || isWordCharacter(text[next]) != word) return at
            at += direction
        }
    }
}
