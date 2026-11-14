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
    private var keyGeometry: KeyGeometry? = null
    /** The bar's line, in px under its top, where the words have the middle of their small letters: see [StripFit.LINE_DP]. */
    private var line = 0f
    /**
     * The lowest anything may reach, in px under the bar's top: the words' tails. The bar's sides keep what reaches that low clear
     * of the screen's rounded corners, and it's over the screen's edge by at least [StripFit.EDGE_DP].
     */
    private var floor = 0f
    /** The bar's words' size, in px, at this Bar text size. */
    private var text = 0f

    /**
     * Draws the display over the bar [height] px tall, between [left] and [right]. Everything it draws is centred on the bar's
     * [line], as the words are (owner, 2026-11-13: "the delete cursor looks visibly too far up"), and what stands for text is
     * sized from the words' size [text], so it lines up with their letters at every Bar text size. Nothing reaches under [floor].
     */
    fun draw(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, height: Int, look: Look, line: Float, floor: Float, text: Float) {
        this.line = line
        this.floor = floor
        this.text = text
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
        val mid = line
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
        val faded = alpha < 1f
        if (faded) canvas.saveLayerAlpha(0f, 0f, right + left, h, (alpha.coerceIn(0f, 1f) * 255).toInt())
        stroke.color = look.accent
        // Across, the path sits over the real keys, as Key calibration has them; the strip is as wide as the screen, and the
        // sides are the same way in, so they add up to its width. It never goes past the sides, where the corners cut.
        val keys = keysOn(right + left)
        var x = keys.x(display.pathX[0]).coerceIn(left, right)
        var y = swipeY(display.pathY[0], h)
        for (index in 1 until count) {
            val nextX = keys.x(display.pathX[index]).coerceIn(left, right)
            val nextY = swipeY(display.pathY[index], h)
            stroke.strokeWidth = dp(PATH_WIDTH) * (display.pathWidth[index - 1] + display.pathWidth[index]) / 2
            canvas.drawLine(x, y, nextX, nextY, stroke)
            x = nextX
            y = nextY
        }
        // The finger's dot keeps over the words' tails too: at its thickest it sits a little above the path's end.
        val finger = dp(PATH_FINGER) * display.pathWidth[count - 1].coerceAtLeast(1f)
        dot(canvas, x, min(y, floor - finger), finger, look.accent)
        if (faded) canvas.restore()
    }

    /** The keys on a strip [width] wide, made again only when the width or the calibration changes, so a frame makes no garbage. */
    private fun keysOn(width: Float): KeyGeometry {
        val now = keyGeometry
        if (now != null && now.width == width && now.alignment == KeyAlignment.current) return now
        return KeyGeometry(width).also { keyGeometry = it }
    }

    /**
     * A touchpad height, from the top row down to the bottom of the letter rows, as a height on the strip: the middle row on the
     * bar's line, and the rows as far apart above it as below, close enough that the bottom row's stroke, at its thickest, keeps
     * over the words' tails, [floor] (owner, 2026-11-13: the bottom row ran along the screen's edge). [PATH_ROW] at most.
     */
    private fun swipeY(y: Float, h: Float): Float {
        val stroke = dp(PATH_WIDTH) * TrailThickness.THICKEST / 2
        val row = min(dp(PATH_ROW), (room() - stroke) / 1.5f)
        return line + (y / .75f * 3 - 1.5f).coerceIn(-1.5f, 1.5f) * row
    }

    /**
     * The guide's finger on the word at [x]: a soft dot on the bar's [line], where the word's letters are, with a ring that closes
     * in on it as the finger holds ([hold], 0 to 1). It's a finger, not text: it keeps one size at every Bar text size, [unit] px
     * to a dp, as the bar's height does. Its ring starts as wide as the room under the line lets it, so it never reaches under
     * [floor], the bar's own, over the screen's edge: the middle word is far from the corners.
     */
    fun finger(canvas: Canvas, x: Float, line: Float, floor: Float, alpha: Float, hold: Float, look: Look, unit: Float) {
        if (alpha <= 0f) return
        val radius = FINGER * unit
        val stroke = FINGER_STROKE * unit
        val widest = max(radius, min(radius * FINGER_RING, floor - line - stroke / 2))
        dot(canvas, x, line, radius, GuideMotion.faded(look.accent, FINGER_FILL * alpha))
        this.stroke.shader = null
        this.stroke.color = GuideMotion.faded(look.accent, alpha)
        this.stroke.strokeWidth = stroke
        canvas.drawCircle(x, line, widest + (radius - widest) * hold, this.stroke)
    }

    /** The room from the bar's line down to [floor]: the most half of anything centred on the line may be. */
    private fun room() = floor - line

    /** Half the height of a caret: as tall as the words' letters ([StripFit.caretHalf]), and never under [floor]. */
    private fun caretHalf() = min(StripFit.caretHalf(text), room())

    /** Half the height of a word drawn as a short bar: the words' small letters, their x-height. */
    private fun wordHalf() = text * StripFit.X_HEIGHT / 2

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
     * A delete's glow: the steering underglow, as soft and in the same orange, along the bottom from the right corner, where
     * Backspace is, leftwards as far as [GestureDisplay.glowWidth]: full at the corner, soft where it ends. A single flick's
     * reaches to the finger and follows it. A hold's reaches further and gets stronger with the pace, up to what steering's
     * leaning side reaches, and stops changing at the top-speed line; in the pause zone it fades out. See [GestureDisplay.glowDelete].
     */
    private fun deleteGlow(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, h: Float, look: Look) {
        val strength = display.glowAlpha.coerceIn(0f, 1f)
        if (strength <= 0f) return
        val alpha = strength * softCap(UNDERGLOW * (1f + DELETE_BOOST * display.shownPace))
        val tall = dp(GLOW_HEIGHT)
        val paint = underGlow(h, look, tall)
        val span = (right - left) * display.glowWidth
        if (span < 1f) return
        val from = right - span
        val slice = max(1f, floor(dp(GLOW_SLICE)))
        var x = floor(from)
        while (x < right) {
            val t = ((x + slice / 2 - from) / span).coerceIn(0f, 1f)
            val shape = (t / DELETE_SOFT).coerceAtMost(1f).let { it * it * (3 - 2 * it) }
            paint.alpha = (alpha * shape * 255).toInt()
            if (paint.alpha > 0) canvas.drawRect(x, h - tall, min(x + slice, right), h, paint)
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
     * Steering: the real text as a line of dots through the middle that moves past the caret, and the caret: a thin solid orange
     * line, as every gesture's is, with no box round it (owner, 2026-11-12). It blinks only once steering pauses, see
     * [GestureDisplay.caretShown]. The dots part
     * round it with a small gap, so a letter the cursor passes hops to the other side. Which way it leans shows in the underglow,
     * see [glow].
     *
     * The caret is a text cursor: thin, as tall as the strip's words, and always between the letters that the real cursor is
     * between. A step moves it there at once, as the text cursor moves, and the line then glides it back to the middle.
     */
    private fun cursor(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, mid: Float, look: Look) {
        val alpha = display.reveal
        val line = mid
        val caret = (left + right) / 2
        val pitch = dp(LETTER_PITCH)
        val clear = dp(STEER_CLEAR)
        // How far each side's dots move out, so the nearest letter sits a small gap from the caret.
        val part = clear + dp(PILL_GAP) + dp(LETTER_DOT) - pitch / 2
        val text = display.line
        // Where the real cursor is: ahead of the middle by the steps the line has still to glide.
        var at = caret
        if (text != null) {
            val cursor = display.caretAt + display.stepped
            at = caret + (display.stepped - display.shownStep) * pitch
            // The real text: a dot for each character, a gap for each space, and nothing past either end of the text. The letters
            // part round the cursor, not the middle, so the caret always sits between the two letters it is between.
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
        if (display.caretShown) bar(canvas, at, line, 2 * caretHalf(), GuideMotion.faded(look.accent, alpha), dp(STEER_CARET))
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

    /**
     * A row of words, with the selection over them in its orange box. A word drag shows it growing and shrinking by whole words.
     * A triple press's pick shows the same from the moment it picks, so the pick and the drag after it are one selection
     * (owner, 2026-11-12). The end that moves has its end mark: see [selectionEnd].
     */
    private fun words(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, mid: Float, look: Look) {
        val slot = (right - left) / GestureDisplay.VISIBLE_WORDS
        val centre = (left + right) / 2
        val alpha = display.reveal
        // The selection goes over the words that are not in it, so a word that it reaches goes under it.
        wordRow(canvas, display, left, right, slot, centre, mid, selected = false, GuideMotion.faded(look.ink, WORD * alpha))
        var start = centre + (display.spanStart - display.camera) * slot
        var end = centre + (display.spanEnd - display.camera) * slot
        if (end <= start) return
        // The selection is cut at the bar's sides too, its end mark with it.
        start = max(start - dp(SELECTION_PAD), left)
        end = min(end + dp(SELECTION_PAD), right)
        val box = min(text * SELECTION_HEIGHT / 2, room())
        rounded(canvas, start, mid - box, end, mid + box, radius,
            GuideMotion.faded(look.accent, alpha))
        wordRow(canvas, display, left, right, slot, centre, mid, selected = true, GuideMotion.faded(look.onAccent, SELECTED * alpha))
        // The mark fades from one end into the other as the drag turns, so it never jumps.
        val right = (display.endLead + 1f) / 2f
        selectionEnd(canvas, end, mid, 1, right * alpha, look)
        selectionEnd(canvas, start, mid, -1, (1f - right) * alpha, look)
    }

    /**
     * The mark at the moving end of a selection, just outside its [edge] on [side], 1 for the right and -1 for the left, as
     * [GestureDisplay.selectionEnd] has it: a handle, a chevron pointing the way it grows, or the thin caret.
     */
    private fun selectionEnd(canvas: Canvas, edge: Float, mid: Float, side: Int, alpha: Float, look: Look) {
        if (alpha <= .01f) return
        val colour = GuideMotion.faded(look.accent, alpha)
        when (GestureDisplay.selectionEnd) {
            SelectionEnd.Caret -> bar(canvas, edge + side * dp(CARET_GAP), mid, 2 * caretHalf(), colour, dp(STEER_CARET))
            SelectionEnd.Handle -> {
                val x = edge + side * dp(CARET_GAP)
                val half = min(text * HANDLE_HEIGHT / 2, room())
                val width = dp(HANDLE_WIDTH)
                rounded(canvas, x - width / 2, mid - half, x + width / 2, mid + half, width / 2, colour)
                // The knob on top: the bar sits on the screen's bottom edge, with no room under the line for it.
                dot(canvas, x, mid - half - dp(HANDLE_KNOB) * .6f, dp(HANDLE_KNOB), colour)
            }
            SelectionEnd.Chevron -> {
                val x = edge + side * dp(CHEVRON_GAP)
                val half = min(dp(CHEVRON_HEIGHT) / 2, room())
                val tip = x + side * dp(CHEVRON_DEPTH)
                stroke.shader = null
                stroke.color = colour
                stroke.strokeWidth = dp(CHEVRON_STROKE)
                canvas.drawLine(x, mid - half, tip, mid, stroke)
                canvas.drawLine(tip, mid, x, mid + half, stroke)
            }
        }
    }

    /** The words of a drag, cut at the bar's sides, [left] and [right], so none goes under the screen's rounded corners. */
    private fun wordRow(canvas: Canvas, display: GestureDisplay, left: Float, right: Float, slot: Float, centre: Float, mid: Float,
                        selected: Boolean, colour: Int) {
        val first = floor(display.camera - GestureDisplay.VISIBLE_WORDS / 2).toInt() - 1
        val last = ceil(display.camera + GestureDisplay.VISIBLE_WORDS / 2).toInt() + 1
        for (index in first..last) {
            if ((index > display.spanStart && index < display.spanEnd) != selected) continue
            val x = centre + (index - display.camera) * slot
            val half = slot * display.wordWidth(index) / 2
            word(canvas, max(x - half, left), min(x + half, right), mid, colour)
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
            // The dots' caret is a little taller than the letters, and grows as each dot lands, as far as the room under the line
            // lets it; past that it grows wider instead, so a pulse shows at every Bar text size.
            val rest = StripFit.caretHalf(text) * (CARET_HEIGHT + (1f - CARET_HEIGHT) * thin)
            val want = rest * (1f + CARET_PULSE * pulse)
            val half = min(want, room())
            val wider = (want / half) / (rest / min(rest, room()))
            bar(canvas, caret, mid, 2 * half, GuideMotion.faded(look.accent, alpha),
                (dp(CARET_WIDTH) + (dp(STEER_CARET) - dp(CARET_WIDTH)) * thin) * wider)
            return
        }
        lines(display, display.shownDeleted, left, stop, slot) { start, end, going ->
            word(canvas, start, end, mid, GuideMotion.faded(look.ink, (if (going) PICKED else WORD) * alpha))
        }
        // A thin solid caret, as a triple press's pick has, that blinks in the pause zone.
        if (display.caretShown) bar(canvas, caret, mid, 2 * caretHalf(), GuideMotion.faded(look.accent, alpha), dp(STEER_CARET))
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
            val half = wordHalf() + (radius - wordHalf()) * m
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
        val half = wordHalf() * size
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
        /** The path's three rows of letters lie at most this far apart on the strip: closer where the room under the line is less. */
        const val PATH_ROW = 14f
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
        const val STEER_CLEAR = 3f
        /** Its caret is a text cursor: thin, and as tall as the bar's letters ([StripFit.caretHalf]). */
        const val STEER_CARET = 2f
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
        /** The dots' caret is this much taller than the letters, as far as the room under the line lets it. */
        const val CARET_HEIGHT = 24f / 17f
        const val CARET_GAP = 4f
        const val CARET_END = 8f
        const val WORD = .28f
        const val SELECTED = .85f
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
        /** A selection is this tall, as a part of the words' size, as far as the room under the line lets it. */
        const val SELECTION_HEIGHT = 26f / 20f
        const val SELECTION_PAD = 5f
        /**
         * A selection handle: taller than the selection where there's room, as a part of the words' size, a little wider than the
         * caret, with its knob on top.
         */
        const val HANDLE_HEIGHT = 34f / 20f
        const val HANDLE_WIDTH = 2.5f
        const val HANDLE_KNOB = 4.5f
        /** A selection's chevron: how tall, how far its point reaches out, how thick, and its gap from the selection. */
        const val CHEVRON_HEIGHT = 14f
        const val CHEVRON_DEPTH = 6f
        const val CHEVRON_STROKE = 2.5f
        const val CHEVRON_GAP = 5f
        /** The guide's finger: its dot, its ring's thickness, how much wider than the dot the ring starts, and the dot's strength. */
        const val FINGER = 9f
        const val FINGER_STROKE = 2f
        const val FINGER_RING = 2.6f
        const val FINGER_FILL = .35f
    }
}
