package com.atlas.connected

import android.view.MotionEvent
import com.atlas.engine.Swipe
import com.atlas.keyboard.Sample
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The touchpad's size for each point of a swipe, for data contributions only. It records nothing unless contributions
 * are on. The size is in the sensor's own steps, round(MotionEvent.getSize() * 255), as the touch size study settled
 * (#30): touch major is scaled to the screen, so it is not used.
 */
object TouchSizes {
    @Volatile
    var recording = false

    private const val CAPACITY = 4096
    /** A point whose time is this close to a recorded size takes it. Points made between events have none. */
    private const val MATCH_MILLIS = 2L
    private val times = LongArray(CAPACITY)
    private val sizes = IntArray(CAPACITY)
    private var next = 0
    private var count = 0
    private val attached = WeakHashMap<Swipe, IntArray>()

    fun record(event: MotionEvent) {
        if (!recording) return
        synchronized(this) {
            for (i in 0 until event.historySize) add(event.getHistoricalEventTime(i), event.getHistoricalSize(i))
            if (event.actionMasked != MotionEvent.ACTION_CANCEL) add(event.eventTime, event.size)
        }
    }

    private fun add(time: Long, size: Float) {
        times[next] = time
        sizes[next] = (size * 255).roundToInt().coerceIn(0, 255)
        next = (next + 1) % CAPACITY
        if (count < CAPACITY) count++
    }

    /** Keeps the sizes for a swipe made from these points, while the swipe is still in use. */
    fun attach(swipe: Swipe, points: List<Sample>) {
        if (!recording) return
        synchronized(this) {
            attached[swipe] = IntArray(points.size) { sizeAt(points[it].time) }
        }
    }

    /** The sizes of a swipe, with -1 for a point that has none, or null when the swipe has no sizes. */
    fun of(swipe: Swipe): IntArray? = synchronized(this) { attached[swipe] }

    private fun sizeAt(time: Long): Int {
        var best = -1
        var bestGap = Long.MAX_VALUE
        for (i in 0 until count) {
            val gap = abs(times[i] - time)
            if (gap < bestGap) {
                bestGap = gap
                best = sizes[i]
            }
        }
        return if (bestGap <= MATCH_MILLIS) best else -1
    }

    fun clear() = synchronized(this) {
        count = 0
        next = 0
        attached.clear()
    }
}
