package com.titankeys.keyboard

import java.io.File
import com.titankeys.keyboard.KeyboardEvent.Chosen
import com.titankeys.keyboard.KeyboardEvent.Fixed
import com.titankeys.keyboard.KeyboardEvent.GroupPicked
import com.titankeys.keyboard.KeyboardEvent.MenuPicked
import com.titankeys.keyboard.KeyboardEvent.PanelSwiped
import com.titankeys.keyboard.KeyboardEvent.CursorStarted
import com.titankeys.keyboard.KeyboardEvent.DeleteHoldStarted
import com.titankeys.keyboard.KeyboardEvent.DeletedWord
import com.titankeys.keyboard.KeyboardEvent.DoubleTapped
import com.titankeys.keyboard.KeyboardEvent.EmptyKey
import com.titankeys.keyboard.KeyboardEvent.Released
import com.titankeys.keyboard.KeyboardEvent.TwoTapsAbove
import com.titankeys.keyboard.KeyboardEvent.FieldChanged
import com.titankeys.keyboard.KeyboardEvent.HeldSymbol
import com.titankeys.keyboard.KeyboardEvent.ModifierState
import com.titankeys.keyboard.KeyboardEvent.Moved
import com.titankeys.keyboard.KeyboardEvent.Pushed
import com.titankeys.keyboard.KeyboardEvent.Recognised
import com.titankeys.keyboard.KeyboardEvent.Tapped
import com.titankeys.keyboard.KeyboardEvent.WordMenu
import com.titankeys.keyboard.Modifiers.Level
import com.titankeys.keyboard.Modifiers.Panel

private fun keys(panel: Panel = Panel.Closed, ctrl: Boolean = false) = ModifierState(Level.Off, Level.Off, panel, ctrl)
private val altOn = ModifierState(Level.Off, Level.Once, Panel.Closed, false)
/** Android's key codes for A, Q, U and Backspace, so that the checks need no Android. */
private const val KEY_A = 29
private const val KEY_Q = 45
private const val KEY_DEL = 67

/** The real things a person does, in the order of the guide. Each slide teaches one instruction at a time. */
private val walk: List<Pair<String, List<KeyboardEvent>>> = listOf(
    "type" to listOf(Fixed("Gte", "Get"), FieldChanged("$TYPE_LINE ", 32, 32)),
    "swipe" to listOf(Recognised(listOf("swipe")), Recognised(listOf("per")), Recognised(listOf("word"))),
    "menu" to listOf(WordMenu(MENU_WORD), MenuPicked("Save"), MenuPicked("Favourite")),
    "backspace" to listOf(Pushed(KEY_DEL), FieldChanged("$BACKSPACE_LINE ", 0, 0)),
    "flick" to listOf(DeletedWord(held = false)),
    "hold" to listOf(DeleteHoldStarted) + List(REGRETS_TO_PASS) { DeletedWord(held = true) },
    "steer" to listOf(CursorStarted(false), FieldChanged("on and on ", 9, 9), FieldChanged("on and on ", 4, 4)),
    "selectmore" to listOf(CursorStarted(true), FieldChanged("on and on ", 0, 9)),
    "alt" to listOf(altOn, FieldChanged(ALT_LINE, 1, 1)),
    "holdsym" to listOf(FieldChanged(HOLD_SYMBOL_LINE, 4, 4)),
    "symmain" to listOf(keys(Panel.Symbols), FieldChanged(SYM_LINE, 4, 4)),
    "symgroups" to listOf(PanelSwiped(0f, .4f, true), FieldChanged(GROUPS_LINE, 4, 4)),
    "clown" to listOf(keys(Panel.Emoji), FieldChanged(CLOWN_LINE, 5, 5)),
    "paste" to listOf(FieldChanged(PASTE_LINE, 24, 24)),
    "clipboard" to listOf(keys(Panel.Symbols), keys(Panel.Clipboard), FieldChanged(CLIP_LINE, 26, 26)),
)

/** Slides that the guide no longer has: the boring guide explains them, or a new slide took over. */
private val gone = listOf("boring", "snippets", "exact", "names", "panel", "symbols", "copy", "share", "emoji", "emojigroups", "quick",
    "select", "sym")

/** The word menu is picked with the keys: the top two rows, split into as many parts as the menu has buttons. */
private fun theMenuKeys() {
    same(MenuKeys.button("I"), MenuKeys.UP, "I steps up")
    same(MenuKeys.button("O"), MenuKeys.DOWN, "O steps down")
    same(MenuKeys.button("P"), MenuKeys.BACK, "P goes back")
    same(listOf("K", "L").map(MenuKeys::button), listOf(MenuKeys.UP, MenuKeys.DOWN), "The second row senses them too")
    for (key in listOf("Q", "W", "E", "R", "T", "Y", "U", "A", "H", "J", "Z", "M", Layout.SPACE))
        same(MenuKeys.button(key), -1, "$key picks nothing: the word sits over the left")
    same(MenuKeys.WORD_COLUMNS + 3 + MenuKeys.AFTER_COLUMNS, 10, "The word, three buttons and P's column fill the ten columns")
    // The bar's buttons, in pixels, on the phone's width: each has room, and sits centred over its own key, as the Sym panel's
    // labels do. The word ends left of I's button.
    val width = 1080f
    val side = Layout.KEY_SIDE_DP * 2.625f
    // On the default keys and on a calibration that moved both edges: the buttons follow the real keys, from one place.
    for (alignment in listOf(KeyAlignment.DEFAULT, KeyAlignment(.05f, .93f))) {
        for ((column, key) in listOf(MenuKeys.UP to 'I', MenuKeys.DOWN to 'O', MenuKeys.BACK to 'P')) {
            val (left, right) = MenuKeys.slot(width, column, alignment)
            check(right - left > 70f) { "The button over $key has room: ${right - left} px" }
            val keyCentre = KeyGeometry(width, alignment).x(Layout.KEY_X[key - 'A'])
            check(kotlin.math.abs((left + right) / 2 - keyCentre) < .5f) { "The button over $key is centred on its key: ${(left + right) / 2} vs $keyCentre" }
        }
        check(MenuKeys.wordRight(width, 21f, alignment) < MenuKeys.slot(width, MenuKeys.UP, alignment).first) { "The word ends left of I's button" }
    }
    val moved = MenuKeys.slot(width, MenuKeys.UP, KeyAlignment(.05f, .93f))
    check(moved != MenuKeys.slot(width, MenuKeys.UP, KeyAlignment.DEFAULT)) { "Calibration moves the buttons" }
    val menu = java.io.File("android/kotlin/com/titankeys/keyboard/MenuKeys.kt").readText()
    check("KEY_SIDE_DP" !in menu && "KEY_SIDE_DP" !in java.io.File("android/kotlin/com/titankeys/keyboard/Strip.kt").readText()
        .substringAfter("private fun drawMenu(").substringBefore("private fun drawIcon(")) { "The menu reads its columns from the keys alone" }
    System.getenv("MENU_PNG")?.let { drawMenuBar(it, width, side) }
    theMenuLadder()
}

/**
 * The word menu's ladder (owner, 2026-11-13): four rungs for every word, BLOCKED ⇄ NOT SAVED ⇄ SAVED ⇄ FAVOURITE. I moves the word
 * one rung up and O one rung down, never two, and the menu always stays open. Every rung × every press is walked below, through
 * [MenuKeys.choices], the one function the keyboard and the guide use. tests/word-menu walks every kind of word through the real
 * word lists, with what each press does to them.
 */
private fun theMenuLadder() {
    val (blocked, new, saved, favourite) = MenuKeys.Step.entries
    same(MenuKeys.Step.entries, listOf(blocked, new, saved, favourite), "Four rungs, bottom to top")
    val (save, unsave, fav) = Triple(MenuKeys.Button.Save, MenuKeys.Button.Unsave, MenuKeys.Button.Favourite)
    val (spent, unfav) = MenuKeys.Button.Favourited to MenuKeys.Button.Unfavourite
    val (block, unblock) = MenuKeys.Button.Block to MenuKeys.Button.Unblock
    same(MenuKeys.choices(favourite), MenuKeys.Choices(spent, favourite, unfav, saved), "Favourite: the spent heart, and Unfavourite to saved")
    same(MenuKeys.choices(saved), MenuKeys.Choices(fav, favourite, unsave, new), "Saved: Favourite, and Unsave to not saved")
    same(MenuKeys.choices(new), MenuKeys.Choices(save, saved, block, blocked), "Not saved: Save, and Block")
    same(MenuKeys.choices(blocked), MenuKeys.Choices(unblock, new, null, null), "Blocked: Unblock, and nothing below")
    for (step in MenuKeys.Step.entries) {
        val choices = MenuKeys.choices(step)
        for ((button, next) in listOf(choices.i to choices.iNext, choices.o to choices.oNext)) {
            if (button == null) continue
            same(MenuKeys.press(step, button), next, "$step: $button goes where choices says")
            if (button != spent) check(kotlin.math.abs(next!!.ordinal - step.ordinal) == 1) { "$step: $button moves one rung, never two" }
        }
        same(MenuKeys.press(step, MenuKeys.Button.entries.first { it != choices.i && it != choices.o }), step, "$step: another button changes nothing")
    }
    // Where a word opens, from what the lists say of it.
    fun at(blocked: Boolean = false, favourite: Boolean = false, saved: Boolean = false, learned: Boolean = false, builtIn: Boolean = false,
           unsaved: Boolean = false) = MenuKeys.step(blocked, favourite, saved, learned, builtIn, unsaved)
    same(at(), new, "A word nobody knows: not saved")
    same(at(learned = true), saved, "A word TitanKeys learned and suggests: saved")
    same(at(saved = true), saved, "A word you saved")
    same(at(builtIn = true), saved, "A dictionary word counts as saved")
    same(at(builtIn = true, unsaved = true), new, "A dictionary word you took out isn't")
    same(at(saved = true, favourite = true), favourite, "A favourite")
    same(at(blocked = true, builtIn = true, favourite = true), blocked, "Blocked wins")
    for (word in listOf("hello", "don't", "Titankeys", "it’s")) same(MenuKeys.opens(word), true, "$word opens a menu")
    for (word in listOf("I", "a", "x", "😀", ":)", "#tag", "2nd", "a-b", "")) same(MenuKeys.opens(word), false, "\"$word\" opens none")
    // A walk under one finger: up the whole ladder and down to the bottom, each press one rung.
    var walk = blocked
    val seen = mutableListOf<String>()
    for (key in "IIIIOOO") {
        val choices = MenuKeys.choices(walk)
        val button = (if (key == 'I') choices.i else choices.o)!!
        walk = MenuKeys.press(walk, button)
        seen += "$key:$button→$walk"
    }
    same(seen, listOf("I:Unblock→New", "I:Save→Saved", "I:Favourite→Favourite", "I:Favourited→Favourite", "O:Unfavourite→Saved",
        "O:Unsave→New", "O:Block→Blocked"), "The walk")
    for (button in MenuKeys.Button.entries) check(MenuKeys.move(button) in -3..3) { "$button moves ${MenuKeys.move(button)}" }
    // The keyboard and the guide press through the same ladder, and no menu opens for a word with no ladder.
    val ime = java.io.File("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt").readText()
    check("val next = MenuKeys.press(menu.step, button)" in ime) { "The keyboard and the guide press through the ladder" }
    check("if (!MenuKeys.opens(held.word)) return false" in ime) { "One letter, symbols and emoji open no menu" }
    theMenuHover()
    theMenuWord()
    theMenuMotion()
}

/**
 * A resting finger hovers a button from its own key or the one below it: I or K, O or L. P and Backspace pick Back but show
 * nothing: Back has no pill. The hover stays through a press, so its pill shows that button's new action.
 */
private fun theMenuHover() {
    same(listOf("I", "K", "O", "L", "P", Layout.BACKSPACE).map(MenuKeys::hovered),
        listOf(MenuKeys.UP, MenuKeys.UP, MenuKeys.DOWN, MenuKeys.DOWN, MenuKeys.BACK, MenuKeys.BACK), "The hover's keys")
    for (key in listOf("U", "J", "M", "Q", Layout.ENTER, "")) same(MenuKeys.hovered(key), -1, "$key hovers nothing")
    for (step in MenuKeys.Step.entries) {
        same(MenuKeys.pills(MenuKeys.BACK, step), false, "Back has no pill at $step")
        same(MenuKeys.pills(MenuKeys.UP, step), true, "I has one at $step")
        same(MenuKeys.pills(MenuKeys.DOWN, step), step != MenuKeys.Step.Blocked, "O has one where it has a button at $step")
    }
    // After a press the finger stays on the key: the touchpad's lift for the press, which on the phone comes after the key is let
    // go, is the press's own and keeps the hover, and so does the quiet after it (owner, 2026-11-12 and 2026-11-13: the hover was
    // lost when the state changed). tests/word-menu walks every order the lift and the key came in.
    val kept = SteadyKey(Layout::keyAt)
    kept.touch(.75f, .1f)
    kept.keyDown(37)
    kept.press("I")
    kept.release(37, 1086)
    kept.lift(1210)
    same(kept.expire(1210 + 20 * SteadyKey.LIFT_MILLIS), false, "The pressed key's hover outlasts the press's own lift")
    same(kept.key, "I", "on I")
    kept.touch(.75f, .1f)
    kept.lift(9000)
    same(kept.expire(9000 + SteadyKey.LIFT_MILLIS), true, "and goes when the finger, seen again, lifts")
    val ime = java.io.File("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt").readText()
    val strip = java.io.File("android/kotlin/com/titankeys/keyboard/Strip.kt").readText()
    check("barHover.keyDown(keyCode)" in ime && "barHover.keyUp(keyCode" in ime) { "The bar's hover hears the keys go down and up" }
    check("if (column == MenuKeys.UP || column == MenuKeys.DOWN) column else -1" in ime) { "Only I's and O's buttons take the mark" }
    check("if (hovered == pillShown && action != pillAction) pillSnap = true" in strip) { "A new action under the finger takes its pill in place" }
    check("if (keyCode == menuKey && event.repeatCount > 0) return true" in ime) { "A key held after its pick never types" }
    check("if (info.packageName == packageName) field = field.copy(learning = false)" in ime) { "The guide's field learns nothing" }
    // No Forget and no Undo (owner, 2026-11-13): every press is a step, and the menu stays open.
    for (gone in listOf("Notice.Forgot", "pendingForget", "undoForget", "FORGET_UNDO_MILLIS", "Action.Undo", "Action.Forget"))
        check(gone !in ime && gone !in strip) { "$gone is gone" }
    check("pendingWords[held.word.lowercase()] = MenuKeys.Step." in ime) { "A press is seen at once, before the lists catch up" }
    for ((name, text) in listOf("TitanKeysIme" to ime, "Strip" to strip)) check("playMenuHint" !in text && "hints_shown" !in text) { "$name has no hint" }
}

/** The word sits centred left of the buttons, placed once, with no quotes; the pill's name never runs into its icon. */
private fun theMenuWord() {
    val width = 1080f
    val icon = 22 * 2.625f
    val (left, right) = MenuKeys.pill(width, MenuKeys.DOWN, 120f, icon, 28f, 18f, 17f)
    val (oLeft, oRight) = MenuKeys.slot(width, MenuKeys.DOWN)
    check(right > (oLeft + oRight) / 2 + icon / 2 && right < oRight + 17f) { "The pill ends just past O's icon" }
    check(MenuKeys.covers(left, right, MenuKeys.UP, width)) { "and covers I's button to its left: hovering Unsave covers Favourite" }
    check(kotlin.math.abs(right - left - (28f + 120f + 18f + icon + 17f)) < .01f) { "It holds the name and the icon" }
    // While the pill grows, its name stays by the icon: the owner saw "Fav♡urite" when it rode the pill's moving left edge.
    val iconLeft = right - 17f - icon
    val labelLeft = MenuKeys.pillLabel(right, 120f, icon, 18f, 17f)
    check(labelLeft + 120f <= iconLeft - 18f + .01f) { "The name ends before the icon" }
    same(MenuKeys.wordCentre(10f, 50f, 50f, 10f), 30f, "The word is centred in its room")
    same(MenuKeys.wordCentre(10f, 50f, 30f, 10f), 25f, "unless the widest pill could reach it: then it moves left just enough")
    val shown = MenuKeys.shorten("incomprehensibilities") { it.length <= 12 }
    check(shown.length <= 12 && shown.startsWith("inco") && shown.endsWith("ties") && "…" in shown) { "Shortened in the middle: $shown" }
    val strip = java.io.File("android/kotlin/com/titankeys/keyboard/Strip.kt").readText()
    val draw = strip.substringAfter("private fun drawMenu(canvas: Canvas)").substringBefore("private fun drawIcon(")
    check("if (menuWordCentre.isNaN()) menuWordCentre = MenuKeys.wordCentre(wordLeft, wordRight, wordEnd, textWidth)" in draw) { "The word is placed once" }
    check("strip_menu_word" !in draw) { "The word has no quotes" }
    check("isFakeBoldText" !in draw && "boldTypeface" in draw) { "The pill's name is Ink's medium weight" }
    check("shown.paintWord(menuCanvas)" in draw && "if (pill != null && !inPill && centre in pill.first..pill.second) continue" in draw) {
        "The scene draws the word under the pill, and the pill covers the buttons to its left"
    }
}

/**
 * No icon snaps to another (owner, 2026-11-12): every change is a few frames long, and the heart fills from the bottom as it warms.
 * The word's own effects are [MenuScene]'s: tests/word-menu and tests/menu-render check and draw them.
 */
private fun theMenuMotion() {
    val heart = MenuKeys.Button.Favourite
    val full = MenuKeys.Button.Favourited
    for ((from, to) in listOf(MenuKeys.Button.Save to heart, heart to full, full to heart, MenuKeys.Button.Unfavourite to MenuKeys.Button.Unsave,
        MenuKeys.Button.Unsave to MenuKeys.Button.Block, MenuKeys.Button.Block to null, null to MenuKeys.Button.Block)) {
        val middle = MenuMotion.icon(from, to, MenuMotion.ICON_MILLIS / 2)
        check(middle.size >= 1 && middle.any { it.alpha in .01f..0.99f || it.fill < 1f || it.orange in .01f..0.99f || it.scale != 1f }) {
            "$from to $to is in between halfway: $middle"
        }
        same(MenuMotion.icon(from, to, MenuMotion.ICON_MILLIS), listOf(MenuMotion.Layer(to, 1f, orange = MenuMotion.restingOrange(to))), "$from to $to ends")
    }
    val filling = MenuMotion.icon(heart, full, MenuMotion.ICON_MILLIS / 3).first { it.button == full }
    check(filling.fill in .1f..0.9f && filling.orange in .1f..0.9f) { "The heart fills and warms: $filling" }
    val draining = MenuMotion.icon(full, heart, MenuMotion.ICON_MILLIS / 3).first { it.button == full }
    check(draining.orange in .1f..0.9f) { "The orange drains: $draining" }
    check(MenuMotion.ICON_MILLIS in 200L..350L) { "Icon changes take 200 to 350 ms" }
    val strip = java.io.File("android/kotlin/com/titankeys/keyboard/Strip.kt").readText()
    // Places (owner, 2026-11-12): I, O and Back on the centres of their columns, a key apart; Back no longer dodges the corner.
    check("if (centre > most) centre = most" !in strip) { "Back no longer dodges the rounded corner" }
    check("barHover.press(pressed)" in java.io.File("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt").readText()) { "A picking key keeps the hover" }
    check("shown.reducedMotion = !GuideMotion.enabled" in strip) { "Motion off, one crossfade to the end state" }
}

