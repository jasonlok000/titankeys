package com.titankeys.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.titankeys.engine.TypingSettings

/**
 * The keyboard's input methods while it runs (docs/languages.md): which are on, which one types now, and its word list,
 * loaded off the main thread when it's switched to. English is always ready: its own code loads as it always has.
 *
 * [words] is what the composer asks for words: English's own while English types, the current input method's list otherwise.
 */
internal class Languages(
    private val context: Context,
    english: Words,
    /** Android's current entry in its keyboard list, as an input method's id. */
    private val android: () -> String?,
    private val changed: (InputMethod) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val sync = KeyboardListSync(syncStore().getString(SYNCED, null))
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

    /**
     * Called on the main thread when an engine's input method is still getting ready [PREPARING_MILLIS] after it was switched
     * to: the first time Rime copies its data out, for one. Whatever it shows ends with [changed], when the input method is
     * ready or the keyboard falls back to English.
     */
    var preparing: ((InputMethodIndex.Entry) -> Unit)? = null

    /** Whether holding Sym opens the overlay: only with two or more on. */
    val switching get() = on.size > 1 && !englishOnly

    /**
     * Reads which input methods are on and which types now, as a field starts: Settings or a restore may have changed them.
     * The setting decides, unless Android's keyboard list picked another since the keyboard last set it ([KeyboardListSync]).
     */
    fun refresh() {
        val index = InputMethodList.index(context)
        val setting = TypingSettings.inputMethods(context)
        on = index.on(setting)
        // A restore writes the setting but not Android's list of enabled entries, which only Settings kept the same. The keyboard
        // checks Android's list at every field start and sets it again whenever it isn't exactly the input methods that are on
        // (German was once on in Settings and missing from Android's list), and once per process or after any change.
        val ids = on.map { it.id }
        val android = InputMethodList.enabledIds(context)
        if (setting != enabledFor || EnabledEntries.differ(ids, android)) {
            if (EnabledEntries.differ(ids, android)) Log.w(LOG_TAG, "Android's keyboard list had $android, not the input methods on, $ids")
            enabledFor = setting
            InputMethodList.enable(context, ids)
        }
        val picked = sync.heard(android())
        saveSync()
        val choice = InputMethodChoice.atFieldStart(index, setting, TypingSettings.currentInputMethod(context), picked)
        if (choice.id == picked) TypingSettings.currentInputMethod(context, picked)
        val line = "Input method at field start: ${choice.id} (${choice.why})" +
            if (englishOnly) "; this field types English: the guide or the playground" else ""
        if (line != lastLogged) {
            lastLogged = line
            Log.i(LOG_TAG, line)
        }
        if (choice.id != target) use(choice.id)
    }

    /** The input methods' setting Android's enabled entries were last made to match, in this process. */
    private var enabledFor: String? = null
    private var lastLogged: String? = null

    /**
     * Android says its keyboard list's entry is [id]: as the keyboard starts, or when it was picked there. A pick of an input
     * method that's on types from now on; Android's old entry, still there after a restore, changes nothing.
     */
    fun androidSays(id: String?) {
        val picked = sync.heard(id)
        saveSync()
        if (picked == null) return
        on = InputMethodList.index(context).on(TypingSettings.inputMethods(context))
        switchTo(picked)
    }

    /** The keyboard set Android's keyboard list to [id], to match the input method typing. */
    fun toldAndroid(id: String) {
        sync.told(id)
        saveSync()
    }

    private fun syncStore() = context.getSharedPreferences(KEYBOARD_LIST, Context.MODE_PRIVATE)

    private fun saveSync() {
        val synced = sync.synced
        if (synced != syncStore().getString(SYNCED, null)) syncStore().edit().putString(SYNCED, synced).apply()
    }

    /**
     * The Chinese variant: exactly one of zh_hk, zh_tw and zh_hans on, from Settings. None until the user picks one. And plain
     * punctuation, Rime's ascii_punct, off unless Settings turns it on.
     */
    fun variant() {
        val engine = loadedEngine ?: return
        val chosen = TypingSettings.chineseVariant(context)
        for (option in CHINESE_VARIANTS) engine.option(option, option == chosen)
        engine.option(ChineseKeys.PLAIN_PUNCTUATION, TypingSettings.chinesePlainPunctuation(context))
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
        on.firstOrNull { it.id == id && it.engine != null }?.let { entry ->
            main.postDelayed({ if (ticket == loading) preparing?.invoke(entry) }, PREPARING_MILLIS)
        }
        Thread({
            val method = InputMethodList.load(context, id)
            // An engine reads its own words (Telex's choices): the keyboard's suggestions never see the word it builds.
            val words = method?.takeIf { it.engine == null }?.let { InputMethodList.words(context, it, it.variants.firstOrNull()?.id) }
            // An engine starts here, off the main thread, as it may take a while (Rime unpacks its tables the first time).
            val engine = method?.engine?.let { name ->
                InputEngines.create(context, name)?.takeIf { it.start(method, java.io.File(context.filesDir, "engines/$name")) }
            }
            main.post {
                if (ticket == loading) Log.i(LOG_TAG, "Input method $id loaded: ${
                    if (method?.engine != null) "engine ${method.engine} ${if (engine != null) "started" else "missing or didn't start"}"
                    else "${words?.size ?: 0} words, no engine"
                }")
                if (method != null && (method.engine == null || engine != null)) apply(method, words, ticket, engine)
                else {
                    engine?.stop()
                    Log.w(LOG_TAG, "Input method $id didn't load (${if (method == null) "its im.json" else "its engine"}): typing English")
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

/** Where [KeyboardListSync]'s entry is kept: Android's list is this phone's, so it isn't in the backup. */
internal const val KEYBOARD_LIST = "keyboard_list"

/** The log tag for which input method types and why: `adb logcat -s TitanKeysIM`. */
internal const val LOG_TAG = "TitanKeysIM"
private const val SYNCED = "synced"

/** How long an engine's input method may take to start before the bar says it's getting ready. */
internal const val PREPARING_MILLIS = 500L

/** What the bar says while an engine's input method gets ready. F (docs/words-m1.md). */
internal fun preparingLine(entry: InputMethodIndex.Entry) = "Warming up ${entry.name}…"

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
     * An engine's choices for the text being built (docs/languages.md, "Chinese on the bar"): over W to O from the left, each
     * as wide as it needs, the input method's [mark] over Q, and P's cue when the engine has more than the row shows. [listed]
     * are the engine's first choices, across its pages; [english] is the letters offered as an English word, second
     * ([EnglishOffer]), or null. Space picks the first; a tap, Shift + key, a hold or a flick picks the one over a key.
     */
    class Choices(val state: EngineState, listed: List<String>, val english: String? = null, mark: String = "") : LanguagePicker {
        val items = EnglishOffer.withWord(listed.take(ChineseRow.ASK), english)
        private val columns = ChineseRow.place(items)
        override val notice = Strip.Notice.Picker(items.take(columns.size), columns, lead = state.code, mark = mark,
            more = listed.size > columns.size - (if (english != null && columns.size > EnglishOffer.PLACE) 1 else 0))
        /** The row's item over key [column], or -1. */
        fun choiceAt(column: Int) = ChineseRow.choiceAt(column, columns)
        /** Whether row item [item] is the English word. */
        fun isEnglish(item: Int) = english != null && item == EnglishOffer.PLACE && item < items.size
        /** The engine's own place for row item [item], counted across its pages: the English word moves the rest one on. */
        fun engineIndex(item: Int) = if (english != null && item > EnglishOffer.PLACE) item - 1 else item
    }

    /** The switch overlay on a held Sym: each input method that's on, the current one marked. */
    class Switch(val entries: List<InputMethodIndex.Entry>, current: String) : LanguagePicker {
        override val notice = Strip.Notice.Picker(entries.map { it.label }, SwitchOverlay.columns(entries.size),
            entries.indexOfFirst { it.id == current })
        fun choice(key: String) = SwitchOverlay.choice(key, entries.size)
    }
}
