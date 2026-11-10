package com.atlas.keyboard

import com.atlas.keyboard.KeyboardEvent.Chosen
import com.atlas.keyboard.KeyboardEvent.CursorStarted
import com.atlas.keyboard.KeyboardEvent.DeletedWord
import com.atlas.keyboard.KeyboardEvent.FieldChanged
import com.atlas.keyboard.KeyboardEvent.FlickPicked
import com.atlas.keyboard.KeyboardEvent.HeldSymbol
import com.atlas.keyboard.KeyboardEvent.ModifierState
import com.atlas.keyboard.KeyboardEvent.Pushed
import com.atlas.keyboard.KeyboardEvent.Recognised
import com.atlas.keyboard.KeyboardEvent.Tapped
import com.atlas.keyboard.Modifiers.Level
import com.atlas.keyboard.Modifiers.Panel

private fun keys(panel: Panel = Panel.Closed, ctrl: Boolean = false) = ModifierState(Level.Off, Level.Off, panel, ctrl)
private fun swiped(count: Int) = List(count) { Recognised(listOf(GuideScript.wordAt(it))) }

/** The real things a person does, in the order of the guide. */
private val walk: List<Pair<String, List<KeyboardEvent>>> = listOf(
    "type" to listOf(Pushed(29), FieldChanged("Nothing", 7, 7), FieldChanged("Nothing ", 8, 8)),
    "swipe" to swiped(1),
    "strip" to listOf(Chosen("to")),
    "pick" to listOf(Chosen("to"), FlickPicked(1)),
    "flick" to listOf(DeletedWord(held = false)),
    "steer" to listOf(CursorStarted(false), FieldChanged("Nothing to unlearn", 18, 18),
        FieldChanged("Nothing to unlearn", 14, 14)),
    "symbols" to listOf(keys(Panel.Symbols), keys(Panel.Closed)),
    "panel" to listOf(keys(Panel.Emoji)),
)

fun main() {
    everyStepIsReachable()
    theStepsHaveOkayWords()
    oneStepForEachIdea()
    theScriptMovesOnWithEachWord()
    theStripOffersTheScriptsWord()
    stepsStartInTheRightMenu()
    wrongEventsDoNotFinish()
    hints()
    theCursorHasToMove()
    settingsThatStepsNeed()
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
    val flow = GuideFlow({ GuideSettings(panelOpen = true, flickToPick = true) })
    for ((step, events) in walk) run(flow, events, step)
    same(flow.step.id, "end", "The walk ends at the ending")
    same(flow.finished, true, "The ending is the end")
    same(walk.map { it.first }, GUIDE_STEPS.dropLast(1).map { it.id }, "The walk covers each step, in order")
    // Flick up to pick is off at first, and then its slide does not show.
    val off = GuideFlow({ GuideSettings(panelOpen = true, flickToPick = false) })
    for ((step, events) in walk.filter { it.first != "pick" }) run(off, events, step)
    same(off.step.id, "end", "With flick up to pick off, the walk skips its slide")
}

