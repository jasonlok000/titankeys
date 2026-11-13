package com.atlas.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The guide's slide decorations (docs/design-v2.md §7): a faint picture behind each slide that only follows its theme. It never
 * reacts to typing, a mistake, a hint or a pass, and never tells you anything.
 *
 * A pure renderer. What it draws depends only on its arguments: the slide's id, how long the slide has shown ([draw]'s millis),
 * the area, the keep-out rects, the pass tone and the dp. Each slide has its own seeded randomness, and every loop is a pure
 * function of millis, so any frame can be drawn again, in any order. What it keeps between calls (paths, the free spots it found
 * for an area) is only a cache that it rebuilds when anything changes.
 *
 * The rules, from §7:
 * - Orange at 14–24% or cream at 10–14%, the whole picture laid down as one layer so overlaps never add up; ink at 8% on the orange
 *   pass screen ([draw]'s onOrange).
 * - At most [MAX_SHAPES] shapes at once, slow loops of 6–20 s ([loopMillis]).
 * - Nothing inside a keep-out rect or within [CLEAR_DP] of one: the rects are cut out of the clip, so a shape that passes behind
 *   the title, the instruction, the field or the keyboard drawing simply goes out of sight there.
 * - Paths and Inter glyphs only, no emoji images.
 *
 * Slide ids are GuideSteps' ids (type, swipe, menu, backspace, flick, hold, steer, selectmore, alt, holdsym, symmain, symgroups,
 * clown, paste, clipboard), plus [FINALE] for the end's grass and [SETUP] for slide zero. Any other id draws nothing.
 *
 * [GuideDecorView] hosts it with the timing, fades and frame rate. How the guide adds it is in that view's KDoc.
 */
