package com.atlas.engine;

import com.atlas.keyboard.Layout;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.futo.ml.inference.SwipeDecoder.Result;

/**
 * Checks the reward for words that start a sentence, with the real word counts, word shapes and recorded swipes.
 * The swipe engine runs only on the phone, so the replay here covers the word shapes and not the engine.
 */
public final class FirstWordChecks {
    private static final String TRIPLES = "android/assets/autocorrect/en_triples.txt";
    private static final String DICTIONARY = "android/assets/swipe/english.combined";
    private static final String VOCABULARY = "android/assets/swipe/hungry_jellyfish/vocab.txt";
    private static final String SENTENCES = "android/debug/recorder/samples/sentences-20261108-192726.jsonl";
    private static final Result[] NO_ENGINE_WORDS = new Result[0];

    private record Recorded(String word, Swipe swipe) {}

    public static void main(String[] args) throws Exception {
        readsCounts();
        Map<String, Double> counts = realCounts();
        findsSentenceStarts();
        SwipeShapes shapes = new SwipeShapes(new File(DICTIONARY));
        rewardNeedsCloseFit(shapes);
        rareFirstWordsGetAReward(shapes, counts);
        firstWordsChooseSameLetters(shapes, counts);
        laterWordsKeepTheirLimits();
        standInDoesNotOrderFirstWords();
        replay(shapes, counts);
        popularStarts(counts);
        System.out.println("First word checks passed");
    }

    private static void popularStarts(Map<String, Double> counts) throws IOException {
        try (BufferedReader vocabulary = Files.newBufferedReader(Path.of(VOCABULARY), StandardCharsets.UTF_8)) {
            ProperNames.load(vocabulary);
        }
        TypedPredictions words;
        try (BufferedReader vocabulary = Files.newBufferedReader(Path.of(VOCABULARY), StandardCharsets.UTF_8)) {
            words = new TypedPredictions(vocabulary, word -> 1);
        }
        List<String> starts = FirstWords.popular(counts, words::contains);
        check(starts.size() > 3 && starts.subList(0, 3).equals(List.of("I", "the", "hello")), "The bar must get the best starts and a greeting: " + starts);
        for (String word : new String[] { "tom", "Tom", "sami", "mary", "ziri", "yanni", "mennad", "algeria", "he", "she" })
            check(!starts.contains(word), word + " must not be a start: " + starts);
        check(starts.stream().noneMatch(word -> word.startsWith("i'")), "I'm must keep its capital: " + starts);
        check(FirstWords.popular(Map.of(), word -> true).isEmpty(), "No counts must give no starts, so the keyboard's own few show");
        check(FirstWords.popular(Map.of("yes", 1.0), word -> true).equals(List.of("yes", "hello")), "A greeting goes last on a short list");
        check(FirstWords.popular(Map.of("hello", 7.0, "i", 6.0, "the", 5.0, "you", 4.0), word -> true).equals(List.of("I", "the", "hello", "you")),
                "A greeting that ranks well must not show twice");
    }

    private static void readsCounts() throws IOException {
        Map<String, Double> read = FirstWords.read(new BufferedReader(new StringReader(
                "# header\n<s> hi there\t3\n<s> hi you\t4\n<s> why not\t20\n<s> broken\n<s> bad line\na b c\t5\n<s> late word\t9\n")));
        check(Math.abs(read.get("hi") / read.get("why") - Math.log1p(7) / Math.log1p(20)) < 1e-9, "The words after a first word must add up: " + read);
        check(Math.abs(read.get("why") - SwipeRecognizer.EXPECTED_WORD_RANGE) < 1e-9, "The commonest first word must take the whole range: " + read);
        check(read.size() == 2 && !read.containsKey("a") && !read.containsKey("late"), "Only the first lines of the file count: " + read);
    }

    private static Map<String, Double> realCounts() throws IOException {
        Map<String, Double> counts;
        try (BufferedReader triples = Files.newBufferedReader(Path.of(TRIPLES), StandardCharsets.UTF_8)) {
            counts = FirstWords.read(triples);
        }
        check(counts.size() > 1000, "The real file must give many first words: " + counts.size());
        for (String word : new String[] { "i", "the", "what", "why", "i'm", "we're", "thanks", "let", "hi", "hello" })
            check(counts.containsKey(word), word + " must count as a first word");
        check(counts.get("the") > counts.get("thanks") && counts.get("thanks") > counts.get("hi"), "Counts must rank first words");
        check(!counts.containsKey("xylophones"), "A word that no sentence starts with must have no count");
        for (Map.Entry<String, Double> word : counts.entrySet())
            check(word.getValue() > 0, word.getKey() + " starts sentences, so it must have a score above the bottom of the range");
        return counts;
    }

    /** "hi" starts few sentences in the counts, and still clearly belongs at the start of one. */
    private static void rareFirstWordsGetAReward(SwipeShapes shapes, Map<String, Double> counts) {
        for (String word : new String[] { "hi", "hello", "ok", "morning" }) {
            double reward = reward(shapes, word, counts);
            check(reward > 1, word + " must get a reward: " + reward);
        }
        check(reward(shapes, "thanks", counts) > reward(shapes, "morning", counts), "A commoner first word must get more reward");
        check(reward(shapes, "xylophones", counts) == 0, "A word that no sentence starts with must get no reward");
    }

