package com.atlas.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Key calibration's colours, the app's (v5.3): pure black, the keys in cream and the two edges in orange. The page is black where
 * it was ink, and its cards and tracks are the divider, never brown.
 */
private object CalibrationColours {
    const val INK = GuideColours.BLACK
    const val CARD = 0xFF141312.toInt()
    const val TRACK = GuideColours.DIVIDER
    const val ORANGE = GuideColours.ORANGE
    const val CREAM = GuideColours.CREAM
    const val DIM = GuideColours.MUTED
    /** A gap between two keys: faint orange, so the gaps read as one line of stripes against the real keys' gaps. */
    const val GAP = 0x40EF804E
    const val LABEL = 0x80FFF1E6.toInt()
    /** The screen outside the keyboard's two edges, a little dimmer, so the edges show. */
    const val OUTSIDE = 0x33EF804E
}

/**
 * Key calibration, in TitanKeys Debug only. The real keys sit just below the screen, and the guide, the Sym panel, the word menu
 * and the swipe's path line up with them. This screen draws only the gaps between the key columns, as one-pixel orange lines at
 * each gap's centre that rise from the screen's bottom edge, to set in the middle of the real dark gaps between the keys below
 * (owner, 2026-11-12: the two edges, then wide bands, were too hard to judge). Two sliders set the spacing from one line to the next
 * (a key and a gap, every key the same width) and the shift of the whole row; each has − and + for half a pixel. Every change is saved at once ([KeyCalibration]), and every
 * drawing follows it ([KeyGeometry]).
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
        // The drawing goes right down to the screen's bottom edge, under the navigation bar, so it meets the real keys.
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        alignment = KeyCalibration.load(this)
        grid = KeyGrid(this)
        val padding = dp(16)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(text("Key calibration", 24f, CalibrationColours.CREAM, bold = true))
            addView(text("Put each thin orange line at the bottom in the middle of a dark gap between the real keys below. Set the " +
                "spacing first, then shift the row. Every key is the same width. It saves as you go.", 15f,
                CalibrationColours.DIM))
            val wide = resources.displayMetrics.widthPixels.toFloat()
            val measured = KeyAlignment.DEFAULT
            addView(card(
                // The lines sit at the gaps' centres, so only the spacing from one line to the next shows: a key and a gap together.
                // The gap keeps its measured width, and the key takes the rest.
                slider("Spacing", (measured.key + measured.gap) * wide, KeyAlignment.KEY_REACH, { (it.key + it.gap) * wide }) { a, px ->
                    KeyAlignment(a.left, px / wide - a.gap, a.gap)
                },
                slider("Shift", measured.left * wide, KeyAlignment.SHIFT_REACH, { it.left * wide }) { a, px ->
                    KeyAlignment(px / wide, a.key, a.gap)
                },
            ))
            addView(button("Reset to measured") {
                KeyCalibration.reset(this@KeyCalibrationActivity)
                show(KeyAlignment.DEFAULT)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CalibrationColours.INK)
            addView(ScrollView(this@KeyCalibrationActivity).apply { addView(content) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        // Only the status bar and the cutout push the text down. Nothing pushes the drawing up from the bottom edge.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(0, bars.top, 0, 0)
            insets
        }
        setContentView(root)
        show(alignment)
    }

    /** Shows [next] on every slider, value and the grid. */
    private fun show(next: KeyAlignment) {
        alignment = next
        for ((bar, progress) in sliders) progress(next).let { if (bar.progress != it) bar.progress = it }
        for ((label, value) in values) label.text = value(next)
        grid.alignment = next
    }

    /**
     * A slider called [name] for a value in pixels, [measured] ± [reach], with its value live beside the name, and − and + that
     * step it by half a pixel. Each change saves, and the bands draw again.
     */
    private fun slider(name: String, measured: Float, reach: Float, pixels: (KeyAlignment) -> Float,
                       changed: (KeyAlignment, Float) -> KeyAlignment): View {
        val shown = text("", 15f, CalibrationColours.ORANGE, bold = true).apply { gravity = Gravity.END }
        fun set(px: Float) {
            val next = changed(alignment, px.coerceIn(measured - reach, measured + reach))
            KeyCalibration.save(this@KeyCalibrationActivity, next)
            show(next)
        }
        val bar = SeekBar(this).apply {
            max = KeyAlignment.steps(reach)
            contentDescription = name
            progressTintList = ColorStateList.valueOf(CalibrationColours.ORANGE)
            thumbTintList = ColorStateList.valueOf(CalibrationColours.ORANGE)
            progressBackgroundTintList = ColorStateList.valueOf(CalibrationColours.TRACK)
            setPadding(dp(12), dp(12), dp(12), dp(16))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    if (fromUser) set(KeyAlignment.pixelsAt(measured, reach, value))
                }

                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        sliders += bar to { a -> KeyAlignment.progressOf(measured, reach, pixels(a)) }
        values += shown to { a -> KeyAlignment.pixelText(pixels(a), measured) }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@KeyCalibrationActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(text(name, 15f, CalibrationColours.CREAM), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(shown)
            })
            addView(LinearLayout(this@KeyCalibrationActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                // Half a pixel at a time, for tuning by eye.
                addView(step("−", "$name, half a pixel less") { set(pixels(alignment) - KeyAlignment.STEP) })
                addView(bar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(step("+", "$name, half a pixel more") { set(pixels(alignment) + KeyAlignment.STEP) })
            })
        }
    }

    /** A round − or + button, finger-sized. */
    private fun step(label: String, description: String, pressed: () -> Unit) = TextView(this).apply {
        text = label
        contentDescription = description
        textSize = 20f
        typeface = GuideFonts.heavy(context)
        gravity = Gravity.CENTER
        setTextColor(CalibrationColours.ORANGE)
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1).coerceAtLeast(2), CalibrationColours.ORANGE)
            cornerRadius = dp(22).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        isClickable = true
        isFocusable = true
        setOnClickListener { pressed() }
    }

    private fun button(label: String, pressed: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 15f
        typeface = GuideFonts.heavy(context)
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
        typeface = if (bold) GuideFonts.heavy(context) else GuideFonts.regular(context)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun intent(context: Context) = Intent(context, KeyCalibrationActivity::class.java)
    }
}

