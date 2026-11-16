package com.titankeys.keyboard

import com.titankeys.engine.Swipe
import java.io.File
import kotlin.system.exitProcess

/**
 * Checks for joining a swipe to the touch after it (#83), on the desktop: no Android and no phone.
 * The fixture holds the tails of real swipe pairs from the saved logs, with how each was labelled in the study.
 */
private var failures = 0

private fun check(name: String, ok: Boolean) {
    if (!ok) {
        failures++
        println("FAIL $name")
    }
}

/** A straight swipe in letter space, sampled every 10 ms. [still] keeps the first points in place. */
private fun stroke(x0: Float, y0: Float, x1: Float, y1: Float, millis: Int, still: Int = 0): Swipe {
    val points = millis / 10 + 1
    val x = FloatArray(points + still)
    val y = FloatArray(points + still)
    val t = FloatArray(points + still)
    for (i in 0 until still) {
        x[i] = x0; y[i] = y0; t[i] = i * 10f
    }
    for (i in 0 until points) {
        val f = i.toFloat() / (points - 1)
        x[still + i] = x0 + (x1 - x0) * f
        y[still + i] = y0 + (y1 - y0) * f
        t[still + i] = (still + i) * 10f
    }
    return Swipe(x, y, t)
}

private const val FLICKER = 90L

private fun syntheticChecks() {
    // A swipe across the middle row, then the same finger back 100 ms later, 0.2 keys on, still travelling.
    val middle = stroke(.2f, .5f, .5f, .5f, 300)
    check("a dropout mid-pad joins", middle.sameStroke(stroke(.52f, .5f, .7f, .5f, 200), 100, FLICKER, Float.NaN))
    check("a dropout mid-pad could continue", middle.couldContinue(.52f, .5f, 100, FLICKER))
    check("a touch landing still mid-pad stays apart", !middle.sameStroke(stroke(.52f, .5f, .7f, .5f, 200, still = 6), 100, FLICKER, Float.NaN))
    check("a dropout needs under 150 ms", !middle.sameStroke(stroke(.52f, .5f, .7f, .5f, 200), 150, FLICKER, Float.NaN))
    check("a dropout needs 2 keys or less", !middle.sameStroke(stroke(.75f, .5f, .9f, .5f, 200), 100, FLICKER, Float.NaN))
    check("a dropout ignores the end size", middle.sameStroke(stroke(.52f, .5f, .7f, .5f, 200), 100, FLICKER, 3f))

    // A swipe that runs off the top edge and comes back 200 ms later, carrying on the same way.
    val upward = stroke(.4f, .6f, .45f, .2f, 300)
    val back = stroke(.46f, .17f, .5f, .5f, 200)
    check("a slip off the top joins when the size isn't known", upward.sameStroke(back, 200, FLICKER, Float.NaN))
    check("a slip off the top joins when the finger thinned out", upward.sameStroke(back, 200, FLICKER, 1.5f))
    check("a full-size end over 150 ms before stays apart", !upward.sameStroke(back, 200, FLICKER, 2f))
    check("a full-size end 150 ms before still joins", upward.sameStroke(back, 150, FLICKER, 2f))
    check("the three-argument call leaves the size out", upward.sameStroke(back, 200, FLICKER))

    // A flicker joins wherever it is, whatever the size.
    check("a flicker still joins", middle.sameStroke(stroke(.51f, .5f, .51f, .5f, 10), 50, FLICKER, 3f))
}

private fun endContactChecks() {
    fun sample(time: Long, step: Int) = Sample(.5f, .5f, time, step)
    check("end contact is NaN without sizes", endContact(listOf(sample(0, 0), sample(100, 0))).isNaN())
    check("end contact is NaN for no points", endContact(emptyList()).isNaN())
    val points = listOf(sample(0, 9), sample(50, 9), sample(100, 1), sample(130, 0), sample(160, 3))
    check("end contact averages the last 60 ms and skips unknown sizes", endContact(points) == 2f)
}

private fun fixtureChecks(path: String) {
    val outcome = HashMap<String, Int>()
    var lines = 0
    for (line in File(path).readLines()) {
        if (line.isBlank() || line.startsWith("#")) continue
        val f = line.trim().split(" ")
        val (label, logged) = f[0] to (f[1] == "joined")
        val oldSame = f[2] == "true"
        val expected = f[3] == "true"
        val gap = f[4].toLong()
        val endContact = f[5].toFloat()
        val na = f[6].toInt()
        val nb = f[7].toInt()
        fun swipe(at: Int, n: Int) = Swipe(FloatArray(n) { f[at + it * 3].toFloat() }, FloatArray(n) { f[at + it * 3 + 1].toFloat() },
            FloatArray(n) { f[at + it * 3 + 2].toFloat() })
        val a = swipe(8, na)
        val b = swipe(8 + na * 3, nb)
        val same = a.sameStroke(b, gap, FLICKER, endContact)
        lines++
        check("fixture line $lines gives the decision the full pair gave", same == expected)
        // As confirm.py: only a change of verdict changes what the log recorded.
        val joins = when {
            logged && oldSame && !same -> false
            !logged && same && !oldSame -> true
            else -> logged
        }
        if (joins != logged) outcome.merge(label + if (joins) " joined" else " cut", 1, Int::plus)
    }
    check("the fixture has its 614 pairs", lines == 614)
    println("fixture: $lines pairs, changes ${outcome.toSortedMap()}")
    // The study's counts (docs/studies/83-joins.md). Any change here needs the study run again.
    check("1 right join lost, which hadn't changed the word", outcome["right-join cut"] == 1)
    check("7 wrong joins cut", outcome["wrong-join cut"] == 7)
    check("2 unclear joins cut", outcome["unclear cut"] == 2)
    check("4 missed joins found", outcome["missed-join joined"] == 4)
    check("no pair the owner kept apart joined", outcome["right-non-join joined"] == null)
    check("5 other pairs joined", outcome["other joined"] == 5)
}

fun main(args: Array<String>) {
    syntheticChecks()
    endContactChecks()
    fixtureChecks(args.firstOrNull() ?: "tests/swipe/join-pairs.txt")
    if (failures > 0) {
        println("$failures join checks failed")
        exitProcess(1)
    }
    println("join checks passed")
}
