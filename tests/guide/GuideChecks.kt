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
    "type" to listOf(Fixed("Gte", "Get"), FieldChanged("$TYPE_LINE ", 32, 32)),
    "swipe" to listOf(Recognised(listOf("swipe")), Recognised(listOf("per")), Recognised(listOf("word"))),
    "menu" to listOf(WordMenu("bar"), MenuPicked("Save"), MenuPicked("Unsave")),
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
    same(MenuKeys.button("I"), MenuKeys.SAVE, "I saves")
    same(MenuKeys.button("O"), MenuKeys.FORGET, "O forgets")
    same(MenuKeys.button("P"), MenuKeys.BACK, "P goes back")
    same(listOf("K", "L").map(MenuKeys::button), listOf(MenuKeys.SAVE, MenuKeys.FORGET), "The second row senses them too")
    for (key in listOf("Q", "W", "E", "R", "T", "Y", "U", "A", "H", "J", "Z", "M", Layout.SPACE))
        same(MenuKeys.button(key), -1, "$key picks nothing: the word sits over the left")
    same(MenuKeys.WORD_COLUMNS + 3 + MenuKeys.AFTER_COLUMNS, 10, "The word, three buttons and P's column fill the ten columns")
    // The bar's buttons, in pixels, on the phone's width: each has room, and sits centred over its own key, as the Sym panel's
    // labels do. The word ends left of Save.
    val width = 1080f
    val side = Layout.KEY_SIDE_DP * 2.625f
    // On the default keys and on a calibration that moved both edges: the buttons follow the real keys, from one place.
    for (alignment in listOf(KeyAlignment.DEFAULT, KeyAlignment(.05f, .93f))) {
        for ((column, key) in listOf(MenuKeys.SAVE to 'I', MenuKeys.FORGET to 'O', MenuKeys.BACK to 'P')) {
            val (left, right) = MenuKeys.slot(width, column, alignment)
            check(right - left > 70f) { "The button over $key has room: ${right - left} px" }
            val keyCentre = KeyGeometry(width, alignment).x(Layout.KEY_X[key - 'A'])
            check(kotlin.math.abs((left + right) / 2 - keyCentre) < .5f) { "The button over $key is centred on its key: ${(left + right) / 2} vs $keyCentre" }
        }
        check(MenuKeys.wordRight(width, 21f, alignment) < MenuKeys.slot(width, MenuKeys.SAVE, alignment).first) { "The word ends left of Save" }
    }
    val moved = MenuKeys.slot(width, MenuKeys.SAVE, KeyAlignment(.05f, .93f))
    check(moved != MenuKeys.slot(width, MenuKeys.SAVE, KeyAlignment.DEFAULT)) { "Calibration moves the buttons" }
    val menu = java.io.File("android/kotlin/com/atlas/keyboard/MenuKeys.kt").readText()
    check("KEY_SIDE_DP" !in menu && "KEY_SIDE_DP" !in java.io.File("android/kotlin/com/atlas/keyboard/Strip.kt").readText()
        .substringAfter("private fun drawMenu(").substringBefore("private fun drawIcon(")) { "The menu reads its columns from the keys alone" }
    System.getenv("MENU_PNG")?.let { drawMenuBar(it, width, side) }
    theMenuLadder()
}

/**
 * The word menu's ladder (owner, 2026-11-12): one simple ladder for every word, dictionary words and single letters too, NOT
 * SAVED → SAVED → FAVOURITE. I moves the word one step up and O one step down, never two, and Unfavourite always lands on SAVED.
 * Only a word the keyboard learned from typing can be forgotten, and never a single letter. Every state × every press is walked
 * below, through [MenuKeys.choices], the one function the keyboard and the guide use, and printed as the table for the owner.
 */
