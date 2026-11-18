package com.titankeys.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    /** The current input method's word list: one at most, whatever the switches ([WordListSlot]). */
    private val slot = WordListSlot()
    private var loadedEngine: InputEngine? = null
    /** Loads one input method at a time, so switches in a row never load several lists side by side. */
    private val loader = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "InputMethod").apply {
            priority = Thread.NORM_PRIORITY - 1
            isDaemon = true
        }
    }

    init {
        live[this] = Unit
    }

    /** The current input method's engine, started, or null for one whose keys type letters straight in. */
    val engine: InputEngine? get() = if (englishOnly) null else loadedEngine
    val words = LanguageWords(english) { slot.list }

    /** Whether typing waits for an input method that's loading, and goes to it once it's ready ([EngineWait]). */
    private val engineWait = EngineWait()
    val loadingNow get() = !englishOnly && engineWait.waiting

    /** The current input method's word list, once loaded: null for English and for one without a list. */
    val list: WordList? get() = if (englishOnly) null else slot.list

    /** The input method switched to, once loaded. */
    private var loaded: InputMethod = InputMethod.ENGLISH
    /** The spelling variant [loaded] types (Portuguese's pt-BR or pt-PT), or null for one without variants. */
    private var loadedVariant: String? = null

    /** The spelling variant input method [id] types, of its [variants], by Settings ([InputMethodVariants]). */
    private fun variantOf(id: String, variants: List<String>): String? =
        InputMethodVariants.chosen(TypingSettings.inputMethodVariants(context), id, variants)

    /** Whether the input method typing now has had its variant changed in Settings since it loaded: it loads again. */
    private fun variantChanged(id: String): Boolean {
        if (loaded.id != id || target != id) return false
        val variants = on.firstOrNull { it.id == id }?.variants.orEmpty().map { it.id }
        return variants.isNotEmpty() && variantOf(id, variants) != loadedVariant
    }

    /**
     * True in the guide's and the playground's fields, which teach English: they type English whatever is switched on, and
     * switching waits for the next field.
     */
    var englishOnly = false
        set(value) {
            if (field == value) return
            field = value
            words.other = !current.isEnglish
            words.wordBreak = current.rules.wordBreak
            words.apostrophes = current.rules.apostrophes
        }

    /** The input method typing now. English until the settings say otherwise, and always in the guide. */
    val current: InputMethod get() = if (englishOnly) InputMethod.ENGLISH else loaded
    /** The language typing now, whose own typing settings apply (TypingSettings.PER_LANGUAGE): Chinese's input methods share one. */
    val languageId: String get() = on.firstOrNull { it.id == current.id }?.language?.id ?: InputMethod.ENGLISH_ID

    /** The input methods that are on, in the overlay's order. */
    var on: List<InputMethodIndex.Entry> = listOf(InputMethodIndex.ENGLISH)
        private set
    /** Counts loads, so a slow one that's been overtaken by another is dropped, or never starts. */
    @Volatile private var loading = 0
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
        warmUp()
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
        if (choice.id != target || variantChanged(choice.id)) use(choice.id)
        // Android's keyboard list shows one entry for TitanKeys, the input method typing now, under its name; languages switch
        // inside the keyboard (owner, 2026-11-16). A restore or Settings may have left it otherwise: checked at every field start.
        val shown = listOf(choice.id)
        val android = InputMethodList.enabledIds(context)
        if (EnabledEntries.differ(shown, android)) {
            Log.i(LOG_TAG, "Android's keyboard list had $android, not the input method typing, ${choice.id}")
            toldAndroid(choice.id)
            InputMethodList.enable(context, shown)
        }
    }
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
        barWords()
        val engine = loadedEngine ?: return
        val chosen = TypingSettings.chineseVariant(context)
        for (option in CHINESE_VARIANTS) engine.option(option, option == chosen)
        engine.option(ChineseKeys.PLAIN_PUNCTUATION, TypingSettings.chinesePlainPunctuation(context))
        engine.option(com.titankeys.rime.RimeEngine.LAZY_SOUNDS, TypingSettings.lazySounds(context, current.id))
    }

    /** Switches to input method [id], saved for next time. Its files load in the background; English is at once. */
    fun switchTo(id: String) {
        if (on.none { it.id == id }) return
        TypingSettings.currentInputMethod(context, id)
        if (id != target) use(id)
    }

    /** Each language's input method last typed with, by language id, for the two-language toggle ([SwitchOverlay.toggle]). */
    val lastUsed = HashMap<String, String>()

    /**
     * As the keyboard's service is made (boot, or the keyboard restarted), before any field: every engine of an input method
     * that's on warms then, once a process, so nobody waits for one (owner, 2026-11-17: "best if we could make it so that
     * there's no warmup at all"). Warm engines stay through field changes and hiding; only turning an input method off, or
     * the process ending, frees them.
     */
    fun warmAtStart() {
        on = InputMethodList.index(context).on(TypingSettings.inputMethods(context))
        warmUp()
    }

    /** Engines kept warm for the input methods that are on, one for each engine: see [warmUp]. */
    private val warm = HashMap<String, InputEngine>()
    private val warming = HashSet<String>()

    /**
     * Each engine an input method that's on uses gets ready in the background as the keyboard starts and at each field start
     * ([InputEngine.warm]): Rime unpacks its 40 MB the first time and opens, Mozc copies its data and loads it. A switch to the
     * input method then only starts its own engine, with nothing to wait for (owner, 2026-11-17: "is the loading for the engine
     * on load avoidable?"). An engine no input method that's on uses any more is stopped.
     */
    private fun warmUp() {
        val wanted = on.filter { it.engine != null }.associateBy { it.engine!! }
        // One typing now goes when the keyboard switches away from it ([release]).
        for (name in warm.keys - wanted.keys) warm.remove(name)?.takeIf { it !== loadedEngine }?.let { engine ->
            Thread({ engine.stop() }, "Cool $name").start()
        }
        for ((name, entry) in wanted) {
            if (name in warm || name in warming) continue
            warming += name
            Thread({
                val started = SystemClock.elapsedRealtime()
                val method = InputMethodList.load(context, entry.id)
                val engine = method?.let { m ->
                    InputEngines.create(context, name)?.takeIf { it.warm(m, java.io.File(context.filesDir, "engines/$name")) }
                }
                val millis = SystemClock.elapsedRealtime() - started
                main.post {
                    warming -= name
                    Log.i(LOG_TAG, "Engine $name warm in $millis ms: ${if (engine != null) "ready (${engine.timing})" else "didn't start"}")
                    if (engine == null) return@post
                    if (on.any { it.engine == name }) warm[name] = engine else Thread({ engine.stop() }, "Cool $name").start()
                }
            }, "Warm $name").apply { priority = Thread.MIN_PRIORITY }.start()
        }
    }

    private fun use(id: String) {
        target = id
        InputMethodList.index(context)[id]?.let { lastUsed[it.language.id] = id }
        val ticket = ++loading
        val listTicket = slot.begin(id)
        // The last input method's next-word table goes with its list.
        if (id != loaded.id) words.nextWords = null
        engineWait.loading(ticket, id)
        if (id == InputMethod.ENGLISH_ID) {
            apply(InputMethod.ENGLISH, null, ticket, listTicket)
            return
        }
        // No line saying it's getting ready: the bar shows the keys that wait, plain ([preparing]).
        on.firstOrNull { it.id == id && it.engine != null }?.let { entry ->
            main.postDelayed({ if (ticket == loading) preparing?.invoke(entry) }, PREPARING_MILLIS)
        }
        // The engine warmed up for this input method's engine, if it's ready: the switch types with it, so nothing loads twice.
        val warmEngine = on.firstOrNull { it.id == id }?.engine?.let { warm[it] }
        loader.execute {
            try {
                load(id, ticket, listTicket, warmEngine)
            } catch (error: Throwable) {
                // Nothing here should throw, but if anything did, the keys waiting for this load ([EngineWait]) would wait for
                // good: it ends as a failed load does, in English, and the gate opens.
                Log.w(LOG_TAG, "Input method $id's load broke: typing English", error)
                main.post {
                    engineWait.loaded(ticket)
                    apply(InputMethod.ENGLISH, null, ticket, listTicket)
                }
            }
        }
    }

    /**
     * [use]'s load, on the loader thread: the input method's file, its words and table, and its engine, then [apply]. Its
     * engine is [warmEngine], the one warmed up for it, when there is one: started again for this input method, it has its
     * data open already, and Rime its schema too.
     */
    private fun load(id: String, ticket: Int, listTicket: Int, warmEngine: InputEngine? = null) {
        // Overtaken by a later switch while it waited its turn: it needn't load at all.
        if (ticket != loading) return
        val startedAt = SystemClock.elapsedRealtime()
        val method = InputMethodList.load(context, id)
        val fileMillis = SystemClock.elapsedRealtime() - startedAt
        // An engine reads its own words (Telex's choices): the keyboard's suggestions never see the word it builds.
        // Its spelling variant, as Settings has it (Portuguese's Brasil or Portugal): its own list and next words.
        val variant = method?.let { m -> variantOf(m.id, m.variants.map { it.id }) }
        val words = method?.takeIf { it.engine == null }?.let { InputMethodList.words(context, it, variant) }
        // Its next-word table, where J's tables have one: the bar's words after a word ([NextWords]).
        val pairs = method?.let { InputMethodList.nextWords(context, it, variant) }
        val wordsMillis = SystemClock.elapsedRealtime() - startedAt - fileMillis
        // An engine starts here, off the main thread, as it may take a while (Rime unpacks its tables the first time).
        val engineBegan = SystemClock.elapsedRealtime()
        val dir = method?.engine?.let { java.io.File(context.filesDir, "engines/$it") }
        val fromWarm = warmEngine?.takeIf { method?.engine != null && dir != null && it.start(method, dir) }
        val engine = fromWarm ?: method?.engine?.let { name -> InputEngines.create(context, name)?.takeIf { it.start(method, dir!!) } }
        val engineMillis = SystemClock.elapsedRealtime() - engineBegan
        main.post {
            engineWait.loaded(ticket)
            if (ticket == loading) Log.i(LOG_TAG, "Input method $id loaded in ${SystemClock.elapsedRealtime() - startedAt} ms: ${
                if (method?.engine != null) "engine ${method.engine} ${if (engine != null) "started" else "missing or didn't start"}" +
                    " (${if (fromWarm != null) "the warm one" else "a new one"}, $engineMillis ms: ${engine?.timing.orEmpty()})"
                else "${words?.size ?: 0} words, no engine"
            }; its file $fileMillis ms, words and next words $wordsMillis ms")
            if (method != null && (method.engine == null || engine != null)) apply(method, words, ticket, listTicket, engine, pairs, variant)
            else {
                release(engine)
                Log.w(LOG_TAG, "Input method $id didn't load (${if (method == null) "its im.json" else "its engine"}): typing English")
                apply(InputMethod.ENGLISH, null, ticket, listTicket)
            }
        }
    }

    /**
     * An engine the keyboard no longer types with: a warm one is kept for the next switch, cleared of anything half typed;
     * any other stops.
     */
    private fun release(engine: InputEngine?) {
        if (engine == null) return
        if (engine in warm.values) engine.reset() else engine.stop()
    }

    private fun apply(method: InputMethod, words: WordList?, ticket: Int, listTicket: Int, engine: InputEngine? = null,
                      pairs: NextWords? = null, spelling: String? = null) {
        if (ticket != loading) {
            if (engine !== loadedEngine) release(engine)
            return
        }
        if (loadedEngine !== engine) release(loadedEngine)
        loadedEngine = engine
        variant()
        loaded = method
        loadedVariant = spelling
        slot.done(listTicket, words)
        this.words.nextWords = pairs
        this.words.other = !current.isEnglish
        this.words.wordBreak = current.rules.wordBreak
        this.words.apostrophes = current.rules.apostrophes
        barWords()
        changed(method)
    }

    /**
     * The keyboard is going: no load finishes, the word list goes, and every engine stops, the warm ones too. Android may make
     * a new keyboard, and this one's data mustn't stay with it.
     */
    fun stop() {
        saveLearning()
        loading++
        engineWait.stop()
        slot.clear()
        if (loadedEngine !in warm.values) loadedEngine?.stop()
        loadedEngine = null
        for (engine in warm.values) engine.stop()
        warm.clear()
        loader.shutdownNow()
        live.remove(this)
    }

    /** The input method whose learning [words] holds now, and the changes not written yet. */
    private var learningFor: String? = null
    private var unsaved = 0

    /**
     * The current input method's starters and what it learned, for the bar's words ([LanguageWords]): Chinese's in Simplified
     * for that variant. Its learning is read from the `language_learning` store as it's switched to, and written back every
     * few words and as another is switched to. With Learn new words off, nothing is learned.
     */
    private fun barWords() {
        val method = loaded
        words.starters = if (method.startersSimplified.isNotEmpty() && TypingSettings.chineseVariant(context) == "zh_hans")
            method.startersSimplified else method.starters
        words.capitals = method.rules.capitalise
        if (learningFor == method.id) return
        saveLearning()
        learningFor = method.id
        words.learning = if (method.isEnglish) null else LearnedWords.of(context, method.id)
        words.learned = {
            if (++unsaved >= SAVE_EVERY) saveLearning()
        }
        words.mayLearn = ::learningOn
        val typing = com.titankeys.engine.SwipeTyping.get(context)
        val favourites = FavouriteWords.get(context)
        words.rules = WordRules(typing::blockedWord, favourites::contains)
    }

    /** Whether the user lets TitanKeys learn their words: the same switch as English's, Learn new words I type. */
    private fun learningOn() = com.titankeys.engine.SwipeTyping.get(context).learningEnabled()


    /** The word menu changed what the current input method knows: written at once. */
    fun learningChanged() {
        unsaved++
        saveLearning()
    }

    /** Writes what the current input method learned. */
    fun saveLearning() {
        val id = learningFor ?: return
        val learning = words.learning ?: return
        if (unsaved == 0) return
        unsaved = 0
        LearnedWords.save(context, id)
    }

    /** What this keyboard holds of its input methods, for LeakCheck. */
    private fun held() = "typing ${current.id}, word list ${slot.id ?: "none"}${slot.list?.let { " (${it.size} words)" }.orEmpty()}" +
        ", engine ${loadedEngine?.javaClass?.simpleName ?: "none"}, warm ${warm.keys.sorted()}"

    companion object {
        /**
         * TitanKeys Debug's LeakCheck: every keyboard's input methods still in memory, and every word list. One keyboard and one
         * list at most is right; more means a switch or a keyboard that went kept its data.
         */
        fun report(): String {
            val keyboards = synchronized(live) { live.keys.toList() }
            val lists = WordList.alive()
            val runtime = Runtime.getRuntime()
            val used = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576
            return "input methods: ${keyboards.size} keyboard(s) (${keyboards.joinToString("; ") { it.held() }}), " +
                "${lists.size} word list(s) in memory${if (lists.isEmpty()) "" else " (${lists.joinToString(", ")})"}, " +
                "heap $used of ${runtime.maxMemory() / 1_048_576} MB"
        }
    }
}