private fun theStepsHaveOkayWords() {
    same(GUIDE_STEPS.map { it.id }.toSet().size, GUIDE_STEPS.size, "Step ids must be unique")
    for (step in GUIDE_STEPS) {
        for (text in listOfNotNull(step.title, step.body, step.done, step.help, step.chapter.label)) {
            // The phone's name is the one number that may show.
            same(text.replace("Titan 2 Elite", "").any { it.isDigit() }, false, "\"$text\" must not have a number")
            same(text.isBlank(), false, "A step must have words")
        }
        same(step.title.length <= 32, true, "Title \"${step.title}\" must stay short")
        same(step.body.length <= 72, true, "Body \"${step.body}\" must stay short")
        if (step.chapter != GuideChapter.End) same(step.title.endsWith("."), true, "Title \"${step.title}\" reads as one sentence")
    }
    same(GUIDE_STEPS.first().id, "type", "The guide starts with the typing that everyone knows")
    same(GUIDE_STEPS.last().title, "TitanKeys", "The guide ends with the name, which flies together from Titan and Keys")
    same(GUIDE_STEPS.last().title.endsWith("Keys") && GUIDE_STEPS.last().title.startsWith("Titan"), true, "The name has its two halves")
    same(GUIDE_STEPS.last().body, "The keys your Titan deserves.", "The slogan has both words of the name")
    same(GuideChapter.End.label, "Built for Titan 2 Elite", "The ending says which phone it is built for")
    same(GUIDE_STEPS.none { listOf(it.title, it.body).any { text -> text.contains("thumb", ignoreCase = true) } }, true,
        "The slides do not say how to hold the phone, as some type with other fingers")
    same(GUIDE_STEPS.map { it.chapter }.distinct(), GuideChapter.entries, "The story comes first, then the end")
    same(GUIDE_STEPS.count { it.pause }, 0, "The story has no stops")
    same(GuideScript.TEXT.any { it.isDigit() }, false, "The script must be easy to swipe, with no numbers")
    for (word in GuideScript.words) same(GuideScript.letters(word).isNotEmpty(), true, "Each word of the script has letters to swipe: $word")
    same(GuideScript.TEXT.contains("two hands or one") && GuideScript.TEXT.contains("ultimate"), true,
        "The script says that it types with two hands and one, and that it is the ultimate keyboard")
    same(GuideScript.TEXT.contains("TitanKeys") && GuideScript.TEXT.endsWith("the keys your Titan deserves."), true,
        "The script names TitanKeys, and ends on the slogan")
    // Setup is slide zero, so its words follow the same rules.
    for (part in SetupPart.entries) {
        for (text in listOfNotNull(part.title, part.body, part.action, part.help))
            same(text.any { it.isDigit() }, false, "\"$text\" must not have a number")
        same(part.title.length <= 32 && part.title.endsWith("."), true, "Setup title \"${part.title}\" is one short sentence")
        same(part.body.length <= 72, true, "Setup body \"${part.body}\" must stay short")
    }
    same(SetupPart.entries.indexOf(SetupPart.Enable) < SetupPart.entries.indexOf(SetupPart.Choose), true,
        "The keyboard is switched on before it can be chosen")
}

/** One step teaches each idea, and keeps guiding for as long as you try it. */
private fun oneStepForEachIdea() {
    same(GUIDE_STEPS.count { it.needs == GuideNeed.SwipeTyping && it.cue == GuideCue.NextWord && !it.nearMiss }, 1, "Only one step swipes words")
    same(GUIDE_STEPS.count { it.cue is GuideCue.Flick }, 1, "One step deletes, the flick and the hold together")
    same(GUIDE_STEPS.count { it.cue == GuideCue.NextWord }, 2, "Swiping and fixing a word follow the line, and nothing else does")
    same(GUIDE_STEPS.count { it.cue is GuideCue.Taps }, 1, "One step steers and selects from Space")
    same(GUIDE_STEPS.filter { it.nearMiss }.map { it.id }, listOf("strip", "pick"), "Only the steps that pick from the strip ask for a near miss")
    same(GuideHooks.takeNearMiss(GuideHooks.FIELD), false, "Nothing is asked for until the step asks")
    GuideHooks.nearMiss = true
    same(GuideHooks.takeNearMiss("some.other.app"), false, "No other field ever gets a near miss")
    same(GuideHooks.takeNearMiss(GuideHooks.FIELD), true, "The guide's field gets one near miss")
    same(GuideHooks.takeNearMiss(GuideHooks.FIELD), false, "One swipe uses it up")
}

private fun theScriptMovesOnWithEachWord() {
    same(GuideScript.progress(""), 0, "An empty text has typed nothing")
    same(GuideScript.progress("Nothing"), 1, "A swiped word counts at once")
    same(GuideScript.progress("Nothing t"), 1, "The start of the next word does not count yet")
    same(GuideScript.progress("Nothing to"), 2, "A whole word counts")
    same(GuideScript.progress("Nothing tu"), 2, "A word that came out different still moves the line on, so nothing waits on it")
    same(GuideScript.progress("Nothing to "), 2, "A space ends a word")
    same(GuideScript.wordAt(GuideScript.words.size), GuideScript.words.first(), "After the last word the script starts again")
    val first = GuideScript.continueFrom(0, 1)
    same(first.startsWith("Nothing to unlearn:") && first.endsWith("."), true, "From the start, the first sentence comes")
    val middle = GuideScript.continueFrom(3, 4)
    same(middle.startsWith("type with two hands"), true, "Text goes on from where the field is, so the line above stays in step")
    same(middle.endsWith("."), true, "Added text ends with a full stop")
    same(GuideScript.continueFrom(3, 30).split(' ').size >= 30, true, "Added text has as many words as the step needs")
    same(GuideScript.slip("key"), "kye", "A slip swaps two letters after the first")
    same(GuideScript.slip("a"), "aa", "A short word gets a doubled letter")
}

