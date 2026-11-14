package com.atlas.keyboard

import android.content.Context

/**
 * Every orange TitanKeys draws natively comes from here, and Settings > Orange accents flips it (owner, 2026-11-13: off means
 * "literally everything globally"). Off, the orange is the text's cream. Where orange fills a background behind ink text, as the
 * guide's pass flood, the playground's warm-up and the Next pill do, it becomes cream behind ink, so it stays readable.
 *
 * Nothing outside this file names the orange (tools/checks/accent.test.ts). Ink's screens take the same switch through the Ink
 * patch's accent (appAccent), and the app's own drawings through lib/brand.ts's useAccent. The keyboard's marks take it through
 * [Look], cream too with Orange accents off.
 */
internal object Accent {
    /** The Titan 2 Elite's orange, sampled from Unihertz's product photos. */
    private const val ORANGE = 0xFFEF804E.toInt()
    /** The text's cream, lib/brand.ts's CREAM, which stands in for orange with Orange accents off. */
    private const val CREAM = 0xFFFFF1E6.toInt()
    /** A cream a step darker, for a button on a cream background, where cream on cream wouldn't show. Ink text reads on it. */
    private const val RAISED_CREAM = 0xFFE3D5CB.toInt()

    /** The setting as TypingSettings keeps it. tools/checks/accent.test.ts checks that the two agree. */
    const val PREFERENCES = "typing_settings"
    const val KEY = "orange_accents"

    /** Settings > Orange accents. Every window reads it as it starts ([load]), and the switch sets it at once. */
    @Volatile var on = true

    /** The accent now: orange, or cream with Orange accents off. */
    val colour: Int get() = if (on) ORANGE else CREAM

    /** The accent for a drawing told the setting itself, such as [Look]: orange, or cream. */
    fun of(orange: Boolean) = if (orange) ORANGE else CREAM

    /**
     * A favourite word's colour on the dark bar: orange, or with Orange accents off, the darker cream, so a favourite stays apart
     * from a white saved word and a grey unsaved one. [warm] of the orange is that very grey, #9B938C.
     */
    val favourite: Int get() = if (on) ORANGE else RAISED_CREAM

    /** A button that sits on an accent background, which was cream on orange: off, a darker cream, so it still shows. */
    val raised: Int get() = if (on) CREAM else RAISED_CREAM

    /**
     * A warm colour from a drawing's own palette, such as a flame's yellow or a spark's peach: as it is, or with Orange accents off,
     * cream at the same lightness, so a flame keeps its light and dark parts without any orange.
     */
    fun warm(colour: Int): Int {
        if (on) return colour
        val scale = (lightness(colour) / lightness(CREAM)).coerceAtMost(1f)
        fun channel(shift: Int) = Math.round(((CREAM shr shift) and 0xFF) * scale)
        return (colour and 0xFF000000.toInt()) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun lightness(colour: Int) =
        .2126f * ((colour shr 16) and 0xFF) + .7152f * ((colour shr 8) and 0xFF) + .0722f * (colour and 0xFF)

    /** Reads the setting. Every window TitanKeys draws starts in FixedScale, which calls this, so each starts with it as it is. */
    fun load(context: Context) {
        on = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }
}
