package com.titankeys.autocorrect

import com.titankeys.engine.PhoneWords
import java.io.File
import java.nio.file.Path
import java.util.Base64

/**
 * Tunes how much the next-word model counts in typed autocorrect, on cases from tools/autocorrect_cases.py.
 * Usage (from a TitanKeys tree): AutocorrectEvalKt cases.tsv
 */
private class EvalLists(val words: PhoneWords) : Lexicon {
    override fun known(word: String) = words.contains(word)
    override fun frequency(word: String) = words.frequency(word)
    override fun personal(word: String) = false
    override fun name(word: String): String? = words.name(word)
    override fun preferredSpelling(word: String) = PhoneWords.spelled(word, false)
    override fun forEachWord(action: (String, Int) -> Unit) = words.forEachWord { word, frequency -> action(word, frequency) }
}

private class Case(val set: String, val before: String, val typed: String, val intended: String, val chances: Map<String, Double>)

fun main(args: Array<String>) {
    val words = PhoneWords(Path.of("android/assets/swipe/hungry_jellyfish/vocab.txt"), Path.of("android/assets/swipe/english.combined"))
    val lists = EvalLists(words)
    val data = CorrectionData.read(lists) { File("android/assets/autocorrect/$it").reader(Charsets.UTF_8) }
    val cases = File(args[0]).readLines().map { line ->
        val cells = line.split('\t')
        val chances = cells[4].split(',').filter { it.isNotEmpty() }.associate { it.substringBefore(':') to it.substringAfter(':').toDouble() }
        Case(cells[0], String(Base64.getDecoder().decode(cells[1]), Charsets.UTF_8), cells[2], cells[3], chances)
    }
    println("cases ${cases.size}")
    for (weight in listOf(0.0, 0.1, 0.2, 0.3, 0.5, 0.75, 1.0)) {
        val corrector = TypedCorrector(lists, data, HashSet(), RecentWords(), { _, word -> Double.NaN }, weight)
        for (set in listOf("hand", "prompted")) {
            var slips = 0; var fixed = 0; var wrong = 0; var clean = 0; var broken = 0
            for (case in cases.filter { it.set == set }) {
                val chance = { _: String, word: String -> case.chances[word] ?: Double.NaN }
                val scored = TypedCorrector(lists, data, HashSet(), RecentWords(), chance, weight)
                val fix = scored.check(case.typed, case.before, null)?.word?.lowercase()
                if (case.typed == case.intended) {
                    clean++
                    if (fix != null) broken++
                } else {
                    slips++
                    if (fix == case.intended) fixed++ else if (fix != null) wrong++
                }
            }
            println("weight $weight $set: slips $slips, fixed ${pct(fixed, slips)}, wrong fix ${pct(wrong, slips)}; " +
                    "typed right $clean, changed ${pct(broken, clean)}")
        }
        corrector.hashCode()
    }
}

private fun pct(a: Int, b: Int) = if (b == 0) "-" else "%.1f%%".format(100.0 * a / b)
