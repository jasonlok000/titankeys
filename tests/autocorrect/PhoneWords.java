package com.atlas.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.ObjIntConsumer;

/**
 * The word list that the keyboard gives autocorrect, built the same way on a computer. The phone reads frequencies
 * from its native dictionary, which joins all spellings of the same letters, so this does that from the same file.
 */
public final class PhoneWords {
    private final TypedPredictions predictions;

    public PhoneWords(Path vocabulary, Path dictionary) throws IOException {
        Map<String, Integer> frequencies = new HashMap<>();
        for (String line : Files.readAllLines(dictionary, StandardCharsets.UTF_8)) {
            if (!line.startsWith(" word=")) continue;
            String[] fields = line.trim().split(",");
            if (fields.length < 2 || !fields[1].startsWith("f=")) continue;
            frequencies.merge(letters(fields[0].substring(5)), Integer.parseInt(fields[1].substring(2)), Math::max);
        }
        try (BufferedReader names = Files.newBufferedReader(vocabulary, StandardCharsets.UTF_8)) {
            ProperNames.load(names);
        }
        try (BufferedReader words = Files.newBufferedReader(vocabulary, StandardCharsets.UTF_8);
             BufferedReader combined = Files.newBufferedReader(dictionary, StandardCharsets.UTF_8)) {
            predictions = new TypedPredictions(words, combined, word -> frequencies.getOrDefault(letters(word), 0));
        }
    }

    private static String letters(String word) {
        return word.toLowerCase(Locale.ROOT).replace("'", "").replace("’", "");
    }

    public boolean contains(String word) { return predictions.contains(word); }

    public int frequency(String word) { return predictions.frequencyOf(word); }

    public String name(String word) {
        String spelling = ProperNames.restore(word);
        return spelling.equals(word) ? null : spelling;
    }

    public void forEachWord(ObjIntConsumer<String> action) { predictions.forEachWord(action); }

    public static String spelled(String word, boolean american) {
        return EnglishSpelling.convert(word, american ? EnglishSpelling.Variant.AMERICAN : EnglishSpelling.Variant.BRITISH);
    }

    /** The user's own words, kept the way the phone keeps them. */
    public static final class Habits {
        private static final int DAY = 20000;
        private final LanguageHabits habits = new LanguageHabits();

        public void typed(String word) { habits.typed(word, DAY, true); }

        public void add(String word) { habits.add(word, DAY); }

        /** The saved spelling with capitals, as SwipeTyping.savedSpelling gives it, or null. */
        public String capitals(String word) {
            String spelling = habits.restoreCase(word, DAY);
            return spelling.equals(spelling.toLowerCase(Locale.ROOT)) || !active(word) ? null : spelling;
        }

        /** What the keyboard counts as learned. */
        public boolean active(String word) { return habits.activeWords(DAY).contains(SwipeWords.normalise(word)); }

        /** Whether the word shows in the saved list, as the word strip's check does. */
        public boolean seen(String word) { return Arrays.stream(habits.spellings(DAY)).anyMatch(word::equalsIgnoreCase); }
    }
}
