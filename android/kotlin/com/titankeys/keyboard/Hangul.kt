package com.titankeys.keyboard

/**
 * Korean: Hangul syllables built from jamo as they're typed, on the 2-set (Dubeolsik) layout over the Titan's QWERTY keys
 * (docs/languages.md, "Korean"). Shift types the tense consonants (ㅃ ㅉ ㄸ ㄲ ㅆ) and the two extra vowels (ㅒ ㅖ); on the
 * other keys it types what the key types without it.
 *
 * The syllable being built is the engine's code, on the bar as Rime's is; a syllable is written to the field once the next
 * jamo can't join it. Backspace takes the last jamo typed off (과 → 고 → ㄱ). Space, Enter, a mark or a digit writes the
 * syllable and then goes on as usual. With a word list ([words]), the choices are the words that start with the word so far,
 * the syllables already written and the one being built, by its letters, so 하 offers 하고 and 한국. A choice writes the rest
 * of its word and a space: the syllables before are in the field already.
 *
 * Plain Kotlin, checked on the JVM (tests/languages); [HangulEngine] is the keyboard's, made by [InputEngines].
 */
open class HangulComposer(protected var words: WordList? = null) : InputEngine {
    /** The jamo typed for the syllable being built, in the order typed: the syllables before it are in the field. */
    private val typed = ArrayList<Char>()
    /** This word's syllables already written to the field, since the last space or mark. */
    private val written = StringBuilder()
    private var choices: List<String> = emptyList()

    override fun start(method: InputMethod, dataDir: java.io.File): Boolean = true

    override fun key(char: Char): EngineState {
        val jamo = jamoOf(char) ?: return commitAndPass()
        typed += jamo
        val syllables = split(typed)
        if (syllables.size == 1) return withChoices(EngineState(code = compose(syllables[0])))
        // The new jamo started a syllable: the ones before it are done.
        val done = syllables.dropLast(1).joinToString("") { compose(it) }
        typed.clear()
        typed += syllables.last()
        written.append(done)
        return withChoices(EngineState(commit = done, code = compose(syllables.last())))
    }

    /** [state] with the word list's words that start with the word so far, as its choices. */
    private fun withChoices(state: EngineState): EngineState {
        if (words == null) return state
        choices = choices(0, CHOICES)
        return state.copy(choices = choices)
    }

    /**
     * What the word so far may be, for matching: its syllables, the one being built by its letters; and where that one ends
     * in a consonant, also that consonant starting the next syllable, as a vowel after it would make it (안녕핫 or 안녕하ᄉ…, so
     * 안녕하세요 is offered before its ㅔ is typed).
     */
    private fun matchKeys(): List<String> {
        val start = written.toString()
        val keys = mutableListOf(start + matchKey())
        val last = typed.lastOrNull()
        if (typed.size >= 3 && last != null && !isVowel(last) && typed.any(::isVowel) && INITIALS.indexOf(last) >= 0)
            keys += start + split(typed.dropLast(1)).joinToString("") { compose(it) } + (0x1100 + INITIALS.indexOf(last)).toChar()
        return keys.filter { it.isNotEmpty() }
    }

    /**
     * The syllable being built, for matching: its letters as a word's syllables split into them (NFD). A lone initial, as ㅎ,
     * is a compatibility letter that doesn't split, so it becomes the initial ᄒ that 하 and 한 start with.
     */
    private fun matchKey(): String {
        val code = code()
        if (code.length == 1 && INITIALS.indexOf(code[0]) >= 0) return (0x1100 + INITIALS.indexOf(code[0])).toChar().toString()
        return code
    }

    override fun space(): EngineState = commitAndPass()

    override fun enter(): EngineState = commitAndPass()

    override fun backspace(): EngineState {
        if (typed.isEmpty()) {
            // The field deletes its last syllable, one of this word's.
            if (written.isNotEmpty()) written.setLength(written.length - 1)
            choices = emptyList()
            return EngineState.UNHANDLED
        }
        typed.removeAt(typed.lastIndex)
        return withChoices(EngineState(code = code()))
    }

