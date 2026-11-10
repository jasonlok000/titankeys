package com.atlas.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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

    /** The clipboard page's clips, newest first, six to a page, in a wide field each. */
    var clips: List<ClipboardHistory.Clip> = emptyList()
        set(value) {
            field = value
            clipPages.fit(value)
            if (clipboard) render()
        }
    /** The open group of the clipboard, Clipboard or Pinned, and its page. */
    private val clipPages = ClipboardHistory.Pages()

    /** The clipboard opens at the first page of Clipboard, as the symbols open at their first page. */
    fun resetClipPages() = clipPages.reset()

    /** On the clipboard page, a push of a letter pins or unpins its clip, and does not paste it. */
    var clipPinning = false
    /** On the clipboard page, Backspace was pushed once, so a second push clears all. */
    var clipClearing = false

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
     * One wide field of the clipboard page, in the place of the letter keys of half a row: the clip in up to two lines that end
     * in "…", with a small pin in its top corner when it is pinned. It sits inside the inner columns, as the symbols do.
     */
    private inner class ClipField(val slot: Int) {
        val text = TextView(context).apply {
            typeface = this@SymPanel.typeface
            maxLines = 2
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
            setOnLongClickListener {
                holdClip(ClipboardHistory.keyFor(slot))
                true
            }
        }
    }
    private val clipFields = List(ClipboardHistory.LIMIT) { ClipField(it) }

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
                if (half != null && ClipboardHistory.firstColumn(index, half) == column) {
                    val field = clipFields[index * 2 + half]
                    addView(field.frame, LayoutParams(0, LayoutParams.MATCH_PARENT, ClipboardHistory.width(index, half).toFloat()).apply {
                        setMargins(dp(2), dp(3), dp(2), dp(3))
                    })
                }
                addView(cells[key] ?: Space(context), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            }
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
        val moved = if (clipboard) clipPages.swipe(clips, across, down) else pages?.swipe(across, down) == true
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
        setOnLongClickListener {
            if (clipAt(key) == null) return@setOnLongClickListener false
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
            Layout.BACKSPACE -> context.getString(R.string.clipboard_clear).takeIf { clips.any { !it.pinned } }
            Layout.ENTER -> context.getString(if (clipPinning) R.string.clipboard_pin_done else R.string.clipboard_pin).takeIf { clips.isNotEmpty() }
            else -> if (showsPosition(key)) position() else null
        }
        showsPosition(key) -> position()
        else -> item(key)
    }

    private fun describe(item: QuickSetting) =
        if (item == QuickSetting.OpenSettings || item == QuickSetting.ChangeKeyboard || item == QuickSetting.Clipboard) item.title else "${item.title}: ${if (quickSettingOn(item)) "On" else "Off"}"

    /** What the strip at the bottom says for a key under a finger on the quick settings page, or null if it says nothing. */
    private fun detail(key: String) = if (quick) QuickSetting.on(key)?.let(::describe)
        else clipAt(key)?.let { clip ->
            ClipboardHistory.preview(clip.text).let { if (clip.pinned) context.getString(R.string.clipboard_pinned, it) else it }
        }

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
            quick -> "$QUICK_TITLE. Rest a finger on a key to see its setting. Push the key to change the setting."
            clipboard -> context.getString(R.string.clipboard_description)
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
        // The clipboard has two groups, named in words, where the symbols and emoji show one of their own.
        val groups = listOf(R.string.clipboard_group_recent, R.string.clipboard_group_pinned).map(context::getString)
        val samples = if (clipboard) groups else current?.samples().orEmpty()
        if (samples != shownSamples) {
            shownSamples = samples
            categories.removeAllViews()
            val names = if (clipboard) groups else current?.groupNames().orEmpty()
            tabs = samples.mapIndexed { index, sample ->
                categoryTab(sample, names[index], index).also { categories.addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)) }
            }
        }
        val open = if (clipboard) clipPages.group else current?.groupIndex() ?: -1
        tabs.forEachIndexed { index, tab ->
            tab.sized(CATEGORY_SIZE, CATEGORY_LARGEST)
            tab.setTextColor(look.ink)
            tab.setBackgroundColor(if (index == open) look.wash else Color.TRANSPARENT)
        }
    }

    /** A letter key under a resting finger takes the accent, as [Layout.restMarks] says. A setting's name shows in the strip at the bottom. */
    private fun showHover() {
        if (clipboard) {
            // The whole clip under a finger, or a message, takes the place of the groups, in the middle as on the quick settings.
            val text = clipBandText()
            preview.text = text.orEmpty()
            preview.visibility = if (text != null) VISIBLE else GONE
            categories.visibility = if (text != null) GONE else VISIBLE
        } else preview.text = detail(hover) ?: if (quick) QUICK_TITLE else ""
        showClipFields()
        // A finger on a clip marks its whole field, as a finger on a symbol marks its key.
        val mark = clipIndex(hover)?.let { "clip$it" }
            ?: hover.takeIf { Layout.restMarks(it, pages != null) && label(it) != null }.orEmpty()
        for ((key, view) in cells) {
            val hovered = key == mark
            view.setBackgroundColor(if (hovered) look.accent else Color.TRANSPARENT)
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
            if (!clipboard || clip == null) continue
            val lit = under == index
            field.text.text = ClipboardHistory.preview(clip.text, CLIP_PREVIEW)
            field.text.sized(CLIP_TEXT_SIZE, CLIP_TEXT_LARGEST)
            field.text.setTextColor(if (lit) look.onAccent else look.ink)
            field.frame.background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(if (lit) look.accent else look.wash)
                if (clipPinning && !lit) setStroke(dp(1).coerceAtLeast(2), look.accent)
            }
            field.pinMark.visibility = if (clip.pinned) VISIBLE else GONE
            field.pinMark.imageTintList = ColorStateList.valueOf(if (lit) look.onAccent else look.accent)
            field.frame.contentDescription = context.getString(R.string.clipboard_paste, ClipboardHistory.preview(clip.text))
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
        /** A clip in its field: two lines that fit the row at any system font size. */
        const val CLIP_TEXT_SIZE = 15f
        const val CLIP_TEXT_LARGEST = 16f
        /** Enough of a clip for two lines. The field ends it with "…" where it runs out of room. */
        const val CLIP_PREVIEW = 160
        const val CLIP_PIN = 11
        const val QUICK_TITLE = "Quick settings"
        const val SLIDE_DISTANCE = 12
        const val SLIDE_ALPHA = .5f
        const val SLIDE_MILLIS = 120L
    }
}
