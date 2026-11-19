package com.titankeys.rime

import android.content.Context
import com.titankeys.keyboard.ChineseKeys
import com.titankeys.keyboard.EngineState
import com.titankeys.keyboard.InputEngine
import com.titankeys.keyboard.InputMethod
import java.io.File
import java.util.IdentityHashMap

/**
 * Chinese through Rime (docs/languages.md, "How Rime plugs in"). A letter goes to Rime, which returns the code and the
 * choices for the picker. Space types the first choice, Enter types the code as typed letters, Backspace deletes from the
 * code. With no code typed, Space, Enter and Backspace are the keyboard's own. Punctuation goes through Rime's punctuator,
 * which gives Chinese marks (，。, and 「」 for quotes in Traditional, [ChineseKeys.marks]); with a code typed, its first
 * choice goes first. Digits stay plain: with a code typed, its first choice goes, then the keyboard types the digit. A
 * capital, or a character Rime has no key for, types as it is, after the code being typed goes as its first choice. The bar's
 * row and its bigger menu read the choices across Rime's pages ([choices], [pickAt]; docs/languages.md, "Chinese on the
 * bar").
 *
 * After a word is committed, Rime offers the words that may come next (librime-predict, #56; docs/languages.md, "Next-word
 * suggestions"): they're its choices with nothing typed, so the bar shows them as it shows a code's. Space picks the first,
 * a tap or the bigger menu any; a letter starts a new code instead, and any other key clears them and is the keyboard's
 * own. A new field starts with none ([reset]). They're cleared with Escape, Rime's own way: clearing alone would make the
 * predictor offer them again.
 *
 * Until a Chinese variant is picked (Settings > Chinese characters, no default: owner, 2026-11-16), the code still builds,
 * but the only choice says to pick one ([NO_VARIANT]), and picking it or Space types nothing. Enter still types the code.
 *
 * [start] runs on a background thread: it copies Rime's data out of the APK the first time ([install], see [RimeData] and
 * [RimeAssets]), opens Rime, and selects the input method's schema. Everything else runs on the keyboard's main thread.
 * Rime is one per app (rime_jni.cc keeps one session, behind one lock): the keyboard makes an engine for each Chinese input
 * method it switches to, and stops the last one after the next has started, so Rime closes only when no engine uses it.
 */
