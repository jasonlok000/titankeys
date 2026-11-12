package com.atlas.keyboard

import android.icu.text.BreakIterator
import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import java.util.Locale

/** [Editor] over the focused app's input connection. */
class ConnectionEditor(
    private val connection: () -> InputConnection?,
    private val info: () -> EditorInfo?,
    private val later: (Long, () -> Unit) -> Unit,
    private val addedApps: () -> Map<String, EnterKey.SendMethod>,
) : Editor {
    private class StateRequest(val owner: Editor.Identity, var result: ((Editor.State?) -> Unit)?)
    private var stateRequest: StateRequest? = null
    private var awaitingStaleReport = false

    override fun identity(): Editor.Identity? {
        val connection = connection() ?: return null
        val info = info()
        return Editor.Identity(connection, info?.packageName, info?.fieldId ?: 0)
    }

    override fun state(result: (Editor.State?) -> Unit) {
        val owner = identity()
        val connection = connection()
        if (owner == null || connection == null || stateRequest != null || awaitingStaleReport) {
            result(null)
            return
        }
        // sendKeyEvent posts through ViewRoot. This bounded query fences that extra post before
        // requesting a one-shot report. A nonzero filter excludes composing text and character arrays.
        if (connection.getTextBeforeCursor(0, 0) == null) {
            result(null)
            return
        }
        // Some editors do not publish cursor reports. Read text and selection together when possible.
        val surrounding = connection.getSurroundingText(Editor.CONTEXT_LENGTH, 32, 0)
        val snapshot = if (surrounding != null && surrounding.offset >= 0) {
            state(surrounding.text, surrounding.selectionStart, surrounding.selectionEnd, surrounding.offset)
        } else {
            val extracted = connection.getExtractedText(ExtractedTextRequest().apply {
                hintMaxChars = Editor.CONTEXT_LENGTH + 32
            }, 0)
            if (extracted?.text != null && extracted.partialStartOffset == -1)
                state(extracted.text, extracted.selectionStart, extracted.selectionEnd, extracted.startOffset)
            else null
        }
        if (snapshot != null) {
            val current = identity()
            result(if (current != null && current.connection === owner.connection
                && current.packageName == owner.packageName && current.fieldId == owner.fieldId) snapshot else null)
            return
        }
        val request = StateRequest(owner, result)
        stateRequest = request
        if (!connection.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE,
                InputConnection.CURSOR_UPDATE_FILTER_EDITOR_BOUNDS)) {
            stateRequest = null
            request.result = null
            result(null)
            return
        }
        later(STATE_TIMEOUT_MILLIS) {
            if (stateRequest === request) cancelState()
        }
    }

    private fun state(text: CharSequence, selectionStart: Int, selectionEnd: Int, offset: Int): Editor.State? {
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)
        if (offset < 0 || start < 0 || end > text.length) return null
        return Editor.State(text.subSequence(0, start).toString().takeLast(Editor.CONTEXT_LENGTH),
            text.subSequence(end, text.length).toString().take(32), offset + selectionStart, offset + selectionEnd)
    }

    /** Reports have no request ID. A cancelled request must consume its late report before reuse. */
    fun cancelState() {
        val request = stateRequest ?: return
        stateRequest = null
        awaitingStaleReport = true
        val result = request.result
        request.result = null
        result?.invoke(null)
    }

    fun cursorAnchor(info: CursorAnchorInfo) {
        if (awaitingStaleReport) {
            awaitingStaleReport = false
            return
        }
        val request = stateRequest ?: return
        stateRequest = null
        val result = request.result
        request.result = null
        val owner = identity()
        if (owner == null || owner.connection !== request.owner.connection
            || owner.packageName != request.owner.packageName || owner.fieldId != request.owner.fieldId
            || info.selectionStart < 0 || info.selectionEnd < 0) {
            result?.invoke(null)
            return
        }
        val before = before()
        val after = after()
        val current = identity()
        result?.invoke(if (before == null || after == null || current == null
            || current.connection !== owner.connection || current.packageName != owner.packageName || current.fieldId != owner.fieldId) null
            else Editor.State(before, after, info.selectionStart, info.selectionEnd))
    }

    override fun before() = connection()?.getTextBeforeCursor(Editor.CONTEXT_LENGTH, 0)?.toString()

    override fun after() = connection()?.getTextAfterCursor(32, 0)?.toString()

    override fun replace(count: Int, text: String, after: Int): Boolean {
        val connection = connection() ?: return false
        connection.beginBatchEdit()
        return try {
            if ((count > 0 || after > 0) && !connection.deleteSurroundingText(count, after)) false
            else connection.commitText(text, 1)
        } finally {
            connection.endBatchEdit()
        }
    }

    override fun delete(word: Boolean, selectionStart: Int, selectionEnd: Int): Int? {
        val connection = connection() ?: return null
        connection.finishComposingText()
        val meta = if (word) KeyEvent.META_CTRL_ON else 0
        if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
            return if (connection.commitText("", 1)) 0 else null
        }
        val before = connection.getTextBeforeCursor(256, 0)?.toString()
        if (selectionStart < 0 || selectionEnd < 0 || before == null) {
            key(connection, KeyEvent.KEYCODE_DEL, meta)
            return null
        }
        if (before.isEmpty()) return 0
        var start = before.length
        if (word) {
            start -= wordDeleteLength(before, connection.getTextAfterCursor(1, 0)?.toString())
        } else {
            val characters = BreakIterator.getCharacterInstance(Locale.UK)
            characters.setText(before)
            start = characters.preceding(before.length).coerceAtLeast(0)
        }
        val removed = before.length - start
        if (connection.deleteSurroundingText(removed, 0)) return removed
        // A false result can still have partly applied deletion. Never replay it as a key.
        return null
    }

    override fun enter(press: EnterKey.Press): Editor.Enter {
        val connection = connection() ?: return Editor.Enter.Rejected
        val info = info()
        val options = info?.imeOptions ?: 0
        val action = when (press) {
            EnterKey.Press.NewLine, EnterKey.Press.ShiftNewLine -> return Editor.Enter.Newline
            EnterKey.Press.Send, EnterKey.Press.ShiftSend -> {
                if (EnterKey.sendMethod(info?.packageName, addedApps()) == EnterKey.SendMethod.PlainEnter)
                    return if (key(connection, KeyEvent.KEYCODE_ENTER, 0)) Editor.Enter.Action else Editor.Enter.Rejected
                EnterKey.sendAction(options, info?.actionId ?: 0)
            }
            EnterKey.Press.FieldDecides -> {
                val own = options and EditorInfo.IME_MASK_ACTION
                if (info == null || options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
                    || own == EditorInfo.IME_ACTION_UNSPECIFIED || own == EditorInfo.IME_ACTION_NONE) return Editor.Enter.Newline
                own
            }
        }
        if (!connection.performEditorAction(action)) return Editor.Enter.Rejected
        return when (action) {
            EditorInfo.IME_ACTION_NEXT -> Editor.Enter.Next
            EditorInfo.IME_ACTION_PREVIOUS -> Editor.Enter.Previous
            else -> Editor.Enter.Action
        }
    }

    private fun key(connection: InputConnection, code: Int, meta: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val down = connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        val up = connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
        return down && up
    }

    companion object {
        private const val STATE_TIMEOUT_MILLIS = 500L
    }
}
