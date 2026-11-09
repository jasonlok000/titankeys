package com.atlas.autocorrect

// The small helpers in this file are copied from Numen's AutoReplaceController (GPL-3.0, https://github.com/jlo-aug/numen,
// commit 7b1391c), which is a modified version of Pastiera.

import com.atlas.keyboard.Layout
import java.io.Reader
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln

/** What autocorrect needs from the keyboard's word list. */
interface Lexicon {
    /** Whether the word list or the user's own words hold [word], in any case. */
    fun known(word: String): Boolean
    /** How common [word] is, from 0 to 255 in any case. 0 means that the list does not hold it. */
    fun frequency(word: String): Int
    /** Whether the user added or learned [word]. A word typed once or twice is not learned yet, as it may be a typo. */
    fun personal(word: String): Boolean
    /** The spelling of a name, such as "Monday" for "monday". Null for all other words. */
    fun name(word: String): String?
    /** [word] in the English spelling that the user chose, such as "colour" or "color". */
    fun preferredSpelling(word: String): String
    /** Calls [action] with each word in lowercase and its frequency. */
    fun forEachWord(action: (String, Int) -> Unit)
}

/** Which letters sit next to each other on the Titan 2 Elite, diagonals included. */
object KeyNeighbours {
    private val near = BooleanArray(26 * 26)

    init {
        val keys = ArrayList<Triple<Int, Int, Int>>()
        for (row in 0..2) for ((column, key) in Layout.rows[row].withIndex())
            if (Layout.isLetter(key)) keys += Triple(key[0] - 'A', row, column)
        for (first in keys) for (second in keys) if (first.first != second.first
            && hypot((first.second - second.second).toDouble(), (first.third - second.third).toDouble()) <= 1.5)
            near[first.first * 26 + second.first] = true
    }

    fun near(a: Char, b: Char): Boolean {
        val first = a.lowercaseChar() - 'a'
        val second = b.lowercaseChar() - 'a'
        return first in 0..25 && second in 0..25 && near[first * 26 + second]
    }
}

/** A word in the list, with how common it is. */
data class DictionaryEntry(val word: String, val frequency: Int)

/** The word data behind autocorrect, in memory only while it is needed. */
class CorrectionData private constructor(
    val pairs: PairCounts,
    val triples: TripleCounts,
    val confusions: ConfusionTable,
    val misspellings: Map<String, String>,
    val known: KnownWords,
    val names: KnownWords,
    val slang: KnownWords,
    val shortWords: Map<String, Int>,
    val spelling: SymSpell,
    /** "doesnt" to "doesn't", as [spelling] holds contractions without the apostrophe. */
    val contractions: Map<String, String>,
    /** Common words by first letter, for the last-resort fix. */
    val longWords: Map<Char, List<DictionaryEntry>>,
) {
    companion object {
        private const val PREFIX_LENGTH = 4
        /** Numen's dictionary stops at about this frequency, so rarer words are not fixes. */
        private const val MIN_FREQUENCY = 60
        private const val LONG_WORD_MIN_FREQUENCY = 100

        /** Reads the English data files, which [open] finds by name, and indexes [lexicon]. */
        fun read(lexicon: Lexicon, open: (String) -> Reader): CorrectionData {
            val pairs = open("en_pairs.txt").useLines { PairCounts.parse(it) }
            val triples = open("en_triples.txt").useLines { TripleCounts.parse(it) }
            val confusions = open("en_confusions.txt").useLines { ConfusionTable.parse(it) }
            val misspellings = open("en_misspellings.txt").useLines { lines ->
                lines.filter { it.isNotEmpty() && !it.startsWith("#") }
                    .mapNotNull { line -> line.split("->").takeIf { it.size == 2 }?.let { (typo, fix) -> typo to fix } }
                    .toMap(HashMap())
            }
            val known = open("en_known.txt").useLines { KnownWords.parse(it) }
            val names = open("en_names.txt").useLines { KnownWords.parse(it) }
            val slang = open("en_slang.txt").useLines { KnownWords.parse(it) }
            val shortWords = open("en_short_words.txt").useLines { lines ->
                lines.filterNot { it.isBlank() || it.startsWith("#") }
                    .mapNotNull { line ->
                        val cells = line.split('\t')
                        cells.getOrNull(1)?.toIntOrNull()?.let { cells[0] to it }
                    }
                    .toMap(HashMap())
            }
            val spelling = SymSpell(maxEditDistance = 2, prefixLength = PREFIX_LENGTH)
            val contractions = HashMap<String, String>()
            val longWords = HashMap<Char, MutableList<DictionaryEntry>>()
            lexicon.forEachWord { word, frequency ->
                if (frequency < MIN_FREQUENCY) return@forEachWord
                if (word.all { it in 'a'..'z' }) {
                    spelling.addWord(word, frequency)
                    if (frequency >= LONG_WORD_MIN_FREQUENCY)
                        longWords.getOrPut(word[0]) { ArrayList() } += DictionaryEntry(word, frequency)
                } else if (NoisyChannel.isUnambiguousContraction(word)) {
                    val plain = word.replace("'", "")
                    contractions[plain] = word
                    spelling.addWord(plain, frequency)
                }
            }
            return CorrectionData(pairs, triples, confusions, misspellings, known, names, slang, shortWords,
                spelling, contractions, longWords)
        }
    }
}

/** A typed word that has ended. [typo] is what the user typed if autocorrect changed it. */
class EndedWord(val word: String, val typo: String?)

/**
 * The state of autocorrect that outlives its word data: words the user took back, words used in this
 * conversation, and the word data itself, which is null until it has loaded.
 */
