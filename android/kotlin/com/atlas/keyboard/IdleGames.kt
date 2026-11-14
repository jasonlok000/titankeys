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
    /**
     * The bar's line, in its units: 39 of its 56 dp, the middle of the bar and the 22 dp black border under the screen, which reads
     * as part of it ([StripFit.LINE_DP], W, 2026-11-13). The words' letters sit on it, so the games start and end there.
     */
    const val LINE = H * 39f / 56f
    /**
     * The lowest that what shows may reach: 2.56 dp over the screen's edge ([StripFit.EDGE_DP]), as the bar's own text keeps, so
     * nothing touches the edge or the corners' curve within 4 px. The floor the figure stands on, and that letters land on.
     */
    const val FLOOR = H * (56f - 2.56f) / 56f
    /** The highest: the bar's top cuts whatever crosses it. */
    const val TOP = .5f

    /**
     * How far a letter's ink reaches over its middle (the middle of Inter's ascent and descent, where a [Glyph] is placed), as a
     * share of its size: capitals, digits and ascenders reach about 0.39, small letters 0.19. A hundredth more, to be sure.
     */
    fun inkUp(char: Char) = if (char.isUpperCase() || char.isDigit() || char in TALL) .41f else .21f
    /** How far a letter's ink reaches under its middle, as a share of its size: about 0.58 with a descender, otherwise 0.38. */
    fun inkDown(char: Char) = if (char in DESCENDERS) .59f else .39f
    private const val TALL = "bdfhijklt!?'\"/\\|()[]{}#%&@$"
    private const val DESCENDERS = "gjpqyQ,;()[]{}|/\\@$"

    /** The [Glyph.char] of the automatic capital's arrow, which is drawn as a shape, not a letter. */
    const val ARROW = '\u21E7'
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

    /**
     * Two letters that touch push apart and trade speed. A heavier letter loses less, and a glancing hit sets them spinning. [push]
     * is how much of their overlap they undo in this step: less than all eases letters that start on top of each other apart over a
     * few frames, instead of snapping them.
     */
    fun collide(a: Glyph, b: Glyph, restitution: Float, push: Float = 1f): Boolean {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val d = hypot(dx, dy)
        val least = a.radius + b.radius
        if (d >= least || d == 0f) return false
        val nx = dx / d
        val ny = dy / d
        val overlap = (least - d) * push
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
 *
 * The automatic capital's arrow, while it shows, plays as one more piece: an [arrow], [advance] wide and tall, that goes with the
 * left word, first in it. The games use it where they can (a pin, Pong's ball when the middle has no letter, a racer, a drifter),
 * and it always comes home to its own place.
 */
internal class Glyph(val char: Char, val word: Int, val homeX: Float, val homeY: Float, val advance: Float, val size: Float,
    val arrow: Boolean = false) {
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

    /**
     * How far its ink reaches over and under [y] at full size, and half its width: a box around the letter. The arrow is a square
     * [advance] wide, and its line reaches a little past it.
     */
    val inkUp = if (arrow) advance * ARROW_REACH else size * Idle.inkUp(char)
    val inkDown = if (arrow) advance * ARROW_REACH else size * Idle.inkDown(char)
    val inkHalf = if (arrow) advance * ARROW_REACH else advance / 2 + size * .04f
    /** Where it turns, at full size: its ink's middle, this far under [y], so a turning letter stays put. [IdleDrawing] turns it there. */
    val pivot = (inkDown - inkUp) / 2
    private val halfHigh = (inkUp + inkDown) / 2

    /** How far its ink reaches under [y] at [scale] and turned by [turn]: its box, turned about [pivot]. */
    fun below(scale: Float = this.scale, turn: Float = rot) = (pivot + abs(sin(turn)) * inkHalf + abs(cos(turn)) * halfHigh) * scale
    /** How far its ink reaches over [y] at [scale] and turned by [turn]. */
    fun above(scale: Float = this.scale, turn: Float = rot) = (-pivot + abs(sin(turn)) * inkHalf + abs(cos(turn)) * halfHigh) * scale
    /** The most its ink reaches from its pivot at [scale], at any turn: for a letter that spins. */
    fun turning(scale: Float = this.scale) = hypot(inkHalf, halfHigh) * scale
    /** The lowest [y] may be at [scale], turning freely, for its ink to stay over [Idle.FLOOR]. */
    fun spinFloor(scale: Float = this.scale) = Idle.FLOOR - pivot * scale - turning(scale)
    /** The highest [y] may be at [scale], turning freely, for its ink to stay under [Idle.TOP]. */
    fun spinCeiling(scale: Float = this.scale) = Idle.TOP - pivot * scale + turning(scale)

    /**
     * On its way to a place on the line: a letter that was lying on the floor stands up as it goes, and its ink stays over the floor
     * while it turns, even if that holds it a little over its place. Upright and full size at its place, its ink is over the floor
     * already, so it still ends exactly there.
     */
    fun overFloor() {
        y = min(y, Idle.FLOOR - below())
    }

    /** Back home, at rest, as on the bar. */
    fun reset() {
        x = homeX; y = homeY; rot = 0f; scale = 1f; alpha = 1f; accent = false
        vx = 0f; vy = 0f; spin = 0f; radius = size * .32f; mass = 1f
    }

    private companion object {
        /** Half the arrow's size, and its line's half width, about a tenth of its size. */
        const val ARROW_REACH = .6f
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
        /**
         * Where the figure walks on from, in from the game's side, fading in over [FADE_IN] ms, and how long it fades as it walks off:
         * from further out, its feet would cross the screen's rounded corner.
         */
        const val ENTER = 14f
        const val FADE_IN = 260f
        const val FADE_OUT = 380f
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
            g.overFloor()
            if (p > .6f) g.accent = false
        }
        if (done) glyphs.forEach { it.reset() }
        return done
    }
}

// The games that play while idle, and loop until you come back.

/**
 * 1. Gravity off: the letters drift like a screensaver logo, bouncing off each other and off every edge of the bar: its top, and its
 * floor over the screen's edge ([Idle.FLOOR]), each letter's ink inside them however it turns.
 */
internal class Drift(glyphs: List<Glyph>, width: Float, random: Random) : IdleGame(glyphs, width, random) {
    private var t = 0f
    private val speed = FloatArray(glyphs.size) { random.nextFloat() * (SPEED_MAX - SPEED_MIN) + SPEED_MIN }
    private val heading = FloatArray(glyphs.size) { random.nextFloat() * 2 * PI.toFloat() }
    // Where each letter bounces. A big letter on the line starts lower than a turning letter may go: its start is its floor until
    // it rises past the real one, so nothing jumps. The same at the sides, for a letter that starts past the game's edge.
    private val floors = FloatArray(glyphs.size) { max(glyphs[it].spinFloor(MOST), glyphs[it].y) }
    private val ceilings = FloatArray(glyphs.size) { min(glyphs[it].spinCeiling(MOST), glyphs[it].y) }
    private val lefts = FloatArray(glyphs.size) { min(glyphs[it].radius, glyphs[it].x) }
    /** Neighbours in a word that start overlapping: they ease apart rather than snap, until they're clear of each other. */
    private val easing = HashSet<Long>().apply {
        for (i in glyphs.indices) for (j in i + 1 until glyphs.size)
            if (hypot(glyphs[i].x - glyphs[j].x, glyphs[i].y - glyphs[j].y) < glyphs[i].radius + glyphs[j].radius) add(i * 1000L + j)
    }
    private val rights = FloatArray(glyphs.size) { max(width - glyphs[it].radius, glyphs[it].x) }

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
                floors[i] = max(g.spinFloor(MOST), minOf(floors[i], g.y, Idle.FLOOR - g.below()))
                ceilings[i] = min(g.spinCeiling(MOST), maxOf(ceilings[i], g.y, Idle.TOP + g.above()))
                lefts[i] = min(g.radius, max(lefts[i], g.x)); rights[i] = max(width - g.radius, min(rights[i], g.x))
                if (g.x < lefts[i]) { g.x = lefts[i]; g.vx = abs(g.vx); g.spin += random.nextFloat() * .6f - .3f }
                if (g.x > rights[i]) { g.x = rights[i]; g.vx = -abs(g.vx); g.spin += random.nextFloat() * .6f - .3f }
                if (g.y < ceilings[i]) { g.y = ceilings[i]; g.vy = abs(g.vy) }
                if (g.y > floors[i]) { g.y = floors[i]; g.vy = -abs(g.vy) }
            }
            for (i in glyphs.indices) for (j in i + 1 until glyphs.size) {
                val key = i * 1000L + j
                val touching = Idle.collide(glyphs[i], glyphs[j], 1f, if (key in easing) EASE_APART else 1f)
                if (!touching) easing -= key
            }
            // A collision may push a letter past its floor or ceiling: it stays inside.
            for ((i, g) in glyphs.withIndex()) g.y = Idle.clamp(g.y, ceilings[i], floors[i])
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
        /** The most a letter grows as it lifts off. */
        const val MOST = 1.06f
        /** How much of their overlap neighbours that start on top of each other undo each step. */
        const val EASE_APART = .03f
        const val SPEED_MIN = 55f
        const val SPEED_MAX = 105f
    }
}

