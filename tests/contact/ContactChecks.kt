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
    touchWaitsForTheCtrlOverlay()
    ctrlOverlayTakesATouch()
    ctrlOverlayDropsATouchWhenItCloses()
    restTypesNothing()
    restThatRollsTypesNothing()
    restThatMovesFarCounts()
    slowSwipeThatStartsAtOnceCounts()
    pauseInASwipeStaysASwipe()
    quickTapStillTaps()
    tapAtTheSameSpotAfterARestStillTaps()
    returningFingerIsARest()
    returningFingerAfterAKeyIsARest()
    swipeStartsJustAfterARestLifts()
    swipeStartsJustAfterAnotherSwipe()
    aSwapDropsTheCutShortTap()
    aRepeatOneFrameLaterDoesNotTapAgain()
    twoFingersHeldDownTypeNothing()
    longContactsInARowAreNotTwoFingersHeldDown()
    panelHoverKeepsARestingFinger()
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

private fun touchWaitsForTheCtrlOverlay() {
    val contact = Contact()
    contact.key(CTRL, true, 1_000)
    contact.down(at(.25f, .4f, 1_100))
    same(contact.travel, null, "A touch while Ctrl waits for the overlay must not type")
}

private fun ctrlOverlayTakesATouch() {
    val contact = Contact()
    contact.key(CTRL, true, 1_000)
    contact.paging = true
    contact.down(at(.25f, .4f, 1_250))
    same(contact.travel, 0f, "The overlay must take a resting finger while Ctrl is held")
    contact.move(listOf(at(.25f, .4f, 1_700)))
    val settlement = contact.up(emptyList(), 1_800)
    same(settlement?.let { contact.settle(it, it.deadline) }, null, "A resting finger in the overlay must do nothing when it lifts")
}

private fun ctrlOverlayDropsATouchWhenItCloses() {
    // AtlasIme cancels the contact when Ctrl is let go. The finger then lifts with the overlay closed.
    val contact = Contact()
    contact.key(CTRL, true, 1_000)
    contact.paging = true
    contact.down(at(.25f, .4f, 1_250))
    contact.move(listOf(at(.65f, .4f, 1_400)))
    contact.key(CTRL, false, 1_500)
    contact.paging = false
    contact.cancel()
    same(contact.up(emptyList(), 1_600), null, "A finger that rested on the overlay must not type when it lifts")
}

private fun restTypesNothing() {
    val contact = Contact()
    val outcome = contact.touch(at(.05f, .4f, 1_000), at(.05f, .4f, 1_400), at(.05f, .4f, 3_000))
    same(outcome, null, "A finger that rests on a key must type nothing when it lifts")
}

private fun restThatRollsTypesNothing() {
    val contact = Contact()
    // Still at the time limit, then a roll that would be a swipe from a finger that was already moving.
    val outcome = contact.touch(at(.5f, .4f, 1_000), at(.51f, .4f, 1_400), at(.51f, .4f, 1_600), at(.58f, .4f, 2_500))
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
    same(settlement?.let { contact.settle(it, it.deadline) }, null, "A finger that returns to a rest must type nothing")
    val tapElsewhere = contact.touch(at(.5f, .4f, 2_500), at(.5f, .4f, 2_580))
    same(tapElsewhere is Contact.Outcome.Tap, true, "A touch elsewhere must not count as the resting finger")
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
    // The touchpad flips between two fingers: each contact is short and the next lands one frame later.
    var time = 1_000L
    val outcomes = List(12) { index ->
        val x = if (index % 2 == 0) .25f else .75f
        // Inside a run, a contact can look like a swipe towards the other finger.
        val end = if (index == 7) x - .14f else x
        val points = arrayOf(at(x, .5f, time), at(end, .5f, time + if (index == 7) 90 else 20))
        time = points.last().time + 10
        contact.touch(*points)
    }
    // The first contact and the first swap look like ordinary input.
    same(outcomes.drop(2).filterNotNull(), emptyList<Contact.Outcome>(), "Two fingers held down must not tap or swipe")
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
