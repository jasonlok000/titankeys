package com.atlas.keyboard

import com.atlas.keyboard.KeyboardEvent.Chosen
import com.atlas.keyboard.KeyboardEvent.Copied
import com.atlas.keyboard.KeyboardEvent.CursorStarted
import com.atlas.keyboard.KeyboardEvent.DeleteHoldStarted
import com.atlas.keyboard.KeyboardEvent.DoubleTapped
import com.atlas.keyboard.KeyboardEvent.EmptyKey
import com.atlas.keyboard.KeyboardEvent.Released
import com.atlas.keyboard.KeyboardEvent.TwoTapsAbove
import com.atlas.keyboard.KeyboardEvent.Moved
import com.atlas.keyboard.KeyboardEvent.DeletedWord
import com.atlas.keyboard.KeyboardEvent.FieldChanged
import com.atlas.keyboard.KeyboardEvent.GroupPicked
import com.atlas.keyboard.KeyboardEvent.MenuPicked
import com.atlas.keyboard.KeyboardEvent.PanelSwiped
import com.atlas.keyboard.KeyboardEvent.Resting
import com.atlas.keyboard.KeyboardEvent.HeldSymbol
import com.atlas.keyboard.KeyboardEvent.ModifierState
import com.atlas.keyboard.KeyboardEvent.Pushed
import com.atlas.keyboard.KeyboardEvent.Recognised
import com.atlas.keyboard.KeyboardEvent.Tapped
import com.atlas.keyboard.KeyboardEvent.WordMenu
import com.atlas.keyboard.Modifiers.Level
import com.atlas.keyboard.Modifiers.Panel
import kotlin.math.abs

/** The background of the guide: ink while you try a step, and orange once you have done it. */
internal enum class GuideTone { Orange, Ink }

/**
 * One chapter of the guide, with the [label] that shows above its slides. Each chapter goes from what you already know, to how
 * TitanKeys does it, to the more advanced stuff, one small slide at a time. The bar at the top shows the chapters, and how far into
 * this one you are.
 */
internal enum class GuideChapter(val label: String, val roast: String? = null) {
    Typing("Typing", "OK, you're not that dumb."),
    Delete("Delete", "Fine, you're not useless. You delete stuff almost as fast as you screw it up."),
    Steering("Steering", "Wow, you moved a cursor. The bar was on the floor and you still needed a run-up."),
    Symbols("Symbols and emoji",
        "Holy shit, you found the clown. Took you long enough, considering you see it in the mirror every day."),
    Clipboard("Copy and paste",
        "You can copy and paste now. Congrats, you've unlocked the skills of every plagiarist who ever failed a class."),
    /** The name, the two big buttons, and the one boring thing: the offer to share swipes, until it has an answer. */
    End("Built for Titan 2 Elite"),
}

/**
 * Slide zero: the parts of setup that only the phone's Settings can change, in the order that the guide asks for them. The guide
 * opens the page for each part, sees the switch go on, and comes back by itself, so that each part takes a touch or two.
 * [title] and [body] follow the same rules as a step's words. [help] shows if you come back with the part still not done.
 */
internal enum class SetupPart(val title: String, val body: String, val action: String, val help: String? = null) {
    Enable("Turn the keyboard on.", "Flip TitanKeys on in the list. You'll land back here by yourself.", "Open the keyboard list",
        "Can't see it? Scroll down. Flip its switch, then press OK twice."),
    Choose("Make it your keyboard.", "Pick TitanKeys in the list. That's it.", "Pick TitanKeys"),
    // Android shows a list of all apps here, not the page for TitanKeys alone.
    Overlay("Let it sit over other apps.", "Allow TitanKeys in the list. It keeps the touchpad on the keys.", "Open the list",
        "Find TitanKeys in the list, open it and flip the switch."),
    ScrollAssistant("Turn on Scroll assistant.", "Swiping reads the touchpad through it. Flip its switch on.", "Open Scroll assistant",
        "The switch is at the top of that page."),
    Back("Let TitanKeys handle Back.", "Turn TitanKeys on in Accessibility. Then Back works in one press.", "Open Accessibility",
        "Switch greyed out? Android is being careful. In App info, open the menu and allow restricted settings."),
    ;

    companion object {
        /** The line above setup's title, as a chapter's label. */
        const val LABEL = "Quick setup"
        /** The line that lands when a part is done, and when all of them are. */
        const val DONE = "Done."
        const val ALL_DONE = "All set."
    }
}

/** What the drawing of the keys shows as a ghost finger, so that the screen says what to do without words. */
internal sealed interface GuideCue {
    /** No ghost. The keys only light up under your own finger. */
    data object Free : GuideCue
    /** A finger goes through the letters of a word. */
    data class Word(val word: String) : GuideCue
    /** A finger goes through the next word of [GuideScript], which changes as you type. */
    data object NextWord : GuideCue
    /** A finger flicks left from Backspace, and stays there if [hold] is true. */
    data class Flick(val hold: Boolean) : GuideCue
    /**
     * These keys light one after the other, slowly, a few times, then stop. Keys in [held] stay lit, steady, as a modifier held
     * down does: never flashing.
     */
    data class Spot(val keys: List<String>, val held: List<String> = emptyList(), val rounds: Int = 3) : GuideCue
    /** The keys of the word that the ghost has next light in order, then Space, once: the screen fills in which keys. */
    data object Spell : GuideCue
    /** A finger goes along the bottom row. */
    data object SlideRow : GuideCue
    /** A finger taps the bottom row, then if [drag] is true stays down, swipes up and leans to one side. */
    data class Taps(val count: Int, val drag: Boolean) : GuideCue
}

/**
 * What a slide shows by itself as soon as it opens, and again when nobody acts for a while: slowly, in grey over the field, without
 * typing anything.
 * The drawing of the keys acts it out at the same time.
 */
internal sealed interface GuideDemo {
    /** The letters of the next word appear one by one, as if pushed, then its punctuation as the keyboard types it. */
    data object TypeNext : GuideDemo
    /** The next word appears whole, at the end of the ghost swipe, then its punctuation as the keyboard types it. */
    data object SwipeNext : GuideDemo
    /** The next word appears with a slip in it, then the right word takes its place, as a touch on the strip does. */
    data object FixNext : GuideDemo
    /** The last word fades away. */
    data object DeleteLast : GuideDemo
    /** The cursor walks back through the last word. */
    data object MoveCursor : GuideDemo
    /** The last word is selected. */
    data object SelectLast : GuideDemo
    /** A letter appears from a key that stays down, then becomes the symbol in that key's corner. */
    data class HoldLetter(val key: String) : GuideDemo
    /** These keys light one after the other, then [text] appears, as from the Sym panel. */
    data class Insert(val keys: List<String>, val text: String) : GuideDemo
    /** The next word is typed with a slip in it, then Space lights and the right word takes its place, as autocorrect does. */
    data object TypeSlip : GuideDemo
    /** Alt lights, then [key], and the symbol in that key's corner appears. */
    data class AltLetter(val key: String) : GuideDemo
    /** The last word is selected, then fn and C light, as a copy does. */
    data object CopyLast : GuideDemo
}

/**
 * What a slide shows on the keyboard's own bar at the same time as its demonstration, so that the bar itself shows what to do.
 * See [GuideHooks.stripDemo].
 */
internal enum class GuideBar {
    /** A word with a slip, then the fix swaps in, as Space does. */
    Fix,
    /** A ghost finger holds the middle word, and its menu opens. */
    Menu,
}

/**
 * The text that the guide suggests you type, written out in the line above the field. It is one paragraph about TitanKeys, and the
 * whole guide is one text in one field: each word that you swipe or type moves the line on to the next word, whatever the word was,
 * so that nothing waits on one word. After the last word it starts again.
 */
internal object GuideScript {
    const val TEXT = "Nothing to unlearn: type with two hands or one, and TitanKeys fixes your typos as you go. Then swipe whole " +
        "words on the same keys. Missed one? The right word is a tap away. Flick to delete, steer and pick from Space, and " +
        "get every symbol and emoji without the screen. This is the ultimate keyboard, and these are the keys your Titan " +
        "deserves."

    val words = TEXT.split(' ')

    /** The letters of a word that a swipe goes through. */
    fun letters(word: String) = word.filter { it.isLetter() }.lowercase()

    /**
     * The script in steps: each word, and after a word that ends in marks, such as "unlearn:" or "go.", those marks as a step of
     * their own, so that the line above waits for them and you get to type them.
     */
    val steps = words.flatMap { word ->
        val marks = word.takeLastWhile { !it.isLetterOrDigit() }
        listOfNotNull(word.dropLast(marks.length).takeIf { it.isNotEmpty() }, marks.takeIf { it.isNotEmpty() })
    }

    /** The step at [index], starting again after the end. */
    fun stepAt(index: Int) = steps[index.mod(steps.size)]

    /** Whether a step is marks to type, and not a word. */
    fun isMarks(step: String) = step.isNotEmpty() && !step[0].isLetterOrDigit()

    /** The word that comes next from step [index] on: the step itself, or the word after the marks. */
    fun wordFrom(index: Int) = stepAt(index).takeUnless(::isMarks) ?: stepAt(index + 1)

    /**
     * How many steps of the script [text] has. It is lenient, so that nothing waits on you: each word counts, also one that came
     * out different, and a word typed where marks are next counts for the marks too. A mark where a word is next is passed over.
     * A last word that is still being typed, the start of the word that is next, does not count until it is whole.
     */
    fun progress(text: String): Int {
        val pieces = PIECE.findAll(text).map { it.value }.toList()
        var done = 0
        for (piece in pieces) {
            if (isMarks(piece)) {
                if (isMarks(stepAt(done))) done++
                continue
            }
            if (isMarks(stepAt(done))) done++
            done++
        }
        val last = pieces.lastOrNull()
        if (last != null && !isMarks(last) && !text.endsWith(' ') && !text.endsWith('\n')) {
            val typed = letters(last)
            val expected = letters(stepAt(done - 1))
            if (typed != expected && expected.startsWith(typed)) done--
        }
        return done
    }

    /** The steps from [index] on for the line above: words with a space between them, and marks right after their word. */
    fun lineFrom(index: Int, count: Int) = buildString {
        for (at in index until index + count) {
            val step = stepAt(at)
            if (isNotEmpty() && !isMarks(step)) append(' ')
            append(step)
        }
    }

