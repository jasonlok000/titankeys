package com.atlas.engine

import com.atlas.keyboard.Fingers
import com.atlas.keyboard.Layout
import com.atlas.keyboard.MenuBar
import com.atlas.keyboard.MenuCanvas
import com.atlas.keyboard.MenuKeys
import com.atlas.keyboard.MenuPath
import com.atlas.keyboard.MenuScene
import com.atlas.keyboard.QuickSetting
import com.atlas.keyboard.QuickSettingsPage
import com.atlas.keyboard.SteadyKey
import java.io.File

/**
 * The word menu from end to end, without a phone, so nothing depends on anyone's word (owner, 2026-11-13):
 * - the ladder of four rungs for every kind of word, with the real word lists behind it ([LanguageHabits], [BlockedWords]):
 *   what I, O and Back show and do, and where the word ends up once the lists have done what the menu asked, Unsave and Block
 *   included;
 * - opening the menu plays no burst;
 * - the six effects of the approved ladder v4 ([MenuScene]): each haptic on its own frame, the book wholly above the bar and
 *   opaque once shut, reduced motion, and a second press mid-effect;
 * - the heart resting where the book does, wholly above the bar, with the book faded out under it;
 * - the hover after a press, up and down, from I, O, K and L, in every order the touchpad's lift and the key came in on the
 *   owner's phone, and the quick settings page keeping its mark the same way.
 */
fun main() {
    theLadder()
    theOpening()
    theEffects()
    theHeartRestsWhereTheBookDoes()
    theHover()
    theQuickSettingsHover()
    theKeyboardMakesTheseCalls()
    println("Word menu checks passed")
}

// The ladder ---------------------------------------------------------------------------------------------------------------------

/**
 * What the phone knows about words, as SwipeTyping and FavouriteWords keep it, and the menu AtlasIme.menuFor opens from it. The
 * dictionary is a few words of the base list. Each press does to the lists what AtlasIme's saveWord, unsaveWord, favouriteWord,
 * unfavouriteWord, blockWord and unblockWord, and SwipeTyping under them, do, at once, as the engine does it in the background.
 */
private class WordLists {
    val day = 20_000
    val habits = LanguageHabits()
    var blocked = BlockedWords(emptyList())
    /** SwipeTyping's unsavedWords: dictionary words taken out with Unsave. */
    var unsaved = BlockedWords(emptyList())
    val favourites = HashSet<String>()
    private val dictionary = setOf("and", "the", "i", "a")

    fun builtIn(word: String) = word.lowercase() in dictionary
    // SwipeTyping's answers, from the sets it publishes.
    fun addedWord(word: String) = SwipeWords.normalise(word) in habits.addedWords()
    fun learnedWord(word: String) = SwipeWords.normalise(word) in habits.learnedWords(day)
    fun savedWord(word: String) = addedWord(word) || SwipeWords.normalise(word) in habits.typedWords()
    fun blockedWord(word: String) = blocked.contains(word)
    fun unsavedWord(word: String) = unsaved.contains(word)
    /** SwipeTyping.wordAllowed: neither blocked nor taken out. */
    fun wordAllowed(word: String) = blocked.allowed(word) && !unsaved.contains(word)

    /** You typed [word] and kept it: SwipeTyping.typed, which learns words the dictionary lacks, and dictionary words you took out. */
    fun typed(word: String) {
        val out = unsaved.contains(word)
        if ((builtIn(word) && !out) || !blocked.allowed(word)) return
        habits.typed(word, day, true)
        if (out && SwipeWords.normalise(word) in habits.activeWords(day)) {
            habits.remove(SwipeWords.normalise(word))
            unsaved = unsaved.remove(word)
        }
    }

    /** The menu's rung, as AtlasIme.menuFor reads it. */
    fun menu(word: String) = MenuKeys.step(
        blocked = blockedWord(word),
        favourite = word.lowercase() in favourites,
        saved = addedWord(word),
        learned = learnedWord(word),
        builtIn = builtIn(word),
        unsaved = unsavedWord(word))

    /** Whether TitanKeys would suggest [word]: the dictionary or an active word of yours, and not left out. */
    fun suggests(word: String) = wordAllowed(word) && (builtIn(word) || habits.active(day).any { it.word == SwipeWords.normalise(word) })

    /** What a press of [button] on [word]'s menu does to the lists. */
    fun pick(word: String, button: MenuKeys.Button) {
        val key = word.lowercase()
        when (button) {
            // A dictionary word comes back (restoreWord); any other word goes into Saved words (addWord).
            MenuKeys.Button.Save -> if (builtIn(word)) unsaved = unsaved.remove(word) else habits.add(word, day)
            // SwipeTyping.unsaveWord: out of Saved words with what was learned, and a dictionary word onto the list taken out.
            MenuKeys.Button.Unsave -> {
                if (savedWord(word)) habits.remove(SwipeWords.normalise(word))
                if (builtIn(word)) unsaved = unsaved.add(word)
            }
            MenuKeys.Button.Favourite -> favourites += key
            MenuKeys.Button.Favourited -> Unit
            MenuKeys.Button.Unfavourite -> {
                favourites -= key
                if (!(addedWord(word) || learnedWord(word) || builtIn(word) && !unsavedWord(word))) habits.add(word, day)
            }
            MenuKeys.Button.Block -> blocked = blocked.add(word)
            MenuKeys.Button.Unblock -> blocked = blocked.remove(word)
        }
    }
}

