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
 * The bar above the keyboard: word suggestions, with small chips for the modifiers that are on. While a finger rests on a letter
 * with a modifier on, or Ctrl is held, the modifiers take the place of the words, at the same height: see [ModifierBar]. While a
 * special gesture runs, [gesture] draws over them at that height too, and the words wait underneath.
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
    /** A touch on the idle game: you came back. The touch does nothing else. */
    private val woke: () -> Unit = {},
) : LinearLayout(context) {
    /** What the buttons in the place of the words do. */
    enum class Action { Save, Favourite, Favourited, Unfavourite, Unsave, Forget, Close, Undo, OpenSelected }

    /** A short line in the place of the words: a word's menu, or what just happened to a word. */
    sealed interface Notice {
        val word: String
        /**
         * The menu of a held word, at [step]: see [MenuKeys.Step]. [forget] is false for a word that no list can drop, and
         * [builtIn] is true for one the dictionary has, where Unfavourite goes back to. Its I and O buttons are [first] and
         * [second], and P is Back.
         */
        data class Menu(
            override val word: String,
            val forget: Boolean,
            val step: MenuKeys.Step = MenuKeys.Step.New,
            val builtIn: Boolean = false,
        ) : Notice {
            val first get() = MenuKeys.first(step).action
            val second get() = MenuKeys.second(step, forget)?.action
        }
        data class Forgot(override val word: String) : Notice
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
    private val menuSpace = android.view.View(context)
    private val noticeRow = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(noticeLabel, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        for (button in noticeButtons) addView(button, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        // In a word's menu, the room over P's column, which picks nothing.
        addView(menuSpace, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
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
        for (chip in chips.values) addView(chip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(CHIP_GAP) })
        addView(hint, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }
    private var navigationInset = 0
    private var corners = Corners.None
    /** How far the content keeps from each side, so that the screen's rounded corners do not cut it. */
    private var side = 0
    private val drawing = GestureDrawing(resources.displayMetrics.density, Radii.px(resources, Radii.SMALL))
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
            // A word that the guide points at stops when it is no longer in its place.
            if (pointed != null && value.getOrNull(pointedIndex) != pointed) point(null)
            render()
        }

    // The guide points at a word: it wiggles up and down in its place until it is picked or changes. See [GuideHooks.stripPoint].

    private var pointedIndex = -1
    private var pointed: String? = null
    private var pointer: android.animation.ValueAnimator? = null

    /** Points at [word], wherever it sits on the bar, or stops with null. */
    internal fun pointWord(word: String?) {
        val index = word?.let { wanted -> words.indexOfFirst { it.equals(wanted, ignoreCase = true) } } ?: -1
        point(index.takeIf { it >= 0 })
    }

    /**
     * The bar shows its side words, or only the middle one, for the guide's first slide. As they come back they slide up into
     * their places.
     */
    var sides = true
        set(value) {
            if (field == value) return
            field = value
            render()
            if (value && GuideMotion.enabled) for ((slot, view) in wordViews.withIndex()) {
                if (WORD_ORDER[slot] == 0) continue
                view.translationY = dp(56).toFloat()
                view.alpha = 0f
                view.animate().translationY(0f).alpha(1f).setDuration(SIDES_MILLIS).start()
            }
        }

    /** Points at the word at [index], as the keyboard ranks them, or stops with null. */
    internal fun point(index: Int?) {
        pointer?.cancel()
        pointer = null
        for (view in wordViews) view.translationY = 0f
        pointed = null
        pointedIndex = -1
        if (index == null || index !in words.indices || !GuideMotion.enabled) return
        val view = wordViews[WORD_ORDER.indexOf(index)]
        pointedIndex = index
        pointed = words[index]
        val reach = dp(POINT_DP).toFloat()
        pointer = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = POINT_MILLIS
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { view.translationY = -reach * kotlin.math.sin(it.animatedFraction * 2 * Math.PI).toFloat() }
            start()
        }
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

    /** Which modifiers are on: chips beside the words, or the whole bar in their place. Null shows the words alone. */
    var modifiers: ModifierBar? = null
        set(value) {
            if (field == value) return
            field = value
            render()
        }

    // The word menu's state comes before init: init renders, and the menu's drawing reads these.
    private val pillLeft = Ease()
    private val pillRight = Ease()
    private val pillOpen = Ease()
    private val wordEase = Ease()
    /** The column whose pill shows or is shrinking away, and the action it shows. */
    private var pillShown = -1
    private var pillAction: Action? = null
    /** The badge after the word, and when it last changed, for its pop. */
    private var menuBadge: MenuKeys.Mark? = null
    private var badgeChanged = 0L

    init {
        // The modifiers come first, so that their chips sit at the start of the words.
        addView(modifierRow, LayoutParams(0, dp(56), 1f))
        for (view in wordViews) addView(view, LayoutParams(0, dp(56), 1f))
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
        // A touch on a word ends the guide's pointing, whichever word it was.
        typeface = this@Strip.typeface
        textSize = WORD_SIZE
        gravity = Gravity.CENTER
        isSingleLine = true
        // StripFit shrinks and cuts the words itself, where they differ. Android's own "…" would cut the part that matters.
        ellipsize = null
        isFocusable = false
        setPadding(dp(WORD_PADDING), 0, dp(WORD_PADDING), 0)
        setOnClickListener {
            point(null)
            press(this)
        }
        // Only a finger held on the screen opens a word's menu: never a touch that reaches the bar any other way, and never
        // a long click without a finger down at all.
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> heldOnScreen = event.isFromSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> heldOnScreen = false
            }
            false
        }
        setOnLongClickListener {
            val opened = heldOnScreen && hold()
            android.util.Log.d(HOLD_TAG, "bar word held: finger on the screen $heldOnScreen, menu opened $opened")
            opened
        }
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
        // A demonstration from the guide shows its own words and menu, in place of the bar's own.
        val demo = this.demo
        val confirm = (if (demo != null) demoNotice else notice).takeIf { showSuggestions }
        val modifierChips = modifiers.takeIf { showSuggestions && confirm == null }
        // Shift, Alt and Ctrl take the whole bar. The Shift that a sentence start turned on only gets a small mark: see [drawAutoCapital].
        val bar = modifierChips?.takeIf { it.replacesWords }
        autoCapital = modifierChips?.autoCapital == true && bar == null
        val offer = clip.takeIf { showSuggestions && confirm == null && bar == null && demo == null }
        val chosen = selected.takeIf { showSuggestions && confirm == null && bar == null && offer == null && demo == null }
        setBackgroundColor(if (!showSuggestions) Color.TRANSPARENT else look.paper)
        // The lowest text sits about 16 dp over the bottom of the strip, where the corners curve in.
        side = maxOf(dp(8), corners.inset((navigationInset + dp(CONTENT_ABOVE)).toFloat()) + dp(4))
        setPadding(side, 0, side, if (showSuggestions) navigationInset else 0)
        // Keep a window for firmware touchpad routing when there is nothing to display.
        minimumHeight = if (showSuggestions) 0 else 1
        // The three places never move or shrink: the middle one is muscle memory. A long word shrinks in its own place, and as a last
        // resort loses the part that it shares with the others, so the part where they differ shows. The fit keeps a little slack,
        // so that the text always fits its view and Android never cuts it with an "…" of its own.
        val shown = WORD_ORDER.map { (demoWords ?: words).getOrElse(it) { "" } }
        val room = (width - paddingLeft - paddingRight).toFloat()
        val fits = if (room > 0) StripFit.fit(shown, room, wordRoomPadding(), ::measureWord, lend = false) else null
        for ((slot, view) in wordViews.withIndex()) {
            val index = WORD_ORDER[slot]
            // While the idle scene plays, it draws the letters itself, and the words keep their places underneath.
            view.visibility = if (showSuggestions && bar == null && confirm == null && offer == null && chosen == null)
                (if (idle != null || !sides && index != 0) INVISIBLE else VISIBLE) else GONE
            val fit = fits?.get(slot)
            view.text = fit?.text ?: shown[slot]
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, wordPixels() * (fit?.scale ?: 1f))
            view.isEnabled = index < words.size && demo == null
            view.setTextColor(look.ink)
        }
        modifierRow.visibility = if (bar != null) VISIBLE else GONE
        for ((name, chip) in chips) {
            chip.visibility = if (bar != null && name in bar.chips) VISIBLE else GONE
            // A key that a finger holds down is filled. One that was pushed and let go is an outline.
            val held = bar != null && name in bar.held
            chip.setTextColor(if (held) look.onAccent else look.accent)
            chip.background = GradientDrawable().apply {
                cornerRadius = Radii.px(resources, Radii.SMALL)
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
        // The clip follows the words' rules: it shrinks to fit, and then shows its start and its last word, "word word … word".
        // While the automatic capital's arrow shows, the clip keeps out of the arrow's place at the left.
        val reserve = if (autoCapital && showSuggestions) Math.round(capitalWidth()) + dp(CAPITAL_CLEAR) + dp(4) else 0
        clipView.setPadding(dp(6) + reserve, 0, dp(6), 0)
        val preview = offer?.let { ClipboardHistory.preview(it, CLIP_PREVIEW_LENGTH) }.orEmpty()
        val icon = Math.round(wordPixels() * CLIP_ICON_SCALE)
        val clipRoom = (width - paddingLeft - paddingRight - clipView.paddingLeft - clipView.paddingRight - icon - clipText.compoundDrawablePadding).toFloat()
        val clipFit = if (clipRoom > 0 && preview.isNotEmpty()) StripFit.fit(listOf(preview), clipRoom, 0f, ::measureWord).single() else null
        val clipScale = clipFit?.scale ?: 1f
        clipText.text = if (clipFit == null) preview else ClipboardHistory.ends(preview, clipRoom) { measureWord(it) * clipScale }
        clipText.setTextSize(TypedValue.COMPLEX_UNIT_PX, wordPixels() * clipScale)
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
        val selectedFit = if (room > 0 && !chosen.isNullOrEmpty()) StripFit.fit(listOf(chosen), room, wordRoomPadding(), ::measureWord).single() else null
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

    /** The room that a word keeps on each side: its padding, and a little slack against rounding. */
    private fun wordRoomPadding() = (dp(WORD_PADDING) + dp(WORD_SLACK)).toFloat()

    /** Shift came on by itself at a sentence start, and the words show: the strip marks it with a small capital. */
    private var autoCapital = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    /**
     * The mark of an automatic capital: a shift arrow in the accent colour, where Shift's chip shows for one push, at the left of the
     * bar and at the size the arrow has in that chip, without the chip's outline. It is drawn over the bar, so the words never move
     * for it. If the left word reaches under it, it goes small into the top left corner for that bar instead.
     */
    private val capitalPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeJoin = android.graphics.Paint.Join.ROUND
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val capitalText = android.graphics.Paint()
    private val capitalArrow = android.graphics.Path()
    private val capitalBox = android.graphics.RectF()

    /** The height of Shift's chip: its text size and padding. The arrow's place is that tall. */
    private fun capitalHeight(): Float {
        capitalText.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CHIP_SIZE, resources.displayMetrics)
        return capitalText.descent() - capitalText.ascent() + 2 * dp(3)
    }

    /** The width of the arrow's place at the left of the bar. */
    private fun capitalWidth() = capitalHeight() - 2 * dp(CAPITAL_ARROW_IN) + 2 * dp(CAPITAL_SIDE_IN)

    /**
     * Puts the arrow's place in [capitalBox] and gives the arrow's size and line width in pixels. While an idle game plays with the
     * arrow, the game draws it.
     */
    private fun placeCapital(): Pair<Float, Float> {
        val height = capitalHeight()
        val arrow = height - 2 * dp(CAPITAL_ARROW_IN)
        val width = capitalWidth()
        val left = side.toFloat()
        val top = (dp(56) - height) / 2
        capitalBox.set(left, top, left + width, top + height)
        // The left word, as drawn: if its text would reach under the chip, the chip goes small into the corner.
        val word = wordViews.filter { it.visibility == VISIBLE && it.text.isNotEmpty() && it.layout != null }.minByOrNull { it.left }
        val textLeft = word?.let { it.left + it.totalPaddingLeft + it.layout.getLineLeft(0) - it.scrollX }
        val small = textLeft != null && textLeft < capitalBox.right + dp(CAPITAL_CLEAR)
        if (small) {
            val scale = CAPITAL_SMALL
            val x = side.toFloat() / 2 + dp(2)
            capitalBox.set(x, dp(4).toFloat(), x + width * scale, dp(4) + height * scale)
        }
        val cornerScale = capitalBox.height() / height
        return arrow * cornerScale to TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CAPITAL_LINE, resources.displayMetrics) * cornerScale
    }

    private fun drawAutoCapital(canvas: Canvas) {
        if (!autoCapital || !showSuggestions || idle?.glyphs?.any { it.arrow } == true) return
        val (size, line) = placeCapital()
        capitalPaint.color = look.accent
        // The shift arrow, where Shift's chip would be, without the chip's outline: a head as wide as it is tall at the top, on a stem half as wide.
        val x = capitalBox.centerX() - size / 2
        val y = capitalBox.centerY() - size / 2
        capitalPaint.strokeWidth = line
        capitalArrow.reset()
        capitalArrow.moveTo(x + size / 2, y)
        capitalArrow.lineTo(x + size, y + size * .52f)
        capitalArrow.lineTo(x + size * .73f, y + size * .52f)
        capitalArrow.lineTo(x + size * .73f, y + size)
        capitalArrow.lineTo(x + size * .27f, y + size)
        capitalArrow.lineTo(x + size * .27f, y + size * .52f)
        capitalArrow.lineTo(x, y + size * .52f)
        capitalArrow.close()
        canvas.drawPath(capitalArrow, capitalPaint)
    }

    /** The word of the open menu, drawn small above its buttons. */
    private var menuWord: String? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    private val menuWordPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
    }

    private fun drawMenuWord(canvas: Canvas) {
        // The word now sits on the left, in the row, over the columns that pick nothing.
        if (!MENU_WORD_ABOVE) return
        val word = menuWord ?: return
        if (noticeRow.visibility != VISIBLE) return
        menuWordPaint.typeface = typeface
        menuWordPaint.color = look.muted
        menuWordPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, MENU_WORD_SIZE, resources.displayMetrics)
        val text = context.getString(R.string.strip_menu_word, word)
        canvas.drawText(text, width / 2f, dp(MENU_WORD_TOP) - menuWordPaint.ascent(), menuWordPaint)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        drawMenuWord(canvas)
        drawMenu(canvas)
        drawAutoCapital(canvas)
        idle?.let { drawIdle(canvas, it) }
        drawDemoFinger(canvas)
        if (!gesture.busy) return
        val scale = if (GuideMotion.enabled) ValueAnimator.getDurationScale() else 0f
        val more = gesture.advance(SystemClock.uptimeMillis(), scale)
        if (gesture.busy) drawing.draw(canvas, gesture, side.toFloat(), (width - side).toFloat(), dp(56), look)
        if (more) postInvalidateOnAnimation()
    }

    /**
     * The column of the word menu's button that a resting finger hovers, or -1: see [MenuKeys.hovered]. Its pill shows the action
     * that the button does now, so after a press it shows the next step's. A hover ends the first-time hint.
     */
    var menuMark = -1
        set(value) {
            if (field == value) return
            field = value
            if (value >= 0) hintStart = -1L
            render()
            invalidate()
        }

    /** When the first-time hint started, in uptime ms, or -1: see [MenuKeys.hint]. */
    private var hintStart = -1L

    /** The menu that just opened shows each button's pill once, slowly, one after another. See [MenuKeys.HINT_OPENS]. */
    fun playMenuHint() {
        hintStart = SystemClock.uptimeMillis()
        invalidate()
    }

    /** The buttons that a notice shows, from the left. */
    fun noticeActions(shown: Notice?): List<Action> = when (shown) {
        is Notice.Menu -> listOfNotNull(shown.first, shown.second, Action.Close)
        is Notice.Forgot -> listOf(Action.Undo)
        Notice.TeamsEgg, null -> emptyList()
    }

    private fun label(action: Action) = when (action) {
        Action.Save -> R.string.strip_save
        Action.Favourite -> R.string.strip_favourite
        Action.Favourited -> R.string.strip_favourited
        Action.Unfavourite -> R.string.strip_unfavourite
        Action.Unsave -> R.string.strip_unsave
        Action.Forget -> R.string.strip_forget
        Action.Close -> R.string.strip_back
        Action.Undo -> R.string.strip_undo
        Action.OpenSelected -> R.string.strip_back
    }

    /** The label and buttons of a word's menu, or of what just happened to a word. */
    private fun showNotice(shown: Notice?) {
        noticeRow.visibility = if (shown != null) VISIBLE else GONE
        val menu = shown as? Notice.Menu
        // A word's menu is drawn on the bar itself, over the key columns: see [drawMenu]. The row stays, empty, to keep the bar's
        // height and to take a touch beside the buttons. Elsewhere a notice is its line and its buttons, as before.
        noticeRow.setPadding(0, 0, 0, 0)
        menuSpace.visibility = GONE
        (noticeLabel.layoutParams as LayoutParams).let { params ->
            if (params.weight != 1f) {
                params.weight = 1f
                noticeLabel.layoutParams = params
            }
        }
        noticeLabel.gravity = Gravity.CENTER_VERTICAL or if (shown == Notice.TeamsEgg) Gravity.CENTER_HORIZONTAL else Gravity.START
        noticeLabel.text = when (shown) {
            is Notice.Menu -> ""
            is Notice.Forgot -> context.getString(R.string.strip_forgot, shown.word)
            Notice.TeamsEgg -> context.getString(R.string.strip_teams_egg)
            null -> ""
        }
        noticeLabel.contentDescription = if (menu == null) null else
            context.getString(R.string.strip_menu_word, menu.word) + ": " + menuSlots(menu).filterNotNull().joinToString(", ") { context.getString(label(it)) }
        noticeLabel.setTextColor(look.ink)
        val actions = if (menu != null) emptyList() else noticeActions(shown)
        for ((index, button) in noticeButtons.withIndex()) {
            val action = actions.getOrNull(index)
            button.visibility = if (action != null) VISIBLE else GONE
            if (action == null) continue
            button.foreground = null
            button.background = null
            button.setCompoundDrawablesRelative(null, null, null, null)
            button.contentDescription = null
            button.setTextColor(if (action == Action.Close) look.muted else look.accent)
            button.text = context.getString(label(action))
            button.setPadding(dp(14), 0, dp(14), 0)
            button.setOnClickListener { act(action) }
        }
        // As the menu opens its buttons move in from the right, to their keys.
        if (menu != null && shownMenu != menu.word) {
            menuIn?.cancel()
            menuShown = if (GuideMotion.enabled) 0f else 1f
            if (GuideMotion.enabled) menuIn = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = MENU_SLIDE_MILLIS
                addUpdateListener {
                    menuShown = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
        if (menu == null) {
            hintStart = -1L
            pillShown = -1
            menuBadge = null
            wordEase.jump(Float.NaN)
        }
        shownMenu = menu?.word
        invalidate()
    }

    /** How far in the menu's buttons are, from 0 as it opens to 1. */
    private var menuShown = 1f
    private var menuIn: ValueAnimator? = null

    /** The menu's buttons, over I's, O's and P's columns. A word that no list can drop has no Forget. */
    private fun menuSlots(menu: Notice.Menu): List<Action?> = listOf(menu.first, menu.second, Action.Close)

    /** The left and right of a key column on this bar, in its own pixels: the Sym panel's columns. See [MenuKeys.slot]. */
    private fun menuColumn(column: Int) = MenuKeys.slot(width.toFloat(), dp(Layout.KEY_SIDE_DP).toFloat(), column)

    private val menuPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val menuWordText = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val pillText = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val menuBox = android.graphics.RectF()

    /** A value that glides to where it is going, for the pill's ends and the word's centre. NaN is nowhere: it jumps there. */
    private class Ease {
        private var from = Float.NaN
        private var to = Float.NaN
        private var start = 0L

        fun jump(value: Float) {
            from = value
            to = value
        }

        /** The value at [now], gliding over [millis] to [target] from wherever it was when the target changed. */
        fun at(target: Float, now: Long, millis: Long): Float {
            if (target != to) {
                from = if (to.isNaN() || millis == 0L) target else value(now, millis)
                to = target
                start = now
            }
            return value(now, millis)
        }

        private fun value(now: Long, millis: Long): Float {
            if (from.isNaN() || millis == 0L) return to
            val t = ((now - start).toFloat() / millis).coerceIn(0f, 1f)
            val eased = 1f - (1f - t) * (1f - t)
            return from + (to - from) * eased
        }

        fun moving(now: Long, millis: Long) = !from.isNaN() && from != to && now - start < millis
    }

    /**
     * The word's menu, drawn on the bar (the owner's design D, 2026-11-12). The word sits centred in the room left of the buttons,
     * with its step's badge after it: a bookmark on a saved word, a heart on a favourite. An icon sits over each button's key. A
     * finger hovering a button stretches its orange tile left into a pill that holds the action's name and covers the buttons to
     * its left, and the word slides to stay centred in the room left of the pill. Nothing comes near the screen's rounded corners.
     */
    private fun drawMenu(canvas: Canvas) {
        val menu = notice as? Notice.Menu ?: return
        if (noticeRow.visibility != VISIBLE || width == 0) return
        val now = SystemClock.uptimeMillis()
        val glide = if (GuideMotion.enabled) MENU_GLIDE_MILLIS else 0L
        val height = dp(56).toFloat()
        val keySide = dp(Layout.KEY_SIDE_DP).toFloat()
        val icon = dp(MENU_ICON_DP)
        val slots = menuSlots(menu)
        // What the pill shows: the hovered button, or the hint's while it plays.
        var more = false
        val hint = if (hintStart < 0) -1 else {
            val elapsed = now - hintStart
            val present = slots.withIndex().filter { it.value != null }.map { MenuKeys.SAVE + it.index }
            if (elapsed > MenuKeys.hintMillis(present.size)) hintStart = -1L else more = true
            MenuKeys.hint(elapsed, present)
        }
        val hovered = (if (menuMark >= 0) menuMark else hint).takeIf { slots.getOrNull(it - MenuKeys.SAVE) != null } ?: -1
        if (hovered >= 0) {
            pillShown = hovered
            pillAction = slots[hovered - MenuKeys.SAVE]
        }
        val open = pillOpen.at(if (hovered >= 0) 1f else 0f, now, glide)
        if (open <= 0f) pillShown = -1
        pillText.typeface = typeface
        pillText.isFakeBoldText = true
        pillText.textSize = wordPixels() * MENU_PILL_TEXT
        val pillHeight = dp(MENU_PILL_DP).toFloat()
        var freeRight = MenuKeys.wordRight(width.toFloat(), keySide, dp(MENU_WORD_GAP).toFloat())
        var pill: Pair<Float, Float>? = null
        val action = pillAction.takeIf { pillShown >= 0 }
        if (action != null) {
            val name = context.getString(label(action))
            val (left, right) = MenuKeys.pill(width.toFloat(), keySide, pillShown, pillText.measureText(name), icon.toFloat(),
                dp(MENU_PILL_LEFT).toFloat(), dp(MENU_PILL_GAP).toFloat(), dp(MENU_PILL_RIGHT).toFloat())
            // The pill grows out of its icon's own tile, and shrinks back into it.
            val shownRight = pillRight.at(right, now, glide)
            val tile = shownRight - icon - 2f * dp(MENU_PILL_RIGHT)
            val shownLeft = tile + (pillLeft.at(left, now, glide) - tile) * open
            pill = shownLeft to shownRight
            freeRight = minOf(freeRight, shownLeft - dp(MENU_WORD_GAP))
        } else {
            pillLeft.jump(Float.NaN)
            pillRight.jump(Float.NaN)
        }
        // The word and its badge, centred in the room left of the buttons or of the pill. A long word loses its middle.
        menuWordText.typeface = typeface
        menuWordText.textSize = wordPixels()
        menuWordText.color = look.ink
        menuWordText.textAlign = android.graphics.Paint.Align.LEFT
        val baseline = height / 2 - (menuWordText.ascent() + menuWordText.descent()) / 2
        val wordFoot = navigationInset + height - (baseline + menuWordText.descent())
        val wordLeft = maxOf(side.toFloat(), keySide, corners.inset(wordFoot).toFloat() + dp(MENU_CORNER_GAP))
        val mark = MenuKeys.mark(menu.step)
        if (mark != menuBadge) {
            badgeChanged = now
            menuBadge = mark
        }
        val badge = if (mark == null) 0f else dp(MENU_BADGE_DP).toFloat() + dp(MENU_BADGE_GAP)
        val room = (freeRight - wordLeft - badge).coerceAtLeast(0f)
        val text = context.getString(R.string.strip_menu_word,
            MenuKeys.shorten(menu.word) { menuWordText.measureText(context.getString(R.string.strip_menu_word, it)) <= room })
        val block = menuWordText.measureText(text) + badge
        val textLeft = wordEase.at(MenuKeys.wordCentre(wordLeft, freeRight), now, glide) - block / 2
        canvas.drawText(text, textLeft, baseline, menuWordText)
        if (mark != null) {
            // The badge pops as it changes: a quick swell and back.
            val since = now - badgeChanged
            val pop = if (GuideMotion.enabled && since < MENU_BADGE_POP_MILLIS) {
                more = true
                1f + MENU_BADGE_POP * kotlin.math.sin(Math.PI * since / MENU_BADGE_POP_MILLIS).toFloat()
            } else 1f
            val size = dp(MENU_BADGE_DP) * pop
            val x = textLeft + block - dp(MENU_BADGE_DP) / 2f
            val y = baseline + menuWordText.ascent() * MENU_BADGE_HEIGHT
            context.getDrawable(if (mark == MenuKeys.Mark.Heart) R.drawable.menu_favourited else R.drawable.menu_mark_saved)?.mutate()?.let {
                it.setTint(if (mark == MenuKeys.Mark.Heart) look.accent else look.muted)
                it.setBounds(Math.round(x - size / 2), Math.round(y - size / 2), Math.round(x + size / 2), Math.round(y + size / 2))
                it.draw(canvas)
            }
        }
        // The pill, under its icon, with the action's name. The spent heart's is dimmer.
        val spent = action == Action.Favourited
        if (pill != null && action != null) {
            menuBox.set(pill.first, (height - pillHeight) / 2, pill.second, (height + pillHeight) / 2)
            menuPaint.color = GuideMotion.faded(look.accent, (if (spent) MENU_SPENT_PILL_ALPHA else MENU_PILL_ALPHA) * open)
            canvas.drawRoundRect(menuBox, pillHeight / 2, pillHeight / 2, menuPaint)
            canvas.save()
            canvas.clipRect(menuBox)
            pillText.color = GuideMotion.faded(look.accent, (if (spent) MENU_SPENT_ALPHA else 1f) * open)
            canvas.drawText(context.getString(label(action)), pill.first + dp(MENU_PILL_LEFT),
                height / 2 - (pillText.ascent() + pillText.descent()) / 2, pillText)
            canvas.restore()
        }
        val slide = (1f - menuShown) * dp(MENU_SLIDE_DP)
        val alpha = (menuShown * 255).toInt().coerceIn(0, 255)
        val top = (height - icon) / 2
        for ((index, slotAction) in slots.withIndex()) {
            if (slotAction == null) continue
            val column = MenuKeys.SAVE + index
            val (left, end) = menuColumn(column)
            var centre = (left + end) / 2 + slide
            // The screen's rounded corner comes in near the bottom right: the icon, and its pill, keep clear of it.
            val foot = navigationInset + height - (height + pillHeight) / 2
            val most = width - corners.inset(foot) - dp(MENU_CORNER_GAP) - icon / 2f - dp(MENU_PILL_RIGHT)
            if (centre > most) centre = most
            val inPill = pill != null && column == pillShown
            // The pill covers the buttons to its left while it shows.
            if (pill != null && !inPill && centre in pill.first..pill.second) continue
            val tint = when {
                inPill -> GuideMotion.faded(look.accent, if (spent) MENU_SPENT_ALPHA else 1f)
                slotAction == Action.Favourited -> GuideMotion.faded(look.accent, MENU_SPENT_ICON_ALPHA)
                pill != null -> look.muted
                else -> look.ink
            }
            context.getDrawable(icon(slotAction))?.mutate()?.let { drawable ->
                drawable.setTint(tint)
                drawable.alpha = alpha
                val x = Math.round(centre - icon / 2f)
                val y = Math.round(top)
                drawable.setBounds(x, y, x + icon, y + icon)
                drawable.draw(canvas)
            }
        }
        if (more || pillOpen.moving(now, glide) || pillLeft.moving(now, glide) || pillRight.moving(now, glide) || wordEase.moving(now, glide))
            postInvalidateOnAnimation()
    }

    /** The menu's button under a touch at [x], or null. */
    private fun menuActionAt(x: Float): Action? {
        val menu = notice as? Notice.Menu ?: return null
        for ((index, action) in menuSlots(menu).withIndex()) {
            val (left, right) = menuColumn(MenuKeys.SAVE + index)
            if (x in left..right) return action
        }
        return null
    }

    /** A touch that went down on one of the menu's buttons, which picks it if it lifts there too. */
    private var menuTouched: Action? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (notice !is Notice.Menu) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> menuTouched = menuActionAt(event.x)
            MotionEvent.ACTION_UP -> {
                val action = menuTouched
                menuTouched = null
                if (action != null && menuActionAt(event.x) == action) act(action)
            }
            MotionEvent.ACTION_CANCEL -> menuTouched = null
        }
        return true
    }

    /** The touch on a bar word came from the screen itself, so holding it may open its menu. */
    private var heldOnScreen = false

    /** The word whose menu shows now, so the buttons move in only as a menu opens. */
    private var shownMenu: String? = null

    private fun icon(action: Action) = when (action) {
        Action.Save -> R.drawable.menu_save
        Action.Favourite -> R.drawable.menu_favourite
        Action.Favourited -> R.drawable.menu_favourited
        Action.Unfavourite -> R.drawable.menu_unfavourite
        Action.Unsave -> R.drawable.menu_unsave
        Action.Forget -> R.drawable.menu_forget
        else -> R.drawable.panel_back
    }

    /** The words are covered while a gesture shows, so a touch must not choose one that nobody can see. */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // A touch on a demonstration only ends it, so that it never presses a button that the demonstration drew.
        if (demo != null) {
            stopDemo()
            return true
        }
        // A touch on the idle game only sends the letters home: nothing is picked.
        if (idle != null) {
            woke()
            return true
        }
        // A touch on one of the menu's buttons is the bar's, which draws them. Beside them it closes the menu, as before.
        if (event.actionMasked == MotionEvent.ACTION_DOWN && menuActionAt(event.x) != null) return true
        return gesture.showing || super.onInterceptTouchEvent(event)
    }

    // The idle games, an Easter egg: see [IdlePlayer]. They have no touch targets. Coming back sends the letters home, and typing or
    // swiping cuts straight back to the words.

    private val idleDrawing = IdleDrawing()
    private var idle: IdlePlayer? = null
    private var idleFrameAt = 0L
    private val idleFrame = Runnable { invalidate() }
    /** How many of the bar's pixels make one of the games' units: they are made for a bar 88 high. */
    private val idleScale get() = dp(56) / Idle.H
    /** How far the game keeps from each side, in pixels, so that the screen's rounded corners cut nothing, down to its floor. */
    private var idleEdge = 0
    /** A game ended by itself, and the bar shows its words again: the next idle wait starts. */
    var idleEnded: () -> Unit = {}

    val idling get() = idle != null

    /**
     * Starts an idle game with the words on the bar, if plain words show: [kind], or one picked by rarity among the games the
     * letters fit and [schedule] allows. Returns false if there is nothing to play with, or the letters do not fit [kind].
     */
    internal fun startIdle(random: kotlin.random.Random, kind: IdlePlayer.Kind? = null, schedule: IdleSchedule? = null): Boolean {
        if (idle != null || !isShown || !showSuggestions || modifiers?.replacesWords == true || notice != null || clip != null || selected != null
            || gesture.showing) return false
        val scale = idleScale
        // The floor of the game, the bottom of its letters, is this far over the bottom of the screen; the corners curve in there.
        idleEdge = corners.inset((height - Idle.H * scale).coerceAtLeast(0f)) + dp(2)
        val glyphs = ArrayList<Glyph>()
        // The words left to right, in the order of the bar, so that word 0, 1 and 2 are left, middle and right.
        val views = wordViews.filter { it.visibility == VISIBLE && it.text.isNotEmpty() && it.layout != null }.sortedBy { it.left }
        for ((word, view) in views.withIndex()) {
            val layout = view.layout
            val paint = view.paint
            // An ellipsized word gives only the letters that show.
            val shown = if (layout.getEllipsisCount(0) > 0) layout.getEllipsisStart(0) else view.text.length
            val middle = view.top + view.totalPaddingTop + layout.getLineBaseline(0) + (paint.ascent() + paint.descent()) / 2
            for (i in 0 until shown) {
                val char = view.text[i]
                if (char.isWhitespace()) continue
                val left = view.left + view.totalPaddingLeft + layout.getPrimaryHorizontal(i) - view.scrollX
                val advance = paint.measureText(view.text, i, i + 1)
                glyphs += Glyph(char, word, (left + advance / 2 - idleEdge) / scale, middle / scale, advance / scale, paint.textSize / scale)
            }
        }
        if (glyphs.isEmpty()) return false
        // The automatic capital's arrow plays too, first in the left word, and comes home to its own place.
        if (autoCapital) {
            val (size, line) = placeCapital()
            idleDrawing.arrowLine = line / scale
            glyphs.add(0, Glyph(Idle.ARROW, 0, (capitalBox.centerX() - idleEdge) / scale, capitalBox.centerY() / scale, size / scale,
                wordViews.first().paint.textSize / scale, arrow = true))
        }
        val game = kind?.takeIf { IdlePlayer.fits(it, glyphs) }
            ?: (if (kind == null) IdlePlayer.choose(glyphs, random) { schedule?.allows(it) != false } else null) ?: return false
        schedule?.played(game)
        idle = IdlePlayer(glyphs, (width - 2 * idleEdge) / scale, random, game)
        idleFrameAt = SystemClock.uptimeMillis()
        render()
        invalidate()
        return true
    }

    /** You came back: the letters go home, by the game's own way. Typing or swiping still cuts to the words at once with [stopIdle]. */
    internal fun comeBackFromIdle(way: IdlePlayer.Way? = null): Boolean {
        val player = idle ?: return false
        player.comeBack(way)
        return true
    }

    /** Ends the idle game at once, and the bar is as it was. */
    fun stopIdle() {
        if (idle == null) return
        idle = null
        removeCallbacks(idleFrame)
        render()
        invalidate()
    }

    /** Moves the idle game on to now and draws it. Android's animation speed sets its pace; with animations off it ends. */
    private fun drawIdle(canvas: Canvas, player: IdlePlayer) {
        val now = SystemClock.uptimeMillis()
        val speed = ValueAnimator.getDurationScale()
        val dt = (now - idleFrameAt).coerceIn(0, IDLE_LONGEST_FRAME).toFloat()
        idleFrameAt = now
        if (speed <= 0f || !player.update(dt / speed)) {
            post { if (idle === player) { stopIdle(); idleEnded() } }
            return
        }
        canvas.save()
        canvas.translate(idleEdge.toFloat(), 0f)
        idleDrawing.draw(canvas, player, idleScale, wordViews.first().paint, look)
        canvas.restore()
        postDelayed(idleFrame, IDLE_FRAME_MILLIS)
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        render()
    }

    // The guide's demonstrations on the bar itself: see [GuideHooks.StripDemo]. They borrow the words' places for a few seconds,
    // type nothing and take no touch, and the bar is as it was when one ends.

    private var demo: GuideHooks.StripDemo? = null
    private var demoWords: List<String>? = null
    private var demoNotice: Notice? = null
    private var demoStart = 0L
    /** The ghost finger on the middle word, from 0 (gone) to 1, and how far its hold has run. */
    private var fingerAlpha = 0f
    private var fingerHold = 0f
    private val fingerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val demoFrame = object : Runnable {
        override fun run() = stepDemo()
    }

    /** Plays [next] from its start, in place of whatever demonstration was playing. */
    internal fun playDemo(next: GuideHooks.StripDemo) {
        if (!isShown || !showSuggestions || modifiers?.replacesWords == true || idle != null) return
        stopDemo()
        demo = next
        demoStart = SystemClock.uptimeMillis()
        stepDemo()
    }

    /** Ends the demonstration, if one plays, and the bar shows its own words again. */
    fun stopDemo() {
        if (demo == null) return
        demo = null
        demoWords = null
        demoNotice = null
        fingerAlpha = 0f
        removeCallbacks(demoFrame)
        middleWord().animate().cancel()
        middleWord().alpha = 1f
        middleWord().translationY = 0f
        render()
        invalidate()
    }

    /** The word in the middle of the bar, the best one, where both demonstrations act. */
    private fun middleWord() = wordViews[WORD_ORDER.indexOf(0)]

    private fun stepDemo() {
        val playing = demo ?: return
        val time = ((SystemClock.uptimeMillis() - demoStart) / demoScale()).toLong()
        when (playing) {
            is GuideHooks.StripDemo.Fix -> {
                // The word with the slip, then Space: the fix rises into its place, as autocorrect does it.
                val fixed = time >= FIX_SWAP_MILLIS
                val words = listOf(if (fixed) playing.fix else playing.typo)
                if (words != demoWords) {
                    demoWords = words
                    render()
                    if (fixed && GuideMotion.enabled) {
                        middleWord().alpha = 0f
                        middleWord().translationY = dp(8).toFloat()
                        middleWord().animate().alpha(1f).translationY(0f).setDuration(FIX_RISE_MILLIS).start()
                    }
                }
                if (time >= FIX_END_MILLIS) return stopDemo()
            }
            is GuideHooks.StripDemo.Menu -> {
                // The bar's own words, or the ones given: a finger comes down on the middle one and stays, the menu opens, then
                // the finger lifts. With no words on the bar there's nothing to hold, so nothing plays.
                val shown = playing.words.ifEmpty { words.take(MENU_WORDS) }
                if (shown.isEmpty()) return stopDemo()
                if (demoWords != shown) {
                    demoWords = shown
                    render()
                }
                fingerAlpha = when {
                    time < MENU_PRESS_MILLIS -> 0f
                    time < MENU_PRESS_MILLIS + FINGER_FADE_MILLIS -> (time - MENU_PRESS_MILLIS).toFloat() / FINGER_FADE_MILLIS
                    time < MENU_OPEN_MILLIS + FINGER_FADE_MILLIS -> 1f
                    time < MENU_OPEN_MILLIS + 2 * FINGER_FADE_MILLIS -> 1f - (time - MENU_OPEN_MILLIS - FINGER_FADE_MILLIS).toFloat() / FINGER_FADE_MILLIS
                    else -> 0f
                }
                fingerHold = ((time - MENU_PRESS_MILLIS).toFloat() / (MENU_OPEN_MILLIS - MENU_PRESS_MILLIS)).coerceIn(0f, 1f)
                // The guide's slide about the menu shows only the hold: the menu itself opens only for a real hold.
                val menu = if (time >= MENU_OPEN_MILLIS && playing.opens) Notice.Menu(shown.first(), true) else null
                if (menu != demoNotice) {
                    demoNotice = menu
                    render()
                }
                invalidate()
                if (time >= MENU_END_MILLIS) return stopDemo()
            }
        }
        postDelayed(demoFrame, DEMO_FRAME_MILLIS)
    }

    private fun demoScale() = if (GuideMotion.enabled) ValueAnimator.getDurationScale().coerceAtLeast(.01f) else 1f

    /** A soft dot on the middle word with a ring that closes in while it holds, like the guide's ghost finger. */
    private fun drawDemoFinger(canvas: Canvas) {
        if (demo !is GuideHooks.StripDemo.Menu || fingerAlpha <= 0f) return
        val view = middleWord()
        val x = view.left + view.width / 2f
        val y = height / 2f - (paddingBottom - paddingTop) / 2f
        val radius = dp(9).toFloat()
        fingerPaint.style = android.graphics.Paint.Style.FILL
        fingerPaint.color = GuideMotion.faded(look.accent, .35f * fingerAlpha)
        canvas.drawCircle(x, y, radius, fingerPaint)
        fingerPaint.style = android.graphics.Paint.Style.STROKE
        fingerPaint.strokeWidth = dp(2).toFloat()
        fingerPaint.color = GuideMotion.faded(look.accent, fingerAlpha)
        canvas.drawCircle(x, y, radius * (2.6f - 1.6f * fingerHold), fingerPaint)
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    companion object {
        private val WORD_ORDER = listOf(1, 0, 2)
        /** How far, in dp, and how quickly a word that the guide points at wiggles. */
        private const val POINT_DP = 3
        private const val POINT_MILLIS = 650L
        /** The words' size, in sp, and the room on each side of a word. */
        private const val WORD_SIZE = 17f
        private const val WORD_PADDING = 6
        /** A little room, in dp, that a fitted word keeps on each side, so that Android never has to cut it. */
        private const val WORD_SLACK = 2
        /** The mark for the automatic capital: its text, its size in sp, and where it sits, in dp. */
        /** How far the arrow keeps from the chip's top and bottom, and from its sides, and its line. */
        private const val CAPITAL_ARROW_IN = 4
        private const val CAPITAL_SIDE_IN = 5
        private const val CAPITAL_LINE = 1.3f
        /** How far the left word's text must stay from the chip, or the chip goes into the corner, at this size. */
        private const val CAPITAL_CLEAR = 2
        private const val CAPITAL_SMALL = .6f
        /** About 30 frames a second while the idle scene moves. */
        private const val IDLE_FRAME_MILLIS = 33L
        /** A frame that comes late, as after the screen was busy, moves the game on by no more than this. */
        private const val IDLE_LONGEST_FRAME = 50L
        // The guide's demonstrations on the bar, in ms from their start. Android's animator duration setting scales them.
        private const val DEMO_FRAME_MILLIS = 33L
        private const val FIX_SWAP_MILLIS = 600L
        private const val FIX_RISE_MILLIS = 220L
        private const val FIX_END_MILLIS = 2200L
        private const val MENU_PRESS_MILLIS = 500L
        private const val MENU_OPEN_MILLIS = 1300L
        private const val FINGER_FADE_MILLIS = 150L
        private const val MENU_END_MILLIS = 3600L
        private const val MENU_WORDS = 3
        /** In logcat, a hold on a bar word and whether its menu opened. */
        private const val HOLD_TAG = "TitanKeysBar"
        /** How long the side words take to slide back up. */
        private const val SIDES_MILLIS = 260L
        /** The menu's word, small above its buttons: its size in sp and its top in dp. */
        private const val MENU_WORD_SIZE = 11f
        private const val MENU_WORD_ABOVE = false
        /** The menu's icons, as a part of the words' size, and how far and how long its buttons move in. */
        private const val MENU_ICON_SCALE = 1.2f
        /** The size of a button's name under its icon, in sp, the gap between them, and the room between the word and Save. */
        /** The menu's icons, in dp. */
        private const val MENU_ICON_DP = 22
        /**
         * A hovered button's pill, as the owner's design D draws it: its height in dp, its name's size as a part of the word's,
         * the room at its left and right ends and between the name and the icon, in dp, and its strength. The spent heart's pill,
         * name and icon are dimmer. The pill grows and the word slides over [MENU_GLIDE_MILLIS].
         */
        private const val MENU_PILL_DP = 42
        private const val MENU_PILL_TEXT = .88f
        private const val MENU_PILL_LEFT = 18
        private const val MENU_PILL_RIGHT = 11
        private const val MENU_PILL_GAP = 12
        private const val MENU_PILL_ALPHA = .24f
        private const val MENU_SPENT_PILL_ALPHA = .12f
        private const val MENU_SPENT_ALPHA = .6f
        private const val MENU_SPENT_ICON_ALPHA = .35f
        private const val MENU_GLIDE_MILLIS = 140L
        /**
         * The badge after the word: its size and its gap from the word in dp, how high its centre sits as a part of the letters'
         * height, and its pop as it changes, how much bigger at most and for how long.
         */
        private const val MENU_BADGE_DP = 14
        private const val MENU_BADGE_GAP = 8
        private const val MENU_BADGE_HEIGHT = .55f
        private const val MENU_BADGE_POP = .35f
        private const val MENU_BADGE_POP_MILLIS = 260L
        /** How far the menu keeps from a rounded corner, in dp. */
        private const val MENU_CORNER_GAP = 4
        private const val MENU_LABEL_GAP = 2
        private const val MENU_WORD_GAP = 8
        private const val MENU_SLIDE_DP = 40
        private const val MENU_SLIDE_MILLIS = 220L
        private const val MENU_WORD_TOP = 2
        /** Enough of the clip for one line. The line itself cuts it cleanly where it ends. */
        private const val CLIP_PREVIEW_LENGTH = 200
        /** The clipboard icon beside a clip, as a part of the text size. */
        private const val CLIP_ICON_SCALE = 1.1f
        /** Save, Forget and Back. */
        private const val MAX_BUTTONS = 3
        private const val CHIP_SIZE = 13f
        /** The gap after each modifier chip, in dp. */
        private const val CHIP_GAP = 6
        private const val CONTENT_ABOVE = 16
        private const val HINT_SMALLEST = 8f
        private const val HINT_LARGEST = 17f
    }
}

/** The strip's action for a button of the word menu. */
internal val MenuKeys.Button.action: Strip.Action
    get() = when (this) {
        MenuKeys.Button.Save -> Strip.Action.Save
        MenuKeys.Button.Favourite -> Strip.Action.Favourite
        MenuKeys.Button.Favourited -> Strip.Action.Favourited
        MenuKeys.Button.Unfavourite -> Strip.Action.Unfavourite
        MenuKeys.Button.Unsave -> Strip.Action.Unsave
        MenuKeys.Button.Forget -> Strip.Action.Forget
    }
