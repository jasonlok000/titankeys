package com.atlas.keyboard

import com.atlas.keyboard.KeyboardEvent.Chosen
import com.atlas.keyboard.KeyboardEvent.Fixed
import com.atlas.keyboard.KeyboardEvent.GroupPicked
import com.atlas.keyboard.KeyboardEvent.MenuPicked
import com.atlas.keyboard.KeyboardEvent.PanelSwiped
import com.atlas.keyboard.KeyboardEvent.CursorStarted
import com.atlas.keyboard.KeyboardEvent.DeleteHoldStarted
import com.atlas.keyboard.KeyboardEvent.DeletedWord
import com.atlas.keyboard.KeyboardEvent.DoubleTapped
import com.atlas.keyboard.KeyboardEvent.EmptyKey
import com.atlas.keyboard.KeyboardEvent.Released
import com.atlas.keyboard.KeyboardEvent.TwoTapsAbove
import com.atlas.keyboard.KeyboardEvent.FieldChanged
import com.atlas.keyboard.KeyboardEvent.HeldSymbol
import com.atlas.keyboard.KeyboardEvent.ModifierState
import com.atlas.keyboard.KeyboardEvent.Moved
import com.atlas.keyboard.KeyboardEvent.Pushed
import com.atlas.keyboard.KeyboardEvent.Recognised
import com.atlas.keyboard.KeyboardEvent.Tapped
import com.atlas.keyboard.KeyboardEvent.WordMenu
import com.atlas.keyboard.Modifiers.Level
import com.atlas.keyboard.Modifiers.Panel

private fun keys(panel: Panel = Panel.Closed, ctrl: Boolean = false) = ModifierState(Level.Off, Level.Off, panel, ctrl)
private val altOn = ModifierState(Level.Off, Level.Once, Panel.Closed, false)
/** Android's key codes for A, Q, U and Backspace, so that the checks need no Android. */
private const val KEY_A = 29
private const val KEY_Q = 45
private const val KEY_DEL = 67

/** The real things a person does, in the order of the guide. Each slide teaches one instruction at a time. */
private val walk: List<Pair<String, List<KeyboardEvent>>> = listOf(
    "type" to listOf(Pushed(KEY_A), Fixed("teh", "the")),
    "swipe" to listOf(Recognised(listOf("this")), Recognised(listOf("keyboard")), Recognised(listOf("fixes"))),
    "strip" to listOf(Recognised(listOf("tp", "to")), Chosen("to")),
    "menu" to listOf(WordMenu("to"), MenuPicked("Close")),
    "backspace" to listOf(Pushed(KEY_DEL)),
    "flick" to listOf(DeletedWord(held = false)),
    "hold" to listOf(DeleteHoldStarted, DeletedWord(held = true)),
    "steer" to listOf(CursorStarted(false), FieldChanged("on and on ", 9, 9), FieldChanged("on and on ", 4, 4)),
    "selectword" to listOf(FieldChanged("on and on ", 3, 6)),
    "selectmore" to listOf(CursorStarted(true), FieldChanged("on and on ", 0, 9)),
    "alt" to listOf(altOn, FieldChanged("$", 1, 1)),
    "holdsym" to listOf(HeldSymbol("@")),
    "symmain" to listOf(keys(Panel.Symbols), FieldChanged("x. &", 4, 4)),
    "symgroups" to listOf(PanelSwiped(0f, .4f, true), GroupPicked(2), FieldChanged("x. §", 4, 4)),
    "clown" to listOf(keys(Panel.Emoji), FieldChanged("x. $CLOWN", 5, 5)),
    "paste" to listOf(FieldChanged("Paste over $PASTE_TEXT", 24, 24)),
    "clipboard" to listOf(keys(Panel.Symbols), keys(Panel.Clipboard), FieldChanged("Paste. ${GUIDE_CLIPS.first()}", 40, 40)),
)

/** Slides that the guide no longer has: the boring guide explains them, or a new slide took over. */
private val gone = listOf("boring", "snippets", "exact", "names", "panel", "symbols", "copy", "share", "emoji", "emojigroups", "quick",
    "select", "sym")

/** The word menu is picked with the keys: the top two rows, split into as many parts as the menu has buttons. */
private fun theMenuKeys() {
    same(MenuKeys.button("I"), MenuKeys.SAVE, "I saves")
    same(MenuKeys.button("O"), MenuKeys.FORGET, "O forgets")
    same(MenuKeys.button("P"), MenuKeys.BACK, "P goes back")
    same(listOf("K", "L").map(MenuKeys::button), listOf(MenuKeys.SAVE, MenuKeys.FORGET), "The second row senses them too")
    for (key in listOf("Q", "W", "E", "R", "T", "Y", "U", "A", "H", "J", "Z", "M", Layout.SPACE))
        same(MenuKeys.button(key), -1, "$key picks nothing: the word sits over the left")
    same(MenuKeys.WORD_COLUMNS + 3 + MenuKeys.AFTER_COLUMNS, 10, "The word, three buttons and P's column fill the ten columns")
}

