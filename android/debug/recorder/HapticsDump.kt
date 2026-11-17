package com.titankeys.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * TitanKeys Debug only (tools/debug.sh adds it): the haptics the keyboard tried to play lately, from the Haptics lab's own feed
 * ([HapticFeed]), for the touch simulator's checks that count them (tools/dev/touch/switcher_checks.py). Only adb can send to
 * it: it needs the DUMP permission that the shell holds. Names and results only, one per line: "uptime haptic result".
 *
 *   adb shell am broadcast -n com.titankeys.debug/com.titankeys.keyboard.HapticsDump --el since 1234567
 *
 * Without "since" it gives every event the feed keeps. The result's first line is the phone's uptime now, so the next call can
 * ask for what came after it.
 */
class HapticsDump : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.packageName.endsWith(".debug")) return
        val since = intent.getLongExtra("since", 0L)
        val lines = HapticFeed.recent().filter { it.atMillis >= since }
            .map { "${it.atMillis} ${it.haptic.id} ${it.result.name}" }
        resultData = (listOf("now ${SystemClock.uptimeMillis()} feed ${if (HapticFeed.enabled) "on" else "off"}") + lines)
            .joinToString(";")
    }
}
