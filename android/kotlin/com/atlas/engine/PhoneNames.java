package com.atlas.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Names on this phone: the words of the installed apps' names and, when the user turns it on, of their contacts' given and
 * family names. Swipes and typing find them as they find saved words, with their own capitals. They are never learned,
 * saved, logged or sent, and a blocked word stays blocked.
 */
public final class PhoneNames {
    /** As common as a word that the user has just saved, so a name competes without pushing out usual words. */
    static final float FREQUENCY = 104f;
    /**
     * A base-list word rarer than this, written with capitals in a name, is a brand: "WhatsApp" and "Spotify" (25 each)
     * take their capitals, while "Chrome" (98), "Messages" (121) and "Clock" (123) are ordinary words and stay as typed.
     */
    static final float BRAND_FREQUENCY = 80f;
    /** Whole words in any letters, so "Zoë" stays one word and is left out, rather than becoming "Zo". */
    private static final Pattern WORD = Pattern.compile("\\p{L}+(?:['’]\\p{L}+)*");

    /**
     * The words of these names, by their lowercase form, each with its spelling: "WhatsApp" for "whatsapp". Words of one
     * letter and words that cannot be swiped are left out. A word that the base list has ({@code baseFrequency} above 0)
     * is left out too, as "Maps" or "Clock" are, unless it is a brand there: written with capitals here, and either rare
     * in the base list or with a capital inside, as "WhatsApp". Such a word only gives its capitals, since the engines
     * already have it. A word written two ways keeps the first one that has capitals.
     */
    public static Map<String, String> words(Iterable<String> names, ToDoubleFunction<String> baseFrequency) {
        Map<String, String> words = new HashMap<>();
        for (String name : names) {
            if (name == null) continue;
            Matcher matcher = WORD.matcher(name);
            while (matcher.find()) {
                String spelling = matcher.group().replace('’', '\'');
                String key = SwipeWords.normalise(spelling);
                if (key.isEmpty()) continue;
                double frequency = baseFrequency.applyAsDouble(key);
                if (frequency > 0 && !brand(spelling, frequency)) continue;
                String known = words.get(key);
                if (known == null || known.equals(key) && !spelling.equals(key)) words.put(key, spelling);
            }
        }
        return words;
    }

    /** A name written with capitals that the base list has only as a rare word, or one with a capital inside. */
    static boolean brand(String spelling, double baseFrequency) {
        if (spelling.equals(spelling.toLowerCase(java.util.Locale.ROOT))) return false;
        boolean inside = false;
        for (int i = 1; i < spelling.length(); i++) inside |= Character.isUpperCase(spelling.charAt(i));
        return inside || baseFrequency < BRAND_FREQUENCY;
    }

    /** One map of both sources. A contact's spelling wins over an app's, as a person's name is the likelier meaning. */
    public static Map<String, String> merged(Map<String, String> apps, Map<String, String> contacts) {
        Map<String, String> all = new HashMap<>(apps);
        all.putAll(contacts);
        return all;
    }

    /**
     * The order in which names go to the swipe engines while their personal list has room: contacts' names first, as a
     * person's name is the likelier to be typed, then apps'. Each part is alphabetical, so the order never depends on how
     * Android listed them.
     */
    public static List<String> order(Map<String, String> contacts, Map<String, String> apps) {
        List<String> order = new ArrayList<>(new TreeSet<>(contacts.keySet()));
        for (String word : new TreeSet<>(apps.keySet())) if (!contacts.containsKey(word)) order.add(word);
        return List.copyOf(order);
    }

    private PhoneNames() {}
}
