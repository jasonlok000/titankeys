package com.atlas.keyboard

import kotlin.math.abs
import kotlin.math.pow

/**
 * A finger that [CursorGesture] started moves the cursor like a joystick: dragged left or right of where it landed,
 * the cursor goes that way. Direction and speed are measured from that spot, so it doesn't matter where on the
 * bottom row the finger came down: a short lean creeps for placing the cursor exactly, and a long one races across
 * long text. Like a swipe, a brief slip off the touchpad doesn't end it: the keyboard holds it through the slip
 * recovery period, and the finger carries on where it lands. Distances are in key widths, as in [Contact].
 */
class CursorMode {
    private enum class State { Idle, Active, Lifted }

    private var state = State.Idle
    /** Where the finger landed. Direction and speed are measured from here. */
    private var landed: Sample? = null
    private var position: Sample? = null
    /** When the finger reached the edge of the keyboard, for the build-up to full speed. */
    private var edgeSince: Long? = null

    val active: Boolean get() = state == State.Active

    /** The finger slipped off during cursor mode and may still come back. */
    val lifted: Boolean get() = state == State.Lifted

    /** Starts cursor mode for a finger that landed at [from]. */
    fun start(from: Sample) {
        state = State.Active
        landed = from
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

    /** Which way the finger is leaning: -1 left, 1 right, 0 still near where it landed. */
    fun direction(): Int {
        val depth = depth() ?: return 0
        return if (depth.first) -1 else 1
    }

    /**
     * Letters a second for the current lean at [now], or 0 when still. The speed follows [SPEEDS],
     * growing by an even factor between each pair of points, so every bit further feels like a step up:
     * slow and precise just off the landing spot, and quick a couple of keys out. The range ends at [MAX_LEAN];
     * holding there or further out builds up to [FASTEST_PER_SECOND] over [RAMP_MILLIS].
     */
    fun speed(now: Long): Float {
        val depth = depth()?.second ?: run { edgeSince = null; return 0f }
        val lean = depth.coerceAtMost(MAX_LEAN)
        var upper = 1
        while (upper < SPEEDS.size - 1 && lean > SPEEDS[upper].first) upper++
        val (fromLean, fromSpeed) = SPEEDS[upper - 1]
        val (toLean, toSpeed) = SPEEDS[upper]
        val reached = fromSpeed * (toSpeed / fromSpeed).pow((lean - fromLean) / (toLean - fromLean))
        if (lean < MAX_LEAN * AT_EDGE) {
            edgeSince = null
            return reached
        }
        val since = edgeSince ?: now.also { edgeSince = it }
        val ramp = ((now - since).toFloat() / RAMP_MILLIS).coerceIn(0f, 1f)
        // Eased so the build-up starts gently and settles into full speed.
        return reached + (FASTEST_PER_SECOND - reached) * ramp * ramp * (3 - 2 * ramp)
    }

    fun end() {
        edgeSince = null
        state = State.Idle
        landed = null
        position = null
    }

    /** Whether the finger leans left, and how far past the still spot it is, or null while it is still. */
    private fun depth(): Pair<Boolean, Float>? {
        val sample = position ?: return null
        val from = landed ?: return null
        if (!active || sample.y < BOTTOM_ROW_TOP) return null
        val lean = (sample.x - from.x) * 10
        val past = abs(lean) - STILL_KEYS
        return if (past > 0) (lean < 0) to past else null
    }

    companion object {
        /** A finger this close to where it landed is holding still, so the cursor stops. */
        private const val STILL_KEYS = .3f
        /** A little above the bottom row still counts, so a finger drifting up keeps control. */
        private const val BOTTOM_ROW_TOP = .7f
        /** The lean that gets the top speed, the same reach as before. Leaning further counts the same. */
        private const val MAX_LEAN = 3.15f
        /** How far out counts as reaching the top speed. */
        private const val AT_EDGE = .97f
        private const val RAMP_MILLIS = 1000f
        /** Letters a second at each lean, in key widths past the still spot. */
        private val SPEEDS = listOf(
            0f to 1.5f, // just off the still spot
            .5f to 3f, // the precise stretch ends
            1.35f to 14f,
            2f to 22f,
            MAX_LEAN to 28f, // where holding builds up to full speed
        )
        private const val FASTEST_PER_SECOND = 52f

        /** How a finger becomes the cursor and not a swipe, for the raw input log. */
        val tuning: Map<String, Any> get() = CursorGesture.tuning + ("stillKeys" to STILL_KEYS)
    }
}
