package com.atlas.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The word search for the CleverKeys encoder: a dictionary trie walked with a pruned Viterbi beam over the encoder's
 * letter chances. It matches tools/ck_core.py step for step, because the ranker learned from that version's output.
 */
public final class CtcWords {
    static final double GAMMA = .9, BETA = .25, GP = .25, BP = .9882, LAMBDA = 2.0;
    static final int BEAM = 100, KEEP = 80, LETTERS = 26, BLANK = 26;
    private static final Pattern ENTRY = Pattern.compile("^ word=([^,]+),f=(\\d+),flags=([^,]*)");
    private static final Map<String, String> SAME_WORD = Map.of("neighbour", "neighbor", "tyres", "tires", "favourite", "favorite",
            "colour", "color", "grey", "gray");

    /** One word the search found: its path score and the score it is ordered by. */
    public static final class Candidate {
        public final String key;
        public final double path, score;

        Candidate(String key, double path, double score) {
            this.key = key; this.path = path; this.score = score;
        }
    }

    // The trie, with each node's children in the order the words first used them, as the Python version keeps them.
    private int[] firstChild = new int[1 << 16], lastChild = new int[1 << 16], nextSibling = new int[1 << 16], word = new int[1 << 16];
    private byte[] letter = new byte[1 << 16];
    private short[] depth = new short[1 << 16];
    private int nodes;
    private final List<String> keys = new ArrayList<>();
    private final List<Integer> counts = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();
    private final Map<String, String> spellings = new HashMap<>();

    public CtcWords(BufferedReader dictionary) throws IOException {
        Map<String, Integer> vocab = new LinkedHashMap<>();
        Map<String, Integer> best = new HashMap<>();
        for (String line; (line = dictionary.readLine()) != null; ) {
            Matcher m = ENTRY.matcher(line);
            if (!m.find() || m.group(3).contains("not_a_word")) continue;
            String original = m.group(1);
            String k = key(original);
            if (k.isEmpty() || !letters(k)) continue;
            int f = Math.max(1, Integer.parseInt(m.group(2)));
            vocab.merge(k, f, Math::max);
            // The spelling shown for a key is its most frequent written form, such as "don't" for "dont".
            if (f > best.getOrDefault(k, -1)) {
                best.put(k, f);
                spellings.put(k, original);
            }
        }
        nodes = 1;
        word[0] = -1;
        firstChild[0] = lastChild[0] = nextSibling[0] = -1;
        for (Map.Entry<String, Integer> e : vocab.entrySet()) {
            int node = 0;
            String k = e.getKey();
            for (int i = 0; i < k.length(); i++) node = child(node, k.charAt(i) - 'a', true);
            word[node] = keys.size();
            index.put(k, keys.size());
            keys.add(k);
            counts.add(e.getValue());
        }
    }

    /** The comparison form of a word: lower case, without apostrophes, with five British spellings made American. */
    public static String key(String w) {
        String k = w.toLowerCase(Locale.ROOT).replace('’', '\'');
        int a = 0, b = k.length();
        while (a < b && k.charAt(a) == '\'') a++;
        while (b > a && k.charAt(b - 1) == '\'') b--;
        k = k.substring(a, b);
        k = SAME_WORD.getOrDefault(k, k);
        return k.replace("'", "");
    }

    private static boolean letters(String k) {
        for (int i = 0; i < k.length(); i++) if (k.charAt(i) < 'a' || k.charAt(i) > 'z') return false;
        return true;
    }

    private int child(int node, int ch, boolean create) {
        for (int c = firstChild[node]; c >= 0; c = nextSibling[c]) if (letter[c] == ch) return c;
        if (!create) return -1;
        if (nodes == word.length) grow();
        int c = nodes++;
        letter[c] = (byte) ch;
        depth[c] = (short) (depth[node] + 1);
        word[c] = -1;
        firstChild[c] = lastChild[c] = nextSibling[c] = -1;
        if (firstChild[node] < 0) firstChild[node] = c;
        else nextSibling[lastChild[node]] = c;
        lastChild[node] = c;
        return c;
    }

