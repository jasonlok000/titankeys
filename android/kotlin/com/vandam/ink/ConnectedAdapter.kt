package com.vandam.ink

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.atlas.connected.Announcements
import com.atlas.connected.Connected
import com.atlas.connected.Contributions
import org.json.JSONArray
import org.json.JSONObject

/** The Settings pages for News and for data contributions. */
internal class ConnectedAdapter(private val activity: MainActivity) : NativeAdapter {
    init {
        // The app may open before the keyboard has ever started, so it sets the jobs too.
        Thread { runCatching { Connected.schedule(activity.applicationContext) } }.start()
    }

    override fun execute(requestId: Long, operation: String, payload: String, complete: NativeResultHandler) {
        try {
            val data = JSONObject(payload)
            val result = when (operation) {
                "state" -> state().toString()
                "announcements" -> {
                    Announcements.enabled(activity, data.getBoolean("on"))
                    "null"
                }
                "allowNotifications" -> {
                    allowNotifications()
                    "null"
                }
                "contribute" -> {
                    Contributions.enabled(activity, data.getBoolean("on"))
                    "null"
                }
                "declineContributions" -> {
                    Contributions.declined(activity)
                    "null"
                }
                "deleteQueued" -> {
                    Contributions.deleteQueued(activity)
                    "null"
                }
                "deleteContributions" -> {
                    Contributions.deleteAll(activity)
                    "null"
                }
                "copyContributorId" -> {
                    val id = Contributions.contributorId(activity) ?: throw IllegalStateException("No contributor ID")
                    activity.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("TitanKeys contributor ID", id))
                    "null"
                }
                "dismissEntry" -> {
                    Announcements.dismiss(activity, data.getString("id"))
                    "null"
                }
                else -> throw IllegalArgumentException("Unknown connected operation: $operation")
            }
            complete(NativeResult.Success(result))
        } catch (error: Exception) {
            complete(NativeResult.Failure(NativeErrorKind.UNEXPECTED, error.message ?: "TitanKeys request failed", false))
        }
    }

    override fun cancel(requestId: Long) = Unit

    private fun state(): JSONObject {
        val dismissed = Announcements.dismissed(activity)
        val entries = JSONArray()
        for (entry in Announcements.entries(activity)) {
            val action = entry.action?.let { JSONObject().put("label", it.label).put("url", it.url ?: "").put("page", it.page ?: "") }
            entries.put(JSONObject().put("id", entry.id).put("title", entry.title).put("text", entry.text)
                .put("action", action ?: JSONObject.NULL).put("dismissed", entry.id in dismissed))
        }
        return JSONObject()
            .put("announcements", Announcements.enabled(activity))
            .put("notificationsAllowed", Announcements.notificationsAllowed(activity))
            .put("entries", entries)
            .put("contribute", Contributions.enabled(activity))
            .put("offerDue", Contributions.offerDue(activity))
            .put("queued", Contributions.queued(activity))
            .put("queuedBytes", Contributions.queuedBytes(activity))
            .put("sent", Contributions.sent(activity))
            .put("sentAt", Contributions.sentAt(activity))
            .put("deletionPending", Contributions.deletionPending(activity))
            .put("contributorId", Contributions.contributorId(activity) ?: "")
            .put("serverReady", Connected.CONTRIBUTIONS_URL.isNotEmpty())
            .put("privacyUrl", Connected.PRIVACY_URL)
    }

    /**
     * Asks Android for the notification permission. Once the user has said no, Android shows no more prompts, so the app's
     * notification settings open instead.
     */
    private fun allowNotifications() {
        val preferences = activity.getSharedPreferences("connected", Context.MODE_PRIVATE)
        val granted = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val asked = preferences.getBoolean(ASKED, false)
        if (!granted && (!asked || activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            preferences.edit().putBoolean(ASKED, true).apply()
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST)
        } else {
            activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
        }
    }

    private companion object {
        const val ASKED = "notifications_asked"
        const val REQUEST = 4_201
    }
}
