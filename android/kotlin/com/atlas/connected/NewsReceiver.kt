package com.atlas.connected

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The Snooze and Don't show again buttons of an announcement. Only this app can send to it. */
class NewsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ID) ?: return
        val result = goAsync()
        Thread {
            try {
                when (intent.action) {
                    SNOOZE -> Announcements.snooze(context, id)
                    DISMISS -> Announcements.dismiss(context, id)
                }
            } finally {
                result.finish()
            }
        }.start()
    }

    companion object {
        const val SNOOZE = "com.titankeys.news.SNOOZE"
        const val DISMISS = "com.titankeys.news.DISMISS"
        private const val ID = "id"

        fun intent(context: Context, action: String, id: String, slot: Int): PendingIntent =
            PendingIntent.getBroadcast(context, id.hashCode() * 4 + slot,
                Intent(context, NewsReceiver::class.java).setAction(action).putExtra(ID, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
