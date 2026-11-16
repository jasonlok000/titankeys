package com.titankeys.engine;

import com.titankeys.keyboard.Layout;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.futo.ml.inference.NativeDictionary;
import org.futo.ml.inference.SwipeDecoder;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Runs labelled strokes through the real SwipeRecognizer on the phone, with no keyboard and no input. It lists the words
 * the keyboard would offer and how deep the decoder had to look for the expected word. See tools/dev/swipe-baseline.
 */
public final class BaselineRunner {
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = SwipeRecognizer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Builds the recogniser without an Android context, from model files in one folder. */
    private static SwipeRecognizer recognizer(String models, SwipeDecoder decoder, NativeDictionary base, NativeDictionary personal,
                                              NativeDictionary contractions) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        SwipeRecognizer recognizer = (SwipeRecognizer) unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, SwipeRecognizer.class);
        set(recognizer, "shapes", new SwipeShapes(new File(models, "english.combined")));
        Map<String, Double> firstWords;
        try (BufferedReader triples = new BufferedReader(new FileReader(models + "/en_triples.txt"))) {
            firstWords = FirstWords.read(triples);
        }
        set(recognizer, "firstWords", firstWords);
        set(recognizer, "rememberedNextWords", new LinkedHashMap<String, Map<String, Double>>());
        set(recognizer, "decoder", decoder);
        set(recognizer, "base", base);
        set(recognizer, "personal", personal);
        set(recognizer, "contractions", contractions);
        set(recognizer, "learnedWords", Set.of());
        set(recognizer, "rawResults", new SwipeDecoder.Result[0]);
        return recognizer;
    }

    private static float[] floats(JSONArray array) throws Exception {
        float[] out = new float[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = (float) array.getDouble(i);
        return out;
    }

    private static String key(String word) {
        return word.toLowerCase(java.util.Locale.ROOT).replace("'", "");
    }

    public static void main(String[] args) throws Exception {
        String models = args[0];
        NativeDictionary base = new NativeDictionary(models + "/english.combined");
        NativeDictionary personal = new NativeDictionary(new String[0], new float[0]);
        NativeDictionary contractions = SwipeRecognizer.contractionDictionary(base);
        SwipeDecoder decoder = new SwipeDecoder(models);
        decoder.dictionaries(Layout.LETTERS, Layout.KEY_X, Layout.KEY_Y, new long[] { base.pointer(), personal.pointer(), contractions.pointer() });
        SwipeRecognizer recognizer = recognizer(models, decoder, base, personal, contractions);
        float weight = decoder.contextWeight();
        int done = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(args[1])); BufferedWriter out = new BufferedWriter(new FileWriter(args[2]))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                JSONObject entry = new JSONObject(line);
                Swipe gesture = new Swipe(floats(entry.getJSONArray("x")), floats(entry.getJSONArray("y")), floats(entry.getJSONArray("t")));
                String[] context = SentenceContext.words(entry.getString("context"));
                long started = System.nanoTime();
                SwipeDecoder.Result[] top = recognizer.recognise(gesture, context, false, word -> true);
                long millis = (System.nanoTime() - started) / 1_000_000;
                SwipeDecoder.Result[] deep = decoder.recognise(gesture.x, gesture.y, gesture.time, SwipeRecognizer.scoringContext(context), new float[] { 1, 0, 1 }, 100);
                String expected = key(entry.getString("expected"));
                int rank = -1;
                SwipeDecoder.Result found = null;
                JSONArray deepWords = new JSONArray();
                for (int i = 0; i < deep.length; i++) {
                    if (i < 8) deepWords.put(new JSONArray().put(deep[i].word).put(deep[i].score).put(deep[i].ctcScore).put(deep[i].lmScore));
                    if (rank < 0 && key(deep[i].word).equals(expected)) {
                        rank = i + 1;
                        found = deep[i];
                    }
                }
                JSONArray words = new JSONArray();
                JSONArray scores = new JSONArray();
                for (SwipeDecoder.Result result : top) {
                    words.put(result.word);
                    scores.put(new JSONArray().put(result.word).put(result.score).put(result.ctcScore).put(result.lmScore));
                }
                JSONObject result = new JSONObject().put("id", entry.getString("id")).put("top", words).put("scores", scores).put("deep", deepWords)
                    .put("rank", rank).put("deepCount", deep.length).put("millis", millis).put("words", context.length);
                if (found != null) result.put("foundScore", found.score).put("foundCtc", found.ctcScore).put("foundLm", found.lmScore);
                out.write(result.toString());
                out.newLine();
                if (++done % 100 == 0) System.out.println(done + " done");
            }
        }
        System.out.println("Replayed " + done + " strokes, context weight " + weight);
    }
}
