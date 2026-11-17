package com.titankeys.keyboard

/**
 * Which typing settings a language's page shows (owner, 2026-11-17: "some settings doesn't work in some languages ... so having it
 * outside makes it look like global settings"). With a second language on, each of these leaves Settings' main page for each
 * language's own page, and shows only where it works; the value is the language's own (TypingSettings.PER_LANGUAGE). Worked out
 * from the input method's own rules, so a new language shows the right rows by itself.
 */
object LanguageSettings {
    /**
     * Fix typos: English's, and every language that corrects on its own word list (LanguageCorrections.corrects: a list and no
     * engine). What gets fixed under it is English's alone: its kinds are English's rules.
     */
    const val FIXES = "fixes"
    /** Capital letter to start a sentence: a script with capitals. */
    const val CAPITALS = "capitals"
    /** Double Space types the language's full stop: none in Thai. */
    const val DOUBLE_SPACE = "doubleSpace"
    /** Space after a sentence: where the full stop is followed by a space. */
    const val SENTENCE_SPACES = "sentenceSpaces"
    /** Space after , ; : : not where the engine types its own marks (Chinese and Japanese, ChineseKeys.usesBar). */
    const val COMMA_SPACE = "commaSpace"
    /** Hold a letter for its symbol: not with accents, where a hold shows them, nor with a third layer (Thai). */
    const val HOLD = "hold"
    /** Swipe to type words: English's. */
    const val SWIPE = "swipe"
    /** Spell out shorthand: English's. */
    const val SHORTHAND = "shorthand"

    /** The engines that type their own marks: ChineseKeys.BAR_ENGINES, which needs the keyboard's other parts. */
    val OWN_MARKS = setOf("rime", "mozc")

    /** The rows [method]'s language shows, in the page's order. */
    fun rows(method: InputMethod): List<String> = buildList {
        if (method.isEnglish || method.words != null && method.engine == null) add(FIXES)
        if (method.rules.capitalise) add(CAPITALS)
        if (method.rules.fullStop.isNotBlank()) add(DOUBLE_SPACE)
        if (method.rules.fullStop.endsWith(" ")) add(SENTENCE_SPACES)
        if (method.engine !in OWN_MARKS) add(COMMA_SPACE)
        if (!method.hasAccents && ('a'..'z').none { method.held(it, false) != null }) add(HOLD)
        if (method.isEnglish) {
            add(SWIPE)
            add(SHORTHAND)
        }
    }

    /** The full stop a quick second Space types, as the Double Space row names it: "." in English, "。" in Chinese. */
    fun stop(method: InputMethod): String = method.rules.fullStop.trim()
}
