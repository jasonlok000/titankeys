package com.atlas.keyboard

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import com.atlas.engine.EnglishSpelling
import com.atlas.engine.SwipeRecognizer
import com.atlas.engine.SwipeTyping
import com.atlas.engine.TypingSettings
import java.io.File
import java.io.OutputStream

/**
 * An optional log of raw input in any app, for tuning swipe typing on everyday text. The phone only records: a tool on
 * a computer works out what the input meant, so better analysis can be rerun on old logs. The keyboard asks for a
 * [Session] as each field starts. With the setting off, or in a field that must not be recorded, there is no session,
 * so each hook is a null check. It is separate from [Recording], so the recorder and replay can run beside it.
 * The format is described in android/debug/recorder/README.md.
 */
object RawLog {
    const val MARK_ACTION = "com.titankeys.rawlog.MARK"
    /** How much of the text before the cursor each selection line keeps. */
    const val BEFORE_LENGTH = 256
    private const val MAX_MARK_LENGTH = 256
    private const val DIRECTORY = "rawlog"

    private var store: RawLogStore? = null
    private var writer: RawLogWriter? = null
    private var lastSettings: Map<String, Any>? = null
    /** A gap marker already covers the fields skipped since the last recorded one. */
    private var skipping = false

    /** Turning it off stops recording at once. What is already saved stays until Delete all. */
    @Synchronized
    fun enabled(context: Context, enabled: Boolean) {
        TypingSettings.rawLog(context, enabled)
        if (!enabled) {
            writer?.close()
            writer = null
        }
    }

    fun size(context: Context) = store(context).size()

    fun export(context: Context, out: OutputStream) = store(context).export(out)

    fun deleteAll(context: Context) {
        val store = store(context)
        val writer = synchronized(this) { writer }
        synchronized(store) {
            writer?.discard()
            store.deleteAll()
        }
    }

    /** Starts recording the field that is starting, or returns null if it must not be recorded. */
    @Synchronized
    fun start(context: Context, info: EditorInfo, restarting: Boolean): Session? {
        if (!TypingSettings.rawLog(context)) return null
        val writer = writer(context)
        if (!Field.logged(info.inputType, info.imeOptions, info.privateImeOptions)) {
            if (!skipping) writer.add(RawLogLines.gap("field", 0, now()))
            skipping = true
            return null
        }
        skipping = false
        val settings = settings(context)
        if (settings != lastSettings) {
            // A new file already starts with a header, so only a change during a file needs another.
            if (lastSettings != null) writer.add(header(context))
            lastSettings = settings
        }
        writer.add(RawLogLines.fieldStart(info.inputType, info.imeOptions, !Field.of(info.inputType, info.imeOptions).learning,
            restarting, info.initialSelStart, info.initialSelEnd, info.getInitialTextBeforeCursor(BEFORE_LENGTH, 0)?.toString(), now()))
        return Session(writer)
    }

    private fun writer(context: Context): RawLogWriter = writer ?: store(context).let { store ->
        // Nothing was recorded before this point, so the new file can't be linked to the one before it.
        store.rotate()
        skipping = false
        RawLogWriter(store) { now() }.also {
            writer = it
            it.add(RawLogLines.gap("start", 0, now()))
        }
    }

    @Synchronized
    private fun store(context: Context): RawLogStore = store ?: context.applicationContext.let { app ->
        RawLogStore(File(app.filesDir, DIRECTORY)) { header(app) }.also { store = it }
    }

    private fun header(context: Context) = RawLogLines.header(now(), System.currentTimeMillis(),
        context.packageManager.getPackageInfo(context.packageName, 0).versionName, Build.MODEL, Build.VERSION.RELEASE,
        TypingSettings.liftOffGraceMillis(context), settings(context),
        Contact.tuning + deleteSwipeTuning + swipePiecesTuning + CursorMode.tuning + SwipeRecognizer.tuning())

    private fun settings(context: Context): Map<String, Any> = linkedMapOf(
        "liftOff" to TypingSettings.liftOffGrace(context),
        "liftOffMillis" to TypingSettings.liftOffDurationMillis(context),
        "tapKeys" to TypingSettings.tapKeys(context),
        "holdSymbols" to TypingSettings.holdSymbols(context),
        "holdSymbolMillis" to TypingSettings.holdSymbolMillis(context),
        "autoCapitalise" to TypingSettings.autoCapitalise(context),
        "autocorrect" to TypingSettings.autocorrect(context),
        "suggestionBar" to TypingSettings.suggestionBar(context),
        "cursorControl" to TypingSettings.cursorControl(context),
        "enterKey" to TypingSettings.enterKey(context),
        "spelling" to if (TypingSettings.spelling(context) == EnglishSpelling.Variant.AMERICAN) "american" else "british",
        "learning" to SwipeTyping.get(context).learningEnabled(),
    )

    private fun now() = SystemClock.uptimeMillis()

    /** Records one field. Every call comes from the keyboard's main thread, so the lines stay in order. */
    class Session internal constructor(private val writer: RawLogWriter) : EditLog {
        private inline fun write(line: () -> String) {
            if (!writer.closed) writer.add(line())
        }

        /** Leaves out one line of input that may not belong to this field. */
        fun skip() = writer.skip()

        fun motion(event: MotionEvent, samples: List<Sample>) = write {
            val history = event.historySize
            RawLogLines.motion(event.actionMasked, event.eventTime, event.rawX, event.rawY, history + 1,
                { if (it < history) event.getHistoricalX(it) else event.x },
                { if (it < history) event.getHistoricalY(it) else event.y },
                { if (it < history) event.getHistoricalEventTime(it) else event.eventTime },
                samples.size, { samples[it].x }, { samples[it].y }, { samples[it].time })
        }

        fun key(event: KeyEvent, gesture: Boolean) = write {
            RawLogLines.key(event.keyCode, event.action == KeyEvent.ACTION_DOWN, event.eventTime, gesture, event.metaState, event.repeatCount)
        }

        fun outcome(outcome: Contact.Outcome) = write {
            when (outcome) {
                is Contact.Outcome.Swipe -> RawLogLines.swipe(outcome.points.size, outcome.startedAt, outcome.endedAt, outcome.deletesWord, now())
                is Contact.Outcome.Tap -> RawLogLines.tap(outcome.key, outcome.endedAt, now())
            }
        }

        override fun recognised(recognition: Words.Recognition, resumed: Boolean) = write {
            val scores = recognition.prediction?.decoded()?.map { RawLogScore(it.word, it.score, it.ctcScore, it.lmScore) }
            RawLogLines.recognised(recognition.words, resumed, scores, now())
        }

        override fun edited(cause: EditCause, deleted: Int, inserted: String, deletedAfter: Int, accepted: Boolean) = write {
            RawLogLines.edit(cause, deleted, inserted, deletedAfter, accepted, now())
        }

        override fun picked(index: Int, word: String, offered: List<String>) = write {
            RawLogLines.picked(index, word, offered, now())
        }

        fun selection(start: Int, end: Int, composingStart: Int, composingEnd: Int, before: CharSequence?) = write {
            RawLogLines.selection(start, end, composingStart, composingEnd, before?.toString(), now())
        }

        fun mark(kind: String?, label: String?) = write {
            RawLogLines.mark(kind?.take(MAX_MARK_LENGTH), label?.take(MAX_MARK_LENGTH), now())
        }

        fun finish() {
            write { RawLogLines.fieldEnd(now()) }
            writer.flush()
        }
    }
}
