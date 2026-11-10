package com.atlas.keyboard

import com.atlas.keyboard.TouchpadHold.Back

fun main() {
    holdsOnlyWithTheSettingAndNoField()
    needsTheOverlayAndTheBackService()
    aHideLeavesTheStatusOnWhileItIsWanted()
    aHideEndsTheStatusWhenItIsNotWanted()
    reportsAtOnce()
    waitsOnlyForABackOnItsWay()
    backTakesTwoPushes()
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
    hold.letGo("app")
    hold.backWentThrough(1_000)
    same(hold.delay(1_000), TouchpadHold.BACK_MILLIS, "The status must wait for the second Back to reach the app")
    same(hold.delay(1_060), TouchpadHold.BACK_MILLIS - 60, "Only the rest of the wait stays")
    same(hold.delay(1_000 + TouchpadHold.BACK_MILLIS), 0L, "After the wait the status comes back at once")
    same(hold.delay(9_999), 0L, "A long time later nothing waits")
    val idle = TouchpadHold()
    idle.backWentThrough(1_000)
    same(idle.delay(1_000), 0L, "A Back with nothing let go must not hold the status back")
}

/** Back takes two pushes while the status is held: the first lets go of the touchpad, and the second goes to the app. */
private fun backTakesTwoPushes() {
    val hold = TouchpadHold()
    hold.letGo("app")
    same(hold.lettingGo, true, "The first push must let go")
    same(TouchpadHold.wanted(settingOn = true, editing = false, overlayUp = true, backServiceOn = true, lettingGo = true), false,
        "While it waits for the second push, the status must stay off, so that the push reaches the app")
    hold.started("app", editing = false)
    same(hold.lettingGo, true, "A restart in the same app with no field must still wait for the second push")
    hold.backWentThrough(1_000)
    same(hold.lettingGo, false, "The second push going through must bring the hold back")
    hold.letGo("app")
    hold.started("other", editing = false)
    same(hold.lettingGo, false, "Another app must bring the hold back")
    hold.letGo("app")
    hold.started("app", editing = true)
    same(hold.lettingGo, false, "A text field must end the wait")
    hold.letGo("app")
    hold.screenOn()
    same(hold.lettingGo, false, "The screen coming on must start afresh")
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
    same(TouchpadHold.backStep(fromHandler = true, down = false), Back.KeepAndLetGo,
        "The handler's release must end the status, so that the next push reaches the app")
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
    val backs = push.count { TouchpadHold.backStep(TouchpadHold.fromHandler(it.deviceId, it.flags), it.down) == Back.KeepAndLetGo }
    same(backs, 1, "One push of Back must let go exactly once")
}
