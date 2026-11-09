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
import com.atlas.keyboard.KeyboardEvent.QuickSettingUsed
import com.atlas.keyboard.KeyboardEvent.Recognised
import com.atlas.keyboard.KeyboardEvent.Resting
import com.atlas.keyboard.KeyboardEvent.Tapped
import com.atlas.keyboard.Modifiers.Panel
import kotlin.math.abs

/** The background of a part of the guide: orange with ink words, or ink with orange and cream words. */
internal enum class GuideTone { Orange, Ink }

/** One part of the story. A new chapter sweeps in with its own background. */
internal enum class GuideChapter(val tone: GuideTone) {
    Hello(GuideTone.Orange),
    Swipe(GuideTone.Ink),
    Flick(GuideTone.Orange),
    Symbols(GuideTone.Ink),
    QuickSettings(GuideTone.Orange),
    Hold(GuideTone.Ink),
    Cursor(GuideTone.Orange),
    End(GuideTone.Ink),
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
    /** A finger taps the bottom row, then drags past Space if [drag] is true. */
    data class Taps(val count: Int, val drag: Boolean) : GuideCue
}

/** A setting that a step needs. If it is off, the step offers to turn it on. */
internal enum class GuideNeed(val offer: String, val optional: Boolean = false) {
    HoldSymbols("Turn on Hold for symbols"),
    CursorControl("Turn on cursor control"),
    SwipeTyping("Turn on Swipe typing"),
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
    /** A short line that lands when the step is done. Steps with none move straight on. */
    val done: String? = null,
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
        body = "Do not push them. Move one finger across.",
        done = "Every key is a touchpad.",
        placeholder = "Your words go here.",
        cue = GuideCue.Word("thumb"),
        check = { event -> if (event is Moved && event.keys >= TOUCH_TRAVEL_KEYS) GuideVerdict.Done else GuideVerdict.Wait },
    ),
    GuideStep(
        id = "swipe", chapter = GuideChapter.Swipe,
        title = "hello",
        body = "Swipe this word. Go from letter to letter, then lift.",
        done = "One swipe. One word.",
        cue = GuideCue.Word("hello"),
        needs = GuideNeed.SwipeTyping,
        check = { event ->
            when (event) {
                is Recognised -> if (event.words.any { it.equals("hello", ignoreCase = true) }) GuideVerdict.Done
                    else GuideVerdict.Hint(if (event.words.isEmpty()) "Move across more letters." else "That was “${event.words.first()}”. Try again.")
                is Tapped -> GuideVerdict.Hint("Do not lift between the letters.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "strip", chapter = GuideChapter.Swipe,
        title = "Touch another word.",
        body = "The strip above the keys shows other words.",
        check = { event -> if (event is Chosen) GuideVerdict.Done else GuideVerdict.Wait },
    ),
    GuideStep(
        id = "flick", chapter = GuideChapter.Flick,
        title = "Flick left from Backspace.",
        body = "One flick deletes one word.",
        done = "Gone.",
        seed = "One word too many many",
        cue = GuideCue.Flick(hold = false),
        needs = GuideNeed.SwipeTyping,
        check = { event ->
            when (event) {
                is DeletedWord -> GuideVerdict.Done
                is Recognised -> GuideVerdict.Hint("Start on Backspace. Move to the left quickly.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "flick-hold", chapter = GuideChapter.Flick,
        title = "Flick. Do not lift.",
        body = "Keep your finger on the keys. The words go faster and faster.",
        done = "All gone.",
        seed = "Flick and hold to make all of these words go away",
        cue = GuideCue.Flick(hold = true),
        needs = GuideNeed.SwipeTyping,
        check = { event ->
            when {
                event is DeletedWord && event.held -> if (++count >= HELD_DELETE_WORDS) GuideVerdict.Done else GuideVerdict.Wait
                // The first word of a held flick is like a plain flick, so one plain flick does not mean that the finger lifted.
                event is DeletedWord && ++mark >= 1 -> GuideVerdict.Hint("Keep your finger on the keys after the flick.")
                event is Recognised -> GuideVerdict.Hint("Start on Backspace. Move to the left quickly.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "sym-open", chapter = GuideChapter.Symbols,
        title = "Push Sym.",
        body = "The keys show symbols now.",
        help = "Nothing? Open the phone Settings, then Shortcut keys. Set Sym key to Programmable key.",
        cue = GuideCue.Spot(listOf(Layout.SYM)),
        check = { event -> if (event is ModifierState && event.panel == Panel.Symbols) GuideVerdict.Done else GuideVerdict.Wait },
    ),
    GuideStep(
        id = "sym-rest", chapter = GuideChapter.Symbols,
        title = "Rest a finger on a key.",
        body = "The panel marks the key under your finger.",
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
        body = "Swipe up or down to see more symbols.",
        cue = GuideCue.SwipeUp,
        panelHint = PANEL_LOST,
        check = { event ->
            when {
                // Any swipe up or down counts, also when the symbols have only one page to show.
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
        cue = GuideCue.Spot(listOf(Layout.SYM)),
        panelHint = PANEL_LOST,
        check = { event ->
            when {
                event is ModifierState && event.panel == Panel.Emoji -> GuideVerdict.Done
                event is ModifierState && event.panel == Panel.Closed -> GuideVerdict.Hint(PANEL_LOST)
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "emoji-slide", chapter = GuideChapter.Symbols,
        title = "Move along the bottom row.",
        body = "Keep your finger on the bottom row. It changes the group.",
        done = "You did not touch the screen.",
        cue = GuideCue.SlideRow,
        panelHint = "Push Sym two times for the emoji.",
        check = { event ->
            when {
                event is GroupPicked -> GuideVerdict.Done
                event is PanelSwiped -> GuideVerdict.Hint("Put your finger on the bottom row of keys.")
                event is ModifierState && event.panel == Panel.Closed -> GuideVerdict.Hint("Push Sym two times for the emoji.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "quick-open", chapter = GuideChapter.QuickSettings,
        title = "Push Alt.",
        body = "The panel shows quick settings.",
        help = "Rest a finger on a key to read its setting.",
        cue = GuideCue.Spot(listOf(Layout.ALT)),
        panelHint = "Push Sym, then push Alt.",
        check = { event ->
            when {
                event is ModifierState && event.panel == Panel.Settings -> GuideVerdict.Done
                event is ModifierState && event.panel == Panel.Closed -> GuideVerdict.Hint("Push Sym, then push Alt.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "quick-hold", chapter = GuideChapter.QuickSettings,
        title = "Push H.",
        body = "This turns on Hold for symbols.",
        cue = GuideCue.Spot(listOf("H")),
        skipWhen = { it.holdSymbols },
        panelHint = "Push Sym, then push Alt.",
        check = { event ->
            when {
                event is QuickSettingUsed && event.item == QuickSetting.HoldSymbols && event.on -> GuideVerdict.Done
                event is QuickSettingUsed -> GuideVerdict.Hint("That is a different setting. Push H.")
                event is ModifierState && event.panel == Panel.Closed -> GuideVerdict.Hint("Push Sym, then push Alt.")
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
                event is FieldChanged && event.text.isNotEmpty() -> GuideVerdict.Hint("Keep the key down longer.")
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "hold-ctrl", chapter = GuideChapter.Hold,
        title = "Hold Ctrl.",
        body = "The panel shows your shortcuts.",
        done = "All your shortcuts.",
        help = "No Ctrl key? Turn on Use fn as Ctrl, or skip this step.",
        cue = GuideCue.Spot(listOf(Layout.FN)),
        needs = GuideNeed.FnAsCtrl,
        check = { event -> if (event is ModifierState && event.ctrlOverlay) GuideVerdict.Done else GuideVerdict.Wait },
    ),
    GuideStep(
        id = "cursor-word", chapter = GuideChapter.Cursor,
        title = "Touch two times quickly.",
        body = "On the bottom row. This selects the word at the cursor.",
        help = "Lift your finger after each touch.",
        seed = "Select this word",
        cue = GuideCue.Taps(2, drag = false),
        needs = GuideNeed.CursorControl,
        check = { event ->
            if (event is FieldChanged && event.end - event.start > 0) GuideVerdict.Done else GuideVerdict.Wait
        },
    ),
    GuideStep(
        id = "cursor-move", chapter = GuideChapter.Cursor,
        title = "Two touches. Hold and move.",
        body = "Do not lift after the second touch. Go past Space, left or right.",
        done = "Your finger is the cursor.",
        help = "Lift after the first touch. Do not lift after the second touch.",
        seed = "Move the cursor along this line",
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
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "cursor-words", chapter = GuideChapter.Cursor,
        title = "Three touches. Hold and move.",
        body = "Do not lift after the third touch. Select word by word.",
        done = "Word by word.",
        help = "Move to the left or right after the third touch.",
        seed = "Select many words at one time",
        cue = GuideCue.Taps(3, drag = true),
        needs = GuideNeed.CursorControl,
        check = { event ->
            when {
                event is CursorStarted && event.words -> {
                    armed = true
                    GuideVerdict.Wait
                }
                event is FieldChanged && armed && event.end > event.start
                    && event.text.substring(event.start, event.end).trim().contains(' ') -> GuideVerdict.Done
                else -> GuideVerdict.Wait
            }
        },
    ),
    GuideStep(
        id = "end", chapter = GuideChapter.End,
        title = "This is TitanKeys.",
        body = "Made for these keys. Now they are yours.",
        placeholder = "Swipe anything.",
        check = { GuideVerdict.Wait },
    ),
)

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

    fun event(event: KeyboardEvent): GuideVerdict = if (finished) GuideVerdict.Wait else step.check(memory, event)

    /** Moves to the next step that shows. Returns the step that was left. */
    fun advance(): GuideStep {
        val left = step
        if (!finished) moveTo(firstFrom(position + 1))
        return left
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
