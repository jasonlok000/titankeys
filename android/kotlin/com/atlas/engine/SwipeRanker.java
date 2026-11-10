package com.atlas.engine;

import org.futo.ml.inference.SwipeDecoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orders a swipe's candidate words with trees learned from labelled strokes (tools/train_ranker.py). It weighs the
 * engine's own lists, the fine-tuned CleverKeys reading and the next-word model together. Each feature here must be
 * worked out exactly as the training script does, or the trees read nonsense.
 */
public final class SwipeRanker {
    static final String[] FEATURES = {"final", "p_rank", "p_ctc", "p_score", "lm", "s_rank", "s_score", "ck_rank", "ck_path", "ck_final",
            "lm_logp", "lm_gap", "freq", "len", "stroke_len", "time", "words_before", "has_context"};
    private static final Pattern WORDS = Pattern.compile("[A-Za-z']+");
    private static final int SHOWN = 3;

    private final List<Tree> trees = new ArrayList<>();
    public final int lmCap;

    private static final class Tree {
        int[] feature, left, right, decision;
        double[] threshold, leaf;

        double value(double[] x) {
            if (feature == null) return leaf[0];
            int node = 0;
            while (node >= 0) {
                double v = x[feature[node]];
                int missing = (decision[node] >> 2) & 3;
                if (Double.isNaN(v) && missing != 2) v = 0;
                if ((missing == 1 && Math.abs(v) <= 1e-35) || (missing == 2 && Double.isNaN(v)))
                    node = (decision[node] & 2) != 0 ? left[node] : right[node];
                else node = v <= threshold[node] ? left[node] : right[node];
            }
            return leaf[~node];
        }
    }

    /** One ranked word: how it is written, its comparison key, and the ranker's score. */
    public static final class Choice {
        public final String word, key;
        public final double score;

        Choice(String word, String key, double score) {
            this.word = word; this.key = key; this.score = score;
        }
    }

