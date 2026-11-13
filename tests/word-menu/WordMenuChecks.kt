package com.atlas.engine

import com.atlas.keyboard.Layout
import com.atlas.keyboard.MenuBar
import com.atlas.keyboard.MenuKeys
import com.atlas.keyboard.MenuMotion
import com.atlas.keyboard.SteadyKey
import java.io.File

/**
 * The word menu from end to end, without a phone (owner, 2026-11-13: two bugs reported again and again, so every state is proved
 * here instead of on anyone's word):
 * - the ladder for every kind of word, with the real dictionary behind it ([LanguageHabits], [BlockedWords], [ForgetWords]): what
 *   I, O and Back show and do, and where the word ends up once the lists have done what the menu asked;
 * - the hover after a press, up and down, from I, O, K and L, in every order the touchpad's lift and the key came in on the
 *   owner's phone.
 */
fun main() {
    theLadder()
    theOpening()
    theHover()
    theKeyboardMakesTheseCalls()
    println("Word menu checks passed")
}

// The ladder ---------------------------------------------------------------------------------------------------------------------

/**
 * What the phone knows about words, as SwipeTyping and FavouriteWords keep it, and the menu AtlasIme.menuFor opens from it. The
 * dictionary is a few words of the base list. Each press does to the lists what AtlasIme's saveWord, unsaveWord, favouriteWord,
 * unfavouriteWord and commitForget do, at once, as the engine does it in the background.
 */
private class WordLists {
    val day = 20_000
    val habits = LanguageHabits()
    var blocked = BlockedWords(emptyList())
    val favourites = HashSet<String>()
    private val dictionary = setOf("and", "the", "i", "a")

    fun builtIn(word: String) = word.lowercase() in dictionary
    // SwipeTyping's answers, from the sets it publishes.
    fun addedWord(word: String) = SwipeWords.normalise(word) in habits.addedWords()
    fun learnedWord(word: String) = SwipeWords.normalise(word) in habits.learnedWords(day)
    fun savedWord(word: String) = addedWord(word) || SwipeWords.normalise(word) in habits.typedWords()
    fun wordAllowed(word: String) = blocked.allowed(word)

    /** You typed [word] and kept it: SwipeTyping.typed, which learns only words the dictionary lacks. */
    fun typed(word: String) {
        if (!builtIn(word) && wordAllowed(word)) habits.typed(word, day, true)
    }

    /** The menu as AtlasIme.menuFor opens it. */
    fun menu(word: String) = MenuKeys.state(
        saved = addedWord(word),
        favourite = word.lowercase() in favourites,
        blocked = !wordAllowed(word),
        learned = learnedWord(word),
        oneLetter = ForgetWords.oneLetter(word),
        builtIn = builtIn(word),
        savable = MenuKeys.savable(word))

    /** Whether TitanKeys would suggest [word]: the dictionary or an active word of yours, and not blocked. */
    fun suggests(word: String) = wordAllowed(word) && (builtIn(word) || habits.active(day).any { it.word == SwipeWords.normalise(word) })

    /** SwipeTyping.addWord: a word the store can't hold, such as "I", fails, and the keyboard only logs it. */
    private fun save(word: String) {
        try {
            habits.add(word, day)
        } catch (error: IllegalArgumentException) {
            check(!MenuKeys.savable(word)) { "Save failed for a word the menu offers to save: $word" }
        }
    }

    /** What a press of [button] on [word]'s menu does to the lists. */
    fun pick(word: String, button: MenuKeys.Button) {
        val key = word.lowercase()
        when (button) {
            MenuKeys.Button.Save -> {
                if (!wordAllowed(word)) blocked = blocked.remove(word)
                save(word)
            }
            MenuKeys.Button.Unsave -> habits.remove(SwipeWords.normalise(word))
            MenuKeys.Button.Favourite -> {
                favourites += key
                if (!wordAllowed(word)) blocked = blocked.remove(word)
            }
            MenuKeys.Button.Favourited -> Unit
            MenuKeys.Button.Unfavourite -> {
                favourites -= key
                if (!addedWord(word)) save(word)
            }
            MenuKeys.Button.Forget -> {
                favourites -= key
                val builtIn = builtIn(word)
                val saved = savedWord(word)
                if (ForgetWords.removes(word, builtIn, saved)) habits.remove(SwipeWords.normalise(word))
                if (ForgetWords.blocks(word, builtIn, saved)) blocked = blocked.add(word)
            }
        }
    }
}

