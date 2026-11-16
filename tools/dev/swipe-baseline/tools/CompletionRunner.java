package com.titankeys.engine;

import com.titankeys.keyboard.Layout;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.futo.ml.inference.NativeDictionary;
import org.futo.ml.inference.SwipeDecoder;
import org.json.JSONObject;

/**
 * Word completions after some letters, with the app's own TypedPredictions, ranked three ways: by the current context model
 * (old), by the next-word model's whole words with frequency after them (model), and by the next-word model's whole words,
 * then the current context model's words (both). For each first try it types 1 letter, 2 letters and so on, and records
 * where the right word is. Usage: CompletionRunner futo-models-dir swipe2-dir corpus.jsonl out.jsonl
 */
public final class CompletionRunner {
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = SwipeRecognizer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int rank(String[] list, String expected) {
        for (int i = 0; i < list.length; i++) if (TypedPredictions.key(list[i]).equals(expected)) return i;
        return -1;
    }

    /** Each word scores 1 / (c + its place) in each list it is in, so a word both models like comes first. */
    private static Map<String, Double> fuse(Map<String, Double> a, Map<String, Double> b, int c) {
        Map<String, Double> out = new HashMap<>();
        for (Map<String, Double> list : List.of(a, b)) {
            List<String> order = list.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .map(Map.Entry::getKey).toList();
            for (int i = 0; i < order.size(); i++) out.merge(order.get(i), 1.0 / (c + i + 1), Double::sum);
        }
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
        set(recognizer, "firstWords", firstWords);
        set(recognizer, "rememberedNextWords", new LinkedHashMap<String, Map<String, Double>>(16, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Map<String, Double>> eldest) { return size() > 6; }
        });
        set(recognizer, "decoder", decoder);
        set(recognizer, "base", base);
        set(recognizer, "personal", personal);
        set(recognizer, "contractions", contractions);
        set(recognizer, "learnedWords", Set.of());
        TypedPredictions typed;
        try (FileReader vocabulary = new FileReader(models + "/hungry_jellyfish/vocab.txt");
             FileReader dictionary = new FileReader(models + "/english.combined")) {
            typed = new TypedPredictions(vocabulary, dictionary, recognizer::frequency);
        }
        SwipeModels engine = SwipeModels.load(new File(args[1]), new File(models, "english.combined"));
        int done = 0, calls = 0;
        double modelMs = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(args[2])); BufferedWriter out = new BufferedWriter(new FileWriter(args[3]))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                JSONObject entry = new JSONObject(line);
                if (!entry.getBoolean("first")) continue;
                String text = entry.getString("context");
                String expected = TypedPredictions.key(entry.getString("expected"));
                if (expected.isEmpty()) continue;
                Map<String, Double> old = recognizer.nextWords(SentenceContext.words(text));
                engine.prepare(text);
                int[] found = { -1, -1, -1, -1 };
                org.json.JSONArray ranks = new org.json.JSONArray();
                for (int k = 1; k < expected.length(); k++) {
                    String letters = expected.substring(0, k);
                    long t0 = System.nanoTime();
                    Map<String, Double> model = engine.completions(text, letters, typed::contains);
                    modelMs += (System.nanoTime() - t0) / 1e6;
                    calls++;
                    Map<String, Double> oldHere = new HashMap<>();
                    old.forEach((word, score) -> { if (word.startsWith(letters)) oldHere.put(word, score); });
                    Map<String, Double>[] maps = new Map[] { old, model.isEmpty() ? old : model, fuse(model, oldHere, 5), fuse(model, oldHere, 20) };
                    int[] at = new int[4];
                    for (int v = 0; v < 4; v++) {
                        at[v] = rank(typed.suggest(letters, List.of(), 0, word -> true, maps[v]), expected);
                        if (found[v] < 0 && at[v] >= 0 && at[v] < 3) found[v] = k;
                    }
                    ranks.put(new org.json.JSONArray(at));
                    if (k >= 3 && found[0] > 0 && found[1] > 0 && found[2] > 0 && found[3] > 0) break;
                }
                out.write(new JSONObject().put("id", entry.getString("id")).put("set", entry.getString("set"))
                        .put("len", expected.length()).put("ranks", ranks).put("found", new org.json.JSONArray(found)).toString());
                out.newLine();
                if (++done % 200 == 0) System.out.println(done + " done");
            }
        }
        System.out.printf("%d words, next-word model completions %.2f ms a call%n", done, modelMs / Math.max(1, calls));
        engine.close();
    }
}
