package com.titankeys.keyboard

import com.titankeys.connected.Json

/**
 * Settings > Your words > Back up everything (#65): one plain JSON file, `titankeys-backup-YYYY-MM-DD.json`, with a format name, a
 * version and one section per part. Restore takes the settings from the file and merges the lists, the file winning a clash, so
 * nothing on the phone is deleted. For a clean copy, Reset literally everything comes first.
 *
 * In: every setting and switch, haptics, saved, learned, favourite, blocked and unsaved words, refused fixes, autocorrect learning,
 * word case, the word pairs that next-word suggestions learned, Chinese learned words (Rime's own snapshots, see RimeBackup),
 * snippets and their start, shorthand, the app lists, emoji recents, whether the guide was seen, and in TitanKeys Debug the key calibration. Out: the clipboard, recordings, Share swipes' ID, consent
 * and sent list, the Accessibility disclosure's answer (Google Play wants it asked on each phone), phone checks (they describe this
 * phone) and the swipe models (they ship with the app).
 *
 * Every SharedPreferences file in the code is in [STORES] and every data file in [FILES], each in the backup or left out with the
 * reason, and tools/checks/backup.test.ts fails on one that isn't, so a new store has to say which it is. This file needs nothing
 * from Android, so tests/backup-all checks it on the JVM; BackupActivity does the reading and writing on the phone.
 */
internal object BackupFile {
    const val FORMAT = "titankeys-backup"
    const val VERSION = 1

    /** Export words' file before 3.0, which Restore still takes: the engine's own format name. */
    const val OLD_WORDS_FORMAT = "physical-swipe-habits"

    /** A backup is a few megabytes at most. Anything far bigger isn't one. */
    const val MAX_BYTES = 64L * 1024 * 1024

    // Restore's failures. The first two are the owner's lines from Import words (BackupFormat.java), kept as they were.
    const val NOT_A_BACKUP = "Yeah, that's not a TitanKeys backup. Pick the right file."
    const val DAMAGED = "Can't read that file, duh. Is it a TitanKeys backup?"
    // A backup from a newer TitanKeys, which this one won't half-restore.
    const val TOO_NEW = "This backup comes from a newer TitanKeys. Update this one, then restore it."

    /**
     * The engine's answer when the words were saved but its dictionary didn't refresh (SwipeTyping.importConfig). A restore
     * goes on after it, as its restart refreshes the dictionary anyway. A check keeps it equal to the engine's text.
     */
    const val WORDS_NEED_RESTART = "Learning was imported. Restart TitanKeys to use the imported words."

    fun name(year: Int, month: Int, day: Int) = "titankeys-backup-%04d-%02d-%02d.json".format(year, month, day)

    /** How a list kept in one key merges with the one on the phone. The file wins a clash. */
    enum class Merge {
        /** A set of words or apps: both. */
        UNION,
        /** "package=method": both, the file's method for an app in both. */
        KEYED_EQUALS,
        /** "shorthand<tab>full word": both, the file's word for a shorthand in both. */
        KEYED_TAB,
        /** "word|day": both, the file's day for a word in both. */
        KEYED_LAST_BAR,
        /** Comma-separated, newest first: the file's first, then the phone's, as many as the longer had. */
        RECENT_COMMA,
        /** Space-separated, newest first, as emoji recents. */
        RECENT_SPACE,
        /** Snippets' JSON list: both, the file's text for a shortcut in both. */
        SNIPPETS,
    }

    sealed class Store {
        /**
         * In the backup. A key in [lists] merges on restore; every other key is a setting and takes the file's value. With [only],
         * just those keys go in. [debugOnly]: only TitanKeys Debug has it, so only its backup holds it and only it restores it.
         */
        data class Backed(val lists: Map<String, Merge> = emptyMap(), val only: Set<String>? = null, val debugOnly: Boolean = false) : Store()

        /** Left out, and why. */
        data class Left(val why: String) : Store()
    }

