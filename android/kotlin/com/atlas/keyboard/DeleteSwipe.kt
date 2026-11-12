package com.atlas.keyboard

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToLong

private const val RIGHT_EDGE = .875f
private const val EDGE_START_TOP = .1f
private const val EDGE_START_BOTTOM = .62f
private const val EDGE_END_TOP = .175f
private const val EDGE_END_BOTTOM = .625f
private const val EDGE_WANDER = 1.1f

private const val INNER_EDGE = .76f
private const val MIDDLE_ROW_TOP = .25f
private const val INNER_START_BOTTOM = .56f
private const val INNER_END_BOTTOM = .6f
private const val INNER_TILT = .1f
private const val INNER_WANDER = 1.1f

private const val MIN_TRAVEL = .1f
private const val TURN_BACK = .05f
/**
 * A flick is short and quick. A longer stroke, or a slower one, is a word: a swipe that starts on P or Backspace and runs left
 * along the keys, such as "press", is straight enough to pass for a flick, but it goes much further and takes longer. In the
 * owner's logs, half the real flicks are about 2 keys and 80 ms, and nearly all are under 5.5 keys and 600 ms; the test swipes
 * that deleted by mistake were 6 to 8.5 keys, or took 600 ms to 1.4 s.
 */
private const val MAX_PATH_KEYS = 5.5f
private const val MAX_FLICK_MILLIS = 600L
/** The flick has arrived once it is this close to where it ends, in key widths; a finger resting there after that is a hold. */
private const val ARRIVED_KEYS = .25f

/**
 * The delete zone as one rectangle, as wide and as tall as the L shape of the right edge and the middle row together, so that it
 * also takes in the edges of I, O, M and Sym. Off, a flick starts only in the L shape. A flick from anywhere in the rectangle is
 * read by the right edge's rules.
 * Off: a replay of the owner's real swipes found that the rectangle deletes about one kept word in sixty that today's zone keeps,
 * while it finds few flicks that today's zone misses. The guide still draws the rectangle: see [guideDeleteBox].
 */
internal const val DELETE_RECTANGLE = false

/**
 * Where a level delete flick can start, as boxes of left, top, right and bottom in touchpad space, read from the numbers above so
 * that the guide's shaded zone cannot drift from the gesture: the right edge with Backspace, and further in on the middle row from
 * K. A level flick ends at the height it starts, so the limits for its end apply too.
 */
internal val deleteStartBoxes: List<FloatArray> get() = if (DELETE_RECTANGLE) listOf(guideDeleteBox) else listOf(
    floatArrayOf(RIGHT_EDGE, max(EDGE_START_TOP, EDGE_END_TOP), 1f, minOf(EDGE_START_BOTTOM, EDGE_END_BOTTOM)),
    floatArrayOf(INNER_EDGE, MIDDLE_ROW_TOP, RIGHT_EDGE, minOf(INNER_START_BOTTOM, INNER_END_BOTTOM)),
)

/**
 * The patch that the guide shades for the delete flick, whatever [DELETE_RECTANGLE] is: one rectangle over the right edge, from
 * I and O down to M and Sym. It is simple to show and to aim for, and the flicks that it teaches start inside the real zone.
 */
internal val guideDeleteBox: FloatArray get() =
    floatArrayOf(INNER_EDGE, max(EDGE_START_TOP, EDGE_END_TOP), 1f, minOf(EDGE_START_BOTTOM, EDGE_END_BOTTOM))

/** Whether a level delete flick can start at this point, as [isDeleteWordSwipe] decides. */
internal fun startsDeleteFlick(x: Float, y: Float) = deleteStartBoxes.any { (left, top, right, bottom) -> x >= left && x < right && y >= top && y < bottom }

/** Where the guide's ghost starts its delete flick: on Backspace, in the middle of the zone, on the middle row. */
internal val GUIDE_FLICK_START = floatArrayOf((RIGHT_EDGE + .95f) / 2, (MIDDLE_ROW_TOP + INNER_START_BOTTOM) / 2)
/** Where it ends: about four keys to the left, like a real flick. */
internal const val GUIDE_FLICK_END_X = .62f

/**
 * A finger that stays this long after a delete flick starts deleting more words: a quarter of the 400 ms it was, as a test
 * (owner, 2026-11-11).
 */
private const val HOLD_MILLIS = 100L

/** The pauses that TitanKeys Debug's Haptics lab can try with its slider. */
internal val HOLD_PAUSE_RANGE = 25L..400L

