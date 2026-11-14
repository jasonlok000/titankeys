package com.atlas.autocorrect

/**
 * When autocorrect's word data may try to load again after a load failed (bug hunt #39). One failure, such as running out of memory
 * while engine 2.0 loads too, used to turn autocorrect off until the process died. Now it waits [first] ms, then twice as long after
 * each failure in a row, up to [longest]; a load that works starts it over. Times are uptime milliseconds.
 */
class LoadRetry(private val first: Long = FIRST_MILLIS, private val longest: Long = LONGEST_MILLIS) {
    private var wait = 0L
    private var from = Long.MIN_VALUE

    /** Whether a load may start at [now]. */
    fun mayLoad(now: Long) = now >= from

    /** A load failed at [now]: the next waits longer than the last. */
    fun failed(now: Long) {
        wait = if (wait == 0L) first else minOf(wait * 2, longest)
        from = now + wait
    }

    /** A load worked: a later failure waits [first] again. */
    fun loaded() {
        wait = 0L
        from = Long.MIN_VALUE
    }

    companion object {
        const val FIRST_MILLIS = 30_000L
        const val LONGEST_MILLIS = 600_000L
    }
}
