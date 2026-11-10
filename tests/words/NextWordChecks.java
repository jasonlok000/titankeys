package com.atlas.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** The learned next-word predictor, how it combines with the context model, and what it reads as kept. */
public final class NextWordChecks {
    private static final long DAY = 86_400_000L;

    public static void main(String[] args) throws Exception {
        combining();
        ranking();
        queue();
        reading();
        keptCounts();
        System.out.println("Next word checks passed");
    }

    /** #38: how often each word was kept, from the pairs, for completions after letters. */
    private static void keptCounts() {
        FakeStore store = new FakeStore();
        // The writer waits at this gate, so every pair below is certainly still pending when it is counted.
        store.gate = new CountDownLatch(1);
        NextWordPredictor predictor = new NextWordPredictor(store, () -> 1_000 * DAY, word -> !word.equalsIgnoreCase("blocked"));
        predictor.learn("see", "Tomorrow");
        predictor.learn("until", "tomorrow");
        predictor.learnSentenceStart("Tomorrow");
        predictor.learn("the", "tower");
        predictor.learn("so", "blocked");
        java.util.Map<String, Integer> waiting = predictor.keptStarting("to");
        check(waiting.getOrDefault("tomorrow", 0) == 3 && waiting.getOrDefault("tower", 0) == 1,
                "A pending word kept after any word, or to start a sentence, counts each time, in any case: " + waiting);
        store.gate.countDown();
        predictor.flush();
        java.util.Map<String, Integer> kept = predictor.keptStarting("to");
        check(kept.get("tomorrow") == 3 && kept.get("tower") == 1 && kept.size() == 2, "Stored counts add up the same: " + kept);
        check(predictor.keptStarting("b").isEmpty(), "A blocked word is never counted");
        check(predictor.keptStarting("").isEmpty(), "Nothing is counted before a letter");
        predictor.clearAll();
        check(predictor.keptStarting("to").isEmpty(), "Clearing the learned pairs clears the counts");
    }

    private static void combining() {
        String[] model = { "the", "of", "my" };
        check(NextWordPredictor.combined(model, List.of()) == model, "With no learned data the model's list must stay exactly as it is");
        same(NextWordPredictor.combined(model, List.of("cat", "dog", "of")), new String[] { "of", "cat", "dog", "the", "my" },
                "A word both offer comes first, then learned words, then the model's words");
        same(NextWordPredictor.combined(model, List.of("my", "of")), new String[] { "my", "of", "the" },
                "Words that both offer keep the learned order");
        same(NextWordPredictor.combined(new String[] { "a", "b", "c", "d", "e", "f", "g", "h" }, List.of("x", "y", "h")),
                new String[] { "h", "x", "y", "a", "b", "c", "d", "e" }, "The list stays at eight words");
        same(NextWordPredictor.combined(new String[] { "Monday" }, List.of("monday")), new String[] { "Monday" },
                "The model's capital stays when the learned word has none");
        same(NextWordPredictor.combined(new String[] { "jason" }, List.of("Jason")), new String[] { "Jason" },
                "The user's capital stays when the model has none");
    }

