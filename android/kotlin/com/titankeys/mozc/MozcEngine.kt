package com.titankeys.mozc

import android.content.Context
import com.titankeys.keyboard.ChineseKeys
import com.titankeys.keyboard.EngineState
import com.titankeys.keyboard.InputEngine
import com.titankeys.keyboard.InputMethod
import com.titankeys.rime.RimeData
import java.io.File
import java.util.IdentityHashMap

/**
 * Japanese through Mozc (docs/languages.md, "Japanese"), on the same bar as Chinese: romaji letters go to Mozc, which builds
 * hiragana (the floating line) and gives every choice at once, whole sentences first, in Android's way. Space types the
 * first choice; a pick types what it covers, and the rest of the text stays (its romaji worked out again by
 * [KanaRomaji]). Enter types the romaji as typed. Backspace takes off the last romaji letter: Mozc starts again from the
 * letters left, so it's always a letter, never a whole kana. Punctuation is Japanese full-width ([marks]); digits stay
 * plain; with text being built, its first choice goes first.
 *
 * After a pick, Mozc offers the words that may come next (its zero-query suggestions), as Chinese does (#56; docs/languages.md,
 * "Next-word suggestions"): they're its choices with nothing typed, so the bar shows them as it shows a text's. Space picks
 * the first, a tap or the bigger menu any, and a pick offers the next ones; a letter starts new text instead, and any other
 * key clears them and is the keyboard's own. A new field starts with none ([reset]).
 *
 * [start] runs on a background thread: it copies Mozc's data out of the APK the first time (one file, 19 MB, through
 * [RimeData]'s checked copy), and opens Mozc. Everything else runs on the keyboard's main thread. Mozc is one per app
 * (tk_jni.cc keeps one session): it closes only when no engine uses it.
 */
