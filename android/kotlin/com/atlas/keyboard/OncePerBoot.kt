package com.atlas.keyboard

import kotlin.math.abs

/**
 * Decides whether something shows for the first time since the phone started, such as the word on Teams. Restarting TitanKeys
 * is not a new boot, because it is the same [Boot].
 *
 * This class holds the decision, so tests can reach it. The keyboard reads the boot and keeps the last one.
 */
object OncePerBoot {
    /**
     * One start of the phone: Android's boot count where an app can read it, and the time the phone started. The start time
     * moves a little when the clock is set, so two times close together are the same boot.
     */
    data class Boot(val count: Int?, val startedAt: Long) {
        fun same(other: Boot): Boolean =
            if (count != null && other.count != null) count == other.count
            else abs(startedAt - other.startedAt) <= SAME_BOOT_MILLIS
    }

    /** True if nothing has shown since this boot: [last] is the boot it last showed in, or null if it never has. */
    fun due(last: Boot?, now: Boot) = last == null || !last.same(now)

    /**
     * How far apart two start times can be in one boot, when the network corrects the clock while the phone runs. The clock is
     * UTC, so a new time zone does not move it, and a phone can't stop, start again and finish starting within this time.
     */
    const val SAME_BOOT_MILLIS = 60 * 1000L
}
