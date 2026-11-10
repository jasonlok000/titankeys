package com.atlas.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Finds the pairs of words that the user kept, by reading the text before the cursor. A word counts as it stands in the
 * end, however it got there, so the typo behind a fix is never learned. Only text typed since the last {@link #clear} counts.
 */
public final class KeptWords {
    /** Ended words that can still change: the next word can fix the last one, and a look-alike check can still be running on the one before. */
    private static final int OPEN_WORDS = 2;
    private static final String OPENERS = "\"'([{“‘";
    private static final String SENTENCE_ENDS = ".!?。！？";
    private static final String CLOSERS = SENTENCE_ENDS + ",;:)]}…\"'”’";
    private static final String LINE_ENDS = "\r\n  ";

    /** A piece of text between spaces. */
    private static final class Run {
        final int start, end;
        /** Null if this run is anything but a word, as a link or a number is. */
        final String word;
        final boolean endsSentence;

        Run(String text, int start, int end, boolean cutOff) {
            this.start = start;
            this.end = end;
            int left = start, right = end;
            while (left < right && OPENERS.indexOf(text.charAt(left)) >= 0) left++;
            boolean ends = false;
            while (right > left && CLOSERS.indexOf(text.charAt(right - 1)) >= 0) {
                ends |= SENTENCE_ENDS.indexOf(text.charAt(right - 1)) >= 0;
                right--;
            }
            for (int i = end; i < text.length() && Character.isWhitespace(text.charAt(i)); i++)
                ends |= LINE_ENDS.indexOf(text.charAt(i)) >= 0;
            endsSentence = ends;
            String core = text.substring(left, right);
            word = cutOff || TypedPredictions.key(core).isEmpty() ? null : core.replace('’', '\'');
        }
    }

    private final BiConsumer<String, String> learn;
    private int typedFrom = -1, learnedTo;
    private String seen;
    private int seenAt;

    /** {@code learn} is told each pair once it is final. Its first word is null if the second word starts a sentence. */
    public KeptWords(BiConsumer<String, String> learn) {
        this.learn = learn;
    }

    /** {@code before} is the text before the cursor, whose position in the field is {@code cursor}. */
    public void saw(String before, int cursor) {
        if (typedFrom < 0) typedFrom = learnedTo = cursor;
        typedFrom = Math.min(typedFrom, cursor);
        learnedTo = Math.min(learnedTo, cursor);
        seen = before;
        seenAt = cursor;
        learn(OPEN_WORDS);
    }

    /** Enter sent the text, so its last word ended there and nothing in it can change any more. */
    public void sent(String before, int cursor) {
        saw(before + '\n', cursor + 1);
        settle();
    }

    /** Nothing can change the words that were last seen, so they all count. */
    public void settle() {
        if (seen != null) learn(0);
        clear();
    }

    /** Forgets what was seen. The words after the next look at the text are the first to count. */
    public void clear() {
        typedFrom = -1;
        learnedTo = 0;
        seen = null;
    }

    /** The word that the next word would follow, "" if the next word starts a sentence, or null if what comes before is no word. */
    static String previousWord(String text) {
        List<Run> runs = runs(text);
        if (runs.isEmpty()) return cutOff(text) ? null : "";
        Run last = runs.get(runs.size() - 1);
        return last.endsSentence ? "" : last.word;
    }

    private void learn(int open) {
        List<Run> runs = runs(seen);
        int offset = seenAt - seen.length();
        int last = runs.size() - 1;
        // No space after it yet, so the user can still be typing this one.
        if (last >= 0 && runs.get(last).end == seen.length()) last--;
        for (int i = 0; i <= last - open; i++) {
            Run run = runs.get(i);
            if (offset + run.start < learnedTo) continue;
            learnedTo = offset + run.end;
            if (run.word == null) continue;
            Run previous = i == 0 ? null : runs.get(i - 1);
            if (previous == null ? !cutOff(seen) : previous.endsSentence) learn.accept(null, withoutSentenceCapital(run.word));
            else if (previous != null && previous.word != null && offset + previous.start >= typedFrom) learn.accept(previous.word, run.word);
        }
    }

    /** Sentence capitals say nothing about the word, unlike the capitals inside "iPhone". */
    private static String withoutSentenceCapital(String word) {
        String rest = word.substring(1);
        return rest.equals(rest.toLowerCase(Locale.ROOT)) ? word.substring(0, 1).toLowerCase(Locale.ROOT) + rest : word;
    }

    private static boolean cutOff(String text) { return text.length() >= SentenceContext.MAX_TEXT_LENGTH; }

    private static List<Run> runs(String text) {
        List<Run> runs = new ArrayList<>();
        int end;
        for (int start = 0; start < text.length(); start = end + 1) {
            end = start;
            while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
            if (end > start) runs.add(new Run(text, start, end, cutOff(text) && start == 0));
        }
        return runs;
    }
}
