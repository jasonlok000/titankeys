package com.atlas.keyboard

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A finger that [CursorGesture] started moves the cursor like a joystick. All of Space is a dead zone: on it the
 * cursor stays still, wherever the finger landed. Past either end of Space the cursor goes that way: slowly just
 * past the end for placing the cursor exactly, and quickly towards the edge of the keyboard to race across long
 * text. A word drag uses the same zones, and each step extends the selection by a word. Like a swipe, a brief slip
 * off the touchpad doesn't end it: the keyboard holds it through the slip recovery period, and the finger carries
 * on where it lands. Distances are in key widths, as in [Contact].
 */
class CursorMode {
    private enum class State { Idle, Active, Lifted }

    private var state = State.Idle
    private var position: Sample? = null
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
        position = null
    }

    /** The finger came back after a slip, carrying on cursor mode from where it lands. */
    fun resume(sample: Sample): Boolean {
        if (state != State.Lifted || !sample.valid) return false
        state = State.Active
        position = sample
        return true
    }

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
        )
    }
}
