package com.atlas.engine;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TypingSettings {
    public static final int MIN_LIFT_OFF_MILLIS = 50, MAX_LIFT_OFF_MILLIS = 200, DEFAULT_LIFT_OFF_MILLIS = 90;
    public static final String TOUCHPAD_ONLY_IN_FIELDS = "touchpad_only_in_fields";
    private static final String DEFAULT_TAP_KEYS = "";

    public static boolean autoCapitalise(Context context) {
        return preferences(context).getBoolean("auto_capitalise", true);
    }

    public static void autoCapitalise(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("auto_capitalise", enabled).apply();
    }

    /** Two quick spaces type a full stop, and typed punctuation moves in front of a space. */
    public static boolean autoPunctuation(Context context) {
        return preferences(context).getBoolean("auto_punctuation", true);
    }

    public static void autoPunctuation(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("auto_punctuation", enabled).apply();
    }

    /** Two quick pushes lock Shift or Alt. When this is off, the second push only turns it off. */
    public static boolean doublePressLocks(Context context) {
        return preferences(context).getBoolean("double_press_locks", false);
    }

    public static void doublePressLocks(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("double_press_locks", enabled).apply();
    }

    public static boolean suggestionBar(Context context) {
        return preferences(context).getBoolean("suggestion_bar", true);
    }

    public static void suggestionBar(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("suggestion_bar", enabled).apply();
    }

    /** A home screen's search field has no suggestion bar until you type in it. When this is off, it is like any other field. */
    public static boolean launcherHidesBar(Context context) {
        return preferences(context).getBoolean("launcher_hides_bar", true);
    }

    public static void launcherHidesBar(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("launcher_hides_bar", enabled).apply();
    }

    /** Fixes slips in typed words when they end. Swiped words never change. */
    public static boolean autocorrect(Context context) {
        return preferences(context).getBoolean("autocorrect", true);
    }

    public static void autocorrect(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("autocorrect", enabled).apply();
    }

    /** Resting a finger on Space starts cursor mode: lean towards either end to move the cursor. */
    public static boolean cursorControl(Context context) {
        return preferences(context).getBoolean("cursor_control", true);
    }

    public static void cursorControl(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("cursor_control", enabled).apply();
    }

    public static boolean liftOffGrace(Context context) { return preferences(context).getBoolean("lift_off_grace", true); }

    public static void liftOffGrace(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("lift_off_grace", enabled).apply();
    }

    public static int liftOffDurationMillis(Context context) {
        return Math.max(MIN_LIFT_OFF_MILLIS, Math.min(MAX_LIFT_OFF_MILLIS,
                preferences(context).getInt("lift_off_duration_ms", DEFAULT_LIFT_OFF_MILLIS)));
    }

    public static void liftOffDurationMillis(Context context, int millis) {
        preferences(context).edit().putInt("lift_off_duration_ms",
                Math.max(MIN_LIFT_OFF_MILLIS, Math.min(MAX_LIFT_OFF_MILLIS, millis))).apply();
    }

    public static int liftOffGraceMillis(Context context) {
        return liftOffGrace(context) ? liftOffDurationMillis(context) : 0;
    }

    /** The letters and space a light touch types, in the order they were added. */
    public static String tapKeys(Context context) {
        return preferences(context).getString("tap_keys", DEFAULT_TAP_KEYS);
    }

    public static void tapKeys(Context context, String keys) {
        StringBuilder unique = new StringBuilder();
        for (char key : keys.toCharArray())
            if ((key == ' ' || key >= 'A' && key <= 'Z') && unique.indexOf(String.valueOf(key)) < 0) unique.append(key);
        preferences(context).edit().putString("tap_keys", unique.toString()).apply();
    }

    /** Holding a letter types the symbol printed on its key. It is off until the user turns it on. */
    public static boolean holdSymbols(Context context) {
        return preferences(context).getBoolean("hold_symbols", false);
    }

    public static void holdSymbols(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("hold_symbols", enabled).apply();
    }

    public static int holdSymbolMillis(Context context) {
        return HoldEngine.clamp(preferences(context).getInt("hold_symbol_ms", HoldEngine.DEFAULT_MILLIS));
    }

    public static void holdSymbolMillis(Context context, int millis) {
        preferences(context).edit().putInt("hold_symbol_ms", HoldEngine.clamp(millis)).apply();
    }

    /** The keyboard treats fn as Ctrl, so Key Mapper is not needed for this. It is off until the user turns it on. */
    public static boolean fnAsCtrl(Context context) {
        return preferences(context).getBoolean("fn_as_ctrl", false);
    }

    public static void fnAsCtrl(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("fn_as_ctrl", enabled).apply();
    }

    /** What Enter does in fields with more than one line: "send", "newline" or "app". */
    public static String enterKey(Context context) {
        return preferences(context).getString("enter_key", "send");
    }

    public static void enterKey(Context context, String value) {
        preferences(context).edit().putString("enter_key", value).apply();
    }

    /**
     * The touchpad overlay stays up when no field is active, so that the firmware gives the touchpad to it and not to the
     * app under it. It is off until the user turns it on.
     */
    public static boolean touchpadOnlyInFields(Context context) {
        return preferences(context).getBoolean(TOUCHPAD_ONLY_IN_FIELDS, false);
    }

    public static void touchpadOnlyInFields(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(TOUCHPAD_ONLY_IN_FIELDS, enabled).apply();
    }

    /** Hears a setting change at once, for a keyboard that is idle and has no field start to read it from. */
    public static void listen(Context context, SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences(context).registerOnSharedPreferenceChangeListener(listener);
    }

    public static void stopListening(Context context, SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences(context).unregisterOnSharedPreferenceChangeListener(listener);
    }

    /** Apps the Enter key setting also applies to, each saved as "package=method". The rules are in EnterKey. */
    public static Set<String> enterApps(Context context) {
        return preferences(context).getStringSet("enter_apps", Collections.emptySet());
    }

    public static void enterApps(Context context, Set<String> apps) {
        preferences(context).edit().putStringSet("enter_apps", new HashSet<>(apps)).apply();
    }

    /** Packages of the apps with a recent multi-line field, newest first. Only the keyboard writes this, and only on this phone. */
    public static List<String> recentEnterApps(Context context) {
        String apps = preferences(context).getString("recent_enter_apps", "");
        return apps.isEmpty() ? Collections.emptyList() : Arrays.asList(apps.split(","));
    }

    public static void recentEnterApps(Context context, List<String> apps) {
        preferences(context).edit().putString("recent_enter_apps", String.join(",", apps)).apply();
    }

    /** Records touches, keys and typed text in a log on this phone. It is off until the user turns it on. */
    public static boolean rawLog(Context context) {
        return preferences(context).getBoolean("raw_log", false);
    }

    public static void rawLog(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("raw_log", enabled).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("typing_settings", Context.MODE_PRIVATE);
    }

    public static EnglishSpelling.Variant spelling(Context context) {
        return "american".equals(preferences(context).getString("spelling", "british"))
                ? EnglishSpelling.Variant.AMERICAN : EnglishSpelling.Variant.BRITISH;
    }

    public static void spelling(Context context, EnglishSpelling.Variant variant) {
        preferences(context).edit().putString("spelling",
                variant == EnglishSpelling.Variant.AMERICAN ? "american" : "british").apply();
    }

    private TypingSettings() {}
}