    /** The keys that type marks, in order: two pushes of Space for a full stop when that is on, and Alt and its key for others. */
    fun markKeys(marks: String, autoPunctuation: Boolean, keyFor: (Char) -> String?): List<List<String>> = marks.map { mark ->
        if (mark == '.' && autoPunctuation) listOf(Layout.SPACE, Layout.SPACE)
        else listOfNotNull(keyFor(mark)?.let { Layout.ALT }, keyFor(mark))
    }.flatMap { keys -> keys.map { listOf(it) } }

    /**
     * The pushes that put [word] in the field as the keyboard really does (#59). Typed, each letter is a push; swiped, the
     * word lands at once with the space that a swipe adds. The marks after it come next: with [autoPunctuation] on, a full
     * stop is two pushes of Space, and any other mark is Alt and the key that [keyFor] says has that mark, or nothing lit if
     * no key has it. A mark after a swiped word takes the place of its space, as on the keyboard.
     */
    fun pushes(word: String, swiped: Boolean, autoPunctuation: Boolean, keyFor: (Char) -> String?): List<DemoPush> {
        val marks = word.takeLastWhile { !it.isLetterOrDigit() }
        val base = word.dropLast(marks.length)
        val pushes = ArrayList<DemoPush>()
        if (swiped) pushes += DemoPush(emptyList(), "$base ")
        else for (i in 1..base.length) pushes += DemoPush(listOfNotNull(base[i - 1].takeIf { it.isLetter() }?.uppercase()), base.take(i))
        var text = pushes.lastOrNull()?.text ?: ""
        for (mark in marks) {
            if (mark == '.' && autoPunctuation) {
                // The first Space after a swipe only arms the full stop: the swipe's space is already there.
                text = if (text.endsWith(' ')) text else "$text "
                pushes += DemoPush(listOf(Layout.SPACE), text)
                text = text.trimEnd() + ". "
                pushes += DemoPush(listOf(Layout.SPACE), text)
                continue
            }
            val key = keyFor(mark)
            if (key != null) pushes += DemoPush(listOf(Layout.ALT), text)
            text = text.trimEnd(' ') + mark
            pushes += DemoPush(listOfNotNull(key), text)
        }
        return pushes
    }

    /** The word at [index], starting again after the end. */
    fun wordAt(index: Int) = words[index.mod(words.size)]

    /** [word] with a slip in it, two letters the wrong way round, for the demonstration of the strip. */
    fun slip(word: String): String {
        val at = word.indexOfFirst { it.isLetter() } + 1
        if (at <= 0 || at + 1 >= word.length || !word[at].isLetter() || !word[at + 1].isLetter() || word[at] == word[at + 1])
            return word + word.last()
        return word.substring(0, at) + word[at + 1] + word[at] + word.substring(at + 2)
    }

    /**
     * The script from step [index] on, at least [count] words and to the end of a sentence, to put in the field for a step that
     * needs text to work on. It goes on from where the text is, so that the line above stays in step with the field. Marks that
     * are next are passed over, as the text has a space after the word by then.
     */
    fun continueFrom(index: Int, count: Int): String {
        var at = index
        while (isMarks(stepAt(at))) at++
        val start = at
        var added = 0
        while (added < count || stepAt(at - 1) != ".") if (!isMarks(stepAt(at++))) added++
        return lineFrom(start, at - start)
    }

    /** Where each step is in [TEXT], from its first character to after its last, in the order of [steps]. */
    val ranges: List<IntRange> = buildList {
        var at = 0
        for (word in words) {
            val marks = word.takeLastWhile { !it.isLetterOrDigit() }
            val base = word.length - marks.length
            if (base > 0) add(at until at + base)
            if (marks.isNotEmpty()) add(at + base until at + word.length)
            at += word.length + 1
        }
    }

    /**
     * Where typing is in the script, for the passage above the field, as a typing race shows it: [next] is the step being typed
     * now, counted on from the start like [progress], [wrong] the steps before it that came out different, and [typed] the
     * letters of the word being typed, while it is not finished.
     */
    class Track(val next: Int, val wrong: Set<Int>, val typed: String)

    fun track(text: String): Track {
        val pieces = PIECE.findAll(text).map { it.value }.toList()
        val open = text.isNotEmpty() && !text.endsWith(' ') && !text.endsWith('\n')
        var done = 0
        val wrong = HashSet<Int>()
        for ((index, piece) in pieces.withIndex()) {
            if (isMarks(piece)) {
                if (isMarks(stepAt(done))) {
                    if (piece != stepAt(done)) wrong += done
                    done++
                }
                continue
            }
            // A word where marks were next leaves the marks out.
            if (isMarks(stepAt(done))) {
                wrong += done
                done++
            }
            // The last word, still being typed, is the word in progress, right or wrong so far.
            if (index == pieces.lastIndex && open) return Track(done, wrong, letters(piece))
            if (letters(piece) != letters(stepAt(done))) wrong += done
            done++
        }
        return Track(done, wrong, "")
    }

    /** A word, or a run of marks, in typed text. */
    private val PIECE = Regex("[\\p{L}\\p{N}'’]+|[^\\s\\p{L}\\p{N}'’]+")
}

/**
 * A text to type along to, in its own box above the field: each slide has its own, about what the slide teaches, and following the
 * instructions perfectly means typing it. It starts again after its end. Words are matched by their letters only, so marks and
 * capitals never hold anyone up, and a wrong word is skipped: see [track].
 */
internal class Passage(lead: String, loop: String = lead) {
    /**
     * The whole run of text: [lead], then [loop] again and again, so that the text never starts over in the box but goes on in
     * new lines. A slide's text is its own lead and loop; once the slide is done, its after text follows on from the line you're on.
     */
    val text: String = buildString {
        append(lead.trim())
        val more = loop.trim()
        var count = lead.split(' ').count { it.isNotEmpty() }
        val step = more.split(' ').count { it.isNotEmpty() }
        // Each time round carries straight on from the end, with no mark and no new line, as if the text went on forever.
        while (step > 0 && count < MIN_WORDS) {
            if (isNotEmpty()) append(' ')
            append(more)
            count += step
        }
    }
    val words: List<String> = text.split(' ').filter { it.isNotEmpty() }

    /** Where each word is in [text], from its first character to after its last. */
    val ranges: List<IntRange> = buildList {
        var at = 0
        for (word in words) {
            at = text.indexOf(word, at)
            add(at until at + word.length)
            at += word.length
        }
    }

    fun wordAt(index: Int) = words[index.mod(words.size)]

    /**
     * Where typing is: [next] is the word to type now, counted on past the end as the text starts again, [done] the words typed
     * right, and [typed] the letters of the word being typed while it is not finished.
     */
    class Track(val next: Int, val done: Set<Int>, val typed: String)

    /**
     * Follows [typed], the text typed since the passage began. A word that matches the next one is done. A word that matches one of
     * the few after it is done there, and the ones before it are skipped. Any other word is ignored, so a slip never gets anyone
     * stuck: for "type with two hands", "type with t two" leaves "hands" next.
     */
    fun track(typed: String): Track {
        val pieces = WORD.findAll(typed).map { it.value }.toList()
        val open = typed.isNotEmpty() && !typed.last().isWhitespace()
        var next = 0
        val done = HashSet<Int>()
        // The dot between rounds has nothing to type, so it is passed over by itself.
        fun pass() {
            while (GuideScript.letters(wordAt(next)).isEmpty() && next < words.size * 2) done += next++
        }
        pass()
        for ((index, piece) in pieces.withIndex()) {
            val letters = GuideScript.letters(piece)
            if (letters.isEmpty()) continue
            if (index == pieces.lastIndex && open) return Track(next, done, letters)
            val ahead = (0..LOOK_AHEAD).firstOrNull { GuideScript.letters(wordAt(next + it)) == letters } ?: continue
            done += next + ahead
            next += ahead + 1
            pass()
        }
        return Track(next, done, "")
    }

    private companion object {
        /** How many words past the next one a typed word may match. */
        const val LOOK_AHEAD = 3
        /** At least this many words in a run, far more than anyone types on one slide. */
        const val MIN_WORDS = 400
        val WORD = Regex("[\\p{L}\\p{N}'’]+")
    }
}

/**
 * The playground's own text, made for speed: short, common words, no capitals and no marks. Now and then, once you're going, it
 * slips in "i am stupid", to see if you're paying attention.
 */
internal object PlaygroundText {
    private val SENTENCES = listOf(
        "titankeys fixes your typos so you can keep pretending you never make any",
        "swipe a word and lift and titankeys types it before you even look",
        "flick left from backspace and the whole word is gone like it never happened",
        "two taps on space and the cursor goes wherever you slide it",
        "hold a letter and titankeys gives you the symbol in its corner",
        "the screen is for looking at and the keys are for everything else",
        "titankeys was built for the titan and the titan deserves it",
        "nobody reads the manual but you swiped through the whole thing",
        "type fast and do not look at your hands they know the way",
        "every word you type here is one titankeys did not have to fix",
    )
    const val SLIP = "i am stupid"
    /** The slip comes no sooner than this many words in, and then every so often. */
    const val SLIP_FIRST = 30
    const val SLIP_GAP_MIN = 35
    const val SLIP_GAP_MAX = 70

    /** A long text in a random order, about [count] words, with the slip in a few places. */
    fun make(random: kotlin.random.Random, count: Int = 600): String {
        val words = ArrayList<String>()
        var slipAt = SLIP_FIRST + random.nextInt(SLIP_GAP_MAX - SLIP_GAP_MIN)
        while (words.size < count) {
            words += SENTENCES[random.nextInt(SENTENCES.size)].split(' ')
            if (words.size >= slipAt) {
                words += SLIP.split(' ')
                slipAt = words.size + SLIP_GAP_MIN + random.nextInt(SLIP_GAP_MAX - SLIP_GAP_MIN)
            }
        }
        return words.joinToString(" ")
    }
}

/** One push in a demonstration: the keys that light, and the text that the word has become after it. */
internal data class DemoPush(val keys: List<String>, val text: String)

/** A setting that a step needs. If it is off, the step offers to turn it on. */
internal enum class GuideNeed(val offer: String, val optional: Boolean = false) {
    HoldSymbols("Turn on Hold a letter for its symbol"),
    CursorControl("Turn on Space taps move the cursor"),
    SwipeTyping("Turn on Swipe to type words"),
    FnAsCtrl("Turn it on"),
}

