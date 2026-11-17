package com.titankeys.keyboard

/**
 * What one input method learned from what the user types (owner, 2026-11-16: "it should be learning from the user too"): the
 * words kept, which word followed which, and the words that started sentences. They raise those words in its completions,
 * its starters and its next words. English learns through its own engine; every other input method learns here.
 *
 * A word the input method's list doesn't have counts only from its [UNKNOWN_USES]th use, so a typo typed once never shows.
 * Each kind is bounded: the least used go first. [encode] and [decode] keep it as text, in the `language_learning` store, one
 * key per input method: on the phone only, in the backup, and cleared by Reset literally everything, as English's are.
 */
class LanguageLearning {
    private val words = HashMap<String, Int>()
    private val pairs = HashMap<String, HashMap<String, Int>>()
    private val starts = HashMap<String, Int>()

    /** [next] was kept after [previous], or at a sentence's start when [previous] is null. [known]: the list has it. */
    fun kept(previous: String?, next: String, known: Boolean) {
        if (next.isBlank() || next.length > MAX_LENGTH) return
        words.merge(next, if (known) UNKNOWN_USES else 1, Int::plus)
        if (previous == null) starts.merge(next, 1, Int::plus)
        else if (previous.length <= MAX_LENGTH) pairs.getOrPut(previous) { HashMap() }.merge(next, 1, Int::plus)
        trim()
    }

    /** The user took [word] back: one use less. */
    fun forget(word: String) {
        val uses = words[word] ?: return
        if (uses <= 1) words.remove(word) else words[word] = uses - 1
    }

    /** Whether [word] has been used enough to offer. */
    fun knows(word: String) = (words[word] ?: 0) >= UNKNOWN_USES

    /** Learned words that start with [prefix], ignoring case, most used first. */
    fun completions(prefix: String, limit: Int = 3): List<String> {
        val lower = prefix.lowercase()
        return words.filter { (word, uses) -> uses >= UNKNOWN_USES && word.lowercase().startsWith(lower) && word.length > prefix.length }
            .entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    /** The words that followed [previous], most often first. */
    fun next(previous: String, limit: Int = 3): List<String> =
        pairs[previous]?.entries?.filter { knows(it.key) }?.sortedByDescending { it.value }?.take(limit)?.map { it.key }.orEmpty()

    /** The words that started sentences, most often first. */
    fun sentenceStarts(limit: Int = 3): List<String> =
        starts.entries.filter { knows(it.key) }.sortedByDescending { it.value }.take(limit).map { it.key }

    val isEmpty get() = words.isEmpty()

    private fun trim() {
        if (words.size > MAX_WORDS) drop(words, words.size - MAX_WORDS * 9 / 10)
        if (starts.size > MAX_STARTS) drop(starts, starts.size - MAX_STARTS * 9 / 10)
        val pairCount = pairs.values.sumOf { it.size }
        if (pairCount > MAX_PAIRS) {
            val all = pairs.flatMap { (first, nexts) -> nexts.map { (next, uses) -> Triple(first, next, uses) } }.sortedBy { it.third }
            for ((first, next) in all.take(pairCount - MAX_PAIRS * 9 / 10).map { it.first to it.second }) {
                pairs[first]?.remove(next)
                if (pairs[first]?.isEmpty() == true) pairs.remove(first)
            }
        }
    }

    private fun drop(map: HashMap<String, Int>, count: Int) {
        for (key in map.entries.sortedBy { it.value }.take(count).map { it.key }) map.remove(key)
    }

    /** Lines of text: `w word uses`, `s word uses`, `p first next uses`, tab-separated. */
    fun encode(): String = buildString {
        for ((word, uses) in words) append("w\t").append(word).append('\t').append(uses).append('\n')
        for ((word, uses) in starts) append("s\t").append(word).append('\t').append(uses).append('\n')
        for ((first, nexts) in pairs) for ((next, uses) in nexts) {
            append("p\t").append(first).append('\t').append(next).append('\t').append(uses).append('\n')
        }
    }

    companion object {
        const val UNKNOWN_USES = 2
        const val MAX_WORDS = 2000
        const val MAX_STARTS = 200
        const val MAX_PAIRS = 5000
        const val MAX_LENGTH = 48

        /** Reads [encode]'s text; a line it can't read is skipped. */
        fun decode(text: String?): LanguageLearning {
            val learning = LanguageLearning()
            for (line in text.orEmpty().lineSequence()) {
                val parts = line.split('\t')
                when {
                    parts.size == 3 && parts[0] == "w" -> parts[2].toIntOrNull()?.let { learning.words[parts[1]] = it }
                    parts.size == 3 && parts[0] == "s" -> parts[2].toIntOrNull()?.let { learning.starts[parts[1]] = it }
                    parts.size == 4 && parts[0] == "p" -> parts[3].toIntOrNull()?.let { learning.pairs.getOrPut(parts[1]) { HashMap() }[parts[2]] = it }
                }
            }
            return learning
        }
    }
}

/**
 * The words the bar offers in an input method that isn't English: after a word or at a sentence's start, and while a word is
 * typed. The user's own come first, then the input method's list and starters. Where none is offered the bar stays empty: it
 * never shows English instead.
 */
object BarWords {
    const val SHOWN = 3

    /**
     * After a word ends: the words learned after [previous], or at a sentence's start ([previous] null), then the input
     * method's own next words for it from its [table] (J's tables), then the starters.
     */
    fun next(previous: String?, learning: LanguageLearning?, starters: List<String>, capitals: Boolean,
             table: NextWords? = null): List<String> {
        val learned = if (previous == null) learning?.sentenceStarts().orEmpty() else learning?.next(previous).orEmpty()
        val listed = table?.after(previous).orEmpty()
        val words = (learned + listed + starters).distinctBy { it.lowercase() }.take(SHOWN)
        return if (previous == null && capitals) words.map { it.replaceFirstChar { c -> c.titlecase() } } else words
    }

    /** While [prefix] is typed: the learned words that start with it, then [listed], the list's. */
    fun complete(prefix: String, learning: LanguageLearning?, listed: List<String>): List<String> =
        (learning?.completions(prefix).orEmpty() + listed).distinctBy { it.lowercase() }.take(SHOWN)
}

/**
 * An input method's next-word table, `pairs.txt` in its folder (pairs-pt-BR.txt for a variant), as J builds them from the
 * Leipzig Corpora Collection: English's en_pairs.txt format, "first second<TAB>count" lines, '#' lines for the source, and
 * <s> as the first word for a sentence's start. Words in lower case where the script has case. Each first word keeps its
 * [KEPT] commonest next words, commonest first.
 */
class NextWords private constructor(private val next: Map<String, List<String>>) {
    /** The words that most often follow [previous], or start a sentence when it's null. */
    fun after(previous: String?): List<String> = next[previous?.lowercase() ?: START].orEmpty()

    val size get() = next.size

    companion object {
        const val START = "<s>"
        const val KEPT = 8

        fun read(lines: Sequence<String>): NextWords {
            val pairs = HashMap<String, MutableList<Pair<String, Int>>>()
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                val tab = line.indexOf('	')
                val space = line.indexOf(' ')
                if (tab <= 0 || space <= 0 || space > tab) continue
                val count = line.substring(tab + 1).trim().toIntOrNull()?.takeIf { it > 0 } ?: continue
                pairs.getOrPut(line.substring(0, space)) { ArrayList() } += line.substring(space + 1, tab) to count
            }
            return NextWords(pairs.mapValues { (_, nexts) -> nexts.sortedByDescending { it.second }.take(KEPT).map { it.first } })
        }
    }
}