private fun theMenuLadder() {
    val (new, saved, favourite) = Triple(MenuKeys.Step.New, MenuKeys.Step.Saved, MenuKeys.Step.Favourite)
    fun state(saved: Boolean = false, favourite: Boolean = false, blocked: Boolean = false, learned: Boolean = false,
              oneLetter: Boolean = false, builtIn: Boolean = false, savable: Boolean = true) =
        MenuKeys.state(saved, favourite, blocked, learned, oneLetter, builtIn, savable)
    // Where each kind of word opens (owner, 2026-11-12): a dictionary word counts as saved; a word the store can't hold has no Save.
    same(state().step, new, "A word nobody saved: not saved")
    same(state(learned = true), MenuKeys.State(new, true), "A word learned from typing isn't saved, and O forgets it")
    same(state(saved = true).step, saved, "A word you saved")
    same(state(builtIn = true).step, saved, "A dictionary word counts as saved")
    same(state(builtIn = true, learned = true).learned, false, "and is never 'learned': O takes it out of the dictionary instead")
    same(state(saved = true, favourite = true).step, favourite, "A favourite")
    same(state(saved = true, favourite = true, blocked = true).step, new, "A forgotten word is not saved, and no favourite")
    same(state(builtIn = true, blocked = true).step, new, "A dictionary word you forgot is not saved: Save lets it back in")
    same(state(learned = true, oneLetter = true).learned, false, "A single letter is never forgotten")
    for (word in listOf("hello", "don't", "Titankeys", "it’s")) same(MenuKeys.savable(word), true, "$word can be saved")
    for (word in listOf("x", "😀", ":)", "#tag", "2nd", "a-b", "")) same(MenuKeys.savable(word), false, "\"$word\" can't be saved")
    // Every kind of word, every state, every press.
    val (save, fav, spent) = Triple(MenuKeys.Button.Save, MenuKeys.Button.Favourite, MenuKeys.Button.Favourited)
    val (unfav, unsave, forget) = Triple(MenuKeys.Button.Unfavourite, MenuKeys.Button.Unsave, MenuKeys.Button.Forget)
    val table = StringBuilder("| Word | State | I shows | I → | O shows | O → |\n|---|---|---|---|---|---|\n")
    val kinds = listOf(
        "a word you never saved (\"Titankeys\")" to MenuKeys.State(new, false),
        "learned from typing (a typo it picked up)" to MenuKeys.State(new, true),
        "a dictionary word (\"and\")" to MenuKeys.State(new, false, builtIn = true),
        "one letter in the dictionary (\"I\")" to MenuKeys.State(new, false, builtIn = true, oneLetter = true),
        "a symbol or emoji (\"😀\")" to MenuKeys.State(new, false, savable = false),
    )
    fun name(state: MenuKeys.State?) = state?.let { "${it.step}" + if (it.step == new && it.learned) " (learned)" else "" } ?: "closes: Forgot · Undo"
    for ((kind, base) in kinds) for (step in MenuKeys.Step.entries) {
        // A dictionary word is never at New unless forgotten, nor is a symbol ever saved: skip the states it can't be in.
        if (base.builtIn && step == new || !base.savable && step != new) continue
        val at = base.copy(step = step, learned = base.learned && step == new)
        val choices = MenuKeys.choices(at)
        table.append("| $kind | $step | ${choices.i ?: "–"} | ${if (choices.i == null) "–" else name(choices.iNext)} | ${choices.o ?: "–"} | " +
            "${if (choices.o == null) "–" else name(choices.oNext)} |\n")
        if (choices.i != null) same(MenuKeys.press(at, choices.i!!), choices.iNext, "$kind at $step: I goes where choices says")
        if (choices.o != null) same(MenuKeys.press(at, choices.o!!), choices.oNext, "$kind at $step: O goes where choices says")
        when (step) {
            new -> {
                same(choices.i, if (base.savable) save else null, "$kind: not saved, I saves if the store can hold it")
                same(choices.o, if (at.learned) forget else null, "$kind: not saved, O forgets only a learned word")
            }
            saved -> {
                same(choices.i to choices.iNext?.step, fav to favourite, "$kind: saved, I favourites")
                when {
                    base.builtIn && base.oneLetter -> same(choices.o, null, "$kind: a single letter is never taken out")
                    base.builtIn -> same(choices.o to choices.oNext, forget to null, "$kind: O takes it out of the dictionary, with Undo")
                    else -> same(choices.o to choices.oNext?.step, unsave to new, "$kind: saved, O unsaves")
                }
            }
            favourite -> {
                same(choices.i to choices.iNext, spent to at, "$kind: a favourite's I is spent")
                same(choices.o to choices.oNext?.step, unfav to saved, "$kind: Unfavourite always lands on saved")
            }
            else -> Unit
        }
        for (button in listOfNotNull(choices.i, choices.o)) if (button != spent) {
            val next = MenuKeys.press(at, button)?.step ?: continue
            check(kotlin.math.abs(next.ordinal - step.ordinal) == 1) { "$kind: $button moves one step, never two" }
        }
    }
    // A symbol's menu has nothing to do, so it never opens.
    same(MenuKeys.choices(state(savable = false)).let { it.i to it.o }, null to null, "A symbol has no buttons, so no menu")
    // A walk under one finger: up the whole ladder and down again, each press one step.
    var walk = state(learned = true)
    val seen = mutableListOf<String>()
    for (key in "IIIOO") {
        val choices = MenuKeys.choices(walk)
        val button = (if (key == 'I') choices.i else choices.o)!!
        walk = MenuKeys.press(walk, button)!!
        seen += "$key:$button→${walk.step}"
    }
    same(seen, listOf("I:Save→Saved", "I:Favourite→Favourite", "I:Favourited→Favourite", "O:Unfavourite→Saved", "O:Unsave→New"), "The walk")
    println("The word menu, every state and every press:\n$table")
    for (button in MenuKeys.Button.entries) check(MenuKeys.move(button) in -3..2) { "$button moves ${MenuKeys.move(button)}" }
    // The keyboard and the guide press through the same ladder, and a menu with nothing to do never opens.
    val ime = java.io.File("android/kotlin/com/atlas/keyboard/AtlasIme.kt").readText()
    check("MenuKeys.press(menu.state, button)" in ime) { "The keyboard and the guide press through the ladder" }
    check("builtIn = typing.builtInWord(word)" in ime && "savable = MenuKeys.savable(word)" in ime) { "The menu knows dictionary words and symbols" }
    check("if (menu.first == null && menu.second == null) return false" in ime) { "A menu with nothing to do never opens" }
    theMenuHover()
    theMenuWord()
    theMenuMotion()
}

/**
 * A resting finger hovers a button from its own key or the one below it: I or K, O or L. P and Backspace pick Back but show
 * nothing: Back has no pill. The hover stays while a key is down, so after a press its pill shows that button's new action.
 */
private fun theMenuHover() {
    same(listOf("I", "K", "O", "L", "P", Layout.BACKSPACE).map(MenuKeys::hovered),
        listOf(MenuKeys.SAVE, MenuKeys.SAVE, MenuKeys.FORGET, MenuKeys.FORGET, MenuKeys.BACK, MenuKeys.BACK), "The hover's keys")
    for (key in listOf("U", "J", "M", "Q", Layout.ENTER, "")) same(MenuKeys.hovered(key), -1, "$key hovers nothing")
    for (step in MenuKeys.Step.entries) {
        val at = MenuKeys.State(step, true)
        same(MenuKeys.pills(MenuKeys.BACK, at), false, "Back has no pill at $step")
        same(MenuKeys.pills(MenuKeys.SAVE, at), true, "I has one at $step")
        same(MenuKeys.pills(MenuKeys.FORGET, at), MenuKeys.second(step, true) != null, "O has one where it has a button at $step")
    }
    same(MenuKeys.pills(MenuKeys.SAVE, MenuKeys.State(MenuKeys.Step.New, false, savable = false)), false, "A symbol has no Save to hover")
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
    val hover = SteadyKey(Layout::keyAt)
    hover.touch(.75f, .1f)
    same(MenuKeys.hovered(hover.key), MenuKeys.SAVE, "A finger on I hovers I's button")
    hover.keyDown(37)
    hover.lift(1000)
    same(hover.expire(1000 + 10 * SteadyKey.LIFT_MILLIS), false, "Pressing I keeps the hover, however long the press")
    same(hover.keyUp(37, 2000), true, "I let go")
    hover.touch(.75f, .1f)
    same(hover.expire(2000 + 2 * SteadyKey.LIFT_MILLIS), false, "The finger still on the pad keeps it")
    hover.lift(3000)
    same(hover.expire(3000 + SteadyKey.LIFT_MILLIS), true, "A lifted finger ends the hover")
    val ime = java.io.File("android/kotlin/com/atlas/keyboard/AtlasIme.kt").readText()
    val strip = java.io.File("android/kotlin/com/atlas/keyboard/Strip.kt").readText()
    check("barHover.keyDown(keyCode)" in ime && "barHover.keyUp(keyCode" in ime) { "The bar's hover hears the keys go down and up" }
    check("if (column == MenuKeys.SAVE || column == MenuKeys.FORGET) column else -1" in ime) { "Only I's and O's buttons take the mark" }
    check("if (hovered == pillShown && action != pillAction) pillSnap = true" in strip) { "A new action under the finger takes its pill in place" }
    check("if (keyCode == menuKey && event.repeatCount > 0) return true" in ime) { "A key held after its pick never types" }
    check("if (info.packageName == packageName) field = field.copy(learning = false)" in ime) { "The guide's field learns nothing" }
    val forgetBody = ime.substringAfter("private fun forgetWord(").substringBefore("\n    }\n")
    check("selected = null" !in forgetBody && "Strip.Notice.Forgot(held.word)" in forgetBody && "menuWord = null" in forgetBody) {
        "Forget closes the menu on Forgot · Undo and keeps the selection"
    }
    check("val pending = pendingWords[key]" in ime && "pendingWords[key] = true" in ime && "pendingWords[key] = false" in ime) {
        "Save and Unsave are seen at once"
    }
    for ((name, text) in listOf("AtlasIme" to ime, "Strip" to strip)) check("playMenuHint" !in text && "hints_shown" !in text) { "$name has no hint" }
}

