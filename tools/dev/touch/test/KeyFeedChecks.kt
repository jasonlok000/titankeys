package com.titankeys.keyboard

/** The key feed's script without a phone (android/debug/recorder/KeyFeedPlan.kt): meta states, repeats, bad scripts. */
fun main() {
    var failed = 0
    fun same(what: String, got: Any?, wanted: Any?) {
        if (got != wanted) {
            failed++
            println("FAIL $what: got $got, wanted $wanted")
        }
    }
    fun plan(script: String) = KeyFeedPlan.read(script, 400, 50)?.map { it.toString() }

    // Thai's Shift + Q: Shift's press has its bit, Q's press and release carry Shift, Shift's release doesn't.
    same("Shift + Q", plan("D 0 SHIFT_RIGHT;D 60 Q;U 140 Q;U 200 SHIFT_RIGHT"),
        listOf("D 0 SHIFT_RIGHT m129", "D 60 Q m129", "U 140 Q m129", "U 200 SHIFT_RIGHT m0"))
    same("Alt + Q carries Alt's bits", plan("D 0 ALT_LEFT;D 50 Q;U 100 Q;U 150 ALT_LEFT")?.get(1), "D 50 Q m18")
    // A held letter repeats from the repeat timeout every repeat delay, as Android's do.
    same("Q held 520 ms", plan("D 0 Q;U 520 Q"), listOf("D 0 Q m0", "D 400 Q r1 m0", "D 450 Q r2 m0", "D 500 Q r3 m0", "U 520 Q m0"))
    same("Shift held never repeats", plan("D 0 SHIFT_RIGHT;U 900 SHIFT_RIGHT"), listOf("D 0 SHIFT_RIGHT m129", "U 900 SHIFT_RIGHT m0"))
    same("a newer key stops the older one's repeats", plan("D 0 Q;D 300 W;U 500 Q;U 600 W"),
        listOf("D 0 Q m0", "D 300 W m0", "U 500 Q m0", "U 600 W m0"))
    same("keys left down are let go at the end", plan("D 0 SHIFT_RIGHT;D 10 Q"),
        listOf("D 0 SHIFT_RIGHT m129", "D 10 Q m129", "U 10 Q m129", "U 10 SHIFT_RIGHT m0"))
    same("a release of a key that's up is refused", plan("U 0 Q"), null)
    same("a key pressed twice is refused", plan("D 0 Q;D 10 Q"), null)
    same("time going back is refused", plan("D 10 Q;U 5 Q"), null)
    same("past 15 s is refused", plan("D 0 Q;U 15001 Q"), null)
    same("a bad name is refused", plan("D 0 q"), null)
    same("nothing is refused", plan(""), null)
    for (case in OnDeviceTestCases.entries) {
        val events = KeyFeedPlan.read(case.keys, 400, 50)
        same("${case.name} fits its on-device observation window",
            events != null && events.isNotEmpty() && events.last().millis < OnDeviceTestCases.SETTLE_MILLIS, true)
        same("${case.name} has a valid editor position", case.cursor in 0..case.text.length, true)
        same("${case.name} has no repeated keys", events?.none { it.repeat != 0 }, true)
    }
    if (failed > 0) {
        println("$failed key feed checks failed")
        kotlin.system.exitProcess(1)
    }
    println("Key feed checks passed")
}
