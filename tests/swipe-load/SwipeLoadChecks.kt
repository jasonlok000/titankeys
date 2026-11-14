package com.atlas.keyboard

/**
 * One failed engine load no longer ends swiping until a reboot (bug hunt 2.1, #22): the next text box loads it again, a bounded
 * number of times, and a swipe that comes to nothing meanwhile says why on the bar. run.sh checks the keyboard calls these.
 */
fun main() {
    // A failure: the next text box tries again, twice at most.
    check(SwipeLoad.retryNow(SwipeLoad.UNAVAILABLE, 0)) { "After a failed load, the next text box loads it again" }
    check(SwipeLoad.retryNow(SwipeLoad.UNAVAILABLE, 1)) { "and again after a second failure" }
    check(!SwipeLoad.retryNow(SwipeLoad.UNAVAILABLE, SwipeLoad.TRIES)) { "but not past its tries" }
    // A loaded or loading engine is left alone, and nothing shows.
    for (readiness in listOf("ready", "loading", null)) {
        check(!SwipeLoad.retryNow(readiness, 0)) { "A $readiness engine isn't loaded again" }
        check(SwipeLoad.notice(readiness, 0) == null) { "A $readiness engine shows nothing" }
    }
    // A swipe while it's unavailable says so, and what happens next: never a Retry in Settings, which doesn't exist.
    val again = SwipeLoad.notice(SwipeLoad.UNAVAILABLE, 0)!!
    check("next text box" in again) { "While it can try again, the bar says it will: $again" }
    val restart = SwipeLoad.notice(SwipeLoad.UNAVAILABLE, SwipeLoad.TRIES)!!
    check("Restart" in restart) { "Out of tries, the bar says to restart: $restart" }
    for (text in listOf(again, restart)) check("Settings" !in text && "settings" !in text) { "No Retry in Settings: $text" }
    check(SwipeLoad.NOTICE_MILLIS in 2_000L..5_000L) { "The notice stays long enough to read" }
    println("Swipe load checks passed")
}
