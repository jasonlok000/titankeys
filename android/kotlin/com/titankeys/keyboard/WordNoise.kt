package com.titankeys.keyboard

/**
 * What the bar leaves out of a language's word data that isn't a word to offer (the native review, 2026-11-17): Korean's bound
 * endings, which the news corpus split off as words (있다 고), and Thai's fragments and rare names among its next words.
 * - [fragment]: never offered, as a completion or a next word. Korean's endings and particles that never stand alone; Thai's
 *   fragments and abbreviations common enough to pass the floor.
 * - [nextWord]: a next word from the input method's table only if its list counts it at least [NEXT_FLOOR] (Thai), so common
 *   words come and rare names (พิทยา after คุณ) and fragments (มแนว, ผบ) don't.
 */
object WordNoise {
    private val FRAGMENTS = mapOf(
        "ko" to setOf("고", "며", "라고", "이라고", "면서", "다고", "이며", "으며", "다며", "라며", "냐고", "자고", "으면서", "이면서"),
        "th" to setOf("โร", "ทนา", "มแนว", "ผบ"),
    )
    private val NEXT_FLOOR = mapOf("th" to 120)

    fun fragment(id: String, word: String) = FRAGMENTS[id]?.contains(word) == true

    fun nextWord(id: String, word: String, frequency: (String) -> Int): Boolean =
        !fragment(id, word) && NEXT_FLOOR[id]?.let { frequency(word) >= it } != false
}
