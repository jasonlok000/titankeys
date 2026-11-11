package com.atlas.connected

import java.time.Instant
import java.util.Base64

/**
 * The announcement feed: a signed list of short messages from the TitanKeys project. The file on the website is an
 * envelope that holds the feed's exact text and an Ed25519 signature of that text. A feed whose signature fails, or that
 * is older than one already seen, is thrown away whole. Nothing here needs Android, so the JVM tests run it as it is.
 */
internal object Feed {
    class Invalid(message: String) : Exception(message)

    /** What a touch on the announcement does: open a web page, or open a page in the app. */
    data class Action(val label: String, val url: String?, val page: String?)

    data class Entry(
        val id: String,
        val title: String,
        val text: String,
        val action: Action?,
        /** Shown from this time and until that time, in milliseconds. Null means no limit. */
        val from: Long?,
        val until: Long?,
        /** The app versions it is for, both ends included. Null means no limit. */
        val minVersion: String?,
        val maxVersion: String?,
        /** The builds it is for: "github", "play" or "debug". */
        val builds: Set<String>,
        /** It also shows as a notification. When false, it shows only on the News page. */
        val notify: Boolean,
    )

    data class Contents(val issued: Long, val entries: List<Entry>)

    const val FORMAT = "titankeys-feed-1"
    const val MAX_ENVELOPE_BYTES = 256 * 1024
    private const val MAX_ENTRIES = 50
    private const val MAX_TITLE = 80
    private const val MAX_TEXT = 600
    private const val MAX_LABEL = 24
    private val ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    private val PAGE = Regex("/[a-z0-9/_-]{0,80}")
    private val VERSION = Regex("[0-9]{1,4}(\\.[0-9]{1,4}){0,3}")
    val BUILDS = setOf("github", "play", "debug")

    /** Checks the envelope's signature with [publicKey] (32 bytes), then reads the feed inside it. */
    fun open(envelope: String, publicKey: ByteArray): Contents {
        if (envelope.length > MAX_ENVELOPE_BYTES) throw Invalid("The feed is too large")
        val outer = try { Json.parse(envelope) as? Map<String, Any?> } catch (error: Json.Error) { null }
            ?: throw Invalid("The feed is not a JSON object")
        if (outer["format"] != FORMAT) throw Invalid("Unknown feed format")
        val text = outer["feed"] as? String ?: throw Invalid("The feed text is missing")
        val signature = try { Base64.getDecoder().decode(outer["signature"] as? String ?: "") } catch (error: IllegalArgumentException) { null }
        if (signature == null || !Ed25519.verify(publicKey, text.toByteArray(Charsets.UTF_8), signature))
            throw Invalid("The feed's signature is not valid")
        return read(text)
    }

    /** Reads a feed's text. The signing tool calls this too, so a feed that the app would refuse is never signed. */
    fun read(text: String): Contents = try {
        @Suppress("UNCHECKED_CAST")
        val feed = Json.parse(text) as? Map<String, Any?> ?: throw Invalid("The feed is not a JSON object")
        if (feed["version"] != 1.0) throw Invalid("Unknown feed version")
        val issued = time(feed.text("issued"))
        val list = feed.list("entries")
        if (list.size > MAX_ENTRIES) throw Invalid("Too many entries")
        val entries = list.map { entry(it as? Map<String, Any?> ?: throw Invalid("An entry is not an object")) }
        if (entries.map { it.id }.toSet().size != entries.size) throw Invalid("Two entries have the same id")
        Contents(issued, entries)
    } catch (error: Json.Error) {
        throw Invalid(error.message ?: "The feed is not valid")
    }

    @Suppress("UNCHECKED_CAST")
    private fun entry(map: Map<String, Any?>): Entry {
        val id = map.text("id")
        if (!ID.matches(id)) throw Invalid("Bad id: $id")
        val title = limited(map.text("title"), MAX_TITLE, "title of $id")
        val text = limited(map.text("text"), MAX_TEXT, "text of $id")
        val action = map.objectOrNull("action")?.let { action ->
            val label = limited(action.text("label"), MAX_LABEL, "action label of $id")
            val url = action.textOrNull("url")
            val page = action.textOrNull("page")
            if ((url == null) == (page == null)) throw Invalid("The action of $id needs a url or a page, not both")
            if (url != null && (!url.startsWith("https://") || url.length > 500 || url.any { it <= ' ' }))
                throw Invalid("The action of $id must be an https link")
            if (page != null && !PAGE.matches(page)) throw Invalid("Bad page for $id")
            Action(label, url, page)
        }
        val minVersion = map.textOrNull("minVersion")?.also { if (!VERSION.matches(it)) throw Invalid("Bad minVersion for $id") }
        val maxVersion = map.textOrNull("maxVersion")?.also { if (!VERSION.matches(it)) throw Invalid("Bad maxVersion for $id") }
        val builds = map.list("builds").map { it as? String ?: throw Invalid("Bad builds for $id") }.toSet()
        if (builds.isEmpty() || !BUILDS.containsAll(builds)) throw Invalid("Builds of $id must be some of $BUILDS")
        val notify = map["notify"]?.let { it as? Boolean ?: throw Invalid("notify of $id must be true or false") } ?: true
        return Entry(id, title, text, action, map.textOrNull("from")?.let(::time), map.textOrNull("until")?.let(::time),
            minVersion, maxVersion, builds, notify)
    }

    private fun limited(value: String, max: Int, what: String): String {
        if (value.isBlank() || value.length > max) throw Invalid("The $what must have 1 to $max characters")
        return value
    }

    private fun time(value: String): Long = try {
        Instant.parse(value).toEpochMilli()
    } catch (error: Exception) {
        throw Invalid("Bad time: $value")
    }

    /** Whether an entry is for this app, this build and this moment. */
    fun applies(entry: Entry, now: Long, version: String, build: String): Boolean =
        build in entry.builds
            && (entry.from == null || now >= entry.from)
            && (entry.until == null || now < entry.until)
            && (entry.minVersion == null || compareVersions(version, entry.minVersion) >= 0)
            && (entry.maxVersion == null || compareVersions(version, entry.maxVersion) <= 0)

    /** Compares versions such as "2.1.0" number by number. Anything after the numbers, such as "-beta", is ignored. */
    fun compareVersions(a: String, b: String): Int {
        fun parts(version: String) = version.takeWhile { it.isDigit() || it == '.' }.split('.').map { it.toIntOrNull() ?: 0 }
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val difference = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }
}
