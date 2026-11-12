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
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import org.json.JSONArray
import java.util.Locale

/** The Haptics lab's colours: the app's dark page and cards, the motor in orange. */
private object LabColours {
    const val INK = 0xFF160A04.toInt()
    const val CARD = 0xFF21140C.toInt()
    const val TRACK = 0xFF3A2A20.toInt()
    const val ORANGE = 0xFFEF804E.toInt()
    const val CREAM = 0xFFFFF1E6.toInt()
    const val DIM = 0xFFA2958C.toInt()
    /** A delete's dots landing, so they stand apart from the words' ticks. */
    const val DOT = 0xFF7FD1C8.toInt()
}

/**
 * The Haptics lab's screen, in TitanKeys Debug only. Haptics go with what you do too closely to judge one alone, so you type,
 * swipe and steer in its practice field, and it shows each haptic the keyboard really plays, live: on a timeline of the last
 * 5 s, and in a list of the last 15. A haptic in the list opens its family's editor, whose patterns the next real action plays.
 * The notes and the patterns in use go in a file that adb can pull.
 */
class HapticsLabActivity : Activity() {
    private lateinit var lab: HapticsLab
    private lateinit var timeline: LiveTimeline
    private lateinit var list: LinearLayout
    private lateinit var editor: LinearLayout
    private val events = ArrayList<HapticEvent>()
    private val opened = SystemClock.uptimeMillis()
    /** Settings' names for the haptics, by haptic. */
    private var names: Map<Haptic, HapticsLab.Named> = emptyMap()
    /** The editor's family, its bars and sliders, to draw again after a change. */
    private var editing: Editing? = null

