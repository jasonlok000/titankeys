package com.titankeys.support

import android.app.Activity
import android.content.Context
import com.titankeys.keyboard.AppBuild
import org.json.JSONObject

/** Totals for the local two-week invite. The store has no typed text, account ID, or upload path. */
internal object SupportStats {
    private const val FIXTURE = "com.titankeys.debug.supportStats"
    private const val FIRST_USE = "firstUse"
    private const val DAYS = "typingDays"
    private const val LAST_DAY = "lastTypingDay"
    private var previewing = false

    enum class Count(val key: String) {
        Words("words"), Swipes("swipes"), Corrections("corrections"), Caps("caps"), Gestures("gestures")
    }

    private fun preferences(context: Context) = context.getSharedPreferences("support_stats", Context.MODE_PRIVATE)

    @Synchronized
    fun add(context: Context, count: Count) {
        val store = preferences(context)
        val edit = store.edit().putLong(count.key, store.getLong(count.key, 0) + 1)
        // Older releases recorded when the app opened. Only a counted keyboard event can start this clock.
        if (!store.contains(FIRST_USE)) edit.putLong(FIRST_USE, System.currentTimeMillis())
        // A typing day counts once, on the first counted event of that local day.
        val today = java.time.LocalDate.now().toEpochDay()
        edit.putInt(DAYS, SupportCard.typingDays(store.getInt(DAYS, 0), store.getLong(LAST_DAY, -1), today)).putLong(LAST_DAY, today)
        edit.apply()
    }

    private fun seen(context: Context) = preferences(context).edit().putBoolean("seen", true).apply()

    fun preview(activity: Activity): Boolean {
        if (AppBuild.kind(activity) != "debug") return false
        if (activity.intent.getBooleanExtra(FIXTURE, false)) {
            activity.intent.removeExtra(FIXTURE)
            previewing = true
        }
        return previewing
    }

    fun dismiss(activity: Activity) {
        if (preview(activity)) previewing = false else seen(activity)
    }

    fun due(context: Context, fixture: Boolean = false): Boolean {
        if (fixture) return true
        val store = preferences(context)
        return SupportCard.due(store.getInt(DAYS, 0), store.getBoolean("seen", false))
    }

    /** Claims the once-only invite. A Debug launch supplies fake totals without spending the real invite. */
    @Synchronized
    fun claimInvite(activity: Activity, launcherOpen: Boolean): LongArray? {
        val fixture = preview(activity)
        if (!fixture && (!launcherOpen || !due(activity))) return null
        val totals = counts(activity, fixture)
        if (SupportCard.displayed(totals).isEmpty()) return null
        if (fixture) previewing = false else seen(activity)
        return totals
    }

    fun totals(context: Context, fixture: Boolean = false) = JSONObject().apply {
        for ((index, total) in counts(context, fixture).withIndex()) put(Count.entries[index].key, total)
    }

    private fun counts(context: Context, fixture: Boolean): LongArray {
        if (fixture) return longArrayOf(12_410, 4_152, 234, 516, 1_842)
        val store = preferences(context)
        return LongArray(Count.entries.size) { store.getLong(Count.entries[it].key, 0) }
    }
}
