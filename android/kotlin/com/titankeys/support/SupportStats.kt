package com.titankeys.support

import android.app.Activity
import android.content.Context
import com.titankeys.keyboard.AppBuild
import org.json.JSONObject

/** Totals for the local two-week card. The store has no typed text, account ID, or upload path. */
internal object SupportStats {
    private const val FIXTURE = "com.titankeys.debug.supportStats"
    private var previewing = false

    enum class Count(val key: String) {
        Words("words"), Swipes("swipes"), Corrections("corrections"), Caps("caps"), Gestures("gestures")
    }

    private fun preferences(context: Context) = context.getSharedPreferences("support_stats", Context.MODE_PRIVATE)

    @Synchronized
    fun firstRun(context: Context) {
        val store = preferences(context)
        if (!store.contains("firstRun")) store.edit().putLong("firstRun", System.currentTimeMillis()).apply()
    }

    @Synchronized
    fun add(context: Context, count: Count) {
        firstRun(context)
        val store = preferences(context)
        store.edit().putLong(count.key, store.getLong(count.key, 0) + 1).apply()
    }

    fun seen(context: Context) = preferences(context).edit().putBoolean("seen", true).apply()

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
        firstRun(context)
        val store = preferences(context)
        return SupportCard.due(System.currentTimeMillis(), store.getLong("firstRun", 0), store.getBoolean("seen", false))
    }

    fun totals(context: Context, fixture: Boolean = false) = JSONObject().apply {
        val store = preferences(context)
        val sample = longArrayOf(12_410, 4_152, 234, 516, 1_842)
        for (count in Count.entries) put(count.key, if (fixture) sample[count.ordinal] else store.getLong(count.key, 0))
    }
}
