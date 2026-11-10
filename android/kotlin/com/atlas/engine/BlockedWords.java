package com.atlas.engine;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** An immutable, case-insensitive list shared by settings, predictions and learning. */
final class BlockedWords {
    private final Set<String> words;

    BlockedWords(Collection<String> words) {
        Set<String> normalized = new HashSet<>();
        for (String word : words) normalized.add(key(word));
        this.words = Set.copyOf(normalized);
    }

    /** Whether the list can hold this word: one word with 1 to 24 letters, and apostrophes. */
    static boolean valid(String word) {
        String normalized = key(word);
        int letters = normalized.replace("'", "").length();
        return letters >= 1 && letters <= 24 && normalized.matches("[a-z]+('[a-z]+)*");
    }

    BlockedWords add(String word) {
        String normalized = key(word);
        if (!valid(word))
            throw new IllegalArgumentException("Enter one word with 1–24 letters. Apostrophes are allowed.");
        Set<String> replacement = new HashSet<>(words);
        replacement.add(normalized);
        return new BlockedWords(replacement);
    }

    BlockedWords remove(String word) {
        Set<String> replacement = new HashSet<>(words);
        replacement.remove(key(word));
        return new BlockedWords(replacement);
    }

    boolean contains(String word) { return !words.isEmpty() && words.contains(key(word)); }
    boolean allowed(String word) { return SuggestionPolicy.allowed(word) && !contains(word); }
    String[] filter(String[] candidates) { return Arrays.stream(candidates).filter(this::allowed).toArray(String[]::new); }
    Set<String> entries() { return words; }
    String[] words() { return words.stream().sorted().toArray(String[]::new); }

    private static String key(String word) {
        return Normalizer.normalize(word.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('\u2019', '\'');
    }
}