class Autocorrect(
    private val keptOnce: KeptOnceStore,
    /** Saves a word that the user kept on two occasions. */
    private val learn: (String) -> Unit,
    /** Whether the user lets the keyboard learn words. If not, a kept word is not noted or saved. */
    private val learning: () -> Boolean = { true },
    /** Called for each typed key, so the word data loads when it is needed and is freed when it is not. */
    val using: () -> Unit = {},
) {
    val rejected = HashSet<String>()
    val recent = RecentWords()
    var corrector: TypedCorrector? = null

    /** Another field starts another session, in which undone words get a new chance. */
    fun newField() = rejected.clear()

    fun undone(word: String) {
        rejected += word.lowercase()
    }

    /** The user took a fix back and then changed the word, so they were only editing. */
    fun notKept(word: String) {
        rejected -= word.lowercase()
    }

    /** The user kept a word they had undone: noted the first time, learned the second. [before] is the text before it. */
    fun keep(word: String, before: String) {
        if (!learning() || !word.all { it.isLetter() || it == '\'' }) return
        if (keptOnce.contains(word)) {
            keptOnce.remove(word)
            // A capital at the start of a sentence is not part of the word, but one mid-sentence marks a name.
            learn(if (TypedCorrector.isSentenceStartCapital(word, before)) word.lowercase() else word)
        } else keptOnce.add(word)
    }
}

/**
 * Numen's autocorrect for typed words, rebuilt around the keyboard's own word list. The small helpers
 * come from Numen's AutoReplaceController; its older Pastiera rules are left out.
 */
