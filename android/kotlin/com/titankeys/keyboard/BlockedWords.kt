package com.titankeys.keyboard

/**
 * Words the bar never offers in an input method (the native review, 2026-11-17): profanity, slurs, sexual violence and self-harm,
 * from its `blocked.txt` (tools/im/blocklist.py: LDNOOBW, CC BY 4.0, and a short hand list). Starters, next words and
 * completions leave them out, and no fix puts one in; the user can still type them, and one the user has typed often enough to
 * be learned may come back for them (LanguageWords). Spaced scripts and Thai match whole words, in lower case; Chinese and
 * Japanese, written without spaces, match a blocked word of two characters or more anywhere inside a phrase.
 */
class BlockedWords(private val words: Set<String>, private val inside: Boolean) {
    fun blocks(word: String): Boolean {
        if (words.isEmpty() || word.isEmpty()) return false
        val lower = word.lowercase()
        // Inside a phrase only for a blocked word of two characters or more: one character alone (性, 奶) is in ordinary
        // phrases (一致性, 奶茶), so it blocks only the word that is just that character.
        return if (inside) lower in words || words.any { it.length > 1 && it in lower } else lower in words
    }

    val size get() = words.size

    companion object {
        val NONE = BlockedWords(emptySet(), false)

        /** A `blocked.txt`: one word a line, '#' lines for the sources. [script] decides how they match. */
        fun read(lines: Sequence<String>, script: String): BlockedWords = BlockedWords(
            lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { it.lowercase() }.toSet(),
            inside = script == "Hani" || script == "Jpan")
    }
}
