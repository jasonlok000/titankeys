package com.atlas.keyboard

import com.atlas.keyboard.KeyboardEvent.Chosen
import com.atlas.keyboard.KeyboardEvent.GroupPicked
import com.atlas.keyboard.KeyboardEvent.MenuPicked
import com.atlas.keyboard.KeyboardEvent.PanelSwiped
import com.atlas.keyboard.KeyboardEvent.Resting
import com.atlas.keyboard.KeyboardEvent.CursorStarted
import com.atlas.keyboard.KeyboardEvent.DeleteHoldStarted
import com.atlas.keyboard.KeyboardEvent.DeletedWord
import com.atlas.keyboard.KeyboardEvent.FieldChanged
import com.atlas.keyboard.KeyboardEvent.HeldSymbol
import com.atlas.keyboard.KeyboardEvent.ModifierState
import com.atlas.keyboard.KeyboardEvent.Pushed
import com.atlas.keyboard.KeyboardEvent.Recognised
import com.atlas.keyboard.KeyboardEvent.Tapped
import com.atlas.keyboard.KeyboardEvent.WordMenu
import com.atlas.keyboard.Modifiers.Level
import com.atlas.keyboard.Modifiers.Panel

private fun keys(panel: Panel = Panel.Closed, ctrl: Boolean = false) = ModifierState(Level.Off, Level.Off, panel, ctrl)
private val altOn = ModifierState(Level.Off, Level.Once, Panel.Closed, false)
private fun swiped(count: Int) = List(count) { Recognised(listOf(GuideScript.wordAt(it))) }
/** Android's key codes for J, A and Backspace, so that the checks need no Android. */
private const val KEY_J = 38
private const val KEY_A = 29
private const val KEY_DEL = 67

/** The real things a person does, in the order of the guide. Each slide teaches one instruction at a time. */
private val walk: List<Pair<String, List<KeyboardEvent>>> = listOf(
    "type" to listOf(Pushed(KEY_A), FieldChanged("Nothing", 7, 7), FieldChanged("Nothing ", 8, 8)),
    "swipe" to swiped(1),
    "strip" to listOf(Recognised(listOf("tp", "to")), Chosen("to")),
    "menu" to listOf(WordMenu("to"), MenuPicked("Save")),
    "flick" to listOf(Pushed(KEY_DEL), DeletedWord(held = false), DeletedWord(held = true)),
    "steer" to listOf(CursorStarted(false), FieldChanged("Nothing to unlearn", 18, 18), FieldChanged("Nothing to unlearn", 14, 14)),
    "select" to listOf(CursorStarted(true), FieldChanged("Nothing to unlearn", 11, 18)),
    "paste" to listOf(FieldChanged("Nothing to $PASTE_TEXT", 24, 24)),
    "clipboard" to listOf(keys(Panel.Symbols), keys(Panel.Clipboard), keys(Panel.Closed)),
    "alt" to listOf(altOn, Pushed(KEY_J)),
    "hold" to listOf(HeldSymbol("(")),
    "sym" to listOf(keys(Panel.Symbols), keys(Panel.Closed)),
    "symgroups" to listOf(GroupPicked(1), keys(Panel.Closed)),
    "emoji" to listOf(keys(Panel.Symbols), keys(Panel.Emoji), keys(Panel.Closed)),
    "emojigroups" to listOf(PanelSwiped(.4f, 0f, true), keys(Panel.Closed)),
    "clown" to listOf(FieldChanged("Nothing $CLOWN", 9, 9)),
    "quick" to listOf(keys(Panel.Symbols), keys(Panel.Settings), Resting("O"), keys(Panel.Closed)),
)

/** Slides that the guide no longer has: the boring guide explains them, or a new slide took over. */
private val gone = listOf("boring", "snippets", "exact", "names", "panel", "symbols", "copy", "share")

/** The word menu is picked with the keys: the top two rows, split into as many parts as the menu has buttons. */
private fun theMenuKeys() {
    same(MenuKeys.button("Q", 3), 0, "Q picks the first of three")
    same(MenuKeys.button("R", 3), 0, "Q to R pick the first")
    same(MenuKeys.button("T", 3), 1, "T picks the second")
    same(MenuKeys.button("P", 3), 2, "P picks the last")
    same(MenuKeys.button("A", 3), 0, "The second row senses it too")
    same(MenuKeys.button("L", 3), 2, "L picks the last")
    same(MenuKeys.button("Z", 3), -1, "The bottom row of letters picks nothing")
    same(MenuKeys.button(Layout.SPACE, 3), -1, "Space picks nothing")
    same(MenuKeys.button("T", 2), 0, "With two buttons, T is on the left half")
    same(MenuKeys.button("Y", 2), 1, "and Y on the right")
    same(MenuKeys.button("Q", 0), -1, "No menu, no button")
    for (count in 1..3) same((0 until 10).map { MenuKeys.button(Layout.rows[0][it], count) }.distinct().size, count, "Every one of $count buttons has keys")
}

