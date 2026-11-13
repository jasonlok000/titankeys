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
    /** A word menu key was pressed under the finger, and the touchpad hasn't reported the press's own lift yet: see [press]. */
    private var pressLift = false
    /** When that key was let go, for [PRESS_LIFT_MILLIS], or null while it is down. */
    private var releasedAt: Long? = null

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

    /**
     * The finger pressed [pressed], and the press picked a word menu button: the finger is on that key, whatever the touchpad said
     * last, so the mark goes there and stays (owner, 2026-11-12: after a press, the pill stays on the pressed key). Returns true
     * if [key] changed.
     *
     * On the phone, a press under a resting finger ends that touch: the touchpad reports a lift, nearly always after the key is
     * let go (the owner's raw log, 2026-11-11: 1,808 of 1,907 presses, a median 124 ms after the key, at most 0.9 s), and then
     * nothing until the finger moves or lands again (no touch at all between 95% of 3,197 quick presses of one key). So that lift
     * is the press's, not the finger leaving, and [lift] lets it go by. The mark then stays until the touchpad shows the finger
     * again: on another key it moves there, and a later lift clears it as any lift does.
     */
    fun press(pressed: String): Boolean {
        liftedAt = null
        pressLift = true
        releasedAt = null
        if (pressed == key) return false
        key = pressed
        return true
    }

    /** The key that picked a word menu button was let go at [time]. Its lift can still come: see [press]. */
    fun release(code: Int, time: Long) {
        keysDown.remove(code)
        releasedAt = time
    }

    /** No key is down any more, as when the keyboard hides. */
    fun keysReleased() = keysDown.clear()

    /** A finger is at [x], [y]. Returns true if [key] changed. */
    fun touch(x: Float, y: Float): Boolean {
        liftedAt = null
        pressLift = false
        val next = keyAt(x, y)
        if (next == key) return false
        if (key.isNotEmpty() && next.isNotEmpty() && !clearlyIn(next, x, y)) return false
        key = next
        return true
    }

    /**
     * The finger lifted at [time]. The key stays until [expire] finds that no touch came back. The lift of a word menu press,
     * while its key is down or within [PRESS_LIFT_MILLIS] after it, is the press's own: the finger is still there. See [press].
     */
    fun lift(time: Long) {
        val released = releasedAt
        val ofPress = pressLift && (keysDown.isNotEmpty() || released != null && time - released <= PRESS_LIFT_MILLIS)
        pressLift = false
        if (!ofPress) liftedAt = time
    }

    /** Clears the key when the finger has stayed off for [LIFT_MILLIS] at [now] and no key is down. Returns true if [key] changed. */
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
        pressLift = false
    }

    /** True if the places a little to each side of [x], [y] are on [next] too, so the finger is not on a line between keys. */
    private fun clearlyIn(next: String, x: Float, y: Float) = keyAt(x - MARGIN_ACROSS, y) == next && keyAt(x + MARGIN_ACROSS, y) == next
        && keyAt(x, y - MARGIN_DOWN) == next && keyAt(x, y + MARGIN_DOWN) == next

    companion object {
        /** A resting finger's touches came back within 60 ms of each lift. */
        const val LIFT_MILLIS = 150L
        /** How long after a word menu key is let go its press's lift can still come: at most 0.9 s in the owner's raw log. */
        const val PRESS_LIFT_MILLIS = 1000L
        /** An eighth of a key across, and a tenth of a row down. */
        private const val MARGIN_ACROSS = .0125f
        private const val MARGIN_DOWN = .025f
    }
}