    /** Every SharedPreferences file, by name. */
    val STORES: Map<String, Store> = mapOf(
        // Every switch, stepper and choice, the app lists, your own shorthand and the snippets' start.
        "typing_settings" to Store.Backed(lists = mapOf(
            "enter_apps" to Merge.KEYED_EQUALS, "exact_apps" to Merge.UNION, "hidden_bar_apps" to Merge.UNION,
            "recent_apps" to Merge.RECENT_COMMA, "recent_enter_apps" to Merge.RECENT_COMMA, "own_shorthand" to Merge.KEYED_TAB,
        )),
        "swipe_haptics" to Store.Backed(),
        "fixed_scale" to Store.Backed(),
        "word_learning" to Store.Backed(),
        // Learn new words' switch from before it moved to word_learning, still read as its default.
        "swipe_calibration" to Store.Backed(),
        "blocked_words" to Store.Backed(lists = mapOf("words" to Merge.UNION)),
        "unsaved_words" to Store.Backed(lists = mapOf("words" to Merge.UNION)),
        "favourite_words" to Store.Backed(lists = mapOf("words" to Merge.UNION)),
        "refused_fixes" to Store.Backed(lists = mapOf("fixes" to Merge.UNION)),
        "autocorrect_learning" to Store.Backed(lists = mapOf("kept_once_words" to Merge.KEYED_LAST_BAR)),
        "word_case" to Store.Backed(),
        "snippets" to Store.Backed(lists = mapOf("list" to Merge.SNIPPETS)),
        "emoji" to Store.Backed(lists = mapOf("recent" to Merge.RECENT_SPACE)),
        // The News switch only: Share swipes' ID, its consent and what was sent stay on this phone, as does the news itself.
        "connected" to Store.Backed(only = setOf("announcements")),
        // Whether the guide was seen. Release notes seen belong to this install.
        "app" to Store.Backed(only = setOf("guide_shown")),
        "key_calibration" to Store.Backed(debugOnly = true),
        "clipboard" to Store.Left("the clipboard can hold passwords and codes"),
        "back_disclosure" to Store.Left("Google Play wants the Accessibility disclosure answered on each phone"),
        "phone_checks" to Store.Left("they describe this phone"),
        "easter_egg" to Store.Left("it counts this phone's boots"),
    )

    /** Data kept in files rather than SharedPreferences, by the start of their names: null if it's in the backup, or why not. */
    val FILES: Map<String, String?> = mapOf(
        // Saved and learned words, through the engine's own export.
        "config" to null,
        // The word pairs that next-word suggestions learned.
        "user_ngrams.db" to null,
        "personal-words-v1" to "an old copy of the saved words, moved into config",
        "word-accuracy-v1" to "it measures this phone's swipes",
        "contributions" to "swipes waiting to be shared stay with Share swipes",
        "clipboard-history" to "the clipboard can hold passwords and codes",
        "rawlog" to "recordings stay on the phone",
        "bug-recording" to "recordings stay on the phone",
        "crash-" to "a crash report belongs to this phone",
        "backup-words" to "the words on their way through a backup or restore, which are in the backup themselves",
        "report-outbox" to "a bug report waiting to be sent belongs to this phone",
        // Rime's folder: its learned words go in through its own snapshots (RimeBackup); its data comes with the app.
        "engines" to null,
    )

    /** One learned word pair: [next] came after [first]. */
    data class WordPair(val first: String, val next: String, val count: Int, val lastUsed: Long)

    data class Contents(
        val app: String,
        val build: String,
        val created: String,
        /** Each backed-up file's keys and values: Boolean, Int, Long, Float, String or Set<String>. */
        val preferences: Map<String, Map<String, Any>>,
        /** The engine's own words export, as it wrote it, or null if the words couldn't be read. */
        val words: Map<String, Any?>?,
        val pairs: List<WordPair>,
        /** Rime's snapshots of its learned words, by file name, as RimeBackup has them: none if Chinese was never typed. */
        val chineseWords: Map<String, String> = emptyMap(),
    )

    /** What a picked file turned out to be. */
    sealed class Picked {
        data class Backup(val contents: Contents) : Picked()
        /** Export words' file from before 3.0. */
        object OldWords : Picked()
        /** Export snippets' file from before 3.0. */
        object OldSnippets : Picked()
    }

    class Problem(message: String) : Exception(message)

    /** Only what goes in: the backed-up stores, the listed keys, and the Debug-only ones only from TitanKeys Debug. */
    fun included(files: Map<String, Map<String, Any?>>, debug: Boolean): Map<String, Map<String, Any>> =
        files.mapNotNull { (name, values) ->
            val store = STORES[name] as? Store.Backed ?: return@mapNotNull null
            if (store.debugOnly && !debug) return@mapNotNull null
            val kept = values.filter { (key, value) -> value != null && (store.only == null || key in store.only) && typed(value) }
            @Suppress("UNCHECKED_CAST")
            (kept as Map<String, Any>).takeIf { it.isNotEmpty() }?.let { name to it }
        }.toMap()

    private fun typed(value: Any?) = value is Boolean || value is Int || value is Long || value is Float || value is String ||
        (value is Set<*> && value.all { it is String })

    // Writing

