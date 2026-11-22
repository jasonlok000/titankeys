package com.titankeys.keyboard

import com.titankeys.connected.Json

/**
 * Every input method TitanKeys has, and which are on (docs/languages.md, "How an input method declares itself").
 *
 * The keyboard reads only the index (`android/assets/im/index.json`) as it starts. An input method's own `im.json` and word
 * list load when it's switched to. English is always in the list, first, and can't be missing.
 */
class InputMethodIndex(entries: List<Entry>) {
    /** What the index says about one input method: enough for the overlay, Settings and Android's keyboard list. */
    data class Entry(
        val id: String,
        val name: String,
        val nameEnglish: String,
        val label: String,
        val locale: String,
        val script: String,
        val engine: String? = null,
        /** Its how-to's lines for Settings, from the index ([InputMethod.howTo]); empty where it has none. */
        val howTo: List<String> = emptyList(),
        /** Its language page's guide, as the index has it (C's data: status, family, keys, roles), passed to Settings as is. */
        val guide: Map<*, *>? = null,
        /** Its spelling variants, the default first (Portuguese's pt-BR Brasil, pt-PT Portugal); empty where it has none. */
        val variants: List<VariantName> = emptyList(),
        /** The language it types: Serbian's Cyrillic and Latin, Chinese's Yale, Jyutping and Pinyin share one. */
        val language: Language = Language(id, name, nameEnglish, label, script, experimental = id != InputMethod.ENGLISH_ID),
    ) {
        val hasKeyPreview: Boolean = InputMethod.mappedPreview(guide?.get("keys") as? Map<*, *>, script)
    }

    /**
     * A language, as Settings > Languages and the held Sym's switcher show it: its input methods are ticked on its own page
     * (owner, 2026-11-16, the Gboard way). [experimental]: every language but English and Chinese is marked so in Settings.
     * [tested]: a native speaker has checked its typing (Korean); it stays [experimental], and Settings shows it as Tested
     * (owner, 2026-11-22, docs/languages.md's stages).
     */
    data class Language(
        val id: String,
        val name: String,
        val nameEnglish: String,
        val label: String,
        val script: String,
        val experimental: Boolean,
        val tested: Boolean = false,
    )

    /** A language that's on, with its input methods that are on, in the setting's order. */
    data class LanguageOn(val language: Language, val methods: List<Entry>)

    /** English first, then the rest in the index's order. */
    val entries: List<Entry> = listOf(ENGLISH) + entries.filter { it.id != InputMethod.ENGLISH_ID }
    private val byId = this.entries.associateBy { it.id }

    operator fun get(id: String) = byId[id]

    /** Every language, in the index's order: English first. */
    val languages: List<Language> get() = entries.map { it.language }.distinctBy { it.id }

    /** The input methods of language [id], in the index's order. */
    fun methodsOf(id: String) = entries.filter { it.language.id == id }

    /** The languages that are on, from the setting's text, each with its input methods that are on. */
    fun languagesOn(setting: String?) = group(on(setting))
    val ids get() = entries.map { it.id }

    /**
     * The input methods that are on, in the overlay's order, from the setting's text (ids with commas between). Unknown ids
     * are dropped, as are repeats; none is dropped for being past a limit, which Settings keeps when turning one on
     * ([InputMethod.MAX_LANGUAGES]). English is on when nothing else is.
     */
    fun on(setting: String?): List<Entry> {
        val wanted = setting.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val known = wanted.mapNotNull { byId[it] }
        return known.ifEmpty { listOf(ENGLISH) }
    }

    /** The input method to type with: [current] when it's on, or else the first one that is. */
    fun current(setting: String?, current: String?): Entry {
        val on = on(setting)
        return on.firstOrNull { it.id == current } ?: on.first()
    }