/** The settings that change which steps show. */
internal data class GuideSettings(
    val holdSymbols: Boolean = false,
    val cursorControl: Boolean = true,
    val swipeTyping: Boolean = true,
    val fnAsCtrl: Boolean = false,
    /** The Sym panel is on the screen now. */
    val panelOpen: Boolean = false,
    /** The offer to share swipes has no answer yet, so its slide shows. */
    val shareUnanswered: Boolean = false,
) {
    fun has(need: GuideNeed) = when (need) {
        GuideNeed.HoldSymbols -> holdSymbols
        GuideNeed.CursorControl -> cursorControl
        GuideNeed.SwipeTyping -> swipeTyping
        GuideNeed.FnAsCtrl -> fnAsCtrl
    }
}

/** What a step makes of an event. */
internal sealed interface GuideVerdict {
    data object Wait : GuideVerdict
    /**
     * A scenario: something happened that the slide has words for. With a [title] it takes the place of the slide's title, and [text]
     * of its instruction, until the user is back on track; without one, [text] shows as a hint. [stage] moves the slide to that stage
     * too. [passes] means it counts: the slide is done, with [title] as the line that lands and [text] under it.
     */
    data class Hint(val text: String, val title: String? = null, val stage: Int? = null, val passes: Boolean = false) : GuideVerdict
    /** A stage's check: this stage is done, so the slide shows the next instruction, or is done after its last one. */
    data object Done : GuideVerdict
    /** The slide moved to the stage at [index], forwards or back, so that it shows only the instruction needed now. */
    data class Stage(val index: Int) : GuideVerdict
    /** A stage's check: the whole slide is done, whatever stage it was at. */
    data object Finished : GuideVerdict
}

/** A step's own memory. Each step starts with a new one. */
internal class GuideMemory {
    var count = 0
    var armed = false
    var mark = -1
    /** The stage that the slide is at. */
    var stage = 0
    /** How long the field's text was when last heard of, and when this stage began. -1 until it is known. */
    var length = -1
    var stageLength = -1
    /** The letters of the word that the text box has next, as the event happened. */
    var expected = ""
    /** A slide that this one makes unneeded, by its id: the guide passes over it. */
    var skip: String? = null
    /** The slide is done in a way that also does the next slide: the guide shows that one done. */
    var jump = false
    /** The Sym panel is open, as far as this slide has heard. */
    var panelOpen = false
    /** What the slide has seen used, across its stages, such as the ways the groups slide turned pages. */
    var used = 0
    /** The text in the field that the slide marks in orange as the thing to aim at, if any. The screen sets it. */
    var target: IntRange? = null
    /** Where the cursor was before this event, or -1 until it is known. */
    var cursor = -1

    /** The field's text is longer than when this stage began: something was typed or pasted. */
    fun grew(event: KeyboardEvent) = event is FieldChanged && stageLength >= 0 && event.text.length > stageLength
}

/**
 * One instruction of a slide. A slide teaches one thing at a time: it shows only the instruction that is needed now, and moves on,
 * or back, as the keyboard changes. [cue] replaces the slide's own ghost while this instruction shows.
 */
internal class GuideStage(
    val text: String,
    val cue: GuideCue? = null,
    /** What the drawing of the keys shows on some keys while this instruction shows, in place of their letters; the rest dim. */
    val labels: Map<String, String>? = null,
    /** What the keyboard lets through while this instruction shows, in place of the slide's own [GuideStep.lessons]. */
    val lessons: Set<GuideLesson>? = null,
    val check: GuideMemory.(KeyboardEvent) -> GuideVerdict,
)

/** The check of a slide made of [stages]: each stage's check runs while it shows, and Done moves to the next stage. */
private fun staged(stages: List<GuideStage>): GuideMemory.(KeyboardEvent) -> GuideVerdict = check@{ event ->
    if (stages.isEmpty()) return@check GuideVerdict.Wait
    if (event is ModifierState) panelOpen = event.panel != Panel.Closed
    val verdict = stages[stage.coerceIn(0, stages.lastIndex)].check(this, event)
    if (event is FieldChanged) {
        length = event.text.length
        cursor = event.start
    }
    fun move(to: Int): GuideVerdict {
        stage = to
        stageLength = length
        count = 0
        armed = false
        mark = -1
        return GuideVerdict.Stage(to)
    }
    when {
        verdict == GuideVerdict.Finished -> GuideVerdict.Done
        verdict == GuideVerdict.Done && stage < stages.lastIndex -> move(stage + 1)
        verdict is GuideVerdict.Stage -> if (verdict.index == stage) GuideVerdict.Wait else move(verdict.index.coerceIn(0, stages.lastIndex))
        verdict is GuideVerdict.Hint && !verdict.passes && verdict.stage != null -> {
            if (verdict.stage != stage) move(verdict.stage.coerceIn(0, stages.lastIndex))
            verdict
        }
        else -> verdict
    }
}

/**
 * One slide. The words and the check are in one place, so that a gesture that changes is easy to change here.
 * [title], [body] and [done] are what the user reads: the owner's approved text (v3.1).
 */
internal class GuideStep(
    val id: String,
    val chapter: GuideChapter,
    val title: String,
    /** The instructions, one at a time. A slide without stages shows [body] and has its own [check]. */
    val stages: List<GuideStage> = emptyList(),
    /** What the slide says first: its first instruction, unless it has no stages. */
    val body: String = stages.firstOrNull()?.text.orEmpty(),
    /** A short line that lands when the step is done. The step then stays, so you can try it more, until you push Next. */
    val done: String,
    /** Shows after a time with no success: as the title, with [help] as the instruction, if [helpTitle] is set. */
    val help: String? = null,
    val helpTitle: String? = null,
    val cue: GuideCue = GuideCue.Free,
    /** The setting that this step needs. */
    val needs: GuideNeed? = null,
    /** The step does not show when this is true. */
    val skipWhen: (GuideSettings) -> Boolean = { false },
    /** The step works with the Sym panel open, so an open panel is no mistake here. */
    val usesPanel: Boolean = false,
    /**
     * The menu that the keyboard is put in when the step starts, so that the step starts in the right place: the Sym panel for
     * the steps that need it, closed for the rest. Null leaves a step that uses the panel as it is.
     */
    val setup: Panel? = if (usesPanel) null else Panel.Closed,
    /** The step works on text, so it needs at least this many words in the field. */
    val needsWords: Int = 0,
    /** A stop between the parts of the guide: it counts as done at once, and offers to start typing. */
    val pause: Boolean = false,
    /** A slide that only tells you something: it counts as done at once, quietly, with nothing to try. */
    val info: Boolean = false,
    /** The keyboard makes the next swipe its second guess, on purpose, so that the step can teach the strip. See [GuideHooks]. */
    val nearMiss: Boolean = false,
    /** What the slide shows by itself when it opens and when nobody acts. */
    val demo: GuideDemo? = null,
    /** What the keyboard's own bar acts out at the same time. */
    val bar: GuideBar? = null,
    /** Text that the slide puts in the field to practise on, once, if the field does not have it yet. */
    val passage: String? = null,
    /** Text that the slide puts on the clipboard as it opens, to paste. */
    val clip: String? = null,
    /** The slide starts with [PASTE_OVER] selected, so that what you paste goes in its place, if nothing is selected yet. */
    val preselect: Boolean = false,
    /**
     * The clips that the keyboard's clipboard shows while this slide is open, in place of the user's own: a sandbox, never saved,
     * never pinned, gone when the slide is. Empty shows the user's own.
     */
    val sandboxClips: List<String> = emptyList(),
    /** What the slide rains down once it is done, if anything. */
    val rain: String? = null,
    /**
     * The chapter's text box, the same on each of its slides. On the typing slides it's the text to type along to, and on the
     * symbol slides it shows which symbol is next. See [Passage].
     */
    val text: String = "",
    val after: String = "",
    /** What the field starts with on this slide, or nothing. A chapter with one text box carries its field from slide to slide. */
    val seed: String = "",
    /**
     * The symbol that this slide waits for, on the symbols chapter: once it's typed the guide types the rest of its sentence, then
     * the slide is done. [ANY_SYMBOL] takes any.
     */
    val symbol: String? = null,
    /** Where the cursor starts in the field that the slide seeds, from its text, or null for the end. */
    val cursorAt: ((String) -> Int)? = null,
    /** A line that shows in place of the instruction while the slide is tried, when it says something, such as a count. */
    val live: (GuideMemory.() -> String?)? = null,
    /** What the field's grey ghost shows ahead of the cursor in place of [text], word for word, such as a typo in every word. */
    val ghost: String = "",
    /** What this slide unlocks on the keyboard, in the guide's field only, besides letters, Backspace and Space. Null locks nothing. */
    val lessons: Set<GuideLesson>? = null,
    /** The bar shows its side words. False shows only the middle one. */
    val sides: Boolean = true,
    /** What the line under the title says once the slide is done: about what you just did, never the next instruction. */
    val praise: String = "",
    /** The slide offers to share swipes: its offer opens the disclosure, and an answer finishes it. */
    val offersShare: Boolean = false,
    /**
     * The line that the slide has you finish (v5): a real takeaway, as it reads once done. The summary at the end stacks them up,
     * and their first letters spell [ACROSTIC].
     */
    val line: String = "",
    /** What a slide with something to find says as time goes: see [Hunt]. */
    val hunt: Hunt? = null,
    /** Any word typed becomes the word the line has next, so there's no way to fail: see [GuideHooks.forceFix]. */
    val forceFix: Boolean = false,
    val check: GuideMemory.(KeyboardEvent) -> GuideVerdict = staged(stages),
) {
    /** Each slide has its own line and its own field (v5): nothing carries on from the slide before. */
    val carriesField get() = false
}

/** From android.view.KeyEvent, so that the steps can be tested without Android. */
private fun isLetter(code: Int) = code in 29..54
private const val KEY_DEL = 67
private const val KEY_SPACE = 62
private const val KEY_ENTER = 66
/** Left and right Alt. */
private val ALT_KEYS = setOf(57, 58)
/** A steer that moves the cursor this far is no nudge. */
private const val CURSOR_TRAVEL = 3

private val wait: GuideMemory.(KeyboardEvent) -> GuideVerdict = { GuideVerdict.Wait }

private fun panel(event: KeyboardEvent, panel: Panel) = event is ModifierState && event.panel == panel

/** A swipe or a touch that showed another group of the panel's symbols or emoji. */
private fun otherGroup(event: KeyboardEvent) = event is GroupPicked || event is PanelSwiped && event.moved && abs(event.across) > abs(event.down)