/** One kind of word: an example, how it gets onto the phone, and the walk it takes. */
private class Kind(val name: String, val word: String, val setUp: WordLists.() -> Unit)

private fun theLadder() {
    val (new, saved, favourite) = Triple(MenuKeys.Step.New, MenuKeys.Step.Saved, MenuKeys.Step.Favourite)
    // The owner's gibberish, typed once in a chat app: a candidate still gathering evidence, never suggested.
    val gibberish = "Hdxzskjfdhbjsjhdsfbjfs"
    run {
        val phone = WordLists()
        phone.typed(gibberish)
        check(SwipeWords.normalise(gibberish) in phone.habits.typedWords()) { "Typing it once puts it in the list, gathering evidence" }
        check(!phone.suggests(gibberish)) { "A word typed once isn't suggested" }
        check(!phone.learnedWord(gibberish)) { "so it isn't learned" }
        same(MenuKeys.choices(phone.menu(gibberish)).o, null, "and its menu has no Forget (owner: \"a word clearly not known\")")
        phone.typed(gibberish)
        check(phone.suggests(gibberish) && phone.learnedWord(gibberish)) { "Typed twice, it's suggested, so it's learned" }
        same(MenuKeys.choices(phone.menu(gibberish)).o, MenuKeys.Button.Forget, "and now O forgets it")
    }

    val kinds = listOf(
        Kind("unknown, never typed", "Zorblax") {},
        Kind("candidate, typed once (gathering evidence)", gibberish) { typed(gibberish) },
        Kind("learned, typed twice (suggested)", "Zorbly") { typed("Zorbly"); typed("Zorbly") },
        Kind("saved by you", "Titankeys") { habits.add("Titankeys", day) },
        Kind("dictionary word", "and") {},
        Kind("favourite", "Titankeys") { habits.add("Titankeys", day); favourites += "titankeys" },
        Kind("favourite dictionary word", "the") { favourites += "the" },
        Kind("one letter (dictionary)", "I") {},
        Kind("one letter, typed", "q") { typed("q") },
        Kind("emoji", "😀") {},
        Kind("symbol", ":)") {},
    )
    // What each kind opens at, and what I and O show there: the owner's rules, written out.
    val opens = mapOf(
        "unknown, never typed" to Triple(new, "Save", "–"),
        "candidate, typed once (gathering evidence)" to Triple(new, "Save", "–"),
        "learned, typed twice (suggested)" to Triple(new, "Save", "Forget"),
        "saved by you" to Triple(saved, "Favourite", "Unsave"),
        "dictionary word" to Triple(saved, "Favourite", "Forget"),
        "favourite" to Triple(favourite, "Favourited", "Unfavourite"),
        "favourite dictionary word" to Triple(favourite, "Favourited", "Unfavourite"),
        "one letter (dictionary)" to Triple(saved, "Favourite", "–"),
        "one letter, typed" to null,
        "emoji" to null,
        "symbol" to null,
    )
    val table = StringBuilder("| Word | Opens at | I | O | Back | O to the bottom, then I once | Ends up | I to the top, then O to the bottom | Ends up |\n" +
        "|---|---|---|---|---|---|---|---|---|\n")
    for (kind in kinds) {
        val phone = WordLists().apply(kind.setUp)
        val word = kind.word
        val opening = phone.menu(word)
        val first = MenuKeys.choices(opening)
        val expected = opens.getValue(kind.name)
        if (expected == null) {
            // Nothing to save and nothing learned: a menu of only Back would do nothing, so it never opens.
            same(first.i to first.o, null to null, "${kind.name}: no buttons")
            table.append("| ${kind.name} (\"$word\") | no menu | – | – | – | – | as it was | – | as it was |\n")
            continue
        }
        same(Triple(opening.step, first.i?.name ?: "–", first.o?.name ?: "–"), expected, "${kind.name}: where it opens and what I and O show")
        // Back is always on P and Backspace, with no pill, and closing changes nothing.
        same(MenuKeys.pills(MenuKeys.BACK, opening), false, "${kind.name}: Back has no pill")
        same(phone.menu(word), opening, "${kind.name}: Back leaves the word as it was")
        // Two walks, each from where the word opens, on lists of its own: O down to the bottom, then I back up once; and I up to
        // the top, then O down to the bottom. Every press is a step and the menu stays open on the word, Forget too (owner,
        // 2026-11-13). After each press the menu shows the press's state, and a menu opened again from the lists shows the same
        // buttons: the menu and the lists agree on where the word is.
        fun walk(order: String): Pair<List<String>, WordLists> {
            val lists = WordLists().apply(kind.setUp)
            var state = opening
            val steps = mutableListOf<String>()
            fun press(button: MenuKeys.Button, key: Char) {
                val next = MenuKeys.press(state, button)
                lists.pick(word, button)
                if (button == MenuKeys.Button.Forget) same(next, state.copy(step = MenuKeys.Step.New, learned = false),
                    "${kind.name}: Forget leaves it not saved, with nothing learned left, and the menu open")
                else check(kotlin.math.abs(next.step.ordinal - state.step.ordinal) <= 1) { "${kind.name}: $button moves one step at most" }
                state = next
                val reopened = lists.menu(word)
                same(reopened.step to MenuKeys.choices(reopened).let { it.i to it.o }, next.step to MenuKeys.choices(next).let { it.i to it.o },
                    "${kind.name}: after $button, the menu shows what the lists say")
                steps += "$key $button → ${next.step}"
            }
            for (key in order) when (key) {
                'I' -> while (true) {
                    val i = MenuKeys.choices(state).i ?: break
                    press(i, 'I')
                    if (i == MenuKeys.Button.Favourited) break
                }
                'O' -> while (true) press(MenuKeys.choices(state).o ?: break, 'O')
                'i' -> MenuKeys.choices(state).i?.let { press(it, 'I') }
            }
            return steps to lists
        }
        fun ends(lists: WordLists) = lists.menu(word).let { end ->
            "${end.step}" + (if (!lists.wordAllowed(word)) ", blocked" else "") +
                (if (lists.suggests(word)) ", suggested" else ", not suggested") + (if (lists.learnedWord(word)) ", learned" else "")
        }
        val (down, downLists) = walk("Oi")
        val (whole, wholeLists) = walk("IO")
        table.append("| ${kind.name} (\"$word\") | ${opening.step} | ${first.i ?: "–"} | ${first.o ?: "–"} | Back | " +
            "${down.joinToString("; ")} | ${ends(downLists)} | ${whole.joinToString("; ")} | ${ends(wholeLists)} |\n")
        // Every kind with a menu reaches Favourite and comes back down through Saved.
        check(whole.any { it.endsWith("→ Favourite") } && "O Unfavourite → Saved" in whole) { "${kind.name}: the whole ladder, $whole" }
        // Down from where it opens, every kind reaches not saved, and I's Save is there to bring it back: all but a single letter,
        // which is never taken out.
        if (opening.oneLetter) same(down.none { it.startsWith("O") }, true, "${kind.name}: a single letter has nothing below Saved")
        else check(down.any { it.startsWith("O") && it.endsWith("→ New") } || opening.step == new) { "${kind.name}: down to not saved, $down" }
    }
    // Save on a candidate works, and keeps it: it's suggested at once.
    run {
        val phone = WordLists().apply { typed(gibberish) }
        phone.pick(gibberish, MenuKeys.Button.Save)
        same(phone.menu(gibberish).step, saved, "Save on a candidate saves it")
        check(phone.suggests(gibberish)) { "A saved candidate is suggested" }
    }
    // Forget on a learned word takes it out, and blocks it, so typing it again can't learn it back. The menu stays open on it, not
    // saved: I is Save, which lets it back in, and O has nothing.
    run {
        val phone = WordLists().apply { typed("Zorbly"); typed("Zorbly") }
        val after = MenuKeys.press(phone.menu("Zorbly"), MenuKeys.Button.Forget)
        phone.pick("Zorbly", MenuKeys.Button.Forget)
        check("zorbly" !in phone.habits.typedWords() && !phone.suggests("Zorbly")) { "Forget takes a learned word out" }
        same(after, MenuKeys.State(new, false), "Forget leaves the menu open, not saved, with nothing to forget")
        same(MenuKeys.choices(after).let { it.i to it.o }, MenuKeys.Button.Save to null, "I is Save, and O has nothing")
        same(phone.menu("Zorbly"), after, "and the lists agree")
        phone.typed("Zorbly"); phone.typed("Zorbly")
        check(!phone.suggests("Zorbly")) { "Typing a forgotten word again doesn't learn it back" }
        phone.pick("Zorbly", MenuKeys.Button.Save)
        check(phone.suggests("Zorbly") && phone.menu("Zorbly").step == saved) { "Save lets it back in" }
    }
    // A dictionary word taken down from Saved leaves the dictionary: it's blocked, with nothing to forget, and the menu stays on it.
    run {
        val phone = WordLists()
        val after = MenuKeys.press(phone.menu("and"), MenuKeys.Button.Forget)
        phone.pick("and", MenuKeys.Button.Forget)
        check(!phone.suggests("and")) { "Forget takes a dictionary word out" }
        same(after, MenuKeys.State(new, false, builtIn = true), "and the menu stays on it, not saved")
        same(phone.menu("and"), after, "as the lists say")
        same(MenuKeys.choices(after).let { it.i to it.o }, MenuKeys.Button.Save to null, "with Save to bring it back")
        phone.pick("and", MenuKeys.Button.Save)
        check(phone.suggests("and") && phone.menu("and").step == saved) { "Save brings it back" }
    }
    println("The word menu, every kind of word, as built:\n$table")
}

