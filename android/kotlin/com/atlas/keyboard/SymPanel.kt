package com.atlas.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowInsets
import android.widget.FrameLayout
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
    /** A finger moved onto a key that shows a mark, like the tick of a swipe that crosses a key. */
    private val marked: () -> Unit = {},
) : LinearLayout(context) {
    private val typeface = resources.getFont(R.font.inter_regular)
    private val symbols = EmojiPages(Keys.symbolGroups(), emptyList())
    /** The setting under a finger on the quick settings page, in the strip at the bottom. */
    private val preview = TextView(context).apply {
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
    private val grid = LinearLayout(context)
    private val cells = (Layout.letterKeys + Layout.ALT + Layout.SYM + Layout.BACKSPACE + Layout.ENTER).associateWith(::cell)
    private var tabs = emptyList<TextView>()
    private var shownSamples = emptyList<String>()
    private var navigationInset = 0
    private var corners = Corners.None
    private var look = Look.of(context)
    private var shownMark = ""

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
            val fromSettings = field == Modifiers.Panel.Settings
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
        Modifiers.Panel.Closed, Modifiers.Panel.Settings -> null
    }

    private val quick get() = mode == Modifiers.Panel.Settings

    init {
        orientation = VERTICAL
        // A letter key slides a little past the edge of its row when the page changes.
        clipChildren = false
        grid.clipChildren = false
        grid.orientation = VERTICAL
        // Each row keeps all ten columns, so that the letter keys stay above the physical letter keys.
        for (row in Layout.rows.take(3)) grid.addView(LinearLayout(context).apply {
            for (key in row) addView(cells[key] ?: Space(context), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT)))
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
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

    /** The symbol or emoji on a letter key, or null if the key is blank. */
    fun item(key: String): String? = pages?.page()?.getOrNull(Layout.panelLetters.indexOf(key))

    /** Follows a swipe, in keys across and rows down. Returns true if a different page now shows. */
    fun swipe(across: Float, down: Float): Boolean {
        val moved = pages?.swipe(across, down) == true
        if (moved) {
            render()
            slideIn(across, down)
        }
        KeyboardEvents.publish { KeyboardEvent.PanelSwiped(across, down, moved) }
        return moved
    }

    /** The strip of groups shows, so the bottom row of the touchpad chooses a group. */
    val hasCategories get() = categories.visibility == VISIBLE

    /** Opens the group at a place along the strip, from 0 at its left end to 1 at its right end. Returns true if the group changes. */
    fun pickCategory(across: Float) = pages?.let { open(it.groupAt(across)) } == true

    /** Opens the group under a bottom-row key, unless the open group is already under that key. Returns true if the group changes. */
    fun pickCategoryUnder(key: String): Boolean {
        val current = pages ?: return false
        val (left, right) = Layout.bottomSpan(key)
        return open(current.groupUnder(left, right))
    }

    private fun open(group: Int): Boolean {
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
        for (key in Layout.panelLetters) cells.getValue(key).apply {
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
    }

    /** A touch on the screen does what a push of the key does. Android's click comes when the finger lifts, and not if it slid off the key. */
    private fun touched(key: String) {
        val rows = pageRows(key)
        when {
            key == Layout.ALT -> pressAlt()
            key == Layout.SYM -> pressSym()
            quick -> QuickSetting.on(key)?.let(useQuickSetting)
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

    private fun icon(key: String) = when {
        key == Layout.ALT -> if (mode == Modifiers.Panel.Settings) Icon(R.drawable.panel_back, "Go back")
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
    private fun showsPosition(key: String) = pages != null && key == Layout.PAGE_POSITION

    /** Where the open page is in its group, such as 2/9, or null if the group has only one page. */
    private fun position() = pages?.takeIf { it.pageCount() > 1 }?.let { "${it.pageNumber()}/${it.pageCount()}" }

    /** What a key shows as text, or null if it shows none. */
    private fun label(key: String) = when {
        quick -> QuickSetting.on(key)?.label
        showsPosition(key) -> position()
        else -> item(key)
    }

    private fun describe(item: QuickSetting) =
        if (item == QuickSetting.OpenSettings || item == QuickSetting.ChangeKeyboard) item.title else "${item.title}: ${if (quickSettingOn(item)) "On" else "Off"}"

    /** What the strip at the bottom says for a key under a finger on the quick settings page, or null if it says nothing. */
    private fun detail(key: String) = if (quick) QuickSetting.on(key)?.let(::describe) else null

    private fun dim(key: String) = quick && QuickSetting.on(key)?.let(quickSettingOn) == false

    private fun categoryTab(sample: String, name: String, index: Int) = TextView(context).apply {
        text = sample
        gravity = Gravity.CENTER
        isSingleLine = true
        includeFontPadding = false
        isFocusable = false
        contentDescription = "Show $name"
        setOnClickListener { if (open(index)) pageChanged() }
    }

    /** Text that follows the system font size up to [largest] dp, so that it stays inside a row of fixed height. */
    private fun TextView.sized(sp: Float, largest: Float) {
        val metrics = this@SymPanel.resources.displayMetrics
        setTextSize(TypedValue.COMPLEX_UNIT_PX, min(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, largest, metrics)))
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
        setPadding(dp(4), 0, dp(4), navigationInset)
        val current = pages
        categories.visibility = if (quick) GONE else VISIBLE
        preview.visibility = if (quick) VISIBLE else GONE
        preview.setTextColor(look.ink)
        showCategories(current)
        keepClearOfCorners()
        for ((key, view) in cells) {
            val value = label(key)
            val icon = icon(key)
            val isPosition = showsPosition(key)
            view.text = value.orEmpty()
            view.foreground = icon?.let { context.getDrawable(it.drawable) }
            // A label must fit its key, so it shrinks where a symbol keeps its size.
            if (quick || isPosition) view.setAutoSizeTextTypeUniformWithConfiguration(LABEL_SMALLEST, LABEL_LARGEST, 1, TypedValue.COMPLEX_UNIT_DIP)
            else {
                view.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
                view.sized(ITEM_SIZE, ITEM_LARGEST)
            }
            // The page position is only to read.
            view.isEnabled = !isPosition && (value != null || icon != null)
            view.contentDescription = when {
                icon != null -> icon.description
                quick -> detail(key)
                isPosition -> value?.let { "Page ${current?.pageNumber()} of ${current?.pageCount()}" }
                else -> value?.let { "Insert $it" }
            }
        }
        contentDescription = when {
            quick -> "$QUICK_TITLE. Rest a finger on a key to see its setting. Push the key to change the setting."
            current != null -> "${if (mode == Modifiers.Panel.Symbols) "Symbols" else "Emoji"}, ${current.group()}. " +
                "Push a key to type it. Swipe up or down on the keyboard to see more, or push Backspace or Enter. " +
                "Swipe left or right to change the group, or put a finger on the bottom row of keys."
            else -> null
        }
        showHover()
    }

    /**
     * The screen's rounded corners cut into the bottom of the panel. The keys in the outer columns move their content in, and
     * the strip at the bottom keeps clear of them, so that the keys stay in line with the physical keys.
     */
    private fun keepClearOfCorners() {
        val bandInset = corners.inset((navigationInset + dp(BAND_CONTENT_ABOVE)).toFloat())
        categories.setPadding(bandInset, 0, bandInset, 0)
        preview.setPadding(bandInset + dp(8), 0, bandInset + dp(8), 0)
        for ((index, row) in Layout.rows.take(3).withIndex()) {
            val rowsBelow = 2 - index
            val above = navigationInset + dp(BAND_HEIGHT + rowsBelow * ROW_HEIGHT + ROW_CONTENT_ABOVE)
            val inset = (corners.inset(above.toFloat()) - dp(4)).coerceAtLeast(0)
            cells[row.first()]?.setPadding(inset, 0, 0, 0)
            cells[row.last()]?.setPadding(0, 0, inset, 0)
        }
    }

    /** Shows a mark for each group, and washes the open one, so that you can see the other groups as you swipe. */
    private fun showCategories(current: EmojiPages?) {
        val samples = current?.samples().orEmpty()
        if (samples != shownSamples) {
            shownSamples = samples
            categories.removeAllViews()
            val names = current?.groupNames().orEmpty()
            tabs = samples.mapIndexed { index, sample ->
                categoryTab(sample, names[index], index).also { categories.addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)) }
            }
        }
        val open = current?.groupIndex() ?: -1
        tabs.forEachIndexed { index, tab ->
            tab.sized(CATEGORY_SIZE, CATEGORY_LARGEST)
            tab.setTextColor(look.ink)
            tab.setBackgroundColor(if (index == open) look.wash else Color.TRANSPARENT)
        }
    }

    /** A letter key under a resting finger takes the accent, as [Layout.restMarks] says. A setting's name shows in the strip at the bottom. */
    private fun showHover() {
        preview.text = detail(hover) ?: if (quick) QUICK_TITLE else ""
        val mark = hover.takeIf { Layout.restMarks(it, pages != null) && label(it) != null }.orEmpty()
        for ((key, view) in cells) {
            val hovered = key == mark
            view.setBackgroundColor(if (hovered) look.accent else Color.TRANSPARENT)
            view.setTextColor(if (hovered) look.onAccent else if (dim(key)) Color.GRAY else if (showsPosition(key)) look.muted else look.ink)
            view.foregroundTintList = ColorStateList.valueOf(look.ink)
        }
        if (mark != shownMark) {
            shownMark = mark
            if (mark.isNotEmpty()) marked()
        }
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        render()
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    private companion object {
        const val BAND_HEIGHT = 32
        const val ROW_HEIGHT = 46
        /** How far the lowest content of a row is over the row's bottom: an icon in the middle of the row. */
        const val ROW_CONTENT_ABOVE = 11
        const val BAND_CONTENT_ABOVE = 6
        const val CATEGORY_SIZE = 15f
        const val CATEGORY_LARGEST = 18f
        const val PREVIEW_SMALLEST = 8
        const val PREVIEW_LARGEST = 16
        const val ITEM_SIZE = 22f
        const val ITEM_LARGEST = 30f
        const val LABEL_SMALLEST = 8
        const val LABEL_LARGEST = 14
        const val QUICK_TITLE = "Quick settings"
        const val SLIDE_DISTANCE = 12
        const val SLIDE_ALPHA = .5f
        const val SLIDE_MILLIS = 120L
    }
}
