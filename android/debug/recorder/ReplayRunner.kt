package com.atlas.keyboard

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import com.atlas.engine.EnglishSpelling
import com.atlas.engine.Swipe
import com.atlas.engine.SwipeTyping
import com.atlas.engine.TypingSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

/**
 * Replays a recorder session through the real touch handling, text logic and swipe engine, without a
 * keyboard window. Run with `am instrument`. Keys that change the behaviour let a candidate fix be
 * measured before any of it goes into the keyboard itself.
 */
class ReplayRunner : Instrumentation() {
    private class Options(arguments: Bundle) {
        val input = arguments.getString("in") ?: "in.jsonl"
        val output = arguments.getString("out") ?: "out.jsonl"
        val lift = arguments.getString("lift")?.toInt()
        /** The window after a loss on the top row, where nearly all tracking loss happens. */
        val liftTop = arguments.getString("lifttop")?.toInt() ?: lift
        /** "0" scores the keyboard without joining short touches to the swipes beside them. */
        val pieces = arguments.getString("pieces") != "0"
        /** "1" times word suggestions instead of replaying a session. */
        val timing = arguments.getString("timing") == "1"
        val lateMillis = arguments.getString("late")?.toLong() ?: 0L
        val lateSpeed = arguments.getString("latespeed")?.toFloat() ?: 4f
        val lateFrom = arguments.getString("latefrom")?.toFloat() ?: .5f
        val keys = arguments.getString("keys")
        /** Joins pieces of one swipe before recognition, with [fill] adding points across each gap. */
        val merge = arguments.getString("merge") == "1"
        val fill = arguments.getString("fill") == "1"
    }

