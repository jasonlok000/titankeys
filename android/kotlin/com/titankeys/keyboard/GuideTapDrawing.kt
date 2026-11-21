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
 * guide's colours, resting on the shaded patch where the taps count ([CursorGesture.inZone]), and a row of boxes over it that
 * light as each tap lands: 1, then "2 · HOLD", the last tap and its hold one box, as one tap-and-hold (owner, 2026-11-16: "2 and
 * hold or 3 and hold shd be the same box, maybe even like tap-hold"); 1, 2, "3 · HOLD" on the select slide. The last box
 * lights as brightly as the tap boxes. Once the mode registers, every box stays lit while the finger is held, in the showing and
 * for real alike, and the thumb shows where to slide (owner, 2026-11-21: the showing and real use didn't light the same boxes).
 *
 * Paths, rects and text only, so tests/guide's slide renders draw it with the same stand-in Canvas as the decorations.
 */
class GuideTapDrawing(private val font: Typeface? = null) {
    /**
     * How the drawing stands: [landed] taps lit of [taps], the hold lit when [held], the thumb [press]ed from 0 (resting) to 1
     * (down on the keys), and [slide] of the way through the drag to the left, from 0 to 1.
     */
    data class Pose(val taps: Int, val landed: Int = 0, val held: Boolean = false, val press: Float = 0f, val slide: Float = 0f) {
        /** Every box stays lit while the mode is on, until the lift. */
        fun lit(box: Int) = if (held) box < taps else box < landed
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
        if (pose.slide != 0f) {
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

    /**
     * The boxes, over the patch's letter row: a pill for each tap but the last, then the last tap and its hold in one. Lit ones
     * fill in the accent; the last half fills while its tap is down and not yet held.
     */
    private fun drawSteps(canvas: Canvas, zone: RectF, row: Float, pose: Pose, dp: Float, accent: Int, paper: Int) {
        val widths = List(pose.taps - 1) { STEP_WIDTH * dp } + HOLD_WIDTH * dp
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
            canvas.drawText(label(index, pose.taps), left + width / 2, top + height / 2 + STEP_TEXT * dp * .36f, text)
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
        /** Box [box]'s words: its tap's number, or for the last, its number and HOLD, one tap-and-hold. */
        fun label(box: Int, taps: Int) = if (box < taps - 1) "${box + 1}" else "${box + 1} · $HOLD"
        // Sizes in dp. Change these to adjust the look.
        const val STEP_WIDTH = 24f
        const val HOLD_WIDTH = 64f
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
         * The slide's own showing, at the keyboard's own speed, so it shows how quick the taps must be (owner, 2026-11-16: "The
         * animations shd reflect the speed for this to be triggered"): each tap down for [DEMO_TAP_MILLIS], the next
         * [DEMO_GAP_MILLIS] after it lifts, inside the keyboard's [TapMeter.KEYBOARD_GAP_MILLIS]. The last stays down; its box
         * fills [DEMO_HOLD_LIGHT_MILLIS] in, as the keyboard registers the hold; with [drag] the thumb then slides left, all the
         * boxes lit, and lifts at the end, the boxes going out with it, as a real lift does. Then it rests, and the showing starts
         * again. [millis] is the time into the showing. The field's own demo follows the same clock: see [demoSlid].
         */
        fun demoAt(millis: Long, taps: Int, drag: Boolean): Pose {
            val t = millis.mod(demoMillis(taps, drag))
            val each = DEMO_TAP_MILLIS + DEMO_GAP_MILLIS
            val lastDown = (taps - 1) * each
            if (t < lastDown) {
                val index = (t / each).toInt()
                val local = t - index * each
                val down = local < DEMO_TAP_MILLIS
                return Pose(taps, landed = index + 1, press = if (down) 1f else 0f)
            }
            val held = t - lastDown
            if (held < DEMO_HOLD_LIGHT_MILLIS) return Pose(taps, taps, press = 1f)
            val sliding = held - DEMO_HOLD_LIGHT_MILLIS - DEMO_HOLD_MILLIS
            if (!drag || sliding < 0) return Pose(taps, taps, held = true, press = 1f)
            if (sliding >= DEMO_SLIDE_MILLIS) return Pose(taps)
            return Pose(taps, taps, held = true, press = 1f, slide = ease((sliding.toFloat() / DEMO_SLIDE_MILLIS).coerceIn(0f, 1f)))
        }

        /**
         * How far the showing's thumb has slid at [millis], from 0 to 1, and 1 after it lifts: the field's caret and selection move
         * with the thumb, on its clock, instead of on their own after it had stopped (owner, 2026-11-21: "the finger should slide
         * after 2. hold in the demo. right now it's held still").
         */
        fun demoSlid(millis: Long, taps: Int, drag: Boolean): Float {
            val sliding = millis.mod(demoMillis(taps, drag)) - demoHeldAt(taps) - DEMO_HOLD_MILLIS
            return if (!drag || sliding < 0) 0f else ease((sliding.toFloat() / DEMO_SLIDE_MILLIS).coerceIn(0f, 1f))
        }

        /** The showing's last tap has landed by [millis]: three taps pick the word in front as the third lands. */
        fun demoLanded(millis: Long, taps: Int, drag: Boolean) =
            millis.mod(demoMillis(taps, drag)) >= (taps - 1) * (DEMO_TAP_MILLIS + DEMO_GAP_MILLIS)

        /** When the showing's hold registers, into the showing. */
        private fun demoHeldAt(taps: Int) = (taps - 1) * (DEMO_TAP_MILLIS + DEMO_GAP_MILLIS) + DEMO_HOLD_LIGHT_MILLIS

        /** How long one showing lasts, its rest at the end included. */
        fun demoMillis(taps: Int, drag: Boolean) =
            (taps - 1) * (DEMO_TAP_MILLIS + DEMO_GAP_MILLIS) + DEMO_HOLD_LIGHT_MILLIS + DEMO_HOLD_MILLIS +
                (if (drag) DEMO_SLIDE_MILLIS else 0L) + DEMO_REST_MILLIS

        internal fun liveAt(meter: TapMeter, now: Long, drag: Boolean, left: Boolean, animate: Boolean): Pose? {
            if (meter.held) {
                val elapsed = now - meter.heldAt
                val progress = if (!drag) 0f else if (!animate) 1f
                    else ease((elapsed.toFloat() / LIVE_SLIDE_MILLIS).coerceIn(0f, 1f))
                return Pose(meter.taps, held = true, press = 1f, slide = if (left) progress else -progress)
            }
            val landed = meter.visibleTaps(now)
            return if (landed == 0) null else Pose(meter.taps, landed, press = if (meter.downAt >= 0) 1f else 0f)
        }

        /** A tap of the showing: down this long, as real taps are (the keyboard takes up to 130 ms for a first). */
        const val DEMO_TAP_MILLIS = 90L
        /** From a tap's lift to the next tap: real gaps took 37 to 124 ms. */
        const val DEMO_GAP_MILLIS = 110L
        /** The last tap down this long before its box fills: the keyboard registers a hold at 70 ms. */
        const val DEMO_HOLD_LIGHT_MILLIS = 120L
        /** Then held, still, before the slide: short, so the slide plainly follows the hold (owner, 2026-11-21). It was 500 ms. */
        const val DEMO_HOLD_MILLIS = 200L
        const val DEMO_SLIDE_MILLIS = 900L
        const val LIVE_SLIDE_MILLIS = 650L
        const val DEMO_REST_MILLIS = 1600L

        private fun ease(x: Float) = x * x * (3 - 2 * x)

        fun faded(colour: Int, alpha: Float) = colour and 0x00FFFFFF or (((colour ushr 24) * alpha.coerceIn(0f, 1f)).roundToInt() shl 24)

        fun mix(a: Int, b: Int, share: Float): Int {
            fun channel(shift: Int) = (((a shr shift) and 0xFF) * (1 - share) + ((b shr shift) and 0xFF) * share).roundToInt() shl shift
            return (a and 0xFF000000.toInt()) or channel(16) or channel(8) or channel(0)
        }
    }
}
