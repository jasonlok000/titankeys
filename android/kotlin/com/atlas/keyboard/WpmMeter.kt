package com.atlas.keyboard

import android.view.KeyEvent
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Words a minute over one burst of typing in the playground, like a typing game but with no score to keep. A burst starts only
 * once a real word is being typed, from a letter key or a swipe ([input]), never from a touch, Space, Backspace or a modifier
 * (owner); once it runs, the rest of the typing keeps it going ([keep]). It ends after [IDLE_MILLIS] with nothing. A word is five
 * characters, and nothing checks what they are. It needs no Android, so the checks can run it with any clock.
 */
internal class WpmMeter {
    private var start = -1L
    private var last = -1L
    private var startLength = 0
    private var length = 0

    /**
     * A real word is being typed at [now], from a letter key or a swipe, and the text is [textLength] long: it starts a burst, or
     * keeps one going. The first input of a burst sets where it starts.
     */
    fun input(now: Long, textLength: Int) {
        if (!active(now)) {
            start = now
            startLength = textLength
        }
        last = now
        length = textLength
    }

    /** Typing that isn't a word's letters, as Space, Backspace or a word from the bar, at [now]: it keeps a burst going, never starts one. */
    fun keep(now: Long, textLength: Int) {
        if (!active(now)) return
        last = now
        length = textLength
    }

    /** The text changed by itself, as when a swipe's word lands: it counts, but it does not keep the burst going. */
    fun text(textLength: Int) {
        if (start >= 0) length = textLength
    }

    fun active(now: Long) = start >= 0 && now - last in 0..IDLE_MILLIS

    /** The speed of this burst, or null between bursts. The first seconds count as [MIN_SPAN_MILLIS], so one quick word is not 300. */
    fun wpm(now: Long): Int? {
        if (!active(now)) return null
        val minutes = max(now - start, MIN_SPAN_MILLIS) / 60_000.0
        return ((length - startLength).coerceAtLeast(0) / CHARS_PER_WORD / minutes).roundToInt()
    }

    companion object {
        /** Shift, Alt, Sym, fn, Ctrl and the like put in no text, so they neither start a burst nor keep it going. */
        private val MODIFIERS = setOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_META_LEFT,
            KeyEvent.KEYCODE_META_RIGHT, KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_FUNCTION, KeyEvent.KEYCODE_CAPS_LOCK)

        /** Whether a push of [code] is typing, which counts towards the speed once it runs. */
        fun counts(code: Int) = code !in MODIFIERS

        /** Whether a push of [code] types a word's letter, which starts the speed: the letter keys only. */
        fun startsWord(code: Int) = code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z

        /** The playground's cream text. */
        private const val CREAM = 0xFFFFF1E6.toInt()
        /** The least contrast for text to read (WCAG AA): below it, cream on the warming background turns to ink. */
        const val READABLE = 4.5
        /** Once ink, the text goes back to cream only above [READABLE] by this much, so it never flickers at the edge. */
        const val READABLE_SLACK = .3

        /**
         * Whether the playground's text is ink on [background], as an ARGB colour: once cream drops below [READABLE] contrast on
         * it (owner: "when fully orange on playground words are not readable"). [inkNow] is whether it's ink already.
         */
        fun inkOn(background: Int, inkNow: Boolean): Boolean {
            val contrast = contrast(CREAM, background)
            return if (inkNow) contrast < READABLE + READABLE_SLACK else contrast < READABLE
        }

        /** The playground's muted grey, for secondary text. */
        private const val MUTED = 0xFF99918A.toInt()

        /**
         * The secondary text's colour on [background] while the text is cream: the muted grey, moved towards cream just enough to
         * keep [READABLE] contrast, so it stays readable as the playground warms and before every text turns ink.
         */
        fun quietOn(background: Int): Int {
            for (step in 0..QUIET_STEPS) {
                val colour = mix(MUTED, CREAM, step.toFloat() / QUIET_STEPS)
                if (contrast(colour, background) >= READABLE) return colour
            }
            return CREAM
        }

        private const val QUIET_STEPS = 20

        private fun mix(from: Int, to: Int, amount: Float): Int {
            fun channel(shift: Int): Int {
                val a = from shr shift and 0xFF
                val b = to shr shift and 0xFF
                return (a + (b - a) * amount + .5f).toInt().coerceIn(0, 255) shl shift
            }
            return channel(24) or channel(16) or channel(8) or channel(0)
        }

        /** The contrast between two opaque colours, from 1 to 21, as WCAG measures it. */
        fun contrast(a: Int, b: Int): Double {
            val lighter = max(luminance(a), luminance(b))
            val darker = minOf(luminance(a), luminance(b))
            return (lighter + .05) / (darker + .05)
        }