/** A scenario: [title] and [text] take the slide's place until the user is back on track. [stage] moves the slide too. */
internal fun roast(title: String, text: String, stage: Int? = null) = GuideVerdict.Hint(text, title, stage)

/** A chip over the instruction, for a real mistake: the slide's own title and instruction stay. */
internal fun chip(text: String) = GuideVerdict.Hint(text)

/** A scenario that counts: the slide is done, with [title] as the line that lands and [text] under it. */
internal fun counts(title: String, text: String) = GuideVerdict.Hint(text, title, passes = true)

/**
 * Every slide's line (guide v5): one short sentence that the slide has you finish, and that is a real takeaway. At the end they
 * stack up as the summary you wrote yourself, and their first letters spell [ACROSTIC]. See [GuideStep.line].
 */
internal const val ACROSTIC = "SO I READ THE MANUAL"

/** Slide 1's line, and the typo in each of its words that the ghost shows. Any word typed for one becomes it: see [GuideHooks.forceFix]. */
internal const val TYPE_LINE = "Space fixes my typos"
internal const val TYPE_TYPOS = "Sapce fxies ym tyops"

/** Slide 2's line. The first word typed right comes out wrong on purpose: see [GuideStep.nearMiss]. */
internal const val BAR_LINE = "Other guesses wait on the bar"

/** Slide 3's line: its first word is in already, and each word after it is one swipe. */
internal const val SWIPE_LEAD = "I "
internal const val SWIPE_LINE = "I swipe whole words"

/** Slide 4's line, in the field already, so the bar has words to hold. */
internal const val MENU_LINE = "Remember or forget any word on the bar"

/** Slide 5's line, and the field it starts with: one e too many, right before the cursor. */
internal const val BACKSPACE_LINE = "Extra letters go with Backspace"
internal const val BACKSPACE_SEED = BACKSPACE_LINE + "e"

/** Slide 6's line, and the field it starts with: the word to flick, right before the cursor. */
internal const val FLICK_LINE = "A flick deletes a word"
internal const val FLICK_WORD = "stupid"
internal const val FLICK_SEED = "A flick deletes a $FLICK_WORD word"

/** Where the flick slide's cursor starts: right after its stupid word. */
internal fun flickCursor(text: String) = text.indexOf(FLICK_WORD).let { if (it < 0) text.length else it + FLICK_WORD.length }

/** The flick slide's word to go, in orange, while it's there. */
internal fun flickTarget(text: String) = text.indexOf(FLICK_WORD).takeIf { it >= 0 }?.let { it until it + FLICK_WORD.length }

/** Slide 7's line, and the field it starts with: regrets in the middle of it, the cursor right after them. */
internal const val HOLD_LINE = "Delete all of it with one held flick"
internal const val HOLD_REGRETS = "typos, rants, reply-alls, 2am texts, drunk tweets, exes, that tattoo, "
internal const val HOLD_SEED = "Delete " + HOLD_REGRETS + "all of it with one held flick"

/** Where the hold slide's cursor starts: right after the regrets. */
internal fun holdCursor(text: String) = text.indexOf(HOLD_REGRETS).let { if (it < 0) text.length else it + HOLD_REGRETS.length }

/** The regrets, in orange, while any of them are there: from after "Delete " to the cursor. */
internal fun holdTarget(text: String, cursor: Int): IntRange? {
    val from = "Delete ".length
    if (!text.startsWith("Delete ") || cursor <= from) return null
    val end = text.substring(0, cursor.coerceAtMost(text.length)).trimEnd().length
    return if (end > from) from until end else null
}

/** Slide 8's line, which goes on and on in the field, so that the cursor never meets an end. */
internal const val STEER_LINE = "Two taps on Space steer the cursor"

/** Slide 9's line, on and on as well, with the words to grab in it. */
internal const val GRAB_LINE = "Hold the third tap to grab my whole heart"

/** Slides 10 to 14: each line is in the field, with a gap where the slide's symbol goes. The cursor waits in the gap. */
internal const val ALT_SYMBOL = "*"
internal const val ALT_LINE = "Even the fine print is Alt and A*"
internal const val HOLD_SYMBOL_LINE = "Make symbols by holding keys: me@titan"
internal const val SYM_LINE = "All symbols are in Sym, terms & conditions apply"
internal const val SECTION = "§"
internal const val GROUPS_LINE = "Need weird ones? Swipe up in Sym. See § 4.2"
internal const val CLOWN = "🤡"
internal const val CLOWN_LINE = "Use Sym twice for emoji. Signed, 🤡"

/** A line with its [symbol] taken out: the text before the gap, and after it. A gap between two spaces keeps both. */
internal fun gapped(line: String, symbol: String): Pair<String, String> {
    val at = line.indexOf(symbol)
    return line.substring(0, at) to line.substring(at + symbol.length)
}

/** What the field starts with on a symbol slide: its line with the gap, and where the cursor waits, in the gap. */
internal fun gapSeed(line: String, symbol: String) = gapped(line, symbol).let { (before, after) -> before + after }
internal fun gapCursor(line: String, symbol: String) = gapped(line, symbol).first.length

/** The symbol is in its gap: the field reads the whole line, give or take spaces at its ends. */
internal fun filledGap(text: String, line: String) = text.trim() == line

/** A symbol typed in the gap that isn't the slide's: the field is the line with something else where the symbol goes. */
internal fun wrongInGap(text: String, line: String, symbol: String): Boolean {
    val (before, after) = gapped(line, symbol)
    val trimmed = text.trimEnd()
    val end = after.trimEnd()
    return trimmed.startsWith(before) && trimmed.endsWith(end) && trimmed.length > before.length + end.length &&
        !filledGap(text, line)
}

/** Slide 15's line, with the words the paste goes over selected, and what the paste puts there. */
internal const val PASTE_LINE = "As fn and V proves, I can't type this myself"
internal const val PASTE_OVER = "type everything myself"
internal const val PASTE_SEED = "As fn and V proves, I $PASTE_OVER"
internal const val PASTE_TEXT = "can't type this myself"

/** A key under each of the clipboard page's five clip fields, newest first: see ClipboardHistory.slot. */
internal val CLIP_FIELD_KEYS = listOf("R", "I", "F", "K", "C")

/** Slide 16's line: the clip that's nice to you pastes the end of it. */
internal const val CLIP_LEAD = "Look in Sym, then A, for what I copied: "
internal const val PRANK_PASTE = "my whole personality"
internal const val CLIP_LINE = CLIP_LEAD + PRANK_PASTE

/** The clipboard lesson's clips, newest first: a thread of digs, and one nice one, which isn't what it pastes. */
internal val GUIDE_CLIPS = listOf(
    "Per my last message, the grey words were the instructions.",
    "Circling back, since reading isn't your thing.",
    "Friendly reminder: nobody believes you read the manual.",
    "As discussed, Next is not a personality.",
    "Honestly, you're doing great.",
)
internal val PRANK_CLIP = GUIDE_CLIPS.last()
/** Every clip that a guide ever put in the user's clipboard, this one's and older ones', to clear from it. */
internal val OLD_GUIDE_CLIPS = GUIDE_CLIPS.toSet() + PASTE_TEXT + setOf(
    "I'm an idiot.", "Sorry, I was busy ignoring you.", "Per my last message, learn to read.",
    "As I said in my last two messages, I'm still ignoring you.", "Just circling back to remind you I'm ignoring you.",
    "Per my last four messages, please stop.", "Honestly, you're doing great.",
)

/** The words that the steer slide aims at are at least this many words from the cursor's column. */
internal const val STEER_TARGET_WORDS = 3

/** Swiped words that pass the swipe slide: the line's words after its first. */
internal const val SWIPES_TO_PASS = 3

/** Words that the mass deletion slide wants gone before it passes. */
internal const val REGRETS_TO_PASS = 8

/** The mass deletion slide's count, as it goes. */
internal fun regretsLine(count: Int) = "Regrets deleted: $count"

/** The count once the finger lifts after the slide passed: frozen on its last number, with the slide's praise. */
internal fun regretsFinal(count: Int) = "$count gone. Backspace pauses, F and G is top speed."

/** The swipe slide's instruction after a swipe: the same words, and how many are left. */
internal fun swipesLeft(left: Int) = "Slide over the next grey word. $left to go."

/** The key that types [ALT_SYMBOL] with Alt on the Titan 2 Elite: * is in A's corner. */
internal const val ALT_SYMBOL_KEY = "A"

/** The hunts on the groups and clown slides: when they mock, hint and say it's ok to skip, in seconds. Silent before that. */
internal const val HUNT_DRAIN_SECONDS = 20
internal const val HUNT_HINT_SECONDS = 35
internal const val HUNT_SKIP_SECONDS = 50

/** What a hunt says as time goes: its mockery, a subtle hint, then the hint and leave to skip. */
internal class Hunt(val mock: String, val hint: String, val skip: String)

internal val SECTION_HUNT = Hunt(
    "It's in every contract you never read.",
    "It's not on the first page. Swipe up.",
    "Common, page two. Or skip it. It's ok.",
)
internal val CLOWN_HUNT = Hunt(
    "You see it in the mirror every day. Come on.",
    "It's a face. Red nose. Sound familiar?",
    "Smileys, near 💩. Or skip. Clowns can't read.",
)

/** A hunt's instruction once its bar has drained: the time, counting up. */
internal fun huntLine(line: String, seconds: Int) = "$line $seconds s"

/** The clown slide's praise, with the time it took. */
internal fun huntPraise(seconds: Int) = "Found in $seconds s. Sym three times closes."

/** The word menu's buttons, on the keys that pick them, as the drawing of the keys hints them one at a time. */
internal val MENU_SAVE = mapOf("I" to "Save")
internal val MENU_FORGET = mapOf("O" to "Forget")

/** The words that the Grab a bunch slide marks in orange to select. */
internal const val GRAB_PHRASE = "my whole heart"
/** Words past the marked ones that a selection may take and still count. */
internal const val GRAB_EXTRA = 2
internal const val GRAB_SHORT = 0
internal const val GRAB_ALL = 1
internal const val GRAB_GREEDY = 2

/** Where the Grab a bunch slide's words are in [text]: the copy from [from] on, or the last one before it. */
internal fun grabTarget(text: String, from: Int): IntRange? {
    val at = text.indexOf(GRAB_PHRASE, from).takeIf { it >= 0 } ?: text.lastIndexOf(GRAB_PHRASE, from).takeIf { it >= 0 } ?: return null
    return at until at + GRAB_PHRASE.length
}

