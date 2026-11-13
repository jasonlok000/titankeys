package com.atlas.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.TextView
import kotlin.math.max

/**
 * Hosts [GuideDecor]: the slide's decoration behind the guide's content, with its fades and its frame rate.
 *
 * - It draws at up to 30 frames a second, and only while it is attached, visible on screen and the screen is on.
 * - With Android's animations off (animator duration scale 0), it draws one still picture of the slide and no fades.
 * - A first slide fades in over [FADE_IN_MILLIS]. A new slide crossfades with the last over [CROSSFADE_MILLIS]. Each slide's
 *   clock starts when it is set, and keeps running through its fade out.
 * - It takes no touches, and nothing it shows depends on what you type or whether you pass.
 *
 * **How the guide adds it** (for whoever wires it in after the redesign; GuideActivity is not changed here):
 * 1. Add it to the slide's root as the first child, so it is behind the title, the instruction, the field and the keyboard
 *    drawing and above the black background: `root.addView(decor, 0, LayoutParams(MATCH_PARENT, MATCH_PARENT))`, in a FrameLayout
 *    or an equivalent that lets it fill the content.
 * 2. Tell it what to keep clear: `decor.keepOutOf(listOf(chapterRow, title, instruction, field, nextButton, keyboardDrawing))`.
 *    It reads their places on every frame, so a field that grows or a button that shows needs nothing more. A view that is gone
 *    keeps nothing out; one that is only invisible keeps its room, so nothing is drawn where it is about to appear. A plain text
 *    keeps out only its words, not the rest of its full-width box.
 * 3. When a slide has settled, set `decor.slide = step.id` (GuideSteps' ids), `GuideDecor.FINALE` when the finale plays and
 *    `GuideDecor.SETUP` for slide zero. Set it to null to fade the decoration away.
 * 4. Set `decor.onOrange = true` while the orange pass screen shows, and back to false after. It turns ink and keeps moving; it
 *    never marks the pass.
 */
class GuideDecorView(context: Context) : View(context) {
    private val decor = GuideDecor(runCatching { context.resources.getFont(R.font.inter_bold) }.getOrNull())
    private val area = RectF()
    private val rects = ArrayList<RectF>()
    private val here = IntArray(2)
    private val there = IntArray(2)
    private var tracked: List<View> = emptyList()
    private var floorView: View? = null
    /** The bottom of the floor view's place, while it keeps its room, or NaN. */
    private var floorBottom = Float.NaN
    /** The keyboard's top, panel open or not, in this view's pixels. */
    private var keyboardTop = 0f
    private var screenOn = true
    private var shown = false
    private var running = false

    private var current: String? = null
    private var currentFrom = 0L
    private var previous: String? = null
    private var previousFrom = 0L
    private var changedAt = 0L
    private var firstFade = true

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** The slide whose decoration shows: a GuideSteps id, [GuideDecor.FINALE] or [GuideDecor.SETUP], or null for none. */
    var slide: String?
        get() = current
        set(value) {
            if (value == current) return
            val now = SystemClock.uptimeMillis()
            firstFade = current == null
            previous = current
            previousFrom = currentFrom
            current = value
            currentFrom = now
            changedAt = now
            update()
            invalidate()
        }

    /** Whether the orange pass screen shows, which turns the decoration ink. */
    var onOrange = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** An area it keeps clear of besides the views, in its own pixels, such as the finale's words, which have no view of their own. */
    private var area2: RectF? = null

    fun keepOutArea(rect: RectF?) {
        area2 = rect
        invalidate()
    }

