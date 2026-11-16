package com.atlas.keyboard

/**
 * Runs [work] once a burst of changes has settled: each [poke] moves it to [millis] after the latest one. A contact sync or an app
 * update sends many changes in a row, and reading the names again for each would wake the phone for nothing (#85). [post] and
 * [cancel] are a Handler's postDelayed and removeCallbacks, so it runs on that Handler's thread and checks need no Android.
 */
class Settle(
    private val millis: Long,
    private val post: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val work: () -> Unit,
) {
    private val run = Runnable { work() }

    fun poke() {
        cancel(run)
        post(run, millis)
    }

    /** Forgets a change still waiting. */
    fun stop() = cancel(run)
}
