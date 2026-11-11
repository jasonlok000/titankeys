package com.atlas.keyboard

/**
 * The two parts of GuideKeyboardView.kt that the strip's drawing uses, as they are there, so the render needs none of the guide's
 * view. tests/gesture-render/run.sh checks that they still match.
 */
internal object GuideColours {
    const val ORANGE = 0xFFEF804E.toInt()
}

internal object GuideMotion {
    fun faded(colour: Int, alpha: Float) = colour and 0x00FFFFFF or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
}
