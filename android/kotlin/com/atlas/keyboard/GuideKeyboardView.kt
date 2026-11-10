package com.atlas.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The guide's brand colours. They are the values in lib/brand.ts and the app icon. */
internal object GuideColours {
    const val ORANGE = 0xFFEF804E.toInt()
    const val INK = 0xFF160A04.toInt()
    const val CREAM = 0xFFFFF1E6.toInt()
}

/** Time for the guide's motion. It follows Android's animator duration setting, and with animations off every motion ends at once. */
internal object GuideMotion {
    val enabled get() = ValueAnimator.areAnimatorsEnabled() && ValueAnimator.getDurationScale() > 0f

    fun now() = SystemClock.uptimeMillis()

    /** How far a motion that began at [start] has run, from 0 to 1. */
    fun progress(start: Long, millis: Long): Float {
        if (!enabled) return 1f
        return ((now() - start) / (millis * ValueAnimator.getDurationScale())).coerceIn(0f, 1f)
    }

    fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

    fun blend(from: Int, to: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val a = from shr shift and 0xFF
            val b = to shr shift and 0xFF
            return (a + (b - a) * amount + .5f).toInt().coerceIn(0, 255) shl shift
        }
        return channel(24) or channel(16) or channel(8) or channel(0)
    }

    fun faded(colour: Int, alpha: Float) = colour and 0x00FFFFFF or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
}

/**
 * A drawing of the Titan 2 Elite's keyboard, from [Layout]. The keys light up and the trail follows your real finger, and a
 * ghost finger shows each gesture before you try it. Everything that sets how it looks is in the numbers at the end of this file.
 */
internal class GuideKeyboardView(context: Context) : View(context) {
    private class Key(val name: String, val letter: Boolean) {
        val box = RectF()
        /** How lit the key is, from 0 to 1. It fades by itself. */
        var heat = 0f
        /** What the key shows for a moment after its letter became a symbol. */
        var symbol: String? = null
        var flipStart = -1L
    }