class MozcEngine(
    private val mozc: MozcCalls,
    /** Puts the data in place under the folder it's given; true once it's there. */
    private val install: (RimeData) -> Boolean,
) : InputEngine {
    constructor(context: Context) : this(MozcNative, MozcAssets.installer(context))

    @Volatile private var open = false
    /** The romaji typed for the text being built. */
    private val typed = StringBuilder()
    /** Mozc's state after the last call. */
    private var last = MozcCalls.Raw(null, "", emptyList())

    override fun start(method: InputMethod, dataDir: File): Boolean {
        if (method.engine != ENGINE) return false
        val data = RimeData(dataDir)
        synchronized(this) {
            if (!open) open = runCatching {
                opened(mozc) { data.user.mkdirs(); install(data) && mozc.open(File(data.shared, DATA).path, data.user.path) }
            }.getOrDefault(false)
        }
        return open
    }

    override fun stop() {
        synchronized(this) {
            if (open) closed(mozc)
            open = false
            typed.clear()
        }
    }

    override fun option(name: String, on: Boolean) = Unit

    private fun composing() = open && typed.isNotEmpty()

    /** Mozc offers next-word suggestions: choices with nothing typed. */
    private fun suggesting() = open && typed.isEmpty() && last.choices.isNotEmpty()

    /** Clears the suggestions, if any, in Mozc too, so the next read has none. */
    private fun dismiss() {
        if (!suggesting()) return
        mozc.reset()
        last = MozcCalls.Raw(null, "", emptyList())
    }

    /** A key that's the keyboard's own with nothing typed: the suggestions go, and the bar with them. */
    private fun dismissed(): EngineState {
        if (!suggesting()) return EngineState.UNHANDLED
        dismiss()
        return read(handled = false)
    }

    override fun key(char: Char): EngineState {
        if (!open) return EngineState.UNHANDLED
        // A letter starts new text over the suggestions; anything else clears them first.
        if (char !in 'a'..'z' && char !in 'A'..'Z') dismiss()
        return when (char) {
            in 'a'..'z' -> letter(char)
            in 'A'..'Z' -> letter(char.lowercaseChar())
            // ー while text is built; on its own, the keyboard's.
            '-' -> if (composing()) letter('-') else mark(char)
            in '0'..'9' -> if (composing()) passOn() else EngineState.UNHANDLED
            else -> if (ChineseKeys.isPunctuation(char)) mark(char) else passOn()
        }
    }

    private fun letter(char: Char): EngineState {
        typed.append(char)
        mozc.key(char)
        return read(handled = true)
    }

    /** A punctuation key: the text being built goes as its first choice, then the Japanese mark. */
    private fun mark(char: Char): EngineState {
        val first = if (composing()) firstChoice() else ""
        return read(handled = true, extra = first + marks(char))
    }

    /** A key Mozc has no part in: the text being built goes as its first choice, and the keyboard types the key after it. */
    private fun passOn(): EngineState {
        if (!composing()) return EngineState.UNHANDLED
        val first = firstChoice()
        return read(handled = false, extra = first)
    }

    /**
     * Picks the first choice, so Mozc learns it, and returns what it typed. If it covered only part, the rest goes as it
     * shows (hiragana): a mark or a digit ends the text being built.
     */
    private fun firstChoice(): String {
        var out = ""
        if (last.choices.isNotEmpty()) {
            mozc.pick(0)
            val raw = mozc.read()
            out = raw?.commit.orEmpty() + raw?.preedit.orEmpty()
        } else out = last.preedit
        mozc.reset()
        typed.clear()
        mozc.read()
        last = MozcCalls.Raw(null, "", emptyList())
        return out
    }

    /** Types the first choice; with nothing typed, the first suggestion, if there are any. */
    override fun space(): EngineState = if (composing() || suggesting()) pickAt(0) else EngineState.UNHANDLED

    override fun enter(): EngineState {
        if (!composing()) return dismissed()
        val letters = typed.toString()
        mozc.reset()
        typed.clear()
        return read(handled = true, extra = letters)
    }

    override fun backspace(): EngineState {
        if (!composing()) return dismissed()
        typed.deleteCharAt(typed.length - 1)
        mozc.reset()
        for (c in typed) mozc.key(c)
        return read(handled = true)
    }

    override fun pick(index: Int): EngineState = pickAt(index)

    override fun pickAt(index: Int): EngineState {
        if (!(composing() || suggesting()) || index < 0) return EngineState.UNHANDLED
        val picked = mozc.pick(index)
        val state = read(handled = picked)
        // A part picked: the rest is hiragana, which Enter must still type as romaji.
        typed.clear()
        if (state.code.isNotEmpty()) typed.append(KanaRomaji.of(state.code) ?: state.code)
        return state.copy(typed = typed.toString())
    }

    override fun choices(start: Int, count: Int): List<String> =
        if (!(composing() || suggesting()) || start < 0 || count <= 0) emptyList() else last.choices.drop(start).take(count)

    override fun page(forward: Boolean): EngineState = EngineState.UNHANDLED

    override fun reset(): EngineState {
        if (!open) return EngineState.UNHANDLED
        mozc.reset()
        typed.clear()
        return read(handled = true)
    }

    private fun read(handled: Boolean, extra: String = ""): EngineState {
        val raw = mozc.read() ?: MozcCalls.Raw(null, "", emptyList())
        last = raw
        val code = if (typed.isEmpty()) "" else raw.preedit
        return EngineState(
            commit = raw.commit.orEmpty() + extra,
            code = code,
            // With nothing being built, Mozc's choices are its next-word suggestions.
            choices = if (code.isEmpty() && raw.preedit.isNotEmpty()) emptyList() else raw.choices.take(PAGE),
            handled = handled,
            typed = typed.toString(),
        )
    }

    companion object {
        /** The engine's name in im.json. */
        const val ENGINE = "mozc"
        /** Mozc's data file, in the data's folder. */
        const val DATA = "mozc.data"
        /** The choices in a state: the row reads the rest through [choices]. */
        const val PAGE = 8

        /** Japanese marks for the punctuation keys: see [JapaneseMarks]. */
        fun marks(char: Char) = JapaneseMarks.of(char)

        private val users = IdentityHashMap<MozcCalls, Int>()

        private fun opened(mozc: MozcCalls, open: () -> Boolean): Boolean = synchronized(users) {
            val count = users[mozc] ?: 0
            if (count == 0 && !open()) return false
            users[mozc] = count + 1
            true
        }

        private fun closed(mozc: MozcCalls) = synchronized(users) {
            val count = (users[mozc] ?: 1) - 1
            if (count <= 0) {
                users.remove(mozc)
                mozc.close()
            } else users[mozc] = count
        }
    }
}

/** Mozc's data in the APK: assets/mozc/mozc.data, listed in assets/mozc.sha256 (native/mozc/build.sh). */
object MozcAssets {
    const val MANIFEST = "mozc.sha256"
    const val ASSETS = "mozc"

    /** Copies the data into [RimeData]'s shared folder, if it isn't there yet: for [MozcEngine]'s `install`. */
    fun installer(context: Context): (RimeData) -> Boolean {
        val assets = context.applicationContext.assets
        return { data ->
            val manifest = assets.open(MANIFEST).bufferedReader().use { it.readText() }
            data.install(manifest) { path -> assets.open("$ASSETS/$path") }
        }
    }
}
