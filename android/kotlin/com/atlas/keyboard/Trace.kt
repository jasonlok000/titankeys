package com.atlas.keyboard

import android.os.Handler
import android.os.Looper
import com.atlas.engine.Swipe
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Live touchpad activity, published as coalesced path additions only while a screen listens. Each point is its x, its y
 * and how thick to draw the trail there, 1 for a touch of the usual size.
 */
object Trace {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(JSONObject) -> Unit>()
    private val pending = ArrayList<FloatArray>()
    private val thickness = TrailThickness()
    /** The points of the trail being drawn, which a settled stroke takes its sizes from. */
    private val drawn = ArrayList<Sample>()
    private var source: List<Sample>? = null
    private var observed = 0
    private var reset = false
    private var ended = false
    private var scheduled = false
    private val publish = Runnable {
        scheduled = false
        if (listeners.isNotEmpty()) {
            val path = JSONArray()
            for (point in pending) path.put(JSONArray().put(point[0].toDouble()).put(point[1].toDouble()).put(point[2].toDouble()))
            val message = JSONObject().put("event", if (ended) "lift" else "touch")
                .put("points", path).put("reset", reset)
            pending.clear()
            reset = false
            for (listener in listeners) listener(message)
        }
    }

    fun listen(listener: (JSONObject) -> Unit): () -> Unit {
        listeners += listener
        return {
            listeners -= listener
            if (listeners.isEmpty()) {
                main.removeCallbacks(publish)
                scheduled = false
                pending.clear()
                drawn.clear()
                source = null
                observed = 0
            }
        }
    }

    val listening get() = listeners.isNotEmpty()

    /** Replaces the trail with the path the recogniser read, now that it is settled what was one stroke. */
    fun stroke(swipe: Swipe) {
        if (listeners.isEmpty() || source != null) return
        pending.clear()
        thickness.reset()
        var sized = 0
        for (i in swipe.x.indices) {
            val time = swipe.time[i].toLong()
            // The size of the drawn point at the same moment or just before it.
            while (sized + 1 < drawn.size && drawn[sized + 1].time <= time) sized++
            val step = drawn.getOrNull(sized)?.takeIf { it.time <= time }?.step ?: 0
            add(Sample(swipe.x[i], swipe.y[i] * 3 / 4, time, step))
        }
        drawn.clear()
        reset = true
        ended = true
        if (!scheduled) {
            scheduled = true
            main.postDelayed(publish, 16L)
        }
    }

    /** [carried] is an earlier touch that this one may be continuing, so the trail keeps it for now. */
    fun touch(points: List<Sample>, ended: Boolean, carried: List<Sample> = emptyList()) {
        if (listeners.isEmpty()) return
        if (source !== points || points.size < observed) {
            source = points
            observed = 0
            pending.clear()
            drawn.clear()
            thickness.reset()
            reset = true
            for (point in carried) if (point.valid) {
                add(point)
                drawn += point
            }
        }
        val count = minOf(points.size, Swipe.MAX_POINTS)
        for (index in observed until count) {
            val point = points[index]
            if (point.valid) {
                add(point)
                drawn += point
            }
        }
        observed = count
        this.ended = ended
        if (ended) source = null
        if (!scheduled) {
            scheduled = true
            main.postDelayed(publish, 16L)
        }
    }

    private fun add(point: Sample) {
        pending += floatArrayOf(point.x, point.y, thickness.next(point.x, point.y, point.step))
    }
}
