package com.titankeys.support

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.titankeys.keyboard.GuideFonts
import com.titankeys.keyboard.GuideMotion
import com.titankeys.keyboard.Haptic
import com.titankeys.keyboard.Haptics
import com.titankeys.keyboard.Layout
import com.titankeys.keyboard.Radii
import com.titankeys.keyboard.onPress
import java.text.NumberFormat
import kotlin.math.roundToInt

/** The playground's once-only invitation. The illustration is decorative, so the stats and actions stay readable. */
internal class SupportInviteView(
    context: Context,
    totals: LongArray,
    haptics: Haptics,
    animate: Boolean,
    close: (openSupport: Boolean) -> Unit,
) : FrameLayout(context) {
    private val format = NumberFormat.getIntegerInstance()
    private val scrim = ColorDrawable(Color.BLACK)
    private val art = SupportInviteArt(context)
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
            addView(art, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
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
                art.sweepOnce()
            }
        })
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
        const val CREAM = 0xFFFFF2DC.toInt()
        const val AMBER = 0xFFFFBB76.toInt()
        const val MUTED = 0xFFCDB3A0.toInt()
        const val OUTLINE = 0xFF765445.toInt()
        const val SCRIM_ALPHA = 201
    }
}

private const val ESPRESSO = 0xFF21130F.toInt()

