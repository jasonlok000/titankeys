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
        same(end.letters.size, letters.size, "At the end every letter is in the build or beside it")
        check(end.letters.none { it.scale == 1f }, "Scene $seed takes every letter from the words")
        same(end.carrying, 0, "At the end nothing is left in the bundle")
        check(!end.figureShown, "Scene $seed ends with the figure gone, and only the build stays")
        check(end.letters.all { it.x in 0f..width && it.y in 0f..height }, "Scene $seed keeps its letters on the bar")
        val later = scene.at(scene.millis + 60_000)
        same(later.figureX to later.letters, end.figureX to end.letters, "After the scene nothing moves, so it costs nothing to show")
        // It builds as it goes: some letters are already in the build while others are still in their words.
        check((0..scene.millis step 250).any { time ->
            val frame = scene.at(time)
            frame.letters.any { it.scale == IdleScene.BUILT_SCALE } && frame.letters.any { it.scale == 1f }
        }, "Scene $seed starts building before every word is taken")
        check((0..scene.millis step 250).all { scene.at(it).figureX in -60f * unit..width + 60f * unit }, "The figure never goes far")
        same(IdleScene(letters, width, height, unit, Random(seed)).at(5000).figureX, scene.at(5000).figureX, "The same seed plays the same scene")
    }
    check(builds.size == IdleScene.Build.entries.size, "Over many scenes every kind of build turns up, not only $builds")
    check(lengths.distinct().size > 10, "Scenes differ from each other in pace: $lengths")
    for (build in IdleScene.Build.entries) {
        val places = IdleScene.shape(build, 300f, height, unit)
        check(places.size >= 7, "A $build has enough pieces to look like one")
        check(places.all { it.second in 0f..height }, "A $build fits the height of the bar")
    }
    val many = (0 until 6).flatMap { word("something", 40f + it * 170f, it) }
    val crowded = IdleScene(many, width, height, unit, Random(3))
    check(crowded.at(crowded.millis).letters.all { it.y in 0f..height && it.x in 0f..width }, "Many letters still fit on the bar")
    val empty = IdleScene(emptyList(), width, height, unit, Random(1))
    check(empty.millis in IdleScene.MIN_MILLIS..IdleScene.MAX_MILLIS && empty.at(empty.millis).letters.isEmpty(), "A bar with no words still plays safely")

    if (failures.isNotEmpty()) {
        failures.distinct().take(20).forEach(::println)
        throw AssertionError("${failures.size} idle scene check(s) failed")
    }
    println("Idle scene checks passed")
}
