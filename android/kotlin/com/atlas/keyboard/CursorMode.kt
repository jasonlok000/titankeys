package com.atlas.keyboard

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A finger that [CursorGesture] started moves the cursor like a joystick. All of Space is a dead zone: on it the
 * cursor stays still, wherever the finger landed. Past either end of Space the cursor goes that way: slowly just
 * past the end for placing the cursor exactly, and quickly towards the edge of the keyboard to race across long
 * text. A word drag uses the same zones, and each step extends the selection by a word. Like a swipe, a brief slip
 * off the touchpad doesn't end it: the keyboard holds it through the slip recovery period, and the finger carries
 * on where it lands. A finger that leans to the end of the touchpad, or off its bottom edge past Space, can run off
 * it and come back near the same place, much later than a slip, so that also carries on, like a swipe that runs off
 * the top of the touchpad. Distances are in key widths, as in [Contact].
 */
class CursorMode {
    private enum class State { Idle, Active, Lifted }

    private var state = State.Idle
    private var position: Sample? = null
    /** Where and when the finger left the touchpad, while it may still come back. */
    private var liftedFrom: Sample? = null
    /** When the finger reached the edge of the keyboard, for the build-up to full speed. */
    private var edgeSince: Long? = null

    val active: Boolean get() = state == State.Active

    /** The finger slipped off during cursor mode and may still come back. */
    val lifted: Boolean get() = state == State.Lifted

    /** Each step extends a selection by a word and doesn't move the cursor by a letter. */
    var words = false
        private set

    /** Starts cursor mode for a finger that landed at [from], or a word drag if [words] is true. */
    fun start(from: Sample, words: Boolean = false) {
        state = State.Active
        this.words = words
        position = from
    }

    /** The finger left the touchpad; cursor mode waits for it to come back. */
    fun lift() {
        if (state != State.Active) return
        state = State.Lifted
        liftedFrom = position
        position = null
    }

    /** How long to wait for the finger after a lift, given the slip recovery period [slipMillis]: longer at the edges of the touchpad. */
    fun holdMillis(slipMillis: Long): Long {
        val from = liftedFrom ?: return slipMillis
        return if (atEdge(from)) max(slipMillis, EDGE_RETURN_MILLIS) else slipMillis
    }

    /**
     * The finger came back after a slip, carrying on cursor mode from where it lands. Within the slip recovery period
     * [slipMillis] it counts anywhere. After that it counts only if it comes back near the place at the edge of the
     * touchpad where it left, and a touch anywhere else is a new touch, such as another finger or the next gesture.
     */
    fun resume(sample: Sample, slipMillis: Long = Long.MAX_VALUE): Boolean {
        if (state != State.Lifted || !sample.valid) return false
        val from = liftedFrom
        if (from != null && sample.time - from.time > slipMillis && !returnsToEdge(from, sample)) return false
        state = State.Active
        liftedFrom = null
        position = sample
        return true
    }

    private fun atEdge(sample: Sample) = sample.x * 10 <= EDGE_ZONE_KEYS || sample.x * 10 >= 10 - EDGE_ZONE_KEYS || atBottomPastSpace(sample)

    private fun atBottomPastSpace(sample: Sample): Boolean {
        val column = sample.x * 10
        return sample.y * 4 >= EDGE_BOTTOM_ROW && (column < SPACE_LEFT || column > SPACE_RIGHT)
    }

    private fun returnsToEdge(from: Sample, to: Sample) = atEdge(from) && to.time - from.time <= EDGE_RETURN_MILLIS
        && hypot((to.x - from.x) * 10, (to.y - from.y) * 4) <= EDGE_RETURN_KEYS

    fun move(samples: List<Sample>) {
        for (sample in samples) {
            if (!sample.valid) continue
            position = sample
        }
    }

    /** Which way the finger is leaning: -1 left of Space, 1 right of Space, 0 on Space. */
    fun direction(): Int {
        val depth = depth() ?: return 0
        return if (depth.first) -1 else 1
    }

