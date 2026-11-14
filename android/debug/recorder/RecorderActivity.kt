package com.atlas.keyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.atlas.engine.Calibration
import com.atlas.engine.SwipeTyping
import com.atlas.engine.TypingSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TitanKeys Debug's sample recorder. Shows one word at a time, captures the touchpad and key events of the
 * swipe made for it, and writes one JSON line per word. It records only while this screen is in front.
 * The field asks for no personalised learning, so a session never teaches TitanKeys Debug its prompts.
 */
class RecorderActivity : Activity(), Recording.Sink {
    private class Attempt {
        val events = JSONArray()
        val gaps = JSONArray()
        var touches = 0
        var swipes = 0
        var presses = 0
        var lastUp = -1L
        var joined = false
        var top: List<String> = emptyList()
    }

    private val prompts = BENCHMARK_SENTENCES
    private val main = Handler(Looper.getMainLooper())
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val replay by lazy { BenchmarkReplay(this, EngineWords(SwipeTyping.get(this))) }
    private val results = linkedMapOf<Int, Triple<Int, Int, Int>>()
    private var wordIndex = 0
    private var touching = false
    private val finishSentence = object : Runnable {
        override fun run() {
            if (touching) main.postDelayed(this, 300) else next()
        }
    }
    private val preferences by lazy { getSharedPreferences("benchmark", MODE_PRIVATE) }
    private val directory by lazy { (getExternalFilesDir("recordings") ?: File(filesDir, "recordings")).apply { mkdirs() } }

