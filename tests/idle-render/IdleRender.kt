package com.atlas.keyboard

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import java.awt.geom.QuadCurve2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The idle games on the bar's line (owner, 2026-11-13: "make sure that its perfect with the new updated visual border rule"), drawn
 * as the Titan 2 Elite shows them. The bar is 88 px at the screen's bottom edge, whose corners are rounded at 100 px, with the 22 dp
 * black border under it, and the words sit on [StripFit.LINE_DP], laid out as [BarLine] lays them out. Each game is the real
 * [IdlePlayer], placed as Strip.startIdle places it, and drawn as [IdleDrawing] draws it, in AWT.
 *
 * Two parts:
 * - The sweep plays every game and every way home, at every Bar text size, on several bars and seeds, and looks at every frame at
 *   60 fps: the outline of every letter as drawn (its own shape, turned and scaled), the figure, the rope, the sparks and the
 *   finish line. It fails if anything that shows comes within 4 px of the screen's outline (its bottom edge or its corners' curve,
 *   as tools/corner_check.py measures), is cut by the bar's top, or jumps between two frames. The floor's line too (owner,
 *   2026-11-13: "let's do 3"): it must show only in a game that uses the floor, never at rest, fade rather than pop, and keep
 *   clear of the outline where it shows.
 * - The keyframes: for each game and its way home, 8 to 12 frames at each size, each as the screen's bottom (for corner_check.py),
 *   and a sheet per game and size with the black border under the screen and the band's middle drawn in.
 *
 * Usage: bash tests/idle-render/run.sh [folder]: with a folder, it draws the keyframes there too.
 */
internal object IdleRender {
    private const val WIDTH = BarLine.WIDTH
    private val BAR = BarLine.BAR
    private const val ABOVE = BarLine.ABOVE
    private val BORDER = BarLine.BORDER
    private const val RADIUS = BarLine.RADIUS
    private const val MARGIN = BarLine.MARGIN
    /** What shows: corner_check.py counts a pixel whose grey differs from black by more than 40. */
    private const val SHOWS = 41f / 255
    private const val FRAME = 1000f / 60
    /**
     * A jump: a letter that shows moves more than [JUMP] px in one frame at 60 fps, and more than three times as far as in the
     * frames before and after. Fast moves, such as the quick way home, are fast throughout and don't count.
     */
    private const val JUMP = 12f

    private val frc = FontRenderContext(null, true, true)
    private lateinit var regular: Font
    private lateinit var bold: Font
    private val INK = Color.WHITE
    private val MUTED = Color(0x88, 0x88, 0x88)
    /** The floor's line: the text's cream at 55%, 1 dp high, its ends fading over 16 dp ([IdleDrawing.floor]). */
    private val CREAM = Color(0xFF, 0xF1, 0xE6)
    private const val FLOOR_ALPHA = 140 / 255f
    private val FLOOR_THICK = 1 * BarLine.UNIT
    private val FLOOR_FADE = 16 * BarLine.UNIT
    /** How bright the cream is, as corner_check.py weighs it, out of 255. */
    private const val CREAM_GREY = (255 * 299 + 0xF1 * 587 + 0xE6 * 114) / 1000f
    private val ACCENT get() = Color(Accent.colour and 0xFFFFFF)

    private fun width(font: Font, size: Float, text: String) = font.deriveFont(size).getStringBounds(text, frc).width.toFloat()

    /**
     * One bar of words at one size: where Strip puts the game, and its letters, as Strip.startIdle makes them; with [arrow], the
     * automatic capital's arrow first, where the chips go, as [BarLine] draws it.
     */
    class Bar(val size: FixedScale.Size, val words: List<String>, val arrow: Boolean = false) {
        val look = BarLine.Look(size, BarLine.Era.After)
        /** The strip is the bar's height, so the game keeps a whole corner's inset and 2 dp from each side. */
        val edge = Corners(RADIUS).inset(0f) + look.dp(2)
        val scale = BAR / Idle.H
        val width = (WIDTH - 2f * edge) / scale
        /** Each word's typeface, left to right: the best word, in the middle, is bold. */
        val bolds = listOf(false, true, false)
        /** The arrow's line, as Strip draws it: 1.3 dp. */
        val arrowLine = 1.3f * look.density
        /** The floor's line, as Strip.drawIdle places it: its bottom at the game's floor, its ends clear of the corners. */
        val floorBottom = Idle.FLOOR * scale
        val floorLeft = Corners(RADIUS).clearInset(BAR - floorBottom, StripFit.EDGE_DP * BarLine.UNIT).toFloat()
        val floorRight = WIDTH - floorLeft

