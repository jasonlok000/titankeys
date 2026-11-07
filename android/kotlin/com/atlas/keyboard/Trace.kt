package com.atlas.keyboard

import android.os.Handler
import android.os.Looper
import com.atlas.engine.Swipe
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

/** Live touchpad activity, published as coalesced path additions only while a screen listens. */
object Trace {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(JSONObject) -> Unit>()
    private val pending = ArrayList<Sample>()
    private var source: List<Sample>? = null
    private var observed = 0
    private var reset = false
    private var ended = false
    private var scheduled = false
    private val publish = Runnable {
        scheduled = false
        if (listeners.isNotEmpty()) {
            val path = JSONArray()
            for (point in pending) path.put(JSONArray().put(point.x.toDouble()).put(point.y.toDouble()))
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
                source = null
                observed = 0
            }
        }
    }

    fun touch(points: List<Sample>, ended: Boolean) {
        if (listeners.isEmpty()) return
        if (source !== points || points.size < observed) {
            source = points
            observed = 0
            pending.clear()
            reset = true
        }
        val count = minOf(points.size, Swipe.MAX_POINTS)
        for (index in observed until count) {
            val point = points[index]
            if (point.valid) pending += point
        }
        observed = count
        this.ended = ended
        if (ended) source = null
        if (!scheduled) {
            scheduled = true
            main.postDelayed(publish, 16L)
        }
    }
}
