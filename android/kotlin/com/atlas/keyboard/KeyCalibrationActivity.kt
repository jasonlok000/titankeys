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

/** Key calibration's colours: the Haptics lab's dark page, with the keys in cream and the two edges in orange. */
private object CalibrationColours {
    const val INK = 0xFF160A04.toInt()
    const val CARD = 0xFF21140C.toInt()
    const val TRACK = 0xFF3A2A20.toInt()
    const val ORANGE = 0xFFEF804E.toInt()
    const val CREAM = 0xFFFFF1E6.toInt()
    const val DIM = 0xFFA2958C.toInt()
    /** A gap between two keys: faint orange, so the gaps read as one line of stripes against the real keys' gaps. */
    const val GAP = 0x40EF804E
    const val LABEL = 0x80FFF1E6.toInt()
}

/**
 * Key calibration, in TitanKeys Debug only. The real keys sit just below the screen, and the guide, the Sym panel, the word menu
 * and the swipe's path line up with them. This screen draws the three letter rows right at the screen's bottom edge, and its two
 * sliders move Q's left edge and P's right edge until the drawing sits right over the real keys; the rest of the keys follow,
 * as the real keyboard scaled ([KeyGeometry]). Every change is saved at once ([KeyCalibration]), and every drawing follows it.
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
            addView(text("Line the drawing at the bottom up with the real keys under the screen. Move the left edge to Q's left " +
                "edge and the right edge to P's right edge. It saves as you go.", 15f, CalibrationColours.DIM))
            addView(card(
                slider("Q's left edge", { KeyAlignment.leftProgress(it.left) }, { edge(it.left) }) { a, p ->
                    KeyAlignment(KeyAlignment.leftAt(p), a.right)
                },
                slider("P's right edge", { KeyAlignment.rightProgress(it.right) }, { edge(1f - it.right) }) { a, p ->
                    KeyAlignment(a.left, KeyAlignment.rightAt(p))
                },
            ))
            addView(button("Reset") {
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

    /** A slider called [name], with its value live beside the name. Each move saves, and the grid draws again. */
    private fun slider(name: String, progress: (KeyAlignment) -> Int, value: (KeyAlignment) -> String,
                       changed: (KeyAlignment, Int) -> KeyAlignment): View {
        val shown = text("", 15f, CalibrationColours.ORANGE, bold = true).apply { gravity = Gravity.END }
        val bar = SeekBar(this).apply {
            max = KeyAlignment.EDGE_STEPS
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

    /** An edge [inward] of the screen's width in from its side, in pixels, as the slider's value. */
    private fun edge(inward: Float) = edgeText((inward * resources.displayMetrics.widthPixels).roundToInt())

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
        fun intent(context: Context) = Intent(context, KeyCalibrationActivity::class.java)

        /** An edge [pixels] in from the screen's side, or out past it when below 0, in words. */
        fun edgeText(pixels: Int) = when {
            pixels == 0 -> "on the edge"
            pixels > 0 -> "$pixels px in"
            else -> "${-pixels} px out"
        }
    }
}

/**
 * The three letter rows of [KeyGeometry], as wide as the screen and as tall as the rows, so that the bottom row ends right at the
 * screen's bottom edge, over the real keys. Each key is a thin outline and each gap a faint orange stripe, to hold against the real
 * keys and their gaps, and Q's left edge and P's right edge, the two that the sliders move, are orange lines.
 */
internal class KeyGrid(context: Context) : View(context) {
    var alignment = KeyAlignment.DEFAULT
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, 800, false)
        textAlign = Paint.Align.CENTER
    }

    /** As tall as the three letter rows on this width: a narrower keyboard is shorter too. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, ceil(KeyGeometry(width.toFloat(), alignment).letterHeight).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val keys = KeyGeometry(width.toFloat(), alignment)
        // The rows sit at the bottom, so the bottom letter row's bottom is the screen's bottom edge.
        val top = height - keys.letterHeight
        val stroke = density
        outline.strokeWidth = stroke
        outline.color = CalibrationColours.CREAM
        label.color = CalibrationColours.LABEL
        label.textSize = keys.key * LABEL
        for (box in keys.boxes) {
            if (box.row > 2) continue
            // The outline sits inside the key, so its outer edge is the key's edge.
            canvas.drawRect(box.left + stroke / 2, top + box.top + stroke / 2, box.right - stroke / 2, top + box.bottom - stroke / 2, outline)
            canvas.drawText(box.name, box.centreX, top + box.centreY - (label.ascent() + label.descent()) / 2, label)
        }
        fill.color = CalibrationColours.GAP
        for (row in 0..2) for (column in 0 until KeyGeometry.COLUMNS - 1) {
            val left = keys.keyLeft(column) + keys.key
            canvas.drawRect(left, top + keys.rowTop(row), left + keys.gap, top + keys.rowTop(row) + keys.keyHeight, fill)
        }
        // The two edges that the sliders move, down the whole drawing.
        fill.color = CalibrationColours.ORANGE
        canvas.drawRect(keys.left, 0f, keys.left + EDGE * density, height.toFloat(), fill)
        canvas.drawRect(keys.right - EDGE * density, 0f, keys.right, height.toFloat(), fill)
    }

    private companion object {
        /** A key's label, as a part of its width, faint: the edges matter here, not the letters. */
        const val LABEL = .3f
        /** How thick the two edges are, in dp. */
        const val EDGE = 2f
    }
}
