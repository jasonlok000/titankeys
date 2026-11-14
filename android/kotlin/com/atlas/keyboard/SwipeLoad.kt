package com.atlas.keyboard

/**
 * When the swipe engine failed to load (low storage while its models copy, a damaged asset), the keyboard tries again by itself
 * and says so on the bar (bug hunt 2.1, #22): one failure used to end swiping until the phone restarted, every swipe vanishing
 * without a word, and the error promised a Retry in Settings that didn't exist.
 */
internal object SwipeLoad {
    /** The engine's readiness after a failed load: see SwipeTyping.readiness(). */
    const val UNAVAILABLE = "unavailable"

    /** How many times the engine loads again after a failure, as SwipeTyping.retryLoading() allows, before it gives up. */
    const val TRIES = 2

    /** On a new text box: load again if the last load failed and tries are left, at most once a text box. */
    fun retryNow(readiness: String?, tried: Int) = readiness == UNAVAILABLE && tried < TRIES

    /** What the bar says for a swipe the engine couldn't take, or null when it's loaded or still loading. */
    fun notice(readiness: String?, tried: Int): String? = when {
        readiness != UNAVAILABLE -> null
        tried < TRIES -> "Swipe typing didn't load. It tries again in the next text box."
        else -> "Swipe typing didn't load. Restart the phone to try again."
    }

    /** How long the bar says it, in ms: long enough to read, and any key or touch clears it sooner. */
    const val NOTICE_MILLIS = 3_000L
}
