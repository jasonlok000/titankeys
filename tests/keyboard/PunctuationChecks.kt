package com.atlas.keyboard

import android.os.Looper
import android.text.InputType
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.SurroundingText
import com.atlas.engine.Swipe
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { _, error ->
        error.printStackTrace()
        kotlin.system.exitProcess(1)
    }
    Looper.prepare()
    for (source in listOf("surrounding", "cursor", "extracted")) for (prefix in listOf("", "word ".repeat(450)))
        for (punctuation in listOf("!", "?", ",", ".")) for (capitalise in listOf(false, true)) {
            var text = prefix
            lateinit var editor: ConnectionEditor
            val connection = Proxy.newProxyInstance(InputConnection::class.java.classLoader,
                arrayOf(InputConnection::class.java)) { _, method, args ->
                when (method.name) {
                    "getTextBeforeCursor" -> text.takeLast(args!![0] as Int)
                    "getTextAfterCursor" -> ""
                    "getSurroundingText" -> if (source == "cursor") null else {
                        val window = text.takeLast(args!![0] as Int)
                        SurroundingText(window, window.length, window.length, if (source == "extracted") -1 else text.length - window.length)
                    }
                    "getExtractedText" -> if (source != "extracted") null else ExtractedText().apply {
                        this.text = text
                        startOffset = 0
                        partialStartOffset = -1
                        selectionStart = text.length
                        selectionEnd = text.length
                    }
                    "requestCursorUpdates" -> {
                        if (source == "cursor") editor.cursorAnchor(CursorAnchorInfo.Builder()
                            .setSelectionRange(text.length, text.length).build())
                        source == "cursor"
                    }
                    "beginBatchEdit", "endBatchEdit" -> true
                    "deleteSurroundingText" -> { text = text.dropLast(args!![0] as Int); true }
                    "commitText" -> { text += args!![0].toString(); true }
                    else -> error("Unexpected InputConnection call: ${method.name}")
                }
            } as InputConnection
            val info = EditorInfo().apply { packageName = "test.external.editor"; fieldId = 1 }
            editor = ConnectionEditor({ connection }, { info }, { _, _ -> })
            val words = object : Words {
                override fun lookAlike(context: String, word: String, next: String, result: (String?) -> Unit) = result(null)
                override fun allowed(word: String) = true
                override fun known(word: String) = true
                override fun recognise(swipe: Swipe, context: String, personalise: Boolean, result: (Words.Recognition) -> Unit) =
                    result(Words.Recognition(listOf("hello"), null))
                override fun complete(before: String, cancelled: AtomicBoolean, sentenceCapitalisation: Boolean, result: (List<String>) -> Unit) = result(emptyList())
                override fun typed(word: String, rememberCase: Boolean) = Unit
                override fun chosen(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) = Unit
                override fun corrected(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) = Unit
                override fun kept(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) = Unit
            }
            val composer = Composer(editor, words, { _, _ -> }, {}, { true }, {})
            val field = Field.of(InputType.TYPE_CLASS_TEXT or
                (if (capitalise) InputType.TYPE_TEXT_FLAG_CAP_SENTENCES else 0), 0)
            composer.start(field, text.length, text.length)
            composer.swipe(Swipe(floatArrayOf(.1f, .5f, .8f), floatArrayOf(.2f, .3f, .4f),
                floatArrayOf(0f, 40f, 80f)), 0, 80, 0)
            val word = if (prefix.isEmpty() && capitalise) "Hello" else "hello"
            check(text == "$prefix$word ") { "Swipe did not insert a word" }
            composer.selectionChanged(text.length, text.length)
            composer.type(punctuation)
            check(text == "$prefix$word$punctuation") { "Expected '$word$punctuation' with $source, got '${text.takeLast(12)}'" }
            text = "$prefix$word "
            composer.start(field, text.length, text.length)
            composer.type(punctuation)
            check(text == "$prefix$word $punctuation") { "Punctuation removed a manually entered space" }
        }
    lookAlikeChecks()
    savedWordChecks()
    println("PASS swipes respect field capitalisation; punctuation attaches after swipes and preserves manual spaces, with and without cursor reports and truncated context")
}

