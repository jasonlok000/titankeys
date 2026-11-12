package com.atlas.keyboard

import kotlin.math.roundToInt

/**
 * Where the real keys sit across the screen, which sits just above them: [left] is Q's left edge and [right] is P's right edge,
 * each as a part of the screen's width. Every drawing of the keys is built from these two by [KeyGeometry], so a drawn key sits
 * right over its real key.
 *
 * The defaults are Unihertz's own face-on drawing of the Titan 2 Elite: the keys span the whole width of the screen. The Key
 * calibration screen in TitanKeys Debug moves the two edges ([KeyCalibration]).
 */
class KeyAlignment(val left: Float = DEFAULT_LEFT, val right: Float = DEFAULT_RIGHT) {
    override fun equals(other: Any?) = other is KeyAlignment && other.left == left && other.right == right
    override fun hashCode() = left.hashCode() * 31 + right.hashCode()

    companion object {
        const val DEFAULT_LEFT = 0f
        const val DEFAULT_RIGHT = 1f
        val DEFAULT = KeyAlignment()

        /**
         * How far the calibration screen moves each edge from its default, as a part of the screen's width: about a key's width
         * either way, which is far more than any phone is off by.
         */
        const val EDGE_REACH = .08f
        /** The sliders move an edge in steps of a thousandth of the screen's width: about a pixel. */
        const val EDGE_STEPS = 160

        fun leftAt(progress: Int) = edgeAt(DEFAULT_LEFT, progress)
        fun rightAt(progress: Int) = edgeAt(DEFAULT_RIGHT, progress)
        fun leftProgress(left: Float) = edgeProgress(DEFAULT_LEFT, left)
        fun rightProgress(right: Float) = edgeProgress(DEFAULT_RIGHT, right)

        private fun edgeAt(default: Float, progress: Int) =
            ((default - EDGE_REACH + 2 * EDGE_REACH * progress / EDGE_STEPS) * 1000).roundToInt() / 1000f

        private fun edgeProgress(default: Float, edge: Float) =
            ((edge - default + EDGE_REACH) / (2 * EDGE_REACH) * EDGE_STEPS).roundToInt().coerceIn(0, EDGE_STEPS)

        /** The alignment every drawing uses now. [KeyCalibration] loads it and keeps it up to date. */
        @Volatile @JvmStatic var current = DEFAULT

        /**
         * The left and right, in pixels, of key column [index], from 0 at Q, on a screen [width] pixels wide, as calibrated now:
         * from the middle of the gap left of the key to the middle of the gap right of it. For anything that sits over a key,
         * such as the word menu's buttons.
         */
        @JvmStatic fun column(width: Float, index: Int): Pair<Float, Float> = KeyGeometry(width, current).cell(index)
    }
}
