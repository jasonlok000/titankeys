package com.atlas.keyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import com.atlas.connected.Connected
import org.json.JSONObject
import java.io.File

/**
 * Record a bug (#64), in every build: while it is on, the keyboard writes what it sees and does to a file of its own, in
 * the typing log's format, so the same tools read it. It holds touches, swipe shapes, keys and the keyboard's decisions,
 * with the build and the settings. It never holds the text around the cursor, and it records nothing in a field that
 * does not allow learning (passwords, private fields and the like). It stops by itself after [MAX_MILLIS].
 * TitanKeys uploads nothing: the user shares the file through Android's share sheet.
 */
object BugRecording {
    const val MAX_MILLIS = 3 * 60_000L
    const val PAGE = "/settings/bug"
    private const val DIRECTORY = "bug-recording"
    private const val CAP_BYTES = 24L * 1024 * 1024
    private const val CHANNEL = "bug-recording"
    private const val NOTIFICATION = 0x4B00001
    private const val STOP = "com.titankeys.bug.STOP"

    private val main = Handler(Looper.getMainLooper())
    private var store: RawLogStore? = null
    @Volatile private var writer: RawLogWriter? = null
    @Volatile private var startedAt = 0L
    @Volatile private var startedWall = 0L
    private var app: Context? = null
    /** The automatic stop waits for the writer, so it runs off the main thread, which the keyboard draws and types on. */
    private val autoStop = Runnable { app?.let { context -> Thread({ stop(context) }, "BugRecordingStop").start() } }
    /** What the finished recording holds, worked out once when it stops, so Settings' every refresh costs nothing. */
    @Volatile private var summary: Summary? = null

    val recording get() = writer != null

    /** How long it has run, in milliseconds, or 0 when it is off. */
    fun elapsed() = if (recording) SystemClock.uptimeMillis() - startedAt else 0L

    @Synchronized
    fun start(context: Context) {
        stop(context)
        val app = context.applicationContext.also { this.app = it }
        val store = store(app)
        store.deleteAll()
        summary = null
        startedAt = SystemClock.uptimeMillis()
        startedWall = System.currentTimeMillis()
        writer = RawLogWriter(store) { SystemClock.uptimeMillis() }.also { it.add(RawLogLines.gap("start", 0, startedAt)) }
        main.postDelayed(autoStop, MAX_MILLIS)
        showNotification(app)
    }

