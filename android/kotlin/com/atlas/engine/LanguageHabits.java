package com.atlas.engine;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded evidence for FUTO word frequencies. It never stores gesture coordinates. */
final class LanguageHabits {
    // Only words missing from the dictionary are learned. In the raw logs few such words came up more than twice, so
    // with three uses needed almost nothing was learned. Two uses, and room for more words, let the list grow.
    static final int LIMIT = 1000;
    private static final int USES_TO_ACTIVATE = 2;
    private static final int DAYS_PER_FADE = 30;
    static final class Entry {
        final String word;
        String spelling;
        int confirmations, typedUses, regrets, day, recoveryConfirmations, recoveryTypedUses;
        boolean held;
        boolean typed;
        /** Added in Settings, so it never fades. */
        boolean added;
        Entry(String word, int day) { this.word = word; this.spelling = word; this.day = day; }
        int decay(int today) { return added ? 0 : Math.max(0, today - day) / DAYS_PER_FADE; }
        int support(int today) { return Math.max(0, Math.max(confirmations, typedUses) - decay(today)); }
        boolean active(int today) { return !held && support(today) >= USES_TO_ACTIVATE; }
        /** Words that are still gathering evidence stay out of the list, so a one-off typo never shows. */
        boolean shown(int today) { return added || held || active(today); }
        boolean faded(int today) { return !added && !held && support(today) == 0; }
        float frequency(float base, int today) {
            return active(today) ? Math.min(255, (base > 0 ? base : 96) + Math.min(96, 8 + Math.max(0, support(today) - USES_TO_ACTIVATE) * 8)) : 0;
        }
    }
    private final LinkedHashMap<String, Entry> words = new LinkedHashMap<>();
    long revision;

    void add(String intended, int today) {
        String word = SwipeWords.normalise(intended);
        if (word.isEmpty()) throw new IllegalArgumentException("Type one word of 2 to 24 letters. Apostrophes are fine.");
        if (!SuggestionPolicy.allowed(word)) throw new IllegalArgumentException("TitanKeys can't save that word.");
        Entry entry = words.get(word);
        if (entry == null) entry = create(word, today);
        if (entry == null) throw new IllegalStateException("Your word list is full. Remove a word, then try again.");
        entry.spelling = intended.replace('\u2019', '\'');
        entry.typedUses = Math.max(USES_TO_ACTIVATE, entry.support(today));
        entry.confirmations = Math.max(0, entry.confirmations - entry.decay(today));
        entry.typed = true;
        entry.added = true;
        entry.held = false;
        entry.regrets = entry.recoveryConfirmations = entry.recoveryTypedUses = 0;
        entry.day = today;
        revision++;
    }

    void confirm(String word, int today) {
        word = SwipeWords.normalise(word);
        if (word.isEmpty() || !SuggestionPolicy.allowed(word)) return;
        Entry entry = words.get(word);
        if (entry == null) {
            entry = create(word, today);
            if (entry == null) return;
        }
        if (entry.held) { recover(entry, false, today); return; }
        int decay = entry.decay(today);
        entry.confirmations = Math.min(16, Math.max(0, entry.confirmations - decay) + 1);
        entry.typedUses = Math.max(0, entry.typedUses - decay);
        if (!entry.held) entry.regrets = Math.max(0, entry.regrets - 1);
        entry.day = today; revision++;
    }

    void typed(String intended, int today, boolean rememberCase) {
        String word = SwipeWords.normalise(intended);
        if (word.isEmpty() || !SuggestionPolicy.allowed(word)) return;
        Entry entry = words.get(word);
        if (entry == null) entry = create(word, today);
        if (entry == null) return;
        String spelling = rememberCase ? intended.replace('\u2019', '\'') : entry.spelling;
        if (!entry.held && entry.typedUses == 16 && entry.day == today && entry.spelling.equals(spelling)) return;
        entry.spelling = spelling;
        if (entry.held) { recover(entry, true, today); return; }
        int decay = entry.decay(today);
        entry.confirmations = Math.max(0, entry.confirmations - decay);
        entry.typedUses = Math.min(16, Math.max(0, entry.typedUses - decay) + 1);
        entry.typed = true; entry.day = today; revision++;
    }

