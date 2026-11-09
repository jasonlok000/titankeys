package com.atlas.engine;

import android.content.Context;
import android.content.SharedPreferences;

public final class TypingSettings {
    public static final int MIN_LIFT_OFF_MILLIS = 50, MAX_LIFT_OFF_MILLIS = 200, DEFAULT_LIFT_OFF_MILLIS = 90;
    private static final String DEFAULT_TAP_KEYS = "";

    public static boolean autoCapitalise(Context context) {
        return preferences(context).getBoolean("auto_capitalise", true);
    }

    public static void autoCapitalise(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("auto_capitalise", enabled).apply();
    }

    public static boolean suggestionBar(Context context) {
        return preferences(context).getBoolean("suggestion_bar", true);
    }

    public static void suggestionBar(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("suggestion_bar", enabled).apply();
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

    /** What Enter does in fields with more than one line: "send", "newline" or "app". */
    public static String enterKey(Context context) {
        return preferences(context).getString("enter_key", "send");
    }

    public static void enterKey(Context context, String value) {
        preferences(context).edit().putString("enter_key", value).apply();
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