    /** How much the first word counts add to a word, on a swipe that follows the word exactly. */
    private static double reward(SwipeShapes shapes, String word, Map<String, Double> counts) {
        Swipe swipe = path(word);
        return score(shapes, swipe, word, counts) - score(shapes, swipe, word, Map.of());
    }

    private static double score(SwipeShapes shapes, Swipe swipe, String word, Map<String, Double> counts) {
        Result[] engine = { new Result(word, 0, 0, 0) };
        for (Result result : SwipeRecognizer.blended(shapes, swipe, engine, counts, SwipeRecognizer.contextStrength(0), false, candidate -> true))
            if (result.word.equals(word)) return result.score;
        throw new AssertionError(word + " must stay in the results");
    }

    private static void findsSentenceStarts() {
        for (String text : new String[] { "", "Hello there. ", "Really? ", "Wow! ", "One line\n" })
            check(SentenceContext.words(text).length == 0, "A sentence must start after '" + text + "'");
        check(SentenceContext.words("Hello there, ").length == 2, "A comma must not start a sentence");
        check(SentenceContext.words("Hello. There ").length == 1, "A later word must not be a sentence start");
    }

    private static void rewardNeedsCloseFit(SwipeShapes shapes) {
        Swipe left = path("left");
        check(first(shapes, left, Map.of()).equals("left"), "A clean swipe must keep its word with no reward: " + first(shapes, left, Map.of()));
        check(first(shapes, left, Map.of("the", 12.0, "left", 1.0)).equals("left"), "A very common first word must not win on a swipe that does not fit it");
        check(first(shapes, left, Map.of("let", 12.0, "left", 1.0)).equals("left"), "A first word must not win on a swipe that fits another word better");
        Swipe good = path("good");
        check(first(shapes, good, Map.of()).equals("good"), "The commoner of two equal shapes must lead with no reward: " + first(shapes, good, Map.of()));
        check(first(shapes, good, Map.of("god", 12.0, "good", 1.0)).equals("god"), "A first word must win on a swipe that fits it as well");
        Result[] engine = { new Result("left", 0, -1, 0), new Result("the", -1, -9, 0) };
        Result[] rewarded = SwipeRecognizer.blended(shapes, left, engine, Map.of("the", 12.0, "left", 1.0), SwipeRecognizer.contextStrength(0), false, word -> true);
        check(rewarded[0].word.equals("left"), "An engine word must not win on being common: " + Arrays.toString(words(rewarded)));
    }

    /** The context limits for later words must stay as they were, while the stand-in word must no longer count. */
    private static void laterWordsKeepTheirLimits() {
        check(SwipeRecognizer.contextStrength(1) == .5f && SwipeRecognizer.contextStrength(2) == 1 && SwipeRecognizer.contextStrength(5) == 1,
                "The strength of later words must not change");
        Swipe unclear = new Swipe(new float[] { .55f, .65f }, new float[] { .5f, .5f }, new float[] { 0, 80 });
        double none = lead(unclear, 0), one = lead(unclear, 1), two = lead(unclear, 2), three = lead(unclear, 3);
        check(Math.abs(one - none - 3) < 1e-3, "One word before the swipe must keep its limit: " + (one - none));
        check(Math.abs(two - none - 8) < 1e-3 && Math.abs(three - two) < 1e-3, "Two or more words must keep their limit: " + (two - none));
    }

    /**
     * "where" and "we're" have the same first and last letters, and "so" much prefers "where". As a first word the
     * better path must lead. After a real word, the gap inside the group must stay as it was.
     */
    private static void standInDoesNotOrderFirstWords() {
        Swipe swipe = new Swipe(new float[] { .15f, .25f }, new float[] { .2f, .2f }, new float[] { 0, 80 });
        check(sameGroupLead(swipe, 0) < 0, "What follows the stand-in word must not choose a first word: " + sameGroupLead(swipe, 0));
        check(Math.abs(sameGroupLead(swipe, 1) - 5) < 1e-3 && Math.abs(sameGroupLead(swipe, 2) - 5) < 1e-3,
                "Later words must keep the gap inside their group: " + sameGroupLead(swipe, 1) + ", " + sameGroupLead(swipe, 2));
    }

    /** How far "where" leads "we're" when "we're" has the better path and the context model much prefers "where". */
    private static double sameGroupLead(Swipe swipe, int contextWords) {
        Result[] ranked = SwipeRecognizer.rank(swipe, new Result[] { new Result("where", -10, -10, 0), new Result("we're", -15, -9, -6) }, 1, .1126f, contextWords);
        float where = 0, were = 0;
        for (Result result : ranked) { if (result.word.equals("where")) where = result.score; else were = result.score; }
        return where - were;
    }

