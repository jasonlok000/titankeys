package com.atlas.keyboard

import com.atlas.keyboard.KeyboardEvent.Chosen
import com.atlas.keyboard.KeyboardEvent.CursorStarted
import com.atlas.keyboard.KeyboardEvent.DeletedWord
import com.atlas.keyboard.KeyboardEvent.FieldChanged
import com.atlas.keyboard.KeyboardEvent.GroupPicked
import com.atlas.keyboard.KeyboardEvent.HeldSymbol
import com.atlas.keyboard.KeyboardEvent.ModifierState
import com.atlas.keyboard.KeyboardEvent.Moved
import com.atlas.keyboard.KeyboardEvent.Pushed
import com.atlas.keyboard.KeyboardEvent.Recognised
import com.atlas.keyboard.KeyboardEvent.Tapped
import com.atlas.keyboard.Modifiers.Panel
import kotlin.math.abs

/** The background of the guide: ink while you try a step, and orange once you have done it. */
internal enum class GuideTone { Orange, Ink }

/** One part of the story, with the [label] that shows above its steps: the basics first, then more exercises for who wants them. */
internal enum class GuideChapter(val label: String) {
    Basics("The basics"),
    Practice("More exercises"),
    End(""),
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
 * The text that the guide suggests you type, written out in the line above the field. It is one paragraph about TitanKeys, and the
 * whole guide is one text in one field: each word that you swipe or type moves the line on to the next word, whatever the word was,
 * so that nothing waits on one word. After the last word it starts again.
 */
internal object GuideScript {
    const val TEXT = "TitanKeys turns every key into a touchpad, so whole words flow from one smooth swipe while your eyes stay on " +
        "what you write. Type with two thumbs or swipe with one hand, and it keeps up either way. Typos are fixed as you go, " +
        "and the next word is ready before you need it. A flick deletes a word, a held letter gives its symbol, and your thumb " +
        "steers the cursor without touching the screen. Every symbol and emoji sits right on the keys. This is the ultimate " +
        "keyboard for your Titan."

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

    /** The word at [index], starting again after the end. */
    fun wordAt(index: Int) = words[index.mod(words.size)]

    /** The sentence that starts at or after word [index], to put in the field for a step that needs text to work on. */
    fun sentenceFrom(index: Int): String {
        val from = (index until index + words.size).first { it == 0 || wordAt(it - 1).endsWith('.') }
        val sentence = ArrayList<String>()
        var at = from
        do {
            sentence += wordAt(at)
        } while (!wordAt(at++).endsWith('.'))
        return sentence.joinToString(" ")
    }
}

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
 * One thing to do. The words and the check are in one place, so that a gesture that changes is easy to change here.
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
    /** Says at once what to do if the step needs the panel and the panel is closed when the step starts. */
    val panelHint: String? = null,
    /** The step works with the Sym panel open, so an open panel is no mistake here. */
    val usesPanel: Boolean = false,
    /** The step works on text, so it needs at least this many words in the field. */
    val needsWords: Int = 0,
    /** A stop between the parts of the guide: it counts as done at once, and offers to start typing. */
    val pause: Boolean = false,
    val check: GuideMemory.(KeyboardEvent) -> GuideVerdict,
)

private const val PANEL_LOST = "The panel closed. Push Sym to open it."
private const val EMOJI_LOST = "The panel closed. Push Sym two times for the emoji."
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

