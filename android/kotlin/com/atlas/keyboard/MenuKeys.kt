package com.atlas.keyboard

/**
 * The word menu on the strip, picked with the keys, as the Sym panel's items are: rest a finger on a key to mark a button, and
 * press the key to pick it. Its three buttons sit over the last three key columns, I for a step up, O for a step down and P for
 * Back, and the top two rows sense them, so K and L work too, and Backspace works as P. The word itself sits over the columns
 * that pick nothing, just left of I's button.
 *
 * The columns are the Sym panel's: ten of them over the real keys ([KeyGeometry]), so that a button sits centred over its own key.
 * [slot] and [wordRight] work them out in pixels, so they can be checked without a phone.
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

    /**
     * The left and right of key [column], from 0, on a bar [width] pixels wide, as wide as the screen: the column over the real key,
     * from the middle of the gap on its left to the middle of the gap on its right, as [KeyGeometry] and Key calibration have it.
     * [side] no longer moves the columns, which follow the real keys; it stays so that the menu's callers don't change.
     */
    @Suppress("UNUSED_PARAMETER")
    fun slot(width: Float, side: Float, column: Int): Pair<Float, Float> = KeyAlignment.column(width, column)

    /** Where the word ends on the right, [gap] pixels left of Save's column. */
    fun wordRight(width: Float, side: Float, gap: Float) = slot(width, side, SAVE).first - gap

    /**
     * The button that a finger resting on [key] hovers: the button's own key, or the one below it, so K and L hover I's and O's,
     * and Backspace hovers P's, since the touchpad barely senses P itself (owner, 2026-11-12). [BACK] for Backspace, else as
     * [button].
     */
    fun hovered(key: String) = if (key == Layout.BACKSPACE) BACK else button(key)

    /**
     * Where a word stands, which sets what I and O do (owner, 2026-11-12). It is a ladder: I always moves the word one step up and
     * O one step down, never two. Save adds it to Saved words, and Favourite puts it first among similar words: see [Favourites].
     * A word the dictionary knows is [BuiltIn]: it can't be saved or unsaved, so its first step up is Favourite.
     */
    enum class Step { New, Saved, BuiltIn, Favourite }

    /**
     * What I and O can do. [Favourited] is I on a favourite: the top of the ladder, a spent heart that does nothing. [Unsave] takes
     * a saved word back out of Saved words.
     */
    enum class Button { Save, Favourite, Favourited, Unfavourite, Unsave, Forget }

    /**
     * A favourite, then a word the dictionary has, then a saved word, then any other word. A [blocked] word, one you forgot, is
     * never suggested, so it is back at the bottom whatever the lists say: I saves it, which lets it back in.
     */
    fun step(saved: Boolean, builtIn: Boolean, favourite: Boolean, blocked: Boolean = false) = when {
        blocked -> Step.New
        favourite -> Step.Favourite
        builtIn -> Step.BuiltIn
        saved -> Step.Saved
        else -> Step.New
    }

    /** I's button, one step up: Save, then Favourite, then the spent heart. */
    fun first(step: Step) = when (step) {
        Step.New -> Button.Save
        Step.Saved, Step.BuiltIn -> Button.Favourite
        Step.Favourite -> Button.Favourited
    }

    /**
     * O's button, one step down: Unfavourite on a favourite, Unsave on a saved word, and Forget at the bottom, or nothing there for
     * a word that no list can drop ([forget]).
     */
    fun second(step: Step, forget: Boolean) = when (step) {
        Step.Favourite -> Button.Unfavourite
        Step.Saved -> Button.Unsave
        Step.New, Step.BuiltIn -> Button.Forget.takeIf { forget }
    }

    /**
     * Where the word stands once [button] is picked at [step], one step from where it was. A favourite goes back to where it came
     * from: [builtIn] for a word the dictionary has, or saved. The menu stays open on it, except after Forget: null.
     */
    fun after(step: Step, button: Button, builtIn: Boolean): Step? = when (button) {
        Button.Save -> Step.Saved
        Button.Favourite -> Step.Favourite
        Button.Favourited -> step
        Button.Unfavourite -> if (builtIn) Step.BuiltIn else Step.Saved
        Button.Unsave -> Step.New
        Button.Forget -> null
    }

    /** How far [button] moves a word: up 1 or 2, down 1, 2 or 3 (Forget, the heaviest), or 0 for the spent heart. See [Haptics]. */
    fun move(button: Button) = when (button) {
        Button.Save -> 1
        Button.Favourite -> 2
        Button.Favourited -> 0
        Button.Unfavourite -> -1
        Button.Unsave -> -2
        Button.Forget -> -3
    }

    /** The small mark by the word that shows its step: a heart on a favourite, a bookmark on a saved word, and none otherwise. */
    enum class Mark { Heart, Bookmark }

    fun mark(step: Step) = when (step) {
        Step.Favourite -> Mark.Heart
        Step.Saved -> Mark.Bookmark
        Step.New, Step.BuiltIn -> null
    }

    // The first-time hint: the first few times the menu opens, each button's pill shows once, slowly, I's, then O's, then P's, and
    // then the menu settles (owner, 2026-11-12). After that the pills show only while a finger hovers a button.

    /** How many times the menu opens with its hint. */
    const val HINT_OPENS = 3
    /** How long each pill stays, and the rest after it before the next, in ms. The pill grows and shrinks as a hover's does. */
    const val HINT_SHOW_MILLIS = 1400L
    const val HINT_REST_MILLIS = 350L
    private const val HINT_EACH_MILLIS = HINT_SHOW_MILLIS + HINT_REST_MILLIS

    /** Whether the menu opens with its hint, when it has opened with it [shown] times before. */
    fun hints(shown: Int) = shown < HINT_OPENS

    /** How long the whole hint takes for [slots] buttons, in ms. */
    fun hintMillis(slots: Int) = slots * HINT_EACH_MILLIS

    /** The column whose pill the hint shows at [elapsed] ms, over the buttons' [slots] in order, or -1 between pills and after. */
    fun hint(elapsed: Long, slots: List<Int>): Int {
        if (elapsed < 0) return -1
        val index = (elapsed / HINT_EACH_MILLIS).toInt()
        if (index >= slots.size) return -1
        return if (elapsed - index * HINT_EACH_MILLIS < HINT_SHOW_MILLIS) slots[index] else -1
    }

    /**
     * The hovered button's pill: it grows left from its key's column to hold the action's name, and covers the buttons to its
     * left. The word slides to stay centred in the room left of it. In pixels on a bar [width] wide with [side] at each edge:
     * [label] is the name's width and [icon] the icon's; [padLeft] and [padRight] are the room at the pill's ends, and [gap] is
     * between the name and the icon. Returns its left and right.
     */
    fun pill(width: Float, side: Float, column: Int, label: Float, icon: Float, padLeft: Float, gap: Float, padRight: Float): Pair<Float, Float> {
        val (left, right) = slot(width, side, column)
        val end = (left + right) / 2 + icon / 2 + padRight
        return end - padRight - icon - gap - label - padLeft to end
    }

    /** Where the word is centred: in the room from [left] to [right], the space left of the buttons or of a hovered pill. */
    fun wordCentre(left: Float, right: Float) = (left + right) / 2

    /**
     * [word], shortened in the middle with "…" until [fits] says it fits, keeping its start and end, which tell a word apart best.
     * At least a letter stays at each side.
     */
    fun shorten(word: String, fits: (String) -> Boolean): String {
        if (fits(word)) return word
        var keep = word.length - 1
        while (keep > 2) {
            val head = (keep + 1) / 2
            val shown = word.take(head) + "…" + word.takeLast(keep - head)
            if (fits(shown)) return shown
            keep--
        }
        return word.take(1) + "…" + word.takeLast(1)
    }
}