    companion object {
        val ENGLISH = Entry(InputMethod.ENGLISH_ID, "English", "English", "EN", "en", "Latn")

        /** The languages of the input methods [on], in the order each first appears, each with its own of them in order. */
        fun group(on: List<Entry>): List<LanguageOn> =
            on.groupBy { it.language.id }.values.map { LanguageOn(it.first().language, it) }

        /** Reads `index.json`: a list of entries. */
        fun parse(text: String): InputMethodIndex {
            val list = Json.parse(text) as? List<*> ?: throw Json.Error("index.json must be a list")
            return InputMethodIndex(list.map {
                val entry = it as? Map<*, *> ?: throw Json.Error("each entry must be an object")
                fun text(key: String) = entry[key] as? String ?: throw Json.Error("$key must be text")
                val language = entry["language"] as? Map<*, *>
                val id = text("id")
                val howTo = (entry["howTo"] as? List<*>)?.map { it as? String ?: throw Json.Error("howTo must list text") }.orEmpty()
                val guide = entry["guide"]?.let { it as? Map<*, *> ?: throw Json.Error("guide must be an object") }
                val variants = (entry["variants"] as? List<*>)?.map {
                    val variant = it as? Map<*, *> ?: throw Json.Error("each variant must be an object")
                    VariantName(variant["id"] as? String ?: throw Json.Error("each variant must have an id"),
                        variant["name"] as? String ?: throw Json.Error("each variant must have a name"))
                }.orEmpty()
                if (language == null) Entry(id, text("name"), text("nameEnglish"), text("label"), text("locale"), text("script"),
                    entry["engine"] as? String, howTo = howTo, guide = guide, variants = variants)
                else {
                    fun of(key: String) = language[key] as? String ?: throw Json.Error("language.$key must be text")
                    Entry(id, text("name"), text("nameEnglish"), text("label"), text("locale"), text("script"), entry["engine"] as? String,
                        howTo = howTo, guide = guide, variants = variants,
                        language = Language(of("id"), of("name"), of("nameEnglish"), of("label"), of("script"), language["experimental"] == true,
                            language["tested"] == true))
                }
            })
        }

        /** The setting's text for these ids, in order. */
        fun setting(ids: List<String>) = ids.distinct().joinToString(",")

        /**
         * Turns [id] on or off in the setting [current], keeping the order: a newly turned on one goes last. English can't be
         * turned off while it's the only one; a fifth language, or a fifth input method in one language, can't be turned on
         * ([InputMethod.MAX_LANGUAGES], [InputMethod.MAX_PER_LANGUAGE]), going by [languageOf]. Returns the new setting, or null
         * if the change isn't allowed.
         */
        fun toggle(current: List<String>, id: String, on: Boolean, languageOf: (String) -> String = { it }): List<String>? = when {
            on && id in current -> current
            on && languageOf(id) !in current.map(languageOf) && current.map(languageOf).distinct().size >= InputMethod.MAX_LANGUAGES -> null
            on && current.count { languageOf(it) == languageOf(id) } >= InputMethod.MAX_PER_LANGUAGE -> null
            on -> current + id
            id !in current -> current
            current.size == 1 -> null
            else -> current - id
        }

        /** Moves [id] one place up (earlier) or down in the order. */
        fun move(current: List<String>, id: String, up: Boolean): List<String> {
            val at = current.indexOf(id)
            val to = if (up) at - 1 else at + 1
            if (at < 0 || to !in current.indices) return current
            return current.toMutableList().apply { add(to, removeAt(at)) }
        }
    }
}

/** What a pick in the switcher does: open a language's second row, or switch to an input method. */
sealed interface SwitchPick {
    data class Row(val language: InputMethodIndex.LanguageOn) : SwitchPick
    data class To(val id: String) : SwitchPick
}

/**
 * The held Sym's switcher, over the top row's keys Q to O (columns 0 to 8), with Back on P (owner, 2026-11-20: "lets do q
 * to o"): the languages that are on, spread evenly (one takes all nine keys, two four each, three three each, four two
 * each, with the key left over empty). Picking a language with more than
 * one input method on shows a second row with them, spread the same way, the language typing now's included; a language
 * with one switches to it. The top two rows pick, as the word menu's do: rest a finger on a key to mark, press it
 * (docs/languages.md, "Switching"; owner, 2026-11-16).
 */
object SwitchOverlay {
    /** The rows of letters that pick: the top two. */
    const val ROWS = 2