/**
 * A pause tried in TitanKeys Debug's Haptics lab, or null for [HOLD_MILLIS]. Only [Haptics.readHoldPause] sets it, and only in
 * the debug build, so every other build always has [HOLD_MILLIS].
 */
@Volatile
internal var triedHoldPauseMillis: Long? = null

/**
 * How long the finger stays still after a delete flick before the hold takes its first word. The hold and the haptics lab's
 * simulated hold both read it here, so a change to the pause moves both.
 */
internal fun deleteHoldPauseMillis() = triedHoldPauseMillis ?: HOLD_MILLIS
/** The finger counts as staying while it is this close to where it stopped, in key widths. */
private const val HOLD_KEYS = .25f
/** The wait between words after a short flick, close to how quickly a person flicks again by hand. */
internal const val REPEAT_SLOW_MILLIS = 350L
/** The wait between words after a long flick. */
private const val REPEAT_FAST_MILLIS = 80L
/** A flick of this many key widths or fewer is short. Between short and long, the wait follows a straight line. */
private const val REPEAT_SHORT_KEYS = 2f
/** A flick of this many key widths or more is long: the longest that still counts as a flick, [MAX_PATH_KEYS]. */
private const val REPEAT_LONG_KEYS = 5.5f
/** While an earlier edit is still going through, the next word looks again after this time. */
private const val BUSY_MILLIS = 20L

/**
 * A quick straight flick to the left from the right of the keyboard deletes the previous word with one thumb.
 * The numbers are fitted to real strokes. A thumb doesn't always land on Backspace, so a stroke can also start
 * as far in as K, but that one has to stay level on the middle row, where almost no real words run in a line.
 * Ending below the top of the keyboard keeps out straight words along the top row, such as "pit".
 */
internal fun isDeleteWordSwipe(points: List<Sample>): Boolean {
    val first = points.firstOrNull() ?: return false
    val last = points.last()
    if (first.x - last.x < MIN_TRAVEL || points.any { it.x < last.x - TURN_BACK }) return false
    val path = pathKeys(points)
    if (path > MAX_PATH_KEYS || flickMillis(points) > MAX_FLICK_MILLIS) return false
    val wander = path / max(hypot((last.x - first.x) * 10, (last.y - first.y) * 4), 1e-6f)
    if (first.x >= RIGHT_EDGE || DELETE_RECTANGLE && first.x >= INNER_EDGE) {
        return first.y >= EDGE_START_TOP && first.y < EDGE_START_BOTTOM &&
            last.y >= EDGE_END_TOP && last.y < EDGE_END_BOTTOM && wander <= EDGE_WANDER
    }
    return first.x >= INNER_EDGE && first.y >= MIDDLE_ROW_TOP && first.y < INNER_START_BOTTOM &&
        last.y >= MIDDLE_ROW_TOP && last.y < INNER_END_BOTTOM && abs(last.y - first.y) <= INNER_TILT && wander <= INNER_WANDER
}

/** The numbers above, for the raw input log. */
internal val deleteSwipeTuning: Map<String, Any> get() = mapOf(
    "rightEdge" to RIGHT_EDGE, "edgeStartTop" to EDGE_START_TOP, "edgeStartBottom" to EDGE_START_BOTTOM,
    "edgeEndTop" to EDGE_END_TOP, "edgeEndBottom" to EDGE_END_BOTTOM, "edgeWander" to EDGE_WANDER,
    "innerEdge" to INNER_EDGE, "middleRowTop" to MIDDLE_ROW_TOP, "innerStartBottom" to INNER_START_BOTTOM,
    "innerEndBottom" to INNER_END_BOTTOM, "innerTilt" to INNER_TILT, "innerWander" to INNER_WANDER,
    "minTravel" to MIN_TRAVEL, "turnBack" to TURN_BACK, "deleteRectangle" to DELETE_RECTANGLE,
    "deleteHoldMillis" to deleteHoldPauseMillis(), "deleteHoldKeys" to HOLD_KEYS, "deleteRepeatSlowMillis" to REPEAT_SLOW_MILLIS,
    "deleteRepeatFastMillis" to REPEAT_FAST_MILLIS, "deleteRepeatShortKeys" to REPEAT_SHORT_KEYS,
    "deleteRepeatLongKeys" to REPEAT_LONG_KEYS, "deleteMaxPathKeys" to MAX_PATH_KEYS, "deleteMaxMillis" to MAX_FLICK_MILLIS,
)

