package com.titankeys.keyboard

import java.text.Normalizer
import java.util.Locale

/**
 * An input method's word list, in AOSP's `.combined` format, as `english.combined` is: a `dictionary=` line, then
 * ` word=...,f=...` lines. Next-word pairs (lines starting with two spaces) are skipped: input methods other than English have
 * no next word.
 *
 * It answers the bar's question, "which words start like this?", ignoring accents and case, so that "cafe" offers café.
 */
class WordList private constructor(
    private val words: Array<String>,
    private val counts: IntArray,
    /** Each word folded (no accents, lower case), sorted, with [order] giving the word's place in [words]. */
    private val folded: Array<String>,
    private val order: IntArray,
    private val locale: Locale,
) {
    val size get() = words.size

    fun contains(word: String): Boolean = find(fold(word, locale)).any { words[it].equals(word, ignoreCase = true) }

    /**
     * Up to [limit] words that start with [prefix], ignoring accents and case, commonest first. A word that is [prefix] with
     * only its accents changed comes before longer words, so typing "cafe" offers café first. A [prefix] that starts with a
     * capital gets its words with one; all capitals give all capitals.
     */
    fun complete(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val key = fold(prefix, locale)
        val found = find(key).sortedWith(compareBy<Int>({ fold(words[it], locale).length != key.length }, { -counts[it] }))
        val picked = LinkedHashSet<String>()
        for (index in found) {
            if (picked.size >= limit) break
            picked += case(words[index], prefix)
        }
        return picked.toList()
    }

    /** Whether [word] is in the list exactly, ignoring case: quicker than [contains] for a short word with many longer ones. */
    fun has(word: String): Boolean {
        val key = fold(word, locale)
        var at = lowerBound(key)
        while (at < folded.size && folded[at] == key) if (words[order[at++]].equals(word, ignoreCase = true)) return true
        return false
    }

    /** Whether any word starts with [prefix], ignoring accents and case. */
    fun hasPrefix(prefix: String): Boolean {
        val key = fold(prefix, locale)
        val at = lowerBound(key)
        return at < folded.size && folded[at].startsWith(key)
    }

    private fun lowerBound(key: String): Int {
        var low = 0
        var high = folded.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (folded[mid] < key) low = mid + 1 else high = mid
        }
        return low
    }

    /** The indices of the words whose folded form starts with [key]. */
    private fun find(key: String): List<Int> {
        val out = ArrayList<Int>()
        var at = lowerBound(key)
        while (at < folded.size && folded[at].startsWith(key) && out.size < MAX_SCAN) out += order[at++]
        return out
    }

    private fun case(word: String, typed: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase(locale)
        typed.first().isUpperCase() -> word.replaceFirstChar { it.titlecase(locale) }
        else -> word
    }

    companion object {
        /** The most matches looked at for one prefix: enough for a one-letter prefix, where the commonest words come first. */
        private const val MAX_SCAN = 20_000

        /**
         * A word without its accents, in lower case: café and CAFE both fold to cafe. ß stays ß. Only the combining accents
         * (U+0300 to U+036F) go: Thai's vowels and tones and Devanagari's signs are letters of the word, not accents.
         */
        fun fold(word: String, locale: Locale): String =
            Normalizer.normalize(word.lowercase(locale), Normalizer.Form.NFD).filter { it !in '̀'..'ͯ' }

        /** Reads a `.combined` word list. Words marked not_a_word, and next-word lines, are skipped. */
        fun read(lines: Sequence<String>, locale: Locale): WordList {
            val words = ArrayList<String>()
            val counts = ArrayList<Int>()
            for (line in lines) {
                if (!line.startsWith(" word=")) continue
                val fields = line.trim().split(',')
                val word = fields.firstOrNull { it.startsWith("word=") }?.removePrefix("word=") ?: continue
                if (word.isEmpty() || fields.any { it == "not_a_word=true" }) continue
                words += word
                counts += fields.firstOrNull { it.startsWith("f=") }?.removePrefix("f=")?.toIntOrNull() ?: 0
            }
            val foldedWords = words.map { fold(it, locale) }
            val order = foldedWords.indices.sortedBy { foldedWords[it] }.toIntArray()
            return WordList(words.toTypedArray(), counts.toIntArray(), Array(order.size) { foldedWords[order[it]] }, order, locale)
        }
    }
}

/**
 * The word being typed in a script that writes no spaces between words (Thai): the run of letters before the cursor is
 * split into the list's words, the fewest pieces, and the last piece, which may be a word not finished yet, is the one
 * being typed. A letter no word explains is a piece of its own, at a cost, so any run splits.
 */
object WordBreak {
    /** The longest word looked for, and how far back the run is read. */
    const val LONGEST = 24
    const val REACH = 48

    fun last(run: String, isWord: (String) -> Boolean, isPrefix: (String) -> Boolean): String {
        val text = run.takeLast(REACH)
        val n = text.length
        if (n == 0) return ""
        // cost[i]: the cheapest split of text[0, i) into whole words, a stray letter costing 3.
        val cost = IntArray(n + 1) { Int.MAX_VALUE / 2 }
        cost[0] = 0
        for (i in 1..n) {
            cost[i] = cost[i - 1] + 3
            for (j in maxOf(0, i - LONGEST) until i) if (cost[j] + 1 < cost[i] && isWord(text.substring(j, i))) cost[i] = cost[j] + 1
        }
        // The last piece: the longest that starts a word, after the cheapest split of what comes before it.
        var best = n - 1
        var bestCost = Int.MAX_VALUE
        for (j in maxOf(0, n - LONGEST) until n) {
            val piece = text.substring(j)
            if (cost[j] + 1 < bestCost && isPrefix(piece)) {
                best = j
                bestCost = cost[j] + 1
            }
        }
        // Marks belong to the letter before them, so the piece never starts with one.
        while (best > 0 && Character.getType(text[best]).let { it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() }) best--
        return text.substring(best)
    }
}
