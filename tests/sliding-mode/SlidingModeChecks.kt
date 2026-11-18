package com.titankeys.keyboard

/**
 * Settings > Setup's Sliding Mode 1 list (SlidingModeList). The owner's phone listed Chrome and Settings as outside Sliding
 * Mode 1 while both were on it and swiping worked (2026-11-13). Their fields take the focus as their screens open, so the one
 * reading the first list trusted, 600 ms in, could still hold the mode of the app before.
 */
private var failures = 0

private fun check(ok: Boolean, what: String) {
    if (!ok) {
        failures++
        println("FAIL: $what")
    }
}

/** The first list's rule, for the misread: one reading, 600 ms in, decided. */
private fun firstRule(outside: Set<String>, app: String, early: String) = if (early == "yes") outside - app else outside + app

private const val CHROME = "com.android.chrome"
private const val SETTINGS = "com.android.settings"
private const val NOTES = "com.example.notes"

/** One field in [app]: readings [early] and [late], and whether a Sliding Mode 1 touch came. Mirrors TitanKeysIme.readSlidingMode. */
private fun SlidingModeList.field(app: String, early: String?, late: String?, mode1Touch: Boolean = false, sameField: Boolean = true) {
    if (early == SlidingModeList.MODE_1 && sameField) mode1(app)
    if (mode1Touch) mode1(app)
    if (late == SlidingModeList.MODE_1 && sameField) mode1(app)
    else if (SlidingModeList.trustworthy(early, late, sameField, mode1Touch)) outsideReading(app)
}

