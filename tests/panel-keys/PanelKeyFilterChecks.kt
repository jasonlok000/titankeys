package com.atlas.keyboard

import android.view.KeyEvent
import com.atlas.keyboard.Modifiers.Panel

private const val ENTER = KeyEvent.KEYCODE_ENTER
private const val BACKSPACE = KeyEvent.KEYCODE_DEL
private val PANEL_KEYS = listOf(ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, BACKSPACE)
private val OPEN_PANELS = listOf(Panel.Symbols, Panel.Emoji, Panel.Settings)

fun main() {
    nothingIsTakenWithThePanelClosed()
    aWholePushIsTakenWithThePanelOpen()
    otherKeysStayWithTheApp()
    aPushFromBeforeThePanelStaysWithTheApp()
    aPushOnThePanelEndsThereWhenThePanelCloses()
    aLostReleaseDoesNotTakeTheNextPush()
    twoKeysAtOnce()
    clearForgetsThePushes()
    onlyTheEmojiPageTurns()
    println("Panel key filter checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private fun nothingIsTakenWithThePanelClosed() {
    val filter = PanelKeyFilter()
    for (code in PANEL_KEYS) {
        same(filter.takes(code, down = true, repeat = 0, Panel.Closed), false, "With the panel closed key $code must reach the app")
        same(filter.takes(code, down = true, repeat = 3, Panel.Closed), false, "With the panel closed a repeat of key $code must reach the app")
        same(filter.takes(code, down = false, repeat = 0, Panel.Closed), false, "With the panel closed the release of key $code must reach the app")
        same(filter.holding, false, "With the panel closed nothing must stay held")
    }
}

private fun aWholePushIsTakenWithThePanelOpen() {
    for (panel in OPEN_PANELS) for (code in PANEL_KEYS) {
        val filter = PanelKeyFilter()
        same(filter.takes(code, down = true, repeat = 0, panel), true, "On $panel the push of key $code must stay from the app")
        same(filter.holding, true, "On $panel the push must count as held")
        same(filter.takes(code, down = true, repeat = 1, panel), true, "On $panel a repeat of key $code must stay from the app")
        same(filter.takes(code, down = true, repeat = 9, panel), true, "On $panel a later repeat of key $code must stay from the app")
        same(filter.takes(code, down = false, repeat = 0, panel), true, "On $panel the release of key $code must stay from the app")
        same(filter.holding, false, "On $panel the release must end the push")
    }
}

private fun otherKeysStayWithTheApp() {
    val others = listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_VOLUME_UP)
    val filter = PanelKeyFilter()
    for (panel in Panel.entries) for (code in others) {
        same(filter.takes(code, down = true, repeat = 0, panel), false, "On $panel key $code must not be taken")
        same(filter.takes(code, down = false, repeat = 0, panel), false, "On $panel the release of key $code must not be taken")
    }
    same(filter.holding, false, "Other keys must not keep the filter on")
}

private fun aPushFromBeforeThePanelStaysWithTheApp() {
    val filter = PanelKeyFilter()
    same(filter.takes(ENTER, down = true, repeat = 0, Panel.Closed), false, "A push with the panel closed belongs to the app")
    same(filter.takes(ENTER, down = true, repeat = 1, Panel.Emoji), false, "Its repeat after the panel opens must reach the app")
    same(filter.takes(ENTER, down = false, repeat = 0, Panel.Emoji), false, "Its release after the panel opens must reach the app")
    same(filter.holding, false, "A push that the app has must not keep the filter on")
}

private fun aPushOnThePanelEndsThereWhenThePanelCloses() {
    val filter = PanelKeyFilter()
    same(filter.takes(ENTER, down = true, repeat = 0, Panel.Emoji), true, "A push on the panel must be taken")
    same(filter.takes(ENTER, down = true, repeat = 1, Panel.Closed), true, "Its repeat must stay from the app when the panel has closed")
    same(filter.holding, true, "The filter must stay on until the release")
    same(filter.takes(ENTER, down = false, repeat = 0, Panel.Closed), true, "Its release must stay from the app when the panel has closed")
    same(filter.holding, false, "The release must let the filter go off")
    same(filter.takes(ENTER, down = true, repeat = 0, Panel.Closed), false, "The next push with the panel closed must reach the app")
}

private fun aLostReleaseDoesNotTakeTheNextPush() {
    val filter = PanelKeyFilter()
    filter.takes(BACKSPACE, down = true, repeat = 0, Panel.Emoji)
    same(filter.takes(BACKSPACE, down = true, repeat = 0, Panel.Closed), false, "A new push with the panel closed must reach the app")
    same(filter.holding, false, "A new push must end a push whose release was lost")
    same(filter.takes(BACKSPACE, down = false, repeat = 0, Panel.Closed), false, "The release of the new push must reach the app")
}

private fun twoKeysAtOnce() {
    val filter = PanelKeyFilter()
    same(filter.takes(ENTER, down = true, repeat = 0, Panel.Emoji), true, "Enter must be taken")
    same(filter.takes(BACKSPACE, down = true, repeat = 0, Panel.Emoji), true, "Backspace must be taken with Enter down")
    same(filter.takes(ENTER, down = false, repeat = 0, Panel.Emoji), true, "The release of Enter must be taken")
    same(filter.holding, true, "Backspace must still count as held")
    same(filter.takes(BACKSPACE, down = false, repeat = 0, Panel.Emoji), true, "The release of Backspace must be taken")
    same(filter.holding, false, "With both released nothing must stay held")
}

private fun clearForgetsThePushes() {
    val filter = PanelKeyFilter()
    filter.takes(ENTER, down = true, repeat = 0, Panel.Emoji)
    filter.clear()
    same(filter.holding, false, "Clear must forget a push")
    same(filter.takes(ENTER, down = false, repeat = 0, Panel.Closed), false, "A release after clear must reach the app")
}

private fun onlyTheEmojiPageTurns() {
    same(Modifiers.panelScroll(BACKSPACE, Panel.Emoji), 1f, "Backspace must turn back on the emoji page")
    same(Modifiers.panelScroll(ENTER, Panel.Emoji), -1f, "Enter must turn on on the emoji page")
    for (code in PANEL_KEYS) for (panel in listOf(Panel.Symbols, Panel.Settings))
        same(Modifiers.panelScroll(code, panel), 0f, "On $panel key $code must do nothing")
}
