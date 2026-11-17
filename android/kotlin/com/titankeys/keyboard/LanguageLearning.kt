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
    /** Words the user saved from the word menu: known from now on, and they never fade. */
    private val saved = HashSet<String>()
    /** The list's words the user unsaved from the word menu, in lower case: never offered until saved again. */
    private val hidden = HashSet<String>()

    /** Save from the word menu: the word is known at once. */
    fun save(word: String) {
        saved += word
        hidden -= word.lowercase()
    }

    /** Unsave from the word menu: the word is forgotten, and a word of the list is hidden too, so it's never offered. */
    fun unsave(word: String, listed: Boolean) {
        saved -= word
        words.remove(word)
        if (listed) hidden += word.lowercase()
    }

    fun isSaved(word: String) = word in saved

    /** The user's own words in this input method, for Settings: the saved ones and the ones learned, in alphabetical order. */
    fun ownWords(): List<String> = (saved + words.filter { it.value >= UNKNOWN_USES }.keys).distinct().sortedBy { it.lowercase() }

    /** Remove all learned words: everything goes, saved and hidden words too. */
    fun forgetAll() {
        words.clear()
        pairs.clear()
        starts.clear()
        saved.clear()
        hidden.clear()
    }

    /** Remove learned words: what typing taught goes, the saved words and the hidden ones stay. */
    fun forgetLearned() {
        words.keys.retainAll(saved)
        pairs.clear()
        starts.clear()
    }

    fun isHidden(word: String) = word.lowercase() in hidden

    /**
     * [next] was kept after [previous], or at a sentence's start when [previous] is null. [known]: the list has it. Not
     * [paired]: the word alone, after something that isn't a word (a stray letter in Thai).
     */
    fun kept(previous: String?, next: String, known: Boolean, paired: Boolean = true) {
        if (next.isBlank() || next.length > MAX_LENGTH) return
        words.merge(next, if (known) UNKNOWN_USES else 1, Int::plus)
        if (!paired) {
            trim()
            return
        }
        if (previous == null) starts.merge(next, 1, Int::plus)
        else if (previous.length <= MAX_LENGTH) pairs.getOrPut(previous) { HashMap() }.merge(next, 1, Int::plus)
        trim()
    }

    /** The user took [word] back: one use less. */
    fun forget(word: String) {
        val uses = words[word] ?: return
        if (uses <= 1) words.remove(word) else words[word] = uses - 1
    }

    /** Whether [word] has been used enough to offer, or was saved. */
    fun knows(word: String) = word in saved || (words[word] ?: 0) >= UNKNOWN_USES

    /** Learned words that start with [prefix], ignoring case, most used first. */
    fun completions(prefix: String, limit: Int = 3): List<String> {
        val lower = prefix.lowercase()
        val used = words.filter { (word, uses) -> uses >= UNKNOWN_USES && word.lowercase().startsWith(lower) && word.length > prefix.length }
            .entries.sortedByDescending { it.value }.map { it.key }
        val own = saved.filter { it.lowercase().startsWith(lower) && it.length > prefix.length }.sorted()
        return (own + used).distinct().filter { !isHidden(it) }.take(limit)
    }

    /** The words that followed [previous], most often first. */
    fun next(previous: String, limit: Int = 3): List<String> =
        pairs[previous]?.entries?.filter { knows(it.key) }?.sortedByDescending { it.value }?.take(limit)?.map { it.key }.orEmpty()

    /** The words that started sentences, most often first. */
    fun sentenceStarts(limit: Int = 3): List<String> =
        starts.entries.filter { knows(it.key) }.sortedByDescending { it.value }.take(limit).map { it.key }

    val isEmpty get() = words.isEmpty() && saved.isEmpty() && hidden.isEmpty()

    /**
     * [other]'s learning added to this one, as a restore does: each word, start and pair at the larger of its two counts (so
     * restoring the same backup again changes nothing), the saved and the hidden words of both, and a word saved on either side
     * isn't hidden. Then trimmed to the same bounds as typing.
     */
    fun merge(other: LanguageLearning) {
        for ((word, uses) in other.words) words.merge(word, uses, ::maxOf)
        for ((word, uses) in other.starts) starts.merge(word, uses, ::maxOf)
        for ((first, nexts) in other.pairs) {
            val mine = pairs.getOrPut(first) { HashMap() }
            for ((next, uses) in nexts) mine.merge(next, uses, ::maxOf)
        }
        saved += other.saved
        hidden += other.hidden
        hidden -= saved.map { it.lowercase() }.toSet()
        trim()
    }

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
        for (word in saved) append("a\t").append(word).append('\n')
        for (word in hidden) append("h\t").append(word).append('\n')
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
                    parts.size == 2 && parts[0] == "a" -> learning.saved += parts[1]
                    parts.size == 2 && parts[0] == "h" -> learning.hidden += parts[1]
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
     * After a word ends: the words learned after [previous], then the input method's own next words for it from its [table]
     * (J's tables), then the starters. At a sentence's start ([previous] null): the sentences the user started, the starters,
     * then the table's.
     */
    fun next(previous: String?, learning: LanguageLearning?, starters: List<String>, capitals: Boolean,
             table: NextWords? = null, rules: WordRules = WordRules.NONE, spelling: (String) -> String = { it }): List<String> {
        val learned = if (previous == null) learning?.sentenceStarts().orEmpty() else learning?.next(previous).orEmpty()
        // The table keeps its words in lower case: each takes the word list's own spelling, so nouns and names keep their capitals.
        val listed = table?.after(previous).orEmpty().map(spelling)
        // At a sentence's start the starters come before the table's: they're the same corpus's sentence starts, without its
        // datelines, page labels and names (make.py's STARTER_SKIP), where the table counts every sentence as it stands.
        val ordered = if (previous == null) learned + starters + listed else learned + listed + starters
        val words = Favourites.first(ordered.distinctBy { it.lowercase() }
            .filter { rules.usable(it) && learning?.isHidden(it) != true }.take(SHOWN), rules.favourite)
        return if (previous == null && capitals) words.map { it.replaceFirstChar { c -> c.titlecase() } } else words
    }

    /** While [prefix] is typed: the learned words that start with it, then [listed], the list's. */
    fun complete(prefix: String, learning: LanguageLearning?, listed: List<String>, rules: WordRules = WordRules.NONE): List<String> =
        Favourites.first((learning?.completions(prefix).orEmpty() + listed).distinctBy { it.lowercase() }
            .filter { rules.usable(it) && learning?.isHidden(it) != true }.take(SHOWN), rules.favourite)
}

