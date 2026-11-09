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