private class FakeEditor(var text: String = "") : Editor {
    private val known = object {}
    private val identity = Editor.Identity(known, "test.editor", 1)
    override fun identity() = identity
    override fun state(result: (Editor.State?) -> Unit) = result(Editor.State(text, "", text.length, text.length))
    override fun before() = text
    override fun after() = ""
    override fun replace(count: Int, text: String, after: Int): Boolean {
        this.text = this.text.dropLast(count) + text
        return true
    }
    override fun delete(word: Boolean, selectionStart: Int, selectionEnd: Int): Int? {
        if (text.isEmpty()) return 0
        val removed = if (word) text.length - text.trimEnd().dropLastWhile { !it.isWhitespace() }.length else 1
        text = text.dropLast(removed)
        return removed
    }
    var enterResult = Editor.Enter.Newline
    override fun enter() = enterResult
}

/** Runs one composer whose swipes return the queued words and whose engine swaps the listed look-alikes. */
private class LookAlikeRun(sentenceCase: Boolean = false, private val swaps: Map<String, String> = mapOf("we're" to "were")) {
    val editor = FakeEditor()
    val swiped = ArrayDeque<String>()
    val asked = ArrayList<String>()
    val saved = ArrayList<String>()
    val corrections = ArrayList<String>()
    var holdResult: (() -> Unit)? = null
    var hold = false
    private val waiting = ArrayDeque<() -> Unit>()
    private val words = object : Words {
        override fun lookAlike(context: String, word: String, next: String, result: (String?) -> Unit) {
            asked += "$context|$word|$next"
            val answer = { result(swaps[word.lowercase()]) }
            if (hold) holdResult = answer else answer()
        }
        override fun allowed(word: String) = true
        override fun known(word: String) = true
        override fun recognise(swipe: Swipe, context: String, personalise: Boolean, result: (Words.Recognition) -> Unit) =
            result(Words.Recognition(listOf(swiped.removeFirst(), "other"), null))
        override fun complete(before: String, cancelled: AtomicBoolean, sentenceCapitalisation: Boolean, result: (List<String>) -> Unit) = result(listOf("the"))
        override fun typed(word: String, rememberCase: Boolean) { saved += word }
        override fun chosen(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) = Unit
        override fun corrected(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) { corrections += word }
        override fun kept(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) = Unit
    }
    val composer = Composer(editor, words, { _, action -> waiting += action }, {}, { true }, {})

    init {
        composer.start(Field.of(InputType.TYPE_CLASS_TEXT or
            (if (sentenceCase) InputType.TYPE_TEXT_FLAG_CAP_SENTENCES else 0), 0), 0, 0)
        settle()
    }

    fun settle() {
        while (waiting.isNotEmpty()) waiting.removeFirst()()
        composer.selectionChanged(editor.text.length, editor.text.length)
    }

    fun swipe(word: String) {
        swiped += word
        composer.swipe(Swipe(floatArrayOf(.1f, .5f), floatArrayOf(.2f, .3f), floatArrayOf(0f, 40f)), 0, 40, 0)
        settle()
    }

    fun type(word: String) {
        for (letter in word) {
            composer.type(letter.toString())
            settle()
        }
    }

    fun space() {
        composer.space(false, 0)
        settle()
    }
}

