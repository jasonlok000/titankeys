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
    println("PASS swipes respect field capitalisation; punctuation attaches after swipes and preserves manual spaces, with and without cursor reports and truncated context")
}
