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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.titankeys.keyboard.GuideFonts
import com.titankeys.keyboard.GuideMotion
import com.titankeys.keyboard.Haptic
import com.titankeys.keyboard.Haptics
import com.titankeys.keyboard.R
import com.titankeys.keyboard.Radii
import com.titankeys.keyboard.onPress
import java.text.NumberFormat
import kotlin.math.roundToInt

/** The playground's once-only invitation. The photo is decorative, so the stats and actions stay native and readable. */
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
    private val card = ScrollView(context).apply {
        background = shape(ESPRESSO, Radii.LARGE)
        clipToOutline = true
        isVerticalScrollBarEnabled = false
        accessibilityPaneTitle = TITLE
        elevation = dp(12).toFloat()
    }

    init {
        // The playground remains visible underneath, but a tap there can't reach it or dismiss the invite.
        isClickable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        background = scrim
        scrim.alpha = SCRIM_ALPHA
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(art(), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        card.addView(panel)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(14), dp(28), dp(24))
        }
        panel.addView(content, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        content.line(label(TITLE, 30f, CREAM, GuideFonts.heavy(context)).apply { isAccessibilityHeading = true })

        val displayed = SupportCard.displayed(totals)
        content.line(stat(displayed.first(), hero = true), gap = 14)
        if (displayed.size > 1) {
            val others = LinearLayout(context).apply { gravity = Gravity.CENTER }
            for (metric in displayed.drop(1)) {
                others.addView(stat(metric, hero = false), LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            }
            content.line(others, gap = 14)
        }
        content.line(label("If TitanKeys has earned a coffee, you can buy it one.", 19f, CREAM), gap = 20)

        val actions = LinearLayout(context)
        actions.addView(action("Buy me a coffee", primary = true).apply {
            onPress(haptics) { close(true) }
        }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 2f))
        actions.addView(action("Nah", primary = false).apply {
            onPress(haptics, Haptic.AppRefused) { close(false) }
        }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) })
        content.line(actions, gap = 20)

        setOnApplyWindowInsetsListener { _, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            setPadding(safe.left + dp(20), safe.top + dp(16), safe.right + dp(20), safe.bottom + dp(16))
            insets
        }
        if (animate && GuideMotion.enabled) enterFromAbove()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        card.layoutParams.width = available.coerceIn(0, dp(596))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDetachedFromWindow() {
        scrimFade?.cancel()
        scrimFade = null
        card.animate().cancel()
        scrim.alpha = SCRIM_ALPHA
        card.translationY = 0f
        super.onDetachedFromWindow()
    }

    private fun enterFromAbove() {
        scrim.alpha = 0
        card.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                card.removeOnLayoutChangeListener(this)
                if (!GuideMotion.enabled) {
                    scrim.alpha = SCRIM_ALPHA
                    return
                }
                card.translationY = -bottom.toFloat()
                card.animate().translationY(0f).setDuration(360).setInterpolator(DecelerateInterpolator(1.55f)).start()
                scrimFade = ValueAnimator.ofInt(0, SCRIM_ALPHA).apply {
                    duration = 220
                    addUpdateListener { scrim.alpha = it.animatedValue as Int }
                    start()
                }
            }
        })
    }

    private fun art() = object : FrameLayout(context) {
        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(R.drawable.support_coffee)
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(View(context).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(ESPRESSO_CLEAR, ESPRESSO))
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(42), Gravity.BOTTOM))
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val height = (MeasureSpec.getSize(widthMeasureSpec) * 0.4f).roundToInt()
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        }
    }

    private fun stat(metric: IndexedValue<Long>, hero: Boolean): LinearLayout {
        val count = SupportStats.Count.entries[metric.index]
        val name = when (count) {
            SupportStats.Count.Words -> if (metric.value == 1L) "word typed" else "words typed"
            SupportStats.Count.Swipes -> if (metric.value == 1L) "swipe" else "swipes"
            SupportStats.Count.Corrections -> if (metric.value == 1L) "autocorrect" else "autocorrects"
            SupportStats.Count.Caps -> if (metric.value == 1L) "auto-capital" else "auto-capitals"
            SupportStats.Count.Gestures -> if (metric.value == 1L) "gesture" else "gestures"
        }
        val number = format.format(metric.value)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isScreenReaderFocusable = true
            contentDescription = "$number $name"
            line(label(number, if (hero) 54f else 27f, if (hero) AMBER else CREAM, GuideFonts.heavy(context)).apply {
                fontFeatureSettings = "tnum"
                maxLines = 1
                setAutoSizeTextTypeUniformWithConfiguration(13, if (hero) 54 else 27, 1, TypedValue.COMPLEX_UNIT_DIP)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            line(label(name, if (hero) 17f else 15f, MUTED, GuideFonts.medium(context)).apply {
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, gap = if (hero) 3 else 5)
        }
    }

    private fun action(word: String, primary: Boolean) = Button(context).apply {
        text = word
        isAllCaps = false
        typeface = GuideFonts.heavy(context)
        setTextColor(if (primary) ESPRESSO else CREAM)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        minimumHeight = dp(52)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        stateListAnimator = null
        val surface = shape(if (primary) AMBER else Color.TRANSPARENT, Radii.LARGE).apply {
            if (!primary) setStroke(dp(1).coerceAtLeast(1), OUTLINE)
        }
        background = RippleDrawable(ColorStateList.valueOf(if (primary) 0x31261309 else 0x30FFF2DC), surface,
            shape(Color.WHITE, Radii.LARGE))
    }

    private fun label(word: String, size: Float, colour: Int, font: Typeface = GuideFonts.regular(context)) =
        TextView(context).apply {
            text = word
            typeface = font
            includeFontPadding = false
            gravity = Gravity.CENTER
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
        const val TITLE = "Two weeks with TitanKeys"
        const val ESPRESSO = 0xFF21130F.toInt()
        const val ESPRESSO_CLEAR = 0x0021130F
        const val CREAM = 0xFFFFF2DC.toInt()
        const val AMBER = 0xFFFFBB76.toInt()
        const val MUTED = 0xFFCDB3A0.toInt()
        const val OUTLINE = 0xFF765445.toInt()
        const val SCRIM_ALPHA = 201
    }
}
