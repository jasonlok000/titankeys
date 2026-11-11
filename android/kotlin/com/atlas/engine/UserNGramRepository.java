package com.atlas.engine;

// Copied from Numen (GPL-3.0, https://github.com/jlo-aug/numen, commit 7b1391c), which is a modified version of Pastiera.
// Numen's version is Kotlin and has a table for each language. TitanKeys is English only.

import java.util.List;

/** Where the word pairs that the user kept are stored. */
interface UserNGramRepository {
    final class Prediction {
        final String word;
        final int count;
        final long lastUsed;

        Prediction(String word, int count, long lastUsed) {
            this.word = word; this.count = count; this.lastUsed = lastUsed;
        }
    }

    void learn(String prefix, String nextWord, long nowMs);
    List<Prediction> predict(String prefix, int limit);
    /**
     * How often each word that starts with {@code letters} was kept, the most first, in lower case. Every kept word is
     * stored as the second word of a pair, so this adds up its pairs.
     */
    java.util.Map<String, Integer> keptStarting(String letters, int limit);
    /** Removes each pair with this word on either side, in any case. {@code prefix} is the form the word takes as a prefix. */
    int deleteWord(String prefix, String nextWord);
    void clearAll();
}
