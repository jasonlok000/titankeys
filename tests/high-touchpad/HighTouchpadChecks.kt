package com.atlas.keyboard

import com.atlas.keyboard.HighTouchpad.Companion.PANE_APPEARED
import com.atlas.keyboard.HighTouchpad.Companion.PANE_DISAPPEARED
import com.atlas.keyboard.HighTouchpad.Companion.PANE_TITLE
import com.atlas.keyboard.HighTouchpad.Companion.PULL_MILLIS

fun main() {
    upOnlyWhileWanted()
    aPopUpKeepsTheTouchpad()
    aPullGivesTheShadeTheTouchpad()
    aTouchLongBeforeAPopUpIsNoPull()
    aTouchWhilePoppedUpIsAPull()
    aGoneShadeGivesTheTouchpadBack()
    aTitleChangeShowsThePanel()
    otherChangesDoNothing()
    theLockScreenKeepsTheTouchpad()
    aResetForgetsEverything()
    println("High touchpad checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private fun upOnlyWhileWanted() {
    val high = HighTouchpad()
    same(high.up(wanted = true, locked = false), true, "While the keyboard wants the touchpad, the window must be up")
    same(high.up(wanted = false, locked = false), false, "With no field and no hold, the window must go")
}

private fun aPopUpKeepsTheTouchpad() {
    val high = HighTouchpad()
    high.panelChanged(PANE_APPEARED, 10_000)
    same(high.panelShown, true, "The panel must count as shown")
    same(high.up(wanted = true, locked = false), true, "A pop-up notification must not take the touchpad")
}

private fun aPullGivesTheShadeTheTouchpad() {
    val high = HighTouchpad()
    high.touched(10_000)
    high.panelChanged(PANE_APPEARED, 10_150)
    same(high.pulled, true, "A panel just after a screen touch is a pull")
    same(high.up(wanted = true, locked = false), false, "A pulled shade must get the touchpad")
}

private fun aTouchLongBeforeAPopUpIsNoPull() {
    val high = HighTouchpad()
    high.touched(10_000)
    high.panelChanged(PANE_APPEARED, 10_000 + PULL_MILLIS + 1)
    same(high.up(wanted = true, locked = false), true, "A pop-up long after a touch must not take the touchpad")
}

private fun aTouchWhilePoppedUpIsAPull() {
    val high = HighTouchpad()
    high.panelChanged(PANE_APPEARED, 10_000)
    high.touched(12_000)
    same(high.up(wanted = true, locked = false), false, "A touch while the panel shows can pull it down, so the shade gets the touchpad")
}

private fun aGoneShadeGivesTheTouchpadBack() {
    val high = HighTouchpad()
    high.touched(10_000)
    high.panelChanged(PANE_APPEARED, 10_100)
    high.panelChanged(PANE_DISAPPEARED, 15_000)
    same(high.panelShown, false, "The panel must count as gone")
    same(high.up(wanted = true, locked = false), true, "Once the shade is gone, the touchpad must come back")
    high.panelChanged(PANE_APPEARED, 30_000)
    same(high.up(wanted = true, locked = false), true, "A later pop-up with no touch must not take the touchpad")
}

private fun aTitleChangeShowsThePanel() {
    val high = HighTouchpad()
    high.touched(10_000)
    high.panelChanged(PANE_TITLE, 10_200)
    same(high.panelShown, true, "A new title means the panel shows")
    same(high.pulled, true, "A new title just after a touch is a pull")
    high.panelChanged(PANE_TITLE or PANE_DISAPPEARED, 11_000)
    same(high.pulled, false, "A change that also says the panel went must end the pull")
}

private fun otherChangesDoNothing() {
    val high = HighTouchpad()
    high.touched(10_000)
    high.panelChanged(0, 10_100)
    same(high.panelShown, false, "A window change that is not the panel must not show it")
    same(high.up(wanted = true, locked = false), true, "A window change that is not the panel must not take the touchpad")
}

private fun theLockScreenKeepsTheTouchpad() {
    val high = HighTouchpad()
    same(high.up(wanted = true, locked = true), false, "The lock screen gets the touchpad, as before")
}

private fun aResetForgetsEverything() {
    val high = HighTouchpad()
    high.touched(10_000)
    high.panelChanged(PANE_APPEARED, 10_100)
    high.reset()
    same(high.panelShown, false, "A reset must forget the panel")
    same(high.pulled, false, "A reset must forget the pull")
    high.panelChanged(PANE_APPEARED, 10_200)
    same(high.pulled, false, "A reset must forget the touch")
}
