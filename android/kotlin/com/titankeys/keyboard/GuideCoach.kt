package com.titankeys.keyboard

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// The guide for people new to the phone (#90): misses say what happened, the Space taps light up as they land, and a swipe far
// from its word shows the word's path next to the finger's. A refinement, not a rework (owner): the slides stay as they are.

/**
 * What a miss was, for its line ("that's an X, not a Y") and for the raw log, which records each miss with its kind ([log]) on
 * the phone only, as the log does today.
 */
internal enum class GuideMiss(val log: String) {
    /** Space pressed down, on a slide that wants it tapped. */
    Press("press"),
    /** A letter typed, on a slide that wants Space tapped. */
    Typing("typing"),
    /** A swipe across the keys, on a slide that wants Space tapped. */
    Swipe("swipe"),
    /** The last tap lifted, where it should have stayed down. */
    Lift("lift"),
    /** A tap that landed beside the shaded patch, where the touchpad doesn't count it. */
    Beside("beside"),
    /** One tap where it takes two or three. */
    Single("single-tap"),
    /** More taps than the slide asks for. */
    Extra("extra-tap"),
    /** Fewer taps than the slide asks for, the last one held. */
    Short("short-taps"),
    /** A lift before the slide that the held tap starts. */
    NoSlide("no-slide"),
    /** A next tap that came too late for the keyboard to count it (owner, 2026-11-16). */
    Slow("slow-taps"),
    /** A swipe far from the grey word's path ([SWIPE_FAR_KEYS]). */
    FarSwipe("far-swipe"),
    /** Something the slide locks: see [blockedChip]. */
    Blocked("blocked"),
    /** Any other line a slide has for a slip. */
    Other("other"),
}

private fun ordinal(n: Int) = when (n) {
    1 -> "first"
    2 -> "second"
    3 -> "third"
    else -> "last"
}

private fun count(n: Int) = when (n) {
    1 -> "one"
    2 -> "two"
    3 -> "three"
    else -> "more"
}

/**
 * The line for a miss on a slide of [taps] Space taps, the last one held (owner, #90: "that's an X, not a Y"). [landed] is how
 * many taps came, for [GuideMiss.Extra] and [GuideMiss.Short].
 */
internal fun tapMissLine(miss: GuideMiss, taps: Int, landed: Int = taps): String = when (miss) {
    GuideMiss.Press -> "That's a press, not a tap. Barely touch Space."
    GuideMiss.Typing -> "That's typing, not a tap. Barely touch Space."
    GuideMiss.Swipe -> "That's a swipe, not a tap. Keep your finger still."
    GuideMiss.Lift -> "That's a lift, not a hold. Keep the ${ordinal(taps)} down."
    GuideMiss.Beside -> "That's beside the patch, not on it. Tap the shade."
    GuideMiss.Single -> "That's one tap, not ${count(taps)}. Hold the ${ordinal(taps)} one."
    GuideMiss.Extra -> "That's ${count(landed)} taps, not ${count(taps)}. Hold the ${ordinal(taps)} one."
    GuideMiss.Short -> "That's ${count(landed)} taps, not ${count(taps)}. Hold the ${ordinal(taps)} one."
    GuideMiss.NoSlide -> "That's a lift, not a slide. Stay down and slide."
    // F: draft (owner, 2026-11-16: "when I'm too slow it shd tell me").
    GuideMiss.Slow -> "That's too slow, not quick taps. Tap faster."
    else -> "That's not this slide's move."
}

/**
 * What a Space-tap slide says as its taps start the mode: which mode, and which way to slide (owner, 2026-11-16). F: draft.
 */
internal fun modeStartLine(words: Boolean, left: Boolean): String {
    val way = if (left) "left" else "right"
    return if (words) "Select mode on. Slide $way, lift at the end." else "Cursor mode on. Slide $way, lift in the word."
}

/** The line after a swipe far from its word, under the drawing of both paths (#90). */
internal const val SWIPE_COACH = "That's a detour, not the path. Pass every letter."

/**
 * How many taps have landed on a Space-tap slide, and whether the last one is held, as the drawing lights them: 1, 2, held
 * (owner, #90). It follows the finger's own contacts, from the touchpad's traces: a touch that starts soon after a tap is the next
 * tap; anything else starts again from one.
 */
internal class TapMeter(val taps: Int) {
    /** Taps landed so far, the one down now included, from 0 to [taps]. */
    var landed = 0
        private set
    /** The last tap stayed down and cursor mode is on: when the keyboard said so. */
    var heldAt = -1L
        private set
    val held get() = heldAt >= 0
    var downAt = -1L
        private set
    private var lastUp = -1L
    /** Painting expires on time, while the old tap remains available to diagnose a slow attempt. */
    val expiresAt: Long get() = when {
        held || landed == 0 -> -1L
        downAt >= 0 -> downAt + TAP_MILLIS
        lastUp >= 0 -> lastUp + TAP_GAP_MILLIS + 1L
        else -> -1L
    }

    fun visibleTaps(now: Long) = if (held || now >= expiresAt) 0 else landed

