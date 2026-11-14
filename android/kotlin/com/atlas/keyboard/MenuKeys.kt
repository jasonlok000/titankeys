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
    /** The columns of the buttons: I for a step up, O for a step down, and P for Back, on the top row. */
    const val UP = 7
    const val DOWN = 8
    const val BACK = 9
    /** The columns the word takes, left of the buttons, and the ones past them that have nothing over them: none. */
    const val WORD_COLUMNS = UP
    const val AFTER_COLUMNS = 0
    /** The ten key columns. */
    const val COLUMNS = 10

    /** Which button [key] picks: [UP], [DOWN] or [BACK], or -1 for a key that picks none. */
    fun button(key: String): Int {
        if (!Layout.isLetter(key)) return -1
        for (row in 0 until ROWS) {
            val column = Layout.rows[row].indexOf(key)
            if (column in UP..BACK) return column
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

    /** Back's arrow: the corners of panel_back's outline, in its 24 by 24 units, which must keep clear of the screen's corner. */
    val BACK_ARROW = listOf(20f to 11f, 7.8f to 11f, 13.4f to 5.4f, 12f to 4f, 4f to 12f, 12f to 20f, 13.4f to 18.6f, 7.8f to 13f, 20f to 13f)

    /**
     * How far an icon [size] px across, its middle at [centre] and its top at [top], rises, in whole px and no more than its own
     * size, so that every corner of its [outline] (in its 24 by 24 units) is [clear], given at x and y in px. Back's arrow rises
     * over Backspace's centre this way, where the bar's line would take it into the curve of the screen's corner at Bigger.
     */
    fun rise(outline: List<Pair<Float, Float>>, size: Float, centre: Float, top: Float, clear: (Float, Float) -> Boolean): Float {
        var rise = 0f
        while (rise < size && outline.any { (x, y) -> !clear(centre + (x / 24f - .5f) * size, top - rise + y / 24f * size) }) rise++
        return rise
    }

    /** Where the word ends on the right, [gap] pixels left of I's column. */
    fun wordRight(width: Float, gap: Float, alignment: KeyAlignment = KeyAlignment.current) = slot(width, UP, alignment).first - gap

    /**
     * The button that a finger resting on [key] hovers: the button's own key, or the one below it, so K and L hover I's and O's,
     * and Backspace hovers P's, since the touchpad barely senses P itself (owner, 2026-11-12). [BACK] for Backspace, else as
     * [button].
     */
    fun hovered(key: String) = if (key == Layout.BACKSPACE) BACK else button(key)

    /**
     * Where a word stands: one ladder of four rungs for every word (owner, 2026-11-13). I moves it one rung up and O one rung down,
     * never two, and the menu always stays open.
     * - [Blocked]: on Blocked words, so it's never suggested, learned or fixed into.
     * - [New], not saved: TitanKeys doesn't know it. A word typed once, a learned word that faded, a dictionary word you took out,
     *   and anything never seen.
     * - [Saved]: TitanKeys knows it. A dictionary word, a word it learned from your typing (typed twice, and still in use), and a
     *   word you saved.
     * - [Favourite]: first among similar words.
     */
    enum class Step { Blocked, New, Saved, Favourite }

    /**
     * What I and O can do. [Favourited] is I on a favourite: the top of the ladder, a spent heart that says no. [Unsave] takes a
     * word out of what TitanKeys knows without blocking it; [Block] puts it on Blocked words, and [Unblock] takes it off.
     */
    enum class Button { Save, Unsave, Favourite, Favourited, Unfavourite, Block, Unblock }

    /** What I and O show at a [Step], and where each press takes the word: null for a button with nothing. */
    data class Choices(val i: Button?, val iNext: Step?, val o: Button?, val oNext: Step?)

    /**
     * Where a word stands as the menu opens, from what the word lists say of it: [blocked] wins, then a [favourite]; then saved, a
     * word you [saved], one TitanKeys [learned] from your typing and still uses, or one the dictionary has ([builtIn]) unless you
     * took it out of the dictionary ([unsaved]); and not saved for anything else.
     */
    fun step(blocked: Boolean, favourite: Boolean, saved: Boolean, learned: Boolean, builtIn: Boolean, unsaved: Boolean): Step = when {
        blocked -> Step.Blocked
        favourite -> Step.Favourite
        saved || learned || builtIn && !unsaved -> Step.Saved
        else -> Step.New
    }

    /**
     * Whether [word] has a ladder, so its menu opens: only a word the word lists can hold, as SwipeWords.normalise has them, 2 to
     * 24 letters a to z with apostrophes between them, in any case. One-letter words, symbols, emoji and numbers open no menu at
     * all (owner, 2026-11-13), and neither do accented words ("café", "José") or a word that starts or ends with an apostrophe
     * ("dogs'", "'til"): the lists can't save, block or find them, so their menu showed the wrong rung and its buttons failed (bug
     * hunt #12).
     */
    fun opens(word: String): Boolean {
        val value = word.lowercase(java.util.Locale.ROOT).replace('’', '\'')
        return value.replace("'", "").length in 2..24 && value.matches(STORED)
    }

    /** The words the word lists hold: SwipeWords.normalise's rule, which tests/word-menu keeps the same. */
    val STORED = Regex("[a-z]+('[a-z]+)*")

    /**
     * The ladder itself, the only place it lives: the keyboard, the guide and the checks all use it.
     * - FAVOURITE: I is the spent heart, which stays. O is Unfavourite, to SAVED.
     * - SAVED: I is Favourite, to FAVOURITE. O is Unsave, to NOT SAVED, for every kind of word.
     * - NOT SAVED: I is Save, to SAVED. O is Block, to BLOCKED.
     * - BLOCKED: I is Unblock, to NOT SAVED. O has nothing.
     */
    fun choices(step: Step): Choices = when (step) {
        Step.Favourite -> Choices(Button.Favourited, Step.Favourite, Button.Unfavourite, Step.Saved)
        Step.Saved -> Choices(Button.Favourite, Step.Favourite, Button.Unsave, Step.New)
        Step.New -> Choices(Button.Save, Step.Saved, Button.Block, Step.Blocked)
        Step.Blocked -> Choices(Button.Unblock, Step.New, null, null)
    }

    /** The rung after [button] at [step]. A button that [step] doesn't show changes nothing. */
    fun press(step: Step, button: Button): Step {
        val choices = choices(step)
        return when (button) {
            choices.i -> choices.iNext ?: step
            choices.o -> choices.oNext ?: step
            else -> step
        }
    }

    /**
     * How high on the ladder [button]'s step is, up or down: up to not saved 1, to saved 2 and to favourite 3; down from favourite
     * 1, from saved 2 and to blocked 3, the heaviest; 0 for the spent heart. Higher steps up end stronger, and lower steps down
     * weigh more: see [Haptics].
     */
    fun move(button: Button) = when (button) {
        Button.Unblock -> 1
        Button.Save -> 2
        Button.Favourite -> 3
        Button.Favourited -> 0
        Button.Unfavourite -> -1
        Button.Unsave -> -2
        Button.Block -> -3
    }

    /**
     * Whether a finger hovering [column] shows a pill: I's and O's buttons do, where they have one. Back has none, no name and
     * no highlight: just its arrow, which works on a press (owner, 2026-11-12).
     */
    fun pills(column: Int, step: Step) = when (column) {
        UP -> choices(step).i != null
        DOWN -> choices(step).o != null
        else -> false
    }

    /**
     * The hovered button's pill: it grows left from its key's column to hold the action's name, and covers the buttons to its
     * left, but never the word, which ends before the widest pill: see [wordEnd]. In pixels on a bar [width] wide, over the real keys:
     * [label] is the name's width and [icon] the icon's; [padLeft] and [padRight] are the room at the pill's ends, and [gap] is
     * between the name and the icon. Returns its left and right.
     */
    fun pill(width: Float, column: Int, label: Float, icon: Float, padLeft: Float, gap: Float, padRight: Float,
             alignment: KeyAlignment = KeyAlignment.current): Pair<Float, Float> {
        val (left, right) = slot(width, column, alignment)
        val end = (left + right) / 2 + icon / 2 + padRight
        return end - padRight - icon - gap - label - padLeft to end
    }

    /** Whether a pill from [left] to [right] covers the button over [column], as it does every button to its left. */
    fun covers(left: Float, right: Float, column: Int, width: Float, alignment: KeyAlignment = KeyAlignment.current): Boolean {
        val (a, b) = slot(width, column, alignment)
        return (a + b) / 2 in left..right
    }

    /**
     * Where the pill's name starts, for a pill whose right end is at [right]: [gap] left of its icon, wherever the pill's left
     * edge is. As the pill grows, its name is uncovered from the icon outward and never slides into it (owner's "Fav♡urite").
     */
    fun pillLabel(right: Float, label: Float, icon: Float, gap: Float, padRight: Float) = right - padRight - icon - gap - label

    /** Every button, with the column it shows in: I's buttons on some rung, and O's. */
    val placed: List<Pair<Int, Button>>
        get() = Step.entries.flatMap { step -> choices(step).let { listOfNotNull(it.i?.let { b -> UP to b }, it.o?.let { b -> DOWN to b }) } }
            .distinct()

    /**
     * How far right the word may reach: [gap] left of the leftmost edge that any pill can have, under I or O on any rung, and never
     * past [wordRight]. [label] is a button's name's width at the Bar text size now; the rest is as [pill] has it. So no pill ever
     * covers the word, or the guide's underline under it, whatever its step or its name (owner, 2026-11-13: the guide's TitanKeys
     * was cut to "TitanKe" under "Already a favourite").
     */
    fun wordEnd(width: Float, gap: Float, label: (Button) -> Float, icon: Float, padLeft: Float, pillGap: Float, padRight: Float,
                alignment: KeyAlignment = KeyAlignment.current): Float =
        minOf(wordRight(width, gap, alignment),
            placed.minOf { (column, button) -> pill(width, column, label(button), icon, padLeft, pillGap, padRight, alignment).first } - gap)

    /**
     * Where the word is centred: in the room from [left] to [right], left of the buttons, unless a word [textWidth] wide would then
     * reach past [end], where the widest pill could begin ([wordEnd]); then just far enough left to clear it, and never left of
     * [left]. It is placed once, as the menu opens, and never moves for a pill or an animation (owner, 2026-11-12).
     */
    fun wordCentre(left: Float, right: Float, end: Float, textWidth: Float) =
        minOf((left + right) / 2, end - textWidth / 2).coerceAtLeast(left + textWidth / 2)

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
