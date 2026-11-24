package com.titankeys.keyboard

fun main() {
    keyMappersReplayIsAnEcho()
    aQuickSecondPushIsNotAnEcho()
    aStaleShownFlagIsNotOnScreen()
    println("Back rule checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private fun keyMappersReplayIsAnEcho() {
    same(BackRule.isEcho(downAt = 1015, releasedAt = 1000), true, "Key Mapper's replay 15 ms after the release must be kept")
    same(BackRule.isEcho(downAt = 1000, releasedAt = 1000), true, "A replay at the release itself must be kept")
}

private fun aQuickSecondPushIsNotAnEcho() {
    same(BackRule.isEcho(downAt = 1050, releasedAt = 1000), false, "A push 50 ms after the release is a new Back")
    same(BackRule.isEcho(downAt = 1200, releasedAt = 1000), false, "A quick second push must not be eaten")
    same(BackRule.isEcho(downAt = 990, releasedAt = 1000), false, "A push before the release is no replay of it")
    same(BackRule.isEcho(downAt = 0, releasedAt = Long.MIN_VALUE / 2), false, "No handled Back yet: nothing is an echo")
}

private fun aStaleShownFlagIsNotOnScreen() {
    same(BackRule.onScreen(inputViewShown = true, windowShown = false), false, "A stale shown flag must not take Back")
    same(BackRule.onScreen(inputViewShown = true, windowShown = true), true, "A shown keyboard takes Back")
    same(BackRule.onScreen(inputViewShown = false, windowShown = true), false, "No input view: Back is the app's")
}
