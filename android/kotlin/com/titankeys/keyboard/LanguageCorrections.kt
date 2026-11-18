package com.titankeys.keyboard

import com.titankeys.autocorrect.CorrectionData
import com.titankeys.autocorrect.FixKind
import com.titankeys.autocorrect.Lexicon
import com.titankeys.autocorrect.NoisyChannel
import com.titankeys.autocorrect.PairCounts
import com.titankeys.autocorrect.RecentWords
import com.titankeys.autocorrect.TypedCorrector
import java.text.Normalizer

/**
 * Corrections for the input methods other than English that have a word list (owner, 2026-11-17: "Its ok to be bad but
 * conservative. It shd still be present ... Basic Correction (at least in suggestions) and suggestions shd be built"). It's
 * English's own corrector ([TypedCorrector], its noisy channel) on the language's list, with only slips on the keys fixed:
 *
 * - **The keys:** each letter's key comes from the input method's layout and its held accents, so a neighbour is a neighbour on
 *   the Titan's keys in any script, and a letter typed for its accented form ("cafe" for café, "senor" for señor) costs almost
 *   nothing ([NoisyChannel.Keys]).
 * - **Space** puts in a fix only at English's strict confidence; otherwise it types what was typed, and the likeliest word only
 *   shows on the bar ([TypedCorrector.suggestion]).
 * - **Learning:** a word the user keeps after taking its fix back, twice, is learned for that language and never fixed again.
 *
 * Nothing here needs Android: tests/autocorrect measures it on every language's list. [LanguageCorrection] runs it on the phone.
 */
object LanguageCorrections {
    /** Whether [method] gets corrections: a word list, and no engine (Hindi's and Vietnamese's build their own words). */
    fun corrects(method: InputMethod) = !method.isEnglish && method.words != null && method.engine == null

    /** The keys behind [method]'s letters: its layout and its held accents, else the letter without accents if that's a to z. */
    fun keys(method: InputMethod): NoisyChannel.Keys {
        val keyOf = HashMap<Char, Char>()
        method.keys?.forEach { (printed, letters) ->
            for (letter in listOfNotNull(letters.base, letters.shift, letters.held, letters.heldShift))
                letter.singleOrNull()?.lowercaseChar()?.let { keyOf.putIfAbsent(it, printed) }
        }
        method.accents.forEach { (printed, accents) ->
            for (accent in accents) accent.singleOrNull()?.lowercaseChar()?.let { keyOf.putIfAbsent(it, printed) }
        }
        val key = { c: Char -> keyOf[c.lowercaseChar()] ?: base(c).takeIf { it in 'a'..'z' } ?: c }
        return NoisyChannel.Keys(key, ::base, shorthand = false)
    }

    /**
     * The case rules [method]'s corrector lowers and capitalises by: Turkish's and Azerbaijani's, where i's capital is İ and ı's
     * is I, as their word lists and next-word tables lower them ([NextWords.TURKIC]); the root's for every other.
     */
    fun caseRules(method: InputMethod): java.util.Locale = if (method.id in NextWords.TURKIC) method.javaLocale else java.util.Locale.ROOT

    /** [c] without its accents, in lower case: é is e, ñ is n. A letter that isn't Latin stays as it is. */
    fun base(c: Char): Char = Normalizer.normalize(c.lowercaseChar().toString(), Normalizer.Form.NFD).firstOrNull() ?: c

