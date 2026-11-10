package com.atlas.keyboard

/**
 * What the interactive guide asks of the keyboard, only in the guide's own field. The guide runs in the keyboard's process, so this
 * is a plain shared object. No other field is ever touched: the guide's field marks itself with [FIELD] in its private options.
 */
internal object GuideHooks {
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

    /** The guide's word for a word after [before], only in the guide's field. */
    fun intended(privateOptions: String?, before: String): String? =
        if (privateOptions?.contains(FIELD) == true) script?.invoke(before)?.takeIf { it.isNotEmpty() } else null
}