/** How a selection from [start] to [end] does on the marked words [aim]: short of them, all of them, or far too many. */
internal fun grabbed(text: String, aim: IntRange, start: Int, end: Int): Int {
    if (start > aim.first || end < aim.last + 1) return GRAB_SHORT
    fun words(from: Int, to: Int) = if (to <= from) 0 else Regex("[A-Za-z']+").findAll(text.substring(from, to)).count()
    return if (words(start, aim.first) + words(aim.last + 1, end) > GRAB_EXTRA) GRAB_GREEDY else GRAB_ALL
}

/**
 * The steer slide's target: a word of three letters or more on the visible line that doesn't hold the cursor, [other], at least
 * [STEER_TARGET_WORDS] words from the word in the cursor's column, the nearest such. [x] says where a character of [text] starts on
 * its line, and [cursorX] where the cursor is. Only the word's letters are marked, not its marks.
 */
internal fun steerTarget(text: String, other: IntRange, cursorX: Float, x: (Int) -> Float): IntRange? {
    val words = Regex("\\S+").findAll(text).map { it.range }.filter { it.first in other }.toList()
    if (words.isEmpty()) return null
    val column = words.indexOfLast { x(it.first) <= cursorX }.coerceAtLeast(0)
    val letters = words.mapIndexedNotNull { index, word ->
        val first = (word.first..word.last).firstOrNull { text[it].isLetter() } ?: return@mapIndexedNotNull null
        val last = (word.first..word.last).last { text[it].isLetter() }
        (index to (first..last)).takeIf { abs(index - column) >= STEER_TARGET_WORDS && last - first + 1 >= 3 }
    }
    return letters.minByOrNull { abs(it.first - column) }?.second
}

/** The steer slide, with a word marked: the cursor has to land in it, and the slide passes as the finger lifts there. */
private fun GuideMemory.steerToward(aim: IntRange, event: KeyboardEvent): GuideVerdict = when {
    // Where the cursor was as the slide began, to tell short of the word from past it.
    event is FieldChanged -> {
        if (mark < 0) mark = if (cursor >= 0) cursor else event.start
        GuideVerdict.Wait
    }
    event is Moved && event.lifted -> when {
        mark < 0 -> GuideVerdict.Hint("Held it, then chickened out. Hold, then slide.", stage = 0)
        cursor in aim.first..aim.last + 1 -> GuideVerdict.Done
        if (mark <= aim.first) cursor > aim.last + 1 else cursor < aim.first ->
            GuideVerdict.Hint("Past it. Slide back a little.", stage = 0)
        else -> GuideVerdict.Hint("Not there yet. Keep going.", stage = 0)
    }
    else -> GuideVerdict.Wait
}

/** What the field gets back on a delete slide that was emptied: its own seed, and a line about it. */
internal val DELETE_RESETS = listOf(
    "Deleted it all? Your regrets stay.",
    "Twice? Now you're deleting our hope.",
)
internal const val STEER_RESET = "The delete lesson is over, man."

/** A line that goes on forever, as far as a cursor can tell: [text] again and again. */
internal fun looping(text: String, times: Int = STEER_LOOPS) = List(times) { text }.joinToString(" ")
/** How many times the steering line repeats: the cursor starts in the middle, far from either end. */
internal const val STEER_LOOPS = 9

/** The end's line in the field: the summary that the guide wrote with you. */
internal const val END_TEXT = "You made it to the end. Now go type something real, with the keys your Titan deserves."

/** Typed text ends with [symbol]. */
internal fun endsWithSymbol(text: String, symbol: String) = text.trimEnd().endsWith(symbol)

/** A typed thing that isn't a letter, digit, space or the slide's own symbol, in the gap of [line]. */
private fun otherSymbol(event: KeyboardEvent, line: String, symbol: String) =
    event is FieldChanged && wrongInGap(event.text, line, symbol)

/** The line of a typing slide is typed through: its letters are all there, and its last word is finished. */
internal fun lineTyped(text: String, line: String): Boolean {
    val done = text.isNotEmpty() && (text.last().isWhitespace() || !text.last().isLetterOrDigit())
    return done && GuideScript.letters(text).startsWith(GuideScript.letters(line))
}

/**
 * The guide's one list (v5). Each title says what to do, and its instruction how; the jokes are in how it's said. Each slide has
 * its own [GuideStep.line] to finish, and its own field. Each slide's words, scenarios and check are together.
 */
