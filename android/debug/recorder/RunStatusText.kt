package com.titankeys.keyboard

/**
 * TitanKeys Debug only: the words of the phone run's notification (RunStatus), without Android, so the touch simulator's offline
 * checks test them (tools/dev/touch/test). Counts and names only, never typed text.
 */
object RunStatusText {
    class Status(
        val batch: Int, val batches: Int, val name: String,
        val yes: Int, val no: Int, val look: Int,
        val eta: String, val note: String, val done: Boolean,
    )

    /** The title, the counts line and the third line: the last NO, or why the run waits. */
    fun lines(s: Status): Triple<String, String, String> {
        val title = if (s.done) "Tests: done, ${s.batches} batches" else "Tests: batch ${s.batch}/${s.batches}, ${s.name}"
        val counts = "YES ${s.yes} · NO ${s.no} · LOOK ${s.look}" + if (s.done || s.eta.isEmpty()) "" else " · ETA ${s.eta}"
        return Triple(title, counts, s.note.take(80))
    }
}