fun main() {
    theMenuKeys()
    steadyKeyLight()
    playgroundSpeed()
    everyStepIsReachable()
    theStepsHaveOkayWords()
    oneStepForEachIdea()
    oneInstructionAtATime()
    theScriptedMiss()
    theShareSlide()
    theChapterBar()
    slideTexts()
    thePassage()
    theScriptMovesOnWithEachWord()
    theStripOffersTheScriptsWord()
    stepsStartInTheRightMenu()
    wrongEventsDoNotFinish()
    hints()
    theCursorHasToMove()
    settingsThatStepsNeed()
    goingBack()
    resuming()
    theTrailFollowsTouchSize()
    demonstrationsTypePunctuation()
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
        val texts = listOfNotNull(step.title, step.body, step.done, step.help, step.chapter.label) + step.stages.map { it.text }
        for (text in texts) {
            // The phone's name is the one number that may show.
            same(text.replace("Titan 2 Elite", "").any { it.isDigit() }, false, "\"$text\" must not have a number")
            same(text.isBlank(), false, "A step must have words")
        }
        same(step.title.length <= 32, true, "Title \"${step.title}\" must stay short")
        for (line in listOf(step.body) + step.stages.map { it.text }) same(line.length <= 72, true, "\"$line\" must stay short")
        if (step.chapter != GuideChapter.End) same(step.title.endsWith("."), true, "Title \"${step.title}\" reads as one sentence")
        if (step.stages.isNotEmpty()) same(step.body, step.stages.first().text, "${step.id} opens with its first instruction")
    }
    same(GUIDE_STEPS.first().id, "type", "The guide starts with the typing that everyone knows")
    same(GUIDE_STEPS.last().title, "TitanKeys", "The guide ends with the name, which flies together from Titan and Keys")
    same(GUIDE_STEPS.last().title.endsWith("Keys") && GUIDE_STEPS.last().title.startsWith("Titan"), true, "The name has its two halves")
    same(GUIDE_STEPS.last().body, "The keys your Titan deserves.", "The slogan has both words of the name")
    same(GUIDE_STEPS.last().offersShare, true, "The end offers to share swipes, the one boring thing")
    same(GuideChapter.End.label, "Built for Titan 2 Elite", "The ending says which phone it is built for")
    same(GUIDE_STEPS.none { listOf(it.title, it.body).any { text -> text.contains("thumb", ignoreCase = true) } }, true,
        "The slides do not say how to hold the phone, as some type with other fingers")
    same(GUIDE_STEPS.map { it.chapter }.distinct(), GuideChapter.entries, "Each chapter in order, its slides together, then the end")
    same(GUIDE_STEPS.count { it.pause }, 0, "The story has no stops")
    same(GUIDE_STEPS.count { it.info }, 0, "Every slide has something to try")
    for (id in gone) same(GUIDE_STEPS.none { it.id == id }, true, "$id is gone from the slides")
    same(GUIDE_STEPS.filter { it.chapter != GuideChapter.End }.all { it.stages.isNotEmpty() }, true, "Each slide teaches step by step")
    same(GUIDE_STEPS.filter { it.chapter != GuideChapter.End }.groupBy { it.chapter }.values.all { it.size >= 1 }, true, "Every chapter has slides")
    same(GuideScript.TEXT.any { it.isDigit() }, false, "The script must be easy to swipe, with no numbers")
    for (word in GuideScript.words) same(GuideScript.letters(word).isNotEmpty(), true, "Each word of the script has letters to swipe: $word")
    same(GuideScript.TEXT.contains("two hands or one") && GuideScript.TEXT.contains("ultimate"), true,
        "The script says that it types with two hands and one, and that it is the ultimate keyboard")
    same(GuideScript.TEXT.contains("TitanKeys") && GuideScript.TEXT.endsWith("the keys your Titan deserves."), true,
        "The script names TitanKeys, and ends on the slogan")
    same(GUIDE_CLIPS.size >= 3, true, "The clipboard lesson has several clips to show")
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

/** The chapters each go from what you know, to how TitanKeys does it, to the advanced stuff, one slide at a time. */
private fun oneStepForEachIdea() {
    val ids = GUIDE_STEPS.map { it.id }
    fun chapter(name: GuideChapter) = GUIDE_STEPS.filter { it.chapter == name }.map { it.id }
    same(chapter(GuideChapter.Typing), listOf("type", "swipe", "strip", "menu"), "Typing: the keys, swiping, fixing a swipe, the word menu")
    same(chapter(GuideChapter.Editing), listOf("flick", "steer", "select"), "Delete and move: deleting, steering, selecting")
    same(chapter(GuideChapter.Clipboard), listOf("paste", "clipboard"), "Copy and paste: pasting, then the clipboard")
    same(chapter(GuideChapter.Symbols), listOf("alt", "hold", "sym", "symgroups"), "Symbols: Alt, holding, the panel, its groups")
    same(chapter(GuideChapter.Emoji), listOf("emoji", "emojigroups", "clown"), "Emoji: the same shape, and the clown")
    same(chapter(GuideChapter.Settings), listOf("quick"), "Quick settings")
    check(ids.indexOf("paste") > ids.indexOf("select")) { "Pasting comes after selecting, so text is selected" }
    same(GUIDE_STEPS.count { it.needs == GuideNeed.SwipeTyping && it.cue == GuideCue.NextWord && !it.nearMiss }, 1, "Only one step swipes words")
    same(GUIDE_STEPS.count { it.cue is GuideCue.Flick }, 1, "One step deletes, the flick and the hold together")
    val byId = GUIDE_STEPS.associateBy { it.id }
    same(byId.getValue("steer").cue, GuideCue.Taps(2, drag = true), "Steering is two taps and a slide")
    same(byId.getValue("select").cue, GuideCue.Taps(3, drag = true), "Selecting is three taps and a slide")
    same(byId.getValue("flick").stages.first().text.contains("Backspace"), true, "Deleting starts with Backspace, as everyone knows it")
    same(GUIDE_STEPS.filter { it.nearMiss }.map { it.id }, listOf("strip"), "Only the strip step asks for a near miss")
    same(GuideHooks.takeNearMiss(GuideHooks.FIELD), false, "Nothing is asked for until the step asks")
    GuideHooks.nearMiss = true
    same(GuideHooks.takeNearMiss("some.other.app"), false, "No other field ever gets a near miss")
    same(GuideHooks.takeNearMiss(GuideHooks.FIELD), true, "The guide's field gets one near miss")
    same(GuideHooks.takeNearMiss(GuideHooks.FIELD), false, "One swipe uses it up")
}

