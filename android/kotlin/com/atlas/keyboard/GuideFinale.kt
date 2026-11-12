package com.atlas.keyboard

import kotlin.math.min

/**
 * The guide's finale (owner, v5), as a timeline that knows nothing of Android, so the phone and the desktop renders draw the same
 * frames. Four parts, one after the other:
 *  1. The summary: every slide's line, as the user finished it, stacks up, one after the other.
 *  2. Their first letters light up one by one, then slide together into [ACROSTIC], and the rest fades: the prank.
 *  3. The phrase goes, and the logo comes back: the mark, then "TK", which grows into "TitanKeys", then "Built for Titan 2 Elite".
 *  4. It stays there, and the end's buttons come in under it (the screen's own views).
 * [frame] gives what to draw at a time; the colours are the guide's, on its orange end.
 */
internal object GuideFinale {
    /** Something to draw: [Text] with its left edge at [x] and its baseline at [y], or the logo's [Mark] centred on [x] and [y]. */
    sealed interface Piece {
        val alpha: Float
    }

    data class Text(val text: String, val x: Float, val y: Float, val size: Float, val colour: Int, override val alpha: Float,
                    val bold: Boolean, val spacing: Float = 0f) : Piece

    data class Mark(val x: Float, val y: Float, val side: Float, override val alpha: Float) : Piece

    /** Measures [text] at [size], bold or not, as the screen will draw it. */
    fun interface Measure {
        fun width(text: String, size: Float, bold: Boolean): Float
    }

    const val INK = 0xFF160A04.toInt()
    const val CREAM = 0xFFFFF1E6.toInt()
    const val ORANGE = 0xFFEF804E.toInt()

    // The parts, in ms. These are safe to change.
    /** Each line of the summary comes in this long after the one before, and takes [LINE_IN] to land. */
    const val LINE_GAP = 150L
    const val LINE_IN = 260L
    /** The beat between the summary landing and the first letter lighting. */
    const val READ_BEAT = 700L
    /** Each first letter lights this long after the one before, and takes [LIGHT_IN] to light. */
    const val LIGHT_GAP = 140L
    const val LIGHT_IN = 220L
    /** The rest of the lines fade, then the letters slide together, and the phrase stays. */
    const val FADE = 450L
    const val GATHER = 950L
    const val PHRASE_HOLD = 1900L
    /** The phrase goes, and the logo's parts come in. */
    const val PHRASE_OUT = 380L
    const val MARK_IN = 520L
    const val TK_IN = 360L
    const val TK_HOLD = 300L
    const val GROW = 900L
    const val BUILT_IN = 450L

    /** How the logo's name starts, and what it grows into. "T" and "K" stay; the rest comes in between and after them. */
    const val SHORT = "TK"
    const val NAME = "TitanKeys"
    const val BUILT_FOR = "BUILT FOR TITAN 2 ELITE"
    /** How much bigger a first letter grows as it lights. */
    const val LIT_GROWTH = 1.3f

    // The logo, from android/src/main/res/drawable/atlas_icon_foreground.xml, in its 1080 square.
    const val MARK_PATH = "M757.7,328.4 L760.8,328.4 L602.1,540 L760.8,751.6 L657.8,751.6 L523.4,572.2 L523.4,751.6 L438.8,751.6 L438.8,600.9 Z " +
        "M382.7,328.4 L632,328.4 L568.5,413 L523.4,413 L523.4,507.8 L657.8,328.4 L760.8,328.4 L607.4,532.9 L538.3,592 L523.4,572.2 L523.4,604.7 L438.8,677 L438.8,413 L319.2,413 Z"
    const val LINE_PATH = "M289.8,756.2 L303.6,767.2 L808.6,335.8 L794.8,324.7 Z"
    /** The part of the 1080 square that the mark's shape takes, so it can be sized by its shape. */
    const val MARK_SHAPE = 520f / 1080f

    private fun count(lines: List<String>) = lines.size
    fun summaryEnd(lines: List<String>) = (count(lines) - 1) * LINE_GAP + LINE_IN
    fun lightStart(lines: List<String>) = summaryEnd(lines) + READ_BEAT
    fun lightEnd(lines: List<String>) = lightStart(lines) + (count(lines) - 1) * LIGHT_GAP + LIGHT_IN
    fun gatherStart(lines: List<String>) = lightEnd(lines) + FADE
    fun phraseEnd(lines: List<String>) = gatherStart(lines) + GATHER + PHRASE_HOLD
    fun markStart(lines: List<String>) = phraseEnd(lines) + PHRASE_OUT
    fun growStart(lines: List<String>) = markStart(lines) + MARK_IN + TK_IN + TK_HOLD
    /** When the whole show is over and the end's buttons come in. */
    fun total(lines: List<String>) = growStart(lines) + GROW + BUILT_IN

    /** The moments worth a haptic: the phrase locking together, and the name grown. */
    fun beats(lines: List<String>) = listOf(gatherStart(lines) + GATHER, growStart(lines) + GROW)

