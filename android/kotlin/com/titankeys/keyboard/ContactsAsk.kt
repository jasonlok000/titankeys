package com.titankeys.keyboard

/**
 * When a press on "Names from my contacts" (Settings > Setup, or the guide's contacts part) goes to the app's page in Android's
 * settings instead of Android's own question (#87).
 *
 * Only once Android has stopped asking, never right after a "Don't allow" the user just pressed. Android asks until it has two
 * noes: after the first it would show its reason (shouldShowRequestPermissionRationale turns true), and the second turns it false
 * again. So TitanKeys counts the noes itself, from the reason read before and after each ask: true then false is the second no,
 * and after it every press goes to App info. Whether Android's question came up can't be seen: on the Titan 2 Elite Android starts
 * its permission screen, empty, even when it answers by itself (phone check, 2026-11-16).
 *
 * Someone who said no twice on a build that didn't count yet: their press gets an answer with no reason before or after, as a
 * dismissed question does, but at once, with no time for anyone to answer, so that counts as Android answering by itself.
 */
object ContactsAsk {
    /** Faster than this, a no had no one answering it: Android's own. */
    const val BY_ITSELF_MILLIS = 600L

    /** What one press did: whether to open App info, and whether Android has stopped asking from now on. */
    data class Outcome(val toSettings: Boolean, val stopped: Boolean)

    /**
     * One press, as Android answered it. [stoppedBefore]: Android had stopped asking, as counted before. [reasonBefore] and
     * [reasonAfter]: Android would show its reason, before the ask and after the answer. [millis]: from the ask to the answer.
     */
    fun answered(granted: Boolean, stoppedBefore: Boolean, reasonBefore: Boolean, reasonAfter: Boolean, millis: Long): Outcome {
        if (granted) return Outcome(toSettings = false, stopped = false)
        val byItself = !reasonBefore && !reasonAfter && millis < BY_ITSELF_MILLIS
        // The second no, just pressed: the answer, and nothing opens. Android stops asking from now on.
        val secondNo = reasonBefore && !reasonAfter
        return Outcome(toSettings = stoppedBefore || byItself, stopped = stoppedBefore || byItself || secondNo)
    }
}
