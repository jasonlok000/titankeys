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
 * - the status marks in the bar just over the word, in every frame, shrinking to fit or hiding when there's no room;
 * - the word solid in each rung's colour, the previews 60% of the way to the next look, and each effect's letters on the way;
 * - the hover after a press, up and down, from I, O, K and L, in every order the touchpad's lift and the key came in on the
 *   owner's phone, and the quick settings page keeping its mark the same way.
 */
fun main() {
    theLadder()
    theOpening()
    theEffects()
    theMarksSitInTheBar()
    theGreyWord()
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
 * the heart particles, then the glitter"). A word opens at rest: a saved word shows its book, a favourite its heart, which beats
 * now and then, the first a little after the menu opens, and nothing else moves. The strip makes the scene at the word's rung and
 * plays no press for it.
 */
private fun theOpening() {
    for (step in MenuKeys.Step.entries) {
        val haptics = ArrayList<String>()
        val opened = scene("whatever", step) { b, label -> haptics += "$b $label" }
        var firstBeat = Float.NaN
        repeat(6 * 120) {
            opened.step(1 / 120f)
            check(opened.parts.isEmpty()) { "$step: opening plays no particle, ${opened.parts.map { it.kind }}" }
            check(opened.book == null && opened.ghost == null) { "$step: no book, no preview" }
            if (opened.beats > 0 && firstBeat.isNaN()) firstBeat = opened.time
        }
        same(haptics, emptyList<String>(), "$step: opening plays no haptic of a step")
        same(opened.rung, step, "$step: and stays where it opened")
        if (step == MenuKeys.Step.Favourite) check(firstBeat >= MenuScene.AMBIENT_FIRST) { "The heart's first beat waits a moment: $firstBeat s" }
        else check(firstBeat.isNaN()) { "$step: no beat" }
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
    override fun text(text: String, x: Float, y: Float, size: Float, colour: Int, centred: Boolean) {
        ops += (if (centred) "ink" else "letter") to (text to colour)
    }
}

/**
 * The status marks live in the bar, just over the word (owner, 2026-11-13: "I find the heart and small book bad to be over the bar
 * as a status. particles are fine. but statuses should be kept maybe just slightly on top of the word"): Saved's filled book,
 * Favourite's heart, beating now and then, and the heart previews in their place. In every frame of every rung, hover and effect,
 * each is centred on the word, over its tallest letter with room to spare, wholly inside the bar and inside the word's room. A
 * preview hides the resting mark under it, and the burst and the crack start from the mark's place. They're about 12 dp across at
 * Normal, and shrink to fit, or hide, when Settings > Size shortens the bar or the word's room is narrow.
 */
private fun theMarksSitInTheBar() {
    /** Inter's x-height, as StripFit has it. */
    val xHeight = 1118f / 2048f
    fun inTheBar(scene: MenuScene, how: String) {
        val letters = scene.baseline - maxOf(MenuScene.TALL * MenuScene.FONT_DP, scene.layout.top)
        for ((name, box) in scene.statusMarks()) {
            check(box[1] >= 0f && box[3] <= scene.barDp) { "$how: the $name sits inside the bar, ${box.toList()}" }
            check(kotlin.math.abs((box[0] + box[2]) / 2 - scene.cx) < .01f) { "$how: the $name is centred on the word" }
            check(box[3] <= letters - 2f) { "$how: the $name clears the letters: its bottom at ${box[3]} dp, the letters' top at $letters" }
            check(box[0] >= scene.roomLeft && box[2] <= scene.roomRight) { "$how: the $name stays in the word's room" }
        }
    }
    fun names(scene: MenuScene) = scene.statusMarks().map { it.first }
    fun run(scene: MenuScene, how: String, frames: Int) = repeat(frames) { scene.step(1 / 120f); inTheBar(scene, how) }
    // At rest: Saved's book and Favourite's heart, about 12 dp across; nothing on the others.
    for (step in MenuKeys.Step.entries) {
        val rest = scene("whatever", step)
        run(rest, "$step at rest", 8 * 120)
        same(names(rest), when (step) {
            MenuKeys.Step.Saved -> listOf("book")
            MenuKeys.Step.Favourite -> listOf("heart")
            else -> emptyList()
        }, "$step's status mark")
        rest.statusMarks().forEach { (name, box) -> check(box[2] - box[0] in 11.5f..13.5f) { "$step's $name is about 12 dp across: ${box[2] - box[0]}" } }
        check(rest.parts.isEmpty()) { "$step at rest: nothing rises over the app" }
    }
    // A heart's preview in the mark's place hides the resting mark, which comes back when the finger moves off.
    for ((step, button) in listOf(MenuKeys.Step.Saved to MenuKeys.Button.Favourite, MenuKeys.Step.Favourite to MenuKeys.Button.Unfavourite)) {
        val hovered = scene("whatever", step)
        hovered.hover(button)
        run(hovered, "$step, hovering $button", 30)
        same(names(hovered), listOf("preview"), "$step, hovering $button: the grey heart alone, in the mark's place")
        hovered.unhover()
        run(hovered, "$step, off $button", 30)
        same(names(hovered), if (step == MenuKeys.Step.Saved) listOf("book") else listOf("heart"), "$step, off $button: the mark is back")
    }
    // Every frame of every effect, from its preview; where each ends; and the burst and the crack start from the mark in the bar.
    for ((button, from) in listOf(MenuKeys.Button.Save to MenuKeys.Step.New, MenuKeys.Button.Unsave to MenuKeys.Step.Saved,
        MenuKeys.Button.Favourite to MenuKeys.Step.Saved, MenuKeys.Button.Unfavourite to MenuKeys.Step.Favourite,
        MenuKeys.Button.Block to MenuKeys.Step.New, MenuKeys.Button.Unblock to MenuKeys.Step.Blocked)) {
        val played = scene("whatever", from)
        played.hover(button)
        run(played, "$button's preview", 24)
        val place = played.statusPlace()
        val middle = place[1] - MenuScene.HEART_BELOW * place[2]
        check(middle > 0f) { "The heart mark's middle is in the bar: $middle dp" }
        played.press(button)
        var started = false
        repeat(3 * 120) {
            played.step(1 / 120f)
            inTheBar(played, "$button +${played.time}")
            val burst = played.parts.filter { it.kind == MenuScene.Kind.Heart || it.kind == MenuScene.Kind.Half }
            if (!started && burst.isNotEmpty()) {
                started = true
                check(burst.all { kotlin.math.hypot(it.x - played.cx, it.y - middle) < 9f }) {
                    "$button: the burst starts from the mark in the bar, at ${burst.map { it.x to it.y }}"
                }
            }
        }
        if (button == MenuKeys.Button.Favourite || button == MenuKeys.Button.Unfavourite) check(started) { "$button bursts" }
        same(names(played), when (MenuScene.next(button)) {
            MenuKeys.Step.Saved -> listOf("book")
            MenuKeys.Step.Favourite -> listOf("heart")
            else -> emptyList()
        }, "$button: the new rung's mark rests in the bar")
    }
    // A favourite's heart beats now and then, in its place: never a heart rising over the app.
    val beating = scene("whatever", MenuKeys.Step.Favourite)
    run(beating, "Favourite at rest", 12 * 120)
    check(beating.beats >= 2 && beating.parts.isEmpty()) { "The resting heart beats now and then: ${beating.beats} beats, ${beating.parts.size} particles" }
    // Settings > Size: at Biggest, the bar is 40 dp in the menu's dp, the word on its baseline as StripFit puts it, its small letters'
    // middle on the bar's centre line; the marks shrink and stay inside.
    for ((size, factor) in listOf("Bigger" to 1.2f, "Biggest" to 1.4f)) {
        val bar = MenuScene.BAR / factor
        val sized = MenuScene("whatever", MenuScene.WordLayout.of("whatever") { it.length * 11f }, 160f, bar / 2 + MenuScene.FONT_DP * xHeight / 2,
            MenuKeys.Step.Saved)
        sized.barDp = bar
        run(sized, "Saved at $size", 10)
        val book = sized.statusMarks().single().second
        check(book[2] - book[0] in 7f..12f) { "At $size the book shrinks to fit the bar, still readable: ${book[2] - book[0]} dp" }
    }
    // A narrow room: the mark stays centred on the word and shrinks to fit, or hides.
    val narrow = scene("whatever", MenuKeys.Step.Saved)
    narrow.roomLeft = narrow.cx - 5f; narrow.roomRight = narrow.cx + 5f
    run(narrow, "Saved in a 10 dp room", 10)
    check(narrow.statusMarks().single().second.let { it[2] - it[0] } <= 10.01f) { "In a 10 dp room the book shrinks to fit" }
    narrow.roomLeft = narrow.cx - 3f; narrow.roomRight = narrow.cx + 3f
    same(names(narrow), emptyList<String>(), "In a 6 dp room the book hides")
    // The marks' look: the book white at 60%, the heart the accent at 70%.
    same(listOf(MenuScene.BOOK_ALPHA, MenuScene.HEART_ALPHA, MenuScene.STATUS_GAP), listOf(.6f, .7f, 3f), "The marks' strengths, and their gap over the word")
}

/**
 * Not saved is a solid grey word, not an outline (owner, 2026-11-13: the outline was "pretty not visible" at every width). Saved and
 * Favourite are white, Not saved #99918A, Blocked #5E5853 under a #99918A strike: each letter drawn once, solid, in one colour.
 * Grey can't mean a preview any more, so a resting finger's preview moves the word about 60% of the way to its next look, in v4's
 * 170 ms, and back when it moves off; a heart's preview leaves the word be. Each effect takes the letters the rest of the way and
 * never back first: Save brightens them to white left to right, Unsave drains them to grey right to left, Block darkens them to the
 * dim grey left to right behind the pen, and Unblock lightens them to grey right to left as the strike pulls back.
 */
private fun theGreyWord() {
    val p = MenuScene.Palette.DARK
    same(listOf(p.ink, p.grey, p.dim), listOf(0xFFFFFF, 0x99918A, 0x5E5853), "The word's white, Not saved's grey and Blocked's dim grey")
    fun drawn(scene: MenuScene): List<Int> = Recorder().also { scene.paintWord(it) }.ops.filter { it.first == "letter" }
        .map { (it.second as Pair<*, *>).second as Int }
    fun toward(from: Int, to: Int, t: Float): Int {
        fun ch(shift: Int) = Math.round(((from shr shift) and 0xFF) + (((to shr shift) and 0xFF) - ((from shr shift) and 0xFF)) * t) shl shift
        return ch(16) or ch(8) or ch(0)
    }
    fun near(a: Int, b: Int) = (0..16 step 8).all { kotlin.math.abs(((a shr it) and 0xFF) - ((b shr it) and 0xFF)) <= 2 }
    fun hex(colours: List<Int>) = colours.joinToString { "#%06X".format(it and 0xFFFFFF) }
    fun brightness(colour: Int) = (colour shr 16 and 0xFF) + (colour shr 8 and 0xFF) + (colour and 0xFF)
    val look = mapOf(MenuKeys.Step.Blocked to p.dim, MenuKeys.Step.New to p.grey, MenuKeys.Step.Saved to p.ink, MenuKeys.Step.Favourite to p.ink)
    val word = "whatever"
    // At rest: each letter drawn once, solid and opaque, in its rung's colour; Blocked's strike in Not saved's grey.
    for ((step, colour) in look) {
        val letters = drawn(scene(word, step))
        same(letters.size, word.length, "$step: each letter drawn once")
        check(letters.all { it ushr 24 == 255 && it and 0xFFFFFF == colour }) { "$step: the word solid in ${hex(listOf(colour))}, not ${hex(letters)}" }
    }
    val strike = Recorder().also { scene(word, MenuKeys.Step.Blocked).paintWord(it) }.ops.filter { it.first == "fill" }
        .map { (it.second as Pair<*, *>).second as Int }
    same(strike.map { it and 0xFFFFFF }, listOf(p.grey), "Blocked's strike is Not saved's grey")
    // The previews: about 60% of the way, in 170 ms, and back when the finger moves off.
    data class Preview(val button: MenuKeys.Button, val from: MenuKeys.Step, val to: Int)
    val previews = listOf(
        Preview(MenuKeys.Button.Save, MenuKeys.Step.New, p.ink), Preview(MenuKeys.Button.Unsave, MenuKeys.Step.Saved, p.grey),
        Preview(MenuKeys.Button.Block, MenuKeys.Step.New, p.dim), Preview(MenuKeys.Button.Unblock, MenuKeys.Step.Blocked, p.grey),
        Preview(MenuKeys.Button.Favourite, MenuKeys.Step.Saved, p.ink), Preview(MenuKeys.Button.Unfavourite, MenuKeys.Step.Favourite, p.ink),
    )
    for (preview in previews) {
        val rest = look.getValue(preview.from)
        val leaned = toward(rest, preview.to, MenuScene.LEAN)
        val hovered = scene(word, preview.from)
        hovered.hover(preview.button)
        repeat(10) { hovered.step(1 / 120f) }
        if (rest != preview.to) check(drawn(hovered).all { !near(it, rest) && !near(it, leaned) }) {
            "${preview.button}: 83 ms in, the preview is on its way, ${hex(drawn(hovered))}"
        }
        repeat(11) { hovered.step(1 / 120f) }
        check(drawn(hovered).all { near(it, leaned) }) { "${preview.button}: 175 ms in, the word is 60% of the way to ${hex(listOf(preview.to))}: ${hex(listOf(leaned))}, not ${hex(drawn(hovered))}" }
        hovered.unhover()
        repeat(21) { hovered.step(1 / 120f) }
        check(drawn(hovered).all { it and 0xFFFFFF == rest }) { "${preview.button}: off the button, the word is back as it was, ${hex(drawn(hovered))}" }
    }
    same(MenuScene.LEAN, .6f, "A preview goes about 60% of the way")
    // The effects: from the preview, the rest of the way, letter by letter in the sweep's direction, and never back first.
    data class Effect(val button: MenuKeys.Button, val from: MenuKeys.Step, val leftToRight: Boolean)
    for (effect in listOf(Effect(MenuKeys.Button.Save, MenuKeys.Step.New, true), Effect(MenuKeys.Button.Unsave, MenuKeys.Step.Saved, false),
        Effect(MenuKeys.Button.Block, MenuKeys.Step.New, true), Effect(MenuKeys.Button.Unblock, MenuKeys.Step.Blocked, false))) {
        val end = look.getValue(MenuScene.next(effect.button)!!)
        val up = brightness(end) > brightness(look.getValue(effect.from))
        val played = scene(word, effect.from)
        played.hover(effect.button)
        repeat(24) { played.step(1 / 120f) }
        var last = drawn(played)
        played.press(effect.button)
        val arrived = IntArray(word.length) { -1 }
        for (frame in 0 until 3 * 120) {
            played.step(1 / 120f)
            val now = drawn(played)
            for (i in now.indices) {
                val back = if (up) brightness(now[i]) < brightness(last[i]) - 1 else brightness(now[i]) > brightness(last[i]) + 1
                check(!back) { "${effect.button}: letter $i went back, from ${hex(listOf(last[i]))} to ${hex(listOf(now[i]))}, at frame $frame" }
                if (arrived[i] < 0 && near(now[i], end)) arrived[i] = frame
            }
            last = now
        }
        check(arrived.all { it >= 0 }) { "${effect.button}: every letter ends ${hex(listOf(end))}, not ${hex(last)}" }
        val order = if (effect.leftToRight) arrived.toList() else arrived.reversed()
        check(order.zipWithNext().all { (a, b) -> a <= b } && order.first() < order.last()) {
            "${effect.button}: the letters arrive ${if (effect.leftToRight) "left to right" else "right to left"}, frames ${arrived.toList()}"
        }
    }
    // The hearts' presses leave the word white throughout, and a press with no preview first still ends in its look.
    for ((button, from) in listOf(MenuKeys.Button.Favourite to MenuKeys.Step.Saved, MenuKeys.Button.Unfavourite to MenuKeys.Step.Favourite)) {
        val played = scene(word, from)
        played.hover(button)
        repeat(24) { played.step(1 / 120f) }
        played.press(button)
        repeat(3 * 120) { played.step(1 / 120f); check(drawn(played).all { it and 0xFFFFFF == p.ink }) { "$button: the word stays white" } }
    }
    val unhovered = scene(word, MenuKeys.Step.New)
    unhovered.press(MenuKeys.Button.Save)
    repeat(3 * 120) { unhovered.step(1 / 120f) }
    check(drawn(unhovered).all { near(it, p.ink) }) { "Save with no preview first: white, ${hex(drawn(unhovered))}" }
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
        check(played.parts.isEmpty() && played.book == null) { "${move.button}: it's all over in 3 s" }
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
        check(twice.book == null && twice.parts.isEmpty()) { "and it all settles" }
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
