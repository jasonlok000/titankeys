package com.atlas.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * Draws a [GestureDisplay] in the place of the words, at the strip's own height, between [left] and [right] so that the screen's
 * rounded corners do not cut it. It is all shapes, so no font size changes it, and everything it uses is made once, so a frame
 * makes no garbage.
 *
 * Text is drawn as it looks from far away: letters as dots, words as short bars. The caret is the one mark in the accent colour.
 * For the cursor the line of letters moves past the caret at the cursor's speed, over a track of the keyboard that shows the dead
 * zone and the finger. For a word drag the selection grows over the words. For a delete the words run into the caret and go.
 */
/** [radius] rounds the selection's corners, from [Radii]. */
internal class GestureDrawing(private val density: Float, private val radius: Float) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    fun draw(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, height: Int, look: Look) {
        val h = height.toFloat()
        // The words stay where they are, under a cover in the strip's own colour, so nothing moves when this comes and goes.
        fill.color = GuideMotion.faded(look.paper, display.reveal)
        canvas.drawRect(0f, 0f, right + left, h, fill)
        val mid = h / 2
        val settle = REST_SCALE + (1f - REST_SCALE) * display.reveal
        canvas.save()
        canvas.scale(1f, settle, (left + right) / 2, mid)
        val inside = left + dp(SIDE)
        val outside = right - dp(SIDE)
        when (display.scene) {
            GestureDisplay.Scene.Cursor -> cursor(canvas, display, inside, outside, mid, look)
            GestureDisplay.Scene.WordDrag, GestureDisplay.Scene.WordSelected -> words(canvas, display, inside, outside, mid, look)
            GestureDisplay.Scene.DeleteHold, GestureDisplay.Scene.DeleteFlick -> delete(canvas, display, inside, outside, mid, look)
            GestureDisplay.Scene.None -> Unit
        }
        canvas.restore()
    }

    /** A line of letters that moves past the caret, and under it the track with the dead zone and the finger. */
    private fun cursor(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, mid: Float, look: Look) {
        val alpha = display.reveal
        val line = mid - dp(LINE_LIFT)
        val caret = (left + right) / 2
        val pitch = dp(LETTER_PITCH)
        val text = display.line
        if (text != null) {
            // The real text: a dot for each character, a gap for each space, and nothing past either end of the text.
            for (index in text.indices) {
                if (text[index].isWhitespace()) continue
                val x = caret + (index - display.caretAt - display.shownStep + .5f) * pitch
                if (x < left || x > right) continue
                letterDot(canvas, x, caret, left, right, line, look, alpha)
            }
        } else {
            val moved = display.letters + display.flow
            val reach = ceil((right - left) / 2 / pitch).toInt() + 1
            val start = floor(moved).toInt() - reach
            for (index in start..start + 2 * reach + 1) {
                if (isSpace(index)) continue
                val x = caret + (index - moved + .5f) * pitch
                if (x < left || x > right) continue
                letterDot(canvas, x, caret, left, right, line, look, alpha)
            }
        }
        bar(canvas, caret, line, dp(CARET_HEIGHT), GuideMotion.faded(look.accent, alpha))
        // The track: the whole keyboard across, the dead zone in the middle of Space, and the finger.
        val track = mid + dp(TRACK_DROP)
        val across = right - left
        rounded(canvas, left, track - dp(TRACK / 2), right, track + dp(TRACK / 2), dp(TRACK / 2), GuideMotion.faded(look.ink, FAINT * alpha))
        rounded(canvas, left + across * display.spaceStart, track - dp(DEAD_HEIGHT / 2), left + across * display.spaceEnd,
            track + dp(DEAD_HEIGHT / 2), dp(DEAD_HEIGHT / 2), GuideMotion.faded(look.ink, FAINT * alpha))
        val finger = left + across * display.marker
        if (display.leaning != 0) {
            val from = left + across * if (display.leaning < 0) display.spaceStart else display.spaceEnd
            val thickness = dp(LEAN + LEAN_GROW * display.intensity)
            rounded(canvas, min(from, finger), track - thickness / 2, maxOf(from, finger), track + thickness / 2, thickness / 2,
                GuideMotion.faded(look.accent, alpha))
        }
        dot(canvas, finger, track, dp(FINGER + FINGER_GROW * display.intensity), GuideMotion.faded(look.accent, alpha))
    }

    /** One letter of the line, fading towards the ends so the eye stays on the caret. */
    private fun letterDot(canvas: Canvas, x: Float, caret: Float, left: Float, right: Float, line: Float, look: Look, alpha: Float) {
        val fade = 1f - abs(x - caret) / ((right - left) / 2)
        dot(canvas, x, line, dp(LETTER_DOT), GuideMotion.faded(look.ink, (LETTER + (1f - LETTER) * .4f * fade) * fade * alpha))
    }

    /** A row of words, with the selection over them. A word drag shows it growing and shrinking by whole words. */
    private fun words(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, mid: Float, look: Look) {
        val slot = (right - left) / GestureDisplay.VISIBLE_WORDS
        val centre = (left + right) / 2
        val alpha = display.reveal
        // The selection goes over the words that are not in it, so a word that it reaches goes under it.
        wordRow(canvas, display, slot, centre, mid, selected = false, GuideMotion.faded(look.ink, WORD * alpha))
        var start = centre + (display.spanStart - display.camera) * slot
        var end = centre + (display.spanEnd - display.camera) * slot
        if (end <= start) return
        start -= dp(SELECTION_PAD)
        end += dp(SELECTION_PAD)
        rounded(canvas, start, mid - dp(SELECTION_HEIGHT / 2), end, mid + dp(SELECTION_HEIGHT / 2), radius,
            GuideMotion.faded(look.accent, alpha))
        wordRow(canvas, display, slot, centre, mid, selected = true, GuideMotion.faded(look.onAccent, SELECTED * alpha))
        // The end that moves has the caret on it.
        bar(canvas, if (display.leadingRight) end + dp(CARET_GAP) else start - dp(CARET_GAP), mid, dp(CARET_HEIGHT), GuideMotion.faded(look.accent, alpha))
    }

    private fun wordRow(canvas: Canvas, display: GestureDisplay, slot: Float, centre: Float, mid: Float, selected: Boolean, colour: Int) {
        val first = floor(display.camera - GestureDisplay.VISIBLE_WORDS / 2).toInt() - 1
        val last = ceil(display.camera + GestureDisplay.VISIBLE_WORDS / 2).toInt() + 1
        for (index in first..last) {
            if ((index > display.spanStart && index < display.spanEnd) != selected) continue
            val x = centre + (index - display.camera) * slot
            val half = slot * display.wordWidth(index) / 2
            word(canvas, x - half, x + half, mid, colour)
        }
    }

    /** Words that run into the caret and go, one for each word that a delete takes. The word that is going takes the accent. */
    private fun delete(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, mid: Float, look: Look) {
        val caret = right - dp(CARET_END)
        val stop = caret - dp(WORD_GAP)
        val slot = (right - left) / GestureDisplay.VISIBLE_WORDS
        val alpha = display.reveal
        val first = floor(display.shownDeleted).toInt()
        for (index in first..first + GestureDisplay.VISIBLE_WORDS.toInt() + 1) {
            // A single flick's word is drawn on its own, as it lifts off; the words before it close the gap.
            if (display.softDelete && index == 0) continue
            val x = stop - slot / 2 - (index - display.shownDeleted) * slot
            val half = slot * widthOf(index) / 2
            if (x - half >= stop || x + half < left) continue
            val going = x + half > stop && !display.softDelete
            val colour = if (going) GuideMotion.faded(look.accent, alpha) else GuideMotion.faded(look.ink, WORD * alpha)
            word(canvas, maxOf(x - half, left), min(x + half, stop), mid, colour)
        }
        if (display.softDelete) dissolve(canvas, stop - slot / 2, slot * widthOf(0) / 2, mid, display.lift, alpha, look)
        // The caret beats once for each word, quicker for a longer flick.
        val beat = 1f - BEAT * display.flow
        bar(canvas, caret, mid, dp(CARET_HEIGHT), GuideMotion.faded(look.accent, beat * alpha))
    }

    private fun isSpace(index: Int): Boolean {
        var at = index.mod(GestureDisplay.PATTERN_LETTERS)
        for (length in GestureDisplay.WORD_LETTERS) {
            if (at < length) return false
            if (at == length) return true
            at -= length + 1
        }
        return true
    }

    /**
     * A single flick's word, centred on [x] and [half] wide each way: it lifts off the line and breaks into pieces that rise, spread,
     * shrink and fade as [lift] goes from 0 to 1.
     */
    private fun dissolve(canvas: Canvas, x: Float, half: Float, mid: Float, lift: Float, alpha: Float, look: Look) {
        val up = 1f - (1f - lift) * (1f - lift)
        val piece = 2 * half / PIECES
        for (i in 0 until PIECES) {
            val from = i - (PIECES - 1) / 2f
            val cx = x + from * piece + from * up * dp(PIECE_SPREAD)
            val cy = mid - up * dp(LIFT_HEIGHT) - (if (i % 2 == 0) up * dp(PIECE_RISE) else 0f)
            val size = 1f - lift * SHRINK
            val gone = (lift * 1.15f - i * .03f).coerceIn(0f, 1f)
            val shownHalf = piece / 2 * size - dp(1f)
            if (shownHalf <= 0f) continue
            word(canvas, cx - shownHalf, cx + shownHalf, cy, GuideMotion.faded(look.accent, alpha * (1f - gone)), size)
        }
    }

    private fun word(canvas: Canvas, start: Float, end: Float, mid: Float, colour: Int, size: Float = 1f) {
        if (end <= start) return
        val half = dp(WORD_HEIGHT / 2) * size
        rounded(canvas, start, mid - half, end, mid + half, half, colour)
    }

    /** The caret: a short upright bar centred on [x], [y]. */
    private fun bar(canvas: Canvas, x: Float, y: Float, height: Float, colour: Int) =
        rounded(canvas, x - dp(CARET_WIDTH / 2), y - height / 2, x + dp(CARET_WIDTH / 2), y + height / 2, dp(CARET_WIDTH / 2), colour)

    private fun dot(canvas: Canvas, x: Float, y: Float, radius: Float, colour: Int) {
        fill.color = colour
        canvas.drawCircle(x, y, radius, fill)
    }

    private fun rounded(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float, colour: Int) {
        fill.color = colour
        box.set(left, top, right, bottom)
        canvas.drawRoundRect(box, radius, radius, fill)
    }

    private fun widthOf(index: Int) = WORD_WIDTHS[index.mod(WORD_WIDTHS.size)]

    private fun dp(value: Float) = value * density

    private companion object {
        /** How much of a word's slot each word takes, in a fixed pattern that reads as a line of text. */
        val WORD_WIDTHS = floatArrayOf(.62f, .82f, .5f, .74f, .88f, .56f, .7f)
        const val REST_SCALE = .86f
        const val SIDE = 10f
        const val LINE_LIFT = 5f
        const val LETTER_PITCH = 9f
        const val LETTER_DOT = 2.2f
        const val LETTER = .3f
        const val CARET_WIDTH = 2.5f
        const val CARET_HEIGHT = 24f
        const val CARET_GAP = 4f
        const val CARET_END = 8f
        const val TRACK_DROP = 17f
        const val TRACK = 2f
        const val DEAD_HEIGHT = 6f
        const val LEAN = 2.5f
        const val LEAN_GROW = 2f
        const val FINGER = 4f
        const val FINGER_GROW = 1.5f
        const val FAINT = .18f
        const val WORD = .28f
        const val SELECTED = .85f
        const val WORD_HEIGHT = 11f
        /** How much of its height a single flick's word loses by the time it has dissolved. */
        const val SHRINK = .6f
        /** A single flick's word breaks into this many pieces, which rise this high, spread this far apart, and every other one higher. */
        const val PIECES = 5
        const val LIFT_HEIGHT = 12f
        const val PIECE_SPREAD = 5f
        const val PIECE_RISE = 4f
        const val WORD_GAP = 8f
        const val SELECTION_HEIGHT = 26f
        const val SELECTION_PAD = 5f
        const val BEAT = .5f
    }
}
