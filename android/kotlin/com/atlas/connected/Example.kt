package com.atlas.connected

/**
 * One labelled swipe example, as it is stored and sent. It holds only what training the swipe models needs: the path
 * with times from the swipe's start, the touch sizes, the kept word (a dictionary word), the ranker's 18 numbers for
 * each candidate, and which versions and phone made it. It never holds the text around the word.
 */
internal class Example(
    /** How the word was confirmed: "chosen" from the bar, "corrected" to another word, or "kept" as it was. */
    val how: String,
    val word: String,
    val x: FloatArray,
    val y: FloatArray,
    /** Milliseconds from the start of the swipe. */
    val time: FloatArray,
    /** The touchpad's size for each point in sensor steps, round(MotionEvent.getSize() * 255), -1 for a point without one, or null when unknown. */
    val sizes: IntArray?,
    /** The ranker's 18 numbers for each candidate word, without the words. */
    val rows: List<DoubleArray>,
    /** The row of the kept word, or -1 when the ranker did not have it. */
    val kept: Int,
    val app: String,
    val models: String,
    val phone: String,
    val build: String,
) {
    fun line(): String {
        require(how in HOW) { "Unknown example kind" }
        require(x.size == y.size && x.size == time.size && x.isNotEmpty()) { "Bad path" }
        require(sizes == null || sizes.size == x.size && sizes.all { it in -1..255 }) { "Bad sizes" }
        require(rows.all { it.size == RANKER_NUMBERS }) { "The ranker has $RANKER_NUMBERS numbers" }
        require(kept in -1 until rows.size) { "Bad kept row" }
        val out = StringBuilder(64 + x.size * 24 + rows.size * RANKER_NUMBERS * 8)
        out.append("{\"v\":1,\"how\":").append(Json.quote(how))
        out.append(",\"word\":").append(Json.quote(word))
        out.append(",\"x\":").append(x.joinToString(",", "[", "]") { rounded(it) })
        out.append(",\"y\":").append(y.joinToString(",", "[", "]") { rounded(it) })
        out.append(",\"t\":").append(time.joinToString(",", "[", "]") { Math.round(it).toString() })
        out.append(",\"size\":").append(sizes?.joinToString(",", "[", "]") ?: "null")
        out.append(",\"rows\":").append(rows.joinToString(",", "[", "]") { row -> row.joinToString(",", "[", "]") { Json.number(it) } })
        out.append(",\"kept\":").append(kept)
        out.append(",\"app\":").append(Json.quote(app))
        out.append(",\"models\":").append(Json.quote(models))
        out.append(",\"phone\":").append(Json.quote(phone))
        out.append(",\"build\":").append(Json.quote(build))
        return out.append('}').toString()
    }

    companion object {
        const val RANKER_NUMBERS = 18
        val HOW = setOf("chosen", "corrected", "kept")
        /** Longer words are almost never real dictionary words, and could be something pasted. */
        private const val MAX_WORD = 32
        private val WORD = Regex("[\\p{L}][\\p{L}'’-]*")

        /** A word may go into an example only if it looks like one word, and the caller found it in the dictionary. */
        fun wordAllowed(word: String, inDictionary: Boolean): Boolean =
            inDictionary && word.length <= MAX_WORD && WORD.matches(word)

        /** The batch that is sealed and sent: the contributor's random ID and the examples. */
        fun batch(contributor: String, lines: List<String>): String =
            "{\"v\":1,\"contributor\":${Json.quote(contributor)},\"examples\":[${lines.joinToString(",")}]}"

        private fun rounded(value: Float): String = Json.number(Math.round(value * 10000.0) / 10000.0)
    }
}
