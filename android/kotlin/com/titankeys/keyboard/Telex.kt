package com.titankeys.keyboard

import java.text.Normalizer
import java.util.Locale

/**
 * Vietnamese with Telex: letters typed twice or followed by w take their marks, and a tone key after the vowel puts its tone
 * on the right vowel (docs/languages.md, "Vietnamese").
 *
 * - aa â, ee ê, oo ô; aw ă, ow ơ, uw ư (uow ươ; a w with no vowel before it is ư); dd đ.
 * - s f r x j: acute, grave, hook, tilde and dot below, on the vowel the spelling puts it on; z takes the tone off.
 * - The same key again undoes it and types the key: "ass" is "as", "aaa" is "aa", "ddd" is "dd".
 *
 * The word being typed is the engine's code, on the bar; the keys typed are kept, and the word is worked out from them each
 * time, so Backspace takes the last key off (tiếng → tiêng). Space, Enter, a mark or a digit writes the word and goes on as
 * usual. With a word list, the choices are its words that are the code without their marks or start with it (tieng → tiếng),
 * picked as English's are, with the finger and Space. Plain Kotlin, checked on the JVM; [TelexEngine] is the keyboard's.
 */
open class TelexComposer(protected var words: WordList? = null) : InputEngine {
    /** The keys typed for the word being built, as typed. */
    private val typed = StringBuilder()
    private var choices: List<String> = emptyList()

    override fun start(method: InputMethod, dataDir: java.io.File): Boolean = true

    override fun key(char: Char): EngineState {
        if (char !in 'a'..'z' && char !in 'A'..'Z') return commitAndPass()
        typed.append(char)
        return state()
    }

    override fun space(): EngineState = commitAndPass()

    override fun enter(): EngineState = commitAndPass()

    override fun backspace(): EngineState {
        if (typed.isEmpty()) return EngineState.UNHANDLED
        typed.setLength(typed.length - 1)
        return state()
    }

    /** A choice from the word list, with the space after it that Space would have typed. */
    override fun pick(index: Int): EngineState {
        val word = choices.getOrNull(index) ?: return EngineState.UNHANDLED
        typed.setLength(0)
        choices = emptyList()
        return EngineState(commit = "$word ")
    }

    override fun page(forward: Boolean): EngineState = EngineState.UNHANDLED

    override fun reset(): EngineState {
        typed.setLength(0)
        choices = emptyList()
        return EngineState()
    }

    override fun option(name: String, on: Boolean) {}

    override fun stop() {
        reset()
    }

    /** The word being built, as it shows. */
    fun code(): String = spell(typed)

    private fun state(): EngineState {
        val code = code()
        choices = if (code.isEmpty()) emptyList() else words?.complete(code, CHOICES)?.filter { it != code }.orEmpty()
        return EngineState(code = code, choices = choices)
    }

    private fun commitAndPass(): EngineState {
        if (typed.isEmpty()) return EngineState.UNHANDLED
        val text = code()
        typed.setLength(0)
        choices = emptyList()
        return EngineState(commit = text, handled = false)
    }

