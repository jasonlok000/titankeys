package com.titankeys.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Space-tap slides' drawing (owner, #90: "Just make the gestures more clearer or more obvious"): a flat thumb, in the
 * guide's colours, resting on the shaded patch where the taps count ([CursorGesture.inZone]), and a row of steps over it that
 * light as each tap lands, 1, 2, then held, or 1, 2, 3, then held on the select slide. It shows from the first try, in place of
 * the ghost finger, and stays calm: the thumb only moves while the slide shows how, or while the real taps land.
 *
 * Paths, rects and text only, so tests/guide's slide renders draw it with the same stand-in Canvas as the decorations.
 */
class GuideTapDrawing(private val font: Typeface? = null) {
    /**
     * How the drawing stands: [landed] taps lit of [taps], the hold lit when [held], the thumb [press]ed from 0 (resting) to 1
     * (down on the keys), and [slide] of the way through the drag to the left, from 0 to 1.
     */
    data class Pose(val taps: Int, val landed: Int = 0, val held: Boolean = false, val press: Float = 0f, val slide: Float = 0f) {
        fun lit(step: Int) = if (step < taps) step < landed else held
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        if (font != null) typeface = font
    }
    private val path = Path()

    /**
     * Draws [pose] over [zone], the shaded patch on the drawing of the keys, in pixels: its upper half is the bottom letter row
     * and its lower half is Space. [reach] is how far left the drag goes, in pixels. [accent] is the guide's orange (or cream with
     * Orange accents off), [paper] the colour behind the keys, and [thumb] the thumb's.
     */
    fun draw(canvas: Canvas, zone: RectF, pose: Pose, dp: Float, accent: Int, paper: Int, thumb: Int, reach: Float) {
        val row = (zone.bottom - zone.top) / 2
        val tipX = (zone.left + zone.right) / 2 - reach * pose.slide
        val tipY = zone.top + row * 1.5f
        // The drag: a line from where the taps landed to where the thumb is now, as the cursor goes.
        if (pose.slide > 0f) {
            stroke.color = accent
            stroke.strokeWidth = 3f * dp
            canvas.drawLine((zone.left + zone.right) / 2, tipY, tipX, tipY, stroke)
        }
        drawSteps(canvas, zone, row, pose, dp, accent, paper)
        // Each tap: a ring at the thumb's tip as it lands.
        if (pose.press > .5f && !pose.held) {
            val ring = (pose.press - .5f) * 2f
            stroke.color = faded(accent, 1f - ring * .6f)
            stroke.strokeWidth = 2f * dp
            canvas.drawCircle(tipX, tipY, (THUMB_WIDTH / 2 + 4f + ring * 8f) * dp, stroke)
        }
        if (pose.held) {
            stroke.color = accent
            stroke.strokeWidth = 2.5f * dp
            canvas.drawCircle(tipX, tipY, (THUMB_WIDTH / 2 + 6f) * dp, stroke)
        }
        drawThumb(canvas, tipX, tipY + pose.press * PRESS_DEPTH * dp, dp, thumb, paper)
    }

    /** The steps, over the patch's letter row: a pill for each tap, then HOLD. Lit ones fill in the accent. */
    private fun drawSteps(canvas: Canvas, zone: RectF, row: Float, pose: Pose, dp: Float, accent: Int, paper: Int) {
        val widths = List(pose.taps) { STEP_WIDTH * dp } + HOLD_WIDTH * dp
        val gap = STEP_GAP * dp
        val total = widths.sum() + gap * (widths.size - 1)
        var left = (zone.left + zone.right) / 2 - total / 2
        val height = STEP_HEIGHT * dp
        val top = zone.top + row / 2 - height / 2
        text.textSize = STEP_TEXT * dp
        for ((index, width) in widths.withIndex()) {
            val lit = pose.lit(index)
            // A dark pill under each, so the step reads over the key's label.
            fill.color = if (lit) accent else faded(paper, .85f)
            canvas.drawRoundRect(left, top, left + width, top + height, height / 2, height / 2, fill)
            stroke.color = accent
            stroke.strokeWidth = 1.5f * dp
            if (!lit) canvas.drawRoundRect(left, top, left + width, top + height, height / 2, height / 2, stroke)
            text.color = if (lit) paper else accent
            canvas.drawText(if (index < pose.taps) "${index + 1}" else HOLD, left + width / 2, top + height / 2 + STEP_TEXT * dp * .36f, text)
            left += width + gap
        }
    }