    private static void ranking() {
        long[] now = { 1_000 * DAY };
        NextWordPredictor predictor = new NextWordPredictor(new FakeStore(), () -> now[0], word -> true);

        for (int i = 0; i < 3; i++) predictor.learn("good", "morning");
        predictor.learn("good", "night");
        same(stored(predictor, "good"), List.of("morning", "night"), "A word used more often after this one comes first");
        same(stored(predictor, "Good"), List.of("morning", "night"), "The word before is matched in any case");
        check(stored(predictor, "morning").isEmpty(), "Pairs only count in the order they were typed");

        predictor.learn("nice", "cat");
        now[0] += DAY;
        predictor.learn("nice", "dog");
        same(stored(predictor, "nice"), List.of("dog", "cat"), "With equal use, the word used more recently comes first");

        now[0] = 2_000 * DAY;
        for (int i = 0; i < 4; i++) predictor.learn("old", "habit");
        now[0] += 90 * DAY;
        for (int i = 0; i < 2; i++) predictor.learn("old", "friend");
        same(stored(predictor, "old"), List.of("friend", "habit"), "A word used lately passes one used more often long ago");

        now[0] = 3_000 * DAY;
        for (int i = 0; i < 3; i++) predictor.learn("new", "york");
        now[0] += DAY;
        predictor.learn("new", "car");
        same(stored(predictor, "new"), List.of("york", "car"), "A day of difference does not outweigh more use");

        predictor.learnSentenceStart("hello");
        same(storedStarts(predictor), List.of("hello"), "How sentences start is learned");
        check(stored(predictor, "hello").isEmpty(), "A first word is not a pair");

        predictor.learn("hi", "jason");
        now[0] += DAY;
        predictor.learn("hi", "Jason");
        same(stored(predictor, "hi"), List.of("Jason"), "One word in two cases is one suggestion, in its latest case");

        predictor.learn("good", "50");
        predictor.learn("50", "good");
        predictor.learnSentenceStart("x1");
        same(stored(predictor, "good"), List.of("morning", "night"), "Only words are learned");
        check(stored(predictor, "50").isEmpty(), "Only words are learned after");
        same(storedStarts(predictor), List.of("hello"), "Only words start a sentence");
        predictor.flush();
    }

    private static void queue() throws Exception {
        FakeStore store = new FakeStore();
        store.gate = new CountDownLatch(1);
        NextWordPredictor predictor = new NextWordPredictor(store, () -> 1000L, word -> true);
        long began = System.nanoTime();
        predictor.learn("ich", "bin");
        check(System.nanoTime() - began < TimeUnit.MILLISECONDS.toNanos(100), "Learning must only add to the queue");
        same(predictor.predict("ich"), List.of("bin"), "A pair counts before it is stored");
        check(store.started.await(2, TimeUnit.SECONDS), "The queue must write in the background");

        store.gate.countDown();
        predictor.flush();
        same(store.words("ich"), List.of("bin"), "The pair reaches the store");
        same(predictor.predict("ich"), List.of("bin"), "A stored pair is suggested once");
        same(store.count("ich", "bin"), 1, "One use is one count");

        store = new FakeStore();
        store.gate = new CountDownLatch(1);
        NextWordPredictor forgetting = new NextWordPredictor(store, () -> 1000L, word -> true);
        forgetting.learn("first", "write");
        check(store.started.await(2, TimeUnit.SECONDS), "The first write must be under way");
        forgetting.learn("zebra", "crossing");
        same(forgetting.predict("zebra"), List.of("crossing"), "A queued pair counts");
        Thread blocking = new Thread(() -> forgetting.forget("crossing"));
        blocking.start();
        for (long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2); !forgetting.predict("zebra").isEmpty() && System.nanoTime() < deadline;)
            Thread.sleep(5);
        check(forgetting.predict("zebra").isEmpty(), "A forgotten word stops at once");
        store.gate.countDown();
        blocking.join();
        forgetting.flush();
        check(!store.writes.contains("zebra crossing"), "A queued pair for a forgotten word never reaches the store");

        store = new FakeStore();
        NextWordPredictor predictor2 = new NextWordPredictor(store, () -> 1000L, word -> true);
        predictor2.learn("good", "morning");
        predictor2.learn("morning", "sir");
        predictor2.learn("other", "words");
        predictor2.flush();
        predictor2.forget("MORNING");
        same(store.words("good"), List.of(), "Forgetting removes the word as the second of a pair");
        same(store.words("morning"), List.of(), "Forgetting removes the word as the first of a pair");
        same(store.words("other"), List.of("words"), "Forgetting leaves other pairs");

        predictor2.learn("good", "morning");
        predictor2.clearAll();
        check(predictor2.predict("good").isEmpty() && predictor2.predict("other").isEmpty(), "Reset clears everything");
        same(store.words("good"), List.of(), "Reset clears a pair that was still in the queue");
        same(store.words("other"), List.of(), "Reset clears the store");

