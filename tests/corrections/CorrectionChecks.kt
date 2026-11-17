package com.titankeys.keyboard

import com.titankeys.autocorrect.RecentWords
import java.io.File
import kotlin.random.Random

/**
 * Corrections in every language with a word list (owner, 2026-11-17: "ok to be bad but conservative"), measured on each list:
 * its commonest words with made-up slips (a neighbouring key, a letter left out, two letters swapped, an accent left off), and
 * the words themselves typed right. Space must almost never make a wrong change; the right word should often be on the bar.
 *
 * A language whose Space would make more than [LanguageCorrections.MOST_WRONG] wrong changes gets its fixes on the bar only: Space
 * never puts them in. This measures each language as if Space did, and the list it makes must match
 * android/assets/im/corrections-bar-only.txt; with --write, it writes that list.
 *
 * Usage: java CorrectionChecksKt android/assets/im [--write]
 */
private const val WORDS = 300

private var failures = 0

private fun check(name: String, ok: Boolean, detail: () -> String = { "" }) {
    if (ok) return
    failures++
    System.err.println("FAIL $name ${detail()}")
}

/** One made-up slip of [word], and what kind it is, or null where that kind can't be made. */
private class Slip(val typed: String, val kind: String)

/** The letter the key [printed] types plainly in [method]: its own letter where the layout gives none. */
private fun plain(method: InputMethod, printed: Char) = method.keys?.get(printed)?.base?.singleOrNull() ?: printed

private fun slips(word: String, method: InputMethod, random: Random): List<Slip> {
    val keys = LanguageCorrections.keys(method)
    val out = ArrayList<Slip>()
    // A neighbouring key, somewhere after the first letter.
    if (word.length >= 3) {
        val at = 1 + random.nextInt(word.length - 1)
        val printed = keys.key(word[at])
        val neighbours = ('a'..'z').filter { it != printed && com.titankeys.autocorrect.KeyNeighbours.near(it, printed) }
        if (printed in 'a'..'z' && neighbours.isNotEmpty())
            out += Slip(word.substring(0, at) + plain(method, neighbours[random.nextInt(neighbours.size)]) + word.substring(at + 1), "neighbour")
    }
    // A letter left out, not the first.
    if (word.length >= 4) {
        val at = 1 + random.nextInt(word.length - 1)
        out += Slip(word.removeRange(at, at + 1), "missed")
    }
    // Two letters swapped.
    if (word.length >= 4) {
        val at = 1 + random.nextInt(word.length - 2)
        out += Slip(word.substring(0, at) + word[at + 1] + word[at] + word.substring(at + 2), "swapped")
    }
    // The accents left off: "cafe" for café.
    val plainWord = word.map(LanguageCorrections::base).joinToString("")
    if (plainWord != word && plainWord.all { it in 'a'..'z' }) out += Slip(plainWord, "accent")
    return out
}

private class Tally {
    var slips = 0
    var spaceRight = 0
    var spaceWrong = 0
    var barRight = 0
    var accents = 0
    var accentsRight = 0
    var right = 0
    var rightChanged = 0
}

