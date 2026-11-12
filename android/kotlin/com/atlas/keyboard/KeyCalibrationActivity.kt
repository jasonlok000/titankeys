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
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale
import kotlin.math.min

/** Key calibration's colours: the Haptics lab's dark page, with the keys in cream and their centres in orange. */
private object CalibrationColours {
    const val INK = 0xFF160A04.toInt()
    const val CARD = 0xFF21140C.toInt()
    const val TRACK = 0xFF3A2A20.toInt()
    const val ORANGE = 0xFFEF804E.toInt()
    const val CREAM = 0xFFFFF1E6.toInt()
    const val DIM = 0xFFA2958C.toInt()
    /** A key where calibration puts it, and faint, where it was before. */
    const val KEY = 0x33FFF1E6
    const val OUTLINE = 0x66A2958C
}

/**
 * Key calibration, in TitanKeys Debug only. The real keys sit just below the screen, and the guide, the Sym panel and the trail
 * draw them at the bottom of it. This screen draws them there too, and its sliders move the drawing until each key sits right
 * over its real key. Every change is saved at once ([KeyCalibration]), and every drawing of the keys follows it.
 */
class KeyCalibrationActivity : Activity() {
    private lateinit var grid: KeyGrid
    private var alignment = KeyAlignment.DEFAULT
    private val sliders = ArrayList<Pair<SeekBar, (KeyAlignment) -> Int>>()
    private val values = ArrayList<Pair<TextView, (KeyAlignment) -> String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only TitanKeys Debug has it. Every other build closes this screen before it shows anything.
        if (!HapticsLab.available(this)) {
            finish()
            return
        }
        alignment = KeyCalibration.load(this)
        grid = KeyGrid(this)
        val padding = dp(16)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(text("Key calibration", 24f, CalibrationColours.CREAM, bold = true))
            addView(text("Move the sliders until each drawn key sits right over its real key below the screen. It saves as you go.",
                15f, CalibrationColours.DIM))
            addView(card(
                slider("Width", KeyAlignment.SCALE_STEPS, { KeyAlignment.scaleProgress(it.scale) },
                    { String.format(Locale.US, "%.3f×", it.scale) }) { a, p -> KeyAlignment(KeyAlignment.scaleAt(p), a.offset, a.rowOffsets) },
                slider("Shift", KeyAlignment.OFFSET_STEPS, { KeyAlignment.offsetProgress(it.offset) }, { keys(it.offset) }) { a, p ->
                    KeyAlignment(a.scale, KeyAlignment.offsetAt(p), a.rowOffsets)
                },
            ))
            addView(card(*ROW_NAMES.indices.map { row ->
                slider(ROW_NAMES[row], KeyAlignment.OFFSET_STEPS, { KeyAlignment.offsetProgress(it.rowOffsets[row]) },
                    { keys(it.rowOffsets[row]) }) { a, p ->
                    KeyAlignment(a.scale, a.offset, a.rowOffsets.copyOf().also { it[row] = KeyAlignment.offsetAt(p) })
                }
            }.toTypedArray()))
            addView(button("Reset") {
                KeyCalibration.reset(this@KeyCalibrationActivity)
                show(KeyAlignment.DEFAULT)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CalibrationColours.INK)
            fitsSystemWindows = true
            addView(ScrollView(this@KeyCalibrationActivity).apply { addView(content) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(KeyAlignment.ROWS * ROW_HEIGHT)))
        })
        show(alignment)
    }

    /** Shows [next] on every slider, value and the grid. */
    private fun show(next: KeyAlignment) {
        alignment = next
        for ((bar, progress) in sliders) progress(next).let { if (bar.progress != it) bar.progress = it }
        for ((label, value) in values) label.text = value(next)
        grid.alignment = next
    }

    /** A slider called [name], with its value live beside the name. Each move saves, and the grid draws again. */
    private fun slider(name: String, steps: Int, progress: (KeyAlignment) -> Int, value: (KeyAlignment) -> String,
                       changed: (KeyAlignment, Int) -> KeyAlignment): View {
        val shown = text("", 15f, CalibrationColours.ORANGE, bold = true).apply { gravity = Gravity.END }
        val bar = SeekBar(this).apply {
            max = steps
            contentDescription = name
            progressTintList = ColorStateList.valueOf(CalibrationColours.ORANGE)
            thumbTintList = ColorStateList.valueOf(CalibrationColours.ORANGE)
            progressBackgroundTintList = ColorStateList.valueOf(CalibrationColours.TRACK)
            setPadding(dp(12), dp(12), dp(12), dp(16))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val next = changed(alignment, value)
                    KeyCalibration.save(this@KeyCalibrationActivity, next)
                    show(next)
                }

                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        sliders += bar to progress
        values += shown to value
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@KeyCalibrationActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(text(name, 15f, CalibrationColours.CREAM), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(shown)
            })
            addView(bar)
        }
    }

    private fun keys(value: Float) = String.format(Locale.US, "%+.2f key", value)

    private fun button(label: String, pressed: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(CalibrationColours.INK)
        minHeight = dp(48)
        setPadding(dp(20), 0, dp(20), 0)
        background = GradientDrawable().apply {
            setColor(CalibrationColours.ORANGE)
            cornerRadius = dp(24).toFloat()
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { pressed() }
    }

    private fun card(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(CalibrationColours.CARD)
            cornerRadius = dp(12).toFloat()
        }
        setPadding(dp(16), dp(12), dp(16), dp(8))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(16) }
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
        /** The height of a row of the grid, in dp. */
        private const val ROW_HEIGHT = 44
        private val ROW_NAMES = listOf("Top row", "Middle row", "Bottom letter row", "Bottom row")

        fun intent(context: Context) = Intent(context, KeyCalibrationActivity::class.java)
    }
}

