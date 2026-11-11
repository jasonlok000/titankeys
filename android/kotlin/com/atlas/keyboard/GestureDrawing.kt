package com.atlas.keyboard

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glowHeight = 0f
    private var glowColour = 0

    fun draw(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, height: Int, look: Look) {
        val h = height.toFloat()
        if (display.scene != GestureDisplay.Scene.None) scene(canvas, display, left, right, h, look)
        if (display.glowing) glow(canvas, display, left, right, h, look)
    }

    private fun scene(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, h: Float, look: Look) {
        // The words stay where they are, under a cover in the strip's own colour, so nothing moves when this comes and goes. A swipe
        // and a delete's dots only dim them, so the suggestions still show, faded, behind.
        val cover = if (display.scene == GestureDisplay.Scene.Swipe || display.softDelete) SWIPE_DIM else 1f
        fill.color = GuideMotion.faded(look.paper, display.reveal * cover)
        canvas.drawRect(0f, 0f, right + left, h, fill)
        if (display.scene == GestureDisplay.Scene.Swipe) {
            // No settling here: the path stays on the keys' map as the finger draws it.
            swipe(canvas, display, left + dp(SIDE), right - dp(SIDE), h, look)
            return
        }
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
            GestureDisplay.Scene.Swipe, GestureDisplay.Scene.None -> Unit
        }
        canvas.restore()
    }

    /**
     * A word swipe's mini path over the dimmed words, in the accent colour, with a dot at the finger. Each stretch is as thick as the
     * touch was firm there. It all fades with [GestureDisplay.reveal]; while it fades it is drawn whole and then faded, so the round
     * ends where stretches meet never show as darker spots.
     */
    private fun swipe(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, h: Float, look: Look) {
        val count = display.pathSize
        if (count == 0) return
        val alpha = display.reveal
        val across = right - left
        val faded = alpha < 1f
        if (faded) canvas.saveLayerAlpha(0f, 0f, right + left, h, (alpha.coerceIn(0f, 1f) * 255).toInt())
        stroke.color = look.accent
        var x = left + across * display.pathX[0]
        var y = swipeY(display.pathY[0], h)
        for (index in 1 until count) {
            val nextX = left + across * display.pathX[index]
            val nextY = swipeY(display.pathY[index], h)
            stroke.strokeWidth = dp(PATH_WIDTH) * (display.pathWidth[index - 1] + display.pathWidth[index]) / 2
            canvas.drawLine(x, y, nextX, nextY, stroke)
            x = nextX
            y = nextY
        }
        dot(canvas, x, y, dp(PATH_FINGER) * display.pathWidth[count - 1].coerceAtLeast(1f), look.accent)
        if (faded) canvas.restore()
    }

    /** A touchpad height, from the top row down to the bottom of the letter rows, as a height on the strip. */
    private fun swipeY(y: Float, h: Float): Float {
        val row = dp(PATH_ROW)
        val top = h / 2 - row
        return (top + (y / .75f * 3 - .5f) * row).coerceIn(dp(PATH_EDGE), h - dp(PATH_EDGE))
    }

    /**
     * The Space glow: soft orange rising from the strip's bottom edge under the middle word, as wide and as strong as the taps so far
     * make it, with a thin line along the bottom on the third tap.
     */
    private fun glow(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, h: Float, look: Look) {
        if (display.glowAlpha <= 0f) return
        val tall = dp(GLOW_HEIGHT)
        if (glowHeight != h || glowColour != look.accent) {
            // Made again only when the strip or the colour changes, so a frame makes no garbage.
            glowHeight = h
            glowColour = look.accent
            glow.shader = LinearGradient(0f, h, 0f, h - tall, look.accent, GuideMotion.faded(look.accent, 0f), Shader.TileMode.CLAMP)
        }
        val centre = (left + right) / 2
        val half = (right - left) * display.glowWidth / 2
        glow.alpha = (display.glowAlpha.coerceIn(0f, 1f) * 255).toInt()
        box.set(centre - half, h - tall, centre + half, h + tall)
        canvas.drawOval(box, glow)
        if (display.glowPulse > 0f) {
            fill.color = GuideMotion.faded(look.accent, PULSE * display.glowPulse * display.glowAlpha / GestureDisplay.GLOW_ALPHAS[3])
            canvas.drawRect(centre - half, h - dp(PULSE_HEIGHT), centre + half, h, fill)
        }
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
        if (display.softDelete) {
            // The dots show only the orange part: a dot for each character that went, and the caret.
            val morphing = display.fromHold && display.dotsIn < 1f
            val pulse = if (morphing) { morph(canvas, display, left, stop, slot, mid, alpha, look); 0f }
                else dots(canvas, display, left, stop, caret, mid, alpha, look)
            // A lifted hold's caret beat settles while its lines turn into dots.
            val beat = if (morphing) 1f - BEAT * display.morphFlow * (1f - display.dotsIn) else 1f
            bar(canvas, caret, mid, dp(CARET_HEIGHT) * (1f + CARET_PULSE * pulse), GuideMotion.faded(look.accent, beat * alpha))
            return
        }
        lines(display.shownDeleted, left, stop, slot) { start, end, going ->
            word(canvas, start, end, mid, if (going) GuideMotion.faded(look.accent, alpha) else GuideMotion.faded(look.ink, WORD * alpha))
        }
        // The caret beats once for each word, quicker for a longer flick.
        val beat = 1f - BEAT * display.flow
        bar(canvas, caret, mid, dp(CARET_HEIGHT), GuideMotion.faded(look.accent, beat * alpha))
    }

    /** A hold's lines with [shown] words gone, nearest the caret first: each line's ends, and whether it is the one going now. */
    private inline fun lines(shown: Float, left: Float, stop: Float, slot: Float, each: (Float, Float, Boolean) -> Unit) {
        val first = floor(shown).toInt()
        for (index in first..first + GestureDisplay.VISIBLE_WORDS.toInt() + 1) {
            val x = stop - slot / 2 - (index - shown) * slot
            val half = slot * widthOf(index) / 2
            if (x - half >= stop || x + half < left) continue
            each(maxOf(x - half, left), min(x + half, stop), x + half > stop)
        }
    }

    /**
     * How far apart the dots are: [DOT_STEP], or closer so that the dots still to land and their word gaps fit between [left] and
     * [stop]. The row follows the queue smoothly as deletes join it.
     */
    private fun dotStep(display: GestureDisplay, left: Float, stop: Float) =
        min(dp(DOT_STEP), (stop - left) / display.shownUnits.coerceAtLeast(1f))

    /**
     * A lifted hold: each of its lines, nearest the caret first, shrinks to the run of dots of one deleted word, in place, while the
     * dots come up inside it and the line fades; a word with no line left on the bar has its dots pop in. With no jump at the
     * start, it is the hold's own picture; at the end, the dots' first frame.
     */
    private fun morph(canvas: Canvas, display: GestureDisplay, left: Float, stop: Float, slot: Float, mid: Float, alpha: Float, look: Look) {
        val m = display.dotsIn.let { it * it * (3 - 2 * it) }
        val step = dotStep(display, left, stop)
        val radius = dp(DOT_RADIUS)
        val groups = display.groups
        var line = 0
        var placed = 0
        lines(display.morphFrom, left, stop, slot) { start, end, going ->
            if (line >= groups.size) return@lines
            val firstSlot = placed + line
            val count = groups[line]
            // The dots' run for this word: from its farthest dot to its nearest.
            val near = stop - step / 2 - firstSlot * step
            val far = stop - step / 2 - (firstSlot + count - 1) * step
            val from = if (going) look.accent else look.ink
            val strength = if (going) 1f else WORD + (1f - WORD) * m
            val fade = 1f - ((m - .45f) / .55f).coerceIn(0f, 1f)
            val half = dp(WORD_HEIGHT / 2) + (radius - dp(WORD_HEIGHT / 2)) * m
            val a = start + (far - radius - start) * m
            val b = end + (near + radius - end) * m
            if (fade > 0f) rounded(canvas, a, mid - half, maxOf(a, b), mid + half, half,
                GuideMotion.faded(blend(from, look.accent, m), strength * fade * alpha))
            val show = ((m - .3f) / .7f).coerceIn(0f, 1f)
            for (k in 0 until count) {
                val home = stop - step / 2 - (firstSlot + k) * step
                // Spread along the line at first, and at their own places at the end.
                val spread = if (count == 1) (start + end) / 2 else end - (end - start) * k / (count - 1)
                dot(canvas, spread + (home - spread) * m, mid, radius * show, GuideMotion.faded(look.accent, alpha))
            }
            placed += count
            line++
        }
        // The words past the lines on the bar pop in at their places.
        for (g in line until groups.size) {
            for (k in 0 until groups[g]) {
                val home = stop - step / 2 - (placed + g + k) * step
                if (home < left) continue
                dot(canvas, home, mid, radius * m, GuideMotion.faded(look.accent, alpha))
            }
            placed += groups[g]
        }
    }

    /** [from] to [to], a part [t] of the way, channel by channel. */
    private fun blend(from: Int, to: Int, t: Float): Int {
        fun mix(shift: Int) = (((from shr shift) and 0xFF) + (((to shr shift) and 0xFF) - ((from shr shift) and 0xFF)) * t).toInt() shl shift
        return mix(24) or mix(16) or mix(8) or mix(0)
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
     * The dots, one for each character deleted, in a row that ends at [stop] beside the caret, with a gap between words. They pop
     * in one after another from the caret outwards and stay. At the end each one, nearest first, winds back a hair, then shoots
     * into the caret with a small swell and is gone. Returns how hard the caret pulses now, from the dots that just arrived, 0 to 1.
     */
    private fun dots(canvas: Canvas, display: GestureDisplay, left: Float, stop: Float, caret: Float, mid: Float, alpha: Float, look: Look): Float {
        val count = display.dots
        val step = dotStep(display, left, stop)
        var pulse = 0f
        for (i in 0 until count) {
            // Each word before this dot's word leaves one gap; the row's place next to the caret moves on as the queue does.
            val place = display.dotPlace(i) - display.shownBase
            val popped = display.dotPopped(i)
            // Each dot has its own part of the way out; it lands in the caret at 1.
            val travel = display.dotTravel(i)
            if (travel >= 1f) {
                // Landed: the caret pulses for a moment.
                pulse = max(pulse, 1f - ((travel - 1f) / DOT_PULSE).coerceIn(0f, 1f))
                continue
            }
            if (popped <= 0f) continue
            val u = travel.coerceIn(0f, 1f)
            // A back-in ease: a small step back, then quickly into the caret.
            val along = u * u * ((BACK + 1) * u - BACK)
            val home = stop - step / 2 - place * step
            val x = home + (caret - home) * along
            val swell = 1f + DOT_SWELL * sin(u * PI.toFloat())
            val size = popped * (1f + .25f * (1f - popped)) * swell * (1f - u * u)
            if (size <= 0f) continue
            dot(canvas, x, mid, dp(DOT_RADIUS) * size, GuideMotion.faded(look.accent, alpha))
        }
        return pulse
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
        /** A swipe dims the words to about an eighth, as in the owner's mockup; they are the path's backdrop. */
        const val SWIPE_DIM = .88f
        /** The path's three rows of letters lie this far apart on the strip, and it keeps this far from the strip's edges. */
        const val PATH_ROW = 14f
        const val PATH_EDGE = 4.5f
        const val PATH_WIDTH = 1.6f
        const val PATH_FINGER = 2.8f
        const val GLOW_HEIGHT = 10f
        const val PULSE = .25f
        const val PULSE_HEIGHT = 1f
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
        /** The dots: their size and how far apart they are at most. */
        const val DOT_RADIUS = 1.7f
        const val DOT_STEP = 6f
        /** On their way into the caret: how far each winds back, and how much it swells. How much later the farthest leaves is in GestureDisplay. */
        const val BACK = 1.4f
        const val DOT_SWELL = .3f
        /** How long the caret pulses for each dot that lands, as a part of the way out, and how much taller it gets. */
        const val DOT_PULSE = .15f
        const val CARET_PULSE = .35f
        const val WORD_GAP = 8f
        const val SELECTION_HEIGHT = 26f
        const val SELECTION_PAD = 5f
        const val BEAT = .5f
    }
}
