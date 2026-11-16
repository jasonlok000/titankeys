package com.titankeys.keyboard

/**
 * Korean: Hangul syllables built from jamo as they're typed, on the 2-set (Dubeolsik) layout over the Titan's QWERTY keys
 * (docs/languages.md, "Korean"). Shift types the tense consonants (ㅃ ㅉ ㄸ ㄲ ㅆ) and the two extra vowels (ㅒ ㅖ); on the
 * other keys it types what the key types without it.
 *
 * The syllable being built is the engine's code, on the bar as Rime's is; a syllable is written to the field once the next
 * jamo can't join it. Backspace takes the last jamo typed off (과 → 고 → ㄱ). Space, Enter, a mark or a digit writes the
 * syllable and then goes on as usual. No word list, so no choices.
 *
 * Plain Kotlin, checked on the JVM (tests/languages); [HangulEngine] is the keyboard's, made by [InputEngines].
 */
open class HangulComposer : InputEngine {
    /** The jamo typed for the syllable being built, in the order typed: the syllables before it are in the field. */
    private val typed = ArrayList<Char>()

    override fun start(method: InputMethod, dataDir: java.io.File): Boolean = true

    override fun key(char: Char): EngineState {
        val jamo = jamoOf(char) ?: return commitAndPass()
        typed += jamo
        val syllables = split(typed)
        if (syllables.size == 1) return EngineState(code = compose(syllables[0]))
        // The new jamo started a syllable: the ones before it are done.
        val done = syllables.dropLast(1).joinToString("") { compose(it) }
        typed.clear()
        typed += syllables.last()
        return EngineState(commit = done, code = compose(syllables.last()))
    }

    override fun space(): EngineState = commitAndPass()

    override fun enter(): EngineState = commitAndPass()

    override fun backspace(): EngineState {
        if (typed.isEmpty()) return EngineState.UNHANDLED
        typed.removeAt(typed.lastIndex)
        return EngineState(code = code())
    }

    override fun pick(index: Int): EngineState = EngineState.UNHANDLED

    override fun page(forward: Boolean): EngineState = EngineState.UNHANDLED

    override fun reset(): EngineState {
        typed.clear()
        return EngineState()
    }

    override fun option(name: String, on: Boolean) {}

    override fun stop() {
        typed.clear()
    }

    /** The syllable being built, as it shows. */
    fun code(): String = split(typed).joinToString("") { compose(it) }

    /** The syllable goes to the field, and the key that ended it goes on as usual (a space, a mark, Enter). */
    private fun commitAndPass(): EngineState {
        if (typed.isEmpty()) return EngineState.UNHANDLED
        val text = code()
        typed.clear()
        return EngineState(commit = text, handled = false)
    }

    companion object {
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
