package com.atlas.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Restores names from the model vocabulary without capitalising ordinary homographs. */
final class ProperNames {
    private static volatile Map<String, String> spellings = Collections.emptyMap();

    static void load(Reader source) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        Map<String, String> names = new HashMap<>();
        Set<String> ordinary = new HashSet<>();
        String word;
        while ((word = reader.readLine()) != null) {
            String lower = word.toLowerCase(Locale.ROOT);
            String key = lower.replace('\u2019', '\'');
            if (word.equals(lower)) ordinary.add(key);
            else names.putIfAbsent(key, word);
        }
        names.keySet().removeAll(ordinary);
        spellings = names;
    }

    static String restore(String word) {
        if (!word.equals(word.toLowerCase(Locale.ROOT))) return word;
        String key = word.replace('\u2019', '\'');
        String builtIn = BuiltInWords.spelling(key);
        if (builtIn != null) return builtIn;
        String spelling = spellings.get(key);
        if (spelling == null && key.endsWith("'s")) {
            String name = spellings.get(key.substring(0, key.length() - 2));
            if (name != null) spelling = name + "'s";
        }
        if (spelling == null) return word;
        return word.indexOf('\u2019') >= 0 ? spelling.replace('\'', '\u2019') : spelling;
    }

    private ProperNames() {}
}
