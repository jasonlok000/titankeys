package com.atlas.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Kinetic type for the demo screen: the text that the script types, big, on ink, with no labels. Typed letters pop, swiped words
 * slide in and settle, a fix flips over in orange, a deleted word breaks apart, a selection lifts in orange and its replacement
 * grows out of it, the emoji bounces in, and the name lands last, letter by letter, with a jolt and a line under it.
 * It mirrors [TrailerScript]'s text through its edits, and draws every frame while anything moves.
 */
internal class TrailerView(context: Context) : View(context) {
    private class Glyph(var text: String, val style: TrailerScript.Style, val born: Long, val delay: Long) {
        var x = Float.NaN
        var y = Float.NaN
        var targetX = 0f
        var targetY = 0f
        var width = 0f
        var lift = 0f
        var selected = false
    }

    private class Debris(val text: String, var x: Float, var y: Float, val vx: Float, val vy: Float, val spin: Float,
                        val born: Long, val removal: TrailerScript.Removal, val colour: Int)

    private val glyphs = ArrayList<Glyph>()
    private val debris = ArrayList<Debris>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, 800, false) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val random = Random(SystemClock.uptimeMillis())
    private var scroll = Float.NaN
    private var targetScroll = 0f
    private var lastFrame = 0L
    private var brandLanded = -1L
    private var cursor = 0

    fun reset() {
        glyphs.clear()
        debris.clear()
        scroll = Float.NaN
        brandLanded = -1
        cursor = 0
        invalidate()
    }

    /** Plays one change of the script, and where the cursor is after it. */
    fun apply(change: TrailerScript.Change, cursor: Int) {
        val now = SystemClock.uptimeMillis()
        for (edit in change.edits) {
            for (index in edit.start until edit.end) {
                val gone = glyphs.removeAt(edit.start)
                if (gone.text.isNotBlank() && !gone.x.isNaN()) debris += breakUp(gone, edit.removal, now)
            }
            val word = edit.insert.count { it != " " }
            var letter = 0
            val added = edit.insert.map { text ->
                val delay = when (edit.style) {
                    TrailerScript.Style.Swiped -> letter * SWIPE_STAGGER
                    TrailerScript.Style.Brand -> letter * BRAND_STAGGER
                    TrailerScript.Style.Fixed -> letter * FIX_STAGGER
                    else -> 0L
                }
                if (text != " ") letter++
                Glyph(text, edit.style, now, delay)
            }
            glyphs.addAll(edit.start, added)
            if (edit.style == TrailerScript.Style.Brand && word > 0) brandLanded = now + (word - 1) * BRAND_STAGGER + BRAND_DROP_MILLIS
        }
        val chosen = change.selection
        for ((index, glyph) in glyphs.withIndex()) glyph.selected = chosen != null && index in chosen
        this.cursor = cursor
        layoutText()
        invalidate()
    }

    private fun breakUp(glyph: Glyph, removal: TrailerScript.Removal, now: Long) = Debris(
        glyph.text, glyph.x, glyph.y,
        vx = if (removal == TrailerScript.Removal.Shatter) (random.nextFloat() - .5f) * 1400f else 0f,
        vy = if (removal == TrailerScript.Removal.Shatter) -400f - random.nextFloat() * 700f else -90f,
        spin = (random.nextFloat() - .5f) * 900f, born = now, removal = removal, colour = if (glyph.selected) ORANGE else CREAM,
    )

    /** Where each glyph goes: words wrap, lines are centred, and the last line sits at the anchor, so older lines move up. */
    private fun layoutText() {
        if (width == 0) return
        paint.textSize = TEXT_SIZE
        for (glyph in glyphs) glyph.width = paint.measureText(glyph.text)
        val room = width - 2 * MARGIN
        val lines = ArrayList<IntRange>()
        var lineStart = 0
        var lineWidth = 0f
        var index = 0
        while (index < glyphs.size) {
            // A word and the spaces after it go on a line together.
            var end = index
            while (end < glyphs.size && glyphs[end].text != " ") end++
            val wordWidth = (index until end).sumOf { glyphs[it].width.toDouble() }.toFloat()
            if (lineWidth + wordWidth > room && index > lineStart) {
                lines += lineStart until index
                lineStart = index
                lineWidth = 0f
            }
            while (end < glyphs.size && glyphs[end].text == " ") end++
            lineWidth += (index until end).sumOf { glyphs[it].width.toDouble() }.toFloat()
            index = end
        }
        if (lineStart < glyphs.size || lines.isEmpty()) lines += lineStart until glyphs.size
        val lineHeight = TEXT_SIZE * LINE_SPACING
        for ((number, line) in lines.withIndex()) {
            var visible = line.last
            while (visible >= line.first && glyphs[visible].text == " ") visible--
            val used = (line.first..visible).sumOf { glyphs[it].width.toDouble() }.toFloat()
            var x = (width - used) / 2
            val y = number * lineHeight
            for (i in line) {
                val glyph = glyphs[i]
                glyph.targetX = x
                glyph.targetY = y
                if (glyph.x.isNaN()) {
                    glyph.x = x
                    glyph.y = y
                }
                x += glyph.width
            }
        }
        targetScroll = height * ANCHOR - (lines.size - 1) * lineHeight
        if (scroll.isNaN()) scroll = targetScroll
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutText()

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(INK)
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16f else (now - lastFrame).coerceIn(1, 50).toFloat()
        lastFrame = now
        var moving = false
        val follow = 1 - exp(-dt / FOLLOW_MILLIS)
        if (!scroll.isNaN() && abs(targetScroll - scroll) > .3f) {
            scroll += (targetScroll - scroll) * follow
            moving = true
        }
        // The name lands with a jolt.
        val shake = if (brandLanded in 1..now && now - brandLanded < SHAKE_MILLIS)
            SHAKE * (1 - (now - brandLanded).toFloat() / SHAKE_MILLIS) * sin((now - brandLanded) / 18f) else 0f
        if (shake != 0f) moving = true
        val brand = brandLanded > 0
        canvas.save()
        canvas.translate(shake, if (scroll.isNaN()) 0f else scroll)
        paint.textSize = TEXT_SIZE
        moving = drawSelection(canvas, follow) || moving
        for ((index, glyph) in glyphs.withIndex()) {
            if (abs(glyph.targetX - glyph.x) > .3f || abs(glyph.targetY - glyph.y) > .3f) {
                glyph.x += (glyph.targetX - glyph.x) * follow
                glyph.y += (glyph.targetY - glyph.y) * follow
                moving = true
            }
            if (glyph.text == " ") continue
            moving = drawGlyph(canvas, glyph, now, brand) || moving
            if (index == cursor - 1) drawCaret(canvas, glyph.x + glyph.width, glyph.y, now)
        }
        if (cursor == 0) drawCaret(canvas, width / 2f, 0f, now)
        moving = drawDebris(canvas, now, dt) || moving
        moving = drawUnderline(canvas, now) || moving
        canvas.restore()
        // The caret pulses, so the screen keeps drawing at the display's rate even when nothing else moves.
        if (moving || isShown) postInvalidateOnAnimation()
    }

    /** One letter as it enters, then as it rests. Returns true while it still moves. */
    private fun drawGlyph(canvas: Canvas, glyph: Glyph, now: Long, brand: Boolean): Boolean {
        val t = (now - glyph.born - glyph.delay).toFloat()
        if (t < 0) return true
        var scaleX = 1f
        var scaleY = 1f
        var dx = 0f
        var dy = 0f
        var rotation = 0f
        var alpha = 1f
        var colour = CREAM
        var moving = false
        when (glyph.style) {
            TrailerScript.Style.Typed -> {
                val p = min(1f, t / TYPE_MILLIS)
                val s = 1.5f - .5f * backOut(p)
                scaleX = s
                scaleY = s
                dy = -26f * (1 - easeOut(p))
                alpha = min(1f, t / 110f)
                moving = p < 1
            }
            TrailerScript.Style.Swiped -> {
                val p = min(1f, t / SWIPE_MILLIS)
                val s = .6f + .4f * backOut(p)
                scaleX = s
                scaleY = s
                dx = 90f * (1 - easeOut(p))
                alpha = min(1f, t / 140f)
                moving = p < 1
            }
            TrailerScript.Style.Fixed -> {
                val p = min(1f, t / FIX_MILLIS)
                scaleY = backOut(p)
                colour = blend(ORANGE, CREAM, min(1f, t / FIX_COLOUR_MILLIS))
                moving = t < FIX_COLOUR_MILLIS
            }
            TrailerScript.Style.Emoji -> {
                val p = min(1f, t / EMOJI_MILLIS)
                val s = elastic(p)
                scaleX = s
                scaleY = s
                rotation = 18f * sin(t / 55f) * (1 - p)
                dy = -60f * sin(PI.toFloat() * p) * (1 - p)
                moving = p < 1
            }
            TrailerScript.Style.Brand -> {
                val p = min(1f, t / BRAND_DROP_MILLIS)
                dy = -320f * (1 - bounceOut(p))
                val s = 1.8f - .8f * easeOut(p)
                scaleX = s
                scaleY = s
                alpha = min(1f, t / 90f)
                colour = ORANGE
                moving = p < 1
            }
        }
        // Once the name has landed, the rest steps back.
        if (brand && glyph.style != TrailerScript.Style.Brand) {
            alpha *= 1 - .65f * min(1f, (now - brandLanded).coerceAtLeast(0) / DIM_MILLIS)
            moving = moving || now - brandLanded < DIM_MILLIS
        }
        val lift = glyph.lift
        if (lift > 0) colour = blend(colour, INK, lift)
        // Lines that scroll off the top fade away.
        val screenY = glyph.y + (if (scroll.isNaN()) 0f else scroll)
        alpha *= ((screenY - TOP_FADE_END) / (TOP_FADE_START - TOP_FADE_END)).coerceIn(0f, 1f)
        if (alpha <= 0f) return moving
        paint.color = colour
        paint.alpha = (alpha * 255).toInt()
        canvas.save()
        val cx = glyph.x + glyph.width / 2
        val cy = glyph.y - TEXT_SIZE * .35f
        canvas.translate(dx, dy - LIFT * lift)
        canvas.rotate(rotation, cx, cy)
        canvas.scale(scaleX, scaleY, cx, cy)
        canvas.drawText(glyph.text, glyph.x, glyph.y, paint)
        canvas.restore()
        return moving
    }

    /** The selection: its letters lift, and an orange block grows behind them, a block for each line. */
    private fun drawSelection(canvas: Canvas, follow: Float): Boolean {
        var moving = false
        for (glyph in glyphs) {
            val target = if (glyph.selected) 1f else 0f
            if (abs(target - glyph.lift) > .01f) {
                glyph.lift += (target - glyph.lift) * follow
                moving = true
            } else glyph.lift = target
        }
        var start = 0
        while (start < glyphs.size) {
            if (glyphs[start].lift <= .01f) {
                start++
                continue
            }
            var end = start
            while (end + 1 < glyphs.size && glyphs[end + 1].lift > .01f && glyphs[end + 1].targetY == glyphs[start].targetY) end++
            var right = end
            while (right > start && glyphs[right].text == " ") right--
            val from = glyphs[start]
            val to = glyphs[right]
            val amount = (start..end).maxOf { glyphs[it].lift }
            box.set(from.x - PAD, from.y - TEXT_SIZE * .82f - LIFT * amount, to.x + to.width + PAD, from.y + TEXT_SIZE * .24f - LIFT * amount)
            fill.color = ORANGE
            fill.alpha = (amount * 255).toInt()
            canvas.drawRoundRect(box, RADIUS, RADIUS, fill)
            start = end + 1
        }
        return moving
    }

    private fun drawDebris(canvas: Canvas, now: Long, dt: Float): Boolean {
        val iterator = debris.iterator()
        while (iterator.hasNext()) {
            val piece = iterator.next()
            val t = (now - piece.born).toFloat()
            val life = if (piece.removal == TrailerScript.Removal.Shatter) SHATTER_MILLIS else MORPH_MILLIS
            if (t >= life || piece.removal == TrailerScript.Removal.Quiet) {
                iterator.remove()
                continue
            }
            val seconds = t / 1000f
            val x = piece.x + piece.vx * seconds
            val y = piece.y + piece.vy * seconds + (if (piece.removal == TrailerScript.Removal.Shatter) GRAVITY * seconds * seconds / 2 else 0f)
            val p = t / life
            val s = if (piece.removal == TrailerScript.Removal.Morph) 1 - .6f * p else 1f
            paint.color = piece.colour
            paint.alpha = ((1 - p) * 255).toInt()
            canvas.save()
            canvas.rotate(piece.spin * seconds, x, y)
            canvas.scale(s, s, x, y)
            canvas.drawText(piece.text, x, y, paint)
            canvas.restore()
        }
        return debris.isNotEmpty()
    }

    /** After the name lands, a line sweeps under it. */
    private fun drawUnderline(canvas: Canvas, now: Long): Boolean {
        if (brandLanded < 0 || now < brandLanded) return brandLanded > 0
        val letters = glyphs.filter { it.style == TrailerScript.Style.Brand && it.text != " " }
        if (letters.isEmpty()) return false
        val p = min(1f, (now - brandLanded) / UNDERLINE_MILLIS)
        val left = letters.first().x
        val right = letters.last().x + letters.last().width
        val y = letters.first().y + TEXT_SIZE * .2f
        fill.color = ORANGE
        fill.alpha = 255
        box.set(left, y, left + (right - left) * easeOut(p), y + UNDERLINE)
        canvas.drawRoundRect(box, UNDERLINE / 2, UNDERLINE / 2, fill)
        return p < 1
    }

    private fun drawCaret(canvas: Canvas, x: Float, y: Float, now: Long) {
        fill.color = ORANGE
        fill.alpha = (150 + 105 * cos(now / 260.0)).toInt().coerceIn(0, 255)
        box.set(x + 6, y - TEXT_SIZE * .78f, x + 6 + CARET, y + TEXT_SIZE * .12f)
        canvas.drawRect(box, fill)
    }

    private companion object {
        const val INK = 0xFF160A04.toInt()
        const val CREAM = 0xFFFFF1E6.toInt()
        const val ORANGE = 0xFFEF804E.toInt()
        const val TEXT_SIZE = 92f
        const val LINE_SPACING = 1.22f
        const val MARGIN = 60f
        /** Where the last line sits, as a part of the height, so the bottom stays clear for the bar in the edit. */
        const val ANCHOR = .58f
        const val TOP_FADE_START = 190f
        const val TOP_FADE_END = 40f
        const val FOLLOW_MILLIS = 90f
        const val TYPE_MILLIS = 260f
        const val SWIPE_MILLIS = 340f
        const val SWIPE_STAGGER = 26L
        const val FIX_MILLIS = 320f
        const val FIX_STAGGER = 30L
        const val FIX_COLOUR_MILLIS = 900f
        const val EMOJI_MILLIS = 700f
        const val BRAND_STAGGER = 70L
        const val BRAND_DROP_MILLIS = 560L
        const val DIM_MILLIS = 700f
        const val SHAKE = 10f
        const val SHAKE_MILLIS = 260L
        const val UNDERLINE_MILLIS = 420f
        const val UNDERLINE = 10f
        const val SHATTER_MILLIS = 950f
        const val MORPH_MILLIS = 300f
        const val GRAVITY = 2600f
        const val LIFT = 16f
        const val PAD = 10f
        const val RADIUS = 14f
        const val CARET = 7f

        fun easeOut(p: Float) = 1 - (1 - p) * (1 - p) * (1 - p)
        fun backOut(p: Float): Float {
            val c = 1.7f
            val q = p - 1
            return 1 + (c + 1) * q * q * q + c * q * q
        }
        fun elastic(p: Float) = if (p <= 0f) 0f else if (p >= 1f) 1f
            else (Math.pow(2.0, -10.0 * p) * sin((p * 10 - .75) * (2 * PI / 3)) + 1).toFloat()
        fun bounceOut(p: Float): Float {
            val n = 7.5625f
            val d = 2.75f
            return when {
                p < 1 / d -> n * p * p
                p < 2 / d -> { val q = p - 1.5f / d; n * q * q + .75f }
                p < 2.5 / d -> { val q = p - 2.25f / d; n * q * q + .9375f }
                else -> { val q = p - 2.625f / d; n * q * q + .984375f }
            }
        }
        fun blend(from: Int, to: Int, amount: Float): Int {
            val a = amount.coerceIn(0f, 1f)
            fun channel(shift: Int) = ((from shr shift and 0xFF) + ((to shr shift and 0xFF) - (from shr shift and 0xFF)) * a).toInt() shl shift
            return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
        }
    }
}