    @Synchronized
    fun stop(context: Context) {
        main.removeCallbacks(autoStop)
        val stopping = writer ?: return
        writer = null
        stopping.add(RawLogLines.gap("stop", 0, SystemClock.uptimeMillis()))
        stopping.close()
        stopping.join(WRITE_WAIT_MILLIS)
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION)
        summary = read(context)
    }

    /**
     * A field starts while recording: returns where its lines go, or null if the field must not be recorded. Only fields
     * that allow learning count, so passwords, addresses and private fields are left out, with a gap line in their place.
     */
    internal fun fieldStart(context: Context, info: EditorInfo, restarting: Boolean): RawLogWriter? {
        val writer = writer ?: return null
        val now = SystemClock.uptimeMillis()
        if (!Field.of(info.inputType, info.imeOptions).learning) {
            writer.add(RawLogLines.gap("field", 0, now))
            return null
        }
        writer.add(RawLogLines.fieldStart(info.inputType, info.imeOptions, false, restarting,
            info.initialSelStart, info.initialSelEnd, null, now))
        return writer
    }

    fun size(context: Context): Long = if (recording) 0L else store(context).size()

    fun delete(context: Context) {
        stop(context)
        store(context).deleteAll()
        summary = null
        File(context.cacheDir, DIRECTORY).deleteRecursively()
    }

    /** The finished recording as one file, ready to share. Only [share] makes it, so a file already lent out stays as it is. */
    private fun file(context: Context): File? {
        if (recording || store(context).size() == 0L) return null
        val folder = File(context.cacheDir, DIRECTORY).apply { deleteRecursively(); mkdirs() }
        val name = "titankeys-bug-${java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ROOT).format(startedWall)}.jsonl"
        val file = File(folder, name)
        file.outputStream().use { store(context).export(it) }
        return file
    }

    /** What the recording holds, for the preview before sharing. */
    data class Summary(val millis: Long, val fields: Int, val touches: Int, val swipes: Int, val keys: Int, val words: List<String>)

    /** The summary from the stop, or, after TitanKeys restarted, read once from the recording. */
    fun summary(context: Context): Summary? {
        if (recording) return null
        summary?.let { return it }
        return read(context).also { summary = it }
    }

    /** Reads the recording straight from its store, line by line, without making the shared file. */
    @Synchronized
    private fun read(context: Context): Summary? {
        val store = store(context)
        if (recording || store.size() == 0L) return null
        var first = Long.MAX_VALUE
        var last = 0L
        var fields = 0
        var touches = 0
        var swipes = 0
        var keys = 0
        val words = ArrayList<String>()
        val lines = LineReader read@{ text ->
            val line = try { JSONObject(text) } catch (error: Exception) { return@read }
            val time = line.optLong("t", 0L)
            if (time > 0) {
                first = minOf(first, time)
                last = maxOf(last, time)
            }
            when (line.optString("k")) {
                "start" -> fields++
                "m" -> if (line.optInt("a", -1) == 0) touches++
                "key" -> if (line.optBoolean("d")) keys++
                "out" -> if (line.optString("type") == "swipe") swipes++
                "text" -> line.optString("ins").trim().takeIf { it.isNotEmpty() }?.let { if (words.size < MAX_WORDS) words += it }
            }
        }
        lines.use { store.export(it) }
        return Summary(if (last > first) last - first else 0L, fields, touches, swipes, keys, words)
    }

    /** Opens Android's share sheet with the file. A read-only provider lends it to the app the user picks. */
    fun share(context: Context, subject: String, text: String) {
        val file = file(context) ?: throw IllegalStateException("Nothing recorded")
        val uri = BugRecordingProvider.uri(context, file.name)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    internal fun folder(context: Context) = File(context.cacheDir, DIRECTORY)

    @Synchronized
    private fun store(context: Context): RawLogStore = store ?: context.applicationContext.let { app ->
        RawLogStore(File(app.filesDir, DIRECTORY), CAP_BYTES, CAP_BYTES) { RawLog.header(app) }.also { store = it }
    }

    /** A quiet notification with a timer and Stop, so a recording never runs unseen. */
    private fun showNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, BugText.CHANNEL, NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        })
        val stop = PendingIntent.getBroadcast(context, 0, Intent(context, BugStopReceiver::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(context, NOTIFICATION, Connected.pageIntent(context, PAGE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(BugText.RECORDING)
            .setContentText(BugText.STOPS_ITSELF)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setWhen(startedWall)
            .setTimeoutAfter(MAX_MILLIS + 5_000)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, BugText.STOP, stop).build())
            .build()
        try {
            manager.notify(NOTIFICATION, notification)
        } catch (error: SecurityException) {
            // Without the notification permission the Settings page still shows the timer and Stop.
        }
    }

    /** Splits what's written to it into lines for [line], so the store can be read without a copy on disk. */
    private class LineReader(private val line: (String) -> Unit) : java.io.OutputStream() {
        private val pending = java.io.ByteArrayOutputStream()

        override fun write(byte: Int) {
            if (byte == NEWLINE) flushLine() else pending.write(byte)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            var start = offset
            for (index in offset until offset + length) {
                if (bytes[index].toInt() != NEWLINE) continue
                pending.write(bytes, start, index - start)
                flushLine()
                start = index + 1
            }
            pending.write(bytes, start, offset + length - start)
        }

        override fun close() {
            if (pending.size() > 0) flushLine()
        }

        private fun flushLine() {
            line(pending.toString(Charsets.UTF_8.name()))
            pending.reset()
        }
    }

    private const val MAX_WORDS = 60
    private const val NEWLINE = 10
    private const val WRITE_WAIT_MILLIS = 2_000L
}

/** The notification's Stop button. Only this app can send to it. */
class BugStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Thread {
            try {
                BugRecording.stop(context)
                Haptics(context).play(Haptic.bugRecording(started = false))
            } finally {
                result.finish()
            }
        }.start()
    }
}

/** The notification's words. The Settings page's words are in lib/bug-text.ts. */
internal object BugText {
    const val CHANNEL = "Bug recording"
    const val RECORDING = "Recording a bug"
    const val STOPS_ITSELF = "Do the thing, then press Stop. It stops by itself after 3 minutes."
    const val STOP = "Stop"
}
