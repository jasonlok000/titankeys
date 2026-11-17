package com.titankeys.keyboard

/**
 * TitanKeys Debug's owner mark (owner, 2026-11-17: the Orchestrator reads Debug's log for "weird interactions"): three quick
 * presses of Shift, with no other key between, write a MARK line with the time into the log, so the moment can be found. No
 * one presses Shift three times in a row by accident: two locks it, and a third press only unlocks it again.
 *
 * The first two presses do what Shift does; at the third, the keyboard puts Shift, Alt and Chinese's bigger menu back as they
 * were before the first, so the mark changes nothing. This holds the decision, so tests/rawlog can reach it.
 */
class OwnerMark(private val windowMillis: Long = WINDOW_MILLIS) {
    private val presses = ArrayList<Long>(3)

    /** A Shift went down at [time] (no repeat). True if it's the third of three within [windowMillis]: the mark. */
    fun shift(time: Long): Boolean {
        presses.removeAll { time - it > windowMillis }
        presses += time
        if (presses.size < 3) return false
        presses.clear()
        return true
    }

    /** This press is the first of a new run: what the keyboard should remember to put back. */
    val first get() = presses.size == 1

    /** Any other key, or a touch: the run starts over. */
    fun other() = presses.clear()

    companion object {
        /** Three presses within this long. Quick, but in reach of a deliberate thumb. */
        const val WINDOW_MILLIS = 900L
    }
}

/** The wall-clock time span of a typing log, from its header lines, for LogPull's answer. Null if it has none. */
object RawLogRange {
    private val HEADER = Regex("\"k\":\"header\".*?\"t\":(\\d+),\"wall\":(\\d+)")
    private val TIME = Regex("\"t\":(\\d+)")

    /** From the first line's wall-clock time to the last's, in epoch milliseconds, worked out from each part's header. */
    fun of(lines: Sequence<String>): Pair<Long, Long>? {
        var offset: Long? = null
        var first: Long? = null
        var last: Long? = null
        for (line in lines) {
            HEADER.find(line)?.let { header ->
                offset = header.groupValues[2].toLong() - header.groupValues[1].toLong()
            }
            val known = offset ?: continue
            val time = TIME.find(line)?.groupValues?.get(1)?.toLong() ?: continue
            val wall = time + known
            if (first == null) first = wall
            last = wall
        }
        val start = first ?: return null
        return start to (last ?: start)
    }
}