/**
 * 2. Pong: the side words stand up as paddles, a round letter of the middle word is the ball, and the rest of the middle word is
 * an obstacle that moves up and down. The ball plays between the bar's top and its floor ([Idle.FLOOR]); the paddles rest on the
 * line as far as they fit, and the obstacle sways about the line, raised only as much as its sway needs to stay over the floor.
 */
internal class Pong(glyphs: List<Glyph>, width: Float, random: Random) : IdleGame(glyphs, width, random) {
    private class Paddle(val list: List<Glyph>, val x: Float, val sign: Float, val scale: Float, val half: Float, val offsets: FloatArray) {
        /** Where it rests, and waits: where its letters' middles are on the line, or as near as it fits over the floor. */
        val rest = Idle.clamp(if (list.isEmpty()) Idle.LINE else list.sumOf { it.homeY.toDouble() }.toFloat() / list.size,
            Idle.TOP + half, Idle.FLOOR - half)
        var y = rest
        var vy = 0f
        fun clampY(y: Float) = Idle.clamp(y, Idle.TOP + half, Idle.FLOOR - half)
    }

    private val middle = words(1)
    private val ball = Idle.roundest(middle.ifEmpty { glyphs.filter { it.arrow } }.ifEmpty { glyphs })!!
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
    /** Where the spinning ball's middle may go, its ink inside the bar. */
    private val ballTop = ball.spinCeiling(BALL_SCALE)
    private val ballFloor = ball.spinFloor(BALL_SCALE)
    /** The obstacle's letters' home, and where it sways about: as near the line as its sway allows. */
    private val wallHome = if (wall.isEmpty()) Idle.LINE else wall.sumOf { it.homeY.toDouble() }.toFloat() / wall.size
    private val wallRest: Float
    /** The middle of the obstacle's ink at rest, which its box is around. */
    private val wallMiddle: Float
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
        val under = wall.maxOfOrNull { it.homeY - wallHome + it.below(WALL_SCALE, 0f) } ?: 0f
        val over = wall.maxOfOrNull { wallHome - it.homeY + it.above(WALL_SCALE, 0f) } ?: 0f
        wallRest = Idle.clamp(wallHome, Idle.TOP + over + SWAY + JOLT, Idle.FLOOR - under - SWAY - JOLT)
        wallMiddle = wallRest + (wall.sumOf { it.pivot.toDouble() }.toFloat() / max(wall.size, 1)) * WALL_SCALE
        paddles = listOf(paddle(words(0).filter { it !== ball }, X_SIDE, -1f), paddle(words(2), width - X_SIDE, 1f))
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
            g.rot = Idle.lerp(0f, pd.sign * PI.toFloat() / 2, p); g.scale = Idle.lerp(1f, pd.scale, p)
            // Half turned, a letter reaches lowest: its ink stays over the floor as it lines up.
            g.x = Idle.lerp(fromX[k], pd.x, p); g.y = Idle.lerp(fromY[k], pd.y - pd.offsets[i] * pd.sign, p)
            g.overFloor()
        }
    }

    /** A shot towards the side [sign]: mostly over or under the wall, now and then straight at it. */
    private fun aim(sign: Float) {
        val speed = BALL_SPEED * (.95f + random.nextFloat() * .13f)
        // Under the obstacle only where there's room between it and the floor.
        val underRoom = ballFloor - 3 > wallMiddle + WALL_HALF + SWAY + JOLT + ballRadius
        val lane = if (wall.isEmpty()) ballTop + random.nextFloat() * (ballFloor - ballTop)
            else if (random.nextFloat() < OVER_CHANCE) (if (random.nextFloat() < .5f || !underRoom) ballTop + 3 else ballFloor - 3)
            else wallMiddle + sway + random.nextFloat() * 12 - 6
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
            // The arrow, as the ball, starts at the bar's left, behind where the paddle stands: it hops into the court.
            val court = Idle.clamp(fromX[k], X_SIDE + 10 + ballRadius + 4, width - X_SIDE - 10 - ballRadius - 4)
            ball.x = Idle.lerp(fromX[k], court, q); ball.y = fromY[k] - sin(q * PI.toFloat()) * 22; ball.accent = true; ball.scale = Idle.lerp(1f, BALL_SCALE, q)
            for (g in wall) {
                g.alpha = Idle.lerp(1f, .55f, q); g.scale = Idle.lerp(1f, WALL_SCALE, p); g.x = Idle.lerp(g.homeX, wallXs[g]!!, p)
                g.y = g.homeY + (wallRest - wallHome) * p
            }
            if (t + dt >= INTRO) { ball.radius = ballRadius; aim(direction) }
            return true
        }
        var left = dt
        while (left > 0) {
            val h = min(Idle.STEP, left) / 1000
            left -= Idle.STEP
            ball.x += ball.vx * h; ball.y += ball.vy * h; ball.rot += ball.spin * h
            if (ball.y < ballTop) { ball.y = ballTop; ball.vy = abs(ball.vy) }
            if (ball.y > ballFloor) { ball.y = ballFloor; ball.vy = -abs(ball.vy) }
            for (pd in paddles) {
                val coming = pd.sign * ball.vx > 0
                val edge = pd.x - pd.sign * 10
                // A paddle glides on a spring to where the ball will arrive, after its bounces; when the ball goes away it drifts
                // back to the middle.
                val want = if (coming) Idle.fold(ball.y + ball.vy * abs(edge - ball.x) / abs(ball.vx), ballTop, ballFloor) else pd.rest
                pd.vy += ((want - pd.y) * SPRING - pd.vy * DAMP) * h
                pd.y = pd.clampY(pd.y + pd.vy * h)
                val reached = if (pd.sign < 0) ball.x - ball.radius <= edge else ball.x + ball.radius >= edge
                if (reached && coming) { ball.x = edge - pd.sign * ball.radius; aim(-pd.sign) }
            }
            // The obstacle sways up and down, slowly; the ball bounces off its box, and the word gives a little.
            sway = SWAY * sin((t / SWAY_PERIOD) * 2 * PI.toFloat()) * Idle.smooth((t - INTRO) / 600)
            if (wall.isNotEmpty()) {
                val y0 = wallMiddle - WALL_HALF + joltY + sway
                val y1 = wallMiddle + WALL_HALF + joltY + sway
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
        for (g in wall) { g.x = wallXs[g]!!; g.y = g.homeY + (wallRest - wallHome) + joltY + sway }
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
        /** How far a hit may knock the obstacle from its sway. */
        const val JOLT = 5f
    }
}

