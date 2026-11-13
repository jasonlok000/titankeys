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
import kotlin.math.asin
import kotlin.math.atan
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
 * The guide's slide decorations (docs/design-v2.md §7): a picture behind each slide that only follows its theme. It never reacts
 * to typing, a mistake, a hint or a pass, and never tells you anything.
 *
 * A pure renderer. What it draws depends only on its arguments: the slide's id, how long the slide has shown ([draw]'s millis),
 * the area, the keep-out rects, the pass tone and the dp. Each slide has its own seeded randomness, and every loop is a pure
 * function of millis, so any frame can be drawn again, in any order. What it keeps between calls (paths, the free spots and open
 * bands it found for an area) is only a cache that it rebuilds when anything changes.
 *
 * The rules, from §7, as reviewed on the phone (round 2, 2026-11-13; at 6–12% they vanished, the owner: "too subtle"):
 * - The accent at 36–46%, the typewriter's layer at 40% (its e cream at about 20%), and the grass in ink at 24, 40 and 60% on the
 *   end's accent. Each picture is laid down as one layer, so overlaps never add up. Ink at 8% on the pass screen ([draw]'s
 *   onOrange). With Orange accents off, the accent is cream at the orange's lightness ([accentColour]), so nothing turns brighter.
 * - At most [MAX_SHAPES] shapes at once, slow loops of 6–20 s ([loopMillis]).
 * - Nothing inside a keep-out rect or within [CLEAR_DP] of one, then a soft edge of [FEATHER_DP]: a shape that passes behind the
 *   title, the instruction, the field or the keyboard drawing goes out of sight there.
 * - Paths and Inter glyphs only, no emoji images.
 *
 * Most pictures live in the open bands across the screen ([gaps]): the one between the field and the keys ([lower]) and the one
 * between the top bar and the title ([upper]).
 *
 * Slide ids are GuideSteps' ids (type, swipe, menu, backspace, flick, hold, steer, selectmore, alt, holdsym, symmain, symgroups,
 * clown, paste, clipboard), plus [FINALE] for the end's grass and [SETUP] for slide zero. Any other id draws nothing.
 *
 * [GuideDecorView] hosts it with the timing, fades and frame rate. How the guide adds it is in that view's KDoc. The designs were
 * drawn and reviewed in a phone preview first, whose renderer is a line-for-line copy of this file in JavaScript.
 */
class GuideDecor(private val font: Typeface? = null) {
    /** Takes away what's under a shape: the soft keep-outs, cut-outs, and flat layers that never add up. */
    private val eraser = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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
    private val gearLines = HashMap<Int, Path>()
    private val gearBodies = HashMap<Int, Path>()
    private val spotCache = LinkedHashMap<Long, FloatArray>()
    private val polyline = FloatArray(800)
    private val pen = FloatArray(2)

    private lateinit var canvas: Canvas
    private val area = RectF()
    private var keepOut: List<RectF> = emptyList()
    private var dp = 1f
    private var colour = 0
    private var accent = 0
    private var shapes = 0

