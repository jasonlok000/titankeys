package com.atlas.keyboard

/**
 * What the interactive guide asks of the keyboard, only in the guide's own field. The guide runs in the keyboard's process, so this
 * is a plain shared object. No other field is ever touched: the guide's field marks itself with [FIELD] in its private options.
 */
internal object GuideHooks {
    /** A short scene on the keyboard's own bar, for the guide. It types nothing, and any input on the keyboard ends it. */
    sealed interface StripDemo {
        /** [typo] shows in the middle of the bar, then [fix] swaps in, as Space does when it fixes a word. */
        data class Fix(val typo: String, val fix: String) : StripDemo
        /** [words] show, a ghost finger holds the middle one, and its menu opens. */
        data class Menu(val words: List<String>) : StripDemo
    }

    const val FIELD = "com.titankeys.guide"

    /**
     * The next swipe comes out as its second guess, so that the right word is waiting on the strip. The guide says that it does
     * this on purpose, for the step that teaches the strip. One swipe uses it up.
     */
    @Volatile var nearMiss = false

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

    /** The guide's word for a word after [before], only in the guide's field. */
    fun intended(privateOptions: String?, before: String): String? =
        if (privateOptions?.contains(FIELD) == true) script?.invoke(before)?.takeIf { it.isNotEmpty() } else null
}
