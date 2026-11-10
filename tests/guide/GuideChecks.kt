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
import com.atlas.keyboard.KeyboardEvent.Pushed
import com.atlas.keyboard.KeyboardEvent.Recognised
import com.atlas.keyboard.KeyboardEvent.Tapped
import com.atlas.keyboard.Modifiers.Level
import com.atlas.keyboard.Modifiers.Panel

private fun keys(panel: Panel = Panel.Closed, ctrl: Boolean = false) = ModifierState(Level.Off, Level.Off, panel, ctrl)
private fun swiped(count: Int) = List(count) { Recognised(listOf(GuideScript.wordAt(it))) }

/** The real things a person does, in the order of the guide. The stop between the parts needs nothing. */
private val walk: List<Pair<String, List<KeyboardEvent>>> = listOf(
    "touch" to listOf(Moved(1f, false), Moved(3.5f, false)),
    "swipe" to swiped(3),
    "type" to listOf(Pushed(29), FieldChanged("TitanKeys turns every a", 23, 23), FieldChanged("TitanKeys turns every key ", 26, 26)),
    "strip" to listOf(Chosen("key")),
    "flick" to listOf(DeletedWord(held = false)),
    "cursor-move" to listOf(CursorStarted(false), FieldChanged("TitanKeys turns every key", 25, 25),
        FieldChanged("TitanKeys turns every key", 21, 21)),
    "sym" to listOf(keys(Panel.Symbols), keys(Panel.Closed)),
    "basics" to emptyList(),
    "sentence" to swiped(8),
    "one-hand" to swiped(3),
    "flick-hold" to listOf(DeletedWord(held = false), DeletedWord(held = true), DeletedWord(held = true), DeletedWord(held = true)),
    "hold-letter" to listOf(Pushed(38), FieldChanged("j", 1, 1), HeldSymbol("(")),
    "emoji-open" to listOf(keys(Panel.Emoji)),
    "emoji-slide" to listOf(GroupPicked(2)),
    "quick-open" to listOf(keys(Panel.Settings)),
    "quick-close" to listOf(keys(Panel.Closed)),
    "hold-ctrl" to listOf(keys(ctrl = true)),
    "cursor-word" to listOf(DoubleTapped, FieldChanged("Select a word", 7, 11)),
    "cursor-words" to listOf(CursorStarted(true), FieldChanged("Select many words at one time", 12, 29)),
)

fun main() {
    everyStepIsReachable()
    theStepsHaveOkayWords()
    theScriptMovesOnWithEachWord()
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
    if (events.isEmpty()) same(flow.step.pause, true, "Only a stop needs nothing to finish")
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
    same(GUIDE_STEPS.map { it.chapter }.distinct(), GuideChapter.entries, "The basics come first, then the exercises, then the end")
    same(GUIDE_STEPS.count { it.pause }, 1, "One stop between the basics and the exercises")
    same(GuideScript.TEXT.any { it.isDigit() }, false, "The script must be easy to swipe, with no numbers")
    for (word in GuideScript.words) same(GuideScript.letters(word).isNotEmpty(), true, "Each word of the script has letters to swipe: $word")
    same(GuideScript.TEXT.contains("one hand") && GuideScript.TEXT.contains("two thumbs") && GuideScript.TEXT.contains("ultimate"), true,
        "The script says that it types with two hands and one, and that it is the ultimate keyboard")
}

private fun theScriptMovesOnWithEachWord() {
    same(GuideScript.progress(""), 0, "An empty text has typed nothing")
    same(GuideScript.progress("TitanKeys"), 1, "A swiped word counts at once")
    same(GuideScript.progress("TitanKeys tu"), 1, "The start of the next word does not count yet")
    same(GuideScript.progress("TitanKeys turns"), 2, "A whole word counts")
    same(GuideScript.progress("TitanKeys turtle"), 2, "A word that came out different still moves the line on, so nothing waits on it")
    same(GuideScript.progress("TitanKeys turns "), 2, "A space ends a word")
    same(GuideScript.wordAt(GuideScript.words.size), GuideScript.words.first(), "After the last word the script starts again")
    same(GuideScript.sentenceFrom(0).startsWith("TitanKeys turns"), true, "The first sentence is the start of the script")
    same(GuideScript.sentenceFrom(3).endsWith("."), true, "A sentence ends with a full stop")
    same(GuideScript.sentenceFrom(3).startsWith("Type with two thumbs"), true, "Text from the middle of a sentence starts at the next one")
}

