// The keyboard's own Look and GuideMotion.faded, without Android's Context, for render.sh.
package com.atlas.keyboard

internal class Look(val dark: Boolean, val orange: Boolean) {
    val ink = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    val paper = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    val muted = if (dark) 0xFF888888.toInt() else 0xFF444444.toInt()
    val accent = if (orange) 0xFFEF804E.toInt() else ink
    val onAccent = if (orange) 0xFF000000.toInt() else paper
    val wash = GuideMotion.faded(accent, if (orange) .35f else .25f)
}

internal object GuideMotion {
    fun faded(colour: Int, alpha: Float) = colour and 0x00FFFFFF or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
}