fun main() {
    modifiersAreNoTyping()
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
    chapterOne()
    scenarios()
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

/** A verdict that finishes the slide: done, or a scenario that counts. */
private fun finishes(verdict: GuideVerdict) = verdict == GuideVerdict.Done || verdict is GuideVerdict.Hint && verdict.passes

private fun run(flow: GuideFlow, events: List<KeyboardEvent>, step: String) {
    same(flow.step.id, step, "The guide should be at this step")
    flow.seed("")
    for ((index, event) in events.withIndex()) {
        val verdict = flow.event(event)
        if (index < events.lastIndex) same(finishes(verdict), false, "$step should wait after $event")
        else same(finishes(verdict), true, "$step should finish on $event, not $verdict")
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
        val texts = listOfNotNull(step.title, step.body, step.help, step.helpTitle, step.chapter.label, step.praise) + step.stages.map { it.text }
        for (text in texts) {
            // The phone's name is the one number that may show.
            same(text.replace("Titan 2 Elite", "").any { it.isDigit() }, false, "\"$text\" must not have a number")
        }
        same(step.title.isNotBlank() && step.done.isNotBlank(), true, "${step.id} has a title and a line that lands")
        if (step.chapter != GuideChapter.End) same(step.title.endsWith("."), true, "Title \"${step.title}\" reads as one sentence")
        if (step.stages.isNotEmpty()) same(step.body, step.stages.first().text, "${step.id} opens with its first instruction")
    }
    same(GUIDE_STEPS.first().id, "type", "The guide starts with the typing that everyone knows")
    same(GUIDE_STEPS.last().title, "TitanKeys", "The guide ends with the name, which flies together from Titan and Keys")
    same(GUIDE_STEPS.last().offersShare, true, "The end offers to share swipes")
    same(GuideChapter.End.label, "Built for Titan 2 Elite", "The ending says which phone it is built for")
    same(GUIDE_STEPS.none { listOf(it.title, it.body).any { text -> text.contains("thumb", ignoreCase = true) } }, true,
        "The slides do not say how to hold the phone, as some type with other fingers")
    same(GUIDE_STEPS.map { it.chapter }.distinct(), GuideChapter.entries, "Each chapter in order, its slides together, then the end")
    same(GUIDE_STEPS.count { it.pause } + GUIDE_STEPS.count { it.info }, 0, "Every slide has something to try")
    for (id in gone) same(GUIDE_STEPS.none { it.id == id }, true, "$id is gone from the slides")
    same(GUIDE_STEPS.filter { it.chapter != GuideChapter.End }.all { it.stages.isNotEmpty() }, true, "Each slide teaches step by step")
    for (chapter in GuideChapter.entries) same(chapter.roast != null, chapter != GuideChapter.End, "${chapter.label} ends with a roast")
    // Setup is slide zero, so its words follow the same rules.
    for (part in SetupPart.entries) {
        for (text in listOfNotNull(part.title, part.body, part.action, part.help))
            same(text.any { it.isDigit() }, false, "\"$text\" must not have a number")
        same(part.title.length <= 32 && part.title.endsWith("."), true, "Setup title \"${part.title}\" is one short sentence")
        same(part.body.length <= 72, true, "Setup body \"${part.body}\" must stay short")
    }
}

/** The chapters, from the owner's text (v3.1). */
private fun oneStepForEachIdea() {
    fun chapter(name: GuideChapter) = GUIDE_STEPS.filter { it.chapter == name }.map { it.id }
    same(chapter(GuideChapter.Typing), listOf("type", "swipe", "strip", "menu"), "Typing: the keys, swiping, fixing a swipe, the word menu")
    same(chapter(GuideChapter.Delete), listOf("backspace", "flick", "hold"), "Delete: Backspace, a flick, the hold")
    same(chapter(GuideChapter.Steering), listOf("steer", "selectword", "selectmore"), "Steering: the cursor, a word, more words")
    same(chapter(GuideChapter.Symbols), listOf("alt", "holdsym", "symmain", "symgroups", "clown"), "Symbols and emoji, then the clown")
    same(chapter(GuideChapter.Clipboard), listOf("paste", "clipboard"), "Copy and paste: pasting, then the clipboard")
    same(GuideChapter.Symbols.label, "Symbols and emoji", "The symbols chapter has the clown too")
    val byId = GUIDE_STEPS.associateBy { it.id }
    same(byId.getValue("steer").cue, GuideCue.Taps(2, drag = true), "Steering is two taps and a slide")
    same(byId.getValue("selectword").cue, GuideCue.Taps(3, drag = false), "Picking a word is three taps that lift")
    same(byId.getValue("selectmore").cue, GuideCue.Taps(3, drag = true), "Selecting more is three taps and a slide")
    same(GUIDE_STEPS.filter { it.nearMiss }.map { it.id }, listOf("strip"), "Only the strip step asks for a near miss")
    same(GuideHooks.takeNearMiss(GuideHooks.FIELD), false, "Nothing is asked for until the step asks")
    GuideHooks.nearMiss = true
    same(GuideHooks.takeNearMiss("some.other.app"), false, "No other field ever gets a near miss")
    same(GuideHooks.takeNearMiss(GuideHooks.FIELD), true, "The guide's field gets one near miss")
    same(GuideHooks.takeNearMiss(GuideHooks.FIELD), false, "One swipe uses it up")
}

/** A slide shows only the instruction that is needed now, and follows the keyboard forwards and back. */
private fun oneInstructionAtATime() {
    val alt = GuideFlow({ GuideSettings() })
    alt.start("alt")
    same(alt.cue, GuideCue.Spot(listOf(Layout.ALT)), "Alt glows first")
    same(alt.event(altOn), GuideVerdict.Stage(1), "Alt on, so the key with $ is next")
    same(alt.instruction.contains("$"), true, "The next instruction names $")
    same(alt.event(FieldChanged("x. #", 4, 4)).let { it is GuideVerdict.Hint && it.title!!.contains("not $") }, true, "Another symbol isn't $")
    same(alt.event(FieldChanged("x. #$", 5, 5)), GuideVerdict.Done, "$ finishes it")

    val groups = GuideFlow({ GuideSettings() })
    groups.start("symgroups")
    same(groups.step.setup, Panel.Closed, "The groups slide opens closed, and starts with Sym")
    same(groups.event(PanelSwiped(0f, .4f, true)), GuideVerdict.Stage(1), "A page turned, so a group is next")
    same(groups.event(PanelSwiped(.4f, 0f, true)).let { it is GuideVerdict.Hint && it.stage == 2 }, true,
        "A swipe sideways changes group too, and counts")
    same(groups.stage, 2, "and a symbol is next")
    same(groups.event(FieldChanged("x. ¤", 4, 4)), GuideVerdict.Done, "Any symbol finishes it")
    same(groupsPraise(groups.counted), "Backspace and Enter turn pages too.", "It teaches the way that wasn't used")
    same(groupsPraise(2 or 4), "Swiping left or right on the keys changes group too.", "or the other one")

    val clown = GuideFlow({ GuideSettings() })
    clown.start("clown")
    same(clown.event(keys(Panel.Emoji)), GuideVerdict.Stage(1), "Sym twice is the emoji, so the clown is next")
    same(clown.event(FieldChanged("Hi 😀", 5, 5)) is GuideVerdict.Hint, true, "Any emoji isn't the clown, and the slide says so")
    same(clown.event(FieldChanged("Hi 😀$CLOWN", 7, 7)), GuideVerdict.Done, "The clown finishes it")
    same(clown.step.done to clown.step.rain, "Hey, that's you." to CLOWN, "and it's you, raining down")

    val paste = GuideFlow({ GuideSettings() })
    paste.start("paste")
    same(paste.step.clip, PASTE_TEXT, "The paste lesson puts something stupid on the clipboard")
    same(paste.step.preselect, true, "and starts with a word selected, so the paste goes in its place")
    same(paste.step.done, "Yes, we know.", "with a line that says so")
    same((paste.event(FieldChanged("Paste over", 10, 10)) as GuideVerdict.Hint).title, "Lost the selection. Classic.",
        "The selected word lost, so the paste goes where the cursor is")
    same(paste.event(FieldChanged("Paste ove", 9, 9)), GuideVerdict.Wait, "It says so once")
    same(paste.event(FieldChanged("Paste $PASTE_TEXT", 19, 19)), GuideVerdict.Done, "The paste finishes it")

    val clips = GuideFlow({ GuideSettings() })
    clips.start("clipboard")
    clips.seed("Paste. ")
    same(clips.step.pinnedClips, GUIDE_CLIPS, "The clipboard lesson pins its clips")
    same(clips.event(keys(Panel.Emoji)).let { it is GuideVerdict.Hint && it.stage == 1 }, true, "The emoji are fine: A works there too")
    same(clips.event(keys(Panel.Closed)), GuideVerdict.Stage(0), "Closed again, so Sym again")
    same(clips.event(keys(Panel.Clipboard)), GuideVerdict.Stage(2), "The clipboard, straight away, is fine too")
    same(clips.event(FieldChanged("Paste. $PRANK_PASTE", 26, 26)).let { it is GuideVerdict.Hint && it.passes && it.title!!.startsWith("Gotcha") },
        true, "The nice clip is a prank, and the slide owns up")

    val menu = GuideFlow({ GuideSettings() })
    menu.start("menu")
    same(menu.event(MenuPicked("Save")), GuideVerdict.Wait, "A pick before the menu opens means nothing")
    same(menu.event(WordMenu("to")), GuideVerdict.Stage(1), "The menu opens, so picking is next")
    same(menu.event(Pushed(KEY_Q)).let { it is GuideVerdict.Hint && it.text.startsWith("Wrong key") }, true, "Q picks nothing, and the slide says so")
    same(menu.event(MenuPicked("Save")).let { it is GuideVerdict.Hint && it.passes && it.title == null && it.text == "Relax. Nothing was saved." },
        true, "Save counts, with its chip and the slide's own success line")
}

/** On the slide about the bar the swipe always comes out wrong, so there is always a word to fix with one tap. */
private fun theScriptedMiss() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("strip")
    same(flow.event(Recognised(listOf("tp"))), GuideVerdict.Stage(1), "Whatever was swiped, the slide says to tap the right word")
    same(flow.instruction.contains("dancing"), true, "The instruction points at the dancing word")
    same(flow.cue, GuideCue.Free, "The ghost stops swiping once there is a word to fix")
    val wrong = flow.event(Chosen("top"), "to")
    same(wrong is GuideVerdict.Hint && wrong.stage == 0 && flow.stage == 0, true, "A wrong word on the bar goes back to the start")
    flow.event(Recognised(listOf("tp")), "to")
    same(flow.event(Chosen("to"), "to"), GuideVerdict.Done, "The right word on the bar finishes it")
    same(GuideHooks.swapClip(PRANK_CLIP), PRANK_PASTE, "The nice clip pastes the prank in the guide")
    same(GuideHooks.swapClip(GUIDE_CLIPS.first()), GUIDE_CLIPS.first(), "Every other clip pastes as it is")
}

