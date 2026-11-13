package com.atlas.keyboard

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

fun main() {
    // The game's width on the Titan 2 Elite: its 1076 px, less a corner's inset and 2 dp at each side, as Strip.startIdle gives it.
    val width = 870f
    // The word size at each Bar text size, in the bar's units: 20 dp at 1.5625 px a dp, times Smaller's 1, Normal's 1.2, Bigger's 1.4.
    val sizes = listOf(31.25f, 37.5f, 43.75f)
    var size = 37.5f
    // Where a letter's middle is on the bar, as Strip.startIdle measures it: the middle of Inter's ascent and descent, with the small
    // letters' middle on the line, unless the descenders would reach the floor (StripFit.baseline).
    fun baseline(size: Float) = min(Idle.LINE + size * 1118 / 2048 / 2, Idle.FLOOR - size * 494 / 2048)
    fun middle(size: Float, letters: Float = size) = baseline(size) - letters * (1984 - 494) / 2048 / 2
    // Words in their thirds, as the bar lays them out, at the bar's own letter size, on its line; a long word shrinks to fit its third,
    // on the same baseline, as StripFit shrinks it.
    fun bar(vararg words: String): List<Glyph> = words.flatMapIndexed { w, word ->
        val letters = size * min(1f, (width / 3 - 8) / (word.length * size * .55f))
        val advance = letters * .55f
        val start = width / 3 * w + width / 6 - word.length * advance / 2
        word.mapIndexed { i, c -> Glyph(c, w, start + i * advance + advance / 2, middle(size, letters), advance, letters) }
    }
    fun fits(kind: IdlePlayer.Kind, vararg words: String) = IdlePlayer.fits(kind, bar(*words))
    // The automatic capital's arrow, at the left of the bar, first in the left word, on the line with the chips.
    fun withArrow(glyphs: List<Glyph>) = listOf(Glyph(Idle.ARROW, 0, 18f, middle(size), size * .5f, size, arrow = true)) + glyphs
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

    // The idle periods (owner, 2026-11-13: "it triggers only on the 3 min mark of idle. Not resetting. The chance of triggering is
    // 1/4... no two same animations in a row. Also no two triggers in a row").
    val clock = IdleClock()
    check(IdleClock.MILLIS == 180_000L, "The roll is at exactly three minutes")
    check(!clock.roll(1_000_000), "No roll before any input")
    clock.input(10_000)
    check(!clock.roll(10_000 + IdleClock.MILLIS - 1), "Not a moment before 3:00")
    check(clock.roll(10_000 + IdleClock.MILLIS), "One roll at 3:00")
    var later = 0
    for (t in 10_000 + IdleClock.MILLIS + 1..10_000 + 4 * 3_600_000L step 1_000) if (clock.roll(t)) later++
    check(later == 0, "However long the idle lasts, nothing more rolls in that period, not $later times")
    // Input resets the clock: a key at 2:59 starts a new period, which rolls 3:00 after it, not at the old mark.
    clock.input(500_000)
    clock.input(500_000 + IdleClock.MILLIS - 1_000)
    check(!clock.roll(500_000 + IdleClock.MILLIS), "Input before 3:00 moves the roll")
    check(clock.roll(500_000 + 2 * IdleClock.MILLIS - 1_000), "to 3:00 after the last input")
    clock.input(2_000_000)
    check(clock.roll(2_000_000 + IdleClock.MILLIS), "A new period after a roll rolls again")
    clock.input(3_000_000)
    clock.stop()
    check(!clock.roll(3_000_000 + IdleClock.MILLIS), "Hidden or with the screen off, no period runs")
    val ime = java.io.File("android/kotlin/com/atlas/keyboard/AtlasIme.kt").readText()
    check(ime.contains("main.postDelayed(idleBar, IDLE_BAR_MILLIS)") && ime.contains("IDLE_BAR_MILLIS = IdleClock.MILLIS"),
        "The keyboard posts its roll for exactly 3:00, with no random extra")
    check(!ime.contains("idleEnded"), "A game that ends by itself starts no new wait")

    // The periods in a row: a quarter play, never two running, never the same game twice running.
    val schedule = IdleSchedule()
    val rolls = Random(5)
    val played = ArrayList<IdlePlayer.Kind?>()
    repeat(40_000) {
        val kind = if (schedule.trigger(rolls)) IdlePlayer.choose(all, rolls) { schedule.allows(it) } else null
        kind?.let { schedule.played(it) }
        played += kind
    }
    for (i in 1 until played.size) check(played[i] == null || played[i - 1] == null, "Period $i: no game in the period after a game")
    val kinds = played.filterNotNull()
    for (i in 1 until kinds.size) check(kinds[i] != kinds[i - 1], "Game $i: never the same game twice running, not ${kinds[i]} again")
    val games = kinds.size
    // A quarter of the periods that may play: p / (1 + p) = 20%.
    check(games in 7_400..8_600, "About one period in five plays: $games games in 40000")
    check(IdlePlayer.Kind.entries.all { kind -> kinds.any { it == kind } }, "Every game still turns up")
    // The period after a game is held back whatever the roll: it never even rolls.
    val held = IdleSchedule()
    held.played(IdlePlayer.Kind.Race)
    val sure = object : Random() { override fun nextBits(bitCount: Int) = 0 }
    check(!held.trigger(sure), "The period after a game doesn't play, even on a winning roll")
    check(held.trigger(sure), "and the one after that may")
    check(!held.allows(IdlePlayer.Kind.Race) && held.allows(IdlePlayer.Kind.Firework), "The last game can't play next, any other can")
    check(IdlePlayer.choose(bar("x"), Random(1)) { held.allows(it) } != null, "With Race out, a bar that fits others plays one")

    val ways = HashSet<String>()
    fun home(glyphs: List<Glyph>, what: String) {
        for (g in glyphs) check(g.x == g.homeX && g.y == g.homeY && g.scale == 1f && g.alpha == 1f && g.rot == 0f && !g.accent,
            "$what: ${g.char} is back in its word exactly")
    }

    for (s in sizes) for (seed in 1..12) for (words in bars) for (kind in IdlePlayer.Kind.entries) {
        size = s
        val glyphs = bar(*words.toTypedArray())
        val player = IdlePlayer(glyphs, width, Random(seed), kind)
        val what = "Seed $seed, $kind with $words at $s"
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
    size = 37.5f

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

    // The bar's line (owner, 2026-11-13: "make sure that its perfect with the new updated visual border rule"). The words sit on the
    // line, the middle of the bar and the 22 dp black border under the screen, so every game starts there and ends there. Every
    // letter's ink, the figure, the rope, the sparks and the finish line keep inside the bar: under its top, and over its floor, 2.56
    // dp over the screen's edge. This is by each letter's box; tests/idle-render checks the letters' real outlines and the screen's
    // corners too, every frame at 60 fps.
    val stripFit = java.io.File("android/kotlin/com/atlas/keyboard/StripFit.kt").readText()
    for (line in listOf("const val BAR_DP = 56f", "const val BORDER_DP = 22f", "const val LINE_DP = (BAR_DP + BORDER_DP) / 2", "const val EDGE_DP = 2.56f"))
        check(line in stripFit, "Idle.LINE and Idle.FLOOR are StripFit's: $line")
    check(abs(Idle.LINE - 88f * 39 / 56) < .001f && abs(Idle.FLOOR - 88f * (56 - 2.56f) / 56) < .001f,
        "The line is 39 of the bar's 56 dp, and the floor 2.56 dp over its edge")
    fun inside(player: IdlePlayer, what: String) {
        for (g in player.glyphs) if (g.alpha > .16f) {
            check(g.y + g.below() <= Idle.FLOOR + .01f, "$what: ${g.char}'s ink reaches under the floor, to ${g.y + g.below()}")
            check(g.y - g.above() >= Idle.TOP - .01f, "$what: ${g.char}'s ink reaches over the bar's top, to ${g.y - g.above()}")
        }
        player.game.figure?.takeIf { it.alpha > .16f }?.let { f ->
            check(f.feet + abs(sin(f.lean)) * 10 + Figure.LINE / 2 <= Idle.FLOOR + .01f, "$what: the figure's feet reach under the floor")
            check(f.feet - Figure.NECK - 2 * Figure.HEAD - Figure.LINE / 2 >= Idle.TOP - .01f, "$what: the figure's head reaches over the bar's top")
        }
        (player.game as? JumpRope)?.let { r ->
            if (r.ropeAmount > 0) check(r.ropeMid in Idle.TOP + 1.5f..Idle.FLOOR - 1.5f, "$what: the rope swings inside the bar, not at ${r.ropeMid}")
        }
        (player.game as? Race)?.let { r -> check(r.lineTop - 1.5f >= Idle.TOP && r.lineBottom + 1.5f <= Idle.FLOOR, "$what: the finish line stays inside") }
        (player.game as? Firework)?.let { f -> for (spark in f.sparks) check(spark.y + 2 <= Idle.FLOOR, "$what: a spark goes out at the floor") }
    }
    val lineBars = listOf(listOf("going", "the", "jumpy"), listOf("Hello", "and", "World"), listOf("my", "pig", "jog"))
    for (s in sizes) for (seed in 1..6) for (words in lineBars) for (kind in IdlePlayer.Kind.entries) for (way in listOf(null) + IdlePlayer.Way.entries) {
        size = s
        val glyphs = bar(*words.toTypedArray())
        if (!IdlePlayer.fits(kind, glyphs) || way == IdlePlayer.Way.Drop && kind != IdlePlayer.Kind.Juggle) continue
        val what = "$kind at $s, seed $seed, $words, ${way ?: "alone"}"
        for (g in glyphs) check(g.y == middle(s, g.size) && g.y == g.homeY, "$what: ${g.char} starts on the line")
        val player = IdlePlayer(glyphs, width, Random(seed), kind)
        var time = 0f
        while (player.update(16f)) {
            time += 16
            inside(player, "$what, at $time ms")
            if (way != null && time == 4000f) player.comeBack(way)
            check(time < IdlePlayer.LOOP_MILLIS + 15_000, "$what: ends")
        }
        home(glyphs, what)
        for (g in glyphs) check(g.y == middle(s, g.size), "$what: ${g.char} ends on the line")
    }
    // The jump rope's holders and jumper stay on the line: the holders step apart along it, and the jumper only jumps up from it.
    for (s in sizes) {
        size = s
        val glyphs = bar("going", "the", "jumpy")
        val player = IdlePlayer(glyphs, width, Random(4), IdlePlayer.Kind.JumpRope)
        repeat(90) { player.update(16f) }
        var lowest = Float.MIN_VALUE
        repeat(200) {
            player.update(16f)
            for (g in glyphs.filter { it.word == 1 }) check(g.y <= g.homeY + .001f, "At $s, the jumper never sinks under the line")
            lowest = maxOf(lowest, glyphs.first { it.word == 1 }.y)
            val holders = listOf(glyphs.first(), glyphs.last())
            for (h in holders) check(h.y <= h.homeY + .001f && h.y >= h.homeY - 4, "At $s, ${h.char} holds the rope on the line, not at ${h.y}")
        }
        check(abs(lowest - glyphs.first { it.word == 1 }.homeY) < .01f, "At $s, the jumper lands back on the line")
    }
    size = 37.5f

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
