package com.atlas.keyboard

/** A flick up on the letter keys picks a strip word (#44). No real swipe, steer from Space or delete flick may count as one. */
fun main() {
    val failures = ArrayList<String>()
    fun same(actual: Any?, expected: Any?, message: String) {
        if (actual != expected) failures += "$message: expected $expected, got $actual"
    }

    same(PickFlick.third(stroke(.17f to .62f, .17f to .38f)), 0, "A flick up from Z, X or C picks the left word")
    same(PickFlick.third(stroke(.5f to .62f, .5f to .38f)), 1, "A flick up from V or B picks the middle word")
    same(PickFlick.third(stroke(.75f to .65f, .76f to .35f)), 2, "A flick up from N or M picks the right word")
    same(PickFlick.third(stroke(.45f to .62f, .46f to .4f, .465f to .36f)), 1, "A flick that leans a little still counts")
    same(PickFlick.third(stroke(.45f to .62f, .48f to .38f)), 1, "A flick that leans 17 degrees, as the owner's did, still counts")
    same(PickFlick.third(stroke(.5f to .7f, .52f to .3f)), 1, "A long flick from the bottom of the row to the top of the next still counts")
    same(PickFlick.third(stroke(.45f to .62f, .5f to .38f)), null, "A stroke that leans 27 degrees is a swipe")
    same(PickFlick.third(stroke(.82f to .38f, .83f to .13f)), null, "A stroke up from the middle row is a swipe")
    same(PickFlick.third(stroke(.6364f to .4345f, .5989f to .2449f)), null, "The short \"my\" from the middle row stays a swipe")
    same(PickFlick.third(stroke(.5f to .62f, .5f to .2f)), null, "A stroke that goes on to the top row is a swipe")
    same(PickFlick.third(stroke(.55f to .625f, .55f to .375f, .55f to .125f)), null, "\"by\" goes up two rows, so it stays a swipe")
    same(PickFlick.third(stroke(.65f to .625f, .6f to .375f, .55f to .125f)), null, "\"my\" stays a swipe")
    same(PickFlick.third(stroke(.5f to .62f, .5f to .38f, millis = 500)), null, "A slow stroke up is not a flick")
    same(PickFlick.third(stroke(.3f to .62f, .45f to .38f)), null, "A slanted stroke is a swipe")
    same(PickFlick.third(stroke(.5f to .62f, .5f to .52f)), null, "A stroke too short to be a swipe is not a flick")
    same(PickFlick.third(stroke(.4f to .62f, .4f to .7f, .4f to .45f)), null, "A stroke that dips first is not a flick")
    same(PickFlick.third(stroke(.4f to .62f, .4f to .3f, .4f to .45f)), null, "A stroke that turns back down is not a flick")
    same(PickFlick.third(stroke(.4f to .62f, .55f to .5f, .4f to .38f)), null, "A stroke that wanders is a swipe")
    same(PickFlick.third(stroke(.4f to .62f, .4f to .38f, .7f to .38f)), null, "A stroke that goes on along a row is a swipe")
    same(PickFlick.third(stroke(.45f to .86f, .45f to .62f)), null, "A stroke up from Space steers the cursor, it does not pick")
    same(PickFlick.third(stroke(.95f to .375f, .62f to .375f)), null, "The delete flick from Backspace does not pick")
    same(PickFlick.third(stroke(.95f to .45f, .95f to .2f)), null, "A stroke up from Backspace does not pick")
    same(isDeleteWordSwipe(stroke(.17f to .62f, .17f to .38f)), false, "A flick up is never a delete flick")
    same(PickFlick.third(stroke(.5f to .62f)), null, "One point is not a flick")

    if (failures.isNotEmpty()) {
        failures.forEach(::println)
        throw AssertionError("${failures.size} flick check(s) failed")
    }
    println("Flick checks passed")
}

/** A stroke through [points] in [millis], with a sample every 10 ms along straight lines between them. */
private fun stroke(vararg points: Pair<Float, Float>, millis: Long = 120): List<Sample> {
    if (points.size == 1) return listOf(Sample(points[0].first, points[0].second, 0))
    val steps = (millis / 10).toInt()
    return (0..steps).map { step ->
        val along = step.toFloat() / steps * (points.size - 1)
        val at = minOf(along.toInt(), points.size - 2)
        val t = along - at
        val (x0, y0) = points[at]
        val (x1, y1) = points[at + 1]
        Sample(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, step * 10L)
    }
}
