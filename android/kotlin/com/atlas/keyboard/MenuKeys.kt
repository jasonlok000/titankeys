package com.atlas.keyboard

/**
 * The word menu on the strip, picked with the keys, as the Sym panel's items are: rest a finger on a key to mark a button, and
 * press the key to pick it. Its three buttons sit over the last three key columns, I for Save, O for Forget and P for Back, and the
 * top two rows sense them, so K and L work too. The word itself sits right-aligned over the columns that pick nothing, just left of
 * Save.
 */
internal object MenuKeys {
    /** The rows of letters that pick the menu's buttons: the top two. */
    const val ROWS = 2
    /** The columns of the buttons: I, O and P on the top row. */
    const val SAVE = 7
    const val FORGET = 8
    const val BACK = 9
    /** The columns the word takes, left of the buttons, and the ones past them that have nothing over them: none. */
    const val WORD_COLUMNS = SAVE
    const val AFTER_COLUMNS = 0

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
