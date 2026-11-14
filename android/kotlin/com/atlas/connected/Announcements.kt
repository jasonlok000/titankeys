package com.atlas.connected

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.atlas.keyboard.R

/**
 * Short messages from the TitanKeys project, such as a new version. The app downloads the signed feed at most once a
 * day, and shows a new entry once as a silent notification, only while no text field is active. The News page in
 * Settings shows the same entries. One switch turns all of it off.
 */
object Announcements {
    private const val TAG = "TitanKeysNews"
    private const val ENABLED = "announcements"
    private const val FEED = "feed_text"
    private const val ISSUED = "feed_issued"
    private const val CHECKED = "feed_checked"
    private const val DISMISSED = "dismissed"
    private const val NOTIFIED = "notified"
    private const val SNOOZE = "snooze_"
    private const val CHANNEL = "news"
    /** A job can run early after it is set again, so a check this soon after the last one does nothing. */
    private const val MIN_CHECK_GAP_MILLIS = 20 * Connected.HOUR_MILLIS
    const val SNOOZE_MILLIS = Connected.DAY_MILLIS
    const val NEWS_PAGE = "/settings/news"

    fun enabled(context: Context) = Connected.preferences(context).getBoolean(ENABLED, true)

    fun enabled(context: Context, on: Boolean) {
        Connected.preferences(context).edit().putBoolean(ENABLED, on).apply()
        if (!on) cancelNews(context)
        Connected.schedule(context)
    }

    /**
     * Takes down the news on show, and nothing else: Record a bug's ongoing notification, with its Stop, stays while it records
     * (bug hunt 2.1, #26).
     */
    private fun cancelNews(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        for (shown in manager.activeNotifications) if (shown.notification.channelId == CHANNEL) manager.cancel(shown.tag, shown.id)
    }

