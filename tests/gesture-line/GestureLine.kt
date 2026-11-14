package com.atlas.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The gesture display on the bar's one line (owner, 2026-11-13: "The delete cursor looks visibly too far up not vertically center
 * aligned"), drawn with the real [GestureDrawing] as the Titan 2 Elite shows it, as tests/menu-render/BarLine.kt draws the bar: 1076
 * px across, 1.5625 px to TitanKeys' dp, the bar 88 px at the screen's bottom edge, whose corners are rounded at 100 px, with the
 * words on it placed by the strip's own rules. Keyframes of every gesture at Smaller, Normal and Bigger: steering (double Space),
 * the word drag and the pick (triple Space) with each selection end, the held delete and its pause zone, the lifted hold turning
 * into dots, the flick's dots, a word swipe's path, the Space glow, and the guide's finger.
 *
 * Each frame is the screen's bottom 200 px, so tools/corner_check.py can run on it as on a screenshot (radius 100, margin 4). It
 * checks, and fails if any breaks: every shape the display draws is centred on the line, 39 dp under the bar's top, to half a pixel
 * (a handle's knob sits on top of its handle, and a swipe's rows are the keys'); every caret is as tall as the words' letters, and a
 * word drawn as a bar is as tall as their small letters, at every size; nothing reaches under the floor, 4 px over the screen's
 * edge; and nothing that shows comes within 4 px of the screen's outline, as corner_check.py finds content.
 */
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "gesture-line" }).apply { mkdirs() }
    val fonts = File(args.getOrElse(1) { "android/src/main/res/font" })
    GestureLine.render(out, fonts)
}

internal object GestureLine {
    const val WIDTH = 1076
    const val UNIT = 1.5625f
    const val RADIUS = 100f
    const val MARGIN = 4f
    const val ABOVE = 112
    val BAR = Math.round(StripFit.BAR_DP * UNIT)
    val HEIGHT = ABOVE + BAR
    /** GestureDrawing's own sizes that the checks need: its handle's knob, in dp. run.sh checks they're still the drawing's. */
    const val HANDLE_KNOB = 4.5f
    const val STEER_CARET = 2f
    const val SELECTION_GAP = 6f

    private val frc = FontRenderContext(null, true, true)
    private lateinit var regular: Font
    private lateinit var bold: Font
    private val look = Look(orange = true)
    private val WORDS = listOf("going", "your", "jumpy")

    /** The bar at one Bar text size, as Strip has it: its line, floor, words' size and sides, in px. */
    class Size(val size: FixedScale.Size) {
        val density = UNIT * size.factor
        val line = StripFit.LINE_DP * UNIT
        val edge = StripFit.EDGE_DP * UNIT
        val word = 20 * density
        fun dp(value: Int) = Math.round(value * density)
        val baseline = Math.round(StripFit.baseline(line, word, BAR - edge)).toFloat()
        /** The words' tails, Strip's wordFoot: the lowest the display may reach. */
        val floor = baseline + word * StripFit.DESCENT
        val side = max(dp(8), Corners(RADIUS).clearInset(BAR - floor, edge))
    }

    /** A shape the display drew, in px under the bar's top, before the display's own settle scale, which keeps the line. */
    class Shape(val kind: String, val left: Float, val top: Float, val right: Float, val bottom: Float, val colour: Int) {
        val middle get() = (top + bottom) / 2
        val height get() = bottom - top
    }