    /** A flat thumb, seen from above: its tip on the keys at [x], [y], leaning in from the lower right, with its nail. */
    private fun drawThumb(canvas: Canvas, x: Float, y: Float, dp: Float, colour: Int, paper: Int) {
        val w = THUMB_WIDTH * dp
        val length = THUMB_LENGTH * dp
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(THUMB_LEAN)
        path.reset()
        roundRect(-w / 2, -w / 2, w / 2, length, w / 2)
        fill.color = colour
        canvas.drawPath(path, fill)
        stroke.color = faded(paper, .5f)
        stroke.strokeWidth = 1.5f * dp
        canvas.drawPath(path, stroke)
        // The nail, near the tip.
        val nail = w * .62f
        path.reset()
        roundRect(-nail / 2, -w / 2 + 3f * dp, nail / 2, -w / 2 + 3f * dp + nail * 1.15f, nail * .45f)
        fill.color = mix(colour, paper, .14f)
        canvas.drawPath(path, fill)
        canvas.restore()
    }

    private fun roundRect(left: Float, top: Float, right: Float, bottom: Float, r: Float) {
        path.moveTo(left + r, top)
        path.lineTo(right - r, top)
        path.quadTo(right, top, right, top + r)
        path.lineTo(right, bottom - r)
        path.quadTo(right, bottom, right - r, bottom)
        path.lineTo(left + r, bottom)
        path.quadTo(left, bottom, left, bottom - r)
        path.lineTo(left, top + r)
        path.quadTo(left, top, left + r, top)
        path.close()
    }

    companion object {
        const val HOLD = "HOLD"
        // Sizes in dp. Change these to adjust the look.
        const val STEP_WIDTH = 24f
        const val HOLD_WIDTH = 48f
        const val STEP_HEIGHT = 20f
        const val STEP_GAP = 5f
        const val STEP_TEXT = 12f
        const val THUMB_WIDTH = 26f
        const val THUMB_LENGTH = 74f
        /** How far the thumb leans, in degrees: in from the lower right, as a right thumb holding the phone. */
        const val THUMB_LEAN = -22f
        /** How far down the thumb goes as it taps. */
        const val PRESS_DEPTH = 3f

        /**
         * The drawing at [t], from 0 to 1, of the slide's own showing: each tap lands and lifts in turn and lights its step, the
         * last stays down and lights HOLD, then with [drag] the thumb slides left. It rests at the end.
         */
        fun demo(t: Float, taps: Int, drag: Boolean): Pose {
            val tapsEnd = if (drag) .55f else .85f
            if (t >= tapsEnd) {
                val slide = if (drag) ((t - tapsEnd) / (1f - tapsEnd - .1f)).coerceIn(0f, 1f) else 0f
                return Pose(taps, taps, held = true, press = 1f, slide = ease(slide))
            }
            val each = tapsEnd / taps
            val index = (t / each).toInt().coerceAtMost(taps - 1)
            val local = (t - index * each) / each
            val last = index == taps - 1
            // A tap goes down, lights its step as it lands, and comes back up; the last one stays down and becomes the hold.
            val press = if (last) (local / .3f).coerceAtMost(1f) else sin((local / .6f).coerceAtMost(1f) * PI).toFloat()
            val landed = index + if (local >= .15f) 1 else 0
            return Pose(taps, landed, held = last && local >= .7f, press = press.coerceIn(0f, 1f))
        }

        private fun ease(x: Float) = x * x * (3 - 2 * x)

        fun faded(colour: Int, alpha: Float) = colour and 0x00FFFFFF or (((colour ushr 24) * alpha.coerceIn(0f, 1f)).roundToInt() shl 24)

        fun mix(a: Int, b: Int, share: Float): Int {
            fun channel(shift: Int) = (((a shr shift) and 0xFF) * (1 - share) + ((b shr shift) and 0xFF) * share).roundToInt() shl shift
            return (a and 0xFF000000.toInt()) or channel(16) or channel(8) or channel(0)
        }
    }
}
