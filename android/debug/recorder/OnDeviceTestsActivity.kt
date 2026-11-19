package com.titankeys.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.AtomicFile
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.titankeys.engine.TypingSettings
import java.io.File

/** Runs synthetic editor assertions with the real Debug IME, entirely within the app's own window. */
class OnDeviceTestsActivity : Activity() {
    private val cases = OnDeviceTestCases.entries

    private val main = Handler(Looper.getMainLooper())
    private val results = mutableListOf<String>()
    private var active = false
    private var status = "Ready"
    private lateinit var editor: EditText
    private lateinit var label: TextView
    private lateinit var start: Button
    private lateinit var resultsView: TextView
    private val report by lazy { AtomicFile(File(filesDir, "on-device-test-results.txt")) }
    private val keyboard by lazy { getSystemService(InputMethodManager::class.java) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val margin = (20 * resources.displayMetrics.density).toInt()
            setPadding(margin, margin, margin, margin)
        }
        page.addView(TextView(this).apply { text = "TitanKeys On-device Tests"; textSize = 24f })
        page.addView(TextView(this).apply { text = "Synthetic English typing, Space, Shift, deletion and cursor checks." })
        label = TextView(this)
        page.addView(label)
        start = Button(this).apply {
            text = "Start tests"
            setOnClickListener { if (active) stop() else begin() }
        }
        page.addView(start)
        editor = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            hint = "Synthetic test field"
        }
        page.addView(editor)
        resultsView = TextView(this)
        page.addView(resultsView)
        page.addView(TextView(this).apply { text = "Full language, gesture, cross-app and build coverage is tracked separately." })
        setContentView(ScrollView(this).apply { addView(page) })
        runCatching { String(report.readFully(), Charsets.UTF_8).lineSequence().filter(String::isNotEmpty).toList() }
            .getOrNull()?.let { lines ->
                status = if (lines.size == cases.size + 1) "Last run finished. Press Start tests to run it again."
                else "Earlier results. Press Start tests for a new run."
                results.addAll(lines.drop(1))
            }
        render()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!active) return
        main.removeCallbacksAndMessages(null)
        if (hasFocus) schedule() else {
            status = "Paused. Return here to resume."
            save()
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun begin() {
        if (keyboard?.currentInputMethodInfo?.packageName != packageName || TypingSettings.currentInputMethod(this) != "en") {
            status = "Choose TitanKeys Debug and English, then press Start tests."
            render()
            return
        }
        results.clear()
        active = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        schedule()
    }

    private fun schedule() {
        if (!hasWindowFocus()) return
        editor.requestFocus()
        keyboard?.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        val deadline = SystemClock.uptimeMillis() + 8_000
        main.post { ready(deadline) }
        status = "Waiting for the test editor."
        save()
    }

    private fun ready(deadline: Long) {
        if (!active || !hasWindowFocus()) return
        if (FeedEditor.capture(packageName)?.valid() == true) next()
        else if (SystemClock.uptimeMillis() >= deadline) finishRun("The Debug keyboard did not connect to the test field.")
        else main.postDelayed({ ready(deadline) }, 100)
    }

    private fun next() {
        val test = cases.getOrNull(results.size) ?: return finishRun("Finished ${cases.size} checks.")
        if (FeedEditor.capture(packageName)?.valid() != true) return schedule()
        status = "Running ${results.size + 1}/${cases.size}. ${test.name}"
        save()
        editor.setText(test.seed)
        editor.setSelection(test.seed.length)
        main.postDelayed({
            if (!active || !hasWindowFocus()) return@postDelayed
            val reply = KeyFeed().queue(this, Intent().putExtra("keys", test.keys))
            if (!reply.startsWith("queued ")) {
                finishRun("The test feed was unavailable. ${test.name} was not measured.")
                return@postDelayed
            }
            main.postDelayed({ measure(test) }, OnDeviceTestCases.SETTLE_MILLIS)
        }, 200)
    }

    private fun measure(test: OnDeviceTestCases.Case) {
        if (!active || !hasWindowFocus()) return
        if (FeedEditor.capture(packageName)?.valid() != true) return schedule()
        val passed = editor.text.toString() == test.text &&
            editor.selectionStart == test.cursor && editor.selectionEnd == test.cursor
        results += "${if (passed) "PASS" else "FAIL"} ${test.name}"
        save()
        next()
    }

    private fun stop() {
        // Let the owned burst cancel before another run can start.
        editor.clearFocus()
        finishRun("Stopped.")
    }

    private fun finishRun(message: String) {
        active = false
        main.removeCallbacksAndMessages(null)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status = message
        save()
    }

    private fun render() {
        label.text = status
        start.text = if (active) "Stop tests" else "Start tests"
        resultsView.text = results.joinToString("\n")
    }

    private fun save() {
        val body = (listOf(status) + results).joinToString("\n", postfix = "\n")
        runCatching {
            val stream = report.startWrite()
            try {
                stream.write(body.toByteArray(Charsets.UTF_8))
                report.finishWrite(stream)
            } catch (error: Exception) {
                report.failWrite(stream)
                throw error
            }
        }.onFailure { status += " Results could not be saved." }
        render()
    }

}
