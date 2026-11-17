package com.titankeys.keyboard

/**
 * What a "word" is in Chinese and Japanese, for every gesture that takes one (owner, 2026-11-17: "chinese triple tap yes, and
 * also like sliding selects word per word. the bar shd also match consistently. it's how we decide what's a 'word' is different
 * from english"). Three Space taps select one, a slide steps one at a time, a delete flick takes one, and the bar's word is one.
 * In this order:
 *
 * 1. **The units as picked** in this field: a choice picked from the engine's row, or a phrase Space committed, stays one unit
 *    wherever its text is (你好 picked together is one word, 唔該晒 too).
 * 2. **A word list** of the language's own, where one is given: Cantonese's words, which ICU's Mandarin dictionary lacks.
 * 3. **ICU's word breaks** ([IcuWords]) for the rest: text typed earlier, or pasted.
 *
 * Spaced languages keep their own rules: the keyboard asks this only while Chinese or Japanese types.
 */
class PickedWords {
    private val units = LinkedHashSet<String>()

    /** An engine committed [text]: it's one unit, the letters of it without marks at its ends (a full stop, a comma). */
    fun picked(text: String) {
        val unit = text.trim { !isLetter(it) }
        if (unit.length < 2 || unit.length > LONGEST || !unit.all(::isLetter) || !WordBreaks.anySpaceless(unit)) return
        units.remove(unit)
        units += unit
        if (units.size > MOST) units.remove(units.first())
    }

    /** Another field: its own units. */
    fun clear() = units.clear()

    /** The units, oldest first. */
    fun units(): Set<String> = units

    /**
     * The word breaks for this field's text: [units] first, then [known]'s words (Cantonese's), then [icu], each by the longest
     * match from the left. A stretch neither finds is [icu]'s, cut where a unit starts.
     */
    fun breaker(icu: WordBreaker, known: ((String) -> Boolean)? = null): WordBreaker = WordBreaker { text -> pieces(text, icu, known) }

    private fun pieces(text: String, icu: WordBreaker, known: ((String) -> Boolean)?): List<WordPiece> {
        val icuPieces = icu.pieces(text)
        val out = ArrayList<WordPiece>()
        var at = 0
        while (at < text.length) {
            val length = longestAt(text, at, known)
            if (length > 0) {
                out += WordPiece(at, at + length, true)
                at += length
                continue
            }
            // ICU's piece from here, cut where the next unit or known word starts.
            val piece = icuPieces.firstOrNull { it.end > at } ?: break
            var end = piece.end
            if (piece.word) for (next in at + 1 until piece.end) if (longestAt(text, next, known) > 0) {
                end = next
                break
            }
            out += WordPiece(at, end, piece.word)
            at = end
        }
        return out
    }

    /** The length of the longest unit, or else known word, that starts at [at] in [text]; 0 for none. */
    private fun longestAt(text: String, at: Int, known: ((String) -> Boolean)?): Int {
        if (!WordBreaks.spaceless(text.codePointAt(at))) return 0
        val most = minOf(LONGEST, text.length - at)
        if (units.isNotEmpty()) for (length in most downTo 2) if (text.substring(at, at + length) in units) return length
        if (known != null) for (length in minOf(KNOWN_LONGEST, most) downTo 2) if (known(text.substring(at, at + length))) return length
        return 0
    }

    /** The word that ends at the end of [before], as the gestures find it, or null where [before] doesn't end with one. */
    fun lastWord(before: String, icu: WordBreaker, known: ((String) -> Boolean)? = null): String? {
        val piece = pieces(before, icu, known).lastOrNull() ?: return null
        return if (piece.word && piece.end == before.length) before.substring(piece.start, piece.end) else null
    }

    private fun isLetter(c: Char) = Character.isLetter(c) || Character.isSurrogate(c) ||
        Character.getType(c).let { it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() }

    companion object {
        /** The longest unit kept: a phrase longer than this was likely a sentence committed at once. */
        const val LONGEST = 8
        /** The longest known word looked for. */
        const val KNOWN_LONGEST = 6
        /** The units a field keeps, the latest. */
        const val MOST = 500
    }
}
