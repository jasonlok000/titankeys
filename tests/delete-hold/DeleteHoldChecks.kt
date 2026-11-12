package com.atlas.keyboard

import android.view.KeyEvent

private const val KEY = KeyEvent.KEYCODE_A

private val holdMillis = deleteSwipeTuning["deleteHoldMillis"] as Long
private val slowMillis = deleteSwipeTuning["deleteRepeatSlowMillis"] as Long
private val fastMillis = deleteSwipeTuning["deleteRepeatFastMillis"] as Long

/** Where a flick from .95 stops, for a length of 1.8, 4.5, and 7 key widths. */
private const val SHORT_STOP = .77f
private const val MIDDLE_STOP = .575f
private const val LONG_STOP = .4f

fun main() {
    aNormalFlickDeletesOneWordAndDoesNotRepeat()
    aHeldFlickRepeatsUntilTheLift()
    aShortFlickWaitsTheLongest()
    aLongFlickWaitsTheShortest()
    aMiddleFlickWaitsBetween()
    theWaitIsTheSameForEveryWordOfAHold()
    theWaitCountsToWhereTheFingerStopped()
    backAtTheStartTheHoldPauses()
    aFlickNeverEndsInThePauseZone()
    thePaceTopsOutAtTheTopSpeedLine()
    eachZoneTicksOncePerCrossing()
    theHoldWaits400()
    aClearFlickCrunchesAndAWordDoesNot()
    moreLengthDoesNotPassTheLimits()
    theFirstWordIsTheFlickItself()
    liftingStopsAtOnceWithNoExtraWord()
    liftingBetweenWordsAddsNothing()
    aBusyHoldAddsNothingAfterTheLift()
    aKeyPressStopsAHold()
    aKeyPressBeforeTheHoldStopsIt()
    aFieldStartCancelsAHold()
    aFieldStartCancelsAWaitingHold()
    aFingerThatKeepsMovingWaits()
    aSmallShakeDoesNotRestartTheWait()
    aFingerThatTurnsBackIsNotAFlick()
    swipeTypingOffGivesNoHold()
    thePanelGivesNoHold()
    aFlickThatDoesNotCountGivesNoHold()
    aBusyEditDoesNotChangeTheWait()
    aStrokeJustAfterASwipeCannotDelete()
    theGuideZoneIsWhereFlicksStart()
    aWordSwipeIsNotAFlick()
    aWordDeleteLeavesNoTwoSpaces()
    everyFlickGlowsAtItsLift()
    aFlickNeverShowsAsASwipeFirst()
    println("Delete hold checks passed")
}

/**
 * A delete flick is a short, quick, straight stroke to the left. A word that starts at the right and runs left along the keys,
 * such as "press" from P, is straight enough, but it goes further, or takes longer: it stays a word.
 */
private fun aWordSwipeIsNotAFlick() {
    // "press": from P along the top row to R and E, then down to S, in about half a second.
    val press = listOf(at(.95f, .15f, 0), at(.75f, .16f, 90), at(.55f, .17f, 180), at(.35f, .18f, 270), at(.25f, .2f, 330),
        at(.2f, .3f, 400), at(.15f, .38f, 460))
    same(isDeleteWordSwipe(press), false, "A swipe of \"press\" from P is a word, not a delete")
    // The owner's false deletes: straight, but six keys or more.
    same(isDeleteWordSwipe((0..12).map { at(.95f - .62f * it / 12, .35f, it * 15L) }), false, "A straight stroke of six keys is a word, even a quick one")
    // A slow drag of three keys.
    same(isDeleteWordSwipe((0..20).map { at(.95f - .3f * it / 20, .35f, it * 40L) }), false, "A stroke that takes 800 ms to get there is a word")
    // Real flicks still delete: two keys in 80 ms, and five keys in 200 ms.
    same(isDeleteWordSwipe((0..8).map { at(.95f - .2f * it / 8, .35f, it * 10L) }), true, "A quick flick of two keys deletes")
    same(isDeleteWordSwipe((0..10).map { at(.95f - .5f * it / 10, .35f, it * 20L) }), true, "A quick flick of five keys deletes")
    // A finger that rests after a quick flick, for a hold, is still a flick: only the way there is timed.
    val rest = (0..8).map { at(.95f - .3f * it / 8, .35f, it * 12L) } + (1..40).map { at(.65f, .35f, 96L + it * 50) }
    same(isDeleteWordSwipe(rest), true, "A quick flick that then rests two seconds is still a flick")
    // A stroke that bows away from a straight line more than a flick does is a word.
    val bowed = (0..10).map { val u = it / 10f; at(.95f - .3f * u, .4f - .2f * kotlin.math.sin(u * Math.PI.toFloat()), it * 15L) }
    same(isDeleteWordSwipe(bowed), false, "A stroke that curves is a word")
}

