package com.atlas.keyboard

import kotlin.math.max

/**
 * What the Back key does while the keyboard shows and a panel is open, so that one push closes the panel and does nothing else.
 *
 * One push of Back can reach the keyboard as more than one pair of events. On the Titan 2 Elite the key's own down and up come
 * first, and a second down and up with the same time often come 10 to 30 ms after that up, 85 to 150 ms after the first down.
 * The panel used to close on the first up, and the second up then found no panel and hid the keyboard. So every event of the
 * push that closed the panel is used up here, and only a push that comes later is a new Back.
 */
class BackPush {
    enum class Action {
        /** The first event of a push with a panel open. It closes the panel. */
        ClosePanel,
        /** A later event of the push that closed the panel. It does nothing. */
        UseUp,
        /** Back with no panel, which does what Back normally does. */
        Normal,
    }

    /** The time of the last event of the push that closed a panel. */
    private var lastEvent = Long.MIN_VALUE
    /** When the push that closed the panel went down, until its release arrives. */
    private var pushedAt: Long? = null

    /** What this Back event does. [panelOpen] is whether a panel shows now, [time] is the event's time and [downTime] its press's. */
    fun event(down: Boolean, panelOpen: Boolean, time: Long, downTime: Long): Action {
        if (panelOpen) {
            lastEvent = time
            pushedAt = if (down) downTime else null
            return Action.ClosePanel
        }
        val ownPress = pushedAt == downTime
        if (!ownPress && time > lastEvent + AFTER_PUSH_MILLIS) return Action.Normal
        lastEvent = max(lastEvent, time)
        if (ownPress && !down) pushedAt = null
        return Action.UseUp
    }

    companion object {
        /**
         * How long after the last event of the push later events still belong to it. The second pair came at most 90 ms after
         * the event before it, and a person's second push of Back comes later than this.
         */
        const val AFTER_PUSH_MILLIS = 250L
    }
}
