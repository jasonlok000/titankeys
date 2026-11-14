// The keyboard's own Look and GuideMotion.faded, without Android's Context, for render.sh.
package com.atlas.keyboard

internal class Look(val orange: Boolean) {
    val ink = 0xFFFFFFFF.toInt()
    val paper = 0xFF000000.toInt()
    val muted = 0xFF888888.toInt()
    val accent = if (orange) 0xFFEF804E.toInt() else 0xFFFFF1E6.toInt()
    val onAccent = 0xFF000000.toInt()
    val wash = GuideMotion.faded(accent, if (orange) .35f else .25f)
}

internal object GuideMotion {
    fun faded(colour: Int, alpha: Float) = colour and 0x00FFFFFF or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
}
