package com.atlas.keyboard

/**
 * What the interactive guide asks of the keyboard, only in the guide's own field. The guide runs in the keyboard's process, so this
 * is a plain shared object. No other field is ever touched: the guide's field marks itself with [FIELD] in its private options.
 */
/** What the guide's slides teach, one by one. A slide that locks the keyboard lets only these through, besides letters. */
/** What a guide slide can unlock: Alt, Sym and a held key for its symbol are each their own, so a slide teaches only one. */
/**
 * What a guide slide can teach, for its lock. [Typing] is a plain key that types: a letter, a digit or punctuation. [Space] is a
 * space that would type, from the key or a light tap; the quick taps that steer are [Cursor], so a slide can lock the one and teach
 * the other (owner, 2026-11-13: a lone Space typed into the steering lines). [Backspace] is the key itself; the flick is [Flick].
 */
internal enum class GuideLesson {
    Swipe, BarTap, BarHold, Flick, Cursor, Alt, Sym, HoldSymbol, Enter, Ctrl, Typing, Space, Backspace, DeleteHold
}

internal object GuideHooks {
    /** A short scene on the keyboard's own bar, for the guide. It types nothing, and any input on the keyboard ends it. */
    sealed interface StripDemo {
        /** [typo] shows in the middle of the bar, then [fix] swaps in, as Space does when it fixes a word. */
        data class Fix(val typo: String, val fix: String) : StripDemo
        /** [words] show, a ghost finger holds the middle one, and its menu opens. */
        data class Menu(val words: List<String>, val opens: Boolean = true) : StripDemo
    }

    const val FIELD = "com.titankeys.guide"
    /**
     * The playground's field: a real field, with the real word menu, saves and clipboard, and none of the slides' locks or
     * show-only parts. It only shares the drawing of the keys with the guide's field. See [practice].
     */
    const val PLAYGROUND = "com.titankeys.playground"

    /** Whether a field's private options are the guide's or the playground's: both draw the keys as your finger moves. */
    fun practice(privateOptions: String?) = privateOptions?.contains(FIELD) == true || privateOptions?.contains(PLAYGROUND) == true

    /**
     * The next swipe comes out as its second guess, so that the right word is waiting on the strip. The guide says that it does
     * this on purpose, for the step that teaches the strip. One swipe uses it up.
     */
    @Volatile var nearMiss = false

    /**
     * Any word typed becomes the word that the guide's line has next, however far off it is, so the first slide has no way to fail
     * (owner, v5). Only in the guide's field.
     */
    @Volatile var forceFix = false

    /**
     * The keyboard listens here, so that the guide can put it in the right menu when a step starts: the Sym panel open for a step
     * about Alt or the emoji, and closed for a step that types. The keyboard acts on it only while it serves the guide's field.
     */
    @Volatile var panelRequests: ((Modifiers.Panel) -> Unit)? = null

    fun openPanel(panel: Modifiers.Panel) {
        panelRequests?.invoke(panel)
    }

    /**
     * The guide's word for a word that starts after the given text, while the guide is open. The keyboard puts it on the strip
     * when a word comes out different, so that one touch puts it right.
     */
    @Volatile var script: ((String) -> String?)? = null

    /** True once, for the swipe that the guide wants as a near miss, and only in the guide's field. */
    fun takeNearMiss(privateOptions: String?): Boolean {
        if (!nearMiss || privateOptions?.contains(FIELD) != true) return false
        nearMiss = false
        return true
    }

    /**
     * The strip listens here, so that a slide can act out a gesture on the keyboard's own bar: see [StripDemo]. Null stops one
     * that plays. The keyboard acts on it only while it serves the guide's field.
     */
    @Volatile var stripDemos: ((StripDemo?) -> Unit)? = null

    /** The strip listens here: its words wait for the given ms, then show once, as a slide changes. Only in the guide's field. */
    @Volatile var barHolds: ((Long) -> Unit)? = null

    fun holdBar(millis: Long) {
        barHolds?.invoke(millis)
    }

    fun stripDemo(demo: StripDemo?) {
        stripDemos?.invoke(demo)
    }

    /**
     * The strip listens here, so that a slide can point at a word on the bar: the word in that place, as the keyboard ranks them,
     * wiggles up and down until it is picked or changes. Null stops it. Only in the guide's field.
     */
    @Volatile var stripPoints: ((Int?) -> Unit)? = null

    fun stripPoint(index: Int?) {
        stripPoints?.invoke(index)
    }

    /**
     * What the slide on the guide's screen unlocks, or null for no lock. In the guide's field only, anything else does nothing at
     * all: no text and no haptic. Letters, Backspace and Space always work.
     */
    @Volatile var lessons: Set<GuideLesson>? = null

    fun allows(lesson: GuideLesson) = lessons?.contains(lesson) ?: true

    /** The right word after a miss, for the guide to point at wherever it is on the bar. The keyboard sets it. */
    @Volatile var rightWord: String? = null

    /** The keyboard listens here, so the bar shows its side words, or only the middle one. Only in the guide's field. */
    @Volatile var barSideRequests: ((Boolean) -> Unit)? = null

    fun barSides(show: Boolean) {
        barSideRequests?.invoke(show)
    }

    /** The keyboard listens here, so the guide points at a word by itself, wherever it sits on the bar. */
    @Volatile var stripPointWords: ((String?) -> Unit)? = null

    fun stripPointWord(word: String?) {
        stripPointWords?.invoke(word)
    }

    /** The word menu is open on the bar, in the guide's field. The keyboard sets it. A demo never plays over it (v5). */
    @Volatile var menuOpen: (() -> Boolean)? = null

    /** The keyboard listens here, so the guide closes the word menu as a slide changes. Only in the guide's field. */
    @Volatile var menuCloses: (() -> Unit)? = null

    fun closeMenu() {
        menuCloses?.invoke()
    }

    /** How many words the bar shows now, or -1 outside the guide's field. The keyboard sets it. */
    @Volatile var barCount: (() -> Int)? = null

    /** Where the right word is on the bar after a miss, as the keyboard ranks the words, for the guide to point at. */
    @Volatile var rightPlace = 1

    /** The guide's prank: the one nice clip in its clipboard lesson pastes something else, which the guide owns up to. */
    fun swapClip(text: String) = if (text == PRANK_CLIP) PRANK_PASTE else text

    /** The keyboard listens here, so that the bar stops offering the newest clip once the guide is done with it. */
    @Volatile var clipDismissals: (() -> Unit)? = null

    fun dismissClip() {
        clipDismissals?.invoke()
    }

    /** The keyboard listens here, so that the clipboard lesson has clips to show: it pins these, newest first, once. */
    @Volatile var clipSeeds: ((List<String>) -> Unit)? = null

    /** The keyboard's clipboard shows only [clips] in the guide's field, never saved; empty shows the user's own again. */
    fun seedClips(clips: List<String>) {
        clipSeeds?.invoke(clips)
    }

    /** The guide's word for a word after [before], only in the guide's field. */
    fun intended(privateOptions: String?, before: String): String? =
        if (privateOptions?.contains(FIELD) == true) script?.invoke(before)?.takeIf { it.isNotEmpty() } else null
}
