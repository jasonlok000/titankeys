package com.atlas.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The playground's show around the speed: a glow, sparks, confetti, flames, the screen's edges, and things that fall, building
 * with the words a minute as [WpmEffects] says, and one plant at the very top. It sits behind the words, takes no touch, makes no
 * sound and draws only while something shows. Everything eases in and out, so when the speed counts down the show winds down too.
 * With animations off it draws a still glow and nothing else.
 */
internal class WpmEffectsView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())

    /** Where the number is, in this view's pixels. */
    private val number = RectF()
    private var effects = WpmEffects.NONE
    private var step = -1
    /** Told each frame how far to tip the number, in degrees, and how far to shake the screen, in pixels. */
    var shaking: ((wobble: Float, dx: Float, dy: Float) -> Unit)? = null

    private enum class Kind { Spark, Confetti, Fall, Sweat }

    private class Bit(
        val kind: Kind, var x: Float, var y: Float, var vx: Float, var vy: Float,
        var angle: Float, val spin: Float, var life: Float, val lifetime: Float, val colour: Int, val size: Float,
        val emoji: String? = null,
        /** Confetti that flickers brown. */
        val brown: Boolean = false,
    )

    private val bits = ArrayList<Bit>()
    /** Eased amounts, from 0 to 1, so that each effect fades in and out. */
    private var glow = 0f
    private var flames = 0f
    private var edges = 0f
    private var wobble = 0f
    private var shake = 0f
    private var sprout = 0f
    private var flash = 0f
    private var pulse = 0f
    private var rainOwed = 0f
    private var sparksOwed = 0f
    private var sweatOwed = 0f
    private var nextFlash = 0L
    private var lastFrame = 0L
    private var clock = 0f
    private var shaken = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // A circle of radius one, scaled to the number when it is drawn, so that no shader is made for each frame.
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(GLOW, GuideMotion.faded(GLOW, .45f), Color.TRANSPARENT),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
    }
    private val edgePaint = Paint()
    private var edgeShaders: List<Shader> = emptyList()
    private val emojiBitmaps = HashMap<String, Bitmap>()

    /**
     * The speed changed: [next] is what shows now, [stepNow] the comment's step, from [WpmComments], and [anchor] where the number
     * is. A new step up gives the glow a pulse, and the confetti that the step has: a pop from the number, or a shower from the top.
     */
    fun update(next: WpmEffects, stepNow: Int, anchor: RectF?) {
        anchor?.let(number::set)
        if (stepNow > step && stepNow >= 0) {
            pulse = 1f
            if (GuideMotion.enabled) {
                pop(next.pop)
                shower(next.shower, next.brown)
            }
        }
        step = stepNow
        // At the top everything stops: what is in the air fades away, and the plant grows.
        if (next.sprout && !effects.sprout) for (bit in bits) bit.life = max(bit.life, bit.lifetime - CLEAR_SECONDS)
        effects = next
        postInvalidateOnAnimation()
    }

    /** A shower of confetti from the top, once: the guide's little cheer at the end of a chapter. */
    fun confetti() {
        if (!GuideMotion.enabled || width == 0) return
        shower(CHAPTER_CONFETTI, false)
        postInvalidateOnAnimation()
    }

    /**
     * Rains [count] of [emoji] down the screen once, as a reward, whatever the speed: the guide's paste lesson uses it. With
     * animations off it does nothing.
     */
    fun burst(emoji: String, count: Int) {
        if (!GuideMotion.enabled || width == 0) return
        repeat(count) {
            val size = dp(random.nextFloat() * 12f + 20f)
            add(Bit(Kind.Fall, random.nextFloat() * width, -size - random.nextFloat() * height * BURST_SPREAD, 0f,
                dp(random.nextFloat() * 160f + 260f), random.nextFloat() * 40f - 20f, (random.nextFloat() - .5f) * 120f, 0f,
                FALL_SECONDS, Color.WHITE, size, emoji))
        }
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val reach = dp(EDGE_DP)
        val glowColour = GuideMotion.faded(EDGE, .7f)
        edgeShaders = listOf(
            LinearGradient(0f, 0f, reach, 0f, glowColour, Color.TRANSPARENT, Shader.TileMode.CLAMP),
            LinearGradient(w.toFloat(), 0f, w - reach, 0f, glowColour, Color.TRANSPARENT, Shader.TileMode.CLAMP),
            LinearGradient(0f, 0f, 0f, reach, glowColour, Color.TRANSPARENT, Shader.TileMode.CLAMP),
            LinearGradient(0f, h.toFloat(), 0f, h - reach, glowColour, Color.TRANSPARENT, Shader.TileMode.CLAMP),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val now = GuideMotion.now()
        if (!GuideMotion.enabled) {
            // Animations off: the comments still show, with a still glow and nothing that moves.
            bits.clear()
            settle()
            if (effects.glow > 0f) drawGlow(canvas, effects.glow)
            lastFrame = 0
            return
        }
        val dt = if (lastFrame == 0L) FRAME_SECONDS else ((now - lastFrame) / 1000f).coerceIn(0f, MAX_STEP_SECONDS)
        lastFrame = now
        clock += dt
        ease(dt)
        spawn(now, dt)
        move(dt)

        if (edges > .01f) drawEdges(canvas)
        if (flames > .01f) drawFlames(canvas)
        if (glow > .01f) drawGlow(canvas, glow * (1f + pulse * PULSE_GLOW))
        for (bit in bits) drawBit(canvas, bit)
        if (sprout > .01f) drawSprout(canvas)
        if (flash > .01f) {
            paint.color = GuideMotion.faded(Color.WHITE, flash * FLASH_ALPHA)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        tipAndShake()
        val busy = effects.any || bits.isNotEmpty() || glow > .01f || flames > .01f || edges > .01f || sprout > .01f ||
            flash > .01f || wobble > .01f || shake > .01f || pulse > .01f
        if (busy) postInvalidateOnAnimation() else {
            lastFrame = 0
            settle()
        }
    }

    /** Each amount moves towards what the speed asks for: up quickly, down a little more slowly. */
    private fun ease(dt: Float) {
        fun towards(value: Float, target: Float) =
            if (target > value) min(target, value + dt / FADE_IN_SECONDS) else max(target, value - dt / FADE_OUT_SECONDS)
        fun towards(value: Float, on: Boolean) = towards(value, if (on) 1f else 0f)
        glow = towards(glow, effects.glow)
        flames = towards(flames, effects.flames)
        edges = towards(edges, effects.edges)
        wobble = towards(wobble, effects.wobble)
        shake = towards(shake, effects.shake)
        sprout = if (effects.sprout) min(1f, sprout + dt / SPROUT_SECONDS) else max(0f, sprout - dt / FADE_OUT_SECONDS)
        pulse = max(0f, pulse - dt / PULSE_SECONDS)
        flash = max(0f, flash - dt / FLASH_SECONDS)
    }

    private fun spawn(now: Long, dt: Float) {
        if (effects.sparks > 0f) {
            sparksOwed += SPARKS_PER_SECOND * effects.sparks * dt
            while (sparksOwed >= 1f) {
                sparksOwed -= 1f
                spark()
            }
        } else sparksOwed = 0f
        if (effects.rain > 0f) {
            rainOwed += effects.rain * dt
            while (rainOwed >= 1f) {
                rainOwed -= 1f
                fall()
            }
        } else rainOwed = 0f
        if (effects.sweat) {
            sweatOwed += SWEAT_PER_SECOND * dt
            while (sweatOwed >= 1f) {
                sweatOwed -= 1f
                sweat()
            }
        } else sweatOwed = 0f
        if (effects.lightning && now >= nextFlash) {
            flash = 1f
            nextFlash = now + random.nextLong(FLASH_GAP_MIN, FLASH_GAP_MAX)
        }
    }

    private fun move(dt: Float) {
        val gravity = dp(GRAVITY_DP)
        val iterator = bits.iterator()
        while (iterator.hasNext()) {
            val bit = iterator.next()
            bit.life += dt
            when (bit.kind) {
                Kind.Spark, Kind.Confetti, Kind.Sweat -> bit.vy += gravity * dt * (if (bit.kind == Kind.Spark) .35f else 1f)
                // Things that fall reach their speed at once and drift a little.
                Kind.Fall -> bit.vx = sin(clock * 2f + bit.size) * dp(12f)
            }
            if (bit.kind == Kind.Confetti) {
                bit.vx *= 1f - min(1f, CONFETTI_DRAG * dt)
                bit.vy = min(bit.vy, dp(CONFETTI_FALL_DP))
            }
            bit.x += bit.vx * dt
            bit.y += bit.vy * dt
            bit.angle += bit.spin * dt
            if (bit.life >= bit.lifetime || bit.y > height + bit.size * 2) iterator.remove()
        }
    }

    private fun add(bit: Bit) {
        if (bits.size >= MAX_BITS) bits.removeAt(0)
        bits += bit
    }

    private fun spark() {
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val speed = dp(random.nextFloat() * 90f + 60f)
        val x = number.centerX() + cos(angle) * number.width() / 2
        val y = number.centerY() + sin(angle) * number.height() / 2
        add(Bit(Kind.Spark, x, y, cos(angle) * speed, sin(angle) * speed - dp(40f), 0f, 0f, 0f,
            random.nextFloat() * .3f + .25f, if (random.nextBoolean()) SPARK else GLOW, dp(random.nextFloat() * 1.5f + 1.5f)))
    }

    /** A burst of [pieces] of confetti from the number, at a new step. */
    private fun pop(pieces: Int) {
        repeat(pieces) {
            val angle = -PI.toFloat() / 2 + (random.nextFloat() - .5f) * 2.4f
            val speed = dp(random.nextFloat() * 260f + 200f)
            add(Bit(Kind.Confetti, number.centerX(), number.centerY(), cos(angle) * speed, sin(angle) * speed,
                random.nextFloat() * 360f, (random.nextFloat() - .5f) * 900f, 0f, random.nextFloat() * .5f + .9f,
                CONFETTI[random.nextInt(CONFETTI.size)], dp(random.nextFloat() * 3f + 4f)))
        }
    }

    /** [pieces] of confetti falling from the top, all across the screen. [brown] ones flicker brown. */
    private fun shower(pieces: Int, brown: Boolean) {
        repeat(pieces) {
            add(Bit(Kind.Confetti, random.nextFloat() * width, -dp(random.nextFloat() * 160f + 10f), dp((random.nextFloat() - .5f) * 60f),
                dp(random.nextFloat() * 80f + 60f), random.nextFloat() * 360f, (random.nextFloat() - .5f) * 700f, 0f, SHOWER_SECONDS,
                CONFETTI[random.nextInt(CONFETTI.size)], dp(random.nextFloat() * 3f + 4f), brown = brown))
        }
    }

    private fun fall() {
        val roll = random.nextFloat()
        val emoji = when {
            effects.toilets && roll < .15f -> "🚽"
            effects.clowns && roll < .4f -> "🤡"
            else -> "💩"
        }
        val size = dp(random.nextFloat() * 10f + 18f)
        add(Bit(Kind.Fall, random.nextFloat() * width, -size, 0f, dp(random.nextFloat() * 120f + 160f),
            random.nextFloat() * 40f - 20f, (random.nextFloat() - .5f) * 90f, 0f, FALL_SECONDS, Color.WHITE, size, emoji))
    }

    /** A drop off the number's top corner, as if it sweats. */
    private fun sweat() {
        val left = random.nextBoolean()
        val x = if (left) number.left else number.right
        add(Bit(Kind.Sweat, x, number.top + number.height() * .2f, dp(if (left) -40f else 40f), dp(-60f), 0f, 0f, 0f, .8f,
            Color.WHITE, dp(14f), "💦"))
    }

    private fun drawBit(canvas: Canvas, bit: Bit) {
        // Each fades over the last part of its life, and quickly when the plant clears the mess.
        val left = 1f - bit.life / bit.lifetime
        val alpha = (left / FADE_END).coerceIn(0f, 1f)
        val emoji = bit.emoji
        if (emoji != null) {
            val bitmap = emojiBitmap(emoji)
            paint.alpha = (alpha * 255).toInt()
            canvas.save()
            canvas.translate(bit.x, bit.y)
            canvas.rotate(bit.angle)
            val scale = bit.size / bitmap.width
            canvas.scale(scale, scale)
            canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, paint)
            canvas.restore()
            paint.alpha = 255
            return
        }
        val colour = if (bit.brown && sin(clock * BROWN_FLICKER + bit.x) > 0f) BROWN else bit.colour
        paint.color = GuideMotion.faded(colour, alpha)
        if (bit.kind == Kind.Confetti) {
            canvas.save()
            canvas.translate(bit.x, bit.y)
            canvas.rotate(bit.angle)
            canvas.drawRect(-bit.size / 2, -bit.size / 4, bit.size / 2, bit.size / 4, paint)
            canvas.restore()
        } else canvas.drawCircle(bit.x, bit.y, bit.size, paint)
    }

    /** A soft light behind the number, from its middle. */
    private fun drawGlow(canvas: Canvas, amount: Float) {
        if (number.isEmpty) return
        glowPaint.alpha = (amount.coerceIn(0f, 1.5f) / 1.5f * 255).toInt()
        canvas.save()
        canvas.translate(number.centerX(), number.centerY())
        canvas.scale(number.width() * .75f + dp(24f), number.height() * .9f + dp(16f))
        canvas.drawCircle(0f, 0f, 1f, glowPaint)
        canvas.restore()
    }

    /** Tongues of flame under the number, each flickering at its own pace. */
    private fun drawFlames(canvas: Canvas) {
        if (number.isEmpty) return
        val base = number.bottom + dp(4f)
        val tongues = FLAME_TONGUES
        for (layer in 0..1) {
            paint.color = GuideMotion.faded(if (layer == 0) FLAME_OUTER else FLAME_INNER, min(1f, flames) * (if (layer == 0) .75f else .9f))
            for (i in 0 until tongues) {
                val x = number.left + number.width() * (i + .5f) / tongues
                val flicker = .6f + .4f * sin(clock * (7f + i * 1.3f) + i * 2.1f)
                val tall = dp(if (layer == 0) 26f else 14f) * flicker * flames
                val wide = number.width() / tongues * (if (layer == 0) .55f else .32f)
                canvas.drawOval(x - wide, base - tall, x + wide, base + tall * .25f, paint)
            }
        }
    }

    private fun drawEdges(canvas: Canvas) {
        val breathe = .7f + .3f * sin(clock * 3f)
        for ((index, shader) in edgeShaders.withIndex()) {
            edgePaint.shader = shader
            edgePaint.alpha = (min(1f, edges) * breathe * 255).toInt().coerceIn(0, 255)
            val reach = dp(EDGE_DP)
            when (index) {
                0 -> canvas.drawRect(0f, 0f, reach, height.toFloat(), edgePaint)
                1 -> canvas.drawRect(width - reach, 0f, width.toFloat(), height.toFloat(), edgePaint)
                2 -> canvas.drawRect(0f, 0f, width.toFloat(), reach, edgePaint)
                else -> canvas.drawRect(0f, height - reach, width.toFloat(), height.toFloat(), edgePaint)
            }
        }
    }

    /** One plant in the middle, growing out of the calm, swaying a little. */
    private fun drawSprout(canvas: Canvas) {
        val bitmap = emojiBitmap("🌱")
        val grown = GuideMotion.easeOut(sprout)
        val size = dp(SPROUT_DP) * grown
        if (size < 1f) return
        canvas.save()
        canvas.translate(width / 2f, height / 2f + dp(SPROUT_DP) / 2)
        canvas.rotate(sin(clock * 1.6f) * 4f)
        val scale = size / bitmap.width
        canvas.scale(scale, scale)
        paint.alpha = (min(1f, sprout * 2f) * 255).toInt()
        canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height.toFloat(), paint)
        paint.alpha = 255
        canvas.restore()
    }

    private fun tipAndShake() {
        val tip = if (wobble > .01f) sin(clock * WOBBLE_SPEED) * WOBBLE_DEGREES * wobble else 0f
        val reach = dp(SHAKE_DP) * shake
        val dx = if (reach > .1f) (random.nextFloat() * 2f - 1f) * reach else 0f
        val dy = if (reach > .1f) (random.nextFloat() * 2f - 1f) * reach else 0f
        shaken = tip != 0f || dx != 0f || dy != 0f
        shaking?.invoke(tip, dx, dy)
    }

    /** Puts the number and the screen back still. */
    private fun settle() {
        if (!shaken) return
        shaken = false
        shaking?.invoke(0f, 0f, 0f)
    }

    /** Emoji are drawn once into a picture, and then only the picture each frame, which is much cheaper. */
    private fun emojiBitmap(emoji: String): Bitmap = emojiBitmaps.getOrPut(emoji) {
        val size = dp(EMOJI_BITMAP_DP).toInt().coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * .8f
            textAlign = Paint.Align.CENTER
        }
        Canvas(bitmap).drawText(emoji, size / 2f, size / 2f - (text.ascent() + text.descent()) / 2, text)
        bitmap
    }

    private fun dp(value: Float) = value * density

    private companion object {
        const val GLOW = 0xFFFFB27A.toInt()
        const val EDGE = 0xFFEF804E.toInt()
        const val SPARK = 0xFFFFE0B2.toInt()
        const val FLAME_OUTER = 0xFFEF804E.toInt()
        const val FLAME_INNER = 0xFFFFD27A.toInt()
        const val BROWN = 0xFF7A4A21.toInt()
        val CONFETTI = intArrayOf(0xFFEF804E.toInt(), 0xFFFFF1E6.toInt(), 0xFFFFD54F.toInt(), 0xFFFF8FB1.toInt(), 0xFF6ED3CF.toInt())

        // Time, in seconds. Android's animator duration setting does not change these: with it off nothing moves at all.
        const val FRAME_SECONDS = 1 / 60f
        const val MAX_STEP_SECONDS = .05f
        const val FADE_IN_SECONDS = .35f
        const val FADE_OUT_SECONDS = .8f
        const val PULSE_SECONDS = .45f
        const val PULSE_GLOW = .5f
        const val FLASH_SECONDS = .18f
        const val FLASH_ALPHA = .28f
        const val FLASH_GAP_MIN = 1300L
        const val FLASH_GAP_MAX = 3400L
        const val SPROUT_SECONDS = 1.4f
        const val CLEAR_SECONDS = .3f
        const val FALL_SECONDS = 6f
        /** A burst starts this much of the screen's height above it, so that it arrives over a second or two. */
        const val BURST_SPREAD = .8f
        const val SHOWER_SECONDS = 6f
        const val CHAPTER_CONFETTI = 45
        const val BROWN_FLICKER = 18f
        /** The last part of a life over which a bit fades. */
        const val FADE_END = .3f

        // Amounts. Sizes are in dp.
        const val MAX_BITS = 150
        const val SPARKS_PER_SECOND = 22f
        const val SWEAT_PER_SECOND = 2.2f
        const val CONFETTI_DRAG = 1.6f
        const val CONFETTI_FALL_DP = 140f
        const val GRAVITY_DP = 900f
        const val FLAME_TONGUES = 5
        const val EDGE_DP = 36f
        const val WOBBLE_SPEED = 13f
        const val WOBBLE_DEGREES = 3f
        const val SHAKE_DP = 1.6f
        const val SPROUT_DP = 72f
        const val EMOJI_BITMAP_DP = 40f
    }
}
