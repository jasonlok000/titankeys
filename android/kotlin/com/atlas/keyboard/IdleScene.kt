package com.atlas.keyboard

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * The idle scene on the suggestion bar, an Easter egg: after a long while with no input, a small stick figure walks on, takes the
 * bar's words apart letter by letter and builds something with them right there, a word at a time. Then it walks off, and only
 * the build stays, still, until you do something. It never says anything.
 * This is the plan of the scene in time and place. It needs no Android, so the checks can play it with any clock. [Strip] draws it.
 *
 * Places are in pixels on the bar, from its left edge and its top. [unit] is the size of a small step, about 1 dp.
 */
internal class IdleScene(
    letters: List<Letter>,
    private val width: Float,
    private val height: Float,
    private val unit: Float,
    random: Random,
) {
    /** A letter of the bar, where it was drawn: [x] at its left, [y] on its baseline, and which word on the bar it is in. */
    data class Letter(val char: Char, val x: Float, val y: Float, val word: Int)

    enum class Pose { Walk, Stand, Pick, Fall }
    enum class Build { Tower, House, Sign, Rocket }

    /**
     * What the scene looks like at one moment. Each letter has a place and a size, from 1 for its size on the bar. The figure is
     * on the bar only while [figureShown].
     */
    class Frame(val figureX: Float, val figureShown: Boolean, val pose: Pose, val facingLeft: Boolean, val step: Float,
                val carrying: Int, val letters: List<Placed>)
    data class Placed(val char: Char, val x: Float, val y: Float, val scale: Float)

    val build: Build = Build.entries[random.nextInt(Build.entries.size)]
    private val speed = unit * BASE_SPEED * (0.8f + random.nextFloat() * 0.4f)
    private val offRight = width + FIGURE_WIDTH * unit * 2
    private val offLeft = -FIGURE_WIDTH * unit * 2

    /** A stretch of the scene: the figure goes from [fromX] to [toX] in [millis], in [pose]. */
    private class Part(val start: Long, val millis: Long, val fromX: Float, val toX: Float, val pose: Pose)

    private val parts = ArrayList<Part>()
    /** When each letter leaves the bar, when it lands in the build, and which piece of the build it is, in order. */
    private val pickedAt = LongArray(letters.size) { -1 }
    private val placedAt = LongArray(letters.size) { -1 }
    private val pieceOf = IntArray(letters.size) { -1 }
    private val homes = letters
    /** Where the build stands, and a place for each piece, bottom first. */
    private val site = width * (0.4f + random.nextFloat() * 0.15f)
    private val slots = shape(build, site, height, unit)
    /** How long the plan takes at the figure's own pace. */
    private val planned: Long
    /** How long the scene lasts: the plan, slowed down or sped up to fit between [MIN_MILLIS] and [MAX_MILLIS]. */
    val millis: Long

    init {
        var now = 0L
        fun move(toX: Float) {
            val fromX = parts.lastOrNull()?.toX ?: offRight
            val time = (abs(toX - fromX) / speed).toLong().coerceAtLeast(1)
            parts += Part(now, time, fromX, toX, Pose.Walk)
            now += time
        }
        fun stay(millis: Long, pose: Pose) {
            val x = parts.lastOrNull()?.toX ?: offRight
            parts += Part(now, millis, x, x, pose)
            now += millis
        }

        // The word where the build goes is taken first, to make room. The others follow in a random order.
        val words = letters.indices.groupBy { letters[it].word }.values.toMutableList()
        words.shuffle(random)
        words.sortBy { word -> if (word.any { abs(letters[it].x - site) < BUILD_WIDTH * unit * 2 }) 0 else 1 }
        var piece = 0
        for (word in words) {
            move(word.map { letters[it].x }.average().toFloat())
            for (index in word.reversed()) {
                pickedAt[index] = now
                stay(PICK_MILLIS + random.nextLong(PICK_SPREAD), Pose.Pick)
            }
            move(site - BUILD_GAP * unit)
            // Now and then a trip on the way, or a pause to think it over.
            if (random.nextFloat() < TRIP_CHANCE) stay(FALL_MILLIS, Pose.Fall)
            else if (random.nextFloat() < PAUSE_CHANCE) stay(PAUSE_MILLIS, Pose.Stand)
            for (index in word.shuffled(random)) {
                pieceOf[index] = piece++
                placedAt[index] = now
                stay(PLACE_MILLIS + random.nextLong(PLACE_SPREAD), Pose.Pick)
            }
        }
        // A look at the work, and off the way that is nearer.
        stay(LOOK_MILLIS, Pose.Stand)
        move(if (site < width / 2) offLeft else offRight)
        planned = now
        millis = planned.coerceIn(MIN_MILLIS, MAX_MILLIS)
    }

    /** The scene at [time], from 0. After [millis] it no longer changes: the build stays, and the figure is gone. */
    fun at(time: Long): Frame {
        val t = time.coerceIn(0, millis) * planned / millis.coerceAtLeast(1)
        val part = parts.lastOrNull { it.start <= t } ?: Part(0, 0, offRight, offRight, Pose.Stand)
        val along = if (part.millis <= 0) 1f else ((t - part.start).toFloat() / part.millis).coerceIn(0f, 1f)
        val x = part.fromX + (part.toX - part.fromX) * along
        val walking = part.pose == Pose.Walk && part.fromX != part.toX
        val step = if (walking) abs(x - part.fromX) / (STEP_LENGTH * unit) else 0f
        var carrying = 0
        val placed = ArrayList<Placed>()
        val headY = height - FIGURE_HEIGHT * unit
        for ((index, home) in homes.withIndex()) {
            val picked = pickedAt[index]
            val dropped = placedAt[index]
            when {
                picked < 0 || t < picked -> placed += Placed(home.char, home.x, home.y, 1f)
                t < picked + FLY_MILLIS -> {
                    // A letter flies from the bar into the bundle over the figure's head.
                    val f = (t - picked).toFloat() / FLY_MILLIS
                    placed += Placed(home.char, lerp(home.x, x, f), lerp(home.y, headY, f), lerp(1f, CARRIED_SCALE, f))
                }
                dropped < 0 || t < dropped -> carrying++
                else -> {
                    val (sx, sy) = place(pieceOf[index])
                    // From the bundle to its place, from where the figure stood when it put it there.
                    val f = ((t - dropped).toFloat() / FLY_MILLIS).coerceIn(0f, 1f)
                    val from = site - BUILD_GAP * unit
                    placed += Placed(home.char, lerp(from, sx, f), lerp(headY, sy, f), lerp(CARRIED_SCALE, BUILT_SCALE, f))
                }
            }
        }
        val shown = x > -FIGURE_WIDTH * unit && x < width + FIGURE_WIDTH * unit
        val facingLeft = part.toX < part.fromX || (part.fromX == part.toX && x > site)
        return Frame(x, shown, if (walking) Pose.Walk else part.pose, facingLeft, step, carrying, placed)
    }

    /** A piece's place: in the build, or past its last piece in a small heap beside it. */
    private fun place(piece: Int): Pair<Float, Float> {
        slots.getOrNull(piece)?.let { return it }
        val heap = piece - slots.size
        val cell = 4f * unit
        return Pair(site + (BUILD_WIDTH + 2) * unit + (heap % 4) * cell, max(cell * 2, height - 3 * unit - (heap / 4) * cell))
    }

    companion object {
        const val BASE_SPEED = .05f
        const val MIN_MILLIS = 20_000L
        const val MAX_MILLIS = 40_000L
        const val FIGURE_WIDTH = 8f
        const val FIGURE_HEIGHT = 26f
        const val STEP_LENGTH = 4f
        const val PICK_MILLIS = 200L
        const val PICK_SPREAD = 160L
        const val FLY_MILLIS = 300L
        const val FALL_MILLIS = 900L
        const val TRIP_CHANCE = .2f
        const val PAUSE_CHANCE = .3f
        const val PAUSE_MILLIS = 800L
        const val PLACE_MILLIS = 320L
        const val PLACE_SPREAD = 260L
        const val LOOK_MILLIS = 1200L
        const val CARRIED_SCALE = .35f
        const val BUILT_SCALE = .5f
        const val BUILD_GAP = 10f
        const val BUILD_WIDTH = 24f

        private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

        /** The places of the pieces of a build, bottom first, at [site] on a bar of [height]. Each place is a letter's baseline. */
        fun shape(build: Build, site: Float, height: Float, unit: Float): List<Pair<Float, Float>> {
            val cell = 6f * unit
            val ground = height - 3 * unit
            fun at(column: Int, row: Int) = Pair(site + column * cell, ground - row * cell)
            return when (build) {
                Build.Tower -> (0..4).map { at(1, it) } + listOf(at(0, 0), at(2, 0))
                Build.House -> listOf(at(0, 0), at(1, 0), at(2, 0), at(3, 0), at(0, 1), at(3, 1), at(0, 2), at(1, 2), at(2, 2), at(3, 2),
                    at(1, 3), at(2, 3))
                Build.Sign -> listOf(at(1, 0), at(1, 1), at(0, 2), at(1, 2), at(2, 2), at(3, 2), at(0, 3), at(1, 3), at(2, 3), at(3, 3))
                Build.Rocket -> listOf(at(0, 0), at(2, 0), at(1, 0), at(1, 1), at(1, 2), at(1, 3), at(1, 4))
            }.map { (x, y) -> Pair(x, max(cell, y)) }
        }
    }
}
