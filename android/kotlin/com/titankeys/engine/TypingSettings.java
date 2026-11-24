package com.titankeys.engine;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class TypingSettings {
    public static final int DEFAULT_LIFT_OFF_MILLIS = 90;
    public static final String TOUCHPAD_ONLY_IN_FIELDS = "touchpad_only_in_fields";
    public static final String INPUT_METHODS = "input_methods";
    private static final String DEFAULT_TAP_KEYS = "";
    // The defaults follow how the Titan 2 Elite is set up by the person who made TitanKeys: holding a letter for its symbol,
    // fn as Ctrl, suggestions in an empty home search, and American spelling. Scroll assistant and the typing log stay off.
    // Hold for symbols starts at Medium, 400 ms (owner, #77, 3.0). It is stored only once someone picks a time, so everyone who
    // never did moves from 2.x's 250 ms with the update, and a stored choice stays.
    public static final int DEFAULT_HOLD_SYMBOL_MILLIS = HoldEngine.DEFAULT_MILLIS;

    /**
     * The settings each language keeps for itself (owner, 2026-11-17: "some settings doesn't work in some languages ... so having it
     * outside makes it look like global settings"). A language's own value is under "key@language"; one it never set reads the
     * plain key, the single value from before, so nothing changes on update. Settings' main page writes the plain key and
     * English's own while English is the only language on; each language's page writes its own.
     */
    public static final String[] PER_LANGUAGE = {
        "autocorrect", "auto_capitalise", "auto_punctuation", "sentence_spaces", "comma_space", "hold_symbols",
    };

    public static String languageKey(String key, String language) {
        return key + "@" + language;
    }

    private static boolean languageBoolean(Context context, String key, String language, boolean otherwise) {
        SharedPreferences settings = preferences(context);
        String own = languageKey(key, language);
        return settings.contains(own) ? settings.getBoolean(own, otherwise) : settings.getBoolean(key, otherwise);
    }

    private static void putLanguageBoolean(Context context, String key, String language, boolean value) {
        preferences(context).edit().putBoolean(languageKey(key, language), value).apply();
    }

    public static boolean autocorrectIn(Context context, String language) {
        return languageBoolean(context, "autocorrect", language, true);
    }

    public static void autocorrectIn(Context context, String language, boolean enabled) {
        putLanguageBoolean(context, "autocorrect", language, enabled);
    }

    public static boolean autoCapitaliseIn(Context context, String language) {
        return languageBoolean(context, "auto_capitalise", language, true);
    }

    public static void autoCapitaliseIn(Context context, String language, boolean enabled) {
        putLanguageBoolean(context, "auto_capitalise", language, enabled);
    }

    public static boolean autoPunctuationIn(Context context, String language) {
        return languageBoolean(context, "auto_punctuation", language, true);
    }

    public static void autoPunctuationIn(Context context, String language, boolean enabled) {
        putLanguageBoolean(context, "auto_punctuation", language, enabled);
    }

    public static boolean commaSpaceIn(Context context, String language) {
        return languageBoolean(context, "comma_space", language, true);
    }

    public static void commaSpaceIn(Context context, String language, boolean enabled) {
        putLanguageBoolean(context, "comma_space", language, enabled);
    }

    public static boolean holdSymbolsIn(Context context, String language) {
        return languageBoolean(context, "hold_symbols", language, true);
    }

    public static void holdSymbolsIn(Context context, String language, boolean enabled) {
        putLanguageBoolean(context, "hold_symbols", language, enabled);
    }

    public static String sentenceSpacesIn(Context context, String language) {
        String own = languageKey("sentence_spaces", language);
        if (!preferences(context).contains(own)) return sentenceSpaces(context);
        String spaces = preferences(context).getString(own, "one");
        return "two".equals(spaces) || "none".equals(spaces) ? spaces : "one";
    }

    public static void sentenceSpacesIn(Context context, String language, String spaces) {
        if (!"one".equals(spaces) && !"two".equals(spaces) && !"none".equals(spaces))
            throw new IllegalArgumentException("Not a number of spaces");
        preferences(context).edit().putString(languageKey("sentence_spaces", language), spaces).apply();
    }

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

    /** A swipe on the touchpad types a word. Tap keys and the panel do not depend on this. */
    public static boolean swipeTyping(Context context) {
        return preferences(context).getBoolean("swipe_typing", true);
    }

    public static void swipeTyping(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("swipe_typing", enabled).apply();
    }

    /** Either Space gesture is on: double Space moves the cursor, triple Space selects. Setting it sets both. */
    public static boolean cursorControl(Context context) {
        return doubleSpaceCursor(context) || tripleSpaceSelect(context);
    }

    public static void cursorControl(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("double_space_cursor", enabled).putBoolean("triple_space_select", enabled).apply();
    }

    /** Two taps on Space pick the word, and a tap then a held drag moves the cursor. Until set, it follows the old one switch, "cursor_control" (#120). */
    public static boolean doubleSpaceCursor(Context context) {
        SharedPreferences preferences = preferences(context);
        return preferences.getBoolean("double_space_cursor", preferences.getBoolean("cursor_control", true));
    }

    public static void doubleSpaceCursor(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("double_space_cursor", enabled).apply();
    }

    /** Three taps on Space pick the word, and two taps then a held drag select words. Until set, it follows "cursor_control" (#120). */
    public static boolean tripleSpaceSelect(Context context) {
        SharedPreferences preferences = preferences(context);
        return preferences.getBoolean("triple_space_select", preferences.getBoolean("cursor_control", true));
    }

    public static void tripleSpaceSelect(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("triple_space_select", enabled).apply();
    }

    /** A flick left from Backspace deletes a word (#120). Swipe typing no longer turns it off. */
    public static boolean deleteFlick(Context context) {
        return preferences(context).getBoolean("delete_flick", true);
    }

    public static void deleteFlick(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("delete_flick", enabled).apply();
    }

    /** The trail a swipe draws on the suggestion bar (#112). Off, the swipe still types. */
    public static boolean swipeTrail(Context context) {
        return preferences(context).getBoolean("swipe_trail", true);
    }

    public static void swipeTrail(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("swipe_trail", enabled).apply();
    }

    /** The Sym panel stays open after a pick, until Sym or Back closes it (#110). Off, one pick closes it. */
    public static boolean symPanelStays(Context context) {
        return preferences(context).getBoolean("sym_panel_stays", false);
    }

    public static void symPanelStays(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("sym_panel_stays", enabled).apply();
    }

    /** The emoji in the Sym panel draw a quarter larger, in the same cells. Off, they are the symbols' size. */
    public static boolean largerEmoji(Context context) {
        return preferences(context).getBoolean("larger_emoji", false);
    }

    public static void largerEmoji(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("larger_emoji", enabled).apply();
    }

    // A broken swipe always joins, no longer a setting (owner, 2026-11-21: "let's remove the switch"). The joining itself is
    // Swipe.sameStroke's rule, tuned on the recordings (docs/paper.md 4.7); this only keeps a brief flicker, and how long a
    // swiped word waits before its last check. An old "lift_off_grace" or "lift_off_duration_ms" left in preferences is ignored.
    public static boolean liftOffGrace(Context context) { return true; }

    public static int liftOffDurationMillis(Context context) { return DEFAULT_LIFT_OFF_MILLIS; }

    public static int liftOffGraceMillis(Context context) { return DEFAULT_LIFT_OFF_MILLIS; }

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
     * app under it. Settings shows it the other way round, as Scroll assistant, the phone's own name for the touchpad
     * scrolling apps. Until the user picks, it follows the phone's own Scroll assistant (owner, 2026-11-12): on if the
     * phone's is on. A stored choice always wins and is never rewritten.
     */
    public static boolean touchpadOnlyInFields(Context context) {
        SharedPreferences preferences = preferences(context);
        if (!preferences.contains(TOUCHPAD_ONLY_IN_FIELDS)) return !phoneScrollAssistant();
        return preferences.getBoolean(TOUCHPAD_ONLY_IN_FIELDS, false);
    }

    /**
     * The phone's own Scroll assistant, persist.sys.scroll_assistant. Read only: TitanKeys never changes it. Phones without the
     * touchpad have none, so there it counts as on, and so does a phone whose properties can't be read.
     */
    public static boolean phoneScrollAssistant() {
        try {
            java.lang.reflect.Method get = Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class);
            if (!"yes".equals(get.invoke(null, "ro.agui.touchpad_function", ""))) return true;
            return "1".equals(get.invoke(null, "persist.sys.scroll_assistant", "0"));
        } catch (ReflectiveOperationException | RuntimeException error) {
            return true;
        }
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

    /**
     * Alt + Backspace deletes forward, the character after the cursor, like Delete on a PC. Off, it goes to the app as Android's
     * own Alt + Backspace, which deletes the line. On to start (owner, 2026-11-13).
     */
    public static boolean altBackspaceForward(Context context) {
        return preferences(context).getBoolean("alt_backspace_forward", true);
    }

    public static void altBackspaceForward(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("alt_backspace_forward", enabled).apply();
    }

    /**
     * A held Shift + Backspace deletes a word. Off, it's a plain Backspace, one character, as if the shortcut weren't there.
     * Ctrl or fn + Backspace still deletes a word. On to start (owner, 2026-11-13: "why is delete a word shift backspace and next keyboard alt sym not toggleable?").
     */
    public static boolean shiftBackspaceWord(Context context) {
        return preferences(context).getBoolean("shift_backspace_word", true);
    }

    public static void shiftBackspaceWord(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("shift_backspace_word", enabled).apply();
    }

    /**
     * A held Alt + Sym switches to the next keyboard. Off, the keys do what they would without it: Sym opens its panel. Android's
     * own keyboard switcher still changes keyboards, so nobody is stuck. On to start (owner, 2026-11-13: "why is delete a word shift backspace and next keyboard alt sym not toggleable?").
     */
    public static boolean altSymNextKeyboard(Context context) {
        return preferences(context).getBoolean("alt_sym_next_keyboard", true);
    }

    public static void altSymNextKeyboard(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("alt_sym_next_keyboard", enabled).apply();
    }

    /**
     * Settings > Accent colour (#111): "off", "orange" (the default), a preset or "phone", as Accent names them. Before it there
     * was a switch, Orange accents ("orange_accents"): off becomes Off, on stays Orange.
     */
    public static String accentColour(Context context) {
        SharedPreferences preferences = preferences(context);
        String chosen = preferences.getString("accent_colour", null);
        if (chosen != null) return chosen;
        return preferences.getBoolean("orange_accents", true) ? "orange" : "off";
    }

    public static void accentColour(Context context, String id) {
        preferences(context).edit().putString("accent_colour", id).remove("orange_accents").apply();
    }

    /** Any accent at all: every choice but Off. */
    public static boolean accentsOn(Context context) {
        return !"off".equals(accentColour(context));
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

    /**
     * The characters that Settings offers to start a snippet's shortcut, the default first. Each is a symbol that Alt types on
     * the Titan's keys, and none can be part of a shortcut or start a code of its own, as : does for emoji.
     */
    public static final String SNIPPET_STARTS = "/#!?*";
    /** Starts from before 2.1. Someone who picked one keeps it, but Settings offers it to no one else. */
    public static final String OLD_SNIPPET_STARTS = ";\\,";
    private static final String SNIPPET_START = "snippet_start";
    /** Set once the start has been settled after the update to 2.1. See snippetStartAfterUpdate. */
    private static final String SNIPPET_START_SETTLED = "snippet_start_settled";

    /**
     * The character that starts a snippet's shortcut, such as / in /addr. The first read after the update to 2.1 settles it
     * once, by snippetStartAfterUpdate, and saves it.
     */
    public static char snippetStart(Context context) {
        SharedPreferences preferences = preferences(context);
        String start = preferences.getString(SNIPPET_START, null);
        if (!preferences.getBoolean(SNIPPET_START_SETTLED, false)) {
            start = snippetStartAfterUpdate(start, madeSnippets(context));
            preferences.edit().putString(SNIPPET_START, start).putBoolean(SNIPPET_START_SETTLED, true).apply();
        }
        return validSnippetStart(start) ? start.charAt(0) : SNIPPET_STARTS.charAt(0);
    }

    public static void snippetStart(Context context, String start) {
        if (!validSnippetStart(start)) throw new IllegalArgumentException("Not a snippet start");
        preferences(context).edit().putString(SNIPPET_START, start).putBoolean(SNIPPET_START_SETTLED, true).apply();
    }

    /** Whether a start can begin a snippet's shortcut: one that Settings offers, or one from before 2.1. */
    public static boolean validSnippetStart(String start) {
        return start != null && start.length() == 1 && (SNIPPET_STARTS.contains(start) || OLD_SNIPPET_STARTS.contains(start));
    }

    /**
     * The start once 2.1 is installed, decided once. stored is the saved start, or null if the user never picked one, which
     * meant ; before 2.1. Someone who ever made a snippet keeps their start. Someone with ; who never made one gets /, the new
     * default, and so does a new install. No Android here, so the tests can reach it.
     */
    public static String snippetStartAfterUpdate(String stored, boolean madeSnippets) {
        String start = validSnippetStart(stored) ? stored : ";";
        return start.equals(";") && !madeSnippets ? SNIPPET_STARTS.substring(0, 1) : start;
    }

    /** Whether the user ever saved, removed or imported a snippet: Snippets.kt writes its list in "snippets" each time. */
    private static boolean madeSnippets(Context context) {
        return context.getSharedPreferences("snippets", Context.MODE_PRIVATE).contains("list");
    }

    /**
     * Whether one kind of autocorrect fix is on, by its id (FixKind in the autocorrect): Settings > Fix typos as you type > What gets
     * fixed. All are on by default, which is autocorrect as it always was.
     */
    public static boolean fixOn(Context context, String id) {
        // The second look and context apostrophes go back to finished words: Fix earlier words decides them (owner, 2026-11-20).
        // Context apostrophes also keep a switch turned off before 3.0, as that person had it.
        if (SECOND_LOOK.equals(id)) return !EARLIER_OFF.equals(earlierWords(context));
        if (CONTEXT_APOSTROPHES.equals(id)) {
            return !EARLIER_OFF.equals(earlierWords(context)) && preferences(context).getBoolean("fix_" + id, true);
        }
        return preferences(context).getBoolean("fix_" + id, true);
    }

    private static final String SECOND_LOOK = "secondLook";
    private static final String CONTEXT_APOSTROPHES = "contextApostrophes";
    public static final String EARLIER_WORDS = "earlier_words";
    /**
     * Fix earlier words' values, as EarlierWords in the autocorrect has them (values, not keys, so declared together). The
     * setting is [EARLIER_OFF] or [EARLIER_ON] (owner, 2026-11-22: "change the fix earlier words to be only off and bold
     * (renamed to be off and on)"). How far On goes is [EARLIER_SMALL] with Only fix when sure, else [EARLIER_BOLD]: see
     * [earlierLevel]. Small: only a slip on a rare word or a short non-word, or an apostrophe. Bold: also guesses what was meant
     * (their to they're, are to were, a changed or redone fix).
     */
    public static final String EARLIER_OFF = "off", EARLIER_ON = "on", EARLIER_SMALL = "small", EARLIER_BOLD = "bold";

    /**
     * Fix earlier words, as Settings > What gets fixed shows it: [EARLIER_OFF] or [EARLIER_ON], On by default. The Small and
     * Bold that 3.0's first candidates stored are both On now. From before 3.0, the Second look switch off is Off.
     */
    public static String earlierWords(Context context) {
        String stored = preferences(context).getString(EARLIER_WORDS, null);
        if (EARLIER_OFF.equals(stored)) return EARLIER_OFF;
        if (stored != null) return EARLIER_ON;
        return preferences(context).getBoolean("fix_" + SECOND_LOOK, true) ? EARLIER_ON : EARLIER_OFF;
    }

    public static void earlierWords(Context context, String value) {
        if (!EARLIER_OFF.equals(value) && !EARLIER_ON.equals(value)) {
            throw new IllegalArgumentException("Fix earlier words is off or on: " + value);
        }
        preferences(context).edit().putString(EARLIER_WORDS, value).apply();
    }

    /**
     * How far autocorrect goes back to a finished word, for EarlierWords: Off, or with it on, the small fixes when Only fix when
     * sure is on (owner, 2026-11-22: "only fix when sure makes fix earlier words use the small fixes settings"), else bold.
     */
    public static String earlierLevel(Context context) {
        if (EARLIER_OFF.equals(earlierWords(context))) return EARLIER_OFF;
        return fixOnlyWhenSure(context) ? EARLIER_SMALL : EARLIER_BOLD;
    }

    /** Whether a Cantonese input method is on, so its romanised syllables count as words (owner, 2026-11-20). */
    public static boolean cantoneseOn(Context context) {
        for (String id : inputMethods(context).split(",")) if (id.trim().startsWith("yue-")) return true;
        return false;
    }

    public static void fixOn(Context context, String id, boolean on) {
        preferences(context).edit().putBoolean("fix_" + id, on).apply();
    }

    /** A fix must be clearly right before it goes in: fewer fixes, and fewer wrong ones. Off by default. */
    public static boolean fixOnlyWhenSure(Context context) {
        return preferences(context).getBoolean("fix_only_when_sure", false);
    }

    public static void fixOnlyWhenSure(Context context, boolean on) {
        preferences(context).edit().putBoolean("fix_only_when_sure", on).apply();
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
        Map<String, String> lower = lowercaseShorthand(own);
        if (!lower.equals(own)) {
            // Shorthand saved with capitals: repaired once, the first time it's read, and kept in lowercase from then on.
            entries = shorthandEntries(lower);
            preferences(context).edit().putStringSet(OWN_SHORTHAND, entries).apply();
        }
        ownShorthandRead = entries;
        ownShorthandCache = Collections.unmodifiableMap(lower);
        return ownShorthandCache;
    }

    /**
     * Saves the user's own shorthand, which the caller has checked, in lowercase whatever was typed: "HW" saves as "hw". Its
     * full word stays as typed. [full] null removes it.
     */
    public static synchronized void ownShorthand(Context context, String shorthand, String full) {
        Map<String, String> own = new TreeMap<>(ownShorthand(context));
        if (full == null) own.remove(shorthandKey(shorthand)); else own.put(shorthandKey(shorthand), full);
        preferences(context).edit().putStringSet(OWN_SHORTHAND, shorthandEntries(own)).apply();
    }

    /** How a shorthand is kept and looked up: in lowercase, with a curly apostrophe as a straight one, as Shorthand.key. */
    public static String shorthandKey(String shorthand) {
        return shorthand.toLowerCase(Locale.ROOT).replace('’', '\'');
    }

    /**
     * The user's own shorthand with every shorthand in lowercase, so "HW" is kept as "hw". Where that makes two the same, the
     * one already in lowercase wins, as it's the one typing found; with none in lowercase, the first in sorted order does.
     */
    public static Map<String, String> lowercaseShorthand(Map<String, String> own) {
        Map<String, String> lower = new TreeMap<>();
        for (Map.Entry<String, String> entry : own.entrySet())
            if (shorthandKey(entry.getKey()).equals(entry.getKey())) lower.put(entry.getKey(), entry.getValue());
        for (Map.Entry<String, String> entry : new TreeMap<>(own).entrySet())
            lower.putIfAbsent(shorthandKey(entry.getKey()), entry.getValue());
        return lower;
    }

    private static Set<String> shorthandEntries(Map<String, String> own) {
        Set<String> entries = new HashSet<>();
        for (Map.Entry<String, String> entry : own.entrySet()) entries.add(entry.getKey() + "\t" + entry.getValue());
        return entries;
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

    /**
     * Records touches, keys and typed text in a log on this phone. Only TitanKeys Debug has the log (RawLogGate), and there it's on
     * from the start and stays on, with no time limit, until the owner turns it off (owner, 2026-11-17: "for all future debugging
     * builds let's have log on by default indefinitely"). Its 200 MB cap drops the oldest data, never the logging (RawLogStore).
     */
    public static boolean rawLog(Context context) {
        return preferences(context).getBoolean("raw_log", context.getPackageName().endsWith(".debug"));
    }

    public static void rawLog(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("raw_log", enabled).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("typing_settings", Context.MODE_PRIVATE);
    }

    public static EnglishSpelling.Variant spelling(Context context) {
        return EnglishSpelling.Variant.of(preferences(context).getString("spelling", "american"));
    }

    public static void spelling(Context context, EnglishSpelling.Variant variant) {
        preferences(context).edit().putString("spelling", variant.value()).apply();
    }

    /**
     * The input methods that are on, in the overlay's order, as ids with commas between (docs/languages.md, "Settings >
     * Languages"). English alone by default.
     */
    public static String inputMethods(Context context) {
        return preferences(context).getString(INPUT_METHODS, "en");
    }

    public static void inputMethods(Context context, String ids) {
        preferences(context).edit().putString(INPUT_METHODS, ids).apply();
    }

    /** The input method the keyboard types with now, picked in the overlay on a held Sym. */
    public static String currentInputMethod(Context context) {
        return preferences(context).getString("current_input_method", "en");
    }

    public static void currentInputMethod(Context context, String id) {
        preferences(context).edit().putString("current_input_method", id).apply();
    }

    /**
     * The Chinese variant for every Chinese input method: zh_hk, zh_tw or zh_hans, or empty until the user picks one. There's no
     * default (owner, 2026-11-16): the user chooses.
     */
    public static String chineseVariant(Context context) {
        return preferences(context).getString("chinese_variant", "");
    }

    public static void chineseVariant(Context context, String variant) {
        preferences(context).edit().putString("chinese_variant", variant).apply();
    }

    /**
     * Which spelling variant each input method with several types (Portuguese's Brasil or Portugal): "id=variant" pairs,
     * InputMethodVariants' format. Empty: each types its first.
     */
    public static String inputMethodVariants(Context context) {
        return preferences(context).getString("input_method_variants", "");
    }

    public static void inputMethodVariants(Context context, String setting) {
        preferences(context).edit().putString("input_method_variants", setting).apply();
    }

    /**
     * Yale's and Jyutping's lazy sounds, per input method [id]: n and l, j and z, ng and no initial, gw as g. On by
     * default; off types the strict spelling only (RimeEngine.LAZY_SOUNDS).
     */
    public static boolean lazySounds(Context context, String id) {
        return preferences(context).getBoolean("lazy_sounds_" + id, true);
    }

    public static void lazySounds(Context context, String id, boolean on) {
        preferences(context).edit().putBoolean("lazy_sounds_" + id, on).apply();
    }

    /** A resting finger can show the current input method's key legend. Keep existing shape-method choices on upgrade. */
    public static boolean keyPreview(Context context, String id, boolean defaultOn) {
        SharedPreferences prefs = preferences(context);
        String setting = "key_preview_" + id;
        String old = "radical_preview_" + id;
        if (prefs.contains(setting)) return prefs.getBoolean(setting, defaultOn);
        if (!prefs.contains(old)) return defaultOn;
        boolean on = prefs.getBoolean(old, defaultOn);
        prefs.edit().putBoolean(setting, on).remove(old).apply();
        return on;
    }

    public static void setKeyPreview(Context context, String id, boolean on) {
        preferences(context).edit().putBoolean("key_preview_" + id, on).remove("radical_preview_" + id).apply();
    }

    /** Whether input method [id] has said its hold hint on the bar (HoldHint): once, the first time it types. */
    public static boolean holdHintShown(Context context, String id) {
        return preferences(context).getStringSet("hold_hints_shown", java.util.Collections.emptySet()).contains(id);
    }

    public static void holdHintShown(Context context, String id, boolean shown) {
        java.util.Set<String> ids = new java.util.HashSet<>(preferences(context).getStringSet("hold_hints_shown", java.util.Collections.emptySet()));
        if (shown) ids.add(id); else ids.remove(id);
        preferences(context).edit().putStringSet("hold_hints_shown", ids).apply();
    }

    /** Whether the keyboard has opened Settings' Chinese page because no variant was picked: it does that once (ChineseSetup). */
    public static boolean chineseVariantShown(Context context) {
        return preferences(context).getBoolean("chinese_variant_shown", false);
    }

    public static void chineseVariantShown(Context context, boolean shown) {
        preferences(context).edit().putBoolean("chinese_variant_shown", shown).apply();
    }

    /**
     * Whether Chinese input methods type plain ASCII punctuation (, . ?) instead of Chinese full-width marks (，。？), which are
     * the default (docs/languages.md, "Chinese on the bar").
     */
    public static boolean chinesePlainPunctuation(Context context) {
        return preferences(context).getBoolean("chinese_plain_punctuation", false);
    }

    public static void chinesePlainPunctuation(Context context, boolean plain) {
        preferences(context).edit().putBoolean("chinese_plain_punctuation", plain).apply();
    }

    private TypingSettings() {}
}