    private class Editing(val family: Family, val bars: PatternBars, val plays: TextView, val sliders: List<Pair<TextView, SeekBar>>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only TitanKeys Debug has the lab. Every other build closes this screen before it shows anything.
        if (!HapticsLab.available(this)) {
            finish()
            return
        }
        lab = HapticsLab(this)
        lab.names = labels(intent.getStringExtra(LABELS))
        names = lab.names.associateBy { it.haptic }
        timeline = LiveTimeline(this, ::name)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        editor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val practice = field("Type, swipe, steer, open panels and menus here.", 3)
        val padding = dp(16)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(text("Haptics lab", 24f, LabColours.CREAM, bold = true))
            addView(text("Each haptic the keyboard plays shows below as it plays. Tap one in the list to change its family.", 15f, LabColours.DIM))
            addView(practice)
            addView(card("Last 5 seconds", timeline, key()))
            addView(card("Last ${HapticTimeline.RECENT}", list))
            addView(card("Patterns", editor))
            addView(card("Overall", field("How do they feel together?", 2, lab.general()) { lab.general(it) }))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(LabColours.INK)
            fitsSystemWindows = true
            addView(content)
        })
        showFamilies()
        practice.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (!::lab.isInitialized) return
        events.clear()
        events += HapticFeed.recent()
        shown()
        // The keyboard plays on the main thread, but a post keeps the screen's changes in order whatever thread it came from.
        HapticFeed.listen { event -> timeline.post { heard(event) } }
    }

    override fun onPause() {
        super.onPause()
        if (!::lab.isInitialized) return
        HapticFeed.stop()
        lab.stop()
    }

    private fun heard(event: HapticEvent) {
        events += event
        while (events.size > HapticFeed.KEEP) events.removeAt(0)
        shown()
    }

    /** Draws the timeline and the list again from [events]. */
    private fun shown() {
        timeline.events(events.toList())
        list.removeAllViews()
        val recent = events.takeLast(HapticTimeline.RECENT).asReversed()
        if (recent.isEmpty()) list.addView(text("Nothing yet. Swipe a word in the field above.", 14f, LabColours.DIM))
        for (event in recent) list.addView(row(event))
    }

    private fun name(haptic: Haptic) = names[haptic]?.label ?: haptic.id

    /** One haptic in the list: when, what, its family and pattern, and whether it played. A tap opens its family's editor. */
    private fun row(event: HapticEvent) = TextView(this).apply {
        val seconds = String.format(Locale.ROOT, "%.2f s", (event.atMillis - opened) / 1000f)
        val outcome = when (event.result) {
            HapticResult.Played -> "played"
            HapticResult.NearWord -> "skipped: ${event.result.words}"
            else -> "dropped: ${event.result.words}"
        }
        val first = "$seconds  ${HapticTimeline.title(event, name(event.haptic))}"
        text = SpannableStringBuilder("$first\n${HapticPalette.describe(event.pattern)} · $outcome").apply {
            setSpan(ForegroundColorSpan(LabColours.CREAM), 0, first.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (!event.result.played) setSpan(ForegroundColorSpan(LabColours.ORANGE), length - outcome.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        textSize = 14f
        setTextColor(LabColours.DIM)
        minHeight = dp(48)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        isClickable = true
        isFocusable = true
        setOnClickListener { edit(event.haptic.family, event.haptic) }
    }

    /** The editor with nothing picked: each family, to open its editor without waiting for it to play. */
    private fun showFamilies() {
        editing = null
        editor.removeAllViews()
        editor.addView(text("Tap a haptic in the list, or a family here.", 14f, LabColours.DIM).apply { setPadding(0, 0, 0, dp(4)) })
        for (family in Family.entries) {
            val pattern = lab.pattern(family)
            editor.addView(TextView(this).apply {
                text = "${family.label}: ${HapticPalette.describe(pattern)}" + if (pattern != family.pattern) "  (tried)" else ""
                textSize = 15f
                setTextColor(LabColours.CREAM)
                minHeight = dp(48)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setOnClickListener { edit(family, null) }
            })
        }
    }

    /** [family]'s editor: its bars, a slider for each pulse and gap, Play and Reset, and notes on it and on [haptic]. */
    private fun edit(family: Family, haptic: Haptic?) {
        editor.removeAllViews()
        val bars = PatternBars(this)
        val plays = text("", 14f, LabColours.DIM)
        val sliders = family.pattern.indices.map { index ->
            val range = if (index % 2 == 0) HapticPalette.PULSE_MILLIS else HapticPalette.gapRange(family)
            text("", 14f, LabColours.CREAM) to slider(range) { millis -> tryMillis(family, index, millis) }
        }
        editing = Editing(family, bars, plays, sliders)
        editor.addView(text(family.label, 18f, LabColours.CREAM, bold = true))
        editor.addView(text(HapticTimeline.meaning(family), 14f, LabColours.DIM))
        editor.addView(plays)
        editor.addView(bars)
        for ((label, bar) in sliders) {
            editor.addView(label)
            editor.addView(bar)
        }
        editor.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("Play", "Play ${family.label} alone") { lab.preview(family) })
            addView(button("Reset", "Reset ${family.label}") {
                lab.tryPattern(family, null)
                drawEditor()
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)).apply { marginStart = dp(12) })
            addView(button("All families", "Show all families") { showFamilies() },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)).apply { marginStart = dp(12) })
        })
        // Hit is a held delete's: it can play a whole hold too, its flick, its words and its dots.
        if (family == Family.Hit) {
            editor.addView(text("A hold's words play it shorter as the hold speeds up, down to " +
                "${HapticPalette.MESSAGE_PULSE_MILLIS} ms at the top pace. Every word plays.", 13f, LabColours.DIM)
                .apply { setPadding(0, dp(8), 0, 0) })
            editor.addView(button("Play a hold", "Play a held delete of ${HoldTicks.PLAY_WORDS} words and its dots") { lab.playHold() },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)).apply { topMargin = dp(8) })
            editor.addView(text("It holds for the flick and ${HoldTicks.PLAY_WORDS} words, $REPEAT_SLOW_MILLIS ms apart, as " +
                "a hold starts. Then it lifts and the dots land. ${HapticTimeline.planWords(lab.holdPlan())}", 13f, LabColours.DIM)
                .apply { setPadding(0, dp(8), 0, 0) })
        }
        editor.addView(text("Notes on ${family.label}", 14f, LabColours.CREAM).apply { setPadding(0, dp(12), 0, 0) })
        editor.addView(field("How does ${family.label} feel?", 1, lab.note(family)) { lab.note(family, it) })
        if (haptic != null) {
            editor.addView(text("Notes on ${name(haptic)}", 14f, LabColours.CREAM).apply { setPadding(0, dp(12), 0, 0) })
            editor.addView(text(names[haptic]?.about ?: HapticTimeline.trigger(haptic), 13f, LabColours.DIM))
            editor.addView(field("How does it feel here?", 1, lab.note(haptic)) { lab.note(haptic, it) })
        }
        drawEditor()
    }

    /** Sets one pulse or gap of [family]'s pattern to [millis], and tries it. The next real action plays it. */
    private fun tryMillis(family: Family, index: Int, millis: Long) {
        val pattern = lab.pattern(family).toMutableList()
        pattern[index] = millis
        lab.tryPattern(family, pattern)
        drawEditor()
    }

    /** Draws the editor again with the pattern in use now. */
    private fun drawEditor() {
        val editing = editing ?: return
        val pattern = lab.pattern(editing.family)
        val tried = pattern != editing.family.pattern
        editing.plays.text = HapticPalette.describe(pattern) + if (tried) "  (tried)" else ""
        val span = HapticTimeline.span(HapticPalette.millis(pattern))
        editing.bars.show(span, HapticTimeline.grid(span), HapticTimeline.buzzes(pattern))
        for ((index, slider) in editing.sliders.withIndex()) {
            val (label, bar) = slider
            val kind = if (index % 2 == 0) "Pulse ${index / 2 + 1}" else "Gap ${index / 2 + 1}"
            label.text = "$kind: ${pattern[index]} ms"
            if (bar.progress != pattern[index].toInt()) bar.progress = pattern[index].toInt()
        }
    }

    /** A slider over [range] that tries each value as it moves. */
    private fun slider(range: LongRange, changed: (Long) -> Unit) = SeekBar(this).apply {
        min = range.first.toInt()
        max = range.last.toInt()
        progressTintList = ColorStateList.valueOf(LabColours.ORANGE)
        thumbTintList = ColorStateList.valueOf(LabColours.ORANGE)
        progressBackgroundTintList = ColorStateList.valueOf(LabColours.TRACK)
        setPadding(dp(12), dp(12), dp(12), dp(16))
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) changed(value.toLong())
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
    }

    /** A small pill button, still 48 dp tall so it's easy to press. */
    private fun button(label: String, description: String, pressed: () -> Unit) = TextView(this).apply {
        text = label
        contentDescription = description
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(LabColours.INK)
        minHeight = dp(48)
        setPadding(dp(20), 0, dp(20), 0)
        background = GradientDrawable().apply {
            setColor(LabColours.ORANGE)
            cornerRadius = dp(24).toFloat()
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { pressed() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48))
    }

    /** A text box. [changed] hears each change; with none it is the practice field, whose text goes nowhere. */
    private fun field(hint: String, lines: Int, value: String = "", changed: ((String) -> Unit)? = null) = EditText(this).apply {
        setText(value)
        this.hint = hint
        textSize = 16f
        setTextColor(LabColours.CREAM)
        setHintTextColor(LabColours.DIM)
        minLines = lines
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        background = GradientDrawable().apply {
            setColor(LabColours.TRACK)
            cornerRadius = dp(8).toFloat()
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(12) }
        if (changed != null) addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(text: Editable?) = changed(text?.toString().orEmpty())
        })
    }

    private fun key() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, 0)
        addView(keyLine("■", LabColours.ORANGE, "on"))
        addView(keyLine("□", LabColours.ORANGE, "dropped, with the reason"))
        addView(keyLine("●", LabColours.DOT, "a delete's dot landing, numbered for a hold"))
        addView(keyLine("○", LabColours.DOT, "a hold's dot skipped near a word's tick"))
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

        /** The lab, with [labels]: Settings' names for the haptics, as a JSON list of { id, label, group, about }. */
        fun intent(context: Context, labels: String) = Intent(context, HapticsLabActivity::class.java).putExtra(LABELS, labels)

        private fun labels(json: String?): List<HapticsLab.Named> {
            val list = JSONArray(json ?: "[]")
            return (0 until list.length()).mapNotNull { index ->
                val item = list.getJSONObject(index)
                val haptic = Haptic.of(item.getString("id")) ?: return@mapNotNull null
                HapticsLab.Named(haptic, item.getString("label"), item.optString("group"), item.optString("about"))
            }
        }
    }
}

