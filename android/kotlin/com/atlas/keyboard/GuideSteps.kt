package com.atlas.keyboard

import com.atlas.keyboard.KeyboardEvent.CursorStarted
import com.atlas.keyboard.KeyboardEvent.Chosen
import com.atlas.keyboard.KeyboardEvent.DeletedWord
import com.atlas.keyboard.KeyboardEvent.FieldChanged
import com.atlas.keyboard.KeyboardEvent.GroupPicked
import com.atlas.keyboard.KeyboardEvent.HeldSymbol
import com.atlas.keyboard.KeyboardEvent.ModifierState
import com.atlas.keyboard.KeyboardEvent.Moved
import com.atlas.keyboard.KeyboardEvent.PanelSwiped
import com.atlas.keyboard.KeyboardEvent.Pushed
import com.atlas.keyboard.KeyboardEvent.QuickSettingUsed
import com.atlas.keyboard.KeyboardEvent.Recognised
import com.atlas.keyboard.KeyboardEvent.Resting
import com.atlas.keyboard.KeyboardEvent.Tapped
import com.atlas.keyboard.Modifiers.Panel
import kotlin.math.abs

/** The background of the guide: ink while you try a step, and orange once you have done it. */
internal enum class GuideTone { Orange, Ink }

/** One part of the story, with the [label] that shows above its steps. The bar at the top shows how far through the chapters you are. */
internal enum class GuideChapter(val label: String) {
    Hello("Real keys"),
    Swipe("Swipe typing"),
    Flick("Deleting"),
    Symbols("Symbols and emoji"),
    QuickSettings("Quick settings"),
    Hold("Holding keys"),
    Cursor("Cursor and selection"),
    End(""),
}

/** What the drawing of the keys shows as a ghost finger, so that the screen says what to do without words. */
internal sealed interface GuideCue {
    /** No ghost. The keys only light up under your own finger. */
    data object Free : GuideCue
    /** A finger goes through the letters of a word. */
    data class Word(val word: String) : GuideCue
    /** A finger flicks left from Backspace, and stays there if [hold] is true. */
    data class Flick(val hold: Boolean) : GuideCue
    /** These keys glow in turn. */
    data class Spot(val keys: List<String>) : GuideCue
    /** A finger swipes up the middle of the keys. */
    data object SwipeUp : GuideCue
    /** A finger goes along the bottom row. */
    data object SlideRow : GuideCue
    /** A finger taps the bottom row, then if [drag] is true stays down, swipes up and leans to one side. */
    data class Taps(val count: Int, val drag: Boolean) : GuideCue
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
    /** Shows in the field while it is empty. */
    val placeholder: String? = null,
    /** The text in the field when the step starts. Null keeps the text there. An empty string clears the field. */
    val seed: String? = null,
    val cue: GuideCue = GuideCue.Free,
    /** The setting that this step needs. */
    val needs: GuideNeed? = null,
    /** The step does not show when this is true. */
    val skipWhen: (GuideSettings) -> Boolean = { false },
    /** Says at once what to do if the step needs the panel and the panel is closed when the step starts. */
    val panelHint: String? = null,
    val check: GuideMemory.(KeyboardEvent) -> GuideVerdict,
)

private const val PANEL_LOST = "Push Sym to open the panel."

