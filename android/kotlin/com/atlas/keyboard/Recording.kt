package com.atlas.keyboard

import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Lets Atlas Debug's recorder observe touchpad input. Normal Atlas contains no recorder and never sets
 * [sink], so every hook is a null check that does nothing. See android/debug/recorder.
 */
object Recording {
    interface Sink {
        fun motion(event: MotionEvent, samples: List<Sample>)
        fun key(event: KeyEvent, gesture: Boolean)
        fun outcome(outcome: Contact.Outcome)
        fun recognised(words: List<String>, resumed: Boolean)
    }

    @Volatile
    var sink: Sink? = null
}