/** The menu's geometry, drawn to a PNG over the keys' columns, to look at without a phone. Set MENU_PNG to a path to get it. */
private fun drawMenuBar(path: String, width: Float, side: Float) {
    val bar = 147
    val image = java.awt.image.BufferedImage(width.toInt(), bar * 2, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = java.awt.Color.BLACK
    g.fillRect(0, 0, image.width, image.height)
    g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 44)
    g.color = java.awt.Color.WHITE
    // The word, centred in the room left of the icons, as the bar draws it.
    val word = "“The”"
    val right = MenuKeys.wordRight(width, 21f)
    g.drawString(word, (side + right) / 2 - g.fontMetrics.stringWidth(word) / 2f, bar / 2f + 15)
    // The icons, 22 dp, centred on their columns, with Save marked softly as a resting finger marks it. Back is never marked.
    val icon = 58
    val accent = java.awt.Color(0xFF, 0x7A, 0x3D)
    for (index in 0..2) {
        val (left, end) = MenuKeys.slot(width, MenuKeys.UP + index)
        val centre = ((left + end) / 2).toInt()
        val marked = index == 0
        if (marked) {
            val markWidth = minOf((end - left).toInt() - 10, icon + 63)
            g.color = java.awt.Color(0xFF, 0x7A, 0x3D, 51)
            g.fillRoundRect(centre - markWidth / 2, (bar - icon - 37) / 2, markWidth, icon + 37, 63, 63)
        }
        g.color = if (marked) accent else java.awt.Color.WHITE
        g.stroke = java.awt.BasicStroke(5f)
        val x = centre - icon / 2
        val y = (bar - icon) / 2
        when (index) {
            // I: a mark of its button.
            0 -> g.drawPolygon(intArrayOf(x + 12, x + icon - 12, x + icon - 12, centre, x + 12), intArrayOf(y + 4, y + 4, y + icon - 4, y + icon - 18, y + icon - 4), 5)
            // O: a mark of its button.
            1 -> {
                g.drawRect(x + 12, y + 14, icon - 24, icon - 18)
                g.drawLine(x + 6, y + 10, x + icon - 6, y + 10)
            }
            // Back: an arrow.
            else -> {
                g.drawLine(x + 6, y + icon / 2, x + icon - 6, y + icon / 2)
                g.drawLine(x + 6, y + icon / 2, x + 24, y + 12)
                g.drawLine(x + 6, y + icon / 2, x + 24, y + icon - 12)
            }
        }
    }
    // The keys underneath: the top row, in its ten columns.
    g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 36)
    for ((column, key) in Layout.rows[0].withIndex()) {
        val (left, end) = MenuKeys.slot(width, column)
        g.color = java.awt.Color(0x33, 0x33, 0x33)
        g.fillRoundRect(left.toInt() + 4, bar + 14, (end - left).toInt() - 8, bar - 28, 14, 14)
        g.color = java.awt.Color.WHITE
        g.drawString(key, ((left + end) / 2 - g.fontMetrics.stringWidth(key) / 2).toInt(), bar + bar / 2 + 12)
    }
    g.dispose()
    javax.imageio.ImageIO.write(image, "png", java.io.File(path))
    println("Menu bar drawn to $path")
}

/** The phone's own name for the mode, the one number setup may say. */
private const val SLIDING_MODE_1 = "Sliding Mode 1"

fun main() {
    // ACCENT=off draws everything as with Settings > Orange accents off: Accent's cream in place of orange.
    if (System.getenv("ACCENT") == "off") Accent.on = false
    modifiersAreNoTyping()
    speedStartsWithAWord()
    readableWhenOrange()
    theMenuKeys()
    steadyKeyLight()
    playgroundSpeed()
    everyStepIsReachable()
    theStepsHaveOkayWords()
    oneStepForEachIdea()
    oneInstructionAtATime()
    theScriptedMiss()
    theFinale()
    theShareSlide()
    theChapterBar()
    slideTexts()
    thePassage()
    theScriptMovesOnWithEachWord()
    theStripOffersTheScriptsWord()
    stepsStartInTheRightMenu()
    demosEndWhereAPassDoes()
    theHoldZonesExplainThemselves()
    theFrontPageKeepsAllFourRows()
    wrongEventsDoNotFinish()
    chapterOne()
    everyTitleFitsItsLine()
    thePlaygroundKeepsItsText()
    theReopenedPlaygroundIsFocused()
    scenarios()
    everyWayToFailIsAnswered()
    everyPassShowsNext()
    theOwnersInfinityJoke()
    theRightClipPulses()
    runThroughFixes()
    theCursorHasToMove()
    steeringToAWord()
    theKeysPart()
    theContactsPart()
    setupHasOneDefinition()
    eachPageSaysWhereYouAre()
    theGuideForNewcomers()
    grabbingABunch()
    settingsThatStepsNeed()
    goingBack()
    resuming()
    theTrailFollowsTouchSize()
    demonstrationsTypePunctuation()
    aPanelMovesNothing()
    nothingPlaysAwayFromTheGuide()
    anAbandonedAccessibilityStepLetsGo()
    aSlideWaitsForYouToComeBack()
    yourClipComesBack()
    // Drawings of the slides in the new layout, to look at without a phone. See SlideRenders.kt.
    System.getenv("SLIDES_PNG")?.let { drawSlides(it, NEW_LAYOUT + GuideChapter.End) }
    System.getenv("FRONT_PNG")?.let { drawFrontPage(it) }
    println("Guide checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    check(actual == expected) { "$message: expected $expected, got $actual" }
}

/**
 * Away from the guide, nothing of it plays (bug hunt 2.1, #1: onPause stopped only the demo, so the help's haptic played every 6 s in
 * other apps, the demo restarted, and the clown hunt's tick vibrated). Each of the slide's timers stops as the guide goes out of
 * sight, everything that buzzes or draws checks that it's in sight, and each starts again as it comes back.
 */
private fun nothingPlaysAwayFromTheGuide() {
    val guide = java.io.File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    fun body(start: String) = guide.substring(guide.indexOf(start)).let { it.substring(0, it.indexOf("\n    }\n") + 6) }
    val timers = Regex("""private val (\w+) = (Runnable \{|object : Runnable)""").findAll(guide).map { it.groupValues[1] }.toSet()
    // The slide's own timers: everything that can buzz, draw or speak on a slide. The setup's and the playground's have their own.
    val slide = listOf("showHelp", "startDemo", "demoTick", "huntTick", "idleOnce", "idleTwice", "menuHint")
    same(timers.containsAll(slide), true, "The slide's timers are still these: $slide")
    val stop = body("private fun stopTimers()")
    for (timer in slide) same(stop.contains(timer), true, "Going out of sight stops $timer")
    same(body("override fun onPause()").contains("stopTimers()"), true, "onPause stops them, Home, another app or the screen off")
    same(body("override fun onResume()").contains("resumeTimers()"), true, "and onResume starts them again")
    for (start in listOf("private fun helpDue()", "private fun beginDemo()", "private fun hinted(")) {
        same(body(start).contains("visible"), true, "$start does nothing out of sight")
    }
    same(guide.substring(guide.indexOf("private val huntTick")).substringBefore("huntSaid = 1").contains("!visible"), true,
        "The hunt's tick stands still out of sight")
}

/**
 * The guide waits for Accessibility only while you're in Android's page for it (bug hunt 2.1, #20: an abandoned step left the
 * wait set for good, so turning TitanKeys on from Settings much later brought a new guide to the front). Coming back to the
 * guide (its window has focus again), or closing it, ends the wait; only accessibilityOn ever starts a guide from it.
 */
private fun anAbandonedAccessibilityStepLetsGo() {
    val guide = java.io.File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    fun body(start: String) = guide.substring(guide.indexOf(start)).let { it.substring(0, it.indexOf("\n    }\n") + 6) }
    same(body("override fun onWindowFocusChanged(").contains("awaitingAccessibility = false"), true, "Back in the guide, the wait ends")
    same(body("override fun onResume()").contains("awaitingAccessibility"), false,
        "but not on resume, which also runs as the disclosure's answer opens the page")
    same(body("override fun onDestroy()").contains("if (isFinishing) awaitingAccessibility = false"), true, "and when the guide closes")
    val sets = Regex("""awaitingAccessibility = (?!false)""").findAll(guide).count()
    same(sets, 1, "Only opening the Accessibility page starts the wait")
}

/**
 * A slide change that lands after you left the guide waits for you to come back (bug hunt 2.1, #31: entered away from the guide, the
 * paste slide put its clip in the user's own clipboard history, as the keyboard no longer served the guide's field).
 */
private fun aSlideWaitsForYouToComeBack() {
    val guide = java.io.File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    val enter = guide.substring(guide.indexOf("private fun enter(step: GuideStep) {")).substringBefore("heldKeys.clear()")
    same(enter.contains("if (!visible) {") && enter.contains("enterWhenBack = step"), true, "Out of sight, a slide waits")
    val resume = guide.substring(guide.indexOf("override fun onResume()")).substringBefore("\n    }\n")
    same(resume.contains("entering?.let(::enter)"), true, "and enters as the guide comes back")
    same(resume.indexOf("entering?.let(::enter)") < resume.indexOf("resumeTimers()"), true, "before its timers start again")
}

/**
 * The paste slide borrows the clipboard and gives it back (bug hunt 2.1, #32: it replaced the user's clip for good). The user's clip
 * is kept before the slide's goes on, and put back as the slide is left, the guide goes out of sight or closes, unless something
 * else was copied since. The keyboard never keeps the guide's clip in the user's history, wherever it lands (#31).
 */
private fun yourClipComesBack() {
    val guide = java.io.File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    val ime = java.io.File("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt").readText()
    fun body(start: String) = guide.substring(guide.indexOf(start)).substringBefore("\n    }\n")
    same(Regex("""clipboard\.setPrimaryClip\(android\.content\.ClipData\.newPlainText""").findAll(guide).count(), 1,
        "The guide puts its clip on the clipboard in one place, which keeps the user's first")
    same(body("private fun putGuideClip(").contains("userClip = runCatching { clipboard.primaryClip }"), true, "It keeps the user's clip")
    val back = body("private fun giveClipBack(")
    same(back.contains("!= GuideHooks.CLIP_LABEL) return") && back.contains("clipboard.setPrimaryClip(own)"), true,
        "and puts it back, unless something else was copied since")
    for (start in listOf("private fun moveOn(", "override fun onPause()", "override fun onDestroy()"))
        same(body(start).contains("giveClipBack()"), true, "$start gives the user's clip back")
    same(ime.contains("if (clip.description?.label == GuideHooks.CLIP_LABEL && !inGuide) return"), true,
        "The keyboard never keeps the guide's clip in the user's history")
}

/** The Titan 2 Elite's column, in px: the screen 1200 tall, the status bar 124, the keyboard's own bar 90. */
private const val PHONE_HEIGHT = 1200
private const val PHONE_STATUS = 124
private const val PHONE_BAR = 90

/**
 * The phone's drawing of the keys and its panel, in px, from Master's recording of build #12's playground (2026-11-13): the frame
 * with the drawing gone and the padding still the keyboard's showed the block half the drawing lower, 139 px, so the drawing is 278
 * px (the owner's calibration; the renders' default is 304), and with the panel settled the block was 51 px lower, half of the
 * drawing less the panel, so the panel is 176 px over the bar. Build #10's 51 px on a slide agrees.
 */
private const val PHONE_KEYS = 278
private const val PHONE_PANEL = 176

private fun phonePx(value: Int) = Math.round(value * 1076f / 688.6f)

/**
 * Opening a panel moves nothing on a slide (Master, 2026-11-13, build #10: as Sym opened on the clown slide, the title, the
 * instruction and the field dropped 51 px, and rose back as it closed). The column is the phone's, with the 48 dp top bar, and the
 * panel every page the same height. Build #10's layout, the drawing of the keys gone and the bottom padding the panel's, is here
 * too, to show that the check sees its drop.
 */
private fun aPanelMovesNothing() {
    fun column(block: Int, panelOver: Int) = GuideRoom.Column(height = PHONE_HEIGHT, top = PHONE_STATUS,
        above = phonePx(48) + phonePx(24), block = block, below = phonePx(24), keys = PHONE_KEYS, keyboard = PHONE_BAR,
        panel = if (panelOver > 0) PHONE_BAR + panelOver else 0, gap = phonePx(24))
    for (block in 200..520 step 20) {
        val closed = GuideRoom.blockTop(column(block, 0))
        val c = column(block, PHONE_PANEL)
        val build10 = c.top + c.above + maxOf(0, c.height - c.top - c.panel - c.above - c.block - c.below) / 2
        same(build10 - closed, 51, "Build #10's layout drops a $block px block as Sym opens")
        for (over in listOf(150, PHONE_PANEL, 202, 260)) same(GuideRoom.blockTop(column(block, over)), closed, "A $block px block with a panel $over px over the bar")
    }
    risesOnlyAsFarAsItMust("slide", ::column)
    thePlaygroundHoldsStill()
}

/**
 * A panel taller than the drawing of the keys' room, which this phone's never is: the block rises only as far as it must to keep
 * 24 dp over the panel, and never drops.
 */
private fun risesOnlyAsFarAsItMust(where: String, column: (block: Int, panelOver: Int) -> GuideRoom.Column) {
    for (block in 200..520 step 20) for (over in 0..900 step 10) {
        val c = column(block, over)
        val top = GuideRoom.blockTop(c)
        val closed = GuideRoom.blockTop(column(block, 0))
        val panelTop = c.height - c.panel
        check(top <= closed) { "A panel $over px over the bar drops a $block px block on the $where" }
        if (over == 0 || closed + block + c.gap <= panelTop) same(top, closed, "A $block px block clear of a panel $over px over the bar on the $where")
        else if (GuideRoom.over(c) > 0) same(top + block + c.gap, panelTop, "A $block px block over a panel $over px over the bar on the $where")
    }
}

/**
 * Opening or closing a panel moves nothing on the playground either, not even for a frame (Master, 2026-11-13, build #12's
 * recording: with any panel open the title, the line and the field sat 51 px lower, and at every opening and closing one frame
 * showed them 139 px lower, then they settled).
 *
 * The flash is two signals arriving apart: the keyboard says a panel is open or closed ([GuideActivity]'s ModifierState), and its
 * window takes or gives back the panel's room (the insets). Each frame is one of the four pairs of the two, the steady room the
 * keyboard's own; each goes through GuideRoom's real [GuideRoom.room], [GuideRoom.keys] and [GuideRoom.padding], as GuideActivity
 * does, into the column, and gives the block's top. Build #12's rule, a panel not still, shows the drop in the settled pair and
 * the flash in both half-way ones; the playground now gives every frame the same top.
 */
private fun thePlaygroundHoldsStill() {
    val extra = phonePx(80)
    val keyboard = PHONE_BAR
    val opened = PHONE_BAR + PHONE_PANEL
    fun top(block: Int, still: Boolean, inset: Int, panelOpen: Boolean): Int {
        val room = GuideRoom.room(inset, panelOpen, keyboard, extra, keep = true, follow = still)
        val keys = GuideRoom.keys(PHONE_KEYS, still, panelOpen || room.sized, end = false)
        val padding = GuideRoom.padding(still, panelOpen, room.keyboard, room.panel)
        // GuideActivity holds the block against a panel only where a panel is still.
        return GuideRoom.blockTop(GuideRoom.Column(height = PHONE_HEIGHT, top = PHONE_STATUS, above = phonePx(56) + phonePx(24),
            block = block, below = phonePx(24), keys = if (keys == GuideRoom.Keys.Gone) 0 else PHONE_KEYS, keyboard = padding,
            panel = if (still) room.panel else 0, gap = phonePx(24)))
    }
    val (_, g) = V2.image()
    for (block in (200..500 step 20) + frontBlock(g)) {
        val closed = top(block, false, keyboard, false)
        same(top(block, false, opened, true) - closed, 51, "Build #12's playground drops a $block px block with a panel open")
        same(top(block, false, keyboard, true) - closed, 139, "Build #12's playground flashes a $block px block lower as a panel opens, the word first")
        same(top(block, false, opened, false) - closed, 139, "Build #12's playground flashes a $block px block lower as a panel's room comes first, or goes last")
        for (inset in listOf(keyboard, opened)) for (open in listOf(false, true))
            same(top(block, true, inset, open), closed, "The playground's $block px block, the panel ${if (open) "open" else "closed"} and its room ${if (inset == opened) "taken" else "not"}")
    }
    risesOnlyAsFarAsItMust("playground") { block, over ->
        GuideRoom.Column(height = PHONE_HEIGHT, top = PHONE_STATUS, above = phonePx(56) + phonePx(24), block = block, below = phonePx(24),
            keys = PHONE_KEYS, keyboard = PHONE_BAR, panel = if (over > 0) PHONE_BAR + over else 0, gap = phonePx(24))
    }
    theRoomFollowsTheKeyboardOnThePlayground(extra)
    // On the phone the playground's block never rises: its drawing of the keys keeps all four rows (theFrontPageKeepsAllFourRows),
    // and the panel's top is lower than theirs.
    check(PHONE_PANEL < PHONE_KEYS) { "On the phone the panel reaches over the playground's drawing of the keys" }
}

/**
 * The playground's keyboard room is the keyboard's room now, never the most it ever saw (phone check, 2026-11-16: TitanKeys' own
 * screen opened over the playground, as the launcher opens it, and the playground came back squeezed under its header, the field
 * out of sight and the drawing of the keys over its title, for good). A new playground's first room can be far too large, before
 * the keyboard has settled: the slides keep the most, as their bar hides for a moment as a slide changes, and that kept it.
 */
private fun theRoomFollowsTheKeyboardOnThePlayground(extra: Int) {
    val huge = PHONE_BAR + 600
    fun settle(follow: Boolean, vararg insets: Int): GuideRoom.Room {
        var steady = 0
        var room = GuideRoom.room(insets.first(), false, steady, extra, keep = true, follow = follow)
        for (inset in insets) {
            room = GuideRoom.room(inset, false, steady, extra, keep = true, follow = follow)
            steady = room.steady
        }
        return room
    }
    same(settle(false, huge, PHONE_BAR).keyboard, huge, "Keeping the most, a first room too large stays: the phone's squeezed playground")
    same(settle(true, huge, PHONE_BAR).keyboard, PHONE_BAR, "Following, the playground's room is the keyboard's bar once it settles")
    same(settle(true, huge, PHONE_BAR, PHONE_BAR).let { it.keyboard to it.panel }, PHONE_BAR to 0, "and stays there")
    same(settle(true, PHONE_BAR, 0).keyboard, PHONE_BAR, "A gone inset keeps the last room")
    // A panel on the playground still moves nothing while it follows: its room arrives as a panel's, over the bar's.
    val opened = GuideRoom.room(PHONE_BAR + PHONE_PANEL, false, PHONE_BAR, extra, keep = true, follow = true)
    same(opened.sized to opened.keyboard, true to PHONE_BAR, "A panel's room on the playground is a panel's, the bar's room kept")
    val guide = File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    same("keep = guideLooks == true || playground,\n                follow = playground)" in guide, true, "The playground follows; the slides keep")
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
            same(text.replace(SLIDING_MODE_1, "").any { it.isDigit() }, false, "\"$text\" must not have a number but the phone's own Sliding Mode 1")
        // 34, for "Let TitanKeys handle the Back key." (owner, 2026-11-21: "at least let TitanKeys handle the back key to be clearer").
        same(part.title.length <= 34 && part.title.endsWith("."), true, "Setup title \"${part.title}\" is one short sentence")
        same(part.body.length <= 72, true, "Setup body \"${part.body}\" must stay short")
    }
    // Android's lists show the app as TitanKeys Keyboard (owner, 2026-11-13), so the steps that send you there say so.
    for (part in listOf(SetupPart.Enable, SetupPart.Choose, SetupPart.Overlay, SetupPart.Back))
        same(part.body.contains("TitanKeys Keyboard"), true, "Setup body \"${part.body}\" names the list entry")
    same(SetupPart.Overlay.help, "On the whole list? Find TitanKeys Keyboard, open it and flip the switch.", "The overlay help names the entry")
    // The step names the mode (owner, 2026-11-13: "turn on scroll assistant, let's specify mode 1").
    same(SetupPart.ScrollAssistant.body.contains(SLIDING_MODE_1), true, "The Scroll assistant step names Sliding Mode 1")
}

