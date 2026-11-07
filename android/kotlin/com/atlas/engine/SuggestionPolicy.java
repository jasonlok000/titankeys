package com.atlas.engine;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/** Words that must never be offered or learned as predictions. */
final class SuggestionPolicy {
    private static final Pattern BLOCKED = Pattern.compile("(?:^|[^\\p{L}])nigger(?:s|'s|s')?(?:$|[^\\p{L}])");

    static boolean allowed(String word) {
        String normalized = Normalizer.normalize(word, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        return !BLOCKED.matcher(normalized).find();
    }

    static String[] filter(String[] words) {
        return Arrays.stream(words).filter(SuggestionPolicy::allowed).toArray(String[]::new);
    }

    private SuggestionPolicy() {}
}
