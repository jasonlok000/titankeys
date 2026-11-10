package com.atlas.keyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * Draws a [GestureDisplay] in the place of the words, at the strip's own height. It is all shapes, so no font size changes it,
 * and everything it uses is made once, so a frame makes no garbage.
 *
 * One shape carries each gesture: an orange capsule with the thin diagonal cream line of the app icon. For the cursor it
 * stretches from Space to the finger. For a word drag it is the selection. For a delete it is what the words run into.
 */
internal class GestureDrawing(private val density: Float) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val box = RectF()
    private val capsule = Path()

    fun draw(canvas: Canvas, display: GestureDisplay, width: Int, height: Int, dark: Boolean) {
        val ink = if (dark) Color.WHITE else Color.BLACK
        val paper = if (dark) Color.BLACK else Color.WHITE
        val w = width.toFloat()
        val h = height.toFloat()
        // The words stay where they are, under a cover in the strip's own colour, so nothing moves when this comes and goes.
        fill.color = GuideMotion.faded(paper, display.reveal)
        canvas.drawRect(0f, 0f, w, h, fill)
        val mid = h / 2
        val settle = REST_SCALE + (1f - REST_SCALE) * display.reveal
        canvas.save()
        canvas.scale(1f, settle, w / 2, mid)
        when (display.scene) {
            GestureDisplay.Scene.Cursor -> cursor(canvas, display, w, mid, ink)
            GestureDisplay.Scene.WordDrag, GestureDisplay.Scene.WordSelected -> words(canvas, display, w, mid, ink)
            GestureDisplay.Scene.DeleteHold, GestureDisplay.Scene.DeleteFlick -> delete(canvas, display, w, mid, ink)
            GestureDisplay.Scene.None -> Unit
        }
        canvas.restore()
    }

    /** A track of the keyboard with Space in the middle, and the capsule from the end of Space to the finger. */
    private fun cursor(canvas: Canvas, display: GestureDisplay, width: Float, mid: Float, ink: Int) {
        val left = dp(SIDE)
        val across = width - 2 * left
        val alpha = display.reveal
        rounded(canvas, left, mid - dp(TRACK / 2), left + across, mid + dp(TRACK / 2), dp(TRACK / 2), GuideMotion.faded(ink, FAINT * alpha))
        rounded(canvas, left + across * display.spaceStart, mid - dp(SPACE_HEIGHT / 2), left + across * display.spaceEnd, mid + dp(SPACE_HEIGHT / 2),
            dp(SPACE_RADIUS), GuideMotion.faded(ink, FAINT * alpha))
        val thickness = dp(BAND + BAND_GROW * display.intensity)
        var start = left + across * display.bandStart
        var end = left + across * display.bandEnd
        // The rounded end sits on the finger, not past it.
        if (display.leaning < 0) start -= thickness / 2 else if (display.leaning > 0) end += thickness / 2
        if (end - start < thickness * PILL) {
            val centre = (start + end) / 2
            start = centre - thickness * PILL / 2
            end = centre + thickness * PILL / 2
        }
        band(canvas, start, end, mid, thickness, display.flow, alpha)
    }

    /** A row of words, with the selection over them. A word drag shows it growing and shrinking by whole words. */
    private fun words(canvas: Canvas, display: GestureDisplay, width: Float, mid: Float, ink: Int) {
        val slot = (width - 2 * dp(SIDE)) / GestureDisplay.VISIBLE_WORDS
        val centre = width / 2
        val alpha = display.reveal
        // The selection goes over the words that are not in it, so a word that it reaches goes under it.
        wordRow(canvas, display, slot, centre, mid, selected = false, GuideMotion.faded(ink, WORD * alpha))
        var start = centre + (display.spanStart - display.camera) * slot
        var end = centre + (display.spanEnd - display.camera) * slot
        if (end <= start) return
        // The end that moves has a handle past the last word, with the cream line in it.
        if (display.leadingRight) end += dp(HANDLE) else start -= dp(HANDLE)
        rounded(canvas, start, mid - dp(SELECTION_HEIGHT / 2), end, mid + dp(SELECTION_HEIGHT / 2), dp(SELECTION_HEIGHT / 2), GuideMotion.faded(GuideColours.ORANGE, alpha))
        wordRow(canvas, display, slot, centre, mid, selected = true, GuideMotion.faded(GuideColours.CREAM, alpha))
        slash(canvas, if (display.leadingRight) end - dp(HANDLE_SLASH) else start + dp(HANDLE_SLASH), mid, dp(HANDLE_SLASH_HALF), alpha)
    }

    private fun wordRow(canvas: Canvas, display: GestureDisplay, slot: Float, centre: Float, mid: Float, selected: Boolean, colour: Int) {
        val first = floor(display.camera - GestureDisplay.VISIBLE_WORDS / 2).toInt() - 1
        val last = ceil(display.camera + GestureDisplay.VISIBLE_WORDS / 2).toInt() + 1
        for (index in first..last) {
            if ((index > display.spanStart && index < display.spanEnd) != selected) continue
            val x = centre + (index - display.camera) * slot
            val half = slot * widthOf(index) / 2
            word(canvas, x - half, x + half, mid, colour)
        }
    }

    /** Words that run into the capsule and go under it, one for each word that goes. */
    private fun delete(canvas: Canvas, display: GestureDisplay, width: Float, mid: Float, ink: Int) {
        val right = width - dp(SIDE)
        val capsuleStart = right - dp(ERASER)
        val slot = (width - 2 * dp(SIDE)) / GestureDisplay.VISIBLE_WORDS
        val alpha = display.reveal
        val colour = GuideMotion.faded(ink, WORD * alpha)
        val stop = capsuleStart - dp(WORD_GAP)
        val first = floor(display.shownDeleted).toInt()
        for (index in first..first + GestureDisplay.VISIBLE_WORDS.toInt() + 1) {
            val x = stop - slot / 2 - (index - display.shownDeleted) * slot
            val half = slot * widthOf(index) / 2
            if (x - half < stop) word(canvas, x - half, min(x + half, stop), mid, colour)
        }
        band(canvas, capsuleStart, right, mid, dp(ERASER_HEIGHT), display.flow, alpha)
    }

    private fun word(canvas: Canvas, start: Float, end: Float, mid: Float, colour: Int) =
        rounded(canvas, start, mid - dp(WORD_HEIGHT / 2), end, mid + dp(WORD_HEIGHT / 2), dp(WORD_HEIGHT / 2), colour)

    /** The orange capsule with its cream lines. */
    private fun band(canvas: Canvas, start: Float, end: Float, mid: Float, thickness: Float, flow: Float, alpha: Float) {
        val half = thickness / 2
        val top = mid - half
        val bottom = mid + half
        rounded(canvas, start, top, end, bottom, half, GuideMotion.faded(GuideColours.ORANGE, alpha))
        box.set(start, top, end, bottom)
        capsule.reset()
        capsule.addRoundRect(box, half, half, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(capsule)
        val gap = dp(SLASH_GAP)
        var x = start - gap + flow * gap - half * SLANT
        while (x < end + half * SLANT) {
            slash(canvas, x, mid, half, alpha)
            x += gap
        }
        canvas.restore()
    }

    /** The thin diagonal cream line of the app icon, centred on [x]. */
    private fun slash(canvas: Canvas, x: Float, mid: Float, half: Float, alpha: Float) {
        line.color = GuideMotion.faded(GuideColours.CREAM, alpha)
        line.strokeWidth = dp(SLASH)
        canvas.drawLine(x - half * SLANT, mid + half, x + half * SLANT, mid - half, line)
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
        const val SIDE = 14f
        const val TRACK = 2f
        const val SPACE_HEIGHT = 24f
        const val SPACE_RADIUS = 7f
        const val BAND = 18f
        const val BAND_GROW = 10f
        /** A capsule on Space is this many times as wide as it is tall. */
        const val PILL = 1.6f
        const val FAINT = .16f
        const val WORD = .28f
        const val WORD_HEIGHT = 11f
        const val WORD_GAP = 10f
        const val SELECTION_HEIGHT = 30f
        const val HANDLE = 18f
        const val HANDLE_SLASH = 11f
        const val HANDLE_SLASH_HALF = 7f
        const val ERASER = 56f
        const val ERASER_HEIGHT = 28f
        const val SLASH_GAP = 18f
        const val SLASH = 2f
        /** The cream line in the app icon runs 505 across for 431 up. */
        const val SLANT = 1.17f
    }
}