    /**
     * The views it keeps clear of, with [GuideDecor.CLEAR_DP] around each: the title, the instruction, the field, the keys. While
     * [floor] (the drawing of the keys) keeps its room, shown or hidden under a panel, the slide's decoration is laid out down to
     * it, so a panel coming or going moves nothing. See [GuideDecor.layoutBottom].
     */
    fun keepOutOf(views: List<View>, floor: View? = null) {
        tracked = views
        floorView = floor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val slide = current
        val now = SystemClock.uptimeMillis()
        // Nothing draws under the status bar (Master, 2026-11-13: shredder tiles, sparkles and rays showed there).
        getLocationInWindow(here)
        val bar = rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
        // Never behind the keyboard (Master, 2026-11-13, build #10: with Sym open, the decoration slid behind the panel): its top,
        // a panel's when one is open, from the bottom of the window.
        val keyboard = rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0
        keyboardTop = (rootView.height - keyboard - here[1]).toFloat()
        area.set(0f, max(0, bar - here[1]).toFloat(), width.toFloat(), height.toFloat())
        measureKeepOut()
        val dp = resources.displayMetrics.density
        if (!ValueAnimator.areAnimatorsEnabled()) {
            if (slide != null) drawSlide(canvas, slide, GuideDecor.stillMillis(slide), dp, 1f)
            return
        }
        val since = now - changedAt
        val fadeIn = if (firstFade) FADE_IN_MILLIS else CROSSFADE_MILLIS
        val old = previous
        if (old != null && since < CROSSFADE_MILLIS)
            drawSlide(canvas, old, now - previousFrom, dp, 1f - since / CROSSFADE_MILLIS.toFloat())
        if (slide != null) drawSlide(canvas, slide, now - currentFrom, dp, ease(since / fadeIn.toFloat()))
        if (running) postInvalidateDelayed(FRAME_MILLIS)
    }

    /** [slide]'s decoration, laid out down to the floor and cut at the keyboard's top. */
    private fun drawSlide(canvas: Canvas, slide: String, millis: Long, dp: Float, fade: Float) {
        val viewBottom = height.toFloat()
        val bottom = GuideDecor.layoutBottom(slide, viewBottom, keyboardTop, floorBottom.takeUnless { it.isNaN() })
        area.bottom = max(area.top, bottom)
        decor.draw(canvas, slide, millis, area, rects, onOrange, dp, fade, GuideDecor.clipBottom(slide, viewBottom, keyboardTop))
    }

    private fun measureKeepOut() {
        rects.clear()
        floorBottom = Float.NaN
        area2?.let { rects += RectF(it) }
        if (tracked.isEmpty()) return
        getLocationInWindow(here)
        for (view in tracked) {
            if (!holdsRoom(view) || view.width == 0 || view.height == 0) continue
            view.getLocationInWindow(there)
            var left = (there[0] - here[0]).toFloat()
            val top = (there[1] - here[1]).toFloat()
            var right = left + view.width
            // A plain text in a full-width box keeps out only its words: a one-line title leaves the rest of its line free.
            val layout = (view as? TextView)?.takeIf { it !is EditText && it.background == null && it.text.isNotEmpty() }?.layout
            if (layout != null && layout.lineCount > 0) {
                var from = Float.MAX_VALUE
                var to = 0f
                for (line in 0 until layout.lineCount) {
                    from = minOf(from, layout.getLineLeft(line))
                    to = maxOf(to, layout.getLineRight(line))
                }
                val inside = (view as TextView).totalPaddingLeft
                val scale = view.scaleX
                right = left + (inside + to) * scale
                left += (inside + from) * scale
            }
            rects += RectF(left, top, right, top + view.height)
            if (view === floorView) floorBottom = top + view.height
        }
    }

    /** Whether [view] holds a place on the screen: attached, and neither it nor anything it is in is gone. */
    private fun holdsRoom(view: View): Boolean {
        if (!view.isAttachedToWindow) return false
        var at: View? = view
        while (at != null) {
            if (at.visibility == GONE) return false
            at = at.parent as? View
        }
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        update()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        update()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        shown = isVisible
        update()
    }

    override fun onScreenStateChanged(screenState: Int) {
        super.onScreenStateChanged(screenState)
        screenOn = screenState == SCREEN_STATE_ON
        update()
    }

    /** Runs the frames only while someone can see them, and only with animations on. */
    private fun update() {
        val run = isAttachedToWindow && shown && screenOn && ValueAnimator.areAnimatorsEnabled() && (current != null || previous != null)
        if (run == running) return
        running = run
        if (run) invalidate()
    }

    private fun ease(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }

    companion object {
        const val FADE_IN_MILLIS = 600L
        const val CROSSFADE_MILLIS = 400L
        /** 30 frames a second. */
        const val FRAME_MILLIS = 33L
    }
}
