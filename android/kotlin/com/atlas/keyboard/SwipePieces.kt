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

    /** What this finished contact should count as, once joined to any piece beside it. */
    fun settle(contact: Contact.Settlement, outcome: Contact.Outcome?): Contact.Outcome? {
        val before = held
        held = null
        if (outcome is Contact.Outcome.Swipe) {
            // A stroke this soon after a swipe is more likely the rest of that swipe than a delete.
            val finished = last?.last()?.time
            if (outcome.deletesWord && (finished == null || outcome.startedAt - finished >= MIN_DELETE_GAP_MILLIS)) {
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
        letterSpace(before).sameStroke(letterSpace(after), after.first().time - before.last().time, flickerMillis())
}
