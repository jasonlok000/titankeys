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

/**
 * #31 parameter sweep: reads every stroke under several settings of the FUTO engine's tuning values in one run, and
 * writes for each setting the engine evidence the ranker reads (shown words, plain and shifted readings). The sweep script
 * compiles a copy of SwipeRecognizer whose swept constants are not final, so they can be set here; the app is unchanged.
 * Usage: SweepRunner models-dir corpus.jsonl out-prefix name:FIELD=value,FIELD=value ...
 */
public final class SweepRunner {
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = SwipeRecognizer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setStatic(String name, String value) throws Exception {
        Field field = SwipeRecognizer.class.getDeclaredField(name);
        field.setAccessible(true);
        if (field.getType() == int.class) field.setInt(null, Integer.parseInt(value));
        else field.setFloat(null, Float.parseFloat(value));
    }

    private static float[] floats(JSONArray array) throws Exception {
        float[] out = new float[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = (float) array.getDouble(i);
        return out;
    }

    private static JSONArray list(SwipeDecoder.Result[] results) throws Exception {
        JSONArray out = new JSONArray();
        for (SwipeDecoder.Result r : results) out.put(new JSONArray().put(r.word).put(r.score).put(r.ctcScore).put(r.lmScore));
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
        Map<String, Double> firstWords;
        try (BufferedReader triples = new BufferedReader(new FileReader(models + "/en_triples.txt"))) {
            firstWords = FirstWords.read(triples);
        }
        set(recognizer, "shapes", new SwipeShapes(new File(models, "english.combined")));
        set(recognizer, "firstWords", firstWords);
        // The app's cache keeps 6 contexts; a plain map would run the phone out of memory.
        set(recognizer, "rememberedNextWords", new LinkedHashMap<String, Map<String, Double>>(16, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Map<String, Double>> eldest) { return size() > 6; }
        });
        set(recognizer, "decoder", decoder);
        set(recognizer, "base", base);
        set(recognizer, "personal", personal);
        set(recognizer, "contractions", contractions);
        set(recognizer, "learnedWords", Set.of());
        set(recognizer, "rawResults", new SwipeDecoder.Result[0]);
        for (int a = 3; a < args.length; a++) {
            String name = args[a].substring(0, args[a].indexOf(':'));
            Map<String, String> values = new LinkedHashMap<>();
            for (String pair : args[a].substring(args[a].indexOf(':') + 1).split(","))
                if (pair.contains("=")) values.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
            for (Map.Entry<String, String> v : values.entrySet()) setStatic(v.getKey(), v.getValue());
            set(recognizer, "rememberedNextWords", new LinkedHashMap<String, Map<String, Double>>(16, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Map<String, Double>> eldest) { return size() > 6; }
            });
            int done = 0;
            long total = 0;
            try (BufferedReader in = new BufferedReader(new FileReader(args[1]));
                 BufferedWriter out = new BufferedWriter(new FileWriter(args[2] + "-" + name + ".jsonl"))) {
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    JSONObject entry = new JSONObject(line);
                    Swipe gesture = new Swipe(floats(entry.getJSONArray("x")), floats(entry.getJSONArray("y")), floats(entry.getJSONArray("t")));
                    String[] context = SentenceContext.words(entry.getString("context"));
                    long began = System.nanoTime();
                    SwipeDecoder.Result[] top = recognizer.recognise(gesture, context, false, word -> true);
                    total += System.nanoTime() - began;
                    SwipeDecoder.Result[][] readings = recognizer.readings();
                    out.write(new JSONObject().put("id", entry.getString("id")).put("top", list(top)).put("plain", list(readings[0]))
                            .put("shifted", list(readings[1])).toString());
                    out.newLine();
                    done++;
                }
            }
            System.out.printf("%s %s: %d strokes, %.1f ms a stroke%n", name, values, done, total / 1e6 / Math.max(1, done));
            // Back to the shipped values before the next setting, so each setting changes only what it names.
            for (String field : values.keySet()) setStatic(field, BASELINE.get(field));
        }
    }

    /** The shipped values, as in SwipeRecognizer.java. */
    static final Map<String, String> BASELINE = Map.of("CANDIDATES", "48", "SAVED_WORD_CANDIDATES", "48", "SWIPE_STRETCH", "1.2",
            "SHIFT_RIGHT", ".06", "FIRST_WORD_CONTEXT", ".5");
}
