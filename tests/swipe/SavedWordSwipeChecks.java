package com.atlas.engine;

import com.atlas.keyboard.Layout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.futo.ml.inference.NativeDictionary;
import org.futo.ml.inference.SwipeDecoder;

/** Runs the real engine on a device, so saved double-letter words are tested against the real scores. */
public final class SavedWordSwipeChecks {
    private static NativeDictionary base;
    private static SwipeDecoder decoder;
    private static NativeDictionary contractions;
    private static NativeDictionary doubleLetters;

    public static void main(String[] args) {
        // app_process kills the process on an uncaught error and reports only "Killed", so print it here.
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace(System.out);
            System.out.flush();
            System.exit(1);
        });
        base = new NativeDictionary(args[0] + "/english.combined");
        decoder = new SwipeDecoder(args[0]);
        contractions = SwipeRecognizer.contractionDictionary(base);
        doubleLetters = NativeDictionary.doubleLetters(base, SwipeRecognizer.DOUBLE_LETTER_FLOOR);
        Swipe swipe = path("ima");

        check(!shown(swipe, List.of(), Set.of()).contains("imma"), "An unsaved word must not appear");
        check(shown(swipe, List.of("imma"), Set.of()).get(0).equals("imma"),
                "A saved double-letter word must win its single-letter swipe");
        check(shown(swipe, List.of("imma"), Set.of()).contains("ima"),
                "The single-letter twin must remain available");
        List<String> blocked = shown(swipe, List.of("imma"), Set.of("ima", "ina"));
        check(blocked.get(0).equals("imma") && blocked.size() == 3 && !blocked.contains("ima") && !blocked.contains("ina"),
                "Blocked words must leave room for three other suggestions: " + blocked);
        check(!shown(swipe, List.of("imma"), Set.of("imma")).contains("imma"), "A blocked saved word must stay hidden");
        String[][] sentences = { {}, { "hi" }, { "i", "love" }, { "this", "is", "a" }, { "hey", "how", "are", "you" } };
        for (String[] sentence : sentences) {
            String words = String.join(" ", sentence);
            String[] context = SwipeRecognizer.scoringContext(sentence);
            int count = sentence.length;
            List<String> liv = shown(path("liv"), context, count, List.of("liv"), Set.of());
            check(liv.get(0).equals("liv"), "A saved name must not lose to an unknown context score after '" + words + "': " + liv);
            List<String> lib = shown(path("lib"), context, count, List.of("liv"), Set.of());
            check(lib.get(0).equals("lib"), "A swipe of another word must keep that word after '" + words + "': " + lib);
            List<String> live = shown(path("live"), context, count, List.of("liv"), Set.of());
            check(live.get(0).equals("live"), "A longer common word must keep its swipe after '" + words + "': " + live);
        }
        System.out.println("Saved word swipe checks passed");
    }

    private static List<String> shown(Swipe swipe, List<String> saved, Set<String> blocked) {
        return shown(swipe, new String[0], 0, saved, blocked);
    }

    /** {@code context} is what the engine scores, and {@code contextWords} how many words the user typed. */
    private static List<String> shown(Swipe swipe, String[] context, int contextWords, List<String> saved, Set<String> blocked) {
        float[] frequencies = new float[saved.size()];
        for (int i = 0; i < frequencies.length; i++) {
            float known = base.frequency(saved.get(i));
            frequencies[i] = (known > 0 ? known : 96) + 8;
        }
        BlockedWords blocks = new BlockedWords(blocked);
        String[] active = saved.stream().filter(blocks::allowed).toArray(String[]::new);
        float[] activeFrequencies = active.length == saved.size() ? frequencies : new float[0];
        try (NativeDictionary personal = new NativeDictionary(active, activeFrequencies)) {
            decoder.dictionaries(Layout.LETTERS, Layout.KEY_X, Layout.KEY_Y, new long[] { base.pointer(), personal.pointer(), contractions.pointer(), doubleLetters.pointer() });
            Set<String> learned = Set.copyOf(Arrays.asList(active));
            SwipeDecoder.Result[] results = decoder.recognise(swipe.x, swipe.y, swipe.time, context,
                    new float[] { 1, 1, 1, 1 }, 48);
            results = Arrays.stream(SwipeRecognizer.savedWords(results, learned, decoder.contextWeight()))
                    .filter(result -> blocks.allowed(result.word)).toArray(SwipeDecoder.Result[]::new);
            results = SwipeRecognizer.rank(swipe, results, decoder.contextWeight(), decoder.shapeNormalizer(), contextWords, learned);
            return Arrays.stream(results).map(result -> result.word).toList();
        }
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
