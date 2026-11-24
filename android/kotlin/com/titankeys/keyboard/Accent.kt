package com.titankeys.keyboard

import android.content.Context
import android.os.Build
import com.titankeys.engine.TypingSettings

/**
 * Every accent TitanKeys draws natively comes from here, and Settings > Accent colour picks it (#111). Off means "literally
 * everything globally" (owner, 2026-11-13): the accent is the text's cream. Where the accent fills a background behind ink text,
 * as the guide's pass flood, the playground's warm-up and the Next pill do, ink text stays on it, so every choice has to read
 * under ink and over black.
 *
 * Must stay readable (owner, #111): everything TitanKeys draws is dark, so every preset sits in one band of lightness
 * ([MIN_LIGHTNESS] to [MAX_LIGHTNESS]). In it, the accent is at least 4.5:1 as text on black, the cards' surface and the support
 * page's panels, ink text is at least 4.5:1 on it, and a cream button on it shows as well as on orange. tools/checks/accent.test.ts
 * checks every preset. The phone's own colour (Material You) is pulled into the same band ([fit]).
 *
 * Nothing outside this file names the orange (tools/checks/accent.test.ts). Ink's screens take the accent through the Ink patch's
 * accent (appAccent), the app's own drawings through lib/brand.ts's useAccent (state's accent), and the keyboard's marks through
 * [Look].
 */
internal object Accent {
    /** The Titan 2 Elite's orange, sampled from Unihertz's product photos. */
    private const val ORANGE = 0xFFEF804E.toInt()
    /** The text's cream, lib/brand.ts's CREAM, which stands in for the accent with Accent colour off. */
    private const val CREAM = 0xFFFFF1E6.toInt()
    /** A cream a step darker, for a button on a cream background, where cream on cream wouldn't show. Ink text reads on it. */
    private const val RAISED_CREAM = 0xFFE3D5CB.toInt()

    /** The choices, in Settings' order. Their names are in lib/setting-names.ts (accentChoices). */
    const val OFF = "off"
    const val DEFAULT = "orange"
    const val PHONE = "phone"
    /** The presets, each checked against every dark surface (tools/checks/accent.test.ts). */
    val PRESETS = linkedMapOf(
        "orange" to ORANGE,
        "blue" to 0xFF4C9EEB.toInt(),
        "teal" to 0xFF22AFA6.toInt(),
        "green" to 0xFF4DB37A.toInt(),
        "purple" to 0xFFA88AEE.toInt(),
        "pink" to 0xFFEA79A9.toInt(),
        "gold" to 0xFFC99A22.toInt(),
    )

    /** WCAG relative luminance band every accent keeps (see the class note). */
    private const val MIN_LIGHTNESS = 0.22
    private const val MAX_LIGHTNESS = 0.36

    /** The setting as TypingSettings keeps it. tools/checks/accent.test.ts checks that the two agree. */
    const val PREFERENCES = "typing_settings"
    const val KEY = "accent_colour"
    /** The old switch, Orange accents: false becomes Off, true or unset Orange (TypingSettings.accentColour). */
    const val OLD_KEY = "orange_accents"

    /** Settings > Accent colour. Every window reads it as it starts ([load]), and the choice sets it at once ([set]). */
    @Volatile var choice = DEFAULT
        private set
    @Volatile private var current = ORANGE

    /** Any colour at all, not Off. */
    val on: Boolean get() = choice != OFF

    /** The plain orange, which Ink's theme already draws, so Ink needs no override. */
    val isDefault: Boolean get() = choice == DEFAULT

    /** The accent now: the chosen colour, or cream with Accent colour off. */
    val colour: Int get() = if (on) current else CREAM

    /** The accent for a drawing told whether accents are on, such as [Look]: the colour, or cream. */
    fun of(on: Boolean) = if (on) current else CREAM

    /** The text's cream whatever the setting, for a mark that is cream by design, such as the idle games' floor ([Look.floorLine]). */
    const val TEXT_CREAM = CREAM

    /**
     * A favourite word's colour on the dark bar: the accent, or with Accent colour off, the darker cream, so a favourite stays apart
     * from a white saved word and a grey unsaved one.
     */
    val favourite: Int get() = if (on) current else RAISED_CREAM

    /** A button that sits on an accent background, which was cream on orange: off, a darker cream, so it still shows. */
    val raised: Int get() = if (on) CREAM else RAISED_CREAM

    /**
     * A warm colour from a drawing's own palette, such as a flame's yellow or a spark's peach: as it is with orange, or otherwise the
     * accent (cream when off) at the same lightness, so a flame keeps its light and dark parts in the chosen colour.
     */
    fun warm(colour: Int): Int {
        if (isDefault) return colour
        val target = this.colour
        val scale = (lightness(colour) / lightness(target)).coerceAtMost(1f)
        fun channel(shift: Int) = Math.round(((target shr shift) and 0xFF) * scale)
        return (colour and 0xFF000000.toInt()) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** The guide's decorations: the accent, or with Accent colour off, cream at the orange's lightness, so nothing turns brighter. */
    val decor: Int get() = if (on) current else warm(ORANGE)

    private fun lightness(colour: Int) =
        .2126f * ((colour shr 16) and 0xFF) + .7152f * ((colour shr 8) and 0xFF) + .0722f * (colour and 0xFF)

    /** Reads the setting. Every window TitanKeys draws starts in FixedScale, which calls this, so each starts with it as it is. */
    fun load(context: Context) = set(context, TypingSettings.accentColour(context))

    /** Takes [id] at once, as Settings picks it. An unknown id is the default. */
    fun set(context: Context, id: String) {
        current = when (id) {
            OFF -> CREAM
            PHONE -> phone(context)
            else -> PRESETS[id] ?: ORANGE
        }
        choice = if (id == OFF || id == PHONE || id in PRESETS) id else DEFAULT
    }

    /** The colour as "#RRGGBB", for the app's screens (state's accent) and the support page. */
    fun hex(): String = String.format("#%06X", colour and 0xFFFFFF)

    /** The phone's Material You accent, pulled into the readable band. Before Android 12 there is none, so it's orange. */
    private fun phone(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return ORANGE
        val system = runCatching { context.getColor(android.R.color.system_accent1_200) }.getOrNull() ?: return ORANGE
        return fit(system)
    }

    /**
     * [colour] blended towards white while it is too dark, or towards black while it is too light, until its luminance is inside
     * the band every preset keeps. Grey or not, it ends up readable on every surface the presets are checked on.
     */
    fun fit(colour: Int): Int {
        var result = colour or 0xFF000000.toInt()
        repeat(64) {
            val l = luminance(result)
            result = when {
                l < MIN_LIGHTNESS + .005 -> GuideMotion.blend(result, 0xFFFFFFFF.toInt(), .04f)
                l > MAX_LIGHTNESS - .005 -> GuideMotion.blend(result, 0xFF000000.toInt(), .04f)
                else -> return result
            }
        }
        return ORANGE
    }

    /** WCAG relative luminance, 0 to 1. */
    fun luminance(colour: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((colour shr shift) and 0xFF) / 255.0
            return if (c <= .04045) c / 12.92 else Math.pow((c + .055) / 1.055, 2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }
}
