package com.atlas.keyboard

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws [IdlePlayer]'s game on the bar: the letters in the words' own look and typeface, the figure, the rope, the sparks and the
 * finish line in thin lines. The game is in the bar's own units, 88 high, which [scale] turns into the bar's pixels. Nothing here
 * is made per frame.
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

    /** [words] is the paint of the bar's words, for their typeface. */
    fun draw(canvas: Canvas, player: IdlePlayer, scale: Float, words: Paint, look: Look) {
        canvas.save()
        canvas.scale(scale, scale)
        letter.typeface = words.typeface
        val game = player.game
        if (game is Race && game.line > 0) {
            line.color = look.muted; line.alpha = (game.line * 255).toInt(); line.strokeWidth = 3f; line.pathEffect = dashes
            canvas.drawLine(game.lineX, 4f, game.lineX, Idle.H - 4, line)
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

    private fun glyph(canvas: Canvas, g: Glyph, look: Look) {
        if (g.alpha <= .01f) return
        letter.color = if (g.accent) look.accent else look.ink
        letter.alpha = (g.alpha.coerceIn(0f, 1f) * 255).toInt()
        letter.textSize = g.size * g.scale
        canvas.save()
        canvas.translate(g.x, g.y)
        if (g.rot != 0f) canvas.rotate(g.rot * 180 / PI.toFloat())
        val metrics = letter.fontMetrics
        canvas.drawText(g.text, 0f, -(metrics.ascent + metrics.descent) / 2, letter)
        canvas.restore()
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
        val mid = Idle.lerp(y0 + 6, Idle.lerp(y0 - 40, y0 + 36, (1 + cos(rope.ropeAngle)) / 2), rope.ropeAmount)
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