/** The guide shades where a delete flick can start, and its ghost flick starts there and really deletes. */
private fun theGuideZoneIsWhereFlicksStart() {
    val (x, y) = GUIDE_FLICK_START
    same(startsDeleteFlick(x, y), true, "The ghost's flick starts inside the shaded zone")
    same(Layout.keyAt(x, y), Layout.BACKSPACE, "The ghost's flick starts on Backspace")
    val ghost = (0..10).map { at(x + (GUIDE_FLICK_END_X - x) * it / 10, y, it * 15L) }
    same(isDeleteWordSwipe(ghost), true, "The ghost's flick is a real delete flick")
    // Each corner of each box, just inside, starts a flick that deletes; just outside the zone, a flick does not.
    for ((left, top, right, bottom) in deleteStartBoxes) for ((cx, cy) in listOf(left + .01f to top + .01f, minOf(right, 1f) - .01f to bottom - .01f)) {
        val stroke = (0..10).map { at(cx - .3f * it / 10, cy, it * 15L) }
        same(startsDeleteFlick(cx, cy), true, "A point just inside the zone can start a flick")
        same(isDeleteWordSwipe(stroke), true, "A flick from just inside the zone at $cx, $cy deletes")
    }
    same(startsDeleteFlick(.7f, .375f), false, "Left of K is outside the zone")
    // The guide always shades the rectangle, and its ghost flick starts where a real flick deletes.
    val (left, top, right, bottom) = guideDeleteBox
    for ((cx, cy, key) in listOf(Triple(.78f, .2f, "I"), Triple(.85f, .2f, "O"), Triple(.78f, .58f, "M"), Triple(.85f, .58f, Layout.SYM)))
        same(cx >= left && cx < right && cy >= top && cy < bottom, true, "The guide's patch covers $key")
    same(startsDeleteFlick(x, y) && x >= left && y >= top, true, "The ghost's flick starts in both the patch and the real zone")
    // A replay of real swipes said no to the rectangle for the real gesture, so it is off.
    same(DELETE_RECTANGLE, false, "The real delete zone is today's L shape")
    same(deleteStartBoxes.size, 2, "Two boxes: the right edge and the middle row from K")
    same(isDeleteWordSwipe((0..10).map { at(.85f - .3f * it / 10, .2f, it * 15L) }), false, "A flick from O does not delete")
    if (DELETE_RECTANGLE) {
        // One rectangle as wide and as tall as the old L shape: it takes in the edges of I, O, M and Sym too.
        same(deleteStartBoxes.size, 1, "The zone is one rectangle")
        for ((cx, cy, key) in listOf(Triple(.78f, .2f, "I"), Triple(.85f, .2f, "O"), Triple(.78f, .58f, "M"), Triple(.85f, .58f, Layout.SYM))) {
            same(Layout.keyAt(cx, cy), key, "The rectangle reaches $key")
            same(isDeleteWordSwipe((0..10).map { at(cx - .3f * it / 10, cy, it * 15L) }), true, "A flick from $key deletes")
        }
    }
    same(isDeleteWordSwipe((0..10).map { at(.7f - .3f * it / 10, .375f, it * 15L) }), false, "A flick from outside the zone does not delete")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private fun isTrue(value: Boolean, message: String) {
    if (!value) throw AssertionError(message)
}

private fun at(x: Float, y: Float, time: Long) = Sample(x, y, time)

/** Runs one delete flick and its timer the way the keyboard does, with the time passed in. */
private class Rig {
    val contact = Contact()
    val hold = DeleteHold(contact)
    /** The time the timer would run next, or null if it is not set. */
    var next: Long? = null
    val flicks = ArrayList<Contact.Outcome.Swipe>()
    val words = ArrayList<Long>()

    fun land(sample: Sample) {
        hold.reset()
        next = null
        contact.down(sample)
    }

    fun move(sample: Sample, allowed: Boolean = true) {
        contact.move(listOf(sample))
        if (!contact.deleting) next = hold.moved(allowed) else hold.follow(sample)?.let { next = it }
    }

    /** A flick left from Backspace that has stopped at [stop], with the finger still down. It stops at [flick] + 60. */
    fun flick(flick: Long, stop: Float = SHORT_STOP) {
        land(at(.95f, .35f, flick))
        for (step in 1..4) move(at(.95f + (stop - .95f) * step / 4, .35f, flick + 15 * step))
    }

    /** Lets the timer run up to and including [until]. */
    fun runTo(until: Long, free: Boolean = true) {
        while (true) {
            val time = next ?: return
            if (time > until) return
            next = hold.step(time, free) { flick ->
                if (flick != null) flicks += flick
                words += time
            }
        }
    }

    fun lift(time: Long): Contact.Outcome? {
        hold.reset()
        next = null
        val settlement = contact.up(emptyList(), time) ?: return null
        return contact.settle(settlement, settlement.deadline)
    }
}

private fun aNormalFlickDeletesOneWordAndDoesNotRepeat() {
    val rig = Rig()
    rig.flick(1_000)
    val lifted = rig.lift(1_100)
    same((lifted as? Contact.Outcome.Swipe)?.deletesWord, true, "A flick that lifts must delete its word when it lifts")
    rig.runTo(10_000)
    same(rig.words.size, 0, "A normal flick must not start to repeat")
}

private fun aHeldFlickRepeatsUntilTheLift() {
    val rig = Rig()
    rig.flick(1_000)
    rig.runTo(1_000 + 60 + holdMillis - 1)
    same(rig.words.size, 0, "A flick must not repeat before the finger has stayed for the hold time")
    rig.runTo(1_000 + 60 + holdMillis)
    same(rig.words.size, 1, "A flick must delete its first word when the finger has stayed for the hold time")
    rig.runTo(1_000 + 60 + holdMillis + 5_000)
    same(rig.words.size > 2, true, "A hold must go on deleting words")
    same(rig.contact.deleting, true, "A hold must go on until the finger lifts")
}

/** The waits between the words of a flick that stops at [stop] and stays for five seconds. */
private fun waitsOf(stop: Float): List<Long> {
    val rig = Rig()
    rig.flick(1_000, stop)
    rig.runTo(1_000 + 60 + holdMillis + 5_000)
    return rig.words.zipWithNext { a, b -> b - a }
}

private fun aShortFlickWaitsTheLongest() {
    same(slowMillis, 350L, "A short flick must wait 350 ms between words")
    same(waitsOf(SHORT_STOP).first(), slowMillis, "A short flick must wait the longest")
    same(waitsOf(.95f - .2f).first(), slowMillis, "A flick of two keys must count as short")
}

private fun aLongFlickWaitsTheShortest() {
    same(fastMillis, 80L, "A long flick must wait 80 ms between words")
    same(waitsOf(LONG_STOP).first(), fastMillis, "A flick of five and a half keys, the longest flick, must wait the shortest")
}

private fun aMiddleFlickWaitsBetween() {
    val wait = waitsOf(MIDDLE_STOP).first()
    same(wait, 215L, "A flick halfway between short and long must wait halfway between the two waits")
    val longer = waitsOf(MIDDLE_STOP - .1f).first()
    same(longer < wait, true, "A longer flick must wait less: $longer against $wait")
}

private fun theWaitIsTheSameForEveryWordOfAHold() {
    for (stop in listOf(SHORT_STOP, MIDDLE_STOP, LONG_STOP)) {
        val waits = waitsOf(stop)
        same(waits.size > 5, true, "A hold of five seconds must delete many words: $waits")
        same(waits.toSet().size, 1, "Every word of one hold must wait the same: $waits")
    }
}

private fun theWaitCountsToWhereTheFingerStopped() {
    // The finger flicks to the middle of the row, then goes on to where a long flick stops, and stays there.
    val rig = Rig()
    rig.flick(1_000, MIDDLE_STOP)
    rig.move(at(.4f, .35f, 1_100))
    rig.move(at(LONG_STOP, .35f, 1_200))
    rig.runTo(1_200 + holdMillis + 1_000)
    same(rig.words.zipWithNext { a, b -> b - a }.first(), fastMillis, "The length must count to where the finger stopped")
    // While the hold deletes, the speed follows how far the finger is now: further left is faster, back is slower.
    val moved = Rig()
    val started = 1_000L + 60 + holdMillis
    moved.flick(1_000, SHORT_STOP)
    moved.runTo(started)
    moved.move(at(LONG_STOP, .35f, started + 10))
    moved.runTo(started + 1_000)
    same(moved.words.zipWithNext { a, b -> b - a }.toSet(), setOf(fastMillis), "Sliding far left during the hold makes it the fastest")
    moved.move(at(MIDDLE_STOP, .35f, started + 1_010))
    val before = moved.words.size
    moved.runTo(started + 3_000)
    same(moved.words.drop(before - 1).zipWithNext { a, b -> b - a }.toSet(), setOf(215L), "Back to the middle, it slows to the middle wait")
    moved.move(at(.15f, .35f, started + 3_010))
    val far = moved.words.size
    moved.runTo(started + 4_000)
    same(moved.words.drop(far - 1).zipWithNext { a, b -> b - a }.toSet(), setOf(fastMillis), "Past five and a half keys it stays at the fastest, the cap")
    // Speeding up takes effect at once: the next word comes the new wait after the last one, not after the old wait.
    val quick = Rig()
    quick.flick(1_000, SHORT_STOP)
    quick.runTo(started)
    quick.move(at(LONG_STOP, .35f, started + 20))
    quick.runTo(started + fastMillis)
    same(quick.words.size, 2, "The next word comes sooner as soon as the finger slides further")
    // From two keys to five and a half, the speed changes smoothly with the distance: each half key a little faster, all the way.
    val waits = (0..7).map { k ->
        val rig = Rig()
        rig.flick(1_000, SHORT_STOP)
        rig.runTo(started)
        rig.move(at(.75f - k * .05f, .35f, started + 10))
        rig.runTo(started + 2_000)
        rig.words.zipWithNext { a, b -> b - a }.last()
    }
    same(waits.zipWithNext().all { (a, b) -> b < a && a - b < 60 }, true, "Each step further makes it a little faster: $waits")
    same(waits.first() to waits.last(), slowMillis to fastMillis, "The whole range: slowest at two keys, fastest at five and a half: $waits")
}

/**
 * While a hold deletes, a finger back within .8 of a key of where the flick started pauses it: no words go while it rests there.
 * Sliding out again, past a small margin, carries on at the speed for that distance, a whole wait after it left.
 */
private fun backAtTheStartTheHoldPauses() {
    val rig = Rig()
    val started = 1_000L + 60 + holdMillis
    rig.flick(1_000, SHORT_STOP)
    rig.runTo(started)
    same(rig.words.size, 1, "The hold has started")
    // Back to half a key from the start, on Backspace.
    rig.move(at(.9f, .35f, started + 10))
    same(rig.hold.paused, true, "Half a key from the start, the hold pauses")
    rig.runTo(started + 3_000)
    same(rig.words.size, 1, "No words go while the finger rests in the pause zone")
    // A shake on the edge does not leave it.
    rig.move(at(.95f - (PAUSE_KEYS + .05f) / 10, .35f, started + 3_010))
    same(rig.hold.paused, true, "A finger just past the edge stays paused, so it does not stutter")
    rig.runTo(started + 3_500)
    same(rig.words.size, 1, "and still no words go")
    // Out again, to a short flick's distance: the slow wait, counted from leaving.
    rig.move(at(SHORT_STOP, .35f, started + 4_000))
    same(rig.hold.paused, false, "Sliding out ends the pause")
    rig.runTo(started + 4_000 + slowMillis - 1)
    same(rig.words.size, 1, "The next word waits its whole step after leaving")
    rig.runTo(started + 4_000 + slowMillis * 3)
    same(rig.words.drop(1).zipWithNext { a, b -> b - a }.toSet(), setOf(slowMillis), "then words go at the speed for that distance")
    same(rig.words[1], started + 4_000 + slowMillis, "the first one a whole wait after leaving")
    // Further out goes faster, as before.
    rig.move(at(LONG_STOP, .35f, started + 6_000))
    val before = rig.words.size
    rig.runTo(started + 7_000)
    same(rig.words.drop(before - 1).zipWithNext { a, b -> b - a }.toSet(), setOf(fastMillis), "and further left is the fastest again")
}

/** A flick travels a whole key at least, so even the shortest one, resting where it ends, deletes on and never pauses. */
private fun aFlickNeverEndsInThePauseZone() {
    isTrue(PAUSE_KEYS < (deleteSwipeTuning["minTravel"] as Float) * 10, "The pause zone is narrower than the shortest flick")
    val rig = Rig()
    // The shortest flick, from Backspace a whole key and a little more.
    rig.flick(1_000, .84f)
    rig.runTo(1_000 + 60 + holdMillis + 2_000)
    same(rig.hold.paused, false, "Resting where it ends, the shortest flick is not paused")
    isTrue(rig.words.size > 3, "and it deletes on: ${rig.words.size}")
    // Before the hold starts, nothing pauses: a finger that comes back is no hold at all, or a fresh wait.
    val early = Rig()
    early.flick(1_000, SHORT_STOP)
    same(early.hold.paused, false, "Nothing is paused before the hold starts")
}

/** The pace is 0 at two keys and 1 at the top-speed line, five and a half keys; past it, it stays 1. The fastest is kept. */
private fun thePaceTopsOutAtTheTopSpeedLine() {
    val rig = Rig()
    val started = 1_000L + 60 + holdMillis
    rig.flick(1_000, SHORT_STOP)
    rig.runTo(started)
    same(rig.hold.pace, 0f, "Two keys or less is the slowest")
    rig.move(at(.95f - REPEAT_LONG_KEYS / 10, .35f, started + 10))
    same(rig.hold.pace, 1f, "At the top-speed line it is the fastest")
    rig.move(at(.1f, .35f, started + 20))
    same(rig.hold.pace, 1f, "and further on it stays there")
    same(rig.hold.repeatMillis, fastMillis, "as the words do")
    rig.move(at(MIDDLE_STOP, .35f, started + 30))
    isTrue(rig.hold.pace > 0f && rig.hold.pace < 1f, "Back in the ramp it is between: ${rig.hold.pace}")
    same(rig.hold.topPace, 1f, "The fastest it went is kept, for the dots at the lift")
}

private fun moreLengthDoesNotPassTheLimits() {
    same(waitsOf(.15f).isEmpty(), true, "A stroke past five and a half keys is a word, so it never holds")
    same(waitsOf(.83f).first(), slowMillis, "A flick under two keys must not wait more than the longest wait")
}

private fun theFirstWordIsTheFlickItself() {
    val rig = Rig()
    rig.flick(1_000)
    rig.runTo(1_000 + 60 + holdMillis + slowMillis)
    same(rig.flicks.size, 1, "Only the first word carries the flick")
    val flick = rig.flicks.single()
    same(flick.deletesWord, true, "The first word must go the way of a delete flick")
    same(flick.startedAt, 1_000L, "The flick must keep its start")
    same(flick.points.size, 5, "The flick must keep its points")
    same(rig.contact.points, emptyList<Sample>(), "A held contact must not draw a path")
    same(rig.words.size, 2, "The second word must follow the flick")
}

private fun liftingStopsAtOnceWithNoExtraWord() {
    val rig = Rig()
    rig.flick(1_000)
    rig.runTo(1_000 + 60 + holdMillis + slowMillis)
    val deleted = rig.words.size
    same(rig.lift(1_000 + 60 + holdMillis + slowMillis + 50), null, "A lift must add no word after a hold")
    rig.runTo(20_000)
    same(rig.words.size, deleted, "A lift must stop the words at once")
    same(rig.contact.deleting, false, "A lift must end the hold")
}

private fun liftingBetweenWordsAddsNothing() {
    val rig = Rig()
    rig.flick(1_000)
    val due = 1_000 + 60 + holdMillis
    rig.runTo(due)
    same(rig.words.size, 1, "The hold must have deleted its first word")
    // The timer is still set for the next word when the lift arrives, and the contact says the hold is over.
    val pending = rig.next
    same(rig.contact.up(emptyList(), due + 100), null, "A lift must add no word after a hold")
    same(rig.hold.step(pending ?: 0, true) { rig.words += 0 }, null, "A timer that runs after the lift must stop")
    same(rig.words.size, 1, "A timer that runs after the lift must delete nothing")
}

private fun aKeyPressStopsAHold() {
    val rig = Rig()
    rig.flick(1_000)
    rig.runTo(1_000 + 60 + holdMillis)
    rig.contact.key(KEY, true, 1_000 + 60 + holdMillis + 10)
    same(rig.contact.deleting, false, "A key press must end the hold")
    rig.runTo(20_000)
    same(rig.words.size, 1, "A key press must stop the words")
    same(rig.lift(20_100), null, "The lift after a key press must add no word")
}

private fun aKeyPressBeforeTheHoldStopsIt() {
    val rig = Rig()
    rig.flick(1_000)
    rig.contact.key(KEY, true, 1_200)
    rig.runTo(20_000)
    same(rig.words.size, 0, "A key press during the wait must call off the hold")
}

private fun aFieldStartCancelsAHold() {
    val rig = Rig()
    rig.flick(1_000)
    rig.runTo(1_000 + 60 + holdMillis)
    // A new field does this in onStartInput.
    rig.contact.releaseKeys()
    same(rig.contact.deleting, false, "A new field must end the hold")
    rig.runTo(20_000)
    same(rig.words.size, 1, "A new field must stop the words")
}

private fun aFieldStartCancelsAWaitingHold() {
    val rig = Rig()
    rig.flick(1_000)
    rig.contact.releaseKeys()
    rig.runTo(20_000)
    same(rig.words.size, 0, "A new field must call off a hold that has not started")
}

private fun aFingerThatKeepsMovingWaits() {
    val rig = Rig()
    rig.flick(1_000)
    // A little more travel, still quick: the finger is not holding, so the wait starts again from the last move.
    rig.runTo(1_099)
    rig.move(at(SHORT_STOP - .05f, .35f, 1_100))
    same(rig.words.size, 0, "A finger that keeps moving must not repeat")
    same(rig.next, 1_100 + holdMillis, "The wait must count from the last move")
    // Slow travel on and on is not a flick any more, but a word: no hold. Each move comes before the pause is over.
    var x = SHORT_STOP - .05f
    var time = 1_100L
    repeat(12) {
        x -= .03f
        time += holdMillis - 10
        rig.runTo(time - 1)
        rig.move(at(x, .35f, time))
    }
    same(rig.words.size, 0, "A finger that keeps moving must not repeat")
    same(rig.next, null, "A stroke that took over a second to get there is a word, so it never holds")
}

private fun aSmallShakeDoesNotRestartTheWait() {
    val rig = Rig()
    rig.flick(1_000)
    val due = rig.next
    rig.move(at(SHORT_STOP - .005f, .36f, 1_200))
    same(rig.next, due, "A small shake of a resting finger must not start the wait again")
    rig.runTo(due!!)
    same(rig.words.size, 1, "A finger that shakes a little still holds")
}

private fun aFingerThatTurnsBackIsNotAFlick() {
    val rig = Rig()
    rig.flick(1_000)
    rig.move(at(.9f, .35f, 1_200))
    same(rig.next, null, "A finger that turns back must call off the wait")
    rig.runTo(20_000)
    same(rig.words.size, 0, "A word that starts like a flick must not repeat")
}

private fun swipeTypingOffGivesNoHold() {
    val rig = Rig()
    rig.contact.swipes = false
    rig.flick(1_000)
    same(rig.next, null, "With swipe typing off a flick must not wait for a hold")
    same(rig.lift(5_000), null, "With swipe typing off a flick must do nothing")
}

private fun thePanelGivesNoHold() {
    val rig = Rig()
    rig.contact.paging = true
    rig.flick(1_000)
    same(rig.next, null, "A flick in the panel must not wait for a hold")
}

private fun aFlickThatDoesNotCountGivesNoHold() {
    val rig = Rig()
    rig.flick(1_000)
    same(rig.hold.moved(false), null, "A flick that would not count now must not wait for a hold")
    // The stroke started in a key's cooldown and has not moved far, so it is not typing yet.
    val typing = Rig()
    typing.contact.key(KEY, true, 1_000)
    typing.contact.key(KEY, false, 1_010)
    typing.land(at(.95f, .35f, 1_050))
    typing.move(at(.84f, .35f, 1_100))
    same(typing.next, null, "A stroke in a key's cooldown must not wait for a hold")
}

private fun aBusyEditDoesNotChangeTheWait() {
    val rig = Rig()
    rig.flick(1_000)
    val due = rig.next!!
    rig.next = rig.hold.step(due, false) { rig.words += due }
    same(rig.words.size, 0, "Nothing is deleted while an earlier edit is going through")
    rig.runTo(due + 100)
    same(rig.words.size, 1, "The word must go when the edit is done")
    val first = rig.words.first()
    same(rig.next, first + slowMillis, "A wait that ran busy must not change the wait after it")
    // The same again between later words.
    val second = rig.next!!
    rig.next = rig.hold.step(second, false) { rig.words += second }
    same(rig.words.size, 1, "A later word must also wait for an edit that is going through")
    rig.runTo(second + 100)
    same(rig.words.size, 2, "The later word must go when the edit is done")
    same(rig.next, rig.words.last() + slowMillis, "A busy wait between words must not change the wait after it")
}

private fun aStrokeJustAfterASwipeCannotDelete() {
    val pieces = SwipePieces({ 0L }, { false }, { false })
    val contact = Contact()
    contact.down(at(.15f, .4f, 1_000))
    contact.move(listOf(at(.45f, .4f, 1_200)))
    val settlement = contact.up(emptyList(), 1_200)!!
    pieces.settle(settlement, contact.settle(settlement, settlement.deadline))
    same(pieces.canDelete(1_300), false, "A stroke just after a swipe is the rest of that swipe")
    same(pieces.canDelete(1_500), true, "A stroke well after a swipe can delete")
}

/**
 * Going into the pause zone, and reaching the top-speed line, each tick once per crossing: nothing more while the finger stays,
 * and again only after it has left and come back. A flick that already ends past the line starts there with no tick.
 */
private fun eachZoneTicksOncePerCrossing() {
    val rig = Rig()
    val started = 1_000L + 60 + holdMillis
    rig.flick(1_000, SHORT_STOP)
    rig.runTo(started)
    same(rig.hold.crossed(), null, "No zone yet")
    rig.move(at(.95f - 5.6f / 10, .35f, started + 10))
    same(rig.hold.crossed(), DeleteHold.Zone.TopSpeed, "Reaching the top-speed line ticks")
    rig.move(at(.95f - 6.5f / 10, .35f, started + 20))
    same(rig.hold.crossed(), null, "Past it, nothing more")
    rig.move(at(.95f - 5.4f / 10, .35f, started + 30))
    same(rig.hold.crossed(), null, "Just back inside, on the line's edge, nothing")
    rig.move(at(.95f - 5.6f / 10, .35f, started + 40))
    same(rig.hold.crossed(), null, "so a finger resting on the line ticks once")
    rig.move(at(.95f - 4f / 10, .35f, started + 50))
    rig.move(at(.95f - 6f / 10, .35f, started + 60))
    same(rig.hold.crossed(), DeleteHold.Zone.TopSpeed, "Back inside and out again ticks again")
    rig.move(at(.9f, .35f, started + 70))
    same(rig.hold.crossed(), DeleteHold.Zone.Pause, "Going into the pause zone ticks")
    rig.move(at(.92f, .35f, started + 80))
    same(rig.hold.crossed(), null, "Resting there, nothing more")
    rig.move(at(.7f, .35f, started + 90))
    same(rig.hold.crossed(), null, "Leaving it is quiet")
    rig.move(at(.9f, .35f, started + 100))
    same(rig.hold.crossed(), DeleteHold.Zone.Pause, "Coming back ticks again")
    // A flick that ends past the line starts there, with no tick.
    val far = Rig()
    far.flick(1_000, .95f - 6f / 10)
    far.runTo(started)
    far.move(at(.95f - 6.2f / 10, .35f, started + 10))
    same(far.hold.crossed(), null, "A hold that starts past the line has not reached it")
}

/** The owner tried 100 ms and went back to 400 (2026-11-12). */
private fun theHoldWaits400() {
    same(holdMillis, 400L, "A finger waits 400 ms after a flick before the hold starts")
}

/** A stroke from [x], [y] to [toX], [toY] over [millis], sampled every 10 ms from [from], as the touchpad gives it. */
private fun stroke(x: Float, y: Float, toX: Float, millis: Long, toY: Float = y, from: Long = 1_000): List<Sample> =
    (0..millis / 10).map { step -> val t = step * 10f / millis; at(x + (toX - x) * t, y + (toY - y) * t, from + step * 10) }

/** What [FlickCrunch] makes of [points] as they come: its state after each, and how many breaks it heard. */
private fun crunch(points: List<Sample>, allowed: Boolean = true): Pair<List<FlickCrunch.State>, Int> {
    val crunch = FlickCrunch()
    crunch.begin(allowed)
    var breaks = 0
    val states = points.indices.map { index -> if (crunch.move(points.subList(0, index + 1))) breaks++; crunch.state }
    return states to breaks
}

/**
 * A stroke crunches only once it is clearly a delete flick: 1.5 keys from where it started, inside the delete zone and no higher
 * than the middle row, within 60 ms, and passing every flick rule so far. Before that it ticks as a swipe, and a stroke that
 * stops being a flick breaks once and ticks as a swipe again. Words from P, K and L that go left never crunch at their start.
 */
private fun aClearFlickCrunchesAndAWordDoesNot() {
    // A real flick's pace: 3 keys from Backspace in 100 ms reaches 1.5 keys in 50.
    val (flick, flickBreaks) = crunch(stroke(.95f, .35f, .65f, 100))
    same(flick.first(), FlickCrunch.State.Waiting, "It ticks as a swipe before it is clear")
    same(flick.last(), FlickCrunch.State.Crunching, "then crunches")
    same(flickBreaks, 0, "and never breaks")
    // A word from L: it starts in the zone but takes 150 ms to go 1.5 keys.
    val (word, wordBreaks) = crunch(stroke(.85f, .4f, .2f, 650))
    isTrue(FlickCrunch.State.Crunching !in word, "A word from L never crunches: ${word.toSet()}")
    same(wordBreaks, 0, "nor breaks")
    // A word from K that goes left as fast as a flick, then turns back: it breaks once, then ticks as a swipe.
    val turn = stroke(.8f, .4f, .62f, 60) + stroke(.62f, .4f, .9f, 120, from = 1_060).drop(1)
    val (k, kBreaks) = crunch(turn)
    isTrue(FlickCrunch.State.Crunching in k, "As fast as a flick, it crunches at first")
    same(kBreaks, 1, "Turning back, it breaks once")
    same(k.last(), FlickCrunch.State.Broken, "then ticks as a swipe")
    // A word from P, in the top row, as fast as a flick: never crunches.
    val (p, _) = crunch(stroke(.92f, .19f, .5f, 120))
    isTrue(FlickCrunch.State.Crunching !in p, "A start on P never crunches: ${p.toSet()}")
    // A fast flick that goes on too far, past 5.5 keys, stops being a flick: one break.
    val (far, farBreaks) = crunch(stroke(.95f, .35f, .2f, 250))
    isTrue(FlickCrunch.State.Crunching in far, "A long fast stroke crunches at first")
    same(farBreaks, 1, "then breaks once as it goes too far")
    same(far.last(), FlickCrunch.State.Broken, "and ticks as a swipe after")
    // A flick that then rests on the keys, for a hold, keeps crunching: resting is no reason to break.
    val rest = stroke(.95f, .35f, .65f, 100) + (1..40).map { at(.65f, .35f, 1_100L + it * 10) }
    val (held, heldBreaks) = crunch(rest)
    same(held.last(), FlickCrunch.State.Crunching, "A flick that rests keeps crunching")
    same(heldBreaks, 0, "with no break")
    // Where a flick would not count, nothing crunches.
    same(crunch(stroke(.95f, .35f, .65f, 100), allowed = false).first.toSet(), setOf(FlickCrunch.State.Plain), "Not allowed: plain")
    // Outside the delete zone, plain at once.
    same(crunch(stroke(.5f, .35f, .2f, 100)).first.first(), FlickCrunch.State.Plain, "From the middle of the keys: plain")
}

/** The text a word delete leaves, as [wordDeleteLength] has it, with the cursor at "|". */
private fun deleted(text: String): String {
    val cursor = text.indexOf('|')
    val before = text.substring(0, cursor)
    val after = text.substring(cursor + 1)
    return before.dropLast(wordDeleteLength(before, after)) + "|" + after
}

/**
 * A flick, and each word of a hold, takes the word before the cursor; when that would leave two spaces, the space before the
 * word goes too (owner, 2026-11-12). Nowhere else does a space go.
 */
private fun aWordDeleteLeavesNoTwoSpaces() {
    same(deleted("one two| three"), "one| three", "Mid-text: one space is left, not two")
    same(deleted("one two three| four"), "one two| four", "Further along too")
    // Each word of a hold, one after another.
    same(deleted(deleted("one two three| four")), "one| four", "A hold's second word, the same")
    same(deleted("one two|"), "one |", "At the end of the text the space before stays, for the next word")
    same(deleted("two| three"), "| three", "At the start of the text there is no space before to take")
    same(deleted("one two|, three"), "one |, three", "Before punctuation nothing more goes")
    same(deleted("one two|. Three"), "one |. Three", "Nor before a full stop")
    same(deleted("one\ntwo| three"), "one\n| three", "A line break is no space, and stays")
    same(deleted("one two |three"), "one |three", "From after a space: the word and its space, no more")
    same(deleted("one  two| three"), "one | three", "Only one space more goes")
    same(deleted("one two|"), "one |", "Nothing after the cursor, nothing more")
    same(wordDeleteLength("one two", null), 3, "A field that doesn't give the text after: the word alone")
    same(deleted("|three"), "|three", "Nothing before the cursor, nothing goes")
}

/**
 * Every delete flick shows its glow at the lift (owner, 2026-11-12), even one so fast that it never got clear while it went:
 * [FlickCrunch.lifted] says so. A stroke that broke, a word, or a flick that doesn't count shows none.
 */
private fun everyFlickGlowsAtItsLift() {
    // The points of the lift itself come with the lift, so the stroke never moves on them: [moved] of them come before it.
    fun lifted(points: List<Sample>, allowed: Boolean = true, moved: Int = points.size): Boolean {
        val crunch = FlickCrunch()
        crunch.begin(allowed)
        for (index in 0 until moved) crunch.move(points.subList(0, index + 1))
        return crunch.lifted(points)
    }
    // Over in 30 ms: by its last move it has gone only one key, too little to be clear, and the rest comes with the lift.
    val fast = listOf(at(.95f, .35f, 1_000), at(.85f, .35f, 1_015), at(.65f, .35f, 1_030))
    same(crunch(fast.take(2)).first.last(), FlickCrunch.State.Waiting, "A flick this fast never got clear while it went")
    same(lifted(fast, moved = 2), true, "but its glow shows at the lift")
    same(lifted(stroke(.95f, .35f, .65f, 100)), true, "A usual flick glows at its lift too")
    same(lifted(fast, allowed = false, moved = 2), false, "Where a flick doesn't count, no glow")
    same(lifted(stroke(.95f, .35f, .2f, 250)), false, "A stroke that went too far and broke: no glow")
    same(lifted(stroke(.85f, .4f, .2f, 650)), false, "A word: no glow")
    same(lifted(stroke(.5f, .35f, .2f, 100)), false, "From the middle of the keys: no glow")
}

/**
 * A held delete never runs on after the lift (owner, 2026-11-12: the guide's hold took a word 1.1 s after it). Only one edit is
 * on its way at a time, so a word waiting for a busy edit when the finger lifts never goes, however long the edit takes.
 */
private fun aBusyHoldAddsNothingAfterTheLift() {
    val rig = Rig()
    rig.flick(1_000)
    val first = rig.next!!
    rig.runTo(first)
    val deleted = rig.words.size
    val due = rig.next!!
    // The next word is due, but the last edit is still going through, so it waits.
    rig.next = rig.hold.step(due, false) { rig.words += due }
    same(rig.words.size, deleted, "A busy edit holds the next word")
    same(rig.contact.up(emptyList(), due + 10), null, "The finger lifts")
    // The edit ends long after the lift; the timer looks again, and finds the hold over.
    for (late in listOf(due + 30, due + 400, due + 1_100)) {
        same(rig.hold.step(late, true) { rig.words += late }, null, "At ${late - due} ms after the lift the hold is over")
    }
    same(rig.words.size, deleted, "No word goes after the lift")
}

/**
 * A stroke that becomes a flick never draws a swipe's trail first (owner, 2026-11-12: some fast flicks showed a trail for a
 * frame or two). The trail waits while [FlickCrunch.mayBeFlick]; a word from the delete zone shows it once it is clearly no flick.
 */
private fun aFlickNeverShowsAsASwipeFirst() {
    fun mayBe(points: List<Sample>, allowed: Boolean = true): List<Boolean> {
        val crunch = FlickCrunch()
        crunch.begin(allowed)
        return points.indices.map { crunch.move(points.subList(0, it + 1)); crunch.mayBeFlick }
    }
    val flick = mayBe(stroke(.95f, .35f, .65f, 100))
    isTrue(flick.all { it }, "A flick may be one at every point, so its trail never shows: $flick")
    val fast = mayBe(listOf(at(.95f, .35f, 1_000), at(.85f, .35f, 1_015), at(.65f, .35f, 1_030)))
    isTrue(fast.all { it }, "A very fast one too: $fast")
    val word = mayBe(stroke(.85f, .4f, .2f, 650))
    isTrue(word.first() && !word.last(), "A word from L holds its trail only until it is clearly no flick: $word")
    isTrue(mayBe(stroke(.5f, .35f, .2f, 300)).none { it }, "A word from the middle of the keys shows its trail at once")
    isTrue(mayBe(stroke(.95f, .35f, .65f, 100), allowed = false).none { it }, "Where a flick doesn't count, the trail shows at once")
    val far = mayBe(stroke(.95f, .35f, .2f, 250))
    isTrue(!far.last(), "A stroke that broke off being a flick shows its trail")
}