class GuideDecor(private val font: Typeface? = null) {
    /** Takes the decoration away where it would sit round a keep-out (featherOut). */
    private val eraser = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        setXfermode(PorterDuffXfermode(PorterDuff.Mode.DST_OUT))
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        textAlign = Paint.Align.CENTER
        if (font != null) typeface = font
    }
    private val path = Path()
    private val unitStar = Path().apply {
        moveTo(0f, -1f)
        quadTo(.2f, -.2f, 1f, 0f)
        quadTo(.2f, .2f, 0f, 1f)
        quadTo(-.2f, .2f, -1f, 0f)
        quadTo(-.2f, -.2f, 0f, -1f)
        close()
    }
    private val unitHeart = Path().apply {
        moveTo(0f, .42f)
        cubicTo(-.62f, 0f, -.5f, -.72f, 0f, -.38f)
        cubicTo(.5f, -.72f, .62f, 0f, 0f, .42f)
        close()
    }
    private val clip = Path().apply {
        val l = CLIP_LENGTH
        moveTo(3f, 13f)
        lineTo(3f, l - 6f)
        arcTo(-8f, l - 11.5f, 3f, l - .5f, 0f, 180f, false)
        lineTo(-8f, 8f)
        arcTo(-8f, 0f, 8f, 16f, 180f, 180f, false)
        lineTo(8f, l - 12f)
        arcTo(-3f, l - 17.5f, 8f, l - 6.5f, 0f, 180f, false)
        lineTo(-3f, 10f)
    }
    private val gears = HashMap<Int, Path>()
    private val spotCache = LinkedHashMap<Long, FloatArray>()
    private val polyline = FloatArray(800)

    private lateinit var canvas: Canvas
    private val area = RectF()
    private var keepOut: List<RectF> = emptyList()
    private var dp = 1f
    private var colour = 0
    private var shapes = 0

    /**
     * Draws [slide]'s decoration as it is [millis] after the slide settled, inside [area] and outside [keepOut] (each with
     * [CLEAR_DP] around it), in the pass screen's ink when [onOrange]. [fade] runs from 0 to 1 for the fades in and between slides.
     * Returns how many shapes it drew, never more than [MAX_SHAPES].
     */
    fun draw(canvas: Canvas, slide: String, millis: Long, area: RectF, keepOut: List<RectF>, onOrange: Boolean, dp: Float,
             fade: Float = 1f): Int {
        val look = LOOKS[slide] ?: return 0
        // The grass sets its own three strengths (owner, 2026-11-13), so its layer is at full strength.
        val level = if (slide == FINALE) 1f else if (onOrange) INK_LEVEL else look.level
        val alpha = (level * fade.coerceIn(0f, 1f) * 255).roundToInt()
        if (alpha <= 0 || area.width() <= 0f || area.height() <= 0f) return 0
        this.canvas = canvas
        this.area.set(area.left, area.top, area.right, area.bottom)
        this.keepOut = keepOut
        this.dp = dp
        colour = if (onOrange) INK else look.colour
        shapes = 0
        canvas.save()
        canvas.clipRect(area.left, area.top, area.right, area.bottom)
        canvas.saveLayerAlpha(this.area, alpha)
        val t = loopTime(slide, millis)
        when (slide) {
            "type" -> redPen(t)
            "swipe" -> airSwipes(t)
            "menu" -> starfield(t)
            "backspace" -> typewriter(t)
            "flick" -> paperBalls(t)
            "hold" -> shredder(t)
            "steer" -> nightDrive(t)
            "selectmore" -> heartbeat(t)
            "alt" -> asteriskSnow(t)
            "holdsym" -> buoys(t)
            "symmain" -> peekaboo(t)
            "symgroups" -> forever(t)
            "clown" -> bigTop(t)
            "paste" -> stickyNotes(t)
            "clipboard" -> zeroG(t)
            FINALE -> grass(t)
            SETUP -> gearsIn(t)
        }
        featherOut()
        canvas.restore()
        canvas.restore()
        return shapes
    }

    // Typing

    /** Red pen: proofreader's marks draw themselves stroke by stroke, linger, fade, and the next starts somewhere else. */
    private fun redPen(t: Float) {
        val spots = spots(1, 34f, MARKS.size)
        val first = floor(t / MARK_EVERY).toInt()
        for (n in first - 2..first) {
            val age = t - n * MARK_EVERY
            if (age < 0f || age >= MARK_LIFE) continue
            val kind = Math.floorMod(n, MARKS.size)
            val reveal = sine(age / MARK_DRAW)
            val fadeOut = 1f - smooth((age - MARK_LINGER) / (MARK_LIFE - MARK_LINGER))
            val x = spots[kind * 2]
            val y = spots[kind * 2 + 1]
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate((hash(11, kind) - .5f) * 14f)
            canvas.scale(1.25f * dp, 1.25f * dp)
            stroke(fadeOut, 1.8f)
            strokes(MARKS[kind], reveal)
            // The circled letter: the letter is already there; the pen draws round it.
            if (kind == 2) {
                fill(fadeOut * .8f)
                paint.textSize = 19f
                canvas.drawText("a", 0f, 6.5f, paint)
            }
            canvas.restore()
            shapes++
        }
    }

    /** Air swipes: one long soft S-curve at a time sweeps across with a tapering tail, like a swipe's trail, then runs off. */
    private fun airSwipes(t: Float) {
        val slot = floor(t / SWIPE_EVERY).toInt()
        val c = SWIPES[Math.floorMod(slot, SWIPES.size)]
        val u = (t - slot * SWIPE_EVERY) / SWIPE_SWEEP
        val mid = band(6, 100f)
        val (top, bottom) = open(mid)
        val swing = max((bottom - top) * .42f, 36f * dp)
        if (u >= 1f) return
        val head = sine(u) * (1f + SWIPE_TAIL)
        val tail = head - SWIPE_TAIL
        val from = max(tail, 0f)
        val to = min(head, 1f)
        if (to <= from) return
        val w = area.width()
        val n = 56
        // Points along the curve, then the outline: out along one side, back along the other. The curve's y runs from -1 to 1
        // across the open band.
        for (i in 0..n) {
            val s = from + (to - from) * i / n
            polyline[i * 2] = area.left + bezier(c[0], c[2], c[4], c[6], s) * w
            polyline[i * 2 + 1] = mid + bezier(c[1], c[3], c[5], c[7], s) * swing
        }
        path.reset()
        for (side in 0..1) {
            for (k in 0..n) {
                val i = if (side == 0) k else n - k
                val s = from + (to - from) * i / n
                val along = ((s - tail) / SWIPE_TAIL).coerceIn(0f, 1f)
                val half = (.35f + 4.4f * along.pow(1.6f)) * dp
                val a = max(i - 1, 0)
                val b = min(i + 1, n)
                val dx = polyline[b * 2] - polyline[a * 2]
                val dy = polyline[b * 2 + 1] - polyline[a * 2 + 1]
                val len = max(hypot(dx, dy), .001f)
                val sign = if (side == 0) 1f else -1f
                val px = polyline[i * 2] - dy / len * half * sign
                val py = polyline[i * 2 + 1] + dx / len * half * sign
                if (k == 0 && side == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
        }
        path.close()
        fill(1f)
        canvas.drawPath(path, paint)
        shapes++
        // The rounded head, while it is still on the curve.
        if (head <= 1f) {
            canvas.drawCircle(polyline[n * 2], polyline[n * 2 + 1], 4.75f * dp, paint)
            shapes++
        }
    }

    /** Starfield: four-point sparkles twinkle at scattered spots, and a few drift slowly up. */
    private fun starfield(t: Float) {
        val fixed = spots(3, 16f, 8)
        for (i in 0 until fixed.size / 2) {
            val period = if (i % 2 == 0) 6000f else 12000f
            val pulse = (.5f + .5f * sin(TAU * (t / period + hash(31, i)))).pow(2f)
            val r = (10f + 11f * hash(32, i)) * (.45f + .55f * pulse)
            star(fixed[i * 2], fixed[i * 2 + 1], r, 12f * sin(TAU * (t / 12000f + hash(33, i))), .4f + .6f * pulse)
        }
        val rising = spots(4, 22f, 3)
        for (i in 0 until rising.size / 2) {
            val u = frac(t / 12000f + i / 3f)
            val x = rising[i * 2] + 9f * dp * sin(TAU * u + i)
            val y = rising[i * 2 + 1] + (50f - 120f * u) * dp
            star(x, y, (7f + 4f * hash(34, i)), u * 90f, sin(PI.toFloat() * u))
        }
    }

    private fun star(x: Float, y: Float, radiusDp: Float, degrees: Float, alpha: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(degrees)
        canvas.scale(radiusDp * dp, radiusDp * dp)
        fill(alpha)
        canvas.drawPath(unitStar, paint)
        canvas.restore()
        shapes++
    }

    // Delete

    /** Typewriter e: a huge e like old metal type, and every few seconds a strip of correction tape glides across part of it. */
    private fun typewriter(t: Float) {
        val (top, bottom) = open(band(5, 100f))
        // Inter's e is about .56 of its size tall, and its shoulder sits 5 dp lower: it fills the open band and stays in it. It
        // used to be at least 110 dp, which, in the guide's real layout, ran into the field above it (v2 wiring).
        val size = min((bottom - top - 5f * dp) / .56f * .9f, 220f * dp).coerceAtLeast(40f * dp)
        val spot = floatArrayOf(area.left + area.width() * .7f, (top + bottom) / 2 - 2.5f * dp)
        val cx = spot[0]
        val baseline = spot[1] + .27f * size
        val u = t / TYPEWRITER_LOOP
        val roll = sine(u / .2f)
        val peel = smooth((u - .66f) / .17f)
        val tape = roll * (1f - peel)
        val tapeLeft = cx - .4f * size
        val tapeRight = tapeLeft + .8f * size * roll
        val tapeTop = spot[1] - .03f * size
        val tapeBottom = tapeTop + .14f * size
        paint.textSize = size
        canvas.save()
        if (tape > .01f) {
            canvas.rotate(-4f, cx, spot[1])
            canvas.clipOutRect(tapeLeft, tapeTop, tapeRight, tapeBottom)
            canvas.rotate(4f, cx, spot[1])
        }
        // The type's shoulder: its outline set off a little, then the face itself.
        stroke(.55f, 2.4f * dp)
        canvas.drawText("e", cx + 5f * dp, baseline + 5f * dp, paint)
        shapes++
        fill(1f)
        canvas.drawText("e", cx, baseline, paint)
        shapes++
        canvas.restore()
        if (tape > .01f) {
            canvas.save()
            canvas.rotate(-4f, cx, spot[1])
            path.reset()
            path.moveTo(tapeLeft, tapeTop)
            path.lineTo(tapeRight, tapeTop)
            // The tape's torn end, while it rolls out.
            val teeth = 5
            for (k in 1..teeth) {
                val y = tapeTop + (tapeBottom - tapeTop) * k / teeth
                path.lineTo(tapeRight + if (k % 2 == 1) -3.5f * dp * roll else 0f, y)
            }
            path.lineTo(tapeLeft, tapeBottom)
            path.close()
            fill(.8f * tape)
            canvas.drawPath(path, paint)
            canvas.restore()
            shapes++
        }
    }

    /** Paper balls: now and then a crumpled ball arcs across from right to left, as if tossed at a bin off screen. */
    private fun paperBalls(t: Float) {
        for (i in 0..1) {
            val u = (t - i * BALL_LOOP / 2) / BALL_FLIGHT
            if (u < 0f || u >= 1f) continue
            val mid = band(7, 100f)
            val (top, bottom) = open(mid)
            val room = (bottom - top) / dp
            val startY = mid + (if (i == 0) -.1f else .15f) * room * dp
            val x = area.right + 30f * dp - (area.width() + 60f * dp) * u
            val y = startY - 4f * (if (i == 0) .45f else .3f) * room * dp * u * (1f - u) + .3f * room * dp * u * u
            val r = (if (i == 0) 20f else 16f) * dp
            // Two soft speed lines behind it.
            stroke(.6f * (1f - u * .6f), 1.6f * dp)
            for (k in 0..1) {
                val ly = y + (k * 2 - 1) * r * .45f
                canvas.drawLine(x + r * 1.5f, ly, x + r * (2.6f + k * .5f), ly + r * .25f, paint)
            }
            shapes++
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(-560f * u + i * 70f)
            crumple(r, i)
            fill(.3f)
            canvas.drawPath(path, paint)
            crumple(r, i)
            // Creases across the ball.
            for (k in 0..2) {
                val a = TAU * hash(42 + i, k)
                val b = a + 2.2f + hash(44 + i, k)
                path.moveTo(cos(a) * r * .75f, sin(a) * r * .75f)
                path.lineTo(cos(a + .9f) * r * .2f, sin(a + .9f) * r * .2f)
                path.lineTo(cos(b) * r * .7f, sin(b) * r * .7f)
            }
            stroke(1f, 1.6f * dp)
            canvas.drawPath(path, paint)
            canvas.restore()
            shapes++
        }
    }

    private fun crumple(r: Float, i: Int) {
        path.reset()
        val corners = 13
        for (k in 0 until corners) {
            val a = TAU * k / corners
            val rr = r * (.84f + .26f * hash(40 + i, k))
            if (k == 0) path.moveTo(cos(a) * rr, sin(a) * rr) else path.lineTo(cos(a) * rr, sin(a) * rr)
        }
        path.close()
    }

    /** Shredder belt: letter tiles slide left on a belt and shred into strips at the left edge, endlessly. */
    private fun shredder(t: Float) {
        val y = band(1, 56f)
        val tile = 34f * dp
        val pitch = SHRED_PITCH * dp
        val mouth = area.left + 72f * dp
        val shift = t / SHRED_STEP * pitch
        // The belt's two rails and the shredder's mouth.
        stroke(.5f, 1.3f * dp)
        canvas.drawLine(mouth, y - tile / 2 - 6f * dp, area.right, y - tile / 2 - 6f * dp, paint)
        canvas.drawLine(mouth, y + tile / 2 + 6f * dp, area.right, y + tile / 2 + 6f * dp, paint)
        shapes++
        stroke(.8f, 1.6f * dp)
        canvas.drawLine(mouth, y - tile / 2 - 12f * dp, mouth, y + tile / 2 + 12f * dp, paint)
        shapes++
        val firstSlot = floor((shift - (area.width() + 2 * pitch)) / pitch).toInt()
        for (slot in firstSlot..firstSlot + 12) {
            val x = area.right + pitch + slot * pitch - shift
            if (x > area.right + tile || x < area.left - 3 * tile) continue
            val letter = SHRED_LETTERS[Math.floorMod(slot, SHRED_LETTERS.length)].toString()
            val u = ((mouth - x) / (110f * dp)).coerceIn(0f, 1f)
            if (u >= 1f) continue
            if (u <= 0f) {
                tile(x, y, tile, letter, 1f)
            } else {
                for (k in 0 until SHRED_STRIPS) {
                    val w = tile / SHRED_STRIPS
                    canvas.save()
                    canvas.translate(-u * (SHRED_STRIPS - k) * 2f * dp, u.pow(1.6f) * (12f + 16f * hash(50, k + slot)) * dp)
                    canvas.rotate(u * (hash(51, k + slot) - .5f) * 30f, x + k * w + w / 2, y)
                    canvas.clipRect(x + k * w, y - tile, x + (k + 1) * w, y + tile)
                    tile(x, y, tile, letter, (1f - u).pow(1.3f))
                    canvas.restore()
                }
            }
            shapes++
        }
    }

    private fun tile(x: Float, y: Float, size: Float, letter: String, alpha: Float) {
        val r = 7f * dp
        path.reset()
        path.moveTo(x + r, y - size / 2)
        path.lineTo(x + size - r, y - size / 2)
        path.quadTo(x + size, y - size / 2, x + size, y - size / 2 + r)
        path.lineTo(x + size, y + size / 2 - r)
        path.quadTo(x + size, y + size / 2, x + size - r, y + size / 2)
        path.lineTo(x + r, y + size / 2)
        path.quadTo(x, y + size / 2, x, y + size / 2 - r)
        path.lineTo(x, y - size / 2 + r)
        path.quadTo(x, y - size / 2, x + r, y - size / 2)
        path.close()
        stroke(alpha, 1.6f * dp)
        canvas.drawPath(path, paint)
        fill(alpha)
        paint.textSize = 19f * dp
        canvas.drawText(letter, x + size / 2, y + 7f * dp, paint)
    }

    // Steering

    /** Night drive: dashed lane lines stream towards you from a vanishing point behind the title, between the road's edges. */
    private fun nightDrive(t: Float) {
        val vx = area.centerX()
        val vy = area.top + area.height() * .2f
        val bottom = area.bottom + area.height() * .15f
        val ends = floatArrayOf(-.95f, -.32f, .32f, .95f)
        // The road's edges, faint and solid.
        stroke(.35f, 1.4f * dp)
        for (e in intArrayOf(0, 3)) canvas.drawLine(vx, vy, vx + ends[e] * area.width(), bottom, paint)
        shapes += 2
        val k = 6f
        fun along(z: Float) = (1f / (1f + k * z) - 1f / (1f + k)) / (1f - 1f / (1f + k))
        for (line in 1..2) {
            val ex = vx + ends[line] * area.width()
            for (d in 0 until DASHES) {
                val z = 1f - frac(d.toFloat() / DASHES + t / DRIVE_LOOP)
                val s0 = along(z)
                val s1 = along(max(z - .07f, 0f))
                val fadeIn = smooth(s0 / .12f)
                if (fadeIn <= 0f) continue
                val w0 = (.5f + 5f * s0) * dp
                val w1 = (.5f + 5f * s1) * dp
                val x0 = vx + (ex - vx) * s0
                val y0 = vy + (bottom - vy) * s0
                val x1 = vx + (ex - vx) * s1
                val y1 = vy + (bottom - vy) * s1
                path.reset()
                path.moveTo(x0 - w0, y0)
                path.lineTo(x0 + w0, y0)
                path.lineTo(x1 + w1, y1)
                path.lineTo(x1 - w1, y1)
                path.close()
                fill(fadeIn)
                canvas.drawPath(path, paint)
                shapes++
            }
        }
    }

    /** Heartbeat: an ECG line traces across, flat, beat, beat, flat, and small outline hearts pulse on the beats. */
    private fun heartbeat(t: Float) {
        val y = band(2, 90f)
        val sweep = 4200f
        val head = area.left + area.width() * (t / sweep)
        val trail = area.width() * .6f
        val step = 2f * dp
        val from = max(area.left, head - trail)
        val to = min(area.right, head)
        if (to > from) {
            val chunks = 12
            for (c in 0 until chunks) {
                val a = from + (to - from) * c / chunks
                val b = from + (to - from) * (c + 1) / chunks
                path.reset()
                var x = a
                path.moveTo(x, y - ecg((x - area.left) / dp) * ECG_SCALE * dp)
                while (x < b) {
                    x = min(x + step, b)
                    path.lineTo(x, y - ecg((x - area.left) / dp) * ECG_SCALE * dp)
                }
                stroke(((b - (head - trail)) / trail).coerceIn(0f, 1f).pow(1.4f), 2.2f * dp)
                paint.strokeCap = Paint.Cap.BUTT
                canvas.drawPath(path, paint)
                paint.strokeCap = Paint.Cap.ROUND
            }
            shapes++
            if (head <= area.right) {
                fill(1f)
                canvas.drawCircle(head, y - ecg((head - area.left) / dp) * ECG_SCALE * dp, 3.2f * dp, paint)
                shapes++
            }
        }
        for (b in BEATS.indices) {
            val peakX = area.left + (BEATS[b] * area.width() / dp + 24f) * dp
            val at = (peakX - area.left) / area.width() * sweep
            val since = t - at
            val pulse = if (since < 0f) exp(-(since / 90f).pow(2f)) else exp(-since / 420f)
            val size = 21f * (1f + .35f * pulse) * dp
            canvas.save()
            canvas.translate(peakX + 30f * dp, y - 24f * dp)
            canvas.scale(size, size)
            stroke(.45f + .55f * pulse, 1.6f * dp / size)
            canvas.drawPath(unitHeart, paint)
            canvas.restore()
            shapes++
        }
    }

    /** The ECG's height in dp at [x] dp from the left: flat, a beat, another, flat. */
    private fun ecg(x: Float): Float {
        val w = area.width() / dp
        for (start in BEATS) {
            val o = x - start * w
            if (o < 0f || o > 70f) continue
            return when {
                o < 14f -> 4f * sin(PI.toFloat() * o / 14f)
                o < 18f -> 0f
                o < 21f -> -6f * (o - 18f) / 3f
                o < 24f -> -6f + 40f * (o - 21f) / 3f
                o < 28f -> 34f - 46f * (o - 24f) / 4f
                o < 31f -> -12f + 12f * (o - 28f) / 3f
                o < 44f -> 0f
                o < 66f -> 7f * sin(PI.toFloat() * (o - 44f) / 22f)
                else -> 0f
            }
        }
        return 0f
    }

    // Symbols

    /** Asterisk snow: asterisks of different sizes fall slowly, turning like snowflakes. */
    private fun asteriskSnow(t: Float) {
        val n = 11
        for (i in 0 until n) {
            val period = SNOW_LOOP / (1 + i % 3)
            val u = frac(t / period + hash(60, i))
            val r = (8f + 12f * hash(61, i)) * dp
            val x = area.left + area.width() * (i + .2f + .6f * hash(62, i)) / n + 10f * dp * sin(TAU * (2f * u + hash(63, i)))
            val y = area.top - 20f * dp + (area.height() + 40f * dp) * u
            val turn = 60f * u * (if (i % 2 == 0) 1f else -1f) + 30f * hash(64, i)
            stroke(.55f + .45f * hash(65, i), r * .26f)
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(turn)
            for (k in 0..2) {
                val a = PI.toFloat() * k / 3f
                canvas.drawLine(-cos(a) * r, -sin(a) * r, cos(a) * r, sin(a) * r, paint)
            }
            canvas.restore()
            shapes++
        }
    }

    /** Buoys: big question marks bob and tilt on a little swell, as if puzzled. */
    private fun buoys(t: Float) {
        val spots = spots(10, 40f, 3)
        for (i in 0 until spots.size / 2) {
            val period = BUOY_LOOP / (2 + i % 2)
            val phase = hash(70, i)
            val size = floatArrayOf(84f, 66f, 100f)[i] * dp
            val cx = spots[i * 2]
            val cy = spots[i * 2 + 1] + 5f * dp * sin(TAU * (t / period + phase))
            canvas.save()
            canvas.rotate(10f * sin(TAU * (t / period + phase + .2f)), cx, cy + size * .3f)
            fill(1f)
            paint.textSize = size
            canvas.drawText("?", cx, cy + size * .36f, paint)
            canvas.restore()
            shapes++
            // The swell under it.
            val wy = spots[i * 2 + 1] + size * .5f
            path.reset()
            for (k in 0..12) {
                val x = cx - size * .32f + size * .64f * k / 12
                val yy = wy + 2.2f * dp * sin(TAU * (k / 6f - t / period))
                if (k == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            stroke(.6f, 1.6f * dp)
            canvas.drawPath(path, paint)
            shapes++
        }
    }

    /** Peekaboo: ampersands peek out from the screen's edges and from behind the title and the keys, then slide back. */
    private fun peekaboo(t: Float) {
        val size = 66f * dp
        val reach = .68f * size
        for (i in 0 until 4) {
            val u = frac(t / PEEK_LOOP - i / 4f) * PEEK_LOOP
            if (u > 4600f) continue
            val out = when {
                u < 1000f -> backOut(u / 1000f)
                u < 3600f -> 1f
                else -> 1f - sine((u - 3600f) / 1000f)
            }
            val wobble = if (u in 1000f..3600f) 4f * sin(TAU * (u - 1000f) / 2600f) else 0f
            var x: Float
            var y: Float
            var tilt: Float
            when (i) {
                0 -> { // the right edge
                    x = area.right + size * .45f - reach * out
                    y = band(8, 90f) - 14f * dp
                    tilt = -16f
                }
                1 -> { // from behind the end of the title
                    val r = titleLike()
                    x = (r?.right ?: area.centerX()) + CLEAR_DP * dp - size * .45f + (reach + 4f * dp) * out
                    y = (r?.centerY() ?: area.top + area.height() * .25f)
                    tilt = 14f
                }
                2 -> { // the left edge
                    x = area.left - size * .45f + reach * out
                    y = band(8, 90f) + 10f * dp
                    tilt = 16f
                }
                else -> { // over the top of the keys
                    val r = floorLike()
                    x = area.left + area.width() * .7f
                    y = (r?.top ?: area.bottom) - CLEAR_DP * dp + size * .45f - (reach + 4f * dp) * out
                    tilt = -8f
                }
            }
            canvas.save()
            canvas.rotate(tilt + wobble, x, y)
            fill(1f)
            paint.textSize = size
            canvas.drawText("&", x, y + size * .36f, paint)
            canvas.restore()
            shapes++
        }
    }

    /** Forever: a dot runs endlessly along a big faint ∞, leaving a fading trail. */
    private fun forever(t: Float) {
        val cy = band(3, 80f)
        val (top, bottom) = open(cy)
        val a = min(area.width() * .4f, (bottom - top) * 1.3f)
        val cx = area.centerX()
        fun x(s: Float) = cx + a * cos(s) / (1f + sin(s) * sin(s))
        fun y(s: Float) = cy + a * sin(s) * cos(s) / (1f + sin(s) * sin(s))
        path.reset()
        for (k in 0..120) {
            val s = TAU * k / 120
            if (k == 0) path.moveTo(x(s), y(s)) else path.lineTo(x(s), y(s))
        }
        path.close()
        stroke(.4f, 2.5f * dp)
        canvas.drawPath(path, paint)
        shapes++
        val head = TAU * t / FOREVER_LOOP
        val chunks = 16
        val length = PI.toFloat() * .9f
        for (c in 0 until chunks) {
            path.reset()
            val s0 = head - length + length * c / chunks
            val s1 = head - length + length * (c + 1) / chunks
            for (k in 0..4) {
                val s = s0 + (s1 - s0) * k / 4
                if (k == 0) path.moveTo(x(s), y(s)) else path.lineTo(x(s), y(s))
            }
            val along = (c + 1f) / chunks
            stroke(along.pow(1.5f), (1f + 3f * along) * dp)
            canvas.drawPath(path, paint)
        }
        shapes++
        fill(.25f)
        canvas.drawCircle(x(head), y(head), 10f * dp, paint)
        fill(1f)
        canvas.drawCircle(x(head), y(head), 4.5f * dp, paint)
        shapes += 2
    }

    /** Big top: circus-tent rays turn slowly from the top centre under a scalloped edge, and three balls are juggled. */
    private fun bigTop(t: Float) {
        val cx = area.centerX()
        val cy = area.top - 24f * dp
        val sector = 140f / 16f
        val turn = t / TENT_LOOP * 2f * sector
        val reach = hypot(area.width(), area.height()) * 1.2f
        for (k in -1..8) {
            val start = 20f + turn + (k * 2 - 1) * sector
            val mid = start + sector / 2
            val fade = 1f - smooth((abs(mid - 90f) - 38f) / 28f)
            if (fade <= 0f) continue
            path.reset()
            path.moveTo(cx, cy)
            val a0 = start * PI.toFloat() / 180f
            val a1 = (start + sector) * PI.toFloat() / 180f
            path.lineTo(cx + cos(a0) * reach, cy + sin(a0) * reach)
            path.lineTo(cx + cos(a1) * reach, cy + sin(a1) * reach)
            path.close()
            fill(.42f * fade)
            canvas.drawPath(path, paint)
            shapes++
        }
        // The tent's scalloped edge.
        path.reset()
        val scallops = 9
        val sw = area.width() / scallops
        path.moveTo(area.left, area.top)
        for (k in 0 until scallops) {
            val x0 = area.left + k * sw
            path.quadTo(x0 + sw / 2, area.top + 22f * dp, x0 + sw, area.top)
        }
        path.close()
        fill(.9f)
        canvas.drawPath(path, paint)
        shapes++
        val hands = spots(13, 48f, 1)
        for (b in 0..2) {
            val p = frac(t / JUGGLE_LOOP + b / 3f)
            val leftToRight = p < .5f
            val q = if (leftToRight) p * 2f else (p - .5f) * 2f
            val from = if (leftToRight) -30f else 30f
            val x = hands[0] + (from + (-from * 2f) * q) * dp
            val y = hands[1] + (30f - 4f * (if (leftToRight) 74f else 54f) * q * (1f - q)) * dp
            fill(.95f)
            canvas.drawCircle(x, y, 6.5f * dp, paint)
            shapes++
        }
    }

    // Copy and paste

    /** Sticky notes: notes pinned at angles sway as if in a breeze, and now and then one flaps a corner. */
    private fun stickyNotes(t: Float) {
        val spots = spots(14, 40f, 4)
        val flapping = Math.floorMod(floor(t / NOTE_FLAP).toInt(), 4)
        val flapU = (t % NOTE_FLAP) / NOTE_FLAP
        for (i in 0 until spots.size / 2) {
            val period = NOTE_LOOP / (2 + i % 2)
            val base = (hash(80, i) - .5f) * 22f
            val sway = base + 2.6f * sin(TAU * (t / period + hash(81, i)))
            val corner = 5f + if (i == flapping) 14f * bump(flapU / .55f) else 0f
            val x = spots[i * 2]
            val y = spots[i * 2 + 1] - 28f * dp
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(sway)
            canvas.scale(dp, dp)
            path.reset()
            path.moveTo(-28f, 4f)
            path.lineTo(28f, 4f)
            path.lineTo(28f, 60f - corner)
            path.lineTo(28f - corner, 60f)
            path.lineTo(-28f, 60f)
            path.close()
            fill(.45f)
            canvas.drawPath(path, paint)
            // The corner, folded over.
            path.reset()
            path.moveTo(28f, 60f - corner)
            path.lineTo(28f - corner, 60f)
            path.lineTo(28f - corner * 1.05f, 60f - corner * 1.05f)
            path.close()
            fill(.9f)
            canvas.drawPath(path, paint)
            stroke(.85f, 1.5f)
            canvas.drawLine(-18f, 26f, 12f, 26f, paint)
            canvas.drawLine(-18f, 36f, 4f, 36f, paint)
            fill(1f)
            canvas.drawCircle(0f, 6f, 4f, paint)
            canvas.restore()
            shapes++
        }
    }

    /** Zero-g clips: outline paper clips float, turning slowly, as if in zero gravity. */
    private fun zeroG(t: Float) {
        val spots = spots(15, 32f, 5)
        for (i in 0 until spots.size / 2) {
            val x = spots[i * 2] + 24f * dp * sin(TAU * (t / CLIPS_LOOP + hash(90, i)))
            val y = spots[i * 2 + 1] + 16f * dp * sin(TAU * (2f * t / CLIPS_LOOP + hash(91, i)))
            val turn = 360f * (t / CLIPS_LOOP) * (if (i % 2 == 0) 1f else -1f) + 360f * hash(92, i)
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(turn)
            val scale = (1f + .45f * hash(93, i)) * dp
            canvas.scale(scale, scale)
            canvas.translate(0f, -CLIP_LENGTH / 2)
            stroke(1f, 2.3f)
            canvas.drawPath(clip, paint)
            canvas.restore()
            shapes++
        }
    }

    // The end and setup

    /**
     * Grass (owner, 2026-11-13, from their picture, "match it to our minimal style"): a dense band of pointed blades along the
     * screen's bottom edge, in three overlapping layers for depth, back to front at [GRASS_TONES]: flat shapes, no outlines, the
     * ink's three strengths on the orange. It grows up from the edge as the finale plays, each blade in its own time, sways gently
     * on a slow wind, and three small flowers open in it. Never taller than the room under the keep-outs, the end's buttons above all.
     */
    private fun grass(t: Float) {
        val bottom = area.bottom
        val ceiling = keepOut.filter { it.bottom < bottom - 1f && it.width() > area.width() * .4f }.maxOfOrNull { it.bottom + CLEAR_DP * dp }
        val room = (if (ceiling == null) 1f else (bottom - ceiling) / (GRASS_TALLEST * dp)).coerceIn(0f, 1f)
        if (room <= 0f) return
        // One slow wind across the band, from the start, so the loop folds back without a jump.
        val wind = TAU * t / GRASS_LOOP
        val blade = BLADE_DP * dp * room
        fun sway(x: Float, h: Float, layer: Int) = (1.5f * dp + h * .07f) * sin(wind - (x - area.left) / area.width() * 1.6f * TAU + layer * .9f)
        for (layer in 0..2) {
            if (layer == 2) flowers(t, bottom, blade, ::sway)
            val pitch = GRASS_PITCH[layer] * dp
            val (low, high) = GRASS_HEIGHTS[layer]
            path.reset()
            var drawn = false
            var i = 0
            while (true) {
                val x = area.left + (i - .5f + (hash(110 + layer, i) - .5f) * .6f) * pitch
                if (x > area.right + pitch) break
                val grown = smooth((t - hash(120 + layer, i) * GRASS_STAGGER - layer * 180f) / GRASS_GROW)
                val h = blade * (low + (high - low) * hash(130 + layer, i).pow(1.3f)) * grown
                i++
                if (h < .5f * dp) continue
                val w = (2.8f + 2.6f * hash(140 + layer, i)) * dp
                val lean = (hash(150 + layer, i) - .5f) * .55f
                val tipX = x + lean * h + sway(x, h, layer)
                val tipY = bottom - h
                val bend = x + lean * h * .3f + sway(x, h, layer) * .3f
                path.moveTo(x - w, bottom + 2f * dp)
                path.quadTo(bend - w * .8f, bottom - h * .42f, tipX, tipY)
                path.quadTo(bend + w * .8f, bottom - h * .42f, x + w, bottom + 2f * dp)
                path.close()
                drawn = true
            }
            if (!drawn) continue
            flat(GRASS_TONES[layer])
            shapes++
        }
    }

    /**
     * Fills [path] at [tone] as a flat shape: what's under it goes first, so where layers overlap the one in front shows its own tone,
     * as in the owner's picture, instead of the inks adding up.
     */
    private fun flat(tone: Float) {
        eraser.alpha = 255
        canvas.drawPath(path, eraser)
        fill(tone)
        canvas.drawPath(path, paint)
    }

    /** Three small flowers in the grass, behind its front blades: a stem, then five round petals that open, and their middle. */
    private fun flowers(t: Float, bottom: Float, blade: Float, sway: (Float, Float, Int) -> Float) {
        for (f in 0..2) {
            val open = smooth((t - GRASS_GROW * .75f - f * 450f) / 1100f)
            val stand = smooth((t - GRASS_GROW * .35f - f * 300f) / GRASS_GROW)
            if (stand <= 0f) continue
            val x = area.left + area.width() * (FLOWERS[f] + (hash(160, f) - .5f) * .06f)
            val h = blade * (.95f + .07f * f) * stand
            val hx = x + sway(x, h, 1)
            val hy = bottom - h
            path.reset()
            path.moveTo(x - .9f * dp, bottom + 2f * dp)
            path.quadTo(x + (hx - x) * .4f - .7f * dp, bottom - h * .5f, hx, hy)
            path.quadTo(x + (hx - x) * .4f + .7f * dp, bottom - h * .5f, x + .9f * dp, bottom + 2f * dp)
            path.close()
            if (open > 0f) for (k in 0 until 5) {
                val a = TAU * k / 5 + f
                path.addCircle(hx + cos(a) * 4.6f * dp * open, hy + sin(a) * 4.6f * dp * open, 3.6f * dp * open, Path.Direction.CW)
            }
            flat(GRASS_TONES[1])
            if (open > 0f) {
                path.reset()
                path.addCircle(hx, hy, 2.6f * dp * open, Path.Direction.CW)
                flat(GRASS_TONES[2])
            }
            shapes++
        }
    }

    /** Gears: faint gear outlines turn slowly in two corners, each a big one meshed with a small one. */
    private fun gearsIn(t: Float) {
        val turn = 360f * (t / GEAR_LOOP) * 2f / BIG_TEETH
        val corners = arrayOf(
            floatArrayOf(area.right - 26f * dp, area.top + 40f * dp, 135f),
            floatArrayOf(area.left + 26f * dp, area.bottom - 40f * dp, -45f),
        )
        for ((n, corner) in corners.withIndex()) {
            val r1 = BIG_TEETH * GEAR_MODULE / 2 * dp
            val r2 = SMALL_TEETH * GEAR_MODULE / 2 * dp
            val alpha = corner[2]
            val a = alpha * PI.toFloat() / 180f
            val spin = if (n == 0) turn else -turn
            gear(corner[0], corner[1], BIG_TEETH, alpha + spin)
            gear(corner[0] + cos(a) * (r1 + r2), corner[1] + sin(a) * (r1 + r2), SMALL_TEETH,
                alpha + 180f + 180f / SMALL_TEETH - spin * BIG_TEETH / SMALL_TEETH)
        }
    }

    private fun gear(x: Float, y: Float, teeth: Int, degrees: Float) {
        val shape = gears.getOrPut(teeth) { gearPath(teeth) }
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(degrees)
        canvas.scale(dp, dp)
        stroke(1f, 1.8f)
        canvas.drawPath(shape, paint)
        canvas.restore()
        shapes++
    }

    /** A gear's outline in dp with its first tooth on angle 0, with its hub and a ring. */
    private fun gearPath(teeth: Int): Path {
        val r = teeth * GEAR_MODULE / 2
        val tip = r + .55f * GEAR_MODULE
        val root = r - .65f * GEAR_MODULE
        val p = TAU / teeth
        val shape = Path()
        for (k in 0 until teeth) {
            val a = k * p
            val points = floatArrayOf(a - .5f * p, root, a - .24f * p, root, a - .13f * p, tip, a + .13f * p, tip, a + .24f * p, root)
            for (j in 0 until points.size / 2) {
                val px = cos(points[j * 2]) * points[j * 2 + 1]
                val py = sin(points[j * 2]) * points[j * 2 + 1]
                if (k == 0 && j == 0) shape.moveTo(px, py) else shape.lineTo(px, py)
            }
        }
        shape.close()
        shape.addCircle(0f, 0f, r * .25f, Path.Direction.CW)
        shape.addCircle(0f, 0f, root - GEAR_MODULE * 1.1f, Path.Direction.CW)
        return shape
    }

    // Placement

    /**
     * Up to [count] centres in [area], spread apart, each with [radiusDp] clear of every keep-out rect (with its [CLEAR_DP]) and of
     * the area's edges where the space allows it, and the clearest spots where it doesn't. Seeded per slide, and kept until the area
     * or the rects change.
     */
    private fun spots(seed: Int, radiusDp: Float, count: Int): FloatArray {
        val key = geometryKey(seed * 7919L + radiusDp.toBits() * 31L + count)
        spotCache[key]?.let { return it }
        val radius = radiusDp * dp
        val step = max(radius * .5f, 6f * dp)
        val xs = ((area.width()) / step).toInt()
        val ys = ((area.height()) / step).toInt()
        val candidates = ArrayList<FloatArray>()
        for (j in 0..ys) for (i in 0..xs) {
            val x = area.left + (i + .5f) * area.width() / (xs + 1)
            val y = area.top + (j + .5f) * area.height() / (ys + 1)
            val room = min(clearance(x, y), min(min(x - area.left, area.right - x), min(y - area.top, area.bottom - y)))
            candidates += floatArrayOf(x, y, room + hash(seed, i * 977 + j) * .01f)
        }
        val fits = candidates.filter { it[2] >= radius }
        val pool = if (fits.size >= count) fits else candidates.sortedByDescending { it[2] }.take(max(count * 6, 24))
        val chosen = ArrayList<FloatArray>()
        chosen += pool.maxByOrNull { hash(seed, (it[0] * 13 + it[1] * 7).toInt()) + it[2] / (radius * 40f) } ?: return floatArrayOf()
        while (chosen.size < count && chosen.size < pool.size) {
            chosen += pool.maxByOrNull { c -> chosen.minOf { hypot(c[0] - it[0], c[1] - it[1]) } } ?: break
        }
        val result = FloatArray(chosen.size * 2) { if (it % 2 == 0) chosen[it / 2][0] else chosen[it / 2][1] }
        remember(key, result)
        return result
    }

    /** The middle of the widest-open band across [area] that is [heightDp] tall, by how much of its width is clear. */
    private fun band(seed: Int, heightDp: Float): Float {
        val key = geometryKey(-seed * 104729L - heightDp.toBits())
        spotCache[key]?.let { return it[0] }
        val half = heightDp * dp / 2
        var best = area.centerY()
        var bestOpen = -1f
        var y = area.top + half
        while (y <= area.bottom - half) {
            var blocked = 0f
            val clear = CLEAR_DP * dp
            val spans = keepOut.filter { it.top - clear < y + half && it.bottom + clear > y - half }
                .map { floatArrayOf(max(it.left - clear, area.left), min(it.right + clear, area.right)) }.sortedBy { it[0] }
            var end = area.left
            for (s in spans) {
                if (s[1] <= end) continue
                blocked += s[1] - max(s[0], end)
                end = s[1]
            }
            val open = area.width() - blocked - abs(y - area.centerY()) * .01f
            if (open > bestOpen) {
                bestOpen = open
                best = y
            }
            y += 4f * dp
        }
        remember(key, floatArrayOf(best))
        return best
    }

    /** The top and bottom of the open space around [y]: up to the wide keep-out rects above and below it, with their margins. */
    private fun open(y: Float): Pair<Float, Float> {
        val clear = CLEAR_DP * dp
        var top = area.top
        var bottom = area.bottom
        for (r in keepOut) {
            if (r.width() < area.width() * .4f) continue
            if (r.bottom + clear <= y) top = max(top, r.bottom + clear)
            if (r.top - clear >= y) bottom = min(bottom, r.top - clear)
        }
        return top to bottom
    }

    private fun remember(key: Long, value: FloatArray) {
        if (spotCache.size > 64) spotCache.remove(spotCache.keys.first())
        spotCache[key] = value
    }

    private fun geometryKey(salt: Long): Long {
        var h = salt * 1_000_003L + area.left.toBits() * 31L + area.top.toBits() * 37L + area.right.toBits() * 41L +
            area.bottom.toBits() * 43L + dp.toBits() * 47L
        for (r in keepOut) h = h * 1_000_033L + r.left.toBits() + r.top.toBits() * 3L + r.right.toBits() * 5L + r.bottom.toBits() * 7L
        return h
    }

    /** How far ([x], [y]) is from the nearest keep-out rect with its margin, 0 inside one. */
    private fun clearance(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        val clear = CLEAR_DP * dp
        for (r in keepOut) {
            val dx = max(max(r.left - clear - x, 0f), x - r.right - clear)
            val dy = max(max(r.top - clear - y, 0f), y - r.bottom - clear)
            best = min(best, hypot(dx, dy))
        }
        return best
    }

    /** The keep-out rect most like the title: in the top half, with the most room to its right. */
    private fun titleLike(): RectF? = keepOut.filter { it.centerY() < area.centerY() && it.height() < area.height() * .15f }
        .maxByOrNull { it.height() * it.height() * (area.right - it.right) }

    /** The keep-out rect most like the keyboard drawing: the widest in the bottom half. */
    private fun floorLike(): RectF? = keepOut.filter { it.centerY() > area.centerY() }.maxByOrNull { it.width() * 2 + it.top / 1000f }

    /**
     * Takes the decoration away round every keep-out, softly: nothing inside one or its [CLEAR_DP], then it comes back over
     * [FEATHER_DP], so a shape that crosses a box fades out before it rather than stopping at a hard edge (Master, 2026-11-13: the
     * clown's rays showed black boxes round the title, the chip and the field). The top edge, under the status bar, fades too.
     */
    private fun featherOut() {
        val clear = CLEAR_DP * dp
        val step = FEATHER_DP * dp / FEATHER_STEPS
        for (r in keepOut) {
            eraser.alpha = 255
            canvas.drawRect(r.left - clear, r.top - clear, r.right + clear, r.bottom + clear, eraser)
            // Ring k takes away 1/(k + 1) of what's left, so what remains climbs evenly from the box out to the full strength.
            for (k in 1..FEATHER_STEPS) {
                val grow = clear + k * step
                eraser.alpha = (255f / (k + 1)).roundToInt()
                canvas.drawRoundRect(r.left - grow, r.top - grow, r.right + grow, r.bottom + grow, k * step, k * step, eraser)
            }
        }
        for (k in 0..FEATHER_STEPS) {
            eraser.alpha = (255f / (k + 1)).roundToInt()
            canvas.drawRect(area.left, area.top - area.height(), area.right, area.top + k * step, eraser)
        }
    }

    // Drawing

    private fun strokes(mark: Array<FloatArray>, fraction: Float) {
        var left = fraction * mark.sumOf { length(it).toDouble() }.toFloat()
        path.reset()
        for (s in mark) {
            if (left <= 0f) break
            path.moveTo(s[0], s[1])
            var i = 2
            while (i < s.size && left > 0f) {
                val dx = s[i] - s[i - 2]
                val dy = s[i + 1] - s[i - 1]
                val len = hypot(dx, dy)
                if (len <= left) path.lineTo(s[i], s[i + 1]) else path.lineTo(s[i - 2] + dx * left / len, s[i - 1] + dy * left / len)
                left -= len
                i += 2
            }
        }
        canvas.drawPath(path, paint)
    }

    private fun stroke(alpha: Float, width: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        tone(alpha)
    }

    private fun fill(alpha: Float) {
        paint.style = Paint.Style.FILL
        tone(alpha)
    }

    private fun tone(alpha: Float) {
        paint.color = colour and 0x00FFFFFF or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)
    }

    companion object {
        /** The id for the end's grass, which GuideSteps calls "end". */
        const val FINALE = "finale"
        /** The id for slide zero, the phone's settings. */
        const val SETUP = "setup"
        const val MAX_SHAPES = 12
        /** How far every shape keeps from a keep-out rect. */
        const val CLEAR_DP = 8f
        /** How far past [CLEAR_DP] the decoration fades back in round a keep-out, and in how many rings, in dp. */
        const val FEATHER_DP = 16f
        const val FEATHER_STEPS = 8
        const val CREAM = 0xFFFFF1E6.toInt()
        const val INK = 0xFF160A04.toInt()
        const val INK_LEVEL = .08f
        /** The grass's front layer, its strongest ink (owner, 2026-11-13): the finale's only shapes stronger than [INK_LEVEL]. */
        const val GRASS_STRONGEST = .45f

        private const val TAU = (2 * PI).toFloat()

        /**
         * Each slide's colour and how strong its whole layer is: the accent at 14–24% or cream at 10–14% (§7), tuned per slide so each
         * reads at a glance on the phone, still behind everything (owner, 2026-11-13: at 6–12% it mostly vanished). The accent is orange,
         * or cream with Orange accents off ([Accent]).
         */
        class Look(val accent: Boolean, val level: Float, val loop: Long, val still: Long) {
            val colour: Int get() = if (accent) Accent.colour else CREAM
        }

        val LOOKS = mapOf(
            "type" to Look(true, .24f, 18_000, 4_400),
            "swipe" to Look(true, .2f, 19_500, 1_500),
            "menu" to Look(true, .2f, 12_000, 2_000),
            "backspace" to Look(false, .12f, 7_000, 3_000),
            "flick" to Look(true, .24f, 6_000, 900),
            "hold" to Look(true, .22f, 16_000, 900),
            "steer" to Look(true, .24f, 6_000, 1_000),
            "selectmore" to Look(true, .2f, 6_000, 2_700),
            "alt" to Look(true, .2f, 20_000, 3_000),
            "holdsym" to Look(true, .18f, 20_000, 1_000),
            "symmain" to Look(true, .24f, 10_000, 2_000),
            "symgroups" to Look(true, .18f, 10_000, 3_000),
            "clown" to Look(true, .16f, 20_000, 2_000),
            "paste" to Look(true, .18f, 20_000, 2_500),
            "clipboard" to Look(true, .18f, 20_000, 3_000),
            FINALE to Look(true, .2f, 8_000, 6_000),
            SETUP to Look(true, .16f, 20_000, 0),
        )

        /** How long [slide]'s picture takes to come round again: after the grass has grown, for the finale. 0 for no slide. */
        fun loopMillis(slide: String) = LOOKS[slide]?.loop ?: 0L

        /** The moment to show as a still picture, with Android's animations off. */
        fun stillMillis(slide: String) = LOOKS[slide]?.still ?: 0L

        /** [millis] folded into [slide]'s loop, so every loop is exactly the same. The grass grows once, then only sways. */
        fun loopTime(slide: String, millis: Long): Float {
            val loop = loopMillis(slide)
            if (loop <= 0L) return 0f
            if (slide == FINALE) {
                val settled = GRASS_SETTLED.toLong()
                return if (millis < settled) millis.coerceAtLeast(0L).toFloat() else (settled + Math.floorMod(millis - settled, loop)).toFloat()
            }
            return Math.floorMod(millis, loop).toFloat()
        }

        private const val MARK_EVERY = 3000f
        private const val MARK_DRAW = 1400f
        private const val MARK_LINGER = 4800f
        private const val MARK_LIFE = 6400f
        private const val SWIPE_EVERY = 6500f
        private const val SWIPE_SWEEP = 3400f
        private const val SWIPE_TAIL = .5f
        private const val TYPEWRITER_LOOP = 7000f
        private const val BALL_LOOP = 6000f
        private const val BALL_FLIGHT = 2000f
        private const val SHRED_STEP = 2000f
        private const val SHRED_PITCH = 86f
        private const val SHRED_STRIPS = 5
        private const val SHRED_LETTERS = "TKAWSLOE"
        private const val DRIVE_LOOP = 6000f
        private const val DASHES = 5
        private val BEATS = floatArrayOf(.3f, .5f)
        private const val ECG_SCALE = 1.3f
        private const val SNOW_LOOP = 20000f
        private const val BUOY_LOOP = 20000f
        private const val PEEK_LOOP = 10000f
        private const val FOREVER_LOOP = 10000f
        private const val TENT_LOOP = 20000f
        private const val JUGGLE_LOOP = 5000f
        private const val NOTE_LOOP = 20000f
        private const val NOTE_FLAP = 5000f
        private const val CLIPS_LOOP = 20000f
        private const val CLIP_LENGTH = 44f
        private const val GRASS_GROW = 2600f
        /** How much later than the first a blade may start to grow, so the band comes up unevenly, as grass does. */
        private const val GRASS_STAGGER = 700f
        /** The tallest the grass grows, a flower's head and petals included, in dp: it fits the end's grass room, 150 dp, less [CLEAR_DP]. */
        private const val GRASS_TALLEST = 142f
        /**
         * The longest blade, in dp; the flowers stand a little over it. Both stay under the soft edge round what's above (the
         * finale's words end [CLEAR_DP] + [FEATHER_DP] higher), so no tip fades.
         */
        private const val BLADE_DP = 104f
        private const val GRASS_SETTLED = 4500f
        private const val GRASS_LOOP = 8000f
        /** The three layers, back to front: the ink's strength on the orange, the blades' spacing in dp, and their heights as parts of [BLADE_DP]. */
        private val GRASS_TONES = floatArrayOf(.18f, .30f, GRASS_STRONGEST)
        private val GRASS_PITCH = floatArrayOf(7f, 8f, 9f)
        private val GRASS_HEIGHTS = arrayOf(.55f to 1f, .38f to .78f, .22f to .52f)
        /** Where the flowers stand, across the screen. */
        private val FLOWERS = floatArrayOf(.18f, .56f, .84f)
        private const val GEAR_LOOP = 20000f
        private const val GEAR_MODULE = 7f
        private const val BIG_TEETH = 14
        private const val SMALL_TEETH = 9

        /** The three swipes, each a cubic Bezier's x0, y0 … x3, y3: x across the area, 0 to 1, and y across the open band, -1 to 1. */
        private val SWIPES = arrayOf(
            floatArrayOf(-.1f, -.7f, .3f, 2.9f, .66f, -2.9f, 1.1f, .6f),
            floatArrayOf(1.1f, -.5f, .72f, 2.8f, .28f, -2.8f, -.1f, .8f),
            floatArrayOf(-.1f, .7f, .32f, -3f, .68f, 3f, 1.1f, -.6f),
        )

        /** The proofreader's marks in dp, each a list of strokes as x, y pairs. */
        private val MARKS: Array<Array<FloatArray>> = arrayOf(
            arrayOf(FloatArray(86) { i -> val x = -42f + i / 2 * 2f; if (i % 2 == 0) x else 4.5f * sin(x / 42f * PI.toFloat() * 3.5f) }),
            arrayOf(floatArrayOf(-12f, 10f, -1f, -10f, 1f, -10.5f, 12f, 10f)),
            arrayOf(FloatArray(100) { i ->
                val a = (200f + 385f * (i / 2) / 49f) * PI.toFloat() / 180f
                val wobble = 1f + .06f * sin(3f * a)
                if (i % 2 == 0) 25f * cos(a) * wobble else 17f * sin(a) * wobble
            }),
            arrayOf(
                smoothPoints(floatArrayOf(-16f, -9f, -19f, -11f, -23f, -10f, -23f, -6f, -17f, -4f, -16f, 0f, -19f, 2f, -24f, 1f), 1.5f),
                smoothPoints(floatArrayOf(-11f, -10f, -11f, 9f), 1.5f),
                smoothPoints(floatArrayOf(-11f, -7f, -7f, -11f, -2f, -10f, -1f, -5f, -4f, -1f, -11f, -2f), 1.5f),
                smoothPoints(floatArrayOf(5f, -11f, 8f, -14f, 13f, -14f, 15f, -10f, 13f, -6f, 9f, -4f, 9f, 0f), 1.5f),
                smoothPoints(floatArrayOf(9f, 4f, 9.3f, 4.6f), 1.5f),
            ),
            arrayOf(
                floatArrayOf(-34f, 0f, 22f, 0f),
                smoothPoints(floatArrayOf(22f, 0f, 30f, -6f, 34f, -14f, 29f, -17f, 26f, -11f, 31f, -3f, 38f, 3f), 1f),
            ),
            arrayOf(
                FloatArray(40) { i -> val x = -38f + i / 2 * 4f; if (i % 2 == 0) x else .8f * sin(x / 9f) },
                FloatArray(36) { i -> val x = -34f + i / 2 * 4f; if (i % 2 == 0) x else 6.5f + .8f * sin(x / 8f + 1f) },
            ),
        )

        /** A smooth curve through [points] (Catmull-Rom), scaled by [scale], as x, y pairs. */
        private fun smoothPoints(points: FloatArray, scale: Float): FloatArray {
            val n = points.size / 2
            if (n < 3) return FloatArray(points.size) { points[it] * scale }
            val steps = 8
            val out = FloatArray(((n - 1) * steps + 1) * 2)
            var o = 0
            for (i in 0 until n - 1) {
                val p0 = max(i - 1, 0)
                val p3 = min(i + 2, n - 1)
                for (s in 0 until steps) {
                    val u = s.toFloat() / steps
                    for (axis in 0..1) {
                        val a = points[p0 * 2 + axis]
                        val b = points[i * 2 + axis]
                        val c = points[(i + 1) * 2 + axis]
                        val d = points[p3 * 2 + axis]
                        out[o++] = scale * .5f * (2 * b + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u * u + (-a + 3 * b - 3 * c + d) * u * u * u)
                    }
                }
            }
            out[o++] = points[(n - 1) * 2] * scale
            out[o] = points[(n - 1) * 2 + 1] * scale
            return out
        }

        private fun length(s: FloatArray): Float {
            var total = 0f
            var i = 2
            while (i < s.size) {
                total += hypot(s[i] - s[i - 2], s[i + 1] - s[i - 1])
                i += 2
            }
            return total
        }

        private fun bezier(a: Float, b: Float, c: Float, d: Float, s: Float): Float {
            val r = 1f - s
            return r * r * r * a + 3 * r * r * s * b + 3 * r * s * s * c + s * s * s * d
        }

        /** A number from 0 to 1 for [seed] and [i], the same every time. */
        fun hash(seed: Int, i: Int): Float {
            var x = seed * -0x61c88647 + i * -0x7a143595
            x = x xor (x ushr 16)
            x *= 0x7feb352d
            x = x xor (x ushr 15)
            x *= -0x7b935975
            x = x xor (x ushr 16)
            return (x ushr 8) / 16777216f
        }

        private fun frac(x: Float) = x - floor(x)
        private fun smooth(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        private fun sine(x: Float): Float = (1f - cos(PI.toFloat() * x.coerceIn(0f, 1f))) / 2f
        private fun bump(x: Float): Float = if (x <= 0f || x >= 1f) 0f else sin(PI.toFloat() * x)
        private fun backOut(x: Float): Float {
            val u = x.coerceIn(0f, 1f) - 1f
            return 1f + u * u * (2.2f * u + 1.2f)
        }

    }
}
