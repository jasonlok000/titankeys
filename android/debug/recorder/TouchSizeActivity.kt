package com.atlas.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * TitanKeys Debug's touch size recorder, for issue #30. It shows the size that the touchpad reports for each touch, on a map
 * of the keys, and sums it up for each kind of touch that you pick, so that you can see what the number means. It keeps only
 * touch positions, times and sizes while this screen is in front, never text.
 */
class TouchSizeActivity : Activity(), Recording.Sink {
    private enum class Kind(val label: String, val id: String, val colour: Int) {
        Free("Free", "free", Color.rgb(142, 142, 147)),
        TipSwipe("Fingertip swipe", "tip_swipe", Color.rgb(255, 149, 0)),
        ThumbSwipe("Flat thumb swipe", "thumb_swipe", Color.rgb(90, 200, 250)),
        ThumbRest("Thumb rest", "thumb_rest", Color.rgb(52, 199, 89)),
        TipRest("Fingertip rest", "tip_rest", Color.rgb(255, 55, 95)),
    }

    /** A point on the touchpad, from 0 to 1 across the keys and down the rows, with its size on the sensor's own scale. */
    private class Point(val x: Float, val y: Float, val size: Float, val time: Long)

    private class Touch(val kind: Kind, val start: Long) {
        val points = ArrayList<Point>()
        var end = start
        var fingers = 1
        var reading = ""

        /** How far the touch moved, in key widths. */
        fun travel(): Float {
            var sum = 0f
            for (i in 1 until points.size)
                sum += hypot((points[i].x - points[i - 1].x) * COLUMNS, (points[i].y - points[i - 1].y) * ROWS)
            return sum
        }
    }

    private val directory by lazy { (getExternalFilesDir("touch-size") ?: File(filesDir, "touch-size")).apply { mkdirs() } }
    private val touches = ArrayList<Touch>()
    private var current: Touch? = null
    private var kind = Kind.Free
    private var saved: File? = null

