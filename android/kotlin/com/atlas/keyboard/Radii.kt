package com.atlas.keyboard

import android.content.res.Resources
import kotlin.math.min

/**
 * The corner radii that every rounded thing in TitanKeys uses, so that the app, the keyboard and the guide match.
 * lib/radii.ts has the same numbers for the app's screens, and tools/checks/radii.test.ts keeps the two the same.
 *
 * The numbers are pixels in Ink's 1080-pixel-wide frame, which Ink scales to the screen. [px] scales them the same way here,
 * so a radius looks the same in the app and on the keyboard whatever the screen's density.
 */
object Radii {
    /** Chips, key highlights, clip fields, the keys in drawings. */
    const val SMALL = 16
    /** Text fields, hints, cards, the top of the keyboard. */
    const val MEDIUM = 32
    /** Buttons. */
    const val LARGE = 56

    private const val FRAME_WIDTH = 1080f

    /** [radius] in this screen's pixels. */
    fun px(resources: Resources, radius: Int): Float {
        val metrics = resources.displayMetrics
        return radius * min(metrics.widthPixels, metrics.heightPixels) / FRAME_WIDTH
    }
}
