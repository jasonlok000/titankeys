package com.atlas.engine

import com.atlas.keyboard.Layout
import com.atlas.keyboard.MenuBar
import com.atlas.keyboard.MenuKeys
import com.atlas.keyboard.SteadyKey
import java.io.File

/**
 * The word menu without a phone (owner, 2026-11-13: the hover was reported lost after a press again and again, so it is proved here
 * instead of on anyone's word): the hover after a press, up and down, from I, O, K and L, in every order the touchpad's lift and
 * the key came in on the owner's phone.
 */
fun main() {
    theHover()
    theKeyboardMakesTheseCalls()
    println("Word menu checks passed")
}

// The hover -----------------------------------------------------------------------------------------------------------------------

/**
 * How a press under a resting finger reaches the keyboard, from the owner's raw log of 2026-11-11 (1,907 presses with the finger
 * already on the pressed key). The touchpad ends the touch with a lift, nearly always after the key is let go: a median 124 ms
 * after, at most 0.9 s. Then it reports nothing until the finger moves or lands again (no touch between 95% of 3,197 quick presses
 * of one key). The plain 150 ms wait after that lift is what cleared the pill before (owner, 2026-11-13).
 */
private enum class Order(val describe: String) {
    Typical("down, up 86 ms later, the lift 124 ms after the up (the median)"),
    Together("down and up together, the lift 115 ms later"),
    LateLift("down, up 90 ms later, the lift 900 ms after the up"),
    LiftWhileDown("down, the lift 20 ms later, up 150 ms after the down (5% of presses)"),
    LiftFirst("the lift, then down 40 ms later, up 100 ms after that"),
}

/**
 * A press of [key] at [time] under the finger, in [order]. The touchpad then reports the finger again [back] ms after the press's
 * lift, on [rest], or, when [back] is null, as the phone mostly does: never, while the finger stays still.
 */
private fun MenuBar.pressUnderFinger(key: String, time: Long, order: Order, back: Long? = null, rest: String = key) {
    val lifted = when (order) {
        Order.Typical -> { keyDown(key, time); keyUp(key, time + 86); lift(time + 210); time + 210 }
        Order.Together -> { press(key, time); lift(time + 115); time + 115 }
        Order.LateLift -> { keyDown(key, time); keyUp(key, time + 90); lift(time + 990); time + 990 }
        Order.LiftWhileDown -> { keyDown(key, time); lift(time + 20); keyUp(key, time + 150); time + 20 }
        Order.LiftFirst -> { lift(time - 40); keyDown(key, time); keyUp(key, time + 100); time - 40 }
    }
    if (back != null) touch(rest, maxOf(lifted, time + 150) + back)
}