/** A slide shows only the instruction that is needed now, and follows the keyboard forwards and back. */
private fun oneInstructionAtATime() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("emoji")
    same(flow.instruction, "Press Sym.", "The emoji slide starts with Sym")
    same(flow.event(keys(Panel.Symbols)), GuideVerdict.Stage(1), "Sym opens the symbols, so the next instruction shows")
    same(flow.event(keys(Panel.Closed)), GuideVerdict.Stage(0), "Closing the panel goes back a step")
    same(flow.event(keys(Panel.Emoji)), GuideVerdict.Stage(2), "Emoji at once skips ahead")
    same(flow.cue, GuideCue.Free, "Nothing glows while you pick an emoji")
    flow.start("emoji")
    flow.seed("Hi ")
    flow.event(keys(Panel.Symbols))
    flow.event(keys(Panel.Emoji))
    same(flow.event(FieldChanged("Hi ", 3, 3)), GuideVerdict.Wait, "Nothing typed yet")
    same(flow.event(FieldChanged("Hi 😀", 5, 5)), GuideVerdict.Done, "An emoji in the field finishes it")

    val alt = GuideFlow({ GuideSettings() })
    alt.start("alt")
    same(alt.cue, GuideCue.Spot(listOf(Layout.ALT)), "Alt glows first")
    same(alt.event(altOn), GuideVerdict.Stage(1), "Alt on, so the letter is next")
    same(alt.cue, GuideCue.Spot(listOf("J")), "Then J glows")
    same(alt.event(Pushed(KEY_J)), GuideVerdict.Done, "The letter types its symbol")

    val groups = GuideFlow({ GuideSettings() })
    groups.start("symgroups")
    same(groups.step.setup, Panel.Symbols, "The groups slide opens on the symbols")
    same(groups.event(PanelSwiped(0f, .4f, true)) is GuideVerdict.Hint, true, "A page down is no other group, and the slide says so")
    same(groups.event(PanelSwiped(.4f, 0f, true)), GuideVerdict.Stage(1), "A swipe sideways shows another group")

    val clown = GuideFlow({ GuideSettings() })
    clown.start("clown")
    same(clown.step.setup, Panel.Emoji, "The clown slide opens on the emoji")
    same(clown.event(FieldChanged("Hi 😀", 5, 5)), GuideVerdict.Wait, "Any emoji isn't the clown")
    same(clown.event(FieldChanged("Hi 😀$CLOWN", 7, 7)), GuideVerdict.Done, "The clown finishes it")
    same(clown.step.done to clown.step.rain, "Hey, that's you." to CLOWN, "and it's you, raining down")

    val quick = GuideFlow({ GuideSettings() })
    quick.start("quick")
    same(quick.event(keys(Panel.Symbols)), GuideVerdict.Stage(1), "Sym first")
    same(quick.event(keys(Panel.Emoji)) is GuideVerdict.Hint, true, "Emoji by mistake says how to get back")
    same(quick.event(keys(Panel.Settings)), GuideVerdict.Stage(2), "The quick settings, then a look around")
    same(quick.event(Resting("")), GuideVerdict.Wait, "A finger that left is no look")
    same(quick.event(Resting("O")), GuideVerdict.Stage(3), "A finger on a key shows its setting")
    same(quick.instruction.contains("Sym"), true, "Then Sym closes it")
    same(quick.event(keys(Panel.Closed)), GuideVerdict.Done, "Sym closes it, and that's the end of it")

    val paste = GuideFlow({ GuideSettings() })
    paste.start("paste")
    same(paste.step.clip, PASTE_TEXT, "The paste lesson puts something stupid on the clipboard")
    same(paste.step.preselect, true, "and starts with a word selected, so the paste goes in its place")
    same(paste.step.rain != CLOWN, true, "and rains something, but not the clown, which waits for its own slide")
    same(paste.step.done, "Yes, we know.", "with a line that says so")
    same(paste.event(FieldChanged("Nothing to", 10, 10)), GuideVerdict.Wait, "Nothing pasted yet")
    same(paste.event(FieldChanged("Nothing $PASTE_TEXT", 21, 21)), GuideVerdict.Done, "The paste finishes it")

    val clips = GuideFlow({ GuideSettings() })
    clips.start("clipboard")
    same(clips.step.pinnedClips, GUIDE_CLIPS, "The clipboard lesson pins its clips")
    same(clips.event(keys(Panel.Symbols)), GuideVerdict.Stage(1), "Then A")
    same(clips.event(keys(Panel.Closed)), GuideVerdict.Stage(0), "Closed again, so Sym again")
    same(clips.event(keys(Panel.Clipboard)), GuideVerdict.Stage(2), "The clipboard, straight away, is fine too")
    same(clips.event(keys(Panel.Closed)), GuideVerdict.Done, "A clip pasted, or the panel closed, finishes it")

    val menu = GuideFlow({ GuideSettings() })
    menu.start("menu")
    same(menu.event(MenuPicked("Save")), GuideVerdict.Wait, "A pick before the menu opens means nothing")
    same(menu.event(WordMenu("to")), GuideVerdict.Stage(1), "The menu opens, so picking is next")
    same(menu.event(MenuPicked("Close")), GuideVerdict.Done, "Any pick, by key or touch, finishes it")

    val flick = GuideFlow({ GuideSettings() })
    flick.start("flick")
    same(flick.event(DeletedWord(held = false)), GuideVerdict.Stage(1), "A flick straight away skips Backspace")
    same(flick.event(DeleteHoldStarted), GuideVerdict.Hint(AHEAD_OF_YOURSELF), "Holding before one quick flick is ahead of itself")
    same(flick.event(DeletedWord(held = false)), GuideVerdict.Wait, "and the hold's first word doesn't count as the flick")
    same(flick.event(DeletedWord(held = true)), GuideVerdict.Wait, "nor do its other words")
    same(flick.event(DeletedWord(held = false)), GuideVerdict.Stage(2), "One quick flick, then the hold")
    same(flick.event(DeletedWord(held = true)), GuideVerdict.Done, "The hold finishes it")
}

