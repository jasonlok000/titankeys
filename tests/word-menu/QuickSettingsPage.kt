package com.atlas.keyboard

import java.util.PriorityQueue

/**
 * The Sym panel's quick settings page as AtlasIme drives it, without Android or a phone: the touchpad's touches and lifts (hover),
 * the keys going down and up (onKeyDown, panelKey's quick settings, onKeyUp), and the main looper's expireHover posts, all in time
 * order. It makes the same calls on the real [SteadyKey] that AtlasIme makes, reading keys as the page does
 * ([Layout.restingKeyAt]), and tests/word-menu checks that AtlasIme still makes them. [mark] is the panel's hover.
 */
class QuickSettingsPage : Fingers {
    /** The panel's pages that matter here, as Modifiers.Panel has them. */
    enum class Page { Settings, Clipboard, Closed }

    private val hover = SteadyKey(Layout::restingKeyAt)
    /** The page shown: the quick settings, the clipboard page after X, or none after Open or Kbd, which close the panel. */
    var page = Page.Settings
        private set
    /** The panel's hover: the key under the resting finger, or empty. */
    var mark = ""
        private set
    /** Every change of [mark], with its time: a mark that went away for a moment shows here. */
    val marks = mutableListOf<Pair<Long, String>>()
    /** Each quick setting's switch, as the toggles leave it. */
    val on = HashMap<QuickSetting, Boolean>()
    private var panelPress = -1
    private var now = 0L
    private val posts = PriorityQueue<Long>()

    override fun at(time: Long): QuickSettingsPage {
        while (posts.isNotEmpty() && posts.peek() <= time) {
            now = posts.poll()
            if (hover.expire(now)) setMark("")
        }
        now = maxOf(now, time)
        return this
    }

    /** A finger reported on [key]'s centre at [time]: hover, which marks the key at every touch. */
    override fun touch(key: String, time: Long) {
        at(time)
        val (x, y) = MenuBar.centre(key)
        hover.touch(x, y)
        setMark(hover.key)
    }

    /** The touchpad reports the finger lifted at [time]: hover. */
    override fun lift(time: Long) {
        at(time)
        hover.lift(time)
        posts += time + SteadyKey.LIFT_MILLIS
    }

    /** [key] goes down at [time]: onKeyDown, then panelKey on the quick settings page, then useQuickSetting and keepPanelHover. */
    override fun keyDown(key: String, time: Long) {
        at(time)
        val code = MenuBar.code(key)
        hover.keyDown(code)
        if (page != Page.Settings) return
        val item = QuickSetting.on(key) ?: return
        when (item) {
            // Open and Kbd close the panel, and X turns to the clipboard page: a new page starts with no mark.
            QuickSetting.OpenSettings, QuickSetting.ChangeKeyboard -> changePage(Page.Closed)
            QuickSetting.Clipboard -> changePage(Page.Clipboard)
            else -> on[item] = !(on[item] ?: false)
        }
        if (page != Page.Settings) return
        hover.press(key)
        panelPress = code
        setMark(hover.key)
    }

    /** [key] is let go at [time]: onKeyUp. */
    override fun keyUp(key: String, time: Long) {
        at(time)
        val code = MenuBar.code(key)
        val release = code == panelPress
        if (release) {
            hover.release(code, time)
            panelPress = -1
        }
        val last = !release && hover.keyUp(code, time) && page != Page.Closed
        if (last) {
            posts.clear()
            posts += time + SteadyKey.LIFT_MILLIS
        }
    }

    override fun press(key: String, time: Long, hold: Long) {
        keyDown(key, time)
        keyUp(key, time + hold)
    }

    private fun changePage(next: Page) {
        page = next
        setMark("")
    }

    private fun setMark(value: String) {
        if (value != mark) marks += now to value
        mark = value
    }
}