        fun glyphs(): List<Glyph> {
            val room = WIDTH - 2f * look.side
            val fits = StripFit.fit(words, room, (look.dp(6) + look.dp(2)).toFloat(), { width(bold, look.wordPx, it) }, lend = false)
            var left = look.side.toFloat()
            val baseline = look.baseline(look.wordPx)
            val glyphs = ArrayList<Glyph>()
            for ((index, slot) in fits.withIndex()) {
                val font = if (bolds[index]) bold else regular
                val size = look.wordPx * slot.scale
                val wide = width(font, size, slot.text)
                val x = left + slot.width / 2 - wide / 2
                // Android's Paint.ascent() and descent() for Inter: the middle of their box is where a glyph's middle is.
                val middle = baseline + (-size * 1984 / 2048f + size * 494 / 2048f) / 2
                for (i in slot.text.indices) {
                    val char = slot.text[i]
                    if (char.isWhitespace()) continue
                    val at = x + width(font, size, slot.text.substring(0, i))
                    val advance = width(font, size, char.toString())
                    glyphs += Glyph(char, index, (at + advance / 2 - edge) / scale, middle / scale, advance / scale, size / scale)
                }
                left += slot.width
            }
            if (arrow) {
                val height = look.chipPx * (1984 + 494) / 2048f + 2 * look.dp(3)
                val size = height - 2 * look.dp(4)
                val centreX = look.chipSide + (size + 2 * look.dp(5)) / 2
                val centreY = look.chipTop + height / 2
                glyphs.add(0, Glyph(Idle.ARROW, 0, (centreX - edge) / scale, centreY / scale, size / scale, look.wordPx / scale, arrow = true))
            }
            return glyphs
        }
    }

    /** A letter's outline at size 100, its middle at the origin, as IdleDrawing draws it: centred, its box's middle on the point. */
    private val outlines = HashMap<Pair<Boolean, Char>, Shape>()
    private fun outline(bold: Boolean, char: Char): Shape = outlines.getOrPut(bold to char) {
        val font = (if (bold) this.bold else regular).deriveFont(100f)
        val wide = width(if (bold) this.bold else regular, 100f, char.toString())
        font.createGlyphVector(frc, char.toString()).getOutline(-wide / 2, -(-100 * 1984 / 2048f + 100 * 494 / 2048f) / 2)
    }

