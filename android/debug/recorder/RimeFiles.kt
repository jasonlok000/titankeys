package com.titankeys.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.titankeys.rime.RimeAssets
import java.io.File

/**
 * TitanKeys Debug only (tools/debug.sh adds it): the files of Rime's data copy, for the phone checks of the copy that keeps
 * only the Chinese input methods that are on (docs/studies/3.0-storage.md). run-as can't read TitanKeys Debug's files, so
 * this answers in the broadcast's data, one "BYTES NAME" line per file, NAME its path under files/engines/rime/shared, in
 * order, and nothing when nothing is copied yet. Only adb can send to it, as it needs the DUMP permission that the shell holds.
 *
 *   adb shell am broadcast -n com.titankeys.debug/com.titankeys.keyboard.RimeFiles
 */
class RimeFiles : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.packageName.endsWith(".debug")) return
        val shared = File(RimeAssets.dataDir(context), "shared")
        val lines = if (!shared.isDirectory) emptyList() else shared.walkTopDown().filter { it.isFile }
            .map { "${it.length()} ${it.relativeTo(shared).path}" }.sortedBy { it.substringAfter(' ') }.toList()
        Log.d(TAG, "rime files: ${lines.size}, ${lines.sumOf { it.substringBefore(' ').toLong() }} bytes")
        resultData = lines.joinToString("\n")
    }

    private companion object {
        const val TAG = "TitanKeysDebug"
    }
}
