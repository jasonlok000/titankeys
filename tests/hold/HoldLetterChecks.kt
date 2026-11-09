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
