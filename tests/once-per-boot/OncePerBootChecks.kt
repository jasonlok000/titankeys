package com.atlas.keyboard

import com.atlas.keyboard.OncePerBoot.Boot

fun main() {
    showsTheFirstTime()
    showsOncePerBoot()
    aRestartOfTitanKeysIsNoReboot()
    aRebootShowsItAgain()
    withoutABootCountTheStartTimeDecides()
    aClockCorrectionIsNoReboot()
    println("Once per boot checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private const val STARTED = 1_762_700_000_000L

private fun showsTheFirstTime() {
    same(OncePerBoot.due(null, Boot(12, STARTED)), true, "It must show if it never has")
}

private fun showsOncePerBoot() {
    val boot = Boot(12, STARTED)
    same(OncePerBoot.due(boot, boot), false, "Once shown in this boot, it must not show again")
}

private fun aRestartOfTitanKeysIsNoReboot() {
    // TitanKeys reads the start time again after a restart, as the current time less the time since boot, a few ms apart.
    same(OncePerBoot.due(Boot(12, STARTED), Boot(12, STARTED + 3)), false, "A restart of TitanKeys must not show it again")
}

private fun aRebootShowsItAgain() {
    same(OncePerBoot.due(Boot(12, STARTED), Boot(13, STARTED + 3_600_000)), true, "After a reboot it must show again")
    same(OncePerBoot.due(Boot(12, STARTED), Boot(13, STARTED + 10)), true, "A new boot count means a new boot, whatever the time")
}

private fun withoutABootCountTheStartTimeDecides() {
    same(OncePerBoot.due(Boot(null, STARTED), Boot(null, STARTED + 20)), false, "With no boot count, a close start time is the same boot")
    same(OncePerBoot.due(Boot(null, STARTED), Boot(null, STARTED + 600_000)), true, "With no boot count, a later start time is a new boot")
    same(OncePerBoot.due(Boot(12, STARTED), Boot(null, STARTED + 600_000)), true, "If the count can no longer be read, the time decides")
}

private fun aClockCorrectionIsNoReboot() {
    same(OncePerBoot.due(Boot(null, STARTED), Boot(null, STARTED - 45_000)), false, "A clock correction of under a minute is the same boot")
    same(OncePerBoot.due(Boot(null, STARTED), Boot(null, STARTED + OncePerBoot.SAME_BOOT_MILLIS + 1)), true,
        "A start time more than a minute away is a new boot")
}
