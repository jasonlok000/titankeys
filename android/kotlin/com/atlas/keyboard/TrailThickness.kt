package com.atlas.keyboard

import kotlin.math.abs

/**
 * How thick the live trail is drawn at each point, from the touch's size against the size usual at that spot (#52).
 * 1 is a usual touch, less is a light fingertip and more is a firm or flat finger. The scale follows the owner's own
 * touches, so their everyday range fills most of it: from [THIN_AT] steps under the usual size, the lightest twentieth of
 * points, to [THICK_AT] over it, as firm as nine swipes in ten get at some point. The thickness follows the size slowly,
 * so a touch that wavers between two steps stays calm.
 */
class TrailThickness {
    private var level = Float.NaN

    fun reset() {
        level = Float.NaN
    }

    /** The thickness at this touchpad point, from its sensor [step]. A step of 0, not known, keeps the thickness as it was. */
    fun next(x: Float, y: Float, step: Int): Float {
        if (step > 0) {
            val over = step - usual(x, y)
            level = if (level.isNaN()) over else level + (over - level) * FOLLOW
        }
        if (level.isNaN() || abs(level) <= USUAL_BAND) return 1f
        return if (level > 0) 1f + (THICKEST - 1f) * ((level - USUAL_BAND) / (THICK_AT - USUAL_BAND)).coerceAtMost(1f)
        else 1f - (1f - THINNEST) * ((-level - USUAL_BAND) / (-THIN_AT - USUAL_BAND)).coerceAtMost(1f)
    }

    companion object {
        /** How far the thickness moves toward each new point's size. */
        const val FOLLOW = .12f
        /** Steps from the usual size that still draw the usual thickness, so a touch between two steps stays calm. */
        const val USUAL_BAND = .15f
        /**
         * Steps over the usual size that draw the thickest trail: the 90th percentile of each swipe's firmest smoothed
         * point in the owner's raw-log-14 (0.82), so a firmer press reaches it and a resting thumb is well past it.
         */
        const val THICK_AT = .85f
        /** Steps under the usual size that draw the thinnest trail: the lightest 5% of smoothed points in raw-log-14. */
        const val THIN_AT = -.8f
        const val THINNEST = .5f
        const val THICKEST = 1.8f

        /**
         * The usual sensor step in each touchpad cell, ten columns by four rows, from a day of the owner's swiping. The
         * top of the pad reads smaller than the middle and the left larger than the right. Cells with too few touches to
         * tell use the overall usual size.
         */
        private val USUAL_STEP = floatArrayOf(
            1.53f, 1.90f, 1.98f, 1.84f, 1.77f, 1.76f, 1.72f, 1.63f, 1.50f, 1.41f,
            2.26f, 2.89f, 2.90f, 2.70f, 2.53f, 2.36f, 2.38f, 2.27f, 1.96f, 1.29f,
            2.26f, 3.10f, 2.98f, 3.00f, 2.74f, 2.52f, 2.52f, 2.21f, 2.26f, 2.26f,
            2.26f, 2.26f, 2.26f, 2.26f, 2.26f, 2.26f, 2.26f, 2.26f, 2.26f, 2.26f,
        )

        fun usual(x: Float, y: Float): Float {
            val column = (x * 10).toInt().coerceIn(0, 9)
            val row = (y * 4).toInt().coerceIn(0, 3)
            return USUAL_STEP[row * 10 + column]
        }
    }
}
