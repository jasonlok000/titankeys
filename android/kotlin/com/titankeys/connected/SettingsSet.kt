package com.titankeys.connected

import android.content.Context
import com.titankeys.keyboard.HapticSwitches
import com.titankeys.keyboard.SettingsReset

/**
 * Which settings hold a value of the user's own: by key only, never the value (docs/analytics.md, "Settings"). A setting holds
 * one once it was changed in Settings, and goes back to none on Reset most things ([SettingsReset]), so this is the list of
 * settings changed since the install or the last reset. The user's own lists (snippets, shorthand, app lists) are data, not
 * settings, and are never named.
 */
internal object SettingsSet {
    /** Kept by the keyboard for itself, not set by the user. */
    private val NOT_SETTINGS = setOf("current_input_method", "chinese_variant_shown", "raw_log")

    fun keys(context: Context): List<String> {
        val typing = context.getSharedPreferences(SettingsReset.TYPING, Context.MODE_PRIVATE).all.keys
        val haptics = context.getSharedPreferences(SettingsReset.HAPTICS, Context.MODE_PRIVATE).all.keys
        return (typing.filter { it in SettingsReset.TYPING_SETTINGS && it !in NOT_SETTINGS || it.startsWith(SettingsReset.FIX_PREFIX) } +
            haptics.filter { it.startsWith(SettingsReset.HAPTIC_PREFIX) || it == HapticSwitches.MASTER }
                .map { if (it == HapticSwitches.MASTER) "haptics" else it })
            .filter { KEY.matches(it) }.sorted()
    }

    private val KEY = Regex("[a-z0-9_]{1,40}")
}
