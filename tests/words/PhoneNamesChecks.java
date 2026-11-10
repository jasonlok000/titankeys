package com.atlas.engine;

import java.util.List;
import java.util.Map;

/** Names on this phone: which words of app and contact names the keyboard takes, and with which capitals. */
public final class PhoneNamesChecks {
    public static void main(String[] args) {
        // As the base list has them, from english.combined.
        Map<String, Double> frequencies = new java.util.HashMap<>();
        String[] words = {"maps", "clock", "google", "messages", "the", "jason", "whatsapp", "spotify", "chrome", "youtube", "music"};
        double[] values = {110, 123, 101, 121, 255, 112, 25, 25, 98, 109, 140};
        for (int i = 0; i < words.length; i++) frequencies.put(words[i], values[i]);
        java.util.function.ToDoubleFunction<String> base = word -> frequencies.getOrDefault(word, 0d);
        Map<String, String> apps = PhoneNames.words(List.of("WhatsApp", "Google Maps", "Clock", "Signal Private Messenger",
                "Pokémon GO", "X", "Stack Overflow 2", "Bank of the West"), base);
        same(apps.get("whatsapp"), "WhatsApp", "An app's name keeps its capitals");
        check(!apps.containsKey("maps") && !apps.containsKey("google") && !apps.containsKey("clock"),
                "Words the base list has are left out: " + apps);
        check(!apps.containsKey("x"), "One letter is no word");
        check(!apps.containsKey("pok") && !apps.containsKey("pokémon"), "A word with letters a swipe cannot type is left out whole");
        same(apps.get("go"), "GO", "Each word of a name counts");
        same(apps.get("stack"), "Stack", "Digits split words and are left out");
        same(apps.get("signal"), "Signal", "Every word of a longer name counts");

        Map<String, String> contacts = PhoneNames.words(List.of("Jason", "Lok", "Zoë", "O’Brien", "lok", "de la Cruz"), base);
        same(contacts.get("lok"), "Lok", "A name written two ways keeps the one with capitals");
        same(contacts.get("o'brien"), "O'Brien", "An apostrophe stays in a name");
        check(!contacts.containsKey("jason"), "A name the base list has needs nothing more");
        check(!contacts.containsKey("zo") && !contacts.containsKey("zoë"), "Zoë stays one word and is left out, not \"Zo\"");
        same(contacts.get("cruz"), "Cruz", "A family name of several words gives each word");

        Map<String, String> both = PhoneNames.merged(Map.of("signal", "Signal", "go", "GO"), Map.of("go", "Go"));
        same(both.get("go"), "Go", "A contact's spelling wins over an app's");
        same(both.get("signal"), "Signal", "Both sources are kept");
        same(PhoneNames.merged(Map.of("signal", "Signal"), Map.of()).size(), 1, "With contacts off, only the apps are left");
        same(PhoneNames.order(Map.of("lok", "Lok", "go", "Go"), Map.of("signal", "Signal", "go", "GO", "arc", "Arc")),
                List.of("go", "lok", "arc", "signal"), "Contacts' names go to the engines first, then apps', each alphabetical");
        // A brand that the base list has as a rare word still gives its capitals; ordinary words never do.
        Map<String, String> brands = PhoneNames.words(List.of("WhatsApp", "Spotify", "Chrome", "Messages", "Clock", "YouTube Music"), base);
        same(brands.get("whatsapp"), "WhatsApp", "A brand's capitals, though the base list has the word");
        same(brands.get("spotify"), "Spotify", "A rare base-list word written with a capital is a brand");
        check(!brands.containsKey("chrome") && !brands.containsKey("messages") && !brands.containsKey("clock"),
                "Common words in an app's name never take capitals: " + brands);
        same(brands.get("youtube"), "YouTube", "A capital inside a word marks a brand however common it is");
        check(!brands.containsKey("music"), "An ordinary word beside a brand stays ordinary");
        check(!PhoneNames.words(List.of("whatsapp"), base).containsKey("whatsapp"), "A label in lowercase gives no capitals");
        System.out.println("Phone name checks passed");
    }

    private static void same(Object actual, Object expected, String message) {
        check(expected.equals(actual), message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
