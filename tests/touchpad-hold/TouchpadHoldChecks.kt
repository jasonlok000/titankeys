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
    aSystemBackGoesToTheApp()
    theHandlerMarkItsOwnEvents()
    aBackFromTheHandlerHidesAndPasses()
    oneRealPushIsOneSystemBack()
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

// The Titan 2 Elite's Back key, a system back from the Back service, and Android's handler for a shown keyboard, as a real log had them.
private const val KEY_DEVICE = 0
private const val NO_DEVICE = -1
private const val KEY_FLAGS = 0xc
private const val SYSTEM_BACK_FLAGS = 0x8
private const val CANCELED_SYSTEM_BACK_FLAGS = 0x28
private const val HANDLER_FLAGS = 0x48
private const val HANDLER_TRACKING_FLAGS = 0x248

private fun aRealBackKeyGoesToTheApp() {
    same(TouchpadHold.fromHandler(KEY_DEVICE, KEY_FLAGS), false, "The Back key is not the handler")
    same(TouchpadHold.backStep(fromHandler = false, down = true), Back.ToApp, "A push of the Back key must reach the app")
    same(TouchpadHold.backStep(fromHandler = false, down = false), Back.ToApp, "The release of the Back key must reach the app")
}

/** The keyboard's own system back comes past the keyboard on its way to the app. Hiding again for it would be a second Back. */
private fun aSystemBackGoesToTheApp() {
    same(TouchpadHold.fromHandler(NO_DEVICE, SYSTEM_BACK_FLAGS), false, "A system back has no device but is not the handler")
    same(TouchpadHold.fromHandler(NO_DEVICE, CANCELED_SYSTEM_BACK_FLAGS), false, "A system back that the app already used is not the handler")
    same(TouchpadHold.backStep(TouchpadHold.fromHandler(NO_DEVICE, SYSTEM_BACK_FLAGS), down = false), Back.ToApp,
        "A system back must not send another system back")
}

private fun theHandlerMarkItsOwnEvents() {
    same(TouchpadHold.fromHandler(NO_DEVICE, HANDLER_FLAGS), true, "The handler's events must count")
    same(TouchpadHold.fromHandler(NO_DEVICE, HANDLER_TRACKING_FLAGS), true, "The handler's release can also say that it is tracked")
}

private fun aBackFromTheHandlerHidesAndPasses() {
    same(TouchpadHold.backStep(fromHandler = true, down = true), Back.Keep, "The handler's first event waits for the release")
    same(TouchpadHold.backStep(fromHandler = true, down = false), Back.KeepAndHide,
        "The handler's release must end the status and pass Back to the app")
}

/**
 * One push of Back while the status is held, in an app on the newer back handling, in the order that the phone reported it: the key
 * goes down, the handler's two events come, the key's release comes, then the keyboard's system back comes past on its way to the app.
 */
private fun oneRealPushIsOneSystemBack() {
    class Event(val deviceId: Int, val flags: Int, val down: Boolean)
    val push = listOf(
        Event(KEY_DEVICE, KEY_FLAGS, down = true),
        Event(NO_DEVICE, HANDLER_FLAGS, down = true),
        Event(NO_DEVICE, HANDLER_FLAGS, down = false),
        Event(KEY_DEVICE, KEY_FLAGS or 0x20, down = false),
        Event(NO_DEVICE, SYSTEM_BACK_FLAGS, down = true),
        Event(NO_DEVICE, CANCELED_SYSTEM_BACK_FLAGS, down = false),
    )
    val backs = push.count { TouchpadHold.backStep(TouchpadHold.fromHandler(it.deviceId, it.flags), it.down) == Back.KeepAndHide }
    same(backs, 1, "One push of Back must send exactly one system back")
}