    /**
     * The key columns each of [count] places spans: the owner's own layout (2026-11-22, #101), every place the same whole number
     * of keys (owner, 2026-11-17), from W, clear of the screen's corner at Q, except four, which need Q: one W–U, two three keys
     * each (W–R, T–U), three two each (W–E, R–T, Y–U), four two each (Q–W, E–R, T–Y, U–I). P is Back's, and O is never used.
     * With four, the pill over Q meets the corner, so every pill draws in the same amount ("trim all pills equally"): see
     * Strip.drawPicker.
     */
    fun columns(count: Int): List<IntRange> = when (count.coerceIn(0, InputMethod.MAX_LANGUAGES)) {
        0 -> emptyList()
        1 -> listOf(1..6)
        2 -> listOf(1..3, 4..6)
        3 -> listOf(1..2, 3..4, 5..6)
        else -> listOf(0..1, 2..3, 4..5, 6..7)
    }

    /** What the switcher shows: the first four languages on, each with its first four input methods. A setting may hold more. */
    fun shown(languages: List<InputMethodIndex.LanguageOn>) = languages.take(InputMethod.MAX_LANGUAGES)
        .map { it.copy(methods = it.methods.take(InputMethod.MAX_PER_LANGUAGE)) }

    /** With two languages, held Sym goes straight to the destination method or its method row. */
    fun shortcut(languages: List<InputMethodIndex.LanguageOn>, current: String): SwitchPick? {
        if (languages.size != 2) return null
        val other = languages.firstOrNull { on -> on.methods.none { it.id == current } } ?: languages[1]
        return pickLanguage(other)
    }

    /**
     * A tap on the language mark: another input method of the language typing now, never another language (owner,
     * 2026-11-17: "switches input method not languages"). With two on, the other; with three or more, the language's row in
     * the switcher; with one, nothing.
     */
    fun markTap(languages: List<InputMethodIndex.LanguageOn>, current: String): SwitchPick? {
        val language = languages.firstOrNull { on -> on.methods.any { it.id == current } } ?: return null
        return when (language.methods.size) {
            1 -> null
            2 -> SwitchPick.To(language.methods.first { it.id != current }.id)
            else -> SwitchPick.Row(language)
        }
    }

    /**
     * The mark at Q (owner, 2026-11-17: "If only one input method is in one language, the q sign shd show the language icon
     * instead"): the language's own mark where it has one input method on (中 with only Yale on, СР with only Serbian
     * Cyrillic), else the input method's (耶, 粵 with two Chinese on).
     */
    fun markLabel(on: List<InputMethodIndex.Entry>, current: String, currentLabel: String): String {
        val entry = on.firstOrNull { it.id == current } ?: return currentLabel
        val language = entry.language
        // Serbian in Latin letters marks SR, not the language's Cyrillic СР: the mark is in the script being typed.
        if (entry.script != language.script) return currentLabel
        return if (on.count { it.language.id == language.id } == 1) language.label else currentLabel
    }

    /** The row the switcher opens on: with one language on, straight to its input methods. */
    fun opening(languages: List<InputMethodIndex.LanguageOn>) = languages.singleOrNull()?.takeIf { it.methods.size > 1 }

    /** Rebuild the opened language's row from the enabled methods when Settings changes them. */
    fun refreshedRow(languages: List<InputMethodIndex.LanguageOn>, previous: InputMethodIndex.LanguageOn?) =
        languages.firstOrNull { it.language.id == previous?.language?.id && it.methods.size > 1 } ?: opening(languages)

    /** Back goes from a method row to the languages above it, or dismisses the switcher at its root. */
    fun backToLanguages(languages: List<InputMethodIndex.LanguageOn>, opened: InputMethodIndex.LanguageOn?) =
        opened != null && languages.size > 1

    /** What picking place [choice] does, in the languages' row or, with [opened], that language's row. */
    fun pick(languages: List<InputMethodIndex.LanguageOn>, opened: InputMethodIndex.LanguageOn?, choice: Int): SwitchPick? {
        if (opened != null) return opened.methods.getOrNull(choice)?.let { SwitchPick.To(it.id) }
        return languages.getOrNull(choice)?.let(::pickLanguage)
    }

    private fun pickLanguage(language: InputMethodIndex.LanguageOn) =
        if (language.methods.size > 1) SwitchPick.Row(language) else SwitchPick.To(language.methods.first().id)

    /** Which of [count] input methods the key [key] (A to Z) picks, or -1. */
    fun choice(key: String, count: Int): Int {
        val column = Picker.column(key, ROWS)
        if (column < 0) return -1
        return columns(count).indexOfFirst { column in it }
    }
}

