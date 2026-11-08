package com.atlas.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Arrays;

public final class SavedWordsChecks {
    public static void main(String[] args) throws Exception {
        int day = 20000;
        LanguageHabits words = new LanguageHabits();
        words.add("Atlas", day);
        check(words.active(day).size() == 1, "An added word must be active immediately");
        check(words.restoreCase("atlas", day).equals("Atlas"), "Keep the entered spelling");
        words.add("ATLAS", day);
        check(words.entries().size() == 1, "Adding another spelling must not duplicate a word");
        words.regret("atlas");
        words.regret("atlas");
        check(words.active(day).isEmpty(), "The word must be held before testing reactivation");
        words.add("Atlas", day + 28);
        check(words.active(day + 28).size() == 1, "Explicitly adding a held word must reactivate it");
        words.add("O’Brien", day + 28);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        words.write(new DataOutputStream(bytes));
        LanguageHabits restored = new LanguageHabits();
        restored.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        check(Arrays.equals(restored.spellings(), new String[] { "Atlas", "O'Brien" }),
                "Added spellings and apostrophes must survive a reload");
        check(restored.active(day + 28).size() == 2, "Added words must remain active after a reload");
        for (String invalid : new String[] { "", "a", "two words", "word1", "abcdefghijklmnopqrstuvwxyz" }) {
            try {
                restored.add(invalid, day + 28);
                throw new AssertionError("Accepted invalid word: " + invalid);
            } catch (IllegalArgumentException expected) {
                check(restored.entries().size() == 2, "Invalid input must leave saved words unchanged");
            }
        }
        restored.remove("atlas");
        check(Arrays.equals(restored.spellings(), new String[] { "O'Brien" }), "Added words must be removable");
        System.out.println("Saved word checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
