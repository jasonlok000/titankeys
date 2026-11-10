package com.atlas.keyboard

import android.view.KeyEvent

private const val KEY = KeyEvent.KEYCODE_A
private const val CTRL = KeyEvent.KEYCODE_CTRL_LEFT
/** One key width and one key row on the touchpad, which runs from 0 to 1 on each axis. */
private const val KEY_WIDTH = .1f
private const val ROW_HEIGHT = .25f

fun main() {
    typingBlocksATouch()
    panelTakesATouchDuringAKey()
    panelTakesATouchAfterAKey()
    panelSwipe()
    panelShortSwipe()
    panelSlowPath()
    keyPressCancelsPanelSwipe()
    touchWhileCtrlIsHeldDoesNotType()
    restTypesNothing()
    restThatRollsTypesNothing()
    restThatMovesFarCounts()
    slowSwipeThatStartsAtOnceCounts()
    pauseInASwipeStaysASwipe()
    quickTapStillTaps()
    tapAtTheSameSpotAfterARestStillTaps()
    returningFingerIsARest()
    repeatedTouchesOfAReturningFingerStayARest()
    returningFingerAfterAKeyIsARest()
    aKeyDuringAStillRestRemembersIt()
    swipeStartsJustAfterARestLifts()
    swipeStartsJustAfterAnotherSwipe()
    oneSwitchIsNotABurst()
    aSwapDropsTheCutShortTap()
    aRepeatOneFrameLaterDoesNotTapAgain()
    twoFingersHeldDownTypeNothing()
    longContactsInARowAreNotTwoFingersHeldDown()
    panelHoverKeepsARestingFinger()
    swipesOffTypeNothing()
    swipesOffKeepTapsAndThePanel()
    println("Contact checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private fun at(x: Float, y: Float, time: Long) = Sample(x, y, time)

/** Lands at the first point, moves through the rest, lifts, and returns the settled result. */
private fun Contact.touch(vararg points: Sample): Contact.Outcome? {
    down(points.first())
    move(points.drop(1))
    val lifted = points.last().time
    val settlement = up(emptyList(), lifted) ?: return null
    return settle(settlement, settlement.deadline)
}

private fun typingBlocksATouch() {
    val contact = Contact()
    contact.key(KEY, true, 1_000)
    contact.down(at(.25f, .4f, 1_010))
    same(contact.travel, null, "A touch must be ignored while a key is held for typing")
}

private fun panelTakesATouchDuringAKey() {
    val contact = Contact()
    contact.paging = true
    contact.key(KeyEvent.KEYCODE_SYM, true, 1_000)
    contact.down(at(.25f, .4f, 1_010))
    same(contact.travel, 0f, "The panel must take a resting finger while a key is held")
}

private fun panelTakesATouchAfterAKey() {
    val contact = Contact()
    contact.paging = true
    contact.key(KeyEvent.KEYCODE_SYM, true, 1_000)
    contact.key(KeyEvent.KEYCODE_SYM, false, 1_050)
    val start = at(.25f, .4f, 1_060)
    val outcome = contact.touch(start, at(.25f + KEY_WIDTH, .4f, 1_160))
    same(outcome is Contact.Outcome.Swipe, true, "A swipe in the panel must count just after Sym")
}

private fun panelSwipe() {
    val contact = Contact()
    contact.paging = true
    val down = contact.touch(at(.5f, .2f, 1_000), at(.5f, .2f + ROW_HEIGHT, 1_200))
    same(down is Contact.Outcome.Swipe, true, "A swipe down one row must count")
    val across = contact.touch(at(.5f, .4f, 2_000), at(.5f - KEY_WIDTH, .4f, 2_200))
    same(across is Contact.Outcome.Swipe, true, "A swipe across one key must count")
    val limit = contact.touch(at(.5f, .4f, 3_000), at(.5f - KEY_WIDTH, .4f, 3_000 + Contact.PAGE_MILLIS))
    same(limit is Contact.Outcome.Swipe, true, "A swipe that takes the full page time must count")
}

private fun panelShortSwipe() {
    val contact = Contact()
    contact.paging = true
    val outcome = contact.touch(at(.5f, .4f, 1_000), at(.5f + KEY_WIDTH * Contact.PAGE_KEYS * .9f, .4f, 1_100))
    same(outcome, null, "A small movement in the panel must do nothing")
    val rest = contact.touch(at(.5f, .4f, 2_000), at(.5f, .4f, 2_900))
    same(rest, null, "A resting finger in the panel must do nothing when it lifts")
}

private fun panelSlowPath() {
    val contact = Contact()
    contact.paging = true
    val outcome = contact.touch(at(.2f, .4f, 1_000), at(.3f, .4f, 1_400), at(.6f, .4f, 1_000 + Contact.PAGE_MILLIS + 1))
    same(outcome, null, "A finger that moves slowly from key to key must not change the page")
}

private fun keyPressCancelsPanelSwipe() {
    val contact = Contact()
    contact.paging = true
    contact.down(at(.2f, .4f, 1_000))
    contact.move(listOf(at(.4f, .4f, 1_100)))
    val settlement = contact.up(emptyList(), 1_100)!!
    contact.key(KEY, true, 1_110)
    same(contact.settle(settlement, settlement.deadline), null, "Pushing a key as the finger lifts must not also change the page")
}

private fun touchWhileCtrlIsHeldDoesNotType() {
    val contact = Contact()
    contact.key(CTRL, true, 1_000)
    contact.down(at(.25f, .4f, 1_100))
    same(contact.travel, null, "A touch while Ctrl is held must not type")
}

private fun restTypesNothing() {
    val contact = Contact()
    val outcome = contact.touch(at(.05f, .4f, 1_000), at(.05f, .4f, 1_400), at(.05f, .4f, 3_000))
    same(outcome, null, "A finger that rests on a key must type nothing when it lifts")
}

private fun restThatRollsTypesNothing() {
    val contact = Contact()
    // Still at the time limit, then a roll that would be a swipe from a finger that was already moving.
    val outcome = contact.touch(at(.5f, .4f, 1_000), at(.51f, .4f, 1_400), at(.51f, .4f, 1_600), at(.58f, .4f, 2_500), at(.58f, .4f, 2_600))
    same(outcome, null, "A resting finger that rolls less than the far limit must type nothing")
}

private fun restThatMovesFarCounts() {
    val contact = Contact()
    val outcome = contact.touch(at(.2f, .4f, 1_000), at(.2f, .4f, 1_700), at(.3f, .4f, 2_000), at(.4f, .4f, 2_300))
    same(outcome is Contact.Outcome.Swipe, true, "A resting finger that moves far must count")
}

private fun slowSwipeThatStartsAtOnceCounts() {
    val contact = Contact()
    // Moves past the rest limit before the time limit, then keeps going slowly.
    val outcome = contact.touch(at(.2f, .4f, 1_000), at(.25f, .4f, 1_300), at(.3f, .4f, 2_500), at(.35f, .4f, 4_000))
    same(outcome is Contact.Outcome.Swipe, true, "A slow swipe that starts to move at once must count")
}

private fun pauseInASwipeStaysASwipe() {
    val contact = Contact()
    val points = arrayOf(at(.2f, .4f, 1_000), at(.3f, .4f, 1_150), at(.3f, .4f, 2_000), at(.4f, .4f, 2_200))
    val outcome = contact.touch(*points)
    same(outcome is Contact.Outcome.Swipe, true, "A swipe that pauses on a key must stay a swipe")
}

private fun quickTapStillTaps() {
    val contact = Contact()
    val quick = contact.touch(at(.05f, .4f, 1_000), at(.05f, .4f, 1_080))
    same(quick is Contact.Outcome.Tap, true, "A quick tap must tap")
    val slower = contact.touch(at(.05f, .4f, 3_000), at(.05f, .4f, 3_340))
    same(slower is Contact.Outcome.Tap, true, "The longest deliberate tap in the raw log must tap")
}

private fun tapAtTheSameSpotAfterARestStillTaps() {
    val contact = Contact()
    same(contact.touch(at(.5f, .85f, 1_000), at(.5f, .85f, 1_700)), null, "A long touch is a rest")
    val tap = contact.touch(at(.5f, .85f, 1_900), at(.5f, .85f, 1_980))
    same(tap is Contact.Outcome.Tap, true, "A second touch at the same spot, with nothing between, must tap")
    // A key or another touch came before this new rest, but nothing has come since it.
    contact.key(KEY, true, 2_500)
    contact.key(KEY, false, 2_550)
    same(contact.touch(at(.5f, .85f, 5_000), at(.5f, .85f, 5_700)), null, "A long touch is a rest")
    val after = contact.touch(at(.5f, .85f, 5_900), at(.5f, .85f, 5_980))
    same(after is Contact.Outcome.Tap, true, "A new rest must start with nothing between it and the next touch")
}

private fun returningFingerIsARest() {
    val contact = Contact()
    same(contact.touch(at(.07f, .85f, 1_000), at(.07f, .85f, 2_000)), null, "A long touch is a rest")
    val swipe = contact.touch(at(.6f, .3f, 2_050), at(.8f, .3f, 2_250))
    same(swipe is Contact.Outcome.Swipe, true, "Another finger must still swipe")
    contact.down(at(.07f, .84f, 2_300))
    same(contact.travel, null, "A finger that returns to a rest must be a rest from when it lands")
    contact.move(listOf(at(.1f, .84f, 2_350)))
    val settlement = contact.up(emptyList(), 2_350)
    same(settlement?.counts, false, "A finger that returns to a rest must not join a swipe")
    same(settlement?.let { contact.settle(it, it.deadline) }, null, "A finger that returns to a rest must type nothing")
    val tapElsewhere = contact.touch(at(.5f, .4f, 2_500), at(.5f, .4f, 2_580))
    same(tapElsewhere is Contact.Outcome.Tap, true, "A touch elsewhere must not count as the resting finger")
}

private fun repeatedTouchesOfAReturningFingerStayARest() {
    val contact = Contact()
    same(contact.touch(at(.07f, .85f, 1_000), at(.07f, .85f, 2_000)), null, "A long touch is a rest")
    same(contact.touch(at(.6f, .3f, 2_010), at(.8f, .3f, 2_210)) is Contact.Outcome.Swipe, true, "Another finger must still swipe")
    // A light grip is reported as short contacts at the same spot, with small gaps and nothing between them.
    for (start in listOf(2_260L, 2_420L, 2_660L)) {
        contact.down(at(.07f, .86f, start))
        same(contact.travel, null, "A finger that returns again and again must stay a rest")
        val settlement = contact.up(listOf(at(.07f, .86f, start + 30)), start + 30)
        same(settlement?.let { contact.settle(it, it.deadline) }, null, "A finger that returns again and again must type nothing")
    }
}

private fun returningFingerAfterAKeyIsARest() {
    val contact = Contact()
    contact.down(at(.07f, .85f, 1_000))
    contact.move(listOf(at(.07f, .85f, 1_700)))
    // The driver powers the touchpad off for the key, so the resting finger comes back as a new contact.
    contact.key(KEY, true, 1_800)
    same(contact.up(emptyList(), 1_801), null, "A key press must end the contact")
    contact.key(KEY, false, 1_850)
    contact.down(at(.07f, .85f, 2_400))
    same(contact.travel, null, "A rest reported again after a key must not type")
}

private fun aKeyDuringAStillRestRemembersIt() {
    val contact = Contact()
    contact.down(at(.07f, .85f, 1_000))
    // A finger that does not move sends nothing, so only the key press shows how long it has rested.
    contact.key(KEY, true, 1_800)
    same(contact.up(emptyList(), 1_801), null, "A key press must end the contact")
    contact.key(KEY, false, 1_850)
    contact.down(at(.07f, .85f, 2_400))
    same(contact.travel, null, "A rest that sent no moves must still be a rest after a key")
}

private fun swipeStartsJustAfterARestLifts() {
    val contact = Contact()
    same(contact.touch(at(.07f, .85f, 1_000), at(.07f, .85f, 2_000)), null, "A long touch is a rest")
    val swipe = contact.touch(at(.6f, .3f, 2_010), at(.8f, .3f, 2_210))
    same(swipe is Contact.Outcome.Swipe, true, "A swipe that starts one frame after a rest lifts must count")
}

private fun swipeStartsJustAfterAnotherSwipe() {
    val contact = Contact()
    val first = contact.touch(at(.2f, .3f, 1_000), at(.4f, .3f, 1_200))
    val next = contact.touch(at(.8f, .3f, 1_210), at(.6f, .3f, 1_400))
    same(first is Contact.Outcome.Swipe && next is Contact.Outcome.Swipe, true, "One switch of finger must not stop a swipe")
}

private fun oneSwitchIsNotABurst() {
    val contact = Contact()
    contact.down(at(.25f, .5f, 1_000))
    val cutShort = contact.up(listOf(at(.25f, .5f, 1_030)), 1_030)!!
    contact.down(at(.75f, .4f, 1_040))
    contact.move(listOf(at(.65f, .4f, 1_140)))
    val swipe = contact.up(listOf(at(.55f, .4f, 1_240)), 1_240)!!
    same(contact.settle(cutShort, cutShort.deadline), null, "A short touch cut off by another finger must not tap")
    same(contact.settle(swipe, swipe.deadline) is Contact.Outcome.Swipe, true, "A swipe after one switch of finger must count")
}

private fun aSwapDropsTheCutShortTap() {
    val contact = Contact()
    contact.down(at(.05f, .4f, 1_000))
    val settlement = contact.up(listOf(at(.05f, .4f, 1_080)), 1_080)!!
    contact.down(at(.55f, .4f, 1_090))
    same(contact.settle(settlement, settlement.deadline), null, "A tap cut short by another finger must not type")
}

private fun aRepeatOneFrameLaterDoesNotTapAgain() {
    val contact = Contact()
    val first = contact.touch(at(.05f, .4f, 1_000), at(.05f, .4f, 1_080))
    same(first is Contact.Outcome.Tap, true, "The first touch taps")
    same(contact.touch(at(.05f, .4f, 1_090), at(.05f, .4f, 1_120)), null, "The touchpad reporting one finger again must not tap again")
}

private fun twoFingersHeldDownTypeNothing() {
    val contact = Contact()
    // The touchpad flips between two fingers: each contact is short and the next lands one frame later,
    // before the contact it cut short has settled.
    var time = 1_000L
    val waiting = ArrayDeque<Contact.Settlement>()
    val outcomes = ArrayList<Contact.Outcome?>()
    fun settleUntil(now: Long) {
        while (waiting.isNotEmpty() && waiting.first().deadline <= now) waiting.removeFirst().let { outcomes += contact.settle(it, it.deadline) }
    }
    val settlements = List(12) { index ->
        val x = if (index % 2 == 0) .25f else .75f
        // Inside a run, a contact can look like a swipe towards the other finger.
        val phantom = index == 1 || index == 7
        val end = at(if (phantom) x - .14f else x, .5f, time + if (phantom) 90 else 20)
        settleUntil(time)
        contact.down(at(x, .5f, time))
        contact.move(listOf(end))
        val settlement = contact.up(emptyList(), end.time)?.also { waiting.addLast(it) }
        time = end.time + 10
        settlement
    }
    settleUntil(Long.MAX_VALUE)
    same(outcomes.filterNotNull(), emptyList<Contact.Outcome>(), "Two fingers held down must not tap or swipe")
    same(settlements.drop(2).filterNotNull(), emptyList<Contact.Settlement>(), "A contact inside the burst must not be able to join a swipe")
}

private fun longContactsInARowAreNotTwoFingersHeldDown() {
    val contact = Contact()
    // Long contacts one frame apart, far from each other: one finger follows another, so all of them count.
    val first = contact.touch(at(.1f, .3f, 1_000), at(.5f, .3f, 1_400))
    val second = contact.touch(at(.9f, .3f, 1_410), at(.3f, .3f, 3_000))
    val third = contact.touch(at(.8f, .3f, 3_010), at(.7f, .3f, 3_400))
    same(listOf(first, second, third).all { it is Contact.Outcome.Swipe }, true, "Long contacts in a row must each still count")
}

private fun panelHoverKeepsARestingFinger() {
    val contact = Contact()
    contact.paging = true
    contact.down(at(.25f, .4f, 1_000))
    contact.move(listOf(at(.25f, .4f, 2_000)))
    same(contact.travel, 0f, "The panel must keep a finger that rests past the time limit")
    same(contact.up(emptyList(), 2_001)?.let { contact.settle(it, it.deadline) }, null, "A finger that hovered must do nothing when it lifts")
    contact.down(at(.25f, .4f, 2_100))
    same(contact.travel, 0f, "A rest must not carry over to the next touch in the panel")
}

private fun swipesOffTypeNothing() {
    val on = Contact()
    val word = at(.15f, .4f, 1_000) to at(.45f, .4f, 1_200)
    val flick = at(.95f, .35f, 2_000) to at(.7f, .35f, 2_120)
    same(on.touch(word.first, word.second) is Contact.Outcome.Swipe, true, "A swipe must count while swipe typing is on")
    val deletes = on.touch(flick.first, flick.second) as? Contact.Outcome.Swipe
    same(deletes?.deletesWord, true, "A flick left from Backspace must delete while swipe typing is on")
    val off = Contact()
    off.swipes = false
    same(off.touch(word.first, word.second), null, "A swipe must type nothing while swipe typing is off")
    same(off.touch(flick.first, flick.second), null, "A flick left from Backspace must delete nothing while swipe typing is off")
}

private fun swipesOffKeepTapsAndThePanel() {
    val contact = Contact()
    contact.swipes = false
    val tap = contact.touch(at(.05f, .4f, 1_000), at(.05f, .4f, 1_080))
    same(tap is Contact.Outcome.Tap, true, "A tap must still tap while swipe typing is off")
    contact.paging = true
    val page = contact.touch(at(.5f, .2f, 3_000), at(.5f, .2f + ROW_HEIGHT, 3_200))
    same(page is Contact.Outcome.Swipe, true, "A swipe must still turn the panel's page while swipe typing is off")
    contact.down(at(.25f, .4f, 4_000))
    same(contact.travel, 0f, "A finger must still rest on the panel while swipe typing is off")
}
