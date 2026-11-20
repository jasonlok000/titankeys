package com.titankeys.autocorrect

/**
 * Fix earlier words (owner, 2026-11-20): how far a fix may go back to a word already finished. The keys are TypingSettings'.
 * [SMALL] keeps to what can't change what someone meant: an apostrophe only ("its" to "it's", "were" to "we're"), a slip on a
 * rare word ("dome" to "some"), or a short word that isn't one ("hs" to "has"). It never swaps a common word for another,
 * changes its mind about an earlier fix, or fixes a fix. [BOLD] allows all of that: autocorrect before 3.0.
 */
object EarlierWords {
    const val OFF = "off"
    const val SMALL = "small"
    const val BOLD = "bold"

    /** The most common a word may be and still be a slip in [SMALL]: the second look's own limit for a real word. */
    const val RARE_FREQUENCY = 160
    /** The longest word that may change in [SMALL] although the fix is more than one key away, if it isn't a word. */
    const val SHORT_WORD = 3

    /**
     * Whether a [level] lets the second look change [word] to [fixed]. [fixedBefore] says that autocorrect already changed
     * [word] once. [known] and [frequency] read the word list.
     */
    fun allows(level: String, word: String, fixed: String, fixedBefore: Boolean, known: (String) -> Boolean,
               frequency: (String) -> Int): Boolean {
        if (level == OFF) return false
        if (level != SMALL) return true
        if (apostropheOnly(word, fixed)) return true
        if (fixedBefore) return false
        if (known(word) && frequency(word) >= RARE_FREQUENCY) return false
        return oneKey(word.lowercase(), fixed.lowercase()) || word.length <= SHORT_WORD && !known(word)
    }

    /** "its" and "it's", "were" and "we're": the same letters. */
    fun apostropheOnly(word: String, fixed: String) = word.replace("'", "").replace("’", "")
        .equals(fixed.replace("'", "").replace("’", ""), ignoreCase = true)

    /** One key apart: one letter changed, added or left out, or two side by side swapped. */
    fun oneKey(a: String, b: String): Boolean {
        if (a == b) return false
        if (a.length == b.length) {
            val differ = a.indices.filter { a[it] != b[it] }
            return differ.size == 1 || differ.size == 2 && differ[1] == differ[0] + 1 && a[differ[0]] == b[differ[1]] && a[differ[1]] == b[differ[0]]
        }
        val (short, long) = if (a.length < b.length) a to b else b to a
        if (long.length != short.length + 1) return false
        val at = short.indices.firstOrNull { short[it] != long[it] } ?: short.length
        return short.substring(at) == long.substring(at + 1)
    }
}
