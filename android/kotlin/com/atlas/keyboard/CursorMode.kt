package com.atlas.keyboard

import kotlin.math.hypot
import kotlin.math.pow

/**
 * Rest a finger on Space to start cursor mode, then lean it towards either end of Space to move the
 * cursor that way, like a joystick. The whole bottom row sets the speed: just inside the end of Space
 * creeps for placing the cursor exactly, and the far edges of the keyboard race across long text. The rest keeps
 * swipes and grazes that cross Space out of cursor mode: the finger must stay within [REST_KEYS] of
 * where it landed for [REST_MILLIS]. Like a swipe, a brief slip off the touchpad doesn't end it: the
 * keyboard holds it through the slip recovery period, and the finger carries on where it lands.
 * Distances are in key widths, as in [Contact].
 */
class CursorMode {
    private enum class State { Idle, Resting, Active, Lifted }

    private var state = State.Idle
    private var landed: Sample? = null
    private var position: Sample? = null

    val active: Boolean get() = state == State.Active

    /** The finger slipped off during cursor mode and may still come back. */
    val lifted: Boolean get() = state == State.Lifted

    /** Whether this contact landed on Space and may start cursor mode once it has rested. */
    fun down(sample: Sample): Boolean {
        val onSpace = sample.valid && Layout.keyAt(sample.x, sample.y) == Layout.SPACE
        state = if (onSpace) State.Resting else State.Idle
        landed = sample.takeIf { onSpace }
        position = landed
        return onSpace
    }

    /** Starts cursor mode if the finger is still resting at [now]. */
    fun rest(now: Long): Boolean {
        val first = landed ?: return false
        if (state != State.Resting || now - first.time < REST_MILLIS) return false
        state = State.Active
        return true
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
            if (state == State.Resting && distance(landed!!, sample) > REST_KEYS) state = State.Idle
            position = sample
        }
    }

    /** Which way the finger is leaning: -1 left, 1 right, 0 resting in the middle of Space. */
    fun direction(): Int {
        val depth = depth() ?: return 0
        return if (depth.first) -1 else 1
    }

    /**
     * Letters a second for the current lean, or 0 in the middle. Space itself spans [SLOWEST_PER_SECOND] to
     * [SPACE_PER_SECOND] for fine control; the rest of the bottom row carries on up to [FASTEST_PER_SECOND].
     * Each stretch grows by the same factor along its length, so every bit further feels like a step up.
     */
    fun speed(): Float {
        val depth = depth()?.second ?: return 0f
        if (depth <= EDGE_KEYS) return SLOWEST_PER_SECOND * (SPACE_PER_SECOND / SLOWEST_PER_SECOND).pow(depth / EDGE_KEYS)
        val beyond = ((depth - EDGE_KEYS) / (MAX_DEPTH - EDGE_KEYS)).coerceAtMost(1f)
        return SPACE_PER_SECOND * (FASTEST_PER_SECOND / SPACE_PER_SECOND).pow(beyond)
    }

    fun end() {
        state = State.Idle
        landed = null
        position = null
    }

    /** Whether the finger leans left, and how far into the edge zone it is, or null in the middle. */
    private fun depth(): Pair<Boolean, Float>? {
        val sample = position ?: return null
        if (!active || sample.y < BOTTOM_ROW_TOP) return null
        val column = sample.x * 10
        val left = SPACE_LEFT + EDGE_KEYS - column
        val right = column - (SPACE_RIGHT - EDGE_KEYS)
        return when {
            left > 0 -> true to left
            right > 0 -> false to right
            else -> null
        }
    }

    private fun distance(a: Sample, b: Sample) = hypot((b.x - a.x) * 10, (b.y - a.y) * 4)

    companion object {
        const val REST_MILLIS = 140L
        private const val REST_KEYS = .3f
        /** The ends of Space, in key widths from the left edge of the keyboard. */
        private val SPACE_LEFT = Layout.bottomWidths.take(3).sum()
        private val SPACE_RIGHT = SPACE_LEFT + Layout.bottomWidths[3]
        /** How far in from each end of Space the cursor starts moving. */
        private const val EDGE_KEYS = 1.1f
        /** A little above the bottom row still counts, so a finger drifting up keeps control. */
        private const val BOTTOM_ROW_TOP = .7f
        /** From where moving starts to the end of the bottom row; Space sits in the middle, so both sides match. */
        private val MAX_DEPTH = SPACE_LEFT + EDGE_KEYS
        private const val SLOWEST_PER_SECOND = 2f
        private const val SPACE_PER_SECOND = 10f
        private const val FASTEST_PER_SECOND = 35f
    }
}
