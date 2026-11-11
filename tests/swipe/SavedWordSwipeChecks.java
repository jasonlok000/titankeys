package com.atlas.engine;

import com.atlas.keyboard.Layout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.futo.ml.inference.SwipeDecoder;

/** Runs the real engine on a device, so saved double-letter words are tested against the real scores. */
public final class SavedWordSwipeChecks {
    private static final int DAY = 20000;
    private static SwipeRecognizer recogniser;

    public static void main(String[] args) {
        // app_process kills the process on an uncaught error and reports only "Killed", so print it here.
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace(System.out);
            System.out.flush();
            System.exit(1);
        });
        recogniser = new SwipeRecognizer(new File(args[0]));
        Swipe swipe = path("hey");

        List<String> unsaved = shown(swipe, List.of(), Set.of());
        check(unsaved.contains("hey") && !unsaved.contains("heyy"), "An unsaved word must not appear: " + unsaved);
        List<String> saved = shown(swipe, List.of("heyy"), Set.of());
        check(saved.get(0).equals("heyy"), "A saved double-letter word must win its single-letter swipe: " + saved);
        check(saved.contains("hey"), "The single-letter twin must remain available: " + saved);
        List<String> blocked = shown(swipe, List.of("heyy"), Set.of("hey", "jerry"));
        check(blocked.get(0).equals("heyy") && blocked.size() == 3 && !blocked.contains("hey") && !blocked.contains("jerry"),
                "Blocked words must leave room for three other suggestions: " + blocked);
        List<String> hidden = shown(swipe, List.of("heyy"), Set.of("heyy"));
        check(!hidden.contains("heyy"), "A blocked saved word must stay hidden: " + hidden);
        String[][] sentences = { {}, { "hi" }, { "i", "love" }, { "this", "is", "a" }, { "hey", "how", "are", "you" } };
        for (String[] sentence : sentences) {
            String words = String.join(" ", sentence);
            List<String> liv = shown(path("liv"), sentence, List.of("liv"), Set.of());
            check(liv.get(0).equals("liv"), "A saved name must not lose to an unknown context score after '" + words + "': " + liv);
            List<String> lib = shown(path("lib"), sentence, List.of("liv"), Set.of());
            check(lib.get(0).equals("lib"), "A swipe of another word must keep that word after '" + words + "': " + lib);
            List<String> live = shown(path("live"), sentence, List.of("liv"), Set.of());
            check(live.get(0).equals("live"), "A longer common word must keep its swipe after '" + words + "': " + live);
        }
        System.out.println("Saved word swipe checks passed");
    }

    private static List<String> shown(Swipe swipe, List<String> saved, Set<String> blocked) {
        return shown(swipe, new String[0], saved, blocked);
    }

    /** What the keyboard offers for a swipe after {@code context}, with these words added in Settings. */
    private static List<String> shown(Swipe swipe, String[] context, List<String> saved, Set<String> blocked) {
        LanguageHabits habits = new LanguageHabits();
        for (String word : saved) habits.add(word, DAY);
        BlockedWords blocks = new BlockedWords(blocked);
        List<LanguageHabits.Entry> active = habits.active(DAY).stream().filter(entry -> blocks.allowed(entry.word)).toList();
        String[] words = active.stream().map(entry -> entry.word).toArray(String[]::new);
        float[] frequencies = new float[words.length];
        for (int i = 0; i < words.length; i++) frequencies[i] = active.get(i).frequency(recogniser.frequency(words[i]), DAY);
        recogniser.personalWords(words, frequencies);
        SwipeDecoder.Result[] results = recogniser.recognise(swipe, context, words.length > 0, blocks::allowed);
        return java.util.Arrays.stream(results).map(result -> result.word).toList();
    }

    private static Swipe path(String letters) {
        List<float[]> points = new ArrayList<>();
        for (int segment = 0; segment < letters.length() - 1; segment++) {
            int from = letters.charAt(segment) - 'a', to = letters.charAt(segment + 1) - 'a';
            for (int step = 0; step < 22; step++) {
                float progress = step / 22f;
                points.add(new float[] { Layout.KEY_X[from] + (Layout.KEY_X[to] - Layout.KEY_X[from]) * progress,
                        Layout.KEY_Y[from] + (Layout.KEY_Y[to] - Layout.KEY_Y[from]) * progress });
            }
        }
        int last = letters.charAt(letters.length() - 1) - 'a';
        points.add(new float[] { Layout.KEY_X[last], Layout.KEY_Y[last] });
        float[] x = new float[points.size()], y = new float[points.size()], time = new float[points.size()];
        for (int i = 0; i < x.length; i++) {
            x[i] = points.get(i)[0];
            y[i] = points.get(i)[1];
            time[i] = i * 8f;
        }
        return new Swipe(x, y, time);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
