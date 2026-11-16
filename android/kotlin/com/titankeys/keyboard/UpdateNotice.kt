package com.titankeys.keyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.titankeys.connected.Connected

/**
 * The rename to TitanKeys (#81) gave the keyboard and Back new class names, so the update from 2.x turns both off once:
 * Android drops a keyboard or an accessibility service whose class is gone. Words and settings stay, as the app ID and the
 * settings files keep their names. Right after that update, one quiet notification says so and opens Settings > Setup.
 *
 * It shows once per install at most. Any run of this code marks the install ([ran]), so an install that started on the new
 * names never sees it on a later update.
 */
internal object UpdateNotice {
    private const val PREFERENCES = "app"
    private const val CHECKED = "rename_checked"
    private const val CHANNEL = "setup"
    private const val NOTIFICATION = 0x5100001
    const val PAGE = "/settings/setup"

    // F: draft, the channel's name and description in Android's notification settings.
    const val CHANNEL_NAME = "Setup"
    const val CHANNEL_TEXT = "Tells you when TitanKeys needs turning back on. Silent."
    // F: draft, the notification itself.
    const val TITLE = "Turn TitanKeys back on"
    const val TEXT = "This update switched TitanKeys off as your keyboard, and Back too if you had it on. Your words and " +
        "settings are all still here. Tap to turn them back on."

    /** The app or the keyboard started on the new names: this install needs no notice on its next update. */
    fun ran(context: Context) {
        val preferences = preferences(context)
        if (!preferences.getBoolean(CHECKED, false)) preferences.edit().putBoolean(CHECKED, true).apply()
    }

    /** The app was just updated. The first time only, says so if the keyboard or Back is now off. */
    fun updated(context: Context) {
        val preferences = preferences(context)
        if (preferences.getBoolean(CHECKED, false)) return
        preferences.edit().putBoolean(CHECKED, true).commit()
        if (!needed(PhoneSetup.selected(context), BackService.enabled(context), BackDisclosureActivity.declined(context))) return
        show(context)
    }

    /** Back counts only for someone who didn't say no to it, so a person who never used it isn't told it's off. */
    fun needed(keyboardSelected: Boolean, backOn: Boolean, backDeclined: Boolean) = !keyboardSelected || !backOn && !backDeclined

    private fun show(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        // Silent: no sound, no vibration, no pop-up.
        manager.createNotificationChannel(NotificationChannel(CHANNEL, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
            description = CHANNEL_TEXT
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        })
        val open = PendingIntent.getActivity(context, NOTIFICATION, Connected.pageIntent(context, PAGE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(NOTIFICATION, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(TITLE)
            .setContentText(TEXT)
            .setStyle(Notification.BigTextStyle().bigText(TEXT))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build())
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

/** Android tells only the updated app, with MY_PACKAGE_REPLACED, right after an update installs. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) UpdateNotice.updated(context)
    }
}
