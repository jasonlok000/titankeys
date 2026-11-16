package com.titankeys.engine;

import com.titankeys.keyboard.Layout;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import org.futo.ml.inference.NativeDictionary;
import org.futo.ml.inference.SwipeDecoder;
import org.json.JSONArray;
import org.json.JSONObject;

/** Asks the context model how likely the next word is after each candidate word. */
public final class NextWordRunner {
    public static void main(String[] args) throws Exception {
        String models = args[0];
        NativeDictionary base = new NativeDictionary(models + "/english.combined");
        NativeDictionary personal = new NativeDictionary(new String[0], new float[0]);
        NativeDictionary contractions = SwipeRecognizer.contractionDictionary(base);
        SwipeDecoder decoder = new SwipeDecoder(models);
        decoder.dictionaries(Layout.LETTERS, Layout.KEY_X, Layout.KEY_Y, new long[] { base.pointer(), personal.pointer(), contractions.pointer() });
        try (BufferedReader in = new BufferedReader(new FileReader(args[1])); BufferedWriter out = new BufferedWriter(new FileWriter(args[2]))) {
            int done = 0;
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                JSONObject entry = new JSONObject(line);
                String[] context = SentenceContext.words(entry.getString("context"));
                String next = entry.getString("next");
                JSONArray cands = entry.getJSONArray("cands");
                JSONArray scores = new JSONArray();
                for (int i = 0; i < cands.length(); i++) {
                    String[] extended = java.util.Arrays.copyOf(context, context.length + 1);
                    extended[context.length] = cands.getString(i).toLowerCase(java.util.Locale.ROOT);
                    SwipeDecoder.Result[] results = decoder.predictNext(extended, 8000);
                    double best = Double.NEGATIVE_INFINITY, found = Double.NaN;
                    double floor = results.length == 0 ? -30 : results[results.length - 1].score;
                    for (SwipeDecoder.Result r : results) best = Math.max(best, r.score);
                    for (SwipeDecoder.Result r : results) if (r.word.equalsIgnoreCase(next)) { found = r.score; break; }
                    scores.put(Double.isNaN(found) ? floor - best - 1 : found - best);
                }
                out.write(new JSONObject().put("id", entry.getString("id")).put("logp", scores).toString());
                out.newLine();
                if (++done % 200 == 0) System.out.println(done);
            }
        }
        System.out.println("done");
    }
}
