package com.titankeys.keyboard

/** #85: a burst of contact or app changes reads the names once, after it settles. */
private class FakeClock {
    var now = 0L
    val waiting = ArrayList<Pair<Long, Runnable>>()
    fun post(run: Runnable, millis: Long) { waiting += now + millis to run }
    fun cancel(run: Runnable) { waiting.removeAll { it.second === run } }
    fun advance(millis: Long) {
        now += millis
        val due = waiting.filter { it.first <= now }
        waiting.removeAll(due.toSet())
        due.forEach { it.second.run() }
    }
}

private fun check(passed: Boolean, message: String) {
    if (!passed) throw AssertionError(message)
}

fun main() {
    val clock = FakeClock()
    var reads = 0
    val settle = Settle(3000, clock::post, clock::cancel) { reads++ }
    repeat(20) {
        settle.poke()
        clock.advance(200)
    }
    check(reads == 0, "Nothing is read while changes keep coming: $reads")
    clock.advance(2999)
    check(reads == 1, "20 changes in a row are read once, 3 s after the last: $reads")
    clock.advance(10_000)
    check(reads == 1, "and only once: $reads")
    settle.poke()
    clock.advance(1000)
    settle.stop()
    clock.advance(10_000)
    check(reads == 1 && clock.waiting.isEmpty(), "Stopped, a waiting change is forgotten: $reads")
    settle.poke()
    clock.advance(3000)
    check(reads == 2, "A later change is read again: $reads")
    println("PASS: battery checks")
}