    public SwipeRanker(BufferedReader model) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        int cap = 32;
        for (String line; (line = model.readLine()) != null; ) {
            if (line.startsWith("feature_names=")) {
                if (!line.substring(14).trim().equals(String.join(" ", FEATURES)))
                    throw new IOException("The ranker was trained on other features");
            } else if (line.startsWith("lm_cap=")) {
                cap = Integer.parseInt(line.substring(7).trim());
            } else if (line.startsWith("Tree=")) {
                if (!fields.isEmpty()) trees.add(tree(fields));
                fields.clear();
                fields.put("Tree", line);
            } else if (!fields.isEmpty() && line.contains("=")) {
                fields.put(line.substring(0, line.indexOf('=')), line.substring(line.indexOf('=') + 1));
            }
        }
        if (!fields.isEmpty()) trees.add(tree(fields));
        if (trees.isEmpty()) throw new IOException("The ranker has no trees");
        lmCap = cap;
    }

    private static Tree tree(Map<String, String> f) {
        Tree t = new Tree();
        t.leaf = doubles(f.get("leaf_value"));
        if (Integer.parseInt(f.get("num_leaves")) > 1) {
            t.feature = ints(f.get("split_feature"));
            t.threshold = doubles(f.get("threshold"));
            t.decision = ints(f.get("decision_type"));
            t.left = ints(f.get("left_child"));
            t.right = ints(f.get("right_child"));
        }
        return t;
    }

    private static double[] doubles(String s) { return Arrays.stream(s.trim().split(" ")).mapToDouble(SwipeRanker::number).toArray(); }

    /** LightGBM writes infinite thresholds as "inf". */
    private static double number(String s) {
        switch (s) {
            case "inf": case "+inf": return Double.POSITIVE_INFINITY;
            case "-inf": return Double.NEGATIVE_INFINITY;
            case "nan": case "-nan": return Double.NaN;
            default: return Double.parseDouble(s);
        }
    }

    private static int[] ints(String s) { return Arrays.stream(s.trim().split(" ")).mapToInt(Integer::parseInt).toArray(); }

    public int trees() { return trees.size(); }

    public double score(double[] x) {
        double sum = 0;
        for (Tree t : trees) sum += t.value(x);
        return sum;
    }

    /**
     * Every candidate with its features, keyed by comparison form, in the order the training script finds them.
     * {@code lm} gives the next-word model's log chance for a key.
     */
    public static Map<String, double[]> features(SwipeDecoder.Result[] shown, SwipeDecoder.Result[] plain, SwipeDecoder.Result[] shifted,
                                                 CtcWords.Candidate[] ck, CtcWords words, ToDoubleFunction<String> lm, Swipe stroke, String context,
                                                 Map<String, String> spellings) {
        Map<String, double[]> cand = new LinkedHashMap<>();
        for (int rank = 0; rank < shown.length; rank++) {
            double[] g = get(cand, spellings, shown[rank].word);
            if (g != null && g[0] == 9) g[0] = rank;
        }
        for (int rank = 0; rank < plain.length; rank++) {
            double[] g = get(cand, spellings, plain[rank].word);
            if (g != null && rank < g[1]) {
                g[1] = rank; g[2] = plain[rank].ctcScore; g[3] = plain[rank].score; g[4] = plain[rank].lmScore;
            }
        }
        for (int rank = 0; rank < shifted.length; rank++) {
            double[] g = get(cand, spellings, shifted[rank].word);
            if (g != null && rank < g[5]) {
                g[5] = rank; g[6] = shifted[rank].score;
            }
        }
        if (ck != null)
            for (int rank = 0; rank < ck.length; rank++) {
                if (!cand.containsKey(ck[rank].key)) spellings.putIfAbsent(ck[rank].key, words.spelling(ck[rank].key));
                double[] g = get(cand, null, ck[rank].key);
                if (rank < g[7]) {
                    g[7] = rank; g[8] = round3(ck[rank].path); g[9] = round3(ck[rank].score);
                }
            }
        double length = 0;
        for (int i = 1; i < stroke.x.length; i++) {
            double dx = ((double) stroke.x[i] - stroke.x[i - 1]) * 10, dy = ((double) stroke.y[i] - stroke.y[i - 1]) * 3;
            length += Math.sqrt(dx * dx + dy * dy);
        }
        double time = (double) stroke.time[stroke.time.length - 1] - stroke.time[0];
        int before = 0;
        Matcher m = WORDS.matcher(context.substring(Math.max(0, context.length() - 300)));
        while (m.find()) before++;
        double topLm = Double.NEGATIVE_INFINITY;
        Map<String, Double> lms = new LinkedHashMap<>();
        for (String k : cand.keySet()) {
            double v = lm == null ? -99 : lm.applyAsDouble(k);
            lms.put(k, v);
            topLm = Math.max(topLm, v);
        }
        for (Map.Entry<String, double[]> e : cand.entrySet()) {
            double[] g = e.getValue();
            String k = e.getKey();
            g[10] = lms.get(k);
            g[11] = lms.get(k) - topLm;
            g[12] = words == null || !words.contains(k) ? -1 : words.count(k);
            g[13] = k.length();
            g[14] = length;
            g[15] = time;
            g[16] = before;
            g[17] = Math.min(before, 1);
            // The training script stores features as 32-bit floats.
            for (int i = 0; i < g.length; i++) g[i] = (double) (float) g[i];
        }
        return cand;
    }

    private static double round3(double v) { return Math.rint(v * 1000) / 1000; }

    private static double[] get(Map<String, double[]> cand, Map<String, String> spellings, String word) {
        String k = CtcWords.key(word);
        if (k.isEmpty()) return null;
        if (spellings != null) spellings.putIfAbsent(k, word);
        return cand.computeIfAbsent(k, x -> new double[]{9, 99, Double.NaN, Double.NaN, Double.NaN, 99, Double.NaN, 99, Double.NaN, Double.NaN,
                0, 0, 0, 0, 0, 0, 0, 0});
    }

    /** The best words, best first, at most {@code limit}, leaving out words {@code allowed} refuses. */
    public Choice[] rank(Map<String, double[]> cand, Map<String, String> spellings, Predicate<String> allowed, int limit) {
        List<Choice> out = new ArrayList<>(cand.size());
        for (Map.Entry<String, double[]> e : cand.entrySet()) {
            String word = spellings.getOrDefault(e.getKey(), e.getKey());
            if (allowed.test(word)) out.add(new Choice(word, e.getKey(), score(e.getValue())));
        }
        // A stable sort, so ties keep the order the candidates were found in, as numpy's stable sort does.
        out.sort((a, b) -> Double.compare(b.score, a.score));
        return out.subList(0, Math.min(limit, out.size())).toArray(new Choice[0]);
    }

    public static int shown() { return SHOWN; }
}