    private lateinit var progress: TextView
    private lateinit var prompt: TextView
    private lateinit var instruction: TextView
    private lateinit var details: TextView
    private lateinit var input: TextView
    private val touchpad by lazy { Touchpad(getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)) }
    private var catcher: View? = null
    private var strokeLeft = 1f
    private var strokeRight = 0f
    private val strokeDone = Runnable {
        val moved = strokeRight - strokeLeft >= MIN_STROKE
        strokeLeft = 1f
        strokeRight = 0f
        if (moved) wordFinished()
    }

    private var file: File? = null
    private var index = 0
    private var attempt = Attempt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progress = text(13f, MUTED)
        prompt = text(40f, Color.WHITE).apply {
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        instruction = text(14f, MUTED).apply { gravity = Gravity.CENTER }
        details = text(14f, Color.WHITE)
        // There is no text field on purpose. Without one no keyboard opens, so this works whichever keyboard is selected.
        input = TextView(this).apply { visibility = View.GONE }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("Skip") { skip() })
            addView(button("Redo") { redo() })
            addView(button("Export") { export() })
            addView(button("New") { newSession() })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            // fitsSystemWindows replaces this view's padding, so the margin belongs on a child.
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 16, 48, 16)
                addView(progress)
                addView(prompt)
                addView(instruction)
                addView(input)
                addView(Button(context).apply {
                    text = "Next"
                    textSize = 16f
                    isAllCaps = false
                    setOnClickListener { next() }
                })
                addView(details)
                addView(buttons)
            })
        })
        index = preferences.getInt("index", 0)
        file = preferences.getString("file", null)?.let { File(directory, it) }?.takeIf { it.exists() }
        if (file == null) newSession() else show()
        Calibration.init(this)
        details.text = "Loading the swipe engine..."
        SwipeTyping.get(this).whenReady { _, _, error ->
            details.text = if (error == null) "Ready. Swipe the word on your keyboard." else "Couldn't load the swipe engine: $error"
        }
    }

    override fun onResume() {
        super.onResume()
        Recording.sink = this
        window.decorView.post { addCatcher() }
    }

    override fun onPause() {
        super.onPause()
        if (Recording.sink === this) Recording.sink = null
        removeCatcher()
        main.removeCallbacks(strokeDone)
        attempt = Attempt()
    }

    /** The firmware sends the touchpad to the topmost window with "InputMethod" in its title, so this tiny one asks for it. */
    private fun addCatcher() {
        if (catcher != null || isFinishing) return
        val view = object : View(this) {
            override fun dispatchTouchEvent(event: MotionEvent) = fromTouchpad(event)
            override fun dispatchGenericMotionEvent(event: MotionEvent) = fromTouchpad(event)
        }
        val params = WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            token = window.decorView.windowToken
            title = "BenchmarkInputMethodTouchpad"
        }
        try {
            windowManager.addView(view, params)
            catcher = view
        } catch (error: RuntimeException) {
            details.text = "Couldn't reach the touchpad: ${error.message}"
        }
    }

    private fun removeCatcher() {
        val view = catcher ?: return
        catcher = null
        windowManager.removeViewImmediate(view)
    }

    private fun fromTouchpad(event: MotionEvent): Boolean {
        if (!touchpad.accepts(event)) return false
        // Unbuffered, as the keyboard reads it, so the samples match real typing.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) (catcher ?: window.decorView).requestUnbufferedDispatch(event)
        motion(event, touchpad.samples(event))
        return true
    }

    // Without the catcher window, the firmware delivers the touchpad to the app in Sliding Mode 1.
    override fun dispatchTouchEvent(event: MotionEvent) = fromTouchpad(event) || super.dispatchTouchEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent) = fromTouchpad(event) || super.dispatchGenericMotionEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_ENTER) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) next()
        return true
    }

    override fun motion(event: MotionEvent, samples: List<Sample>) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) main.removeCallbacks(strokeDone)
        for (sample in samples) if (sample.x.isFinite()) {
            strokeLeft = minOf(strokeLeft, sample.x)
            strokeRight = maxOf(strokeRight, sample.x)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) main.postDelayed(strokeDone, STROKE_DONE_MILLIS)
        val current = attempt
        val points = JSONArray()
        for (i in 0 until event.historySize)
            points.put(point(event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalEventTime(i)))
        points.put(point(event.x, event.y, event.eventTime))
        val converted = JSONArray()
        for (sample in samples) converted.put(point(sample.x, sample.y, sample.time))
        current.events.put(JSONObject().put("k", "m").put("a", event.actionMasked).put("t", event.eventTime)
            .put("rx", number(event.rawX)).put("ry", number(event.rawY)).put("p", points).put("s", converted))
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (current.touches > 0 && current.lastUp >= 0) current.gaps.put(event.eventTime - current.lastUp)
                current.touches++
                touching = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                current.lastUp = event.eventTime
                touching = false
            }
        }
    }

    override fun key(event: KeyEvent, gesture: Boolean) {
        if (event.action == KeyEvent.ACTION_DOWN && !gesture && event.repeatCount == 0
            && event.keyCode != KeyEvent.KEYCODE_ENTER) attempt.presses++
        attempt.events.put(JSONObject().put("k", "key").put("c", event.keyCode)
            .put("d", event.action == KeyEvent.ACTION_DOWN).put("t", event.eventTime).put("g", gesture))
    }

    override fun outcome(outcome: Contact.Outcome) {
        val entry = JSONObject().put("k", "out")
        when (outcome) {
            is Contact.Outcome.Swipe -> {
                attempt.swipes++
                entry.put("type", "swipe").put("n", outcome.points.size)
                    .put("start", outcome.startedAt).put("end", outcome.endedAt)
            }
            is Contact.Outcome.Tap -> entry.put("type", "tap").put("key", outcome.key).put("end", outcome.endedAt)
        }
        attempt.events.put(entry)
    }

    override fun recognised(words: List<String>, resumed: Boolean) {
        attempt.top = words
        attempt.joined = attempt.joined || resumed
        attempt.events.put(JSONObject().put("k", "rec").put("words", JSONArray(words)).put("resumed", resumed))
    }

    /** Ends the current word. Everything since the last word is kept, however many touches it took. */
    private fun next() {
        val word = prompts.getOrNull(index) ?: return
        val done = attempt
        if (done.touches == 0 && done.presses == 0) {
            details.text = "Nothing recorded for this word yet. Tap Skip to leave it out."
            return
        }
        val typed = input.text.toString()
        append(JSONObject().put("prompt", word).put("index", index).put("pace", pace(index))
            .put("touches", done.touches).put("gaps", done.gaps).put("joined", done.joined)
            .put("top", JSONArray(done.top)).put("text", typed).put("events", done.events))
        val position = index
        main.removeCallbacks(finishSentence)
        details.text = "Checking..."
        worker.execute {
            val message = try {
                val before = replay.run(done.events, true)
                val after = replay.run(done.events, false)
                val expected = word.split(" ")
                results[position] = Triple(wordsRight(expected, before), wordsRight(expected, after), expected.size)
                report(word, before, after)
            } catch (error: Exception) {
                "Couldn't check that swipe: ${error.message}"
            }
            main.post { details.text = message }
        }
        move(index + 1)
    }

    /** Both versions read the same touches, so the only difference between the scores is the code. */
    private fun report(word: String, before: String, after: String): String {
        return "Sentence: $word\nAtlas typed: ${before.ifEmpty { "nothing" }}\nTitanKeys typed: ${after.ifEmpty { "nothing" }}\n\n${scores()}"
    }

    private fun scores(): String {
        val total = results.values.sumOf { it.third }
        if (total == 0) return "Scores appear after the first sentence."
        fun line(name: String, right: Int) = "$name: $right of $total words (${right * 100 / total}%)"
        return "${line("Atlas", results.values.sumOf { it.first })}\n${line("TitanKeys", results.values.sumOf { it.second })}\n" +
            "Atlas is the version TitanKeys was forked from. TitanKeys is all of its changes combined."
    }

    private fun wordFinished() {
        val sentence = prompts.getOrNull(index)?.split(" ") ?: return
        wordIndex++
        show()
        val soFar = JSONArray(attempt.events.toString())
        worker.execute {
            val message = try {
                val before = replay.run(soFar, true)
                val after = replay.run(soFar, false)
                "Atlas so far: ${before.ifEmpty { "nothing" }}\nTitanKeys so far: ${after.ifEmpty { "nothing" }}\n\n${scores()}"
            } catch (error: Exception) {
                "Couldn't check that swipe: ${error.message}"
            }
            main.post { details.text = message }
        }
        if (wordIndex >= sentence.size) {
            main.removeCallbacks(finishSentence)
            main.postDelayed(finishSentence, 900)
        }
    }

    /** How many of the sentence's words were typed, in order. Extra or missing words only lose the words they affect. */
    private fun wordsRight(expected: List<String>, typed: String): Int {
        val words = typed.lowercase().split(" ").filter { it.isNotEmpty() }
        val best = Array(expected.size + 1) { IntArray(words.size + 1) }
        for (i in expected.indices.reversed()) for (j in words.indices.reversed())
            best[i][j] = if (expected[i] == words[j]) best[i + 1][j + 1] + 1 else maxOf(best[i + 1][j], best[i][j + 1])
        return best[0][0]
    }

    private fun unusedDescribe(word: String, done: Attempt): String {
        if (pace(index) == TYPED)
            return "Last sentence: ${done.presses} key presses, ${done.touches} touches, ${done.swipes} read as swipes"
        val verdict = when {
            done.top.isEmpty() -> "no word found"
            done.top.first().equals(word, ignoreCase = true) -> "correct"
            done.top.any { it.equals(word, ignoreCase = true) } -> "in suggestions"
            else -> "missed"
        }
        val touches = if (done.touches == 1) "1 touch, no gaps"
            else "${done.touches} touches, gaps ${done.gaps.join(", ")} ms, ${if (done.joined) "joined" else "not joined"}"
        return "Last word: $word, $verdict\nTop: ${done.top.take(3).joinToString(", ")}\n$touches"
    }

    private fun skip() {
        if (index >= prompts.size) return
        append(JSONObject().put("skip", index))
        move(index + 1)
    }

    private fun redo() {
        if (index == 0) return
        append(JSONObject().put("redo", index - 1))
        details.text = "Redoing the last word."
        move(index - 1)
    }

    private fun newSession() {
        val name = "benchmark-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.jsonl"
        val created = File(directory, name)
        file = created
        preferences.edit().putString("file", name).apply()
        append(JSONObject().put("format", 1).put("model", Build.MODEL).put("android", Build.VERSION.RELEASE)
            .put("atlas", packageManager.getPackageInfo(packageName, 0).versionName)
            .put("slipMillis", TypingSettings.liftOffGraceMillis(this)))
        details.text = ""
        results.clear()
        move(0)
    }

    @Suppress("DEPRECATION")
    private fun export() {
        val current = file ?: return
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, current.name), EXPORT)
    }

    @Deprecated("Android activity result callback")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        val current = file
        if (requestCode != EXPORT || resultCode != RESULT_OK || uri == null || current == null) return
        details.text = try {
            contentResolver.openOutputStream(uri)!!.use { out -> current.inputStream().use { it.copyTo(out) } }
            "Exported ${current.name}."
        } catch (error: Exception) {
            "Export failed: ${error.message}"
        }
    }

    private fun move(position: Int) {
        attempt = Attempt()
        if (!touching) input.setText("")
        index = position
        wordIndex = 0
        preferences.edit().putInt("index", index).apply()
        show()
    }

    private fun show() {
        val sentence = prompts.getOrNull(index)?.split(" ")
        progress.text = if (sentence == null) "Finished" else "Sentence ${index + 1} of ${prompts.size}, word ${minOf(wordIndex + 1, sentence.size)} of ${sentence.size}"
        val word = sentence?.let { it.getOrNull(wordIndex) ?: it.last() }
        prompt.text = word ?: "All done"
        prompt.textSize = if (pace(index) == TYPED && word != null) 22f else 40f
        instruction.text = when {
            word == null -> "Tap Export to save the session."
            pace(index) == TYPED -> "Type this on the keys as you normally would, then press Enter or Next."
            pace(index) == "rushed" -> "Swipe fast. Don't be careful. Then press Enter or Next."
            else -> "Swipe each word on the keyboard the way you normally would. It moves on by itself. Don't fix mistakes."
        }
    }

    private fun append(line: JSONObject) {
        file?.appendText(line.toString() + "\n")
    }

    private fun keyboardReady() =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true

    private fun pace(position: Int) = "benchmark"

    private fun point(x: Float, y: Float, time: Long) = JSONArray().put(number(x)).put(number(y)).put(time)

    private fun number(value: Float): Any =
        if (value.isFinite()) Math.round(value.toDouble() * 100000.0) / 100000.0 else JSONObject.NULL

    private fun text(size: Float, colour: Int) = TextView(this).apply {
        textSize = size
        setTextColor(colour)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener { action() }
    }

    private companion object {
        const val NORMAL_WORDS = 200
        const val EXPORT = 1
        const val TYPED = "typed"
        val TYPED_SENTENCES = listOf(
            "see you at the station tomorrow", "can you send me the address", "i will call you after work",
            "the meeting moved to next thursday", "please bring your laptop and charger",
            "we should leave before it rains", "my phone battery is almost empty", "let me know when you arrive",
            "dinner is ready in ten minutes", "she left her keys on the table", "what time does the store close",
            "i forgot to reply to your message", "the train was late again today", "could you pick up some milk",
            "thanks for helping me move house", "he is working from home this week", "we watched a film last night",
            "the kids are playing in the garden", "do you want coffee or tea", "i need to book a dentist appointment",
            "it was really good to see you", "the new update fixed the problem", "turn left at the next traffic light",
            "they are coming over on saturday", "remember to lock the door when you leave",
        )
        val BENCHMARK_SENTENCES = listOf(
            "our flight leaves early next monday",
            "thanks for cooking such great food",
            "she painted the fence bright yellow",
            "could you check the printer again",
            "my sister loves reading mystery novels",
            "the market closes right after noon",
            "we should probably order some pizza",
            "his bicycle needs two new tires",
        )
        /** A swipe that breaks at the top edge comes back within this time, so the word only ends after it. */
        const val STROKE_DONE_MILLIS = 450L
        const val MIN_STROKE = .06f
        val MUTED = Color.rgb(142, 142, 147)
    }
}
