package com.atlas.keyboard

import android.app.Activity
import android.text.StaticLayout
import android.text.style.UnderlineSpan
import android.animation.ValueAnimator
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.atlas.connected.Connected
import com.atlas.connected.Contributions
import com.atlas.engine.TypingSettings
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** The heavy diagonal of the logo's swipe line, in degrees. A line at this angle rises towards the right. */
private const val SWEEP_ANGLE = -40.5f

/** The full-bleed background. A new colour comes in behind the cream line, at the angle of the logo. */
internal class GuideBackdrop(context: Context) : View(context) {
    private val paint = Paint()
    private var current = GuideColours.ORANGE
    private var next = GuideColours.ORANGE
    private var start = -1L

    fun show(colour: Int) {
        current = colour
        next = colour
        start = -1
        invalidate()
    }

    fun wipeTo(colour: Int) {
        if (colour == next && start < 0) return
        current = next
        next = colour
        start = GuideMotion.now()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(current)
        if (start < 0) return
        val t = GuideMotion.progress(start, SWEEP_MILLIS)
        if (t >= 1f) {
            canvas.drawColor(next)
            current = next
            start = -1
            return
        }
        val reach = hypot(width.toFloat(), height.toFloat())
        val cx = width / 2f
        val cy = height / 2f
        val front = -reach / 2 + reach * GuideMotion.easeOut(t)
        canvas.save()
        canvas.rotate(SWEEP_ANGLE, cx, cy)
        paint.color = next
        canvas.drawRect(-reach, -reach, reach * 2, cy + front, paint)
        canvas.restore()
        postInvalidateOnAnimation()
    }

    companion object {
        const val SWEEP_MILLIS = 620L
    }
}

/** What crosses in front of everything: the cream line, the mark and the flourish at the end. It takes no touches. */
internal class GuideOverlay(context: Context) : View(context) {
    private class Sweep(val start: Long, val thickness: Float)

    private val sweeps = ArrayList<Sweep>()
    private val cream = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GuideColours.CREAM }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mark = logoPath(MARK)
    private val line = logoPath(LINE)
    private var markStart = -1L
    private var markEnd = -1L
    private var markColour = GuideColours.INK

    init {
        isClickable = false
        isFocusable = false
    }

    /** Sends the cream line across the screen, after [delay] ms. */
    fun sweep(delay: Long = 0, thickness: Float = THICKNESS) {
        if (!GuideMotion.enabled) return
        sweeps += Sweep(GuideMotion.now() + delay, dp(thickness))
        invalidate()
    }

    /** The logo appears in the wake of a line. */
    fun showMark(colour: Int) {
        markColour = colour
        markStart = GuideMotion.now()
        markEnd = -1
        sweep(thickness = THICKNESS * 1.6f)
        invalidate()
    }

    fun hideMark() {
        if (markStart < 0) return
        markEnd = GuideMotion.now()
        invalidate()
    }

    private var wordmark: Wordmark? = null

    private var washStart = -1L
    private val washPaint = Paint()

    /** A short full-screen wash of [colour] as a slide passes (owner, v5): up fast, then away, about 0.4 s. */
    fun wash(colour: Int) {
        if (!GuideMotion.enabled) return
        washPaint.color = colour
        washStart = GuideMotion.now()
        invalidate()
    }

    /** True while the wash still shows. */
    private fun drawWash(canvas: Canvas): Boolean {
        if (washStart < 0) return false
        val t = GuideMotion.progress(washStart, WASH_MILLIS)
        if (t >= 1f) {
            washStart = -1
            return false
        }
        val strength = if (t < WASH_PEAK) t / WASH_PEAK else 1f - (t - WASH_PEAK) / (1f - WASH_PEAK)
        val colour = washPaint.color
        washPaint.color = GuideMotion.faded(colour, WASH_ALPHA * strength)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), washPaint)
        washPaint.color = colour
        return true
    }

    /** The two halves of a name, the [left] one in from the left side and the [right] one in from the right. */
    private class Wordmark(val left: String, val right: String, val paint: Paint, val x: Float, val baseline: Float,
                           val start: Long, val locked: () -> Unit)

    /**
     * [left] flies in from the left side and [right] from the right side, and they lock together at [x] and [baseline], where
     * the title shows the whole name with this [paint]. Then [locked] runs, and the title takes over from the drawing.
     */
    fun flyIn(left: String, right: String, paint: Paint, x: Float, baseline: Float, locked: () -> Unit) {
        if (!GuideMotion.enabled) {
            locked()
            return
        }
        wordmark = Wordmark(left, right, Paint(paint), x, baseline, GuideMotion.now(), locked)
        invalidate()
    }

    /** True while the halves are still on their way. */
    private fun drawWordmark(canvas: Canvas, mark: Wordmark): Boolean {
        val t = GuideMotion.progress(mark.start, WORDMARK_MILLIS)
        if (t >= 1f) {
            wordmark = null
            // Views change when the halves lock, so that waits until this drawing is done.
            post { mark.locked() }
            return false
        }
        // Fast at first and slow at the end, so that the halves land softly, then click together.
        val left = 1f - GuideMotion.easeOut(t)
        val travel = width * WORDMARK_TRAVEL
        val colour = mark.paint.color
        mark.paint.color = GuideMotion.faded(colour, min(1f, t * WORDMARK_FADE_IN))
        canvas.drawText(mark.left, mark.x - travel * left, mark.baseline, mark.paint)
        canvas.drawText(mark.right, mark.x + mark.paint.measureText(mark.left) + travel * left, mark.baseline, mark.paint)
        mark.paint.color = colour
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val reach = hypot(width.toFloat(), height.toFloat())
        val cx = width / 2f
        val cy = height / 2f
        var moving = false
        if (drawWash(canvas)) moving = true
        if (markStart >= 0) moving = drawMark(canvas, reach, cx, cy)
        wordmark?.let { if (drawWordmark(canvas, it)) moving = true }
        val each = sweeps.iterator()
        while (each.hasNext()) {
            val sweep = each.next()
            val t = GuideMotion.progress(sweep.start, GuideBackdrop.SWEEP_MILLIS)
            if (t >= 1f) {
                each.remove()
                continue
            }
            moving = true
            if (GuideMotion.now() < sweep.start) continue
            val front = -reach / 2 + reach * GuideMotion.easeOut(t)
            canvas.save()
            canvas.rotate(SWEEP_ANGLE, cx, cy)
            canvas.drawRect(-reach, cy + front - sweep.thickness / 2, reach * 2, cy + front + sweep.thickness / 2, cream)
            canvas.restore()
        }
        if (moving) postInvalidateOnAnimation()
    }

    private fun drawMark(canvas: Canvas, reach: Float, cx: Float, cy: Float): Boolean {
        val t = GuideMotion.progress(markStart, GuideBackdrop.SWEEP_MILLIS)
        val out = if (markEnd < 0) 0f else GuideMotion.progress(markEnd, MARK_OUT_MILLIS)
        if (out >= 1f) {
            markStart = -1
            markEnd = -1
            return false
        }
        // The artwork fills its 1080 square with room around it. This makes the shape itself about half of the screen.
        val side = min(width, height) * MARK_SCALE * (1f + out * MARK_OUT_GROWTH)
        val scale = side / 1080f
        canvas.save()
        // The mark is uncovered by the line, so it clips to what the line has passed.
        canvas.rotate(SWEEP_ANGLE, cx, cy)
        canvas.clipRect(-reach, -reach, reach * 2, cy + (-reach / 2 + reach * GuideMotion.easeOut(t)))
        canvas.rotate(-SWEEP_ANGLE, cx, cy)
        canvas.translate(cx - side / 2, cy - side / 2)
        canvas.scale(scale, scale)
        markPaint.color = GuideMotion.faded(markColour, 1f - out)
        canvas.drawPath(mark, markPaint)
        markPaint.color = GuideMotion.faded(GuideColours.CREAM, 1f - out)
        canvas.drawPath(line, markPaint)
        canvas.restore()
        return t < 1f || markEnd >= 0
    }

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private companion object {
        const val THICKNESS = 5f
        /** The pass's wash: how long, when it peaks, and how strong at its peak. */
        const val WASH_MILLIS = 400L
        const val WASH_PEAK = .3f
        const val WASH_ALPHA = .85f
        /** The size of the mark's whole artwork, against the shorter side of the screen. */
        const val MARK_SCALE = 1.15f
        const val MARK_OUT_MILLIS = 340L
        const val MARK_OUT_GROWTH = .18f
        /** How long the two halves of the name take to fly together. */
        const val WORDMARK_MILLIS = 900L
        /** How far the halves start out, against the width of the screen. */
        const val WORDMARK_TRAVEL = .9f
        /** The halves are fully there after this part of the flight, divided into one. */
        const val WORDMARK_FADE_IN = 3f
        // From android/src/main/res/drawable/atlas_icon_foreground.xml.
        const val MARK = "M757.7,328.4 L760.8,328.4 L602.1,540 L760.8,751.6 L657.8,751.6 L523.4,572.2 L523.4,751.6 L438.8,751.6 L438.8,600.9 Z " +
            "M382.7,328.4 L632,328.4 L568.5,413 L523.4,413 L523.4,507.8 L657.8,328.4 L760.8,328.4 L607.4,532.9 L538.3,592 L523.4,572.2 L523.4,604.7 L438.8,677 L438.8,413 L319.2,413 Z"
        const val LINE = "M289.8,756.2 L303.6,767.2 L808.6,335.8 L794.8,324.7 Z"

        /** Reads the simple moves and lines of the logo's path data. */
        fun logoPath(data: String) = Path().apply {
            for (part in data.split(' ')) {
                if (part == "Z") {
                    close()
                    continue
                }
                val (x, y) = part.substring(1).split(',').map { it.toFloat() }
                if (part[0] == 'M') moveTo(x, y) else lineTo(x, y)
            }
        }
    }
}

/** A real text field that says when its selection changes. */
/**
 * One chapter in the bar at the top: a tick for each of its slides, side by side (owner, v5.2), so you see where you are inside the
 * chapter. A passed slide's tick is filled, the slide you're on is half lit, and the rest wait, faint.
 */
private class GuideSegment(context: Context) : View(context) {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    private var on = 0
    private var off = 0
    private var passed = BooleanArray(0)
    private var current = -1
    /** The tick that a pass fills now, and how far, from 0 to 1, with a small glow that comes and goes. */
    private var filling = -1
    private var fill = 1f
    private var glow = 0f
    private var stepping: ValueAnimator? = null
    private val line = context.resources.displayMetrics.density * LINE_DP
    private val reach = context.resources.displayMetrics.density * GLOW_DP
    private val gap = context.resources.displayMetrics.density * TICK_GAP_DP

    /** The chapter's [passed] slides, in order, with [current] the one on the screen, or -1 for another chapter's. */
    fun paint(on: Int, off: Int, passed: BooleanArray, current: Int) {
        this.on = on
        this.off = off
        this.passed = passed
        this.current = current
        invalidate()
    }

    /** Fills tick [tick], with a small glow that comes and goes: the slide just passed. */
    fun advance(tick: Int) {
        stepping?.cancel()
        if (!GuideMotion.enabled || tick !in passed.indices) {
            filling = -1
            invalidate()
            return
        }
        filling = tick
        stepping = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = STEP_MILLIS
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedFraction
                fill = t
                // Up quickly, then away.
                glow = if (t < .3f) t / .3f else 1f - (t - .3f) / .7f
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    filling = -1
                    fill = 1f
                    glow = 0f
                    invalidate()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val count = passed.size
        if (count == 0) return
        val top = (height - line) / 2
        val radius = line / 2
        val each = (width - gap * (count - 1)) / count
        for (tick in 0 until count) {
            val left = tick * (each + gap)
            val right = left + each
            pen.color = if (tick == current && !passed[tick]) GuideMotion.faded(on, CURRENT_ALPHA) else off
            canvas.drawRoundRect(left, top, right, top + line, radius, radius, pen)
            if (!passed[tick]) continue
            val filled = if (tick == filling) left + maxOf(line, each * fill) else right
            if (tick == filling && glow > 0f) {
                pen.color = GuideMotion.faded(on, GLOW_ALPHA * glow)
                val spread = reach * glow
                canvas.drawRoundRect(left - spread, top - spread, filled + spread, top + line + spread, radius + spread, radius + spread, pen)
            }
            pen.color = on
            canvas.drawRoundRect(left, top, filled, top + line, radius, radius, pen)
        }
    }

    private companion object {
        const val LINE_DP = 4f
        const val GLOW_DP = 4f
        const val GLOW_ALPHA = .35f
        const val STEP_MILLIS = 600L
        /** The room between two ticks of a chapter. Chapters are further apart. */
        const val TICK_GAP_DP = 2f
        /** How lit the slide you're on is, before it's passed. */
        const val CURRENT_ALPHA = .55f
    }
}

/** The orange check after a success line: it draws itself in, stroke by stroke, as [progress] goes from 0 to 1. */
private class CheckSpan(private val colour: Int) : android.text.style.ReplacementSpan() {
    var progress = 1f
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = android.graphics.Path()
    private val part = android.graphics.Path()

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) paint.getFontMetricsInt(fm)
        return Math.round(paint.textSize * WIDTH)
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val size = paint.textSize * SIZE
        val left = x + paint.textSize * GAP
        val base = y.toFloat()
        path.reset()
        path.moveTo(left, base - size * .45f)
        path.lineTo(left + size * .36f, base - size * .08f)
        path.lineTo(left + size, base - size * .86f)
        part.reset()
        val measure = android.graphics.PathMeasure(path, false)
        measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), part, true)
        pen.color = colour
        pen.strokeWidth = paint.textSize * STROKE
        canvas.drawPath(part, pen)
    }

    private companion object {
        const val WIDTH = .78f
        const val SIZE = .5f
        const val GAP = .18f
        const val STROKE = .1f
    }
}

/** Room before a letter, [extra] wide: the gap's box sits in it, so the letter and the rest of the line move over. */
private class GapRoom(private val extra: Float) : android.text.style.ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, metrics: Paint.FontMetricsInt?): Int {
        metrics?.let { paint.getFontMetricsInt(it) }
        return kotlin.math.ceil(extra + paint.measureText(text, start, end)).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        canvas.drawText(text, start, end, x + extra, y.toFloat(), paint)
    }
}

private class GuideField(context: Context) : EditText(context) {
    /** Android calls [onSelectionChanged] while it builds the view, so this has to work before it is set. */
    var changed: (() -> Unit)? = null

    /**
     * Scrolls by whole lines only, so the field always shows whole lines and never a sliced one at its top or a peek of the next at
     * its bottom. Android scrolls by pixels to bring the cursor into view; this rounds that to the nearest line that still shows the
     * cursor's line.
     */
    override fun scrollTo(x: Int, y: Int) {
        val own = layout
        val shown = maxLines
        if (own == null || shown < 1 || shown == Int.MAX_VALUE || own.lineCount <= shown) {
            super.scrollTo(x, y)
            return
        }
        val near = own.getLineForVertical(y)
        val rounded = if (y - own.getLineTop(near) > (own.getLineTop(near + 1) - own.getLineTop(near)) / 2) near + 1 else near
        val cursor = own.getLineForOffset(selectionEnd.coerceAtLeast(0))
        val top = rounded.coerceIn(cursor - shown + 1, cursor).coerceIn(0, own.lineCount - shown)
        super.scrollTo(x, own.getLineTop(top))
    }

    /**
     * Grey text that carries on right after what's typed, as one paragraph with it: the same font, size and lines, wrapping where
     * the typed text would. It isn't in the field's text, so the keyboard never sees it. See [GuideActivity.showGhost].
     */
    var ghost = ""
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    var ghostColour = 0
    private var ghostLayout: StaticLayout? = null
    private var ghostKey = ""

    /** The text that the slide marks in orange to aim at, by its characters, or null. See [GuideMemory.target]. */
    var target: IntRange? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** The target is only underlined, with no wash, as on the steer slide, where a wash would look like a selection (owner, v5). */
    var underlineOnly = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * Where a symbol slide's gap is, in the text, or -1: an orange box the width of [gapSymbol], where the symbol goes (v5). The text
     * after it moves over by the box's width, so the box never sits on the next letters (owner, v5.3: it covered "c" and "4").
     */
    var gap = -1
        set(value) {
            if (field == value) return
            field = value
            makeRoom()
            invalidate()
        }

    /** The symbol the gap waits for, which sets the box's width. */
    var gapSymbol = ""

    /** The room made after the gap, on the letter that follows it, if any. */
    private var room: GapRoom? = null

