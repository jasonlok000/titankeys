package com.atlas.keyboard

import java.io.File
import kotlin.math.hypot

/**
 * #30 mistaps: replays logged touchpad motion and key presses through the keyboard's own [Contact], with the swipe tick
 * rule of AtlasIme.motion and Haptics.move, to find which contacts vibrated. The touchpad's other vibrations (accepted
 * tap keys, the cursor, a delete flick) are left out: tap keys were off, and the cursor's and the flick's are input.
 * Prints one line per contact: down, up, ticks, first tick time, outcome (swipe, delete, tap, none), whether it counted,
 * travel in keys, landing step, largest step, and whether the first tick came before the contact counted as a swipe.
 * Usage: HapticReplay events.txt out.tsv
 */
/** Where the first tick plays: 0.2 keys before #30, 0.6 (Contact.SWIPE_KEYS) since. Set with the third argument. */
private var FIRST_TICK_KEYS = .6f

fun main(args: Array<String>) {
    args.getOrNull(2)?.let { FIRST_TICK_KEYS = it.toFloat() }
    val contact = Contact()
    val out = File(args[1]).printWriter()
    out.println("down\tup\tticks\tfirstTick\toutcome\tcounts\ttravel\tlanding\tlargest\ttickBeforeSwipe\tx\ty")
    class Open(val down: Long, val first: Sample) {
        var ticks = 0
        var firstTick = -1L
        var lastTick = -1L
        var travel = 0f
        var last: Sample? = first
        var largest = first.step
        var landing = first.step
        var tickBeforeSwipe = false
        var landingCount = 1
    }
    class Waiting(val open: Open, val settlement: Contact.Settlement, val up: Long)
    var open: Open? = null
    val waiting = ArrayList<Waiting>()
    fun distance(a: Sample, b: Sample) = hypot((b.x - a.x) * 10, (b.y - a.y) * 4)

    fun settleDue(now: Long) {
        val iterator = waiting.iterator()
        while (iterator.hasNext()) {
            val w = iterator.next()
            if (now < w.settlement.deadline) continue
            iterator.remove()
            val outcome = contact.settle(w.settlement, w.settlement.deadline)
            val kind = when (outcome) {
                is Contact.Outcome.Swipe -> if (outcome.deletesWord) "delete" else "swipe"
                is Contact.Outcome.Tap -> "tap:" + outcome.key
                null -> "none"
            }
            val o = w.open
            out.println(listOf(o.down, w.up, o.ticks, o.firstTick, kind, w.settlement.counts, "%.2f".format(o.travel), o.landing, o.largest,
                o.tickBeforeSwipe, "%.3f".format(o.first.x), "%.3f".format(o.first.y)).joinToString("\t"))
        }
    }

    /** Haptics.move, for the first tick and the ones after it with the Normal preset. */
    fun ticks(o: Open, samples: List<Sample>) {
        var ticked = false
        var travel = 0f
        for (sample in samples) {
            o.last?.let { travel += distance(it, sample) }
            o.last = sample
            if (!Layout.isLetter(Layout.keyAt(sample.x, sample.y))) {
                travel = 0f
                continue
            }
            val ready = if (o.lastTick < 0) distance(o.first, sample) >= FIRST_TICK_KEYS
                else travel >= HapticPreset.Normal.spacing && sample.time - o.lastTick >= HapticPreset.Normal.intervalMillis
            if (!ready || ticked) continue
            o.lastTick = sample.time
            travel = 0f
            ticked = true
            if (o.ticks == 0) {
                o.firstTick = sample.time
                o.tickBeforeSwipe = (contact.travel ?: 0f) < Contact.SWIPE_KEYS
            }
            o.ticks++
        }
    }

    File(args[0]).forEachLine { line ->
        val f = line.split(' ')
        when (f[0]) {
            "S" -> {
                settleDue(f[1].toLong())
                contact.releaseKeys()
            }
            "K" -> {
                val time = f[3].toLong()
                settleDue(time)
                contact.key(f[1].toInt(), f[2] == "1", time)
            }
            "M" -> {
                val action = f[1].toInt()
                val time = f[2].toLong()
                settleDue(time)
                val n = f[3].toInt()
                val samples = (0 until n).map { i ->
                    val x = f[4 + 4 * i].toFloat()
                    val y = f[5 + 4 * i].toFloat()
                    Sample(x, y, f[6 + 4 * i].toLong(), f[7 + 4 * i].toInt())
                }
                for (s in samples) open?.let { o ->
                    if (s.step > o.largest) o.largest = s.step
                    if (s.time - o.down <= 80 && s.step > o.landing) o.landing = s.step
                }
                when (action) {
                    0 -> {
                        val first = samples.firstOrNull() ?: Sample(Float.NaN, Float.NaN, time)
                        val o = Open(time, first)
                        open = o
                        contact.down(first)
                        contact.move(samples.drop(1))
                        o.travel = contact.travel ?: o.travel
                    }
                    2 -> open?.let { o ->
                        contact.move(samples)
                        o.travel = maxOf(o.travel, samples.maxOfOrNull { if (it.valid) distance(o.first, it) else 0f } ?: 0f)
                        if (contact.travel != null) ticks(o, samples)
                    }
                    1 -> open?.let { o ->
                        o.travel = maxOf(o.travel, samples.maxOfOrNull { if (it.valid) distance(o.first, it) else 0f } ?: 0f)
                        val settlement = contact.up(samples, time)
                        if (settlement != null) waiting += Waiting(o, settlement, time)
                        else out.println(listOf(o.down, time, o.ticks, o.firstTick, "ignored", false, "%.2f".format(o.travel), o.landing, o.largest,
                            o.tickBeforeSwipe, "%.3f".format(o.first.x), "%.3f".format(o.first.y)).joinToString("\t"))
                        open = null
                    }
                    else -> open?.let { o ->
                        contact.cancelActive()
                        out.println(listOf(o.down, time, o.ticks, o.firstTick, "cancelled", false, "%.2f".format(o.travel), o.landing, o.largest,
                            o.tickBeforeSwipe, "%.3f".format(o.first.x), "%.3f".format(o.first.y)).joinToString("\t"))
                        open = null
                    }
                }
            }
        }
    }
    settleDue(Long.MAX_VALUE)
    out.close()
}
