package com.atlas.keyboard

/**
 * The word menu on the strip, picked with the keys, as the Sym panel's items are: rest a finger on a key to mark a button, and
 * press the key to pick it. The top two rows of letters sense it, split into as many parts as the menu has buttons, left to right,
 * so that each button sits over the keys that pick it.
 */
internal object MenuKeys {
    /** The rows of letters that pick the menu's buttons: the top two. */
    const val ROWS = 2

    /** The button, from the left, that [key] picks out of [count] buttons, or -1 for a key that picks none. */
    fun button(key: String, count: Int): Int {
        if (count <= 0 || !Layout.isLetter(key)) return -1
        for (row in 0 until ROWS) {
            val column = Layout.rows[row].indexOf(key)
            if (column >= 0) return column * count / COLUMNS
        }
        return -1
    }

    private const val COLUMNS = 10
}
