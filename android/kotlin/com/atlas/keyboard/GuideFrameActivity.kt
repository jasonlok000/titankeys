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
    private val heavy: Typeface = Typeface.create(Typeface.DEFAULT, 900, false)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only TitanKeys Debug has the frame. Every other build closes it before it shows anything.
        if (!HapticsLab.available(this)) {
            finish()
            return
        }
        val state = intent.getStringExtra(STATE) ?: "a"
        val passed = state == "c"
        window.setBackgroundDrawable(ColorDrawable(GuideColours.INK))
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(topBar(passed), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(TOP_BAR)))
        column.addView(title(passed), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(TITLE_HEIGHT)).apply {
            topMargin = dp(10)
        })
        column.addView(instruction(state == "b", passed), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(INSTRUCTION_HEIGHT)).apply {
            topMargin = dp(4)
        })
        column.addView(field(passed), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })
        // The rest of the room goes above the drawing, so the drawing and the bar sit at the bottom as the keyboard would.
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
            val width = root.width - 2 * side
            if (width > 0) (keys.layoutParams as LinearLayout.LayoutParams).height = (width * KEYS_ASPECT).toInt()
            root.post { column.requestLayout(); bar.requestLayout() }
            insets
        }
        setContentView(root)
        keys.appear()
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(10)
        })
        addView(TextView(context).apply {
            text = if (passed) "Next" else "Skip"
            typeface = heavy
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(if (passed) GuideColours.INK else GuideMotion.faded(GuideColours.CREAM, PILL_FADED))
            background = GradientDrawable().apply {
                cornerRadius = dp(PILL_HEIGHT).toFloat()
                if (passed) setColor(GuideColours.ORANGE)
                else setStroke(dp(1).coerceAtLeast(2), GuideMotion.faded(GuideColours.CREAM, PILL_FADED))
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(PILL_HEIGHT)).apply { marginStart = dp(12) })
        addView(ImageView(context).apply {
            setImageResource(R.drawable.panel_close)
            imageTintList = android.content.res.ColorStateList.valueOf(GuideColours.CREAM)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            contentDescription = "Close"
        }, LinearLayout.LayoutParams(dp(TOP_BAR), dp(TOP_BAR)).apply { marginStart = dp(4) })
    }

    /** One line, one size for every title, never wrapping: the size that fits the longest title this frame has. */
    private fun title(passed: Boolean) = object : TextView(this) {
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val room = (w - paddingLeft - paddingRight).toFloat()
            val probe = Paint(getPaint()).apply { typeface = heavy }
            var size = sp(TITLE_LARGEST)
            while (size > sp(TITLE_SMALLEST)) {
                probe.textSize = size
                if (TITLES.all { probe.measureText(it + CHECK_ROOM) <= room }) break
                size -= 1f
            }
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        }
    }.apply {
        typeface = heavy
        maxLines = 1
        isSingleLine = true
        ellipsize = null
        letterSpacing = -.02f
        includeFontPadding = false
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
    private fun instruction(hinted: Boolean, passed: Boolean) = FrameLayout(this).apply {
        val line = TextView(context).apply {
            text = if (passed) "Space fixes typos. Keep typing if you like." else "Make a typo, then press Space."
            typeface = medium
            maxLines = 1
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setTextColor(GuideMotion.faded(GuideColours.CREAM, BODY_ALPHA))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = GradientDrawable().apply {
                cornerRadius = dp(INSTRUCTION_HEIGHT).toFloat()
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
        val ahead = if (passed) "fixes your typos, so you can keep pretending you never make any."
            else "aord fixes your typos, so you can keep pretending you never make any."
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
        typeface = medium
        setLines(2)
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        setLineSpacing(dp(4).toFloat(), 1f)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
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
        private const val TITLE_HEIGHT = 40
        private const val INSTRUCTION_HEIGHT = 28
        private const val BAR_HEIGHT = 56
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
        private const val KEYS_ASPECT = .31f

        private const val HINT_SLIDE = 60
        private const val HINT_DELAY_MILLIS = 400L
        private const val HINT_MILLIS = 260L
    }
}