/**
 * The picker on the bar: a row of choices, each over a key, picked with the keys as the word menu's buttons are. Accents use
 * it with one page, starting over the held key and spreading to fit; Chinese uses it with pages, from the left
 * (docs/languages.md, "Accents and the picker on the bar").
 */
object Picker {
    /** The ten key columns, Q to P. */
    const val COLUMNS = 10
    /** The rows of letters that pick a choice: the top two, as the word menu's. */
    const val ROWS = 2

    /** The key column of letter [key] (A to Z) in the top [rows] rows, or -1. */
    fun column(key: String, rows: Int = ROWS): Int {
        if (!Layout.isLetter(key)) return -1
        for (row in 0 until rows.coerceAtMost(Layout.rows.size)) {
            val column = Layout.rows[row].indexOf(key)
            if (column >= 0) return column
        }
        return -1
    }

    /** The column of letter [key] on any row, for centring the accents over the held key. */
    fun anyColumn(key: String): Int = column(key, Layout.rows.size)

    /**
     * The columns of [count] accents of the key in column [held]: centred over it, moved in to stay between W and O, the
     * switcher's range, never over Q's or P's column (owner, 2026-11-17). More than [ACCENTS] are cut to that many: no input
     * method has a letter with more (tests/languages).
     */
    fun accentColumns(count: Int, held: Int): List<Int> {
        val shown = count.coerceIn(0, ACCENTS)
        if (shown == 0) return emptyList()
        val start = (held - (shown - 1) / 2).coerceIn(FIRST_ACCENT, FIRST_ACCENT + ACCENTS - shown)
        return List(shown) { start + it }
    }

    /** Accents start at W's column at the leftmost. */
    const val FIRST_ACCENT = 1
    /** The most accents a key shows: W to O. */
    const val ACCENTS = COLUMNS - 2

    /** An engine's first choice sits over E: Q and W show the code being typed and the page. */
    const val FIRST_CHOICE = 2
    /** The most choices on a page: E to P. */
    const val CHOICES = COLUMNS - FIRST_CHOICE

    /** The columns of a page of [count] choices from an engine, from E. */
    fun pageColumns(count: Int): List<Int> = List(count.coerceIn(0, CHOICES)) { FIRST_CHOICE + it }

    /**
     * The item a finger's tap at [x] picks, or -1: the one whose keys' span on screen holds it, from its first key's left edge
     * to its last key's right ([cell] gives a key column's span in px). The same item a key under it picks: the switcher's
     * tiles, its input-method row, the accents and Chinese's row all map taps this way.
     */
    fun itemAt(x: Float, columns: List<IntRange>, items: Int, cell: (Int) -> Pair<Float, Float>): Int {
        for ((index, span) in columns.withIndex()) {
            if (index >= items) break
            if (x >= cell(span.first).first && x <= cell(span.last).second) return index
        }
        return -1
    }

    /** Which choice the key [key] picks, given each choice's column, or -1. */
    fun choice(key: String, columns: List<Int>): Int {
        val column = column(key)
        return if (column < 0) -1 else columns.indexOf(column)
    }
}

/** What holding a letter key does in an input method (docs/languages.md, "Accents and the picker on the bar"). */
enum class HoldKind {
    /** The key has choices (Latin accents, Cyrillic's held letters): the hold types the first and opens [HeldRow]. */
    Choices,
    /** The key has a letter on a third layer (Thai, Hebrew): the hold types it. */
    Third,
    /** Anything else: the hold types the key's printed symbol, as in English, if hold for symbols is on. */
    Symbol;

    companion object {
        /**
         * What a hold on the key printed [printed] does in [method]. Only keys with choices hold them: since the owner's
         * decision of 2026-11-22 (#107), the other keys of an input method with accents hold for their symbol as in English.
         */
        fun of(method: InputMethod, printed: Char): HoldKind = when {
            method.accentsOf(printed, false).isNotEmpty() -> Choices
            method.held(printed, false) != null -> Third
            else -> Symbol
        }
    }
}

