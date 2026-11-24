package com.titankeys.keyboard

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
    private const val SLIDING_EVIDENCE = "sliding_evidence_version"
    /**
     * The first list, which one early reading could fill with apps that were on Sliding Mode 1 (Chrome and Settings on the
     * owner's phone). It's never read, and goes at the next change.
     */
    private const val OLD_OUTSIDE_SLIDING = "outside_sliding_mode_1"

    enum class Key(val id: String) { Sym("sym"), Fn("fn") }

    /** Phones without the Titan's programmable keys have nothing to set, so there the step counts as done. */
    fun programmable(context: Context, key: Key) = programmableReading(context, key) ?: true

    /**
     * Whether [key] is set to Programmable key: true, false, or null when its setting can't be confirmed (another phone, or the
     * Titan's setting can't be read). Cheap enough to call as a field starts or a key is pressed (#116).
     *
     * What it returns for each mode:
     * - `persist.sys.agui_smart_<key>_key` = `programmable_key_enable`: true. Any other non-empty value is another mode on the
     *   Shortcut keys page (Tab, an app, a shortcut and so on): false. Those values aren't recorded yet: the owner is to read them
     *   on the phone (`adb shell getprop persist.sys.agui_smart_fn_key` after picking each mode) and note them here.
     * - The property empty: Settings.System `<key>_programmable_key_enable`, 1 true, 0 false, missing or unreadable null.
     * - `ro.agui.symkey_fnkey_default` empty (not a Titan with these keys): null.
     */
    fun programmableReading(context: Context, key: Key): Boolean? {
        if (property("ro.agui.symkey_fnkey_default").isNullOrEmpty()) return null
        val mode = property("persist.sys.agui_smart_${key.id}_key")
        if (!mode.isNullOrEmpty()) return mode == "programmable_key_enable"
        return try {
            when (Settings.System.getInt(context.contentResolver, "${key.id}_programmable_key_enable", -1)) {
                0 -> false
                1 -> true
                else -> null
            }
        } catch (error: SecurityException) {
            null
        }
    }

    /**
     * The phone's Shortcut keys page, where Sym and fn are set; the phone's Settings when it can't be found. It's the page the
     * phone's own Settings opens, the app's entry: its other activities need a key handed to them, and the first one Android
     * lists for MAIN, FunctionDisplayActivity, crashed the app without one (owner, 2026-11-14, build #20: "The two set to
     * programmable keys shortcut in setup is not working it crash").
     */
    fun shortcutKeysPage(context: Context): Intent {
        val packages = context.packageManager
        packages.getLaunchIntentForPackage(SHORTCUT_KEYS)?.let { return it }
        val entry = Intent(Intent.ACTION_MAIN).setClassName(SHORTCUT_KEYS, SHORTCUT_KEYS_ENTRY)
        if (packages.resolveActivity(entry, 0)?.activityInfo?.exported == true) return entry
        val fromSettings = Intent(SETTINGS_ENTRY).setPackage(SHORTCUT_KEYS)
        packages.queryIntentActivities(fromSettings, 0).firstOrNull { it.activityInfo.exported }?.activityInfo?.let {
            return Intent(Intent.ACTION_MAIN).setClassName(it.packageName, it.name)
        }
        return Intent(Settings.ACTION_SETTINGS)
    }

    /**
     * The keyboard's warning that [key] isn't Programmable key (#116), shown once: true the first time it's asked while the
     * reading is false. Once the key reads Programmable key again, the warning is armed again, so a later change is caught too.
     * A null reading has its own once-only note, [unreadableNoteDue].
     */
    fun keyWarningDue(context: Context, key: Key, reading: Boolean?): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val name = "warned_${key.id}_key"
        if (reading == true) {
            if (preferences.getBoolean(name, false)) preferences.edit().remove(name).apply()
            return false
        }
        if (reading == null || preferences.getBoolean(name, false)) return false
        preferences.edit().putBoolean(name, true).apply()
        return true
    }

    /** True once ever: the first time [key] is used while its setting can't be read (#116). */
    fun unreadableNoteDue(context: Context, key: Key): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val name = "noted_${key.id}_key_unreadable"
        if (preferences.getBoolean(name, false)) return false
        preferences.edit().putBoolean(name, true).apply()
        return true
    }

    /** The bar's words for [key] not being Programmable key, or for fn when its setting can't be read ([reading] null). */
    fun keyWarning(key: Key, reading: Boolean?): String = when {
        key == Key.Sym -> "Sym can't open symbols. Tap, then Sym key > Programmable key."
        reading == null -> "fn works as Ctrl (fn+C copies). Not working? Tap, then fn key > Programmable key."
        else -> "fn isn't working as Ctrl. Tap, then fn key > Programmable key."
    }

    /** The Shortcut keys app's entry, as the phone's Settings lists it. */
    private const val SHORTCUT_KEYS_ENTRY = "$SHORTCUT_KEYS.ui.EntryAppActivity"
    /** The action the phone's Settings finds its extra pages by, the Shortcut keys page among them. */
    private const val SETTINGS_ENTRY = "com.android.settings.action.EXTRA_SETTINGS"

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
    fun outsideReading(context: Context, app: String) {
        if (inPicker(context, app)) update(context) { it.outsideReading(app) }
    }

    /**
     * Scroll assistant's picker lists only the apps on the home screen. Fields can belong to others: Settings' search box is
     * Settings Suggestions (com.android.settings.intelligence), which Setup told the owner to find on firmware V02.00.06
     * (2026-11-22), and the picker has no such entry, on old firmware or new. So an app without a launcher entry never joins.
     */
    private fun inPicker(context: Context, app: String) = context.packageManager.getLaunchIntentForPackage(app) != null

    private fun update(context: Context, change: (SlidingModeList) -> Unit) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val list = slidingList(context)
        val before = list.outside to list.suspects
        change(list)
        if (before == list.outside to list.suspects) return
        preferences.edit().putStringSet(OUTSIDE_SLIDING, list.outside).putStringSet(SUSPECT_SLIDING, list.suspects)
            .remove(OLD_OUTSIDE_SLIDING).apply()
    }

    private fun slidingList(context: Context): SlidingModeList {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val outside = preferences.getStringSet(OUTSIDE_SLIDING, emptySet()).orEmpty().toSet()
        val suspects = preferences.getStringSet(SUSPECT_SLIDING, emptySet()).orEmpty().toSet()
        val version = preferences.getInt(SLIDING_EVIDENCE, 0)
        val list = SlidingModeList.restored(outside, suspects, version)
        // Older records could count repeated starts of one field or unknown property values.
        // They need fresh evidence. Persist filtering too, so Android Settings cannot return.
        if (version != SlidingModeList.EVIDENCE_VERSION || outside != list.outside || suspects != list.suspects ||
            preferences.contains(OLD_OUTSIDE_SLIDING)) {
            preferences.edit().putStringSet(OUTSIDE_SLIDING, list.outside).putStringSet(SUSPECT_SLIDING, list.suspects)
                .putInt(SLIDING_EVIDENCE, SlidingModeList.EVIDENCE_VERSION).remove(OLD_OUTSIDE_SLIDING).apply()
        }
        return list
    }

    /** The names of the apps on Setup's list that are still on the phone, for Settings > Setup. */
    fun outsideSlidingMode(context: Context): List<String> {
        val packages = context.packageManager
        return slidingList(context).outside
            .filter { inPicker(context, it) }
            .mapNotNull { app ->
                try {
                    packages.getApplicationLabel(packages.getApplicationInfo(app, 0)).toString()
                } catch (error: Exception) {
                    null
                }
            }
            .sorted()
    }

    internal fun property(name: String): String? = try {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
    } catch (error: ReflectiveOperationException) {
        null
    }
}
