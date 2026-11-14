package com.atlas.keyboard

import android.view.KeyEvent
import kotlin.math.abs

private const val KEY = KeyEvent.KEYCODE_A
private const val SPACE = KeyEvent.KEYCODE_SPACE
/** The middle of Space, in key widths and rows from the top left of the keyboard. */
private const val COLUMN = 5.3f
private const val ROW = 3.4f
private const val FIRST_MILLIS = 60L
private const val GAP_MILLIS = 80L

fun main() {
    twoQuickTapsSelect()
    worksAnywhereOnTheRow()
    selectingNeedsTheConfirmWait()
    dragStartsFromWhereTheSecondTouchLanded()
    speedIsMeasuredFromTheLandingSpot()
    stillSpotStopsTheCursor()
    dragCanStartLate()
    singleTapDoesNothing()
    slowFirstTouchDoesNothing()
    firstTouchThatMovesDoesNothing()
    tooLongAGapDoesNothing()
    tooShortAGapDoesNothing()
    tooFarApartDoesNothing()
    heldSecondTouchDoesNothing()
    slowSecondTapDoesNothing()
    swipeUpFromTheBottomRowStaysAWord()
    diagonalDragDoesNothing()
    pathThatLeftTheRowDoesNothing()
    otherRowsDoNothing()
    secondTouchOnAnotherRowDoesNothing()
    typingDoesNothing()
    touchSoonAfterAKeyDoesNothing()
    keyBetweenTheTapsCallsItOff()
    keyAfterTheTapsCallsItOff()
    pushingSpaceTwiceDoesNothing()
    thirdTouchCallsOffTheDoubleTap()
    restingFingerSeriesDoesNothing()
    doubleTapCountsOnce()
    tapKeyWaitsOutTheWindow()
    tapKeyWaitsForTheSecondTouch()
    tapKeyIsFreedByAWordSwipe()
    repeatedDragsKeepWorking()
    println("Cursor checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

/** For speeds, where the same lean from another landing spot differs only by float rounding. */
private fun close(actual: Float, expected: Float, message: String) {
    if (abs(actual - expected) > .001f) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private fun at(column: Float, time: Long, row: Float = ROW) = Sample(column / 10, row / 4, time)

/** Feeds a [Contact] and a [CursorGesture] in the order that the keyboard does. */
private class Pad {
    val contact = Contact()
    val gesture = CursorGesture()
    /** Where the finger landed, once a drag has started the cursor. */
    var started: Sample? = null
    var tapped = false
    var clock = 1_000L

    fun down(sample: Sample) {
        contact.down(sample)
        gesture.down(sample, contact.travel != null)
        tapped = false
    }

    fun move(vararg samples: Sample) {
        val list = samples.toList()
        contact.move(list)
        gesture.move(list)?.let {
            started = it
            contact.cancelActive()
        }
    }

    /** The finger lifts at [time] and the wait for a key press that the lift hid has passed. */
    fun up(time: Long): Contact.Outcome? {
        tapped = gesture.up(emptyList(), time)
        val settlement = contact.up(emptyList(), time) ?: return null
        return contact.settle(settlement, settlement.deadline)
    }

    fun tap(column: Float, time: Long, lasts: Long, row: Float = ROW): Contact.Outcome? {
        down(at(column, time, row))
        return up(time + lasts)
    }

    /** A quick first tap, then a second touch landing at [column] after [GAP_MILLIS]. Returns when it landed. */
    fun tapThenLand(column: Float = COLUMN): Long {
        val start = clock
        tap(COLUMN, start, FIRST_MILLIS)
        val landed = start + FIRST_MILLIS + GAP_MILLIS
        down(at(column, landed))
        return landed
    }

    fun key(code: Int, time: Long, lasts: Long = 70) {
        contact.key(code, true, time)
        gesture.cancel(time)
        contact.key(code, false, time + lasts)
    }

    /** True if a double tap is waiting and still stands once the wait for a third touch has passed. */
    fun selects() = tapped && gesture.confirm()
}

private fun twoQuickTapsSelect() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.up(landed + 90)
    same(pad.selects(), true, "Two quick taps must select the word")
    same(pad.started, null, "Two quick taps must not start the cursor")
}

private fun worksAnywhereOnTheRow() {
    for (column in listOf(.4f, 2.5f, 5.3f, 8f, 9.6f)) {
        val pad = Pad()
        pad.tap(column, 1_000, FIRST_MILLIS)
        pad.tap(column, 1_140, 90)
        same(pad.selects(), true, "A double tap must count at column $column")
    }
}

private fun selectingNeedsTheConfirmWait() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.up(landed + 90)
    same(pad.tapped, true, "The second lift must finish the double tap")
    same(pad.gesture.deciding(landed + 90), true, "Until the wait is over, the double tap can still be called off")
}

private fun dragStartsFromWhereTheSecondTouchLanded() {
    val pad = Pad()
    val landed = pad.tapThenLand(6f)
    pad.move(at(6.3f, landed + 100))
    same(pad.started, null, "A short movement must not start the cursor")
    pad.move(at(6.6f, landed + 200))
    same(pad.started, at(6f, landed), "The cursor must start from where the second touch landed, not the first")
}

private fun cursorFrom(landing: Float, position: Float) = CursorMode().apply {
    start(at(landing, 0))
    move(listOf(at(position, 100)))
}

private fun speedIsMeasuredFromTheLandingSpot() {
    same(cursorFrom(5f, 6.2f).direction(), 1, "Moving right of the landing spot must move the cursor right")
    same(cursorFrom(5f, 3.8f).direction(), -1, "Moving left of the landing spot must move the cursor left")
    // This is the same position on the row, on opposite sides of each landing spot.
    same(cursorFrom(3f, 4f).direction(), 1, "A landing spot left of the finger must move the cursor right")
    same(cursorFrom(5f, 4f).direction(), -1, "A landing spot right of the finger must move the cursor left")
    close(cursorFrom(2f, 3.5f).speed(100), cursorFrom(7f, 8.5f).speed(100), "The same lean must give the same speed wherever the finger landed")
    close(cursorFrom(5f, 3.5f).speed(100), cursorFrom(5f, 6.5f).speed(100), "Leaning left or right the same amount must give the same speed")
    val slow = cursorFrom(5f, 5.6f).speed(100)
    val fast = cursorFrom(5f, 7f).speed(100)
    same(fast > slow * 3, true, "A longer lean must be clearly quicker")
}

private fun stillSpotStopsTheCursor() {
    val cursor = cursorFrom(5f, 5.2f)
    same(cursor.active, true, "The cursor must be active")
    same(cursor.direction(), 0, "Near the landing spot the cursor must not move")
    same(cursor.speed(100), 0f, "Near the landing spot the speed must be zero")
}

private fun dragCanStartLate() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.move(at(COLUMN + .1f, landed + 300))
    pad.move(at(COLUMN - .1f, landed + 900))
    same(pad.started, null, "Lingering must not start the cursor")
    pad.move(at(COLUMN + .6f, landed + 1_200))
    same(pad.started, at(COLUMN, landed), "A drag that begins after a pause must still start the cursor")
}

