package com.atlas.keyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.atlas.engine.TypingSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TitanKeys Debug's Space gesture recorder: for each try at steering (Space twice, then swipe) or selecting (Space three
 * times, then swipe), it keeps every key press, every touchpad point with its size, what the gesture code decided and how
 * the field changed, so a tool can find which rule made a try fail. The field holds sample text only.
 */
class SpaceGestureActivity : Activity(), Recording.Sink {
    private enum class Kind(val label: String, val id: String, val colour: Int, val wanted: String) {
        Steer("Steer: Space twice, then swipe", "steer", Color.rgb(90, 200, 250), "steer"),
        Select("Select: Space three times, then swipe", "select", Color.rgb(255, 149, 0), "select"),
        Other("Other / not meant", "other", Color.rgb(142, 142, 147), "nothing"),
    }

    /** One try: everything from its first touch or key until a quiet moment, in event order. */
    private class Try(val kind: Kind, val start: Long, val textBefore: String) {
        val events = JSONArray()
        var end = start
        var drag: Boolean? = null
        var doubleTap = false
        var textAfter = textBefore
        var touches = 0
        var keys = 0

        fun result(): String = when {
            drag == false -> "steer"
            drag == true -> "select"
            doubleTap -> "selected a word (double tap)"
            textAfter != textBefore -> "typed '${typed()}'"
            else -> "nothing"
        }

        /** What the try added to the text, or a note if it only removed some. */
        fun typed(): String {
            var front = 0
            while (front < textBefore.length && front < textAfter.length && textBefore[front] == textAfter[front]) front++
            var back = 0
            while (back < textBefore.length - front && back < textAfter.length - front &&
                textBefore[textBefore.length - 1 - back] == textAfter[textAfter.length - 1 - back]) back++
            val added = textAfter.substring(front, textAfter.length - back)
            return added.ifEmpty { "(deleted ${textBefore.length - textAfter.length})" }
        }

        fun worked() = result() == kind.wanted
    }

    private val main = Handler(Looper.getMainLooper())
    private val directory by lazy { (getExternalFilesDir("space-gestures") ?: File(filesDir, "space-gestures")).apply { mkdirs() } }
    private val tries = ArrayList<Try>()
    private var current: Try? = null
    private var kind = Kind.Steer
    private var fingerDown = false
    /** This session's file. Each try is added the moment it ends, so leaving the screen loses nothing. */
    private var session: File? = null
    private var settingText = false