// The opening -------------------------------------------------------------------------------------------------------------------

/**
 * Opening the menu plays no step's burst, and never two things in a row (owner, 2026-11-13: "when selecting a word, it first shows
 * the heart particles, then the glitter"). A word that isn't saved gets nothing; a saved word, a dictionary word included, gets a
 * soft echo of its own mark; a favourite a soft echo of hearts. The quiet ambient marks then wait a whole cycle before the first.
 *
 * The cause was two things: the open's echo drew hearts for every saved word, a favourite's sign; and the ambient began with its
 * mark, a sparkle by a saved word, the moment the echo ended. The strip plays MenuKeys.motion(null, state) as it opens, then the
 * ambient from when the motion ends, as checked below in its source.
 */
private fun theOpening() {
    val box = MenuMotion.Word(120f, 220f, 27f, MenuMotion.Word.seed("Titankeys"))
    val bursts = setOf(MenuKeys.Motion.Save, MenuKeys.Motion.Favourite, MenuKeys.Motion.Unfavourite, MenuKeys.Motion.Unsave, MenuKeys.Motion.Forget)
    val gibberish = "Hdxzskjfdhbjsjhdsfbjfs"
    val kinds = listOf(
        Triple("unknown", "Zorblax") { _: WordLists -> },
        Triple("candidate", gibberish) { it: WordLists -> it.typed(gibberish) },
        Triple("learned", "Zorbly") { it: WordLists -> it.typed("Zorbly"); it.typed("Zorbly") },
        Triple("saved", "Titankeys") { it: WordLists -> it.habits.add("Titankeys", it.day) },
        Triple("dictionary", "and") { _: WordLists -> },
        Triple("favourite", "Titankeys") { it: WordLists -> it.habits.add("Titankeys", it.day); it.favourites += "titankeys" },
    )
    for ((kind, word, setUp) in kinds) {
        val opening = WordLists().also(setUp).menu(word)
        val motion = MenuKeys.motion(null, opening)
        check(motion !in bursts) { "$kind: opening plays no step's burst, but $motion" }
        same(motion, when (opening.step) {
            MenuKeys.Step.New -> null
            MenuKeys.Step.Saved -> MenuKeys.Motion.SavedEcho
            MenuKeys.Step.Favourite -> MenuKeys.Motion.Echo
        }, "$kind: what opening plays")
        val echo = motion?.let { MenuMotion.millis(it) } ?: 0L
        val mark = if (opening.step == MenuKeys.Step.Favourite) MenuMotion.Shape.Heart else MenuMotion.Shape.Sparkle
        for (millis in 0 until echo step 10) {
            val sprites = MenuMotion.frame(motion!!, millis, box).sprites
            check(sprites.size <= 3 && sprites.all { it.shape == mark && it.size <= 9f && it.alpha <= .75f }) {
                "$kind: the echo is soft, a few faint ${mark}s, at $millis ms: $sprites"
            }
        }
        // After the echo the menu rests: nothing for a whole cycle of its ambient mark.
        val quiet = when (opening.step) {
            MenuKeys.Step.New -> Long.MAX_VALUE
            MenuKeys.Step.Saved -> MenuMotion.AMBIENT_TWINKLE_EVERY
            MenuKeys.Step.Favourite -> MenuMotion.AMBIENT_HEART_EVERY
        }
        for (millis in 0L until minOf(quiet, 6_000L) step 10) same(MenuMotion.ambient(opening.step, millis, box, false), emptyList<MenuMotion.Sprite>(),
            "$kind: nothing plays $millis ms after the echo")
        if (opening.step != MenuKeys.Step.New) check(MenuMotion.ambient(opening.step, quiet + 300, box, false).single().let {
            it.shape == mark && it.alpha <= MenuMotion.AMBIENT_ALPHA + .001f
        }) { "$kind: then a faint ${mark.name.lowercase()} now and then" }
    }
    // A step's burst is followed by the same quiet: never two in a row after a press either.
    for (step in listOf(MenuKeys.Step.Saved, MenuKeys.Step.Favourite)) for (millis in 0L until 2_000L step 10)
        same(MenuMotion.ambient(step, millis, box, false), emptyList<MenuMotion.Sprite>(), "Nothing right after a press to $step")
    // The strip plays these: the open's motion from no state, then the ambient from when the motion ends.
    val strip = File("android/kotlin/com/atlas/keyboard/Strip.kt").readText()
    for (line in listOf(
        "menuMotion = MenuKeys.motion(if (opened) null else shownState, menu.state)?.takeIf { GuideMotion.enabled }",
        "if (ambientStart < 0) ambientStart = now",
        "for (sprite in MenuMotion.ambient(menu.step, now - ambientStart, box, still)) drawSprite(canvas, sprite, density)",
    )) check(line in strip) { "Strip no longer has: $line" }
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
private fun MenuBar.pressUnderFinger(key: String, time: Long, order: Order, back: Long? = null, rest: String = key) {
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
    val state = { step: MenuKeys.Step, learned: Boolean -> MenuKeys.State(step, learned) }
    val (new, saved, favourite) = Triple(MenuKeys.Step.New, MenuKeys.Step.Saved, MenuKeys.Step.Favourite)
    val (i, o) = MenuKeys.SAVE to MenuKeys.FORGET
    for (order in Order.entries) for (back in listOf(null, 60L)) for ((up, down) in listOf("I" to "O", "K" to "L")) {
        val how = "${order.describe}, the finger ${if (back == null) "still and unreported" else "reported again"}, $up and $down"
        // Up the ladder with I (or K) under one resting finger: the pill stays on I and shows the next step each time.
        val bar = MenuBar(state(new, true))
        bar.touch(up, 0)
        same(bar.pill, i to MenuKeys.Button.Save, "$how: resting on $up shows Save")
        var time = 1_000L
        for ((after, shows) in listOf(saved to MenuKeys.Button.Favourite, favourite to MenuKeys.Button.Favourited,
                favourite to MenuKeys.Button.Favourited)) {
            bar.pressUnderFinger(up, time, order, back)
            // Right after the press, past the press's lift and the plain wait that cleared it before, and long after.
            for (later in listOf(1L, 300L, 1_200L, 2_900L)) {
                bar.at(time + later)
                same(bar.state.step, after, "$how: the press moved the word to $after")
                same(bar.pill, i to shows, "$how: $later ms after the press, the pill stays on I and shows $shows")
            }
            time += 3_000
        }
        // Down the ladder with O (or L): from Favourite to Saved, then to not saved, where O has nothing left, so no pill.
        bar.touch(down, time)
        same(bar.pill, o to MenuKeys.Button.Unfavourite, "$how: on $down, Unfavourite")
        time += 1_000
        for ((after, shows) in listOf(saved to MenuKeys.Button.Unsave, new to null)) {
            bar.pressUnderFinger(down, time, order, back)
            for (later in listOf(1L, 300L, 1_200L, 2_900L)) {
                bar.at(time + later)
                same(bar.state.step, after, "$how: the press moved the word to $after")
                same(bar.pill, shows?.let { o to it }, "$how: $later ms after the press, O shows ${shows ?: "nothing: no pill"}")
            }
            time += 3_000
        }
        // Not once did the hover leave the finger's key: the first mark is I's, the second O's, and nothing else.
        same(bar.marks.map { it.second }, listOf(i, o), "$how: the hover never went away, ${bar.marks}")
        same(bar.picked, listOf("Save", "Favourite", "Favourited", "Unfavourite", "Unsave"), "$how: each press one step")
        same(bar.open, true, "$how: the menu stays open")
    }
    for (order in Order.entries) {
        val how = order.describe
        // A finger that moves to another key after the press takes the hover with it at once, and off the buttons ends it.
        val moved = MenuBar(state(new, true))
        moved.touch("I", 0)
        moved.pressUnderFinger("I", 1_000, order)
        moved.touch("O", 2_500)
        same(moved.pill, o to MenuKeys.Button.Unsave, "$how: moving to O hovers O's button at once")
        moved.touch("U", 2_600)
        same(moved.pill, null, "$how: moving off the buttons ends the hover")
        // A finger the touchpad shows again, then lifting: the pill goes after the plain wait, as for any lift.
        val lifted = MenuBar(state(new, false))
        lifted.touch("I", 0)
        lifted.pressUnderFinger("I", 1_000, order, back = 60)
        lifted.lift(3_000)
        same(lifted.at(3_000 + SteadyKey.LIFT_MILLIS - 1).pill, i to MenuKeys.Button.Favourite, "$how: a short lift keeps it")
        same(lifted.at(3_000 + SteadyKey.LIFT_MILLIS).pill, null, "$how: a real lift ends it")
        // A finger that lifts while the touchpad is quiet can't be seen: the pill stays until the finger lands again.
        val unseen = MenuBar(state(new, false))
        unseen.touch("I", 0)
        unseen.pressUnderFinger("I", 1_000, order)
        same(unseen.at(10_000).pill, i to MenuKeys.Button.Favourite, "$how: no touchpad event, no change")
        unseen.touch("H", 10_500)
        same(unseen.pill, null, "$how: the finger landing elsewhere ends it")
    }
    // A press with the finger resting elsewhere: the pill goes to the pressed key, where the finger is, and stays there.
    val elsewhere = MenuBar(state(saved, false))
    elsewhere.touch("U", 0)
    same(elsewhere.pill, null, "Resting on U hovers nothing")
    elsewhere.pressUnderFinger("I", 1_000, Order.Typical)
    same(elsewhere.at(3_000).pill, i to MenuKeys.Button.Favourited, "Pressing I hovers I")
    // A slow app: the touchpad's lift reached the keyboard 200 ms before the key did, so the plain wait cleared the hover first.
    // The press brings it back on the pressed key.
    val slow = MenuBar(state(new, false))
    slow.touch("I", 0)
    slow.lift(800)
    slow.press("I", 1_000)
    same(slow.at(3_000).pill, i to MenuKeys.Button.Favourite, "A late key brings the hover back")
    // Back has no hover, and closes the menu, from P or Backspace.
    for (back in listOf("P", Layout.BACKSPACE)) {
        val bar = MenuBar(state(saved, false))
        bar.touch(back, 0)
        same(bar.mark to bar.pill, -1 to null, "$back hovers nothing: Back has no pill")
        bar.pressUnderFinger(back, 1_000, Order.Typical)
        same(bar.open to bar.pill, false to null, "$back closes the menu")
        same(bar.picked, listOf("Back"), "$back picks Back")
    }
    // Forget is a step down: the menu stays open, the hover stays on O, and O has nothing left, so no pill; I shows Save.
    val forget = MenuBar(state(new, true))
    forget.touch("O", 0)
    same(forget.pill, o to MenuKeys.Button.Forget, "O shows Forget on a learned word")
    forget.pressUnderFinger("O", 1_000, Order.Typical)
    same(forget.at(3_000).let { Triple(it.open, it.mark, it.pill) }, Triple(true, o, null), "After Forget the menu stays, the hover stays on O, with no pill")
    same(forget.state, state(new, false), "not saved, nothing to forget")
    forget.touch("I", 3_500)
    same(forget.pill, i to MenuKeys.Button.Save, "and I is Save")
    // At the bottom of an unknown word, O has no button: its key picks nothing, so it types and closes the menu, as any key does.
    val bottom = MenuBar(state(new, false))
    bottom.touch("O", 0)
    same(bottom.pill, null, "O at the bottom has no pill")
    bottom.press("O", 1_000)
    same(bottom.open to bottom.picked, false to listOf("types O"), "O with no button types, and the menu closes")
    // A hover with no press goes after the plain wait, as before.
    val plain = MenuBar(state(new, false))
    plain.touch("I", 0)
    plain.lift(1_000)
    same(plain.at(1_000 + SteadyKey.LIFT_MILLIS - 1).pill, i to MenuKeys.Button.Save, "A short lift keeps the pill")
    same(plain.at(1_000 + SteadyKey.LIFT_MILLIS).pill, null, "A real lift ends it after the usual wait")
}

/**
 * [MenuBar] makes AtlasIme's calls in AtlasIme's order. These are the lines it copies; a change to one of them must be made in
 * [MenuBar] too, or this check fails.
 */
private fun theKeyboardMakesTheseCalls() {
    val ime = File("android/kotlin/com/atlas/keyboard/AtlasIme.kt").readText()
    val typing = File("android/kotlin/com/atlas/engine/SwipeTyping.java").readText()
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
        "learned = pending == null && key !in forgetting && typing.learnedWord(word),",
        "blocked = key in forgetting || pending == null && !typing.wordAllowed(word),",
        "if (column == MenuKeys.SAVE || column == MenuKeys.FORGET) column else -1",
        "val next = MenuKeys.press(menu.state, button)",
        "menuPressed(held, MenuKeys.Button.Forget)",
    )) check(line in ime) { "AtlasIme no longer has: $line" }
    for (gone in listOf("Strip.Notice.Forgot(", "pendingForget", "FORGET_UNDO_MILLIS")) check(gone !in ime) { "Forget has no Undo now: $gone" }
    for (line in listOf(
        "learnedEntries = dictionary.learnedWords(day);",
        "typedEntries = dictionary.typedWords();",
        "public boolean learnedWord(String word) { return learnedEntries.contains(SwipeWords.normalise(word)); }",
        "public boolean savedWord(String word) { return addedWord(word) || typedEntries.contains(SwipeWords.normalise(word)); }",
    )) check(line in typing) { "SwipeTyping no longer has: $line" }
    // The strip keeps the hover through a new state: a new menu notice for the same word never resets the mark or the pill.
    val strip = File("android/kotlin/com/atlas/keyboard/Strip.kt").readText()
    val showNotice = strip.substringAfter("private fun showNotice(").substringBefore("\n    private var menuIn")
    check("menuMark" !in showNotice) { "A new notice must not touch the hover's mark" }
    check("pillShown = -1" in showNotice.substringAfter("if (menu == null) {").substringBefore("} else if")) {
        "Only a closed menu lets go of the pill"
    }
    check("pillShown" !in showNotice.substringAfter("} else if")) {
        "A new step keeps the pill"
    }
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}
