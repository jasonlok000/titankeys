package com.atlas.keyboard

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The idle games on the suggestion bar, an Easter egg. After a long while with no input, the bar's own words and letters play a
 * game, inside the bar: Gravity off, Pong, Bowling, Jump rope, Race or Juggling, which loop until you come back. When you come
 * back, the letters go home: quickly, or with a Shell game or a Firework on the way, and juggled letters are dropped and fall into
 * place. The moment you type or swipe, the bar cuts straight back to its words. Nothing just pops in: every game starts by moving
 * the letters out of the words. Nothing makes a sound, and no letter takes a touch.
 *
 * This is the motion only, in fixed steps, so it needs no Android and the checks can play it. [IdleDrawing] draws it, and [Strip]
 * runs it. Places are in the bar's own units: the bar is [H] high, as the real bar is 88 pixels, and [IdlePlayer.width] wide.
 * Ported from the owner's approved demo, idle-demo-v2.
 */
internal object Idle {
    const val H = 88f
    /** The size the demo's numbers were made for: a letter of this size has the demo's radius and speed. */
    const val FONT_SIZE = 40f
    const val EXIT_MILLIS = 300f
    /** The physics moves in steps this long, so the motion is the same at any frame rate. */
    const val STEP = 1000f / 240f

    fun clamp(v: Float, a: Float, b: Float) = max(a, min(b, v))
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun smooth(t: Float) = clamp(t, 0f, 1f).let { it * it * (3 - 2 * it) }
    fun easeInOut(t: Float) = clamp(t, 0f, 1f).let { if (it < .5f) 4 * it * it * it else 1 - (-2 * it + 2).pow(3) / 2 }
    fun easeOut(t: Float) = clamp(t, 0f, 1f).let { 1 - (1 - it).pow(3) }
    fun easeIn(t: Float) = clamp(t, 0f, 1f).let { it * it * it }
    fun backOut(t: Float) = clamp(t, 0f, 1f).let { val c = 1.6f; val q = it - 1; 1 + (c + 1) * q * q * q + c * q * q }
    fun wrapAngle(angle: Float): Float {
        var a = (angle + PI.toFloat()) % (2 * PI.toFloat())
        if (a < 0) a += 2 * PI.toFloat()
        return a - PI.toFloat()
    }
    /** [y] folded into [lo, hi] as a ball bouncing off both ends would be: where it really is after its bounces. */
    fun fold(y: Float, lo: Float, hi: Float): Float {
        val span = hi - lo
        val m = (((y - lo) % (2 * span)) + 2 * span) % (2 * span)
        return if (m <= span) lo + m else hi - (m - span)
    }
    fun Random.range(a: Float, b: Float) = a + (b - a) * nextFloat()

    /** Two letters that touch push apart and trade speed. A heavier letter loses less, and a glancing hit sets them spinning. */
    fun collide(a: Glyph, b: Glyph, restitution: Float): Boolean {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val d = hypot(dx, dy)
        val least = a.radius + b.radius
        if (d >= least || d == 0f) return false
        val nx = dx / d
        val ny = dy / d
        val overlap = least - d
        val sa = b.mass / (a.mass + b.mass)
        val sb = a.mass / (a.mass + b.mass)
        a.x -= nx * overlap * sa; a.y -= ny * overlap * sa; b.x += nx * overlap * sb; b.y += ny * overlap * sb
        val rel = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
        if (rel > 0) return true
        val j = -(1 + restitution) * rel / (1 / a.mass + 1 / b.mass)
        a.vx -= j * nx / a.mass; a.vy -= j * ny / a.mass; b.vx += j * nx / b.mass; b.vy += j * ny / b.mass
        val tangent = (b.vx - a.vx) * -ny + (b.vy - a.vy) * nx
        a.spin -= tangent * .004f; b.spin += tangent * .004f
        return true
    }

    /** A letter that rolls well: the roundest one in [list], or the first. */
    fun roundest(list: List<Glyph>): Glyph? {
        for (char in "oO0aeQqdbpgc") list.firstOrNull { it.char == char }?.let { return it }
        return list.firstOrNull()
    }
}

/**
 * One letter of the bar: where it lives in its word ([homeX], [homeY], its middle), how wide it is there ([advance]) and its text
 * size ([size]), all in the bar's units; and where it is now.
 */
internal class Glyph(val char: Char, val word: Int, val homeX: Float, val homeY: Float, val advance: Float, val size: Float) {
    val text = char.toString()
    var x = homeX
    var y = homeY
    var rot = 0f
    var scale = 1f
    var alpha = 1f
    /** Drawn in the accent colour instead of the words' own. */
    var accent = false
    var vx = 0f
    var vy = 0f
    var spin = 0f
    var radius = size * .32f
    var mass = 1f

    /** Back home, at rest, as on the bar. */
    fun reset() {
        x = homeX; y = homeY; rot = 0f; scale = 1f; alpha = 1f; accent = false
        vx = 0f; vy = 0f; spin = 0f; radius = size * .32f; mass = 1f
    }
}

/** The stick figure: thin lines in the accent colour. Arms are offsets from the shoulder. */
internal class Figure(var x: Float, var feet: Float) {
    var lean = 0f
    var step = 0f
    var alpha = 1f
    var armLX = -12f; var armLY = 16f
    var armRX = 12f; var armRY = 16f

    fun copy() = Figure(x, feet).also {
        it.lean = lean; it.step = step; it.alpha = alpha
        it.armLX = armLX; it.armLY = armLY; it.armRX = armRX; it.armRY = armRY
    }

    companion object {
        const val HEAD = 8f
        const val NECK = 52f
        const val HIP = 26f
        const val LINE = 3f
    }
}

/** A game on the bar. [update] moves it on by some milliseconds and returns false once it is over. */
internal abstract class IdleGame(val glyphs: List<Glyph>, val width: Float, val random: Random) {
    open val figure: Figure? get() = null
    /** True for a game that is only a way home, so it is never chosen to play while idle. */
    open val returning: Boolean get() = false
    abstract fun update(dt: Float): Boolean

    protected fun words(word: Int) = glyphs.filter { it.word == word }
    protected fun wordCentre(word: Int) = words(word).let { list -> if (list.isEmpty()) width / 2 else list.sumOf { it.homeX.toDouble() }.toFloat() / list.size }
}

/** Every letter home in [millis], an in-out ease, [stagger] apart, from wherever it is now. */
internal class HomeTween(private val glyphs: List<Glyph>, private val millis: Float, private val stagger: Float) {
    private val fromX = FloatArray(glyphs.size) { glyphs[it].x }
    private val fromY = FloatArray(glyphs.size) { glyphs[it].y }
    private val fromRot = FloatArray(glyphs.size) { Idle.wrapAngle(glyphs[it].rot) }
    private val fromScale = FloatArray(glyphs.size) { glyphs[it].scale }
    private val fromAlpha = FloatArray(glyphs.size) { glyphs[it].alpha }
    private var t = 0f

    /** Returns true once every letter is home. */
    fun update(dt: Float): Boolean {
        t += dt
        var done = true
        for ((i, g) in glyphs.withIndex()) {
            val p = Idle.easeInOut((t - i * stagger) / millis)
            if (p < 1) done = false
            g.x = Idle.lerp(fromX[i], g.homeX, p); g.y = Idle.lerp(fromY[i], g.homeY, p) - sin(p * PI.toFloat()) * 10
            g.rot = Idle.lerp(fromRot[i], 0f, p); g.scale = Idle.lerp(fromScale[i], 1f, p); g.alpha = Idle.lerp(fromAlpha[i], 1f, p)
            if (p > .6f) g.accent = false
        }
        if (done) glyphs.forEach { it.reset() }
        return done
    }
}

