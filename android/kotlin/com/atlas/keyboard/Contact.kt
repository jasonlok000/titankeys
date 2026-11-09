package com.atlas.keyboard

import android.view.KeyEvent
import com.atlas.engine.Swipe
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Turns one finger contact on the touchpad into a tap, a swipe or nothing.
 *
 * Pressing a physical key makes the keyboard driver power the touchpad off and report a lift, so a
 * contact that overlaps a key press is never typing. The key event and that lift reach the keyboard
 * through different windows and can arrive in either order, so a finished contact is held for
 * [SETTLE_MILLIS] before it counts, and any key press from its start until [SETTLE_MILLIS] after its lift
 * cancels it.
 *
 * A new contact that starts shortly after a key event, or longer after one while the user types steadily,
 * only counts once it has moved far, so a finger that brushes the keys between presses does not type.
 * A second Space press in a row adds no pause.
 */
class Contact {
    sealed interface Outcome {
        data class Tap(val key: String, val endedAt: Long) : Outcome
        data class Swipe(val points: List<Sample>, val startedAt: Long, val endedAt: Long, val deletesWord: Boolean = false) : Outcome
    }

    private sealed interface State {
        data object Idle : State
        /** A contact that cannot type: it began during a key press or was interrupted. */
        data object Ignored : State
        class Touching(val first: Sample, val afterKey: Boolean) : State {
            val points = arrayListOf(first)
            val pickup = Pickup(first)
            var travel = 0f
            val counts get() = !afterKey || travel >= AFTER_KEY_SWIPE_KEYS
        }
    }

    class Settlement internal constructor(
        val deadline: Long,
        internal val outcome: Outcome?,
        internal val startedAt: Long,
        internal val endedAt: Long,
        internal val points: List<Sample>,
        internal val counts: Boolean,
    ) {
        internal var cancelled = false
    }

    private var state: State = State.Idle
    private val pending = ArrayDeque<Settlement>()
    private val heldKeys = HashSet<Int>()
    private var blockedUntil = Long.MIN_VALUE
    private var lastKeyCode = 0
    private var doubleSpace = false
    private var lastKeyDown = Long.MIN_VALUE
    private var typing = false

    /** Alt or Sym is on, so swipes turn strip pages: they can be shorter and start on any key. */
    var paging = false

    /** Whether the current contact could still type, with how far it has moved in key widths. */
    val travel: Float? get() = (state as? State.Touching)?.takeIf { it.counts }?.travel

    val points: List<Sample> get() = (state as? State.Touching)?.points ?: emptyList()

    fun down(sample: Sample) {
        state = if (sample.valid && heldKeys.isEmpty()) State.Touching(sample, sample.time < blockedUntil) else State.Ignored
    }

    fun move(samples: List<Sample>) {
        val touching = state as? State.Touching ?: return
        for (sample in samples) {
            if (!sample.valid || sample.time < touching.points.last().time
                || sample.time - touching.first.time > Swipe.MAX_DURATION_MILLIS
                || touching.points.size >= Swipe.MAX_POINTS) {
                state = State.Ignored
                return
            }
            touching.points += sample
            touching.pickup.add(sample, touching.points.size - 1)
            val dx = (sample.x - touching.first.x) * 10
            val dy = (sample.y - touching.first.y) * 4
            touching.travel = max(touching.travel, hypot(dx, dy))
        }
    }

    /** Each ended contact owns its deadline, independently of the next contact. */
    fun up(samples: List<Sample>, now: Long): Settlement? {
        move(samples)
        val touching = state as? State.Touching
        state = State.Idle
        if (touching == null) return null
        val last = touching.points.last()
        val settlement = Settlement(now + SETTLE_MILLIS, classify(touching), touching.first.time, last.time, touching.points.toList(), touching.counts)
        if (pending.size == MAX_PENDING_CONTACTS) pending.removeFirst()
        pending.addLast(settlement)
        return settlement
    }

    fun cancel() {
        cancelActive()
        pending.clear()
    }

    fun cancelActive() {
        state = State.Idle
    }

    fun settle(contact: Settlement, now: Long): Outcome? {
        if (now < contact.deadline || !pending.remove(contact)) return null
        return contact.outcome.takeUnless { contact.cancelled }
    }

    fun key(code: Int, down: Boolean, time: Long) {
        if (down) {
            heldKeys += code
            for (contact in pending) if (time in contact.startedAt..contact.endedAt + SETTLE_MILLIS) contact.cancelled = true
            if (state is State.Touching) state = State.Ignored
            doubleSpace = code == KeyEvent.KEYCODE_SPACE && lastKeyCode == code
            lastKeyCode = code
            typing = time - lastKeyDown in 0..TYPING_GAP_MILLIS
            lastKeyDown = time
        } else heldKeys -= code
        // A second Space press in a row adds no pause.
        if (doubleSpace && code == KeyEvent.KEYCODE_SPACE) return
        blockedUntil = max(blockedUntil, time + if (typing) TYPING_COOLDOWN_MILLIS else KEY_COOLDOWN_MILLIS)
    }

