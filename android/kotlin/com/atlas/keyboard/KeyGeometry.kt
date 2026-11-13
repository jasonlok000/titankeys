package com.atlas.keyboard

/**
 * The real keys, in pixels on a screen [width] pixels wide: every drawing of the keys takes its boxes from here, so that each drawn
 * key sits over its real key. It is built without Android, so the checks can use it too.
 *
 * The shape is the Titan 2 Elite's, as the owner tuned it on the phone (2026-11-12): **square keys with no gap between keys** in a
 * row, so each key takes its whole column, and 10.5 px between rows on the 1076 px wide screen. Each column is a key and a gap of
 * [alignment] (the calibration: 107.5 px, centred), from the middle of the gap left of the key to the middle of the gap right of
 * it, so every column, and the middle of every key, is where the calibration puts it. The heights scale with a column, so a
 * calibrated keyboard is the real one, scaled. [scale] squashes the heights only, as the guide's drawing does ([DRAWN]).
 *
 * The touchpad's coordinates run from 0 to 1 across the ten columns, with column c's middle at (c + .5) / 10, and from 0 to 1 down
 * the four rows, with row r's middle at (r + .5) / 4. [x] and [y] map them so those middles land on the middles of the keys.
 */
class KeyGeometry(val width: Float, val alignment: KeyAlignment = KeyAlignment.current, val scale: Float = 1f) {
    /** A column: a key and a gap of the calibration, in pixels. */
    val pitch = (alignment.key + alignment.gap) * width
    /** Q's left edge: where Q's column starts, half the calibration's gap left of its key. */
    val left = (alignment.left - alignment.gap / 2) * width
    /** One key's width: its whole column, as there is no gap between keys. */
    val key = pitch
    val gap = 0f
    /** P's right edge. */
    val right = left + COLUMNS * pitch
    /** A key's height and a row (a key and the space below it), in proportion to a column, times [scale]. */
    val keyHeight = pitch * KEY_HEIGHT * scale
    val rowPitch = pitch * ROW * scale
    /** From the top of the top row to the bottom of the bottom row. */
    val height = (ROWS - 1) * rowPitch + keyHeight
    /** The three letter rows alone. */
    val letterHeight = 2 * rowPitch + keyHeight

    /** A key's box in pixels, and its place on the face. */
    class Box(val name: String, val row: Int, val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        val centreX get() = (left + right) / 2
        val centreY get() = (top + bottom) / 2
    }

    /** The left edge of the key in [column], from 0, on a letter row. */
    fun keyLeft(column: Int) = left + column * pitch

    /** The top of [row], from 0 at the top row. */
    fun rowTop(row: Int) = row * rowPitch

    /**
     * Every key of [Layout], from the top row down, in its order there. The bottom row's keys take their widths from
     * [Layout.bottomWidths], out of the ten columns, and span the same edges.
     */
    val boxes: List<Box> = Layout.rows.withIndex().flatMap { (row, names) ->
        val top = rowTop(row)
        if (row < ROWS - 1) names.mapIndexed { column, name -> Box(name, row, keyLeft(column), top, keyLeft(column) + key, top + keyHeight) }
        else {
            var at = left
            names.mapIndexed { index, name ->
                val wide = (right - left) * Layout.bottomWidths[index] / COLUMNS
                Box(name, row, at, top, at + wide, top + keyHeight).also { at += wide }
            }
        }
    }

    /** Where touchpad [x], from 0 to 1 across the ten columns, is in pixels: each column's middle on its key's middle. */
    fun x(x: Float) = left + key / 2 + (x * COLUMNS - .5f) * pitch

    /** Where touchpad [y], from 0 to 1 down the four rows, is in pixels: each row's middle on its keys' middle. */
    fun y(y: Float) = keyHeight / 2 + (y * ROWS - .5f) * rowPitch

    /** The touchpad's x at pixel [x]: the other way from [x]. */
    fun touchX(x: Float) = ((x - left - key / 2) / pitch + .5f) / COLUMNS

    /** The touchpad's y at pixel [y]: the other way from [y]. */
    fun touchY(y: Float) = ((y - keyHeight / 2) / rowPitch + .5f) / ROWS

    /** Key [column]'s whole column, from the middle of the calibration's gap on its left to the middle of the one on its right. */
    fun cell(column: Int): Pair<Float, Float> = keyLeft(column) to keyLeft(column) + pitch

    /**
     * A box over [row]'s keys, from their top to their bottom, across from touchpad [fromX] to [toX]: for a zone that lies on one
     * row, such as the held delete's.
     */
    fun onRow(row: Int, fromX: Float, toX: Float) = Box("", row, x(fromX), rowTop(row), x(toX), rowTop(row) + keyHeight)

    /** The box from touchpad point [left], [top] to [right], [bottom], in pixels. */
    fun area(left: Float, top: Float, right: Float, bottom: Float) = Box("", -1, x(left), y(top), x(right), y(bottom))

    companion object {
        const val ROWS = 4
        const val COLUMNS = 10
        // Unihertz's face-on drawing, in px at 1080 wide: a key 84 and a gap 24. Only the calibration uses them now, for how a
        // column splits into key and gap on its screen ([KeyAlignment]); the real face has no gap between keys.
        const val KEY = 84f
        const val GAP = 24f
        /** Ten keys and the nine gaps between them, in the measured drawing. */
        const val SPAN = COLUMNS * KEY + (COLUMNS - 1) * GAP
        /**
         * A key's height and a row's, as parts of a column (owner, 2026-11-12): the keys are square, and the rows are 10.5 px apart
         * when a column is 107.5 px, as on the phone.
         */
        const val KEY_HEIGHT = 1f
        const val ROW = 1f + 10.5f / 107.5f
        /** The guide's and the playground's drawing: the same width and 60% of the real height (owner, 2026-11-12). */
        const val DRAWN = .6f

        /** How tall all four rows are on a screen [width] wide, as calibrated now, at [scale]. */
        fun heightFor(width: Float, alignment: KeyAlignment = KeyAlignment.current, scale: Float = 1f) =
            KeyGeometry(width, alignment, scale).height
    }
}
