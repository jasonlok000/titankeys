package com.titankeys.keyboard

import android.content.Context
import com.titankeys.connected.Feed

/**
 * The bar's line after an update (#121): the first field on a new version says what changed, once per version, and a tap opens
 * Settings > What's new. Never on a fresh install. Someone who skipped versions gets the same line, and What's new shows them all,
 * from the version they had ([notesFrom]). Opening the app after an update shows What's new first, tapped or not.
 *
 * The summaries come from release-notes.txt and release-history.txt, carried into android/assets/release-summaries.txt by
 * `bun run notes`: one "version<TAB>summary" line per version, newest first.
 */
object UpdateNote {
    /** The last version whose update line the keyboard handled: shown, skipped with the bar off, or a fresh install. */
    private const val BAR_SEEN = "update_bar_seen"

    /** "<from> <to>": the version someone had before updating to <to>, kept by whichever sees the update first. */
    private const val UPDATED_FROM = "updated_from"

    private const val ASSET = "release-summaries.txt"

    /** An update to [to] from [from], [count] versions on, with [to]'s summary. */
    data class Update(val from: String, val to: String, val count: Int, val summary: String)

    /** What the bar says about [update]: short, and What's new has the rest (owner, 2026-11-25: "just updated: tap to see whats new"). */
    @Suppress("UNUSED_PARAMETER")
    fun text(update: Update): String = "Updated: tap to see what's new"

    /**
     * The update the bar should tell about now, or null. A fresh install, or a version already handled, has none; a fresh
     * install, or one whose earlier version is unknown, is marked as handled at once.
     */
    fun due(context: Context): Update? {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val current = info.versionName ?: return null
        val preferences = preferences(context)
        val seen = preferences.getString(BAR_SEEN, null)
        if (seen == current) return null
        val from = seen ?: notesFrom(context).takeIf { info.firstInstallTime != info.lastUpdateTime }
        if (from == null || Feed.compareVersions(from, current) >= 0) {
            markSeen(context)
            return null
        }
        recordFrom(context, from, current)
        val summaries = summaries(context)
        val count = summaries.keys.count { Feed.compareVersions(it, from) > 0 && Feed.compareVersions(it, current) <= 0 }
        return Update(from, current, count.coerceAtLeast(1), summaries[current].orEmpty())
    }

    fun markSeen(context: Context) {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: return
        preferences(context).edit().putString(BAR_SEEN, version).apply()
    }

    /** Keeps the version someone had before [to], unless one is already kept for [to]. */
    fun recordFrom(context: Context, from: String, to: String) {
        val preferences = preferences(context)
        if (preferences.getString(UPDATED_FROM, null)?.substringAfter(' ') == to) return
        preferences.edit().putString(UPDATED_FROM, "$from $to").apply()
    }

    /** The version someone had before this one, for What's new to show every version since; null when unknown. */
    fun notesFrom(context: Context): String? {
        val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: return null
        val preferences = preferences(context)
        val kept = preferences.getString(UPDATED_FROM, null)?.split(' ')
        if (kept?.size == 2 && kept[1] == current) return kept[0]
        return preferences.getString(FreshStart.SEEN_NOTES, null)?.takeIf { it != current }
    }

    private fun summaries(context: Context): Map<String, String> = try {
        context.assets.open(ASSET).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.associate { it.substringBefore('\t') to it.substringAfter('\t', "").trim() }
        }
    } catch (error: java.io.IOException) {
        emptyMap()
    }

    private fun preferences(context: Context) = context.getSharedPreferences("app", Context.MODE_PRIVATE)
}