private fun singleTapDoesNothing() {
    val pad = Pad()
    pad.tap(COLUMN, 1_000, FIRST_MILLIS)
    same(pad.tapped, false, "One tap must not finish a double tap")
    same(pad.gesture.confirm(), false, "One tap must not select")
    same(pad.started, null, "One tap must not start the cursor")
}

private fun slowFirstTouchDoesNothing() {
    val pad = Pad()
    pad.tap(COLUMN, 1_000, 131)
    pad.down(at(COLUMN, 1_131 + GAP_MILLIS))
    pad.up(1_131 + GAP_MILLIS + 90)
    same(pad.selects(), false, "A slow first touch must not count")
}

private fun firstTouchThatMovesDoesNothing() {
    val pad = Pad()
    pad.down(at(COLUMN, 1_000))
    pad.move(at(COLUMN + .35f, 1_040))
    pad.up(1_060)
    pad.tap(COLUMN, 1_060 + GAP_MILLIS, 90)
    same(pad.selects(), false, "A first touch that moved is not a tap")
}

private fun tooLongAGapDoesNothing() {
    val pad = Pad()
    pad.tap(COLUMN, 1_000, FIRST_MILLIS)
    pad.tap(COLUMN, 1_000 + FIRST_MILLIS + 151, 90)
    same(pad.selects(), false, "A long gap must not count")
}

