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
 * For the cursor the line of letters moves past the caret at the cursor's speed, and the Space glow lies under it, brighter
 * on the side it leans to. For a word drag the selection grows over the words. For a delete the words run into the caret and go.
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
    private var glowTall = 0f
    private var glowColour = 0
    /** A held delete's glow along the bar's right edge: the underglow turned on its side, fading in from the edge. */
    private val edgeGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private var edgeAt = Float.NaN
    private var edgeColour = 0
    private var edgeTall = 0f
    /** A held delete's glow along the top, fading down from it. */
    private val top = Paint(Paint.ANTI_ALIAS_FLAG)
    private var topColour = 0
    private var topTall = 0f

    fun draw(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, height: Int, look: Look) {
        val h = height.toFloat()
        if (display.scene != GestureDisplay.Scene.None) scene(canvas, display, left, right, h, look)
        if (display.glowing) {
            if (display.glowDelete) deleteGlow(canvas, display, left, right, h, look) else glow(canvas, display, left, right, h, look)
        }
    }

    private fun scene(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, h: Float, look: Look) {
        // The words stay where they are, under a cover in the strip's own colour, so nothing moves when this comes and goes. A swipe
        // and a delete's dots only dim them, so the suggestions still show, faded, behind; after a hold they come out from under
        // its full cover.
        val cover = when {
            display.scene == GestureDisplay.Scene.Swipe -> SWIPE_DIM
            display.softDelete -> SWIPE_DIM + (1f - SWIPE_DIM) * display.solid
            else -> 1f
        }
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
     * The Space glow: soft orange rising from the strip's bottom edge under the middle word, or under the finger of a held delete,
     * as wide and as strong as the taps so far make it, with a thin line along the bottom on the third tap. It is one glow with
     * no edges: thin upright slices, each fading upwards, whose strength follows one smooth curve across, so no oval or step shows.
     */
    private fun glow(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, h: Float, look: Look) {
        val strength = display.glowAlpha.coerceIn(0f, 1f)
        if (strength <= 0f) return
        // A triple press's glow, held or selecting, is a little thicker than steering's.
        val tall = dp(GLOW_HEIGHT) * if (display.glowThick) THICK else 1f
        if (glowHeight != h || glowColour != look.accent || glowTall != tall) {
            // Made again only when the strip, the colour or the thickness changes, so a frame makes no garbage.
            glowHeight = h
            glowTall = tall
            glowColour = look.accent
            glow.shader = LinearGradient(0f, h, 0f, h - tall, look.accent, GuideMotion.faded(look.accent, 0f), Shader.TileMode.CLAMP)
        }
        val across = right - left
        val half = across * display.glowWidth / 2
        if (half < 1f) return
        // Under the middle for Space, under the finger for a held delete, and always whole on the strip.
        val reach = min(half, across / 2)
        val centre = (left + across * display.glowCentre).coerceIn(left + reach, right - reach)
        // While steering, the glow is a soft underglow across the whole strip, brighter on the side you steer towards: at a full
        // lean that side comes to about 2.5 times the other, see [UNDERGLOW] and [SIDE_BOOST]. A held triple press and its select
        // have the same look and follow the hand the same way.
        val steering = display.scene == GestureDisplay.Scene.Cursor || display.glowThick
        val base = if (steering) UNDERGLOW else 1f
        val lean = if (steering) display.steerLean.coerceIn(-1f, 1f) else 0f
        // A small glow is a soft hump; as it grows to the whole strip it flattens, with soft ends.
        val wide = ((display.glowWidth - WIDE_FROM) / (1f - WIDE_FROM)).coerceIn(0f, 1f)
        val pulse = if (display.glowPulse > 0f) PULSE * display.glowPulse * strength / GestureDisplay.GLOW_ALPHAS[3] else 0f
        val slice = max(1f, floor(dp(GLOW_SLICE)))
        var x = floor(centre - half)
        val end = ceil(centre + half)
        while (x < end) {
            val u = ((x + slice / 2 - centre) / half).coerceIn(-1f, 1f)
            val shape = glowShape(u, wide)
            if (shape > 0f) {
                var level = base * shape
                if (lean != 0f) {
                    // Only the half it leans to brightens, rising smoothly from nothing in the middle.
                    val toward = max(0f, if (lean < 0) -u else u)
                    level *= 1f + SIDE_BOOST * abs(lean) * toward * toward
                }
                glow.alpha = (strength * (if (lean != 0f) softCap(level) else level.coerceAtMost(1f)) * 255).toInt()
                if (glow.alpha > 0) canvas.drawRect(x, h - tall, x + slice, h, glow)
                if (pulse > 0f) {
                    fill.color = GuideMotion.faded(look.accent, pulse * shape)
                    canvas.drawRect(x, h - dp(PULSE_HEIGHT), x + slice, h, fill)
                }
            }
            x += slice
        }
    }

    /**
     * A held delete's glow: the steering underglow, as soft and in the same orange, rooted where Backspace is or under the finger,
     * in the place the Haptics lab picks ([GestureDisplay.DeleteGlow]). It gets stronger with the pace, up to what steering's
     * leaning side reaches, and stops changing at the top-speed line. In the pause zone it fades out: see [GestureDisplay.glowDelete].
     */
    private fun deleteGlow(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, h: Float, look: Look) {
        val strength = display.glowAlpha.coerceIn(0f, 1f)
        if (strength <= 0f) return
        val level = softCap(UNDERGLOW * (1f + DELETE_BOOST * display.shownPace))
        val tall = dp(GLOW_HEIGHT)
        when (display.deleteGlowPlace) {
            GestureDisplay.DeleteGlow.RightEdge -> edgeBand(canvas, left, right, h, look, tall, strength * level)
            GestureDisplay.DeleteGlow.RightEdgeThick ->
                edgeBand(canvas, left, right, h, look, tall * THICKER, strength * softCap(level * THICKER_LEVEL))
            GestureDisplay.DeleteGlow.Bottom -> cornerRun(canvas, display, left, right, h, look, tall, strength * level, bottom = true)
            GestureDisplay.DeleteGlow.Top -> cornerRun(canvas, display, left, right, h, look, tall, strength * level, bottom = false)
            GestureDisplay.DeleteGlow.Corner -> {
                edgeBand(canvas, left, right, h, look, tall, strength * level)
                cornerRun(canvas, display, left, right, h, look, tall, strength * level, bottom = true)
            }
            GestureDisplay.DeleteGlow.Finger -> underFinger(canvas, display, left, right, h, look, tall, strength)
        }
    }

    /** Along the right edge of the bar, top to bottom, [tall] thick, with the soft ends of a wide underglow. */
    private fun edgeBand(canvas: Canvas, left: Float, right: Float, h: Float, look: Look, tall: Float, alpha: Float) {
        val edge = right + left
        if (edgeAt != edge || edgeColour != look.accent || edgeTall != tall) {
            // Made again only when the strip, the colour or the thickness changes, so a frame makes no garbage.
            edgeAt = edge
            edgeColour = look.accent
            edgeTall = tall
            edgeGlow.shader = LinearGradient(edge, 0f, edge - tall, 0f, look.accent, GuideMotion.faded(look.accent, 0f), Shader.TileMode.CLAMP)
        }
        val slice = max(1f, floor(dp(GLOW_SLICE)))
        var y = 0f
        while (y < h) {
            val v = ((y + slice / 2) / h * 2 - 1).coerceIn(-1f, 1f)
            edgeGlow.alpha = (alpha * glowShape(v, 1f) * 255).toInt()
            if (edgeGlow.alpha > 0) canvas.drawRect(edge - tall, y, edge, min(y + slice, h), edgeGlow)
            y += slice
        }
    }

    /** Along the [bottom] or the top from the right corner leftwards, as far as the pace: full at the corner, soft where it ends. */
    private fun cornerRun(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, h: Float, look: Look, tall: Float,
                          alpha: Float, bottom: Boolean) {
        val paint = if (bottom) underGlow(h, look, tall) else topGlow(look, tall)
        val span = (right - left) * display.glowWidth
        if (span < 1f) return
        val from = right - span
        val slice = max(1f, floor(dp(GLOW_SLICE)))
        var x = floor(from)
        while (x < right) {
            val t = ((x + slice / 2 - from) / span).coerceIn(0f, 1f)
            val shape = (t / DELETE_SOFT).coerceAtMost(1f).let { it * it * (3 - 2 * it) }
            paint.alpha = (alpha * shape * 255).toInt()
            if (paint.alpha > 0) canvas.drawRect(x, if (bottom) h - tall else 0f, min(x + slice, right), if (bottom) h else tall, paint)
            x += slice
        }
    }

    /**
     * Along the bottom under the finger, as wide as [FINGER_SPAN] of the bar, leaning left as steering leans towards its side:
     * the left half brightens with the pace, from steering's lean at rest to its full lean at the top-speed line.
     */
    private fun underFinger(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, h: Float, look: Look, tall: Float, strength: Float) {
        val paint = underGlow(h, look, tall)
        val across = right - left
        val half = across * FINGER_SPAN / 2
        val centre = (left + across * display.glowCentre).coerceIn(left + half, right - half)
        val lean = GestureDisplay.LEAN_BASE + (1f - GestureDisplay.LEAN_BASE) * display.shownPace
        val slice = max(1f, floor(dp(GLOW_SLICE)))
        var x = floor(centre - half)
        val end = ceil(centre + half)
        while (x < end) {
            val u = ((x + slice / 2 - centre) / half).coerceIn(-1f, 1f)
            val shape = glowShape(u, 0f)
            if (shape > 0f) {
                val toward = max(0f, -u)
                paint.alpha = (strength * softCap(UNDERGLOW * shape * (1f + SIDE_BOOST * lean * toward * toward)) * 255).toInt()
                if (paint.alpha > 0) canvas.drawRect(x, h - tall, x + slice, h, paint)
            }
            x += slice
        }
    }

    /** The underglow's paint, fading up from the bottom over [tall]: made again only when something it is made from changes. */
    private fun underGlow(h: Float, look: Look, tall: Float): Paint {
        if (glowHeight != h || glowColour != look.accent || glowTall != tall) {
            glowHeight = h
            glowTall = tall
            glowColour = look.accent
            glow.shader = LinearGradient(0f, h, 0f, h - tall, look.accent, GuideMotion.faded(look.accent, 0f), Shader.TileMode.CLAMP)
        }
        return glow
    }

    /** The same, fading down from the top. */
    private fun topGlow(look: Look, tall: Float): Paint {
        if (topColour != look.accent || topTall != tall) {
            topColour = look.accent
            topTall = tall
            top.shader = LinearGradient(0f, 0f, 0f, tall, look.accent, GuideMotion.faded(look.accent, 0f), Shader.TileMode.CLAMP)
        }
        return top
    }

    /** [level], kept under 1 without a corner: as it is up to [CAP_FROM], then easing towards 1. */
    private fun softCap(level: Float) =
        if (level <= CAP_FROM) level else CAP_FROM + (1f - CAP_FROM) * (1f - kotlin.math.exp(-(level - CAP_FROM) / (1f - CAP_FROM)))

    /**
     * How strong the glow is at [u], from -1 at its left end to 1 at its right: a smooth hump for a small glow, flat with soft
     * ends for a [wide] one, and 0 at both ends, so it has no edge.
     */
    private fun glowShape(u: Float, wide: Float): Float {
        val inside = 1f - abs(u)
        if (inside <= 0f) return 0f
        val hump = 1f - u * u
        val soft = (inside / WIDE_SOFT).coerceAtMost(1f).let { it * it * (3 - 2 * it) }
        return hump + (soft - hump) * wide
    }

    /**
     * Steering: the real text as a line of dots through the middle that moves past the caret, and the caret in a filled accent pill
     * as tall as select's, in the dark colour, the other way round from select's dark word in its pill. The dots part round the
     * pill with a small gap, so a letter the cursor passes hops to the other side. Which way it leans shows in the underglow, see
     * [glow].
     *
     * The caret is a text cursor: thin, as tall as the strip's words, and always between the letters that the real cursor is
     * between. A step moves it there at once, as the text cursor moves, and the line then glides it back to the middle. It blinks
     * only when steering pauses, see [GestureDisplay.caretShown].
     */
    private fun cursor(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, mid: Float, look: Look) {
        val alpha = display.reveal
        val line = mid
        val caret = (left + right) / 2
        val pitch = dp(LETTER_PITCH)
        val pill = dp(STEER_PILL / 2)
        // How far each side's dots move out, so the nearest letter sits a small gap from the pill.
        val part = pill + dp(PILL_GAP) + dp(LETTER_DOT) - pitch / 2
        val text = display.line
        // Where the real cursor is: ahead of the middle by the steps the line has still to glide.
        var at = caret
        if (text != null) {
            val cursor = display.caretAt + display.stepped
            at = caret + (display.stepped - display.shownStep) * pitch
            // The real text: a dot for each character, a gap for each space, and nothing past either end of the text. The letters
            // part round the cursor, not the middle, so the pill always sits between the two letters it is between.
            for (index in text.indices) {
                if (text[index].isWhitespace()) continue
                val d = (index - display.caretAt - display.shownStep + .5f) * pitch
                val x = caret + d + if (index < cursor) -part else part
                if (x < left || x > right) continue
                letterDot(canvas, x, caret, left, right, line, look, alpha)
            }
        } else {
            val moved = display.letters + display.flow
            val reach = ceil((right - left) / 2 / pitch).toInt() + 1
            val start = floor(moved).toInt() - reach
            for (index in start..start + 2 * reach + 1) {
                if (isSpace(index)) continue
                val d = (index - moved + .5f) * pitch
                val x = caret + d + if (d < 0) -part else part
                if (x < left || x > right) continue
                letterDot(canvas, x, caret, left, right, line, look, alpha)
            }
        }
        rounded(canvas, at - pill, line - dp(SELECTION_HEIGHT / 2), at + pill, line + dp(SELECTION_HEIGHT / 2), radius,
            GuideMotion.faded(look.accent, alpha))
        if (display.caretShown) bar(canvas, at, line, dp(STEER_CARET_HEIGHT), GuideMotion.faded(look.onAccent, alpha), dp(STEER_CARET))
    }

    /**
     * One letter of the line: as strong as select's words far off and stronger next to the caret, so the eye stays on it, and
     * fading out only at the very ends.
     */
    private fun letterDot(canvas: Canvas, x: Float, caret: Float, left: Float, right: Float, line: Float, look: Look, alpha: Float) {
        val fade = 1f - abs(x - caret) / ((right - left) / 2)
        val strength = (WORD + (LETTER_NEAR - WORD) * fade * fade) * min(1f, fade * LETTER_ENDS)
        dot(canvas, x, line, dp(LETTER_DOT), GuideMotion.faded(look.ink, strength * alpha))
    }

    /** A row of words, with the selection over them. A word drag shows it growing and shrinking by whole words. */
    private fun words(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, mid: Float, look: Look) {
        val slot = (right - left) / GestureDisplay.VISIBLE_WORDS
        val centre = (left + right) / 2
        val alpha = display.reveal
        if (display.pickCaret) {
            // A triple press's pick: no box, only the row with the picked word a little stronger, and a thin solid caret after it.
            wordRow(canvas, display, slot, centre, mid, selected = false, GuideMotion.faded(look.ink, WORD * alpha))
            val x = centre - display.camera * slot
            val half = slot * display.wordWidth(0) / 2
            word(canvas, x - half, x + half, mid, GuideMotion.faded(look.ink, PICKED * alpha))
            bar(canvas, x + half + dp(CARET_GAP), mid, dp(STEER_CARET_HEIGHT), GuideMotion.faded(look.accent, alpha), dp(STEER_CARET))
            return
        }
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

    /**
     * Words that run into the caret and go, one for each word that a delete takes. A hold draws the real words before the cursor as
     * pills as long as each word, the one about to go a little stronger, as a triple press's pick is, and a thin solid caret. With
     * no text left it draws no words, only the caret, which blinks while the finger rests in the pause zone.
     */
    private fun delete(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, mid: Float, look: Look) {
        val caret = right - dp(CARET_END)
        val stop = caret - dp(WORD_GAP)
        val slot = (right - left) / GestureDisplay.VISIBLE_WORDS
        val alpha = display.reveal
        if (display.softDelete) {
            // The dots show only the orange part: a dot for each character that went, and the caret.
            clearBehindDots(canvas, display, left, stop, caret, mid, alpha, look)
            val morphing = display.fromHold && display.dotsIn < 1f
            val pulse = if (morphing) { morph(canvas, display, left, stop, slot, mid, alpha, look); 0f }
                else dots(canvas, display, left, stop, caret, mid, alpha, look)
            // A lifted hold's thin solid caret grows into the dots' own while its lines turn into dots.
            val thin = if (display.fromHold) 1f - display.dotsIn.let { it * it * (3 - 2 * it) } else 0f
            val height = dp(CARET_HEIGHT) + (dp(STEER_CARET_HEIGHT) - dp(CARET_HEIGHT)) * thin
            bar(canvas, caret, mid, height * (1f + CARET_PULSE * pulse), GuideMotion.faded(look.accent, alpha),
                dp(CARET_WIDTH) + (dp(STEER_CARET) - dp(CARET_WIDTH)) * thin)
            return
        }
        lines(display, display.shownDeleted, left, stop, slot) { start, end, going ->
            word(canvas, start, end, mid, GuideMotion.faded(look.ink, (if (going) PICKED else WORD) * alpha))
        }
        // A thin solid caret, as a triple press's pick has, that blinks in the pause zone.
        if (display.caretShown) bar(canvas, caret, mid, dp(STEER_CARET_HEIGHT), GuideMotion.faded(look.accent, alpha), dp(STEER_CARET))
    }

    /**
     * A hold's words with [shown] words gone, nearest the caret first: each word's ends, and whether it is the one about to go or
     * one already going into the caret. Each is as long as the real word: see [GestureDisplay.holdWordWidth].
     */
    private inline fun lines(display: GestureDisplay, shown: Float, left: Float, stop: Float, slot: Float, each: (Float, Float, Boolean) -> Unit) {
        val first = floor(shown).toInt()
        for (index in first..first + GestureDisplay.VISIBLE_WORDS.toInt() + 1) {
            val width = display.holdWordWidth(index)
            if (width <= 0f) continue
            val x = stop - slot / 2 - (index - shown) * slot
            val half = slot * width / 2
            if (x - half >= stop || x + half < left) continue
            each(maxOf(x - half, left), min(x + half, stop), index <= display.holdNext)
        }
    }

    /**
     * The words stay faded behind the dots, but right behind the row of dots they fade much further, in a band with soft edges,
     * so the dots read cleanly over a word while the rest of the bar keeps the faded look. The band covers the row still to land,
     * from its far end to just past the caret.
     */
    private fun clearBehindDots(canvas: Canvas, display: GestureDisplay, left: Float, stop: Float, caret: Float, mid: Float, alpha: Float, look: Look) {
        if (display.dots == 0 || alpha <= 0f) return
        val step = dotStep(display, left, stop)
        val far = max(left, stop - step * display.shownUnits)
        val pad = dp(BAND_PAD)
        val half = dp(DOT_RADIUS) * (1f + DOT_SWELL) + pad
        // Each layer is a little smaller than the one before, so together they make soft edges and reach [BAND_CLEAR] in the middle.
        val layer = 1f - Math.pow((1f - BAND_CLEAR).toDouble(), 1.0 / BAND_LAYERS).toFloat()
        for (index in 0 until BAND_LAYERS) {
            val inset = pad * index / BAND_LAYERS
            val top = mid - half + inset
            val bottom = mid + half - inset
            rounded(canvas, far - pad + inset, top, caret + pad - inset, bottom, (bottom - top) / 2, GuideMotion.faded(look.paper, layer * alpha))
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
        lines(display, display.morphFrom, left, stop, slot) { start, end, going ->
            if (line >= groups.size) return@lines
            val firstSlot = placed + line
            val count = groups[line]
            // The dots' run for this word: from its farthest dot to its nearest.
            val near = stop - step / 2 - firstSlot * step
            val far = stop - step / 2 - (firstSlot + count - 1) * step
            val from = look.ink
            val strength = (if (going) PICKED else WORD) + (1f - if (going) PICKED else WORD) * m
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
            // A dot that set off before the row slid past it keeps the place next to the caret, so none starts past the caret.
            val place = max(0f, display.dotPlace(i) - display.shownBase)
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
            // A row longer than the bar waits past its far end, and each dot fades in there as the row slides along.
            val edge = ((home - left) / step + 1f).coerceIn(0f, 1f)
            if (edge <= 0f) continue
            val x = home + (caret - home) * along
            val swell = 1f + DOT_SWELL * sin(u * PI.toFloat())
            val size = popped * (1f + .25f * (1f - popped)) * swell * (1f - u * u) * edge
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
    private fun bar(canvas: Canvas, x: Float, y: Float, height: Float, colour: Int, width: Float = dp(CARET_WIDTH)) =
        rounded(canvas, x - width / 2, y - height / 2, x + width / 2, y + height / 2, width / 2, colour)

    private fun dot(canvas: Canvas, x: Float, y: Float, radius: Float, colour: Int) {
        fill.color = colour
        canvas.drawCircle(x, y, radius, fill)
    }

    private fun rounded(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float, colour: Int) {
        fill.color = colour
        box.set(left, top, right, bottom)
        canvas.drawRoundRect(box, radius, radius, fill)
    }

    private fun dp(value: Float) = value * density

    private companion object {
        const val REST_SCALE = .86f
        /** A swipe dims the words to about an eighth, as in the owner's mockup; they are the path's backdrop. */
        const val SWIPE_DIM = .88f
        /**
         * Behind the dots the faded words fade further, to about 30% of how they show elsewhere: the band clears this much more of
         * them, over [BAND_LAYERS] soft layers that reach [BAND_PAD] past the dots.
         */
        const val BAND_CLEAR = .7f
        const val BAND_LAYERS = 4
        const val BAND_PAD = 4f
        /** The path's three rows of letters lie this far apart on the strip, and it keeps this far from the strip's edges. */
        const val PATH_ROW = 14f
        const val PATH_EDGE = 4.5f
        const val PATH_WIDTH = 1.6f
        const val PATH_FINGER = 2.8f
        const val GLOW_HEIGHT = 10f
        /** A triple press's glow is this much thicker than steering's. */
        const val THICK = 1.3f
        /**
         * A held delete's glow at the top-speed line is this much stronger than at the slowest: 2.5 times, about what steering's
         * glow reaches on the side it leans to. Along the bottom it fades in over [DELETE_SOFT] of its length from its left end.
         */
        const val DELETE_BOOST = 1.5f
        const val DELETE_SOFT = .45f
        /** The thicker right edge is this much thicker, and this much stronger. */
        const val THICKER = 1.5f
        const val THICKER_LEVEL = 1.25f
        /** A held delete's glow under the finger is this much of the bar wide. */
        const val FINGER_SPAN = .6f
        /** A triple press's picked word, with no box round it, and a hold's word about to go, are this strong. */
        const val PICKED = .75f
        const val PULSE = .25f
        const val PULSE_HEIGHT = 1f
        const val SIDE = 10f
        const val LETTER_PITCH = 11f
        const val LETTER_DOT = 3.5f
        /** A letter next to the caret is this strong; far off it is as strong as select's words, and it fades out only at the ends. */
        const val LETTER_NEAR = .75f
        const val LETTER_ENDS = 4f
        /** Steering's pill: as tall and as round as select's, and narrow, with its dark caret inside. */
        const val STEER_PILL = 38f
        /** Its caret is a text cursor: thin, and as tall as the strip's words, which are 17 sp. */
        const val STEER_CARET = 2f
        const val STEER_CARET_HEIGHT = 17f
        /** The gap between the pill and the nearest letter on each side. */
        const val PILL_GAP = 6.5f
        /** While steering the underglow lies across the strip this strong, and the side it leans to grows from this far out. */
        const val UNDERGLOW = .4f
        /**
         * At a full lean the half it leans to brightens, from nothing in the middle to this much stronger again at its edge, so
         * a quarter of the way in from each side it is about 2.5 times the other. The far half stays as it is.
         */
        const val SIDE_BOOST = 8f
        /** The glow's strength rises as it is up to this, then eases towards full, so a bright part has no hard top. */
        const val CAP_FROM = .8f
        /**
         * The glow is drawn in upright slices this wide, each its own strength, too thin to see as steps. A glow of [WIDE_FROM]
         * of the strip or less is a hump; at the whole strip it is flat with soft ends [WIDE_SOFT] of its half-width long.
         */
        const val GLOW_SLICE = 1f
        const val WIDE_FROM = .71f
        const val WIDE_SOFT = .25f
        const val CARET_WIDTH = 2.5f
        const val CARET_HEIGHT = 24f
        const val CARET_GAP = 4f
        const val CARET_END = 8f
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
    }
}
