package com.atlas.keyboard

import com.atlas.keyboard.KeyboardEvent.Chosen
import com.atlas.keyboard.KeyboardEvent.CursorStarted
import com.atlas.keyboard.KeyboardEvent.DeletedWord
import com.atlas.keyboard.KeyboardEvent.DoubleTapped
import com.atlas.keyboard.KeyboardEvent.FieldChanged
import com.atlas.keyboard.KeyboardEvent.GroupPicked
import com.atlas.keyboard.KeyboardEvent.HeldSymbol
import com.atlas.keyboard.KeyboardEvent.ModifierState
import com.atlas.keyboard.KeyboardEvent.Moved
import com.atlas.keyboard.KeyboardEvent.PanelSwiped
import com.atlas.keyboard.KeyboardEvent.QuickSettingUsed
import com.atlas.keyboard.KeyboardEvent.Recognised
import com.atlas.keyboard.KeyboardEvent.Resting
import com.atlas.keyboard.KeyboardEvent.Tapped
import com.atlas.keyboard.Modifiers.Level
import com.atlas.keyboard.Modifiers.Panel

private fun keys(panel: Panel = Panel.Closed, ctrl: Boolean = false) = ModifierState(Level.Off, Level.Off, panel, ctrl)

/** The real things a person does, in the order of the guide. */
private val walk: List<Pair<String, List<KeyboardEvent>>> = listOf(
    "touch" to listOf(Moved(1f, false), Moved(3.5f, false)),
    "swipe" to listOf(Recognised(listOf("Hello", "hell"))),
    "strip" to listOf(Chosen("hell")),
    "flick" to listOf(DeletedWord(held = false)),
    "flick-hold" to listOf(DeletedWord(held = false), DeletedWord(held = true), DeletedWord(held = true), DeletedWord(held = true)),
    "sym-open" to listOf(keys(Panel.Symbols)),
    "sym-rest" to listOf(Resting("Q")),
    "sym-page" to listOf(PanelSwiped(0f, -1f, moved = true)),
    "emoji-open" to listOf(keys(Panel.Emoji)),
    "emoji-slide" to listOf(GroupPicked(2)),
    "quick-open" to listOf(keys(Panel.Settings)),
    "quick-hold" to listOf(QuickSettingUsed(QuickSetting.HoldSymbols, on = true)),
    "quick-close" to listOf(keys(Panel.Closed)),
    "hold-letter" to listOf(FieldChanged("j", 1, 1), HeldSymbol("(")),
    "hold-ctrl" to listOf(keys(ctrl = true)),
    "cursor-word" to listOf(DoubleTapped, FieldChanged("Select this word", 12, 16)),
    "cursor-move" to listOf(CursorStarted(false), FieldChanged("Move the cursor along this line", 31, 31),
        FieldChanged("Move the cursor along this line", 28, 28)),
    "cursor-words" to listOf(CursorStarted(true), FieldChanged("Select many words at one time", 12, 29)),
)

