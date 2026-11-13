package com.atlas.keyboard

/**
 * The word menu on the strip, picked with the keys, as the Sym panel's items are: rest a finger on a key to mark a button, and
 * press the key to pick it. Its three buttons sit over the last three key columns, I for a step up, O for a step down and P for
 * Back, and the top two rows sense them, so K and L work too, and Backspace works as P. The word itself sits over the columns
 * that pick nothing, just left of I's button.
 *
 * The columns are the real keys' ([KeyGeometry], as calibrated: [KeyAlignment]), so that a button sits centred over its own key
 * once the keys are calibrated. [slot] is the only place the menu reads them from, so it can be checked without a phone.
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
     * from the middle of the gap on its left to the middle of the gap on its right, as [KeyGeometry] has it for [alignment], the
     * keys as calibrated now.
     */
    fun slot(width: Float, column: Int, alignment: KeyAlignment = KeyAlignment.current): Pair<Float, Float> =
        KeyGeometry(width, alignment).cell(column)

    /** Where the word ends on the right, [gap] pixels left of Save's column. */
    fun wordRight(width: Float, gap: Float, alignment: KeyAlignment = KeyAlignment.current) = slot(width, SAVE, alignment).first - gap

    /**
     * The button that a finger resting on [key] hovers: the button's own key, or the one below it, so K and L hover I's and O's,
     * and Backspace hovers P's, since the touchpad barely senses P itself (owner, 2026-11-12). [BACK] for Backspace, else as
     * [button].
     */
    fun hovered(key: String) = if (key == Layout.BACKSPACE) BACK else button(key)


    /**
     * Where a word stands: one simple ladder for every word (owner, 2026-11-12), dictionary words and single letters too.
     * NOT SAVED → SAVED ("remembered") → FAVOURITE. I moves the word one step up and O one step down, never two.
     */
    enum class Step { New, Saved, Favourite }

    /**
     * What I and O can do. [Favourited] is I on a favourite: the top of the ladder, a spent heart that says no. [Unsave] takes your
     * save away, and nothing else: a dictionary word stays in the dictionary and is never blocked. [Forget] removes what the
     * keyboard learned from your typing, such as a typo it picked up.
     */
    enum class Button { Save, Favourite, Favourited, Unfavourite, Unsave, Forget }

    /**
     * Where a word stands for the menu: its [step]; whether the keyboard [learned] it from your typing, which O can forget at the
     * bottom; whether the dictionary has it ([builtIn]), so it counts as saved and O takes it out of the dictionary; whether the
     * word store can hold it at all ([savable]: symbols and emoji can't be saved); and whether it is a single letter, which is
     * never forgotten.
     */
    data class State(val step: Step, val learned: Boolean, val builtIn: Boolean = false, val savable: Boolean = true,
                     val oneLetter: Boolean = false)

    /** What I and O show at a [State], and where each press takes the word: null for a button with nothing, or after Forget. */
    data class Choices(val i: Button?, val iNext: State?, val o: Button?, val oNext: State?)

    /**
     * Where a word stands as the menu opens (owner, 2026-11-12): a favourite; then saved, a word you saved or one the dictionary
     * has ("saving is adding to the dictionary, so a dictionary word is saved"); then not saved. A [blocked] word, one you forgot,
     * is not saved and no favourite, whatever the lists still say. Only a learned word that isn't in the dictionary can be
     * forgotten at the bottom, and never a single letter.
     */
    fun state(saved: Boolean, favourite: Boolean, blocked: Boolean, learned: Boolean, oneLetter: Boolean,
              builtIn: Boolean = false, savable: Boolean = true) = State(
        when {
            blocked -> Step.New
            favourite -> Step.Favourite
            saved || builtIn -> Step.Saved
            else -> Step.New
        },
        learned && !oneLetter && !blocked && !builtIn, builtIn, savable, oneLetter)

    /**
     * Whether the word store can hold [word]: 2 to 24 letters, with apostrophes. Anything else, symbols, emoji, numbers, can't be
     * saved, so its menu has no Save (owner, 2026-11-12: Save failed silently for them).
     */
    fun savable(word: String): Boolean {
        val letters = word.count(Char::isLetter)
        return letters in 2..24 && word.all { it.isLetter() || it == '\'' || it == '’' }
    }

    /**
     * The ladder itself, the only place it lives: the keyboard, the guide and the checks all use it.
     * - NOT SAVED: I is Save, to SAVED, for a word the store can hold. O is Forget, only for a word the keyboard learned, and
     *   closes the menu.
     * - SAVED: I is Favourite, to FAVOURITE. O is Unsave, to NOT SAVED, with nothing learned left to forget; for a dictionary
     *   word O is Forget, which takes it out of the dictionary with an Undo and closes the menu, except for a single letter.
     * - FAVOURITE: I is spent and stays. O is Unfavourite, to SAVED, always.
     */
    fun choices(state: State): Choices = when (state.step) {
        Step.New -> Choices(Button.Save.takeIf { state.savable }, state.copy(step = Step.Saved),
            Button.Forget.takeIf { state.learned }, null)
        Step.Saved -> if (state.builtIn) Choices(Button.Favourite, state.copy(step = Step.Favourite),
            Button.Forget.takeUnless { state.oneLetter }, null)
        else Choices(Button.Favourite, state.copy(step = Step.Favourite), Button.Unsave, state.copy(step = Step.New, learned = false))
        Step.Favourite -> Choices(Button.Favourited, state, Button.Unfavourite, state.copy(step = Step.Saved))
    }

    /** I's button at [step], for an ordinary word. */
    fun first(step: Step) = choices(State(step, false)).i

    /** O's button at [step], for an ordinary word that the keyboard [learned] or not. */
    fun second(step: Step, learned: Boolean) = choices(State(step, learned)).o

    /** The state after [button] at [state], or null when the menu closes, after Forget. */
    fun press(state: State, button: Button): State? {
        val choices = choices(state)
        return when (button) {
            choices.i -> choices.iNext
            choices.o -> choices.oNext
            else -> state
        }
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
    fun pills(column: Int, state: State) = when (column) {
        SAVE -> choices(state).i != null
        FORGET -> choices(state).o != null
        else -> false
    }

    /** The animations of the word menu: see [MenuMotion]. Each change of step plays one, and so do opening and Forget. */
    enum class Motion { Save, Favourite, Unfavourite, Unsave, Forget, Echo }

    /**
     * What plays as the word goes from [from] to [to], or as the menu opens on it when [from] is null: a soft echo for a saved word
     * or a favourite, and nothing for one that isn't saved.
     */
    fun motion(from: Step?, to: Step): Motion? = when {
        from == null -> if (to == Step.New) null else Motion.Echo
        from == to -> null
        to == Step.Favourite -> Motion.Favourite
        from == Step.Favourite -> Motion.Unfavourite
        to == Step.Saved -> Motion.Save
        else -> Motion.Unsave
    }

    /**
     * The hovered button's pill: it grows left from its key's column to hold the action's name, and covers the buttons to its
     * left, and the end of a long word, which fades under its leading edge. In pixels on a bar [width] wide, over the real keys:
     * [label] is the name's width and [icon] the icon's; [padLeft] and [padRight] are the room at the pill's ends, and [gap] is
     * between the name and the icon. Returns its left and right.
     */
    fun pill(width: Float, column: Int, label: Float, icon: Float, padLeft: Float, gap: Float, padRight: Float,
             alignment: KeyAlignment = KeyAlignment.current): Pair<Float, Float> {
        val (left, right) = slot(width, column, alignment)
        val end = (left + right) / 2 + icon / 2 + padRight
        return end - padRight - icon - gap - label - padLeft to end
    }

    /**
     * Where the pill's name starts, for a pill whose right end is at [right]: [gap] left of its icon, wherever the pill's left
     * edge is. As the pill grows, its name is uncovered from the icon outward and never slides into it (owner's "Fav♡urite").
     */
    fun pillLabel(right: Float, label: Float, icon: Float, gap: Float, padRight: Float) = right - padRight - icon - gap - label

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