    /**
     * The fake Canvas, keeping every shape as it draws it. Rectangles are the cover and the glow, which are not shapes on the line.
     * Without [glow] it leaves the glow out: the light from under the screen's edge, which reaches the edge by design.
     */
    class Recording(image: BufferedImage, private val glow: Boolean, private val paper: Int) : Canvas(image) {
        val shapes = ArrayList<Shape>()
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            if (!glow && (paint.shader != null || (paint.color and 0xFFFFFF) != (paper and 0xFFFFFF))) return
            super.drawRect(left, top, right, bottom, paint)
        }
        override fun drawRoundRect(r: RectF, rx: Float, ry: Float, paint: Paint) {
            shapes += Shape("bar", r.left, r.top, r.right, r.bottom, paint.color)
            super.drawRoundRect(r, rx, ry, paint)
        }
        override fun drawCircle(x: Float, y: Float, radius: Float, paint: Paint) {
            val reach = radius + if (paint.style == Paint.Style.STROKE) paint.strokeWidth / 2 else 0f
            shapes += Shape(if (paint.style == Paint.Style.STROKE) "ring" else "dot", x - reach, y - reach, x + reach, y + reach, paint.color)
            super.drawCircle(x, y, radius, paint)
        }
        override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint) {
            val w = paint.strokeWidth / 2
            shapes += Shape("line", min(x0, x1) - w, min(y0, y1) - w, max(x0, x1) + w, max(y0, y1) + w, paint.color)
            super.drawLine(x0, y0, x1, y1, paint)
        }
    }

    /** A keyframe: [setup] plays a gesture on a new display; [extra] draws what the guide adds, as the finger. */
    class Frame(val name: String, val caption: String, val selectionEnd: SelectionEnd = SelectionEnd.Handle,
                val extra: ((GestureDrawing, Canvas, Size) -> Unit)? = null, val setup: (GestureDisplay) -> Unit)

    fun render(out: File, fonts: File) {
        regular = Font.createFont(Font.TRUETYPE_FONT, File(fonts, "inter_regular.otf"))
        bold = Font.createFont(Font.TRUETYPE_FONT, File(fonts, "inter_bold.otf"))
        val problems = ArrayList<String>()
        val glowing = ArrayList<String>()
        for (size in FixedScale.Size.entries) {
            val s = Size(size)
            val dir = File(out, "frames/${size.name.lowercase()}").apply { mkdirs() }
            // The same frames without the glow, so corner_check.py judges the shapes alone: it takes the bar's colour from the
            // bottom middle, where the Space glow is.
            val bareDir = File(out, "frames-no-glow/${size.name.lowercase()}").apply { mkdirs() }
            val rows = ArrayList<BufferedImage>()
            rows += row(s, "${size.name}: the words at rest", picture(s, null, null).first)
            for (frame in frames()) {
                val display = GestureDisplay()
                val before = GestureDisplay.selectionEnd
                GestureDisplay.selectionEnd = frame.selectionEnd
                frame.setup(display)
                val (image, shapes) = picture(s, display, frame.extra)
                val bare = picture(s, display, frame.extra, glow = false).first
                GestureDisplay.selectionEnd = before
                ImageIO.write(image, "png", File(dir, "${frame.name}.png"))
                ImageIO.write(bare, "png", File(bareDir, "${frame.name}.png"))
                problems += check(s, frame, display, shapes, bare).map { "${size.name}, ${frame.name}: $it" }
                if (risky(image).isNotEmpty() && risky(bare).isEmpty()) glowing += "${size.name} ${frame.name}"
                rows += row(s, "${size.name}, ${frame.caption}", image)
            }
            stack(rows, File(out, "sheet-${size.name.lowercase()}.png"))
            println("${size.name}: line %.2f px, floor %.2f px, words %.2f px, caret %.2f px tall, sides %d px".format(
                s.line, s.floor, s.word, 2 * min(StripFit.caretHalf(s.word), s.floor - s.line), s.side))
        }
        if (glowing.isNotEmpty()) println("The glow along the screen's edge comes within 4 px of it, by design, in: " + glowing.joinToString(", "))
        kotlin.check(problems.isEmpty()) { "The gesture display breaks the bar's line:\n" + problems.joinToString("\n") }
        println("Every gesture at every size: centred on the line to half a pixel, carets as tall as the letters, nothing under the floor, nothing within 4 px of the screen's outline but the glow")
    }

    /** The screen's bottom with the bar at its edge: the words as Strip places them, then the display over them, as Strip draws it. */
    private fun picture(s: Size, display: GestureDisplay?, extra: ((GestureDrawing, Canvas, Size) -> Unit)?, glow: Boolean = true): Pair<BufferedImage, List<Shape>> {
        val bar = BufferedImage(WIDTH, BAR, BufferedImage.TYPE_INT_ARGB)
        val g = bar.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(look.paper, true)
        g.fillRect(0, 0, WIDTH, BAR)
        words(s, g)
        g.dispose()
        val canvas = Recording(bar, glow, look.paper)
        val drawing = GestureDrawing(s.density, Radii.SMALL * s.density)
        if (display != null && display.busy)
            drawing.draw(canvas, display, s.side.toFloat(), (WIDTH - s.side).toFloat(), BAR, look, s.line, s.floor, s.word)
        extra?.invoke(drawing, canvas, s)
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color(look.paper, true)
            fillRect(0, 0, WIDTH, HEIGHT)
            drawImage(bar, 0, ABOVE, null)
            dispose()
        }
        return image to canvas.shapes
    }

    /** The three words, as Strip lays them out: the best one in the middle in bold, all three on the line. */
    private fun words(s: Size, g: Graphics2D) {
        val fits = StripFit.fit(WORDS, WIDTH - 2f * s.side, (s.dp(6) + s.dp(2)).toFloat(), { width(bold, s.word, it) }, lend = false)
        var left = s.side.toFloat()
        for ((index, slot) in fits.withIndex()) {
            val font = if (index == 1) bold else regular
            val size = s.word * slot.scale
            val x = left + slot.width / 2 - width(font, size, slot.text) / 2
            g.color = Color(look.ink, true)
            g.fill(font.deriveFont(size).createGlyphVector(frc, slot.text).getOutline(x, s.baseline))
            left += slot.width
        }
    }

    /** Where the middle word's middle is, as Strip finds it for the guide's finger. */
    private fun middleWord(s: Size): Float {
        val fits = StripFit.fit(WORDS, WIDTH - 2f * s.side, (s.dp(6) + s.dp(2)).toFloat(), { width(bold, s.word, it) }, lend = false)
        return s.side + fits[0].width + fits[1].width / 2
    }

    private fun width(font: Font, size: Float, text: String) = font.deriveFont(size).getStringBounds(text, frc).width.toFloat()

    // The keyframes ------------------------------------------------------------------------------------------------------------

    private const val TEXT = "The quick brown fox jumps over the lazy dog"

    fun frames(): List<Frame> = listOf(
        Frame("steer-rest", "double Space: steering, at rest") { steer(it, direction = 0, speed = 0f, until = 400L) },
        Frame("steer-left", "double Space: steering left") { steer(it, direction = -1, speed = CursorMode.SLOWEST_PER_SECOND * 3) },
        Frame("steer-right-fast", "double Space: steering right, fast") { steer(it, direction = 1, speed = CursorMode.FASTEST_PER_SECOND / 2) },
        Frame("steer-paused", "double Space: paused, the caret on") { d ->
            var now = steer(d, direction = 1, speed = CursorMode.SLOWEST_PER_SECOND * 4)
            repeat(18) { now += 16; d.cursorFrame(5f, 0, 0f, false, now); d.advance(now, 1f) }
        },
        Frame("pick", "triple Space: the word picked") { d -> drag(d, 0, 300L); d.wordSelected(320L, held = true); run(d, 320L, 600L) },
        Frame("drag-right", "triple Space: the drag taking words to the right, the handle") { d -> drag(d, 1, 900L) },
        Frame("drag-left", "triple Space: the drag taking words to the left, the handle") { d -> drag(d, -1, 900L) },
        Frame("drag-caret", "triple Space: the drag, its end as a caret", SelectionEnd.Caret) { d -> drag(d, 1, 900L) },
        Frame("drag-chevron", "triple Space: the drag, its end as a chevron", SelectionEnd.Chevron) { d -> drag(d, 1, 900L) },
        Frame("hold", "held delete: its words and caret") { d -> hold(d, 1_500L) },
        Frame("hold-paused", "held delete: in the pause zone, the caret on") { d ->
            hold(d, 1_500L)
            d.deletePaused(true)
            // Long enough for the pause to show in full, on a beat where the blinking caret is on.
            run(d, 1_500L, 1_548L + 2 * GestureDisplay.BLINK_MILLIS)
        },
        Frame("hold-lifted", "held delete lifted: its words turning into dots") { d -> hold(d, 1_500L); d.deleteEnded(1_516L); run(d, 1_516L, 1_640L) },
        Frame("flick-in", "flick: the dots coming in") { d -> d.wordDeleted(1_000L, 5); run(d, 1_000L, 1_096L) },
        Frame("flick-row", "flick: the dots in a row") { d -> d.wordDeleted(1_000L, 5); d.wordDeleted(1_150L, 4); run(d, 1_000L, 1_300L) },
        Frame("flick-landing", "flick: the dots going into the caret, its pulse") { d -> flickLanding(d) },
        Frame("swipe", "word swipe: the path over the keys") { d -> swipe(d) },
        Frame("space-glow", "Space tapped three times: the glow") { d -> d.spaceTapped(1, 0L); d.spaceTapped(2, 120L); d.spaceTapped(3, 240L); run(d, 240L, 360L) },
        Frame("finger", "the guide's finger on the middle word, coming down", extra = { drawing, canvas, s ->
            drawing.finger(canvas, middleWord(s), s.line, BAR - s.edge, 1f, 0f, look, UNIT) }) { },
        Frame("finger-held", "the guide's finger on the middle word, held", extra = { drawing, canvas, s ->
            drawing.finger(canvas, middleWord(s), s.line, BAR - s.edge, 1f, 1f, look, UNIT) }) { },
    )

    private fun run(d: GestureDisplay, from: Long, to: Long) {
        var now = from
        while (now < to) {
            now = min(now + 16, to)
            d.advance(now, 1f)
        }
    }

    /** A tap, then a held touch that steers through the text from after "The quick", as tests/gesture-render does. */
    private fun steer(d: GestureDisplay, direction: Int, speed: Float, until: Long = 700L): Long {
        d.spaceTapped(1, 0L)
        d.advance(100L, 1f)
        d.cursorFrame(5f, 0, 0f, false, 150L)
        d.cursorText(TEXT.take(9), TEXT.drop(9))
        var now = 150L
        var owed = 0f
        var at = 9
        while (now < until) {
            now += 16
            d.cursorFrame(if (direction == 0) 5f else 5f + direction * 3.5f, direction, speed, false, now)
            owed += speed * 16 / 1000
            while (owed >= 1f && at + direction in 0..TEXT.length) {
                owed -= 1f
                at += direction
                d.cursorStepped(direction)
            }
            d.advance(now, 1f)
        }
        return now
    }

    /** Two taps, then a held touch on "quick" that takes words to the [direction] until [until]. */
    private fun drag(d: GestureDisplay, direction: Int, until: Long) {
        d.spaceTapped(1, 0L)
        d.spaceTapped(2, 150L)
        d.advance(250L, 1f)
        d.cursorFrame(5f, 0, 0f, true, 300L)
        d.wordText("The ", 5, " brown fox jumps over the lazy dog", moreBefore = true)
        var now = 300L
        var stepped = 0
        while (now < until) {
            now += 16
            d.cursorFrame(5f + direction * 3f, direction, if (direction == 0) 0f else CursorMode.SLOWEST_PER_SECOND, true, now)
            if (direction != 0 && now > 400L + stepped * 200L && stepped < 2) {
                d.wordStep(direction)
                stepped++
            }
            d.advance(now, 1f)
        }
    }

    /** A held delete over real text, three words gone by [until]. */
    private fun hold(d: GestureDisplay, until: Long) {
        d.deleteHeld(200, 1_000L, .6f, TEXT)
        d.wordDeleted(1_000L, 3)
        d.wordDeleted(1_200L, 4)
        d.wordDeleted(1_400L, 4)
        run(d, 1_000L, until)
    }

    /** A flick's dots, played until the first dot lands in the caret and it pulses. */
    private fun flickLanding(d: GestureDisplay) {
        d.wordDeleted(1_000L, 5)
        var now = 1_000L
        while (now < 4_000L) {
            now += 16
            d.advance(now, 1f)
            if ((0 until d.dots).any { d.dotTravel(it) in 1f..1.08f }) return
        }
    }

    /** A word swipe across the touchpad: from the top row's left, down to the bottom row and back up the middle. */
    private fun swipe(d: GestureDisplay) {
        val points = listOf(.06f to .05f, .3f to .2f, .52f to .4f, .74f to .7f, .9f to .72f, .6f to .5f, .38f to .36f)
        var now = 0L
        var step = 0
        for (i in 0 until points.size - 1) {
            val (x0, y0) = points[i]
            val (x1, y1) = points[i + 1]
            for (k in 0 until 8) {
                val t = k / 8f
                d.swipePoint(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, step++, now)
                now += 16
                d.advance(now, 1f)
            }
        }
    }

    // The checks ---------------------------------------------------------------------------------------------------------------

    private fun check(s: Size, frame: Frame, d: GestureDisplay, shapes: List<Shape>, image: BufferedImage): List<String> {
        val found = ArrayList<String>()
        if (!d.busy && frame.extra == null) found += "draws nothing"
        // The guide's finger, on the middle word far from the corners, keeps over the bar's own floor; everything else over the
        // words' tails, as the bar's sides keep what reaches that low clear of the corners.
        val floor = if (frame.extra != null) BAR - s.edge else s.floor
        for (shape in shapes) if (shape.bottom > floor + .01f)
            found += "a ${shape.kind} reaches %.2f px under the bar's top, under %.2f px".format(shape.bottom, floor)
        val knob = HANDLE_KNOB * s.density
        val swipe = d.scene == GestureDisplay.Scene.Swipe
        // A swipe's path is centred on the line, as far above it as it may go below, down to the words' tails (owner, 2026-11-13).
        if (swipe) {
            val path = shapes.filter { it.kind == "line" }
            if (path.isEmpty()) found += "draws no path"
            else if (path.minOf { it.top } < s.line - (s.floor - s.line) - .5f)
                found += "its path reaches %.2f px above the line, more than the %.2f px it may go below".format(s.line - path.minOf { it.top }, s.floor - s.line)
        }
        // The chevron is two strokes; together they are centred on the line.
        val lines = shapes.filter { it.kind == "line" }
        if (!swipe && lines.isNotEmpty()) {
            val middle = (lines.minOf { it.top } + lines.maxOf { it.bottom }) / 2
            if (abs(middle - s.line) > .5f) found += "the chevron's middle is %.2f px off the line".format(middle - s.line)
        }
        for (shape in shapes) {
            if (swipe || shape.kind == "line") continue
            // A handle's knob sits on top of its handle, the one thing above the line.
            if (shape.kind == "dot" && abs(shape.height / 2 - knob) < .01f && frame.selectionEnd == SelectionEnd.Handle) continue
            if (abs(shape.middle - s.line) > .5f) found += "a ${shape.kind} at x %.0f is centred %.2f px off the line".format((shape.left + shape.right) / 2, shape.middle - s.line)
        }
        // What stands for text lines up with the words' letters: a caret as tall as they are, a word as tall as its small letters.
        val caret = 2 * min(StripFit.caretHalf(s.word), s.floor - s.line)
        val accent = look.accent and 0xFFFFFF
        val carets = shapes.filter { it.kind == "bar" && (it.colour and 0xFFFFFF) == accent && abs(it.right - it.left - STEER_CARET * s.density) < .01f }
        if (frame.name.startsWith("steer") || frame.name == "hold" || frame.name == "hold-paused" || frame.name == "drag-caret") {
            if (carets.isEmpty()) found += "has no caret"
            for (bar in carets) if (abs(bar.height - caret) > .5f) found += "its caret is %.2f px tall, the letters %.2f px".format(bar.height, caret)
        }
        if (frame.name == "hold" || frame.name.startsWith("drag") || frame.name == "pick") {
            val ink = look.ink and 0xFFFFFF
            val words = shapes.filter { it.kind == "bar" && (it.colour and 0xFFFFFF) == ink }
            if (words.isEmpty()) found += "has no words"
            for (word in words) if (abs(word.height - s.word * StripFit.X_HEIGHT) > .5f)
                found += "a word is %.2f px tall, the small letters %.2f px".format(word.height, s.word * StripFit.X_HEIGHT)
        }
        // The selection keeps the same least gap from the words beside it on both sides (owner, 2026-11-13: it ran into the word
        // on its left).
        if (frame.name == "pick" || frame.name.startsWith("drag")) {
            val box = shapes.filter { it.kind == "bar" && (it.colour and 0xFFFFFF) == accent }.maxByOrNull { it.height }
            val grey = shapes.filter { it.kind == "bar" && (it.colour and 0xFFFFFF) == (look.ink and 0xFFFFFF) }
            if (box == null) found += "has no selection"
            else for (word in grey) {
                val gap = if (word.right <= box.left + .5f) box.left - word.right else word.left - box.right
                if (gap < SELECTION_GAP * s.density - .5f)
                    found += "a word at x %.0f is %.2f px from the selection, under %.2f".format((word.left + word.right) / 2, gap, SELECTION_GAP * s.density)
            }
        }
        // The held delete's words about to go are orange; in the pause zone they're light grey again and its caret is dimmed
        // (owner, 2026-11-13).
        if (frame.name == "hold" || frame.name == "hold-paused") {
            val taking = shapes.filter { it.kind == "bar" && (it.colour and 0xFFFFFF) == accent && it.right - it.left > STEER_CARET * s.density + .01f }
            val caretAlpha = carets.maxOfOrNull { it.colour ushr 24 } ?: 0
            if (frame.name == "hold") {
                if (taking.isEmpty()) found += "has no orange word for the word it's taking"
                if (caretAlpha < 250) found += "its caret is dimmed while it isn't paused: alpha $caretAlpha"
            } else {
                if (taking.isNotEmpty()) found += "has ${taking.size} orange words while paused"
                if (caretAlpha > 120) found += "its caret isn't dimmed while paused: alpha $caretAlpha"
            }
        }
        val risky = risky(image)
        if (risky.isNotEmpty()) found += "${risky.size} px that show are within 4 px of the screen's outline, the first at ${risky.first()}"
        return found
    }

    /**
     * Pixels that show and come within [MARGIN] of the screen's outline, as tools/corner_check.py finds them: any pixel whose grey
     * differs from the bar's black by more than 40, and the outline is the screen's bottom edge, its sides and its corners' curve.
     */
    fun risky(image: BufferedImage): List<String> {
        val found = ArrayList<String>()
        val h = image.height
        for (y in ABOVE until h) for (x in 0 until image.width) {
            val rgb = image.getRGB(x, y)
            val grey = ((rgb shr 16) and 0xFF) * 299 / 1000 + ((rgb shr 8) and 0xFF) * 587 / 1000 + (rgb and 0xFF) * 114 / 1000
            if (grey <= 40) continue
            if (!Corners.clear(RADIUS, x + .5f, h - (y + .5f), image.width.toFloat(), MARGIN)) found += "($x, ${y - ABOVE})"
        }
        return found
    }

    // The sheets ---------------------------------------------------------------------------------------------------------------

    /**
     * A frame on a sheet, as corner_check.py shows it, with the guides: the bar's line (pink, dashed), where the words' small
     * letters have their middle and every shape should too, and the bar's own middle (grey), where the display was before.
     */
    private fun row(s: Size, caption: String, image: BufferedImage): BufferedImage {
        val shown = overlay(image)
        val crop = 60
        val out = BufferedImage(WIDTH, 34 + HEIGHT - crop, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x22, 0x22, 0x22)
        g.fillRect(0, 0, out.width, out.height)
        g.color = Color(0xAA, 0xAA, 0xAA)
        g.font = regular.deriveFont(18f)
        g.drawString(caption, 16, 24)
        g.drawImage(shown.getSubimage(0, crop, WIDTH, HEIGHT - crop), 0, 34, null)
        val top = 34 + ABOVE - crop
        fun guide(y: Float, colour: Color, dash: Boolean) {
            g.color = colour
            g.stroke = if (dash) BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(6f, 6f), 0f) else BasicStroke(1f)
            // Only beside the bar's content, at the far sides, so the guides never hide what they measure.
            g.draw(Line2D.Float(0f, top + y, 40f, top + y))
            g.draw(Line2D.Float(WIDTH - 40f, top + y, WIDTH.toFloat(), top + y))
        }
        guide(BAR / 2f, Color(0x80, 0x80, 0x80), false)
        guide(s.line, Color(0xFF, 0x50, 0xC0), true)
        g.dispose()
        return out
    }

    /** [image] as corner_check.py shows it: what the screen hides shaded, the safe line, and anything that shows outside it red. */
    fun overlay(image: BufferedImage): BufferedImage {
        val shown = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val h = image.height
        for (y in 0 until h) for (x in 0 until image.width) {
            var rgb = image.getRGB(x, y)
            val above = h - (y + .5f)
            val grey = ((rgb shr 16) and 0xFF) * 299 / 1000 + ((rgb shr 8) and 0xFF) * 587 / 1000 + (rgb and 0xFF) * 114 / 1000
            if (y >= ABOVE && !Corners.clear(RADIUS, x + .5f, above, image.width.toFloat(), 0f)) rgb = blend(rgb, 0xFF00A0, .6f)
            if (y >= ABOVE && grey > 40 && !Corners.clear(RADIUS, x + .5f, above, image.width.toFloat(), MARGIN)) rgb = 0xFF0000
            shown.setRGB(x, y, rgb)
        }
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