// The games that play while idle, and loop until you come back.

/** 1. Gravity off: the letters drift like a screensaver logo, bouncing off each other and off every edge of the bar. */
internal class Drift(glyphs: List<Glyph>, width: Float, random: Random) : IdleGame(glyphs, width, random) {
    private var t = 0f
    private val speed = FloatArray(glyphs.size) { random.nextFloat() * (SPEED_MAX - SPEED_MIN) + SPEED_MIN }
    private val heading = FloatArray(glyphs.size) { random.nextFloat() * 2 * PI.toFloat() }

    init {
        glyphs.forEach { it.spin = random.nextFloat() * 1.2f - .6f }
    }

    override fun update(dt: Float): Boolean {
        t += dt
        val ramp = Idle.smooth(t / INTRO)
        var left = dt
        while (left > 0) {
            val h = min(Idle.STEP, left) / 1000
            left -= Idle.STEP
            for ((i, g) in glyphs.withIndex()) {
                // The letters lift off one after another, and get up to speed gently.
                val start = Idle.clamp((t - (i % 6) * 60) / INTRO, 0f, 1f)
                if (t < INTRO) {
                    g.vx = cos(heading[i]) * speed[i] * Idle.smooth(start); g.vy = sin(heading[i]) * speed[i] * Idle.smooth(start)
                }
                g.x += g.vx * h; g.y += g.vy * h; g.rot += g.spin * h
                g.scale = 1 + .06f * sin(start * PI.toFloat())
                if (g.x < g.radius) { g.x = g.radius; g.vx = abs(g.vx); g.spin += random.nextFloat() * .6f - .3f }
                if (g.x > width - g.radius) { g.x = width - g.radius; g.vx = -abs(g.vx); g.spin += random.nextFloat() * .6f - .3f }
                if (g.y < g.radius) { g.y = g.radius; g.vy = abs(g.vy) }
                if (g.y > Idle.H - g.radius) { g.y = Idle.H - g.radius; g.vy = -abs(g.vy) }
            }
            for (i in glyphs.indices) for (j in i + 1 until glyphs.size) Idle.collide(glyphs[i], glyphs[j], 1f)
            // Collisions trade speed; each letter drifts back to its own pace, so the bar never calms down or races.
            for ((i, g) in glyphs.withIndex()) {
                val v = hypot(g.vx, g.vy).takeIf { it > 0 } ?: 1f
                if (t >= INTRO) { val k = Idle.lerp(v, speed[i] * ramp, .002f) / v; g.vx *= k; g.vy *= k }
                g.spin = Idle.clamp(g.spin * .9995f, -2f, 2f)
            }
        }
        return true
    }

    private companion object {
        const val INTRO = 700f
        const val SPEED_MIN = 55f
        const val SPEED_MAX = 105f
    }
}

/**
 * 2. Pong: the side words stand up as paddles, a round letter of the middle word is the ball, and the rest of the middle word is
 * an obstacle that moves up and down.
 */
internal class Pong(glyphs: List<Glyph>, width: Float, random: Random) : IdleGame(glyphs, width, random) {
    private class Paddle(val list: List<Glyph>, val x: Float, val sign: Float, val scale: Float, val half: Float, val offsets: FloatArray) {
        var y = Idle.H / 2
        var vy = 0f
    }

    private val middle = words(1)
    private val ball = Idle.roundest(middle.ifEmpty { glyphs })!!
    private val wall = middle.filter { it !== ball }
    private val wallXs = HashMap<Glyph, Float>()
    private val wallX0: Float
    private val wallX1: Float
    private val paddles: List<Paddle>
    private val fromX = FloatArray(glyphs.size) { glyphs[it].x }
    private val fromY = FloatArray(glyphs.size) { glyphs[it].y }
    private var joltY = 0f
    private var joltV = 0f
    private val ballRadius = ball.radius * BALL_SCALE
    private var t = 0f
    private val direction = if (random.nextFloat() < .5f) -1f else 1f
    /** Where the obstacle is in its own slow sway, up and down. */
    private var sway = 0f

    init {
        // The wall closes the gap the ball left and shrinks a little, so there is room to pass over and under it.
        val centre = if (wall.isEmpty()) width / 2 else wall.sumOf { it.homeX.toDouble() }.toFloat() / wall.size
        val wallWidth = wall.sumOf { (it.advance * WALL_SCALE).toDouble() }.toFloat()
        var x = centre - wallWidth / 2
        for (g in wall) { val a = g.advance * WALL_SCALE; wallXs[g] = x + a / 2; x += a }
        wallX0 = (wallXs.values.minOrNull() ?: 0f) - 12 * WALL_SCALE
        wallX1 = (wallXs.values.maxOrNull() ?: 0f) + 12 * WALL_SCALE
        paddles = listOf(paddle(words(0), X_SIDE, -1f), paddle(words(2), width - X_SIDE, 1f))
    }

    private fun paddle(list: List<Glyph>, x: Float, sign: Float): Paddle {
        val length = list.sumOf { it.advance.toDouble() }.toFloat().takeIf { it > 0 } ?: 1f
        val scale = min(.7f, PADDLE_MAX / length)
        val half = length * scale / 2 + 4
        var at = -half + 4
        val offsets = FloatArray(list.size)
        for ((i, g) in list.withIndex()) { val a = g.advance * scale; offsets[i] = at + a / 2; at += a }
        return Paddle(list, x, sign, scale, half, offsets)
    }

    private fun placePaddles(p: Float) {
        for (pd in paddles) for ((i, g) in pd.list.withIndex()) {
            val k = glyphs.indexOf(g)
            g.x = Idle.lerp(fromX[k], pd.x, p); g.y = Idle.lerp(fromY[k], pd.y - pd.offsets[i] * pd.sign, p)
            g.rot = Idle.lerp(0f, pd.sign * PI.toFloat() / 2, p); g.scale = Idle.lerp(1f, pd.scale, p)
        }
    }

    /** A shot towards the side [sign]: mostly over or under the wall, now and then straight at it. */
    private fun aim(sign: Float) {
        val speed = BALL_SPEED * (.95f + random.nextFloat() * .13f)
        val lane = if (wall.isEmpty()) ballRadius + random.nextFloat() * (Idle.H - 2 * ballRadius)
            else if (random.nextFloat() < OVER_CHANCE) (if (random.nextFloat() < .5f) ballRadius + 3 else Idle.H - ballRadius - 3)
            else Idle.H / 2 + sway + random.nextFloat() * 12 - 6
        val reach = if (wall.isEmpty()) width / 2 else abs((wallX0 + wallX1) / 2 - ball.x)
        ball.vx = sign * speed
        ball.vy = Idle.clamp((lane - ball.y) * speed / max(reach, 60f), -speed * .7f, speed * .7f)
        ball.spin = sign * (3 + random.nextFloat() * 3)
    }