    /** As IdleDrawing draws it: placed by its box's middle, turned about its ink's middle ([Glyph.pivot]). The arrow is its outline. */
    private fun glyphShape(bar: Bar, g: Glyph): Shape {
        if (g.arrow) {
            val size = g.advance * g.scale
            val x = -size / 2
            val y = -size / 2
            val path = Path2D.Float()
            path.moveTo(x + size / 2, y)
            path.lineTo(x + size, y + size * .52f)
            path.lineTo(x + size * .73f, y + size * .52f)
            path.lineTo(x + size * .73f, y + size)
            path.lineTo(x + size * .27f, y + size)
            path.lineTo(x + size * .27f, y + size * .52f)
            path.lineTo(x, y + size * .52f)
            path.closePath()
            val stroked = BasicStroke(bar.arrowLine / bar.scale * g.scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(path)
            val t = AffineTransform()
            t.translate(bar.edge.toDouble(), 0.0)
            t.scale(bar.scale.toDouble(), bar.scale.toDouble())
            t.translate(g.x.toDouble(), g.y.toDouble())
            if (g.rot != 0f) t.rotate(g.rot.toDouble())
            return t.createTransformedShape(stroked)
        }
        val pivot = g.pivot * g.scale
        val t = AffineTransform()
        t.translate((bar.edge + g.x * bar.scale).toDouble(), ((g.y + pivot) * bar.scale).toDouble())
        if (g.rot != 0f) t.rotate(g.rot.toDouble())
        t.translate(0.0, (-pivot * bar.scale).toDouble())
        val k = g.size * g.scale * bar.scale / 100.0
        t.scale(k, k)
        return t.createTransformedShape(outline(bar.bolds.getOrElse(g.word) { false }, g.char))
    }

    /** A stroke of the figure, the rope or the line, in the bar's px: its points and its half width. */
    private class Stroke(val what: String, val shape: Shape, val half: Float)

    private fun figureStrokes(bar: Bar, f: Figure): List<Stroke> {
        val t = AffineTransform()
        t.translate((bar.edge + f.x * bar.scale).toDouble(), (f.feet * bar.scale).toDouble())
        t.scale(bar.scale.toDouble(), bar.scale.toDouble())
        if (f.lean != 0f) t.rotate(f.lean.toDouble())
        val swing = sin(f.step) * 10
        val shoulder = -Figure.NECK + 6
        val shapes = listOf(
            Ellipse2D.Float(-Figure.HEAD, -Figure.NECK - 2 * Figure.HEAD, 2 * Figure.HEAD, 2 * Figure.HEAD),
            Line2D.Float(0f, -Figure.NECK, 0f, -Figure.HIP), Line2D.Float(0f, -Figure.HIP, -swing, 0f), Line2D.Float(0f, -Figure.HIP, swing, 0f),
            Line2D.Float(f.armLX, shoulder + f.armLY, 0f, shoulder), Line2D.Float(0f, shoulder, f.armRX, shoulder + f.armRY))
        return shapes.map { Stroke("the figure", t.createTransformedShape(it), Figure.LINE / 2 * bar.scale) }
    }

    private fun ropeShape(bar: Bar, rope: JumpRope): Shape {
        val y0 = rope.ropeY
        val mid = rope.ropeMid
        val t = AffineTransform()
        t.translate(bar.edge.toDouble(), 0.0)
        t.scale(bar.scale.toDouble(), bar.scale.toDouble())
        return t.createTransformedShape(QuadCurve2D.Float(rope.ropeX0, y0, (rope.ropeX0 + rope.ropeX1) / 2, 2 * mid - y0, rope.ropeX1, y0))
    }

    private fun ropeAlpha(rope: JumpRope) = (if (sin(rope.ropeAngle) < 0) .45f else .9f) * rope.ropeAmount

    // The sweep ------------------------------------------------------------------------------------------------------------------

    /** What's wrong in one frame, if anything: what that shows is too near the screen's outline, cut by the bar's top, or off the side. */
    private fun problems(bar: Bar, player: IdlePlayer): List<String> {
        val found = ArrayList<String>()
        fun points(what: String, shape: Shape, half: Float) {
            val it = shape.getPathIterator(null, .25)
            val c = FloatArray(6)
            var worstEdge: String? = null
            var worstTop: String? = null
            while (!it.isDone) {
                if (it.currentSegment(c) != PathIterator.SEG_CLOSE) {
                    val x = c[0]
                    val y = c[1]
                    if (worstEdge == null && !Corners.clear(RADIUS, x, BAR - y, WIDTH.toFloat(), MARGIN + half))
                        worstEdge = "$what comes within ${MARGIN.toInt()} px of the screen's outline at (%.1f, %.1f)".format(x, y)
                    if (worstTop == null && y - half < -.5f) worstTop = "$what is cut by the bar's top at (%.1f, %.1f)".format(x, y - half)
                }
                it.next()
            }
            worstEdge?.let { found += it }
            worstTop?.let { found += it }
        }
        val game = player.game
        // The floor's line: the part that shows, where its fading ends are bright enough for corner_check.py to count.
        val strength = CREAM_GREY * FLOOR_ALPHA * player.floor / 255f
        if (strength > SHOWS) {
            val from = bar.floorLeft + FLOOR_FADE * SHOWS / strength
            val to = bar.floorRight - FLOOR_FADE * SHOWS / strength
            points("the floor", Rectangle2D.Float(from, bar.floorBottom - FLOOR_THICK, to - from, FLOOR_THICK), 0f)
        }
        for (g in player.glyphs) if (g.alpha >= SHOWS && g.scale > 0f) points("'${g.char}'", glyphShape(bar, g), 0f)
        game.figure?.let { f -> if (f.alpha >= SHOWS) for (s in figureStrokes(bar, f)) points(s.what, s.shape, s.half) }
        if (game is JumpRope && game.ropeAmount > 0 && ropeAlpha(game) >= SHOWS) points("the rope", ropeShape(bar, game), 1.5f * bar.scale)
        if (game is Firework) for (s in game.sparks) {
            if ((1 - s.life / s.max) < SHOWS) continue
            points("a spark", Ellipse2D.Float(bar.edge + (s.x - 2) * bar.scale, (s.y - 2) * bar.scale, 4 * bar.scale, 4 * bar.scale), 0f)
        }
        if (game is Race && game.line * 0x88 / 255f >= SHOWS) {
            val x = bar.edge + game.lineX * bar.scale
            points("the finish line", Line2D.Float(x, game.lineTop * bar.scale, x, game.lineBottom * bar.scale), 1.5f * bar.scale)
        }
        return found
    }

    /** A game to play: [kind], then after [comeBackAt] ms, if not over, the way home [way] (or the one comeBack picks). */
    class Scenario(val name: String, val kind: IdlePlayer.Kind, val comeBackAt: Float?, val way: IdlePlayer.Way?) {
        /** Whether the floor shows: in a game that uses it (the figure's, Race, the Firework) or a way home that does (Drop, Firework). */
        val floor = kind in setOf(IdlePlayer.Kind.Bowling, IdlePlayer.Kind.Juggle, IdlePlayer.Kind.Race, IdlePlayer.Kind.Firework) ||
            way == IdlePlayer.Way.Drop || way == IdlePlayer.Way.Firework
    }

    val scenarios = listOf(
        Scenario("drift-exit", IdlePlayer.Kind.Drift, 6000f, IdlePlayer.Way.Exit),
        Scenario("drift-shell", IdlePlayer.Kind.Drift, 6000f, IdlePlayer.Way.Shell),
        Scenario("drift-firework", IdlePlayer.Kind.Drift, 6000f, IdlePlayer.Way.Firework),
        Scenario("pong-exit", IdlePlayer.Kind.Pong, 6000f, IdlePlayer.Way.Exit),
        Scenario("pong-shell", IdlePlayer.Kind.Pong, 6000f, IdlePlayer.Way.Shell),
        Scenario("rope-exit", IdlePlayer.Kind.JumpRope, 6000f, IdlePlayer.Way.Exit),
        Scenario("rope-firework", IdlePlayer.Kind.JumpRope, 6000f, IdlePlayer.Way.Firework),
        Scenario("juggle-drop", IdlePlayer.Kind.Juggle, 6000f, IdlePlayer.Way.Drop),
        Scenario("bowling", IdlePlayer.Kind.Bowling, null, null),
        Scenario("bowling-exit", IdlePlayer.Kind.Bowling, 1800f, IdlePlayer.Way.Exit),
        Scenario("race", IdlePlayer.Kind.Race, null, null),
        Scenario("race-exit", IdlePlayer.Kind.Race, 2500f, IdlePlayer.Way.Exit),
        Scenario("firework", IdlePlayer.Kind.Firework, null, null),
        Scenario("firework-exit", IdlePlayer.Kind.Firework, 1800f, IdlePlayer.Way.Exit),
        // A loop left alone goes home by itself after LOOP_MILLIS.
        Scenario("drift-alone", IdlePlayer.Kind.Drift, null, null),
        Scenario("juggle-alone", IdlePlayer.Kind.Juggle, null, null),
    )

    /** Plays [scenario] on [bar] with [seed], calling [frame] with the time and the player before the first update and after each. */
    fun play(bar: Bar, scenario: Scenario, seed: Int, frame: (Float, IdlePlayer) -> Unit): Boolean {
        val glyphs = bar.glyphs()
        if (!IdlePlayer.fits(scenario.kind, glyphs)) return false
        val player = IdlePlayer(glyphs, bar.width, Random(seed), scenario.kind)
        var t = 0f
        frame(t, player)
        var cameBack = false
        while (true) {
            val going = player.update(FRAME)
            t += FRAME
            if (!going) { frame(t, player); break }
            val at = scenario.comeBackAt
            if (at != null && !cameBack && t >= at) { player.comeBack(scenario.way); cameBack = true }
            frame(t, player)
            check(t < IdlePlayer.LOOP_MILLIS + 20_000) { "${scenario.name} never ends" }
        }
        return true
    }

    val sweepBars = listOf(listOf("going", "the", "jumpy"), listOf("Hello", "and", "World"), listOf("quickly", "dog", "bigger"),
        listOf("I", "am", "ok"), listOf("my", "pig", "jog"))
    /** Bars with the automatic capital's arrow, which plays along. */
    val arrowBars = listOf(listOf("The", "I", "This"), listOf("going", "the", "jumpy"), listOf("Good", "", "gonna"))

    fun sweep(): List<String> {
        val problems = LinkedHashMap<String, Int>()
        var frames = 0
        for (size in FixedScale.Size.entries) for ((words, arrow) in sweepBars.map { it to false } + arrowBars.map { it to true }) {
            val bar = Bar(size, words, arrow)
            for (scenario in scenarios) for (seed in 1..(if (scenario.name.endsWith("alone")) 2 else 6)) {
                val track = ArrayList<List<Triple<Float, Float, Boolean>>>()
                val times = ArrayList<Float>()
                var glyphs: List<Glyph> = emptyList()
                val floors = ArrayList<Float>()
                play(bar, scenario, seed) { t, player ->
                    frames++
                    floors += player.floor
                    glyphs = player.glyphs
                    track += player.glyphs.map { Triple(bar.edge + it.x * bar.scale, it.y * bar.scale, it.alpha >= SHOWS) }
                    times += t
                    for (p in problems(bar, player)) {
                        val key = "${size.name}, $words${if (arrow) " with the arrow" else ""}, ${scenario.name}: ${p.substringBefore(" at (")}"
                        if (key !in problems) println("$key, first at ${"%.0f".format(t)} ms, seed $seed: $p")
                        problems[key] = (problems[key] ?: 0) + 1
                    }
                    if (t == 0f) for (g in player.glyphs)
                        kotlin.check(g.x == g.homeX && g.y == g.homeY) { "${scenario.name}: '${g.char}' starts away from its place" }
                }
                // A game these letters don't fit doesn't play.
                if (floors.isEmpty()) continue
                // The floor: only in a game that uses it, there all through its middle, never at the start or the end, and it fades.
                val what = "${size.name}, $words, ${scenario.name}, seed $seed"
                kotlin.check(floors.first() == 0f && floors.last() == 0f) { "$what: the floor shows at rest" }
                if (scenario.floor) kotlin.check(floors.max() == 1f) { "$what: the floor never shows fully" }
                else kotlin.check(floors.all { it == 0f }) { "$what: the floor shows in a game that doesn't use it" }
                for (f in 1 until floors.size) kotlin.check(abs(floors[f] - floors[f - 1]) <= .3f) {
                    "$what: the floor pops from ${floors[f - 1]} to ${floors[f]} at ${times[f]} ms"
                }
                // Home at the end, exactly, and every frame's move in line with the frames either side.
                for (g in glyphs) kotlin.check(g.x == g.homeX && g.y == g.homeY && g.scale == 1f && g.rot == 0f && g.alpha == 1f) {
                    "${scenario.name}: '${g.char}' ends away from its place"
                }
                fun move(f: Int, i: Int) = if (f < 1 || f >= track.size) 0f else
                    hypot(track[f][i].first - track[f - 1][i].first, track[f][i].second - track[f - 1][i].second)
                for (f in 1 until track.size) for (i in glyphs.indices) {
                    if (!track[f][i].third || !track[f - 1][i].third) continue
                    val d = move(f, i)
                    if (d > JUMP && d > 3 * move(f - 1, i) && d > 3 * move(f + 1, i)) {
                        val key = "${size.name}, $words, ${scenario.name}: '${glyphs[i].char}' jumps"
                        if (key !in problems) println("$key %.1f px at %.0f ms, seed $seed".format(d, times[f]))
                        problems[key] = (problems[key] ?: 0) + 1
                    }
                }
            }
        }
        println("Idle sweep: $frames frames")
        return problems.map { (k, v) -> "$k ($v frames)" }
    }

    // The keyframes --------------------------------------------------------------------------------------------------------------

    private fun draw(g: Graphics2D, bar: Bar, player: IdlePlayer) {
        val game = player.game
        if (player.floor > 0f) {
            val a = (FLOOR_ALPHA * player.floor.coerceIn(0f, 1f) * 255).toInt()
            val full = Color(CREAM.red, CREAM.green, CREAM.blue, a)
            val clear = Color(CREAM.red, CREAM.green, CREAM.blue, 0)
            val end = FLOOR_FADE / (bar.floorRight - bar.floorLeft)
            val paint = g.paint
            g.paint = LinearGradientPaint(bar.floorLeft, 0f, bar.floorRight, 0f, floatArrayOf(0f, end, 1 - end, 1f), arrayOf(clear, full, full, clear))
            g.fill(Rectangle2D.Float(bar.floorLeft, bar.floorBottom - FLOOR_THICK, bar.floorRight - bar.floorLeft, FLOOR_THICK))
            g.paint = paint
        }
        val saved = g.transform
        g.translate(bar.edge.toDouble(), 0.0)
        g.scale(bar.scale.toDouble(), bar.scale.toDouble())
        fun withAlpha(c: Color, a: Float) = Color(c.red, c.green, c.blue, (a.coerceIn(0f, 1f) * 255).toInt())
        if (game is Race && game.line > 0) {
            g.color = withAlpha(MUTED, game.line)
            g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(6f, 6f), 0f)
            g.draw(Line2D.Float(game.lineX, game.lineTop, game.lineX, game.lineBottom))
        }
        if (game is Firework) for (s in game.sparks) {
            g.color = withAlpha(ACCENT, 1 - s.life / s.max)
            g.fill(Ellipse2D.Float(s.x - 2, s.y - 2, 4f, 4f))
        }
        val rope = game as? JumpRope
        val behind = rope != null && sin(rope.ropeAngle) < 0
        fun ropeNow() {
            if (rope == null || rope.ropeAmount <= 0) return
            g.transform = saved
            g.color = withAlpha(INK, ropeAlpha(rope))
            g.stroke = BasicStroke(3f * bar.scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(ropeShape(bar, rope))
            g.translate(bar.edge.toDouble(), 0.0)
            g.scale(bar.scale.toDouble(), bar.scale.toDouble())
        }
        fun figureNow(f: Figure?) {
            if (f == null || f.alpha <= .01f) return
            g.transform = saved
            g.color = withAlpha(ACCENT, f.alpha)
            g.stroke = BasicStroke(Figure.LINE * bar.scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (s in figureStrokes(bar, f)) g.draw(s.shape)
            g.translate(bar.edge.toDouble(), 0.0)
            g.scale(bar.scale.toDouble(), bar.scale.toDouble())
        }
        fun glyphNow(glyph: Glyph) {
            if (glyph.alpha <= .01f) return
            g.transform = saved
            g.color = withAlpha(if (glyph.accent || glyph.arrow) ACCENT else INK, glyph.alpha)
            g.fill(glyphShape(bar, glyph))
            g.translate(bar.edge.toDouble(), 0.0)
            g.scale(bar.scale.toDouble(), bar.scale.toDouble())
        }
        if (behind) ropeNow()
        if (game is Juggle) {
            game.others.forEach { glyphNow(it) }
            figureNow(game.figure)
            game.balls.forEach { glyphNow(it) }
        } else {
            figureNow(game.figure)
            player.glyphs.forEach { glyphNow(it) }
        }
        if (!behind) ropeNow()
        g.transform = saved
    }

    private fun hints(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    /** The screen's bottom, as a screenshot shows it: the bar at its edge, the game in it, clipped to the bar as the strip clips. */
    private fun screen(bar: Bar, player: IdlePlayer): BufferedImage {
        val image = BufferedImage(WIDTH, ABOVE + BAR, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        hints(g)
        g.color = Color.BLACK
        g.fillRect(0, 0, image.width, image.height)
        g.translate(0, ABOVE)
        g.clip(Rectangle2D.Float(0f, 0f, WIDTH.toFloat(), BAR.toFloat()))
        draw(g, bar, player)
        g.dispose()
        return image
    }

    /**
     * One row of a sheet: the phone's black face (the screen's bottom and the border under it), the screen's outline faint, the
     * bar's top, the band's middle in blue, the line of the letters' middle in pink, the safe line 4 px in, in cyan, and anything
     * that shows outside it in red. Above it, unclipped and faint, whatever the bar's top cuts.
     */
    private fun row(bar: Bar, player: IdlePlayer, caption: String, screen: BufferedImage): BufferedImage {
        val above = 40
        val border = Math.round(BORDER)
        val image = BufferedImage(WIDTH + 60, 30 + above + BAR + border + 14, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        hints(g)
        g.color = Color(0x1C, 0x1C, 0x1C)
        g.fillRect(0, 0, image.width, image.height)
        g.color = Color.BLACK
        g.fillRect(0, 30, image.width, above + BAR + border)
        val top = 30 + above
        // What the bar's top cuts, faint.
        val cut = g.create() as Graphics2D
        hints(cut)
        cut.translate(30, top)
        cut.clip(Rectangle2D.Float(0f, -above.toFloat(), WIDTH.toFloat(), above.toFloat()))
        cut.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, .35f)
        draw(cut, bar, player)
        cut.dispose()
        // The screen, with corner_check's marks: content outside the safe line in red.
        val marked = BufferedImage(WIDTH, BAR, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until BAR) for (x in 0 until WIDTH) {
            var rgb = screen.getRGB(x, ABOVE + y)
            val grey = ((rgb shr 16 and 0xFF) * 299 + (rgb shr 8 and 0xFF) * 587 + (rgb and 0xFF) * 114) / 1000
            val visible = Corners.clear(RADIUS, x + .5f, BAR - (y + .5f), WIDTH.toFloat(), 0f)
            if (!visible) rgb = 0x3A0024
            if (grey > 40 && !Corners.clear(RADIUS, x + .5f, BAR - (y + .5f), WIDTH.toFloat(), MARGIN)) rgb = 0xFF0000
            marked.setRGB(x, y, rgb)
        }
        g.drawImage(marked, 30, top, null)
        // The outline, the safe line and the guides.
        fun outline(inset: Float, colour: Color, width: Float) {
            val p = Path2D.Float()
            val r = RADIUS - inset
            val l = 30 + inset
            val rt = 30 + WIDTH - inset
            val b = top + BAR - inset
            p.moveTo(l, top.toFloat()); p.lineTo(l, b - r); p.quadTo(l, b, l + r, b); p.lineTo(rt - r, b); p.quadTo(rt, b, rt, b - r); p.lineTo(rt, top.toFloat())
            g.color = colour
            g.stroke = BasicStroke(width)
            g.draw(p)
        }
        outline(0f, Color(0x55, 0x55, 0x55), 1f)
        outline(MARGIN, Color(0, 160, 160), 1f)
        g.color = Color(0x55, 0x55, 0x55)
        g.draw(Line2D.Float(0f, top.toFloat(), image.width.toFloat(), top.toFloat()))
        val dash = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(8f, 6f), 0f)
        g.stroke = dash
        g.color = Color(0, 200, 255)
        val band = top + (BAR + BORDER) / 2
        g.draw(Line2D.Float(0f, band, image.width.toFloat(), band))
        g.color = Color(0xAA, 0xAA, 0xAA)
        g.font = regular.deriveFont(17f)
        g.drawString(caption, 16, 22)
        g.font = regular.deriveFont(12f)
        g.color = Color(0x66, 0x66, 0x66)
        g.drawString("screen's edge; the 22 dp border under it", 36, top + BAR + 14)
        g.color = Color(0, 200, 255)
        g.drawString("blue: the black band's middle, the line", image.width - 250f, 22f)
        g.dispose()
        return image
    }

    /** The times to show: the start, the game in 8 to 12 frames, and its way home. */
    private fun keyTimes(scenario: Scenario, end: Float, wayStart: Float?): List<Float> {
        val times = ArrayList<Float>()
        if (wayStart == null) {
            val n = 10
            for (i in 0 until n) times += end * i / (n - 1)
        } else {
            times += listOf(0f, 200f, 450f, 800f, 1500f, 3000f, 4500f, wayStart - FRAME)
            val way = end - wayStart
            for (i in 1..4) times += wayStart + way * i / 4
        }
        return times
    }

    fun keyframes(out: File, frameDir: File) {
        val words = listOf("going", "the", "jumpy")
        for (size in FixedScale.Size.entries) {
            val bar = Bar(size, words)
            for (scenario in scenarios) {
                if (scenario.name.endsWith("alone")) continue
                // First, how long it runs and when the way home starts.
                var end = 0f
                var wayStart: Float? = null
                play(bar, scenario, 1) { t, player -> end = t; if (wayStart == null && player.returning) wayStart = t }
                val times = keyTimes(scenario, end, wayStart)
                val rows = ArrayList<BufferedImage>()
                var next = 0
                val dir = File(frameDir, "${size.name.lowercase()}/${scenario.name}").apply { mkdirs() }
                play(bar, scenario, 1) { t, player ->
                    while (next < times.size && t >= times[next] - FRAME / 2) {
                        val screen = screen(bar, player)
                        ImageIO.write(screen, "png", File(dir, "%02d-%05dms.png".format(next, t.toInt())))
                        val phase = if (player.returning) "way home (${player.game::class.simpleName})" else "playing"
                        val floor = if (player.floor > 0f) ", floor %.0f%%".format(player.floor * 100) else ", no floor"
                        rows += row(bar, player, "${scenario.name}, ${size.name}, %5.0f ms, ".format(t) + phase + floor, screen)
                        next++
                    }
                }
                stack(rows, File(out, "${size.name.lowercase()}-${scenario.name}.png"))
            }
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

    /** Sweeps every game; with [out], draws the keyframes there too. */
    fun main(fonts: File, out: File?) {
        regular = Font.createFont(Font.TRUETYPE_FONT, File(fonts, "inter_regular.otf"))
        bold = Font.createFont(Font.TRUETYPE_FONT, File(fonts, "inter_bold.otf"))
        val problems = sweep()
        if (out != null) {
            out.mkdirs()
            keyframes(File(out, "sheets").apply { mkdirs() }, File(out, "frames"))
        }
        kotlin.check(problems.isEmpty()) { "The idle games break the bar's line:\n" + problems.joinToString("\n") }
        println("Every idle game and way home, at every size: nothing within 4 px of the screen's outline, nothing cut by the bar's top, " +
            "no jumps; the floor only in the games that use it, fading in and out, its line clear of the corners")
    }
}

fun main(args: Array<String>) = IdleRender.main(File(args[0]), args.getOrNull(1)?.let(::File))