/** On the slide about the bar the swipe always comes out wrong, so there is always a word to fix with one tap. */
private fun theScriptedMiss() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("strip")
    same(flow.event(Recognised(listOf("tp"))), GuideVerdict.Stage(1), "Whatever was swiped, the slide says to tap the right word")
    same(flow.instruction.contains("dancing"), true, "The instruction points at the dancing word")
    same(flow.cue, GuideCue.Free, "The ghost stops swiping once there is a word to fix")
    same(flow.event(Chosen("to")), GuideVerdict.Done, "A touch on the bar finishes it")
}

/** Each slide has its own text to type, a new one once it's done, and the playground its own, fast one. */
private fun slideTexts() {
    for (step in GUIDE_STEPS.filter { it.chapter != GuideChapter.End }) {
        same(step.text.isNotBlank() && step.after.isNotBlank(), true, "${step.id} has a text to type, before and after")
        same(step.text != step.after, true, "${step.id}'s text changes once it's done")
        same(step.text.any { it.isDigit() } || step.after.any { it.isDigit() }, false, "${step.id}'s texts are easy to type")
    }
    same(GUIDE_STEPS.map { it.text }.distinct().size, GUIDE_STEPS.size, "Every slide has a text of its own")
    val race = Passage("Nothing to unlearn: type with two hands or one")
    same(race.wordAt(race.track("Nothing to unlearn type with t two ").next), "hands", "A wrong word is skipped, matching by what's typed")
    same(race.track("Nothing to unlearn type with t two ").done.contains(5), true, "two counts as done")
    same(race.track("nothing TO").typed, "to", "Capitals don't matter, and the word being typed is followed")
    same(race.wordAt(race.track("Nothing type ").next), "with", "A word from a little further on skips ahead to it")
    same(race.track("Nothing zebra ").next, 1, "Any other word is ignored")
    same(race.wordAt(race.words.size), "Nothing", "The text starts again after its end")
    val fast = PlaygroundText.make(kotlin.random.Random(7))
    same(fast.any { it.isUpperCase() || it.isDigit() || it in ".,:;!?" }, false, "The playground's text is all plain, fast words")
    val words = fast.split(' ')
    val slip = words.windowed(3).indexOfFirst { it.joinToString(" ") == PlaygroundText.SLIP }
    same(slip >= PlaygroundText.SLIP_FIRST, true, "The slip only comes once you're going")
}

/** The bar at the top shows chapters, and how far into this one you are. */
private fun theChapterBar() {
    val flow = GuideFlow({ GuideSettings() })
    same(flow.barChapters, GuideChapter.entries.dropLast(1), "Every chapter but the end")
    same(flow.stepInChapter to flow.stepsInChapter, 0 to 4, "The first slide of the four in Typing")
    flow.start("strip")
    same(flow.stepInChapter, 2, "The third slide of Typing")
    flow.start("steer")
    same(flow.step.chapter to flow.stepInChapter, GuideChapter.Editing to 1, "The second of Delete and move")
}

/** The passage above the field, as a typing race shows it. */
private fun thePassage() {
    same(GuideScript.ranges.size, GuideScript.steps.size, "Each step has its place in the script")
    for ((index, range) in GuideScript.ranges.withIndex())
        same(GuideScript.TEXT.substring(range.first, range.last + 1), GuideScript.steps[index], "Step $index sits where the script has it")
    val fresh = GuideScript.track("")
    same(fresh.next to fresh.typed, 0 to "", "At the start, the first word is next")
    val typing = GuideScript.track("Nothing t")
    same(typing.next to typing.typed, 1 to "t", "A word being typed is the word you're on")
    val typo = GuideScript.track("Nothing tp")
    same(typo.next to typo.typed, 1 to "tp", "A typo stays on its word, to be boxed until it's fixed")
    val past = GuideScript.track("Nothing tp unlearn ")
    same(past.next, 3, "A finished word moves on, right or wrong")
    same(past.wrong, setOf(1), "and a wrong one stays marked")
    same(GuideScript.track("Nothing to unlearn type ").wrong, setOf(3), "Leaving the colon out marks it")
    same(GuideScript.track("Nothing to unlearn: ").wrong, emptySet<Int>(), "Typed right, nothing is marked")
}

