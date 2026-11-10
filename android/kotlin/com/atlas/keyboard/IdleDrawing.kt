package com.atlas.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.sin

/** Draws a frame of [IdleScene] on the bar: thin lines in the accent colour for the figure, and the letters in the words' own look. */
internal class IdleDrawing(private val density: Float) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val words = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * [letters] is the paint of the bar's words, for their typeface and size. [height] is the height of the row of words, where the
     * figure stands on the bottom. It draws no text but the letters: the figure never says anything.
     */
    fun draw(canvas: Canvas, frame: IdleScene.Frame, letters: Paint, height: Float, look: Look) {
        val u = density
        words.set(letters)
        words.color = look.ink
        for (placed in frame.letters) {
            words.textSize = letters.textSize * placed.scale
            canvas.drawText(placed.char.toString(), placed.x, placed.y, words)
        }
        if (!frame.figureShown) return
        line.color = look.accent
        line.strokeWidth = LINE_WIDTH * u
        val feet = height - FEET_ABOVE * u
        val x = frame.figureX
        val side = if (frame.facingLeft) -1f else 1f
        if (frame.pose == IdleScene.Pose.Fall) lying(canvas, x, feet, side, u) else standing(canvas, x, feet, side, u, frame)
        if (frame.carrying > 0 && frame.pose != IdleScene.Pose.Fall) {
            // The bundle of letters, held over the head: a little bigger with each letter.
            val r = (BUNDLE_SMALLEST + minOf(frame.carrying, 12) * BUNDLE_GROWTH) * u
            canvas.drawCircle(x, feet - (FIGURE_HEIGHT + 2) * u - r, r, line)
        }
    }

    private fun standing(canvas: Canvas, x: Float, feet: Float, side: Float, u: Float, frame: IdleScene.Frame) {
        val swing = if (frame.pose == IdleScene.Pose.Walk) sin(frame.step * PI.toFloat()) else 0f
        val hip = feet - 10 * u
        val neck = feet - 19 * u
        canvas.drawCircle(x, feet - 22.5f * u, 3 * u, line)
        canvas.drawLine(x, neck, x, hip, line)
        canvas.drawLine(x, hip, x + swing * 3.5f * u, feet, line)
        canvas.drawLine(x, hip, x - swing * 3.5f * u, feet, line)
        val arms = neck + 2 * u
        when {
            // Arms up to hold the bundle.
            frame.carrying > 0 -> {
                canvas.drawLine(x, arms, x - 3 * u, arms - 8 * u, line)
                canvas.drawLine(x, arms, x + 3 * u, arms - 8 * u, line)
            }
            // Reaching down for a letter, or to put one in place.
            frame.pose == IdleScene.Pose.Pick -> {
                canvas.drawLine(x, arms, x + side * 6 * u, arms + 6 * u, line)
                canvas.drawLine(x, arms, x - side * 2 * u, arms + 7 * u, line)
            }
            else -> {
                canvas.drawLine(x, arms, x - swing * 3 * u, arms + 7 * u, line)
                canvas.drawLine(x, arms, x + swing * 3 * u, arms + 7 * u, line)
            }
        }
    }

    private fun lying(canvas: Canvas, x: Float, feet: Float, side: Float, u: Float) {
        val y = feet - 1.5f * u
        canvas.drawCircle(x - side * 9 * u, y - 1.5f * u, 3 * u, line)
        canvas.drawLine(x - side * 6 * u, y, x + side * 4 * u, y, line)
        canvas.drawLine(x + side * 4 * u, y, x + side * 10 * u, y - 2 * u, line)
        canvas.drawLine(x + side * 4 * u, y, x + side * 10 * u, y, line)
    }

    companion object {
        const val LINE_WIDTH = 1.5f
        const val FEET_ABOVE = 3f
        const val FIGURE_HEIGHT = 26f
        const val BUNDLE_SMALLEST = 2.5f
        const val BUNDLE_GROWTH = .35f
    }
}
