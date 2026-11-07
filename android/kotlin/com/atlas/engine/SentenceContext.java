package com.atlas.engine;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SentenceContext {
    public static final int MAX_TEXT_LENGTH = 2048;
    private static final Pattern WORD = Pattern.compile("[a-zA-Z]+(?:'[a-zA-Z]+)?");

    static String[] words(String text) {
        ArrayList<String> words = new ArrayList<>();
        int start = Math.max(0, text.length() - MAX_TEXT_LENGTH);
        for (int i = text.length() - 1; i >= start; i--) {
            char c = text.charAt(i);
            if (".!?\r\n\u2028\u2029\u3002\uff01\uff1f".indexOf(c) < 0) continue;
            if (c == '.' && i > 0 && i + 1 < text.length()
                    && Character.isLetterOrDigit(text.charAt(i - 1))
                    && Character.isLetterOrDigit(text.charAt(i + 1))) continue;
            start = i + 1;
            break;
        }
        Matcher matcher = WORD.matcher(text.substring(start).replace('\u2019', '\''));
        while (matcher.find()) {
            words.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return words.toArray(new String[0]);
    }

    private SentenceContext() {}
}
