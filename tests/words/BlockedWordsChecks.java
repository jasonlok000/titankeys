package com.atlas.engine;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class BlockedWordsChecks {
    public static void main(String[] args) throws Exception {
        BlockedWords empty = new BlockedWords(Set.of());
        BlockedWords blocked = empty.add(" Apple ").add("APPLE").add("O’Brien").add("i");
        check(empty.allowed("apple"), "Adding a block must not change an in-flight snapshot");
        check(Arrays.equals(blocked.words(), new String[] { "apple", "i", "o'brien" }),
                "Blocks must ignore case, trim spaces and avoid duplicates");
        check(Arrays.equals(blocked.filter(new String[] { "Apple", "APPLE", "O'BRIEN", "I", "apricot" }),
                new String[] { "apricot" }), "Filtering must keep only unblocked predictions");
        BlockedWords restored = new BlockedWords(blocked.entries());
        check(!restored.allowed("O’Brien"), "Reloading stored entries must preserve apostrophe matching");
        check(restored.remove("APPLE").allowed("Apple"), "Removing a block must allow that word again");
        check(!restored.allowed("Apple"), "Removing a block must not alter an earlier snapshot");
        for (String invalid : new String[] { "", "two words", "word1", "abcdefghijklmnopqrstuvwxyz" }) {
            try {
                restored.add(invalid);
                throw new AssertionError("Accepted invalid blocked word: " + invalid);
            } catch (IllegalArgumentException expected) {
                check(restored.words().length == 3, "Invalid input must keep existing blocks");
            }
        }

        String[] highRanked = { "apple", "application", "applicant", "apply", "appraise", "approve", "approach", "apparatus" };
        TypedPredictions predictions = new TypedPredictions(new StringReader(String.join("\n", highRanked) + "\napricot\n"),
                word -> word.equals("apricot") ? 1 : 100);
        BlockedWords allTopWords = new BlockedWords(List.of(highRanked));
        check(Arrays.equals(predictions.suggest("ap", List.of(), 20000, allTopWords::allowed), new String[] { "apricot" }),
                "Blocked top matches must leave room for lower-ranked allowed completions");
        LanguageHabits personal = new LanguageHabits();
        personal.add("Appville", 20000);
        BlockedWords personalBlock = allTopWords.add("APPVILLE");
        check(predictions.suggest("app", personal.active(20000), 20000, personalBlock::allowed).length == 0,
                "A learned word must remain blocked despite its personal frequency boost");
        System.out.println("Blocked word checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
