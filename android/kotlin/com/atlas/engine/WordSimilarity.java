package com.atlas.engine;

/** Conservative lexical gate for inferring an intended replacement after deletion. */
final class WordSimilarity {
    static boolean replacement(String original, String replacement) {
        String a = SwipeWords.letters(original), b = SwipeWords.letters(replacement);
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        if (Math.min(a.length(), b.length()) < 3) return false;
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j < previous.length; j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] next = new int[b.length() + 1]; next[0] = i;
            for (int j = 1; j <= b.length(); j++) next[j] = Math.min(Math.min(next[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            previous = next;
        }
        return previous[b.length()] * 2 <= Math.max(a.length(), b.length());
    }
}
