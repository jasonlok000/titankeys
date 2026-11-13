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

    /**
     * The logo's mark, centred on [x], [y], [side] across its shape, or one [part] of it, with its slash at [line]'s strength. The
     * parts are the mark's own T and K: they split along the diagonal between the T's bar and the K's arm, and each keeps the
     * shared stem, so the logo itself can walk apart into the T and the K of "TitanKeys".
     */
    data class Mark(val x: Float, val y: Float, val side: Float, override val alpha: Float, val part: MarkPart = MarkPart.Whole,
                    val line: Float = alpha) : Piece

    enum class MarkPart { Whole, T, K }

    /** Measures [text] at [size], bold or not, as the screen will draw it. */
    fun interface Measure {
        fun width(text: String, size: Float, bold: Boolean): Float
    }

    const val INK = 0xFF160A04.toInt()
    const val CREAM = 0xFFFFF1E6.toInt()

    // The parts, in ms. These are safe to change.
    /** Each line of the summary comes in this long after the one before, and takes [LINE_IN] to land. */
    const val LINE_GAP = 220L
    const val LINE_IN = 260L
    /** The beat between the summary landing and the first letter lighting. */
    const val READ_BEAT = 1600L
    /** Each first letter lights this long after the one before, and takes [LIGHT_IN] to light. */
    const val LIGHT_GAP = 140L
    const val LIGHT_IN = 220L
    /** The rest of the lines fade, then the letters slide together, and the phrase stays. */
    const val FADE = 450L
    const val GATHER = 950L
    /** Long enough for the joke to land before the logo takes over (owner, 2026-11-13: "give it more time"). */
    const val PHRASE_HOLD = 3600L
    /** The phrase goes, and the logo's parts come in. */
    const val PHRASE_OUT = 380L
    const val MARK_IN = 520L
    const val TK_IN = 360L
    const val TK_HOLD = 300L
    /** The mark walking apart into the name: long enough to follow, as the logo itself becomes the word. */
    const val GROW = 1500L
    const val BUILT_IN = 450L

    /** What the logo grows into: its T and K become the name's, and the rest comes in between and after them. */
    const val NAME = "TitanKeys"
    const val BUILT_FOR = "BUILT FOR TITAN 2 ELITE"
    /**
     * The end's sizes, in dp (owner, 2026-11-13: "all text combined is a bit small"): the name as big as this, the line under it in
     * capitals at the Body size, and the phrase's two lines up to [PHRASE_DP]. The block under the top bar is the name, [BUILT_FOR],
     * [BLOCK_GAP] and the buttons, centred in the room above the grass.
     */
    const val NAME_DP = 64f
    const val BUILT_DP = 19f
    const val PHRASE_DP = 112f
    /** Between the name's lines and the buttons (pre-release, build #8: at 32 dp the name almost touched the first button). */
    const val BLOCK_GAP = 48f
    /** The bottom of the end is the grass's, this tall, a real band (owner's reference): the finale and the end's block keep above it. */
    const val GRASS_DP = 150f
    /** How much bigger a first letter grows as it lights. */
    const val LIT_GROWTH = 1.3f

    // The logo, from android/src/main/res/drawable/atlas_icon_foreground.xml, in its 1080 square.
    const val MARK_PATH = "M757.7,328.4 L760.8,328.4 L602.1,540 L760.8,751.6 L657.8,751.6 L523.4,572.2 L523.4,751.6 L438.8,751.6 L438.8,600.9 Z " +
        "M382.7,328.4 L632,328.4 L568.5,413 L523.4,413 L523.4,507.8 L657.8,328.4 L760.8,328.4 L607.4,532.9 L538.3,592 L523.4,572.2 L523.4,604.7 L438.8,677 L438.8,413 L319.2,413 Z"
    const val LINE_PATH = "M289.8,756.2 L303.6,767.2 L808.6,335.8 L794.8,324.7 Z"
    /** The part of the 1080 square that the mark's shape takes, so it can be sized by its shape. */
    const val MARK_SHAPE = 520f / 1080f
    /** The mark's letters in its 1080 square: their cap top and baseline, the shared stem's left and right, and the T's middle. */
    const val MARK_TOP = 328.4f
    const val MARK_BASE = 751.6f
    const val STEM_LEFT = 438.8f
    const val STEM_RIGHT = 523.4f
    const val T_MIDDLE = 481.1f
    /**
     * The mark's T and K, as clips in its 1080 square. They part along the line midway between the T bar's slanted end and the K
     * arm's parallel edge, which meets the stem at y 490.4; the K keeps the stem below the bar too.
     */
    val T_CLIP = floatArrayOf(0f, 0f, 891.2f, 0f, STEM_RIGHT, 490.4f, STEM_RIGHT, 1080f, 0f, 1080f)
    val K_CLIP = floatArrayOf(891.2f, 0f, 1080f, 0f, 1080f, 1080f, STEM_LEFT, 1080f, STEM_LEFT, 420f, STEM_RIGHT, 420f, STEM_RIGHT, 490.4f)
    /** Inter Bold's capitals, as a part of its size, and its K's stem in from the letter's left. */
    const val CAP = .727f
    const val K_STEM = .08f

    private fun count(lines: List<String>) = lines.size
    fun summaryEnd(lines: List<String>) = (count(lines) - 1) * LINE_GAP + LINE_IN
    fun lightStart(lines: List<String>) = summaryEnd(lines) + READ_BEAT
    fun lightEnd(lines: List<String>) = lightStart(lines) + (count(lines) - 1) * LIGHT_GAP + LIGHT_IN
    fun gatherStart(lines: List<String>) = lightEnd(lines) + FADE
    fun phraseEnd(lines: List<String>) = gatherStart(lines) + GATHER + PHRASE_HOLD
    fun markStart(lines: List<String>) = phraseEnd(lines) + PHRASE_OUT
    fun growStart(lines: List<String>) = markStart(lines) + MARK_IN + TK_IN + TK_HOLD
    /** From here the parts give way to the name's own letters, so it ends as crisp type. */
    const val SETTLE = .8f
    /** When the whole show is over and the end's buttons come in. */
    fun total(lines: List<String>) = growStart(lines) + GROW + BUILT_IN

    /** The moments worth a haptic: the phrase locking together, and the name grown. */
    fun beats(lines: List<String>) = listOf(gatherStart(lines) + GATHER, growStart(lines) + GROW)

    /** The name's size in pixels on a screen [width] wide: [NAME_DP], or less if that would crowd the sides. */
    fun nameSize(width: Float, measure: Measure, dp: Float) = min(width * .88f / measure.width(NAME, 100f, true) * 100f * .82f, NAME_DP * dp)

    /** How far the name's block reaches above its baseline (its capitals) and below it (to the bottom of [BUILT_FOR]). */
    private fun nameAbove(nameSize: Float) = nameSize * CAP
    private fun builtBase(nameSize: Float, dp: Float) = nameSize * .3f + BUILT_DP * dp * 1.2f
    private fun nameBelow(nameSize: Float, dp: Float) = builtBase(nameSize, dp) + BUILT_DP * dp * .1f

    /**
     * The end as one block (owner, 2026-11-13: it sat too low): the name, [BUILT_FOR], [BLOCK_GAP], then the buttons, [buttons]
     * tall, in the room from [top] to [bottom], a little above its middle. Returns the name's baseline and the buttons' top, which
     * the screen and the renders both use, so the finale's last frame is where the end's block puts the name.
     */
    fun endBlock(top: Float, bottom: Float, buttons: Float, nameSize: Float, dp: Float): Pair<Float, Float> {
        val above = nameAbove(nameSize)
        val below = nameBelow(nameSize, dp)
        val total = above + below + BLOCK_GAP * dp + buttons
        val first = top + ((bottom - top - total) * .45f).coerceAtLeast(0f)
        val base = first + above
        return base to base + below + BLOCK_GAP * dp
    }

    /** The phrase in two balanced lines: split at the space that keeps the wider line narrowest. */
    fun phraseLines(measure: Measure): List<String> {
        val spaces = ACROSTIC.indices.filter { ACROSTIC[it] == ' ' }
        val at = spaces.minByOrNull { maxOf(measure.width(ACROSTIC.substring(0, it), 100f, true), measure.width(ACROSTIC.substring(it + 1), 100f, true)) }
            ?: return listOf(ACROSTIC)
        return listOf(ACROSTIC.substring(0, at), ACROSTIC.substring(at + 1))
    }

    private fun ease(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
    private fun easeInOut(t: Float) = if (t < .5f) 4 * t * t * t else 1 - (-2 * t + 2).let { it * it * it } / 2
    private fun part(at: Long, from: Long, length: Long) = ((at - from).toFloat() / length).coerceIn(0f, 1f)

    /**
     * What to draw at [at] ms, in a box [width] wide from [top] to [bottom]. [lines] are the summary's lines, whose first letters
     * spell [ACROSTIC]. [dp] is a dp in pixels. [nameBase] is the name's baseline at the end, where the end's block puts it
     * ([endBlock]); without it, the name sits a little under the box's middle.
     */
    fun frame(at: Long, lines: List<String>, width: Float, top: Float, bottom: Float, measure: Measure, dp: Float,
              nameBase: Float = Float.NaN): List<Piece> {
        val pieces = ArrayList<Piece>()
        val side = width * .06f
        val room = width - 2 * side
        val height = bottom - top
        // The summary: one size for every line, as big as fits both ways.
        val widest = lines.maxOf { measure.width(it, 100f, false) }
        val lineSize = min(room / widest * 100f, height / (lines.size * 1.42f)).coerceAtMost(height / 12f)
        val step = lineSize * 1.42f
        val first = top + (height - step * lines.size) / 2 + lineSize
        // The phrase: the letters gathered into the finale's headline, two balanced lines, each centred, as big as fits (owner,
        // 2026-11-13: it was a bit small).
        val phrase = phraseLines(measure)
        val phraseSize = minOf(room / phrase.maxOf { measure.width(it, 100f, true) } * 100f, height * .2f, PHRASE_DP * dp)
        val phraseStep = phraseSize * 1.08f
        val phraseFirst = top + height * .45f - (phraseSize * CAP + phraseStep * (phrase.size - 1)) / 2 + phraseSize * CAP
        // Where each line's first letter goes: the phrase's letters in order, spaces skipped, as a left edge and a baseline.
        val slots = ArrayList<Pair<Float, Float>>()
        for ((row, text) in phrase.withIndex()) {
            val left = (width - measure.width(text, phraseSize, true)) / 2
            for (i in text.indices) if (text[i] != ' ') slots += (left + measure.width(text.substring(0, i), phraseSize, true)) to (phraseFirst + row * phraseStep)
        }
        val phraseOut = part(at, phraseEnd(lines), PHRASE_OUT)
        for ((index, line) in lines.withIndex()) {
            val landing = ease(part(at, index * LINE_GAP, LINE_IN))
            if (landing <= 0f) continue
            val y = first + index * step + (1f - landing) * step * .6f
            val lit = part(at, lightStart(lines) + index * LIGHT_GAP, LIGHT_IN)
            val rest = 1f - part(at, lightEnd(lines), FADE)
            val head = line.take(1)
            // The rest of the line, after its first letter.
            // At rest the line reads as plain text; as its first letter lights and grows, the rest eases right to make room.
            val headWidth = measure.width(head, lineSize, false) +
                (measure.width(head, lineSize * LIT_GROWTH, true) - measure.width(head, lineSize, false)) * lit
            if (rest > 0f) pieces += Text(line.drop(1), side + headWidth, y, lineSize, INK,
                landing * rest * (1f - .45f * lit), false)
            // The first letter: lights up, then slides to its place in the phrase.
            val gather = easeInOut(part(at, gatherStart(lines), GATHER))
            val slot = slots.getOrNull(index)
            val toX = slot?.first ?: side
            val x = side + (toX - side) * gather
            val baseline = y + ((slot?.second ?: phraseFirst) - y) * gather
            val size = lineSize * (1f + (LIT_GROWTH - 1f) * lit) + (phraseSize - lineSize * LIT_GROWTH) * gather
            val colour = if (lit > 0f) CREAM else INK
            val alpha = landing * (1f - phraseOut)
            if (alpha > 0f) pieces += Text(head, x, baseline - phraseOut * height * .08f, size, colour, alpha, lit > .5f || gather > 0f)
        }
        // The logo (owner: "the ending tk animation should be the logo itself"): the real mark comes in, holds, then walks apart into
        // the name, its own T to the T of "TitanKeys" and its own K to the K, shrinking to the name's capitals, while the slash
        // goes and the rest of the name comes in between them. At the end the parts give way to the name's own type.
        val markIn = ease(part(at, markStart(lines), MARK_IN))
        if (markIn > 0f) {
            val markSide = min(width * .5f, height * .38f)
            val bigScale = markSide * (.7f + .3f * markIn) / (1080f * MARK_SHAPE)
            val bigX = width / 2
            val bigY = top + height * .45f
            val nameSize = nameSize(width, measure, dp)
            val baseline = if (nameBase.isNaN()) top + height * .55f else nameBase
            val grow = easeInOut(part(at, growStart(lines), GROW))
            fun w(text: String) = measure.width(text, nameSize, true)
            val left = (width - w(NAME)) / 2
            val kLeft = left + w("Titan")
            // The parts end at the name's capitals: the mark's cap height is the name's.
            val smallScale = CAP * nameSize / (MARK_BASE - MARK_TOP)
            val scale = bigScale + (smallScale - bigScale) * grow
            // Where each part's centre goes, so its stem lands on its letter's stem and its baseline on the name's.
            val endY = baseline - (MARK_BASE - 540f) * smallScale
            val tEndX = left + w("T") / 2 - (T_MIDDLE - 540f) * smallScale
            val kEndX = kLeft + nameSize * K_STEM - (STEM_LEFT - 540f) * smallScale
            val y = bigY + (endY - bigY) * grow
            val settle = ((grow - SETTLE) / (1f - SETTLE)).coerceIn(0f, 1f)
            val side = scale * 1080f * MARK_SHAPE
            val slash = markIn * (1f - (grow / .35f).coerceIn(0f, 1f))
            if (grow <= 0f) pieces += Mark(bigX, bigY, side, markIn)
            else {
                pieces += Mark(bigX + (tEndX - bigX) * grow, y, side, 1f - settle, MarkPart.T, 0f)
                pieces += Mark(bigX + (kEndX - bigX) * grow, y, side, 1f - settle, MarkPart.K, 0f)
                // The slash goes as they part.
                if (slash > 0f) pieces += Mark(bigX, bigY + (endY - bigY) * grow, side, 0f, MarkPart.Whole, slash)
            }
            // The name's other letters come in once the parts are well on their way; its T and K take over as they land.
            val between = ((grow - .5f) / .35f).coerceIn(0f, 1f)
            if (between > 0f) {
                pieces += Text("itan", left + w("T"), baseline, nameSize, INK, between, true)
                pieces += Text("eys", kLeft + w("K"), baseline, nameSize, INK, between, true)
            }
            if (settle > 0f) {
                pieces += Text("T", left, baseline, nameSize, INK, settle, true)
                pieces += Text("K", kLeft, baseline, nameSize, INK, settle, true)
            }
            val built = ease(part(at, growStart(lines) + GROW, BUILT_IN))
            if (built > 0f) {
                // In capitals with the Label style's spacing, at the Body size, under the name (owner, 2026-11-13: too small).
                val size = BUILT_DP * dp
                val spacing = size * .08f
                val wide = measure.width(BUILT_FOR, size, true) + spacing * (BUILT_FOR.length - 1)
                pieces += Text(BUILT_FOR, (width - wide) / 2, baseline + builtBase(nameSize, dp) + (1f - built) * size, size, INK, built * .8f,
                    true, spacing)
            }
        }
        return pieces.filter { it.alpha > 0f || it is Mark && it.line > 0f }
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
