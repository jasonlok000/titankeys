package com.atlas.keyboard

import kotlin.math.abs
import kotlin.random.Random

fun main() {
    val width = 1076f
    // Words in their thirds, as the bar lays them out, at about the bar's own letter size.
    fun bar(vararg words: String): List<Glyph> = words.flatMapIndexed { w, word ->
        val start = width / 3 * w + width / 6 - word.length * 11f
        word.mapIndexed { i, c -> Glyph(c, w, start + i * 22f + 11f, Idle.H / 2, 22f, 40f) }
    }
    fun fits(kind: IdlePlayer.Kind, vararg words: String) = IdlePlayer.fits(kind, bar(*words))
    // The automatic capital's arrow, at the left of the bar, first in the left word.
    fun withArrow(glyphs: List<Glyph>) = listOf(Glyph(Idle.ARROW, 0, 18f, Idle.H / 2, 20f, 40f, arrow = true)) + glyphs
    val bars = listOf(listOf("going", "the", "and"), listOf("good", "one", "gonna"), listOf("recommendations", "red", "recommended"))
    for (words in bars) for (kind in IdlePlayer.Kind.entries) check(fits(kind, *words.toTypedArray()), "$words fit $kind")

    // The letter rules: a game the letters do not fit is skipped.
    check(!fits(IdlePlayer.Kind.Bowling, "hi", "my", "sky"), "Bowling needs a round letter for the ball")
    check(!fits(IdlePlayer.Kind.Bowling, "ok"), "Bowling needs at least three pins besides the ball")
    check(fits(IdlePlayer.Kind.Bowling, "dish"), "One round letter and three others are enough for Bowling")
    check(IdlePlayer.ballOf(bar("hi", "my", "big"))?.char == 'g', "The ball is a round letter, the roundest first")
    check(!fits(IdlePlayer.Kind.Race, "good", "is") && !fits(IdlePlayer.Kind.JumpRope, "good", "is"), "Race and Jump rope need three words")
    check(!fits(IdlePlayer.Kind.Pong, "good", "is"), "Pong needs two side words and a ball")
    check(!fits(IdlePlayer.Kind.Juggle, "a", "them", "b") && !fits(IdlePlayer.Kind.Juggle, "a", "is", "b"), "Juggling needs a middle word of exactly three")
    check(fits(IdlePlayer.Kind.Drift, "x") && fits(IdlePlayer.Kind.Firework, "x"), "Drift and Firework play with any letters")
    repeat(500) { seed ->
        val kind = IdlePlayer.choose(bar("hi", "is", "sky"), Random(seed))
        check(kind != null && kind != IdlePlayer.Kind.Bowling && kind != IdlePlayer.Kind.Juggle, "Seed $seed: only a game that fits is picked, not $kind")
    }
    check(IdlePlayer.choose(emptyList(), Random(1)) == null, "With no letters nothing plays")

    // The chance: most idle times nothing plays at all.
    val random = Random(11)
    val plays = (1..20_000).count { IdlePlayer.shouldPlay(random) }
    check(plays in 4_600..5_400, "About a quarter of idle times start a game, not ${plays / 200}%")

    // The rarity: common, uncommon and rare, as the weights in one table say.
    val counts = IdlePlayer.Kind.entries.associateWith { 0 }.toMutableMap()
    val picker = Random(3)
    val all = bar("going", "the", "and")
    repeat(60_000) { val kind = IdlePlayer.choose(all, picker)!!; counts[kind] = counts[kind]!! + 1 }
    val total = IdlePlayer.Kind.entries.sumOf { it.weight }
    for (kind in IdlePlayer.Kind.entries) {
        val expected = 60_000f * kind.weight / total
        check(abs(counts[kind]!! - expected) < expected * .12f, "$kind is picked about as often as its weight says: ${counts[kind]} for $expected")
    }
    val common = IdlePlayer.Kind.entries.filter { !it.loops }
    val rare = IdlePlayer.Kind.entries.filter { it.loops }
    check(common.toSet() == setOf(IdlePlayer.Kind.Bowling, IdlePlayer.Kind.Race, IdlePlayer.Kind.Firework), "Bowling, Race and Firework play once")
    check(common.minOf { counts[it]!! } > rare.maxOf { counts[it]!! },
        "Bowling, Race and Firework are common, the loops Drift, Pong, Jump rope and Juggling rare: $counts")
    check(IdlePlayer.LOOP_MILLIS == 30_000f, "A loop plays 30 seconds at most")

    // The idle times in a row: never two games back to back, and no game again within two idle times.
    val schedule = IdleSchedule()
    val rolls = Random(5)
    val played = ArrayList<IdlePlayer.Kind?>()
    repeat(40_000) {
        val kind = if (schedule.trigger(rolls)) IdlePlayer.choose(all, rolls) { schedule.allows(it) } else null
        kind?.let { schedule.played(it) }
        played += kind
    }
    for (i in 1 until played.size) check(played[i] == null || played[i - 1] == null, "Idle time $i: a quiet idle time always comes between games")
    for (i in played.indices) played[i]?.let { kind ->
        for (j in maxOf(0, i - IdleSchedule.GAP)..<i) check(played[j] != kind, "Idle time $i: $kind waits two idle times before it plays again")
    }
    val games = played.count { it != null }
    check(games in 6_400..9_600, "Most idle times play nothing: $games games in 40000")
    check(IdlePlayer.Kind.entries.all { kind -> played.any { it == kind } }, "Every game still turns up")

    val ways = HashSet<String>()
    fun home(glyphs: List<Glyph>, what: String) {
        for (g in glyphs) check(g.x == g.homeX && g.y == g.homeY && g.scale == 1f && g.alpha == 1f && g.rot == 0f && !g.accent,
            "$what: ${g.char} is back in its word exactly")
    }

    for (seed in 1..30) for (words in bars) for (kind in IdlePlayer.Kind.entries) {
        val glyphs = bar(*words.toTypedArray())
        val player = IdlePlayer(glyphs, width, Random(seed), kind)
        val what = "Seed $seed, $kind with $words"
        check(!player.returning, "$what: starts as a game, not a way home")

        // Nothing pops in: after the first frame every letter is still close to its place in the words.
        player.update(16f)
        for (g in glyphs) check(abs(g.x - g.homeX) < 12 && abs(g.y - g.homeY) < 12, "$what: moves ${g.char} out of its word gently")

        if (!kind.loops) {
            // Bowling, Race and Firework play once and give the bar back by themselves.
            var time = 16f
            while (player.update(33f)) {
                time += 33
                check(time < 30_000, "$what: plays once and ends")
                for (g in glyphs) check(g.x in -10f..width + 10 && g.y in -10f..Idle.H + 10, "$what: keeps ${g.char} inside the bar")
            }
            home(glyphs, what)
            check(!player.update(33f), "$what: does not start again")
            continue
        }

        // A loop plays until you come back, but never more than a minute.
        val sides = glyphs.filter { it.word != 1 }
        var time = 16f
        if (seed % 2 == 0) {
            while (time < IdlePlayer.LOOP_MILLIS - 100) {
                check(player.update(33f), "$what: loops until you come back")
                check(!player.returning, "$what: does not go home by itself before a minute")
                for (g in glyphs) check(g.x in -10f..width + 10 && g.y in -10f..Idle.H + 10, "$what: keeps ${g.char} inside the bar, not at ${g.x}, ${g.y}")
                if (kind == IdlePlayer.Kind.Juggle) for (g in sides) check(g.x == g.homeX && g.y == g.homeY && g.alpha == 1f, "$what: the side words stay put")
                time += 33
            }
            val juggling = player.game is Juggle
            while (!player.returning) { check(player.update(33f), "$what: goes home by its normal way"); time += 33 }
            check(time < IdlePlayer.LOOP_MILLIS + 200, "$what: after a minute it goes home by itself")
            check(if (juggling) player.game is Drop else player.game is Exit, "$what: by its normal exit, not ${player.game::class.simpleName}")
            while (player.update(33f)) { time += 33; check(time < IdlePlayer.LOOP_MILLIS + 12_000, "$what: the way home ends") }
            home(glyphs, what)
            continue
        }

        // You come back: the letters go home, and the bar is exactly as it was.
        repeat(100) { player.update(33f) }
        val juggling = player.game is Juggle
        val withFigure = player.game.figure != null
        player.comeBack()
        check(player.returning, "$what: coming back starts the way home")
        val way = player.game
        ways += way::class.simpleName!!
        if (juggling) check(way is Drop, "$what: the juggler drops the letters")
        else if (withFigure) check(way is Exit, "$what: a game with the figure goes home quickly, and the figure with it")
        player.comeBack()
        check(player.game === way, "$what: coming back again changes nothing")
        var back = 0f
        while (player.update(33f)) { back += 33; check(back < 12_000, "$what: the way home ends") }
        home(glyphs, what)
    }
    check("Shell" in ways && "Firework" in ways && "Exit" in ways && "Drop" in ways, "Every way home turns up, not only $ways")

    // The arrow plays along while it shows, and always comes home to its own place, never left behind or out of the bar.
    val arrowBars = bars.map { withArrow(bar(*it.toTypedArray())) } + listOf(withArrow(bar("good", "", "gonna")))
    val moved = HashSet<IdlePlayer.Kind>()
    for (seed in 1..20) for (glyphs in arrowBars) for (kind in IdlePlayer.Kind.entries) {
        if (!IdlePlayer.fits(kind, glyphs)) continue
        val arrow = glyphs.first()
        val player = IdlePlayer(glyphs, width, Random(seed), kind)
        val what = "Seed $seed, $kind with the arrow"
        var time = 0f
        var away = 0f
        var back = false
        while (player.update(33f)) {
            time += 33
            check(time < IdlePlayer.LOOP_MILLIS + 30_000, "$what: ends")
            check(arrow.x in -10f..width + 10 && arrow.y in -10f..Idle.H + 10, "$what: keeps the arrow inside the bar, not at ${arrow.x}, ${arrow.y}")
            away = maxOf(away, abs(arrow.x - arrow.homeX) + abs(arrow.y - arrow.homeY))
            if (kind == IdlePlayer.Kind.Juggle && !player.returning) check(arrow.x == arrow.homeX && arrow.y == arrow.homeY, "$what: the arrow stays put")
            if (!back && seed % 2 == 1 && time > 4_000) { player.comeBack(); back = true }
        }
        check(arrow.x == arrow.homeX && arrow.y == arrow.homeY && arrow.scale == 1f && arrow.alpha == 1f && arrow.rot == 0f && !arrow.accent,
            "$what: the arrow is back in its place exactly")
        if (away > 20) moved += kind
    }
    for (kind in listOf(IdlePlayer.Kind.Drift, IdlePlayer.Kind.Pong, IdlePlayer.Kind.Bowling, IdlePlayer.Kind.Race, IdlePlayer.Kind.Firework))
        check(kind in moved, "$kind plays with the arrow, not only $moved")
    check(fits(IdlePlayer.Kind.Pong, "good", "", "gonna").not() && IdlePlayer.fits(IdlePlayer.Kind.Pong, withArrow(bar("good", "", "gonna"))),
        "With no middle letter, the arrow is Pong's ball")

    // The shell game is a way home only with three words.
    repeat(200) { seed ->
        val glyphs = bar("good", "is")
        val player = IdlePlayer(glyphs, width, Random(seed), IdlePlayer.Kind.Drift)
        player.update(33f)
        player.comeBack()
        check(player.game !is Shell, "Seed $seed: no shell game with two words")
    }

    // The same seed plays the same game.
    val a = bar("hello", "help", "held")
    val b = bar("hello", "help", "held")
    val first = IdlePlayer(a, width, Random(7), IdlePlayer.Kind.Pong)
    val second = IdlePlayer(b, width, Random(7), IdlePlayer.Kind.Pong)
    repeat(200) { first.update(33f); second.update(33f) }
    check(a.zip(b).all { (x, y) -> x.x == y.x && x.y == y.y }, "The same seed plays the same game")
    println("Idle games checks passed")
}

private fun check(ok: Boolean, message: String) {
    if (!ok) throw AssertionError(message)
}
