package com.atlas.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Byte-level BPE, the tokenizer of GPT-2 and the small models like it. Reads the vocabulary as one token per line in id
 * order and the merges as one "left right" pair per line in rank order, both written by tools/export_lm.py.
 */
public final class ByteBpe {
    private static final Pattern WORDS = Pattern.compile("'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+");
    private static final Pattern DIGITS = Pattern.compile("\\p{N}");
    private static final char[] BYTE_CHAR = new char[256];

    static {
        // GPT-2's map from bytes to printable characters, so every byte has a visible token character.
        int extra = 0;
        for (int b = 0; b < 256; b++) {
            boolean printable = (b >= '!' && b <= '~') || (b >= 0xA1 && b <= 0xAC) || (b >= 0xAE && b <= 0xFF);
            BYTE_CHAR[b] = printable ? (char) b : (char) (256 + extra++);
        }
    }

    private final Map<String, Integer> ids = new HashMap<>();
    private final Map<String, Integer> ranks = new HashMap<>();
    private final boolean splitDigits;
    private final Map<String, int[]> cache = new LinkedHashMap<String, int[]>(512, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest) {
            return size() > 4000;
        }
    };

    public ByteBpe(BufferedReader vocab, BufferedReader merges, boolean splitDigits) throws IOException {
        this.splitDigits = splitDigits;
        int id = 0;
        for (String line; (line = vocab.readLine()) != null; ) ids.put(line, id++);
        int rank = 0;
        for (String line; (line = merges.readLine()) != null; ) {
            if (!line.isEmpty() && !line.startsWith("#")) ranks.put(line, rank++);
        }
    }

    public int vocabularySize() {
        return ids.size();
    }

    /** Token ids of the text, in order. Synchronized because the cache is shared by the worker and the main thread. */
    public synchronized int[] encode(String text) {
        List<Integer> out = new ArrayList<>();
        Matcher m = WORDS.matcher(text);
        while (m.find()) {
            String piece = m.group();
            if (splitDigits && DIGITS.matcher(piece).find()) {
                for (String part : splitAtDigits(piece)) add(out, part);
            } else {
                add(out, piece);
            }
        }
        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }

    /** The first token of the text, or -1 if it has none. */
    public int first(String text) {
        int[] t = encode(text);
        return t.length == 0 ? -1 : t[0];
    }

    private static List<String> splitAtDigits(String piece) {
        List<String> parts = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < piece.length(); ) {
            int cp = piece.codePointAt(i);
            if (Character.isDigit(cp)) {
                if (run.length() > 0) parts.add(run.toString());
                run.setLength(0);
                parts.add(new String(Character.toChars(cp)));
            } else {
                run.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        if (run.length() > 0) parts.add(run.toString());
        return parts;
    }

    private void add(List<Integer> out, String piece) {
        int[] known = cache.get(piece);
        if (known == null) {
            known = bpe(piece);
            cache.put(piece, known);
        }
        for (int id : known) out.add(id);
    }

    private int[] bpe(String piece) {
        byte[] bytes = piece.getBytes(StandardCharsets.UTF_8);
        List<String> symbols = new ArrayList<>(bytes.length);
        for (byte b : bytes) symbols.add(String.valueOf(BYTE_CHAR[b & 0xFF]));
        while (symbols.size() > 1) {
            int best = -1, bestRank = Integer.MAX_VALUE;
            for (int i = 0; i + 1 < symbols.size(); i++) {
                Integer r = ranks.get(symbols.get(i) + " " + symbols.get(i + 1));
                if (r != null && r < bestRank) {
                    bestRank = r;
                    best = i;
                }
            }
            if (best < 0) break;
            String left = symbols.get(best), right = symbols.get(best + 1);
            List<String> merged = new ArrayList<>(symbols.size());
            for (int i = 0; i < symbols.size(); i++) {
                if (i + 1 < symbols.size() && symbols.get(i).equals(left) && symbols.get(i + 1).equals(right)) {
                    merged.add(left + right);
                    i++;
                } else {
                    merged.add(symbols.get(i));
                }
            }
            symbols = merged;
        }
        int[] result = new int[symbols.size()];
        int n = 0;
        for (String s : symbols) {
            Integer id = ids.get(s);
            if (id != null) result[n++] = id;
        }
        return n == result.length ? result : java.util.Arrays.copyOf(result, n);
    }
}
