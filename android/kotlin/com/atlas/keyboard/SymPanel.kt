package com.atlas.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.atlas.engine.EmojiPages
import kotlin.math.min
import kotlin.math.sign

/**
 * The panel that Sym opens: a symbol or an emoji on each letter key of the top three rows but Q, A and P, in line with the physical
 * letter keys. P shows the position of the open page and only that, and the other keys are left empty. A finger that rests on a
 * letter key marks that key, as [Layout.restMarks] says, and the keys with an icon show no mark. The groups of symbols or emoji are
 * in a strip along the bottom, above the bottom row of physical keys, and a finger that moves along that row, or rests on it, opens
 * the group at its place.
 *
 * Alt's place shows the quick settings key and Sym's place shows what Sym does next, each as an icon. When the open group has
 * more than one page, the places of Backspace and Enter show an icon for the page that each key opens. On the quick settings page
 * each letter key that has an item shows its short label, dim when the setting is off, and a finger that rests on the key shows
 * the setting's name and state in the strip at the bottom. Every page is the same height, so the app above does not move.
 */
class SymPanel(
    context: Context,
    private val pick: (String) -> Unit,
    private val useQuickSetting: (QuickSetting) -> Unit,
    private val quickSettingOn: (QuickSetting) -> Boolean,
    private val pressAlt: () -> Unit,
    private val pressSym: () -> Unit,
    private val pageChanged: () -> Unit,
    /** A touch on a group's tab opened that group. */
    private val groupChanged: () -> Unit = {},
    /** A finger moved onto a key that shows a mark, like the tick of a swipe that crosses a key. */
    private val marked: () -> Unit = {},
    /** A touch on a key of the clipboard page, which does what a push of that key does. */
    private val clipKey: (String) -> Unit = {},
    /** A touch held on a clip, which pins or unpins it. The key is one under the clip. */
    private val holdClip: (String) -> Unit = {},
    /** A touch on A, which opens the clipboard from the symbols or emoji. */
    private val openClipboard: () -> Unit = {},
) : LinearLayout(context) {
    private val typeface = resources.getFont(R.font.inter_regular)
    private val symbols = EmojiPages(Keys.symbolGroups(), emptyList())
    /** The setting under a finger on the quick settings page, in the strip at the bottom. */
    private val preview = LineText(context).apply {
        typeface = this@SymPanel.typeface
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        // A setting's name must fit the strip at any system font size.
        setAutoSizeTextTypeUniformWithConfiguration(PREVIEW_SMALLEST, PREVIEW_LARGEST, 1, TypedValue.COMPLEX_UNIT_SP)
    }
    /** One mark for each group, across the whole width of the panel. */
    private val categories = LinearLayout(context)
    /** The strip along the bottom: the groups, or on the quick settings page the setting under a finger. */
    private val band = FrameLayout(context).apply {
        addView(categories, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(preview, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }
    /** On the clipboard page, the whole clip under a finger, or a message, in the place of the groups. A long clip scrolls. */
    private val clipLine = LineText(context).apply {
        typeface = this@SymPanel.typeface
        includeFontPadding = false
        visibility = GONE
    }
    private val clipMarquee = BandMarquee(clipLine)
    private val grid = LinearLayout(context)
    private val cells = (Layout.letterKeys + Layout.ALT + Layout.SYM + Layout.BACKSPACE + Layout.ENTER).associateWith(::cell)
    private var tabs = emptyList<LineText>()
    /** How far the band's groups keep from each side, for the screen's corners. */
    private var bandInset = 0
    /**
     * How far the panel's bottom is over the bottom of its window, the screen's bottom edge, in px: the bar's height when the bar
     * shows under the panel, about nothing when it's hidden. Only a band at the edge has the bar's line: see [bandLine].
     */
    private var below = 0
    private val windowAt = IntArray(2)
    private var shownSamples = emptyList<String>()
    private var navigationInset = 0
    private var corners = Corners.None
    private var look = Look.of(context)
    private var shownMark = ""

    /** The clipboard page's clips, newest first, six to a page, in a wide field each. */
    var clips: List<ClipboardHistory.Clip> = emptyList()
        set(value) {
            field = value
            clipPages.fit(value)
            if (clipboard) render()
        }
    /** A clip that pulses on the clipboard page, to show which to pick: the guide's right one. Null for none. */
    var flashClip: String? = null
    /** The open group of the clipboard, Clipboard or Pinned, and its page. */
    private val clipPages = ClipboardHistory.Pages()

    /** The clipboard opens at the first page of Clipboard, as the symbols open at their first page. */
    fun resetClipPages() = clipPages.reset()

    /** On the clipboard page, a push of a letter pins or unpins its clip, and does not paste it. */
    var clipPinning = false
    /** On the clipboard page, Backspace was pushed once, so a second push clears all. */
    var clipClearing = false
    /** The clipboard shows the guide's own clips: one group, and no Clear or Pin. */
    var clipSandbox = false

    /** Quick settings that don't work here, as the key that opens all settings in the guide: no mark, no name, no switch. */
    var blockedKeys: Set<String> = emptySet()

    /** Null until the emoji list has loaded. */
    var emoji: EmojiPages? = null
        set(value) {
            field = value
            render()
        }

    var mode = Modifiers.Panel.Closed
        set(value) {
            if (field == value) return
            // The quick settings page goes in front of the symbols or emoji, so they are still at the same page after it.
            val fromSettings = field == Modifiers.Panel.Settings || field == Modifiers.Panel.Clipboard
            field = value
            if (!fromSettings) pages?.reset()
            hover = ""
            render()
        }

    /** The key under a resting finger, or an empty string when no finger rests on the keyboard. */
    var hover = ""
        set(value) {
            if (field == value) return
            field = value
            showHover()
            KeyboardEvents.publish { KeyboardEvent.Resting(value) }
        }

    private val pages get() = when (mode) {
        Modifiers.Panel.Symbols -> symbols
        Modifiers.Panel.Emoji -> emoji
        Modifiers.Panel.Closed, Modifiers.Panel.Settings, Modifiers.Panel.Clipboard -> null
    }

    private val quick get() = mode == Modifiers.Panel.Settings
    private val clipboard get() = mode == Modifiers.Panel.Clipboard

    /** The place in [clips] of the clip over a key, or null if the key has none. The outer columns never have one. */
    fun clipIndex(key: String) = if (!clipboard) null else ClipboardHistory.slot(key)?.let { clipPages.index(clips, it) }

    /** The clip over a key of the clipboard page, or null if the key has none. */
    private fun clipAt(key: String) = clipIndex(key)?.let(clips::get)

    /**
     * One wide field of the clipboard page, in the place of the letter keys of half a row: the clip on one line that ends in "…",
     * with a small pin in its top corner when it is pinned. The whole clip shows in the strip at the bottom under a resting finger. It sits inside the inner columns, as the symbols do.
     */
    private inner class ClipField(val slot: Int) {
        val text = TextView(context).apply {
            typeface = this@SymPanel.typeface
            isSingleLine = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(8), 0, dp(CLIP_PIN + 6), 0)
        }
        val pinMark = ImageView(context).apply { setImageResource(R.drawable.pin) }
        val frame = FrameLayout(context).apply {
            addView(text, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(pinMark, FrameLayout.LayoutParams(dp(CLIP_PIN), dp(CLIP_PIN), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(4)
                marginEnd = dp(4)
            })
            visibility = GONE
            setOnClickListener { clipKey(ClipboardHistory.keyFor(slot)) }
            onHold {
                holdClip(ClipboardHistory.keyFor(slot))
                true
            }
        }
    }
    private val clipFields = List(ClipboardHistory.LIMIT) { ClipField(it) }
    /**
     * The clips that pulse softly, each with its animation: see [flash]. Before init, which renders, so it is never null there.
     * See tools/checks/init-order.test.ts.
     */
    private val flashes = HashMap<android.view.View, android.animation.ValueAnimator>()

    init {
        orientation = VERTICAL
        // A letter key slides a little past the edge of its row when the page changes.
        clipChildren = false
        grid.clipChildren = false
        grid.orientation = VERTICAL
        // Each row keeps all ten columns, so that the letter keys stay above the physical letter keys.
        // On the clipboard page, two wide fields take the place of the letter keys of each row, one over each half.
        for ((index, row) in Layout.rows.take(3).withIndex()) grid.addView(LinearLayout(context).apply {
            for ((column, key) in row.withIndex()) {
                val half = ClipboardHistory.halfAt(index, column)
                if (half != null && index * 2 + half < ClipboardHistory.PER_PAGE && ClipboardHistory.firstColumn(index, half) == column) {
                    val field = clipFields[index * 2 + half]
                    addView(field.frame, LayoutParams(0, LayoutParams.MATCH_PARENT, ClipboardHistory.width(index, half).toFloat()).apply {
                        setMargins(dp(2), dp(3), dp(2), dp(3))
                    })
                }
                addView(cells[key] ?: Space(context), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT)))
        // The ten columns sit over the real keys, each from the middle of one gap to the middle of the next: see onMeasure.
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        band.addView(clipLine, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(band, LayoutParams(LayoutParams.MATCH_PARENT, dp(BAND_HEIGHT)))
        // Keep the keys clear of the gesture handle, which draws over the bottom of the keyboard, and of the rounded corners.
        setOnApplyWindowInsetsListener { _, insets ->
            navigationInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            corners = Corners.of(insets, display)
            render()
            insets
        }
        render()
    }

    /**
     * The ten columns of each row sit over the real keys below the screen, as [KeyGeometry] has them on this width and as Key
     * calibration in TitanKeys Debug says: each column runs from the middle of the gap left of its key to the middle of the gap
     * right of it, so a label is centred over its key. The grid's margins set where the rows start and end. At the defaults the
     * keys span the whole screen, so the outer columns reach half a gap past it. The panel is as wide as the screen.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val keys = KeyGeometry(MeasureSpec.getSize(widthMeasureSpec).toFloat(), KeyCalibration.load(context))
        (grid.layoutParams as? LayoutParams)?.apply {
            leftMargin = Math.round(keys.left - keys.gap / 2) - paddingLeft
            rightMargin = Math.round(keys.width - keys.right - keys.gap / 2) - paddingRight
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** The symbol or emoji on a letter key, or null if the key is blank. */
    fun item(key: String): String? = pages?.page()?.getOrNull(Layout.panelLetters.indexOf(key))

    /** Follows a swipe, in keys across and rows down. Returns true if a different page now shows. */
    fun swipe(across: Float, down: Float): Boolean {
        // The guide's clipboard has one group: a sideways swipe there goes nowhere.
        val sideways = kotlin.math.abs(across) > kotlin.math.abs(down)
        val moved = if (clipboard) !(clipSandbox && sideways) && clipPages.swipe(clips, across, down)
            else pages?.swipe(across, down) == true
        if (moved) {
            render()
            slideIn(across, down)
        }
        KeyboardEvents.publish { KeyboardEvent.PanelSwiped(across, down, moved) }
        return moved
    }

    /** The strip of groups shows, so the bottom row of the touchpad chooses a group. */
    val hasCategories get() = !quick && (pages != null || clipboard)

    /** Opens the group at a place along the strip, from 0 at its left end to 1 at its right end. Returns true if the group changes. */
    fun pickCategory(across: Float) = if (clipboard) open(clipPages.groupAt(across)) else pages?.let { open(it.groupAt(across)) } == true

    /** Opens the group under a bottom-row key, unless the open group is already under that key. Returns true if the group changes. */
    fun pickCategoryUnder(key: String): Boolean {
        val (left, right) = Layout.bottomSpan(key)
        if (clipboard) return open(clipPages.groupUnder(left, right))
        val current = pages ?: return false
        return open(current.groupUnder(left, right))
    }

    private fun open(group: Int): Boolean {
        if (clipboard) {
            if (clipSandbox && group != ClipboardHistory.RECENT) return false
            val before = clipPages.group
            if (!clipPages.open(group)) return false
            render()
            slideIn(if (group > before) -1f else 1f, 0f)
            KeyboardEvents.publish { KeyboardEvent.GroupPicked(group) }
            return true
        }
        val current = pages ?: return false
        val before = current.groupIndex()
        if (!current.open(group)) return false
        render()
        slideIn(if (group > before) -1f else 1f, 0f)
        KeyboardEvents.publish { KeyboardEvent.GroupPicked(group) }
        return true
    }

    /**
     * The new letter keys slide in the way the finger went. The icons are not part of a page, so they stay still.
     * Android's animator duration setting shortens this or turns it off.
     */
    private fun slideIn(across: Float, down: Float) {
        val distance = dp(SLIDE_DISTANCE).toFloat()
        val upOrDown = EmojiPages.turnsPage(across, down)
        val moving = if (clipboard) clipFields.map { it.frame } else Layout.panelLetters.map(cells::getValue)
        for (view in moving) view.apply {
            animate().cancel()
            translationX = if (upOrDown) 0f else -across.sign * distance
            translationY = if (upOrDown) -down.sign * distance else 0f
            alpha = SLIDE_ALPHA
            animate().translationX(0f).translationY(0f).alpha(1f).setDuration(SLIDE_MILLIS).start()
        }
    }

    private fun cell(key: String) = TextView(context).apply {
        typeface = this@SymPanel.typeface
        gravity = Gravity.CENTER
        foregroundGravity = Gravity.CENTER
        maxLines = 1
        includeFontPadding = false
        isFocusable = false
        setOnClickListener { touched(key) }
        onHold {
            if (clipAt(key) == null) return@onHold false
            holdClip(key)
            true
        }
    }

    /** A touch on the screen does what a push of the key does. Android's click comes when the finger lifts, and not if it slid off the key. */
    private fun touched(key: String) {
        val rows = pageRows(key)
        when {
            key == Layout.ALT -> pressAlt()
            key == Layout.SYM -> pressSym()
            opensClipboard(key) -> openClipboard()
            quick -> QuickSetting.on(key)?.let(useQuickSetting)
            clipboard -> clipKey(key)
            rows != null -> if (swipe(0f, rows)) pageChanged()
            else -> item(key)?.let(pick)
        }
    }

    /** What the key at the place of Backspace or Enter does to the pages, in rows down, or null if it changes nothing here. */
    private fun pageRows(key: String): Float? {
        val code = when (key) {
            Layout.BACKSPACE -> KeyEvent.KEYCODE_DEL
            Layout.ENTER -> KeyEvent.KEYCODE_ENTER
            else -> return null
        }
        return Modifiers.panelScroll(code, mode)?.takeIf { it != 0f && (pages?.pageCount() ?: 0) > 1 }
    }

    /** An icon, for a key whose purpose is not a letter or symbol. It is the same size at any system font size. */
    private class Icon(val drawable: Int, val description: String)

    private fun opensClipboard(key: String) = pages != null && key == Layout.CLIPBOARD_KEY

    private fun icon(key: String) = when {
        opensClipboard(key) -> Icon(R.drawable.clipboard, context.getString(R.string.clipboard_open))
        key == Layout.ALT -> if (quick || clipboard) Icon(R.drawable.panel_back, "Go back")
            else Icon(R.drawable.panel_settings, "Open quick settings")
        // What Sym does next: from the symbols it opens the emoji, and from the other pages it closes the panel.
        key == Layout.SYM -> if (mode == Modifiers.Panel.Symbols) Icon(R.drawable.panel_emoji, "Show emoji")
            else Icon(R.drawable.panel_close, "Close the panel")
        // Rows up is what a swipe up does, which opens the next page.
        else -> pageRows(key)?.let { rows ->
            if (rows < 0) Icon(R.drawable.panel_next, "Show the next page")
            else Icon(R.drawable.panel_previous, "Show the previous page")
        }
    }

    /** This key shows where the open page is, and does nothing else. */
    private fun showsPosition(key: String) = (pages != null || clipboard) && key == Layout.PAGE_POSITION

    private fun pageNumber() = if (clipboard) clipPages.page + 1 else pages?.pageNumber() ?: 0
    private fun pageCount() = if (clipboard) clipPages.pageCount(clips) else pages?.pageCount() ?: 0

    /** Where the open page is in its group, such as 2/9, or null if the group has only one page. */
    private fun position() = pageCount().takeIf { it > 1 }?.let { "${pageNumber()}/$it" }

    /** What a key shows as text, or null if it shows none. */
    private fun label(key: String) = when {
        quick -> QuickSetting.on(key)?.label
        clipboard -> when (key) {
            Layout.BACKSPACE -> context.getString(R.string.clipboard_clear).takeIf { !clipSandbox && clips.any { !it.pinned } }
            Layout.ENTER -> context.getString(if (clipPinning) R.string.clipboard_pin_done else R.string.clipboard_pin)
                .takeIf { !clipSandbox && clips.isNotEmpty() }
            else -> if (showsPosition(key)) position() else null
        }
        showsPosition(key) -> position()
        else -> item(key)
    }

    private fun describe(item: QuickSetting) =
        if (item == QuickSetting.OpenSettings || item == QuickSetting.ChangeKeyboard || item == QuickSetting.Clipboard) item.title else "${item.title}: ${if (quickSettingOn(item)) "On" else "Off"}"

    /** What the strip at the bottom says for a key under a finger on the quick settings page, or null if it says nothing. */
    private fun detail(key: String) = if (quick) QuickSetting.on(key)?.takeIf { key !in blockedKeys }?.let(::describe)
        else clipAt(key)?.let { clip -> ClipboardHistory.preview(clip.text) }

    /**
     * What the strip at the bottom of the clipboard page says in the place of the groups: the whole clip under a resting finger,
     * what a push does next, or why a group is empty. Null shows the groups.
     */
    private fun clipBandText(): String? {
        detail(hover)?.let { return it }
        val message = when {
            clipClearing -> R.string.clipboard_clear_again
            clipPinning -> R.string.clipboard_pinning
            clipPages.items(clips).isEmpty() -> if (clipPages.group == ClipboardHistory.PINNED) R.string.clipboard_pinned_empty
                else R.string.clipboard_empty
            else -> return null
        }
        return context.getString(message)
    }

    private fun dim(key: String) = quick && (key in blockedKeys || QuickSetting.on(key)?.let(quickSettingOn) == false)

    private fun categoryTab(sample: String, name: String, index: Int) = LineText(context).apply {
        text = sample
        gravity = Gravity.CENTER
        isSingleLine = true
        includeFontPadding = false
        isFocusable = false
        contentDescription = "Show $name"
        setOnClickListener { if (open(index)) groupChanged() }
    }

    /** Text that follows the system font size up to [largest] dp, so that it stays inside a row of fixed height. */
    private fun TextView.sized(sp: Float, largest: Float) {
        val metrics = this@SymPanel.resources.displayMetrics
        val size = min(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, largest, metrics))
        // Only a real change: setting the size lays the text out again, which would throw a rolling line back to its start mid-roll
        // each time a finger moves.
        if (kotlin.math.abs(textSize - size) > .5f) setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
    }

    /** The insets listener does not always run in the keyboard's window, so the corners are read here as well. */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        corners = Corners.of(rootWindowInsets, display)
        render()
    }

    /** Shows the panel as it is now, also after a quick setting changes. */
    fun render() {
        look = Look.of(context)
        visibility = if (mode == Modifiers.Panel.Closed) GONE else VISIBLE
        setBackgroundColor(look.paper)
        setPadding(dp(PANEL_SIDE_DP), 0, dp(PANEL_SIDE_DP), navigationInset)
        val current = pages
        categories.visibility = if (quick) GONE else VISIBLE
        preview.visibility = if (quick) VISIBLE else GONE
        preview.setTextColor(look.ink)
        // The corners first: the washed group's highlight depends on how far the band keeps from them.
        keepClearOfCorners()
        showCategories(current)
        for ((key, view) in cells) {
            val value = label(key)
            val icon = icon(key)
            val isPosition = showsPosition(key)
            // On the clipboard page the wide fields take the place of the inner letter keys.
            view.visibility = if (clipboard && ClipboardHistory.slot(key) != null) GONE else VISIBLE
            view.text = value.orEmpty()
            view.foreground = icon?.let { context.getDrawable(it.drawable) }
            // A label must fit its key, so it shrinks where a symbol keeps its size.
            if (quick || clipboard || isPosition) view.setAutoSizeTextTypeUniformWithConfiguration(LABEL_SMALLEST, LABEL_LARGEST, 1, TypedValue.COMPLEX_UNIT_DIP)
            else {
                view.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
                view.sized(ITEM_SIZE, ITEM_LARGEST)
            }
            // The page position is only to read.
            view.isEnabled = !isPosition && (value != null || icon != null)
            view.contentDescription = when {
                icon != null -> icon.description
                quick -> detail(key)
                clipboard -> detail(key)?.let { context.getString(R.string.clipboard_paste, it) } ?: value
                isPosition -> value?.let { "Page ${pageNumber()} of ${pageCount()}" }
                else -> value?.let { "Insert $it" }
            }
        }
        contentDescription = when {
            quick -> "$QUICK_TITLE. Rest a finger on a key to see its setting. Press the key to switch it."
            clipboard -> context.getString(R.string.clipboard_description)
            current != null -> "${if (mode == Modifiers.Panel.Symbols) "Symbols" else "Emoji"}, ${current.group()}. " +
                "Press a key to type it. Swipe up or down on the keyboard for more, or press Backspace or Enter. " +
                "Swipe left or right to change the group, or put a finger on the bottom row of keys."
            else -> null
        }
        showHover()
        // The guide draws the keys as the panel shows them, so that the drawing on its slide matches the real panel.
        KeyboardEvents.publish { KeyboardEvent.PanelFace(mode, if (mode == Modifiers.Panel.Closed) emptyMap() else face()) }
    }

    /** What each key shows now, as text, with a short mark for each icon. */
    private fun face() = cells.keys.mapNotNull { key ->
        val rows = pageRows(key)
        val shown = when {
            opensClipboard(key) -> "📋"
            key == Layout.ALT -> if (quick || clipboard) "←" else "⚙"
            key == Layout.SYM -> if (mode == Modifiers.Panel.Symbols) "☺" else "✕"
            rows != null -> if (rows < 0) "↓" else "↑"
            clipboard -> clipAt(key)?.let { ClipboardHistory.preview(it.text, FACE_CLIP_LENGTH) } ?: label(key)
            else -> label(key)
        }
        shown?.takeIf { it.isNotEmpty() }?.let { key to it }
    }.toMap()

    /**
     * The screen's rounded corners cut into the bottom of the panel. The keys in the outer columns move their content in, and
     * the strip at the bottom keeps clear of them, so that the keys stay in line with the physical keys.
     */
    private fun keepClearOfCorners() {
        // The band's text sits on its line, and its tails are the lowest that shows in it. The sides keep from the corners as if
        // the band were at the screen's edge, where it is with the bar hidden, so its groups don't move when the bar comes or goes.
        val line = bandLine()
        for (view in listOf(preview, clipLine) + tabs) onLine(view, line)
        val tails = dp(BAND_HEIGHT) - line - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BAND_TEXT_LARGEST_ANY,
            resources.displayMetrics) * (StripFit.X_HEIGHT / 2 + StripFit.DESCENT)
        bandInset = corners.clearInset(navigationInset + tails, edge())
        categories.setPadding(bandInset, 0, bandInset, 0)
        preview.setPadding(bandInset + dp(8), 0, bandInset + dp(8), 0)
        clipLine.setPadding(bandInset + dp(8), 0, bandInset + dp(8), 0)
        val keys = KeyGeometry(resources.displayMetrics.widthPixels.toFloat(), KeyCalibration.load(context))
        // Where the outer columns start and end, in from the screen's sides: see onMeasure.
        val leftColumn = Math.round(keys.left - keys.gap / 2)
        val rightColumn = Math.round(keys.width - keys.right - keys.gap / 2)
        for ((index, row) in Layout.rows.take(3).withIndex()) {
            val rowsBelow = 2 - index
            val above = navigationInset + dp(BAND_HEIGHT + rowsBelow * ROW_HEIGHT + ROW_CONTENT_ABOVE)
            // The outer cells already sit in as far as their columns do, so only a corner deeper than that moves their content.
            val corner = corners.inset(above.toFloat())
            cells[row.first()]?.setPadding((corner - leftColumn).coerceAtLeast(0), 0, 0, 0)
            cells[row.last()]?.setPadding(0, 0, (corner - rightColumn).coerceAtLeast(0), 0)
        }
    }

    /** Shows a mark for each group, and washes the open one, so that you can see the other groups as you swipe. */
    private fun showCategories(current: EmojiPages?) {
        // The clipboard has two groups, named in words, where the symbols and emoji show one of their own.
        val groups = (if (clipSandbox) listOf(R.string.clipboard_group_recent)
            else listOf(R.string.clipboard_group_recent, R.string.clipboard_group_pinned)).map(context::getString)
        val samples = if (clipboard) groups else current?.samples().orEmpty()
        if (samples != shownSamples) {
            shownSamples = samples
            categories.removeAllViews()
            val names = if (clipboard) groups else current?.groupNames().orEmpty()
            tabs = samples.mapIndexed { index, sample ->
                categoryTab(sample, names[index], index).also {
                    onLine(it, bandLine())
                    categories.addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
                }
            }
        }
        val open = if (clipboard) clipPages.group else current?.groupIndex() ?: -1
        tabs.forEachIndexed { index, tab ->
            tab.sized(CATEGORY_SIZE, CATEGORY_LARGEST)
            tab.setTextColor(look.ink)
            // The wash is round the tab's text, on the bar's line, a little over the band's own middle.
            tab.background = if (index != open) null else clearOfCorners(rounded(look.wash), navigationInset, dp(TAB_INSET),
                first = index == 0, last = index == tabs.lastIndex, sideGap = dp(4) + bandInset,
                lift = Math.round(dp(BAND_HEIGHT) / 2f - bandLine()))
        }
    }

    /** A letter key under a resting finger takes the accent, as [Layout.restMarks] says. A setting's name shows in the strip at the bottom. */
    private fun showHover() {
        if (clipboard) {
            // The whole clip under a finger, or a message, takes the place of the groups, in the middle as on the quick settings.
            val text = clipBandText()
            clipLine.visibility = if (text != null) VISIBLE else GONE
            categories.visibility = if (text != null) GONE else VISIBLE
            clipLine.sized(BAND_TEXT_SIZE, BAND_TEXT_LARGEST)
            clipLine.setTextColor(look.ink)
            if (text != null) clipMarquee.show(text) else clipMarquee.stop()
        } else {
            preview.text = detail(hover) ?: if (quick) QUICK_TITLE else ""
            clipLine.visibility = GONE
            clipMarquee.stop()
        }
        showClipFields()
        // A finger on a clip marks its whole field, as a finger on a symbol marks its key.
        val mark = clipIndex(hover)?.let { "clip$it" }
            ?: hover.takeIf { Layout.restMarks(it, pages != null) && label(it) != null }.orEmpty()
        for ((key, view) in cells) {
            val hovered = key == mark
            view.background = if (hovered) cellHighlight(key) else null
            view.setTextColor(when {
                hovered -> look.onAccent
                dim(key) -> Color.GRAY
                showsPosition(key) -> look.muted
                // Clear and Pin are actions, so they take the accent like the buttons on the strip.
                clipboard && (key == Layout.BACKSPACE || key == Layout.ENTER) -> look.accent
                else -> look.ink
            })
            view.foregroundTintList = ColorStateList.valueOf(look.ink)
        }
        if (mark != shownMark) {
            shownMark = mark
            if (mark.isNotEmpty()) marked()
        }
    }

    /** The clipboard page's fields: each clip, the one under a resting finger in the accent colour, and an outline while pinning. */
    private fun showClipFields() {
        val under = clipIndex(hover)
        for (field in clipFields) {
            val index = clipPages.index(clips, field.slot)
            val clip = index?.let(clips::getOrNull)
            field.frame.visibility = if (!clipboard) GONE else if (clip == null) INVISIBLE else VISIBLE
            if (!clipboard || clip == null) {
                flash(field.frame, false)
                continue
            }
            val lit = under == index
            field.text.text = ClipboardHistory.preview(clip.text, CLIP_PREVIEW)
            field.text.sized(CLIP_TEXT_SIZE, CLIP_TEXT_LARGEST)
            field.text.setTextColor(if (lit) look.onAccent else look.ink)
            field.frame.background = GradientDrawable().apply {
                cornerRadius = Radii.px(resources, Radii.SMALL)
                setColor(if (lit) look.accent else look.wash)
                if (clipPinning && !lit) setStroke(dp(1).coerceAtLeast(2), look.accent)
            }
            field.pinMark.visibility = if (clip.pinned) VISIBLE else GONE
            flash(field.frame, clip.text == flashClip && !lit)
            field.pinMark.imageTintList = ColorStateList.valueOf(if (lit) look.onAccent else look.accent)
            field.frame.contentDescription = context.getString(R.string.clipboard_paste, ClipboardHistory.preview(clip.text))
        }
    }


    /**
     * A gentle orange pulse on [view]'s field, never a harsh flash (owner, 2026-11-13): its outline glows in and out in orange,
     * and its fill warms a little, slowly. With animations off, it keeps a steady orange outline. It reads the field's
     * background on every frame, since the page draws it anew.
     */
    private fun flash(view: android.view.View, on: Boolean) {
        if (!on || !GuideMotion.enabled) {
            flashes.remove(view)?.cancel()
            if (on) pulse(view, 1f)
            return
        }
        // The page drew the field anew: the running pulse paints over it at once, so it never blinks plain.
        flashes[view]?.let { running ->
            pulse(view, running.animatedValue as Float)
            return
        }
        flashes[view] = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PULSE_MILLIS
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { pulse(view, it.animatedValue as Float) }
            start()
        }
    }

    private fun pulse(view: android.view.View, t: Float) {
        val fill = view.background as? GradientDrawable ?: return
        fill.setStroke(dp(1).coerceAtLeast(2), GuideMotion.faded(look.accent, PULSE_LEAST + (1f - PULSE_LEAST) * t))
        fill.setColor(GuideMotion.blend(look.wash, look.accent, PULSE_WARMTH * t))
    }

    /** The key under a finger. A key in an outer column keeps its highlight clear of the screen's corners. */
    private fun cellHighlight(key: String): Drawable {
        val row = Layout.rows.take(3).indexOfFirst { key in it }
        if (row < 0) return rounded(look.accent)
        val keys = Layout.rows[row]
        val bottom = navigationInset + dp(BAND_HEIGHT + (2 - row) * ROW_HEIGHT)
        // An outer column starts where the keys do, half a gap out from them: past the screen's side at the defaults.
        val geometry = KeyGeometry(resources.displayMetrics.widthPixels.toFloat(), KeyCalibration.load(context))
        val first = key == keys.first()
        val sideGap = Math.round(if (first) geometry.left - geometry.gap / 2 else geometry.width - geometry.right - geometry.gap / 2)
        return clearOfCorners(rounded(look.accent), bottom, 0, first = first, last = key == keys.last(), sideGap = sideGap)
    }

    /**
     * A highlight that the screen's rounded corners never cut. [bottom] is how far the highlighted box's bottom is over the
     * bottom of the screen, and [sideGap] how far a [first] or [last] box is from the screen's side. The highlight keeps [inset]
     * from the box's top and bottom, and moves in from the side as far as the corner needs at its lowest edge. Only the
     * highlight changes: the box itself does not move.
     */
    private fun clearOfCorners(highlight: Drawable, bottom: Int, inset: Int, first: Boolean, last: Boolean, sideGap: Int, lift: Int = 0): Drawable {
        val side = (corners.inset((bottom + inset + lift).toFloat()) + dp(CORNER_MARGIN) - sideGap).coerceAtLeast(0)
        return InsetDrawable(highlight, if (first) side else 0, inset - lift, if (last) side else 0, inset + lift)
    }

    /**
     * The band's line, in px under the band's top, where its text has the middle of its small letters. At the screen's bottom edge,
     * with the bar hidden, it's the bar's line, [StripFit.LINE_ABOVE_DP] over the edge, by the black border under the screen. With
     * the bar under the panel the band isn't at the border, and its text is in the band's own middle.
     */
    private fun bandLine() = if (below <= dp(2)) dp(BAND_HEIGHT) - StripFit.LINE_ABOVE_DP * resources.displayMetrics.density
        else dp(BAND_HEIGHT) / 2f

    /** Where the panel sits in its window decides the band's line: it lays the band out again when the bar under it comes or goes. */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        getLocationInWindow(windowAt)
        val now = (rootView.height - (windowAt[1] + height)).coerceAtLeast(0)
        if (now != below) {
            below = now
            render()
        }
    }

    /** [view], which fills the band, draws its text on the band's [line]. */
    private fun onLine(view: LineText, line: Float) {
        view.line = line
        view.floor = dp(BAND_HEIGHT) - edge()
    }

    /** How far what shows keeps from the screen's outline, in px: [StripFit.EDGE_DP]. */
    private fun edge() = StripFit.EDGE_DP * resources.displayMetrics.density

    /** A fill with the small corners that every highlight in TitanKeys has. */
    private fun rounded(colour: Int) = GradientDrawable().apply {
        cornerRadius = Radii.px(resources, Radii.SMALL)
        setColor(colour)
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        render()
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    private companion object {
        /** A flashing clip dims to this and back, once each way in this long. */
        /** The right clip's pulse: one way takes this long, its outline never fainter than [PULSE_LEAST], its fill this warm at most. */
        const val PULSE_MILLIS = 1100L
        const val PULSE_LEAST = .3f
        const val PULSE_WARMTH = .14f
        /** How much of a clip the guide's drawing shows on its key. */
        const val FACE_CLIP_LENGTH = 4
        const val BAND_HEIGHT = 32
        /** The panel's own room on each side, in dp. */
        const val PANEL_SIDE_DP = 4
        const val ROW_HEIGHT = 46
        /** How far the lowest content of a row is over the row's bottom: an icon in the middle of the row. */
        const val ROW_CONTENT_ABOVE = 11
        /** The band's largest text, its tabs' and the quick setting's name, in dp: their tails are the lowest that shows in it. */
        const val BAND_TEXT_LARGEST_ANY = 18f
        /** How far the washed group keeps from the top and bottom of the band, so it is a pill inside the band. */
        const val TAB_INSET = 3
        /** How far a highlight keeps from the curve of a screen corner. */
        const val CORNER_MARGIN = 2
        const val CATEGORY_SIZE = 15f
        const val CATEGORY_LARGEST = 18f
        /** No text below [Strip.SMALLEST_TEXT]: 13 at the least, for the setting's name in the strip and a key's label. */
        const val PREVIEW_SMALLEST = 13
        const val PREVIEW_LARGEST = 18
        const val ITEM_SIZE = 22f
        const val ITEM_LARGEST = 30f
        const val LABEL_SMALLEST = 13
        const val LABEL_LARGEST = 16
        /** A clip in its field: two lines that fit the row at any system font size. */
        const val CLIP_TEXT_SIZE = 15f
        const val CLIP_TEXT_LARGEST = 16f
        /** Enough of a clip for two lines. The field ends it with "…" where it runs out of room. */
        const val CLIP_PREVIEW = 160
        const val CLIP_PIN = 11
        /** The clip under a finger in the strip at the bottom. */
        const val BAND_TEXT_SIZE = 15f
        const val BAND_TEXT_LARGEST = 16f
        const val QUICK_TITLE = "Quick settings"
        const val SLIDE_DISTANCE = 12
        const val SLIDE_ALPHA = .5f
        const val SLIDE_MILLIS = 120L
    }
}