    private fun ease(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
    private fun easeInOut(t: Float) = if (t < .5f) 4 * t * t * t else 1 - (-2 * t + 2).let { it * it * it } / 2
    private fun part(at: Long, from: Long, length: Long) = ((at - from).toFloat() / length).coerceIn(0f, 1f)

    /**
     * What to draw at [at] ms, in a box [width] wide from [top] to [bottom]. [lines] are the summary's lines, whose first letters
     * spell [ACROSTIC]. [logoTop] is where the logo's part starts, so it can sit higher than the summary.
     */
    fun frame(at: Long, lines: List<String>, width: Float, top: Float, bottom: Float, measure: Measure): List<Piece> {
        val pieces = ArrayList<Piece>()
        val side = width * .06f
        val room = width - 2 * side
        val height = bottom - top
        // The summary: one size for every line, as big as fits both ways.
        val widest = lines.maxOf { measure.width(it, 100f, false) }
        val lineSize = min(room / widest * 100f, height / (lines.size * 1.42f)).coerceAtMost(height / 12f)
        val step = lineSize * 1.42f
        val first = top + (height - step * lines.size) / 2 + lineSize
        // The phrase: the letters gathered, centred, as big as fits.
        val phrase = ACROSTIC
        val phraseSize = min(room / measure.width(phrase, 100f, true) * 100f, height * .16f)
        val phraseLeft = (width - measure.width(phrase, phraseSize, true)) / 2
        val phraseBase = top + height * .45f
        // Where each line's first letter goes in the phrase: the letters of the phrase in order, spaces skipped.
        val slots = phrase.indices.filter { phrase[it] != ' ' }
        val phraseOut = part(at, phraseEnd(lines), PHRASE_OUT)
        for ((index, line) in lines.withIndex()) {
            val landing = ease(part(at, index * LINE_GAP, LINE_IN))
            if (landing <= 0f) continue
            val y = first + index * step + (1f - landing) * step * .6f
            val lit = part(at, lightStart(lines) + index * LIGHT_GAP, LIGHT_IN)
            val rest = 1f - part(at, lightEnd(lines), FADE)
            val head = line.take(1)
            // The rest of the line, after its first letter.
            // The rest keeps the room of its first letter lit, from the start, so the letter never runs into it as it lights.
            if (rest > 0f) pieces += Text(line.drop(1), side + measure.width(head, lineSize * LIT_GROWTH, true), y, lineSize, INK,
                landing * rest * (1f - .45f * lit), false)
            // The first letter: lights up, then slides to its place in the phrase.
            val gather = easeInOut(part(at, gatherStart(lines), GATHER))
            val slot = slots.getOrNull(index)
            val toX = if (slot == null) side else phraseLeft + measure.width(phrase.substring(0, slot), phraseSize, true)
            val x = side + (toX - side) * gather
            val baseline = y + (phraseBase - y) * gather
            val size = lineSize * (1f + (LIT_GROWTH - 1f) * lit) + (phraseSize - lineSize * LIT_GROWTH) * gather
            val colour = if (lit > 0f) CREAM else INK
            val alpha = landing * (1f - phraseOut)
            if (alpha > 0f) pieces += Text(head, x, baseline - phraseOut * height * .08f, size, colour, alpha, lit > 0f || gather > 0f)
        }
        // The logo: the mark, then "TK", which grows into the name, then what it's built for.
        val markIn = ease(part(at, markStart(lines), MARK_IN))
        if (markIn > 0f) {
            val markSide = min(width * .5f, height * .38f)
            pieces += Mark(width / 2, top + height * .3f, markSide * (.7f + .3f * markIn), markIn)
            val nameSize = min(room / measure.width(NAME, 100f, true) * 100f * .82f, height * .13f)
            val baseline = top + height * .74f
            val tkIn = ease(part(at, markStart(lines) + MARK_IN, TK_IN))
            val grow = easeInOut(part(at, growStart(lines), GROW))
            fun w(text: String) = measure.width(text, nameSize, true)
            val startLeft = (width - w(SHORT)) / 2
            val endLeft = (width - w(NAME)) / 2
            val tX = startLeft + (endLeft - startLeft) * grow
            val kX = startLeft + w("T") + (endLeft + w("Titan") - startLeft - w("T")) * grow
            pieces += Text("T", tX, baseline, nameSize, INK, tkIn, true)
            pieces += Text("K", kX, baseline, nameSize, INK, tkIn, true)
            // The letters between come in once the gap between T and K is wide enough for them, so they never overlap.
            val gap = kX - tX - w("T")
            val letters = ((gap / w("itan") - .85f) / .15f).coerceIn(0f, 1f)
            if (letters > 0f) {
                pieces += Text("itan", tX + w("T"), baseline, nameSize, INK, letters, true)
                pieces += Text("eys", kX + w("K"), baseline, nameSize, INK, letters, true)
            }
            val built = ease(part(at, growStart(lines) + GROW, BUILT_IN))
            if (built > 0f) {
                val size = nameSize * .2f
                val spacing = size * .14f
                val wide = measure.width(BUILT_FOR, size, true) + spacing * (BUILT_FOR.length - 1)
                pieces += Text(BUILT_FOR, (width - wide) / 2, baseline + nameSize * .55f + (1f - built) * size, size, INK, built * .8f,
                    true, spacing)
            }
        }
        return pieces.filter { it.alpha > 0f }
    }

    /** Reads the simple moves and lines of the logo's path data, as points per part: each list is one closed shape. */
    fun shapes(data: String): List<List<Pair<Float, Float>>> {
        val out = ArrayList<List<Pair<Float, Float>>>()
        var shape = ArrayList<Pair<Float, Float>>()
        for (part in data.split(' ')) {
            if (part == "Z") {
                out += shape
                shape = ArrayList()
                continue
            }
            val (x, y) = part.substring(1).split(',').map { it.toFloat() }
            shape += x to y
        }
        if (shape.isNotEmpty()) out += shape
        return out
    }

    /** The first letters of [lines], as the acrostic reads them, without spaces: they must spell [ACROSTIC]. */
    fun initials(lines: List<String>) = lines.joinToString("") { it.take(1).uppercase() }

}
