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
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.atlas.engine.EmojiPages
import kotlin.math.min
import kotlin.math.sign

/**
 * The panel that Sym opens: a symbol or an emoji on each letter key of the top three rows but Q, A and P, in line with the physical
 * letter keys. P shows the position of the open page and only that, and the other keys are left empty. A finger that rests on a key
 * marks that key, except in the last column, which the touchpad cannot read well. The groups of emoji are in a strip along the bottom,
 * above the bottom row of physical keys, and a finger that moves along that row opens the group at its place.
 *
 * Alt's place shows the quick settings key and Sym's place shows what Sym does next, each as an icon. When a group of emoji has
 * more than one page, the places of Backspace and Enter show an icon for the page that each key opens. On the quick settings page
 * each letter key that has an item shows its short label, dim when the setting is off, and a finger that rests on the key shows
 * the setting's name and state at the top.
 */
class SymPanel(
    context: Context,
    private val pick: (String) -> Unit,
    private val useQuickSetting: (QuickSetting) -> Unit,
    private val quickSettingOn: (QuickSetting) -> Boolean,
    private val pressAlt: () -> Unit,
    private val pressSym: () -> Unit,
    private val pageChanged: () -> Unit,
) : LinearLayout(context) {
    private val typeface = resources.getFont(R.font.inter_regular)
    private val symbols = EmojiPages(listOf(EmojiPages.Group("Symbols", Keys.extraSymbols())), emptyList())
    private val group = headerText(Gravity.START or Gravity.CENTER_VERTICAL)
    private val pageNumber = headerText(Gravity.END or Gravity.CENTER_VERTICAL)
    private val preview = TextView(context).apply {
        typeface = this@SymPanel.typeface
        gravity = Gravity.CENTER
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        // A setting's name must fit the header at any system font size.
        setAutoSizeTextTypeUniformWithConfiguration(PREVIEW_SMALLEST, PREVIEW_LARGEST, 1, TypedValue.COMPLEX_UNIT_SP)
    }
    /** One mark for each group of emoji, across the whole width of the panel. */
    private val categories = LinearLayout(context)
    /** The name of the open group and its page number. */
    private val heading = LinearLayout(context).apply {
        addView(group, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(pageNumber, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }
    private val header = LinearLayout(context)
    private val grid = LinearLayout(context)
    private val cells = (Layout.letterKeys + Layout.ALT + Layout.SYM + Layout.BACKSPACE + Layout.ENTER).associateWith(::cell)
    private var tabs = emptyList<TextView>()
    private var shownSamples = emptyList<String>()
    private var navigationInset = 0

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
        header.setPadding(dp(8), 0, dp(8), 0)
        header.addView(heading, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        header.addView(preview, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(HEADER_HEIGHT)))
        grid.orientation = VERTICAL
        // Each row keeps all ten columns, so that the letter keys stay above the physical letter keys.
        for (row in Layout.rows.take(3)) grid.addView(LinearLayout(context).apply {
            for (key in row) addView(cells[key] ?: Space(context), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT)))
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(categories, LayoutParams(LayoutParams.MATCH_PARENT, dp(CATEGORY_HEIGHT)))
        // Keep the keys clear of the gesture handle, which draws over the bottom of the keyboard.
        setOnApplyWindowInsetsListener { _, insets ->
            navigationInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
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

    private fun headerText(side: Int) = TextView(context).apply {
        typeface = this@SymPanel.typeface
        gravity = side
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
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

    /** Where the open page is in its group, such as 2/9, or null if there is no page. */
    private fun position() = pages?.takeIf { it.pageCount() > 0 }?.let { "${it.pageNumber()}/${it.pageCount()}" }

    /** What a key shows as text, or null if it shows none. */
    private fun label(key: String) = when {
        quick -> QuickSetting.on(key)?.label
        showsPosition(key) -> position()
        else -> item(key)
    }

    private fun describe(item: QuickSetting) =
        if (item == QuickSetting.OpenSettings || item == QuickSetting.ChangeKeyboard) item.title else "${item.title}: ${if (quickSettingOn(item)) "On" else "Off"}"

    /** What the top of the panel says for a key under a finger, or null if it says nothing. */
    private fun detail(key: String) = if (quick) icon(key)?.description ?: QuickSetting.on(key)?.let(::describe) else null

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

    private val dark get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val ink get() = if (dark) Color.WHITE else Color.BLACK
    private val paper get() = if (dark) Color.BLACK else Color.WHITE
    private val muted get() = if (dark) Color.GRAY else Color.DKGRAY
    private val shade get() = if (dark) 0x40FFFFFF else 0x30000000

    /** Shows the panel as it is now, also after a quick setting changes. */
    fun render() {
        visibility = if (mode == Modifiers.Panel.Closed) GONE else VISIBLE
        setBackgroundColor(paper)
        setPadding(dp(4), 0, dp(4), navigationInset)
        val current = pages
        // Only a setting's name needs the large text.
        heading.visibility = if (quick) GONE else VISIBLE
        preview.visibility = if (quick) VISIBLE else GONE
        showCategories(current)
        group.setTextColor(muted)
        group.text = current?.group().orEmpty()
        group.sized(HEADER_SIZE, HEADER_LARGEST)
        preview.setTextColor(ink)
        pageNumber.setTextColor(muted)
        pageNumber.sized(HEADER_SIZE, HEADER_LARGEST)
        val count = current?.pageCount() ?: 0
        pageNumber.text = if (count > 1) "${current?.pageNumber()}/$count" else ""
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
            mode == Modifiers.Panel.Symbols -> "Symbols. Push a key to type its symbol. " +
                "Swipe up or down on the keyboard to see more."
            mode == Modifiers.Panel.Emoji -> "Emoji, ${current?.group().orEmpty()}. Push a key to type its emoji. " +
                "Swipe left or right on the keyboard to change the group, or move a finger along the bottom row. " +
                "Swipe up or down to see more, or push Backspace or Enter."
            else -> null
        }
        showHover()
    }

    /** Shows a mark for each group, and shades the open one, so that you can see the other groups as you swipe. */
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
        // Symbols are one group, so they have no categories to choose from.
        val strip = samples.size > 1
        categories.visibility = if (strip) VISIBLE else GONE
        // The strip takes its height from the header, so that the keyboard stays the same size on every page.
        val headerHeight = dp(if (strip) HEADER_HEIGHT - CATEGORY_HEIGHT else HEADER_HEIGHT)
        if (header.layoutParams.height != headerHeight) header.layoutParams = header.layoutParams.apply { height = headerHeight }
        val open = current?.groupIndex() ?: -1
        tabs.forEachIndexed { index, tab ->
            tab.sized(CATEGORY_SIZE, CATEGORY_LARGEST)
            tab.setBackgroundColor(if (index == open) shade else Color.TRANSPARENT)
        }
    }

    /**
     * A cell under a resting finger changes colour, except in the last column on a page of symbols or emoji.
     * A setting's name also shows at the top of the panel.
     */
    private fun showHover() {
        preview.text = detail(hover) ?: if (quick) QUICK_TITLE else ""
        val marked = hover.takeIf { (label(it) != null || icon(it) != null) && (pages == null || it !in Layout.lastColumn) }
        for ((key, view) in cells) {
            val hovered = key == marked
            view.setBackgroundColor(if (hovered) ink else Color.TRANSPARENT)
            view.setTextColor(if (hovered) paper else if (dim(key)) Color.GRAY else if (showsPosition(key)) muted else ink)
            view.foregroundTintList = ColorStateList.valueOf(if (hovered) paper else ink)
        }
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        render()
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    private companion object {
        const val HEADER_HEIGHT = 56
        const val CATEGORY_HEIGHT = 32
        const val ROW_HEIGHT = 46
        const val HEADER_SIZE = 14f
        const val HEADER_LARGEST = 18f
        const val CATEGORY_SIZE = 15f
        const val CATEGORY_LARGEST = 18f
        const val PREVIEW_SMALLEST = 8
        const val PREVIEW_LARGEST = 36
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
