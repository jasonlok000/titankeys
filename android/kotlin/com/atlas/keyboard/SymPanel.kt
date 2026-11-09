package com.atlas.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.atlas.engine.EmojiPages
import kotlin.math.min
import kotlin.math.sign

/**
 * The panel that Sym opens: a symbol or an emoji on each letter key of the top three rows, in line with the physical letter
 * keys. The other keys are left empty. A finger that rests on a key shows that key's item larger.
 *
 * While Ctrl is held it shows Ctrl's shortcuts instead. Each key that has a shortcut shows its letter, all other keys are blank,
 * and the header says what each shortcut does. The touchpad reports nothing while a physical key other than Shift is held, so
 * a finger that rests on a key cannot show this. The name of one shortcut shows larger only if a touch does arrive.
 */
class SymPanel(
    context: Context,
    private val pick: (String) -> Unit,
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
        // An item, a shortcut's name and the list of shortcuts must all fit the header at any system font size.
        setAutoSizeTextTypeUniformWithConfiguration(PREVIEW_SMALLEST, PREVIEW_LARGEST, 1, TypedValue.COMPLEX_UNIT_SP)
    }
    /** One mark for each group of emoji. */
    private val categories = LinearLayout(context)
    /** The categories, with the name of the open one and its page number under them. */
    private val info = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(categories, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(LinearLayout(context).apply {
            addView(group, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(pageNumber, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }
    private val grid = LinearLayout(context)
    private val cells = Layout.panelLetters.associateWith(::cell)
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
            field = value
            symPages?.reset()
            hover = ""
            render()
        }

    /** Ctrl's shortcuts cover the symbols or emoji until Ctrl is let go, and those then show again at the same page. */
    var ctrl = false
        set(value) {
            if (field == value) return
            field = value
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

    private val symPages get() = when (mode) {
        Modifiers.Panel.Symbols -> symbols
        Modifiers.Panel.Emoji -> emoji
        Modifiers.Panel.Closed -> null
    }

    private val pages get() = if (ctrl) null else symPages

    init {
        orientation = VERTICAL
        addView(LinearLayout(context).apply {
            setPadding(dp(8), 0, dp(8), 0)
            addView(info, LayoutParams(0, LayoutParams.MATCH_PARENT, 3f))
            addView(preview, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(HEADER_HEIGHT)))
        grid.orientation = VERTICAL
        // Each row keeps all ten columns, so that the letter keys stay above the physical letter keys.
        for (row in Layout.rows.take(3)) grid.addView(LinearLayout(context).apply {
            for (key in row) addView(cells[key] ?: Space(context), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT)))
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
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

    /** The new keys slide in the way the finger went. Android's animator duration setting shortens this or turns it off. */
    private fun slideIn(across: Float, down: Float) {
        val distance = dp(SLIDE_DISTANCE).toFloat()
        val upOrDown = EmojiPages.turnsPage(across, down)
        grid.animate().cancel()
        grid.translationX = if (upOrDown) 0f else -across.sign * distance
        grid.translationY = if (upOrDown) -down.sign * distance else 0f
        grid.alpha = SLIDE_ALPHA
        grid.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(SLIDE_MILLIS).start()
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
        maxLines = 1
        includeFontPadding = false
        isFocusable = false
        setOnClickListener { item(key)?.let(pick) }
    }

    private fun categoryTab(sample: String) = TextView(context).apply {
        text = sample
        gravity = Gravity.CENTER
        isSingleLine = true
        includeFontPadding = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
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

    private fun render() {
        visibility = if (mode == Modifiers.Panel.Closed && !ctrl) GONE else VISIBLE
        setBackgroundColor(paper)
        setPadding(dp(4), 0, dp(4), navigationInset)
        val current = pages
        // What the shortcuts do is longer than a symbol, so it takes the whole header.
        info.visibility = if (ctrl) GONE else VISIBLE
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
            val action = Modifiers.CTRL_ACTIONS[key]
            val value = if (ctrl) action?.let { key } else item(key)
            view.text = value.orEmpty()
            view.sized(ITEM_SIZE, ITEM_LARGEST)
            // A shortcut runs from the key, so a touch on the screen does nothing here.
            view.isEnabled = !ctrl && value != null
            view.contentDescription = if (ctrl) action?.let { "Ctrl $key, $it" } else value?.let { "Insert $it" }
        }
        contentDescription = when {
            ctrl -> "Ctrl shortcuts. $CTRL_LIST. Push a key to use its shortcut."
            mode == Modifiers.Panel.Symbols -> "Symbols. Push a key to type its symbol. Swipe up or down on the keyboard to see more."
            mode == Modifiers.Panel.Emoji -> "Emoji, ${current?.group().orEmpty()}. Push a key to type its emoji. " +
                "Swipe left or right on the keyboard to change the group, or up or down to see more."
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
            tabs = samples.map { sample -> categoryTab(sample).also { categories.addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)) } }
        }
        // Symbols are one group, so they have no categories to choose from.
        categories.visibility = if (samples.size > 1) VISIBLE else GONE
        val open = current?.groupIndex() ?: -1
        tabs.forEachIndexed { index, tab ->
            tab.sized(CATEGORY_SIZE, CATEGORY_LARGEST)
            tab.setBackgroundColor(if (index == open) shade else Color.TRANSPARENT)
        }
    }

    /** A cell under a resting finger changes colour, and its item or shortcut shows larger at the top of the panel. */
    private fun showHover() {
        val shown = if (ctrl) Modifiers.CTRL_ACTIONS[hover] else item(hover)
        preview.text = shown ?: if (ctrl) CTRL_LIST else ""
        for ((key, view) in cells) {
            val hovered = shown != null && key == hover
            view.setBackgroundColor(if (hovered) ink else Color.TRANSPARENT)
            view.setTextColor(if (hovered) paper else ink)
        }
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        render()
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    private companion object {
        const val HEADER_HEIGHT = 56
        const val ROW_HEIGHT = 46
        const val HEADER_SIZE = 14f
        const val HEADER_LARGEST = 18f
        const val CATEGORY_SIZE = 15f
        const val CATEGORY_LARGEST = 18f
        const val PREVIEW_SMALLEST = 8
        const val PREVIEW_LARGEST = 36
        const val ITEM_SIZE = 22f
        const val ITEM_LARGEST = 30f
        const val SLIDE_DISTANCE = 12
        const val SLIDE_ALPHA = .5f
        const val SLIDE_MILLIS = 120L

        /** What each shortcut does. Spaces that cannot wrap keep each shortcut on one line. */
        val CTRL_LIST = Modifiers.CTRL_ACTIONS.entries.joinToString("   ") { (key, action) -> "$key $action".replace(' ', ' ') }
    }
}
