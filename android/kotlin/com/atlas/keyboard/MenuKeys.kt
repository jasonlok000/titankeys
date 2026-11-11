package com.atlas.keyboard

/**
 * The word menu on the strip, picked with the keys, as the Sym panel's items are: rest a finger on a key to mark a button, and
 * press the key to pick it. Its three buttons sit over three key columns, U for Save, I for Forget and O for Back, and the top two
 * rows sense them, so J, K and L work too. The word itself sits on the left, over the columns that pick nothing, and nothing is
 * drawn over P's column.
 */
internal object MenuKeys {
    /** The rows of letters that pick the menu's buttons: the top two. */
    const val ROWS = 2
    /** The columns of the buttons: U, I and O on the top row. */
    const val SAVE = 6
    const val FORGET = 7
    const val BACK = 8
    /** The columns the word takes, left of the buttons, and the one past them, P's, that has nothing over it. */
    const val WORD_COLUMNS = SAVE
    const val AFTER_COLUMNS = 1

    /** Which button [key] picks: [SAVE], [FORGET] or [BACK], or -1 for a key that picks none. */
    fun button(key: String): Int {
        if (!Layout.isLetter(key)) return -1
        for (row in 0 until ROWS) {
            val column = Layout.rows[row].indexOf(key)
            if (column in SAVE..BACK) return column
        }
        return -1
    }
}