/** The guide's one list. Each step's words and check are together. To change a gesture, change it here. */
internal val GUIDE_STEPS = listOf(
    GuideStep(
        id = "touch", chapter = GuideChapter.Hello,
        title = "Touch the keys.",
        body = "Do not push them. Slide one finger across the bright area.",
        done = "Every key is a touchpad.",
        placeholder = "Your words go here.",
        cue = GuideCue.Word("thumb"),
        check = { event ->
            when {
                event is Moved && event.keys >= TOUCH_TRAVEL_KEYS -> GuideVerdict.Done
                event is Pushed && isLetter(event.code) -> GuideVerdict.Hint(PUSHED)
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "type", chapter = GuideChapter.Hello,
        title = "Type with two thumbs.",
        body = "Push the keys as usual. Type “teh” and a space. TitanKeys fixes it.",
        done = "Typos fixed as you type.",
        seed = "Real keys, smart fixes: ",
        check = { event ->
            when {
                event is FieldChanged && FIXED.containsMatchIn(event.text) -> GuideVerdict.Done
                event is FieldChanged && UNFIXED.containsMatchIn(event.text) ->
                    GuideVerdict.Hint("Fix typos as you type is off. Turn it on in Settings or in the quick settings.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "swipe", chapter = GuideChapter.Swipe,
        title = "faster",
        body = "Swipe this word. Go from letter to letter, then lift.",
        done = "One swipe. One word.",
        cue = GuideCue.Word("faster"),
        needs = GuideNeed.SwipeTyping,
        check = { event ->
            when (event) {
                is Recognised -> if (event.words.any { it.equals("faster", ignoreCase = true) }) GuideVerdict.Done
                    else GuideVerdict.Hint(if (event.words.isEmpty()) "Move across more letters." else "That was “${event.words.first()}”. Try again.")
                is Tapped -> GuideVerdict.Hint("Do not lift between the letters.")
                is Pushed -> if (isLetter(event.code)) GuideVerdict.Hint(PUSHED) else GuideVerdict.Wait
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "strip", chapter = GuideChapter.Swipe,
        title = "Touch another word.",
        body = "The strip above the keys shows other words.",
        done = "The right word, one touch away.",
        check = { event -> if (event is Chosen) GuideVerdict.Done else GuideVerdict.Wait },
    ),
    GuideStep(
        id = "flick", chapter = GuideChapter.Flick,
        title = "Flick left from Backspace.",
        body = "One flick deletes one word.",
        done = "Gone.",
        seed = "Glass keyboards make typos typos",
        cue = GuideCue.Flick(hold = false),
        needs = GuideNeed.SwipeTyping,
        check = { event ->
            when (event) {
                is DeletedWord -> GuideVerdict.Done
                is Recognised -> GuideVerdict.Hint("That typed a word. Start on Backspace and move left quickly.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "flick-hold", chapter = GuideChapter.Flick,
        title = "Flick. Do not lift.",
        body = "Keep your finger on the keys. A longer flick deletes faster.",
        done = "All gone.",
        seed = "Every word that you do not want goes away in one smooth hold",
        cue = GuideCue.Flick(hold = true),
        needs = GuideNeed.SwipeTyping,
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
        id = "sym-open", chapter = GuideChapter.Symbols,
        title = "Push Sym.",
        body = "The keys show symbols now.",
        done = "Every symbol. No menus.",
        help = "Nothing? Open the phone Settings, then Shortcut keys. Set Sym key to Programmable key.",
        cue = GuideCue.Spot(listOf(Layout.SYM)),
        check = { event -> if (event is ModifierState && event.panel == Panel.Symbols) GuideVerdict.Done else GuideVerdict.Wait },
    ),
    GuideStep(
        id = "sym-rest", chapter = GuideChapter.Symbols,
        title = "Rest a finger on a key.",
        body = "The panel marks the key under your finger.",
        done = "Look down less.",
        panelHint = PANEL_LOST,
        check = { event ->
            when {
                event is Resting && event.key.isNotEmpty() -> GuideVerdict.Done
                event is ModifierState && event.panel == Panel.Closed -> GuideVerdict.Hint(PANEL_LOST)
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "sym-page", chapter = GuideChapter.Symbols,
        title = "Swipe up.",
        body = "Swipe up or down for more. Backspace and Enter turn pages too.",
        done = "Pages at a swipe.",
        cue = GuideCue.SwipeUp,
        panelHint = PANEL_LOST,
        check = { event ->
            when {
                // Any swipe up or down counts, also when the group has only one page to show.
                event is PanelSwiped && abs(event.down) > abs(event.across) -> GuideVerdict.Done
                event is ModifierState && event.panel == Panel.Closed -> GuideVerdict.Hint(PANEL_LOST)
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "emoji-open", chapter = GuideChapter.Symbols,
        title = "Push Sym again.",
        body = "Now the keys show emoji.",
        done = "Emoji, right on the keys.",
        cue = GuideCue.Spot(listOf(Layout.SYM)),
        panelHint = PANEL_LOST,
        check = { event ->
            when {
                event is ModifierState && event.panel == Panel.Emoji -> GuideVerdict.Done
                event is ModifierState && event.panel == Panel.Closed -> GuideVerdict.Hint("The panel closed. Push Sym two times for the emoji.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "emoji-slide", chapter = GuideChapter.Symbols,
        title = "Use the bottom row.",
        body = "Rest or move a finger on the bottom row. It changes the group.",
        done = "You did not touch the screen.",
        cue = GuideCue.SlideRow,
        panelHint = "Push Sym two times for the emoji.",
        check = { event ->
            when {
                event is GroupPicked -> GuideVerdict.Done
                event is PanelSwiped -> GuideVerdict.Hint("That was a swipe. Put your finger on the bottom row of keys.")
                event is ModifierState && event.panel == Panel.Closed -> GuideVerdict.Hint("The panel closed. Push Sym two times for the emoji.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "quick-open", chapter = GuideChapter.QuickSettings,
        title = "Push Alt.",
        body = "The panel shows quick settings.",
        done = "Settings at your fingertips.",
        help = "Rest a finger on a key to read its setting.",
        cue = GuideCue.Spot(listOf(Layout.ALT)),
        panelHint = "Push Sym, then push Alt.",
        check = { event ->
            when {
                event is ModifierState && event.panel == Panel.Settings -> GuideVerdict.Done
                event is ModifierState && event.panel == Panel.Closed -> GuideVerdict.Hint("The panel closed. Push Sym, then push Alt.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "quick-hold", chapter = GuideChapter.QuickSettings,
        title = "Push H.",
        body = "This turns on Hold a letter for its symbol.",
        done = "On, with one key.",
        cue = GuideCue.Spot(listOf("H")),
        skipWhen = { it.holdSymbols },
        panelHint = "Push Sym, then push Alt.",
        check = { event ->
            when {
                event is QuickSettingUsed && event.item == QuickSetting.HoldSymbols && event.on -> GuideVerdict.Done
                event is QuickSettingUsed -> GuideVerdict.Hint("That changed ${event.item.title}. Push it again to change it back, then push H.")
                event is ModifierState && event.panel == Panel.Closed -> GuideVerdict.Hint("The panel closed. Push Sym, then push Alt.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "quick-close", chapter = GuideChapter.QuickSettings,
        title = "Push Sym.",
        body = "Sym closes the panel.",
        done = "Changed with one key.",
        cue = GuideCue.Spot(listOf(Layout.SYM)),
        // If the panel is already closed, there is nothing to close.
        skipWhen = { !it.panelOpen },
        check = { event -> if (event is ModifierState && event.panel == Panel.Closed) GuideVerdict.Done else GuideVerdict.Wait },
    ),
    GuideStep(
        id = "hold-letter", chapter = GuideChapter.Hold,
        title = "Hold a letter.",
        body = "Keep it down. The letter becomes its symbol.",
        done = "One key. Two jobs.",
        seed = "",
        cue = GuideCue.Spot(listOf("J", "K", "L")),
        needs = GuideNeed.HoldSymbols,
        check = { event ->
            when {
                event is HeldSymbol -> GuideVerdict.Done
                event is FieldChanged && event.text.isNotEmpty() -> GuideVerdict.Hint("That typed the letter. Keep the key down longer.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "hold-ctrl", chapter = GuideChapter.Hold,
        title = "Hold Ctrl.",
        body = "The bar shows your shortcuts.",
        done = "All your shortcuts.",
        help = "No Ctrl key? Turn on Use fn as Ctrl, or push Next.",
        cue = GuideCue.Spot(listOf(Layout.FN)),
        needs = GuideNeed.FnAsCtrl,
        check = { event -> if (event is ModifierState && event.ctrlOverlay) GuideVerdict.Done else GuideVerdict.Wait },
    ),
    GuideStep(
        id = "cursor-word", chapter = GuideChapter.Cursor,
        title = "Touch Space two times.",
        body = "Quickly, and lift after each touch. It selects the word at the cursor.",
        done = "No screen needed.",
        help = "Touch lightly. Do not push the key.",
        seed = "Select a word without the screen",
        cue = GuideCue.Taps(2, drag = false),
        needs = GuideNeed.CursorControl,
        check = { event ->
            if (event is FieldChanged && event.end - event.start > 0) GuideVerdict.Done else GuideVerdict.Wait
        },
    ),
    GuideStep(
        id = "cursor-move", chapter = GuideChapter.Cursor,
        title = "Two touches. Then swipe up.",
        body = "Keep it down after the second. Lean left or right. Further is faster.",
        done = "Your finger is the cursor.",
        help = "Lift after the first touch. Do not lift after the second touch.",
        seed = "Your finger is the cursor now, and it never covers the text",
        cue = GuideCue.Taps(2, drag = true),
        needs = GuideNeed.CursorControl,
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
        id = "cursor-words", chapter = GuideChapter.Cursor,
        title = "Three touches. Then swipe up.",
        body = "Lean to select word by word. The middle goes back to the first word.",
        done = "Word by word.",
        help = "Lift after the first two touches. Do not lift after the third touch.",
        seed = "Select whole phrases in one smooth move",
        cue = GuideCue.Taps(3, drag = true),
        needs = GuideNeed.CursorControl,
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
        placeholder = "Type anything.",
        check = { GuideVerdict.Wait },
    ),
)

/** A swipe or touch step heard a key go down, which is a push and not a touch. */
private const val PUSHED = "That pushed a key. Touch lightly and slide across the letters."

/** From android.view.KeyEvent, so that the steps can be tested without Android. */
private fun isLetter(code: Int) = code in 29..54

/** The typed word that the typing step asks for, after autocorrect has fixed it, and before. */
private val FIXED = Regex("\\bthe\\s", RegexOption.IGNORE_CASE)
private val UNFIXED = Regex("\\bteh\\s", RegexOption.IGNORE_CASE)

/**
 * Something that is easy to do by chance and stops a step from working. The guide says what happened and how to go back,
 * and the step goes on as before. A step's own check comes first, so a step that wants one of these things gets it.
 */
internal fun commonMistake(step: GuideStep, event: KeyboardEvent): String? {
    val panelStep = step.chapter == GuideChapter.Symbols || step.chapter == GuideChapter.QuickSettings
    return when {
        event is ModifierState && event.panel != Panel.Closed && !panelStep ->
            "That opened the Sym panel. Push Sym until it closes, then try again."
        event is ModifierState && event.alt != Modifiers.Level.Off && !panelStep ->
            "Alt is on, so the touchpad does not type. Push Alt to turn it off."
        event is CursorStarted && step.chapter != GuideChapter.Cursor ->
            "That moves the cursor. Quick touches on Space start it. Lift your finger to stop."
        event is HeldSymbol && step.chapter != GuideChapter.Hold ->
            "A held key types its symbol. Push the keys quickly for letters."
        else -> null
    }
}

/** The finger must travel this far, in key widths, for the keys to have felt it. */
private const val TOUCH_TRAVEL_KEYS = 3f
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

    /** Which of the steps this is, as a share of the chapters done. */
    val chapterIndex get() = GuideChapter.entries.indexOf(step.chapter)
    val chapterCount get() = GuideChapter.entries.size

    /** Where to come back to: the start of this chapter, because a panel that a step needs is closed after the guide is left. */
    val resumeId get() = steps.first { it.chapter == step.chapter }.id

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