    override fun update(dt: Float): Boolean {
        t += dt
        if (t < INTRO) {
            val p = Idle.easeInOut(t / INTRO)
            val q = Idle.easeOut(t / INTRO)
            val k = glyphs.indexOf(ball)
            placePaddles(p)
            // The ball hops out of its word; the rest of the word draws in and dims.
            ball.x = fromX[k]; ball.y = fromY[k] - sin(q * PI.toFloat()) * 22; ball.accent = true; ball.scale = Idle.lerp(1f, BALL_SCALE, q)
            for (g in wall) { g.alpha = Idle.lerp(1f, .55f, q); g.scale = Idle.lerp(1f, WALL_SCALE, p); g.x = Idle.lerp(g.homeX, wallXs[g]!!, p) }
            if (t + dt >= INTRO) { ball.radius = ballRadius; aim(direction) }
            return true
        }
        var left = dt
        while (left > 0) {
            val h = min(Idle.STEP, left) / 1000
            left -= Idle.STEP
            ball.x += ball.vx * h; ball.y += ball.vy * h; ball.rot += ball.spin * h
            if (ball.y < ball.radius) { ball.y = ball.radius; ball.vy = abs(ball.vy) }
            if (ball.y > Idle.H - ball.radius) { ball.y = Idle.H - ball.radius; ball.vy = -abs(ball.vy) }
            for (pd in paddles) {
                val coming = pd.sign * ball.vx > 0
                val edge = pd.x - pd.sign * 10
                // A paddle glides on a spring to where the ball will arrive, after its bounces; when the ball goes away it drifts
                // back to the middle.
                val want = if (coming) Idle.fold(ball.y + ball.vy * abs(edge - ball.x) / abs(ball.vx), ball.radius, Idle.H - ball.radius)
                    else Idle.H / 2
                pd.vy += ((want - pd.y) * SPRING - pd.vy * DAMP) * h
                pd.y = Idle.clamp(pd.y + pd.vy * h, pd.half, Idle.H - pd.half)
                val reached = if (pd.sign < 0) ball.x - ball.radius <= edge else ball.x + ball.radius >= edge
                if (reached && coming) { ball.x = edge - pd.sign * ball.radius; aim(-pd.sign) }
            }
            // The obstacle sways up and down, slowly; the ball bounces off its box, and the word gives a little.
            sway = SWAY * sin((t / SWAY_PERIOD) * 2 * PI.toFloat()) * Idle.smooth((t - INTRO) / 600)
            if (wall.isNotEmpty()) {
                val y0 = Idle.H / 2 - WALL_HALF + joltY + sway
                val y1 = Idle.H / 2 + WALL_HALF + joltY + sway
                val r = ball.radius
                if (ball.x + r > wallX0 && ball.x - r < wallX1 && ball.y + r > y0 && ball.y - r < y1) {
                    val dx = min(ball.x + r - wallX0, wallX1 - (ball.x - r))
                    val dy = min(ball.y + r - y0, y1 - (ball.y - r))
                    if (dx < dy) {
                        ball.x += if (ball.x < (wallX0 + wallX1) / 2) -dx else dx; ball.vx = -ball.vx; joltV += random.nextFloat() * 60 - 30
                    } else {
                        val up = ball.y < (y0 + y1) / 2
                        ball.y += if (up) -dy else dy; ball.vy = if (up) -abs(ball.vy) else abs(ball.vy); joltV += if (up) 70 else -70
                    }
                }
            }
            joltV += (-joltY * 180 - joltV * 14) * h; joltY += joltV * h
        }
        placePaddles(1f)
        for (g in wall) { g.x = wallXs[g]!!; g.y = g.homeY + joltY + sway }
        return true
    }

    private companion object {
        const val INTRO = 900f
        const val BALL_SPEED = 460f
        const val BALL_SCALE = .75f
        const val WALL_SCALE = .75f
        const val WALL_HALF = 12f
        const val PADDLE_MAX = 58f
        const val X_SIDE = 34f
        const val SPRING = 260f
        val DAMP = 2 * sqrt(260f)
        const val OVER_CHANCE = .75f
        /** How far the obstacle sways from the middle, and how long one sway takes. */
        const val SWAY = 14f
        const val SWAY_PERIOD = 2600f
    }
}

/** 3. Bowling: the figure rolls a round letter at the other letters, stacked like pins; they scatter and go home. Each round ends. */
internal class Bowling(glyphs: List<Glyph>, width: Float, random: Random) : IdleGame(glyphs, width, random) {
    private enum class Phase { Intro, Windup, Roll, Scatter, Cheer, Home }

    private val ball = IdlePlayer.ballOf(glyphs)!!
    private val pins = glyphs.filter { it !== ball }
    private val fromX = FloatArray(glyphs.size) { glyphs[it].x }
    private val fromY = FloatArray(glyphs.size) { glyphs[it].y }
    private val spotX: FloatArray
    private val spotY: FloatArray
    private val roll = 560 + random.nextFloat() * 140
    override val figure = Figure(-40f, Idle.H - 2)
    private var t = 0f
    private var phase = Phase.Intro
    private var settled = 0f
    private var cheer = 0f
    private var struckAt = 0f
    private var outro: HomeTween? = null

    init {
        // Up to three rows, so the top of the stack stays inside the bar; long words make a wider stack instead.
        var rows = 1
        while (rows < 3 && rows * (rows + 1) / 2 < pins.size) rows++
        val bottom = ceil((pins.size + rows * (rows - 1) / 2f) / rows).toInt()
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val gap = 30 * PIN_SCALE
        for (r in 0 until rows) for (c in 0 until bottom - r) if (xs.size < pins.size) {
            xs += width * .8f - (bottom - 1) * gap + c * gap + r * gap / 2; ys += FLOOR - 13 - r * 22
        }
        spotX = xs.toFloatArray()
        spotY = ys.toFloatArray()
    }