/** Each chapter has one text box, the same on each of its slides. */
private fun slideTexts() {
    fun texts(chapter: GuideChapter) = GUIDE_STEPS.filter { it.chapter == chapter }.map { it.text }.distinct()
    same(texts(GuideChapter.Typing), listOf(TYPING_TEXT), "The typing chapter has one text box")
    same(texts(GuideChapter.Symbols), listOf(SYMBOLS_TEXT), "The symbols chapter has one text box")
    same(TYPING_TEXT.first().isUpperCase(), true, "The first text box starts with a capital")
    same(TYPING_TEXT.any { it.isDigit() }, false, "The typing text is easy to type")
    for (step in GUIDE_STEPS.filter { it.chapter == GuideChapter.Delete }) same(step.seed, DELETE_TEXT, "${step.id} starts with the delete text")
    for (step in GUIDE_STEPS.filter { it.chapter == GuideChapter.Clipboard }) same(step.seed, CLIP_TEXT, "${step.id} starts with its text")
    for (text in listOf(DELETE_TEXT, CLIP_TEXT, STEER_TEXT) + DELETE_RESETS.map { it.second } + STEER_RESET.second)
        same(text.endsWith("."), true, "\"${text.takeLast(20)}\" ends with a full stop")
    same(DELETE_RESETS.size, 2, "Two resets for the delete chapter, the second longer and ruder")
    same(DELETE_RESETS[1].second.length > DELETE_RESETS[0].second.length, true, "The second reset is longer")
    val line = looping(STEER_TEXT)
    same(line.length, STEER_LOOPS * (STEER_TEXT.length + 1) - 1, "The steering line repeats, joined by a space")
    // The symbols chapter: each sentence starts with its symbol, in the order of the slides.
    val symbols = GUIDE_STEPS.mapNotNull { it.symbol }
    same(symbols, listOf("$", "@", "&", ANY_SYMBOL, CLOWN), "Each symbol slide waits for its own symbol")
    same(SYMBOL_PARTS.map { it.first }, symbols, "and the text box has them in that order")
    for (symbol in symbols) same(SYMBOLS_TEXT.contains(symbol + wordsAfter(symbol).trimEnd()), true, "$symbol starts its sentence")
    same(endsWithSymbol("x. $", "$"), true, "$ is $")
    same(endsWithSymbol("x. @ ", "$"), false, "@ is not $")
    same(endsWithSymbol("x. §", ANY_SYMBOL), true, "Any symbol counts on the groups slide")
    same(endsWithSymbol("x. a", ANY_SYMBOL) || endsWithSymbol("x.", ANY_SYMBOL) || endsWithSymbol("", ANY_SYMBOL), false,
        "A letter, a full stop or nothing is no symbol")
    same(endsWithSymbol("x. $CLOWN", ANY_SYMBOL), true, "An emoji counts as a symbol")
    for (step in GUIDE_STEPS.filter { it.chapter != GuideChapter.End }) {
        // Once done, the line under the title reacts to what you did. It never gives the next instruction.
        same(step.praise.isNotBlank(), true, "${step.id} says something about what you just did")
        same(step.stages.none { it.text == step.praise }, true, "${step.id}'s praise is no instruction")
    }
    // The text box carries straight on from its start: no dot, no line break.
    val race = Passage("one two three")
    same(race.text.startsWith("one two three one two three"), true, "The text box carries on with no mark between rounds")
    same(race.text.contains('\n') || race.text.contains('·'), false, "No line breaks and no dots")
    same(race.wordAt(race.track("one two three one ").next), "two", "Typing carries on into the next round")
    val passage = Passage("Nothing to unlearn: type with two hands or one")
    same(passage.wordAt(passage.track("Nothing to unlearn type with t two ").next), "hands", "A wrong word is skipped, matching by what's typed")
    same(passage.track("nothing TO").typed, "to", "Capitals don't matter, and the word being typed is followed")
    same(passage.track("Nothing zebra ").next, 1, "Any other word is ignored")
    val symbolsBox = Passage(SYMBOLS_TEXT)
    same(symbolsBox.wordAt(symbolsBox.track("").next), "is", "A symbol in the text box has no letters to type, so it's passed by itself")
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
    flow.start("selectword")
    same(flow.step.chapter to flow.stepInChapter, GuideChapter.Steering to 1, "The second of Steering")
    same(flow.idsOf(GuideChapter.Clipboard), listOf("paste", "clipboard"), "A chapter's slides, to jump to")
}

