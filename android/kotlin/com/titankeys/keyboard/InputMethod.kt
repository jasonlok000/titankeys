package com.titankeys.keyboard

import com.titankeys.connected.Json
import java.util.Locale

/**
 * One way of typing: English, Spanish, Russian, Cantonese in Yale... Each lives in `android/assets/im/<id>/im.json`, with its
 * word list beside it (docs/languages.md, "The input method file"). English is built in and keeps all its own code; it only
 * borrows this shape for its name, label and locale.
 *
 * Nothing here needs Android, so tests/languages checks it on the JVM.
 */
data class InputMethod(
    /** The folder's name, a lower-case BCP 47 tag: `es`, `pt`, `sr-latn`, `yue-yale`. */
    val id: String,
    /** Its own name, as in the overlay and Settings: Español. */
    val name: String,
    /** Its English name, for Settings' second line: Spanish. */
    val nameEnglish: String,
    /** Two or three characters for the overlay: ES, RU, 粵. */
    val label: String,
    /** Android's locale for its keyboard-list entry and for capitals (Turkish's i and İ). */
    val locale: String,
    /** ISO 15924: Latn, Cyrl, Grek, Geor, Hani. */
    val script: String,
    /**
     * What each of the 26 keys types, by the letter printed on it (lower case), or null where the keys type what they show,
     * as in every Latin-script input method.
     */
    val keys: Map<Char, KeyLetters>?,
    /** The letters held on each key, by the letter printed on it, in the order they show on the bar. */
    val accents: Map<Char, List<String>>,
    /** The word list's file in the input method's folder, or null. */
    val words: String?,
    /** Spellings with their own word lists, such as Portuguese's Brazil and Portugal, or none. */
    val variants: List<Variant>,
    val rules: Rules,
    /** The engine's name (`rime`), or null for an input method whose keys type letters straight in. */
    val engine: String?,
    /** What the engine needs to know: Rime's schema, for one. */
    val engineArg: String?,
    /** Where the letters and the words come from, for the licences page and for contributors. */
    val source: Map<String, String>,
    /**
     * Its commonest words, commonest first, for the bar at a sentence's start and after a word until the user's own take over
     * (owner, 2026-11-16: "each language should have a list of starting words"). tools/im/make.py writes them.
     */
    val starters: List<String> = emptyList(),
    /** Chinese's [starters] in Simplified characters, for the Simplified variant. Empty elsewhere. */
    val startersSimplified: List<String> = emptyList(),
    /** A few lines on what's special to typing it, for Settings > Languages (tools/im/howto.json). Empty where it has none. */
    val howTo: List<String> = emptyList(),
    /** Printed, shifted and held legends from the same guide as the language settings. */
    val previewKeys: Map<*, *>? = null,
) {
    /**
     * What a key types: [base], and [shift] where Shift gives another letter rather than [base]'s capital (Georgian). A
     * layout with more keys than the Titan's (Thai's Kedmanee) puts the rest on a third layer: holding the key types [held],
     * and with Shift, [heldShift].
     */
    data class KeyLetters(val base: String, val shift: String? = null, val held: String? = null, val heldShift: String? = null)

    data class Variant(val id: String, val name: String, val words: String?)

    data class Rules(
        /**
         * The script has capitals: one after a sentence ends, and Shift types a key's capital. Georgian has none, so Shift
         * types a key's Shift letter or its own letter, never Mtavruli.
         */
        val capitalise: Boolean = true,
        /** The marks that end a sentence. */
        val sentenceEnd: String = ".?!",
        /** Marks that open a sentence, as Spanish's ¿ and ¡. */
        val openingMarks: String = "",
        /** Whether an apostrophe can be part of a word (French: l'eau). */
        val apostrophes: Boolean = true,
        /** The script writes no spaces between words (Thai): the word being typed is found in the word list ([WordBreak]). */
        val wordBreak: Boolean = false,
        /**
         * What a quick second Space types in place of the first: the language's full stop, with the space after it when the
         * language puts one ("। " in Hindi), or none (Chinese and Japanese: "。"). Empty where the script has no full stop
         * (Thai): two Spaces stay two spaces. The Sentence spaces setting doubles a full stop's space.
         */
        val fullStop: String = FULL_STOP,
    )

    val isEnglish get() = id == ENGLISH_ID

    /**
     * Whether Shift changes what a key types, so the bar shows Shift and Caps (owner, 2026-11-17: "if shift does nth, shift
     * shd not show up in the first place"): capitals, Shift letters (Georgian, Thai), or an engine that reads Shift (Korean's
     * double consonants, Hindi's sounds, Vietnamese's capitals). Not in Hebrew, where Shift types the same letter, nor in
     * Chinese and Japanese, where Shift opens the bigger menu.
     */
    val shiftMatters: Boolean get() = when (engine) {
        "rime", "mozc" -> false
        "hangul", "rules", "telex" -> true
        else -> rules.capitalise || keys?.values?.any { it.shift != null && it.shift != it.base } == true
    }
    /** Hebrew, Arabic: the bar's side words swap ([BarOrder]). */
    val rightToLeft get() = script in RightToLeft.SCRIPTS
    val hasAccents get() = accents.isNotEmpty()
    val javaLocale: Locale get() = Locale.forLanguageTag(locale)

    /**
     * What the key printed [printed] (a to z, any case) types, with Shift on or off: its own letter, upper-cased in the
     * input method's language, or the letter Shift gives it. Null for a key this input method doesn't change.
     */
    fun letter(printed: Char, shift: Boolean): String? {
        val key = keys?.get(printed.lowercaseChar()) ?: return null
        if (!shift) return key.base
        return key.shift ?: if (rules.capitalise) key.base.uppercase(javaLocale) else key.base
    }

    /** What holding the key printed [printed] types on a third layer (Thai), with or without Shift, or null. */
    fun held(printed: Char, shift: Boolean): String? {
        val key = keys?.get(printed.lowercaseChar()) ?: return null
        return if (shift) key.heldShift ?: key.held else key.held
    }

    val keyPreviewDefault: Boolean get() = mappedPreview(previewKeys, script)

    /** Returns the legend only. Typing and the input engine do not receive this value. */
    fun keyPreview(printed: Char, shift: Boolean, hold: Boolean = false): String {
        val key = printed.lowercaseChar()
        if (key !in 'a'..'z') return ""
        fun legend(layer: String) = (previewKeys?.get(layer) as? Map<*, *>)?.get(key.toString()) as? String
        if (hold) return (if (shift) legend("heldShift") else null)
            ?: legend("held") ?: held(key, shift) ?: accentsOf(key, shift).firstOrNull().orEmpty()
        if (shift) legend("shift")?.let { return it }
        val base = legend("map")
        if (base != null) return if (shift && previewKeys?.get("capitals") == true) base.uppercase(javaLocale) else base
        if (id == "zh-zhuyin") return ""
        return letter(key, shift) ?: if (shift && shiftMatters) key.toString().uppercase(javaLocale) else key.toString()
    }

    /** The letters held on the key printed [printed], in capitals with [shift], or none. */
    fun accentsOf(printed: Char, shift: Boolean): List<String> {
        val held = accents[printed.lowercaseChar()] ?: return emptyList()
        return if (shift && rules.capitalise) held.map { it.uppercase(javaLocale) } else held
    }

    companion object {
        const val ENGLISH_ID = "en"
        /** The full stop a double space types unless an input method says otherwise ([Rules.fullStop]). */
        const val FULL_STOP = ". "
        /**
         * The most languages on, and the most input methods on within one language (owner, 2026-11-17: "Don't allow selecting
         * more than 4"). No other total: four languages of four each can be on. Settings refuses a fifth; a setting that has
         * more (a restore, an old one) keeps them, and the switcher shows the first four of each.
         */
        const val MAX_LANGUAGES = 4
        const val MAX_PER_LANGUAGE = 4
        /** The longest label the overlay can show over its key. */
        const val MAX_LABEL = 3
        /** The most accents on one key: one per key column. */
        const val MAX_ACCENTS = 10

        /** English, built in: it has no file of its own. */
        val ENGLISH = InputMethod(ENGLISH_ID, "English", "English", "EN", "en", "Latn", null, emptyMap(), null,
            emptyList(), Rules(), null, null, emptyMap())

        /** Reads an `im.json`. Throws [Json.Error] with the reason when the file is wrong. */
        fun parse(text: String): InputMethod {
            val root = Json.parse(text) as? Map<*, *> ?: throw Json.Error("im.json must be an object")
            fun text(key: String) = root[key] as? String ?: throw Json.Error("$key must be text")
            fun textOrNull(key: String) = when (val value = root[key]) {
                null -> null
                is String -> value
                else -> throw Json.Error("$key must be text or null")
            }
            val id = text("id")
            if (!ID.matches(id)) throw Json.Error("id must be a lower-case tag such as es or sr-latn")
            val label = text("label")
            if (label.codePointCount(0, label.length) !in 1..MAX_LABEL) throw Json.Error("label must be 1 to $MAX_LABEL characters")
            val keys = when (val value = root["keys"]) {
                "qwerty", null -> null
                is Map<*, *> -> value.entries.associate { (key, letters) ->
                    val printed = printedKey(key)
                    // "a" or "a/shifted", or a list, [base, shift, held, held with Shift], where a layout needs "/" (Thai).
                    val parts = when (letters) {
                        is String -> letters.split('/').also { if (it.size > 2) throw Json.Error("keys.$printed must be a letter, or letter/shifted") }
                        is List<*> -> letters.map { it as? String ?: throw Json.Error("keys.$printed must list text") }
                        else -> throw Json.Error("keys.$printed must be text")
                    }
                    if (parts.size !in 1..4 || parts.any { it.isEmpty() }) throw Json.Error("keys.$printed must have 1 to 4 letters")
                    printed to KeyLetters(parts[0], parts.getOrNull(1), parts.getOrNull(2), parts.getOrNull(3))
                }
                else -> throw Json.Error("keys must be \"qwerty\" or a map of keys")
            }
            val accents = when (val value = root["accents"]) {
                null -> emptyMap()
                is Map<*, *> -> value.entries.associate { (key, held) ->
                    val printed = printedKey(key)
                    val letters = graphemes(held as? String ?: throw Json.Error("accents.$printed must be text"))
                    if (letters.isEmpty() || letters.size > MAX_ACCENTS) throw Json.Error("accents.$printed must hold 1 to $MAX_ACCENTS letters")
                    printed to letters
                }
                else -> throw Json.Error("accents must be a map of keys")
            }
            val variants = when (val value = root["variants"]) {
                null -> emptyList()
                is List<*> -> value.map {
                    val variant = it as? Map<*, *> ?: throw Json.Error("each variant must be an object")
                    Variant(variant["id"] as? String ?: throw Json.Error("a variant needs an id"),
                        variant["name"] as? String ?: throw Json.Error("a variant needs a name"), variant["words"] as? String)
                }
                else -> throw Json.Error("variants must be a list or null")
            }
            val rules = (root["rules"] as? Map<*, *>)?.let {
                Rules(it["capitalise"] as? Boolean ?: true, it["sentenceEnd"] as? String ?: ".?!", it["openingMarks"] as? String ?: "",
                    it["apostrophes"] as? Boolean ?: true, it["wordBreak"] as? Boolean ?: false, it["fullStop"] as? String ?: FULL_STOP)
            } ?: Rules()
            val (engine, engineArg) = when (val value = root["engine"]) {
                null -> null to null
                is String -> value to null
                is Map<*, *> -> {
                    val entry = value.entries.singleOrNull() ?: throw Json.Error("engine must name one engine")
                    entry.key as String to (entry.value as? String)
                }
                else -> throw Json.Error("engine must be null, a name, or { name: argument }")
            }
            val source = (root["source"] as? Map<*, *>)?.entries?.associate { (k, v) -> k as String to v.toString() } ?: emptyMap()
            fun list(key: String) = (root[key] as? List<*>)?.map { it as? String ?: throw Json.Error("$key must list text") }.orEmpty()
            val preview = (root["guide"] as? Map<*, *>)?.get("keys") as? Map<*, *>
            return InputMethod(id, text("name"), text("nameEnglish"), label, text("locale"), text("script"), keys, accents,
                textOrNull("words"), variants, rules, engine, engineArg, source, list("starters"), list("startersSimplified"), list("howTo"), preview)
        }

        fun mappedPreview(guideKeys: Map<*, *>?, script: String): Boolean {
            if (script == "Latn") return false
            val map = guideKeys?.get("map") as? Map<*, *> ?: return false
            return map.any { (printed, output) -> output is String && output.isNotEmpty() && output != printed }
        }

        private val ID = Regex("[a-z]{2,3}(-[a-z0-9]{2,8})*")

        private fun printedKey(key: Any?): Char {
            val text = key as? String
            if (text == null || text.length != 1 || text[0] !in 'a'..'z') throw Json.Error("\"$key\" is not a key: use a to z")
            return text[0]
        }

        /** Splits text into what reads as single letters, so a letter with a combining mark stays whole. */
        fun graphemes(text: String): List<String> {
            val out = ArrayList<String>()
            val iterator = java.text.BreakIterator.getCharacterInstance(Locale.ROOT)
            iterator.setText(text)
            var start = iterator.first()
            var end = iterator.next()
            while (end != java.text.BreakIterator.DONE) {
                val piece = text.substring(start, end)
                if (piece.isNotBlank()) out += piece
                start = end
                end = iterator.next()
            }
            return out
        }
    }
}
