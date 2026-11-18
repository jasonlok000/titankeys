package com.titankeys.keyboard

/**
 * An input method's engine: code that turns keys into text with choices, for an input method whose keys don't type letters
 * straight into the field (docs/languages.md, "How Rime plugs in"). Rime (Chinese) is the first; Korean and Telex can follow.
 * Data-only input methods (Spanish, Russian...) have no engine.
 *
 * The keyboard owns the field. While an engine is current, the keyboard hands it letters, Space, Enter and Backspace before
 * anything else, and writes only [EngineState.commit] to the field. The text being built ([EngineState.code]) and the choices
 * show on the bar, in the picker. Every call happens on the keyboard's main thread, except [start], which may be slow and is
 * called on a background thread before the first key.
 *
 * An engine never touches Android's views or the field itself, so it can be checked on the JVM.
 */
interface InputEngine {
    /**
     * Gets ready to type [method], which names the engine's own data in [InputMethod.engineArg] (Rime's schema, for one).
     * [dataDir] is a folder the engine may keep files in. Returns false if it can't start; the keyboard then says so on the bar.
     */
    fun start(method: InputMethod, dataDir: java.io.File): Boolean

    /** A printable key: a letter, digit or punctuation mark, already in the case Shift gives. */
    fun key(char: Char): EngineState

    /** Space. With text being built, it usually picks the first choice. */
    fun space(): EngineState

    /** Enter. With text being built, it usually types the code as typed. */
    fun enter(): EngineState

    /** Backspace. With text being built it deletes from it; with none, [EngineState.handled] is false and the field gets it. */
    fun backspace(): EngineState

    /** Picks the choice at [index] on the page shown. */
    fun pick(index: Int): EngineState

    /** Turns the page of choices, forward or back. */
    fun page(forward: Boolean): EngineState

    /**
     * Up to [count] choices from choice [start], across every page, for the bar's row and its bigger menu (docs/languages.md,
     * "Chinese on the bar"). Empty from an engine that has only the page in its state (Korean, Telex): the keyboard uses that.
     */
    fun choices(start: Int, count: Int): List<String> = emptyList()

    /** Picks choice [index] counted across every page, as [choices] gives them. */
    fun pickAt(index: Int): EngineState = pick(index)

    /**
     * The bar's own words (starters, learned and listed next words), offered as the engine's choices while it builds nothing
     * and suggests nothing, so they show in the engine's wide row, as many as fit (owner, 2026-11-17: "there shd be more than 3
     * words for chinese for suggestion"). A pick of one types it with no space, and the engine's next words for it follow
     * (owner: "chinese suggestion (first word) doesn't get updated after pressed and types a space"). UNHANDLED from an
     * engine that offers none: the bar keeps them.
     */
    fun offer(words: List<String>): EngineState = EngineState.UNHANDLED

    /**
     * A flick while text is being built (owner, 2026-11-17): the code still pending goes, and what was picked of it stays; the
     * next flick takes the last picked word. An engine without picks in part drops everything, as [reset].
     */
    fun flick(): EngineState = reset()

    /** Drops whatever is being built, as when the field changes or the input method is switched away. */
    fun reset(): EngineState

    /**
     * An option the keyboard sets from Settings, such as the Chinese variant: exactly one of `zh_hk`, `zh_tw` and `zh_hans` is
     * on (docs/languages.md, "Settings > Languages").
     */
    fun option(name: String, on: Boolean)

    /**
     * Gets the engine's data and library ready in the background, before anyone types with it, so a switch to it never waits
     * (owner, 2026-11-17: "is the loading for the engine on load avoidable?"). The keyboard then types with this same engine
     * when it switches to one of its input methods ([start] on it again), so the work done here is never done twice: Rime
     * has its schema selected already. [stop] frees it.
     */
    fun warm(method: InputMethod, dataDir: java.io.File): Boolean = start(method, dataDir)

    /** Frees what [start] loaded. The engine may be started again later. */
    fun stop()

    /** What the last [start] or [warm] took, part by part, for the keyboard's log ("Input method … loaded in"); "" if it says nothing. */
    val timing: String get() = ""
}

/** Picks the displayed word only while it still occupies this engine index. */
internal fun InputEngine.pickDisplayed(index: Int, word: String): EngineState? =
    if (choices(index, 1).singleOrNull() == word) pickAt(index) else null

/**
 * What an engine returns after each call.
 *
 * - [commit]: text to write to the field now, possibly empty.
 * - [code]: the text being built, as the user typed it (Rime's preedit), empty when nothing is being built.
 * - [choices]: the choices on this page, first first. Empty when there are none.
 * - [page], [lastPage]: the page shown, counted from 0, and whether another follows.
 * - [pages]: how many pages there are, or 0 when the engine can't tell (Rime can't): the bar then shows the page and an arrow.
 * - [handled]: false when the key meant nothing to the engine (Backspace with nothing built, for one), so the keyboard
 *   handles it as usual.
 * - [typed]: the letters as typed, without the engine's spelling or spaces (Rime's input): "hello" where [code] is "he l lo".
 * - [partial]: the choices cover only part of the code: the engine couldn't read the rest (Rime's "world" reads only "wo").
 */
data class EngineState(
    val commit: String = "",
    val code: String = "",
    val choices: List<String> = emptyList(),
    val page: Int = 0,
    val lastPage: Boolean = true,
    val handled: Boolean = true,
    val pages: Int = 0,
    val typed: String = "",
    val partial: Boolean = false,
) {
    /**
     * Where the choices are, for the bar: "1/3" when the engine knows how many pages there are, else the page and "›" while
     * more follow, as "2›". Empty with only one page.
     */
    val pageMark: String get() = when {
        page == 0 && lastPage -> ""
        pages > 0 -> "${page + 1}/$pages"
        else -> "${page + 1}${if (lastPage) "" else "›"}"
    }

    /** True while text is being built, so the picker stays on the bar. */
    val composing: Boolean get() = code.isNotEmpty()

    companion object {
        /** Nothing happened, and the keyboard should handle the key itself. */
        val UNHANDLED = EngineState(handled = false)
    }
}

/**
 * Each engine's name in `im.json` and its class, which [InputEngines] makes by name. Every class here implements
 * [InputEngine] and has a constructor that takes an Android Context; android/proguard-rules.pro keeps both, as R8 can't see a
 * class made by name and drops it from the APK otherwise (Korean and Vietnamese typed English on the phone).
 * tests/languages checks that every engine an input method names is here, and tests/languages/apk.sh that the built APK has
 * each class and constructor.
 */
object EngineClasses {
    val BY_NAME = mapOf(
        "rime" to "com.titankeys.rime.RimeEngine",
        "hangul" to "com.titankeys.keyboard.HangulEngine",
        "telex" to "com.titankeys.keyboard.TelexEngine",
        "rules" to "com.titankeys.keyboard.RulesEngine",
        "mozc" to "com.titankeys.mozc.MozcEngine",
    )
}
