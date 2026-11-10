package com.atlas.keyboard

import com.atlas.keyboard.KeyboardEvent.Chosen
import com.atlas.keyboard.KeyboardEvent.CursorStarted
import com.atlas.keyboard.KeyboardEvent.DeletedWord
import com.atlas.keyboard.KeyboardEvent.FieldChanged
import com.atlas.keyboard.KeyboardEvent.HeldSymbol
import com.atlas.keyboard.KeyboardEvent.ModifierState
import com.atlas.keyboard.KeyboardEvent.Pushed
import com.atlas.keyboard.KeyboardEvent.Recognised
import com.atlas.keyboard.KeyboardEvent.Tapped
import com.atlas.keyboard.Modifiers.Panel
import kotlin.math.abs

/** The background of the guide: ink while you try a step, and orange once you have done it. */
internal enum class GuideTone { Orange, Ink }

/**
 * One part of the story, with the [label] that shows above its steps. The story starts from the typing that everyone knows and
 * shows, one slide for each idea, that all the rest comes from the same keys. Each slide introduces its idea and lets you try it.
 */
internal enum class GuideChapter(val label: String) {
    Story("Everything from the keys"),
    End("Built for Titan 2 Elite"),
}

/**
 * Slide zero: the parts of setup that only the phone's Settings can change, in the order that the guide asks for them. The guide
 * opens the page for each part, sees the switch go on, and comes back by itself, so that each part takes a touch or two.
 * [title] and [body] follow the same rules as a step's words. [help] shows if you come back with the part still not done.
 */
internal enum class SetupPart(val title: String, val body: String, val action: String, val help: String? = null) {
    Enable("Switch on the keyboard.", "Turn on TitanKeys in the list. The guide comes back by itself.", "Open the keyboard list",
        "TitanKeys can be further down the list. Turn on its switch, then push OK two times."),
    Choose("Make it your keyboard.", "Select TitanKeys in the list. That is all.", "Select TitanKeys"),
    // Android shows a list of all apps here, not the page for TitanKeys alone.
    Overlay("Allow it over other apps.", "Allow TitanKeys in the list. This keeps the touchpad on the keys.", "Open the list",
        "Find TitanKeys in the list, open it, and turn on its switch."),
    ScrollAssistant("Turn on Scroll assistant.", "Swiping needs it to read the touchpad. Turn on its switch.", "Open Scroll assistant",
        "Turn on the switch at the top of that page."),
    Back("Let TitanKeys handle Back.", "In Accessibility, turn on TitanKeys. Back then works in one push.", "Open Accessibility",
        "Is the switch grey? In App info, open the menu and allow restricted settings."),
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
}

/**
 * The text that the guide suggests you type, written out in the line above the field. It is one paragraph about TitanKeys, and the
 * whole guide is one text in one field: each word that you swipe or type moves the line on to the next word, whatever the word was,
 * so that nothing waits on one word. After the last word it starts again.
 */
internal object GuideScript {
    const val TEXT = "Nothing to unlearn: type with two hands or one, and TitanKeys fixes the typos as you go. Then swipe whole " +
        "words on the same keys. A missed word is one touch from right. Flick to delete, steer and select from Space, and " +
        "reach every symbol and emoji without the screen. This is the ultimate keyboard, and these are the keys your Titan " +
        "deserves."

    val words = TEXT.split(' ')

    /** The letters of a word that a swipe goes through. */
    fun letters(word: String) = word.filter { it.isLetter() }.lowercase()

