package com.atlas.keyboard

import com.atlas.engine.Swipe
import java.util.concurrent.atomic.AtomicBoolean

/** Checks that a held key replaces the letter it typed, through a real Composer. */

private val FIELD = Field(words = true, learning = true, fullStops = true, starters = false, sentenceCapitalisation = true,
    autocorrect = false, suggestions = true)

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private class FakeEditor : Editor {
    private val owner = Any()
    var text = ""
    /** Where the user has moved the cursor to, or null when it follows the typing. */
    var cursor: Int? = null
    val reports = ArrayDeque<Int>()
    private var reported = 0
    private val at get() = cursor ?: text.length

    private fun report() {
        if (text.length != reported) reports += text.length
        reported = text.length
    }

    override fun identity() = Editor.Identity(owner, "test", 1)
    override fun state(result: (Editor.State?) -> Unit) = result(Editor.State(text.take(at), text.drop(at), at, at))
    override fun before() = text.take(at)
    override fun after() = text.drop(at)
    override fun enter(press: EnterKey.Press) = Editor.Enter.Newline
    override fun replace(count: Int, text: String, after: Int): Boolean {
        this.text = this.text.dropLast(count) + text
        report()
        return true
    }
    override fun delete(word: Boolean, selectionStart: Int, selectionEnd: Int): Int {
        text = text.dropLast(1)
        report()
        return 1
    }
}

private class FakeWords : Words {
    var swiped = ""
    override fun recognise(swipe: Swipe, context: String, personalise: Boolean, result: (Words.Recognition) -> Unit) =
        result(Words.Recognition(listOf(swiped), null))
    override fun complete(before: String, cancelled: AtomicBoolean, sentenceCapitalisation: Boolean, result: (List<String>) -> Unit) = result(emptyList())
    override fun prepare(text: String, cancelled: AtomicBoolean) = Unit
    override fun lookAlike(context: String, word: String, next: String, result: (String?) -> Unit) = result(null)
    override fun allowed(word: String) = true
    override fun known(word: String) = true
    override fun typed(word: String, rememberCase: Boolean) = Unit
    override fun untyped(word: String) = Unit
    override fun keptPair(previous: String?, next: String) = Unit
    override fun chosen(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) = Unit
    override fun corrected(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) = Unit
    override fun kept(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) = Unit
}

private class Typist(autoCapitalise: Boolean = false) {
    val editor = FakeEditor()
    val words = FakeWords()
    private val posted = ArrayDeque<() -> Unit>()
    val composer = Composer(editor, words, { _, action -> posted += action }, {}, { autoCapitalise }, {})

    init {
        composer.start(FIELD, 0, 0)
        settle()
    }

    fun settle() {
        var budget = 1000
        while (budget-- > 0) {
            val report = editor.reports.removeFirstOrNull()
            if (report != null) {
                composer.selectionChanged(report, report)
                continue
            }
            (posted.removeFirstOrNull() ?: return)()
        }
    }

    /** Each key is typed on its press, in the order that the keyboard sends them. */
    fun type(text: String): Typist {
        for (character in text) {
            if (character == ' ') composer.space(false, 0L) else composer.type(character.toString())
            settle()
        }
        return this
    }

    fun hold(symbol: String): Typist {
        composer.replaceLetter(symbol)
        settle()
        return this
    }

    fun swipe(word: String): Typist {
        words.swiped = word
        val stroke = Swipe(floatArrayOf(.1f, .4f, .8f), floatArrayOf(.2f, .3f, .4f), floatArrayOf(0f, 100f, 200f))
        composer.swipe(stroke, 0, 200, 0)
        settle()
        return this
    }

    val text get() = editor.text
}

fun main() {
    quickPressTypesTheLetterOnly()
    heldPressReplacesOnce()
    onlyTheLastLetterIsReplaced()
    symbolTakesTheCaseOfTheSentence()
    symbolAfterASwipeMovesBeforeTheSpace()
    aLetterThatHasMovedStays()
    exactTyping()
    println("PASS: hold letter checks")
}

private fun quickPressTypesTheLetterOnly() {
    same(Typist().type("a").text, "a", "A quick press types the letter and nothing else")
}

