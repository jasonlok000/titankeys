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
     * Where a word stands, which sets what I and O do (owner, 2026-11-12). It is a ladder: I moves the word one step up and O one
     * step down, never two.
     * - [New]: not saved. It may be a word the keyboard learned from your typing, which isn't saved: only Save saves a word.
     * - [Saved]: you pressed Save on it.
     * - [BuiltIn]: the dictionary has it, so Save means nothing for it, and its first step up is Favourite.
     * - [Favourite]: first among similar words, see [Favourites].
     */
    enum class Step { New, Saved, BuiltIn, Favourite }

    /**
     * What I and O can do. [Favourited] is I on a favourite: the top of the ladder, a spent heart that does nothing. [Unsave] takes
     * a saved word back out, and [Forget] removes what the keyboard learned from your typing, such as a typo it picked up.
     */
    enum class Button { Save, Favourite, Favourited, Unfavourite, Unsave, Forget }

    /**
     * Where a word stands without its favourite: a dictionary word, then a saved one, then any other. A [blocked] word, one you
     * forgot or blocked in Settings, is never suggested, so it is not saved, whatever the lists still say: Save lets it back in. A
     * dictionary word stays one, blocked or not.
     */
    fun base(saved: Boolean, builtIn: Boolean, blocked: Boolean = false) = when {
        builtIn -> Step.BuiltIn
        blocked -> Step.New
        saved -> Step.Saved
        else -> Step.New
    }

    /** Where a word stands: a favourite, or its [base]. A blocked word lost its favourite when it was forgotten. */
    fun step(saved: Boolean, builtIn: Boolean, favourite: Boolean, blocked: Boolean = false) =
        if (favourite && !(blocked && !builtIn)) Step.Favourite else base(saved, builtIn, blocked)

    /** I's button, one step up: Save, then Favourite, then the spent heart. */
    fun first(step: Step) = when (step) {
        Step.New -> Button.Save
        Step.Saved, Step.BuiltIn -> Button.Favourite
        Step.Favourite -> Button.Favourited
    }

    /**
     * O's button, one step down: Unfavourite on a favourite, Unsave on a saved word, and at the bottom Forget, only for a word the
     * keyboard [learned] from your typing. A word it never learned, and a dictionary word, have nothing on O.
     */
    fun second(step: Step, learned: Boolean) = when (step) {
        Step.Favourite -> Button.Unfavourite
        Step.Saved -> Button.Unsave
        Step.New -> Button.Forget.takeIf { learned }
        Step.BuiltIn -> null
    }

    /**
     * Where the word stands once [button] is picked at [step], one step from where it was. Unfavourite goes back to the word's
     * [base], where it was before it was a favourite; Unsave to not saved, and nothing learned is left to forget. The menu stays
     * open on it, except after Forget: null, and the bar says "Forgot" with its Undo.
     */
    fun after(step: Step, button: Button, base: Step): Step? = when (button) {
        Button.Save -> Step.Saved
        Button.Favourite -> Step.Favourite
        Button.Favourited -> step
        Button.Unfavourite -> base
        Button.Unsave -> Step.New
        Button.Forget -> null
    }

    /**
     * What the menu shows for a word: its [step], where Unfavourite takes it back to ([base]), and whether O can [forget] it at
     * the bottom.
     */
    data class State(val step: Step, val base: Step, val forget: Boolean)

    /**
     * The menu for a word as it opens (owner, 2026-11-12): [saved] only if you pressed Save on it; [learned] if the keyboard
     * picked it up from your typing; [builtIn] if the dictionary has it; [blocked] if you forgot it or blocked it. Only a learned
     * word can be forgotten, and never a dictionary word or a single letter.
     */
    fun state(saved: Boolean, builtIn: Boolean, favourite: Boolean, blocked: Boolean, learned: Boolean, oneLetter: Boolean) =
        State(step(saved, builtIn, favourite, blocked), base(saved, builtIn, blocked), learned && !builtIn && !oneLetter)

    /**
     * The menu after [button] is pressed at [state], or null when it closes, after Forget. Save leaves nothing to forget, as the
     * word is saved, and Unsave takes what was learned of it too.
     */
    fun press(state: State, button: Button): State? = when (button) {
        Button.Save -> State(Step.Saved, Step.Saved, false)
        Button.Unsave -> State(Step.New, Step.New, false)
        Button.Forget -> null
        else -> after(state.step, button, state.base)?.let { state.copy(step = it) }
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

    /**
     * Whether a finger hovering [column] shows a pill: I's and O's buttons do, where they have one. Back has none, no name and
     * no highlight: just its arrow, which works on a press (owner, 2026-11-12).
     */
    fun pills(column: Int, step: Step, learned: Boolean) = when (column) {
        SAVE -> true
        FORGET -> second(step, learned) != null
        else -> false
    }

    /**
     * The little animations that take the badge's place (owner, 2026-11-12): each change of step plays one over the word, and the
     * buttons show the step afterwards. Nothing stays on the bar. See [MenuMotion].
     */
    enum class Motion { BookmarkDrop, HeartBurst, HeartCrack, BookmarkTear, BookmarkFlick, HeartDrift }

    /**
     * What plays as the word goes from [from] to [to], or as the menu opens on it when [from] is null: a quick, subtle replay for a
     * saved word or a favourite, and nothing for the others.
     */
    fun motion(from: Step?, to: Step): Motion? = when {
        from == null -> when (to) {
            Step.Saved -> Motion.BookmarkFlick
            Step.Favourite -> Motion.HeartDrift
            else -> null
        }
        from == to -> null
        to == Step.Favourite -> Motion.HeartBurst
        from == Step.Favourite -> Motion.HeartCrack
        to == Step.Saved -> Motion.BookmarkDrop
        from == Step.Saved -> Motion.BookmarkTear
        else -> null
    }

    /**
     * The hovered button's pill: it grows left from its key's column to hold the action's name, and covers the buttons to its
     * left, and the end of a long word, which fades under its leading edge. In pixels on a bar [width] wide with [side] at each
     * edge: [label] is the name's width and [icon] the icon's; [padLeft] and [padRight] are the room at the pill's ends, and [gap]
     * is between the name and the icon. Returns its left and right.
     */
    fun pill(width: Float, side: Float, column: Int, label: Float, icon: Float, padLeft: Float, gap: Float, padRight: Float): Pair<Float, Float> {
        val (left, right) = slot(width, side, column)
        val end = (left + right) / 2 + icon / 2 + padRight
        return end - padRight - icon - gap - label - padLeft to end
    }

    /**
     * Where the word is centred: in the room from [left] to [right], left of the buttons. It is placed once, as the menu opens,
     * and never moves for a pill or an animation (owner, 2026-11-12).
     */
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
