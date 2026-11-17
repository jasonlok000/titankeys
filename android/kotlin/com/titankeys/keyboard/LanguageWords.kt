package com.titankeys.keyboard

import com.titankeys.engine.Swipe
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The words the bar and Composer use: English's own while English is the input method, and the current input method's word
 * list otherwise. Other input methods have no swiping, no next word and learn nothing into English's words: they complete the
 * word being typed from their list (docs/languages.md, "Accents and the picker on the bar"), and those with a list offer the
 * likeliest word for a slip, which Space puts in only when sure (LanguageCorrections).
 */
class LanguageWords(private val english: Words, private val current: () -> WordList?) : Words {
    /** True while another input method than English is typing, with or without a word list. */
    var other = false
    /** True while it writes no spaces between words (Thai): the word being typed is found in its list ([WordBreak]). */
    var wordBreak = false
    /** Whether an apostrophe can be part of a word, as in French's l'eau: the input method's rule. */
    var apostrophes = true
    /**
     * The likeliest word for a slip typed in another input method, given the word and the text before it, or null: it shows
     * beside the completions, even where Space won't put it in (LanguageCorrections).
     */
    var suggest: (String, String) -> String? = { _, _ -> null }

    private fun list() = if (other) current() else null

    override fun recognise(swipe: Swipe, context: String, personalise: Boolean, result: (Words.Recognition) -> Unit) =
        if (other) result(Words.Recognition(emptyList(), null)) else english.recognise(swipe, context, personalise, result)

    override fun complete(before: String, cancelled: AtomicBoolean, sentenceCapitalisation: Boolean, result: (List<String>) -> Unit) {
        if (!other) return english.complete(before, cancelled, sentenceCapitalisation, result)
        val prefix = prefix(before)
        val completions = list()?.complete(prefix).orEmpty()
        // A word no completion explains may be a slip: its likeliest word goes first among the others.
        val slip = if (prefix.isEmpty() || completions.any { it.equals(prefix, ignoreCase = true) }) null
            else suggest(prefix, before.dropLast(prefix.length))
        result((listOfNotNull(slip) + completions).distinctBy { it.lowercase() })
    }

    override fun prefix(before: String): String {
        if (!other) return english.prefix(before)
        val run = lastWord(before, apostrophes)
        val list = list()
        return if (wordBreak && list != null) WordBreak.last(run, list::has, list::hasPrefix) else run
    }

    override fun prepare(text: String, cancelled: AtomicBoolean) { if (!other) english.prepare(text, cancelled) }
    override fun lookAlike(context: String, word: String, next: String, result: (String?) -> Unit) =
        if (other) result(null) else english.lookAlike(context, word, next, result)
    /** Only the input method's own words show on the bar: English's sentence starts don't, for one. */
    override fun allowed(word: String) = if (other) list()?.contains(word) == true else english.allowed(word)
    /** Another input method's fix: only its corrector makes one, and only when sure (LanguageCorrections). */
    override fun fixAllowed(typed: String, fix: String) = if (other) true else english.fixAllowed(typed, fix)
    /** Whether [word] is in English's own list, whatever input method types: Chinese offers it as a choice ([EnglishOffer]). */
    fun englishKnown(word: String) = english.known(word)

    override fun known(word: String) = if (other) list()?.contains(word) == true else english.known(word)
    override fun sentenceStarts(): List<String> = if (other) emptyList() else english.sentenceStarts()
    override fun typed(word: String, rememberCase: Boolean) { if (!other) english.typed(word, rememberCase) }
    override fun untyped(word: String) { if (!other) english.untyped(word) }
    override fun keptPair(previous: String?, next: String) { if (!other) english.keptPair(previous, next) }
    override fun chosen(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) {
        if (!other) english.chosen(swipe, context, recognition, word)
    }
    override fun corrected(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) {
        if (!other) english.corrected(swipe, context, recognition, word)
    }
    override fun kept(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) {
        if (!other) english.kept(swipe, context, recognition, word)
    }
    override fun keptAtEnd(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) {
        if (!other) english.keptAtEnd(swipe, context, recognition, word)
    }
    override fun background(work: () -> String?, result: (String?) -> Unit) = english.background(work, result)

    companion object {
        /** The word the cursor is in: the letters (and apostrophes inside a word) at the end of [before]. */
        fun lastWord(before: String, apostrophes: Boolean = true): String {
            var start = before.length
            while (start > 0 && (before[start - 1].isLetter() || apostrophes && (before[start - 1] == '\'' || before[start - 1] == '’') ||
                    Character.getType(before[start - 1]) == Character.NON_SPACING_MARK.toInt() ||
                    Character.getType(before[start - 1]) == Character.COMBINING_SPACING_MARK.toInt())) start--
            return before.substring(start).trimStart('\'', '’')
        }
    }
}