/**
 * 3. Bowling: the figure rolls a round letter at the other letters, stacked like pins; they scatter and go home. Each round ends.
 * The lane is the bar's floor ([Idle.FLOOR]): the figure stands on it, and every letter's ink lands on it, however it lies.
 */
internal class Bowling(glyphs: List<Glyph>, width: Float, random: Random) : IdleGame(glyphs, width, random) {
    private enum class Phase { Intro, Windup, Roll, Scatter, Cheer, Home }

    private val ball = IdlePlayer.ballOf(glyphs)!!
    private val pins = glyphs.filter { it !== ball }
    private val fromX = FloatArray(glyphs.size) { glyphs[it].x }
    private val fromY = FloatArray(glyphs.size) { glyphs[it].y }
    private val spotX: FloatArray
    private val spotY: FloatArray
    private val roll = 560 + random.nextFloat() * 140
    override val figure = Figure(Figure.ENTER, FEET).also { it.alpha = 0f }
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
        // Each row a little lower than a pin is tall, so the stack leans together; the bottom row stands on the floor, and the top
        // row stays under the bar's top.
        val rise = (pins.maxOfOrNull { it.size } ?: Idle.FONT_SIZE) * PIN_SCALE * .62f
        for (r in 0 until rows) for (c in 0 until bottom - r) if (xs.size < pins.size) {
            val pin = pins[xs.size]
            xs += width * .8f - (bottom - 1) * gap + c * gap + r * gap / 2
            ys += max(FLOOR - pin.below(PIN_SCALE, 0f) - r * rise, Idle.TOP + pin.above(PIN_SCALE, 0f))
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
                figure.x = Idle.lerp(Figure.ENTER, 90f, p); figure.step = p * 9; figure.alpha = Idle.smooth(t / Figure.FADE_IN)
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
                    if (phase == Phase.Roll && ball.vy == 0f) ball.y = FLOOR - ball.below()
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
                            for (i in pins.indices) for (j in i + 1 until pins.size) Idle.collide(pins[i], pins[j], .2f, SETTLE_PUSH)
                        }
                        for (p in pins) if (p.y + p.below() >= FLOOR - .5f) p.rot += (round(p.rot / PI.toFloat() * 2) * PI.toFloat() / 2 - p.rot) * .02f
                        // A collision may push a letter, or a turn make it reach further: its ink stays between the floor and the top.
                        for (b in pins + ball) {
                            if (b.y + b.below() > FLOOR) b.y = FLOOR - b.below()
                            if (b.y - b.above() < Idle.TOP) b.y = Idle.TOP + b.above()
                        }
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
                figure.feet = FEET - abs(sin(cheer / 160 * PI.toFloat())) * 6
                figure.armLX = -10f; figure.armLY = -22f; figure.armRX = 10f; figure.armRY = -22f
                if (cheer > 640) {
                    phase = Phase.Home; t = 0f; outro = HomeTween(glyphs, 700f, 10f)
                    figure.feet = FEET; figure.armLX = -12f; figure.armLY = 16f; figure.armRX = 12f; figure.armRY = 16f
                }
            }
            Phase.Home -> {
                // Everything back to the bar, and the figure walks off. Then the next round.
                figure.feet = FEET
                figure.x -= dt * .25f; figure.step += dt / 70; figure.alpha = max(0f, 1 - t / Figure.FADE_OUT)
                return !(outro!!.update(dt) && t > 600)
            }
        }
        // Leaning, the figure's trailing foot would dip under the floor: it stands as much higher. Cheering, it hops.
        if (phase != Phase.Cheer) figure.feet = FEET - abs(sin(figure.lean)) * 10
        return true
    }

    private fun move(b: Glyph, h: Float) {
        b.vy += GRAVITY * h; b.x += b.vx * h; b.y += b.vy * h; b.rot += (if (b === ball) b.vx / b.radius else b.spin) * h
        if (b.y + b.below() > FLOOR) {
            // The ball rolls on with little loss; pins slide and stop quickly.
            b.y = FLOOR - b.below(); b.vy = -b.vy * .3f; if (abs(b.vy) < 20) b.vy = 0f
            b.vx *= exp(-(if (b === ball) .3f else 4f) * h); b.spin *= exp(-6 * h)
        }
        if (b.x > width - b.radius) { b.x = width - b.radius; b.vx = -b.vx * .4f }
        if (b.y - b.above() < Idle.TOP) { b.y = Idle.TOP + b.above(); b.vy = abs(b.vy) * .3f }
    }

    private companion object {
        const val FLOOR = Idle.FLOOR
        /** Where the figure's feet are: its line's round end touches the floor. */
        const val FEET = Idle.FLOOR - Figure.LINE / 2
        const val GRAVITY = 1100f
        const val BALL_MASS = 4f
        const val STRIKE = 340f
        const val STRIKE_REACH = 200f
        const val SETTLE_AFTER = 260f
        /** How much of their overlap pins lying together undo each step, so they spread out instead of snapping apart. */
        const val SETTLE_PUSH = .06f
        const val WALK = 700f
        const val WINDUP = 380f
        const val PIN_SCALE = .82f
    }
}