/**
 * A held letter's row on the bar (owner, 2026-11-22, #107: "yea i think abc"). At the hold time the letter it typed becomes
 * [items]' first, the commonest, whether the key has one choice or several. The row shows the key's choices, then the key's
 * own printed [symbol], the one hold for symbols types; never the plain letter. It stays after the lift, with no timeout: a
 * touch on the screen on an item puts it in place of the one typed, and the row stays for another change of mind. The next
 * key closes it and does what it always does. While the letter is still held, a key under an item picks it too.
 */
class HeldRow(choices: List<String>, symbol: String?, held: String) {
    /**
     * The choices, most common first, then the key's symbol, never one twice. An `im.json` never holds a key's own letter
     * (tests/languages), so the plain letter is never here.
     */
    val items: List<String> = run {
        val own = choices.filter { it.isNotEmpty() }.distinct()
        val end = symbol?.takeIf { it.isNotEmpty() && it !in own }
        own.take(if (end != null) Picker.ACCENTS - 1 else Picker.ACCENTS) + listOfNotNull(end)
    }
    /** Each item's key column, centred over the held key, between W and O. */
    val columns: List<Int> = Picker.accentColumns(items.size, Picker.anyColumn(held))
    /** The item in the field now: the hold types the first. */
    var current = 0
        private set
    /** Whether the held letter is still down. Only then does a key under an item pick it. */
    var held = true
        private set

    /** What the hold types in place of the letter. */
    val first get() = items.first()

    /** The held letter came up: from now on every key closes the row and types as itself. */
    fun lift() {
        held = false
    }

    /**
     * A touch on the screen on item [index] (or a key under it while the letter is held): the text that takes the place of
     * the one in the field, or null for no item. The row stays open.
     */
    fun pick(index: Int): String? {
        val item = items.getOrNull(index) ?: return null
        current = index
        return item
    }

    /**
     * A key pressed while the row is open, [key] being its letter (A to Z) or null for any other key: the item it picks, or
     * -1, when the row closes and the key does what it always does. After the lift that's every key, Backspace and Space too.
     */
    fun key(key: String?): Int = if (!held || key == null) -1 else Picker.choice(key, columns)

    companion object {
        /** The key's printed symbol from what its key map types with Alt, or null where it types none. */
        fun symbol(code: Int): String? =
            if (code <= 0 || !Character.isValidCodePoint(code) || Character.isISOControl(code)) null else String(Character.toChars(code))
    }
}

/** Which input method types as a field starts, and why: the keyboard logs it (Languages.refresh). */
/**
 * Whether a Sym press waits to see if it's held, which opens the input method overlay: null when it waits, or why not, for
 * TitanKeys Debug's TitanKeysSym log.
 */
/** Android's list of TitanKeys' enabled entries against the input methods that are on: the two must be the same set. */
object EnabledEntries {
    /** True when Android's [android] entries (null when it can't say) aren't exactly the input methods [on]. */
    fun differ(on: List<String>, android: List<String>?) = android != null && android.toSet() != on.toSet()
}

object SymHold {
    /** What a Sym tap (let go before the hold time) does: it steps back, clearing a code first, else Sym as usual. */
    enum class Tap { ClearCode, Usual }

    fun tap(codePending: Boolean) = if (codePending) Tap.ClearCode else Tap.Usual

    fun whyNot(onCount: Int, englishOnly: Boolean, panelOpen: Boolean, altOn: Boolean, inGuide: Boolean, exact: Boolean,
               waiting: Int): String? = when {
        onCount < 2 -> "only $onCount input method on"
        englishOnly -> "this field types English: the guide's lessons"
        altOn -> "Alt is on"
        inGuide -> "the guide"
        exact -> "an exact typing app"
        waiting > 0 -> "$waiting keys still waiting for the app"
        else -> null
    }
}

/**
 * In an input method with its own letters (Georgian, Russian), a letter key without Alt, Ctrl or Meta is the keyboard's,
 * Shift or not: it types the key's letter or its Shift letter, and never reaches the app, whose own handling of a Latin key
 * would be wrong (a Shift + W in Georgian once moved the focus out of the field).
 */
object KeysMapKeys {
    fun keeps(hasKeys: Boolean, letterKey: Boolean, alt: Boolean, ctrl: Boolean, meta: Boolean) = hasKeys && letterKey && !alt && !ctrl && !meta
}

