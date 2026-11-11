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
 *
 * The full log is only in TitanKeys Debug ([RawLogGate]). A [Session] also feeds a [BugRecording] while one runs, in every
 * build, with the same lines but without the text around the cursor.
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

    /** Whether this build has the full typing log at all: only TitanKeys Debug does. */
    fun available(context: Context) = RawLogGate.allows(context.packageName)

    /** The full log records now: this build has it, and its switch is on. */
    fun on(context: Context) = available(context) && TypingSettings.rawLog(context)

    /** Turning it off stops recording at once. What is already saved stays until Delete all. Only debug builds turn it on. */
    @Synchronized
    fun enabled(context: Context, enabled: Boolean) {
        check(!enabled || available(context)) { "The typing log is only in TitanKeys Debug" }
        TypingSettings.rawLog(context, enabled)
        if (!enabled) {
            writer?.close()
            writer = null
        }
    }

    fun size(context: Context) = if (available(context)) store(context).size() else 0L

    fun export(context: Context, out: OutputStream) {
        check(available(context)) { "The typing log is only in TitanKeys Debug" }
        store(context).export(out)
    }

    /**
     * A public release has no typing log. A phone that had one from TitanKeys 2.0 keeps nothing it can't see or export:
     * the switch goes off and the old files go.
     */
    fun removeLeftovers(context: Context) {
        if (available(context)) return
        if (TypingSettings.rawLog(context)) TypingSettings.rawLog(context, false)
        val directory = File(context.applicationContext.filesDir, DIRECTORY)
        if (directory.exists()) deleteAll(context)
    }

    fun deleteAll(context: Context) {
        val store = store(context)
        val writer = synchronized(this) { writer }
        synchronized(store) {
            writer?.discard()
            store.deleteAll()
        }
    }

    /** Starts recording the field that is starting, or returns null if neither the log nor a bug recording takes it. */
    @Synchronized
    fun start(context: Context, info: EditorInfo, restarting: Boolean): Session? {
        val debug = if (on(context)) debugField(context, info, restarting) else null
        val bug = BugRecording.fieldStart(context, info, restarting)
        return if (debug == null && bug == null) null else Session(debug, bug)
    }

    private fun debugField(context: Context, info: EditorInfo, restarting: Boolean): RawLogWriter? {
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
        return writer
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

    internal fun header(context: Context) = RawLogLines.header(now(), System.currentTimeMillis(),
        context.packageManager.getPackageInfo(context.packageName, 0).versionName, Build.MODEL, Build.VERSION.RELEASE,
        TypingSettings.liftOffGraceMillis(context), settings(context),
        Contact.tuning + deleteSwipeTuning + swipePiecesTuning + CursorMode.tuning + SwipeRecognizer.tuning())

    private fun settings(context: Context): Map<String, Any> = linkedMapOf(
        "liftOff" to TypingSettings.liftOffGrace(context),
        "liftOffMillis" to TypingSettings.liftOffDurationMillis(context),
        "tapKeys" to TypingSettings.tapKeys(context),
        "holdSymbols" to TypingSettings.holdSymbols(context),
        "holdSymbolMillis" to TypingSettings.holdSymbolMillis(context),
        "fnAsCtrl" to TypingSettings.fnAsCtrl(context),
        "doublePressLocks" to TypingSettings.doublePressLocks(context),
        "autoCapitalise" to TypingSettings.autoCapitalise(context),
        "autoPunctuation" to TypingSettings.autoPunctuation(context),
        "sentenceSpaces" to TypingSettings.sentenceSpaces(context),
        "commaSpace" to TypingSettings.commaSpace(context),
        "autocorrect" to TypingSettings.autocorrect(context),
        "suggestionBar" to TypingSettings.suggestionBar(context),
        "launcherHidesBar" to TypingSettings.launcherHidesBar(context),
        "cursorControl" to TypingSettings.cursorControl(context),
        "swipeTyping" to TypingSettings.swipeTyping(context),
        "touchpadOnlyInFields" to TypingSettings.touchpadOnlyInFields(context),
        "orangeAccents" to TypingSettings.orangeAccents(context),
        "enterKey" to TypingSettings.enterKey(context),
        "enterApps" to TypingSettings.enterApps(context).sorted().joinToString(","),
        "spelling" to if (TypingSettings.spelling(context) == EnglishSpelling.Variant.AMERICAN) "american" else "british",
        "learning" to SwipeTyping.get(context).learningEnabled(),
    )

    private fun now() = SystemClock.uptimeMillis()

    /**
     * Records one field, to the full log ([debug], debug builds only) and to a running bug recording ([bug]), either or
     * both. Every call comes from the keyboard's main thread, so the lines stay in order.
     */
    class Session internal constructor(private val debug: RawLogWriter?, private val bug: RawLogWriter?) : EditLog {
        private inline fun write(line: () -> String) {
            val open = listOfNotNull(debug, bug).filter { !it.closed }
            if (open.isEmpty()) return
            val text = line()
            for (writer in open) writer.add(text)
        }

        /** A line with the text before the cursor: only the full log keeps that text. A bug recording never does. */
        private inline fun writeWithText(line: (keepText: Boolean) -> String) {
            if (debug != null && !debug.closed) debug.add(line(true))
            if (bug != null && !bug.closed) bug.add(line(false))
        }

        /** Leaves out one line of input that may not belong to this field. */
        fun skip() {
            debug?.skip()
            bug?.skip()
        }

        fun motion(event: MotionEvent, samples: List<Sample>) = write {
            val history = event.historySize
            RawLogLines.motion(event.actionMasked, event.eventTime, event.rawX, event.rawY, history + 1,
                { if (it < history) event.getHistoricalX(it) else event.x },
                { if (it < history) event.getHistoricalY(it) else event.y },
                { if (it < history) event.getHistoricalEventTime(it) else event.eventTime },
                samples.size, { samples[it].x }, { samples[it].y }, { samples[it].time },
                { if (it < history) event.getHistoricalTouchMajor(it) else event.touchMajor })
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
            RawLogLines.recognised(recognition.words, resumed, scores, now(), recognition.prediction?.ranked() == true)
        }

        override fun edited(cause: EditCause, deleted: Int, inserted: String, deletedAfter: Int, accepted: Boolean) = write {
            RawLogLines.edit(cause, deleted, inserted, deletedAfter, accepted, now())
        }

        override fun picked(index: Int, word: String, offered: List<String>) = write {
            RawLogLines.picked(index, word, offered, now())
        }

        fun selection(start: Int, end: Int, composingStart: Int, composingEnd: Int, before: CharSequence?) = writeWithText { keepText ->
            RawLogLines.selection(start, end, composingStart, composingEnd, if (keepText) before?.toString() else null, now())
        }

        /** Any app can send a mark, so only the debug log takes it, never a user's bug recording. */
        fun mark(kind: String?, label: String?) {
            if (debug != null && !debug.closed) debug.add(RawLogLines.mark(kind?.take(MAX_MARK_LENGTH), label?.take(MAX_MARK_LENGTH), now()))
        }

        fun finish() {
            write { RawLogLines.fieldEnd(now()) }
            debug?.flush()
            bug?.flush()
        }
    }
}
