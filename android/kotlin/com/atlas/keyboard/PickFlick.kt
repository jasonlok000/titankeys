package com.atlas.keyboard

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * A short, quick flick straight up on the letter keys picks a word from the strip: the left, middle or right word, by the third
 * of the keys where the flick starts. It is a setting.
 *
 * A word's swipe almost never goes straight up for one row and stops. The few that do are two letters one above the other, such
 * as "lo", so a flick must also be quick, straight, near vertical, and not longer than [MAX_RISE] rows: "by" and "my" go up two
 * rows, and a short "my" leans 26 degrees. The Space row and Backspace are not letters, so steering from Space and the delete
 * flick never start one.
 */
internal object PickFlick {
    /** How far up a flick goes, in rows. A contact must go [Contact.SWIPE_KEYS] to be more than a tap. */
    const val MIN_RISE = .6f
    const val MAX_RISE = 1.25f
    /** How far across a flick may drift, in key widths. */
    const val MAX_DRIFT = .6f
    /**
     * How far a flick may lean from straight up, in degrees, with keys and rows as equal units. On 6,259 recorded swipes, this
     * many read as flicks at each bound: 3 at 5, 8 at 10, 14 at 15, 29 at 27. Two of them were within 1 degree of straight up,
     * so no bound reaches none. Loosen it only if real flicks lean more than this.
     */
    const val MAX_ANGLE_DEGREES = 10f
    private val MAX_SLANT = tan(Math.toRadians(MAX_ANGLE_DEGREES.toDouble())).toFloat()
    /** How much longer than a straight line its path may be. */
    const val MAX_WANDER = 1.3f
    /** How far, in rows, it may dip below where it landed or fall back from its top. */
    const val MAX_DIP = .15f
    const val MAX_MILLIS = 300L

    /** The third of the keys where a flick starts, 0 to 2 from the left, or null if [points] is not a flick. */
    fun third(points: List<Sample>): Int? {
        if (points.size < 2) return null
        val first = points.first()
        val last = points.last()
        if (!Layout.isLetter(Layout.keyAt(first.x, first.y)) || !Layout.isLetter(Layout.keyAt(last.x, last.y))) return null
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
        return min(2, (first.x * 3).toInt().coerceAtLeast(0))
    }

    /** The numbers above, for the raw input log. */
    val tuning: Map<String, Any> get() = mapOf(
        "flickMinRise" to MIN_RISE, "flickMaxRise" to MAX_RISE, "flickMaxDrift" to MAX_DRIFT, "flickMaxAngleDegrees" to MAX_ANGLE_DEGREES,
        "flickMaxWander" to MAX_WANDER, "flickMaxDip" to MAX_DIP, "flickMaxMillis" to MAX_MILLIS,
    )
}