        private fun luminance(colour: Int): Double {
            fun channel(shift: Int): Double {
                val c = (colour shr shift and 0xFF) / 255.0
                return if (c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4)
            }
            return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
        }

        /** Each comment stays at least this long, up or down, so that it can be read. */
        const val COMMENT_MIN_MILLIS = 3000L

        /** Five seconds with nothing ends a burst, and the speed counts back down to nothing. */
        const val IDLE_MILLIS = 5000L
        const val MIN_SPAN_MILLIS = 4000L
        const val CHARS_PER_WORD = 5.0
        /** The speed where the background is as orange as it gets. */
        const val FULL_WPM = 90f

        /** How orange the background is for a speed, from 0 to 1, easing in so that slow typing barely tints it. */
        fun heat(wpm: Int?) = ((wpm ?: 0) / FULL_WPM).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
    }
}

/**
 * The playground's comments on your speed: a line from each step on, until the next step, from [STEPS]. The lines themselves are
 * in the strings, as playground_comments, one for each step, in the same order. The show in [WpmEffects] follows the same steps.
 */
internal object WpmComments {
    /** Every 5 words a minute from 15 to 100, then every 10 to 200. Most people type around 30, so it starts low. */
    val STEPS: List<Int> = (15..100 step 5).toList() + (110..200 step 10).toList()

    /** The step that [wpm] has reached, as an index into [STEPS], or -1 below the first. */
    fun step(wpm: Int) = STEPS.indexOfLast { wpm >= it }
}

/**
 * What the playground shows around the number at a speed, step by step with [WpmComments]. Each step adds to the ones below it, and
 * at the top it all stops for one small plant. Amounts are from 0, with 1 for the usual full amount: [flames] and [sparks] go past
 * it at full blast. [pop] and [shower] are how many pieces of confetti burst from the number, or fall from the top, at each new step.
 * [rain] is how many things fall each second.
 */
internal data class WpmEffects(
    val glow: Float = 0f,
    val sparks: Float = 0f,
    val pop: Int = 0,
    val shower: Int = 0,
    /** The confetti flickers brown, just before it turns into something worse. */
    val brown: Boolean = false,
    val flames: Float = 0f,
    val edges: Float = 0f,
    val rain: Float = 0f,
    val wobble: Boolean = false,
    val clowns: Boolean = false,
    val sweat: Boolean = false,
    val shake: Boolean = false,
    val toilets: Boolean = false,
    val lightning: Boolean = false,
    /** Everything stops, the mess clears, and one plant grows in the middle. */
    val sprout: Boolean = false,
) {
    val any get() = this != NONE

    companion object {
        val NONE = WpmEffects()

        fun at(wpm: Int): WpmEffects {
            if (wpm >= 200) return WpmEffects(sprout = true)
            if (wpm < 15) return NONE
            fun from(vararg steps: Pair<Int, Float>) = steps.lastOrNull { wpm >= it.first }?.second ?: 0f
            return WpmEffects(
                // 15 starts the glow, 20 pulses it at each step, 25 makes it brighter.
                glow = from(15 to .55f, 20 to .7f, 25 to 1f),
                // 30 sparks, 35 more, 90 full blast.
                sparks = from(30 to 1f, 35 to 1.8f, 90 to 2.6f),
                pop = when (wpm) {
                    in 40 until 45 -> 12
                    in 45 until 50 -> 22
                    else -> 0
                },
                // 80 a shower, 85 a bigger one, 90 full blast, 95 flickering brown. At 100 the confetti turns into rain.
                shower = when {
                    wpm >= 100 -> 0
                    wpm >= 90 -> 80
                    wpm >= 85 -> 55
                    wpm >= 80 -> 35
                    else -> 0
                },
                brown = wpm in 95 until 100,
                // 50 small flames, 55 growing, 60 taller, 65 licking the number, 70 bigger, 90 full blast.
                flames = from(50 to .35f, 55 to .55f, 60 to .75f, 65 to .95f, 70 to 1.2f, 90 to 1.4f),
                // 70 the edges glow, 75 hotter.
                edges = from(70 to .55f, 75 to 1f),
                // 100 a drizzle, 110 heavier, 120 steady, 170 a storm, 190 at its worst.
                rain = from(100 to 1.5f, 110 to 3.5f, 120 to 6f, 160 to 7f, 170 to 14f, 190 to 20f),
                wobble = wpm >= 110,
                clowns = wpm >= 130,
                sweat = wpm >= 140,
                shake = wpm >= 150,
                toilets = wpm >= 160,
                lightning = wpm >= 180,
            )
        }
    }
}