private fun theHover() {
    val state = { step: MenuKeys.Step, learned: Boolean -> MenuKeys.State(step, learned) }
    val (new, saved, favourite) = Triple(MenuKeys.Step.New, MenuKeys.Step.Saved, MenuKeys.Step.Favourite)
    val (i, o) = MenuKeys.SAVE to MenuKeys.FORGET
    for (order in Order.entries) for (back in listOf(null, 60L)) for ((up, down) in listOf("I" to "O", "K" to "L")) {
        val how = "${order.describe}, the finger ${if (back == null) "still and unreported" else "reported again"}, $up and $down"
        // Up the ladder with I (or K) under one resting finger: the pill stays on I and shows the next step each time.
        val bar = MenuBar(state(new, true))
        bar.touch(up, 0)
        same(bar.pill, i to MenuKeys.Button.Save, "$how: resting on $up shows Save")
        var time = 1_000L
        for ((after, shows) in listOf(saved to MenuKeys.Button.Favourite, favourite to MenuKeys.Button.Favourited,
                favourite to MenuKeys.Button.Favourited)) {
            bar.pressUnderFinger(up, time, order, back)
            // Right after the press, past the press's lift and the plain wait that cleared it before, and long after.
            for (later in listOf(1L, 300L, 1_200L, 2_900L)) {
                bar.at(time + later)
                same(bar.state.step, after, "$how: the press moved the word to $after")
                same(bar.pill, i to shows, "$how: $later ms after the press, the pill stays on I and shows $shows")
            }
            time += 3_000
        }
        // Down the ladder with O (or L): from Favourite to Saved, then to not saved, where O has nothing left, so no pill.
        bar.touch(down, time)
        same(bar.pill, o to MenuKeys.Button.Unfavourite, "$how: on $down, Unfavourite")
        time += 1_000
        for ((after, shows) in listOf(saved to MenuKeys.Button.Unsave, new to null)) {
            bar.pressUnderFinger(down, time, order, back)
            for (later in listOf(1L, 300L, 1_200L, 2_900L)) {
                bar.at(time + later)
                same(bar.state.step, after, "$how: the press moved the word to $after")
                same(bar.pill, shows?.let { o to it }, "$how: $later ms after the press, O shows ${shows ?: "nothing: no pill"}")
            }
            time += 3_000
        }
        // Not once did the hover leave the finger's key: the first mark is I's, the second O's, and nothing else.
        same(bar.marks.map { it.second }, listOf(i, o), "$how: the hover never went away, ${bar.marks}")
        same(bar.picked, listOf("Save", "Favourite", "Favourited", "Unfavourite", "Unsave"), "$how: each press one step")
        same(bar.open, true, "$how: the menu stays open")
    }
    for (order in Order.entries) {
        val how = order.describe
        // A finger that moves to another key after the press takes the hover with it at once, and off the buttons ends it.
        val moved = MenuBar(state(new, true))
        moved.touch("I", 0)
        moved.pressUnderFinger("I", 1_000, order)
        moved.touch("O", 2_500)
        same(moved.pill, o to MenuKeys.Button.Unsave, "$how: moving to O hovers O's button at once")
        moved.touch("U", 2_600)
        same(moved.pill, null, "$how: moving off the buttons ends the hover")
        // A finger the touchpad shows again, then lifting: the pill goes after the plain wait, as for any lift.
        val lifted = MenuBar(state(new, false))
        lifted.touch("I", 0)
        lifted.pressUnderFinger("I", 1_000, order, back = 60)
        lifted.lift(3_000)
        same(lifted.at(3_000 + SteadyKey.LIFT_MILLIS - 1).pill, i to MenuKeys.Button.Favourite, "$how: a short lift keeps it")
        same(lifted.at(3_000 + SteadyKey.LIFT_MILLIS).pill, null, "$how: a real lift ends it")
        // A finger that lifts while the touchpad is quiet can't be seen: the pill stays until the finger lands again.
        val unseen = MenuBar(state(new, false))
        unseen.touch("I", 0)
        unseen.pressUnderFinger("I", 1_000, order)
        same(unseen.at(10_000).pill, i to MenuKeys.Button.Favourite, "$how: no touchpad event, no change")
        unseen.touch("H", 10_500)
        same(unseen.pill, null, "$how: the finger landing elsewhere ends it")
    }
    // A press with the finger resting elsewhere: the pill goes to the pressed key, where the finger is, and stays there.
    val elsewhere = MenuBar(state(saved, false))
    elsewhere.touch("U", 0)
    same(elsewhere.pill, null, "Resting on U hovers nothing")
    elsewhere.pressUnderFinger("I", 1_000, Order.Typical)
    same(elsewhere.at(3_000).pill, i to MenuKeys.Button.Favourited, "Pressing I hovers I")
    // A slow app: the touchpad's lift reached the keyboard 200 ms before the key did, so the plain wait cleared the hover first.
    // The press brings it back on the pressed key.
    val slow = MenuBar(state(new, false))
    slow.touch("I", 0)
    slow.lift(800)
    slow.press("I", 1_000)
    same(slow.at(3_000).pill, i to MenuKeys.Button.Favourite, "A late key brings the hover back")
    // Back has no hover, and closes the menu, from P or Backspace.
    for (back in listOf("P", Layout.BACKSPACE)) {
        val bar = MenuBar(state(saved, false))
        bar.touch(back, 0)
        same(bar.mark to bar.pill, -1 to null, "$back hovers nothing: Back has no pill")
        bar.pressUnderFinger(back, 1_000, Order.Typical)
        same(bar.open to bar.pill, false to null, "$back closes the menu")
        same(bar.picked, listOf("Back"), "$back picks Back")
    }
    // Forget closes the menu with no hover left over.
    val forget = MenuBar(state(new, true))
    forget.touch("O", 0)
    same(forget.pill, o to MenuKeys.Button.Forget, "O shows Forget on a learned word")
    forget.pressUnderFinger("O", 1_000, Order.Typical)
    same(forget.open to forget.pill, false to null, "Forget closes the menu")
    // At the bottom of an unknown word, O has no button: its key picks nothing, so it types and closes the menu, as any key does.
    val bottom = MenuBar(state(new, false))
    bottom.touch("O", 0)
    same(bottom.pill, null, "O at the bottom has no pill")
    bottom.press("O", 1_000)
    same(bottom.open to bottom.picked, false to listOf("types O"), "O with no button types, and the menu closes")
    // A hover with no press goes after the plain wait, as before.
    val plain = MenuBar(state(new, false))
    plain.touch("I", 0)
    plain.lift(1_000)
    same(plain.at(1_000 + SteadyKey.LIFT_MILLIS - 1).pill, i to MenuKeys.Button.Save, "A short lift keeps the pill")
    same(plain.at(1_000 + SteadyKey.LIFT_MILLIS).pill, null, "A real lift ends it after the usual wait")
}

/**
 * [MenuBar] makes AtlasIme's calls in AtlasIme's order. These are the lines it copies; a change to one of them must be made in
 * [MenuBar] too, or this check fails.
 */
private fun theKeyboardMakesTheseCalls() {
    val ime = File("android/kotlin/com/atlas/keyboard/AtlasIme.kt").readText()
    for (line in listOf(
        "barHover.keyDown(keyCode)",
        "val action = menuAction(pressed)",
        "barHover.press(pressed)",
        "markMenu(restingKey, quiet = true)",
        "val menuRelease = keyCode == menuKey && menuOpen",
        "if (menuRelease) barHover.release(keyCode, event.eventTime)",
        "val barLast = !menuRelease && barHover.keyUp(keyCode, event.eventTime) && !panelOpen",
        "if (panelLast || barLast) {",
        "if (barHover.expire(now)) {",
        "barHover.lift(event.eventTime)",
        "if (last == null || !barHover.touch(last.x, last.y)) return",
        "if (column == MenuKeys.SAVE || column == MenuKeys.FORGET) column else -1",
    )) check(line in ime) { "AtlasIme no longer has: $line" }
    // The strip keeps the hover through a new state: a new menu notice for the same word never resets the mark or the pill.
    val strip = File("android/kotlin/com/atlas/keyboard/Strip.kt").readText()
    val showNotice = strip.substringAfter("private fun showNotice(").substringBefore("\n    private var menuIn")
    check("menuMark" !in showNotice) { "A new notice must not touch the hover's mark" }
    check("pillShown = -1" in showNotice.substringAfter("if (menu == null) {").substringBefore("} else if")) {
        "Only a closed menu lets go of the pill"
    }
    check("pillShown" !in showNotice.substringAfter("} else if")) {
        "A new step keeps the pill"
    }
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}
