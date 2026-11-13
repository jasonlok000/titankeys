package com.atlas.keyboard

/**
 * How the strip shows words that are too long for their place, so that words such as "recommendations", "recommend" and
 * "recommended" can still be told apart. In this order:
 * 1. A word shrinks to fit its place, down to [MIN_SCALE] of its size.
 * 2. Only where [fit] may lend room: a word that still does not fit takes room from shorter words beside it, so the places are no
 *    longer the same width. The strip's three words never do this: their places stay where they are, as the middle one is muscle
 *    memory.
 * 3. Last, the word is cut with "…" where it is the same as another word on the strip: a word that starts like another keeps
 *    its end ("…mendations"), and any other word keeps its start ("extraordi…"), so the part where the words differ shows.
 * It needs no Android: [measure] gives the width of a text at full size, in the same units as the widths.
 */
internal object StripFit {
    /** What a place shows: [text], at [scale] of its own size, in a place [width] wide. */
    data class Slot(val text: String, val scale: Float, val width: Float)

    const val MIN_SCALE = .7f
    const val ELLIPSIS = "…"

    /** Inter's x-height, the height of its small letters, as a part of its size: 1118 of its 2048 units, in every weight. */
    const val X_HEIGHT = 1118f / 2048f

    /**
     * Where a line of Inter at [size] pixels has its baseline so that its small letters sit in the middle of a bar whose centre
     * line is at [centre]: the middle of the x-height on it. Centring the font's box instead sets the letters about 3 px low, as
     * Inter's box reaches far above its capitals for accents.
     */
    fun baseline(centre: Float, size: Float) = centre + size * X_HEIGHT / 2

    /**
     * How much more room a one-line text view needs under its text than over it, in pixels, so that centring its box from [ascent]
     * (negative) to [descent] puts its small letters on the view's centre line: the box's middle is that far above the letters'.
     */
    fun lift(ascent: Float, descent: Float, size: Float) = -(ascent + descent) - size * X_HEIGHT

    /**
     * Lays out [words], left to right, across [total], with [padding] on each side of each word. With [lend], a word that does not
     * fit takes room from the others, and an empty word lends its place. Without, every place is the same width.
     */
    fun fit(words: List<String>, total: Float, padding: Float, measure: (String) -> Float, minScale: Float = MIN_SCALE,
            lend: Boolean = true): List<Slot> {
        if (words.isEmpty()) return emptyList()
        val share = total / words.size
        val natural = words.map { if (it.isEmpty()) 0f else measure(it) }
        val full = natural.map { it + 2 * padding }
        val smallest = natural.map { it * minScale + 2 * padding }
        // Words that fit at full size can lend what they do not use. Words that do not fit even when small want more room.
        val spare = words.indices.map { if (full[it] <= share) share - full[it] else 0f }
        val wants = words.indices.map { if (smallest[it] > share) smallest[it] - share else 0f }
        val pool = spare.sum()
        val wanted = wants.sum()
        val widths = FloatArray(words.size) { share }
        if (lend && wanted > 0 && pool > 0) {
            val borrowers = words.indices.filter { wants[it] > 0 }
            // Room left once every word that wants room has enough to fit small grows it back towards full size.
            val leftover = (pool - wanted).coerceAtLeast(0f)
            var taken = 0f
            for (index in borrowers) {
                val given = if (pool >= wanted) wants[index] + minOf(leftover / borrowers.size, full[index] - smallest[index])
                    else pool * wants[index] / wanted
                widths[index] = share + given
                taken += given
            }
            for (index in words.indices) if (spare[index] > 0) widths[index] = share - spare[index] * taken / pool
        }
        return words.indices.map { index ->
            val word = words[index]
            val room = widths[index] - 2 * padding
            when {
                word.isEmpty() || natural[index] <= room -> Slot(word, 1f, widths[index])
                natural[index] * minScale <= room -> Slot(word, room / natural[index], widths[index])
                else -> Slot(cut(word, words.filterIndexed { other, _ -> other != index }, room / minScale, measure), minScale,
                    widths[index])
            }
        }
    }

    /**
     * [word] cut to fit [room] at full size, keeping what tells it apart from [others]. If the start that fits is all shared with
     * another word, the end shows instead.
     */
    fun cut(word: String, others: List<String>, room: Float, measure: (String) -> Float): String {
        if (measure(word) <= room) return word
        var start = word.length
        while (start > 0 && measure(word.take(start) + ELLIPSIS) > room) start--
        val shared = others.filter { it.isNotEmpty() && !it.equals(word, ignoreCase = true) }
            .maxOfOrNull { commonStart(word, it) } ?: 0
        if (shared == 0 || start > shared) return word.take(start) + ELLIPSIS
        var end = word.length
        while (end > 0 && measure(ELLIPSIS + word.takeLast(end)) > room) end--
        return ELLIPSIS + word.takeLast(end)
    }

    private fun commonStart(a: String, b: String): Int {
        var length = 0
        while (length < a.length && length < b.length && a[length].equals(b[length], ignoreCase = true)) length++
        return length
    }
}