    /**
     * How many words of the script [text] has. Each word counts, so that a word that came out different still moves the line on.
     * A last word that is still being typed, the start of the word that is next, does not count until it is whole.
     */
    fun progress(text: String): Int {
        val typed = text.split(' ', '\n').filter { it.isNotEmpty() }
        var done = typed.size
        if (done > 0 && !text.endsWith(' ') && !text.endsWith('\n')) {
            val last = letters(typed.last())
            val expected = letters(wordAt(done - 1))
            if (last != expected && expected.startsWith(last)) done--
        }
        return done
    }

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
     * The script from word [index] on, at least [count] words and to the end of a sentence, to put in the field for a step that
     * needs text to work on. It goes on from where the text is, so that the line above stays in step with the field.
     */
    fun continueFrom(index: Int, count: Int): String {
        val added = ArrayList<String>()
        var at = index
        while (added.size < count || !wordAt(at - 1).endsWith('.')) added += wordAt(at++)
        return added.joinToString(" ")
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
    data object Done : GuideVerdict
}

/** A step's own memory. Each step starts with a new one. */
internal class GuideMemory {
    var count = 0
    var armed = false
    var mark = -1
}

/**
 * One slide. The words and the check are in one place, so that a gesture that changes is easy to change here.
 * [title], [body] and [done] are what the user reads, in ASD-STE100. They have no numbers, because this guide is not a manual.
 */
internal class GuideStep(
    val id: String,
    val chapter: GuideChapter,
    val title: String,
    val body: String,
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
    /** The keyboard makes the next swipe its second guess, on purpose, so that the step can teach the strip. See [GuideHooks]. */
    val nearMiss: Boolean = false,
    /** What the slide shows by itself when it opens and when nobody acts. */
    val demo: GuideDemo? = null,
    val check: GuideMemory.(KeyboardEvent) -> GuideVerdict,
)

/** A swipe or touch step heard a key go down, which is a push and not a touch. */
private const val PUSHED = "That pushed a key. Touch lightly and slide across the letters."

/** From android.view.KeyEvent, so that the steps can be tested without Android. */
private fun isLetter(code: Int) = code in 29..54

/** Counts the swipes that typed a word, whatever the word, and is done after [words]. */
private fun swipes(words: Int): GuideMemory.(KeyboardEvent) -> GuideVerdict = { event ->
    when (event) {
        is Recognised -> if (event.words.isNotEmpty() && ++count >= words) GuideVerdict.Done else GuideVerdict.Wait
        is Tapped -> GuideVerdict.Hint("That was a touch. Do not lift between the letters of a word.")
        is Pushed -> if (isLetter(event.code)) GuideVerdict.Hint(PUSHED) else GuideVerdict.Wait
        else -> GuideVerdict.Wait
    }
}

/** The guide's one list: the story, then the ending. Each slide's words, demonstration and check are together. */
internal val GUIDE_STEPS = listOf(
    GuideStep(
        id = "type", chapter = GuideChapter.Story,
        title = "Nothing to unlearn.",
        body = "Type as you always have. Typos fix themselves when you push Space.",
        done = "Typos fixed as you type.",
        demo = GuideDemo.TypeNext,
        check = { event ->
            when {
                event is Pushed && isLetter(event.code) -> {
                    armed = true
                    GuideVerdict.Wait
                }
                event is FieldChanged && armed && event.text.endsWith(' ') -> GuideVerdict.Done
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "swipe", chapter = GuideChapter.Story,
        title = "Now swipe on the same keys.",
        body = "Slide across the letters of the next word above. Do not push. Then lift.",
        done = "One swipe. One word.",
        cue = GuideCue.NextWord,
        needs = GuideNeed.SwipeTyping,
        demo = GuideDemo.SwipeNext,
        check = swipes(BASIC_SWIPES),
    ),
    GuideStep(
        id = "strip", chapter = GuideChapter.Story,
        title = "Missed? One touch fixes it.",
        body = "Swipe the next word. It comes out wrong on purpose. Touch the right one.",
        done = "The right word, one touch away.",
        cue = GuideCue.NextWord,
        needs = GuideNeed.SwipeTyping,
        nearMiss = true,
        demo = GuideDemo.FixNext,
        check = { event ->
            when (event) {
                is Chosen -> GuideVerdict.Done
                is Recognised -> if (event.words.size < 2) GuideVerdict.Hint("That word had nothing else to offer. Swipe the next one.") else GuideVerdict.Wait
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "flick", chapter = GuideChapter.Story,
        title = "Delete without reaching up.",
        body = "Flick left from Backspace. Keep your finger down to delete more.",
        done = "Gone.",
        cue = GuideCue.Flick(hold = true),
        needs = GuideNeed.SwipeTyping,
        needsWords = 4,
        demo = GuideDemo.DeleteLast,
        check = { event ->
            when (event) {
                is DeletedWord -> GuideVerdict.Done
                is Recognised -> GuideVerdict.Hint("That typed a word. Start on Backspace and move left quickly.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "steer", chapter = GuideChapter.Story,
        title = "Steer and select from Space.",
        body = "Tap just above the middle of Space, then touch and slide to steer.",
        done = "No screen needed.",
        help = "Tap twice first to select. Tap lightly in the shaded patch, and keep the last touch down as you slide.",
        cue = GuideCue.Taps(2, drag = true),
        needs = GuideNeed.CursorControl,
        needsWords = 6,
        demo = GuideDemo.MoveCursor,
        check = { event ->
            when {
                event is CursorStarted -> {
                    armed = true
                    mark = -1
                    GuideVerdict.Wait
                }
                event is FieldChanged && event.end > event.start -> GuideVerdict.Done
                event is FieldChanged && armed -> {
                    if (mark < 0) mark = event.start
                    if (abs(event.start - mark) >= CURSOR_TRAVEL_CHARACTERS) GuideVerdict.Done else GuideVerdict.Wait
                }
                event is Recognised -> GuideVerdict.Hint("That typed a word. Start with quick taps just above the middle of Space.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "symbols", chapter = GuideChapter.Story,
        title = "Every symbol within reach.",
        body = "Hold J for the symbol in its corner, or push Sym for all the rest.",
        done = "No menus to dig through.",
        // Holding a letter can be off, so then the slide offers it at once. Sym works either way.
        cue = GuideCue.Spot(listOf("J", Layout.SYM)),
        needs = GuideNeed.HoldSymbols,
        usesPanel = true,
        setup = Panel.Closed,
        demo = GuideDemo.HoldLetter("J"),
        check = { event ->
            when {
                event is HeldSymbol -> GuideVerdict.Done
                event is ModifierState && event.panel != Panel.Closed -> {
                    armed = true
                    GuideVerdict.Wait
                }
                event is ModifierState && armed -> GuideVerdict.Done
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "panel", chapter = GuideChapter.Story,
        title = "Emoji and settings, no screen.",
        body = "Push Sym two times for emoji. In the panel, Alt opens quick settings.",
        done = "All from the keys. In the panel, A opens the clipboard.",
        cue = GuideCue.Spot(listOf(Layout.SYM, Layout.ALT)),
        usesPanel = true,
        setup = Panel.Closed,
        demo = GuideDemo.Insert(listOf(Layout.SYM, Layout.SYM, "W"), "😀"),
        check = { event ->
            if (event is ModifierState && (event.panel == Panel.Emoji || event.panel == Panel.Settings)) GuideVerdict.Done else GuideVerdict.Wait
        },
    ),
    GuideStep(
        id = "end", chapter = GuideChapter.End,
        title = "TitanKeys",
        body = "The keys your Titan deserves.",
        done = "The keys your Titan deserves.",
        check = { GuideVerdict.Wait },
    ),
)

/**
 * Something that is easy to do by chance and stops a step from working. The guide says what happened and how to go back,
 * and the step goes on as before. A step's own check comes first, so a step that wants one of these things gets it.
 */
internal fun commonMistake(step: GuideStep, event: KeyboardEvent): String? = when {
    event is ModifierState && event.panel != Panel.Closed && !step.usesPanel ->
        "That opened the Sym panel. Push Sym until it closes, then try again."
    event is ModifierState && event.alt != Modifiers.Level.Off && !step.usesPanel ->
        "Alt is on, so the touchpad does not type. Push Alt to turn it off."
    event is CursorStarted && step.cue !is GuideCue.Taps ->
        "That moves the cursor. Quick taps just above the middle of Space start it. Lift your finger to stop."
    event is HeldSymbol && step.id != "symbols" ->
        "A held key types its symbol. Push the keys quickly for letters."
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

    /** How far through the steps the guide is, for the bar at the top. The ending is not a step to do. */
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
