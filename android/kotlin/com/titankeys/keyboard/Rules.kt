package com.titankeys.keyboard

import com.titankeys.connected.Json

/**
 * Transliteration by rules, as Wikimedia's jquery.ime does it (https://github.com/wikimedia/jquery.ime): Hindi first, and
 * any other script from its own rule file (docs/languages.md, "Hindi").
 *
 * A rule file lists patterns in order. Each key typed is added to the last [Rules.maxKeyLength] letters of the word so far;
 * the first pattern that matches the end of that, and whose context pattern (if any) matches the end of the keys typed so far
 * ([Rules.contextLength] of them), rewrites it. namaste gives नमस्ते.
 */
class Rules(val maxKeyLength: Int, val contextLength: Int, private val patterns: List<Rule>) {
    class Rule(val input: Regex, val context: Regex?, val replacement: String)

    /** The word [keys] spell, typed one after another from nothing. */
    fun spell(keys: CharSequence): String {
        var state = State("", "")
        for (key in keys) state = step(state, key)
        return state.text
    }

    /** The text so far and the last keys typed, which a rule's context looks at. */
    data class State(val text: String, val context: String)

    /** [state] after one more [key]. */
    fun step(state: State, key: Char): State {
        val start = maxOf(0, state.text.length - maxKeyLength)
        val output = apply(state.text.substring(start) + key, state.context)
        return State(state.text.substring(0, start) + output, (state.context + key).takeLast(contextLength))
    }

    /** Whether [key] after [before] is taken by a rule, rather than typing itself. */
    fun changes(before: String, key: Char): Boolean {
        val input = before.takeLast(maxKeyLength) + key
        return apply(input, "") != input
    }

    private fun apply(input: String, context: CharSequence): String {
        for (rule in patterns) {
            val match = rule.input.find(input) ?: continue
            if (rule.context != null && rule.context.find(context) == null) continue
            return input.substring(0, match.range.first) + expand(rule.replacement, match)
        }
        return input
    }

    /** The replacement with JavaScript's $1 to $9 filled in from [match]. */
    private fun expand(replacement: String, match: MatchResult): String {
        if ('$' !in replacement) return replacement
        val out = StringBuilder()
        var i = 0
        while (i < replacement.length) {
            val c = replacement[i]
            if (c == '$' && i + 1 < replacement.length && replacement[i + 1].isDigit()) {
                out.append(match.groupValues.getOrNull(replacement[i + 1] - '0').orEmpty())
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    companion object {
        /**
         * Reads a rule file: `{"maxKeyLength": 4, "contextLength": 2, "patterns": [[input, replacement] or [input, context,
         * replacement], ...]}`, as tools/im/make.py writes it from jquery.ime's own. Each pattern matches at the end.
         */
        fun parse(text: String): Rules {
            val root = Json.parse(text) as? Map<*, *> ?: throw Json.Error("a rule file must be an object")
            val patterns = (root["patterns"] as? List<*> ?: throw Json.Error("patterns must be a list")).map {
                val parts = (it as? List<*>)?.map { part -> part as? String ?: throw Json.Error("a pattern must list text") }
                    ?: throw Json.Error("a pattern must be a list")
                when (parts.size) {
                    2 -> Rule(Regex("(?:${parts[0]})$"), null, parts[1])
                    3 -> Rule(Regex("(?:${parts[0]})$"), Regex("(?:${parts[1]})$"), parts[2])
                    else -> throw Json.Error("a pattern has 2 or 3 parts")
                }
            }
            return Rules((root["maxKeyLength"] as? Number)?.toInt() ?: 4, (root["contextLength"] as? Number)?.toInt() ?: 0, patterns)
        }
    }
}

/**
 * An input method typed through [Rules] (Hindi). The word being built is the engine's code, on the bar, worked out again from
 * the keys typed, so Backspace takes the last key off. Its word list's words that start with it are the choices, picked with
 * the finger and Space; Space alone writes the word as typed, as Telex does. A key no rule takes, after a letter, ends the
 * word and goes on as usual. Plain Kotlin, checked on the JVM; [RulesEngine] is the keyboard's.
 */
open class RulesComposer(protected var rules: Rules? = null, protected var words: WordList? = null) : InputEngine {
    private val typed = StringBuilder()
    private var choices: List<String> = emptyList()

    override fun start(method: InputMethod, dataDir: java.io.File): Boolean = rules != null

    override fun key(char: Char): EngineState {
        val rules = rules ?: return EngineState.UNHANDLED
        // Letters always go to the rules; anything else only where a rule takes it (Hindi's . after a letter, for one).
        if (!char.isLetter() && !(typed.isNotEmpty() && rules.changes(code(), char))) return commitAndPass()
        typed.append(char)
        return state()
    }

    /** Space goes through the rules too, as in jquery.ime: a word's last virama goes (agar is अगर, agar~ is अगर्). */
    override fun space(): EngineState {
        val rules = rules
        if (typed.isEmpty() || rules == null) return EngineState.UNHANDLED
        val text = rules.spell(typed.toString() + " ")
        typed.setLength(0)
        choices = emptyList()
        return EngineState(commit = text)
    }

    /** Enter writes the word as Space would, without the space, and then goes on as usual. */
    override fun enter(): EngineState {
        val rules = rules
        if (typed.isEmpty() || rules == null) return EngineState.UNHANDLED
        val text = rules.spell(typed.toString() + " ").removeSuffix(" ")
        typed.setLength(0)
        choices = emptyList()
        return EngineState(commit = text, handled = false)
    }

    override fun backspace(): EngineState {
        if (typed.isEmpty()) return EngineState.UNHANDLED
        typed.setLength(typed.length - 1)
        return state()
    }

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
    fun code(): String = rules?.spell(typed).orEmpty()

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
    }
}
