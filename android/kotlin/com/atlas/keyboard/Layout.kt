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
    const val FN = "fn"
    /** Where a finger counts as resting on P and not on the key beside it. See [restingKeyAt]. */
    private const val P_FROM_LEFT = .875f
    private const val P_TO_BOTTOM = .3f

    val rows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "⌫"),
        listOf(ALT, "Z", "X", "C", "V", "B", "N", "M", SYM, "↵"),
        listOf("⇧", "○", "◁", SPACE, "▦", FN, "⇧"),
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

    /**
     * The key under a finger that rests on the touchpad, for the Sym panel. The touchpad stops at the middle of P, because the
     * sensor ends there and calibration maps its edge to P's centre, so P has only half a column to be read from and a finger
     * on it reads partly on the keys beside it. Here P also takes the right of O and the top of Backspace.
     */
    fun restingKeyAt(x: Float, y: Float): String {
        val key = keyAt(x, y)
        return if (key == "O" && x >= P_FROM_LEFT || key == "⌫" && y < P_TO_BOTTOM) "P" else key
    }

    /** Whether a touchpad height is on the bottom row of keys. */
    fun isBottomRow(y: Float) = y >= .75f

    /** The left and right edges of a bottom-row key, from 0 to 1 across the keyboard. */
    fun bottomSpan(key: String): Pair<Float, Float> {
        val index = rows[3].indexOf(key)
        val left = bottomWidths.take(index).sum() / 10
        return left to left + bottomWidths[index] / 10
    }

    /** A letter's centre in touchpad coordinates. */
    fun centre(letter: Char): Pair<Float, Float> {
        val index = letter.lowercaseChar() - 'a'
        return KEY_X[index] to KEY_Y[index] * .75f
    }
}
