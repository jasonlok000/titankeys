package com.atlas.keyboard

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * A short, quick flick up from the bottom row of letters picks a word from the strip: the left, middle or right word, by where
 * the flick starts. Z, X and C pick the left word, V and B the middle word, N and M the right word. It is a setting.
 *
 * The flick starts on Z to M and lifts on the row above, A to L. "by" and "my" go on to the top row, so they stay swipes, and so
 * does a stroke that leans more than [MAX_ANGLE_DEGREES], or is slow or bent. The Space row and Backspace are not letters, so
 * steering from Space and the delete flick never start one. These numbers come from the owner's flicks in the phone round of
 * 2026-11-10: from the bottom row to the middle row, 0.9 to 1.5 rows up, up to 25 degrees, in 120 to 210 ms.
 */
internal object PickFlick {
    /** How far up a flick goes, in rows. A contact must go [Contact.SWIPE_KEYS] to be more than a tap. */
    const val MIN_RISE = .6f
    const val MAX_RISE = 1.75f
    /** How far across a flick may drift, in key widths. */
    const val MAX_DRIFT = .6f
    /**
     * How far a flick may lean from straight up, in degrees, with keys and rows as equal units. At 25 it caught 12 of the owner's 17
     * tries, and 6 of 5,572 recorded swipes that typed a word read as flicks. The other matching strokes turned pages in the Sym panel,
     * where flicks are off.
     */
    const val MAX_ANGLE_DEGREES = 25f
    private val MAX_SLANT = tan(Math.toRadians(MAX_ANGLE_DEGREES.toDouble())).toFloat()
    /** How much longer than a straight line its path may be. */
    const val MAX_WANDER = 1.3f
    /** How far, in rows, it may dip below where it landed or fall back from its top. */
    const val MAX_DIP = .15f
    const val MAX_MILLIS = 300L
    /** Where the bottom row of letters splits into the left, middle and right word: Z X C, V B, N M. */
    const val MIDDLE_FROM = .4f
    const val RIGHT_FROM = .6f

    /** The word a flick picks, 0 to 2 from the left, or null if [points] is not a flick. */
    fun third(points: List<Sample>): Int? {
        if (points.size < 2) return null
        val first = points.first()
        val last = points.last()
        if (!Layout.isLetter(Layout.keyAt(first.x, first.y)) || !Layout.isLetter(Layout.keyAt(last.x, last.y))) return null
        if (row(first.y) != BOTTOM_LETTERS || row(last.y) != MIDDLE_LETTERS) return null
        if (last.time - first.time !in 0..MAX_MILLIS) return null
        val rise = (first.y - last.y) * 4
        val drift = abs(last.x - first.x) * 10
        if (rise !in MIN_RISE..MAX_RISE || drift > MAX_DRIFT || drift > rise * MAX_SLANT) return null
        var lowest = first.y
        var highest = first.y
        var path = 0f
        for ((a, b) in points.zipWithNext()) {
            path += hypot((b.x - a.x) * 10, (b.y - a.y) * 4)
            lowest = max(lowest, b.y)
            highest = min(highest, b.y)
        }
        if ((lowest - first.y) * 4 > MAX_DIP || (last.y - highest) * 4 > MAX_DIP) return null
        if (path > hypot(drift, rise) * MAX_WANDER) return null
        return when {
            first.x < MIDDLE_FROM -> 0
            first.x < RIGHT_FROM -> 1
            else -> 2
        }
    }

    private const val MIDDLE_LETTERS = 1
    private const val BOTTOM_LETTERS = 2
    private fun row(y: Float) = (y * 4).toInt()

    /** The numbers above, for the raw input log. */
    val tuning: Map<String, Any> get() = mapOf(
        "flickMinRise" to MIN_RISE, "flickMaxRise" to MAX_RISE, "flickMaxDrift" to MAX_DRIFT, "flickMaxAngleDegrees" to MAX_ANGLE_DEGREES,
        "flickMaxWander" to MAX_WANDER, "flickMaxDip" to MAX_DIP, "flickMaxMillis" to MAX_MILLIS,
        "flickMiddleFrom" to MIDDLE_FROM, "flickRightFrom" to RIGHT_FROM,
    )
}