private fun theShareSlide() {
    same(GUIDE_STEPS.none { it.chapter != GuideChapter.End && it.offersShare }, true, "Only the end offers to share swipes")
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
    for (id in listOf("alt", "sym", "emoji", "quick", "clipboard"))
        same(byId.getValue(id).setup, Panel.Closed, "$id starts closed, so that its first instruction opens the panel")
    for (step in GUIDE_STEPS.filter { !it.usesPanel }) same(step.setup, Panel.Closed, "${step.id} types, so the panel is closed for it")
    for (step in GUIDE_STEPS.filter { it.chapter != GuideChapter.End })
        same(step.demo != null || step.bar != null, true, "${step.id} must show its gesture by itself when nobody acts")
    same(byId.getValue("clipboard").demo, GuideDemo.Insert(listOf(Layout.SYM, Layout.CLIPBOARD_KEY), ""), "The clipboard slide lights Sym, then A")
    same(byId.getValue("hold").demo, GuideDemo.HoldLetter("J"), "The hold slide shows J held down, the key that it names")
    same(byId.getValue("alt").demo, GuideDemo.AltLetter("J"), "The Alt slide shows Alt, then J")
    same(byId.getValue("type").bar, GuideBar.Fix, "Autocorrect shows the fix swapping in on the bar")
    same(byId.getValue("menu").bar, GuideBar.Menu, "The word menu shows a ghost hold on the bar, and the menu opening")
}

private fun wrongEventsDoNotFinish() {
    val flow = GuideFlow({ GuideSettings() })
    same(flow.step.id, "type", "The guide starts with typing")
    same(flow.event(FieldChanged("key ", 4, 4)), GuideVerdict.Wait, "A word that no key typed does not finish typing")
    same(flow.event(Pushed(KEY_A)), GuideVerdict.Stage(1), "A letter, so Space is next")
    flow.advance()
    same(flow.step.id, "swipe", "Next is the swipe")
    same(flow.event(Chosen("faster")) is GuideVerdict.Done, false, "Touching a word must not finish the swipe")
    same(flow.event(FieldChanged("faster", 6, 6)) is GuideVerdict.Done, false, "Typing must not finish the swipe")
    same(flow.event(Pushed(62)), GuideVerdict.Wait, "Space is not a letter, so it says nothing")
    val menu = GuideFlow({ GuideSettings() })
    menu.start("menu")
    same(menu.event(Chosen("to")) is GuideVerdict.Hint, true, "A touch on a word is not a hold, and the slide says so")
}

private fun hints() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("swipe")
    same(flow.event(Tapped("A")) is GuideVerdict.Hint, true, "A tap on the swipe step gives a hint")
    same(flow.event(Pushed(30)) is GuideVerdict.Hint, true, "A push on the swipe step gives a hint")
    same(flow.event(Recognised(emptyList())), GuideVerdict.Wait, "A swipe that typed nothing does not count")
    // Mistakes that are easy to make say what happened, and do not count against you.
    val sym = flow.event(keys(Panel.Symbols))
    same(sym is GuideVerdict.Hint && sym.text.contains("Sym"), true, "Opening the panel by chance says so")
    val alt = flow.event(altOn)
    same(alt is GuideVerdict.Hint && alt.text.contains("Alt"), true, "Alt by chance says that the touchpad does not type")
    same(flow.event(CursorStarted(false)) is GuideVerdict.Hint, true, "Starting the cursor by chance says so")
    same(flow.event(HeldSymbol("(")) is GuideVerdict.Hint, true, "A held symbol by chance says so")
    same(flow.event(Recognised(listOf("whatever"))), GuideVerdict.Done, "One swiped word introduces swiping, whatever the word was")
    val panel = GuideFlow({ GuideSettings() })
    panel.start("sym")
    same(panel.event(keys(Panel.Symbols)), GuideVerdict.Stage(1), "A step that wants the panel is not told off for it")
    same(panel.event(keys(Panel.Closed)), GuideVerdict.Done, "Closing the panel, by a symbol or by Sym, finishes it")
    val alts = GuideFlow({ GuideSettings() })
    alts.start("alt")
    same(alts.event(altOn), GuideVerdict.Stage(1), "Alt is no mistake on the Alt slide")
    val hold = GuideFlow({ GuideSettings() })
    hold.start("hold")
    same(hold.event(HeldSymbol("(")), GuideVerdict.Done, "Holding a letter for its symbol finishes its slide")
    val steer = GuideFlow({ GuideSettings() })
    steer.start("steer")
    same(steer.event(Recognised(listOf("word"))) is GuideVerdict.Hint, true, "A swiped word on the steer step says to start on Space")
    same(steer.event(CursorStarted(true)), GuideVerdict.Stage(1), "Three touches and a swipe up steer too")
}

private fun theCursorHasToMove() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("steer")
    same(flow.event(FieldChanged("Move the cursor", 15, 15)), GuideVerdict.Wait, "A cursor change before the drag starts means nothing")
    same(flow.event(CursorStarted(false)), GuideVerdict.Stage(1), "The taps start the cursor, so sliding is next")
    same(flow.event(FieldChanged("Move the cursor", 14, 14)), GuideVerdict.Wait, "The first change sets the mark")
    same(flow.event(FieldChanged("Move the cursor", 13, 13)), GuideVerdict.Wait, "One letter is not enough")
    same(flow.event(FieldChanged("Move the cursor", 10, 10)), GuideVerdict.Done, "A few letters finishes it")

    val select = GuideFlow({ GuideSettings() })
    select.start("select")
    same(select.event(FieldChanged("Select many words", 7, 7)), GuideVerdict.Wait, "A cursor alone is no selection")
    same(select.event(CursorStarted(false)) is GuideVerdict.Hint, true, "Two taps are steering, not selecting, and the slide says so")
    same(select.event(CursorStarted(true)), GuideVerdict.Stage(1), "Three taps start selecting")
    same(select.event(FieldChanged("Select many words", 7, 11)), GuideVerdict.Done, "A selected word finishes it")
}