/** The word sits centred left of the buttons, placed once, with no quotes; the pill's name never runs into its icon. */
private fun theMenuWord() {
    val width = 1080f
    val icon = 22 * 2.625f
    val (left, right) = MenuKeys.pill(width, MenuKeys.FORGET, 120f, icon, 28f, 18f, 17f)
    val (oLeft, oRight) = MenuKeys.slot(width, MenuKeys.FORGET)
    check(right > (oLeft + oRight) / 2 + icon / 2 && right < oRight + 17f) { "The pill ends just past O's icon" }
    check(left < MenuKeys.slot(width, MenuKeys.SAVE).second) { "and covers I's button to its left" }
    check(kotlin.math.abs(right - left - (28f + 120f + 18f + icon + 17f)) < .01f) { "It holds the name and the icon" }
    // While the pill grows, its name stays by the icon: the owner saw "Fav♡urite" when it rode the pill's moving left edge.
    val iconLeft = right - 17f - icon
    for (grown in listOf(0f, .25f, .5f, .75f, 1f)) {
        val labelLeft = MenuKeys.pillLabel(right, 120f, icon, 18f, 17f)
        check(labelLeft + 120f <= iconLeft - 18f + .01f) { "The name ends before the icon, $grown grown" }
    }
    same(MenuKeys.wordCentre(10f, 50f), 30f, "The word is centred in its room")
    val shown = MenuKeys.shorten("incomprehensibilities") { it.length <= 12 }
    check(shown.length <= 12 && shown.startsWith("inco") && shown.endsWith("ties") && "…" in shown) { "Shortened in the middle: $shown" }
    val strip = java.io.File("android/kotlin/com/atlas/keyboard/Strip.kt").readText()
    val draw = strip.substringAfter("private fun drawMenu(canvas: Canvas)").substringBefore("private fun wordBox(")
    check("if (menuWordCentre.isNaN()) menuWordCentre = MenuKeys.wordCentre(wordLeft, wordRight)" in draw) { "The word is placed once" }
    check("strip_menu_word" !in draw) { "The word has no quotes" }
    check("menuWordText.color = look.ink" in draw && "menuWordText.color = look.accent" !in strip) { "The word never changes colour" }
    check("isFakeBoldText" !in draw && "boldTypeface" in draw) { "The pill's name is Ink's medium weight" }
}

/**
 * Word menu v4's animations (design v2, section 4; owner, 2026-11-12): Save's sparkles, Unsave's dim and falling sparkles, and the
 * heart breaking with a crack, shards and a dip, with Favourite's burst beside them.
 */
private fun theV4Motions(word: MenuMotion.Word) {
    val favourite = MenuMotion.frame(MenuKeys.Motion.Favourite, 200, word)
    check(favourite.sprites.size in 6..8 && favourite.sprites.map { it.size }.toSet().size >= 2) { "6 to 8 hearts of different sizes" }
    check(MenuMotion.frame(MenuKeys.Motion.Favourite, 90, word).wordScale > 1.03f) { "The word pops as they burst" }
    check(MenuMotion.peak(MenuKeys.Motion.Favourite) == 0L) { "Favourite's haptic hits on the burst" }
    check(MenuMotion.frame(MenuKeys.Motion.Forget, 300, word).wordAlpha < 1f) { "Forget dissolves the word" }
    check(MenuMotion.frame(MenuKeys.Motion.Favourite, 200, MenuMotion.Word(120f, 220f, 27f, MenuMotion.Word.seed("hello")))
        != favourite) { "Another word gets another layout" }
    // The heart breaking (owner: "slightly bad"; design v2, section 4): a zigzag crack draws down it in 120 ms, the halves tilt
    // apart with 4 tiny shards, fall and fade; the word dips 2 dp and comes back, with no shake. The haptic lands on the crack.
    same(MenuMotion.peak(MenuKeys.Motion.Unfavourite), MenuMotion.CRACK_MILLIS, "The haptic lands as the crack is done")
    check(MenuMotion.CRACK_MILLIS == 120L) { "The crack takes 120 ms" }
    val cracks = listOf(10L, 60L, 110L).map { millis -> MenuMotion.frame(MenuKeys.Motion.Unfavourite, millis, word).sprites.single { it.shape == MenuMotion.Shape.Crack }.amount }
    check(cracks.zipWithNext().all { (a, b) -> b > a } && cracks.last() > .85f) { "The crack draws down: $cracks" }
    val broken = MenuMotion.frame(MenuKeys.Motion.Unfavourite, 260, word).sprites
    same(broken.count { it.shape == MenuMotion.Shape.Shard }, 4, "Four tiny shards")
    check(broken.any { it.shape == MenuMotion.Shape.HeartLeft } && broken.any { it.shape == MenuMotion.Shape.HeartRight }) { "Two halves" }
    val falling = listOf(260L, 450L, 620L).map { millis -> MenuMotion.frame(MenuKeys.Motion.Unfavourite, millis, word).sprites.first { it.shape == MenuMotion.Shape.HeartLeft }.y }
    check(falling.zipWithNext().all { (a, b) -> b > a }) { "The halves fall: $falling" }
    val dips = (0 until MenuMotion.millis(MenuKeys.Motion.Unfavourite) step 10).map { MenuMotion.frame(MenuKeys.Motion.Unfavourite, it, word) }
    check(dips.maxOf { it.wordDrop } in 1.9f..2.01f && dips.all { it.wordShift == 0f } && dips.last().wordDrop < .1f) { "The word dips 2 dp and back, no shake" }
    // Save (design v2, section 4): 8 to 10 bold sparkles burst out in a ring, a little bigger at first, then drift up; the word pops.
    val save = MenuMotion.frame(MenuKeys.Motion.Save, 150, word)
    check(save.sprites.size in 8..10 && save.sprites.all { it.shape == MenuMotion.Shape.Sparkle && it.behind }) { "8 to 10 sparkles: ${save.sprites.size}" }
    check(save.sprites.any { it.y > word.middle + 3f } && save.sprites.any { it.y < word.middle - 3f }) { "in a ring, above and below the word" }
    val early = MenuMotion.frame(MenuKeys.Motion.Save, 40, word).sprites.map { it.size }.average()
    val late = MenuMotion.frame(MenuKeys.Motion.Save, 400, word).sprites.map { it.size }.average()
    check(early > late) { "bigger at first: $early, then $late" }
    val rising = listOf(300L, 500L).map { millis -> MenuMotion.frame(MenuKeys.Motion.Save, millis, word).sprites.map { it.y }.average() }
    check(rising[0] > rising[1]) { "then drifting up: $rising" }
    same(MenuMotion.millis(MenuKeys.Motion.Save), MenuMotion.millis(MenuKeys.Motion.Favourite), "Save takes as long as Favourite")
    check(MenuMotion.frame(MenuKeys.Motion.Save, 90, word).wordScale > 1.03f) { "The word pops as the sparkles burst" }
    same(MenuMotion.peak(MenuKeys.Motion.Save), 0L, "Save's haptic hits on the burst")
    // Unsave (owner, 2026-11-12: "what does unsave have to do with sparkles?"): a bookmark breaks over the word as the heart does,
    // more quietly: 2 shards, and no dip of the word. The haptic lands as its crack is done.
    val unsaveMotion = MenuKeys.Motion.Unsave
    same(MenuMotion.peak(unsaveMotion), MenuMotion.CRACK_MILLIS, "Unsave's haptic lands on its crack")
    val cracking = MenuMotion.frame(unsaveMotion, 60L, word).sprites
    check(cracking.any { it.shape == MenuMotion.Shape.Bookmark } && cracking.any { it.shape == MenuMotion.Shape.Crack }) { "A bookmark cracks: $cracking" }
    val torn = MenuMotion.frame(unsaveMotion, 260L, word).sprites
    check(torn.any { it.shape == MenuMotion.Shape.BookmarkLeft } && torn.any { it.shape == MenuMotion.Shape.BookmarkRight }) { "into two halves" }
    same(torn.count { it.shape == MenuMotion.Shape.Shard }, 2, "with 2 shards, quieter than the heart's 4")
    check(torn.none { it.shape == MenuMotion.Shape.Sparkle }) { "No sparkles" }
    val unsaving = ArrayList<MenuMotion.Frame>()
    for (millis in 0L until MenuMotion.millis(unsaveMotion) step 10L) unsaving += MenuMotion.frame(unsaveMotion, millis, word)
    check(unsaving.all { it.wordDrop == 0f && it.wordShift == 0f && it.wordAlpha == 1f }) { "The word stays still" }
}

