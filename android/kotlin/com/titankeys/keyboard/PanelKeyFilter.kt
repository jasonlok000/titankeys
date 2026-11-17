package com.titankeys.keyboard

/**
 * Which key events the Back service keeps from the app while the Sym panel is open, or while an engine is building text (a
 * code pending, or Chinese's bigger menu open). A chat app can take Enter before the keyboard hears it and send the message, and
 * the keyboard then hears only the release (owner, 2026-11-17: "Enter with the chinese overlay expanded still sends", and "the
 * keycode didn't get sent but the message is sent out"). So Enter and Backspace go to the keyboard first, from the push to the
 * release, and the app does not hear them: the panel turns its pages, the menu its own, and an engine types its code.
 *
 * A push stays with what heard it first. A push that started with the panel open stays with the panel if the panel closes while
 * the key is down, and a push that started before the panel opened stays with the app.
 */
class PanelKeyFilter {
    private val taken = HashSet<Int>()

    /** A push that the panel took is still down, so its release must still be kept from the app. */
    val holding get() = taken.isNotEmpty()

    /** Whether this event must stay from the app. */
    fun takes(code: Int, down: Boolean, repeat: Int, panel: Modifiers.Panel, building: Boolean = false): Boolean {
        // The keys that can turn the emoji pages are the keys that belong to the panel.
        if (Modifiers.panelScroll(code, Modifiers.Panel.Emoji) == null) return false
        if (!down) return taken.remove(code)
        if (repeat > 0) return code in taken
        // A new push means that the release of an earlier one was lost.
        taken -= code
        if (panel == Modifiers.Panel.Closed && !building) return false
        taken += code
        return true
    }

    /** The service stopped, so no release can arrive here. */
    fun clear() = taken.clear()
}
