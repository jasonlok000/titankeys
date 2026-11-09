package com.atlas.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView
import com.atlas.engine.EmojiPages

/**
 * The panel that Sym opens: the top three rows of the keyboard, with a symbol or an emoji in place of each letter.
 * The other keys in those rows keep their own labels. A finger that rests on a key shows that key's item larger.
 */
class SymPanel(
    context: Context,
    private val pick: (String) -> Unit,
) : LinearLayout(context) {
    private val typeface = resources.getFont(R.font.inter_regular)
    private val symbols = EmojiPages(listOf(EmojiPages.Group("Symbols", Keys.extraSymbols())), emptyList())
    private val group = headerText(Gravity.START or Gravity.CENTER_VERTICAL)
    private val preview = headerText(Gravity.CENTER).apply { textSize = PREVIEW_SIZE }
    private val pageNumber = headerText(Gravity.END or Gravity.CENTER_VERTICAL)
    private val cells = Layout.rows.take(3).flatten().associateWith(::cell)
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
            pages?.reset()
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
        Modifiers.Panel.Closed -> null
    }

    init {
        orientation = VERTICAL
        addView(LinearLayout(context).apply {
            setPadding(dp(8), 0, dp(8), 0)
            addView(group, LayoutParams(0, LayoutParams.MATCH_PARENT, 2f))
            addView(preview, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(pageNumber, LayoutParams(0, LayoutParams.MATCH_PARENT, 2f))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(HEADER_HEIGHT)))
        for (row in Layout.rows.take(3)) addView(LinearLayout(context).apply {
            for (key in row) addView(cells.getValue(key), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT)))
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
        if (moved) render()
        return moved
    }

    private fun headerText(side: Int) = TextView(context).apply {
        typeface = this@SymPanel.typeface
        textSize = HEADER_SIZE
        gravity = side
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    private fun cell(key: String) = TextView(context).apply {
        typeface = this@SymPanel.typeface
        gravity = Gravity.CENTER
        isSingleLine = true
        includeFontPadding = false
        isFocusable = false
        if (Layout.isLetter(key)) {
            textSize = ITEM_SIZE
            setOnClickListener { item(key)?.let(pick) }
        } else {
            textSize = LABEL_SIZE
            text = key
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    private val dark get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val ink get() = if (dark) Color.WHITE else Color.BLACK
    private val paper get() = if (dark) Color.BLACK else Color.WHITE
    private val muted get() = if (dark) Color.GRAY else Color.DKGRAY

    private fun render() {
        visibility = if (mode == Modifiers.Panel.Closed) GONE else VISIBLE
        setBackgroundColor(paper)
        setPadding(dp(4), 0, dp(4), navigationInset)
        val current = pages
        group.setTextColor(muted)
        group.text = current?.group().orEmpty()
        preview.setTextColor(ink)
        pageNumber.setTextColor(muted)
        val count = current?.pageCount() ?: 0
        pageNumber.text = if (count > 1) "${current?.pageNumber()}/$count" else ""
        for ((key, view) in cells) {
            if (!Layout.isLetter(key)) continue
            val value = item(key)
            view.text = value.orEmpty()
            view.isEnabled = value != null
            view.contentDescription = value?.let { "Insert $it" }
        }
        contentDescription = when (mode) {
            Modifiers.Panel.Symbols -> "Symbols. Push a key to type its symbol. Swipe up or down on the keyboard to see more."
            Modifiers.Panel.Emoji -> "Emoji, ${current?.group().orEmpty()}. Push a key to type its emoji. " +
                "Swipe left or right on the keyboard to change the group, or up or down to see more."
            Modifiers.Panel.Closed -> null
        }
        showHover()
    }

    /** A cell under a resting finger changes colour, and its item shows larger at the top of the panel. */
    private fun showHover() {
        val shown = item(hover)
        preview.text = shown.orEmpty()
        for ((key, view) in cells) {
            val hovered = shown != null && key == hover
            view.setBackgroundColor(if (hovered) ink else Color.TRANSPARENT)
            view.setTextColor(if (hovered) paper else if (Layout.isLetter(key)) ink else muted)
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
        const val PREVIEW_SIZE = 36f
        const val ITEM_SIZE = 22f
        const val LABEL_SIZE = 15f
    }
}
