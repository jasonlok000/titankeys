package com.atlas.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/**
 * The Haptics lab in TitanKeys Debug's Settings. Its screen ([HapticsLabActivity]) shows the haptics the keyboard really plays
 * as you type, lets you try other patterns for each family, and keeps your notes in a file that adb can pull. Only a package
 * that ends in ".debug" has it.
 */
class HapticsLab(private val context: Context) {
    // Settings has no keyboard to borrow from, so the lab has its own Haptics, for the editor's Play.
    private val haptics = Haptics(context)
    private val main = Handler(Looper.getMainLooper())
    private val write = Runnable { writeNotes() }

    /** Settings' names for the haptics, with their groups, for the notes file. */
    var names: List<Named> = emptyList()

    data class Named(val haptic: Haptic, val label: String, val group: String, val about: String)

    /** The notes: one on the whole, one per family and one per haptic, by id. Read from the file the first time. */
    private val notes by lazy { readNotes() }

    private class Notes(var general: String, val families: MutableMap<String, String>, val haptics: MutableMap<String, String>)

    /** What [family] plays now. */
    fun pattern(family: Family) = Haptics.pattern(context, family)

    /** Tries [pattern] for [family], or puts the rulebook's back with null. The next real action plays it. */
    fun tryPattern(family: Family, pattern: List<Long>?) {
        Haptics.tryPattern(context, family, pattern)
        later()
    }

    /** Plays what [family] plays now, alone, with no switch or gate. */
    fun preview(family: Family) = haptics.preview(pattern(family))

    fun stop() {
        if (main.hasCallbacks(write)) {
            main.removeCallbacks(write)
            writeNotes()
        }
    }

    /** Opens the lab's screen. [labels] are Settings' names for the haptics, as JSON. */
    fun showGraph(labels: String) = context.startActivity(HapticsLabActivity.intent(context, labels))

    /** Each family's pattern in use now, and each haptic's family and what it plays. */
    fun state(): JSONObject = JSONObject().put("families", families()).put("haptics", haptics())
        .put("experiments", JSONObject().put(DOTS_SPEED_UP, Haptics.dotsSpeedUp(context)))

    /** Turns one of the lab's experiments on or off, by its id. */
    fun experiment(id: String, on: Boolean) = when (id) {
        DOTS_SPEED_UP -> Haptics.tryDotsSpeedUp(context, on)
        else -> throw IllegalArgumentException("No experiment called $id")
    }

    private fun families() = JSONObject().apply {
        for (family in Family.entries) {
            val pattern = pattern(family)
            put(family.id, JSONObject().put("name", family.label).put("pattern", JSONArray(pattern))
                .put("plays", HapticPalette.describe(pattern)).put("tried", pattern != family.pattern))
        }
    }

    private fun haptics() = JSONObject().apply {
        for (haptic in Haptic.entries) {
            val pattern = pattern(haptic.family)
            put(haptic.id, JSONObject().put("family", haptic.family.label).put("pattern", JSONArray(pattern))
                .put("plays", HapticPalette.describe(pattern)))
        }
    }

    fun general() = notes.general
    fun note(family: Family) = notes.families[family.id].orEmpty()
    fun note(haptic: Haptic) = notes.haptics[haptic.id].orEmpty()

    fun general(text: String) {
        notes.general = text
        later()
    }

    fun note(family: Family, text: String) {
        notes.families[family.id] = text
        later()
    }

    fun note(haptic: Haptic, text: String) {
        notes.haptics[haptic.id] = text
        later()
    }

    /** Writes the file a moment after the last change, so typing doesn't write on every letter. [stop] writes at once. */
    private fun later() {
        main.removeCallbacks(write)
        main.postDelayed(write, WRITE_DELAY_MILLIS)
    }

    private fun readNotes(): Notes {
        val file = runCatching { JSONObject(file().takeIf { it.exists() }?.readText() ?: "{}") }.getOrElse { JSONObject() }
        val families = HashMap<String, String>()
        file.optJSONObject("families")?.let { saved ->
            for (id in saved.keys()) saved.optJSONObject(id)?.optString("comment")?.takeIf { it.isNotEmpty() }?.let { families[id] = it }
        }
        val haptics = HashMap<String, String>()
        val list = file.optJSONArray("haptics") ?: JSONArray()
        for (index in 0 until list.length()) {
            val entry = list.optJSONObject(index) ?: continue
            val comment = entry.optString("comment")
            if (comment.isNotEmpty()) haptics[entry.optString("id")] = comment
        }
        return Notes(file.optString("general"), families, haptics)
    }

    /**
     * The file adb pulls: the notes, each family's pattern in use and whether it's tried, and each haptic's family and pattern.
     * It goes to a new file first, so a write that fails never leaves half a file.
     */
    private fun writeNotes() {
        val families = families()
        for (family in Family.entries) families.getJSONObject(family.id).put("comment", note(family))
        val haptics = haptics()
        val list = JSONArray()
        val named = names.associateBy { it.haptic }
        for (haptic in Haptic.entries) {
            val state = haptics.getJSONObject(haptic.id)
            list.put(JSONObject().put("id", haptic.id).put("name", named[haptic]?.label ?: haptic.id).put("group", named[haptic]?.group ?: "")
                .put("family", state.getString("family")).put("pattern", state.getJSONArray("pattern")).put("plays", state.getString("plays"))
                .put("comment", note(haptic)))
        }
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
        val text = JSONObject().put("savedAt", Instant.now().toString()).put("version", version).put("general", notes.general)
            .put("families", families).put("haptics", list).toString(2)
        val file = file()
        val written = File(file.parentFile, "${file.name}.new")
        written.writeText(text)
        check(written.renameTo(file)) { "Couldn't save the notes." }
    }

    /** /sdcard/Android/data/<package>/files/haptics-lab/comments.json, which adb can pull. */
    private fun file() = File(requireNotNull(context.getExternalFilesDir("haptics-lab")) { "No storage for the notes." }, "comments.json")

    companion object {
        fun available(context: Context) = context.packageName.endsWith(".debug")
        /** The id of the experiment "Dots speed up when long", as the lab's page names it. */
        const val DOTS_SPEED_UP = "dotsSpeedUp"

        private const val WRITE_DELAY_MILLIS = 800L
    }
}
