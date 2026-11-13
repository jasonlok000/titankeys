package com.atlas.autocorrect

/** Chat shorthand (#63): the list in en_shorthand.txt and the user's own, as autocorrect and Settings read them. */
object Shorthand {
    const val MAX_SHORTHAND = 12
    const val MAX_FULL = 40

    /**
     * Each shorthand of en_shorthand.txt in lowercase, in the file's order, with its full word after a tab, or null where it
     * stays as typed.
     */
    fun parse(lines: Sequence<String>): Map<String, String?> =
        lines.map { it.trim() }.filterNot { it.isEmpty() || it.startsWith("#") }
            .map { line -> line.split('\t').let { key(it[0]) to it.getOrNull(1)?.trim()?.takeIf(String::isNotEmpty) } }
            .toMap(LinkedHashMap())

    /** How a shorthand is looked up: in lowercase, with a curly apostrophe as a straight one. */
    fun key(shorthand: String) = shorthand.lowercase().replace('’', '\'')

    /** What's wrong with the user's own [shorthand] for [full], or null if it can be saved. */
    fun problem(shorthand: String, full: String): String? = when {
        shorthand.isEmpty() || shorthand.length > MAX_SHORTHAND || !shorthand.all { it.isLetterOrDigit() || it == '\'' || it == '’' } ->
            "Shorthand can only use letters, numbers and ', up to $MAX_SHORTHAND of them."
        full.isBlank() -> "Type what it should turn into."
        full.length > MAX_FULL || full.any { it == '\t' || it == '\n' || it == '\r' } -> "Keep it to one line, up to $MAX_FULL characters."
        else -> null
    }
}