    private lateinit var input: EditText
    private lateinit var live: TextView
    private lateinit var counts: TextView
    private lateinit var status: TextView
    private val kindButtons = HashMap<Kind, Button>()
    private val finish = Runnable { finishTry() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        live = text(22f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD }
        counts = text(14f, Color.WHITE).apply { typeface = Typeface.MONOSPACE }
        status = text(13f, MUTED)
        input = object : EditText(this) {
            override fun onSelectionChanged(selStart: Int, selEnd: Int) {
                super.onSelectionChanged(selStart, selEnd)
                // The field reports its first selection while the screen is built, before there is anything to log.
                if (::input.isInitialized) log("sel", SystemClock.uptimeMillis(), JSONArray().put(selStart).put(selEnd))
            }
        }.apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            textSize = 16f
            minLines = 4
            setTextColor(Color.WHITE)
            setText(SAMPLE)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (settingText) return
                    val now = s.toString()
                    log("text", SystemClock.uptimeMillis(), JSONArray().put(now.length))
                    current?.textAfter = now
                }
            })
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 16, 40, 32)
            addView(text(20f, Color.WHITE).apply {
                typeface = Typeface.DEFAULT_BOLD
                text = "Space gestures"
            })
            addView(text(13f, MUTED).apply {
                text = "Pick what you mean to do, put the cursor in the text, then try it once on the keys. " +
                    "Wait a moment between tries: each try ends after 1.5 seconds without a touch or a key."
            })
            for (each in Kind.values()) addView(kindButton(each))
            addView(input)
            addView(live)
            addView(counts)
            addView(row(listOf(
                button("Reset text") { resetText() },
                button("Clear") { clear() },
                button("Export") { export() },
                button("Switch") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
            )))
            addView(status)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            addView(content)
        })
        choose(Kind.Steer)
        show()
    }

    override fun onResume() {
        super.onResume()
        Recording.sink = this
        input.requestFocus()
        status.text = when {
            !keyboardReady() -> "TitanKeys Debug isn't the current keyboard. Tap Switch to change to it."
            !TypingSettings.cursorControl(this) -> "\"Space taps move the cursor\" is off in Settings, so these gestures are off."
            else -> ""
        }
    }

    override fun onPause() {
        super.onPause()
        if (Recording.sink === this) Recording.sink = null
        main.removeCallbacks(finish)
        finishTry()
    }

    override fun motion(event: MotionEvent, samples: List<Sample>) {
        val scale = sensorScale(event)
        val sizes = ArrayList<Float>(event.historySize + 1)
        for (i in 0 until event.historySize) sizes += event.getHistoricalSize(i) * scale
        if (event.actionMasked != MotionEvent.ACTION_CANCEL) sizes += event.size * scale
        val count = minOf(samples.size, sizes.size)
        val points = JSONArray()
        for (i in 0 until count) {
            val sample = samples[samples.size - count + i]
            points.put(JSONArray().put(round(sample.x)).put(round(sample.y)).put(Math.round(sizes[sizes.size - count + i])).put(sample.time))
        }
        val action = event.actionMasked
        val time = event.eventTime
        val fingers = event.pointerCount
        runOnUiThread {
            if (action == MotionEvent.ACTION_DOWN) {
                fingerDown = true
                begin(time).touches++
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) fingerDown = false
            log("m", time, JSONObject().put("a", action).put("fingers", fingers).put("p", points))
        }
    }

    override fun key(event: KeyEvent, gesture: Boolean) {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        val time = event.eventTime
        val repeat = event.repeatCount
        runOnUiThread {
            if (down && repeat == 0) begin(time).keys++
            log("key", time, JSONObject().put("c", code).put("name", KeyEvent.keyCodeToString(code)).put("d", down)
                .put("repeat", repeat).put("g", gesture))
        }
    }

    override fun outcome(outcome: Contact.Outcome) {
        val detail = when (outcome) {
            is Contact.Outcome.Swipe -> JSONObject().put("type", "swipe").put("n", outcome.points.size).put("deletes", outcome.deletesWord)
            is Contact.Outcome.Tap -> JSONObject().put("type", "tap").put("key", outcome.key)
        }
        runOnUiThread { log("out", SystemClock.uptimeMillis(), detail) }
    }

    override fun recognised(words: List<String>, resumed: Boolean) {
        runOnUiThread { log("rec", SystemClock.uptimeMillis(), JSONArray(words.take(3))) }
    }

    override fun gesture(event: String, time: Long, detail: String) {
        runOnUiThread {
            val open = current ?: begin(time)
            if (event == "drag") open.drag = detail.contains("words=true")
            if (event == "double") open.doubleTap = true
            log("gesture", time, JSONObject().put("event", event).put("detail", detail))
        }
    }

    private fun begin(time: Long): Try = current ?: Try(kind, time, input.text.toString()).also {
        current = it
        live.text = "Trying: ${kind.label}…"
    }

    /** Adds one event to the open try and keeps the try open for another 1.5 seconds. */
    private fun log(type: String, time: Long, data: Any) {
        val open = current ?: return
        open.events.put(JSONArray().put(type).put(time).put(data))
        open.end = maxOf(open.end, time)
        main.removeCallbacks(finish)
        main.postDelayed(finish, QUIET_MILLIS)
    }

    private fun finishTry() {
        if (fingerDown) {
            main.postDelayed(finish, QUIET_MILLIS)
            return
        }
        val done = current ?: return
        current = null
        done.textAfter = input.text.toString()
        tries += done
        save(done)
        live.text = "Read as: ${done.result()}" + if (done.worked()) "  ✓" else "  ✗"
        show()
    }

    private fun show() {
        counts.text = Kind.values().joinToString("\n") { each ->
            val mine = tries.filter { it.kind == each }
            String.format(Locale.US, "%-7s %3d worked  %3d failed", each.id, mine.count { it.worked() }, mine.count { !it.worked() })
        }
        if (tries.isEmpty() && current == null) live.text = "Read as: –"
    }

    private fun choose(next: Kind) {
        kind = next
        for ((each, button) in kindButtons) button.alpha = if (each == next) 1f else .45f
    }

    private fun resetText() {
        settingText = true
        input.setText(SAMPLE)
        settingText = false
        input.setSelection(input.text.length)
    }

    private fun clear() {
        tries.clear()
        current = null
        session = null
        resetText()
        show()
    }

    /**
     * Adds a finished try to this session's file, which starts with a header holding the gesture's numbers. The file is
     * in the app's files on the phone (Android/data/<package>/files/space-gestures), so a closed screen loses nothing.
     */
    private fun save(done: Try) {
        val file = session ?: File(directory, "space-gestures-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.jsonl").also {
            val tuning = JSONObject()
            for ((key, value) in CursorGesture.tuning) tuning.put(key, value)
            it.writeText(JSONObject().put("format", 1).put("model", Build.MODEL).put("android", Build.VERSION.RELEASE)
                .put("app", packageManager.getPackageInfo(packageName, 0).versionName)
                .put("cursorControl", TypingSettings.cursorControl(this)).put("tuning", tuning)
                .put("settleMillis", Contact.SETTLE_MILLIS).put("swipeKeys", Contact.SWIPE_KEYS)
                .put("points", "x, y (0 to 1 across the keys and down the four rows), sensor size step, time").toString() + "\n")
            session = it
        }
        try {
            file.appendText(JSONObject().put("kind", done.kind.id).put("start", done.start).put("end", done.end)
                .put("result", done.result()).put("worked", done.worked()).put("touches", done.touches).put("keys", done.keys)
                .put("events", done.events).toString() + "\n")
            status.text = "Saved ${tries.size} tries in ${file.name}."
        } catch (error: Exception) {
            status.text = "Could not save the try: ${error.message}"
        }
    }

    /** Asks where to save a copy of this session's file, which already holds every try. */
    @Suppress("DEPRECATION")
    private fun export() {
        val file = session
        if (file == null) {
            status.text = "Nothing to export yet."
            return
        }
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, file.name), EXPORT)
    }

    @Deprecated("Android activity result callback")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        val file = session
        if (requestCode != EXPORT || file == null) return
        status.text = if (resultCode != RESULT_OK || uri == null) "Kept ${file.name} in the app's files only." else try {
            contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            "Exported ${file.name}."
        } catch (error: Exception) {
            "Export failed: ${error.message}"
        }
    }

    /** Android's size axis is the sensor's touch major over 255, so this turns it back into the sensor's steps. */
    private fun sensorScale(event: MotionEvent): Float {
        val device = event.device
        val range = device?.getMotionRange(MotionEvent.AXIS_SIZE, event.source) ?: device?.getMotionRange(MotionEvent.AXIS_SIZE)
        val largest = range?.max?.takeIf { it.isFinite() && it > 0f } ?: 1f
        return 255f / largest
    }

    private fun keyboardReady() =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true

    private fun round(value: Float): Double = Math.round(value.toDouble() * 10000.0) / 10000.0

    private fun text(size: Float, colour: Int) = TextView(this).apply {
        textSize = size
        setTextColor(colour)
        setPadding(0, 8, 0, 8)
    }

    private fun row(buttons: List<Button>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (button in buttons) addView(button)
    }

    private fun kindButton(each: Kind) = Button(this).apply {
        text = each.label
        textSize = 13f
        isAllCaps = false
        setTextColor(each.colour)
        setOnClickListener { choose(each) }
        kindButtons[each] = this
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener { action() }
    }

    private companion object {
        const val EXPORT = 1
        const val QUIET_MILLIS = 1500L
        val MUTED = Color.rgb(142, 142, 147)
        const val SAMPLE = "The quick brown fox jumps over the lazy dog. Move the cursor through these words, " +
            "or select a few of them, then try again. Nothing here is kept."
    }
}
