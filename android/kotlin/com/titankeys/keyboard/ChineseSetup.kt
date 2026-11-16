package com.titankeys.keyboard

/**
 * There's no default Chinese variant (owner, 2026-11-16): the user picks one. So the first time a Chinese input method types
 * with none picked, the keyboard opens Settings' Chinese page for it, once. After that, until one is picked, the bar's only
 * choice says to pick one (RimeEngine's NO_VARIANT). Reset most things clears the once with the variant itself.
 */
internal object ChineseSetup {
    /** Settings' Chinese characters page, opened the way notifications open a page. */
    const val PAGE = "/settings/chinese"

    /** Engines whose input methods are Chinese and take the variant. */
    private val CHINESE_ENGINES = setOf("rime")

    /**
     * Whether switching to [method] should open [PAGE]: a Chinese engine's input method, no variant picked ([variant] is the
     * setting, empty until one is), the page not opened before ([shown]), and a field being typed in that isn't the guide's
     * ([typing]). In the guide, which teaches English, the page waits for a later switch.
     */
    fun opensPage(method: InputMethod, variant: String, shown: Boolean, typing: Boolean): Boolean =
        typing && !shown && variant.isEmpty() && method.engine in CHINESE_ENGINES
}
