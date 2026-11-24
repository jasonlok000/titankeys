package com.titankeys.keyboard

import android.content.Context
import com.titankeys.connected.Announcements
import com.titankeys.engine.SwipeTyping
import com.titankeys.engine.TypingSettings

/**
 * Settings > Reset most things (owner, 2026-11-13: "there's so many now its easy to mess up"). A reset removes each setting's
 * stored value, so each falls back to the default its own code defines: no second list of defaults to drift. News is a switch
 * too, so it goes back to on. What's yours stays: words, snippets and their start, shorthand, the app lists, the clipboard, Share
 * swipes, the news you dismissed or saw, the key calibration and recordings, each in its own file or listed in [TYPING_DATA].
 *
 * Every key a store holds is either reset or kept here, and tools/checks/settings-reset.test.ts fails on one that is neither,
 * so a new setting has to say which it is.
 */
internal object SettingsReset {
    /** TypingSettings' and ExactTyping's file. */
    const val TYPING = "typing_settings"

    /** The settings in [TYPING]: every switch, stepper and choice. Scroll assistant then follows the phone's own again. */
    val TYPING_SETTINGS = setOf(
        "accent_colour", "alt_backspace_forward", "alt_sym_next_keyboard", "auto_capitalise", "auto_punctuation", "autocorrect", "chinese_plain_punctuation", "chinese_variant", "chinese_variant_shown", "comma_space", "contact_names", "current_input_method", "cursor_control", "delete_flick",
        "double_press_locks", "double_space_cursor", "earlier_words", "enter_key", "exact_real_alt", "expand_shorthand", "fix_only_when_sure",
        "hold_hints_shown", "hold_symbol_ms", "hold_symbols", "input_method_variants", "input_methods", "larger_emoji", "launcher_hides_bar", "orange_accents",
        "raw_log", "sentence_spaces", "shift_backspace_word", "shortcodes", "shorthand_off", "spelling", "suggestion_bar", "swipe_trail", "swipe_typing", "sym_panel_stays", "symbols_stay", "emoji_stays", "tap_keys",
        "touchpad_only_in_fields", "triple_space_select",
    )

    /** What gets fixed: one switch for each kind of fix, "fix_" and its id. */
    const val FIX_PREFIX = "fix_"

    /** Input-method switches, one per input method (TypingSettings): Yale's and Jyutping's lazy sounds, Cangjie's radical preview. */
    val INPUT_METHOD_PREFIXES = listOf("lazy_sounds_", "radical_preview_", "key_preview_")

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
        val fixes = typing.all.keys.filter { key -> key.startsWith(FIX_PREFIX) || INPUT_METHOD_PREFIXES.any { key.startsWith(it) } }
        // Each language's own values too ("autocorrect@fr"): see TypingSettings.PER_LANGUAGE.
        val languages = typing.all.keys.filter { key -> TypingSettings.PER_LANGUAGE.any { key.startsWith("$it@") } }
        typing.edit().apply { (TYPING_SETTINGS + fixes + languages).forEach { remove(it) } }.apply()
        val haptics = context.getSharedPreferences(HAPTICS, Context.MODE_PRIVATE)
        val switches = haptics.all.keys.filter { it.startsWith(HAPTIC_PREFIX) }
        haptics.edit().apply { (switches + HapticSwitches.MASTER).forEach { remove(it) } }.apply()
        FixedScale.resetSize(context)
        // News: its switch, on by default (bug hunt 2.1, #27). Dismissed and seen news stay.
        Announcements.resetSwitch(context)
        // Learn new words: only its switch. The words it learned stay.
        SwipeTyping.get(context).resetLearning()
        Accent.load(context)
    }
}