/**
 * The last 5 s of haptics, newest on the right, moving left as time goes. Each is its pulses to scale, hollow if it was
 * dropped, with its name and family above it, and what set it off, or why it dropped. It draws on every frame only while a
 * haptic is still in view.
 */
internal class LiveTimeline(context: Context, private val name: (Haptic) -> String) : View(context) {
    private var events: List<HapticEvent> = emptyList()
    private val density = resources.displayMetrics.density
    private val laneHeight = 30 * density
    /** A row for the dots' numbers, under the lanes of names. */
    private val dotRow = LANES * laneHeight + 4 * density
    private val barTop = dotRow + DOT_LABEL_ROWS * 10 * density + 6 * density
    private val barHeight = 18 * density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val line = Paint().apply {
        color = LabColours.DIM
        alpha = 0x80
        strokeWidth = density
    }
    private val nameText = text(11f, LabColours.CREAM).apply { typeface = Typeface.DEFAULT_BOLD }
    private val wordsText = text(11f, LabColours.DIM)
    private val droppedText = text(11f, LabColours.ORANGE)
    private val axisText = text(10f, LabColours.DIM)
    private val dotText = text(9f, LabColours.DOT).apply { textAlign = Paint.Align.CENTER }
    private val skippedDotText = text(9f, LabColours.ORANGE).apply { textAlign = Paint.Align.CENTER }

