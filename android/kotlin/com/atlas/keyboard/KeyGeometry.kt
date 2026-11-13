package com.atlas.keyboard

/**
 * The real keys, in pixels on a screen [width] pixels wide: every drawing of the keys takes its boxes from here, so that each drawn
 * key sits over its real key. It is built without Android, so the checks can use it too.
 *
 * The shape is the Titan 2 Elite's, measured from Unihertz's own face-on drawing at 1080 px wide: keys 84 wide with gaps of 24,
 * 107 tall with a row every 123, and the three letter rows in the same columns. Q's left edge, a key's width and a gap's come
 * from [alignment], and the heights scale with a column (a key and a gap), so a calibrated keyboard is the real one, scaled.
 *
 * The touchpad's coordinates run from 0 to 1 across the ten columns, with column c's middle at (c + .5) / 10, and from 0 to 1 down
 * the four rows, with row r's middle at (r + .5) / 4. [x] and [y] map them so those middles land on the middles of the keys.
 */
class KeyGeometry(val width: Float, val alignment: KeyAlignment = KeyAlignment.current) {
    /** Q's left edge, one key's width, the gap between two keys, and a column: a key and a gap, all in pixels. */
    val left = alignment.left * width
    val key = alignment.key * width
    val gap = alignment.gap * width
    val pitch = key + gap
    /** P's right edge. */
    val right = left + COLUMNS * key + (COLUMNS - 1) * gap
    /** A key's height and a row: a key and the gap below it, in proportion to a column. */
    val keyHeight = pitch * KEY_HEIGHT / (KEY + GAP)
    val rowPitch = pitch * ROW / (KEY + GAP)
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
     * [Layout.bottomWidths], with the same gaps, and span the same edges.
     */
    val boxes: List<Box> = Layout.rows.withIndex().flatMap { (row, names) ->
        val top = rowTop(row)
        if (row < ROWS - 1) names.mapIndexed { column, name -> Box(name, row, keyLeft(column), top, keyLeft(column) + key, top + keyHeight) }
        else {
            // The keys share what the gaps leave of the span, each as wide as Layout says, out of the ten columns.
            val room = right - left - (names.size - 1) * gap
            var at = left
            names.mapIndexed { index, name ->
                val wide = room * Layout.bottomWidths[index] / COLUMNS
                Box(name, row, at, top, at + wide, top + keyHeight).also { at += wide + gap }
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

    /** Key [column]'s whole column, from the middle of the gap on its left to the middle of the gap on its right. */
    fun cell(column: Int): Pair<Float, Float> = keyLeft(column) - gap / 2 to keyLeft(column) + key + gap / 2

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
        // The measured face, in px at 1080 wide: they set the proportions, and the two edges set the size.
        const val KEY = 84f
        const val GAP = 24f
        const val KEY_HEIGHT = 107f
        const val ROW = 123f
        /** Ten keys and the nine gaps between them. */
        const val SPAN = COLUMNS * KEY + (COLUMNS - 1) * GAP

        /** How tall all four rows are on a screen [width] wide, as calibrated now. */
        fun heightFor(width: Float, alignment: KeyAlignment = KeyAlignment.current) = KeyGeometry(width, alignment).height
    }
}