/** In the guide's field, and only there, a word that comes out different has the script's word on the strip. */
private fun theStripOffersTheScriptsWord() {
    GuideHooks.script = null
    same(GuideHooks.intended(GuideHooks.FIELD, ""), null, "With the guide closed, nothing is offered")
    GuideHooks.script = { before -> GuideScript.wordAt(GuideScript.progress(before)).trim { !it.isLetter() } }
    same(GuideHooks.intended("some.other.app", ""), null, "No other field is ever touched")
    same(GuideHooks.intended(GuideHooks.FIELD, ""), "Nothing", "The first word is the start of the script")
    same(GuideHooks.intended(GuideHooks.FIELD, "Nothing to "), "unlearn", "Punctuation is left for you to type")
    same(GuideHooks.intended(GuideHooks.FIELD, "Nothing tp "), "unlearn", "A word that came out different still moves on")
    GuideHooks.script = null
}

/** The guide puts the keyboard in the menu that each step needs, and each step can show its gesture by itself. */
private fun stepsStartInTheRightMenu() {
    val byId = GUIDE_STEPS.associateBy { it.id }
    same(byId.getValue("symbols").setup, Panel.Closed, "The symbol step starts closed, so that Sym opens the panel")
    same(byId.getValue("panel").setup, Panel.Closed, "The emoji step starts closed, so that Sym two times shows the emoji")
    for (step in GUIDE_STEPS.filter { !it.usesPanel }) same(step.setup, Panel.Closed, "${step.id} types, so the panel is closed for it")
    for (step in GUIDE_STEPS.filter { it.chapter != GuideChapter.End })
        same(step.demo != null, true, "${step.id} must show its gesture by itself when nobody acts")
    same(byId.getValue("symbols").demo, GuideDemo.HoldLetter("J"), "The symbol slide shows J held down, the key that it names")
}

private fun wrongEventsDoNotFinish() {
    val flow = GuideFlow({ GuideSettings() })
    same(flow.step.id, "type", "The guide starts with typing")
    same(flow.event(FieldChanged("key ", 4, 4)), GuideVerdict.Wait, "A word that no key typed does not finish typing")
    flow.advance()
    same(flow.step.id, "swipe", "Next is the swipe")
    same(flow.event(Chosen("faster")) is GuideVerdict.Done, false, "Touching a word must not finish the swipe")
    same(flow.event(FieldChanged("faster", 6, 6)) is GuideVerdict.Done, false, "Typing must not finish the swipe")
    same(flow.event(Pushed(62)), GuideVerdict.Wait, "Space is not a letter, so it says nothing")
}

