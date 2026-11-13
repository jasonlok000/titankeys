package com.atlas.keyboard

import java.util.PriorityQueue

/**
 * The bar's word menu as AtlasIme drives it, without Android or a phone: the touchpad's touches and lifts (followRestingKey), the
 * keys going down and up (onKeyDown's menu keys and onKeyUp), and the main looper's expireHover posts, all in time order. It makes
 * the same calls on the real [SteadyKey] and [MenuKeys] that AtlasIme makes, and tests/word-menu checks that AtlasIme still makes
 * them. [mark] is the strip's menuMark, and [pill] is what the strip's pill shows from it (Strip.drawMenu).
 *
 * Times are the events' own, in milliseconds. Every event runs the posts that fell due before it, as the looper would.
 */
/**
 * What a press under a resting finger reaches, as AtlasIme hears it: the touchpad's touches and lifts, and the keys, at their times.
 * The word menu ([MenuBar]) and the quick settings page ([QuickSettingsPage]) both take it, so one set of event orders drives both.
 */
interface Fingers {
    /** Runs what the looper had posted for [time]. */
    fun at(time: Long): Fingers
    fun touch(key: String, time: Long)
    fun lift(time: Long)
    fun keyDown(key: String, time: Long)
    fun keyUp(key: String, time: Long)
    /** A press and its release, [hold] ms apart. */
    fun press(key: String, time: Long, hold: Long = 0)
}

class MenuBar(state: MenuKeys.State) : Fingers {
    private val hover = SteadyKey(Layout::keyAt)
    /** The open menu's state, which every press moves along the ladder. */
    var state = state
        private set
    /** Whether the menu is open: Back and Forget close it, and so does a key that picks nothing, which types. */
    var open = true
        private set
    /** The strip's menuMark: the column of the hovered button, or -1. */
    var mark = -1
        private set
    /** The buttons that the presses picked, in order, and the closing action, if any. */
    val picked = mutableListOf<String>()
    /** Every change of [mark], with its time: a pill that went away for a moment shows here. */
    val marks = mutableListOf<Pair<Long, Int>>()
    private var menuKey = -1
    private var now = 0L
    private val posts = PriorityQueue<Long>()

    /** The pill the strip draws: the hovered button's column and what pressing it does now, or null for none. */
    val pill: Pair<Int, MenuKeys.Button>?
        get() {
            if (!open) return null
            val column = mark.takeIf { MenuKeys.pills(it, state) } ?: return null
            val choices = MenuKeys.choices(state)
            return column to (if (column == MenuKeys.SAVE) choices.i else choices.o)!!
        }

    /** The key under the resting finger, as the hover holds it. */
    val key get() = hover.key

    /** Runs the expireHover posts due by [time]. */
    override fun at(time: Long): MenuBar {
        while (posts.isNotEmpty() && posts.peek() <= time) {
            now = posts.poll()
            expireHovers()
        }
        now = maxOf(now, time)
        return this
    }

    /** A finger reported on [key]'s centre at [time]: followRestingKey. */
    override fun touch(key: String, time: Long) = touchAt(centre(key), time)

    fun touchAt(place: Pair<Float, Float>, time: Long) {
        at(time)
        if (!hover.touch(place.first, place.second)) return
        markMenu(hover.key)
    }

    /** The touchpad reports the finger lifted at [time]: followRestingKey. */
    override fun lift(time: Long) {
        at(time)
        hover.lift(time)
        posts += time + SteadyKey.LIFT_MILLIS
    }

    /** [key] goes down at [time]: onKeyDown, with the word menu open. */
    override fun keyDown(key: String, time: Long) {
        at(time)
        val code = code(key)
        hover.keyDown(code)
        if (!open) return
        val action = menuAction(key)
        if (action == null) {
            // A key that picks nothing types, and typing closes the menu.
            picked += "types $key"
            close()
            return
        }
        menuKey = code
        stripAction(action)
        if (open) {
            hover.press(key)
            markMenu(hover.key)
        }
    }

    /** [key] is let go at [time]: onKeyUp. */
    override fun keyUp(key: String, time: Long) {
        at(time)
        val code = code(key)
        val menuRelease = code == menuKey && open
        if (menuRelease) hover.release(code, time)
        val barLast = !menuRelease && hover.keyUp(code, time)
        if (barLast) {
            posts.clear()
            posts += time + SteadyKey.LIFT_MILLIS
        }
        if (code == menuKey) menuKey = -1
    }

    /** A press and its release, [hold] ms apart, under a finger that rests: down, then up. */
    override fun press(key: String, time: Long, hold: Long) {
        keyDown(key, time)
        keyUp(key, time + hold)
    }

    private fun expireHovers() {
        if (hover.expire(now)) markMenu("")
    }

    private fun markMenu(key: String) {
        val column = if (open) MenuKeys.hovered(key) else -1
        setMark(if (column == MenuKeys.SAVE || column == MenuKeys.FORGET) column else -1)
    }

    private fun setMark(value: Int) {
        if (value != mark) marks += now to value
        mark = value
    }

    /** AtlasIme.menuAction: I's button, O's, or Back, for the key pressed. */
    private fun menuAction(key: String): String? {
        val choices = MenuKeys.choices(state)
        return when (MenuKeys.hovered(key)) {
            MenuKeys.SAVE -> choices.i?.name
            MenuKeys.FORGET -> choices.o?.name
            MenuKeys.BACK -> "Back"
            else -> null
        }
    }

    /** AtlasIme.stripAction: a step of the ladder keeps the menu open; Forget and Back close it. */
    private fun stripAction(action: String) {
        picked += action
        when (action) {
            "Back" -> close()
            "Forget" -> open = false
            else -> state = MenuKeys.press(state, MenuKeys.Button.valueOf(action)) ?: state
        }
    }

    private fun close() {
        open = false
        setMark(-1)
    }

    companion object {
        /** The centre of [key] on the touchpad, from 0 to 1 across and down, as [Layout.keyAt] reads it. */
        fun centre(key: String): Pair<Float, Float> {
            val row = Layout.rows.indexOfFirst { key in it }
            check(row in 0..2) { "$key isn't on the top three rows" }
            return (Layout.rows[row].indexOf(key) + .5f) / 10 to (row + .5f) / 4
        }

        /** The Android key code of [key]: a letter, or Backspace. */
        fun code(key: String) = if (key == Layout.BACKSPACE) 67 else 29 + (key[0] - 'A')
    }
}
