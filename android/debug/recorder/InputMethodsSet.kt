package com.titankeys.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.titankeys.engine.TypingSettings

/**
 * TitanKeys Debug only (tools/debug.sh adds it): sets which input methods are on and which one types, as Settings > Languages
 * and the held Sym's switcher do, for the touch simulator's languages batch (tools/dev/touch/language_checks.py). The keyboard
 * reads both at the next field start. Only adb can send to it: it needs the DUMP permission that the shell holds.
 *
 *   adb shell am broadcast -n com.titankeys.debug/com.titankeys.keyboard.InputMethodsSet --es on en,ru,ko --es current ru
 *
 * Ids that aren't input methods are dropped, as Settings drops them; English comes back when nothing is left. The result says
 * what was set, or why not. With --ez read true, it only reports the two saved settings.
 * With --ez restart true, it restarts the focused Space gestures field's input. Send --ez read true too so older builds
 * only report their settings.
 */
class InputMethodsSet : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.packageName.endsWith(".debug")) return
        if (intent.getBooleanExtra("restart", false)) {
            val restarted = runCatching { restartOwnedEditor(context) }.getOrDefault(false)
            resultData = if (restarted) "editor-restart-v1 restarted" else "editor-restart-v1 refused"
            return
        }
        if (intent.getBooleanExtra("read", false)) {
            val on = TypingSettings.inputMethods(context)
            val current = TypingSettings.currentInputMethod(context)
            resultData = "on $on; typing $current"
            return
        }
        val index = InputMethodList.index(context)
        val asked = intent.getStringExtra("on")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val on = index.on(InputMethodIndex.setting(asked)).map { it.id }
        val current = intent.getStringExtra("current") ?: on.firstOrNull() ?: "en"
        if (current !in on) {
            resultData = "not set: $current isn't among the input methods on ($on)"
            return
        }
        TypingSettings.inputMethods(context, InputMethodIndex.setting(on))
        TypingSettings.currentInputMethod(context, current)
        // Android's list has one TitanKeys entry, the input method typing, as Settings leaves it.
        InputMethodList.enable(context, listOf(current))
        Log.d("TitanKeysDebug", "input methods set: $on, typing $current")
        resultData = "on ${on.joinToString(",")}; typing $current"
    }

    private fun restartOwnedEditor(context: Context): Boolean {
        // The recorder is only the Space gestures activity while it is resumed.
        val activity = Recording.sink as? SpaceGestureActivity ?: return false
        if (activity.packageName != context.packageName || activity.isFinishing || activity.isDestroyed) return false
        val window = activity.window.decorView
        val editor = window.findFocus() as? EditText ?: return false
        val token = window.windowToken ?: return false
        if (!window.isAttachedToWindow || !window.hasWindowFocus() || !editor.isAttachedToWindow ||
            editor.rootView !== window || editor.windowToken !== token || editor.context.packageName != context.packageName ||
            !editor.isFocused || !editor.isShown || !editor.onCheckIsTextEditor()) return false
        val debug = "${context.packageName}/${TitanKeysIme::class.java.name}"
        if (Settings.Secure.getString(activity.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) != debug) return false
        val ime = activity.getSystemService(InputMethodManager::class.java) ?: return false
        if (ime.currentInputMethodInfo?.id != debug || !ime.isActive(editor) || !ime.isAcceptingText) return false
        ime.restartInput(editor)
        return true
    }
}
