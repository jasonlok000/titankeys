package com.atlas.keyboard

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TitanKeys Debug's demo screen, for filming in one take: full screen, in the phone's own shape, with the typed text as kinetic
 * type. While it is in front it directs the keyboard through [ScriptedInput], so every swipe, key and touch on the strip types
 * what the script says next, whatever was really swiped. The script is not in the app: it is read from
 * getExternalFilesDir("trailer")/script.json each time the screen starts. Volume Down starts the take again.
 *
 * Each take writes a log next to the script, take-<time>.jsonl, so that the edit can draw a bar in step with the video: a line for
 * each step and each modifier key, with the uptime, and a sync line for one orange frame at the first input of the take.
 */
class TrailerActivity : Activity(), ScriptedInput.Director {
    private lateinit var view: TrailerView
    private lateinit var field: EditText
    private lateinit var note: TextView
    private var script: TrailerScript? = null
    private var stopEvents: (() -> Unit)? = null
    private lateinit var flash: View
    /** This take's log. It opens at the first input after a reset, so the newest file is the newest take. */
    private var log: BufferedWriter? = null
    /** The modifier keys that are down now, by name, for the bar's chips in the edit. */
    private val held = LinkedHashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        view = TrailerView(this)
        // The keyboard needs a field to type into. Nobody sees it: the script's text is what shows.
        field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            alpha = 0f
            setBackgroundColor(Color.TRANSPARENT)
            isCursorVisible = false
        }
        note = TextView(this).apply {
            setTextColor(Color.rgb(255, 241, 230))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(22, 10, 4))
            addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(field, FrameLayout.LayoutParams(2, 2))
            addView(note, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            // The sync mark: one orange frame over everything at the start of a take.
            flash = View(this@TrailerActivity).apply {
                setBackgroundColor(Color.rgb(239, 128, 78))
                visibility = View.GONE
            }
            addView(flash, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        })
    }

    override fun onResume() {
        super.onResume()
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        script = load()
        restart()
        ScriptedInput.director = this
        stopEvents = KeyboardEvents.listen { event ->
            when (event) {
                is KeyboardEvent.Recognised -> play(TrailerScript.Input.Swipe)
                is KeyboardEvent.DeletedWord -> if (!event.held) play(TrailerScript.Input.Flick)
                is KeyboardEvent.CursorStarted -> if (event.words) play(TrailerScript.Input.Select)
                else -> Unit
            }
        }
        field.requestFocus()
        field.post { getSystemService(InputMethodManager::class.java).showSoftInput(field, 0) }
    }

    override fun onPause() {
        closeLog()
        if (ScriptedInput.director === this) ScriptedInput.director = null
        stopEvents?.invoke()
        stopEvents = null
        ScriptedInput.refresh()
        super.onPause()
    }

    private fun load(): TrailerScript? {
        val file = File(getExternalFilesDir("trailer"), "script.json")
        return try {
            val beats = JSONObject(file.readText()).getJSONArray("beats")
            TrailerScript((0 until beats.length()).map { beat(beats.getJSONObject(it)) }).also { note.text = "" }
        } catch (error: Exception) {
            note.text = "No script. Push it to ${file.path}\n\n${error.message}"
            null
        }
    }

    private fun beat(json: JSONObject): TrailerScript.Beat = when (val input = json.getString("input")) {
        "type" -> TrailerScript.Beat.Type(json.getString("text"), json.optJSONObject("slips")?.let { slips ->
            slips.keys().asSequence().associateWith { slips.getString(it) }
        }.orEmpty())
        "swipe" -> TrailerScript.Beat.Swipe(json.getString("text"))
        "tap" -> TrailerScript.Beat.Tap(json.getString("text"), json.optJSONArray("bars")?.let { bars ->
            (0 until bars.length()).map { index -> bars.getJSONArray(index).let { bar -> (0 until bar.length()).map(bar::getString) } }
        }.orEmpty())
        "flick" -> TrailerScript.Beat.Flick(json.getString("word"))
        "select" -> TrailerScript.Beat.Select(json.getString("span"))
        "sym" -> TrailerScript.Beat.Sym(json.getString("emoji"), json.optInt("presses", 2))
        "brand" -> TrailerScript.Beat.Brand(json.getString("word"))
        else -> throw IllegalArgumentException("Unknown input \"$input\"")
    }

    /** Back to the start of the take, at once. */
    private fun restart() {
        closeLog()
        held.clear()
        script?.reset()
        view.reset()
        field.text.clear()
        ScriptedInput.refresh()
    }

    private fun play(input: TrailerScript.Input, picked: Int? = null) {
        val script = script ?: return
        val bar = script.bar
        val change = script.input(input) ?: return
        if (log == null) startTake()
        view.apply(change, script.cursorAt)
        ScriptedInput.refresh()
        write(JSONObject()
            .put("t", SystemClock.uptimeMillis())
            .put("input", input.name.lowercase())
            .put("text", script.shown)
            .put("bar", JSONArray(bar))
            .put("picked", picked?.let { PLACES[it] } ?: JSONObject.NULL)
            .put("next", JSONArray(script.bar))
            .put("chips", JSONArray(held.toList()))
            .put("selection", change.selection?.let { JSONArray().put(it.first).put(it.last + 1) } ?: JSONObject.NULL))
    }

    /** A take starts: its log opens, and one orange frame marks the moment on the video. */
    private fun startTake() {
        val name = "take-" + SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date()) + ".jsonl"
        log = File(getExternalFilesDir("trailer"), name).bufferedWriter()
        flash.visibility = View.VISIBLE
        flash.postOnAnimation {
            write(JSONObject().put("t", SystemClock.uptimeMillis()).put("sync", "orange frame"))
            flash.postDelayed({ flash.visibility = View.GONE }, FLASH_MILLIS)
        }
    }

    private fun write(line: JSONObject) {
        val out = log ?: return
        out.write(line.toString())
        out.newLine()
        out.flush()
    }

    private fun closeLog() {
        log?.close()
        log = null
        flash.visibility = View.GONE
    }

    /** A modifier key went down or up: its chip on the bar comes and goes in the edit. */
    private fun modifier(code: Int, down: Boolean) {
        val name = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
        if (down) held += name else held -= name
        write(JSONObject().put("t", SystemClock.uptimeMillis()).put("input", "modifier").put("key", name).put("down", down)
            .put("chips", JSONArray(held.toList())))
    }

    // The director. Every key that is not a system key is the script's: letters and marks type its next letter, Space its next
    // space or fix, Sym its emoji. Shift, Alt and the other modifiers do nothing, so that a capital or a mark can be typed
    // with them for the camera. Backspace does nothing either: the delete flick is the script's way to delete.

    override fun key(code: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return true
        if (Modifiers.isModifier(code)) modifier(code, event.action == KeyEvent.ACTION_DOWN)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        when {
            code == KeyEvent.KEYCODE_SYM -> play(TrailerScript.Input.Sym)
            code == KeyEvent.KEYCODE_SPACE -> play(TrailerScript.Input.Space)
            Modifiers.isModifier(code) || code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_ENTER -> Unit
            else -> play(TrailerScript.Input.Key)
        }
        return true
    }

    override fun strip(index: Int): Boolean {
        play(TrailerScript.Input.Tap, picked = index)
        return true
    }

    /** The next pick's words, from left, middle and right to the composer's order: middle, left, right. */
    override val words: List<String>
        get() = script?.bar?.takeIf { it.size == 3 }?.let { listOf(it[1], it[0], it[2]) } ?: emptyList()

    private companion object {
        /** The strip's places in the composer's order: middle, left, right. */
        val PLACES = listOf("middle", "left", "right")
        const val FLASH_MILLIS = 40L
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.repeatCount == 0) restart()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