/**
 * The animations (owner, 2026-11-12): quick, inside the bar, seeded by the word so a word always gets the same layout, roughly
 * symmetric on both sides, all orange, and none with motion off. No icon snaps to another.
 */
private fun theMenuMotion() {
    val steps = MenuKeys.Step.entries
    same(steps.map { MenuKeys.motion(null, it) }, listOf(null, MenuKeys.Motion.SavedEcho, MenuKeys.Motion.Echo),
        "Opening echoes softly on a saved word or a favourite, each in its own mark")
    same(MenuKeys.motion(MenuKeys.Step.New, MenuKeys.Step.Saved), MenuKeys.Motion.Save, "Save")
    same(MenuKeys.motion(MenuKeys.Step.Saved, MenuKeys.Step.Favourite), MenuKeys.Motion.Favourite, "Favourite")
    same(MenuKeys.motion(MenuKeys.Step.Favourite, MenuKeys.Step.Saved), MenuKeys.Motion.Unfavourite, "Unfavourite")
    same(MenuKeys.motion(MenuKeys.Step.Saved, MenuKeys.Step.New), MenuKeys.Motion.Unsave, "Unsave")
    same(MenuKeys.motion(MenuKeys.Step.Favourite, MenuKeys.Step.Favourite), null, "The spent heart plays nothing")
    val word = MenuMotion.Word(120f, 220f, 27f, MenuMotion.Word.seed("Titankeys"))
    for (motion in MenuKeys.Motion.entries) {
        val total = MenuMotion.millis(motion)
        check(total in 300L..700L) { "$motion is quick: $total ms" }
        check(MenuMotion.peak(motion) in 0L until total) { "$motion peaks while it plays" }
        same(MenuMotion.frame(motion, total, word).sprites, emptyList<MenuMotion.Sprite>(), "$motion is gone at its end")
        same(MenuMotion.frame(motion, total / 3, word), MenuMotion.frame(motion, total / 3, word), "$motion is the same each time")
        var left = false
        var right = false
        for (millis in 0 until total step 20) {
            val frame = MenuMotion.frame(motion, millis, word)
            for (sprite in frame.sprites) {
                check(sprite.y - sprite.size / 2 >= -.01f && sprite.y + sprite.size / 2 <= MenuMotion.BAR + .01f) { "$motion stays in the bar: $sprite" }
                check(sprite.alpha in 0f..1f) { "$motion's strength: $sprite" }
                if (sprite.x < word.centre) left = true else right = true
            }
            check(frame.wordScale in 1f..1.07f && kotlin.math.abs(frame.wordShift) <= 1.5f && kotlin.math.abs(frame.wordDrop) <= 2.01f) {
                "$motion only pops, dips or shakes the word a little"
            }
        }
        check(left && right) { "$motion plays on both sides of the word" }
    }
    theV4Motions(word)
    // No icon snaps: every change is a few frames long, and the heart fills from the bottom as it warms.
    val heart = MenuKeys.Button.Favourite
    val full = MenuKeys.Button.Favourited
    for ((from, to) in listOf(MenuKeys.Button.Save to heart, heart to full, full to heart, MenuKeys.Button.Unfavourite to MenuKeys.Button.Unsave,
        MenuKeys.Button.Unsave to null, null to MenuKeys.Button.Forget)) {
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
    // The ambient status, at rest: a faint heart now and then from alternating sides of a favourite, a faint sparkle twinkling now
    // and then by a saved word, nothing by a word that isn't saved; still where motion is off.
    same(MenuMotion.ambient(MenuKeys.Step.New, 500, word, false), emptyList<MenuMotion.Sprite>(), "Nothing by a word that isn't saved")
    val sides = (0 until 4).map { cycle ->
        val heart = MenuMotion.ambient(MenuKeys.Step.Favourite, (cycle + 1) * MenuMotion.AMBIENT_HEART_EVERY + 400, word, false).single()
        check(heart.alpha <= MenuMotion.AMBIENT_ALPHA + .001f && heart.shape == MenuMotion.Shape.Heart) { "A faint heart: $heart" }
        heart.x > word.centre
    }
    same(sides, listOf(sides[0], !sides[0], sides[0], !sides[0]), "The hearts come from alternating sides")
    same(MenuMotion.ambient(MenuKeys.Step.Favourite, MenuMotion.AMBIENT_HEART_EVERY + MenuMotion.AMBIENT_HEART_MILLIS + 100, word, false),
        emptyList<MenuMotion.Sprite>(), "and rest between")
    for (step in listOf(MenuKeys.Step.Saved, MenuKeys.Step.Favourite)) for (millis in 0L until 2_000L step 20)
        same(MenuMotion.ambient(step, millis, word, false), emptyList<MenuMotion.Sprite>(), "Nothing right after the menu comes to rest at $step")
    check(MenuMotion.AMBIENT_HEART_EVERY in 2000L..3000L && MenuMotion.AMBIENT_TWINKLE_EVERY in 3000L..4000L) { "Every 2.5 s and every 3.5 s" }
    val twinkle = MenuMotion.ambient(MenuKeys.Step.Saved, MenuMotion.AMBIENT_TWINKLE_EVERY + 350, word, false).single()
    check(twinkle.shape == MenuMotion.Shape.Sparkle && twinkle.alpha in .3f..0.36f && twinkle.size > 8f) { "A faint sparkle twinkles: $twinkle" }
    val twinkles = (0 until 4).map { cycle -> MenuMotion.ambient(MenuKeys.Step.Saved, (cycle + 1) * MenuMotion.AMBIENT_TWINKLE_EVERY + 350, word, false).single().x > word.centre }
    same(twinkles, listOf(twinkles[0], !twinkles[0], twinkles[0], !twinkles[0]), "at alternating ends")
    same(MenuMotion.ambient(MenuKeys.Step.Saved, MenuMotion.AMBIENT_TWINKLE_EVERY + 2000, word, false), emptyList<MenuMotion.Sprite>(), "and rests between")
    same(MenuMotion.ambient(MenuKeys.Step.Saved, 300, word, true).single().let { it.shape to it.turn }, MenuMotion.Shape.Sparkle to 0f,
        "With motion off a faint sparkle sits still by a saved word")
    same(MenuMotion.ambient(MenuKeys.Step.Favourite, 300, word, true).size, 1, "and a faint heart sits still by a favourite")
    same(MenuMotion.ambientWait(MenuKeys.Step.Saved, 2000, false), MenuMotion.AMBIENT_TWINKLE_EVERY - 2000, "It waits out the first cycle")
    same(MenuMotion.ambientWait(MenuKeys.Step.Saved, MenuMotion.AMBIENT_TWINKLE_EVERY + 2000, false), MenuMotion.AMBIENT_TWINKLE_EVERY - 2000,
        "It redraws only when it moves")
    val strip = java.io.File("android/kotlin/com/atlas/keyboard/Strip.kt").readText()
    // Places (owner, 2026-11-12): I, O and Back on the centres of their columns, a key apart; Back no longer dodges the corner.
    check("if (centre > most) centre = most" !in strip) { "Back no longer dodges the rounded corner" }
    check("barHover.press(pressed)" in java.io.File("android/kotlin/com/atlas/keyboard/AtlasIme.kt").readText()) { "A picking key keeps the hover" }
    check("if (pillShown >= 0) ambientStart = -1L" in strip) { "The ambient status waits while a button is hovered" }
    check("MenuKeys.motion(if (opened) null else shownStep, menu.step)?.takeIf { GuideMotion.enabled }" in strip) { "Motion off, no animations" }
    check("drawable.setTint(look.accent)" in strip.substringAfter("private fun drawSprite(")) { "Every particle is orange" }
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
        val (left, end) = MenuKeys.slot(width, MenuKeys.SAVE + index)
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
            // Save: a bookmark.
            0 -> g.drawPolygon(intArrayOf(x + 12, x + icon - 12, x + icon - 12, centre, x + 12), intArrayOf(y + 4, y + 4, y + icon - 4, y + icon - 18, y + icon - 4), 5)
            // Forget: a bin.
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

fun main() {
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
    wrongEventsDoNotFinish()
    chapterOne()
    scenarios()
    everyWayToFailIsAnswered()
    everyPassShowsNext()
    theOwnersInfinityJoke()
    theRightClipPulses()
    runThroughFixes()
    theCursorHasToMove()
    steeringToAWord()
    grabbingABunch()
    settingsThatStepsNeed()
    goingBack()
    resuming()
    theTrailFollowsTouchSize()
    demonstrationsTypePunctuation()
    // Drawings of the slides in the new layout, to look at without a phone. See SlideRenders.kt.
    System.getenv("SLIDES_PNG")?.let { drawSlides(it, NEW_LAYOUT + GuideChapter.End) }
    System.getenv("FRONT_PNG")?.let { drawFrontPage(it) }
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
    same(GROUPS_LINE.startsWith("S") && GROUPS_LINE.endsWith("how long I last in bed with your mother: ∞"), true, "Its line sets up the joke, and starts with S for the finale")
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
        same(line.length <= LINE_MOST, true, "The hunt's \"$line\" is one line")
    same(CLOWN_HUNT.skip.contains("skip") && WEIRD_HUNT.skip.contains("skip"), true, "Each hunt says it's ok to skip, at the end")
    same(WEIRD_HUNT.fast, "Quick. Just like you in bed.", "A quick find of ∞ gets the owner's chip")
    same(WEIRD_HUNT.slow(40), "That took 40 seconds. Still longer than you claim to last.", "and a slow one the owner's line")
    same(CLOWN_HUNT.slow(40).contains("40"), true, "A slow clown says how long it took")
    same(huntFast(HUNT_DRAIN_SECONDS - 1) && !huntFast(HUNT_DRAIN_SECONDS), true, "Fast is before the mockery starts")
    same(GUIDE_STEPS.mapNotNull { it.hunt }, listOf(WEIRD_HUNT, CLOWN_HUNT), "The weird one and the clown are hunts")
    same(HUNT_DRAIN_SECONDS < HUNT_HINT_SECONDS && HUNT_HINT_SECONDS < HUNT_SKIP_SECONDS, true, "Mockery, then a hint, then leave to skip")

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
    same(clips.step.done, "Gotcha. You believed it.", "and the slide owns up to the prank")
    same(GuideHooks.swapClip(PRANK_CLIP), PRANK_PASTE, "The nice clip pastes the prank in the guide")
    same(GuideHooks.swapClip(GUIDE_CLIPS.first()), GUIDE_CLIPS.first(), "Every other clip pastes as it is")
    same(OLD_GUIDE_CLIPS.containsAll(GUIDE_CLIPS), true, "The guide clears its own clips from the clipboard")

    val menu = GuideFlow({ GuideSettings() })
    menu.start("menu")
    same(menu.event(MenuPicked("Save")), GuideVerdict.Wait, "A pick before the menu opens means nothing")
    same(menu.event(WordMenu("to")), GuideVerdict.Stage(1), "The menu opens, so Save is next")
    same(menu.labels to menu.cue, MENU_SAVE to GuideCue.Spot(listOf("I"), rounds = 1), "I lights once: Save")
    same(MENU_SAVE["K"] to MENU_SAVE[Layout.BACKSPACE], "Save" to "Back", "K works as I, and Backspace as P, and they say so")
    same(menu.event(MenuPicked("Save")), GuideVerdict.Stage(2), "Saved, so Unsave is next, as the real menu's O")
    same(menu.labels to menu.cue, MENU_UNSAVE to GuideCue.Spot(listOf("O"), rounds = 1), "then O: Unsave")
    same(MENU_UNSAVE["L"], "Unsave", "L works as O")
    same(menu.event(MenuPicked("Favourite")) is GuideVerdict.Hint, true, "I again makes a favourite, and the slide says so")
    same(menu.event(MenuPicked("Unfavourite")) is GuideVerdict.Hint, true, "O takes the favourite back first")
    same(menu.event(MenuPicked("Unsave")), GuideVerdict.Done, "Unsave on the saved word finishes it")
    val early = GuideFlow({ GuideSettings() })
    early.start("menu")
    early.event(WordMenu("to"))
    same(early.event(MenuPicked("Unsave")), GuideVerdict.Done, "A word saved already opens saved: Unsave straight away counts")
    for (stage in GUIDE_STEPS.first { it.id == "menu" }.stages) same(stage.text.contains("O, and O again"), false, "No O, and O again")
    same(early.event(MenuPicked("Close")).let { it is GuideVerdict.Hint || it == GuideVerdict.Wait }, true, "Closed, it waits for a hold")
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
}

/** The finale: the summary lands, the letters light and gather into the phrase, then the logo grows from TK. */
private fun theFinale() {
    val lines = SUMMARY_LINES
    val measure = GuideFinale.Measure { text, size, _ -> text.length * size * .55f }
    fun texts(at: Long) = GuideFinale.frame(at, lines, 1076f, 120f, 760f, measure, 14f * 1076f / 688.6f).filterIsInstance<GuideFinale.Text>()
    same(texts(GuideFinale.LINE_IN / 2).size, 2, "At the start, only the first line is coming in")
    val stacked = texts(GuideFinale.summaryEnd(lines))
    same(stacked.count { it.alpha >= .99f }, lines.size * 2, "Then every line, whole")
    val gathered = texts(GuideFinale.gatherStart(lines) + GuideFinale.GATHER + 10)
    same(gathered.map { it.text }.joinToString(""), ACROSTIC.replace(" ", ""), "Then only the first letters, which spell the phrase")
    same(gathered.zipWithNext().all { (a, b) -> a.x < b.x }, true, "in order, left to right")
    same(gathered.map { Math.round(it.y) }.distinct().size, 1, "on one line")
    // The logo itself becomes the name (owner): the real mark comes back whole, then walks apart into its T and its K, which land
    // on the name's T and K, and the name's own type takes over as they land.
    val mark = GuideFinale.frame(GuideFinale.growStart(lines) - 10, lines, 1076f, 120f, 760f, measure, 14f * 1076f / 688.6f)
    same(mark.filterIsInstance<GuideFinale.Mark>().map { it.part }, listOf(GuideFinale.MarkPart.Whole), "The logo's mark comes back whole")
    same(mark.none { it is GuideFinale.Text && it.text in listOf("T", "K") }, true, "with no stand-in letters beside it")
    val walking = GuideFinale.frame(GuideFinale.growStart(lines) + GuideFinale.GROW / 2, lines, 1076f, 120f, 760f, measure, 14f * 1076f / 688.6f)
    val parts = walking.filterIsInstance<GuideFinale.Mark>().filter { it.alpha > 0f }
    same(parts.map { it.part }.sorted(), listOf(GuideFinale.MarkPart.T, GuideFinale.MarkPart.K), "Halfway, the mark is its own T and K")
    check(parts.first { it.part == GuideFinale.MarkPart.K }.x > parts.first { it.part == GuideFinale.MarkPart.T }.x) { "the K walks right of the T" }
    val logo = GuideFinale.frame(GuideFinale.total(lines), lines, 1076f, 120f, 760f, measure, 14f * 1076f / 688.6f)
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
        same(step.demo != null || step.bar != null, true, "${step.id} must show its gesture by itself when nobody acts")
    same(byId.getValue("clipboard").demo, GuideDemo.Insert(listOf(Layout.SYM, Layout.CLIPBOARD_KEY), ""), "The clipboard slide lights Sym, then A")
    same(byId.getValue("holdsym").demo, GuideDemo.HoldLetter(HOLD_SYMBOL), "The hold slide shows the key with ? held down")
    same(byId.getValue("alt").demo, GuideDemo.AltLetter(ALT_SYMBOL), "The Alt slide shows Alt, then the key with *")
    same(byId.getValue("paste").demo, GuideDemo.Insert(listOf(Layout.FN, "V", Layout.FN, "V"), ""), "The paste demo is only fn and V")
    same(byId.getValue("type").bar, GuideBar.Fix, "Autocorrect shows the fix swapping in on the bar")
    same(byId.getValue("menu").bar, GuideBar.Menu, "The word menu shows a ghost hold on the bar, and the menu opening")
}

/** Every slide: one-line words, and each slide unlocks only what it teaches. */
private fun chapterOne() {
    for (step in GUIDE_STEPS.filter { it.chapter != GuideChapter.End }) {
        for (line in listOf(step.title, step.done)) same(line.length <= TITLE_MOST, true, "\"$line\" is one line")
        for (line in step.stages.map { it.text } + step.praise + listOfNotNull(step.help, step.helpTitle))
            same(line.length <= LINE_MOST, true, "\"$line\" is one line")
    }
    val keys = setOf(GuideLesson.Typing, GuideLesson.Backspace)
    val typing = GUIDE_STEPS.filter { it.chapter == GuideChapter.Typing }
    // Swiping types its own spaces, so its slide locks a lone Space (owner, 2026-11-13).
    same(typing.map { it.lessons }, listOf(keys + GuideLesson.Space, setOf(GuideLesson.Swipe) + keys,
        setOf(GuideLesson.BarHold, GuideLesson.Space) + keys), "Each slide unlocks what it teaches; none teaches picking from the bar (v5.2)")
    same(GUIDE_STEPS.filter { it.chapter == GuideChapter.Delete }.map { it.lessons }, listOf(setOf(GuideLesson.Backspace),
        setOf(GuideLesson.Flick), setOf(GuideLesson.Flick, GuideLesson.DeleteHold)), "Delete unlocks Backspace, the flick, then its hold")
    same(GUIDE_STEPS.filter { it.chapter == GuideChapter.Steering }.map { it.lessons }, List(2) { setOf(GuideLesson.Cursor) },
        "Steering unlocks only the cursor gestures")
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
        same(said.contains("blocked", ignoreCase = true) || said.startsWith("Blocked"), true, "\"$said\" says it's blocked")
        same(said.length <= LINE_MOST, true, "The lock's chip \"$said\" is one line")
    }
}

/** The chapters rebuilt in the new layout so far. */
private val NEW_LAYOUT = setOf(GuideChapter.Typing, GuideChapter.Delete, GuideChapter.Steering, GuideChapter.Symbols,
    GuideChapter.Clipboard)
/** Titles shrink to fit their one line now (v5.2). */
private const val TITLE_MOST = 38
private const val LINE_MOST = 50

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
    same(menu.event(Chosen("to")) is GuideVerdict.Hint, true, "A touch on a word is not a hold, and the slide says so")
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
    swipe.event(Pushed(KEY_A))
    same(title(swipe.event(Pushed(62))), "That's typing. Swipe this one.", "A word typed key by key on the swipe slide")
    // Whatever is swiped becomes the word (the keyboard puts the line's word first), so any swipe counts (owner, v5.2).
    same(swipe.event(Recognised(listOf("tp")), "to"), GuideVerdict.Wait, "Any swipe counts, however bad: no bar to pick from")
    same(swipe.liveLine(), swipesLeft(2), "and the count goes down")
    same(title(swipe.event(Tapped("A"))), "That's a tap. Slide, don't lift.", "A tap, with tap keys on")

    val flick = GuideFlow({ GuideSettings() })
    flick.start("flick")
    same(flick.event(DeletedWord(held = true)), GuideVerdict.Wait, "A held word isn't the flick")
    same(flick.event(DeletedWord(held = false)), GuideVerdict.Done, "One quick flick finishes it")
    same(blockedChip(flick.step, GuideLesson.DeleteHold.name), "Held delete is blocked here. Flick and lift.", "The hold, blocked")
    same(blockedChip(flick.step, GuideLesson.Backspace.name), "Backspace is blocked. Flick it.", "Backspace on the flick slide")
    same(blockedChip(GUIDE_STEPS.first { it.id == "steer" }, GuideLesson.Typing.name), "Typing's blocked here. Typing's done. Keep up.",
        "Letters on Steering")

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
    same(title(steer.event(DoubleTapped(2))).startsWith("You lifted"), true, "Two taps that lift")
    same(title(steer.event(TwoTapsAbove)).startsWith("You lifted"), true, "Two taps above Space that lift")
    same(title(steer.event(CursorStarted(true))).startsWith("One tap too many"), true, "Three taps")
    same(steer.event(CursorStarted(false)), GuideVerdict.Stage(1), "Two taps, the second held")
    same(steer.event(Moved(0f, lifted = true)).let { it is GuideVerdict.Hint && it.stage == 0 }, true, "Held, then let go")

    val more = GuideFlow({ GuideSettings() })
    more.start("selectmore")
    same(title(more.event(CursorStarted(false))).startsWith("That's two"), true, "Two taps, the second held, steer")
    same(more.event(FieldChanged("a b", 0, 1)), GuideVerdict.Wait, "A held third tap picks a word first: no scolding yet")
    same(title(more.event(Moved(0f, true))).startsWith("One word"), true, "Three taps that lift pick one word")
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
    // A line of words, one character wide each, so a character's place is its index.
    val text = "aa bb cc dd eee fff ggg hhh iii. jjj"
    val word = steerTarget(text, 0 until text.length, 0f) { it.toFloat() }
    same(word?.let { text.substring(it.first, it.last + 1) }, "eee", "Three words or more from the cursor's column, three letters or more")
    same(steerTarget(text, 0 until text.length, 33f) { it.toFloat() }?.let { text.substring(it.first, it.last + 1) }, "ggg",
        "counted either way from the column")
    same(steerTarget("ab. cd ef gh iii.", 0 until 17, 0f) { it.toFloat() }, 13..15, "only its letters, not the full stop")

    val steer = GuideFlow({ GuideSettings() })
    steer.start("steer")
    steer.seed("x".repeat(60), 10)
    steer.aim(30..33)
    steer.event(CursorStarted(false))
    same(steer.instruction, "Now slide. Lift when the cursor's in it.", "With a word marked, the slide asks for it")
    steer.event(FieldChanged("x".repeat(60), 20, 20))
    same(title(steer.event(Moved(20f, lifted = true))).startsWith("Not there yet"), true, "Short of the word")
    steer.event(CursorStarted(false))
    steer.event(FieldChanged("x".repeat(60), 40, 40))
    same(title(steer.event(Moved(20f, lifted = true))).startsWith("Past it"), true, "Past the word")
    steer.event(CursorStarted(false))
    steer.event(FieldChanged("x".repeat(60), 32, 32))
    same(steer.event(Moved(20f, lifted = true)), GuideVerdict.Done, "In the word as the finger lifts passes")
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
        same(blockedChip(step, lesson.name) != "That's blocked here. That's later.", true, "${step.id} says why ${lesson.name} is blocked")
    }
    fun step(id: String) = GUIDE_STEPS.first { it.id == id }
    same(blockedChip(step("backspace"), GuideLesson.Flick.name), "Flicks are blocked here. Backspace this one.", "A flick on the Backspace slide")
    same(blockedChip(step("hold"), GuideLesson.Flick.name, emptySet()), "Blocked. The regrets are gone, the rest stays.",
        "Deleting on, once the regrets are gone")
    same(blockedChip(step("flick"), GuideLesson.DeleteHold.name), "Held delete is blocked here. Flick and lift.", "A hold on the flick slide")
    same(blockedChip(step("swipe"), GuideLesson.DeleteHold.name), "Held delete is blocked here. That's later.", "A hold before its time")
    same(blockedChip(step("type"), GuideLesson.Cursor.name), "Space taps are blocked here. That's later.", "Double or triple Space before steering")
    same(blockedChip(step("type"), GuideLesson.HoldSymbol.name), "Holding for symbols is blocked here. That's later.", "A letter held too early")
    same(blockedChip(step("type"), GuideLesson.BarHold.name), "Holding bar words is blocked here. That's later.", "A bar word held too early")
    // Wrong moves the earlier checks don't cover.
    val menu = GuideFlow({ GuideSettings() })
    menu.start("menu")
    same(title(menu.event(Chosen("the"))), "That typed it. Hold it, don't tap.", "A tap on a bar word")
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
    val source = java.io.File("android/kotlin/com/atlas/keyboard/GuideActivity.kt").readText()
    val morph = source.substring(source.indexOf("private fun morphNext()"), source.indexOf("private fun nextPressed()"))
    same(morph.contains("next.animate().cancel()"), false, "The pass's pulse leaves Next's fade-in alone")
    same(source.contains("private val nextInView"), true, "and Next is put in full view after a pass whatever happens")
}

