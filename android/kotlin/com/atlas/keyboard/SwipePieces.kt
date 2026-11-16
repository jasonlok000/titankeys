package com.atlas.keyboard

import com.atlas.engine.Swipe

/** Converts touchpad points to the recogniser's letter space. */
internal fun letterSpace(points: List<Sample>): Swipe {
    val start = points.first().time
    return Swipe(
        FloatArray(points.size) { points[it].x },
        FloatArray(points.size) { points[it].y * 4 / 3 },
        FloatArray(points.size) { (points[it].time - start).toFloat() },
    )
}

/** How far back from a swipe's end [endContact] looks. */
private const val END_CONTACT_MILLIS = 60L

/**
 * The finger's mean contact size over the last moments of a swipe, in the sensor's steps, or NaN when the sensor gave
 * none. A finger sliding off the touchpad's edge thins out; one that finished a word lifts at full size (#83).
 */
internal fun endContact(points: List<Sample>): Float {
    val end = points.lastOrNull()?.time ?: return Float.NaN
    var sum = 0
    var count = 0
    for (index in points.indices.reversed()) {
        val point = points[index]
        if (end - point.time > END_CONTACT_MILLIS) break
        if (point.step > 0) {
            sum += point.step
            count++
        }
    }
    return if (count == 0) Float.NaN else sum.toFloat() / count
}

private const val MIN_DELETE_GAP_MILLIS = 250L

/** The number above, for the raw input log. */
internal val swipePiecesTuning: Map<String, Any> get() = mapOf("minDeleteGapMillis" to MIN_DELETE_GAP_MILLIS)

/**
 * A finger near the top of the touchpad often loses contact for a moment, which splits a swipe into pieces.
 * A short touch straight after a swipe, or straight before one, can be part of that swipe and not a touch of its own.
 */
internal class SwipePieces(
    private val flickerMillis: () -> Long,
    private val canContinue: () -> Boolean,
    private val isTapKey: (String) -> Boolean,
) {
    private var last: List<Sample>? = null
    private var held: Contact.Settlement? = null

    fun clear() {
        last = null
        held = null
    }

    /** A stroke this soon after a swipe is more likely the rest of that swipe than a delete. */
    fun canDelete(startedAt: Long): Boolean {
        val finished = last?.last()?.time
        return finished == null || startedAt - finished >= MIN_DELETE_GAP_MILLIS
    }

    /** What this finished contact should count as, once joined to any piece beside it. */
    fun settle(contact: Contact.Settlement, outcome: Contact.Outcome?): Contact.Outcome? {
        val before = held
        held = null
        if (outcome is Contact.Outcome.Swipe) {
            if (outcome.deletesWord && canDelete(outcome.startedAt)) {
                last = null
                return outcome
            }
            val word = outcome.copy(deletesWord = false)
            val joined = if (before != null && follows(before.points, word.points))
                Contact.Outcome.Swipe(before.points + word.points, before.points.first().time, word.endedAt) else word
            last = joined.points
            return joined
        }
        // A configured tap key has to type straight away, and a brush just after a key press is not typing.
        if (contact.points.isEmpty() || !contact.counts || (outcome is Contact.Outcome.Tap && isTapKey(outcome.key))) return outcome
        val previous = last
        // A piece the composer cannot join would be read as a word of its own.
        if (previous != null && canContinue() && follows(previous, contact.points)) {
            last = previous + contact.points
            return Contact.Outcome.Swipe(contact.points, contact.points.first().time, contact.points.last().time)
        }
        held = contact
        return outcome
    }

    private fun follows(before: List<Sample>, after: List<Sample>) = flickerMillis() > 0 &&
        letterSpace(before).sameStroke(letterSpace(after), after.first().time - before.last().time, flickerMillis(), endContact(before))
}
