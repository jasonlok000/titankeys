package com.titankeys.connected

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Report a bug and Suggest an idea (#97): the form's report goes to the contributions Worker (server/contributions,
 * POST /v1/report), only when the user presses Send. It's one ZIP, holding report.json and any recording or crash report,
 * split into parts that fit the Worker's limit, and each part is sealed on the phone exactly as a contribution batch is
 * ([Seal], the same public key), so only the owner's private key opens it. tools/contributions/reports.ts joins and opens
 * them. If it can't go, the ZIP waits in the outbox and the form offers email instead (#74).
 *
 * This needs nothing from Android, so tests/connected checks it on the JVM. [ReportUpload] sends it.
 */
object Reports {
    /** Below the Worker's 320 KB a part, sealing included. */
    const val PART_BYTES = 240 * 1024
    /** As the Worker's MAX_REPORT_PARTS: about 5.6 MB zipped, enough for a recording. */
    const val MAX_PARTS = 24
    const val MAX_TEXT = 4000

    /** The form's kinds, in its order. The ids go in report.json. */
    val KINDS = listOf("word", "swiping", "touchpad", "keyboard", "crash", "guide", "settings", "idea", "other")

    private const val OUTBOX = "report-outbox"

    class TooLarge : IOException("The report is too large to send")

    /** A new random ID for a report, which only links its parts. */
    fun newId(random: SecureRandom = SecureRandom()): String =
        ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    /** report.json: the form's fields, then the versions, as plain JSON. */
    fun json(fields: Map<String, String>): String =
        fields.entries.joinToString(",\n  ", "{\n  ", "\n}\n") { (key, value) -> "${Json.quote(key)}: ${Json.quote(value.take(MAX_TEXT))}" }

    /** The report as one ZIP: report.json first, then each attachment under its name. */
    fun pack(json: String, attachments: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("report.json"))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((name, bytes) in attachments) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** The ZIP in parts of at most [PART_BYTES], or [TooLarge] past [MAX_PARTS]. */
    fun split(zip: ByteArray): List<ByteArray> {
        val parts = (zip.indices step PART_BYTES).map { zip.copyOfRange(it, minOf(zip.size, it + PART_BYTES)) }
        if (parts.size > MAX_PARTS) throw TooLarge()
        return parts
    }

    /** Keeps the one report that couldn't go, replacing any older one, so the form can offer it by email. */
    fun keep(folder: File, name: String, zip: ByteArray): File {
        val outbox = File(folder, OUTBOX).apply { deleteRecursively(); mkdirs() }
        return File(outbox, name).apply { writeBytes(zip) }
    }

    /** The report waiting in the outbox, if any. */
    fun kept(folder: File): File? = File(folder, OUTBOX).listFiles()?.firstOrNull { it.isFile }

    fun forget(folder: File) {
        File(folder, OUTBOX).deleteRecursively()
    }
}