private fun settingsThatStepsNeed() {
    var settings = GuideSettings(swipeTyping = false, cursorControl = false)
    val flow = GuideFlow({ settings })
    flow.start("swipe")
    same(flow.missing, GuideNeed.SwipeTyping, "A step can ask for its setting")
    flow.start("steer")
    same(flow.missing, GuideNeed.CursorControl, "The steer step asks for cursor control")
    flow.start("select")
    same(flow.missing, GuideNeed.CursorControl, "And so does the select step")
    settings = GuideSettings(cursorControl = true)
    same(flow.missing, null, "Nothing is missing when the setting is on")
    flow.start("hold")
    same(flow.missing, GuideNeed.HoldSymbols, "Holding a letter is off at first, so the hold step offers it")
    settings = GuideSettings(cursorControl = true, holdSymbols = true)
    same(flow.missing, null, "With holding on, the hold step needs nothing more")
    flow.start("paste")
    same(flow.missing, GuideNeed.FnAsCtrl, "The paste step needs fn as Ctrl")
    same(GuideNeed.FnAsCtrl.optional, true, "But only offers it with its help, as some phones already have Ctrl")
    same(GuideSettings().has(GuideNeed.FnAsCtrl), false, "fn is not Ctrl by default")
}

private fun goingBack() {
    val flow = GuideFlow({ GuideSettings() })
    same(flow.canGoBack, false, "The first step has nothing before it")
    same(flow.back(), false, "Back from the first step does nothing")
    flow.start("swipe")
    same(flow.back(), true, "Back goes to the step before")
    same(flow.step.id, "type", "The step before swiping is typing")
    flow.start("end")
    same(flow.back(), true, "Back from the end goes to the last chapter")
    same(flow.step.id, "quick", "The quick settings")
}

private fun resuming() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("emojigroups")
    same(flow.resumeId, "emojigroups", "A step that sets up its own menu comes back to itself")
    flow.start("flick")
    same(flow.resumeId, "flick", "Other steps come back to themselves")
    flow.start("missing step")
    same(flow.step.id, "type", "An unknown step starts from the beginning")
    same(flow.chapterIndex, 0, "The first part is number zero")
    same(flow.chapterCount, GuideChapter.entries.size, "Each part counts")
    same(flow.stepCount, GUIDE_STEPS.size - 1, "The bar counts every step but the ending")
    same(flow.finished, false, "The start is not the end")
    flow.start("boring")
    same(flow.step.id, "type", "A saved place from before this guide starts from the beginning")
}

/** #52: a light fingertip draws a thin trail, a flat thumb a thick one, and a touch that wavers by a step stays calm. */
private fun theTrailFollowsTouchSize() {
    fun steady(step: Int, x: Float = .45f, y: Float = .4f): Float {
        val trail = TrailThickness()
        var thickness = 0f
        repeat(40) { thickness = trail.next(x, y, step) }
        return thickness
    }
    same(TrailThickness().next(.5f, .5f, 0), 1f, "A touch of unknown size should draw the usual trail")
    // In the middle of the pad the usual size is 2.53, so step 3 is half a step firmer and step 2 half a step lighter.
    same(steady(1), TrailThickness.THINNEST, "A light fingertip should draw the thinnest trail")
    check(steady(2) < .8f) { "A touch half a step lighter than usual should look clearly thinner, got ${steady(2)}" }
    check(steady(3) > 1.25f) { "A touch half a step firmer than usual should look clearly thicker, got ${steady(3)}" }
    same(steady(4), TrailThickness.THICKEST, "A firm press should reach the thickest trail, not only a resting thumb")
    same(steady(40), TrailThickness.THICKEST, "The thickest trail should be capped")
    // Step 3 is a little firmer than usual in the middle of the pad and much firmer on the top row.
    check(steady(3, y = .1f) > steady(3, y = .4f) + .2f) { "The same step should count as larger where touches are usually smaller" }
    // A usual touch wavers between the steps either side of the usual size. It must look usual, and calm.
    val trail = TrailThickness()
    repeat(60) { trail.next(.45f, .4f, if (it % 2 == 0) 2 else 3) }
    val wobble = (0 until 40).map { trail.next(.45f, .4f, if (it % 2 == 0) 2 else 3) }
    check(wobble.all { it in .95f..1.05f }) { "A touch wavering between the steps around usual should look usual: $wobble" }
    check(wobble.zipWithNext().all { (a, b) -> kotlin.math.abs(a - b) < .06f }) { "A touch wavering by a step should not flicker: $wobble" }
    check(trail.next(.45f, .4f, 4) < 1.1f) { "One larger point should only start to thicken the trail" }
    same(trail.next(.45f, .4f, 0), trail.next(.45f, .4f, 0), "A point of unknown size should keep the thickness")
}

