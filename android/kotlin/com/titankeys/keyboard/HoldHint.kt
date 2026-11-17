package com.titankeys.keyboard

/**
 * The line the bar says once, the first time an input method with held letters types (docs/languages.md, "Accents and the
 * picker on the bar"). Where a hold gives accents, it says so; in Cyrillic a hold gives the letters the keys have no room for
 * (ж э on L, х ъ on P), not accents, so it says that instead (F, docs/howto-drafts.md). Greek's holds are its accents. Input
 * methods without held letters on the bar (English, Thai's and Hebrew's held keys type straight in) say nothing.
 */
object HoldHint {
    // F: approved (docs/howto-drafts.md, "Cyrillic").
    const val ACCENTS = "Hold a letter for its accents."
    const val MISSING = "Missing a letter? Hold a key to find it."

    fun of(method: InputMethod): String? = when {
        method.accents.isEmpty() -> null
        method.script == "Cyrl" -> MISSING
        else -> ACCENTS
    }
}
