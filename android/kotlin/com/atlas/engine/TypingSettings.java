package com.atlas.engine;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class TypingSettings {
    public static final int MIN_LIFT_OFF_MILLIS = 50, MAX_LIFT_OFF_MILLIS = 200, DEFAULT_LIFT_OFF_MILLIS = 90;
    public static final String TOUCHPAD_ONLY_IN_FIELDS = "touchpad_only_in_fields";
    private static final String DEFAULT_TAP_KEYS = "";
    // The defaults follow how the Titan 2 Elite is set up by the person who made TitanKeys: holding a letter for its symbol,
    // fn as Ctrl, suggestions in an empty home search, and American spelling. Scroll assistant and the typing log stay off.
    private static final int DEFAULT_HOLD_SYMBOL_MILLIS = 250;

    public static boolean autoCapitalise(Context context) {
        return preferences(context).getBoolean("auto_capitalise", true);
    }

    public static void autoCapitalise(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("auto_capitalise", enabled).apply();
    }

    public static final String CONTACT_NAMES = "contact_names";

    /**
     * Swipes and typing know the given and family names of the user's contacts. Off until the user turns it on, and it
     * also needs Android's Contacts permission. The names stay in memory on the phone.
     */
    public static boolean contactNames(Context context) {
        return preferences(context).getBoolean(CONTACT_NAMES, false);
    }

    public static void contactNames(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(CONTACT_NAMES, enabled).apply();
    }

    /** Where the typing settings are kept, for a part that follows one of them as it changes. */
    public static SharedPreferences settings(Context context) {
        return preferences(context);
    }

    /** Two quick spaces type a full stop, and typed punctuation moves in front of a space. */
    public static boolean autoPunctuation(Context context) {
        return preferences(context).getBoolean("auto_punctuation", true);
    }

    public static void autoPunctuation(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("auto_punctuation", enabled).apply();
    }

    /**
     * How many spaces go in after a typed full stop, question mark or exclamation mark, once the next letter comes: "one",
     * "two" or "none". One until the user picks another.
     */
    public static String sentenceSpaces(Context context) {
        String spaces = preferences(context).getString("sentence_spaces", "one");
        return "two".equals(spaces) || "none".equals(spaces) ? spaces : "one";
    }

    public static void sentenceSpaces(Context context, String spaces) {
        if (!"one".equals(spaces) && !"two".equals(spaces) && !"none".equals(spaces))
            throw new IllegalArgumentException("Not a number of spaces");
        preferences(context).edit().putString("sentence_spaces", spaces).apply();
    }

    /** A space goes in after a typed comma, semicolon or colon, once the next letter comes. */
    public static boolean commaSpace(Context context) {
        return preferences(context).getBoolean("comma_space", true);
    }

    public static void commaSpace(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("comma_space", enabled).apply();
    }

    /** Two quick pushes lock Shift or Alt. When this is off, they leave it on for the next character only. */
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
        return preferences(context).getBoolean("launcher_hides_bar", false);
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

    /** A swipe on the touchpad types a word, and a flick left from Backspace deletes one. Tap keys and the panel do not depend on this. */
    public static boolean swipeTyping(Context context) {
        return preferences(context).getBoolean("swipe_typing", true);
    }

    public static void swipeTyping(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("swipe_typing", enabled).apply();
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

    /** Holding a letter types the symbol printed on its key. */
    public static boolean holdSymbols(Context context) {
        return preferences(context).getBoolean("hold_symbols", true);
    }

    public static void holdSymbols(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("hold_symbols", enabled).apply();
    }

    public static int holdSymbolMillis(Context context) {
        return HoldEngine.clamp(preferences(context).getInt("hold_symbol_ms", DEFAULT_HOLD_SYMBOL_MILLIS));
    }

    public static void holdSymbolMillis(Context context, int millis) {
        preferences(context).edit().putInt("hold_symbol_ms", HoldEngine.clamp(millis)).apply();
    }

    /** The keyboard treats fn as Ctrl, so Key Mapper is not needed for this. */
    public static boolean fnAsCtrl(Context context) {
        return preferences(context).getBoolean("fn_as_ctrl", true);
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
     * app under it. It is off until the user turns it on. Settings shows it the other way round, as Scroll assistant outside
     * text fields, which is the phone's own name for the touchpad scrolling apps.
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

    /** A few small marks on the keyboard are orange, such as the key under a finger. Off, they take the colour of the text. */
    public static boolean orangeAccents(Context context) {
        return preferences(context).getBoolean("orange_accents", true);
    }

    public static void orangeAccents(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("orange_accents", enabled).apply();
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

    /** The characters that a snippet's shortcut can start with. Words do not use them, so a shortcut never clashes with one. */
    public static final String SNIPPET_STARTS = ";/\\,";

    /** The character that starts a snippet's shortcut, such as ; in ;addr. */
    public static char snippetStart(Context context) {
        String start = preferences(context).getString("snippet_start", ";");
        return start.length() == 1 && SNIPPET_STARTS.contains(start) ? start.charAt(0) : ';';
    }

    public static void snippetStart(Context context, String start) {
        if (start.length() != 1 || !SNIPPET_STARTS.contains(start)) throw new IllegalArgumentException("Not a snippet start");
        preferences(context).edit().putString("snippet_start", start).apply();
    }

    /** Chat shorthand such as "shd" becomes its full word, "should" (issue 63). Off by default: then it stays as typed. */
    public static boolean expandShorthand(Context context) {
        return preferences(context).getBoolean("expand_shorthand", false);
    }

    public static void expandShorthand(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("expand_shorthand", enabled).apply();
    }

    /** Listed shorthand, in lowercase, that the user switched off in Settings: it stays as typed. None at first. */
    public static Set<String> shorthandOff(Context context) {
        return preferences(context).getStringSet("shorthand_off", Collections.emptySet());
    }

    public static void shorthandOn(Context context, String shorthand, boolean on) {
        Set<String> off = new HashSet<>(shorthandOff(context));
        if (on) off.remove(shorthand); else off.add(shorthand);
        preferences(context).edit().putStringSet("shorthand_off", off).apply();
    }

    private static final String OWN_SHORTHAND = "own_shorthand";
    private static Set<String> ownShorthandRead;
    private static Map<String, String> ownShorthandCache = Collections.emptyMap();

    /**
     * The user's own shorthand: each full word by its shorthand in lowercase, sorted. Kept as "shorthand, tab, full word"
     * entries, read again only when they change, as autocorrect asks for them at each word.
     */
    public static synchronized Map<String, String> ownShorthand(Context context) {
        Set<String> entries = preferences(context).getStringSet(OWN_SHORTHAND, Collections.emptySet());
        if (entries == ownShorthandRead) return ownShorthandCache;
        Map<String, String> own = new TreeMap<>();
        for (String entry : entries) {
            int tab = entry.indexOf('\t');
            if (tab > 0 && tab < entry.length() - 1) own.put(entry.substring(0, tab), entry.substring(tab + 1));
        }
        ownShorthandRead = entries;
        ownShorthandCache = Collections.unmodifiableMap(own);
        return ownShorthandCache;
    }

    /** Saves the user's own shorthand, which the caller has checked. [full] null removes it. */
    public static synchronized void ownShorthand(Context context, String shorthand, String full) {
        Map<String, String> own = new TreeMap<>(ownShorthand(context));
        if (full == null) own.remove(shorthand); else own.put(shorthand, full);
        Set<String> entries = new HashSet<>();
        for (Map.Entry<String, String> entry : own.entrySet()) entries.add(entry.getKey() + "\t" + entry.getValue());
        preferences(context).edit().putStringSet(OWN_SHORTHAND, entries).apply();
    }

    /** A name between two colons, such as :smile:, types its emoji. */
    public static boolean shortcodes(Context context) {
        return preferences(context).getBoolean("shortcodes", true);
    }

    public static void shortcodes(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("shortcodes", enabled).apply();
    }

    /**
     * Apps where the bar is not drawn. The keyboard's window stays, one pixel high, so the keys, the panel and the app's own
     * view of the keyboard do not change. No apps at first.
     */
    public static Set<String> hiddenBarApps(Context context) {
        return preferences(context).getStringSet("hidden_bar_apps", Collections.emptySet());
    }

    public static void hiddenBarApps(Context context, Set<String> apps) {
        preferences(context).edit().putStringSet("hidden_bar_apps", new HashSet<>(apps)).apply();
    }

    /** Packages of the apps where a field started recently, newest first. Only the keyboard writes this, and only on this phone. */
    public static List<String> recentApps(Context context) {
        String apps = preferences(context).getString("recent_apps", "");
        return apps.isEmpty() ? Collections.emptyList() : Arrays.asList(apps.split(","));
    }

    public static void recentApps(Context context, List<String> apps) {
        preferences(context).edit().putString("recent_apps", String.join(",", apps)).apply();
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
        return "american".equals(preferences(context).getString("spelling", "american"))
                ? EnglishSpelling.Variant.AMERICAN : EnglishSpelling.Variant.BRITISH;
    }

    public static void spelling(Context context, EnglishSpelling.Variant variant) {
        preferences(context).edit().putString("spelling",
                variant == EnglishSpelling.Variant.AMERICAN ? "american" : "british").apply();
    }

    private TypingSettings() {}
}
