package com.atlas.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowInsets
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The bar for exact typing, half as tall as the strip, worked by touch: an "Exact" label, then chips for Shift, Alt, Ctrl and fn,
 * then keys that a terminal or a remote computer needs and the Titan lacks. A tap on a chip turns it on for the next key, a long
 * press locks it. The keys' second page, behind "more", holds Home, End, Page up, Page down and F1 to F12.
 */
internal class ExactBar(
    context: Context,
    private val chip: (ExactTyping.BarKey, Boolean) -> Unit,
    private val key: (Int) -> Unit,
    private val typeClip: () -> Unit,
    /** Any touch on the bar, which keeps it up. */
    private val touched: () -> Unit,
) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private var look = Look.of(context)
    private val chips = ExactTyping.BarKey.entries.associateWith { barKey ->
        label(chipName(barKey)).apply {
            setOnClickListener { touched(); chip(barKey, false) }
            setOnLongClickListener { touched(); chip(barKey, true); true }
        }
    }
    private val first = LinearLayout(context)
    private val second = LinearLayout(context)
    private val secondScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(second)
    }
    private var navigationInset = 0

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label(context.getString(R.string.exact_bar_label)).apply { isClickable = false })
        for (view in chips.values) addView(view)
        for ((name, code) in listOf("Esc" to KeyEvent.KEYCODE_ESCAPE, "Tab" to KeyEvent.KEYCODE_TAB, "←" to KeyEvent.KEYCODE_DPAD_LEFT,
                "↑" to KeyEvent.KEYCODE_DPAD_UP, "↓" to KeyEvent.KEYCODE_DPAD_DOWN, "→" to KeyEvent.KEYCODE_DPAD_RIGHT))
            first.addView(keyView(name, code), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        first.addView(label(context.getString(R.string.exact_bar_clip)).apply { setOnClickListener { touched(); typeClip() } },
            LayoutParams(0, LayoutParams.MATCH_PARENT, 1.3f))
        first.addView(label("…").apply { setOnClickListener { touched(); page(second = true) } }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        second.addView(label("‹").apply { setOnClickListener { touched(); page(second = false) } })
        for ((name, code) in listOf("Home" to KeyEvent.KEYCODE_MOVE_HOME, "End" to KeyEvent.KEYCODE_MOVE_END,
                "PgUp" to KeyEvent.KEYCODE_PAGE_UP, "PgDn" to KeyEvent.KEYCODE_PAGE_DOWN) +
                (1..12).map { "F$it" to KeyEvent.KEYCODE_F1 + it - 1 })
            second.addView(keyView(name, code))
        addView(first, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(secondScroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        page(second = false)
        setOnApplyWindowInsetsListener { _, insets ->
            navigationInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            layoutBar()
            insets
        }
        layoutBar()
    }

    /** Shows the chips as the modifiers stand: filled when held or locked, an outline when on once. */
    fun show(levels: (ExactTyping.BarKey) -> ExactTyping.Level) {
        look = Look.of(context)
        setBackgroundColor(look.paper)
        for ((barKey, view) in chips) {
            val level = levels(barKey)
            view.setTextColor(if (level == ExactTyping.Level.Locked) look.onAccent else if (level == ExactTyping.Level.Once) look.accent else look.muted)
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

    private fun layoutBar() {
        setPadding(dp(6f).toInt(), 0, dp(6f).toInt(), navigationInset)
        minimumHeight = dp(HEIGHT).toInt() + navigationInset
    }

    private fun keyView(name: String, code: Int) = label(name).apply { setOnClickListener { touched(); key(code) } }

    private fun label(text: String) = TextView(context).apply {
        this.text = text
        textSize = TEXT_SIZE
        gravity = Gravity.CENTER
        isSingleLine = true
        includeFontPadding = false
        setTextColor(look.ink)
        setPadding(dp(7f).toInt(), dp(3f).toInt(), dp(7f).toInt(), dp(3f).toInt())
    }

    private fun chipName(key: ExactTyping.BarKey) = when (key) {
        ExactTyping.BarKey.Shift -> "Shift"
        ExactTyping.BarKey.Alt -> "Alt"
        ExactTyping.BarKey.Ctrl -> "Ctrl"
        ExactTyping.BarKey.Fn -> "fn"
    }

    private fun dp(value: Float) = value * density

    companion object {
        /** About half the strip's 56 dp. */
        const val HEIGHT = 28f
        const val TEXT_SIZE = 13f
    }
}
