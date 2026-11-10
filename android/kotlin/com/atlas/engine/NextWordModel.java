package com.atlas.engine;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The phone-sized next-word model (DistilGPT-2, 8-bit). Given the text before the cursor it gives the log chance of every
 * next token. A word is scored by its first token, as tools/train_ranker.py scores it. The last answer is kept, because the
 * text before the cursor stays the same while a word is swiped, typed or completed.
 */
public final class NextWordModel implements AutoCloseable {
    private static final int CONTEXT_CHARS = 500;
    private final OrtEnvironment env;
    private final OrtSession session;
    private final ByteBpe bpe;
    private final int start, cap;
    private final String[] tokens;
    private final Map<String, Integer> firstTokens = Collections.synchronizedMap(new LinkedHashMap<String, Integer>(256, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > 20000;
        }
    });
    private volatile Last last;

    public NextWordModel(File dir, String model, int cap, int threads) throws IOException, OrtException {
        Map<String, String> info = new HashMap<>();
        for (String line : Files.readAllLines(new File(dir, "lm-info.txt").toPath(), StandardCharsets.UTF_8)) {
            int eq = line.indexOf('=');
            if (eq > 0) info.put(line.substring(0, eq), line.substring(eq + 1).trim());
        }
        start = Integer.parseInt(info.getOrDefault("start_token", "50256"));
        this.cap = cap;
        try (BufferedReader vocab = Files.newBufferedReader(new File(dir, "lm-vocab.txt").toPath(), StandardCharsets.UTF_8);
             BufferedReader merges = Files.newBufferedReader(new File(dir, "lm-merges.txt").toPath(), StandardCharsets.UTF_8)) {
            bpe = new ByteBpe(vocab, merges, Boolean.parseBoolean(info.getOrDefault("split_digits", "false")));
        }
        tokens = Files.readAllLines(new File(dir, "lm-vocab.txt").toPath(), StandardCharsets.UTF_8).toArray(new String[0]);
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(threads);
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        session = env.createSession(new File(dir, model).getPath(), options);
    }

    /** The token ids the model reads for this text: its last 500 characters without trailing spaces, then the last tokens. */
    int[] context(String text) {
        String context = text.substring(startAt(text));
        int end = context.length();
        while (end > 0 && context.charAt(end - 1) == ' ') end--;
        context = context.substring(0, end);
        int[] ids = context.trim().isEmpty() ? new int[0] : bpe.encode(context);
        if (ids.length == 0) return new int[]{start};
        return ids.length > cap ? java.util.Arrays.copyOfRange(ids, ids.length - cap, ids.length) : ids;
    }

    /** Where the last 500 characters begin, counted as Python counts them, by code point. */
    private static int startAt(String text) {
        int points = text.codePointCount(0, text.length());
        return points <= CONTEXT_CHARS ? 0 : text.offsetByCodePoints(0, points - CONTEXT_CHARS);
    }

    /** Log chances of the next token after this text. The same tokens give the same answer, so the last one is kept. */
    public synchronized float[] after(String text) throws OrtException {
        int[] ids = context(text);
        Last known = last;
        if (known != null && java.util.Arrays.equals(ids, known.ids)) return known.chances;
        long[] input = new long[ids.length];
        for (int i = 0; i < ids.length; i++) input[i] = ids[i];
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, LongBuffer.wrap(input), new long[]{1, input.length});
             OrtSession.Result result = session.run(Collections.singletonMap("input_ids", tensor))) {
            float[][] out = (float[][]) result.get(0).getValue();
            last = new Last(ids, out[0]);
            return out[0];
        }
    }

    /**
     * The chances for this text if they are already worked out, or null. It takes no lock, so the main thread never
     * waits while the model runs for other text.
     */
    public float[] ifReady(String text) {
        int[] ids = context(text);
        Last known = last;
        return known != null && java.util.Arrays.equals(ids, known.ids) ? known.chances : null;
    }

    /** The last answer, published whole so a reader on another thread never sees half of it. */
    private static final class Last {
        final int[] ids;
        final float[] chances;

        Last(int[] ids, float[] chances) { this.ids = ids; this.chances = chances; }
    }

    private int firstToken(String form) {
        Integer id = firstTokens.get(form);
        if (id == null) {
            id = bpe.first(" " + form);
            firstTokens.put(form, id);
        }
        return id;
    }

    /** A word's log chance, by its first token, the better of the word as written and capitalised. -99 if it has none. */
    public double word(float[] chances, String key) {
        double best = -99;
        if (key.isEmpty()) return best;
        for (String form : new String[]{key, Character.toUpperCase(key.charAt(0)) + key.substring(1)}) {
            int id = firstToken(form);
            if (id >= 0 && id < chances.length) best = Math.max(best, chances[id]);
        }
        return best;
    }

    /**
     * The likeliest next whole words: tokens that start with a space, are all letters (an apostrophe allowed) and pass
     * {@code known}. Best first, each with its log chance.
     */
    public List<Map.Entry<String, Double>> likelyWords(float[] chances, int limit, java.util.function.Predicate<String> known) {
        int[] ids = wordTokens();
        // The best few hundred word tokens by a bounded heap, then the dictionary check on those only.
        int keep = Math.min(ids.length, Math.max(limit * 8, 200));
        java.util.PriorityQueue<Integer> heap = new java.util.PriorityQueue<>(keep, (a, b) -> Float.compare(chances[a], chances[b]));
        for (int id : ids) {
            if (id >= chances.length) continue;
            if (heap.size() < keep) heap.add(id);
            else if (chances[id] > chances[heap.peek()]) { heap.poll(); heap.add(id); }
        }
        List<Integer> best = new ArrayList<>(heap);
        best.sort((a, b) -> Float.compare(chances[b], chances[a]));
        List<Map.Entry<String, Double>> out = new ArrayList<>();
        for (int id : best) {
            if (out.size() >= limit) break;
            String word = tokens[id].substring(1);
            if (known.test(word)) out.add(Map.entry(word, (double) chances[id]));
        }
        return out;
    }

    /**
     * The whole-word tokens that start with {@code letters} (lower case) and pass {@code known}, lower case, each with the
     * better log chance of its forms, as "the" and "The". A word the model splits into pieces is left out, because its first
     * piece's chance is shared with every other word that starts the same way.
     */
    public Map<String, Double> wordsStarting(float[] chances, String letters, java.util.function.Predicate<String> known) {
        int[] ids = wordTokens();
        String[] lower = wordTokenText;
        Map<String, Double> out = new HashMap<>();
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] >= chances.length || !lower[i].startsWith(letters)) continue;
            Double seen = out.get(lower[i]);
            if (seen == null ? known.test(lower[i]) : chances[ids[i]] > seen) out.put(lower[i], (double) chances[ids[i]]);
        }
        return out;
    }

    private static final Pattern WORD = Pattern.compile("[A-Za-z]+('[a-z]+)?");
    private int[] wordTokens;
    /** Each word token's text in lower case, without its space, in the same order as {@code wordTokens}. */
    private String[] wordTokenText;

    /** Tokens that are a space and then a whole word, found once. */
    private synchronized int[] wordTokens() {
        if (wordTokens == null) {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < tokens.length; i++)
                if (tokens[i].length() > 1 && tokens[i].charAt(0) == 'Ġ' && WORD.matcher(tokens[i].substring(1)).matches()) ids.add(i);
            String[] text = new String[ids.size()];
            for (int i = 0; i < text.length; i++) text[i] = tokens[ids.get(i)].substring(1).toLowerCase(java.util.Locale.ROOT);
            wordTokenText = text;
            wordTokens = ids.stream().mapToInt(Integer::intValue).toArray();
        }
        return wordTokens;
    }

    @Override
    public void close() {
        try { session.close(); } catch (OrtException ignored) { }
    }
}
