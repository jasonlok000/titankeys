package com.titankeys.keyboard

import com.titankeys.autocorrect.RecentWords
import java.io.File
import kotlin.random.Random

/**
 * Corrections in every language with a word list (owner, 2026-11-17: "ok to be bad but conservative"), measured on each list:
 * its commonest words with made-up slips (a neighbouring key, a letter left out, two letters swapped, an accent left off), and
 * the words themselves typed right. Space must almost never make a wrong change; the right word should often be on the bar.
 *
 * Usage: java CorrectionChecksKt android/assets/im
 */
private const val WORDS = 300
private const val MOST_WRONG = 0.02

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
    val folder = File(args.firstOrNull() ?: "android/assets/im")
    println("language  slips  Space right  Space wrong  on the bar  accents fixed  right words changed")
    val total = Tally()
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
        println("%-8s %6d  %11s  %11s  %10s  %13s  %19s".format(method.id, tally.slips, pct(tally.spaceRight, tally.slips),
            pct(tally.spaceWrong, tally.slips), pct(tally.barRight, tally.slips), pct(tally.accentsRight, tally.accents),
            pct(tally.rightChanged, tally.right)))
        check("${method.id}: Space's wrong changes stay rare", tally.spaceWrong <= MOST_WRONG * tally.slips) { "${tally.spaceWrong} of ${tally.slips}" }
        check("${method.id}: a word typed right is never changed", tally.rightChanged == 0) { "${tally.rightChanged} of ${tally.right}" }
        total.slips += tally.slips; total.spaceRight += tally.spaceRight; total.spaceWrong += tally.spaceWrong
        total.barRight += tally.barRight; total.accents += tally.accents; total.accentsRight += tally.accentsRight
        total.right += tally.right; total.rightChanged += tally.rightChanged
    }
    println("all      %6d  %10.1f%%  %10.1f%%  %9.1f%%  %12.1f%%  %18.1f%%".format(total.slips, 100.0 * total.spaceRight / total.slips,
        100.0 * total.spaceWrong / total.slips, 100.0 * total.barRight / total.slips,
        100.0 * total.accentsRight / maxOf(1, total.accents), 100.0 * total.rightChanged / maxOf(1, total.right)))
    check("English isn't one of them: it keeps its own corrector", !LanguageCorrections.corrects(InputMethod.ENGLISH))
    if (failures > 0) {
        System.err.println("$failures failed")
        kotlin.system.exitProcess(1)
    }
    println("Correction checks passed")
}
