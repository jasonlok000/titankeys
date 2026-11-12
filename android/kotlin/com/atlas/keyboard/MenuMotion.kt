package com.atlas.keyboard

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The word menu's animations (owner, 2026-11-12), all inside the bar's height, roughly symmetric on both sides of the word:
 * - Favourite: the word pops about 6% as 6 to 8 hearts of three sizes burst from behind it, mostly up and out to both sides,
 *   swaying and fading. The haptic lands on the burst.
 * - Unfavourite: a heart cracks over the word, and its halves fall and spin away with gravity. The word gives a tiny shake in time
 *   with the haptic.
 * - Save: a bookmark ribbon slides down onto the word's left edge, bounces once and settles. Unsave: the ribbon tears, and its
 *   halves drift down.
 * - Forget: the word dissolves into a little dust. Opening on a saved word or a favourite: two or three hearts drift up, once.
 *
 * Every particle and mark is orange; the word itself never changes colour. Each word always gets the same layout, seeded by the
 * word. And no button snaps from one icon to another: [icon] morphs them. It is plain numbers, so the checks and the renders draw
 * the same frames as the bar, and a frame depends only on the time, so a quiet loop could run it again later.
 *
 * Places are in dp from the bar's top left. The bar is [BAR] tall, and no particle leaves it.
 */
object MenuMotion {
    const val BAR = 56f

    enum class Shape { Heart, HeartLeft, HeartRight, Ribbon, RibbonLeft, RibbonRight, Dust }

    /** One particle: its centre, size and turn in degrees, how strongly it shows, and whether it is behind the word. */
    data class Sprite(val shape: Shape, val x: Float, val y: Float, val size: Float, val turn: Float, val alpha: Float,
                      val behind: Boolean = false)

    /** What a moment of an animation draws, and what it does to the word: its size, a small sideways shake, and how much shows. */
    data class Frame(val sprites: List<Sprite>, val wordScale: Float = 1f, val wordShift: Float = 0f, val wordAlpha: Float = 1f)

    /** The word on the bar: its left and right ends, the middle of its letters, and the seed of its layout. */
    data class Word(val left: Float, val right: Float, val middle: Float, val seed: Int) {
        val centre get() = (left + right) / 2
        companion object {
            fun seed(word: String) = word.lowercase().hashCode()
        }
    }

    /** How long each animation takes, in ms. */
    fun millis(motion: MenuKeys.Motion) = when (motion) {
        MenuKeys.Motion.Save -> 520L
        MenuKeys.Motion.Favourite -> 620L
        MenuKeys.Motion.Unfavourite -> 640L
        MenuKeys.Motion.Unsave -> 520L
        MenuKeys.Motion.Forget -> 420L
        MenuKeys.Motion.Echo -> 520L
    }

    /** When the animation peaks, in ms from its start: its haptic plays then, so what you feel lands with what you see. */
    fun peak(motion: MenuKeys.Motion) = when (motion) {
        MenuKeys.Motion.Save -> (millis(motion) * RIBBON_LANDS).toLong()
        MenuKeys.Motion.Unfavourite -> (millis(motion) * CRACK).toLong()
        MenuKeys.Motion.Unsave -> (millis(motion) * TEAR).toLong()
        else -> 0L
    }

    /** The frame of [motion] at [elapsed] ms, over [word]. Nothing once it is over. */
    fun frame(motion: MenuKeys.Motion, elapsed: Long, word: Word): Frame {
        val total = millis(motion)
        if (elapsed < 0 || elapsed >= total) return Frame(emptyList())
        val t = elapsed.toFloat() / total
        val random = Random(word.seed * 31 + motion.ordinal)
        val frame = when (motion) {
            MenuKeys.Motion.Favourite -> favourite(t, word, random)
            MenuKeys.Motion.Unfavourite -> unfavourite(t, word, random)
            MenuKeys.Motion.Save -> Frame(listOf(Sprite(Shape.Ribbon, word.left - RIBBON_OFF, ribbonY(t, word), RIBBON, 0f, fade(t, .7f))))
            MenuKeys.Motion.Unsave -> unsave(t, word, random)
            MenuKeys.Motion.Forget -> dust(t, word, random)
            MenuKeys.Motion.Echo -> echo(t, word, random)
        }
        return frame.copy(sprites = frame.sprites.map { it.copy(y = it.y.coerceIn(it.size / 2, BAR - it.size / 2)) })
    }

