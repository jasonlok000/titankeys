package com.atlas.keyboard

import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Lets TitanKeys Debug's recorder observe touchpad input. The normal app contains no recorder and never sets
 * [sink], so every hook is a null check that does nothing. See android/debug/recorder.
 */
object Recording {
    interface Sink {
        fun motion(event: MotionEvent, samples: List<Sample>)
        fun key(event: KeyEvent, gesture: Boolean)
        fun outcome(outcome: Contact.Outcome)
        fun recognised(words: List<String>, resumed: Boolean)
        /**
         * What the Space row's cursor gestures decided at [time] ([CursorGesture]): "down" with whether the touch could
         * count, "up" with whether it finished two quick taps, "drag" when steering or selecting starts, "double" when a
         * double tap selects a word, and "cancel" when a key press or another finger calls them off.
         */
        fun gesture(event: String, time: Long, detail: String) {}
    }

    @Volatile
    var sink: Sink? = null
}