        store = new FakeStore();
        store.learn("good", "morning", 1000L);
        store.learn("good", "APPLE", 1000L);
        NextWordPredictor blocking2 = new NextWordPredictor(store, () -> 1000L, word -> !word.equalsIgnoreCase("apple"));
        same(blocking2.predict("good"), List.of("morning"), "A blocked word that was learned earlier is never suggested");
        blocking2.learn("good", "Apple");
        blocking2.learn("apple", "pie");
        blocking2.learnSentenceStart("Apple");
        same(blocking2.predict("apple"), List.of(), "A pair is not learned after a blocked word");
        same(blocking2.predictSentenceStart(), List.of(), "A blocked word is not learned as a first word");
        blocking2.flush();
        same(store.count("good", "Apple"), 0, "A blocked word is not stored as the second of a pair");
        same(store.words("apple"), List.of(), "A blocked word is not stored as the first of a pair");
    }

    private static void reading() {
        List<String> learned = new ArrayList<>();
        KeptWords kept = new KeptWords((previous, next) -> learned.add((previous == null ? "^" : previous) + " " + next));

        see(kept, "");
        see(kept, "Good morning my friend ");
        same(learned, List.of("^ good", "Good morning"), "A word counts once two more words have ended after it");
        see(kept, "Good morning my friend ");
        same(learned, List.of("^ good", "Good morning"), "The same text teaches nothing new");
        see(kept, "Good morning my friend how");
        same(learned, List.of("^ good", "Good morning"), "A word still being typed has not ended");
        see(kept, "Good morning my friend how ");
        same(learned, List.of("^ good", "Good morning", "morning my"), "Each word that ends lets one more count");

        learned.clear();
        kept.clear();
        see(kept, "");
        see(kept, "hello there frie");
        kept.settle();
        same(learned, List.of("^ hello", "hello there"), "Settling counts the ended words, without the unfinished word");
        learned.clear();
        kept.settle();
        same(learned, List.of(), "Settling twice teaches nothing new");

        kept.clear();
        see(kept, "");
        see(kept, "hello there ");
        kept.clear();
        kept.settle();
        same(learned, List.of(), "Clearing drops words that have not counted");

        learned.clear();
        see(kept, "");
        kept.sent("see you soon", 12);
        same(learned, List.of("^ see", "see you", "you soon"), "Enter ends the last word and counts them all");

        learned.clear();
        see(kept, "");
        see(kept, "Go now. Then stop here please ");
        same(learned, List.of("^ go", "Go now", "^ then", "Then stop"), "A full stop starts a new sentence");

        learned.clear();
        kept.clear();
        see(kept, "");
        see(kept, "one two\nthree four five six ");
        same(learned, List.of("^ one", "one two", "^ three", "three four"), "A new line starts a new sentence");

        learned.clear();
        kept.clear();
        see(kept, "");
        see(kept, "first, second third fourth fifth ");
        same(learned, List.of("^ first", "first second", "second third"), "A comma does not end a pair");

        learned.clear();
        kept.clear();
        see(kept, "");
        see(kept, "paid 50 dollars today at alex@example.com for work now please ok ");
        same(learned, List.of("^ paid", "dollars today", "today at", "for work", "work now"),
                "A number or an address breaks the chain");

        learned.clear();
        kept.clear();
        see(kept, "");
        see(kept, "“Don’t stop” she said now ");
        same(learned, List.of("^ don't", "Don't stop", "stop she"), "Quotes are not part of a word, and curly apostrophes are straight");

        learned.clear();
        kept.clear();
        see(kept, "");
        see(kept, "iPhone works well today ");
        same(learned, List.of("^ iPhone", "iPhone works"), "Capitals inside a word stay");

        learned.clear();
        kept.clear();
        see(kept, "old words ");
        see(kept, "old words new ones go on ");
        same(learned, List.of("new ones"), "Text from before the first look does not count");

        learned.clear();
        kept.clear();
        see(kept, "");
        see(kept, "one two three four ");
        see(kept, "one two ");
        see(kept, "one two five six seven ");
        same(learned, List.of("^ one", "one two", "two five"), "Words deleted before they counted are gone, and counted words are not counted again");

        learned.clear();
        kept.clear();
        String padding = "x".repeat(SentenceContext.MAX_TEXT_LENGTH);
        kept.saw(padding, SentenceContext.MAX_TEXT_LENGTH);
        kept.saw(padding.substring(0, 2040) + "ab cd ef gh ", SentenceContext.MAX_TEXT_LENGTH + 5);
        same(learned, List.of(), "A word cut off at the start of the window is not a first word");

        same(KeptWords.previousWord(""), "", "An empty field is the start of a sentence");
        same(KeptWords.previousWord("Good "), "Good", "The last word is what the next word follows");
        same(KeptWords.previousWord("good, "), "good", "A comma keeps the last word");
        same(KeptWords.previousWord("Done. "), "", "A full stop starts a sentence");
        same(KeptWords.previousWord("Done\n"), "", "A new line starts a sentence");
        check(KeptWords.previousWord("paid 50 ") == null, "A number is no word to follow");
        check(KeptWords.previousWord(padding) == null, "A word cut off at the start of the window is no word to follow");
    }

    private static void see(KeptWords kept, String text) { kept.saw(text, text.length()); }

    /** Waits for the queue, as a pair stored while it is read can count twice for a moment. */
    private static List<String> stored(NextWordPredictor predictor, String word) {
        predictor.flush();
        return predictor.predict(word);
    }

    private static List<String> storedStarts(NextWordPredictor predictor) {
        predictor.flush();
        return predictor.predictSentenceStart();
    }

    private static final class FakeStore implements UserNGramRepository {
        private static final class Row {
            final String prefix, word;
            int count;
            long lastUsed;
            Row(String prefix, String word) { this.prefix = prefix; this.word = word; }
        }

        private final List<Row> rows = new ArrayList<>();
        final List<String> writes = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch started = new CountDownLatch(1);
        volatile CountDownLatch gate;

        @Override public void learn(String prefix, String nextWord, long nowMs) {
            started.countDown();
            try {
                if (gate != null) gate.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                throw new IllegalStateException(error);
            }
            writes.add(prefix + " " + nextWord);
            synchronized (rows) {
                Row row = rows.stream().filter(it -> it.prefix.equals(prefix) && it.word.equals(nextWord)).findFirst().orElse(null);
                if (row == null) rows.add(row = new Row(prefix, nextWord));
                row.count++;
                row.lastUsed = nowMs;
            }
        }

        @Override public List<Prediction> predict(String prefix, int limit) {
            synchronized (rows) {
                return rows.stream().filter(row -> row.prefix.equals(prefix))
                        .sorted(Comparator.<Row>comparingInt(row -> -row.count).thenComparingLong(row -> -row.lastUsed))
                        .limit(limit).map(row -> new Prediction(row.word, row.count, row.lastUsed)).toList();
            }
        }

        @Override public java.util.Map<String, Integer> keptStarting(String letters, int limit) {
            java.util.Map<String, Integer> kept = new java.util.HashMap<>();
            synchronized (rows) {
                for (Row row : rows)
                    if (row.word.toLowerCase(java.util.Locale.ROOT).startsWith(letters))
                        kept.merge(row.word.toLowerCase(java.util.Locale.ROOT), row.count, Integer::sum);
            }
            return kept;
        }

        @Override public int deleteWord(String prefix, String nextWord) {
            synchronized (rows) {
                int before = rows.size();
                rows.removeIf(row -> row.word.equalsIgnoreCase(nextWord) || row.prefix.equals(prefix));
                return before - rows.size();
            }
        }

        @Override public void clearAll() {
            synchronized (rows) { rows.clear(); }
        }

        List<String> words(String prefix) { return predict(prefix, 100).stream().map(row -> row.word).toList(); }

        int count(String prefix, String word) {
            return predict(prefix, 100).stream().filter(row -> row.word.equals(word)).mapToInt(row -> row.count).sum();
        }
    }

    private static void same(String[] actual, String[] expected, String message) {
        if (!Arrays.equals(actual, expected)) throw new AssertionError(message + ": " + Arrays.toString(actual));
    }

    private static void same(Object actual, Object expected, String message) {
        if (!actual.equals(expected)) throw new AssertionError(message + ": " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
