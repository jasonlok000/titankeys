package com.atlas.keyboard

/**
 * The word menu on the strip, picked with the keys, as the Sym panel's items are: rest a finger on a key to mark a button, and
 * press the key to pick it. Its three buttons sit over the last three key columns, I for Save, O for Forget and P for Back, and the
 * top two rows sense them, so K and L work too. The word itself sits right-aligned over the columns that pick nothing, just left of
 * Save.
 *
 * The columns are the Sym panel's: ten of them, [Layout.KEY_SIDE_DP] in from each side of the screen, so that a button sits centred
 * over its own key. [slot] and [wordRight] work them out in pixels, so they can be checked without a phone.
 */
object MenuKeys {
    /** The rows of letters that pick the menu's buttons: the top two. */
    const val ROWS = 2
    /** The columns of the buttons: I, O and P on the top row. */
    const val SAVE = 7
    const val FORGET = 8
    const val BACK = 9
    /** The columns the word takes, left of the buttons, and the ones past them that have nothing over them: none. */
    const val WORD_COLUMNS = SAVE
    const val AFTER_COLUMNS = 0
    /** The ten key columns. */
    const val COLUMNS = 10

    /** Which button [key] picks: [SAVE], [FORGET] or [BACK], or -1 for a key that picks none. */
    fun button(key: String): Int {
        if (!Layout.isLetter(key)) return -1
        for (row in 0 until ROWS) {
            val column = Layout.rows[row].indexOf(key)
            if (column in SAVE..BACK) return column
        }
        return -1
    }

    /** The left and right of key [column], from 0, on a bar [width] pixels wide whose columns start [side] pixels in from each edge. */
    fun slot(width: Float, side: Float, column: Int): Pair<Float, Float> {
        val each = (width - 2 * side) / COLUMNS
        return side + column * each to side + (column + 1) * each
    }

    /** Where the word ends on the right, [gap] pixels left of Save's column. */
    fun wordRight(width: Float, side: Float, gap: Float) = slot(width, side, SAVE).first - gap

    /**
     * Where a word stands, which sets what I and O do (owner, 2026-11-12). Save adds it to the dictionary, and Favourite puts it
     * first among similar words: see [Favourites].
     */
    enum class Step { New, Saved, Favourite }

    /** What I and O can do. [Favourited] is I on a favourite: it shows that the word is one, and does nothing more. */
    enum class Button { Save, Favourite, Favourited, Unfavourite, Forget }

    /** A favourite, then a word that is saved or that the dictionary already knows, then any other word. */
    fun step(known: Boolean, favourite: Boolean) = when {
        favourite -> Step.Favourite
        known -> Step.Saved
        else -> Step.New
    }

    /** I's button: Save, then Favourite, then Favourite with its tick. */
    fun first(step: Step) = when (step) {
        Step.New -> Button.Save
        Step.Saved -> Button.Favourite
        Step.Favourite -> Button.Favourited
    }

    /** O's button: Unfavourite on a favourite, and otherwise Forget, or nothing for a word that no list can drop ([forget]). */
    fun second(step: Step, forget: Boolean) = if (step == Step.Favourite) Button.Unfavourite else Button.Forget.takeIf { forget }

    /** Where the word stands once [button] is picked at [step]. The menu stays open on it, except after Forget: null. */
    fun after(step: Step, button: Button): Step? = when (button) {
        Button.Save, Button.Unfavourite -> Step.Saved
        Button.Favourite -> Step.Favourite
        Button.Favourited -> step
        Button.Forget -> null
    }
}
