package com.atlas.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Dictionary-backed completions for physical typing; never rewrites text automatically. */
public final class TypedPredictions {
    private static final int PERSONAL_COMPLETION_BOOST = 32;
    private static final int SPELLING_SUPPORT = 5;
    private final Map<String, Double> vocabulary = new LinkedHashMap<>();

    TypedPredictions(Reader source, ToDoubleFunction<String> frequency) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        String line;
        while ((line = reader.readLine()) != null) {
            String word = key(line);
            if (!word.isEmpty() && SuggestionPolicy.allowed(word)) vocabulary.putIfAbsent(word, frequency.applyAsDouble(word));
        }
    }

    static String key(String word) {
        return word.equalsIgnoreCase("a") || word.equalsIgnoreCase("i")
                ? word.toLowerCase(Locale.ROOT) : SwipeWords.normalise(word);
    }

    boolean contains(String word) {
        return vocabulary.containsKey(word.toLowerCase(Locale.ROOT).replace('\u2019', '\''));
    }

    public static String prefix(String before) {
        int start = before.length();
        while (start > 0 && (Character.isLetter(before.charAt(start - 1))
                || before.charAt(start - 1) == '\'' || before.charAt(start - 1) == '\u2019')) start--;
        return before.substring(start);
    }

    String[] suggest(String prefix, List<LanguageHabits.Entry> personal, int today, Predicate<String> allowed) {
        return suggest(prefix, personal, today, allowed, Map.of());
    }

    /** {@code context} holds the context model's scores for the words before the cursor. Empty means rank by frequency. */
    String[] suggest(String prefix, List<LanguageHabits.Entry> personal, int today, Predicate<String> allowed, Map<String, Double> context) {
        String lower = prefix.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        Map<String, Double> matches = new LinkedHashMap<>();
        Map<String, Double> personalMatches = new LinkedHashMap<>();
        Map<String, Double> similar = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : vocabulary.entrySet())
            if (entry.getKey().startsWith(lower) && allowed.test(entry.getKey())) matches.put(entry.getKey(), entry.getValue());
        for (LanguageHabits.Entry entry : personal) {
            if (!entry.active(today) || !SuggestionPolicy.allowed(entry.word) || !allowed.test(entry.word)) continue;
            double frequency = entry.frequency(vocabulary.getOrDefault(entry.word, 0d).floatValue(), today);
            if (entry.word.startsWith(lower)) personalMatches.put(entry.word, frequency + PERSONAL_COMPLETION_BOOST);
            else if (entry.support(today) >= SPELLING_SUPPORT && similar(lower, entry.word)) similar.put(entry.word, frequency);
        }
        Comparator<Map.Entry<String, Double>> frequency = Comparator
                .<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey);
        List<String> result;
        if (context.isEmpty()) {
            personalMatches.forEach((word, boosted) -> matches.merge(word, boosted, Math::max));
            result = ranked(matches, lower, frequency);
        } else {
            personalMatches.keySet().forEach(matches::remove);
            Comparator<Map.Entry<String, Double>> likely = Comparator
                    .<Map.Entry<String, Double>>comparingDouble(entry -> context.getOrDefault(entry.getKey(), Double.NEGATIVE_INFINITY))
                    .reversed().thenComparing(frequency);
            result = ranked(matches, lower, likely);
            // With no typed letters there is no evidence for a saved word, so it would crowd every bar.
            if (!lower.isEmpty()) personalMatches.entrySet().stream().sorted(frequency).forEach(entry -> {
                int index = entry.getKey().equals(lower) ? 0 : !result.isEmpty() && result.get(0).equals(lower) ? 1 : 0;
                while (index < result.size() && vocabulary.getOrDefault(result.get(index), 0d) >= entry.getValue()) index++;
                result.add(index, entry.getKey());
            });
        }
        similar.entrySet().stream().sorted(frequency).findFirst()
                .ifPresent(entry -> result.add(Math.min(2, result.size()), entry.getKey()));
        return result.stream().limit(8).toArray(String[]::new);
    }

    private static List<String> ranked(Map<String, Double> matches, String lower, Comparator<Map.Entry<String, Double>> ranking) {
        return matches.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Double>>comparingInt(entry -> entry.getKey().equals(lower) ? 0 : 1)
                        .thenComparing(ranking))
                .limit(8).map(Map.Entry::getKey).collect(Collectors.toCollection(ArrayList::new));
    }

    private static boolean similar(String typed, String word) {
        if (typed.length() < 2 || typed.length() != word.length() || typed.charAt(0) != word.charAt(0)) return false;
        int differences = 0;
        for (int i = 1; i < typed.length(); i++) {
            if (typed.charAt(i) != word.charAt(i)) {
                if (!Character.isLetter(typed.charAt(i)) || !Character.isLetter(word.charAt(i)) || ++differences > 1) return false;
            }
        }
        return differences == 1;
    }
}
