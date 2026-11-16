package com.atlas.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Issue #39 pilot, on the PC: completions after typed letters, with the app's own TypedPredictions and next-word model,
 * ranked several ways. For each first try it types 1 letter, 2 letters and so on and records where the right word lands.
 * Variant 0 is TitanKeys 2.0.0 (only words the model holds whole get a score). The others score every matching word:
 * the model's chance (a word of several tokens gets its first token's chance less {@code split} per extra token) plus
 * {@code a} x letters x frequency / 32, so the word list counts more as more letters are typed.
 * The 2.0.0 fallback to the FUTO context model when the model holds no word is native, so here it falls back to frequency.
 * Usage: CompletionPilot predictor-dir vocab.txt english.combined corpus.jsonl out.jsonl
 */
public final class CompletionPilot {
    // {a, split} as above; a = -1 is 2.0.0, -2 frequency only. a = -3 to -6 build on 2.0.0 with the owner's history:
    // -3 learned pairs that start with the letters go first (count 1+), -4 the same with count 2+,
    // -5 a personal count bonus of {split} x ln(1 + times kept), capped at 3 nats, -6 both pairs (1+) and the bonus.
    static final double[][] VARIANTS = {{-1, 0}, {-2, 0}, {.5, 4}, {-3, 0}, {-4, 0}, {-5, .5}, {-5, 1}, {-5, 2}, {-6, 1}};
    private static final Pattern ENTRY = Pattern.compile("^ word=([^,]+),f=(\\d+),flags=([^,]*)");
    private static final Pattern FIELD = Pattern.compile("\"(context|expected|id|set)\": \"((?:[^\"\\\\]|\\\\.)*)\"");