/** How long the stroke took to arrive where it ends: a finger that then rests there, for a hold, does not make it slower. */
internal fun flickMillis(points: List<Sample>): Long {
    val first = points.firstOrNull() ?: return 0
    val last = points.last()
    val arrived = points.first { hypot((last.x - it.x) * 10, (last.y - it.y) * 4) <= ARRIVED_KEYS }
    return arrived.time - first.time
}

private fun pathKeys(points: List<Sample>) = points.zipWithNext { a, b -> hypot((b.x - a.x) * 10, (b.y - a.y) * 4) }.sum()

/**
 * A delete flick whose finger stays on the keys deletes more and more words until the finger lifts. The further the finger is
 * from where the flick started, the faster: slide further left and it speeds up, back towards Backspace and it slows, smoothly,
 * between [REPEAT_SLOW_MILLIS] and [REPEAT_FAST_MILLIS] a word. This class only keeps the times. [contact] knows if the finger is still there, so a lift,
 * a key press or a new field ends the hold without a call to this class, and a test can run it with no clock.
 */
internal class DeleteHold(private val contact: Contact) {
    private var still: Sample? = null
    private var started = false
    private var nextAt = 0L
    /** Where the flick started, which the finger's distance is measured from while it holds. */
    private var origin: Sample? = null
    /** When the last word went. */
    private var lastAt = 0L
    /** The wait between words. It is set while the flick is read, because a contact that deletes keeps no points. */
    var repeatMillis = REPEAT_SLOW_MILLIS
        private set

    /**
     * Follows the finger after it moves, and reads the flick as a lift would. Returns when the next word is due,
     * or null if the finger is not on a flick that can repeat. [allowed] is false where a delete flick would not count now.
     */
    fun moved(allowed: Boolean): Long? {
        if (contact.deleting) return nextAt
        val points = contact.points
        if (!allowed || !contact.swipes || contact.paging || contact.travel == null || !isDeleteWordSwipe(points)) {
            still = null
            return null
        }
        val last = points.last()
        val held = still?.takeIf { hypot((last.x - it.x) * 10, (last.y - it.y) * 4) <= HOLD_KEYS } ?: last
        still = held
        origin = points.first()
        repeatMillis = repeatMillis(keysFrom(points.first(), held))
        return held.time + deleteHoldPauseMillis()
    }

    /**
     * The finger moved while the hold deletes: the wait between words follows how far it now is from where the flick started.
     * Returns when the next word is due, sooner or later than before, or null if nothing is deleting.
     */
    fun follow(finger: Sample): Long? {
        if (!contact.deleting || !started || !finger.valid) return null
        val from = origin ?: return null
        repeatMillis = repeatMillis(keysFrom(from, finger))
        nextAt = lastAt + repeatMillis
        return nextAt
    }

    private fun keysFrom(from: Sample, to: Sample) = hypot((to.x - from.x) * 10, (to.y - from.y) * 4)

    /**
     * The timer ran. Deletes a word if one is due, through [delete]: the flick itself for the first word, then null for each
     * word after it. Returns when to run again, or null when the hold is over. [free] is false while an earlier
     * edit is still going through, so that a lift never leaves words waiting to be deleted.
     */
    fun step(now: Long, free: Boolean, delete: (Contact.Outcome.Swipe?) -> Unit): Long? {
        if (!contact.deleting) {
            if (started) return null
            val at = (still ?: return null).time + deleteHoldPauseMillis()
            if (now < at) return at
            if (!free) return now + BUSY_MILLIS
            delete(contact.holdDelete(now) ?: return null)
        } else {
            if (now < nextAt) return nextAt
            if (!free) return now + BUSY_MILLIS
            delete(null)
        }
        lastAt = now
        nextAt = now + repeatMillis
        started = true
        return nextAt
    }

    /** A finger further from the start waits less between words. */
    private fun repeatMillis(flickKeys: Float): Long {
        val longer = ((flickKeys - REPEAT_SHORT_KEYS) / (REPEAT_LONG_KEYS - REPEAT_SHORT_KEYS)).coerceIn(0f, 1f)
        return (REPEAT_SLOW_MILLIS + (REPEAT_FAST_MILLIS - REPEAT_SLOW_MILLIS) * longer).roundToLong()
    }

    fun reset() {
        still = null
        started = false
        nextAt = 0
        lastAt = 0
        origin = null
    }
}