    /** The word pops, and hearts burst from behind it, up and out to both sides, swaying as they rise and fade. */
    private fun favourite(t: Float, word: Word, random: Random): Frame {
        val count = 6 + random.nextInt(3)
        val width = word.right - word.left
        val hearts = (0 until count).mapNotNull { index ->
            val right = index % 2 == 0
            val size = HEART_SIZES[random.nextInt(HEART_SIZES.size)]
            val delay = random.nextFloat() * .18f
            val from = word.centre + (if (right) 1 else -1) * width * (.1f + .25f * random.nextFloat())
            val to = (if (right) word.right else word.left) + (if (right) 1 else -1) * (4f + 18f * random.nextFloat())
            val rise = 12f + 12f * random.nextFloat()
            val own = ((t - delay) / (1f - delay)).takeIf { it in 0f..1f } ?: return@mapNotNull null
            val out = 1f - (1f - own) * (1f - own)
            Sprite(Shape.Heart, from + (to - from) * out + 2f * sin(own * 2.5f * PI.toFloat() + index), word.middle - rise * out, size,
                (if (right) 8f else -8f) * own, (own / .12f).coerceAtMost(1f) * fade(own, .55f), behind = true)
        }
        return Frame(hearts, wordScale = if (t < POP) 1f + .06f * sin(PI.toFloat() * t / POP) else 1f)
    }

    /** A heart over the word cracks, and its halves fall and spin away. The word shakes a little as it cracks. */
    private fun unfavourite(t: Float, word: Word, random: Random): Frame {
        val x = word.centre
        val y = word.middle - 13f
        if (t < CRACK) return Frame(listOf(Sprite(Shape.Heart, x, y, HEART, 0f, (t / .08f).coerceAtMost(1f))))
        val s = (t - CRACK) / (1f - CRACK)
        val spinLeft = 40f + 25f * random.nextFloat()
        val spinRight = 40f + 25f * random.nextFloat()
        val fall = 30f * s * s
        val alpha = fade(s, .5f)
        val halves = listOf(
            Sprite(Shape.HeartLeft, x - 2f - 9f * s, y + fall, HEART, -spinLeft * s, alpha),
            Sprite(Shape.HeartRight, x + 2f + 10f * s, y + fall * .92f, HEART, spinRight * s, alpha),
        )
        val shake = if (s < SHAKE) 1.4f * sin(s / SHAKE * 4 * PI.toFloat()) * (1f - s / SHAKE) else 0f
        return Frame(halves, wordShift = shake)
    }

    /** Where the ribbon is as it slides down onto the word's left edge, bounces once and settles. */
    private fun ribbonY(t: Float, word: Word): Float {
        val land = word.middle - 4f
        val top = RIBBON / 2
        if (t < RIBBON_LANDS) {
            val fall = t / RIBBON_LANDS
            return top + (land - top) * fall * fall
        }
        val bounce = (t - RIBBON_LANDS) / .2f
        return land - if (bounce < 1f) 3f * sin(PI.toFloat() * bounce) else 0f
    }

    /** The ribbon at the word's left edge tears, and its halves drift down and fade. */
    private fun unsave(t: Float, word: Word, random: Random): Frame {
        val x = word.left - RIBBON_OFF
        val y = word.middle - 4f
        if (t < TEAR) return Frame(listOf(Sprite(Shape.Ribbon, x, y, RIBBON, 0f, (t / .1f).coerceAtMost(1f))))
        val s = (t - TEAR) / (1f - TEAR)
        val drift = 14f * s * s + 4f * s
        val alpha = fade(s, .45f)
        return Frame(listOf(
            Sprite(Shape.RibbonLeft, x - 2f - 4f * s, y + drift, RIBBON, -(14f + 8f * random.nextFloat()) * s, alpha),
            Sprite(Shape.RibbonRight, x + 2f + 4f * s, y + drift * .9f, RIBBON, (14f + 8f * random.nextFloat()) * s, alpha),
        ))
    }

    /** The word dissolves into a little dust, drifting up and out from where its letters were. */
    private fun dust(t: Float, word: Word, random: Random): Frame {
        val count = 10 + random.nextInt(5)
        val specks = (0 until count).map { index ->
            val x = word.left + (word.right - word.left) * (index + random.nextFloat()) / count
            val y = word.middle + (random.nextFloat() - .5f) * 10f
            val away = (x - word.centre) / (word.right - word.left + 1f) * 10f
            Sprite(Shape.Dust, x + away * t, y - (5f + 6f * random.nextFloat()) * t, 2f + random.nextFloat() * 1.5f, 0f, fade(t, .3f) * .9f)
        }
        return Frame(specks, wordAlpha = (1f - t / .5f).coerceIn(0f, 1f))
    }

