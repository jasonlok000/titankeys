package com.atlas.keyboard

import android.view.View

/**
 * Every press on the touchscreen in TitanKeys' own screens plays something (owner, 2026-11-13: "Add haptics to every touch screen
 * press on the app"). A control that changes something plays what it means: a switch locks or lets go, a stepper ticks, a pick is
 * done, a wipe is gone, a refusal is no. Every other press, ✕, ←, a row, a button, a pill, a link, Next or Skip, plays Press, one
 * light tick, as a click. Android's stock click stays off (docs/haptics.md, rule 5).
 *
 * This file is the one place a press goes through: [onPress] for the native screens, and [InkPress] for every press in Ink's
 * pages. tools/checks/app-haptics.test.ts fails on a press handler set any other way.
 */

/**
 * Makes [this] a control that plays [haptic] as it is pressed, then does [action]: Press for a plain press. Null is for a control
 * whose [action] plays its own haptic, for what it means, such as a switch, so no Press plays on top of it.
 */
fun View.onPress(haptics: Haptics, haptic: Haptic? = Haptic.AppPress, action: () -> Unit) {
    isHapticFeedbackEnabled = false
    setOnClickListener {
        haptic?.let(haptics::play)
        action()
    }
}

/**
 * Press for each press in Ink's pages. Ink reports every press as it lands, the ✕ and ← in its header too, but a control's own
 * meaning comes a moment later, from the page's script. So Press waits [WAIT_MILLIS], and a meaning that comes in that time
 * takes its place ([meaning]): a switch only locks, never clicks then locks. No Android parts, so tests/haptics can run it.
 */
class InkPress(private val schedule: (Runnable, Long) -> Unit, private val cancel: (Runnable) -> Unit, play: () -> Unit) {
    private val pending = Runnable { play() }

    /** A press landed: Press plays in [WAIT_MILLIS], unless a meaning comes first. A second press starts the wait again. */
    fun pressed() {
        cancel(pending)
        schedule(pending, WAIT_MILLIS)
    }

    /** The control's own haptic is about to play: the waiting Press goes. */
    fun meaning() = cancel(pending)

    companion object {
        /**
         * How long Press waits for a control's own haptic, in ms: long enough for the page's script to answer the press, and short
         * enough to feel like the press itself. Not tried on the phone yet; the lab is the place to tune it.
         */
        const val WAIT_MILLIS = 50L
    }
}
