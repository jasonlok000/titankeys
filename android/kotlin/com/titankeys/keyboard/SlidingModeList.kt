package com.titankeys.keyboard

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
 * readings ([trustworthy]), from two separate fields the user interacted with, with no sign of Sliding Mode 1 in between: the first makes it a suspect,
 * which Setup doesn't show.
 */
internal class SlidingModeList(outside: Set<String>, suspects: Set<String>) {
    /** The apps Setup lists. */
    var outside = outside - ANDROID_SETTINGS
        private set

    /** Apps with one trustworthy reading outside Sliding Mode 1, not shown yet. */
    var suspects = suspects - ANDROID_SETTINGS
        private set

    /** [app] is on Sliding Mode 1: it leaves the list and its doubt at once. */
    fun mode1(app: String) {
        outside = outside - app
        suspects = suspects - app
    }

    /** A field in [app] gave a trustworthy reading outside Sliding Mode 1. The second in a row puts it on the list. */
    fun outsideReading(app: String) {
        if (app == ANDROID_SETTINGS || app in outside) return
        if (app in suspects) {
            suspects = suspects - app
            outside = outside + app
        } else suspects = suspects + app
    }

    companion object {
        const val EVIDENCE_VERSION = 2
        fun restored(outside: Set<String>, suspects: Set<String>, version: Int) =
            if (version == EVIDENCE_VERSION) SlidingModeList(outside, suspects) else SlidingModeList(emptySet(), emptySet())
        // The Titan's Scroll assistant picker has no entry for the Android Settings app.
        private const val ANDROID_SETTINGS = "com.android.settings"
        /**
         * One field's readings of the mode, [early] and [late], are trustworthy when the keyboard was still in that app's field
         * for both ([sameField]), both were read (a phone that can't be read says nothing), they agree, neither is Sliding Mode 1,
         * and no Sliding Mode 1 touch came from the touchpad in between ([mode1Touch]). The late one comes seconds after the
         * field started, when any screen opening has long finished and the phone has set the mode for the app in front.
         */
        fun trustworthy(early: String?, late: String?, sameField: Boolean, mode1Touch: Boolean) =
            sameField && !mode1Touch && early == "no" && late == "no"

        /** persist.sys.touchpad_forcescroll in Sliding Mode 1. */
        const val MODE_1 = "yes"
    }
}

/** One actual field session. Input-view restarts are not independent negative evidence. */
internal class SlidingModeField(val app: String, val fieldId: Int, connection: Any?) {
    private var connection = connection?.let { java.lang.ref.WeakReference(it) }
    var interacted = false
    var mode1Touch = false
    private var reported = false
    class Sample(val field: SlidingModeField) { var early: String? = null }
    private var sample: Sample? = null

    fun resume(app: String, fieldId: Int, next: Any?, restarting: Boolean): Boolean {
        if (this.app != app || this.fieldId != fieldId ||
            !(restarting || next != null && connection?.get() === next)) return false
        connection = next?.let { java.lang.ref.WeakReference(it) }
        return true
    }

    fun owns(app: String?, fieldId: Int?, current: Any?) =
        this.app == app && this.fieldId == fieldId && current != null && connection?.get() === current

    fun begin() = Sample(this).also { sample = it }
    fun current(reading: Sample) = sample === reading
    fun negative(reading: Sample, late: String?, sameField: Boolean): Boolean {
        if (reported || !interacted || connection?.get() == null || !current(reading) ||
            !SlidingModeList.trustworthy(reading.early, late, sameField, mode1Touch)) return false
        reported = true
        return true
    }
}