private fun oneStepForEachIdea() {
    fun chapter(name: GuideChapter) = GUIDE_STEPS.filter { it.chapter == name }.map { it.id }
    same(chapter(GuideChapter.Typing), listOf("type", "swipe", "menu"), "Typing: the keys, swiping, the word menu; no bar-pick slide (v5.2)")
    same(chapter(GuideChapter.Delete), listOf("backspace", "flick", "hold"), "Delete: Backspace, a flick, the hold")
    same(chapter(GuideChapter.Steering), listOf("steer", "selectmore"), "Steering: the cursor, then more words")
    same(chapter(GuideChapter.Symbols), listOf("alt", "holdsym", "symmain", "symgroups", "clown"), "Symbols and emoji, then the clown")
    same(chapter(GuideChapter.Clipboard), listOf("paste", "clipboard"), "Copy and paste: pasting, then the clipboard")
    same(GuideChapter.Symbols.label, "Symbols and emoji", "The symbols chapter has the clown too")
    val byId = GUIDE_STEPS.associateBy { it.id }
    same(byId.getValue("steer").cue, GuideCue.Taps(2, drag = true), "Steering is two taps and a slide")
    same(byId.getValue("selectmore").cue, GuideCue.Taps(3, drag = true), "Selecting more is three taps and a slide")
    same(GUIDE_STEPS.filter { it.nearMiss }.map { it.id }, emptyList<String>(), "No slide misses on purpose any more (v5.2)")
    same(GUIDE_STEPS.filter { it.forceFix }.map { it.id }, listOf("type", "swipe"), "Typed or swiped, any word becomes the line's next")
    // The front page's prompt is whole sentences (v5.3).
    val front = PlaygroundText.make(kotlin.random.Random(7)).split(' ')
    val starts = PlaygroundText.sentenceStarts(front)
    same(starts.first() == 0 && starts.zipWithNext().all { (a, b) -> b - a >= 3 }, true, "Every sentence found is a real one")
    val around = PlaygroundText.sentenceAround(starts, front.size, 40)
    same(40 in around && around.first in starts, true, "A word's sentence starts where a sentence does")
    for (step in GUIDE_STEPS.filter { it.chapter != GuideChapter.Typing && it.chapter != GuideChapter.End })
        same(blockedChip(step, GuideLesson.Swipe.name).contains("Type it"), false, "${step.id}'s swipe chip fits its own slide")
    same(byId.getValue("swipe").lessons.orEmpty().contains(GuideLesson.BarTap), false, "No slide teaches picking from the bar")
    same(GuideHooks.takeNearMiss(GuideHooks.FIELD), false, "Nothing is asked for until the step asks")
    GuideHooks.nearMiss = true
    same(GuideHooks.takeNearMiss("some.other.app"), false, "No other field ever gets a near miss")
    same(GuideHooks.takeNearMiss(GuideHooks.FIELD), true, "The guide's field gets one near miss")
    same(GuideHooks.takeNearMiss(GuideHooks.FIELD), false, "One word uses it up")
    // The playground is a real field with the real word menu (Master, 2026-11-13): its mark is never the guide's, and it only
    // shares the drawing of the keys.
    check(!GuideHooks.PLAYGROUND.contains(GuideHooks.FIELD)) { "The playground's mark doesn't count as the guide's" }
    GuideHooks.nearMiss = true
    same(GuideHooks.takeNearMiss(GuideHooks.PLAYGROUND), false, "The playground gets no near miss")
    GuideHooks.nearMiss = false
    check(GuideHooks.practice(GuideHooks.FIELD) && GuideHooks.practice(GuideHooks.PLAYGROUND)) { "Both draw the keys" }
    check(!GuideHooks.practice(null) && !GuideHooks.practice("com.titankeys.rawlog.reference")) { "No other field does" }
}

/** A slide shows only the instruction that is needed now, and follows the keyboard forwards and back. */
private fun oneInstructionAtATime() {
    val alt = GuideFlow({ GuideSettings() })
    alt.start("alt")
    same(alt.cue, GuideCue.Spot(emptyList(), held = listOf(Layout.ALT)), "Alt shows held, steady, never flashing")
    same(alt.event(altOn), GuideVerdict.Stage(1), "Alt on, so A is next")
    same(alt.cue, GuideCue.Spot(listOf("A"), held = listOf(Layout.ALT)), "A lights, and Alt stays held")
    val gapped = gapSeed(ALT_LINE, ALT_SYMBOL)
    same(alt.event(FieldChanged("$gapped#", 4, 4)).let { it is GuideVerdict.Hint && it.text.contains("not *") }, true, "Another symbol isn't *")
    same(alt.event(FieldChanged(ALT_LINE, 5, 5)), GuideVerdict.Done, "* in its gap finishes it")

    val groups = GuideFlow({ GuideSettings() })
    groups.start("symgroups")
    same(groups.step.setup, Panel.Closed, "The groups slide opens closed, and starts with Sym")
    same(groups.event(PanelSwiped(0f, .4f, true)), GuideVerdict.Stage(1), "A page turned, so finding ∞ is next")
    same(groups.step.stages.size, 2, "Two steps: turn a page, then find ∞")
    same(groups.step.title.contains(WEIRD), false, "The title doesn't name the weird one")
    same(GROUPS_LINE.startsWith("S") && GROUPS_LINE.endsWith("how long do I last in bed with your mother? ∞"), true, "Its line sets up the joke, and starts with S for the finale")
    same(groups.event(FieldChanged(GROUPS_LINE.replace(WEIRD, "¤"), 4, 4)).let { it is GuideVerdict.Hint && !it.passes }, true,
        "Another symbol isn't ∞")
    same(groups.event(keys(Panel.Closed)), GuideVerdict.Stage(0), "A wrong one closed the panel: Sym again, the chip stays")
    same(groups.event(FieldChanged(GROUPS_LINE.replace(WEIRD, "¤∞"), 4, 4)), GuideVerdict.Done, "∞ after a wrong one finishes it")
    val sideways = GuideFlow({ GuideSettings() })
    sideways.start("symgroups")
    same(sideways.event(GroupPicked(2)).let { it is GuideVerdict.Hint && it.text == "Yeah, that works too." && it.stage == 1 }, true,
        "The bottom row works too, and the slide says so")
    same(groupsPraise(4).startsWith("The bottom row works too"), true, "and its praise owns it")

    val clown = GuideFlow({ GuideSettings() })
    clown.start("clown")
    same(clown.event(keys(Panel.Emoji)), GuideVerdict.Stage(1), "Sym twice is the emoji, so the clown is next")
    same(clown.event(FieldChanged(gapSeed(CLOWN_LINE, CLOWN) + "😀", 5, 5)) is GuideVerdict.Hint, true, "Any emoji isn't the clown")
    same(clown.event(FieldChanged(CLOWN_LINE, 7, 7)), GuideVerdict.Done, "The clown finishes it")
    same(clown.step.done to clown.step.rain, "Hey, that's you." to CLOWN, "and it's you, raining down")
    for (hunt in listOf(WEIRD_HUNT, CLOWN_HUNT)) for (line in listOf(hunt.mock, hunt.hint, hunt.skip, hunt.fast))
        same(fits(line), true, "The hunt's \"$line\" is one line")
    same(CLOWN_HUNT.skip.contains("skip") && WEIRD_HUNT.skip.contains("skip"), true, "Each hunt says it's ok to skip, at the end")
    same(WEIRD_HUNT.fast, "Quick. Just like you in bed.", "A quick find of ∞ gets the owner's chip")
    same(WEIRD_HUNT.slow(40), "That took 40 seconds. Still longer than you claim to last.", "and a slow one the owner's line")
    same(CLOWN_HUNT.slow(40).contains("40"), true, "A slow clown says how long it took")
    same(huntFast(HUNT_DRAIN_SECONDS - 1) && !huntFast(HUNT_DRAIN_SECONDS), true, "Fast is before the mockery starts")
    same(GUIDE_STEPS.mapNotNull { it.hunt }, listOf(WEIRD_HUNT, CLOWN_HUNT), "The weird one and the clown are hunts")
    same(HUNT_DRAIN_SECONDS < HUNT_HINT_SECONDS && HUNT_HINT_SECONDS < HUNT_SKIP_SECONDS, true, "Mockery, then a hint, then leave to skip")
    same(Triple(HUNT_DRAIN_SECONDS, HUNT_HINT_SECONDS, HUNT_SKIP_SECONDS), Triple(10, 20, 30), "The hunts mock at 10 s, hint at 20 s and offer Skip at 30 s (owner, #68)")

    val paste = GuideFlow({ GuideSettings() })
    paste.start("paste")
    same(paste.step.clip, PASTE_TEXT, "The paste lesson puts the roast on the clipboard")
    same(paste.step.preselect, true, "and starts with words selected, so the paste goes in their place")
    same(PASTE_SEED.replace(PASTE_OVER, PASTE_TEXT), PASTE_LINE, "and the paste makes the line")
    same(paste.cue, GuideCue.Spot(listOf("V"), held = listOf(Layout.FN)), "fn shows held, steady; V lights")
    same(GUIDE_CLIPS.size <= 5, true, "Every clip, the prank too, fits on the clipboard's first page")
    same((paste.event(FieldChanged("As fn", 5, 5)) as GuideVerdict.Hint).text, "Lost the selection. Classic. Paste anyway.",
        "The selection lost, so the paste goes where the cursor is")
    same(paste.event(FieldChanged("As f", 4, 4)), GuideVerdict.Wait, "It says so once")
    same(paste.event(FieldChanged(PASTE_LINE, 19, 19)), GuideVerdict.Done, "The paste finishes it")

    val clips = GuideFlow({ GuideSettings() })
    clips.start("clipboard")
    clips.seed(CLIP_LEAD)
    same(clips.step.sandboxClips, GUIDE_CLIPS, "The clipboard lesson shows only its own clips")
    same(GUIDE_STEPS.count { it.sandboxClips.isNotEmpty() }, 1, "and no other slide has a sandbox")
    same(clips.event(keys(Panel.Emoji)).let { it is GuideVerdict.Hint && it.stage == 1 }, true, "The emoji are fine: A works there too")
    same(clips.event(keys(Panel.Closed)), GuideVerdict.Stage(0), "Closed again, so Sym again")
    same(clips.event(keys(Panel.Clipboard)), GuideVerdict.Stage(2), "The clipboard, straight away, is fine too")
    same(clips.event(FieldChanged(CLIP_LEAD + GUIDE_CLIPS.first(), 40, 40)), GuideVerdict.Wait, "A rude clip: the screen takes it back")
    same(clips.event(FieldChanged(CLIP_LINE, 60, 60)), GuideVerdict.Done, "The nice clip passes")
    same(clips.step.done, "No, seriously. I mean it.", "and the slide doubles down on the kind surprise (owner, #69)")
    same(GuideHooks.swapClip(PRANK_CLIP), PRANK_PASTE, "The nice clip pastes the prank in the guide")
    same(GuideHooks.swapClip(GUIDE_CLIPS.first()), GUIDE_CLIPS.first(), "Every other clip pastes as it is")
    same(OLD_GUIDE_CLIPS.containsAll(GUIDE_CLIPS), true, "The guide clears its own clips from the clipboard")

    theLadderOnTitanKeys()
}

/**
 * The word menu slide teaches the ladder on TitanKeys alone (owner, 2026-11-13): TitanKeys is selected in the box, so the bar shows it
 * alone, its menu opens at not saved at a tap or a hold, I saves it, and I again makes it a favourite, which passes. Each wrong move
 * says so in the owner's tone.
 */
private fun theLadderOnTitanKeys() {
    val step = GUIDE_STEPS.first { it.id == "menu" }
    same(step.title, "Make TitanKeys your favourite.", "The slide's title")
    same(step.selected, "TitanKeys", "TitanKeys is selected in the box, so the bar shows it alone")
    same(step.line to step.seed, MENU_LINE to MENU_LINE, "The slide's line")
    same(MENU_LINE, "TitanKeys belongs in your favourites", "It starts with TitanKeys (owner, 2026-11-13)")
    same(MENU_LINE.first(), 'T', "and keeps the T for the finale's GO TOUCH GRASS NOW")
    same(menuTarget("$MENU_LINE ")?.let { "$MENU_LINE ".substring(it.first, it.last + 1) }, "TitanKeys", "TitanKeys is the box's selected word")
    same(menuSelected("$MENU_LINE ", 0, MENU_WORD.length), true, "Selected from its first letter to its last")
    same(step.lessons, setOf(GuideLesson.BarHold), "Only the selected word's menu: typing, swiping, Space and Backspace are blocked")
    same(step.stages.map { it.text }, listOf("Tap TitanKeys on the bar. Nothing gets saved.", "Press I/K to save it.",
        "Press I/K again to favourite it."), "Tap, I/K, then I/K again: the keys named in pairs (owner, #67)")
    same(step.done to step.praise, "Favourited. Good taste." to "O/L steps it down. P/Backspace closes the menu.", "Done and praise")
    // The show-only menu opens at not saved: the ladder's I is Save, then Favourite.
    same(MenuKeys.choices(MenuKeys.Step.New).i to MenuKeys.choices(MenuKeys.Step.Saved).i, MenuKeys.Button.Save to MenuKeys.Button.Favourite,
        "Not saved, I saves; saved, I favourites")

    val menu = GuideFlow({ GuideSettings() })
    menu.start("menu")
    same(menu.event(MenuPicked("Save")), GuideVerdict.Wait, "A pick before the menu opens means nothing")
    same(menu.event(WordMenu(MENU_WORD)), GuideVerdict.Stage(1), "The menu opens, so Save is next")
    same(menu.labels to menu.cue, MENU_SAVE to GuideCue.Spot(listOf("I", "K"), rounds = 1, together = true), "I and K light once, together: Save")
    same(MENU_SAVE["K"] to MENU_SAVE[Layout.BACKSPACE], "Save" to "Back", "K works as I, and Backspace as P, and they say so")
    fun said(verdict: GuideVerdict) = (verdict as? GuideVerdict.Hint)?.let { it.title ?: it.text }
    same(said(menu.event(MenuPicked("Block"))), "Blocked TitanKeys? Rude. I/K brings it back.", "O at not saved blocks it")
    same(menu.stage, 1, "and it still waits for the save")
    menu.event(MenuPicked("Unblock"))
    same(menu.event(MenuPicked("Save")), GuideVerdict.Stage(2), "Saved, so the favourite is next")
    same(menu.labels to menu.cue, MENU_FAVOURITE to GuideCue.Spot(listOf("I", "K"), rounds = 1, together = true), "I and K light once again, together: Favourite")
    same(MENU_FAVOURITE["K"] to MENU_FAVOURITE["P"], "Favourite" to "Back", "K works as I, and P is Back")
    val unsaved = menu.event(MenuPicked("Unsave"))
    same(said(unsaved) to (unsaved as GuideVerdict.Hint).stage, "Unsaved. Cold. I/K, then I/K again." to 1, "O at saved unsaves it: back to the save")
    menu.event(MenuPicked("Save"))
    same(menu.event(MenuPicked("Favourite")), GuideVerdict.Done, "I again: a favourite, and the slide passes")

    val closed = GuideFlow({ GuideSettings() })
    closed.start("menu")
    closed.event(WordMenu(MENU_WORD))
    val close = closed.event(MenuPicked("Close"))
    same(said(close) to (close as GuideVerdict.Hint).stage, "Closed it. Tap TitanKeys again." to 0, "Backspace or P closes it: tap again")
    closed.event(WordMenu(MENU_WORD))
    closed.event(MenuPicked("Save"))
    val again = closed.event(MenuPicked("Close"))
    same((again as GuideVerdict.Hint).stage, 0, "Closed after the save too")
    closed.event(WordMenu(MENU_WORD))
    same(finishes(closed.event(MenuPicked("Favourite"))), true, "Opened again, it's saved: I favourites it, and that passes")

    // A tap on the selected word opens its menu as a hold does: the slide hears the menu, not the tap.
    same(GuideFlow({ GuideSettings() }).also { it.start("menu") }.event(WordMenu(MENU_WORD)), GuideVerdict.Stage(1), "A tap or a hold opens it")
    same(blockedChip(step, GuideLesson.BarTap.name), "That's another word, not TitanKeys. Tap TitanKeys.", "Another word on the bar, if one ever shows")
    for (stage in step.stages) same(stage.text.length <= 50, true, "Stage within 50: ${stage.text}")
    same(step.title.length <= 38, true, "Title within 38")
    theMenuSlidesOffTrackMoves(step)
    titanKeysIsSelected(step)
}

/**
 * Every off-track move on the word menu slide says what to do instead, in one short chip (owner, 2026-11-13: "what about did not
 * select the right word? or selected other stuff instead?"). Each stage answers the same; each chip is 50 or less.
 */
private fun theMenuSlidesOffTrackMoves(step: GuideStep) {
    fun said(verdict: GuideVerdict) = (verdict as? GuideVerdict.Hint)?.let { it.title ?: it.text }
    val text = "$MENU_LINE "
    for (stage in 0..2) {
        val flow = GuideFlow({ GuideSettings() })
        flow.start("menu")
        if (stage >= 1) flow.event(WordMenu(MENU_WORD))
        if (stage >= 2) flow.event(MenuPicked("Save"))
        same(flow.stage, stage, "At stage $stage")
        same(flow.event(FieldChanged(text, 0, 9)), GuideVerdict.Wait, "TitanKeys selected is the box as it should be, at $stage")
        same(said(flow.event(FieldChanged(text, 10, 18))), "Not the box. Tap TitanKeys on the bar.", "Other text selected in the box, at $stage")
        same(said(flow.event(FieldChanged(text, 3, 3))), "Not the box. Tap TitanKeys on the bar.", "A moved cursor, at $stage")
        same(said(flow.event(FieldChanged(text, 0, 4))), "Not the box. Tap TitanKeys on the bar.", "Part of TitanKeys, at $stage")
        same(said(flow.event(KeyboardEvent.MenuMissed)), "I/K or O/L. Nothing else does anything here.", "Another key in the menu, at $stage")
        same(flow.stage, stage, "and none of them moves the slide, at $stage")
    }
    // The lock's words on this slide: each says what it was, then points at TitanKeys (#90).
    same(blockedChip(step, GuideLesson.Typing.name), "That's typing, not a tap on TitanKeys.", "Typing a letter")
    same(blockedChip(step, GuideLesson.Swipe.name), "That's a swipe, not a tap on TitanKeys.", "Swiping a word")
    same(blockedChip(step, GuideLesson.Cursor.name), "That's a Space tap, not a tap on TitanKeys.", "Space taps that would select")
    same(blockedChip(step, GuideLesson.Space.name), "That's a space, not a tap on TitanKeys.", "A space")
    same(blockedChip(step, GuideLesson.Backspace.name), "That's Backspace, not a tap on TitanKeys.", "Backspace with the menu shut")
    for (lesson in GuideLesson.entries) same(fits(blockedChip(step, lesson.name)), true, "The lock's chip for $lesson fits")
    // A tap is as good as a hold now, so nothing on the slide says to hold, or not to tap.
    val words = step.stages.map { it.text } + listOfNotNull(step.help, step.done, step.praise) +
        GuideLesson.entries.map { blockedChip(step, it.name) } + listOf(MENU_CLOSED, MENU_UNSAVED, MENU_NOT_BOX, MENU_MISSED)
    for (line in words) same(line.contains("Hold TitanKeys") || line.contains("Hold, ") || line.contains("don't tap"), false,
        "\"$line\" says nothing of holding TitanKeys")
    for (line in listOf(MENU_CLOSED, MENU_UNSAVED, MENU_NOT_BOX, MENU_MISSED, MENU_BLOCKED))
        same(line.length <= 50, true, "\"$line\" is 50 or less")
    // Closed halfway, it keeps its step: held again after the save it opens saved, and I favourites it.
    val halfway = GuideFlow({ GuideSettings() })
    halfway.start("menu")
    halfway.event(WordMenu(MENU_WORD))
    halfway.event(MenuPicked("Save"))
    same(said(halfway.event(MenuPicked("Close"))), "Closed it. Tap TitanKeys again.", "Closed after the save")
    halfway.event(WordMenu(MENU_WORD))
    same(finishes(halfway.event(MenuPicked("Favourite"))), true, "and opened again, it carries on from saved")
    same(step.help, "Already selected. Tap TitanKeys on the bar.", "The help line")
}

/** & passes as soon as it's in, even after a wrong one (owner, v5.2); the screen takes a wrong one back out. */
private fun theScriptedMiss() {
    val flow = GuideFlow({ GuideSettings() })
    flow.start("symmain")
    same(flow.event(keys(Panel.Symbols)), GuideVerdict.Stage(1), "Sym, so & is next")
    val (before, after) = gapped(SYM_LINE, "&")
    same(flow.event(FieldChanged("$before%$after", 4, 4)).let { it is GuideVerdict.Hint && it.stage == null && it.text.contains("Took it back") },
        true, "A wrong symbol gets a chip, and the screen takes it back")
    same(onlyWrongInGap("$before%$after", SYM_LINE, "&"), true, "which is only another symbol in the gap")
    same(onlyWrongInGap("${before}v$after", SYM_LINE, "&"), false, "A held key's letter, before it turns, stays")
    same(flow.event(keys(Panel.Closed)), GuideVerdict.Stage(0), "The panel closed with it: Sym again, quietly")
    same(flow.event(keys(Panel.Symbols)), GuideVerdict.Stage(1), "Sym again")
    same(flow.event(FieldChanged("$before%&$after", 5, 5)), GuideVerdict.Done, "& in the gap passes, even with the wrong one left")
    same(symbolInGap("$before&$after", SYM_LINE, "&") && !symbolInGap("$before%$after", SYM_LINE, "&"), true, "Only & counts")
}

