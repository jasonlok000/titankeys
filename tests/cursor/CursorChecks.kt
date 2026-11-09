package com.atlas.keyboard

import android.view.KeyEvent
import kotlin.math.abs

private const val KEY = KeyEvent.KEYCODE_A
private const val SPACE = KeyEvent.KEYCODE_SPACE
/** The middle of Space, in key widths and rows from the top left of the keyboard. */
private const val COLUMN = 5.3f
private const val ROW = 3.4f
private const val FIRST_MILLIS = 60L
private const val SECOND_MILLIS = 90L
private const val GAP_MILLIS = 80L

fun main() {
    twoQuickTapsSelect()
    worksAnywhereOnTheRow()
    selectingNeedsTheConfirmWait()
    dragStartsFromWhereTheSecondTouchLanded()
    spaceIsADeadZone()
    directionComesFromTheEndsOfSpace()
    speedIsTheSameOnBothSides()
    speedIsSlowNextToSpace()
    holdingAtTheEdgeBuildsUp()
    slipCarriesOnFromWhereItLands()
    slipCarriesOnAnywhereInTime()
    runningOffTheEndCarriesOn()
    runningOffTheEndMustComeBackNearby()
    runningOffTheEndMustComeBackInTime()
    awayFromTheEndsOnlyASlipCounts()
    dropoutsFromTheLogResume()
    wordDragRunsOffTheEndToo()
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
    secondFingerJumpDoesNothing()
    keyDuringTheSecondTouchCallsItOff()
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
    wordSelectionTakesATrailingSpace()
    wordSelectionFindsTheWord()
    wordSelectionDoesNothingBetweenWords()
    extendGrowsWordByWord()
    extendGivesBackBeforeGrowingTheOtherWay()
    extendSkipsPunctuation()
    extendKeepsATrailingSpaceAnchor()
    extendStartsFromACursorBetweenWords()
    thirdTouchDragSelectsWords()
    thirdTouchDragStartsFromWhereItLanded()
    secondTouchDragIsNotAWordDrag()
    wordDragUsesTheSameZones()
    wordSpeedStaysCountable()
    tripleTapWithoutADragDoesNothing()
    thirdTouchThatIsHeldDoesNothing()
    thirdTouchTimingMustMatch()
    thirdTouchMustBeClose()
    slowSecondTapHasNoThirdTouch()
    thirdTouchSwipeStaysAWord()
    thirdTouchJumpDoesNothing()
    thirdTouchThatIsTooDiagonalDoesNothing()
    keyDuringTheThirdTouchCallsItOff()
    restingFingerSeriesNeverDrags()
    typingNeverStartsAWordDrag()
    tapKeyWaitsForTheThirdTouch()
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
    /** Whether the drag that started selects words and doesn't move the cursor. */
    var words = false
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
            started = it.landed
            words = it.words
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

    /** Two quick taps, then a third touch landing at [column] after [gap]. Returns when it landed. */
    fun twoTapsThenLand(column: Float = COLUMN, gap: Long = GAP_MILLIS): Long {
        val second = tapThenLand()
        up(second + SECOND_MILLIS)
        val landed = second + SECOND_MILLIS + gap
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

/** Space runs from column 3.6 to column 6.4. */
private fun spaceIsADeadZone() {
    for (landing in listOf(1f, 3.8f, 5f, 6.2f, 9f)) for (position in listOf(3.65f, 4.2f, 5f, 5.9f, 6.35f)) {
        val cursor = cursorFrom(landing, position)
        same(cursor.active, true, "The cursor must be active")
        same(cursor.direction(), 0, "At column $position on Space the cursor must not move, landing at $landing")
        same(cursor.speed(100), 0f, "At column $position on Space the speed must be zero, landing at $landing")
    }
}

private fun directionComesFromTheEndsOfSpace() {
    same(cursorFrom(5f, 3.5f).direction(), -1, "Left of Space must move the cursor left")
    same(cursorFrom(5f, 6.5f).direction(), 1, "Right of Space must move the cursor right")
    // The finger moved towards Space from where it landed, and is still on the same side of Space.
    same(cursorFrom(1f, 3f).direction(), -1, "Left of Space must move left, also if the finger went right from where it landed")
    same(cursorFrom(9f, 7f).direction(), 1, "Right of Space must move right, also if the finger went left from where it landed")
    same(cursorFrom(2f, 8f).direction(), 1, "The side of Space decides, wherever the finger landed")
    same(cursorFrom(8f, 2f).direction(), -1, "The side of Space decides, wherever the finger landed")
}

private fun speedIsTheSameOnBothSides() {
    for (lean in listOf(.1f, .7f, 1.4f, 2.3f, 3.1f)) {
        val left = cursorFrom(5f, 3.6f - lean).speed(100)
        close(left, cursorFrom(5f, 6.4f + lean).speed(100), "Leaning $lean past either end of Space must give the same speed")
        close(left, cursorFrom(2f, 3.6f - lean).speed(100), "The same position must give the same speed wherever the finger landed")
    }
}

private fun speedIsSlowNextToSpace() {
    val leans = listOf(.05f, .4f, .8f, 1.2f, 1.6f, 2.3f, 2.8f)
    val speeds = leans.map { cursorFrom(5f, 3.6f - it).speed(100) }
    same(speeds[0] <= 1.6f, true, "Just past Space the cursor must creep, got ${speeds[0]}")
    same(speeds[2] <= 3.1f, true, "Most of the key beside Space must be slow, got ${speeds[2]}")
    for (i in 1 until speeds.size) same(speeds[i] > speeds[i - 1], true, "Each lean must be quicker than the one before: $speeds")
    same(speeds.last() > speeds[0] * 10, true, "Near the edge must be far quicker than next to Space: $speeds")
}

/** Real drags reached column 0.65 on the left and column 9.4 on the right, so full speed must be reachable there. */
private fun holdingAtTheEdgeBuildsUp() {
    for (edge in listOf(.65f, 9.4f)) same(cursorFrom(5f, edge).speed(1_000) >= 27.9f, true, "Column $edge must reach the top speed")
    val cursor = cursorFrom(5f, .65f)
    val start = cursor.speed(1_000)
    close(start, 28f, "Reaching the edge must start at the top of the curve")
    same(cursor.speed(1_500) > start, true, "Holding at the edge must build up")
    close(cursor.speed(2_000), 52f, "Holding at the edge must reach full speed")
    cursor.move(listOf(at(5f, 2_100)))
    same(cursor.speed(2_100), 0f, "Back on Space the speed must be zero")
    close(cursorFrom(5f, .65f).speed(5_000), 28f, "Coming back to the edge must start the build-up again")
}

private fun slipCarriesOnFromWhereItLands() {
    val cursor = cursorFrom(5f, 2f)
    same(cursor.direction(), -1, "The finger is left of Space")
    cursor.lift()
    same(cursor.direction(), 0, "A lifted finger must not move the cursor")
    same(cursor.resume(at(8f, 200)), true, "A finger that comes back must carry on")
    same(cursor.direction(), 1, "After a slip, the position where it lands must decide")
}

private const val SLIP = 90L
private const val END_ROW = 3.5f

/** A cursor whose finger leaned to [column] and lifted at [time]. */
private fun liftedAt(column: Float, time: Long) = CursorMode().apply {
    start(at(5f, 0))
    move(listOf(at(column, time, END_ROW)))
    lift()
}

private fun slipCarriesOnAnywhereInTime() {
    val cursor = liftedAt(2f, 1_000)
    same(cursor.holdMillis(SLIP), SLIP, "Away from the ends of the touchpad, the wait must be the slip recovery period")
    same(cursor.resume(at(7f, 1_080, END_ROW), SLIP), true, "A finger within the slip recovery period must carry on anywhere")
    same(cursor.direction(), 1, "The place where it lands must decide")
}

private fun runningOffTheEndCarriesOn() {
    for (edge in listOf(9.4f, .65f)) {
        val cursor = liftedAt(edge, 1_000)
        same(cursor.holdMillis(SLIP), 450L, "At column $edge the keyboard must wait for the finger to come back")
        val landed = edge + if (edge > 5) -.3f else .3f
        same(cursor.resume(at(landed, 1_350, END_ROW), SLIP), true, "A finger that runs off column $edge and comes back near it must carry on")
        same(cursor.active, true, "The cursor must keep following it")
        same(cursor.direction(), if (edge > 5) 1 else -1, "It must carry on in the same direction")
    }
}

private fun runningOffTheEndMustComeBackNearby() {
    val cursor = liftedAt(9.4f, 1_000)
    // The touchpad reports one point, so a second finger shows up as a touch somewhere else while the first has lifted.
    same(cursor.resume(at(5.5f, 1_250, END_ROW), SLIP), false, "A touch far away must be another finger or the next gesture, not the same drag")
    same(cursor.active, false, "The drag must not carry on for a second finger")
    val second = liftedAt(9.4f, 1_000)
    same(second.resume(at(7.9f, 1_200, END_ROW), SLIP), false, "A touch more than a key away must be a new touch")
    val near = liftedAt(9.4f, 1_000)
    same(near.resume(at(8.5f, 1_200, END_ROW), SLIP), true, "A touch within a key must carry on")
    val row = liftedAt(9.4f, 1_000)
    same(row.resume(at(9.3f, 1_200, 2.3f), SLIP), false, "A touch on another row must be a new touch")
}

private fun runningOffTheEndMustComeBackInTime() {
    same(liftedAt(9.4f, 1_000).resume(at(9.3f, 1_450, END_ROW), SLIP), true, "A finger back within the wait must carry on")
    same(liftedAt(9.4f, 1_000).resume(at(9.3f, 1_451, END_ROW), SLIP), false, "A finger back after the wait must be a new touch")
}

private fun awayFromTheEndsOnlyASlipCounts() {
    val cursor = liftedAt(8f, 1_000)
    same(cursor.holdMillis(SLIP), SLIP, "Away from the ends the wait must be only the slip recovery period")
    same(cursor.resume(at(8.1f, 1_200, END_ROW), SLIP), false, "After the slip recovery period, a touch away from the ends must be a new touch")
}

/** The gaps and return points of the three real drags that ran off the end of the touchpad and came back. */
private fun dropoutsFromTheLogResume() {
    for ((gap, landed, row) in listOf(Triple(314L, 9.21f, .77f * 4), Triple(323L, 9.27f, .82f * 4), Triple(390L, 9.35f, .81f * 4))) {
        val cursor = liftedAt(9.39f, 1_000)
        same(cursor.resume(at(landed, 1_000 + gap, row), SLIP), true, "A real drop of $gap ms must carry on")
    }
}

private fun wordDragRunsOffTheEndToo() {
    val cursor = CursorMode().apply {
        start(at(5f, 0), words = true)
        move(listOf(at(9.4f, 1_000, END_ROW)))
        lift()
    }
    same(cursor.resume(at(9.3f, 1_300, END_ROW), SLIP), true, "A word drag must carry on after it runs off the end")
    same(cursor.words, true, "It must stay a word drag")
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

private fun secondFingerJumpDoesNothing() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    // The touchpad reports one point, which flips to where a second finger lands.
    pad.move(at(COLUMN + 3f, landed + 10))
    same(pad.started, null, "A sudden jump is a second finger, so it must not start the cursor")
    pad.move(at(COLUMN + 3.3f, landed + 20))
    same(pad.started, null, "A touch that jumped must not start the cursor afterwards")
}

private fun keyDuringTheSecondTouchCallsItOff() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.key(SPACE, landed + 40)
    pad.move(at(COLUMN + 1f, landed + 100))
    same(pad.started, null, "A key press during the second touch must call off the drag")
    pad.up(landed + 120)
    same(pad.selects(), false, "A key press during the second touch must call off the double tap")
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

private fun range(text: String, start: Int, end: Int = start) = WordSelection.at(text, start, end)

/** The text between a selection's two ends, for messages that are easy to read. */
private fun picked(text: String, selection: Pair<Int, Int>?) = selection?.let { text.substring(it.first, it.second) }

private fun wordSelectionTakesATrailingSpace() {
    same(picked("testing ", range("testing ", 8)), "testing ", "A cursor after one space must take the word and the space")
    same(picked("one testing ", range("one testing ", 12)), "testing ", "Only the last word and its space must be taken")
    same(picked("testing  more", range("testing  more", 8)), "testing ", "Text after the cursor must not change this")
}

private fun wordSelectionFindsTheWord() {
    same(picked("testing", range("testing", 3)), "testing", "Inside a word must take the word")
    same(picked("testing", range("testing", 7)), "testing", "Just after a word must take the word")
    same(picked("testing", range("testing", 0)), "testing", "Just before a word must take the word")
    same(picked("one two", range("one two", 4)), "two", "A cursor that touches a word must take that word, not the one before the space")
    same(picked("don't stop", range("don't stop", 2)), "don't", "An apostrophe must stay inside the word")
    same(picked("one two", range("one two", 1, 2)), "one", "A selection must grow to the whole word")
}

private fun wordSelectionDoesNothingBetweenWords() {
    same(range("", 0), null, "Empty text has no word")
    same(range(" ", 1), null, "A space with no word before it has no word")
    same(range("testing  ", 9), null, "After two spaces there is no word at the cursor")
    same(range("testing, ", 9), null, "A space after punctuation has no word at the cursor")
    same(range("testing\n", 8), null, "The next line has no word at the cursor")
}

private fun extendGrowsWordByWord() {
    val text = "one two three four"
    var selection = 4 to 7
    fun step(direction: Int) { selection = WordSelection.extend(text, selection.first, selection.second, 4, 7, direction) }
    step(1); same(picked(text, selection), "two three", "Right must take the next word")
    step(1); same(picked(text, selection), "two three four", "Right again must take one more")
    step(1); same(picked(text, selection), "two three four", "Right at the end must not change it")
}

private fun extendGivesBackBeforeGrowingTheOtherWay() {
    val text = "one two three four"
    var selection = 4 to 18
    fun step(direction: Int) { selection = WordSelection.extend(text, selection.first, selection.second, 4, 7, direction) }
    step(-1); same(picked(text, selection), "two three", "Left must give back the last word first")
    step(-1); same(picked(text, selection), "two", "Left must give back down to the first word")
    step(-1); same(picked(text, selection), "one two", "Left must then grow to the left")
    step(-1); same(picked(text, selection), "one two", "Left at the start must not change it")
    step(1); same(picked(text, selection), "two", "Right must give back the word on the left first")
    step(1); same(picked(text, selection), "two three", "Right must then grow to the right")
}

private fun extendSkipsPunctuation() {
    val text = "Hello, world. Next"
    same(picked(text, WordSelection.extend(text, 0, 5, 0, 5, 1)), "Hello, world", "Punctuation between words must be crossed")
    same(picked(text, WordSelection.extend(text, 0, 12, 0, 5, 1)), "Hello, world. Next", "Each step must end at the end of a word")
    same(picked(text, WordSelection.extend(text, 7, 12, 7, 12, -1)), "Hello, world", "Going left must end at the start of a word")
    val last = "Hello."
    same(WordSelection.extend(last, 0, 5, 0, 5, 1), 0 to 5, "Punctuation with no word after it must not be taken")
}

private fun extendKeepsATrailingSpaceAnchor() {
    val text = "one two three"
    var selection = 4 to 8
    fun step(direction: Int) { selection = WordSelection.extend(text, selection.first, selection.second, 4, 8, direction) }
    step(1); same(picked(text, selection), "two three", "Right must take the next word")
    step(-1); same(picked(text, selection), "two ", "Giving back must stop at the word and its space")
    step(-1); same(picked(text, selection), "one two ", "Left must grow to the left and keep the space")
}

private fun extendStartsFromACursorBetweenWords() {
    val text = "one  two"
    same(picked(text, WordSelection.extend(text, 4, 4, 4, 4, 1)), " two", "A word drag between words must take the next word")
    same(picked(text, WordSelection.extend(text, 4, 4, 4, 4, -1)), "one ", "A word drag between words must take the word before")
}

private fun thirdTouchDragSelectsWords() {
    for (way in listOf(-1f, 1f)) {
        val pad = Pad()
        val landed = pad.twoTapsThenLand()
        pad.move(at(COLUMN + .3f * way, landed + 100))
        same(pad.started, null, "A short movement must not start a word drag")
        pad.move(at(COLUMN + .6f * way, landed + 200))
        same(pad.started != null, true, "A third touch dragged sideways must start a word drag, way $way")
        same(pad.words, true, "A third touch dragged sideways must select words, way $way")
        same(pad.selects(), false, "A word drag must not also select one word by double tap")
    }
}

private fun thirdTouchDragStartsFromWhereItLanded() {
    val pad = Pad()
    val landed = pad.twoTapsThenLand(6f)
    pad.move(at(6.6f, landed + 200))
    same(pad.started, at(6f, landed), "The drag must start from where the third touch landed")
}

private fun secondTouchDragIsNotAWordDrag() {
    val pad = Pad()
    val landed = pad.tapThenLand()
    pad.move(at(COLUMN + 1f, landed + 200))
    same(pad.started != null, true, "A second touch dragged sideways must start the cursor")
    same(pad.words, false, "A second touch dragged sideways must move the cursor, not select words")
}

private fun wordCursor(position: Float) = CursorMode().apply {
    start(at(5f, 0), words = true)
    move(listOf(at(position, 100)))
}

private fun wordDragUsesTheSameZones() {
    same(wordCursor(5.9f).words, true, "A word drag must say so")
    same(wordCursor(5.9f).direction(), 0, "On Space a word drag must not extend")
    same(wordCursor(5.9f).speed(100), 0f, "On Space a word drag must not extend")
    same(wordCursor(3f).direction(), -1, "Left of Space must extend to the left")
    same(wordCursor(7f).direction(), 1, "Right of Space must extend to the right")
    val cursor = wordCursor(2f)
    cursor.lift()
    same(cursor.resume(at(8f, 200)), true, "A word drag must survive a slip")
    same(cursor.words, true, "A word drag must stay a word drag after a slip")
    cursor.end()
    same(cursor.words, false, "Ending must clear the word drag")
}

private fun wordSpeedStaysCountable() {
    val near = wordCursor(3.55f).speed(100)
    same(near in 1f..1.5f, true, "Next to Space about one word a second must be selected, got $near")
    val far = wordCursor(.65f).speed(100)
    same(far in 4f..6f, true, "Near the edge a word drag must be quicker but countable, got $far")
    same(far < cursorFrom(5f, .65f).speed(100), true, "A word drag must take fewer steps than letters")
}

private fun tripleTapWithoutADragDoesNothing() {
    val pad = Pad()
    val landed = pad.twoTapsThenLand()
    same(pad.gesture.confirm(), false, "A third touch must call off the waiting double tap")
    pad.up(landed + 60)
    same(pad.tapped, false, "A third tap must not finish a double tap")
    same(pad.gesture.confirm(), false, "A triple tap with no drag must not select")
    same(pad.started, null, "A triple tap with no drag must not start a drag")
    same(pad.gesture.deciding(landed + 61), false, "A triple tap with no drag must free the tap key")
    pad.tap(COLUMN, landed + 60 + 80, FIRST_MILLIS)
    pad.tap(COLUMN, landed + 60 + 80 + FIRST_MILLIS + GAP_MILLIS, 90)
    same(pad.selects(), false, "A tap straight after a triple tap must not start another gesture")
}

private fun thirdTouchThatIsHeldDoesNothing() {
    val pad = Pad()
    val landed = pad.twoTapsThenLand()
    pad.move(at(COLUMN + .1f, landed + 800))
    pad.up(landed + 2_000)
    same(pad.started, null, "A third touch that is held must not start a drag")
    same(pad.selects(), false, "A third touch that is held must not select")
}

private fun thirdTouchTimingMustMatch() {
    val late = Pad()
    val lateLanded = late.twoTapsThenLand(gap = 151)
    late.move(at(COLUMN + 1f, lateLanded + 200))
    same(late.started, null, "A third touch after the longest gap must not start a word drag")
    val soon = Pad()
    val soonLanded = soon.twoTapsThenLand(gap = 20)
    soon.move(at(COLUMN + 1f, soonLanded + 200))
    same(soon.started, null, "A third touch this soon is the finger flickering")
    val edge = Pad()
    val edgeLanded = edge.twoTapsThenLand(gap = 150)
    edge.move(at(COLUMN + 1f, edgeLanded + 200))
    same(edge.words, true, "A third touch at the longest gap must count")
}

private fun thirdTouchMustBeClose() {
    val far = Pad()
    val farLanded = far.twoTapsThenLand(COLUMN + 1.1f)
    far.move(at(COLUMN + 2f, farLanded + 200))
    same(far.started, null, "A third touch more than a key away must not count")
    val near = Pad()
    val nearLanded = near.twoTapsThenLand(COLUMN + .9f)
    near.move(at(COLUMN + 1.6f, nearLanded + 200))
    same(near.words, true, "A third touch within a key must count")
}

private fun slowSecondTapHasNoThirdTouch() {
    val pad = Pad()
    val second = pad.tapThenLand()
    pad.up(second + 201)
    val landed = second + 201 + GAP_MILLIS
    pad.down(at(COLUMN, landed))
    pad.move(at(COLUMN + 1f, landed + 200))
    same(pad.started, null, "After a slow second tap a third touch must not start a word drag")
}

private fun thirdTouchSwipeStaysAWord() {
    val pad = Pad()
    val landed = pad.twoTapsThenLand()
    pad.move(at(COLUMN + .2f, landed + 20, ROW - .5f))
    pad.move(at(COLUMN + .9f, landed + 40, ROW - 1f), at(COLUMN + 1.4f, landed + 60, ROW - 2f))
    same(pad.started, null, "A swipe up into the letters must not start a word drag")
    val outcome = pad.up(landed + 120)
    same(outcome is Contact.Outcome.Swipe, true, "A swipe up into the letters must stay a word swipe")
}

private fun thirdTouchJumpDoesNothing() {
    val pad = Pad()
    val landed = pad.twoTapsThenLand()
    pad.move(at(COLUMN + 3f, landed + 10))
    pad.move(at(COLUMN + 3.3f, landed + 20))
    same(pad.started, null, "A sudden jump is a second finger, so it must not start a word drag")
}

private fun thirdTouchThatIsTooDiagonalDoesNothing() {
    val pad = Pad()
    val landed = pad.twoTapsThenLand()
    pad.move(at(COLUMN + .55f, landed + 100, ROW - .35f))
    same(pad.started, null, "A drag that isn't mostly sideways must not start a word drag")
}

private fun keyDuringTheThirdTouchCallsItOff() {
    val pad = Pad()
    val landed = pad.twoTapsThenLand()
    pad.key(SPACE, landed + 40)
    pad.move(at(COLUMN + 1f, landed + 100))
    same(pad.started, null, "A key press during the third touch must call off the word drag")
}

private fun restingFingerSeriesNeverDrags() {
    val pad = Pad()
    var time = 1_000L
    // Short touches at nearly the same place, each with a small wobble, for longer than any gesture lasts.
    repeat(8) { round ->
        pad.down(at(COLUMN, time))
        pad.move(at(COLUMN + (if (round % 2 == 0) .15f else -.15f), time + 20))
        pad.up(time + 40)
        time += 40 + 60
    }
    same(pad.started, null, "A resting finger's series must not start a drag")
    same(pad.gesture.confirm(), false, "A resting finger's series must not select")
}

private fun typingNeverStartsAWordDrag() {
    val pad = Pad()
    pad.key(KEY, 1_000)
    pad.key(KEY, 1_250)
    pad.clock = 1_400
    val landed = pad.twoTapsThenLand()
    pad.move(at(COLUMN + 1f, landed + 200))
    same(pad.started, null, "Three touches in the pause after typing must not start a word drag")
    val pushed = Pad()
    // A push reports a lift, and each press calls the gesture off.
    pushed.down(at(COLUMN, 1_000))
    pushed.key(SPACE, 1_040)
    pushed.up(1_045)
    pushed.down(at(COLUMN, 1_160))
    pushed.key(SPACE, 1_190)
    pushed.up(1_195)
    pushed.down(at(COLUMN, 1_280))
    pushed.move(at(COLUMN + 1f, 1_380))
    same(pushed.started, null, "Pushing Space three times must only type")
}

private fun tapKeyWaitsForTheThirdTouch() {
    val pad = Pad()
    val landed = pad.twoTapsThenLand()
    same(pad.gesture.deciding(landed + 5_000), true, "A tap must wait while the third touch is undecided")
    pad.up(landed + 5_000)
    same(pad.gesture.deciding(landed + 5_001), false, "A held third touch that did nothing must free the tap")
}
