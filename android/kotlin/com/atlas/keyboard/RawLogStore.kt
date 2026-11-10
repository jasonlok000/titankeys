package com.atlas.keyboard

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * The raw input log's files: numbered JSONL files in one folder, each starting with a [header]. When the folder
 * would pass [capBytes], the oldest files go first. Only whole lines reach a file, so a killed process can't
 * leave half a line, and each process starts a new file.
 */
internal class RawLogStore(
    private val directory: File,
    private val capBytes: Long = CAP_BYTES,
    private val fileBytes: Long = FILE_BYTES,
    private val header: () -> String,
) {
    private val buffer = ByteArrayOutputStream()
    private var output: FileOutputStream? = null
    private var currentBytes = 0L
    private var totalBytes = 0L

    /** False if the line was lost. */
    @Synchronized
    fun append(line: String): Boolean {
        val bytes = encode(line)
        // No real line is near this size, and a limit keeps every file and the folder within their sizes.
        if (bytes.size > fileBytes / 2) return false
        return try {
            if (output == null || currentBytes + bytes.size > fileBytes) open()
            add(bytes)
            if (buffer.size() >= BUFFER_BYTES) drain()
            true
        } catch (_: IOException) {
            abandon()
            false
        }
    }

    /** False if some waiting lines were lost. */
    @Synchronized
    fun flush(): Boolean = try {
        drain()
        true
    } catch (_: IOException) {
        abandon()
        false
    }

    /** Closes the current file, so the next line starts a new one. */
    @Synchronized
    fun rotate() {
        try {
            close()
        } catch (_: IOException) {
            abandon()
        }
    }

    /** The folder's size, with the lines that are still waiting to be written. */
    @Synchronized
    fun size(): Long = files().sumOf { it.length() } + buffer.size()

    @Synchronized
    fun deleteAll() {
        abandon()
        files().forEach { it.delete() }
    }

    /** Writes every line to [out], oldest first. New lines go to a new file, so what is copied can't change. */
    fun export(out: OutputStream) {
        for (file in sealed()) {
            try {
                file.inputStream().use { it.copyTo(out, BUFFER_BYTES) }
            } catch (_: FileNotFoundException) {
                // The size limit removed this old file during the copy.
            }
        }
    }

    @Synchronized
    private fun sealed(): List<File> {
        close()
        return files()
    }

    private fun files(): List<File> =
        directory.listFiles { file -> NAME.matches(file.name) }.orEmpty().sortedBy { it.name }

    private fun open() {
        close()
        val first = encode(header())
        directory.mkdirs()
        val existing = files()
        val next = (existing.lastOrNull()?.let { number(it) } ?: 0L) + 1
        totalBytes = existing.sumOf { it.length() }
        output = FileOutputStream(File(directory, "rawlog-${next.toString().padStart(12, '0')}.jsonl"))
        currentBytes = 0
        add(first)
    }

    private fun add(bytes: ByteArray) {
        makeRoom(bytes.size.toLong())
        buffer.write(bytes)
        currentBytes += bytes.size
        totalBytes += bytes.size
    }

    /** Removes old files until [incoming] more bytes fit. The newest file is the one being written. */
    private fun makeRoom(incoming: Long) {
        if (totalBytes + incoming <= capBytes) return
        for (file in files().dropLast(1)) {
            val size = file.length()
            if (file.delete()) totalBytes -= size
            if (totalBytes + incoming <= capBytes) return
        }
    }

    private fun drain() {
        val open = output ?: return
        if (buffer.size() == 0) return
        buffer.writeTo(open)
        buffer.reset()
    }

    private fun close() {
        val open = output ?: return
        drain()
        output = null
        open.close()
    }

    /** Forgets what hasn't been written. The next line starts a new file. */
    private fun abandon() {
        buffer.reset()
        try {
            output?.close()
        } catch (_: IOException) {
            // Nothing is left to save.
        }
        output = null
    }

    private fun encode(line: String) = (line + "\n").toByteArray(Charsets.UTF_8)

    private fun number(file: File) = file.name.removePrefix("rawlog-").removeSuffix(".jsonl").toLong()

    private companion object {
        const val CAP_BYTES = 200L * 1024 * 1024
        const val FILE_BYTES = 8L * 1024 * 1024
        const val BUFFER_BYTES = 64 * 1024
        val NAME = Regex("rawlog-\\d{12}\\.jsonl")
    }
}
