package com.atlas.keyboard

/**
 * The word menu on the strip, picked with the keys, as the Sym panel's items are: rest a finger on a key to mark a button, and
 * press the key to pick it. The top two rows of letters sense it, split into as many parts as the menu has buttons, left to right,
 * and the strip draws each button over the key columns that pick it. The last column, P's, picks nothing and has nothing over it.
 */
internal object MenuKeys {
    /** The rows of letters that pick the menu's buttons: the top two. */
    const val ROWS = 2

    /** The button, from the left, that [key] picks out of [count] buttons, or -1 for a key that picks none. */
    fun button(key: String, count: Int): Int {
        if (count <= 0 || !Layout.isLetter(key)) return -1
        for (row in 0 until ROWS) {
            val column = Layout.rows[row].indexOf(key)
            if (column >= 0) return if (column < COLUMNS) column * count / COLUMNS else -1
        }
        return -1
    }

    /** How many key columns pick [button] out of [count], so that the strip can make it as wide as they are. */
    fun columns(button: Int, count: Int) = if (count <= 0) 0 else (0 until COLUMNS).count { it * count / COLUMNS == button }

    /** The key columns that pick a button: all but the last, P's. */
    const val COLUMNS = 9
}
