package com.atlas.engine;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * What holding a word on the strip does to it. A word that TitanKeys learned from you leaves Saved words, and any word that can be
 * is blocked, so it is not suggested, swiped or used as a fix. Both can be undone in Settings, or with Save in the word menu.
 */
public final class ForgetWords {
    public enum Kind { NONE, REMOVE, BLOCK }

    private ForgetWords() {}

    /**
     * [builtIn] is whether the built-in word list has the word, and [saved] whether Saved words has it. A built-in word is
     * blocked even when it is also saved, because removing it would not stop it.
     */
    public static Kind kind(String word, boolean builtIn, boolean saved) {
        if (word == null || word.trim().isEmpty()) return Kind.NONE;
        if (!builtIn && saved) return Kind.REMOVE;
        return BlockedWords.valid(word) ? Kind.BLOCK : saved ? Kind.REMOVE : Kind.NONE;
    }

    /** Whether Forget takes [word] out of Saved words: any saved word that can be forgotten, built in or not. */
    public static boolean removes(String word, boolean builtIn, boolean saved) {
        return saved && kind(word, builtIn, saved) != Kind.NONE;
    }

    /**
     * Whether Forget blocks [word]: any word that can be blocked, saved or not, so it is gone for good and typing it again can't
     * learn it back (owner, 2026-11-12). Save lets it back in.
     */
    public static boolean blocks(String word, boolean builtIn, boolean saved) {
        return kind(word, builtIn, saved) != Kind.NONE && BlockedWords.valid(word);
    }

    /**
     * Whether [word] on the strip was a fix for the letters [typed], and not a completion of them, as "the" is for "teh". A
     * forgotten fix is also refused for those letters, so autocorrect does not make it again.
     */
    public static boolean fix(String typed, String word) {
        if (typed == null || word == null) return false;
        String letters = key(typed);
        String candidate = key(word);
        return !letters.isEmpty() && !candidate.isEmpty() && !candidate.startsWith(letters);
    }

    static String key(String word) {
        return Normalizer.normalize(word.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('’', '\'');
    }

    /** Fixes that autocorrect must not make again: "do not change these letters to this word". Immutable and case-insensitive. */
    public static final class RefusedFixes {
        /** Enough for years of use. Past it, an old entry makes room for the new one. */
        static final int LIMIT = 500;
        private static final char SEPARATOR = '\t';
        private final Set<String> entries;

        public RefusedFixes(Collection<String> stored) {
            Set<String> valid = new HashSet<>();
            for (String entry : stored) if (entry.indexOf(SEPARATOR) > 0) valid.add(entry);
            entries = Set.copyOf(valid);
        }

        public RefusedFixes add(String typed, String fix) {
            String entry = key(typed) + SEPARATOR + key(fix);
            if (entries.contains(entry)) return this;
            Set<String> replacement = new HashSet<>(entries);
            while (replacement.size() >= LIMIT) replacement.remove(replacement.iterator().next());
            replacement.add(entry);
            return new RefusedFixes(replacement);
        }

        /** Forgets every refusal to change anything to [fix], because the word itself is back. */
        public RefusedFixes withoutFix(String fix) {
            String ending = SEPARATOR + key(fix);
            Set<String> replacement = new HashSet<>(entries);
            if (!replacement.removeIf(entry -> entry.endsWith(ending))) return this;
            return new RefusedFixes(replacement);
        }

        /** Whether autocorrect may change [typed] to [fix]. */
        public boolean allowed(String typed, String fix) {
            return entries.isEmpty() || typed == null || fix == null || !entries.contains(key(typed) + SEPARATOR + key(fix));
        }

        public Set<String> entries() { return entries; }
    }
}