    private fun makeRoom() {
        val own = text ?: return
        room?.let(own::removeSpan)
        room = null
        val at = gap
        if (at < 0 || at >= own.length) return
        room = GapRoom(gapWidth()).also { own.setSpan(it, at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }

    /** The gap's box: its symbol's width and a little either side, never narrower than a letter. */
    private fun gapWidth() = max(paint.measureText(gapSymbol.ifEmpty { "M" }), paint.measureText("n")) + resources.displayMetrics.density * 6

    /** The gap's box, on the line where it is, right where the cursor waits. */
    private fun drawGap(canvas: Canvas, own: android.text.Layout) {
        val at = gap
        if (at < 0 || at > text.length) return
        val line = own.getLineForOffset(at)
        val x = own.getPrimaryHorizontal(at)
        val unit = resources.displayMetrics.density
        val wide = gapWidth() - unit * 6
        canvas.save()
        canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
        targetPaint.color = GuideMotion.faded(GuideColours.ORANGE, GuideActivity.TARGET_WASH)
        canvas.drawRoundRect(x + unit * 3, own.getLineTop(line) + unit * 2, x + unit * 3 + wide, own.getLineBottom(line) - unit * 2,
            unit * 3, unit * 3, targetPaint)
        targetPaint.color = GuideColours.ORANGE
        canvas.drawRect(x + unit * 3, own.getLineBottom(line) - unit * 2, x + unit * 3 + wide, own.getLineBottom(line).toFloat(), targetPaint)
        canvas.restore()
    }

    /** A wash of orange behind the target, and an orange line under it, on each of its lines. */
    private fun drawTarget(canvas: Canvas, own: android.text.Layout) {
        val aim = target ?: return
        val length = text.length
        if (aim.isEmpty() || aim.first < 0 || aim.last >= length) return
        val left = compoundPaddingLeft.toFloat()
        val top = extendedPaddingTop.toFloat()
        canvas.save()
        canvas.clipRect(left + scrollX, top + scrollY, (width - compoundPaddingRight + scrollX).toFloat(),
            (height - extendedPaddingBottom + scrollY).toFloat())
        canvas.translate(left, top)
        val underline = resources.displayMetrics.density * 2
        for (line in own.getLineForOffset(aim.first)..own.getLineForOffset(aim.last)) {
            val from = max(aim.first, own.getLineStart(line))
            val to = min(aim.last + 1, own.getLineEnd(line))
            if (to <= from) continue
            val x1 = own.getPrimaryHorizontal(from)
            val x2 = if (to >= own.getLineEnd(line)) own.getLineRight(line) else own.getPrimaryHorizontal(to)
            val lineTop = own.getLineTop(line).toFloat()
            val lineBottom = own.getLineBottom(line).toFloat()
            if (!underlineOnly) {
                targetPaint.color = GuideMotion.faded(GuideColours.ORANGE, GuideActivity.TARGET_WASH)
                canvas.drawRoundRect(x1 - underline, lineTop, x2 + underline, lineBottom, underline * 2, underline * 2, targetPaint)
            }
            targetPaint.color = GuideColours.ORANGE
            val thick = if (underlineOnly) underline * 1.5f else underline
            canvas.drawRect(x1, lineBottom - thick, x2, lineBottom, targetPaint)
        }
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        val own = layout
        // Only whole lines show: the tops of the line below the last one that fits never peek out at the bottom (owner, v5).
        canvas.save()
        if (own != null && maxLines in 1 until Int.MAX_VALUE) {
            val first = own.getLineForVertical(scrollY)
            if (first + maxLines < own.lineCount) {
                canvas.clipRect(scrollX.toFloat(), scrollY.toFloat(), (scrollX + width).toFloat(),
                    (extendedPaddingTop + own.getLineTop(first + maxLines)).toFloat())
            }
        }
        super.onDraw(canvas)
        canvas.restore()
        if (own == null) return
        drawTarget(canvas, own)
        drawGap(canvas, own)
        if (ghost.isEmpty()) return
        val typed = text.toString()
        val key = "$typed\u0000$ghost\u0000${own.width}\u0000$ghostColour"
        val ghostly = ghostLayout?.takeIf { ghostKey == key } ?: run {
            // What's typed, invisible, then the ghost: so the ghost starts exactly where the typed text ends, on its line.
            val built = SpannableStringBuilder(typed)
            if (typed.isNotEmpty()) built.setSpan(ForegroundColorSpan(Color.TRANSPARENT), 0, typed.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val start = built.length
            built.append(ghost)
            built.setSpan(ForegroundColorSpan(ghostColour), start, built.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            StaticLayout.Builder.obtain(built, 0, built.length, paint, own.width)
                .setAlignment(own.alignment)
                .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
                .setIncludePad(includeFontPadding)
                .setBreakStrategy(breakStrategy)
                .setHyphenationFrequency(hyphenationFrequency)
                .build()
                .also { ghostLayout = it; ghostKey = key }
        }
        // The same place the field draws its own text: inside its padding, scrolled with it, and only within its lines.
        val left = compoundPaddingLeft.toFloat()
        val top = extendedPaddingTop.toFloat()
        canvas.save()
        val first = ghostly.getLineForVertical(scrollY)
        val lastTop = if (maxLines in 1 until Int.MAX_VALUE) top + ghostly.getLineTop(minOf(first + maxLines, ghostly.lineCount)) else Float.MAX_VALUE
        canvas.clipRect(left + scrollX, top + scrollY, (width - compoundPaddingRight + scrollX).toFloat(),
            minOf((height - extendedPaddingBottom + scrollY).toFloat(), lastTop))
        canvas.translate(left, top)
        ghostly.draw(canvas)
        canvas.restore()
    }
    /** A finger lifted from the field: the guide's steering slides have words for that. */
    var touched: (() -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        changed?.invoke()
    }

    /**
     * Android's text view crashes on a touch while it has no layout for its text, which it can lack for a moment while the guide
     * changes the text or the screen under the finger: it places the cursor at -1. A touch then does nothing, and the next one works.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (layout == null) return true
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP) touched?.invoke()
        return try {
            super.onTouchEvent(event)
        } catch (error: IndexOutOfBoundsException) {
            true
        }
    }

    /** The same for a long press, which needs a selection handle that a text view without a layout has not made. */
    override fun performLongClick(): Boolean {
        if (layout == null) return true
        return try {
            super.performLongClick()
        } catch (error: NullPointerException) {
            true
        }
    }
}

/**
 * The interactive guide. It opens after setup and sells the keyboard by having you use it: each step waits for the real gesture
 * on the real keyboard, in a real text field. Everything that a step says and checks is in [GUIDE_STEPS].
 *
 * A step starts on ink. When you do it, the screen sweeps to orange, the phone gives a pulse and a short line lands. The step then
 * stays, so you can try the gesture more, until you push Next. Next also moves on from a step that you do not want to do.
 *
 * Practice text teaches the keyboard nothing: the field asks for no personalised learning, like the recorder's field.
 */
class GuideActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    /**
     * The app's front page: the same screen with no steps, a free place to type and swipe. The line above the field still
     * suggests words, and the drawing of the keys still follows your finger. See [playground].
     */
    private val playground by lazy { intent.getBooleanExtra(PLAYGROUND, false) }
    private val preferences by lazy { getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }
    private val flow by lazy { GuideFlow(::readSettings) }
    private val haptics by lazy { Haptics(this) }
    private val input by lazy { getSystemService(InputMethodManager::class.java) }
    private val clipboard by lazy { getSystemService(ClipboardManager::class.java) }
    /** A copy, from the keyboard's fn and C or from anything else, as the copy slides wait for. */
    private val clipChanged = ClipboardManager.OnPrimaryClipChangedListener {
        val text = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull().orEmpty()
        handle(KeyboardEvent.Copied(text))
    }

    private lateinit var root: FrameLayout
    private lateinit var backdrop: GuideBackdrop
    private lateinit var overlay: GuideOverlay
    /** The playground's show around its speed, behind the words. See [WpmEffectsView]. */
    private lateinit var effects: WpmEffectsView
    private lateinit var column: LinearLayout
    private lateinit var segments: LinearLayout
    private lateinit var next: TextView
    /** Once a slide passes: Next, big, full width, orange, above the keys, with a gentle pulse (owner, v5). */
    private lateinit var nextBig: TextView
    private var nextPulse: ValueAnimator? = null
    private lateinit var previous: TextView
    private lateinit var again: TextView
    /** The playground's ways out, to the written guide and to Settings. */
    private lateinit var guideAction: TextView
    private lateinit var settingsAction: TextView
    private lateinit var close: ImageView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var hint: TextView
    private lateinit var offer: TextView
    private lateinit var field: GuideField
    private lateinit var keys: GuideKeyboardView
    private lateinit var chapter: TextView
    /** The line above the field that suggests what to type. */
    private lateinit var script: TextView
    /** Over the field: what a demonstration would do, in grey, without typing it. */
    private lateinit var phantom: TextView
    /** The instruction's line, with the chip that slides over it for a real mistake, and the room that is left above the keys. */
    private lateinit var instruction: FrameLayout
    /** The clown hunt's bar under the instruction, draining for [HUNT_DRAIN_SECONDS]. */
    private lateinit var huntBar: View
    /** When the clown hunt began, or -1 with no hunt on. */
    private var huntStart = -1L
    /** What the clown hunt has said: 1 its chip, 2 its hint too. */
    private var huntSaid = 0
    /** Each second of the clown hunt: the count-up, and its chip and hint when their time comes. */
    private val huntTick = object : Runnable {
        override fun run() {
            val hunt = flow.step.hunt
            if (huntStart < 0 || passed || hunt == null) return
            val seconds = huntSeconds()
            if (seconds >= HUNT_DRAIN_SECONDS) {
                huntBar.visibility = View.GONE
                if (flow.stage == 1) body.text = huntLine(flow.instruction, seconds)
            }
            // Each once, even if a tick comes late: the mockery, a subtle hint, then the hint and leave to skip (owner, v5).
            if (seconds >= HUNT_DRAIN_SECONDS && huntSaid < 1) {
                huntSaid = 1
                hinted(hunt.mock)
            }
            if (seconds >= HUNT_HINT_SECONDS && huntSaid < 2) {
                huntSaid = 2
                hinted(hunt.hint)
            }
            if (seconds >= HUNT_SKIP_SECONDS && huntSaid < 3) {
                huntSaid = 3
                hinted(hunt.skip)
            }
            main.postDelayed(this, 1000L - (SystemClock.uptimeMillis() - huntStart) % 1000L)
        }
    }
    private lateinit var chip: TextView
    private lateinit var spacer: View
    private lateinit var lowerSpacer: View
    /** The most room at the bottom that the keyboard took while the slides show, so the keys never jump. See the insets listener. */
    private var steadyBottom = 0
    /** The end's two big buttons, in the middle, each with its line under it. */
    private lateinit var endButtons: LinearLayout
    /** The end's big button: go type something, in the playground (owner, v5: bigger, and first). */
    private lateinit var boringButton: TextView
    /** The finale: the summary, the acrostic and the logo. See [GuideFinale]. */
    private lateinit var finaleView: GuideFinaleView
    /** At the end, under the share switch: the guide from the start. */
    private lateinit var againLink: TextView
    private lateinit var shareButton: TextView
    private lateinit var shareLine: TextView
    /** A chapter's roast, over the whole screen, between chapters. */
    private lateinit var roastView: TextView

    private val keyboardChanges = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = checkKeyboard()
    }
    private var stopEvents: (() -> Unit)? = null
    private var stopTrace: (() -> Unit)? = null
    /** Between a finished step and the next, events have no step to go to. */
    private var busy = true
    private var ready = false
    private var keyboardChecked = false
    private var started = false
    private var seeding = false
    private var panelOpen = false
    /** The keyboard's window has a panel's room, which can come before it says a panel is open. */
    private var panelRoom = false
    private var lastKey: String? = null
    private var travel = 0f
    private var lastPoint: FloatArray? = null
    /** A finger is on the touchpad now, as the trace last said: no demo plays meanwhile. */
    private var fingerDown = false
    private var tone = GuideTone.Ink
    /** The step is done, so the screen is orange and Next is filled. */
    private var passed = false
    /** Between two steps, Next does nothing. */
    private var changing = false
    /** A scenario's words show in place of the slide's title and instruction, until the user is back on track. */
    private var scenarioShown = false
    /** The scenario that shows is the idle one, which any action takes away. */
    private var idleShown = false
    /** Words that the next slide shows in place of its own, as it opens: going back, or coming back. */
    private var arrivalTitle: String? = null
    private var arrivalBody: String? = null
    /** The next slide opens done, as another slide did it already. */
    private var arriveDone = false
    /** A slide was on the screen before this one, so a panel open now was left from it. */
    private var enteredBefore = false
    /** The chapter of the slide on the screen, so that a slide in the same chapter carries the field on. */
    private var enteredChapter: GuideChapter? = null
    /** How many times the delete chapter's field was emptied, and the steering line's length as it was put in. */
    private var deleteResets = 0
    /** The word that the steering slides mark, in the field's text: see [pickSteerAim]. It moves with the cursor's copy of the line. */
    private var steerAim: IntRange? = null
    /** The cursor is in the steer slide's word, as last heard, so entering it is felt once. */
    private var inSteerAim = false
    /** Words that went on the mass deletion slide, counted on after it passed, until the finger lifts and the count stops. */
    private var regrets = 0
    private var regretsFrozen = false
    private var steerLength = 0
    /** The guide is typing a symbol's sentence, letter by letter. */
    private var autoTyping = false
    /** The next hint is a line of its own, as the box to tick is, and not a chip. */
    private var keepHint = false
    /** The user ticked the box to go on without pasting. */
    private var pasteDeclined = false
    /** Alt is on, as the keyboard last said. */
    private var altOn = false
    /** The word menu slide says the bar is empty. */
    private var emptyBarShown = false
    /** On the word menu slide, the bar with no words to hold says so, and stops once it has some. See [GuideHooks.barCount]. */
    private val checkBar = Runnable {
        if (passed || busy || flow.step.bar != GuideBar.Menu || flow.stage != 0) return@Runnable
        val count = GuideHooks.barCount?.invoke() ?: -1
        // Never a made-up word to hold: with nothing on the bar, the instruction says to fill it, until it has words.
        if (count == 0 && !emptyBarShown) {
            body.text = EMPTY_BAR_BODY
            emptyBarShown = true
        } else if (count > 0 && emptyBarShown) {
            emptyBarShown = false
            body.text = flow.instruction
        }
    }
    private val idleOnce = Runnable { idle(IDLE_TITLE, -1) }
    /** The idle chip has shown on this slide, and the lock's chip too: each shows once a slide. */
    private var idleChipShown = false
    /** The lock's chips shown on this slide, so each shows once. */
    private val lockChips = HashSet<String>()
    /** The chip on the screen, since when, and the one that waits for it to end. */
    private var chipAt = 0L
    private var pendingChip: String? = null
    private val chipOver = Runnable { hideChip() }
    /** Which look the screen has: the guide's slides, one line each, or the playground's and setup's, which wrap. */
    private var guideLooks: Boolean? = null
    private val idleTwice = Runnable { idle(IDLE_AGAIN_TITLE, -1) }
    /** The line under the title once the slide is done: its own, or a scenario's that counted. */
    private var settledPraise = ""
    /** After the line that lands, the step's words come back, so you can read them while you try more. */
    private val settleReaction = Runnable {
        if (!passed) return@Runnable
        swapIn(title, flow.step.title, 0)
        // The line under the title reacts to what you just did, and never gives the next instruction.
        if (settledPraise.isNotEmpty()) body.text = settledPraise
        body.animate().alpha(1f).setDuration(FADE_MILLIS).start()
    }

    // Slide zero: setup. While a part is missing, it comes before the story. The guide opens the page for a part, watches for its
    // switch, closes the page and comes back by itself, then opens the next part after a short beat. See [SetupPart].

    /** The part of setup on the screen, or null when the guide is not setting up. */
    private var setupPart: SetupPart? = null
    /** The part whose Settings page is open now, which the guide watches. */
    private var openedPart: SetupPart? = null
    /** A part that opens by itself after a short beat, because the part before it was just done. */
    private var autoPart: SetupPart? = null
    /** Between a part that is done and the next one, nothing else moves setup on. */
    private var setupChanging = false
    private var visible = false
    private val settingUp get() = setupPart != null

    private val watchSetup = object : Runnable {
        override fun run() {
            val part = openedPart ?: return
            if (!PhoneSetup.done(this@GuideActivity, part)) {
                main.postDelayed(this, SETUP_POLL_MILLIS)
                return
            }
            openedPart = null
            // The page was opened from here, so closing it brings the guide back. A page in a task of its own needs the guide sent for.
            finishActivity(SETUP_REQUEST)
            main.postDelayed({ if (!visible) bringBack() }, SETUP_RETURN_MILLIS)
            checkKeyboard()
        }
    }

    private val openAuto = Runnable {
        val part = autoPart ?: return@Runnable
        autoPart = null
        if (visible && setupPart == part && openedPart == null) openPart(part)
    }

    private var helpShown = false
    private val showHelp = Runnable { helpDue() }

    /** Keys down now, as the keyboard reported them: help waits while one is held. */
    private val heldKeys = HashSet<Int>()

    /** Restarts the help's wait, on any input: help comes after a pause, never in the middle of trying (owner, v5.3). */
    private fun waitForHelp() {
        if (helpShown || passed || flow.step.help == null) return
        main.removeCallbacks(showHelp)
        main.postDelayed(showHelp, if (flow.step.helpTitle != null) HELP_TITLE_AFTER_MILLIS else HELP_AFTER_MILLIS)
    }

    private fun helpDue() {
        // Never while a finger is down, a key is held or the word menu is open: "Hold, don't tap" showed with the menu open, and
        // the steer help mid-steer. It waits, and asks again.
        if (fingerDown || heldKeys.isNotEmpty() || GuideHooks.menuOpen?.invoke() == true) {
            main.postDelayed(showHelp, HELP_RECHECK_MILLIS)
            return
        }
        helpShown = true
        val step = flow.step
        val help = step.help
        if (help != null && !passed) {
            if (step.helpTitle != null) showScenario(step.helpTitle, help) else {
                haptics.play(Haptic.GuideHint)
                showHint(help)
            }
        }
        offerFor(step)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuideFonts.load(this)
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        window.setBackgroundDrawable(ColorDrawable(GuideColours.BLACK))
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContentView(build())
        if (playground) {
            playgrounds += this
            playgroundStarting = -PLAYGROUND_QUIET_MILLIS
        } else guidesOpen++
        // During setup another keyboard may still be the phone's keyboard, and it would cover the slide.
        if (PhoneSetup.missing(this).isEmpty()) field.requestFocus() else keyboardShown(false)
        field.changed = ::fieldChanged
        field.touched = ::fieldTouched
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = fieldChanged()
        })
        flow.start(preferences.getString(RESUME, null))
        applyTone(GuideTone.Ink)
        backdrop.show(backgroundOf(tone))
        for (view in listOf(title, body, hint, offer, next, previous, chapter, again, close, segments, script, field, keys)) view.alpha = 0f
    }

