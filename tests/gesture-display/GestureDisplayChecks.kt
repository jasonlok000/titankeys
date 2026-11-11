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
    same(long.dots, GestureDisplay.MAX_DOTS, "A long hold shows about 30 dots at most")
    same(GestureDisplay.MAX_DOTS, 30, "The most dots is 30")
    same(long.groups.toList(), listOf(7, 7, 7, 7, 2), "The nearest words show first, and the last one is cut")

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
    same(display.reveal, 1f, "The cover is in at once, so the old words never show through")
    same(display.groups.toList(), listOf(5), "One word, one group")
    display.run(1_080, 1_310)
    same(display.dotsIn, 1f, "The dots are all in after 120 ms")
    same(display.dotsOut, 0f, "and they stay a while, about 200 ms")
    same(display.reveal, 1f, "while the cover stays")
    display.run(1_326, 1_760)
    isTrue(display.dotsOut > 0f && display.dotsOut < 1f, "Then they go into the caret, over about 450 ms")
    same(display.reveal, 1f, "The words stay covered while the dots go")
    display.run(1_776, 1_910)
    isTrue(display.reveal < 1f && display.reveal > 0f, "Then the words fade back in, over about 150 ms")
    display.run(1_926, 1_940)
    same(display.scene, Scene.None, "It is over by about 0.92 s, and it is only a picture, so typing never waits for it")
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
 * The dots go into the caret over the owner's 450 ms while they fit, at least 30 ms apart. More dots stretch the way in, so each
 * lands on its own, and the haptic ticks with them come from the same numbers.
 */
private fun aFlicksWayInStretchesToFitItsDots() {
    for ((letters, out) in listOf(1 to 450f, 8 to 450f, 30 to 202.5f + 30 * 30)) {
        val display = GestureDisplay()
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
    val thirty = GestureDisplay()
    thirty.wordDeleted(1_000, 30)
    val landings = thirty.dotLandings(1f)
    isTrue(landings.last() - landings.first() == 29L * 30, "Thirty dots land 30 ms apart: ${landings.first()} to ${landings.last()}")
    thirty.run(1_000, 1_000 + (GestureDisplay.FLICK_IN + GestureDisplay.FLICK_HOLD + GestureDisplay.outMillis(30, 1f) + GestureDisplay.FLICK_BACK).toLong() + 20)
    same(thirty.scene, Scene.None, "and it's over once the words are back")
    // Faster animations shrink the way in, but never below 30 ms a dot.
    val fast = GestureDisplay()
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