/** One kind of word: an example, and how it gets onto the phone. */
private class Kind(val name: String, val word: String, val setUp: WordLists.() -> Unit)

private fun theLadder() {
    val (blocked, new, saved, favourite) = MenuKeys.Step.entries
    // The owner's gibberish, typed once in a chat app: a candidate still gathering evidence, never suggested, so not saved.
    val gibberish = "Hdxzskjfdhbjsjhdsfbjfs"
    val kinds = listOf(
        Kind("unknown, never typed", "Zorblax") {},
        Kind("candidate, typed once (gathering evidence)", gibberish) { typed(gibberish) },
        Kind("learned, typed twice (suggested)", "Zorbly") { typed("Zorbly"); typed("Zorbly") },
        Kind("learned, faded (not used for a month)", "Plimbo") { habits.typed("Plimbo", day - 30, true); habits.typed("Plimbo", day - 30, true) },
        Kind("saved by you", "Titankeys") { habits.add("Titankeys", day) },
        Kind("dictionary word", "and") {},
        Kind("favourite", "Titankeys") { habits.add("Titankeys", day); favourites += "titankeys" },
        Kind("favourite dictionary word", "the") { favourites += "the" },
        Kind("blocked", "Zorblax") { this.blocked = this.blocked.add("Zorblax") },
        Kind("one letter", "I") {},
        Kind("emoji", "😀") {},
        Kind("symbol", ":)") {},
    )
    val opens = mapOf(
        "unknown, never typed" to new, "candidate, typed once (gathering evidence)" to new, "learned, typed twice (suggested)" to saved,
        "learned, faded (not used for a month)" to new, "saved by you" to saved, "dictionary word" to saved, "favourite" to favourite,
        "favourite dictionary word" to favourite, "blocked" to blocked,
    )
    val table = StringBuilder("| Word | Opens at | I | O | Back | O to the bottom, then I back up | Ends up | I to the top, then O to the bottom | Ends up |\n" +
        "|---|---|---|---|---|---|---|---|---|\n")
    for (kind in kinds) {
        val word = kind.word
        if (!MenuKeys.opens(word)) {
            // No ladder, so no menu at all (owner, 2026-11-13).
            check(kind.name in listOf("one letter", "emoji", "symbol")) { "${kind.name} opens a menu" }
            table.append("| ${kind.name} (\"$word\") | no menu | – | – | – | – | as it was | – | as it was |\n")
            continue
        }
        val opening = WordLists().apply(kind.setUp).menu(word)
        same(opening, opens.getValue(kind.name), "${kind.name}: where it opens")
        val first = MenuKeys.choices(opening)
        // Each walk on lists of its own. After every press the menu shows the press's rung, and a menu opened again from the lists
        // shows the same rung: the menu and the lists agree on where the word is.
        fun walk(order: String): Pair<List<String>, WordLists> {
            val lists = WordLists().apply(kind.setUp)
            var step = opening
            val steps = mutableListOf<String>()
            fun press(button: MenuKeys.Button, key: Char) {
                val next = MenuKeys.press(step, button)
                lists.pick(word, button)
                if (button != MenuKeys.Button.Favourited) check(kotlin.math.abs(next.ordinal - step.ordinal) == 1) { "${kind.name}: $button moves one rung" }
                step = next
                same(lists.menu(word), next, "${kind.name}: after $button, the lists put it where the menu does")
                steps += "$key $button → $next"
            }
            for (key in order) when (key) {
                'I' -> while (true) {
                    val i = MenuKeys.choices(step).i ?: break
                    press(i, 'I')
                    if (i == MenuKeys.Button.Favourited) break
                }
                'O' -> while (true) press(MenuKeys.choices(step).o ?: break, 'O')
                // Back up to where it opened.
                'b' -> while (step.ordinal < opening.ordinal) press(MenuKeys.choices(step).i!!, 'I')
            }
            return steps to lists
        }
        fun ends(lists: WordLists) = lists.menu(word).let { end ->
            "$end" + (if (lists.blockedWord(word)) ", blocked" else "") + (if (lists.unsavedWord(word)) ", taken out" else "") +
                (if (lists.suggests(word)) ", suggested" else ", not suggested")
        }
        val (down, downLists) = walk("Ob")
        val (whole, wholeLists) = walk("IO")
        table.append("| ${kind.name} (\"$word\") | $opening | ${first.i} | ${first.o ?: "–"} | Back | ${down.joinToString("; ")} | ${ends(downLists)} | " +
            "${whole.joinToString("; ")} | ${ends(wholeLists)} |\n")
        same(downLists.menu(word), opening, "${kind.name}: back up where it opened")
        check(whole.last().endsWith("→ Blocked")) { "${kind.name}: the whole ladder ends blocked: $whole" }
        check(whole.any { it.endsWith("→ Favourite") }) { "${kind.name}: and reaches favourite: $whole" }
    }
    // What the store does, kind by kind (owner, 2026-11-13).
    run {
        // Unsave on a dictionary word: it really leaves suggestions, yet isn't blocked. Typing it twice learns it back.
        val lists = WordLists()
        lists.pick("and", MenuKeys.Button.Unsave)
        check(!lists.suggests("and") && !lists.blockedWord("and") && lists.unsavedWord("and")) { "Unsave takes a dictionary word out, unblocked" }
        same(lists.menu("and"), new, "and it opens not saved")
        lists.typed("and")
        same(lists.menu("and"), new, "Typed once, it's still out")
        lists.typed("and")
        check(lists.suggests("and") && !lists.unsavedWord("and") && lists.menu("and") == saved) { "Typed twice, it's learned back, saved" }
        check(lists.habits.entries().none { it.word == "and" }) { "and the dictionary has it again, with no entry of its own" }
    }
    run {
        // Save brings a dictionary word you took out straight back.
        val lists = WordLists()
        lists.pick("and", MenuKeys.Button.Unsave)
        lists.pick("and", MenuKeys.Button.Save)
        check(lists.suggests("and") && lists.menu("and") == saved && !lists.addedWord("and")) { "Save brings it back, without a Saved words entry" }
    }
    run {
        // Unsave on a learned word: forgotten, not blocked, and typing it twice learns it again.
        val lists = WordLists().apply { typed("Zorbly"); typed("Zorbly") }
        lists.pick("Zorbly", MenuKeys.Button.Unsave)
        check(!lists.suggests("Zorbly") && !lists.blockedWord("Zorbly") && "zorbly" !in lists.habits.typedWords()) { "Unsave forgets a learned word" }
        lists.typed("Zorbly"); lists.typed("Zorbly")
        same(lists.menu("Zorbly"), saved, "and typing it twice learns it again")
    }
    run {
        // Unsave on a word you saved: it leaves Saved words.
        val lists = WordLists().apply { habits.add("Titankeys", day) }
        lists.pick("Titankeys", MenuKeys.Button.Unsave)
        check(!lists.addedWord("Titankeys") && !lists.suggests("Titankeys") && !lists.blockedWord("Titankeys")) { "Unsave takes it out of Saved words" }
    }
    run {
        // Block puts it on Blocked words: never suggested or learned. Unblock takes it off, back to not saved.
        val lists = WordLists()
        lists.pick("Zorblax", MenuKeys.Button.Block)
        lists.typed("Zorblax"); lists.typed("Zorblax")
        check(lists.blockedWord("Zorblax") && !lists.suggests("Zorblax") && lists.menu("Zorblax") == blocked) { "Blocked: typing doesn't learn it" }
        lists.pick("Zorblax", MenuKeys.Button.Unblock)
        same(lists.menu("Zorblax"), new, "Unblock: back to not saved")
        lists.typed("Zorblax"); lists.typed("Zorblax")
        same(lists.menu("Zorblax"), saved, "and now typing it twice learns it")
    }
    run {
        // The owner's gibberish: Save on a candidate works.
        val lists = WordLists().apply { typed(gibberish) }
        same(MenuKeys.choices(lists.menu(gibberish)).let { it.i to it.o }, MenuKeys.Button.Save to MenuKeys.Button.Block, "A candidate: Save and Block, no Forget")
        lists.pick(gibberish, MenuKeys.Button.Save)
        check(lists.suggests(gibberish) && lists.menu(gibberish) == saved) { "Save on a candidate saves it" }
    }
    println("The word menu, every kind of word, as built:\n$table")
}