    fun releaseKeys() {
        heldKeys.clear()
        cancel()
    }

    private fun classify(touching: State.Touching): Outcome? {
        if (!touching.counts) return null
        val points = touching.points
        val first = points.first()
        val last = points.last()
        if (touching.travel >= if (paging) PAGE_KEYS else SWIPE_KEYS) {
            if (paging) return Outcome.Swipe(points.toList(), first.time, last.time)
            // Checked here because the pickup below trims a swipe to its first letter, which loses its real start.
            val deletes = isDeleteWordSwipe(points)
            touching.pickup.finish()
            val entry = touching.pickup.entry
            if (entry != null) return Outcome.Swipe(listOf(entry) + points.subList(touching.pickup.entryIndex, points.size), first.time, last.time, deletes)
            if (deletes || Layout.isLetter(Layout.keyAt(first.x, first.y))) return Outcome.Swipe(points.toList(), first.time, last.time, deletes)
            return null
        }
        val key = Layout.keyAt(first.x, first.y)
        return if (key.isNotEmpty() && key == Layout.keyAt(last.x, last.y)) Outcome.Tap(key, last.time) else null
    }

    /**
     * A swipe that starts on a key other than a letter begins at the first letter the path clearly
     * travels through, ignoring shallow grazes of a corner on the way.
     */
    private class Pickup(first: Sample) {
        var entry: Sample? = null
        var entryIndex = -1
        private val enabled = Layout.keyAt(first.x, first.y).let { it.isNotEmpty() && !Layout.isLetter(it) }
        private var previous = first
        private var pending = 0.toChar()
        private var evidence = 0f
        private var closest = 0f
        private var entryDx = 0f
        private var entryDy = 0f
        private var turning = 0f
        private var pendingIndex = 0
        private var pendingEntry: Sample? = null

        fun add(sample: Sample, index: Int) {
            if (!enabled || entry != null) {
                previous = sample
                return
            }
            val dx = (sample.x - previous.x) * 10
            val dy = (sample.y - previous.y) * 4
            val distance = hypot(dx, dy)
            // Integrating along the path keeps the decision independent of the sensor's event rate.
            val steps = max(1, ceil(distance / SAMPLE_DISTANCE).toInt())
            for (step in 1..steps) {
                if (entry != null) break
                val t = (step - .5f) / steps
                val x = previous.x + (sample.x - previous.x) * t
                val y = previous.y + (sample.y - previous.y) * t
                val key = Layout.keyAt(x, y)
                val letter = if (Layout.isLetter(key)) key[0].lowercaseChar() else 0.toChar()
                if (letter != pending) {
                    pending = letter
                    evidence = 0f
                    closest = Float.POSITIVE_INFINITY
                    turning = 0f
                    entryDx = if (distance == 0f) 0f else dx / distance
                    entryDy = if (distance == 0f) 0f else dy / distance
                    pendingIndex = index
                    pendingEntry = Sample(x, y, previous.time + ((sample.time - previous.time) * t).toLong())
                }
                if (letter == 0.toChar()) continue
                val (centreX, centreY) = Layout.centre(letter)
                val cx = (x - centreX) * 10
                val cy = (y - centreY) * 4
                // Travel near an edge still counts, so the whole sensor stays usable.
                val weight = .25f + .75f * max(0f, 1 - 4 * (cx * cx + cy * cy))
                evidence += distance / steps * weight
                closest = min(closest, hypot(cx, cy))
                turning = if (distance > 0 && (dx * entryDx + dy * entryDy) / distance < STRAIGHT) turning + distance / steps else 0f
                if (evidence >= REQUIRED_EVIDENCE && (closest <= .3f || turning >= .15f)) commit()
            }
            previous = sample
        }

        /** Stopping inside a letter also shows intent, including at an outer edge. */
        fun finish() {
            if (entry == null && pending != 0.toChar() && evidence >= REQUIRED_EVIDENCE) commit()
        }

        private fun commit() {
            entry = pendingEntry
            entryIndex = pendingIndex
        }
    }

    companion object {
        const val SETTLE_MILLIS = 30L
        /** Movement in key widths before a contact becomes a swipe. */
        const val SWIPE_KEYS = .6f
        /** How far a swipe must go, in keys across or rows down, to turn a strip page or jump an emoji group. */
        const val PAGE_KEYS = .45f
        /** Movement in key widths before a contact that starts during a key cooldown counts. */
        private const val AFTER_KEY_SWIPE_KEYS = 1.5f
        private const val KEY_COOLDOWN_MILLIS = 200L
        /** Key presses this close together count as steady typing. */
        private const val TYPING_GAP_MILLIS = 500L
        private const val TYPING_COOLDOWN_MILLIS = 500L
        private const val SAMPLE_DISTANCE = .025f
        private const val REQUIRED_EVIDENCE = .12f
        private const val STRAIGHT = .819152f
        const val MAX_PENDING_CONTACTS = 16
    }
}
