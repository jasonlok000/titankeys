package com.titankeys.connected

import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.zip.ZipInputStream

/**
 * The report form's upload (#97): a report packs into one ZIP, splits into parts that fit the Worker, each part seals as a
 * contribution batch does, and joining the opened parts gives the ZIP back. The owner's tool then opens them too.
 */
fun main(args: Array<String>) {
    val failures = ArrayList<String>()
    fun same(actual: Any?, expected: Any?, message: String) {
        if (actual != expected) failures += "$message: expected $expected, got $actual"
    }

    val json = Reports.json(linkedMapOf("kind" to "swiping", "what" to "It typed \"teh\"\nagain", "email" to "", "version" to "3.0.0"))
    same((Json.parse(json) as Map<*, *>)["what"], "It typed \"teh\"\nagain", "report.json holds the form's text as typed")
    same((Json.parse(Reports.json(mapOf("what" to "x".repeat(9000)))) as Map<*, *>)["what"].toString().length, Reports.MAX_TEXT,
        "A field is cut at MAX_TEXT")

    // A recording-sized attachment: varied enough not to shrink to nothing.
    val random = java.util.Random(7)
    val recording = (0 until 60_000).joinToString("\n") { """{"k":"m","t":${it * 8},"x":${random.nextInt(1076)},"y":${random.nextInt(400)}}""" }
        .toByteArray()
    val zip = Reports.pack(json, listOf("recording.jsonl" to recording, "crash.txt" to "Crashed: 2026-11-16".toByteArray()))
    val parts = Reports.split(zip)
    same(parts.size > 1, true, "A recording's report splits into parts")
    same(parts.all { it.size <= Reports.PART_BYTES }, true, "Every part fits PART_BYTES")
    same(parts.fold(ByteArray(0)) { all, part -> all + part }.contentEquals(zip), true, "The parts join back into the ZIP")

    val pair = KeyPairGenerator.getInstance("XDH").generateKeyPair()
    val sealed = parts.map { Seal.seal(it, pair.public.encoded) }
    // The Worker's MAX_BATCH_BYTES (server/contributions/src/worker.js).
    same(sealed.all { it.size <= 320 * 1024 }, true, "Every sealed part fits the Worker's limit")
    val opened = sealed.map { Seal.open(it, pair.private.encoded, pair.public.encoded) }.fold(ByteArray(0)) { all, part -> all + part }
    val entries = LinkedHashMap<String, ByteArray>()
    ZipInputStream(opened.inputStream()).use { input ->
        while (true) {
            val entry = input.nextEntry ?: break
            entries[entry.name] = input.readBytes()
        }
    }
    same(entries.keys.toList(), listOf("report.json", "recording.jsonl", "crash.txt"), "The opened report has every part")
    same(entries["recording.jsonl"]!!.contentEquals(recording), true, "The recording comes back whole")
    same(String(entries["report.json"]!!), json, "report.json comes back whole")

    val tooBig = ByteArray(Reports.PART_BYTES * Reports.MAX_PARTS + 1)
    same(try { Reports.split(tooBig); "split" } catch (_: Reports.TooLarge) { "too large" }, "too large", "A report past MAX_PARTS is too large")
    same(Reports.newId().matches(Regex("[0-9a-f]{32}")), true, "A report's ID is 32 hex digits, as the Worker wants")
    same(Reports.newId() != Reports.newId(), true, "Each report has its own ID")

    // The outbox keeps one report, for email.
    val folder = Files.createTempDirectory("outbox").toFile().also { it.deleteOnExit() }
    Reports.keep(folder, "titankeys-report.zip", zip)
    Reports.keep(folder, "titankeys-report.zip", zip)
    same(Reports.kept(folder)?.readBytes()?.contentEquals(zip), true, "The outbox keeps the report")
    Reports.forget(folder)
    same(Reports.kept(folder), null, "Forgetting empties the outbox")

    // Hand the sealed parts to the owner's tool, which joins and opens them.
    args.getOrNull(0)?.let { handover ->
        val dir = Files.createTempDirectory("reports").toFile()
        val id = Reports.newId()
        sealed.forEachIndexed { index, part -> File(dir, "$id-$index-of-${sealed.size}.tkr").writeBytes(part) }
        File(dir, "key.pem").writeText("-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(pair.private.encoded) +
            "\n-----END PRIVATE KEY-----\n")
        File(dir, "expected.zip").writeBytes(zip)
        File(handover).writeText("${dir.absolutePath}\n$id\n")
    }

    if (failures.isNotEmpty()) {
        failures.forEach(System.err::println)
        System.exit(1)
    }
    println("Report checks passed")
}
