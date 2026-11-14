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

        // The text before the held word (bug hunt #11): a swiped or spaced word is already in the text, so it's what comes before it.
        same(WordCase.kept("Yeet", WordCase.before("Yeet", "Yeet", 0), false), "yeet", "#11: a swiped \"Yeet\" at a field's start is kept as \"yeet\"");
        same(WordCase.kept("Yeet", WordCase.before("Yeet ", "Yeet", 4), false), "yeet", "#11: and one typed and followed by Space");
        same(WordCase.kept("Yeet", WordCase.before("Done. Yeet ", "Yeet", 0), false), "yeet", "#11: after a full stop too");
        same(WordCase.kept("Jason", WordCase.before("I met Jason", "Jason", 0), false), "Jason", "A swiped name mid-sentence keeps its capital");
        same(WordCase.before("Hello wor", "World", 3), "Hello ", "A word still being typed: its typed letters come off");
        same(WordCase.before("Hi ", "Yeet", 0), "Hi ", "A suggestion not in the text: all of the text is before it");
        same(WordCase.before(null, "Yeet", 0), null, "Nothing known stays nothing known");
        // What the keyboard did before: the typed letters only, so a word already in the text ended "before", no sentence start.
        same(WordCase.kept("Yeet", "Yeet ".substring(0, 5), false), "Yeet", "#11 as it was: the word itself counted as the text before it");

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
