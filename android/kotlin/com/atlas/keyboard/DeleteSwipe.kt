package com.atlas.keyboard

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

private const val RIGHT_EDGE = .875f
private const val EDGE_START_TOP = .1f
private const val EDGE_START_BOTTOM = .62f
private const val EDGE_END_TOP = .175f
private const val EDGE_END_BOTTOM = .625f
private const val EDGE_WANDER = 1.15f

private const val INNER_EDGE = .76f
private const val MIDDLE_ROW_TOP = .25f
private const val INNER_START_BOTTOM = .56f
private const val INNER_END_BOTTOM = .6f
private const val INNER_TILT = .1f
private const val INNER_WANDER = 1.1f

private const val MIN_TRAVEL = .1f
private const val TURN_BACK = .05f

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
    val wander = pathKeys(points) / max(hypot((last.x - first.x) * 10, (last.y - first.y) * 4), 1e-6f)
    if (first.x >= RIGHT_EDGE) {
        return first.y >= EDGE_START_TOP && first.y < EDGE_START_BOTTOM &&
            last.y >= EDGE_END_TOP && last.y < EDGE_END_BOTTOM && wander <= EDGE_WANDER
    }
    return first.x >= INNER_EDGE && first.y >= MIDDLE_ROW_TOP && first.y < INNER_START_BOTTOM &&
        last.y >= MIDDLE_ROW_TOP && last.y < INNER_END_BOTTOM && abs(last.y - first.y) <= INNER_TILT && wander <= INNER_WANDER
}

private fun pathKeys(points: List<Sample>) = points.zipWithNext { a, b -> hypot((b.x - a.x) * 10, (b.y - a.y) * 4) }.sum()