    override fun update(dt: Float): Boolean {
        t += dt
        when (phase) {
            Phase.Intro -> {
                // The figure walks on; the ball hops to its hand; the other letters hop into a stack of pins.
                val p = Idle.easeInOut(t / WALK)
                figure.x = Idle.lerp(-40f, 90f, p); figure.step = p * 9
                val k = glyphs.indexOf(ball)
                val q = Idle.easeInOut((t - 200) / 600)
                ball.x = Idle.lerp(fromX[k], figure.x + 18, q); ball.y = Idle.lerp(fromY[k], FLOOR - 32, q) - sin(q * PI.toFloat()) * 30
                ball.accent = true; ball.scale = Idle.lerp(1f, .9f, q)
                for ((i, g) in pins.withIndex()) {
                    val j = glyphs.indexOf(g)
                    val s = Idle.easeInOut((t - 100 - i * 18) / 520)
                    g.x = Idle.lerp(fromX[j], spotX[i], s); g.y = Idle.lerp(fromY[j], spotY[i], s) - sin(s * PI.toFloat()) * 22
                    g.scale = Idle.lerp(1f, PIN_SCALE, s)
                    // A pin is as wide as its letter, so knocked-down pins lie side by side instead of on top of each other.
                    g.radius = Idle.clamp(g.advance * PIN_SCALE / 2, 7f, 30 * PIN_SCALE / 2 - .5f)
                }
                if (t > WALK + 200 + pins.size * 18 + 100) { phase = Phase.Windup; t = 0f }
            }
            Phase.Windup -> {
                val p = t / WINDUP
                val s = sin(p * PI.toFloat())
                figure.lean = -s * .25f; figure.armRX = 12 + 10 * s; figure.armRY = 16 - 26 * s
                ball.x = figure.x + 18 - s * 16; ball.y = FLOOR - 32 + s * 10
                if (p >= 1) {
                    phase = Phase.Roll; t = 0f; ball.vx = roll; ball.vy = 0f; ball.mass = BALL_MASS
                    figure.lean = .15f; figure.armRX = 22f; figure.armRY = 4f
                }
            }
            Phase.Roll, Phase.Scatter -> {
                figure.lean = Idle.lerp(figure.lean, 0f, .05f)
                var left = dt
                while (left > 0) {
                    val h = min(Idle.STEP, left) / 1000
                    left -= Idle.STEP
                    move(ball, h)
                    if (phase == Phase.Scatter) pins.forEach { move(it, h) }
                    if (phase == Phase.Roll && ball.vy == 0f) ball.y = FLOOR - ball.radius
                    // The strike: the first touch sends every pin flying forward and up, harder the nearer it stood to the ball.
                    if (phase == Phase.Roll && pins.any { hypot(it.x - ball.x, it.y - ball.y) < it.radius + ball.radius }) {
                        phase = Phase.Scatter; struckAt = t
                        for (p in pins) {
                            val dx = p.x - ball.x
                            val dy = p.y - ball.y
                            val d = hypot(dx, dy).takeIf { it > 0 } ?: 1f
                            val power = STRIKE * exp(-d / STRIKE_REACH) * (.75f + random.nextFloat() * .45f)
                            p.vx = power * (.3f + .6f * dx / d); p.vy = -power * (.35f + random.nextFloat() * .5f) + power * .25f * dy / d
                            p.spin = random.nextFloat() * 18 - 9
                        }
                        ball.vx *= .75f
                    }
                    if (phase == Phase.Scatter) {
                        pins.forEach { Idle.collide(ball, it, .4f) }
                        // Pins pass through each other in the air at first, then lie down side by side.
                        if (t - struckAt > SETTLE_AFTER) repeat(2) {
                            for (i in pins.indices) for (j in i + 1 until pins.size) Idle.collide(pins[i], pins[j], .2f)
                        }
                        for (p in pins) if (p.y >= FLOOR - p.radius - .5f) p.rot += (round(p.rot / PI.toFloat() * 2) * PI.toFloat() / 2 - p.rot) * .02f
                    }
                }
                val moving = hypot(ball.vx, ball.vy) > 18 || pins.any { hypot(it.vx, it.vy) > 18 }
                settled = if (moving) 0f else settled + dt
                if (phase == Phase.Scatter && (settled > 450 || t > 3600)) { phase = Phase.Cheer; t = 0f }
                // Never stuck: a roll that somehow misses still ends.
                if (phase == Phase.Roll && t > 4000) { phase = Phase.Cheer; t = 0f; cheer = 640f }
            }
            Phase.Cheer -> {
                cheer += dt
                figure.feet = Idle.H - 2 - abs(sin(cheer / 160 * PI.toFloat())) * 6
                figure.armLX = -10f; figure.armLY = -22f; figure.armRX = 10f; figure.armRY = -22f
                if (cheer > 640) {
                    phase = Phase.Home; t = 0f; outro = HomeTween(glyphs, 700f, 10f)
                    figure.feet = Idle.H - 2; figure.armLX = -12f; figure.armLY = 16f; figure.armRX = 12f; figure.armRY = 16f
                }
            }
            Phase.Home -> {
                // Everything back to the bar, and the figure walks off. Then the next round.
                figure.x -= dt * .25f; figure.step += dt / 70; figure.alpha = 1 - t / 600
                return !(outro!!.update(dt) && t > 600)
            }
        }
        return true
    }

    private fun move(b: Glyph, h: Float) {
        b.vy += GRAVITY * h; b.x += b.vx * h; b.y += b.vy * h; b.rot += (if (b === ball) b.vx / b.radius else b.spin) * h
        if (b.y > FLOOR - b.radius) {
            // The ball rolls on with little loss; pins slide and stop quickly.
            b.y = FLOOR - b.radius; b.vy = -b.vy * .3f; if (abs(b.vy) < 20) b.vy = 0f
            b.vx *= exp(-(if (b === ball) .3f else 4f) * h); b.spin *= exp(-6 * h)
        }
        if (b.x > width - b.radius) { b.x = width - b.radius; b.vx = -b.vx * .4f }
        if (b.y < b.radius) { b.y = b.radius; b.vy = abs(b.vy) * .3f }
    }

    private companion object {
        const val FLOOR = Idle.H - 6
        const val GRAVITY = 1100f
        const val BALL_MASS = 4f
        const val STRIKE = 340f
        const val STRIKE_REACH = 200f
        const val SETTLE_AFTER = 260f
        const val WALK = 700f
        const val WINDUP = 380f
        const val PIN_SCALE = .82f
    }
}

/** 5. Jump rope: a letter at each side swings a rope, and the middle word jumps it. */
internal class JumpRope(glyphs: List<Glyph>, width: Float, random: Random) : IdleGame(glyphs, width, random) {
    private val holderL = words(0).firstOrNull() ?: glyphs.first()
    private val holderR = words(2).lastOrNull() ?: glyphs.last()
    private val jumper = words(1)
    private val rest = glyphs.filter { it !== holderL && it !== holderR && it.word != 1 }
    private val fromX = FloatArray(glyphs.size) { glyphs[it].x }
    private val fromY = FloatArray(glyphs.size) { glyphs[it].y }
    private val period = 820 + random.nextFloat() * 160
    private var t = 0f
    private var landed = -1f
    /** The rope, for the drawing: how far it has swung out, its angle, and its ends. */
    var ropeAmount = 0f; private set
    var ropeAngle = PI.toFloat(); private set
    val ropeX0 get() = holderL.x + HAND
    val ropeX1 get() = holderR.x - HAND
    val ropeY get() = holderL.y + 6

    override fun update(dt: Float): Boolean {
        t += dt
        val p = Idle.easeInOut(t / INTRO)
        for ((g, x) in listOf(holderL to width * .27f, holderR to width * .73f)) {
            val k = glyphs.indexOf(g)
            g.x = Idle.lerp(fromX[k], x, p); g.y = Idle.lerp(fromY[k], Idle.H / 2 - 4, p)
        }
        // The other side letters make room and watch.
        for (g in rest) { val k = glyphs.indexOf(g); g.x = Idle.lerp(fromX[k], fromX[k] + if (g.word == 0) -50 else 50, p); g.alpha = Idle.lerp(1f, .32f, p) }
        val speed = (2 * PI.toFloat() / period) * Idle.smooth((t - 300) / INTRO)
        ropeAngle += speed * dt
        holderL.rot = .35f * sin(ropeAngle) * p; holderR.rot = -.35f * sin(ropeAngle) * p
        // The jumper is in the air while the rope passes under it, highest when the rope is lowest, and it squashes on landing.
        val u = Idle.wrapAngle(ropeAngle)
        val air = abs(u) < 1.05f && speed > 0
        val height = if (air) JUMP * cos(u / 1.05f * PI.toFloat() / 2) * Idle.smooth((t - 300) / INTRO) else 0f
        if (!air && landed < 0 && speed > 0 && abs(u) < 1.4f) landed = t
        if (air) landed = -1f
        val squash = if (landed >= 0) sin(Idle.clamp((t - landed) / 140, 0f, 1f) * PI.toFloat()) * .12f else 0f
        for ((i, g) in jumper.withIndex()) {
            g.y = g.homeY + 10 * p - height; g.scale = 1 - squash * .5f; g.rot = if (air) sin(u) * .05f * (if (i % 2 == 1) 1 else -1) else 0f
        }
        ropeAmount = Idle.smooth((t - 200) / INTRO)
        return true
    }

