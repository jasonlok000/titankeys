package com.titankeys.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TitanKeys Debug only (tools/debug.sh adds it; owner, 2026-11-17: the Orchestrator reads Debug's log to find the owner's "weird
 * interactions"). Copies the typing log, and the last Record a bug recording if there is one, into the app's external files
 * folder, where adb can pull them, and answers with the files and the log's time range. The owner's MARK lines ([OwnerMark]) say
 * where to look. Only adb can send to it, as it needs the DUMP permission that the shell holds, and it does nothing outside Debug.
 *
 *   adb shell am broadcast -n com.titankeys.debug/com.titankeys.keyboard.LogPull -a com.titankeys.debug.PULL_LOGS
 *     The result data is JSON: {"files": [...], "from": "...", "to": "..."}, local times. Then:
 *   adb pull /sdcard/Android/data/com.titankeys.debug/files/logs/
 *
 * The logs hold what was typed, so the copies stay out of git, and old copies are deleted with
 *   adb shell rm -r /sdcard/Android/data/com.titankeys.debug/files/logs
 */
class LogPull : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION || !RawLog.available(context)) return
        val pending = goAsync()
        Thread {
            pending.resultData = try {
                val folder = context.getExternalFilesDir("logs") ?: throw IOException("No external files folder")
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                val files = ArrayList<File>()
                val log = File(folder, "typing-log-$stamp.jsonl")
                log.outputStream().use { RawLog.export(context, it) }
                files += log
                BugRecording.attachment(context)?.let { bytes ->
                    val zip = bytes.size > 1 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
                    files += File(folder, "bug-recording-$stamp.${if (zip) "zip" else "jsonl"}").apply { writeBytes(bytes) }
                }
                val range = log.bufferedReader().useLines { RawLogRange.of(it) }
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                Log.d(TAG, "logs copied to ${folder.path}: ${files.joinToString { "${it.name} ${it.length()} bytes" }}")
                "{\"files\":[" + files.joinToString(",") { "\"${it.path}\"" } + "]," +
                    "\"from\":" + (range?.let { "\"${time.format(Date(it.first))}\"" } ?: "null") + "," +
                    "\"to\":" + (range?.let { "\"${time.format(Date(it.second))}\"" } ?: "null") + "}"
            } catch (error: IOException) {
                Log.w(TAG, "Could not copy the logs", error)
                "{\"failed\":\"${error.message.orEmpty().replace("\"", "'")}\"}"
            }
            pending.finish()
        }.start()
    }

    private companion object {
        const val ACTION = "com.titankeys.debug.PULL_LOGS"
        const val TAG = "TitanKeysDebug"
    }
}