data class InputMethodChoice(val id: String, val why: String) {
    companion object {
        /**
         * From the input methods' [setting], the [current] one saved (Settings, the overlay or a restore), and [picked], one picked
         * in Android's keyboard list since the keyboard last set it ([KeyboardListSync.heard]), or null.
         */
        fun atFieldStart(index: InputMethodIndex, setting: String?, current: String?, picked: String?): InputMethodChoice {
            val on = index.on(setting)
            if (picked != null && on.any { it.id == picked }) return InputMethodChoice(picked, "picked in Android's keyboard list")
            if (on.any { it.id == current }) return InputMethodChoice(current!!, "the saved setting")
            return InputMethodChoice(on.first().id, "the saved $current isn't on, so the first that is")
        }
    }
}

/**
 * Which input method Android's keyboard list picked, if any. Android keeps its own current entry, and it isn't in TitanKeys'
 * backup: after a restore, or a change in Settings > Languages while the keyboard was off, Android still has the old one and
 * says so as the keyboard starts. So only a change of Android's entry is a pick. [synced] is the entry the keyboard last left
 * Android on, or heard from it; with none yet, the first one heard is taken as it is, and the setting decides.
 */
class KeyboardListSync(var synced: String? = null) {
    /** Android says its entry is [id]: the input method picked there, to switch to, or null when nothing was picked. */
    fun heard(id: String?): String? {
        if (id == null || id == synced) return null
        val first = synced == null
        synced = id
        return if (first) null else id
    }

    /** The keyboard set Android's entry to [id], to match the input method typing. */
    fun told(id: String) {
        synced = id
    }
}

/** Which suggestion, by rank (0 the best), each of the bar's three places shows, left to right. */
object BarOrder {
    private val LEFT_TO_RIGHT = listOf(1, 0, 2)
    private val RIGHT_TO_LEFT = listOf(2, 0, 1)

    fun of(rightToLeft: Boolean) = if (rightToLeft) RIGHT_TO_LEFT else LEFT_TO_RIGHT
}

/** Right-to-left text, for steering: which way the cursor goes logically when the finger moves it visually. */
object RightToLeft {
    /** Scripts written right to left. */
    val SCRIPTS = setOf("Hebr", "Arab")

    /**
     * Whether the paragraph the cursor is in reads right to left: its first letter with a direction decides, as Android's
     * text does (the text before the cursor back to the line's start, then what's after it).
     */
    fun paragraph(before: String, after: String): Boolean {
        val text = before.substringAfterLast('\n') + after.substringBefore('\n')
        for (c in text) when (Character.getDirectionality(c)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
        }
        return false
    }

    /** Whether moving the cursor one step visually right ([direction] > 0) goes forward in the text. */
    fun forward(direction: Int, rightToLeft: Boolean) = (direction > 0) != rightToLeft
}

/**
 * One Sym press, as the switcher sees it: let go soon it's Sym as always; held, it opens the switcher; pressed again while
 * the switcher is open, it closes it. Android repeats a held key, so a held Sym's repeats come in while its own switcher is
 * open: they're swallowed, never taken as a new press, and the release always ends the press, so the next Sym works.
 */
class SymPress {
    enum class Press {
        /** The switcher is open: this press closes it, and its repeats and release are swallowed. */
        Close,
        /** A repeat, or a press while one waits: nothing. */
        Swallow,
        /** Waits for the hold time: [held] then opens the switcher. */
        Wait,
        /** Sym as always. */
        Usual,
    }

    enum class Release {
        /** It opened or closed the switcher: the release does nothing more. */
        Kept,
        /** Let go before the hold time: its press now goes on as Sym always does. */
        Usual,
        /** Not a press this kept back. */
        NotMine,
    }

    /** True from a press that waits until its release. */
    var waiting = false
        private set
    private var opened = false
    private var closing = false

    /** A Sym down: its [repeat] count, whether the switcher is open, and whether a new press may wait (two or more on). */
    fun press(repeat: Int, switcherOpen: Boolean, mayWait: () -> Boolean): Press = when {
        // The held Sym that opened the switcher, or the press that closes it, still repeating.
        repeat > 0 && (switcherOpen || waiting || closing) -> Press.Swallow
        switcherOpen -> {
            closing = true
            Press.Close
        }
        waiting -> Press.Swallow
        repeat > 0 || !mayWait() -> Press.Usual
        else -> {
            waiting = true
            opened = false
            Press.Wait
        }
    }

