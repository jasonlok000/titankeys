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
    display.wordDeleted(1_000)
    display.run(1_000, 1_100)
    display.deleteEnded()
    display.run(1_116, 1_400)
    same(display.scene, Scene.None, "The display must go after the hold")
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
    display.wordDeleted(1_000)
    same(display.scene, Scene.DeleteFlick, "A flick must show a flourish")
    same(display.showing, true, "The flourish must cover the words")
    display.run(1_000, 1_100)
    isTrue(display.flourish > 0f, "The flourish must move")
    isTrue(display.reveal > 0f, "The flourish must show")
    same(display.softDelete, true, "A single flick's word lifts off and dissolves")
    isTrue(display.lift > 0f && display.lift < 1f, "At 100 ms the word is still lifting off")
    display.run(1_116, 1_280)
    same(display.scene, Scene.DeleteFlick, "At 280 ms it still shows, long enough to see clearly")
    isTrue(display.lift > .7f, "By then it has nearly dissolved")
    isTrue(display.shownDeleted > .8f, "The words before it have nearly closed the gap by then")
    display.run(1_296, 1_376)
    same(display.scene, Scene.None, "It is over by about 360 ms, and it is only a picture, so typing never waits for it")
}

private fun aDoubleTapHasABriefConfirmation() {
    val display = GestureDisplay()
    display.wordSelected(1_000)
    same(display.scene, Scene.WordSelected, "A selected word must show a confirmation")
    display.run(1_000, 1_100)
    isTrue(display.spanEnd > 0f, "The selection must open over the word")
    display.run(1_116, 1_260)
    same(display.scene, Scene.None, "The confirmation must be over by 260 ms")
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
    near(display.shownDeleted, 1f, "With motion off the word must already be gone")
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
