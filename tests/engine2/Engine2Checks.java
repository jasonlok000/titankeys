package com.atlas.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.futo.ml.inference.SwipeDecoder.Result;

/**
 * Checks the parts of swipe engine 2.0 that run without the phone: the next-word model's tokenizer, the CleverKeys word
 * search and stroke features, and the ranker's trees and features. ONNX Runtime itself only runs on the phone.
 */
public final class Engine2Checks {
    private static int failures;

    public static void main(String[] args) throws Exception {
        tokenizer();
        wordSearch();
        features();
        ranker();
        if (failures > 0) throw new AssertionError(failures + " engine 2.0 check(s) failed");
        System.out.println("Engine 2.0 checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            failures++;
            System.err.println("FAILED: " + message);
        }
    }

    /** The bundled vocabulary must give GPT-2's own token ids. */
    private static void tokenizer() throws IOException {
        Path dir = Path.of("android/assets/swipe2/predictor");
        ByteBpe bpe;
        try (BufferedReader vocab = Files.newBufferedReader(dir.resolve("lm-vocab.txt"), StandardCharsets.UTF_8);
             BufferedReader merges = Files.newBufferedReader(dir.resolve("lm-merges.txt"), StandardCharsets.UTF_8)) {
            bpe = new ByteBpe(vocab, merges, false);
        }
        check(bpe.vocabularySize() == 50257, "The predictor's vocabulary must have GPT-2's 50,257 tokens");
        check(Arrays.equals(bpe.encode("Hello world"), new int[]{15496, 995}), "\"Hello world\" must be GPT-2's tokens");
        check(Arrays.equals(bpe.encode(" hello world"), new int[]{23748, 995}), "A space must join the word that follows it");
        check(Arrays.equals(bpe.encode("I'm going to the shop's door, 12 times!"),
                new int[]{40, 1101, 1016, 284, 262, 6128, 338, 3420, 11, 1105, 1661, 0}), "Apostrophes, digits and marks must split as GPT-2 splits them");
        check(Arrays.equals(bpe.encode("\n\n"), new int[]{628}), "Line breaks must be one token");
        check(bpe.first(" the") == 262 && bpe.first("") == -1, "The first token of a word, or none");
    }

    private static CtcWords smallDictionary() throws IOException {
        String dictionary = "dictionary=main:en\n"
                + " word=cat,f=200,flags=,originalFreq=200\n"
                + " word=car,f=150,flags=,originalFreq=150\n"
                + " word=cart,f=100,flags=,originalFreq=100\n"
                + " word=don't,f=180,flags=,originalFreq=180\n"
                + " word=zzz,f=10,flags=not_a_word,originalFreq=10\n";
        return new CtcWords(new BufferedReader(new StringReader(dictionary)));
    }

    /** Letter chances that clearly spell {@code word}, each letter held two frames, blanks after. */
    private static double[][] spelling(String word) {
        double[][] lp = new double[32][27];
        for (double[] frame : lp) Arrays.fill(frame, -12);
        int t = 0;
        for (char c : word.toCharArray()) {
            lp[t++][c - 'a'] = 0;
            lp[t++][c - 'a'] = 0;
            lp[t++][CtcWords.BLANK] = 0;
        }
        while (t < 32) lp[t++][CtcWords.BLANK] = 0;
        return lp;
    }

    private static void wordSearch() throws IOException {
        CtcWords words = smallDictionary();
        check(CtcWords.key("Don’t").equals("dont") && CtcWords.key("'Colour'").equals("color"), "Keys drop apostrophes and case");
        check(words.contains("dont") && words.spelling("dont").equals("don't"), "A key must keep its written form");
        check(!words.contains("zzz"), "Entries marked not_a_word must be left out");
        check(words.count("cat") == 200 && words.count("dog") == 0, "Word counts come from the dictionary");
        CtcWords.Candidate[] found = words.search(spelling("cart"));
        check(found.length > 0 && found[0].key.equals("cart"), "Clear letters must find their word first, not " + (found.length > 0 ? found[0].key : "nothing"));
        found = words.search(spelling("cat"));
        check(found.length > 0 && found[0].key.equals("cat"), "A shorter word must not lose to a longer one it starts");
    }