/** The passage above the field, as a typing race shows it. */
private fun thePassage() {
    same(GuideScript.ranges.size, GuideScript.steps.size, "Each step has its place in the script")
    for ((index, range) in GuideScript.ranges.withIndex())
        same(GuideScript.TEXT.substring(range.first, range.last + 1), GuideScript.steps[index], "Step $index sits where the script has it")
    val typing = GuideScript.track("Nothing t")
    same(typing.next to typing.typed, 1 to "t", "A word being typed is the word you're on")
    val past = GuideScript.track("Nothing tp unlearn ")
    same(past.next, 3, "A finished word moves on, right or wrong")
    same(past.wrong, setOf(1), "and a wrong one stays marked")
}

private fun theShareSlide() {
    same(GUIDE_STEPS.none { it.chapter != GuideChapter.End && it.offersShare }, true, "Only the end offers to share swipes")
}

private fun theScriptMovesOnWithEachWord() {
    same(GuideScript.progress(""), 0, "An empty text has typed nothing")
    same(GuideScript.progress("Nothing"), 1, "A swiped word counts at once")
    same(GuideScript.progress("Nothing t"), 1, "The start of the next word does not count yet")
    same(GuideScript.progress("Nothing tu"), 2, "A word that came out different still moves the line on, so nothing waits on it")
    same(GuideScript.wordAt(GuideScript.words.size), GuideScript.words.first(), "After the last word the script starts again")
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
    GuideHooks.script = null
}

