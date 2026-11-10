package com.atlas.engine;

// Copied from Numen (GPL-3.0, https://github.com/jlo-aug/numen, commit 7b1391c), which is a modified version of Pastiera.
// Numen's version is Kotlin, with a coroutine queue and a table for each language. TitanKeys is English only.

import android.util.Log;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Learns which words the user types after which, and how they start sentences. Writes wait on a queue of their own, so
 * learning does not slow down typing, and a pair counts in predictions from the moment it is learned.
 */
final class NextWordPredictor {
    private static final String TAG = "NextWordPredictor";
    private static final String SENTENCE_START = "__sentence_start__";
    /** As many suggestions as the context model's list can hold. */
    private static final int SUGGESTIONS = 8;
    /** Enough of the words after a word to find the ones that the context model offers too. */
    private static final int STORED_WORDS = 64;
    /** Enough kept words that start with some letters to cover every completion the strip can show. */
    private static final int KEPT_WORDS = 200;
    /** A use counts half as much after this long, so a word used lately can pass one used more often long ago. */
    private static final double HALF_LIFE_MILLIS = 30 * 86_400_000d;

    private static final class Pending {
        final String prefix, word;
        int count;
        long lastUsed;

        Pending(String prefix, String word) { this.prefix = prefix; this.word = word; }
    }