    private lateinit var live: TextView
    private lateinit var map: SizeMap
    private lateinit var last: TextView
    private lateinit var summary: TextView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private val kindButtons = HashMap<Kind, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        live = text(34f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD }
        map = SizeMap(this)
        last = text(14f, Color.WHITE)
        summary = text(13f, Color.WHITE).apply { typeface = Typeface.MONOSPACE }
        status = text(13f, MUTED)
        input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            isSingleLine = true
            textSize = 16f
            hint = "Keep the cursor here. Words you swipe go here and are not kept."
            setHintTextColor(MUTED)
            setTextColor(Color.WHITE)
        }
        val kinds = Kind.values().toList()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 16, 40, 32)
            addView(text(20f, Color.WHITE).apply {
                typeface = Typeface.DEFAULT_BOLD
                text = "Touch size"
            })
            addView(text(13f, MUTED).apply {
                text = "The touchpad gives each point of a touch a size from 0 to 255. On the Titan 2 Elite it is usually 1 to 5. " +
                    "Pick a kind of touch, then do it 10 times on the keys. Each circle on the map is one point, as large as its size."
            })
            addView(live)
            addView(map)
            addView(last)
            addView(row(kinds.take(3).map { kindButton(it) }))
            addView(row(kinds.drop(3).map { kindButton(it) }))
            addView(input)
            addView(row(listOf(
                button("Clear") { clear() },
                button("Export") { export() },
                button("Switch") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
            )))
            addView(summary)
            addView(status)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            addView(content)
        })
        choose(Kind.Free)
        show()
    }

    override fun onResume() {
        super.onResume()
        Recording.sink = this
        input.requestFocus()
        status.text = if (keyboardReady()) "" else "TitanKeys Debug isn't the current keyboard. Tap Switch to change to it."
    }

    override fun onPause() {
        super.onPause()
        if (Recording.sink === this) Recording.sink = null
        current = null
    }

    override fun motion(event: MotionEvent, samples: List<Sample>) {
        // Read everything now: the keyboard may reuse the event after this returns.
        val unit = sizeUnit(event)
        val sizes = ArrayList<Float>(event.historySize + 1)
        for (i in 0 until event.historySize) sizes += event.getHistoricalTouchMajor(i) / unit
        if (event.actionMasked != MotionEvent.ACTION_CANCEL) sizes += event.touchMajor / unit
        val count = minOf(samples.size, sizes.size)
        val points = (0 until count).map { i ->
            val sample = samples[samples.size - count + i]
            Point(sample.x, sample.y, sizes[sizes.size - count + i], sample.time)
        }.filter { it.x.isFinite() && it.y.isFinite() && it.size.isFinite() }
        val action = event.actionMasked
        val fingers = event.pointerCount
        val time = event.eventTime
        runOnUiThread { take(action, points, fingers, time) }
    }

    override fun outcome(outcome: Contact.Outcome) {
        val reading = when (outcome) {
            is Contact.Outcome.Swipe -> "TitanKeys read it as a swipe of ${outcome.points.size} points."
            is Contact.Outcome.Tap -> "TitanKeys read it as a tap on ${outcome.key}."
        }
        runOnUiThread {
            val touch = current ?: touches.lastOrNull() ?: return@runOnUiThread
            touch.reading = reading
            if (touch !== current) last.text = describe(touch)
        }
    }

    override fun key(event: KeyEvent, gesture: Boolean) {}

    override fun recognised(words: List<String>, resumed: Boolean) {}

    private fun take(action: Int, points: List<Point>, fingers: Int, time: Long) {
        if (action == MotionEvent.ACTION_DOWN || current == null) {
            if (touches.size >= MAX_TOUCHES) touches.removeAt(0)
            current = Touch(kind, time).also { touches += it }
        }
        val touch = current ?: return
        touch.points += points
        touch.end = time
        touch.fingers = max(touch.fingers, fingers)
        points.lastOrNull()?.let { live.text = "Size ${it.size.roundToInt()}" }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            current = null
            last.text = describe(touch)
            show()
        }
        map.invalidate()
    }

    private fun describe(touch: Touch): String {
        val sizes = touch.points.map { it.size }
        if (sizes.isEmpty()) return "Last touch: no position was read."
        val fingers = if (touch.fingers == 1) "1 finger" else "${touch.fingers} fingers"
        return String.format(Locale.US, "Last touch, %s: %.2f s, moved %.1f keys, %d points, size %d to %d, average %.1f, %s.",
            touch.kind.label, (touch.end - touch.start) / 1000f, touch.travel(), sizes.size,
            (sizes.minOrNull() ?: 0f).roundToInt(), (sizes.maxOrNull() ?: 0f).roundToInt(), sizes.average(), fingers) +
            if (touch.reading.isEmpty()) "" else "\n${touch.reading}"
    }

    /** One block for each kind of touch: how many, their sizes, and how often each size came up. */
    private fun show() {
        val blocks = ArrayList<String>()
        for (kind in Kind.values()) {
            val done = touches.filter { it.kind == kind && it !== current && it.points.isNotEmpty() }
            if (done.isEmpty()) continue
            val sizes = done.flatMap { touch -> touch.points.map { it.size } }
            val shares = sizes.groupingBy { it.roundToInt() }.eachCount().toSortedMap().entries
                .joinToString("  ") { "${it.key}: ${100 * it.value / sizes.size}%" }
            val seconds = done.map { (it.end - it.start) / 1000.0 }.average()
            val travel = done.map { it.travel().toDouble() }.average()
            blocks += String.format(Locale.US, "%s, %d touches\n  size average %.1f, largest %d\n  %s\n  %.2f s and %.1f keys a touch",
                kind.label, done.size, sizes.average(), (sizes.maxOrNull() ?: 0f).roundToInt(), shares, seconds, travel)
        }
        summary.text = if (blocks.isEmpty()) "No touches yet." else blocks.joinToString("\n\n")
        if (touches.isEmpty()) {
            live.text = "Size –"
            last.text = "Touch the keys."
        }
    }

    private fun choose(next: Kind) {
        kind = next
        for ((each, button) in kindButtons) button.alpha = if (each == next) 1f else .45f
    }

    private fun clear() {
        touches.clear()
        current = null
        saved = null
        input.setText("")
        show()
        map.invalidate()
    }

    /** Writes the touches as JSON lines, then asks where to save a copy. */
    @Suppress("DEPRECATION")
    private fun export() {
        val done = touches.filter { it !== current && it.points.isNotEmpty() }
        if (done.isEmpty()) {
            status.text = "Nothing to export yet."
            return
        }
        val name = "touch-size-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.jsonl"
        val file = File(directory, name)
        file.writeText(JSONObject().put("format", 1).put("model", Build.MODEL).put("android", Build.VERSION.RELEASE)
            .put("app", packageManager.getPackageInfo(packageName, 0).versionName)
            .put("size", "touch major on the sensor's 0 to 255 scale").toString() + "\n")
        for (touch in done) {
            val points = JSONArray()
            for (point in touch.points)
                points.put(JSONArray().put(round(point.x)).put(round(point.y)).put(round(point.size)).put(point.time))
            file.appendText(JSONObject().put("kind", touch.kind.id).put("start", touch.start).put("end", touch.end)
                .put("fingers", touch.fingers).put("reading", touch.reading).put("points", points).toString() + "\n")
        }
        saved = file
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, name), EXPORT)
    }

    @Deprecated("Android activity result callback")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        val file = saved
        if (requestCode != EXPORT || file == null) return
        status.text = if (resultCode != RESULT_OK || uri == null) "Kept ${file.name} in the app's files only." else try {
            contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            "Exported ${file.name}."
        } catch (error: Exception) {
            "Export failed: ${error.message}"
        }
    }

    /** The touchpad reports touch major scaled to the screen. This divides by the scale to get the sensor's own steps. */
    private fun sizeUnit(event: MotionEvent): Float {
        val device = event.device
        val range = device?.getMotionRange(MotionEvent.AXIS_TOUCH_MAJOR, event.source)
            ?: device?.getMotionRange(MotionEvent.AXIS_TOUCH_MAJOR)
        val largest = range?.max?.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_TOUCH_MAJOR_MAX
        return largest / RAW_SIZE_MAX
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

    private fun kindButton(each: Kind) = button(each.label) { choose(each) }.apply {
        setTextColor(each.colour)
        kindButtons[each] = this
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener { action() }
    }

    /** The Titan keys, with the latest touches drawn on them as circles as large as their size. */
    private inner class SizeMap(context: Context) : View(context) {
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.rgb(58, 58, 62)
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(88, 88, 94)
            textAlign = Paint.Align.CENTER
        }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        private val box = RectF()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(width, (width * ROWS / COLUMNS).roundToInt())
        }

        override fun onDraw(canvas: Canvas) {
            val keyWidth = width / COLUMNS
            val rowHeight = height / ROWS
            label.textSize = rowHeight * .3f
            for (row in 0..2) for ((column, key) in Layout.rows[row].withIndex())
                key(canvas, column * keyWidth, row * rowHeight, keyWidth, rowHeight, key)
            var left = 0f
            for ((index, keyWidths) in Layout.bottomWidths.withIndex()) {
                key(canvas, left, 3 * rowHeight, keyWidths * keyWidth, rowHeight, Layout.rows[3][index])
                left += keyWidths * keyWidth
            }
            val shown = touches.takeLast(SHOWN_TOUCHES)
            for ((index, touch) in shown.withIndex()) {
                dot.color = touch.kind.colour
                dot.alpha = if (touch === current || index == shown.lastIndex) 220 else 70
                for (point in touch.points)
                    canvas.drawCircle(point.x * width, point.y * height, keyWidth * DOT_PER_SIZE * point.size.coerceIn(.5f, 12f), dot)
            }
        }

        private fun key(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, name: String) {
            box.set(x + 3, y + 3, x + width - 3, y + height - 3)
            canvas.drawRoundRect(box, 10f, 10f, outline)
            canvas.drawText(name, box.centerX(), box.centerY() + label.textSize / 3, label)
        }
    }

    private companion object {
        const val COLUMNS = 10f
        const val ROWS = 4f
        /** The sensor's own scale for touch major, from its input device description. */
        const val RAW_SIZE_MAX = 255f
        /** What Android reports as the largest touch major on the Titan 2 Elite, if the device gives no range. */
        const val DEFAULT_TOUCH_MAJOR_MAX = 1614.435f
        /** A circle's radius for each step of size, as a share of a key's width. */
        const val DOT_PER_SIZE = .06f
        const val SHOWN_TOUCHES = 12
        const val MAX_TOUCHES = 2000
        const val EXPORT = 2
        val MUTED = Color.rgb(142, 142, 147)
    }
}