fun main() {
    // The misread: Chrome's address bar starts as Chrome opens from the launcher, whose mode is still set 600 ms in. Chrome is on
    // Sliding Mode 1, which the phone has set by the late reading, and every swipe there comes as a screen touch.
    var first = emptySet<String>()
    for (time in 1..5) first = firstRule(first, CHROME, early = "no")
    check(CHROME in first, "The first rule lists Chrome on early readings alone: the misread reproduces")
    val list = SlidingModeList(emptySet(), emptySet())
    repeat(5) { list.field(CHROME, early = "no", late = "yes") }
    check(CHROME !in list.outside && CHROME !in list.suspects, "Readings that change within a field aren't trusted, and a late yes clears")
    // Settings' search: the early reading misses, and a swipe in it arrives as a screen touch.
    repeat(5) { list.field(SETTINGS, early = "no", late = "no", mode1Touch = true) }
    check(SETTINGS !in list.outside, "A Sliding Mode 1 touch in the field wins over any reading")

    // An app on the list leaves it at the first sign of Sliding Mode 1.
    val listed = SlidingModeList(setOf(CHROME, SETTINGS), emptySet())
    listed.field(CHROME, early = "no", late = "no", mode1Touch = true)
    listed.field(SETTINGS, early = "yes", late = null, sameField = true)
    check(listed.outside.isEmpty(), "Chrome and Settings leave the list as soon as they're seen on Sliding Mode 1: ${listed.outside}")

    // An app joins only on trustworthy readings from two fields in a row.
    val notes = SlidingModeList(emptySet(), emptySet())
    notes.field(NOTES, early = "no", late = "no")
    check(NOTES !in notes.outside && NOTES in notes.suspects, "One trustworthy field makes a suspect, which Setup doesn't show")
    notes.field(NOTES, early = "no", late = "no")
    check(NOTES in notes.outside && NOTES !in notes.suspects, "A second puts it on the list")
    // Sliding Mode 1 between the two clears the doubt, so the count starts again.
    val between = SlidingModeList(emptySet(), emptySet())
    between.field(NOTES, early = "no", late = "no")
    between.field(NOTES, early = "yes", late = "yes")
    between.field(NOTES, early = "no", late = "no")
    check(NOTES !in between.outside, "Sliding Mode 1 between two outside fields starts the count again")

    // What makes a reading untrustworthy.
    check(!SlidingModeList.trustworthy("no", "no", sameField = false, mode1Touch = false), "The keyboard left the field")
    check(!SlidingModeList.trustworthy("no", "no", sameField = true, mode1Touch = true), "A Sliding Mode 1 touch came")
    check(!SlidingModeList.trustworthy(null, "no", sameField = true, mode1Touch = false), "The early reading is missing")
    check(!SlidingModeList.trustworthy("", "", sameField = true, mode1Touch = false), "The phone gave nothing")
    check(!SlidingModeList.trustworthy("no", "yes", sameField = true, mode1Touch = false), "The readings disagree")
    check(!SlidingModeList.trustworthy("yes", "yes", sameField = true, mode1Touch = false), "Sliding Mode 1 is never outside it")
    check(SlidingModeList.trustworthy("no", "no", sameField = true, mode1Touch = false), "Two readings that agree, in the same field")

    check(!SlidingModeList.trustworthy("unknown", "unknown", true, false), "Unknown matching values are not negative evidence")
    val migrated = SlidingModeList.restored(setOf(CHROME, SETTINGS), setOf(NOTES), 0)
    check(migrated.outside.isEmpty() && migrated.suspects.isEmpty(), "Old evidence is discarded once for fresh confirmation")
    val current = SlidingModeList.restored(setOf(NOTES, SETTINGS), setOf(CHROME, SETTINGS), SlidingModeList.EVIDENCE_VERSION)
    check(current.outside == setOf(NOTES) && current.suspects == setOf(CHROME), "Current evidence survives, Android Settings never does")
    current.outsideReading(SETTINGS); current.outsideReading(SETTINGS)
    check(SETTINGS !in current.outside && SETTINGS !in current.suspects, "Settings is absent from the vendor picker and never flagged")

    val connection = Any()
    val session = SlidingModeField(CHROME, 7, connection)
    var sample = session.begin().also { it.early = "no" }
    check(!session.negative(sample, "no", true), "Merely opening a focused field contributes no negative vote")
    session.interacted = true
    sample = session.begin().also { it.early = "no" }
    check(session.negative(sample, "no", true), "An interacted field can contribute its first confirmed vote")
    check(!session.negative(sample, "no", true), "A duplicate late callback cannot vote twice")
    check(session.resume(CHROME, 7, connection, false), "Repeated view start on the same connection keeps the session")
    sample = session.begin().also { it.early = "no" }
    check(!session.negative(sample, "no", true), "Repeated view start does not become a second field")
    val restarted = Any()
    check(session.resume(CHROME, 7, restarted, true), "Restarting the same field may replace its input connection")
    sample = session.begin().also { it.early = "no" }
    check(!session.negative(sample, "no", true), "A restarted input connection preserves the consumed vote")
    check(!session.owns(CHROME, 7, connection) && session.owns(CHROME, 7, restarted), "Only the live connection owns readings")
    check(!session.resume(CHROME, 8, restarted, true), "A different field id is a different field even during restart")
    check(!session.resume(NOTES, 7, restarted, true), "Another app cannot inherit a field session")
    check(!session.resume(CHROME, 7, Any(), false), "A new non-restarting connection starts another session")

    val delayed = SlidingModeField(NOTES, 2, connection).also { it.interacted = true }
    val stale = delayed.begin().also { it.early = "no" }
    val fresh = delayed.begin().also { it.early = "no" }
    check(!delayed.negative(stale, "no", true), "A view restart invalidates older timed pairs")
    check(!delayed.negative(fresh, null, true), "An unknown late reading does not consume the vote")
    check(!delayed.negative(fresh, "no", false), "Losing the actual field invalidates the pair")
    check(delayed.negative(fresh, "no", true), "A later valid pair still works after unknown evidence")
    val touched = SlidingModeField(NOTES, 3, connection).also { it.interacted = true; it.mode1Touch = true }
    check(!touched.negative(touched.begin().also { it.early = "no" }, "no", true), "Mode 1 touch wins over negative readings for the whole session")
    val disconnected = SlidingModeField(NOTES, 4, null).also { it.interacted = true }
    check(!disconnected.negative(disconnected.begin().also { it.early = "no" }, "no", true), "An unidentified connection cannot flag an app")

    if (failures > 0) {
        println("$failures Sliding Mode check(s) failed")
        kotlin.system.exitProcess(1)
    }
    println("Sliding Mode checks passed")
}
