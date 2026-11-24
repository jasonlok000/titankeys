package com.titankeys.keyboard

import android.os.SystemClock
import android.util.Log
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.SurroundingText

/**
 * The connection the keyboard uses for every other app's text box. It stops asking an app that has just been slow to answer.
 *
 * A question to the app (the text before or after the cursor, the selection) waits for its answer on the keyboard's main
 * thread, up to Android's 2-second limit. While an app is busy, several questions on one key added up past 5 seconds and
 * Android showed "TitanKeys Keyboard isn't responding" (2026-11-24, typing in the Claude app while it showed a long reply:
 * Composer.edit read the text after the cursor). So when one answer takes longer than [SLOW_MILLIS], the keyboard stops
 * asking that app for [PAUSE_MILLIS] and goes on without the answer, as it does for any app that gives none: corrections may
 * see less around a word, but every key still goes in. Edits are never held back; they don't wait for an answer.
 */
internal class GuardedConnection(private val app: InputConnection, private val pkg: String?) : InputConnectionWrapper(app, true) {
    fun wraps(connection: InputConnection) = connection === app

    private inline fun <T> ask(what: String, question: () -> T?): T? {
        if (paused()) return null
        val started = SystemClock.uptimeMillis()
        val answer = question()
        val took = SystemClock.uptimeMillis() - started
        if (took > SLOW_MILLIS) {
            slowApp = pkg
            slowUntil = SystemClock.uptimeMillis() + PAUSE_MILLIS
            Log.i("TitanKeys", "$pkg took $took ms to answer $what; not asking it for $PAUSE_MILLIS ms")
        }
        return answer
    }

    private fun paused() = slowApp == pkg && SystemClock.uptimeMillis() < slowUntil

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = ask("text before") { super.getTextBeforeCursor(n, flags) }
    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = ask("text after") { super.getTextAfterCursor(n, flags) }
    override fun getSelectedText(flags: Int): CharSequence? = ask("selection") { super.getSelectedText(flags) }
    override fun getSurroundingText(beforeLength: Int, afterLength: Int, flags: Int): SurroundingText? =
        ask("surrounding text") { super.getSurroundingText(beforeLength, afterLength, flags) }
    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? =
        ask("extracted text") { super.getExtractedText(request, flags) }
    override fun getCursorCapsMode(reqModes: Int): Int = ask("caps mode") { super.getCursorCapsMode(reqModes) } ?: 0

    companion object {
        /** An answer slower than this means the app is busy. Answers normally take a few milliseconds. */
        const val SLOW_MILLIS = 400L
        /** How long the keyboard then goes without asking that app. */
        const val PAUSE_MILLIS = 3_000L

        /** The app that was last slow, and until when it isn't asked: kept across its boxes, as a busy app stays busy. */
        @Volatile private var slowApp: String? = null
        @Volatile private var slowUntil = 0L
    }
}
