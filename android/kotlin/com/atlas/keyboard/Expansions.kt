package com.atlas.keyboard

/**
 * Text expansion: snippets (issue 50) and emoji shortcodes (issue 47). Each turns a short code into other text, and a Backspace
 * straight after puts the code back, the same way it takes back an autocorrect.
 *
 * - A snippet's shortcut starts with [start], such as `;addr`. Space after it puts in the snippet's text.
 * - A shortcode is a name between two colons, such as `:smile:`. The second colon puts in the emoji.
 *
 * A code counts only at the start of the text or after a space or an opening mark, so `12:30:` and `a;b` never expand.
 * The keyboard moves a space typed before a colon or a semicolon to after it, as in "Note:". So a code can also start at
 * `freeMark`, the place of such a mark, and the space comes back in front of what the code puts in.
 * Names match in any case. The strip offers the snippets and emoji whose code starts with what is typed.
 *
 * This holds no Android code, so tests can reach it. The keyboard reads the snippets and the shortcode list.
 */
class Expansions(
    /** Each snippet's text by its shortcut, without [start]. */
    snippets: Map<String, String>,
    /** The character that starts a snippet's shortcut. */
    val start: Char,
    /** Each emoji by its shortcode name, such as "smile", in the order to offer them. Empty when shortcodes are off. */
    private val emoji: Map<String, String>,
) {
    /** [code] is the text that goes away, and [text] what takes its place. [restored] is what Backspace puts back. */
    data class Expansion(val code: String, val text: String, val restored: String)

    /** A choice for the strip: [label] shows on it, and [expansion] replaces what was typed. */
    data class Choice(val label: String, val expansion: Expansion)

    private val snippets = snippets.entries.associate { it.key.lowercase() to it.value }

    /** The snippet whose shortcut [before] ends with. Space puts it in, with the space after it. */
    fun snippet(before: String, freeMark: Int = -1): Expansion? {
        val code = codeAt(before, start, minName = 1, freeMark) ?: return null
        val text = snippets[code.substring(1).lowercase()] ?: return null
        return Expansion(code, spaced(before, code, "$text "), spaced(before, code, "$code "))
    }

    /** The emoji for the shortcode that [before] ends with, once a second colon closes it. */
    fun shortcode(before: String, freeMark: Int = -1): Expansion? {
        val code = codeAt(before, ':', minName = 1, freeMark) ?: return null
        val text = emoji[code.substring(1).lowercase()] ?: return null
        return Expansion(code, spaced(before, code, text), spaced(before, code, "$code:"))
    }

    /**
     * Snippets and emoji whose code starts with what [before] ends with, for the strip: at most [limit], an exact name first.
     * A start character on its own offers the snippets. A shortcode needs [MIN_SHORTCODE] letters, as `:)` is a face of its own.
     */
    fun choices(before: String, limit: Int, freeMark: Int = -1): List<Choice> {
        codeAt(before, start, minName = 0, freeMark)?.let { code ->
            val typed = code.substring(1).lowercase()
            val found = snippets.entries.filter { it.key.startsWith(typed) }
                .sortedWith(compareBy({ it.key != typed }, { it.key }))
                .take(limit)
                .map { Choice(label(it.value), Expansion(code, spaced(before, code, it.value + " "), spaced(before, code, code))) }
            if (found.isNotEmpty()) return found
        }
        if (emoji.isEmpty()) return emptyList()
        val code = codeAt(before, ':', minName = MIN_SHORTCODE, freeMark) ?: return emptyList()
        val typed = code.substring(1).lowercase()
        val seen = HashSet<String>()
        val exact = emoji[typed]?.let { listOf(typed to it) }.orEmpty()
        return (exact + emoji.entries.asSequence().filter { it.key.startsWith(typed) }.map { it.key to it.value })
            .filter { seen.add(it.second) }
            .take(limit)
            .map { (_, text) -> Choice(text, Expansion(code, spaced(before, code, text), spaced(before, code, code))) }
            .toList()
    }

    companion object {
        const val MAX_NAME = 40

        /**
         * Whether saving [key] would replace one of the user's own in [existing] that isn't the one being changed, [previous]. Keys
         * match in any case. Saving onto one replaced it without a word, up to 4,000 characters of a snippet (bug hunt #15).
         */
        fun taken(key: String, previous: String?, existing: Collection<String>): Boolean {
            val wanted = key.lowercase()
            return wanted != previous?.lowercase() && existing.any { it.lowercase() == wanted }
        }
        const val MIN_SHORTCODE = 2
        private const val LABEL_LENGTH = 24
        private const val OPENERS = "\"'([{“‘"

        /** A character that a shortcut or a shortcode name can hold. */
        fun nameChar(char: Char) = char.isLetterOrDigit() || char == '_' || char == '-' || char == '+'

        /** Whether [name] can be a snippet's shortcut. */
        fun validName(name: String) = name.length in 1..MAX_NAME && name.all(::nameChar)

        /**
         * The code at the end of [before]: [mark] and at least [minName] name characters, with nothing before the mark but
         * the start of the text, a space or an opening mark. A mark at [freeMark] needs none of them, unless it ends a sentence:
         * "coming?" then "omw" is a question and a word, never the snippet "?omw" (bug hunt #14).
         */
        fun codeAt(before: String, mark: Char, minName: Int, freeMark: Int = -1): String? {
            var index = before.length
            while (index > 0 && before.length - index < MAX_NAME && nameChar(before[index - 1])) index--
            if (index == 0 || before[index - 1] != mark || before.length - index < minName) return null
            val markAt = index - 1
            val free = markAt == freeMark && mark !in SENTENCE_ENDS
            if (!free && !opens(before, markAt)) return null
            return before.substring(markAt)
        }

        /** Marks that end a sentence. One that took the place of a space ends the sentence, and starts no code. */
        private const val SENTENCE_ENDS = ".!?"

        /** Whether a code may start at [at]: at the start of the text, or after a space or an opening mark. */
        private fun opens(before: String, at: Int) = at == 0 || before[at - 1].isWhitespace() || before[at - 1] in OPENERS

        /** [text] with a space in front when [code] took the place of a space, at [codeAt]'s free mark. */
        private fun spaced(before: String, code: String, text: String) =
            if (opens(before, before.length - code.length)) text else " $text"

        /** A snippet's text on one short line, for the strip. */
        fun label(text: String): String {
            val line = text.trim().replace(Regex("\\s+"), " ")
            return if (line.length <= LABEL_LENGTH) line else line.take(LABEL_LENGTH - 1).trimEnd() + "…"
        }

        /** Reads the shortcode list: one emoji per line, then its names, split by spaces. Lines that start with # are notes. */
        fun readShortcodes(lines: Sequence<String>): Map<String, String> {
            val emoji = LinkedHashMap<String, String>()
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val parts = line.trim().split(' ')
                for (name in parts.drop(1)) if (name.isNotEmpty()) emoji.putIfAbsent(name.lowercase(), parts[0])
            }
            return emoji
        }
    }
}