/** The guide's physical keys in the background, with a swipe that curls into steam behind the cup. */
private class SupportInviteArt(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8C8077.toInt()
        textSize = 8f
        textAlign = Paint.Align.CENTER
        typeface = GuideFonts.medium(context)
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val backdrop = RadialGradient(227f, 72f, 145f, 0xFF593321.toInt(), ESPRESSO, Shader.TileMode.CLAMP)
    private val footer = LinearGradient(0f, 120f, 0f, HEIGHT, 0x0021130F, ESPRESSO, Shader.TileMode.CLAMP)
    private val paper = LinearGradient(183f, 0f, 264f, 0f, 0xFFE6C5A9.toInt(), 0xFFFFF0D7.toInt(), Shader.TileMode.CLAMP)
    private val cardboard = LinearGradient(183f, 0f, 264f, 0f, 0xFFB84F24.toInt(), 0xFFF69951.toInt(), Shader.TileMode.CLAMP)
    private val trailColour = LinearGradient(87f, 124f, 260f, 10f, 0xFFF07C37.toInt(), 0xFFFFD2A1.toInt(), Shader.TileMode.CLAMP)
    private val trail = Path().apply {
        moveTo(87f, 124f)
        cubicTo(111f, 125f, 122f, 96f, 145f, 94f)
        cubicTo(166f, 92f, 173f, 110f, 191f, 96f)
        cubicTo(207f, 83f, 211f, 57f, 224f, 42f)
        cubicTo(235f, 29f, 260f, 35f, 260f, 21f)
        cubicTo(260f, 15f, 254f, 11f, 247f, 10f)
    }
    private val trailMeasure = PathMeasure(trail, false)
    private val highlight = Path()
    private val cup = Path().apply {
        moveTo(184f, 51f)
        lineTo(264f, 51f)
        lineTo(253f, 136f)
        quadTo(251f, 143f, 244f, 144f)
        quadTo(224f, 148f, 204f, 144f)
        quadTo(197f, 143f, 195f, 136f)
        close()
    }
    private val sleeve = Path().apply {
        moveTo(181f, 76f)
        quadTo(224f, 82f, 267f, 76f)
        lineTo(261f, 119f)
        quadTo(224f, 126f, 187f, 119f)
        close()
    }
    private val lid = Path().apply {
        moveTo(181f, 46f)
        lineTo(187f, 39f)
        quadTo(189f, 36f, 195f, 36f)
        lineTo(253f, 36f)
        quadTo(259f, 36f, 261f, 39f)
        lineTo(267f, 46f)
        close()
    }
    private var sweep: ValueAnimator? = null
    private var progress = 0f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, (width * HEIGHT / WIDTH).roundToInt())
    }

    fun sweepOnce() {
        if (!GuideMotion.enabled) return
        sweep = ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = 300
            duration = 1_180
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.scale(width / WIDTH, height / HEIGHT)
        fill.shader = backdrop
        canvas.drawRect(0f, 0f, WIDTH, HEIGHT, fill)
        fill.shader = null
        drawKeys(canvas)
        fill.shader = footer
        canvas.drawRect(0f, 120f, WIDTH, HEIGHT, fill)
        fill.shader = null
        drawTrail(canvas)
        drawCup(canvas)
        canvas.restoreToCount(saved)
    }

    private fun drawKeys(canvas: Canvas) {
        val saved = canvas.save()
        canvas.translate(44f, 67f)
        canvas.rotate(-9f, 156f, 40f)
        for ((row, keys) in Layout.rows.withIndex()) {
            var left = 0f
            val top = row * 23f
            for ((column, key) in keys.withIndex()) {
                val span = 31f * if (row == 3) Layout.bottomWidths[column] else 1f
                val right = left + span - 4f
                fill.color = 0xFF100D0B.toInt()
                canvas.drawRoundRect(left, top + 2f, right, top + 21f, 4f, 4f, fill)
                fill.color = 0xFF302822.toInt()
                canvas.drawRoundRect(left, top, right, top + 18f, 4f, 4f, fill)
                fill.color = 0xFF211C19.toInt()
                canvas.drawRoundRect(left + 1f, top + 1f, right - 1f, top + 17f, 3f, 3f, fill)
                val centre = (left + right) / 2f
                if (key == Layout.SPACE) {
                    fill.color = keyLabel.color
                    canvas.drawRoundRect(centre - 8f, top + 9f, centre + 8f, top + 10.5f, 1f, 1f, fill)
                } else {
                    canvas.drawText(key, centre, top + 12f, keyLabel)
                }
                left += span
            }
        }
        canvas.restoreToCount(saved)
    }

    private fun drawTrail(canvas: Canvas) {
        stroke.color = 0x44F07C37
        stroke.strokeWidth = 7f
        canvas.drawPath(trail, stroke)
        stroke.shader = trailColour
        stroke.alpha = 255
        stroke.strokeWidth = 2.2f
        canvas.drawPath(trail, stroke)
        stroke.shader = null
        fill.color = 0xFFF07C37.toInt()
        canvas.drawCircle(87f, 124f, 3.4f, fill)

        if (progress <= 0f || progress >= 1f) return
        val front = trailMeasure.length * progress * 1.2f
        highlight.rewind()
        if (!trailMeasure.getSegment((front - trailMeasure.length * .2f).coerceAtLeast(0f), front, highlight, true)) return
        stroke.color = 0xFFFFE9C6.toInt()
        stroke.strokeWidth = 3.2f
        canvas.drawPath(highlight, stroke)
    }

    private fun drawCup(canvas: Canvas) {
        fill.color = 0x660A0706
        canvas.drawOval(172f, 136f, 276f, 153f, fill)
        fill.shader = paper
        fill.alpha = 255
        canvas.drawPath(cup, fill)
        fill.shader = cardboard
        canvas.drawPath(sleeve, fill)
        fill.shader = null

        val saved = canvas.save()
        canvas.clipPath(sleeve)
        stroke.color = 0x38FFE0BE
        stroke.strokeWidth = .9f
        for (ridge in 0..12) {
            val left = 185f + ridge * 6.5f
            val bottom = 190f + ridge * 5.7f
            canvas.drawLine(left, 76f, bottom, 124f, stroke)
        }
        canvas.restoreToCount(saved)

        fill.color = 0xFF322B27.toInt()
        canvas.drawPath(lid, fill)
        fill.color = 0xFF151211.toInt()
        canvas.drawRoundRect(179f, 44f, 269f, 56f, 4f, 4f, fill)
        fill.color = 0xFF635149.toInt()
        canvas.drawRoundRect(181f, 46f, 267f, 48f, 1f, 1f, fill)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) stop()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun stop() {
        sweep?.cancel()
        sweep = null
        progress = 0f
        invalidate()
    }

    private companion object {
        const val WIDTH = 400f
        const val HEIGHT = 160f
    }
}