/** The guide's key light under a finger: steady at key edges, and through the touchpad's short dropouts of contact. */
private fun steadyKeyLight() {
    val hover = KeyHover()
    same(hover.current(0), null, "No finger, no key")
    hover.point(.15f, .125f)
    same(hover.current(0), "W", "A finger lights the key under it at once")
    // W is from .1 to .2 across. Just over the border into E stays on W.
    hover.point(.205f, .125f)
    same(hover.current(10), "W", "Just over a border, the light stays where it was")
    hover.point(.195f, .125f)
    hover.point(.21f, .125f)
    same(hover.current(20), "W", "Wobbling on a border does not flip the light")
    hover.point(.225f, .125f)
    same(hover.current(30), "E", "Clearly inside the next key, the light moves")
    hover.point(.25f, .27f)
    same(hover.current(40), "E", "Just below a row's border, it stays in the row it was in")
    hover.point(.25f, .36f)
    same(hover.current(50), "D", "Well inside the next row, it moves")
    hover.lift(100)
    same(hover.current(150), "D", "A short dropout keeps the light")
    hover.point(.25f, .37f)
    same(hover.current(200), "D", "The finger back after a dropout carries on")
    hover.lift(300)
    same(hover.current(300 + KeyHover.DROPOUT_MILLIS), "D", "Up to the end of the grace the light stays")
    same(hover.current(301 + KeyHover.DROPOUT_MILLIS), null, "After a real lift the light goes")
    hover.point(.55f, .62f)
    same(hover.current(500), "B", "A new touch after a lift lights its key at once")
    hover.point(.05f, .375f)
    same(hover.current(510), "A", "An edge key can be reached, as far as the touchpad goes")
}

/** The playground's speed: one burst from the first input, five characters a word, and gone after a quiet while. */
private fun playgroundSpeed() {
    val meter = WpmMeter()
    same(meter.wpm(0), null, "Before any typing there is no speed")
    meter.input(1000, 0)
    meter.text(5)
    same(meter.wpm(2000), 15, "One word in the first seconds counts over the shortest span, so it is not a wild number")
    for (i in 1..60) meter.input(1000L + i * 1000, 5 + i * 5)
    same(meter.wpm(61_000), 61, "Sixty more words in a minute is about sixty words a minute")
    same(meter.active(61_000 + WpmMeter.IDLE_MILLIS), true, "The burst lasts through a short pause")
    same(meter.wpm(61_001 + WpmMeter.IDLE_MILLIS), null, "After the quiet while the speed goes, and the name comes back")
    meter.input(100_000, 400)
    meter.text(410)
    same(meter.wpm(105_000), 24, "A new burst counts from its own start, not from the text before it")
    meter.input(105_500, 300)
    same(meter.wpm(106_000), 0, "Deleting gives no negative speed")
    same(WpmMeter.IDLE_MILLIS, 5000L, "Five seconds with nothing ends the burst")
    // The comments: one for each step, from its step until the next one. Most people type around thirty, so it starts low.
    same(WpmComments.STEPS.first(), 15, "The comments start at fifteen")
    same(WpmComments.STEPS.last(), 200, "and end at two hundred")
    same(WpmComments.STEPS.size, 28, "Twenty-eight steps")
    same(WpmComments.STEPS.zipWithNext { a, b -> b - a }.distinct(), listOf(5, 10), "Steps of five to a hundred, then ten")
    same(WpmComments.step(14), -1, "Below fifteen there is no comment")
    same(WpmComments.step(15), 0, "Fifteen shows the first")
    same(WpmComments.step(19), 0, "and keeps it until the next step")
    same(WpmComments.step(30), WpmComments.STEPS.indexOf(30), "Thirty has its own line")
    same(WpmComments.step(104), WpmComments.STEPS.indexOf(100), "A hundred and four still shows the hundred line")
    same(WpmComments.step(450), WpmComments.STEPS.lastIndex, "Above two hundred the last line stays")
    val strings = java.io.File("android/src/main/res/values/strings.xml").readText()
    val array = strings.substringAfter("<string-array name=\"playground_comments\">").substringBefore("</string-array>")
    val lines = Regex("<item>\"(.*)\"</item>").findAll(array).map { it.groupValues[1] }.toList()
    same(lines.size, WpmComments.STEPS.size, "One comment in the strings for each step")
    same(lines[WpmComments.step(15)], "Hey, you're typing.", "Fifteen's line")
    same(lines[WpmComments.step(30)], "Oh wow, actual typing.", "Thirty's line")
    same(lines[WpmComments.step(95)], "Last chance to slow down.", "Ninety-five's line")
    same(lines[WpmComments.step(100)], "You sure you're not just mashing shit?", "A hundred's line")
    same(lines[WpmComments.step(200)], "Fuck it, you win. Go touch grass.", "Two hundred's line")
    // The show builds step by step with the lines, and stops for one plant at the top.
    fun at(wpm: Int) = WpmEffects.at(wpm)
    same(at(14), WpmEffects.NONE, "Nothing below fifteen")
    check(at(15).glow > 0f && at(25).glow > at(20).glow && at(20).glow > at(15).glow) { "The glow starts at fifteen and gets brighter" }
    same(at(25).sparks, 0f, "No sparks before thirty")
    check(at(30).sparks > 0f && at(35).sparks > at(30).sparks) { "Sparks at thirty, more at thirty-five" }
    check(at(40).pop > 0 && at(45).pop > at(40).pop && at(50).pop == 0) { "A confetti pop at forty, a bigger one at forty-five" }
    same(at(45).flames, 0f, "No flames before fifty")
    check(listOf(50, 55, 60, 65, 70).map { at(it).flames }.zipWithNext().all { (a, b) -> b > a }) { "The flames grow at each step from fifty" }
    check(at(65).edges == 0f && at(70).edges > 0f && at(75).edges > at(70).edges) { "The edges glow at seventy, hotter at seventy-five" }
    check(at(75).shower == 0 && at(80).shower > 0 && at(85).shower > at(80).shower && at(90).shower > at(85).shower) { "Confetti showers from eighty" }
    check(at(90).flames > at(85).flames && at(90).sparks > at(85).sparks) { "Ninety is full blast" }
    same(at(95).brown to at(90).brown, true to false, "At ninety-five the confetti flickers brown")
    check(at(100).shower == 0 && at(100).rain > 0f) { "At a hundred the confetti turns into rain" }
    check(at(110).rain > at(100).rain && at(110).wobble && !at(100).wobble) { "A hundred and ten rains harder, and wobbles" }
    check(at(120).rain > at(110).rain) { "A hundred and twenty is steady rain" }
    same(at(130).clowns to at(120).clowns, true to false, "Clowns join at a hundred and thirty")
    same(at(140).sweat to at(130).sweat, true to false, "The number sweats at a hundred and forty")
    same(at(150).shake to at(140).shake, true to false, "The screen shakes at a hundred and fifty")
    same(at(160).toilets to at(150).toilets, true to false, "Toilets at a hundred and sixty")
    check(at(170).rain >= 14f) { "A storm at a hundred and seventy" }
    same(at(180).lightning to at(170).lightning, true to false, "Lightning at a hundred and eighty")
    check(at(190).rain > at(180).rain) { "The storm at its worst at a hundred and ninety" }
    same(at(200), WpmEffects(sprout = true), "At two hundred everything stops for one plant")
    same(WpmMeter.heat(null), 0f, "No speed, no orange")
    same(WpmMeter.heat(90), 1f, "Full orange near ninety words a minute")
    same(WpmMeter.heat(200), 1f, "It saturates there")
    check(WpmMeter.heat(30) < WpmMeter.heat(60) && WpmMeter.heat(30) < .3f) { "Slow typing barely warms the screen" }
}