/** Every keyboard's [Languages] still in memory, weakly, for TitanKeys Debug's LeakCheck: one, or none, is right. */
private val live = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Languages, Unit>())

/** How many learned words go by before they're written. */
private const val SAVE_EVERY = 5

/** Where [KeyboardListSync]'s entry is kept: Android's list is this phone's, so it isn't in the backup. */
internal const val KEYBOARD_LIST = "keyboard_list"

/** The log tag for which input method types and why: `adb logcat -s TitanKeysIM`. */
internal const val LOG_TAG = "TitanKeysIM"
/** The log tag for the bar's choices and the bigger menu, in TitanKeys Debug: `adb logcat -s TitanKeysChoices`. */
internal const val CHOICES_TAG = "TitanKeysChoices"
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
        /** The items in the row, in order: a choice too wide for the room left waits for the bigger menu. */
        private val shown = items.indices.filter { columns[it] != null }
        /**
         * The engine has more choices than the row shows, so the bigger menu has something to add: only then do Shift and a
         * swipe up open it (owner, 2026-11-17: "disable shift to expand the bar if there's nothing besides the main bar").
         */
        val more = ChineseRow.more(listed)
        override val notice = Strip.Notice.Picker(shown.map { items[it] }, shown.map { columns[it]!! }, lead = state.code,
            mark = mark, more = more)
        /** The row's item over key [column], or -1. */
        fun choiceAt(column: Int) = ChineseRow.choiceAt(column, columns)
        /** The item at [position] in the row as drawn, as a tap on the screen finds it. */
        fun itemAt(position: Int) = shown.getOrElse(position) { -1 }
        /** Whether row item [item] is the English word. */
        fun isEnglish(item: Int) = english != null && item == EnglishOffer.PLACE && item < items.size
        /** The engine's own place for row item [item], counted across its pages: the English word moves the rest one on. */
        fun engineIndex(item: Int) = if (english != null && item > EnglishOffer.PLACE) item - 1 else item
    }

    /**
     * The switcher on a held Sym ([SwitchOverlay]): the languages that are on, or with [opened] that language's input methods
     * that are on. The one typing now is marked.
     */
    class Switch(val languages: List<InputMethodIndex.LanguageOn>, val opened: InputMethodIndex.LanguageOn?, current: String) :
        LanguagePicker {
        private val labels = opened?.methods?.map { it.label } ?: languages.map { it.language.label }
        override val notice = Strip.Notice.Picker(labels, SwitchOverlay.columns(labels.size),
            opened?.methods?.indexOfFirst { it.id == current } ?: languages.indexOfFirst { on -> on.methods.any { it.id == current } })
        fun choice(key: String) = SwitchOverlay.choice(key, labels.size)
    }
}
