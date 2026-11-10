package com.atlas.keyboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.Gravity
import android.widget.TextView
import kotlin.math.ceil

/**
 * One line of text that fits shows still, in the middle. A longer line starts at its beginning, and after [START_MILLIS] it
 * scrolls calmly to its end, pauses, goes back to the beginning, and does it again, for as long as the same text shows. Android's
 * animator duration setting slows this down or turns it off, and then the line stays at its beginning.
 */
internal class BandMarquee(private val view: TextView) {
    private var animator: ValueAnimator? = null
    /** The text that shows now, or null after [stop], so that the same text shown again starts over. */
    private var shown: String? = null
    private val scroll = Runnable { scrollOnce() }

    init {
        view.setHorizontallyScrolling(true)
        view.isSingleLine = true
        view.ellipsize = null
    }

    /** Shows [text]. The same text again keeps its marquee going, so a finger that stays on one clip sees it go on. */
    fun show(text: String) {
        if (shown == text) return
        stop()
        shown = text
        view.text = text
        view.post(::place)
    }

    fun stop() {
        shown = null
        animator?.cancel()
        animator = null
        view.removeCallbacks(scroll)
        view.scrollTo(0, 0)
    }

    private fun overflow(): Int {
        val room = view.width - view.totalPaddingLeft - view.totalPaddingRight
        return if (room <= 0) 0 else ceil(view.paint.measureText(view.text.toString()) - room).toInt().coerceAtLeast(0)
    }

    private fun place() {
        if (shown == null) return
        // A line that has just become visible may not be measured yet.
        if (view.width == 0) {
            if (view.isShown) view.post(::place)
            return
        }
        val long = overflow() > 0
        view.gravity = (if (long) Gravity.START else Gravity.CENTER_HORIZONTAL) or Gravity.CENTER_VERTICAL
        view.scrollTo(0, 0)
        if (long && ValueAnimator.areAnimatorsEnabled()) view.postDelayed(scroll, START_MILLIS)
    }

    private fun scrollOnce() {
        val distance = overflow()
        if (distance <= 0) return
        val density = view.resources.displayMetrics.density
        animator = ValueAnimator.ofInt(0, distance).apply {
            duration = (distance / density / DP_PER_SECOND * 1000).toLong().coerceAtLeast(MIN_SCROLL_MILLIS)
            addUpdateListener { view.scrollTo(it.animatedValue as Int, 0) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    // A pause at the end, then back to the beginning, a pause there, and again.
                    view.postDelayed({
                        if (animator !== this@apply) return@postDelayed
                        view.scrollTo(0, 0)
                        view.postDelayed(scroll, START_PAUSE_MILLIS)
                    }, END_PAUSE_MILLIS)
                }
            })
            start()
        }
    }

    companion object {
        /** How long a finger rests before a long line starts to move. */
        const val START_MILLIS = 2000L
        const val END_PAUSE_MILLIS = 1500L
        const val START_PAUSE_MILLIS = 1500L
        /** Slow enough to read as it goes. */
        const val DP_PER_SECOND = 45f
        const val MIN_SCROLL_MILLIS = 600L
    }
}