    private companion object {
        const val INTRO = 1000f
        const val JUMP = 24f
        const val HAND = 14f
    }
}

/** 7. Race: the words line up in three lanes and race to a finish line; one wins, and they all go home. Each race ends. */
internal class Race(glyphs: List<Glyph>, width: Float, random: Random) : IdleGame(glyphs, width, random) {
    private enum class Phase { LineUp, Ready, Race, Finish, Home }
    private class Racer(var front: Float, val top: Float, val surgeA: Float, val surgeB: Float) {
        var speed = 0f
        var done = false
    }

    private val lanes = floatArrayOf(17f, Idle.H / 2, Idle.H - 17)
    private val finish = width - 46
    private val present = (0..2).filter { w -> glyphs.any { it.word == w } }
    private val winner = present[random.nextInt(present.size)]
    private val lengths = FloatArray(3) { w -> words(w).sumOf { (it.advance * SCALE).toDouble() }.toFloat() }
    private val offsets = FloatArray(glyphs.size)
    private val racers: List<Racer>
    private val fromX = FloatArray(glyphs.size) { glyphs[it].x }
    private val fromY = FloatArray(glyphs.size) { glyphs[it].y }
    private var t = 0f
    private var phase = Phase.LineUp
    private var finishAt = -1f
    private var outro: HomeTween? = null
    /** How much of the finish line shows, for the drawing, and where it is. */
    var line = 0f; private set
    val lineX get() = finish + 4

    init {
        for ((i, g) in glyphs.withIndex()) {
            var at = 0f
            for (o in words(g.word)) { if (o === g) break; at += o.advance * SCALE }
            offsets[i] = at + g.advance * SCALE / 2
        }
        // The winner is a touch faster overall, but leads can change.
        racers = (0..2).map { w ->
            Racer(START + lengths[w], (if (w == winner) 1f else .88f + random.nextFloat() * .08f) * (330 + random.nextFloat() * 50),
                random.nextFloat() * 6, random.nextFloat() * 6)
        }
    }

    private fun place() {
        for ((i, g) in glyphs.withIndex()) {
            val r = racers[g.word]
            val bob = if (phase == Phase.Race) abs(sin(t / 55 + i)) * -2.5f * (r.speed / r.top) else 0f
            g.x = r.front - lengths[g.word] + offsets[i]; g.y = lanes[g.word] + bob; g.scale = SCALE
        }
    }

    override fun update(dt: Float): Boolean {
        t += dt
        when (phase) {
            Phase.LineUp -> {
                line = Idle.smooth(t / LINE_UP)
                for ((i, g) in glyphs.withIndex()) {
                    val p = Idle.easeInOut((t - (i % 5) * 20) / LINE_UP)
                    val r = racers[g.word]
                    g.x = Idle.lerp(fromX[i], r.front - lengths[g.word] + offsets[i], p)
                    g.y = Idle.lerp(fromY[i], lanes[g.word], p) - sin(p * PI.toFloat()) * 8; g.scale = Idle.lerp(1f, SCALE, p)
                }
                if (t >= LINE_UP + 120) { phase = Phase.Ready; t = 0f }
            }
            Phase.Ready -> {
                // Three little crouches before the start.
                place()
                val crouch = if (floor(t / HOP) < READY) sin((t % HOP) / HOP * PI.toFloat()) else 0f
                glyphs.forEach { it.y += crouch * 3; it.scale = SCALE * (1 - crouch * .06f) }
                if (t >= HOP * READY + 80) { phase = Phase.Race; t = 0f }
            }
            Phase.Race, Phase.Finish -> {
                for ((w, r) in racers.withIndex()) {
                    if (lengths[w] == 0f) continue
                    val surge = 1 + .09f * sin(t / 420 + r.surgeA) + .05f * sin(t / 170 + r.surgeB)
                    val want = if (r.done) 0f else r.top * surge
                    r.speed += (want - r.speed) * Idle.clamp(dt / (if (r.done) 260 else 380), 0f, 1f)
                    r.front = min(r.front + r.speed * dt / 1000, width - 6)
                    if (r.front >= finish && !r.done) { r.done = true; if (finishAt < 0 && w == winner) finishAt = t }
                }
                // Only the winner may cross first: a loser that would get there first eases off just before the line.
                for ((w, r) in racers.withIndex()) if (w != winner && !racers[winner].done && r.front > finish - 12) {
                    r.front = finish - 12; r.speed = min(r.speed, racers[winner].speed * .9f)
                }
                place()
                if (finishAt >= 0) {
                    phase = Phase.Finish
                    val p = t - finishAt
                    val hop = abs(sin(p / 140 * PI.toFloat())) * 6 * (if (p < 560) 1 else 0)
                    for (g in words(winner)) { g.accent = true; g.y -= hop; g.scale = SCALE * (1 + .12f * Idle.smooth(p / 200)) }
                    if (p > 1100) { phase = Phase.Home; t = 0f; outro = HomeTween(glyphs, 800f, 8f) }
                }
            }
            Phase.Home -> {
                line = 1 - Idle.smooth(t / 500)
                return !outro!!.update(dt)
            }
        }
        return true
    }

    private companion object {
        const val SCALE = .56f
        const val START = 22f
        const val LINE_UP = 750f
        const val READY = 3
        const val HOP = 230f
    }
}

/** 8. Juggling: the figure walks on and juggles the middle word's three letters in a three-ball cascade; the side words stay put. */
internal class Juggle(glyphs: List<Glyph>, width: Float, random: Random) : IdleGame(glyphs, width, random) {
    val balls: List<Glyph>
    val others: List<Glyph>
    private val centre = width / 2
    private val feet = Idle.H - 2
    private val beat = 300 + random.nextFloat() * 40
    private val fromX = FloatArray(glyphs.size) { glyphs[it].x }
    private val fromY = FloatArray(glyphs.size) { glyphs[it].y }
    override val figure = Figure(width + 40, feet)
    private var t = 0f
    private var playing = 0f
    private val handY = feet - Figure.NECK + 6 + 18

    init {
        // The middle word's three letters; the side words stay where they are.
        balls = words(1)
        others = glyphs.filter { it.word != 1 }
    }

    private fun handX(side: Float, phase: Float) = centre + side * (HAND_SPREAD + sin(phase * PI.toFloat() * 2) * 4 * side * .5f)
    private fun handY(phase: Float) = handY + abs(cos(phase * PI.toFloat())) * 3