/**
 * 5. Jump rope: a letter at each side swings a rope, and the middle word jumps it. Everyone stays on the line: the holders step
 * apart along it, the jumper jumps up from it, and the rope swings from over the jumper's head to the floor ([Idle.FLOOR]).
 */
internal class JumpRope(glyphs: List<Glyph>, width: Float, random: Random) : IdleGame(glyphs, width, random) {
    private val holderL = words(0).firstOrNull { !it.arrow } ?: glyphs.first()
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
    /** The rope's middle, as it swings: at the top over the jumper's head, at the bottom on the floor, inside the bar. */
    val ropeMid get() = Idle.lerp(ropeY + 6, Idle.lerp(max(ropeY - 40, Idle.TOP + ROPE), min(ropeY + 36, Idle.FLOOR - ROPE),
        (1 + cos(ropeAngle)) / 2), ropeAmount)
    /** Each holder's place on the line, raised only if its letter, rocking, would reach the floor. */
    private val holdAt = listOf(holderL, holderR).map { min(it.homeY, Idle.FLOOR - it.below(1f, ROCK)) }

    override fun update(dt: Float): Boolean {
        t += dt
        val p = Idle.easeInOut(t / INTRO)
        for ((i, pair) in listOf(holderL to width * .27f, holderR to width * .73f).withIndex()) {
            val (g, x) = pair
            val k = glyphs.indexOf(g)
            g.x = Idle.lerp(fromX[k], x, p); g.y = Idle.lerp(fromY[k], holdAt[i], p)
        }
        // The other side letters make room and watch.
        // The rest step aside, but never past the ends of the bar, where the screen's corners would cut them.
        for (g in rest) {
            val k = glyphs.indexOf(g)
            val aside = Idle.clamp(fromX[k] + if (g.word == 0) -50 else 50, min(fromX[k], g.advance / 2), max(fromX[k], width - g.advance / 2))
            g.x = Idle.lerp(fromX[k], aside, p); g.alpha = Idle.lerp(1f, .32f, p)
        }
        val speed = (2 * PI.toFloat() / period) * Idle.smooth((t - 300) / INTRO)
        ropeAngle += speed * dt
        holderL.rot = ROCK * sin(ropeAngle) * p; holderR.rot = -ROCK * sin(ropeAngle) * p
        // The jumper is in the air while the rope passes under it, highest when the rope is lowest, and it squashes on landing.
        val u = Idle.wrapAngle(ropeAngle)
        val air = abs(u) < 1.05f && speed > 0
        val height = if (air) JUMP * cos(u / 1.05f * PI.toFloat() / 2) * Idle.smooth((t - 300) / INTRO) else 0f
        if (!air && landed < 0 && speed > 0 && abs(u) < 1.4f) landed = t
        if (air) landed = -1f
        val squash = if (landed >= 0) sin(Idle.clamp((t - landed) / 140, 0f, 1f) * PI.toFloat()) * .12f else 0f
        for ((i, g) in jumper.withIndex()) {
            g.y = g.homeY - height; g.scale = 1 - squash * .5f; g.rot = if (air) sin(u) * .05f * (if (i % 2 == 1) 1 else -1) else 0f
        }
        ropeAmount = Idle.smooth((t - 200) / INTRO)
        return true
    }

