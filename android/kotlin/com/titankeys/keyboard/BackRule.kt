package com.titankeys.keyboard

/**
 * When Back is the keyboard's, apart from Android: no Android types here, so its checks run on a plain JVM
 * (tests/back-rule).
 */
object BackRule {
    /**
     * Key Mapper replays every Back about 15 ms after its release. A Back pushed within this long of the release of one the
     * keyboard handled is that replay. A person's own second push comes later than this, and is a new Back (#125: the old
     * 250 ms from the push ate a quick second Back).
     */
    const val ECHO_MILLIS = 50L

    /** Whether a Back pushed at [downAt] is the replay of the handled Back released at [releasedAt] (both uptime). */
    fun isEcho(downAt: Long, releasedAt: Long): Boolean = downAt >= releasedAt && downAt - releasedAt < ECHO_MILLIS

    /**
     * Whether the keyboard is really on screen for Back: Android's flag that the input view is shown, and its window shown and
     * not hidden since. The flag alone stayed true after Android had hidden the keyboard, and a Back taken to hide a keyboard
     * already gone did nothing (#125).
     */
    fun onScreen(inputViewShown: Boolean, windowShown: Boolean): Boolean = inputViewShown && windowShown
}