    override fun update(dt: Float): Boolean {
        t += dt
        if (t < WALK + 500) {
            val p = Idle.easeInOut(t / WALK)
            figure.x = Idle.lerp(width + 40, centre, p); figure.step = p * 12
            // Each ball hops from its word into the figure's hands, one after another.
            for ((i, g) in balls.withIndex()) {
                val k = glyphs.indexOf(g)
                val q = Idle.easeInOut((t - WALK + 200 - i * 120) / 420)
                val side = if (i % 2 == 1) 1f else -1f
                g.x = Idle.lerp(fromX[k], handX(side, 0f), q); g.y = Idle.lerp(fromY[k], handY(0f) - 6, q) - sin(q * PI.toFloat()) * 24
                g.scale = Idle.lerp(1f, .68f, q)
            }
            figure.armLX = -HAND_SPREAD; figure.armLY = 16f; figure.armRX = HAND_SPREAD; figure.armRY = 16f
            return true
        }
        playing += dt
        val tau = playing / beat
        // A three-ball cascade: each ball is thrown every three beats, from alternate hands, and rests in a hand for a moment.
        for ((k, g) in balls.withIndex()) {
            val local = tau - k
            if (local < 0) { g.x = handX(if (k % 2 == 1) 1f else -1f, 0f); g.y = handY(0f) - 6; continue }
            val n = floor(local / 3).toInt()
            val u = local - n * 3
            val fromSide = if ((k + 3 * n) % 2 == 1) 1f else -1f
            val toSide = -fromSide
            val flight = 3 - DWELL
            if (u < flight) {
                val s = u / flight
                g.x = Idle.lerp(handX(fromSide, s), handX(toSide, 0f), s)
                g.y = Idle.lerp(handY(s), handY(0f), s) - 6 - 4 * HEIGHT * s * (1 - s); g.rot += dt / 1000 * 7 * -fromSide
            } else {
                val q = (u - flight) / DWELL
                g.x = handX(toSide, q); g.y = handY(q) - 6 + sin(q * PI.toFloat()) * 3
            }
        }
        // The hands follow the balls.
        val shoulder = feet - Figure.NECK + 6
        figure.armLX = handX(-1f, tau % 1) - centre; figure.armLY = handY(tau % 1) - shoulder
        figure.armRX = handX(1f, (tau + .5f) % 1) - centre; figure.armRY = handY((tau + .5f) % 1) - shoulder
        figure.step = 12f
        return true
    }

    private companion object {
        const val WALK = 800f
        const val HEIGHT = 44f
        const val HAND_SPREAD = 38f
        const val DWELL = .45f
    }
}

// The ways home, when you come back.

/** The quick way home: from wherever each letter is, back to its word in about 300 ms, a little staggered; the figure drops out. */
internal class Exit(glyphs: List<Glyph>, width: Float, random: Random, figure: Figure?) : IdleGame(glyphs, width, random) {
    override val returning get() = true
    override val figure = figure?.copy()
    private val fromX = FloatArray(glyphs.size) { glyphs[it].x }
    private val fromY = FloatArray(glyphs.size) { glyphs[it].y }
    private val fromRot = FloatArray(glyphs.size) { Idle.wrapAngle(glyphs[it].rot) }
    private val fromScale = FloatArray(glyphs.size) { glyphs[it].scale }
    private val fromAlpha = FloatArray(glyphs.size) { glyphs[it].alpha }
    private var t = 0f

    override fun update(dt: Float): Boolean {
        t += dt
        for ((i, g) in glyphs.withIndex()) {
            val p = Idle.easeOut((t - (i % 7) * 12) / (Idle.EXIT_MILLIS - 84))
            g.x = Idle.lerp(fromX[i], g.homeX, p); g.y = Idle.lerp(fromY[i], g.homeY, p) - sin(p * PI.toFloat()) * 6
            g.rot = Idle.lerp(fromRot[i], 0f, p); g.scale = Idle.lerp(fromScale[i], 1f, p); g.alpha = Idle.lerp(fromAlpha[i], 1f, p)
            if (p > .5f) g.accent = false
        }
        figure?.let { it.alpha = 1 - Idle.easeOut(t / 180); it.feet += dt * .25f }
        if (t >= Idle.EXIT_MILLIS) { glyphs.forEach { it.reset() }; return false }
        return true
    }
}

/** Juggling's way home: the juggler drops the letters, they fall to the floor, and they hop back into their places in the words. */
internal class Drop(glyphs: List<Glyph>, width: Float, random: Random, figure: Figure?, private val balls: List<Glyph>) :
    IdleGame(glyphs, width, random) {
    override val returning get() = true
    override val figure = figure?.copy()
    private var t = 0f
    private var home: HomeTween? = null

    init {
        // Dropped: a little sideways push, and down they go.
        for (g in balls) { g.vx = random.nextFloat() * 120 - 60; g.vy = -40f; g.spin = random.nextFloat() * 8 - 4 }
    }

    override fun update(dt: Float): Boolean {
        t += dt
        figure?.let { it.armLX = -14f; it.armLY = 2f; it.armRX = 14f; it.armRY = 2f; it.alpha = 1 - Idle.easeIn(t / FALL) }
        if (home == null) {
            var left = dt
            while (left > 0) {
                val h = min(Idle.STEP, left) / 1000
                left -= Idle.STEP
                for (g in balls) {
                    g.vy += 1400 * h; g.x += g.vx * h; g.y += g.vy * h; g.rot += g.spin * h
                    if (g.y > Idle.H - g.radius * .8f) { g.y = Idle.H - g.radius * .8f; g.vy = -g.vy * .35f; g.vx *= .7f }
                }
            }
            if (t >= FALL) home = HomeTween(glyphs, 420f, 12f)
            return true
        }
        return !home!!.update(dt)
    }

    private companion object {
        const val FALL = 380f
    }
}

/** 4. Shell game, a way home: the three words shrink a little and swap places, faster then slower, and end where they started. */
internal class Shell(glyphs: List<Glyph>, width: Float, random: Random) : IdleGame(glyphs, width, random) {
    override val returning get() = true
    private val centres = FloatArray(3) { wordCentre(it) }
    private val swaps = ArrayList<IntArray>()
    private val durations: FloatArray
    private val at = intArrayOf(0, 1, 2)
    private val place = intArrayOf(0, 1, 2)
    private val fromX = FloatArray(glyphs.size) { glyphs[it].x }
    private val fromY = FloatArray(glyphs.size) { glyphs[it].y }
    private val fromRot = FloatArray(glyphs.size) { Idle.wrapAngle(glyphs[it].rot) }
    private val fromScale = FloatArray(glyphs.size) { glyphs[it].scale }
    private val fromAlpha = FloatArray(glyphs.size) { glyphs[it].alpha }
    private var t = 0f
    private var index = 0
    private var phase = 0
    private val ox = FloatArray(3)
    private val oy = FloatArray(3)
    private val os = FloatArray(3)
    private val oa = FloatArray(3)

    init {
        repeat(SWAPS) { val a = random.nextInt(3); var b = random.nextInt(2); if (b >= a) b++; swaps += intArrayOf(a, b) }
        // Then the swaps that put every word back in its own place.
        val sim = intArrayOf(0, 1, 2)
        for (s in swaps) { val x = sim[s[0]]; sim[s[0]] = sim[s[1]]; sim[s[1]] = x }
        for (i in 0..2) if (sim[i] != i) { val j = sim.indexOf(i); swaps += intArrayOf(i, j); val x = sim[i]; sim[i] = sim[j]; sim[j] = x }
        // Slow, faster in the middle, slow again; a swap across the whole bar takes a little longer.
        durations = FloatArray(swaps.size) { i ->
            (560 - 260 * sin(PI.toFloat() * (i + .5f) / swaps.size)) * (if (abs(swaps[i][0] - swaps[i][1]) == 2) 1.2f else 1f)
        }
        glyphs.forEach { it.accent = false; it.vx = 0f; it.vy = 0f }
    }

    /** Each word at its x, raised by y, at a scale and alpha; [blend] mixes in from where the letters were when this began. */
    private fun drawWords(blend: Float) {
        for ((i, g) in glyphs.withIndex()) {
            val w = g.word
            val x = ox[w] + (g.homeX - centres[w]) * os[w]
            g.x = Idle.lerp(fromX[i], x, blend); g.y = Idle.lerp(fromY[i], g.homeY + oy[w], blend)
            g.scale = Idle.lerp(fromScale[i], os[w], blend); g.alpha = Idle.lerp(fromAlpha[i], oa[w], blend)
            g.rot = Idle.lerp(fromRot[i], 0f, blend)
        }
    }

