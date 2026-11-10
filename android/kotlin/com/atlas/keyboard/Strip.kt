package com.atlas.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The bar above the keyboard: word suggestions, or which modifiers are on. The modifiers take the place of the words, at the same
 * height. While a special gesture runs, [gesture] draws over them at that height too, and the words wait underneath.
 */
class Strip(
    context: Context,
    private val choose: (Int) -> Unit,
) : LinearLayout(context) {
    private val typeface = resources.getFont(R.font.inter_regular)
    private val wordViews = WORD_ORDER.map { index -> item { choose(index) } }
    private val chips = ModifierBar.ALL.associateWith(::chip)
    private val hint = TextView(context).apply {
        typeface = this@Strip.typeface
        gravity = Gravity.CENTER
        // A single-line view scrolls where this one must shrink, so it only has a limit of one line.
        maxLines = 1
        includeFontPadding = false
        isFocusable = false
        val metrics = resources.displayMetrics
        setAutoSizeTextTypeUniformWithConfiguration(
            Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, HINT_SMALLEST, metrics)),
            Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, HINT_LARGEST, metrics)),
            1, TypedValue.COMPLEX_UNIT_PX)
    }
    private val modifierRow = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        for (chip in chips.values) addView(chip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        addView(hint, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }
    private var navigationInset = 0
    private var corners = Corners.None
    /** How far the content keeps from each side, so that the screen's rounded corners do not cut it. */
    private var side = 0
    private val drawing = GestureDrawing(resources.displayMetrics.density)
    private var look = Look.of(context)

    /** Shows what a special gesture is doing, in the place of the words. It shows only where the strip shows. */
    val gesture = GestureDisplay { postInvalidateOnAnimation() }

    var showSuggestions = true
        set(value) {
            if (field == value) return
            field = value
            render()
        }

    var words: List<String> = emptyList()
        set(value) {
            field = value
            render()
        }

    /** Which modifiers are on, in the place of the words. Null shows the words. */
    var modifiers: ModifierBar? = null
        set(value) {
            if (field == value) return
            field = value
            render()
        }

    init {
        for (view in wordViews) addView(view, LayoutParams(0, dp(56), 1f))
        addView(modifierRow, LayoutParams(0, dp(56), 1f))
        // Screen readers go left to right, so point them at the best word first.
        val ranked = wordViews.withIndex().sortedBy { WORD_ORDER[it.index] }.map { it.value }
        for (view in ranked) view.id = generateViewId()
        ranked.zipWithNext { view, next -> view.accessibilityTraversalBefore = next.id }
        // Keep the words clear of the gesture handle, which draws over the bottom of the keyboard, and of the rounded corners.
        setOnApplyWindowInsetsListener { _, insets ->
            navigationInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            corners = Corners.of(insets, display)
            render()
            insets
        }
        render()
    }

    private fun item(press: (TextView) -> Unit) = TextView(context).apply {
        typeface = this@Strip.typeface
        textSize = 17f
        gravity = Gravity.CENTER
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        isFocusable = false
        setPadding(dp(6), 0, dp(6), 0)
        setOnClickListener { press(this) }
    }

    /** A small label for one modifier. It is the same size at any system font size. */
    private fun chip(name: String) = TextView(context).apply {
        typeface = this@Strip.typeface
        text = name
        isSingleLine = true
        includeFontPadding = false
        isFocusable = false
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, CHIP_SIZE)
        setPadding(dp(8), dp(3), dp(8), dp(3))
    }

    /** The insets listener does not always run in the keyboard's window, so the corners are read here as well. */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        corners = Corners.of(rootWindowInsets, display)
        render()
    }

    private fun render() {
        look = Look.of(context)
        gesture.allowed = showSuggestions
        // A strip that shows nothing has no height, and a bar there would move the app each time a modifier changes.
        val bar = modifiers.takeIf { showSuggestions }
        setBackgroundColor(if (!showSuggestions) Color.TRANSPARENT else look.paper)
        // The lowest text sits about 16 dp over the bottom of the strip, where the corners curve in.
        side = maxOf(dp(8), corners.inset((navigationInset + dp(CONTENT_ABOVE)).toFloat()) + dp(4))
        setPadding(side, 0, side, if (showSuggestions) navigationInset else 0)
        // Keep a window for firmware touchpad routing when there is nothing to display.
        minimumHeight = if (showSuggestions) 0 else 1
        for ((slot, view) in wordViews.withIndex()) {
            val index = WORD_ORDER[slot]
            view.visibility = if (showSuggestions && bar == null) VISIBLE else GONE
            view.text = words.getOrElse(index) { "" }
            view.isEnabled = index < words.size
            view.setTextColor(look.ink)
        }
        modifierRow.visibility = if (bar != null) VISIBLE else GONE
        for ((name, chip) in chips) {
            chip.visibility = if (bar != null && name in bar.chips) VISIBLE else GONE
            // A key that a finger holds down is filled. One that was pushed and let go is an outline.
            val held = bar != null && name in bar.held
            chip.setTextColor(if (held) look.onAccent else look.accent)
            chip.background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                if (held) setColor(look.accent) else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1).coerceAtLeast(2), look.accent)
                }
            }
        }
        hint.text = bar?.hint.orEmpty()
        hint.setTextColor(look.ink)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!gesture.showing) return
        val scale = if (GuideMotion.enabled) ValueAnimator.getDurationScale() else 0f
        val more = gesture.advance(SystemClock.uptimeMillis(), scale)
        if (gesture.showing) drawing.draw(canvas, gesture, side.toFloat(), (width - side).toFloat(), dp(56), look)
        if (more) postInvalidateOnAnimation()
    }

    /** The words are covered while a gesture shows, so a touch must not choose one that nobody can see. */
    override fun onInterceptTouchEvent(event: MotionEvent) = gesture.showing || super.onInterceptTouchEvent(event)

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        render()
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    companion object {
        private val WORD_ORDER = listOf(1, 0, 2)
        private const val CHIP_SIZE = 13f
        private const val CONTENT_ABOVE = 16
        private const val HINT_SMALLEST = 8f
        private const val HINT_LARGEST = 17f
    }
}
