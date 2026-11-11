package com.atlas.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import org.json.JSONArray

/** The Haptics lab's colours: the app's dark page and cards, ours in orange, the phone's own effects in tan. */
private object LabColours {
    const val INK = 0xFF160A04.toInt()
    const val CARD = 0xFF21140C.toInt()
    const val TRACK = 0xFF3A2A20.toInt()
    const val ORANGE = 0xFFEF804E.toInt()
    const val PHONE = 0xFFC9A27E.toInt()
    const val CREAM = 0xFFFFF1E6.toInt()
    const val DIM = 0xFFA2958C.toInt()
}

/**
 * The Haptics lab's graph, in TitanKeys Debug only: sliders for the lengths of moving and a step, and when the motor is on for
 * each feel, each fast run and each haptic. A tap on a row plays it, and a playhead sweeps the row as the motor runs.
 */
class HapticsLabActivity : Activity() {
    private lateinit var lab: HapticsLab
    private lateinit var feels: HapticTimelineView
    private lateinit var runs: HapticTimelineView
    private lateinit var each: HapticTimelineView
    private val movingLabel by lazy { text("", 16f, LabColours.CREAM) }
    private val stepLabel by lazy { text("", 16f, LabColours.CREAM) }
    /** The haptics in Settings' order with Settings' names. The guide's celebration is longer than a row, so it isn't here. */
    private var listed: List<Pair<Haptic, String>> = emptyList()
    private val runList = listOf(
        Triple("Swipe, 10 in a row", "moving every ${HapticTimeline.RUN_MILLIS} ms", HapticTimeline.swipeRun),
        Triple("Steering, 10 in a row", "\"${HapticTimeline.STEERING_TEXT}\": letters move, spaces step", HapticTimeline.steeringRun),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only TitanKeys Debug has the lab. Every other build closes this screen before it shows anything.
        if (!HapticsLab.available(this)) {
            finish()
            return
        }
        lab = HapticsLab(this)
        listed = labels(intent.getStringExtra(LABELS))
        feels = HapticTimelineView(this, HapticTimeline.FEEL_SPAN_MILLIS, HapticTimeline.FEEL_GRID_MILLIS) { row ->
            playFeel(feels, row, HapticTimeline.sample(HapticTimeline.feels[row].first))
        }
        runs = HapticTimelineView(this, HapticTimeline.RUN_SPAN_MILLIS, HapticTimeline.RUN_GRID_MILLIS, ::playRun)
        each = HapticTimelineView(this, HapticTimeline.FEEL_SPAN_MILLIS, HapticTimeline.FEEL_GRID_MILLIS) { row ->
            playFeel(each, row, listed[row].first)
        }
        refresh()

        val padding = dp(16)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(text("Haptics graph", 24f, LabColours.CREAM, bold = true))
            addView(text("Drag a length. Tap a row to feel it.", 15f, LabColours.DIM))
            addView(card("Lengths",
                movingLabel, slider(Feel.Moving, Haptics.movingMillis(this@HapticsLabActivity)),
                stepLabel, slider(Feel.Step, Haptics.stepMillis(this@HapticsLabActivity))))
            addView(card("Feels", feels, key()))
            addView(card("Runs", text("A hollow tick is one the gate dropped.", 14f, LabColours.DIM), runs))
            addView(card("Each haptic", each))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(LabColours.INK)
            fitsSystemWindows = true
            addView(content)
        })
    }

    override fun onPause() {
        super.onPause()
        if (::lab.isInitialized) stop()
    }

    /** Plays [haptic] and sweeps [row] of [view] from the same call, hollow if the gate dropped it. */
    private fun playFeel(view: HapticTimelineView, row: Int, haptic: Haptic) {
        stop()
        val start = SystemClock.uptimeMillis()
        val played = lab.play(haptic)
        view.buzzes(row, HapticTimeline.buzzes(haptic.feel, moving(), step(), dropped = !played))
        view.sweep(row, start)
    }

    /** Plays a run and sweeps its strip. Each tick moves to when it really played, hollow if the gate dropped it. */
    private fun playRun(row: Int) {
        stop()
        val run = runList[row].third
        val moving = moving()
        val step = step()
        val ticks = HapticTimeline.ticks(run, moving, step).toMutableList()
        val start = SystemClock.uptimeMillis()
        runs.sweep(row, start)
        lab.playRun(run, start) { index, atMillis, admitted ->
            ticks[index] = HapticTimeline.tick(run[index].haptic, atMillis, moving, step, admitted)
            runs.buzzes(row, ticks.toList())
        }
        runs.buzzes(row, ticks.toList())
    }

    private fun stop() {
        lab.stop()
        for (view in listOf(feels, runs, each)) view.stopSweep()
    }

    private fun moving() = Haptics.movingMillis(this)
    private fun step() = Haptics.stepMillis(this)

    /** Draws every row again with the lengths in use now. */
    private fun refresh() {
        val moving = moving()
        val step = step()
        movingLabel.text = "Moving: $moving ms"
        stepLabel.text = "Step: $step ms"
        feels.rows(HapticTimeline.feels.map { (feel, words) ->
            HapticTimelineView.Row(feel.name, words, HapticTimeline.buzzes(feel, moving, step))
        })
        runs.rows(runList.map { (name, words, run) -> HapticTimelineView.Row(name, words, HapticTimeline.ticks(run, moving, step)) })
        each.rows(listed.map { (haptic, label) ->
            HapticTimelineView.Row(label, "${haptic.feel.name}: ${HapticPalette.describe(haptic, moving, step)}",
                HapticTimeline.buzzes(haptic.feel, moving, step))
        })
    }

    /** A slider from 4 to 40 ms that tries each length as it moves. */
    private fun slider(feel: Feel, millis: Long) = SeekBar(this).apply {
        min = HapticPalette.MOVING_TRY_MILLIS.first.toInt()
        max = HapticPalette.MOVING_TRY_MILLIS.last.toInt()
        progress = millis.toInt()
        progressTintList = ColorStateList.valueOf(LabColours.ORANGE)
        thumbTintList = ColorStateList.valueOf(LabColours.ORANGE)
        progressBackgroundTintList = ColorStateList.valueOf(LabColours.TRACK)
        setPadding(dp(12), dp(12), dp(12), dp(16))
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                lab.length(feel, value.toLong())
                refresh()
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
    }

    private fun key() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, 0)
        addView(keyLine("■", LabColours.ORANGE, "on (we set the length)"))
        addView(keyLine("■", LabColours.PHONE, "on (the phone's own effect, length fixed)"))
        addView(keyLine("■", LabColours.TRACK, "off"))
        addView(keyLine("□", LabColours.ORANGE, "dropped by the gate"))
    }

    private fun keyLine(mark: String, colour: Int, words: String) = TextView(this).apply {
        text = SpannableStringBuilder("$mark  $words").apply {
            setSpan(ForegroundColorSpan(colour), 0, mark.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        textSize = 13f
        setTextColor(LabColours.DIM)
    }

    private fun card(title: String, vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(LabColours.CARD)
            cornerRadius = dp(12).toFloat()
        }
        setPadding(dp(16), dp(12), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(16) }
        addView(text(title, 18f, LabColours.CREAM, bold = true).apply { setPadding(0, 0, 0, dp(8)) })
        for (view in views) addView(view)
    }

    private fun text(value: String, size: Float, colour: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(colour)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val LABELS = "labels"

        /** The graph, with [labels]: Settings' names for the haptics, as a JSON list of { id, label }. */
        fun intent(context: Context, labels: String) = Intent(context, HapticsLabActivity::class.java).putExtra(LABELS, labels)

        private fun labels(json: String?): List<Pair<Haptic, String>> {
            val list = JSONArray(json ?: "[]")
            return (0 until list.length()).mapNotNull { index ->
                val item = list.getJSONObject(index)
                val haptic = Haptic.of(item.getString("id")) ?: return@mapNotNull null
                if (haptic == Haptic.GuideCelebration) null else haptic to item.getString("label")
            }
        }
    }
}