/** The guide puts the keyboard in the menu that each step needs, and each step can show its gesture by itself. */
private fun stepsStartInTheRightMenu() {
    val byId = GUIDE_STEPS.associateBy { it.id }
    for (step in GUIDE_STEPS.filter { it.usesPanel })
        same(step.setup, Panel.Closed, "${step.id} starts closed, so that its first instruction opens the panel")
    for (step in GUIDE_STEPS.filter { !it.usesPanel }) same(step.setup, Panel.Closed, "${step.id} types, so the panel is closed for it")
    for (step in GUIDE_STEPS.filter { it.chapter != GuideChapter.End })
        same(step.demo != null || step.bar != null, true, "${step.id} must show its gesture by itself when nobody acts")
    same(byId.getValue("clipboard").demo, GuideDemo.Insert(listOf(Layout.SYM, Layout.CLIPBOARD_KEY), ""), "The clipboard slide lights Sym, then A")
    same(byId.getValue("holdsym").demo, GuideDemo.HoldLetter("@"), "The hold slide shows the key with @ held down")
    same(byId.getValue("alt").demo, GuideDemo.AltLetter("$"), "The Alt slide shows Alt, then the key with $")
    same(byId.getValue("paste").demo, GuideDemo.Insert(listOf(Layout.FN, "V", Layout.FN, "V"), ""), "The paste demo is only fn and V")
    same(byId.getValue("type").bar, GuideBar.Fix, "Autocorrect shows the fix swapping in on the bar")
    same(byId.getValue("menu").bar, GuideBar.Menu, "The word menu shows a ghost hold on the bar, and the menu opening")
}