    private val keys = Layout.rows.flatMap { row -> row.map { Key(it, Layout.isLetter(it)) } }
    private val byName = keys.associateBy { it.name }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, 800, false)
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val altPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, 600, false) }
    /** The symbol that Alt types on each letter key, read from the phone's own keyboard, as it is printed on the key. */
    private val altSymbols = keys.filter { it.letter }.mapNotNull { key ->
        Keys.letter(key.name, android.view.KeyEvent.META_ALT_ON)?.let { key.name to it }
    }.toMap()
    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val areaPath = Path()
    private val path = Path()

    var tone = GuideTone.Ink
        set(value) {
            field = value
            invalidate()
        }

    /** The gesture that the ghost finger shows now. */
    var cue: GuideCue = GuideCue.Free
        private set
    private var ghost: List<FloatArray> = emptyList()
    private var cueStart = 0L

    /** The finger's path in touchpad space, from 0 to 1 each way, as pairs of x and y. */
    private val trail = ArrayList<FloatArray>()
    private var liftedAt = -1L
    private var lastFrame = 0L
    private var appearStart = -1L
    private var waveStart = -1L

    fun show(next: GuideCue) {
        cue = next
        cueStart = GuideMotion.now()
        ghost = ghostFor(next)
        invalidate()
    }

    /** Lets the keys arrive one after the other. */
    fun appear() {
        appearStart = GuideMotion.now()
        invalidate()
    }

    /** A wave of light across the keys, when something works. */
    fun celebrate() {
        waveStart = GuideMotion.now()
        invalidate()
    }

    /** There are two Shift keys, so a name can light more than one key. */
    fun light(name: String) {
        for (key in keys) if (key.name == name) key.heat = 1f
        invalidate()
    }

    /** The key that [name] names turns over and shows its [symbol]. */
    fun flip(name: String, symbol: String) {
        val key = byName[name] ?: return
        key.symbol = symbol
        key.flipStart = GuideMotion.now()
        key.heat = 1f
        invalidate()
    }

    /** Adds to the live trail. [reset] starts a new path. */
    fun trace(points: List<FloatArray>, reset: Boolean, lifted: Boolean) {
        if (reset) trail.clear()
        trail.addAll(points)
        for (point in points) byName[Layout.keyAt(point[0], point[1])]?.heat = 1f
        liftedAt = if (lifted) GuideMotion.now() else -1
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val keyWidth = w / 10f
        val rowHeight = h / 4f
        var index = 0
        for (row in 0..2) for (column in 0..9)
            keys[index++].box.set(column * keyWidth, row * rowHeight, (column + 1) * keyWidth, (row + 1) * rowHeight)
        var left = 0f
        for (width in Layout.bottomWidths) {
            keys[index++].box.set(left, 3 * rowHeight, left + width * keyWidth, 4 * rowHeight)
            left += width * keyWidth
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = GuideMotion.now()
        val elapsed = if (lastFrame == 0L) 0L else now - lastFrame
        lastFrame = now
        val frameCount = if (GuideMotion.enabled) elapsed / FRAME_MILLIS else 99f
        var moving = cue != GuideCue.Free && GuideMotion.enabled && isShown
        for (key in keys) moving = drawKey(canvas, key, now, frameCount) || moving
        drawTouchArea(canvas)
        drawGhost(canvas, now)
        moving = drawTrail(canvas, now) || moving
        if (moving) postInvalidateOnAnimation() else lastFrame = 0
    }

    private fun drawKey(canvas: Canvas, key: Key, now: Long, frames: Float): Boolean {
        var moving = false
        val onOrange = tone == GuideTone.Orange
        val idleFill = GuideMotion.faded(if (onOrange) GuideColours.INK else GuideColours.CREAM, if (onOrange) KEY_FILL_ORANGE else KEY_FILL_INK)
        val idleLabel = if (onOrange) GuideColours.INK else GuideMotion.faded(GuideColours.CREAM, KEY_LABEL_INK)
        val litFill = if (onOrange) GuideColours.INK else GuideColours.ORANGE
        val litLabel = if (onOrange) GuideColours.ORANGE else GuideColours.INK

        var heat = key.heat
        if (key.heat > 0f) {
            key.heat = if (GuideMotion.enabled) max(0f, key.heat - HEAT_FADE_PER_FRAME * frames) else 0f
            moving = true
        }
        val spot = cue as? GuideCue.Spot
        if (spot != null && key.name in spot.keys && GuideMotion.enabled) {
            val at = spot.keys.indexOf(key.name)
            heat = max(heat, .5f + .5f * sin(((now - cueStart + at * 240) / SPOT_PULSE_MILLIS) * 2 * PI).toFloat())
        }
        // The wave moves over the keys from the left and from the top.
        var bump = 0f
        if (waveStart >= 0) {
            val delay = (key.box.centerX() + key.box.centerY()) / max(1f, (width + height).toFloat()) * WAVE_SPREAD_MILLIS
            val t = GuideMotion.progress(waveStart + delay.toLong(), WAVE_MILLIS)
            if (GuideMotion.progress(waveStart, (WAVE_SPREAD_MILLIS + WAVE_MILLIS).toLong()) < 1f) {
                bump = sin(t * PI).toFloat()
                heat = max(heat, bump)
                moving = true
            }
        }
        var appear = 1f
        if (appearStart >= 0) {
            val delay = (key.box.centerX() + key.box.centerY()) / max(1f, (width + height).toFloat()) * APPEAR_SPREAD_MILLIS
            val t = GuideMotion.progress(appearStart + delay.toLong(), APPEAR_MILLIS)
            appear = GuideMotion.easeOut(t)
            if (t < 1f) moving = true
        }

        val inset = dp(KEY_GAP)
        val pressed = 1f + bump * WAVE_SCALE - heat * PRESS_DEPTH
        val centreX = key.box.centerX()
        val centreY = key.box.centerY()
        canvas.save()
        canvas.scale(pressed * appear, pressed * appear, centreX, centreY)
        var label = key.name
        var flip = 1f
        if (key.flipStart >= 0) {
            val t = GuideMotion.progress(key.flipStart, FLIP_MILLIS)
            // The key turns over to show the symbol on its back, stays there, and turns back.
            flip = when {
                t < FLIP_TURN -> 1f - t / FLIP_TURN
                t < 2 * FLIP_TURN -> (t - FLIP_TURN) / FLIP_TURN
                t < 1f - FLIP_TURN -> 1f
                else -> (1f - t) / FLIP_TURN
            }
            canvas.scale(1f, max(.02f, flip), centreX, centreY)
            if (t >= FLIP_TURN && t < 1f - FLIP_TURN / 2) label = key.symbol ?: key.name
            if (t >= 1f) {
                key.flipStart = -1
                key.symbol = null
            } else moving = true
        }
        val radius = min(key.box.width(), key.box.height()) * KEY_ROUNDNESS
        boxPaint.color = GuideMotion.blend(idleFill, litFill, heat.coerceIn(0f, 1f))
        canvas.drawRoundRect(key.box.left + inset, key.box.top + inset, key.box.right - inset, key.box.bottom - inset, radius, radius, boxPaint)
        val labelColour = GuideMotion.blend(idleLabel, litLabel, heat.coerceIn(0f, 1f))
        drawLabel(canvas, key, label, labelColour)
        // Like the real keys, each letter has its Alt symbol printed small in its top left corner.
        altSymbols[key.name]?.takeIf { label == key.name }?.let { symbol ->
            altPaint.color = GuideMotion.faded(labelColour, ALT_ALPHA)
            altPaint.textSize = min(key.box.width(), key.box.height()) * ALT_SIZE
            canvas.drawText(symbol, key.box.left + inset + key.box.width() * ALT_INSET, key.box.top + inset - altPaint.ascent() + key.box.height() * ALT_INSET / 2, altPaint)
        }
        canvas.restore()
        return moving
    }

    /**
     * The part of the keys that the touchpad feels stays bright, and the rest is dimmed: its sensor stops at the middle of the outer
     * columns, of the top row and of the bottom row, so a finger there is not read well. A line would cross the letters, so the
     * edge of the dimmed part is the box.
     */
    private fun drawTouchArea(canvas: Canvas) {
        areaPaint.color = GuideMotion.faded(if (tone == GuideTone.Orange) GuideColours.ORANGE else GuideColours.INK, AREA_SHADE)
        areaPath.rewind()
        areaPath.fillType = Path.FillType.EVEN_ODD
        areaPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        areaPath.addRoundRect(width * AREA_LEFT, height * AREA_TOP, width * AREA_RIGHT, height * AREA_BOTTOM,
            dp(AREA_RADIUS), dp(AREA_RADIUS), Path.Direction.CW)
        canvas.drawPath(areaPath, areaPaint)
    }

    private fun drawLabel(canvas: Canvas, key: Key, label: String, colour: Int) {
        val box = key.box
        val size = min(box.width(), box.height()) * (if (key.letter || label.length == 1) LABEL_LETTER else LABEL_SMALL)
        labelPaint.color = colour
        labelPaint.textSize = size
        iconPaint.color = colour
        iconPaint.strokeWidth = max(1.5f, size * .14f)
        val x = box.centerX()
        val y = box.centerY()
        val r = size * .42f
        when (label) {
            Layout.SPACE -> {
                val halfSpan = box.width() * .22f
                canvas.drawLine(x - halfSpan, y + r * .35f, x + halfSpan, y + r * .35f, iconPaint)
                canvas.drawLine(x - halfSpan, y + r * .35f, x - halfSpan, y - r * .1f, iconPaint)
                canvas.drawLine(x + halfSpan, y + r * .35f, x + halfSpan, y - r * .1f, iconPaint)
            }
            Layout.BACKSPACE -> {
                path.rewind()
                path.moveTo(x - r * 1.2f, y)
                path.lineTo(x - r * .4f, y - r)
                path.lineTo(x + r * 1.2f, y - r)
                path.lineTo(x + r * 1.2f, y + r)
                path.lineTo(x - r * .4f, y + r)
                path.close()
                canvas.drawPath(path, iconPaint)
                canvas.drawLine(x, y - r * .35f, x + r * .6f, y + r * .35f, iconPaint)
                canvas.drawLine(x + r * .6f, y - r * .35f, x, y + r * .35f, iconPaint)
            }
            Layout.ENTER -> {
                path.rewind()
                path.moveTo(x + r, y - r)
                path.lineTo(x + r, y + r * .3f)
                path.lineTo(x - r, y + r * .3f)
                path.moveTo(x - r * .4f, y - r * .35f)
                path.lineTo(x - r, y + r * .3f)
                path.lineTo(x - r * .4f, y + r * .95f)
                canvas.drawPath(path, iconPaint)
            }
            "⇧" -> {
                path.rewind()
                path.moveTo(x, y - r)
                path.lineTo(x + r, y)
                path.lineTo(x + r * .45f, y)
                path.lineTo(x + r * .45f, y + r)
                path.lineTo(x - r * .45f, y + r)
                path.lineTo(x - r * .45f, y)
                path.lineTo(x - r, y)
                path.close()
                canvas.drawPath(path, iconPaint)
            }
            "○" -> canvas.drawCircle(x, y, r * .8f, iconPaint)
            "◁" -> {
                path.rewind()
                path.moveTo(x + r * .7f, y - r * .8f)
                path.lineTo(x - r * .7f, y)
                path.lineTo(x + r * .7f, y + r * .8f)
                path.close()
                canvas.drawPath(path, iconPaint)
            }
            // The key next to Space shows one square, the recent apps mark.
            "▦" -> canvas.drawRoundRect(x - r * .75f, y - r * .75f, x + r * .75f, y + r * .75f, r * .15f, r * .15f, iconPaint)
            else -> canvas.drawText(label, x, y - (labelPaint.ascent() + labelPaint.descent()) / 2, labelPaint)
        }
    }

    private fun drawTrail(canvas: Canvas, now: Long): Boolean {
        if (trail.size < 2) {
            if (liftedAt >= 0) {
                trail.clear()
                liftedAt = -1
            }
            return false
        }
        var alpha = 1f
        var moving = false
        if (liftedAt >= 0) {
            val t = GuideMotion.progress(liftedAt, TRAIL_FADE_MILLIS)
            alpha = 1f - t
            if (t >= 1f) {
                trail.clear()
                liftedAt = -1
                return false
            }
            moving = true
        }
        path.rewind()
        var px = trail[0][0] * width
        var py = trail[0][1] * height
        path.moveTo(px, py)
        for (i in 1 until trail.size) {
            val x = trail[i][0] * width
            val y = trail[i][1] * height
            path.quadTo(px, py, (px + x) / 2, (py + y) / 2)
            px = x
            py = y
        }
        path.lineTo(px, py)
        val under = if (tone == GuideTone.Orange) GuideMotion.faded(GuideColours.INK, TRAIL_SHADE * alpha) else GuideMotion.faded(GuideColours.ORANGE, TRAIL_SHADE * alpha)
        trailPaint.color = under
        trailPaint.strokeWidth = dp(TRAIL_WIDTH) * TRAIL_UNDER
        canvas.drawPath(path, trailPaint)
        trailPaint.color = GuideMotion.faded(GuideColours.CREAM, alpha)
        trailPaint.strokeWidth = dp(TRAIL_WIDTH)
        canvas.drawPath(path, trailPaint)
        dotPaint.color = trailPaint.color
        canvas.drawCircle(px, py, dp(TRAIL_WIDTH) * .9f, dotPaint)
        return moving
    }

    /** The ghost finger: thin, dashed by the time it lasts, then a pause, then again. */
    private fun drawGhost(canvas: Canvas, now: Long) {
        if (cue == GuideCue.Free || cue is GuideCue.Spot) return
        val colour = if (tone == GuideTone.Orange) GuideColours.INK else GuideColours.ORANGE
        val cycle = cycleMillis()
        val t = if (GuideMotion.enabled) ((now - cueStart) % cycle).toFloat() / cycle else 1f

        if (cue is GuideCue.Taps) {
            drawTaps(canvas, cue as GuideCue.Taps, t, colour)
            return
        }
        if (ghost.size < 2) return
        drawGhostPath(canvas, ghost, t, colour)
    }

    private fun drawGhostPath(canvas: Canvas, points: List<FloatArray>, t: Float, colour: Int) {
        val hold = (cue as? GuideCue.Flick)?.hold == true
        val travel = (if (hold) t / .5f else t / .7f).coerceIn(0f, 1f)
        val alpha = when {
            t > .9f -> (1f - t) / .1f
            hold && t > .5f -> 1f
            else -> 1f
        }.coerceIn(0f, 1f)
        // All the ghost's path, thin and faint, so the shape shows at once.
        ghostPaint.color = GuideMotion.faded(colour, GHOST_LINE * alpha)
        ghostPaint.strokeWidth = dp(GHOST_WIDTH)
        linePath(points, 1f)
        canvas.drawPath(path, ghostPaint)
        // The part that the ghost has done, heavier, with the ghost finger at its end.
        ghostPaint.color = GuideMotion.faded(colour, alpha)
        ghostPaint.strokeWidth = dp(GHOST_DONE_WIDTH)
        val head = linePath(points, travel)
        canvas.drawPath(path, ghostPaint)
        dotPaint.color = GuideMotion.faded(GuideColours.CREAM, alpha)
        canvas.drawCircle(head[0], head[1], dp(GHOST_FINGER), dotPaint)
        if (hold && t in .5f..0.9f) {
            // A flick that stays: the finger pulses in place, and words would keep going.
            val pulse = ((t - .5f) * 6f) % 1f
            ringPaint.color = GuideMotion.faded(GuideColours.CREAM, (1f - pulse) * alpha)
            ringPaint.strokeWidth = dp(2f)
            canvas.drawCircle(head[0], head[1], dp(GHOST_FINGER) * (1f + pulse * 2.5f), ringPaint)
        }
    }

    /** Fills [path] with the first [portion] of [points], and returns where it ends. */
    private fun linePath(points: List<FloatArray>, portion: Float): FloatArray {
        var total = 0f
        for (i in 1 until points.size) total += distance(points[i - 1], points[i])
        val limit = total * portion
        var walked = 0f
        path.rewind()
        path.moveTo(points[0][0] * width, points[0][1] * height)
        var end = floatArrayOf(points[0][0] * width, points[0][1] * height)
        for (i in 1 until points.size) {
            val step = distance(points[i - 1], points[i])
            val share = if (step <= 0f) 1f else ((limit - walked) / step).coerceIn(0f, 1f)
            end = floatArrayOf(
                (points[i - 1][0] + (points[i][0] - points[i - 1][0]) * share) * width,
                (points[i - 1][1] + (points[i][1] - points[i - 1][1]) * share) * height,
            )
            path.lineTo(end[0], end[1])
            walked += step
            if (walked >= limit) break
        }
        return end
    }

    private fun distance(a: FloatArray, b: FloatArray) = hypot((b[0] - a[0]) * width, (b[1] - a[1]) * height)

    private fun drawTaps(canvas: Canvas, cue: GuideCue.Taps, t: Float, colour: Int) {
        // Rings for each tap, one after the other, then a drag if there is one.
        val tapsEnd = if (cue.drag) .45f else .8f
        val x = width * SPACE_CENTRE
        val y = height * ROW_CENTRE
        ringPaint.strokeWidth = dp(2.5f)
        for (tap in 0 until cue.count) {
            val start = tap * tapsEnd / cue.count
            val local = ((t - start) / (tapsEnd / cue.count)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue
            ringPaint.color = GuideMotion.faded(colour, 1f - local)
            canvas.drawCircle(x, y, dp(GHOST_FINGER) * (1f + local * 3f), ringPaint)
        }
        dotPaint.color = GuideMotion.faded(GuideColours.CREAM, if (t < .95f) 1f else (1f - t) / .05f)
        var fingerX = x
        var fingerY = y
        if (cue.drag && t > tapsEnd) {
            // The finger stays down, swipes up onto the letters, then leans to one side.
            val travel = ((t - tapsEnd) / (1f - tapsEnd - .08f)).coerceIn(0f, 1f)
            val up = (travel / UP_SHARE).coerceAtMost(1f)
            val across = ((travel - UP_SHARE) / (1f - UP_SHARE)).coerceIn(0f, 1f)
            val top = height * (ROW_CENTRE - DRAG_UP)
            fingerY = y + (top - y) * up
            fingerX = x + (width * DRAG_REACH) * across
            // The drag is the part to copy, so it is as strong as the part of a word that the ghost has done.
            ghostPaint.color = colour
            ghostPaint.strokeWidth = dp(GHOST_DONE_WIDTH)
            canvas.drawLine(x, y, x, fingerY, ghostPaint)
            if (across > 0f) canvas.drawLine(x, top, fingerX, top, ghostPaint)
        }
        canvas.drawCircle(fingerX, fingerY, dp(GHOST_FINGER), dotPaint)
    }

    private fun cycleMillis() = when (cue) {
        is GuideCue.Word -> 3000L
        is GuideCue.Flick -> if ((cue as GuideCue.Flick).hold) 3400L else 2000L
        GuideCue.SlideRow -> 2200L
        is GuideCue.Taps -> if ((cue as GuideCue.Taps).drag) 3200L else 1800L
        else -> 2400L
    }

    private fun ghostFor(cue: GuideCue): List<FloatArray> = when (cue) {
        is GuideCue.Word -> cue.word.map { letter ->
            val (x, y) = Layout.centre(letter)
            floatArrayOf(x, y)
        }
        is GuideCue.Flick -> listOf(floatArrayOf(.95f, .375f), floatArrayOf(.62f, .375f))
        GuideCue.SlideRow -> listOf(floatArrayOf(.08f, .875f), floatArrayOf(.92f, .875f))
        else -> emptyList()
    }

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private companion object {
        // Keys. Sizes are in dp unless they say otherwise. Change these to adjust the look.
        const val FRAME_MILLIS = 16f
        const val KEY_GAP = 2f
        /** How round a key is, as a part of its shorter side. */
        const val KEY_ROUNDNESS = .22f
        const val KEY_FILL_ORANGE = .13f
        const val KEY_FILL_INK = .1f
        const val KEY_LABEL_INK = .85f
        const val LABEL_LETTER = .52f
        const val LABEL_SMALL = .36f
        // The Alt symbol in the corner of a letter key, as parts of the key's size.
        const val ALT_SIZE = .26f
        const val ALT_INSET = .1f
        const val ALT_ALPHA = .6f
        /** How much of a lit key's size goes away, so that it looks pushed in. */
        const val PRESS_DEPTH = .06f
        const val HEAT_FADE_PER_FRAME = .055f
        const val SPOT_PULSE_MILLIS = 900f

        // The wave that runs over the keys when something works.
        const val WAVE_MILLIS = 360L
        const val WAVE_SPREAD_MILLIS = 380f
        const val WAVE_SCALE = .1f
        const val APPEAR_MILLIS = 280L
        const val APPEAR_SPREAD_MILLIS = 340f
        const val FLIP_MILLIS = 1500L
        /** The part of the time that one turn of the key takes. */
        const val FLIP_TURN = .12f

        // The real trail under your finger.
        const val TRAIL_WIDTH = 5f
        const val TRAIL_UNDER = 1.9f
        const val TRAIL_SHADE = .38f
        const val TRAIL_FADE_MILLIS = 700L

        // The ghost finger that shows each gesture.
        const val GHOST_WIDTH = 2f
        const val GHOST_DONE_WIDTH = 4f
        const val GHOST_LINE = .34f
        const val GHOST_FINGER = 7f
        const val SPACE_CENTRE = .5f
        const val ROW_CENTRE = .875f
        const val DRAG_REACH = .32f
        /** How far up the drag goes, as a part of the drawing's height, and the part of the drag that going up takes. */
        const val DRAG_UP = .4f
        const val UP_SHARE = .35f

        // The box round the part of the keys that the touchpad feels, as parts of the drawing's width and height.
        const val AREA_LEFT = .05f
        const val AREA_RIGHT = .95f
        /** The middle of the top row. */
        const val AREA_TOP = .5f / 4
        /** The touchpad reports its last point at row 3.54 of the 4 rows. */
        const val AREA_BOTTOM = 3.54f / 4
        /** How strongly the part that the touchpad does not read is dimmed. */
        const val AREA_SHADE = .5f
        const val AREA_RADIUS = 10f
    }
}
