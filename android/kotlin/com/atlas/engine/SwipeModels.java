package com.atlas.engine;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import org.futo.ml.inference.SwipeDecoder;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Swipe engine 2.0's models: the CleverKeys encoder fine-tuned on Titan strokes, the phone-sized next-word model and the
 * ranker that weighs them with the FUTO engine's own lists. Any failure leaves the FUTO engine's order in place.
 */
final class SwipeModels implements AutoCloseable {
    static final String FOLDER = "swipe2", HEADER = "titankeys-swipe-models-v2";
    private static final int ENCODER_THREADS = 2, PREDICTOR_THREADS = 4, NEXT_WORDS = 300;
    final CtcReader reader;
    final NextWordModel predictor;
    final SwipeRanker ranker;
    private volatile Map<String, Integer> personal = Map.of();

    private SwipeModels(CtcReader reader, NextWordModel predictor, SwipeRanker ranker) {
        this.reader = reader; this.predictor = predictor; this.ranker = ranker;
    }

    /** Copies and checks the models, then loads them. Takes a few seconds, so it runs off the recognition thread. */
    static SwipeModels load(Context context) throws Exception {
        File dir = new File(context.getNoBackupFilesDir(), "swipe-models-v2");
        ModelAssets.install(context.getAssets(), FOLDER, HEADER, dir.toPath());
        return load(dir, new File(context.getNoBackupFilesDir(), "swipe-models-v1/english.combined"));
    }

    /** Loads the models from {@code dir}, with the FUTO engine's word list. Also used to test them on the phone. */
    static SwipeModels load(File dir, File dictionary) throws Exception {
        long began = SystemClock.uptimeMillis();
        CtcWords words;
        try (BufferedReader in = Files.newBufferedReader(dictionary.toPath(), StandardCharsets.UTF_8)) {
            words = new CtcWords(in);
        }
        SwipeRanker ranker;
        try (BufferedReader in = Files.newBufferedReader(new File(dir, "swipe-ranker.txt").toPath(), StandardCharsets.UTF_8)) {
            ranker = new SwipeRanker(in);
        }
        CtcReader reader = new CtcReader(new File(dir, "ck-titan.onnx"), words, ENCODER_THREADS);
        NextWordModel predictor;
        try {
            predictor = new NextWordModel(new File(dir, "predictor"), "model-int8pc.onnx", ranker.lmCap, PREDICTOR_THREADS);
        } catch (Exception error) {
            reader.close();
            throw error;
        }
        Log.d("TouchpadSwipe", "Swipe models 2.0 ready in " + (SystemClock.uptimeMillis() - began) + " ms, " + ranker.trees() + " trees");
        return new SwipeModels(reader, predictor, ranker);
    }

    /** Saved words and how common the user makes them, for the ranker's frequency of a word the dictionary lacks. */
    void personalWords(String[] words, float[] frequencies) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < words.length; i++) map.merge(CtcWords.key(words[i]), Math.round(frequencies[i]), Math::max);
        personal = map;
    }

    /**
     * The ranker's order for a swipe: up to three words, best first. {@code shown} is the FUTO engine's own order and
     * {@code readings} its plain and shifted lists. The FUTO path and context scores are kept for the raw log.
     */
    SwipeDecoder.Result[] rank(SwipeDecoder.Result[] shown, SwipeDecoder.Result[][] readings, Swipe gesture, String text,
                               Predicate<String> allowed) throws Exception {
        CtcWords.Candidate[] ck = reader.read(gesture);
        float[] chances = predictor.after(text);
        Map<String, String> spellings = new HashMap<>();
        CtcWords words = reader.words();
        Map<String, Integer> saved = personal;
        Map<String, double[]> cand = SwipeRanker.features(shown, readings[0], readings[1], ck, words, k -> predictor.word(chances, k), gesture,
                text, spellings);
        for (Map.Entry<String, double[]> e : cand.entrySet())
            if (e.getValue()[12] < 0 && saved.containsKey(e.getKey())) e.getValue()[12] = saved.get(e.getKey());
        SwipeRanker.Choice[] best = ranker.rank(cand, spellings, allowed, SwipeRanker.shown());
        Map<String, SwipeDecoder.Result> futo = new LinkedHashMap<>();
        for (SwipeDecoder.Result[] list : new SwipeDecoder.Result[][]{shown, readings[0], readings[1]})
            for (SwipeDecoder.Result r : list) futo.putIfAbsent(CtcWords.key(r.word), r);
        SwipeDecoder.Result[] out = new SwipeDecoder.Result[best.length];
        for (int i = 0; i < best.length; i++) {
            SwipeDecoder.Result r = futo.get(best[i].key);
            out[i] = new SwipeDecoder.Result(best[i].word, (float) best[i].score, r == null ? 0 : r.ctcScore, r == null ? 0 : r.lmScore);
        }
        return out;
    }

    /** Gets the next-word chances for this text ready, so a swipe or a completion after it need not wait for them. */
    void prepare(String text) throws Exception { predictor.after(text); }

    /** The likeliest next words after this text, lower case, as log chances: for suggestions before any letter. */
    Map<String, Double> nextWords(String text, Predicate<String> known) throws Exception {
        float[] chances = predictor.after(text);
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : predictor.likelyWords(chances, NEXT_WORDS, w -> known.test(w.toLowerCase(java.util.Locale.ROOT))))
            out.merge(e.getKey().toLowerCase(java.util.Locale.ROOT), e.getValue(), Math::max);
        return out;
    }

    /** The next-word model's log chance of this word after this text, or NaN when the chances are not ready yet. */
    double ifReady(String text, String word) {
        float[] chances = predictor.ifReady(text);
        return chances == null ? Double.NaN : predictor.word(chances, word.toLowerCase(java.util.Locale.ROOT).replace('’', '\''));
    }

    @Override
    public void close() {
        reader.close();
        predictor.close();
    }
}
