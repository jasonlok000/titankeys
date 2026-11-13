package com.atlas.keyboard

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The word menu's animations (owner, 2026-11-12), all inside the bar's height, roughly symmetric on both sides of the word:
 * - Favourite: the word pops about 6% as 6 to 8 hearts of three sizes burst from behind it, mostly up and out to both sides,
 *   swaying and fading. The haptic lands on the burst.
 * - Unfavourite: a zigzag crack draws down a heart over the word in 120 ms; the halves tilt apart as 4 tiny shards spray out,
 *   then fall with gravity, spinning a little, and fade. The word dips 2 dp and comes back, with the haptic on the crack.
 * - Save: the word pops as 8 to 10 bold four-point sparkles burst out from it in a ring, a little bigger at first, then drift up
 *   and fade: Favourite's physics, in the "kept, it shines" shape (design v2, section 4). The haptic lands on the burst.
 * - Unsave: a bookmark breaks over the word as the heart does, quieter (owner, 2026-11-12: sparkles have nothing to do with
 *   unsaving): its crack draws down, the halves tilt apart with 2 shards and fall, with no dip of the word.
 * - Forget: the word dissolves into a little dust. Opening on a saved word or a favourite: two or three hearts drift up, once.
 * - At rest, the ambient status: a faint heart drifts up now and then by a favourite, a faint sparkle twinkles by a saved word.
 *
 * Every particle and mark is orange; the word itself never changes colour. Each word always gets the same layout, seeded by the
 * word. And no button snaps from one icon to another: [icon] morphs them. It is plain numbers, so the checks and the renders draw
 * the same frames as the bar, and a frame depends only on the time, so a quiet loop could run it again later.
 *
 * Places are in dp from the bar's top left. The bar is [BAR] tall, and no particle leaves it.
 */
object MenuMotion {
    const val BAR = 56f

    /**
     * The particles' shapes. [Crack] is the zigzag drawn down a heart as it breaks, as far down as its [Sprite.amount]; a
     * [Shard] is a tiny splinter of the broken heart.
     */
    enum class Shape { Heart, HeartLeft, HeartRight, Bookmark, BookmarkLeft, BookmarkRight, Dust, Sparkle, Crack, Shard }

    /**
     * One particle: its centre, size and turn in degrees, how strongly it shows, whether it is behind the word, and for a
     * [Shape.Crack] how much of it is drawn, from 0 to 1.
     */
    data class Sprite(val shape: Shape, val x: Float, val y: Float, val size: Float, val turn: Float, val alpha: Float,
                      val behind: Boolean = false, val amount: Float = 1f)

    /**
     * What a moment of an animation draws, and what it does to the word: its size, a small sideways shake, how much shows, and
     * how far it dips, in dp. The word itself never changes colour.
     */
    data class Frame(val sprites: List<Sprite>, val wordScale: Float = 1f, val wordShift: Float = 0f, val wordAlpha: Float = 1f,
                     val wordDrop: Float = 0f)

    /** The word on the bar: its left and right ends, the middle of its letters, and the seed of its layout. */
    data class Word(val left: Float, val right: Float, val middle: Float, val seed: Int) {
        val centre get() = (left + right) / 2
        companion object {
            fun seed(word: String) = word.lowercase().hashCode()
        }
    }

    /** How long each animation takes, in ms. */
    fun millis(motion: MenuKeys.Motion) = when (motion) {
        MenuKeys.Motion.Save -> 620L
        MenuKeys.Motion.Favourite -> 620L
        MenuKeys.Motion.Unfavourite -> 700L
        MenuKeys.Motion.Unsave -> 560L
        MenuKeys.Motion.Forget -> 420L
        MenuKeys.Motion.Echo, MenuKeys.Motion.SavedEcho -> 520L
    }

    /** When the animation peaks, in ms from its start: its haptic plays then, so what you feel lands with what you see. */
    fun peak(motion: MenuKeys.Motion) = when (motion) {
        MenuKeys.Motion.Unfavourite -> CRACK_MILLIS
        MenuKeys.Motion.Unsave -> CRACK_MILLIS
        else -> 0L
    }

