package com.atlas.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** How often each word starts a sentence, from the sentence-start lines of autocorrect's word triples. */
final class FirstWords {
    private static final String START = "<s> ";

    /**
     * The score of each word that starts a sentence, from its count added up over the words that follow it. The triples
     * come from everyday sentences. The pairs lean on encyclopedia text and leave out common first words, such as "why" and "I'm".
     */
    static Map<String, Double> read(BufferedReader triples) throws IOException {
        Map<String, Integer> counts = new HashMap<>();
        // The file sorts these lines first, so the reading stops where they end.
        for (String line = triples.readLine(); line != null && (line.startsWith("#") || line.startsWith(START)); line = triples.readLine()) {
            int tab = line.indexOf('\t');
            int space = line.indexOf(' ', START.length());
            if (line.startsWith("#") || tab < 0 || space < 0 || space > tab) continue;
            counts.merge(line.substring(START.length(), space), Integer.parseInt(line.substring(tab + 1).trim()), Integer::sum);
        }
        // Log counts span more than the range of the expected word boost, which would leave a word like "hi" with nothing.
        // So the commonest word takes the whole range, and every other word a share by its log count.
        double most = Math.log1p(counts.values().stream().max(Integer::compare).orElse(0));
        Map<String, Double> scores = new HashMap<>();
        counts.forEach((word, count) -> scores.put(word, SwipeRecognizer.EXPECTED_WORD_RANGE * Math.log1p(count) / most));
        return scores;
    }

    private FirstWords() {}
}
