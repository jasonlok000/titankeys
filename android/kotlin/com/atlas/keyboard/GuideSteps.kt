package com.atlas.keyboard

import com.atlas.keyboard.KeyboardEvent.Chosen
import com.atlas.keyboard.KeyboardEvent.Copied
import com.atlas.keyboard.KeyboardEvent.CursorStarted
import com.atlas.keyboard.KeyboardEvent.DeleteHoldStarted
import com.atlas.keyboard.KeyboardEvent.DoubleTapped
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
internal enum class GuideChapter(val label: String) {
    Typing("Typing"),
    Editing("Delete and move"),
    Clipboard("Copy and paste"),
    Symbols("Symbols"),
    Emoji("Emoji"),
    Settings("Quick settings"),
    /** The name, and the one boring thing: the offer to share swipes, until it has an answer. No keys on this one. */
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
internal class Passage(val text: String) {
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
        for ((index, piece) in pieces.withIndex()) {
            val letters = GuideScript.letters(piece)
            if (letters.isEmpty()) continue
            if (index == pieces.lastIndex && open) return Track(next, done, letters)
            val ahead = (0..LOOK_AHEAD).firstOrNull { GuideScript.letters(wordAt(next + it)) == letters } ?: continue
            done += next + ahead
            next += ahead + 1
        }
        return Track(next, done, "")
    }

    private companion object {
        /** How many words past the next one a typed word may match. */
        const val LOOK_AHEAD = 3
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
    /** Some phones already have a Ctrl key, so this only shows with the step's help. */
    FnAsCtrl("Use fn as Ctrl", optional = true),
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
    data class Hint(val text: String) : GuideVerdict
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
        else -> verdict
    }
}

/**
 * One slide. The words and the check are in one place, so that a gesture that changes is easy to change here.
 * [title], [body] and [done] are what the user reads, in ASD-STE100. They have no numbers, because this guide is not a manual.
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
    /** Shows after a time with no success, for a step where a feature may not be set up. */
    val help: String? = null,
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
    /** The slide's own text to type along to, and the one that takes its place once the slide is done. See [Passage]. */
    val text: String = "",
    val after: String = "",
    /** The slide offers to share swipes: its offer opens the disclosure, and an answer finishes it. */
    val offersShare: Boolean = false,
    val check: GuideMemory.(KeyboardEvent) -> GuideVerdict = staged(stages),
)

/** A swipe or touch step heard a key go down, which is a push and not a touch. */
private const val PUSHED = "That pressed a key. Touch lightly and slide across the letters."

/** From android.view.KeyEvent, so that the steps can be tested without Android. */
private fun isLetter(code: Int) = code in 29..54
/** Backspace, from android.view.KeyEvent. */
private const val KEY_DEL = 67
/** The emoji that the emoji chapter has you find, just for fun. */
internal const val CLOWN = "🤡"

/** Counts the swipes that typed a word, whatever the word, and is done after [words]. */
private fun swipes(words: Int): GuideMemory.(KeyboardEvent) -> GuideVerdict = { event ->
    when (event) {
        is Recognised -> if (event.words.isNotEmpty() && ++count >= words) GuideVerdict.Done else GuideVerdict.Wait
        is Tapped -> GuideVerdict.Hint("That was a tap. Don't lift between the letters of a word.")
        is Pushed -> if (isLetter(event.code)) GuideVerdict.Hint(PUSHED) else GuideVerdict.Wait
        else -> GuideVerdict.Wait
    }
}

private val wait: GuideMemory.(KeyboardEvent) -> GuideVerdict = { GuideVerdict.Wait }

private fun panel(event: KeyboardEvent, panel: Panel) = event is ModifierState && event.panel == panel

/** A swipe or a touch that showed another group of the panel's symbols or emoji. */
private fun otherGroup(event: KeyboardEvent) = event is GroupPicked || event is PanelSwiped && event.moved && abs(event.across) > abs(event.down)

/** What the paste lesson puts on your clipboard. */
internal const val PASTE_TEXT = "I'm an idiot."

