package com.titankeys.autocorrect

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
        judge(before, typed, after)?.takeIf { it.margin > margin() + (EXTRA_MARGIN[typed.lowercase()] ?: 0.0) }?.form

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
         * More margin for words that are right far more often than not. "were" is a very common right word, so it becomes "we're"
         * only at 3.0, or 4.0 when only sure: on all of Tatoeba's English, 40,488 meant "were", the highest margin was 2.98, and 3.0
         * made no wrong change (docs/studies/73-were.md).
         */
        val EXTRA_MARGIN = mapOf("were" to 2.0)

        /**
         * Typed forms that are real words, with the forms with an apostrophe they may be meant as. "were" takes a higher margin:
         * see [EXTRA_MARGIN]. Never "their" for "there": that's a word swap, not an apostrophe.
         */
        val CANDIDATES = mapOf(
            "its" to listOf("it's"), "lets" to listOf("let's"), "ill" to listOf("i'll"), "well" to listOf("we'll"),
            "id" to listOf("i'd"), "hell" to listOf("he'll"), "shed" to listOf("she'd"), "wed" to listOf("we'd"),
            "shell" to listOf("she'll"), "your" to listOf("you're"), "their" to listOf("they're"), "there" to listOf("they're"),
            "were" to listOf("we're"),
        )

        /** Whether only the words around [word] can say if it's missing an apostrophe. */
        fun isTarget(word: String) = word.lowercase() in CANDIDATES

        /**
         * The other form of each pair, both ways, that the bar offers while the word is being typed (#73). Offered only: a tap is
         * the user's choice. "they're" offers "their".
         */
        val OTHER_FORMS: Map<String, String> = run {
            val pairs = CANDIDATES.flatMap { (plain, forms) -> forms.map { plain to it } }
            val other = LinkedHashMap<String, String>()
            for ((plain, form) in pairs) {
                other.putIfAbsent(plain, form)
                other.putIfAbsent(form, plain)
            }
            other
        }

        /** The bar's second place, which it draws on the left (Strip's WORD_ORDER): the middle is the first. */
        const val LEFT_PLACE = 1

        /**
         * [suggestions] for [typed], the word being typed, with its pair's other form in the bar's left place: "it's" while you
         * type "its", and "its" while you type "it's". If the other form is already first, the typed form takes the left place.
         */
        fun offerOther(suggestions: List<String>, typed: String): List<String> {
            val other = OTHER_FORMS[typed.lowercase().replace('’', '\'')] ?: return suggestions
            if (suggestions.isEmpty()) return suggestions
            val shown = if (typed.startsWith("I'") || typed.startsWith("I’")) other else cased(other, typed, false)
            val first = suggestions[0]
            val left = if (first.equals(shown, ignoreCase = true)) typed else shown
            val rest = suggestions.drop(1).filterNot { it.equals(left, ignoreCase = true) }
            return listOf(first) + left + rest
        }

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
         * space, or sooner at any mark: a comma, semicolon, colon, dash, bracket, quote, . ! ? or a line break (#73). Words past it
         * never count, and with too few words before it for the model, the word stays as typed. A hyphen inside a word
         * ("well-known") is part of the word, not a mark. The words come as written, with the mark stuck to the last one ("free.",
         * "to,"), as the study read them: "you're free." beats "your free." by far more than "you're free" does.
         */
        fun following(rest: String, wanted: Int): Following {
            if (rest.isEmpty() || rest[0].isLetterOrDigit() || rest[0] == '\'' || rest[0] == '’') return Following.Drop
            // A hyphen straight after the word joins it to another, or may: "its-" is no longer the word that waited.
            if (rest[0] in HYPHENS && (rest.length == 1 || isWordLetter(rest[1]))) return Following.Drop
            fun ready(end: Int) = Following.Ready(rest.substring(0, rest.firstFrom(end) { it.isWhitespace() }))
            var at = 0
            var words = 0
            var end = 0
            while (true) {
                val gap = at
                while (at < rest.length && !isWordLetter(rest[at])) at++
                if (rest.substring(gap, at).any { !it.isWhitespace() || it == '\n' }) return ready(end)
                if (at == rest.length) return Following.Wait
                // A hyphen between letters keeps the word going, and one at the cursor may still get the rest of the word.
                while (at < rest.length && (isWordLetter(rest[at]) || rest[at] in HYPHENS && isWordLetter(rest[at - 1])
                        && (at + 1 == rest.length || isWordLetter(rest[at + 1])))) at++
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

        /** A hyphen, which joins two words into one when it has letters on both sides. A dash on its own is a mark. */
        private val HYPHENS = setOf('-', '‐')
    }
}
