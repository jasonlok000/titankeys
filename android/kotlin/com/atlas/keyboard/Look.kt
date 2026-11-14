package com.atlas.keyboard

import android.content.Context
import android.graphics.Color
import com.atlas.engine.TypingSettings

/**
 * The keyboard's colours: always the dark look, white text on black, whatever the phone's dark mode (owner, 2026-11-13: "I never
 * know it follows light mode. Make it always black. The app is always in its current dark mode settings."). The bar's black meets
 * the screen's own black border, so the bar reads as part of the edge. The bar, the panels, the word menu and exact typing's bar
 * all take it from here, and the app is dark too.
 *
 * An accent for the few small marks that come and go, such as the key under a finger: orange, or with Orange accents off, the
 * cream every other drawing uses ([Accent]; bug hunt 2.1, #36).
 */
internal class Look(val orange: Boolean) {
    val ink = Color.WHITE
    val paper = Color.BLACK
    val muted = Color.GRAY
    val accent = Accent.of(orange)
    /** Text or an icon on [accent]. Black is clearer than white on orange, and on cream. */
    val onAccent = Color.BLACK
    /**
     * The idle games' floor (owner, 2026-11-13, of the phone preview: "let's do 3"): the text's cream at 55%. It's never orange,
     * so Orange accents changes nothing.
     */
    val floorLine = (Accent.TEXT_CREAM and 0xFFFFFF) or (FLOOR_ALPHA shl 24)
    /** A faint wash of [accent], for a mark that stays, such as the open group. */
    val wash = GuideMotion.faded(accent, if (orange) .35f else .25f)

    companion object {
        /** 55%, of 255. */
        private const val FLOOR_ALPHA = 140

        fun of(context: Context) = Look(TypingSettings.orangeAccents(context).also { Accent.on = it })
    }
}