/** What the clipboard lesson pins: a story in five parts, each one ending so that you want the next. */
internal val GUIDE_CLIPS = listOf(
    "Day one with real keys on my phone. I typed a whole email without looking. Then my boss called. To be continued...",
    "Boss asked how I type so fast. I said practice. It was the Titan. I felt like a fraud. But then...",
    "The group chat found out. Now everyone wants me to type their messages. I charge by the word. Next pin...",
    "Business is booming. I swipe, they pay. My thumbs have an accountant now. You won't believe what happened next...",
    "Nothing happened. We just wanted you to read all five. Thanks for pasting, you absolute legend.",
)

/** What the end of the delete slide says to anyone who held before they flicked once. */
internal const val AHEAD_OF_YOURSELF = "Wow, straight to the advanced stuff. Cute. One quick flick first, then the hold."

/** The guide's one list: six chapters of small slides, then the end. Each slide's words, text, demonstration and check are together. */
internal val GUIDE_STEPS = listOf(
    // Typing: the keys you know, then swiping on them, then fixing a swipe, then the word menu.
    GuideStep(
        id = "type", chapter = GuideChapter.Typing,
        title = "The old keyboard? Kinda shit.",
        stages = listOf(
            GuideStage("This one isn't. Type the text in the box. Like a normal person.") { event ->
                if (event is Pushed && isLetter(event.code)) GuideVerdict.Done else GuideVerdict.Wait
            },
            GuideStage("Now Space. It fixes your typos, so you can keep pretending.", GuideCue.Spot(listOf(Layout.SPACE))) { event ->
                if (event is FieldChanged && event.text.endsWith(' ')) GuideVerdict.Done else GuideVerdict.Wait
            },
        ),
        done = "Fixed. Nobody saw a thing.",
        text = "this keyboard fixes your typos so you can keep pretending you never make any",
        after = "see nobody noticed a thing and your dignity is still mostly intact",
        demo = GuideDemo.TypeSlip,
        bar = GuideBar.Fix,
    ),
    GuideStep(
        id = "swipe", chapter = GuideChapter.Typing,
        title = "Now swipe on the same keys.",
        stages = listOf(GuideStage("Slide over the letters of the next word in the box. Don't press. Lift.", check = swipes(BASIC_SWIPES))),
        done = "One swipe, one word. Easy.",
        text = "swipe these words without lifting and pretend you have always done it this way",
        after = "one swipe one word and your thumbs are already less tired",
        cue = GuideCue.NextWord,
        needs = GuideNeed.SwipeTyping,
        demo = GuideDemo.SwipeNext,
    ),
    GuideStep(
        id = "strip", chapter = GuideChapter.Typing,
        title = "Missed? One tap fixes it.",
        stages = listOf(
            GuideStage("Swipe the next word. We'll get it wrong on purpose. Every time.") { event ->
                when {
                    event is Chosen -> GuideVerdict.Finished
                    event is Recognised && event.words.isNotEmpty() -> GuideVerdict.Done
                    event is Tapped -> GuideVerdict.Hint("That was a tap. A swipe doesn't lift between letters.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("See? Wrong. Tap the right word on the bar, the one that's dancing.", GuideCue.Free) { event ->
                if (event is Chosen) GuideVerdict.Done else GuideVerdict.Wait
            },
        ),
        done = "Fixed with one tap. Keep going, it stays wrong.",
        text = "every word you swipe here comes out wrong because we said so",
        after = "keep swiping if you like it is still going to be wrong",
        cue = GuideCue.NextWord,
        needs = GuideNeed.SwipeTyping,
        nearMiss = true,
        demo = GuideDemo.FixNext,
    ),
    GuideStep(
        id = "menu", chapter = GuideChapter.Typing,
        title = "Hold a word for its menu.",
        stages = listOf(
            GuideStage("Hold a word on the bar, right above the keys. Any word. Judge it.") { event ->
                when (event) {
                    is WordMenu -> GuideVerdict.Done
                    is Chosen -> GuideVerdict.Hint("That was a tap, so it typed the word. Hold it this time.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Rest a finger on the top rows to mark a button. Press to pick.") { event ->
                if (event is MenuPicked) GuideVerdict.Done else GuideVerdict.Wait
            },
        ),
        done = "Your words, your call.",
        text = "hold any word on the bar and judge it like you judge everyone",
        after = "save the good ones and forget the bad ones just like real life",
        bar = GuideBar.Menu,
    ),
    // Delete and move: Backspace as you know it, then one flick, then the hold, then steering and selecting from Space.
    GuideStep(
        id = "flick", chapter = GuideChapter.Editing,
        title = "Delete without reaching up.",
        stages = listOf(
            GuideStage("Press Backspace. Yes, the old way. Humour us.", GuideCue.Spot(listOf(Layout.BACKSPACE))) { event ->
                when {
                    event is Pushed && event.code == KEY_DEL -> GuideVerdict.Done
                    event is DeleteHoldStarted -> GuideVerdict.Hint(AHEAD_OF_YOURSELF)
                    event is DeletedWord -> GuideVerdict.Stage(1)
                    else -> GuideVerdict.Wait
                }
            },
            // One quick flick first. A hold here is ahead of itself: it says so, and its words don't count.
            GuideStage("Now one quick flick left from the shaded keys, and lift. One word.") { event ->
                when {
                    event is DeleteHoldStarted -> {
                        mark = 1
                        GuideVerdict.Hint(AHEAD_OF_YOURSELF)
                    }
                    event is DeletedWord && event.held -> GuideVerdict.Wait
                    event is DeletedWord && mark == 1 -> {
                        mark = -1
                        GuideVerdict.Wait
                    }
                    event is DeletedWord -> GuideVerdict.Done
                    event is Recognised -> GuideVerdict.Hint("That typed a word. Start in the shaded patch and flick left, fast.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Now flick and keep your finger down. Watch your regrets go.") { event ->
                if (event is DeletedWord && event.held) GuideVerdict.Done else GuideVerdict.Wait
            },
        ),
        done = "Gone. Like your patience.",
        text = "delete this sentence one word at a time because you clearly regret all of it",
        after = "hold the flick and watch your mistakes go faster than your excuses",
        cue = GuideCue.Flick(hold = true),
        needs = GuideNeed.SwipeTyping,
        needsWords = 8,
        demo = GuideDemo.DeleteLast,
    ),
    GuideStep(
        id = "steer", chapter = GuideChapter.Editing,
        title = "Steer from Space.",
        stages = listOf(
            GuideStage("Tap twice, quick, just above Space. Keep the second tap down.") { event ->
                when (event) {
                    is CursorStarted -> GuideVerdict.Done
                    is DoubleTapped -> GuideVerdict.Hint("You lifted, so that picked a word. Tap twice and keep the second one down.")
                    is Recognised -> GuideVerdict.Hint("That typed a word. Quick taps just above the middle of Space.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Got it. Now slide left or right, finger still down.") { event ->
                when {
                    event is FieldChanged && event.end > event.start -> GuideVerdict.Done
                    event is FieldChanged -> {
                        if (mark < 0) mark = event.start
                        if (abs(event.start - mark) >= CURSOR_TRAVEL_CHARACTERS) GuideVerdict.Done else GuideVerdict.Wait
                    }
                    // Lifted before sliding: start again, from the taps.
                    event is Moved && event.lifted -> GuideVerdict.Stage(0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "No more poking the glass.",
        help = "Tap lightly in the shaded patch, and keep your last touch down while you slide.",
        text = "move the cursor around this line without touching the screen like a grownup",
        after = "the screen is for watching videos not for poking at text",
        cue = GuideCue.Taps(2, drag = true),
        needs = GuideNeed.CursorControl,
        needsWords = 6,
        demo = GuideDemo.MoveCursor,
    ),
    GuideStep(
        id = "select", chapter = GuideChapter.Editing,
        title = "Select from Space.",
        stages = listOf(
            GuideStage("Tap three times just above Space. Keep the third tap down.") { event ->
                when {
                    event is CursorStarted && event.words -> GuideVerdict.Done
                    event is CursorStarted -> GuideVerdict.Hint("That's two taps. Two steers. Count to three, we believe in you.")
                    event is FieldChanged && event.end > event.start -> GuideVerdict.Finished
                    event is Recognised -> GuideVerdict.Hint("That typed a word. Quick taps just above the middle of Space.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Got it. Now slide to grab more words, finger still down.") { event ->
                when {
                    event is FieldChanged && event.end > event.start -> GuideVerdict.Done
                    event is Moved && event.lifted -> GuideVerdict.Stage(0)
                    else -> GuideVerdict.Wait
                }
            },
        ),
        done = "Selected. Glass untouched.",
        help = "Two taps that lift select the word at the cursor. Three, with the last one down, select more.",
        text = "select a few of these words and feel powerful for a second",
        after = "selected and not a single fingerprint on the glass",
        cue = GuideCue.Taps(3, drag = true),
        needs = GuideNeed.CursorControl,
        needsWords = 6,
        demo = GuideDemo.SelectLast,
    ),
    // Copy and paste: after selecting, so that something is selected. Paste first, then the clipboard.
    GuideStep(
        id = "paste", chapter = GuideChapter.Clipboard,
        title = "Paste without the screen.",
        stages = listOf(GuideStage("We put something lovely on your clipboard. Hold fn, press V.", GuideCue.Spot(listOf(Layout.FN, "V"))) { event ->
            if (event is FieldChanged && event.text.contains(PASTE_TEXT)) GuideVerdict.Done else GuideVerdict.Wait
        }),
        done = "Yes, we know.",
        help = "Nothing pasted? Some phones need fn as Ctrl.",
        text = "go on paste it we promise it is something nice",
        after = "you pasted it yourself so it must be true",
        needs = GuideNeed.FnAsCtrl,
        needsWords = 4,
        clip = PASTE_TEXT,
        preselect = true,
        rain = "🙄",
        demo = GuideDemo.Insert(listOf(Layout.FN, "V"), PASTE_TEXT),
    ),
    GuideStep(
        id = "clipboard", chapter = GuideChapter.Clipboard,
        title = "Your clipboard, keys only.",
        stages = listOf(
            GuideStage("Press Sym.", GuideCue.Spot(listOf(Layout.SYM))) { event ->
                when {
                    panel(event, Panel.Symbols) -> GuideVerdict.Done
                    panel(event, Panel.Clipboard) -> GuideVerdict.Stage(2)
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Now A. We left you a little story in the pins.", GuideCue.Spot(listOf(Layout.CLIPBOARD_KEY))) { event ->
                when {
                    panel(event, Panel.Clipboard) -> GuideVerdict.Done
                    panel(event, Panel.Emoji) -> GuideVerdict.Hint("That's the emoji. Press Sym to close them, then Sym once, then A.")
                    panel(event, Panel.Closed) -> GuideVerdict.Stage(0)
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Rest on a clip to read it. Press its key to paste it.", GuideCue.Free) { event ->
                if (grew(event) || panel(event, Panel.Closed)) GuideVerdict.Done else GuideVerdict.Wait
            },
        ),
        done = "Pinned. They're never leaving.",
        text = "your clipboard remembers everything you copied even the stuff you regret",
        after = "everything you copy ends up here so maybe copy nicer things",
        usesPanel = true,
        setup = Panel.Closed,
        pinnedClips = GUIDE_CLIPS,
        demo = GuideDemo.Insert(listOf(Layout.SYM, Layout.CLIPBOARD_KEY), ""),
    ),
    // Symbols: Alt as on any keyboard, then holding a key, then the Sym panel, then its other groups.
    GuideStep(
        id = "alt", chapter = GuideChapter.Symbols,
        title = "Symbols, the old way.",
        stages = listOf(
            GuideStage("Press Alt.", GuideCue.Spot(listOf(Layout.ALT))) { event ->
                if (event is ModifierState && event.alt != Level.Off) GuideVerdict.Done else GuideVerdict.Wait
            },
            GuideStage("Now J. You get the tiny symbol in its corner.", GuideCue.Spot(listOf("J"))) { event ->
                if (event is Pushed && isLetter(event.code)) GuideVerdict.Done else GuideVerdict.Wait
            },
        ),
        done = "Old school. Still works.",
        text = "press alt and then a letter to get its symbol like it is the old days",
        after = "that symbol was printed on the key the whole time you just never looked",
        usesPanel = true,
        setup = Panel.Closed,
        demo = GuideDemo.AltLetter("J"),
    ),
    GuideStep(
        id = "hold", chapter = GuideChapter.Symbols,
        title = "Hold a letter for its symbol.",
        stages = listOf(GuideStage("Hold J until it turns into its symbol. Alt can take a nap.") { event ->
            if (event is HeldSymbol) GuideVerdict.Done else GuideVerdict.Wait
        }),
        done = "Quicker than Alt, right?",
        text = "hold a letter long enough and it turns into a symbol like magic but slower",
        after = "holding keys works out better than holding grudges",
        // Holding a letter can be off, so then the slide offers it at once.
        cue = GuideCue.Spot(listOf("J")),
        needs = GuideNeed.HoldSymbols,
        demo = GuideDemo.HoldLetter("J"),
    ),
    GuideStep(
        id = "sym", chapter = GuideChapter.Symbols,
        title = "Every symbol within reach.",
        stages = listOf(
            GuideStage("Press Sym.", GuideCue.Spot(listOf(Layout.SYM))) { event ->
                when {
                    panel(event, Panel.Symbols) -> GuideVerdict.Done
                    panel(event, Panel.Emoji) -> GuideVerdict.Hint("Too far, that's emoji. Press Sym to close them, then once.")
                    else -> GuideVerdict.Wait
                }
            },
            // Typing a symbol closes the panel, and so does Sym. Either way the panel has been seen and used.
            GuideStage("Each key shows a symbol now. Press one to type it.", GuideCue.Free) { event ->
                if (grew(event) || panel(event, Panel.Closed)) GuideVerdict.Done else GuideVerdict.Wait
            },
        ),
        done = "All of them, right under your thumbs.",
        text = "every symbol you never knew you needed lives right behind sym",
        after = "now you know where the weird stuff lives",
        usesPanel = true,
        setup = Panel.Closed,
        demo = GuideDemo.Insert(listOf(Layout.SYM, "W"), "&"),
    ),
    GuideStep(
        id = "symgroups", chapter = GuideChapter.Symbols,
        title = "There are weirder symbols.",
        stages = listOf(
            GuideStage("Swipe left or right on the keys for another group.") { event ->
                when {
                    otherGroup(event) -> GuideVerdict.Done
                    event is PanelSwiped && event.moved -> GuideVerdict.Hint("That's another page of the same group. Sideways, not up or down.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Find the rudest-looking symbol in there and type it.") { event ->
                if (grew(event) || panel(event, Panel.Closed)) GuideVerdict.Done else GuideVerdict.Wait
            },
        ),
        done = "We saw that. We're not judging. Much.",
        text = "swipe sideways for more symbols and find the rudest one in there",
        after = "we saw what you picked and we are only a little bit judging",
        usesPanel = true,
        setup = Panel.Symbols,
        demo = GuideDemo.Insert(listOf("A", "S", "D", "F", "G", "H"), ""),
    ),
    // Emoji: the same shape. Open them, change group, and then find the clown.
    GuideStep(
        id = "emoji", chapter = GuideChapter.Emoji,
        title = "Emoji, keys only.",
        stages = listOf(
            GuideStage("Press Sym.", GuideCue.Spot(listOf(Layout.SYM))) { event ->
                when {
                    panel(event, Panel.Symbols) -> GuideVerdict.Done
                    panel(event, Panel.Emoji) -> GuideVerdict.Stage(2)
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Press it again for emoji.", GuideCue.Spot(listOf(Layout.SYM))) { event ->
                when {
                    panel(event, Panel.Emoji) -> GuideVerdict.Done
                    panel(event, Panel.Settings) -> GuideVerdict.Hint("That's Alt, the quick settings. Sym is the one you want.")
                    panel(event, Panel.Closed) -> GuideVerdict.Stage(0)
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Pick one from Recent that matches your mood. Press its key.", GuideCue.Free) { event ->
                if (grew(event) || panel(event, Panel.Closed)) GuideVerdict.Done else GuideVerdict.Wait
            },
        ),
        done = "Look at you, with feelings.",
        text = "press sym twice and pick an emoji that matches your mood",
        after = "great now your messages have feelings",
        usesPanel = true,
        setup = Panel.Closed,
        demo = GuideDemo.Insert(listOf(Layout.SYM, Layout.SYM, "W"), "😎"),
    ),
    GuideStep(
        id = "emojigroups", chapter = GuideChapter.Emoji,
        title = "More faces than you need.",
        stages = listOf(
            GuideStage("Swipe left or right on the keys for another group.") { event ->
                when {
                    otherGroup(event) -> GuideVerdict.Done
                    event is PanelSwiped && event.moved -> GuideVerdict.Hint("That's another page of the same group. Sideways, not up or down.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Press a key to type one.") { event ->
                if (grew(event) || panel(event, Panel.Closed)) GuideVerdict.Done else GuideVerdict.Wait
            },
        ),
        done = "Plenty more where that came from.",
        text = "swipe sideways for other groups because one face is never enough",
        after = "there are more emoji than you will ever need and you will use three",
        usesPanel = true,
        setup = Panel.Emoji,
        demo = GuideDemo.Insert(listOf("A", "S", "D", "F", "G", "H"), ""),
    ),
    GuideStep(
        id = "clown", chapter = GuideChapter.Emoji,
        title = "Find the clown.",
        stages = listOf(GuideStage("Find the clown and type it. No clown, no next slide.") { event ->
            when {
                event is FieldChanged && event.text.contains(CLOWN) -> GuideVerdict.Done
                grew(event) -> GuideVerdict.Hint("Nice, but that's not the clown. Smileys, swipe up for more.")
                else -> GuideVerdict.Wait
            }
        }),
        done = "Hey, that's you.",
        help = "Smileys are the first group after Recent. Swipe up or down for more pages.",
        text = "find the clown in the emoji and type it to move on",
        after = "hey that is you",
        usesPanel = true,
        setup = Panel.Emoji,
        rain = CLOWN,
        demo = GuideDemo.Insert(listOf("E", "R", "T"), "❓"),
    ),
    // Quick settings: open them, look around, and close them with Sym.
    GuideStep(
        id = "quick", chapter = GuideChapter.Settings,
        title = "Settings, keys only.",
        stages = listOf(
            GuideStage("Press Sym.", GuideCue.Spot(listOf(Layout.SYM))) { event ->
                when {
                    panel(event, Panel.Symbols) -> GuideVerdict.Done
                    panel(event, Panel.Settings) -> GuideVerdict.Stage(2)
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Now press Alt for quick settings.", GuideCue.Spot(listOf(Layout.ALT))) { event ->
                when {
                    panel(event, Panel.Settings) -> GuideVerdict.Done
                    panel(event, Panel.Closed) -> GuideVerdict.Stage(0)
                    panel(event, Panel.Emoji) -> GuideVerdict.Hint("Those are the emoji. Press Sym to close them, then press it once.")
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Rest a finger on a key to see what it switches. O is off limits here.", GuideCue.Free) { event ->
                when {
                    event is Resting && event.key.isNotEmpty() -> GuideVerdict.Done
                    panel(event, Panel.Closed) -> GuideVerdict.Stage(0)
                    else -> GuideVerdict.Wait
                }
            },
            GuideStage("Done snooping? Sym closes it.", GuideCue.Spot(listOf(Layout.SYM))) { event ->
                if (panel(event, Panel.Closed)) GuideVerdict.Done else GuideVerdict.Wait
            },
        ),
        done = "Look at you, all set up.",
        help = "Outside the guide, O opens all of TitanKeys' settings.",
        text = "quick settings live behind sym and alt so you never need the settings app",
        after = "close it with sym and act like you knew that already",
        usesPanel = true,
        setup = Panel.Closed,
        demo = GuideDemo.Insert(listOf(Layout.SYM, Layout.ALT, Layout.SYM), ""),
    ),
    GuideStep(
        id = "end", chapter = GuideChapter.End,
        title = "TitanKeys",
        body = "The keys your Titan deserves.",
        done = "The keys your Titan deserves.",
        text = "you made it to the end now go type something real",
        after = "you made it to the end now go type something real",
        offersShare = true,
        check = wait,
    ),
)

/**
 * Something that is easy to do by chance and stops a step from working. The guide says what happened and how to go back,
 * and the step goes on as before. A step's own check comes first, so a step that wants one of these things gets it.
 */
internal fun commonMistake(step: GuideStep, event: KeyboardEvent): String? = when {
    event is ModifierState && event.panel != Panel.Closed && !step.usesPanel ->
        "That opened the Sym panel. Press Sym until it closes, then try again."
    event is ModifierState && event.alt != Modifiers.Level.Off && !step.usesPanel ->
        "Alt is on, so the touchpad won't type. Press Alt to turn it off."
    event is CursorStarted && step.cue !is GuideCue.Taps ->
        "That's the cursor. Quick taps just above the middle of Space start it. Lift your finger to stop."
    event is HeldSymbol && step.id != "hold" ->
        "Holding a key types its symbol. Press quickly for letters."
    else -> null
}

/** Swipes that finish the swipe step. The guide introduces swiping, so one word shows it; the line above goes on guiding after that. */
private const val BASIC_SWIPES = 1
/** The cursor must move this far along the text. */
private const val CURSOR_TRAVEL_CHARACTERS = 3

/** Where the guide is and what happens next. It knows nothing of the screen, so a test can run it with no phone. */
internal class GuideFlow(
    private val settings: () -> GuideSettings,
    private val steps: List<GuideStep> = GUIDE_STEPS,
) {
    private var position = 0
    private var memory = GuideMemory()

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
    val stepInChapter get() = steps.subList(0, position).count { it.chapter == step.chapter && !it.skipWhen(settings()) }
    val stepsInChapter get() = steps.count { it.chapter == step.chapter && !it.skipWhen(settings()) }

    /** How far through the steps the guide is. The ending is not a step to do. */
    val stepIndex get() = position
    val stepCount get() = steps.size - 1

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

    /** Tells the step what the field holds as it starts, so that a stage can tell when something is typed or pasted. */
    fun seed(text: String) {
        memory.length = text.length
        memory.stageLength = text.length
    }

    /** What the step makes of [event]. If it waits, a mistake that is easy to make gets a hint that says what happened. */
    fun event(event: KeyboardEvent): GuideVerdict {
        if (finished) return GuideVerdict.Wait
        val verdict = step.check(memory, event)
        if (verdict != GuideVerdict.Wait) return verdict
        return commonMistake(step, event)?.let { GuideVerdict.Hint(it) } ?: GuideVerdict.Wait
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

    private fun previous(): Int? {
        var at = position - 1
        while (at >= 0 && steps[at].skipWhen(settings())) at--
        return at.takeIf { it >= 0 }
    }

    /** Starts from the first step, or from the step with this id. */
    fun start(id: String? = null) {
        moveTo(firstFrom(steps.indexOfFirst { it.id == id }.coerceAtLeast(0)))
    }

    /** A step that will not show moves the guide on to the first one after it. The ending always shows. */
    private fun firstFrom(from: Int): Int {
        var at = from.coerceAtMost(steps.lastIndex)
        while (at < steps.lastIndex && steps[at].skipWhen(settings())) at++
        return at
    }

    private fun moveTo(next: Int) {
        position = next
        memory = GuideMemory()
    }
}