/** Each slide has its own line (v5): one real takeaway, and the first letters of them all spell the prank. */
private fun slideTexts() {
    val lines = SUMMARY_LINES
    same(lines.size, GUIDE_STEPS.size - 1, "Every slide has its line")
    same(GuideFinale.initials(lines), ACROSTIC.replace(" ", ""), "Their first letters spell \"$ACROSTIC\"")
    for (line in lines) {
        same(line.endsWith("."), false, "\"$line\" is a line the user finishes, not a sentence of ours")
        same(line.length <= 62, true, "\"$line\" fits the summary")
    }
    same(GUIDE_STEPS.first { it.id == "type" }.ghost.split(' ').size, TYPE_LINE.split(' ').size, "A typo for every word of slide one")
    for ((typo, word) in TYPE_TYPOS.split(' ').zip(TYPE_LINE.split(' '))) same(typo != word && typo.length == word.length, true,
        "\"$typo\" is a typo of \"$word\"")
    // The symbol slides: each line with its gap, the cursor in it, and filling it is the only way through.
    for (step in GUIDE_STEPS.filter { it.chapter == GuideChapter.Symbols }) {
        val symbol = step.symbol!!
        same(step.seed, gapSeed(step.line, symbol), "${step.id} starts with its line, gapped")
        same(step.cursorAt!!(step.seed), gapCursor(step.line, symbol), "and the cursor in the gap")
        same(filledGap(gapped(step.line, symbol).let { (a, b) -> a + symbol + b }, step.line), true, "${step.id}'s symbol fills it")
        same(wrongInGap(gapped(step.line, symbol).let { (a, b) -> a + "#" + b }, step.line, symbol), true, "Another symbol is wrong")
    }
    same(GUIDE_STEPS.mapNotNull { it.symbol }, listOf(ALT_SYMBOL, HOLD_SYMBOL, "&", WEIRD, CLOWN), "Each symbol slide waits for its own symbol")
    same(GUIDE_STEPS.first { it.id == "holdsym" }.title to HOLD_SYMBOL_LINE, "Add the ?. Hold V for it." to "Are you even reading this?", "Holding V for ?")
    same(gapSeed(SYM_LINE, "&").contains("terms  conditions"), true, "A gap between words keeps both spaces")
    same(BACKSPACE_SEED.dropLast(1), BACKSPACE_LINE, "One e too many, at the end")
    same(flickTarget(FLICK_SEED)?.let { FLICK_SEED.substring(it.first, it.last + 1) }, FLICK_WORD, "The flick slide marks its stupid word")
    same(FLICK_SEED.replace(" $FLICK_WORD", ""), FLICK_LINE, "and flicking it away makes the line")
    same(flickCursor(FLICK_SEED), FLICK_SEED.indexOf(FLICK_WORD) + FLICK_WORD.length, "The cursor waits right after it")
    val held = HOLD_SEED.replace(HOLD_REGRETS, "")
    same(held, HOLD_LINE, "Deleting the regrets makes the hold slide's line")
    same(HOLD_REGRETS.split(' ').count { it.isNotEmpty() } >= REGRETS_TO_PASS, true, "There are enough regrets to pass")
    same(holdTarget(HOLD_SEED, holdCursor(HOLD_SEED))?.let { HOLD_SEED.substring(it.first, it.last + 1) }, HOLD_REGRETS.trimEnd(),
        "The regrets are marked, up to the cursor")
    same(GRAB_LINE.contains(GRAB_PHRASE), true, "The grab slide's line has the words to grab")
    same(lineTyped("$TYPE_LINE ", TYPE_LINE) && !lineTyped(TYPE_LINE.dropLast(1), TYPE_LINE), true, "A line counts once typed through")
    same(DELETE_RESETS.size, 2, "Two resets for the delete chapter")
    for (step in GUIDE_STEPS.filter { it.chapter != GuideChapter.End }) {
        // Once done, the line under the title reacts to what you did. It never gives the next instruction.
        same(step.praise.isNotBlank(), true, "${step.id} says something about what you just did")
        same(step.stages.none { it.text == step.praise }, true, "${step.id}'s praise is no instruction")
    }
    val race = Passage("one two three")
    same(race.wordAt(race.track("one two three one ").next), "two", "Typing carries on into the next round")
    val passage = Passage("Nothing to unlearn: type with two hands or one")
    same(passage.wordAt(passage.track("Nothing to unlearn type with t two ").next), "hands", "A wrong word is skipped, matching by what's typed")
    same(passage.track("nothing TO").typed, "to", "Capitals don't matter, and the word being typed is followed")
    val fast = PlaygroundText.make(kotlin.random.Random(7))
    same(fast.any { it.isUpperCase() || it.isDigit() || it in ".,:;!?" }, false, "The playground's text is all plain, fast words")
    same(fast.contains("titankeys"), true, "The playground's text is about TitanKeys (v5)")
    // One paragraph with no stutter where two lines meet: "your messages your messages" (owner, 2026-11-13).
    val sentences = PlaygroundText.SENTENCES
    val stutterPairs = sentences.flatMap { a -> sentences.filter { b -> PlaygroundText.stutters(a, b) }.map { a to it } }
    same(stutterPairs.isNotEmpty(), true, "Some lines could stutter, so the rule matters")
    for (seed in 0 until 200) {
        val text = PlaygroundText.make(kotlin.random.Random(seed))
        for ((a, b) in stutterPairs)
            same(text.contains("$a $b"), false, "Seed $seed puts \"$b\" straight after \"$a\"")
    }
}

/** The finale: the summary lands, the letters light and gather into the phrase, then the logo grows from TK. */
private fun theFinale() {
    val lines = SUMMARY_LINES
    val measure = GuideFinale.Measure { text, size, _ -> text.length * size * .55f }
    fun texts(at: Long) = GuideFinale.frame(at, lines, 1076f, 120f, 760f, measure, 1076f / 688.6f).filterIsInstance<GuideFinale.Text>()
    same(texts(GuideFinale.LINE_IN / 2).size, 2, "At the start, only the first line is coming in")
    val stacked = texts(GuideFinale.summaryEnd(lines))
    same(stacked.count { it.alpha >= .99f }, lines.size * 2, "Then every line, whole")
    // No keyboard and no bar on the finale and the end, however they're reached (build #9: its bar came back after a Skip); every
    // slide before has them.
    same(GUIDE_STEPS.filter { !it.keyboard }.map { it.chapter }.distinct(), listOf(GuideChapter.End), "Only the end goes without the keyboard")
    same(GUIDE_STEPS.last().keyboard, false, "and the end, where the guide finishes, has none")
    val gathered = texts(GuideFinale.gatherStart(lines) + GuideFinale.GATHER + 10)
    same(gathered.map { it.text }.joinToString(""), ACROSTIC.replace(" ", ""), "Then only the first letters, which spell the phrase")
    // The phrase is the finale's headline (owner, 2026-11-13: it was a bit small): two balanced lines, read in order.
    val rows = gathered.map { Math.round(it.y) }.distinct()
    same(rows.size, 2, "on two lines")
    same(gathered.zipWithNext().all { (a, b) -> if (Math.round(a.y) == Math.round(b.y)) a.x < b.x else b.y > a.y }, true, "in order, left to right, top line first")
    same(GuideFinale.phraseLines(measure), listOf("GO TOUCH", "GRASS NOW"), "split where the lines balance")
    check(gathered.first().size >= 36f * 1076f / 688.6f) { "the phrase is at least the Display size" }
    same(GuideFinale.PHRASE_HOLD >= 3400L, true, "and holds long enough to land")
    // The end is one block: the name, then its line, then the buttons, above the grass.
    val dp = 1076f / 688.6f
    val nameSize = GuideFinale.nameSize(1076f, measure, dp)
    val (base, buttonsTop) = GuideFinale.endBlock(120f, 1000f, 400f, nameSize, dp)
    check(base - nameSize * GuideFinale.CAP >= 120f && buttonsTop > base && buttonsTop + 400f <= 1000f) { "the end's block fits above the grass, name first" }
    same(Math.round(buttonsTop - base - nameSize * .3f - GuideFinale.BUILT_DP * dp * 1.3f), Math.round(GuideFinale.BLOCK_GAP * dp), "with the block's gap between the name's line and the buttons")
    // The logo itself becomes the name (owner): the real mark comes back whole, then walks apart into its T and its K, which land
    // on the name's T and K, and the name's own type takes over as they land.
    val mark = GuideFinale.frame(GuideFinale.growStart(lines) - 10, lines, 1076f, 120f, 760f, measure, 1076f / 688.6f)
    same(mark.filterIsInstance<GuideFinale.Mark>().map { it.part }, listOf(GuideFinale.MarkPart.Whole), "The logo's mark comes back whole")
    same(mark.none { it is GuideFinale.Text && it.text in listOf("T", "K") }, true, "with no stand-in letters beside it")
    val walking = GuideFinale.frame(GuideFinale.growStart(lines) + GuideFinale.GROW / 2, lines, 1076f, 120f, 760f, measure, 1076f / 688.6f)
    val parts = walking.filterIsInstance<GuideFinale.Mark>().filter { it.alpha > 0f }
    same(parts.map { it.part }.sorted(), listOf(GuideFinale.MarkPart.T, GuideFinale.MarkPart.K), "Halfway, the mark is its own T and K")
    check(parts.first { it.part == GuideFinale.MarkPart.K }.x > parts.first { it.part == GuideFinale.MarkPart.T }.x) { "the K walks right of the T" }
    val logo = GuideFinale.frame(GuideFinale.total(lines), lines, 1076f, 120f, 760f, measure, 1076f / 688.6f)
    same(logo.none { it is GuideFinale.Mark }, true, "At the end the mark has become the name")
    val name = logo.filterIsInstance<GuideFinale.Text>().filter { it.text != GuideFinale.BUILT_FOR }.sortedBy { it.x }.joinToString("") { it.text }
    same(name, GuideFinale.NAME, "The mark grows into TitanKeys")
    same(logo.filterIsInstance<GuideFinale.Text>().any { it.text == GuideFinale.BUILT_FOR }, true, "and says what it's built for")
    same(GuideFinale.beats(lines).size, 2, "Two beats to feel: the phrase locking, the name grown")
}

/** The bar at the top shows chapters, and how far into this one you are. */
private fun theChapterBar() {
    val flow = GuideFlow({ GuideSettings() })
    same(flow.barChapters, GuideChapter.entries.dropLast(1), "Every chapter but the end")
    same(flow.stepInChapter to flow.stepsInChapter, 0 to 3, "The first slide of the three in Typing")
    flow.start("swipe")
    same(flow.stepInChapter, 1, "The second slide of Typing")
    // A tick for each slide (owner, v5.2): the ticks across the bar are every slide but the end.
    same(flow.barChapters.sumOf { flow.idsOf(it).size }, GUIDE_STEPS.size - 1, "One tick per slide across the whole bar")
    flow.start("selectmore")
    same(flow.step.chapter to flow.stepInChapter, GuideChapter.Steering to 1, "The second of Steering")
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
        same(step.demo != null || step.bar != null || step.cue != GuideCue.Free && step.cue !is GuideCue.Spot, true,
            "${step.id} must show its gesture by itself when nobody acts")
    // The held flick's field stays still: only the keyboard's ghost acts it out (owner, 2026-11-13).
    same(byId.getValue("hold").demo, null, "The hold slide's field has no demo")
    same(byId.getValue("hold").cue, GuideCue.Flick(hold = true), "The hold slide's keyboard acts out the held flick")
    same(byId.getValue("clipboard").demo, GuideDemo.Insert(listOf(Layout.SYM, Layout.CLIPBOARD_KEY), ""), "The clipboard slide lights Sym, then A")
    same(byId.getValue("holdsym").demo, GuideDemo.HoldLetter(HOLD_SYMBOL), "The hold slide shows the key with ? held down")
    same(byId.getValue("alt").demo, GuideDemo.AltLetter(ALT_SYMBOL), "The Alt slide shows Alt, then the key with *")
    same(byId.getValue("paste").demo, GuideDemo.Insert(listOf(Layout.FN, "V", Layout.FN, "V"), ""), "The paste demo is only fn and V")
    same(byId.getValue("type").bar, GuideBar.Fix, "Autocorrect shows the fix swapping in on the bar")
    same(byId.getValue("menu").bar, GuideBar.Menu, "The word menu slide's keys press I once the menu is open")
}

/** Every slide: one-line words, and each slide unlocks only what it teaches. */
private fun chapterOne() {
    for (step in GUIDE_STEPS.filter { it.chapter != GuideChapter.End }) {
        for (line in listOf(step.title, step.done)) same(fits(line, TITLE_MOST), true, "\"$line\" is one line")
        for (line in step.stages.map { it.text } + step.praise + listOfNotNull(step.help, step.helpTitle))
            same(fits(line), true, "\"$line\" is one line")
    }
    val keys = setOf(GuideLesson.Typing, GuideLesson.Backspace)
    val typing = GUIDE_STEPS.filter { it.chapter == GuideChapter.Typing }
    // Swiping types its own spaces, so its slide locks a lone Space (owner, 2026-11-13).
    // The word menu slide teaches only the hold on TitanKeys: everything else is blocked (owner, 2026-11-13).
    // The swipe slide opens swiping alone: every key press, Backspace included, is locked (owner, 2026-11-16).
    same(typing.map { it.lessons }, listOf(keys + GuideLesson.Space, setOf(GuideLesson.Swipe),
        setOf(GuideLesson.BarHold)), "Each slide unlocks what it teaches; none teaches picking from the bar (v5.2)")
    same(GUIDE_STEPS.filter { it.chapter == GuideChapter.Delete }.map { it.lessons }, listOf(setOf(GuideLesson.Backspace),
        setOf(GuideLesson.Flick), setOf(GuideLesson.Flick, GuideLesson.DeleteHold)), "Delete unlocks Backspace, the flick, then its hold")
    // The cursor slide bans three taps, which select (owner, 2026-11-16); the select slide opens them.
    same(GUIDE_STEPS.filter { it.chapter == GuideChapter.Steering }.map { it.lessons },
        listOf(setOf(GuideLesson.Cursor), setOf(GuideLesson.Cursor, GuideLesson.Select)), "Steering unlocks only the cursor gestures")
    val steerStep = GUIDE_STEPS.first { it.id == "steer" }
    same(blockedChip(steerStep, GuideLesson.Select.name), "That's three taps, not two. Hold the second one.",
        "Three taps on the cursor slide: refused, and said")
    GuideHooks.lessons = steerStep.lessons
    same(GuideHooks.allows(GuideLesson.Cursor) to GuideHooks.allows(GuideLesson.Select), true to false, "The cursor slide's lock")
    GuideHooks.lessons = null
    val ime = File("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt").readText()
    same(Regex("""inGuide && !guideAllows\(GuideLesson\.Select\)""").findAll(ime).count(), 3,
        "The keyboard refuses three taps where the slide doesn't open them: as the third lands, as it registers, as it slides")
    same(GUIDE_STEPS.filter { it.chapter == GuideChapter.Symbols }.map { it.lessons }, listOf(setOf(GuideLesson.Alt),
        setOf(GuideLesson.HoldSymbol), setOf(GuideLesson.Sym), setOf(GuideLesson.Sym), setOf(GuideLesson.Sym)),
        "Symbols unlocks Alt, then a held key, then Sym, one at a time")
    same(GUIDE_STEPS.filter { it.chapter == GuideChapter.Clipboard }.map { it.lessons }, listOf(setOf(GuideLesson.Ctrl), setOf(GuideLesson.Sym)),
        "Copy and paste unlocks fn for the paste, then Sym for the clipboard")
    for (line in DELETE_RESETS) same(line.length <= LINE_MOST, true, "The reset chip \"$line\" is one line")
    same(STEER_RESET.length <= LINE_MOST, true, "The steering reset chip is one line")
    same(typing.map { it.sides }, listOf(false, true, true), "Only the first slide shows just the bar's middle word")
    same(GUIDE_STEPS.first { it.id == "type" }.cue, GuideCue.Spell, "The first slide spells the next word's keys, then Space")
    GuideHooks.lessons = setOf(GuideLesson.Swipe)
    same(GuideHooks.allows(GuideLesson.Swipe) to GuideHooks.allows(GuideLesson.BarHold), true to false, "The lock lets through what's taught")
    GuideHooks.lessons = null
    same(GuideHooks.allows(GuideLesson.Sym), true, "With no lock, everything works")
    // Nothing is blocked silently: every lesson that a slide can block has its own words, on one line (owner, v5).
    for (step in GUIDE_STEPS) for (lesson in GuideLesson.entries) {
        val said = blockedChip(step, lesson.name)
        same(said.startsWith("That's ") && (", not " in said || "than needed" in said), true, "\"$said\" says what it was, not what was wanted (#90)")
        same(fits(said), true, "The lock's chip \"$said\" is one line")
    }
}

/**
 * Every title fits its lines in pixels, not just in letters (#66: "Put the cursor in the underlined word." was 38 letters, inside
 * the old count, but upper case in Display at 80% it took 100.5% of the line here, and the phone, which measures a little
 * differently, wrapped it into a second line it doesn't have and cut its last word). Measured as the renders draw them ([V2]):
 * Inter Bold, upper case, the phone's width less the side margins, at 80% of Display, the least a title shrinks to, and with
 * [TITLE_SPARE] of the line left over so the phone's measure can't tip it. The phone's font size can't change this:
 * GuideActivity's sizes are FixedScale's, font scale 1 at any setting, Android's largest (2.0) included.
 */
private fun everyTitleFitsItsLine() {
    val g = V2.image().second
    val inner = V2.W - 2 * V2.dp(V2.SIDE)
    val least = g.getFontMetrics(V2.display().let { it.deriveFont(it.size2D * .8f) })
    /** How much of [width] [text] takes, upper case, at the least size. */
    fun share(text: String, width: Int) = least.stringWidth(text.uppercase()).toFloat() / width
    /** How many lines [text] takes at the least size in [width] less the spare, wrapped at its spaces as a TextView wraps it. */
    fun lines(text: String, width: Int): Int {
        var count = 1
        var line = ""
        for (word in text.split(' ')) {
            val longer = if (line.isEmpty()) word else "$line $word"
            if (share(longer, width) <= 1 - TITLE_SPARE || line.isEmpty()) line = longer else { count++; line = word }
        }
        return count
    }
    for (step in GUIDE_STEPS.filter { it.chapter != GuideChapter.End }) {
        same(share(step.title, inner) <= 1 - TITLE_SPARE, true,
            "The title \"${step.title}\" fits its one line on the phone (${"%.3f".format(share(step.title, inner))} of it)")
        // Once passed, the check sits after the title, in the same line.
        val done = inner - V2.dp(28)
        same(share(step.done, done) <= 1 - TITLE_SPARE, true,
            "The pass line \"${step.done}\" fits its one line with the check (${"%.3f".format(share(step.done, done))} of it)")
    }
    // Setup wraps its titles over two lines (GuideActivity.SETUP_TITLE_LINES), in the same type.
    for (title in SetupPart.entries.map { it.title } + SetupPart.KEYS_WARN_TITLE)
        same(lines(title, inner) <= 2, true, "The setup title \"$title\" fits its two lines")
    same(lines("Put the cursor in the underlined word.", inner), 2, "The title the phone cut takes two lines here too")
    // The phone's sizes ignore the font size, and a title that still runs over shrinks further rather than lose a word.
    val guide = File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    same("override fun attachBaseContext(base: Context) = super.attachBaseContext(FixedScale.wrap(base))" in guide, true,
        "The guide's sizes are fixed, at any font size")
    same("fontScale = 1f" in File("android/kotlin/com/titankeys/keyboard/FixedScale.kt").readText(), true,
        "and FixedScale sets font scale 1")
    same(Regex("""shrinkToFit\(title, \(titlePx \* SHRINK\)""").containsMatchIn(guide), false, "Slide titles no longer stop at 80%")
    same("shrinkToFit(title, (titlePx * TITLE_FLOOR).toInt(), titlePx.toInt())" in guide, true,
        "they shrink past it as a last resort, never cut")
}