private fun hints() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("swipe")
    same(flow.event(Tapped("A")) is GuideVerdict.Hint, true, "A tap on the swipe step gives a hint")
    same(flow.event(Pushed(30)) is GuideVerdict.Hint, true, "A push on the swipe step gives a hint")
    same(flow.event(Recognised(emptyList())), GuideVerdict.Wait, "A swipe that typed nothing does not count")
    same(flow.step.id, "swipe", "A hint does not move the guide on")
    // Mistakes that are easy to make say what happened, and do not count against you.
    val sym = flow.event(keys(Panel.Symbols))
    same(sym is GuideVerdict.Hint && sym.text.contains("Sym"), true, "Opening the panel by chance says so")
    val alt = flow.event(ModifierState(Level.Off, Level.Once, Panel.Closed, false))
    same(alt is GuideVerdict.Hint && alt.text.contains("Alt"), true, "Alt by chance says that the touchpad does not type")
    same(flow.event(CursorStarted(false)) is GuideVerdict.Hint, true, "Starting the cursor by chance says so")
    same(flow.event(HeldSymbol("(")) is GuideVerdict.Hint, true, "A held symbol by chance says so")
    same(flow.event(Recognised(listOf("whatever"))), GuideVerdict.Done, "One swiped word introduces swiping, whatever the word was")
    val panel = GuideFlow({ GuideSettings() })
    panel.start("symbols")
    same(panel.event(keys(Panel.Symbols)), GuideVerdict.Wait, "A step that wants the panel is not told off for it")
    same(panel.event(keys(Panel.Closed)), GuideVerdict.Done, "Closing the panel, by a symbol or by Sym, finishes it")
    panel.start("symbols")
    same(panel.event(HeldSymbol("(")), GuideVerdict.Done, "Holding a letter for its symbol finishes it too")
    val steer = GuideFlow({ GuideSettings() })
    steer.start("steer")
    same(steer.event(Recognised(listOf("word"))) is GuideVerdict.Hint, true, "A swiped word on the steer step says to start on Space")
    same(steer.event(CursorStarted(true)), GuideVerdict.Wait, "Three touches and a swipe up are no mistake here")
    val emoji = GuideFlow({ GuideSettings() })
    emoji.start("panel")
    same(emoji.event(keys(Panel.Symbols)), GuideVerdict.Wait, "One push of Sym is not the emoji yet")
    same(emoji.event(keys(Panel.Settings)), GuideVerdict.Done, "The quick settings finish it too")
}

private fun theCursorHasToMove() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("steer")
    same(flow.event(FieldChanged("Move the cursor", 15, 15)), GuideVerdict.Wait, "A cursor change before the drag starts means nothing")
    flow.event(CursorStarted(false))
    same(flow.event(FieldChanged("Move the cursor", 14, 14)), GuideVerdict.Wait, "The first change sets the mark")
    same(flow.event(FieldChanged("Move the cursor", 13, 13)), GuideVerdict.Wait, "One letter is not enough")
    same(flow.event(FieldChanged("Move the cursor", 10, 10)), GuideVerdict.Done, "A few letters finishes it")

    val select = GuideFlow({ GuideSettings() })
    select.start("steer")
    same(select.event(FieldChanged("Select many words", 7, 7)), GuideVerdict.Wait, "A cursor alone is no selection")
    same(select.event(FieldChanged("Select many words", 7, 11)), GuideVerdict.Done, "A selected word finishes it")
}

private fun settingsThatStepsNeed() {
    var settings = GuideSettings(swipeTyping = false, cursorControl = false)
    val flow = GuideFlow({ settings })
    flow.start("swipe")
    same(flow.missing, GuideNeed.SwipeTyping, "A step can ask for its setting")
    flow.start("steer")
    same(flow.missing, GuideNeed.CursorControl, "The steer step asks for cursor control")
    settings = GuideSettings(cursorControl = true)
    same(flow.missing, null, "Nothing is missing when the setting is on")
    flow.start("symbols")
    same(flow.missing, GuideNeed.HoldSymbols, "Holding a letter is off at first, so the symbol step offers it")
    settings = GuideSettings(cursorControl = true, holdSymbols = true)
    same(flow.missing, null, "With holding on, the symbol step needs nothing more")
    same(GuideSettings().has(GuideNeed.FnAsCtrl), false, "fn is not Ctrl by default")
}

private fun goingBack() {
    val flow = GuideFlow({ GuideSettings() })
    same(flow.canGoBack, false, "The first step has nothing before it")
    same(flow.back(), false, "Back from the first step does nothing")
    flow.start("swipe")
    same(flow.back(), true, "Back goes to the step before")
    same(flow.step.id, "type", "The step before swiping is typing")
}

private fun resuming() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("panel")
    same(flow.resumeId, "panel", "A step that sets up its own menu comes back to itself")
    flow.start("flick")
    same(flow.resumeId, "flick", "Other steps come back to themselves")
    flow.start("missing step")
    same(flow.step.id, "type", "An unknown step starts from the beginning")
    same(flow.chapterIndex, 0, "The first part is number zero")
    same(flow.chapterCount, GuideChapter.entries.size, "Each part counts")
    same(flow.stepCount, GUIDE_STEPS.size - 1, "The bar counts every step but the ending")
    same(flow.finished, false, "The start is not the end")
}
