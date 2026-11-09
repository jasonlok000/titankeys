package com.atlas.keyboard

private const val BACKSPACE_LEFT = .9f
private const val FAR_ENOUGH = .7f
private const val TOO_FAR = .5f
private const val MIDDLE_ROW_TOP = .25f
private const val MIDDLE_ROW_BOTTOM = .5f
private const val DRIFT = .1f
private const val TURN_BACK = .05f

/**
 * A swipe from Backspace left to J or H, along the middle row, deletes the previous word with one thumb.
 * Stopping there matters: long swipes for words on P or L can stay on the row, but they carry on further left.
 */
internal fun isDeleteWordSwipe(points: List<Sample>): Boolean {
    val first = points.firstOrNull() ?: return false
    val last = points.last()
    return first.x >= BACKSPACE_LEFT && onMiddleRow(first.y) && last.x <= FAR_ENOUGH && last.x >= TOO_FAR && onMiddleRow(last.y) &&
        points.all { it.y >= MIDDLE_ROW_TOP - DRIFT && it.y < MIDDLE_ROW_BOTTOM + DRIFT && it.x >= last.x - TURN_BACK }
}

private fun onMiddleRow(y: Float) = y >= MIDDLE_ROW_TOP && y < MIDDLE_ROW_BOTTOM
