package com.titankeys.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Debug
import android.util.Log
import com.titankeys.rime.RimeAssets
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TitanKeys Debug only (tools/debug.sh adds it): the memory stress check (#85). It runs a full collection, so what
 * `dumpsys meminfo` counts next is only what something still holds, and lists the guides and playgrounds still in memory. A
 * destroyed one in the list is a leak, and the line names the guide's own statics that hold it, if any do. Only adb can send to
 * it, as it needs the DUMP permission that the shell holds.
 *
 *   adb shell am broadcast -n com.titankeys.debug/com.titankeys.keyboard.LeakCheck
 *   adb shell dumpsys meminfo com.titankeys.debug
 *
 * It also says what Rime's data copy holds (docs/studies/3.0-storage.md): its size and files, and its tables and grammar
 * models by name, as only the input methods that are on are copied. run-as can't read the app's files on TitanKeys Debug.
 *
 * With `--ez dump true` it also writes a heap dump, which needs no debuggable build: the app writes it itself. The result names
 * the file, in Android/data/com.titankeys.debug/files/leaks/. Then, on the computer:
 *
 *   adb pull /sdcard/Android/data/com.titankeys.debug/files/leaks/
 *   python3 tools/leak-paths.py leaks/leak-<time>.hprof
 *
 * which prints what holds each destroyed guide and playground, by class and field names only. The dump holds everything in the
 * app's memory, typed text among it, so it never goes into git, an issue or a report. Delete it on both sides when done:
 *
 *   adb shell rm -r /sdcard/Android/data/com.titankeys.debug/files/leaks
 */
class LeakCheck : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.packageName.endsWith(".debug")) return
        collect()
        val guides = GuideActivity.inMemory()
        val report = (if (guides.isEmpty()) "no guide or playground in memory" else guides.joinToString("; ")) +
            // What the input methods hold: one keyboard and one word list at most (the 18-switch OutOfMemoryError, 2026-11-17).
            "; " + Languages.report() + "; " + rimeData(context)
        Log.d(TAG, "leak check: $report")
        if (!intent.getBooleanExtra(DUMP, false)) {
            resultData = report
            return
        }
        val pending = goAsync()
        Thread {
            pending.resultData = try {
                val folder = context.getExternalFilesDir("leaks") ?: throw IOException("No external files folder")
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                val file = File(folder, "leak-$stamp.hprof")
                collect()
                Debug.dumpHprofData(file.path)
                Log.d(TAG, "leak check: heap dump at ${file.path}, ${file.length()} bytes")
                "$report; heap dump: ${file.path}"
            } catch (error: IOException) {
                Log.w(TAG, "Could not write the heap dump", error)
                "$report; heap dump failed: ${error.message}"
            }
            pending.finish()
        }.start()
    }

    /** What Rime's data copy holds: its size and files, and its tables and grammar models by name. */
    private fun rimeData(context: Context): String {
        val shared = File(RimeAssets.dataDir(context), "shared")
        if (!shared.isDirectory) return "rime data: none copied"
        val files = shared.walkTopDown().filter { it.isFile }.toList()
        val big = files.map { it.name }.filter { it.endsWith(".gram") || it.endsWith(".table.bin") }.sorted()
        return "rime data: %.1f MB in %d files; tables and grammars: %s".format(Locale.ROOT, files.sumOf { it.length() } / 1e6,
            files.size, big.joinToString(", ").ifEmpty { "none" })
    }

    /** One collection can leave objects that only become garbage once their finalizers have run. */
    private fun collect() = repeat(3) {
        Runtime.getRuntime().gc()
        System.runFinalization()
    }

    private companion object {
        const val TAG = "TitanKeysDebug"
        const val DUMP = "dump"
    }
}