    private companion object {
        const val INTRO = 1000f
        const val JUMP = 24f
        const val HAND = 14f
        /** How far the holders rock as they swing, and half the rope's width. */
        const val ROCK = .35f
        const val ROPE = 3f
    }
}

/**
 * 7. Race: the words line up in three lanes and race to a finish line; one wins, and they all go home. Each race ends. The lanes
 * stack up from the bar's floor ([Idle.FLOOR]), a lane's height apart, as low as the letters' ink allows.
 */
internal class Race(glyphs: List<Glyph>, width: Float, random: Random) : IdleGame(glyphs, width, random) {
    private enum class Phase { LineUp, Ready, Race, Finish, Home }
    private class Racer(var front: Float, val top: Float, val surgeA: Float, val surgeB: Float) {
        var speed = 0f
        var done = false
    }

    private val lanes = FloatArray(3)
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
    /** The finish line's ends, with its round caps inside the bar. */
    val lineTop = Idle.TOP + 3
    val lineBottom = Idle.FLOOR - 3

    init {
        // Each lane is the height of its letters at the winner's size, and leaves room for the crouch under it and the hop over it.
        fun below(w: Int) = words(w).maxOfOrNull { it.below(SCALE * WIN, 0f) } ?: 0f
        fun above(w: Int) = words(w).maxOfOrNull { it.above(SCALE * WIN, 0f) } ?: 0f
        lanes[2] = Idle.FLOOR - below(2) - CROUCH
        val apart = (0..2).maxOf { below(it) + above(it) } + 2
        val room = (lanes[2] - (Idle.TOP + above(0) + HOP_UP + BOB)) / 2
        val gap = min(apart, room)
        lanes[1] = lanes[2] - gap
        lanes[0] = lanes[1] - gap
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
            val bob = if (phase == Phase.Race) abs(sin(t / 55 + i)) * -BOB * (r.speed / r.top) else 0f
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
                glyphs.forEach { it.y += crouch * CROUCH; it.scale = SCALE * (1 - crouch * .06f) }
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
                    val hop = abs(sin(p / 140 * PI.toFloat())) * HOP_UP * (if (p < 560) 1 else 0)
                    for (g in words(winner)) { g.accent = true; g.y -= hop; g.scale = SCALE * (1 + (WIN - 1) * Idle.smooth(p / 200)) }
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
        /** How much the winner grows, how high it hops, how far the racers crouch before the start and bob as they run. */
        const val WIN = 1.12f
        const val HOP_UP = 6f
        const val CROUCH = 3f
        const val BOB = 2.5f
        const val START = 22f
        const val LINE_UP = 750f
        const val READY = 3
        const val HOP = 230f
    }
}