    /** As the menu opens on a saved word or a favourite: two or three small hearts drift up from its ends, once, softly. */
    private fun echo(t: Float, word: Word, random: Random): Frame {
        val count = 2 + random.nextInt(2)
        val hearts = (0 until count).mapNotNull { index ->
            val right = index % 2 == 0
            val delay = index * .12f + random.nextFloat() * .06f
            val own = ((t - delay) / (1f - delay)).takeIf { it in 0f..1f } ?: return@mapNotNull null
            val x = (if (right) word.right + 6f else word.left - 6f) + (if (right) 1 else -1) * 3f * own
            Sprite(Shape.Heart, x, word.middle - 4f - (8f + 4f * random.nextFloat()) * own, 7f + 2f * random.nextFloat(), 0f,
                (own / .2f).coerceAtMost(1f) * fade(own, .4f) * .75f)
        }
        return Frame(hearts)
    }

    // The ambient status (owner, 2026-11-12): while the menu rests on a favourite, a faint heart drifts up now and then from
    // alternating sides of the word; on a saved word a faint ribbon rests at its left edge and sways now and then; on a word that
    // isn't saved, nothing. Quiet on purpose: faint, slow, and still where motion is off.

    /** How often a favourite's heart drifts up, how long each drift takes, how often the saved ribbon sways, and for how long. */
    const val AMBIENT_HEART_EVERY = 2500L
    const val AMBIENT_HEART_MILLIS = 1600L
    const val AMBIENT_SWAY_EVERY = 4000L
    const val AMBIENT_SWAY_MILLIS = 900L
    /** How strongly the ambient marks show: faint. */
    const val AMBIENT_ALPHA = .35f

    /**
     * The ambient marks at [elapsed] ms since the menu came to rest at [step] over [word]. With motion off ([still]) they sit
     * still: a faint heart by a favourite, the faint ribbon by a saved word.
     */
    fun ambient(step: MenuKeys.Step, elapsed: Long, word: Word, still: Boolean): List<Sprite> = when (step) {
        MenuKeys.Step.New -> emptyList()
        MenuKeys.Step.Saved -> {
            val into = elapsed % AMBIENT_SWAY_EVERY
            val sway = if (still || into >= AMBIENT_SWAY_MILLIS) 0f else {
                val p = into.toFloat() / AMBIENT_SWAY_MILLIS
                8f * sin(2 * PI.toFloat() * p) * (1f - p)
            }
            listOf(Sprite(Shape.Ribbon, word.left - RIBBON_OFF, word.middle - 4f, RIBBON, sway, AMBIENT_ALPHA))
        }
        MenuKeys.Step.Favourite -> {
            if (still) listOf(Sprite(Shape.Heart, word.right + 7f, word.middle - 6f, 8f, 0f, AMBIENT_ALPHA))
            else {
                val cycle = elapsed / AMBIENT_HEART_EVERY
                val into = elapsed % AMBIENT_HEART_EVERY
                if (into >= AMBIENT_HEART_MILLIS) emptyList() else {
                    val p = into.toFloat() / AMBIENT_HEART_MILLIS
                    val right = (cycle + word.seed).mod(2L) == 0L
                    val x = (if (right) word.right + 7f else word.left - 7f) + (if (right) 1f else -1f) * 2f * p
                    listOf(Sprite(Shape.Heart, x, word.middle - 2f - 12f * p, 8f, 0f, AMBIENT_ALPHA * (p / .2f).coerceAtMost(1f) * fade(p, .5f))
                        .let { it.copy(y = it.y.coerceIn(it.size / 2, BAR - it.size / 2)) })
                }
            }
        }
    }

    /** How long until the ambient marks next move, in ms: 0 while they move, so the bar redraws only then. */
    fun ambientWait(step: MenuKeys.Step, elapsed: Long, still: Boolean): Long = when {
        still || step == MenuKeys.Step.New -> Long.MAX_VALUE
        step == MenuKeys.Step.Saved -> (elapsed % AMBIENT_SWAY_EVERY).let { if (it < AMBIENT_SWAY_MILLIS) 0L else AMBIENT_SWAY_EVERY - it }
        else -> (elapsed % AMBIENT_HEART_EVERY).let { if (it < AMBIENT_HEART_MILLIS) 0L else AMBIENT_HEART_EVERY - it }
    }

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

    /** Full until [from], then fading to nothing at 1. */
    private fun fade(t: Float, from: Float) = if (t <= from) 1f else (1f - (t - from) / (1f - from)).coerceIn(0f, 1f)

    /** The ribbon's size and how far left of the word it sits, the hearts', and when each animation peaks, as a part of it. */
    private const val RIBBON = 13f
    private const val RIBBON_OFF = 7f
    private const val HEART = 15f
    private val HEART_SIZES = floatArrayOf(7f, 10f, 13f)
    private const val POP = .3f
    private const val CRACK = .2f
    private const val SHAKE = .3f
    private const val RIBBON_LANDS = .4f
    private const val TEAR = .18f
}