/** The ∞ is the owner's joke (owner, 2026-11-13): the title never names it, and the found-fast and took-long lines land as written. */
private fun theOwnersInfinityJoke() {
    val groups = GUIDE_STEPS.first { it.id == "symgroups" }
    same(groups.title, "Add how long you last in bed.", "The ∞ slide's title")
    same(groups.title.contains(WEIRD), false, "never names the symbol")
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
    val keyboard = java.io.File("android/kotlin/com/atlas/keyboard/AtlasIme.kt").readText()
    same(keyboard.contains("guideFlashClip = clips.lastOrNull()"), true, "The last clip pulses")
    same(keyboard.contains("if (clip.text == guideFlashClip) guideFlashClip = null"), true, "until it's the one pasted")
    val panel = java.io.File("android/kotlin/com/atlas/keyboard/SymPanel.kt").readText()
    same(panel.contains("View.ALPHA"), false, "A pulse in orange, not a blink of the whole field")
}

/** The build #2 run-through's guide fixes (owner, 2026-11-13). */
private fun runThroughFixes() {
    fun step(id: String) = GUIDE_STEPS.first { it.id == id }
    val keyboard = java.io.File("android/kotlin/com/atlas/keyboard/AtlasIme.kt").readText()
    val guide = java.io.File("android/kotlin/com/atlas/keyboard/GuideActivity.kt").readText()

    // A tap on a bar word on the menu slide gets that slide's words, not the other slides' "Do it with the keys".
    same(blockedChip(step("menu"), GuideLesson.BarTap.name), "Taps are blocked here. Hold, don't tap.", "A bar tap on the menu slide")
    same(blockedChip(step("steer"), GuideLesson.BarTap.name), "The bar's blocked here. Do it with the keys.", "A bar tap elsewhere")
    same(blockedChip(step("type"), GuideLesson.BarTap.name), "The bar's blocked here. Type it, typo and all.", "A bar tap on the first slide")

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
    for (id in listOf("type", "menu")) same(step(id).lessons.orEmpty().contains(GuideLesson.Space), true, "$id types spaces")
    for (id in listOf("steer", "selectmore")) {
        same(step(id).lessons.orEmpty().contains(GuideLesson.Cursor), true, "$id still teaches the steering taps")
        same(blockedChip(step(id), GuideLesson.Space.name), "Typed spaces are blocked. Taps on Space steer.", "Space on $id")
    }
    same(blockedChip(step("swipe"), GuideLesson.Space.name), "Spaces are blocked here. Swipes space themselves.", "Space on swipe")
    same(keyboard.contains("keyCode == KeyEvent.KEYCODE_SPACE -> GuideLesson.Space"), true, "A pressed Space is its own lesson")
    same(keyboard.contains("keyCode == KeyEvent.KEYCODE_SPACE && GuideHooks.allows(GuideLesson.Cursor)"), false,
        "and never slips through as the cursor's")
    same(keyboard.contains("if (key == \" \" && guideBlocked(GuideLesson.Space)) return"), true, "A light tap that types a space is locked too")
    // A key press ends the touchpad's taps, so a press is never part of a steer: refusing it leaves the taps that steer alone.
    val gesture = java.io.File("android/kotlin/com/atlas/keyboard/CursorGesture.kt").readText()
    same(gesture.contains("/** A key press or something else interrupted the touchpad at [time]. */"), true, "A press ends the taps")

    // Space on the Alt slide says what fits Space, not the letters' Alt line.
    same(blockedChip(step("alt"), GuideLesson.Space.name), "Spaces are blocked here. Just the symbol.", "Space on the Alt slide")
    same(blockedChip(step("alt"), GuideLesson.Typing.name), "Blocked. Hold Alt while you press it.", "A letter there keeps its line")
    same(blockedChip(step("paste"), GuideLesson.Space.name), "Spaces are blocked here. Just paste.", "Space on the paste slide")
    for (lesson in GuideLesson.entries) for (slide in GUIDE_STEPS) {
        val said = blockedChip(slide, lesson.name)
        same(lesson == GuideLesson.Typing || !said.startsWith("Blocked. Hold Alt"), true, "${slide.id}: only letters get the Alt line")
    }

    // One Backspace takes the e: the cursor starts after it, before the field's trailing space.
    val backspace = step("backspace")
    val seeded = backspace.seed + " "
    same(backspace.cursorAt!!(seeded), BACKSPACE_SEED.length, "The cursor starts right after the extra e")
    val deleted = seeded.removeRange(BACKSPACE_SEED.length - 1, BACKSPACE_SEED.length)
    val flow = GuideFlow({ GuideSettings() })
    flow.start("backspace")
    same(flow.event(FieldChanged(deleted, BACKSPACE_LINE.length, BACKSPACE_LINE.length)), GuideVerdict.Done, "One Backspace passes")

    // The ∞ slide never names the symbol: the title, its steps, its chips and its hunt.
    val groups = step("symgroups")
    for (line in listOf(groups.title, groups.done, groups.praise) + groups.stages.map { it.text }) {
        same(line.contains(WEIRD), false, "\"$line\" doesn't name it")
    }
    same(groups.stages[1].text, "Find the one that never ends.", "The hunt's instruction")
    for (line in listOf(WEIRD_HUNT.fast, WEIRD_HUNT.slow(HUNT_DRAIN_SECONDS))) same(line.contains(WEIRD), false, "\"$line\" doesn't name it")
    val steps = java.io.File("android/kotlin/com/atlas/keyboard/GuideSteps.kt").readText()
    val block = steps.substring(steps.indexOf("id = \"symgroups\""), steps.indexOf("id = \"clown\""))
    val literals = Regex("\"([^\"\\n]*)\"").findAll(block).map { it.groupValues[1] }.toList()
    same(literals.none { WEIRD in it }, true, "No line on the slide says the symbol")
    // The owner's two lines, exactly.
    same(WEIRD_HUNT.fast, "Quick. Just like you in bed.", "The fast line")
    same(WEIRD_HUNT.slow(40), "That took 40 seconds. Still longer than you claim to last.", "The slow line")
}