/**
 * 8. Juggling: the figure walks on and juggles the middle word's three letters in a three-ball cascade; the side words stay put.
 * The figure stands on the bar's floor ([Idle.FLOOR]), and the throws go as high as they can with the letters under the bar's top.
 */
internal class Juggle(glyphs: List<Glyph>, width: Float, random: Random) : IdleGame(glyphs, width, random) {
    val balls: List<Glyph>
    val others: List<Glyph>
    private val centre = width / 2
    private val feet = Idle.FLOOR - Figure.LINE / 2
    private val beat = 300 + random.nextFloat() * 40
    private val fromX = FloatArray(glyphs.size) { glyphs[it].x }
    private val fromY = FloatArray(glyphs.size) { glyphs[it].y }
    override val figure = Figure(width - Figure.ENTER, feet).also { it.alpha = 0f }
    private var t = 0f
    private var playing = 0f
    private val handY = feet - Figure.NECK + 6 + 18
    /** How high a throw goes: [HEIGHT], or less, so that no spinning letter at its top reaches the bar's top. */
    private val height: Float

    init {
        // The middle word's three letters; the side words stay where they are.
        balls = words(1)
        others = glyphs.filter { it.word != 1 }
        height = min(HEIGHT, handY - 6 - (balls.maxOfOrNull { it.spinCeiling(BALL) } ?: 0f))
    }

    private fun handX(side: Float, phase: Float) = centre + side * (HAND_SPREAD + sin(phase * PI.toFloat() * 2) * 4 * side * .5f)
    private fun handY(phase: Float) = handY + abs(cos(phase * PI.toFloat())) * 3