/** #59: a demonstration types a word's marks as the keyboard really does, and counting still ignores them. */
private fun demonstrationsTypePunctuation() {
    val alt = mapOf(',' to "M", ':' to "Z", '.' to "N")
    fun shown(word: String, swiped: Boolean, auto: Boolean = true, keyFor: (Char) -> String? = { alt[it] }) =
        GuideScript.pushes(word, swiped, auto, keyFor).map { it.keys.joinToString("+") + "=" + it.text }
    same(shown("Then", false), listOf("T=T", "H=Th", "E=The", "N=Then"), "A typed word is a push for each letter")
    same(shown("go.", false), listOf("G=g", "O=go", "space=go ", "space=go. "), "A full stop is Space twice")
    same(shown("go.", true), listOf("=go ", "space=go ", "space=go. "), "After a swipe, the first Space only arms the full stop")
    same(shown("one,", false), listOf("O=o", "N=on", "E=one", "alt=one", "M=one,"), "A comma is Alt and its key")
    same(shown("one,", true), listOf("=one ", "alt=one ", "M=one,"), "A mark after a swipe takes the place of its space")
    same(shown("unlearn:", true).last(), "Z=unlearn:", "A colon is Alt and its key")
    same(shown("go.", false, auto = false).takeLast(2), listOf("alt=go", "N=go."), "With auto punctuation off, a full stop is Alt and its key")
    same(shown("hi?", true, keyFor = { null }).last(), "=hi?", "A mark that no key has still shows, with nothing lit")
    for (word in GuideScript.words) {
        val text = GuideScript.pushes(word, false, true) { alt[it] }.last().text
        same(text.trimEnd(), word, "Typing $word ends in the script's own word")
    }
    // The marks after a word are a step of their own, so that you get to type them, and counting stays lenient.
    same(GuideScript.steps.take(5), listOf("Nothing", "to", "unlearn", ":", "type"), "A word's marks are a step after it")
    same(GuideScript.steps.count(GuideScript::isMarks), GuideScript.words.count { !it.last().isLetterOrDigit() }, "Each word with marks gives one step of marks")
    same(GuideScript.progress("Nothing to unlearn"), 3, "A word without its colon counts")
    same(GuideScript.stepAt(GuideScript.progress("Nothing to unlearn ")), ":", "After the word, the colon is next")
    same(GuideScript.progress("Nothing to unlearn: "), 4, "Typing the colon moves on")
    same(GuideScript.progress("Nothing to unlearn:"), 4, "The colon counts at once")
    same(GuideScript.progress("Nothing to unlearn type "), 5, "Typing the next word instead counts for the colon too, so nobody is stuck")
    same(GuideScript.progress("Nothing to unlearn ty"), 4, "The start of the next word still waits, past the colon")
    same(GuideScript.progress("Nothing, to "), 2, "A mark where a word is next is passed over")
    same(GuideScript.wordFrom(3), "type", "The word to expect after the colon is the next word")
    same(GuideScript.lineFrom(2, 4), "unlearn: type with", "The line puts marks right after their word")
    same(GuideScript.markKeys(".", true) { "N" }, listOf(listOf(Layout.SPACE), listOf(Layout.SPACE)), "A full stop is Space twice")
    same(GuideScript.markKeys(":", true) { "Z" }, listOf(listOf(Layout.ALT), listOf("Z")), "A colon is Alt and its key")
    same(GuideScript.markKeys("?", true) { null }, emptyList<List<String>>(), "A mark that no key has lights nothing")
    val flow = GuideFlow({ GuideSettings() })
    flow.start("swipe")
    same(flow.step.id, "swipe", "The swipe slide should open")
    val done = (0 until 10).firstOrNull { flow.event(Recognised(listOf(GuideScript.letters(GuideScript.wordAt(it))))) == GuideVerdict.Done }
    same(done != null, true, "Swiped words without their marks should finish the swipe slide")
}