// Opening ------------------------------------------------------------------------------------------------------------------------

/** A word's layout for the checks: each letter 11 dp wide, as a stand-in for the font. */
private fun scene(word: String, step: MenuKeys.Step, onHaptic: (MenuKeys.Button, String) -> Unit = { _, _ -> }) =
    MenuScene(word, MenuScene.WordLayout.of(word) { it.length * 11f }, 160f, MenuScene.CY + 6.5f, step, onHaptic = onHaptic)

/**
 * Opening the menu plays no step's burst, and never two things in a row (owner, 2026-11-13: "when selecting a word, it first shows
 * the heart particles, then the glitter"). As v4 has it, a word opens at rest: a saved word shows its faint book, a favourite gets a
 * faint heart now and then, the first a little after the menu opens, and nothing else moves. The strip makes the scene at the
 * word's rung and plays no press for it.
 */
private fun theOpening() {
    for (step in MenuKeys.Step.entries) {
        val haptics = ArrayList<String>()
        val opened = scene("whatever", step) { b, label -> haptics += "$b $label" }
        var firstHeart = Float.NaN
        repeat(6 * 120) {
            opened.step(1 / 120f)
            check(opened.parts.all { it.ambient && it.alpha <= .33f }) { "$step: opening plays only faint ambient marks, ${opened.parts.map { it.kind }}" }
            check(opened.book == null && opened.ghost == null) { "$step: no book, no preview" }
            if (opened.parts.isNotEmpty() && firstHeart.isNaN()) firstHeart = opened.time
        }
        same(haptics, emptyList<String>(), "$step: opening plays no haptic of a step")
        same(opened.rung, step, "$step: and stays where it opened")
        if (step == MenuKeys.Step.Favourite) check(firstHeart >= MenuScene.AMBIENT_FIRST) { "The first faint heart waits a moment: $firstHeart s" }
        else check(firstHeart.isNaN()) { "$step: no ambient particle" }
    }
    val strip = File("android/kotlin/com/atlas/keyboard/Strip.kt").readText()
    check("menuWordCentre / density, baseline / density, menu.step).also {\n            scene = it\n            sceneNotice = menu" in strip) {
        "A new scene starts at the menu's rung, and plays no press for it"
    }
}

