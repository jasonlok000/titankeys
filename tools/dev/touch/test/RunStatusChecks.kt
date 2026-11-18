package com.titankeys.keyboard

/** The run notification's words (RunStatusText), checked without Android: tools/dev/touch/test/run.sh. */
fun main() {
    fun check(ok: Boolean, what: String) { if (!ok) { println("FAIL $what"); kotlin.system.exitProcess(1) } }
    val running = RunStatusText.lines(RunStatusText.Status(4, 12, "gestures-fix", 120, 3, 9, "02:40", "waiting: another app in front", false))
    check(running.first == "Tests: batch 4/12, gestures-fix", "title: ${running.first}")
    check(running.second == "YES 120 · NO 3 · LOOK 9 · ETA 02:40", "counts: ${running.second}")
    check(running.third == "waiting: another app in front", "note: ${running.third}")
    val done = RunStatusText.lines(RunStatusText.Status(12, 12, "languages", 300, 5, 20, "07:10", "", true))
    check(done.first == "Tests: done, 12 batches" && done.second == "YES 300 · NO 5 · LOOK 20", "done: $done")
    check(RunStatusText.lines(RunStatusText.Status(1, 2, "x", 0, 0, 0, "", "n".repeat(200), false)).third.length == 80, "note cut at 80")
    println("Run status checks passed")
}