private fun lookAlikeChecks() {
    LookAlikeRun().apply {
        swipe("we're")
        swipe("you")
        check(editor.text == "were you ") { "Next swipe did not correct: '${editor.text}'" }
        check(asked == listOf("|we're|you")) { "Checked $asked" }
        swipe("going")
        check(editor.text == "were you going ") { "Later words broke the text: '${editor.text}'" }
    }
    LookAlikeRun(sentenceCase = true).apply {
        swipe("we're")
        check(editor.text == "We're ") { "Sentence start was '${editor.text}'" }
        swipe("you")
        check(editor.text == "Were you ") { "Capital was lost: '${editor.text}'" }
    }
    LookAlikeRun().apply {
        swipe("we're")
        type("you")
        check(editor.text == "we're you") { "Corrected before the next word ended: '${editor.text}'" }
        space()
        check(editor.text == "were you ") { "Typed next word did not correct: '${editor.text}'" }
    }
    LookAlikeRun().apply {
        swipe("we're")
        type("you")
        composer.type("?")
        settle()
        check(editor.text == "were you?") { "Punctuation did not end the next word: '${editor.text}'" }
    }
    LookAlikeRun().apply {
        swipe("we're")
        composer.choose(0)
        settle()
        swipe("you")
        check(editor.text == "we're you ") { "A chosen word changed: '${editor.text}'" }
        check(asked.isEmpty()) { "Checked a chosen word: $asked" }
    }
    LookAlikeRun().apply {
        type("we're")
        space()
        swipe("you")
        check(editor.text == "we're you ") { "A typed word changed: '${editor.text}'" }
    }
    LookAlikeRun().apply {
        swipe("we're")
        swipe("you")
        check(editor.text == "were you ")
        repeat(9) { composer.backspace(false); settle() }
        check(editor.text.isEmpty()) { "Delete left '${editor.text}'" }
        swipe("we're")
        swipe("you")
        check(editor.text == "we're you ") { "A word changed back was changed again: '${editor.text}'" }
    }
    LookAlikeRun().apply {
        hold = true
        swipe("we're")
        swipe("you")
        composer.backspace(false)
        settle()
        holdResult!!()
        check(editor.text == "we're you") { "A late answer overwrote new text: '${editor.text}'" }
    }
    LookAlikeRun(swaps = emptyMap()).apply {
        swipe("were")
        swipe("you")
        check(editor.text == "were you ") { "An unsure answer changed text: '${editor.text}'" }
    }
    println("PASS the next word corrects one swiped look-alike, keeping capitals and leaving typed, chosen and changed-back words alone")
}

private fun savedWordChecks() {
    LookAlikeRun().apply {
        type("zorp")
        space()
        check(saved == listOf("zorp")) { "Space saved $saved" }
    }
    LookAlikeRun().apply {
        type("zorp")
        swipe("you")
        check(editor.text == "zorp you ") { "Swipe wrote '${editor.text}'" }
        check(saved == listOf("zorp")) { "A swipe after a typed word saved $saved" }
    }
    LookAlikeRun().apply {
        type("zorp,")
        swipe("you")
        check(saved == listOf("zorp")) { "A swipe after punctuation saved $saved" }
    }
    LookAlikeRun().apply {
        type("zor'")
        swipe("you")
        check(saved.isEmpty()) { "A swipe that joined the word saved $saved" }
    }
    LookAlikeRun().apply {
        type("zorp")
        composer.type("a", letterTap = true)
        settle()
        check(editor.text == "zorp a ") { "Tap key wrote '${editor.text}'" }
        check(saved == listOf("zorp")) { "A tap key after a typed word saved $saved" }
    }
    for (result in listOf(Editor.Enter.Action, Editor.Enter.Next)) LookAlikeRun().apply {
        editor.enterResult = result
        type("zorp")
        composer.enter()
        check(saved == listOf("zorp")) { "$result saved $saved" }
    }
    LookAlikeRun().apply {
        swipe("the")
        composer.backspace(false)
        settle()
        composer.backspace(false)
        settle()
        composer.backspace(false)
        settle()
        type("obias")
        space()
        check(editor.text == "tobias ") { "Correction wrote '${editor.text}'" }
        check(corrections == listOf("tobias")) { "Corrections were $corrections" }
        check(saved == listOf("tobias")) { "A word retyped over a swipe saved $saved" }
    }
    println("PASS typed words are saved however they end: space, swipe, tap key, send, next field or a correction")
}