    /** Takes back the last {@link #typed} use when the user deletes the word straight away. */
    void untyped(String intended, int today) {
        Entry entry = words.get(SwipeWords.normalise(intended));
        if (entry == null || entry.added) return;
        if (entry.held) entry.recoveryTypedUses = Math.max(0, entry.recoveryTypedUses - 1);
        else {
            entry.typedUses = Math.max(0, entry.typedUses - 1);
            if (entry.typedUses == 0) entry.typed = false;
            if (entry.faded(today) || entry.confirmations == 0 && !entry.typed) words.remove(entry.word);
        }
        revision++;
    }

    private void recover(Entry entry, boolean manual, int today) {
        int decay = entry.decay(today);
        entry.recoveryConfirmations = Math.max(0, entry.recoveryConfirmations - decay);
        entry.recoveryTypedUses = Math.max(0, entry.recoveryTypedUses - decay);
        if (manual) entry.recoveryTypedUses++; else entry.recoveryConfirmations++;
        entry.day = today;
        if (Math.max(entry.recoveryConfirmations, entry.recoveryTypedUses) >= 3) {
            entry.confirmations = entry.recoveryConfirmations; entry.typedUses = entry.recoveryTypedUses;
            entry.typed = entry.typedUses > 0; entry.held = false; entry.regrets = 0;
            entry.recoveryConfirmations = entry.recoveryTypedUses = 0;
        }
        revision++;
    }

    private Entry create(String word, int today) {
        words.values().removeIf(entry -> entry.faded(today));
        if (words.size() == LIMIT) {
            Entry oldest = words.values().stream().filter(value -> !value.held)
                    .min(Comparator.comparingInt((Entry value) -> value.active(today) ? 1 : 0)
                            .thenComparingInt(value -> value.support(today)).thenComparingInt(value -> value.day)).orElse(null);
            if (oldest == null) return null;
            words.remove(oldest.word);
        }
        Entry entry = new Entry(word, today); words.put(word, entry); return entry;
    }
    void regret(String word) {
        Entry entry = words.get(SwipeWords.normalise(word));
        if (entry == null || entry.held) return;
        if (++entry.regrets >= 2) { entry.held = true; entry.recoveryConfirmations = entry.recoveryTypedUses = 0; }
        revision++;
    }

