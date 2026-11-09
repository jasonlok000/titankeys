package com.atlas.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import com.atlas.engine.Behaviour
import com.atlas.engine.Swipe
import com.atlas.engine.TypingSettings
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Replays recorded touches through the keyboard's real touch handling and text logic, either as the original
 * Atlas behaviour or as TitanKeys. It types into an empty field of its own. Call it away from the main thread.
 */
internal class BenchmarkReplay(private val context: Context, private val words: Words) {
    private val main = Handler(Looper.getMainLooper())

    /** True during a replay, so the recorder can ignore what the replay's composer reports. */
    @Volatile
    var running = false
        private set

    /** The text that this version would have typed for these touches. */
    fun run(events: JSONArray, atlas: Boolean): String {
        Behaviour.atlas = atlas
        running = true
        try {
            return Pass(events, atlas).run().trim()
        } finally {
            running = false
            Behaviour.atlas = false
        }
    }

    private fun onMain(action: () -> Unit) {
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        main.post {
            try {
                action()
            } catch (error: Throwable) {
                failure = error
            }
            done.countDown()
        }
        check(done.await(20, TimeUnit.SECONDS)) { "The keyboard did not answer" }
        failure?.let { throw it }
    }

    private class FieldText : Editor {
        private val owner = Any()
        var text = ""

        override fun identity() = Editor.Identity(owner, "benchmark", 1)
        override fun state(result: (Editor.State?) -> Unit) = result(Editor.State(text.takeLast(Editor.CONTEXT_LENGTH), "", text.length, text.length))
        override fun before() = text.takeLast(Editor.CONTEXT_LENGTH)
        override fun after() = ""
        override fun enter() = Editor.Enter.Newline

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

    private inner class Pass(private val events: JSONArray, private val atlas: Boolean) {
        private val editor = FieldText()
        private val contact = Contact()
        private val cursor = CursorMode()
        private val cursorControl = TypingSettings.cursorControl(context)
        private val tapKeys = TypingSettings.tapKeys(context)
        private val lift = TypingSettings.liftOffGraceMillis(context)
        private val settlements = ArrayDeque<Contact.Settlement>()
        private val pieces = SwipePieces({ composer.liftOffMillis.toLong() }, { composer.canContinueSwipe }) { key ->
            if (key == Layout.SPACE) ' ' in tapKeys else Layout.isLetter(key) && key[0] in tapKeys
        }
        private lateinit var composer: Composer
        private var startCursorAt: Long? = null
        private var endSlippedAt: Long? = null

        fun run(): String {
            onMain {
                composer = Composer(editor, words, { delay, action -> main.postDelayed(action, delay) }, {}, { false }, {})
                composer.reportsRecording = false
                composer.liftOffMillis = lift
                composer.start(FIELD, 0, 0)
            }
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i)
                when (event.getString("k")) {
                    "m" -> onMain { motion(event) }
                    "key" -> onMain { key(event) }
                }
                idle()
            }
            onMain { settle(Long.MAX_VALUE) }
            idle()
            return editor.text
        }

        private fun idle() {
            val deadline = System.currentTimeMillis() + 15_000
            while (true) {
                var busy = false
                onMain { busy = composer.hasPendingInput }
                if (!busy) return
                check(System.currentTimeMillis() < deadline) { "Recognition stalled" }
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
                    val first = samples.firstOrNull() ?: Sample(Float.NaN, Float.NaN, time)
                    contact.down(first)
                    if (cursor.resume(first)) {
                        endSlippedAt = null
                        contact.cancelActive()
                        cursor.move(samples.drop(1))
                        return
                    }
                    contact.move(samples.drop(1))
                    endCursor()
                    if (cursorControl && contact.travel != null && cursor.down(first)) {
                        cursor.move(samples.drop(1))
                        startCursorAt = first.time + CursorMode.REST_MILLIS
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    cursor.move(samples)
                    if (cursor.active) return
                    contact.move(samples)
                }
                MotionEvent.ACTION_UP -> {
                    if (cursor.active) {
                        cursor.lift()
                        if (lift > 0) endSlippedAt = time + lift else endCursor()
                        return
                    }
                    endCursor()
                    contact.up(samples, time)?.let { settlements.addLast(it) }
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    contact.cancelActive()
                    endCursor()
                }
            }
        }

        private fun key(event: JSONObject) {
            val time = event.getLong("t")
            advance(time)
            if (event.getBoolean("g")) return
            val down = event.getBoolean("d")
            contact.key(event.getInt("c"), down, time)
            if (!down) return
            endCursor()
            if (event.getInt("c") == KeyEvent.KEYCODE_DEL) composer.backspace(false)
        }

        private fun advance(time: Long) {
            settle(time)
            startCursorAt?.let {
                if (time >= it) {
                    startCursorAt = null
                    if (cursor.rest(it)) contact.cancelActive()
                }
            }
            endSlippedAt?.let { if (time >= it) endCursor() }
        }

        private fun endCursor() {
            startCursorAt = null
            endSlippedAt = null
            cursor.end()
        }

        private fun settle(time: Long) {
            while (settlements.isNotEmpty() && settlements.first().deadline <= time) {
                val finished = settlements.removeFirst()
                val outcome = contact.settle(finished, finished.deadline)
                if (!finished.cancelled) deliver(finished, outcome)
            }
        }

        private fun deliver(finished: Contact.Settlement, outcome: Contact.Outcome?) {
            when (val result = if (atlas) outcome else pieces.settle(finished, outcome)) {
                is Contact.Outcome.Swipe -> if (result.deletesWord) composer.backspace(true) else swipe(result.points, result.startedAt, result.endedAt)
                is Contact.Outcome.Tap -> tap(result)
                null -> Unit
            }
        }

        private fun swipe(points: List<Sample>, startedAt: Long, endedAt: Long) {
            composer.liftOffMillis = lift
            val start = points.first().time
            composer.swipe(Swipe(FloatArray(points.size) { points[it].x }, FloatArray(points.size) { points[it].y * 4 / 3 },
                FloatArray(points.size) { (points[it].time - start).toFloat() }), startedAt, endedAt, 0)
        }

        private fun tap(outcome: Contact.Outcome.Tap) {
            val isTapKey = if (outcome.key == Layout.SPACE) ' ' in tapKeys else Layout.isLetter(outcome.key) && outcome.key[0] in tapKeys
            if (!isTapKey) return
            if (outcome.key == Layout.SPACE) composer.space(repeated = false, time = outcome.endedAt)
            else composer.type(if (outcome.key == "I" && TypingSettings.autoCapitalise(context)) "I" else outcome.key.lowercase(), letterTap = true)
        }
    }

    private companion object {
        val FIELD = Field.of(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_GO or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI)
    }
}