/** The guide's one list: the basics, a stop, then more exercises. Each step's words and check are together. */
internal val GUIDE_STEPS = listOf(
    GuideStep(
        id = "touch", chapter = GuideChapter.Basics,
        title = "Touch the keys.",
        body = "Do not push them. Slide one finger across the bright area.",
        done = "Every key is a touchpad.",
        cue = GuideCue.NextWord,
        check = { event ->
            when {
                event is Moved && event.keys >= TOUCH_TRAVEL_KEYS -> GuideVerdict.Done
                event is Pushed && isLetter(event.code) -> GuideVerdict.Hint(PUSHED)
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "swipe", chapter = GuideChapter.Basics,
        title = "Swipe the line above.",
        body = "Slide through each word, then lift. Do not stop at one word.",
        done = "One swipe. One word.",
        cue = GuideCue.NextWord,
        needs = GuideNeed.SwipeTyping,
        check = swipes(BASIC_SWIPES),
    ),
    GuideStep(
        id = "type", chapter = GuideChapter.Basics,
        title = "Type with two thumbs.",
        body = "Push the keys for the next word. Typos are fixed when you push Space.",
        done = "Two thumbs or one. It keeps up.",
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
        id = "strip", chapter = GuideChapter.Basics,
        title = "Touch another word.",
        body = "The strip above the keys shows other words. Touch one to use it.",
        done = "The right word, one touch away.",
        check = { event -> if (event is Chosen) GuideVerdict.Done else GuideVerdict.Wait },
    ),
    GuideStep(
        id = "flick", chapter = GuideChapter.Basics,
        title = "Flick left from Backspace.",
        body = "One flick deletes one word.",
        done = "Gone.",
        cue = GuideCue.Flick(hold = false),
        needs = GuideNeed.SwipeTyping,
        needsWords = 2,
        check = { event ->
            when (event) {
                is DeletedWord -> GuideVerdict.Done
                is Recognised -> GuideVerdict.Hint("That typed a word. Start on Backspace and move left quickly.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "cursor-move", chapter = GuideChapter.Basics,
        title = "Two touches on Space. Swipe up.",
        body = "Keep it down after the second. Lean left or right. Further is faster.",
        done = "Your finger is the cursor.",
        help = "Lift after the first touch. Do not lift after the second touch.",
        cue = GuideCue.Taps(2, drag = true),
        needs = GuideNeed.CursorControl,
        needsWords = 4,
        check = { event ->
            when {
                event is CursorStarted && !event.words -> {
                    armed = true
                    mark = -1
                    GuideVerdict.Wait
                }
                event is FieldChanged && armed -> {
                    if (mark < 0) mark = event.start
                    if (abs(event.start - mark) >= CURSOR_TRAVEL_CHARACTERS) GuideVerdict.Done else GuideVerdict.Wait
                }
                event is Recognised -> GuideVerdict.Hint("That typed a word. Start with two quick touches on Space.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "sym", chapter = GuideChapter.Basics,
        title = "Push Sym, then a key.",
        body = "The keys show symbols. Push one to type it, or push Sym to go on.",
        done = "Every symbol. No menus.",
        help = "Nothing? Open the phone Settings, then Shortcut keys. Set Sym key to Programmable key.",
        cue = GuideCue.Spot(listOf(Layout.SYM)),
        usesPanel = true,
        check = { event ->
            when {
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
        id = "basics", chapter = GuideChapter.Basics,
        title = "That is the basics.",
        body = "Keep typing, or push Next for more exercises.",
        done = "That is the basics.",
        pause = true,
        check = { GuideVerdict.Wait },
    ),
    GuideStep(
        id = "sentence", chapter = GuideChapter.Practice,
        title = "Swipe a whole sentence.",
        body = "Keep going through the line above. Lift after each word.",
        done = "Whole sentences. No screen.",
        cue = GuideCue.NextWord,
        needs = GuideNeed.SwipeTyping,
        check = swipes(SENTENCE_SWIPES),
    ),
    GuideStep(
        id = "one-hand", chapter = GuideChapter.Practice,
        title = "Now use one hand.",
        body = "Hold the phone in one hand and swipe with your thumb.",
        done = "One hand is enough.",
        cue = GuideCue.NextWord,
        needs = GuideNeed.SwipeTyping,
        check = swipes(BASIC_SWIPES),
    ),
    GuideStep(
        id = "flick-hold", chapter = GuideChapter.Practice,
        title = "Flick. Do not lift.",
        body = "Keep your finger on the keys. A longer flick deletes faster.",
        done = "All gone.",
        cue = GuideCue.Flick(hold = true),
        needs = GuideNeed.SwipeTyping,
        needsWords = 6,
        check = { event ->
            when {
                event is DeletedWord && event.held -> if (++count >= HELD_DELETE_WORDS) GuideVerdict.Done else GuideVerdict.Wait
                // The first word of a held flick is like a plain flick, so one plain flick does not mean that the finger lifted.
                event is DeletedWord && ++mark >= 1 -> GuideVerdict.Hint("Your finger lifted. Keep it on the keys after the flick.")
                event is Recognised -> GuideVerdict.Hint("That typed a word. Start on Backspace and move left quickly.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "hold-letter", chapter = GuideChapter.Practice,
        title = "Hold a letter.",
        body = "Keep it down. The letter becomes the symbol in its corner.",
        done = "One key. Two jobs.",
        cue = GuideCue.Spot(listOf("J", "K", "L")),
        needs = GuideNeed.HoldSymbols,
        check = { event ->
            when {
                event is HeldSymbol -> GuideVerdict.Done
                event is Pushed && isLetter(event.code) -> {
                    armed = true
                    GuideVerdict.Wait
                }
                event is FieldChanged && armed -> GuideVerdict.Hint("That typed the letter. Keep the key down longer.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "emoji-open", chapter = GuideChapter.Practice,
        title = "Push Sym two times.",
        body = "Now the keys show emoji.",
        done = "Emoji, right on the keys.",
        cue = GuideCue.Spot(listOf(Layout.SYM)),
        usesPanel = true,
        check = { event -> if (event is ModifierState && event.panel == Panel.Emoji) GuideVerdict.Done else GuideVerdict.Wait },
    ),
    GuideStep(
        id = "emoji-slide", chapter = GuideChapter.Practice,
        title = "Use the bottom row.",
        body = "Rest or move a finger on the bottom row. It changes the group.",
        done = "You did not touch the screen.",
        cue = GuideCue.SlideRow,
        panelHint = "Push Sym two times for the emoji.",
        usesPanel = true,
        check = { event ->
            when {
                event is GroupPicked -> GuideVerdict.Done
                event is ModifierState && event.panel == Panel.Closed -> GuideVerdict.Hint(EMOJI_LOST)
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "quick-open", chapter = GuideChapter.Practice,
        title = "Push Alt.",
        body = "With the panel open, Alt shows the quick settings.",
        done = "Settings at your fingertips.",
        help = "Rest a finger on a key to read its setting at the bottom.",
        cue = GuideCue.Spot(listOf(Layout.ALT)),
        panelHint = "Push Sym, then push Alt.",
        usesPanel = true,
        check = { event ->
            when {
                event is ModifierState && event.panel == Panel.Settings -> GuideVerdict.Done
                event is ModifierState && event.panel == Panel.Closed -> GuideVerdict.Hint(PANEL_LOST)
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "quick-close", chapter = GuideChapter.Practice,
        title = "Push Sym.",
        body = "Sym closes the panel.",
        done = "Changed with one key.",
        cue = GuideCue.Spot(listOf(Layout.SYM)),
        usesPanel = true,
        // If the panel is already closed, there is nothing to close.
        skipWhen = { !it.panelOpen },
        check = { event -> if (event is ModifierState && event.panel == Panel.Closed) GuideVerdict.Done else GuideVerdict.Wait },
    ),
    GuideStep(
        id = "hold-ctrl", chapter = GuideChapter.Practice,
        title = "Hold Ctrl.",
        body = "The bar shows your shortcuts.",
        done = "All your shortcuts.",
        help = "No Ctrl key? Turn on Use fn as Ctrl, or push Next.",
        cue = GuideCue.Spot(listOf(Layout.FN)),
        needs = GuideNeed.FnAsCtrl,
        check = { event -> if (event is ModifierState && event.ctrlOverlay) GuideVerdict.Done else GuideVerdict.Wait },
    ),
    GuideStep(
        id = "cursor-word", chapter = GuideChapter.Practice,
        title = "Touch Space two times.",
        body = "Quickly, and lift after each touch. It selects the word at the cursor.",
        done = "No screen needed.",
        help = "Touch lightly. Do not push the key.",
        cue = GuideCue.Taps(2, drag = false),
        needs = GuideNeed.CursorControl,
        needsWords = 2,
        check = { event ->
            if (event is FieldChanged && event.end - event.start > 0) GuideVerdict.Done else GuideVerdict.Wait
        },
    ),
    GuideStep(
        id = "cursor-words", chapter = GuideChapter.Practice,
        title = "Three touches. Then swipe up.",
        body = "Lean to select word by word. The middle goes back to the first word.",
        done = "Word by word.",
        help = "Lift after the first two touches. Do not lift after the third touch.",
        cue = GuideCue.Taps(3, drag = true),
        needs = GuideNeed.CursorControl,
        needsWords = 6,
        check = { event ->
            when {
                event is CursorStarted && event.words -> {
                    armed = true
                    GuideVerdict.Wait
                }
                event is CursorStarted -> GuideVerdict.Hint("That was two touches, so it moves the cursor. Lift, then touch three times.")
                event is FieldChanged && armed && event.end > event.start
                    && event.text.substring(event.start, event.end).trim().contains(' ') -> GuideVerdict.Done
                event is Recognised -> GuideVerdict.Hint("That typed a word. Start with three quick touches on Space.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "end", chapter = GuideChapter.End,
        title = "TitanKeys",
        body = "Built for Titan 2 Elite.",
        done = "Built for Titan 2 Elite.",
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
    event is CursorStarted && !step.id.startsWith("cursor") ->
        "That moves the cursor. Quick touches on Space start it. Lift your finger to stop."
    event is HeldSymbol && step.id != "hold-letter" ->
        "A held key types its symbol. Push the keys quickly for letters."
    else -> null
}

/** The finger must travel this far, in key widths, for the keys to have felt it. */
private const val TOUCH_TRAVEL_KEYS = 3f
/** Swipes that finish a swipe step, and the longer practice of a whole sentence. */
private const val BASIC_SWIPES = 3
private const val SENTENCE_SWIPES = 8
/** Words that a held flick must delete after the first, so that you feel it speed up. */
private const val HELD_DELETE_WORDS = 3
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
     * Where to come back to: this step, or for a step that needs the Sym panel, the step that opened it, because the panel is
     * closed after the guide is left.
     */
    val resumeId: String get() {
        var at = position
        while (at > 0 && steps[at].usesPanel && steps[at - 1].usesPanel) at--
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