    private fun set(w: Int, x: Float, y: Float, s: Float, a: Float) { ox[w] = x; oy[w] = y; os[w] = s; oa[w] = a }

    override fun update(dt: Float): Boolean {
        t += dt
        when (phase) {
            0 -> {
                // From wherever the letters are, the words gather themselves: a small hop, and they shrink to playing size.
                val p = Idle.easeInOut(t / INTRO)
                val hop = sin(p * PI.toFloat())
                for (w in 0..2) set(w, centres[w], -hop * 8, Idle.lerp(1f, SCALE, p), 1f)
                drawWords(p)
                if (t >= INTRO) { phase = 1; t = 0f }
            }
            1 -> {
                val (a, b) = swaps[index].let { it[0] to it[1] }
                val p = Idle.easeInOut(t / durations[index])
                val arc = sin(p * PI.toFloat())
                val wa = place[a]
                val wb = place[b]
                for (w in 0..2) set(w, centres[at[w]], 0f, SCALE, 1f)
                // The word going right passes over, the one going left passes under; a word they pass by ducks and dims.
                val right = if (centres[b] > centres[a]) 1f else -1f
                set(wa, Idle.lerp(centres[a], centres[b], p), -right * arc * ARC, SCALE * (1 + right * .08f * arc), 1 - (if (right < 0) .3f * arc else 0f))
                set(wb, Idle.lerp(centres[b], centres[a], p), right * arc * ARC, SCALE * (1 - right * .08f * arc), 1 - (if (right > 0) .3f * arc else 0f))
                if (abs(a - b) == 2) set(place[1], centres[1], 0f, SCALE * (1 - .25f * arc), 1 - .5f * arc)
                drawWords(1f)
                if (t >= durations[index] + 40) {
                    at[wa] = b; at[wb] = a; place[a] = wb; place[b] = wa
                    t = 0f; index++
                    if (index >= swaps.size) phase = 2
                }
            }
            else -> {
                // Back to full size, with a small bounce as the words come to rest.
                val p = Idle.backOut(t / SETTLE)
                for (w in 0..2) set(w, centres[w], 0f, Idle.lerp(SCALE, 1f, p), 1f)
                drawWords(1f)
                if (t >= SETTLE) { glyphs.forEach { it.reset() }; return false }
            }
        }
        return true
    }

    private companion object {
        const val SCALE = .72f
        const val ARC = 24f
        const val INTRO = 450f
        const val SETTLE = 380f
        const val SWAPS = 4
    }
}

/** 6. Firework, a way home: every letter gathers into one round letter, it shoots up, bursts into all the letters, and they float home. */
internal class Firework(glyphs: List<Glyph>, width: Float, random: Random, private val asReturn: Boolean = true) :
    IdleGame(glyphs, width, random) {
    override val returning get() = asReturn
    /** A spark from the rocket, for the drawing. */
    class Spark(var x: Float, var y: Float, val vx: Float, val vy: Float, val max: Float) { var life = 0f }

    private val rocket = Idle.roundest(words(1).ifEmpty { glyphs })!!
    private val fromX = FloatArray(glyphs.size) { glyphs[it].x }
    private val fromY = FloatArray(glyphs.size) { glyphs[it].y }
    private val fromScale = FloatArray(glyphs.size) { glyphs[it].scale }
    private val fromAlpha = FloatArray(glyphs.size) { glyphs[it].alpha }
    private val launchX = rocket.homeX
    private val ground = Idle.H - 14
    val sparks = ArrayList<Spark>()
    private var t = 0f
    private var phase = 0
    private var home: HomeTween? = null

    init {
        glyphs.forEach { it.vx = 0f; it.vy = 0f; it.rot = Idle.wrapAngle(it.rot) }
    }

    override fun update(dt: Float): Boolean {
        t += dt
        when (phase) {
            0 -> {
                // The other letters stream into the rocket, which sinks a little to its launch spot.
                for ((i, g) in glyphs.withIndex()) {
                    if (g === rocket) {
                        g.x = Idle.lerp(fromX[i], launchX, Idle.easeInOut(t / GATHER)); g.y = Idle.lerp(fromY[i], ground, Idle.easeInOut(t / GATHER))
                        g.accent = true; g.alpha = 1f; g.rot = Idle.lerp(g.rot, 0f, .2f); g.scale = Idle.lerp(fromScale[i], 1.1f, Idle.smooth(t / GATHER))
                        continue
                    }
                    val p = Idle.easeIn((t - (i % 9) * 24) / (GATHER - 200))
                    g.x = Idle.lerp(fromX[i], launchX, p); g.y = Idle.lerp(fromY[i], ground, p)
                    g.scale = Idle.lerp(fromScale[i], .25f, p); g.alpha = fromAlpha[i] * (1 - p)
                }
                if (t >= GATHER) { phase = 1; t = 0f }
            }
            1 -> {
                val p = Idle.easeIn(t / LAUNCH)
                rocket.y = Idle.lerp(ground, APEX, p); rocket.x = launchX + sin(t / 40) * 1.5f * (1 - p); rocket.rot = sin(t / 30) * .05f
                if (random.nextFloat() < .7f && sparks.size < MAX_SPARKS) sparks += Spark(rocket.x + random.nextFloat() * 6 - 3, rocket.y + 12,
                    random.nextFloat() * 60 - 30, 40 + random.nextFloat() * 80, 220 + random.nextFloat() * 200)
                if (t >= LAUNCH) {
                    phase = 2; t = 0f
                    // Out it bursts: all the letters, spinning. Each aims at a spot in the bar; with drag, a speed of distance × DRAG
                    // comes to rest right there, so the burst fills the bar and never leaves it.
                    for ((i, g) in glyphs.withIndex()) {
                        val a = (i.toFloat() / glyphs.size) * PI.toFloat() * 2 + random.nextFloat() * .3f - .15f
                        val reach = .55f + random.nextFloat() * .45f
                        val tx = Idle.clamp(rocket.x + cos(a) * reach * width * .45f, 24f, width - 24)
                        val ty = Idle.clamp(rocket.y + 26 + sin(a) * reach * 22, 14f, Idle.H - 26)
                        g.x = rocket.x; g.y = rocket.y; g.vx = (tx - g.x) * DRAG; g.vy = (ty - g.y) * DRAG
                        g.spin = random.nextFloat() * 12 - 6; g.alpha = 1f; g.scale = .3f; g.accent = i % 3 == 0
                    }
                }
            }
            2 -> {
                var left = dt
                while (left > 0) {
                    val h = min(Idle.STEP, left) / 1000
                    left -= Idle.STEP
                    val k = exp(-DRAG * h)
                    for (g in glyphs) {
                        g.vx *= k; g.vy = g.vy * k + GRAVITY * h * Idle.smooth(t / 600); g.x += g.vx * h; g.y += g.vy * h; g.rot += g.spin * h; g.spin *= k
                        // A soft floor and sides: the late fall settles on the bar's floor instead of dropping out.
                        if (g.y > Idle.H - 12) { g.y = Idle.H - 12; g.vy = -abs(g.vy) * .25f; g.vx *= .9f }
                        if (g.x < 12 || g.x > width - 12) { g.x = Idle.clamp(g.x, 12f, width - 12); g.vx = -g.vx * .3f }
                    }
                }
                glyphs.forEach { it.scale = Idle.lerp(.3f, 1f, Idle.easeOut(t / 260)) }
                if (t >= BURST) { phase = 3; t = 0f; home = HomeTween(glyphs, 900f, 14f) }
            }
        }
        for (s in sparks) { s.life += dt; s.x += s.vx * dt / 1000; s.y += s.vy * dt / 1000 }
        sparks.removeAll { it.life > it.max }
        if (phase == 3) return !home!!.update(dt) || sparks.isNotEmpty()
        return true
    }

    private companion object {
        const val GATHER = 650f
        const val LAUNCH = 520f
        const val BURST = 1500f
        const val GRAVITY = 70f
        const val DRAG = 1.5f
        const val APEX = 16f
        const val MAX_SPARKS = 60
    }
}

