package com.atlas.keyboard

import com.atlas.keyboard.BackPush.Action

fun main() {
    secondPairOfEventsDoesNotHideTheKeyboard()
    eitherEventClosesThePanel()
    secondPairCanComeBeforeTheRelease()
    holdingBackDoesNothingMore()
    aLaterPushIsNormalBackAgain()
    withoutAPanelBackIsNormal()
    aPanelThatOpensAgainStillCloses()
    println("Back checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

/** Each event is (down, panel open now, time, the time its press went down). */
private class Press(val down: Boolean, val panelOpen: Boolean, val time: Long, val downTime: Long = time)

private fun actions(vararg events: Press): List<Action> {
    val back = BackPush()
    return events.map { back.event(it.down, it.panelOpen, it.time, it.downTime) }
}

/** The events of one push of Back with the panel open, as a phone reported them: the key itself, then the second pair. */
private fun secondPairOfEventsDoesNotHideTheKeyboard() {
    val pressedAt = 182_813_892L
    fun at(offset: Long) = pressedAt + offset
    same(actions(
        Press(true, true, at(0), at(0)),
        Press(false, false, at(112), at(0)),
        Press(true, false, at(138)),
        Press(false, false, at(138)),
    ), listOf(Action.ClosePanel, Action.UseUp, Action.UseUp, Action.UseUp),
        "One push must only close the panel, and the second pair of events must not hide the keyboard")
    same(actions(
        Press(true, true, at(0), at(0)),
        Press(false, false, at(74), at(0)),
        Press(true, false, at(89)),
        Press(false, false, at(89)),
    ), listOf(Action.ClosePanel, Action.UseUp, Action.UseUp, Action.UseUp), "The same must hold for a quicker push")
}

private fun eitherEventClosesThePanel() {
    same(actions(Press(true, true, 1_000, 1_000), Press(false, false, 1_080, 1_000)),
        listOf(Action.ClosePanel, Action.UseUp), "A down that arrives first must close the panel and use up the up")
    same(actions(Press(false, true, 1_000, 900), Press(true, false, 1_020), Press(false, false, 1_020)),
        listOf(Action.ClosePanel, Action.UseUp, Action.UseUp), "An up that arrives first must close the panel and use up what follows")
}

/** The second pair can arrive while the key is still down, with the key's own up much later. */
private fun secondPairCanComeBeforeTheRelease() {
    same(actions(
        Press(true, true, 2_000, 2_000),
        Press(true, false, 2_090),
        Press(false, false, 2_091),
        Press(false, false, 2_830, 2_000),
    ), listOf(Action.ClosePanel, Action.UseUp, Action.UseUp, Action.UseUp), "The key's own up must never hide the keyboard, however late it comes")
}

private fun holdingBackDoesNothingMore() {
    same(actions(
        Press(true, true, 1_000, 1_000),
        Press(true, false, 1_400, 1_000),
        Press(true, false, 1_450, 1_000),
        Press(false, false, 2_100, 1_000),
    ), listOf(Action.ClosePanel, Action.UseUp, Action.UseUp, Action.UseUp), "Holding Back must close the panel once and do nothing else")
}

private fun aLaterPushIsNormalBackAgain() {
    same(actions(
        Press(true, true, 1_000, 1_000),
        Press(false, false, 1_100, 1_000),
        Press(true, false, 1_500, 1_500),
        Press(false, false, 1_600, 1_500),
    ), listOf(Action.ClosePanel, Action.UseUp, Action.Normal, Action.Normal), "A later push of Back must do what Back normally does")
}

private fun withoutAPanelBackIsNormal() {
    same(actions(
        Press(true, false, 1_000, 1_000),
        Press(false, false, 1_080, 1_000),
        Press(true, false, 1_090),
        Press(false, false, 1_090),
    ), listOf(Action.Normal, Action.Normal, Action.Normal, Action.Normal), "With no panel, nothing must change")
}

private fun aPanelThatOpensAgainStillCloses() {
    same(actions(
        Press(true, true, 1_000, 1_000),
        Press(false, false, 1_100, 1_000),
        Press(true, true, 1_180, 1_180),
        Press(false, false, 1_260, 1_180),
    ), listOf(Action.ClosePanel, Action.UseUp, Action.ClosePanel, Action.UseUp), "A panel that is open must always close")
}