    fun events(events: List<HapticEvent>) {
        this.events = events
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (barTop + barHeight + 22 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        fill.color = LabColours.TRACK
        canvas.drawRect(0f, barTop, width.toFloat(), barTop + barHeight, fill)
        for (second in 0..HapticTimeline.LIVE_MILLIS / 1000) {
            val x = x(now - second * 1000, now)
            canvas.drawLine(x, barTop - 2 * density, x, barTop + barHeight + 2 * density, line)
            axisText.textAlign = if (second == 0L) Paint.Align.RIGHT else Paint.Align.CENTER
            canvas.drawText(if (second == 0L) "now" else "-$second s", x.coerceAtLeast(12 * density), barTop + barHeight + 16 * density, axisText)
        }
        val shown = events.filter { now - it.atMillis <= HapticTimeline.LIVE_MILLIS }
        var lanes = 0
        var dotRows = 0
        for (event in shown) {
            val dropped = !event.result.played
            if (HapticTimeline.isDot(event.haptic)) {
                drawDot(canvas, event, dropped, now, dotRows++)
                continue
            }
            for (buzz in HapticTimeline.buzzes(event.pattern, event.atMillis, dropped)) {
                val left = x(buzz.fromMillis, now)
                // A 17 ms pulse is about a pixel at this scale, so every pulse gets at least 2 dp.
                val right = maxOf(x(buzz.toMillis, now), left + 2 * density)
                if (dropped) {
                    outline.color = LabColours.ORANGE
                    val inset = outline.strokeWidth / 2
                    canvas.drawRect(left + inset, barTop + inset, right - inset, barTop + barHeight - inset, outline)
                } else {
                    fill.color = LabColours.ORANGE
                    canvas.drawRect(left, barTop, right, barTop + barHeight, fill)
                }
            }
            // Labels take turns over three lanes, so ones close together don't sit on each other.
            val lane = lanes++ % LANES
            val top = lane * laneHeight
            val at = x(event.atMillis, now)
            val title = HapticTimeline.title(event, name(event.haptic))
            val words = HapticTimeline.outcome(event)
            val wordsPaint = if (dropped) droppedText else wordsText
            val wide = maxOf(nameText.measureText(title), wordsPaint.measureText(words))
            val left = if (at + wide > width) maxOf(0f, at - wide) else at
            canvas.drawLine(at, top + laneHeight - 4 * density, at, barTop, line)
            canvas.drawText(title, left, top + 12 * density, nameText)
            canvas.drawText(words, left, top + 25 * density, wordsPaint)
        }
        if (shown.isNotEmpty()) postInvalidateOnAnimation()
    }

    /**
     * A dot landing: a round mark in the dots' colour on the bar, hollow if it was skipped or dropped. A hold's dot has its number
     * in the row above, taking turns over two rows, and says if it was skipped. A flick's dots have no number.
     */
    private fun drawDot(canvas: Canvas, event: HapticEvent, dropped: Boolean, now: Long, row: Int) {
        val at = x(event.atMillis, now)
        val radius = 4 * density
        val middle = barTop + barHeight / 2
        if (dropped) {
            outline.color = LabColours.DOT
            canvas.drawCircle(at, middle, radius - outline.strokeWidth / 2, outline)
        } else {
            fill.color = LabColours.DOT
            canvas.drawCircle(at, middle, radius, fill)
        }
        val number = event.dot ?: return
        val top = dotRow + (row % DOT_LABEL_ROWS + 1) * 10 * density
        canvas.drawText(HapticTimeline.dotLabel(number, event.result), at, top, if (dropped) skippedDotText else dotText)
    }

    private fun x(millis: Long, now: Long) = width - (now - millis).toFloat() / HapticTimeline.LIVE_MILLIS * width

    private fun text(size: Float, colour: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, resources.displayMetrics)
        color = colour
    }

