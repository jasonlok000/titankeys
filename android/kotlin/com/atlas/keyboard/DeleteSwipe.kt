package com.atlas.keyboard

private const val BACKSPACE_LEFT = .9f
private const val FAR_ENOUGH = .7f
private const val MIDDLE_ROW_TOP = .25f
private const val MIDDLE_ROW_BOTTOM = .5f
private const val DRIFT = .06f

/**
 * A swipe from Backspace left to J or further, along the middle row, deletes the previous word with one thumb.
 * No word runs along that path, and a swipe for a word on P or L leaves the row to reach its next letter.
 */
internal fun isDeleteWordSwipe(points: List<Sample>): Boolean {
    val first = points.firstOrNull() ?: return false
    val last = points.last()
    return first.x >= BACKSPACE_LEFT && onMiddleRow(first.y) && last.x <= FAR_ENOUGH && onMiddleRow(last.y) &&
        points.all { it.y >= MIDDLE_ROW_TOP - DRIFT && it.y < MIDDLE_ROW_BOTTOM + DRIFT }
}

private fun onMiddleRow(y: Float) = y >= MIDDLE_ROW_TOP && y < MIDDLE_ROW_BOTTOM