private fun tooShortAGapDoesNothing() {
    val pad = Pad()
    pad.tap(COLUMN, 1_000, FIRST_MILLIS)
    pad.tap(COLUMN, 1_000 + FIRST_MILLIS + 20, 90)
    same(pad.selects(), false, "A touch this soon is the finger flickering")
}

private fun tooFarApartDoesNothing() {
    val pad = Pad()
    val landed = pad.tapThenLand(COLUMN + 1.1f)
    pad.up(landed + 90)
    same(pad.selects(), false, "Taps more than a key apart must not count")
    val near = Pad()
    val close = near.tapThenLand(COLUMN + .9f)
    near.up(close + 90)
    same(near.selects(), true, "Taps within a key must count")
}

private fun heldSecondTouchDoesNothing() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.move(at(COLUMN + .1f, landed + 800))
    pad.up(landed + 2_000)
    same(pad.selects(), false, "A second touch that is held must not select")
    same(pad.started, null, "A second touch that is held must not start the cursor")
}

private fun slowSecondTapDoesNothing() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.up(landed + 201)
    same(pad.selects(), false, "A slow second tap must not select")
}

private fun swipeUpFromTheBottomRowStaysAWord() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.move(at(COLUMN + .2f, landed + 20, ROW - .5f))
    pad.move(at(COLUMN + .9f, landed + 40, ROW - 1f), at(COLUMN + 1.4f, landed + 60, ROW - 2f))
    same(pad.started, null, "A swipe up into the letters must not start the cursor")
    val outcome = pad.up(landed + 120)
    same(outcome is Contact.Outcome.Swipe, true, "A swipe up into the letters must stay a word swipe")
    same(pad.selects(), false, "A swipe up into the letters must not select")
}

private fun diagonalDragDoesNothing() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.move(at(COLUMN + .55f, landed + 100, ROW - .35f))
    same(pad.started, null, "A drag that isn't mostly sideways must not start the cursor")
}

private fun pathThatLeftTheRowDoesNothing() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.move(at(COLUMN, landed + 100, ROW - .45f))
    pad.move(at(COLUMN + 2f, landed + 300))
    same(pad.started, null, "A finger that has gone up must not start the cursor when it comes back")
}

private fun otherRowsDoNothing() {
    val pad = Pad()
    pad.tap(COLUMN, 1_000, FIRST_MILLIS, row = 2.5f)
    pad.tap(COLUMN, 1_140, 90, row = 2.5f)
    same(pad.selects(), false, "Double taps on the letters must not count")
}

private fun secondTouchOnAnotherRowDoesNothing() {
    val pad = Pad()
    pad.tap(COLUMN, 1_000, FIRST_MILLIS)
    pad.tap(COLUMN, 1_140, 90, row = 2.5f)
    same(pad.selects(), false, "Both taps must be on the bottom row")
}

private fun typingDoesNothing() {
    val pad = Pad()
    // The finger lands on Space between key presses while typing steadily.
    pad.key(KEY, 1_000)
    pad.key(KEY, 1_250)
    pad.tap(COLUMN, 1_400, FIRST_MILLIS)
    pad.tap(COLUMN, 1_400 + FIRST_MILLIS + GAP_MILLIS, 90)
    same(pad.selects(), false, "Taps in the pause after typing must not count")
    pad.key(KEY, 2_000)
    pad.key(KEY, 2_250)
    pad.tap(COLUMN, 2_300, FIRST_MILLIS)
    pad.down(at(COLUMN, 2_300 + FIRST_MILLIS + GAP_MILLIS))
    pad.move(at(COLUMN + 1f, 2_600))
    same(pad.started, null, "A drag in the pause after typing must not start the cursor")
}

private fun touchSoonAfterAKeyDoesNothing() {
    val pad = Pad()
    // Past the keyboard's own pause after one key, but still within the gesture's.
    pad.key(KEY, 1_000)
    pad.tap(COLUMN, 1_400, FIRST_MILLIS)
    pad.tap(COLUMN, 1_400 + FIRST_MILLIS + GAP_MILLIS, 90)
    same(pad.selects(), false, "A double tap so soon after a key press must not count")
    val later = Pad()
    later.key(KEY, 1_000)
    later.tap(COLUMN, 1_510, FIRST_MILLIS)
    later.tap(COLUMN, 1_510 + FIRST_MILLIS + GAP_MILLIS, 90)
    same(later.selects(), true, "A double tap after the pause must count")
}

