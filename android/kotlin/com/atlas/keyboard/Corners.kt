package com.atlas.keyboard

import android.view.Display
import android.view.RoundedCorner
import android.view.WindowInsets
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * The rounded corners of the screen, which cut off what is drawn in them. The keyboard sits on the bottom edge, so content near
 * its sides keeps the distance that [inset] gives. A screen that reaches the top, such as the guide, also keeps its top content
 * clear with [topInset].
 */
class Corners(private val radius: Float = 0f, private val topRadius: Float = 0f) {
    /** How far content [above] pixels over the bottom edge of the screen must keep from each side. */
    fun inset(above: Float) = inset(radius, above)

    /** How far content [below] pixels under the top edge of the screen must keep from each side. */
    fun topInset(below: Float) = inset(topRadius, below)

    /**
     * Whether a point [x] px across a screen [width] wide and [above] px over its bottom edge shows whole, at least [gap] px from
     * the screen's outline: its bottom edge, its sides and the curve of its bottom corners, measured straight to the curve.
     */
    fun clear(x: Float, above: Float, width: Float, gap: Float) = clear(radius, x, above, width, gap)

    /**
     * How far in from each side content [above] px over the bottom edge keeps to stay [gap] px from the curve itself, measured
     * straight to it: [inset] measures across only, which lets content near the bottom of the curve, where it's nearly flat, come
     * closer than [gap] to it.
     */
    fun clearInset(above: Float, gap: Float): Int = ceil(gap).toInt() + if (radius <= 0f) 0 else inset(radius - gap, above - gap)

    private fun inset(radius: Float, fromEdge: Float): Int {
        if (radius <= 0f || fromEdge >= radius) return 0
        val down = radius - fromEdge.coerceAtLeast(0f)
        return ceil(radius - sqrt(radius * radius - down * down)).toInt()
    }

    companion object {
        val None = Corners()

        /** [clear] on a screen whose bottom corners have [radius] px: see [Corners.clear]. Plain arithmetic, for the checks too. */
        fun clear(radius: Float, x: Float, above: Float, width: Float, gap: Float): Boolean {
            if (above < gap || x < gap || x > width - gap) return false
            val across = minOf(x, width - x)
            if (radius <= 0f || above >= radius || across >= radius) return true
            val dx = radius - across
            val dy = radius - above
            return dx * dx + dy * dy <= (radius - gap) * (radius - gap)
        }

        private val BOTTOM = listOf(RoundedCorner.POSITION_BOTTOM_LEFT, RoundedCorner.POSITION_BOTTOM_RIGHT)
        private val TOP = listOf(RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT)

        /**
         * The corners from the window's insets, or failing that from the screen. The keyboard's window does not always get them in
         * its insets, but the screen always knows them: the Titan 2 Elite reports a radius of 100 pixels at each corner.
         */
        fun of(insets: WindowInsets?, display: Display?): Corners {
            fun largest(positions: List<Int>): Float {
                val fromInsets = positions.maxOf { insets?.getRoundedCorner(it)?.radius ?: 0 }
                val fromDisplay = positions.maxOf { display?.getRoundedCorner(it)?.radius ?: 0 }
                return maxOf(fromInsets, fromDisplay).toFloat()
            }
            return Corners(largest(BOTTOM), largest(TOP))
        }
    }
}
