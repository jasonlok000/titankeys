package com.titankeys.keyboard

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.net.Uri
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The crash offer (#74, #97). APKs from GitHub get no Android vitals, so TitanKeys keeps the last crash's report on the phone,
 * and on the next open of the app asks once whether to send it. Yes opens the report form with it attached. It's never sent by
 * itself, and it's deleted once it's sent or dismissed; one offered and not sent goes at the open after.
 *
 * A crash's report is where it happened in the code: each exception's class and its stack, never its message, which could hold
 * text, so nothing typed goes in. Freezes, and crashes in native code, come from Android's own exit records.
 */
object CrashReport {
    /** In the no-backup folder. Reset literally everything and the backup both know "crash-" (FreshStart, BackupFile). */
    private const val FILE = "crash-report.txt"
    /** When the exit records were last read, so each freeze is offered once. */
    private const val CHECKED = "crash-checked"
    /** The report was offered and the form opened, so the next open lets it go. */
    private const val OFFERED = "crash-offered"
    /** The cache folder of the copy lent to the mail app. */
    private const val LENT = "crash-report"
    private val LENT_NAME = Regex("titankeys-(crash|freeze|report)-[0-9-]+\\.(txt|zip)")
    private const val FROZE = "Froze"
    /** Plenty for a stack, and the file stays small. */
    private const val MAX_CHARS = 24_000
    private const val MAX_TRACE_LINES = 120

    /**
     * Told of each crash and freeze, by "crash.count" or "freeze.count", for Sharing's daily totals (#96): [CrashKeeper] sets it
     * to the Counts store as the process starts. A hook, so that this file needs none of Sharing's code.
     */
    @Volatile
    var counted: ((Context, String) -> Unit)? = null

    /** Keeps each uncaught exception's report, then lets Android end the process as it would have. */
    fun install(context: Context) {
        val app = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                // Sharing's daily totals count it, with nothing about it (#96).
                counted?.invoke(app, "crash.count")
                write(app, header(app, "Crashed") + "Thread: ${thread.name}\n\n" + stack(error))
            } catch (_: Throwable) {
                // The report is a nicety: the crash itself must still reach Android.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The report waiting to be offered, if there is one, after adding a freeze from Android's exit records. Off the main thread. */
    fun pending(context: Context): String? {
        try {
            checkExitRecords(context)
        } catch (_: Exception) {
            // Exit records are a bonus. A crash report still shows.
        }
        if (File(context.noBackupFilesDir, OFFERED).isFile) {
            clear(context)
            return null
        }
        return held(context)
    }

    /** The kept report, for the form to attach, whether or not it was offered. */
    fun held(context: Context): String? {
        val file = File(context.noBackupFilesDir, FILE)
        return if (file.isFile) file.readText().take(MAX_CHARS) else null
    }

    /** The user chose to send it: the form has it now, and if it isn't sent, the next open lets it go. */
    fun offered(context: Context) {
        File(context.noBackupFilesDir, OFFERED).writeText("")
    }

    fun clear(context: Context) {
        File(context.noBackupFilesDir, FILE).delete()
        File(context.noBackupFilesDir, OFFERED).delete()
    }

    /** Whether [report] is a freeze's, which the offer names as one. */
    fun froze(report: String) = report.startsWith(FROZE)

    /**
     * A copy of [file], a report the form couldn't send (#97), to attach to a mail, in a cache folder of its own that
     * [CrashKeeper] lends read-only to the mail app. The copy stays only until the app next opens, as the mail app may still
     * be reading it.
     */
    fun lend(context: Context, file: File): Pair<Uri, String> {
        val folder = File(context.cacheDir, LENT).apply { deleteRecursively(); mkdirs() }
        val name = "titankeys-report-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date()) + "." + file.extension
        file.copyTo(File(folder, name))
        return Uri.Builder().scheme("content").authority("${context.packageName}.crashkeeper").appendPath(name).build() to name
    }

    /** The copy lent last time, which the mail app has had long enough to read. */
    fun forgetLent(context: Context) {
        File(context.cacheDir, LENT).deleteRecursively()
    }

    internal fun lent(context: Context, name: String): File? =
        File(File(context.cacheDir, LENT), name).takeIf { LENT_NAME.matches(name) && it.isFile }

    private fun write(context: Context, report: String) {
        File(context.noBackupFilesDir, FILE).writeText(report.take(MAX_CHARS))
    }

    private fun checkExitRecords(context: Context) {
        val checked = File(context.noBackupFilesDir, CHECKED)
        // The first time, only what happened since this version was installed.
        val since = checked.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
            ?: context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        checked.writeText(System.currentTimeMillis().toString())
        val records = context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName, 0, 10)
        val ended = records.filter { it.timestamp > since && it.processName == context.packageName &&
            (it.reason == ApplicationExitInfo.REASON_ANR || it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) }
        // Sharing's daily totals count each, with nothing about it (#96). A Java crash counted itself as it happened.
        for (record in ended) counted?.invoke(context, if (record.reason == ApplicationExitInfo.REASON_ANR) "freeze.count" else "crash.count")
        val last = ended.maxByOrNull { it.timestamp } ?: return
        // A Java crash after it has its own report already.
        if (File(context.noBackupFilesDir, FILE).let { it.isFile && it.lastModified() > last.timestamp }) return
        val kind = if (last.reason == ApplicationExitInfo.REASON_ANR) FROZE else "Crashed in native code"
        val trace = try {
            last.traceInputStream?.bufferedReader()?.useLines { mainThread(it) }.orEmpty()
        } catch (_: Exception) {
            ""
        }
        write(context, header(context, kind, last.timestamp) + "Android says: ${last.description.orEmpty()}\n\n$trace")
    }

    /** The main thread's part of a freeze's trace, where a freeze nearly always is, at most [MAX_TRACE_LINES] lines. */
    internal fun mainThread(lines: Sequence<String>): String {
        val out = ArrayList<String>()
        var inMain = false
        for (line in lines) {
            if (!inMain && line.startsWith("\"main\"")) inMain = true
            else if (inMain && line.isBlank()) break
            if (inMain) out += line
            if (out.size >= MAX_TRACE_LINES) break
        }
        return out.joinToString("\n")
    }

    private fun header(context: Context, kind: String, at: Long = System.currentTimeMillis()): String {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
        return "$kind: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(at))}\n" +
            "TitanKeys $version (${AppBuild.kind(context)})\n" +
            "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n"
    }

    /** Each exception's class and where it was thrown, with its causes, and never its message. */
    internal fun stack(error: Throwable): String {
        val out = StringBuilder()
        var current: Throwable? = error
        var first = true
        val seen = HashSet<Throwable>()
        while (current != null && seen.add(current)) {
            out.append(if (first) "" else "Caused by: ").append(current.javaClass.name).append('\n')
            for (frame in current.stackTrace.take(60)) out.append("\tat ").append(frame).append('\n')
            first = false
            current = current.cause
        }
        return out.toString()
    }
}