    private static void features() {
        float[] x = {0, .5f, 1}, y = {0, .5f, 1}, t = {0, 500, 1000};
        float[] f = CtcWords.features(x, y, t);
        check(f.length == 128, "64 x and 64 y values");
        check(Math.abs(f[0]) < 1e-6 && Math.abs(f[63] - 1) < 1e-6 && Math.abs(f[32] - 32 / 63f) < 1e-4, "A steady stroke is resampled evenly");
        check(Math.abs(f[64 + 63] - 1) < 1e-6, "y follows x");
        float[] still = CtcWords.features(new float[]{.3f}, new float[]{.4f}, new float[]{5});
        check(still[10] == .3f && still[64 + 10] == .4f, "A single point is held in place");
        float[] keys = CtcWords.titanKeys();
        check(Math.abs(keys[2 * ('q' - 'a')] - .05f) < 1e-6 && Math.abs(keys[2 * ('z' - 'a')] - .15f) < 1e-6, "The bottom row starts one key in");
    }

    private static String model(String names) {
        return "# test model\nfeature_names=" + names + "\nlm_cap=24\n"
                + "Tree=0\nnum_leaves=2\nsplit_feature=0\nthreshold=0.5\ndecision_type=2\nleft_child=-1\nright_child=-2\nleaf_value=1 -1\n\n"
                + "Tree=1\nnum_leaves=3\nsplit_feature=2 10\nthreshold=inf -5\ndecision_type=10 2\nleft_child=1 -1\nright_child=-3 -2\n"
                + "leaf_value=0.5 0.25 -0.75\n\n";
    }

    private static void ranker() throws IOException {
        SwipeRanker ranker = new SwipeRanker(new BufferedReader(new StringReader(model(String.join(" ", SwipeRanker.FEATURES)))));
        check(ranker.trees() == 2 && ranker.lmCap == 24, "Both trees and the context length must be read");
        double[] shownFirst = new double[SwipeRanker.FEATURES.length];
        shownFirst[2] = Double.NaN;
        shownFirst[10] = -10;
        check(ranker.score(shownFirst) == 1.5, "A missing value must follow the tree's default side");
        double[] other = new double[SwipeRanker.FEATURES.length];
        other[0] = 9;
        other[10] = -1;
        check(ranker.score(other) == -0.75, "Trees must add up");
        try {
            new SwipeRanker(new BufferedReader(new StringReader(model("final p_rank"))));
            check(false, "A model trained on other features must be refused");
        } catch (IOException expected) {
            // Refused, as it must be.
        }

        Result[] shown = {new Result("Don't", -1, -2, -3), new Result("cat", -2, -2, -3)};
        Result[] plain = {new Result("cat", -4, -5, -6), new Result("cart", -7, -8, -9)};
        CtcWords words = smallDictionary();
        CtcWords.Candidate[] ck = words.search(spelling("car"));
        // 3 keys across and 4 keys down: a 5-key stroke.
        Swipe stroke = new Swipe(new float[]{0, .3f}, new float[]{0, 4f / 3}, new float[]{10, 110});
        Map<String, String> spellings = new HashMap<>();
        Map<String, double[]> cand = SwipeRanker.features(shown, plain, new Result[0], ck, words, k -> k.equals("cat") ? -2 : -6, stroke,
                "see the ", spellings);
        check(cand.keySet().iterator().next().equals("dont") && spellings.get("dont").equals("Don't"), "Shown words come first, written as shown");
        double[] cat = cand.get("cat");
        check(cat[0] == 1 && cat[1] == 0 && cat[3] == -4 && cat[5] == 99 && Double.isNaN(cat[6]), "Engine places and scores for a word");
        check(cat[10] == -2 && cat[11] == 0 && cand.get("dont")[11] == -4, "Next-word chances and their gap from the best");
        check(cat[12] == 200 && cat[13] == 3 && Math.abs(cat[14] - 5) < 1e-6 && cat[15] == 100 && cat[16] == 2 && cat[17] == 1,
                "Frequency, length, stroke length in keys, time and words before");
        check(cand.containsKey("car") && cand.get("car")[7] < 99, "CleverKeys' words join the candidates");
        SwipeRanker.Choice[] best = ranker.rank(cand, spellings, w -> !w.equals("Don't"), 3);
        check(Arrays.stream(best).noneMatch(c -> c.word.equals("Don't")) && best.length == 3, "Refused words must be left out");
    }
}
