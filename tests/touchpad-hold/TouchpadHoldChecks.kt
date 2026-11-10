package com.atlas.keyboard

import com.atlas.keyboard.TouchpadHold.Back

fun main() {
    holdsOnlyWithTheSettingAndNoField()
    needsTheOverlayAndTheBackService()
    aHideLeavesTheStatusOnWhileItIsWanted()
    aHideEndsTheStatusWhenItIsNotWanted()
    reportsAtOnce()
    waitsOnlyForABackOnItsWay()
    heldFollowsShownAndHidden()
    aRealBackKeyGoesToTheApp()
    aBackFromTheHandlerHidesAndPasses()
    println("Touchpad hold checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private fun holdsOnlyWithTheSettingAndNoField() {
    same(TouchpadHold.wanted(settingOn = true, editing = false, overlayUp = true, backServiceOn = true), true,
        "With the setting on and no field, the status must stay on")
    same(TouchpadHold.wanted(settingOn = false, editing = false, overlayUp = true, backServiceOn = true), false,
        "With the setting off, nothing must change")
    same(TouchpadHold.wanted(settingOn = true, editing = true, overlayUp = true, backServiceOn = true), false,
        "In a field the strip shows the keyboard, so there is nothing to hold")
}

private fun needsTheOverlayAndTheBackService() {
    same(TouchpadHold.wanted(settingOn = true, editing = false, overlayUp = false, backServiceOn = true), false,
        "Without the overlay nothing stops the touchpad events")
    same(TouchpadHold.wanted(settingOn = true, editing = false, overlayUp = true, backServiceOn = false), false,
        "Without the Back service, Back would be lost in newer apps")
}

/** The hides that a real log showed at each close: the strip's own, and the ones that Android sends after it. */
private fun aHideLeavesTheStatusOnWhileItIsWanted() {
    same(TouchpadHold.keepsStatus(wanted = true, windowUp = true), true,
        "A hide with no field must not turn the status off, or the touchpad scrolls the app")
}

private fun aHideEndsTheStatusWhenItIsNotWanted() {
    same(TouchpadHold.keepsStatus(wanted = false, windowUp = true), false, "A hide with a field must go through the normal way")
    same(TouchpadHold.keepsStatus(wanted = true, windowUp = false), false, "With no window up there is no status to keep")
}

private fun reportsAtOnce() {
    val hold = TouchpadHold()
    same(hold.delay(1_000_000), 0L, "A status that is off must come back at once")
    hold.shown()
    hold.hidden()
    same(hold.delay(1_000_050), 0L, "A hide straight after a report must not make the next report wait")
}

private fun waitsOnlyForABackOnItsWay() {
    val hold = TouchpadHold()
    hold.backPassed(1_000)
    same(hold.delay(1_000), TouchpadHold.BACK_MILLIS, "The status must wait for the system back to reach the app")
    same(hold.delay(1_060), TouchpadHold.BACK_MILLIS - 60, "Only the rest of the wait stays")
    same(hold.delay(1_000 + TouchpadHold.BACK_MILLIS), 0L, "After the wait the status comes back at once")
    same(hold.delay(9_999), 0L, "A long time later nothing waits")
}

private fun heldFollowsShownAndHidden() {
    val hold = TouchpadHold()
    same(hold.held, false, "A new keyboard holds nothing")
    hold.shown()
    same(hold.held, true, "A report starts the hold")
    hold.hidden()
    same(hold.held, false, "A hide ends the hold")
}

private fun aRealBackKeyGoesToTheApp() {
    same(TouchpadHold.backStep(fromKey = true, down = true), Back.ToApp, "A push of the Back key must reach the app")
    same(TouchpadHold.backStep(fromKey = true, down = false), Back.ToApp, "The release of the Back key must reach the app")
}

private fun aBackFromTheHandlerHidesAndPasses() {
    same(TouchpadHold.backStep(fromKey = false, down = true), Back.Keep, "The handler's first event waits for the release")
    same(TouchpadHold.backStep(fromKey = false, down = false), Back.KeepAndHide,
        "The handler's release must end the status and pass Back to the app")
}
