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

    /** The indices of the words whose folded form starts with [key]. */
    private fun find(key: String): List<Int> {
        var low = 0
        var high = folded.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (folded[mid] < key) low = mid + 1 else high = mid
        }
        val out = ArrayList<Int>()
        var at = low
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

        /** A word without its accents, in lower case: café and CAFE both fold to cafe. ß stays ß. */
        fun fold(word: String, locale: Locale): String =
            Normalizer.normalize(word.lowercase(locale), Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }

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
