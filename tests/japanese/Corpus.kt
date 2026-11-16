package com.titankeys.mozc

import java.io.File
import java.text.Normalizer
import kotlin.random.Random

/**
 * The Japanese corpus test (docs/languages.md, "Japanese"): public sentences with known readings, typed as romaji through
 * TitanKeys' own Mozc path, and the first choice compared with the sentence as written, and with Mozc's own desktop
 * conversion of the same reading.
 *
 * The sentences are Tatoeba's (CC BY 2.0 FR), with the furigana transcriptions its contributors reviewed ([漢字|かん|じ]).
 * Each reading is turned into romaji ([KanaRomaji]) and typed as the app types it: a punctuation mark ends the text being
 * built, so each part between marks is typed on its own and its first choice taken, the mark then added. Mozc's desktop
 * conversion gets the whole reading at once, marks included, and Space.
 *
 *   Corpus <transcriptions.csv> <mozc_tool> <mozc.data> <work folder> [sentences] [seed]
 *
 * Only sentences in kana and kanji, with the usual marks, of 4 to 40 characters; a sample of them, the same each run.
 */
fun main(args: Array<String>) {
    val transcriptions = File(args[0])
    val tool = args[1]
    val data = args[2]
    val work = File(args[3]).apply { mkdirs() }
    val wanted = args.getOrNull(4)?.toInt() ?: 2000
    val seed = args.getOrNull(5)?.toInt() ?: 56

    // id, lang, script, user, transcription: reviewed Japanese ones only (a user's name on it).
    val all = transcriptions.readLines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 5 || parts[1] != "jpn" || parts[3].isEmpty() || parts[3] == "\\N") return@mapNotNull null
        val text = written(parts[4])
        val reading = reading(parts[4])
        if (text.length !in 4..40 || !text.all(::allowed)) return@mapNotNull null
        val romaji = KanaRomaji.of(reading) ?: return@mapNotNull null
        Triple(text, reading, romaji)
    }
    val sample = all.shuffled(Random(seed)).take(wanted)
    println("Tatoeba: ${all.size} reviewed sentences usable, ${sample.size} sampled (seed $seed)")

    // The app's way: each part between marks typed on its own, its first choice taken, then the mark.
    val parts = sample.flatMap { split(it.third).filter { p -> p.isNotEmpty() && p !in MARKS } }.distinct()
    val mobile = run(tool, data, work, "mobile", parts, emptyList())
        .associate { it[0] to (it.getOrNull(2) ?: it.getOrNull(1) ?: "") }
    // Mozc's desktop way: the whole reading, Space.
    val desktop = run(tool, data, work, "desktop", sample.map { it.third }.distinct(), listOf("--desktop"))
        .associate { it[0] to (it.getOrNull(2) ?: "") }

    var appExact = 0; var desktopExact = 0; var same = 0
    var appChars = 0.0; var desktopChars = 0.0
    val misses = StringBuilder()
    for ((text, reading, romaji) in sample) {
        val app = split(romaji).joinToString("") { if (it in MARKS) JapaneseMarks.of(it[0]) else mobile[it].orEmpty() }
        val desk = desktop[romaji].orEmpty()
        val want = norm(text)
        if (norm(app) == want) appExact++
        if (norm(desk) == want) desktopExact++
        if (norm(app) == norm(desk)) same++
        appChars += 1.0 - distance(norm(app), want).toDouble() / want.length
        desktopChars += 1.0 - distance(norm(desk), want).toDouble() / want.length
        if (norm(app) != want) misses.append("$text\t$reading\t$romaji\tapp: $app\tdesktop: $desk\n")
    }
    val n = sample.size.toDouble()
    val report = """
        |sentences: ${sample.size}
        |TitanKeys (Android's way, first choice per part): ${"%.1f".format(100 * appExact / n)}% exactly as written, ${"%.1f".format(100 * appChars / n)}% of characters
        |Mozc desktop (whole reading, Space): ${"%.1f".format(100 * desktopExact / n)}% exactly as written, ${"%.1f".format(100 * desktopChars / n)}% of characters
        |TitanKeys the same as Mozc desktop: ${"%.1f".format(100 * same / n)}%
        """.trimMargin()
    println(report)
    File(work, "report.txt").writeText(report + "\n")
    File(work, "misses.tsv").writeText(misses.toString())
}

/** Marks that end the text being built, as the app types them. */
private val MARKS = setOf(",", ".", "?", "!", "[", "]", "/", "~")

/** Romaji cut at its marks, each mark kept as a part of its own. Mozc's ー is "-", which stays in its word. */
private fun split(romaji: String): List<String> {
    val out = ArrayList<String>()
    val part = StringBuilder()
    for (c in romaji) {
        if (c.toString() in MARKS) {
            out += part.toString(); part.clear(); out += c.toString()
        } else part.append(c)
    }
    out += part.toString()
    return out
}

/** The sentence as written, from a transcription: [漢字|かん|じ] → 漢字. */
private fun written(t: String) = Regex("""\[([^|\]]*)(\|[^\]]*)?]""").replace(t) { it.groupValues[1] }

/** Its reading: [漢字|かん|じ] → かんじ. */
private fun reading(t: String) = Regex("""\[([^|\]]*)((\|[^|\]]*)*)]""").replace(t) { m ->
    val readings = m.groupValues[2].split('|').filter { it.isNotEmpty() }
    if (readings.isEmpty()) m.groupValues[1] else readings.joinToString("")
}

private fun allowed(c: Char) = c in 'ぁ'..'ゖ' || c in 'ァ'..'ヺ' || c == 'ー' || c in '一'..'鿿' ||
    c == '々' || c in "、。？！「」・〜"

private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC)

/** Levenshtein distance, by code point. */
private fun distance(a: String, b: String): Int {
    val x = a.codePoints().toArray(); val y = b.codePoints().toArray()
    var prev = IntArray(y.size + 1) { it }
    for (i in 1..x.size) {
        val cur = IntArray(y.size + 1); cur[0] = i
        for (j in 1..y.size) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1)
        prev = cur
    }
    return prev[y.size]
}

/** Runs mozc_tool on [lines], one each, with a fresh profile; returns each output line's fields. */
private fun run(tool: String, data: String, work: File, name: String, lines: List<String>, extra: List<String>): List<List<String>> {
    val profile = File(work, "profile-$name").apply { deleteRecursively(); mkdirs() }
    val input = File(work, "$name.in").apply { writeText(lines.joinToString("\n", postfix = "\n")) }
    val output = File(work, "$name.out")
    val process = ProcessBuilder(listOf(tool, data, profile.path) + extra + listOf("--choices", "1"))
        .redirectInput(input).redirectOutput(output).redirectError(File(work, "$name.err")).start()
    check(process.waitFor() == 0) { "mozc_tool failed: ${File(work, "$name.err").readText()}" }
    return output.readLines().map { it.split('\t') }
}
