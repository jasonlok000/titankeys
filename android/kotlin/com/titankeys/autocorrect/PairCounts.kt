package com.titankeys.autocorrect

// Copied from Numen (GPL-3.0, https://github.com/jlo-aug/numen, commit 7b1391c), which is a modified version of Pastiera.

/**
 * Word-pair counts for sentence-aware correction. Pairs are a sorted LongArray of word numbers searched by binary search, and
 * the words one UTF-8 byte array sorted by their bytes, also searched by binary search: no map of strings, so the table takes
 * a few MB and the compact file (en_pairs.bin, tools/en_pairs_bin.py) loads in one read.
 */
class PairCounts private constructor(
    /** Every word in UTF-8, one after another, sorted by their bytes: word i runs from [wordStart][i] to [wordStart][i + 1]. */
    private val words: ByteArray,
    private val wordStart: IntArray,
    private val keys: LongArray,
    private val counts: IntArray
) {
    val size: Int get() = keys.size

    /** What its arrays take, in bytes. */
    val bytes: Long get() = words.size + 4L * (wordStart.size + counts.size) + 8L * keys.size

    /** How often [second] followed [first]; 0 when the pair is unknown. Inputs are lowercase. */
    fun count(first: String, second: String): Int {
        val a = id(first)
        if (a < 0) return 0
        val b = id(second)
        if (b < 0) return 0
        val index = keys.binarySearch(key(a, b))
        return if (index >= 0) counts[index] else 0
    }

    private fun id(word: String): Int {
        val key = word.toByteArray(Charsets.UTF_8)
        var low = 0
        var high = wordStart.size - 2
        while (low <= high) {
            val mid = (low + high) ushr 1
            val order = compare(words, wordStart[mid], wordStart[mid + 1], key)
            when {
                order < 0 -> low = mid + 1
                order > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    companion object {
        /** Sentence boundaries, as written in the pairs file. */
        const val SENTENCE_START = "<s>"
        const val SENTENCE_END = "</s>"

        // A pair's key packs [first id: 20 bits][second id: 20 bits], so the table sorts as one LongArray in key order.
        // Larger counts are capped and larger vocabularies rejected.
        private const val ID_BITS = 20
        private const val COUNT_BITS = 23
        private const val MAX_ID = (1 shl ID_BITS) - 1
        private const val MAX_COUNT = (1 shl COUNT_BITS) - 1
        private const val VERSION = 1

        private fun key(first: Int, second: Int): Long = (first.toLong() shl ID_BITS) or second.toLong()

        /** Bytes [from] until [to] of [words] against [key], as unsigned values: the order the words are sorted in. */
        private fun compare(words: ByteArray, from: Int, to: Int, key: ByteArray): Int {
            for (k in 0 until minOf(to - from, key.size)) {
                val d = (words[from + k].toInt() and 0xFF) - (key[k].toInt() and 0xFF)
                if (d != 0) return d
            }
            return (to - from) - key.size
        }

        private val UNSIGNED = Comparator<ByteArray> { a, b ->
            for (k in 0 until minOf(a.size, b.size)) {
                val d = (a[k].toInt() and 0xFF) - (b[k].toInt() and 0xFF)
                if (d != 0) return@Comparator d
            }
            a.size - b.size
        }

        /**
         * The compact file: "TKPC", a version, the counts V (words), B (bytes of words) and P (pairs), then the V+1 starts of
         * each word in the bytes, the B bytes, the P keys (sorted) and the P counts, big-endian (tools/en_pairs_bin.py).
         */
        fun readBinary(input: java.io.InputStream): PairCounts {
            val buffer = java.nio.ByteBuffer.wrap(input.readBytes())
            val magic = ByteArray(4).also { buffer.get(it) }
            require(String(magic, Charsets.US_ASCII) == "TKPC") { "Not a pair table" }
            require(buffer.int == VERSION) { "Unknown pair table version" }
            val wordCount = buffer.int
            val byteCount = buffer.int
            val pairCount = buffer.int
            val wordStart = IntArray(wordCount + 1).also { buffer.asIntBuffer().get(it) }
            buffer.position(buffer.position() + 4 * (wordCount + 1))
            val words = ByteArray(byteCount).also { buffer.get(it) }
            val keys = LongArray(pairCount).also { buffer.asLongBuffer().get(it) }
            buffer.position(buffer.position() + 8 * pairCount)
            val counts = IntArray(pairCount).also { buffer.asIntBuffer().get(it) }
            // A damaged file fails here, where the loader catches it, not later while correcting.
            require(wordStart.isNotEmpty() && wordStart[0] == 0 && wordStart.last() == byteCount &&
                (1 until wordStart.size).all { wordStart[it] >= wordStart[it - 1] } &&
                (1 until keys.size).all { keys[it] > keys[it - 1] } &&
                keys.all { (it ushr ID_BITS) < wordCount && (it and MAX_ID.toLong()) < wordCount }) { "A damaged pair table" }
            return PairCounts(words, wordStart, keys, counts)
        }

        /** Parses "first second<TAB>count" lines; '#' lines and malformed lines are skipped. */
        fun parse(lines: Sequence<String>): PairCounts {
            val vocabulary = HashMap<String, Int>()
            val firsts = ArrayList<Int>()
            val seconds = ArrayList<Int>()
            val found = ArrayList<Int>()
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                val tab = line.indexOf('\t')
                val space = line.indexOf(' ')
                if (tab <= 0 || space <= 0 || space > tab) continue
                val count = line.substring(tab + 1).trim().toIntOrNull()?.takeIf { it > 0 } ?: continue
                firsts += vocabulary.getOrPut(line.substring(0, space)) { vocabulary.size }
                seconds += vocabulary.getOrPut(line.substring(space + 1, tab)) { vocabulary.size }
                require(vocabulary.size <= MAX_ID) { "Pair vocabulary too large: ${vocabulary.size}" }
                found += minOf(count, MAX_COUNT)
            }
            // Number the words in the order of their bytes, as the compact file does.
            val sorted = vocabulary.keys.map { it.toByteArray(Charsets.UTF_8) }.sortedWith(UNSIGNED)
            val number = HashMap<String, Int>(sorted.size * 2)
            sorted.forEachIndexed { i, bytes -> number[String(bytes, Charsets.UTF_8)] = i }
            val renumber = IntArray(vocabulary.size)
            for ((word, old) in vocabulary) renumber[old] = number.getValue(word)
            val out = java.io.ByteArrayOutputStream()
            val wordStart = IntArray(sorted.size + 1)
            sorted.forEachIndexed { i, bytes -> out.write(bytes); wordStart[i + 1] = out.size() }
            val packed = LongArray(found.size) {
                (key(renumber[firsts[it]], renumber[seconds[it]]) shl COUNT_BITS) or found[it].toLong()
            }
            packed.sort()
            return PairCounts(
                out.toByteArray(),
                wordStart,
                LongArray(packed.size) { packed[it] ushr COUNT_BITS },
                IntArray(packed.size) { (packed[it] and MAX_COUNT.toLong()).toInt() }
            )
        }
    }
}
