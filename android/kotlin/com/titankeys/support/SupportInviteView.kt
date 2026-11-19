package com.titankeys.support

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.titankeys.keyboard.GuideFonts
import com.titankeys.keyboard.GuideMotion
import com.titankeys.keyboard.Haptic
import com.titankeys.keyboard.Haptics
import com.titankeys.keyboard.Radii
import com.titankeys.keyboard.onPress
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * The once-only card over the front page, after about ten typing days (owner-approved support flow v4): a line, up to four of the
 * device's own counts counting up, a soft way to the coffee fund, and Nah. Small and friendly, never the support page itself.
 */
internal class SupportInviteView(
    context: Context,
    totals: LongArray,
    haptics: Haptics,
    animate: Boolean,
    close: (openSupport: Boolean) -> Unit,
) : FrameLayout(context) {
    private val format = NumberFormat.getIntegerInstance()
    private val scrim = ColorDrawable(Color.BLACK)
    private var scrimFade: ValueAnimator? = null
    private val counters = mutableListOf<Pair<TextView, Long>>()
    private val card = ScrollView(context).apply {
        background = shape(RAISED, Radii.LARGE).apply { setStroke(dp(1).coerceAtLeast(1), LINE) }
        clipToOutline = true
        isVerticalScrollBarEnabled = false
        accessibilityPaneTitle = TITLE
        elevation = dp(12).toFloat()
    }

    init {
        // The front page stays visible underneath, but a tap there can't reach it or dismiss the card.
        isClickable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        background = scrim
        scrim.alpha = SCRIM_ALPHA
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(24), dp(26), dp(18))
        }
        card.addView(content)
        content.line(label(TITLE, 25f, CREAM, GuideFonts.heavy(context), Gravity.START).apply { isAccessibilityHeading = true })

        // Up to four nonzero counts, two to a row.
        // F's four tiles: words typed, swipes, fixes and gestures (auto-capitals aren't one of them).
        val shown = SupportCard.displayed(totals.copyOf().also { it[SupportStats.Count.Caps.ordinal] = 0 })
        for (pair in shown.chunked(2)) {
            val row = LinearLayout(context)
            for ((index, metric) in pair.withIndex()) {
                row.addView(tile(metric), LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = dp(8)
                })
            }
            if (pair.size == 1) row.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = dp(8) })
            content.line(row, gap = if (pair === shown.chunked(2).first()) 18 else 8)
        }
        content.line(action(SEE_FUND).apply { onPress(haptics) { close(true) } }, gap = 16)
        content.line(action(NAH, quiet = true).apply { onPress(haptics, Haptic.AppRefused) { close(false) } }, gap = 4)

        setOnApplyWindowInsetsListener { _, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            setPadding(safe.left + dp(22), safe.top + dp(16), safe.right + dp(22), safe.bottom + dp(16))
            insets
        }
        if (animate && GuideMotion.enabled) enter() else counters.forEach { (view, to) -> view.text = format.format(to) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        card.layoutParams.width = available.coerceIn(0, dp(560))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDetachedFromWindow() {
        scrimFade?.cancel()
        scrimFade = null
        card.animate().cancel()
        scrim.alpha = SCRIM_ALPHA
        card.scaleX = 1f
        card.scaleY = 1f
        card.alpha = 1f
        super.onDetachedFromWindow()
    }

    /** The card pops in, the scrim fades up, and the counts run up from zero. */
    private fun enter() {
        scrim.alpha = 0
        card.alpha = 0f
        card.scaleX = .92f
        card.scaleY = .92f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(DecelerateInterpolator(1.6f)).start()
        scrimFade = ValueAnimator.ofInt(0, SCRIM_ALPHA).apply {
            duration = 220
            addUpdateListener { scrim.alpha = it.animatedValue as Int }
            start()
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1300
            startDelay = 200
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                for ((view, to) in counters) view.text = format.format((to * t).roundToInt().toLong().coerceAtMost(to))
            }
            start()
        }
    }

    private fun tile(metric: IndexedValue<Long>): LinearLayout {
        val count = SupportStats.Count.entries[metric.index]
        val one = metric.value == 1L
        val name = when (count) {
            SupportStats.Count.Words -> if (one) "word typed" else "words typed"
            SupportStats.Count.Swipes -> if (one) "swipe" else "swipes"
            SupportStats.Count.Corrections -> if (one) "fix" else "fixes"
            SupportStats.Count.Caps -> if (one) "auto-capital" else "auto-capitals"
            SupportStats.Count.Gestures -> if (one) "gesture" else "gestures"
        }
        val number = label("0", 26f, com.titankeys.keyboard.Accent.colour, GuideFonts.heavy(context), Gravity.START).apply {
            fontFeatureSettings = "tnum"
            maxLines = 1
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        counters += number to metric.value
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(SURFACE, Radii.MEDIUM)
            setPadding(dp(14), dp(12), dp(14), dp(10))
            isScreenReaderFocusable = true
            contentDescription = "${format.format(metric.value)} $name"
            line(number)
            line(label(name, 14f, MUTED, gravity = Gravity.START).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }, gap = 3)
        }
    }

    /** The way to the fund is soft: an orange outline, not a filled button. Nah is plain text. */
    private fun action(word: String, quiet: Boolean = false) = Button(context).apply {
        text = word
        isAllCaps = false
        typeface = GuideFonts.heavy(context)
        setTextColor(if (quiet) MUTED else com.titankeys.keyboard.Accent.colour)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (quiet) 17f else 18f)
        minimumHeight = dp(52)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        stateListAnimator = null
        val surface = shape(Color.TRANSPARENT, Radii.LARGE).apply {
            if (!quiet) setStroke(dp(2).coerceAtLeast(1), com.titankeys.keyboard.Accent.colour)
        }
        background = RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), surface, shape(Color.WHITE, Radii.LARGE))
    }

    private fun label(word: String, size: Float, colour: Int, font: Typeface = GuideFonts.regular(context), gravity: Int = Gravity.CENTER) =
        TextView(context).apply {
            text = word
            typeface = font
            includeFontPadding = false
            this.gravity = gravity
            setTextColor(colour)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        }

    private fun LinearLayout.line(view: View, gap: Int = 0) {
        addView(view, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(gap) })
    }

    private fun shape(colour: Int, radius: Int) = GradientDrawable().apply {
        setColor(colour)
        cornerRadius = Radii.px(resources, radius)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        // F's words (docs/support-text.md), coffee only.
        const val TITLE = "Look at all that typing. Here's what TitanKeys did for you so far."
        const val SEE_FUND = "See the coffee fund"
        const val NAH = "Nah"
        const val CREAM = 0xFFFFF1E6.toInt()
        const val MUTED = 0xFF99918A.toInt()
        const val RAISED = 0xFF1C1714.toInt()
        const val SURFACE = 0xFF110E0C.toInt()
        const val LINE = 0xFF2A2623.toInt()
        const val SCRIM_ALPHA = 190
    }
}
