package com.atlas.keyboard

import kotlin.math.roundToInt

/**
 * Where the real keys sit across the screen, which sits just above them, each as a part of the screen's width: [left] is Q's left
 * edge, [key] one key's width and [gap] the gap between two keys. Every key in a row is the same width, so column c starts at
 * left + c × (key + gap) (owner, 2026-11-12: the keyboard is calibrated by its gaps, which are what the eye can judge). Every
 * drawing of the keys is built from these three by [KeyGeometry], so a drawn key sits right over its real key.
 *
 * The defaults are Unihertz's own face-on drawing of the Titan 2 Elite: keys 84 wide with gaps of 24, spanning the whole width of
 * the screen. The Key calibration screen in TitanKeys Debug sets the three ([KeyCalibration]).
 */
class KeyAlignment(val left: Float, val key: Float, val gap: Float) {
    /** Q's left edge and P's right edge, the keys spanning the whole width at the measured shape. */
    constructor(left: Float = DEFAULT_LEFT, right: Float = DEFAULT_RIGHT) :
        this(left, (right - left) * KeyGeometry.KEY / KeyGeometry.SPAN, (right - left) * KeyGeometry.GAP / KeyGeometry.SPAN)

    /** P's right edge. */
    val right get() = left + KeyGeometry.COLUMNS * key + (KeyGeometry.COLUMNS - 1) * gap

    override fun equals(other: Any?) = other is KeyAlignment && other.left == left && other.key == key && other.gap == gap
    override fun hashCode() = (left.hashCode() * 31 + key.hashCode()) * 31 + gap.hashCode()

    companion object {
        const val DEFAULT_LEFT = 0f
        const val DEFAULT_RIGHT = 1f
        val DEFAULT = KeyAlignment()

        /**
         * The calibration screen's reach, in pixels of the screen, either side of the measured value: a key's width ±16 px, a gap's
         * ±12 px, and the shift ±40 px. Far more than any phone is off by.
         */
        const val KEY_REACH = 16f
        const val GAP_REACH = 12f
        const val SHIFT_REACH = 40f
        /** The sliders, and the − and + buttons, move in half pixels: the owner tunes by eye. */
        const val STEP = .5f

        /** Steps a slider has for a reach of [reach] pixels either way. */
        fun steps(reach: Float) = (2 * reach / STEP).roundToInt()

        /** A value [default] ± [reach] pixels, as the slider's [progress], back in pixels, to the half pixel. */
        fun pixelsAt(default: Float, reach: Float, progress: Int) = default - reach + progress * STEP

        /** The slider's place for [pixels]. */
        fun progressOf(default: Float, reach: Float, pixels: Float) = ((pixels - default + reach) / STEP).roundToInt().coerceIn(0, steps(reach))

        /**
         * A slider's value in words: [pixels] to the half pixel, and how far that is from the [measured] value, so the owner sees
         * both what it is and how far it's been moved.
         */
        fun pixelText(pixels: Float, measured: Float): String {
            val off = Math.round((pixels - measured) * 2) / 2f
            val value = Math.round(pixels * 2) / 2f
            return "${halves(value)} px" + when {
                off == 0f -> ", measured"
                off > 0f -> ", +${halves(off)}"
                else -> ", −${halves(-off)}"
            }
        }

        private fun halves(value: Float) = if (value % 1f == 0f) value.toInt().toString() else value.toString()

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
