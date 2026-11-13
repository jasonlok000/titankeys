package com.atlas.keyboard

import kotlin.math.abs

/** The strip's long words: they shrink, then borrow room, then lose the part they share with the other words. */
fun main() {
    val failures = ArrayList<String>()
    fun same(actual: Any?, expected: Any?, message: String) {
        if (actual != expected) failures += "$message: expected $expected, got $actual"
    }
    fun check(condition: Boolean, message: String) {
        if (!condition) failures += message
    }
    // Every letter is 10 wide, and the ellipsis too.
    val measure: (String) -> Float = { it.length * 10f }
    fun fits(slots: List<StripFit.Slot>) = slots.all { measure(it.text) * it.scale <= it.width - 10f + .01f }
    fun total(slots: List<StripFit.Slot>) = slots.sumOf { it.width.toDouble() }.toFloat()

    // Short words keep their size and equal places.
    val plain = StripFit.fit(listOf("the", "and", "you"), 300f, 5f, measure)
    same(plain.map { it.text to it.scale }, listOf("the" to 1f, "and" to 1f, "you" to 1f), "Short words stay as they are")
    same(plain.map { it.width }, listOf(100f, 100f, 100f), "Short words share the strip evenly")

    // 1. A word a little too long shrinks in its own place.
    val shrunk = StripFit.fit(listOf("the", "something", "you"), 290f, 5f, measure)
    same(shrunk[1].text, "something", "A word that fits when smaller shows whole")
    check(shrunk[1].scale < 1f && shrunk[1].scale >= StripFit.MIN_SCALE, "It shrinks, but not below the smallest size: ${shrunk[1].scale}")
    same(shrunk.map { it.width }, List(3) { 290f / 3 }, "Shrinking alone leaves the places even")

    // 2. A word too long even when small takes room from the short words beside it.
    val borrowed = StripFit.fit(listOf("a", "internationalization", "of"), 300f, 5f, measure)
    same(borrowed[1].text, "internationalization", "With room from its neighbours the long word shows whole")
    check(borrowed[1].width > 100f && borrowed[0].width < 100f && borrowed[2].width < 100f, "The long word's place grows, the others shrink: ${borrowed.map { it.width }}")
    check(abs(total(borrowed) - 300f) < .01f, "The places still fill the strip exactly")
    check(fits(borrowed), "Every word fits its place: $borrowed")
    same(borrowed[0].text to borrowed[0].scale, "a" to 1f, "A neighbour that lends room keeps its own size")
    check(borrowed[0].width >= 10f + 10f - .01f, "A neighbour keeps room for itself")
    val empty = StripFit.fit(listOf("", "internationalization", ""), 300f, 5f, measure)
    same(empty[1].text to empty[1].scale, "internationalization" to 1f, "Empty places lend all their room, so the word can stay full size")

    // 3. Too long for any room: cut where the words differ.
    val similar = StripFit.fit(listOf("recommendations", "recommend", "recommended"), 240f, 5f, measure)
    check(fits(similar), "Cut words fit: $similar")
    check(similar[0].text.startsWith(StripFit.ELLIPSIS) && similar[0].text.endsWith("ations"), "The long one keeps its end: ${similar[0].text}")
    same(similar[1].text, "recommend", "The one that fits when small shows whole")
    check(similar[2].text.startsWith(StripFit.ELLIPSIS) && similar[2].text.endsWith("ended"), "The one that shares the start keeps its end: ${similar[2].text}")
    same(similar.map { it.text }.distinct().size, 3, "The three can be told apart")
    val alone = StripFit.fit(listOf("extraordinarily", "thoughtfulness", "incomprehensibly"), 240f, 5f, measure)
    check(alone.all { it.text.endsWith(StripFit.ELLIPSIS) && !it.text.startsWith(StripFit.ELLIPSIS) }, "Words with nothing in common keep their starts: ${alone.map { it.text }}")
    same(alone[0].text.removeSuffix(StripFit.ELLIPSIS), "extraordinarily".take(alone[0].text.length - 1), "A start cut keeps the first letters")
    same(StripFit.cut("recommendations", listOf("recommend", "recommended"), 130f, measure), "recommendati…",
        "When more than the shared start fits, the start tells it apart, so it stays")
    same(StripFit.cut("Recommended", listOf("recommend"), 100f, measure), "…commended", "Shared starts are found in any case")

    // The strip's three words: their places never move or shrink, as the middle one is muscle memory.
    val fixed = StripFit.fit(listOf("a", "internationalization", "of"), 300f, 5f, measure, lend = false)
    same(fixed.map { it.width }, List(3) { 100f }, "Without lending, the places stay even, whatever the words")
    check(fits(fixed), "The long word still fits its own place: $fixed")
    // The owner's case: typing "recommendatons", the middle word was cut at its end while the sides showed whole.
    val owner = StripFit.fit(listOf("recommendation's", "recommendations", "recommendation"), 300f, 5f, measure, lend = false)
    same(owner.map { it.width }, List(3) { 100f }, "The places stay even")
    check(fits(owner), "All three fit: $owner")
    check(owner[1].text.startsWith(StripFit.ELLIPSIS) && owner[1].text.endsWith("tions"), "The middle keeps the end where it differs: ${owner[1].text}")
    check(owner[0].text.endsWith("n's") && owner[2].text.endsWith("tion"), "The sides keep their ends too: ${owner.map { it.text }}")
    same(owner.map { it.text }.distinct().size, 3, "The three can be told apart")

    // The clipboard chip: one place, the same rules.
    val clip = StripFit.fit(listOf("Meet me at the station at noon tomorrow, and bring the tickets"), 300f, 5f, measure)
    check(clip.single().scale == StripFit.MIN_SCALE && clip.single().text.endsWith(StripFit.ELLIPSIS), "A long clip shrinks, then loses its end: $clip")
    check(fits(clip), "The clip fits the chip")
    same(StripFit.fit(listOf("Hello"), 300f, 5f, measure).single(), StripFit.Slot("Hello", 1f, 300f), "A short clip stays whole")
    same(StripFit.fit(emptyList(), 300f, 5f, measure), emptyList<StripFit.Slot>(), "No words, no places")

    // All three words are one size (owner, 2026-11-13: "I want it consistent"): a long one shrinks only in its own place.
    val even = StripFit.fit(listOf("a", "internationalization", "of"), 300f, 5f, measure, lend = false)
    check(even[0].scale == 1f && even[2].scale == 1f && even[1].scale < 1f, "Only the long word shrinks: $even")

    // The words sit truly in the middle of the bar: the middle of Inter's small letters on its centre line, not the font's box.
    val size = 31.25f
    check(kotlin.math.abs(StripFit.baseline(44f, size) - size * StripFit.X_HEIGHT / 2 - 44f) < .001f, "The baseline puts the letters' middle on the line")
    // A view with its text at the top starts it at [StripFit.top]: its baseline is that, less its ascent as Android rounds it.
    fun drawnBaseline(textSize: Float) = kotlin.math.floor(-textSize * 1984 / 2048).toInt().let { ascent -> StripFit.top(88, size, ascent) - ascent }
    val full = drawnBaseline(size)
    check(kotlin.math.abs(full - size * StripFit.X_HEIGHT / 2 - 44f) <= 1f, "A full-size word's letters are within 1 px of the centre: $full")
    // "recommendations" shrunk to fit beside full-size words sits on their line (owner, 2026-11-13: one bar baseline).
    val long = StripFit.fit(listOf("recommendations", "the", "you"), 300f, 5f, measure, lend = false)
    check(long[0].scale < 1f && long[1].scale == 1f, "The long word shrinks and the others don't: $long")
    for (scale in listOf(long[0].scale, StripFit.MIN_SCALE, .85f)) {
        val shrunk = drawnBaseline(size * scale)
        check(kotlin.math.abs(shrunk - full) <= .5f, "A word at $scale sits on the full-size line: $shrunk, not $full")
    }

    // Settings > Size at Biggest: the bar's places are as wide as ever, but every letter is bigger. The three places stay even,
    // and long words shrink or lose a part to fit, as at Normal.
    val biggest: (String) -> Float = { measure(it) * FixedScale.BIGGEST }
    fun fitsBiggest(slots: List<StripFit.Slot>) = slots.all { biggest(it.text) * it.scale <= it.width - 10f + .01f }
    for (words in listOf(listOf("the", "and", "you"), listOf("the", "something", "you"), listOf("a", "internationalization", "of"),
        listOf("recommendation's", "recommendations", "recommendation"))) {
        val big = StripFit.fit(words, 300f, 5f, biggest, lend = false)
        same(big.map { it.width }, List(3) { 100f }, "At Biggest the three places stay even for $words")
        check(fitsBiggest(big), "At Biggest every word fits its place: $big")
        check(big.all { it.scale >= StripFit.MIN_SCALE }, "At Biggest no word goes below the smallest size: $big")
    }

    if (failures.isNotEmpty()) {
        failures.forEach(::println)
        throw AssertionError("${failures.size} strip fit check(s) failed")
    }
    println("Strip fit checks passed")
}
