package com.titankeys.engine;

import com.titankeys.keyboard.Layout;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.futo.ml.inference.NativeDictionary;
import org.futo.ml.inference.SwipeDecoder;
import org.json.JSONArray;
import org.json.JSONObject;

/** Dumps every candidate the engine sees for each stroke, so ranking and retrieval can be studied on a computer. */
public final class CandidateRunner {
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = SwipeRecognizer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static float[] floats(JSONArray array) throws Exception {
        float[] out = new float[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = (float) array.getDouble(i);
        return out;
    }

    public static void main(String[] args) throws Exception {
        String models = args[0];
        NativeDictionary base = new NativeDictionary(models + "/english.combined");
        NativeDictionary personal = new NativeDictionary(new String[0], new float[0]);
        NativeDictionary contractions = SwipeRecognizer.contractionDictionary(base);
        SwipeDecoder decoder = new SwipeDecoder(models);
        decoder.dictionaries(Layout.LETTERS, Layout.KEY_X, Layout.KEY_Y, new long[] { base.pointer(), personal.pointer(), contractions.pointer() });
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        SwipeRecognizer recognizer = (SwipeRecognizer) unsafeClass.getMethod("allocateInstance", Class.class).invoke(theUnsafe.get(null), SwipeRecognizer.class);
        SwipeShapes shapes = new SwipeShapes(new File(models, "english.combined"));
        Map<String, Double> firstWords;
        try (BufferedReader triples = new BufferedReader(new FileReader(models + "/en_triples.txt"))) {
            firstWords = FirstWords.read(triples);
        }
        set(recognizer, "shapes", shapes);
        set(recognizer, "firstWords", firstWords);
        set(recognizer, "rememberedNextWords", new LinkedHashMap<String, Map<String, Double>>());
        set(recognizer, "decoder", decoder);
        set(recognizer, "base", base);
        set(recognizer, "personal", personal);
        set(recognizer, "contractions", contractions);
        set(recognizer, "learnedWords", Set.of());
        set(recognizer, "rawResults", new SwipeDecoder.Result[0]);
        int done = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(args[1])); BufferedWriter out = new BufferedWriter(new FileWriter(args[2]))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                JSONObject entry = new JSONObject(line);
                Swipe gesture = new Swipe(floats(entry.getJSONArray("x")), floats(entry.getJSONArray("y")), floats(entry.getJSONArray("t")));
                String[] context = SentenceContext.words(entry.getString("context"));
                JSONObject result = new JSONObject().put("id", entry.getString("id")).put("n", context.length);
                JSONArray top = new JSONArray();
                for (SwipeDecoder.Result r : recognizer.recognise(gesture, context, false, word -> true))
                    top.put(new JSONArray().put(r.word).put(r.score).put(r.ctcScore).put(r.lmScore));
                result.put("top", top);
                JSONArray deep = new JSONArray();
                for (SwipeDecoder.Result r : decoder.recognise(gesture.x, gesture.y, gesture.time, SwipeRecognizer.scoringContext(context), new float[] { 1, 0, 1 }, 48))
                    deep.put(new JSONArray().put(r.word).put(r.score).put(r.ctcScore).put(r.lmScore));
                result.put("deep", deep);
                if (entry.optBoolean("outlines")) {
                    JSONArray outlines = new JSONArray();
                    float[][] path = SwipeShapes.path(gesture);
                    if (path != null)
                        for (SwipeShapes.Fit fit : shapes.closest(path, 30)) outlines.put(new JSONArray().put(fit.word).put(fit.score).put(fit.closeness));
                    result.put("outlines", outlines);
                    JSONObject next = new JSONObject();
                    int kept = 0;
                    Map<String, Double> scores = context.length == 0 ? firstWords : recognizer.nextWords(context);
                    double best = Double.NEGATIVE_INFINITY;
                    for (double v : scores.values()) best = Math.max(best, v);
                    result.put("nextBest", best);
                }
                out.write(result.toString());
                out.newLine();
                if (++done % 1000 == 0) { System.out.println(done + " done"); out.flush(); }
            }
        }
        System.out.println("Dumped " + done);
    }
}
