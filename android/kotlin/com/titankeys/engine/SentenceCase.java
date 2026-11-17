package com.titankeys.engine;

import java.util.Locale;
import java.util.Set;

/** Capitalises the first letter of each sentence. */
public final class SentenceCase {
    /** Full-width marks, which take no space after them, end a sentence in every language. */
    private static final String FULL_WIDTH = "。！？";
    /**
     * The marks that end a sentence in the input method typing now, its im.json's sentenceEnd: Greek's ; is its question mark,
     * Hindi's । its full stop. The keyboard sets it as the input method changes ({@link #ends(String)}).
     */
    private static volatile String ends = ".!?";

    /** Marks that open a sentence in the input method typing now, its im.json's openingMarks: Spanish's ¿ and ¡. */
    private static volatile String openers = "";

    /** The current input method's sentence ends, or English's where it gives none, and its opening marks. */
    public static void ends(String marks, String opening) {
        ends = marks == null || marks.isEmpty() ? ".!?" : marks;
        openers = opening == null ? "" : opening;
    }

    /** Whether [mark] ends a sentence in the input method typing now. */
    public static boolean isEnd(int mark) { return ends.indexOf(mark) >= 0 || FULL_WIDTH.indexOf(mark) >= 0; }

    /** A sentence end that takes a space after it, as . and ; in Greek do: the full-width ones don't. */
    public static boolean isSpacedEnd(int mark) { return ends.indexOf(mark) >= 0 && FULL_WIDTH.indexOf(mark) < 0; }

    // A full stop after these does not end the sentence.
    private static final Set<String> ABBREVIATIONS = Set.of("e.g.", "i.e.", "eg.", "ie.", "vs.", "cf.", "viz.");

    public static String capitalise(String text, String before) {
        StringBuilder result = new StringBuilder(before);
        for (int index = 0; index < text.length();) {
            int character = text.codePointAt(index);
            result.appendCodePoint(startsSentence(result) ? Character.toTitleCase(character) : character);
            index += Character.charCount(character);
        }
        return result.substring(before.length());
    }

    /** A full stop must have a space after it, so "e.g" and "atlas.com" stay in lowercase. */
    static boolean startsSentence(CharSequence before) {
        boolean spaced = false;
        for (int end = before.length(); end > 0;) {
            int previous = Character.codePointBefore(before, end);
            if (lineBreak(previous)) return true;
            // Some fields, like WhatsApp search, start with an invisible character such as a zero-width space.
            if (Character.getType(previous) == Character.FORMAT) {
                end -= Character.charCount(previous);
            } else if (openers.indexOf(previous) >= 0) {
                // An opening mark starts the sentence it opens: "¿qué" gets its capital after the ¿.
                end -= Character.charCount(previous);
            } else if (Character.isWhitespace(previous)) {
                spaced = true;
                end -= Character.charCount(previous);
            } else {
                // Full-width marks do not take a space.
                if (FULL_WIDTH.indexOf(previous) >= 0) return true;
                return isSpacedEnd(previous) && spaced && !abbreviation(before, end);
            }
        }
        return true;
    }

    private static boolean abbreviation(CharSequence before, int end) {
        int start = end;
        while (start > 0 && !Character.isWhitespace(before.charAt(start - 1))) start--;
        return ABBREVIATIONS.contains(before.subSequence(start, end).toString().toLowerCase(Locale.ROOT));
    }

    private static boolean lineBreak(int character) {
        return "\n\r  ".indexOf(character) >= 0;
    }

    private SentenceCase() {}
}