// The effects --------------------------------------------------------------------------------------------------------------------

/** A [MenuCanvas] that writes down what is drawn, in order, for the checks. */
private class Recorder : MenuCanvas {
    val ops = ArrayList<Pair<String, Any?>>()
    override fun save() {}
    override fun restore() {}
    override fun translate(x: Float, y: Float) {}
    override fun rotate(radians: Float) {}
    override fun scale(x: Float, y: Float) {}
    override fun clip(path: MenuPath) {}
    override fun fill(path: MenuPath, colour: Int) { ops += "fill" to (path to colour) }
    override fun stroke(path: MenuPath, width: Float, colour: Int) { ops += "stroke" to (path to colour) }
    override fun circle(x: Float, y: Float, radius: Float, colour: Int) { ops += "circle" to colour }
    override fun rect(left: Float, top: Float, right: Float, bottom: Float, colour: Int) { ops += "rect" to colour }
    override fun text(text: String, x: Float, y: Float, size: Float, fill: Int, outline: Int, outlineWidth: Float, centred: Boolean) {
        ops += (if (centred) "ink" else "letter") to text
    }
}

/**
 * The heart's grey preview, and the pressed heart, sit where Saved's faint book rests, their tip 8 dp over the bar: no heart across
 * the bar's top edge, and none on the book, which fades out while a heart's preview shows (owner, 2026-11-13, as for the book).
 */
private fun theHeartRestsWhereTheBookDoes() {
    fun aboveTheBar(scene: MenuScene, how: String) = scene.heartBox()?.let { box ->
        check(box[3] <= -MenuScene.CLEAR + .01f) { "$how: the heart sits above the bar, its tip at ${box[3]} dp" }
    }
    // Saved, hovering I: the grey heart comes in as the book goes, and the book comes back when the finger moves off.
    val saved = scene("whatever", MenuKeys.Step.Saved)
    same(saved.markShown(), 1f, "Saved shows its faint book")
    saved.hover(MenuKeys.Button.Favourite)
    repeat(48) { saved.step(1 / 120f); aboveTheBar(saved, "Favourite's preview") }
    val box = saved.heartBox()!!
    same(Math.round(box[3] * 100) / 100f, -MenuScene.CLEAR, "The grey heart's tip rests 8 dp over the bar, as the book's foot does")
    check(saved.markShown() < .01f) { "and the book has faded out under it: ${saved.markShown()}" }
    saved.unhover()
    repeat(48) { saved.step(1 / 120f) }
    check(saved.heartBox() == null && saved.markShown() > .99f) { "Off the button, the heart goes and the book is back" }
    // The press takes the preview over: the book never comes back through the heart, which pops about its tip.
    saved.hover(MenuKeys.Button.Favourite)
    repeat(48) { saved.step(1 / 120f) }
    saved.press(MenuKeys.Button.Favourite)
    repeat(3 * 120) {
        saved.step(1 / 120f)
        aboveTheBar(saved, "Favourite's press")
        check(saved.markShown() < .01f) { "Favourite's press: the book stays gone, ${saved.markShown()}" }
    }
    // Favourite, hovering O: the cracked heart in the same place; Unfavourite's press cracks it there, and the book comes back.
    val favourite = scene("whatever", MenuKeys.Step.Favourite)
    favourite.hover(MenuKeys.Button.Unfavourite)
    repeat(48) { favourite.step(1 / 120f); aboveTheBar(favourite, "Unfavourite's preview") }
    same(Math.round(favourite.heartBox()!![3] * 100) / 100f, -MenuScene.CLEAR, "The cracked heart rests in the book's place too")
    favourite.press(MenuKeys.Button.Unfavourite)
    repeat(3 * 120) { favourite.step(1 / 120f); aboveTheBar(favourite, "Unfavourite's press") }
    check(favourite.rung == MenuKeys.Step.Saved && favourite.markShown() > .99f) { "Back on Saved, the faint book is back" }
}

/**
 * The six effects of ladder v4, played as the strip plays them: hover first, then the press, a frame at a time. Each plays its
 * haptic once, on its own frame; the book stays wholly above the bar with its bottom at least 8 dp over it, and once shut its
 * pages are the bar's own colour, drawn over the flying ink, so nothing shows through (owner's corrections, 2026-11-13).
 */
