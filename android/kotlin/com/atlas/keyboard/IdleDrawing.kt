package com.atlas.keyboard

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.sin

/**
 * Draws [IdlePlayer]'s game on the bar: the letters in the words' own look and typeface, the figure, the rope, the sparks and the
 * finish line in thin lines. The game is in the bar's own units, 88 high, which [scale] turns into the bar's pixels. Nothing here
 * is made per frame. tests/idle-render draws the same in AWT, to check every frame against the screen's outline.
 */
internal class IdleDrawing {
    private val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dashes = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    private val path = Path()

    /**
     * [typefaces] are the bar's words' typefaces, left to right, as a [Glyph.word] counts them: the best word, in the middle, is
     * bold, so each letter keeps its own weight and nothing changes as a game starts or ends.
     */
    fun draw(canvas: Canvas, player: IdlePlayer, scale: Float, typefaces: List<Typeface?>, look: Look) {
        canvas.save()
        canvas.scale(scale, scale)
        this.typefaces = typefaces
        val game = player.game
        if (game is Race && game.line > 0) {
            line.color = look.muted; line.alpha = (game.line * 255).toInt(); line.strokeWidth = 3f; line.pathEffect = dashes
            canvas.drawLine(game.lineX, game.lineTop, game.lineX, game.lineBottom, line)
            line.pathEffect = null; line.alpha = 255
        }
        if (game is Firework) {
            dot.color = look.accent
            for (s in game.sparks) { dot.alpha = ((1 - s.life / s.max).coerceIn(0f, 1f) * 255).toInt(); canvas.drawCircle(s.x, s.y, 2f, dot) }
        }
        val rope = game as? JumpRope
        // The rope behind the letters while it swings at the back, and in front of them at the front.
        val behind = rope != null && sin(rope.ropeAngle) < 0
        if (rope != null && behind) rope(canvas, rope, look, true)
        if (game is Juggle) {
            for (g in game.others) glyph(canvas, g, look)
            game.figure.let { figure(canvas, it, look) }
            for (g in game.balls) glyph(canvas, g, look)
        } else {
            game.figure?.let { figure(canvas, it, look) }
            for (g in player.glyphs) glyph(canvas, g, look)
        }
        if (rope != null && !behind) rope(canvas, rope, look, false)
        canvas.restore()
    }

    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var floorKey = ""