/**
 * Rows of motor on/off bars, to scale over [spanMillis], with a gridline every [gridMillis]. A tap on a row calls [tapped].
 * It draws only when something changes, and on every frame only while a playhead sweeps.
 */
internal class HapticTimelineView(
    context: Context,
    private val spanMillis: Long,
    private val gridMillis: Long,
    private val tapped: (row: Int) -> Unit,
) : View(context) {
    data class Row(val name: String, val description: String, val buzzes: List<Buzz>)

    private var rows: List<Row> = emptyList()
    private var sweepRow = -1
    private var sweepStart = 0L
    private var flashRow = -1
    private var flashUntil = 0L

    private val density = resources.displayMetrics.density
    private val axisHeight = 18 * density
    private val rowHeight = 46 * density
    private val barTop = 24 * density
    private val barHeight = 12 * density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val grid = Paint().apply {
        color = LabColours.DIM
        alpha = 0x60
        strokeWidth = density
    }
    private val playhead = Paint().apply {
        color = LabColours.CREAM
        strokeWidth = 2 * density
    }
    private val nameText = text(14f, LabColours.CREAM).apply { typeface = Typeface.DEFAULT_BOLD }
    private val wordsText = text(12f, LabColours.DIM)
    private val axisText = text(10f, LabColours.DIM)

    fun rows(rows: List<Row>) {
        val resized = rows.size != this.rows.size
        this.rows = rows
        if (resized) requestLayout()
        invalidate()
    }

    fun buzzes(row: Int, buzzes: List<Buzz>) {
        rows = rows.toMutableList().also { it[row] = it[row].copy(buzzes = buzzes) }
        invalidate()
    }

    /**
     * Sweeps a playhead across [row] in real time, from [start], an uptime taken in the call that started the motor. With
     * Android's animations off, the row only flashes.
     */
    fun sweep(row: Int, start: Long) {
        if (Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) {
            flashRow = row
            flashUntil = start + FLASH_MILLIS
            postInvalidateDelayed(FLASH_MILLIS)
        } else {
            sweepRow = row
            sweepStart = start
        }
        invalidate()
    }

    fun stopSweep() {
        if (sweepRow < 0 && flashRow < 0) return
        sweepRow = -1
        flashRow = -1
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (axisHeight + rows.size * rowHeight).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        for (millis in 0..spanMillis step gridMillis) {
            axisText.textAlign = when (millis) {
                0L -> Paint.Align.LEFT
                spanMillis -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText("$millis ms", x(millis), 12 * density, axisText)
        }
        for ((index, row) in rows.withIndex()) {
            val top = axisHeight + index * rowHeight
            if (index == flashRow && now < flashUntil) {
                fill.color = LabColours.TRACK
                canvas.drawRect(0f, top, width.toFloat(), top + rowHeight - 4 * density, fill)
            }
            val name = row.name
            canvas.drawText(name, 0f, top + 15 * density, nameText)
            val wordsLeft = nameText.measureText(name) + 8 * density
            val words = TextUtils.ellipsize(row.description, wordsText, width - wordsLeft, TextUtils.TruncateAt.END)
            canvas.drawText(words, 0, words.length, wordsLeft, top + 15 * density, wordsText)

            val bar = top + barTop
            fill.color = LabColours.TRACK
            canvas.drawRect(0f, bar, width.toFloat(), bar + barHeight, fill)
            for (millis in gridMillis until spanMillis step gridMillis)
                canvas.drawLine(x(millis), bar - 2 * density, x(millis), bar + barHeight + 2 * density, grid)
            for (buzz in row.buzzes) {
                val left = x(buzz.fromMillis)
                // A 4 ms buzz is still wide enough to see.
                val right = maxOf(x(buzz.toMillis), left + 2 * density)
                val colour = if (buzz.ours) LabColours.ORANGE else LabColours.PHONE
                if (buzz.dropped) {
                    outline.color = colour
                    val inset = outline.strokeWidth / 2
                    canvas.drawRect(left + inset, bar + inset, right - inset, bar + barHeight - inset, outline)
                } else {
                    fill.color = colour
                    canvas.drawRect(left, bar, right, bar + barHeight, fill)
                }
            }
            if (index == sweepRow) {
                val elapsed = now - sweepStart
                if (elapsed > spanMillis) {
                    sweepRow = -1
                } else {
                    val x = x(elapsed)
                    canvas.drawLine(x, bar - 5 * density, x, bar + barHeight + 5 * density, playhead)
                    postInvalidateOnAnimation()
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val row = ((event.y - axisHeight) / rowHeight).toInt()
            if (event.y >= axisHeight && row in rows.indices) {
                performClick()
                tapped(row)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun x(millis: Long) = millis.coerceIn(0, spanMillis).toFloat() / spanMillis * width

    private fun text(size: Float, colour: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, resources.displayMetrics)
        color = colour
    }

    companion object {
        /** With animations off, a played row lights up this long instead. */
        const val FLASH_MILLIS = 150L
    }
}
