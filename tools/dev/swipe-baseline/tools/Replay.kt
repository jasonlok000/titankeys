package com.atlas.keyboard

import java.io.File

private fun sum(points: List<Sample>) = points.fold(17) { h, p -> 31 * (31 * (31 * h + p.x.toRawBits()) + p.y.toRawBits()) + p.time.toInt() }

/** Feeds a converted event file through the real Contact and SwipePieces, the way AtlasIme.motion does. */
fun main(args: Array<String>) {
    val out = File(args[1]).bufferedWriter()
    val tapKeys = args[2].replace('_', ' ')
    val isTap = { key: String -> if (key == Layout.SPACE) ' ' in tapKeys else Layout.isLetter(key) && key[0] in tapKeys }
    var contact = Contact()
    var slip = 90L
    var continuable = false
    val pieces = SwipePieces({ slip }, { slip > 0 && continuable }, isTap)
    val queue = ArrayDeque<Contact.Settlement>()
    var prompt = -1
    fun flush(now: Long) {
        while (queue.isNotEmpty() && queue.first().deadline <= now) {
            val s = queue.removeFirst()
            val settled = contact.settle(s, s.deadline)
            val cancelled = s.cancelled
            val result = if (cancelled) settled else pieces.settle(s, settled)
            when (result) {
                is Contact.Outcome.Swipe -> continuable = true
                is Contact.Outcome.Tap -> if (isTap(result.key)) continuable = false
                null -> {}
            }
            fun d(o: Contact.Outcome?) = when (o) {
                is Contact.Outcome.Swipe -> "S ${o.points.size} ${sum(o.points)} ${if (o.deletesWord) 1 else 0}"
                is Contact.Outcome.Tap -> "T ${o.key.replace(' ', '_')} 0 0"
                null -> "N - 0 0"
            }
            out.write("$prompt ${s.startedAt} ${s.endedAt} ${if (s.counts) 1 else 0} ${if (cancelled) 1 else 0} ${s.outcome?.let { d(it) } ?: "N - 0 0"} ${d(result)}\n")
        }
    }
    File(args[0]).forEachLine { line ->
        val f = line.split(' ')
        when (f[0]) {
            "H" -> slip = f[1].toLong()
            "P" -> prompt = f[1].toInt()
            "X" -> {
                flush(Long.MAX_VALUE)
                contact.cancel()
                pieces.clear()
                continuable = false
            }
            "R", "E" -> {
                flush(Long.MAX_VALUE)
                contact.releaseKeys()
                pieces.clear()
                continuable = false
            }
            "K" -> {
                val code = f[1].toInt()
                val down = f[2] == "1"
                val time = f[3].toLong()
                flush(time)
                if (f[4] == "0" && code != android.view.KeyEvent.KEYCODE_BACK) {
                    contact.key(code, down, time)
                    if (down) continuable = false
                }
            }
            "M" -> {
                val action = f[1].toInt()
                val time = f[2].toLong()
                val n = f[3].toInt()
                val samples = List(n) { i -> Sample(f[4 + 3 * i].toFloat(), f[5 + 3 * i].toFloat(), f[6 + 3 * i].toLong()) }
                flush(time)
                when (action) {
                    0 -> { contact.down(samples.firstOrNull() ?: Sample(Float.NaN, Float.NaN, time)); contact.move(samples.drop(1)) }
                    2 -> contact.move(samples)
                    1 -> contact.up(samples, time)?.let { queue.addLast(it) }
                    3, 5 -> contact.cancelActive()
                }
            }
        }
    }
    flush(Long.MAX_VALUE)
    out.close()
}
