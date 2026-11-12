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
import com.atlas.keyboard.BugRecording
import com.atlas.keyboard.Haptic
import com.atlas.keyboard.Haptics
import org.json.JSONArray
import org.json.JSONObject

/** The Settings pages for News and for data contributions. */
internal class ConnectedAdapter(private val activity: MainActivity) : NativeAdapter {
    private val haptics by lazy { Haptics(activity) }

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
                "bugState" -> bugState().toString()
                "bugStart" -> {
                    BugRecording.start(activity)
                    // A lock as it starts and a release as it stops, since a button on the screen has no click of its own.
                    haptics.play(Haptic.bugRecording(started = true))
                    "null"
                }
                "bugStop" -> {
                    BugRecording.stop(activity)
                    haptics.play(Haptic.bugRecording(started = false))
                    "null"
                }
                "bugDelete" -> {
                    BugRecording.delete(activity)
                    "null"
                }
                "bugShare" -> {
                    BugRecording.share(activity, data.getString("subject"), data.getString("text"))
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

    /** Record a bug: whether it runs and for how long, or what the finished file holds. */
    private fun bugState(): JSONObject {
        val state = JSONObject()
            .put("recording", BugRecording.recording)
            .put("elapsed", BugRecording.elapsed())
            .put("maxMillis", BugRecording.MAX_MILLIS)
            .put("notificationsAllowed", Announcements.notificationsAllowed(activity))
        val summary = if (BugRecording.recording) null else BugRecording.summary(activity)
        if (summary != null) state.put("summary", JSONObject()
            .put("millis", summary.millis).put("fields", summary.fields).put("touches", summary.touches)
            .put("swipes", summary.swipes).put("keys", summary.keys).put("words", JSONArray(summary.words))
            .put("bytes", BugRecording.size(activity)))
        return state
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
