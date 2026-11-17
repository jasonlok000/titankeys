package com.titankeys.connected

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.security.SecureRandom

/**
 * Sharing's daily totals on the phone (#96, docs/analytics.md): the keyboard, the guide and the crash report count here, only
 * while Sharing is on, and only in fields that allow learning (the keyboard checks that before it counts). Once a day the job
 * seals each closed day with the contributions key and sends it, on any network. TitanKeys Debug counts, so that Settings >
 * Sharing > What's shared can show it, but never sends.
 */
object Counts {
    private const val TAG = "TitanKeysCounts"
    private const val FILE = "sharing/counts.json"
    /** Crashes and freezes, a line each, written as they happen, and added to the day the next time TitanKeys runs. */
    private const val CRASHES = "sharing/crashes.txt"
    private const val COUNTS_TO_DELETE = "counts_to_delete"
    /** In "connected": the envelope the user agreed to when they turned Sharing on, and the day (docs/analytics.md, §1). */
    internal const val AGREED_ENVELOPE = "sharing_envelope"
    internal const val AGREED_ON = "sharing_agreed_on"

    /** Sharing is on. A plain field, read on every count, so a count costs a check when it's off. */
    @Volatile
    var on = false
        internal set

    private var store: DailyCounts? = null

    internal fun store(context: Context): DailyCounts = synchronized(this) {
        store ?: run {
            val app = context.applicationContext ?: context
            DailyCounts(File(app.noBackupFilesDir, FILE), newId = ::newId, facts = { facts(app) }).also { store = it }
        }
    }

    /** The keyboard or the app started: counting follows the switch. */
    fun start(context: Context) {
        on = Contributions.enabled(context)
        if (on) store(context)
    }

    /** One more of [key], today. Does nothing while Sharing is off or before the keyboard started. */
    fun add(key: String) {
        if (!on) return
        (store ?: Connected.context?.let(::store))?.add(key)
    }

    /** The guide showed slide [index] ([step]); [finished] at its end. */
    fun guide(context: Context, index: Int, step: String, finished: Boolean) {
        if (!active(context)) return
        store(context).guide(index, step, finished)
        save(context)
    }

    fun guideSkipped(context: Context, step: String) {
        if (!active(context)) return
        store(context).guideSkipped(step)
        save(context)
    }

    /** A crash or a freeze, from [com.titankeys.keyboard.CrashReport], as a line: the process may end right after. */
    fun crashed(context: Context, key: String) {
        try {
            if (!active(context)) return
            File(context.noBackupFilesDir, CRASHES).apply { parentFile?.mkdirs() }.appendText("$key\n")
        } catch (_: Throwable) {
            // A count is a nicety; the crash must still reach Android.
        }
    }

    /** Whether Sharing is on, for the app's own screens, which may run before the keyboard has started. */
    private fun active(context: Context): Boolean {
        if (!on) on = Contributions.enabled(context)
        return on
    }

    /** Writes today's totals to the phone, off the main thread. */
    fun save(context: Context) {
        if (!on) return
        val counts = store(context)
        Thread { runCatching { counts.save() } }.start()
    }

    /** What would go for today, as JSON, for What's shared. Empty totals while Sharing is off. */
    fun today(context: Context): String {
        val counts = store(context)
        if (on) {
            takeCrashes(context, counts)
            counts.settings(SettingsSet.keys(context))
        }
        return counts.today()
    }

    fun waitingDays(context: Context) = store(context).waitingDays()

    /** Turning Sharing off: what waits on the phone goes. */
    fun clear(context: Context) {
        on = false
        store(context).clear()
        File(context.noBackupFilesDir, CRASHES).delete()
    }

