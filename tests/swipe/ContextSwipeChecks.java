package com.atlas.engine;

import com.atlas.keyboard.Layout;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.futo.ml.inference.NativeDictionary;
import org.futo.ml.inference.SwipeDecoder;

/** Runs the real context model on a device: contractions on swipes, and context on typed suggestions. */
public final class ContextSwipeChecks {
    private static SwipeDecoder decoder;

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
        NativeDictionary base = new NativeDictionary(args[0] + "/english.combined");
        NativeDictionary personal = new NativeDictionary(new String[0], new float[0]);
        NativeDictionary contractions = SwipeRecognizer.contractionDictionary(base);
        decoder = new SwipeDecoder(args[0]);
        decoder.dictionaries(Layout.LETTERS, Layout.KEY_X, Layout.KEY_Y, new long[] { base.pointer(), personal.pointer(), contractions.pointer() });

        List<String> dont = swipe("dont", "i");
        check(dont.get(0).equals("don't"), "A contraction must be scored by its real spelling: " + dont);
        check(dont.stream().filter(word -> word.replace("'", "").equals("dont")).count() == 1, "Spellings must not repeat: " + dont);
        check(swipe("its", "i think").get(0).equals("it's"), "Context must choose it's: " + swipe("its", "i think"));
        check(swipe("its", "the dog ate").get(0).equals("its"), "Context must choose its: " + swipe("its", "the dog ate"));
        check(swipe("well", "very").get(0).equals("well"), "A real word must survive its contraction twin: " + swipe("well", "very"));

        check("were".equals(SwipeRecognizer.lookAlike(decoder, new String[0], "we're", "you")), "we're you must become were you");
        check("we'll".equals(SwipeRecognizer.lookAlike(decoder, new String[0], "well", "be")), "well be must become we'll be");
        check(SwipeRecognizer.lookAlike(decoder, new String[0], "well", "grab") == null, "well grab is too weak a clue to change");
        check(SwipeRecognizer.lookAlike(decoder, new String[0], "were", "you") == null, "were you must stay");
        check(SwipeRecognizer.lookAlike(decoder, new String[0], "we're", "going") == null, "we're going must stay");
        check(SwipeRecognizer.lookAlike(decoder, new String[0], "house", "you") == null, "A word with no look-alike must stay");
        // Typed words change only on strong evidence. Each real word here must stay, even the close ones.
        String[][] typed = {
            { "", "were", "going", "we're" }, { "", "were", "gonna", "we're" }, { "", "its", "not", "it's" },
            { "", "ill", "be", "i'll" }, { "maybe", "ill", "see", "i'll" }, { "", "well", "be", "we'll" },
            { "", "id", "like", "i'd" }, { "she said", "shed", "come", "she'd" },
            { "they", "were", "going", null }, { "", "were", "you", null }, { "", "were", "there", null },
            { "", "were", "all", null }, { "", "were", "either", null }, { "", "well", "look", null },
            { "", "were", "the", null }, { "the dog wagged", "its", "tail", null }, { "very", "well", "done", null },
            { "", "well", "it", null }, { "she is", "ill", "and", null }, { "she", "lets", "me", null },
            { "the garden", "shed", "is", null }, { "your", "id", "card", null }, { "get", "well", "soon", null },
        };
        for (String[] example : typed) {
            String found = SwipeRecognizer.lookAlike(decoder, SentenceContext.words(example[0]), example[1], example[2], true);
            check(java.util.Objects.equals(found, example[3]), "Typed " + example[0] + " [" + example[1] + "] " + example[2]
                + " must give " + example[3] + ", not " + found);
        }
        try (Reader vocabulary = new FileReader(args[0] + "/vocab.txt")) {
            ProperNames.load(vocabulary);
        }
        check(SwipeSpelling.restore("i'll").equals("I'll"), "I'll must keep its capital: " + SwipeSpelling.restore("i'll"));
        check(SwipeSpelling.restore("i'd").equals("I'd"), "I'd must keep its capital: " + SwipeSpelling.restore("i'd"));

        for (String next : new String[] { "raining", "snowing", "windy", "rainy", "foggy", "tuesday", "christmas", "midnight" })
            check(SwipeRecognizer.lookAlike(decoder, new String[0], "it's", next) == null, "it's " + next + " must stay");
        check("its".equals(SwipeRecognizer.lookAlike(decoder, new String[] { "the", "horse", "hurt" }, "it's", "leg")), "the horse hurt it's leg must become its");
        check("its".equals(SwipeRecognizer.lookAlike(decoder, new String[] { "the", "watch", "stopped", "because" }, "it's", "battery")), "because it's battery must become its");
        check(swipe("of", "knock it").get(0).equals("off"), "Context must choose off: " + swipe("of", "knock it"));
        check(swipe("of", "a cup").get(0).equals("of"), "Context must keep of: " + swipe("of", "a cup"));
        check(swipe("to", "i want").get(0).equals("to"), "Context must keep to: " + swipe("to", "i want"));
        check(swipe("to", "i want").contains("too"), "too must stay offered: " + swipe("to", "i want"));
        check(cleanSwipeFollowsContext(), "Two likely words must not tie at the context limit on a clean swipe");

        TypedPredictions predictions;
        try (Reader vocabulary = new FileReader(args[0] + "/vocab.txt")) {
            predictions = new TypedPredictions(vocabulary, base::frequency);
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
        String[] context = SwipeRecognizer.scoringContext(SentenceContext.words(text));
        Map<String, Double> scores = new java.util.HashMap<>();
        for (SwipeDecoder.Result result : decoder.predictNext(context, 3000))
            scores.putIfAbsent(TypedPredictions.key(result.word), (double) result.score);
        return scores;
    }

    private static List<String> top(TypedPredictions predictions, String prefix, Map<String, Double> likely) {
        return Arrays.asList(predictions.suggest(prefix, List.of(), 20000, word -> true, likely)).subList(0, 3);
    }

    private static List<String> swipe(String letters, String text) {
        Swipe gesture = path(letters);
        String[] context = SentenceContext.words(text);
        SwipeDecoder.Result[] results = SwipeRecognizer.contractionSpellings(
                decoder.recognise(gesture.x, gesture.y, gesture.time, SwipeRecognizer.scoringContext(context), new float[] { 1, 0, 1 }), decoder.contextWeight());
        results = SwipeRecognizer.rank(gesture, results, decoder.contextWeight(), decoder.shapeNormalizer(), context.length, Set.of());
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