/**
 * An input method's next-word table, `pairs.bin` in its folder (pairs-pt-BR.bin for a variant), as J builds them from the
 * Leipzig Corpora Collection (tools/im/next_words.py). Words in lower case where the script has case, and <s> as the first word
 * for a sentence's start. Each first word keeps its [KEPT] commonest next words, commonest first.
 *
 * Compact, as the phone's heap is small (the OutOfMemoryError of 2026-11-16): every word once, in UTF-8, in one byte array, the
 * first words sorted by their bytes for a binary search, and each first word's next words as word numbers. The largest table,
 * Georgian's, takes a few MB this way, against 19 MB as strings, and loads in one read.
 */
class NextWords private constructor(
    /** Every word in UTF-8, one after another: word i is [words] from [wordStart][i] to [wordStart][i + 1]. */
    private val words: ByteArray,
    private val wordStart: IntArray,
    /** First word i's next words are [nexts] from [nextStart][i] to [nextStart][i + 1]. First words are words 0 until [size]. */
    private val nextStart: IntArray,
    private val nexts: IntArray,
) {
    /** The words that most often follow [previous], or start a sentence when it's null. */
    fun after(previous: String?): List<String> {
        val first = find((previous?.lowercase() ?: START).toByteArray(Charsets.UTF_8))
        if (first < 0) return emptyList()
        return (nextStart[first] until nextStart[first + 1]).map { word(nexts[it]) }
    }

    /** How many words have next words. */
    val size get() = nextStart.size - 1

    /** Each word with next words ("<s>" for a sentence's start), each next word, and its place among them from 0 (D's corrections). */
    fun forEach(action: (first: String, next: String, rank: Int) -> Unit) {
        for (first in 0 until size) {
            val word = word(first)
            for (at in nextStart[first] until nextStart[first + 1]) action(word, word(nexts[at]), at - nextStart[first])
        }
    }

    /** What its arrays take, in bytes: the heap it holds, give or take a few object headers. */
    val bytes get() = words.size + 4L * (wordStart.size + nextStart.size + nexts.size)

    private fun word(i: Int) = String(words, wordStart[i], wordStart[i + 1] - wordStart[i], Charsets.UTF_8)

    private fun find(key: ByteArray): Int {
        var low = 0
        var high = size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val order = compare(mid, key)
            when {
                order < 0 -> low = mid + 1
                order > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    /** Word [i] against [key], byte by byte as unsigned values: the order the tool sorted them in. */
    private fun compare(i: Int, key: ByteArray): Int {
        val start = wordStart[i]
        val length = wordStart[i + 1] - start
        for (k in 0 until minOf(length, key.size)) {
            val d = (words[start + k].toInt() and 0xFF) - (key[k].toInt() and 0xFF)
            if (d != 0) return d
        }
        return length - key.size
    }

    companion object {
        const val START = "<s>"
        const val KEPT = 8
        private const val VERSION = 1

        /** The asset: "TKNW", a version, then the arrays as tools/im/next_words.py's write_binary lays them out. */
        fun readBinary(input: java.io.InputStream): NextWords {
            val bytes = input.readBytes()
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            val magic = ByteArray(4).also { buffer.get(it) }
            require(String(magic, Charsets.US_ASCII) == "TKNW") { "Not a next-word table" }
            require(buffer.int == VERSION) { "Unknown next-word table version" }
            val wordCount = buffer.int
            val firstCount = buffer.int
            val byteCount = buffer.int
            val nextCount = buffer.int
            val wordStart = IntArray(wordCount + 1).also { buffer.asIntBuffer().get(it) }
            buffer.position(buffer.position() + 4 * (wordCount + 1))
            val words = ByteArray(byteCount).also { buffer.get(it) }
            val nextStart = IntArray(firstCount + 1).also { buffer.asIntBuffer().get(it) }
            buffer.position(buffer.position() + 4 * (firstCount + 1))
            val nexts = IntArray(nextCount).also { buffer.asIntBuffer().get(it) }
            // A damaged file fails here, where InputMethodList catches it, not later as a word is looked up in it (review,
            // 2026-11-17: a start past the end loaded, then threw while typing).
            require(starts(wordStart, byteCount) && starts(nextStart, nextCount) && firstCount <= wordCount &&
                nexts.all { it in 0 until wordCount }) { "A damaged next-word table" }
            return NextWords(words, wordStart, nextStart, nexts).also { live[it] = "${it.size} first words" }
        }

        /** [starts] begin at 0, never go back, and end at [end]: a table's offsets, as the tool writes them. */
        internal fun starts(starts: IntArray, end: Int): Boolean =
            starts.isNotEmpty() && starts[0] == 0 && starts.last() == end && (1 until starts.size).all { starts[it] >= starts[it - 1] }

        /** The readable form, English's en_pairs.txt format ("first second<TAB>count" lines), for tests and small tables. */
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
            return of(pairs.mapValues { (_, nexts) -> nexts.sortedByDescending { it.second }.take(KEPT).map { it.first } })
        }

        private fun of(table: Map<String, List<String>>): NextWords {
            val unsigned = Comparator<ByteArray> { a, b ->
                for (k in 0 until minOf(a.size, b.size)) {
                    val d = (a[k].toInt() and 0xFF) - (b[k].toInt() and 0xFF)
                    if (d != 0) return@Comparator d
                }
                a.size - b.size
            }
            val firsts = table.keys.sortedWith(compareBy(unsigned) { it.toByteArray(Charsets.UTF_8) })
            val rest = (table.values.flatten().toSet() - table.keys).sortedWith(compareBy(unsigned) { it.toByteArray(Charsets.UTF_8) })
            val vocabulary = firsts + rest
            val number = vocabulary.withIndex().associate { (i, w) -> w to i }
            val out = java.io.ByteArrayOutputStream()
            val wordStart = IntArray(vocabulary.size + 1)
            vocabulary.forEachIndexed { i, w -> out.write(w.toByteArray(Charsets.UTF_8)); wordStart[i + 1] = out.size() }
            val nextStart = IntArray(firsts.size + 1)
            val nexts = ArrayList<Int>()
            firsts.forEachIndexed { i, w -> table.getValue(w).forEach { nexts += number.getValue(it) }; nextStart[i + 1] = nexts.size }
            return NextWords(out.toByteArray(), wordStart, nextStart, nexts.toIntArray()).also { live[it] = "${it.size} first words" }
        }

        /** Every table still in memory, weakly, as [WordList.alive]: a switch must free the last. */
        private val live = java.util.Collections.synchronizedMap(java.util.WeakHashMap<NextWords, String>())

        /** What [live] holds now: one line for each table still in memory. */
        fun alive(): List<String> = synchronized(live) { live.values.toList() }
    }
}

/**
 * What the user decided about words, for every language: Blocked words and Favourites, one shared set each (most words differ
 * between languages, and one set keeps Settings, the backup and the word menu as they are). D's corrections ask the same.
 */
class WordRules(val blocked: (String) -> Boolean, val favourite: (String) -> Boolean) {
    /** Not blocked: may be offered or fixed to. */
    fun usable(word: String) = !blocked(word)

    companion object {
        val NONE = WordRules({ false }, { false })
    }
}