    /**
     * Steps a second for the current lean at [now], or 0 on Space. The speed follows [SPEEDS], growing by an even
     * factor between each pair of points, so every bit further feels like a step up: slow and precise just past
     * Space, and quick near the edge of the keyboard. The range ends at [MAX_LEAN]; holding there or further out
     * builds up to [FASTEST_PER_SECOND] over [RAMP_MILLIS]. Words are long, so a word drag takes the square root:
     * about one word a second next to Space and seven at full speed.
     */
    fun speed(now: Long): Float {
        val depth = depth()?.second ?: run { edgeSince = null; return 0f }
        val lean = depth.coerceAtMost(MAX_LEAN)
        var upper = 1
        while (upper < SPEEDS.size - 1 && lean > SPEEDS[upper].first) upper++
        val (fromLean, fromSpeed) = SPEEDS[upper - 1]
        val (toLean, toSpeed) = SPEEDS[upper]
        val reached = fromSpeed * (toSpeed / fromSpeed).pow((lean - fromLean) / (toLean - fromLean))
        val letters = if (lean < MAX_LEAN * AT_EDGE) {
            edgeSince = null
            reached
        } else {
            val since = edgeSince ?: now.also { edgeSince = it }
            val ramp = ((now - since).toFloat() / RAMP_MILLIS).coerceIn(0f, 1f)
            // Eased so the build-up starts gently and settles into full speed.
            reached + (FASTEST_PER_SECOND - reached) * ramp * ramp * (3 - 2 * ramp)
        }
        return if (words) sqrt(letters) else letters
    }

    fun end() {
        edgeSince = null
        state = State.Idle
        words = false
        position = null
        liftedFrom = null
    }

    /** Whether the finger leans left, and how far past the end of Space it is, or null while it is on Space. */
    private fun depth(): Pair<Boolean, Float>? {
        val sample = position ?: return null
        if (!active || sample.y < BOTTOM_ROW_TOP) return null
        val column = sample.x * 10
        return when {
            column < SPACE_LEFT -> true to SPACE_LEFT - column
            column > SPACE_RIGHT -> false to column - SPACE_RIGHT
            else -> null
        }
    }

    companion object {
        /** The ends of Space, in key widths from the left edge of the keyboard. All of Space between them is the dead zone. */
        private val SPACE_LEFT = Layout.bottomWidths.take(3).sum()
        private val SPACE_RIGHT = SPACE_LEFT + Layout.bottomWidths[3]
        /** A little above the bottom row still counts, so a finger drifting up keeps control. */
        private const val BOTTOM_ROW_TOP = .7f
        /** How near the end of the touchpad a lift must be to count as running off it. Real ones left at column 0.65 and column 9.4. */
        private const val EDGE_ZONE_KEYS = .8f
        /**
         * A lift this low on the bottom row is at the bottom edge of the touchpad, which reports its last position at row 3.54. Only past
         * the ends of Space does it count: a finger on Space moves nothing, and the next gesture lands there, within a key of such a lift.
         */
        private const val EDGE_BOTTOM_ROW = 3.45f
        /** Fingers that ran off an end of the touchpad came back 314 to 390 ms later and carried on for several keys. */
        private const val EDGE_RETURN_MILLIS = 450L
        /** They came back 0.15 to 0.48 keys from where they left. A new gesture right after a lift at an end landed over 3.5 keys away. */
        private const val EDGE_RETURN_KEYS = 1f
        /** The lean that gets the top speed. Real drags reached column 0.65 and column 9.4, where the touchpad ends. */
        private const val MAX_LEAN = 2.9f
        /** How far out counts as reaching the top speed. */
        private const val AT_EDGE = .97f
        private const val RAMP_MILLIS = 1000f
        /** Letters a second at each lean, in key widths past the end of Space. The keys beside Space are 1.2 wide. */
        private val SPEEDS = listOf(
            0f to 1.5f, // just past the end of Space
            .8f to 3f, // the precise stretch ends
            1.6f to 8f,
            2.3f to 16f,
            MAX_LEAN to 28f, // where holding builds up to full speed
        )
        private const val FASTEST_PER_SECOND = 52f

        /** How a finger becomes the cursor and how fast it moves, for the raw input log. */
        val tuning: Map<String, Any> get() = CursorGesture.tuning + mapOf(
            "spaceLeftKeys" to SPACE_LEFT, "spaceRightKeys" to SPACE_RIGHT, "maxLeanKeys" to MAX_LEAN,
            "speedPoints" to SPEEDS.joinToString(" ") { (lean, speed) -> "$lean:$speed" },
            "fastestPerSecond" to FASTEST_PER_SECOND, "wordSpeed" to "sqrt",
            "edgeZoneKeys" to EDGE_ZONE_KEYS, "edgeBottomRow" to EDGE_BOTTOM_ROW, "edgeReturnMillis" to EDGE_RETURN_MILLIS, "edgeReturnKeys" to EDGE_RETURN_KEYS,
        )
    }
}
