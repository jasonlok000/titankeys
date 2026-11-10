package com.atlas.keyboard

import android.view.RoundedCorner
import android.view.WindowInsets
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * The rounded bottom corners of the screen, which cut off what is drawn in them. The keyboard sits on the bottom edge, so
 * content near its sides keeps the distance that [inset] gives.
 */
class Corners(private val radius: Float = 0f) {
    /** How far content [above] pixels over the bottom edge of the screen must keep from each side. */
    fun inset(above: Float): Int {
        if (radius <= 0f || above >= radius) return 0
        val down = radius - above.coerceAtLeast(0f)
        return ceil(radius - sqrt(radius * radius - down * down)).toInt()
    }

    companion object {
        val None = Corners()

        fun of(insets: WindowInsets): Corners {
            val radius = listOf(RoundedCorner.POSITION_BOTTOM_LEFT, RoundedCorner.POSITION_BOTTOM_RIGHT)
                .maxOf { insets.getRoundedCorner(it)?.radius ?: 0 }
            return Corners(radius.toFloat())
        }
    }
}