/** Chapter one, the reference slice: one-line titles, a typo in every grey word, and each slide unlocks only what it teaches. */
private fun chapterOne() {
    val typing = GUIDE_STEPS.filter { it.chapter == GuideChapter.Typing }
    for (step in typing) {
        // One line at one size: these fit the phone's width with the check after them.
        for (line in listOf(step.title, step.done)) same(line.length <= TITLE_MOST, true, "\"$line\" is one line")
        for (line in step.stages.map { it.text } + step.praise) same(line.length <= LINE_MOST, true, "\"$line\" is one line")
    }
    same(typing.last().done, "OK, you're not that dumb.", "The chapter's roast is its last success line")
    same(typing.map { it.lessons }, listOf(emptySet(), setOf(GuideLesson.Swipe, GuideLesson.BarTap),
        setOf(GuideLesson.Swipe, GuideLesson.BarTap), setOf(GuideLesson.Swipe, GuideLesson.BarTap, GuideLesson.BarHold)),
        "Each slide unlocks what it teaches, and keeps what was taught")
    same(GUIDE_STEPS.filter { it.chapter != GuideChapter.Typing }.all { it.lessons == null }, true, "The other chapters lock nothing yet")
    same(typing.map { it.sides }, listOf(false, true, true, true), "Only the first slide shows just the bar's middle word")
    val typos = TYPING_TYPOS.split(' ')
    val words = TYPING_TEXT.split(' ').take(typos.size)
    same(typos.size, 14, "The first sentence, word for word")
    for ((typo, word) in typos.zip(words)) {
        // Only "typos" stays right: no typo of it is fixed.
        same(typo == word, word == "typos,", "\"$typo\" has a typo of \"$word\"")
        same(typo.takeLastWhile { !it.isLetter() }, word.takeLastWhile { !it.isLetter() }, "\"$typo\" keeps \"$word\"'s marks")
    }
    same(GUIDE_STEPS.first { it.id == "type" }.ghost, TYPING_TYPOS, "The first slide's ghost has the typos")
    GuideHooks.lessons = setOf(GuideLesson.Swipe)
    same(GuideHooks.allows(GuideLesson.Swipe) to GuideHooks.allows(GuideLesson.BarHold), true to false, "The lock lets through what's taught")
    GuideHooks.lessons = null
    same(GuideHooks.allows(GuideLesson.Symbols), true, "With no lock, everything works")
}

private const val TITLE_MOST = 33
private const val LINE_MOST = 50

private fun wrongEventsDoNotFinish() {
    val flow = GuideFlow({ GuideSettings() })
    same(flow.step.id, "type", "The guide starts with typing")
    same(flow.event(FieldChanged("key ", 4, 4)), GuideVerdict.Wait, "A word that no key typed does not finish typing")
    same(flow.event(Pushed(KEY_A)), GuideVerdict.Wait, "A letter is the start")
    flow.advance()
    same(flow.step.id, "swipe", "Next is the swipe")
    same(finishes(flow.event(Chosen("faster"))), false, "Touching a word must not finish the swipe")
    same(finishes(flow.event(FieldChanged("faster", 6, 6))), false, "Typing must not finish the swipe")
    same(flow.event(Pushed(62)), GuideVerdict.Wait, "Space is not a letter, so it says nothing")
    val menu = GuideFlow({ GuideSettings() })
    menu.start("menu")
    same(menu.event(Chosen("to")) is GuideVerdict.Hint, true, "A touch on a word is not a hold, and the slide says so")
}

