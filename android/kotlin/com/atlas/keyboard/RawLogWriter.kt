package com.atlas.keyboard

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Moves log lines from the keyboard to [store] on a background thread. The keyboard only puts a line in a bounded queue
 * and never waits: when the queue is full the line is lost, and a gap marker takes its place in the log.
 */
internal class RawLogWriter(
    private val store: RawLogStore,
    capacity: Int = CAPACITY,
    private val clock: () -> Long,
) {
    private val queue = ArrayBlockingQueue<String>(capacity)
    private var dropped = 0L
    private var skipped = 0L
    private var failed = 0L
    private var unflushed = false
    /** Changes when Delete all removes the queue, so a line already taken from the queue is forgotten too. */
    @Volatile private var epoch = 0
    @Volatile var closed = false
        private set
    private val worker = thread(isDaemon = true, name = "TitanKeys raw log", priority = Thread.NORM_PRIORITY - 1) { run() }

    val backlog get() = queue.size

    /** Never waits for the disk. */
    @Synchronized
    fun add(line: String) {
        if (closed) return
        if (dropped > 0) {
            if (!queue.offer(RawLogLines.gap("queue", dropped, clock()))) {
                dropped++
                return
            }
            dropped = 0
        }
        if (skipped > 0) {
            if (!queue.offer(RawLogLines.gap("leaving", skipped, clock()))) {
                dropped++
                return
            }
            skipped = 0
        }
        if (!queue.offer(line)) dropped++
    }

    /** Leaves out a line that must not be recorded. A gap marker goes in the log before the next line that is. */
    @Synchronized
    fun skip() {
        skipped++
    }

    /** Asks for the waiting lines to reach the disk. If the queue is full, the writer is busy and flushes when it is idle. */
    fun flush() {
        queue.offer(FLUSH)
    }

    /** Forgets the waiting lines. For Delete all: call it while holding the lock of [store]. */
    fun discard() {
        epoch++
        queue.clear()
    }

    /** Stops taking lines. The lines already waiting are still written. */
    fun close() {
        closed = true
        queue.offer(FLUSH)
    }

    fun join(millis: Long) = worker.join(millis)

    private fun run() {
        val batch = ArrayList<String>(BATCH)
        while (true) {
            // An idle thread with nothing to flush sleeps until there is work, so the log costs nothing between words.
            val first = if (unflushed) queue.poll(IDLE_MILLIS, TimeUnit.MILLISECONDS) else queue.take()
            val seen = epoch
            // A problem in the log must never stop the keyboard.
            try {
                synchronized(store) {
                    if (first == null) flushStore()
                    else if (seen == epoch) {
                        batch.add(first)
                        queue.drainTo(batch, BATCH - 1)
                        for (line in batch) if (line === FLUSH) flushStore() else write(line)
                    }
                }
            } catch (_: Exception) {
                failed++
            }
            batch.clear()
            if (closed && queue.isEmpty()) {
                try {
                    store.flush()
                } catch (_: Exception) {
                    // Nothing else can be done with these lines.
                }
                return
            }
        }
    }

    private fun write(line: String) {
        if (failed > 0 && store.append(RawLogLines.gap("error", failed, clock()))) failed = 0
        if (failed > 0 || !store.append(line)) failed++
        unflushed = true
    }

    private fun flushStore() {
        if (!store.flush()) failed++
        unflushed = false
    }

    private companion object {
        const val CAPACITY = 4096
        const val BATCH = 256
        const val IDLE_MILLIS = 2000L
        /** Compared by identity, so that no line of text can be taken for it. */
        val FLUSH = String(CharArray(0))
    }
}
