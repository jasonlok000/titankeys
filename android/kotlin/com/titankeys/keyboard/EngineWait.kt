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

    /** Load [ticket] of input method [id] began: typing waits for any but English, engine or not (Yale, Russian, Greek). */
    fun loading(ticket: Int, id: String) = loading(ticket, waits = id != InputMethod.ENGLISH_ID)

    /** Load [ticket] began: typing waits for it when [waits]. */
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

    /**
     * Take an uninterrupted batch of physical keys held for the load out of a restarting editor's queue. The stop may have
     * opened already and part of the batch may have run. Touches, navigation and other pending edits keep their usual cleanup.
     */
    fun takeKeys(queue: MutableList<T>, heldKey: (T) -> Boolean): List<T> {
        val first = if (gate != null && queue.firstOrNull() === gate) 1 else 0
        val keys = queue.subList(first, queue.size)
        if (keys.isEmpty() || !keys.all(heldKey)) return emptyList()
        return keys.toList().also {
            queue.clear()
            clear()
        }
    }

    /** The queue was emptied, the stop with it. */
    fun clear() {
        gate = null
    }
}

/** Which editor typed a batch held for a language load. Android can give the same named field a new connection on restart. */
internal class EngineEditor(
    private val app: String?, private val id: Int, private val type: Int, private val options: Int,
    private val privateOptions: String?, connection: Any?,
) {
    private val connection = java.lang.ref.WeakReference(connection)

    fun continues(previous: EngineEditor?, restarting: Boolean, previousStart: Int, previousEnd: Int, start: Int, end: Int): Boolean {
        if (start < 0 || end < 0 || start != previousStart || end != previousEnd) return false
        if (!restarting || previous == null || app.isNullOrEmpty() || app != previous.app || id != previous.id ||
            type != previous.type || options != previous.options || privateOptions != previous.privateOptions) return false
        return id > 0 || connection.get()?.let { it === previous.connection.get() } == true
    }
}

/**
 * The letters of the keys waiting for an input method that is loading, to show plain on the bar instead of a line saying it's
 * getting ready (owner, 2026-11-17: "there shd be no warm up visible to the user"). Each key is (down, repeat, keycode), in
 * Android's codes: a letter's push adds it, Backspace's takes the last one off, repeats and releases add nothing.
 */
object HeldLetters {
    private const val KEYCODE_A = 29
    private const val KEYCODE_Z = 54
    private const val KEYCODE_DEL = 67

    fun of(keys: List<Triple<Boolean, Int, Int>>): String {
        val out = StringBuilder()
        for ((down, repeat, code) in keys) {
            if (!down || repeat > 0) continue
            when (code) {
                in KEYCODE_A..KEYCODE_Z -> out.append('a' + (code - KEYCODE_A))
                KEYCODE_DEL -> if (out.isNotEmpty()) out.setLength(out.length - 1)
            }
        }
        return out.toString()
    }
}