    /** The frame of [motion] at [elapsed] ms, over [word]. Nothing once it is over. */
    fun frame(motion: MenuKeys.Motion, elapsed: Long, word: Word): Frame {
        val total = millis(motion)
        if (elapsed < 0 || elapsed >= total) return Frame(emptyList())
        val t = elapsed.toFloat() / total
        val random = Random(word.seed * 31 + motion.ordinal)
        val frame = when (motion) {
            MenuKeys.Motion.Favourite -> burst(t, word, random, Shape.Heart, HEART_TURN)
            MenuKeys.Motion.Unfavourite -> breaking(elapsed, t, word, random, Shape.Heart, Shape.HeartLeft, Shape.HeartRight,
                shards = 4, dips = true, total = total)
            MenuKeys.Motion.Save -> sparkles(t, word, random)
            MenuKeys.Motion.Unsave -> breaking(elapsed, t, word, random, Shape.Bookmark, Shape.BookmarkLeft, Shape.BookmarkRight,
                shards = 2, dips = false, total = total)
            MenuKeys.Motion.Forget -> dust(t, word, random)
            MenuKeys.Motion.Echo -> echo(t, word, random, Shape.Heart)
            MenuKeys.Motion.SavedEcho -> echo(t, word, random, Shape.Sparkle)
        }
        return frame.copy(sprites = frame.sprites.map { it.copy(y = it.y.coerceIn(it.size / 2, BAR - it.size / 2)) })
    }

