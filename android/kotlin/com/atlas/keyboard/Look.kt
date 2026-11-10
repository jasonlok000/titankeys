package com.atlas.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import com.atlas.engine.TypingSettings

/**
 * The keyboard's colours: the text in black or white on the other, and an accent for the few small marks that come and go, such
 * as the key under a finger. With Orange accents off, the accent is the colour of the text.
 */
internal class Look(val dark: Boolean, val orange: Boolean) {
    val ink = if (dark) Color.WHITE else Color.BLACK
    val paper = if (dark) Color.BLACK else Color.WHITE
    val muted = if (dark) Color.GRAY else Color.DKGRAY
    val accent = if (orange) GuideColours.ORANGE else ink
    /** Text or an icon on [accent]. Black is clearer than white on orange. */
    val onAccent = if (orange) Color.BLACK else paper
    /** A faint wash of [accent], for a mark that stays, such as the open group. */
    val wash = GuideMotion.faded(accent, if (orange) .35f else .25f)

    companion object {
        fun of(context: Context) = Look(
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
            TypingSettings.orangeAccents(context))
    }
}
