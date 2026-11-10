package com.atlas.engine;

import java.util.Set;

/** Holding a word on the strip to forget it (#43): what happens to the word, and the fixes that autocorrect stops making. */
public final class ForgetWordsChecks {
    public static void main(String[] args) {
        check(ForgetWords.kind("the", true, false) == ForgetWords.Kind.BLOCK, "A built-in word must be blocked");
        check(ForgetWords.kind("the", true, true) == ForgetWords.Kind.BLOCK,
                "A built-in word that is also saved must be blocked, since removing it would not stop it");
        check(ForgetWords.kind("Zorbix", false, true) == ForgetWords.Kind.REMOVE, "A learned word must leave Saved words");
        check(ForgetWords.kind("R2D2", false, true) == ForgetWords.Kind.REMOVE,
                "A learned word that Blocked words can't hold must still leave Saved words");
        check(ForgetWords.kind("quokka", false, false) == ForgetWords.Kind.BLOCK, "A word only the swipe list knows must be blocked");
        check(ForgetWords.kind("R2D2", false, false) == ForgetWords.Kind.NONE, "A word that no list can drop must be left alone");
        check(ForgetWords.kind(" ", true, true) == ForgetWords.Kind.NONE, "An empty place must forget nothing");

        check(ForgetWords.fix("teh", "the"), "A word that does not continue the typed letters must count as a fix");
        check(!ForgetWords.fix("th", "the"), "A completion of the typed letters must not count as a fix");
        check(!ForgetWords.fix("Th", "THE"), "A completion in another case must not count as a fix");
        check(!ForgetWords.fix("", "the"), "With no typed letters there is no fix");
        check(ForgetWords.fix("dont", "don’t"), "A fix that adds an apostrophe must count as a fix");

        ForgetWords.RefusedFixes none = new ForgetWords.RefusedFixes(Set.of());
        ForgetWords.RefusedFixes refused = none.add("Teh", "THE");
        check(none.allowed("teh", "the"), "Refusing a fix must not change an earlier snapshot");
        check(!refused.allowed("teh", "the"), "A refused fix must not be made again");
        check(!refused.allowed("TEH", "The"), "A refused fix must ignore case");
        check(refused.allowed("teh", "ten"), "Refusing one fix must leave other fixes for the same letters");
        check(refused.allowed("hte", "the"), "Refusing one fix must leave the same fix for other letters");
        ForgetWords.RefusedFixes reloaded = new ForgetWords.RefusedFixes(refused.entries());
        check(!reloaded.allowed("teh", "the"), "Refused fixes must survive a reload");
        check(new ForgetWords.RefusedFixes(Set.of("broken", "")).entries().isEmpty(), "Damaged entries must be dropped");
        ForgetWords.RefusedFixes two = reloaded.add("hte", "the").add("teh", "ten");
        ForgetWords.RefusedFixes restored = two.withoutFix("The");
        check(restored.allowed("teh", "the") && restored.allowed("hte", "the"),
                "A word given back in Settings must be a fix again for any letters");
        check(!restored.allowed("teh", "ten"), "Giving back one word must keep the refusals of other words");
        check(two.add("teh", "the") == two, "Refusing a fix twice must keep one entry");
        ForgetWords.RefusedFixes full = none;
        for (int i = 0; i < ForgetWords.RefusedFixes.LIMIT + 20; i++) full = full.add("typo" + i, "word" + i);
        check(full.entries().size() == ForgetWords.RefusedFixes.LIMIT, "Refused fixes must stay within their limit");
        check(!full.add("teh", "the").allowed("teh", "the"), "A full list must still take a new refusal");
        System.out.println("Forget words checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