    companion object {
        const val CHOICES = 8
        private val VI = Locale.forLanguageTag("vi")
        /** The tone keys and their marks: acute, grave, hook above, tilde, dot below. */
        private val TONES = mapOf('s' to '́', 'f' to '̀', 'r' to '̉', 'x' to '̃', 'j' to '̣')
        private const val VOWELS = "aăâeêioôơuưy"
        /** What doubling or w makes of a plain vowel, and back. */
        private val HAT = mapOf('a' to 'â', 'e' to 'ê', 'o' to 'ô')
        private val HORN = mapOf('a' to 'ă', 'o' to 'ơ', 'u' to 'ư')
        private val PLAIN = mapOf('â' to 'a', 'ê' to 'e', 'ô' to 'o', 'ă' to 'a', 'ơ' to 'o', 'ư' to 'u')

        private fun base(c: Char) = c.lowercaseChar()
        private fun isVowel(c: Char) = base(c) in VOWELS
        private fun withCase(c: Char, like: Char) = if (like.isUpperCase()) c.uppercaseChar() else c

        /** The word Telex keys [keys] spell. */
        fun spell(keys: CharSequence): String {
            val out = StringBuilder()
            var tone: Char? = null
            // A key that just undid its own mark types itself from then on, as "ass" leaves "as" and a third s stays an s.
            val undone = HashSet<Char>()
            for (key in keys) {
                val k = key.lowercaseChar()
                val hasVowel = out.any { isVowel(it) }
                when {
                    k in TONES && hasVowel && k !in undone -> {
                        if (tone == TONES[k]) {
                            tone = null
                            undone += k
                            out.append(key)
                        } else tone = TONES[k]
                    }
                    k == 'z' && tone != null -> tone = null
                    k in HAT.keys && k !in undone && hatTarget(out, k) >= 0 -> {
                        val at = hatTarget(out, k)
                        val c = base(out[at])
                        if (c == HAT[k]) {
                            out[at] = withCase(k, out[at])
                            undone += k
                            out.append(key)
                        } else out[at] = withCase(HAT.getValue(k), out[at])
                    }
                    k == 'w' && 'w' !in undone -> when (horn(out)) {
                        Horn.ADDED -> {}
                        Horn.TAKEN_OFF -> {
                            undone += 'w'
                            out.append(key)
                        }
                        Horn.NONE -> out.append(if (hasVowel) key else withCase('ư', key))
                    }
                    k == 'd' && 'd' !in undone && out.isNotEmpty() && base(out.last()) == 'd' -> out[out.lastIndex] = withCase('đ', out.last())
                    k == 'd' && 'd' !in undone && out.isNotEmpty() && base(out.last()) == 'đ' -> {
                        out[out.lastIndex] = withCase('d', out.last())
                        undone += 'd'
                        out.append(key)
                    }
                    else -> out.append(key)
                }
            }
            // ươ always has a letter after it (người, đường); at the end of a word it's uơ (thuở, huơ), so w after uo makes ơ alone
            // until the next letter shows which.
            if (out.length >= 2 && base(out[out.lastIndex]) == 'ơ' && base(out[out.lastIndex - 1]) == 'ư') {
                out[out.lastIndex - 1] = withCase('u', out[out.lastIndex - 1])
            }
            val word = out.toString()
            val mark = tone ?: return word
            val at = toneAt(word)
            if (at < 0) return word
            return Normalizer.normalize(word.substring(0, at + 1) + mark + word.substring(at + 1), Normalizer.Form.NFC)
        }

        /**
         * The vowel a doubled [k] marks: the word's last vowel, with only consonants after it, as tieen and tienge both make
         * tiên(g). It's [k] itself, [k] with its hat already (to undo), or ă for a doubled a. -1 for none.
         */
        private fun hatTarget(out: CharSequence, k: Char): Int {
            val at = out.indexOfLast { isVowel(it) }
            if (at < 0) return -1
            val c = base(out[at])
            return if (c == k || c == HAT[k] || k == 'a' && c == 'ă') at else -1
        }

        private enum class Horn { ADDED, TAKEN_OFF, NONE }

        /** w's horn or breve on the word in [out]: uo becomes ươ, else the last vowel if it's a, o or u; or taken off again. */
        private fun horn(out: StringBuilder): Horn {
            val at = out.indexOfLast { isVowel(it) }
            if (at < 0) return Horn.NONE
            if (at > 0 && base(out[at - 1]) == 'u' && base(out[at]) == 'o') {
                out[at - 1] = withCase('ư', out[at - 1])
                out[at] = withCase('ơ', out[at])
                return Horn.ADDED
            }
            val c = base(out[at])
            HORN[c]?.let {
                out[at] = withCase(it, out[at])
                return Horn.ADDED
            }
            if (c != 'ă' && c != 'ơ' && c != 'ư') return Horn.NONE
            out[at] = withCase(PLAIN.getValue(c), out[at])
            if (c == 'ơ' && at > 0 && base(out[at - 1]) == 'ư') out[at - 1] = withCase('u', out[at - 1])
            return Horn.TAKEN_OFF
        }

        /**
         * Where the tone goes in [word]: on a vowel with a hat or horn (the ơ of ươ), else on the only vowel, else on the last
         * vowel before a final consonant, else in an open syllable on the middle of three vowels or the first of two, the
         * older style that most Vietnamese write (hòa, thúy). The u of qu and the i of gi before another vowel are the
         * consonant's. -1 with no vowel.
         */
        fun toneAt(word: String): Int {
            val lower = word.lowercase(VI)
            var start = lower.indexOfFirst { it in VOWELS }
            if (start < 0) return -1
            var end = start
            while (end + 1 < lower.length && lower[end + 1] in VOWELS) end++
            if (start > 0 && end > start && (lower[start - 1] == 'q' && lower[start] == 'u' || lower[start - 1] == 'g' && lower[start] == 'i')) start++
            val nucleus = (start..end)
            nucleus.lastOrNull { lower[it] == 'ơ' }?.let { return it }
            nucleus.firstOrNull { lower[it] in "ăâêôư" }?.let { return it }
            if (start == end) return start
            val closed = end + 1 < lower.length
            if (closed) return end
            return if (end - start >= 2) start + 1 else start
        }
    }
}