    /** A choice: the rest of its word, after the syllables already written, and a space. */
    override fun pick(index: Int): EngineState = pickWord(choices.getOrNull(index))

    /**
     * The row's and the bigger menu's words (a swipe up opens it, as in Telex and Hindi): first the word as typed, which is
     * what Space writes, so the bar shows what Space types (owner, #98); then the word list's words for the word so far, in
     * the same order. A pick of the word as typed writes it as Space does, with the space.
     */
    override fun choices(start: Int, count: Int): List<String> {
        val list = words ?: return emptyList()
        if (start < 0 || count <= 0) return emptyList()
        val whole = written.toString() + code()
        val typedFirst = if (whole.isEmpty()) emptyList() else listOf(whole)
        val listed = matchKeys().flatMap { list.complete(it, start + count + 1) }.distinct().filter { it != whole }
        return (typedFirst + listed).drop(start).take(count)
    }

    /** A pick from the bar or the bigger menu, [index] counted across all of [choices]. */
    override fun pickAt(index: Int): EngineState = pickWord(choices(index, 1).firstOrNull())

    private fun pickWord(word: String?): EngineState {
        if (word == null) return EngineState.UNHANDLED
        val rest = if (word.startsWith(written)) word.substring(written.length) else word
        typed.clear()
        written.setLength(0)
        choices = emptyList()
        return EngineState(commit = "$rest ")
    }

    override fun page(forward: Boolean): EngineState = EngineState.UNHANDLED

    override fun reset(): EngineState {
        typed.clear()
        written.setLength(0)
        choices = emptyList()
        return EngineState()
    }

    override fun option(name: String, on: Boolean) {}

    override fun stop() {
        reset()
    }

    /** The syllable being built, as it shows. */
    fun code(): String = split(typed).joinToString("") { compose(it) }

    /** The syllable goes to the field, and the key that ended it goes on as usual (a space, a mark, Enter). */
    private fun commitAndPass(): EngineState {
        written.setLength(0)
        choices = emptyList()
        if (typed.isEmpty()) return EngineState.UNHANDLED
        val text = code()
        typed.clear()
        return EngineState(commit = text, handled = false)
    }

