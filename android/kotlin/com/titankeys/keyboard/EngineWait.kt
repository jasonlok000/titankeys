package com.titankeys.keyboard

/**
 * Whether typing must wait for an input method that's loading: one other than English loads off the main thread as a field
 * starts or it's switched to, and until it's ready the keyboard types English. A key typed in that time went to the field as a
 * plain letter, not into the engine's code (owner, 2026-11-17: "it'll type the first character out without putting it as a
 * keycode"), most easily with the keyboard hidden, as nothing on screen says it's still loading; and in an input method with its
 * own letters (Russian, Greek) it would be an English letter. So the keyboard holds what's typed in its queue while [waiting],
 * keys and touches alike ([LoadGate]), and lets it go once the load ends, whichever way it ends.
 *
 * Each load has a ticket ([Languages]' count); only the last one's end counts, as an overtaken load's never applies.
 */
class EngineWait {
    private var awaited = NONE

    /** A load is under way that typing waits for. */
    val waiting get() = awaited != NONE

    /** Load [ticket] began: typing waits for it when [waits], any input method but English. */
    fun loading(ticket: Int, waits: Boolean) {
        awaited = if (waits) ticket else NONE
    }

    /** Load [ticket] ended, ready or not. True if typing waited for it. */
    fun loaded(ticket: Int): Boolean {
        if (ticket != awaited) return false
        awaited = NONE
        return true
    }

    /** The keyboard is going: nothing waits. */
    fun stop() {
        awaited = NONE
    }

    private companion object {
        const val NONE = Int.MIN_VALUE
    }
}

/**
 * The stop in the keyboard's input queue while an input method loads ([EngineWait]): the first key or touch that types puts it
 * in, once, and it and everything after it wait behind it, in the order typed, until the load ends and the stop opens. [make]
 * makes the stop, an item that isn't ready.
 */
class LoadGate<T : Any>(private val make: () -> T) {
    private var gate: T? = null

    /** Whether the stop is in [queue]. */
    fun closed(queue: Collection<T>) = gate?.let { it in queue } == true

    /** Something that types is about to join [queue]: while [loading], the stop goes in first, once. True if it went in now. */
    fun before(loading: Boolean, queue: MutableCollection<T>): Boolean {
        if (!loading || closed(queue)) return false
        gate = make().also(queue::add)
        return true
    }

    /** The load ended: the stop to open, if it's still in [queue]; null while [loading] again, as a field start's new load. */
    fun open(loading: Boolean, queue: Collection<T>): T? {
        if (loading) return null
        val stop = gate ?: return null
        gate = null
        return stop.takeIf { it in queue }
    }

    /** The queue was emptied, the stop with it. */
    fun clear() {
        gate = null
    }
}