    /**
     * The corrector for [method]'s [list]. [learned] says whether the user learned or saved a word in this language, which
     * is then never fixed; [usable] whether a fix may put a word in (not a blocked word, nor one the user unsaved); [favourite]
     * whether the user made it a favourite, which wins a close call; [typos] whether Fix typos is on; [pairs] the input method's
     * word pairs (its pairs.txt, J's next-word table), which weigh a slip's candidates by the word before, as English's do. It's
     * always as strict as English's Only fix when sure: the pairs hold only each word's commonest followers, far fewer than
     * English's, so a fix must still clearly beat every other. It reads the whole list, so it runs away from the keyboard.
     */
    fun corrector(method: InputMethod, list: WordList, learned: (String) -> Boolean, rejected: MutableSet<String>,
                  recent: RecentWords, typos: () -> Boolean = { true }, usable: (String) -> Boolean = { true },
                  favourite: (String) -> Boolean = { false }, spaceFixes: Boolean = true,
                  pairs: PairCounts? = null): TypedCorrector {
        val lexicon = ListLexicon(list, learned, typos, usable, favourite)
        val keys = keys(method)
        val keysOf = { word: String -> String(CharArray(word.length) { keys.key(word[it]) }) }
        // How common a word is stands in for English's next-word model: between two close slips, the commoner word wins.
        val chance = { _: String, word: String -> list.frequency(word) / FREQUENCY_PER_NAT }
        val data = if (pairs == null) CorrectionData.forLanguage(lexicon, keysOf)
            else CorrectionData.forLanguage(lexicon, keysOf, pairs = pairs)
        return TypedCorrector(lexicon, data, rejected, recent, nextWordChance = chance,
            keys = keys, spaceFixes = spaceFixes, locale = caseRules(method))
    }

    /**
     * The input methods whose corrections show on the bar only, never put in by Space: those whose Space made more than
     * [MOST_WRONG] wrong changes on tests/corrections' slips, which writes the list ([BAR_ONLY_FILE], one id a line).
     */
    fun barOnly(lines: Sequence<String>): Set<String> =
        lines.map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()

    /** In the input methods' folder, android/assets/im. */
    const val BAR_ONLY_FILE = "corrections-bar-only.txt"
    /**
     * The input methods whose word pairs add wrong changes, measured mid-sentence by tests/corrections (more than
     * [MOST_ADDED_WRONG] in 600 words): their corrector goes without pairs. Read as [barOnly] is.
     */
    const val NO_PAIRS_FILE = "corrections-no-pairs.txt"
    const val MOST_ADDED_WRONG = 1
    /** Space's most wrong changes mid-sentence, of a language's slips, before its fixes go on the bar only (Master, 2026-11-17). */
    const val MID_SENTENCE_MOST_WRONG = 0.01
    /** The most wrong changes, of a language's slips, for Space to put fixes in (owner, 2026-11-17: near zero). */
    const val MOST_WRONG = 0.005

    /**
     * A next-word table ([NextWords], pairs.bin) as word-pair counts for the noisy channel: the table keeps only each word's
     * commonest followers in order, not their counts, so each counts by its place, halving down the list (256 for the first).
     */
    fun pairs(table: NextWords): PairCounts {
        val lines = ArrayList<String>()
        table.forEach { first, next, rank -> lines += "$first $next\t${PAIR_TOP shr rank}" }
        return PairCounts.parse(lines.asSequence())
    }

    /** The count a table's first follower stands for: see [pairs]. */
    private const val PAIR_TOP = 256

    /** AOSP's word lists rate words from 0 to 255 on a log scale, about this many steps a nat. */
    private const val FREQUENCY_PER_NAT = 16.0

    /** A language's word list as autocorrect's [Lexicon]: only Fix typos, no names, shorthand or English spellings. */
    private class ListLexicon(private val list: WordList, private val learned: (String) -> Boolean, private val typos: () -> Boolean,
                              private val usable: (String) -> Boolean, private val favourite: (String) -> Boolean) : Lexicon {
        override fun known(word: String) = list.has(word) || learned(word)
        override fun frequency(word: String) = list.frequency(word)
        override fun personal(word: String) = learned(word)
        override fun name(word: String): String? = null
        override fun preferredSpelling(word: String) = word
        override fun fixes(kind: FixKind) = kind == FixKind.TYPOS && typos()
        override fun onlyWhenSure() = true
        override fun forEachWord(action: (String, Int) -> Unit) = list.forEachWord(action)
        override fun usable(word: String) = usable.invoke(word)
        override fun favourite(word: String) = favourite.invoke(word)
    }
}
