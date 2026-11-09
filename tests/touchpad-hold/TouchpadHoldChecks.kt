package com.atlas.keyboard

import com.atlas.keyboard.TouchpadHold.Back

fun main() {
    holdsOnlyWithTheSettingAndNoField()
    needsTheOverlayAndTheBackService()
    waitsAfterAHide()
    doesNotReportFasterThanTheGap()
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

private fun waitsAfterAHide() {
    val hold = TouchpadHold()
    same(hold.delay(1_000_000), TouchpadHold.SETTLE_MILLIS, "The first report still waits for the hides to settle")
}

private fun doesNotReportFasterThanTheGap() {
    val hold = TouchpadHold()
    hold.shown(1_000)
    same(hold.delay(1_050), TouchpadHold.MIN_GAP_MILLIS - 50, "A hide just after a report must wait out the gap")
    same(hold.delay(1_000 + TouchpadHold.MIN_GAP_MILLIS), TouchpadHold.SETTLE_MILLIS, "After the gap only the short wait stays")
    same(hold.delay(9_999), TouchpadHold.SETTLE_MILLIS, "A long time later only the short wait stays")
}

private fun heldFollowsShownAndHidden() {
    val hold = TouchpadHold()
    same(hold.held, false, "A new keyboard holds nothing")
    hold.shown(10)
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
        "The handler's release must hide the window and pass Back to the app")
}