    fun write(contents: Contents): String {
        val root = linkedMapOf<String, Any?>(
            "format" to FORMAT,
            "version" to VERSION,
            "app" to contents.app,
            "build" to contents.build,
            "created" to contents.created,
            "settings" to contents.preferences.toSortedMap().mapValues { (_, values) ->
                values.toSortedMap().mapValues { (_, value) -> tagged(value) }
            },
            "words" to contents.words,
            "wordPairs" to contents.pairs.map { listOf(it.first, it.next, it.count, it.lastUsed) },
            "chineseWords" to contents.chineseWords.toSortedMap(),
        )
        return StringBuilder().also { json(root, it, 0) }.append('\n').toString()
    }

    /** A setting's value with its type, so an Int comes back an Int and a set a set. */
    private fun tagged(value: Any): Map<String, Any?> = when (value) {
        is Boolean -> mapOf("type" to "boolean", "value" to value)
        is Int -> mapOf("type" to "int", "value" to value)
        is Long -> mapOf("type" to "long", "value" to value)
        is Float -> mapOf("type" to "float", "value" to value)
        is String -> mapOf("type" to "string", "value" to value)
        is Set<*> -> mapOf("type" to "strings", "value" to value.map { it as String }.sorted())
        else -> throw IllegalArgumentException("Can't back up a ${value::class.simpleName}")
    }

    private fun json(value: Any?, out: StringBuilder, depth: Int) {
        when (value) {
            null -> out.append("null")
            is String -> out.append(Json.quote(value))
            is Boolean -> out.append(value)
            is Int, is Long -> out.append(value)
            is Float -> out.append(Json.number(value.toDouble()))
            is Double -> out.append(Json.number(value))
            is Map<*, *> -> {
                if (value.isEmpty()) { out.append("{}"); return }
                out.append("{\n")
                value.entries.forEachIndexed { index, (key, item) ->
                    indent(out, depth + 1).append(Json.quote(key as String)).append(": ")
                    json(item, out, depth + 1)
                    out.append(if (index < value.size - 1) ",\n" else "\n")
                }
                indent(out, depth).append('}')
            }
            is Iterable<*> -> {
                val items = value.toList()
                if (items.isEmpty()) { out.append("[]"); return }
                // Short lists of plain values, such as a word pair, stay on one line.
                if (items.none { it is Map<*, *> || it is Iterable<*> }) {
                    out.append('[')
                    items.forEachIndexed { index, item -> if (index > 0) out.append(", "); json(item, out, depth) }
                    out.append(']')
                    return
                }
                out.append("[\n")
                items.forEachIndexed { index, item ->
                    indent(out, depth + 1)
                    json(item, out, depth + 1)
                    out.append(if (index < items.size - 1) ",\n" else "\n")
                }
                indent(out, depth).append(']')
            }
            else -> throw IllegalArgumentException("Can't write a ${value::class.simpleName}")
        }
    }

    private fun indent(out: StringBuilder, depth: Int): StringBuilder {
        repeat(depth) { out.append("  ") }
        return out
    }

    /** JSON text for any value [Json.parse] gives, such as the engine's words section on its way back to the engine. */
    fun jsonText(value: Any?): String = StringBuilder().also { json(value, it, 0) }.toString()

    // Reading

