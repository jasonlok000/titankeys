package com.atlas.engine;

import java.util.Map;

/**
 * Words that TitanKeys knows from the start, though the word lists do not have them, such as its own name. Swipes and typing
 * find them with learning on or off, and they keep their spelling.
 */
final class BuiltInWords {
    /** Each word in lowercase, with its spelling. */
    static final Map<String, String> SPELLINGS = Map.of("titankeys", "TitanKeys");
    /** How common the words are, from 0 to 255: enough for a swipe to find them, not so much that they push out usual words. */
    static final float FREQUENCY = 120f;

    /** The spelling of a built-in word in lowercase, or null for any other word. */
    static String spelling(String word) { return SPELLINGS.get(word); }

    private BuiltInWords() {}
}
