package com.atlas.keyboard

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Words a minute over one burst of typing in the playground, like a typing game but with no score to keep. A burst starts at the
 * first key, touch or swipe, and ends after [IDLE_MILLIS] with nothing. A word is five characters, and nothing checks what they are.
 * It needs no Android, so the checks can run it with any clock.
 */
internal class WpmMeter {
    private var start = -1L
    private var last = -1L
    private var startLength = 0
    private var length = 0

    /** Something went in at [now], and the text is [textLength] long. The first input of a burst sets where it starts. */
    fun input(now: Long, textLength: Int) {
        if (!active(now)) {
            start = now
            startLength = textLength
        }
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
        const val IDLE_MILLIS = 7000L
        const val MIN_SPAN_MILLIS = 4000L
        const val CHARS_PER_WORD = 5.0
        /** The speed where the background is as orange as it gets. */
        const val FULL_WPM = 90f

        /** How orange the background is for a speed, from 0 to 1, easing in so that slow typing barely tints it. */
        fun heat(wpm: Int?) = ((wpm ?: 0) / FULL_WPM).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
    }
}
