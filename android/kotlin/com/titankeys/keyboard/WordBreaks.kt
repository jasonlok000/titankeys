package com.titankeys.keyboard

/**
 * Words in scripts written without spaces between them: Chinese, Japanese, Thai, Lao, Khmer, Burmese. There a run of letters can
 * be a whole sentence, so the gestures that take a word (a delete flick and each step of its hold, Ctrl + Backspace, three Space
 * taps) take one word as ICU's word breaks find it ([IcuWords] on the phone), not the run (owner, 2026-11-16: the gestures work
 * in every language). Where words have spaces, nothing changes.
 *
 * Nothing here needs Android: the checks give a [WordBreaker] of their own.
 */
object WordBreaks {
    private val SPACELESS = setOf(
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.THAI, Character.UnicodeScript.LAO, Character.UnicodeScript.KHMER, Character.UnicodeScript.MYANMAR,
    )

    /** A letter of a script written without spaces between words. */
    fun spaceless(codePoint: Int) = runCatching { Character.UnicodeScript.of(codePoint) in SPACELESS }.getOrDefault(false)

    /** [text] has a letter of a script written without spaces, so its words need [WordBreaker]'s breaks. */
    fun anySpaceless(text: CharSequence) = text.codePoints().anyMatch(::spaceless)

    /**
     * Where the last word of [text] starts, for a word delete: the last piece that is a word, with the marks after it (a full
     * stop goes with its word, as "hello." does), or 0 when [text] is only marks.
     */
    fun lastWordStart(text: String, breaker: WordBreaker): Int {
        val pieces = breaker.pieces(text)
        return pieces.lastOrNull { it.word }?.start ?: 0
    }

    /**
     * The word of [text] that a selection from [start] to [end] touches, as a range in [text]: the words it covers, or with a
     * plain cursor the word it's in, else the word that ends at it, else the one that starts at it. Null where it touches none.
     */
    fun wordAt(text: String, start: Int, end: Int, breaker: WordBreaker): Pair<Int, Int>? {
        val words = breaker.pieces(text).filter { it.word }
        if (start != end) {
            val covered = words.filter { it.start < end && it.end > start }
            return if (covered.isEmpty()) null else covered.first().start to covered.last().end
        }
        val word = words.firstOrNull { start > it.start && start < it.end }
            ?: words.lastOrNull { it.end == start }
            ?: words.firstOrNull { it.start == start }
            ?: return null
        return word.start to word.end
    }
}

/** A piece of text between two word breaks, from [start] to [end]: [word] when it's a word, not spaces or marks. */
data class WordPiece(val start: Int, val end: Int, val word: Boolean)

/** A script's word breaks: [IcuWords] on the phone, a stand-in in the checks. */
fun interface WordBreaker {
    /** [text] cut at every word break, in order, the pieces covering all of it. */
    fun pieces(text: String): List<WordPiece>
}
