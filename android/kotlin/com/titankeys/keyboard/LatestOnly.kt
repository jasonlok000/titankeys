package com.titankeys.keyboard

/**
 * Runs jobs one at a time on its own thread, and only the newest of those waiting: a job submitted while one runs replaces any
 * that hasn't started. For work each switch of input method asks for and only the last one needs, such as a language's
 * corrections ([LanguageCorrection]): a thread for each switch built every one at once, each holding its half-made corrector
 * (a word list's whole delete index) until it ended, and a run through every input method ran out of memory (owner,
 * 2026-11-17).
 */
class LatestOnly(private val name: String) {
    private val lock = Any()
    private var waiting: (() -> Unit)? = null
    private var running = false

    /** Runs [job] after the one running now, if no newer job comes first. */
    fun submit(job: () -> Unit) {
        synchronized(lock) {
            waiting = job
            if (running) return
            running = true
        }
        Thread(::work, name).apply {
            priority = Thread.MIN_PRIORITY
            isDaemon = true
            start()
        }
    }

    private fun work() {
        while (true) {
            val job = synchronized(lock) {
                val next = waiting
                waiting = null
                if (next == null) running = false
                next
            } ?: return
            try {
                job()
            } catch (error: RuntimeException) {
                // One job's failure is its own: the next still runs.
            }
        }
    }
}
