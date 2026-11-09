package com.atlas.keyboard

/**
 * The Titan 2 keyboard face. Touchpad coordinates run from 0 to 1 across the ten columns and down the
 * four rows; the recogniser's letter space stretches the three letter rows over the same 0 to 1.
 */
object Layout {
    const val LETTERS = "abcdefghijklmnopqrstuvwxyz"
    const val SPACE = "space"
    const val ALT = "alt"
    const val SYM = "sym"

    val rows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "⌫"),
        listOf(ALT, "Z", "X", "C", "V", "B", "N", "M", SYM, "↵"),
        listOf("⇧", "○", "◁", SPACE, "▦", "fn", "⇧"),
    )
    val bottomWidths = floatArrayOf(1.2f, 1.2f, 1.2f, 2.8f, 1.2f, 1.2f, 1.2f)
    /** The letter keys, from the top row down: the order in which the Sym panel fills them. */
    val panelLetters = rows.take(3).flatten().filter { isLetter(it) }

    /** Letter centres in letter space, indexed by [LETTERS]. */
    @JvmField val KEY_X = FloatArray(26)
    @JvmField val KEY_Y = FloatArray(26)

    init {
        for (row in 0..2) for ((column, key) in rows[row].withIndex()) {
            if (!isLetter(key)) continue
            KEY_X[key[0] - 'A'] = (column + .5f) / 10
            KEY_Y[key[0] - 'A'] = (row + .5f) / 3
        }
    }

    fun isLetter(key: String) = key.length == 1 && key[0] in 'A'..'Z'

    /** The key under a touchpad position, or an empty string outside the keyboard. */
    fun keyAt(x: Float, y: Float): String {
        if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) return ""
        val column = x * 10
        val row = minOf(3, (y * 4).toInt())
        if (row < 3) return rows[row][minOf(9, column.toInt())]
        var right = 0f
        for ((index, width) in bottomWidths.withIndex()) {
            right += width
            if (column < right) return rows[3][index]
        }
        return rows[3].last()
    }

    /** A letter's centre in touchpad coordinates. */
    fun centre(letter: Char): Pair<Float, Float> {
        val index = letter.lowercaseChar() - 'a'
        return KEY_X[index] to KEY_Y[index] * .75f
    }
}
