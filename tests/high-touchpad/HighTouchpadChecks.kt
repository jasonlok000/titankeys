package com.atlas.keyboard

fun main() {
    upWhileTyping()
    downWithoutAField()
    downWithTheScreenOff()
    downOnTheLockScreen()
    backAfterAnUnlock()
    println("High touchpad checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private fun upWhileTyping() {
    same(HighTouchpad.up(wanted = true, screenOn = true, lockScreenShows = false), true,
        "While a field is being edited, the window must stand above the shade")
}

private fun downWithoutAField() {
    same(HighTouchpad.up(wanted = false, screenOn = true, lockScreenShows = false), false, "With no field and no hold, the window must go")
}

private fun downWithTheScreenOff() {
    same(HighTouchpad.up(wanted = true, screenOn = false, lockScreenShows = false), false, "With the screen off, the window must go")
}

private fun downOnTheLockScreen() {
    same(HighTouchpad.up(wanted = true, screenOn = true, lockScreenShows = true), false, "The lock screen keeps the touchpad as before")
}

private fun backAfterAnUnlock() {
    // The rule has no memory: once the lock screen has gone, the window is up again, whether or not a broadcast said so.
    HighTouchpad.up(wanted = true, screenOn = false, lockScreenShows = true)
    same(HighTouchpad.up(wanted = true, screenOn = true, lockScreenShows = false), true,
        "After an unlock the window must come back, with nothing left over from the lock")
}
