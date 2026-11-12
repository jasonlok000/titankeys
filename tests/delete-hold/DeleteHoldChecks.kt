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
    moreLengthDoesNotPassTheLimits()
    theFirstWordIsTheFlickItself()
    liftingStopsAtOnceWithNoExtraWord()
    liftingBetweenWordsAddsNothing()
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