private fun heldPressReplacesOnce() {
    val typist = Typist().type("a")
    same(typist.text, "a", "The letter types before the hold is decided")
    same(typist.hold("@").text, "@", "A held press replaces the letter with its symbol")
    same(typist.hold("#").text, "@", "A held press replaces the letter one time")
    same(typist.type("b").text, "@b", "Typing goes on after the symbol")
}

private fun onlyTheLastLetterIsReplaced() {
    same(Typist().type("ab").hold("@").text, "a@", "Only the letter of the held key is replaced")
    same(Typist().type("ab cd").hold("?").text, "ab c?", "A symbol after a word replaces only that letter")
}

private fun symbolTakesTheCaseOfTheSentence() {
    same(Typist(autoCapitalise = true).type("h").text, "H", "The first letter of a sentence types as a capital")
    same(Typist(autoCapitalise = true).type("h").hold("ñ").text, "Ñ", "A letter symbol at the start of a sentence is a capital")
    same(Typist(autoCapitalise = true).type("h").hold("@").text, "@", "A symbol with no case stays as it is")
    same(Typist(autoCapitalise = true).type("ab c").hold("ñ").text, "Ab ñ", "Inside a sentence a letter symbol is lowercase")
}

private fun symbolAfterASwipeMovesBeforeTheSpace() {
    same(Typist().swipe("hello").text, "hello ", "A swiped word ends with an automatic space")
    same(Typist().swipe("hello").type("q").hold("?").text, "hello?",
        "A held key does what typing its symbol does, so the symbol takes the place of the automatic space")
}

private fun aLetterThatHasMovedStays() {
    val moved = Typist().type("a")
    moved.editor.text = "xa"
    same(moved.hold("@").text, "xa", "A letter that the app has changed stays as it is")

    val typed = Typist().type("ab")
    typed.editor.cursor = 1
    typed.composer.selectionChanged(1, 1)
    typed.settle()
    same(typed.hold("@").text, "ab", "A held key must not replace a letter away from the cursor")
}

/**
 * Exact typing (issue 45), the way AtlasIme runs it: the hold engine sees each press first, a press it does not keep goes through
 * [ExactTyping.plan], and the keyboard keeps Shift, Alt and Sym. The owner's AnyDesk round found repeats of a held J, a one-time Alt
 * that stayed on or locked, and Sym that did not open. Each has a check here.
 */
private class ExactKeys {
    val modifiers = Modifiers()
    val sent = ArrayList<String>()
    private var held = 0
    private val engine = com.atlas.engine.HoldEngine { key ->
        sent += ExactTyping.takeBack.map(::describe)
        sent += "text " + symbolOf(key)
        held = 0
    }
    var time = 1000L

    /** [Modifiers.metaState] without Android's normalising, which the computer cannot run: the same bits for Shift and Alt. */
    private val meta: Int get() {
        var state = 0
        if (modifiers.shift != Modifiers.Level.Off) state = state or android.view.KeyEvent.META_SHIFT_ON or android.view.KeyEvent.META_SHIFT_LEFT_ON
        if (modifiers.alt != Modifiers.Level.Off) state = state or android.view.KeyEvent.META_ALT_ON or android.view.KeyEvent.META_ALT_LEFT_ON
        return state
    }

    private fun describe(event: ExactTyping.Event) =
        "${event.code} ${if (event.down) "down" else "up"}${if (event.meta != 0) " meta" else ""}"

    /** The fake key map: Alt gives the symbol printed on J, K and T, and Shift a capital. */
    private fun symbolOf(code: Int) = when (code) { J -> "#"; K -> "@"; T -> "|"; else -> "?" }
    private fun charFor(code: Int, meta: Int): Int = when {
        code == TAB -> 9
        meta and android.view.KeyEvent.META_ALT_MASK != 0 && code in listOf(J, K, T) -> symbolOf(code)[0].code
        code in listOf(J, K, T) -> ('a' + (code - android.view.KeyEvent.KEYCODE_A)).let { if (meta and android.view.KeyEvent.META_SHIFT_MASK != 0) it.uppercaseChar() else it }.code
        else -> 0
    }