    private lateinit var options: Options

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        options = Options(arguments ?: Bundle())
        start()
    }

    override fun onStart() {
        super.onStart()
        val result = Bundle()
        try {
            replay(result)
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString(REPORT_KEY_STREAMRESULT, Log.getStackTraceString(error))
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun replay(result: Bundle) {
        val directory = targetContext.getExternalFilesDir("replay")!!
        // The engine reads the key positions as it loads, so they must change first.
        options.keys?.let { applyKeys(File(directory, it)) }
        // Recordings are scored with the spelling they were made in, whatever the app is set to now.
        val spelling = TypingSettings.spelling(targetContext)
        TypingSettings.spelling(targetContext, EnglishSpelling.Variant.BRITISH)
        try {
            replayAll(directory, result)
        } finally {
            TypingSettings.spelling(targetContext, spelling)
        }
    }

    private fun replayAll(directory: File, result: Bundle) {
        val typing = SwipeTyping.get(targetContext)
        val ready = CountDownLatch(1)
        var failure: String? = null
        runOnMainSync {
            typing.whenReady { _, _, error ->
                failure = error
                ready.countDown()
            }
        }
        check(ready.await(60, TimeUnit.SECONDS)) { "The engine did not load" }
        failure?.let { throw IllegalStateException(it) }
        if (options.timing) {
            timeSuggestions(typing, File(directory, options.output))
            result.putString(REPORT_KEY_STREAMRESULT, "Timed suggestions\n")
            return
        }
        val words = EngineWords(typing)
        var count = 0
        File(directory, options.output).bufferedWriter().use { output ->
            File(directory, options.input).forEachLine { line ->
                val entry = JSONObject(line)
                if (entry.has("events")) {
                    output.write(Attempt(entry, words).run().toString())
                    count++
                } else output.write(line)
                output.newLine()
            }
        }
        Recording.sink = null
        result.putString(REPORT_KEY_STREAMRESULT, "Replayed $count attempts\n")
    }

    /** Times suggestions for a new context, for more letters of the same word, and for a context made ready first. */
    private fun timeSuggestions(typing: SwipeTyping, file: File) {
        fun complete(before: String): Long {
            val done = CountDownLatch(1)
            val started = System.nanoTime()
            typing.complete(before, AtomicBoolean(false), false) { _, _, _ -> done.countDown() }
            check(done.await(5, TimeUnit.SECONDS)) { "Suggestions stalled" }
            return (System.nanoTime() - started) / 1_000_000
        }
        file.bufferedWriter().use { output ->
            for (phrase in TIMED_PHRASES) {
                val fresh = complete(phrase)
                val same = complete(phrase + "t")
                typing.prepare(phrase + "word ", AtomicBoolean(false))
                Thread.sleep(400)
                val ready = complete(phrase + "word ")
                output.write(JSONObject().put("fresh", fresh).put("same", same).put("ready", ready).toString())
                output.newLine()
            }
        }
    }

    private fun applyKeys(file: File) = file.forEachLine { line ->
        val parts = line.trim().split(" ")
        if (parts.size != 3) return@forEachLine
        val index = parts[0][0] - 'a'
        Layout.KEY_X[index] = parts[1].toFloat()
        Layout.KEY_Y[index] = parts[2].toFloat()
    }

    /** A field with no app behind it. It is always in sync, as the live field was between words. */
    private class ReplayEditor : Editor {
        private val owner = Any()
        var text = ""

        override fun identity() = Editor.Identity(owner, "replay", 1)
        override fun state(result: (Editor.State?) -> Unit) = result(Editor.State(text.takeLast(Editor.CONTEXT_LENGTH), "", text.length, text.length))
        override fun before() = text.takeLast(Editor.CONTEXT_LENGTH)
        override fun after() = ""
        override fun enter(press: EnterKey.Press) = Editor.Enter.Newline

        override fun replace(count: Int, text: String, after: Int): Boolean {
            this.text = this.text.dropLast(count) + text
            return true
        }

        override fun delete(word: Boolean, selectionStart: Int, selectionEnd: Int): Int {
            val removed = minOf(1, text.length)
            text = text.dropLast(removed)
            return removed
        }
    }

    /** One prompt: follows the same order of calls as the keyboard does for live input. */
    private inner class Attempt(private val entry: JSONObject, private val words: Words) : Recording.Sink {
        private val main = Handler(Looper.getMainLooper())
        private val typed = entry.optString("pace") == "typed"
        private val editor = ReplayEditor()
        private val contact = Contact()
        private val cursor = CursorMode()
        private val cursorGesture = CursorGesture()
        private val cursorControl = TypingSettings.cursorControl(targetContext)
        private val tapKeys = TypingSettings.tapKeys(targetContext)
        private val defaultLift = options.lift ?: TypingSettings.liftOffGraceMillis(targetContext)
        private val settlements = ArrayDeque<Contact.Settlement>()
        private val pieces = SwipePieces({ composer.liftOffMillis.toLong() }, { composer.canContinueSwipe }, ::isTapKey)
        private val outcomes = JSONArray()
        private val recognitions = JSONArray()
        private lateinit var composer: Composer
        private var confirmAt: Long? = null
        private var endSlippedAt: Long? = null
        private var lastSwipeEnd: Sample? = null
        private var merged: MutableList<Sample>? = null
        private var mergedStart = 0L
        private var mergedEnd = 0L
        /** Taps on a tap key that may be part of a double tap, not typed until they can't be. */
        private val heldTaps = ArrayDeque<Pair<Contact.Settlement, Contact.Outcome?>>()
        private var selects = 0
        private var touches = 0
        private var top: List<String> = emptyList()
        private var joined = false

        fun run(): JSONObject {
            Recording.sink = this
            runOnMainSync {
                composer = Composer(editor, words, { delay, action -> main.postDelayed(action, delay) }, {}, { false }, {})
                composer.liftOffMillis = if (options.merge) 0 else defaultLift
                composer.start(FIELD, 0, 0)
            }
            val events = entry.getJSONArray("events")
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i)
                when (event.getString("k")) {
                    "m" -> runOnMainSync { motion(event) }
                    "key" -> runOnMainSync { key(event) }
                }
                idle()
            }
            runOnMainSync {
                advance(Long.MAX_VALUE)
                flushMerged()
            }
            idle()
            return JSONObject().put("index", entry.getInt("index")).put("prompt", entry.getString("prompt"))
                .put("pace", entry.optString("pace")).put("touches", touches).put("joined", joined)
                .put("top", JSONArray(top)).put("text", editor.text).put("recs", recognitions).put("outs", outcomes)
                .put("selects", selects)
        }

        private fun idle() {
            val deadline = System.currentTimeMillis() + 15_000
            while (true) {
                var busy = false
                runOnMainSync { busy = composer.hasPendingInput }
                if (!busy) return
                check(System.currentTimeMillis() < deadline) { "Recognition stalled on ${entry.getString("prompt")}" }
                Thread.sleep(1)
            }
        }

        private fun samples(event: JSONObject): List<Sample> {
            val array = event.getJSONArray("s")
            return List(array.length()) {
                val point = array.getJSONArray(it)
                Sample(point.optDouble(0).toFloat(), point.optDouble(1).toFloat(), point.getLong(2))
            }
        }

        private fun motion(event: JSONObject) {
            val time = event.getLong("t")
            advance(time)
            val samples = samples(event)
            when (event.getInt("a")) {
                MotionEvent.ACTION_DOWN -> {
                    touches++
                    val first = samples.firstOrNull() ?: Sample(Float.NaN, Float.NaN, time)
                    contact.down(first)
                    if (cursor.resume(first)) {
                        endSlippedAt = null
                        contact.cancelActive()
                        cursor.move(samples.drop(1))
                        return
                    }
                    contact.move(samples.drop(1))
                    stopCursor()
                    confirmAt = null
                    cursorGesture.down(first, cursorControl && contact.travel != null)
                    cursorGesture.move(samples.drop(1))?.let { startCursor(it, samples.drop(1)) }
                }
                MotionEvent.ACTION_MOVE -> {
                    cursor.move(samples)
                    if (cursor.active) return
                    contact.move(samples)
                    cursorGesture.move(samples)?.let { startCursor(it, samples) }
                }
                MotionEvent.ACTION_UP -> {
                    val doubleTapped = cursorGesture.up(samples, time)
                    if (cursor.active) {
                        cursor.lift()
                        if (defaultLift > 0) endSlippedAt = time + defaultLift else stopCursor()
                        return
                    }
                    stopCursor()
                    if (doubleTapped) confirmAt = time + CursorGesture.CONFIRM_MILLIS
                    contact.up(samples, time)?.let { settlements.addLast(it) }
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    contact.cancelActive()
                    endCursor(time)
                }
            }
            typeHeldTaps(time)
        }

        private fun startCursor(drag: CursorGesture.Drag, samples: List<Sample>) {
            cursor.start(drag.landed, drag.words)
            cursor.move(samples)
            contact.cancelActive()
            heldTaps.clear()
        }

        private fun key(event: JSONObject) {
            val time = event.getLong("t")
            advance(time)
            if (event.getBoolean("g")) return
            val down = event.getBoolean("d")
            contact.key(event.getInt("c"), down, time)
            if (!down) return
            endCursor(time)
            flushMerged()
            if (!typed && event.getInt("c") == KeyEvent.KEYCODE_DEL) composer.backspace(false)
        }

        private fun advance(time: Long) {
            settle(time)
            confirmAt?.let {
                if (time >= it) {
                    confirmAt = null
                    if (cursorGesture.confirm()) {
                        selects++
                        heldTaps.clear()
                    }
                }
            }
            endSlippedAt?.let { if (time >= it) stopCursor() }
            typeHeldTaps(time)
        }

        private fun stopCursor() {
            endSlippedAt = null
            cursor.end()
        }

        private fun endCursor(time: Long) {
            stopCursor()
            confirmAt = null
            cursorGesture.cancel(time)
            typeHeldTaps(time)
        }

        private fun settle(time: Long) {
            while (settlements.isNotEmpty() && settlements.first().deadline <= time) {
                val finished = settlements.removeFirst()
                val outcome = contact.settle(finished, finished.deadline)
                if (finished.cancelled) continue
                if (outcome is Contact.Outcome.Tap && isTapKey(outcome.key) && cursorGesture.deciding(finished.deadline)) {
                    heldTaps.addLast(finished to outcome)
                } else {
                    typeHeldTaps(finished.deadline)
                    deliver(finished, outcome)
                }
            }
        }

        private fun typeHeldTaps(time: Long) {
            if (cursorGesture.deciding(time)) return
            while (heldTaps.isNotEmpty()) heldTaps.removeFirst().let { (finished, outcome) -> deliver(finished, outcome) }
        }

        private fun isTapKey(key: String) = if (key == Layout.SPACE) ' ' in tapKeys else Layout.isLetter(key) && key[0] in tapKeys

        private fun window(previous: Sample?) = if (previous != null && previous.y < .25f) options.liftTop ?: defaultLift else defaultLift

        private fun deliver(finished: Contact.Settlement, outcome: Contact.Outcome?) {
            when (val result = if (options.pieces) pieces.settle(finished, outcome) else outcome) {
                is Contact.Outcome.Swipe -> if (result.deletesWord) {
                    outcomes.put(JSONObject().put("type", "delete").put("end", result.endedAt))
                    composer.backspace(true)
                } else swipe(result.points, result.startedAt, result.endedAt)
                is Contact.Outcome.Tap -> tap(result)
                null -> Unit
            }
        }

        private fun swipe(points: List<Sample>, startedAt: Long, endedAt: Long) {
            val path = extended(points, startedAt, endedAt)
            outcomes.put(JSONObject().put("type", "swipe").put("n", path.size).put("start", startedAt).put("end", endedAt))
            if (options.merge) {
                merge(path, startedAt, endedAt)
                return
            }
            composer.liftOffMillis = window(lastSwipeEnd)
            lastSwipeEnd = path.last()
            if (!typed) composer.swipe(letterSpace(path), startedAt, endedAt, 0)
        }

        private fun merge(path: List<Sample>, startedAt: Long, endedAt: Long) {
            val open = merged
            val last = open?.last()
            if (open != null && startedAt - mergedEnd in 0..window(last).toLong()) {
                if (options.fill) {
                    val first = path.first()
                    val steps = ((first.time - last!!.time) / 10).toInt()
                    for (step in 1 until steps) {
                        val t = step.toFloat() / steps
                        open += Sample(last.x + (first.x - last.x) * t, last.y + (first.y - last.y) * t, last.time + step * 10L)
                    }
                }
                open += path
            } else {
                flushMerged()
                merged = path.toMutableList()
                mergedStart = startedAt
            }
            mergedEnd = endedAt
            lastSwipeEnd = path.last()
        }

        private fun flushMerged() {
            val path = merged ?: return
            merged = null
            if (!typed) composer.swipe(letterSpace(path), mergedStart, mergedEnd, 0)
        }

        /** Swipes that start in the top right register late, so run the first movement back to where it began. */
        private fun extended(points: List<Sample>, startedAt: Long, endedAt: Long): List<Sample> {
            if (options.lateMillis == 0L || points.size < 3) return points
            val first = points.first()
            if (first.y >= .25f || first.x < options.lateFrom) return points
            val early = points.takeWhile { it.time - first.time <= 40 }
            if (early.size < 2 || early.last().time == first.time) return points
            val seconds = (early.last().time - first.time) / 1000f
            val dx = early.last().x - first.x
            val dy = early.last().y - first.y
            if (hypot(dx * 10, dy * 4) / seconds < options.lateSpeed) return points
            val scale = options.lateMillis / 1000f / seconds
            val start = Sample((first.x - dx * scale).coerceIn(0f, 1f), (first.y - dy * scale).coerceIn(0f, 1f), first.time - options.lateMillis)
            return listOf(start) + points
        }

        private fun tap(outcome: Contact.Outcome.Tap) {
            outcomes.put(JSONObject().put("type", "tap").put("key", outcome.key).put("end", outcome.endedAt))
            if (typed) return
            if (!isTapKey(outcome.key)) return
            if (outcome.key == Layout.SPACE) composer.space(repeated = false, time = outcome.endedAt)
            else composer.type(if (outcome.key == "I" && TypingSettings.autoCapitalise(targetContext)) "I" else outcome.key.lowercase(), letterTap = true)
        }

        private fun letterSpace(points: List<Sample>): Swipe {
            val start = points.first().time
            return Swipe(FloatArray(points.size) { points[it].x }, FloatArray(points.size) { points[it].y * 4 / 3 },
                FloatArray(points.size) { (points[it].time - start).toFloat() })
        }

        override fun motion(event: MotionEvent, samples: List<Sample>) = Unit
        override fun key(event: KeyEvent, gesture: Boolean) = Unit
        override fun outcome(outcome: Contact.Outcome) = Unit

        override fun recognised(words: List<String>, resumed: Boolean) {
            top = words
            joined = joined || resumed
            recognitions.put(JSONObject().put("words", JSONArray(words)).put("resumed", resumed))
        }
    }

    private companion object {
        val TIMED_PHRASES = listOf("what do you ", "thank you so ", "see you at the ", "let me know when ", "do you want to ",
            "we should talk about ", "can you send me ", "that sounds like ", "have a great ", "are you free ", "my phone is ",
            "please call me ", "it was really ", "sorry for the ", "what time does ", "do you have any ", "we are going ",
            "i will be there ", "hope you have ", "how are you ", "the weather is ", "i think we ", "they went to ", "she said that ")
        val FIELD = Field.of(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_GO or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI)
    }
}
