package com.atlas.engine;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

final class SwipeSpelling {
    private static final Map<String, String> CONTRACTIONS = new HashMap<>();
    private static final Map<String, Float> SHARED_LETTERS = new HashMap<>();
    private static final Map<String, String> LOOK_ALIKES = new HashMap<>();

    static {
        String[] contractions = {
            "ain't", "aren't", "can't", "couldn't", "didn't", "doesn't", "don't",
            "hadn't", "hasn't", "haven't", "isn't", "mustn't", "needn't", "oughtn't",
            "shan't", "shouldn't", "wasn't", "weren't", "won't", "wouldn't",
            "I'm", "I've", "you'd", "you'll", "you're", "you've", "we've",
            "they'd", "they'll", "they're", "they've", "he's", "she's",
            "that'll", "that's", "there'll", "there're", "there's", "there've",
            "what'll", "what're", "what's", "what've", "when's", "where's",
            "who'll", "who's", "who've", "why's", "how's", "it'll", "he'd", "who'd", "that'd",
            "o'clock", "y'all", "ma'am", "here's", "could've", "should've", "would've", "might've", "must've"
        };
        for (String word : contractions) {
            CONTRACTIONS.put(word.replace("'", "").toLowerCase(Locale.ROOT), word);
        }
        // These share their letters with another word, so context chooses. Dictionary frequencies keep the usual order.
        SHARED_LETTERS.put("it's", 162f);
        SHARED_LETTERS.put("i'll", 164f);
        SHARED_LETTERS.put("i'd", 136f);
        SHARED_LETTERS.put("let's", 145f);
        SHARED_LETTERS.put("we're", 106f);
        SHARED_LETTERS.put("we'll", 94f);
        SHARED_LETTERS.put("we'd", 89f);
        SHARED_LETTERS.put("he'll", 94f);
        SHARED_LETTERS.put("she'll", 84f);
        SHARED_LETTERS.put("she'd", 92f);
        String[][] lookAlikes = {
            {"were", "we're"}, {"its", "it's"}, {"well", "we'll"}, {"ill", "i'll"}, {"hell", "he'll"},
            {"shell", "she'll"}, {"lets", "let's"}, {"shed", "she'd"}, {"wed", "we'd"}, {"id", "i'd"}
        };
        for (String[] pair : lookAlikes) {
            LOOK_ALIKES.put(pair[0], pair[1]);
            LOOK_ALIKES.put(pair[1], pair[0]);
        }
    }

    /** Lowercase spellings to give the decoder, so the context model scores "don't" rather than "dont". */
    static Map<String, Float> contractions(ToDoubleFunction<String> frequency) {
        Map<String, Float> spellings = new HashMap<>(SHARED_LETTERS);
        for (Map.Entry<String, String> entry : CONTRACTIONS.entrySet()) {
            float known = (float) frequency.applyAsDouble(entry.getKey());
            if (known >= 1) spellings.put(entry.getValue().toLowerCase(Locale.ROOT), Math.min(255, known));
        }
        return spellings;
    }

    /** Whether these letters or this spelling can only be a contraction, as "dont" and "don't" can. */
    static boolean onlyContraction(String word) {
        return CONTRACTIONS.containsKey(word.replace("'", ""));
    }

    /** The lowercase contraction for letters that can only be a contraction, otherwise the same word. */
    static String contraction(String word) {
        String spelling = CONTRACTIONS.get(word);
        return spelling == null ? word : spelling.toLowerCase(Locale.ROOT);
    }

    /** The word spelled with the same letters, as "were" and "we're" are, or null if there is none. */
    static String lookAlike(String word) {
        return LOOK_ALIKES.get(word.replace('\u2019', '\'').toLowerCase(Locale.ROOT));
    }

    static String restore(String word) {
        String spelling = CONTRACTIONS.get(word);
        return ProperNames.restore(spelling == null ? word : spelling);
    }

    private SwipeSpelling() {}
}
