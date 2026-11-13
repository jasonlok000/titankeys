package com.atlas.autocorrect

/**
 * The next-word model's summed log chance, in nats, of each of [texts] straight after [before]. Null when the model isn't there.
 * It may take a while, so it runs away from the keyboard's thread.
 */
fun interface ContinuationScorer {
    fun scores(before: String, texts: List<String>): DoubleArray?
}

/**
 * Which words the model reads around a word: [before] cuts the text before the word down to the words it reads, and [after] is how
 * many words after it to wait for. One small function, so another window can be tried without touching the rest.
 */
class ContextWindow(val after: Int, val before: (text: String) -> String) {
    companion object {
        /**
         * The owner's pick (apostrophe study, 2026-11-11): back to the last . ! ? or line break, or the start of the field, at most
         * 10 words, then 2 words after. Commas, semicolons, colons and dashes don't count as a boundary.
         */
        val SENTENCE = ContextWindow(after = 2) { text -> sinceSentenceMark(text, 10) }

        /** The words of [text] since its last sentence mark or line break, at most [most], as written, with no space at the end. */
        fun sinceSentenceMark(text: String, most: Int): String {
            var start = text.length
            var at = text.length
            var words = 0
            while (words < most) {
                while (at > 0 && text[at - 1].isWhitespace()) {
                    if (text[at - 1] == '\n') return text.substring(start).trimEnd()
                    at--
                }
                if (at == 0) break
                var from = at
                while (from > 0 && !text[from - 1].isWhitespace()) from--
                // A word that ends in . ! or ? ends the sentence before this one.
                if (text[at - 1] in SENTENCE_MARKS) break
                start = from
                at = from
                words++
            }
            return text.substring(start).trimEnd()
        }

        val SENTENCE_MARKS = setOf('.', '!', '?')
    }
}

/**
 * Apostrophes that only the words around them can tell: "its" is "it's" in "its going to rain", and stays in "wagged its tail". The
 * next-word model scores the typed form and each form with an apostrophe, with the words before and after, and an apostrophe form
 * wins only when it beats the typed one by more than the margin, in nats. It only ever adds an apostrophe. The owner's apostrophe
 * study (2026-11-11) found 0 wrong changes this way on 277 judged cases.
 */
class ApostropheContext(
    private val scorer: ContinuationScorer,
    private val window: ContextWindow = ContextWindow.SENTENCE,
    private val margin: () -> Double = { MARGIN },
) {
    /** The best apostrophe form for a word and how far it beats the typed one, in nats. */
    class Verdict(val form: String, val margin: Double)

    /** What the text after a waiting word says: wait for more words, give up on it, or ask the model with these [after] words. */
    sealed interface Following {
        data object Wait : Following
        data object Drop : Following
        data class Ready(val after: String) : Following
    }

    /** How many words after a waiting word to wait for. */
    val wanted get() = window.after

    /**
     * What [typed] should become, or null to keep it. [before] is all the text before it and [after] the words after it as written,
     * each with the space before it: " going to".
     */
    fun decide(before: String, typed: String, after: String): String? =
        judge(before, typed, after)?.takeIf { it.margin > margin() }?.form

    /** The best apostrophe form for [typed] with its margin, or null when there is none or too little to go on. */
    fun judge(before: String, typed: String, after: String): Verdict? {
        val forms = CANDIDATES[typed.lowercase()] ?: return null
        val context = window.before(before)
        // Too thin: a word alone, or with one word beside it, stays as typed.
        if (words(context) + words(after) < MIN_OTHER_WORDS) return null
        val opening = context.isEmpty()
        val lead = if (opening) "" else " "
        val texts = (listOf(typed) + forms).map { lead + cased(it, typed, opening) + after }
        val scores = scorer.scores(context, texts)?.takeIf { it.size == texts.size && it.none(Double::isNaN) } ?: return null
        val best = (1..forms.size).maxBy { scores[it] }
        return Verdict(cased(forms[best - 1], typed, false), scores[best] - scores[0])
    }

    companion object {
        /** How much likelier, in nats, the apostrophe form must be. At 1.0 the study's whole policy made no wrong change. */
        const val MARGIN = 1.0
        /** The margin when the user asks for fixes only when they're sure: still no wrong change in the study, and fewer fixes. */
        const val SURE_MARGIN = 2.0
        /** Words besides the target, before and after it together, that the model needs. */
        private const val MIN_OTHER_WORDS = 2

        /**
         * Typed forms that are real words, with the forms with an apostrophe they may be meant as. Never "were" for "we're": the
         * model can't tell them apart. Never "their" for "there": that's a word swap, not an apostrophe.
         */
        val CANDIDATES = mapOf(
            "its" to listOf("it's"), "lets" to listOf("let's"), "ill" to listOf("i'll"), "well" to listOf("we'll"),
            "id" to listOf("i'd"), "hell" to listOf("he'll"), "shed" to listOf("she'd"), "wed" to listOf("we'd"),
            "shell" to listOf("she'll"), "your" to listOf("you're"), "their" to listOf("they're"), "there" to listOf("they're"),
        )

        /** Whether only the words around [word] can say if it's missing an apostrophe. */
        fun isTarget(word: String) = word.lowercase() in CANDIDATES

        /** [form] in [typed]'s case: "I'll" always has its capital, and a capital or [opening] a sentence gives the first one. */
        fun cased(form: String, typed: String, opening: Boolean): String = when {
            form.startsWith("i'") -> "I" + form.drop(1)
            typed.length > 1 && typed.none { it.isLowerCase() } -> form.uppercase()
            opening || typed.first().isUpperCase() -> form.replaceFirstChar { it.uppercaseChar() }
            else -> form
        }

        private fun words(text: String) = text.split(' ', '\n', '\t').count { word -> word.any { it.isLetterOrDigit() } }

        /**
         * Reads [rest], the text between a waiting word and the cursor. It's ready once [wanted] words after it have ended with a
         * space or a mark, or sooner at a . ! ? or line break, which ends the sentence: words past that never count. The words come
         * as written, with any mark stuck to the last one ("free.", "to,"), as the study read them: "you're free." beats "your free."
         * by far more than "you're free" does.
         */
        fun following(rest: String, wanted: Int): Following {
            if (rest.isEmpty() || rest[0].isLetterOrDigit() || rest[0] == '\'' || rest[0] == '’') return Following.Drop
            fun ready(end: Int) = Following.Ready(rest.substring(0, rest.firstFrom(end) { it.isWhitespace() }))
            var at = 0
            var words = 0
            var end = 0
            while (true) {
                val gap = at
                while (at < rest.length && !isWordLetter(rest[at])) at++
                if (rest.substring(gap, at).any { it == '\n' || it in ContextWindow.SENTENCE_MARKS }) return ready(end)
                if (at == rest.length) return Following.Wait
                while (at < rest.length && isWordLetter(rest[at])) at++
                // A word that runs to the cursor is still being typed.
                if (at == rest.length) return Following.Wait
                words++
                end = at
                if (words == wanted) return ready(end)
            }
        }

        /** Where the first character from [from] that matches [stop] is, or the end. */
        private inline fun String.firstFrom(from: Int, stop: (Char) -> Boolean): Int {
            var at = from
            while (at < length && !stop(this[at])) at++
            return at
        }

        private fun isWordLetter(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’'
    }
}