    /**
     * The word pops, and [shape]s burst from behind it, up and out to both sides, swaying as they rise and fade, each turning by up
     * to [turn] degrees: Favourite's hearts, and Save's ribbons, which flutter a little more.
     */
    private fun burst(t: Float, word: Word, random: Random, shape: Shape, turn: Float): Frame {
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
            Sprite(shape, from + (to - from) * out + 2f * sin(own * 2.5f * PI.toFloat() + index), word.middle - rise * out, size,
                (if (right) turn else -turn) * own, (own / .12f).coerceAtMost(1f) * fade(own, .55f), behind = true)
        }
        return Frame(hearts, wordScale = if (t < POP) 1f + .06f * sin(PI.toFloat() * t / POP) else 1f)
    }

    /**
     * Save: the word pops like Favourite's as bold sparkles burst out from it in a ring, an ellipse round the word, a little bigger
     * at first, then drift up and fade. Every other one goes the other way round, so the ring is even on both sides.
     */
    private fun sparkles(t: Float, word: Word, random: Random): Frame {
        val count = 8 + random.nextInt(3)
        val across = (word.right - word.left) / 2 + 16f
        val sparks = (0 until count).mapNotNull { index ->
            // Evenly round the ring, nudged a little so no two words look the same.
            val angle = (index + .5f + (random.nextFloat() - .5f) * .4f) / count * 2 * PI.toFloat()
            val reach = .85f + .3f * random.nextFloat()
            val size = SPARKLE_SIZES[random.nextInt(SPARKLE_SIZES.size)]
            val delay = random.nextFloat() * .08f
            val drift = 8f + 6f * random.nextFloat()
            val own = ((t - delay) / (1f - delay)).takeIf { it in 0f..1f } ?: return@mapNotNull null
            // Out fast, in the first 35%, then a slow drift up as it fades.
            val out = (own / .35f).coerceAtMost(1f).let { 1f - (1f - it) * (1f - it) }
            val up = ((own - .35f) / .65f).coerceIn(0f, 1f)
            val x = word.centre + kotlin.math.cos(angle) * across * reach * out
            val y = word.middle + kotlin.math.sin(angle) * RING_DOWN * reach * out - drift * up
            Sprite(Shape.Sparkle, x, y, size * (1.25f - .25f * out), 20f * own * (if (index % 2 == 0) 1 else -1),
                (own / .08f).coerceAtMost(1f) * fade(own, .5f), behind = true)
        }
        return Frame(sparks, wordScale = if (t < POP) 1f + .06f * sin(PI.toFloat() * t / POP) else 1f)
    }

    /**
     * A [whole] shape over the word breaks: a zigzag crack draws down it in 120 ms; its halves tilt apart as tiny [shards] spray
     * out, then fall with gravity, spinning a little, and fade, behind the word. Unfavourite breaks a heart and the word [dips]
     * 2 dp and comes back; Unsave breaks a bookmark, more quietly.
     */
    private fun breaking(elapsed: Long, t: Float, word: Word, random: Random, whole: Shape, left: Shape, right: Shape, shards: Int,
                         dips: Boolean, total: Long): Frame {
        val x = word.centre
        val y = word.middle - 15f
        val crack = CRACK_MILLIS.toFloat() / total
        val dip = if (dips && t in crack - .05f..crack + .3f) DIP * sin(PI.toFloat() * (t - crack + .05f) / .35f) else 0f
        if (elapsed < CRACK_MILLIS) {
            val drawn = elapsed.toFloat() / CRACK_MILLIS
            return Frame(listOf(Sprite(whole, x, y, BROKEN_HEART, 0f, (t / .05f).coerceAtMost(1f)),
                Sprite(Shape.Crack, x, y, BROKEN_HEART, 0f, 1f, amount = drawn)), wordDrop = dip)
        }
        val s = (t - crack) / (1f - crack)
        // A moment of tilting apart, then gravity.
        val tilt = (s / .2f).coerceAtMost(1f)
        val fall = 34f * ((s - .15f) / .85f).coerceAtLeast(0f).let { it * it }
        val spinLeft = 14f + 10f * random.nextFloat()
        val spinRight = 14f + 10f * random.nextFloat()
        val alpha = fade(s, .45f)
        val halves = listOf(
            // Behind the word, so its letters stay clean as the halves fall past them.
            Sprite(left, x - 1.5f - 3f * tilt - 6f * s, y + fall, BROKEN_HEART, -12f * tilt - spinLeft * s, alpha, behind = true),
            Sprite(right, x + 1.5f + 3f * tilt + 6.5f * s, y + fall * .94f, BROKEN_HEART, 12f * tilt + spinRight * s, alpha, behind = true),
        )
        // Tiny shards spray out of the crack, as many to each side, and fall faster than the halves.
        val splinters = (0 until shards).map { index ->
            val side = if (index % 2 == 0) 1f else -1f
            val out = (5f + 5f * random.nextFloat()) * side
            val upward = 4f + 4f * random.nextFloat()
            val gravity = 40f
            Sprite(Shape.Shard, x + out * (s / .6f).coerceAtMost(1f), y - 2f - upward * s + gravity * s * s, 3f, 90f * s * side,
                fade(s, .2f) * .95f, behind = true)
        }
        return Frame(halves + splinters, wordDrop = dip)
    }

    /**
     * A little dust drifts up and out from the word's letters, and the word dims and comes back: Forget takes what was learned,
     * and the word stays in the menu, not saved (owner, 2026-11-13).
     */
    private fun dust(t: Float, word: Word, random: Random): Frame {
        val count = 10 + random.nextInt(5)
        val specks = (0 until count).map { index ->
            val x = word.left + (word.right - word.left) * (index + random.nextFloat()) / count
            val y = word.middle + (random.nextFloat() - .5f) * 10f
            val away = (x - word.centre) / (word.right - word.left + 1f) * 10f
            Sprite(Shape.Dust, x + away * t, y - (5f + 6f * random.nextFloat()) * t, 2f + random.nextFloat() * 1.5f, 0f, fade(t, .3f) * .9f)
        }
        return Frame(specks, wordAlpha = 1f - .65f * sin(PI.toFloat() * t))
    }

    /**
     * As the menu opens on a saved word or a favourite: two or three small [shape]s, the mark of where the word stands, drift up
     * from its ends, once, softly. A heart only by a favourite.
     */
    private fun echo(t: Float, word: Word, random: Random, shape: Shape): Frame {
        val count = 2 + random.nextInt(2)
        val hearts = (0 until count).mapNotNull { index ->
            val right = index % 2 == 0
            val delay = index * .12f + random.nextFloat() * .06f
            val own = ((t - delay) / (1f - delay)).takeIf { it in 0f..1f } ?: return@mapNotNull null
            val x = (if (right) word.right + 6f else word.left - 6f) + (if (right) 1 else -1) * 3f * own
            Sprite(shape, x, word.middle - 4f - (8f + 4f * random.nextFloat()) * own, 7f + 2f * random.nextFloat(), 0f,
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
    const val AMBIENT_TWINKLE_EVERY = 3500L
    const val AMBIENT_TWINKLE_MILLIS = 700L
    /** How strongly the ambient marks show: faint. */
    const val AMBIENT_ALPHA = .35f

    /**
     * The ambient marks at [elapsed] ms since the menu came to rest at [step] over [word]. The first comes a whole cycle after
     * that, so nothing plays straight after the open's echo or a step's burst: never two in a row (owner, 2026-11-13). With motion
     * off ([still]) they sit still: a faint heart by a favourite, a faint sparkle by a saved word.
     */
    fun ambient(step: MenuKeys.Step, elapsed: Long, word: Word, still: Boolean): List<Sprite> =
        if (!still && elapsed < every(step)) emptyList() else cycles(step, elapsed - every(step), word, still)

    /** How often [step]'s ambient mark comes: never for a word that isn't saved. */
    private fun every(step: MenuKeys.Step) = when (step) {
        MenuKeys.Step.New -> 0L
        MenuKeys.Step.Saved -> AMBIENT_TWINKLE_EVERY
        MenuKeys.Step.Favourite -> AMBIENT_HEART_EVERY
    }

    /** The ambient marks [elapsed] ms into their cycles. */
    private fun cycles(step: MenuKeys.Step, elapsed: Long, word: Word, still: Boolean): List<Sprite> = when (step) {
        MenuKeys.Step.New -> emptyList()
        MenuKeys.Step.Saved -> {
            // A faint sparkle twinkles by the word now and then, at alternating ends: it swells, turns a little, and shrinks away.
            if (still) listOf(Sprite(Shape.Sparkle, word.right + 7f, word.middle - 7f, 8f, 0f, AMBIENT_ALPHA))
            else {
                val cycle = elapsed / AMBIENT_TWINKLE_EVERY
                val into = elapsed % AMBIENT_TWINKLE_EVERY
                if (into >= AMBIENT_TWINKLE_MILLIS) emptyList() else {
                    val p = into.toFloat() / AMBIENT_TWINKLE_MILLIS
                    val right = (cycle + word.seed).mod(2L) == 0L
                    listOf(Sprite(Shape.Sparkle, if (right) word.right + 7f else word.left - 7f, word.middle - 7f, 9f * sin(PI.toFloat() * p),
                        30f * p, AMBIENT_ALPHA * sin(PI.toFloat() * p)))
                }
            }
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

    /** How long until the ambient marks next move, in ms: 0 while they move, so the bar redraws only then. See [ambient]. */
    fun ambientWait(step: MenuKeys.Step, elapsed: Long, still: Boolean): Long = when {
        still || step == MenuKeys.Step.New -> Long.MAX_VALUE
        elapsed < every(step) -> every(step) - elapsed
        else -> cycleWait(step, elapsed - every(step))
    }

    private fun cycleWait(step: MenuKeys.Step, elapsed: Long): Long = when {
        step == MenuKeys.Step.Saved -> (elapsed % AMBIENT_TWINKLE_EVERY).let { if (it < AMBIENT_TWINKLE_MILLIS) 0L else AMBIENT_TWINKLE_EVERY - it }
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

    /** The hearts' sizes, and how long Favourite's pop lasts, as a part of it. */
    private const val HEART = 15f
    private val HEART_SIZES = floatArrayOf(7f, 10f, 13f)
    private const val POP = .3f
    /** The heart's crack draws down it in this long, in ms, and the haptic lands as it's done. The word dips this far, in dp. */
    const val CRACK_MILLIS = 120L
    private const val DIP = 2f
    /** Save's sparkles: their sizes, and how far below and above the word their ring reaches, in dp. */
    private val SPARKLE_SIZES = floatArrayOf(10f, 12f, 14f)
    /** The heart or bookmark that breaks over the word: bigger than Favourite's hearts, so its crack reads. */
    private const val BROKEN_HEART = 18f
    private const val RING_DOWN = 15f
    /** How far Favourite's hearts turn as they fly, in degrees. */
    private const val HEART_TURN = 8f
}
