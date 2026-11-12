package com.atlas.keyboard

import kotlin.math.PI
import kotlin.math.sin

/**
 * The word menu's little animations, in place of a badge (owner, 2026-11-12): a bookmark drops onto a saved word and settles,
 * hearts burst up off a new favourite, a heart cracks and its halves fall for Unfavourite, a bookmark tears and falls for Unsave,
 * and as the menu opens on a saved word or a favourite, the bookmark flicks or a few hearts drift up, once. Each is quick, light,
 * and over by its end: nothing stays, and the buttons show the step. The word never moves for one.
 *
 * Plain numbers, so the checks and the renders draw the same frames as the bar. Everything is in dp from the bar's top left, at
 * [anchorX], [anchorY]: just past the end of the word, a little above its middle. [Sprite]s never leave the bar's height.
 */
object MenuMotion {
    enum class Shape { Heart, HeartLeft, HeartRight, Bookmark, BookmarkLeft, BookmarkRight }

    /** One shape to draw: its centre, its size, its turn in degrees, and how strongly, from 0 to 1. */
    data class Sprite(val shape: Shape, val x: Float, val y: Float, val size: Float, val turn: Float, val alpha: Float)

    /** How long each animation takes, in ms: none over 600 (owner). */
    fun millis(motion: MenuKeys.Motion) = when (motion) {
        MenuKeys.Motion.BookmarkDrop -> 520L
        MenuKeys.Motion.HeartBurst -> 600L
        MenuKeys.Motion.HeartCrack -> 560L
        MenuKeys.Motion.BookmarkTear -> 560L
        MenuKeys.Motion.BookmarkFlick -> 380L
        MenuKeys.Motion.HeartDrift -> 520L
    }

    /** The bar's height, in dp. */
    const val BAR = 56f

    /** What [motion] draws at [elapsed] ms, past the word's end at [anchorX], [anchorY]. Empty once it is over. */
    fun frame(motion: MenuKeys.Motion, elapsed: Long, anchorX: Float, anchorY: Float): List<Sprite> {
        val total = millis(motion)
        if (elapsed < 0 || elapsed >= total) return emptyList()
        val t = elapsed.toFloat() / total
        val sprites = when (motion) {
            MenuKeys.Motion.BookmarkDrop -> {
                // It falls from the top of the bar, lands with a small bounce, rests, and fades.
                val fall = (t / .45f).coerceAtMost(1f)
                val landed = anchorY
                val from = BOOKMARK / 2 + 1
                val bounce = if (t in .45f..0.7f) -3f * sin(PI.toFloat() * (t - .45f) / .25f) else 0f
                val y = from + (landed - from) * fall * fall + bounce
                listOf(Sprite(Shape.Bookmark, anchorX, y, BOOKMARK, 0f, fade(t, .75f)))
            }
            MenuKeys.Motion.HeartBurst -> HEARTS.mapNotNull { (dx, rise, size, delay) ->
                // Each heart starts a little after the last, rises, sways a touch, and fades.
                val own = ((t - delay) / (1f - delay)).takeIf { it in 0f..1f } ?: return@mapNotNull null
                val up = 1f - (1f - own) * (1f - own)
                Sprite(Shape.Heart, anchorX + dx + 2f * sin(own * 2 * PI.toFloat()), anchorY - rise * up, size, 0f,
                    (own / .15f).coerceAtMost(1f) * fade(own, .5f))
            }
            MenuKeys.Motion.HeartCrack -> broken(t, anchorX, anchorY, Shape.Heart, Shape.HeartLeft, Shape.HeartRight, HEART)
            MenuKeys.Motion.BookmarkTear -> broken(t, anchorX, anchorY, Shape.Bookmark, Shape.BookmarkLeft, Shape.BookmarkRight, BOOKMARK)
            MenuKeys.Motion.BookmarkFlick -> {
                // It pops in, flicks once, and goes: a reminder, not a mark.
                val size = BOOKMARK * (.6f + .4f * (t / .3f).coerceAtMost(1f))
                val turn = -14f * sin(PI.toFloat() * ((t - .2f) / .5f).coerceIn(0f, 1f))
                listOf(Sprite(Shape.Bookmark, anchorX, anchorY, size, turn, (t / .15f).coerceAtMost(1f) * fade(t, .55f) * SUBTLE))
            }
            MenuKeys.Motion.HeartDrift -> HEARTS.take(3).mapNotNull { (dx, rise, size, delay) ->
                val own = ((t - delay) / (1f - delay)).takeIf { it in 0f..1f } ?: return@mapNotNull null
                Sprite(Shape.Heart, anchorX + dx * .6f, anchorY - rise * .6f * own, size * .8f, 0f,
                    (own / .2f).coerceAtMost(1f) * fade(own, .4f) * SUBTLE)
            }
        }
        // Never past the bar's top or bottom.
        return sprites.map { it.copy(y = it.y.coerceIn(it.size / 2, BAR - it.size / 2)) }
    }

    /** A whole shape shakes for a moment, then splits: its halves tip apart and fall, fading. */
    private fun broken(t: Float, x: Float, y: Float, whole: Shape, left: Shape, right: Shape, size: Float): List<Sprite> {
        if (t < .25f) return listOf(Sprite(whole, x + 1.2f * sin(t * 60f), y, size, 0f, (t / .1f).coerceAtMost(1f)))
        val split = (t - .25f) / .75f
        val fall = 16f * split * split
        val apart = 3f + 4f * split
        val alpha = fade(split, .35f)
        return listOf(
            Sprite(left, x - apart, y + fall, size, -28f * split, alpha),
            Sprite(right, x + apart, y + fall, size, 28f * split, alpha),
        )
    }

    /** Full until [from], then fading to nothing at 1. */
    private fun fade(t: Float, from: Float) = if (t <= from) 1f else (1f - (t - from) / (1f - from)).coerceIn(0f, 1f)

    /** The shapes' sizes, in dp, and how strong the opening replay is. */
    private const val BOOKMARK = 14f
    private const val HEART = 14f
    private const val SUBTLE = .75f

    /** The burst's hearts: across from the anchor, how far each rises, its size and its delay, as a part of the whole. */
    private data class Heart(val dx: Float, val rise: Float, val size: Float, val delay: Float)

    private val HEARTS = listOf(
        Heart(0f, 20f, 11f, 0f),
        Heart(-9f, 16f, 8f, .08f),
        Heart(8f, 18f, 9f, .14f),
        Heart(-3f, 23f, 7f, .22f),
        Heart(5f, 14f, 8f, .3f),
    )
}