class RimeEngine(
    private val rime: RimeCalls,
    /** Whether the compiled files for a schema are already installed. */
    private val schemaReady: (RimeData, String) -> Boolean = { _, _ -> true },
    /** Puts the data in place under the folder it's given; true once it's there. */
    private val install: (RimeData) -> Boolean,
) : InputEngine {
    /** The engine the keyboard makes by name (InputEngines): the phone's Rime, with the data from the APK. */
    constructor(context: Context) : this(RimeNative, RimeAssets.schemaReadiness(context), RimeAssets.installer(context))

    /** TitanKeys' schemas, one per Chinese input method: an input method's `"engine": { "rime": "<id>" }`. */
    enum class Schema(val id: String) {
        Yale("tk_yale"), Jyutping("tk_jyutping"), Pinyin("tk_pinyin"), Cangjie("tk_cangjie"), Quick("tk_quick"), Zhuyin("tk_zhuyin")
    }

    @Volatile private var open = false
    @Volatile var schema: String? = null
        private set
    /** The options set so far, set again after every schema switch, which resets them. */
    private val options = linkedMapOf<String, Boolean>()

    /** Whether a Chinese variant is on: until one is, Rime's choices aren't shown. */
    val variantPicked get() = synchronized(this) { ChineseVariant.entries.any { options[it.option] == true } }

    /** What the last [start] or [warm] took: Rime's open (or that it was open), the schema's select (or that it was selected). */
    override var timing: String = ""
        private set

    private fun millisSince(nanos: Long) = (System.nanoTime() - nanos) / 1_000_000

    override fun start(method: InputMethod, dataDir: File): Boolean {
        val id = method.engineArg ?: return false
        if (method.engine != ENGINE) return false
        val data = RimeData(dataDir)
        synchronized(this) {
            val began = System.nanoTime()
            val opening = !open
            if (!open) open = runCatching { opened(rime) { data.prepareUser(); install(data) && rime.open(data.shared.path, data.user.path) } }
                .getOrDefault(false)
            val openMillis = millisSince(began)
            if (!open) return false
            val selecting = schema != id || seen != syncs
            val selectBegan = System.nanoTime()
            timing = "Rime ${if (opening) "opened in $openMillis ms" else "open already"}, $id " +
                if (selecting) "selected" else "selected already"
            if (selecting) {
                // Native selection can succeed with missing tables. Install them with Rime closed so it opens them afresh.
                val effective = effective(id)
                if (!schemaReady(data, effective) || !select(id)) {
                    if (!reopen(rime, data, install) || !schemaReady(data, effective) || !select(id)) return false
                }
            }
            if (selecting) timing += " in ${millisSince(selectBegan)} ms"
            // A restored backup's learned words merge in now ([RimeBackup]), once the schema has opened its dictionary.
            if (RimeBackup.pending(data)) {
                sync(rime, data)
                if (!select(id)) return false
            }
        }
        return true
    }

    /**
     * Unpacks the data the first time, opens Rime and selects [method]'s schema, so its dictionary, grammar and OpenCC are
     * loaded before anyone types: the keyboard types with this same engine when it switches to a Chinese input method, and
     * a switch to [method] then has nothing left to do (owner, 2026-11-17: "I'm still seeing loading for Yale"). Rime has one
     * session: if another engine has selected a schema since Rime opened, that one types, and this one selects nothing.
     */
    override fun warm(method: InputMethod, dataDir: File): Boolean {
        if (method.engine != ENGINE) return false
        val data = RimeData(dataDir)
        synchronized(this) {
            val began = System.nanoTime()
            if (!open) open = runCatching { opened(rime) { data.prepareUser(); install(data) && rime.open(data.shared.path, data.user.path) } }
                .getOrDefault(false)
            timing = "Rime opened in ${millisSince(began)} ms"
            val id = method.engineArg
            if (open && id != null && schema == null && claimed(rime, this) && schemaReady(data, effective(id))) {
                val selectBegan = System.nanoTime()
                if (select(id)) timing += ", $id selected in ${millisSince(selectBegan)} ms"
            }
        }
        return open
    }

    /** The sync count when this engine last selected its schema: a sync since closed the session, so it selects it again. */
    private var seen = -1

    /** Selects [id] on Rime's session, with every option set so far. */
    private fun select(id: String): Boolean {
        selected(rime, this)
        schema = null
        seen = syncs
        if (!rime.schema(effective(id))) return false
        schema = id
        for ((name, on) in options) rime.option(name, on)
        return true
    }

    private fun reselect() = synchronized(this) {
        val id = schema ?: return@synchronized
        schema = null
        seen = syncs
        if (rime.schema(effective(id))) {
            schema = id
            for ((name, on) in options) rime.option(name, on)
        }
    }

    override fun stop() {
        synchronized(this) {
            if (open) closed(rime)
            open = false
            schema = null
        }
    }

    /** Sets an option, such as one of the Chinese variants ([ChineseVariant.options]). Kept for the next schema too. */
    /** Lazy sounds on ([LAZY_SOUNDS]): the input method's own schema; off, its strict twin, with only its own spellings. */
    @Volatile private var lazy = true

    /** The schema Rime selects for input method schema [id]: its strict twin when lazy sounds are off and it has one. */
    private fun effective(id: String) = if (!lazy && id in LAZY_SCHEMAS) id + STRICT else id

    override fun option(name: String, on: Boolean) {
        if (name == LAZY_SOUNDS) {
            val changed = synchronized(this) { (lazy != on).also { lazy = on } }
            if (changed && open && schema != null) reselect()
            return
        }
        synchronized(this) {
            options[name] = on
            if (open && schema != null) rime.option(name, on)
        }
    }

    /** Sets [variant]'s option on and the others off, as the keyboard does from Settings. */
    fun variant(variant: ChineseVariant) {
        for ((name, on) in ChineseVariant.options(variant)) option(name, on)
    }

    private val ready: Boolean get() {
        if (!open || schema == null) return false
        if (seen != syncs) reselect()
        return schema != null
    }

    /** Rime's state now, with nothing sent. */
    fun state(): EngineState = if (ready) read(handled = false) else EngineState.UNHANDLED

    /** The bar's words on offer ([offer]), empty when none are. */
    @Volatile private var offered = emptyList<String>()

    override fun offer(words: List<String>): EngineState {
        if (!ready || !variantPicked || composing()) return EngineState.UNHANDLED
        offered = words
        return read(handled = true)
    }

    /** Rime's own next words, when it has any: they come first, before the bar's ([offered]). */
    private fun rimeSuggestions(): List<String> = if (suggesting()) rime.choices(0, MOST_SUGGESTIONS) else emptyList()

    /**
     * With nothing typed: Rime's next words, then the bar's own that aren't among them, so the row always has as many as fit
     * (owner, 2026-11-17: "sometimes there's only 3 suggestions for Chinese", where Rime's table has only three).
     */
    private fun suggestionsWithOffered(): List<String> {
        val own = rimeSuggestions()
        return own + offered.filter { it !in own }
    }

    /** Picks item [index] of [suggestionsWithOffered]: Rime's own as Rime picks, else the bar's word, typed with no space. */
    private fun pickOffered(index: Int): EngineState {
        val own = rimeSuggestions()
        if (index < own.size) {
            offered = emptyList()
            return read(handled = rime.selectAt(index))
        }
        val word = offered.filter { it !in own }.getOrNull(index - own.size) ?: return EngineState.UNHANDLED
        offered = emptyList()
        dismiss()
        rime.predictAfter(word)
        return read(handled = true).let { it.copy(commit = word + it.commit) }
    }

    override fun key(char: Char): EngineState {
        if (!ready) return EngineState.UNHANDLED
        offered = emptyList()
        dismiss()
        return when (char) {
            in 'a'..'z' -> send(char.code)
            // Rime takes printable ASCII (digits pick a choice, punctuation becomes Chinese marks); capitals and anything else
            // type as they are.
            in 'A'..'Z' -> passOn()
            in '0'..'9' -> when {
                !composing() -> EngineState.UNHANDLED
                !variantPicked -> read(handled = true)
                else -> passOn()
            }
            ' ' -> send(char.code)
            in '!'..'~' -> punctuation(char)
            else -> passOn()
        }
    }

    /** The variant that's on, or null before one is picked. */
    private val variantOn: String? get() = synchronized(this) { ChineseVariant.entries.firstOrNull { options[it.option] == true }?.option }

    /**
     * A punctuation key: Rime's mark (，。), in the variant's quotes. With a code typed, its first choice goes first, never
     * Rime's paging on - and =. Rime doesn't take it with plain punctuation on, and the keyboard types it as it is.
     */
    private fun punctuation(char: Char): EngineState {
        var first = ""
        if (composing()) {
            if (!variantPicked) return read(handled = true)
            rime.process(RimeKeys.SPACE, 0)
            first = rime.read()?.commit.orEmpty()
        }
        val state = send(char.code)
        val marks = ChineseKeys.marks(state.commit, variantOn)
        return state.copy(commit = first + marks)
    }

    override fun space(): EngineState = when {
        // With nothing typed, Space is a space: next words and the bar's own words are picked only by a tap or their keys
        // (owner, 2026-11-17: "space shdnt type the next word"). They go, and the keyboard types the space.
        !composing() -> {
            offered = emptyList()
            // Before a variant is picked, Rime's suggestions stay hidden there, untouched.
            if (variantPicked) dismissed() else EngineState.UNHANDLED
        }
        !variantPicked -> read(handled = true)
        else -> send(RimeKeys.SPACE)
    }

    /** Types the code as letters, as typed (Rime's input), and clears it. With no code, Enter is the keyboard's. */
    override fun enter(): EngineState {
        offered = emptyList()
        if (!composing()) return dismissed()
        // What was picked of the code, then the letters still pending (owner, 2026-11-17: "typing enter shd type out alr
        // selected word + remaining keycode"): 頂你個 picked and fai pending types 頂你個fai. Cleared, not committed: Rime
        // learns nothing.
        val input = rime.read()?.input.orEmpty()
        val (picked, from) = rime.picked()
        val letters = picked + input.substring(from.coerceIn(0, input.length))
        rime.reset()
        return read(handled = true).let { it.copy(commit = it.commit + letters) }
    }

    override fun backspace(): EngineState {
        offered = emptyList()
        return if (composing()) send(RimeKeys.BACKSPACE) else dismissed()
    }

    override fun pick(index: Int): EngineState {
        if (!ready || index < 0) return EngineState.UNHANDLED
        if (offered.isNotEmpty() && !composing()) return pickOffered(index)
        if (!variantPicked) return read(handled = true)
        return read(handled = rime.select(index))
    }

    override fun choices(start: Int, count: Int): List<String> {
        if (!ready || start < 0 || count <= 0) return emptyList()
        if (!variantPicked) return if (start == 0 && composing()) listOf(NO_VARIANT) else emptyList()
        if (offered.isNotEmpty() && !composing()) return suggestionsWithOffered().drop(start).take(count)
        return rime.choices(start, count)
    }

    override fun pickAt(index: Int): EngineState {
        if (!ready || index < 0) return EngineState.UNHANDLED
        if (offered.isNotEmpty() && !composing()) return pickOffered(index)
        if (!variantPicked) return read(handled = true)
        return read(handled = rime.selectAt(index))
    }

    override fun page(forward: Boolean): EngineState = when {
        offered.isNotEmpty() && !composing() -> read(handled = true)
        !composing() -> dismissed()
        !variantPicked -> read(handled = true)
        else -> send(if (forward) RimeKeys.PAGE_DOWN else RimeKeys.PAGE_UP)
    }

    override fun flick(): EngineState {
        if (composing() && rime.dropLast()) return read(handled = true)
        // The picked prefix is still part of the composition. Cancel it without sending anything to the field.
        return reset()
    }

    override fun reset(): EngineState {
        offered = emptyList()
        if (!ready) return EngineState.UNHANDLED
        rime.reset()
        // Clearing makes the predictor offer the last word's suggestions again: a new field, or Back, starts with none.
        dismiss()
        return read(handled = true)
    }

    private fun composing() = ready && rime.read()?.input?.isNotEmpty() == true

    /** Rime offers next-word suggestions: choices with nothing typed. */
    private fun suggesting(): Boolean {
        if (!ready) return false
        val raw = rime.read() ?: return false
        return raw.input.isEmpty() && raw.choices.isNotEmpty()
    }

    /** Clears the suggestions, if any, with Escape: the predictor then offers none until the next commit. */
    private fun dismiss() {
        if (suggesting()) rime.process(RimeKeys.ESCAPE, 0)
    }

    /** A key that's the keyboard's own with nothing typed: the suggestions go, and the bar with them. */
    private fun dismissed(): EngineState {
        if (!ready || !suggesting()) return EngineState.UNHANDLED
        dismiss()
        return read(handled = false)
    }

    private fun send(keysym: Int) = read(handled = rime.process(keysym, 0))

    /** The key isn't Rime's: the code being typed goes as its first choice, and the keyboard types the key after it. */
    private fun passOn(): EngineState {
        if (!composing()) return EngineState.UNHANDLED
        if (!variantPicked) return read(handled = false)
        rime.process(RimeKeys.SPACE, 0)
        return read(handled = false)
    }

    private fun read(handled: Boolean): EngineState {
        val raw = rime.read() ?: return EngineState(handled = handled)
        val code = if (raw.input.isEmpty()) "" else raw.preedit ?: raw.input
        // Suggestions only once a variant is picked: before, they'd be Rime's Traditional, whatever the user wants.
        if (code.isEmpty() && !variantPicked) return EngineState(commit = raw.commit.orEmpty(), handled = handled)
        if (code.isNotEmpty() && !variantPicked) return EngineState(commit = raw.commit.orEmpty(), code = code, choices = listOf(NO_VARIANT),
            handled = handled, typed = raw.input)
        // The bar's words on offer, while Rime has nothing of its own to show.
        if (code.isEmpty() && offered.isNotEmpty())
            return EngineState(commit = raw.commit.orEmpty(), choices = raw.choices + offered.filter { it !in raw.choices }, handled = handled)
        return EngineState(
            commit = raw.commit.orEmpty(),
            code = code,
            choices = raw.choices,
            page = raw.page,
            lastPage = raw.lastPage,
            handled = handled,
            pages = raw.pages,
            typed = raw.input,
            partial = raw.partial,
        )
    }

    companion object {
        /** The engine's name in im.json. */
        const val ENGINE = "rime"
        /**
         * The keyboard's option for Yale's and Jyutping's lazy sounds (Settings, on by default): n and l, j and z, ng and no
         * initial, gw as g. Off selects the schema's strict twin (data.sh's tk_*_strict), with its own spellings only.
         */
        const val LAZY_SOUNDS = "tk_lazy_sounds"
        /** The schemas with lazy sounds, and their strict twins' suffix. */
        val LAZY_SCHEMAS = setOf("tk_yale", "tk_jyutping")
        const val STRICT = "_strict"
        /** How many of Rime's own next words are read, at most: more than the row and its menu's first page show. */
        private const val MOST_SUGGESTIONS = 64

        /** The only choice until a Chinese variant is picked. F (docs/words-m1.md). */
        const val NO_VARIANT = "Simplified or Traditional? Settings."

        /** How many engines have each Rime open: it closes when the last stops. */
        private val users = IdentityHashMap<RimeCalls, Int>()

        /** The engine that last selected a schema on each Rime's one session, since it opened: that engine types. */
        private val selector = IdentityHashMap<RimeCalls, RimeEngine>()

        private fun selected(rime: RimeCalls, engine: RimeEngine) = synchronized(users) { selector[rime] = engine }

        /** Whether [engine] may select a schema while warming: no other engine has selected one since Rime opened. */
        private fun claimed(rime: RimeCalls, engine: RimeEngine) = synchronized(users) {
            val by = selector[rime]
            by == null || by === engine
        }

        /** How many syncs have run: each closes Rime's session, so an engine selects its schema again after one. */
        @Volatile private var syncs = 0

        /**
         * Rime's own backup of the learned words ([RimeBackup]): merges in every snapshot in [data]'s sync folder, a restored
         * backup's included, and writes its own. Through the open Rime if an engine has it, else Rime opens for it. Takes
         * only Rime's count, never an engine, so it can't wait on one that waits on it.
         */
        fun sync(rime: RimeCalls, data: RimeData): Boolean = synchronized(users) {
            val merging = RimeBackup.mergeable(data)
            val ok = if ((users[rime] ?: 0) > 0) rime.sync() else {
                data.prepareUser()
                runCatching { rime.open(data.shared.path, data.user.path) && rime.sync().also { rime.close() } }.getOrDefault(false)
            }
            syncs++
            if (ok) merging.forEach { it.delete() }
            ok
        }

        /**
         * Puts the data of the input methods on now in place under a Rime that's open, and opens it again with them: the
         * copy can't change files Rime has open. Each engine selects its schema again after, as after a [sync]. False if
         * Rime wasn't open, or doesn't open again.
         */
        private fun reopen(rime: RimeCalls, data: RimeData, install: (RimeData) -> Boolean): Boolean = synchronized(users) {
            if ((users[rime] ?: 0) == 0) return false
            rime.close()
            val ok = runCatching { install(data) && rime.open(data.shared.path, data.user.path) }.getOrDefault(false)
            syncs++
            ok
        }

        private fun opened(rime: RimeCalls, open: () -> Boolean): Boolean = synchronized(users) {
            val count = users[rime] ?: 0
            if (count == 0 && !open()) return false
            users[rime] = count + 1
            true
        }

        private fun closed(rime: RimeCalls) = synchronized(users) {
            val count = (users[rime] ?: 1) - 1
            if (count <= 0) {
                users.remove(rime)
                selector.remove(rime)
                rime.close()
            } else {
                users[rime] = count
            }
        }
    }
}
