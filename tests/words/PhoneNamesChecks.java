package com.atlas.engine;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Names on this phone: which words of app and contact names the keyboard takes, and with which capitals. */
public final class PhoneNamesChecks {
    public static void main(String[] args) {
        Set<String> base = Set.of("maps", "clock", "google", "messages", "the", "jason");
        Map<String, String> apps = PhoneNames.words(List.of("WhatsApp", "Google Maps", "Clock", "Signal Private Messenger",
                "Pokémon GO", "X", "Stack Overflow 2", "Bank of the West"), base::contains);
        same(apps.get("whatsapp"), "WhatsApp", "An app's name keeps its capitals");
        check(!apps.containsKey("maps") && !apps.containsKey("google") && !apps.containsKey("clock"),
                "Words the base list has are left out: " + apps);
        check(!apps.containsKey("x"), "One letter is no word");
        check(!apps.containsKey("pok") && !apps.containsKey("pokémon"), "A word with letters a swipe cannot type is left out whole");
        same(apps.get("go"), "GO", "Each word of a name counts");
        same(apps.get("stack"), "Stack", "Digits split words and are left out");
        same(apps.get("signal"), "Signal", "Every word of a longer name counts");

        Map<String, String> contacts = PhoneNames.words(List.of("Jason", "Lok", "Zoë", "O’Brien", "lok", "de la Cruz"), base::contains);
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
        System.out.println("Phone name checks passed");
    }

    private static void same(Object actual, Object expected, String message) {
        check(expected.equals(actual), message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