    private void grow() {
        int n = word.length * 2;
        firstChild = Arrays.copyOf(firstChild, n);
        lastChild = Arrays.copyOf(lastChild, n);
        nextSibling = Arrays.copyOf(nextSibling, n);
        word = Arrays.copyOf(word, n);
        letter = Arrays.copyOf(letter, n);
        depth = Arrays.copyOf(depth, n);
    }

    public boolean contains(String key) { return index.containsKey(key); }

    /** How common the word is in the dictionary, or 0 if it is not there. */
    public int count(String key) {
        Integer i = index.get(key);
        return i == null ? 0 : counts.get(i);
    }

    /** The written form of a key, such as "don't" for "dont". */
    public String spelling(String key) { return spellings.getOrDefault(key, key); }

    /**
     * The best words for these letter chances: frames x 27, log chances, blank last. Ordered as the Python version orders
     * them: path score per letter, a bonus per letter, and how common the word is.
     */
    public synchronized Candidate[] search(double[][] lp) {
        // A hypothesis is a node and whether its last frame was a blank, keyed as node * 2 + blank.
        if (stamp == null || stamp.length < nodes * 2) {
            stamp = new int[nodes * 2];
            slotOf = new int[nodes * 2];
        }
        int[] hypNode = {0};
        double[] hypScore = {0};
        boolean[] hypEnded = {false};
        int size = 1;
        for (double[] frame : lp) {
            generation++;
            count = 0;
            for (int h = 0; h < size; h++) {
                int n = hypNode[h];
                double base = hypScore[h];
                offer((n << 1) | 1, base + frame[BLANK], n, true);
                for (int c = firstChild[n]; c >= 0; c = nextSibling[c]) offer(c << 1, base + frame[letter[c]], c, false);
                if (!hypEnded[h] && depth[n] > 0) offer(n << 1, base + frame[letter[n]], n, false);
            }
            int kept = count;
            Integer[] order = new Integer[count];
            for (int i = 0; i < count; i++) order[i] = i;
            if (count > BEAM) {
                double[] rank = new double[count];
                for (int i = 0; i < count; i++) {
                    int d = depth[candNode[i]];
                    rank[i] = -(candScore[i] / Math.pow(Math.max(d, 1), GP) + BP * d);
                }
                // A stable sort, like Python's, so equal hypotheses keep the order they were found in.
                Arrays.sort(order, (a, b) -> Double.compare(rank[a], rank[b]));
                kept = BEAM;
            }
            hypNode = new int[kept];
            hypScore = new double[kept];
            hypEnded = new boolean[kept];
            for (int i = 0; i < kept; i++) {
                hypNode[i] = candNode[order[i]];
                hypScore[i] = candScore[order[i]];
                hypEnded[i] = candEnded[order[i]];
            }
            size = kept;
        }
        Map<Integer, Double> found = new LinkedHashMap<>();
        for (int h = 0; h < size; h++) {
            int w = word[hypNode[h]];
            if (w >= 0) found.merge(w, hypScore[h], Math::max);
        }
        List<Candidate> out = new ArrayList<>(found.size());
        for (Map.Entry<Integer, Double> e : found.entrySet()) {
            String k = keys.get(e.getKey());
            double s = e.getValue() / Math.pow(Math.max(k.length(), 1), GAMMA) + BETA * k.length() + LAMBDA * Math.log(counts.get(e.getKey()));
            out.add(new Candidate(k, e.getValue(), s));
        }
        // Python sorts (score, word, path) tuples in reverse, so equal scores fall back to the word, last first.
        out.sort((a, b) -> a.score != b.score ? Double.compare(b.score, a.score) : b.key.compareTo(a.key));
        return out.subList(0, Math.min(KEEP, out.size())).toArray(new Candidate[0]);
    }

    // Working space for search, reused between strokes: each frame's hypotheses, and where each key sits in them.
    private int[] stamp, slotOf;
    private int generation, count;
    private double[] candScore = new double[1024];
    private int[] candNode = new int[1024];
    private boolean[] candEnded = new boolean[1024];

