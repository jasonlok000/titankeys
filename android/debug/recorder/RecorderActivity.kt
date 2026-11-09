package com.atlas.keyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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

    private val prompts = BENCHMARK_WORDS
    private val main = Handler(Looper.getMainLooper())
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val replay by lazy { BenchmarkReplay(this, EngineWords(SwipeTyping.get(this))) }
    private val results = linkedMapOf<Int, Pair<Boolean, Boolean>>()
    private val preferences by lazy { getSharedPreferences("benchmark", MODE_PRIVATE) }
    private val directory by lazy { (getExternalFilesDir("recordings") ?: File(filesDir, "recordings")).apply { mkdirs() } }

    private lateinit var progress: TextView
    private lateinit var prompt: TextView
    private lateinit var instruction: TextView
    private lateinit var details: TextView
    private lateinit var input: EditText

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
        input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI
            isSingleLine = true
            textSize = 18f
            setTextColor(Color.WHITE)
            // The keyboard turns the Enter key into this field's editor action.
            setOnEditorActionListener { _, _, _ ->
                next()
                true
            }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("Skip") { skip() })
            addView(button("Redo") { redo() })
            addView(button("Switch") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() })
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
    }

    override fun onResume() {
        super.onResume()
        Recording.sink = this
        input.requestFocus()
        if (!keyboardReady()) details.text = "This app isn't your current keyboard. Tap Switch to change to it."
    }

    override fun onPause() {
        super.onPause()
        if (Recording.sink === this) Recording.sink = null
        attempt = Attempt()
    }

    override fun motion(event: MotionEvent, samples: List<Sample>) {
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
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> current.lastUp = event.eventTime
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
        if (replay.running) return
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
        details.text = "Checking..."
        worker.execute {
            val message = try {
                val before = replay.run(done.events, true)
                val after = replay.run(done.events, false)
                results[position] = before.equals(word, ignoreCase = true) to after.equals(word, ignoreCase = true)
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
        val count = results.size
        val atlas = results.values.count { it.first }
        val titan = results.values.count { it.second }
        fun line(name: String, right: Int) = "$name: $right of $count (${right * 100 / count}%)"
        return "You swiped: $word\nAtlas typed: ${before.ifEmpty { "nothing" }}\nTitanKeys typed: ${after.ifEmpty { "nothing" }}\n\n" +
            "${line("Atlas", atlas)}\n${line("TitanKeys", titan)}\nAtlas is the version TitanKeys was forked from. TitanKeys is all of its changes combined."
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
        input.setText("")
        index = position
        preferences.edit().putInt("index", index).apply()
        show()
    }

    private fun show() {
        progress.text = "${minOf(index + 1, prompts.size)} of ${prompts.size}"
        val word = prompts.getOrNull(index)
        prompt.text = word ?: "All done"
        prompt.textSize = if (pace(index) == TYPED && word != null) 22f else 40f
        instruction.text = when {
            word == null -> "Tap Export to save the session."
            pace(index) == TYPED -> "Type this on the keys as you normally would, then press Enter or Next."
            pace(index) == "rushed" -> "Swipe fast. Don't be careful. Then press Enter or Next."
            else -> "Swipe it the way you normally would, then press Enter or Next. Don't fix mistakes."
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
        val BENCHMARK_WORDS = listOf("above", "palace", "among", "turning", "ruling", "park", "exercise", "strongest", "told", "days", "proclaimed", "red", "doctorate", "civil", "star", "use", "learned", "ability", "prevented", "parent", "missions", "priest", "racial", "legal", "hosted", "referring", "completed", "sufficient", "center", "advantages")
        val MUTED = Color.rgb(142, 142, 147)
    }
}
