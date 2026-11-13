package com.atlas.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import android.view.View

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
 *    It reads their places on every frame, so a field that grows or a button that shows needs nothing more. A hidden view keeps
 *    nothing out.
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

    /** The views it keeps clear of, with [GuideDecor.CLEAR_DP] around each: the title, the instruction, the field, the keys. */
    fun keepOutOf(views: List<View>) {
        tracked = views
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val slide = current
        val now = SystemClock.uptimeMillis()
        area.set(0f, 0f, width.toFloat(), height.toFloat())
        measureKeepOut()
        val dp = resources.displayMetrics.density
        if (!ValueAnimator.areAnimatorsEnabled()) {
            if (slide != null) decor.draw(canvas, slide, GuideDecor.stillMillis(slide), area, rects, onOrange, dp)
            return
        }
        val since = now - changedAt
        val fadeIn = if (firstFade) FADE_IN_MILLIS else CROSSFADE_MILLIS
        val old = previous
        if (old != null && since < CROSSFADE_MILLIS)
            decor.draw(canvas, old, now - previousFrom, area, rects, onOrange, dp, 1f - since / CROSSFADE_MILLIS.toFloat())
        if (slide != null) decor.draw(canvas, slide, now - currentFrom, area, rects, onOrange, dp, ease(since / fadeIn.toFloat()))
        if (running) postInvalidateDelayed(FRAME_MILLIS)
    }

    private fun measureKeepOut() {
        rects.clear()
        if (tracked.isEmpty()) return
        getLocationInWindow(here)
        for (view in tracked) {
            if (!view.isShown || view.width == 0 || view.height == 0) continue
            view.getLocationInWindow(there)
            val left = (there[0] - here[0]).toFloat()
            val top = (there[1] - here[1]).toFloat()
            rects += RectF(left, top, left + view.width, top + view.height)
        }
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
