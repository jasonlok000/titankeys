package com.atlas.keyboard

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * The idle scene on the suggestion bar, an Easter egg: after a long while with no input, a small stick figure walks on, takes the
 * bar's words apart letter by letter and builds something wide with them right there, from left to right, a word at a time: a
 * bridge, a train, a castle wall, a skyline, a bus or a boat, across half to most of the bar. When the words have too few letters,
 * it brings a few spare ones of its own. Then it walks off, and only the build stays, still, until you do something. It never says
 * anything. This is the plan of the scene in time and place. It needs no Android, so the checks can play it with any clock.
 * [Strip] draws it.
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
    enum class Build { Bridge, Train, Castle, Skyline, Bus, Boat }

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
    /** The bar's letters, then spare ones that the figure brings along, which have no place on the bar. */
    private val pieces: List<Letter>
    private val spares: Int
    /** When each piece leaves the bar, when it lands in the build, and which place of the build it takes. */
    private val pickedAt: LongArray
    private val placedAt: LongArray
    private val placeOf: IntArray
    /** The build: a place for each piece, from left to right and bottom up, across [span] of the bar from [left]. */
    private val span = 0.5f + random.nextFloat() * 0.3f
    private val left = width * (1 - span) * (0.2f + random.nextFloat() * 0.6f)
    private val slots: List<Pair<Float, Float>>
    /** How big a piece is in the build, so that it fills its place in the grid. */
    private val builtScale: Float
    /** How long the plan takes at the figure's own pace. */
    private val planned: Long
    /** How long the scene lasts: the plan, slowed down or sped up to fit between [MIN_MILLIS] and [MAX_MILLIS]. */
    val millis: Long

    init {
        val wanted = max(letters.size, MIN_PIECES).coerceAtMost(max(letters.size, MAX_PIECES))
        spares = (wanted - letters.size).coerceAtLeast(0)
        val stock = letters.ifEmpty { SPARE_STOCK.mapIndexed { i, c -> Letter(c, 0f, 0f, i) } }
        pieces = letters + (0 until spares).map { val from = stock[it % stock.size]; Letter(from.char, offRight, height, -1) }
        pickedAt = LongArray(pieces.size) { -1 }
        placedAt = LongArray(pieces.size) { -1 }
        placeOf = IntArray(pieces.size) { -1 }
        val grid = design(build, min(pieces.size, MAX_PIECES), random)
        val columns = (grid.maxOfOrNull { it.first } ?: 0) + 1
        val cell = (width * span / columns).coerceIn(MIN_CELL * unit, MAX_CELL * unit)
        val rise = cell * ROW_RATIO
        val ground = height - GROUND * unit
        slots = grid.map { (column, row) -> Pair(left + column * cell, max(rise, ground - row * rise)) }
        builtScale = (cell / (unit * LETTER_WIDTH)).coerceIn(MIN_BUILT_SCALE, MAX_BUILT_SCALE)

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

        // The spare letters come along in the bundle from the start, and go in with the first word's letters.
        for (index in letters.size until pieces.size) pickedAt[index] = 0
        // Which piece goes in each place: the bar's letters from left to right, with the spares after the first one.
        val byX = letters.indices.sortedWith(compareBy({ letters[it].x }, { it }))
        val order = byX.take(1) + (letters.size until pieces.size) + byX.drop(1)
        // Words from left to right, so the build grows behind the figure as it clears the bar.
        val words = letters.indices.groupBy { letters[it].word }.values.sortedBy { word -> word.minOf { letters[it].x } }
        var piece = 0
        fun placeSome(count: Int) {
            repeat(count) {
                if (piece >= pieces.size) return
                val index = order[piece]
                val (x, _) = place(piece)
                if (abs(x - BUILD_GAP * unit - (parts.lastOrNull()?.toX ?: offRight)) > STEP_LENGTH * unit) move(x - BUILD_GAP * unit)
                placeOf[index] = piece
                placedAt[index] = now
                stay(PLACE_MILLIS + random.nextLong(PLACE_SPREAD), Pose.Pick)
                piece++
            }
        }
        for ((number, word) in words.withIndex()) {
            move(word.map { letters[it].x }.average().toFloat())
            for (index in word.reversed()) {
                pickedAt[index] = now
                stay(PICK_MILLIS + random.nextLong(PICK_SPREAD), Pose.Pick)
            }
            // Now and then a trip on the way, or a pause to think it over.
            if (random.nextFloat() < TRIP_CHANCE) stay(FALL_MILLIS, Pose.Fall)
            else if (random.nextFloat() < PAUSE_CHANCE) stay(PAUSE_MILLIS, Pose.Stand)
            // The spare pieces go in with the first word's letters.
            placeSome(word.size + if (number == 0) spares else 0)
        }
        placeSome(pieces.size - piece)
        // A look at the work, and off the way that is nearer.
        stay(LOOK_MILLIS, Pose.Stand)
        move(if (left + width * span / 2 < width / 2) offLeft else offRight)
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
        for ((index, home) in pieces.withIndex()) {
            val picked = pickedAt[index]
            val dropped = placedAt[index]
            when {
                picked < 0 || t < picked -> placed += Placed(home.char, home.x, home.y, 1f)
                home.word >= 0 && t < picked + FLY_MILLIS -> {
                    // A letter flies from the bar into the bundle over the figure's head.
                    val f = (t - picked).toFloat() / FLY_MILLIS
                    placed += Placed(home.char, lerp(home.x, x, f), lerp(home.y, headY, f), lerp(1f, CARRIED_SCALE, f))
                }
                dropped < 0 || t < dropped -> carrying++
                else -> {
                    val (sx, sy) = place(placeOf[index])
                    // From the bundle to its place, from where the figure stood when it put it there.
                    val f = ((t - dropped).toFloat() / FLY_MILLIS).coerceIn(0f, 1f)
                    val from = sx - BUILD_GAP * unit
                    placed += Placed(home.char, lerp(from, sx, f), lerp(headY, sy, f), lerp(CARRIED_SCALE, builtScale, f))
                }
            }
        }
        val shown = x > -FIGURE_WIDTH * unit && x < width + FIGURE_WIDTH * unit
        val facingLeft = part.toX < part.fromX || (part.fromX == part.toX && x > left + width * span / 2)
        return Frame(x, shown, if (walking) Pose.Walk else part.pose, facingLeft, step, carrying, placed)
    }

    /** A piece's place: in the build, or past its last place in a small heap beside it. */
    private fun place(piece: Int): Pair<Float, Float> {
        slots.getOrNull(piece)?.let { return it }
        val heap = piece - slots.size
        val cell = 4f * unit
        val right = (slots.maxOfOrNull { it.first } ?: left) + 2 * cell
        return Pair(min(width - cell, right + (heap % 4) * cell), max(cell * 2, height - GROUND * unit - (heap / 4) * cell))
    }

    companion object {
        const val BASE_SPEED = .05f
        const val MIN_MILLIS = 20_000L
        const val MAX_MILLIS = 40_000L
        const val FIGURE_WIDTH = 8f
        const val FIGURE_HEIGHT = 26f
        const val STEP_LENGTH = 4f
        const val PICK_MILLIS = 160L
        const val PICK_SPREAD = 120L
        const val FLY_MILLIS = 300L
        const val FALL_MILLIS = 900L
        const val TRIP_CHANCE = .2f
        const val PAUSE_CHANCE = .3f
        const val PAUSE_MILLIS = 800L
        const val PLACE_MILLIS = 190L
        const val PLACE_SPREAD = 150L
        const val LOOK_MILLIS = 1200L
        const val CARRIED_SCALE = .35f
        const val BUILD_GAP = 8f
        /** A build has at least this many pieces, with spares when the words are short, and at most this many. */
        const val MIN_PIECES = 36
        const val MAX_PIECES = 60
        /** The size of a place in the build's grid, in steps, and how much of it a row rises. */
        const val MIN_CELL = 7f
        const val MAX_CELL = 14f
        const val ROW_RATIO = .95f
        const val GROUND = 3f
        /** About how wide a letter of the bar is, in steps, to size the pieces to their places. */
        const val LETTER_WIDTH = 12f
        const val MIN_BUILT_SCALE = .5f
        const val MAX_BUILT_SCALE = .9f
        const val MAX_ROWS = 4
        /** Letters to build with when the bar has none. */
        private const val SPARE_STOCK = "titankeys"

        private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

        /**
         * The grid of a build with [count] pieces, as columns and rows from the bottom left, in the order they are built: from left
         * to right, and from the bottom up in each column. Each kind of build grows wider, not taller, as it gets more pieces.
         */
        fun design(build: Build, count: Int, random: Random): List<Pair<Int, Int>> {
            var columns = 3
            while (true) {
                val places = plan(build, columns, random.nextInt()).distinct().filter { it.second in 0 until MAX_ROWS }
                if (places.size >= count || columns > count) {
                    val chosen = places.take(count)
                    return chosen.sortedWith(compareBy({ it.first }, { it.second }))
                }
                columns++
            }
        }

        /** Every place of a build that is [columns] wide, the parts that matter most first. */
        private fun plan(build: Build, columns: Int, seed: Int): List<Pair<Int, Int>> {
            val random = Random(seed)
            val last = columns - 1
            return when (build) {
                // A long deck on piers, with railings at its ends.
                Build.Bridge -> (0..last).map { it to 1 } + (0..last step 4).map { it to 0 } + listOf(last to 0) +
                    listOf(0 to 2, last to 2) + (2 until last step 4).map { it to 2 }
                // An engine with a chimney, then carriages, each two places long and two high, with a gap between them.
                Build.Train -> listOf(0 to 0, 1 to 0, 0 to 1, 0 to 2) +
                    (3..last).filter { (it - 3) % 3 != 2 }.flatMap { listOf(it to 0, it to 1) }
                // A long wall, battlements on every other place, and a tower at each end.
                Build.Castle -> (0..last).map { it to 0 } + (0..last step 2).map { it to 1 } +
                    listOf(0 to 1, 0 to 2, 1 to 2, last to 1, last to 2, last - 1 to 2)
                // Buildings of different heights side by side, now and then with a gap.
                Build.Skyline -> (0..last).filter { random.nextFloat() > .15f || it == 0 || it == last }
                    .flatMap { column -> (0..random.nextInt(MAX_ROWS - 1)).map { column to it } }
                // A long body, with a window gap every third place on the upper row.
                Build.Bus -> (0..last).map { it to 0 } + (0..last).filter { it % 3 != 2 }.map { it to 1 }
                // A hull narrowing at the front, with a mast and a sail in the middle.
                Build.Boat -> (1..last).map { it to 0 } + (0..last).map { it to 1 } +
                    listOf(columns / 2 to 2, columns / 2 to 3, columns / 2 + 1 to 2)
            }
        }
    }
}
