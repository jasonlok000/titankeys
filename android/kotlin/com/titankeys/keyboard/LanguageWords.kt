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
    /** The current input method's starters ([InputMethod.starters], in Simplified for that Chinese variant). */
    var starters: List<String> = emptyList()
    /** Whether a sentence start takes a capital in the current input method. */
    var capitals = true
    /** Blocked words and Favourites, shared by every language ([WordRules]). */
    var rules: WordRules = WordRules.NONE

    /** The input method typing now, for [WordNoise]. */
    var id = ""

    /** [rules], and the input method's fragments ([WordNoise]), for everything the bar offers and every fix. */
    private val offered: WordRules get() {
        val rules = rules
        val id = id
        return WordRules({ rules.blocked(it) || WordNoise.fragment(id, it) && learning?.knows(it) != true },
            rules.favourite)
    }

    /** A next word from the table worth offering: no fragment, and common enough where the language has a floor. */
    private fun nextWord(word: String): Boolean {
        val list = list()
        return WordNoise.nextWord(id, word) { list?.frequency(it) ?: Int.MAX_VALUE }
    }

    /** Whether [word] may be offered or fixed to in the input method typing now: not blocked, nor unsaved in it. D's fixes ask. */
    fun usable(word: String) = offered.usable(word) && learning?.isHidden(word) != true

    /** Whether [word] is a favourite: first among similar words, in every language. */
    fun favourite(word: String) = rules.favourite(word)

    /** Whether the input method typing now has [word] in its own list or starters, for the word menu. */
    fun listed(word: String) = list()?.contains(word) == true || word in starters

    /**
     * How many words the bar offers: three, or as many as Chinese's and Japanese's wide row fits, where the engine shows them
     * ([InputEngine.offer]).
     */
    var shown: () -> Int = { BarWords.SHOWN }

    /** The current input method's next-word table, where it has one ([NextWords]). */
    var nextWords: NextWords? = null
    /** What the current input method learned from the user, or null where learning is off. */
    var learning: LanguageLearning? = null
    /** Called after [learning] changed, to keep it. */
    var learned: () -> Unit = {}
    /** Whether new words may be learned: Learn new words I type, English's switch too. What was learned still ranks. */
    var mayLearn: () -> Boolean = { true }

    private fun list() = if (other) current() else null

    override fun recognise(swipe: Swipe, context: String, personalise: Boolean, result: (Words.Recognition) -> Unit) =
        if (other) result(Words.Recognition(emptyList(), null)) else english.recognise(swipe, context, personalise, result)

    override fun complete(before: String, cancelled: AtomicBoolean, sentenceCapitalisation: Boolean, result: (List<String>) -> Unit) {
        if (!other) return english.complete(before, cancelled, sentenceCapitalisation, result)
        val prefix = prefix(before)
        // A word ended: the next words, or the starters at a sentence's start (owner, 2026-11-16: "I dont like the bar being
        // empty"). The field's own rules still keep the bar empty in passwords and the other fields without words.
        if (prefix.isEmpty()) {
            val start = com.titankeys.engine.SwipeTyping.startsSentence(before)
            val previous = if (start) null else previousWord(before.trimEnd())
            result(BarWords.next(previous, learning, starters, capitals && sentenceCapitalisation, nextWords, offered,
                spelling = { word -> list()?.spelling(word) ?: word }, shown = shown(), nextWord = ::nextWord))
            return
        }
        val completions = BarWords.complete(prefix, learning, list()?.complete(prefix).orEmpty(), offered, shown())
        // A word no completion explains may be a slip: its likeliest word goes first among the others (LanguageCorrections).
        val slip = if (completions.any { it.equals(prefix, ignoreCase = true) }) null
            else suggest(prefix, before.dropLast(prefix.length))?.takeIf(::usable)
        result((listOfNotNull(slip) + completions).distinctBy { it.lowercase() })
    }

    override fun prefix(before: String): String {
        if (!other) return english.prefix(before)
        val run = lastWord(before, apostrophes)
        val list = list()
        return if (wordBreak && list != null) WordBreak.last(run, { WordBreak.word(it, list::has) }, list::hasPrefix) else run
    }

    /**
     * The word a word that just ended follows, for its next words: the last run of letters, or in Thai, which has no spaces
     * between words, that run's last word ([WordBreak.lastWord]); null where there's none.
     */
    fun previousWord(before: String): String? {
        val run = lastWord(before, apostrophes).takeIf { it.isNotEmpty() } ?: return null
        val list = list()
        return if (wordBreak && list != null) WordBreak.lastWord(run, { WordBreak.word(it, list::has) }) else run
    }

    override fun prepare(text: String, cancelled: AtomicBoolean) { if (!other) english.prepare(text, cancelled) }
    override fun lookAlike(context: String, word: String, next: String, result: (String?) -> Unit) =
        if (other) result(null) else english.lookAlike(context, word, next, result)
    /** Only the input method's own words show on the bar: English's sentence starts don't, for one. */
    override fun allowed(word: String) =
        if (other) usable(word) && (list()?.contains(word) == true || word in starters || learning?.knows(word) == true ||
            nextWords?.after(null)?.contains(word) == true) else english.allowed(word)
    /** Another input method's fix: only its corrector makes one, and only when sure (LanguageCorrections). */
    override fun fixAllowed(typed: String, fix: String) = if (other) true else english.fixAllowed(typed, fix)
    /** Whether [word] is in English's own list, whatever input method types: Chinese offers it as a choice ([EnglishOffer]). */
    fun englishKnown(word: String) = english.known(word)

    override fun known(word: String) =
        if (other) list()?.contains(word) == true || word in starters || learning?.knows(word) == true else english.known(word)
    override fun sentenceStarts(): List<String> =
        if (other) BarWords.next(null, learning, starters, capitals = false, table = nextWords, rules = offered,
            spelling = { word -> list()?.spelling(word) ?: word }) else english.sentenceStarts()
    override fun typed(word: String, rememberCase: Boolean) { if (!other) english.typed(word, rememberCase) }
    override fun untyped(word: String) {
        if (!other) return english.untyped(word)
        val learning = learning ?: return
        learning.forget(word)
        learned()
    }
    /** Every word kept, with the one before it: what another input method learns from (Composer only calls it where it may). */
    override fun keptPair(previous: String?, next: String) {
        if (!other) return english.keptPair(previous, next)
        val learning = learning?.takeIf { mayLearn() } ?: return
        val list = list()
        if (wordBreak && list != null) {
            // Thai: what's kept between two spaces is a phrase, so each of its words is learned after the one before it, and
            // a stray letter breaks the chain. Only the list's words: a phrase is never learned as one word.
            val isWord = { piece: String -> WordBreak.word(piece, list::has) }
            var before = previous?.let { WordBreak.lastWord(it, isWord) }
            // A sentence's start only where the whole phrase starts one; after a stray letter, a word alone.
            var paired = previous == null || before != null
            for (piece in WordBreak.words(next, isWord)) {
                if (piece != null) learning.kept(before, piece, known = true, paired = paired)
                before = piece
                paired = piece != null
            }
        } else learning.kept(previous, next, known = list?.contains(next) == true || next in starters)
        learned()
    }
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