    /** What [text] is: a backup, an old words or snippets file, or a [Problem] that says what's wrong. */
    fun read(text: String): Picked {
        val root = try {
            Json.parse(text) as? Map<*, *>
        } catch (error: Json.Error) {
            null
        } ?: throw Problem(if (text.contains("\"$FORMAT\"") || text.contains("\"$OLD_WORDS_FORMAT\"")) DAMAGED else NOT_A_BACKUP)
        return when (root["format"]) {
            FORMAT -> {
                val version = (root["version"] as? Double)?.toInt() ?: throw Problem(DAMAGED)
                if (version > VERSION) throw Problem(TOO_NEW)
                if (version < 1) throw Problem(DAMAGED)
                try {
                    @Suppress("UNCHECKED_CAST")
                    Picked.Backup(contents(root as Map<String, Any?>))
                } catch (error: RuntimeException) {
                    throw Problem(DAMAGED)
                } catch (error: Json.Error) {
                    throw Problem(DAMAGED)
                }
            }
            OLD_WORDS_FORMAT -> Picked.OldWords
            else -> if (root["snippets"] is List<*>) Picked.OldSnippets else throw Problem(NOT_A_BACKUP)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun contents(root: Map<String, Any?>): Contents {
        val settings = root["settings"] as? Map<String, Any?> ?: emptyMap()
        val preferences = settings.mapValues { (_, values) ->
            (values as Map<String, Any?>).mapNotNull { (key, item) -> untagged(item as Map<String, Any?>)?.let { key to it } }.toMap()
        }
        val pairs = (root["wordPairs"] as? List<Any?> ?: emptyList()).map { item ->
            val pair = item as List<Any?>
            WordPair(pair[0] as String, pair[1] as String, (pair[2] as Double).toInt(), (pair[3] as Double).toLong())
        }
        // Older backups have no Chinese words.
        val chinese = (root["chineseWords"] as? Map<String, Any?> ?: emptyMap()).mapValues { (_, text) -> text as String }
        return Contents(root["app"] as? String ?: "", root["build"] as? String ?: "", root["created"] as? String ?: "",
            preferences, root["words"] as? Map<String, Any?>, pairs, chinese)
    }

    /** A setting's value as its type, or null for a type this version doesn't know, which is skipped. */
    private fun untagged(item: Map<String, Any?>): Any? {
        val value = item["value"]
        return when (item["type"]) {
            "boolean" -> value as Boolean
            "int" -> (value as Double).toInt()
            "long" -> (value as Double).toLong()
            "float" -> (value as Double).toFloat()
            "string" -> value as String
            "strings" -> (value as List<*>).map { it as String }.toSet()
            else -> null
        }
    }

    // Restoring

    /**
     * What to write to each file to restore [backup] onto the phone's [phone] files: a setting takes the file's value, a list
     * merges. Files and keys this version doesn't back up are skipped, so a crafted file can't set Share swipes' ID.
     */
    fun restored(backup: Map<String, Map<String, Any>>, phone: Map<String, Map<String, Any?>>, debug: Boolean): Map<String, Map<String, Any>> =
        included(backup, debug).mapValues { (name, values) ->
            val store = STORES[name] as Store.Backed
            val here = phone[name].orEmpty()
            values.mapValues { (key, value) -> store.lists[key]?.let { merge(it, here[key], value) } ?: value }
        }

    fun merge(kind: Merge, phone: Any?, file: Any): Any {
        if (phone == null) return file
        return when (kind) {
            Merge.UNION -> if (phone is Set<*> && file is Set<*>) phone + file else file
            Merge.KEYED_EQUALS -> keyed(phone, file) { it.substringBefore('=') }
            Merge.KEYED_TAB -> keyed(phone, file) { it.substringBefore('\t') }
            Merge.KEYED_LAST_BAR -> keyed(phone, file) { it.substringBeforeLast('|') }
            Merge.RECENT_COMMA -> recent(phone, file, ",")
            Merge.RECENT_SPACE -> recent(phone, file, " ")
            Merge.SNIPPETS -> snippets(phone, file)
        }
    }

    private fun keyed(phone: Any, file: Any, key: (String) -> String): Any {
        if (phone !is Set<*> || file !is Set<*>) return file
        val fileKeys = file.map { key(it as String) }.toSet()
        return file + phone.filter { key(it as String) !in fileKeys }
    }

    private fun recent(phone: Any, file: Any, separator: String): Any {
        if (phone !is String || file !is String) return file
        val mine = phone.split(separator).filter { it.isNotEmpty() }
        val theirs = file.split(separator).filter { it.isNotEmpty() }
        return (theirs + mine).distinct().take(maxOf(mine.size, theirs.size)).joinToString(separator)
    }

    /** Snippets as Snippets.kt keeps them: `{"snippets":[{"shortcut":...,"text":...}]}`, sorted by shortcut. */
    private fun snippets(phone: Any, file: Any): Any {
        if (phone !is String || file !is String) return file
        val merged = sortedMapOf<String, String>()
        try {
            merged.putAll(snippetMap(phone))
        } catch (error: Exception) {
            return file
        }
        // A damaged list in the file keeps the phone's: restore runs after the words are in, so it mustn't throw here (#65).
        try {
            merged.putAll(snippetMap(file))
        } catch (error: Exception) {
            return phone
        }
        return jsonText(mapOf("snippets" to merged.map { (shortcut, text) -> linkedMapOf("shortcut" to shortcut, "text" to text) }))
    }

    private fun snippetMap(text: String): Map<String, String> {
        val list = (Json.parse(text) as Map<*, *>)["snippets"] as List<*>
        return list.mapNotNull { item ->
            val snippet = item as? Map<*, *> ?: return@mapNotNull null
            val shortcut = snippet["shortcut"] as? String ?: return@mapNotNull null
            val body = snippet["text"] as? String ?: return@mapNotNull null
            shortcut.lowercase() to body
        }.toMap()
    }
}