    void retained(String word, int today) {
        Entry entry = words.get(SwipeWords.normalise(word));
        if (entry == null || !entry.active(today) || entry.day >= today) return;
        int decay = entry.decay(today);
        entry.confirmations = Math.max(0, entry.confirmations - decay);
        entry.typedUses = Math.max(0, entry.typedUses - decay);
        entry.day = today; revision++;
    }
    void remove(String word) { if (words.remove(word) != null) revision++; }
    boolean removeIf(java.util.function.Predicate<String> predicate) {
        if (!words.keySet().removeIf(predicate)) return false;
        revision++;
        return true;
    }
    void merge(LanguageHabits other) {
        for (Entry entry : other.words.values()) {
            if (words.size() == LIMIT) return;
            if (words.putIfAbsent(entry.word, entry) == null) revision++;
        }
    }
    LanguageHabits copy() throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        write(new java.io.DataOutputStream(bytes));
        LanguageHabits copy = new LanguageHabits();
        copy.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        copy.revision = revision;
        return copy;
    }
    List<Entry> entries() { return new ArrayList<>(words.values()); }
    List<Entry> active(int day) { ArrayList<Entry> result = new ArrayList<>(); for (Entry entry : words.values()) if (entry.active(day) && SuggestionPolicy.allowed(entry.word)) result.add(entry); return result; }
    /** The words you saved with Save, in Settings or the word menu: not the ones learned from your typing. */
    java.util.Set<String> addedWords() { java.util.Set<String> result = new java.util.HashSet<>(); for (Entry entry : words.values()) if (entry.added) result.add(entry.word); return result; }
    /**
     * Every word your typing put in the list, which you never saved, even one still gathering evidence: Forget takes any of them
     * out. See {@link #learnedWords(int)} for the ones TitanKeys has learned.
     */
    java.util.Set<String> typedWords() { java.util.Set<String> result = new java.util.HashSet<>(); for (Entry entry : words.values()) if (!entry.added) result.add(entry.word); return result; }
    /**
     * The words TitanKeys learned from your typing, which you never saved: the ones it knows, so it suggests them (owner,
     * 2026-11-13). A word still gathering evidence, such as one typed once, isn't learned yet, nor is one held back. A
     * picked-up typo is learned, until Forget takes it out.
     */
    java.util.Set<String> learnedWords(int day) { java.util.Set<String> result = new java.util.HashSet<>(); for (Entry entry : active(day)) if (!entry.added) result.add(entry.word); return result; }
    java.util.Set<String> activeWords(int day) { java.util.Set<String> result = new java.util.HashSet<>(); for (Entry entry : active(day)) result.add(entry.word); return result; }
    /**
     * Rewrites each entry whose word [lowercaseWord] says the dictionary has in lowercase, and that is kept with capitals, back to
     * lowercase: the one-time repair of words the word menu saved in the case a sentence start, Shift or Caps Lock gave them
     * (owner, 2026-11-13: "And" and "AUTO"). Returns how many it rewrote.
     */
    int lowercase(java.util.function.Predicate<String> lowercaseWord) {
        int changed = 0;
        for (Entry entry : words.values()) {
            if (entry.spelling.equals(entry.word) || !lowercaseWord.test(entry.word)) continue;
            entry.spelling = entry.word;
            changed++;
        }
        if (changed > 0) revision++;
        return changed;
    }
    String restoreCase(String word, int day) {
        Entry entry = words.get(SwipeWords.normalise(word));
        return entry != null && entry.active(day) && !entry.spelling.equals(entry.word) ? entry.spelling : word;
    }
    String status(int day) {
        int active = 0, held = 0;
        for (Entry entry : words.values()) { if (entry.active(day)) active++; if (entry.held) held++; }
        return words.size() + " saved words · " + active + " active · " + (words.size() - active - held) + " gathering evidence · " + held + " held back";
    }
    private java.util.stream.Stream<Entry> shown(int day) {
        return words.values().stream().filter(entry -> entry.shown(day)).sorted(Comparator.comparing(entry -> entry.word));
    }
    String[] descriptions(int day) {
        return shown(day).map(entry ->
                entry.spelling + " · " + (entry.typed ? "manually typed · " : "")
                        + (entry.support(day) > 0 || !entry.typed ? entry.support(day) + " confirmations · " : "")
                        + (entry.held ? "held back" : entry.active(day) ? "active" : "needs 3 confirmations")).toArray(String[]::new);
    }
    String[] swipeStatuses(int day) {
        return shown(day).map(entry -> entry.held ? "Held back" : "").toArray(String[]::new);
    }
    String[] spellings(int day) {
        return shown(day).map(entry -> entry.spelling).toArray(String[]::new);
    }

    org.json.JSONObject config() throws org.json.JSONException {
        org.json.JSONArray entries = new org.json.JSONArray();
        for (Entry entry : words.values()) entries.put(new org.json.JSONObject()
                .put("word", entry.word).put("spelling", entry.spelling)
                .put("confirmations", entry.confirmations).put("typedUses", entry.typedUses)
                .put("regrets", entry.regrets).put("day", entry.day).put("held", entry.held).put("typed", entry.typed)
                .put("added", entry.added)
                .put("recoveryConfirmations", entry.recoveryConfirmations).put("recoveryTypedUses", entry.recoveryTypedUses));
        return new org.json.JSONObject().put("version", 1).put("words", entries);
    }

    static LanguageHabits fromConfig(org.json.JSONObject config) throws org.json.JSONException, IOException {
        if (config.getInt("version") != 1) throw new IOException("Unsupported words config version");
        org.json.JSONArray entries = config.getJSONArray("words");
        if (entries.length() > LIMIT) throw new IOException("Too many saved words");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        DataOutput output = new java.io.DataOutputStream(bytes);
        output.writeInt(6); output.writeInt(entries.length());
        for (int i = 0; i < entries.length(); i++) {
            org.json.JSONObject entry = entries.getJSONObject(i);
            output.writeUTF(entry.getString("word")); output.writeInt(entry.getInt("confirmations"));
            output.writeInt(entry.getInt("regrets")); output.writeInt(entry.getInt("day"));
            output.writeBoolean(entry.getBoolean("held")); output.writeBoolean(entry.getBoolean("typed"));
            output.writeInt(entry.getInt("typedUses")); output.writeInt(entry.getInt("recoveryConfirmations"));
            output.writeInt(entry.getInt("recoveryTypedUses"));
            output.writeUTF(entry.getString(entry.has("spelling") ? "spelling" : "word"));
            output.writeBoolean(entry.optBoolean("added", false));
        }
        LanguageHabits result = new LanguageHabits();
        result.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        return result;
    }

    void write(DataOutput output) throws IOException {
        output.writeInt(6); output.writeInt(words.size());
        for (Entry entry : words.values()) {
            output.writeUTF(entry.word); output.writeInt(entry.confirmations); output.writeInt(entry.regrets);
            output.writeInt(entry.day); output.writeBoolean(entry.held);
            output.writeBoolean(entry.typed);
            output.writeInt(entry.typedUses);
            output.writeInt(entry.recoveryConfirmations); output.writeInt(entry.recoveryTypedUses);
            output.writeUTF(entry.spelling);
            output.writeBoolean(entry.added);
        }
    }
    void read(DataInput input) throws IOException {
        int version = input.readInt();
        if (version < 1 || version > 6) throw new IOException("Personal dictionary version");
        int size = input.readInt(); if (size < 0 || size > LIMIT) throw new IOException("Personal dictionary size");
        LinkedHashMap<String, Entry> loaded = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String word = input.readUTF();
            int count = input.readInt(), regrets = input.readInt(), day = input.readInt(); boolean held = input.readBoolean();
            boolean typed = version >= 2 && input.readBoolean();
            int typedUses = version >= 3 ? input.readInt() : typed ? 1 : 0;
            int recovery = version >= 4 ? input.readInt() : 0, recoveryTyped = version >= 4 ? input.readInt() : 0;
            String spelling = version >= 5 ? input.readUTF() : word;
            boolean added = version >= 6 && input.readBoolean();
            if (word.isEmpty() || !word.equals(SwipeWords.normalise(word)) || !word.equals(SwipeWords.normalise(spelling))
                    || count < 0 || count == 0 && !typed || added && !typed || count > 16
                    || typedUses < 0 || typedUses > 16 || !typed && typedUses != 0
                    || recovery < 0 || recovery > 2 || recoveryTyped < 0 || recoveryTyped > 2 || !held && (recovery != 0 || recoveryTyped != 0)
                    || regrets < 0 || regrets > 2 || day < 0 || day > 1000000 || held != (regrets == 2) || loaded.containsKey(word)) throw new IOException("Personal dictionary entry");
            Entry entry = new Entry(word, day); entry.confirmations = count; entry.regrets = regrets; entry.held = held;
            entry.typed = typed; entry.added = added;
            entry.spelling = spelling;
            entry.typedUses = typedUses; entry.recoveryConfirmations = recovery; entry.recoveryTypedUses = recoveryTyped;
            loaded.put(word, entry);
        }
        words.clear(); words.putAll(loaded); revision++;
    }
}
