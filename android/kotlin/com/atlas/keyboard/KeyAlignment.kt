package com.atlas.keyboard

import kotlin.math.floor

/**
 * Lines our drawings of the physical keys up with the real keys, which sit just below the screen. A drawing places each key by
 * [Layout] across the ten key columns, from 0 to 1, and this moves it there: [scale] stretches the columns about the middle,
 * [offset] moves them all, and [rowOffsets] moves one row, from the top row to the bottom row. Offsets are in key widths.
 *
 * The values come from the Key calibration screen in TitanKeys Debug ([KeyCalibration]). The defaults leave every drawing exactly
 * as it was. A key moved past the edge of its drawing keeps its centre over the real key and gets narrower, so its label shrinks
 * to fit.
 */
class KeyAlignment(val scale: Float = 1f, val offset: Float = 0f, rowOffsets: FloatArray = FloatArray(ROWS)) {
    val rowOffsets: FloatArray = rowOffsets.copyOf(ROWS)
    /** Nothing moves: each drawing stays as [Layout] places it. */
    val identity = scale == 1f && offset == 0f && this.rowOffsets.all { it == 0f }

    /** Where [x], from 0 to 1 across the keys, is drawn on [row]. */
    fun x(x: Float, row: Int): Float =
        if (identity) x else .5f + (x - .5f) * scale + (offset + rowOffsets[row.coerceIn(0, ROWS - 1)]) / COLUMNS

    /**
     * Where a point of a trail is drawn, as its x, y and anything after them. [point]'s y runs from 0 to 1 down the four rows, and
     * between the middles of two rows the row offset moves smoothly from one to the other.
     */
    fun point(point: FloatArray): FloatArray {
        if (identity) return point
        val row = (point[1] * ROWS - .5f).coerceIn(0f, ROWS - 1f)
        val above = floor(row).toInt()
        val below = minOf(ROWS - 1, above + 1)
        val part = row - above
        val shift = rowOffsets[above] * (1 - part) + rowOffsets[below] * part
        return point.copyOf().also { it[0] = .5f + (point[0] - .5f) * scale + (offset + shift) / COLUMNS }
    }

    /** A key's left and right once aligned, from 0 to 1, and how much its label is shrunk to fit: 1 for not at all. */
    class Span(val left: Float, val right: Float, val fit: Float) {
        val centre get() = (left + right) / 2
    }

    /**
     * Where the key from [left] to [right] on [row] is drawn. Its centre follows [x]. Where that pushes it past 0 or 1, it narrows
     * about its centre to fit, and [Span.fit] says by how much, so that a label keeps to the key.
     */
    fun span(left: Float, right: Float, row: Int): Span {
        if (identity) return Span(left, right, 1f)
        val centre = x((left + right) / 2, row)
        val half = (right - left) / 2 * scale
        val room = minOf(half, centre, 1f - centre).coerceAtLeast(0f)
        return Span(centre - room, centre + room, if (half > 0f) room / half else 1f)
    }

    /** A key of the face, on its row, from [left] to [right] across the keys, before alignment. */
    class Key(val name: String, val row: Int, val left: Float, val right: Float)

    companion object {
        const val ROWS = 4
        const val COLUMNS = 10
        /** How far the calibration screen stretches the columns, and moves them, in key widths. */
        const val SCALE_MIN = .9f
        const val SCALE_MAX = 1.1f
        const val OFFSET_MAX = .5f
        val DEFAULT = KeyAlignment()
        /** The calibration screen's sliders: steps of .001 for the scale and of .01 key for an offset. */
        const val SCALE_STEPS = 200
        const val OFFSET_STEPS = 100

        fun scaleAt(progress: Int) = Math.round((SCALE_MIN + (SCALE_MAX - SCALE_MIN) * progress / SCALE_STEPS) * 1000) / 1000f
        fun scaleProgress(scale: Float) = Math.round((scale - SCALE_MIN) / (SCALE_MAX - SCALE_MIN) * SCALE_STEPS).coerceIn(0, SCALE_STEPS)
        fun offsetAt(progress: Int) = Math.round((-OFFSET_MAX + 2 * OFFSET_MAX * progress / OFFSET_STEPS) * 100) / 100f
        fun offsetProgress(offset: Float) = Math.round((offset + OFFSET_MAX) / (2 * OFFSET_MAX) * OFFSET_STEPS).coerceIn(0, OFFSET_STEPS)

        /** The alignment every drawing uses now. [KeyCalibration] loads it and keeps it up to date. */
        @Volatile @JvmStatic var current = DEFAULT

        /** Every key of [Layout], from the top row down, where the drawings put it before alignment. */
        val keys: List<Key> = Layout.rows.withIndex().flatMap { (row, names) ->
            var left = 0f
            names.mapIndexed { index, name ->
                val width = if (row == ROWS - 1) Layout.bottomWidths[index] else 1f
                Key(name, row, left / COLUMNS, (left + width) / COLUMNS).also { left += width }
            }
        }
    }
}
