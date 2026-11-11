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
    /** A word on the bar was held, to open its menu. Returns false if it has no menu. */
    private val hold: (Int) -> Boolean = { false },
    /** A button of the word menu or of a notice was touched. */
    private val act: (Action) -> Unit = {},
    /** The newest clip was touched, to paste it. */
    private val paste: () -> Unit = {},
    /** A touch ended the idle scene. The touch does nothing else. */
    private val woke: () -> Unit = {},
) : LinearLayout(context) {
    /** What the buttons in the place of the words do. */
    enum class Action { Save, Forget, Close, Undo, OpenSelected }

    /** A short line in the place of the words: a word's menu, or what just happened to a word. */
    sealed interface Notice {
        val word: String
        /** The menu of a held word. [forget] is false for a word that no list can drop. */
        data class Menu(override val word: String, val forget: Boolean) : Notice
        data class Forgot(override val word: String) : Notice
        data class Saved(override val word: String) : Notice
        /** A second of fun when the keyboard has had to hide and show itself for Microsoft Teams. It has no buttons. */
        object TeamsEgg : Notice {
            override val word = ""
        }
    }

    private val typeface = resources.getFont(R.font.inter_regular)
    private val wordViews = WORD_ORDER.map { index -> item({ choose(index) }, { hold(index) }) }
    /** The word that the field has selected, alone in the middle. A touch or a hold opens its menu. */
    private val selectedView = item({ act(Action.OpenSelected) }, { act(Action.OpenSelected); true })
    private val noticeLabel = TextView(context).apply {
        typeface = this@Strip.typeface
        textSize = 16f
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        isFocusable = false
        setPadding(dp(8), 0, dp(8), 0)
        // A touch beside the buttons is a way out of the menu.
        setOnClickListener { if (notice is Notice.Menu) act(Action.Close) }
    }
    private val noticeButtons = List(MAX_BUTTONS) {
        TextView(context).apply {
            typeface = this@Strip.typeface
            textSize = 16f
            gravity = Gravity.CENTER
            isSingleLine = true
            isFocusable = false
            setPadding(dp(14), 0, dp(14), 0)
        }
    }
    private val noticeRow = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(noticeLabel, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        for (button in noticeButtons) addView(button, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }
    /**
     * The newest clip, right after a copy: a clipboard icon and the start of the clip, at the size and on the line of the words, so
     * that it reads as one of them. A touch pastes it.
     */
    private val clipText = TextView(context).apply {
        typeface = this@Strip.typeface
        textSize = WORD_SIZE
        gravity = Gravity.CENTER
        isSingleLine = true
        // StripFit shrinks and cuts the clip, as it does the words.
        ellipsize = null
        isFocusable = false
        compoundDrawablePadding = dp(8)
    }
    /** Said once, under the first clip that the strip offers: where the older clips are. */
    private val clipHintText = TextView(context).apply {
        typeface = this@Strip.typeface
        textSize = 12f
        gravity = Gravity.CENTER
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        isFocusable = false
        text = context.getString(R.string.strip_clip_hint)
    }
    private val clipView = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isFocusable = false
        setPadding(dp(6), 0, dp(6), 0)
        addView(clipText, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(clipHintText, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        setOnClickListener { paste() }
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

    /** A word's menu or a notice about a word, in the place of everything else. */
    var notice: Notice? = null
        set(value) {
            if (field == value) return
            field = value
            render()
        }

    /** The word that the field has selected, shown alone so that it can be saved or forgotten. */
    var selected: String? = null
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
        addView(noticeRow, LayoutParams(0, dp(56), 1f))
        addView(clipView, LayoutParams(0, dp(56), 1f))
        addView(selectedView, LayoutParams(0, dp(56), 1f))
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
        textSize = WORD_SIZE
        gravity = Gravity.CENTER
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        isFocusable = false
        setPadding(dp(WORD_PADDING), 0, dp(WORD_PADDING), 0)
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
        val confirm = notice.takeIf { showSuggestions }
        val bar = modifiers.takeIf { showSuggestions && confirm == null }
        val offer = clip.takeIf { showSuggestions && confirm == null && bar == null }
        val chosen = selected.takeIf { showSuggestions && confirm == null && bar == null && offer == null }
        setBackgroundColor(if (!showSuggestions) Color.TRANSPARENT else look.paper)
        // The lowest text sits about 16 dp over the bottom of the strip, where the corners curve in.
        side = maxOf(dp(8), corners.inset((navigationInset + dp(CONTENT_ABOVE)).toFloat()) + dp(4))
        setPadding(side, 0, side, if (showSuggestions) navigationInset else 0)
        // Keep a window for firmware touchpad routing when there is nothing to display.
        minimumHeight = if (showSuggestions) 0 else 1
        // Long words shrink, borrow room from short ones, and as a last resort lose the part they share with the others.
        val shown = WORD_ORDER.map { words.getOrElse(it) { "" } }
        val room = (width - paddingLeft - paddingRight).toFloat()
        val fits = if (room > 0) StripFit.fit(shown, room, dp(WORD_PADDING).toFloat(), ::measureWord) else null
        for ((slot, view) in wordViews.withIndex()) {
            val index = WORD_ORDER[slot]
            // While the idle scene plays, it draws the letters itself, and the words keep their places underneath.
            view.visibility = if (showSuggestions && bar == null && confirm == null && offer == null && chosen == null)
                (if (idle != null) INVISIBLE else VISIBLE) else GONE
            val fit = fits?.get(slot)
            view.text = fit?.text ?: shown[slot]
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, wordPixels() * (fit?.scale ?: 1f))
            // Each place is as wide as what it shows, so a touch lands on the word that it shows.
            val params = view.layoutParams as LayoutParams
            val weight = fit?.width ?: 1f
            if (params.weight != weight) {
                params.weight = weight
                view.layoutParams = params
            }
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
        showNotice(confirm)
        clipView.visibility = if (offer != null) VISIBLE else GONE
        // The start of the clip, cut cleanly at the end of the line.
        // The clip follows the words' rules: it shrinks to fit, then loses its end.
        val preview = offer?.let { ClipboardHistory.preview(it, CLIP_PREVIEW_LENGTH) }.orEmpty()
        val icon = Math.round(wordPixels() * CLIP_ICON_SCALE)
        val clipRoom = (width - paddingLeft - paddingRight - clipView.paddingLeft - clipView.paddingRight - icon - clipText.compoundDrawablePadding).toFloat()
        val clipFit = if (clipRoom > 0 && preview.isNotEmpty()) StripFit.fit(listOf(preview), clipRoom, 0f, ::measureWord).single() else null
        clipText.text = clipFit?.text ?: preview
        clipText.setTextSize(TypedValue.COMPLEX_UNIT_PX, wordPixels() * (clipFit?.scale ?: 1f))
        clipText.setTextColor(look.ink)
        // The icon is as tall as the words' letters, so it sits on their line.
        clipText.setCompoundDrawablesRelative(context.getDrawable(R.drawable.clipboard)?.mutate()?.apply {
            setBounds(0, 0, icon, icon)
            setTint(look.accent)
        }, null, null, null)
        clipHintText.visibility = if (offer != null && clipHint) VISIBLE else GONE
        clipHintText.setTextColor(look.muted)
        selectedView.visibility = if (chosen != null) VISIBLE else GONE
        // The selected word follows the words' rules too: it shrinks to fit, then loses its end.
        val selectedFit = if (room > 0 && !chosen.isNullOrEmpty()) StripFit.fit(listOf(chosen), room, dp(WORD_PADDING).toFloat(), ::measureWord).single() else null
        selectedView.text = selectedFit?.text ?: chosen.orEmpty()
        selectedView.setTextSize(TypedValue.COMPLEX_UNIT_PX, wordPixels() * (selectedFit?.scale ?: 1f))
        selectedView.setTextColor(look.ink)
    }

    /** The words' size in pixels, at full size. */
    private fun wordPixels() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, WORD_SIZE, resources.displayMetrics)

    private val measurePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    /** How wide a word is at the words' full size. */
    private fun measureWord(text: String): Float {
        measurePaint.typeface = typeface
        measurePaint.textSize = wordPixels()
        return measurePaint.measureText(text)
    }

    /** The words' places depend on the strip's width, so they are laid out again when it changes. */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) post { render() }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        idle?.let { scene ->
            val time = SystemClock.uptimeMillis() - idleStart
            idleDrawing.draw(canvas, scene.at(time), wordViews.first().paint, dp(56).toFloat(), look)
            // A modest frame rate, and none at all once the build stands still.
            if (time < scene.millis) postDelayed(idleFrame, IDLE_FRAME_MILLIS)
        }
        if (!gesture.showing) return
        val scale = if (GuideMotion.enabled) ValueAnimator.getDurationScale() else 0f
        val more = gesture.advance(SystemClock.uptimeMillis(), scale)
        if (gesture.showing) drawing.draw(canvas, gesture, side.toFloat(), (width - side).toFloat(), dp(56), look)
        if (more) postInvalidateOnAnimation()
    }

    /** The label and buttons of a word's menu, or of what just happened to a word. */
    private fun showNotice(shown: Notice?) {
        noticeRow.visibility = if (shown != null) VISIBLE else GONE
        noticeLabel.text = when (shown) {
            is Notice.Menu -> context.getString(R.string.strip_menu_word, shown.word)
            is Notice.Forgot -> context.getString(R.string.strip_forgot, shown.word)
            is Notice.Saved -> context.getString(R.string.strip_saved, shown.word)
            Notice.TeamsEgg -> context.getString(R.string.strip_teams_egg)
            null -> ""
        }
        noticeLabel.setTextColor(look.ink)
        val buttons = when (shown) {
            is Notice.Menu -> listOfNotNull(Action.Save to R.string.strip_save, (Action.Forget to R.string.strip_forget).takeIf { shown.forget },
                Action.Close to R.string.strip_back)
            is Notice.Forgot -> listOf(Action.Undo to R.string.strip_undo)
            is Notice.Saved, Notice.TeamsEgg, null -> emptyList()
        }
        for ((index, button) in noticeButtons.withIndex()) {
            val action = buttons.getOrNull(index)
            button.visibility = if (action != null) VISIBLE else GONE
            if (action == null) continue
            button.text = context.getString(action.second)
            // Back is the calm way out, so only the actions take the accent.
            button.setTextColor(if (action.first == Action.Close) look.muted else look.accent)
            button.setOnClickListener { act(action.first) }
        }
    }

    /** The words are covered while a gesture shows, so a touch must not choose one that nobody can see. */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // A touch on the idle scene only ends it: the words come back as they were, and nothing is picked.
        if (idle != null) {
            stopIdle()
            woke()
            return true
        }
        return gesture.showing || super.onInterceptTouchEvent(event)
    }

    // The idle scene, an Easter egg: see [IdleScene]. It has no touch targets, and any input ends it at once.

    private val idleDrawing = IdleDrawing(resources.displayMetrics.density)
    private var idle: IdleScene? = null
    private var idleStart = 0L
    private val idleFrame = Runnable { invalidate() }

    val idling get() = idle != null

    /** Starts the idle scene with the words on the bar, if plain words show. Returns false if there is nothing to play with. */
    fun startIdle(random: kotlin.random.Random): Boolean {
        if (idle != null || !isShown || !showSuggestions || modifiers != null || notice != null || clip != null || selected != null
            || gesture.showing) return false
        val letters = ArrayList<IdleScene.Letter>()
        for ((word, view) in wordViews.withIndex()) {
            val layout = view.layout ?: continue
            if (view.visibility != VISIBLE || view.text.isEmpty()) continue
            // An ellipsized word gives only the letters that show.
            val shown = if (layout.getEllipsisCount(0) > 0) layout.getEllipsisStart(0) else view.text.length
            val baseline = view.top + view.totalPaddingTop + layout.getLineBaseline(0).toFloat()
            for (i in 0 until shown) {
                val char = view.text[i]
                if (char.isWhitespace()) continue
                letters += IdleScene.Letter(char, view.left + view.totalPaddingLeft + layout.getPrimaryHorizontal(i) - view.scrollX, baseline, word)
            }
        }
        if (letters.isEmpty()) return false
        idle = IdleScene(letters, width.toFloat(), dp(56).toFloat(), resources.displayMetrics.density, random)
        idleStart = SystemClock.uptimeMillis()
        render()
        invalidate()
        return true
    }

    /** Ends the idle scene, and the bar is as it was. */
    fun stopIdle() {
        if (idle == null) return
        idle = null
        removeCallbacks(idleFrame)
        render()
        invalidate()
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        render()
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    companion object {
        private val WORD_ORDER = listOf(1, 0, 2)
        /** The words' size, in sp, and the room on each side of a word. */
        private const val WORD_SIZE = 17f
        private const val WORD_PADDING = 6
        /** About 30 frames a second while the idle scene moves. */
        private const val IDLE_FRAME_MILLIS = 33L
        /** Enough of the clip for one line. The line itself cuts it cleanly where it ends. */
        private const val CLIP_PREVIEW_LENGTH = 200
        /** The clipboard icon beside a clip, as a part of the text size. */
        private const val CLIP_ICON_SCALE = 1.1f
        /** Save, Forget and Back. */
        private const val MAX_BUTTONS = 3
        private const val CHIP_SIZE = 13f
        private const val CONTENT_ABOVE = 16
        private const val HINT_SMALLEST = 8f
        private const val HINT_LARGEST = 17f
    }
}