/**
 * The keys, drawn where the guide and the Sym panel draw them: the ten columns [Layout.KEY_SIDE_DP] in from each side of the
 * screen. Each key shows faint where it was, and solid where [alignment] puts it, with an orange line down its centre to hold
 * against the real key.
 */
internal class KeyGrid(context: Context) : View(context) {
    var alignment = KeyAlignment.DEFAULT
        set(value) {
            field = value
            invalidate()
        }
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, 800, false)
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        val side = Layout.KEY_SIDE_DP * density
        val across = width - 2 * side
        val rowHeight = height / KeyAlignment.ROWS.toFloat()
        val gap = 2 * density
        val radius = 6 * density
        outline.color = CalibrationColours.OUTLINE
        fill.color = CalibrationColours.KEY
        for (key in KeyAlignment.keys) {
            val top = key.row * rowHeight + gap
            val bottom = (key.row + 1) * rowHeight - gap
            // Where the key was before calibration.
            canvas.drawRoundRect(side + key.left * across + gap, top, side + key.right * across - gap, bottom, radius, radius, outline)
            val span = alignment.span(key.left, key.right, key.row)
            val left = side + span.left * across
            val right = side + span.right * across
            canvas.drawRoundRect(left + gap, top, right - gap, bottom, radius, radius, fill)
            val centre = side + span.centre * across
            fill.color = CalibrationColours.ORANGE
            canvas.drawRect(centre - density / 2, top, centre + density / 2, top + (bottom - top) * .22f, fill)
            canvas.drawRect(centre - density / 2, bottom - (bottom - top) * .22f, centre + density / 2, bottom, fill)
            fill.color = CalibrationColours.KEY
            // A key squeezed at the edge keeps its centre, and its label shrinks with it.
            label.color = CalibrationColours.CREAM
            label.textSize = min(rowHeight, (key.right - key.left) * across) * LABEL * span.fit
            canvas.drawText(key.name, centre, (top + bottom) / 2 - (label.ascent() + label.descent()) / 2, label)
        }
    }

    private companion object {
        const val LABEL = .42f
    }
}