internal val GUIDE_STEPS = listOf(
    // TYPING: each slide's line in grey in the field, typed over; chips for real mistakes; each slide locked to what it teaches.
    GuideStep(
        id = "type", chapter = GuideChapter.Typing,
        title = "Make a typo. We'll fix it.",
        // Whatever is typed for a word becomes that word: there's no way to fail. The keys light in order, once.
        stages = listOf(GuideStage("Type the grey words, typos and all.") { event ->
            when {
                event is FieldChanged && lineTyped(event.text, TYPE_LINE) ->
                    if (count > 0) GuideVerdict.Done else counts("No typos? Show-off.", "Space fixes them anyway. Next when ready.")
                event is KeyboardEvent.Fixed && event.isRealFix -> {
                    count++
                    GuideVerdict.Wait
                }
                event is KeyboardEvent.Fixed && mark != 1 -> {
                    mark = 1
                    chip("Make a typo, bro. Learn to read.")
                }
                event is Pushed && event.code == KEY_SPACE && !armed -> chip("Letters first. Then Space fixes them.")
                event is Pushed && isLetter(event.code) -> {
                    armed = true
                    GuideVerdict.Wait
                }
                event is Pushed && event.code == KEY_SPACE -> {
                    armed = false
                    GuideVerdict.Wait
                }
                else -> GuideVerdict.Wait
            }
        }),
        done = "Fixed. Nobody saw a thing.",
        praise = "Keep typing if you like, or press Next.",
        line = TYPE_LINE,
        text = TYPE_LINE,
        ghost = TYPE_TYPOS,
        cue = GuideCue.Spell,
        lessons = setOf(GuideLesson.Typing, GuideLesson.Backspace),
        forceFix = true,
        sides = false,
        demo = GuideDemo.TypeSlip,
        bar = GuideBar.Fix,
    ),
    GuideStep(
        id = "strip", chapter = GuideChapter.Typing,
        title = "Type it right. We'll still miss.",
        stages = listOf(
            GuideStage("Type the grey words. Carefully, this time.") { event ->
                when {
                    // The first word typed right comes out wrong, on purpose, and the right one waits on the bar.
                    event is KeyboardEvent.Fixed && event.word == GuideScript.slip(event.typed) -> GuideVerdict.Done
                    event is KeyboardEvent.Fixed && event.isRealFix -> chip("Typo. Carefully, we said. Next word.")
                    event is FieldChanged && lineTyped(event.text, BAR_LINE) -> GuideVerdict.Finished
                    else -> GuideVerdict.Wait
                }
            },
            // Only the bar works now, so the miss can't be typed past.
            GuideStage("Tap the right word. It's dancing on the bar.", GuideCue.Free, lessons = setOf(GuideLesson.BarTap)) { event ->
                when {
                    event is Chosen -> GuideVerdict.Done
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Fixed. Now finish the line.") { event ->
                if (event is FieldChanged && lineTyped(event.text, BAR_LINE)) GuideVerdict.Done else GuideVerdict.Wait
            },
        ),
        done = "Missed on purpose. Fixed it.",
        praise = "A miss is one tap away on the bar.",
        line = BAR_LINE,
        text = BAR_LINE,
        nearMiss = true,
        lessons = setOf(GuideLesson.BarTap, GuideLesson.Typing, GuideLesson.Backspace),
        demo = GuideDemo.FixNext,
    ),
    GuideStep(
        id = "swipe", chapter = GuideChapter.Typing,
        title = "Swipe it. One hand, no questions.",
        stages = listOf(GuideStage("Slide over the next grey word. Lift at the end.") { event ->
            when {
                // The right word is on the bar after a miss, and tapping it counts as the swipe.
                event is Chosen && mark == 1 -> {
                    mark = -1
                    if (++count >= SWIPES_TO_PASS) GuideVerdict.Done else GuideVerdict.Wait
                }
                event is Recognised && event.words.isNotEmpty() && expected.isNotEmpty() &&
                    GuideScript.letters(event.words.first()) != expected -> {
                    mark = 1
                    chip("Close. Tap the right one on the bar.")
                }
                event is Recognised && event.words.isNotEmpty() -> {
                    mark = -1
                    armed = false
                    if (++count >= SWIPES_TO_PASS) GuideVerdict.Done else GuideVerdict.Wait
                }
                event is Tapped -> chip("That's a tap. Slide, don't lift.")
                event is Pushed && isLetter(event.code) -> {
                    armed = true
                    GuideVerdict.Wait
                }
                event is Pushed && event.code == KEY_SPACE && armed -> {
                    armed = false
                    chip("That's typing. Swipe this one.")
                }
                else -> GuideVerdict.Wait
            }
        }),
        done = "One swipe, one word. Look at you.",
        praise = "Swipe the next one, or press Next.",
        // After the first swipe, the instruction counts down the swipes left.
        live = { if (count in 1 until SWIPES_TO_PASS) swipesLeft(SWIPES_TO_PASS - count) else null },
        line = SWIPE_LINE,
        seed = SWIPE_LEAD.trimEnd(),
        text = SWIPE_LINE.removePrefix(SWIPE_LEAD),
        cue = GuideCue.NextWord,
        needs = GuideNeed.SwipeTyping,
        lessons = setOf(GuideLesson.Swipe, GuideLesson.BarTap, GuideLesson.Typing, GuideLesson.Backspace),
        demo = GuideDemo.SwipeNext,
    ),
    GuideStep(
        id = "menu", chapter = GuideChapter.Typing,
        title = "Save a word. Then dump it.",
        stages = listOf(
            GuideStage("Hold any word on the bar until its menu opens.") { event ->
                when (event) {
                    is WordMenu -> GuideVerdict.Done
                    is Chosen -> chip("That typed it. Hold it, don't tap.")
                    else -> GuideVerdict.Wait
                }
            },
            // One key at a time: the drawing hints Save on I, then Forget on O.
            GuideStage("Press I to save it.", GuideCue.Spot(listOf("I")), MENU_SAVE) { event ->
                when {
                    event is MenuPicked && event.action == "Save" -> GuideVerdict.Done
                    event is MenuPicked && event.action == "Close" -> GuideVerdict.Hint("Closed it. Hold the word again.", stage = 0)
                    event is MenuPicked && event.action == "Forget" -> chip("Straight to dumping it? Save it first. I.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Now press O to forget it.", GuideCue.Spot(listOf("O")), MENU_FORGET) { event ->
                when {
                    event is MenuPicked && event.action == "Forget" -> GuideVerdict.Done
                    // I again makes it a favourite, and then O takes that back first.
                    event is MenuPicked && event.action == "Favourite" -> chip("A favourite. Fancy. O, and O again.")
                    event is MenuPicked && event.action == "Unfavourite" -> chip("Unfavourited. Once more, O forgets.")
                    event is MenuPicked && event.action == "Close" -> GuideVerdict.Hint("Closed it. Hold the word again.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "Saved, then dumped. Cold.",
        praise = "I twice makes a favourite. P closes the menu.",
        line = MENU_LINE,
        seed = MENU_LINE,
        help = "Hold, don't tap. Keep your finger on a bar word.",
        lessons = setOf(GuideLesson.BarHold, GuideLesson.Typing, GuideLesson.Backspace),
        bar = GuideBar.Menu,
    ),
    // DELETE: each slide starts with its line in the field and the cursor where the deleting is; the thing to delete in orange.
    GuideStep(
        id = "backspace", chapter = GuideChapter.Delete,
        title = "Delete the extra e. The old way.",
        stages = listOf(GuideStage("Press Backspace once.", GuideCue.Spot(listOf(Layout.BACKSPACE))) { event ->
            if (event is FieldChanged && event.text.trimEnd() == BACKSPACE_LINE) GuideVerdict.Done else GuideVerdict.Wait
        }),
        done = "One letter. Thrilling.",
        praise = "That's how everyone deletes. Next, whole words.",
        line = BACKSPACE_LINE,
        seed = BACKSPACE_SEED,
        lessons = setOf(GuideLesson.Backspace),
        demo = GuideDemo.DeleteLast,
    ),
    GuideStep(
        id = "flick", chapter = GuideChapter.Delete,
        title = "Flick away the stupid word.",
        // A held delete is the next slide's: the keyboard blocks it here, and says so.
        stages = listOf(GuideStage("Flick left from Backspace, and lift at once.") { event ->
            when {
                event is DeletedWord && event.held -> GuideVerdict.Wait
                event is FieldChanged && FLICK_WORD !in event.text && event.text.trimEnd().endsWith("word") -> GuideVerdict.Done
                event is DeletedWord -> GuideVerdict.Done
                else -> GuideVerdict.Wait
            }
        }),
        done = "Gone. Like your patience.",
        praise = "One flick, one word. Next, a lot of words.",
        line = FLICK_LINE,
        seed = FLICK_SEED,
        cursorAt = ::flickCursor,
        cue = GuideCue.Flick(hold = false),
        needs = GuideNeed.SwipeTyping,
        lessons = setOf(GuideLesson.Flick),
        demo = GuideDemo.DeleteLast,
    ),
    GuideStep(
        id = "hold", chapter = GuideChapter.Delete,
        title = "Hold the flick. Delete in bulk.",
        // A count of regrets deleted takes the instruction's place from the first word, and the slide passes at eight.
        stages = listOf(GuideStage("Flick left from Backspace, then keep holding.") { event ->
            when {
                event is DeleteHoldStarted -> {
                    mark = 1
                    GuideVerdict.Wait
                }
                event is DeletedWord -> {
                    count++
                    when {
                        count >= REGRETS_TO_PASS -> GuideVerdict.Done
                        mark != 1 -> chip("That was one word. Hold it down.")
                        else -> GuideVerdict.Wait
                    }
                }
                // A hold let go too soon.
                event is Moved && event.lifted && mark == 1 && count in 1 until REGRETS_TO_PASS -> {
                    mark = -1
                    chip("Therapy's not over. Hold longer.")
                }
                else -> GuideVerdict.Wait
            }
        }),
        done = "Watch your regrets go.",
        // After the count: see [regretsFinal]. The zones are on the drawing while the slide is tried.
        praise = "Back on Backspace pauses. F and G is top speed.",
        live = { if (count > 0) regretsLine(count) else null },
        line = HOLD_LINE,
        seed = HOLD_SEED,
        cursorAt = ::holdCursor,
        cue = GuideCue.Flick(hold = true),
        needs = GuideNeed.SwipeTyping,
        lessons = setOf(GuideLesson.Flick, GuideLesson.DeleteHold),
        demo = GuideDemo.DeleteLast,
    ),
    // STEERING: the slide's line again and again, with the cursor in the middle; the cursor gestures only.
    GuideStep(
        id = "steer", chapter = GuideChapter.Steering,
        title = "Steer to the underlined word.",
        stages = listOf(
            GuideStage("Tap Space twice, and hold the second tap.") { event ->
                when {
                    event is CursorStarted && !event.words -> GuideVerdict.Done
                    event is CursorStarted -> chip("One tap too many. Two taps, the second held.")
                    event is DoubleTapped || event is TwoTapsAbove -> chip("You lifted. Keep the second tap down.")
                    else -> GuideVerdict.Wait
                }
            },
            // The screen underlines a word on the other line, and the cursor has to land in it.
            GuideStage("Now slide. Lift when the cursor's in it.") { event ->
                val aim = target
                when {
                    aim != null -> steerToward(aim, event)
                    // Nothing marked: a real steer passes as it goes.
                    event is FieldChanged -> when {
                        mark < 0 -> {
                            mark = event.start
                            GuideVerdict.Wait
                        }
                        abs(event.start - mark) >= CURSOR_TRAVEL || event.end != event.start -> GuideVerdict.Done
                        else -> GuideVerdict.Wait
                    }
                    event is Moved && event.lifted -> GuideVerdict.Hint("Held it, then chickened out. Hold, then slide.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "Cursor moved. No screen harmed.",
        praise = "Slide further out and it goes faster.",
        help = "Tap in the shaded patch. Hold the second tap.",
        line = STEER_LINE,
        seed = STEER_LINE,
        cue = GuideCue.Taps(2, drag = true),
        needs = GuideNeed.CursorControl,
        lessons = setOf(GuideLesson.Cursor),
        demo = GuideDemo.MoveCursor,
    ),
    GuideStep(
        id = "selectmore", chapter = GuideChapter.Steering,
        title = "Grab my whole heart. Three taps.",
        stages = listOf(
            // The words to grab are marked in orange, and the cursor waits right before them.
            GuideStage("Tap Space three times. Hold the third.") { event ->
                when {
                    event is CursorStarted && event.words -> GuideVerdict.Done
                    event is CursorStarted -> chip("That's two. Count to three. We believe in you.")
                    event is FieldChanged && event.end > event.start -> chip("One word? I'm worth more than that.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Slide to grab all of it. Don't be shy.") { event ->
                val aim = target
                when {
                    // The marked words, and a word or two more, pass as the finger lifts. Many more is greedy.
                    aim != null && event is FieldChanged && event.end > event.start -> {
                        val was = mark
                        mark = grabbed(event.text, aim, event.start, event.end)
                        if (mark == GRAB_GREEDY && was != GRAB_GREEDY) chip("Easy. Just my heart. Slide back a bit.") else GuideVerdict.Wait
                    }
                    aim != null && event is Moved && event.lifted -> when (mark) {
                        GRAB_ALL -> GuideVerdict.Done
                        GRAB_GREEDY -> GuideVerdict.Hint("Easy. Just my heart. Slide back a bit.", stage = 0)
                        else -> GuideVerdict.Hint("So close. Then you let go. Story of my life.", stage = 0)
                    }
                    // Nothing marked: any selection passes.
                    event is FieldChanged && event.end > event.start -> GuideVerdict.Done
                    event is Moved && event.lifted -> GuideVerdict.Hint("So close. Then you let go. Story of my life.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "You had me at the third tap.",
        praise = "Three taps pick a word. Hold the third and slide.",
        help = "Three taps, the third held, then slide.",
        line = GRAB_LINE,
        seed = GRAB_LINE,
        cue = GuideCue.Taps(3, drag = true),
        needs = GuideNeed.CursorControl,
        lessons = setOf(GuideLesson.Cursor),
        demo = GuideDemo.SelectLast,
    ),
    // SYMBOLS AND EMOJI: each slide's line in the field with a gap, the cursor in it; the slide's symbol goes there, by its way.
    GuideStep(
        id = "alt", chapter = GuideChapter.Symbols,
        title = "Add the *, the traditional way.",
        stages = listOf(
            GuideStage("Hold Alt.", GuideCue.Spot(emptyList(), held = listOf(Layout.ALT))) { event ->
                when {
                    event is FieldChanged && filledGap(event.text, ALT_LINE) -> GuideVerdict.Finished
                    event is ModifierState && event.alt != Level.Off && event.panel == Panel.Closed -> GuideVerdict.Done
                    else -> GuideVerdict.Wait
                }
            },
            // Alt stays lit, held; A lights, and the other corners fade.
            GuideStage("Keep holding Alt and press A.", GuideCue.Spot(listOf(ALT_SYMBOL_KEY), held = listOf(Layout.ALT))) { event ->
                when {
                    event is FieldChanged && filledGap(event.text, ALT_LINE) -> GuideVerdict.Done
                    event is Pushed && isLetter(event.code) -> {
                        armed = true
                        GuideVerdict.Wait
                    }
                    // Alt let go before any key: it waits for one key, which works too.
                    event is Released && event.code in ALT_KEYS && !armed -> chip("Fine, that works too. Alt waits for one key.")
                    otherSymbol(event, ALT_LINE, ALT_SYMBOL) -> chip("That's not *. Fine print's not your thing, huh.")
                    event is ModifierState && event.alt == Level.Locked -> chip("Locked it. Commitment, finally.")
                    event is ModifierState && event.alt == Level.Off && event.panel == Panel.Closed ->
                        GuideVerdict.Hint("On, off. Very decisive.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "*Terms and conditions apply.",
        praise = "Alt types the symbol in a key's corner.",
        line = ALT_LINE,
        seed = gapSeed(ALT_LINE, ALT_SYMBOL),
        cursorAt = { gapCursor(ALT_LINE, ALT_SYMBOL) },
        symbol = ALT_SYMBOL,
        usesPanel = true,
        setup = Panel.Closed,
        lessons = setOf(GuideLesson.Alt),
        demo = GuideDemo.AltLetter(ALT_SYMBOL),
    ),
    GuideStep(
        id = "holdsym", chapter = GuideChapter.Symbols,
        title = "Add the @. Hold P for it.",
        stages = listOf(GuideStage("Hold P until it turns into @.", GuideCue.Spot(listOf("P"))) { event ->
            when {
                event is FieldChanged && filledGap(event.text, HOLD_SYMBOL_LINE) -> GuideVerdict.Done
                event is HeldSymbol && event.symbol != "@" -> chip("Not @. Close. Not really.")
                // A letter let go before it turned into its symbol.
                event is Pushed && isLetter(event.code) -> {
                    mark = event.code
                    GuideVerdict.Wait
                }
                event is Released && event.code == mark -> {
                    mark = -1
                    chip("Too quick. Hold it like a grudge.")
                }
                else -> GuideVerdict.Wait
            }
        }),
        done = "Look at you, holding it together.",
        praise = "Any letter, held, types its corner symbol.",
        line = HOLD_SYMBOL_LINE,
        seed = gapSeed(HOLD_SYMBOL_LINE, "@"),
        cursorAt = { gapCursor(HOLD_SYMBOL_LINE, "@") },
        symbol = "@",
        needs = GuideNeed.HoldSymbols,
        usesPanel = true,
        setup = Panel.Closed,
        lessons = setOf(GuideLesson.HoldSymbol),
        demo = GuideDemo.HoldLetter("@"),
    ),
    GuideStep(
        id = "symmain", chapter = GuideChapter.Symbols,
        title = "Add the &. It's behind Sym.",
        stages = listOf(
            GuideStage("Press Sym.", GuideCue.Spot(listOf(Layout.SYM))) { event ->
                when {
                    panel(event, Panel.Symbols) -> GuideVerdict.Done
                    panel(event, Panel.Emoji) -> chip("Emoji already? Patience. Sym again closes them.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Find & and press its key.", GuideCue.Free) { event ->
                when {
                    event is FieldChanged && filledGap(event.text, SYM_LINE) -> GuideVerdict.Done
                    otherSymbol(event, SYM_LINE, "&") -> GuideVerdict.Hint("Not &. Read, then type.", stage = 0)
                    event is EmptyKey -> chip("That one's empty. Like this joke.")
                    panel(event, Panel.Emoji) -> GuideVerdict.Hint("Emoji already? Patience. Sym again closes them.", stage = 0)
                    panel(event, Panel.Closed) -> GuideVerdict.Hint("Just looking? Window shopper.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "& that's how it's done.",
        praise = "Sym opens every symbol. One press, and it closes.",
        line = SYM_LINE,
        seed = gapSeed(SYM_LINE, "&"),
        cursorAt = { gapCursor(SYM_LINE, "&") },
        symbol = "&",
        usesPanel = true,
        setup = Panel.Closed,
        lessons = setOf(GuideLesson.Sym),
        demo = GuideDemo.Insert(listOf(Layout.SYM), ""),
    ),
    GuideStep(
        id = "symgroups", chapter = GuideChapter.Symbols,
        title = "Add the §. It's further in.",
        // used holds what was used: 1 a page by swiping up or down, 2 a page by the arrows (Backspace or Enter), 4 a group by the
        // bottom row or a sideways swipe, which works too and says so.
        stages = listOf(
            GuideStage("Press Sym, then swipe up for the next page.", GuideCue.Spot(listOf(Layout.SYM))) { event ->
                when {
                    event is PanelSwiped && event.moved && abs(event.down) >= abs(event.across) -> {
                        used = used or 1
                        GuideVerdict.Done
                    }
                    event is Pushed && panelOpen && (event.code == KEY_DEL || event.code == KEY_ENTER) -> {
                        used = used or 2
                        GuideVerdict.Done
                    }
                    otherGroup(event) -> {
                        used = used or 4
                        GuideVerdict.Hint("Yeah, that works too.", stage = 1)
                    }
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Find § and press its key.", GuideCue.Free) { event ->
                when {
                    event is FieldChanged && filledGap(event.text, GROUPS_LINE) -> GuideVerdict.Done
                    otherSymbol(event, GROUPS_LINE, SECTION) -> GuideVerdict.Hint("Wrong squiggle. § looks like an S that ate an S.", stage = 1)
                    otherGroup(event) && used and 4 == 0 -> {
                        used = used or 4
                        chip("Yeah, that works too.")
                    }
                    panel(event, Panel.Closed) -> GuideVerdict.Hint("Closed it. The § is still in there.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "See § 4.2. Nobody ever does.",
        praise = "Up or down turns pages. The bottom row, groups.",
        line = GROUPS_LINE,
        seed = gapSeed(GROUPS_LINE, SECTION),
        cursorAt = { gapCursor(GROUPS_LINE, SECTION) },
        symbol = SECTION,
        hunt = SECTION_HUNT,
        usesPanel = true,
        setup = Panel.Closed,
        lessons = setOf(GuideLesson.Sym),
        demo = GuideDemo.Insert(listOf(Layout.SYM), ""),
    ),
    GuideStep(
        id = "clown", chapter = GuideChapter.Symbols,
        title = "Sign it with 🤡. Find yourself.",
        stages = listOf(
            GuideStage("Press Sym twice for the emoji.", GuideCue.Spot(listOf(Layout.SYM))) { event ->
                when {
                    panel(event, Panel.Emoji) -> GuideVerdict.Done
                    otherSymbol(event, CLOWN_LINE, CLOWN) -> chip("That's a symbol, not a clown.")
                    else -> GuideVerdict.Wait
                }
            },
            // A hunt: a bar drains, silent, then the time counts up, with mockery, a hint and leave to skip. It never fails.
            GuideStage("Find 🤡 and press its key.", GuideCue.Free) { event ->
                when {
                    event is FieldChanged && filledGap(event.text, CLOWN_LINE) -> GuideVerdict.Done
                    otherSymbol(event, CLOWN_LINE, CLOWN) -> GuideVerdict.Hint("Not you. The other clown. The one that's also you.", stage = 1)
                    panel(event, Panel.Closed) -> GuideVerdict.Hint("Closed? The clown's still in there. Like a mirror.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "Hey, that's you.",
        // Once found, the praise is the time: see [huntPraise]. This shows if the hunt had no time.
        praise = "Sym once: symbols. Twice: emoji. Three: closes.",
        line = CLOWN_LINE,
        seed = gapSeed(CLOWN_LINE, CLOWN),
        cursorAt = { gapCursor(CLOWN_LINE, CLOWN) },
        symbol = CLOWN,
        hunt = CLOWN_HUNT,
        usesPanel = true,
        setup = Panel.Closed,
        lessons = setOf(GuideLesson.Sym),
        rain = CLOWN,
        demo = GuideDemo.Insert(listOf(Layout.SYM, Layout.SYM), ""),
    ),
    // COPY AND PASTE: the field starts with the slide's line, the paste lesson's last words selected.
    GuideStep(
        id = "paste", chapter = GuideChapter.Clipboard,
        title = "Paste over the orange words.",
        // fn is held, steady; V lights.
        stages = listOf(GuideStage("Hold fn and press V.", GuideCue.Spot(listOf("V"), held = listOf(Layout.FN))) { event ->
            when {
                event is FieldChanged && event.text.contains(PASTE_TEXT) -> GuideVerdict.Done
                event is KeyboardEvent.Copied && event.text != PASTE_TEXT -> chip("That's copy. V for paste. Nobody knows why it's V.")
                grew(event) && (event as FieldChanged).text.length - stageLength > 2 -> chip("That's not ours. Ours is better.")
                // The slide starts with words selected: a cursor alone means they're not.
                event is FieldChanged && event.start == event.end && mark != 1 -> {
                    mark = 1
                    chip("Lost the selection. Classic. Paste anyway.")
                }
                else -> GuideVerdict.Wait
            }
        }),
        done = "Pasted. Honest, at least.",
        praise = "fn and V pastes. fn and C copies.",
        line = PASTE_LINE,
        seed = PASTE_SEED,
        needs = GuideNeed.FnAsCtrl,
        clip = PASTE_TEXT,
        preselect = true,
        rain = "🤪",
        lessons = setOf(GuideLesson.Ctrl),
        demo = GuideDemo.Insert(listOf(Layout.FN, "V", Layout.FN, "V"), ""),
    ),
    GuideStep(
        id = "clipboard", chapter = GuideChapter.Clipboard,
        title = "Paste the one nice clip.",
        stages = listOf(
            GuideStage("Press Sym.", GuideCue.Spot(listOf(Layout.SYM))) { event ->
                when {
                    panel(event, Panel.Symbols) -> GuideVerdict.Done
                    panel(event, Panel.Emoji) -> GuideVerdict.Hint("Emoji? Sure, A works from there too.", stage = 1)
                    panel(event, Panel.Clipboard) -> GuideVerdict.Stage(2)
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Press A for the clipboard.", GuideCue.Spot(listOf(Layout.CLIPBOARD_KEY))) { event ->
                when {
                    panel(event, Panel.Clipboard) -> GuideVerdict.Done
                    panel(event, Panel.Closed) -> GuideVerdict.Stage(0)
                    else -> GuideVerdict.Wait
                }
            },
            // Reading the clips is the skill: rest on one to read it. Only one is nice, and it's the prank. A rude one is taken
            // back out at once, and the slide says so: see the screen.
            // The drawing walks over the clips, one after another, as the finger should: a key in each clip's field.
            GuideStage("Rest on each clip to read it. Paste the nice one.", GuideCue.Spot(CLIP_FIELD_KEYS)) { event ->
                when {
                    event is FieldChanged && event.text.contains(PRANK_PASTE) -> GuideVerdict.Done
                    panel(event, Panel.Closed) -> GuideVerdict.Hint("Leaving? The nice one's still in there. Somewhere.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        // The nice clip pastes the prank, and the slide owns up.
        done = "Gotcha. Swapped it on purpose.",
        praise = "It keeps what you copy. Sym, then A, any time.",
        line = CLIP_LINE,
        seed = CLIP_LEAD.trimEnd(),
        usesPanel = true,
        setup = Panel.Closed,
        sandboxClips = GUIDE_CLIPS,
        rain = "🤪",
        lessons = setOf(GuideLesson.Sym),
        demo = GuideDemo.Insert(listOf(Layout.SYM, Layout.CLIPBOARD_KEY), ""),
    ),
    GuideStep(
        id = "end", chapter = GuideChapter.End,
        title = "TitanKeys",
        body = "Built for Titan 2 Elite.",
        done = "The keys your Titan deserves.",
        text = END_TEXT,
        offersShare = true,
        check = wait,
    ),
)

/** The lines that the guide's slides have you finish, in order: the summary at the end. Their first letters spell [ACROSTIC]. */
internal val SUMMARY_LINES get() = GUIDE_STEPS.filter { it.line.isNotEmpty() }.map { it.line }

/** What the groups slide says once done, besides its own praise: a way that worked too. See [GuideMemory.used]. */
internal fun groupsPraise(used: Int) = when {
    used and 4 != 0 -> "The bottom row works too. Up or down turns pages."
    used and 2 == 0 -> "Backspace and Enter turn pages too."
    else -> "Up or down turns pages. The bottom row, groups."
}

/**
 * The chip for something that [step]'s lock kept from the field, by its [GuideLesson] name. Nothing is ever blocked silently
 * (owner, v5): each says that it's blocked, and why or what to do instead. [lessons] is what the slide lets through now.
 */
internal fun blockedChip(step: GuideStep, lesson: String, lessons: Set<GuideLesson>? = step.lessons): String {
    val open = lessons.orEmpty()
    return when (lesson) {
        GuideLesson.Typing.name -> when {
            step.chapter in NOT_TYPING -> "Typing's blocked here. Typing's done. Keep up."
            GuideLesson.Alt in open -> "Blocked. Hold Alt while you press it."
            GuideLesson.Ctrl in open -> "Blocked. Hold fn with it. That's the trick."
            GuideLesson.HoldSymbol in open -> "Blocked. Hold the key until it turns."
            GuideLesson.Sym in open -> "Typing's blocked. Press Sym first."
            GuideLesson.BarTap in open -> "Typing's blocked. Tap the dancing word first."
            else -> "Typing's blocked on this slide."
        }
        GuideLesson.Backspace.name -> if (GuideLesson.Flick in open) "Backspace is blocked. Flick it." else
            "Backspace is blocked here. Nothing to delete."
        GuideLesson.Swipe.name -> "Swiping's blocked here. Type it. Swiping's next."
        GuideLesson.BarTap.name -> if (step.id == "type") "The bar's blocked here. Type it, typo and all." else
            "The bar's blocked here. Do it with the keys."
        GuideLesson.BarHold.name -> "Holding bar words is blocked here. That's later."
        GuideLesson.Flick.name -> "Flicks are blocked here. That's later."
        GuideLesson.DeleteHold.name -> "Held delete is blocked here. Flick and lift."
        GuideLesson.Cursor.name -> "Steering's blocked here. That's later."
        GuideLesson.Alt.name -> if (step.chapter == GuideChapter.Symbols) "Alt is blocked on this slide. Not this way." else
            "Alt is blocked here. That's later."
        GuideLesson.Sym.name -> if (step.chapter == GuideChapter.Symbols) "Sym is blocked on this slide. Not this way." else
            "Sym is blocked here. That's later."
        GuideLesson.HoldSymbol.name -> "Holding for symbols is blocked here. That's later."
        GuideLesson.Enter.name -> "Enter is blocked here. Nothing to send."
        GuideLesson.Ctrl.name -> "Shortcuts are blocked here. That's later."
        else -> "That's blocked here. That's later."
    }
}

/** The chapters that aren't about typing, where letters typed are a scenario of their own. */
private val NOT_TYPING = setOf(GuideChapter.Delete, GuideChapter.Steering)

/**
 * Something that is easy to do by chance and stops a step from working, with the words for it. A step's own check comes first,
 * so a step that wants one of these things gets it. [instruction] is the slide's instruction now, for a scenario that repeats it.
 */
internal fun commonMistake(step: GuideStep, event: KeyboardEvent, instruction: String): GuideVerdict.Hint? = when {
    event is ModifierState && event.panel == Panel.Settings -> GuideVerdict.Hint("Press Sym to close them.", "Settings? Nobody asked.")
    event is ModifierState && event.panel != Panel.Closed && !step.usesPanel ->
        GuideVerdict.Hint("Press Sym until the panel closes.", "Easy, tiger. That's later.")
    event is ModifierState && event.alt != Level.Off && !step.usesPanel ->
        GuideVerdict.Hint("Press Alt to turn it off.", "Alt is on, so the touchpad won't type.")
    step.chapter in NOT_TYPING && (event is Pushed && isLetter(event.code) || event is Recognised && event.words.isNotEmpty()) ->
        GuideVerdict.Hint(instruction, "Typing's done. Keep up.")
    event is CursorStarted && step.cue !is GuideCue.Taps ->
        GuideVerdict.Hint("That's the cursor. Quick taps just above the middle of Space start it. Lift your finger to stop.")
    event is HeldSymbol && step.chapter != GuideChapter.Symbols -> GuideVerdict.Hint("Holding a key types its symbol. Press quickly for letters.")
    else -> null
}

/** Where the guide is and what happens next. It knows nothing of the screen, so a test can run it with no phone. */
internal class GuideFlow(
    private val settings: () -> GuideSettings,
    private val steps: List<GuideStep> = GUIDE_STEPS,
) {
    private var position = 0
    private var memory = GuideMemory()
    /** Slides that another slide made unneeded, by id: the guide passes over them. */
    private val skipped = HashSet<String>()

    init {
        position = firstFrom(0)
    }

    val step get() = steps[position]

    /** The last step is the ending, which has no check to pass. */
    val finished get() = position == steps.lastIndex

    /** Which part of the guide this is. */
    val chapterIndex get() = GuideChapter.entries.indexOf(step.chapter)
    val chapterCount get() = GuideChapter.entries.size

    /** This slide is the last of its chapter. */
    val chapterEnds get() = steps.getOrNull(firstFrom(position + 1))?.chapter != step.chapter

    /** The chapters that the bar at the top shows: all but the end. */
    val barChapters get() = GuideChapter.entries.filter { it != GuideChapter.End }

    /** Which slide of its chapter this is, from 0, and how many slides its chapter has that show. */
    val stepInChapter get() = steps.subList(0, position).count { it.chapter == step.chapter && shows(it) }
    val stepsInChapter get() = steps.count { it.chapter == step.chapter && shows(it) }

    /** How far through the steps the guide is. The ending is not a step to do. */
    val stepIndex get() = position
    val stepCount get() = steps.size - 1

    /** The slides of [chapter] that show, by id. */
    fun idsOf(chapter: GuideChapter) = steps.filter { it.chapter == chapter && shows(it) }.map { it.id }

    /**
     * Where to come back to: this step, or for a step that takes the Sym panel as the step before left it, the first of those
     * steps in a row, because the panel is closed after the guide is left. A step that sets up its own menu comes back to itself.
     */
    val resumeId: String get() {
        var at = position
        while (at > 0 && steps[at].setup == null && steps[at - 1].usesPanel) at--
        return steps[at].id
    }

    /** The setting that this step needs and that is off now, if any. */
    val missing get() = step.needs?.takeUnless { settings().has(it) }

    /** The stage of the slide, and what it says and shows now: only the instruction that is needed. */
    val stage get() = memory.stage
    val instruction get() = step.stages.getOrNull(memory.stage)?.text ?: step.body
    val cue get() = step.stages.getOrNull(memory.stage)?.cue ?: step.cue
    /** What the drawing of the keys shows on its keys now, if anything. See [GuideStage.labels]. */
    val labels get() = step.stages.getOrNull(memory.stage)?.labels
    /** What the keyboard lets through now: the stage's own, or the slide's. */
    val lessons get() = step.stages.getOrNull(memory.stage)?.lessons ?: step.lessons
    /** What the slide counted, such as which ways the groups slide was used. */
    val counted get() = memory.used

    /** The slide was done in a way that does the next slide too, which then shows done. */
    val jumps get() = memory.jump

    /** Tells the step what the field holds as it starts, so that a stage can tell when something is typed or pasted. */
    fun seed(text: String, cursor: Int = -1) {
        memory.length = text.length
        memory.stageLength = text.length
        memory.cursor = cursor
    }

    /** Tells the step what the field marks in orange to aim at, or null for nothing. See [GuideMemory.target]. */
    fun aim(range: IntRange?) {
        memory.target = range
    }

    /** What the step has counted on this stage, such as words deleted. */
    val tally get() = memory.count

    /** The line that shows in place of the instruction now, if the step has one. See [GuideStep.live]. */
    fun liveLine() = step.live?.invoke(memory)

    /**
     * What the step makes of [event], with [expected] the letters of the word that the text box has next. If it waits, a mistake
     * that is easy to make gets a scenario that says what happened.
     */
    fun event(event: KeyboardEvent, expected: String = ""): GuideVerdict {
        if (finished) return GuideVerdict.Wait
        memory.expected = expected
        val verdict = step.check(memory, event)
        memory.skip?.let {
            skipped += it
            memory.skip = null
        }
        if (verdict != GuideVerdict.Wait) return verdict
        return commonMistake(step, event, instruction) ?: GuideVerdict.Wait
    }

    /** Moves to the next step that shows. Returns the step that was left. */
    fun advance(): GuideStep {
        val left = step
        if (!finished) moveTo(firstFrom(position + 1))
        return left
    }

    /** There is a step before this one that shows. */
    val canGoBack get() = previous() != null

    /** Moves back to the step before that shows. Returns false at the first step. */
    fun back(): Boolean {
        moveTo(previous() ?: return false)
        return true
    }

    private fun shows(step: GuideStep) = !step.skipWhen(settings()) && step.id !in skipped

    private fun previous(): Int? {
        var at = position - 1
        while (at >= 0 && !shows(steps[at])) at--
        return at.takeIf { it >= 0 }
    }

    /** Starts from the first step, or from the step with this id. */
    fun start(id: String? = null) {
        skipped.clear()
        moveTo(firstFrom(steps.indexOfFirst { it.id == id }.coerceAtLeast(0)))
    }

    /** A step that will not show moves the guide on to the first one after it. The ending always shows. */
    private fun firstFrom(from: Int): Int {
        var at = from.coerceAtMost(steps.lastIndex)
        while (at < steps.lastIndex && !shows(steps[at])) at++
        return at
    }

    private fun moveTo(next: Int) {
        position = next
        memory = GuideMemory()
    }
}
