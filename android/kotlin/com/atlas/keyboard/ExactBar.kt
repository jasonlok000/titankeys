package com.atlas.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowInsets
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The bar for exact typing (experimental), as tall as the strip and worked by touch: chips for Shift, Alt and Ctrl, then Esc, Tab
 * and "more", which flips to the arrows, Home, End, Page up, Page down and F1 to F12. A tap on a chip turns it on for the next key,
 * a long press locks it. Like the strip, it keeps clear of the screen's rounded corners and the gesture handle.
 */
internal class ExactBar(
    context: Context,
    private val chip: (ExactTyping.BarKey, Boolean) -> Unit,
    private val key: (Int) -> Unit,
) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private var look = Look.of(context)
    private val chips = ExactTyping.BarKey.entries.associateWith { barKey ->
        label(barKey.name).apply {
            setOnClickListener { chip(barKey, false) }
            setOnLongClickListener { chip(barKey, true); true }
        }
    }
    private val first = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val second = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val secondScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(second)
    }
    private var navigationInset = 0
    private var corners = Corners.None

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        for (view in chips.values) first.addView(view, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        first.addView(keyView("Esc", KeyEvent.KEYCODE_ESCAPE), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        first.addView(keyView("Tab", KeyEvent.KEYCODE_TAB), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        first.addView(label(context.getString(R.string.exact_bar_more)).apply { setOnClickListener { page(second = true) } },
            LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        second.addView(label("‹").apply { setOnClickListener { page(second = false) } })
        for ((name, code) in listOf("←" to KeyEvent.KEYCODE_DPAD_LEFT, "↑" to KeyEvent.KEYCODE_DPAD_UP,
                "↓" to KeyEvent.KEYCODE_DPAD_DOWN, "→" to KeyEvent.KEYCODE_DPAD_RIGHT, "Home" to KeyEvent.KEYCODE_MOVE_HOME,
                "End" to KeyEvent.KEYCODE_MOVE_END, "PgUp" to KeyEvent.KEYCODE_PAGE_UP, "PgDn" to KeyEvent.KEYCODE_PAGE_DOWN) +
                (1..12).map { "F$it" to KeyEvent.KEYCODE_F1 + it - 1 })
            second.addView(keyView(name, code))
        addView(first, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(secondScroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        page(second = false)
        setOnApplyWindowInsetsListener { _, insets ->
            navigationInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            corners = Corners.of(insets, display)
            layoutBar()
            insets
        }
        layoutBar()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        corners = Corners.of(rootWindowInsets, display)
        layoutBar()
    }

    /** Shows the chips as the modifiers stand: filled when held or locked, an outline when on once. */
    fun show(levels: (ExactTyping.BarKey) -> ExactTyping.Level) {
        look = Look.of(context)
        setBackgroundColor(look.paper)
        for ((barKey, view) in chips) {
            val level = levels(barKey)
            view.setTextColor(if (level == ExactTyping.Level.Locked) look.onAccent else if (level == ExactTyping.Level.Once) look.accent else look.ink)
            view.background = if (level == ExactTyping.Level.Off) null else GradientDrawable().apply {
                cornerRadius = dp(6f)
                if (level == ExactTyping.Level.Locked) setColor(look.accent) else {
                    setColor(Color.TRANSPARENT)
                    setStroke(Math.max(2, dp(1f).toInt()), look.accent)
                }
            }
        }
    }

    private fun page(second: Boolean) {
        first.visibility = if (second) GONE else VISIBLE
        secondScroll.visibility = if (second) VISIBLE else GONE
    }

    /** The strip's height, and its room at the sides for the rounded corners and at the bottom for the gesture handle. */
    private fun layoutBar() {
        val side = maxOf(dp(8f).toInt(), corners.inset(navigationInset + dp(CONTENT_ABOVE)) + dp(4f).toInt())
        setPadding(side, 0, side, navigationInset)
        minimumHeight = dp(HEIGHT).toInt() + navigationInset
    }

    private fun keyView(name: String, code: Int) = label(name).apply { setOnClickListener { key(code) } }

    private fun label(text: String) = TextView(context).apply {
        this.text = text
        // Ink's font, as everywhere on the bar (owner, 2026-11-12).
        typeface = context.resources.getFont(R.font.inter_regular)
        textSize = TEXT_SIZE
        gravity = Gravity.CENTER
        maxLines = 1
        // A label shrinks to fit its place, so a narrow phone or a large font never cuts it.
        setAutoSizeTextTypeUniformWithConfiguration(SMALLEST_TEXT.toInt(), TEXT_SIZE.toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
        setTextColor(look.ink)
        setPadding(dp(6f).toInt(), dp(4f).toInt(), dp(6f).toInt(), dp(4f).toInt())
    }

    private fun dp(value: Float) = value * density

    companion object {
        /** The strip's own height. */
        const val HEIGHT = 56f
        const val TEXT_SIZE = 17f
        const val SMALLEST_TEXT = 9f
        private const val CONTENT_ABOVE = 16f
    }
}
