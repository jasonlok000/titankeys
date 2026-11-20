package com.titankeys.keyboard

/**
 * Cantonese typed in Latin letters among English: Jyutping and Yale syllables, and the chat spellings Hong Kong uses for
 * particles ("ja", "lor", "wor"). With a Cantonese input method on, English autocorrect leaves them as typed (owner,
 * 2026-11-20, after "ja" became "is": "I'm also ok with this cantonese specific optimization"). Every pairing of an initial and
 * a final counts, a few more than Cantonese uses, as a missed fix costs less than a word changed. Tones are not typed in chat.
 */
object CantoneseSyllables {
    private val INITIALS = listOf("", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "ng", "h", "gw", "kw", "w", "s")
    private val JYUTPING_INITIALS = INITIALS + listOf("z", "c", "j")
    private val YALE_INITIALS = INITIALS + listOf("j", "ch", "y")
    private val JYUTPING_FINALS = listOf(
        "aa", "aai", "aau", "aam", "aan", "aang", "aap", "aat", "aak", "ai", "au", "am", "an", "ang", "ap", "at", "ak",
        "e", "ei", "eu", "em", "eng", "ep", "ek", "i", "iu", "im", "in", "ing", "ip", "it", "ik", "o", "oi", "ou", "on", "ong",
        "ot", "ok", "u", "ui", "un", "ung", "ut", "uk", "oe", "oeng", "oek", "eoi", "eon", "eot", "yu", "yun", "yut")
    /** Yale writes Jyutping's "aa" as "a" alone, and "oe", "eo" as "eu". */
    private val YALE_FINALS = listOf(
        "a", "aai", "aau", "aam", "aan", "aang", "aap", "aat", "aak", "ai", "au", "am", "an", "ang", "ap", "at", "ak",
        "e", "ei", "em", "eng", "ep", "ek", "i", "iu", "im", "in", "ing", "ip", "it", "ik", "o", "oi", "ou", "on", "ong",
        "ot", "ok", "u", "ui", "un", "ung", "ut", "uk", "eu", "eung", "euk", "eui", "eun", "eut", "yu", "yun", "yut")
    /** Hong Kong chat spellings that are neither: particles and common words. */
    private val CHAT = listOf(
        "ja", "jor", "jo", "la", "lah", "lor", "wor", "woh", "ga", "gar", "ge", "gei", "gum", "kum", "mah", "meh", "leh", "lei",
        "nei", "ngo", "keoi", "hai", "mai", "dou", "dim", "mud", "mut", "sik", "ar", "aa", "wo", "lo", "zo", "zeoi", "gwai",
        "hea", "chur", "chok", "kai", "siu", "sai", "tim", "law", "dlam")

    private val all: Set<String> = buildSet {
        for (initial in JYUTPING_INITIALS) for (final in JYUTPING_FINALS) add(initial + final)
        for (initial in YALE_INITIALS) for (final in YALE_FINALS) add(initial + final)
        addAll(CHAT)
        // Syllabic m and ng stand alone ("m goi", "ng").
        add("m"); add("ng"); add("hm"); add("hng")
    }

    /** Whether [word], in any case, is a Cantonese syllable or chat spelling. */
    fun contains(word: String): Boolean = word.length in 1..6 && word.lowercase() in all
}
