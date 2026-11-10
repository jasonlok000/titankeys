package com.atlas.keyboard

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sign

/**
 * How thick the live trail is drawn at each point, from the touch's size against the size usual at that spot (#52).
 * 1 is a usual touch, less is a light fingertip and more is a flat finger. One step of the sensor is a large change, so
 * the thickness follows it slowly and a touch that wavers between two steps stays calm.
 */
class TrailThickness {
    private var level = Float.NaN

    fun reset() {
        level = Float.NaN
    }

    /** The thickness at this touchpad point, from its sensor [step]. A step of 0, not known, keeps the thickness as it was. */
    fun next(x: Float, y: Float, step: Int): Float {
        if (step > 0) {
            val relative = ln(step / usual(x, y))
            level = if (level.isNaN()) relative else level + (relative - level) * FOLLOW
        }
        if (level.isNaN()) return 1f
        // A touch near the usual size is the usual thickness: the sensor's whole steps are never exactly the usual.
        val beyond = sign(level) * max(0f, abs(level) - USUAL_BAND)
        return exp(beyond * STRENGTH).coerceIn(THINNEST, THICKEST)
    }

    companion object {
        /** How far the thickness moves toward each new point's size. */
        const val FOLLOW = .12f
        /** How far from the usual size, as a log of the ratio, still counts as usual: about a fifth either way. */
        const val USUAL_BAND = .2f
        /** Below 1, a touch twice the usual size draws less than twice as thick. */
        const val STRENGTH = .8f
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