    /**
     * Draws [slide]'s decoration as it is [millis] after the slide settled, inside [area] and outside [keepOut] (each with
     * [CLEAR_DP] around it), in the pass screen's ink when [onOrange]. [fade] runs from 0 to 1 for the fades in and between slides.
     * Returns how many shapes it drew, never more than [MAX_SHAPES].
     */
    fun draw(canvas: Canvas, slide: String, millis: Long, area: RectF, keepOut: List<RectF>, onOrange: Boolean, dp: Float,
             fade: Float = 1f): Int {
        val look = LOOKS[slide] ?: return 0
        // The grass is always on the end's accent, in ink at its own strengths; the slides turn ink at 8% on the pass screen.
        val level = if (slide == FINALE || !onOrange) look.level else INK_LEVEL
        val alpha = (level * fade.coerceIn(0f, 1f) * 255).roundToInt()
        if (alpha <= 0 || area.width() <= 0f || area.height() <= 0f) return 0
        this.canvas = canvas
        this.area.set(area.left, area.top, area.right, area.bottom)
        this.keepOut = keepOut
        this.dp = dp
        colour = if (onOrange) INK else look.colour
        accent = if (onOrange) INK else accentColour()
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

    /** Red pen: proofreader's marks draw themselves behind a fountain pen's nib, linger, fade, and the next starts somewhere else. */
    private fun redPen(t: Float) {
        val spots = spots(1, 42f, MARKS.size)
        val first = floor(t / MARK_EVERY).toInt()
        for (n in first - 2..first) {
            val age = t - n * MARK_EVERY
            if (age < 0f || age >= MARK_LIFE) continue
            val kind = Math.floorMod(n, MARKS.size)
            if (spots.size < kind * 2 + 2) continue
            val reveal = sine(age / MARK_DRAW)
            val fadeOut = 1f - smooth((age - MARK_LINGER) / (MARK_LIFE - MARK_LINGER))
            val k = 2.25f * dp
            // Each mark stays on the screen by its own half width.
            val reach = MARK_HALF[kind] * k + 10f * dp
            val x = min(area.right - reach, max(area.left + reach, spots[kind * 2]))
            canvas.save()
            canvas.translate(x, spots[kind * 2 + 1])
            canvas.rotate((hash(11, kind) - .5f) * 14f)
            canvas.scale(k, k)
            stroke(fadeOut, 1.5f)
            val drawn = strokes(MARKS[kind], reveal)
            // The circled letter: the letter is already there; the pen draws round it.
            if (kind == 2) {
                fill(fadeOut * .85f)
                paint.textSize = 19f
                canvas.drawText("a", 0f, 6.5f, paint)
            }
            val nib = 1f - smooth((age - MARK_DRAW) / 500f)
            if (drawn && nib > 0f) nib(pen[0], pen[1], fadeOut * nib)
            canvas.restore()
            shapes++
        }
    }

    /** A fountain pen's nib with its tip on ([x], [y]), held as a right hand holds it, in the mark's units. */
    private fun nib(x: Float, y: Float, a: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(32f)
        path.reset()
        path.moveTo(0f, 0f)
        path.lineTo(-3.4f, -7f)
        path.quadTo(-4.4f, -12f, -3.2f, -15f)
        path.lineTo(3.2f, -15f)
        path.quadTo(4.4f, -12f, 3.4f, -7f)
        path.close()
        fill(a)
        canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(-4.4f, -16.5f)
        path.lineTo(4.4f, -16.5f)
        path.lineTo(4.8f, -30f)
        path.quadTo(0f, -33f, -4.8f, -30f)
        path.close()
        fill(a * .55f)
        canvas.drawPath(path, paint)
        // Its slit and breather hole, cut out.
        path.reset()
        path.moveTo(0f, -1.6f)
        path.lineTo(0f, -8.4f)
        erase(1f, .9f)
        path.reset()
        path.addCircle(0f, -9.6f, 1.3f, Path.Direction.CW)
        erase()
        canvas.restore()
    }

    /** Air swipes: one long soft S-curve at a time sweeps across with a tapering tail and a glow, like a swipe's trail, then runs off. */
    private fun airSwipes(t: Float) {
        val slot = floor(t / SWIPE_EVERY).toInt()
        val c = SWIPES[Math.floorMod(slot, SWIPES.size)]
        val u = (t - slot * SWIPE_EVERY) / SWIPE_SWEEP
        if (u >= 1f) return
        val g = lower()
        val mid = (g[0] + g[1]) / 2
        val swing = max((g[1] - g[0]) * .4f, 36f * dp)
        val head = sine(u) * (1f + SWIPE_TAIL)
        val tail = head - SWIPE_TAIL
        val from = max(tail, 0f)
        val to = min(head, 1f)
        if (to <= from) return
        val w = area.width()
        val n = 64
        for (i in 0..n) {
            val s = from + (to - from) * i / n
            polyline[i * 2] = area.left + bezier(c[0], c[2], c[4], c[6], s) * w
            polyline[i * 2 + 1] = mid + bezier(c[1], c[3], c[5], c[7], s) * swing
        }
        // The glow, then the trail itself: out along one side of the curve and back along the other, widening to the head.
        for ((grow, a) in GLOW) {
            path.reset()
            for (side in 0..1) {
                for (k in 0..n) {
                    val i = if (side == 0) k else n - k
                    val s = from + (to - from) * i / n
                    val along = ((s - tail) / SWIPE_TAIL).coerceIn(0f, 1f)
                    val half = (.4f + 5.6f * along.pow(1.6f)) * dp * grow
                    val pa = max(i - 1, 0)
                    val pb = min(i + 1, n)
                    val dx = polyline[pb * 2] - polyline[pa * 2]
                    val dy = polyline[pb * 2 + 1] - polyline[pa * 2 + 1]
                    val len = max(hypot(dx, dy), .001f)
                    val sign = if (side == 0) 1f else -1f
                    val px = polyline[i * 2] - dy / len * half * sign
                    val py = polyline[i * 2 + 1] + dx / len * half * sign
                    if (k == 0 && side == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
            }
            path.close()
            if (head <= 1f) path.addCircle(polyline[n * 2], polyline[n * 2 + 1], 6.4f * dp * (if (grow == 1f) 1f else 2.1f), Path.Direction.CW)
            fill(a)
            canvas.drawPath(path, paint)
        }
        shapes += if (head <= 1f) 3 else 2
    }

    /** Starfield: four-point sparkles twinkle in the open space, and now and then a shooting star crosses a band. */
    private fun starfield(t: Float) {
        val fixed = spots(3, 18f, 8)
        for (i in 0 until fixed.size / 2) {
            val period = if (i % 2 == 0) 6000f else 12000f
            val pulse = (.5f + .5f * sin(TAU * (t / period + hash(31, i)))).pow(2f)
            val r = (12f + 16f * hash(32, i)) * (.5f + .5f * pulse)
            star(fixed[i * 2], fixed[i * 2 + 1], r, 15f * sin(TAU * (t / 12000f + hash(33, i))), .3f + .7f * pulse)
        }
        val bands = arrayOf(upper(), lower())
        for (k in 0..1) {
            val u = (t - k * 6000f - 1800f) / SHOOT
            val g = bands[k]
            if (u < 0f || u >= 1f || g == null) continue
            val x0 = area.left + area.width() * (if (k == 0) .1f else .5f)
            val x1 = x0 + area.width() * .38f
            val y0 = g[0] + (g[1] - g[0]) * .3f
            val y1 = g[0] + (g[1] - g[0]) * .7f
            val p = 1f - (1f - u).pow(2f)
            val hx = x0 + (x1 - x0) * p
            val hy = y0 + (y1 - y0) * p
            val len = hypot(x1 - x0, y1 - y0)
            val ux = (x1 - x0) / len
            val uy = (y1 - y0) / len
            val tailLength = 150f * dp * (1f - .6f * u) * smooth(u / .15f)
            val w = 3.2f * dp
            path.reset()
            path.moveTo(hx - ux * tailLength, hy - uy * tailLength)
            path.lineTo(hx - uy * w, hy + ux * w)
            path.lineTo(hx + uy * w, hy - ux * w)
            path.close()
            val fade = 1f - smooth((u - .7f) / .3f)
            fill(.7f * fade)
            canvas.drawPath(path, paint)
            shapes++
            star(hx, hy, 11f, u * 120f, fade)
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

    /** Typewriter e: a huge cream e with a letterpress shadow, and every few seconds a strip of correction tape rolls over it. */
    private fun typewriter(t: Float) {
        val g = lower()
        val size = max(min((g[1] - g[0] - 5f * dp) / .56f * .94f, 240f * dp), 40f * dp)
        val cx = area.left + area.width() * .66f
        val cy = (g[0] + g[1]) / 2 - 2.5f * dp
        val baseline = cy + .27f * size
        val u = t / TYPEWRITER_LOOP
        val roll = sine((u - .15f) / .18f)
        val peel = smooth((u - .55f) / .15f)
        val tape = roll * (1f - peel)
        paint.textSize = size
        // The type's shoulder: its outline set off a little, then the face.
        stroke(.36f, 2.6f * dp)
        canvas.drawText("e", cx + 6f * dp, baseline + 6f * dp, paint)
        fill(.5f)
        canvas.drawText("e", cx, baseline, paint)
        shapes += 2
        if (tape <= .01f) return
        val left = cx - .46f * size
        val right = left + .92f * size * roll
        val tapeTop = cy - .05f * size
        val tapeBottom = tapeTop + .17f * size
        canvas.save()
        canvas.translate(0f, -peel * .12f * size)
        canvas.rotate(-4f - 6f * peel, cx, cy)
        path.reset()
        path.moveTo(left, tapeTop)
        path.lineTo(right, tapeTop)
        // The tape's torn end, while it rolls out.
        val teeth = 6
        for (k in 1..teeth) path.lineTo(right + if (k % 2 == 1) -4f * dp * roll else 0f, tapeTop + (tapeBottom - tapeTop) * k / teeth)
        path.lineTo(left, tapeBottom)
        path.close()
        erase(tape)
        fill(tape, accent)
        canvas.drawPath(path, paint)
        // Its sheen.
        path.reset()
        path.moveTo(left + 8f * dp, tapeTop + 4f * dp)
        path.lineTo(max(left + 8f * dp, right - 14f * dp), tapeTop + 4f * dp)
        erase(.35f * tape, 2f * dp)
        canvas.restore()
        shapes++
    }

    /** Paper balls: now and then a crumpled ball arcs in from the right and drops into a wire bin on the left, which wobbles. */
    private fun paperBalls(t: Float) {
        val g = lower()
        val floor = g[1] - 12f * dp
        val binWide = 60f * dp
        val binTall = 70f * dp
        val binX = area.left + 74f * dp
        val binTop = floor - binTall
        var wobble = 0f
        for (i in 0..1) {
            val since = wrap(t - (i * BALL_LOOP / 2 + BALL_FLIGHT), BALL_LOOP)
            wobble += (if (i == 0) 7f else -5f) * exp(-since / 420f) * sin(TAU * since / 520f)
        }
        for (i in 0..1) {
            val at = t - i * BALL_LOOP / 2
            val u = at / BALL_FLIGHT
            if (u < 0f || at >= BALL_FLIGHT + BALL_DROP) continue
            val r = (if (i == 0) 24f else 19f) * dp
            val x0 = area.right + r + 6f * dp
            val y0 = g[0] + (g[1] - g[0]) * (if (i == 0) .5f else .62f)
            val x1 = binX
            val y1 = binTop - r * .1f
            val peak = g[0] + r + 6f * dp
            val controlY = 2 * peak - (y0 + y1) / 2
            val controlX = x0 + (x1 - x0) * .45f
            val x: Float
            val y: Float
            var alpha = 1f
            if (u < 1f) {
                val q = 1f - u
                x = q * q * x0 + 2 * q * u * controlX + u * u * x1
                y = q * q * y0 + 2 * q * u * controlY + u * u * y1
                // Two speed lines behind it, along its flight.
                val vx = 2 * q * (controlX - x0) + 2 * u * (x1 - controlX)
                val vy = 2 * q * (controlY - y0) + 2 * u * (y1 - controlY)
                val len = max(hypot(vx, vy), .001f)
                val ux = vx / len
                val uy = vy / len
                path.reset()
                for (k in 0..1) {
                    val off = (k * 2 - 1) * r * .42f
                    val sx = x - ux * r * 1.5f - uy * off
                    val sy = y - uy * r * 1.5f + ux * off
                    path.moveTo(sx, sy)
                    path.lineTo(sx - ux * r * (1.1f + k * .4f), sy - uy * r * (1.1f + k * .4f))
                }
                stroke(.55f * (1f - u * .5f), 2.4f * dp)
                canvas.drawPath(path, paint)
                shapes++
            } else {
                val d = (at - BALL_FLIGHT) / BALL_DROP
                x = x1
                y = y1 + d * d * binTall * .7f
                alpha = 1f - smooth(d)
            }
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(-520f * min(u, 1.2f) + i * 70f)
            crumple(r, i)
            fill(.42f * alpha)
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
            stroke(alpha, 2.2f * dp)
            canvas.drawPath(path, paint)
            canvas.restore()
            shapes++
        }
        // The bin: wire sides and a rim, standing on the floor of the open band.
        canvas.save()
        canvas.rotate(wobble, binX, floor)
        val bottomWide = binWide * .78f
        path.reset()
        path.moveTo(binX - binWide / 2, binTop)
        path.lineTo(binX - bottomWide / 2, floor)
        path.lineTo(binX + bottomWide / 2, floor)
        path.lineTo(binX + binWide / 2, binTop)
        path.close()
        fill(.16f)
        canvas.drawPath(path, paint)
        stroke(1f, 2.6f * dp)
        canvas.drawPath(path, paint)
        path.reset()
        for (k in 1..4) {
            val s = k / 5f
            path.moveTo(binX - binWide / 2 + binWide * s, binTop + 5f * dp)
            path.lineTo(binX - bottomWide / 2 + bottomWide * s, floor)
        }
        stroke(.55f, 1.8f * dp)
        canvas.drawPath(path, paint)
        stroke(1f, 4.2f * dp)
        canvas.drawLine(binX - binWide / 2 - 4f * dp, binTop, binX + binWide / 2 + 4f * dp, binTop, paint)
        canvas.restore()
        shapes++
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

    /** Shredder: letter tiles ride a belt into a shredder on the left, and come out underneath as strips that fall away. */
    private fun shredder(t: Float) {
        val g = lower()
        val tile = 40f * dp
        val y = (g[0] + g[1]) / 2 - 10f * dp
        val pitch = SHRED_PITCH * dp
        val mouth = area.left + 86f * dp
        val shift = t / SHRED_STEP * pitch
        // The belt's two rails.
        path.reset()
        for (rail in floatArrayOf(y - tile / 2 - 7f * dp, y + tile / 2 + 7f * dp)) {
            path.moveTo(mouth, rail)
            path.lineTo(area.right, rail)
        }
        stroke(.55f, 2f * dp)
        canvas.drawPath(path, paint)
        shapes++
        val boxTop = y - tile / 2 - 24f * dp
        val boxBottom = y + tile / 2 + 18f * dp
        val firstSlot = floor((shift - (area.width() + 2 * pitch)) / pitch).toInt()
        var shredding = 0
        for (slot in firstSlot..firstSlot + 12) {
            val x = area.right + pitch + slot * pitch - shift
            if (x > area.right + tile) continue
            if (mouth - x >= tile) {
                if ((mouth - x - tile) / (pitch * 1.2f) < 1f) shredding = shredding or (1 shl (slot - firstSlot))
                continue
            }
            tile(x, y, tile, SHRED_LETTERS[Math.floorMod(slot, SHRED_LETTERS.length)].toString())
            shapes++
        }
        // The machine, over the tiles going in.
        path.reset()
        roundRect(area.left - 20f * dp, boxTop, mouth + 4f * dp, boxBottom, 10f * dp)
        erase()
        fill(.2f)
        canvas.drawPath(path, paint)
        stroke(1f, 2.6f * dp)
        canvas.drawPath(path, paint)
        // Its mouth, with teeth.
        val teeth = 6
        val mouthTop = y - tile / 2 - 4f * dp
        val mouthBottom = y + tile / 2 + 4f * dp
        path.reset()
        path.moveTo(mouth + 4f * dp, mouthTop)
        for (k in 1..teeth * 2) path.lineTo(mouth + 4f * dp + (if (k % 2 == 1) 5f * dp else 0f), mouthTop + (mouthBottom - mouthTop) * k / (teeth * 2))
        canvas.drawPath(path, paint)
        // Its light, which blinks as a tile goes in.
        fill(.35f + .65f * bump(wrap(t, SHRED_STEP) / 500f))
        canvas.drawCircle(mouth - 18f * dp, boxTop + 12f * dp, 3.6f * dp, paint)
        shapes++
        // The strips, out of the bottom.
        if (shredding == 0) return
        paint.strokeCap = Paint.Cap.BUTT
        for (n in 0..12) {
            if (shredding and (1 shl n) == 0) continue
            val slot = firstSlot + n
            val x = area.right + pitch + slot * pitch - shift
            val u = (mouth - x - tile) / (pitch * 1.2f)
            path.reset()
            for (k in 0 until SHRED_STRIPS) {
                val sx = mouth - 22f * dp - tile + (k + .5f) * tile / SHRED_STRIPS * 1.05f + (hash(51, k + slot) - .5f) * 6f * dp
                val sy = boxBottom + 2f * dp + u * (26f + 18f * hash(50, k + slot)) * dp
                val length = tile * (.5f + .4f * hash(52, k + slot)) * (1f - .3f * u)
                path.moveTo(sx, sy)
                path.lineTo(sx + (hash(53, k + slot) - .5f) * 8f * dp, sy + length)
            }
            stroke(.85f * (1f - u).pow(1.2f), max(tile / SHRED_STRIPS - 2.4f * dp, 2f * dp))
            canvas.drawPath(path, paint)
            shapes++
        }
        paint.strokeCap = Paint.Cap.ROUND
    }

    private fun tile(x: Float, y: Float, size: Float, letter: String) {
        path.reset()
        roundRect(x, y - size / 2, x + size, y + size / 2, 7f * dp)
        fill(.16f)
        canvas.drawPath(path, paint)
        stroke(1f, 2.2f * dp)
        canvas.drawPath(path, paint)
        fill(1f)
        paint.textSize = 22f * dp
        canvas.drawText(letter, x + size / 2, y + 8f * dp, paint)
    }

    // Steering

    /**
     * Night drive: a road winds away to the horizon in the open band, bending one way and then the other, its centre dashes and
     * roadside posts streaming towards you, under a moon.
     */
    private fun nightDrive(t: Float) {
        val g = lower()
        val vx = area.centerX()
        val vy = g[0] + 4f * dp
        val bottom = g[1] + (g[1] - vy) * .5f
        val half = .5f * area.width()
        val bend = .2f * area.width() * sin(TAU * t / DRIVE_LOOP)
        // Along the road, s runs from the horizon (0) to under the car (1).
        fun centre(s: Float) = vx + bend * (1f - s) * (1f - s)
        fun y(s: Float) = vy + (bottom - vy) * s
        val n = 24
        path.reset()
        for (i in 0..n) {
            val s = i / n.toFloat()
            if (i == 0) path.moveTo(centre(s) - half * s - 2f * dp, y(s)) else path.lineTo(centre(s) - half * s - 2f * dp, y(s))
        }
        for (i in n downTo 0) {
            val s = i / n.toFloat()
            path.lineTo(centre(s) + half * s + 2f * dp, y(s))
        }
        path.close()
        fill(.13f)
        canvas.drawPath(path, paint)
        path.reset()
        for (side in intArrayOf(-1, 1)) for (i in 0..n) {
            val s = i / n.toFloat()
            val x = centre(s) + side * (half * s + 2f * dp)
            if (i == 0) path.moveTo(x, y(s)) else path.lineTo(x, y(s))
        }
        stroke(.85f, 2.6f * dp)
        canvas.drawPath(path, paint)
        stroke(.45f, 1.8f * dp)
        canvas.drawLine(area.left, vy, area.right, vy, paint)
        shapes += 2
        for (d in 0 until DASHES) {
            val z = 1f - frac(d.toFloat() / DASHES + t / DASH_LOOP)
            val s0 = along(z)
            val s1 = along(max(z - .07f, 0f))
            val fadeIn = smooth(s0 / .1f)
            if (fadeIn <= 0f) continue
            val w0 = (.6f + 7f * s0) * dp
            val w1 = (.6f + 7f * s1) * dp
            path.reset()
            path.moveTo(centre(s0) - w0, y(s0))
            path.lineTo(centre(s0) + w0, y(s0))
            path.lineTo(centre(s1) + w1, y(s1))
            path.lineTo(centre(s1) - w1, y(s1))
            path.close()
            fill(fadeIn)
            canvas.drawPath(path, paint)
            shapes++
        }
        // Roadside posts, two a side, each with its reflector.
        for (p in 0 until 4) {
            val side = if (p % 2 == 0) -1f else 1f
            val z = 1f - frac((p / 2) / 2f + (if (side > 0f) .25f else 0f) + t / DASH_LOOP)
            val s = along(z)
            val fadeIn = smooth(s / .1f)
            if (fadeIn <= 0f) continue
            val px = centre(s) + side * half * s * 1.18f
            val py = y(s)
            val tall = (4f + 60f * s) * dp
            val wide = (.8f + 4.4f * s) * dp
            path.reset()
            roundRect(px - wide, py - tall, px + wide, py, min(wide, 2f * dp))
            fill(.7f * fadeIn)
            canvas.drawPath(path, paint)
            fill(fadeIn)
            canvas.drawRect(px - wide, py - tall, px + wide, py - tall * .78f, paint)
            shapes++
        }
        // The moon, in the band above the title.
        val up = upper() ?: return
        val r = min(20f * dp, (up[1] - up[0]) / 2 - 2f * dp)
        if (r <= 6f * dp) return
        val mx = area.left + area.width() * .8f
        val my = (up[0] + up[1]) / 2
        fill(.95f)
        canvas.drawCircle(mx, my, r, paint)
        path.reset()
        path.addCircle(mx + r * .42f, my - r * .22f, r * .86f, Path.Direction.CW)
        erase()
        shapes++
    }

    /** Where a point [z] of the way back to the horizon appears on the road: perspective, with far things bunched up. */
    private fun along(z: Float): Float {
        val k = 6f
        return (1f / (1f + k * z) - 1f / (1f + k)) / (1f - 1f / (1f + k))
    }

    /** Heartbeat: a bold ECG line traces across, flat, beat, beat, flat, and filled hearts pulse on the beats. */
    private fun heartbeat(t: Float) {
        val g = lower()
        val y = (g[0] + g[1]) / 2 + 16f * dp
        val scale = min((y - g[0] - 10f * dp) / 34f, 2.4f * dp)
        val sweep = 4200f
        val head = area.left + area.width() * (t / sweep)
        val trail = area.width() * .62f
        val from = max(area.left, head - trail)
        val to = min(area.right, head)
        if (to > from) {
            val chunks = 14
            val step = 2f * dp
            paint.strokeCap = Paint.Cap.BUTT
            for (c in 0 until chunks) {
                val a = from + (to - from) * c / chunks
                val b = from + (to - from) * (c + 1) / chunks
                path.reset()
                var x = a
                path.moveTo(x, y - ecg((x - area.left) / dp) * scale)
                while (x < b) {
                    x = min(x + step, b)
                    path.lineTo(x, y - ecg((x - area.left) / dp) * scale)
                }
                stroke(((b - (head - trail)) / trail).coerceIn(0f, 1f).pow(1.3f), 3.2f * dp)
                canvas.drawPath(path, paint)
            }
            paint.strokeCap = Paint.Cap.ROUND
            shapes++
            if (head <= area.right) {
                val hy = y - ecg((head - area.left) / dp) * scale
                fill(.22f)
                canvas.drawCircle(head, hy, 11f * dp, paint)
                fill(1f)
                canvas.drawCircle(head, hy, 4.6f * dp, paint)
                shapes++
            }
        }
        for (b in BEATS.indices) {
            val peakX = area.left + (BEATS[b] * area.width() / dp + 24f) * dp
            val at = (peakX - area.left) / area.width() * sweep
            val since = t - at
            val pulse = if (since < 0f) exp(-(since / 90f).pow(2f)) else exp(-since / 520f)
            val size = 30f * (1f + .3f * pulse) * dp
            canvas.save()
            canvas.translate(peakX + 36f * dp, y - 30f * scale)
            canvas.rotate(if (b == 0) -10f else 8f)
            canvas.scale(size, size)
            fill(.3f + .7f * pulse)
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

    /** Asterisk snow: bold asterisks of many sizes fall slowly, swaying and turning like snowflakes. */
    private fun asteriskSnow(t: Float) {
        val n = 12
        for (i in 0 until n) {
            val period = SNOW_LOOP / (1 + i % 2)
            val u = frac(t / period + hash(60, i))
            val r = (12f + 20f * hash(61, i)) * dp
            val x = area.left + area.width() * (i + .2f + .6f * hash(62, i)) / n + 14f * dp * sin(TAU * (2f * u + hash(63, i)))
            val y = area.top - 30f * dp + (area.height() + 60f * dp) * u
            val turn = 90f * u * (if (i % 2 == 0) 1f else -1f) + 30f * hash(64, i)
            stroke(.55f + .45f * hash(65, i), max(r * .22f, 2.6f * dp))
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(turn)
            path.reset()
            for (k in 0..2) {
                val a = PI.toFloat() * k / 3f
                path.moveTo(-cos(a) * r, -sin(a) * r)
                path.lineTo(cos(a) * r, sin(a) * r)
            }
            canvas.drawPath(path, paint)
            canvas.restore()
            shapes++
        }
    }

    /** Buoys: big question marks bob and tilt on a rolling sea along the open band. */
    private fun buoys(t: Float) {
        val g = lower()
        val sea = g[1] - 26f * dp
        val wavelength = 150f * dp
        val amplitude = 5f * dp
        val period = 5000f
        fun wave(x: Float, lag: Float) = sea + amplitude * sin(TAU * ((x - area.left) / wavelength - t / period + lag))
        for (i in 0..2) {
            val size = BUOY_SIZES[i] * dp
            val x = area.left + area.width() * BUOY_AT[i]
            val y = wave(x, 0f) + 3f * dp * sin(TAU * (t / (BUOY_LOOP / (2 + i % 2)) + hash(70, i)))
            val slope = amplitude * TAU / wavelength * cos(TAU * ((x - area.left) / wavelength - t / period))
            val tilt = atan(slope) * 180f / PI.toFloat() * 2.2f + 6f * sin(TAU * (t / (BUOY_LOOP / 4) + hash(71, i)))
            canvas.save()
            canvas.rotate(tilt, x, y)
            fill(1f)
            paint.textSize = size
            canvas.drawText("?", x, y + size * .04f, paint)
            canvas.restore()
            shapes++
        }
        // The sea: under the front wave everything goes, then the water and its two crests.
        val step = 4f * dp
        path.reset()
        var x = area.left
        path.moveTo(x, wave(x, 0f) + 2f * dp)
        while (x <= area.right) {
            x += step
            path.lineTo(x, wave(x, 0f) + 2f * dp)
        }
        path.lineTo(x, area.bottom)
        path.lineTo(area.left, area.bottom)
        path.close()
        erase()
        fill(.16f)
        canvas.drawPath(path, paint)
        shapes++
        for (crest in 0..1) {
            val lag = if (crest == 0) .35f else 0f
            val drop = if (crest == 0) 12f * dp else 0f
            path.reset()
            x = area.left
            path.moveTo(x, wave(x, lag) + drop)
            while (x <= area.right) {
                x += step
                path.lineTo(x, wave(x, lag) + drop)
            }
            stroke(if (crest == 0) .45f else 1f, (if (crest == 0) 2f else 2.8f) * dp)
            canvas.drawPath(path, paint)
            shapes++
        }
    }

    /** Peekaboo: big ampersands peek out from the screen's edges and from behind the title and the keys, look about, and duck back. */
    private fun peekaboo(t: Float) {
        val size = 96f * dp
        val reach = .62f * size
        val g = lower()
        for (i in 0 until 4) {
            val u = frac(t / PEEK_LOOP - i / 4f) * PEEK_LOOP
            if (u > 4600f) continue
            val out = when {
                u < 900f -> backOut(u / 900f)
                u < 3700f -> 1f
                else -> 1f - sine((u - 3700f) / 900f)
            }
            val look = if (u in 900f..3700f) 7f * sin(TAU * (u - 900f) / 2800f) else 0f
            val x: Float
            val y: Float
            val tilt: Float
            when (i) {
                0 -> { // the right edge, clear of Skip
                    x = area.right + size * .42f - reach * out
                    y = (g[0] + g[1]) / 2 + 14f * dp
                    tilt = -16f
                }
                1 -> { // from behind the end of the title
                    val r = titleLike()
                    x = (r?.right ?: area.centerX()) + CLEAR_DP * dp - size * .42f + (reach + 6f * dp) * out
                    y = r?.centerY() ?: (area.top + area.height() * .25f)
                    tilt = 14f
                }
                2 -> { // the left edge
                    x = area.left - size * .42f + reach * out
                    y = (g[0] + g[1]) / 2 + 6f * dp
                    tilt = 16f
                }
                else -> { // over the top of the keys
                    val r = floorLike()
                    x = area.left + area.width() * .36f
                    y = (r?.top ?: area.bottom) - CLEAR_DP * dp + size * .5f - (reach + 6f * dp) * out
                    tilt = -6f
                }
            }
            canvas.save()
            canvas.rotate(tilt + look, x, y)
            fill(1f)
            paint.textSize = size
            canvas.drawText("&", x, y + size * .36f, paint)
            canvas.restore()
            shapes++
        }
    }

    /** Forever: a glowing dot runs endlessly along a big ∞, leaving a fading trail. */
    private fun forever(t: Float) {
        val g = lower()
        val cy = (g[0] + g[1]) / 2
        val a = min(area.width() * .4f, (g[1] - g[0]) * 1.3f)
        val cx = area.centerX()
        fun x(s: Float) = cx + a * cos(s) / (1f + sin(s) * sin(s))
        fun y(s: Float) = cy + a * sin(s) * cos(s) / (1f + sin(s) * sin(s))
        path.reset()
        for (k in 0..120) {
            val s = TAU * k / 120
            if (k == 0) path.moveTo(x(s), y(s)) else path.lineTo(x(s), y(s))
        }
        path.close()
        stroke(.45f, 3.2f * dp)
        canvas.drawPath(path, paint)
        shapes++
        val head = TAU * t / FOREVER_LOOP
        val chunks = 18
        val length = PI.toFloat() * 1.05f
        for (c in 0 until chunks) {
            path.reset()
            val s0 = head - length + length * c / chunks
            val s1 = head - length + length * (c + 1) / chunks
            for (k in 0..4) {
                val s = s0 + (s1 - s0) * k / 4
                if (k == 0) path.moveTo(x(s), y(s)) else path.lineTo(x(s), y(s))
            }
            val along = (c + 1f) / chunks
            stroke(along.pow(1.5f), (1.4f + 4.6f * along) * dp)
            canvas.drawPath(path, paint)
        }
        shapes++
        fill(.22f)
        canvas.drawCircle(x(head), y(head), 15f * dp, paint)
        fill(1f)
        canvas.drawCircle(x(head), y(head), 6.5f * dp, paint)
        shapes += 2
    }

    /**
     * Big top: a light striped circus valance under the top bar, rippling, with chase lights along its edge (Master: half as deep,
     * about 30%, so it's no second header); and three two-tone balls juggled in the open band on their faintly drawn arcs, each
     * with a fading trail.
     */
    private fun bigTop(t: Float) {
        val top = upper()?.get(0) ?: area.top
        val n = 14
        val wide = area.width() / n
        val depth = 12f * dp
        val drop = 10f * dp
        fun dip(k: Int) = depth * (1f + .1f * sin(TAU * t / 5000f - k * .8f))
        for (even in booleanArrayOf(true, false)) {
            path.reset()
            var k = if (even) 0 else 1
            while (k < n) {
                val x0 = area.left + k * wide
                path.moveTo(x0, top - 40f * dp)
                path.lineTo(x0 + wide, top - 40f * dp)
                path.lineTo(x0 + wide, top + drop)
                path.quadTo(x0 + wide / 2, top + drop + 2 * dip(k), x0, top + drop)
                path.close()
                k += 2
            }
            fill(if (even) .75f else .32f)
            canvas.drawPath(path, paint)
            shapes++
        }
        path.reset()
        for (k in 0 until n) {
            val x0 = area.left + k * wide
            path.moveTo(x0, top + drop)
            path.quadTo(x0 + wide / 2, top + drop + 2 * dip(k), x0 + wide, top + drop)
        }
        stroke(.75f, 1.8f * dp)
        canvas.drawPath(path, paint)
        shapes++
        // Chase lights along the trim: a bulb at each scallop's lowest point and each join, lit in turn.
        val chase = floor(t / CHASE_STEP).toInt()
        for (lit in booleanArrayOf(false, true)) {
            path.reset()
            for (k in 0..2 * n) {
                if ((Math.floorMod(k - chase, 3) == 0) != lit) continue
                val y = top + drop + if (k % 2 == 1) dip((k - 1) / 2) else 0f
                path.addCircle(area.left + k * wide / 2, y, (if (lit) 3.4f else 2.6f) * dp, Path.Direction.CW)
            }
            fill(if (lit) .7f else .3f)
            canvas.drawPath(path, paint)
            shapes++
        }
        // The juggling.
        val g = lower()
        val r = 14f * dp
        val hx = area.left + area.width() * .7f
        val hy = g[1] - r - 10f * dp
        val spread = 48f * dp
        val high = max(min((g[1] - g[0]) - 2 * r - 34f * dp, 96f * dp), 20f * dp)
        fun ballX(leftToRight: Boolean, q: Float) = hx + (if (leftToRight) -spread else spread) * (1f - 2f * q)
        fun ballY(leftToRight: Boolean, q: Float) = hy - 4f * (if (leftToRight) high else high * .7f) * q * (1f - q)
        path.reset()
        for (leftToRight in booleanArrayOf(true, false)) for (k in 0..24) {
            val q = k / 24f
            if (k == 0) path.moveTo(ballX(leftToRight, q), ballY(leftToRight, q)) else path.lineTo(ballX(leftToRight, q), ballY(leftToRight, q))
        }
        stroke(.16f, 2f * dp)
        canvas.drawPath(path, paint)
        shapes++
        val band = asin(.3f) * 180f / PI.toFloat()
        val chord = r * cos(asin(.3f))
        for (b in 0..2) {
            val p = frac(t / JUGGLE_LOOP + b / 3f)
            val leftToRight = p < .5f
            val q = if (leftToRight) p * 2f else (p - .5f) * 2f
            // The trail: three fading copies behind it, along its arc.
            for (k in 3 downTo 1) {
                val back = max(q - k * .075f, 0f)
                fill(.26f - .07f * k)
                canvas.drawCircle(ballX(leftToRight, back), ballY(leftToRight, back), r * (1f - .2f * k), paint)
            }
            canvas.save()
            canvas.translate(ballX(leftToRight, q), ballY(leftToRight, q))
            canvas.rotate(360f * (p * 1.5f + b / 3f))
            path.reset()
            path.addCircle(0f, 0f, r, Path.Direction.CW)
            erase()
            fill(.5f)
            canvas.drawPath(path, paint)
            // Its band, the second tone, between two chords.
            path.reset()
            path.moveTo(-chord, -r * .3f)
            path.lineTo(chord, -r * .3f)
            path.arcTo(-r, -r, r, r, -band, 2 * band, false)
            path.lineTo(-chord, r * .3f)
            path.arcTo(-r, -r, r, r, 180f - band, 2 * band, false)
            path.close()
            fill(1f)
            canvas.drawPath(path, paint)
            canvas.restore()
            shapes++
        }
    }

    // Copy and paste

    /** Sticky notes: notes with scribbles, pinned at angles, sway as if in a breeze, and now and then one flaps a corner. */
    private fun stickyNotes(t: Float) {
        val spots = spots(14, 42f, 4)
        val flapping = Math.floorMod(floor(t / NOTE_FLAP).toInt(), 4)
        val flapU = wrap(t, NOTE_FLAP) / NOTE_FLAP
        for (i in 0 until spots.size / 2) {
            val period = NOTE_LOOP / (2 + i % 2)
            val sway = (hash(80, i) - .5f) * 22f + 3.2f * sin(TAU * (t / period + hash(81, i)))
            val corner = 7f + if (i == flapping) 16f * bump(flapU / .55f) else 0f
            canvas.save()
            canvas.translate(spots[i * 2], spots[i * 2 + 1] - 32f * dp)
            canvas.rotate(sway)
            canvas.scale(dp, dp)
            path.reset()
            path.moveTo(-32f, 4f)
            path.lineTo(32f, 4f)
            path.lineTo(32f, 68f - corner)
            path.lineTo(32f - corner, 68f)
            path.lineTo(-32f, 68f)
            path.close()
            fill(.5f)
            canvas.drawPath(path, paint)
            // The corner, folded over.
            path.reset()
            path.moveTo(32f, 68f - corner)
            path.lineTo(32f - corner, 68f)
            path.lineTo(32f - corner * 1.05f, 68f - corner * 1.05f)
            path.close()
            fill(.95f)
            canvas.drawPath(path, paint)
            // The scribbles, as cut-outs.
            path.reset()
            for (line in 0..2) {
                val y = 26f + 11f * line
                val w = NOTE_LINES[line]
                path.moveTo(-21f, y)
                var x = -21f
                while (x <= -21f + w) {
                    path.lineTo(x, y + 1.6f * sin(x * .5f + i))
                    x += 3f
                }
            }
            erase(.8f, 3f)
            fill(1f)
            canvas.drawCircle(0f, 7f, 5f, paint)
            canvas.restore()
            shapes++
        }
    }

    /** Zero-g clips: bold paper clips float, drifting and turning slowly, as if in zero gravity. */
    private fun zeroG(t: Float) {
        val spots = spots(15, 34f, 5)
        for (i in 0 until spots.size / 2) {
            val x = spots[i * 2] + 24f * dp * sin(TAU * (t / CLIPS_LOOP + hash(90, i)))
            val y = spots[i * 2 + 1] + 14f * dp * sin(TAU * (2f * t / CLIPS_LOOP + hash(91, i)))
            val turn = 360f * (t / CLIPS_LOOP) * (if (i % 2 == 0) 1f else -1f) + 360f * hash(92, i)
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(turn)
            val scale = (1.25f + .45f * hash(93, i)) * dp
            canvas.scale(scale, scale)
            canvas.translate(0f, -CLIP_LENGTH / 2)
            stroke(1f, 2.4f)
            canvas.drawPath(clip, paint)
            canvas.restore()
            shapes++
        }
    }

    // The end and setup

    /**
     * Grass (owner, 2026-11-13, from their picture, "match it to our minimal style"; Master: a real band, about 150 dp): a dense band
     * of pointed blades along the screen's bottom edge, in three overlapping layers for depth, back to front at [GRASS_TONES] of the
     * layer's strength: flat shapes, no outlines, the ink's three strengths on the accent. It grows up from the edge as the finale
     * plays, each blade in its own time, sways gently on a slow wind, and three small flowers open on tall stems. Never taller than the
     * room under the keep-outs, the end's words and buttons above all: in a smaller room, the whole band scales down to fit.
     */
    private fun grass(t: Float) {
        val bottom = area.bottom
        val ceiling = keepOut.filter { it.bottom < bottom - 1f && it.width() > area.width() * .4f }.maxOfOrNull { it.bottom + CLEAR_DP * dp }
        val room = (if (ceiling == null) 1f else (bottom - ceiling) / (GRASS_TALLEST * dp)).coerceIn(0f, 1f)
        if (room <= 0f) return
        // One slow wind across the band, from the start, so the loop folds back without a jump.
        val wind = TAU * t / GRASS_LOOP
        val blade = BLADE_DP * dp * room
        for (layer in 0..2) {
            if (layer == 2) flowers(t, bottom, blade, wind)
            val pitch = GRASS_PITCH[layer] * dp
            val low = GRASS_HEIGHTS[layer * 2]
            val high = GRASS_HEIGHTS[layer * 2 + 1]
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
                val sway = sway(x, h, layer, wind)
                val bend = x + lean * h * .3f + sway * .3f
                path.moveTo(x - w, bottom + 2f * dp)
                path.quadTo(bend - w * .8f, bottom - h * .42f, x + lean * h + sway, bottom - h)
                path.quadTo(bend + w * .8f, bottom - h * .42f, x + w, bottom + 2f * dp)
                path.close()
                drawn = true
            }
            if (!drawn) continue
            flat(GRASS_TONES[layer])
            shapes++
        }
    }

    private fun sway(x: Float, h: Float, layer: Int, wind: Float) =
        (2f * dp + h * .09f) * sin(wind - (x - area.left) / area.width() * 1.6f * TAU + layer * .9f)

    /**
     * Fills [path] at [tone] as a flat shape: what's under it goes first, so where layers overlap the one in front shows its own tone,
     * as in the owner's picture, instead of the inks adding up.
     */
    private fun flat(tone: Float) {
        erase()
        fill(tone)
        canvas.drawPath(path, paint)
    }

    /** Three small flowers in the grass, behind its front blades: a tall stem, then five round petals that open, and their middle. */
    private fun flowers(t: Float, bottom: Float, blade: Float, wind: Float) {
        for (f in 0..2) {
            val open = smooth((t - GRASS_GROW * .75f - f * 450f) / 1100f)
            val stand = smooth((t - GRASS_GROW * .35f - f * 300f) / GRASS_GROW)
            if (stand <= 0f) continue
            val x = area.left + area.width() * (FLOWERS[f] + (hash(160, f) - .5f) * .06f)
            val h = blade * (1f + .03f * f) * stand
            val hx = x + sway(x, h, 1, wind)
            val hy = bottom - h
            val petal = 5.4f * dp
            path.reset()
            path.moveTo(x - dp, bottom + 2f * dp)
            path.quadTo(x + (hx - x) * .4f - .8f * dp, bottom - h * .5f, hx, hy)
            path.quadTo(x + (hx - x) * .4f + .8f * dp, bottom - h * .5f, x + dp, bottom + 2f * dp)
            path.close()
            if (open > 0f) for (k in 0 until 5) {
                val a = TAU * k / 5 + f
                path.addCircle(hx + cos(a) * petal * open, hy + sin(a) * petal * open, petal * .78f * open, Path.Direction.CW)
            }
            flat(GRASS_TONES[1])
            if (open > 0f) {
                path.reset()
                path.addCircle(hx, hy, petal * .56f * open, Path.Direction.CW)
                flat(GRASS_TONES[2])
            }
            shapes++
        }
    }

    /** Gears: gears turn slowly in two corners, each a big one meshed with a small one. */
    private fun gearsIn(t: Float) {
        val turn = 360f * (t / GEAR_LOOP) * 2f / BIG_TEETH
        val corners = arrayOf(
            floatArrayOf(area.right - 30f * dp, area.top + 56f * dp, 135f),
            floatArrayOf(area.left + 30f * dp, area.bottom - 56f * dp, -45f),
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
        val body = gearBodies.getOrPut(teeth) { gearPath(teeth, false) }
        val lines = gearLines.getOrPut(teeth) { gearPath(teeth, true) }
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(degrees)
        canvas.scale(dp, dp)
        // A faint body with its ring open, then the outline, the ring and the hub.
        fill(.14f)
        canvas.drawPath(body, paint)
        eraser.style = Paint.Style.FILL
        eraser.alpha = 255
        canvas.drawCircle(0f, 0f, teeth * GEAR_MODULE / 2 - 1.75f * GEAR_MODULE, eraser)
        canvas.drawCircle(0f, 0f, teeth * GEAR_MODULE / 2 * .25f, paint)
        stroke(1f, 2.4f)
        canvas.drawPath(lines, paint)
        canvas.restore()
        shapes++
    }

    /** A gear's outline in dp with its first tooth on angle 0; with its hub and ring when [rings]. */
    private fun gearPath(teeth: Int, rings: Boolean): Path {
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
        if (rings) {
            shape.addCircle(0f, 0f, r * .25f, Path.Direction.CW)
            shape.addCircle(0f, 0f, root - GEAR_MODULE * 1.1f, Path.Direction.CW)
        }
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
        val xs = (area.width() / step).toInt()
        val ys = (area.height() / step).toInt()
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

    /** The open bands across [area] between the wide keep-out rects, with their margins, top to bottom: top, bottom, top, bottom… */
    private fun gaps(): FloatArray {
        val key = geometryKey(-7L)
        spotCache[key]?.let { return it }
        val clear = CLEAR_DP * dp
        val bands = ArrayList<Float>()
        var y = area.top
        for (r in keepOut.filter { it.width() > area.width() * .4f }.sortedBy { it.top }) {
            if (r.top - clear > y + 1f) {
                bands += y
                bands += r.top - clear
            }
            y = max(y, r.bottom + clear)
        }
        if (area.bottom > y + 1f) {
            bands += y
            bands += area.bottom
        }
        val result = bands.toFloatArray()
        remember(key, result)
        return result
    }

    /** The tallest open band in the lower half: between the field and the keys, on a slide. */
    private fun lower(): FloatArray {
        val mid = area.top + area.height() * .45f
        val all = gaps()
        var best = -1
        for (pass in 0..1) {
            for (i in 0 until all.size / 2) {
                if (pass == 0 && (all[i * 2] + all[i * 2 + 1]) / 2 <= mid) continue
                if (best < 0 || all[i * 2 + 1] - all[i * 2] > all[best * 2 + 1] - all[best * 2]) best = i
            }
            if (best >= 0) break
        }
        return if (best < 0) floatArrayOf(area.top, area.bottom) else floatArrayOf(all[best * 2], all[best * 2 + 1])
    }

    /** The tallest open band in the upper half, over 24 dp: between the top bar and the title, on a slide. */
    private fun upper(): FloatArray? {
        val mid = area.top + area.height() * .45f
        val all = gaps()
        var best = -1
        for (i in 0 until all.size / 2) {
            val h = all[i * 2 + 1] - all[i * 2]
            if ((all[i * 2] + all[i * 2 + 1]) / 2 > mid || h <= 24f * dp) continue
            if (best < 0 || h > all[best * 2 + 1] - all[best * 2]) best = i
        }
        return if (best < 0) null else floatArrayOf(all[best * 2], all[best * 2 + 1])
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
        eraser.style = Paint.Style.FILL
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

    /** Draws [fraction] of [mark]'s strokes, and leaves where the pen is in [pen]. False while nothing is drawn yet. */
    private fun strokes(mark: Array<FloatArray>, fraction: Float): Boolean {
        var left = fraction * mark.sumOf { length(it).toDouble() }.toFloat()
        var drawn = false
        path.reset()
        for (s in mark) {
            if (left <= 0f) break
            path.moveTo(s[0], s[1])
            pen[0] = s[0]
            pen[1] = s[1]
            drawn = true
            var i = 2
            while (i < s.size && left > 0f) {
                val dx = s[i] - s[i - 2]
                val dy = s[i + 1] - s[i - 1]
                val len = hypot(dx, dy)
                if (len <= left) {
                    pen[0] = s[i]
                    pen[1] = s[i + 1]
                } else {
                    pen[0] = s[i - 2] + dx * left / len
                    pen[1] = s[i - 1] + dy * left / len
                }
                path.lineTo(pen[0], pen[1])
                left -= len
                i += 2
            }
        }
        canvas.drawPath(path, paint)
        return drawn
    }

    /** Adds a rounded rect to [path], with quadratic corners. */
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

    /** Takes away what's under [path] by [alpha]: filled, or along it at [width] when that's more than 0. */
    private fun erase(alpha: Float = 1f, width: Float = 0f) {
        eraser.style = if (width > 0f) Paint.Style.STROKE else Paint.Style.FILL
        eraser.strokeWidth = width
        eraser.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        canvas.drawPath(path, eraser)
    }

    private fun stroke(alpha: Float, width: Float, colour: Int = this.colour) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        tone(alpha, colour)
    }

    private fun fill(alpha: Float, colour: Int = this.colour) {
        paint.style = Paint.Style.FILL
        tone(alpha, colour)
    }

    private fun tone(alpha: Float, colour: Int) {
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

        private const val TAU = (2 * PI).toFloat()

        /**
         * The accent as the decorations draw it: orange, or with Orange accents off, cream at the orange's lightness, so a slide's
         * decoration keeps its strength instead of turning 2.6 times brighter.
         */
        fun accentColour(): Int = Accent.warm(Accent.of(true))

        /**
         * Each slide's colour and how strong its whole layer is: the accent at 36–46%, or cream for the typewriter's e, its layer at
         * 40% (round 2, reviewed on the phone, 2026-11-13). The finale's level is its grass's front tone, in ink.
         */
        class Look(val accent: Boolean, val level: Float, val loop: Long, val still: Long) {
            val colour: Int get() = if (accent) accentColour() else CREAM
        }

        val LOOKS = mapOf(
            "type" to Look(true, .45f, 18_000, 4_400),
            "swipe" to Look(true, .42f, 12_000, 1_500),
            "menu" to Look(true, .46f, 12_000, 2_000),
            "backspace" to Look(false, .4f, 7_000, 2_400),
            "flick" to Look(true, .45f, 6_000, 1_100),
            "hold" to Look(true, .36f, 16_000, 900),
            "steer" to Look(true, .4f, 12_000, 2_000),
            "selectmore" to Look(true, .44f, 6_000, 2_700),
            "alt" to Look(true, .44f, 20_000, 3_000),
            "holdsym" to Look(true, .4f, 20_000, 1_000),
            "symmain" to Look(true, .36f, 10_000, 2_000),
            "symgroups" to Look(true, .44f, 10_000, 3_000),
            "clown" to Look(true, .4f, 20_000, 2_000),
            "paste" to Look(true, .38f, 20_000, 2_500),
            "clipboard" to Look(true, .44f, 20_000, 3_000),
            FINALE to Look(true, .6f, 8_000, 6_000),
            SETUP to Look(true, .4f, 20_000, 0),
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
        private const val SWIPE_EVERY = 4000f
        private const val SWIPE_SWEEP = 2900f
        private const val SWIPE_TAIL = .55f
        /** The swipe's glow and its trail: how much wider each is, and how strong. */
        private val GLOW = listOf(2.6f to .2f, 1f to 1f)
        private const val SHOOT = 1300f
        private const val TYPEWRITER_LOOP = 7000f
        private const val BALL_LOOP = 6000f
        private const val BALL_FLIGHT = 1500f
        private const val BALL_DROP = 380f
        private const val SHRED_STEP = 2000f
        private const val SHRED_PITCH = 92f
        private const val SHRED_STRIPS = 5
        private const val SHRED_LETTERS = "TKAWSLOE"
        /** The road bends one way and back over this; its dashes and posts come round every [DASH_LOOP]. */
        private const val DRIVE_LOOP = 12000f
        private const val DASH_LOOP = 4000f
        private const val DASHES = 5
        private val BEATS = floatArrayOf(.28f, .56f)
        private const val SNOW_LOOP = 20000f
        private const val BUOY_LOOP = 20000f
        private val BUOY_SIZES = floatArrayOf(86f, 66f, 100f)
        private val BUOY_AT = floatArrayOf(.2f, .5f, .8f)
        private const val PEEK_LOOP = 10000f
        private const val FOREVER_LOOP = 10000f
        /** The chase lights' step: 48 to the circus's 20 s loop, a multiple of 3, so the loop comes round exactly. */
        private const val CHASE_STEP = 20000f / 48
        private const val JUGGLE_LOOP = 5000f
        private const val NOTE_LOOP = 20000f
        private const val NOTE_FLAP = 5000f
        private val NOTE_LINES = floatArrayOf(38f, 30f, 20f)
        private const val CLIPS_LOOP = 20000f
        private const val CLIP_LENGTH = 44f
        private const val GRASS_GROW = 2600f
        /** How much later than the first a blade may start to grow, so the band comes up unevenly, as grass does. */
        private const val GRASS_STAGGER = 700f
        /**
         * The tallest the grass grows, a flower's head and petals included, in dp: the end's grass room (GuideFinale.GRASS_DP, 150 dp).
         * Where the room is smaller, the whole band scales down to fit it.
         */
        private const val GRASS_TALLEST = 150f
        /** The longest blade, in dp; the flowers stand a little over it. */
        private const val BLADE_DP = 136f
        private const val GRASS_SETTLED = 4500f
        private const val GRASS_LOOP = 8000f
        /** The three layers, back to front: their tone as part of the layer's strength, their spacing in dp, their heights in [BLADE_DP]. */
        private val GRASS_TONES = floatArrayOf(.4f, .667f, 1f)
        private val GRASS_PITCH = floatArrayOf(6f, 7f, 8f)
        private val GRASS_HEIGHTS = floatArrayOf(.6f, 1f, .42f, .68f, .2f, .42f)
        /** Where the flowers stand, across the screen. */
        private val FLOWERS = floatArrayOf(.18f, .56f, .84f)
        private const val GEAR_LOOP = 20000f
        private const val GEAR_MODULE = 8.5f
        private const val BIG_TEETH = 14
        private const val SMALL_TEETH = 9

        /** The three swipes, each a cubic Bezier's x0, y0 … x3, y3: x across the area, 0 to 1, and y across the open band, -1 to 1. */
        private val SWIPES = arrayOf(
            floatArrayOf(-.1f, -.7f, .3f, 2.9f, .66f, -2.9f, 1.1f, .6f),
            floatArrayOf(1.1f, -.5f, .72f, 2.8f, .28f, -2.8f, -.1f, .8f),
            floatArrayOf(-.1f, .7f, .32f, -3f, .68f, 3f, 1.1f, -.6f),
        )

        /** The proofreader's marks in units, each a list of strokes as x, y pairs. */
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

        /** How far each mark reaches either side of its middle, in units. */
        private val MARK_HALF = FloatArray(MARKS.size) { k -> MARKS[k].maxOf { s -> (s.indices step 2).maxOf { abs(s[it]) } } }

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
        /** [x] folded into 0 until [n], for times. */
        private fun wrap(x: Float, n: Float) = x - floor(x / n) * n
        private fun smooth(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        private fun sine(x: Float): Float = (1f - cos(PI.toFloat() * x.coerceIn(0f, 1f))) / 2f
        private fun bump(x: Float): Float = if (x <= 0f || x >= 1f) 0f else sin(PI.toFloat() * x)
        private fun backOut(x: Float): Float {
            val u = x.coerceIn(0f, 1f) - 1f
            return 1f + u * u * (2.2f * u + 1.2f)
        }
    }
}
