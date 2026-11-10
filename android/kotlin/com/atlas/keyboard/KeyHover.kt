package com.atlas.keyboard

/**
 * Which key a finger on the touchpad is over, for the guide's drawing of the keys, held steady so the light does not flicker. A
 * finger near the border of two keys stays on the key it was on until it is clearly inside the next one, [EDGE] of a key in. The
 * touchpad drops contact for a moment now and then, so a lift keeps the key for [DROPOUT_MILLIS] before it lets go.
 * It needs no Android, so the checks can run it with any clock.
 */
internal class KeyHover {
    private var key: String? = null
    private var liftedAt = -1L

    /** The finger is at this point of the touchpad, from 0 to 1 across and down. */
    fun point(x: Float, y: Float) {
        liftedAt = -1
        val under = Layout.keyAt(x, y).takeIf { it.isNotEmpty() } ?: return
        if (key == null || under != key && clearlyInside(under, x, y)) key = under
    }

    /** The finger lifted at [now]. It may only be a dropout, so the key stays for a moment. */
    fun lift(now: Long) {
        if (liftedAt < 0) liftedAt = now
    }

    /** The key under the finger at [now], or null when no finger is on the keys. */
    fun current(now: Long): String? {
        if (liftedAt >= 0 && now - liftedAt > DROPOUT_MILLIS) {
            key = null
            liftedAt = -1
        }
        return key
    }

    /** Whether the point is inside [key] by [EDGE] of a key on every side, as far as the touchpad goes. */
    private fun clearlyInside(key: String, x: Float, y: Float): Boolean {
        val across = EDGE / 10
        val down = EDGE / 4
        return listOf(x - across to y, x + across to y, x to y - down, x to y + down)
            .all { (px, py) -> Layout.keyAt(px.coerceIn(0f, 1f), py.coerceIn(0f, 1f)) == key }
    }

    companion object {
        /** How far into the next key a finger must be before the light moves to it, as a part of a key. */
        const val EDGE = .18f
        /** The longest dropout of contact that the light rides out. */
        const val DROPOUT_MILLIS = 70L
    }
}