/**
 * The playground keeps what you typed across Home and back (Master's phone checks: tools/checks/home-focus.py typed "hello",
 * pressed Home, came back through the launcher, and the field was empty every time). The launcher brings the app's own screen to
 * the front, which is singleTask and so closes the playground over it, and the front page opens a new one: the old one hands its
 * text, cursor and type-along line to the new one. Back leaves the app, and the next playground starts empty.
 */
private fun thePlaygroundKeepsItsText() {
    val guide = File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    fun body(start: String) = guide.substring(guide.indexOf(start)).let { it.substring(0, it.indexOf("\n    }\n") + 6) }
    same(body("override fun onPause()").contains("if (playground && started && !settingUp && !leftByBack)\n            playgroundKept = PlaygroundKept(" +
        "field.text.toString(), field.selectionEnd, passage, passageFrom, passageText, passageSource,\n                java.lang.ref.WeakReference(this))"), true,
        "Out of sight, the playground keeps its text, its cursor and its line")
    val show = body("private fun showPlayground()")
    same(show.contains("if (kept != null) keepPlayground(kept) else startPassage(PlaygroundText.make("), true,
        "The next playground puts it back, or starts a new line when there's nothing kept")
    same(show.indexOf("playgroundKept = null") in 0 until show.indexOf("keepPlayground(kept)"), true, "and takes it only once")
    val keep = body("private fun keepPlayground(kept: PlaygroundKept)")
    same(listOf("seeding = true", "field.setText(kept.text)", "seeding = false", "passage = kept.passage", "passageFrom = kept.passageFrom")
        .all { it in keep }, true, "The text goes back quietly, and the line follows on from where it was")
    same(body("override fun onResume()").contains("if (playgroundKept?.from?.get() === this) playgroundKept = null"), true,
        "The same playground back in sight has its own text still, so its copy is let go")
    val back = body("override fun onBackPressed()")
    same(back.indexOf("leftByBack = true") in 0 until back.indexOf("finishAffinity()") &&
        back.indexOf("playgroundKept = null") in 0 until back.indexOf("finishAffinity()"), true, "Back leaves the app with nothing kept")
    // The check on the phone expects exactly this.
    same("keeps its text\nwhen you leave the app and come back" in File("tools/checks/home-focus.py").readText(), true,
        "tools/checks/home-focus.py checks the same")
}

/**
 * The playground back in front has its field focused (phone check, 2026-11-16: reopened over TitanKeys' own screen, typing reached
 * the field, but neither uiautomator nor the served view saw it focused). Asked once laid out, as a field not laid out can't take it.
 */
private fun theReopenedPlaygroundIsFocused() {
    val guide = File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    fun body(start: String) = guide.substring(guide.indexOf(start)).let { it.substring(0, it.indexOf("\n    }\n") + 6) }
    same(body("override fun onWindowFocusChanged(").contains("if (playground) focusPlaygroundField()"), true,
        "Back in front, the playground focuses its field")
    same(body("private fun showPlayground()").contains("focusPlaygroundField()"), true, "and as it shows")
    val focus = body("private fun focusPlaygroundField()")
    same(listOf("field.post {", "settingUp || supportInvite != null || field.isFocused) return@post",
        "field.isFocusableInTouchMode = true", "field.requestFocus()")
        .all { it in focus }, true, "after layout, unless focused, in setup, or behind the support invite")
}

/** How much of a title's line stays free in the renders' measure, for the phone's (#66). */
private const val TITLE_SPARE = .05f

/** The chapters rebuilt in the new layout so far. */
private val NEW_LAYOUT = setOf(GuideChapter.Typing, GuideChapter.Delete, GuideChapter.Steering, GuideChapter.Symbols,
    GuideChapter.Clipboard)
/** Titles shrink to fit their one line now (v5.2). */
private const val TITLE_MOST = 38
private const val LINE_MOST = 50
/**
 * Lines allowed past one line of the slide. None now: F shortened the ones that ran over (docs/words-m1.md), so every line keeps
 * the limits.
 */
private val F_LONGER = emptySet<String>()
private fun fits(line: String, most: Int = LINE_MOST) = line.length <= most || line in F_LONGER

private fun wrongEventsDoNotFinish() {
    val flow = GuideFlow({ GuideSettings() })
    same(flow.step.id, "type", "The guide starts with typing")
    same(flow.event(FieldChanged("key ", 4, 4)), GuideVerdict.Wait, "A word that isn't the line does not finish typing")
    same(flow.event(Pushed(KEY_A)), GuideVerdict.Wait, "A letter is the start")
    flow.start("swipe")
    same(finishes(flow.event(Chosen("faster"))), false, "Touching a word must not finish the swipe")
    same(finishes(flow.event(FieldChanged("faster", 6, 6))), false, "Typing must not finish the swipe")
    val menu = GuideFlow({ GuideSettings() })
    menu.start("menu")
    same(finishes(menu.event(Chosen(MENU_WORD))), false, "A word chosen from the bar does not finish the menu slide")
}

/** The owner's scenarios: what happens says so, in a chip, and some of it counts. */
private fun scenarios() {
    fun title(verdict: GuideVerdict) = (verdict as? GuideVerdict.Hint)?.let { it.title ?: it.text }.orEmpty()
    val type = GuideFlow({ GuideSettings() })
    same(title(type.event(Pushed(62))).startsWith("Letters first"), true, "Space with nothing typed")
    type.event(Pushed(KEY_A))
    same(title(type.event(Fixed("Space", "Space"))).startsWith("Make a typo, bro"), true, "A word typed right")
    same(type.event(Fixed("Sapce", "Space")), GuideVerdict.Wait, "A real fix counts, and the line goes on")
    same(type.event(FieldChanged("$TYPE_LINE ", 21, 21)), GuideVerdict.Done, "The line typed through finishes it")
    val perfect = GuideFlow({ GuideSettings() })
    perfect.start("type")
    same(perfect.event(FieldChanged("$TYPE_LINE ", 21, 21)).let { it is GuideVerdict.Hint && it.passes }, true,
        "No typo at all still passes, and the slide says so")

    val swipe = GuideFlow({ GuideSettings() })
    swipe.start("swipe")
    // Keys do nothing here: the keyboard's lock keeps them out of the field and says what they were (owner, 2026-11-16).
    val swipeStep = GUIDE_STEPS.first { it.id == "swipe" }
    GuideHooks.lessons = swipeStep.lessons
    same(listOf(GuideLesson.Typing, GuideLesson.Space, GuideLesson.Backspace, GuideLesson.Flick, GuideLesson.DeleteHold, GuideLesson.Enter)
        .none(GuideHooks::allows) && GuideHooks.allows(GuideLesson.Swipe), true, "Only swiping gets through on the swipe slide")
    GuideHooks.lessons = null
    same(blockedChip(swipeStep, GuideLesson.Typing.name), "That's typing, not a swipe. Swipe this one.", "A letter, refused")
    same(blockedChip(swipeStep, GuideLesson.Backspace.name), "That's Backspace, not a swipe. Swipe this one.", "Backspace, refused")
    same(blockedChip(swipeStep, GuideLesson.Space.name), "That's a space, not a swipe. Swipes add their own.", "Space, refused")
    same(swipe.event(Pushed(KEY_A)), GuideVerdict.Wait, "A key that got through anyway moves nothing on")
    // Whatever is swiped becomes the word (the keyboard puts the line's word first), so any swipe counts (owner, v5.2).
    same(swipe.event(Recognised(listOf("tp")), "to"), GuideVerdict.Wait, "Any swipe counts, however bad: no bar to pick from")
    same(swipe.liveLine(), swipesLeft(2), "and the count goes down")
    same(title(swipe.event(Tapped("A"))), "That's a tap. Slide, don't lift.", "A tap, with tap keys on")

    val flick = GuideFlow({ GuideSettings() })
    flick.start("flick")
    same(flick.event(DeletedWord(held = true)), GuideVerdict.Wait, "A held word isn't the flick")
    same(flick.event(DeletedWord(held = false)), GuideVerdict.Done, "One quick flick finishes it")
    same(blockedChip(flick.step, GuideLesson.DeleteHold.name), "That's a held flick, not a flick. Flick and lift.", "The hold, blocked")
    same(blockedChip(flick.step, GuideLesson.Backspace.name), "That's a press, not a flick. Flick from Backspace.", "Backspace on the flick slide")
    same(blockedChip(GUIDE_STEPS.first { it.id == "steer" }, GuideLesson.Typing.name), tapMissLine(GuideMiss.Typing, 2),
        "Letters on the cursor slide: typing, not a tap")

    val regret = GuideFlow({ GuideSettings() })
    regret.start("hold")
    same(title(regret.event(DeletedWord(held = false))).startsWith("That was one word"), true, "A flick that lifts")
    same(regret.liveLine(), "Regrets deleted: 1", "then the count")
    regret.event(DeleteHoldStarted)
    regret.event(DeletedWord(held = true))
    same(title(regret.event(Moved(40f, lifted = true))).startsWith("Therapy's not over"), true, "A hold let go too soon")
    regret.event(DeleteHoldStarted)
    repeat(REGRETS_TO_PASS - 3) { same(regret.event(DeletedWord(held = true)), GuideVerdict.Wait, "Under eight, it waits") }
    same(regret.event(DeletedWord(held = true)), GuideVerdict.Done, "The eighth word passes")
    same(regretsFinal(12).length <= LINE_MOST, true, "The frozen count is one line")

    val steer = GuideFlow({ GuideSettings() })
    steer.start("steer")
    same(title(steer.event(DoubleTapped(2))), "That's a lift, not a hold. Keep the second down.", "Two taps that lift")
    same(title(steer.event(TwoTapsAbove)), "That's a lift, not a hold. Keep the second down.", "Two taps above Space that lift")
    same(title(steer.event(CursorStarted(true))), "That's three taps, not two. Hold the second one.", "Three taps")
    same(steer.event(CursorStarted(false)), GuideVerdict.Stage(1), "Two taps, the second held")
    same(steer.event(Moved(0f, lifted = true)).let { it is GuideVerdict.Hint && it.stage == 0 }, true, "Held, then let go")

    val more = GuideFlow({ GuideSettings() })
    more.start("selectmore")
    same(title(more.event(CursorStarted(false))), "That's two taps, not three. Hold the third one.", "Two taps, the second held, steer")
    same(more.event(FieldChanged("a b", 0, 1)), GuideVerdict.Wait, "A held third tap picks a word first: no scolding yet")
    same(title(more.event(Moved(0f, true))), "That's a lift, not a hold. Keep the third down.", "Three taps that lift pick one word: a lift, not a hold")
    val held = GuideFlow({ GuideSettings() })
    held.start("selectmore")
    held.event(FieldChanged("a b", 0, 1))
    same(held.event(CursorStarted(true)), GuideVerdict.Stage(1), "and the held third tap that starts the slide passes the step")

    val holdsym = GuideFlow({ GuideSettings() })
    holdsym.start("holdsym")
    same(title(holdsym.event(HeldSymbol("#"))).startsWith("Not ?"), true, "Another key held")
    same(holdsym.event(FieldChanged(HOLD_SYMBOL_LINE, 4, 4)), GuideVerdict.Done, "? in the gap counts")

    val symmain = GuideFlow({ GuideSettings() })
    symmain.start("symmain")
    same(title(symmain.event(keys(Panel.Emoji))).startsWith("Emoji already"), true, "Too far")
    same(symmain.event(keys(Panel.Symbols)), GuideVerdict.Stage(1), "Sym once, then &")
    same(title(symmain.event(keys(Panel.Closed))).startsWith("Just looking"), true, "Closed without typing")

    val altLetGo = GuideFlow({ GuideSettings() })
    altLetGo.start("alt")
    altLetGo.event(altOn)
    same(title(altLetGo.event(Released(57))).startsWith("Fine, that works too"), true, "Alt let go before a key waits for one key")

    val quick = GuideFlow({ GuideSettings() })
    quick.start("holdsym")
    quick.event(Pushed(KEY_Q))
    same(title(quick.event(Released(KEY_Q))).startsWith("Too quick"), true, "A letter let go before its symbol")

    val empty = GuideFlow({ GuideSettings() })
    empty.start("symmain")
    empty.event(keys(Panel.Symbols))
    same(title(empty.event(EmptyKey)).startsWith("That key's empty"), true, "A key with nothing on it")

    val settings = GuideFlow({ GuideSettings() })
    settings.start("swipe")
    same(title(settings.event(ModifierState(Level.Off, Level.Off, Panel.Settings, false))), "Settings? Nobody asked.", "Quick settings opened")
    same(title(settings.event(keys(Panel.Symbols))), "Easy, tiger. That's later.", "Sym opened too early")
    same(title(settings.event(altOn)), "Alt is on, so the touchpad won't type.", "Alt on too early")
}

private fun theCursorHasToMove() {
    val far = GuideFlow({ GuideSettings() })
    far.start("steer")
    same(far.event(FieldChanged("on and on", 4, 4)), GuideVerdict.Wait, "A cursor change before the drag starts means nothing")
    same(far.event(CursorStarted(false)), GuideVerdict.Stage(1), "The taps start the cursor, so sliding is next")
    far.event(FieldChanged("on and on", 9, 9))
    same(far.event(FieldChanged("on and on", 2, 2)), GuideVerdict.Done, "With nothing marked, a real steer finishes it as it goes")
}

private fun steeringToAWord() {
    fun title(verdict: GuideVerdict) = (verdict as? GuideVerdict.Hint)?.let { it.title ?: it.text }.orEmpty()
    fun marked(text: String, cursor: Int = text.trimEnd().length, line: IntRange = text.indices) =
        steerTarget(text, line, cursor)?.let { text.substring(it.first, it.last + 1) }
    // The slide's own line, alone, with the cursor at its end, as GuideActivity seeds it (owner, #66).
    same(marked(STEER_LINE), "cursor", "The target is the word right before the cursor's, one word back (F)")
    same(STEER_LINE.first(), 'H', "The line keeps its H for the finale's GO TOUCH GRASS NOW")
    same(STEER_LINE.contains("steer", ignoreCase = true), false, "No steering in the line")
    // No steering anywhere the guide talks (owner, #90: "change out the steering term"): titles, lines, stages, chips, chapters.
    val slide = GUIDE_STEPS.find { it.id == "steer" }!!
    same(slide.title to slide.chapter.label, "Land in the underlined word." to "Cursor", "The slide's title and chapter")
    val said = GUIDE_STEPS.flatMap { step ->
        listOfNotNull(step.title, step.body, step.done, step.praise, step.help, step.helpTitle, step.line, step.seed) +
            step.stages.map { it.text } + GuideLesson.entries.map { blockedChip(step, it.name) }
    } + GuideChapter.entries.mapNotNull { it.roast } + GuideChapter.entries.map { it.label } + GuideScript.TEXT
    for (text in said) same(text.contains("steer", ignoreCase = true), false, "No steering in \"$text\"")
    // It follows the line's words, so a reworded line still gets a word left of the cursor.
    same(marked("Hold the second tap and drag it along"), "drag", "A reworded line: its own word, short ones passed over")
    same(marked("Hold the second tap, then slide the cursor"), "slide", "and another")
    val text = "$STEER_LINE "
    val aim = steerTarget(text, text.indices, STEER_LINE.length)!!
    same(aim.last < STEER_LINE.length, true, "Always to the left of the cursor")
    same(marked("aaaa bbbb cccc dddd", 7), "aaaa", "Never the word the cursor is in")
    same(marked("aaaa bbbb cccc dddd", 9), "aaaa", "nor the one it touches")
    same(marked("aaaa bbbb. cc dd", 16), "bbbb", "Only its letters, not the full stop")
    // Only the cursor's own line: a word on the line above is never the target.
    val two = "Long words above\nHold it to go "
    same(marked(two, two.trimEnd().length, two.indexOf("Hold") until two.length), "Hold", "The cursor's own line only")
    same(marked("aaaa bbbb\nab cd ef", 18, 10 until 18), null, "A line with no such word marks nothing, and a real move passes")

    val steer = GuideFlow({ GuideSettings() })
    steer.start("steer")
    steer.seed("x".repeat(60), 10)
    steer.aim(30..33)
    steer.event(CursorStarted(false))
    // The mode started: the slide says so, and which way the marked word is, here to the right (owner, 2026-11-16).
    same(steer.instruction, "Cursor mode on. Slide right, lift in the word.", "With a word marked, the slide says which way")
    steer.event(FieldChanged("x".repeat(60), 20, 20))
    same(title(steer.event(Moved(20f, lifted = true))).startsWith("Not there yet"), true, "Short of the word")
    steer.event(CursorStarted(false))
    steer.event(FieldChanged("x".repeat(60), 40, 40))
    same(title(steer.event(Moved(20f, lifted = true))).startsWith("Past it"), true, "Past the word")
    steer.event(CursorStarted(false))
    steer.event(FieldChanged("x".repeat(60), 32, 32))
    same(steer.event(Moved(20f, lifted = true)), GuideVerdict.Done, "In the word as the finger lifts passes")
}

/**
 * Sym and fn as Programmable key (owner, #72): a part of the guide's setup after Back, shown only while a key isn't set, and
 * skippable with a warning. Once skipped, the slides that need a key say so in one line.
 */
private fun theKeysPart() {
    val parts = SetupPart.entries
    same(parts.indexOf(SetupPart.Keys), parts.indexOf(SetupPart.Back) + 1, "The keys come right after Back")
    fun ask(keysSet: Boolean, skipped: Set<SetupPart> = emptySet()) =
        setupToAsk({ it != SetupPart.Keys || keysSet }, backDeclined = false, guide = true) { it in skipped }
    same(ask(keysSet = false), listOf(SetupPart.Keys), "It shows while Sym or fn isn't Programmable key")
    same(ask(keysSet = true), emptyList<SetupPart>(), "and hides once both are, or on a phone without them")
    same(ask(keysSet = false, skipped = setOf(SetupPart.Keys)), emptyList<SetupPart>(), "Skipped, it isn't asked again")
    same(setupToAsk({ false }, backDeclined = true, guide = true) { false }.contains(SetupPart.Back), false, "Back's Not now still counts")
    same(setupToAsk({ false }, backDeclined = false, guide = true) { false }, SetupPart.entries.toList(), "Nothing done: every part, in order, in the guide")
    same(SetupPart.Keys.skip to SetupPart.KEYS_WARN_SKIP, "Not now" to "Skip anyway", "Not now, then the warning's Skip anyway")
    same("won't" in SetupPart.KEYS_WARN_BODY && SetupPart.KEYS_WARN_TITLE.endsWith("?"), true, "The warning says what won't work")
    same(SetupPart.Keys.body.contains("Programmable key") && SetupPart.Keys.action.contains("Shortcut keys"), true, "It names the mode and the page")

    // The slides that need a key, and their one line once the part was skipped.
    val needs = GUIDE_STEPS.associate { it.id to programmableKeys(it) }.filterValues { it.isNotEmpty() }
    same(needs, mapOf("symmain" to listOf(SYM_KEY), "symgroups" to listOf(SYM_KEY), "clown" to listOf(SYM_KEY),
        "paste" to listOf(FN_KEY), "clipboard" to listOf(SYM_KEY)), "The Sym, ∞, emoji and clipboard slides need Sym; paste needs fn")
    val clipboard = GUIDE_STEPS.first { it.id == "clipboard" }
    val paste = GUIDE_STEPS.first { it.id == "paste" }
    same(keyOff(clipboard, skipped = false) { false }, null, "Not skipped: no line, setup asks for it first")
    same(keyOff(clipboard, skipped = true) { false }, SYM_KEY, "Skipped and Sym not set: the clipboard slide says so")
    same(keyOff(clipboard, skipped = true) { it == SYM_KEY }, null, "Set by hand since: no line")
    same(keyOff(paste, skipped = true) { it == SYM_KEY }, FN_KEY, "The paste slide is about fn")
    for (step in GUIDE_STEPS.filter { it.id !in needs }) same(keyOff(step, skipped = true) { false }, null, "${step.id} needs neither")
    for (key in listOf(SYM_KEY, FN_KEY)) {
        val line = keyOffLine(key)
        same(line.startsWith(key) && "skip" in line && line.length <= LINE_MOST, true, "One line that says to skip: \"$line\"")
    }
}