    /** Downloads the feed if a day has passed, keeps it if its signature is good, then shows what is due. */
    fun check(context: Context, force: Boolean = false): Boolean {
        if (!enabled(context)) return true
        val preferences = Connected.preferences(context)
        val now = System.currentTimeMillis()
        // TitanKeys Debug checks whenever its job runs, so a test can make it check again at once.
        val gap = if (Connected.build(context) == "debug") 0 else MIN_CHECK_GAP_MILLIS
        if (!force && now - preferences.getLong(CHECKED, 0) in 0 until gap) {
            showDue(context)
            return true
        }
        val envelope = try {
            val connection = Connected.open(Connected.FEED_URL, "GET")
            connection.setRequestProperty("Accept", "application/json")
            try {
                if (!Connected.ok(connection)) {
                    Log.w(TAG, "The feed answered ${connection.responseCode}")
                    return false
                }
                String(Connected.readLimited(connection.inputStream, Feed.MAX_ENVELOPE_BYTES), Charsets.UTF_8)
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not download the feed: ${error.javaClass.simpleName}")
            return false
        }
        preferences.edit().putLong(CHECKED, now).apply()
        try {
            val feed = try {
                Feed.open(envelope, Ed25519.publicKeyFromSpki(Connected.key(Connected.FEED_KEY)))
            } finally {
                if (Connected.build(context) == "debug") Log.d(TAG, "verifier=${Ed25519.lastVerifier?.name?.lowercase() ?: "none"}")
            }
            if (feed.issued < preferences.getLong(ISSUED, 0)) {
                Log.w(TAG, "Ignored a feed older than the one already seen")
            } else {
                val text = Json.parse(envelope).let { (it as Map<*, *>)["feed"] as String }
                preferences.edit().putString(FEED, text).putLong(ISSUED, feed.issued).apply()
                Log.d(TAG, "Feed verified: ${feed.entries.size} entries")
            }
        } catch (error: Exception) {
            // A feed that fails its signature or its checks is thrown away whole, and the one kept before stays.
            Log.w(TAG, "Ignored a feed that is not valid: ${error.message}")
        }
        showDue(context)
        return true
    }

    /** The entries for this app and build now, newest first, as the News page shows them. */
    internal fun entries(context: Context): List<Feed.Entry> {
        val text = Connected.preferences(context).getString(FEED, null) ?: return emptyList()
        val feed = try { Feed.read(text) } catch (error: Feed.Invalid) { return emptyList() }
        val now = System.currentTimeMillis()
        val version = Connected.version(context)
        val build = Connected.build(context)
        return feed.entries.filter { Feed.applies(it, now, version, build) }.reversed()
    }

    fun notificationsAllowed(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            && context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true

    /** Shows the next entry that is due, unless a text field is active. One at a time; the next waits for another moment. */
    @Synchronized
    fun showDue(context: Context) {
        if (!enabled(context) || Connected.typing || !notificationsAllowed(context)) return
        val preferences = Connected.preferences(context)
        val dismissed = preferences.getStringSet(DISMISSED, emptySet())!!
        val notified = preferences.getStringSet(NOTIFIED, emptySet())!!
        val now = System.currentTimeMillis()
        val entry = entries(context).lastOrNull { entry ->
            entry.notify && entry.id !in dismissed && entry.id !in notified && preferences.getLong(SNOOZE + entry.id, 0) <= now
        } ?: return
        preferences.edit().putStringSet(NOTIFIED, notified + entry.id).remove(SNOOZE + entry.id).apply()
        notify(context, entry)
    }

    private fun notify(context: Context, entry: Feed.Entry) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // A short pop-up, with no sound and no vibration.
        val channel = NotificationChannel(CHANNEL, "News from TitanKeys", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "New versions and other news. It never shows up while you're typing."
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(entry.title)
            .setContentText(entry.text)
            .setStyle(Notification.BigTextStyle().bigText(entry.text))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setContentIntent(openPage(context, NEWS_PAGE, entry.id.hashCode() * 4))
        entry.action?.let { action -> builder.addAction(Notification.Action.Builder(null, action.label, actionIntent(context, entry, action)).build()) }
        builder.addAction(Notification.Action.Builder(null, "Snooze", NewsReceiver.intent(context, NewsReceiver.SNOOZE, entry.id, 1)).build())
        builder.addAction(Notification.Action.Builder(null, "Don't show again", NewsReceiver.intent(context, NewsReceiver.DISMISS, entry.id, 2)).build())
        manager.notify(notificationId(entry.id), builder.build())
    }

    internal fun notificationId(id: String) = NOTIFICATION_BASE + (id.hashCode() and 0xFFFF)

    private fun actionIntent(context: Context, entry: Feed.Entry, action: Feed.Action): PendingIntent =
        // Android lets a notification open an activity only directly, not through a receiver.
        if (action.url != null) PendingIntent.getActivity(context, entry.id.hashCode() * 4 + 3,
            Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        else openPage(context, action.page!!, entry.id.hashCode() * 4 + 3)

    /** Opens a page of the app, through Ink's own way to open a page from a notification. */
    fun openPage(context: Context, page: String, request: Int): PendingIntent {
        return PendingIntent.getActivity(context, request, Connected.pageIntent(context, page),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun openLink(context: Context, url: String) {
        if (!url.startsWith("https://")) return
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun snooze(context: Context, id: String) {
        val preferences = Connected.preferences(context)
        val notified = preferences.getStringSet(NOTIFIED, emptySet())!!
        preferences.edit().putLong(SNOOZE + id, System.currentTimeMillis() + SNOOZE_MILLIS).putStringSet(NOTIFIED, notified - id).apply()
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId(id))
    }

    fun dismiss(context: Context, id: String) {
        val preferences = Connected.preferences(context)
        val dismissed = preferences.getStringSet(DISMISSED, emptySet())!!
        preferences.edit().putStringSet(DISMISSED, dismissed + id).remove(SNOOZE + id).apply()
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId(id))
    }

    fun dismissed(context: Context): Set<String> = Connected.preferences(context).getStringSet(DISMISSED, emptySet())!!

    private const val NOTIFICATION_BASE = 0x4E00000
}
