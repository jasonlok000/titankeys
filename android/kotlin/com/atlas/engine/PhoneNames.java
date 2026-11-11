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
    /** Whole words in any letters, so "Zoë" stays one word and is left out, rather than becoming "Zo". */
    private static final Pattern WORD = Pattern.compile("\\p{L}+(?:['’]\\p{L}+)*");

    /**
     * The words of these names, by their lowercase form, each with its spelling: "WhatsApp" for "whatsapp". Words of one
     * letter and words that cannot be swiped are left out. A word that the base list has ({@code baseFrequency} above 0)
     * is left out too, as "Maps", "Clock" or "Toggle" are, unless it is a brand: written with capitals here, and either
     * with a capital inside, as "WhatsApp", or only ever written with a capital in the base list, as "Spotify" ({@code
     * lowercase} says whether the base list has the word in lowercase). An ordinary word never takes a name's capitals,
     * however rare it is. A brand only gives its capitals, since the engines already have it. A word written two ways
     * keeps the first one that has capitals.
     */
    public static Map<String, String> words(Iterable<String> names, ToDoubleFunction<String> baseFrequency,
            java.util.function.Predicate<String> lowercase) {
        Map<String, String> words = new HashMap<>();
        for (String name : names) {
            if (name == null) continue;
            Matcher matcher = WORD.matcher(name);
            while (matcher.find()) {
                String spelling = matcher.group().replace('’', '\'');
                String key = SwipeWords.normalise(spelling);
                if (key.isEmpty()) continue;
                double frequency = baseFrequency.applyAsDouble(key);
                if (frequency > 0 && !brand(spelling, lowercase.test(key))) continue;
                String known = words.get(key);
                if (known == null || known.equals(key) && !spelling.equals(key)) words.put(key, spelling);
            }
        }
        return words;
    }

    /** A name written with capitals, with one inside it or that the base list never writes in lowercase. */
    static boolean brand(String spelling, boolean lowercaseInBase) {
        if (spelling.equals(spelling.toLowerCase(java.util.Locale.ROOT))) return false;
        boolean inside = false;
        for (int i = 1; i < spelling.length(); i++) inside |= Character.isUpperCase(spelling.charAt(i));
        return inside || !lowercaseInBase;
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
