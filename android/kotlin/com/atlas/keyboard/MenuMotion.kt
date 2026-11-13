package com.atlas.keyboard

import kotlin.math.PI
import kotlin.math.sin

/**
 * How the word menu's buttons change icon: never a snap (owner, 2026-11-12). The word's own effects, the book, the hearts, the
 * strike and the rest, are [MenuScene]'s, from the approved ladder v4.
 */
object MenuMotion {
    /** One layer of a button's icon while it changes: which icon, how strongly and how big, how far down, how orange (0 is the
     * text's colour, 1 the accent), and how much of a filling heart is filled, from the bottom. */
    data class Layer(val button: MenuKeys.Button?, val alpha: Float, val scale: Float = 1f, val drop: Float = 0f, val orange: Float,
                     val fill: Float = 1f)

    /** How long a button's icon takes to change. */
    const val ICON_MILLIS = 300L

    /** The colour a button's icon rests at: the filled heart of a favourite is orange, every other icon the text's colour. */
    fun restingOrange(button: MenuKeys.Button?) = if (button == MenuKeys.Button.Favourited) 1f else 0f

    /**
     * A button's icon changing from [from] to [to], [elapsed] ms in: never a snap (owner, 2026-11-12). The outline heart fills
     * from the bottom as it turns orange, with a small pop; the filled heart drains back to white; anything else cross-fades with a
     * small scale. Null [from] or [to] is an empty button. With motion off, it is a plain cross-fade.
     */
    fun icon(from: MenuKeys.Button?, to: MenuKeys.Button?, elapsed: Long, motion: Boolean = true): List<Layer> {
        val t = (elapsed.toFloat() / ICON_MILLIS).coerceIn(0f, 1f)
        if (t >= 1f || from == to) return listOf(Layer(to, 1f, orange = restingOrange(to)))
        val heart = MenuKeys.Button.Favourite
        val full = MenuKeys.Button.Favourited
        if (motion && from == heart && to == full) {
            // The fill rises and the colour warms, with a small pop at the end.
            val pop = if (t > .4f) 1f + .15f * sin(PI.toFloat() * (t - .4f) / .6f) else 1f
            val warm = (t / .8f).coerceAtMost(1f)
            return listOf(
                Layer(heart, 1f - ((t - .65f) / .35f).coerceIn(0f, 1f), pop, orange = warm),
                Layer(full, 1f, pop, orange = warm, fill = (t / .7f).coerceAtMost(1f)),
            )
        }
        if (motion && from == full && to == heart) {
            // The orange drains away, and the fill goes with it, back to the white outline.
            val drain = 1f - (t / .75f).coerceAtMost(1f)
            return listOf(
                Layer(full, 1f - ((t - .35f) / .65f).coerceIn(0f, 1f), orange = drain),
                Layer(heart, ((t - .35f) / .65f).coerceIn(0f, 1f), orange = drain),
            )
        }
        val out = (t / .6f).coerceAtMost(1f)
        val into = ((t - .25f) / .75f).coerceIn(0f, 1f)
        return listOfNotNull(
            from?.let { Layer(it, 1f - out, if (motion) 1f - .2f * out else 1f, orange = restingOrange(it)) },
            to?.let { Layer(it, into, if (motion) .85f + .15f * into else 1f, if (motion) -3f * (1f - into) else 0f, orange = restingOrange(it)) },
        )
    }
}