    /** A press of a key, as the phone sends it, with [repeats] key repeats while it stays down. */
    fun press(code: Int, repeats: Int = 0, holdUntilSymbol: Boolean = false): ExactKeys {
        if (Modifiers.isModifier(code)) {
            modifiers.down(code, time)
            modifiers.up(code, time + 40)
            time += 400
            return this
        }
        val eligible = code in listOf(J, K, T) && meta == 0
        for (repeat in 0..repeats) {
            if (engine.down(code, 1, time, repeat > 0, eligible)) continue
            val plan = ExactTyping.plan(code, repeat, meta) { state -> charFor(code, state) }
            plan.text?.let { sent += "text $it" }
            sent += plan.events.map(::describe)
            modifiers.consume()
        }
        engine.pending()?.let { if (holdUntilSymbol) engine.holdDue(it.id, 0) }
        // Android's key repeat starts after about 400 ms, so a press with repeats is a long one.
        engine.up(code, 1, time + if (holdUntilSymbol || repeats > 0) 600 else 100)
        time += 1000
        return this
    }

    companion object {
        const val J = android.view.KeyEvent.KEYCODE_J
        const val K = android.view.KeyEvent.KEYCODE_K
        const val T = android.view.KeyEvent.KEYCODE_T
        const val TAB = android.view.KeyEvent.KEYCODE_TAB
        const val ALT = android.view.KeyEvent.KEYCODE_ALT_LEFT
        const val SYM = android.view.KeyEvent.KEYCODE_SYM
    }
}

private fun exactTyping() {
    val j = ExactKeys.J
    val k = ExactKeys.K
    val del = android.view.KeyEvent.KEYCODE_DEL
    for (repeats in listOf(0, 12)) {
        val keys = ExactKeys().press(ExactKeys.J, repeats = repeats, holdUntilSymbol = true)
        same(keys.sent, listOf("$j down", "$j up", "$del down", "$del up", "text #"),
            "Exact typing: a held J sends J once, takes it back and gives one symbol, with $repeats key repeats")
    }
    same(ExactKeys().press(ExactKeys.J).sent, listOf("$j down", "$j up"), "Exact typing: a J released before the hold time goes once")

    ExactKeys().press(ExactKeys.ALT).press(ExactKeys.J).press(ExactKeys.K).press(ExactKeys.ALT).press(ExactKeys.J).press(ExactKeys.K).also {
        same(it.sent, listOf("text #", "$k down", "$k up", "text #", "$k down", "$k up"),
            "Exact typing: Alt then a letter gives the symbol at once, two times, and the next letter is a letter")
        same(it.modifiers.alt, Modifiers.Level.Off, "Exact typing: Alt is off after its letter")
    }
    val tab = ExactKeys.TAB
    val alt = ExactKeys.ALT
    ExactKeys().press(ExactKeys.ALT).press(ExactKeys.TAB).press(ExactKeys.K).also {
        same(it.sent, listOf("$alt down meta", "$tab down meta", "$tab up meta", "$alt up meta", "$k down", "$k up"),
            "Exact typing: Alt and Tab go as a shortcut with Alt's own key, and Alt does not stay on")
        same(it.modifiers.alt, Modifiers.Level.Off, "Exact typing: no Alt is left on after a shortcut")
    }
    same(ExactKeys().also { it.modifiers.doublePressLocks = true }.press(ExactKeys.ALT).also { it.time += 0 }.sent.isEmpty(), true,
        "Exact typing: Alt itself never goes to the app")
    ExactKeys().also { it.modifiers.doublePressLocks = true }.also { keys ->
        keys.modifiers.down(ExactKeys.ALT, 1000); keys.modifiers.up(ExactKeys.ALT, 1040)
        keys.modifiers.down(ExactKeys.ALT, 1100); keys.modifiers.up(ExactKeys.ALT, 1140)
        keys.time = 2000
        keys.press(ExactKeys.J).press(ExactKeys.K)
        same(keys.sent, listOf("text #", "text @"), "Exact typing: a locked Alt gives symbols until it is pushed again")
        keys.press(ExactKeys.ALT).press(ExactKeys.K)
        same(keys.sent.last(), "$k up", "Exact typing: a third push of Alt ends the lock")
    }
    ExactKeys().press(ExactKeys.SYM).also {
        same(it.modifiers.panel, Modifiers.Panel.Symbols, "Exact typing: Sym opens the panel")
        same(it.sent, emptyList<String>(), "Exact typing: Sym never goes to the app")
    }
}
