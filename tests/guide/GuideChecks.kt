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
import com.atlas.keyboard.KeyboardEvent.Pushed
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
    "type" to listOf(FieldChanged("Real keys, smart fixes: t", 25, 25), FieldChanged("Real keys, smart fixes: the ", 28, 28)),
    "swipe" to listOf(Recognised(listOf("Faster", "fast"))),
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
    "cursor-word" to listOf(DoubleTapped, FieldChanged("Select a word without the screen", 26, 32)),
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
    goingBack()
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
            // The phone's name is the one number that may show.
            same(text.replace("Titan 2 Elite", "").any { it.isDigit() }, false, "\"$text\" must not have a number")
            same(text.isBlank(), false, "A step must have words")
        }
        same(step.title.length <= 32, true, "Title \"${step.title}\" must stay short")
        same(step.body.length <= 72, true, "Body \"${step.body}\" must stay short")
        if (step.chapter != GuideChapter.End)
            same(step.title.endsWith(".") || step.title == step.title.lowercase(), true, "Title \"${step.title}\" reads as one instruction")
    }
    same(GUIDE_STEPS.last().title, "TitanKeys", "The guide ends with the name")
    same(GUIDE_STEPS.last().body, "Built for Titan 2 Elite.", "The guide ends with what it is built for")
    same(GUIDE_STEPS.last().chapter, GuideChapter.End, "The last step is the ending")
    same(GUIDE_STEPS.map { it.chapter }.distinct().size, GuideChapter.entries.size, "Each chapter has steps")
}

private fun wrongEventsDoNotFinish() {
    val flow = GuideFlow({ GuideSettings() })
    same(flow.event(Moved(1f, lifted = true)), GuideVerdict.Wait, "A small touch must not finish the first step")
    same(flow.event(Pushed(29)) is GuideVerdict.Hint, true, "Pushing a letter on the touch step says to touch lightly")
    same(flow.event(Pushed(62)), GuideVerdict.Wait, "Space is not a letter, so it says nothing")
    flow.advance()
    same(flow.step.id, "type", "Next is typing")
    same(flow.event(FieldChanged("Real keys, smart fixes: teh", 27, 27)), GuideVerdict.Wait, "A typo before its space is not done")
    same(flow.event(FieldChanged("Real keys, smart fixes: teh ", 28, 28)) is GuideVerdict.Hint, true, "A typo that stays says that autocorrect is off")
    flow.advance()
    same(flow.step.id, "swipe", "Next is the swipe")
    same(flow.event(Chosen("faster")) is GuideVerdict.Done, false, "Touching a word must not finish the swipe")
    same(flow.event(FieldChanged("faster", 6, 6)) is GuideVerdict.Done, false, "Typing must not finish the swipe")
}

private fun hints() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("swipe")
    val wrong = flow.event(Recognised(listOf("fade", "fast")))
    same(wrong is GuideVerdict.Hint, true, "A wrong word gives a hint")
    same((wrong as GuideVerdict.Hint).text.contains("fade"), true, "The hint says what it read")
    same(flow.event(Tapped("A")) is GuideVerdict.Hint, true, "A tap on the swipe step gives a hint")
    same(flow.step.id, "swipe", "A hint does not move the guide on")
    // Mistakes that are easy to make say what happened, and do not count against you.
    val sym = flow.event(keys(Panel.Symbols))
    same(sym is GuideVerdict.Hint && sym.text.contains("Sym"), true, "Opening the panel by chance says so")
    val alt = flow.event(ModifierState(Level.Off, Level.Once, Panel.Closed, false))
    same(alt is GuideVerdict.Hint && alt.text.contains("Alt"), true, "Alt by chance says that the touchpad does not type")
    same(flow.event(CursorStarted(false)) is GuideVerdict.Hint, true, "Starting the cursor by chance says so")
    same(flow.event(HeldSymbol("(")) is GuideVerdict.Hint, true, "A held symbol by chance says so")
    val done = flow.event(Recognised(listOf("Faster")))
    same(done, GuideVerdict.Done, "A word in any case finishes the swipe")
    val panel = GuideFlow({ GuideSettings() })
    panel.start("sym-open")
    same(panel.event(keys(Panel.Symbols)), GuideVerdict.Done, "A step that wants the panel is not told off for it")
    val cursor = GuideFlow({ GuideSettings() })
    cursor.start("cursor-words")
    same(cursor.event(CursorStarted(false)) is GuideVerdict.Hint, true, "Two touches on the word step say that it needs three")
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

private fun goingBack() {
    val flow = GuideFlow({ GuideSettings() })
    same(flow.canGoBack, false, "The first step has nothing before it")
    same(flow.back(), false, "Back from the first step does nothing")
    flow.start("swipe")
    same(flow.back(), true, "Back goes to the step before")
    same(flow.step.id, "type", "The step before the swipe is typing")
    val skipping = GuideFlow({ GuideSettings(holdSymbols = true, panelOpen = false) })
    skipping.start("hold-letter")
    skipping.back()
    same(skipping.step.id, "quick-open", "Back passes over the steps that do not show")
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
