package com.atlas.keyboard

import android.content.Context
import com.atlas.engine.SwipeTyping

/**
 * Settings > Reset all settings (owner, 2026-11-13: "there's so many now its easy to mess up"). A reset removes each setting's
 * stored value, so each falls back to the default its own code defines: no second list of defaults to drift. What's yours
 * stays: words, snippets and their start, shorthand, the app lists, the clipboard, Share swipes, news, the key calibration and
 * recordings, each in its own file or listed in [TYPING_DATA].
 *
 * Every key a store holds is either reset or kept here, and tools/checks/settings-reset.test.ts fails on one that is neither,
 * so a new setting has to say which it is.
 */
internal object SettingsReset {
    /** TypingSettings' and ExactTyping's file. */
    const val TYPING = "typing_settings"

    /** The settings in [TYPING]: every switch, stepper and choice. Scroll assistant then follows the phone's own again. */
    val TYPING_SETTINGS = setOf(
        "auto_capitalise", "auto_punctuation", "autocorrect", "comma_space", "contact_names", "cursor_control",
        "double_press_locks", "enter_key", "exact_real_alt", "expand_shorthand", "fix_only_when_sure", "fn_as_ctrl",
        "hold_symbol_ms", "hold_symbols", "launcher_hides_bar", "lift_off_duration_ms", "lift_off_grace", "orange_accents",
        "raw_log", "sentence_spaces", "shortcodes", "shorthand_off", "spelling", "suggestion_bar", "swipe_typing", "tap_keys",
        "touchpad_only_in_fields",
    )

    /** What gets fixed: one switch for each kind of fix, "fix_" and its id. */
    const val FIX_PREFIX = "fix_"

    /** What's yours in [TYPING], which a reset leaves: the app lists, the snippets' start, and your own shorthand. */
    val TYPING_DATA = setOf(
        "enter_apps", "exact_apps", "hidden_bar_apps", "recent_apps", "recent_enter_apps", "own_shorthand", "snippet_start",
        "snippet_start_settled",
    )

    /** Haptics' file: All haptics and each haptic's own switch reset. The Haptics lab's tuning, in TitanKeys Debug, stays. */
    const val HAPTICS = "swipe_haptics"
    const val HAPTIC_PREFIX = "haptic_"

    /** Puts every setting back to how it started, and tells the windows that show them. */
    fun reset(context: Context) {
        val typing = context.getSharedPreferences(TYPING, Context.MODE_PRIVATE)
        val fixes = typing.all.keys.filter { it.startsWith(FIX_PREFIX) }
        typing.edit().apply { (TYPING_SETTINGS + fixes).forEach { remove(it) } }.apply()
        val haptics = context.getSharedPreferences(HAPTICS, Context.MODE_PRIVATE)
        val switches = haptics.all.keys.filter { it.startsWith(HAPTIC_PREFIX) }
        haptics.edit().apply { (switches + HapticSwitches.MASTER).forEach { remove(it) } }.apply()
        FixedScale.resetSize(context)
        // Learn new words: only its switch. The words it learned stay.
        SwipeTyping.get(context).resetLearning()
        Accent.load(context)
    }
}