fun main(args: Array<String>) {
    val folder = File(args.firstOrNull { !it.startsWith("--") } ?: "android/assets/im")
    val write = "--write" in args
    println("language  slips  measured wrong  Space         Space right  Space wrong  on the bar  accents fixed  right words changed")
    val total = Tally()
    val barOnly = sortedSetOf<String>()
    for (dir in folder.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }) {
        val method = runCatching { InputMethod.parse(File(dir, "im.json").readText()) }.getOrNull() ?: continue
        if (!LanguageCorrections.corrects(method)) continue
        val list = File(dir, method.words!!).useLines { WordList.read(it, method.javaLocale) }
        val corrector = LanguageCorrections.corrector(method, list, { false }, HashSet(), RecentWords())
        val common = ArrayList<Pair<String, Int>>()
        list.forEachWord { word, frequency -> if (word.length in 4..10 && word.all { it.isLetter() }) common += word to frequency }
        val sample = common.sortedByDescending { it.second }.map { it.first }.distinct().take(WORDS)
        val random = Random(method.id.hashCode())
        val tally = Tally()
        for (word in sample) {
            // Typed right: Space keeps it.
            tally.right++
            if (corrector.check(word, "", null) != null) tally.rightChanged++
            for (slip in slips(word, method, random)) {
                // A slip that is itself a word is meant as typed, as far as anyone can tell.
                if (list.has(slip.typed)) continue
                tally.slips++
                if (slip.kind == "accent") tally.accents++
                val space = corrector.check(slip.typed, "", null)?.word?.lowercase()
                when {
                    space == word -> {
                        tally.spaceRight++
                        if (slip.kind == "accent") tally.accentsRight++
                    }
                    space != null -> {
                        tally.spaceWrong++
                        if (System.getenv("CORRECTIONS_WRONG") == method.id) println("  wrong ${slip.kind}: ${slip.typed} meant $word, Space gave $space")
                    }
                }
                val bar = listOfNotNull(space, corrector.suggestion(slip.typed, "")?.lowercase()) +
                    list.complete(slip.typed, 3).map { it.lowercase() }
                if (word in bar) tally.barRight++
            }
        }
        fun pct(part: Int, whole: Int) = if (whole == 0) "   -  " else "%5.1f%%".format(100.0 * part / whole)
        // Too many wrong changes: the bar shows the fixes, and Space types what was typed.
        val bar = tally.spaceWrong > LanguageCorrections.MOST_WRONG * tally.slips
        val measuredWrong = tally.spaceWrong
        if (bar) {
            barOnly += method.id
            val barCorrector = LanguageCorrections.corrector(method, list, { false }, HashSet(), RecentWords(), spaceFixes = false)
            check("${method.id}: on the bar only, Space never puts a fix in", sample.none { word ->
                slips(word, method, Random(method.id.hashCode())).any { barCorrector.check(it.typed, "", null) != null } })
            tally.spaceRight = 0
            tally.spaceWrong = 0
            tally.accentsRight = 0
        }
        println("%-8s %6d  %14s  %-12s  %11s  %11s  %10s  %13s  %19s".format(method.id, tally.slips, pct(measuredWrong, tally.slips),
            if (bar) "bar only" else "puts fixes in", pct(tally.spaceRight, tally.slips), pct(tally.spaceWrong, tally.slips),
            pct(tally.barRight, tally.slips), pct(tally.accentsRight, tally.accents), pct(tally.rightChanged, tally.right)))
        check("${method.id}: a word typed right is never changed", tally.rightChanged == 0) { "${tally.rightChanged} of ${tally.right}" }
        total.slips += tally.slips; total.spaceRight += tally.spaceRight; total.spaceWrong += tally.spaceWrong
        total.barRight += tally.barRight; total.accents += tally.accents; total.accentsRight += tally.accentsRight
        total.right += tally.right; total.rightChanged += tally.rightChanged
    }
    println("all      %6d  %14s  %-12s  %10.1f%%  %10.1f%%  %9.1f%%  %12.1f%%  %18.1f%%".format(total.slips, "", "",
        100.0 * total.spaceRight / total.slips,
        100.0 * total.spaceWrong / total.slips, 100.0 * total.barRight / total.slips,
        100.0 * total.accentsRight / maxOf(1, total.accents), 100.0 * total.rightChanged / maxOf(1, total.right)))
    check("English isn't one of them: it keeps its own corrector", !LanguageCorrections.corrects(InputMethod.ENGLISH))
    engineSlips(folder)
    check("Space's wrong changes stay near zero where it puts fixes in", total.spaceWrong <= LanguageCorrections.MOST_WRONG * total.slips)
    val file = File(folder, LanguageCorrections.BAR_ONLY_FILE)
    if (write) {
        file.writeText("# Input methods whose corrections show on the bar only: Space never puts them in. Written by\n" +
            "# tests/corrections/run.sh --write: each made more than 0.5% wrong changes on its slips there.\n" +
            barOnly.joinToString("") { "$it\n" })
        println("wrote ${file.path}: ${barOnly.joinToString(" ")}")
    } else {
        val listed = if (file.exists()) file.useLines { LanguageCorrections.barOnly(it) } else emptySet<String>()
        check("${LanguageCorrections.BAR_ONLY_FILE} is the measured list (run.sh --write)", listed == barOnly) { "has $listed, measured $barOnly" }
    }
    if (failures > 0) {
        System.err.println("$failures failed")
        kotlin.system.exitProcess(1)
    }
    println("Correction checks passed")
}

/** Telex keys for a Vietnamese [word]: â aa, ă aw, ơ ow, ư uw, đ dd, and its tone's key last. Null where a letter has none. */
private fun telexKeys(word: String): String? {
    val tones = mapOf('́' to 's', '̀' to 'f', '̉' to 'r', '̃' to 'x', '̣' to 'j')
    val marks = mapOf('̂' to null, '̆' to 'w', '̛' to 'w')
    val out = StringBuilder()
    var tone: Char? = null
    for (c in java.text.Normalizer.normalize(word, java.text.Normalizer.Form.NFD)) {
        when {
            c in tones -> tone = tones[c]
            c == '̂' -> out.append(out.last())
            c in marks -> out.append(marks[c])
            c == 'đ' -> out.append("dd")
            c in 'a'..'z' -> out.append(c)
            else -> return null
        }
    }
    tone?.let { out.append(it) }
    return out.toString()
}

