package com.atlas.keyboard

import com.atlas.connected.Json

/**
 * Every input method TitanKeys has, and which are on (docs/languages.md, "How an input method declares itself").
 *
 * The keyboard reads only the index (`android/assets/im/index.json`) as it starts. An input method's own `im.json` and word
 * list load when it's switched to. English is always in the list, first, and can't be missing.
 */
class InputMethodIndex(entries: List<Entry>) {
    /** What the index says about one input method: enough for the overlay, Settings and Android's keyboard list. */
    data class Entry(
        val id: String,
        val name: String,
        val nameEnglish: String,
        val label: String,
        val locale: String,
        val script: String,
        val engine: String? = null,
    )

    /** English first, then the rest in the index's order. */
    val entries: List<Entry> = listOf(ENGLISH) + entries.filter { it.id != InputMethod.ENGLISH_ID }
    private val byId = this.entries.associateBy { it.id }

    operator fun get(id: String) = byId[id]
    val ids get() = entries.map { it.id }

    /**
     * The input methods that are on, in the overlay's order, from the setting's text (ids with commas between). Unknown ids
     * are dropped, as are repeats and any past [InputMethod.MAX_ON]. English is on when nothing else is.
     */
    fun on(setting: String?): List<Entry> {
        val wanted = setting.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val known = wanted.mapNotNull { byId[it] }.take(InputMethod.MAX_ON)
        return known.ifEmpty { listOf(ENGLISH) }
    }

    /** The input method to type with: [current] when it's on, or else the first one that is. */
    fun current(setting: String?, current: String?): Entry {
        val on = on(setting)
        return on.firstOrNull { it.id == current } ?: on.first()
    }

    companion object {
        val ENGLISH = Entry(InputMethod.ENGLISH_ID, "English", "English", "EN", "en", "Latn")

        /** Reads `index.json`: a list of entries. */
        fun parse(text: String): InputMethodIndex {
            val list = Json.parse(text) as? List<*> ?: throw Json.Error("index.json must be a list")
            return InputMethodIndex(list.map {
                val entry = it as? Map<*, *> ?: throw Json.Error("each entry must be an object")
                fun text(key: String) = entry[key] as? String ?: throw Json.Error("$key must be text")
                Entry(text("id"), text("name"), text("nameEnglish"), text("label"), text("locale"), text("script"), entry["engine"] as? String)
            })
        }

        /** The setting's text for these ids, in order. */
        fun setting(ids: List<String>) = ids.distinct().joinToString(",")

        /**
         * Turns [id] on or off in the setting [current], keeping the order: a newly turned on one goes last. English can't be
         * turned off while it's the only one, and nothing goes past [InputMethod.MAX_ON]. Returns the new setting, or null if
         * the change isn't allowed.
         */
        fun toggle(current: List<String>, id: String, on: Boolean): List<String>? = when {
            on && id in current -> current
            on && current.size >= InputMethod.MAX_ON -> null
            on -> current + id
            id !in current -> current
            current.size == 1 -> null
            else -> current - id
        }

        /** Moves [id] one place up (earlier) or down in the order. */
        fun move(current: List<String>, id: String, up: Boolean): List<String> {
            val at = current.indexOf(id)
            val to = if (up) at - 1 else at + 1
            if (at < 0 || to !in current.indices) return current
            return current.toMutableList().apply { add(to, removeAt(at)) }
        }
    }
}

/**
 * Where the switch overlay puts each input method that's on, over the top row's keys W to O (columns 1 to 8). With four or
 * fewer, each spans two keys so it's easier to hit. The top two rows pick, as the word menu's do: rest a finger on a key to
 * mark, press it to switch (docs/languages.md, "Switching").
 */
object SwitchOverlay {
    /** The first and last columns the overlay uses: W and O. */
    const val FIRST = 1
    const val LAST = 8
    /** The rows of letters that pick: the top two. */
    const val ROWS = 2

    /** The key columns each of [count] input methods spans, in order. */
    fun columns(count: Int): List<IntRange> {
        val shown = count.coerceIn(0, InputMethod.MAX_ON)
        return if (shown <= InputMethod.MAX_ON / 2) List(shown) { FIRST + 2 * it..FIRST + 2 * it + 1 }
        else List(shown) { FIRST + it..FIRST + it }
    }

    /** Which of [count] input methods the key [key] (A to Z) picks, or -1. */
    fun choice(key: String, count: Int): Int {
        val column = Picker.column(key, ROWS)
        if (column < 0) return -1
        return columns(count).indexOfFirst { column in it }
    }
}

/**
 * The picker on the bar: a row of choices, each over a key, picked with the keys as the word menu's buttons are. Accents use
 * it with one page, starting over the held key and spreading to fit; Chinese uses it with pages, from the left
 * (docs/languages.md, "Accents and the picker on the bar").
 */
object Picker {
    /** The ten key columns, Q to P. */
    const val COLUMNS = 10
    /** The rows of letters that pick a choice: the top two, as the word menu's. */
    const val ROWS = 2

    /** The key column of letter [key] (A to Z) in the top [rows] rows, or -1. */
    fun column(key: String, rows: Int = ROWS): Int {
        if (!Layout.isLetter(key)) return -1
        for (row in 0 until rows.coerceAtMost(Layout.rows.size)) {
            val column = Layout.rows[row].indexOf(key)
            if (column >= 0) return column
        }
        return -1
    }

    /** The column of letter [key] on any row, for centring the accents over the held key. */
    fun anyColumn(key: String): Int = column(key, Layout.rows.size)

    /**
     * The columns of [count] accents of the key in column [held]: centred over it, moved in to stay on the keys. More than
     * ten are cut to ten.
     */
    fun accentColumns(count: Int, held: Int): List<Int> {
        val shown = count.coerceIn(0, COLUMNS)
        if (shown == 0) return emptyList()
        val start = (held - (shown - 1) / 2).coerceIn(0, COLUMNS - shown)
        return List(shown) { start + it }
    }

    /** An engine's first choice sits over E: Q and W show the code being typed and the page. */
    const val FIRST_CHOICE = 2
    /** The most choices on a page: E to P. */
    const val CHOICES = COLUMNS - FIRST_CHOICE

    /** The columns of a page of [count] choices from an engine, from E. */
    fun pageColumns(count: Int): List<Int> = List(count.coerceIn(0, CHOICES)) { FIRST_CHOICE + it }

    /** Which choice the key [key] picks, given each choice's column, or -1. */
    fun choice(key: String, columns: List<Int>): Int {
        val column = column(key)
        return if (column < 0) -1 else columns.indexOf(column)
    }
}