    companion object {
        /** The most choices asked of the word list, one for each key over W to O. */
        const val CHOICES = 8

        /** The 2-set layout: each key's jamo, then its jamo with Shift where that differs. */
        private val PLAIN = mapOf(
            'q' to 'ㅂ', 'w' to 'ㅈ', 'e' to 'ㄷ', 'r' to 'ㄱ', 't' to 'ㅅ', 'y' to 'ㅛ', 'u' to 'ㅕ', 'i' to 'ㅑ', 'o' to 'ㅐ', 'p' to 'ㅔ',
            'a' to 'ㅁ', 's' to 'ㄴ', 'd' to 'ㅇ', 'f' to 'ㄹ', 'g' to 'ㅎ', 'h' to 'ㅗ', 'j' to 'ㅓ', 'k' to 'ㅏ', 'l' to 'ㅣ',
            'z' to 'ㅋ', 'x' to 'ㅌ', 'c' to 'ㅊ', 'v' to 'ㅍ', 'b' to 'ㅠ', 'n' to 'ㅜ', 'm' to 'ㅡ',
        )
        private val SHIFTED = mapOf('q' to 'ㅃ', 'w' to 'ㅉ', 'e' to 'ㄸ', 'r' to 'ㄲ', 't' to 'ㅆ', 'o' to 'ㅒ', 'p' to 'ㅖ')

        /** Initial consonants, in Unicode's syllable order. */
        private const val INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        /** Vowels, in Unicode's syllable order (the compatibility jamo ㅏ to ㅣ run in the same order). */
        private const val VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
        /** Final consonants, in Unicode's syllable order, after "none". */
        private const val FINALS = "\u0000ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

        private val VOWEL_PAIRS = mapOf("ㅗㅏ" to 'ㅘ', "ㅗㅐ" to 'ㅙ', "ㅗㅣ" to 'ㅚ', "ㅜㅓ" to 'ㅝ', "ㅜㅔ" to 'ㅞ', "ㅜㅣ" to 'ㅟ', "ㅡㅣ" to 'ㅢ')
        private val FINAL_PAIRS = mapOf("ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ', "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ', "ㄹㅂ" to 'ㄼ',
            "ㄹㅅ" to 'ㄽ', "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ')

        /** The jamo key [char] types (a capital is the key with Shift), or null for anything that isn't a letter key. */
        fun jamoOf(char: Char): Char? {
            if (char !in 'a'..'z' && char !in 'A'..'Z') return null
            val key = char.lowercaseChar()
            return (if (char.isUpperCase()) SHIFTED[key] else null) ?: PLAIN[key]
        }

        private fun isVowel(jamo: Char) = jamo in VOWELS

        /**
         * Splits typed jamo into syllables, each the jamo typed for it: an initial, one or two vowels, and up to two finals.
         * A vowel after a final takes that final (or the second of two) as its own initial, as 갑 + ㅏ is 가바.
         */
        fun split(jamo: List<Char>): List<List<Char>> {
            val out = ArrayList<MutableList<Char>>()
            var initial: Char? = null
            val vowels = ArrayList<Char>()
            val finals = ArrayList<Char>()
            fun close() {
                if (initial == null && vowels.isEmpty()) return
                out += (listOfNotNull(initial) + vowels + finals).toMutableList()
                initial = null
                vowels.clear()
                finals.clear()
            }
            for (j in jamo) {
                if (!isVowel(j)) {
                    when {
                        initial == null && vowels.isEmpty() -> initial = j
                        vowels.isEmpty() || initial == null -> { close(); initial = j }
                        finals.isEmpty() && FINALS.indexOf(j) > 0 -> finals += j
                        finals.size == 1 && "${finals[0]}$j" in FINAL_PAIRS -> finals += j
                        else -> { close(); initial = j }
                    }
                } else {
                    when {
                        initial != null && vowels.isEmpty() -> vowels += j
                        initial == null && vowels.isEmpty() -> vowels += j
                        finals.isEmpty() && vowels.size == 1 && "${vowels[0]}$j" in VOWEL_PAIRS -> vowels += j
                        finals.isEmpty() -> { close(); vowels += j }
                        else -> {
                            val moved = finals.removeAt(finals.lastIndex)
                            close()
                            initial = moved
                            vowels += j
                        }
                    }
                }
            }
            close()
            return out
        }

        /** One syllable's jamo as it's written: a whole syllable, or a lone jamo while it has no initial and vowel both. */
        fun compose(jamo: List<Char>): String {
            val initial = jamo.firstOrNull()?.takeIf { !isVowel(it) }
            val rest = if (initial != null) jamo.drop(1) else jamo
            val vowelPart = rest.takeWhile { isVowel(it) }
            val finalPart = rest.drop(vowelPart.size)
            val vowel = when (vowelPart.size) {
                0 -> null
                1 -> vowelPart[0]
                else -> VOWEL_PAIRS["${vowelPart[0]}${vowelPart[1]}"]
            }
            val final = when (finalPart.size) {
                0 -> null
                1 -> finalPart[0]
                else -> FINAL_PAIRS["${finalPart[0]}${finalPart[1]}"]
            }
            if (initial == null || vowel == null) return listOfNotNull(initial, vowel, final).joinToString("")
            val code = 0xAC00 + (INITIALS.indexOf(initial) * 21 + VOWELS.indexOf(vowel)) * 28 + (final?.let { FINALS.indexOf(it) } ?: 0)
            return code.toChar().toString()
        }
    }
}
