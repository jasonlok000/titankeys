package com.atlas.keyboard

import android.view.KeyEvent

private const val KEY = KeyEvent.KEYCODE_A

private val holdMillis = deleteSwipeTuning["deleteHoldMillis"] as Long
private val slowMillis = deleteSwipeTuning["deleteRepeatSlowMillis"] as Long
private val fastMillis = deleteSwipeTuning["deleteRepeatFastMillis"] as Long

/** Where a flick from .95 stops, for a length of 1.8, 4.5, and 7 key widths. */
private const val SHORT_STOP = .77f
private const val MIDDLE_STOP = .5f
private const val LONG_STOP = .25f

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
    println("Delete hold checks passed")
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
        if (!contact.deleting) next = hold.moved(allowed)
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
    same(waitsOf(LONG_STOP).first(), fastMillis, "A flick of seven keys must wait the shortest")
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
    // A move after the words have started is ignored, so it must not change the speed.
    val moved = Rig()
    moved.flick(1_000, SHORT_STOP)
    moved.runTo(1_000 + 60 + holdMillis)
    moved.move(at(LONG_STOP, .35f, 1_000 + 60 + holdMillis + 10))
    moved.runTo(1_000 + 60 + holdMillis + 2_000)
    same(moved.words.zipWithNext { a, b -> b - a }.toSet(), setOf(slowMillis), "A move during the hold must not change the wait")
}

private fun moreLengthDoesNotPassTheLimits() {
    same(waitsOf(.15f).first(), fastMillis, "A flick past seven keys must not wait less than the shortest wait")
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
    // Slow travel that is still a clean flick. The finger is not holding, so the wait starts again at each step.
    var x = SHORT_STOP
    var time = 1_060L
    repeat(5) {
        x -= .05f
        time += 300
        rig.runTo(time - 1)
        rig.move(at(x, .35f, time))
    }
    same(rig.words.size, 0, "A finger that keeps moving must not repeat")
    same(rig.next, time + holdMillis, "The wait must count from the last move")
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