    /**
     * Words that share their letters have the same path, so how often each one starts a sentence must choose between
     * them, even when the dictionary puts one far ahead. After a real word, the engine's order must stay.
     */
    private static void firstWordsChooseSameLetters(SwipeShapes shapes, Map<String, Double> counts) {
        Swipe swipe = path("were");
        Result[] engine = { new Result("were", -10, -10, 0), new Result("we're", -14, -14, 0) };
        Result[] ranked = SwipeRecognizer.ranked(swipe, engine, 1, .1126f, 0, Set.of());
        Result[] rewarded = SwipeRecognizer.blended(shapes, swipe, ranked, counts, SwipeRecognizer.contextStrength(0), false, word -> true);
        check(rewarded[0].word.equals("we're"), "A sentence start must prefer we're: " + Arrays.toString(words(rewarded)));
        Result[] later = SwipeRecognizer.rank(swipe, engine, 1, .1126f, 1);
        check(later[0].word.equals("were") && later[0].score - later[1].score == 4, "A later word must keep the engine's order: " + Arrays.toString(words(later)));
    }

    /** How far "cat" leads "dog" when the context model much prefers "cat". */
    private static double lead(Swipe swipe, int contextWords) {
        Result[] ranked = SwipeRecognizer.rank(swipe, new Result[] { new Result("cat", -5, -5, 0), new Result("dog", -106, -6, -100) }, 1, .1126f, contextWords);
        float cat = 0, dog = 0;
        for (Result result : ranked) { if (result.word.equals("cat")) cat = result.score; else dog = result.score; }
        return cat - dog;
    }

    /** Replays the first try at the first word of each recorded sentence, before and after the reward. */
    private static void replay(SwipeShapes shapes, Map<String, Double> counts) throws IOException {
        List<Recorded> recorded = recorded();
        check(recorded.size() == 24, "The sample must hold 24 sentences: " + recorded.size());
        int before = 0, after = 0;
        for (Recorded sentence : recorded) {
            if (first(shapes, sentence.swipe, Map.of()).equalsIgnoreCase(sentence.word)) before++;
            if (first(shapes, sentence.swipe, counts).equalsIgnoreCase(sentence.word)) after++;
        }
        System.out.println("First word comes first on word shapes alone: " + before + " of 24 before, " + after + " of 24 after");
        check(after >= before, "The reward must not lose first words: " + before + " before, " + after + " after");
    }

    private static String first(SwipeShapes shapes, Swipe swipe, Map<String, Double> counts) {
        Result[] results = SwipeRecognizer.blended(shapes, swipe, NO_ENGINE_WORDS, counts, SwipeRecognizer.contextStrength(0), false, word -> true);
        return results.length == 0 ? "" : results[0].word;
    }

    private static String[] words(Result[] results) { return Arrays.stream(results).map(result -> result.word).toArray(String[]::new); }

    private static final Pattern PROMPT = Pattern.compile("\"prompt\":\"([^\"]*)\"");
    private static final Pattern POINTS = Pattern.compile("\"s\":\\[\\[(.*?)\\]\\]");
    private static final Pattern SPAN = Pattern.compile("\"start\":(\\d+),\"end\":(\\d+)");

    /** In each line, the swipe just before the first words offered is the first try at the first word. */
    private static List<Recorded> recorded() throws IOException {
        List<Recorded> found = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(SENTENCES), StandardCharsets.UTF_8)) {
            Matcher prompt = PROMPT.matcher(line);
            if (!prompt.find()) continue;
            List<double[]> points = new ArrayList<>();
            long start = 0, end = 0;
            for (String event : line.split("\\{\"k\":\"")) {
                if (event.startsWith("rec\"")) break;
                if (event.startsWith("m\"")) points.addAll(points(event));
                else if (event.startsWith("out\"") && event.contains("\"type\":\"swipe\"")) {
                    Matcher span = SPAN.matcher(event);
                    if (span.find()) { start = Long.parseLong(span.group(1)); end = Long.parseLong(span.group(2)); }
                }
            }
            found.add(new Recorded(prompt.group(1).split(" ")[0], swipe(points, start, end)));
        }
        return found;
    }

    private static List<double[]> points(String event) {
        List<double[]> points = new ArrayList<>();
        Matcher matcher = POINTS.matcher(event);
        if (!matcher.find()) return points;
        for (String point : matcher.group(1).split("\\],\\[")) {
            if (point.contains("null")) continue;
            String[] parts = point.split(",");
            points.add(new double[] { Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]) });
        }
        return points;
    }

    /** Keeps the touch points of the swipe and puts them in letter space, as the keyboard does. */
    private static Swipe swipe(List<double[]> points, long start, long end) {
        List<double[]> kept = points.stream().filter(point -> point[2] >= start && point[2] <= end).toList();
        float[] x = new float[kept.size()], y = new float[kept.size()], time = new float[kept.size()];
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) kept.get(i)[0];
            y[i] = (float) kept.get(i)[1] * 4 / 3;
            time[i] = (float) (kept.get(i)[2] - start);
        }
        return new Swipe(x, y, time);
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
