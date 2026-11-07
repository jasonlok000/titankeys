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
            String word = line.equalsIgnoreCase("a") || line.equalsIgnoreCase("i")
                    ? line.toLowerCase(Locale.ROOT) : SwipeWords.normalise(line);
            if (!word.isEmpty() && SuggestionPolicy.allowed(word)) vocabulary.putIfAbsent(word, frequency.applyAsDouble(word));
        }
    }

    public static String prefix(String before) {
        int start = before.length();
        while (start > 0 && (Character.isLetter(before.charAt(start - 1))
                || before.charAt(start - 1) == '\'' || before.charAt(start - 1) == '\u2019')) start--;
        return before.substring(start);
    }

    String[] suggest(String prefix, List<LanguageHabits.Entry> personal, int today, Predicate<String> allowed) {
        String lower = prefix.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        Map<String, Double> matches = new LinkedHashMap<>();
        Map<String, Double> similar = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : vocabulary.entrySet())
            if (entry.getKey().startsWith(lower) && allowed.test(entry.getKey())) matches.put(entry.getKey(), entry.getValue());
        for (LanguageHabits.Entry entry : personal) {
            if (!entry.active(today) || !SuggestionPolicy.allowed(entry.word) || !allowed.test(entry.word)) continue;
            double frequency = entry.frequency(vocabulary.getOrDefault(entry.word, 0d).floatValue(), today);
            if (entry.word.startsWith(lower)) matches.merge(entry.word, frequency + PERSONAL_COMPLETION_BOOST, Math::max);
            else if (entry.support(today) >= SPELLING_SUPPORT && similar(lower, entry.word)) similar.put(entry.word, frequency);
        }
        Comparator<Map.Entry<String, Double>> ranking = Comparator
                .<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey);
        List<String> result = matches.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Double>>comparingInt(entry -> entry.getKey().equals(lower) ? 0 : 1)
                        .thenComparing(ranking))
                .limit(8).map(Map.Entry::getKey).collect(Collectors.toCollection(ArrayList::new));
        similar.entrySet().stream().sorted(ranking).findFirst()
                .ifPresent(entry -> result.add(Math.min(2, result.size()), entry.getKey()));
        return result.stream().limit(8).toArray(String[]::new);
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