    static String unescape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') { b.append(c); continue; }
            char e = s.charAt(++i);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'u': b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; break;
                default: b.append(e);
            }
        }
        return b.toString();
    }

    /** A flat {"word": count} object named {@code name} from a history line. */
    static Map<String, Integer> flat(String line, String name) {
        Map<String, Integer> out = new HashMap<>();
        int at = line.indexOf("\"" + name + "\": {");
        if (at < 0) return out;
        int end = line.indexOf('}', at);
        Matcher m = Pattern.compile("\"([^\"]+)\": ([0-9]+)").matcher(line.substring(at + name.length() + 4, end));
        while (m.find()) out.put(unescape(m.group(1)), Integer.parseInt(m.group(2)));
        return out;
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        NextWordModel model = new NextWordModel(dir, "model-int8pc.onnx", 32, 4);
        ByteBpe bpe;
        try (BufferedReader v = Files.newBufferedReader(new File(dir, "lm-vocab.txt").toPath(), StandardCharsets.UTF_8);
             BufferedReader m = Files.newBufferedReader(new File(dir, "lm-merges.txt").toPath(), StandardCharsets.UTF_8)) {
            bpe = new ByteBpe(v, m, false);
        }
        Map<String, Integer> freq = new HashMap<>();
        try (BufferedReader r = Files.newBufferedReader(new File(args[2]).toPath(), StandardCharsets.UTF_8)) {
            for (String line; (line = r.readLine()) != null; ) {
                Matcher m = ENTRY.matcher(line);
                if (m.find() && !m.group(3).contains("not_a_word")) freq.merge(m.group(1).toLowerCase(java.util.Locale.ROOT), Integer.parseInt(m.group(2)), Math::max);
            }
        }
        TypedPredictions typed;
        try (FileReader vocabulary = new FileReader(args[1]); FileReader dictionary = new FileReader(args[2])) {
            typed = new TypedPredictions(vocabulary, dictionary, w -> freq.getOrDefault(w, 0));
        }
        // Words by first letter, for scoring every word that starts with the typed letters.
        Map<Character, List<String>> byFirst = new HashMap<>();
        typed.forEachWord((w, f) -> { if (!w.isEmpty()) byFirst.computeIfAbsent(w.charAt(0), c -> new ArrayList<>()).add(w); });
        Map<String, Integer> tokens = new HashMap<>();
        // Optional history (tools/history.py): what the owner kept before each word.
        Map<String, String> history = new HashMap<>();
        if (args.length > 5)
            for (String line : Files.readAllLines(new File(args[5]).toPath(), StandardCharsets.UTF_8)) {
                Matcher m = Pattern.compile("\"id\": \"([^\"]+)\"").matcher(line);
                if (m.find()) history.put(m.group(1), line);
            }
        int done = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(args[3])); BufferedWriter out = new BufferedWriter(new FileWriter(args[4]))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                if (!line.contains("\"first\": true")) continue;
                Map<String, String> f = new HashMap<>();
                Matcher m = FIELD.matcher(line);
                while (m.find()) f.putIfAbsent(m.group(1), unescape(m.group(2)));
                String text = f.getOrDefault("context", ""), expected = TypedPredictions.key(f.getOrDefault("expected", ""));
                if (expected.isEmpty() || !Character.isLetter(expected.charAt(0))) continue;
                float[] chances = model.after(text);
                Map<String, Integer> counts = Map.of(), pairs = Map.of();
                String hist = history.get(f.get("id"));
                if (hist != null) { counts = flat(hist, "counts"); pairs = flat(hist, "pairs"); }
                int[] found = new int[VARIANTS.length];
                java.util.Arrays.fill(found, -1);
                StringBuilder ranks = new StringBuilder("[");
                List<String> pool = byFirst.getOrDefault(expected.charAt(0), List.of());
                for (int k = 1; k < expected.length(); k++) {
                    String letters = expected.substring(0, k);
                    Map<String, Double> whole = model.wordsStarting(chances, letters, typed::contains);
                    if (k > 1) ranks.append(',');
                    ranks.append('[');
                    for (int v = 0; v < VARIANTS.length; v++) {
                        double a = VARIANTS[v][0], split = VARIANTS[v][1];
                        Map<String, Double> map;
                        if (a <= -3) {
                            map = new HashMap<>(whole);
                            if (a == -5 || a == -6) {
                                for (Map.Entry<String, Integer> c : counts.entrySet()) {
                                    String w = TypedPredictions.key(c.getKey());
                                    if (!w.startsWith(letters) || !typed.contains(w)) continue;
                                    Double lm = map.get(w);
                                    if (lm == null) {
                                        int n = tokens.computeIfAbsent(w, x -> bpe.encode(" " + x).length);
                                        lm = model.word(chances, w) - 2.0 * Math.max(0, n - 1);
                                    }
                                    map.put(w, lm + Math.min(3, split * Math.log(1 + c.getValue())));
                                }
                            }
                        } else if (a == -1) map = whole;
                        else if (a == -2) map = Map.of();
                        else {
                            map = new HashMap<>();
                            for (String w : pool) {
                                if (!w.startsWith(letters)) continue;
                                Double lm = whole.get(w);
                                if (lm == null) {
                                    int n = tokens.computeIfAbsent(w, x -> bpe.encode(" " + x).length);
                                    lm = model.word(chances, w) - split * Math.max(0, n - 1);
                                }
                                map.put(w, lm + a * k * freq.getOrDefault(w, 0) / 32.0);
                            }
                        }
                        String[] shown = typed.suggest(letters, List.of(), 0, w -> true, map);
                        if (a == -3 || a == -4 || a == -6) {
                            // Learned pairs that start with the letters, most kept first, in front as before any letter.
                            int min = a == -4 ? 2 : 1;
                            List<String> learned = new ArrayList<>();
                            pairs.entrySet().stream().filter(p -> p.getValue() >= min && TypedPredictions.key(p.getKey()).startsWith(letters))
                                    .sorted((p, q) -> q.getValue() - p.getValue()).forEach(p -> learned.add(TypedPredictions.key(p.getKey())));
                            List<String> merged = new ArrayList<>(learned);
                            for (String w : shown) if (!merged.contains(TypedPredictions.key(w))) merged.add(w);
                            shown = merged.toArray(new String[0]);
                        }
                        int at = -1;
                        for (int i = 0; i < shown.length; i++) if (TypedPredictions.key(shown[i]).equals(expected)) { at = i; break; }
                        if (found[v] < 0 && at >= 0 && at < 3) found[v] = k;
                        if (v > 0) ranks.append(',');
                        ranks.append(at);
                    }
                    ranks.append(']');
                }
                ranks.append(']');
                StringBuilder fs = new StringBuilder("[");
                for (int v = 0; v < found.length; v++) fs.append(v > 0 ? "," : "").append(found[v]);
                out.write("{\"id\":\"" + f.get("id") + "\",\"set\":\"" + f.get("set") + "\",\"len\":" + expected.length()
                        + ",\"ranks\":" + ranks + ",\"found\":" + fs.append(']') + "}");
                out.newLine();
                if (++done % 300 == 0) System.out.println(done + " words");
            }
        }
        System.out.println(done + " words done");
        model.close();
    }
}
