package com.atlas.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
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
     * letter, words in the base list (which already has them, as "Maps" or "Clock"), and words that cannot be swiped are
     * left out. A word written two ways keeps the first one that has capitals.
     */
    public static Map<String, String> words(Iterable<String> names, Predicate<String> inBaseList) {
        Map<String, String> words = new HashMap<>();
        for (String name : names) {
            if (name == null) continue;
            Matcher matcher = WORD.matcher(name);
            while (matcher.find()) {
                String spelling = matcher.group().replace('’', '\'');
                String key = SwipeWords.normalise(spelling);
                if (key.isEmpty() || inBaseList.test(key)) continue;
                String known = words.get(key);
                if (known == null || known.equals(key) && !spelling.equals(key)) words.put(key, spelling);
            }
        }
        return words;
    }

    /** One map of both sources. A contact's spelling wins over an app's, as a person's name is the likelier meaning. */
    public static Map<String, String> merged(Map<String, String> apps, Map<String, String> contacts) {
        Map<String, String> all = new HashMap<>(apps);
        all.putAll(contacts);
        return all;
    }

    private PhoneNames() {}
}
