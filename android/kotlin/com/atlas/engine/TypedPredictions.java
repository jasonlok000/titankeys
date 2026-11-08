package com.atlas.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Dictionary-backed completions and spelling fixes for physical typing; never rewrites text automatically. */
public final class TypedPredictions {
    private static final int PERSONAL_COMPLETION_BOOST = 32;
    private static final int SPELLING_SUPPORT = 5;
    private static final int CORRECTION_LENGTH = 3;
    private static final int CORRECTIONS = 3;
    /** A fix goes before the completions only when it is clearly more common than all of them. */
    private static final int CORRECTION_LEAD = 40;
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
        addCorrections(result, lower, allowed, context, frequency);
        return result.stream().limit(8).toArray(String[]::new);
    }

    /** Known words one typo away, as "the" is from "teh", for a typed word the dictionary does not know. */
    private void addCorrections(List<String> result, String lower, Predicate<String> allowed, Map<String, Double> context,
                                Comparator<Map.Entry<String, Double>> frequency) {
        if (lower.length() < CORRECTION_LENGTH || vocabulary.containsKey(lower)) return;
        int limit = lower.length() >= 7 ? 2 : 1;
        Map<String, Integer> distances = new HashMap<>();
        Map<String, Double> found = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : vocabulary.entrySet()) {
            String word = entry.getKey();
            if (word.length() < 2 || result.contains(word)) continue;
            int distance = distance(lower, word, limit);
            if (distance > limit || !sameStart(lower, word) || !allowed.test(word)) continue;
            distances.put(word, distance);
            found.put(word, entry.getValue());
        }
        Comparator<Map.Entry<String, Double>> likely = Comparator
                .<Map.Entry<String, Double>>comparingInt(entry -> distances.get(entry.getKey()))
                .thenComparing(Comparator.<Map.Entry<String, Double>>comparingDouble(
                        entry -> context.getOrDefault(entry.getKey(), Double.NEGATIVE_INFINITY)).reversed())
                .thenComparing(frequency);
        List<Map.Entry<String, Double>> fixes = found.entrySet().stream().sorted(likely).limit(CORRECTIONS).toList();
        if (fixes.isEmpty()) return;
        if (result.isEmpty()) {
            fixes.forEach(entry -> result.add(entry.getKey()));
            return;
        }
        // An unfinished word such as "thi" keeps its completions in view, so only one fix shares the visible slots.
        double best = result.stream().mapToDouble(word -> vocabulary.getOrDefault(word, 0d)).max().orElse(0);
        String fix = fixes.get(0).getKey();
        result.add(fixes.get(0).getValue() >= best + CORRECTION_LEAD ? 0 : 1, fix);
        fixes.stream().skip(1).forEach(entry -> result.add(entry.getKey()));
    }

    /** Typos rarely change the first letter, so a fix keeps it, unless the first two letters swapped as in "hte". */
    private static boolean sameStart(String typed, String word) {
        return typed.charAt(0) == word.charAt(0) || typed.charAt(0) == word.charAt(1) && typed.charAt(1) == word.charAt(0);
    }

    /** Edits between two words, counting a swap of neighbouring letters as one; anything over {@code limit} is limit + 1. */
    static int distance(String a, String b, int limit) {
        if (Math.abs(a.length() - b.length()) > limit) return limit + 1;
        int[] twoBack = new int[b.length() + 1], previous = new int[b.length() + 1], next = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            next[0] = i;
            int lowest = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int value = Math.min(Math.min(next[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1))
                    value = Math.min(value, twoBack[j - 2] + 1);
                next[j] = value;
                lowest = Math.min(lowest, value);
            }
            if (lowest > limit) return limit + 1;
            int[] spare = twoBack; twoBack = previous; previous = next; next = spare;
        }
        return Math.min(previous[b.length()], limit + 1);
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