    override fun update(dt: Float): Boolean {
        t += dt
        if (t < WALK + 500) {
            val p = Idle.easeInOut(t / WALK)
            figure.x = Idle.lerp(width - Figure.ENTER, centre, p); figure.step = p * 12; figure.alpha = Idle.smooth(t / Figure.FADE_IN)
            // Each ball hops from its word into the figure's hands, one after another.
            for ((i, g) in balls.withIndex()) {
                val k = glyphs.indexOf(g)
                val q = Idle.easeInOut((t - WALK + 200 - i * 120) / 420)
                val side = if (i % 2 == 1) 1f else -1f
                g.x = Idle.lerp(fromX[k], handX(side, 0f), q); g.y = Idle.lerp(fromY[k], handY(0f) - 6, q) - sin(q * PI.toFloat()) * 24
                g.scale = Idle.lerp(1f, BALL, q)
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
                g.y = Idle.lerp(handY(s), handY(0f), s) - 6 - 4 * height * s * (1 - s); g.rot += dt / 1000 * 7 * -fromSide
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
        /** The letters' size in the air. */
        const val BALL = .68f
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
            g.overFloor()
            if (p > .5f) g.accent = false
        }
        // The figure fades where it stands: sinking, it would cross the screen's edge.
        figure?.let { it.alpha = 1 - Idle.easeOut(t / 180) }
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
                    if (g.y + g.below() > Idle.FLOOR) { g.y = Idle.FLOOR - g.below(); g.vy = -g.vy * .35f; g.vx *= .7f }
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

/**
 * 4. Shell game, a way home: the three words shrink a little and swap places, faster then slower, and end where they started. The
 * words rise off the line only as far as the word passing under needs to stay over the floor ([Idle.FLOOR]), and the arcs shrink
 * if a big size leaves no room for both.
 */
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
    /** How far a word passes over or under another, and how far the words rise off the line while they play. */
    private val arc: Float
    private val rise: Float

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
        val low = glyphs.maxOf { it.homeY + it.below(SCALE, 0f) }
        val high = glyphs.minOf { it.homeY - it.above(SCALE * 1.08f, 0f) }
        arc = max(0f, min(ARC, ((Idle.FLOOR - low) + (high - Idle.TOP)) / 2))
        rise = min(0f, Idle.FLOOR - low - arc)
    }

    /** Each word at its x, raised by y, at a scale and alpha; [blend] mixes in from where the letters were when this began. */
    private fun drawWords(blend: Float) {
        for ((i, g) in glyphs.withIndex()) {
            val w = g.word
            val x = ox[w] + (g.homeX - centres[w]) * os[w]
            g.x = Idle.lerp(fromX[i], x, blend); g.y = Idle.lerp(fromY[i], g.homeY + oy[w], blend)
            g.scale = Idle.lerp(fromScale[i], os[w], blend); g.alpha = Idle.lerp(fromAlpha[i], oa[w], blend)
            g.rot = Idle.lerp(fromRot[i], 0f, blend)
            g.overFloor()
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
                for (w in 0..2) set(w, centres[w], rise * p - hop * 8, Idle.lerp(1f, SCALE, p), 1f)
                drawWords(p)
                if (t >= INTRO) { phase = 1; t = 0f }
            }
            1 -> {
                val (a, b) = swaps[index].let { it[0] to it[1] }
                val p = Idle.easeInOut(t / durations[index])
                val arc = sin(p * PI.toFloat())
                val wa = place[a]
                val wb = place[b]
                for (w in 0..2) set(w, centres[at[w]], rise, SCALE, 1f)
                // The word going right passes over, the one going left passes under; a word they pass by ducks and dims.
                val right = if (centres[b] > centres[a]) 1f else -1f
                set(wa, Idle.lerp(centres[a], centres[b], p), rise - right * arc * this.arc, SCALE * (1 + right * .08f * arc), 1 - (if (right < 0) .3f * arc else 0f))
                set(wb, Idle.lerp(centres[b], centres[a], p), rise + right * arc * this.arc, SCALE * (1 - right * .08f * arc), 1 - (if (right > 0) .3f * arc else 0f))
                if (abs(a - b) == 2) set(place[1], centres[1], rise, SCALE * (1 - .25f * arc), 1 - .5f * arc)
                drawWords(1f)
                if (t >= durations[index] + 40) {
                    at[wa] = b; at[wb] = a; place[a] = wb; place[b] = wa
                    t = 0f; index++
                    if (index >= swaps.size) phase = 2
                }
            }
            else -> {
                // Back to full size, with a small bounce as the words come to rest, and down onto the line, with none.
                val p = Idle.backOut(t / SETTLE)
                for (w in 0..2) set(w, centres[w], rise * (1 - Idle.easeInOut(t / SETTLE)), Idle.lerp(SCALE, 1f, p), 1f)
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
    /** Where the rocket waits: its ink a little over the bar's floor, and where it bursts: its ink under the bar's top. */
    private val ground = Idle.FLOOR - rocket.below(ROCKET, 0f) - 2
    private val apex = max(APEX, Idle.TOP + rocket.above(ROCKET, WOBBLE))
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
                        g.accent = true; g.alpha = 1f; g.rot = Idle.lerp(g.rot, 0f, .2f); g.scale = Idle.lerp(fromScale[i], ROCKET, Idle.smooth(t / GATHER))
                        g.overFloor()
                        continue
                    }
                    val p = Idle.easeIn((t - (i % 9) * 24) / (GATHER - 200))
                    g.x = Idle.lerp(fromX[i], launchX, p); g.y = Idle.lerp(fromY[i], ground, p)
                    g.scale = Idle.lerp(fromScale[i], .25f, p); g.alpha = fromAlpha[i] * (1 - p)
                    g.overFloor()
                }
                if (t >= GATHER) { phase = 1; t = 0f }
            }
            1 -> {
                val p = Idle.easeIn(t / LAUNCH)
                rocket.y = Idle.lerp(ground, apex, p); rocket.x = launchX + sin(t / 40) * 1.5f * (1 - p); rocket.rot = sin(t / 30) * WOBBLE
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
                        val ty = Idle.clamp(rocket.y + 26 + sin(a) * reach * 22, Idle.TOP + g.above(1f, 0f), Idle.FLOOR - g.below(1f, 0f))
                        g.x = rocket.x; g.y = rocket.y; g.vx = (tx - g.x) * DRAG; g.vy = (ty - g.y) * DRAG
                        g.spin = random.nextFloat() * 12 - 6; g.alpha = 1f; g.scale = .3f; g.accent = i % 3 == 0
                    }
                }
            }
            2 -> {
                // Grown first, so the soft floor and top below hold the letters at their size now.
                glyphs.forEach { it.scale = Idle.lerp(.3f, 1f, Idle.easeOut(t / 260)) }
                var left = dt
                while (left > 0) {
                    val h = min(Idle.STEP, left) / 1000
                    left -= Idle.STEP
                    val k = exp(-DRAG * h)
                    for (g in glyphs) {
                        g.vx *= k; g.vy = g.vy * k + GRAVITY * h * Idle.smooth(t / 600); g.x += g.vx * h; g.y += g.vy * h; g.rot += g.spin * h; g.spin *= k
                        // A soft floor, top and sides: the late fall settles on the bar's floor instead of dropping out.
                        if (g.y + g.below() > Idle.FLOOR) { g.y = Idle.FLOOR - g.below(); g.vy = -abs(g.vy) * .25f; g.vx *= .9f }
                        if (g.y - g.above() < Idle.TOP) { g.y = Idle.TOP + g.above(); g.vy = abs(g.vy) * .25f }
                        if (g.x < 12 || g.x > width - 12) { g.x = Idle.clamp(g.x, 12f, width - 12); g.vx = -g.vx * .3f }
                    }
                }
                if (t >= BURST) { phase = 3; t = 0f; home = HomeTween(glyphs, 900f, 14f) }
            }
        }
        for (s in sparks) { s.life += dt; s.x += s.vx * dt / 1000; s.y += s.vy * dt / 1000 }
        // A spark that reaches the bar's floor goes out there.
        sparks.removeAll { it.life > it.max || it.y > Idle.FLOOR - SPARK }
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
        /** The rocket's size, how far it wobbles as it climbs, and a spark's radius. */
        const val ROCKET = 1.1f
        const val WOBBLE = .05f
        const val SPARK = 2f
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
        /** How often an idle period's one roll starts a game ("The chance of triggering is 1/4"); else nothing plays till input. */
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
            Kind.Pong -> word(glyphs, 0).any { !it.arrow } && word(glyphs, 2).isNotEmpty() && (word(glyphs, 1).isNotEmpty() || glyphs.any { it.arrow })
            Kind.Bowling -> ballOf(glyphs) != null && glyphs.size >= 4
            Kind.JumpRope -> words(glyphs) == 3 && word(glyphs, 0).any { !it.arrow }
            Kind.Race -> words(glyphs) == 3
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

        /** Whether this idle period's roll starts a game at all. */
        fun shouldPlay(random: Random) = random.nextFloat() < PLAY_CHANCE
    }
}

