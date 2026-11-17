package com.titankeys.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.titankeys.engine.TypingSettings

/**
 * TitanKeys Debug only (tools/debug.sh adds it): sets which input methods are on and which one types, as Settings > Languages
 * and the held Sym's switcher do, for the touch simulator's languages batch (tools/dev/touch/language_checks.py). The keyboard
 * reads both at the next field start. Only adb can send to it: it needs the DUMP permission that the shell holds.
 *
 *   adb shell am broadcast -n com.titankeys.debug/com.titankeys.keyboard.InputMethodsSet --es on en,ru,ko --es current ru
 *
 * Ids that aren't input methods are dropped, as Settings drops them; English comes back when nothing is left. The result says
 * what was set, or why not.
 */
class InputMethodsSet : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.packageName.endsWith(".debug")) return
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
}
