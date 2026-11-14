package com.atlas.keyboard

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * The phone's own settings that TitanKeys needs but must never change: the Sym and fn keys set to Programmable key, and
 * each app's Scroll assistant set to Sliding Mode 1. Settings > Setup shows them as steps, and opens the phone's page for
 * each. Everything here only reads.
 *
 * Where the Titan 2 Elite keeps them (read on the owner's phone, 2026-11-12):
 * - A key's mode is `persist.sys.agui_smart_<key>_key`, `programmable_key_enable` for Programmable key, and the same as
 *   `<key>_programmable_key_enable` = 1 in Settings.System. The Shortcut keys page is in com.agui.shortcutsettings.
 * - Each app's Sliding Mode is in a list that only the system can read. The system copies the mode of the app in front to
 *   `persist.sys.touchpad_forcescroll`: `yes` in Sliding Mode 1. So the keyboard notes it for each app it types in, and
 *   [SlidingModeList] decides which readings to trust.
 */
internal object PhoneChecks {
    private const val SHORTCUT_KEYS = "com.agui.shortcutsettings"
    private const val PREFERENCES = "phone_checks"
    /** Setup's list of apps outside Sliding Mode 1, and the suspects, by package ([SlidingModeList]). */
    private const val OUTSIDE_SLIDING = "outside_sliding_mode"
    private const val SUSPECT_SLIDING = "suspect_sliding_mode"
    /**
     * The first list, which one early reading could fill with apps that were on Sliding Mode 1 (Chrome and Settings on the
     * owner's phone). It's never read, and goes at the next change.
     */
    private const val OLD_OUTSIDE_SLIDING = "outside_sliding_mode_1"

    enum class Key(val id: String) { Sym("sym"), Fn("fn") }

    /** Phones without the Titan's programmable keys have nothing to set, so there the step counts as done. */
    fun programmable(context: Context, key: Key): Boolean {
        if (property("ro.agui.symkey_fnkey_default").isNullOrEmpty()) return true
        val mode = property("persist.sys.agui_smart_${key.id}_key")
        if (!mode.isNullOrEmpty()) return mode == "programmable_key_enable"
        return try {
            Settings.System.getInt(context.contentResolver, "${key.id}_programmable_key_enable", 0) == 1
        } catch (error: SecurityException) {
            true
        }
    }

    /** The phone's Shortcut keys page, where Sym and fn are set; the phone's Settings when it can't be found. */
    fun shortcutKeysPage(context: Context): Intent {
        val packages = context.packageManager
        packages.getLaunchIntentForPackage(SHORTCUT_KEYS)?.let { return it }
        val main = Intent(Intent.ACTION_MAIN).setPackage(SHORTCUT_KEYS)
        packages.queryIntentActivities(main, 0).firstOrNull { it.activityInfo.exported }?.activityInfo?.let {
            return Intent(Intent.ACTION_MAIN).setClassName(it.packageName, it.name)
        }
        return Intent(Settings.ACTION_SETTINGS)
    }

    /**
     * The touchpad's mode for the app in front, for the keyboard in [app]'s field: null when it says nothing about [app], as in
     * TitanKeys itself, with Scroll assistant off or on a phone without the touchpad. See [SlidingModeList].
     */
    fun slidingReading(context: Context, app: String): String? {
        if (app == context.packageName || !PhoneSetup.scrollAssistant() || property("ro.agui.touchpad_function") != "yes") return null
        return property("persist.sys.touchpad_forcescroll")
    }

    /** [app] showed Sliding Mode 1: a reading of yes, or a touchpad touch only Sliding Mode 1 sends. It leaves Setup's list. */
    fun mode1(context: Context, app: String) = update(context) { it.mode1(app) }

    /** A field in [app] gave a trustworthy reading outside Sliding Mode 1 ([SlidingModeList.trustworthy]). */
    fun outsideReading(context: Context, app: String) = update(context) { it.outsideReading(app) }

    private fun update(context: Context, change: (SlidingModeList) -> Unit) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val list = slidingList(context)
        val before = list.outside to list.suspects
        change(list)
        if (before == list.outside to list.suspects && !preferences.contains(OLD_OUTSIDE_SLIDING)) return
        preferences.edit().putStringSet(OUTSIDE_SLIDING, list.outside).putStringSet(SUSPECT_SLIDING, list.suspects)
            .remove(OLD_OUTSIDE_SLIDING).apply()
    }

    private fun slidingList(context: Context): SlidingModeList {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return SlidingModeList(preferences.getStringSet(OUTSIDE_SLIDING, emptySet()).orEmpty().toSet(),
            preferences.getStringSet(SUSPECT_SLIDING, emptySet()).orEmpty().toSet())
    }

    /** The names of the apps on Setup's list that are still on the phone, for Settings > Setup. */
    fun outsideSlidingMode(context: Context): List<String> {
        val packages = context.packageManager
        return slidingList(context).outside
            .mapNotNull { app ->
                try {
                    packages.getApplicationLabel(packages.getApplicationInfo(app, 0)).toString()
                } catch (error: Exception) {
                    null
                }
            }
            .sorted()
    }

    private fun property(name: String): String? = try {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
    } catch (error: ReflectiveOperationException) {
        null
    }
}