private fun theEffects() {
    data class Move(val button: MenuKeys.Button, val from: MenuKeys.Step, val haptic: String, val millis: IntRange)
    val moves = listOf(
        Move(MenuKeys.Button.Save, MenuKeys.Step.New, "the book claps shut", 500..1000),
        Move(MenuKeys.Button.Unsave, MenuKeys.Step.Saved, "the page rips free", 400..450),
        Move(MenuKeys.Button.Favourite, MenuKeys.Step.Saved, "the hearts release", 150..200),
        Move(MenuKeys.Button.Unfavourite, MenuKeys.Step.Favourite, "the heart cracks", 170..240),
        Move(MenuKeys.Button.Block, MenuKeys.Step.New, "the strike lands", 250..500),
        Move(MenuKeys.Button.Unblock, MenuKeys.Step.Blocked, "the strike is gone", 250..500),
    )
    for (move in moves) {
        val haptics = ArrayList<Triple<MenuKeys.Button, String, Int>>()
        var pressedAt = 0f
        val played = scene("whatever", move.from)
        played.onHaptic = { b, label -> haptics += Triple(b, label, ((played.time - pressedAt) * 1000).toInt()) }
        played.hover(move.button)
        repeat(48) { played.step(1 / 120f) }
        same(played.ghost, move.button, "${move.button}: the resting finger shows its preview")
        pressedAt = played.time
        played.press(move.button)
        var shutChecked = false
        repeat(3 * 120) {
            played.step(1 / 120f)
            played.bookBox()?.let { box -> check(box[3] <= -MenuScene.CLEAR) { "${move.button}: the book sits above the bar, bottom at ${box[3]} dp" } }
            played.markBox()?.let { box -> check(box[3] <= -MenuScene.CLEAR + .01f) { "${move.button}: the resting book sits above the bar, bottom at ${box[3]} dp" } }
            if (!shutChecked && played.bookShut() >= .99f) {
                // The book shut: its pages are filled with the bar's own colour, after the ink, so the ink is hidden.
                val drawn = Recorder()
                played.paintEffects(drawn)
                val pageFills = drawn.ops.withIndex().filter { (_, op) -> op.first == "fill" && (op.second as Pair<*, *>).first in listOf(MenuScene.PAGE_L, MenuScene.PAGE_R) }
                check(pageFills.any { (_, op) -> ((op.second as Pair<*, *>).second as Int) and 0xFFFFFF == MenuScene.Palette.DARK.paper && ((op.second as Pair<*, *>).second as Int) ushr 24 >= 250 }) {
                    "The shut book's pages are opaque, in the bar's colour"
                }
                val lastInk = drawn.ops.indexOfLast { it.first == "ink" }
                check(lastInk < pageFills.first().index) { "The flying ink is drawn under the book" }
                shutChecked = true
            }
        }
        same(haptics.map { it.first to it.second }, listOf(move.button to move.haptic), "${move.button}: one haptic, on its frame")
        check(haptics.single().third in move.millis) { "${move.button}'s haptic at ${haptics.single().third} ms" }
        same(played.rung, MenuScene.next(move.button), "${move.button}: the word ends on its rung")
        check(played.parts.all { it.ambient } && played.book == null) { "${move.button}: it's all over in 3 s" }
        if (move.button == MenuKeys.Button.Save) check(shutChecked) { "Save's book shut" }
    }
    // Reduced motion: one crossfade to the end, no particles, and the haptic at once.
    val haptics = ArrayList<String>()
    val calm = scene("whatever", MenuKeys.Step.New) { _, label -> haptics += label }
    calm.reducedMotion = true
    calm.press(MenuKeys.Button.Save)
    same(haptics, listOf("end state"), "Reduced motion: the haptic as the press lands")
    repeat(25) { calm.step(1 / 120f); check(calm.parts.isEmpty() && calm.book == null) { "No particles with motion off" } }
    repeat(10) { calm.step(1 / 120f) }
    check(!calm.busy && calm.rung == MenuKeys.Step.Saved) { "Done within the crossfade's 200 ms" }
    // A second press mid-effect carries on from where things are, and the word ends where the last press put it.
    for ((first, second, after) in listOf(Triple(MenuKeys.Button.Save, MenuKeys.Button.Unsave, 380), Triple(MenuKeys.Button.Block, MenuKeys.Button.Unblock, 200))) {
        val heard = ArrayList<MenuKeys.Button>()
        val twice = scene("whatever", MenuKeys.Step.New) { b, _ -> heard += b }
        twice.press(first)
        repeat(after * 120 / 1000) { twice.step(1 / 120f) }
        twice.press(second)
        repeat(3 * 120) { twice.step(1 / 120f) }
        same(twice.rung, MenuKeys.Step.New, "$first, then $second mid-effect: back to not saved")
        same(heard.last(), second, "and the second press's haptic plays")
        check(twice.book == null && twice.parts.all { it.ambient }) { "and it all settles" }
    }
    // The tokens are v4's.
    same(listOf(MenuScene.PREVIEW, MenuScene.SQUASH, MenuScene.FADE, MenuScene.SETTLE, MenuScene.EXIT, MenuScene.REDUCED),
        listOf(170f, 70f, 220f, 520f, 380f, 200f), "v4's timings")
    same(MenuScene.POP, MenuScene.Spring.Cfg(1f, 520f, 16f), "v4's pop spring")
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
private fun Fingers.pressUnderFinger(key: String, time: Long, order: Order, back: Long? = null, rest: String = key) {
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
    val (blocked, new, saved, favourite) = MenuKeys.Step.entries
    val (i, o) = MenuKeys.UP to MenuKeys.DOWN
    for (order in Order.entries) for (back in listOf(null, 60L)) for ((up, down) in listOf("I" to "O", "K" to "L")) {
        val how = "${order.describe}, the finger ${if (back == null) "still and unreported" else "reported again"}, $up and $down"
        // Up the ladder with I (or K) under one resting finger: the pill stays on I and shows the next step each time.
        val bar = MenuBar(new)
        bar.touch(up, 0)
        same(bar.pill, i to MenuKeys.Button.Save, "$how: resting on $up shows Save")
        var time = 1_000L
        for ((after, shows) in listOf(saved to MenuKeys.Button.Favourite, favourite to MenuKeys.Button.Favourited,
                favourite to MenuKeys.Button.Favourited)) {
            bar.pressUnderFinger(up, time, order, back)
            // Right after the press, past the press's lift and the plain wait that cleared it before, and long after.
            for (later in listOf(1L, 300L, 1_200L, 2_900L)) {
                bar.at(time + later)
                same(bar.state, after, "$how: the press moved the word to $after")
                same(bar.pill, i to shows, "$how: $later ms after the press, the pill stays on I and shows $shows")
            }
            time += 3_000
        }
        // Down the ladder with O (or L): from Favourite to Saved, to not saved, to blocked, where O has nothing, so no pill.
        bar.touch(down, time)
        same(bar.pill, o to MenuKeys.Button.Unfavourite, "$how: on $down, Unfavourite")
        time += 1_000
        for ((after, shows) in listOf(saved to MenuKeys.Button.Unsave, new to MenuKeys.Button.Block, blocked to null)) {
            bar.pressUnderFinger(down, time, order, back)
            for (later in listOf(1L, 300L, 1_200L, 2_900L)) {
                bar.at(time + later)
                same(bar.state, after, "$how: the press moved the word to $after")
                same(bar.pill, shows?.let { o to it }, "$how: $later ms after the press, O shows ${shows ?: "nothing: no pill"}")
            }
            time += 3_000
        }
        // Not once did the hover leave the finger's key: the first mark is I's, the second O's, and nothing else.
        same(bar.marks.map { it.second }, listOf(i, o), "$how: the hover never went away, ${bar.marks}")
        same(bar.picked, listOf("Save", "Favourite", "Favourited", "Unfavourite", "Unsave", "Block"), "$how: each press one step")
        same(bar.open, true, "$how: the menu stays open")
    }
    for (order in Order.entries) {
        val how = order.describe
        // A finger that moves to another key after the press takes the hover with it at once, and off the buttons ends it.
        val moved = MenuBar(new)
        moved.touch("I", 0)
        moved.pressUnderFinger("I", 1_000, order)
        moved.touch("O", 2_500)
        same(moved.pill, o to MenuKeys.Button.Unsave, "$how: moving to O hovers O's button at once")
        moved.touch("U", 2_600)
        same(moved.pill, null, "$how: moving off the buttons ends the hover")
        // A finger the touchpad shows again, then lifting: the pill goes after the plain wait, as for any lift.
        val lifted = MenuBar(new)
        lifted.touch("I", 0)
        lifted.pressUnderFinger("I", 1_000, order, back = 60)
        lifted.lift(3_000)
        same(lifted.at(3_000 + SteadyKey.LIFT_MILLIS - 1).pill, i to MenuKeys.Button.Favourite, "$how: a short lift keeps it")
        same(lifted.at(3_000 + SteadyKey.LIFT_MILLIS).pill, null, "$how: a real lift ends it")
        // A finger that lifts while the touchpad is quiet can't be seen: the pill stays until the finger lands again.
        val unseen = MenuBar(new)
        unseen.touch("I", 0)
        unseen.pressUnderFinger("I", 1_000, order)
        same(unseen.at(10_000).pill, i to MenuKeys.Button.Favourite, "$how: no touchpad event, no change")
        unseen.touch("H", 10_500)
        same(unseen.pill, null, "$how: the finger landing elsewhere ends it")
    }
    // A press with the finger resting elsewhere: the pill goes to the pressed key, where the finger is, and stays there.
    val elsewhere = MenuBar(saved)
    elsewhere.touch("U", 0)
    same(elsewhere.pill, null, "Resting on U hovers nothing")
    elsewhere.pressUnderFinger("I", 1_000, Order.Typical)
    same(elsewhere.at(3_000).pill, i to MenuKeys.Button.Favourited, "Pressing I hovers I")
    // A slow app: the touchpad's lift reached the keyboard 200 ms before the key did, so the plain wait cleared the hover first.
    // The press brings it back on the pressed key.
    val slow = MenuBar(new)
    slow.touch("I", 0)
    slow.lift(800)
    slow.press("I", 1_000)
    same(slow.at(3_000).pill, i to MenuKeys.Button.Favourite, "A late key brings the hover back")
    // Back has no hover, and closes the menu, from P or Backspace.
    for (back in listOf("P", Layout.BACKSPACE)) {
        val bar = MenuBar(saved)
        bar.touch(back, 0)
        same(bar.mark to bar.pill, -1 to null, "$back hovers nothing: Back has no pill")
        bar.pressUnderFinger(back, 1_000, Order.Typical)
        same(bar.open to bar.pill, false to null, "$back closes the menu")
        same(bar.picked, listOf("Back"), "$back picks Back")
    }
    // Block, then Unblock: the menu stays open; on blocked, O has nothing, so the hover on O shows no pill, and I is Unblock.
    val block = MenuBar(new)
    block.touch("O", 0)
    same(block.pill, o to MenuKeys.Button.Block, "O shows Block on a word that isn't saved")
    block.pressUnderFinger("O", 1_000, Order.Typical)
    same(block.at(3_000).let { Triple(it.open, it.mark, it.pill) }, Triple(true, o, null), "Blocked: the menu stays, the hover on O, with no pill")
    block.touch("I", 3_500)
    same(block.pill, i to MenuKeys.Button.Unblock, "and I is Unblock")
    block.pressUnderFinger("I", 4_000, Order.Typical)
    same(block.at(6_000).let { it.state to it.pill }, new to (i to MenuKeys.Button.Save), "Unblock: not saved again, and I is Save")
    // At the bottom, a blocked word's O has no button: its key picks nothing, so it types and closes the menu, as any key does.
    val bottom = MenuBar(blocked)
    bottom.touch("O", 0)
    same(bottom.pill, null, "O at the bottom has no pill")
    bottom.press("O", 1_000)
    same(bottom.open to bottom.picked, false to listOf("types O"), "O with no button types, and the menu closes")
    // A hover with no press goes after the plain wait, as before.
    val plain = MenuBar(new)
    plain.touch("I", 0)
    plain.lift(1_000)
    same(plain.at(1_000 + SteadyKey.LIFT_MILLIS - 1).pill, i to MenuKeys.Button.Save, "A short lift keeps the pill")
    same(plain.at(1_000 + SteadyKey.LIFT_MILLIS).pill, null, "A real lift ends it after the usual wait")
}

// The quick settings page ---------------------------------------------------------------------------------------------------------

/**
 * The quick settings page keeps its mark through a toggle, as the word menu keeps its hover (owner, 2026-11-13: the page was the
 * word menu's model, so it must really keep it). Same touchpad, same event orders: before, its mark went about 0.3 s after a
 * toggle under a still finger, when the press's own lift had waited out the plain 150 ms.
 */
private fun theQuickSettingsHover() {
    for (order in Order.entries) for (back in listOf(null, 60L)) for (key in listOf("F", "L", "M", "S")) {
        val item = QuickSetting.on(key)!!
        val how = "Quick settings, ${order.describe}, the finger ${if (back == null) "still and unreported" else "reported again"}, $key"
        val page = QuickSettingsPage()
        page.touch(key, 0)
        same(page.mark, key, "$how: resting on $key marks it")
        var time = 1_000L
        for (toggle in 1..3) {
            page.pressUnderFinger(key, time, order, back)
            for (later in listOf(1L, 300L, 1_200L, 2_900L)) {
                page.at(time + later)
                same(page.mark, key, "$how: $later ms after toggle $toggle, the mark stays on $key")
                same(page.on[item], toggle % 2 == 1, "$how: toggle $toggle switched $item")
            }
            time += 3_000
        }
        same(page.marks.map { it.second }, listOf(key), "$how: the mark never went away, ${page.marks}")
    }
    for (order in Order.entries) {
        val how = "Quick settings, ${order.describe}"
        // A finger that moves to another key after a toggle takes the mark with it at once.
        val moved = QuickSettingsPage()
        moved.touch("F", 0)
        moved.pressUnderFinger("F", 1_000, order)
        moved.touch("V", 2_500)
        same(moved.mark, "V", "$how: moving to V marks V at once")
        // A finger the touchpad shows again, then lifting: the mark goes after the plain wait, as for any lift.
        val lifted = QuickSettingsPage()
        lifted.touch("F", 0)
        lifted.pressUnderFinger("F", 1_000, order, back = 60)
        lifted.lift(3_000)
        same(lifted.at(3_000 + SteadyKey.LIFT_MILLIS - 1).mark, "F", "$how: a short lift keeps it")
        same(lifted.at(3_000 + SteadyKey.LIFT_MILLIS).mark, "", "$how: a real lift ends it")
        // A finger that lifts while the touchpad is quiet can't be seen: the mark stays until the finger lands again.
        val unseen = QuickSettingsPage()
        unseen.touch("F", 0)
        unseen.pressUnderFinger("F", 1_000, order)
        same(unseen.at(10_000).mark, "F", "$how: no touchpad event, no change")
        unseen.touch("V", 10_500)
        same(unseen.mark, "V", "$how: the finger landing on V marks V")
    }
    // Open and Kbd close the panel, and X turns to the clipboard page: the mark doesn't carry over.
    for (key in listOf("O", "K", "X")) {
        val page = QuickSettingsPage()
        page.touch(key, 0)
        same(page.mark, key, "Resting on $key marks it")
        page.pressUnderFinger(key, 1_000, Order.Typical)
        same(page.at(3_000).let { it.page != QuickSettingsPage.Page.Settings && it.mark == "" }, true, "$key leaves the page, with no mark")
    }
    // A mark with no press goes after the usual wait, as before.
    val plain = QuickSettingsPage()
    plain.touch("F", 0)
    plain.lift(1_000)
    same(plain.at(1_000 + SteadyKey.LIFT_MILLIS - 1).mark, "F", "A short lift keeps the mark")
    same(plain.at(1_000 + SteadyKey.LIFT_MILLIS).mark, "", "A real lift ends it after the usual wait")
}

/**
 * [MenuBar], [QuickSettingsPage] and [WordLists] make AtlasIme's and SwipeTyping's calls in their order. These are the lines they
 * copy; a change to one of them must be made in the copy too, or this check fails.
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
        "if (column == MenuKeys.UP || column == MenuKeys.DOWN) column else -1",
        "val next = MenuKeys.press(menu.step, button)",
        // The quick settings page, as [QuickSettingsPage] copies it.
        "if (modifiers.panel == Modifiers.Panel.Settings) Layout.restingKeyAt(x, y) else Layout.keyAt(x, y)",
        "panelHover.keyDown(keyCode)",
        "keepPanelHover(letter, event.keyCode)",
        "if (modifiers.panel != Modifiers.Panel.Settings) return",
        "panelHover.press(key)",
        "val panelRelease = keyCode == panelPress",
        "panelHover.release(keyCode, event.eventTime)",
        "val panelLast = !panelRelease && panelHover.keyUp(keyCode, event.eventTime) && panelOpen",
        "panelHover.lift(event.eventTime)",
        "panelHover.touch(last.x, last.y)",
        "panel?.hover = panelHover.key",
        "if (panelHover.expire(now)) panel?.hover = \"\"",
        // The menu's rung from the lists, and what each press asks of them, as [WordLists] copies it.
        "blocked = typing.blockedWord(word),",
        "favourite = FavouriteWords.get(this).contains(word),",
        "saved = typing.addedWord(word),",
        "learned = typing.learnedWord(word),",
        "builtIn = typing.builtInWord(word),",
        "unsaved = typing.unsavedWord(word))",
        "if (typing.builtInWord(held.word)) typing.restoreWord(held.word, done) else typing.addWord(keptCase(held), done)",
        // The case a saved word is kept in is its own, not the sentence start's, Shift's or Caps Lock's (owner, 2026-11-13).
        "typing.addWord(keptCase(held)) { error ->",
        "return WordCase.kept(held.word, before?.dropLast(held.typed.length), modifiers.shift != Modifiers.Level.Off)",
        "SwipeTyping.get(this).unsaveWord(held.word) { error ->",
        "SwipeTyping.get(this).blockWord(held.word, held.typed) { error ->",
        "SwipeTyping.get(this).setWordBlocked(held.word, false) { error ->",
        "typing.builtInWord(held.word) && !typing.unsavedWord(held.word)",
        "MenuKeys.Button.Block -> Haptic.WordBlocked",
        "MenuKeys.Button.Unblock -> Haptic.WordUnblocked",
    )) check(line in ime) { "AtlasIme no longer has: $line" }
    val typing = File("android/kotlin/com/atlas/engine/SwipeTyping.java").readText()
    for (line in listOf(
        "learnedEntries = dictionary.learnedWords(day);",
        "typedEntries = dictionary.typedWords();",
        "public boolean wordAllowed(String word) { return hiddenWords.allowed(word); }",
        "if (failure == null && builtInWord(word)) failure = unsave(word, true);",
        "boolean unsaved = unsavedWords.contains(word);",
        "if ((standard(word) && !unsaved) || !blockedWords.allowed(word) || phoneName(word)) return;",
        "if (unsaved && dictionary.activeWords(today()).contains(word)) {",
        "String result = block(word, true);",
        "repairCase();",
        "int changed = dictionary.lowercase(word -> baseFrequency(word) > 0 && lowercase.lowercase(word));",
    )) check(line in typing) { "SwipeTyping no longer has: $line" }
    check("words.typed(WordCase.kept(word, if (index < 0) null else before!!.substring(0, index), false), rememberCase)" in
        File("android/kotlin/com/atlas/keyboard/Composer.kt").readText()) { "Typing learns a word in its own case" }
    // Every filter leaves out both lists, the blocked words and the dictionary words taken out.
    check("blockedWords.filter(" !in typing && Regex("hiddenWords\\.filter\\(").findAll(typing).count() == 4) { "Every filter leaves out both lists" }
    // The strip keeps the hover through a new state: a new menu notice for the same word never resets the mark or the pill.
    val strip = File("android/kotlin/com/atlas/keyboard/Strip.kt").readText()
    val showNotice = strip.substringAfter("private fun showNotice(").substringBefore("\n    private var menuIn")
    check("menuMark" !in showNotice) { "A new notice must not touch the hover's mark" }
    check("pillShown = -1" in showNotice.substringAfter("if (menu == null) {").substringBefore("} else {")) { "Only a closed menu lets go of the pill" }
    check("pillShown" !in showNotice.substringAfter("} else {")) { "A new step keeps the pill" }
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}