class TypedCorrector(
    private val lexicon: Lexicon,
    private val data: CorrectionData,
    private val rejected: MutableSet<String>,
    private val recent: RecentWords,
) {
    /** The last [typed] characters of the text should become [word]. */
    class Fix(val typed: String, val word: String)

    /** The second check: [original] at the end of the text should become [replaced], because [word] should be [fix]. */
    class Review(val word: String, val fix: String, val original: String, val replaced: String, val reviewedLast: Boolean)

    private val channel = NoisyChannel(::candidates, KeyNeighbours::near, data.pairs::count, data.triples::count)

    private val context = ContextCorrector(
        frequencyOf = lexicon::frequency,
        isNeighborKey = KeyNeighbours::near,
        pairCount = data.pairs::count,
        isProtected = { word -> word in data.slang || lexicon.personal(word) || word.first().isUpperCase() && word in data.names },
        isRecognized = { word -> word in data.known },
        rejected = rejected,
        noisyChannel = channel,
        confusions = data.confusions,
        tripleCount = data.triples::count,
        shortWords = data.shortWords,
        isNameOrAcronym = { word -> lexicon.name(word) != null },
    )

    /** Dictionary words within two edits, for the channel. Names are left to [nameSlip]. */
    private fun candidates(typed: String): List<String> =
        data.spelling.lookup(typed, MAX_CANDIDATES).mapNotNull { item ->
            data.contractions[item.term] ?: item.term.takeIf { lexicon.name(it) == null }
        }

    private fun isKnown(word: String) = lexicon.known(word) || word in data.known || word in data.slang

    private fun spelled(word: String) = lexicon.name(word) ?: word

    /** A dictionary word as a fix writes it. A name has one spelling, and other words follow the user's English spelling. */
    private fun written(word: String) = lexicon.name(word) ?: lexicon.preferredSpelling(word)

    private fun recased(word: String, typed: String) = CasingHelper.applyCasing(written(word), typed, forceLeadingCapital = false)

    /** The second check's [fixed] for [word], in the user's English spelling. Null if that is what [word] already is. */
    private fun respelled(fixed: String?, word: String) = fixed?.let(lexicon::preferredSpelling)?.takeUnless { it == word }

    /** Whether the word list or the user's learned words hold [word]. The next word can still change any other word. */
    fun listed(word: String) = lexicon.known(word)

    /**
     * The first check, when a typed [word] ends. [before] is the text before it, and [typedPrevious] is the word
     * before it if the user typed that word, because a swiped word must not change.
     */
    fun check(word: String, before: String, typedPrevious: String?): Fix? =
        firstFix(word, before, typedPrevious)?.takeUnless { it.word == it.typed }

    private fun firstFix(word: String, before: String, typedPrevious: String?): Fix? {
        if (isInsideAddress(before + word)) return null
        val lower = word.lowercase()
        val isRejected = lower in rejected
        if (isCamelCaseName(word)) {
            // The one camel-case slip worth fixing is a missed space before a name ("andMary").
            if (isRejected || isKnown(word)) return null
            return nameSplit(word)?.let { Fix(word, it) }
        }
        if (!isRejected && !hasExactCase(word)) shiftHeldTooLongFix(word, lexicon::frequency)?.let { return Fix(word, it) }
        if (!isRejected) spacedContraction(word, before, typedPrevious)?.let { return it }
        val letters = word.all { it.isLetter() }
        if (!isKnown(word) && word !in data.names && !isRejected && letters) nameSplit(word)?.let { return Fix(word, it) }
        val isName = isCapitalizedName(word, before)
        when {
            word.any { it.isDigit() } -> return null // "3pm", "5km"
            word.length >= 2 && word.all { it.isUpperCase() } -> return null // "TXU", "LV"
            isStretchedForEmphasis(word, ::isKnown) -> return null // "sooo", "pleaseee"
            isPluralOfKnownWord(word, ::isKnown, lexicon::frequency) -> return null // "autocorrects"
            isWordWithClitic(word, ::isKnown, rootMayBeName = isName) -> return null
            // A capitalised first name ("Ali") is meant, even where it could be a slip ("All").
            word.first().isUpperCase() && word.drop(1).all { it.isLowerCase() } && word in data.names && !isKnown(word) -> return null
            isName -> return (recentSlip(word, lower) ?: nameSlip(word, lower))?.let { Fix(word, it) }
            word.all { it.isLowerCase() } && word in data.known -> return null
        }
        if (isRejected || isKnown(word)) return null
        // A well-known misspelling ("recieve") has one known fix.
        data.misspellings[lower]?.let { return Fix(word, recased(it, word)) }
        // A missing apostrophe ("doesnt"). Without it these are not words, so there is nothing else it could be.
        data.contractions[lower]?.let { return Fix(word, recased(it, word)) }
        val previous = previousWordFor(before)
        val beforePrevious = wordBeforePrevious(before, previous)
        val casual = word.all { it.isLowerCase() } || isSentenceStartCapital(word, before)
        if (casual) {
            twoLetterFix(lower, lexicon::frequency, KeyNeighbours::near) { contextFit(it, previous, beforePrevious) }
                ?.let { return Fix(word, recased(it, word)) }
            if (letters && word.length >= 2) {
                val fixed = channel.pick(lower, previous, beforePrevious, next = null)?.let(channel::confidentFix)
                    // A capitalised first word may be a name ("Mennad"): only a likely slip to a common word.
                    ?.takeIf { word.first().isLowerCase() || isPlausibleForCasing(word, spelled(it)) }
                    ?.takeUnless { isLikelyCompound(lower, it) }
                if (fixed != null) return Fix(word, recased(fixed, word))
            }
        }
        (recentSlip(word, lower) ?: nameSlip(word, lower))?.let { return Fix(word, it) }
        if (isLikelyCompound(lower, null)) return null
        findWordSplit(word, lexicon::frequency, ::isKnown) { left, right ->
            // A rare half must be seen next to the other ("con tuned" is no missed space), and a
            // capitalised word may be a name, so it always must ("Mansell" is not "Man sell").
            data.pairs.count(left.lowercase(), right.lowercase()) > 0 || !word.first().isUpperCase() &&
                minOf(lexicon.frequency(left), lexicon.frequency(right)) >= unattestedSplitFrequency(left, right)
        }?.let { return Fix(word, it) }
        if (word.length >= DEEP_FIX_MIN_LENGTH && letters) {
            deepFix(word, data.longWords[lower.first()].orEmpty(), KeyNeighbours::near)
                ?.takeIf { isPlausibleForCasing(word, spelled(it)) }
                ?.let { return Fix(word, recased(it, word)) }
        }
        return null
    }

    /**
     * The second check, once the space or mark after [latest] is in [text]. [earlier] is the typed word before it, if
     * there is one. A swiped [latest] says what fits around [earlier], but only a typed word is reviewed.
     */
    fun review(text: String, truncated: Boolean, earlier: EndedWord?, latest: String, latestTyped: Boolean): Review? {
        val trailing = ContextCorrector.findTrailing(text, truncated) ?: return null
        if (!same(trailing.next, latest)) return null
        if (earlier != null && same(trailing.word, earlier.word) && !isDeliberate(trailing.word)) {
            val typo = earlier.typo
            // A fix made at the last space, seen again with the word after it.
            val fixed = respelled(typo?.let { context.reconsiderFix(trailing.before, it, trailing.word, trailing.next) }, trailing.word)
                ?: respelled(context.review(trailing.before, trailing.word, trailing.next, trailing.beforePrevious, typedAsIs = typo == null), trailing.word)
            if (fixed != null) return Review(trailing.word, fixed, "${trailing.word} ${trailing.next}${trailing.tail}",
                "$fixed ${trailing.next}${trailing.tail}", false)
        }
        if (!latestTyped || trailing.tail.firstOrNull() !in SENTENCE_END_MARKS || isDeliberate(trailing.next)) return null
        return respelled(context.review(trailing.word, trailing.next, PairCounts.SENTENCE_END), trailing.next)?.let { fixed ->
            Review(trailing.next, fixed, "${trailing.next}${trailing.tail}", "$fixed${trailing.tail}", true)
        }
    }

    /** The first check left these alone on purpose, so the second must too. */
    private fun isDeliberate(word: String) =
        isStretchedForEmphasis(word, ::isKnown) || isPluralOfKnownWord(word, ::isKnown, lexicon::frequency)

    /** Two words that are not seen together must both be common, unless one is a word that goes almost anywhere. */
    private fun unattestedSplitFrequency(left: String, right: String) =
        if (left.lowercase() in data.shortWords || right.lowercase() in data.shortWords) MIN_SPLIT_PART_FREQUENCY else UNATTESTED_SPLIT_MIN_FREQUENCY

    /** A space typed for the apostrophe: "I ll" to "I'll", "don t" to "don't". */
    private fun spacedContraction(word: String, before: String, typedPrevious: String?): Fix? {
        if (!before.endsWith(' ')) return null
        val previous = before.removeSuffix(" ").takeLastWhile { it.isLetter() }
        val beforePrevious = before.removeSuffix(" ").dropLast(previous.length).lastOrNull()
        if (previous.isEmpty() || beforePrevious?.isLetterOrDigit() == true || !previous.equals(typedPrevious, ignoreCase = true)
            || "${previous.lowercase()}'${word.lowercase()}" !in SPACED_CONTRACTIONS) return null
        return Fix("$previous $word", "$previous'$word")
    }

    /** A slip on a word used twice in this conversation, such as a friend's name. */
    private fun recentSlip(word: String, lower: String): String? {
        if (lower in rejected || !word.all { it.isLetter() } || isKnown(word)) return null
        return recent.slipTarget(word) { typed, used ->
            // Names and words the dictionary lacks; common words already have their own fixes.
            lexicon.frequency(used) < RECENT_SLIP_MAX_COMMON_FREQUENCY && abs(typed.length - used.length) <= 1
                && typoLikelihood(typed, used, KeyNeighbours::near) >= RECENT_SLIP_MIN_LIKELIHOOD
        }
    }

    /** A classic slip on one common name: "Sudnay" to "Sunday", "frday" to "Friday". */
    private fun nameSlip(word: String, lower: String): String? {
        if (word.length < PROPER_NOUN_SLIP_MIN_LENGTH || !word.all { it.isLetter() } || word.drop(1).any { it.isUpperCase() }
            || lower in rejected || isKnown(word)) return null
        return data.spelling.lookup(lower, MAX_CANDIDATES)
            .mapNotNull { lexicon.name(it.term) }
            .filter { name ->
                name.first().isUpperCase() && name.drop(1).all { it.isLowerCase() } && !name.equals(word, ignoreCase = true)
                    && lexicon.frequency(name) >= PROPER_NOUN_CLASSIC_SLIP_MIN_FREQUENCY
                    && typoLikelihood(word, name, KeyNeighbours::near) >= CLASSIC_SLIP_LIKELIHOOD
            }
            .distinct()
            .singleOrNull()
    }

    /**
     * A missed space next to a name: "andMary" (a lowercase word, then a capital) or "Tomis" (a
     * name, then a common word). Brand-style words ("iPhone", "YouTube") and surnames never split.
     */
    private fun nameSplit(word: String): String? {
        if (word.length < MIN_SPLIT_WORD_LENGTH + 1) return null
        val capitals = (1 until word.length).filter { word[it].isUpperCase() }
        fun isNameLike(part: String) = part.length >= 2 && part.first().isUpperCase() && part.drop(1).all { it.isLowerCase() }
            && (part in data.names || lexicon.name(part.lowercase()) == part && lexicon.frequency(part) >= PROPER_NOUN_CLASSIC_SLIP_MIN_FREQUENCY)
        fun isCommon(part: String) = lexicon.frequency(part) >= if (part.length <= 2) MIN_SPLIT_SHORT_PART_FREQUENCY else NAME_SPLIT_MIN_WORD_FREQUENCY
        if (word.first().isLowerCase() && capitals.size == 1) {
            val left = word.substring(0, capitals[0])
            val right = word.substring(capitals[0])
            return "$left $right".takeIf { left.length >= 2 && isCommon(left) && isNameLike(right) }
        }
        if (word.first().isUpperCase() && capitals.isEmpty()) {
            return (2..word.length - 2)
                .map { word.substring(0, it) to word.substring(it) }
                .filter { (name, rest) -> isNameLike(name) && isCommon(rest) && rest !in SURNAME_ENDINGS }
                .maxByOrNull { (_, rest) -> lexicon.frequency(rest) }
                ?.let { (name, rest) -> "$name $rest" }
        }
        return null
    }

    /** A word spelled exactly this way in the word list ("VMware") is not a slip. */
    private fun hasExactCase(word: String) = lexicon.name(word.lowercase()) == word

    private fun isPlausibleForCasing(word: String, candidate: String): Boolean {
        val frequency = lexicon.frequency(candidate)
        val candidateIsName = candidate.firstOrNull()?.isUpperCase() == true
        val likelihood = typoLikelihood(word, candidate, KeyNeighbours::near)
        if (candidateIsName && frequency < PROPER_NOUN_FIX_MIN_FREQUENCY
            && !(likelihood >= CLASSIC_SLIP_LIKELIHOOD && frequency >= PROPER_NOUN_CLASSIC_SLIP_MIN_FREQUENCY)) return false
        val typedIsCapitalized = word.firstOrNull()?.isUpperCase() == true && word.drop(1).all { !it.isUpperCase() }
        if (!typedIsCapitalized) return true
        if (candidateIsName) return true
        // A long everyday word one letter off ("Yesterdar") is a slip, not a name.
        if (candidate.length >= SENTENCE_START_LONG_WORD_LENGTH && frequency >= SENTENCE_START_LONG_FIX_MIN_FREQUENCY
            && (likelihood >= SENTENCE_START_FIX_MIN_LIKELIHOOD || editDistance(word.lowercase(), candidate.lowercase()) == 1)) return true
        return frequency >= SENTENCE_START_FIX_MIN_FREQUENCY && likelihood >= SENTENCE_START_FIX_MIN_LIKELIHOOD
    }

    /**
     * Two common words written as one ("backplate", "groupchat") and not a known phrase: a
     * closed compound, meant as typed unless the fix is a common word itself ("everbody" ->
     * "everybody" still is fixed; "backplate" -> "backplane" is not).
     */
    private fun isLikelyCompound(word: String, fix: String?): Boolean {
        if (word.length < 2 * COMPOUND_MIN_HALF || !word.all { it in 'a'..'z' }) return false
        val fixFrequency = fix?.let { lexicon.frequency(it) } ?: 0
        return (COMPOUND_MIN_HALF..word.length - COMPOUND_MIN_HALF).any { cut ->
            val left = word.substring(0, cut)
            val right = word.substring(cut)
            val weaker = minOf(lexicon.frequency(left), lexicon.frequency(right))
            weaker >= COMPOUND_HALF_MIN_FREQUENCY && fixFrequency < COMPOUND_FIX_MIN_FREQUENCY && data.pairs.count(left, right) == 0
                // A compound is not made with a word like "how", so this is a missed space ("hellohow").
                && left !in data.shortWords && right !in data.shortWords
        }
    }

    private fun wordBeforePrevious(before: String, previous: String?): String? {
        if (previous == null || previous == PairCounts.SENTENCE_START) return null
        val trimmed = before.trimEnd(' ')
        return previousWordFor(trimmed.dropLast(trimmed.takeLastWhile { it.isLetter() || it == '\'' || it == '’' }.length))
    }

    private fun contextFit(candidate: String, previous: String?, beforePrevious: String?): Double {
        val pairs = previous?.let { data.pairs.count(it, candidate) } ?: 0
        val triples = if (previous != null && beforePrevious != null) data.triples.count(beforePrevious, previous, candidate) else 0
        return saturating(pairs, CONTEXT_SATURATION_COUNT) * CONTEXT_WEIGHT + saturating(triples, TRIPLE_SATURATION_COUNT) * TRIPLE_WEIGHT
    }

    companion object {
        private const val MAX_CANDIDATES = 64
        private val SENTENCE_END_MARKS = setOf('.', '!', '?')
        private const val PLURAL_MIN_LENGTH = 5
        private const val COMPOUND_MIN_HALF = 3
        private const val COMPOUND_HALF_MIN_FREQUENCY = 100
        private const val COMPOUND_FIX_MIN_FREQUENCY = 100
        // Plurals of very common words are in the dictionary already; "agains" is a slip.
        private const val PLURAL_BASE_MAX_FREQUENCY = 140
        // Ranking: typo likelihood dominates, word frequency (0..~220) breaks ties.
        private const val FREQUENCY_WEIGHT = 0.6
        // Bonus for a candidate that often follows the word before, saturating at this pair count.
        private const val CONTEXT_WEIGHT = 0.8
        private const val CONTEXT_SATURATION_COUNT = 1000.0
        // The same for the two words before.
        private const val TRIPLE_WEIGHT = 0.3
        private const val TRIPLE_SATURATION_COUNT = 100.0
        // How likely a slip onto a word from this conversation must be, and how common it may be.
        private const val RECENT_SLIP_MIN_LIKELIHOOD = 0.65
        private const val RECENT_SLIP_MAX_COMMON_FREQUENCY = 140
        private const val MAX_FREQUENCY = 220.0
        private const val FIRST_LETTER_FIX_MIN_FREQUENCY = 115
        private const val MIN_SPLIT_WORD_LENGTH = 4
        // deepFix tuning: fix badly mangled common words but leave rare unknown words alone.
        private const val DEEP_SLIP_COST = 0.5
        private const val DEEP_VOWEL_COST = 0.5
        private const val VOWELS = "aeiou"
        private const val DEEP_MISSED_COST = 0.7
        private const val DEEP_EXTRA_COST = 0.9
        private const val DEEP_FIX_MIN_LENGTH = 7
        private const val DEEP_FIX_MAX_LENGTH_DIFFERENCE = 2
        private const val DEEP_FIX_MIN_FREQUENCY = 100
        private const val DEEP_FIX_MAX_COST = 1.25
        private const val DEEP_FIX_MIN_MARGIN = 0.3
        private const val TWO_LETTER_FIX_MIN_FREQUENCY = 150
        private const val TWO_LETTER_FIX_MIN_MARGIN = 0.15
        // A capitalised word may be a name, so at a sentence start it is only fixed by a likely
        // slip to a common lowercase word.
        private const val SENTENCE_START_FIX_MIN_FREQUENCY = 115
        private const val SENTENCE_START_FIX_MIN_LIKELIHOOD = 0.65
        // Names are rarely one slip away from a long everyday word, so those may be less common.
        private const val SENTENCE_START_LONG_WORD_LENGTH = 7
        private const val SENTENCE_START_LONG_FIX_MIN_FREQUENCY = 60
        // A lowercase slip only becomes a name or place when that name is common.
        private const val PROPER_NOUN_FIX_MIN_FREQUENCY = 130
        private const val PROPER_NOUN_CLASSIC_SLIP_MIN_FREQUENCY = 100
        private const val PROPER_NOUN_SLIP_MIN_LENGTH = 5
        // typoLikelihood of a missed letter, swapped letters or a doubled key.
        private const val CLASSIC_SLIP_LIKELIHOOD = 0.8
        // English clitics after an apostrophe.
        private val CLITICS = setOf("s", "ll", "re", "ve", "d", "m")
        private const val PREVIOUS_WORD_LOOKBACK = 32
        // Both halves of a split must be common words, and two-letter halves very common.
        private const val MIN_SPLIT_PART_FREQUENCY = 100
        private const val MIN_SPLIT_SHORT_PART_FREQUENCY = 150
        // A missed space after or before a little everyday word ("of", "by", "the").
        private const val GLUE_WORD_MAX_LENGTH = 4
        private const val GLUE_WORD_MIN_FREQUENCY = 170
        private const val UNATTESTED_SPLIT_MIN_FREQUENCY = 130
        private const val GLUE_OTHER_MIN_LENGTH = 4
        // A missed space next to a name ("andMary", "Tomis"); surname endings never split ("Peterson").
        private const val NAME_SPLIT_MIN_WORD_FREQUENCY = 120
        // Contractions whose apostrophe is often typed as a space ("I ll"). Only unambiguous ones.
        private val SPACED_CONTRACTIONS = buildSet {
            listOf("i", "you", "we", "they", "he", "she", "it", "that", "there", "who", "what").forEach { add("$it'll") }
            listOf("i", "you", "we", "they", "he", "she", "that", "there", "who").forEach { add("$it'd") }
            listOf("i", "you", "we", "they", "could", "would", "should", "might", "must", "who").forEach { add("$it've") }
            listOf("you", "we", "they", "who", "what").forEach { add("$it're") }
            add("i'm")
            listOf("it", "that", "what", "there", "let").forEach { add("$it's") }
            listOf("don", "can", "won", "isn", "aren", "wasn", "weren", "didn", "doesn", "hasn", "haven", "hadn",
                "couldn", "wouldn", "shouldn", "mustn", "ain").forEach { add("$it't") }
        }
        private val SURNAME_ENDINGS = setOf("son", "sen", "ton", "ley", "ford", "berg", "stein", "man", "field", "wood", "worth", "by", "er", "ing", "ville")

        private fun same(a: String, b: String) = a.replace('’', '\'').equals(b.replace('’', '\''), ignoreCase = true)

        private fun saturating(count: Int, saturation: Double): Double =
            if (count <= 0) 0.0 else minOf(1.0, ln(1.0 + count) / ln(1.0 + saturation))

        /** Fixes a two-letter non-word ("si" to "is") towards a very common word, only when that fix clearly beats every other. */
        internal fun twoLetterFix(
            word: String,
            frequencyOf: (String) -> Int,
            isNeighborKey: (Char, Char) -> Boolean,
            contextFit: (String) -> Double = { 0.0 },
        ): String? {
            if (word.length != 2 || !word.all { it in 'a'..'z' }) return null
            val candidates = buildSet {
                add(word.reversed())
                for (i in 0..1) for (c in 'a'..'z') if (c != word[i] && isNeighborKey(word[i], c)) add(word.replaceRange(i, i + 1, c.toString()))
                for (i in 0..2) for (c in 'a'..'z') add(word.substring(0, i) + c + word.substring(i))
            }
            val ranked = candidates
                .filter { candidate -> frequencyOf(candidate) >= TWO_LETTER_FIX_MIN_FREQUENCY }
                .map { candidate ->
                    candidate to typoLikelihood(word, candidate, isNeighborKey) +
                        FREQUENCY_WEIGHT * frequencyOf(candidate) / MAX_FREQUENCY + contextFit(candidate)
                }
                .sortedByDescending { (_, score) -> score }
            val (best, score) = ranked.firstOrNull() ?: return null
            val runnerUp = ranked.getOrNull(1)?.second ?: 0.0
            return best.takeIf { score - runnerUp >= TWO_LETTER_FIX_MIN_MARGIN }
        }

        /** Keyboard-weighted edit cost of [typed] against [word]. An extra letter is cheap when a finger could have added it. */
        internal fun keyboardWeightedCost(typed: String, word: String, isNeighborKey: (Char, Char) -> Boolean): Double {
            val t = typed.lowercase()
            val w = word.lowercase()
            // Rolling rows: i-2 (for swapped letters), i-1 and i.
            var twoBack = DoubleArray(w.length + 1)
            var previous = DoubleArray(w.length + 1) { j -> j * DEEP_MISSED_COST }
            var current = DoubleArray(w.length + 1)
            for (i in 1..t.length) {
                val letter = t[i - 1]
                val before = t.getOrNull(i - 2)
                val after = t.getOrNull(i)
                val fatFinger = (before != null && (before == letter || isNeighborKey(letter, before))) ||
                    (after != null && (after == letter || isNeighborKey(letter, after)))
                val extra = if (fatFinger) DEEP_SLIP_COST else DEEP_EXTRA_COST
                current[0] = previous[0] + DEEP_EXTRA_COST
                for (j in 1..w.length) {
                    val target = w[j - 1]
                    val substitution = when {
                        letter == target -> 0.0
                        isNeighborKey(letter, target) -> DEEP_SLIP_COST
                        // Spelling, not fingers: one vowel for another ("seperate", "definately").
                        letter in VOWELS && target in VOWELS -> DEEP_VOWEL_COST
                        else -> 1.0
                    }
                    // One of a doubled letter left out ("acomodation", "embaras") is a spelling habit.
                    val missed = if (j > 1 && w[j - 1] == w[j - 2]) DEEP_SLIP_COST else DEEP_MISSED_COST
                    var cost = minOf(previous[j] + extra, current[j - 1] + missed, previous[j - 1] + substitution)
                    if (i > 1 && j > 1 && letter == w[j - 2] && before == target && letter != target) {
                        cost = minOf(cost, twoBack[j - 2] + DEEP_SLIP_COST)
                    }
                    current[j] = cost
                }
                val recycled = twoBack
                twoBack = previous
                previous = current
                current = recycled
            }
            return previous[w.length]
        }

        /** Last resort for a long word too mangled for the regular fix: the common word with the same first letter that a few likely slips explain, if it clearly beats every other. */
        internal fun deepFix(word: String, candidates: List<DictionaryEntry>, isNeighborKey: (Char, Char) -> Boolean): String? {
            if (word.length < DEEP_FIX_MIN_LENGTH || !word.all { it.isLetter() }) return null
            val ranked = candidates.asSequence()
                .filter { entry ->
                    entry.frequency >= DEEP_FIX_MIN_FREQUENCY && entry.word.all { it.isLetter() } &&
                        // Two letters apart covers both of two doubled letters left out ("embaras").
                        abs(entry.word.length - word.length) <= DEEP_FIX_MAX_LENGTH_DIFFERENCE &&
                        entry.word.first().equals(word.first(), ignoreCase = true)
                }
                .mapNotNull { entry ->
                    val cost = keyboardWeightedCost(word, entry.word, isNeighborKey)
                    if (cost > DEEP_FIX_MAX_COST) null
                    else entry.word to cost - FREQUENCY_WEIGHT * entry.frequency / MAX_FREQUENCY
                }
                .sortedBy { (_, score) -> score }
                .take(2)
                .toList()
            val (best, score) = ranked.firstOrNull() ?: return null
            val runnerUp = ranked.getOrNull(1)?.second
            return best.takeIf { runnerUp == null || runnerUp - score >= DEEP_FIX_MIN_MARGIN }
        }

        /** A real word with its last letter held for emphasis ("sooo", "pleaseee"), not a slip. Three letters where the doubled form is a word ("alll") is one extra key instead. */
        internal fun isStretchedForEmphasis(word: String, isKnown: (String) -> Boolean): Boolean {
            val last = word.lastOrNull()?.takeIf { it.isLetter() } ?: return false
            val run = word.takeLastWhile { it.equals(last, ignoreCase = true) }.length
            if (run < 3) return false
            val stem = word.dropLast(run)
            val doubled = isKnown(stem + last + last)
            return if (run == 3) isKnown(stem + last) && !doubled else isKnown(stem + last) || doubled
        }

        /** Best "left right" split of a word typed without its space, or null if none is convincing. */
        internal fun findWordSplit(
            word: String,
            frequencyOf: (String) -> Int,
            isKnownWord: (String) -> Boolean = { false },
            // Whether the two words are seen together in real text ("see you", not "con tuned").
            isAttested: (String, String) -> Boolean = { _, _ -> true },
        ): String? {
            if (word.length < MIN_SPLIT_WORD_LENGTH || !word.all { it.isLetter() }) return null
            var best: String? = null
            var bestScore = 0
            for (i in 2..word.length - 2) {
                val left = word.substring(0, i)
                val right = word.substring(i)
                if (!isAttested(left, right)) continue
                val score = minOf(splitPartScore(left, frequencyOf), splitPartScore(right, frequencyOf))
                if (score > bestScore) {
                    bestScore = score
                    best = "$left $right"
                }
            }
            if (best != null) return best
            // A very common little word glued to a rarer real one ("ofnausea", "tormentedby").
            return (2..word.length - 2).map { word.substring(0, it) to word.substring(it) }
                .filter { (left, right) ->
                    if (!isAttested(left, right)) return@filter false
                    val (common, other) = if (frequencyOf(left.lowercase()) >= frequencyOf(right.lowercase())) left to right else right to left
                    common.length <= GLUE_WORD_MAX_LENGTH && frequencyOf(common.lowercase()) >= GLUE_WORD_MIN_FREQUENCY &&
                        other.length >= GLUE_OTHER_MIN_LENGTH && isKnownWord(other)
                }
                .singleOrNull()
                ?.let { (left, right) -> "$left $right" }
        }

        private fun splitPartScore(part: String, frequencyOf: (String) -> Int): Int {
            val frequency = frequencyOf(part.lowercase())
            val minimum = if (part.length == 2) MIN_SPLIT_SHORT_PART_FREQUENCY else MIN_SPLIT_PART_FREQUENCY
            return if (frequency >= minimum) frequency else 0
        }

        /** Whether the text ends in an email address, link, hashtag or code name ("alex@domian", "#nofilter", "my_var"). */
        internal fun isInsideAddress(textBeforeCursor: String): Boolean {
            val chunk = textBeforeCursor.takeLastWhile { !it.isWhitespace() }
            return chunk.contains('@') || chunk.contains('/') || chunk.contains('#') || chunk.contains('_') ||
                chunk.startsWith("www.", ignoreCase = true)
        }

        /** How plausible [candidate] is as the intended word for [input] on a physical keyboard. */
        internal fun typoLikelihood(input: String, candidate: String, isNeighborKey: (Char, Char) -> Boolean): Double {
            val typed = input.lowercase()
            val word = candidate.lowercase()
            val substitution = singleSubstitution(typed, word)
            return when {
                hasSingleRepeatedCharInsertion(typed, word) || hasSingleRepeatedCharInsertion(word, typed) -> 1.0
                isAdjacentTransposition(typed, word) -> 0.9
                isSingleInsertion(typed, word) -> 0.8
                substitution != null && isNeighborKey(substitution.first, substitution.second) -> 0.8
                isSingleInsertion(word, typed) && isFatFingerInsertion(typed, word, isNeighborKey) -> 0.65
                isSingleInsertion(word, typed) -> 0.5
                substitution != null -> 0.4
                else -> 0.3
            }
        }

        /** [typed] has one extra letter on a key next to a letter beside it. */
        private fun isFatFingerInsertion(typed: String, word: String, isNeighborKey: (Char, Char) -> Boolean): Boolean {
            val i = word.indices.firstOrNull { word[it] != typed[it] } ?: word.length
            val extra = typed[i]
            return listOfNotNull(typed.getOrNull(i - 1), typed.getOrNull(i + 1))
                .any { beside -> isNeighborKey(extra.lowercaseChar(), beside.lowercaseChar()) }
        }

        /** A known word, or a capitalised name when [rootMayBeName], plus an English clitic ("waiter's"). The dictionary lists few of these, so they are kept as typed. */
        internal fun isWordWithClitic(word: String, isKnown: (String) -> Boolean, rootMayBeName: Boolean = false): Boolean {
            val normalized = word.replace('’', '\'')
            val apostrophe = normalized.lastIndexOf('\'')
            if (apostrophe <= 0 || normalized.substring(apostrophe + 1).lowercase() !in CLITICS) return false
            val root = normalized.substring(0, apostrophe)
            return root.all { it.isLetter() } && (rootMayBeName && root.first().isUpperCase() || isKnown(root))
        }

        /** [long] is [short] with exactly one extra character somewhere. */
        private fun isSingleInsertion(short: String, long: String): Boolean {
            if (long.length != short.length + 1) return false
            val i = short.indices.firstOrNull { short[it] != long[it] } ?: short.length
            return short.substring(i) == long.substring(i + 1)
        }

        private fun singleSubstitution(a: String, b: String): Pair<Char, Char>? {
            if (a.length != b.length) return null
            val diffs = a.indices.filter { a[it] != b[it] }
            return diffs.singleOrNull()?.let { a[it] to b[it] }
        }

        private fun isAdjacentTransposition(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            val diffs = a.indices.filter { a[it] != b[it] }
            return diffs.size == 2 && diffs[1] == diffs[0] + 1 && a[diffs[0]] == b[diffs[1]] && a[diffs[1]] == b[diffs[0]]
        }

        /** Optimal string alignment distance (a swap of neighbours counts as one edit). */
        internal fun editDistance(a: String, b: String): Int {
            val x = a.lowercase()
            val y = b.lowercase()
            val d = Array(x.length + 1) { IntArray(y.length + 1) }
            for (i in 0..x.length) d[i][0] = i
            for (j in 0..y.length) d[0][j] = j
            for (i in 1..x.length) {
                for (j in 1..y.length) {
                    val cost = if (x[i - 1] == y[j - 1]) 0 else 1
                    d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                    if (i > 1 && j > 1 && x[i - 1] == y[j - 2] && x[i - 2] == y[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
            return d[x.length][y.length]
        }

        /** Fixes Shift held too long ("THe" to "The"). Two capitals before a single letter ("IDs") are left alone unless the result is a very common word. */
        internal fun shiftHeldTooLongFix(word: String, frequencyOf: (String) -> Int): String? {
            if (word.length < 3 || !word.all { it.isLetter() }) return null
            if (!word[0].isUpperCase() || !word[1].isUpperCase()) return null
            val rest = word.substring(2)
            if (!rest.all { it.isLowerCase() }) return null
            val fixed = word[0] + word.substring(1).lowercase()
            val minimumFrequency = if (rest.length >= 2) 1 else FIRST_LETTER_FIX_MIN_FREQUENCY
            return fixed.takeIf { frequencyOf(it) >= minimumFrequency }
        }

        /** A known word plus a plural or verb "s" ("autocorrects", "keycaps"). Not after an "s" or a consonant + "y" ("wordss", "storys"), which are slips. */
        internal fun isPluralOfKnownWord(word: String, isKnown: (String) -> Boolean, frequencyOf: (String) -> Int = { 0 }): Boolean {
            if (word.length < PLURAL_MIN_LENGTH || !word.all { it in 'a'..'z' } || !word.endsWith('s')) return false
            val base = word.dropLast(1)
            if (base.endsWith('s')) return false
            if (base.endsWith('y') && base.getOrNull(base.length - 2)?.let { it !in "aeiou" } == true) return false
            return isKnown(base) && frequencyOf(base) < PLURAL_BASE_MAX_FREQUENCY
        }

        /** A lowercase letter followed by a capital marks a name ("iPhone"). */
        internal fun isCamelCaseName(word: String): Boolean = word.zipWithNext().any { (a, b) -> a.isLowerCase() && b.isUpperCase() }

        /** A word capitalised mid-sentence is treated as a name. */
        internal fun isCapitalizedName(word: String, textBeforeWord: String): Boolean {
            if (word.firstOrNull()?.isUpperCase() != true) return false
            val previous = textBeforeWord.trimEnd(' ').lastOrNull() ?: return false
            return previous != '\n' && previous !in ".!?"
        }

        /** The lowercase word before the one just typed, [PairCounts.SENTENCE_START] at a sentence start, or null after symbols. */
        internal fun previousWordFor(textBeforeWord: String): String? {
            val trimmed = textBeforeWord.trimEnd(' ')
            // After a comma or colon a new clause starts, much like a sentence ("Ok, ill call").
            if (trimmed.isEmpty() || trimmed.last() == '\n' || trimmed.last() in ".!?,;:") return PairCounts.SENTENCE_START
            if (trimmed.length == textBeforeWord.length) return null // Glued to something.
            val token = trimmed.takeLastWhile { it.isLetter() || it == '\'' || it == '’' }.replace('’', '\'')
            if (token.isEmpty() || token.length == trimmed.length && textBeforeWord.length >= PREVIOUS_WORD_LOOKBACK) {
                return null // Not a word, or possibly cut off.
            }
            val beforeToken = trimmed.getOrNull(trimmed.length - token.length - 1)
            return token.lowercase().takeIf { beforeToken == null || beforeToken.isWhitespace() }
        }

        /** A capitalised word at the start of a sentence, as opposed to a name mid-sentence. */
        internal fun isSentenceStartCapital(word: String, textBeforeWord: String): Boolean =
            word.first().isUpperCase() && word.drop(1).all { it.isLowerCase() } && !isCapitalizedName(word, textBeforeWord)

        internal fun hasSingleRepeatedCharInsertion(input: String, candidate: String): Boolean {
            if (candidate.length != input.length + 1) return false
            var i = 0
            var j = 0
            var insertedIndex = -1
            while (i < input.length && j < candidate.length) {
                if (input[i].equals(candidate[j], ignoreCase = true)) {
                    i++
                    j++
                } else {
                    if (insertedIndex != -1) return false
                    insertedIndex = j
                    j++
                }
            }
            if (insertedIndex == -1) insertedIndex = candidate.lastIndex
            val inserted = candidate.getOrNull(insertedIndex) ?: return false
            val prev = candidate.getOrNull(insertedIndex - 1)
            val next = candidate.getOrNull(insertedIndex + 1)
            return prev?.equals(inserted, ignoreCase = true) == true || next?.equals(inserted, ignoreCase = true) == true
        }
    }
}
