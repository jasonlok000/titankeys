package com.atlas.engine;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Locale;

/** Paired first-choice outcomes only. No words, contexts, or gestures are persisted. */
final class PersonalizationStats {
    private static final int RECENT = 100;
    private final long[][] personalized = new long[2][4];
    private final long[][] originalOnly = new long[2][2];
    private final byte[] recent = new byte[RECENT];
    private int recentSize, next;

    static boolean sameWord(String first, String confirmed) {
        return first.replace('\u2019', '\'').equalsIgnoreCase(confirmed.replace('\u2019', '\''));
    }

    void record(boolean originalCorrect, boolean personalizedCorrect, boolean applied, boolean candidate) {
        int source = candidate ? 0 : 1;
        if (!applied) {
            if (originalCorrect != personalizedCorrect) throw new IllegalArgumentException("Standard-only predictions must share an outcome");
            originalOnly[source][originalCorrect ? 1 : 0]++;
            return;
        }
        int outcome = (originalCorrect ? 1 : 0) | (personalizedCorrect ? 2 : 0);
        personalized[source][outcome]++;
        recent[next] = (byte) outcome;
        next = (next + 1) % RECENT;
        recentSize = Math.min(RECENT, recentSize + 1);
    }

    String summary() {
        long[] counts = new long[4];
        long candidates = 0, corrections = 0, baseline = 0, baselineRight = 0;
        for (int source = 0; source < 2; source++) {
            for (int outcome = 0; outcome < 4; outcome++) {
                counts[outcome] += personalized[source][outcome];
                if (source == 0) candidates += personalized[source][outcome]; else corrections += personalized[source][outcome];
            }
            long unadjusted = originalOnly[source][0] + originalOnly[source][1];
            baseline += unadjusted; baselineRight += originalOnly[source][1];
            if (source == 0) candidates += unadjusted; else corrections += unadjusted;
        }
        long total = sum(counts);
        StringBuilder text = new StringBuilder("Since stats reset · ").append(total)
                .append(total == 1 ? " personalized swipe compared\n" : " personalized swipes compared\n");
        if (total == 0) text.append("Waiting for confirmed swipes with active language habits.\n");
        else {
            text.append(comparison(counts));
            text.append("\nHelped ").append(counts[2]).append(" · Hurt ").append(counts[1]);
            text.append("\nBoth correct ").append(counts[3]).append(" · Both wrong ").append(counts[0]);
            if (total < 20) text.append("\nSmall sample. Keep collecting confirmations.");
            if (total > RECENT) {
                long[] window = new long[4];
                for (byte outcome : recent) window[outcome]++;
                text.append("\n\nLast 100 personalized swipes\n").append(comparison(window));
            }
            text.append('\n');
        }
        text.append("\nStandard FUTO only: ").append(baseline).append(baseline == 1 ? " confirmation" : " confirmations");
        if (baseline > 0) text.append(" · ").append(percent(baselineRight, baseline)).append(" correct");
        text.append("\nCandidate confirmations ").append(candidates).append(" · Manual corrections ").append(corrections);
        return text.toString();
    }

    private static String comparison(long[] counts) {
        long total = sum(counts), original = counts[1] + counts[3], adjusted = counts[2] + counts[3];
        return "Standard FUTO: " + percent(original, total) + " (" + original + "/" + total + ")\n"
                + "Personalised: " + percent(adjusted, total) + " (" + adjusted + "/" + total + ")\n"
                + String.format(Locale.UK, "Net change: %+.1f percentage points", (adjusted - original) * 100.0 / total);
    }
    private static String percent(long correct, long total) { return String.format(Locale.UK, "%.1f%%", correct * 100.0 / total); }
    private static long sum(long[] values) { long result = 0; for (long value : values) result += value; return result; }

    void write(DataOutput out) throws IOException {
        out.writeInt(1);
        for (long[] source : personalized) for (long value : source) out.writeLong(value);
        for (long[] source : originalOnly) for (long value : source) out.writeLong(value);
        out.writeInt(recentSize); out.writeInt(next); out.write(recent);
    }

    void read(DataInput in) throws IOException {
        if (in.readInt() != 1) throw new IOException("Accuracy stats version");
        long[] counts = new long[4];
        for (long[] source : personalized) for (int i = 0; i < source.length; i++) { source[i] = readCount(in); counts[i] += source[i]; }
        for (long[] source : originalOnly) for (int i = 0; i < source.length; i++) source[i] = readCount(in);
        recentSize = in.readInt(); next = in.readInt(); in.readFully(recent);
        if (recentSize != Math.min(RECENT, sum(counts)) || next != sum(counts) % RECENT) throw new IOException("Accuracy stats window");
        long[] window = new long[4];
        for (int i = 0; i < RECENT; i++) {
            if (recent[i] < 0 || recent[i] > 3) throw new IOException("Accuracy outcome");
            if (i < recentSize) window[recent[i]]++;
        }
        for (int i = 0; i < 4; i++) if (window[i] > counts[i]) throw new IOException("Accuracy stats counts");
    }
    private static long readCount(DataInput in) throws IOException {
        long count = in.readLong();
        if (count < 0 || count > 1000000000000L) throw new IOException("Accuracy stats count");
        return count;
    }
}
