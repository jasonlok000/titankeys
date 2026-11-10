package com.atlas.keyboard

/**
 * The texts that you copied last, newest first, for the Clipboard page of the Sym panel. A clip goes after [KEEP_MILLIS] unless
 * it is pinned. It stays on this phone, in a file that backups leave out, and it is never part of a data contribution.
 * It needs no Android, so the checks can run it with any clock.
 */
class ClipboardHistory(clips: List<Clip> = emptyList()) {
    data class Clip(val text: String, val time: Long, val pinned: Boolean = false)

    private val items = ArrayList(clips).also { trim(it) }

    val clips: List<Clip> get() = items

    /**
     * Keeps a copied text as the newest clip. A text that is already here moves to the front and keeps its pin. Returns false if
     * the text is not kept: blank or too long. Past [LIMIT] clips that are not pinned, the oldest of them goes. Pins have no limit.
     */
    fun add(text: String, now: Long): Boolean {
        if (text.isBlank() || text.length > MAX_CHARS) return false
        val earlier = items.indexOfFirst { it.text == text }
        val pinned = earlier >= 0 && items[earlier].pinned
        if (earlier >= 0) items.removeAt(earlier)
        items.add(0, Clip(text, now, pinned))
        trim(items)
        return true
    }

    /** Takes out the clips that are older than [KEEP_MILLIS] and not pinned. Returns true if any went. */
    fun expire(now: Long) = items.removeAll { !it.pinned && now - it.time !in 0..KEEP_MILLIS }

    /** Pins or unpins the clip at [index]. Returns false if there is no clip there. */
    fun togglePin(index: Int): Boolean {
        val clip = items.getOrNull(index) ?: return false
        items[index] = clip.copy(pinned = !clip.pinned)
        return true
    }

    /** Clear all: every clip goes but the pinned ones, which you unpin to remove. */
    fun clear() = items.removeAll { !it.pinned }

    /** One line for each clip: its time, whether it is pinned, and its text with backslash, tab and line breaks escaped. */
    fun encode() = items.joinToString("\n") { "${it.time}\t${if (it.pinned) 1 else 0}\t${escape(it.text)}" }

    /**
     * Which clips the clipboard page shows, as the symbols and emoji show theirs: two groups, Clipboard (every clip, newest first)
     * and Pinned, each in pages of [PER_PAGE]. A swipe up or down turns the page, and a swipe sideways changes the group.
     */
    class Pages {
        var group = RECENT
            private set
        var page = 0
            private set

        /** The places in [clips] of the open group's clips, in order. */
        fun items(clips: List<Clip>) = if (group == PINNED) clips.indices.filter { clips[it].pinned } else clips.indices.toList()

        fun pageCount(clips: List<Clip>) = maxOf(1, (items(clips).size + PER_PAGE - 1) / PER_PAGE)

        /** The place in [clips] of the clip in field [slot] of the open page, or null if that field is empty. */
        fun index(clips: List<Clip>, slot: Int) = items(clips).getOrNull(page * PER_PAGE + slot)

        fun reset() {
            group = RECENT
            page = 0
        }

        /** Keeps the page inside the group after clips come or go. */
        fun fit(clips: List<Clip>) {
            page = page.coerceIn(0, pageCount(clips) - 1)
        }

        /** Opens the first page of a group. Returns true if a different group now shows. */
        fun open(group: Int): Boolean {
            if (group !in 0 until GROUPS || group == this.group) return false
            this.group = group
            page = 0
            return true
        }

        /** The group at a place along the category strip, from 0 at its left end to 1 at its right end. */
        fun groupAt(across: Float) = (across * GROUPS).toInt().coerceIn(0, GROUPS - 1)

        /** The group under a key that covers this part of the category strip. The open group stays if it is under the key. */
        fun groupUnder(left: Float, right: Float) =
            if ((group + 1f) / GROUPS > left && group.toFloat() / GROUPS < right) group else groupAt((left + right) / 2)

        /** A swipe, in keys across and rows down, as on the symbols. Returns true if a different page now shows. */
        fun swipe(clips: List<Clip>, across: Float, down: Float): Boolean {
            val before = group to page
            if (kotlin.math.abs(down) > kotlin.math.abs(across)) page = Math.floorMod(page + if (down < 0) 1 else -1, pageCount(clips))
            else {
                group = Math.floorMod(group + if (across > 0) -1 else 1, GROUPS)
                page = 0
            }
            return (group to page) != before
        }
    }

    companion object {
        /** The newest clips that are not pinned. Pins have no limit. */
        const val LIMIT = 20
        /** The clipboard page shows this many clips at a time: two to a row, each in a wide field over half a row of keys. */
        const val PER_PAGE = 6
        const val RECENT = 0
        const val PINNED = 1
        const val GROUPS = 2

        private fun trim(items: MutableList<Clip>) {
            while (items.count { !it.pinned } > LIMIT) items.removeAt(items.indexOfLast { !it.pinned })
        }

        /** Which half of a row of the panel a column is in, 0 or 1, or null for an outer column. */
        fun halfAt(row: Int, column: Int): Int? {
            val key = Layout.rows.getOrNull(row)?.getOrNull(column) ?: return null
            if (!Layout.isLetter(key) || column == 0 || column == Layout.rows[row].lastIndex) return null
            return if (column < HALF_SPLIT) 0 else 1
        }

        /** The field over a key: row times two, plus one for the right half. Null for a key that is no letter of the inner columns. */
        fun slot(key: String): Int? {
            for ((row, keys) in Layout.rows.take(PER_PAGE / 2).withIndex()) {
                val column = keys.indexOf(key)
                if (column >= 0) return halfAt(row, column)?.let { row * 2 + it }
            }
            return null
        }

        /** The first column of a field, where it starts. */
        fun firstColumn(row: Int, half: Int) = Layout.rows[row].indices.first { halfAt(row, it) == half }

        /** How many keys wide a field is. */
        fun width(row: Int, half: Int) = Layout.rows[row].indices.count { halfAt(row, it) == half }

        /** A letter key under a field, which pastes what a touch on the field pastes. */
        fun keyFor(slot: Int) = Layout.rows[slot / 2][firstColumn(slot / 2, slot % 2)]

        private const val HALF_SPLIT = 5
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

        /** The clip on one line, for the strip and the band under the keys, cut at [length] characters. */
        fun preview(text: String, length: Int = Int.MAX_VALUE): String {
            val line = text.trim().replace(Regex("\\s+"), " ")
            return if (line.length <= length) line else line.take(length - 1).trimEnd() + "…"
        }

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
