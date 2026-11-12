package com.atlas.keyboard

import kotlin.math.abs

/**
 * The Titan 2 keyboard face. Touchpad coordinates run from 0 to 1 across the ten columns and down the
 * four rows; the recogniser's letter space stretches the three letter rows over the same 0 to 1.
 */
object Layout {
    const val LETTERS = "abcdefghijklmnopqrstuvwxyz"
    const val SPACE = "space"

    /**
     * The key of the layout for an Android key code, for the debug build's panel check: a letter, Alt, Sym, Backspace, Enter or
     * Space. The codes are written out, as this file is also built without Android: A is 29 to Z 54, Alt 57 and 58, Space 62,
     * Sym 63, Enter 66 and Backspace (DEL) 67.
     */
    fun keyForCode(code: Int): String? = when (code) {
        in 29..54 -> ('A' + (code - 29)).toString()
        67 -> BACKSPACE
        66 -> ENTER
        57, 58 -> ALT
        63 -> SYM
        62 -> SPACE
        else -> null
    }
    const val ALT = "alt"
    const val SYM = "sym"
    const val FN = "fn"
    const val BACKSPACE = "⌫"
    const val ENTER = "↵"
    /** The Sym panel shows the page position on this key, and no symbol or emoji. The touchpad cannot read it well. */
    const val PAGE_POSITION = "P"
    /** On the symbols and emoji, A holds no item and opens the clipboard. The touchpad cannot read it well, but the key can. */
    const val CLIPBOARD_KEY = "A"
    /** Where a finger counts as resting on P and not on the key beside it. See [restingKeyAt]. */
    private const val LAST_COLUMN_FROM_LEFT = .875f
    private const val P_TO_BOTTOM = .3f
    /** Real pushes of Back moved at most .055 across the touchpad before the key went down. */
    private const val BOTTOM_ROW_MOVE = .06f

    val rows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", BACKSPACE),
        listOf(ALT, "Z", "X", "C", "V", "B", "N", "M", SYM, ENTER),
        listOf("⇧", "○", "◁", SPACE, "▦", FN, "⇧"),
    )
    val bottomWidths = floatArrayOf(1.2f, 1.2f, 1.2f, 2.8f, 1.2f, 1.2f, 1.2f)
    /**
     * How far in from each side of the screen, in dp, the guide's text and the word menu's word keep. It no longer places the
     * keys: every drawing of them takes its columns from [KeyGeometry], which has the real keys span the screen.
     */
    const val KEY_SIDE_DP = 18
    /** Every letter key, from the top row down. */
    val letterKeys = rows.take(3).flatten().filter { isLetter(it) }
    /**
     * The letter keys that hold a symbol or an emoji: the order in which the Sym panel fills them. The touchpad cannot read the
     * first and last columns well, so Q, A and P hold none.
     */
    val panelLetters = letterKeys - setOf("Q", "A", PAGE_POSITION)

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

    /**
     * Whether a finger that rests on this key marks it in the Sym panel. The keys with an icon never do. On a page of symbols or emoji,
     * P does not either, because the touchpad's sensor ends there.
     */
    fun restMarks(key: String, itemPage: Boolean) = isLetter(key) && !(itemPage && key == PAGE_POSITION)

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
     * The key under a finger that rests on the touchpad, for the quick settings page, where P has a setting. The touchpad stops
     * at the middle of P, because the sensor ends there and calibration maps its edge to P's centre, so P has only half a column
     * to be read from and a finger on it reads partly on the keys beside it. Here P also takes the right of O and the top of
     * Backspace.
     */
    fun restingKeyAt(x: Float, y: Float): String {
        val key = keyAt(x, y)
        return if (key == "O" && x >= LAST_COLUMN_FROM_LEFT || key == BACKSPACE && y < P_TO_BOTTOM) "P" else key
    }

    /** Whether a touchpad height is on the bottom row of keys. */
    fun isBottomRow(y: Float) = y >= .75f

    /**
     * Whether a finger that landed on the bottom row at [landedX] has moved along the row, so that it is choosing a group of emoji.
     * A finger that only lands there can be on its way to push Back, which reads as a touch on this row first.
     */
    fun movedAlongBottomRow(landedX: Float, x: Float) = abs(x - landedX) >= BOTTOM_ROW_MOVE

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
