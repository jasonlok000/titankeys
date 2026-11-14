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

    /** Inter's descent, how far its tails reach under the baseline, as a part of its size: 494 of its 2048 units, in every weight. */
    const val DESCENT = 494f / 2048f

    /** Inter's cap height, the height of its capitals, as a part of its size: 1490 of its 2048 units, in every weight. */
    const val CAP_HEIGHT = 1490f / 2048f

    /**
     * Half the height of a caret beside words [size] px big: a caret as tall as their letters, from the top of a capital to the
     * bottom of a tail. Centred on the bar's line, it lines up with the letters there to within 3% of their size.
     */
    fun caretHalf(size: Float) = size * (CAP_HEIGHT + DESCENT) / 2

    /** The bar's height in dp, at every Bar text size: the words, chips and menu in it grow, the bar doesn't. */
    const val BAR_DP = 56f

    /**
     * The black border under the screen's bottom edge, in dp: the owner measured it with a ruler on the Titan 2 Elite, about 22 dp
     * (2026-11-13). Under the bar you see one band of black, the bar and this border, and the bar's text sits in its middle.
     */
    const val BORDER_DP = 22f

    /**
     * The bar's one line, in dp under its top: the middle of the black you see, the bar and the border under it. Everything with
     * text in the bar has the middle of its small letters on it: 39 dp, 11 dp under the bar's own middle (owner, 2026-11-13: "I
     * measured with a ruler and I think 11dp lower is right… the letter's middle is right in the middle").
     */
    const val LINE_DP = (BAR_DP + BORDER_DP) / 2

    /** The same line over the screen's bottom edge, in dp: 17 dp. Exact typing's bar and the Sym panel's band sit there too. */
    const val LINE_ABOVE_DP = BAR_DP - LINE_DP

    /** How far a box (a pill, a chip, a highlight) keeps inside the bar, at least, in dp, so the screen's edge never cuts it. */
    const val INSIDE_DP = 1f

    /**
     * How far anything that shows clearly (letters and their tails, outlines, icons, the guide's light) keeps from the screen's
     * outline, its bottom edge and its curved corners, in dp: 4 px on the Titan 2 Elite, the margin tools/corner_check.py checks.
     * The menu's pill is a faint wash and only keeps [INSIDE_DP].
     */
    const val EDGE_DP = 2.56f

    /**
     * Where a line of Inter at [size] pixels has its baseline so that its small letters sit on a [line]: the middle of the x-height
     * on it. Centring the font's box instead sets the letters about 3 px low, as Inter's box reaches far above its capitals for
     * accents.
     */
    fun baseline(line: Float, size: Float) = line + size * X_HEIGHT / 2

    /**
     * The baseline on the [line], as [baseline] has it, unless the text's tails would then reach under [floor]: then it rises just
     * enough. At the bar's sizes nothing rises; it keeps text whole at a size the bar wasn't made for.
     */
    fun baseline(line: Float, size: Float, floor: Float) = minOf(baseline(line, size), floor - size * DESCENT)

    /**
     * Where a one-line text view starts its text, as its top padding with its gravity at the top, so that its baseline is the
     * line of words [lineSize] px big on the bar's [line] ([baseline], kept over [floor]), in whole pixels. Every word sits on
     * that one line, a long word shrunk to fit too, and never floats up from its neighbours (owner, 2026-11-13: one bar
     * baseline). [ascent] is the text's own ascent as Android rounds it (Paint.FontMetricsInt, negative): its baseline is that far
     * below the top of its text.
     */
    fun top(line: Float, lineSize: Float, ascent: Int, floor: Float = Float.MAX_VALUE) =
        Math.round(baseline(line, lineSize, floor)) + ascent

    /**
     * The top of a box [height] tall whose small letters' middle is [middle] under its top, so that they sit on the [line]: unless
     * that would take the box's bottom under [floor], and then it rises just enough.
     */
    fun boxTop(line: Float, middle: Float, height: Float, floor: Float) = minOf(line - middle, floor - height)

    /** [boxTop] in whole px, for a view's place: the nearest to the line, but never lower than [floor] allows. */
    fun boxTopPx(line: Float, middle: Float, height: Float, floor: Float) =
        minOf(Math.round(line - middle), kotlin.math.floor(floor - height).toInt())

    /** How high Inter's tallest letters reach over the baseline, the dots of i and j, as a part of its size: 1572 of 2048. */
    const val TALL = 1572f / 2048f

    /**
     * Two lines that stack and can't both sit on the line, the clip offer and its hint: how far to move them, in px, so the middle
     * of their small letters, from the first line's x-height top ([firstXTop]) to the second's baseline ([secondBaseline]), is on
     * the [line], as nearly as fits. Their tallest letters ([highest]) stay under [ceiling], and their tails ([lowest]) over
     * [floor], which wins if both can't (owner, 2026-11-13: "the hint for that should also follow the visible border thing. not the
     * old mathematical center").
     */
    fun pairShift(firstXTop: Float, secondBaseline: Float, highest: Float, lowest: Float, line: Float, ceiling: Float, floor: Float) =
        (line - (firstXTop + secondBaseline) / 2).coerceAtLeast(ceiling - highest).coerceAtMost(floor - lowest)

    /** [pairShift] in whole px, for a view's translation: the nearest, but never taking the tails under [floor]. */
    fun pairShiftPx(firstXTop: Float, secondBaseline: Float, highest: Float, lowest: Float, line: Float, ceiling: Float, floor: Float) =
        minOf(Math.round(pairShift(firstXTop, secondBaseline, highest, lowest, line, ceiling, floor)), kotlin.math.floor(floor - lowest).toInt())

    /**
     * The tallest box that fits round the [line], its middle on it, in a bar [bar] tall and at least [inside] within it: the word
     * menu's pill, and the boxes of Exact typing's bar.
     */
    fun around(line: Float, bar: Float, inside: Float) = 2 * minOf(bar - inside - line, line - inside)

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
