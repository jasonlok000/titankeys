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
    /** These keys glow in turn. */
    data class Spot(val keys: List<String>) : GuideCue
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
        "you and me can go out for a bit if it is not too late",
        "we all know the best way to get it done is to just do it",
        "she said it was fine and then it was not fine at all",
        "i think we can make it if we go now and do not stop",
        "this is the part where you type fast and try not to look",
        "it was a good day and then he said he had to go home",
        "the more you type the less you have to think about it",
        "do not look at your hands they know what they are doing",
        "if you can read this you are going way too slow",
        "all of this is just words and none of it means a thing",
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

    /** The field's text is longer than when this stage began: something was typed or pasted. */
    fun grew(event: KeyboardEvent) = event is FieldChanged && stageLength >= 0 && event.text.length > stageLength
}

/**
 * One instruction of a slide. A slide teaches one thing at a time: it shows only the instruction that is needed now, and moves on,
 * or back, as the keyboard changes. [cue] replaces the slide's own ghost while this instruction shows.
 */
internal class GuideStage(val text: String, val cue: GuideCue? = null, val check: GuideMemory.(KeyboardEvent) -> GuideVerdict)

/** The check of a slide made of [stages]: each stage's check runs while it shows, and Done moves to the next stage. */
private fun staged(stages: List<GuideStage>): GuideMemory.(KeyboardEvent) -> GuideVerdict = check@{ event ->
    if (stages.isEmpty()) return@check GuideVerdict.Wait
    if (event is ModifierState) panelOpen = event.panel != Panel.Closed
    val verdict = stages[stage.coerceIn(0, stages.lastIndex)].check(this, event)
    if (event is FieldChanged) length = event.text.length
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
    /** The slide starts with a word selected, so that what you paste goes in its place, if nothing is selected yet. */
    val preselect: Boolean = false,
    /** Clips that the slide pins in the keyboard's clipboard as it opens, so that it has something to show. */
    val pinnedClips: List<String> = emptyList(),
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
    /** What the line under the title says once the slide is done: about what you just did, never the next instruction. */
    val praise: String = "",
    /** The slide offers to share swipes: its offer opens the disclosure, and an answer finishes it. */
    val offersShare: Boolean = false,
    val check: GuideMemory.(KeyboardEvent) -> GuideVerdict = staged(stages),
) {
    /** The field carries on from the slide before when both are in this chapter: one text box, one field. */
    val carriesField get() = chapter == GuideChapter.Typing || chapter == GuideChapter.Symbols
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
/** U, I, O, J, K and L: the keys that pick the word menu's buttons. */
private val MENU_KEYS = setOf(49, 37, 43, 38, 39, 40)
/** The emoji that the symbols chapter has you find. */
internal const val CLOWN = "🤡"

private val wait: GuideMemory.(KeyboardEvent) -> GuideVerdict = { GuideVerdict.Wait }

private fun panel(event: KeyboardEvent, panel: Panel) = event is ModifierState && event.panel == panel

/** A swipe or a touch that showed another group of the panel's symbols or emoji. */
private fun otherGroup(event: KeyboardEvent) = event is GroupPicked || event is PanelSwiped && event.moved && abs(event.across) > abs(event.down)

/** A scenario: [title] and [text] take the slide's place until the user is back on track. [stage] moves the slide too. */
internal fun roast(title: String, text: String, stage: Int? = null) = GuideVerdict.Hint(text, title, stage)

/** A scenario that counts: the slide is done, with [title] as the line that lands and [text] under it. */
internal fun counts(title: String, text: String) = GuideVerdict.Hint(text, title, passes = true)

/** The Typing chapter's one text box, across its four slides. */
internal const val TYPING_TEXT = "This keyboard fixes your typos, so you can keep pretending you never make any. Swipe the words on the " +
    "same keys and lift after each one. When a swipe misses, the right word is one tap away on the bar. Hold a word on the bar to " +
    "judge it like you judge everyone else."

/** The Delete chapter's field: it shows its end first, so it's written to land at its end. And what comes back when it's all gone. */
internal const val DELETE_TEXT = "Delete these words, because you clearly regret every single one of them. You regret the typos, the " +
    "autocorrects you never checked, the reply-all you sent to the whole office, and that message you typed at two in the " +
    "morning. Start with the last letter, then whole words, then everything you've ever typed. This sentence especially. It " +
    "knows what you did."
internal val DELETE_RESETS = listOf(
    "Deleted it all? But you can't delete all your regrets." to
        "Wow, you deleted everything. Here's more to regret. Try deleting just a little this time.",
    "Twice? You're not deleting text, you're deleting our hope in you." to
        "Deleted it all again. Impressive commitment to destruction, terrible commitment to instructions. Here's an even longer " +
        "one so you can't get rid of it so fast. Every word in here is a word you'll have to delete by hand, one at a time, like " +
        "a person who reads. Start with the last word. Just the last word. Please.",
)

/** The Steering chapter's line, which loops forever, and what replaces it if it's deleted. */
internal const val STEER_TEXT = "This line goes on and on, and so does the cursor, wherever you steer it. Slide left and it runs back " +
    "through every word you skipped. Slide right and it races ahead to words you haven't even read. There's no end to this " +
    "line, so there's no excuse to stop steering, and no reason at all to poke the screen."
internal val STEER_RESET = "The delete lesson is over, man." to "The delete lesson is over, man. You had one instruction, it was " +
    "right there in plain words, and you deleted the whole line anyway. Reading clearly isn't your thing, so here it is slowly: " +
    "tap twice, keep your finger down, slide. Not delete. Never delete. Nobody asked you to delete. If you delete this one too, " +
    "we'll just keep putting it back, forever, because apparently we have more patience than you have reading skills."

/** A line that goes on forever, as far as a cursor can tell: [text] again and again. */
internal fun looping(text: String, times: Int = STEER_LOOPS) = List(times) { text }.joinToString(" ")
/** How many times the steering line repeats: the cursor starts in the middle, far from either end. */
internal const val STEER_LOOPS = 9

/**
 * The Symbols and emoji chapter's text box. Each sentence starts with the symbol to type; the guide types the words after it.
 * [ANY_SYMBOL] stands for "any symbol you like", on the groups slide.
 */
internal const val ANY_SYMBOL = "⬚"
internal val SYMBOL_PARTS = listOf(
    "$" to " is what this guide cost you, and somehow you're still behind. ",
    "@" to " is for tagging someone who actually reads instructions. ",
    "&" to " is for when one insult isn't enough. ",
    ANY_SYMBOL to " is the weirdest thing you could find, and yes, we're judging it. ",
    CLOWN to " is you, obviously. ",
)
internal val SYMBOLS_TEXT = SYMBOL_PARTS.joinToString("") { (symbol, words) -> symbol + words }.trim()

/** The words that the guide types after [symbol], on the symbols chapter. */
internal fun wordsAfter(symbol: String) = SYMBOL_PARTS.firstOrNull { it.first == symbol }?.second.orEmpty()

/** The Copy and paste chapter's field, its last word selected for the paste. */
internal const val CLIP_TEXT = "Paste over this word. Then open your clipboard and paste something from it. We left you some " +
    "favourites in there."

/** What the paste lesson puts on your clipboard. */
internal const val PASTE_TEXT = "I'm an idiot."

/** The clipboard lesson's clips, newest first: a passive-aggressive thread, and one nice one that isn't what it pastes. */
internal val GUIDE_CLIPS = listOf(
    "Per my last message, learn to read.",
    "Sorry, I was busy ignoring you.",
    "As I said in my last two messages, I'm still ignoring you.",
    "Just circling back to remind you I'm ignoring you.",
    "Per my last four messages, please stop.",
    "Honestly, you're doing great.",
)
internal val PRANK_CLIP = GUIDE_CLIPS.last()
internal const val PRANK_PASTE = "I am a super idiot."

/** The end's text box. */
internal const val END_TEXT = "You made it to the end. Now go type something real, with the keys your Titan deserves."

/** Typed text ends with [symbol], or any symbol for [ANY_SYMBOL]: not a letter, digit, space or plain punctuation. */
internal fun endsWithSymbol(text: String, symbol: String): Boolean {
    val trimmed = text.trimEnd()
    if (symbol != ANY_SYMBOL) return trimmed.endsWith(symbol)
    if (trimmed.isEmpty()) return false
    val last = trimmed.codePointBefore(trimmed.length)
    return !Character.isLetterOrDigit(last) && !Character.isWhitespace(last) && last.toChar() !in ".,'’!?"
}

/** The field now ends with [symbol]. */
private fun typedSymbol(event: KeyboardEvent, symbol: String) = event is FieldChanged && endsWithSymbol(event.text, symbol)

/** The field now ends with a symbol, but not [symbol]. */
private fun otherSymbol(event: KeyboardEvent, symbol: String) =
    event is FieldChanged && endsWithSymbol(event.text, ANY_SYMBOL) && !endsWithSymbol(event.text, symbol)

/** The guide's one list, from the owner's approved text (v3.1). Each slide's words, scenarios and check are together. */
internal val GUIDE_STEPS = listOf(
    // TYPING: one text box across the four slides, and the field carries on.
    GuideStep(
        id = "type", chapter = GuideChapter.Typing,
        title = "Type like a normal person.",
        stages = listOf(GuideStage("Type the text in the box. Make a typo, then press Space.") { event ->
            when {
                event is KeyboardEvent.Fixed && event.isRealFix -> GuideVerdict.Done
                event is KeyboardEvent.Fixed && event.typed != event.word ->
                    roast("That's just a capital. It does that by itself.", "Type a word wrong, then Space, to see a real fix.")
                event is KeyboardEvent.Fixed -> roast("Perfect spelling. Impressive. Annoying, but impressive.",
                    "Type a word wrong on purpose, then press Space.")
                event is Recognised && event.words.isNotEmpty() ->
                    roast("Swiping already? That's the next slide, keen bean.", "Press the keys to type a word, with a typo. Then Space.")
                event is Pushed && isLetter(event.code) -> {
                    armed = true
                    GuideVerdict.Wait
                }
                event is Pushed && event.code == KEY_SPACE && !armed -> roast("Nothing to fix. Yet.", "Type a word first. Ideally a wrong one.")
                else -> GuideVerdict.Wait
            }
        }),
        done = "Fixed. Nobody saw a thing.",
        praise = "Space fixes typos. Keep typing if you like.",
        text = TYPING_TEXT,
        cue = GuideCue.Free,
        demo = GuideDemo.TypeSlip,
        bar = GuideBar.Fix,
    ),
    GuideStep(
        id = "swipe", chapter = GuideChapter.Typing,
        title = "Type with one hand. We won't ask what the other one's doing.",
        stages = listOf(GuideStage("Slide over the letters of the next word in the box without pressing. Lift at the end.") { event ->
            when {
                // The right word is on the bar after a miss. Tapping it does the next slide too.
                event is Chosen && mark == 1 -> {
                    jump = true
                    GuideVerdict.Finished
                }
                event is Recognised && event.words.isNotEmpty() && expected.isNotEmpty() &&
                    GuideScript.letters(event.words.first()) != expected -> {
                    mark = 1
                    roast("Missed. Good news: the next slide is about exactly that.", "Tap the right word on the bar.")
                }
                event is Recognised && event.words.isNotEmpty() -> GuideVerdict.Done
                event is Tapped -> roast("That's a tap. Tapping is so slide one.", "Don't lift between letters. Slide from the first letter to the last.")
                event is Pushed && isLetter(event.code) -> roast("You pressed it. Not what we asked for, but ok.", "Touch lightly and slide. No pressing.")
                else -> GuideVerdict.Wait
            }
        }),
        done = "One swipe, one word. Look at you.",
        praise = "Swipe the next one, or press Next.",
        text = TYPING_TEXT,
        cue = GuideCue.NextWord,
        needs = GuideNeed.SwipeTyping,
        demo = GuideDemo.SwipeNext,
    ),
    GuideStep(
        id = "strip", chapter = GuideChapter.Typing,
        title = "Missed? We did that on purpose.",
        stages = listOf(
            GuideStage("Swipe or type the next word. It'll come out wrong, on purpose.") { event ->
                when {
                    event is Recognised && event.words.isNotEmpty() -> GuideVerdict.Done
                    event is Tapped -> roast("If you want to tap a keyboard, why did you even buy this phone?", "Swipe the word, or press its keys.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Tap the right word on the bar. It's the one dancing.", GuideCue.Free) { event ->
                when {
                    event is Chosen && (expected.isEmpty() || GuideScript.letters(event.word) == expected) -> GuideVerdict.Done
                    event is Chosen -> roast("Not that one. It was literally dancing for you.",
                        "Swipe or type the next word again, then tap the dancing one.", stage = 0)
                    event is Recognised && event.words.isNotEmpty() ->
                        roast("You just left it there? Forever? In your text?", "Tap the dancing word on the bar.")
                    event is DeletedWord || event is Pushed && event.code == KEY_DEL ->
                        roast("The hard way. Works, sure.", "Try the lazy way: type the word again, then tap the dancing one.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "It stays wrong until you leave, because we're petty.",
        praise = "Keep going if you like, or press Next.",
        text = TYPING_TEXT,
        cue = GuideCue.NextWord,
        needs = GuideNeed.SwipeTyping,
        nearMiss = true,
        demo = GuideDemo.FixNext,
    ),
    GuideStep(
        id = "menu", chapter = GuideChapter.Typing,
        title = "Judge a word like you judge everyone else.",
        stages = listOf(
            GuideStage("Hold a word on the bar until its menu opens.") { event ->
                when (event) {
                    is WordMenu -> GuideVerdict.Done
                    is Chosen -> roast("That typed it. Commitment issues?", "Hold the word until the menu opens.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Rest a finger on U, I or O to mark a button, then press that key. O closes it.") { event ->
                when {
                    event is MenuPicked && (event.action == "Save" || event.action == "Forget") ->
                        counts("Srsly? Relax. Thankfully we didn't actually save it.", "It's a guide. Nothing was saved or forgotten.")
                    event is MenuPicked -> GuideVerdict.Done
                    event is Pushed && isLetter(event.code) && event.code !in MENU_KEYS ->
                        roast("Wrong key. Do you read instructions??", "Rest on U, I or O to mark a button, then press that key.")
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "Judged and dealt with. Brutal.",
        praise = "U saves a word, I forgets it, O closes the menu.",
        text = TYPING_TEXT,
        bar = GuideBar.Menu,
    ),
    // DELETE: each slide starts with the text box in the field, its end showing.
    GuideStep(
        id = "backspace", chapter = GuideChapter.Delete,
        title = "The old way. Humour us.",
        stages = listOf(GuideStage("Press Backspace.", GuideCue.Spot(listOf(Layout.BACKSPACE))) { event ->
            when {
                event is Pushed && event.code == KEY_DEL -> GuideVerdict.Done
                event is DeletedWord -> counts("Skipped ahead. Show-off move.", "It counts. Press Next.")
                else -> GuideVerdict.Wait
            }
        }),
        done = "One letter. Thrilling.",
        praise = "That's how everyone deletes. Next, a whole word at once.",
        seed = DELETE_TEXT,
        demo = GuideDemo.DeleteLast,
    ),
    GuideStep(
        id = "flick", chapter = GuideChapter.Delete,
        title = "Delete a whole word. Your regrets are bigger than a letter.",
        stages = listOf(GuideStage("Flick left from Backspace, fast, and lift your finger at once.") { event ->
            when {
                // A hold is the next slide's lesson: it's done, so that slide is passed over, and this one still wants its flick.
                event is DeleteHoldStarted -> {
                    mark = 1
                    skip = "hold"
                    roast("Straight to the hold? We see you. Skipping that lesson for you.", "Now one quick flick left from Backspace, and lift.")
                }
                event is DeletedWord && (event.held || mark == 1) -> {
                    if (!event.held) mark = -1
                    GuideVerdict.Wait
                }
                event is DeletedWord -> GuideVerdict.Done
                event is Recognised && event.words.isNotEmpty() -> roast("That typed something. Delete it, maybe?", "Start on Backspace and flick left, fast.")
                event is Pushed && event.code == KEY_DEL -> roast("That's the last slide's trick.", "Flick left from Backspace instead, and lift.")
                else -> GuideVerdict.Wait
            }
        }),
        done = "Gone. Like your patience.",
        praise = "One flick, one word. Next, a lot of words.",
        seed = DELETE_TEXT,
        cue = GuideCue.Flick(hold = false),
        needs = GuideNeed.SwipeTyping,
        demo = GuideDemo.DeleteLast,
    ),
    GuideStep(
        id = "hold", chapter = GuideChapter.Delete,
        title = "Mass deletion. Very therapeutic.",
        stages = listOf(GuideStage("Flick left from Backspace, then keep holding your finger down. It keeps deleting until you lift.") { event ->
            when {
                event is DeleteHoldStarted -> {
                    mark = 1
                    GuideVerdict.Wait
                }
                event is DeletedWord && event.held -> GuideVerdict.Done
                event is DeletedWord && mark == 1 -> GuideVerdict.Wait
                event is DeletedWord -> roast("That was one word. Commit.", "Flick and keep your finger down.")
                else -> GuideVerdict.Wait
            }
        }),
        done = "Watch your regrets go.",
        praise = "A longer flick deletes faster.",
        seed = DELETE_TEXT,
        cue = GuideCue.Flick(hold = true),
        needs = GuideNeed.SwipeTyping,
        demo = GuideDemo.DeleteLast,
    ),
    // STEERING: a line that loops forever, with the cursor in the middle.
    GuideStep(
        id = "steer", chapter = GuideChapter.Steering,
        title = "Your finger is not a mouse. Today it is.",
        stages = listOf(
            GuideStage("Tap twice, quickly, on Space or just above it. Keep your finger down on the second tap.") { event ->
                when {
                    event is CursorStarted && !event.words -> GuideVerdict.Done
                    event is CursorStarted -> roast("One tap too many. Counting is hard.", "Two taps, the second one held.")
                    event is DoubleTapped || event is TwoTapsAbove ->
                        roast("You lifted. Read the instructions, genius.", "Tap twice and keep your finger down on the second tap.")
                    event is Recognised && event.words.isNotEmpty() -> roast("That typed a word. Not the plan.", "Quick, light taps on Space or just above it.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Now slide left or right. The cursor follows.") { event ->
                when {
                    // A real steer passes as it goes. A nudge passes too, with its own line, once the finger lifts.
                    event is FieldChanged -> when {
                        mark < 0 -> {
                            mark = event.start
                            GuideVerdict.Wait
                        }
                        abs(event.start - mark) >= CURSOR_TRAVEL || event.end != event.start -> GuideVerdict.Done
                        else -> {
                            if (event.start != mark) used = 1
                            GuideVerdict.Wait
                        }
                    }
                    event is Moved && event.lifted && used == 1 ->
                        counts("That's a nudge, not a steer, but ok.", "Slide further from the middle next time.")
                    event is Moved && event.lifted -> roast("Held it, then chickened out.", "Tap twice again, keep it down, then slide.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "Cursor moved. No screen harmed.",
        praise = "The further you slide from the middle, the faster it goes.",
        help = "Tap lightly in the shaded patch, and keep your second touch down while you slide.",
        cue = GuideCue.Taps(2, drag = true),
        needs = GuideNeed.CursorControl,
        demo = GuideDemo.MoveCursor,
    ),
    GuideStep(
        id = "selectword", chapter = GuideChapter.Steering,
        title = "Pick a word. Any word.",
        stages = listOf(GuideStage("Tap three times, quickly, on Space or just above it, and lift each time.") { event ->
            when {
                // Two taps on Space pick the word at the cursor, which counts.
                event is DoubleTapped && event.taps == 2 -> counts("Fine, that works too. Overachiever.", "It counts. Press Next.")
                event is TwoTapsAbove -> roast("Two taps up there do nothing. On purpose.", "Tap three times, lifting each time.")
                event is FieldChanged && event.end > event.start -> GuideVerdict.Done
                event is CursorStarted -> roast("That's steering, or selecting. The keyboard is confused, and so are we.",
                    "Three quick taps, lifting each time.")
                event is Recognised && event.words.isNotEmpty() -> roast("That typed a word.", "Quick, light taps, lifting each time.")
                else -> GuideVerdict.Wait
            }
        }),
        done = "Picked. Like a pro.",
        praise = "Three quick taps pick the word in front of the cursor. Two taps on Space do it too.",
        cue = GuideCue.Taps(3, drag = false),
        needs = GuideNeed.CursorControl,
        demo = GuideDemo.SelectLast,
    ),
    GuideStep(
        id = "selectmore", chapter = GuideChapter.Steering,
        title = "Grab a bunch. Feel powerful for a second.",
        stages = listOf(
            GuideStage("Tap three times on Space or just above it. Keep your finger down on the third tap.") { event ->
                when {
                    event is CursorStarted && event.words -> GuideVerdict.Done
                    event is CursorStarted -> roast("That's two. Count to three. We believe in you.", "Three taps, the third held.")
                    event is FieldChanged && event.end > event.start ->
                        roast("One word. We asked for a bunch.", "Keep the third tap down, then slide.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Selecting is on. Now slide left or right to select more words.") { event ->
                when {
                    event is FieldChanged && event.end > event.start -> GuideVerdict.Done
                    event is Moved && event.lifted -> roast("So close. Then you just let go.", "Three taps again, the last one held, then slide.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "Selected. Screen untouched.",
        praise = "Slide back the other way to give words back.",
        help = "Three taps, the third one held, then slide left or right. Lean back the other way to give words back.",
        cue = GuideCue.Taps(3, drag = true),
        needs = GuideNeed.CursorControl,
        demo = GuideDemo.SelectLast,
    ),
    // SYMBOLS AND EMOJI: one text box, and a field that starts empty and only grows.
    GuideStep(
        id = "alt", chapter = GuideChapter.Symbols,
        title = "Symbols the way your grandparents typed them.",
        stages = listOf(
            GuideStage("Hold Alt.", GuideCue.Spot(listOf(Layout.ALT))) { event ->
                when {
                    typedSymbol(event, "$") -> GuideVerdict.Finished
                    event is HeldSymbol && event.symbol == "$" -> counts("Skipped a slide. Overachiever.", "It counts. Press Next.")
                    event is ModifierState && event.alt != Level.Off && event.panel == Panel.Closed -> GuideVerdict.Done
                    panel(event, Panel.Symbols) -> roast("That's Sym. Wrong key, right idea.", "Press Sym to close it, then hold Alt.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Keep holding Alt and press the key with $ in its corner.") { event ->
                when {
                    typedSymbol(event, "$") -> GuideVerdict.Done
                    event is Pushed && isLetter(event.code) -> {
                        armed = true
                        GuideVerdict.Wait
                    }
                    // Alt let go before any key: it waits for one key, which works too.
                    event is Released && event.code in ALT_KEYS && !armed ->
                        roast("Fine, that works too. Alt waits for one key.", "Press the key with $ in its corner.")
                    otherSymbol(event, "$") -> roast("That's not $. Money's not your thing, huh.", "Hold Alt and press the key with $ in its corner.")
                    event is ModifierState && event.alt == Level.Locked ->
                        roast("Locked it. Commitment, finally.", "Press the key with $ in its corner. Press Alt again to let go.")
                    event is ModifierState && event.alt == Level.Off && event.panel == Panel.Closed ->
                        roast("On, off. Very decisive.", "Hold Alt and press the key with $ in its corner.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "$0. About right for what you paid.",
        praise = "Alt types the symbol in a key's corner. Press Alt once for one symbol, or hold it.",
        text = SYMBOLS_TEXT,
        symbol = "$",
        usesPanel = true,
        setup = Panel.Closed,
        demo = GuideDemo.AltLetter("$"),
    ),
    GuideStep(
        id = "holdsym", chapter = GuideChapter.Symbols,
        title = "Do this one-handed.",
        stages = listOf(GuideStage("Hold the key with @ in its corner until it turns into @.") { event ->
            when {
                event is HeldSymbol && event.symbol == "@" -> GuideVerdict.Done
                event is HeldSymbol -> roast("Not @. Close. Not really.", "Hold the key with @ in its corner.")
                event is ModifierState && event.alt != Level.Off -> {
                    armed = true
                    GuideVerdict.Wait
                }
                // A letter let go before it turned into its symbol.
                event is Pushed && isLetter(event.code) -> {
                    mark = event.code
                    GuideVerdict.Wait
                }
                event is Released && event.code == mark && !armed -> {
                    mark = -1
                    roast("Too quick. Hold it like a grudge.", "Hold the key with @ in its corner a bit longer.")
                }
                typedSymbol(event, "@") && armed -> counts("Old habits.", "It counts. Next time, just hold the key.")
                typedSymbol(event, "@") -> GuideVerdict.Done
                else -> GuideVerdict.Wait
            }
        }),
        done = "Look at you, holding things together.",
        praise = "Any letter, held, types its corner symbol.",
        text = SYMBOLS_TEXT,
        symbol = "@",
        needs = GuideNeed.HoldSymbols,
        usesPanel = true,
        setup = Panel.Closed,
        demo = GuideDemo.HoldLetter("@"),
    ),
    GuideStep(
        id = "symmain", chapter = GuideChapter.Symbols,
        title = "The key you thought was decoration.",
        stages = listOf(
            GuideStage("Press Sym.", GuideCue.Spot(listOf(Layout.SYM))) { event ->
                when {
                    panel(event, Panel.Symbols) -> GuideVerdict.Done
                    panel(event, Panel.Emoji) -> roast("Emoji already? Patience.", "Press Sym to close them, then press it once.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Find & and press its key.", GuideCue.Free) { event ->
                when {
                    typedSymbol(event, "&") -> GuideVerdict.Done
                    otherSymbol(event, "&") -> roast("Not &. Read, then type.", "Press Sym and find &.", stage = 0)
                    event is EmptyKey -> roast("That one's empty. Like this joke.", "Pick a key that shows a symbol.")
                    panel(event, Panel.Emoji) -> roast("Emoji already? Patience.", "Press Sym to close them, then press it once.")
                    panel(event, Panel.Closed) -> roast("Just looking? Window shopper.", "Press Sym again and find &.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "& that's how it's done.",
        praise = "Sym opens every symbol. Press one and the panel closes by itself.",
        text = SYMBOLS_TEXT,
        symbol = "&",
        usesPanel = true,
        setup = Panel.Closed,
        demo = GuideDemo.Insert(listOf(Layout.SYM), ""),
    ),
    GuideStep(
        id = "symgroups", chapter = GuideChapter.Symbols,
        title = "The weird ones hide further in.",
        // used holds what was used: 1 a page by swiping, 2 a page by Backspace or Enter, 4 a group by the bottom row, 8 a group
        // by swiping on the keys.
        stages = listOf(
            GuideStage("Press Sym, then swipe up or down on the keys to turn the page.") { event ->
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
                        used = used or if (event is GroupPicked) 4 else 8
                        GuideVerdict.Wait
                    }
                    typedSymbol(event, ANY_SYMBOL) -> roast("That's the boring page. The weird ones are hiding.", "Turn a page and change group first.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Now slide along the bottom row of keys to another group.") { event ->
                when {
                    event is GroupPicked -> {
                        used = used or 4
                        GuideVerdict.Done
                    }
                    otherGroup(event) -> {
                        used = used or 8
                        roast("That works too. Show-off.", "It counts. Now pick a symbol.", stage = 2)
                    }
                    panel(event, Panel.Closed) -> roast("Closed it. Bold.", "Press Sym, then swipe up or down.", stage = 0)
                    typedSymbol(event, ANY_SYMBOL) -> roast("That's the boring page. The weird ones are hiding.", "Turn a page and change group first.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Pick the weirdest symbol you can find and press its key.", GuideCue.Free) { event ->
                if (typedSymbol(event, ANY_SYMBOL)) GuideVerdict.Done else GuideVerdict.Wait
            },
        ),
        done = "We saw that. We're only a little bit judging.",
        praise = "Backspace and Enter turn pages too.",
        text = SYMBOLS_TEXT,
        symbol = ANY_SYMBOL,
        usesPanel = true,
        setup = Panel.Closed,
        demo = GuideDemo.Insert(listOf("A", "S", "D", "F", "G", "H"), ""),
    ),
    GuideStep(
        id = "clown", chapter = GuideChapter.Symbols,
        title = "Find yourself.",
        stages = listOf(
            GuideStage("Press Sym twice for the emoji.", GuideCue.Spot(listOf(Layout.SYM))) { event ->
                when {
                    panel(event, Panel.Emoji) -> GuideVerdict.Done
                    panel(event, Panel.Settings) -> roast("That's Alt. Wrong key, and the clown isn't in there.", "Press Sym to close it, then Sym twice.")
                    otherSymbol(event, CLOWN) -> roast("That's a symbol, not a clown.", "Press Sym twice for the emoji.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Find 🤡 and type it.", GuideCue.Free) { event ->
                when {
                    event is FieldChanged && event.text.trimEnd().endsWith(CLOWN) -> GuideVerdict.Done
                    otherSymbol(event, CLOWN) -> roast("That's not what we asked for. Reading is optional for you, huh.", "Find 🤡 and type it.")
                    panel(event, Panel.Settings) -> roast("That's Alt. Wrong key, and the clown isn't in there.", "Press Sym to close it, then Sym twice.", stage = 0)
                    panel(event, Panel.Closed) -> roast("Closed it? The clown's still in there. Waiting. Like a mirror.", "Press Sym twice and find 🤡.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "Hey, that's you.",
        praise = "Sym once is symbols, twice is emoji, three times closes.",
        helpTitle = "It shouldn't be that hard. Unless you're a slow learner.",
        help = "Find 🤡 in the emoji and type it.",
        text = SYMBOLS_TEXT,
        symbol = CLOWN,
        usesPanel = true,
        setup = Panel.Closed,
        rain = CLOWN,
        demo = GuideDemo.Insert(listOf(Layout.SYM, Layout.SYM), ""),
    ),
    // COPY AND PASTE: the field starts with the text box, its last word selected.
    GuideStep(
        id = "paste", chapter = GuideChapter.Clipboard,
        title = "Something's on your clipboard.",
        stages = listOf(GuideStage("Hold fn and press V.", GuideCue.Spot(listOf(Layout.FN, "V"))) { event ->
            when {
                event is FieldChanged && event.text.contains(PASTE_TEXT) -> GuideVerdict.Done
                event is KeyboardEvent.Copied && event.text != PASTE_TEXT ->
                    roast("That's copy. V for paste. Nobody knows why it's V.", "Hold fn and press V.")
                grew(event) && (event as FieldChanged).text.length - stageLength > 2 ->
                    roast("That's not ours. Ours is better.", "Hold fn and press V.")
                // The slide starts with a word selected: a cursor alone means it's gone.
                event is FieldChanged && event.start == event.end && mark != 1 -> {
                    mark = 1
                    roast("Lost the selection. Classic.", "Paste anyway. It goes where the cursor is.")
                }
                else -> GuideVerdict.Wait
            }
        }),
        done = "Yes, we know.",
        praise = "fn and V pastes. fn and C copies.",
        seed = CLIP_TEXT,
        needs = GuideNeed.FnAsCtrl,
        clip = PASTE_TEXT,
        preselect = true,
        rain = "🤪",
        demo = GuideDemo.Insert(listOf(Layout.FN, "V", Layout.FN, "V"), ""),
    ),
    GuideStep(
        id = "clipboard", chapter = GuideChapter.Clipboard,
        title = "Your clipboard. We left some favourites in there.",
        stages = listOf(
            GuideStage("Press Sym.", GuideCue.Spot(listOf(Layout.SYM))) { event ->
                when {
                    panel(event, Panel.Symbols) -> GuideVerdict.Done
                    panel(event, Panel.Emoji) -> roast("Emoji? Sure, A works from there too.", "Press A.", stage = 1)
                    panel(event, Panel.Clipboard) -> GuideVerdict.Stage(2)
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Press A.", GuideCue.Spot(listOf(Layout.CLIPBOARD_KEY))) { event ->
                when {
                    panel(event, Panel.Clipboard) -> GuideVerdict.Done
                    panel(event, Panel.Settings) -> roast("Wrong key. Again.", "Press Alt to go back, then A.")
                    panel(event, Panel.Closed) -> GuideVerdict.Stage(0)
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Rest a finger on a clip to read it, then press its key to paste it.", GuideCue.Free) { event ->
                when {
                    event is FieldChanged && event.text.contains(PRANK_PASTE) ->
                        counts("Gotcha. We swapped that one on purpose.", "It's a prank, not a bug. Chill.")
                    grew(event) -> GuideVerdict.Done
                    panel(event, Panel.Closed) -> roast("Just looking? Window shopping our insults.", "Press Sym, then A, and paste one.", stage = 0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "Pasted. Use it wisely. You won't.",
        praise = "Your clipboard keeps what you copy. Sym, then A, any time.",
        seed = CLIP_TEXT,
        usesPanel = true,
        setup = Panel.Closed,
        pinnedClips = GUIDE_CLIPS,
        demo = GuideDemo.Insert(listOf(Layout.SYM, Layout.CLIPBOARD_KEY), ""),
    ),
    GuideStep(
        id = "end", chapter = GuideChapter.End,
        title = "TitanKeys",
        body = "This concludes the guide. We have nothing left to teach you, mostly because you couldn't handle more.",
        done = "The keys your Titan deserves. Whether you deserve them is a separate conversation.",
        text = END_TEXT,
        offersShare = true,
        check = wait,
    ),
)

/** What the symbols groups slide says once done: the way to turn pages or change group that wasn't used. See its [GuideMemory.count]. */
internal fun groupsPraise(used: Int) = when {
    used and 2 == 0 -> "Backspace and Enter turn pages too."
    used and 8 == 0 -> "Swiping left or right on the keys changes group too."
    else -> "The bottom row works both ways."
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
    /** What the slide counted, such as which ways the groups slide was used. */
    val counted get() = memory.used

    /** The slide was done in a way that does the next slide too, which then shows done. */
    val jumps get() = memory.jump

    /** Tells the step what the field holds as it starts, so that a stage can tell when something is typed or pasted. */
    fun seed(text: String) {
        memory.length = text.length
        memory.stageLength = text.length
    }

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
