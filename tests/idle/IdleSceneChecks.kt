package com.atlas.keyboard

import kotlin.random.Random

/** The idle scene on the bar: it plays out in 20 to 40 seconds, builds with every letter, leaves only the build, and then stays still. */
fun main() {
    val failures = ArrayList<String>()
    fun same(actual: Any?, expected: Any?, message: String) {
        if (actual != expected) failures += "$message: expected $expected, got $actual"
    }
    fun check(condition: Boolean, message: String) {
        if (!condition) failures += message
    }

    val unit = 2.75f
    val width = 1080f
    val height = 56 * unit
    fun word(text: String, left: Float, index: Int) = text.mapIndexed { i, c -> IdleScene.Letter(c, left + i * 25f, 100f, index) }
    val letters = word("their", 120f, 0) + word("there", 450f, 1) + word("they're", 780f, 2)

    val builds = HashSet<IdleScene.Build>()
    val lengths = ArrayList<Long>()
    for (seed in 0 until 40) {
        val scene = IdleScene(letters, width, height, unit, Random(seed))
        builds += scene.build
        lengths += scene.millis
        check(scene.millis in IdleScene.MIN_MILLIS..IdleScene.MAX_MILLIS, "Scene $seed lasts 20 to 40 seconds, not ${scene.millis}")
        val start = scene.at(0)
        same(start.letters.size, letters.size, "At the start every letter is on the bar")
        check(start.letters.zip(letters).all { (placed, home) -> placed.x == home.x && placed.y == home.y && placed.scale == 1f },
            "At the start the letters are where the words were, so the bar looks the same")
        check(!start.figureShown, "The figure starts out of sight")
        val end = scene.at(scene.millis)
        check(end.letters.size >= letters.size, "At the end every letter is in the build or beside it")
        check(end.letters.none { it.scale == 1f }, "Scene $seed takes every letter from the words")
        val left = end.letters.minOf { it.x }
        val right = end.letters.maxOf { it.x }
        check(right - left >= width * .4f, "Scene $seed builds something wide: ${(right - left) / width} of the bar")
        val rows = end.letters.map { it.y }.distinct().size
        check(rows <= 6, "Scene $seed stays low and wide, in $rows rows")
        same(end.carrying, 0, "At the end nothing is left in the bundle")
        check(!end.figureShown, "Scene $seed ends with the figure gone, and only the build stays")
        check(end.letters.all { it.x in 0f..width && it.y in 0f..height }, "Scene $seed keeps its letters on the bar")
        val later = scene.at(scene.millis + 60_000)
        same(later.figureX to later.letters, end.figureX to end.letters, "After the scene nothing moves, so it costs nothing to show")
        // It builds as it goes: some letters are already in the build while others are still in their words.
        check((0..scene.millis step 250).any { time ->
            val frame = scene.at(time)
            frame.letters.any { it.scale < 1f && it.scale > IdleScene.CARRIED_SCALE } && frame.letters.any { it.scale == 1f }
        }, "Scene $seed starts building before every word is taken")
        check((0..scene.millis step 250).all { scene.at(it).figureX in -60f * unit..width + 60f * unit }, "The figure never goes far")
        same(IdleScene(letters, width, height, unit, Random(seed)).at(5000).figureX, scene.at(5000).figureX, "The same seed plays the same scene")
    }
    check(builds.size == IdleScene.Build.entries.size, "Over many scenes every kind of build turns up, not only $builds")
    check(lengths.distinct().size > 10, "Scenes differ from each other in pace: $lengths")
    for (build in IdleScene.Build.entries) for (count in listOf(IdleScene.MIN_PIECES, 25, IdleScene.MAX_PIECES)) {
        val grid = IdleScene.design(build, count, Random(count))
        same(grid.size, count, "A $build of $count pieces uses every piece")
        same(grid.distinct().size, count, "A $build puts no two pieces in one place")
        check(grid.all { it.second in 0 until IdleScene.MAX_ROWS }, "A $build stays within the bar's height")
        val columns = grid.maxOf { it.first } + 1
        check(columns >= count / IdleScene.MAX_ROWS, "A $build of $count pieces is wide, $columns columns")
        check(grid.zipWithNext().all { (a, b) -> a.first < b.first || a.first == b.first && a.second < b.second },
            "A $build goes up from left to right, and from the bottom up")
    }
    val short = IdleScene(word("hi", 500f, 0), width, height, unit, Random(5))
    check(short.at(short.millis).letters.size >= IdleScene.MIN_PIECES, "A bar with few letters gets spares, so the build is still big")
    val many = (0 until 6).flatMap { word("something", 40f + it * 170f, it) }
    val crowded = IdleScene(many, width, height, unit, Random(3))
    check(crowded.at(crowded.millis).letters.all { it.y in 0f..height && it.x in 0f..width }, "Many letters still fit on the bar")
    val empty = IdleScene(emptyList(), width, height, unit, Random(1))
    check(empty.millis in IdleScene.MIN_MILLIS..IdleScene.MAX_MILLIS, "A bar with no words still plays safely")

    if (failures.isNotEmpty()) {
        failures.distinct().take(20).forEach(::println)
        throw AssertionError("${failures.size} idle scene check(s) failed")
    }
    println("Idle scene checks passed")
}
