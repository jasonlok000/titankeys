package com.atlas.engine;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class SwipeSpelling {
    private static final Map<String, String> CONTRACTIONS = new HashMap<>();

    static {
        String[] contractions = {
            "ain't", "aren't", "can't", "couldn't", "didn't", "doesn't", "don't",
            "hadn't", "hasn't", "haven't", "isn't", "mustn't", "needn't", "oughtn't",
            "shan't", "shouldn't", "wasn't", "weren't", "won't", "wouldn't",
            "I'm", "I've", "you'd", "you'll", "you're", "you've", "we've",
            "they'd", "they'll", "they're", "they've", "he's", "she's",
            "that'll", "that's", "there'll", "there're", "there's", "there've",
            "what'll", "what're", "what's", "what've", "when's", "where's",
            "who'll", "who's", "who've", "why's", "how's"
        };
        for (String word : contractions) {
            CONTRACTIONS.put(word.replace("'", "").toLowerCase(Locale.ROOT), word);
        }
    }

    static String restore(String word) {
        String spelling = CONTRACTIONS.get(word);
        return ProperNames.restore(spelling == null ? word : spelling);
    }

    private SwipeSpelling() {}
}
