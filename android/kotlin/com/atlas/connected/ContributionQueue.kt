package com.atlas.connected

import java.io.File

/**
 * Swipe examples that wait on the phone to be sent, one JSON line each, in one file. It never grows past [capBytes]:
 * when it would, the oldest examples go first. The user sees its size and can delete it at any time.
 */
internal class ContributionQueue(directory: File, private val capBytes: Long) {
    private val file = File(directory, "queue.jsonl")
    private val lock = Any()

    init {
        directory.mkdirs()
    }

    fun add(line: String) = synchronized(lock) {
        require('\n' !in line) { "An example must be one line" }
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        if (bytes.size > capBytes / 4) return@synchronized
        file.appendBytes(bytes)
        if (file.length() > capBytes) trim(capBytes * 3 / 4)
    }

    fun bytes(): Long = synchronized(lock) { file.length() }

    fun count(): Int = synchronized(lock) { lines().size }

    /** The oldest examples, as many as fit in [maxBytes], but always at least one. */
    fun oldest(maxBytes: Int): List<String> = synchronized(lock) {
        val out = ArrayList<String>()
        var total = 0
        for (line in lines()) {
            total += line.length + 1
            if (out.isNotEmpty() && total > maxBytes) break
            out += line
        }
        out
    }

    /**
     * Removes the examples in [sent], once each, as after they were sent. By content, not by count: the queue may have been
     * cleared or trimmed while they were on their way, and newer examples that weren't sent must stay (bug hunt 2.1, #28).
     */
    fun removeSent(sent: List<String>) = synchronized(lock) {
        val left = sent.groupingBy { it }.eachCount().toMutableMap()
        val lines = lines()
        val kept = lines.filter { line ->
            val count = left[line] ?: 0
            if (count > 0) left[line] = count - 1
            count == 0
        }
        if (kept.size != lines.size) write(kept)
    }

    fun clear() = synchronized(lock) {
        file.delete()
        Unit
    }

    private fun lines(): List<String> = if (file.exists()) file.readLines(Charsets.UTF_8).filter { it.isNotEmpty() } else emptyList()

    private fun trim(target: Long) {
        val lines = lines()
        var total = lines.sumOf { it.toByteArray(Charsets.UTF_8).size + 1L }
        var first = 0
        while (first < lines.size && total > target) total -= lines[first++].toByteArray(Charsets.UTF_8).size + 1L
        write(lines.drop(first))
    }

    private fun write(lines: List<String>) {
        if (lines.isEmpty()) {
            file.delete()
            return
        }
        val staging = File(file.parentFile, "queue.jsonl.tmp")
        staging.writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        if (!staging.renameTo(file)) {
            file.delete()
            staging.renameTo(file)
        }
    }
}