    /**
     * A finger came down at [time]: the next tap, if it follows a tap as soon as the keyboard needs, or the first. True when it was
     * meant as the next tap but came too late for the keyboard, so the slide can say so (owner, 2026-11-16: "when I'm too slow it
     * shd tell me").
     */
    fun down(time: Long): Boolean {
        // Held, the finger hasn't lifted: this is the cursor's own trace starting as the slide begins, not a new tap. It used to
        // start the count again, so the boxes lit 1 for a moment and went out mid-slide (owner, 2026-11-21: "it stayed on for a
        // split second and then turns off by itself").
        if (held) return false
        heldAt = -1L
        downAt = time
        val gap = if (lastUp >= 0) time - lastUp else -1L
        val counting = landed in 1 until taps
        val slow = counting && gap > TAP_GAP_MILLIS && gap <= SLOW_MILLIS
        landed = if (counting && gap in 0..TAP_GAP_MILLIS) landed + 1 else 1
        return slow
    }

    /** The finger lifted at [time]. [tap] says whether the touch was a tap: short and still. Anything else starts again. */
    fun up(time: Long, tap: Boolean) {
        if (!tap || held) reset() else {
            downAt = -1L
            lastUp = time
        }
    }

    /** Cursor mode is on: every box stays lit until the finger lifts. */
    fun hold(time: Long) {
        if (!held) heldAt = time
        landed = taps
    }

    fun reset() {
        landed = 0
        heldAt = -1L
        downAt = -1L
        lastUp = -1L
    }

    companion object {
        /**
         * A touch that starts this soon after a tap lifted is the next tap: the keyboard's own most, CursorGesture's
         * MAX_DRAG_GAP_MILLIS, so the boxes light only for taps that count. It was a kinder 450 ms, which lit taps the keyboard
         * didn't take, and the slide couldn't tell anyone they were too slow.
         */
        const val KEYBOARD_GAP_MILLIS = 200L
        const val TAP_GAP_MILLIS = KEYBOARD_GAP_MILLIS
        /** Later than the keyboard takes, up to this long after a tap, the next is too slow; later still, it's a new start. */
        const val SLOW_MILLIS = 1_000L
        /** A tap is a touch shorter than this, that moved less than [TAP_TRAVEL] keys. */
        const val TAP_MILLIS = 300L
        const val TAP_TRAVEL = .5f
    }
}

/**
 * How far a swipe was from [word]'s path, in key widths: both resampled to [SWIPE_POINTS] points along their length, then the mean
 * distance between matching points. [trail] is in the touchpad's space, x across the ten keys and y down the four rows, as the
 * guide's trail is; the word's path goes through each letter's middle, as the ghost finger does.
 */
internal fun swipeDistance(trail: List<FloatArray>, word: String): Float? {
    val path = wordPath(word)
    if (trail.size < 2 || path.isEmpty()) return null
    val a = resample(trail.map { floatArrayOf(it[0] * KeyGeometry.COLUMNS, it[1] * KeyGeometry.ROWS) })
    val b = resample(path.map { floatArrayOf(it[0] * KeyGeometry.COLUMNS, it[1] * KeyGeometry.ROWS) })
    return a.indices.map { hypot(a[it][0] - b[it][0], a[it][1] - b[it][1]) }.average().toFloat()
}

/** The path a swipe of [word] takes: each letter's middle, in the touchpad's space, with a letter twice in a row once. */
internal fun wordPath(word: String): List<FloatArray> = word.filter { it.isLetter() && it.lowercaseChar() in 'a'..'z' }
    .map { letter -> Layout.centre(letter).let { (x, y) -> floatArrayOf(x, y) } }
    .fold(ArrayList<FloatArray>()) { kept, point ->
        if (kept.isEmpty() || !kept.last().contentEquals(point)) kept += point
        kept
    }

/** A swipe this far from its word, in key widths, or further, shows the word's path and a line on how to swipe (#90). */
internal fun swipeFar(distance: Float?) = distance != null && distance >= SWIPE_FAR_KEYS

/**
 * The cut-off, set from the owner's recorded swipes (1,227 with their expected word, numbers only): at 1.2 key widths, 3% of the
 * swipes the engine read right are over it, and 43% of the ones it read wrong.
 */
internal const val SWIPE_FAR_KEYS = 1.2f
internal const val SWIPE_POINTS = 32

private fun resample(points: List<FloatArray>, n: Int = SWIPE_POINTS): List<FloatArray> {
    if (points.size == 1) return List(n) { points[0] }
    val along = FloatArray(points.size)
    for (i in 1 until points.size) along[i] = along[i - 1] + hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1])
    val total = along.last()
    if (total <= 0f) return List(n) { points[0] }
    var j = 0
    return List(n) { i ->
        val at = total * i / (n - 1)
        while (j < points.size - 2 && along[j + 1] < at) j++
        val span = along[j + 1] - along[j]
        val u = if (span <= 0f) 0f else min(1f, max(0f, (at - along[j]) / span))
        floatArrayOf(points[j][0] + (points[j + 1][0] - points[j][0]) * u, points[j][1] + (points[j + 1][1] - points[j][1]) * u)
    }
}

/** The raw log's marks for the guide (RawLog.MARK_ACTION): the slide that shows, and each miss with its kind. */
internal object GuideLog {
    const val SLIDE = "guide-slide"
    const val MISS = "guide-miss"
    fun missLabel(slide: String, miss: GuideMiss, detail: String? = null) = listOfNotNull(slide, miss.log, detail).joinToString(" ")
}
