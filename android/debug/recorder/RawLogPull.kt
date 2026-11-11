package com.atlas.keyboard

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
 * TitanKeys Debug only (tools/debug.sh adds it): copies the raw typing log where adb can pull it, so a test does not need the
 * owner to export it by hand. The copy is the same as Settings > Your data > Typing log > Export: every part, oldest first.
 * Only adb can send to it, as it needs the DUMP permission that the shell holds, and it does nothing outside the Debug build.
 *
 *   adb shell am broadcast -n com.titankeys.debug/com.atlas.keyboard.RawLogPull -a com.titankeys.debug.EXPORT_RAWLOG
 *     The result data names the file, in Android/data/com.titankeys.debug/files/rawlog/. Then:
 *   adb pull /sdcard/Android/data/com.titankeys.debug/files/rawlog/
 *
 * The log holds what was typed, so the copy stays out of git, and old copies are deleted with
 *   adb shell rm -r /sdcard/Android/data/com.titankeys.debug/files/rawlog
 */
class RawLogPull : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION || !context.packageName.endsWith(".debug")) return
        val pending = goAsync()
        Thread {
            pending.resultData = try {
                val folder = context.getExternalFilesDir("rawlog") ?: throw IOException("No external files folder")
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                val file = File(folder, "raw-log-$stamp.jsonl")
                file.outputStream().use { RawLog.export(context, it) }
                Log.d(TAG, "raw log copied to ${file.path}, ${file.length()} bytes")
                file.path
            } catch (error: IOException) {
                Log.w(TAG, "Could not copy the raw log", error)
                "failed: ${error.message}"
            }
            pending.finish()
        }.start()
    }

    private companion object {
        const val ACTION = "com.titankeys.debug.EXPORT_RAWLOG"
        const val TAG = "TitanKeysDebug"
    }
}