/** The owner's scenarios: what happens says so, in the slide's place, and some of it counts. */
private fun scenarios() {
    // What a scenario says: its title, or the chip's text when it has no title.
    fun title(verdict: GuideVerdict) = (verdict as? GuideVerdict.Hint)?.let { it.title ?: it.text }.orEmpty()
    val type = GuideFlow({ GuideSettings() })
    same(title(type.event(Pushed(62))).startsWith("Nothing to fix"), true, "Space with nothing typed")
    type.event(Pushed(KEY_A))
    same(title(type.event(Fixed("the", "the"))).startsWith("Perfect spelling"), true, "A word typed right")
    same(title(type.event(Fixed("i", "I"))).startsWith("That's just a capital"), true, "Only a capital changed")
    same(type.event(Recognised(listOf("this"))), GuideVerdict.Wait, "A swipe on the typing slide: the keyboard's lock stops it, not the slide")
    same(type.event(Fixed("teh", "the")), GuideVerdict.Done, "A real fix finishes it")

    val swipe = GuideFlow({ GuideSettings() })
    swipe.start("swipe")
    same(title(swipe.event(Recognised(listOf("tp")), "to")), "Close. Tap the right one on the bar.", "A missed swipe points at the bar")
    same(swipe.event(Chosen("to"), "to"), GuideVerdict.Done, "The right word on the bar finishes it")
    same(swipe.jumps, true, "and does the next slide too")
    swipe.advance()
    same(swipe.step.id, "strip", "which opens next, done")
    same(title(swipe.event(Tapped("A"))), "That's a tap. Swipe the word.", "A tap, with tap keys on")

    val backspace = GuideFlow({ GuideSettings() })
    backspace.start("backspace")
    same(backspace.event(DeletedWord(held = false)).let { it is GuideVerdict.Hint && it.passes }, true, "A flick on the Backspace slide counts")

    val flick = GuideFlow({ GuideSettings() })
    flick.start("flick")
    same(title(flick.event(DeleteHoldStarted)).startsWith("Straight to the hold"), true, "A hold says it skips that lesson")
    same(flick.event(DeletedWord(held = false)), GuideVerdict.Wait, "The hold's first word isn't the flick")
    same(flick.event(DeletedWord(held = true)), GuideVerdict.Wait, "nor are its others")
    same(flick.event(DeletedWord(held = false)), GuideVerdict.Done, "One quick flick finishes it")
    flick.advance()
    same(flick.step.id, "steer", "and the hold slide is passed over")
    same(flick.back() && flick.step.id == "flick", true, "Back goes past it too")
    same(title(flick.event(Pushed(KEY_DEL))).startsWith("That's the last slide"), true, "Backspace on the flick slide")

    val hold = GuideFlow({ GuideSettings() })
    hold.start("hold")
    same(title(hold.event(DeletedWord(held = false))).startsWith("That was one word"), true, "A flick that lifts")

    val steer = GuideFlow({ GuideSettings() })
    steer.start("steer")
    same(title(steer.event(DoubleTapped(2))).startsWith("You lifted"), true, "Two taps that lift")
    same(title(steer.event(TwoTapsAbove)).startsWith("You lifted"), true, "Two taps above Space that lift")
    same(title(steer.event(CursorStarted(true))).startsWith("One tap too many"), true, "Three taps")
    same(steer.event(CursorStarted(false)), GuideVerdict.Stage(1), "Two taps, the second held")
    same(steer.event(Moved(0f, lifted = true)).let { it is GuideVerdict.Hint && it.stage == 0 }, true, "Held, then let go")
    val typed = steer.event(Pushed(KEY_A))
    same(title(typed) to (typed as GuideVerdict.Hint).text, "Typing's done. Keep up." to steer.instruction, "Typing on a slide that isn't about it")

    val more = GuideFlow({ GuideSettings() })
    more.start("selectmore")
    same(title(more.event(CursorStarted(false))).startsWith("That's two"), true, "Two taps, the second held, steer")
    same(title(more.event(FieldChanged("a b", 0, 1))).startsWith("One word"), true, "Three taps that lift pick one word")

    val holdsym = GuideFlow({ GuideSettings() })
    holdsym.start("holdsym")
    same(title(holdsym.event(HeldSymbol("#"))).startsWith("Not @"), true, "Another key held")
    holdsym.event(altOn)
    same(holdsym.event(FieldChanged("x. @", 4, 4)).let { it is GuideVerdict.Hint && it.passes && it.title == "Old habits." }, true,
        "Alt and the key type @, and it counts")

    val symmain = GuideFlow({ GuideSettings() })
    symmain.start("symmain")
    same(title(symmain.event(keys(Panel.Emoji))).startsWith("Emoji already"), true, "Too far")
    same(symmain.event(keys(Panel.Symbols)), GuideVerdict.Stage(1), "Sym once, then &")
    same(title(symmain.event(keys(Panel.Closed))).startsWith("Just looking"), true, "Closed without typing")

    val word = GuideFlow({ GuideSettings() })
    word.start("selectword")
    same(title(word.event(TwoTapsAbove)).startsWith("Two taps up there"), true, "Two taps above Space pick nothing")
    same(word.event(DoubleTapped(3)), GuideVerdict.Wait, "Three taps wait for the word to be picked")
    same(word.event(DoubleTapped(2)).let { it is GuideVerdict.Hint && it.passes }, true, "Two taps on Space pick a word too, and count")

    val altLetGo = GuideFlow({ GuideSettings() })
    altLetGo.start("alt")
    altLetGo.event(altOn)
    same(title(altLetGo.event(Released(57))).startsWith("Fine, that works too"), true, "Alt let go before a key waits for one key")
    val altHeld = GuideFlow({ GuideSettings() })
    altHeld.start("alt")
    altHeld.event(altOn)
    altHeld.event(Pushed(KEY_A))
    same(altHeld.event(Released(57)), GuideVerdict.Wait, "Alt let go after a key is a hold")

    val quick = GuideFlow({ GuideSettings() })
    quick.start("holdsym")
    quick.event(Pushed(KEY_Q))
    same(title(quick.event(Released(KEY_Q))).startsWith("Too quick"), true, "A letter let go before its symbol")

    val empty = GuideFlow({ GuideSettings() })
    empty.start("symmain")
    empty.event(keys(Panel.Symbols))
    same(title(empty.event(EmptyKey)).startsWith("That one's empty"), true, "A key with nothing on it")

    val settings = GuideFlow({ GuideSettings() })
    settings.start("swipe")
    same(title(settings.event(ModifierState(Level.Off, Level.Off, Panel.Settings, false))), "Settings? Nobody asked.", "Quick settings opened")
    same(title(settings.event(keys(Panel.Symbols))), "Easy, tiger. That's later.", "Sym opened too early")
    same(title(settings.event(altOn)), "Alt is on, so the touchpad won't type.", "Alt on too early")
}

