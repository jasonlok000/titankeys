package com.atlas.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.atlas.engine.TypingSettings

/**
 * The keyboard's input methods while it runs (docs/languages.md): which are on, which one types now, and its word list,
 * loaded off the main thread when it's switched to. English is always ready: its own code loads as it always has.
 *
 * [words] is what the composer asks for words: English's own while English types, the current input method's list otherwise.
 */
internal class Languages(private val context: Context, english: Words, private val changed: (InputMethod) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var list: WordList? = null
    private var loadedEngine: InputEngine? = null

    /** The current input method's engine, started, or null for one whose keys type letters straight in. */
    val engine: InputEngine? get() = if (englishOnly) null else loadedEngine
    val words = LanguageWords(english) { list }

    /** The input method switched to, once loaded. */
    private var loaded: InputMethod = InputMethod.ENGLISH

    /**
     * True in the guide's and the playground's fields, which teach English: they type English whatever is switched on, and
     * switching waits for the next field.
     */
    var englishOnly = false
        set(value) {
            if (field == value) return
            field = value
            words.other = !current.isEnglish
        }

    /** The input method typing now. English until the settings say otherwise, and always in the guide. */
    val current: InputMethod get() = if (englishOnly) InputMethod.ENGLISH else loaded
    /** The input methods that are on, in the overlay's order. */
    var on: List<InputMethodIndex.Entry> = listOf(InputMethodIndex.ENGLISH)
        private set
    /** Counts loads, so a slow one that's been overtaken by another is dropped. */
    private var loading = 0
    /** The input method being switched to, which may still be loading. */
    private var target = InputMethod.ENGLISH_ID

    /** Whether holding Sym opens the overlay: only with two or more on. */
    val switching get() = on.size > 1 && !englishOnly

    /** Reads which input methods are on and which types now, as a field starts: Settings may have changed them. */
    fun refresh() {
        val index = InputMethodList.index(context)
        val setting = TypingSettings.inputMethods(context)
        on = index.on(setting)
        val wanted = index.current(setting, TypingSettings.currentInputMethod(context)).id
        if (wanted != target) use(wanted)
    }

    /** The Chinese variant: exactly one of zh_hk, zh_tw and zh_hans on, from Settings. None until the user picks one. */
    fun variant() {
        val engine = loadedEngine ?: return
        val chosen = TypingSettings.chineseVariant(context)
        for (option in CHINESE_VARIANTS) engine.option(option, option == chosen)
    }

    /** Switches to input method [id], saved for next time. Its files load in the background; English is at once. */
    fun switchTo(id: String) {
        if (on.none { it.id == id }) return
        TypingSettings.currentInputMethod(context, id)
        if (id != target) use(id)
    }

    private fun use(id: String) {
        target = id
        val ticket = ++loading
        if (id == InputMethod.ENGLISH_ID) {
            apply(InputMethod.ENGLISH, null, ticket)
            return
        }
        Thread({
            val method = InputMethodList.load(context, id)
            val words = method?.let { InputMethodList.words(context, it, it.variants.firstOrNull()?.id) }
            // An engine starts here, off the main thread, as it may take a while (Rime unpacks its tables the first time).
            val engine = method?.engine?.let { name ->
                InputEngines.create(context, name)?.takeIf { it.start(method, java.io.File(context.filesDir, "engines/$name")) }
            }
            main.post {
                if (method != null && (method.engine == null || engine != null)) apply(method, words, ticket, engine)
                else {
                    engine?.stop()
                    apply(InputMethod.ENGLISH, null, ticket)
                }
            }
        }, "InputMethod $id").apply { priority = Thread.NORM_PRIORITY - 1 }.start()
    }

    private fun apply(method: InputMethod, words: WordList?, ticket: Int, engine: InputEngine? = null) {
        if (ticket != loading) {
            engine?.stop()
            return
        }
        loadedEngine?.stop()
        loadedEngine = engine
        variant()
        loaded = method
        list = words
        this.words.other = !current.isEnglish
        changed(method)
    }
}

/** The Chinese variants, one setting for every Chinese input method (docs/languages.md). */
internal val CHINESE_VARIANTS = listOf("zh_hk", "zh_tw", "zh_hans")

/** What the bar's picker is showing, and so what a key picks. */
internal sealed interface LanguagePicker {
    val notice: Strip.Notice.Picker

    /** A held letter's accents. Picking one replaces the letter it typed. */
    class Accents(val accents: List<String>, held: String) : LanguagePicker {
        private val columns = Picker.accentColumns(accents.size, Picker.anyColumn(held))
        override val notice = Strip.Notice.Picker(accents.take(columns.size), columns.map { it..it })
        fun choice(key: String) = Picker.choice(key, columns)
    }

    /**
     * An engine's choices for the text being built, from the left. Letters go on building it, so a choice is picked with Space:
     * the one a resting finger marks, or else the first.
     */
    class Choices(val state: EngineState) : LanguagePicker {
        private val columns = Picker.pageColumns(state.choices.size)
        override val notice = Strip.Notice.Picker(state.choices.take(columns.size), columns.map { it..it },
            lead = state.code, leadMark = state.pageMark)
        /** The choice over key [column], or -1. */
        fun choiceAt(column: Int) = columns.indexOf(column)
    }

    /** The switch overlay on a held Sym: each input method that's on, the current one marked. */
    class Switch(val entries: List<InputMethodIndex.Entry>, current: String) : LanguagePicker {
        override val notice = Strip.Notice.Picker(entries.map { it.label }, SwitchOverlay.columns(entries.size),
            entries.indexOfFirst { it.id == current })
        fun choice(key: String) = SwitchOverlay.choice(key, entries.size)
    }
}