    private void offer(int key, double value, int n, boolean blank) {
        if (stamp[key] == generation) {
            int at = slotOf[key];
            if (candScore[at] < value) {
                candScore[at] = value;
                candNode[at] = n;
                candEnded[at] = blank;
            }
            return;
        }
        if (count == candScore.length) {
            candScore = Arrays.copyOf(candScore, count * 2);
            candNode = Arrays.copyOf(candNode, count * 2);
            candEnded = Arrays.copyOf(candEnded, count * 2);
        }
        stamp[key] = generation;
        slotOf[key] = count;
        candScore[count] = value;
        candNode[count] = n;
        candEnded[count] = blank;
        count++;
    }

    /**
     * FUTO's resampling, as the encoder was trained with: about 60 points a second, then 64 points evenly by index,
     * clipped to the keyboard. Returns x then y, 64 each.
     */
    public static float[] features(float[] x, float[] y, float[] t) {
        int len = x.length;
        double duration = (double) t[len - 1] - t[0];
        double[] hx, hy;
        if (len < 2 || duration <= 1e-12) {
            hx = new double[]{x[0], x[len - 1]};
            hy = new double[]{y[0], y[len - 1]};
        } else {
            int n = Math.max(2, (int) Math.rint(duration / (1000.0 / 60)) + 1);
            hx = new double[n];
            hy = new double[n];
            double step = duration / (n - 1);
            int j = 0;
            for (int i = 0; i < n; i++) {
                double g = Math.min(t[0] + i * step, (double) t[len - 1]);
                while (j < len && t[j] < g) j++;
                if (j == 0) {
                    hx[i] = x[0]; hy[i] = y[0];
                } else if (j >= len) {
                    hx[i] = x[len - 1]; hy[i] = y[len - 1];
                } else {
                    double seg = (double) t[j] - t[j - 1];
                    double f = seg > 1e-12 ? (g - t[j - 1]) / seg : 0;
                    hx[i] = x[j - 1] + f * ((double) x[j] - x[j - 1]);
                    hy[i] = y[j - 1] + f * ((double) y[j] - y[j - 1]);
                }
            }
        }
        float[] out = new float[128];
        int last = hx.length - 1;
        for (int i = 0; i < 64; i++) {
            double pos = (i == 63 ? 1.0 : i * (1.0 / 63)) * last;
            int i0 = (int) pos;
            int i1 = Math.min(i0 + 1, last);
            double f = pos - i0;
            out[i] = (float) Math.min(1, Math.max(0, hx[i0] + f * (hx[i1] - hx[i0])));
            out[64 + i] = (float) Math.min(1, Math.max(0, hy[i0] + f * (hy[i1] - hy[i0])));
        }
        return out;
    }

    /** The letters and blank from the encoder's 65 outputs per frame, as log chances that add up to one. */
    public static double[][] letterChances(float[] emissions, int frames, int width) {
        double[][] lp = new double[frames][LETTERS + 1];
        for (int t = 0; t < frames; t++) {
            double top = Double.NEGATIVE_INFINITY;
            for (int c = 0; c <= LETTERS; c++) {
                lp[t][c] = emissions[t * width + (c < LETTERS ? c : 64)];
                top = Math.max(top, lp[t][c]);
            }
            double sum = 0;
            for (int c = 0; c <= LETTERS; c++) sum += Math.exp(lp[t][c] - top);
            double norm = top + Math.log(sum);
            for (int c = 0; c <= LETTERS; c++) lp[t][c] -= norm;
        }
        return lp;
    }

    /** Key centres of the Titan grid in letter space, a to z, for the encoder's layout input. */
    public static float[] titanKeys() {
        String[] rows = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
        float[] keys = new float[128];
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < rows[r].length(); c++) {
                int i = rows[r].charAt(c) - 'a';
                keys[2 * i] = (float) ((c + (r == 2 ? 1 : 0) + .5) / 10);
                keys[2 * i + 1] = (float) ((r + .5) / 3);
            }
        return keys;
    }
}
