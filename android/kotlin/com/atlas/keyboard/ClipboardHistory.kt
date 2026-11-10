package com.atlas.keyboard

/**
 * The texts that you copied last, newest first, for the Clipboard page of the Sym panel. A clip goes after [KEEP_MILLIS] unless
 * it is pinned. It stays on this phone, in a file that backups leave out, and it is never part of a data contribution.
 * It needs no Android, so the checks can run it with any clock.
 */
class ClipboardHistory(clips: List<Clip> = emptyList()) {
    data class Clip(val text: String, val time: Long, val pinned: Boolean = false)

    private val items = ArrayList(clips.take(LIMIT))

    val clips: List<Clip> get() = items

    /**
     * Keeps a copied text as the newest clip. A text that is already here moves to the front and keeps its pin. Returns false if
     * the text is not kept: blank, too long, or every place is pinned.
     */
    fun add(text: String, now: Long): Boolean {
        if (text.isBlank() || text.length > MAX_CHARS) return false
        val earlier = items.indexOfFirst { it.text == text }
        val pinned = earlier >= 0 && items[earlier].pinned
        if (earlier >= 0) items.removeAt(earlier)
        else if (items.size >= LIMIT) {
            val oldest = items.indexOfLast { !it.pinned }
            if (oldest < 0) return false
            items.removeAt(oldest)
        }
        items.add(0, Clip(text, now, pinned))
        return true
    }

    /** Takes out the clips that are older than [KEEP_MILLIS] and not pinned. Returns true if any went. */
    fun expire(now: Long) = items.removeAll { !it.pinned && now - it.time !in 0..KEEP_MILLIS }

    fun togglePin(index: Int): Boolean {
        val clip = items.getOrNull(index) ?: return false
        items[index] = clip.copy(pinned = !clip.pinned)
        return true
    }

    /** Clear all: every clip goes but the pinned ones, which you unpin to remove. */
    fun clear() = items.removeAll { !it.pinned }

    /** One line for each clip: its time, whether it is pinned, and its text with backslash, tab and line breaks escaped. */
    fun encode() = items.joinToString("\n") { "${it.time}\t${if (it.pinned) 1 else 0}\t${escape(it.text)}" }

    companion object {
        /** The letter keys of the panel hold this many, with room to spare. */
        const val LIMIT = 20
        const val KEEP_MILLIS = 60 * 60 * 1000L
        /** A longer text stays in Android's own clipboard only. */
        const val MAX_CHARS = 10_000

        /** Reads what [encode] wrote. A damaged line is left out. */
        fun decode(stored: String) = ClipboardHistory(stored.split('\n').mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            val time = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            if (parts.size < 3 || parts[1] !in setOf("0", "1")) return@mapNotNull null
            Clip(unescape(parts[2]), time, parts[1] == "1").takeIf { it.text.isNotBlank() }
        })

        /** A short label for a key: the start of the clip's first line that has text. */
        fun label(text: String, length: Int = LABEL_LENGTH): String {
            val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
            return if (line.length <= length) line else line.take(length - 1).trimEnd() + "…"
        }

        /** The clip on one line, for the strip and the band under the keys, cut at [length] characters. */
        fun preview(text: String, length: Int = Int.MAX_VALUE): String {
            val line = text.trim().replace(Regex("\\s+"), " ")
            return if (line.length <= length) line else line.take(length - 1).trimEnd() + "…"
        }

        private const val LABEL_LENGTH = 6

        private fun escape(text: String) = buildString {
            for (character in text) when (character) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }

        private fun unescape(text: String) = buildString {
            var escaped = false
            for (character in text) {
                if (!escaped && character == '\\') {
                    escaped = true
                    continue
                }
                append(if (!escaped) character else when (character) {
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    else -> character
                })
                escaped = false
            }
        }
    }
}
