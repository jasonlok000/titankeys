package com.titankeys.keyboard

import android.icu.text.BreakIterator
import android.icu.util.ULocale
import java.util.Locale

/**
 * ICU's word breaks, as Android ships them: dictionaries for Chinese, Japanese, Thai, Lao, Khmer and Burmese ([WordBreaks]).
 * Java's own BreakIterator has no Chinese dictionary and takes a whole run of Chinese as one word, so it can't stand in.
 */
internal object IcuWords {
    private var locale: Locale? = null
    private var breaker: WordBreaker? = null

    /** The word breaks for [locale], the current input method's: the same breaker while the input method stays. */
    @Synchronized
    fun of(locale: Locale): WordBreaker {
        breaker?.takeIf { this.locale == locale }?.let { return it }
        val made = WordBreaker { text ->
            val words = BreakIterator.getWordInstance(ULocale.forLocale(locale))
            words.setText(text)
            val pieces = ArrayList<WordPiece>()
            var start = words.first()
            var end = words.next()
            while (end != BreakIterator.DONE) {
                pieces += WordPiece(start, end, words.ruleStatus != BreakIterator.WORD_NONE)
                start = end
                end = words.next()
            }
            pieces
        }
        this.locale = locale
        breaker = made
        return made
    }
}
