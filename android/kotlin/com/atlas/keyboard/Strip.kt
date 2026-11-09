package com.atlas.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView

/** The bar above the keyboard: word suggestions. */
class Strip(
    context: Context,
    private val choose: (Int) -> Unit,
) : LinearLayout(context) {
    private val typeface = resources.getFont(R.font.inter_regular)
    private val wordViews = WORD_ORDER.map { index -> item { choose(index) } }
    private var navigationInset = 0

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

    init {
        for (view in wordViews) addView(view, LayoutParams(0, dp(56), 1f))
        // Screen readers go left to right, so point them at the best word first.
        val ranked = wordViews.withIndex().sortedBy { WORD_ORDER[it.index] }.map { it.value }
        for (view in ranked) view.id = generateViewId()
        ranked.zipWithNext { view, next -> view.accessibilityTraversalBefore = next.id }
        // Keep the words clear of the gesture handle, which draws over the bottom of the keyboard.
        setOnApplyWindowInsetsListener { _, insets ->
            navigationInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            render()
            insets
        }
        render()
    }

    private fun item(press: (TextView) -> Unit) = TextView(context).apply {
        typeface = this@Strip.typeface
        textSize = 17f
        gravity = Gravity.CENTER
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        isFocusable = false
        setPadding(dp(6), 0, dp(6), 0)
        setOnClickListener { press(this) }
    }

    private fun render() {
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val ink = if (dark) Color.WHITE else Color.BLACK
        setBackgroundColor(if (!showSuggestions) Color.TRANSPARENT else if (dark) Color.BLACK else Color.WHITE)
        setPadding(dp(8), 0, dp(8), if (showSuggestions) navigationInset else 0)
        // Keep a window for firmware touchpad routing when there is nothing to display.
        minimumHeight = if (showSuggestions) 0 else 1
        for ((slot, view) in wordViews.withIndex()) {
            val index = WORD_ORDER[slot]
            view.visibility = if (showSuggestions) VISIBLE else GONE
            view.text = words.getOrElse(index) { "" }
            view.isEnabled = index < words.size
            view.setTextColor(ink)
        }
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        render()
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    companion object {
        private val WORD_ORDER = listOf(1, 0, 2)
    }
}
