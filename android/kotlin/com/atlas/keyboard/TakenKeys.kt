package com.atlas.keyboard

import android.view.KeyEvent

/**
 * The keys that the keyboard takes from an app that would take them before the keyboard hears them: Smart Launcher's search
 * field, where the launcher typed its own letters, so TitanKeys' fixes, Shift + Backspace and the Sym key never ran there (owner,
 * 2026-11-13). A press is taken whole: its repeats and its release go with it, even once the field has closed, and a release
 * whose press the app had stays the app's. Back and the phone's own keys, such as volume, stay with the app.
 */
class TakenKeys {
    private val down = HashSet<Int>()

    /** Whether a taken key is still down, so its release must still be heard. */
    val holding get() = down.isNotEmpty()

    /** Whether the keyboard takes this press ([pressed]) or release. [taking] is whether it takes new presses now. */
    fun takes(code: Int, pressed: Boolean, taking: Boolean, system: Boolean): Boolean {
        if (!pressed) return down.remove(code)
        if (code in down) return true
        if (!taking || system || code == KeyEvent.KEYCODE_BACK) return false
        down += code
        return true
    }

    fun clear() = down.clear()
}
