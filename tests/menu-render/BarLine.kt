package com.atlas.keyboard

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The bar on its one line (owner, 2026-11-13: "For the 11dp lower, implement it. Do visual checks with the corner overlays… Check
 * the visuals if they're aligned properly with a simulated border too"), drawn as the Titan 2 Elite shows it: 1076 px across,
 * 1.5625 px to TitanKeys' dp, the bar 88 px at the screen's bottom edge, whose corners are rounded at 100 px. Every bar state, at
 * Smaller, Normal and Bigger, before (the bar's middle, as test-2.1h had it) and after (the line, [StripFit.LINE_DP]): the words
 * with and without tails, long side words, a selected word, the chips and their hint, the automatic capital's arrow, a notice, the
 * clip offer alone and with its hint, every menu rung at rest and hovered, Back, the guide's lit TitanKeys, Exact typing's bar and
 * the Sym panel's band. Each is placed by the strip's own rules ([StripFit], [Corners], [MenuKeys], [MenuScene]) as Strip,
 * ExactBar and SymPanel place it, in their pixels.
 *
 * Each picture is the screen's bottom 200 px, so tools/corner_check.py can run on it as on a screenshot (radius 100, margin 4).
 * It checks, and fails if any breaks, after the change: the middle of every text's small letters is within half a pixel of the
 * line, 39 dp under the bar's top (17 dp over the screen's edge in the Sym panel's band); every box is inside the bar, the pill at
 * least [StripFit.INSIDE_DP] and what shows clearly [StripFit.EDGE_DP]; and nothing that shows comes within 4 px of the screen's
 * outline, its edge or its curve, as corner_check.py finds content. It draws the bar with the black border under the screen too,
 * with guides at the black band's middle and the letters' middle, and the hardest frames before and after, side by side.
 */
internal object BarLine {
    const val WIDTH = 1076
    /** TitanKeys' dp on the Titan 2 Elite, in px: see [FixedScale]. */
    const val UNIT = 1.5625f
    /** The screen's bottom corners, in px, and how far inside its outline what shows must keep: tools/corner_check.py's. */
    const val RADIUS = 100f
    const val MARGIN = 4f
    /** The screen above the bar in each picture: more than a corner's radius, so the picture's top corners stay empty. */
    const val ABOVE = 112
    val BAR = Math.round(StripFit.BAR_DP * UNIT)
    val HEIGHT = ABOVE + BAR
    /** The black border under the screen, in px. */
    val BORDER = StripFit.BORDER_DP * UNIT

    enum class Era { Before, After }

    private val frc = FontRenderContext(null, true, true)
    private lateinit var regular: Font
    private lateinit var medium: Font
    private lateinit var bold: Font
    private lateinit var drawables: File
    private lateinit var strings: String

    private val INK = Color.WHITE
    private val PAPER = Color.BLACK
    private val MUTED = Color(0x88, 0x88, 0x88)
    private val ACCENT get() = Color(Accent.colour and 0xFFFFFF)
    private val ON_ACCENT = Color.BLACK

    private fun width(font: Font, size: Float, text: String) = font.deriveFont(size).getStringBounds(text, frc).width.toFloat()
    private fun faded(colour: Color, alpha: Float) = Color((colour.red * alpha).toInt(), (colour.green * alpha).toInt(), (colour.blue * alpha).toInt())
    private fun string(name: String) = Regex("<string name=\"$name\">([^<]*)</string>").find(strings)!!.groupValues[1].replace("\\'", "'")

    /** Android's FontMetricsInt for Inter at [size] px: its ascent floored and its descent ceiled; with font padding, its whole box. */
    private fun ascent(size: Float) = floor(-size * 1984 / 2048f).toInt()
    private fun descent(size: Float) = ceil(size * 494 / 2048f).toInt()
    private fun padTop(size: Float) = floor(-size * 2269 / 2048f).toInt()
    private fun padBottom(size: Float) = ceil(size * 660 / 2048f).toInt()

    /** A text drawn in a state, with where its small letters' middle is, in px under the bar's top, to check against the line. */
    class Mark(val what: String, val middle: Float, val line: Float)

    /** A box in a state: its bottom, in px under the bar's top, and how far inside the bar it must keep. */
    class Box(val what: String, val bottom: Float, val inside: Float)

    /**
     * One bar state at one size, before or after: Strip's sizes at [size]'s factor, and its rules for [era]. [bar] is the height
     * of what is drawn at the screen's bottom: the strip, Exact typing's bar or the Sym panel's band.
     */
    class Look(val size: FixedScale.Size, val era: Era) {
        val after = era == Era.After
        val density = UNIT * size.factor
        val corners = Corners(RADIUS)
        val line = if (after) StripFit.LINE_DP * UNIT else BAR / 2f
        val edge = StripFit.EDGE_DP * UNIT
        val floor = if (after) BAR - edge else Float.MAX_VALUE
        val inside = StripFit.INSIDE_DP * UNIT
        fun dp(value: Int) = Math.round(value * density)
        val wordPx = 20 * density
        fun baseline(size: Float) = Math.round(if (after) StripFit.baseline(line, size, floor) else StripFit.baseline(BAR / 2f, size)).toFloat()
        fun sideFor(above: Float) = maxOf(dp(8), corners.clearInset(above, edge))
        val side = if (after) sideFor(BAR - (baseline(wordPx) + wordPx * StripFit.DESCENT)) else maxOf(dp(8), corners.inset(dp(16).toFloat()) + dp(4))
        val chipPx = 14 * density
        val chipHeight = 2 * dp(3) + descent(chipPx) - ascent(chipPx)
        val chipTop = if (after) StripFit.boxTopPx(line, dp(3) - ascent(chipPx) - chipPx * StripFit.X_HEIGHT / 2, chipHeight.toFloat(), floor)
            else (BAR - chipHeight) / 2
        val chipSide = if (after) maxOf(side, sideFor((BAR - (chipTop + chipHeight)).toFloat())) else side
        val marks = ArrayList<Mark>()
        val boxes = ArrayList<Box>()
    }

    /** A bar state: [draw] draws it at [Look]'s size and rules on a picture of the screen's bottom, the bar's top at [ABOVE]. */
    class State(val name: String, val caption: String, val draw: (Look, Graphics2D) -> Unit)

    fun render(folder: File, fonts: File, drawables: File, strings: String) {
        fun font(name: String) = Font.createFont(Font.TRUETYPE_FONT, File(fonts, name))
        regular = font("inter_regular.otf")
        medium = font("inter_medium.otf")
        bold = font("inter_bold.otf")
        this.drawables = drawables
        this.strings = strings
        val out = File(folder, "bar-line").apply { mkdirs() }
        val problems = ArrayList<String>()
        val pictures = HashMap<String, BufferedImage>()
        for (era in Era.entries) for (size in FixedScale.Size.entries) {
            val dir = File(out, "${era.name.lowercase()}/${size.name.lowercase()}").apply { mkdirs() }
            for (state in states()) {
                val look = Look(size, era)
                val image = picture(look, state)
                ImageIO.write(image, "png", File(dir, "${state.name}.png"))
                pictures["${era.name}/${size.name}/${state.name}"] = image
                if (era == Era.Before) continue
                val how = "${size.name}, ${state.name}"
                for (mark in look.marks) if (abs(mark.middle - mark.line) > .5f)
                    problems += "$how: ${mark.what}'s small letters' middle is %.2f px off the line".format(mark.middle - mark.line)
                for (box in look.boxes) if (box.bottom > BAR - box.inside + .01f)
                    problems += "$how: ${box.what} reaches %.2f px under the bar's top, inside it by %.2f px, not %.2f".format(box.bottom, BAR - box.bottom, box.inside)
                val risky = risky(image)
                if (risky.isNotEmpty()) problems += "$how: ${risky.size} px that show are within 4 px of the screen's outline, the first at ${risky.first()}"
            }
            println("Bar line, ${era.name.lowercase()}, ${size.name}: ${Look(size, era).let { "sides ${it.side} px, chips ${it.chipSide} px, chips' box ${it.chipTop} to ${it.chipTop + it.chipHeight} px" }}")
        }
        border(out)
        beforeAfter(out, pictures)
        line(out, pictures)
        kotlin.check(problems.isEmpty()) { "The bar's line breaks:\n" + problems.joinToString("\n") }
        println("Every bar state at every size: its text on the line to half a pixel, its boxes inside the bar, nothing within 4 px of the screen's outline")
    }

    /** The screen's bottom with the bar of [state] drawn at its bottom edge. */
    private fun picture(look: Look, state: State): BufferedImage {
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.color = PAPER
        g.fillRect(0, 0, WIDTH, HEIGHT)
        g.translate(0, ABOVE)
        state.draw(look, g)
        g.dispose()
        return image
    }

    private fun text(g: Graphics2D, font: Font, size: Float, text: String, x: Float, baseline: Float, colour: Color) {
        g.color = colour
        g.fill(font.deriveFont(size).createGlyphVector(frc, text).getOutline(x, baseline))
    }

    private fun mark(look: Look, what: String, baseline: Float, size: Float, line: Float = look.line) =
        look.marks.add(Mark(what, baseline - size * StripFit.X_HEIGHT / 2, line))

    // The strip ------------------------------------------------------------------------------------------------------------------

    /** The three words left to right, as Strip lays them out, the best one in the middle in bold; [lit] underlines one of them. */
    private fun words(look: Look, g: Graphics2D, shown: List<String>, lit: Int = -1, measured: Boolean = true) {
        val room = WIDTH - 2f * look.side
        val fits = StripFit.fit(shown, room, (look.dp(6) + look.dp(2)).toFloat(), { width(bold, look.wordPx, it) }, lend = false)
        var left = look.side.toFloat()
        val baseline = look.baseline(look.wordPx)
        for ((index, slot) in fits.withIndex()) {
            val font = if (index == 1) bold else regular
            val size = look.wordPx * slot.scale
            val wide = width(font, size, slot.text)
            val x = left + slot.width / 2 - wide / 2
            text(g, font, size, slot.text, x, baseline, INK)
            if (slot.scale == 1f && slot.text.isNotEmpty() && measured) mark(look, "the word \"${slot.text}\"", baseline, size)
            if (index == lit) underline(look, g, x, x + wide, baseline)
            left += slot.width
        }
    }

    /** The guide's light under a word, as Strip.underline draws it: the accent, 2 dp thick, 3 dp under the baseline. */
    private fun underline(look: Look, g: Graphics2D, left: Float, right: Float, baseline: Float) {
        g.color = ACCENT
        val top = baseline + 3f * look.density
        g.fill(Rectangle2D.Float(left, top, right - left, 2f * look.density))
        look.boxes += Box("the guide's light", top + 2f * look.density, look.edge)
    }

    /** The word the field has selected, alone in the middle, in the words' size and font. */
    private fun selected(look: Look, g: Graphics2D, word: String) {
        val room = WIDTH - 2f * look.side
        val fit = StripFit.fit(listOf(word), room, (look.dp(6) + look.dp(2)).toFloat(), { width(bold, look.wordPx, it) }).single()
        val size = look.wordPx * fit.scale
        val baseline = look.baseline(look.wordPx)
        text(g, regular, size, fit.text, WIDTH / 2f - width(regular, size, fit.text) / 2, baseline, INK)
        if (fit.scale == 1f) mark(look, "the selected word", baseline, size)
    }

    /** The Shift, Alt and Ctrl chips, filled for a held key and an outline for a pushed one, with the hint beside them. */
    private fun chips(look: Look, g: Graphics2D, chips: List<Pair<String, Boolean>>, hint: String) {
        var x = look.chipSide.toFloat()
        val top = look.chipTop.toFloat()
        val radius = 16f * WIDTH / 1080f
        for ((name, held) in chips) {
            val wide = width(regular, look.chipPx, name) + 2 * look.dp(8)
            val shape = RoundRectangle2D.Float(x, top, wide, look.chipHeight.toFloat(), 2 * radius, 2 * radius)
            if (held) {
                g.color = ACCENT
                g.fill(shape)
            } else {
                val stroke = maxOf(2, look.dp(1)).toFloat()
                g.color = ACCENT
                g.stroke = BasicStroke(stroke)
                g.draw(RoundRectangle2D.Float(x + stroke / 2, top + stroke / 2, wide - stroke, look.chipHeight - stroke, 2 * radius, 2 * radius))
            }
            val baseline = top + look.dp(3) - ascent(look.chipPx)
            text(g, regular, look.chipPx, name, x + look.dp(8), baseline, if (held) ON_ACCENT else ACCENT)
            mark(look, "the $name chip", baseline, look.chipPx)
            look.boxes += Box("the $name chip", top + look.chipHeight, look.edge)
            x += wide + look.dp(6)
        }
        // The hint fills the rest, shrinking to fit, its text centred across it and, after, drawn on the line (LineText).
        val right = WIDTH - look.side.toFloat()
        var size = Math.round(18 * look.density).toFloat()
        while (size > Math.round(13 * look.density) && width(regular, size, hint) > right - x) size--
        val voffset = (BAR - (descent(size) - ascent(size))) / 2
        val laid = (voffset - ascent(size)).toFloat()
        val baseline = if (look.after) laid + Math.round(StripFit.baseline(look.line, size, look.floor) - laid) else laid
        text(g, regular, size, hint, x + (right - x) / 2 - width(regular, size, hint) / 2, baseline, INK)
        mark(look, "the hint", baseline, size)
    }

    /** Shift's arrow for an automatic capital, where Shift's chip would be, beside the words. */
    private fun capital(look: Look, g: Graphics2D) {
        words(look, g, listOf("The", "I", "This"))
        val height = look.chipPx * (1984 + 494) / 2048f + 2 * look.dp(3)
        val arrow = height - 2 * look.dp(4)
        val wide = arrow + 2 * look.dp(5)
        val left = look.chipSide.toFloat()
        val top = if (look.after) look.chipTop.toFloat() else (BAR - height) / 2
        val x = left + wide / 2 - arrow / 2
        val y = top + height / 2 - arrow / 2
        val path = Path2D.Float()
        path.moveTo(x + arrow / 2, y)
        path.lineTo(x + arrow, y + arrow * .52f)
        path.lineTo(x + arrow * .73f, y + arrow * .52f)
        path.lineTo(x + arrow * .73f, y + arrow)
        path.lineTo(x + arrow * .27f, y + arrow)
        path.lineTo(x + arrow * .27f, y + arrow * .52f)
        path.lineTo(x, y + arrow * .52f)
        path.closePath()
        g.color = ACCENT
        g.stroke = BasicStroke(1.3f * look.density, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(path)
        look.boxes += Box("the automatic capital's arrow", y + arrow, look.edge)
    }

    /** A notice: the Teams egg's line, 18 dp, in the middle, on its own letters' line as centreLetters puts it. */
    private fun notice(look: Look, g: Graphics2D) {
        val size = 18 * look.density
        val said = string("strip_teams_egg")
        val baseline = look.baseline(size)
        text(g, regular, size, said, WIDTH / 2f - width(regular, size, said) / 2, baseline, INK)
        mark(look, "the notice", baseline, size)
    }

    /**
     * The clip offer: the clipboard icon and the start of the clip at the words' size. Alone it sits on the line (LineText); with
     * its hint the two lines stack in the middle, shrunk to fit if they must, and after, kept the edge's distance over the bottom.
     */
    private fun clip(look: Look, g: Graphics2D, clip: String, hinted: Boolean) {
        val icon = Math.round(look.wordPx * 1.1f)
        val gap = look.dp(8)
        val room = WIDTH - 2f * look.side - 2 * look.dp(6) - icon - gap
        val fit = StripFit.fit(listOf(clip), room, 0f, { width(bold, look.wordPx, it) }).single()
        var size = look.wordPx * fit.scale
        var hintSize = 13 * look.density
        val bottomPad = if (hinted && look.after) Math.round(look.edge) else 0
        if (hinted && !look.after) {
            // As test-2.1h had it: shrunk to the bar's height, from the lines' heights in fractions of a px.
            val shrink = minOf(1f, BAR / (size * (2269 + 660) / 2048f + hintSize * (1984 + 494) / 2048f))
            size *= shrink
            hintSize *= shrink
        } else if (hinted) {
            // As Strip.fitClipLines: the two lines' heights in whole px, shrunk a hundredth at a time until they fit.
            val clipPx = size
            val hintPx = hintSize
            var fit = 1f
            while (padBottom(size) - padTop(size) + descent(hintSize) - ascent(hintSize) > BAR - bottomPad && fit > .7f) {
                fit -= .01f
                size = clipPx * fit
                hintSize = hintPx * fit
            }
        }
        val clipHeight = padBottom(size) - padTop(size)
        val hintHeight = if (hinted) descent(hintSize) - ascent(hintSize) else 0
        val top = (BAR - bottomPad - clipHeight - hintHeight) / 2
        val laid = (top - padTop(size)).toFloat()
        val shift = if (look.after && !hinted) Math.round(StripFit.baseline(look.line - top, size, look.floor - top) - (laid - top)).toFloat() else 0f
        val baseline = laid + shift
        val wide = width(regular, size, fit.text)
        val left = WIDTH / 2f - (icon + gap + wide) / 2
        drawVector(g, File(drawables, "clipboard.xml").readText(), left, top + (clipHeight - icon) / 2 + shift, icon.toFloat(), ACCENT)
        text(g, regular, size, fit.text, left + icon + gap, baseline, INK)
        if (!hinted) mark(look, "the clip", baseline, size)
        if (hinted) {
            val said = string("strip_clip_hint")
            val hintBaseline = (top + clipHeight - ascent(hintSize)).toFloat()
            text(g, regular, hintSize, said, WIDTH / 2f - width(regular, hintSize, said) / 2, hintBaseline, MUTED)
            look.boxes += Box("the clip's hint", hintBaseline + hintSize * StripFit.DESCENT, if (look.after) look.edge else 0f)
        }
    }

    // The word menu --------------------------------------------------------------------------------------------------------------

    private fun label(button: MenuKeys.Button) = string("strip_" + button.name.lowercase())

    /** The word's menu, as Strip.drawMenu draws it: the word, the hovered button's pill, and the icons over I, O and Back. */
    private fun menu(look: Look, g: Graphics2D, word: String, step: MenuKeys.Step, hover: Int = -1, lit: Boolean = false) {
        val density = look.density
        val canvas = AwtMenuCanvas(g, bold)
        val icon = look.dp(22)
        val labelPx = look.wordPx * .88f
        val labelWidth = { button: MenuKeys.Button -> width(medium, labelPx, label(button)) }
        val wordPx = MenuScene.FONT_DP * density
        val baseline = if (look.after) StripFit.baseline(look.line, wordPx, look.floor) else StripFit.baseline(BAR / 2f, wordPx)
        val foot = BAR - (baseline + maxOf(wordPx * 494 / 2048f, if (lit && look.after) 5f * density else 0f))
        val keySide = MenuKeys.slot(WIDTH.toFloat(), 0).first
        val wordLeft = maxOf(look.side.toFloat(), keySide,
            if (look.after) look.corners.clearInset(foot, look.edge).toFloat() else (look.corners.inset(foot) + look.dp(4)).toFloat())
        val wordRight = MenuKeys.wordRight(WIDTH.toFloat(), look.dp(8).toFloat())
        val wordEnd = MenuKeys.wordEnd(WIDTH.toFloat(), look.dp(8).toFloat(), labelWidth, icon.toFloat(), look.dp(18).toFloat(),
            look.dp(12).toFloat(), look.dp(11).toFloat())
        val shown = MenuKeys.shorten(word) { canvas.measure(it) * density <= (wordEnd - wordLeft).coerceAtLeast(0f) }
        val textWidth = canvas.measure(shown) * density
        val centre = MenuKeys.wordCentre(wordLeft, wordRight, wordEnd, textWidth)
        val scene = MenuScene(shown, MenuScene.WordLayout.of(shown, canvas.top(shown)) { canvas.measure(it) }, centre / density, baseline / density, step)
        val choices = MenuKeys.choices(step)
        val hovered = hover.takeIf { MenuKeys.pills(it, step) } ?: -1
        (if (hovered == MenuKeys.UP) choices.i else if (hovered == MenuKeys.DOWN) choices.o else null)?.let(scene::hover)
        repeat(60) { scene.step(1 / 120f) }
        val saved = g.transform
        g.scale(density.toDouble(), density.toDouble())
        scene.paintWord(canvas)
        g.transform = saved
        mark(look, "the menu's word", baseline, wordPx)
        val textLeft = centre - textWidth / 2
        if (lit) underline(look, g, textLeft, textLeft + textWidth, baseline)
        val pillHeight = if (look.after) minOf(look.dp(42).toFloat(), StripFit.around(look.line, BAR.toFloat(), look.inside))
            else minOf(look.dp(42), BAR * 42 / 56).toFloat()
        val middle = if (look.after) look.line else BAR / 2f
        var pill: Pair<Float, Float>? = null
        if (hovered >= 0) {
            val button = (if (hovered == MenuKeys.UP) choices.i else choices.o)!!
            val name = label(button)
            val wide = labelWidth(button)
            pill = MenuKeys.pill(WIDTH.toFloat(), hovered, wide, icon.toFloat(), look.dp(18).toFloat(), look.dp(12).toFloat(), look.dp(11).toFloat())
            val box = RoundRectangle2D.Float(pill.first, middle - pillHeight / 2, pill.second - pill.first, pillHeight, pillHeight, pillHeight)
            val spent = button == MenuKeys.Button.Favourited
            g.color = faded(ACCENT, if (spent) .12f else .24f)
            g.fill(box)
            look.boxes += Box("the pill", middle + pillHeight / 2, look.inside)
            val clip = g.clip
            g.clip(box)
            val labelBaseline = if (look.after) StripFit.baseline(look.line, labelPx, look.floor) else StripFit.baseline(BAR / 2f, labelPx)
            text(g, medium, labelPx, name, MenuKeys.pillLabel(pill.second, wide, icon.toFloat(), look.dp(12).toFloat(), look.dp(11).toFloat()),
                labelBaseline, faded(ACCENT, if (spent) .6f else 1f))
            g.clip = clip
            mark(look, "the pill's \"$name\"", labelBaseline, labelPx)
        }
        val iconTop = if (look.after) minOf(look.line - icon / 2f, BAR - look.inside - icon) else (BAR - icon) / 2f
        for ((index, button) in listOf(choices.i, choices.o, null).withIndex()) {
            val column = MenuKeys.UP + index
            if (button == null && index < 2) continue
            val (left, right) = MenuKeys.slot(WIDTH.toFloat(), column)
            val centreX = (left + right) / 2
            val inPill = column == hovered
            if (pill != null && !inPill && centreX in pill.first..pill.second) continue
            val top = if (column != MenuKeys.BACK || !look.after) iconTop else iconTop - MenuKeys.rise(MenuKeys.BACK_ARROW, icon.toFloat(), centreX, iconTop) { x, y ->
                Corners.clear(RADIUS, x, BAR - y, WIDTH.toFloat(), look.edge)
            }
            val colour = when {
                inPill || button == MenuKeys.Button.Favourited -> ACCENT
                pill != null -> MUTED
                else -> INK
            }
            drawVector(g, File(drawables, "${button?.let { "menu_" + it.name.lowercase() } ?: "panel_back"}.xml").readText(), centreX - icon / 2f, top,
                icon.toFloat(), colour)
            look.boxes += Box("the ${button?.name ?: "Back"} icon", top + icon, look.inside)
        }
    }

    // Exact typing's bar and the Sym panel's band -------------------------------------------------------------------------------

    /**
     * Exact typing's bar, which stays unscaled, as tall as the strip: its first page with Shift locked and Alt on once, or its
     * second, the arrows and keys. After, its labels sit on the line and a chip's box hugs its label inside the bar.
     */
    private fun exact(look: Look, g: Graphics2D, second: Boolean) {
        val height = (StripFit.BAR_DP * UNIT).toInt()
        g.translate(0, BAR - height)
        val textPx = 17 * UNIT
        val pad = (4 * UNIT).toInt()
        val line = StripFit.LINE_DP * UNIT
        val floor = height - look.edge
        val boxHeight = 2 * 4 * UNIT + textPx * (1984 + 494) / 2048f
        val boxTop = StripFit.boxTop(line, 4 * UNIT + textPx * 1984 / 2048f - textPx * StripFit.X_HEIGHT / 2, boxHeight, floor)
        val side = if (look.after) maxOf((8 * UNIT).toInt(), look.corners.clearInset(height - (boxTop + boxHeight), look.edge))
            else maxOf((8 * UNIT).toInt(), look.corners.inset(16 * UNIT) + (4 * UNIT).toInt())
        val cells: List<Pair<String, Int>> = if (second) (listOf("‹", "←", "↑", "↓", "→", "Home", "End", "PgUp", "PgDn") + (1..12).map { "F$it" }).map { it to 0 }
            else listOf("Shift" to 2, "Alt" to 1, "Ctrl" to 0, "Esc" to 0, "Tab" to 0, string("exact_bar_more") to 0)
        var x = side.toFloat()
        val across = (WIDTH - 2f * side) / cells.size
        for ((name, level) in cells) {
            var size = textPx
            val wide = if (second) width(regular, size, name) + 2 * (6 * UNIT).toInt() else across
            if (second && x + wide > WIDTH - side) break
            while (size > 13 * UNIT && width(regular, size, name) > wide - 2 * (6 * UNIT).toInt()) size--
            val laid = (pad + ((height - 2 * pad) - (padBottom(size) - padTop(size))) / 2 - padTop(size)).toFloat()
            val baseline = if (look.after) laid + Math.round(StripFit.baseline(line, size, floor) - laid) else laid
            if (level > 0) {
                val radius = 6 * UNIT
                val top = if (look.after) Math.round(boxTop).toFloat() else 0f
                val bottom = if (look.after) height - Math.round(height - (boxTop + boxHeight)).toFloat() else height.toFloat()
                val shape = RoundRectangle2D.Float(x, top, wide, bottom - top, 2 * radius, 2 * radius)
                g.color = ACCENT
                if (level == 2) g.fill(shape) else {
                    val stroke = maxOf(2f, UNIT.toInt().toFloat())
                    g.stroke = BasicStroke(stroke)
                    g.draw(RoundRectangle2D.Float(x + stroke / 2, top + stroke / 2, wide - stroke, bottom - top - stroke, 2 * radius, 2 * radius))
                }
                look.boxes += Box("Exact typing's $name chip", bottom + (BAR - height), look.edge)
            }
            text(g, regular, size, name, x + wide / 2 - width(regular, size, name) / 2, baseline, if (level == 2) ON_ACCENT else if (level == 1) ACCENT else INK)
            if (size == textPx) look.marks += Mark("Exact typing's \"$name\"", baseline - size * StripFit.X_HEIGHT / 2, line)
            x += wide
        }
        g.translate(0, height - BAR)
    }

    /**
     * The Sym panel's band at the screen's bottom edge: the symbol groups with the first or last one washed, the clipboard's two
     * groups, or the quick settings' title. After, its text sits on the line, 17 dp over the screen's edge.
     */
    private fun band(look: Look, g: Graphics2D, kind: String) {
        val band = Math.round(32 * UNIT)
        g.translate(0, BAR - band)
        val line = band - StripFit.LINE_ABOVE_DP * UNIT
        val floor = band - look.edge
        val panelSide = Math.round(4 * UNIT)
        val tails = band - line - 18 * UNIT * (StripFit.X_HEIGHT / 2 + StripFit.DESCENT)
        val inset = if (look.after) look.corners.clearInset(tails, look.edge) else look.corners.inset(Math.round(6 * UNIT).toFloat())
        fun place(size: Float, text: String, left: Float, right: Float, colour: Color) {
            val laid = ((band - (descent(size) - ascent(size))) / 2 - ascent(size)).toFloat()
            val baseline = if (look.after) laid + Math.round(StripFit.baseline(line, size, floor) - laid) else laid
            text(g, regular, size, text, (left + right) / 2 - width(regular, size, text) / 2, baseline, colour)
            look.marks += Mark("the band's \"$text\"", baseline - size * StripFit.X_HEIGHT / 2 + (BAR - band), line + (BAR - band))
        }
        if (kind == "quick") {
            var size = 18 * UNIT
            val left = panelSide + inset + 8 * UNIT
            val right = WIDTH - left
            while (size > 13 * UNIT && width(regular, size, "Quick settings") > right - left) size--
            place(size, "Quick settings", left, right, INK)
        } else {
            val samples = if (kind == "clipboard") listOf(string("clipboard_group_recent"), string("clipboard_group_pinned"))
                else listOf("€", "→", "±", "§", "…", "¿", "½")
            val open = if (kind == "last") samples.lastIndex else 0
            val left = panelSide + inset.toFloat()
            val across = (WIDTH - 2f * left) / samples.size
            val size = minOf(15f, 18f) * UNIT
            for ((index, sample) in samples.withIndex()) {
                val tabLeft = left + index * across
                if (index == open) {
                    val inset3 = Math.round(3 * UNIT)
                    val lift = if (look.after) Math.round(band / 2f - line) else 0
                    val sideGap = Math.round(4 * UNIT) + inset
                    val side = (look.corners.inset((inset3 + lift).toFloat()) + Math.round(2 * UNIT) - sideGap).coerceAtLeast(0)
                    val l = tabLeft + if (index == 0) side else 0
                    val r = tabLeft + across - if (index == samples.lastIndex) side else 0
                    val radius = 16f * WIDTH / 1080f
                    g.color = faded(ACCENT, .35f)
                    g.fill(RoundRectangle2D.Float(l, (inset3 - lift).toFloat(), r - l, band - 2f * inset3, 2 * radius, 2 * radius))
                    look.boxes += Box("the band's wash", band - (inset3 + lift).toFloat() + (BAR - band), look.inside)
                }
                place(size, sample, tabLeft, tabLeft + across, INK)
            }
        }
        g.translate(0, band - BAR)
    }

    // The states -----------------------------------------------------------------------------------------------------------------

    private const val LONG = "pneumonoultramicroscopicsilicovolcanoconiosis"
    /** Ctrl's shortcuts as ModifierBar.SHORTCUTS says them, which needs Android's key codes to build. */
    private const val SHORTCUTS = "A all  C copy  X cut  V paste  Z undo  Y redo"

    fun states(): List<State> {
        val (blocked, new, saved, favourite) = MenuKeys.Step.entries
        val (up, down, back) = Triple(MenuKeys.UP, MenuKeys.DOWN, MenuKeys.BACK)
        val list = mutableListOf(
            State("words", "Words without tails") { l, g -> words(l, g, listOf("the", "and", "it")) },
            State("words-tails", "Words with tails") { l, g -> words(l, g, listOf("going", "your", "jumpy")) },
            State("words-long", "Long side words with tails, shrunk and cut to their places") { l, g ->
                words(l, g, listOf("psychologically", "the", "photographing"), measured = false)
            },
            State("words-lit", "The guide's lit TitanKeys on the bar") { l, g -> words(l, g, listOf("", "TitanKeys", ""), lit = 1) },
            State("selected", "A selected word, alone") { l, g -> selected(l, g, "playgrounds") },
            State("chips-ctrl", "Ctrl held, and its shortcuts") { l, g -> chips(l, g, listOf("Ctrl" to true), SHORTCUTS) },
            State("chips-alt", "Shift and Alt pushed, and a deep symbol") { l, g -> chips(l, g, listOf("Shift" to false, "Alt" to false), "|") },
            State("capital", "A sentence starts: Shift's arrow in the chips' place") { l, g -> capital(l, g) },
            State("notice", "A notice (the Teams egg)") { l, g -> notice(l, g) },
            State("clip", "The clip offer, alone") { l, g -> clip(l, g, "Meet at the corner of King and Bay at 6, bring the keys", false) },
            State("clip-hint", "The clip offer with its hint, two lines") { l, g -> clip(l, g, "Meet at the corner of King and Bay at 6", true) },
        )
        val rungs = listOf(
            Triple(blocked, -1, "Blocked"), Triple(blocked, up, "Blocked, hovering Unblock"),
            Triple(new, -1, "Not saved"), Triple(new, up, "Not saved, hovering Save"), Triple(new, down, "Not saved, hovering Block"),
            Triple(saved, -1, "Saved"), Triple(saved, up, "Saved, hovering Favourite"), Triple(saved, down, "Saved, hovering Unsave"),
            Triple(favourite, -1, "Favourite"), Triple(favourite, up, "Favourite, hovering Already a favourite"),
            Triple(favourite, down, "Favourite, hovering Unfavourite"), Triple(saved, back, "Saved, hovering Back"),
        )
        for ((index, rung) in rungs.withIndex()) {
            val (step, hover, caption) = rung
            list += State("menu-%02d".format(index + 1), "Menu: $caption") { l, g -> menu(l, g, "whatever", step, hover) }
        }
        list += State("menu-long", "Menu: a long favourite, hovering Already a favourite") { l, g -> menu(l, g, LONG, favourite, up) }
        list += State("menu-lit", "Menu: the guide's lit TitanKeys, a favourite, hovering Already a favourite") { l, g ->
            menu(l, g, "TitanKeys", favourite, up, lit = true)
        }
        list += State("menu-lit-o", "Menu: the guide's lit TitanKeys, a favourite, hovering Unfavourite") { l, g ->
            menu(l, g, "TitanKeys", favourite, down, lit = true)
        }
        list += State("exact", "Exact typing's bar: Shift locked, Alt once") { l, g -> exact(l, g, false) }
        list += State("exact-more", "Exact typing's bar: the arrows and keys") { l, g -> exact(l, g, true) }
        list += State("band-first", "The Sym panel's band: the first group open") { l, g -> band(l, g, "first") }
        list += State("band-last", "The Sym panel's band: the last group open") { l, g -> band(l, g, "last") }
        list += State("band-clipboard", "The Sym panel's band: the clipboard's groups") { l, g -> band(l, g, "clipboard") }
        list += State("band-quick", "The Sym panel's band: the quick settings' title") { l, g -> band(l, g, "quick") }
        return list
    }

    // The checks and the sheets ---------------------------------------------------------------------------------------------------

    /**
     * Pixels that show and come within [MARGIN] of the screen's outline at the bottom of [image], as tools/corner_check.py finds
     * them: content is any pixel whose grey differs from the bar's colour by more than 40, and the outline is the screen's bottom
     * edge, its sides and its corners' curve, drawn [MARGIN] in.
     */
    fun risky(image: BufferedImage): List<String> {
        val found = ArrayList<String>()
        val background = image.getRGB(image.width / 2, image.height - 2)
        val h = image.height
        for (y in ABOVE until h) for (x in 0 until image.width) {
            val rgb = image.getRGB(x, y)
            fun ch(shift: Int) = abs(((rgb shr shift) and 0xFF) - ((background shr shift) and 0xFF))
            if (ch(16) * 299 / 1000 + ch(8) * 587 / 1000 + ch(0) * 114 / 1000 <= 40) continue
            if (!Corners.clear(RADIUS, x + .5f, h - (y + .5f), image.width.toFloat(), MARGIN)) found += "($x, ${y - ABOVE})"
        }
        return found
    }

    /** [image] as corner_check.py shows it: what the screen hides shaded, the safe line, and anything that shows outside it red. */
    fun overlay(image: BufferedImage): BufferedImage {
        val shown = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val background = image.getRGB(image.width / 2, image.height - 2)
        val h = image.height
        for (y in 0 until h) for (x in 0 until image.width) {
            var rgb = image.getRGB(x, y)
            val above = h - (y + .5f)
            val visible = Corners.clear(RADIUS, x + .5f, above, image.width.toFloat(), 0f) && y >= ABOVE - 0
            fun ch(shift: Int) = abs(((rgb shr shift) and 0xFF) - ((background shr shift) and 0xFF))
            val content = ch(16) * 299 / 1000 + ch(8) * 587 / 1000 + ch(0) * 114 / 1000 > 40
            if (y >= ABOVE && !Corners.clear(RADIUS, x + .5f, above, image.width.toFloat(), 0f)) rgb = blend(rgb, 0xFF00A0, .6f)
            if (y >= ABOVE && content && !Corners.clear(RADIUS, x + .5f, above, image.width.toFloat(), MARGIN)) rgb = 0xFF0000
            shown.setRGB(x, y, rgb)
            if (!visible) continue
        }
        // The safe line, MARGIN inside the outline.
        val g = shown.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0, 255, 255)
        g.stroke = BasicStroke(1.5f)
        val path = Path2D.Float()
        val r = RADIUS - MARGIN
        path.moveTo(MARGIN, ABOVE.toFloat())
        path.lineTo(MARGIN, h - MARGIN - r)
        path.quadTo(MARGIN, h - MARGIN, MARGIN + r, h - MARGIN)
        path.lineTo(WIDTH - MARGIN - r, h - MARGIN)
        path.quadTo(WIDTH - MARGIN, h - MARGIN, WIDTH - MARGIN, h - MARGIN - r)
        path.lineTo(WIDTH - MARGIN, ABOVE.toFloat())
        g.draw(path)
        g.dispose()
        return shown
    }

    private fun blend(rgb: Int, to: Int, part: Float): Int {
        fun mix(shift: Int) = (((rgb shr shift) and 0xFF) * (1 - part) + ((to shr shift) and 0xFF) * part).toInt() shl shift
        return mix(16) or mix(8) or mix(0)
    }

    private fun caption(g: Graphics2D, text: String, x: Int, y: Int, size: Float = 22f) {
        g.color = Color(0xAA, 0xAA, 0xAA)
        g.font = regular.deriveFont(size)
        g.drawString(text, x, y)
    }

    /**
     * The bar with the black border under the screen drawn below it, as the phone looks, at every size: guides at the middle of
     * the black band (the bar and the border, blue) and at the middle of the words' small letters (pink, dashed), which should be
     * one line, and the bar's own middle, where the letters were before (grey), with the middle word four times as big under it.
     */
    private fun border(out: File) {
        val border = Math.round(BORDER)
        val rows = FixedScale.Size.entries.map { size ->
            val look = Look(size, Era.After)
            // The phone's black face: the bottom of the screen, the bar on it, and the border under the screen, one black band.
            val face = BufferedImage(WIDTH + 60, ABOVE + BAR + border + 20, BufferedImage.TYPE_INT_RGB)
            val f = face.createGraphics()
            f.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            f.color = Color(0x1C, 0x1C, 0x1C)
            f.fillRect(0, 0, face.width, face.height)
            f.color = Color.BLACK
            f.fillRect(0, 0, face.width, ABOVE + BAR + border)
            val bar = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
            val bg = bar.createGraphics()
            bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            bg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            bg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            bg.color = PAPER
            bg.fillRect(0, 0, WIDTH, HEIGHT)
            bg.translate(0, ABOVE)
            words(look, bg, listOf("going", "your", "jumpy"))
            bg.dispose()
            f.drawImage(bar, 30, 0, null)
            f.dispose()
            val barTop = ABOVE.toFloat()
            val black = barTop + (BAR + BORDER) / 2
            val letters = barTop + look.marks.first().middle
            val middle = barTop + BAR / 2f
            /** The guides and the screen's outline over a picture of [face] drawn at [x], [y], [zoom] times as big, from face px [from]. */
            fun guides(g: Graphics2D, x: Float, y: Float, zoom: Float, from: Float = 0f, across: Float = face.width.toFloat()) {
                fun at(fy: Float) = y + fy * zoom
                fun line(fy: Float, colour: Color, dash: Boolean) {
                    g.color = colour
                    g.stroke = if (dash) BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(10f, 8f), 0f) else BasicStroke(1.5f)
                    g.draw(java.awt.geom.Line2D.Float(x, at(fy), x + across * zoom, at(fy)))
                }
                // The screen's edge and its corners, faint, and the bar's top.
                g.color = Color(0x55, 0x55, 0x55)
                g.stroke = BasicStroke(1f)
                val outline = Path2D.Float()
                val left = x + (30 - from) * zoom
                val right = x + (30 + WIDTH - from) * zoom
                val bottom = at(barTop + BAR)
                outline.moveTo(left, at(0f))
                outline.lineTo(left, bottom - RADIUS * zoom)
                outline.quadTo(left, bottom, left + RADIUS * zoom, bottom)
                outline.lineTo(right - RADIUS * zoom, bottom)
                outline.quadTo(right, bottom, right, bottom - RADIUS * zoom)
                outline.lineTo(right, at(0f))
                val clip = g.clip
                g.clip(Rectangle2D.Float(x, y, across * zoom, face.height * zoom))
                g.draw(outline)
                g.draw(java.awt.geom.Line2D.Float(x, at(barTop), x + across * zoom, at(barTop)))
                line(middle, Color(0x80, 0x80, 0x80), true)
                line(black, Color(0, 200, 255), false)
                line(letters, Color(0xFF, 0x50, 0xC0), true)
                g.clip = clip
            }
            val zoom = 4f
            val from = 30 + WIDTH / 2f - 130
            val wide = 260f
            val top = barTop - 14
            val tall = BAR + border + 22f
            val image = BufferedImage(face.width, 44 + face.height + 16 + (tall * zoom).toInt() + 20, BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = Color(0x1C, 0x1C, 0x1C)
            g.fillRect(0, 0, image.width, image.height)
            caption(g, "${size.name}: black band's middle (blue) %.1f px under the bar's top, letters' middle (pink) %.1f px: %.2f px apart"
                .format(black - barTop, letters - barTop, abs(letters - black)), 16, 30, 20f)
            g.drawImage(face, 0, 44, null)
            guides(g, 0f, 44f, 1f)
            caption(g, "bar's top", 36, 44 + ABOVE - 6, 15f)
            caption(g, "screen's edge; the border under it", 36, 44 + ABOVE + BAR + 18, 15f)
            caption(g, "grey: the bar's middle, where the letters were", 36, 44 + ABOVE - 26, 15f)
            // The middle word, four times as big, pixel for pixel.
            val y = 44f + face.height + 16
            val x = (image.width - wide * zoom) / 2
            val crop = face.getSubimage(from.toInt(), top.toInt(), wide.toInt(), tall.toInt())
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g.drawImage(crop, x.toInt(), y.toInt(), (wide * zoom).toInt(), (tall * zoom).toInt(), null)
            guides(g, x, y - top * zoom, zoom, from, wide)
            g.dispose()
            image
        }
        stack(rows, File(out, "border.png"))
    }

    /** The hardest frames before and after, side by side at Bigger and Normal, each as corner_check.py shows it. */
    private fun beforeAfter(out: File, pictures: Map<String, BufferedImage>) {
        val frames = listOf("words-long" to "Bigger", "words-lit" to "Bigger", "chips-ctrl" to "Bigger", "capital" to "Bigger", "clip-hint" to "Normal",
            "menu-10" to "Bigger", "menu-lit" to "Bigger", "menu-12" to "Bigger", "exact" to "Normal", "band-first" to "Normal")
        val captions = states().associate { it.name to it.caption }
        val rows = frames.map { (name, size) ->
            val before = overlay(pictures["Before/$size/$name"]!!)
            val after = overlay(pictures["After/$size/$name"]!!)
            val image = BufferedImage(2 * WIDTH + 20, 40 + HEIGHT, BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = Color(0x22, 0x22, 0x22)
            g.fillRect(0, 0, image.width, image.height)
            caption(g, "$size, ${captions[name]}: before (the bar's middle)", 16, 28)
            caption(g, "after (the line, 39 dp)", WIDTH + 36, 28)
            g.drawImage(before, 0, 40, null)
            g.drawImage(after, WIDTH + 20, 40, null)
            g.dispose()
            image
        }
        stack(rows, File(out, "before-after.png"))
    }

    /** Every state after, at the three sizes, as corner_check.py shows it, one sheet a size, and before, for comparison. */
    private fun line(out: File, pictures: Map<String, BufferedImage>) {
        for (era in Era.entries) for (size in FixedScale.Size.entries) {
            val rows = states().map { state ->
                val image = BufferedImage(WIDTH, 34 + HEIGHT - 60, BufferedImage.TYPE_INT_RGB)
                val g = image.createGraphics()
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g.color = Color(0x22, 0x22, 0x22)
                g.fillRect(0, 0, image.width, image.height)
                caption(g, "${size.name} ${era.name.lowercase()}: ${state.caption}", 16, 24, 18f)
                g.drawImage(overlay(pictures["${era.name}/${size.name}/${state.name}"]!!).getSubimage(0, 60, WIDTH, HEIGHT - 60), 0, 34, null)
                g.dispose()
                image
            }
            stack(rows, File(out, "sheet-${era.name.lowercase()}-${size.name.lowercase()}.png"))
        }
    }

    private fun stack(rows: List<BufferedImage>, file: File) {
        val sheet = BufferedImage(rows.maxOf { it.width }, rows.sumOf { it.height }, BufferedImage.TYPE_INT_RGB)
        var y = 0
        for (row in rows) {
            sheet.createGraphics().apply { drawImage(row, 0, y, null); dispose() }
            y += row.height
        }
        ImageIO.write(sheet, "png", file)
    }
}
