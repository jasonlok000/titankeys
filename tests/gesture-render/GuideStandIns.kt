package com.atlas.keyboard

/**
 * The part of GuideKeyboardView.kt that the strip's drawing uses, as it is there, so the render needs none of the guide's view.
 * tests/gesture-render/run.sh checks that it still matches. The orange comes from the real Accent.
 */
internal object GuideMotion {
    fun faded(colour: Int, alpha: Float) = colour and 0x00FFFFFF or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
}