private val RULE_KEYS = ('a'..'z') + listOf('~', '.', '^', '_')

/** Keys that type [word] through [rules] by sound, as tests/languages finds them: a search over the keys. */
private fun ruleKeys(rules: Rules, word: String): String? {
    var beam = listOf("" to Rules.State("", ""))
    repeat(word.length * 3 + 2) {
        val next = ArrayList<Triple<String, Rules.State, Int>>()
        for ((keys, state) in beam) for (k in RULE_KEYS) {
            val after = rules.step(state, k)
            if (rules.step(after, ' ').text.removeSuffix(" ") == word) return keys + k
            val common = after.text.commonPrefixWith(word).length
            if (after.text.length - common > rules.maxKeyLength) continue
            next += Triple(keys + k, after, common * 10 - keys.length)
        }
        beam = next.sortedByDescending { it.third }.distinctBy { it.second }.take(24).map { it.first to it.second }
        if (beam.isEmpty()) return null
    }
    return null
}

/** One slip in a word's [keys]: a neighbouring key, a key left out, two swapped. */
private fun keySlips(keys: String, random: Random): List<String> {
    val out = ArrayList<String>()
    if (keys.length >= 3) {
        val at = 1 + random.nextInt(keys.length - 1)
        val near = ('a'..'z').filter { it != keys[at] && com.titankeys.autocorrect.KeyNeighbours.near(it, keys[at]) }
        if (near.isNotEmpty()) out += keys.substring(0, at) + near[random.nextInt(near.size)] + keys.substring(at + 1)
    }
    if (keys.length >= 4) {
        val at = 1 + random.nextInt(keys.length - 1)
        out += keys.removeRange(at, at + 1)
        val swap = 1 + random.nextInt(keys.length - 2)
        out += keys.substring(0, swap) + keys[swap + 1] + keys[swap] + keys.substring(swap + 2)
    }
    return out
}

/**
 * Hindi and Vietnamese, whose engines build the words: the commonest words' keys with one slip, typed into the engine. Before,
 * only the list's words starting like the word were offered; now one slip undone is offered too, on the bar only. Space always
 * writes what the keys spell.
 */
private fun engineSlips(folder: File) {
    println("\nengines   slips  on the bar before  on the bar now  Space changes  ms a key  slowest key")
    for (id in listOf("hi", "vi")) {
        val dir = File(folder, id)
        val method = InputMethod.parse(File(dir, "im.json").readText())
        val list = File(dir, method.words!!).useLines { WordList.read(it, method.javaLocale) }
        val rules = if (id == "hi") Rules.parse(File(dir, method.engineArg ?: "rules.json").readText()) else null
        fun engine(): InputEngine = if (rules != null) RulesComposer(rules, list) else TelexComposer(list)
        fun spoken(keys: String) = if (rules != null) rules.spell("$keys ").removeSuffix(" ") else TelexComposer.spell(keys)
        val common = ArrayList<Pair<String, Int>>()
        list.forEachWord { word, frequency -> if (word.length in 3..10) common += word to frequency }
        val random = Random(id.hashCode())
        var slips = 0
        var before = 0
        var now = 0
        var spaceChanged = 0
        var keyNanos = 0L
        var keysTyped = 0
        var slowest = 0L
        for (word in common.sortedByDescending { it.second }.map { it.first }.distinct().take(WORDS)) {
            val keys = (if (rules != null) ruleKeys(rules, word) else telexKeys(word))?.takeIf { spoken(it) == word } ?: continue
            for (slip in keySlips(keys, random)) {
                val typed = spoken(slip)
                // A slip that spells a word, or the start of one, is no slip the bar can tell.
                if (typed == word || list.has(typed)) continue
                slips++
                val engine = engine()
                var state = EngineState()
                for (k in slip) {
                    val start = System.nanoTime()
                    state = engine.key(k)
                    val took = System.nanoTime() - start
                    keyNanos += took
                    keysTyped++
                    slowest = maxOf(slowest, took)
                }
                if (list.complete(typed, 8).any { it.equals(word, ignoreCase = true) }) before++
                if (state.choices.any { it.equals(word, ignoreCase = true) }) now++
                // Space writes the keys' word, never a choice.
                val written = engine.space().commit.trimEnd()
                if (written != typed) spaceChanged++
            }
        }
        println("%-8s %6d  %16.1f%%  %13.1f%%  %13d  %8.2f  %8.1f ms".format(id, slips, 100.0 * before / maxOf(1, slips),
            100.0 * now / maxOf(1, slips), spaceChanged, keyNanos / 1e6 / maxOf(1, keysTyped), slowest / 1e6))
        check("$id: Space never puts a slip's choice in", spaceChanged == 0) { "$spaceChanged" }
        check("$id: the bar offers more right words than before", now >= before)
    }
}