/**
 * An idle period's clock: input starts a period, and exactly [MILLIS] later it rolls once, and never again in that period, however
 * long it lasts. The keyboard posts its roll for [MILLIS] from the input; this keeps the rule in one place the checks can run.
 */
internal class IdleClock {
    private var start: Long? = null
    private var rolled = false

    /** Input at [now] (uptime millis) starts a new period. */
    fun input(now: Long) {
        start = now
        rolled = false
    }

    /** The keyboard is hidden or the screen is off: no period runs until the next one starts. */
    fun stop() {
        start = null
    }

    /** Whether a period is running: input started one, and nothing stopped it since. Its roll may have come already. */
    val running get() = start != null

    /** Whether the period's one roll happens at [now]: true once, at or after its mark, and false after that until new input. */
    fun roll(now: Long): Boolean {
        val from = start ?: return false
        if (rolled || now - from < MILLIS) return false
        rolled = true
        return true
    }

    companion object {
        /** Three minutes, with no random extra. */
        const val MILLIS = 3 * 60 * 1000L
    }
}

/**
 * The idle periods that reached their roll, so that games stay a surprise (owner, 2026-11-13): each such period rolls once, the
 * one after a period that played a game never plays ("no two triggers in a row"), and a game is never the kind that played last
 * ("no two same animations in a row"). A period that input ends before its roll doesn't count. Only the real idle periods count,
 * not the debug build's trigger.
 */
internal class IdleSchedule {
    private var lastPlayed = false
    /** The kind that played last, or null before the first. */
    var lastKind: IdlePlayer.Kind? = null
        private set

    /**
     * An idle period reaches its roll, its only one. Returns whether it plays a game: never in the period right after one that
     * played, else by [IdlePlayer.PLAY_CHANCE].
     */
    fun trigger(random: Random): Boolean {
        val after = lastPlayed
        lastPlayed = false
        return !after && IdlePlayer.shouldPlay(random)
    }

    /** Whether [kind] may play now: any kind but the one that played last. */
    fun allows(kind: IdlePlayer.Kind) = kind != lastKind

    /** [kind] plays in this period. */
    fun played(kind: IdlePlayer.Kind) {
        lastPlayed = true
        lastKind = kind
    }
}