fun main() {
    everyStepIsReachable()
    theStepsHaveOkayWords()
    wrongEventsDoNotFinish()
    hints()
    aHeldFlickNeedsMoreWords()
    theCursorHasToMove()
    settingsAndSkipping()
    resuming()
    println("Guide checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    check(actual == expected) { "$message: expected $expected, got $actual" }
}

private fun run(flow: GuideFlow, events: List<KeyboardEvent>, step: String) {
    same(flow.step.id, step, "The guide should be at this step")
    for ((index, event) in events.withIndex()) {
        val verdict = flow.event(event)
        if (index < events.lastIndex) same(verdict is GuideVerdict.Done, false, "$step should wait after $event")
        else same(verdict, GuideVerdict.Done, "$step should finish on $event")
    }
    flow.advance()
}

private fun everyStepIsReachable() {
    val flow = GuideFlow({ GuideSettings(panelOpen = true) })
    for ((step, events) in walk) run(flow, events, step)
    same(flow.step.id, "end", "The walk ends at the ending")
    same(flow.finished, true, "The ending is the end")
    same(walk.map { it.first }, GUIDE_STEPS.dropLast(1).map { it.id }, "The walk covers each step, in order")
}

private fun theStepsHaveOkayWords() {
    same(GUIDE_STEPS.map { it.id }.toSet().size, GUIDE_STEPS.size, "Step ids must be unique")
    for (step in GUIDE_STEPS) {
        for (text in listOfNotNull(step.title, step.body, step.done, step.help)) {
            same(text.any { it.isDigit() }, false, "\"$text\" must not have a number")
            same(text.isBlank(), false, "A step must have words")
        }
        same(step.title.length <= 32, true, "Title \"${step.title}\" must stay short")
        same(step.body.length <= 72, true, "Body \"${step.body}\" must stay short")
        same(step.title.endsWith(".") || step.title == step.title.lowercase(), true, "Title \"${step.title}\" reads as one instruction")
    }
    same(GUIDE_STEPS.last().chapter, GuideChapter.End, "The last step is the ending")
    same(GUIDE_STEPS.map { it.chapter }.distinct().size, GuideChapter.entries.size, "Each chapter has steps")
}

private fun wrongEventsDoNotFinish() {
    val flow = GuideFlow({ GuideSettings() })
    same(flow.event(Moved(1f, lifted = true)), GuideVerdict.Wait, "A small touch must not finish the first step")
    flow.advance()
    same(flow.step.id, "swipe", "Next is the swipe")
    same(flow.event(Chosen("hello")) is GuideVerdict.Done, false, "Touching a word must not finish the swipe")
    same(flow.event(FieldChanged("hello", 5, 5)) is GuideVerdict.Done, false, "Typing must not finish the swipe")
}

private fun hints() {
    val flow = GuideFlow({ GuideSettings() })
    flow.advance()
    val wrong = flow.event(Recognised(listOf("hell", "help")))
    same(wrong is GuideVerdict.Hint, true, "A wrong word gives a hint")
    same((wrong as GuideVerdict.Hint).text.contains("hell"), true, "The hint says what it read")
    same(flow.event(Tapped("A")) is GuideVerdict.Hint, true, "A tap on the swipe step gives a hint")
    same(flow.step.id, "swipe", "A hint does not move the guide on")
    val done = flow.event(Recognised(listOf("Hello")))
    same(done, GuideVerdict.Done, "A word in any case finishes the swipe")
}

private fun aHeldFlickNeedsMoreWords() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("flick-hold")
    same(flow.event(DeletedWord(held = false)), GuideVerdict.Wait, "The first word of a held flick looks like a plain flick")
    same(flow.event(DeletedWord(held = false)) is GuideVerdict.Hint, true, "A second plain flick gets a hint")
    same(flow.event(DeletedWord(held = true)), GuideVerdict.Wait, "One held word is not enough")
    same(flow.event(DeletedWord(held = true)), GuideVerdict.Wait, "Two held words are not enough")
    same(flow.event(DeletedWord(held = true)), GuideVerdict.Done, "A hold that goes on finishes the step")
}

private fun theCursorHasToMove() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("cursor-move")
    same(flow.event(FieldChanged("Move the cursor", 15, 15)), GuideVerdict.Wait, "A selection change before the drag starts means nothing")
    flow.event(CursorStarted(false))
    same(flow.event(FieldChanged("Move the cursor", 14, 14)), GuideVerdict.Wait, "The first change sets the mark")
    same(flow.event(FieldChanged("Move the cursor", 13, 13)), GuideVerdict.Wait, "One letter is not enough")
    same(flow.event(FieldChanged("Move the cursor", 10, 10)), GuideVerdict.Done, "A few letters finishes it")

    val words = GuideFlow({ GuideSettings() })
    words.start("cursor-words")
    words.event(CursorStarted(true))
    same(words.event(FieldChanged("Select many words", 7, 11)), GuideVerdict.Wait, "One word is not many words")
    same(words.event(FieldChanged("Select many words", 7, 17)), GuideVerdict.Done, "Two words finishes it")
}

private fun settingsAndSkipping() {
    var settings = GuideSettings(holdSymbols = true, panelOpen = true)
    val flow = GuideFlow({ settings })
    flow.start("quick-open")
    flow.advance()
    same(flow.step.id, "quick-close", "The step that turns on Hold for symbols does not show when it is on")
    settings = GuideSettings(holdSymbols = true, panelOpen = false)
    flow.start("quick-open")
    flow.advance()
    same(flow.step.id, "hold-letter", "If the panel is closed there is nothing to close")
    settings = GuideSettings(holdSymbols = false, cursorControl = false)
    flow.start("hold-letter")
    same(flow.missing, GuideNeed.HoldSymbols, "A step can ask for its setting")
    flow.start("cursor-word")
    same(flow.missing, GuideNeed.CursorControl, "The cursor steps ask for cursor control")
    settings = GuideSettings(cursorControl = true)
    same(flow.missing, null, "Nothing is missing when the setting is on")
    same(GuideSettings().has(GuideNeed.FnAsCtrl), false, "fn is not Ctrl by default")
}

private fun resuming() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("emoji-slide")
    same(flow.resumeId, "sym-open", "The guide comes back to the start of a chapter")
    flow.start("missing step")
    same(flow.step.id, "touch", "An unknown step starts from the beginning")
    same(flow.chapterIndex, 0, "The first chapter is number zero")
    same(flow.chapterCount, GuideChapter.entries.size, "Each chapter counts")
    same(flow.finished, false, "The start is not the end")
}