    /**
     * This press cleared a code being built, and does nothing more: its repeats and release are swallowed, as a press that
     * closes the switcher (owner, 2026-11-17: "pressing the sym button should remove the keycode stuff first").
     */
    fun cleared() {
        waiting = false
        closing = true
    }

    /** The hold time passed: true if Sym is still down and the switcher opens. */
    fun held(): Boolean {
        if (waiting) opened = true
        return waiting
    }

    /** Sym let go. */
    fun release(): Release = when {
        closing -> {
            reset()
            Release.Kept
        }
        !waiting -> Release.NotMine
        else -> {
            val kept = opened
            reset()
            if (kept) Release.Kept else Release.Usual
        }
    }

    /** A new field: nothing is held back any more. */
    fun reset() {
        waiting = false
        opened = false
        closing = false
    }
}

/**
 * TitanKeys' picking overlays and the touchpad. While the held Sym's switcher (either row), the accent picker or Chinese's
 * bigger menu is open, a swipe on the keys neither draws its trail nor types a word or deletes one, as on the Sym panel:
 * a finger only marks (owner, 2026-11-16: "swipe animation and swipe typing still triggers"). The bar's one row of choices
 * keeps its own swipes: a swipe up opens the bigger menu, and the menu's swipes turn its pages.
 */
object Overlays {
    enum class Open { None, Switcher, SwitcherRow, Accents, Choices, ChoicesMenu }

    /** No trail, no swiped word, no delete flick or held delete. */
    fun stopsSwipes(open: Open) = open == Open.Switcher || open == Open.SwitcherRow || open == Open.Accents || open == Open.ChoicesMenu

    /** The touchpad works as on the Sym panel: a resting finger marks, and only a quick swipe comes out, to turn a page. */
    fun paging(open: Open, panelOpen: Boolean) = panelOpen || open != Open.None
}

/** A spelling variant as the index lists it: its id and its name in its language (Brasil, Portugal). */
data class VariantName(val id: String, val name: String)

/**
 * Which spelling variant each input method with several types (Settings > Languages, the language's page; Portuguese's Brasil
 * or Portugal): the setting is "id=variant" pairs separated by commas, and an input method without one, or with a variant it
 * doesn't have, types its first.
 */
object InputMethodVariants {
    /** The variant [id] types, of its [variants] (the default first), by [setting]; null where it has none. */
    fun chosen(setting: String, id: String, variants: List<String>): String? {
        val picked = read(setting)[id]
        return picked?.takeIf { it in variants } ?: variants.firstOrNull()
    }

    /** [setting] with [id] typing [variant]. */
    fun with(setting: String, id: String, variant: String): String = (read(setting) + (id to variant)).entries
        .joinToString(",") { "${it.key}=${it.value}" }

    fun read(setting: String): Map<String, String> = setting.split(',').mapNotNull { pair ->
        val (id, variant) = pair.split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() && it[1].isNotBlank() }
            ?: return@mapNotNull null
        id.trim() to variant.trim()
    }.toMap()
}

/**
 * Which engines stay warm (Languages.warmUp): only those of the input methods that are on, one input method for each (owner,
 * 2026-11-17: "if the language or input method is not chosen, it should not load. Meaning that disabling it should disable
 * that engine as well"). An engine no input method on uses is stopped at the next field start.
 */
object WarmEngines {
    fun wanted(on: List<InputMethodIndex.Entry>): Map<String, InputMethodIndex.Entry> =
        on.filter { it.engine != null }.associateBy { it.engine!! }
}

/** The input method requested by one of the app's Try it editors. */
internal object PracticeInputMethod {
    private const val PREFIX = "com.titankeys.practiceMethod="

    fun requested(editorPackage: String?, ownPackage: String, options: String?): String? =
        options?.takeIf { editorPackage == ownPackage && it.startsWith(PREFIX) }
            ?.removePrefix(PREFIX)?.takeIf { it.isNotEmpty() }

    fun enabled(id: String?, methods: List<InputMethodIndex.Entry>): InputMethodIndex.Entry? =
        methods.firstOrNull { it.id == id }
}
