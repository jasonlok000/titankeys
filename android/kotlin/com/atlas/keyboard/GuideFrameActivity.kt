package com.atlas.keyboard

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * One still frame of the new guide's layout, for the owner to review before it is built: chapter one, slide one, halfway through.
 * Everything is hardcoded, at real size, with the guide's own keyboard drawing, fonts and colours, and nothing reflows. It is apart
 * from the guide, and only TitanKeys Debug opens it, from adb:
 *
 *     adb shell am start -n com.titankeys.debug/com.atlas.keyboard.GuideFrameActivity --es state a
 *
 * [STATE] picks the frame: "a" mid-task, "b" with a hint over the instruction, "c" passed.
 */
class GuideFrameActivity : Activity() {
    /** The same sizes at any display size or font size (FixedScale). */
    override fun attachBaseContext(base: Context) = super.attachBaseContext(FixedScale.wrap(base))

    private val heavy: Typeface get() = GuideFonts.heavy(this)
    private val medium: Typeface get() = GuideFonts.medium(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only TitanKeys Debug has the frame. Every other build closes it before it shows anything.
        if (!HapticsLab.available(this)) {
            finish()
            return
        }
        val state = intent.getStringExtra(STATE) ?: "a"
        val passed = state == "c"
        window.setBackgroundDrawable(ColorDrawable(GuideColours.BLACK))
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // Each line has its own fixed height, from its font with room for descenders, and the same gap between them all.
        val titleSize = titleSize()
        val instructionSize = capped(INSTRUCTION_SP, INSTRUCTION_DP)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(topBar(passed), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(TOP_BAR)))
        column.addView(title(passed, titleSize), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            lineHeight(heavy, titleSize)).apply { topMargin = dp(GAP) })
        column.addView(instruction(state == "b", passed, instructionSize), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            lineHeight(medium, instructionSize) + dp(HINT_PAD) * 2).apply { topMargin = dp(GAP) })
        column.addView(field(passed), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(GAP)
        })
        // The rest of the room goes above the drawing, so the drawing and the bar sit at the bottom, as in the guide, where the
        // drawing is the last thing in the column and the keyboard's bar is right under it.
        column.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val keys = GuideKeyboardView(this).apply { tone = GuideTone.Ink }
        column.addView(keys, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { topMargin = dp(8) })

        val root = FrameLayout(this)
        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val bar = Bar(this, if (passed) "" else "keyboard")
        root.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(BAR_HEIGHT), Gravity.BOTTOM))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val corners = Corners.of(insets, display)
            val side = maxOf(dp(Layout.KEY_SIDE_DP) + bars.left, corners.topInset((bars.top + dp(4)).toFloat()))
            // The bar is the keyboard's, at the very bottom; the drawing of the keys sits right above it.
            column.setPadding(side, bars.top + dp(4), side, bars.bottom + dp(BAR_HEIGHT) + dp(8))
            bar.setPadding(0, 0, 0, bars.bottom)
            (bar.layoutParams as FrameLayout.LayoutParams).height = dp(BAR_HEIGHT) + bars.bottom
            root.post { column.requestLayout(); bar.requestLayout() }
            insets
        }
        // The drawing of the keys is as wide as the screen, past the column's sides, so each key sits over its real key, and as
        // tall as the real keyboard is at that width.
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (root.width <= 0) return@addOnLayoutChangeListener
            val target = keys.heightFor(root.width)
            val params = keys.layoutParams as LinearLayout.LayoutParams
            if (params.height != target || params.leftMargin != -column.paddingLeft || params.rightMargin != -column.paddingRight) {
                params.height = target
                params.leftMargin = -column.paddingLeft
                params.rightMargin = -column.paddingRight
                root.post { keys.requestLayout() }
            }
        }
        setContentView(root)
        keys.appear()
    }

    // A second adb launch reaches this screen while it's open: it shows that state.
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    /** A text size in px: [sp], but no bigger than [most] dp, so a large font setting can't push a line out of its place. */
    private fun capped(sp: Float, most: Int) = minOf(sp(sp), dp(most).toFloat())

    /** The height of one line of [face] at [px], from its font's top to its bottom, so nothing is cut. */
    private fun lineHeight(face: Typeface, px: Float): Int {
        val metrics = Paint().apply { typeface = face; textSize = px }.fontMetrics
        return kotlin.math.ceil(metrics.bottom - metrics.top).toInt()
    }

    /** One size for every title, the largest that fits the longest of them, with its check, on one line across the screen. */
    private fun titleSize(): Float {
        val room = resources.displayMetrics.widthPixels - 2 * dp(Layout.KEY_SIDE_DP) - dp(TITLE_SLACK)
        val probe = Paint().apply { typeface = heavy; letterSpacing = -.02f }
        var size = capped(TITLE_LARGEST, TITLE_LARGEST.toInt())
        while (size > sp(TITLE_SMALLEST)) {
            probe.textSize = size
            if (TITLES.all { probe.measureText(it + CHECK_ROOM) <= room }) break
            size -= 1f
        }
        return size
    }

    /** The chapters' progress, the chapter's name, Skip (or Next once passed) and ✕, on one line. */
    private fun topBar(passed: Boolean) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        val segments = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        // Typing is one slide in of four; the other chapters wait.
        val fills = listOf(if (passed) .25f else .12f, 0f, 0f, 0f, 0f)
        for (fill in fills) segments.addView(Segment(context, fill), LinearLayout.LayoutParams(0, dp(SEGMENT_HEIGHT), 1f).apply {
            marginEnd = dp(3)
        })
        addView(segments, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = "Typing"
            typeface = heavy
            isAllCaps = true
            letterSpacing = .12f
            includeFontPadding = false
            maxLines = 1
            setTextColor(GuideMotion.faded(GuideColours.CREAM, CHAPTER_ALPHA))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, capped(11f, 15))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(10)
        })
        addView(TextView(context).apply {
            text = if (passed) "Next" else "Skip"
            typeface = heavy
            gravity = Gravity.CENTER
            maxLines = 1
            // Sized to its text: the font's own padding keeps every letter whole, and a little more round it.
            setPadding(dp(12), dp(3), dp(12), dp(3))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, capped(12f, 15))
            setTextColor(if (passed) GuideColours.INK else GuideMotion.faded(GuideColours.CREAM, PILL_FADED))
            background = GradientDrawable().apply {
                cornerRadius = dp(PILL_HEIGHT).toFloat()
                if (passed) setColor(GuideColours.ORANGE)
                else setStroke(dp(1).coerceAtLeast(2), GuideMotion.faded(GuideColours.CREAM, PILL_FADED))
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(12) })
        addView(ImageView(context).apply {
            setImageResource(R.drawable.panel_close)
            imageTintList = android.content.res.ColorStateList.valueOf(GuideColours.CREAM)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            contentDescription = "Close"
        }, LinearLayout.LayoutParams(dp(TOP_BAR), dp(TOP_BAR)).apply { marginStart = dp(4) })
    }

    /** One line, one size for every title, never wrapping: the size that fits the longest title this frame has. */
    private fun title(passed: Boolean, size: Float) = TextView(this).apply {
        typeface = heavy
        maxLines = 1
        isSingleLine = true
        ellipsize = null
        letterSpacing = -.02f
        includeFontPadding = false
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setTextColor(GuideColours.CREAM)
        text = if (!passed) TITLES[0] else SpannableStringBuilder(TITLES[1]).apply {
            // A small orange check after the line that lands.
            val start = length
            append(" ✓")
            setSpan(ForegroundColorSpan(GuideColours.ORANGE), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(CHECK_SIZE), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** The instruction, one plain line, and the hint that slides over it in state b. */
    private fun instruction(hinted: Boolean, passed: Boolean, size: Float) = FrameLayout(this).apply {
        val line = TextView(context).apply {
            text = if (passed) "Space fixes typos. Keep typing if you like." else "Make a typo, then press Space."
            typeface = medium
            maxLines = 1
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setTextColor(GuideMotion.faded(GuideColours.CREAM, BODY_ALPHA))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        }
        addView(line, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (!hinted) return@apply
        val chip = TextView(context).apply {
            text = "Perfect spelling. Annoying. Make a typo."
            typeface = medium
            maxLines = 1
            isSingleLine = true
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setPadding(dp(12), 0, dp(12), 0)
            setTextColor(GuideColours.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size * HINT_SCALE)
            background = GradientDrawable().apply {
                cornerRadius = dp(PILL_HEIGHT).toFloat()
                setColor(GuideColours.CREAM)
            }
        }
        addView(chip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // It slides in over the instruction from the right, which fades under it.
        chip.translationX = dp(HINT_SLIDE).toFloat()
        chip.alpha = 0f
        chip.animate().translationX(0f).alpha(1f).setStartDelay(HINT_DELAY_MILLIS).setDuration(HINT_MILLIS).start()
        line.animate().alpha(0f).setStartDelay(HINT_DELAY_MILLIS).setDuration(HINT_MILLIS).start()
    }

    /** The field, exactly two lines: what's typed in cream, the cursor, and the rest of the text in grey ahead of it. */
    private fun field(passed: Boolean) = TextView(this).apply {
        val typed = if (passed) "This keyboard " else "This keyb"
        // Every word but "typos" has a small typo that autocorrect really fixes, checked against the engine: no typo of "typos"
        // is fixed, so it stays right.
        val ahead = if (passed) "fxies yuor typos, sp yuo cna kepe pretendign yuo nevr maek ayn."
            else "aord fxies yuor typos, sp yuo cna kepe pretendign yuo nevr maek ayn."
        text = SpannableStringBuilder().apply {
            append(typed)
            setSpan(ForegroundColorSpan(GuideColours.CREAM), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val cursor = length
            append("|")
            setSpan(ForegroundColorSpan(GuideColours.ORANGE), cursor, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val rest = length
            append(ahead)
            setSpan(ForegroundColorSpan(GuideMotion.faded(GuideColours.CREAM, AHEAD_ALPHA)), rest, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // The guide's own field: its font, size and padding, and exactly two lines, so the frame shows its true height.
        typeface = medium
        setLines(2)
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, capped(18f, 24))
        background = GradientDrawable().apply {
            cornerRadius = Radii.px(resources, Radii.MEDIUM)
            setColor(GuideMotion.faded(GuideColours.CREAM, FIELD_FILL))
        }
    }

    /** The keyboard's bar, as the keyboard draws it: three places, only the middle one with a word. */
    private class Bar(context: Context, private val middle: String) : View(context) {
        private val look = Look.of(context)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = context.resources.getFont(R.font.inter_regular)
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, WORD_SIZE, context.resources.displayMetrics)
            textAlign = Paint.Align.CENTER
            color = look.ink
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(look.paper)
            val room = (height - paddingBottom).toFloat()
            val baseline = room / 2 - (paint.ascent() + paint.descent()) / 2
            canvas.drawText(middle, width / 2f, baseline, paint)
        }
    }

    /** One chapter on the top bar: a rounded line, filled from the left as far as [fill]. */
    private class Segment(context: Context, private val fill: Float) : View(context) {
        private val pen = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val radius = height / 2f
            pen.color = GuideMotion.faded(GuideColours.ORANGE, SEGMENT_OFF)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, pen)
            if (fill <= 0f) return
            pen.color = GuideColours.ORANGE
            canvas.drawRoundRect(0f, 0f, maxOf(height.toFloat(), width * fill), height.toFloat(), radius, radius, pen)
        }
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    companion object {
        /** The intent extra that picks the frame: "a", "b" or "c". */
        const val STATE = "state"
        private val TITLES = listOf("Type like a normal person.", "Fixed. Nobody saw a thing.")
        /** Room after a title for the check, so both titles have one size. */
        private const val CHECK_ROOM = " ✓"
        private const val CHECK_SIZE = .7f

        // The frame's fixed heights, in dp.
        private const val TOP_BAR = 32
        private const val PILL_HEIGHT = 24
        private const val BAR_HEIGHT = 56
        /** The same gap between the top bar, the title, the instruction and the field. */
        private const val GAP = 10
        /** Room above and below the instruction, for the hint that slides over it. */
        private const val HINT_PAD = 3
        private const val HINT_SCALE = .94f
        private const val INSTRUCTION_SP = 16f
        private const val INSTRUCTION_DP = 20
        /** Room the title keeps at its end, so it never touches the edge. */
        private const val TITLE_SLACK = 8
        private const val SEGMENT_HEIGHT = 4

        private const val TITLE_LARGEST = 32f
        private const val TITLE_SMALLEST = 16f
        private const val WORD_SIZE = 17f
        private const val CHAPTER_ALPHA = .55f
        private const val BODY_ALPHA = .86f
        private const val PILL_FADED = .6f
        private const val SEGMENT_OFF = .25f
        private const val AHEAD_ALPHA = .38f
        private const val FIELD_FILL = .08f

        private const val HINT_SLIDE = 60
        private const val HINT_DELAY_MILLIS = 400L
        private const val HINT_MILLIS = 260L
    }
}
