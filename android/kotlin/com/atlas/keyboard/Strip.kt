package com.atlas.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
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
    /** A word was held to forget it. Returns false if it can't be forgotten. */
    private val hold: (Int) -> Boolean = { false },
    private val undo: () -> Unit = {},
    /** The newest clip was touched, to paste it. */
    private val paste: () -> Unit = {},
) : LinearLayout(context) {
    private val typeface = resources.getFont(R.font.inter_regular)
    private val wordViews = WORD_ORDER.map { index -> item({ choose(index) }, { hold(index) }) }
    /** The confirm after a word is held: what was forgotten, and Undo. */
    private val forgotLabel = TextView(context).apply {
        typeface = this@Strip.typeface
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        isFocusable = false
    }
    private val undoButton = TextView(context).apply {
        typeface = this@Strip.typeface
        textSize = 17f
        gravity = Gravity.CENTER
        isFocusable = false
        text = context.getString(R.string.strip_undo)
        setPadding(dp(16), 0, dp(16), 0)
        setOnClickListener { undo() }
    }
    /** The newest clip, right after a copy. A touch pastes it. */
    private val clipView = TextView(context).apply {
        typeface = this@Strip.typeface
        textSize = 17f
        gravity = Gravity.CENTER
        // One line for the clip, and a second line only for the one-time hint.
        maxLines = 2
        includeFontPadding = false
        ellipsize = TextUtils.TruncateAt.END
        isFocusable = false
        setPadding(dp(12), 0, dp(12), 0)
        setOnClickListener { paste() }
    }
    private val forgetRow = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(forgotLabel, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(undoButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }
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

    /** A word that was just held to forget it. While it is set, the strip shows it with Undo in the place of the words. */
    var forgotten: String? = null
        set(value) {
            if (field == value) return
            field = value
            render()
        }

    /** The newest clip, shown in the place of the words right after a copy, until it is pasted or you type on. */
    var clip: String? = null
        set(value) {
            if (field == value) return
            field = value
            render()
        }

    /** The first clip that the strip ever offers also says where the older clips are. */
    var clipHint = false
        set(value) {
            if (field == value) return
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
        addView(forgetRow, LayoutParams(0, dp(56), 1f))
        addView(clipView, LayoutParams(0, dp(56), 1f))
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

    private fun item(press: (TextView) -> Unit, hold: () -> Boolean) = TextView(context).apply {
        typeface = this@Strip.typeface
        textSize = 17f
        gravity = Gravity.CENTER
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        isFocusable = false
        setPadding(dp(6), 0, dp(6), 0)
        setOnClickListener { press(this) }
        setOnLongClickListener { hold() }
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
        val confirm = forgotten.takeIf { showSuggestions }
        val bar = modifiers.takeIf { showSuggestions && confirm == null }
        val offer = clip.takeIf { showSuggestions && confirm == null && bar == null }
        setBackgroundColor(if (!showSuggestions) Color.TRANSPARENT else look.paper)
        // The lowest text sits about 16 dp over the bottom of the strip, where the corners curve in.
        side = maxOf(dp(8), corners.inset((navigationInset + dp(CONTENT_ABOVE)).toFloat()) + dp(4))
        setPadding(side, 0, side, if (showSuggestions) navigationInset else 0)
        // Keep a window for firmware touchpad routing when there is nothing to display.
        minimumHeight = if (showSuggestions) 0 else 1
        for ((slot, view) in wordViews.withIndex()) {
            val index = WORD_ORDER[slot]
            view.visibility = if (showSuggestions && bar == null && confirm == null && offer == null) VISIBLE else GONE
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
        forgetRow.visibility = if (confirm != null) VISIBLE else GONE
        forgotLabel.text = confirm?.let { context.getString(R.string.strip_forgot, it) }.orEmpty()
        forgotLabel.setTextColor(look.ink)
        undoButton.setTextColor(look.accent)
        clipView.visibility = if (offer != null) VISIBLE else GONE
        clipView.text = offer?.let { clipText(it) }.orEmpty()
        clipView.setTextColor(look.accent)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!gesture.showing) return
        val scale = if (GuideMotion.enabled) ValueAnimator.getDurationScale() else 0f
        val more = gesture.advance(SystemClock.uptimeMillis(), scale)
        if (gesture.showing) drawing.draw(canvas, gesture, side.toFloat(), (width - side).toFloat(), dp(56), look)
        if (more) postInvalidateOnAnimation()
    }

    private fun clipText(clip: String): CharSequence {
        val line = context.getString(R.string.strip_clip, ClipboardHistory.preview(clip, CLIP_PREVIEW_LENGTH))
        if (!clipHint) return line
        val text = SpannableStringBuilder(line).append('\n')
        val start = text.length
        text.append(context.getString(R.string.strip_clip_hint))
        text.setSpan(RelativeSizeSpan(HINT_SIZE), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(ForegroundColorSpan(look.ink), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }

    /** Whether the words are on show, so a flick up can pick one. */
    val wordsShowing get() = showSuggestions && modifiers == null && forgotten == null && clip == null && !gesture.showing && isShown

    /** Marks the word at place [index] on the bar for a moment, so that you see which word a flick picked. */
    fun flash(index: Int) {
        val view = wordViews.getOrNull(WORD_ORDER.indexOf(index)) ?: return
        view.background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(look.accent and 0x00FFFFFF or FLASH_ALPHA)
        }
        flashEnd?.let(::removeCallbacks)
        val clear = Runnable { for (word in wordViews) word.background = null }
        flashEnd = clear
        postDelayed(clear, FLASH_MILLIS)
    }

    private var flashEnd: Runnable? = null

    /** The words are covered while a gesture shows, so a touch must not choose one that nobody can see. */
    override fun onInterceptTouchEvent(event: MotionEvent) = gesture.showing || super.onInterceptTouchEvent(event)

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        render()
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    companion object {
        /** The place on the bar of the word shown left, in the middle and right. The best word is in the middle. */
        val WORD_ORDER = listOf(1, 0, 2)
        private const val FLASH_MILLIS = 300L
        /** The clip on the strip stops after this many characters, so that it fits one line. */
        private const val CLIP_PREVIEW_LENGTH = 40
        private const val HINT_SIZE = .7f
        private const val FLASH_ALPHA = 0x55000000
        private const val CHIP_SIZE = 13f
        private const val CONTENT_ABOVE = 16
        private const val HINT_SMALLEST = 8f
        private const val HINT_LARGEST = 17f
    }
}