/**
 * On the slides with a panel, the instruction follows the page that's open (owner, M1): what to do here, how to get to the next
 * page or back, on the keys or with Enter and Backspace, and how to leave.
 */
private fun eachPageSaysWhereYouAre() {
    fun step(id: String) = GUIDE_STEPS.first { it.id == id }
    fun face(panel: Panel, position: String?, vararg shown: String) =
        KeyboardEvent.PanelFace(panel, shown.withIndex().associate { (i, s) -> "WERTYUIO"[i].toString() to s } +
            listOfNotNull(position?.let { Layout.PAGE_POSITION to it }))
    same(panelPage(Panel.Symbols, mapOf("P" to "2/9", "W" to "∞")), PanelPage(Panel.Symbols, setOf("∞"), 2, 9), "P's position is the page")
    same(panelPage(Panel.Closed, emptyMap()), null, "No page with the panel closed")
    // Enter and Backspace really page there: Enter the next page, as a swipe up, and Backspace the one before.
    same(Modifiers.panelScroll(66, Panel.Symbols) to Modifiers.panelScroll(67, Panel.Symbols), -1f to 1f, "Enter: next. Backspace: back")
    same(Modifiers.panelScroll(66, Panel.Emoji), -1f, "The same on the emoji")
    same(Modifiers.panelScroll(66, Panel.Clipboard), 0f, "On the clipboard Enter pins, so it doesn't page")
    // The guide's Recent emoji is told by its first one.
    val recent = File("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt").readText().substringAfter("GUIDE_RECENT_EMOJI = listOf(\"")
    same(recent.startsWith(GUIDE_RECENT_MARK), true, "The guide's Recent starts with $GUIDE_RECENT_MARK")

    // The & (symmain): the first group's first page.
    val amp = GuideFlow({ GuideSettings() })
    amp.start("symmain")
    same(amp.liveLine(), null, "Panel closed: the stage's own instruction")
    amp.event(face(Panel.Symbols, "1/9", "&", "%"))
    same(amp.liveLine() to amp.panelHint(), SYMMAIN_PAGES[PanelSpot.Found] to "Sym twice closes it.", "The &'s page, and the way out")
    amp.event(face(Panel.Symbols, "2/9", "√"))
    same(amp.liveLine(), "Too far. Swipe down on the keys or press Backspace.", "A later page: back")
    amp.event(face(Panel.Symbols, "1/2", "–"))
    same(amp.liveLine(), SYMMAIN_PAGES[PanelSpot.OtherGroup], "Another group")
    amp.event(face(Panel.Emoji, "1/1", GUIDE_RECENT_MARK))
    same(amp.liveLine() to amp.panelHint(), "Emoji already? Patience. Sym again closes them." to null, "The emoji")
    amp.event(face(Panel.Settings, null, "Swipe"))
    same(amp.liveLine(), "Wrong page. Alt goes back to the symbols.", "Quick settings")

    // The ∞ hunt, page by page.
    val groups = GuideFlow({ GuideSettings() })
    groups.start("symgroups")
    same(groups.instruction, "Press Sym. Then swipe up on the keys, or Enter.", "It names both ways")
    groups.event(face(Panel.Symbols, "1/9", "&", "%"))
    same(groups.liveLine() to groups.panelHint(), "Not on this page. Swipe up on the keys or press Enter." to "Sym twice closes it.",
        "Sym's first page: on to the next, and the way out under it")
    groups.event(face(Panel.Symbols, "2/9", "√", WEIRD))
    same(groups.liveLine(), "It's on this page. Find ∞ and press its key.", "The page with ∞")
    groups.event(face(Panel.Symbols, "3/9", "√"))
    same(groups.liveLine(), "Too far. Swipe down on the keys or press Backspace.", "Past it: back")
    groups.event(face(Panel.Symbols, "1/9", "&"))
    same(groups.liveLine(), "Not on this page. Swipe up on the keys or press Enter.", "Back on the first page: on again")
    groups.event(KeyboardEvent.GroupPicked(3))
    groups.event(face(Panel.Symbols, "1/2", "½"))
    same(groups.liveLine(), SYMGROUPS_PAGES[PanelSpot.OtherGroup], "Another group, by its first page")
    groups.event(face(Panel.Symbols, "2/2", "⅛"))
    same(groups.liveLine(), SYMGROUPS_PAGES[PanelSpot.OtherGroup], "and its later pages")
    groups.event(face(Panel.Emoji, "1/5", "😀"))
    same(groups.liveLine() to groups.panelHint(), "Not emoji. Sym closes them. Then press Sym once." to null, "The emoji on the ∞ slide")
    groups.event(face(Panel.Clipboard, null, "Per my last…"))
    same(groups.liveLine(), "Wrong page. Alt goes back to the symbols.", "The clipboard on the ∞ slide")
    groups.event(KeyboardEvent.PanelFace(Panel.Closed, emptyMap()))
    same(groups.liveLine() to groups.panelHint(), null to null, "Closed again: the stage's own instruction, nothing under it")

    // The clown: Recent, Smileys, another group.
    val clown = GuideFlow({ GuideSettings() })
    clown.start("clown")
    clown.event(face(Panel.Symbols, "1/9", "&"))
    same(clown.liveLine(), "That's symbols. Press Sym again for the emoji.", "Symbols on the clown slide")
    clown.event(face(Panel.Emoji, null, GUIDE_RECENT_MARK, "🍆"))
    same(clown.liveLine() to clown.panelHint(), CLOWN_PAGES[PanelSpot.Recent] to "Sym closes the emoji.", "The guide's Recent")
    clown.event(face(Panel.Emoji, "1/9", SMILEYS_HOME, "😃"))
    same(clown.liveLine(), "Not on this page. Swipe up on the keys or press Enter.", "Smileys' first page: on")
    clown.event(face(Panel.Emoji, "3/9", "🤠", CLOWN))
    same(clown.liveLine(), "It's on this page. Find 🤡 and press its key.", "The clown's page")
    clown.event(face(Panel.Emoji, "4/9", "😺"))
    same(clown.liveLine(), "Too far. Swipe down on the keys or press Backspace.", "Past the clown")
    clown.event(face(Panel.Emoji, "1/6", "👋"))
    same(clown.liveLine(), CLOWN_PAGES[PanelSpot.OtherGroup], "Another group")

    // The clipboard: the guide's clips, and Pinned.
    val clips = GuideFlow({ GuideSettings() })
    clips.start("clipboard")
    clips.event(face(Panel.Symbols, "1/9", "&"))
    same(clips.liveLine(), "Press A for the clipboard.", "Symbols on the clipboard slide: A")
    clips.event(face(Panel.Clipboard, null, "Per my last…", PRANK_CLIP.take(8) + "…"))
    same(clips.liveLine(), CLIPBOARD_PAGES[PanelSpot.Found], "The nice clip's page, told by its start")
    same(clips.panelHint(), "$CLIP_KEYS_REMINDER Sym closes the clipboard. Alt goes back to the symbols.", "The reminder and the way out")
    clips.event(face(Panel.Clipboard, "2/2", GUIDE_CLIPS.first()))
    same(clips.liveLine(), "Not on this page. Swipe up or down on the keys for the others.", "Another page of clips")
    clips.event(face(Panel.Clipboard, null, "←", "✕"))
    same(clips.liveLine(), CLIPBOARD_PAGES[PanelSpot.OtherGroup], "Pinned, with none of the guide's clips")

    // The paste slide needs no panel.
    val paste = GuideFlow({ GuideSettings() })
    paste.start("paste")
    paste.event(face(Panel.Symbols, "1/9", "&"))
    same(paste.liveLine() to paste.panelHint(), PASTE_PAGES[PanelSpot.Symbols] to null, "A panel on the paste slide: how to close it")

    // Every slide that opens a panel has a line for every place it can be.
    val places = mapOf(
        "symmain" to listOf(PanelSpot.Found, PanelSpot.Before, PanelSpot.Past, PanelSpot.OtherGroup, PanelSpot.Emoji, PanelSpot.Clipboard, PanelSpot.Settings),
        "symgroups" to listOf(PanelSpot.Found, PanelSpot.Before, PanelSpot.Past, PanelSpot.OtherGroup, PanelSpot.Emoji, PanelSpot.Clipboard, PanelSpot.Settings),
        "clown" to listOf(PanelSpot.Found, PanelSpot.Before, PanelSpot.Past, PanelSpot.OtherGroup, PanelSpot.Recent, PanelSpot.Symbols, PanelSpot.Clipboard, PanelSpot.Settings),
        "clipboard" to listOf(PanelSpot.Found, PanelSpot.Before, PanelSpot.OtherGroup, PanelSpot.Symbols, PanelSpot.Emoji, PanelSpot.Settings),
        "paste" to listOf(PanelSpot.Symbols, PanelSpot.Emoji, PanelSpot.Clipboard, PanelSpot.Settings),
    )
    for ((id, spots) in places) for (spot in spots) same(step(id).pageLines[spot] != null, true, "$id has a line for $spot")

    // Every swipe on a panel says where: on the keys, as a first-time user may swipe on the screen.
    val said = GUIDE_STEPS.flatMap { s -> s.stages.map { it.text } + s.praise + s.pageLines.values + listOfNotNull(s.leaveLine) } +
        listOf(WEIRD_HUNT, CLOWN_HUNT).flatMap { listOf(it.mock, it.hint, it.skip) } + listOf(1, 2, 4).map(::groupsPraise)
    for (line in said.filter { Regex("(?i)swip(e|ing) (up|down|left|right)|up or down|left or right|sideways").containsMatchIn(it) })
        same(line.contains("on the keys"), true, "\"$line\" says the swipe is on the keys")
    val boring = File("app/settings/guide-boring.tsx").readText().lines()
        .filter { line -> listOf("\"The Sym panel\"", "\"Emoji\"", "\"The clipboard\"").any { it in line } }
    same(boring.size, 3, "The boring guide's panel topics")
    for (line in boring) for (sentence in line.split(". ").filter { Regex("(?i)\\bswipe").containsMatchIn(it) })
        same(sentence.contains("on the keys"), true, "Boring guide: \"$sentence\" says on the keys")
}

/** Names from contacts (owner, #87): the last part, asked once, Yes or Not now. */
private fun theContactsPart() {
    same(SetupPart.entries.last(), SetupPart.Contacts, "Contact names come last in setup, after the keys")
    fun ask(answered: Boolean) = setupToAsk({ it != SetupPart.Contacts || answered }, backDeclined = false, guide = true) { false }
    same(ask(answered = false), listOf(SetupPart.Contacts), "It shows until it's answered")
    same(ask(answered = true), emptyList<SetupPart>(), "and never again once it is")
    same(SetupPart.Contacts.skip, "Not now", "Not now moves on")
    same("never leave your phone" in SetupPart.Contacts.body, true, "It says the names stay on the phone before Android asks")
    contactsAskOnlySendsToSettingsWhenAndroidStopped()
}

/**
 * A second "Don't allow" is the answer, and nothing opens after it; the press after that opens App info (#87). Played against
 * Android as the Titan 2 Elite has it: it asks until it has two noes, shows its reason after one, a question dismissed without an
 * answer counts for nothing, and its permission screen starts, empty, even when it answers by itself, so whether a question came
 * up can't be told (phone check: the third and fourth presses opened nothing).
 */
private fun contactsAskOnlySendsToSettingsWhenAndroidStopped() {
    var noes = 0
    var stopped = false
    /** One press of the row: what the user answers if Android asks (null to dismiss it), and whether App info opens. */
    fun press(answer: Boolean?, millis: Long = 2_000): Boolean {
        val asks = noes < 2
        val reasonBefore = noes == 1
        val granted = asks && answer == true
        if (asks && answer == false) noes++
        if (granted) noes = 0
        val reasonAfter = noes == 1
        // When Android doesn't ask, its no comes back at once.
        val outcome = ContactsAsk.answered(granted, stopped, reasonBefore, reasonAfter, if (asks) millis else 150)
        stopped = outcome.stopped
        return outcome.toSettings
    }
    same(press(false), false, "The first Don't allow: nothing opens")
    same(press(false), false, "The second Don't allow: nothing opens either")
    same(press(null), true, "The third press, which Android no longer asks about: App info opens")
    same(press(null), true, "and the fourth")
    // A quick second no, pressed in a hurry, is still counted from the reason: before true, after false.
    noes = 0; stopped = false
    press(false)
    same(press(false, millis = 300) to stopped, false to true, "A quick second Don't allow opens nothing, and counts")
    same(press(null), true, "then the next press opens App info")
    // Dismissed questions come up again, so nothing opens, however many.
    noes = 0; stopped = false
    same(listOf(press(null), press(null), press(null)), listOf(false, false, false), "A dismissed question asks again")
    same(press(true) to stopped, false to false, "Allowed: nothing opens, and the count starts over")
    // Two noes on a build that didn't count yet: the first press now gets Android's own no, at once, and opens App info.
    same(ContactsAsk.answered(false, stoppedBefore = false, reasonBefore = false, reasonAfter = false, millis = 150).toSettings, true,
        "Noes from before the count: Android's no at once opens App info")
    same(ContactsAsk.answered(false, stoppedBefore = false, reasonBefore = false, reasonAfter = false, millis = 2_000).toSettings, false,
        "but a first question someone dismissed opens nothing")
    // Settings > Setup's row and the guide's contacts part both go through the one activity, which decides by ContactsAsk.
    val activity = File("android/kotlin/com/vandam/ink/ContactsPermissionActivity.kt").readText()
    same("ContactsAsk.answered(granted, stoppedBefore, reasonBefore, reasonAfter, SystemClock.elapsedRealtime() - askedAt)" in activity,
        true, "The activity decides by it")
    same("preferences().edit().putBoolean(STOPPED, outcome.stopped).apply()" in activity, true, "and keeps the count")
    same("onPause" in activity, false, "The pause under Android's screen counts for nothing: it pauses even when nothing is asked")
    same(activity.indexOf("reasonBefore = should") in 0 until activity.indexOf("requestPermissions("), true, "The reason is read before asking")
    same("startActivityForResult(Intent(this, ContactsPermissionActivity::class.java), CONTACTS_REQUEST)" in
        File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText(), true, "The guide's part asks through it")
    same("ContactsPermissionActivity.start(activity)" in File("android/kotlin/com/vandam/ink/AppNativeAdapters.kt").readText(), true,
        "and so does Settings > Setup's row")
}

