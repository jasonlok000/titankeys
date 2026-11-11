package com.atlas.keyboard

import kotlin.math.abs
import kotlin.random.Random

fun main() {
    val width = 1076f
    // Three words in their thirds, as the bar lays them out, at about the bar's own letter size.
    fun bar(vararg words: String): List<Glyph> = words.flatMapIndexed { w, word ->
        val start = width / 3 * w + width / 6 - word.length * 11f
        word.mapIndexed { i, c -> Glyph(c, w, start + i * 22f + 11f, Idle.H / 2, 22f, 40f) }
    }
    val bars = listOf(listOf("going", "good", "gonna"), listOf("the", "I", "and"), listOf("recommendations", "recommend", "recommended"))
    val kinds = HashSet<IdlePlayer.Kind>()
    val ways = HashSet<String>()

    for (seed in 1..60) for (words in bars) {
        val glyphs = bar(*words.toTypedArray())
        val player = IdlePlayer(glyphs, width, Random(seed))
        kinds += player.kind
        check(!player.returning, "Seed $seed: ${player.kind} starts as a game, not a way home")

        // Nothing pops in: after the first frame every letter is still close to its place in the words.
        player.update(16f)
        for (g in glyphs) check(abs(g.x - g.homeX) < 12 && abs(g.y - g.homeY) < 12,
            "Seed $seed: ${player.kind} moves ${g.char} out of its word gently, not at once")

        // A loop plays on for a minute, inside the bar, and never gives the bar back by itself.
        var time = 0f
        while (time < 60_000) {
            check(player.update(33f), "Seed $seed: ${player.kind} loops until you come back")
            check(!player.returning, "Seed $seed: ${player.kind} never turns into a way home by itself")
            for (g in glyphs) check(g.x in -10f..width + 10 && g.y in -10f..Idle.H + 10,
                "Seed $seed: ${player.kind} keeps ${g.char} inside the bar, not at ${g.x}, ${g.y}")
            time += 33
        }

        // You come back: the letters go home, and the bar is exactly as it was.
        val juggling = player.game is Juggle
        val withFigure = player.game.figure != null
        player.comeBack()
        check(player.returning, "Seed $seed: coming back starts the way home")
        val way = player.game
        ways += way::class.simpleName!!
        if (juggling) check(way is Drop, "Seed $seed: the juggler drops the letters")
        else if (withFigure) check(way is Exit, "Seed $seed: a game with the figure goes home quickly, and the figure with it")
        player.comeBack()
        check(player.game === way, "Seed $seed: coming back again changes nothing")
        var home = 0f
        while (player.update(33f)) {
            home += 33
            check(home < 12_000, "Seed $seed: the way home after ${player.kind} ends")
        }
        for (g in glyphs) check(g.x == g.homeX && g.y == g.homeY && g.scale == 1f && g.alpha == 1f && g.rot == 0f && !g.accent,
            "Seed $seed: after ${player.kind}, ${g.char} is back in its word exactly")
    }
    check(kinds == IdlePlayer.Kind.entries.toSet(), "Over many bars every game turns up, not only $kinds")
    check("Shell" in ways && "Firework" in ways && "Exit" in ways && "Drop" in ways, "Every way home turns up, not only $ways")

    // The same seed plays the same game.
    val a = bar("hello", "help", "held")
    val b = bar("hello", "help", "held")
    val first = IdlePlayer(a, width, Random(7))
    val second = IdlePlayer(b, width, Random(7))
    repeat(200) { first.update(33f); second.update(33f) }
    check(a.zip(b).all { (x, y) -> x.x == y.x && x.y == y.y }, "The same seed plays the same game")
    println("Idle games checks passed")
}

private fun check(ok: Boolean, message: String) {
    if (!ok) throw AssertionError(message)
}