    /**
     * The floor (owner, 2026-11-13, of the phone preview: "let's do 3"), in the strip's own pixels, before the game: a line [thick] high whose bottom is at [bottom],
     * where the game's floor is, so what stands on the floor stands on it. It runs from [left] to [right], clear of the screen's
     * curved corners, and fades out over [fade] at each end. [IdlePlayer.floor] says how much of it shows: none at rest, or in a
     * game that doesn't use the floor. The screen's edge blends into the black border under it, so without it the figure
     * stood on nothing.
     */
    fun floor(canvas: Canvas, amount: Float, left: Float, right: Float, bottom: Float, thick: Float, fade: Float, look: Look) {
        if (amount <= 0f || right - left <= 2 * fade) return
        val key = "$left $right ${look.floorLine}"
        if (key != floorKey) {
            floorKey = key
            val clear = look.floorLine and 0xFFFFFF
            val end = fade / (right - left)
            floorPaint.shader = LinearGradient(left, 0f, right, 0f, intArrayOf(clear, look.floorLine, look.floorLine, clear),
                floatArrayOf(0f, end, 1 - end, 1f), Shader.TileMode.CLAMP)
        }
        floorPaint.alpha = (amount.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawRect(left, bottom - thick, right, bottom, floorPaint)
    }

    /** The arrow's line width, in the games' units: [Strip] sets it to match its own arrow. */
    var arrowLine = 3f
    private var typefaces: List<Typeface?> = emptyList()

    private fun glyph(canvas: Canvas, g: Glyph, look: Look) {
        if (g.alpha <= .01f) return
        if (g.arrow) { arrow(canvas, g, look); return }
        letter.color = if (g.accent) look.accent else look.ink
        letter.alpha = (g.alpha.coerceIn(0f, 1f) * 255).toInt()
        letter.textSize = g.size * g.scale
        letter.typeface = typefaces.getOrNull(g.word) ?: typefaces.firstOrNull()
        // Placed by the middle of its box, as on the bar, and turned about its ink's middle, [Glyph.pivot].
        val pivot = g.pivot * g.scale
        canvas.save()
        canvas.translate(g.x, g.y + pivot)
        if (g.rot != 0f) canvas.rotate(g.rot * 180 / PI.toFloat())
        val metrics = letter.fontMetrics
        canvas.drawText(g.text, 0f, -(metrics.ascent + metrics.descent) / 2 - pivot, letter)
        canvas.restore()
    }

    /** The automatic capital's arrow, as the strip draws it: a head as wide as it is tall at the top, on a stem half as wide. */
    private fun arrow(canvas: Canvas, g: Glyph, look: Look) {
        stroke.color = look.accent
        stroke.alpha = (g.alpha.coerceIn(0f, 1f) * 255).toInt()
        stroke.strokeWidth = arrowLine * g.scale
        val size = g.advance * g.scale
        canvas.save()
        canvas.translate(g.x, g.y)
        if (g.rot != 0f) canvas.rotate(g.rot * 180 / PI.toFloat())
        val x = -size / 2
        val y = -size / 2
        path.reset()
        path.moveTo(x + size / 2, y)
        path.lineTo(x + size, y + size * .52f)
        path.lineTo(x + size * .73f, y + size * .52f)
        path.lineTo(x + size * .73f, y + size)
        path.lineTo(x + size * .27f, y + size)
        path.lineTo(x + size * .27f, y + size * .52f)
        path.lineTo(x, y + size * .52f)
        path.close()
        canvas.drawPath(path, stroke)
        canvas.restore()
    }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private fun figure(canvas: Canvas, f: Figure, look: Look) {
        if (f.alpha <= .01f) return
        line.color = look.accent
        line.alpha = (f.alpha.coerceIn(0f, 1f) * 255).toInt()
        line.strokeWidth = Figure.LINE
        canvas.save()
        canvas.translate(f.x, f.feet)
        if (f.lean != 0f) canvas.rotate(f.lean * 180 / PI.toFloat())
        val swing = sin(f.step) * 10
        canvas.drawCircle(0f, -Figure.NECK - Figure.HEAD, Figure.HEAD, line)
        canvas.drawLine(0f, -Figure.NECK, 0f, -Figure.HIP, line)
        canvas.drawLine(0f, -Figure.HIP, -swing, 0f, line)
        canvas.drawLine(0f, -Figure.HIP, swing, 0f, line)
        val shoulder = -Figure.NECK + 6
        canvas.drawLine(f.armLX, shoulder + f.armLY, 0f, shoulder, line)
        canvas.drawLine(0f, shoulder, f.armRX, shoulder + f.armRY, line)
        canvas.restore()
        line.alpha = 255
    }

    /** A rope seen from the side: its middle goes from under the feet to over the head and back. */
    private fun rope(canvas: Canvas, rope: JumpRope, look: Look, behind: Boolean) {
        if (rope.ropeAmount <= 0f) return
        val y0 = rope.ropeY
        val mid = rope.ropeMid
        line.color = look.ink
        line.alpha = ((if (behind) .45f else .9f) * rope.ropeAmount * 255).toInt()
        line.strokeWidth = 3f
        path.reset()
        path.moveTo(rope.ropeX0, y0)
        path.quadTo((rope.ropeX0 + rope.ropeX1) / 2, 2 * mid - y0, rope.ropeX1, y0)
        canvas.drawPath(path, line)
        line.alpha = 255
    }
}
