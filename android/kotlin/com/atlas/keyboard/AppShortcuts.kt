package com.atlas.keyboard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.KeyEvent

/** Whether app shortcuts are on, the hyper key, and the app each letter opens. */
object AppShortcuts {
    /** No hyper key, so no key opens a shortcut. */
    const val NONE = KeyEvent.KEYCODE_UNKNOWN

    class App(val packageName: String, val name: String)

    private fun preferences(context: Context) = context.getSharedPreferences("app_shortcuts", Context.MODE_PRIVATE)

    fun enabled(context: Context) = preferences(context).getBoolean("enabled", true)

    fun enabled(context: Context, on: Boolean) {
        preferences(context).edit().putBoolean("enabled", on).apply()
    }

    /** The app opens with Android's usual animation. Off, it shows at once. */
    fun animation(context: Context) = preferences(context).getBoolean("animation", true)

    fun animation(context: Context, on: Boolean) {
        preferences(context).edit().putBoolean("animation", on).apply()
    }

    /** The key that opens shortcuts, or [NONE] when shortcuts are off or have no key. */
    fun activeKey(context: Context) = if (enabled(context)) hyperKey(context) else NONE

    fun hyperKey(context: Context) = preferences(context).getInt("hyper_key", NONE)

    fun hyperKey(context: Context, code: Int) {
        require(code == NONE || HyperKey.problem(code) == null)
        preferences(context).edit().putInt("hyper_key", code).apply()
    }

    /** The name on the key, such as "Fn", or one from Android, such as "F12", or "Key 403" when it has none. */
    fun label(code: Int): String {
        KEY_NAMES[code]?.let { return it }
        val name = KeyEvent.keyCodeToString(code)
        if (!name.startsWith("KEYCODE_")) return "Key $code"
        return name.removePrefix("KEYCODE_").split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
    }

    /** Titan 2 Elite keys, named as printed. The firmware sends Fn as 403, a code that Android has no name for. */
    private val KEY_NAMES = mapOf(403 to "Fn", KeyEvent.KEYCODE_SYM to "Sym", 342 to "Sym",
        KeyEvent.KEYCODE_ALT_LEFT to "Alt", KeyEvent.KEYCODE_SHIFT_LEFT to "Shift", KeyEvent.KEYCODE_SHIFT_RIGHT to "Shift",
        KeyEvent.KEYCODE_BACK to "Back", KeyEvent.KEYCODE_HOME to "Home", KeyEvent.KEYCODE_APP_SWITCH to "Recent apps")

    fun app(context: Context, letter: Char): String? = preferences(context).getString("letter_$letter", null)

    fun app(context: Context, letter: Char, packageName: String?) {
        require(letter in 'A'..'Z')
        preferences(context).edit().apply {
            if (packageName == null) remove("letter_$letter") else putString("letter_$letter", packageName)
        }.apply()
    }

    /** Each letter with an app, in order. */
    fun shortcuts(context: Context): List<Pair<Char, String>> =
        ('A'..'Z').mapNotNull { letter -> app(context, letter)?.let { letter to it } }

    /** Apps with a launcher icon, by name. */
    fun apps(context: Context): List<App> {
        val manager = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return manager.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
            .map { App(it.activityInfo.packageName, it.loadLabel(manager).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.name.lowercase() }
    }

    fun name(context: Context, packageName: String): String = try {
        val manager = context.packageManager
        manager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0)).loadLabel(manager).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    /** Opens the letter's app. Returns false when the letter has no app or the app is gone. */
    fun open(context: Context, letter: Char): Boolean {
        val packageName = app(context, letter) ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        // A custom animation of none is ignored for a start from a service; only this flag skips it.
        if (!animation(context)) intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        context.startActivity(intent)
        return true
    }
}