/**
 * The gaps between the ten key columns of [KeyGeometry], and the keyboard's two outer edges, as one-pixel orange lines that rise a short
 * way from the screen's bottom edge, so each reads as a real gap between the keys below carrying on up the screen (owner,
 * 2026-11-12). No key is drawn whole: the boxes were what made it hard to judge. Each column's letter sits faintly between its
 * bands, for the top row.
 */
internal class KeyGrid(context: Context) : View(context) {
    var alignment = KeyAlignment.DEFAULT
        set(value) {
            field = value
            invalidate()
        }
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    /** The one-pixel lines, drawn without smoothing so each stays exactly one pixel wide. */
    private val line = Paint()
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = GuideFonts.medium(context)
        textAlign = Paint.Align.CENTER
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (BANDS * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val keys = KeyGeometry(width.toFloat(), alignment)
        // The screen outside the keys, dimmed a little.
        fill.color = CalibrationColours.OUTSIDE
        canvas.drawRect(0f, 0f, keys.left, height.toFloat(), fill)
        canvas.drawRect(keys.right, 0f, width.toFloat(), height.toFloat(), fill)
        // Each gap is one line, one pixel wide, at the gap's centre (owner, 2026-11-12), so it lines up with the middle of the dark
        // gap between the real keys below. Lines snap to whole pixels, so none blurs across two.
        line.color = CalibrationColours.ORANGE
        for (column in 0 until KeyGeometry.COLUMNS - 1) {
            pixelLine(canvas, keys.keyLeft(column) + keys.key + keys.gap / 2)
        }
        // The outer edges, Q's left and P's right, one pixel each too.
        pixelLine(canvas, keys.left)
        pixelLine(canvas, keys.right)
        // The letters, faint, between their bands.
        label.color = CalibrationColours.LABEL
        label.textSize = LABEL * density
        val names = Layout.rows[0]
        for (column in 0 until KeyGeometry.COLUMNS) {
            val centre = keys.keyLeft(column) + keys.key / 2
            canvas.drawText(names[column], centre, LABEL_TOP * density - label.ascent(), label)
        }
    }

    /** A line one pixel wide, on the whole pixel whose centre is nearest [x]. */
    private fun pixelLine(canvas: Canvas, x: Float) {
        val pixel = Math.round(x - 0.5f).toFloat()
        canvas.drawRect(pixel, 0f, pixel + 1f, height.toFloat(), line)
    }

    private companion object {
        /** How far the lines rise from the screen's bottom edge, in dp: a short way, like the real gaps. */
        const val BANDS = 72f
        /** The letters' size and how far from the bands' top, in dp. */
        const val LABEL = 12f
        const val LABEL_TOP = 6f
    }
}
