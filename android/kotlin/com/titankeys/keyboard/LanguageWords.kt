package com.titankeys.keyboard

import com.titankeys.engine.Swipe
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The words the bar and Composer use: English's own while English is the input method, and the current input method's word
 * list otherwise. Other input methods have no swiping, no next word and learn nothing into English's words: they only complete
 * the word being typed from their list (docs/languages.md, "Accents and the picker on the bar").
 */
class LanguageWords(private val english: Words, private val current: () -> WordList?) : Words {
    /** True while another input method than English is typing, with or without a word list. */
    var other = false
    /** True while it writes no spaces between words (Thai): the word being typed is found in its list ([WordBreak]). */
    var wordBreak = false
    /** The current input method's starters ([InputMethod.starters], in Simplified for that Chinese variant). */
    var starters: List<String> = emptyList()
    /** Whether a sentence start takes a capital in the current input method. */
    var capitals = true
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
            val previous = if (start) null else lastWord(before.trimEnd()).takeIf { it.isNotEmpty() }
            result(BarWords.next(previous, learning, starters, capitals && sentenceCapitalisation, nextWords))
            return
        }
        result(BarWords.complete(prefix, learning, list()?.complete(prefix).orEmpty()))
    }

    override fun prefix(before: String): String {
        if (!other) return english.prefix(before)
        val run = lastWord(before)
        val list = list()
        return if (wordBreak && list != null) WordBreak.last(run, list::has, list::hasPrefix) else run
    }

    override fun prepare(text: String, cancelled: AtomicBoolean) { if (!other) english.prepare(text, cancelled) }
    override fun lookAlike(context: String, word: String, next: String, result: (String?) -> Unit) =
        if (other) result(null) else english.lookAlike(context, word, next, result)
    /** Only the input method's own words show on the bar: English's sentence starts don't, for one. */
    override fun allowed(word: String) =
        if (other) list()?.contains(word) == true || word in starters || learning?.knows(word) == true ||
            nextWords?.after(null)?.contains(word) == true else english.allowed(word)
    override fun fixAllowed(typed: String, fix: String) = if (other) false else english.fixAllowed(typed, fix)
    /** Whether [word] is in English's own list, whatever input method types: Chinese offers it as a choice ([EnglishOffer]). */
    fun englishKnown(word: String) = english.known(word)

    override fun known(word: String) =
        if (other) list()?.contains(word) == true || word in starters || learning?.knows(word) == true else english.known(word)
    override fun sentenceStarts(): List<String> =
        if (other) BarWords.next(null, learning, starters, capitals = false, table = nextWords) else english.sentenceStarts()
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
        learning.kept(previous, next, known = list()?.contains(next) == true || next in starters)
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
        fun lastWord(before: String): String {
            var start = before.length
            while (start > 0 && (before[start - 1].isLetter() || before[start - 1] == '\'' || before[start - 1] == '’' ||
                    Character.getType(before[start - 1]) == Character.NON_SPACING_MARK.toInt() ||
                    Character.getType(before[start - 1]) == Character.COMBINING_SPACING_MARK.toInt())) start--
            return before.substring(start).trimStart('\'', '’')
        }
    }
}
