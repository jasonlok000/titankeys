package com.atlas.engine;

import com.atlas.keyboard.Layout;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.futo.ml.inference.SwipeDecoder;

/** Runs the real context model on a device: contractions on swipes, and context on typed suggestions. */
public final class ContextSwipeChecks {
    private static SwipeRecognizer recogniser;

    /** A swipe ending between w and s, slightly nearer s: how is likelier, and both are over the limit. */
    private static boolean cleanSwipeFollowsContext() {
        int start = Layout.LETTERS.indexOf('h'), w = Layout.LETTERS.indexOf('w'), s = Layout.LETTERS.indexOf('s');
        float endX = Layout.KEY_X[s] + (Layout.KEY_X[w] - Layout.KEY_X[s]) * .4f;
        float endY = Layout.KEY_Y[s] + (Layout.KEY_Y[w] - Layout.KEY_Y[s]) * .4f;
        Swipe gesture = new Swipe(new float[] { Layout.KEY_X[start], endX }, new float[] { Layout.KEY_Y[start], endY }, new float[] { 0, 80 });
        float weight = .6f;
        SwipeDecoder.Result[] results = {
            new SwipeDecoder.Result("how", -5 + weight * 12, -6, 12),
            new SwipeDecoder.Result("his", -5 + weight * 9, -5, 9)
        };
        return SwipeRecognizer.rank(gesture, results, weight, .1126f, 2, Set.of())[0].word.equals("how");
    }

