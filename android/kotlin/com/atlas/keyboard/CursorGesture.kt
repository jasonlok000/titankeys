package com.atlas.keyboard

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Tells the two gestures that control the cursor from every other touch on the bottom row. Both begin with a
 * quick tap anywhere on the row. If a second touch lands just after it and lifts quickly, that selects the word
 * at the cursor. If the second touch is held and dragged sideways, that moves the cursor.
 *
 * The touchpad reports one point only, and a resting finger is reported again as short touches at the same place,
 * which look like repeated taps. So the first tap must come alone, and a double tap only counts once
 * [CONFIRM_MILLIS] have passed with no third touch. A wrong selection is worse than a missed one, so anything
 * in doubt does nothing. Distances are in key widths and rows, as in [Contact]. The numbers come from recordings
 * of these gestures on a Titan 2 Elite.
 */
class CursorGesture {
    private sealed interface State {
        data object Idle : State
        class FirstTouch(val landed: Sample) : State {
            var travel = 0f
        }
        /** The first tap is over, and a second touch can still land. */
        class Waiting(val landed: Sample, val lifted: Long) : State
        class SecondTouch(val landed: Sample) : State {
            var across = 0f
            var rows = 0f
            var travel = 0f
        }
        /** Two quick taps are done, and a third touch or a key press can still call them off. */
        data object Confirming : State
    }

    private var state: State = State.Idle
    /** A touch that lands before this time can't be a first tap. */
    private var firstTapFrom = 0L

    /** A finger landed. [counts] is false for a touch that can't type, such as one in the pause after a key press. */
    fun down(sample: Sample, counts: Boolean) {
        val previous = state
        state = when {
            !counts || !sample.valid || sample.y < BOTTOM_ROW_TOP -> State.Idle
            previous is State.Waiting && follows(previous, sample) -> State.SecondTouch(sample)
            sample.time >= firstTapFrom -> State.FirstTouch(sample)
            else -> State.Idle
        }
    }

    /** Returns where the second touch landed, once this movement makes it a sideways drag that moves the cursor. */
    fun move(samples: List<Sample>): Sample? {
        for (sample in samples) {
            val current = state
            if (!sample.valid) {
                state = State.Idle
                return null
            }
            if (current is State.FirstTouch) current.travel = max(current.travel, distance(current.landed, sample))
            if (current !is State.SecondTouch) continue
            current.across = max(current.across, abs(sample.x - current.landed.x) * 10)
            current.rows = max(current.rows, abs(sample.y - current.landed.y) * 4)
            current.travel = max(current.travel, distance(current.landed, sample))
            // This looks at how far the path has gone, because a drag can go left and right and end near its start.
            if (current.rows > MAX_DRAG_ROWS) state = State.Idle
            else if (current.across >= DRAG_KEYS && current.across >= SIDEWAYS * current.rows) {
                state = State.Idle
                return current.landed
            }
        }
        return null
    }

    /** The finger lifted at [time]. Returns true if this finished two quick taps, for [confirm] to check later. */
    fun up(samples: List<Sample>, time: Long): Boolean {
        move(samples)
        firstTapFrom = max(firstTapFrom, time + ALONE_MILLIS)
        val current = state
        state = State.Idle
        if (current is State.FirstTouch && time - current.landed.time <= FIRST_TAP_MILLIS && current.travel < TAP_KEYS) {
            state = State.Waiting(current.landed, time)
        }
        if (current is State.SecondTouch && time - current.landed.time <= SECOND_TAP_MILLIS && current.travel < TAP_KEYS) {
            state = State.Confirming
            return true
        }
        return false
    }

    /** True if the double tap still stands [CONFIRM_MILLIS] after it lifted. It can only be used once. */
    fun confirm(): Boolean {
        if (state != State.Confirming) return false
        state = State.Idle
        return true
    }

    /** A key press or something else interrupted the touchpad at [time]. */
    fun cancel(time: Long) {
        state = State.Idle
        firstTapFrom = max(firstTapFrom, time + AFTER_KEY_MILLIS)
    }

    /** Whether the taps so far may still become a gesture at [now], so a tap key must not type them yet. */
    fun deciding(now: Long) = when (val current = state) {
        is State.Waiting -> now < current.lifted + MAX_GAP_MILLIS
        is State.SecondTouch, State.Confirming -> true
        else -> false
    }

    private fun follows(first: State.Waiting, second: Sample) =
        second.time - first.lifted in MIN_GAP_MILLIS..MAX_GAP_MILLIS && abs(second.x - first.landed.x) * 10 <= MAX_APART_KEYS

    private fun distance(a: Sample, b: Sample) = hypot((b.x - a.x) * 10, (b.y - a.y) * 4)

    companion object {
        private const val BOTTOM_ROW_TOP = .75f
        /** How long each tap may last. Real first taps took up to 105 ms and real second taps up to 191 ms. */
        private const val FIRST_TAP_MILLIS = 130L
        private const val SECOND_TAP_MILLIS = 200L
        /** A touch that moves this far is not a tap. */
        private const val TAP_KEYS = .3f
        /** Real gaps between the taps took 37 to 124 ms. Shorter than the settle time, a key press can still cancel the first. */
        private const val MIN_GAP_MILLIS = Contact.SETTLE_MILLIS
        private const val MAX_GAP_MILLIS = 150L
        /** Real taps of a pair landed up to 0.61 keys apart. */
        private const val MAX_APART_KEYS = 1f
        /** The first tap must come this long after the touch before it, which also leaves out a resting finger's series. */
        private const val ALONE_MILLIS = 200L
        /** The first tap must come this long after a key press. */
        private const val AFTER_KEY_MILLIS = 500L
        /** How long a double tap waits for a third touch, which would make it part of a series. */
        const val CONFIRM_MILLIS = MAX_GAP_MILLIS
        /** How far sideways the second touch must go to start moving the cursor. */
        private const val DRAG_KEYS = .5f
        /** A drag has to be at least this many times wider than it is tall. */
        private const val SIDEWAYS = 2f
        /** A touch that goes this many rows up or down is a word swipe. Real drags went at most 0.2 before they started. */
        private const val MAX_DRAG_ROWS = .4f

        /** The numbers that decide what counts as a gesture, for the raw input log. */
        val tuning: Map<String, Any> get() = mapOf(
            "firstTapMillis" to FIRST_TAP_MILLIS, "secondTapMillis" to SECOND_TAP_MILLIS, "tapKeys" to TAP_KEYS,
            "minGapMillis" to MIN_GAP_MILLIS, "maxGapMillis" to MAX_GAP_MILLIS, "maxApartKeys" to MAX_APART_KEYS,
            "aloneMillis" to ALONE_MILLIS, "afterKeyMillis" to AFTER_KEY_MILLIS, "confirmMillis" to CONFIRM_MILLIS,
            "dragKeys" to DRAG_KEYS, "sideways" to SIDEWAYS, "maxDragRows" to MAX_DRAG_ROWS,
        )
    }
}
