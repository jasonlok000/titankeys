package com.titankeys.keyboard

/**
 * TitanKeys Debug only: the key feed's script, read and timed without Android, so the touch simulator's offline checks run it
 * (tools/dev/touch/test). "D ms NAME" presses a key, "U ms NAME" lets it go, ";" between them; NAME is a KeyEvent name without
 * KEYCODE_ (SHIFT_RIGHT, Q, SPACE, SYM, ALT_LEFT). Each event carries the meta state of the modifiers down, as the phone's
 * keyboard's do: a modifier's own press has its bit, its release doesn't. The last key pressed that isn't a modifier repeats
 * from the repeat timeout every repeat delay until it's let go or another key is pressed, as Android repeats a real one. Keys
 * left down at the end are let go there, so nothing stays held.
 */
object KeyFeedPlan {
    class Step(val millis: Long, val down: Boolean, val name: String, val repeat: Int, val meta: Int) {
        override fun toString() = "${if (down) "D" else "U"} $millis $name${if (repeat > 0) " r$repeat" else ""} m$meta"
    }

    /** The meta bits each modifier sets while it's down: KeyEvent's META_* values. */
    val META = mapOf(
        "SHIFT_LEFT" to (0x1 or 0x40), "SHIFT_RIGHT" to (0x1 or 0x80),
        "ALT_LEFT" to (0x2 or 0x10), "ALT_RIGHT" to (0x2 or 0x20),
        "SYM" to 0x4, "FUNCTION" to 0x8,
        "CTRL_LEFT" to (0x1000 or 0x2000), "CTRL_RIGHT" to (0x1000 or 0x4000),
    )
    const val MAX_MILLIS = 15_000L

    /** The steps of [script], or null for a bad one: a key pressed twice or let go while up, times going back, or too long. */
    fun read(script: String, repeatTimeout: Long, repeatDelay: Long): List<Step>? {
        val presses = try {
            script.split(';').filter { it.isNotBlank() }.map { part ->
                val bits = part.trim().split(' ').filter { it.isNotEmpty() }
                require(bits.size == 3 && (bits[0] == "D" || bits[0] == "U") && bits[2].matches(Regex("[A-Z0-9_]+")))
                Triple(bits[0] == "D", bits[1].toLong(), bits[2])
            }
        } catch (error: RuntimeException) {
            return null
        }
        if (presses.isEmpty() || presses.first().second < 0 || presses.zipWithNext().any { (a, b) -> b.second < a.second } ||
            presses.last().second > MAX_MILLIS) return null
        val steps = ArrayList<Step>()
        val held = LinkedHashSet<String>()
        fun meta() = held.sumOf { META[it] ?: 0 }
        var repeating: String? = null
        var next = 0L
        var count = 0
        fun repeatUntil(millis: Long) {
            val key = repeating ?: return
            while (next < millis) {
                steps += Step(next, true, key, ++count, meta())
                next += repeatDelay
            }
        }
        for ((down, millis, name) in presses) {
            repeatUntil(millis)
            if (down) {
                if (!held.add(name)) return null
                steps += Step(millis, true, name, 0, meta())
                if (name !in META) {
                    repeating = name
                    next = millis + repeatTimeout
                    count = 0
                }
            } else {
                if (!held.remove(name)) return null
                if (name == repeating) repeating = null
                steps += Step(millis, false, name, 0, meta())
            }
        }
        val end = presses.last().second
        for (name in held.toList().asReversed()) {
            held.remove(name)
            steps += Step(end, false, name, 0, meta())
        }
        return steps
    }
}