    /** "Delete what I've sent": the IDs the server should delete, kept until the deletion has gone. */
    internal fun forget(context: Context) {
        val ids = store(context).forget()
        val preferences = Connected.preferences(context)
        val waiting = preferences.getString(COUNTS_TO_DELETE, null)?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
        val all = (waiting + ids).distinct().takeLast(2 * DailyCounts.KEPT_IDS)
        if (all.isNotEmpty() && Connected.CONTRIBUTIONS_URL.isNotEmpty())
            preferences.edit().putString(COUNTS_TO_DELETE, all.joinToString(",")).apply()
    }

    internal fun toDelete(context: Context): List<String> =
        Connected.preferences(context).getString(COUNTS_TO_DELETE, null)?.split(',')?.filter { ID.matches(it) }.orEmpty()

    internal fun deleted(context: Context) {
        Connected.preferences(context).edit().remove(COUNTS_TO_DELETE).apply()
    }

    /** Seals and sends each closed day. Runs only from the daily job; never from TitanKeys Debug. */
    fun upload(context: Context): Boolean {
        if (!Contributions.enabled(context) || Connected.CONTRIBUTIONS_URL.isEmpty()) return true
        on = true
        // Totals go only under the envelope the user agreed to: a wider one waits for a new yes.
        if (Connected.preferences(context).getInt(AGREED_ENVELOPE, 0) < DailyCounts.ENVELOPE) return true
        val counts = store(context)
        takeCrashes(context, counts)
        counts.settings(SettingsSet.keys(context))
        val due = counts.due()
        counts.save()
        if (Connected.build(context) == "debug") return true
        for (day in due) {
            val sealed = Seal.seal(day.json.toByteArray(Charsets.UTF_8), Connected.key(Connected.CONTRIBUTION_KEY))
            val connection = Connected.open("${Connected.CONTRIBUTIONS_URL}/v1/counts", "POST")
            try {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.setRequestProperty("X-Counts", Contributions.folder(day.id))
                connection.setRequestProperty("X-Day", day.day)
                connection.setFixedLengthStreamingMode(sealed.size)
                connection.outputStream.use { it.write(sealed) }
                if (!Connected.ok(connection)) {
                    Log.w(TAG, "The server answered ${connection.responseCode}")
                    return false
                }
            } catch (error: Exception) {
                Log.w(TAG, "Upload failed: ${error.javaClass.simpleName}")
                return false
            } finally {
                connection.disconnect()
            }
            // Sharing may have been turned off meanwhile, which already cleared the day.
            if (!Contributions.enabled(context)) return true
            counts.sent(day)
            counts.save()
        }
        return true
    }

    /** How long the keyboard took to show its keys for a field, in bands. */
    fun openBand(millis: Long): String = band(millis, OPEN_BANDS)

    /** How long a swipe took from the finger lifting to its word showing, in bands. */
    fun swipeBand(millis: Long): String = band(millis, SWIPE_BANDS)

    private fun band(millis: Long, edges: LongArray): String {
        var below = 0L
        for (edge in edges) {
            if (millis < edge) return if (below == 0L) "<$edge" else "$below-$edge"
            below = edge
        }
        return "$below+"
    }

    private val OPEN_BANDS = longArrayOf(50, 100, 200, 500)
    private val SWIPE_BANDS = longArrayOf(30, 60, 120, 250)
    private val ID = Regex("[0-9a-f]{32}")

    private fun takeCrashes(context: Context, counts: DailyCounts) {
        val file = File(context.noBackupFilesDir, CRASHES)
        if (!file.isFile) return
        val lines = try {
            file.readLines()
        } catch (_: Exception) {
            emptyList()
        }
        file.delete()
        for (line in lines) if (line in CRASH_KEYS) counts.add(line)
    }

    internal val CRASH_KEYS = setOf("crash.count", "freeze.count")

    private fun newId(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private fun facts(context: Context): Map<String, String> = mapOf(
        "app" to runCatching { Connected.version(context) }.getOrDefault(""),
        "build" to Connected.build(context),
        "android" to Build.VERSION.RELEASE.orEmpty(),
        "phone" to Build.MODEL.orEmpty(),
        "models" to Contributions.models(context),
    )
}
