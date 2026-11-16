package com.atlas.engine;

import com.atlas.keyboard.Layout;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.futo.ml.inference.NativeDictionary;
import org.futo.ml.inference.SwipeDecoder;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Saved words and a boost for them (owner's request, Nov 10): runs swipe engine 2.0 on the phone as the app does, with a
 * list of saved words given to both engines as the app gives them, and ranks every candidate. For each boost, a saved
 * word's ranker score goes up by that much and the top three are written. With no saved list, it runs as the app does
 * with nothing saved. Usage: SavedRunner futo-models-dir swipe2-dir corpus.jsonl out.jsonl [saved.tsv] [boosts]
 */
public final class SavedRunner {
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
        List<String> savedWords = new ArrayList<>();
        List<Float> savedFrequencies = new ArrayList<>();
        if (args.length > 4 && !args[4].isEmpty()) {
            try (BufferedReader in = new BufferedReader(new FileReader(args[4]))) {
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    String[] parts = line.split("\t");
                    if (parts.length == 2) { savedWords.add(parts[0]); savedFrequencies.add(Float.parseFloat(parts[1])); }
                }
            }
        }
        double[] boosts = java.util.Arrays.stream((args.length > 5 ? args[5] : "0").split(",")).mapToDouble(Double::parseDouble).toArray();
        String[] words = savedWords.toArray(new String[0]);
        float[] frequencies = new float[words.length];
        for (int i = 0; i < words.length; i++) frequencies[i] = savedFrequencies.get(i);
        Set<String> savedKeys = new java.util.HashSet<>();
        Map<String, Integer> savedFrequency = new HashMap<>();
        for (int i = 0; i < words.length; i++) {
            savedKeys.add(CtcWords.key(words[i]));
            savedFrequency.merge(CtcWords.key(words[i]), Math.round(frequencies[i]), Math::max);
        }

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
        set(recognizer, "rememberedNextWords", new LinkedHashMap<String, Map<String, Double>>(16, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Map<String, Double>> eldest) { return size() > 6; }
        });
        set(recognizer, "decoder", decoder);
        set(recognizer, "base", base);
        set(recognizer, "personal", personal);
        set(recognizer, "contractions", contractions);
        set(recognizer, "learnedWords", Set.of());
        set(recognizer, "rawResults", new SwipeDecoder.Result[0]);
        boolean personalized = words.length > 0;
        // As SwipeTyping.syncDictionary does: the FUTO engine's personal dictionary, then the ranker's frequencies.
        if (personalized) recognizer.personalWords(words, frequencies);
        SwipeModels engine = SwipeModels.load(new File(args[1]), new File(models, "english.combined"));
        if (personalized) engine.personalWords(words, frequencies);
        System.out.printf("loaded, %d saved words, boosts %s%n", words.length, java.util.Arrays.toString(boosts));
        int done = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(args[2])); BufferedWriter out = new BufferedWriter(new FileWriter(args[3]))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                JSONObject entry = new JSONObject(line);
                Swipe gesture = new Swipe(floats(entry.getJSONArray("x")), floats(entry.getJSONArray("y")), floats(entry.getJSONArray("t")));
                String text = entry.getString("context");
                String[] context = SentenceContext.words(text);
                engine.prepare(text);
                SwipeDecoder.Result[] shown = recognizer.recognise(gesture, context, personalized, word -> true);
                SwipeDecoder.Result[][] readings = recognizer.readings();
                // SwipeModels.rank, keeping every candidate's score.
                CtcWords.Candidate[] ck = engine.reader.read(gesture);
                float[] chances = engine.predictor.after(text);
                Map<String, String> spellings = new HashMap<>();
                Map<String, double[]> cand = SwipeRanker.features(shown, readings[0], readings[1], ck, engine.reader.words(),
                        k -> engine.predictor.word(chances, k), gesture, text, spellings);
                for (Map.Entry<String, double[]> e : cand.entrySet())
                    if (e.getValue()[12] < 0 && savedFrequency.containsKey(e.getKey())) e.getValue()[12] = savedFrequency.get(e.getKey());
                for (Map.Entry<String, double[]> e : cand.entrySet())
                    if (e.getValue()[12] < 0 && BuiltInWords.spelling(e.getKey()) != null) e.getValue()[12] = BuiltInWords.FREQUENCY;
                SwipeRanker.Choice[] all = engine.ranker.rank(cand, spellings, word -> true, cand.size());
                JSONObject tops = new JSONObject();
                for (double boost : boosts) {
                    List<SwipeRanker.Choice> order = new ArrayList<>(java.util.Arrays.asList(all));
                    // A stable sort, as the ranker's own.
                    order.sort((a, b) -> Double.compare(b.score + (savedKeys.contains(b.key) ? boost : 0), a.score + (savedKeys.contains(a.key) ? boost : 0)));
                    JSONArray top = new JSONArray();
                    for (int i = 0; i < Math.min(3, order.size()); i++) top.put(order.get(i).word);
                    tops.put(String.valueOf(boost), top);
                }
                JSONArray savedFound = new JSONArray();
                for (SwipeRanker.Choice c : all) if (savedKeys.contains(c.key)) savedFound.put(c.word);
                out.write(new JSONObject().put("id", entry.getString("id")).put("tops", tops).put("savedCandidates", savedFound).toString());
                out.newLine();
                if (++done % 200 == 0) { System.gc(); System.out.println(done + " done"); }
            }
        }
        System.out.println(done + " strokes");
    }
}