    override fun onResume() {
        super.onResume()
        visible = true
        // Back from a Settings page with its part still not done: say what to do there, and wait for the button.
        openedPart?.let { part ->
            openedPart = null
            main.removeCallbacks(watchSetup)
            if (!PhoneSetup.done(this, part)) part.help?.let(::showHint)
        }
        if (autoPart != null) {
            main.removeCallbacks(openAuto)
            main.postDelayed(openAuto, SETUP_AUTO_MILLIS)
        }
        if (stopEvents == null) stopEvents = KeyboardEvents.listen { event -> runOnUiThread { handle(event) } }
        if (stopTrace == null) stopTrace = Trace.listen { message -> trace(message) }
        // A word that comes out different gets the script's word on the strip, so the line above is always one touch away.
        // The playground is free typing, so there the strip keeps to your own words.
        // Only where there's text to type along to: elsewhere the bar keeps to your own words.
        if (!playground) GuideHooks.script = { before ->
            // Past the end of the slide's line there's nothing more to type along to: room to play, with no word forced.
            val next = passage.track(typedSince(before)).next
            if (!hasText() || next >= flow.step.text.split(' ').count { it.isNotEmpty() }) "" else GuideScript.letters(passage.wordAt(next))
        }
        contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, keyboardChanges)
        clipboard.addPrimaryClipChangedListener(clipChanged)
        // Back from the disclosure: the end screen shows the answer, or keeps asking.
        if (ready && flow.finished && !playground) showEndChoices()
        // Back on a slide: its lock and its bar again.
        if (ready && started && !playground && !settingUp && !flow.finished && guideLooks == true) {
            GuideHooks.lessons = flow.step.lessons
            GuideHooks.barSides(flow.step.sides)
        }
    }

    /** The keyboard as it always is: nothing locked, and the bar with all its words. */
    private fun unlockKeyboard() {
        GuideHooks.lessons = null
        GuideHooks.barSides(true)
    }

    override fun onPause() {
        super.onPause()
        visible = false
        // The lock and the bar's look are the guide's own: anywhere else the keyboard is as it always is.
        unlockKeyboard()
        stopEvents?.invoke()
        stopEvents = null
        stopTrace?.invoke()
        stopTrace = null
        GuideHooks.script = null
        contentResolver.unregisterContentObserver(keyboardChanges)
        clipboard.removePrimaryClipChangedListener(clipChanged)
        endDemo()
        // A hidden playground keeps no clock going; its speed starts over when you type again.
        main.removeCallbacks(wpmTick)
        if (playground) calmWpm()
    }

    override fun onDestroy() {
        if (playground) playgrounds -= this
        if (playground) Log.d(TAG, "playground closed: finishing $isFinishing, told ${playgroundExit != null}")
        // Closed by something else, such as a link that opens the app's own screen over it: the front page opens it again.
        if (playground && isFinishing) {
            playgroundExit?.invoke(PLAYGROUND_CLOSED)
            playgroundExit = null
        }
        // The front page asked for the playground while this guide was open, so it opens now that the guide has closed.
        if (!playground) {
            guidesOpen--
            if (guidesOpen == 0 && isFinishing) waitingPlayground?.let { open ->
                waitingPlayground = null
                // Not [main], whose messages this guide drops as it closes.
                Handler(Looper.getMainLooper()).post { runCatching(open) }
            }
        }
        GuideHooks.nearMiss = false
        unlockKeyboard()
        GuideHooks.stripDemo(null)
        GuideHooks.stripPoint(null)
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        // The keyboard list can close without a change, so this checks each time the guide comes back.
        checkKeyboard()
        // During setup there is nothing to type yet, and another keyboard would cover the slide.
        if (settingUp) return
        field.requestFocus()
        input.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
    }

    @Deprecated("Back reaches an activity here, because the app does not opt in to predictive back")
    override fun onBackPressed() {
        // Back goes to the step before, like the Back button. From the first step or the ending it leaves the guide.
        // With the leaving pop-up open, Back closes it: you stay.
        if (leavePopup != null) {
            closeLeavePopup()
            return
        }
        if (changing) return
        // The playground is the front page, so Back leaves the app, as it does from any app's front page.
        if (playground && !settingUp) {
            playgroundExit = null
            finishAffinity()
        } else if (settingUp) leave()
        else {
            if (ready && !flow.finished) preferences.edit().putBoolean(LEFT_EARLY, true).apply()
            leave()
        }
    }

    // The screen.

    private fun build(): View {
        root = FrameLayout(this)
        backdrop = GuideBackdrop(this)
        overlay = GuideOverlay(this)
        effects = WpmEffectsView(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        keys = GuideKeyboardView(this)
        title = TextView(this).apply {
            typeface = HEAVY
            gravity = Gravity.BOTTOM or Gravity.START
            includeFontPadding = false
            letterSpacing = -.02f
            // The owner's titles and scenarios run long: three lines, smaller if they need it, rather than cut words.
            maxLines = 3
            setAutoSizeTextTypeUniformWithConfiguration(TITLE_SMALLEST, TITLE_LARGEST, 2, TypedValue.COMPLEX_UNIT_DIP)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body = TextView(this).apply {
            typeface = MEDIUM
            gravity = Gravity.TOP or Gravity.START
            includeFontPadding = false
            maxLines = 5
            setAutoSizeTextTypeUniformWithConfiguration(BODY_SMALLEST, BODY_LARGEST, 1, TypedValue.COMPLEX_UNIT_DIP)
        }
        hint = TextView(this).apply {
            typeface = MEDIUM
            includeFontPadding = false
            maxLines = 3
            visibility = View.GONE
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(14f), dp(18).toFloat()))
        }
        offer = TextView(this).apply {
            typeface = HEAVY
            gravity = Gravity.CENTER
            maxLines = 2
            visibility = View.GONE
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(15f), dp(20).toFloat()))
            setOnClickListener { offerPressed() }
        }
        // A small pill in the top bar: Skip until the slide is passed, then a filled Next.
        next = TextView(this).apply {
            text = "Skip"
            typeface = HEAVY
            gravity = Gravity.CENTER
            maxLines = 1
            // A finger-sized target, though the pill looks small (owner, v5.3).
            minHeight = dp(TOUCH)
            setPadding(dp(16), dp(4), dp(16), dp(4))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(14f), dp(18).toFloat()))
            setOnClickListener { nextPressed() }
        }
        chip = TextView(this).apply {
            typeface = MEDIUM
            maxLines = 1
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            visibility = View.GONE
            setPadding(dp(CHIP_SIDE), dp(CHIP_TOP), dp(CHIP_SIDE), dp(CHIP_TOP))
            setTextColor(GuideColours.CREAM)
            background = chipBackground()
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        previous = TextView(this).apply {
            text = "Back"
            typeface = HEAVY
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(15f), dp(20).toFloat()))
            setOnClickListener { backPressed() }
        }
        field = GuideField(this).apply {
            typeface = MEDIUM
            setTextColor(GuideColours.INK)
            setHintTextColor(GuideMotion.faded(GuideColours.INK, .45f))
            // A selection is cream, so it never reads as the orange words a slide marks to aim at (owner, v5.3).
            highlightColor = GuideMotion.faded(GuideColours.CREAM, SELECTION_ALPHA)
            // No Cut, Copy or Paste toolbar: it covered the instructions, stayed into the next slide, and its Cut and Paste went
            // round the slides' locks (owner, v5.3). Copy and paste are fn and C or V, as the guide teaches.
            val noToolbar = object : android.view.ActionMode.Callback {
                override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = false
                override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = false
                override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem) = false
                override fun onDestroyActionMode(mode: android.view.ActionMode) = Unit
            }
            customSelectionActionModeCallback = noToolbar
            customInsertionActionModeCallback = noToolbar
            setTextCursorDrawable(GradientDrawable().apply {
                setColor(GuideColours.ORANGE)
                setSize(dp(3), dp(20))
            })
            background = GradientDrawable().apply {
                setColor(GuideColours.CREAM)
                cornerRadius = Radii.px(resources, FIELD_RADIUS)
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(FIELD_SP), dp(FIELD_DP).toFloat()))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            minLines = 1
            maxLines = 2
            hint = FIELD_HINT
            // Lets the keyboard know this field, for the one step that asks it for a near miss. See [GuideHooks].
            privateImeOptions = GuideHooks.FIELD
            isVerticalScrollBarEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        // The same text settings as the field, so that the grey words line up with the real ones.
        phantom = TextView(this).apply {
            breakStrategy = field.breakStrategy
            hyphenationFrequency = field.hyphenationFrequency
            typeface = field.typeface
            setTextSize(TypedValue.COMPLEX_UNIT_PX, field.textSize)
            setPadding(field.paddingLeft, field.paddingTop, field.paddingRight, field.paddingBottom)
            gravity = field.gravity
            includeFontPadding = field.includeFontPadding
            setLineSpacing(field.lineSpacingExtra, field.lineSpacingMultiplier)
            letterSpacing = field.letterSpacing
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        script = TextView(this).apply {
            typeface = MEDIUM
            includeFontPadding = false
            // Exactly two lines, always: the passage moves a whole line at a time. See [showScript]. It has its own box, so it
            // reads as the text to type and not as more instructions.
            setLines(2)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(GuideMotion.faded(GuideColours.CREAM, PASSAGE_BOX))
                cornerRadius = dp(10).toFloat()
            }
            isVerticalScrollBarEnabled = false
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(16f), dp(22).toFloat()))
            contentDescription = "Text to type"
        }
        chapter = TextView(this).apply {
            typeface = HEAVY
            includeFontPadding = false
            isAllCaps = true
            letterSpacing = .12f
            maxLines = 1
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(11f), dp(15).toFloat()))
        }
        segments = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            // Once every chapter is finished, a touch on the bar jumps to that chapter.
            setOnTouchListener { view, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP && childCount > 0 && view.width > 0) {
                    chapterTouched((event.x / view.width * childCount).toInt().coerceIn(0, childCount - 1))
                }
                true
            }
        }
        // Each segment's view is taller than its line, so the glow as it steps forward has room. Each is as wide as its slides.
        for ((index, chapter) in flow.barChapters.withIndex()) segments.addView(GuideSegment(this),
            LinearLayout.LayoutParams(0, dp(SEGMENT_HEIGHT + 2 * SEGMENT_GLOW), flow.idsOf(chapter).size.coerceAtLeast(1).toFloat()).apply {
                if (index < flow.barChapters.lastIndex) marginEnd = dp(CHAPTER_GAP)
            })
        again = topAction("Start again") { startAgain() }.apply { visibility = View.GONE }
        guideAction = topAction("Guide") {
            if (playground) leavePlayground(PLAYGROUND_GUIDE) else {
                startActivity(Connected.pageIntent(this, BORING_GUIDE))
                finish()
            }
        }.apply { visibility = View.GONE }
        settingsAction = topAction("Settings") { leavePlayground(PLAYGROUND_SETTINGS) }.apply { visibility = View.GONE }
        // ✕ on the right, big enough to hit at once (owner, v5.2).
        close = ImageView(this).apply {
            setImageResource(R.drawable.panel_close)
            contentDescription = "Close"
            setPadding(dp(9), dp(9), dp(9), dp(9))
            setOnClickListener { confirmLeave() }
        }
        boringButton = bigButton(END_NEXT) {
            haptics.play(Haptic.GuideNext)
            preferences.edit().remove(RESUME).apply()
            startActivity(Connected.pageIntent(this, "/"))
            finish()
        }.apply { setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(24f), dp(32).toFloat())) }
        // A real button, outlined, not fine print (owner, v5.2).
        againLink = bigButton(AGAIN_LINK) { startAgain() }.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(17f), dp(22).toFloat()))
            setPadding(dp(16), dp(11), dp(16), dp(11))
        }
        finaleView = GuideFinaleView(this).apply {
            visibility = View.GONE
            beat = { haptics.celebrate() }
            over = { showEndChoices() }
        }
        // A switch: on opens the share policy, to agree there; off is at once.
        shareButton = bigButton(SHARE_OFFER) {
            if (Contributions.enabled(this)) {
                Contributions.enabled(this, false)
                showEndChoices()
            } else startActivity(Connected.pageIntent(this, Contributions.PAGE))
        }
        shareLine = underLine()
        endButtons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(boringButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(shareButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            })
            addView(shareLine, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            addView(againLink, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            })
        }
        roastView = TextView(this).apply {
            typeface = HEAVY
            gravity = Gravity.CENTER
            includeFontPadding = false
            letterSpacing = -.02f
            visibility = View.GONE
            setPadding(dp(GUTTER * 2), 0, dp(GUTTER * 2), 0)
            setAutoSizeTextTypeUniformWithConfiguration(TITLE_SMALLEST, ROAST_LARGEST, 2, TypedValue.COMPLEX_UNIT_DIP)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setOnClickListener { endRoast() }
        }
        // On top, the chapters across the whole width, a tick for each slide (owner, v5.2); under them, the chapter's name on the
        // left, and Skip or Next and ✕ on the right. Back is the phone's Back.
        val actionsRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(TOP_ACTION)
            addView(chapter, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(again, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(guideAction, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(settingsAction, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
            addView(next, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(10)
            })
            addView(close, LinearLayout.LayoutParams(dp(CLOSE_SIZE), dp(CLOSE_SIZE)).apply { marginStart = dp(4) })
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(segments, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(SEGMENT_HEIGHT + 2 * SEGMENT_GLOW)))
            addView(actionsRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // The offer to turn on a setting, if a step needs one, sits above the keys.
        // Next once a slide is passed: a normal button, not a huge one (owner, v5.2), on the right where a thumb reaches it.
        nextBig = bigButton("Next") { nextPressed() }.apply {
            visibility = View.GONE
            setTextColor(GuideColours.INK)
            background = rounded(GuideColours.ORANGE, OFFER_RADIUS)
            setPadding(dp(NEXT_PAD_SIDE), dp(NEXT_PAD_TOP), dp(NEXT_PAD_SIDE), dp(NEXT_PAD_TOP))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(NEXT_SP), dp(NEXT_DP).toFloat()))
        }
        val actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            addView(offer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(nextBig, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(12)
            })
        }
        instruction = FrameLayout(this).apply {
            addView(body, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(chip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL))
            huntBar = View(context).apply {
                background = rounded(GuideColours.ORANGE, Radii.SMALL)
                pivotX = 0f
                visibility = View.GONE
            }
            addView(huntBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.BOTTOM))
        }
        spacer = View(this)
        lowerSpacer = View(this)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, TITLE_SHARE).apply { topMargin = dp(6) })
            addView(instruction, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, BODY_SHARE).apply { topMargin = dp(4) })
            addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            addView(script, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(FrameLayout(context).apply {
                addView(field, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(phantom, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            // The room left between the field and the keys splits evenly above and below Next's place, so the field, Next and
            // the keys are one column with even gaps (owner, v5).
            addView(spacer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
            addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(lowerSpacer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
            addView(keys, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { topMargin = dp(8) })
            addView(endButtons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }
        root.addView(backdrop, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(effects, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(finaleView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(roastView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            // The content nearest the screen's rounded corners, such as Back and Next at the bottom and the chapter at the top,
            // keeps clear of them. The camera hole is in the status bar, which bars.top already leaves out.
            val top = bars.top + dp(4)
            // On the slides the keys stay where they are: the keyboard's bar hides for a moment as a slide changes, and the room it
            // gives back would drop the keys by its height (owner, 2026-11-11). They keep the most room the keyboard took.
            // Only the keyboard's own height counts: an open panel is much taller, and keeping its room after it closed squashed the
            // keys and left a gap for the rest of the guide (owner, v5: slide 12). The panel's room counts only while it's open.
            val panelSized = steadyBottom > 0 && bars.bottom > steadyBottom + dp(PANEL_EXTRA)
            // A panel's room arrives before the keyboard says the panel is open: the drawing hides at once, so its rows never peek
            // above the panel or get cut by it (owner, v5.3).
            panelRoom = panelSized
            if (panelSized && ::keys.isInitialized) keys.visibility = View.GONE
            if (guideLooks != true) steadyBottom = 0
            else if (!panelOpen && !panelSized) steadyBottom = maxOf(steadyBottom, bars.bottom)
            val bottom = (if (panelOpen) maxOf(bars.bottom, steadyBottom) else maxOf(steadyBottom, if (panelSized) 0 else bars.bottom)) + dp(8)
            val corners = Corners.of(insets, display)
            val corner = maxOf(corners.inset(bottom.toFloat()), corners.topInset(top.toFloat()))
            column.setPadding(maxOf(dp(GUTTER) + bars.left, corner), top, maxOf(dp(GUTTER) + bars.right, corner), bottom)
            fitKeys()
            insets
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitKeys() }
        effects.shaking = { tip, dx, dy ->
            title.rotation = tip
            column.translationX = dx
            column.translationY = dy
        }
        return root
    }

    /**
     * The guide's slides have fixed heights and nothing reflows: one line for the title at one size, one line for the instruction,
     * the field exactly two lines, and the room above the keys left free. Setup, the end and the playground keep their wrapping look.
     */
    private fun guideLook(guide: Boolean) {
        if (guideLooks == guide) return
        guideLooks = guide
        if (guide) {
            val titlePx = min(sp(TITLE_SP), dp(TITLE_DP).toFloat())
            val bodyPx = min(sp(BODY_SP), dp(BODY_DP).toFloat())
            // The room goes to the words, not the keys (owner, v5.2): the keys take only their real size, and the title and the
            // instruction are bigger, one line each, the same on every slide; a long one shrinks to fit rather than lose words.
            title.isSingleLine = false
            title.maxLines = TITLE_LINES
            title.ellipsize = null
            title.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            title.setAutoSizeTextTypeUniformWithConfiguration(sizeIn(TITLE_SMALLEST_SP, titlePx), titlePx.toInt(), 1, TypedValue.COMPLEX_UNIT_PX)
            title.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TITLE_LINES * lineHeight(HEAVY, titlePx)).apply {
                topMargin = dp(GAP)
            }
            body.isSingleLine = false
            body.maxLines = BODY_LINES
            body.ellipsize = null
            body.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            body.setAutoSizeTextTypeUniformWithConfiguration(sizeIn(BODY_SMALLEST_SP, bodyPx), bodyPx.toInt(), 1, TypedValue.COMPLEX_UNIT_PX)
            chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, bodyPx * CHIP_SCALE)
            instruction.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                BODY_LINES * lineHeight(MEDIUM, bodyPx) + 2 * dp(CHIP_PAD)).apply { topMargin = dp(GAP) }
            spacer.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            lowerSpacer.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            // The field on ink, as the approved frame has it: what's typed in cream, exactly two lines.
            field.setTextColor(GuideColours.CREAM)
            field.setHintTextColor(GuideMotion.faded(GuideColours.CREAM, .45f))
            field.background = rounded(fieldFill(), FIELD_RADIUS)
            field.hint = null
            field.ghostColour = ghostGrey()
            // Three lines on every slide (v5): the slide's line and room to play, a writing area, not a strip.
            for (view in listOf<TextView>(field, phantom)) {
                view.minLines = FIELD_LINES
                view.maxLines = FIELD_LINES
                view.gravity = Gravity.TOP or Gravity.START
            }
        } else {
            title.isSingleLine = false
            title.maxLines = 3
            title.ellipsize = null
            title.gravity = Gravity.BOTTOM or Gravity.START
            title.setAutoSizeTextTypeUniformWithConfiguration(TITLE_SMALLEST, TITLE_LARGEST, 2, TypedValue.COMPLEX_UNIT_DIP)
            title.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, TITLE_SHARE).apply { topMargin = dp(6) }
            body.isSingleLine = false
            body.maxLines = 5
            body.ellipsize = null
            body.gravity = Gravity.TOP or Gravity.START
            body.setAutoSizeTextTypeUniformWithConfiguration(BODY_SMALLEST, BODY_LARGEST, 1, TypedValue.COMPLEX_UNIT_DIP)
            instruction.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, BODY_SHARE).apply { topMargin = dp(4) }
            spacer.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
            lowerSpacer.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
            field.setTextColor(GuideColours.INK)
            field.setHintTextColor(GuideMotion.faded(GuideColours.INK, .45f))
            field.background = rounded(GuideColours.CREAM, FIELD_RADIUS)
            field.hint = FIELD_HINT
            field.ghost = ""
            for (view in listOf<TextView>(field, phantom)) {
                view.minLines = 1
                view.maxLines = 2
                view.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            }
        }
    }

    /** When the word menu was last seen open, or -1 while it's closed. See [menuSettled]. */
    private var menuOpenSince = -1L
    private val menuHint = Runnable { if (!passed && !playground) showScript() }

    /** The word menu has been open for [MENU_SETTLE_MILLIS], so its buttons are in place under the hint. */
    private fun menuSettled(): Boolean {
        if (GuideHooks.menuOpen?.invoke() != true) {
            menuOpenSince = -1L
            return false
        }
        val now = GuideMotion.now()
        if (menuOpenSince < 0) menuOpenSince = now
        return now - menuOpenSince >= MENU_SETTLE_MILLIS
    }

    /** The end of the guide is on the screen: no drawn keys there, only the finale and its buttons (owner, v5). */
    private fun atEnd() = !playground && ::finaleView.isInitialized && finaleView.visibility == View.VISIBLE

    /** The field's own colour on the slides, for a word in a demo to fade into. */
    private fun fieldFill() = if (guideLooks == true) GuideMotion.blend(GuideColours.BLACK, GuideColours.CREAM, FIELD_FILL) else GuideColours.CREAM

    /** The grey of what isn't typed yet: the ghost ahead of the cursor, and a demo's words. */
    private fun ghostGrey() = if (guideLooks == true) GuideMotion.faded(GuideColours.CREAM, GHOST_ALPHA) else GuideMotion.faded(GuideColours.INK, PHANTOM_ALPHA)

    /** [sp] in pixels, never more than [most]: the smallest size a title or instruction shrinks to. */
    private fun sizeIn(sp: Float, most: Float) = min(sp(sp), most).toInt().coerceAtLeast(1)

    /** The height of one line of [face] at [px], from its font's top to its bottom, so nothing is cut. */
    private fun lineHeight(face: Typeface, px: Float): Int {
        val metrics = Paint().apply { typeface = face; textSize = px }.fontMetrics
        return kotlin.math.ceil(metrics.bottom - metrics.top).toInt() + dp(2)
    }

    /** One of the end's big buttons. */
    private fun bigButton(text: String, press: () -> Unit) = TextView(this).apply {
        this.text = text
        typeface = HEAVY
        gravity = Gravity.CENTER
        maxLines = 1
        setPadding(dp(16), dp(14), dp(16), dp(14))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(20f), dp(26).toFloat()))
        setOnClickListener { press() }
    }

    /** The line under one of the end's big buttons. */
    private fun underLine() = TextView(this).apply {
        typeface = MEDIUM
        gravity = Gravity.CENTER
        maxLines = 4
        setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(15f), dp(19).toFloat()))
    }

    /** A button in the header: finger-sized, a pill (owner, v5.3: the front page's were tiny). */
    private fun topAction(text: String, press: () -> Unit) = TextView(this).apply {
        this.text = text
        typeface = MEDIUM
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        minHeight = dp(TOUCH)
        setPadding(dp(16), 0, dp(16), 0)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(15f), dp(19).toFloat()))
        setOnClickListener { press() }
    }

    /** The guide's one chip, for every chip and hint: a pill of the divider colour with cream words. */
    private fun chipBackground() = rounded(GuideColours.DIVIDER, Radii.LARGE)

    /**
     * The drawing of the keys is exactly the real keyboard's size: the whole screen's width, past the column's sides, and the
     * height that the keys' own proportions give it (owner, v5.2). The rest of the room goes to the words. With the screen too
     * short, as with a panel open, the keys go away: the panel shows the same keys, right above your finger.
     */
    private fun fitKeys() {
        val free = root.height - column.paddingTop - column.paddingBottom
        if (root.width == 0 || free <= 0) return
        val compact = free < dp(COMPACT_HEIGHT)
        // The rest of the column has to fit above the keys, or the keys are cut at the bottom (owner, v5.3: the bottom row went
        // under the bar). Too short, and the field gives a line; shorter still, and the keys drop their bottom row.
        val above = (0 until column.childCount).map(column::getChildAt).filter { it !== keys && it !== spacer && it !== lowerSpacer && it.visibility != View.GONE }
            .sumOf { child -> child.height + ((child.layoutParams as? LinearLayout.LayoutParams)?.let { it.topMargin + it.bottomMargin } ?: 0) }
        keys.lettersOnly = false
        if (guideLooks == true && above > 0 && above + keys.heightFor(root.width) + dp(8) > free) {
            if (field.maxLines > FIELD_LINES - 1) for (view in listOf<TextView>(field, phantom)) {
                view.minLines = FIELD_LINES - 1
                view.maxLines = FIELD_LINES - 1
            }
            val saved = field.lineHeight
            keys.lettersOnly = above - saved + keys.heightFor(root.width) + dp(8) > free
        }
        val target = if (compact) 0 else keys.heightFor(root.width)
        val params = keys.layoutParams as LinearLayout.LayoutParams
        // Shown or not follows every change of room, a panel's coming or going too, even when the size stays.
        keys.visibility = if (target == 0 || atEnd() || panelOpen || panelRoom) View.GONE else View.VISIBLE
        if (params.height == target && params.leftMargin == -column.paddingLeft && params.rightMargin == -column.paddingRight) return
        params.height = target
        params.leftMargin = -column.paddingLeft
        params.rightMargin = -column.paddingRight
        root.post { keys.requestLayout() }
    }

    private fun backgroundOf(tone: GuideTone) = if (tone == GuideTone.Orange) GuideColours.ORANGE else GuideColours.BLACK

    /** The words take the colours of the background: cream on ink while you try, ink on orange once it is done. */
    private fun applyTone(next: GuideTone) {
        tone = next
        val onInk = next == GuideTone.Ink
        val strong = if (onInk) GuideColours.CREAM else GuideColours.INK
        // On black, the titles and the chapter's name are the orange accent; on the pass's orange, they're ink (v5.3).
        val accent = if (onInk) GuideColours.ORANGE else GuideColours.INK
        title.setTextColor(accent)
        body.setTextColor(GuideMotion.faded(strong, BODY_ALPHA))
        // A hint says what happened, calmly: it is not a mistake that counts.
        // The guide's one chip style (owner, v5.3): the chip and the hint alike.
        hint.setTextColor(GuideColours.CREAM)
        hint.background = chipBackground()
        offer.setTextColor(if (onInk) GuideColours.INK else GuideColours.CREAM)
        offer.background = rounded(if (onInk) GuideColours.ORANGE else GuideColours.BLACK, OFFER_RADIUS)
        again.setTextColor(strong)
        paintEndButtons()
        for (index in 0 until endButtons.childCount) (endButtons.getChildAt(index) as TextView).let { line ->
            if (line !== boringButton && line !== shareButton && line !== againLink) line.setTextColor(GuideMotion.faded(strong, BODY_ALPHA))
        }
        roastView.setTextColor(strong)
        // The front page's two buttons: Guide filled in orange, the one to try first; Settings outlined (owner, v5.3).
        guideAction.setTextColor(GuideColours.INK)
        guideAction.background = rounded(GuideColours.ORANGE, Radii.LARGE)
        settingsAction.setTextColor(GuideColours.ORANGE)
        settingsAction.background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1).coerceAtLeast(2), GuideColours.ORANGE)
            cornerRadius = Radii.px(resources, Radii.LARGE)
        }
        close.imageTintList = ColorStateList.valueOf(strong)
        chapter.setTextColor(if (onInk) accent else GuideMotion.faded(strong, CHAPTER_ALPHA))
        keys.tone = next
        paintNext()
        paintSegments(flow.stepIndex)
        val appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (onInk) 0 else appearance, appearance)
        if (::field.isInitialized && ::script.isInitialized) showScript()
    }

    /** Next is an outline while the step waits, and filled once the step is done. Back is always a quiet outline. */
    private fun paintNext() {
        val strong = if (tone == GuideTone.Ink) GuideColours.CREAM else GuideColours.INK
        previous.setTextColor(GuideMotion.faded(strong, NEXT_WAITING))
        previous.background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1).coerceAtLeast(2), GuideMotion.faded(strong, BACK_OUTLINE))
            cornerRadius = Radii.px(resources, OFFER_RADIUS)
        }
        // Skip is an outline; once the slide is passed it's a filled orange Next.
        if (passed) {
            next.setTextColor(GuideColours.INK)
            next.background = rounded(if (tone == GuideTone.Ink) GuideColours.ORANGE else GuideColours.BLACK, OFFER_RADIUS)
            if (tone != GuideTone.Ink) next.setTextColor(GuideColours.CREAM)
        } else {
            next.setTextColor(GuideMotion.faded(strong, NEXT_WAITING))
            next.background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1).coerceAtLeast(2), GuideMotion.faded(strong, NEXT_WAITING))
                cornerRadius = Radii.px(resources, OFFER_RADIUS)
            }
        }
    }

    /** A fill with one of the shared [Radii]. */
    private fun rounded(colour: Int, radius: Int) = GradientDrawable().apply {
        setColor(colour)
        cornerRadius = Radii.px(resources, radius)
    }

    /**
     * The bar at the top shows the chapters: those behind you are full, the one you're in fills up slide by slide, and the
     * rest wait. [done] is not used: the flow knows where it is.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun paintSegments(done: Int, advancing: Boolean = false) {
        val strong = if (tone == GuideTone.Ink) GuideColours.ORANGE else GuideColours.INK
        val passed = passedIds()
        // Each chapter is as wide as its slides, so every tick in the bar is the same size (owner, v5.2).
        for ((index, chapter) in flow.barChapters.withIndex()) {
            val segment = segments.getChildAt(index) as? GuideSegment ?: continue
            val ids = flow.idsOf(chapter)
            val params = segment.layoutParams as LinearLayout.LayoutParams
            val weight = ids.size.coerceAtLeast(1).toFloat()
            if (params.weight != weight) {
                params.weight = weight
                segment.layoutParams = params
            }
            val here = ids.indexOf(flow.step.id)
            // A chapter before this one counts as done, as Next can skip a slide.
            val before = flow.barChapters.indexOf(flow.step.chapter).let { it < 0 || index < it }
            val ticks = BooleanArray(ids.size) { before || ids[it] in passed }
            segment.paint(on = strong, off = GuideMotion.faded(strong, SEGMENT_OFF), passed = ticks, current = here)
            if (advancing && here >= 0) segment.advance(here)
        }
    }

    // The story.

    private fun begin() {
        if (started) return
        started = true
        if (playground) {
            showPlayground()
            return
        }
        val resumed = preferences.getString(RESUME, null) != null && flow.step.id != GUIDE_STEPS.first().id
        again.visibility = if (resumed) View.VISIBLE else View.GONE
        // Coming back says one thing, once: that you're back, or that chapters can be picked. On the very first slide, neither.
        val leftEarly = preferences.getBoolean(LEFT_EARLY, false)
        preferences.edit().remove(LEFT_EARLY).apply()
        val first = flow.step.id == GUIDE_STEPS.first().id
        if (leftEarly && !first) {
            arrivalTitle = COME_BACK_TITLE
            arrivalBody = COME_BACK_BODY
        }
        if (resumed || !GuideMotion.enabled) {
            reveal(flow.step)
            return
        }
        // The first thing you see: the line crosses the screen and leaves the mark behind.
        overlay.showMark(GuideColours.ORANGE)
        main.postDelayed({
            overlay.hideMark()
            reveal(flow.step)
        }, INTRO_MILLIS)
    }

    private fun reveal(step: GuideStep) {
        if (playground) {
            showPlayground()
            return
        }
        keys.appear()
        for (view in listOf(field, keys, next, previous, chapter, again, close, segments, script)) view.animate().alpha(1f).setDuration(FADE_MILLIS).start()
        enter(step)
    }

    private fun enter(step: GuideStep) {
        heldKeys.clear()
        // The field is set a few times as the slide opens: the bar shows only where it settles.
        GuideHooks.holdBar(BAR_SETTLE_MILLIS)
        guideLook(true)
        showBigNext(false)
        hideChip(now = true)
        idleChipShown = false
        lockChips.clear()
        lastPoint = null
        travel = 0f
        helpShown = false
        passed = false
        scenarioShown = false
        idleShown = false
        autoTyping = false
        pasteDeclined = false
        endButtons.visibility = View.GONE
        hint.setOnClickListener(null)
        // A chapter with one text box carries its field on from slide to slide; any other slide starts a clean one.
        val carry = enteredChapter == step.chapter && step.carriesField
        regrets = 0
        regretsFrozen = false
        stopHunt()
        if (enteredChapter != step.chapter) {
            deleteResets = 0
            if (step.chapter == GuideChapter.End) markAllFinished()
        }
        enteredChapter = step.chapter
        // At the end Next keeps its room, so that the offer to start typing stays in the middle.
        next.visibility = if (step.chapter == GuideChapter.End) View.INVISIBLE else View.VISIBLE
        // Back is the phone's Back.
        previous.visibility = View.GONE
        chapter.text = step.chapter.label
        GuideHooks.nearMiss = step.nearMiss
        // A slide with nothing to try is done as it opens.
        passed = step.info
        // Until the slide is passed, Next says Skip.
        next.text = if (passed) "Next" else "Skip"
        paintNext()
        offerFor(step)
        // A slide starts with a clean field, with its own text to work on or nothing, unless its chapter carries the field on.
        // The delete chapter's field shows its end; the steering line has the cursor in its middle.
        if (!carry) {
            seeding = true
            when (step.chapter) {
                GuideChapter.Steering -> seedSteering(step.seed)
                else -> {
                    field.setText(if (step.seed.isEmpty()) "" else step.seed + " ")
                    field.setSelection(step.cursorAt?.invoke(field.text.toString())?.coerceIn(0, field.length()) ?: field.length())
                }
            }
            seeding = false
            // The keyboard reads the new field afresh, so a first word gets its capital.
            input.restartInput(field)
        }
        // Each slide unlocks what it teaches, and only in this field; the bar shows its side words or only the middle one.
        GuideHooks.lessons = flow.lessons
        GuideHooks.forceFix = step.forceFix
        field.underlineOnly = step.id == "steer"
        GuideHooks.barSides(step.sides)
        setUpLesson(step)
        flow.seed(field.text.toString(), field.selectionStart)
        aim()
        // The chapter's text box shows on the slides that have one, and stays as it is from slide to slide.
        val typing = step.text.isNotEmpty()
        // No box above the field (v5): each slide's line is in the field, in grey ahead of the cursor on the typing slides.
        script.visibility = View.GONE
        if (typing && step.chapter != GuideChapter.End) startPassage(step.text)
        // A setting that the slide needs is off: the slide says so, over its own words.
        val missing = flow.missing
        // The title is always the slide's own. A line about arriving, or about a setting that's off, is a chip.
        val (said, shownBody) = when {
            missing == GuideNeed.FnAsCtrl -> FN_OFF_TITLE to FN_OFF_BODY
            missing == GuideNeed.HoldSymbols -> HOLD_OFF_TITLE to HOLD_OFF_BODY
            missing != null -> FEATURE_OFF_TITLE to FEATURE_OFF_BODY
            else -> arrivalTitle to flow.instruction
        }
        arrivalTitle = null
        arrivalBody = null
        swapIn(title, step.title, 0)
        // Only the instruction that is needed now. The slide moves on, or back, as the keyboard changes. See [showStage].
        swapIn(body, shownBody, ENTER_STAGGER_MILLIS)
        if (said != null) main.postDelayed({ if (!passed && flow.step === step) showChip(said) }, ENTER_MILLIS)
        hideHint()
        if (missing == GuideNeed.FnAsCtrl) offerDecline()

        if (step.offersShare) Contributions.guideEnded(this)
        // The keyboard starts the step in the menu that it needs: the Sym panel for Alt and the emoji, closed for typing.
        val panelLeft = panelOpen && step.setup == Modifiers.Panel.Closed && enteredBefore
        val altLeft = altOn && !panelOpen && !step.usesPanel && enteredBefore
        enteredBefore = true
        emptyBarShown = false
        if (panelLeft) {
            showScenario(TOO_LATE_TITLE, TOO_LATE_PANEL)
            main.postDelayed({ if (!passed && !changing && !panelOpen) backOnTrack() }, TOO_LATE_MILLIS)
        } else if (altLeft) {
            showScenario(TOO_LATE_TITLE, TOO_LATE_ALT)
            // It goes once Alt is off. See [modifiersWentOff].
            shownModifierHint = GuideVerdict.Hint(TOO_LATE_ALT, TOO_LATE_TITLE)
        }
        step.setup?.let(GuideHooks::openPanel)
        showScript()
        paintSegments(flow.stepIndex)
        main.removeCallbacks(showHelp)
        if (step.help != null) main.postDelayed(showHelp, if (step.helpTitle != null) HELP_TITLE_AFTER_MILLIS else HELP_AFTER_MILLIS)
        if (step.chapter == GuideChapter.End) {
            preferences.edit().remove(RESUME).apply()
            // The end of the guide offers to share swipes until the user answers: here, and in the app if they leave.
            Contributions.guideEnded(this)
        } else preferences.edit().putString(RESUME, flow.resumeId).apply()
        busy = false
        // A new slide shows its gesture at once, as soon as its words are in.
        acted(DEMO_ENTER_MILLIS)
        main.removeCallbacks(checkBar)
        if (step.bar == GuideBar.Menu) main.postDelayed(checkBar, BAR_CHECK_MILLIS)
        // Another slide did this one already: it shows done.
        if (arriveDone) {
            arriveDone = false
            stepDone()
        }
        // The stop between the parts is a win already: it shows as done, with the offer to start typing.
        if (step.pause) {
            busy = true
            passed = true
            backdrop.wipeTo(GuideColours.ORANGE)
            overlay.sweep()
            main.postDelayed({ if (passed) applyTone(GuideTone.Orange) }, WIPE_MILLIS)
        }
        if (step.chapter == GuideChapter.End) showEnd(step)
    }

    /**
     * The end, however it's reached: the name, the line under it and the slogan, then the two big buttons. It wraps its words, as
     * setup does, and locks nothing. The text box goes, so the name and the buttons have the room: its words wait in the field.
     */
    private fun showEnd(end: GuideStep) {
        guideLook(false)
        unlockKeyboard()
        keyboardShown(false)
        // Nothing of the slides stays: the finale has the screen, and the buttons wait at the bottom until it's done.
        for (view in listOf<View>(script, title, instruction, hint, field, phantom, keys, segments, again, offer)) view.visibility = View.GONE
        // The chapter's name keeps its room, empty, so ✕ stays in the top right corner (owner, v5.2: it moved to the left).
        chapter.visibility = View.INVISIBLE
        next.visibility = View.INVISIBLE
        // The buttons keep their room, unseen, while the finale plays, so it's drawn between the top bar and them and nothing
        // overlaps, whatever the screen's size (owner, v5.2: the end didn't fit).
        fillEndChoices()
        endButtons.visibility = View.INVISIBLE
        spacer.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        title.text = end.title
        finaleView.lines = SUMMARY_LINES
        finaleView.visibility = View.VISIBLE
        root.post {
            val top = (column.paddingTop + dp(TOP_ACTION + 16)).toFloat()
            val bottom = if (endButtons.height > 0) endButtons.top - dp(FINALE_ROOM).toFloat() else root.height * FINALE_SHARE
            finaleView.box = top to maxOf(top + dp(160), bottom)
            finaleView.play()
        }
    }

    /**
     * The line above the field: the script from the next word on, with that word in the accent. The ghost finger on the drawing
     * of the keys follows the same word on the steps that swipe.
     */
    private fun showScript() {
        showPassage()
        // Once a slide is passed nothing glows any more, as Space did on the first slide.
        // The word menu's key hints wait until the menu is open and its buttons are in: never a blink before (owner, v5.2).
        val menuWaits = flow.step.bar == GuideBar.Menu && flow.stage > 0 && !menuSettled()
        val shown = ghostCue(if (playground || passed || menuWaits) GuideCue.Free else flow.cue)
        if (shown != keys.cue) keys.show(shown)
        keys.face = if (playground || passed || menuWaits) null else flow.labels
        if (menuWaits && menuOpenSince >= 0) {
            main.removeCallbacks(menuHint)
            main.postDelayed(menuHint, MENU_SETTLE_MILLIS)
        }
        showGhost()
    }

    /**
     * The Typing chapter's text, in grey in the field itself, ahead of what's typed: the rest of the word you're on, letter by
     * letter, then the words after it. A demo draws over it while it plays.
     */
    private fun showGhost() {
        if (!::field.isInitialized) return
        // The field draws it itself, from the slide's first frame, demo or not. See [GuideField.ghost].
        field.ghost = when {
            playground || !started || settingUp || guideLooks != true -> ""
            flow.step.chapter == GuideChapter.Typing && flow.step.text.isNotEmpty() -> ghostText()
            else -> ""
        }
    }

    /** What the ghost says after the field's text: see [showGhost]. A slide with a [GuideStep.ghost] shows its words instead. */
    private fun ghostText(): String {
        val step = flow.step
        val text = field.text.toString()
        val track = passage.track(typedSince(text))
        val own = step.ghost.split(' ').filter { it.isNotEmpty() }
        fun shown(at: Int) = own.getOrNull(at) ?: passage.wordAt(at)
        val out = StringBuilder()
        if (track.typed.isNotEmpty()) ghostRest(shown(track.next), track.typed)?.let(out::append)
        else {
            if (text.isNotEmpty() && !text.last().isWhitespace()) out.append(' ')
            out.append(shown(track.next))
        }
        val left = step.text.split(' ').count { it.isNotEmpty() } - 1 - track.next
        if (left < 0) return ""
        for (more in 1..minOf(GHOST_WORDS, left)) out.append(' ').append(shown(track.next + more))
        return out.toString()
    }

    /** What's left of [word] after the letters [typed], or null if they aren't its start. */
    private fun ghostRest(word: String, typed: String): String? {
        var matched = 0
        var at = 0
        while (at < word.length && matched < typed.length) {
            if (word[at].isLetter()) {
                if (!word[at].equals(typed[matched], ignoreCase = true)) return null
                matched++
            }
            at++
        }
        return if (matched == typed.length) word.substring(at) else null
    }

    // The passage above the field, as a typing race shows it: always exactly two lines of the script, cut where its lines end, with
    // the word you're on underlined wherever it is on the line, the words behind you dimmed, and anything typed wrong boxed in red
    // until it's fixed. When you finish a line, the lines slide up by one.

    /** The script laid out at the passage's width, and that width. */
    private var passageLayout: StaticLayout? = null
    private var passageWidth = -1
    /** The line that the passage shows on top, and its slide up, if one runs. */
    private var passageLine = -1
    private var passageSlide: ValueAnimator? = null

    /** The text to type along to now, and where in the field it began: what's typed since then is matched against it. */
    private var passage = Passage(GuideScript.TEXT)
    private var passageFrom = 0
    private var passageText = ""
    /** The text that the passage started from, before any symbol took the place of its ⬚. */
    private var passageSource = ""

    /** Starts [text] as the text to type along to, from the end of the field. */
    private fun startPassage(text: String) {
        if (text.isEmpty()) return
        passage = Passage(text)
        sentenceStarts = null
        passageFrom = field.length()
        passageLayout = null
        passageLine = -1
        passageText = text
        passageSource = text
    }

    /**
     * The slide is done: [text] follows on from the end of the line you're on, and then again and again. What's typed and the lines
     * on show stay as they are, so the box never jumps and nothing you typed counts for less.
     */
    private fun continuePassage(text: String) {
        if (text.isEmpty()) return
        val next = passage.track(typedSince(field.text.toString())).next
        // The last word of the line with the word you're on: the new text starts on the line after it.
        val layout = passageLayout
        val lastOnLine = if (layout == null) next else {
            val end = layout.getLineEnd(layout.getLineForOffset(passage.ranges[next.mod(passage.words.size)].first))
            (passage.ranges.indexOfLast { it.first < end }).coerceAtLeast(next)
        }
        val lead = (0..lastOnLine).joinToString(" ") { passage.wordAt(it) }
        passage = Passage(lead, text)
        passageLayout = null
        passageText = text
    }

    /** What's been typed since the passage began, out of [text]: if text before it was deleted, from wherever it ends now. */
    private fun typedSince(text: String): String {
        if (text.length < passageFrom) passageFrom = text.length
        return text.substring(passageFrom)
    }

    private fun showPassage() {
        val width = script.width - script.totalPaddingLeft - script.totalPaddingRight
        if (width <= 0) {
            // Before the first layout there's no width to cut lines at, so it waits for one.
            script.post { if (script.width > 0) showPassage() }
            return
        }
        val text = passage.text
        val layout = passageLayout?.takeIf { passageWidth == width }
            ?: StaticLayout.Builder.obtain(text, 0, text.length, script.paint, width).setIncludePad(false).build()
                .also { passageLayout = it; passageWidth = width; passageLine = -1 }
        val track = passage.track(typedSince(field.text.toString()))
        val count = passage.words.size
        val next = track.next.mod(count)
        val cycle = track.next - next
        val line = layout.getLineForOffset(passage.ranges[next].first)
        val styled = passageSpans(track, next, cycle)
        if (playground) {
            // The front page shows the whole sentence you're in, wrapped by words, never cut (owner, v5.3).
            val starts = sentenceStarts ?: PlaygroundText.sentenceStarts(passage.words).also { sentenceStarts = it }
            val words = PlaygroundText.sentenceAround(starts, count, next)
            val from = passage.ranges[words.first].first
            val to = passage.ranges[words.last].last + 1
            script.maxLines = PROMPT_LINES
            script.setLines(PROMPT_LINES)
            script.scrollTo(0, 0)
            script.text = styled.subSequence(from, to)
            return
        }
        // The line after the last is the first again, as the script starts over.
        fun lineText(at: Int): CharSequence {
            val wrapped = at.mod(layout.lineCount)
            val start = layout.getLineStart(wrapped)
            var end = layout.getLineEnd(wrapped)
            while (end > start && text[end - 1].isWhitespace()) end--
            return if (at >= layout.lineCount) plain(text.substring(start, end)) else styled.subSequence(start, end)
        }
        fun lines(from: Int, many: Int) = SpannableStringBuilder().apply {
            for (at in from until from + many) {
                if (at > from) append("\n")
                append(lineText(at))
            }
        }
        val forward = passageLine >= 0 && line == passageLine + 1
        passageLine = line
        if (passageSlide?.isRunning == true) passageSlide?.cancel()
        if (!forward || !GuideMotion.enabled) {
            script.scrollTo(0, 0)
            script.text = lines(line, 2)
            return
        }
        // Only the lines move: the line you finished goes up and out, and the next comes up into its place.
        script.text = lines(line - 1, 3)
        val lineHeight = script.lineHeight
        passageSlide = ValueAnimator.ofInt(0, lineHeight).apply {
            duration = PASSAGE_SLIDE_MILLIS
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { script.scrollTo(0, it.animatedValue as Int) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    script.scrollTo(0, 0)
                    script.text = lines(line, 2)
                }
            })
            start()
        }
    }

    /** The front page's sentences, by the word each starts at, for its passage. */
    private var sentenceStarts: List<Int>? = null

    /** The whole passage, coloured for where typing is: right words grey, skipped ones boxed, the word you're on underlined. */
    private fun passageSpans(track: Passage.Track, next: Int, cycle: Int): SpannableStringBuilder {
        val strong = if (tone == GuideTone.Ink) GuideColours.CREAM else GuideColours.INK
        val accent = if (tone == GuideTone.Ink) GuideColours.ORANGE else GuideColours.INK
        val wrong = GuideMotion.faded(PASSAGE_WRONG, PASSAGE_WRONG_ALPHA)
        val built = SpannableStringBuilder(passage.text)
        fun span(what: Any, range: IntRange) = built.setSpan(what, range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        built.setSpan(ForegroundColorSpan(strong), 0, built.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        for ((index, range) in passage.ranges.withIndex()) {
            when {
                index < next -> {
                    span(ForegroundColorSpan(GuideMotion.faded(strong, PASSAGE_DONE)), range)
                    // A word skipped over keeps a faint box, so you can see what you missed.
                    if (cycle + index !in track.done) span(BackgroundColorSpan(GuideMotion.faded(PASSAGE_WRONG, PASSAGE_SKIPPED_ALPHA)), range)
                }
                index == next -> {
                    span(ForegroundColorSpan(accent), range)
                    span(UnderlineSpan(), range)
                    // The letters typed so far: each one that doesn't match is boxed, and so is anything past the word's end.
                    val expected = passage.text.substring(range.first, range.last + 1)
                    var letter = 0
                    for ((offset, char) in expected.withIndex()) {
                        if (!char.isLetter()) continue
                        if (letter >= track.typed.length) break
                        val at = (range.first + offset)..(range.first + offset)
                        // Each letter typed right turns grey as it's typed, like the words behind you; a wrong one is boxed.
                        if (!track.typed[letter].equals(char, ignoreCase = true)) span(BackgroundColorSpan(wrong), at)
                        else span(ForegroundColorSpan(GuideMotion.faded(strong, PASSAGE_DONE)), at)
                        letter++
                    }
                    if (track.typed.length > GuideScript.letters(expected).length) span(BackgroundColorSpan(wrong), range)
                }
            }
        }
        return built
    }

    /** A line of the script as it is, before typing reaches it. */
    private fun plain(text: String) = SpannableStringBuilder(text).apply {
        val strong = if (tone == GuideTone.Ink) GuideColours.CREAM else GuideColours.INK
        setSpan(ForegroundColorSpan(strong), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** What the drawing of the keys can draw for [cue]: the next word of the script is turned into that word's letters. */
    private fun ghostCue(cue: GuideCue): GuideCue {
        if (cue == GuideCue.Spell) {
            // Only what's left of the word: typed t and stopped, it goes on from i (owner, v5).
            val text = field.text.toString()
            val track = passage.track(typedSince(text))
            if (track.next >= passage.words.size) return GuideCue.Free
            val word = flow.step.ghost.split(' ').filter { it.isNotEmpty() }.getOrNull(track.next) ?: passage.wordAt(track.next)
            val rest = if (track.typed.isEmpty()) word else ghostRest(word, track.typed) ?: ""
            return GuideCue.Spot(rest.filter(Char::isLetter).map { it.uppercaseChar().toString() } + Layout.SPACE, rounds = 1)
        }
        if (cue != GuideCue.NextWord) return cue
        val word = passage.wordAt(passage.track(typedSince(field.text.toString())).next)
        return GuideCue.Word(GuideScript.letters(word).ifEmpty { "titan" })
    }

    // Demonstrations. A slide shows its gesture by itself, in grey over the field, again and again until you take over. It types
    // nothing. Anything that you do stops it at once, and it starts again after a quiet while.

    private var demoStart = -1L
    private var demoLength = 0L
    private var demoKeys = 0
    private var demoTurned = false
    private var demo: GuideDemo? = null
    /** The bar's own demonstration has started in this showing. */
    private var demoBarPlayed = false
    /** The pushes of the word that this demonstration types or swipes, worked out on its first frame. */
    private var demoPushes: List<DemoPush>? = null
    /** Which letter key types each mark with Alt, read from the phone's own keyboard. */
    private val altKeys by lazy {
        val found = HashMap<Char, String>()
        for (key in Layout.letterKeys) Keys.letter(key, KeyEvent.META_ALT_ON)?.singleOrNull()?.let { found.putIfAbsent(it, key) }
        found
    }
    private val startDemo = Runnable { beginDemo() }
    private val demoTick = Runnable { stepDemo() }

    /** Something the user did: a demonstration stops, and the next one waits for a quiet moment, or [wait] on a new slide. */
    private fun acted(wait: Long = DEMO_IDLE_MILLIS) {
        endDemo()
        main.removeCallbacks(startDemo)
        if (!passed && ready && !changing) main.postDelayed(startDemo, wait)
        // One idle chip, once a slide, after a long while with no touch at all.
        main.removeCallbacks(idleOnce)
        if (!idleChipShown && !passed && ready && !changing && !playground && !settingUp && !flow.finished) main.postDelayed(idleOnce, IDLE_MILLIS)
    }

    /** Nobody acted for a while: the slide asks, with its own instruction, and asks again after [again] if it's not -1. */
    @Suppress("UNUSED_PARAMETER")
    private fun idle(title: String, again: Long) {
        // The clown hunt keeps its own time and says its own things.
        if (passed || busy || changing || playground || settingUp || idleChipShown || huntStart >= 0) return
        idleChipShown = true
        showChip(title)
    }

    /** A scenario: its words show in a chip over the instruction. The title and instruction stay the slide's own. */
    @Suppress("UNUSED_PARAMETER")
    private fun showScenario(heading: String, text: String) = showChip(heading)

    /** Back on track: nothing to undo, as a chip goes by itself. */
    private fun backOnTrack() = Unit

    /** A hint or a scenario's chip, with its haptic (owner, v5). */
    private fun hinted(text: String) {
        if (text.isBlank() || passed) return
        haptics.play(Haptic.GuideHint)
        showChip(text)
    }

    /**
     * A chip slides over the instruction for a real mistake, for at most [CHIP_MILLIS], and then the instruction comes back. One at a
     * time and no flicker: a chip shows for at least [CHIP_MIN_MILLIS], and a new one waits until it ends. A passed slide shows
     * none, unless [force], for the line that comes with passing.
     */
    private fun showChip(text: String, force: Boolean = false) {
        if (!::chip.isInitialized || guideLooks != true || (passed && !force) || text.isBlank()) return
        if (chip.visibility == View.VISIBLE) {
            if (chip.text.toString() == text) return
            pendingChip = text
            main.removeCallbacks(chipOver)
            main.postDelayed(chipOver, max(0L, chipAt + CHIP_MIN_MILLIS - SystemClock.uptimeMillis()))
            return
        }
        chip.animate().cancel()
        chip.text = text
        chip.visibility = View.VISIBLE
        chipAt = SystemClock.uptimeMillis()
        chip.alpha = 0f
        chip.translationX = dp(CHIP_SLIDE).toFloat()
        chip.animate().alpha(1f).translationX(0f).setDuration(CHIP_IN_MILLIS).start()
        body.animate().alpha(0f).setDuration(CHIP_IN_MILLIS).start()
        main.removeCallbacks(chipOver)
        main.postDelayed(chipOver, CHIP_MILLIS)
    }

    /** The chip goes, and the one waiting shows, or the instruction comes back. [now] clears it all at once, as a slide changes. */
    private fun hideChip(now: Boolean = false) {
        if (!::chip.isInitialized) return
        main.removeCallbacks(chipOver)
        val waiting = pendingChip
        pendingChip = null
        chip.animate().cancel()
        if (now || chip.visibility != View.VISIBLE) {
            chip.visibility = View.GONE
            body.animate().cancel()
            body.alpha = 1f
            return
        }
        chip.animate().alpha(0f).translationX(-dp(CHIP_SLIDE / 4).toFloat()).setDuration(CHIP_OUT_MILLIS).withEndAction {
            chip.visibility = View.GONE
            if (waiting != null && !passed && !changing) showChip(waiting)
            else body.animate().alpha(1f).setDuration(CHIP_OUT_MILLIS).start()
        }.start()
    }

    /**
     * A demo may play now: not over selected text, an open word menu, or a finger on the pad (owner, v5: it replaced a selection
     * and an open menu). One that plays stops as soon as any of them is there.
     */
    private fun demoAllowed(): Boolean {
        if (field.selectionStart != field.selectionEnd) return false
        if (GuideHooks.menuOpen?.invoke() == true) return false
        return !fingerDown
    }

    private fun beginDemo() {
        val step = flow.step
        if (busy || passed || changing || step.pause || step.chapter == GuideChapter.End) return
        if (!demoAllowed()) {
            main.postDelayed(startDemo, DEMO_GAP_MILLIS)
            return
        }
        val stageCue = flow.cue
        val hasGhost = stageCue != GuideCue.Free && stageCue !is GuideCue.Spot
        if (step.demo == null && step.bar == null && !hasGhost) return
        demo = step.demo
        val cue = ghostCue(stageCue)
        if (cue != keys.cue) keys.show(cue)
        demoStart = GuideMotion.now()
        demoLength = if (hasGhost) maxOf(keys.ghostMillis(), DEMO_MILLIS) else DEMO_MILLIS
        demoKeys = 0
        demoTurned = false
        demoPushes = null
        demoBarPlayed = false
        // The word menu is a gesture on the bar itself, so the bar acts it out.
        if (step.bar == GuideBar.Menu && flow.stage == 0) {
            demoBarPlayed = true
            // Only the hold on a real word of the bar: the menu itself opens only for a real hold.
            GuideHooks.stripDemo(GuideHooks.StripDemo.Menu(emptyList(), opens = false))
        }
        keys.demo(true)
        // The grey words would sit on top of the empty field's hint.
        field.hint = null
        main.post(demoTick)
    }

    private fun stepDemo() {
        if (demoStart < 0) return
        if (!demoAllowed()) {
            endDemo()
            main.postDelayed(startDemo, DEMO_IDLE_MILLIS)
            return
        }
        val t = (GuideMotion.now() - demoStart).toFloat() / demoLength
        if (t >= 1f) {
            endDemo()
            main.postDelayed(startDemo, DEMO_GAP_MILLIS)
            return
        }
        demo?.let { showPhantom(it, t) }
        // Once the menu is really open, the demo presses P, which closes it.
        if (flow.step.bar == GuideBar.Menu && flow.stage == 1 && demoKeys == 0 && t > MENU_O_OPEN_AT) {
            demoKeys = 1
            keys.light("P")
        }
        main.postDelayed(demoTick, DEMO_FRAME_MILLIS)
    }

    private fun endDemo() {
        main.removeCallbacks(demoTick)
        if (demoBarPlayed) {
            demoBarPlayed = false
            GuideHooks.stripDemo(null)
        }
        if (demoStart < 0 && phantom.text.isEmpty()) return
        demoStart = -1
        keys.demo(false)
        phantom.text = ""
        field.hint = if (guideLooks == true) null else FIELD_HINT
        showGhost()
    }

    /**
     * Draws [demo] at [t], from 0 to 1, over the field. The field's own text is there too, but clear, so that what is added
     * lines up with it. Added text is grey; a word that goes is covered in the field's colour; a selection is washed in orange.
     */
    private fun showPhantom(demo: GuideDemo, t: Float) {
        val text = field.text.toString()
        val grey = ghostGrey()
        val built = SpannableStringBuilder(text)
        // Android logs an error for a span with no length, and this runs for every frame.
        if (text.isNotEmpty()) built.setSpan(ForegroundColorSpan(Color.TRANSPARENT), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val gap = if (text.isEmpty() || text.endsWith(' ')) "" else " "
        val next = passage.wordAt(passage.track(typedSince(text)).next)
        // On a symbol slide what the demo types goes in the gap, where the cursor waits, never after the line (owner, v5.3: a
        // grey "p" sat after the line).
        val inGap = field.gap.takeIf { it in 0..text.length }
        fun add(added: String) {
            if (added.isEmpty()) return
            if (inGap != null) {
                val shown = added.trim()
                built.insert(inGap, shown)
                built.setSpan(ForegroundColorSpan(grey), inGap, inGap + shown.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                return
            }
            val start = built.length
            built.append(added)
            built.setSpan(ForegroundColorSpan(grey), start, built.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val lastStart = text.trimEnd().lastIndexOf(' ') + 1
        val lastEnd = text.trimEnd().length
        val typing = demo == GuideDemo.TypeNext || demo == GuideDemo.SwipeNext || demo == GuideDemo.FixNext || demo == GuideDemo.TypeSlip
        if (typing && GuideScript.isMarks(next)) {
            // Marks go right after their word: the keys light one push at a time, and then the marks show in place.
            val pushes = markPushes(next)
            val shown = ((t / TYPE_SHARE) * pushes.size).toInt().coerceIn(0, pushes.size)
            while (demoKeys < shown) pushes[demoKeys++].forEach(keys::light)
            if (t > TYPE_SHARE) {
                built.delete(text.trimEnd().length, built.length)
                add(next + " ")
            }
        } else when (demo) {
            GuideDemo.TypeNext -> {
                val pushes = pushesFor(next, swiped = false)
                val shown = ((t / TYPE_SHARE) * pushes.size).toInt().coerceIn(0, pushes.size)
                lightPushes(pushes, shown)
                if (shown > 0) add(gap + pushes[shown - 1].text)
            }
            // The word lands at the end of the ghost's path, and its marks follow one push at a time.
            GuideDemo.SwipeNext -> if (t > SWIPE_LANDS) {
                val pushes = pushesFor(next, swiped = true)
                val marks = ((t - SWIPE_LANDS) / (MARKS_DONE - SWIPE_LANDS) * pushes.size).toInt()
                val shown = (1 + marks).coerceIn(1, pushes.size)
                lightPushes(pushes, shown)
                add(gap + pushes[shown - 1].text)
            }
            GuideDemo.FixNext -> when {
                t > FIX_LANDS -> add(gap + next)
                t > SWIPE_LANDS / 2 -> add(gap + GuideScript.slip(next))
            }
            GuideDemo.DeleteLast -> if (lastEnd > lastStart) {
                if ((flow.cue as? GuideCue.Flick)?.hold == true) {
                    // A flick that stays: words keep going, one after another, while the finger is down.
                    val gone = ((t - .5f) / .4f * HOLD_DEMO_WORDS).toInt().coerceIn(0, HOLD_DEMO_WORDS) + if (t > .5f) 1 else 0
                    var from = lastEnd
                    repeat(gone) { from = text.substring(0, from).trimEnd().lastIndexOf(' ') + 1 }
                    if (from < lastEnd) built.setSpan(BackgroundColorSpan(fieldFill()), from, lastEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    // One flick, one word, gone at once.
                    if (t > FLICK_GONE) built.setSpan(BackgroundColorSpan(fieldFill()), lastStart, lastEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            GuideDemo.MoveCursor -> if (lastEnd > 0) {
                val back = ((t - TAPS_SHARE).coerceAtLeast(0f) / (1f - TAPS_SHARE) * (lastEnd - lastStart + 1)).toInt()
                val at = (lastEnd - back).coerceIn(1, lastEnd)
                built.setSpan(BackgroundColorSpan(GuideMotion.faded(GuideColours.ORANGE, CARET_ALPHA)), at - 1, at, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            GuideDemo.SelectLast -> if (lastEnd > lastStart && t > TAPS_SHARE) {
                val wash = ((t - TAPS_SHARE) / .2f).coerceIn(0f, 1f) * SELECT_ALPHA
                built.setSpan(BackgroundColorSpan(GuideMotion.faded(GuideColours.ORANGE, wash)), lastStart, lastEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            is GuideDemo.HoldLetter -> {
                val key = keyFor(demo.key)
                val symbol = Keys.letter(key, KeyEvent.META_ALT_ON) ?: demo.key
                if (t < HOLD_TURNS) {
                    keys.light(key)
                    add(gap + key.lowercase())
                } else {
                    if (!demoTurned) {
                        demoTurned = true
                        keys.flip(key, symbol)
                    }
                    add(gap + symbol)
                }
            }
            // A slip typed key by key; then Space lights, the bar shows the fix swapping in, and the right word takes its place.
            GuideDemo.TypeSlip -> {
                val slip = GuideScript.slip(next)
                val pushes = pushesFor(slip, swiped = false)
                val shown = ((t / SLIP_TYPED) * pushes.size).toInt().coerceIn(0, pushes.size)
                lightPushes(pushes, shown)
                if (t >= SLIP_TYPED && !demoBarPlayed) {
                    demoBarPlayed = true
                    GuideHooks.stripDemo(GuideHooks.StripDemo.Fix(slip, next))
                }
                if (t < SLIP_FIXED) {
                    if (shown > 0) add(gap + pushes[shown - 1].text)
                } else {
                    if (!demoTurned) {
                        demoTurned = true
                        keys.light(Layout.SPACE)
                    }
                    add(gap + next + " ")
                }
            }
            is GuideDemo.AltLetter -> {
                val key = keyFor(demo.key)
                val symbol = Keys.letter(key, KeyEvent.META_ALT_ON) ?: demo.key
                val pushed = listOf(Layout.ALT, key)
                val due = (t / ALT_KEYS_SHARE * pushed.size).toInt().coerceAtMost(pushed.size)
                while (demoKeys < due) keys.light(pushed[demoKeys++])
                if (t > ALT_KEYS_SHARE) add(gap + symbol)
            }
            GuideDemo.CopyLast -> if (lastEnd > lastStart && t > TAPS_SHARE) {
                val wash = ((t - TAPS_SHARE) / .2f).coerceIn(0f, 1f) * SELECT_ALPHA
                built.setSpan(BackgroundColorSpan(GuideMotion.faded(GuideColours.ORANGE, wash)), lastStart, lastEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (t > COPY_KEYS && demoKeys == 0) {
                    demoKeys = 1
                    keys.light(Layout.FN)
                    keys.light("C")
                }
            }
            is GuideDemo.Insert -> {
                val pushed = (t / INSERT_SHARE * demo.keys.size).toInt().coerceAtMost(demo.keys.size)
                if (pushed > demoKeys) {
                    keys.light(demo.keys[pushed - 1])
                    demoKeys = pushed
                }
                if (t > INSERT_SHARE) add(gap + demo.text)
            }
        }
        phantom.text = if (guideLooks == true && flow.step.chapter == GuideChapter.Typing) "" else built
        phantom.scrollTo(0, field.scrollY)
    }

    /**
     * What a lesson needs before it starts: something on the clipboard to paste, a word already selected so that the paste goes in
     * its place, and the guide's own clips in the keyboard's clipboard, in place of the user's, so that it has something to show.
     */
    private fun setUpLesson(step: GuideStep) {
        step.clip?.let { text ->
            runCatching { clipboard.setPrimaryClip(android.content.ClipData.newPlainText("TitanKeys guide", text)) }
            main.postDelayed({ GuideHooks.dismissClip() }, CLIP_HIDE_MILLIS)
        }
        if (step.preselect && field.selectionStart == field.selectionEnd) {
            // The words that the text's first sentence points at: "Paste over this word."
            val start = field.text.toString().indexOf(PASTE_OVER)
            if (start >= 0) field.setSelection(start, start + PASTE_OVER.length)
        }
        // The clipboard shows the slide's own clips, or the user's again once a slide has none.
        GuideHooks.seedClips(step.sandboxClips)
    }

    /** The letter key for [key]: itself, or for a symbol, the key that types it with Alt on this phone. */
    private fun keyFor(key: String) = if (key.length == 1 && key[0].isLetter()) key.uppercase() else altKeys[key.firstOrNull() ?: ' '] ?: key

    private fun markPushes(marks: String) = GuideScript.markKeys(marks, TypingSettings.autoPunctuation(this)) { altKeys[it] }

    private fun pushesFor(word: String, swiped: Boolean) = demoPushes
        ?: GuideScript.pushes(word, swiped, TypingSettings.autoPunctuation(this)) { altKeys[it] }.also { demoPushes = it }

    /** Lights the keys of every push up to [shown] that has not lit yet. */
    private fun lightPushes(pushes: List<DemoPush>, shown: Int) {
        while (demoKeys < shown) pushes[demoKeys++].keys.forEach(keys::light)
    }

    /** Adds words to the end of the text, as if typed, without a step taking them as done. */
    private fun addText(words: String) {
        seeding = true
        val text = field.text.toString()
        val joined = if (text.isEmpty() || text.endsWith(' ')) words else " $words"
        field.text.append(joined)
        field.setSelection(field.text.length)
        seeding = false
    }

    private fun swapIn(view: TextView, text: String, delay: Long) {
        view.animate().cancel()
        view.text = text
        view.alpha = 0f
        view.translationX = dp(ENTER_OFFSET).toFloat()
        view.animate().alpha(1f).translationX(0f).setStartDelay(delay).setDuration(ENTER_MILLIS)
            .setInterpolator(OvershootInterpolator(ENTER_OVERSHOOT)).start()
    }

    private fun showHint(text: String) {
        if (guideLooks == true && !keepHint) {
            showChip(text)
            return
        }
        hint.text = text
        if (hint.visibility != View.VISIBLE) {
            hint.visibility = View.VISIBLE
            hint.alpha = 0f
            hint.translationY = dp(8).toFloat()
        }
        hint.animate().alpha(1f).translationY(0f).setDuration(ENTER_MILLIS).setInterpolator(OvershootInterpolator(ENTER_OVERSHOOT)).start()
    }

    private fun hideHint() {
        hint.animate().cancel()
        hint.visibility = View.GONE
    }

    private fun offerFor(step: GuideStep) {
        val missing = flow.missing
        when {
            step.offersShare && Contributions.unanswered(this) -> showOffer(SHARE_OFFER)
            step.chapter == GuideChapter.End -> offer.visibility = View.GONE
            step.pause -> showOffer("Go type something")
            !ready -> showOffer("Pick TitanKeys")
            missing != null && (!missing.optional || helpShown) -> showOffer(missing.offer)
            else -> offer.visibility = View.GONE
        }
    }

    private fun showOffer(text: String) {
        offer.text = text
        offer.visibility = View.VISIBLE
        offer.alpha = 1f
    }

    private fun offerPressed() {
        setupPart?.let {
            if (!setupChanging) openPart(it)
            return
        }
        val step = flow.step
        when {
            // The app shows the full disclosure, with "I agree" and "No thanks". Nothing is shared before that.
            step.offersShare && Contributions.unanswered(this) -> startActivity(Connected.pageIntent(this, Contributions.PAGE))
            step.pause -> leave()
            !ready -> input.showInputMethodPicker()
            else -> flow.missing?.let { need ->
                writeNeed(need)
                // The keyboard reads its settings when the field starts.
                input.restartInput(field)
                offerFor(step)
                if (need == GuideNeed.FnAsCtrl) hideHint()
                backOnTrack()
            }
        }
    }

    /** The paste slide's box, for going on without fn as Ctrl: ticked, the slide counts as passed. */
    private fun offerDecline() {
        keepHint = true
        showHint(DECLINE_OFF)
        keepHint = false
        hint.setOnClickListener {
            pasteDeclined = !pasteDeclined
            hint.text = if (pasteDeclined) DECLINE_ON else DECLINE_OFF
            if (pasteDeclined && !passed) {
                passed = true
                next.text = "Next"
                paintNext()
            }
        }
    }

    private fun writeNeed(need: GuideNeed) = when (need) {
        GuideNeed.HoldSymbols -> TypingSettings.holdSymbols(this, true)
        GuideNeed.CursorControl -> TypingSettings.cursorControl(this, true)
        GuideNeed.SwipeTyping -> TypingSettings.swipeTyping(this, true)
        GuideNeed.FnAsCtrl -> TypingSettings.fnAsCtrl(this, true)
    }

    private fun readSettings() = GuideSettings(
        holdSymbols = TypingSettings.holdSymbols(this),
        cursorControl = TypingSettings.cursorControl(this),
        swipeTyping = TypingSettings.swipeTyping(this),
        fnAsCtrl = TypingSettings.fnAsCtrl(this),
        panelOpen = panelOpen,
        shareUnanswered = Contributions.unanswered(this),
    )

    /**
     * Decides between setup and the story. Setup shows before the story while any part is missing, and again during the story if
     * the keyboard stops being TitanKeys, as the story needs it. A part that is done moves setup on.
     */
    private fun checkKeyboard() {
        if (setupChanging) return
        val missing = PhoneSetup.missing(this)
        val shown = setupPart
        if (shown != null && shown !in missing) {
            // The part on the screen is done, by the guide's page or by hand.
            partDone(missing.firstOrNull())
            return
        }
        if (missing.isNotEmpty() && (!started || shown != null || SetupPart.Enable in missing || SetupPart.Choose in missing)) {
            if (missing.first() != shown) setUp(missing.first(), auto = false)
            return
        }
        if (keyboardChecked && ready) return
        keyboardChecked = true
        ready = true
        if (!started) begin() else reveal(flow.step)
    }

    /** Shows a part of setup, with the button that opens its page. [auto] opens the page by itself after a short beat. */
    private fun setUp(part: SetupPart, auto: Boolean) {
        if (setupPart == null) {
            ready = false
            keyboardChecked = false
            busy = true
            main.removeCallbacks(startDemo)
            endDemo()
            main.removeCallbacks(showHelp)
            keyboardShown(false)
            for (view in listOf(field, keys, script, next, previous, again, segments)) {
                view.animate().cancel()
                view.alpha = 0f
                view.visibility = View.INVISIBLE
            }
            for (view in listOf(chapter, close)) view.animate().alpha(1f).setStartDelay(0).setDuration(FADE_MILLIS).start()
        }
        setupPart = part
        guideLook(false)
        unlockKeyboard()
        chapter.text = SetupPart.LABEL
        hideHint()
        swapIn(title, part.title, 0)
        swapIn(body, part.body, ENTER_STAGGER_MILLIS)
        showOffer(part.action)
        main.removeCallbacks(openAuto)
        autoPart = part.takeIf { auto }
        if (auto && visible) main.postDelayed(openAuto, SETUP_AUTO_MILLIS)
    }

    private fun openPart(part: SetupPart) {
        main.removeCallbacks(openAuto)
        autoPart = null
        hideHint()
        // Google Play asks for its own disclosure before Accessibility opens. "No thanks" lets setup go on without it.
        if (part == SetupPart.Back && !BackDisclosureActivity.agreed(this)) {
            @Suppress("DEPRECATION")
            startActivityForResult(BackDisclosureActivity.forGuide(this), DISCLOSURE_REQUEST)
            return
        }
        val page = PhoneSetup.page(this, part)
        if (page == null) {
            PhoneSetup.chooseKeyboard(this)
            return
        }
        try {
            startActivityForResult(page, SETUP_REQUEST)
        } catch (error: RuntimeException) {
            showHint(SETUP_NO_PAGE)
            return
        }
        openedPart = part
        awaitingAccessibility = part == SetupPart.Back
        main.removeCallbacks(watchSetup)
        main.postDelayed(watchSetup, SETUP_POLL_MILLIS)
    }

    /** A part of setup is done: a pulse and a line, then the [next] part, which opens by itself. After the last, the story starts. */
    private fun partDone(next: SetupPart?) {
        setupChanging = true
        main.removeCallbacks(openAuto)
        autoPart = null
        hideHint()
        offer.visibility = View.GONE
        haptics.celebrate()
        overlay.sweep()
        showReaction(if (next == null) SetupPart.ALL_DONE else SetupPart.DONE)
        main.postDelayed({
            setupChanging = false
            if (next != null) setUp(next, auto = true) else setupFinished()
        }, if (next == null) SETUP_FINISH_MILLIS else SETUP_BEAT_MILLIS)
    }

    @Deprecated("Android activity result callback")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != DISCLOSURE_REQUEST) return
        // After "Agree" Accessibility opens as before. After "No thanks" the part is no longer missing, and setup moves on.
        if (resultCode == RESULT_OK && setupPart == SetupPart.Back) openPart(SetupPart.Back) else checkKeyboard()
    }

    private fun setupFinished() {
        setupPart = null
        title.animate().cancel()
        title.scaleX = 1f
        title.scaleY = 1f
        for (view in listOf(field, keys, script, segments)) view.visibility = View.VISIBLE
        again.visibility = View.GONE
        keyboardChecked = true
        ready = true
        keyboardShown(true)
        if (!started) begin() else reveal(flow.step)
    }

    /** The keyboard stays up for the story and the playground, and stays down during setup. */
    private fun keyboardShown(shown: Boolean) {
        val state = if (shown) WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE else WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        window.setSoftInputMode(state or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (shown) {
            field.requestFocus()
            input.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        } else {
            field.clearFocus()
            input.hideSoftInputFromWindow(field.windowToken, 0)
        }
    }

    /** The playground: the name, a line to follow if you like, the field and the keys. Nothing is checked, and nothing waits. */
    private fun showPlayground() {
        guideLook(false)
        unlockKeyboard()
        busy = true
        passed = false
        keys.appear()
        // The bar keeps its room, so that Guide and Settings stay at the right.
        segments.visibility = View.INVISIBLE
        for (view in listOf(next, previous, again, close, offer)) view.visibility = View.GONE
        hideHint()
        guideAction.visibility = View.VISIBLE
        settingsAction.visibility = View.VISIBLE
        // The name below says it all: the header has only its two buttons, on the right.
        chapter.text = ""
        // The playground's own text, made for speed, in a new order each time.
        startPassage(PlaygroundText.make(kotlin.random.Random(SystemClock.uptimeMillis())))
        swapIn(title, PLAYGROUND_TITLE, 0)
        swapIn(body, PLAYGROUND_BODY, ENTER_STAGGER_MILLIS)
        for (view in listOf(field, keys, chapter, script, guideAction, settingsAction))
            view.animate().alpha(1f).setStartDelay(0).setDuration(FADE_MILLIS).start()
        showScript()
        field.requestFocus()
    }

    /** The playground's Guide and Settings buttons: the app opens that page, and the playground opens again when you come back. */
    private fun leavePlayground(action: String) {
        playgroundExit?.invoke(action)
        playgroundExit = null
        finish()
    }

    /** Sends for the guide when a Settings page opened in a task of its own, which closing it from here cannot reach. */
    private fun bringBack() {
        try {
            startActivity(Intent(this, GuideActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (error: RuntimeException) {
            // Android can refuse this from the background. Then the guide waits until you come back to it.
        }
    }

    private fun fieldChanged() {
        if (!::field.isInitialized || !::script.isInitialized) return
        showScript()
        if (seeding) return
        if (!playground && ready && !changing && resetField()) return
        val start = min(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        val end = max(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        if (!playground) aim()
        // The cursor coming into the steer slide's word is felt, once each time.
        val entered = steerAim?.let { start == end && start in it.first..it.last + 1 } == true
        if (entered && !inSteerAim && flow.step.id == "steer" && !passed) haptics.play(Haptic.SelectionWord)
        inSteerAim = entered
        handle(KeyboardEvent.FieldChanged(field.text.toString(), start, end))
    }

    /**
     * Marks in orange what the slide aims at, in the field, and tells the slide (design v4): the stray letter on the Backspace slide,
     * the last word on the flick slide, a word on the other line on the steer slide.
     * Nothing on other slides, or once the guide's look is off.
     */
    private fun aim() {
        if (!::field.isInitialized) return
        val text = field.text.toString()
        val step = flow.step
        val range = if (playground || guideLooks != true) null else when (step.id) {
            "backspace" -> if (text.trimEnd() == BACKSPACE_SEED) text.trimEnd().length - 1..text.trimEnd().length - 1 else null
            "flick" -> if (passed) null else flickTarget(text)
            "hold" -> if (passed) null else holdTarget(text, field.selectionStart)
            "steer", "selectmore" -> steerAim
            else -> null
        }
        // A symbol slide's gap, while it's empty: where the cursor waits for the symbol.
        field.gapSymbol = step.symbol.orEmpty()
        field.gap = if (playground || guideLooks != true || passed || step.symbol == null || step.line.isEmpty() ||
            text.trimEnd() != gapSeed(step.line, step.symbol).trimEnd()) -1 else gapCursor(step.line, step.symbol)
        field.target = range
        flow.aim(range)
    }

    /**
     * The delete chapter's field, emptied, comes back with more to delete, and words to match. The steering line, deleted, comes
     * back as the reset line. The steering line also keeps the cursor far from its ends, so it never reaches one. True if it reset.
     */
    private fun resetField(): Boolean {
        val step = flow.step
        val text = field.text.toString()
        when (step.chapter) {
            GuideChapter.Delete -> if (text.isBlank()) {
                val heading = DELETE_RESETS[deleteResets.coerceAtMost(DELETE_RESETS.lastIndex)]
                deleteResets++
                seeding = true
                field.setText(step.seed + " ")
                field.setSelection(step.cursorAt?.invoke(field.text.toString())?.coerceIn(0, field.length()) ?: field.length())
                seeding = false
                flow.seed(field.text.toString(), field.selectionStart)
                if (!passed) showScenario(heading, flow.instruction)
                return true
            }
            GuideChapter.Steering -> {
                if (steerLength > 0 && text.length < steerLength - STEER_DELETE_LEEWAY) {
                    seeding = true
                    seedSteering(step.seed)
                    seeding = false
                    flow.seed(field.text.toString())
                    if (!passed) showScenario(STEER_RESET, flow.instruction)
                    return true
                }
                recentre()
            }
            else -> Unit
        }
        return false
    }

    /** Puts [line] in the field again and again, with the cursor in the middle after a space. */
    private fun seedSteering(line: String) {
        // The lines finished so far, then the slide's own: text to steer through that reads as the user's own, and never the
        // same line again and again (owner, v5).
        val before = GUIDE_STEPS.takeWhile { it.id != flow.step.id }.map { it.line }.filter { it.isNotEmpty() }.takeLast(STEER_LINES_BEFORE)
        val text = (before + line).joinToString("\n") + " "
        field.setText(text)
        steerLength = text.length
        val middle = text.length / 2
        // The cursor starts at the end of the slide's line; the word to steer to is on a line above.
        field.setSelection(text.length - 1)
        steerAim = null
        inSteerAim = false
        if (flow.step.id == "steer") field.post { pickSteerAim() }
        // Grab a bunch: its words are marked, and the cursor waits right before them, their line at the top of the field.
        if (flow.step.id == "selectmore") grabTarget(text, middle)?.let { words ->
            steerAim = words
            field.setSelection(words.first)
            field.post {
                val own = field.layout ?: return@post
                field.scrollTo(0, own.getLineTop(own.getLineForOffset(words.first)))
                aim()
            }
        }
    }

    /**
     * The steer slide's word, once the field has its lines: on the visible line that doesn't hold the cursor, a few words from its
     * column, so both stay in the two lines (design v4). The field shows those two lines.
     */
    private fun pickSteerAim() {
        if (flow.step.id != "steer" || playground) return
        val own = field.layout ?: return
        val cursor = field.selectionStart.coerceAtLeast(0)
        val cursorLine = own.getLineForOffset(cursor)
        val other = if (cursorLine + 1 < own.lineCount) cursorLine + 1 else cursorLine - 1
        if (other < 0) return
        val text = field.text.toString()
        steerAim = steerTarget(text, own.getLineStart(other) until own.getLineEnd(other), own.getPrimaryHorizontal(cursor)) {
            own.getPrimaryHorizontal(it)
        }
        field.scrollTo(0, own.getLineTop(min(cursorLine, other)))
        inSteerAim = false
        aim()
    }

    /** The cursor near either end of the steering line moves by a whole line, to the same place in a copy nearer the middle. */
    private fun recentre() {
        // The steering text no longer loops (v5), so there's no copy to move to.
        if (!flow.step.seed.isEmpty()) return
        val start = field.selectionStart
        val end = field.selectionEnd
        if (start < 0 || end < 0) return
        val text = field.text.toString()
        // The copies are joined by a space, so one time round is the line and its space.
        val period = steerPeriod(text) + 1
        val length = text.length
        if (period * 2 >= length) return
        val shift = when {
            start < period -> period * (STEER_LOOPS / 2)
            end > length - period -> -period * (STEER_LOOPS / 2)
            else -> return
        }
        if (start + shift < 0 || end + shift > length) return
        // The marked word moves with the cursor, to the same word in its copy of the line.
        steerAim = steerAim?.let { (it.first + shift)..(it.last + shift) }
        field.setSelection(start + shift, end + shift)
    }

    /** The length of one time round the steering line in [text]. */
    private fun steerPeriod(text: String): Int {
        return flow.step.seed.length
    }

    /** A touch on the field, on a slide about doing that from the keys. */
    private fun fieldTouched() {
        if (playground || passed || busy || flow.step.chapter != GuideChapter.Steering) return
        showScenario(SCREEN_TOUCHED, flow.instruction)
    }

    private fun trace(message: JSONObject) {
        // A finger on the touchpad is someone there: no idle chip, and the demo waits.
        if (!playground && ::phantom.isInitialized) acted()
        val path = message.getJSONArray("points")
        val points = (0 until path.length()).map { index ->
            val point = path.getJSONArray(index)
            floatArrayOf(point.getDouble(0).toFloat(), point.getDouble(1).toFloat(), point.optDouble(2, 1.0).toFloat())
        }
        val reset = message.getBoolean("reset")
        val lifted = message.getString("event") == "lift"
        fingerDown = !lifted
        if (reset) {
            travel = 0f
            lastPoint = null
        }
        for (point in points) {
            lastPoint?.let { travel += hypot((point[0] - it[0]) * 10, (point[1] - it[1]) * 4) }
            lastPoint = point
        }
        keys.trace(points, reset, lifted)
        waitForHelp()
        if (lifted) freezeRegrets()
        acted()
        if (!busy && ready) judge(KeyboardEvent.Moved(travel, lifted))
    }

    // The playground's speed: while you type, the name turns into words a minute, the screen warms towards orange, and from 40
    // words a minute the playground comments on it and puts on a show: see [WpmComments] and [WpmEffects]. Five seconds with
    // nothing, and the number counts back down to nothing, and the show winds down with it.

    private val wpm = WpmMeter()
    /** The speed that shows: it counts up and down towards the real one, so that nothing ever jumps or cuts off. */
    private var shownWpm = 0f
    private var showingWpm = false
    /** The comment's step that shows, from [WpmComments.STEPS], or -1 for none. */
    private var commentStep = -1
    /** When that comment came, for [WpmMeter.COMMENT_MIN_MILLIS]. */
    private var commentAt = 0L
    private val comments by lazy { resources.getStringArray(R.array.playground_comments) }
    private val wpmTick = object : Runnable {
        override fun run() = showWpm()
    }

    private fun countWpm(event: KeyboardEvent) {
        val now = SystemClock.uptimeMillis()
        when (event) {
            // Shift, Alt, Sym, fn and Ctrl type nothing, so they don't count.
            is KeyboardEvent.Pushed -> if (WpmMeter.counts(event.code)) wpm.input(now, field.length()) else return
            is KeyboardEvent.Tapped, is KeyboardEvent.Recognised, is KeyboardEvent.DeletedWord,
            is KeyboardEvent.Chosen -> wpm.input(now, field.length())
            is KeyboardEvent.FieldChanged -> wpm.text(event.text.length)
            else -> return
        }
        if (wpm.active(now) && !showingWpm) showWpm()
    }

    private fun showWpm() {
        main.removeCallbacks(wpmTick)
        val now = SystemClock.uptimeMillis()
        val speed = wpm.wpm(now)
        val target = (speed ?: 0).toFloat()
        shownWpm = when {
            !GuideMotion.enabled -> target
            target > shownWpm -> shownWpm + (target - shownWpm) * WPM_RISE
            // Down, it counts at a steady pace at least, so that it reaches nothing in a few seconds, not in a long tail.
            else -> max(target, shownWpm - max((shownWpm - target) * WPM_FALL, WPM_FALL_LEAST))
        }
        val shown = Math.round(shownWpm)
        val heat = WpmMeter.heat(shown)
        if (speed != null || shown > 0) {
            showingWpm = true
            title.text = SpannableStringBuilder("$shown").apply {
                val start = length
                append(getString(R.string.playground_wpm))
                setSpan(RelativeSizeSpan(WPM_LABEL_SIZE), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else if (showingWpm) {
            showingWpm = false
            title.text = PLAYGROUND_TITLE
        }
        showComment(WpmComments.step(shown))
        backdrop.show(GuideMotion.blend(backgroundOf(tone), GuideColours.ORANGE, heat * WPM_MOST_ORANGE))
        title.post { effects.update(WpmEffects.at(shown), commentStep, numberBounds("$shown")) }
        // It stops ticking once the burst is over and the number is back to nothing.
        if (speed != null || shown > 0) main.postDelayed(wpmTick, WPM_TICK_MILLIS)
        else {
            // The number is back to nothing, so the playground's own line returns, however long the last comment has shown.
            showComment(-1, force = true)
            shownWpm = 0f
            backdrop.show(backgroundOf(tone))
        }
    }

    /**
     * The comment for [step], in the place of the playground's line, from its step until the next one. Going up, it slides in and
     * the number gives a small pulse; counting down, it steps back quietly, and below the first step the playground's line returns.
     */
    private fun showComment(step: Int, force: Boolean = false) {
        if (step == commentStep) return
        // Each comment stays long enough to read, whichever way the speed goes next.
        val now = SystemClock.uptimeMillis()
        if (!force && commentStep >= 0 && now - commentAt < WpmMeter.COMMENT_MIN_MILLIS) return
        commentAt = now
        val rose = step > commentStep
        commentStep = step
        val line = comments.getOrNull(step) ?: PLAYGROUND_BODY
        if (rose && GuideMotion.enabled) {
            swapIn(body, line, 0)
            title.animate().cancel()
            title.scaleX = WPM_PULSE
            title.scaleY = WPM_PULSE
            title.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(WPM_PULSE_MILLIS)
                .setInterpolator(OvershootInterpolator(ENTER_OVERSHOOT)).start()
        } else body.text = line
    }

    /** Where the number is, in the show's own pixels, and the point that it pulses and wobbles around. */
    private fun numberBounds(number: String): RectF? {
        val layout = title.layout ?: return null
        val at = IntArray(2)
        val origin = IntArray(2)
        title.getLocationInWindow(at)
        effects.getLocationInWindow(origin)
        val inside = title.totalPaddingLeft + layout.getLineLeft(0)
        val baseline = title.totalPaddingTop + layout.getLineBaseline(0).toFloat()
        val wide = title.paint.measureText(number)
        title.pivotX = inside + wide / 2
        title.pivotY = baseline
        val left = at[0] - origin[0] + inside
        val top = at[1] - origin[1] + baseline
        return RectF(left, top + title.paint.ascent() * .8f, left + wide, top + title.paint.descent() * .2f)
    }

    /** The playground went away: the speed starts over, and the show stops at once, with everything back in place. */
    private fun calmWpm() {
        shownWpm = 0f
        if (showingWpm) title.text = PLAYGROUND_TITLE
        showingWpm = false
        backdrop.show(backgroundOf(tone))
        if (commentStep >= 0) body.text = PLAYGROUND_BODY
        commentStep = -1
        effects.update(WpmEffects.NONE, -1, null)
        title.rotation = 0f
        column.translationX = 0f
        column.translationY = 0f
    }

    private fun handle(event: KeyboardEvent) {
        if (playground) countWpm(event)
        // The keyboard reports its modifiers whenever it redraws them, as when a slide opens a panel. That is not you acting.
        if (::phantom.isInitialized && event !is KeyboardEvent.ModifierState) {
            acted()
            waitForHelp()
        }
        when (event) {
            is KeyboardEvent.Pushed -> keyName(event.code)?.let {
                lastKey = it
                // The right Shift is the second ⇧ of the drawing; every other key has one.
                keys.press(it, if (event.code == KeyEvent.KEYCODE_SHIFT_RIGHT) 1 else 0)
                heldKeys += event.code
            }
            is KeyboardEvent.Released -> heldKeys -= event.code
            is KeyboardEvent.Resting -> if (event.key.isNotEmpty()) {
                keys.light(event.key)
                keys.touched()
            }
            is KeyboardEvent.HeldSymbol -> lastKey?.let { keys.flip(it, event.symbol) }
            is KeyboardEvent.ModifierState -> {
                panelOpen = event.panel != Modifiers.Panel.Closed
                // The drawing of the keys never covers the real keyboard or its panels (owner, v5.2): it hides while a panel
                // (Sym, emoji or clipboard) is open, and comes back as it closes. The panel shows the same keys itself.
                keys.visibility = if (keys.layoutParams.height > 0 && !atEnd() && !panelOpen && !panelRoom) View.VISIBLE else View.GONE
                // The panel's room is the keyboard's only while it's open: closed, the slide gets it back (owner, v5: slide 12).
                root.requestApplyInsets()
                altOn = event.alt != Modifiers.Level.Off
                modifiersOff = !panelOpen && event.alt == Modifiers.Level.Off
                if (modifiersOff) modifiersWentOff()
            }
            // The drawing of the keys doesn't copy the panel: the real panel is on the screen. See ModifierState above.
            is KeyboardEvent.PanelFace -> Unit
            // On the slide about the bar, every swipe comes out wrong, so that there is always something to fix with one tap.
            else -> Unit
        }
        if (!playground && ready && !changing && !settingUp) watch(event)
        // Mass deletion, passed: the count goes on with every word, under the success line, until the finger lifts. Before the
        // check, so the word that passes it isn't counted twice.
        if (event is KeyboardEvent.DeletedWord && passed && !playground && flow.step.id == "hold" && !regretsFrozen && regrets > 0) {
            regrets++
            settledPraise = regretsLine(regrets)
            body.text = settledPraise
        }
        if (!busy && ready) judge(event)
    }

    /** The finger lifted on mass deletion, passed: the count stops on its last number. */
    private fun freezeRegrets() {
        if (!passed || playground || flow.step.id != "hold" || regretsFrozen || regrets < REGRETS_TO_PASS) return
        regretsFrozen = true
        settledPraise = regretsFinal(regrets)
        body.text = settledPraise
    }

    /** Seconds since the clown hunt began. */
    private fun huntSeconds() = ((SystemClock.uptimeMillis() - huntStart) / 1000L).toInt()

    /** The clown hunt begins once the emoji are open: the bar drains, silent, and the time is kept. It never fails. */
    private fun startHunt() {
        if (huntStart >= 0 || passed || playground || flow.step.hunt == null || flow.stage != 1) return
        huntStart = SystemClock.uptimeMillis()
        huntSaid = 0
        huntBar.animate().cancel()
        huntBar.scaleX = 1f
        huntBar.visibility = View.VISIBLE
        huntBar.animate().scaleX(0f).setDuration(HUNT_DRAIN_SECONDS * 1000L).setInterpolator(android.view.animation.LinearInterpolator()).start()
        main.postDelayed(huntTick, 1000L)
    }

    private fun stopHunt() {
        huntStart = -1L
        main.removeCallbacks(huntTick)
        if (!::huntBar.isInitialized) return
        huntBar.animate().cancel()
        huntBar.visibility = View.GONE
    }

    /** A line the slide shows in place of its instruction while it's tried, such as a count. See [GuideStep.live]. */
    private fun showLive() {
        if (passed || playground) return
        val line = flow.liveLine() ?: return
        if (body.text.toString() != line) body.text = line
    }

    /**
     * What the screen itself watches for, beyond a slide's check: a fix undone after the typing slide passed, fn and V that changed
     * nothing, and the bar emptying on the word menu slide.
     */
    private fun watch(event: KeyboardEvent) {
        val step = flow.step
        // Something the slide doesn't teach yet did nothing at all: the chip says so once a slide, or for the bar on the first slide.
        if (event is KeyboardEvent.Blocked) {
            // Nothing is blocked silently: each try says so, and the phone says no. A chip that shows already isn't said twice.
            haptics.play(Haptic.GuideBlocked)
            showChip(blockedChip(step, event.lesson, flow.lessons), force = true)
        }
        // A rude clip pasted on the clipboard slide is taken back out at once, and the slide says so: only the nice one counts.
        if (event is KeyboardEvent.FieldChanged && step.id == "clipboard" && !passed && flow.stage == 2 &&
            event.text.trimEnd() != step.seed && PRANK_PASTE !in event.text && event.text.length > step.seed.length + 2) {
            seeding = true
            field.setText(step.seed + " ")
            field.setSelection(field.length())
            seeding = false
            flow.seed(field.text.toString(), field.selectionStart)
            haptics.play(Haptic.GuideBlocked)
            showChip(RUDE_CLIP, force = true)
        }
        // The held delete only ever takes the regrets (owner, v5.3: it went on 1.1 s past the lift and ate the line's first word).
        // Once they're gone, deleting is locked for the slide, and a word of the line that goes anyway comes straight back.
        if (event is KeyboardEvent.FieldChanged && step.id == "hold") {
            if (holdTarget(event.text, event.start) == null) GuideHooks.lessons = emptySet()
            if (!event.text.startsWith(HOLD_LEAD)) {
                val tail = event.text.substring(event.start.coerceIn(0, event.text.length)).trimStart()
                val mended = HOLD_LEAD + tail.removePrefix(HOLD_LEAD)
                main.post {
                    if (flow.step !== step || field.text.toString() == mended) return@post
                    seeding = true
                    field.setText(mended)
                    field.setSelection(HOLD_LEAD.length.coerceAtMost(field.length()))
                    seeding = false
                    flow.seed(field.text.toString(), field.selectionStart)
                }
            }
        }
        // A wrong symbol in a symbol slide's gap is taken back out at once, so the right one passes on its own (owner, v5.2). The
        // slide's check says so with its chip.
        val symbol = step.symbol
        if (event is KeyboardEvent.FieldChanged && symbol != null && !passed && step.line.isNotEmpty() &&
            onlyWrongInGap(event.text, step.line, symbol)) {
            main.post {
                if (passed || flow.step !== step || !onlyWrongInGap(field.text.toString(), step.line, symbol)) return@post
                seeding = true
                field.setText(gapSeed(step.line, symbol))
                field.setSelection(gapCursor(step.line, symbol).coerceAtMost(field.length()))
                seeding = false
                flow.seed(field.text.toString(), field.selectionStart)
                haptics.play(Haptic.GuideBlocked)
            }
        }
        if (event is KeyboardEvent.Pushed && event.code == KeyEvent.KEYCODE_V && step.id == "paste" && !passed) {
            val before = field.text.toString()
            main.postDelayed({
                if (!passed && flow.step.id == "paste" && field.text.toString() == before && flow.missing == GuideNeed.FnAsCtrl)
                    showScenario(NOTHING_PASTED_TITLE, NOTHING_PASTED_BODY)
            }, NOTHING_PASTED_MILLIS)
        }
        if (step.bar == GuideBar.Menu && !passed) {
            main.removeCallbacks(checkBar)
            main.postDelayed(checkBar, BAR_CHECK_MILLIS)
        }
    }

    /** The slide is not done after all, as the user undid it: it goes back to ink, and says why. */
    private fun unpass(heading: String, text: String) {
        passed = false
        showBigNext(false)
        busy = false
        main.removeCallbacks(settleReaction)
        next.text = "Skip"
        backdrop.wipeTo(GuideColours.BLACK)
        main.postDelayed({ if (!passed) applyTone(GuideTone.Ink) }, WIPE_MILLIS)
        title.animate().cancel()
        title.scaleX = 1f
        title.scaleY = 1f
        showScenario(heading, text)
        acted()
    }

    /** The slide has a text to type along to: in its box, or as the ghost in the field. */
    private fun hasText() = !settingUp && started && flow.step.chapter == GuideChapter.Typing && flow.step.text.isNotEmpty() && guideLooks == true

    /** The letters of the word that the text box has next, or nothing on a slide without one. */
    private fun expectedWord() = if (!hasText() || playground) ""
        else GuideScript.letters(passage.wordAt(passage.track(typedSince(field.text.toString())).next))

    private fun judge(event: KeyboardEvent) {
        val verdict = flow.event(event, expectedWord())
        // The slide made the next one unneeded: the bar at the top counts one slide fewer.
        if (verdict != GuideVerdict.Wait) paintSegments(flow.stepIndex)
        when (verdict) {
            GuideVerdict.Done, GuideVerdict.Finished -> done()
            is GuideVerdict.Stage -> showStage()
            // A scenario about Alt or the panel waits a moment, to be sure that they are really still on: the keyboard reports its
            // modifiers on every redraw, and a passing state must not leave words about something that is no longer true.
            is GuideVerdict.Hint -> when {
                // A chip that comes with passing: the slide's own success line, and the chip.
                verdict.passes && verdict.title == null -> {
                    done()
                    showChip(verdict.text, force = true)
                }
                verdict.passes -> done(verdict.title, verdict.text)
                event is KeyboardEvent.ModifierState && verdict.stage == null -> {
                    main.removeCallbacks(modifierHint)
                    pendingModifierHint = verdict
                    main.postDelayed(modifierHint, MODIFIER_HINT_MILLIS)
                }
                else -> scenario(verdict)
            }
            GuideVerdict.Wait -> Unit
        }
        showLive()
        // A miss on the swipe slide: the right word, on the bar, wiggles to show where to tap.
        if (verdict is GuideVerdict.Hint && flow.step.id == "swipe" && event is KeyboardEvent.Recognised) pointAtRightWord()
    }

    /** Shows what a scenario says: in the slide's place with a title, or as a hint without one. A stage it moves to shows too. */
    private fun scenario(verdict: GuideVerdict.Hint) {
        if (verdict.stage != null) {
            body.text = flow.instruction
            GuideHooks.lessons = flow.lessons
            startHunt()
            showScript()
            if (flow.step.nearMiss && flow.stage == 1) pointAtRightWord() else GuideHooks.stripPointWord(null)
        }
        hinted(verdict.title ?: verdict.text)
    }

    /**
     * The right word dances on the bar, wherever the keyboard put it: by the word itself, once the bar has it. It's the right word
     * that moves, never a wrong one.
     */
    private fun pointAtRightWord() {
        main.postDelayed({ if (!changing) GuideHooks.stripPointWord(GuideHooks.rightWord) }, POINT_AFTER_MILLIS)
    }

    /** A scenario about Alt or the panel, waiting to be sure, and the one that shows, which goes as soon as it is no longer true. */
    private var pendingModifierHint: GuideVerdict.Hint? = null
    private var shownModifierHint: GuideVerdict.Hint? = null
    private var modifiersOff = true
    private val modifierHint = Runnable {
        val shown = pendingModifierHint ?: return@Runnable
        pendingModifierHint = null
        if (modifiersOff || busy) return@Runnable
        scenario(shown)
        shownModifierHint = shown
    }

    /** Alt and the panel are off now: a scenario about them, waiting or shown, is no longer true. */
    private fun modifiersWentOff() {
        main.removeCallbacks(modifierHint)
        pendingModifierHint = null
        val shown = shownModifierHint ?: return
        shownModifierHint = null
        if (::chip.isInitialized && chip.text.toString() == (shown.title ?: shown.text)) hideChip()
    }

    /**
     * The slide is done: on the symbols chapter the guide first types the rest of the symbol's sentence, letter by letter, and the
     * slide is done once it's in. [reaction] and [praise] replace the slide's own lines, for a scenario that counts.
     */
    private fun done(reaction: String? = null, praise: String? = null) = stepDone(reaction, praise)

    /** The slide moved on, or back, to another instruction: only that one shows, and the ghost follows it. */
    private fun showStage() {
        startHunt()
        hideHint()
        hideChip(now = true)
        scenarioShown = false
        idleShown = false
        // The instruction changes only here, when the slide moves to its next step.
        swapIn(body, flow.instruction, 0)
        startHunt()
        showScript()
        // The word came out wrong on purpose: the right word, wherever it is on the bar, dances to show where to tap.
        if (flow.step.nearMiss && flow.stage == 1) pointAtRightWord() else GuideHooks.stripPointWord(null)
        // A stage can lock more than its slide does, as the bar slide's tap does.
        GuideHooks.lessons = flow.lessons
        main.removeCallbacks(showHelp)
        if (flow.step.help != null) main.postDelayed(showHelp, if (flow.step.helpTitle != null) HELP_TITLE_AFTER_MILLIS else HELP_AFTER_MILLIS)
    }

    /**
     * The real gesture happened. The screen sweeps to orange, the phone gives a pulse, and a line lands. The step stays, so you can
     * try it more: the keys still answer, and only the checks stop. Next is filled now.
     */
    private fun stepDone(reaction: String? = null, praise: String? = null) {
        GuideHooks.stripPoint(null)
        GuideHooks.stripPointWord(null)
        hideChip(now = true)
        busy = true
        passed = true
        scenarioShown = false
        idleShown = false
        main.removeCallbacks(startDemo)
        main.removeCallbacks(idleOnce)
        main.removeCallbacks(idleTwice)
        endDemo()
        main.removeCallbacks(showHelp)
        hideHint()
        hint.setOnClickListener(null)
        // The pass beat, all at once: the title becomes the success line with a pop and a check that draws in, the chapter steps
        // forward with a glow, Skip turns into a filled Next with one pulse, and the phone celebrates. Nothing else moves.
        haptics.celebrate()
        overlay.wash(GuideColours.ORANGE)
        morphNext()
        showBigNext(true)
        val step = flow.step
        markPassed(step)
        showSuccess(reaction ?: step.done)
        val hunted = if (step.hunt != null && huntStart >= 0) huntSeconds() else -1
        stopHunt()
        // A hunt's roast goes by how long it took (owner, v5.2): a quick find gets a chip, a slow one its line under the success,
        // where it has room.
        val hunt = step.hunt
        val quick = hunt != null && hunted >= 0 && huntFast(hunted)
        val said = praise ?: when {
            hunt != null && hunted >= 0 && !quick -> hunt.slow(hunted)
            step.id == "symgroups" -> groupsPraise(flow.counted)
            // The count carries on under the success line, until the finger lifts.
            step.id == "hold" -> {
                regrets = flow.tally
                regretsLine(regrets)
            }
            else -> step.praise
        }
        if (said.isNotEmpty()) body.text = said
        if (quick) showChip(hunt!!.fast, force = true)
        // A symbol slide passed with a wrong one still in its gap reads as its line, clean (owner, v5.2).
        val symbol = step.symbol
        if (symbol != null && step.line.isNotEmpty() && field.text.toString().trim() != step.line &&
            symbolInGap(field.text.toString(), step.line, symbol)) {
            seeding = true
            field.setText(step.line + " ")
            field.setSelection(field.length())
            seeding = false
        }
        paintSegments(flow.stepIndex, advancing = true)
        settledPraise = said
        step.rain?.let { effects.burst(it, RAIN_PIECES) }
        // Done in a way that does the next slide too: it opens, done.
        if (flow.jumps) main.postDelayed({
            if (!passed || changing || flow.step !== step) return@postDelayed
            arriveDone = true
            moveOn()
        }, JUMP_MILLIS)
        if (flow.step.text.isNotEmpty()) continuePassage(flow.step.after)
        // What the paste lesson put on the clipboard has done its job: the bar stops offering it.
        if (flow.step.clip != null) GuideHooks.dismissClip()
        showScript()
        main.removeCallbacks(settleReaction)
    }

    /**
     * The success line, in the title's place until Next: the old title fades, the new one comes in a little big and settles, and
     * then its orange check draws itself in.
     */
    private fun showSuccess(text: String) {
        val check = CheckSpan(GuideColours.ORANGE).apply { progress = if (GuideMotion.enabled) 0f else 1f }
        val line = SpannableStringBuilder(text).apply {
            append(" ")
            val start = length
            append("✓")
            setSpan(check, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        title.animate().cancel()
        title.translationX = 0f
        if (!GuideMotion.enabled) {
            title.text = line
            title.alpha = 1f
            title.scaleX = 1f
            title.scaleY = 1f
            return
        }
        title.animate().alpha(0f).setStartDelay(0).setDuration(SUCCESS_OUT_MILLIS).withEndAction {
            title.text = line
            title.pivotX = 0f
            title.pivotY = title.height / 2f
            title.scaleX = SUCCESS_POP
            title.scaleY = SUCCESS_POP
            title.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(SUCCESS_IN_MILLIS)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).withEndAction {
                    ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = CHECK_MILLIS
                        addUpdateListener {
                            check.progress = it.animatedFraction
                            title.invalidate()
                        }
                        start()
                    }
                }.start()
        }.start()
    }

    /**
     * The calm done state: Next is a big orange button, full width above the keys, breathing gently until it's pressed. The small
     * pill at the top steps aside meanwhile, so there's one Next.
     */
    private fun showBigNext(show: Boolean) {
        nextPulse?.cancel()
        nextPulse = null
        nextBig.scaleX = 1f
        nextBig.scaleY = 1f
        if (!show || flow.step.chapter == GuideChapter.End) {
            nextBig.visibility = if (flow.step.chapter == GuideChapter.End || guideLooks != true) View.GONE else View.INVISIBLE
            next.visibility = if (flow.step.chapter == GuideChapter.End) next.visibility else View.VISIBLE
            return
        }
        offer.visibility = View.GONE
        next.visibility = View.INVISIBLE
        nextBig.visibility = View.VISIBLE
        nextBig.alpha = 0f
        nextBig.animate().alpha(1f).setStartDelay(WASH_SETTLE_MILLIS).setDuration(ENTER_MILLIS).start()
        if (!GuideMotion.enabled) return
        nextPulse = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = NEXT_BREATH_MILLIS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener {
                val scale = 1f + NEXT_BREATH * it.animatedFraction
                nextBig.scaleX = scale
                nextBig.scaleY = scale
            }
            start()
        }
    }

    /** Skip turns into a filled orange Next: the outline fills in, the words turn dark, and the pill gives one pulse. */
    private fun morphNext() {
        next.text = "Next"
        if (!GuideMotion.enabled || tone != GuideTone.Ink) {
            paintNext()
            return
        }
        val strong = GuideColours.CREAM
        val pill = GradientDrawable().apply { cornerRadius = Radii.px(resources, OFFER_RADIUS) }
        next.background = pill
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MORPH_MILLIS
            addUpdateListener {
                val t = it.animatedFraction
                pill.setColor(GuideMotion.faded(GuideColours.ORANGE, t))
                pill.setStroke(dp(1).coerceAtLeast(2), GuideMotion.blend(GuideMotion.faded(strong, NEXT_WAITING), GuideColours.ORANGE, t))
                next.setTextColor(GuideMotion.blend(GuideMotion.faded(strong, NEXT_WAITING), GuideColours.INK, t))
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    paintNext()
                    next.animate().cancel()
                    next.animate().scaleX(NEXT_PULSE).scaleY(NEXT_PULSE).setDuration(PULSE_MILLIS / 2).withEndAction {
                        next.animate().scaleX(1f).scaleY(1f).setDuration(PULSE_MILLIS / 2).start()
                    }.start()
                }
            })
            start()
        }
    }

    private fun nextPressed() {
        if (changing || !ready) return
        haptics.play(Haptic.GuideNext)
        // At the end, the small pill at the top is the boring guide; the big button below goes to type.
        if (flow.finished) {
            preferences.edit().remove(RESUME).apply()
            startActivity(Connected.pageIntent(this, BORING_GUIDE))
            finish()
            return
        }
        if (!passed) {
            // Skipping: the roast shows for a moment as the slide changes.
            changing = true
            busy = true
            autoTyping = false
            main.removeCallbacks(startDemo)
            main.removeCallbacks(idleOnce)
            main.removeCallbacks(idleTwice)
            endDemo()
            hideHint()
            hideChip(now = true)
            showChip(SKIP_ROAST, force = true)
            main.postDelayed({
                changing = false
                moveOn()
            }, SKIP_MILLIS)
            return
        }
        moveOn()
    }

    private fun backPressed() {
        if (changing || !ready || !flow.canGoBack) return
        arrivalTitle = if (flow.step.chapter == GuideChapter.End) BACK_FROM_END else GOING_BACK
        arrivalBody = null
        moveOn(forward = false)
    }

    /**
     * The line that lands takes the title's place for a moment, with a small pop from its bottom left corner. It is in the title's
     * own box, so it fits there at any font size and covers nothing.
     */
    private fun showReaction(text: String) {
        title.animate().cancel()
        title.text = text
        title.translationX = 0f
        title.pivotX = 0f
        title.pivotY = title.height.toFloat()
        title.alpha = 0f
        title.scaleX = REACTION_FROM
        title.scaleY = REACTION_FROM
        title.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(REACTION_IN_MILLIS)
            .setInterpolator(OvershootInterpolator(REACTION_OVERSHOOT)).start()
        body.animate().alpha(REACTION_DIM).setDuration(FADE_MILLIS).start()
    }

    /** The story goes on, or back a step: to ink for a step behind the cream line, or orange for the ending. */
    private fun moveOn(forward: Boolean = true) {
        showBigNext(false)
        GuideHooks.closeMenu()
        GuideHooks.stripDemo(null)
        if (field.selectionStart != field.selectionEnd) {
            seeding = true
            field.setSelection(field.selectionEnd)
            seeding = false
        }
        if (flow.step.clip != null) GuideHooks.dismissClip()
        GuideHooks.stripPoint(null)
        changing = true
        busy = true
        passed = false
        main.removeCallbacks(startDemo)
        endDemo()
        again.visibility = View.GONE
        main.removeCallbacks(showHelp)
        main.removeCallbacks(settleReaction)
        main.removeCallbacks(idleOnce)
        main.removeCallbacks(idleTwice)
        autoTyping = false
        hideHint()
        hideChip(now = true)
        val leaving = flow.step
        if (forward) flow.advance() else flow.back()
        val coming = flow.step
        val target = if (coming.chapter == GuideChapter.End) GuideTone.Orange else GuideTone.Ink
        val away = (if (forward) -dp(ENTER_OFFSET) else dp(ENTER_OFFSET)).toFloat()
        for (view in listOf(title, body)) view.animate().alpha(0f).translationX(away).setDuration(FADE_MILLIS).start()
        val newChapter = coming.chapter != leaving.chapter
        val arrive: () -> Unit = {
            if (target != tone) backdrop.wipeTo(backgroundOf(target))
            // The stroke crosses the screen only on a change of chapter. Between slides the screen just fades.
            if (newChapter) overlay.sweep(thickness = LINE)
            main.postDelayed({
                changing = false
                applyTone(target)
                if (coming.chapter == GuideChapter.End) finale(coming) else enter(coming)
            }, WIPE_MILLIS)
        }
        // No full-screen roasts: a chapter's last slide says it in its success line.
        arrive()
    }

    /** What happens after a chapter's roast, once it's over or touched away. */
    private var afterRoast: (() -> Unit)? = null
    private val roastOver = Runnable { endRoast() }

    /**
     * A chapter's roast, over the whole screen: the words big in the middle, with the playground's show behind them, bigger each
     * chapter. A touch ends it early. Then [then] goes on to the next chapter.
     */
    private fun showRoast(text: String, chapter: GuideChapter, then: () -> Unit) {
        afterRoast = then
        val level = GuideChapter.entries.indexOf(chapter).coerceIn(0, ROAST_SPEEDS.lastIndex)
        keyboardShown(false)
        column.animate().cancel()
        column.animate().alpha(0f).setDuration(FADE_MILLIS).start()
        backdrop.wipeTo(GuideColours.ORANGE)
        overlay.sweep(thickness = LINE)
        roastView.setTextColor(GuideColours.INK)
        roastView.text = text
        roastView.visibility = View.VISIBLE
        roastView.alpha = 0f
        roastView.scaleX = REACTION_FROM
        roastView.scaleY = REACTION_FROM
        roastView.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(WIPE_MILLIS).setDuration(REACTION_IN_MILLIS)
            .setInterpolator(OvershootInterpolator(REACTION_OVERSHOOT)).start()
        haptics.celebrate()
        val speed = ROAST_SPEEDS[level]
        effects.update(WpmEffects.at(speed), WpmComments.step(speed), RectF(0f, root.height * .4f, root.width.toFloat(), root.height * .6f))
        repeat(level + 1) { main.postDelayed({ effects.confetti() }, it * ROAST_CONFETTI_GAP_MILLIS) }
        main.removeCallbacks(roastOver)
        main.postDelayed(roastOver, ROAST_MILLIS + level * ROAST_MORE_MILLIS)
    }

    private fun endRoast() {
        val then = afterRoast ?: return
        afterRoast = null
        main.removeCallbacks(roastOver)
        effects.update(WpmEffects.NONE, -1, null)
        roastView.animate().cancel()
        roastView.animate().alpha(0f).setStartDelay(0).setDuration(FADE_MILLIS).withEndAction { roastView.visibility = View.GONE }.start()
        column.animate().alpha(1f).setDuration(FADE_MILLIS).start()
        keyboardShown(true)
        then()
    }

    // Progress, kept across restarts: each slide passed, and once every chapter is finished, chapters to jump to.

    private fun passedIds(): Set<String> = preferences.getStringSet(PASSED, emptySet()).orEmpty()

    private fun markPassed(step: GuideStep) {
        if (step.chapter == GuideChapter.End) return
        preferences.edit().putStringSet(PASSED, passedIds() + step.id).apply()
    }

    private fun markAllFinished() = preferences.edit().putBoolean(ALL_FINISHED, true).apply()

    /** Every chapter is finished, so the bar at the top jumps to any of them. */
    private val canJump get() = preferences.getBoolean(ALL_FINISHED, false)

    private fun chapterTouched(index: Int) {
        // Forward only (owner, v5): a chapter can't be picked. Start again starts from the top.
        if (index >= 0 || !canJump || changing || !ready || playground || settingUp) return
        val chapter = flow.barChapters.getOrNull(index) ?: return
        val first = flow.idsOf(chapter).firstOrNull() ?: return
        changing = true
        busy = true
        passed = false
        main.removeCallbacks(startDemo)
        endDemo()
        hideHint()
        flow.start(first)
        enteredChapter = null
        keyboardShown(true)
        for (view in listOf(field, phantom, keys, script)) {
            view.visibility = View.VISIBLE
            view.animate().alpha(1f).setDuration(FADE_MILLIS).start()
        }
        for (view in listOf(title, body)) view.animate().alpha(0f).setDuration(FADE_MILLIS).start()
        backdrop.wipeTo(backgroundOf(GuideTone.Ink))
        overlay.sweep(thickness = LINE)
        main.postDelayed({
            changing = false
            applyTone(GuideTone.Ink)
            enter(flow.step)
        }, WIPE_MILLIS)
    }

    /** Leaving before the end asks first. Coming back after that gets a line. */
    private fun confirmLeave() {
        if (playground || settingUp || flow.finished || !ready) {
            leave()
            return
        }
        showLeavePopup()
    }

    /** The leaving pop-up while it's open. */
    private var leavePopup: View? = null

    /**
     * "Leaving already?" in the guide's own look, not Android's dialog (owner, v5.2): a cream card over the dimmed slide, its title
     * heavy, Stay filled in orange and leaving outlined. A touch outside it, or Back, stays.
     */
    private fun showLeavePopup() {
        if (leavePopup != null) return
        haptics.play(Haptic.GuideHint)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(GuideColours.CREAM, Radii.LARGE)
            setPadding(dp(24), dp(24), dp(24), dp(20))
            isClickable = true
            elevation = dp(8).toFloat()
            addView(TextView(context).apply {
                text = LEAVE_TITLE
                typeface = HEAVY
                includeFontPadding = false
                letterSpacing = -.02f
                setTextColor(GuideColours.INK)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(28f), dp(34).toFloat()))
            })
            addView(TextView(context).apply {
                text = LEAVE_BODY
                typeface = MEDIUM
                setTextColor(GuideMotion.faded(GuideColours.INK, BODY_ALPHA))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(17f), dp(22).toFloat()))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            addView(bigButton(LEAVE_NO) { closeLeavePopup() }.apply {
                setTextColor(GuideColours.INK)
                background = rounded(GuideColours.ORANGE, OFFER_RADIUS)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20) })
            addView(bigButton(LEAVE_YES) {
                preferences.edit().putBoolean(LEFT_EARLY, true).apply()
                closeLeavePopup()
                leave()
            }.apply {
                setTextColor(GuideColours.INK)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(16f), dp(21).toFloat()))
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1).coerceAtLeast(2), GuideMotion.faded(GuideColours.INK, NEXT_WAITING))
                    cornerRadius = Radii.px(resources, OFFER_RADIUS)
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }
        val scrim = FrameLayout(this).apply {
            setBackgroundColor(GuideMotion.faded(GuideColours.BLACK, LEAVE_SCRIM))
            isClickable = true
            setOnClickListener { closeLeavePopup() }
            addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                leftMargin = dp(GUTTER + 8)
                rightMargin = dp(GUTTER + 8)
            })
        }
        leavePopup = scrim
        root.addView(scrim, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(FADE_MILLIS * 2).start()
        card.scaleX = LEAVE_POP
        card.scaleY = LEAVE_POP
        card.animate().scaleX(1f).scaleY(1f).setDuration(ENTER_MILLIS).setInterpolator(android.view.animation.OvershootInterpolator()).start()
    }

    private fun closeLeavePopup() {
        val popup = leavePopup ?: return
        leavePopup = null
        popup.animate().cancel()
        root.removeView(popup)
    }

    /**
     * The high note: lines cross the screen one after the other, then "Titan" flies in from the left and "Keys" from the right.
     * They lock together into the name with a click, the keys answer, and the slogan follows.
     */
    private fun finale(end: GuideStep) {
        haptics.celebrate()
        for (sweep in 0 until FINALE_SWEEPS) overlay.sweep(delay = sweep * FINALE_STAGGER_MILLIS, thickness = BEAT_LINE + sweep * 2f)
        enter(end)
    }

    /**
     * The last screen's one boring thing: the offer to share swipes, in plain words, until it has an answer. Then Next goes to the
     * playground.
     */
    private fun showEndChoices() {
        if (!flow.finished) return
        // The header keeps only the way to the boring guide, small, and ✕: the big button is going to type (owner, v5).
        next.visibility = View.VISIBLE
        next.text = BORING_GUIDE_LABEL
        offer.visibility = View.GONE
        hideHint()
        if (endButtons.visibility != View.VISIBLE) {
            endButtons.visibility = View.VISIBLE
            endButtons.alpha = 0f
            endButtons.translationY = dp(16).toFloat()
            endButtons.animate().alpha(1f).translationY(0f).setDuration(ENTER_MILLIS).start()
        }
        fillEndChoices()
    }

    /** The end's words: the share switch, and under it the explanation until it's answered, then the owner's line alone. */
    private fun fillEndChoices() {
        val asking = Contributions.unanswered(this)
        // A switch, off until the user agrees on the share policy page; never shown on before that.
        shareButton.text = if (Contributions.enabled(this)) SHARE_ON else SHARE_OFF
        shareLine.text = if (asking) SHARE_PLAIN else SHARE_ANSWERED
        paintEndButtons()
    }

    /** The boring guide is a filled button; sharing is a switch, filled only while it's on. */
    private fun paintEndButtons() {
        val onInk = tone == GuideTone.Ink
        val strong = if (onInk) GuideColours.CREAM else GuideColours.INK
        boringButton.setTextColor(if (onInk) GuideColours.INK else GuideColours.CREAM)
        boringButton.background = rounded(if (onInk) GuideColours.ORANGE else GuideColours.BLACK, OFFER_RADIUS)
        againLink.setTextColor(strong)
        againLink.background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1).coerceAtLeast(2), GuideMotion.faded(strong, NEXT_WAITING))
            cornerRadius = Radii.px(resources, OFFER_RADIUS)
        }
        if (Contributions.enabled(this)) {
            shareButton.setTextColor(if (onInk) GuideColours.INK else GuideColours.CREAM)
            shareButton.background = rounded(if (onInk) GuideColours.ORANGE else GuideColours.BLACK, OFFER_RADIUS)
        } else {
            shareButton.setTextColor(strong)
            shareButton.background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1).coerceAtLeast(2), GuideMotion.faded(strong, NEXT_WAITING))
                cornerRadius = Radii.px(resources, OFFER_RADIUS)
            }
        }
    }

    /** Flies the two halves of the name to where the title shows it, then shows the title in their place. */
    private fun landName(name: String) {
        val layout = title.layout
        val split = name.indexOf(NAME_SECOND_HALF)
        if (layout == null || split <= 0 || overlay.width == 0) {
            nameLanded()
            return
        }
        val at = IntArray(2)
        val origin = IntArray(2)
        title.getLocationInWindow(at)
        overlay.getLocationInWindow(origin)
        val baseline = title.getLineBounds(0, null).toFloat()
        val x = at[0] - origin[0] + title.compoundPaddingLeft + layout.getLineLeft(0)
        val paint = Paint(title.paint).apply { color = title.currentTextColor }
        overlay.flyIn(name.substring(0, split), name.substring(split), paint, x, at[1] - origin[1] + baseline, ::nameLanded)
    }

    private fun nameLanded() {
        haptics.celebrate()
        overlay.sweep(thickness = LINE)
        keys.celebrate()
        title.alpha = 1f
        // The name clicks into place: a small push out from its corner, then back.
        title.pivotX = 0f
        title.pivotY = title.height.toFloat()
        title.scaleX = NAME_CLICK
        title.scaleY = NAME_CLICK
        title.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(NAME_CLICK_MILLIS)
            .setInterpolator(OvershootInterpolator(REACTION_OVERSHOOT)).start()
        body.animate().alpha(1f).setStartDelay(SLOGAN_DELAY_MILLIS).setDuration(ENTER_MILLIS).start()
        chapter.animate().alpha(1f).setStartDelay(SLOGAN_DELAY_MILLIS * 2).setDuration(ENTER_MILLIS).start()
    }

    private fun startAgain() {
        main.removeCallbacksAndMessages(null)
        finaleView.stop()
        finaleView.visibility = View.GONE
        for (view in listOf<View>(title, instruction, segments, chapter)) view.visibility = View.VISIBLE
        busy = true
        passed = false
        changing = true
        flow.start()
        enteredChapter = null
        endButtons.visibility = View.GONE
        preferences.edit().remove(RESUME).apply()
        again.visibility = View.GONE
        again.text = "Start again"
        for (view in listOf(title, body)) view.animate().alpha(0f).setDuration(FADE_MILLIS).start()
        // The ending hides the line above the title until its name lands, so it comes back here.
        chapter.animate().alpha(1f).setStartDelay(0).setDuration(FADE_MILLIS).start()
        backdrop.wipeTo(backgroundOf(GuideTone.Ink))
        overlay.sweep()
        main.postDelayed({
            changing = false
            applyTone(GuideTone.Ink)
            seeding = true
            field.setText("")
            seeding = false
            for (view in listOf(field, phantom, keys, script)) {
                view.visibility = View.VISIBLE
                view.animate().alpha(1f).setDuration(FADE_MILLIS).start()
            }
            keyboardShown(true)
            enter(flow.step)
        }, WIPE_MILLIS)
    }

    /** Leaves at any time. The guide remembers where you stopped, and opens there from the guide page. */
    private fun leave() {
        // The playground's setup slide closes to the app's setup page, which waits for a touch to start again.
        if (playground && settingUp) leavePlayground(PLAYGROUND_SETUP) else finish()
    }

    private fun keyName(code: Int): String? = when (code) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (code - KeyEvent.KEYCODE_A)).toString()
        KeyEvent.KEYCODE_DEL -> Layout.BACKSPACE
        KeyEvent.KEYCODE_ENTER -> Layout.ENTER
        KeyEvent.KEYCODE_SPACE -> Layout.SPACE
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> Layout.ALT
        KeyEvent.KEYCODE_SYM -> Layout.SYM
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> "⇧"
        // On this phone fn can also arrive as Ctrl.
        KeyEvent.KEYCODE_FUNCTION, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> Layout.FN
        else -> null
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    companion object {
        private const val PREFERENCES = "app"
        private const val SHOWN = "guide_shown"
        private const val RESUME = "guide_resume"
        /** The slides passed, kept across restarts; every chapter finished; and the guide left before its end. */
        private const val PASSED = "guide_passed"
        private const val ALL_FINISHED = "guide_all_finished"
        private const val LEFT_EARLY = "guide_left_early"

        // The owner's words for what happens around the slides (v3.1).
        private const val IDLE_TITLE = "Still there?"
        private const val IDLE_AGAIN_TITLE = "The keys won't press themselves. We checked."
        private const val SKIP_ROAST = "Skipping? Fine. You'll be back."
        private const val GOING_BACK = "Missed something? Happens to the best of us."
        private const val BACK_FROM_END = "Miss us already? That's sweet. Weird, but sweet."
        private const val FEATURE_OFF_TITLE = "You turned this off. Bold."
        private const val FEATURE_OFF_BODY = "Press the button below to turn it back on, then try again."
        private const val HOLD_OFF_TITLE = "You turned this off. We'll pretend you didn't."
        private const val HOLD_OFF_BODY = "Press the button below to turn it on."
        private const val FN_OFF_TITLE = "Your fn key isn't Ctrl."
        private const val FN_OFF_BODY = "Paste needs it. Turn it on?"
        private const val DECLINE_OFF = "☐  I'd rather not paste. That's on me, and I accept the consequences."
        private const val DECLINE_ON = "☑  I'd rather not paste. That's on me, and I accept the consequences."
        private const val SCREEN_TOUCHED = "The screen? Really? Why'd you buy this phone?"
        private const val LEAVE_TITLE = "Leaving already?"
        private const val LEAVE_BODY = "You haven't finished the guide. Leave anyway?"
        private const val LEAVE_YES = "I already know everything"
        private const val LEAVE_NO = "Stay"
        /** The leaving pop-up's dimming, and how small its card starts before it pops in. */
        private const val LEAVE_SCRIM = .62f
        private const val LEAVE_POP = .92f
        private const val COME_BACK_TITLE = "Oh, look who's back. Didn't know everything after all?"
        private const val COME_BACK_BODY = "Pick up where you left off."
        private const val FIX_UNDONE_TITLE = "You wanted the typo back? Rebel."
        private const val FIX_UNDONE_BODY = "Let it fix one and leave it. Type a typo, then Space."
        private const val NOTHING_PASTED_TITLE = "Nothing happened. Shocking."
        private const val NOTHING_PASTED_BODY = "fn isn't Ctrl yet. Press the button below to turn it on."
        private const val EMPTY_BAR_TITLE = "Nothing to judge. Rare for you."
        private const val EMPTY_BAR_BODY = "Type a letter so the bar fills up."
        private const val TOO_LATE_TITLE = "Still open? The last slide's over. Let it go."
        private const val TOO_LATE_PANEL = "Press Sym to close it."
        private const val TOO_LATE_ALT = "Press Alt to turn it off."
        /** How long the too-late line shows while the panel closes by itself. */
        private const val TOO_LATE_MILLIS = 1600L
        /** fn and V that changed nothing in this long says so. */
        private const val NOTHING_PASTED_MILLIS = 600L
        /** The word menu slide looks at the bar this long after the last thing that happened. */
        private const val BAR_CHECK_MILLIS = 700L
        /** When the word menu's demo presses O: late in the hold demo, early once the menu is really open. */
        private const val MENU_O_AT = .75f
        private const val MENU_O_OPEN_AT = .3f
        /** The flick demo's word goes as the flick snaps; the hold demo deletes up to this many more while the finger stays. */
        private const val FLICK_GONE = .25f
        private const val HOLD_DEMO_WORDS = 4
        /** The chapter one chips that the screen itself shows. */
        /** A rude clip, pasted on the clipboard slide, taken back out. */
        private const val RUDE_CLIP = "That one's rude. Took it back. Find the nice one."
        // The slides' look, in the new layout: one size for every title, one for the instruction, and the chip's timing.
        // Bigger in v5: the room above the keys goes to what's there, not to more things (owner).
        private const val TITLE_SP = 36f
        private const val TITLE_DP = 40
        private const val TITLE_SMALLEST_SP = 24f
        private const val TITLE_LINES = 1
        private const val BODY_SP = 20f
        private const val BODY_DP = 24
        private const val BODY_SMALLEST_SP = 15f
        private const val BODY_LINES = 1
        private const val GAP = 14
        private const val CHIP_PAD = 3
        private const val CHIP_SCALE = .94f
        private const val CHIP_SLIDE = 40
        private const val CHIP_MILLIS = 3000L
        private const val CHIP_MIN_MILLIS = 1200L
        private const val CHIP_IN_MILLIS = 220L
        private const val CHIP_OUT_MILLIS = 180L
        private const val CHECK_SIZE = .7f
        /** The pass beat: the old title's fade, the success line's pop from this size, its check, and the pill's morph and pulse. */
        private const val SUCCESS_OUT_MILLIS = 90L
        private const val SUCCESS_IN_MILLIS = 250L
        private const val SUCCESS_POP = 1.08f
        private const val CHECK_MILLIS = 220L
        private const val MORPH_MILLIS = 220L
        private const val PULSE_MILLIS = 240L
        private const val NEXT_PULSE = 1.12f
        /** The big Next's gentle breath, and the beat it waits for the wash to settle. */
        private const val NEXT_BREATH = .035f
        private const val NEXT_BREATH_MILLIS = 1100L
        private const val WASH_SETTLE_MILLIS = 250L
        /** The field's fill on ink, and how grey the ghost is, as in the approved frame. */
        private const val FIELD_FILL = .08f
        /** How strong the orange wash is behind what a slide aims at, in the field. */
        const val TARGET_WASH = .35f
        private const val GHOST_ALPHA = .38f
        /** How many words the ghost shows after the one you're on: more than two lines hold. */
        private const val GHOST_WORDS = 24
        /** The bar has the right word a moment after the swipe that missed. */
        private const val POINT_AFTER_MILLIS = 160L
        /** No input this long shows the idle words, and this much more shows them again. */
        private const val IDLE_MILLIS = 20_000L
        private const val IDLE_AGAIN_MILLIS = 20_000L
        /** How long the Skip roast shows as the slide changes. */
        private const val SKIP_MILLIS = 1100L
        /** A help with a title of its own shows after this long. */
        private const val HELP_TITLE_AFTER_MILLIS = 20_000L
        /** The beat before a slide done by the slide before it opens, done. */
        private const val JUMP_MILLIS = 900L
        /** Each letter that the guide types after a symbol. */
        private const val AUTO_TYPE_MILLIS = 28L
        /** The paste slide's clip stops being offered on the bar this soon, so the paste is a surprise. */
        private const val CLIP_HIDE_MILLIS = 400L
        /** The steering line, this much shorter than it was put in, counts as deleted. */
        private const val STEER_DELETE_LEEWAY = 12
        /** A chapter's roast: how long it shows, and how much longer each chapter, and the speed whose show plays behind it. */
        private const val ROAST_MILLIS = 2600L
        private const val ROAST_MORE_MILLIS = 400L
        private const val ROAST_CONFETTI_GAP_MILLIS = 350L
        private val ROAST_SPEEDS = listOf(45, 70, 90, 130, 170)
        private const val ROAST_LARGEST = 64
        private const val BORING_GUIDE_LINE = "Every feature in words. For nerds, and for people who skipped half of this. So, you."
        private const val SHARE_ON = "Share my swipes: on"
        private const val SHARE_OFF = "Share my swipes: off"
        /** The owner's words (v5). */
        /** The share line is the owner's words alone; how to change it later is in the explanation (owner, v5.2). */
        private const val SHARE_ANSWERED = "Trust me bro."
        private const val AGAIN_LINK = "Start the guide again"
        /** The finale's part of the end screen, from the top: the buttons have the rest. */
        private const val FINALE_SHARE = .68f
        /** The room between the finale and the end's buttons. */
        private const val FINALE_ROOM = 20

        // Setup, in ms. These are safe to change.
        private const val SETUP_REQUEST = 7
        private const val DISCLOSURE_REQUEST = 8
        /** How often the guide looks at the part whose page is open. Reading these settings is cheap. */
        private const val SETUP_POLL_MILLIS = 400L
        /** How long the guide waits to be in front again after it closes a page, before it sends for itself. */
        private const val SETUP_RETURN_MILLIS = 350L
        /** The beat after a part is done, before the next part shows, and the wait before its page opens by itself. */
        private const val SETUP_BEAT_MILLIS = 900L
        private const val SETUP_AUTO_MILLIS = 1300L
        /** How long "All set." stays before the story starts. */
        private const val SETUP_FINISH_MILLIS = 1400L
        private const val SETUP_NO_PAGE = "This phone doesn't have that page. Find it in Settings, then come back."

        private const val FIELD_HINT = "Type here. Or swipe."
        private const val TAG = "TitanKeysGuide"
        private const val PLAYGROUND = "playground"
        private const val PLAYGROUND_LABEL = "Playground"
        /** The share slide's offer (#34), while it is unanswered. It opens the full disclosure. */
        private const val SHARE_OFFER = "Share my swipes"
        /** The end's instruction. */
        /** The one boring thing at the end, in plain words: it is about consent, so no jokes here. */
        private const val SHARE_PLAIN = "Off until you agree. Then we get examples of your swipes to make swiping better. Change it " +
            "any time in Settings > Share swipes.\n" + SHARE_ANSWERED
        private const val END_NEXT = "Go type something"
        /** How many things the paste lesson rains down. */
        private const val RAIN_PIECES = 26
        private const val PLAYGROUND_TITLE = "TitanKeys"
        /** The playground's speed updates this often while you type, and its colour eases this much of the way each time. */
        private const val WPM_TICK_MILLIS = 120L
        /** How much of the way to the real speed the number goes each tick, up and down, and the least it counts down by. */
        private const val WPM_RISE = .35f
        private const val WPM_FALL = .12f
        private const val WPM_FALL_LEAST = 1.5f
        /** The number's pulse at each new step of the comments. */
        private const val WPM_PULSE = 1.14f
        private const val WPM_PULSE_MILLIS = 320L
        private const val WPM_LABEL_SIZE = .35f
        /** At full speed the screen is this much of the way to orange, so that the words stay easy to read. */
        private const val WPM_MOST_ORANGE = .85f
        private const val PLAYGROUND_BODY = "Type or swipe whatever you like. Follow the line above, or ignore it."

        // The look, in dp. These are safe to change. Words fit their box at any system font size, because the boxes set their size.
        // Ink's font (v5.3): GuideFonts is loaded first thing in onCreate, before anything is built.
        private val HEAVY: Typeface get() = GuideFonts.loadedHeavy
        private val MEDIUM: Typeface get() = GuideFonts.loadedMedium
        /** The same as the key columns' side, so that the drawn keys line up with the Sym panel's labels. */
        private const val GUTTER = Layout.KEY_SIDE_DP
        private const val TOP_ACTION = 32
        private const val TITLE_SMALLEST = 18
        private const val TITLE_LARGEST = 76
        private const val BODY_SMALLEST = 11
        private const val BODY_LARGEST = 20
        private const val CHAPTER_ALPHA = .55f
        private const val TITLE_SHARE = 2.2f
        private const val BODY_SHARE = 1.4f
        private const val FIELD_RADIUS = Radii.MEDIUM
        private const val HINT_RADIUS = Radii.MEDIUM
        private const val OFFER_RADIUS = Radii.LARGE
        private const val BODY_ALPHA = .86f
        private const val HINT_FILL = .14f
        /** How strong Next is while the step waits, and how strong Back's outline is. */
        private const val NEXT_WAITING = .6f
        private const val BACK_OUTLINE = .35f
        private const val LINE = 5f
        private const val SEGMENT_HEIGHT = 4
        private const val SEGMENT_OFF = .25f
        private const val SEGMENT_GLOW = 4
        /** The room between two chapters in the bar: wider than between two slides' ticks. */
        private const val CHAPTER_GAP = 6
        /** ✕, in the top right corner. */
        private const val CLOSE_SIZE = 48
        /** The least height of anything to touch. */
        private const val TOUCH = 44
        /** The chip's padding. */
        private const val CHIP_SIDE = 14
        private const val CHIP_TOP = 7
        /** Next, once a slide is passed: a normal button. */
        private const val NEXT_SP = 17f
        private const val NEXT_DP = 22
        private const val NEXT_PAD_SIDE = 28
        private const val NEXT_PAD_TOP = 10
        /** Below this much height the screen is too short for the drawing of the keys. */
        private const val COMPACT_HEIGHT = 240
        /** How long the word menu is open before its key hints show: its buttons have moved in by then. */
        private const val MENU_SETTLE_MILLIS = 450L
        /** The field on the slides: three lines, at this size. */
        private const val FIELD_LINES = 3
        /** How many of the lines finished so far the steering slides put above their own. */
        private const val STEER_LINES_BEFORE = 4
        private const val FIELD_SP = 23f
        private const val FIELD_DP = 29
        /** A bottom inset this much over the keyboard's own is an open panel's, in dp. */
        private const val PANEL_EXTRA = 80

        // The motion, in ms. Android's animator duration setting also scales the parts that draw themselves.
        private const val INTRO_MILLIS = 1250L
        private const val FADE_MILLIS = 140L
        private const val ENTER_MILLIS = 340L
        private const val ENTER_STAGGER_MILLIS = 60L
        private const val ENTER_OFFSET = 30
        private const val ENTER_OVERSHOOT = 1.4f
        private const val REACTION_IN_MILLIS = 300L
        private const val REACTION_OVERSHOOT = 2.2f
        private const val REACTION_DIM = .3f
        /** The size that the line that lands grows from. */
        private const val REACTION_FROM = .86f
        /** How long a finished step's line stays before the step's words come back. */
        private const val REACTION_MILLIS = 1400L
        /** When the new background has reached the middle of the screen. */
        private const val WIPE_MILLIS = 300L
        private const val BEAT_LINE = 3f
        private const val FINALE_SWEEPS = 6
        private const val FINALE_STAGGER_MILLIS = 110L
        /** The ending's name is "Titan" and "Keys", which fly in from the two sides and lock together. */
        private const val NAME_SECOND_HALF = "Keys"
        /** How far the name pushes out when it locks, and how long it takes to settle. */
        private const val NAME_CLICK = 1.08f
        private const val NAME_CLICK_MILLIS = 420L
        /** The slogan comes after the name has clicked into place, and the line above it after that. */
        private const val SLOGAN_DELAY_MILLIS = 260L
        /** A slide's help shows this soon (v5: sooner, so the hint is there before anyone gives up). */
        private const val HELP_AFTER_MILLIS = 5000L
        /** How soon help that had to wait for a finger, a key or the menu looks again. */
        private const val HELP_RECHECK_MILLIS = 700L
        /** The front page's prompt: room for a whole sentence. */
        private const val PROMPT_LINES = 3
        /** How long the bar's words wait as a slide opens, so it shows the slide's words once. */
        private const val BAR_SETTLE_MILLIS = 450L
        /** How strong a selection's cream is in the field. */
        private const val SELECTION_ALPHA = .32f
        /** How long Alt or the panel must stay on before the guide says so. */
        private const val MODIFIER_HINT_MILLIS = 350L
        /** How strong the words after the next one are in that line. */
        private const val SCRIPT_REST = .55f
        /** The passage: how dim the words behind you are, the box round a mistake, and how long a line takes to slide up. */
        private const val PASSAGE_DONE = .4f
        private const val PASSAGE_WRONG = 0xFFFF4D4D.toInt()
        private const val PASSAGE_WRONG_ALPHA = .55f
        private const val PASSAGE_SLIDE_MILLIS = 260L
        private const val PASSAGE_SKIPPED_ALPHA = .22f
        private const val PASSAGE_BOX = .08f
        /** What the guide says when Save or Forget is pressed, as they're only for show here. */
        private const val MENU_HINT_MILLIS = 120L
        private const val BORING_GUIDE = "/settings/guide-boring"
        private const val BORING_GUIDE_LABEL = "The boring guide"

        // Demonstrations: back to back until you act. These are safe to change.
        /** How long nobody acts before a slide shows its gesture again after you act. */
        private const val DEMO_IDLE_MILLIS = 5000L
        /** When a new slide shows its gesture: as soon as the slide's words have slid in. */
        private const val DEMO_ENTER_MILLIS = ENTER_STAGGER_MILLIS + ENTER_MILLIS
        /** The pause between one showing and the next. */
        private const val DEMO_GAP_MILLIS = 1000L
        /** How long a demonstration with no ghost finger takes. One with a ghost takes as long as the ghost. */
        private const val DEMO_MILLIS = 4200L
        private const val DEMO_FRAME_MILLIS = 33L
        /** How strong the grey words are. */
        private const val PHANTOM_ALPHA = .38f
        /** The part of the demonstration that typing a word takes, and when a swiped word lands, at the end of the ghost's path. */
        private const val TYPE_SHARE = .8f
        private const val SWIPE_LANDS = .72f
        /** When the last mark after a swiped word is in. */
        private const val MARKS_DONE = .94f
        /** When the strip puts the right word in place of the slip. */
        private const val FIX_LANDS = .62f
        /** The part that the ghost's taps take, before the cursor moves or the selection shows. */
        private const val TAPS_SHARE = .45f
        private const val CARET_ALPHA = .7f
        private const val SELECT_ALPHA = .4f
        /** When a held letter turns into its symbol. */
        private const val HOLD_TURNS = .55f
        /** The part that pushing the keys takes, before the text from the panel appears. */
        private const val INSERT_SHARE = .6f
        /** When a slip is typed and the bar starts to fix it, and when Space puts the right word in, after the bar's swap. */
        private const val SLIP_TYPED = .5f
        private const val SLIP_FIXED = .64f
        /** The part that Alt and the letter take, before the symbol appears. */
        private const val ALT_KEYS_SHARE = .5f
        /** When fn and C light, once the word is selected. */
        private const val COPY_KEYS = .72f
        /** How many words the bar's demonstrations show. */
        private const val BAR_WORDS = 3

        /** True until the guide has opened once, so it opens by itself after setup. */
        fun due(context: Context) = !context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(SHOWN, false)

        fun start(activity: Activity) {
            activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(SHOWN, true).apply()
            activity.startActivity(Intent(activity, GuideActivity::class.java))
        }

        /** The guide opened Accessibility for setup and waits for TitanKeys to be turned on there. */
        @Volatile private var awaitingAccessibility = false

        /**
         * TitanKeys was turned on in Accessibility. If the guide asked for it, the guide comes to the front again: Accessibility
         * opens its page for TitanKeys over its list, which the guide cannot close, and Android lets the service bring it back.
         */
        fun accessibilityOn(context: Context) {
            if (!awaitingAccessibility) return
            awaitingAccessibility = false
            try {
                context.startActivity(Intent(context, GuideActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            } catch (error: RuntimeException) {
                // Then the guide waits until you come back to it.
            }
        }

        const val PLAYGROUND_GUIDE = "guide"
        const val PLAYGROUND_SETTINGS = "settings"
        const val PLAYGROUND_CLOSED = "closed"
        const val PLAYGROUND_SETUP = "setup"
        /** Told where to go when the playground closes. Only one playground is open at a time. */
        private var playgroundExit: ((String) -> Unit)? = null

        /** Interactive guides that are open now. Only the main thread changes this. */
        private var guidesOpen = 0
        /** The playground asked for while a guide was open, which opens once the guide closes. */
        private var waitingPlayground: (() -> Unit)? = null

        /**
         * Opens the playground, the app's front page. [exit] gets [PLAYGROUND_GUIDE] or [PLAYGROUND_SETTINGS] from its buttons,
         * [PLAYGROUND_SETUP] when it closes from its setup slide, or [PLAYGROUND_CLOSED] when something else closed it.
         * The front page can ask for it while the guide is open, as when setup ends in the guide, so then it waits for the guide.
         */
        fun playground(activity: Activity, exit: (String) -> Unit) {
            playgroundExit = exit
            // The front page asks each time its window is in front, so one that is open, or that a button just closed, is enough.
            // A playground that Android is closing counts as closed: it can close after the app's window is in front. And with
            // the app's window in front, no playground is: the app's window is at the bottom of the app's screens.
            val showing = playgrounds.any { !it.isFinishing } && !activity.hasWindowFocus()
            val now = SystemClock.uptimeMillis()
            val quiet = now - playgroundStarting < PLAYGROUND_QUIET_MILLIS
            Log.d(TAG, "playground asked: open ${playgrounds.size}, showing $showing, quiet $quiet, guides $guidesOpen")
            if (showing || quiet) return
            val open = {
                // One that is starting does not count as open yet, so a second ask in the meantime is left out.
                playgroundStarting = SystemClock.uptimeMillis()
                activity.startActivity(Intent(activity, GuideActivity::class.java).putExtra(PLAYGROUND, true))
            }
            if (guidesOpen > 0) waitingPlayground = open else open()
        }

        /** The front page went away, so a playground that waits for a guide to close is no longer wanted. */
        fun cancelPlayground() {
            waitingPlayground = null
        }

        /** Playgrounds that are open now. Main thread only. */
        private val playgrounds = ArrayList<GuideActivity>()
        /** When a playground was asked to start, until it has started. */
        private var playgroundStarting = -PLAYGROUND_QUIET_MILLIS
        /** A playground takes a moment to start. A second ask in this time is left out. */
        private const val PLAYGROUND_QUIET_MILLIS = 1500L
    }
}