    private final UserNGramRepository store;
    private final LongSupplier clock;
    private final Predicate<String> allowed;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "next-word-learning");
        thread.setDaemon(true);
        return thread;
    });
    /** Learned pairs that the queue has not stored yet. Guarded by itself. */
    private final Map<String, Pending> pending = new HashMap<>();

    /** A word that {@code allowed} refuses, as a blocked word is, is never learned or suggested. */
    NextWordPredictor(UserNGramRepository store, LongSupplier clock, Predicate<String> allowed) {
        this.store = store;
        this.clock = clock;
        this.allowed = allowed;
    }

    void learn(String previousWord, String nextWord) {
        String prefix = TypedPredictions.key(previousWord);
        if (!prefix.isEmpty() && allowed.test(previousWord)) enqueue(prefix, nextWord);
    }

    void learnSentenceStart(String firstWord) {
        enqueue(SENTENCE_START, firstWord);
    }

    /** The words the user has kept after this one, best first. */
    List<String> predict(String previousWord) {
        String prefix = TypedPredictions.key(previousWord);
        return prefix.isEmpty() ? List.of() : predictFor(prefix);
    }

    List<String> predictSentenceStart() {
        return predictFor(SENTENCE_START);
    }

    /** How often the user kept each word that starts with these letters (lower case, as a key), pending ones included. */
    Map<String, Integer> keptStarting(String letters) {
        Map<String, Integer> kept = new HashMap<>();
        if (letters.isEmpty()) return kept;
        // Pending pairs first, then the store. A pair leaves the pending list only after the store has it, so one that moves
        // in between is counted twice for a moment and never missed.
        synchronized (pending) {
            for (Pending entry : pending.values()) {
                String word = TypedPredictions.key(entry.word);
                if (word.startsWith(letters) && allowed.test(entry.word)) kept.merge(word, entry.count, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> row : store.keptStarting(letters, KEPT_WORDS).entrySet())
            if (allowed.test(row.getKey())) kept.merge(TypedPredictions.key(row.getKey()), row.getValue(), Integer::sum);
        return kept;
    }

    /**
     * Words that both predictors offer come first, then the user's other words, then the model's other words. Each
     * group keeps its order. With nothing learned, the model's list stays exactly as it is.
     */
    static String[] combined(String[] model, List<String> learned) {
        if (learned.isEmpty()) return model;
        Map<String, String> modelOnly = new LinkedHashMap<>();
        for (String word : model) modelOnly.putIfAbsent(word.toLowerCase(Locale.ROOT), word);
        List<String> both = new ArrayList<>();
        List<String> learnedOnly = new ArrayList<>();
        for (String word : learned) {
            String lower = word.toLowerCase(Locale.ROOT);
            String offered = modelOnly.remove(lower);
            if (offered == null) learnedOnly.add(word);
            else both.add(word.equals(lower) ? offered : word);
        }
        both.addAll(learnedOnly);
        both.addAll(modelOnly.values());
        return both.stream().limit(SUGGESTIONS).toArray(String[]::new);
    }

    /** Removes this word from what was learned, on either side of a pair. */
    void forget(String word) {
        String spelling = word.trim().replace('’', '\'');
        String prefix = TypedPredictions.key(spelling);
        synchronized (pending) {
            pending.values().removeIf(entry -> entry.word.equalsIgnoreCase(spelling) || entry.prefix.equals(prefix));
        }
        onWriter(() -> store.deleteWord(prefix, spelling));
    }

    void clearAll() {
        synchronized (pending) { pending.clear(); }
        onWriter(store::clearAll);
    }

    /** Waits until the queue is empty. */
    void flush() {
        onWriter(() -> {});
    }

    private void enqueue(String prefix, String word) {
        if (TypedPredictions.key(word).isEmpty() || !allowed.test(word)) return;
        long now = clock.getAsLong();
        String key = prefix + ' ' + word;
        synchronized (pending) {
            Pending entry = pending.computeIfAbsent(key, ignored -> new Pending(prefix, word));
            entry.count++;
            entry.lastUsed = now;
        }
        writer.execute(() -> {
            try {
                // A pair forgotten while it waited is not pending any more.
                if (isPending(key)) store.learn(prefix, word, now);
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not store a learned word pair", error);
            } finally {
                finished(key);
            }
        });
    }

    private boolean isPending(String key) {
        synchronized (pending) { return pending.containsKey(key); }
    }

    private void finished(String key) {
        synchronized (pending) {
            Pending entry = pending.get(key);
            if (entry != null && --entry.count <= 0) pending.remove(key);
        }
    }

    private void onWriter(Runnable change) {
        try {
            writer.submit(change).get();
        } catch (ExecutionException error) {
            throw new IllegalStateException("Could not change the learned word pairs", error.getCause());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not change the learned word pairs", error);
        }
    }

    private List<String> predictFor(String prefix) {
        // Pending pairs first, then the store: one stored in between then counts twice for a moment, never not at all.
        List<UserNGramRepository.Prediction> waiting = new ArrayList<>();
        synchronized (pending) {
            for (Pending entry : pending.values())
                if (entry.prefix.equals(prefix)) waiting.add(new UserNGramRepository.Prediction(entry.word, entry.count, entry.lastUsed));
        }
        List<UserNGramRepository.Prediction> stored = store.predict(prefix, STORED_WORDS);
        // One word can have rows in more than one case, as "Good" and "good" do. The latest case is the one to show.
        Map<String, UserNGramRepository.Prediction> merged = new LinkedHashMap<>();
        for (UserNGramRepository.Prediction row : stored) add(merged, row);
        for (UserNGramRepository.Prediction row : waiting) add(merged, row);
        long now = clock.getAsLong();
        return merged.values().stream()
                .filter(row -> allowed.test(row.word))
                .sorted(Comparator.<UserNGramRepository.Prediction>comparingDouble(row -> -weight(row, now))
                        .thenComparing(row -> -row.lastUsed)
                        .thenComparing(row -> row.word))
                .map(row -> row.word)
                .toList();
    }

    private static void add(Map<String, UserNGramRepository.Prediction> merged, UserNGramRepository.Prediction row) {
        merged.merge(row.word.toLowerCase(Locale.ROOT), row, (first, second) -> new UserNGramRepository.Prediction(
                second.lastUsed > first.lastUsed ? second.word : first.word,
                first.count + second.count,
                Math.max(first.lastUsed, second.lastUsed)));
    }

    private static double weight(UserNGramRepository.Prediction row, long now) {
        return row.count * Math.pow(0.5, Math.max(0, now - row.lastUsed) / HALF_LIFE_MILLIS);
    }
}