private fun grabbingABunch() {
    val text = "x " + GRAB_PHRASE + " and more words here"
    val aim = grabTarget(text, 0)!!
    same(text.substring(aim.first, aim.last + 1), GRAB_PHRASE, "The words to grab")
    same(grabbed(text, aim, aim.first, aim.first + 5), GRAB_SHORT, "One word is short")
    same(grabbed(text, aim, aim.first, aim.last + 1), GRAB_ALL, "All of them")
    same(grabbed(text, aim, aim.first, aim.last + 10), GRAB_ALL, "and two more is still fine")
    same(grabbed(text, aim, 0, text.length), GRAB_GREEDY, "but many more is greedy")
    val grab = GuideFlow({ GuideSettings() })
    grab.start("selectmore")
    grab.aim(aim)
    grab.event(CursorStarted(true))
    grab.event(FieldChanged(text, aim.first, aim.first + 5))
    same((grab.event(Moved(10f, lifted = true)) as GuideVerdict.Hint).text.startsWith("So close"), true, "Let go short")
    grab.event(CursorStarted(true))
    same((grab.event(FieldChanged(text, 0, text.length)) as GuideVerdict.Hint).text.startsWith("Easy. Just my heart"), true, "Greedy, as it grows")
    grab.event(FieldChanged(text, aim.first, aim.last + 1))
    same(grab.event(Moved(10f, lifted = true)), GuideVerdict.Done, "All of them as the finger lifts")
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
    same(flow.missing, null, "The paste step needs no setting: fn is always Ctrl")
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
    // A paste in a burst goes on with it, but doesn't count (bug hunt 2.1, #21: fn and V gave hundreds of words a minute).
    meter.skip(410 + 300)
    same(meter.wpm(105_000), 24, "Three hundred pasted characters add nothing to the speed")
    val fresh = WpmMeter()
    fresh.skip(500)
    same(fresh.wpm(1000), null, "and a paste starts no speed")
    val guide = java.io.File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    val count = guide.substring(guide.indexOf("private fun countWpm("), guide.indexOf("private fun showWpm("))
    same(count.contains("ctrlOn || panelOpen || GuideHooks.menuOpen?.invoke() == true"), true,
        "A key with fn or Ctrl, or one the menu or a panel takes, doesn't count")
    same(count.contains("wpm.skip(event.text.length)"), true, "and text that grows without typing is skipped")
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

/**
 * The playground's speed starts only once a real word is being typed, from a letter key or a swipe (owner): a touch, Space,
 * Backspace or a modifier never starts it. Once it runs, Space, Backspace and a word from the bar keep it going.
 */
private fun speedStartsWithAWord() {
    for (code in android.view.KeyEvent.KEYCODE_A..android.view.KeyEvent.KEYCODE_Z) same(WpmMeter.startsWord(code), true, "Letter key $code starts it")
    for (code in listOf(android.view.KeyEvent.KEYCODE_SPACE, android.view.KeyEvent.KEYCODE_DEL, android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_SHIFT_LEFT, android.view.KeyEvent.KEYCODE_ALT_LEFT, android.view.KeyEvent.KEYCODE_SYM))
        same(WpmMeter.startsWord(code), false, "Key $code doesn't start it")
    val meter = WpmMeter()
    meter.keep(1000, 1)
    same(meter.active(1000), false, "Space or Backspace before any word starts nothing")
    same(meter.wpm(1000), null, "and shows no speed")
    meter.text(4)
    same(meter.wpm(1100), null, "Text that changes by itself starts nothing either")
    meter.input(2000, 4)
    same(meter.active(2000), true, "A letter starts it")
    meter.keep(4000, 10)
    meter.keep(6500, 12)
    same(meter.active(6500 + WpmMeter.IDLE_MILLIS), true, "Once it runs, Space and Backspace keep it going")
    same(meter.wpm(6500), Math.round((12 - 4) / WpmMeter.CHARS_PER_WORD / (4500 / 60_000.0)).toInt(), "and what they type counts")
    meter.keep(6501 + 2 * WpmMeter.IDLE_MILLIS, 20)
    same(meter.active(6501 + 2 * WpmMeter.IDLE_MILLIS), false, "After the burst, Space starts nothing again")
}

/**
 * Every text on the playground stays readable as it warms (owner): once cream drops below 4.5:1 on the background, the text
 * turns ink, and it goes back to cream only a little past that, so it never flickers. Black is cream's; full orange is ink's.
 */
private fun readableWhenOrange() {
    fun warmth(heat: Float): Int {
        fun mix(shift: Int) = Math.round((0xEF804E shr shift and 0xFF) * heat * .85f) shl shift
        return (0xFF shl 24) or mix(16) or mix(8) or mix(0)
    }
    same(WpmMeter.inkOn(0xFF000000.toInt(), inkNow = false), false, "Cream on black")
    same(WpmMeter.inkOn(warmth(1f), inkNow = false), true, "Ink on the warmest orange")
    check(WpmMeter.contrast(0xFFFFF1E6.toInt(), 0xFF000000.toInt()) > 18) { "cream on black reads very well" }
    check(Math.abs(WpmMeter.contrast(0xFFFFFFFF.toInt(), 0xFF000000.toInt()) - 21.0) < 1e-6) { "white on black is 21:1" }
    // Where it turns: every heat with cream under 4.5:1 is ink, every one over it is cream.
    var turned = -1f
    for (step in 0..100) {
        val heat = step / 100f
        val contrast = WpmMeter.contrast(0xFFFFF1E6.toInt(), warmth(heat))
        same(WpmMeter.inkOn(warmth(heat), inkNow = false), contrast < WpmMeter.READABLE, "heat $heat")
        if (turned < 0 && contrast < WpmMeter.READABLE) turned = heat
    }
    check(turned > 0f && turned < 1f) { "it turns somewhere on the way to orange" }
    // Once ink, a tiny cooling doesn't flip it back.
    var heat = turned
    while (WpmMeter.contrast(0xFFFFF1E6.toInt(), warmth(heat)) < WpmMeter.READABLE + .1) heat -= .002f
    same(WpmMeter.inkOn(warmth(heat), inkNow = true), true, "just past the edge, ink stays")
    same(WpmMeter.inkOn(warmth(heat), inkNow = false), false, "though cream would read there")
    // The secondary text stays readable too, while the rest is still cream: muted on black, nearer cream as it warms.
    same(WpmMeter.quietOn(0xFF000000.toInt()), 0xFF99918A.toInt(), "Muted on black")
    for (step in 0..100) {
        val background = warmth(step / 100f)
        if (WpmMeter.inkOn(background, inkNow = false)) continue
        check(WpmMeter.contrast(WpmMeter.quietOn(background), background) >= WpmMeter.READABLE) { "secondary text reads at heat ${step / 100f}" }
    }
}

/**
 * Every way to fail gets an answer (owner, 2026-11-13; docs/guide-failures.md lists them all). A locked gesture or key is blocked
 * with words of its own for the slide, never the catch-all, and each slide's wrong moves get a chip or a hint, not silence or a pass.
 */
private fun everyWayToFailIsAnswered() {
    fun title(verdict: GuideVerdict) = (verdict as? GuideVerdict.Hint)?.let { it.title ?: it.text }.orEmpty()
    // The list covers every slide.
    val doc = java.io.File("docs/guide-failures.md").readText()
    for (step in GUIDE_STEPS.filter { it.id != "end" }) same(doc.contains("### ${step.id}:"), true, "docs/guide-failures.md lists ${step.id}")
    // Every lesson a slide locks has its own words: never the catch-all.
    for (step in GUIDE_STEPS) for (lesson in GuideLesson.entries) {
        if (step.lessons?.contains(lesson) == true) continue
        same(blockedChip(step, lesson.name) != "That's not this slide's move.", true, "${step.id} says why ${lesson.name} is blocked")
    }
    fun step(id: String) = GUIDE_STEPS.first { it.id == id }
    same(blockedChip(step("backspace"), GuideLesson.Flick.name), "That's a flick, not a press. Press Backspace.", "A flick on the Backspace slide")
    same(blockedChip(step("hold"), GuideLesson.Flick.name, emptySet()), "That's more deleting than needed. The rest stays.",
        "Deleting on, once the regrets are gone")
    same(blockedChip(step("flick"), GuideLesson.DeleteHold.name), "That's a held flick, not a flick. Flick and lift.", "A hold on the flick slide")
    same(blockedChip(step("swipe"), GuideLesson.DeleteHold.name), "That's a held flick, not this slide's move.", "A hold before its time")
    same(blockedChip(step("type"), GuideLesson.Cursor.name), "That's a Space tap, not this slide's move.", "Double or triple Space before steering")
    same(blockedChip(step("type"), GuideLesson.HoldSymbol.name), "That's a held key, not this slide's move.", "A letter held too early")
    same(blockedChip(step("type"), GuideLesson.BarHold.name), "That's a hold on the bar, not this slide's move.", "A bar word held too early")
    // Wrong moves the earlier checks don't cover.
    val menu = GuideFlow({ GuideSettings() })
    menu.start("menu")
    same(title(menu.event(FieldChanged("$MENU_LINE ", 3, 3))), "Not the box. Tap TitanKeys on the bar.", "The cursor off TitanKeys")
    val paste = GuideFlow({ GuideSettings() })
    paste.start("paste")
    same(title(paste.event(KeyboardEvent.Copied("orange words"))).startsWith("That's copy"), true, "A copy on the paste slide")
    same(finishes(paste.event(FieldChanged(PASTE_SEED + " " + PASTE_TEXT, 3, 3))), true, "and ours pasted after it still passes")
    val clown = GuideFlow({ GuideSettings() })
    clown.start("clown")
    same(clown.event(keys(Panel.Emoji)), GuideVerdict.Stage(1), "Sym twice: the emoji")
    same(clown.event(keys(Panel.Closed)).let { it is GuideVerdict.Hint && it.stage == 0 }, true, "Closed, it goes back to Sym")
    val clips = GuideFlow({ GuideSettings() })
    clips.start("clipboard")
    clips.event(keys(Panel.Symbols))
    clips.event(keys(Panel.Clipboard))
    same(clips.event(keys(Panel.Closed)).let { it is GuideVerdict.Hint && it.stage == 0 }, true, "Leaving the clips goes back to Sym")
    // Nothing a slide doesn't teach passes it.
    for (id in listOf("backspace", "flick", "steer", "alt", "symmain")) {
        val flow = GuideFlow({ GuideSettings() })
        flow.start(id)
        for (event in listOf(Pushed(KEY_A), Tapped("A"), Moved(0f, lifted = true), keys(Panel.Settings)))
            same(finishes(flow.event(event)), false, "$id doesn't pass on $event")
    }
}

/** Every passed slide shows Next, however it passed (owner, 2026-11-13: "some next are missing after completion"). */
private fun everyPassShowsNext() {
    for (step in GUIDE_STEPS) same(showsNextWhenPassed(step), step.chapter != GuideChapter.End, "${step.id} shows Next once passed")
    // The pass's pulse must never stop Next's own fade-in, which a quick pass waits for.
    val source = java.io.File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    val morph = source.substring(source.indexOf("private fun morphNext()"), source.indexOf("private fun nextPressed()"))
    same(morph.contains("next.animate().cancel()"), false, "The pass's pulse leaves Next's fade-in alone")
    same(source.contains("private val nextInView"), true, "and Next is put in full view after a pass whatever happens")
}

/** The ∞ is the owner's joke (owner, 2026-11-13): answer honestly, add ∞, "Sure you do." The fast and slow lines land as written. */
private fun theOwnersInfinityJoke() {
    val groups = GUIDE_STEPS.first { it.id == "symgroups" }
    same(groups.title, "Answer honestly.", "The ∞ slide's title")
    same(groups.title.contains(WEIRD), false, "leaves the ∞ to the hunt")
    same(groups.done, "Sure you do.", "and the punchline")
    same(groups.hunt, WEIRD_HUNT, "It's a hunt")
    same(WEIRD_HUNT.fast, "Quick. Just like you in bed.", "Found fast")
    for (seconds in listOf(HUNT_DRAIN_SECONDS, 40, 300)) same(WEIRD_HUNT.slow(seconds), "That took $seconds seconds. Still longer than you claim to last.",
        "Found after $seconds s")
    same(huntFast(0), true, "A find before the hunt's clock started is quick, so it gets its line too")
    same(huntFast(HUNT_DRAIN_SECONDS - 1) to huntFast(HUNT_DRAIN_SECONDS), true to false, "Quick until the bar has drained")
    val flow = GuideFlow({ GuideSettings() })
    flow.start("symgroups")
    same(flow.instruction.contains("Sym"), true, "The instruction still says where it is")
}

/** The clipboard slide's right clip, the last and the nice one, is the one that pulses (owner, 2026-11-13). */
private fun theRightClipPulses() {
    same(PRANK_CLIP, GUIDE_CLIPS.last(), "The nice clip is the last")
    val keyboard = java.io.File("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt").readText()
    same(keyboard.contains("guideFlashClip = clips.lastOrNull()"), true, "The last clip pulses")
    same(keyboard.contains("if (clip.text == guideFlashClip) guideFlashClip = null"), true, "until it's the one pasted")
    val panel = java.io.File("android/kotlin/com/titankeys/keyboard/SymPanel.kt").readText()
    same(panel.contains("View.ALPHA"), false, "A pulse in orange, not a blink of the whole field")
}

/** The build #2 run-through's guide fixes (owner, 2026-11-13). */
private fun runThroughFixes() {
    fun step(id: String) = GUIDE_STEPS.first { it.id == id }
    val keyboard = java.io.File("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt").readText()
    val guide = java.io.File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()

    // A tap on another bar word on the menu slide gets that slide's words, not the other slides' "Do it with the keys".
    same(blockedChip(step("menu"), GuideLesson.BarTap.name), "That's another word, not TitanKeys. Tap TitanKeys.", "A bar tap on the menu slide")
    same(blockedChip(step("steer"), GuideLesson.BarTap.name), "That's the bar, not the keys. Do it on the keys.", "A bar tap elsewhere")
    same(blockedChip(step("type"), GuideLesson.BarTap.name), "That's the bar, not typing. Type it, typo and all.", "A bar tap on the first slide")

    // The first help comes 3 s after a slide is reached by Next, Skip or Back, all of which arrive through enter(), then every 6 s.
    val moveOn = guide.substring(guide.indexOf("private fun moveOn("), guide.indexOf("/** What happens after a chapter's roast"))
    same(moveOn.contains("enter(coming)"), true, "Next, Skip and Back all arrive through enter()")
    same(guide.contains("moveOn(forward = false)"), true, "Back too")
    val enter = guide.substring(guide.indexOf("private fun enter(step: GuideStep)"), guide.indexOf("private fun showEnd("))
    same(enter.contains("fingerDown = false"), true, "A finger from the slide before never holds back this slide's help")
    same(enter.contains("main.postDelayed(showHelp, if (step.helpTitle != null) HELP_TITLE_AFTER_MILLIS else HELP_AFTER_MILLIS)"), true,
        "Each slide starts its help's wait")
    same(guide.contains("private const val HELP_AFTER_MILLIS = 3000L") && guide.contains("private const val HELP_REPEAT_MILLIS = 6000L"),
        true, "3 s, then every 6 s")
    same(guide.contains("if (event !is KeyboardEvent.FieldChanged) waitForHelp()"), true,
        "The keyboard settling the field doesn't put the help off")
    same(step("menu").helpTitle, null, "The menu slide's help is the 3 s kind")

    // A lone Space that would type is blocked where the slide has no typing, with the slide's own words.
    for (id in listOf("swipe", "steer", "selectmore")) same(step(id).lessons.orEmpty().contains(GuideLesson.Space), false, "$id locks a lone Space")
    same(step("type").lessons.orEmpty().contains(GuideLesson.Space), true, "type types spaces")
    same(step("menu").lessons.orEmpty().contains(GuideLesson.Space), false, "the menu slide's Space is blocked: it would replace TitanKeys")
    for (id in listOf("steer", "selectmore")) {
        same(step(id).lessons.orEmpty().contains(GuideLesson.Cursor), true, "$id still teaches the steering taps")
        same(blockedChip(step(id), GuideLesson.Space.name), tapMissLine(GuideMiss.Press, step(id).cue.let { (it as GuideCue.Taps).count }), "Space on $id: a press, not a tap")
    }
    same(blockedChip(step("swipe"), GuideLesson.Space.name), "That's a space, not a swipe. Swipes add their own.", "Space on swipe")
    same(keyboard.contains("keyCode == KeyEvent.KEYCODE_SPACE -> GuideLesson.Space"), true, "A pressed Space is its own lesson")
    same(keyboard.contains("keyCode == KeyEvent.KEYCODE_SPACE && GuideHooks.allows(GuideLesson.Cursor)"), false,
        "and never slips through as the cursor's")
    same(keyboard.contains("if (key == \" \" && guideBlocked(GuideLesson.Space)) return"), true, "A light tap that types a space is locked too")
    // A key press ends the touchpad's taps, so a press is never part of a steer: refusing it leaves the taps that steer alone.
    val gesture = java.io.File("android/kotlin/com/titankeys/keyboard/CursorGesture.kt").readText()
    same(gesture.contains("/** A key press or something else interrupted the touchpad at [time]. */"), true, "A press ends the taps")

    // Space on the Alt slide says what fits Space, not the letters' Alt line.
    same(blockedChip(step("alt"), GuideLesson.Space.name), "That's a space, not the symbol. Just the symbol.", "Space on the Alt slide")
    same(blockedChip(step("alt"), GuideLesson.Typing.name), "That's a letter alone, not with Alt. Hold Alt.", "A letter there keeps its line")
    same(blockedChip(step("paste"), GuideLesson.Space.name), "That's a space, not a paste. Just paste.", "Space on the paste slide")
    for (lesson in GuideLesson.entries) for (slide in GUIDE_STEPS) {
        val said = blockedChip(slide, lesson.name)
        same(lesson == GuideLesson.Typing || !said.endsWith("Hold Alt."), true, "${slide.id}: only letters get the Alt line")
    }

    // One Backspace takes the e: the cursor starts after it, before the field's trailing space.
    val backspace = step("backspace")
    val seeded = backspace.seed + " "
    same(backspace.cursorAt!!(seeded), BACKSPACE_SEED.length, "The cursor starts right after the extra e")
    val deleted = seeded.removeRange(BACKSPACE_SEED.length - 1, BACKSPACE_SEED.length)
    val flow = GuideFlow({ GuideSettings() })
    flow.start("backspace")
    same(flow.event(FieldChanged(deleted, BACKSPACE_LINE.length, BACKSPACE_LINE.length)), GuideVerdict.Done, "One Backspace passes")

    // The ∞ slide is a symbol hunt, not a riddle (owner, 2026-11-13): the hunt, its chip and its hint say ∞ outright.
    val groups = step("symgroups")
    same(groups.stages[1].text, "Find ∞ and press its key. It's further in.", "The hunt's instruction names it")
    same(groups.stages[1].text.length <= LINE_MOST, true, "and fits its line")
    val steps = java.io.File("android/kotlin/com/titankeys/keyboard/GuideSteps.kt").readText()
    val block = steps.substring(steps.indexOf("id = \"symgroups\""), steps.indexOf("id = \"clown\""))
    same(block.contains("chip(\"Not that one. Find ∞.\")"), true, "A wrong symbol says which to find")
    same(block.contains("Hint(\"Closed it. The ∞ is still in there.\""), true, "and so does closing Sym")
    same(block.contains("never ends"), false, "No riddle left on the slide")
    // The owner's two lines, exactly.
    same(WEIRD_HUNT.fast, "Quick. Just like you in bed.", "The fast line")
    same(WEIRD_HUNT.slow(40), "That took 40 seconds. Still longer than you claim to last.", "The slow line")
}

/**
 * The held delete's zones explain themselves (owner, 2026-11-13: ASDF looked like where to swipe, and the pause zone should be full
 * height). Only START is a filled patch; FASTER is an arrow, never a box; PAUSE is the touchpad's full height, as the real hold
 * counts it at any row. GuideKeyboardView and DeleteSwipe aren't compiled for these checks, so their source is read.
 */
private fun theHoldZonesExplainThemselves() {
    val view = java.io.File("android/kotlin/com/titankeys/keyboard/GuideKeyboardView.kt").readText()
    val zones = view.substring(view.indexOf("private fun drawHoldZones("), view.indexOf("private fun holdLabel("))
    for (word in listOf("\"START\"", "\"FASTER\"", "\"PAUSE\"")) check(word in zones) { "The hold zones say $word" }
    check("geometry.area(HOLD_PAUSE_X, AREA_TOP, AREA_RIGHT, AREA_BOTTOM)" in zones) { "The pause zone is the touchpad's full height" }
    check("onRow(" !in zones) { "No hold zone is one row" }
    check("Paint.Style.STROKE" in zones && "zonePaint" !in zones) { "Nothing in the hold zones is filled like a target: START is the one patch" }
    check("HOLD_TOP_X" in zones && "drawLine(" in zones) { "FASTER is an arrow out to the top-speed line" }
    val swipe = java.io.File("android/kotlin/com/titankeys/keyboard/DeleteSwipe.kt").readText()
    check("if (back < PAUSE_KEYS)" in swipe && "private fun keysBack(from: Sample, to: Sample) = (from.x - to.x) * 10" in swipe) {
        "The real pause goes by the way across only, so it counts at any height, as drawn"
    }
}

/**
 * The front page keeps all four rows of its keys above the keyboard's bar (owner, 2026-11-13: "the keyboard is cutoff by the bar").
 * GuideActivity drops the bottom row when the content and the keys don't fit, and on the phone it had none to spare: the touch
 * zone's label took 24 dp of its own over the keys, and the empty row of actions 16 dp. The label now sits in the room
 * always kept over the keys, the playground has no actions row, and the room to spare must be at least [FRONT_SLACK_DP].
 */
private fun theFrontPageKeepsAllFourRows() {
    val view = java.io.File("android/kotlin/com/titankeys/keyboard/GuideKeyboardView.kt").readText()
    check("ceil(if (lettersOnly) it.letterHeight else it.height).toInt()" in view) { "The drawing's height is its rows alone, with no room for a label" }
    val guide = java.io.File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    check("if (playground) visibility = View.GONE" in guide) { "The playground has no actions row taking room" }
    val slack = frontPageSlack()
    check(slack >= FRONT_SLACK_DP * V2.D) { "The front page has $slack px to spare over its four rows, under ${FRONT_SLACK_DP} dp" }
}

/**
 * The room the front page keeps to spare, in dp, for rounding and a status bar a little taller than measured. A comment never wraps
 * (the front page's render checks it stays one line), so this needn't hold a second line.
 */
private const val FRONT_SLACK_DP = 8

/**
 * A demo ends where a real pass does (build #8 pass): the Backspace slide's takes only the extra e, as its one Backspace does, and
 * the grab slide's selects the marked "my whole heart", as three taps, the third held and slid, do. Each end state passes its slide.
 */
private fun demosEndWhereAPassDoes() {
    val byId = GUIDE_STEPS.associateBy { it.id }

    val seed = "$BACKSPACE_SEED "
    val backspace = byId.getValue("backspace").demo!!
    same(backspace, GuideDemo.DeleteLetter, "The Backspace slide's demo takes one letter")
    // Its showing is on the keys: the extra e stays in the field until Backspace is pressed (owner, 2026-11-16: "I dont want it
    // vanished"). The flick's showing still fades its word.
    val shown = File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    same(Regex("""GuideDemo\.DeleteLetter\s*(,|->)""").findAll(shown).map { it.value }.toList(), listOf("GuideDemo.DeleteLetter ->"),
        "The field never hides the e during the showing")
    same("GuideDemo.DeleteLetter -> Unit" in shown, true, "its branch leaves the field alone")
    same("GuideDemo.DeleteLast -> demoRange(demo, text, null)" in shown, true, "while the flick's word still fades")
    val gone = demoRange(backspace, seed, null)!!
    same(seed.substring(gone.first, gone.last + 1), "e", "and that letter is the extra e, not the whole word")
    val after = seed.removeRange(gone)
    val deleting = GuideFlow({ GuideSettings() })
    deleting.start("backspace")
    deleting.seed(seed, seed.trimEnd().length)
    same(finishes(deleting.event(FieldChanged(after, gone.first, gone.first))), true, "What the demo leaves passes the slide")

    val grab = byId.getValue("selectmore").demo!!
    same(grab, GuideDemo.SelectTarget, "The grab slide's demo selects the marked words")
    // Only the slide's own line in the field, as on every slide (owner, M1: "this should also be one line only").
    val seeding = java.io.File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
        .substringAfter("private fun seedSteering(line: String) {").substringBefore("\n    }\n")
    same(seeding.contains("takeWhile") || seeding.contains("joinToString"), false, "The steering slides seed only their own line")
    val text = "$GRAB_LINE "
    val aim = grabTarget(text, 0)!!
    val picked = demoRange(grab, text, aim)!!
    same(picked, aim, "It selects exactly what the slide marks")
    same(text.substring(picked.first, picked.last + 1), "my whole heart", "which is my whole heart, not the last word")
    same(grabbed(text, aim, picked.first, picked.last + 1), GRAB_ALL, "and that is all of it, no more")
    val selecting = GuideFlow({ GuideSettings() })
    selecting.start("selectmore")
    selecting.seed(text, aim.first)
    selecting.aim(aim)
    selecting.event(FieldChanged(text, aim.first, aim.first + 2))
    same(selecting.event(CursorStarted(true)), GuideVerdict.Stage(1), "Three taps, the third held, start the slide")
    selecting.event(FieldChanged(text, picked.first, picked.last + 1))
    same(finishes(selecting.event(Moved(0f, lifted = true))), true, "and the demo's selection, let go, passes it")

    // The other demos that delete or select still take the last word.
    same(demoRange(GuideDemo.DeleteLast, seed, null)?.let { seed.substring(it.first, it.last + 1) }, "Backspacee", "A flick's demo takes the word")
    same(demoRange(GuideDemo.SelectTarget, "a word ", null), 2 until 6, "With nothing marked, the select demo takes the last word")
}

/**
 * The bar's TitanKeys is underlined in the accent the whole menu slide, with its menu open and after the pass, and nowhere else
 * (owner, 2026-11-13: "keep it highlighted the whole time"). It replaces the wiggle. GuideActivity and TitanKeysIme aren't compiled for
 * these checks, so their wiring is read from the source.
 */
private fun titanKeysIsSelected(step: GuideStep) {
    val guide = java.io.File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    val ime = java.io.File("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt").readText()
    val hooks = java.io.File("android/kotlin/com/titankeys/keyboard/GuideHooks.kt").readText()
    val strip = java.io.File("android/kotlin/com/titankeys/keyboard/Strip.kt").readText()
    // Selected in the box as the slide starts, before the keyboard reads the field, and the keyboard shows a selection it starts with.
    same(guide.contains("val selected = step.selected?.let { menuTarget(field.text.toString()) }") &&
        guide.contains("if (selected != null) field.setSelection(selected.first, selected.last + 1)"), true, "The slide starts with TitanKeys selected")
    same(ime.contains("if (editing && field.suggestions) showSelectedWord(info.initialSelStart, info.initialSelEnd)"), true,
        "A field that starts with a word selected shows it on the bar")
    // The bar shows it as any selected word, and a tap opens its menu as a hold does, behind the slide's one unlock.
    same(strip.contains("private val selectedView = item({ act(Action.OpenSelected) }, { act(Action.OpenSelected); true })"), true,
        "A tap or a hold on the selected word opens its menu")
    // Any selected word is underlined on the bar until its menu opens, the guide's TitanKeys as any other (owner, 2026-11-13): drawn
    // only while the selected word itself shows, which an open menu takes the place of.
    same(strip.contains("drawSelected(canvas)") && strip.contains("if (view.visibility != VISIBLE || view.text.isEmpty()) return") &&
        strip.contains("val chosen = selected.takeIf { showSuggestions && confirm == null"), true, "The selected word is underlined until its menu opens")
    same(ime.contains("Strip.Action.OpenSelected -> if (!guideBlocked(GuideLesson.BarHold)) selectedWord?.let { openMenu(HeldWord(it, \"\")) }"), true,
        "and the slide's BarHold lets it")
    same(step.lessons.orEmpty().contains(GuideLesson.BarHold), true, "which the menu slide unlocks")
    // The selection holds: touches on the box change nothing, and any other selection or cursor goes back to TitanKeys.
    same(guide.contains("field.holdsSelection = step.selected != null"), true, "The box takes no touches on the menu slide")
    same(guide.contains("step.selected != null && !menuSelected(event.text, event.start, event.end)"), true,
        "and any other selection goes back, after the pass too")
    // A stray key in the menu keeps it open where typing is locked, as on this slide.
    same(ime.contains("menuOpen && inGuide && !guideAllows(GuideLesson.Typing)"), true, "Another key leaves the menu open")
    // No underline anywhere: not on the bar, and not in the box.
    for ((name, text) in listOf("GuideActivity" to guide, "TitanKeysIme" to ime, "GuideHooks" to hooks))
        for (gone in listOf("barLit", "guideLit", "barWord(", "barWordRequests", "guideBarWord", "shownGuideWord"))
            same(text.contains(gone), false, "$name has no $gone: the bar's guide-only light and word are gone")
    same(Regex(""""menu" -> menuTarget""").containsMatchIn(guide), false, "The box doesn't underline TitanKeys")
}

/**
 * The guide for people new to the phone (#90): every miss says what happened, "that's an X, not a Y"; the Space taps light the
 * drawing as they land, 1, 2, held; a swipe far from its word is told; and the raw log notes each slide and miss.
 */
private fun theGuideForNewcomers() {
    // Each miss on a Space-tap slide says what it was, on one line.
    val tapMisses = GuideMiss.entries - setOf(GuideMiss.FarSwipe, GuideMiss.Blocked, GuideMiss.Other)
    for (taps in listOf(2, 3)) for (miss in tapMisses) {
        val line = tapMissLine(miss, taps, landed = if (miss == GuideMiss.Extra) taps + 1 else taps - 1)
        same(line.startsWith("That's ") && ", not " in line, true, "$miss on $taps taps: \"$line\" says what it was")
        same(line.length <= LINE_MOST, true, "\"$line\" is one line")
    }
    same(tapMissLine(GuideMiss.Lift, 2), "That's a lift, not a hold. Keep the second down.", "A lift on the cursor slide")
    same(tapMissLine(GuideMiss.Single, 3), "That's one tap, not three. Hold the third one.", "One tap on the select slide")
    same(GuideMiss.entries.map { it.log }.toSet().size, GuideMiss.entries.size, "Each miss has its own name in the log")
    same(GuideMiss.entries.all { it.log.none(Char::isWhitespace) }, true, "and the names have no spaces, for the log's label")
    same(GuideLog.missLabel("steer", GuideMiss.Beside), "steer beside", "The log's label: the slide and the miss")
    same(GuideLog.missLabel("type", GuideMiss.Blocked, "swipe"), "type blocked swipe", "with what was blocked")
    same(SWIPE_COACH.length <= LINE_MOST && SWIPE_COACH.startsWith("That's "), true, "The far swipe's line")
    // The touches the Space-tap slides lock say what they were, against a tap.
    for (id in listOf("steer", "selectmore")) {
        val step = GUIDE_STEPS.first { it.id == id }
        val taps = (step.cue as GuideCue.Taps).count
        same(blockedChip(step, GuideLesson.Space.name), tapMissLine(GuideMiss.Press, taps), "$id: Space pressed is a press, not a tap")
        same(blockedChip(step, GuideLesson.Typing.name), tapMissLine(GuideMiss.Typing, taps), "$id: a letter is typing, not a tap")
        same(blockedChip(step, GuideLesson.Swipe.name), tapMissLine(GuideMiss.Swipe, taps), "$id: a swipe is a swipe, not a tap")
    }
    val steer = GuideFlow({ GuideSettings() })
    steer.start("steer")
    same((steer.event(DoubleTapped(2)) as GuideVerdict.Hint).miss, GuideMiss.Lift, "A lift is logged as one")
    steer.event(CursorStarted(false))
    same((steer.event(Moved(1f, lifted = true)) as GuideVerdict.Hint).let { it.text to it.miss },
        tapMissLine(GuideMiss.NoSlide, 2) to GuideMiss.NoSlide, "Held, then lifted before the slide")

    // The tap meter: each tap lights its box as it comes down, the hold lights the last, at the keyboard's own gap.
    val meter = TapMeter(2)
    same(meter.landed to meter.held, 0 to false, "Nothing yet")
    same(meter.down(1_000), false, "The first tap is never slow")
    same(meter.landed, 1, "The first tap lights 1 as it lands")
    meter.up(1_080, tap = true)
    same(meter.down(1_200), false, "The second, 120 ms on, is quick enough")
    same(meter.landed, 2, "and lights 2")
    meter.hold(1_270)
    same(meter.visibleTaps(1_270), 0, "Held, the taps aren't counted as pending: the held pose lights every box instead")
    // The cursor's own trace starts as the slide begins: the same finger, still held, not a new tap (owner, 2026-11-21).
    same(meter.down(1_400) to meter.held, false to true, "The slide's trace keeps the hold")
    // The mode starting moves the slide to its next line; the held taps keep every box lit and the thumb sliding (owner,
    // 2026-11-22: "all of it shd be highlighted and the finger shd swipe when the gestures r triggered").
    same(meter.stageChanged() to meter.held, true to true, "The slide's next line keeps the held boxes")
    same(GuideTapDrawing.liveAt(meter, 1_500, drag = true, left = true, animate = true)?.let { it.held to (it.slide > 0f) },
        true to true, "and the live thumb slides")
    val select = TapMeter(3)
    select.hold(10_000)
    same(select.stageChanged() to GuideTapDrawing.liveAt(select, 10_100, drag = true, left = false, animate = true)?.let {
        List(3, it::lit) to (it.slide < 0f) }, true to (listOf(true, true, true) to true), "Select mode too: three lit, sliding right")
    val idle = TapMeter(2)
    idle.down(20_000)
    same(idle.stageChanged() to idle.landed, false to 0, "Not held, a new stage counts from nothing, as before")
    same("if (meter?.stageChanged() == true) keys.taps(meter, flow.tapSlideLeft) else keys.taps(null)" in
        File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText(), true, "The slide's stage change uses it")
    meter.up(2_000, tap = false)
    same(meter.landed to meter.held, 0 to false, "A lift after the hold starts again")
    meter.down(3_000)
    meter.up(3_080, tap = true)
    same(TapMeter.TAP_GAP_MILLIS, 200L, "The meter counts a next tap as the keyboard does")
    same(meter.down(3_080 + TapMeter.TAP_GAP_MILLIS + 100), true, "A next tap 300 ms on is too slow, and says so (owner, 2026-11-16)")
    same(meter.landed, 1, "and lights only 1, as the keyboard didn't count it")
    meter.up(3_480, tap = true)
    same(meter.down(3_480 + TapMeter.SLOW_MILLIS + 1), false, "Much later, it's a new start, not a slow tap")
    meter.up(5_000, tap = false)
    same(meter.landed, 0, "A touch that isn't a tap starts again")
    val three = TapMeter(3)
    for (t in 0 until 4) {
        three.down(t * 150L)
        three.up(t * 150L + 60, tap = true)
    }
    same(three.landed <= 3, true, "Never more than the slide's taps")
    same(tapMissLine(GuideMiss.Slow, 2), "That's too slow, not quick taps. Tap faster.", "The slow line (F: draft)")
    // The keyboard's own gap, which the meter follows.
    same("private const val MAX_DRAG_GAP_MILLIS = 200L" in File("android/kotlin/com/titankeys/keyboard/CursorGesture.kt").readText(), true,
        "The keyboard takes a next tap up to 200 ms after the last")
    val activityText = File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    same("if (tapMeter?.down(now) == true && flow.stage == 0 && !passed && !settingUp)" in activityText, true, "The slide says it")

    // The drawing: the last tap and its hold are one box, "2 · HOLD" (owner, 2026-11-16).
    same(List(2) { GuideTapDrawing.label(it, 2) }, listOf("1", "2 · HOLD"), "The cursor slide's boxes")
    same(List(3) { GuideTapDrawing.label(it, 3) }, listOf("1", "2", "3 · HOLD"), "The select slide's")
    same(GuideTapDrawing.Pose(2, 2).let { List(2, it::lit) }, listOf(true, true), "The last tap uses the same full colour")
    // Held, every box stays lit, in the showing and for real alike (owner, 2026-11-21).
    same(GuideTapDrawing.Pose(2, 2, held = true).let { List(2, it::lit) }, listOf(true, true), "Held: every box stays lit")
    same(GuideTapDrawing.Pose(3, held = true).let { List(3, it::lit) }, listOf(true, true, true), "on the select slide too")
    same(GuideTapDrawing.Pose(2, 1).let { listOf(it.lit(0), it.lit(1)) }, listOf(true, false), "Pose: 1 lit")
    // Its showing, at the keyboard's own speed: taps 90 ms down, 110 ms apart, inside the keyboard's 200 ms.
    same(GuideTapDrawing.DEMO_GAP_MILLIS < TapMeter.KEYBOARD_GAP_MILLIS && GuideTapDrawing.DEMO_TAP_MILLIS <= 130, true,
        "The showing's taps are as quick as the keyboard needs")
    same(GuideTapDrawing.demoAt(0, 2, drag = true).let { it.landed to it.press }, 1 to 1f, "The showing starts with 1 down")
    same(GuideTapDrawing.demoAt(150, 2, drag = true).let { it.landed to it.press }, 1 to 0f, "lifts")
    same(GuideTapDrawing.demoAt(210, 2, drag = true).let { it.landed to it.lit(1) }, 2 to true, "the second lands at full colour")
    same(GuideTapDrawing.demoAt(200 + 130, 2, drag = true).held, true, "and fills as the keyboard registers the hold")
    // The slide follows the hold at once, all the boxes lit, then the thumb lifts and they go out (owner, 2026-11-21: "the
    // finger should slide after 2. hold in the demo"). It used to finish the slide and stay down, still, through the rest.
    val slideFrom = 200 + GuideTapDrawing.DEMO_HOLD_LIGHT_MILLIS + GuideTapDrawing.DEMO_HOLD_MILLIS
    same(GuideTapDrawing.DEMO_HOLD_MILLIS <= 200L, true, "The thumb slides soon after the hold registers")
    same(GuideTapDrawing.demoAt(slideFrom + 450, 2, drag = true).let { it.slide > 0f && List(2, it::lit) == listOf(true, true) }, true,
        "then the slide to the left, every box lit")
    same(GuideTapDrawing.demoAt(slideFrom + GuideTapDrawing.DEMO_SLIDE_MILLIS - 1, 2, drag = true).slide > .99f, true, "all the way")
    same(GuideTapDrawing.demoAt(slideFrom + GuideTapDrawing.DEMO_SLIDE_MILLIS + 100, 2, drag = true).let { it.held || it.landed > 0 }, false,
        "then it lifts, and the boxes go out")
    // The field's caret moves on the same clock as the thumb.
    same(GuideTapDrawing.demoSlid(slideFrom - 1, 2, drag = true), 0f, "The caret waits for the slide")
    same(GuideTapDrawing.demoSlid(slideFrom + 450, 2, drag = true), GuideTapDrawing.demoAt(slideFrom + 450, 2, drag = true).slide,
        "and moves with the thumb")
    same(GuideTapDrawing.demoSlid(slideFrom + GuideTapDrawing.DEMO_SLIDE_MILLIS + 100, 2, drag = true), 1f, "and stays there after the lift")
    same("cue is GuideCue.Taps -> GuideTapDrawing.demoMillis(cue.count, cue.drag)" in File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText(),
        true, "The field's demo lasts one showing of the thumb")
    same(GuideTapDrawing.demoAt(GuideTapDrawing.demoMillis(2, true), 2, drag = true).landed, 1, "and again")
    same(GuideTapDrawing.demoAt(410, 3, drag = true).let { it.landed to it.held }, 3 to false, "The select slide's third tap lands")
    same("GuideTapDrawing.demoAt(now - cueStart, taps.count, taps.drag)" in File("android/kotlin/com/titankeys/keyboard/GuideKeyboardView.kt").readText(),
        true, "The keys show it at that speed, not slowed like the ghost finger")

    for (count in 2..3) {
        val cues = TapMeter(count)
        cues.down(1000)
        cues.up(1080, tap = true)
        same(cues.visibleTaps(1280), 1, "A tap still counts at the last eligible instant")
        same(GuideTapDrawing.liveAt(cues, 1281, true, true, true), null, "The pending cue expires without another touch")
        same(cues.down(1350), true, "An expired cue still coaches a slow next tap")
        cues.hold(1420)
        val pose = GuideTapDrawing.liveAt(cues, 1720, drag = true, left = true, animate = true)!!
        same(List(count, pose::lit), List(count) { true }, "Every box is lit while held, on each cursor lesson")
        same(pose.slide in 0.1f..0.9f, true, "The live thumb is already sliding left during the hold")
        same(GuideTapDrawing.liveAt(cues, 1720, true, false, true)?.slide, -pose.slide, "A rightward goal points right")
        same(GuideTapDrawing.liveAt(cues, 1420, true, true, false)?.slide, 1f, "Reduce motion shows the final position immediately")
        cues.up(1850, tap = false)
        same(GuideTapDrawing.liveAt(cues, 1850, true, true, true), null, "Lift resets the live slide cue")
    }

    // The mode started: which mode, and which way to slide, toward the marked words (owner, 2026-11-16).
    val grab = GuideFlow({ GuideSettings() })
    grab.start("selectmore")
    grab.seed("x".repeat(60), 20)
    grab.aim(21..40)
    grab.event(CursorStarted(true))
    same(grab.instruction, "Select mode on. Slide right, lift at the end.", "Select mode, the words to the right")
    val back = GuideFlow({ GuideSettings() })
    back.start("steer")
    back.seed("x".repeat(60), 50)
    back.aim(10..14)
    back.event(CursorStarted(false))
    same(back.instruction, "Cursor mode on. Slide left, lift in the word.", "Cursor mode, the word to the left")
    for (line in listOf(modeStartLine(true, true), modeStartLine(false, false))) same(line.length <= LINE_MOST, true, "\"$line\" is one line")

    // Passed, the slide's patch stays (owner, 2026-11-16: "Keep the touch zone after its finished").
    val keysView = File("android/kotlin/com/titankeys/keyboard/GuideKeyboardView.kt").readText()
    same("if (cue !is GuideCue.Taps && zoneCue !is GuideCue.Taps) return" in keysView &&
        "if (cue !is GuideCue.Flick && zoneCue !is GuideCue.Flick) return" in keysView, true, "The patches draw from the passed slide's cue too")
    same("keys.zoneCue = flow.step.cue" in activityText && "keys.zoneCue = if (passed && !playground) flow.step.cue else null" in activityText,
        true, "set as the slide passes, cleared on the next")
    // The ghost finger no longer draws the taps: the drawing does, from the first try.
    val view = File("android/kotlin/com/titankeys/keyboard/GuideKeyboardView.kt").readText()
    same("if (cue is GuideCue.Taps) return" in view && "drawTapSteps(canvas, now)" in view, true, "The thumb replaces the ghost finger")

    // A swipe far from its word: measured against the word's path, in key widths, with the cut-off from recorded swipes.
    val path = wordPath("swipe")
    same(path.size, 5, "The word's path goes through each letter")
    same(wordPath("too").size, 2, "A letter twice in a row is one point")
    same(swipeDistance(path, "swipe")!! < .01f, true, "A swipe right on the path is 0 keys off")
    val lower = path.map { floatArrayOf(it[0], it[1] + 1f / KeyGeometry.ROWS) }
    same(swipeDistance(lower, "swipe")!!.let { it > .99f && it < 1.01f }, true, "A row lower all the way is 1 key off")
    same(SWIPE_FAR_KEYS, 1.2f, "The cut-off, set from the recorded swipes")
    same(swipeFar(1.1f) to swipeFar(1.3f), false to true, "Far only past it")
    same(swipeDistance(listOf(floatArrayOf(.5f, .5f)), "swipe"), null, "No measure without a trail")

    // Key wording names one action at a time (#90): Sym, let go, then A, and the line keeps its W.
    same(CLIP_LEAD.first() to ("let go" in CLIP_LEAD), 'W' to true, "The paste slide's lead: one action each, still a W")
    same("let go" in GUIDE_STEPS.first { it.id == "clipboard" }.praise, true, "and the clipboard slide's praise")

    // The raw log notes each slide and each miss, through the keyboard's mark, which only TitanKeys Debug's log keeps.
    val guide = File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    for (call in listOf("logMark(GuideLog.SLIDE, step.id)", "RawLog.MARK_ACTION", "GuideLog.missLabel(flow.step.id, verdict.miss ?: GuideMiss.Other)"))
        same(call in guide, true, "The guide logs: $call")
}

/**
 * The front page, the playground and the guide decide setup by one definition (review, M2): Sym and fn and contact names are
 * asked only in the guide's own setup, before the guide. A 2.1.0 user who updates with contacts unanswered and Sym and fn not set
 * has setup done for the front page and the playground alike, so the playground never shows a setup slide that ✕ loops back to.
 */
private fun setupHasOneDefinition() {
    val guideOnly = SetupPart.entries.filter { it.guideOnly }
    same(guideOnly, listOf(SetupPart.Keys, SetupPart.Contacts), "Sym and fn, and contact names, are the guide's own")
    // The updating user: the five parts done, the guide's two not.
    val updated = { part: SetupPart -> !part.guideOnly }
    same(setupToAsk(updated, backDeclined = false) { false }, emptyList<SetupPart>(), "The front page and the playground ask nothing")
    same(setupToAsk(updated, backDeclined = false, guide = true) { false }, guideOnly, "The guide's own setup asks the two, before the guide")
    // Every phone state: the playground's list is empty exactly when the front page sends you to the playground, as both read
    // the same list, and the guide only ever adds its own two.
    for (bits in 0 until (1 shl SetupPart.entries.size)) for (declined in listOf(false, true)) {
        val done = { part: SetupPart -> bits and (1 shl part.ordinal) != 0 }
        val playground = setupToAsk(done, declined) { false }
        val guide = setupToAsk(done, declined, guide = true) { false }
        same(playground.none { it.guideOnly }, true, "The playground never asks a guide-only part")
        same(guide.filterNot { it.guideOnly }, playground, "The guide asks what the playground does, then its own two")
    }
    val activity = File("android/kotlin/com/titankeys/keyboard/GuideActivity.kt").readText()
    same(activity.contains("PhoneSetup.missing(this, guide = !playground)") && !activity.contains("PhoneSetup.missing(this)"), true,
        "The playground reads the front page's list, the guide its own")
}
