package com.titankeys.keyboard

/**
 * Holding Backspace deletes on and on, as on a PC (owner, 2026-11-17: "long pressing backspace doesn't delete word repeately like
 * on a pc only one character. it shd be like that"). Android repeats a held key itself where it can, but on the Titan a held
 * Backspace reached the keyboard once: so TitanKeys repeats it, at Android's own key repeat wait and rate, through the same path a
 * repeat takes (a code pending loses its letters first, Shift keeps deleting words). If Android's own repeats do arrive, they
 * win and TitanKeys' stop, so a key never repeats twice.
 */
class BackspaceRepeat {
    /** The repeats TitanKeys sent for the press held now; -1 when none is held, or Android repeats it itself. */
    var sent = -1
        private set
    /** True while TitanKeys hands its own repeat in, so [down] can tell it from Android's. */
    var sending = false
        private set

    /**
     * A Backspace event came: [repeat] is its repeat count, and [handled] whether the keyboard typed it. Returns true when
     * TitanKeys should repeat it after the wait: a first press the keyboard took.
     */
    fun down(repeat: Int, handled: Boolean): Boolean {
        if (sending) return false
        if (repeat > 0) {
            // Android repeats it: TitanKeys stays out.
            sent = -1
            return false
        }
        sent = if (handled) 0 else -1
        return handled
    }

    /** The next repeat's count, as the event carries it, or -1 when none is due any more. */
    fun next(): Int = if (sent < 0) -1 else ++sent

    /** [send] hands a repeat in: it's TitanKeys' own while it runs. */
    fun send(send: () -> Unit) {
        sending = true
        try { send() } finally { sending = false }
    }

    /** Backspace let go, or the field went: no more repeats. */
    fun up() {
        sent = -1
    }
}
