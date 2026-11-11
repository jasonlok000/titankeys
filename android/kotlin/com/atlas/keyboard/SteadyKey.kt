package com.atlas.keyboard

/**
 * The key under a finger that rests on the touchpad, held steady for the marks that show it. The touchpad reports a resting finger
 * as short lifts and new touches at the same place, and a finger on the line between two keys flips from one to the other. So a lift
 * clears the key only when no touch has come back for [LIFT_MILLIS], and another key takes over only when the finger is clearly
 * inside it. Places are from 0 to 1 across and down the touchpad, as [keyAt] reads them.
 */
class SteadyKey(private val keyAt: (Float, Float) -> String) {
    var key = ""
        private set
    private var liftedAt: Long? = null
    /**
     * The physical keys down now. The touchpad reports a lift when a key goes down and then nothing until it is let go, so the
     * key stays while a key is down, as when a key toggles a quick setting under a resting finger.
     */
    private val keysDown = HashSet<Int>()

    /** A physical key went down. */
    fun keyDown(code: Int) {
        keysDown += code
    }

    /**
     * A physical key was let go at [time]. Returns true when it was the last one: then a resting finger is reported again at once,
     * and if none is, the key clears as after a lift.
     */
    fun keyUp(code: Int, time: Long): Boolean {
        if (!keysDown.remove(code) || keysDown.isNotEmpty()) return false
        liftedAt = time
        return true
    }

    /** No key is down any more, as when the keyboard hides. */
    fun keysReleased() = keysDown.clear()

    /** A finger is at [x], [y]. Returns true if [key] changed. */
    fun touch(x: Float, y: Float): Boolean {
        liftedAt = null
        val next = keyAt(x, y)
        if (next == key) return false
        if (key.isNotEmpty() && next.isNotEmpty() && !clearlyIn(next, x, y)) return false
        key = next
        return true
    }

    /** The finger lifted at [time]. The key stays until [expire] finds that no touch came back. */
    fun lift(time: Long) {
        liftedAt = time
    }

    /** Clears the key when the finger has stayed off for [LIFT_MILLIS] at [now]. Returns true if [key] changed. */
    fun expire(now: Long): Boolean {
        val lifted = liftedAt ?: return false
        if (keysDown.isNotEmpty() || now - lifted < LIFT_MILLIS) return false
        liftedAt = null
        if (key.isEmpty()) return false
        key = ""
        return true
    }

    fun clear() {
        key = ""
        liftedAt = null
    }

    /** True if the places a little to each side of [x], [y] are on [next] too, so the finger is not on a line between keys. */
    private fun clearlyIn(next: String, x: Float, y: Float) = keyAt(x - MARGIN_ACROSS, y) == next && keyAt(x + MARGIN_ACROSS, y) == next
        && keyAt(x, y - MARGIN_DOWN) == next && keyAt(x, y + MARGIN_DOWN) == next

    companion object {
        /** A resting finger's touches came back within 60 ms of each lift. */
        const val LIFT_MILLIS = 150L
        /** An eighth of a key across, and a tenth of a row down. */
        private const val MARGIN_ACROSS = .0125f
        private const val MARGIN_DOWN = .025f
    }
}
