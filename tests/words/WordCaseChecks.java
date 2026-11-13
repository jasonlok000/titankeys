package com.atlas.engine;

/**
 * The case a word is kept in (owner, 2026-11-13: "why is And kept on auto correcting to cap? And AUTO too?"), and the one-time
 * repair of words kept with capitals that weren't their own.
 */
public final class WordCaseChecks {
    public static void main(String[] args) {
        same(WordCase.kept("And", "", false), "and", "\"And\" at the start of the text is kept as \"and\"");
        same(WordCase.kept("And", "It works. ", false), "and", "and after a full stop");
        same(WordCase.kept("And", "it works and ", true), "and", "and with Shift on for it");
        same(WordCase.kept("AUTO", "set it to ", false), "auto", "All capitals are Caps Lock's, not the word's");
        same(WordCase.kept("Jason", "I met ", false), "Jason", "A name in the middle of a sentence keeps its capital");
        same(WordCase.kept("iPhone", "", false), "iPhone", "\"iPhone\" keeps its own capitals, even at a sentence start");
        same(WordCase.kept("WhatsApp", "Send it on ", false), "WhatsApp", "and so does \"WhatsApp\"");
        same(WordCase.kept("zorbly", "", false), "zorbly", "A lowercase word stays lowercase");
        same(WordCase.kept("Don't", "", false), "don't", "An apostrophe changes nothing");
        same(WordCase.kept("I", "so ", false), "I", "One capital letter alone isn't all capitals");
        same(WordCase.kept("Zorbly", null, false), "zorbly", "With nothing known before it, a first capital counts as the start's");

        // The repair: saved and learned words that the dictionary has in lowercase go back to lowercase. Others keep their case.
        int day = 20000;
        LanguageHabits words = new LanguageHabits();
        words.add("And", day);
        words.add("AUTO", day);
        words.add("Jason", day);
        words.add("iPhone", day);
        words.typed("Zorbly", day, true);
        words.typed("Zorbly", day, true);
        java.util.Set<String> lowercaseDictionary = java.util.Set.of("and", "auto");
        same(words.lowercase(lowercaseDictionary::contains), 2, "Two words are repaired");
        same(words.restoreCase("and", day), "and", "\"and\" is kept in lowercase again");
        same(words.restoreCase("auto", day), "auto", "and so is \"auto\"");
        same(words.restoreCase("jason", day), "Jason", "\"Jason\" keeps its capital");
        same(words.restoreCase("iphone", day), "iPhone", "\"iPhone\" keeps its own");
        same(words.restoreCase("zorbly", day), "Zorbly", "A word the dictionary lacks keeps the case it was learned in");
        same(words.lowercase(lowercaseDictionary::contains), 0, "A second repair finds nothing");
        System.out.println("Word case checks passed");
    }

    private static void same(Object actual, Object expected, String message) {
        if (!java.util.Objects.equals(actual, expected)) throw new AssertionError(message + "\n  expected: " + expected + "\n  actual:   " + actual);
    }
}
