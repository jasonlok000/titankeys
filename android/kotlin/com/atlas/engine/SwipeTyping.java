package com.atlas.engine;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Log;
import org.futo.ml.inference.SwipeDecoder;
import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SwipeTyping {
    private static volatile SwipeTyping instance;
    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    /** The fixes that you refused by holding them on the strip. Never part of a data contribution. */
    private static final String REFUSED_FIXES = "refused_fixes";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger pendingExamples = new AtomicInteger();
    private SwipeRecognizer recogniser;
    private volatile TypedPredictions typedPredictions;
    private volatile List<String> sentenceStarts = List.of();
    private volatile String loadError;
    private volatile String readiness = "loading";
    private int loadingRetries;
    private final InferenceTiming recognitionTiming = new InferenceTiming("Recognition");
    private final InferenceTiming completionTiming = new InferenceTiming("Completion");
    private LanguageHabits dictionary = new LanguageHabits();
    private PersonalizationStats accuracy = new PersonalizationStats();
    private AtomicFile dictionaryFile;
    private final AtomicFile accuracyFile;
    private LearningConfigs configs;
    private volatile boolean learningEnabled;
    private volatile boolean wordsAvailable;
    private volatile String wordsSaveError = "";
    private volatile long learningGeneration, accuracyEpoch;
    /** The last word that {@link #typed} counted. Only the worker uses it. */
    private String lastTyped;
    private volatile String learningStatus = "Loading personal dictionary…", accuracyStatus = "Loading accuracy stats…";
    private long configuredRevision = -1;
    private int configuredDay = -1, activeCount;
    /** The names on this phone that the engines last took, and the state they were configured for. */
    private int nameCount;
    private long configuredNames = -1;
    private boolean configuredLearning;
    private volatile Words wordView = new Words(new String[0], new String[0], new String[0]);
    private volatile java.util.Set<String> activeWords = java.util.Set.of();
    /**
     * Words you saved, words learned from your typing, and every word your typing put in the list, even one still gathering
     * evidence, normalised: see [addedWord], [learnedWord] and [savedWord].
     */
    private volatile java.util.Set<String> addedEntries = java.util.Set.of();
    private volatile java.util.Set<String> learnedEntries = java.util.Set.of();
    private volatile java.util.Set<String> typedEntries = java.util.Set.of();
    /** Saved words whose spelling has capitals, such as "Lok", by their lowercase form. Read while typing. */
    private volatile Map<String, String> savedCapitals = Map.of();
    /** Names on this phone (see {@link PhoneNames}), by their lowercase form. Never learned, saved or logged. */
    private volatile Map<String, String> phoneNames = Map.of();
    /** The same names in the order they go to the swipe engines while there is room: contacts' names first, then apps'. */
    private volatile List<String> phoneNameOrder = List.of();
    /**
     * How many words the FUTO engine's personal list holds: its native library refuses more (personal_dictionary.cpp),
     * and the refusal would stop every swipe. Saved words come first, then names on this phone while there is room.
     */
    static final int PERSONAL_LIMIT = 256;
    /** Whether the one-time repair of saved words' case has run: see [repairCase]. */
    private static final String WORD_CASE = "word_case";
    /** Where the dictionary words you took out are kept: see [unsavedWords]. */
    private static final String UNSAVED_WORDS = "unsaved_words";
    private volatile long namesRevision;
    private volatile BlockedWords blockedWords;
    /**
     * Dictionary words you took out with Unsave (owner, 2026-11-13): never suggested, swiped or fixed into, yet not blocked, so
     * typing one twice learns it back, and Save brings it back at once. See [unsaveWord].
     */
    private volatile BlockedWords unsavedWords;
    /** Blocked words and the dictionary words you took out, together: what suggestions, swipes and fixes leave out. */
    private volatile BlockedWords hiddenWords;
    private volatile ForgetWords.RefusedFixes refusedFixes;
    private final NextWordPredictor nextWords;
    /** Swipe engine 2.0's models, once loaded. Until then, or if they fail, the FUTO engine works alone. */
    private volatile SwipeModels models;
    private final AtomicBoolean modelsLoading = new AtomicBoolean();
    private volatile String[] personalWords = new String[0];
    private volatile float[] personalFrequencies = new float[0];

    public static final class Words {
        public final String[] words, descriptions, swipeStatuses;
        Words(String[] words, String[] descriptions, String[] swipeStatuses) {
            this.words = words; this.descriptions = descriptions; this.swipeStatuses = swipeStatuses;
        }
    }
    public interface Callback {
        void result(String[] words, long elapsed, String error);
        default void result(String[] words, long elapsed, String error, Prediction prediction) { result(words, elapsed, error); }
    }
    public interface OperationCallback { void result(String error); }
    public static final class Prediction {
        final String firstWord;
        final EnglishSpelling.Variant variant;
        final boolean personalized;
        final long epoch, generation;
        final SwipeDecoder.Result[] results;
        final boolean ranked;
        /** The ranker's numbers for each candidate, keyed by comparison form, or null when the ranker did not run. */
        volatile Map<String, double[]> features;
        Prediction(String firstWord, EnglishSpelling.Variant variant, boolean personalized, long epoch,
                   long generation, SwipeDecoder.Result[] results) {
            this(firstWord, variant, personalized, epoch, generation, results, false);
        }
        Prediction(String firstWord, EnglishSpelling.Variant variant, boolean personalized, long epoch,
                   long generation, SwipeDecoder.Result[] results, boolean ranked) {
            this.firstWord = firstWord; this.variant = variant; this.personalized = personalized;
            this.epoch = epoch; this.generation = generation; this.results = results; this.ranked = ranked;
        }
        /** Whether engine 2.0's ranker chose this order, so the scores are the ranker's. */
        public boolean ranked() { return ranked; }
        /** The decoder's words and scores, best first, before spelling and blocked words apply. */
        public SwipeDecoder.Result[] decoded() { return results; }
        /** How far the best word leads the next one, or infinity when no other word was found. */
        public float margin() { return results.length < 2 ? Float.POSITIVE_INFINITY : results[0].score - results[1].score; }
    }
    public static synchronized SwipeTyping get(Context context) {
        if (instance == null) instance = new SwipeTyping(context.getApplicationContext());
        return instance;
    }
    private SwipeTyping(Context context) {
        this.context = context;
        nextWords = new NextWordPredictor(new UserNGramStore(context), System::currentTimeMillis,
                word -> wordAllowed(rendered(word, TypingSettings.spelling(context))));
        blockedWords = new BlockedWords(context.getSharedPreferences("blocked_words", 0).getStringSet("words", java.util.Set.of()));
        unsavedWords = new BlockedWords(context.getSharedPreferences(UNSAVED_WORDS, 0).getStringSet("words", java.util.Set.of()));
        hide();
        refusedFixes = new ForgetWords.RefusedFixes(context.getSharedPreferences(REFUSED_FIXES, 0).getStringSet("fixes", java.util.Set.of()));
        Calibration.init(context);
        learningEnabled = storedLearning();
        context.getSharedPreferences("word_learning", 0).edit().remove("gesture_enabled").apply();
        accuracyFile = new AtomicFile(new File(context.getNoBackupFilesDir(), "word-accuracy-v1"));
        worker.execute(() -> {
            loadHabits();
            try {
                try (DataInputStream input = new DataInputStream(accuracyFile.openRead())) { accuracy.read(input); }
                catch (IOException | RuntimeException ignored) { accuracy = new PersonalizationStats(); }
                // Coordinate calibration and its statistics are deliberately not migrated into word learning.
                new AtomicFile(new File(context.getNoBackupFilesDir(), "swipe-calibration-v1")).delete();
                new AtomicFile(new File(context.getNoBackupFilesDir(), "swipe-accuracy-v1")).delete();
                loadEngine();
            } catch (IOException | RuntimeException | LinkageError error) {
                unavailable(error);
            }
        });
    }

    private void loadEngine() throws IOException {
        long began = SystemClock.uptimeMillis();
        if (recogniser != null) recogniser.close();
        recogniser = null; typedPredictions = null;
        configuredRevision = -1; configuredDay = -1; activeCount = 0;
        recogniser = new SwipeRecognizer(context);
        try (Reader vocabulary = new InputStreamReader(context.getAssets().open("swipe/hungry_jellyfish/vocab.txt"), "UTF-8")) {
            ProperNames.load(vocabulary);
        }
        try (Reader vocabulary = new InputStreamReader(context.getAssets().open("swipe/hungry_jellyfish/vocab.txt"), "UTF-8");
             Reader dictionary = new InputStreamReader(context.getAssets().open("swipe/english.combined"), "UTF-8")) {
            typedPredictions = new TypedPredictions(vocabulary, dictionary, word -> recogniser.frequency(word));
        }
        sentenceStarts = FirstWords.popular(recogniser.firstWords(), typedPredictions::contains);
        repairCase();
        syncDictionary(); publish();
        loadError = null; readiness = "ready";
        Log.d("TouchpadSwipe", "Swipe engine ready in " + (SystemClock.uptimeMillis() - began) + " ms");
        loadModels();
    }

    /** Loads engine 2.0's models on their own thread, as they take seconds and swiping works without them. */
    private void loadModels() {
        if (models != null || !modelsLoading.compareAndSet(false, true)) return;
        Thread loader = new Thread(() -> {
            try {
                SwipeModels loaded = SwipeModels.load(context);
                loaded.personalWords(personalWords, personalFrequencies);
                models = loaded;
            } catch (Exception | LinkageError error) {
                Log.w("TouchpadSwipe", "Swipe engine 2.0 unavailable; the FUTO engine works alone", error);
            } finally {
                modelsLoading.set(false);
            }
        }, "swipe-models-2");
        loader.setPriority(Thread.MIN_PRIORITY);
        loader.start();
    }

    /**
     * The next-word model's log chance of {@code word} straight after {@code before}, or NaN when it has not been worked
     * out yet. Never waits, so typing is never held up for it.
     */
    public double nextWordChance(String before, String word) {
        SwipeModels loaded = models;
        if (loaded == null) return Double.NaN;
        try { return loaded.ifReady(before, word); }
        catch (RuntimeException error) { return Double.NaN; }
    }

    private void unavailable(Throwable error) {
        SwipeRecognizer failed = recogniser;
        recogniser = null; typedPredictions = null;
        loadError = "Swipe typing unavailable. Retry loading in settings.";
        readiness = "unavailable";
        if (failed != null) {
            try { failed.close(); }
            catch (RuntimeException | LinkageError closeError) { error.addSuppressed(closeError); }
        }
        Log.e("TouchpadSwipe", "Could not load FUTO Swipe", error);
    }

    public String readiness() { return readiness; }
    public String availabilityError() { return loadError; }

    /** Explicit recovery is bounded; recognition never reloads native models implicitly. */
    public void retryLoading(OperationCallback callback) {
        worker.execute(() -> {
            String error = null;
            if (!"ready".equals(readiness)) {
                if (loadingRetries >= 2) error = "Swipe loading retry limit reached. Restart TitanKeys to try again.";
                else {
                    loadingRetries++; readiness = "loading";
                    try { loadEngine(); }
                    catch (IOException | RuntimeException | LinkageError failure) { unavailable(failure); error = loadError; }
                }
            }
            operationResult(callback, error);
        });
    }

    private void operationResult(OperationCallback callback, String error) {
        main.post(() -> callback.result(error));
    }

    private LearningConfigs configs() throws IOException {
        if (configs == null) {
            configs = new LearningConfigs(new File(context.getFilesDir(), "config"), context.getNoBackupFilesDir());
            dictionaryFile = configs.words;
            try { dictionary = configs.loadWords(); wordsAvailable = true; wordsSaveError = ""; }
            catch (IOException | RuntimeException error) { Log.w("TouchpadSwipe", "Saved words unavailable", error); }
            configuredRevision = -1;
            publish();
        }
        return configs;
    }

    private void loadHabits() {
        try { configs(); }
        catch (IOException | RuntimeException error) {
            publish();
            Log.w("TouchpadSwipe", "Habits storage unavailable", error);
        }
    }
    public void whenReady(Callback callback) { worker.execute(() -> main.post(() -> callback.result(new String[0], 0, loadError))); }
    public void recognise(Swipe gesture, String text, Callback callback) { recognise(gesture, text, true, callback); }
    public void recognise(Swipe gesture, String text, boolean personalise, Callback callback) {
        final String[] contextWords = SentenceContext.words(text);
        final EnglishSpelling.Variant variant = TypingSettings.spelling(context);
        final long epoch = accuracyEpoch, generation = learningGeneration;
        InferenceTiming.Request timing = new InferenceTiming.Request();
        worker.execute(() -> {
            timing.start();
            long began = SystemClock.uptimeMillis();
            try {
                if (recogniser == null) throw new IllegalStateException(loadError);
                syncDictionary();
                boolean saved = learningEnabled && wordsAvailable && activeCount > 0;
                // Saved words and names on this phone both reach the engines through the personal list.
                boolean personalized = personalise && (saved || nameCount > 0);
                java.util.function.Predicate<String> allowed = word -> wordAllowed(rendered(word, variant));
                SwipeDecoder.Result[] decoded = recogniser.recognise(gesture, contextWords, personalized, allowed);
                SwipeModels loaded = models;
                boolean ranked = false;
                Map<String, double[]> features = null;
                if (loaded != null) {
                    try { decoded = loaded.rank(decoded, recogniser.readings(), gesture, text, allowed); ranked = true; features = loaded.lastFeatures; }
                    catch (Exception | LinkageError error) { Log.w("TouchpadSwipe", "Swipe ranker skipped", error); }
                }
                String[] words = hiddenWords.filter(EnglishSpelling.candidates(Arrays.stream(decoded).map(result -> result.word).toArray(String[]::new), variant));
                // Saved spellings only where the swipe was personalised. A name on this phone gets its capitals in any
                // field, a search field too, as reading it keeps nothing.
                for (int i = 0; i < words.length; i++)
                    words[i] = nameCase(personalized && saved ? dictionary.restoreCase(words[i], configuredDay) : words[i]);
                // Accuracy stats follow saved words only, as they did before names on this phone.
                Prediction prediction = new Prediction(words.length == 0 ? "" : words[0], variant,
                        personalise && saved, epoch, generation, decoded, ranked);
                prediction.features = features;
                long elapsed = SystemClock.uptimeMillis() - began;
                timing.finish();
                main.post(() -> {
                    recognitionTiming.record(timing, false, false, true);
                    callback.result(hiddenWords.filter(words), elapsed, null, prediction);
                });
            } catch (RuntimeException | LinkageError error) {
                if (recogniser != null && !recogniser.isOpen()) unavailable(error);
                timing.finish();
                main.post(() -> {
                    recognitionTiming.record(timing, true, false, true);
                    callback.result(new String[0], 0, "Swipe recognition failed");
                });
            }
        });
    }
    /** Offers the look-alike that fits the next word better, such as "were" for "we're you", or null. */
    public void lookAlike(String before, String word, String next, java.util.function.Consumer<String> result) {
        final EnglishSpelling.Variant variant = TypingSettings.spelling(context);
        worker.execute(() -> {
            String replacement = null;
            try {
                String found = recogniser == null ? null : recogniser.lookAlike(SentenceContext.words(before), word, next.replace('\u2019', '\''));
                if (found != null && wordAllowed(rendered(found, variant))) replacement = rendered(found, variant);
            } catch (RuntimeException | LinkageError error) {
                if (recogniser != null && !recogniser.isOpen()) unavailable(error);
            }
            final String offered = replacement;
            main.post(() -> result.accept(offered));
        });
    }

    /** Gets the next-word list for this text ready, so that it does not have to be worked out when it is needed. */
    public void prepare(String text, AtomicBoolean cancelled) {
        worker.execute(() -> {
            if (cancelled.get() || recogniser == null) return;
            try { recogniser.nextWords(SentenceContext.words(text)); }
            catch (RuntimeException | LinkageError error) {
                if (recogniser != null && !recogniser.isOpen()) unavailable(error);
            }
            SwipeModels loaded = models;
            if (loaded != null && !cancelled.get()) {
                try { loaded.prepare(text); }
                catch (Exception | LinkageError error) { Log.w("TouchpadSwipe", "Next-word model skipped", error); }
            }
        });
    }

    public void complete(String before, AtomicBoolean cancelled, boolean sentenceCapitalisation, Callback callback) {
        final EnglishSpelling.Variant variant = TypingSettings.spelling(context);
        final boolean autoCapitalise = sentenceCapitalisation && TypingSettings.autoCapitalise(context);
        InferenceTiming.Request timing = new InferenceTiming.Request();
        worker.execute(() -> {
            timing.start();
            if (cancelled.get()) {
                timing.finish(); completionTiming.record(timing, false, true, false); return;
            }
            String[] result;
            String failure = null;
            try {
                if (typedPredictions == null) throw new IllegalStateException(loadError);
                final String prefix = TypedPredictions.prefix(before);
                final String contextText = before.substring(0, before.length() - prefix.length());
                int day = today();
                List<LanguageHabits.Entry> personal = learningEnabled && wordsAvailable ? dictionary.active(day) : List.of();
                if (cancelled.get()) {
                    timing.finish(); completionTiming.record(timing, false, true, false); return;
                }
                Map<String, Double> likely = recogniser == null ? Map.of() : recogniser.nextWords(SentenceContext.words(contextText));
                // The next-word model puts the right word in the top three more often, before any letter and after the
                // first few. After letters it ranks only the words it holds whole; the rest follow by frequency.
                SwipeModels loaded = models;
                if (loaded != null) {
                    try {
                        // After letters, the user's own words count too: how often they kept each one (#38, #39).
                        Map<String, Integer> kept = Map.of();
                        if (!prefix.isEmpty() && learningEnabled) {
                            try { kept = nextWords.keptStarting(TypedPredictions.key(prefix)); }
                            catch (RuntimeException error) { Log.w("TouchpadSwipe", "Could not read the kept words", error); }
                        }
                        Map<String, Double> predicted = prefix.isEmpty() ? loaded.nextWords(contextText, typedPredictions::contains)
                                : loaded.completions(contextText, prefix, typedPredictions::contains, kept);
                        if (!predicted.isEmpty()) likely = predicted;
                    } catch (Exception | LinkageError error) { Log.w("TouchpadSwipe", "Next-word model skipped", error); }
                }
                String[] found = EnglishSpelling.candidates(typedPredictions.suggest(prefix, personal, day,
                        word -> hiddenWords.entries().isEmpty() || wordAllowed(rendered(word, variant)), likely), variant);
                if (prefix.isEmpty() && learningEnabled) found = NextWordPredictor.combined(found, learnedNextWords(contextText, variant));
                result = hiddenWords.filter(found);
                boolean upper = prefix.length() > 1 && prefix.equals(prefix.toUpperCase(Locale.UK));
                String casedAfter = beforeSuggestion(contextText, prefix);
                for (int i = 0; i < result.length; i++) {
                    // No name's capitals while a word is in progress: "togg" is never "Toggle". The typed word gets them when it ends.
                    String word = learningEnabled && wordsAvailable ? dictionary.restoreCase(result[i], today()) : result[i];
                    result[i] = upper ? word.toUpperCase(Locale.UK) : capitalise(word, casedAfter, 0,
                            !prefix.isEmpty() && Character.isUpperCase(prefix.charAt(0)), autoCapitalise);
                }
            } catch (RuntimeException | LinkageError error) {
                if (recogniser != null && !recogniser.isOpen()) unavailable(error);
                result = new String[0]; failure = "Word completion failed";
            }
            String[] words = result;
            String error = failure;
            timing.finish();
            main.post(() -> {
                boolean isCancelled = cancelled.get();
                completionTiming.record(timing, error != null, isCancelled, true);
                if (!isCancelled) callback.result(hiddenWords.filter(words), timing.finished - timing.started, error);
            });
        });
    }

    /** What the user has kept after this text. A problem with the stored pairs must not cost the model's suggestions. */
    private List<String> learnedNextWords(String text, EnglishSpelling.Variant variant) {
        String previous = KeptWords.previousWord(text);
        if (previous == null) return List.of();
        try {
            List<String> learned = previous.isEmpty() ? nextWords.predictSentenceStart() : nextWords.predict(previous);
            return Arrays.asList(EnglishSpelling.candidates(learned.toArray(new String[0]), variant));
        } catch (RuntimeException error) {
            Log.w("TouchpadSwipe", "Could not read the learned next words", error);
            return List.of();
        }
    }

    /** The user kept {@code next} after {@code previous}, which is null if {@code next} starts a sentence. */
    public void keptPair(String previous, String next) {
        if (!learningEnabled) return;
        // Names on this phone are never learned, so no pair with one is kept either.
        if (phoneName(next) || previous != null && phoneName(previous)) return;
        if (previous == null) nextWords.learnSentenceStart(next);
        else nextWords.learn(previous, next);
    }

    public boolean learningEnabled() { return learningEnabled; }
    /** Engine 2.0's models are loaded, so swipes are ranked by them. Replays wait for this to score engine 2.0. */
    public boolean modelsReady() { return models != null; }
    /** Engine 2.0's models are still loading. */
    public boolean modelsLoading() { return modelsLoading.get(); }
    boolean feedbackEnabled() { return learningEnabled && wordsAvailable; }
    public long learningGeneration() { return learningGeneration; }
    public String learningStatus() { return (learningEnabled ? "Learning on · " : "Language habits paused · ") + learningStatus + wordsSaveError; }
    String accuracyStats() { return accuracyStatus; }
    public Words learnedWords() { return wordView; }
    public String[] blockedWords() { return blockedWords.words(); }
    /** Whether [word] may be suggested, swiped or fixed into: not blocked, and not a dictionary word you took out. */
    public boolean wordAllowed(String word) { return hiddenWords.allowed(word); }
    /** Whether [word] is on Blocked words. */
    public boolean blockedWord(String word) { return blockedWords.contains(word); }
    /** Whether [word] is a dictionary word you took out with Unsave: see [unsaveWord]. */
    public boolean unsavedWord(String word) { return unsavedWords.contains(word); }

    /** Runs where the lists change: the two lists of words to leave out, as one. */
    private void hide() {
        java.util.Set<String> both = new java.util.HashSet<>(blockedWords.entries());
        both.addAll(unsavedWords.entries());
        hiddenWords = new BlockedWords(both);
    }

    public TypedPredictions typedPredictions() { return typedPredictions; }

    /** The commonest ways to start a sentence, best first. Empty until the word lists have loaded. */
    public List<String> sentenceStarts() { return sentenceStarts; }

    /** Whether the user added this word or used it enough to count as learned. A word typed once or twice may be a typo. */
    public boolean personalWord(String word) {
        String key = SwipeWords.normalise(word);
        return activeWords.contains(key) || phoneNames.containsKey(key);
    }

    /**
     * The spelling that the user gave a saved word when it has capitals, such as "Lok" for "lok", or null. Only while
     * learning is on, as the saved words only count then.
     */
    public String savedSpelling(String word) {
        String key = SwipeWords.normalise(word);
        String saved = learningEnabled ? savedCapitals.get(key) : null;
        if (saved != null) return saved;
        String name = phoneNames.get(key);
        return name == null || name.equals(key) ? null : name;
    }

    /**
     * Takes the names on this phone: the words of app names and, if the user turned it on, of contacts' names. Replaces
     * the earlier ones, so turning contacts off clears theirs at once. The engines take them before the next swipe.
     */
    public void phoneNames(Map<String, String> contacts, Map<String, String> apps) {
        phoneNames = Map.copyOf(PhoneNames.merged(apps, contacts));
        phoneNameOrder = PhoneNames.order(contacts, apps);
        namesRevision++;
    }

    /** How common the base list makes this word, 0 if it lacks it, for names on this phone. 0 until the lists load. */
    public float baseFrequency(String word) {
        SwipeRecognizer loaded = recogniser;
        return loaded == null ? 0 : loaded.frequency(SwipeWords.normalise(word));
    }

    /** Whether the base list has this word in lowercase, an ordinary word ("toggle"), not only a name ("Spotify"). */
    public boolean baseLowercase(String word) {
        TypedPredictions loaded = typedPredictions;
        return loaded == null || loaded.lowercase(word);
    }

    /** Whether this word is one of the names on this phone, for anything that must leave them out, such as contributions. */
    public boolean phoneName(String word) {
        return phoneNames.containsKey(SwipeWords.normalise(word));
    }

    /** A swiped or completed word in a name's own capitals, when it is a name on this phone written in lowercase. */
    private String nameCase(String word) {
        if (!word.equals(word.toLowerCase(Locale.ROOT))) return word;
        String name = phoneNames.get(SwipeWords.normalise(word));
        return name == null ? word : name;
    }

    /** The spelling of a name, such as "Monday" for "monday", or null for any other word. */
    public String properName(String word) {
        String spelling = ProperNames.restore(word);
        return spelling.equals(word) ? null : spelling;
    }

    /**
     * The next-word model's summed log chance of each of {@code texts} after {@code before}, or null while it isn't loaded. It runs
     * the model once for each token, so it must run on {@link #background}'s thread, never on the main thread.
     */
    public double[] continuations(String before, String[] texts) {
        SwipeModels loaded = models;
        if (loaded == null) return null;
        try { return loaded.continuations(before, texts); }
        catch (Exception | LinkageError error) {
            Log.w("TouchpadSwipe", "Next-word model skipped", error);
            return null;
        }
    }

    /** Runs {@code work} on the engine's own thread, after the work already waiting there, and hands its answer to the main thread. */
    public void background(java.util.function.Supplier<String> work, java.util.function.Consumer<String> result) {
        worker.execute(() -> {
            String answer = null;
            try { answer = work.get(); }
            catch (RuntimeException | LinkageError error) { Log.w("TouchpadSwipe", "Background work failed", error); }
            final String done = answer;
            main.post(() -> result.accept(done));
        });
    }

    /** Whether this kind of autocorrect fix is on, by its id: Settings > What gets fixed. */
    public boolean fixOn(String id) {
        return TypingSettings.fixOn(context, id);
    }

    /** Whether autocorrect fixes a word only when it's sure, as Settings asks. */
    public boolean fixOnlyWhenSure() {
        return TypingSettings.fixOnlyWhenSure(context);
    }

    /** Whether chat shorthand such as "shd" becomes its full word, as Settings asks. */
    public boolean expandShorthand() {
        return TypingSettings.expandShorthand(context);
    }

    /** Whether the user switched off the listed shorthand, in lowercase, so it stays as typed. */
    public boolean shorthandOff(String shorthand) {
        return TypingSettings.shorthandOff(context).contains(shorthand);
    }

    /** The full word of the user's own shorthand, in lowercase, or null if it isn't one of theirs. */
    public String ownShorthand(String shorthand) {
        return TypingSettings.ownShorthand(context).get(shorthand);
    }

    /** The word in the English spelling that the settings ask for. */
    public String preferredSpelling(String word) {
        return EnglishSpelling.convert(word, TypingSettings.spelling(context));
    }

    /** Whether a typed word is complete, so the next suggestions should follow it instead of correcting it. */
    public boolean knownWord(String word) {
        TypedPredictions predictions = typedPredictions;
        if (predictions != null && predictions.contains(word)) return true;
        return Arrays.stream(wordView.words).anyMatch(word::equalsIgnoreCase);
    }

    public void setWordBlocked(String word, boolean blocked, OperationCallback callback) {
        worker.execute(() -> operationResult(callback, block(word, blocked)));
    }

    /**
     * The word menu's Unblock: [word] comes off Blocked words onto the rung under it, not saved. A dictionary word blocked from
     * Settings never went on the list of words taken out, so it goes on it now; without that the menu showed Not saved and then
     * Saved on opening again (bug hunt #25). Settings' own unblock ([setWordBlocked]) gives the dictionary word back whole.
     */
    public void unblockWord(String word, OperationCallback callback) {
        worker.execute(() -> {
            String failure = block(word, false);
            if (failure == null && builtInWord(word) && !unsavedWords.contains(word)) failure = unsave(word, true);
            operationResult(callback, failure);
        });
    }

    /** Runs on the worker. Returns why it failed, or null. A word that comes back can be a fix again. */
    private String block(String word, boolean blocked) {
        boolean persisted = false;
        try {
            BlockedWords replacement = blocked ? blockedWords.add(word) : blockedWords.remove(word);
            if (!context.getSharedPreferences("blocked_words", 0).edit().putStringSet("words", replacement.entries()).commit())
                throw new IOException("Could not save blocked words");
            blockedWords = replacement;
            hide();
            learningGeneration++;
            configuredRevision = -1;
            persisted = true;
            if (blocked) nextWords.forget(word);
            else allowFixesTo(word);
            syncDictionary();
            return null;
        } catch (IllegalArgumentException error) {
            return error.getMessage();
        } catch (IOException | RuntimeException | LinkageError error) {
            return persisted ? "Blocked words are saved, but the swipe dictionary didn't update."
                    : "Couldn't save blocked words. Try again.";
        }
    }

    /** Whether autocorrect may change the letters [typed] to [fix]. Holding a fix on the strip refuses it. */
    public boolean fixAllowed(String typed, String fix) { return refusedFixes.allowed(typed, fix); }

    /**
     * Whether the base word list has [word], not only Saved words. The typed list can't tell: it was built with the saved words
     * of the time in it, so a saved word looked built in, and Forget only blocked it (owner's "utun", 2026-11-12).
     */
    public boolean builtInWord(String word) { return baseFrequency(word) > 0; }

    /**
     * Whether you saved [word] with Save, in the word menu or Settings (owner, 2026-11-12). A word learned from your typing is not
     * saved: see [learnedWord].
     */
    public boolean addedWord(String word) { return addedEntries.contains(SwipeWords.normalise(word)); }

    /**
     * Whether TitanKeys learned [word] from your typing, without your saving it: it knows the word, so it suggests it. The word
     * menu's Forget is only for these (owner, 2026-11-13: "Why is there a forget for a word clearly not known?"). A word still
     * gathering evidence, such as gibberish typed once, isn't learned.
     */
    public boolean learnedWord(String word) { return learnedEntries.contains(SwipeWords.normalise(word)); }

    /**
     * Whether your words hold [word] at all: saved, or put there by your typing, even one still gathering evidence. Forget takes
     * any of them out.
     */
    public boolean savedWord(String word) { return addedWord(word) || typedEntries.contains(SwipeWords.normalise(word)); }

    /**
     * The word menu's Block (owner, 2026-11-13): puts [word] on Blocked words, so it's never suggested, learned or fixed into. If it
     * was a fix for the letters [typed], autocorrect also stops making that fix. [typed] is empty when no letters were typed.
     * Unblock ([setWordBlocked] to false) takes it off and allows the fix again.
     */
    public void blockWord(String word, String typed, OperationCallback callback) {
        worker.execute(() -> {
            String failure = null;
            if (ForgetWords.fix(typed, word)) {
                ForgetWords.RefusedFixes replacement = refusedFixes.add(typed, word);
                if (saveRefusedFixes(replacement)) refusedFixes = replacement;
                else failure = "Could not save the change to autocorrect.";
            }
            String result = block(word, true);
            operationResult(callback, result != null ? result : failure);
        });
    }

    /**
     * The word menu's Unsave (owner, 2026-11-13): takes [word] out of what TitanKeys knows, without blocking it. It leaves Saved
     * words, with what was learned of it, and a dictionary word goes on the list of words you took out, so it's never suggested,
     * swiped or fixed into. Typing it twice learns it back ([typed]), and Save brings it back at once ([restoreWord]).
     */
    public void unsaveWord(String word, OperationCallback callback) {
        learningGeneration++;
        worker.execute(() -> {
            String failure = null;
            if (savedWord(word)) {
                failure = remove(word);
                publish();
            }
            if (failure == null && builtInWord(word)) failure = unsave(word, true);
            operationResult(callback, failure);
        });
    }

    /** Save on a dictionary word you took out: it's back. See [unsaveWord]. */
    public void restoreWord(String word, OperationCallback callback) {
        worker.execute(() -> operationResult(callback, unsave(word, false)));
    }

    /** Runs on the worker: puts [word] on the list of dictionary words you took out, or takes it off. Returns why it failed, or null. */
    private String unsave(String word, boolean out) {
        try {
            BlockedWords replacement = out ? unsavedWords.add(word) : unsavedWords.remove(word);
            if (!context.getSharedPreferences(UNSAVED_WORDS, 0).edit().putStringSet("words", replacement.entries()).commit())
                throw new IOException("Could not save the words taken out");
            unsavedWords = replacement;
            hide();
            learningGeneration++;
            configuredRevision = -1;
            if (out) nextWords.forget(word);
            syncDictionary();
            return null;
        } catch (IllegalArgumentException error) {
            return error.getMessage();
        } catch (IOException | RuntimeException | LinkageError error) {
            return out ? "Couldn't take the word out. Try again." : "Couldn't bring the word back. Try again.";
        }
    }

    private void allowFixesTo(String word) {
        ForgetWords.RefusedFixes replacement = refusedFixes.withoutFix(word);
        if (replacement != refusedFixes && saveRefusedFixes(replacement)) refusedFixes = replacement;
    }

    private boolean saveRefusedFixes(ForgetWords.RefusedFixes fixes) {
        return context.getSharedPreferences(REFUSED_FIXES, 0).edit().putStringSet("fixes", fixes.entries()).commit();
    }

    public void learningEnabled(boolean enabled) {
        learningEnabled = enabled; learningGeneration++;
        context.getSharedPreferences("word_learning", 0).edit().putBoolean("enabled", enabled).apply();
    }

    /** Whether to learn new words, as stored, or the default: on, unless an old version's setting said otherwise. */
    private boolean storedLearning() {
        return context.getSharedPreferences("word_learning", 0).getBoolean("enabled",
                context.getSharedPreferences("swipe_calibration", 0).getBoolean("enabled", true));
    }

    /** Settings > Reset most things: the learning switch goes back to its default. The words it learned stay. */
    public void resetLearning() {
        context.getSharedPreferences("word_learning", 0).edit().remove("enabled").apply();
        learningEnabled = storedLearning(); learningGeneration++;
    }
    void resetAccuracyStats() {
        accuracyEpoch++;
        worker.execute(() -> { accuracy = new PersonalizationStats(); accuracyFile.delete(); accuracyStatus = accuracy.summary(); });
    }
    public void clearDictionary(OperationCallback callback) {
        learningGeneration++;
        worker.execute(() -> {
            boolean persisted = false;
            String failure = null;
            try {
                nextWords.clearAll();
                LanguageHabits cleared = new LanguageHabits();
                configs().replaceWords(cleared);
                dictionary = cleared; wordsAvailable = true; wordsSaveError = ""; configuredRevision = -1;
                persisted = true;
                refreshDictionary();
            } catch (IOException | RuntimeException | LinkageError error) {
                failure = persisted ? "Saved words are cleared, but the swipe dictionary didn't update."
                        : "Couldn't reset saved words. Your words are still there.";
                wordsSaveError = persisted ? " · Swipe dictionary refresh failed" : " · Could not reset saved words";
                if (recogniser != null && !recogniser.isOpen()) unavailable(error);
            }
            try { publish(); } finally { operationResult(callback, failure); }
        });
    }
    public void addWord(String word, OperationCallback callback) {
        learningGeneration++;
        worker.execute(() -> {
            boolean persisted = false;
            String failure = null;
            try {
                configs();
                if (!wordsAvailable) throw new IOException("Saved words unavailable");
                LanguageHabits replacement = dictionary.copy();
                if (blockedWords.contains(word)) throw new IllegalArgumentException("That word is blocked. Take it off Blocked words first.");
                replacement.add(word.trim(), today());
                configs.saveWords(replacement);
                allowFixesTo(word);
                dictionary = replacement; configuredRevision = -1; wordsSaveError = "";
                persisted = true;
                refreshDictionary();
            } catch (IllegalArgumentException error) {
                failure = error.getMessage();
            } catch (IOException | RuntimeException | LinkageError error) {
                failure = persisted ? "The word is saved, but the swipe dictionary didn't update."
                        : "Couldn't save the word. Try again.";
                wordsSaveError = persisted ? " · Swipe dictionary refresh failed" : " · Could not save word";
                if (recogniser != null && !recogniser.isOpen()) unavailable(error);
            }
            try { publish(); } finally { operationResult(callback, failure); }
        });
    }
    public void removeWord(String word, OperationCallback callback) {
        learningGeneration++;
        worker.execute(() -> {
            String failure = remove(word);
            try { publish(); } finally { operationResult(callback, failure); }
        });
    }

    /** Runs on the worker: takes [word] out of Saved words, with the pairs learned with it. Returns why it failed, or null. */
    private String remove(String word) {
        learningGeneration++;
        boolean persisted = false;
        try {
            configs();
            if (!wordsAvailable) throw new IOException("Saved words unavailable");
            String normalised = SwipeWords.normalise(word);
            if (normalised.isEmpty()) throw new IllegalArgumentException("Invalid saved word");
            LanguageHabits replacement = dictionary.copy();
            replacement.remove(normalised);
            configs.saveWords(replacement);
            dictionary = replacement; configuredRevision = -1; wordsSaveError = "";
            persisted = true;
            nextWords.forget(word);
            refreshDictionary();
            return null;
        } catch (IOException | RuntimeException | LinkageError error) {
            wordsSaveError = persisted ? " · Swipe dictionary refresh failed" : " · Could not remove saved word";
            if (recogniser != null && !recogniser.isOpen()) unavailable(error);
            return persisted ? "The word is gone, but the swipe dictionary didn't update."
                    : "Couldn't remove the word. Nothing else changed.";
        }
    }

    public void exportConfig(Uri destination, OperationCallback callback) {
        worker.execute(() -> {
            String failure = null;
            try {
                if (!wordsAvailable || configs().hasPendingImport())
                    throw new IOException("Learning data unavailable");
                var backup = LearningConfigs.export(dictionary);
                try (OutputStream output = context.getContentResolver().openOutputStream(destination, "wt")) {
                    if (output == null) throw new IOException("Could not open the export file");
                    LearningConfigs.writeJson(output, backup);
                }
            } catch (IOException | RuntimeException error) {
                Log.w("TouchpadSwipe", "Could not export learning", error);
                failure = "Could not export learning. Restart TitanKeys and try again.";
            }
            operationResult(callback, failure);
        });
    }

    public void importConfig(Uri source, OperationCallback callback) {
        worker.execute(() -> {
            LanguageHabits imported;
            try (InputStream input = context.getContentResolver().openInputStream(source)) {
                if (input == null) throw new IOException("Could not open the import file");
                imported = dictionary.copy();
                imported.merge(LearningConfigs.readExport(input));
            } catch (BackupFormat.NotABackup error) {
                Log.w("TouchpadSwipe", "Not a learning backup", error);
                operationResult(callback, BackupFormat.NOT_A_BACKUP);
                return;
            } catch (IOException | RuntimeException error) {
                // A backup that is damaged, or a file that could not be opened.
                Log.w("TouchpadSwipe", "Could not read learning backup", error);
                operationResult(callback, BackupFormat.DAMAGED);
                return;
            }
            try {
                configs().importWords(imported);
            } catch (IOException | RuntimeException error) {
                Log.w("TouchpadSwipe", "Could not save learning backup", error);
                operationResult(callback, "Could not finish importing. Restart TitanKeys to recover your learning data.");
                return;
            }
            learningGeneration++;
            dictionary = imported;
            wordsAvailable = true;
            wordsSaveError = ""; configuredRevision = -1;
            String failure = null;
            try {
                syncDictionary();
            } catch (RuntimeException | LinkageError error) {
                Log.w("TouchpadSwipe", "Could not refresh the imported dictionary", error);
                failure = "Learning was imported. Restart TitanKeys to use the imported words.";
            }
            publish();
            operationResult(callback, failure);
        });
    }

    /** Whether the built-in dictionary already has this word, in either spelling. */
    private boolean standard(String word) {
        String letters = SwipeWords.letters(word);
        return recogniser.frequency(letters) > 0
                || recogniser.frequency(EnglishSpelling.convert(letters, EnglishSpelling.Variant.BRITISH)) > 0
                || recogniser.frequency(EnglishSpelling.convert(letters, EnglishSpelling.Variant.AMERICAN)) > 0;
    }
    private void refreshDictionary() {
        if (recogniser == null) throw new IllegalStateException("Swipe typing unavailable");
        syncDictionary();
    }
    static int today() { return (int) (System.currentTimeMillis() / 86400000L); }
    private void syncDictionary() {
        long names = namesRevision;
        boolean learning = learningEnabled;
        if (recogniser == null || (configuredRevision == dictionary.revision && configuredDay == today()
                && configuredNames == names && configuredLearning == learning)) return;
        // Saved words only count while learning is on. Names on this phone count either way, as they are never learned.
        int day = today();
        // Over the limit, the most used saved words stay.
        List<LanguageHabits.Entry> active = wordsAvailable && learning ? dictionary.active(day).stream()
                .filter(entry -> wordAllowed(entry.word))
                .sorted(java.util.Comparator.comparingDouble((LanguageHabits.Entry entry) -> -entry.frequency(0, day)))
                .limit(PERSONAL_LIMIT).toList() : java.util.Collections.emptyList();
        java.util.Set<String> savedKeys = new java.util.HashSet<>();
        for (LanguageHabits.Entry entry : active) savedKeys.add(entry.word);
        Map<String, String> phone = phoneNames;
        List<String> nameWords = phoneNameOrder.stream()
                .filter(word -> phone.containsKey(word) && !savedKeys.contains(word) && recogniser.frequency(word) <= 0
                        && wordAllowed(phone.get(word)))
                .limit(PERSONAL_LIMIT - active.size()).toList();
        String[] words = new String[active.size() + nameWords.size()];
        float[] frequencies = new float[words.length];
        for (int i = 0; i < active.size(); i++) {
            words[i] = active.get(i).word;
            frequencies[i] = active.get(i).frequency(recogniser.frequency(words[i]), today());
        }
        for (int i = 0; i < nameWords.size(); i++) {
            words[active.size() + i] = nameWords.get(i);
            frequencies[active.size() + i] = PhoneNames.FREQUENCY;
        }
        try { recogniser.personalWords(words, frequencies); }
        catch (RuntimeException | LinkageError error) {
            if (!recogniser.isOpen()) unavailable(error);
            throw error;
        }
        personalWords = words; personalFrequencies = frequencies;
        SwipeModels loaded = models;
        if (loaded != null) loaded.personalWords(words, frequencies);
        activeCount = active.size(); nameCount = nameWords.size(); configuredRevision = dictionary.revision; configuredDay = today();
        configuredNames = names; configuredLearning = learning;
        publish();
    }
    private static String rendered(String word, EnglishSpelling.Variant variant) { return EnglishSpelling.convert(SwipeSpelling.restore(word), variant); }
    private void publish() {
        String importWarning = configs == null ? "" : configs.importWarning();
        learningStatus = (wordsAvailable ? dictionary.status(today()) : "Saved words unavailable; existing files preserved") + importWarning;
        int day = today();
        wordView = new Words(dictionary.spellings(day), dictionary.descriptions(day), dictionary.swipeStatuses(day));
        activeWords = dictionary.activeWords(day);
        addedEntries = dictionary.addedWords();
        learnedEntries = dictionary.learnedWords(day);
        typedEntries = dictionary.typedWords();
        Map<String, String> capitals = new HashMap<>();
        if (wordsAvailable) for (LanguageHabits.Entry entry : dictionary.active(day))
            if (!entry.spelling.equals(entry.spelling.toLowerCase(Locale.ROOT)) && entry.spelling.equalsIgnoreCase(entry.word))
                capitals.put(entry.word, entry.spelling);
        savedCapitals = capitals;
        accuracyStatus = accuracy.summary();
    }

    public void typed(String intended, long generation) {
        typed(intended, generation, true);
    }

    public void typed(String intended, long generation, boolean rememberCase) {
        String word = SwipeWords.normalise(intended);
        if (!learningEnabled || !wordsAvailable || generation != learningGeneration || word.isEmpty()) return;
        worker.execute(() -> {
            if (!learningEnabled || !wordsAvailable || generation != learningGeneration || recogniser == null) return;
            // Only words the dictionary lacks are learned, and a dictionary word you took out with Unsave, which typing twice brings
            // back (owner, 2026-11-13).
            boolean unsaved = unsavedWords.contains(word);
            if ((standard(word) && !unsaved) || !blockedWords.allowed(word) || phoneName(word)) return;
            long revision = dictionary.revision;
            dictionary.typed(intended, today(), rememberCase);
            lastTyped = revision == dictionary.revision ? null : word;
            if (lastTyped == null) return;
            if (unsaved && dictionary.activeWords(today()).contains(word)) {
                // Learned back: the dictionary has it again, so its own entry goes.
                dictionary.remove(word);
                lastTyped = null;
                unsave(word, false);
            }
            try { syncDictionary(); }
            catch (RuntimeException | LinkageError error) {
                if (recogniser != null && !recogniser.isOpen()) unavailable(error);
                Log.w("TouchpadSwipe", "Could not refresh learned words");
            }
            saveDictionary(); publish();
        });
    }

    /** The user deleted the word they just typed, so that use was a mistake. */
    public void untyped(String intended, long generation) {
        String word = SwipeWords.normalise(intended);
        if (word.isEmpty()) return;
        worker.execute(() -> {
            // Only a use that was counted can be taken back.
            if (!word.equals(lastTyped)) return;
            lastTyped = null;
            if (!learningEnabled || !wordsAvailable || generation != learningGeneration || recogniser == null) return;
            long revision = dictionary.revision;
            dictionary.untyped(word, today());
            try { saveLearningChanges(revision); }
            catch (RuntimeException | LinkageError error) {
                if (recogniser != null && !recogniser.isOpen()) unavailable(error);
                Log.w("TouchpadSwipe", "Could not refresh learned words");
            }
        });
    }

    /** What a data contribution may take from a swipe, worked out on the engine's thread. */
    public interface ContributionRows { void result(double[][] rows, int kept); }

    /**
     * For data contributions: the ranker's numbers for each candidate of this swipe, without the words, and the row of
     * {@code word}, or -1 when the ranker did not have it. Nothing comes back when {@code word} is not a dictionary word,
     * so names and other personal words are never sent.
     */
    public void contributionRows(String word, Prediction prediction, ContributionRows result) {
        String normalised = SwipeWords.normalise(word);
        if (normalised.isEmpty() || prediction == null) return;
        worker.execute(() -> {
            try {
                // Names on this phone are never sent, even one that is also a dictionary word.
                if (recogniser == null || !standard(normalised) || phoneName(normalised)) return;
                Map<String, double[]> features = prediction.features;
                double[][] rows = features == null ? new double[0][] : features.values().toArray(new double[0][]);
                int kept = -1, index = 0;
                String key = CtcWords.key(word);
                if (features != null) for (String candidate : features.keySet()) {
                    if (candidate.equals(key)) kept = index;
                    index++;
                }
                result.result(rows, kept);
            } catch (RuntimeException | LinkageError error) {
                Log.w("TouchpadSwipe", "Contribution skipped");
            }
        });
    }

    public void confirm(Swipe gesture, String text, String intended, long generation, Prediction prediction, boolean candidate) {
        feedback(gesture, text, intended, generation, prediction, candidate ? LearningEvidence.CHOICE : LearningEvidence.CORRECTION);
    }

    public void retained(Swipe gesture, String text, String intended, long generation, Prediction prediction) {
        feedback(gesture, text, intended, generation, prediction, LearningEvidence.KEPT);
    }

    private void feedback(Swipe gesture, String text, String intended, long generation, Prediction prediction, LearningEvidence evidence) {
        String word = SwipeWords.normalise(intended);
        if (!feedbackEnabled() || generation != learningGeneration || word.isEmpty() || prediction == null) return;
        if (evidence.replacement && !WordSimilarity.replacement(prediction.firstWord, word)) return;
        if (pendingExamples.incrementAndGet() > 4) { pendingExamples.decrementAndGet(); return; }
        final String[] contextWords = SentenceContext.words(text);
        worker.execute(() -> {
            try {
                if (!feedbackEnabled() || generation != learningGeneration || recogniser == null || prediction.generation != generation || !wordAllowed(word)) return;
                long revision = dictionary.revision;
                boolean direct = evidence == LearningEvidence.CHOICE || evidence == LearningEvidence.CORRECTION;
                if (evidence == LearningEvidence.KEPT) {
                    if (!PersonalizationStats.sameWord(prediction.firstWord, word)) return;
                    dictionary.retained(word, today());
                    saveLearningChanges(revision); return;
                }
                SwipeDecoder.Result[] baseline = prediction.personalized ? recogniser.recognise(gesture, contextWords, false) : prediction.results;
                String first = baseline.length == 0 ? "" : rendered(baseline[0].word, prediction.variant);
                boolean standardCorrect = PersonalizationStats.sameWord(first, intended);
                boolean personalCorrect = PersonalizationStats.sameWord(prediction.firstWord, intended);
                if (direct && prediction.epoch == accuracyEpoch) {
                    accuracy.record(standardCorrect, personalCorrect, prediction.personalized, evidence == LearningEvidence.CHOICE);
                    accuracyStatus = accuracy.summary(); saveAccuracy();
                }
                SwipeDecoder.Result match = null;
                for (SwipeDecoder.Result result : prediction.results)
                    if (PersonalizationStats.sameWord(rendered(result.word, prediction.variant), intended)) match = result;
                boolean fits = standardCorrect || match != null || (baseline.length > 0
                        && recogniser.fits(gesture, contextWords, word, baseline[0]));
                if (!fits) return;
                if (prediction.personalized && prediction.results.length > 0) {
                    String previous = rendered(prediction.results[0].word, prediction.variant);
                    if (!PersonalizationStats.sameWord(previous, word) && dictionary.active(today()).stream().anyMatch(entry -> entry.word.equals(SwipeWords.normalise(previous))))
                        dictionary.regret(previous);
                }
                if (phoneName(word)) { /* A name on this phone is never learned. */ }
                else if (!direct) dictionary.retained(word, today());
                else if (!standard(word)) dictionary.confirm(word, today());
                saveLearningChanges(revision);
            } catch (RuntimeException | LinkageError error) {
                if (recogniser != null && !recogniser.isOpen()) unavailable(error);
                Log.w("TouchpadSwipe", "Learning feedback skipped");
            }
            finally { pendingExamples.decrementAndGet(); }
        });
    }
    private void saveLearningChanges(long revision) {
        if (revision != dictionary.revision) { syncDictionary(); saveDictionary(); }
        publish();
    }

    private void saveAccuracy() {
        FileOutputStream stream = null;
        try {
            stream = accuracyFile.startWrite(); DataOutputStream output = new DataOutputStream(stream);
            accuracy.write(output); output.flush(); accuracyFile.finishWrite(stream);
        } catch (IOException error) {
            if (stream != null) accuracyFile.failWrite(stream);
            accuracyStatus = accuracy.summary() + "\nAccuracy stats could not be saved.";
        }
    }
    /**
     * Once, on the update that brought it (owner, 2026-11-13: "why is And kept on auto correcting to cap? And AUTO too?"): a saved
     * or learned word that the dictionary has in lowercase, kept with capitals that a sentence start, Shift or Caps Lock gave it,
     * goes back to lowercase, so autocorrect stops writing it with capitals everywhere. See [WordCase]. Runs on the worker, once
     * the word lists have loaded.
     */
    private void repairCase() {
        android.content.SharedPreferences preferences = context.getSharedPreferences(WORD_CASE, 0);
        if (!wordsAvailable || preferences.getBoolean("repaired", false)) return;
        TypedPredictions lowercase = typedPredictions;
        int changed = dictionary.lowercase(word -> baseFrequency(word) > 0 && lowercase.lowercase(word));
        if (changed > 0) saveDictionary();
        preferences.edit().putBoolean("repaired", true).apply();
        if (changed > 0) Log.i("TouchpadSwipe", "Put " + changed + " saved words back in lowercase");
    }

    private void saveDictionary() {
        if (!wordsAvailable) return;
        try {
            if (configs == null) throw new IOException("Config storage unavailable");
            configs.saveWords(dictionary); wordsSaveError = "";
        } catch (IOException error) { wordsSaveError = " · Could not save words"; }
    }

    public static String capitalise(String text, String before) { return SentenceCase.capitalise(text, before); }

    /** Whether the next letter after [before] starts a sentence. */
    public static boolean startsSentence(CharSequence before) { return SentenceCase.startsSentence(before); }

    public static String capitalise(String str, String str2, int i, boolean z, boolean autoCapitalise) {
        if ((1048576 & i) != 0) {
            return str.toUpperCase(Locale.UK);
        }
        if ((i & 193) != 0 || z) {
            if (str.isEmpty()) {
                return str;
            }
            return str.substring(0, 1).toUpperCase(Locale.UK) + str.substring(1);
        }
        return autoCapitalise ? capitalise(str, str2) : str;
    }

    public static String prefix(String str) {
        if (str.isEmpty()) {
            return "";
        }
        char cCharAt = str.charAt(str.length() - 1);
        return (Character.isLetterOrDigit(cCharAt) || ".,!?;:)]".indexOf(cCharAt) >= 0) ? " " : "";
    }

    /** The text that a suggestion follows. A word picked with no letters typed first gets the space that {@link #prefix} gives. */
    public static String beforeSuggestion(String contextText, String typed) {
        return typed.isEmpty() ? contextText + prefix(contextText) : contextText;
    }
}
