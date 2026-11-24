package com.titankeys.keyboard

import android.text.TextUtils
import android.view.KeyEvent
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.SurroundingText

/**
 * The connection the keyboard uses for a text box on TitanKeys' own Support page.
 *
 * The keyboard and that page share one process and one main thread. The page's WebView answers the keyboard's questions
 * (the text before the cursor, the selection, caps mode) on that main thread, which the keyboard is holding while it asks, so
 * every question waited for Android's timeout: on 2026-11-24 the board's name box froze for 5 to 16 s at a time, both when it
 * started and on each key (four ANRs). No other app is affected: their boxes answer from their own process.
 *
 * So this keeps its own copy of the text before the cursor, from what Android handed over with the box plus everything the
 * keyboard typed or deleted since, and answers from that at once. Every edit still goes to the page as usual; edits are sent
 * without waiting for an answer, so they never block. A cursor moved by a tap on the page isn't seen, which only affects what
 * auto-capitalisation and corrections read in a short name or message box.
 */
internal class OwnPageConnection(private val page: InputConnection, initialBefore: CharSequence?) :
    InputConnectionWrapper(page, true) {
    private val before = StringBuilder(initialBefore ?: "")
    private var composing = ""

    fun wraps(connection: InputConnection) = connection === page

    private fun text() = before.toString() + composing

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = text().takeLast(n.coerceAtLeast(0))
    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
    override fun getSelectedText(flags: Int): CharSequence? = null
    override fun getCursorCapsMode(reqModes: Int): Int = text().let { TextUtils.getCapsMode(it, it.length, reqModes) }

    override fun getSurroundingText(beforeLength: Int, afterLength: Int, flags: Int): SurroundingText {
        val all = text()
        val shown = all.takeLast(beforeLength.coerceAtLeast(0))
        return SurroundingText(shown, shown.length, shown.length, all.length - shown.length)
    }

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText = ExtractedText().apply {
        val all = text()
        text = all
        startOffset = 0
        selectionStart = all.length
        selectionEnd = all.length
        partialStartOffset = -1
        partialEndOffset = -1
    }

    // Asking the page for cursor updates waits for its answer as well.
    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false
    override fun requestCursorUpdates(cursorUpdateMode: Int, cursorUpdateFilter: Int): Boolean = false

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        composing = ""
        before.append(text ?: "")
        return super.commitText(text, newCursorPosition)
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        composing = text?.toString() ?: ""
        return super.setComposingText(text, newCursorPosition)
    }

    override fun finishComposingText(): Boolean {
        before.append(composing)
        composing = ""
        return super.finishComposingText()
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        val all = text()
        val from = start.coerceIn(0, all.length)
        val to = end.coerceIn(from, all.length)
        before.setLength(0)
        before.append(all, 0, from)
        composing = all.substring(from, to)
        return super.setComposingRegion(start, end)
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        before.append(composing)
        composing = ""
        before.setLength((before.length - beforeLength.coerceAtLeast(0)).coerceAtLeast(0))
        return super.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        before.append(composing)
        composing = ""
        var left = beforeLength.coerceAtLeast(0)
        while (left > 0 && before.isNotEmpty()) {
            val cut = Character.charCount(before.codePointBefore(before.length))
            before.setLength(before.length - cut)
            left--
        }
        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                if (composing.isNotEmpty()) composing = composing.dropLast(1)
                else if (before.isNotEmpty()) before.setLength(before.length - Character.charCount(before.codePointBefore(before.length)))
            }
            KeyEvent.KEYCODE_ENTER -> before.append('\n')
            else -> event.unicodeChar.takeIf { it > 0 }?.let { before.appendCodePoint(it) }
        }
        return super.sendKeyEvent(event)
    }

    companion object {
        /** How much of the box's text the copy starts with: more than any name or message on the board. */
        const val REACH = 1_000
    }
}