    companion object {
        const val LANES = 3
        const val DOT_LABEL_ROWS = 2
    }
}

/** One pattern's motor on and off bars, to scale, with a gridline every so often and the ms above them. */
internal class PatternBars(context: Context) : View(context) {
    private var spanMillis = HapticTimeline.SPAN_MILLIS
    private var gridMillis = HapticTimeline.GRID_MILLIS
    private var buzzes: List<Buzz> = emptyList()

    private val density = resources.displayMetrics.density
    private val barTop = 22 * density
    private val barHeight = 18 * density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint().apply {
        color = LabColours.DIM
        alpha = 0x60
        strokeWidth = density
    }
    private val axisText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        color = LabColours.DIM
    }

    /** Shows [buzzes] over [spanMillis], with a gridline every [gridMillis]. */
    fun show(spanMillis: Long, gridMillis: Long, buzzes: List<Buzz>) {
        this.spanMillis = spanMillis
        this.gridMillis = gridMillis
        this.buzzes = buzzes
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (barTop + barHeight + 8 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        for (millis in 0..spanMillis step gridMillis) {
            axisText.textAlign = when (millis) {
                0L -> Paint.Align.LEFT
                spanMillis -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText("$millis", x(millis), 14 * density, axisText)
        }
        fill.color = LabColours.TRACK
        canvas.drawRect(0f, barTop, width.toFloat(), barTop + barHeight, fill)
        for (millis in gridMillis until spanMillis step gridMillis)
            canvas.drawLine(x(millis), barTop - 2 * density, x(millis), barTop + barHeight + 2 * density, grid)
        fill.color = LabColours.ORANGE
        for (buzz in buzzes) {
            val left = x(buzz.fromMillis)
            canvas.drawRect(left, barTop, maxOf(x(buzz.toMillis), left + 2 * density), barTop + barHeight, fill)
        }
    }

    private fun x(millis: Long) = millis.coerceIn(0, spanMillis).toFloat() / spanMillis * width
}
