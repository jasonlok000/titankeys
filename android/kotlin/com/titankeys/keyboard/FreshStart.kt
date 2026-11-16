package com.titankeys.keyboard

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
 * Afterwards TitanKeys starts as a fresh install does (phone check, 2026-11-16: it opened on the playground, because the
 * guide's "seen" and Back's "Not now" survived). So the guide is due again, Accessibility's disclosure asks again, and the
 * release notes count as seen, as on a new install. What a fresh install doesn't have, and the wipe keeps: the key calibration,
 * which only TitanKeys Debug has; and the Share swipes ID with its record of what was sent, so swipes already shared can still
 * be deleted on the Share swipes page. tools/checks/fresh-start.test.ts lists it all.
 *
 * What isn't TitanKeys' to wipe stays: the system's own keyboard, overlay and Accessibility switches, which live in the phone's
 * settings, so Setup shows only the parts they leave to do. The swipe models, which ship with the app, stay too.
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
        // That the guide was seen, the release notes, the update notice and the other-phone warning: all as on a new install.
        "app",
        // Back's disclosure answer: Setup asks again while Accessibility is off, as on a new install. With it on, Back is done.
        "back_disclosure",
    )

    /** Files kept whole: the key calibration, which only TitanKeys Debug has. */
    val KEPT = setOf("key_calibration")

    /** Files wiped but for these keys. */
    val PARTLY_KEPT = mapOf(
        // Share swipes' ID, and what was sent and is waiting to be deleted, so the Share swipes page can still delete it.
        "connected" to setOf("contributor", "contributor_to_delete", "contributions_sent", "contributions_sent_at"),
    )

    /** Files and folders of data, by the start of their names, in the app's no-backup folder, its files and its cache. */
    private val NO_BACKUP_DATA = listOf("contributions", "word-accuracy-v1", "user_ngrams.db", "clipboard-history", "personal-words-v1", "crash-", "report-outbox")
    private val FILES_DATA = listOf("config", "rawlog", "bug-recording", "engines")
    private val CACHE_DATA = listOf("bug-recording", "crash-report", "backup-words")

    /** Set in "app" by the wipe, and taken by the front page, which says "Fresh start. Don't waste it." once. */
    private const val NOTE = "fresh_start"
    const val NOTE_TEXT = "Fresh start. Don't waste it."
    /** The release notes seen, in "app": a new install counts this version's as seen, so after the wipe they don't show. */
    const val SEEN_NOTES = "seen_release_notes"

    /** Wipes it all, then restarts TitanKeys on its front page. */
    fun wipe(activity: Activity) {
        // Deleting a file leaves Android's copy of it in memory, and a write still queued for it, such as Reset most things'
        // apply(), would put the whole copy back on disk (bug hunt 2.1, #2). Clearing it first, with commit(), which waits behind
        // any queued write, makes the last write an empty file; then the file goes.
        for (name in WIPED) {
            activity.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            activity.deleteSharedPreferences(name)
        }
        for ((name, kept) in PARTLY_KEPT) {
            val preferences = activity.getSharedPreferences(name, Context.MODE_PRIVATE)
            preferences.edit().apply { preferences.all.keys.filter { it !in kept }.forEach { remove(it) } }.commit()
        }
        activity.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean(NOTE, true)
            .putString(SEEN_NOTES, activity.packageManager.getPackageInfo(activity.packageName, 0).versionName).commit()
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
