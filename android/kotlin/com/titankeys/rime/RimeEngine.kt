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
    /** Puts the data in place under the folder it's given; true once it's there. */
    private val install: (RimeData) -> Boolean,
) : InputEngine {
    /** The engine the keyboard makes by name (InputEngines): the phone's Rime, with the data from the APK. */
    constructor(context: Context) : this(RimeNative, RimeAssets.installer(context))

    /** TitanKeys' schemas, one per Chinese input method: an input method's `"engine": { "rime": "<id>" }`. */
    enum class Schema(val id: String) { Yale("tk_yale"), Jyutping("tk_jyutping"), Pinyin("tk_pinyin") }

    @Volatile private var open = false
    @Volatile var schema: String? = null
        private set
    /** The options set so far, set again after every schema switch, which resets them. */
    private val options = linkedMapOf<String, Boolean>()

    /** Whether a Chinese variant is on: until one is, Rime's choices aren't shown. */
    val variantPicked get() = synchronized(this) { ChineseVariant.entries.any { options[it.option] == true } }

    override fun start(method: InputMethod, dataDir: File): Boolean {
        val id = method.engineArg ?: return false
        if (method.engine != ENGINE) return false
        val data = RimeData(dataDir)
        synchronized(this) {
            if (!open) open = runCatching { opened(rime) { data.prepareUser(); install(data) && rime.open(data.shared.path, data.user.path) } }
                .getOrDefault(false)
            if (!open) return false
            if ((schema != id || seen != syncs) && !select(id)) return false
            // A restored backup's learned words merge in now ([RimeBackup]), once the schema has opened its dictionary.
            if (RimeBackup.pending(data)) {
                sync(rime, data)
                if (!select(id)) return false
            }
        }
        return true
    }

    /**
     * Unpacks the data the first time and opens Rime, selecting no schema: Rime has one session, and the engine that types
     * owns it. Rime stays open while this engine is warm, so the engine that types only selects its schema.
     */
    override fun warm(method: InputMethod, dataDir: File): Boolean {
        if (method.engine != ENGINE) return false
        val data = RimeData(dataDir)
        synchronized(this) {
            if (!open) open = runCatching { opened(rime) { data.prepareUser(); install(data) && rime.open(data.shared.path, data.user.path) } }
                .getOrDefault(false)
        }
        return open
    }

    /** The sync count when this engine last selected its schema: a sync since closed the session, so it selects it again. */
    private var seen = -1

    /** Selects [id] on Rime's session, with every option set so far. */
    private fun select(id: String): Boolean {
        schema = null
        seen = syncs
        if (!rime.schema(id)) return false
        schema = id
        for ((name, on) in options) rime.option(name, on)
        return true
    }

    private fun reselect() = synchronized(this) {
        val id = schema ?: return@synchronized
        schema = null
        seen = syncs
        if (rime.schema(id)) {
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
    override fun option(name: String, on: Boolean) {
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

    override fun key(char: Char): EngineState {
        if (!ready) return EngineState.UNHANDLED
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
        !composing() -> EngineState.UNHANDLED
        !variantPicked -> read(handled = true)
        else -> send(RimeKeys.SPACE)
    }

    /** Types the code as letters, as typed (Rime's input), and clears it. With no code, Enter is the keyboard's. */
    override fun enter(): EngineState {
        if (!composing()) return EngineState.UNHANDLED
        val letters = rime.read()?.input.orEmpty()
        rime.reset()
        return read(handled = true).let { it.copy(commit = it.commit + letters) }
    }

    override fun backspace(): EngineState = if (composing()) send(RimeKeys.BACKSPACE) else EngineState.UNHANDLED

    override fun pick(index: Int): EngineState {
        if (!ready || index < 0) return EngineState.UNHANDLED
        if (!variantPicked) return read(handled = true)
        return read(handled = rime.select(index))
    }

    override fun choices(start: Int, count: Int): List<String> {
        if (!ready || start < 0 || count <= 0) return emptyList()
        if (!variantPicked) return if (start == 0 && composing()) listOf(NO_VARIANT) else emptyList()
        return rime.choices(start, count)
    }

    override fun pickAt(index: Int): EngineState {
        if (!ready || index < 0) return EngineState.UNHANDLED
        if (!variantPicked) return read(handled = true)
        return read(handled = rime.selectAt(index))
    }

    override fun page(forward: Boolean): EngineState = when {
        !composing() -> EngineState.UNHANDLED
        !variantPicked -> read(handled = true)
        else -> send(if (forward) RimeKeys.PAGE_DOWN else RimeKeys.PAGE_UP)
    }

    override fun reset(): EngineState {
        if (!ready) return EngineState.UNHANDLED
        rime.reset()
        return read(handled = true)
    }

    private fun composing() = ready && rime.read()?.input?.isNotEmpty() == true

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
        if (code.isNotEmpty() && !variantPicked) return EngineState(commit = raw.commit.orEmpty(), code = code, choices = listOf(NO_VARIANT),
            handled = handled, typed = raw.input)
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

        /** The only choice until a Chinese variant is picked. F (docs/words-m1.md). */
        const val NO_VARIANT = "Simplified or Traditional? Settings."

        /** How many engines have each Rime open: it closes when the last stops. */
        private val users = IdentityHashMap<RimeCalls, Int>()

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
                rime.close()
            } else {
                users[rime] = count
            }
        }
    }
}
