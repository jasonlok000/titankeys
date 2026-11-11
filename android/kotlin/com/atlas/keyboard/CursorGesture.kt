package com.atlas.keyboard

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Tells the gestures that control the cursor from every other touch on the bottom row. All begin with a quick tap
 * anywhere on the row. If a second touch lands just after it and lifts quickly, that selects the word at the cursor.
 * If the second touch is held and dragged sideways or up, that moves the cursor. If a third touch lands just after two
 * quick taps and is dragged sideways or up, that selects words. Up takes the finger onto the letter rows, which the
 * touchpad reads better than the bottom row, and [CursorMode] follows it there.
 *
 * A held touch registers as soon as it has stayed down for [REGISTER_MILLIS], before any slide: see [held]. That is when the
 * keyboard shows the gesture on the strip and plays its feel. Three quick taps pick the word at the cursor with no slide, after the
 * same wait as two: see [tripled]. A resting finger's series of short touches looks like three taps, and its fourth calls them off.
 *
 * The touchpad reports one point only, and a resting finger is reported again as short touches at the same place,
 * which look like repeated taps. So the first tap must come alone, a double tap only counts once [CONFIRM_MILLIS]
 * have passed with no third touch, and a third touch that lifts without a drag does nothing. A wrong selection is
 * worse than a missed one, so anything in doubt does nothing. Distances are in key widths and rows, as in [Contact].
 * The numbers come from recordings of these gestures on a Titan 2 Elite.
 *
 * The touchpad stops at the middle of the Space row, so a tap low on Space is often not felt at all. The taps may also
 * land just above Space: anywhere on C, V, B and N. A touch that lands there is the start of a word as often as not, so there only a sideways drag counts,
 * never one up.
 */
class CursorGesture {
    /** A touch that began to drag sideways, from where it [landed]. After three touches it selects [words]. */
    data class Drag(val landed: Sample, val words: Boolean)

    private sealed interface State {
        data object Idle : State
        class FirstTouch(val landed: Sample) : State {
            var travel = 0f
        }
        /** [taps] quick taps are over, and another touch can still land. A third touch or a key press calls off two. */
        class Tapped(val taps: Int, val landed: Sample, val lifted: Long) : State
        /** The touch that came after [taps] quick taps. */
        class NextTouch(val taps: Int, val landed: Sample, val gap: Long, val tapLanded: Sample) : State {
            var registered = false
            var last = landed
            var across = 0f
            var up = 0f
            var travel = 0f
        }
    }

    private var state: State = State.Idle
    /** A touch that lands before this time can't be a first tap. */
    private var firstTapFrom = 0L

    /** True after an [up] that finished three quick taps on Space, for [confirmTriple] to check after [CONFIRM_MILLIS]. */
    var tripled = false
        private set

    /** True while a second or third touch is down that may still become a gesture, for the keyboard to check [held] after a while. */
    val touchPending get() = state is State.NextTouch

    /** The quick taps that the last [up] finished, 1 to 3, for the strip's Space glow, or 0 when it finished none. */
    val tapped get() = (state as? State.Tapped)?.taps ?: 0

    /** Where the last of those taps landed. */
    val tappedLanded get() = (state as? State.Tapped)?.landed

    /** The quick taps before the touch that is down now: 2 when it is the third touch, which picks the word as it lands. */
    val tapsBefore get() = (state as? State.NextTouch)?.taps ?: 0

    /**
     * Once the second or third touch has stayed down for [REGISTER_MILLIS] by [now], the count of touches so far: 2 for a tap then
     * a held touch (steering), 3 for two taps then a held touch (picking words). It answers once, and 0 otherwise. A resting
     * finger's touches last about 40 ms, so they never get this far.
     */
    fun held(now: Long): Int {
        val current = state as? State.NextTouch ?: return 0
        if (current.registered || now - current.landed.time < REGISTER_MILLIS) return 0
        current.registered = true
        return current.taps + 1
    }

    /** A finger landed. [counts] is false for a touch that can't type, such as one in the pause after a key press. */
    fun down(sample: Sample, counts: Boolean) {
        val previous = state
        state = when {
            !counts || !sample.valid || !inZone(sample.x, sample.y) -> State.Idle
            // A fourth touch is a resting finger's series, which never makes a gesture.
            previous is State.Tapped && previous.taps == 3 -> State.Idle
            previous is State.Tapped && follows(previous, sample) -> State.NextTouch(previous.taps, sample, sample.time - previous.lifted, previous.landed)
            sample.time >= firstTapFrom -> State.FirstTouch(sample)
            else -> State.Idle
        }
    }