/**
 * Runs the idle games on one bar: a game picked at random, round after round, until you come back; then a way home. While it
 * plays, the bar's own words are hidden and these letters stand in for them, so when it ends the bar is exactly as it was.
 */
internal class IdlePlayer(val glyphs: List<Glyph>, val width: Float, private val random: Random, val kind: Kind) {
    /**
     * The games, with the numbers of the owner's demo, how often each is picked against the others ([weight]: the games that play
     * once are common, the loops rare), and whether it loops until you come back, at most [LOOP_MILLIS], or plays once and gives the
     * bar back by itself.
     */
    enum class Kind(val number: Int, val weight: Int, val loops: Boolean) {
        Bowling(3, COMMON, false), Race(7, COMMON, false), Firework(6, COMMON, false),
        Drift(1, RARE, true), Pong(2, RARE, true), JumpRope(5, RARE, true), Juggle(8, RARE, true),
    }
    /** The ways home, for the debug build's trigger; normally [comeBack] picks one. */
    enum class Way(val number: Int) { Shell(4), Firework(6), Drop(0), Exit(0) }

    var game: IdleGame = make(kind)
        private set
    /** True once you came back and the letters are on their way home. */
    val returning get() = game.returning
    private var played = 0f

    private fun make(kind: Kind): IdleGame = when (kind) {
        Kind.Drift -> Drift(glyphs, width, random)
        Kind.Pong -> Pong(glyphs, width, random)
        Kind.Bowling -> Bowling(glyphs, width, random)
        Kind.JumpRope -> JumpRope(glyphs, width, random)
        Kind.Race -> Race(glyphs, width, random)
        Kind.Juggle -> Juggle(glyphs, width, random)
        Kind.Firework -> Firework(glyphs, width, random, asReturn = false)
    }

    /**
     * Moves on by [dt] milliseconds. Returns false once it is over and the bar shows its words again: after the way home, after a
     * game that plays once, or after a loop's own way home once it has played [LOOP_MILLIS].
     */
    fun update(dt: Float): Boolean {
        if (!game.returning) {
            played += dt
            if (kind.loops && played >= LOOP_MILLIS) {
                comeBack(if (game is Juggle) Way.Drop else Way.Exit)
                return true
            }
        }
        return game.update(dt)
    }

    /**
     * You came back. Juggled letters are dropped; any other game goes home quickly, or now and then by way of a Shell game or a
     * Firework, so that it stays a surprise and never gets tiring.
     */
    fun comeBack(way: Way? = null) {
        if (game.returning) return
        val current = game
        val figure = current.figure
        if (way != null) {
            // The debug build's trigger asks for a way home by name, to show each one.
            game = when (way) {
                Way.Shell -> Shell(glyphs, width, random)
                Way.Firework -> Firework(glyphs, width, random)
                Way.Drop -> Drop(glyphs, width, random, figure, (current as? Juggle)?.balls ?: glyphs.take(3))
                Way.Exit -> Exit(glyphs, width, random, figure)
            }
            return
        }
        game = when {
            current is Juggle -> Drop(glyphs, width, random, figure, current.balls)
            figure != null -> Exit(glyphs, width, random, figure)
            else -> when (random.nextInt(4)) {
                0 -> if (words(glyphs) == 3) Shell(glyphs, width, random) else Exit(glyphs, width, random, null)
                1 -> Firework(glyphs, width, random)
                else -> Exit(glyphs, width, random, null)
            }
        }
    }

    companion object {
        /** How often an idle time starts a game at all; the rest of the time nothing plays, and the next wait starts. */
        const val PLAY_CHANCE = .25f
        /** The longest a looping game plays before it goes home by itself. */
        const val LOOP_MILLIS = 30_000f
        const val COMMON = 30
        const val RARE = 10
        /** The letters that roll well enough to be Bowling's ball. */
        const val ROUND = "oaedgbpq"

        private fun words(glyphs: List<Glyph>) = glyphs.map { it.word }.distinct().size
        private fun word(glyphs: List<Glyph>, w: Int) = glyphs.filter { it.word == w }

        /** Bowling's ball: a round letter, the roundest first. */
        fun ballOf(glyphs: List<Glyph>): Glyph? {
            for (char in ROUND) glyphs.firstOrNull { it.char == char }?.let { return it }
            return null
        }

        /** Whether the bar's letters fit [kind]: a game that needs letters the bar does not have is skipped. */
        fun fits(kind: Kind, glyphs: List<Glyph>): Boolean = glyphs.isNotEmpty() && when (kind) {
            Kind.Drift, Kind.Firework -> true
            Kind.Pong -> word(glyphs, 0).isNotEmpty() && word(glyphs, 2).isNotEmpty() && word(glyphs, 1).isNotEmpty()
            Kind.Bowling -> ballOf(glyphs) != null && glyphs.size >= 4
            Kind.JumpRope, Kind.Race -> words(glyphs) == 3
            Kind.Juggle -> word(glyphs, 1).size == 3
        }

        /** A game for these letters, picked by rarity among the ones they fit, or null if none fits. */
        fun choose(glyphs: List<Glyph>, random: Random, allowed: (Kind) -> Boolean = { true }): Kind? {
            val fitting = Kind.entries.filter { allowed(it) && fits(it, glyphs) }
            val total = fitting.sumOf { it.weight }
            if (total == 0) return null
            var pick = random.nextInt(total)
            for (kind in fitting) { pick -= kind.weight; if (pick < 0) return kind }
            return fitting.last()
        }

        /** Whether this idle time starts a game at all. */
        fun shouldPlay(random: Random) = random.nextFloat() < PLAY_CHANCE
    }
}

/**
 * The idle times so far, so that games stay a surprise: the idle time right after a game never plays one, and a game waits at
 * least [GAP] more idle times before it plays again. Only the real idle times count, not the debug build's trigger.
 */
internal class IdleSchedule {
    private var count = 0
    private var lastGame = Int.MIN_VALUE / 2
    private val last = HashMap<IdlePlayer.Kind, Int>()

    /** An idle time is reached. Returns whether it may play a game: not right after one, and only by [IdlePlayer.PLAY_CHANCE]. */
    fun trigger(random: Random): Boolean {
        count++
        if (count - lastGame <= 1) return false
        return IdlePlayer.shouldPlay(random)
    }

    /** Whether [kind] may play at this idle time: at least [GAP] idle times have passed since it last played. */
    fun allows(kind: IdlePlayer.Kind) = count - (last[kind] ?: (Int.MIN_VALUE / 2)) > GAP

    /** [kind] plays at this idle time. */
    fun played(kind: IdlePlayer.Kind) { lastGame = count; last[kind] = count }

    companion object {
        const val GAP = 2
    }
}
