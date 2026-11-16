package com.atlas.rime

import com.atlas.keyboard.EngineState
import com.atlas.keyboard.InputEngine
import com.atlas.keyboard.InputMethod
import java.io.File

/**
 * Chinese through Rime (docs/languages.md, "How Rime plugs in"). A letter goes to Rime, which returns the code and the
 * choices for the picker. Space types the first choice, Enter types the code as typed letters, Backspace deletes from the
 * code. With no code typed, Space, Enter and Backspace are the keyboard's own. Punctuation goes through Rime's punctuator,
 * which gives Chinese marks (，。). A capital, or a character Rime has no key for, types as it is, after the code being
 * typed goes as its first choice.
 *
 * [start] runs on a background thread: it copies Rime's data out of the APK the first time ([install], see [RimeData] and
 * [RimeAssets]), opens Rime once for every Chinese input method, and selects the input method's schema. Everything else
 * runs on the keyboard's main thread. Rime's own lock (rime_jni.cc) keeps the two apart.
 */
class RimeEngine(
    private val rime: RimeCalls = RimeNative,
    /** Puts the data in place under the folder it's given; true once it's there. */
    private val install: (RimeData) -> Boolean,
) : InputEngine {
    /** TitanKeys' schemas, one per Chinese input method: an input method's `"engine": { "rime": "<id>" }`. */
    enum class Schema(val id: String) { Yale("tk_yale"), Jyutping("tk_jyutping"), Pinyin("tk_pinyin") }

    @Volatile private var open = false
    @Volatile var schema: String? = null
        private set
    /** The options set so far, set again after every schema switch, which resets them. */
    private val options = linkedMapOf<String, Boolean>()

    override fun start(method: InputMethod, dataDir: File): Boolean {
        val id = method.engineArg ?: return false
        if (method.engine != ENGINE) return false
        synchronized(this) {
            if (!open) {
                val data = RimeData(dataDir)
                open = runCatching { install(data) && rime.open(data.shared.path, data.user.path) }.getOrDefault(false)
            }
            if (!open) return false
            if (schema != id) {
                if (!rime.schema(id)) return false
                schema = id
            }
            for ((name, on) in options) rime.option(name, on)
        }
        return true
    }

    override fun stop() {
        synchronized(this) {
            if (open) rime.close()
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

    /** Sets [variant]'s option on and the others off. */
    fun variant(variant: ChineseVariant) {
        for ((name, on) in ChineseVariant.options(variant)) option(name, on)
    }

    private val ready get() = open && schema != null

    /** Rime's state now, with nothing sent. */
    fun state(): EngineState = if (ready) read(handled = false) else EngineState.UNHANDLED

    override fun key(char: Char): EngineState {
        if (!ready) return EngineState.UNHANDLED
        return when (char) {
            in 'a'..'z' -> send(char.code)
            // Rime takes printable ASCII (digits pick a choice, punctuation becomes Chinese marks); capitals and anything else
            // type as they are.
            in 'A'..'Z' -> passOn()
            in ' '..'~' -> send(char.code)
            else -> passOn()
        }
    }

    override fun space(): EngineState = if (composing()) send(RimeKeys.SPACE) else EngineState.UNHANDLED

    override fun enter(): EngineState = if (composing()) send(RimeKeys.RETURN) else EngineState.UNHANDLED

    override fun backspace(): EngineState = if (composing()) send(RimeKeys.BACKSPACE) else EngineState.UNHANDLED

    override fun pick(index: Int): EngineState {
        if (!ready || index < 0) return EngineState.UNHANDLED
        return read(handled = rime.select(index))
    }

    override fun page(forward: Boolean): EngineState =
        if (composing()) send(if (forward) RimeKeys.PAGE_DOWN else RimeKeys.PAGE_UP) else EngineState.UNHANDLED

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
        rime.process(RimeKeys.SPACE, 0)
        return read(handled = false)
    }

    private fun read(handled: Boolean): EngineState {
        val raw = rime.read() ?: return EngineState(handled = handled)
        return EngineState(
            commit = raw.commit.orEmpty(),
            code = if (raw.input.isEmpty()) "" else raw.preedit ?: raw.input,
            choices = raw.choices,
            page = raw.page,
            lastPage = raw.lastPage,
            handled = handled,
        )
    }

    companion object {
        /** The engine's name in im.json. */
        const val ENGINE = "rime"
    }
}