    /** Returns a [Drag] once this movement makes the second or third touch a sideways drag. */
    fun move(samples: List<Sample>): Drag? {
        for (sample in samples) {
            val current = state
            if (!sample.valid) {
                state = State.Idle
                return null
            }
            if (current is State.FirstTouch) current.travel = max(current.travel, distance(current.landed, sample))
            if (current !is State.NextTouch) continue
            // The touchpad reports one point, so a second finger shows up as a sudden jump and not as a drag.
            if (distance(current.last, sample) > JUMP_KEYS) {
                state = State.Idle
                continue
            }
            current.last = sample
            current.across = max(current.across, abs(sample.x - current.landed.x) * 10)
            current.up = max(current.up, (current.landed.y - sample.y) * 4)
            current.travel = max(current.travel, distance(current.landed, sample))
            // This looks at how far the path has gone, because a drag can go left and right and end near its start.
            // Above Space a drag up is a word, so there only a sideways one counts.
            val onSpace = current.landed.y >= BOTTOM_ROW_TOP
            val sideways = current.across >= DRAG_KEYS && current.across >= (if (onSpace) SIDEWAYS else ABOVE_SIDEWAYS) * current.up
            if (onSpace && current.up >= UP_ROWS || sideways) {
                state = State.Idle
                return Drag(current.landed, words = current.taps == 2)
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
        tripled = false
        // A third quick tap, after two quick ones, picks the word at the cursor once no fourth touch follows. Unlike two taps, three
        // count anywhere in the zone, also above Space: the touchpad barely feels the lower half of Space, so taps land on the row
        // above, and three quick taps there with no press between them are no typing.
        if (current is State.NextTouch && current.taps == 2 && time - current.landed.time <= SECOND_TAP_MILLIS && current.travel < TAP_KEYS
            && current.gap <= MAX_GAP_MILLIS) {
            state = State.Tapped(3, current.landed, time)
            tripled = true
            return false
        }
        if (current is State.FirstTouch && time - current.landed.time <= FIRST_TAP_MILLIS && current.travel < TAP_KEYS) {
            state = State.Tapped(1, current.landed, time)
        }
        // A third touch that lifts here ends with nothing, as it would in a resting finger's series.
        if (current is State.NextTouch && current.taps == 1 && time - current.landed.time <= SECOND_TAP_MILLIS && current.travel < TAP_KEYS) {
            state = State.Tapped(2, current.landed, time)
            // A double tap by itself selects a word only on Space, after today's gap. Above Space, or after a longer gap,
            // two taps can only lead on to a drag that selects: two quick taps on V or B must never select a word.
            return current.gap <= MAX_GAP_MILLIS && current.landed.y >= BOTTOM_ROW_TOP && current.tapLanded.y >= BOTTOM_ROW_TOP
        }
        return false
    }

    /** True if the double tap still stands [CONFIRM_MILLIS] after it lifted. It can only be used once. */
    fun confirm(): Boolean {
        if ((state as? State.Tapped)?.taps != 2) return false
        state = State.Idle
        return true
    }

    /** True if the triple tap still stands [CONFIRM_MILLIS] after it lifted, with no fourth touch. It can only be used once. */
    fun confirmTriple(): Boolean {
        if ((state as? State.Tapped)?.taps != 3) return false
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
        is State.Tapped -> current.taps >= 2 || now < current.lifted + MAX_GAP_MILLIS
        is State.NextTouch -> true
        else -> false
    }

    private fun follows(tap: State.Tapped, next: Sample) =
        next.time - tap.lifted in MIN_GAP_MILLIS..MAX_DRAG_GAP_MILLIS && abs(next.x - tap.landed.x) * 10 <= MAX_APART_KEYS

    private fun distance(a: Sample, b: Sample) = hypot((b.x - a.x) * 10, (b.y - a.y) * 4)

    companion object {
        const val BOTTOM_ROW_TOP = .75f
        /** How long each tap may last. Real first taps took up to 105 ms and real second taps up to 191 ms. */
        private const val FIRST_TAP_MILLIS = 130L
        private const val SECOND_TAP_MILLIS = 200L
        /** A touch that moves this far is not a tap. */
        private const val TAP_KEYS = .3f
        /** Real gaps between the taps took 37 to 124 ms. Shorter than the settle time, a key press can still cancel the first. */
        private const val MIN_GAP_MILLIS = Contact.SETTLE_MILLIS
        private const val MAX_GAP_MILLIS = 150L
        /**
         * A touch that goes on to drag may come this long after the tap before it. In the owner's recording two real
         * gestures came 153 and 173 ms after their taps; in the raw logs the longer gap took no typed words.
         */
        private const val MAX_DRAG_GAP_MILLIS = 200L
        /** Real taps of a pair landed up to 0.61 keys apart. */
        private const val MAX_APART_KEYS = 1f
        /** The first tap must come this long after the touch before it, which also leaves out a resting finger's series. */
        private const val ALONE_MILLIS = 200L
        /** The first tap must come this long after a key press. */
        private const val AFTER_KEY_MILLIS = 500L
        /** A second or third touch that stays down this long registers, before any slide. Resting touches last about 40 ms. */
        const val REGISTER_MILLIS = 70L
        /** How long a double tap waits for a third touch, which would make it part of a series or a word drag. */
        const val CONFIRM_MILLIS = MAX_GAP_MILLIS
        /** How far sideways the second or third touch must go to start a drag. */
        private const val DRAG_KEYS = .5f
        /** A sideways drag has to be at least this many times wider than it is tall. */
        private const val SIDEWAYS = 2f
        /**
         * Above Space, how many times wider than tall a drag must be. The owner's drags go about twice as far across as up,
         * and lean up most at their start, so 2 missed some. 1.5 caught 35 of 41 recorded gestures against 33.
         */
        private const val ABOVE_SIDEWAYS = 1.5f
        /**
         * Above Space, taps count on the whole height of the bottom letter row, as wide as the Space bar. The owner first chose
         * the lower half from a recording (35 of 41 gestures against 29), then asked for the whole row (2026-11-11). Over the
         * owner's raw log 17 (12,979 touches, 31,104 key presses) the whole row adds 2 gestures, takes no more swiped words,
         * and fires none within 600 ms before a key press. A key press always calls a gesture off, so no press is lost.
         */
        const val ZONE_TOP = .5f
        /**
         * From C's left edge to N's right edge, the whole of C, V, B and N (owner, 2026-11-11). Over raw log 17 that is one more
         * gesture and one more swiped word taken as one than the inner edges of C and N, and no press is lost: a press calls a
         * gesture off.
         */
        const val ZONE_LEFT = .3f
        const val ZONE_RIGHT = .7f

        /** Whether a touch that lands at [x], [y] may be part of a gesture: anywhere on Space, or in the zone above it. */
        fun inZone(x: Float, y: Float) = y >= BOTTOM_ROW_TOP || y >= ZONE_TOP && x >= ZONE_LEFT && x < ZONE_RIGHT
        /** How far up the second or third touch must go to start a drag. Real sideways drags went at most 0.2 rows up before they started. */
        private const val UP_ROWS = .6f
        /** Real drags moved at most 0.43 keys between two points. One finger can't go this far so fast. */
        private const val JUMP_KEYS = 1f

        /** The numbers that decide what counts as a gesture, for the raw input log. */
        val tuning: Map<String, Any> get() = mapOf(
            "firstTapMillis" to FIRST_TAP_MILLIS, "secondTapMillis" to SECOND_TAP_MILLIS, "tapKeys" to TAP_KEYS,
            "minGapMillis" to MIN_GAP_MILLIS, "maxGapMillis" to MAX_GAP_MILLIS, "maxApartKeys" to MAX_APART_KEYS,
            "aloneMillis" to ALONE_MILLIS, "afterKeyMillis" to AFTER_KEY_MILLIS, "confirmMillis" to CONFIRM_MILLIS,
            "dragKeys" to DRAG_KEYS, "sideways" to SIDEWAYS, "upRows" to UP_ROWS, "jumpKeys" to JUMP_KEYS,
            "maxDragGapMillis" to MAX_DRAG_GAP_MILLIS, "aboveSideways" to ABOVE_SIDEWAYS, "registerMillis" to REGISTER_MILLIS,
        )
    }
}
