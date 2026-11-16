package com.titankeys.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * TitanKeys Debug only (tools/debug.sh adds it): the memory stress check without a heap dump (#85). It runs a full collection, so
 * what `dumpsys meminfo` counts next is only what something still holds, and lists the guides and playgrounds still in memory. A
 * destroyed one in the list is a leak. Only adb can send to it, as it needs the DUMP permission that the shell holds.
 *
 *   adb shell am broadcast -n com.titankeys.debug/com.titankeys.keyboard.LeakCheck
 *   adb shell dumpsys meminfo com.titankeys.debug
 */
class LeakCheck : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.packageName.endsWith(".debug")) return
        // One collection can leave objects that only become garbage once their finalizers have run.
        repeat(3) {
            Runtime.getRuntime().gc()
            System.runFinalization()
        }
        val guides = GuideActivity.inMemory()
        val report = if (guides.isEmpty()) "no guide or playground in memory" else guides.joinToString("; ")
        Log.d(TAG, "leak check: $report")
        resultData = report
    }

    private companion object {
        const val TAG = "TitanKeysDebug"
    }
}
