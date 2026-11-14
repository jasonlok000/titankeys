package com.atlas.keyboard

/**
 * Settings > Setup's list of apps outside Sliding Mode 1, kept so that it can't cry wolf (owner, 2026-11-13: "why does it say
 * sliding mode 1 in my apps are to do and that it doesn't work in chrome and settings, but it's working indeed and on?").
 *
 * The phone copies the mode of the app in front to a property as that app comes in, and the first list trusted one reading of
 * it, 600 ms after a field started. Settings' search and Chrome's address bar take the focus as their screen opens, so that
 * reading could still hold the app before, and an app only left the list on a later reading in a field of its own. One bad
 * reading stuck for good.
 *
 * Now an app leaves the list at the first sign of Sliding Mode 1 ([mode1]): a reading of yes, or a touchpad touch that arrives
 * the way only Sliding Mode 1 sends it, as a screen touch ([Touchpad.sentAsScreenTouch]). It joins only on trustworthy
 * readings ([trustworthy]), from two separate fields, with no sign of Sliding Mode 1 in between: the first makes it a suspect,
 * which Setup doesn't show.
 */
internal class SlidingModeList(outside: Set<String>, suspects: Set<String>) {
    /** The apps Setup lists. */
    var outside = outside
        private set

    /** Apps with one trustworthy reading outside Sliding Mode 1, not shown yet. */
    var suspects = suspects
        private set

    /** [app] is on Sliding Mode 1: it leaves the list and its doubt at once. */
    fun mode1(app: String) {
        outside = outside - app
        suspects = suspects - app
    }

    /** A field in [app] gave a trustworthy reading outside Sliding Mode 1. The second in a row puts it on the list. */
    fun outsideReading(app: String) {
        if (app in outside) return
        if (app in suspects) {
            suspects = suspects - app
            outside = outside + app
        } else suspects = suspects + app
    }

    companion object {
        /**
         * One field's readings of the mode, [early] and [late], are trustworthy when the keyboard was still in that app's field
         * for both ([sameField]), both were read (a phone that can't be read says nothing), they agree, neither is Sliding Mode 1,
         * and no Sliding Mode 1 touch came from the touchpad in between ([mode1Touch]). The late one comes seconds after the
         * field started, when any screen opening has long finished and the phone has set the mode for the app in front.
         */
        fun trustworthy(early: String?, late: String?, sameField: Boolean, mode1Touch: Boolean) =
            sameField && !mode1Touch && !early.isNullOrEmpty() && early == late && early != MODE_1

        /** persist.sys.touchpad_forcescroll in Sliding Mode 1. */
        const val MODE_1 = "yes"
    }
}
