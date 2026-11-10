package com.atlas.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** How often each word starts a sentence, from the sentence-start lines of autocorrect's word triples. */
final class FirstWords {
    private static final String START = "<s> ";
    /** The bar shows three. The rest take the place of any that the user has blocked. */
    private static final int STARTS = 8;
    /** The counts come from example sentences, which rarely greet anyone, so a greeting keeps the last place on the bar. */
    private static final String GREETING = "hello";
    private static final int GREETING_PLACE = 2;
    /** Example sentences are mostly about other people, which is not how most messages start. */
    private static final Set<String> NOT_STARTS = Set.of("he", "she");

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

    /**
     * The commonest ways to start a sentence, best first and as they are written inside one. Empty if there are no counts.
     * {@code known} says which words the word list holds, because the example sentences are full of names such as "Sami".
     */
    static List<String> popular(Map<String, Double> scores, Predicate<String> known) {
        List<String> starts = scores.entrySet().stream()
                .filter(entry -> known.test(entry.getKey()) && !NOT_STARTS.contains(entry.getKey()))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> written(entry.getKey()))
                .filter(Objects::nonNull)
                .limit(STARTS)
                .collect(Collectors.toCollection(ArrayList::new));
        if (starts.isEmpty()) return starts;
        starts.remove(GREETING);
        starts.add(Math.min(GREETING_PLACE, starts.size()), GREETING);
        return starts;
    }

    /** The word as it is written in the middle of a sentence, or null for a name. The word list has no lowercase "I". */
    private static String written(String word) {
        if (word.equals("i") || word.startsWith("i'")) return "I" + word.substring(1);
        return ProperNames.restore(word).equals(word) ? word : null;
    }

    private FirstWords() {}
}
