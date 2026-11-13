package com.atlas.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Process
import java.io.File

/**
 * Settings > Reset literally everything (owner, 2026-11-13): TitanKeys as if freshly installed. Every setting, as Reset most
 * things does ([SettingsReset]), and every bit of data TitanKeys keeps: saved, blocked, favourite and learned words, refused
 * fixes, snippets and their start, shorthand, the app lists, the clipboard, recordings, news and Share swipes, which goes back
 * to off.
 *
 * What isn't TitanKeys' to wipe stays: the system's own keyboard and Accessibility switches, which live in the phone's
 * settings; the answer to the Accessibility disclosure that goes with that switch; the key calibration, which only TitanKeys
 * Debug has; and the Share swipes ID with its record of what was sent, so swipes already shared can still be deleted on the
 * Share swipes page. The swipe models, which ship with the app, stay too.
 *
 * The keyboard, the engine and the screens hold some of it in memory, so wiping the files alone could let it come back. So
 * the wipe ends by restarting TitanKeys' process, as a crash would: never a force-stop, which would turn its Accessibility
 * service off. [FreshStartActivity], in a process of its own, ends the old one and opens the front page in a new one.
 *
 * Every SharedPreferences file in the code is in [WIPED], [KEPT] or [PARTLY_KEPT], and tools/checks/settings-reset.test.ts
 * fails on one that isn't, so a new store has to say which it is.
 */
internal object FreshStart {
    /** Files wiped whole. */
    val WIPED = setOf(
        SettingsReset.TYPING, SettingsReset.HAPTICS, "fixed_scale", "word_learning", "swipe_calibration", "blocked_words",
        "refused_fixes", "favourite_words", "snippets", "autocorrect_learning", "emoji", "easter_egg", "clipboard", "phone_checks",
        // The dictionary words the word menu's Unsave took out, and the one-time repair of saved words' case having run.
        "unsaved_words", "word_case",
    )

    /** Files kept whole: the key calibration, and the Accessibility disclosure's answer. */
    val KEPT = setOf("key_calibration", "back_disclosure")

    /** Files wiped but for these keys. */
    val PARTLY_KEPT = mapOf(
        // Share swipes' ID, and what was sent and is waiting to be deleted, so the Share swipes page can still delete it.
        "connected" to setOf("contributor", "contributor_to_delete", "contributions_sent", "contributions_sent_at"),
        // The guide has been seen, so TitanKeys opens on its front page; release notes already seen stay seen.
        "app" to setOf("guide_shown", "seen_release_notes"),
    )

    /** Files and folders of data, by the start of their names, in the app's no-backup folder, its files and its cache. */
    private val NO_BACKUP_DATA = listOf("contributions", "word-accuracy-v1", "user_ngrams.db", "clipboard-history", "personal-words-v1")
    private val FILES_DATA = listOf("config", "rawlog", "bug-recording")
    private val CACHE_DATA = listOf("bug-recording")

    /** Set in "app" by the wipe, and taken by the front page, which says "Fresh start. Don't waste it." once. */
    private const val NOTE = "fresh_start"
    const val NOTE_TEXT = "Fresh start. Don't waste it."

    /** Wipes it all, then restarts TitanKeys on its front page. */
    fun wipe(activity: Activity) {
        for (name in WIPED) activity.deleteSharedPreferences(name)
        for ((name, kept) in PARTLY_KEPT) {
            val preferences = activity.getSharedPreferences(name, Context.MODE_PRIVATE)
            preferences.edit().apply { preferences.all.keys.filter { it !in kept }.forEach { remove(it) } }.commit()
        }
        activity.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean(NOTE, true).commit()
        delete(activity.noBackupFilesDir, NO_BACKUP_DATA)
        delete(activity.filesDir, FILES_DATA)
        delete(activity.cacheDir, CACHE_DATA)
        activity.startActivity(Intent(activity, FreshStartActivity::class.java)
            .putExtra(FreshStartActivity.PID, Process.myPid())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    /** Every file or folder in [folder] whose name starts with one of [names], with AtomicFile's .bak and .new and SQLite's journals. */
    private fun delete(folder: File, names: List<String>) {
        folder.listFiles()?.filter { file -> names.any { file.name.startsWith(it) } }?.forEach { it.deleteRecursively() }
    }

    /** The front page's note after a wipe, once: the note, or null. */
    fun take(context: Context): String? {
        val preferences = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        if (!preferences.getBoolean(NOTE, false)) return null
        preferences.edit().remove(NOTE).apply()
        return NOTE_TEXT
    }
}
