package com.atlas.engine;

import android.content.Context;
import com.atlas.keyboard.Layout;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class SwipeRecognizer implements AutoCloseable {
    private static final float MAX_CONTEXT_SCORE = 1.5f;
    private static final float MAX_SENTENCE_CONTEXT_SCORE = 4f;
    private static final float MAX_PATH_SCORE_GAP = 3f;
    private static final int NEXT_WORD_CANDIDATES = 3000;
    private org.futo.ml.inference.SwipeDecoder decoder;
    private org.futo.ml.inference.NativeDictionary base, personal;
    private Set<String> learnedWords = Set.of();
    private org.futo.ml.inference.SwipeDecoder.Result[] rawResults = new org.futo.ml.inference.SwipeDecoder.Result[0];

    public org.futo.ml.inference.SwipeDecoder.Result[] rawResults() { return rawResults; }
    public float contextWeight() { return decoder.contextWeight(); }

    public SwipeRecognizer(Context context) throws IOException {
        File file = new File(context.getNoBackupFilesDir(), "swipe-models-v1");
        ModelAssets.install(context.getAssets(), file.toPath());
        try {
            base = new org.futo.ml.inference.NativeDictionary(new File(file, "english.combined").getPath());
            personal = new org.futo.ml.inference.NativeDictionary(new String[0], new float[0]);
            decoder = new org.futo.ml.inference.SwipeDecoder(file.getPath());
            configure(base.pointer(), personal.pointer());
        } catch (RuntimeException | LinkageError error) { close(); throw error; }
    }

    private void configure(long... pointers) {
        decoder.dictionaries(Layout.LETTERS, Layout.KEY_X, Layout.KEY_Y, pointers);
    }

    public void personalWords(String[] words, float[] frequencies) {
        Set<String> learned = Set.copyOf(java.util.Arrays.asList(words));
        org.futo.ml.inference.NativeDictionary replacement = new org.futo.ml.inference.NativeDictionary(words, frequencies);
        try { configure(base.pointer(), replacement.pointer()); }
        catch (RuntimeException | LinkageError error) {
            // A failed configuration may have changed native pointers before reporting failure.
            try { configure(base.pointer(), personal.pointer()); }
            catch (RuntimeException | LinkageError restoreError) { error.addSuppressed(restoreError); close(); }
            finally { replacement.close(); }
            throw error;
        }
        org.futo.ml.inference.NativeDictionary previous = personal;
        personal = replacement;
        learnedWords = learned;
        previous.close();
    }

    public float frequency(String word) { return base.frequency(word); }

    public org.futo.ml.inference.SwipeDecoder.Result[] recognise(Swipe gesture, String[] context, boolean personalized) {
        return recognise(gesture, context, personalized, word -> true);
    }

    org.futo.ml.inference.SwipeDecoder.Result[] recognise(Swipe gesture, String[] context, boolean personalized,
            Predicate<String> allowed) {
        org.futo.ml.inference.SwipeDecoder.Result[] results = decoder.recognise(
                gesture.x, gesture.y, gesture.time, context, new float[]{1, personalized ? 1 : 0});
        rawResults = results.clone();
        results = java.util.Arrays.stream(results).filter(result -> allowed.test(result.word))
                .toArray(org.futo.ml.inference.SwipeDecoder.Result[]::new);
        return rank(gesture, results, decoder.contextWeight(), decoder.shapeNormalizer(), context.length,
                personalized ? learnedWords : Set.of());
    }

    public Map<String, Double> nextWords(String[] context) {
        if (context.length == 0) return Map.of();
        Map<String, Double> scores = new HashMap<>();
        for (org.futo.ml.inference.SwipeDecoder.Result result : decoder.predictNext(context, NEXT_WORD_CANDIDATES))
            scores.putIfAbsent(TypedPredictions.key(result.word), (double) result.score);
        return scores;
    }

    public static org.futo.ml.inference.SwipeDecoder.Result[] rank(Swipe gesture,
            org.futo.ml.inference.SwipeDecoder.Result[] results, float contextWeight, float shapeNormalizer,
            int contextWords) {
        return rank(gesture, results, contextWeight, shapeNormalizer, contextWords, Set.of());
    }

    static org.futo.ml.inference.SwipeDecoder.Result[] rank(Swipe gesture,
            org.futo.ml.inference.SwipeDecoder.Result[] results, float contextWeight, float shapeNormalizer,
            int contextWords, Set<String> learnedWords) {
        results = java.util.Arrays.stream(results).filter(result -> SuggestionPolicy.allowed(result.word))
                .toArray(org.futo.ml.inference.SwipeDecoder.Result[]::new);
        org.futo.ml.inference.SwipeDecoder.Result shapeBest = null;
        for (org.futo.ml.inference.SwipeDecoder.Result result : results) {
            if (shapeBest == null || result.ctcScore > shapeBest.ctcScore) shapeBest = result;
        }
        boolean clearEndpoints = shapeBest != null
                && endpointDistance(gesture, shapeBest.word, false) <= .25f
                && endpointDistance(gesture, shapeBest.word, true) <= .25f;
        float contextLimit = MAX_CONTEXT_SCORE;
        if (clearEndpoints) contextLimit = .5f;
        else if (contextWords >= 2) contextLimit = MAX_SENTENCE_CONTEXT_SCORE;
        Map<String, EndpointScores> groups = new HashMap<>();
        float[] adjustedScores = new float[results.length];
        for (int i = 0; i < results.length; i++) {
            org.futo.ml.inference.SwipeDecoder.Result result = results[i];
            float contextScore = contextWeight * result.lmScore;
            float boundedContext = Math.max(-contextLimit, Math.min(contextLimit, contextScore));
            float penalty = endpointPenalty(gesture, result.word, false) + endpointPenalty(gesture, result.word, true);
            float adjusted = result.score - penalty - contextScore + boundedContext;
            adjustedScores[i] = adjusted;
            EndpointScores group = groups.computeIfAbsent(endpointGroup(result.word), key -> new EndpointScores());
            double pathScore = result.ctcScore / Math.pow(Math.max(1, result.word.replace("'", "").length()), shapeNormalizer);
            group.pathBest = Math.max(group.pathBest, pathScore);
            if (learnedWords.contains(result.word)) group.learnedPath = Math.max(group.learnedPath, pathScore);
            if (result.score > group.nativeBest) { group.nativeBest = result.score; group.nativePath = pathScore; }
            group.adjustedBest = Math.max(group.adjustedBest, adjusted);
        }
        // Keep context bounded when a learned word is the best shape match in its group.
        for (int i = 0; i < results.length; i++) {
            org.futo.ml.inference.SwipeDecoder.Result result = results[i];
            EndpointScores group = groups.get(endpointGroup(result.word));
            float score = group.learnedPath < group.pathBest && group.nativePath >= group.pathBest - MAX_PATH_SCORE_GAP
                    ? group.adjustedBest + (result.score - group.nativeBest) : adjustedScores[i];
            results[i] = new org.futo.ml.inference.SwipeDecoder.Result(
                    result.word, score, result.ctcScore, result.lmScore);
        }
        java.util.Arrays.sort(results, (a, b) -> Float.compare(b.score, a.score));
        return java.util.Arrays.copyOf(results, Math.min(3, results.length));
    }

    private static String endpointGroup(String word) {
        return word.isEmpty() ? "" : word.charAt(0) + ":" + word.charAt(word.length() - 1);
    }

    private static final class EndpointScores {
        float nativeBest = Float.NEGATIVE_INFINITY;
        float adjustedBest = Float.NEGATIVE_INFINITY;
        double pathBest = Double.NEGATIVE_INFINITY;
        double learnedPath = Double.NEGATIVE_INFINITY;
        double nativePath;
    }

    private static float endpointPenalty(Swipe gesture, String word, boolean end) {
        float distance = endpointDistance(gesture, word, end);
        return Float.isFinite(distance) ? 2f * Math.min(4, distance) : 0;
    }

    private static float endpointDistance(Swipe gesture, String word, boolean end) {
        if (word.isEmpty() || gesture.x.length == 0) return Float.POSITIVE_INFINITY;
        int key = Layout.LETTERS.indexOf(word.charAt(end ? word.length() - 1 : 0));
        if (key < 0) return Float.POSITIVE_INFINITY;
        int point = end ? gesture.x.length - 1 : 0;
        float dx = (gesture.x[point] - Layout.KEY_X[key]) / 0.1f;
        float dy = (gesture.y[point] - Layout.KEY_Y[key]) / (1f / 3f);
        return dx * dx + dy * dy;
    }

    public boolean fits(Swipe gesture, String[] context, String word,
                        org.futo.ml.inference.SwipeDecoder.Result baseline) {
        if (baseline == null) return false;
        org.futo.ml.inference.NativeDictionary probe = new org.futo.ml.inference.NativeDictionary(new String[]{word}, new float[]{64});
        try {
            configure(base.pointer(), personal.pointer(), probe.pointer());
            org.futo.ml.inference.SwipeDecoder.Result[] candidates = decoder.recognise(
                    gesture.x, gesture.y, gesture.time, context, new float[]{0, 0, 1});
            if (candidates.length == 0) return false;
            return plausible(candidates[0], baseline);
        } finally {
            try { configure(base.pointer(), personal.pointer()); }
            catch (RuntimeException | LinkageError error) { close(); throw error; }
            finally { probe.close(); }
        }
    }

    public boolean plausible(org.futo.ml.inference.SwipeDecoder.Result candidate,
                             org.futo.ml.inference.SwipeDecoder.Result baseline) {
        if (candidate == null || baseline == null) return false;
        double normalizer = decoder.shapeNormalizer();
        double score = candidate.ctcScore / Math.pow(candidate.word.replace("'", "").length(), normalizer);
        double reference = baseline.ctcScore / Math.pow(baseline.word.replace("'", "").length(), normalizer);
        return Double.isFinite(score) && score >= reference - MAX_PATH_SCORE_GAP;
    }

    public String[] recognise(Swipe gesture, String[] context) {
        return java.util.Arrays.stream(recognise(gesture, context, false)).map(result -> result.word).toArray(String[]::new);
    }

    public boolean isOpen() { return decoder != null && base != null && personal != null; }

    @Override public void close() {
        try { if (decoder != null) decoder.close(); }
        finally {
            decoder = null;
            try { if (personal != null) personal.close(); }
            finally {
                personal = null;
                try { if (base != null) base.close(); }
                finally { base = null; }
            }
        }
    }
}