    public static void main(String[] args) throws Exception {
        // app_process kills the process on an uncaught error and reports only "Killed", so print it here.
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace(System.out);
            System.out.flush();
            System.exit(1);
        });
        recogniser = new SwipeRecognizer(new File(args[0]));

        List<String> dont = swipe("dont", "i");
        check(dont.get(0).equals("don't"), "A contraction must be scored by its real spelling: " + dont);
        check(dont.stream().filter(word -> word.replace("'", "").equals("dont")).count() == 1, "Spellings must not repeat: " + dont);
        check(swipe("its", "i think").get(0).equals("it's"), "Context must choose it's: " + swipe("its", "i think"));
        check(swipe("its", "the dog ate").get(0).equals("its"), "Context must choose its: " + swipe("its", "the dog ate"));
        check(swipe("well", "very").get(0).equals("well"), "A real word must survive its contraction twin: " + swipe("well", "very"));

        check("were".equals(lookAlike(new String[0], "we're", "you")), "we're you must become were you");
        check("we'll".equals(lookAlike(new String[0], "well", "be")), "well be must become we'll be");
        check(lookAlike(new String[0], "well", "grab") == null, "well grab is too weak a clue to change");
        check(lookAlike(new String[0], "were", "you") == null, "were you must stay");
        check(lookAlike(new String[0], "we're", "going") == null, "we're going must stay");
        check(lookAlike(new String[0], "house", "you") == null, "A word with no look-alike must stay");
        // Typed words change only on strong evidence. Each real word here must stay, even the close ones.
        String[][] typed = {
            { "", "were", "going", "we're" }, { "", "were", "gonna", "we're" }, { "", "its", "not", "it's" },
            { "", "ill", "be", "i'll" }, { "maybe", "ill", "see", "i'll" }, { "", "well", "be", "we'll" },
            { "", "id", "like", "i'd" }, { "", "id", "rather", "i'd" }, { "", "id", "go", "i'd" }, { "", "id", "just", "i'd" },
            { "but", "id", "just", "i'd" }, { "she said", "shed", "come", "she'd" },
            { "they", "were", "going", null }, { "", "were", "you", null }, { "", "were", "there", null },
            { "", "were", "all", null }, { "", "were", "either", null }, { "", "well", "look", null },
            { "", "were", "the", null }, { "the dog wagged", "its", "tail", null }, { "very", "well", "done", null },
            { "", "well", "it", null }, { "she is", "ill", "and", null }, { "she", "lets", "me", null },
            { "the garden", "shed", "is", null }, { "your", "id", "card", null },
            { "", "id", "card", null }, { "", "id", "is", null }, { "user", "id", "for", null }, { "enter", "id", "and", null }, { "get", "well", "soon", null },
        };
        for (String[] example : typed) {
            String found = recogniser.lookAlike(SentenceContext.words(example[0]), example[1], example[2], true);
            check(java.util.Objects.equals(found, example[3]), "Typed " + example[0] + " [" + example[1] + "] " + example[2]
                + " must give " + example[3] + ", not " + found);
        }
        try (Reader vocabulary = new FileReader(args[0] + "/vocab.txt")) {
            ProperNames.load(vocabulary);
        }
        check(SwipeSpelling.restore("i'll").equals("I'll"), "I'll must keep its capital: " + SwipeSpelling.restore("i'll"));
        check(SwipeSpelling.restore("i'd").equals("I'd"), "I'd must keep its capital: " + SwipeSpelling.restore("i'd"));

        for (String next : new String[] { "raining", "snowing", "windy", "rainy", "foggy", "tuesday", "christmas", "midnight" })
            check(lookAlike(new String[0], "it's", next) == null, "it's " + next + " must stay");
        check("its".equals(lookAlike(new String[] { "the", "horse", "hurt" }, "it's", "leg")), "the horse hurt it's leg must become its");
        check("its".equals(lookAlike(new String[] { "the", "watch", "stopped", "because" }, "it's", "battery")), "because it's battery must become its");
        check(swipe("of", "knock it").get(0).equals("off"), "Context must choose off: " + swipe("of", "knock it"));
        check(swipe("of", "a cup").get(0).equals("of"), "Context must keep of: " + swipe("of", "a cup"));
        check(swipe("to", "i want").get(0).equals("to"), "Context must keep to: " + swipe("to", "i want"));
        check(swipe("to", "i want").contains("too"), "too must stay offered: " + swipe("to", "i want"));
        check(cleanSwipeFollowsContext(), "Two likely words must not tie at the context limit on a clean swipe");

        TypedPredictions predictions;
        try (Reader vocabulary = new FileReader(args[0] + "/vocab.txt")) {
            predictions = new TypedPredictions(vocabulary, recogniser::frequency);
        }
        Map<String, Double> likely = nextWords("i don't");
        check(top(predictions, "", likely).contains("know"), "Next words must follow context: " + top(predictions, "", likely));
        check(top(predictions, "t", nextWords("i don't")).contains("think"), "Completions must follow context: " + top(predictions, "t", nextWords("i don't")));
        check(top(predictions, "", nextWords("")).contains("i"), "A sentence start must suggest likely first words: " + top(predictions, "", nextWords("")));
        check(swipe("im", "but").get(0).equals("i'm"), "I'm must not lose to the model's low rating: " + swipe("im", "but"));
        check(swipe("in", "but").get(0).equals("in"), "A clean swipe of in must stay in: " + swipe("in", "but"));
        check(swipe("were", "").get(0).equals("we're"), "A sentence start must prefer we're: " + swipe("were", ""));
        check(swipe("were", "they").get(0).equals("were"), "Real context must still choose were: " + swipe("were", "they"));
        check(swipe("oclock", "at nine").get(0).equals("o'clock"), "O'clock must keep its apostrophe: " + swipe("oclock", "at nine"));
        System.out.println("Context swipe checks passed");
    }

    private static Map<String, Double> nextWords(String text) {
        return recogniser.nextWords(SentenceContext.words(text));
    }

    /** A swiped word's look-alike that the next word makes clearly more likely, or null. */
    private static String lookAlike(String[] context, String word, String next) {
        return recogniser.lookAlike(context, word, next, false);
    }

    private static List<String> top(TypedPredictions predictions, String prefix, Map<String, Double> likely) {
        return Arrays.asList(predictions.suggest(prefix, List.of(), 20000, word -> true, likely)).subList(0, 3);
    }

    /** What the keyboard offers for a swipe after {@code text}, with no saved words. */
    private static List<String> swipe(String letters, String text) {
        SwipeDecoder.Result[] results = recogniser.recognise(path(letters), SentenceContext.words(text), false);
        return Arrays.stream(results).map(result -> result.word).toList();
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
