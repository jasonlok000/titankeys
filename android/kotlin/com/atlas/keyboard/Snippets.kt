package com.atlas.keyboard

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * The user's snippets (issue 50): pairs of a shortcut and its text, kept on this phone only. They are never learned as words,
 * never part of a contribution, and never put in a password field. A backup file holds them as JSON:
 * `{"snippets":[{"shortcut":"addr","text":"..."}]}`, with each shortcut written without its start character.
 */
object Snippets {
    private const val PREFERENCES = "snippets"
    private const val LIST = "list"
    const val MAX_TEXT = 4000
    /** A file may have its shortcuts written with a start character. */
    private val START_MARKS = charArrayOf(';', '/', '\\', ',')

    /** Each snippet's text by its shortcut, in the order of the shortcuts. */
    fun load(context: Context): Map<String, String> =
        read(preferences(context).getString(LIST, null) ?: return emptyMap())

    /** Adds or changes a snippet. [previous] is its old shortcut when the shortcut changed. */
    fun put(context: Context, shortcut: String, text: String, previous: String? = null) {
        require(Expansions.validName(shortcut)) { "Shortcuts can only use letters, numbers, _ - and +, up to ${Expansions.MAX_NAME} of them." }
        require(text.isNotEmpty() && text.length <= MAX_TEXT) { "Type what the shortcut should turn into." }
        val snippets = LinkedHashMap(load(context))
        if (previous != null) snippets.remove(previous.lowercase())
        snippets[shortcut.lowercase()] = text
        save(context, snippets)
    }

    fun remove(context: Context, shortcut: String) {
        val snippets = LinkedHashMap(load(context))
        if (snippets.remove(shortcut.lowercase()) != null) save(context, snippets)
    }

    /** Writes every snippet to [uri]. */
    fun export(context: Context, uri: Uri) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open that file.")
        stream.bufferedWriter().use { it.write(write(load(context))) }
    }

    /** Adds the snippets in [uri]. A shortcut that is already here takes the file's text. Returns how many it read. */
    fun import(context: Context, uri: Uri): Int {
        val stream = context.contentResolver.openInputStream(uri) ?: error("Couldn't open that file.")
        val text = stream.bufferedReader().use { it.readText() }
        val found = try {
            read(text)
        } catch (error: org.json.JSONException) {
            throw IllegalArgumentException("That file has no snippets in it. Pick the right one.", error)
        }
        val snippets = LinkedHashMap(load(context))
        snippets.putAll(found)
        save(context, snippets)
        return found.size
    }

    private fun save(context: Context, snippets: Map<String, String>) {
        preferences(context).edit().putString(LIST, write(snippets)).apply()
    }

    private fun write(snippets: Map<String, String>): String {
        val list = JSONArray()
        for ((shortcut, text) in snippets.entries.sortedBy { it.key })
            list.put(JSONObject().put("shortcut", shortcut).put("text", text))
        return JSONObject().put("snippets", list).toString(2)
    }

    /** Snippets with a shortcut that is not valid, or with no text, are left out. */
    private fun read(json: String): Map<String, String> {
        val list = JSONObject(json).getJSONArray("snippets")
        val snippets = sortedMapOf<String, String>()
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val shortcut = item.optString("shortcut").trimStart(*START_MARKS).lowercase()
            val text = item.optString("text")
            if (Expansions.validName(shortcut) && text.isNotEmpty() && text.length <= MAX_TEXT) snippets[shortcut] = text
        }
        return LinkedHashMap(snippets)
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
