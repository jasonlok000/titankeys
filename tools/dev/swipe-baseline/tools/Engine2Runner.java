package com.titankeys.engine;

import com.titankeys.keyboard.Layout;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.futo.ml.inference.NativeDictionary;
import org.futo.ml.inference.SwipeDecoder;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Runs swipe engine 2.0 on the phone exactly as the app does (FUTO engine, CleverKeys, next-word model, ranker), for
 * each stroke, and times each part. Usage: Engine2Runner futo-models-dir swipe2-dir corpus.jsonl out.jsonl
 */
public final class Engine2Runner {
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = SwipeRecognizer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static long rssMb() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/status"))) {
            for (String line; (line = r.readLine()) != null; )
                if (line.startsWith("VmRSS:")) return Long.parseLong(line.replaceAll("[^0-9]", "")) / 1024;
        } catch (Exception ignored) { }
        return -1;
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
        Map<String, Double> firstWords;
        try (BufferedReader triples = new BufferedReader(new FileReader(models + "/en_triples.txt"))) {
            firstWords = FirstWords.read(triples);
        }
        set(recognizer, "shapes", new SwipeShapes(new File(models, "english.combined")));
        set(recognizer, "firstWords", firstWords);
        // The app's cache keeps 6 contexts. A plain map here would keep every context and grow without end.
        set(recognizer, "rememberedNextWords", new LinkedHashMap<String, Map<String, Double>>(16, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Map<String, Double>> eldest) { return size() > 6; }
        });
        set(recognizer, "decoder", decoder);
        set(recognizer, "base", base);
        set(recognizer, "personal", personal);
        set(recognizer, "contractions", contractions);
        set(recognizer, "learnedWords", Set.of());
        set(recognizer, "rawResults", new SwipeDecoder.Result[0]);
        // FUTO_ONLY=1 skips engine 2.0, to tell its memory use from the FUTO engine's.
        boolean futoOnly = "1".equals(System.getenv("FUTO_ONLY"));
        System.out.printf("FUTO engine loaded, memory %d MB%n", rssMb());
        long loadStart = System.nanoTime();
        SwipeModels engine = futoOnly ? null : SwipeModels.load(new File(args[1]), new File(models, "english.combined"));
        System.out.printf("engine 2.0 loaded in %.0f ms, memory %d MB%n", (System.nanoTime() - loadStart) / 1e6, rssMb());
        int done = 0;
        double[] totals = new double[5];
        try (BufferedReader in = new BufferedReader(new FileReader(args[2])); BufferedWriter out = new BufferedWriter(new FileWriter(args[3]))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                JSONObject entry = new JSONObject(line);
                Swipe gesture = new Swipe(floats(entry.getJSONArray("x")), floats(entry.getJSONArray("y")), floats(entry.getJSONArray("t")));
                String text = entry.getString("context");
                String[] context = SentenceContext.words(text);
                // The next-word chances are worked out after the word before, as the app's prepare() does, so time it apart.
                long t0 = System.nanoTime();
                if (engine != null) engine.prepare(text);
                long t1 = System.nanoTime();
                SwipeDecoder.Result[] shown = recognizer.recognise(gesture, context, false, word -> true);
                long t2 = System.nanoTime();
                if (engine != null) engine.reader.read(gesture);
                long t3 = System.nanoTime();
                SwipeDecoder.Result[] ranked = engine == null ? shown : engine.rank(shown, recognizer.readings(), gesture, text, word -> true);
                long t4 = System.nanoTime();
                double[] ms = { (t1 - t0) / 1e6, (t2 - t1) / 1e6, (t3 - t2) / 1e6, (t4 - t3) / 1e6 - (t3 - t2) / 1e6, (t4 - t1) / 1e6 - (t3 - t2) / 1e6 };
                for (int i = 0; i < 5; i++) totals[i] += ms[i];
                JSONArray top = new JSONArray(), old = new JSONArray();
                for (SwipeDecoder.Result r : ranked) top.put(r.word);
                for (SwipeDecoder.Result r : shown) old.put(r.word);
                out.write(new JSONObject().put("id", entry.getString("id")).put("top", top).put("today", old)
                        .put("ms", new JSONArray(Arrays.stream(ms).map(v -> Math.round(v * 10) / 10.0).toArray())).toString());
                out.newLine();
                if (++done % 200 == 0) {
                    System.gc();
                    System.out.println(done + " done, memory " + rssMb() + " MB, Java heap " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576 + " MB");
                }
                if (args.length > 4 && done >= Integer.parseInt(args[4])) break;
            }
        }
        System.out.printf("%d strokes. Average ms: next-word model %.1f (done after the word before), FUTO engine %.1f, CleverKeys %.1f, ranker %.1f, whole swipe %.1f%n",
                done, totals[0] / done, totals[1] / done, totals[2] / done, totals[3] / done, totals[4] / done);
        if (engine != null) engine.close();
    }
}
