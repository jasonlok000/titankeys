package com.atlas.engine;

import java.util.Locale;

/**
 * The case a word is kept in when it is saved or learned: its own, never one that the sentence start, Shift or Caps Lock gave it
 * (owner, 2026-11-13: "why is And kept on auto correcting to cap? And AUTO too?"). A word kept with capitals is a word with its
 * own capitals, such as a name, and autocorrect writes it that way everywhere; so "And", saved from a sentence start, turned every
 * "and" into "And".
 */
public final class WordCase {
    private WordCase() {}

    /**
     * [word] as it is kept. All capitals ("AUTO") become lowercase, and so does a capital on its first letter alone when the word
     * stood at a sentence start ([before] is the text before it) or Shift was on for it ([shifted]). Any other capitals are the
     * word's own and stay: "Jason" in the middle of a sentence, "iPhone" anywhere. Null [before] counts as a sentence start.
     */
    public static String kept(String word, CharSequence before, boolean shifted) {
        int letters = 0, upper = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (!Character.isLetter(c)) continue;
            letters++;
            if (Character.isUpperCase(c)) upper++;
        }
        String lower = word.toLowerCase(Locale.ROOT);
        if (letters >= 2 && upper == letters) return lower;
        boolean firstOnly = upper == 1 && letters > 0 && Character.isUpperCase(firstLetter(word));
        if (firstOnly && (shifted || before == null || SentenceCase.startsSentence(before))) return lower;
        return word;
    }

    private static char firstLetter(String word) {
        for (int i = 0; i < word.length(); i++) if (Character.isLetter(word.charAt(i))) return word.charAt(i);
        return ' ';
    }
}
