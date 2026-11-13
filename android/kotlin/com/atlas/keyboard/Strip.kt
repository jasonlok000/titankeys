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
         * The menu of a held word, at [step]: see [MenuKeys.State]. [forget] is true for a word the keyboard learned from your
         * typing, which O can forget while it isn't saved; [builtIn] for a dictionary word, which counts as saved; [savable] is
         * false for a word the store can't hold, such as an emoji. Its I and O buttons are [first] and [second], and P is Back.
         */
        data class Menu(
            override val word: String,
            val forget: Boolean,
            val step: MenuKeys.Step = MenuKeys.Step.New,
            val builtIn: Boolean = false,
            val savable: Boolean = true,
            val oneLetter: Boolean = false,
        ) : Notice {
            val state get() = MenuKeys.State(step, forget, builtIn, savable, oneLetter)
            val first get() = MenuKeys.choices(state).i?.action
            val second get() = MenuKeys.choices(state).o?.action
        }
        data class Forgot(override val word: String) : Notice
        /** A second of fun when the keyboard has had to hide and show itself for Microsoft Teams. It has no buttons. */
        object TeamsEgg : Notice {
            override val word = ""
        }
    }

    /** Ink's font, as everywhere in TitanKeys (owner, 2026-11-12), and its medium weight for the word menu's pills. */
    private val typeface = resources.getFont(R.font.inter_regular)
    private val boldTypeface = resources.getFont(R.font.inter_medium)
    private val wordViews = WORD_ORDER.map { index -> item({ choose(index) }, { hold(index) }) }
    /** The word that the field has selected, alone in the middle. A touch or a hold opens its menu. */
    private val selectedView = item({ act(Action.OpenSelected) }, { act(Action.OpenSelected); true })
    private val noticeLabel = TextView(context).apply {
        typeface = this@Strip.typeface
        textSize = NOTICE_SIZE
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
            textSize = NOTICE_SIZE
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
        textSize = SMALLEST_TEXT
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
            // While the guide changes slides, the words wait and show once, settled: see [holdWords].
            if (SystemClock.uptimeMillis() < wordsHeldUntil) return
            render()
        }

    /** Until when the words wait, and the redraw that shows them after. Both start unset, so the init may run before them. */
    private var wordsHeldUntil = 0L
    private var wordsRelease: Runnable? = null

    /**
     * The words don't change on the screen for [millis]; then the latest show, once. The guide's slide change sets the field a few
     * times over, and the bar showed each of them, five sets in a blink (owner, v5.3).
     */
    fun holdWords(millis: Long) {
        wordsHeldUntil = SystemClock.uptimeMillis() + millis
        wordsRelease?.let(::removeCallbacks)
        wordsRelease = Runnable { render() }.also { postDelayed(it, millis) }
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

    // Everything that init can reach comes before it: Kotlin sets properties top to bottom, so one declared below init is still
    // null, or 0, while init's render runs, and the keyboard crashed opening (2026-11-12). tools/checks/init-order.test.ts checks it.
    private val measurePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

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

    /** How far in the menu's buttons are, from 0 as it opens to 1. */
    private var menuShown = 1f

    // The word menu's state comes before init: init renders, and the menu's drawing reads these.
    private val pillLeft = Ease()
    private val pillRight = Ease()
    private val pillOpen = Ease()
    /** The column whose pill shows or is shrinking away, and the action it shows. */
    private var pillShown = -1
    private var pillAction: Action? = null
    /** The hovered button's action changed under the finger: the pill takes its new size at once. */
    private var pillSnap = false
    /** Where the word's centre is, set once as the menu opens: it never moves. NaN until then. */
    private var menuWordCentre = Float.NaN
    /** The step the menu shows, and the animation that its last change plays, from when. See [MenuKeys.motion]. */
    private var shownStep: MenuKeys.Step? = null
    private var menuMotion: MenuKeys.Motion? = null
    /**
     * I's and O's icons, and the icons they are changing from, since when: no icon snaps to another, see [MenuMotion.icon]. Each
     * as the menu's buttons, not the strip's actions.
     */
    private val slotIcons = arrayOfNulls<MenuKeys.Button>(2)
    private val slotFrom = arrayOfNulls<MenuKeys.Button>(2)
    private val slotChanged = LongArray(2)
    /** The word as the menu last drew it, for Forget's dust: its text, left, baseline and width, in pixels. */
    private var drawnWord: String? = null
    private var drawnLeft = 0f
    private var drawnBaseline = 0f
    private var drawnWidth = 0f
    /** Forget's dust, from when, over the word that was drawn: see [drawDust]. */
    private var dustStart = -1L
    private var dustWord: String? = null
    private var motionStart = 0L
    /** When the menu came to rest at its step, for the ambient marks, or -1 while it hasn't: see [MenuMotion.ambient]. */
    private var ambientStart = -1L
    private val crackPath = android.graphics.Path()

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
        val sizes = WORD_ORDER.map { wordSize(it) / WORD_SIZE }
        val fits = if (room > 0) StripFit.fit(shown, room, wordRoomPadding(), ::measureWord, lend = false, sizes = sizes) else null
        for ((slot, view) in wordViews.withIndex()) {
            val index = WORD_ORDER[slot]
            // While the idle scene plays, it draws the letters itself, and the words keep their places underneath.
            view.visibility = if (showSuggestions && bar == null && confirm == null && offer == null && chosen == null)
                (if (idle != null || !sides && index != 0) INVISIBLE else VISIBLE) else GONE
            val fit = fits?.get(slot)
            view.text = fit?.text ?: shown[slot]
            // The middle word is the strongest: bigger, and in Ink's medium weight. The side words are a step down (owner,
            // 2026-11-13: "text size should convey hierarchy").
            view.typeface = if (index == 0) boldTypeface else typeface
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, wordPixels() * sizes[slot] * (fit?.scale ?: 1f))
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

    /** The size of word [index], 0 for the best one in the middle, in sp. */
    private fun wordSize(index: Int) = if (index == 0) WORD_SIZE else SIDE_WORD_SIZE

    /** How wide a word is at the words' full size. */
    private fun measureWord(text: String): Float {
        // In the middle word's weight, the widest of the three, so a fitted word never runs over.
        measurePaint.typeface = boldTypeface
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
        drawDust(canvas)
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
     * that the button does now, so after a press it shows the next step's, in place.
     */
    var menuMark = -1
        set(value) {
            if (field == value) return
            field = value
            render()
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
            // Forget closes the menu on its notice: the word dissolves into dust first, and the notice fades in after it.
            if (shown is Notice.Forgot && shownMenu == shown.word && drawnWord != null && GuideMotion.enabled) {
                dustStart = SystemClock.uptimeMillis()
                dustWord = drawnWord
                noticeLabel.alpha = 0f
                noticeLabel.animate().alpha(1f).setStartDelay(MenuMotion.millis(MenuKeys.Motion.Forget) / 2)
                    .setDuration(MENU_GLIDE_MILLIS * 2).start()
            }
            pillShown = -1
            menuWordCentre = Float.NaN
            shownStep = null
            menuMotion = null
            drawnWord = null
            slotIcons.fill(null)
        } else if (shownMenu != menu.word || shownStep != menu.step) {
            // A new menu, or a new step: its animation plays once, unless motion is off. A new word gets a new place, and its
            // icons come as they are; a new step changes them gently.
            val opened = shownMenu != menu.word
            if (opened) menuWordCentre = Float.NaN
            menuMotion = MenuKeys.motion(if (opened) null else shownStep, menu.step)?.takeIf { GuideMotion.enabled }
            val now = SystemClock.uptimeMillis()
            motionStart = now
            ambientStart = -1L
            shownStep = menu.step
            val buttons = MenuKeys.choices(menu.state).let { arrayOf(it.i, it.o) }
            for (index in 0..1) {
                if (!opened && slotIcons[index] != buttons[index]) {
                    slotFrom[index] = slotIcons[index]
                    slotChanged[index] = now
                } else if (opened) slotChanged[index] = 0L
                slotIcons[index] = buttons[index]
            }
        } else {
            // The same step: only O's Forget can come or go, as Save and Unsave change what is left to forget.
            val second = MenuKeys.choices(menu.state).o
            if (slotIcons[1] != second) {
                slotFrom[1] = slotIcons[1]
                slotChanged[1] = SystemClock.uptimeMillis()
                slotIcons[1] = second
            }
        }
        shownMenu = menu?.word
        invalidate()
    }

    private var menuIn: ValueAnimator? = null

    /** The menu's buttons, over I's, O's and P's columns. A word that no list can drop has no Forget. */
    private fun menuSlots(menu: Notice.Menu): List<Action?> = listOf(menu.first, menu.second, Action.Close)

    /** The left and right of a key column on this bar, in its own pixels: the Sym panel's columns. See [MenuKeys.slot]. */
    private fun menuColumn(column: Int) = MenuKeys.slot(width.toFloat(), column)

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
     * The word's menu, drawn on the bar (the owner's design, 2026-11-12). The word sits centred in the room left of the buttons,
     * placed once as the menu opens: it never moves. An icon sits over each button's key. A finger hovering I's or O's button
     * stretches its orange tile left into a pill that holds the action's name; the pill covers the buttons to its left, and slides
     * over the end of a long word, which fades under its leading edge. Back has no pill. A change of step plays a little
     * animation past the word, see [MenuMotion], and the buttons show the step. Nothing comes near the screen's rounded corners.
     */
    private fun drawMenu(canvas: Canvas) {
        val menu = notice as? Notice.Menu ?: return
        if (noticeRow.visibility != VISIBLE || width == 0) return
        val now = SystemClock.uptimeMillis()
        val glide = if (GuideMotion.enabled) MENU_GLIDE_MILLIS else 0L
        val height = dp(56).toFloat()
        // The real keys' left edge, as calibrated: the word keeps over the keys, as the buttons do.
        val keySide = MenuKeys.slot(width.toFloat(), 0).first
        val icon = dp(MENU_ICON_DP)
        val slots = menuSlots(menu)
        var more = false
        // The pill shows the action that the hovered button does now. After a press under a finger that stays, it shows the
        // next step's action in place, at once: no shrinking and growing again.
        val hovered = menuMark.takeIf { MenuKeys.pills(it, menu.state) } ?: -1
        if (hovered >= 0) {
            val action = slots[hovered - MenuKeys.SAVE]
            if (hovered == pillShown && action != pillAction) pillSnap = true
            pillShown = hovered
            pillAction = action
        }
        val open = pillOpen.at(if (hovered >= 0) 1f else 0f, now, glide)
        if (open <= 0f) pillShown = -1
        // Ink's font, in its own medium weight, as Ink's headings use it: no faked bold.
        pillText.typeface = boldTypeface
        pillText.textSize = wordPixels() * MENU_PILL_TEXT
        val pillHeight = dp(MENU_PILL_DP).toFloat()
        var pill: Pair<Float, Float>? = null
        val action = pillAction.takeIf { pillShown >= 0 }
        if (action != null) {
            val name = context.getString(label(action))
            val (left, right) = MenuKeys.pill(width.toFloat(), pillShown, pillText.measureText(name), icon.toFloat(),
                dp(MENU_PILL_LEFT).toFloat(), dp(MENU_PILL_GAP).toFloat(), dp(MENU_PILL_RIGHT).toFloat())
            if (pillSnap) {
                pillLeft.jump(left)
                pillRight.jump(right)
                pillSnap = false
            }
            // The pill grows out of its icon's own tile, and shrinks back into it.
            val shownRight = pillRight.at(right, now, glide)
            val tile = shownRight - icon - 2f * dp(MENU_PILL_RIGHT)
            pill = tile + (pillLeft.at(left, now, glide) - tile) * open to shownRight
        } else {
            pillLeft.jump(Float.NaN)
            pillRight.jump(Float.NaN)
        }
        // The word, placed once in the room left of the buttons. A long word loses its middle to fit that room. It is always the
        // text's colour; an animation may only pop it a little or shake it, and Forget dissolves it.
        menuWordText.typeface = typeface
        menuWordText.textSize = wordPixels()
        menuWordText.color = look.ink
        menuWordText.textAlign = android.graphics.Paint.Align.LEFT
        val baseline = height / 2 - (menuWordText.ascent() + menuWordText.descent()) / 2
        val wordFoot = navigationInset + height - (baseline + menuWordText.descent())
        val wordLeft = maxOf(side.toFloat(), keySide, corners.inset(wordFoot).toFloat() + dp(MENU_CORNER_GAP))
        val wordRight = MenuKeys.wordRight(width.toFloat(), dp(MENU_WORD_GAP).toFloat())
        if (menuWordCentre.isNaN()) menuWordCentre = MenuKeys.wordCentre(wordLeft, wordRight)
        val text = MenuKeys.shorten(menu.word) { menuWordText.measureText(it) <= (wordRight - wordLeft).coerceAtLeast(0f) }
        val textWidth = menuWordText.measureText(text)
        val textLeft = menuWordCentre - textWidth / 2
        drawnWord = text
        drawnLeft = textLeft
        drawnBaseline = baseline
        drawnWidth = textWidth
        val density = resources.displayMetrics.density
        val playing = menuMotion
        val frame = if (playing == null) null else MenuMotion.frame(playing, now - motionStart, wordBox(text, textLeft, textWidth, baseline, density))
        if (frame != null && frame.sprites.isEmpty() && now - motionStart >= MenuMotion.millis(playing!!)) menuMotion = null
        else if (frame != null) more = true
        // Hearts burst from behind the word: they draw first.
        frame?.sprites?.filter { it.behind }?.forEach { drawSprite(canvas, it, density) }
        canvas.save()
        if (frame != null) {
            canvas.translate(frame.wordShift * density, frame.wordDrop * density)
            canvas.scale(frame.wordScale, frame.wordScale, menuWordCentre, baseline + (menuWordText.ascent() + menuWordText.descent()) / 2)
            // Unsave dims the word for a moment; it stays the text's colour.
            menuWordText.alpha = (frame.wordAlpha * 255).toInt().coerceIn(0, 255)
        }
        canvas.drawText(text, textLeft, baseline, menuWordText)
        menuWordText.alpha = 255
        canvas.restore()
        frame?.sprites?.filterNot { it.behind }?.forEach { drawSprite(canvas, it, density) }
        // At rest, the ambient status: a faint heart now and then by a favourite, a faint sparkle by a saved word. It waits while a
        // button is hovered, and starts over after.
        var ambientWait = Long.MAX_VALUE
        if (menuMotion == null) {
            if (pillShown >= 0) ambientStart = -1L
            else {
                if (ambientStart < 0) ambientStart = now
                val still = !GuideMotion.enabled
                val box = wordBox(text, textLeft, textWidth, baseline, density)
                for (sprite in MenuMotion.ambient(menu.step, now - ambientStart, box, still)) drawSprite(canvas, sprite, density)
                ambientWait = MenuMotion.ambientWait(menu.step, now - ambientStart, still)
            }
        }
        // The pill, under its icon, with the action's name. It covers what it slides over: a long word's end fades under it.
        val spent = action == Action.Favourited
        if (pill != null && action != null) {
            menuBox.set(pill.first, (height - pillHeight) / 2, pill.second, (height + pillHeight) / 2)
            if (textLeft + textWidth > pill.first - dp(MENU_PILL_FADE)) {
                val fade = dp(MENU_PILL_FADE).toFloat()
                menuPaint.shader = android.graphics.LinearGradient(pill.first - fade, 0f, pill.first + pillHeight / 2, 0f,
                    look.paper and 0x00FFFFFF, look.paper, android.graphics.Shader.TileMode.CLAMP)
                canvas.drawRect(pill.first - fade, 0f, pill.first + pillHeight / 2, height, menuPaint)
                menuPaint.shader = null
                menuPaint.color = look.paper
                canvas.drawRoundRect(menuBox, pillHeight / 2, pillHeight / 2, menuPaint)
            }
            menuPaint.color = GuideMotion.faded(look.accent, (if (spent) MENU_SPENT_PILL_ALPHA else MENU_PILL_ALPHA) * open)
            canvas.drawRoundRect(menuBox, pillHeight / 2, pillHeight / 2, menuPaint)
            canvas.save()
            canvas.clipRect(menuBox)
            pillText.color = GuideMotion.faded(look.accent, (if (spent) MENU_SPENT_ALPHA else 1f) * open)
            // The name keeps its place by the icon, and the growing pill uncovers it: it never slides into the icon.
            val name = context.getString(label(action))
            canvas.drawText(name, MenuKeys.pillLabel(pill.second, pillText.measureText(name), icon.toFloat(), dp(MENU_PILL_GAP).toFloat(),
                dp(MENU_PILL_RIGHT).toFloat()),
                height / 2 - (pillText.ascent() + pillText.descent()) / 2, pillText)
            canvas.restore()
        }
        val slide = (1f - menuShown) * dp(MENU_SLIDE_DP)
        val alpha = (menuShown * 255).toInt().coerceIn(0, 255)
        val top = (height - icon) / 2
        for ((index, slotAction) in slots.withIndex()) {
            // A button that just went still fades out: nothing snaps away.
            if (slotAction == null && !(index < 2 && slotChanged[index] > 0L)) continue
            val column = MenuKeys.SAVE + index
            val (left, end) = menuColumn(column)
            // On its key's centre, a key apart (owner, 2026-11-12): Back sits on Backspace's centre and no longer dodges the
            // rounded corner. Back has no pill, and I's and O's pills end well clear of it.
            val centre = (left + end) / 2 + slide
            val inPill = pill != null && column == pillShown
            // The pill covers the buttons to its left while it shows.
            if (pill != null && !inPill && centre in pill.first..pill.second) continue
            // Orange means on or touched, as Settings' switches (owner, 2026-11-12): a hovered button inside its pill, and the
            // filled heart of a favourite. Every other icon is the text's colour, and grey beside a pill. No icon snaps to another:
            // it morphs, see [MenuMotion.icon].
            val layers = if (index < 2 && slotChanged[index] > 0L) {
                val elapsed = now - slotChanged[index]
                if (elapsed < MenuMotion.ICON_MILLIS) more = true else slotChanged[index] = 0L
                MenuMotion.icon(slotFrom[index], slotIcons[index], elapsed, GuideMotion.enabled)
            } else null
            if (layers == null) {
                val action = slotAction ?: continue
                drawIcon(canvas, icon(action), centre, top, icon, 1f, 0f, alpha,
                    if (inPill || action == Action.Favourited) look.accent else if (pill != null) look.muted else look.ink, 1f)
            } else for (layer in layers) {
                val button = layer.button ?: continue
                val rest = if (pill != null && !inPill) look.muted else look.ink
                val tint = if (inPill) look.accent else blend(rest, look.accent, layer.orange)
                drawIcon(canvas, icon(button.action), centre, top + layer.drop * density, icon, layer.scale, 0f,
                    (alpha * layer.alpha).toInt(), tint, layer.fill)
            }
        }
        if (more || ambientWait == 0L || pillOpen.moving(now, glide) || pillLeft.moving(now, glide) || pillRight.moving(now, glide))
            postInvalidateOnAnimation()
        else if (ambientWait != Long.MAX_VALUE) postInvalidateDelayed(ambientWait)
    }

    /** [word] on the bar, as [MenuMotion] places its particles: its ends and the middle of its letters, in dp, and its seed. */
    private fun wordBox(word: String, left: Float, width: Float, baseline: Float, density: Float) = MenuMotion.Word(
        left / density, (left + width) / density, (baseline + (menuWordText.ascent() + menuWordText.descent()) / 2) / density,
        MenuMotion.Word.seed(word))

    /** One of a button's icons at [centre], scaled, [fill]ed from the bottom when less than 1, as [MenuMotion.icon] has it. */
    private fun drawIcon(canvas: Canvas, id: Int, centre: Float, top: Float, size: Int, scale: Float, turn: Float, alpha: Int,
                         tint: Int, fill: Float) {
        val drawable = context.getDrawable(id)?.mutate() ?: return
        drawable.setTint(tint)
        drawable.alpha = alpha.coerceIn(0, 255)
        val x = Math.round(centre - size / 2f)
        val y = Math.round(top)
        drawable.setBounds(x, y, x + size, y + size)
        canvas.save()
        canvas.scale(scale, scale, centre, top + size / 2f)
        if (turn != 0f) canvas.rotate(turn, centre, top + size / 2f)
        if (fill < 1f) canvas.clipRect(x.toFloat(), y + size * (1f - fill), (x + size).toFloat(), (y + size).toFloat())
        drawable.draw(canvas)
        canvas.restore()
    }

    /** [from] turned towards [to] by [part], from 0 to 1, channel by channel. */
    private fun blend(from: Int, to: Int, part: Float): Int {
        val p = part.coerceIn(0f, 1f)
        fun mix(shift: Int) = (((from shr shift) and 0xFF) * (1 - p) + ((to shr shift) and 0xFF) * p).toInt() shl shift
        return mix(24) or mix(16) or mix(8) or mix(0)
    }

    /**
     * One particle of an animation, always orange (owner, 2026-11-12): a heart, a bookmark, a sparkle, a shard or a speck of dust; the crack is
     * a gap in the bar's own colour, drawn down a heart as it breaks. A half is
     * the shape cut along the broken heart's crack, the same zigzag as Unfavourite's and Unsave's icons.
     */
    private fun drawSprite(canvas: Canvas, sprite: MenuMotion.Sprite, density: Float) {
        val size = sprite.size * density
        val x = sprite.x * density
        val y = sprite.y * density
        val alpha = (sprite.alpha * 255).toInt().coerceIn(0, 255)
        if (sprite.shape == MenuMotion.Shape.Dust) {
            menuPaint.shader = null
            menuPaint.color = look.accent
            menuPaint.alpha = alpha
            canvas.drawCircle(x, y, size / 2, menuPaint)
            menuPaint.alpha = 255
            return
        }
        if (sprite.shape == MenuMotion.Shape.Crack) {
            // The zigzag drawn down the heart as it breaks, as far as the sprite says: a gap in the bar's own colour.
            val left = x - size / 2
            val top = y - size / 2
            crackPath.reset()
            val points = CRACK.map { (px, py) -> left + px / 24f * size to top + py / 24f * size }
            val bottom = top + size * sprite.amount
            crackPath.moveTo(points[0].first, points[0].second)
            for ((index, point) in points.withIndex().drop(1)) {
                val (px, py) = point
                val (qx, qy) = points[index - 1]
                if (py <= bottom) crackPath.lineTo(px, py)
                else {
                    if (bottom > qy) crackPath.lineTo(qx + (px - qx) * (bottom - qy) / (py - qy), bottom)
                    break
                }
            }
            menuPaint.shader = null
            menuPaint.style = android.graphics.Paint.Style.STROKE
            menuPaint.strokeWidth = size * .1f
            menuPaint.strokeJoin = android.graphics.Paint.Join.ROUND
            menuPaint.color = look.paper
            canvas.drawPath(crackPath, menuPaint)
            menuPaint.style = android.graphics.Paint.Style.FILL
            return
        }
        if (sprite.shape == MenuMotion.Shape.Shard) {
            // A tiny splinter of the heart: a sharp orange triangle, turning as it falls.
            canvas.save()
            canvas.rotate(sprite.turn, x, y)
            crackPath.reset()
            crackPath.moveTo(x, y - size * .7f)
            crackPath.lineTo(x + size * .45f, y + size * .4f)
            crackPath.lineTo(x - size * .45f, y + size * .3f)
            crackPath.close()
            menuPaint.shader = null
            menuPaint.color = look.accent
            menuPaint.alpha = alpha
            canvas.drawPath(crackPath, menuPaint)
            menuPaint.alpha = 255
            canvas.restore()
            return
        }
        val drawable = context.getDrawable(when (sprite.shape) {
            MenuMotion.Shape.Heart, MenuMotion.Shape.HeartLeft, MenuMotion.Shape.HeartRight -> R.drawable.menu_favourited
            MenuMotion.Shape.Bookmark, MenuMotion.Shape.BookmarkLeft, MenuMotion.Shape.BookmarkRight -> R.drawable.menu_mark_saved
            else -> R.drawable.menu_sparkle
        })?.mutate() ?: return
        drawable.setTint(look.accent)
        drawable.alpha = alpha
        canvas.save()
        canvas.rotate(sprite.turn, x, y)
        val left = x - size / 2
        val top = y - size / 2
        drawable.setBounds(Math.round(left), Math.round(top), Math.round(left + size), Math.round(top + size))
        val half = when (sprite.shape) {
            MenuMotion.Shape.HeartLeft, MenuMotion.Shape.BookmarkLeft -> CRACK_LEFT
            MenuMotion.Shape.HeartRight, MenuMotion.Shape.BookmarkRight -> CRACK_RIGHT
            else -> null
        }
        if (half != null) {
            crackPath.reset()
            half.forEachIndexed { index, (px, py) ->
                val cx = left + px / 24f * size
                val cy = top + py / 24f * size
                if (index == 0) crackPath.moveTo(cx, cy) else crackPath.lineTo(cx, cy)
            }
            crackPath.close()
            canvas.clipPath(crackPath)
        }
        drawable.draw(canvas)
        canvas.restore()
    }

    /** Forget's dust: the forgotten word fades as it dissolves into orange specks, under the notice fading in. */
    private fun drawDust(canvas: Canvas) {
        val word = dustWord ?: return
        val elapsed = SystemClock.uptimeMillis() - dustStart
        if (elapsed >= MenuMotion.millis(MenuKeys.Motion.Forget)) {
            dustWord = null
            return
        }
        val density = resources.displayMetrics.density
        val frame = MenuMotion.frame(MenuKeys.Motion.Forget, elapsed, wordBox(word, drawnLeft, drawnWidth, drawnBaseline, density))
        menuWordText.color = look.ink
        menuWordText.alpha = (frame.wordAlpha * 255).toInt()
        canvas.drawText(word, drawnLeft, drawnBaseline, menuWordText)
        menuWordText.alpha = 255
        for (sprite in frame.sprites) drawSprite(canvas, sprite, density)
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
        // A touch on a demonstration ends it, and the bar's real words come back under the finger, so the touch goes on to them:
        // the guide's first hold on a word must open its menu, not only stop the demo (owner, v5: the first hold was blocked).
        // The buttons that the demonstration drew are gone by then, so none of them is pressed.
        if (demo != null) stopDemo()
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
        // Never over a selected word or an open menu: the demo would take their place (owner, v5).
        if (notice is Notice.Menu || selected != null) return
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
        /**
         * The words' size, in sp, which is dp at TitanKeys' fixed scale: the middle word's, the best one, and the side words', a
         * step down, so the sizes show which word is best and every one reads on a 9 cm screen (owner, 2026-11-13: "a bit too
         * small"). The room on each side of a word.
         */
        private const val WORD_SIZE = 20f
        private const val SIDE_WORD_SIZE = 18f
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
        private const val MENU_WORD_SIZE = 13f
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
        private const val MENU_GLIDE_MILLIS = 140L
        /** How far the pill's leading edge fades a long word that it slides over, in dp. */
        private const val MENU_PILL_FADE = 20
        /** The broken heart's crack, in its icon's 24 by 24 units: each half of a broken heart or bookmark is cut along it. */
        private val CRACK = listOf(12.3f to 0f, 12.3f to 6.6f, 10.4f to 10.4f, 13.6f to 12.6f, 11.6f to 17.4f, 11.6f to 24f)
        private val CRACK_LEFT = listOf(0f to 0f) + CRACK + listOf(0f to 24f)
        private val CRACK_RIGHT = listOf(24f to 0f) + CRACK + listOf(24f to 24f)
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
        private const val CHIP_SIZE = 14f
        /** The gap after each modifier chip, in dp. */
        private const val CHIP_GAP = 6
        private const val CONTENT_ABOVE = 16
        /** No text on the bar or the panels is smaller than this, in sp: real text at TitanKeys' fixed scale (owner, 2026-11-13). */
        const val SMALLEST_TEXT = 13f
        private const val HINT_SMALLEST = SMALLEST_TEXT
        private const val HINT_LARGEST = 18f
        /** "Forgot it" and its Undo, in sp: a step under the words. */
        private const val NOTICE_SIZE = 18f
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