private fun keyBetweenTheTapsCallsItOff() {
    val pad = Pad()
    pad.tap(COLUMN, 1_000, FIRST_MILLIS)
    pad.gesture.cancel(1_100)
    pad.tap(COLUMN, 1_140, 90)
    same(pad.selects(), false, "A key press between the taps must call it off")
}

private fun keyAfterTheTapsCallsItOff() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.up(landed + 90)
    pad.gesture.cancel(landed + 150)
    same(pad.selects(), false, "A key press just after the taps must call it off")
}

private fun pushingSpaceTwiceDoesNothing() {
    val pad = Pad()
    // A push reports a lift, and the key press can arrive before or after that lift.
    pad.down(at(COLUMN, 1_000))
    pad.key(SPACE, 1_040)
    pad.up(1_045)
    pad.down(at(COLUMN, 1_160))
    pad.up(1_190)
    pad.key(SPACE, 1_195)
    same(pad.selects(), false, "Pushing Space two times must only type, for the full stop")
    same(pad.started, null, "Pushing Space two times must not start the cursor")
}

private fun thirdTouchCallsOffTheDoubleTap() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.up(landed + 90)
    pad.down(at(COLUMN, landed + 150))
    same(pad.gesture.confirm(), false, "A third touch straight after must not leave a double tap")
    pad.up(landed + 200)
    same(pad.gesture.confirm(), false, "A third touch must not start a new double tap")
}

private fun restingFingerSeriesDoesNothing() {
    val pad = Pad()
    var time = 1_000L
    // A resting finger reported again and again as short touches at the same place. Each lands before the wait ends.
    repeat(6) {
        pad.tap(COLUMN, time, 40)
        time += 40 + 60
    }
    same(pad.gesture.confirm(), false, "A series of short touches must not select")
    // Two touches right after another touch look like a double tap, but the first of them didn't come alone.
    val late = Pad()
    late.tap(COLUMN, 1_000, 40)
    late.tap(COLUMN, 1_040 + 400, 40)
    late.tap(COLUMN, 1_480 + 60, 40)
    late.tap(COLUMN, 1_580 + 60, 40)
    same(late.tapped, false, "Three touches in a row must not end in a double tap")
}

private fun doubleTapCountsOnce() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.up(landed + 90)
    same(pad.selects(), true, "The double tap must select")
    same(pad.gesture.confirm(), false, "One double tap must select only once")
    // A tap straight after the selection is too soon to start another one.
    pad.tap(COLUMN, landed + 90 + 160, FIRST_MILLIS)
    pad.tap(COLUMN, landed + 90 + 160 + FIRST_MILLIS + GAP_MILLIS, 90)
    same(pad.selects(), false, "A tap straight after a double tap must not be the first tap of another")
}

private fun tapKeyWaitsOutTheWindow() {
    val pad = Pad()
    pad.tap(COLUMN, 1_000, FIRST_MILLIS)
    val lifted = 1_000 + FIRST_MILLIS
    same(pad.gesture.deciding(lifted + 30), true, "A tap must wait while a second touch can still land")
    same(pad.gesture.deciding(lifted + 150), false, "A tap must not wait after the gap has passed")
}

private fun tapKeyWaitsForTheSecondTouch() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    same(pad.gesture.deciding(landed + 5_000), true, "A tap must wait while the second touch is undecided")
    pad.up(landed + 5_000)
    same(pad.gesture.deciding(landed + 5_001), false, "A held second touch that did nothing must free the tap")
}

private fun tapKeyIsFreedByAWordSwipe() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.move(at(COLUMN, landed + 50, ROW - 1f))
    same(pad.gesture.deciding(landed + 50), false, "A second touch that became a word swipe must free the tap")
}

/** Real attempts started about 200 ms after the last drag lifted, so a finished drag must leave no pause of its own. */
private fun repeatedDragsKeepWorking() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.move(at(COLUMN + 1f, landed + 300))
    same(pad.started != null, true, "The first drag must start the cursor")
    val lifted = landed + 900
    pad.gesture.up(emptyList(), lifted)
    pad.started = null
    pad.clock = lifted + 210
    val again = pad.tapThenLand()
    pad.move(at(COLUMN + 1f, again + 300))
    same(pad.started != null, true, "A drag straight after another must start the cursor")
}
