package com.atlas.keyboard

import com.atlas.keyboard.GestureDisplay.Scene
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

private const val FRAME = 16L
/** Columns of the keyboard: one left of Space, the middle of Space, one right of Space, and the edge. */
private const val LEFT_OF_SPACE = 3f
private const val ON_SPACE = 5.3f
private const val RIGHT_OF_SPACE = 7.5f

fun main() {
    aFlicksWayInStretchesToFitItsDots()
    aHeldDeleteGlowFollowsItsPace()
    aFlicksGlowFollowsTheFinger()
    everyFlickShowsItsGlow()
    aHoldDrawsTheRealWords()
    aHoldWithNoTextLeftDrawsNoPills()
    aPausedHoldFadesAndBlinks()
    aHoldsDotsSpeedUpAsFarAsItsWordsDid()
    aHeldTripleGlowsEveryTime()
    theTripleGlowFollowsTheHand()
    aTriplePickShowsItsSelectionAtOnce()
    aTriplePickWaitsForTheLift()
    aHoldsDotsNeverLieOverTheWords()
    anyNumberOfWordsShowsItsDots()
    quickFlicksMakeOneStream()
    aJoinStartsTheWaitAgain()
    longDotsSpeedUpWhenTried()
    aPickedWordHoldsABeat()
    realTextFollowsTheCursor()
    realWordsForAWordDrag()
    nothingShowsAtFirst()
    onSpaceOnlyTheFingerShows()
    leaningLeftStretchesFromTheEndOfSpace()
    leaningRightStretchesFromTheEndOfSpace()
    aFingerOffTheKeyboardStaysOnTheTrack()
    theDeadZoneIsSpace()
    theSpeedLevelFollowsTheRealCursor()
    aWordDragReadsItsSpeedLikeTheCursor()
    fasterFlowsFaster()
    theFlowGoesTheWayTheFingerLeans()
    aLateFrameDoesNotMakeTheFlowJump()
    aSlipStopsTheFlowAndKeepsTheDisplay()
    theFingerComingBackCarriesOn()
    theEndHidesItAndGivesTheWordsBack()
    aWordDragMatchesTheRealSelection()
    aSelectionThatFitsStaysInTheMiddle()
    aLongSelectionKeepsItsMovingEndInView()
    aWordDragWithoutStepsChangesNothing()
    aHoldCountsEachWord()
    aLongerFlickDeletesFaster()
    theHoldEndsHideIt()
    aHoldIsNotAFlourish()
    aFlickHasABriefFlourish()
    aFlickCountsTheCharactersItDeleted()
    newInputEndsAFlick()
    aDoubleTapHasABriefConfirmation()
    aSecondFlickDoesNotFlash()
    aFlourishDoesNotBreakADrag()
    aDragReplacesAFlourish()
    aSlowerAnimationScaleSlowsItDown()
    withMotionOffItShowsAtOnce()
    withMotionOffNothingFlows()
    withMotionOffAFlourishIsAStillPicture()
    aFrameIsOnlyAskedForWhenNeeded()
    whereTheStripDoesNotShowNothingShows()
    turningTheStripOffEndsTheDisplay()
    aRecoveredGestureStartsClean()
    aSwipeDrawsItsPath()
    aSwipePathFadesIntoTheResult()
    aDeleteFlickCancelsThePath()
    aFlickFlourishReplacesThePath()
    aLongSwipeKeepsItsShape()
    aSwipeAsksForFramesOnlyWhileItFades()
    theGlowGrowsWithEachTap()
    theGlowFadesWhenNoTapFollows()
    aKeyPressPutsTheGlowOut()
    theGlowLeavesTheWordsAlone()
    withMotionOffTheGlowIsStill()
    onlyCountedTapsGlow()
    thePathFollowsTheTouchSize()
    aSlipCarriesThePathOn()
    aLiftedPathWaitsForTheSlipWindow()
    theGlowCarriesIntoASteer()
    theGlowCarriesIntoASelect()
    theHeldGlowGoesWithTheDisplay()
    theUnderglowLeansWithTheSteering()
    steeringsCaretNeverBlinks()
    println("Gesture display checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private fun near(actual: Float, expected: Float, message: String, tolerance: Float = .01f) {
    if (abs(actual - expected) > tolerance) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private fun isTrue(value: Boolean, message: String) {
    if (!value) throw AssertionError(message)
}

/** Advances frame by frame from [from] up to and including [to], and returns [to]. */
private fun GestureDisplay.run(from: Long, to: Long, scale: Float = 1f): Long {
    var now = from
    while (now <= to) {
        advance(now, scale)
        now += FRAME
    }
    return to
}

/** A drag that sends a frame each 16 ms, as the cursor's own timer does. */
private fun GestureDisplay.drag(column: Float, direction: Int, speed: Float, from: Long, to: Long, words: Boolean = false, scale: Float = 1f): Long {
    var now = from
    while (now <= to) {
        cursorFrame(column, direction, speed, words, now)
        advance(now, scale)
        now += FRAME
    }
    return to
}

private fun nothingShowsAtFirst() {
    val display = GestureDisplay()
    same(display.scene, Scene.None, "Nothing must show before a gesture")
    same(display.showing, false, "The words must take touches before a gesture")
    same(display.advance(1_000, 1f), false, "An empty display must not ask for frames")
}

private fun onSpaceOnlyTheFingerShows() {
    val display = GestureDisplay()
    display.drag(ON_SPACE, 0, 0f, 1_000, 1_100)
    same(display.scene, Scene.Cursor, "A cursor drag on Space must show the cursor scene")
    near(display.marker, .53f, "The marker must be where the finger is")
    near(display.bandStart, display.bandEnd, "On Space there must be no stretch", .001f)
    near(display.reveal, 1f, "The display must have replaced the words")
    near(display.intensity, 0f, "On Space the speed must be zero")
}

private fun leaningLeftStretchesFromTheEndOfSpace() {
    val display = GestureDisplay()
    display.drag(LEFT_OF_SPACE, -1, 4f, 1_000, 1_100)
    near(display.bandStart, .3f, "The stretch must reach the finger")
    near(display.bandEnd, display.spaceStart, "The stretch must begin at the left end of Space", .001f)
    same(display.leaning, -1, "The display must show the way the finger leans")
}

private fun leaningRightStretchesFromTheEndOfSpace() {
    val display = GestureDisplay()
    display.drag(RIGHT_OF_SPACE, 1, 4f, 1_000, 1_100)
    near(display.bandStart, display.spaceEnd, "The stretch must begin at the right end of Space", .001f)
    near(display.bandEnd, .75f, "The stretch must reach the finger")
    same(display.leaning, 1, "The display must show the way the finger leans")
}

private fun aFingerOffTheKeyboardStaysOnTheTrack() {
    val display = GestureDisplay()
    display.drag(-1f, -1, 4f, 1_000, 1_020)
    near(display.marker, 0f, "A finger past the left edge must stay at the end of the track")
    display.drag(11f, 1, 4f, 1_020, 1_040)
    near(display.marker, 1f, "A finger past the right edge must stay at the end of the track")
}

private fun theDeadZoneIsSpace() {
    val display = GestureDisplay()
    val (start, end) = Layout.bottomSpan(Layout.SPACE)
    near(display.spaceStart, CursorMode.DEAD_LEFT / 10, "The dead zone must be the one that the cursor uses", .001f)
    near(display.spaceEnd, CursorMode.DEAD_RIGHT / 10, "The dead zone must be the one that the cursor uses", .001f)
    isTrue(display.spaceStart > start && display.spaceEnd < end, "The dead zone must be inside Space")
    isTrue(display.spaceStart < .5f && display.spaceEnd > .5f, "Space must be in the middle")
}

private fun theSpeedLevelFollowsTheRealCursor() {
    val cursor = CursorMode()
    near(GestureDisplay.speedLevel(CursorMode.SLOWEST_PER_SECOND, false), 0f, "The slowest speed must be the bottom of the range")
    near(GestureDisplay.speedLevel(CursorMode.FASTEST_PER_SECOND, false), 1f, "The fastest speed must be the top of the range")
    same(GestureDisplay.speedLevel(0f, false), 0f, "On Space the level must be zero")
    var previous = -1f
    // From just past the left end of the dead zone out to where the top speed starts.
    var column = 3.8f
    while (column >= 1f) {
        cursor.start(Sample(.3f, .9f, 0))
        cursor.move(listOf(Sample(column / 10, .9f, 10)))
        val level = GestureDisplay.speedLevel(cursor.speed(10), false)
        isTrue(level > previous, "A further lean must show a higher level: column $column gave $level after $previous")
        previous = level
        column -= .4f
    }
}

private fun aWordDragReadsItsSpeedLikeTheCursor() {
    for (letters in listOf(1.5f, 3f, 8f, 16f, 28f, 52f)) {
        near(GestureDisplay.speedLevel(sqrt(letters), true), GestureDisplay.speedLevel(letters, false),
            "A word drag at the square root of a speed must show the level of that speed")
    }
}

private fun fasterFlowsFaster() {
    fun flowAfter(speed: Float): Float {
        val display = GestureDisplay()
        display.cursorFrame(RIGHT_OF_SPACE, 1, speed, false, 1_000)
        display.advance(1_000, 1f)
        // Long enough for the level to settle, then a single short stretch that does not wrap round.
        var now = 1_016L
        while (now <= 1_400) {
            display.cursorFrame(RIGHT_OF_SPACE, 1, speed, false, now)
            display.advance(now, 1f)
            now += FRAME
        }
        val before = display.flow
        display.cursorFrame(RIGHT_OF_SPACE, 1, speed, false, now)
        display.advance(now, 1f)
        return (display.flow - before).mod(1f)
    }
    val slow = flowAfter(1.5f)
    val fast = flowAfter(52f)
    isTrue(fast > slow * 3, "The top speed must flow several times faster than the lowest: $fast against $slow")
    // At 9 slashes a second a frame moves .144, so the flow can never look like it runs the wrong way.
    isTrue(fast < .25f, "The top speed must stay readable: $fast of a gap in a frame")
}

private fun theFlowGoesTheWayTheFingerLeans() {
    fun movedBy(direction: Int): Float {
        val display = GestureDisplay()
        display.cursorFrame(if (direction < 0) LEFT_OF_SPACE else RIGHT_OF_SPACE, direction, 8f, false, 1_000)
        display.advance(1_000, 1f)
        display.cursorFrame(if (direction < 0) LEFT_OF_SPACE else RIGHT_OF_SPACE, direction, 8f, false, 1_016)
        display.advance(1_016, 1f)
        val moved = display.flow
        return if (moved > .5f) moved - 1f else moved
    }
    isTrue(movedBy(1) > 0f, "A lean to the right must flow to the right")
    isTrue(movedBy(-1) < 0f, "A lean to the left must flow to the left")
}

private fun aLateFrameDoesNotMakeTheFlowJump() {
    val display = GestureDisplay()
    display.drag(RIGHT_OF_SPACE, 1, 8f, 1_000, 1_100)
    val before = display.flow
    display.cursorFrame(RIGHT_OF_SPACE, 1, 8f, false, 1_600)
    display.advance(1_600, 1f)
    isTrue((display.flow - before).mod(1f) < .5f, "A late frame must not move the slashes far")
}

private fun aSlipStopsTheFlowAndKeepsTheDisplay() {
    val display = GestureDisplay()
    display.drag(RIGHT_OF_SPACE, 1, 8f, 1_000, 1_100)
    display.cursorPaused()
    same(display.leaning, 0, "A slip must stop the flow")
    val time = display.run(1_116, 1_300)
    same(display.scene, Scene.Cursor, "A slip must keep the display while the finger can still come back")
    near(display.marker, .75f, "A slip must leave the marker where the finger left")
    near(display.intensity, 0f, "A slip must let the speed fall", .05f)
    near(display.reveal, 1f, "A slip must not hide the display")
    isTrue(time > 0, "The run must reach its end")
}

private fun theFingerComingBackCarriesOn() {
    val display = GestureDisplay()
    display.drag(RIGHT_OF_SPACE, 1, 8f, 1_000, 1_100)
    display.cursorPaused()
    display.run(1_116, 1_200)
    display.drag(LEFT_OF_SPACE, -1, 4f, 1_216, 1_300)
    same(display.leaning, -1, "A finger that came back must carry on")
    same(display.scene, Scene.Cursor, "The same scene must carry on")
}

private fun theEndHidesItAndGivesTheWordsBack() {
    val display = GestureDisplay()
    display.drag(RIGHT_OF_SPACE, 1, 8f, 1_000, 1_100)
    display.cursorEnded()
    same(display.scene, Scene.Cursor, "The display must stay for its way out")
    same(display.showing, true, "The words must stay covered until it has gone")
    display.advance(1_116, 1f)
    isTrue(display.reveal < 1f, "The display must start to go")
    isTrue(display.reveal > 0f, "The display must not vanish in one frame")
    display.run(1_132, 1_400)
    same(display.scene, Scene.None, "The display must go")
    same(display.showing, false, "The words must take touches again")
    near(display.reveal, 0f, "The words must be back")
    same(display.advance(1_500, 1f), false, "A display that has gone must not ask for frames")
}

private fun aWordDragMatchesTheRealSelection() {
    val text = "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen"
    val random = Random(7)
    repeat(40) {
        val display = GestureDisplay()
        display.cursorFrame(ON_SPACE, 0, 0f, true, 1_000)
        var at = text.indexOf("seven")
        var end = at + "seven".length
        val anchorStart = at
        val anchorEnd = end
        repeat(30) {
            val direction = if (random.nextBoolean()) 1 else -1
            val (newStart, newEnd) = WordSelection.extend(text, at, end, anchorStart, anchorEnd, direction)
            if (newStart == at && newEnd == end) return@repeat
            at = newStart
            end = newEnd
            display.wordStep(direction)
            val left = text.substring(at, anchorStart).trim().split(" ").count { it.isNotEmpty() }
            val right = text.substring(anchorEnd, end).trim().split(" ").count { it.isNotEmpty() }
            same(display.wordsLeft, left, "Words added on the left must match the real selection")
            same(display.wordsRight, right, "Words added on the right must match the real selection")
            isTrue(display.wordsLeft == 0 || display.wordsRight == 0, "The selection must grow on one side only")
        }
    }
}

private fun aSelectionThatFitsStaysInTheMiddle() {
    val display = GestureDisplay()
    display.cursorFrame(ON_SPACE, 1, 3f, true, 1_000)
    display.wordStep(1)
    display.wordStep(1)
    display.run(1_000, 1_500)
    near(display.camera, 1f, "Two words to the right must move the middle of the view by one word", .05f)
    near(display.spanEnd, 2.5f, "The selection must end after the last word", .4f)
    near(display.spanStart, -.5f, "The selection must start at the first word", .05f)
    same(display.leadingRight, true, "The right end is the one that moves")
}

private fun aLongSelectionKeepsItsMovingEndInView() {
    fun check(direction: Int) {
        val display = GestureDisplay()
        display.cursorFrame(ON_SPACE, 0, 0f, true, 1_000)
        repeat(12) { display.wordStep(direction) }
        display.run(1_000, 1_600)
        val edge = if (direction > 0) display.spanEnd else display.spanStart
        val offset = edge - display.camera
        isTrue(abs(offset) < GestureDisplay.VISIBLE_WORDS / 2, "The moving end must stay in view: it is $offset from the middle")
        isTrue(direction * offset > 0f, "The moving end must be on its own side of the middle")
        same(display.leadingRight, direction > 0, "The end that moves must be the one that grows")
    }
    check(1)
    check(-1)
}

private fun aWordDragWithoutStepsChangesNothing() {
    val display = GestureDisplay()
    display.drag(RIGHT_OF_SPACE, 1, 3f, 1_000, 1_100, words = true)
    same(display.scene, Scene.WordDrag, "A word drag must show the word scene")
    same(display.wordsRight, 0, "A lean with no step must select nothing more")
    same(display.wordsLeft, 0, "A lean with no step must select nothing more")
    isTrue(display.spanEnd > .5f, "A lean must pull the moving end towards the next word")
    isTrue(display.spanEnd < 1f, "The pull must stay short of a whole word")
}

private fun aHoldCountsEachWord() {
    val display = GestureDisplay()
    display.deleteHeld(200, 1_000)
    display.wordDeleted(1_000)
    display.wordDeleted(1_200)
    display.wordDeleted(1_400)
    display.run(1_400, 1_800)
    same(display.scene, Scene.DeleteHold, "A hold must show its scene")
    same(display.deleted, 3, "A hold must count each deleted word")
    near(display.shownDeleted, 3f, "The display must catch up with the count", .05f)
}

private fun aLongerFlickDeletesFaster() {
    fun flowOf(repeatMillis: Long): Float {
        val display = GestureDisplay()
        display.deleteHeld(repeatMillis, 1_000)
        display.advance(1_000, 1f)
        display.advance(1_016, 1f)
        return display.flow
    }
    val slowFlick = flowOf(350)
    val fastFlick = flowOf(80)
    isTrue(fastFlick > 3 * slowFlick, "A long flick must flow several times faster than a short one: $fastFlick against $slowFlick")
    isTrue(flowOf(30) <= fastFlick + .001f, "The flow must stay within what a person can read")
}

private fun theHoldEndsHideIt() {
    val display = GestureDisplay()
    display.deleteHeld(200, 1_000)
    display.wordDeleted(1_000, 5)
    display.wordDeleted(1_200, 3)
    display.wordDeleted(1_400, 4)
    display.run(1_000, 1_500)
    val shown = display.shownDeleted
    display.deleteEnded(1_516)
    same(display.scene, Scene.DeleteFlick, "When the hold lifts, its lines turn into the dots")
    same(display.fromHold, true, "The dots come from the hold")
    same(display.softDelete, true, "and show as a single flick's do")
    same(display.morphFrom, shown, "The lines start where the hold left them, with no jump")
    same(display.groups.toList(), listOf(5, 3, 4), "The dots group by word, nearest the caret first, with a gap where each space was")
    same(display.dots, 12, "A dot for each character the hold deleted")
    same(display.reveal, 1f, "The words stay covered at the lift")
    display.run(1_516, 1_700)
    isTrue(display.dotsIn > 0f && display.dotsIn < 1f, "The lines shrink into the dots over about 240 ms")
    display.run(1_716, 1_960)
    same(display.dotsIn, 1f, "Then the dots are in")
    same(display.dotsOut, 0f, "and they stay about 200 ms")
    display.run(1_976, 2_400)
    isTrue(display.dotsOut > 0f && display.dotsOut < 1f, "Then they go into the caret")
    display.run(2_416, 2_700)
    same(display.scene, Scene.None, "and the words come back, by about 1.15 s after the lift: twelve dots need 30 ms each to land")

    val empty = GestureDisplay()
    empty.deleteHeld(200, 1_000)
    empty.run(1_000, 1_100)
    empty.deleteEnded(1_116)
    empty.run(1_116, 1_400)
    same(empty.scene, Scene.None, "A hold that deleted nothing just goes")

    val long = GestureDisplay()
    long.deleteHeld(200, 1_000)
    repeat(8) { long.wordDeleted(1_000L + it * 200, 7) }
    long.deleteEnded(2_700)
    same(GestureDisplay.MAX_DOTS, 30, "About 30 dots")
    same(long.groups.toList(), List(8) { 4 }, "Every word shows, each with its share of about 30 dots")

    val cut = GestureDisplay()
    cut.deleteHeld(200, 1_000)
    cut.wordDeleted(1_000, 4)
    cut.deleteEnded(1_100)
    cut.run(1_100, 1_200)
    cut.interrupt()
    same(cut.scene, Scene.None, "A key or a touch cuts the hold's dots at once")
}

private fun aHoldIsNotAFlourish() {
    val display = GestureDisplay()
    display.deleteHeld(200, 1_000)
    display.wordDeleted(1_000)
    same(display.scene, Scene.DeleteHold, "The first word of a hold must count in the hold")
    same(display.deleted, 1, "The first word of a hold must count")
}

private fun aFlickHasABriefFlourish() {
    val display = GestureDisplay()
    display.wordDeleted(1_000, GestureDisplay.letters("I don't"))
    same(display.scene, Scene.DeleteFlick, "A flick must show a flourish")
    same(display.showing, true, "The flourish must cover the words")
    same(display.softDelete, true, "A single flick shows only its dots and the caret")
    same(display.dots, 5, "\"don't\" took five characters, so it shows five dots")
    display.run(1_000, 1_064)
    isTrue(display.reveal > .5f && display.reveal < 1f, "The words fade out, easing out, over about 150 ms: ${display.reveal}")
    display.run(1_080, 1_160)
    same(display.reveal, 1f, "and then they stay faded")
    same(display.groups.toList(), listOf(5), "One word, one group")
    display.run(1_080, 1_310)
    same(display.dotsIn, 1f, "The dots are all in after 120 ms")
    same(display.dotsOut, 0f, "and they stay a while, about 200 ms")
    same(display.reveal, 1f, "while the cover stays")
    display.run(1_326, 1_760)
    isTrue(display.dotsOut > 0f && display.dotsOut < 1f, "Then they go into the caret, over about 450 ms")
    same(display.reveal, 1f, "The words stay covered while the dots go")
    display.run(1_776, 1_910)
    isTrue(display.reveal < 1f && display.reveal > 0f, "Then the words fade back in, over about 200 ms")
    display.run(1_926, 1_990)
    same(display.scene, Scene.None, "It is over by about 0.97 s, and it is only a picture, so typing never waits for it")
}

private fun aFlickCountsTheCharactersItDeleted() {
    same(GestureDisplay.letters("I don't"), 5, "\"don't\" is five characters")
    same(GestureDisplay.letters("hello world   "), 5, "Spaces after the word do not count")
    same(GestureDisplay.letters("x"), 1, "One character, one dot")
    same(GestureDisplay.letters(""), 1, "A flick always shows at least one dot")
    same(GestureDisplay.letters(null), 1, "With no text to read, one dot")
    val long = GestureDisplay()
    long.wordDeleted(1_000, 60)
    same(long.dots, GestureDisplay.MAX_DOTS, "A very long word shows no more than the bar has room for")
}

private fun newInputEndsAFlick() {
    val display = GestureDisplay()
    display.wordDeleted(1_000, 4)
    display.run(1_000, 1_200)
    display.interrupt()
    same(display.scene, Scene.None, "A key or a touch ends the dots at once")
    same(display.showing, false, "and the words are back")
}

private fun aDoubleTapHasABriefConfirmation() {
    val display = GestureDisplay()
    display.wordSelected(1_000)
    same(display.scene, Scene.WordSelected, "A selected word must show a confirmation")
    display.run(1_000, 1_100)
    isTrue(display.spanEnd > 0f, "The selection must open over the word")
    display.run(1_116, 1_260)
    same(display.scene, Scene.WordSelected, "The pick holds a beat now, past the old 260 ms (owner, 2026-11-11)")
    display.run(1_276, 1_000 + GestureDisplay.PICK_MILLIS.toLong() + 20)
    same(display.scene, Scene.None, "and is over by about 790 ms")
}

private fun aSecondFlickDoesNotFlash() {
    val display = GestureDisplay()
    display.wordDeleted(1_000)
    display.run(1_000, 1_100)
    val before = display.reveal
    display.wordDeleted(1_116)
    display.advance(1_116, 1f)
    isTrue(display.reveal >= before - .01f, "A flick during a flourish must not make the display flash: $before to ${display.reveal}")
    same(display.scene, Scene.DeleteFlick, "A second flick must show its own flourish")
}

private fun aFlourishDoesNotBreakADrag() {
    val display = GestureDisplay()
    display.drag(RIGHT_OF_SPACE, 1, 8f, 1_000, 1_100)
    display.wordDeleted(1_100)
    display.wordSelected(1_100)
    same(display.scene, Scene.Cursor, "A flourish must not take over a running drag")
    display.deleteEnded()
    same(display.scene, Scene.Cursor, "The end of a hold must not end a cursor drag")
    display.run(1_116, 1_400)
    same(display.scene, Scene.Cursor, "The drag must still be showing")
}

private fun aDragReplacesAFlourish() {
    val display = GestureDisplay()
    display.wordDeleted(1_000)
    display.run(1_000, 1_100)
    display.cursorFrame(ON_SPACE, 0, 0f, false, 1_116)
    same(display.scene, Scene.Cursor, "A drag that starts must replace a flourish")
    same(display.flourish, 0f, "A new scene must start clean")
}

private fun aSlowerAnimationScaleSlowsItDown() {
    fun revealAfter(scale: Float): Float {
        val display = GestureDisplay()
        display.cursorFrame(ON_SPACE, 0, 0f, false, 1_000)
        display.advance(1_000, scale)
        display.advance(1_040, scale)
        return display.reveal
    }
    isTrue(revealAfter(1f) > revealAfter(2f), "A longer animation scale must make the display come in more slowly")
    val slowed = GestureDisplay()
    slowed.wordDeleted(1_000)
    slowed.run(1_000, 1_260, scale = 2f)
    same(slowed.scene, Scene.DeleteFlick, "A flourish must last longer with a longer animation scale")
}

private fun withMotionOffItShowsAtOnce() {
    val display = GestureDisplay()
    display.cursorFrame(RIGHT_OF_SPACE, 1, 8f, false, 1_000)
    display.advance(1_000, 0f)
    same(display.reveal, 1f, "With motion off the display must show at once")
    display.cursorEnded()
    same(display.advance(1_016, 0f), false, "With motion off the end must need no frames")
    same(display.scene, Scene.None, "With motion off the display must go at once")
}

private fun withMotionOffNothingFlows() {
    val display = GestureDisplay()
    display.drag(RIGHT_OF_SPACE, 1, 8f, 1_000, 1_100, scale = 0f)
    same(display.flow, 0f, "With motion off the slashes must not move")
    near(display.marker, .75f, "With motion off the marker must still show where the finger is")
    near(display.bandEnd, .75f, "With motion off the stretch must still show")
    near(display.intensity, GestureDisplay.speedLevel(8f, false), "With motion off the speed must show at once")
    val hold = GestureDisplay()
    hold.deleteHeld(200, 1_000)
    hold.wordDeleted(1_000)
    hold.wordDeleted(1_200)
    hold.advance(1_300, 0f)
    same(hold.flow, 0f, "With motion off a hold must not flow")
    near(hold.shownDeleted, 2f, "With motion off the words must step, with no slide")
    val words = GestureDisplay()
    words.drag(RIGHT_OF_SPACE, 1, 8f, 1_000, 1_020, words = true, scale = 0f)
    words.wordStep(1)
    words.advance(1_040, 0f)
    near(words.spanEnd, 1.5f + GestureDisplay.speedLevel(8f, true) * .3f, "With motion off a word must be added at once")
}

private fun withMotionOffAFlourishIsAStillPicture() {
    val display = GestureDisplay()
    display.wordDeleted(1_000)
    same(display.advance(1_000, 0f), true, "With motion off a flourish must wait for its end")
    near(display.reveal, 1f, "With motion off a flourish must show at once")
    near(display.flourish, 1f, "With motion off a flourish must show its end state")
    near(display.dotsIn, 1f, "With motion off the dots must all show at once, as a still picture")
    display.advance(1_300, 0f)
    same(display.scene, Scene.DeleteFlick, "With motion off a flourish must stay long enough to see")
    display.advance(1_400, 0f)
    same(display.scene, Scene.None, "With motion off a flourish must still end")
}

private fun aFrameIsOnlyAskedForWhenNeeded() {
    var asked = 0
    val display = GestureDisplay { asked++ }
    display.cursorFrame(RIGHT_OF_SPACE, 1, 8f, false, 1_000)
    same(asked, 1, "A drag frame must ask for a frame")
    same(display.advance(1_016, 1f), true, "A running drag must keep asking for frames")
    same(display.advance(1_016, 0f), false, "A drag with motion off must wait for the next finger update")
    asked = 0
    display.cursorPaused()
    same(asked, 1, "A slip must ask for a frame")
    asked = 0
    display.cursorEnded()
    same(asked, 1, "The end must ask for a frame")
    asked = 0
    val idle = GestureDisplay { asked++ }
    idle.cursorEnded()
    idle.deleteEnded()
    idle.cursorPaused()
    idle.wordStep(1)
    same(asked, 0, "Ends with nothing running must do no work, as they run on every touch")
}

private fun whereTheStripDoesNotShowNothingShows() {
    var asked = 0
    val display = GestureDisplay { asked++ }
    display.allowed = false
    display.cursorFrame(RIGHT_OF_SPACE, 1, 8f, false, 1_000)
    display.deleteHeld(200, 1_000)
    display.wordDeleted(1_000)
    display.wordSelected(1_000)
    same(display.scene, Scene.None, "A field with no strip must show nothing")
    same(display.showing, false, "A field with no strip must not take touches")
    same(asked, 0, "A field with no strip must ask for no frames")
    same(display.advance(1_016, 1f), false, "A field with no strip must not ask for frames")
}

private fun turningTheStripOffEndsTheDisplay() {
    val display = GestureDisplay()
    display.drag(RIGHT_OF_SPACE, 1, 8f, 1_000, 1_100)
    display.allowed = false
    same(display.scene, Scene.None, "A strip that goes away must take the display with it")
    near(display.reveal, 0f, "A strip that goes away must not leave a part of the display")
    display.allowed = true
    same(display.scene, Scene.None, "A strip that comes back must not bring back an old gesture")
}

private fun aRecoveredGestureStartsClean() {
    val display = GestureDisplay()
    display.cursorFrame(ON_SPACE, 0, 0f, true, 1_000)
    repeat(3) { display.wordStep(1) }
    display.cursorEnded()
    display.run(1_000, 1_400)
    display.cursorFrame(ON_SPACE, 0, 0f, true, 1_500)
    same(display.wordsRight, 0, "A new word drag must not keep the old words")
    display.run(1_500, 1_600)
    near(display.spanEnd, .5f, "A new word drag must start at its first word", .01f)
}

/** While steering, the line of letters is the real text around the caret, and it follows each step of the cursor. */
private fun realTextFollowsTheCursor() {
    val display = GestureDisplay()
    display.cursorFrame(5f, 0, 0f, words = false, now = 0L)
    display.cursorText("Hello there", " world")
    if (display.line.toString() != "Hello there world" || display.caretAt != 11) throw AssertionError("The line is the text, with the caret after 'there'")
    if (display.cursorStepped(-1)) throw AssertionError("One step needs no new text")
    if (display.stepped != -1) throw AssertionError("A step to the left moves the caret one character left")
    display.advance(1_000L, 0f)
    if (display.shownStep != -1f) throw AssertionError("With motion off the line is where the cursor is")
    val long = GestureDisplay()
    long.cursorFrame(5f, 0, 0f, words = false, now = 0L)
    long.cursorText("a".repeat(100), "")
    if (long.line!!.length != GestureDisplay.LINE_REACH) throw AssertionError("Only what the strip can use is kept")
    var reread = false
    repeat(GestureDisplay.LINE_REACH) { if (long.cursorStepped(-1)) reread = true }
    if (!reread) throw AssertionError("Near the end of the text read, it asks for the text again")
    long.cursorText("a".repeat(20), "b".repeat(20))
    if (long.stepped != 0) throw AssertionError("New text starts from the caret")
    if (GestureDisplay().line != null) throw AssertionError("With no text, the made-up line shows")
}

/** A word drag and a picked word show the real words around them, as wide as their letters, and none past the text. */
private fun realWordsForAWordDrag() {
    if (!GestureDisplay.wordLengths("  one two  three ").contentEquals(intArrayOf(3, 3, 5))) throw AssertionError("Words split at spaces")
    val display = GestureDisplay()
    display.wordText("the quick ", 5, " fox")
    if (display.wordWidth(0) != GestureDisplay.widthFor(5)) throw AssertionError("The first word is as wide as its letters")
    if (display.wordWidth(-1) != GestureDisplay.widthFor(5)) throw AssertionError("The word before it is quick")
    if (display.wordWidth(-2) != GestureDisplay.widthFor(3)) throw AssertionError("and then the")
    if (display.wordWidth(-3) != 0f) throw AssertionError("Past the start of the text there is no word")
    if (display.wordWidth(1) != GestureDisplay.widthFor(3) || display.wordWidth(2) != 0f) throw AssertionError("After it, fox and then nothing")
    if (GestureDisplay.widthFor(1) >= GestureDisplay.widthFor(9)) throw AssertionError("A longer word is wider")
    if (GestureDisplay().wordWidth(5) <= 0f) throw AssertionError("With no text known, a made-up row shows")
}

private fun near(actual: Float, expected: Float, message: String) {
    if (kotlin.math.abs(actual - expected) > .001f) throw AssertionError("$message: expected $expected, got $actual")
}

/** A word swipe dims the words and draws its path from the first point to the finger. */
private fun aSwipeDrawsItsPath() {
    val display = GestureDisplay()
    display.swipePoint(.1f, .1f, 0, 0L)
    display.swipePoint(.3f, .2f, 0, 10L)
    display.swipePoint(.5f, .1f, 0, 20L)
    same(display.scene, GestureDisplay.Scene.Swipe, "A swipe shows its mini path")
    same(display.pathSize, 3, "with each point")
    near(display.pathX[2], .5f, "The last point is the finger")
    display.advance(200L, 1f)
    near(display.reveal, 1f, "The words dim fully while it goes")
    display.swipePoint(.5f, .1f, 0, 210L)
    same(display.pathSize, 3, "A point where the finger already is adds nothing")
}

/** On lift the path fades over about 300 ms, while the words come back with what it typed. */
private fun aSwipePathFadesIntoTheResult() {
    val display = GestureDisplay()
    display.swipePoint(.1f, .1f, 0, 0L)
    display.swipePoint(.4f, .3f, 0, 10L)
    display.advance(200L, 1f)
    display.swipeEnded()
    display.advance(350L, 1f)
    if (display.reveal <= 0f || display.reveal >= 1f) throw AssertionError("Halfway, the path is fading: ${display.reveal}")
    display.advance(560L, 1f)
    same(display.scene, GestureDisplay.Scene.None, "After the fade the words are back")
    same(display.pathSize, 0, "and the path is gone")
    display.swipePoint(.2f, .2f, 0, 600L)
    same(display.pathSize, 1, "The next swipe starts a new path")
}

/** A touch that turns out to be a delete flick takes the path away at once, but a fading path is left to fade. */
private fun aDeleteFlickCancelsThePath() {
    val display = GestureDisplay()
    display.swipePoint(.9f, .3f, 0, 0L)
    display.swipePoint(.8f, .3f, 0, 10L)
    display.swipeCancelled()
    same(display.scene, GestureDisplay.Scene.None, "A delete flick shows no path")
    display.swipePoint(.1f, .1f, 0, 20L)
    display.swipePoint(.3f, .1f, 0, 30L)
    display.advance(150L, 1f)
    display.swipeEnded()
    display.swipeCancelled()
    same(display.scene, GestureDisplay.Scene.Swipe, "The next touch leaves a fading path to fade")
}

/** A flick's dots replace the path, and a cursor drag does too. */
private fun aFlickFlourishReplacesThePath() {
    val display = GestureDisplay()
    display.swipePoint(.1f, .1f, 0, 0L)
    display.swipePoint(.3f, .1f, 0, 10L)
    display.swipeEnded()
    display.wordDeleted(20L, 3)
    same(display.scene, GestureDisplay.Scene.DeleteFlick, "A flick's dots replace the path")
    same(display.pathSize, 0, "and the path goes")
    val drag = GestureDisplay()
    drag.cursorFrame(5f, 0, 0f, false, 0L)
    drag.swipePoint(.1f, .1f, 0, 10L)
    same(drag.scene, GestureDisplay.Scene.Cursor, "A swipe point never breaks a cursor drag")
}

/** A long swipe keeps every other point when it runs out of room, so its start and its finger stay. */
private fun aLongSwipeKeepsItsShape() {
    val display = GestureDisplay()
    for (index in 0..GestureDisplay.MAX_PATH) display.swipePoint((index % 100) / 100f, if (index / 100 % 2 == 0) .1f else .5f, 0, index.toLong())
    if (display.pathSize > GestureDisplay.MAX_PATH) throw AssertionError("The path never grows past its room")
    near(display.pathX[0], 0f, "The path still starts where the swipe did")
    near(display.pathX[display.pathSize - 1], (GestureDisplay.MAX_PATH % 100) / 100f, "and ends at the finger")
}

/** Light on frames: the path asks for a frame when it moves, and only while it comes in or fades. */
private fun aSwipeAsksForFramesOnlyWhileItFades() {
    var asked = 0
    val display = GestureDisplay { asked++ }
    display.swipePoint(.1f, .1f, 0, 0L)
    display.swipePoint(.3f, .1f, 0, 10L)
    same(asked, 2, "Each new point asks for a frame")
    display.swipePoint(.3f, .1f, 0, 12L)
    same(asked, 2, "A point that does not move asks for none")
    if (!display.advance(40L, 1f)) throw AssertionError("While it comes in, it asks for frames")
    display.advance(300L, 1f)
    if (display.advance(320L, 1f)) throw AssertionError("A still finger asks for no more frames")
    display.swipeEnded()
    if (!display.advance(330L, 1f)) throw AssertionError("While it fades, it asks for frames")
}

/** Tap one is small and soft, tap two grows from where the glow is, and tap three is full under the middle word with a pulse. */
private fun theGlowGrowsWithEachTap() {
    val display = GestureDisplay()
    display.spaceTapped(1, 0L)
    same(display.showing, false, "The glow leaves the words showing")
    display.advance(GestureDisplay.GLOW_GROW.toLong(), 1f)
    near(display.glowWidth, GestureDisplay.GLOW_WIDTHS[1], "One tap: a small glow")
    near(display.glowAlpha, GestureDisplay.GLOW_ALPHAS[1], "and a soft one")
    display.advance(150L, 1f)
    val before = display.glowWidth
    display.spaceTapped(2, 150L)
    display.advance(150L, 1f)
    near(display.glowWidth, before, "Tap two grows from where the glow is")
    display.advance(150L + GestureDisplay.GLOW_GROW.toLong(), 1f)
    near(display.glowWidth, GestureDisplay.GLOW_WIDTHS[2], "Two taps: wider")
    near(display.glowAlpha, GestureDisplay.GLOW_ALPHAS[2], "and brighter")
    display.spaceTapped(3, 300L)
    display.advance(330L, 1f)
    if (display.glowPulse <= 0f) throw AssertionError("Three taps pulse")
    display.advance(300L + GestureDisplay.GLOW_GROW.toLong(), 1f)
    near(display.glowWidth, GestureDisplay.GLOW_WIDTHS[3], "Three taps: full under the middle word")
    if (GestureDisplay.GLOW_WIDTHS[1] >= GestureDisplay.GLOW_WIDTHS[2] || GestureDisplay.GLOW_WIDTHS[2] >= GestureDisplay.GLOW_WIDTHS[3]
        || GestureDisplay.GLOW_ALPHAS[1] >= GestureDisplay.GLOW_ALPHAS[2] || GestureDisplay.GLOW_ALPHAS[2] >= GestureDisplay.GLOW_ALPHAS[3])
        throw AssertionError("Each tap is wider and brighter than the last")
}

/** With no tap after it, the glow fades in about 250 ms, and an animation scale slows it. */
private fun theGlowFadesWhenNoTapFollows() {
    val display = GestureDisplay()
    display.spaceTapped(1, 0L)
    display.advance(GestureDisplay.GLOW_GROW.toLong() + 125, 1f)
    if (display.glowAlpha <= 0f || display.glowAlpha >= GestureDisplay.GLOW_ALPHAS[1]) throw AssertionError("Halfway, it is fading: ${display.glowAlpha}")
    if (display.advance((GestureDisplay.GLOW_GROW + GestureDisplay.GLOW_FADE).toLong() + 1, 1f)) throw AssertionError("Then it asks for no frames")
    same(display.glowTaps, 0, "and it is gone")
    if (GestureDisplay.GLOW_FADE != 250f) throw AssertionError("The owner asked for about 250 ms")
    val slow = GestureDisplay()
    slow.spaceTapped(1, 0L)
    slow.advance((GestureDisplay.GLOW_GROW + GestureDisplay.GLOW_FADE).toLong() + 1, 2f)
    same(slow.glowTaps, 1, "With the animation scale at 2 it lasts twice as long")
}

/** A key press means the taps made nothing, so the glow goes at once. A touch landing does not. */
private fun aKeyPressPutsTheGlowOut() {
    val display = GestureDisplay()
    display.spaceTapped(1, 0L)
    display.interrupt()
    same(display.glowTaps, 1, "The next touch landing keeps the glow, so its tap can grow it")
    display.glowOff()
    same(display.glowTaps, 0, "A key press puts it out")
    same(display.busy, false, "and nothing is left to draw")
}

/** The glow sits under the words and never covers them, also over a swipe's path, and not where the strip does not show. */
private fun theGlowLeavesTheWordsAlone() {
    val display = GestureDisplay()
    display.spaceTapped(2, 0L)
    same(display.busy, true, "The glow needs drawing")
    same(display.showing, false, "but a touch on the strip can still pick a word")
    display.allowed = false
    same(display.glowTaps, 0, "Where the strip does not show, the glow goes")
    display.spaceTapped(1, 10L)
    same(display.glowTaps, 0, "and none starts")
}

/** With animations off, the glow shows at its size with no motion, then goes. */
private fun withMotionOffTheGlowIsStill() {
    val display = GestureDisplay()
    display.spaceTapped(3, 0L)
    display.advance(1L, 0f)
    near(display.glowWidth, GestureDisplay.GLOW_WIDTHS[3], "It shows at its size at once")
    same(display.glowPulse, 0f, "with no pulse")
    display.advance((GestureDisplay.GLOW_GROW + GestureDisplay.GLOW_FADE).toLong(), 0f)
    same(display.glowTaps, 0, "then goes")
}

/** The gesture code says which taps count: quick taps on Space, one to three. A slow touch or one that slides counts none. */
private fun onlyCountedTapsGlow() {
    val gesture = CursorGesture()
    val start = 10_000L
    fun tap(at: Long, x: Float = .5f, lift: Long = 60L, slide: Float = 0f): Int {
        gesture.down(Sample(x, .9f, at), true)
        gesture.up(listOf(Sample(x + slide, .9f, at + lift)), at + lift)
        return gesture.tapped
    }
    same(tap(start), 1, "A quick tap on Space counts as one")
    same(tap(start + 160), 2, "a second as two")
    same(tap(start + 320), 3, "a third as three")
    val slow = CursorGesture()
    slow.down(Sample(.5f, .9f, start), true)
    slow.up(listOf(Sample(.5f, .9f, start + 400)), start + 400)
    same(slow.tapped, 0, "A held touch is no tap")
    val swipe = CursorGesture()
    swipe.down(Sample(.5f, .9f, start), true)
    swipe.up(listOf(Sample(.2f, .9f, start + 60)), start + 60)
    same(swipe.tapped, 0, "A touch that slides is no tap")
    val key = CursorGesture()
    key.down(Sample(.5f, .9f, start), false)
    key.up(listOf(Sample(.5f, .9f, start + 60)), start + 60)
    same(key.tapped, 0, "A touch that can't type, such as just after a key press, is no tap")
}

/** A word picked with no slide shows its mark, holds it about 400 ms, then goes over about 300 ms. A key or touch ends it at once. */
private fun aPickedWordHoldsABeat() {
    val display = GestureDisplay()
    display.wordSelected(0L)
    display.advance(GestureDisplay.PICK_IN.toLong() + 200, 1f)
    if (display.scene != GestureDisplay.Scene.WordSelected) throw AssertionError("The pick still shows in its hold")
    if (display.reveal != 1f) throw AssertionError("The cover is full during the hold, so no suggestion shows through: ${display.reveal}")
    if (display.spanStart != -.5f || display.spanEnd != .5f) throw AssertionError("The mark covers the whole word during the hold")
    display.advance((GestureDisplay.PICK_IN + GestureDisplay.PICK_HOLD + GestureDisplay.PICK_OUT / 2).toLong(), 1f)
    if (display.reveal <= 0f || display.reveal >= 1f) throw AssertionError("Halfway out, it is going: ${display.reveal}")
    display.advance(GestureDisplay.PICK_MILLIS.toLong() + 1, 1f)
    if (display.scene != GestureDisplay.Scene.None) throw AssertionError("After about 790 ms the suggestions are back")
    if (GestureDisplay.PICK_HOLD != 400f || GestureDisplay.PICK_OUT != 300f) throw AssertionError("The owner's timings: hold 400 ms, exit 300 ms")

    val cut = GestureDisplay()
    cut.wordSelected(0L)
    cut.advance(150L, 1f)
    cut.interrupt()
    if (cut.scene != GestureDisplay.Scene.None) throw AssertionError("A key or touch ends the pick at once")
}

/** The path is thin where the touch is light and thicker where it is firmer, and it follows the size live. */
private fun thePathFollowsTheTouchSize() {
    val display = GestureDisplay()
    val usual = Math.round(TrailThickness.usual(.2f, .3f))
    display.swipePoint(.20f, .3f, usual, 0L)
    display.swipePoint(.22f, .3f, usual, 10L)
    near(display.pathWidth[1], 1f, "A usual touch draws the usual thickness")
    var x = .32f
    repeat(30) {
        x += .01f
        display.swipePoint(x, .3f, usual + 3, 20L + it)
    }
    if (display.pathWidth[display.pathSize - 1] <= 1.5f) throw AssertionError("A firmer touch draws thicker: ${display.pathWidth[display.pathSize - 1]}")
    val light = GestureDisplay()
    light.swipePoint(.3f, .3f, 1, 0L)
    x = .3f
    repeat(30) {
        x += .01f
        light.swipePoint(x, .3f, 1, 10L + it)
    }
    if (light.pathWidth[light.pathSize - 1] >= 1f) throw AssertionError("A light touch draws thinner: ${light.pathWidth[light.pathSize - 1]}")
    var asked = 0
    val still = GestureDisplay { asked++ }
    still.swipePoint(.2f, .3f, usual, 0L)
    repeat(20) { still.swipePoint(.2f, .3f, usual + 4, 10L + it) }
    same(still.pathSize, 1, "A finger that only presses harder adds no point")
    if (still.pathWidth[0] <= 1.2f || asked < 2) throw AssertionError("but its thickness grows live: ${still.pathWidth[0]}")
}

/** After a slip or a short lift, a touch that carries the swipe on carries its path on too, as one line. */
private fun aSlipCarriesThePathOn() {
    val display = GestureDisplay()
    display.swipePoint(.1f, .1f, 0, 0L)
    display.swipePoint(.3f, .1f, 0, 10L)
    display.advance(100L, 1f)
    display.swipeEnded(100L, 200L)
    same(display.swiping, false, "A lifted swipe is not swiping")
    same(display.swipeCarriesOn(), true, "A touch that carries it on keeps the path")
    display.swipePoint(.32f, .12f, 0, 150L)
    same(display.pathSize, 3, "and its points add to the same line")
    near(display.pathX[0], .1f, "which still starts where the swipe began")
    val gone = GestureDisplay()
    gone.swipePoint(.1f, .1f, 0, 0L)
    gone.swipePoint(.3f, .1f, 0, 10L)
    gone.advance(100L, 1f)
    gone.swipeEnded(100L, 0L)
    gone.advance(500L, 1f)
    same(gone.swipeCarriesOn(), false, "A path that has gone can't be carried on")
    same(GestureDisplay().swipeCarriesOn(), false, "nor can a swipe that never showed")
}

/** A lifted path stays through the keep-swiping window, then fades into the result. */
private fun aLiftedPathWaitsForTheSlipWindow() {
    val display = GestureDisplay()
    display.swipePoint(.1f, .1f, 0, 0L)
    display.swipePoint(.3f, .1f, 0, 10L)
    display.advance(100L, 1f)
    display.swipeEnded(100L, 250L)
    if (!display.advance(300L, 1f)) throw AssertionError("While it waits, it still asks for frames so it can start to fade")
    near(display.reveal, 1f, "Within the window it stays whole")
    display.advance(500L, 1f)
    if (display.reveal >= 1f || display.reveal <= 0f) throw AssertionError("After the window it fades: ${display.reveal}")
    display.advance(700L, 1f)
    same(display.scene, GestureDisplay.Scene.None, "and then it is gone")
}

/** A tap then a held touch steers: the tap's glow grows to the full width and stays as part of the steering, with no gap. */
private fun theGlowCarriesIntoASteer() {
    val display = GestureDisplay()
    display.spaceTapped(1, 0L)
    display.advance(150L, 1f)
    val before = display.glowWidth
    val strength = display.glowAlpha
    display.cursorFrame(5f, 0, 0f, false, 150L)
    display.advance(150L, 1f)
    near(display.glowWidth, before, "It carries on from where the tap left it, with no jump")
    near(display.glowAlpha, strength, "and no flash")
    display.advance(150L + GestureDisplay.GLOW_HOLD_GROW.toLong(), 1f)
    near(display.glowWidth, GestureDisplay.GLOW_HELD_WIDTH, "It grows to the full width")
    display.advance(2000L, 1f)
    same(display.glowing, true, "and stays as long as the steering does")
    near(display.glowAlpha, GestureDisplay.GLOW_HELD_ALPHA, "at full strength")
}

/** Two taps then a held touch select: the glow carries on the same way into the selecting display. */
private fun theGlowCarriesIntoASelect() {
    val display = GestureDisplay()
    display.spaceTapped(1, 0L)
    display.spaceTapped(2, 150L)
    display.advance(250L, 1f)
    val before = display.glowWidth
    display.cursorFrame(5f, 0, 0f, true, 250L)
    same(display.scene, GestureDisplay.Scene.WordDrag, "Two taps and a hold select words")
    display.advance(250L, 1f)
    near(display.glowWidth, before, "The glow goes on from where it was")
    display.advance(250L + GestureDisplay.GLOW_HOLD_GROW.toLong(), 1f)
    near(display.glowWidth, GestureDisplay.GLOW_HELD_WIDTH, "to the full width")
    val still = GestureDisplay()
    still.spaceTapped(1, 0L)
    still.cursorFrame(5f, 0, 0f, true, 50L)
    still.advance(51L, 0f)
    near(still.glowWidth, GestureDisplay.GLOW_HELD_WIDTH, "With animations off it is full at once")
}

/** The held glow goes as the steering or selecting goes, and a key press or a flourish does not leave it behind. */
private fun theHeldGlowGoesWithTheDisplay() {
    val display = GestureDisplay()
    display.spaceTapped(1, 0L)
    display.cursorFrame(5f, 0, 0f, false, 100L)
    display.advance(400L, 1f)
    display.cursorEnded()
    display.advance(450L, 1f)
    if (display.glowAlpha >= GestureDisplay.GLOW_HELD_ALPHA || display.glowAlpha <= 0f) throw AssertionError("It fades with the steering: ${display.glowAlpha}")
    display.advance(1000L, 1f)
    same(display.scene, GestureDisplay.Scene.None, "The steering is gone")
    same(display.glowing, false, "and so is the glow")
    same(display.busy, false, "with nothing left to draw")
    val off = GestureDisplay()
    off.spaceTapped(1, 0L)
    off.cursorFrame(5f, 0, 0f, false, 100L)
    off.allowed = false
    same(off.glowing, false, "Where the strip does not show, the held glow goes too")
}

/**
 * A held delete's glow is the underglow rooted where Backspace is. It grows in as the hold starts, gets stronger as the pace
 * speeds up and stops changing at the top-speed line, and along the bottom it reaches further left with the pace. At the lift it
 * stays where it was and fades; a key ends it at once. Space taps keep their own glow.
 */
private fun aHeldDeleteGlowFollowsItsPace() {
    val display = GestureDisplay()
    display.deleteHeld(350, 1_000, pace = 0f)
    same(display.glowDelete, true, "A held delete's glow is its own")
    same(display.glowing, true, "and shows")
    display.run(1_000, 1_400)
    near(display.glowAlpha, GestureDisplay.GLOW_HELD_ALPHA, "It grows to the held glow's strength", .01f)
    near(display.shownPace, 0f, "At the slowest it is at its weakest")
    near(display.glowWidth, GestureDisplay.DELETE_REACH_LEAST, "and along the bottom at its shortest")
    display.deleteHeld(200, 1_400, pace = .5f)
    display.run(1_416, 1_432)
    isTrue(display.shownPace > 0f && display.shownPace < .5f, "It follows the pace smoothly: ${display.shownPace}")
    display.run(1_448, 1_900)
    near(display.shownPace, .5f, "and gets there", .005f)
    display.deleteHeld(80, 1_900, pace = 1f)
    display.run(1_916, 2_400)
    near(display.shownPace, 1f, "At the top-speed line it is at its strongest", .005f)
    near(display.glowWidth, GestureDisplay.DELETE_REACH_MOST, "and reaches furthest", .005f)
    // Past the line the pace is still 1, so nothing changes.
    display.deleteHeld(80, 2_400, pace = 1.4f)
    display.run(2_416, 2_600)
    near(display.shownPace, 1f, "Past the line it stops changing")
    display.wordDeleted(2_600, 4)
    display.deleteEnded(2_616)
    same(display.glowing, true, "At the lift it stays")
    display.run(2_616, 2_616 + GestureDisplay.GLOW_FADE.toLong() / 2)
    isTrue(display.glowAlpha > 0f && display.glowAlpha < GestureDisplay.GLOW_HELD_ALPHA, "and fades: ${display.glowAlpha}")
    same(display.glowDelete, true, "as the hold's glow")
    display.run(2_616 + GestureDisplay.GLOW_FADE.toLong() / 2 + 16, 2_616 + GestureDisplay.GLOW_FADE.toLong() + 32)
    same(display.glowing, false, "then it is gone")
    // The Space glow is still under the middle word.
    val space = GestureDisplay()
    space.spaceTapped(2, 1_000)
    space.run(1_000, 1_100)
    near(space.glowCentre, .5f, "Space taps glow under the middle word")
    same(space.glowDelete, false, "with their own glow")
    // A key ends a held delete's fading glow at once.
    val key = GestureDisplay()
    key.deleteHeld(200, 1_000)
    key.wordDeleted(1_100, 3)
    key.deleteEnded(1_200)
    key.glowOff()
    same(key.glowing, false, "A key ends it at once")
    same(key.glowDelete, false, "and it is no longer the hold's")
}

/**
 * In the pause zone the hold shows its zone by what it does: its words stand still, its glow fades out, and its caret blinks as
 * steering's does when it pauses. Out of it they move again, the glow comes back and the caret stays on.
 */
private fun aPausedHoldFadesAndBlinks() {
    val display = GestureDisplay()
    display.deleteHeld(200, 1_000, pace = .5f)
    display.run(1_000, 1_300)
    display.deletePaused(true)
    same(display.deletePaused, true, "The display knows the hold is paused")
    val flow = display.flow
    display.run(1_316, 1_332)
    same(display.caretShown, true, "The caret shows as the pause starts")
    display.run(1_348, 1_316 + GestureDisplay.BLINK_MILLIS + 16)
    same(display.caretShown, false, "then blinks off")
    display.run(1_316 + GestureDisplay.BLINK_MILLIS + 32, 1_316 + 2 * GestureDisplay.BLINK_MILLIS + 16)
    same(display.caretShown, true, "and on again")
    same(display.flow, flow, "Paused, its words stand still")
    near(display.glowAlpha, 0f, "and its glow has faded out", .01f)
    display.deletePaused(false)
    display.run(2_400, 2_800)
    same(display.caretShown, true, "Out of the pause the caret stays on")
    near(display.glowAlpha, GestureDisplay.GLOW_HELD_ALPHA, "the glow comes back: ${display.glowAlpha}", .02f)
    isTrue(display.flow != flow, "and the words move again")
}

/**
 * A hold draws the real words before the cursor: one pill for each, as long as the word, nearest the caret first, from the text
 * read as each word is about to go. A lifted hold's lines keep their lengths as they turn into dots.
 */
private fun aHoldDrawsTheRealWords() {
    val display = GestureDisplay()
    display.deleteHeld(200, 1_000, before = "The quick brown fox")
    near(display.holdWordWidth(0), GestureDisplay.widthFor(3), "The nearest pill is fox's")
    near(display.holdWordWidth(1), GestureDisplay.widthFor(5), "then brown's")
    near(display.holdWordWidth(2), GestureDisplay.widthFor(5), "then quick's")
    near(display.holdWordWidth(3), GestureDisplay.widthFor(3), "then The's")
    same(display.holdWordWidth(4), 0f, "and nothing past the start of the text")
    same(display.holdNext, 0, "fox is about to go")
    display.wordDeleted(1_100, 3)
    display.deleteText("The quick brown ")
    same(display.holdNext, 1, "then brown")
    near(display.holdWordWidth(0), GestureDisplay.widthFor(3), "fox keeps its length as it goes into the caret")
    near(display.holdWordWidth(1), GestureDisplay.widthFor(5), "brown is next")
    near(display.holdWordWidth(3), GestureDisplay.widthFor(3), "The is still last")
    same(display.holdWordWidth(4), 0f, "and nothing past it")
    display.wordDeleted(1_200, 5)
    display.deleteEnded(1_300)
    near(display.holdWordWidth(1), GestureDisplay.widthFor(5), "A lifted hold's lines keep their lengths")
    same(display.holdNext, 2, "and which was about to go")
    // A text cut off at the reach starts in the middle of a word: that one is not drawn, and past it the words are made up.
    val long = GestureDisplay()
    val words = (1..20).joinToString(" ") { "word" }
    long.deleteHeld(200, 1_000, before = words)
    val known = (words.length - words.indexOf(' ', words.length - GestureDisplay.LINE_REACH)) / 5
    isTrue(long.holdWordWidth(known + 2) > 0f, "Past the text read, more words show")
    // A field that gives no text keeps the made-up row.
    val unknown = GestureDisplay()
    unknown.deleteHeld(200, 1_000)
    isTrue((0..6).all { unknown.holdWordWidth(it) > 0f }, "With no text known, a made-up row shows")
}

/** With no real text left before the cursor a hold draws no pills at all, only its caret. */
private fun aHoldWithNoTextLeftDrawsNoPills() {
    val empty = GestureDisplay()
    empty.deleteHeld(200, 1_000, before = "")
    isTrue((-1..10).all { empty.holdWordWidth(it) == 0f }, "Nothing before the cursor: no pills")
    // Running out: the last word goes, then the text before the cursor is empty.
    val last = GestureDisplay()
    last.deleteHeld(200, 1_000, before = "Hello")
    last.wordDeleted(1_100, 5)
    last.deleteText("")
    same(last.holdWordWidth(1), 0f, "After the last word, no pills")
    isTrue((2..10).all { last.holdWordWidth(it) == 0f }, "none at all")
    near(last.holdWordWidth(0), GestureDisplay.widthFor(5), "Only the word that went, still going into the caret")
    last.run(1_100, 1_400)
    same(last.caretShown, true, "The caret still shows")
}

/** When each dot of a lifted hold of [words] words of five letters lands, for a hold whose fastest pace was [pace]. */
private fun holdDots(pace: Float, words: Int = 20): List<Float> {
    val display = GestureDisplay()
    display.speedUp = true
    display.deleteHeld(200, 1_000, pace = pace)
    repeat(words) { display.wordDeleted(1_100L + it, 5) }
    display.deleteEnded(1_200)
    return (0 until display.dots).map { display.dotLandsAt(it) }
}

/**
 * A lifted hold's dots speed up only as far as its words did: not at all if the finger stayed at the slowest, to the fastest gap
 * once it reached the top-speed line, and between for a finger between. So dots, words and glow top out together.
 */
private fun aHoldsDotsSpeedUpAsFarAsItsWordsDid() {
    val slow = holdDots(0f).zipWithNext { a, b -> b - a }
    val middle = holdDots(.5f).zipWithNext { a, b -> b - a }
    val top = holdDots(1f).zipWithNext { a, b -> b - a }
    near(slow.minOrNull()!!, slow.maxOrNull()!!, "At the slowest the dots keep an even pace: $slow", .5f)
    near(top.minOrNull()!!, GestureDisplay.DOT_FASTEST_GAP, "At the top-speed line they reach the fastest gap", .5f)
    isTrue(middle.minOrNull()!! > top.minOrNull()!! + 1f && middle.minOrNull()!! < slow.minOrNull()!! - 1f,
        "Between, they top out between: ${middle.minOrNull()}")
    isTrue(holdDots(1f).last() < holdDots(.5f).last() && holdDots(.5f).last() < holdDots(0f).last(), "and a faster hold's dots end sooner")
}

/**
 * Two taps then a held third press glow every time, however steady the finger: the glow grows over the whole bar, a little
 * thicker than steering's, stays as long as the finger does, and fades at the lift. A slide carries it on as the select's.
 */
private fun aHeldTripleGlowsEveryTime() {
    for (hold in listOf(200L, 900L, 2_500L)) {
        val display = GestureDisplay()
        display.spaceTapped(2, 900)
        display.wordSelected(1_000, held = true)
        display.pickHeldGlow(1_000 + CursorGesture.REGISTER_MILLIS)
        display.run(1_000, 1_000 + hold)
        same(display.glowing, true, "Held $hold ms, the triple press still glows")
        same(display.glowThick, true, "a little thicker than steering's")
        near(display.glowWidth, GestureDisplay.GLOW_HELD_WIDTH, "over the whole bar")
        display.pickLifted(1_000 + hold)
        display.run(1_000 + hold + 16, 1_000 + hold + GestureDisplay.GLOW_FADE.toLong() + 48)
        same(display.glowing, false, "After the lift it fades and goes")
    }
    // With no pick yet, as when typing still goes through, it still glows.
    val early = GestureDisplay()
    early.pickHeldGlow(1_000)
    early.run(1_000, 1_500)
    same(early.glowing, true, "It glows before the pick shows too")
    // A slide makes it the select's glow, which goes with the select.
    val slide = GestureDisplay()
    slide.wordSelected(1_000, held = true)
    slide.pickHeldGlow(1_070)
    slide.run(1_000, 1_200)
    slide.drag(6f, 1, 3f, 1_216, 1_400, words = true)
    same(slide.glowing, true, "A slide keeps the glow")
    same(slide.glowThick, true, "still thicker")
    near(slide.glowAlpha, GestureDisplay.GLOW_HELD_ALPHA, "with no dip")
    // Nothing to pick puts it out.
    val nothing = GestureDisplay()
    nothing.pickHeldGlow(1_000)
    nothing.glowOff()
    same(nothing.glowing, false, "With nothing to pick it goes at once")
}

/** The triple press's select glow leans with the hand, as steering's does. */
private fun theTripleGlowFollowsTheHand() {
    val display = GestureDisplay()
    display.pickHeldGlow(1_000)
    display.drag(8f, 1, 4f, 1_000, 1_600, words = true)
    isTrue(display.steerLean > .5f, "Leaning right, the select's glow leans right: ${display.steerLean}")
    display.drag(2f, -1, 4f, 1_616, 2_200, words = true)
    isTrue(display.steerLean < -.5f, "and left: ${display.steerLean}")
}

/**
 * A triple press's pick shows its orange selection at once, as a drag does, with no wait for a slide; a slide from it carries on
 * as the same selection, from the same word (owner, 2026-11-12).
 */
private fun aTriplePickShowsItsSelectionAtOnce() {
    val triple = GestureDisplay()
    triple.wordSelected(1_000, held = true)
    triple.run(1_016, 1_400)
    same(triple.scene, GestureDisplay.Scene.WordSelected, "The pick shows")
    near(triple.spanStart, -.5f, "Its selection covers the word", .01f)
    near(triple.spanEnd, .5f, "the whole word", .01f)
    triple.drag(8f, 1, 3f, 1_400, 1_416, words = true)
    same(triple.scene, GestureDisplay.Scene.WordDrag, "A slide selects on")
    isTrue(triple.spanStart <= -.49f && triple.spanEnd >= .49f, "from the same word: ${triple.spanStart}..${triple.spanEnd}")
}

/**
 * A triple tap's third touch picks the word as it lands. While it stays down the mark stays over the word, however long, and the
 * selected text waits under it; after the lift the mark goes at its usual pace. A quick lift keeps the usual timing.
 */
private fun aTriplePickWaitsForTheLift() {
    val display = GestureDisplay()
    display.wordSelected(1_000, held = true)
    display.run(1_000, 3_000)
    same(display.scene, Scene.WordSelected, "Two seconds down, the mark still shows")
    same(display.reveal, 1f, "and covers the selected text")
    isTrue(display.spanEnd - display.spanStart > .99f, "fully grown over the word")
    display.pickLifted()
    display.run(3_016, 3_016 + GestureDisplay.PICK_OUT.toLong() / 2)
    isTrue(display.reveal > 0f && display.reveal < 1f, "After the lift it fades: ${display.reveal}")
    display.run(3_016 + GestureDisplay.PICK_OUT.toLong() / 2 + 16, 3_016 + GestureDisplay.PICK_OUT.toLong() + 32)
    same(display.scene, Scene.None, "and the selected text shows")

    val quick = GestureDisplay()
    quick.wordSelected(1_000, held = true)
    quick.run(1_000, 1_100)
    quick.pickLifted()
    quick.run(1_116, 1_000 + (GestureDisplay.PICK_IN + GestureDisplay.PICK_HOLD).toLong() - 16)
    same(quick.reveal, 1f, "A quick lift still holds the mark its usual time")
    quick.run(1_000 + (GestureDisplay.PICK_IN + GestureDisplay.PICK_HOLD).toLong(), 1_000 + GestureDisplay.PICK_MILLIS.toLong() + 20)
    same(quick.scene, Scene.None, "then goes as before")

    val cut = GestureDisplay()
    cut.wordSelected(1_000, held = true)
    cut.run(1_000, 1_600)
    cut.interrupt()
    same(cut.scene, Scene.None, "A key or a touch still ends it at once")
}

/**
 * A hold covers the words fully, and its dots stay on that cover until they have all gone into the caret, so a row of dots never
 * lies over the suggestions (owner, 2026-11-12: one crossed out a word). Then the words come back, easing in.
 */
private fun aHoldsDotsNeverLieOverTheWords() {
    val display = GestureDisplay()
    display.deleteHeld(200, 1_000)
    repeat(3) { display.wordDeleted(1_000L + it, 4) }
    display.run(1_000, 1_200)
    display.deleteEnded(1_216)
    display.run(1_216, 1_216)
    same(display.solid, 1f, "At the lift the words are fully covered")
    val landed = display.dotLandsAt(display.dots - 1).toLong()
    display.run(1_232, 1_216 + landed)
    same(display.solid, 1f, "and stay covered while any dot is still on its way")
    same(display.reveal, 1f, "the cover whole")
    display.run(1_216 + landed + 16, 1_216 + landed + 2_000)
    same(display.showing, false, "Once the dots are in, the words come back")
    val flick = GestureDisplay()
    flick.wordDeleted(1_000, 4)
    flick.run(1_000, 1_100)
    same(flick.solid, 0f, "A flick's words were never fully covered")
}

/**
 * However many words go, the dots play: a hold of eighty words, and a hundred quick flicks one after another, show a dot for
 * every word, and every dot lands before the words come back. A row longer than the bar keeps its dots their size and slides
 * along as they land.
 */
private fun anyNumberOfWordsShowsItsDots() {
    fun landsAll(display: GestureDisplay, from: Long): Long {
        val landed = BooleanArray(display.dots)
        var now = from
        while (display.scene != Scene.None && now < from + 30_000) {
            display.advance(now, 1f)
            isTrue(display.shownUnits <= GestureDisplay.MAX_ROW + .01f, "The row never squeezes past the bar's room: ${display.shownUnits}")
            if (display.scene == Scene.DeleteFlick) for (index in landed.indices) if (display.dotTravel(index) >= 1f) landed[index] = true
            now += 16
        }
        isTrue(landed.all { it }, "Every dot lands: ${landed.count { it }} of ${landed.size}")
        return now
    }
    val hold = GestureDisplay()
    hold.deleteHeld(80, 1_000)
    repeat(80) { hold.wordDeleted(1_000L + it * 80, 5 + it % 4) }
    isTrue(hold.deleteEnded(7_500), "A hold of eighty words turns into dots")
    same(hold.groups.size, 80, "Each of the eighty words keeps its own group")
    isTrue(hold.groups.all { it >= 1 }, "with a dot at least")
    landsAll(hold, 7_500)

    val flicks = GestureDisplay()
    var now = 1_000L
    var shown = 0
    repeat(100) {
        flicks.wordDeleted(now, 7)
        isTrue(flicks.dots > shown, "Flick ${it + 1} adds its dots: ${flicks.dots}")
        shown = flicks.dots
        same(flicks.chained, it > 0, "Every flick after the first joins the one stream")
        flicks.run(now, now + 184)
        now += 200
    }
    landsAll(flicks, now)
}

/** Each dot's landing, in real ms after the stream started. */
private fun lands(display: GestureDisplay) = (0 until display.dots).map { display.dotLandsAt(it) }

/** What a join waits before its first dot lands, in ms: the new dots pop in, wait, then travel into the caret. */
private val joinWait get() = GestureDisplay.FLICK_IN + GestureDisplay.flickHoldMillis() + GestureDisplay.DOT_TRAVEL

/** The dots land one after another, in order. (Their ticks keep 30 ms apart: see [GestureDisplay.dotLandings].) */
private fun inOrder(lands: List<Float>, what: String) =
    isTrue(lands.zipWithNext().all { (a, b) -> b > a }, "$what: every dot lands after the one before: $lands")

/**
 * A delete that joins starts the dots' wait again for the whole queue (owner, 2026-11-12): every dot not yet on its way holds
 * still, and from the join they wait the flick's wait, then all drain in order. A dot already on its way still lands.
 */
private fun aJoinStartsTheWaitAgain() {
    // A flick again at 300 ms: the first dot hasn't left yet, so everything waits the full wait from 300 ms.
    val early = GestureDisplay()
    early.wordDeleted(1_000, 5)
    early.run(1_000, 1_284)
    early.wordDeleted(1_300, 4)
    val after = lands(early)
    same(after.size, 9, "The four join the five")
    near(after.first(), 300f + joinWait, "The first dot lands a whole wait after the join", 1f)
    inOrder(after, "A join at 300 ms")

    // A flick again at 540 ms, into a long word's dots: one has landed, some are on their way, and the rest hold and go again
    // from the join's wait. (At 600 ms every dot of a 12-letter word has already set off with the 200 ms wait.)
    val late = GestureDisplay()
    late.wordDeleted(1_000, 12)
    val before = lands(late)
    late.run(1_000, 1_524)
    late.wordDeleted(1_540, 4)
    val now = lands(late)
    val going = before.indices.filter { before[it] - GestureDisplay.DOT_TRAVEL <= 540f }
    val waiting = before.indices.filter { before[it] - GestureDisplay.DOT_TRAVEL > 540f }
    isTrue(before.first() < 540f, "One dot has already landed: $before")
    isTrue(going.isNotEmpty() && waiting.isNotEmpty(), "At 540 ms some dots have set off and some haven't: $before")
    for (index in going) near(now[index], before[index], "Dot $index, already on its way, lands as it was going to", .5f)
    isTrue(waiting.all { now[it] >= 540f + joinWait - .5f }, "The dots still waiting go again from the join's wait: $now")
    near(now[waiting.first()], 540f + joinWait, "The first held dot lands a whole wait after the join", 1f)
    inOrder(now, "A join at 540 ms")
    same(now.size, 16, "The new four go last")

    // Three quick flicks: each join starts the wait again, so the whole queue waits from the last.
    val three = GestureDisplay()
    three.wordDeleted(1_000, 4)
    three.run(1_000, 1_134)
    three.wordDeleted(1_150, 3)
    three.run(1_150, 1_284)
    three.wordDeleted(1_300, 5)
    val all = lands(three)
    same(all.size, 12, "Three flicks' dots in one queue")
    near(all.first(), 300f + joinWait, "Everything waits from the third flick", 1f)
    inOrder(all, "Three quick flicks")
    val ticks = three.dotLandings(1f)
    isTrue(ticks.isNotEmpty() && ticks.first() >= (joinWait - 1).toLong(), "The whole queue's ticks go again from the last join: $ticks")
    isTrue(ticks.zipWithNext().all { (a, b) -> b - a >= 30 }, "Its ticks keep 30 ms apart: $ticks")
    // Every dot lands, in order, before the words come back.
    var at = 1_300L
    val landed = BooleanArray(12)
    while (three.scene == Scene.DeleteFlick && at < 5_000) {
        three.advance(at, 1f)
        if (three.scene == Scene.DeleteFlick) for (index in 0 until 12) if (three.dotTravel(index) >= 1f) landed[index] = true
        at += 16
    }
    isTrue(landed.all { it }, "Every one of the twelve lands: ${landed.toList()}")
}

/**
 * Deletes that come while the dots still play don't start them over or cut them: their dots join the end of the queue, and the
 * stream goes on, every dot landing and ticking 30 ms or more after the one before.
 */
private fun quickFlicksMakeOneStream() {
    val display = GestureDisplay()
    display.wordDeleted(1_000, 5)
    val first = display.dotLandings(1f).map { 1_000 + it }
    display.run(1_000, 1_240)
    val started = display.dotsAt
    display.wordDeleted(1_256, 4)
    same(display.scene, Scene.DeleteFlick, "A second flick keeps the dots going")
    same(display.chained, true, "It joins the stream")
    same(display.dotsAt, started, "The stream does not start over")
    same(display.dots, 9, "Its four dots join the five")
    same(display.groups.toList(), listOf(5, 4), "Each word keeps its own group, with a gap between")
    val second = display.dotLandings(1f).map { 1_256 + it }
    isTrue(second.size >= 4, "A tick for each dot still to land, the new ones too: $second")
    isTrue(second.first() > first.first(), "The join holds the dots back: $first, then $second")
    display.run(1_256, 1_500)
    display.wordDeleted(1_512, 7)
    same(display.dots, 16, "A third flick grows the queue again")
    val all = display.dotLandings(1f).map { 1_512 + it }
    isTrue(all.size >= 7, "Every dot still to land ticks: $all")
    isTrue(all.zipWithNext().all { (a, b) -> b - a >= 30 }, "Every dot lands 30 ms or more after the one before: $all")
    isTrue(all.first() > 1_512, "Nothing lands before the join")
    // Every dot lands, in order, before the words come back.
    val landed = BooleanArray(16)
    var now = 1_512L
    while (display.scene != Scene.None && now < 6_000) {
        display.advance(now, 1f)
        if (display.scene == Scene.DeleteFlick) for (index in 0 until 16) if (display.dotTravel(index) >= 1f) landed[index] = true
        now += 16
    }
    isTrue(landed.all { it }, "Every one of the sixteen dots lands: ${landed.toList()}")
    isTrue(now > all.last(), "The words come back only after the last dot")
    // After the stream is over, the next flick starts a new one.
    display.wordDeleted(now + 100, 3)
    same(display.chained, false, "A flick after the dots are over starts fresh")
    // Anything but a delete still cuts the stream at once.
    display.run(now + 100, now + 300)
    display.wordDeleted(now + 316, 2)
    display.interrupt()
    same(display.scene, Scene.None, "A key or a touch that is no delete cuts the whole stream")
    // With motion off there is no stream to join: each flick shows its own still picture.
    val still = GestureDisplay()
    still.wordDeleted(1_000, 3)
    still.advance(1_000, 0f)
    still.wordDeleted(1_100, 2)
    same(still.chained, false, "With motion off a flick starts its own dots")
}

/**
 * The experiment "Dots speed up when long": the first few dots keep their spacing, then each lands about 8% sooner than the one
 * before, down to 10 ms, so a long delete whooshes in. Ticks keep 30 ms apart by leaving some dots without one.
 */
private fun longDotsSpeedUpWhenTried() {
    val plain = GestureDisplay()
    plain.speedUp = false
    plain.wordDeleted(1_000, 30)
    val plainSpan = plain.dotLandsAt(29) - plain.dotLandsAt(0)
    val fast = GestureDisplay()
    fast.speedUp = true
    fast.wordDeleted(1_000, 30)
    val gaps = (1 until 30).map { fast.dotLandsAt(it) - fast.dotLandsAt(it - 1) }
    isTrue(gaps.take(3).all { kotlin.math.abs(it - 30f) < .01f }, "The first few keep 30 ms: $gaps")
    isTrue(gaps.drop(2).zipWithNext().all { (a, b) -> b <= a + .01f }, "Then each is no longer than the one before: $gaps")
    isTrue(kotlin.math.abs(gaps[3] - 30f * GestureDisplay.DOT_SPEED_UP) < .01f, "about 8% sooner each: ${gaps[3]}")
    isTrue(gaps.all { it >= GestureDisplay.DOT_FASTEST_GAP - .01f } && kotlin.math.abs(gaps.last() - 10f) < .01f, "down to 10 ms: $gaps")
    val span = fast.dotLandsAt(29) - fast.dotLandsAt(0)
    isTrue(span < plainSpan * .6f, "A long delete is over much sooner: $span ms against $plainSpan")
    val ticks = fast.dotLandings(1f)
    isTrue(ticks.zipWithNext().all { (a, b) -> b - a >= 30 }, "Ticks stay 30 ms apart or more: $ticks")
    isTrue(ticks.size in 10 until 30, "so some dots land without one: ${ticks.size} ticks for 30 dots")
    isTrue(kotlin.math.abs(ticks.first() - fast.dotLandsAt(0)) <= .5f, "The first dot ticks as it lands: ${ticks.first()}")
    // A few dots with it on keep a tick each, as their spacing stays over 30 ms.
    val few = GestureDisplay()
    few.speedUp = true
    few.wordDeleted(1_000, 4)
    same(few.dotLandings(1f).size, 4, "Four dots, four ticks")
    // On by default; off, the dots keep their even spacing.
    same(GestureDisplay().speedUp, true, "The experiment is on by default")
    isTrue((1 until 30).all { kotlin.math.abs(plain.dotLandsAt(it) - plain.dotLandsAt(it - 1) - 30f) < .01f }, "Off, thirty dots land 30 ms apart")
}

/**
 * The dots go into the caret over the owner's 450 ms while they fit, at least 30 ms apart. More dots stretch the way in, so each
 * lands on its own, and the haptic ticks with them come from the same numbers.
 */
private fun aFlicksWayInStretchesToFitItsDots() {
    for ((letters, out) in listOf(1 to 450f, 8 to 450f, 30 to 202.5f + 30 * 30)) {
        val display = GestureDisplay().apply { speedUp = false }
        display.wordDeleted(1_000, letters)
        val landings = display.dotLandings(1f)
        same(landings.size, letters, "$letters dots land one by one")
        isTrue(kotlin.math.abs(GestureDisplay.outMillis(letters, 1f) - out) < .01f, "$letters dots go in over ${out} ms")
        isTrue(landings.zipWithNext().all { (a, b) -> b - a >= 30 }, "$letters dots land at least 30 ms apart: $landings")
        // The drawing's dots land when the haptic says: each is just short of the caret a frame before, and in it after.
        display.run(1_000, 1_000 + landings.last() - 2)
        isTrue(display.dotTravel(letters - 1) < 1f, "$letters dots: the last isn't in yet")
        isTrue(letters == 1 || display.dotTravel(letters - 2) >= 1f, "$letters dots: the one before it is")
        display.run(1_000 + landings.last() + 2, 1_000 + landings.last() + 2)
        isTrue(display.dotTravel(letters - 1) >= 1f, "$letters dots: then the last lands")
        same(display.scene, Scene.DeleteFlick, "$letters dots: the words come back only after")
    }
    // Thirty dots land over about 900 ms, then the words come back.
    val thirty = GestureDisplay().apply { speedUp = false }
    thirty.wordDeleted(1_000, 30)
    val landings = thirty.dotLandings(1f)
    isTrue(landings.last() - landings.first() == 29L * 30, "Thirty dots land 30 ms apart: ${landings.first()} to ${landings.last()}")
    thirty.run(1_000, 1_000 + (GestureDisplay.FLICK_IN + GestureDisplay.FLICK_HOLD + GestureDisplay.outMillis(30, 1f) + GestureDisplay.FLICK_BACK).toLong() + 20)
    same(thirty.scene, Scene.None, "and it's over once the words are back")
    // Faster animations shrink the way in, but never below 30 ms a dot.
    val fast = GestureDisplay().apply { speedUp = false }
    fast.wordDeleted(1_000, 10)
    val quick = fast.dotLandings(.25f)
    isTrue(quick.zipWithNext().all { (a, b) -> b - a >= 30 }, "At a quarter speed the dots still land 30 ms apart: $quick")
    same(fast.dotLandings(0f), emptyList<Long>(), "With no motion the dots don't land")
    // New input ends it at once.
    fast.interrupt()
    same(fast.scene, Scene.None, "Any key or touch cuts the dots")
    same(fast.dotLandings(1f), emptyList<Long>(), "and they have nowhere left to land")
}

/** While steering, the underglow brightens on the side it leans to, more the faster it goes, and eases there with no jump. */
private fun theUnderglowLeansWithTheSteering() {
    val display = GestureDisplay()
    display.spaceTapped(1, 0L)
    display.cursorFrame(5f, 0, 0f, false, 100L)
    display.advance(400L, 1f)
    near(display.steerLean, 0f, "On Space it leans neither way")
    display.cursorFrame(8.5f, 1, CursorMode.SLOWEST_PER_SECOND, false, 400L)
    display.advance(416L, 1f)
    if (display.steerLean <= 0f || display.steerLean >= GestureDisplay.LEAN_BASE) throw AssertionError("It eases to the right, with no jump: ${display.steerLean}")
    var now = 416L
    while (now < 1200L) { now += 16; display.cursorFrame(8.5f, 1, CursorMode.SLOWEST_PER_SECOND, false, now); display.advance(now, 1f) }
    val slow = display.steerLean
    if (slow < GestureDisplay.LEAN_BASE - .01f || slow > .7f) throw AssertionError("A slow lean brightens its side part way: $slow")
    while (now < 2000L) { now += 16; display.cursorFrame(9.8f, 1, CursorMode.FASTEST_PER_SECOND, false, now); display.advance(now, 1f) }
    if (display.steerLean < .95f) throw AssertionError("A fast lean brightens it fully: ${display.steerLean}")
    while (now < 2800L) { now += 16; display.cursorFrame(1f, -1, CursorMode.FASTEST_PER_SECOND, false, now); display.advance(now, 1f) }
    if (display.steerLean > -.95f) throw AssertionError("Leaning left brightens the left: ${display.steerLean}")
    display.cursorPaused()
    display.advance(now + 1000L, 1f)
    near(display.steerLean, 0f, "A slip lets it go back to even")
    val still = GestureDisplay()
    still.cursorFrame(9.8f, 1, CursorMode.FASTEST_PER_SECOND, false, 0L)
    still.advance(1L, 0f)
    near(still.steerLean, 1f, "With animations off it shows the lean at once")
    still.cursorEnded()
    still.advance(2L, 0f)
    near(still.steerLean, 0f, "and none once steering is gone")
}

/** Steering's caret never blinks, not even when steering pauses (owner, 2026-11-12). Only a held delete's pause zone blinks. */
private fun steeringsCaretNeverBlinks() {
    val display = GestureDisplay()
    display.spaceTapped(1, 0L)
    display.cursorFrame(5f, 0, 0f, false, 100L)
    display.cursorText("The quick", " brown fox")
    // Resting on Space from the start, for many blinks' time.
    var now = 100L
    while (now < 100L + 6 * GestureDisplay.BLINK_MILLIS) {
        display.advance(now, 1f)
        if (!display.caretShown) throw AssertionError("The caret stays on while steering rests, at $now ms")
        now += 16
    }
    // Steering, then pausing again for a long while.
    val moving = now + 1800L
    while (now < moving) {
        now += 16
        display.cursorFrame(8.5f, 1, CursorMode.SLOWEST_PER_SECOND * 4, false, now)
        if (now % 160L == 0L) display.cursorStepped(1)
        display.advance(now, 1f)
        if (!display.caretShown) throw AssertionError("The caret stays on while steering, at $now ms")
    }
    display.cursorFrame(5f, 0, 0f, false, now)
    val paused = now + 6 * GestureDisplay.BLINK_MILLIS
    while (now < paused) {
        now += 16
        display.advance(now, 1f)
        if (!display.caretShown) throw AssertionError("Paused, steering's caret still never blinks, at $now ms")
    }
    // With animations off it stays on too.
    val still = GestureDisplay()
    still.cursorFrame(5f, 0, 0f, false, 0L)
    still.advance(GestureDisplay.BLINK_MILLIS + 10, 0f)
    if (!still.caretShown) throw AssertionError("With animations off the caret stays on")
}

/**
 * A clear delete flick shows the delete glow while the stroke goes on: along the bottom from the right corner to the finger,
 * following it, and fading at the lift (owner, 2026-11-12). A hold takes it on from where it reached, and then it follows the pace.
 */
private fun aFlicksGlowFollowsTheFinger() {
    val display = GestureDisplay()
    display.flickGlow(.8f, 1_000)
    same(display.glowDelete, true, "A flick's glow is the delete glow")
    same(display.glowing, true, "and shows")
    near(display.glowWidth, .2f, "It starts at the finger, not sweeping across", .001f)
    display.run(1_000, 1_016)
    near(display.glowAlpha, GestureDisplay.GLOW_HELD_ALPHA, "at full strength at once: a flick is quick", .01f)
    display.run(1_016, 1_600)
    near(display.glowAlpha, GestureDisplay.GLOW_HELD_ALPHA, "and stays so", .01f)
    display.flickGlow(.5f, 1_600)
    display.run(1_616, 1_632)
    isTrue(display.glowWidth > .2f && display.glowWidth < .5f, "It follows the finger smoothly: ${display.glowWidth}")
    display.run(1_648, 1_900)
    near(display.glowWidth, .5f, "and reaches it", .005f)
    // A swipe path that the stroke cancels leaves the glow with the stroke.
    display.swipePoint(.5f, .4f, 0, 1_900)
    display.swipeCancelled()
    same(display.glowing, true, "The glow stays with its stroke")
    // At the lift it stays where it reached and fades.
    display.flickGlowEnded(1_900)
    near(display.glowWidth, .5f, "It fades where it was", .005f)
    display.run(1_916, 1_916 + GestureDisplay.GLOW_FADE.toLong() + 100)
    same(display.glowing, false, "then goes")
    // A flick that becomes a hold: the glow carries on, and then follows the pace.
    val held = GestureDisplay()
    held.flickGlow(.6f, 2_000)
    held.run(2_016, 2_600)
    val alpha = held.glowAlpha
    held.deleteHeld(350, 2_600, pace = 0f)
    same(held.glowDelete, true, "The hold keeps the delete glow")
    near(held.glowAlpha, alpha, "with no jump", .01f)
    held.run(2_616, 3_200)
    near(held.glowWidth, GestureDisplay.DELETE_REACH_LEAST, "then reaches as far as its pace", .005f)
    // A hold with nothing deleted: no flick glow can start over it.
    held.flickGlow(.1f, 3_200)
    near(held.glowWidth, GestureDisplay.DELETE_REACH_LEAST, "A hold's glow is the pace's", .005f)
}

/**
 * Every delete flick shows its glow (owner, 2026-11-12). One so fast that it lifted before its glow had a frame, or before it was
 * clear while it went, plays the glow after the lift: from the corner to where the finger lifted, at full strength for
 * [GestureDisplay.FLICK_GLOW_SEEN], then fading.
 */
private fun everyFlickShowsItsGlow() {
    // Never clear while it went: no glow before the lift, then one at the lift.
    val unseen = GestureDisplay()
    same(unseen.glowing, false, "Nothing glows while the stroke wasn't clear")
    unseen.flickLifted(.6f, 1_000)
    same(unseen.glowDelete, true, "At the lift the delete glow shows")
    near(unseen.glowWidth, .4f, "from the corner to where the finger lifted", .001f)
    unseen.run(1_000, 1_000 + GestureDisplay.FLICK_GLOW_SEEN - 10)
    near(unseen.glowAlpha, GestureDisplay.GLOW_HELD_ALPHA, "at full strength for a moment", .001f)
    unseen.run(1_000 + GestureDisplay.FLICK_GLOW_SEEN, 1_000 + GestureDisplay.FLICK_GLOW_SEEN + 100)
    isTrue(unseen.glowAlpha < GestureDisplay.GLOW_HELD_ALPHA && unseen.glowing, "then it fades: ${unseen.glowAlpha}")
    unseen.run(1_300, 1_000 + GestureDisplay.FLICK_GLOW_SEEN + GestureDisplay.GLOW_FADE.toLong() + 50)
    same(unseen.glowing, false, "and goes")
    // Its glow started but lifted before a frame: it shows where the finger lifted, for the same moment.
    val early = GestureDisplay()
    early.flickGlow(.8f, 2_000)
    early.flickLifted(.55f, 2_010)
    near(early.glowWidth, .45f, "Where the finger lifted, not where the stroke was first clear", .001f)
    early.run(2_010, 2_000 + GestureDisplay.FLICK_GLOW_SEEN - 10)
    near(early.glowAlpha, GestureDisplay.GLOW_HELD_ALPHA, "It stays at full until it has shown long enough", .001f)
    // A flick whose glow showed all along fades at once at its lift, as before.
    val seen = GestureDisplay()
    seen.flickGlow(.7f, 3_000)
    seen.run(3_000, 3_200)
    seen.flickLifted(.5f, 3_200)
    seen.run(3_216, 3_232)
    isTrue(seen.glowAlpha < GestureDisplay.GLOW_HELD_ALPHA, "A glow that showed long enough fades at the lift: ${seen.glowAlpha}")
    // The flick's dots don't put out the late glow.
    val dots = GestureDisplay()
    dots.flickLifted(.6f, 4_000)
    dots.wordDeleted(4_020, 4)
    same(dots.scene, Scene.DeleteFlick, "The dots play")
    dots.run(4_020, 4_000 + GestureDisplay.FLICK_GLOW_SEEN - 10)
    near(dots.glowAlpha, GestureDisplay.GLOW_HELD_ALPHA, "with the late glow still at full", .001f)
    // A hold's glow just fades at its lift.
    val hold = GestureDisplay()
    hold.deleteHeld(350, 5_000, pace = 0f)
    hold.run(5_000, 5_400)
    hold.flickLifted(.5f, 5_400)
    hold.run(5_416, 5_416 + GestureDisplay.GLOW_FADE.toLong() + 50)
    same(hold.glowing, false, "A hold's glow fades at its lift, with no late glow")
    // With animations off nothing glows on after the lift.
    val still = GestureDisplay()
    still.flickLifted(.6f, 6_000)
    still.advance(6_016, 0f)
    same(still.glowing, false, "With animations off the late glow doesn't hang on")
}
