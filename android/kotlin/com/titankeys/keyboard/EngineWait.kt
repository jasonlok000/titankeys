package com.titankeys.keyboard

/**
 * Whether keys must wait for an engine: an input method with an engine (Yale, Pinyin, Japanese, Korean...) loads off the main
 * thread as a field starts or it's switched to, and until it's ready the keyboard types English. A key typed in that time
 * went to the field as a plain letter, not into the engine's code (owner, 2026-11-17: "it'll type the first character out
 * without putting it as a keycode"), most easily with the keyboard hidden, as nothing on screen says it's still loading. So
 * the keyboard holds keys in its queue while [waiting], and lets them go once the load ends, whichever way it ends.
 *
 * Each load has a ticket ([Languages]' count); only the last one's end counts, as an overtaken load's never applies.
 */
class EngineWait {
    private var awaited = NONE

    /** A load of an input method's files and engine waits for one only when it has an engine. */
    val waiting get() = awaited != NONE

    /** Load [ticket] began: of an input method with an engine when [engine]. */
    fun loading(ticket: Int, engine: Boolean) {
        awaited = if (engine) ticket else NONE
    }

    /** Load [ticket] ended, ready or not. True if the keys waited for it. */
    fun loaded(ticket: Int): Boolean {
        if (ticket != awaited) return false
        awaited = NONE
        return true
    }

    /** The keyboard is going: nothing waits. */
    fun stop() {
        awaited = NONE
    }

    private companion object {
        const val NONE = Int.MIN_VALUE
    }
}
