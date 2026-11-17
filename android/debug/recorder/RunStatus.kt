package com.titankeys.keyboard

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * TitanKeys Debug only (tools/debug.sh adds it): one ongoing notification with the phone run's progress, for the owner to follow
 * on the phone (2026-11-17). Silent by its channel: IMPORTANCE_LOW, no sound, no vibration, no heads-up (the owner's silent-phone
 * rule). A tap does nothing. Only adb can send to it: it needs the DUMP permission that the shell holds.
 *
 *   adb shell am broadcast -n com.titankeys.debug/com.titankeys.keyboard.RunStatus --ei batch 4 --ei batches 12 \
 *     --es name gestures-fix --ei yes 120 --ei no 3 --ei look 9 --es eta 02:40 --es note "waiting: another app in front"
 *   adb shell am broadcast -n com.titankeys.debug/com.titankeys.keyboard.RunStatus --ez done true ...   (the end)
 *   adb shell am broadcast -n com.titankeys.debug/com.titankeys.keyboard.RunStatus --ez clear true      (takes it away)
 *
 * Without the notification permission (Android 13 on) it logs that and shows nothing: the owner allows it on Debug's own prompt.
 */
class RunStatus : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.packageName.endsWith(".debug")) return
        val notifications = context.getSystemService(NotificationManager::class.java) ?: return
        if (intent.getBooleanExtra("clear", false)) {
            notifications.cancel(ID)
            resultData = "cleared"
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "run status: no notification permission, nothing shown")
            resultData = "no notification permission"
            return
        }
        val channel = NotificationChannel(CHANNEL, "Phone test runs", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        notifications.createNotificationChannel(channel)
        val status = RunStatusText.Status(
            intent.getIntExtra("batch", 0), intent.getIntExtra("batches", 0), intent.getStringExtra("name").orEmpty(),
            intent.getIntExtra("yes", 0), intent.getIntExtra("no", 0), intent.getIntExtra("look", 0),
            intent.getStringExtra("eta").orEmpty(), intent.getStringExtra("note").orEmpty(), intent.getBooleanExtra("done", false))
        val (title, counts, note) = RunStatusText.lines(status)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(title)
            .setContentText(counts)
            .setStyle(Notification.BigTextStyle().bigText(if (note.isEmpty()) counts else "$counts\n$note"))
            .setOngoing(!status.done)
            .setOnlyAlertOnce(true)
            .build()
        notifications.notify(ID, notification)
        Log.d(TAG, "run status: $title; $counts")
        resultData = "shown: $title; $counts"
    }

    private companion object {
        const val TAG = "TitanKeysDebug"
        const val CHANNEL = "phone_runs"
        const val ID = 7301
    }
}
