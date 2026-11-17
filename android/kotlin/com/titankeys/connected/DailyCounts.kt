package com.titankeys.connected

import java.io.File
import java.time.LocalDate

/**
 * Sharing's daily totals (#96, docs/analytics.md): how many times a day each of TitanKeys' features acted and how its result
 * settled, kept on the phone in one small file and sent once a day, sealed. Only totals for a day, never single events, never a
 * word or a time of day. Nothing here needs Android, so tests/connected checks it on a computer.
 *
 * The day is the phone's local date. When it changes, the day before closes into the outbox, with the facts of that moment (the
 * versions and the phone model) and the counts ID of that day. At most [MAX_DAYS] days wait; older ones go. The counts ID is
 * random, never the contributor ID, and is replaced every [ID_DAYS] days. The IDs of the last [KEPT_IDS] periods stay, so that
 * "Delete what I've sent" can ask for all of them to be deleted.
 */
internal class DailyCounts(
    private val file: File,
    private val date: () -> String = { LocalDate.now().toString() },
    private val newId: () -> String,
    /** The versions and the phone model, for the day that closes. */
    private val facts: () -> Map<String, String> = { emptyMap() },
) {
    /** A closed day, ready to seal and send. */
    class Day(val day: String, val id: String, val json: String)

    private var day = ""
    private val counts = HashMap<String, Int>()
    private var guideFurthest = -1
    private var guideStep = ""
    private var guideFinished = false
    private val guideSkipped = LinkedHashSet<String>()
    private var guideChanged = false
    /** The settings set today, and what was last sent, so a day sends them only when they changed. */
    private var settings: List<String>? = null
    private var settingsSent: List<String>? = null
    private var id = ""
    private var idSince = ""
    private val pastIds = ArrayList<String>()
    private val outbox = ArrayList<Day>()
    private var loaded = false
    private var dirty = false

    @Synchronized
    fun add(key: String, by: Int = 1) {
        if (by <= 0 || !KEY.matches(key)) return
        roll()
        counts[key] = (counts[key] ?: 0) + by
        dirty = true
    }

    /** The guide showed slide [index], [step] by its ID; [finished] once it reached its end. Only the furthest counts. */
    @Synchronized
    fun guide(index: Int, step: String, finished: Boolean) {
        roll()
        if (index > guideFurthest) {
            guideFurthest = index
            guideStep = step
            guideChanged = true
        }
        if (finished && !guideFinished) {
            guideFinished = true
            guideChanged = true
        }
        dirty = true
    }

    /** The guide's slide [step] was skipped with Next before it was done: a hunt among them. */
    @Synchronized
    fun guideSkipped(step: String) {
        roll()
        if (guideSkipped.add(step)) guideChanged = true
        dirty = true
    }

    /** The settings that hold a value of the user's own, by key only, never the value. */
    @Synchronized
    fun settings(keys: List<String>) {
        roll()
        settings = keys.sorted()
        dirty = true
    }

    /** Today's totals as the payload would send them, for Settings > Sharing > What's shared. */
    @Synchronized
    fun today(): String {
        roll()
        return payload(day, id, facts())
    }

    /** The closed days waiting to go, oldest first. */
    @Synchronized
    fun due(): List<Day> {
        roll()
        return outbox.toList()
    }

    @Synchronized
    fun sent(day: Day) {
        outbox.removeAll { it.day == day.day && it.id == day.id }
        dirty = true
    }

    @Synchronized
    fun waitingDays(): Int {
        roll()
        return outbox.size
    }

    /** Turning sharing off: today's totals and every waiting day go. The IDs stay, for a deletion asked later. */
    @Synchronized
    fun clear() {
        load()
        outbox.clear()
        resetDay()
        settingsSent = null
        dirty = true
        save()
    }

    /** "Delete what I've sent": every counts ID this phone used in the last year, for the server to delete; then fresh ones. */
    @Synchronized
    fun forget(): List<String> {
        load()
        val ids = (pastIds + id).filter { it.isNotEmpty() }.distinct()
        outbox.clear()
        resetDay()
        settingsSent = null
        pastIds.clear()
        id = ""
        idSince = ""
        dirty = true
        save()
        return ids
    }

    @Synchronized
    fun save() {
        if (!dirty) return
        val text = buildString {
            append("{\"day\":").append(Json.quote(day))
            append(",\"counts\":").append(countsJson())
            append(",\"guideFurthest\":").append(guideFurthest)
            append(",\"guideStep\":").append(Json.quote(guideStep))
            append(",\"guideFinished\":").append(guideFinished)
            append(",\"guideSkipped\":").append(list(guideSkipped))
            append(",\"guideChanged\":").append(guideChanged)
            append(",\"settings\":").append(settings?.let(::list) ?: "null")
            append(",\"settingsSent\":").append(settingsSent?.let(::list) ?: "null")
            append(",\"id\":").append(Json.quote(id))
            append(",\"idSince\":").append(Json.quote(idSince))
            append(",\"pastIds\":").append(list(pastIds))
            append(",\"outbox\":[")
            outbox.forEachIndexed { index, waiting ->
                if (index > 0) append(',')
                append("{\"day\":").append(Json.quote(waiting.day)).append(",\"id\":").append(Json.quote(waiting.id))
                    .append(",\"json\":").append(Json.quote(waiting.json)).append('}')
            }
            append("]}")
        }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".new")
        temporary.writeText(text)
        if (!temporary.renameTo(file)) {
            file.delete()
            temporary.renameTo(file)
        }
        dirty = false
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val saved = try {
            if (file.isFile) Json.parse(file.readText()) as? Map<*, *> else null
        } catch (_: Exception) {
            null
        } ?: return
        day = saved["day"] as? String ?: ""
        (saved["counts"] as? Map<*, *>)?.forEach { (key, value) ->
            if (key is String && value is Double && KEY.matches(key)) counts[key] = value.toInt()
        }
        guideFurthest = (saved["guideFurthest"] as? Double)?.toInt() ?: -1
        guideStep = saved["guideStep"] as? String ?: ""
        guideFinished = saved["guideFinished"] == true
        (saved["guideSkipped"] as? List<*>)?.filterIsInstance<String>()?.let(guideSkipped::addAll)
        guideChanged = saved["guideChanged"] == true
        settings = (saved["settings"] as? List<*>)?.filterIsInstance<String>()
        settingsSent = (saved["settingsSent"] as? List<*>)?.filterIsInstance<String>()
        id = saved["id"] as? String ?: ""
        idSince = saved["idSince"] as? String ?: ""
        (saved["pastIds"] as? List<*>)?.filterIsInstance<String>()?.let(pastIds::addAll)
        (saved["outbox"] as? List<*>)?.forEach { waiting ->
            val entry = waiting as? Map<*, *> ?: return@forEach
            val waitingDay = entry["day"] as? String ?: return@forEach
            val waitingId = entry["id"] as? String ?: return@forEach
            val json = entry["json"] as? String ?: return@forEach
            outbox += Day(waitingDay, waitingId, json)
        }
    }

    /** Closes the day before once the date has changed, and replaces the counts ID once its 30 days are up. */
    private fun roll() {
        load()
        val now = date()
        if (day.isNotEmpty() && day != now) {
            if (hasNews()) {
                outbox += Day(day, id, payload(day, id, facts()))
                if (settings != null) settingsSent = settings
                while (outbox.size > MAX_DAYS) outbox.removeAt(0)
            }
            resetDay()
            dirty = true
        }
        if (day != now) {
            day = now
            dirty = true
        }
        if (id.isEmpty() || idSince.isEmpty() || daysBetween(idSince, now) >= ID_DAYS) {
            if (id.isNotEmpty()) {
                pastIds += id
                while (pastIds.size > KEPT_IDS) pastIds.removeAt(0)
            }
            id = newId()
            idSince = now
            dirty = true
        }
    }

    /** A day with nothing new sends nothing. */
    private fun hasNews() = counts.isNotEmpty() || guideChanged || settings != null && settings != settingsSent

    private fun resetDay() {
        counts.clear()
        guideFurthest = -1
        guideStep = ""
        guideFinished = false
        guideSkipped.clear()
        guideChanged = false
        settings = null
    }

    private fun payload(day: String, id: String, facts: Map<String, String>): String = buildString {
        append("{\"v\":").append(VERSION).append(",\"envelope\":").append(ENVELOPE)
        append(",\"id\":").append(Json.quote(id)).append(",\"day\":").append(Json.quote(day))
        for (fact in FACTS) append(",\"").append(fact).append("\":").append(Json.quote(facts[fact].orEmpty()))
        append(",\"counts\":").append(countsJson())
        if (guideChanged) {
            append(",\"guide\":{\"furthest\":").append(Json.quote(guideStep)).append(",\"finished\":").append(guideFinished)
                .append(",\"skipped\":").append(list(guideSkipped)).append('}')
        }
        val today = settings
        if (today != null && today != settingsSent) append(",\"settings\":{\"set\":").append(list(today)).append('}')
        append('}')
    }

    private fun countsJson() = counts.entries.sortedBy { it.key }
        .joinToString(",", "{", "}") { "${Json.quote(it.key)}:${it.value}" }

    private fun list(values: Collection<String>) = values.joinToString(",", "[", "]") { Json.quote(it) }

    companion object {
        /** The payload's format. */
        const val VERSION = 1
        /** The envelope the user agreed to (docs/analytics.md, §1). A new kind of data or purpose raises it, and asks again. */
        const val ENVELOPE = 1
        const val MAX_DAYS = 7
        const val ID_DAYS = 30
        /** A year of 30-day IDs. */
        const val KEPT_IDS = 13
        /** The facts that go with each day: versions and the phone model, nothing else. */
        val FACTS = listOf("app", "build", "android", "phone", "models")
        /** A count's key: letters, digits, dots, dashes, + and <, as "swipe.kept.5-7" or "speed.open.<50". Never a word typed. */
        val KEY = Regex("[a-z][a-zA-Z0-9]*(\\.[a-zA-Z0-9<+-]+){1,3}")

        fun daysBetween(from: String, to: String): Long = try {
            java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to))
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }
}