private fun wrongEventsDoNotFinish() {
    val flow = GuideFlow({ GuideSettings() })
    same(flow.event(Moved(1f, lifted = true)), GuideVerdict.Wait, "A small touch must not finish the first step")
    same(flow.event(Pushed(29)) is GuideVerdict.Hint, true, "Pushing a letter on the touch step says to touch lightly")
    same(flow.event(Pushed(62)), GuideVerdict.Wait, "Space is not a letter, so it says nothing")
    flow.advance()
    same(flow.step.id, "swipe", "Next is the swipe")
    same(flow.event(Chosen("faster")) is GuideVerdict.Done, false, "Touching a word must not finish the swipe")
    same(flow.event(FieldChanged("faster", 6, 6)) is GuideVerdict.Done, false, "Typing must not finish the swipe")
    flow.advance()
    same(flow.step.id, "type", "Next is typing")
    same(flow.event(FieldChanged("key ", 4, 4)), GuideVerdict.Wait, "A word that no key typed does not finish typing")
}

private fun hints() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("swipe")
    same(flow.event(Tapped("A")) is GuideVerdict.Hint, true, "A tap on the swipe step gives a hint")
    same(flow.event(Pushed(30)) is GuideVerdict.Hint, true, "A push on the swipe step gives a hint")
    same(flow.event(Recognised(listOf("fade"))), GuideVerdict.Wait, "Any word moves on, and one is not enough")
    same(flow.event(Recognised(listOf("anything"))), GuideVerdict.Wait, "Two words are not enough")
    same(flow.step.id, "swipe", "A hint does not move the guide on")
    // Mistakes that are easy to make say what happened, and do not count against you.
    val sym = flow.event(keys(Panel.Symbols))
    same(sym is GuideVerdict.Hint && sym.text.contains("Sym"), true, "Opening the panel by chance says so")
    val alt = flow.event(ModifierState(Level.Off, Level.Once, Panel.Closed, false))
    same(alt is GuideVerdict.Hint && alt.text.contains("Alt"), true, "Alt by chance says that the touchpad does not type")
    same(flow.event(CursorStarted(false)) is GuideVerdict.Hint, true, "Starting the cursor by chance says so")
    same(flow.event(HeldSymbol("(")) is GuideVerdict.Hint, true, "A held symbol by chance says so")
    same(flow.event(Recognised(listOf("whatever"))), GuideVerdict.Done, "The third word finishes the swipe, whatever it was")
    val panel = GuideFlow({ GuideSettings() })
    panel.start("sym")
    same(panel.event(keys(Panel.Symbols)), GuideVerdict.Wait, "A step that wants the panel is not told off for it")
    same(panel.event(keys(Panel.Closed)), GuideVerdict.Done, "Closing the panel, by a symbol or by Sym, finishes it")
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
    same(flow.step.id, "quick-close", "With the panel open there is a panel to close")
    settings = GuideSettings(holdSymbols = true, panelOpen = false)
    flow.start("quick-open")
    flow.advance()
    same(flow.step.id, "hold-ctrl", "If the panel is closed there is nothing to close")
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
    flow.start("type")
    same(flow.back(), true, "Back goes to the step before")
    same(flow.step.id, "swipe", "The step before typing is the swipe")
    val skipping = GuideFlow({ GuideSettings(panelOpen = false) })
    skipping.start("hold-ctrl")
    skipping.back()
    same(skipping.step.id, "quick-open", "Back passes over the steps that do not show")
}

private fun resuming() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("emoji-slide")
    same(flow.resumeId, "emoji-open", "A step that needs the panel comes back to the step that opens it")
    flow.start("flick")
    same(flow.resumeId, "flick", "Other steps come back to themselves")
    flow.start("missing step")
    same(flow.step.id, "touch", "An unknown step starts from the beginning")
    same(flow.chapterIndex, 0, "The first part is number zero")
    same(flow.chapterCount, GuideChapter.entries.size, "Each part counts")
    same(flow.stepCount, GUIDE_STEPS.size - 1, "The bar counts every step but the ending")
    same(flow.finished, false, "The start is not the end")
}