private fun theCursorHasToMove() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("steer")
    same(flow.event(FieldChanged("on and on", 4, 4)), GuideVerdict.Wait, "A cursor change before the drag starts means nothing")
    same(flow.event(CursorStarted(false)), GuideVerdict.Stage(1), "The taps start the cursor, so sliding is next")
    same(flow.event(FieldChanged("on and on", 4, 4)), GuideVerdict.Wait, "The first change sets the mark")
    same(flow.event(FieldChanged("on and on", 3, 3)), GuideVerdict.Wait, "A nudge waits for the lift")
    same(flow.event(Moved(0f, lifted = true)).let { it is GuideVerdict.Hint && it.passes && it.title!!.startsWith("That's a nudge") }, true,
        "A nudge counts, with its own line")
    val far = GuideFlow({ GuideSettings() })
    far.start("steer")
    far.event(CursorStarted(false))
    far.event(FieldChanged("on and on", 9, 9))
    same(far.event(FieldChanged("on and on", 2, 2)), GuideVerdict.Done, "A real steer finishes it as it goes")

    val select = GuideFlow({ GuideSettings() })
    select.start("selectword")
    same(select.event(FieldChanged("on and on", 7, 7)), GuideVerdict.Wait, "A cursor alone is no selection")
    same(select.event(CursorStarted(false)) is GuideVerdict.Hint, true, "A held tap is steering, not picking, and the slide says so")
    same(select.event(FieldChanged("on and on", 3, 6)), GuideVerdict.Done, "A picked word finishes it")
}

private fun settingsThatStepsNeed() {
    var settings = GuideSettings(swipeTyping = false, cursorControl = false)
    val flow = GuideFlow({ settings })
    flow.start("swipe")
    same(flow.missing, GuideNeed.SwipeTyping, "A step can ask for its setting")
    flow.start("steer")
    same(flow.missing, GuideNeed.CursorControl, "The steer step asks for cursor control")
    flow.start("selectmore")
    same(flow.missing, GuideNeed.CursorControl, "And so does the select step")
    settings = GuideSettings(cursorControl = true)
    same(flow.missing, null, "Nothing is missing when the setting is on")
    flow.start("holdsym")
    same(flow.missing, GuideNeed.HoldSymbols, "The hold slide offers holding a letter when it's off")
    flow.start("paste")
    same(flow.missing, GuideNeed.FnAsCtrl, "The paste step needs fn as Ctrl")
    same(GuideNeed.FnAsCtrl.optional, false, "and offers it at once, as paste needs it")
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
    same(flow.step.id, "clipboard", "The clipboard")
}

private fun resuming() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("symgroups")
    same(flow.resumeId, "symgroups", "A step that sets up its own menu comes back to itself")
    flow.start("flick")
    same(flow.resumeId, "flick", "Other steps come back to themselves")
    flow.start("missing step")
    same(flow.step.id, "type", "An unknown step starts from the beginning")
    same(flow.chapterIndex, 0, "The first part is number zero")
    same(flow.chapterCount, GuideChapter.entries.size, "Each part counts")
    same(flow.stepCount, GUIDE_STEPS.size - 1, "The bar counts every step but the ending")
    same(flow.finished, false, "The start is not the end")
    flow.start("emoji")
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

/** Shift, Alt, Sym, fn and Ctrl put in no text, so they neither start nor keep up the playground's speed. */
private fun modifiersAreNoTyping() {
    for (code in listOf(android.view.KeyEvent.KEYCODE_SHIFT_LEFT, android.view.KeyEvent.KEYCODE_ALT_LEFT, android.view.KeyEvent.KEYCODE_SYM,
            android.view.KeyEvent.KEYCODE_FUNCTION, android.view.KeyEvent.KEYCODE_CTRL_LEFT))
        if (WpmMeter.counts(code)) throw AssertionError("Key $code must not count towards the speed")
    for (code in listOf(android.view.KeyEvent.KEYCODE_A, android.view.KeyEvent.KEYCODE_SPACE, android.view.KeyEvent.KEYCODE_DEL))
        if (!WpmMeter.counts(code)) throw AssertionError("Key $code is typing and counts")
}
