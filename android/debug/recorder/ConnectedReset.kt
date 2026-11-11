package com.atlas.keyboard

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.atlas.connected.Connected
import com.atlas.connected.Contributions
import com.atlas.connected.TouchSizes

/**
 * TitanKeys Debug only (tools/debug.sh adds it): resets the connected features for phone tests, without clearing data.
 * Only adb can send to it, as it needs the DUMP permission that the shell holds.
 *
 *   adb shell am broadcast -n com.titankeys.debug/com.atlas.keyboard.ConnectedReset -a com.titankeys.debug.RESET_SHARE
 *     Contributions off, the waiting swipes deleted, and the offer unanswered, so the guide's last slide asks again.
 *   adb shell am broadcast -n com.titankeys.debug/com.atlas.keyboard.ConnectedReset -a com.titankeys.debug.RESET_NEWS
 *     Every news entry can show again: nothing notified, snoozed or hidden, and the next job run checks the feed.
 */
class ConnectedReset : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val preferences = context.getSharedPreferences("connected", Context.MODE_PRIVATE)
        when (intent.action) {
            "com.titankeys.debug.RESET_SHARE" -> {
                Contributions.enabled(context, false)
                // Turning off counts as an answer; the test wants none.
                preferences.edit().remove("contribute_offer").commit()
                TouchSizes.recording = false
                Log.d(TAG, "share reset: off, queue empty, offer unanswered")
            }
            "com.titankeys.debug.RESET_NEWS" -> {
                val edit = preferences.edit().remove("notified").remove("dismissed").remove("feed_checked")
                for (key in preferences.all.keys) if (key.startsWith("snooze_")) edit.remove(key)
                edit.commit()
                context.getSystemService(NotificationManager::class.java)?.cancelAll()
                Connected.schedule(context)
                Log.d(TAG, "news reset: nothing notified, snoozed or hidden")
            }
        }
        resultData = "done"
    }

    private companion object {
        const val TAG = "TitanKeysDebug"
    }
}
