package com.atlas.keyboard

import android.view.KeyEvent

/**
 * Scripted typing for a debug build's demo screen: while that screen is in front it sets [director], and the director decides
 * what each key press and each touch on the strip does, and what the strip shows. Swipes, delete flicks and selections still
 * reach it through [KeyboardEvents]. Normal TitanKeys never sets a director, so none of this does anything there.
 */
object ScriptedInput {
    interface Director {
        /** A key that is not a system key went down or up. True if the director takes it, so the keyboard does nothing with it. */
        fun key(code: Int, event: KeyEvent): Boolean
        /** A word on the strip was touched, at its place in the composer's order. True if the director takes it. */
        fun strip(index: Int): Boolean
        /** What the strip shows, in the composer's order, or null for the usual words. */
        val words: List<String>?
    }

    @Volatile var director: Director? = null

    /** The keyboard sets this: it shows the director's words on the strip now. Call it on the main thread. */
    var refresh: () -> Unit = {}
}
