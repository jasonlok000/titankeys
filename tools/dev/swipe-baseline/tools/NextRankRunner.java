package com.titankeys.engine;

import com.titankeys.keyboard.Layout;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import org.futo.ml.inference.NativeDictionary;
import org.futo.ml.inference.SwipeDecoder;
import org.json.JSONObject;

/**
 * Asks the current context model to predict each word before it is swiped. Reports the word's rank, alone and among
 * words whose first and last letters lie near the stroke's ends, so a prediction list can be judged as a separate path.
 */
public final class NextRankRunner {
    private static float distance(float x, float y, char letter) {
        int key = Layout.LETTERS.indexOf(letter);
        if (key < 0) return 99;
        return (float) Math.hypot((x - Layout.KEY_X[key]) * 10, (y - Layout.KEY_Y[key]) * 3);
    }

    private static String clean(String word) {
        return word.toLowerCase(java.util.Locale.ROOT).replace("'", "");
    }

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
                if (!entry.getBoolean("first")) continue;
                String[] context = SentenceContext.words(entry.getString("context"));
                String expected = clean(entry.getString("expected"));
                float startX = (float) entry.getJSONArray("x").getDouble(0), startY = (float) entry.getJSONArray("y").getDouble(0);
                int last = entry.getJSONArray("x").length() - 1;
                float endX = (float) entry.getJSONArray("x").getDouble(last), endY = (float) entry.getJSONArray("y").getDouble(last);
                SwipeDecoder.Result[] results = decoder.predictNext(SwipeRecognizer.scoringContext(context), 8000);
                int rank = -1, filtered = -1, seen = 0, seenFiltered = 0;
                java.util.HashSet<String> words = new java.util.HashSet<>();
                for (SwipeDecoder.Result result : results) {
                    String word = clean(result.word);
                    if (word.isEmpty() || !word.chars().allMatch(c -> c >= 'a' && c <= 'z') || !words.add(word)) continue;
                    seen++;
                    if (word.equals(expected)) rank = seen;
                    boolean near = distance(startX, startY, word.charAt(0)) <= 1.6f && distance(endX, endY, word.charAt(word.length() - 1)) <= 1.6f;
                    if (near) {
                        seenFiltered++;
                        if (word.equals(expected)) filtered = seenFiltered;
                    }
                }
                out.write(new JSONObject().put("id", entry.getString("id")).put("rank", rank).put("filtered", filtered).put("listed", seen).put("n", context.length).toString());
                out.newLine();
                if (++done % 400 == 0) System.out.println(done);
            }
        }
        System.out.println("done");
    }
}
