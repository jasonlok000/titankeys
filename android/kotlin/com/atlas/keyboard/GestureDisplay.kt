package com.atlas.keyboard

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * What the strip shows while a special gesture runs: the cursor drag, the word drag, a held delete flick, and a short flourish
 * for one deleted word or one selected word. A word swipe shows a mini path: the words dim and the path is drawn over them as it
 * goes, as thick as the touch is firm. A Space tap that counts gives a glow under the middle word that grows with each tap, and
 * carries on into the steering or selecting that a held touch after it starts.
 *
 * The gesture code gives this class the numbers it already has, and [GestureDrawing] reads the numbers that come out. They are
 * all plain values kept in this one object, so a frame makes no garbage, and a test can run it with no clock and no phone.
 * [changed] asks for a new frame.
 */
class GestureDisplay(private val changed: () -> Unit = {}) {
    enum class Scene { None, Cursor, WordDrag, DeleteHold, DeleteFlick, WordSelected, Swipe }

    /** The dead zone in the middle of Space, from 0 to 1 across the keyboard. */
    val spaceStart = CursorMode.DEAD_LEFT / COLUMNS
    val spaceEnd = CursorMode.DEAD_RIGHT / COLUMNS

    /** False where the strip does not show, such as a field with no suggestions. Then nothing shows here. */
    var allowed = true
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                clear()
                endGlow()
            }
        }

    /** What shows now. With [Scene.None], the strip shows its words or modifiers. */
    var scene = Scene.None
        private set
    /** How far this display has replaced the words, from 0 to 1. */
    var reveal = 0f
        private set

    /** Where the finger is, from 0 to 1 across the keyboard. */
    var marker = .5f
        private set
    /** Which way the finger leans: -1, 0 on Space, or 1. */
    var leaning = 0
        private set
    /** How fast the gesture goes, from 0 for the slowest to 1 for the fastest. */
    var intensity = 0f
        private set
    /**
     * Which way steering leans and how hard, eased so it never jumps: -1 fully left to 1 fully right, 0 on Space. The underglow
     * brightens on that side.
     */
    var steerLean = 0f
        private set
    /** How far the line of text has gone past the caret, from 0 to 1 of a letter. It wraps round into [letters]. */
    var flow = 0f
        private set
    /** Whole letters that the line of text has gone past the caret, wrapping round at [PATTERN_LETTERS]. */
    var letters = 0
        private set
    /** The stretch from the end of Space to the finger, from 0 to 1 across the keyboard. On Space it is only the finger. */
    var bandStart = .5f
        private set
    var bandEnd = .5f
        private set

    /** Words selected past the word that a word drag began with, on each side. Only one side is ever above 0. */
    var wordsLeft = 0
        private set
    var wordsRight = 0
        private set
    /** The selected span, in words from the first word of the drag. */
    var spanStart = -.5f
        private set
    var spanEnd = .5f
        private set
    /** Whether the right end of [spanStart] to [spanEnd] is the one that moves. */
    var leadingRight = true
        private set
    /** The word at the middle of the strip, in the same units as [spanStart]. */
    var camera = 0f
        private set

    /**
     * The real text around the caret while steering, read when the gesture starts: the caret sits before index [caretAt]. Null
     * where the field gives no text, and then a made-up line moves past the caret instead.
     */
    var line: CharSequence? = null
        private set
    var caretAt = 0
        private set
    /** Characters that the cursor has moved since [line] was read, and the same number as it shows, which follows it smoothly. */
    var stepped = 0
        private set
    var shownStep = 0f
        private set
    /**
     * Whether the caret shows now. Only a held delete's caret blinks, on and off for [BLINK_MILLIS] each, while the finger rests
     * in the pause zone. Steering's never blinks, even when it pauses (owner, 2026-11-12).
     */
    var caretShown = true
        private set
    /** Whether more text may lie past each end of [line], so that the cursor going there reads it again. */
    private var moreBefore = false
    private var moreAfter = false
    /** The real words of a word drag or a picked word, as letter counts: the first word, then nearest first on each side. Null where unknown. */
    var anchorLetters: Int? = null
        private set
    var leftWords = IntArray(0)
        private set
    var rightWords = IntArray(0)
        private set

    /** Words that the hold has deleted, and the same number as it shows, which follows it smoothly. */
    var deleted = 0
        private set
    var shownDeleted = 0f
        private set
    /** How far a flourish has gone, from 0 to 1. */
    var flourish = 0f
        private set

    /** True while the words are covered, so a touch on the strip must not choose one. */
    val showing get() = scene != Scene.None

    /** True while anything here needs drawing: a scene, or the Space glow, which leaves the words as they are. */
    val busy get() = scene != Scene.None || glowing

    /** True while the Space glow shows, from taps, as part of a steer, select or held delete, or as it fades after one. */
    val glowing get() = glowTaps > 0 || glowHeld || glowFading

    /**
     * A word swipe's path so far, in touchpad space: x from 0 to 1 across, y from 0 at the top row to [Layout]'s bottom row. Only the
     * first [pathSize] points count. The arrays are made once, so a frame makes no garbage.
     */
    val pathX = FloatArray(MAX_PATH)
    val pathY = FloatArray(MAX_PATH)
    /** How thick the path is at each point, 1 for a touch of the usual size: see [TrailThickness]. */
    val pathWidth = FloatArray(MAX_PATH)
    var pathSize = 0
        private set
    private val thickness = TrailThickness()
    /** When a lifted swipe's path starts to fade: it waits through the time a slip may still carry the swipe on. */
    private var swipeFadeAt = 0L
    /** True while the glow is part of a steer, select or held delete, which it grows into and goes with. */
    private var glowHeld = false
    /** True while a held delete's glow fades after its lift. */
    private var glowFading = false
    /** The width and strength a held glow grows to: the whole strip for a steer or select, the finger's own for a held delete. */
    private var heldWidth = GLOW_HELD_WIDTH
    private var heldAlpha = GLOW_HELD_ALPHA
    /**
     * Where the glow's middle is, from 0 to 1 across the strip: the middle for Space, under the finger for a held delete,
     * following it smoothly.
     */
    var glowCentre = .5f
        private set
    private var centreTarget = .5f
    private var glowFrame = 0L

    /** The Space glow under the middle word: its width, from 0 to 1 of the strip, and its strength, from 0 to 1. */
    var glowWidth = 0f
        private set
    val glowAlpha get() = if (glowHeld && leaving) glowBase * reveal else glowBase
    private var glowBase = 0f
    /** The quick line along the bottom on the third tap, from 1 down to 0. */
    var glowPulse = 0f
        private set
    /** The taps the glow shows, 1 to 3, or 0 for none. */
    var glowTaps = 0
        private set
    private var glowAt = 0L
    private var glowFromWidth = 0f
    private var glowFromAlpha = 0f

    private var leaving = false
    private var lastFrame = 0L
    private var speedTarget = 0f
    private var shownLeft = 0f
    private var shownRight = 0f
    private var deleteMillis = 200L
    private var flourishAt = 0L
    private var revealedAtStart = 0f
    /** True while the touch that picked a word is still down: see [wordSelected]. */
    private var pickHeld = false

    private val live get() = !leaving && (scene == Scene.Cursor || scene == Scene.WordDrag || scene == Scene.DeleteHold)
    private val flourishing get() = scene == Scene.DeleteFlick || scene == Scene.WordSelected

    /**
     * A single flick: only the orange part shows, a dot for each character that went and the caret, where a hold's words run
     * straight into the caret.
     */
    val softDelete get() = scene == Scene.DeleteFlick

    /** How many dots show: a dot for each character deleted, not counting spaces, with each word cut to fit: see [setGroups]. */
    var dots = 0
        private set
    /**
     * The dots by word, nearest the caret first: a hold's deleted words, or a flick's one word. A gap shows where each space was,
     * so the shape mirrors what went. They add up to [dots].
     */
    var groups = IntArray(0)
        private set
    /**
     * True when the dots come from a hold that lifted: then they do not pop in, the hold's lines shrink and turn into them in place,
     * over [dotsIn]. [morphFrom] and [morphFlow] are the hold's [shownDeleted] and [flow] at the lift, where its lines were.
     */
    var fromHold = false
        private set
    var morphFrom = 0f
        private set
    var morphFlow = 0f
        private set
    /** The letters of each word a hold deleted, in order. */
    private val held = ArrayList<Int>()
    /** How long the first dots take to come in: a flick's pop, or a lifted hold's morph. */
    private var dotsInMillis = FLICK_IN
    /** How far the first dots have come in, from 0 to 1, and how far the whole stream's way into the caret has gone. */
    var dotsIn = 0f
        private set
    var dotsOut = 0f
        private set

    /**
     * The dots are one stream: each delete that comes while they play adds its dots after the ones still to land, as a queue,
     * and nothing starts over. A batch is one delete's dots: its first dot, how many, and when it came, in real ms after the
     * stream started. Each dot's times are worked out for an animator scale ([planned]), in real ms after the stream started.
     * The arrays grow as deletes join, so however many words go, each one gets its dots; they never shrink, so a frame makes no
     * garbage.
     */
    private var batchFrom = IntArray(16)
    private var batchCount = IntArray(16)
    private var batchAt = FloatArray(16)
    private var batches = 0
    private var dotLand = FloatArray(64)
    private var dotPopAt = FloatArray(64)
    private var dotPlace = IntArray(64)
    /** How long after the dot before it each dot lands, as [plan] works it out, so a join can lay the queue again with the same steps. */
    private var dotStep = FloatArray(64)
    /** One step after the last dot lands: then the words come back. */
    private var doneAt = 0f
    private var planned = Float.NaN
    /** Real ms since the stream started, at the last frame, and whether that frame had motion. */
    private var streamAt = 0f
    private var still = false
    /** The place in the row that sits next to the caret, and how many places the row must fit, both following the queue smoothly. */
    var shownBase = 0f
        private set
    var shownUnits = 1f
        private set
    private var baseTarget = 0f
    private var unitsTarget = 1f
    /**
     * 1 while a lifted hold's dots play: they stay on the hold's full cover until they have all gone in, so they never lie over
     * the suggestions. 0 for a flick's, whose words show faded behind them.
     */
    var solid = 0f
        private set
    /** True when the last delete added its dots to a stream that was still playing. */
    var chained = false
        private set

    /** The experiment "Dots speed up when long", from the Haptics lab, on by default: after the first few, each dot lands a little sooner. */
    var speedUp = true
        set(value) {
            if (field != value) planned = Float.NaN
            field = value
        }

    /** The cursor or word drag at this frame. [column] is in key widths, as [CursorMode] gives it. [speed] is 0 on Space. */
    fun cursorFrame(column: Float, direction: Int, speed: Float, words: Boolean, now: Long) {
        if (!begin(if (words) Scene.WordDrag else Scene.Cursor, now)) return
        marker = (column / COLUMNS).coerceIn(0f, 1f)
        leaning = direction
        speedTarget = speedLevel(speed, words)
        when {
            direction < 0 -> { bandStart = marker; bandEnd = spaceStart }
            direction > 0 -> { bandStart = spaceEnd; bandEnd = marker }
            else -> { bandStart = marker; bandEnd = marker }
        }
        changed()
    }

    /** The finger slipped off and may come back. The drag stays on the strip, but nothing moves. */
    fun cursorPaused() {
        if (!live || scene == Scene.DeleteHold) return
        leaning = 0
        speedTarget = 0f
        changed()
    }

    fun cursorEnded() {
        if (scene == Scene.Cursor || scene == Scene.WordDrag) leave()
    }

    /** The text around the caret, [before] and [after] it, as the cursor drag starts or after it has gone far. */
    fun cursorText(before: CharSequence, after: CharSequence) {
        val left = before.takeLast(LINE_REACH)
        line = left.toString() + after.take(LINE_REACH)
        caretAt = left.length
        moreBefore = before.length >= LINE_REACH
        moreAfter = after.length >= LINE_REACH
        // The picture keeps where it was: the new text is read where the cursor is now.
        shownStep -= stepped
        stepped = 0
        changed()
    }

    /** The cursor moved one character. True when it is far enough from where the text was read to read it again. */
    fun cursorStepped(direction: Int): Boolean {
        stepped += direction
        changed()
        val text = line ?: return false
        return moreBefore && caretAt + stepped < REREAD_MARGIN || moreAfter && text.length - caretAt - stepped < REREAD_MARGIN
    }

    /**
     * The words around a word drag or a picked word: [before] it, the word itself of [letters], and [after] it, as the selection
     * counts words ([WordSelection.lengths]), so each step takes one pill. [moreBefore] and [moreAfter] say the text was cut off
     * there: the cut word is left out, and past the known words the bar draws made-up ones instead of running out. A word drag
     * reads them again at each step, so on long text its pills go on as far as the selection does.
     */
    fun wordText(before: CharSequence, letters: Int, after: CharSequence, moreBefore: Boolean = false, moreAfter: Boolean = false) {
        anchorLetters = letters
        leftWords = WordSelection.lengths(before, cutStart = moreBefore).reversedArray()
        rightWords = WordSelection.lengths(after, cutEnd = moreAfter)
        wordsMoreBefore = moreBefore
        wordsMoreAfter = moreAfter
        changed()
    }

    /** Whether the text read for a word drag was cut off before or after it, so more words lie past the known ones. */
    private var wordsMoreBefore = false
    private var wordsMoreAfter = false

    /**
     * How much of a word's place the word at [index] fills, in a word drag: 0 is the first word, below 0 the words before it.
     * Real words get a width from their length. Past the end of the text there is no word, which is 0. With no text known, a
     * made-up row reads as a line of text.
     */
    fun wordWidth(index: Int): Float {
        val first = anchorLetters ?: return MADE_UP_WIDTHS[index.mod(MADE_UP_WIDTHS.size)]
        val madeUp = MADE_UP_WIDTHS[index.mod(MADE_UP_WIDTHS.size)]
        val letters = when {
            index == 0 -> first
            index < 0 -> leftWords.getOrNull(-index - 1) ?: return if (wordsMoreBefore) madeUp else 0f
            else -> rightWords.getOrNull(index - 1) ?: return if (wordsMoreAfter) madeUp else 0f
        }
        return widthFor(letters)
    }

    /** One step of a word drag changed the selection. It gives back a word from the far side first, as [WordSelection.extend] does. */
    fun wordStep(direction: Int) {
        if (scene != Scene.WordDrag || leaving) return
        if (direction < 0) { if (wordsRight > 0) wordsRight-- else wordsLeft++ }
        else { if (wordsLeft > 0) wordsLeft-- else wordsRight++ }
        changed()
    }

    /**
     * The finger stayed on a delete flick, and more words will go, one each [repeatMillis]. [pace] is how fast the hold goes, from
     * 0 at the slowest to 1 at the top-speed line: the glow gets stronger with it, and stops changing at the line. [before] is
     * the real text before the cursor, for the words on the bar: see [deleteText].
     */
    fun deleteHeld(repeatMillis: Long, now: Long, pace: Float = 1f, before: CharSequence? = null) {
        if (!begin(Scene.DeleteHold, now)) return
        deleteMillis = repeatMillis
        deletePace = pace.coerceIn(0f, 1f)
        topPace = max(topPace, deletePace)
        // A flick's glow carries on as the hold's, from where it reached: from there it follows the pace.
        if (!glowHeld || !glowDelete) {
            holdGlow(now)
            glowDelete = true
            // A hold with no flick glow before it starts at its shortest reach, and grows in from nothing.
            glowWidth = DELETE_REACH_LEAST
        }
        // From a flick's reach, the glow eases to the pace's; after that it follows the pace, which already moves smoothly.
        if (!flickReach.isNaN()) reachEasing = true
        flickReach = Float.NaN
        if (before != null) deleteText(before)
        changed()
    }

    /**
     * A stroke that is clearly a delete flick has its finger at [x], from 0 to 1 across the touchpad: the delete glow reaches
     * along the bottom from the right corner to the finger, and follows it while the stroke goes on (owner, 2026-11-12). It is
     * the held delete's glow, in the same style. It fades at the lift, see [flickGlowEnded], and a hold takes it on.
     */
    fun flickGlow(x: Float, now: Long) {
        if (!allowed || scene == Scene.DeleteHold) return
        val reach = (1f - x).coerceIn(0f, 1f)
        if (!glowHeld || !glowDelete) {
            holdGlow(now)
            glowDelete = true
            // A flick is fast, so its glow is as strong as a hold's at a good pace, and stays so while the stroke goes on.
            deletePace = FLICK_GLOW_PACE
            shownPace = FLICK_GLOW_PACE
            // It starts at the finger, so it never sweeps across the bar, and at full strength: a flick is over in about 100 ms,
            // too soon for it to grow in.
            glowWidth = reach
            glowBase = heldAlpha
            flickGlowAt = now
            flickFramed = false
        }
        flickReach = reach
        deletePace = FLICK_GLOW_PACE
        changed()
    }

    /** The stroke stopped being a flick, or a hold lifted: the glow stays where it reached and fades. */
    fun flickGlowEnded(now: Long) {
        if (!glowHeld || !glowDelete) return
        fadeGlow(now)
        changed()
    }

    /**
     * A delete flick lifted with its finger at [x]. Every flick shows its glow (owner, 2026-11-12): a very fast one can be over
     * before its glow had a frame, or before it was clearly a flick at all, so its glow then plays after the lift, from the
     * corner to where the finger lifted. It stays at full strength until it has shown for [FLICK_GLOW_SEEN] in all, then fades
     * as any flick's does. A hold's glow just fades, as it always did.
     */
    fun flickLifted(x: Float, now: Long) {
        if (!allowed) return
        val reach = (1f - x).coerceIn(0f, 1f)
        val flickShowing = glowHeld && glowDelete && !flickReach.isNaN()
        if (glowHeld && glowDelete && !flickShowing) return flickGlowEnded(now)
        if (!flickShowing) {
            // Never clearly a flick while it went: the glow starts now, as a flick's would have.
            holdGlow(now)
            glowDelete = true
            deletePace = FLICK_GLOW_PACE
            shownPace = FLICK_GLOW_PACE
            glowBase = heldAlpha
            glowWidth = reach
            flickGlowAt = now
        } else if (!flickFramed) {
            // Its glow never drew: it shows where the finger lifted, not where the stroke was first seen.
            shownPace = FLICK_GLOW_PACE
            glowBase = heldAlpha
            glowWidth = reach
        }
        fadeGlow(now)
        // A fading glow counts its fade from [glowAt], so one set later holds it at full until then.
        glowAt = max(now, flickGlowAt + FLICK_GLOW_SEEN)
        changed()
    }

    /** How far a flick's glow reaches from the right, as a share of the bar, or NaN once a hold follows the pace instead. */
    private var flickReach = Float.NaN
    /** When the flick's glow came on, and whether it has had a frame since. */
    private var flickGlowAt = 0L
    private var flickFramed = false
    /** True while a hold's glow eases from where its flick's reached to where its pace has it. */
    private var reachEasing = false

    /**
     * The held delete's finger went into the pause zone, or out of it. While it is in, no words go: the hold's words stand still,
     * its glow fades out and its caret blinks, as steering's does when it pauses.
     */
    fun deletePaused(paused: Boolean) {
        if (scene != Scene.DeleteHold || deletePaused == paused) return
        deletePaused = paused
        pausedAt = -1L
        changed()
    }

    /**
     * The real text before the cursor as the hold's next word is about to go: the bar draws a pill for each of its words, as long
     * as the word, nearest the caret first, and the one about to go a little stronger. With no text left it draws none, only the
     * caret. Text the field doesn't give keeps the words already known, and past the known ones the bar shows made-up words only
     * where more text lies beyond what was read.
     */
    fun deleteText(before: CharSequence) {
        if (scene != Scene.DeleteHold || leaving) return
        // A text cut off at the reach starts in the middle of a word, which is not drawn: more lies beyond it.
        val more = before.length >= LINE_REACH
        val whole = if (more) before.subSequence(before.indexOfFirst { it.isWhitespace() }.let { if (it < 0) before.length else it }, before.length) else before
        val words = wordLengths(whole)
        val needed = deleted + words.size
        if (needed > holdLetters.size) holdLetters = holdLetters.copyOf(max(needed, holdLetters.size * 2))
        for (j in words.indices) holdLetters[deleted + j] = words[words.size - 1 - j]
        holdKnown = needed
        holdMore = more
        changed()
    }

    /**
     * How much of a word's place the hold's word [index] fills, counting from the first word the hold took: from its length when
     * the text is known, 0 past the end of the text, and a made-up width where the field gave no text.
     */
    fun holdWordWidth(index: Int): Float = when {
        holdKnown < 0 || index >= holdKnown && holdMore -> MADE_UP_WIDTHS[index.mod(MADE_UP_WIDTHS.size)]
        index < 0 || index >= holdKnown -> 0f
        else -> widthFor(holdLetters[index])
    }

    /** The hold's word about to go, counting from the first it took; a lifted hold keeps it while its lines turn into dots. */
    val holdNext get() = if (scene == Scene.DeleteHold) deleted else morphNext
    private var morphNext = 0
    /** True while the held delete's finger rests in the pause zone and no words go. */
    var deletePaused = false
        private set
    /** When the pause's caret started blinking, or -1. */
    private var pausedAt = -1L
    /** The letters of the hold's words, from the first it took, as far as [holdKnown]; -1 when the field gave no text. */
    private var holdLetters = IntArray(32)
    private var holdKnown = -1
    private var holdMore = false
    /** How fast the hold goes, from 0 to 1, and the pace the glow shows, which follows it smoothly. */
    private var deletePace = 0f
    var shownPace = 0f
        private set

    /**
     * True when the glow is a delete's: a single flick's while the stroke goes on, or a held delete's. It is the Space glow's
     * underglow, in its softness, thickness and colour, along the bottom from the right corner, where Backspace is (owner,
     * 2026-11-12: the right edge looked bad on the bar's rounded corner). A flick's reaches to the finger; a hold's reaches
     * further and gets stronger as the pace speeds up ([shownPace]), and fades out in the pause zone.
     */
    var glowDelete = false
        private set

    /**
     * True while the glow is a triple press's that stays down: the same glow as steering's, a little thicker, held until the
     * lift or until a slide makes it a select's.
     */
    var pickGlow = false
        private set
    /** True when the glow is a triple press's, held or selecting: it is drawn a little thicker than steering's. */
    val glowThick get() = pickGlow || glowHeld && scene == Scene.WordDrag
    /** The fastest a hold has gone, and the pace its dots at the lift speed up to: see [plan]. */
    private var topPace = 0f
    private var dotPace = 1f

    /** One word of [letters] went. In a hold that is one more word; a flick that lifted gets a brief flourish. */
    fun wordDeleted(now: Long, letters: Int = 1) {
        if (scene == Scene.DeleteHold && !leaving) {
            deleted++
            held += letters.coerceAtLeast(1)
            changed()
        } else {
            if (!allowed || live) return
            // Dots still playing take the new ones at the end of their queue, and go on.
            if (scene == Scene.DeleteFlick && dots > 0 && !still && now - flourishAt < doneAt) {
                chain(letters, (now - flourishAt).toFloat())
                return
            }
            setGroups(listOf(letters))
            startFlourish(Scene.DeleteFlick, now)
            dotPace = 1f
            fromHold = false
            dotsInMillis = FLICK_IN
            startStream()
        }
    }

    /** The first batch of a stream: the dots that [setGroups] put in place. */
    private fun startStream() {
        room(dots, 1)
        batches = 1
        batchFrom[0] = 0
        batchCount[0] = dots
        batchAt[0] = 0f
        placeDots(0)
        planned = Float.NaN
        chained = false
        baseTarget = 0f
        shownBase = 0f
        unitsTarget = min(places().toFloat(), MAX_ROW)
        shownUnits = unitsTarget
    }

    /** Makes room for [dots] dots and [batches] deletes. */
    private fun room(dots: Int, batches: Int) {
        if (dots > dotLand.size) {
            val size = max(dots, dotLand.size * 2)
            dotLand = dotLand.copyOf(size)
            dotPopAt = dotPopAt.copyOf(size)
            dotPlace = dotPlace.copyOf(size)
            dotStep = dotStep.copyOf(size)
        }
        if (batches > batchFrom.size) {
            val size = max(batches, batchFrom.size * 2)
            batchFrom = batchFrom.copyOf(size)
            batchCount = batchCount.copyOf(size)
            batchAt = batchAt.copyOf(size)
        }
    }

    /** Each dot's place in the row from [from] on: one after another, with a gap where each space was. */
    private fun placeDots(from: Int) {
        var index = 0
        var place = 0
        for ((group, count) in groups.withIndex()) {
            for (k in 0 until count) {
                if (index >= from) dotPlace[index] = place
                index++
                place++
            }
            if (group < groups.lastIndex) place++
        }
    }

    private fun places() = if (dots == 0) 1 else dotPlace[dots - 1] + 1

    /** A delete of [letters] [at] real ms into a stream that still plays: its dots go at the end of the queue. */
    private fun chain(letters: Int, at: Float) {
        val count = letters.coerceIn(1, MAX_DOTS)
        room(dots + count, batches + 1)
        val from = dots
        groups += count
        dots += count
        placeDots(from)
        batchFrom[batches] = from
        batchCount[batches] = count
        batchAt[batches] = at
        batches++
        planned = Float.NaN
        chained = true
        // The row slides along: the place next to the caret is now the first dot that hasn't landed, and the rest fit after it.
        var next = 0
        while (next < from && dotLand[next] <= at) next++
        baseTarget = dotPlace[next].toFloat()
        unitsTarget = (places() - baseTarget).coerceIn(1f, MAX_ROW)
        changed()
    }

    /**
     * Works out when each dot pops in and lands, in real ms after the stream started, at animator [scale]. A delete that joins
     * starts the wait again for the whole queue (owner, 2026-11-12): every dot that hasn't set off for the caret by then holds
     * still, and from the join they all wait the flick's wait, then go in order at the usual pace and speed-up, the new ones last.
     * A dot already on its way still lands as it was going to.
     */
    private fun plan(scale: Float) {
        if (planned == scale || scale <= 0f) return
        planned = scale
        var last = 0f
        var interval = 0f
        for (b in 0 until batches) {
            val from = batchFrom[b]
            val count = batchCount[b]
            val base = dotStepMillis(count, scale)
            // A lifted hold's dots speed up only as far as its words did: to the fastest gap once the finger reached the
            // top-speed line, not at all if it stayed at the slowest. Flicks that join go as fast as they can.
            val fastest = if (b == 0 && fromHold) base + (DOT_FASTEST_GAP - base) * dotPace else DOT_FASTEST_GAP
            val inMillis = if (b == 0) dotsInMillis else FLICK_IN
            val wait = if (b == 0 && fromHold) FLICK_HOLD else flickHoldMillis()
            val earliest = batchAt[b] + (inMillis + wait + DOT_TRAVEL) * scale
            if (b > 0) {
                // A join: the first dot not yet on its way, and every one after it, go again from the join's wait.
                var held = 0
                while (held < from && dotLand[held] - DOT_TRAVEL * scale <= batchAt[b]) held++
                // The first held one waits for the join's wait, and for the dot ahead of it; the rest keep their steps behind it.
                for (index in held until from) dotLand[index] = when {
                    index > held -> dotLand[index - 1] + dotStep[index]
                    index == 0 -> earliest
                    else -> max(earliest, dotLand[index - 1] + dotStep[index])
                }
                if (held < from) last = dotLand[from - 1]
            }
            for (j in 0 until count) {
                val index = from + j
                interval = if (speedUp) max(min(fastest, base), base * DOT_SPEED_UP.pow(max(0, index - DOT_SPEED_FROM))) else base
                last = when {
                    // The first batch keeps exact steps, so its dots land just where they always did.
                    !speedUp && b == 0 -> earliest + j * base
                    j == 0 && b == 0 -> earliest
                    j == 0 -> max(earliest, last + interval)
                    else -> last + interval
                }
                dotLand[index] = last
                dotStep[index] = interval
                dotPopAt[index] = if (b == 0 && fromHold) Float.NEGATIVE_INFINITY
                    else batchAt[b] + j.toFloat() / count * POP_SPREAD * FLICK_IN * scale
            }
        }
        doneAt = last + interval
    }

    /**
     * How far dot [index] has popped in, from 0 to 1. A lifted hold's first dots come out of its lines instead: see [dotsIn].
     */
    fun dotPopped(index: Int): Float {
        if (still || index >= dots) return 1f
        val at = dotPopAt[index]
        if (at == Float.NEGATIVE_INFINITY) return 1f
        return ((streamAt - at) / (POP_LENGTH * FLICK_IN * planned)).coerceIn(0f, 1f)
    }

    /** When dot [index] lands, in real ms after the stream started, at animator [scale]. */
    fun dotLandsAt(index: Int, scale: Float = 1f): Float {
        plan(scale)
        return dotLand[index]
    }

    /** The place in the row of dot [index], counting a gap for each space; the row's place [shownBase] is next to the caret. */
    fun dotPlace(index: Int) = dotPlace[index]

    /**
     * When the last of a single flick's dots has popped in, in ms after the flick, at Android's animator duration [scale]. Null
     * when no flick's dots pop in now, or with no motion. The delete flick's haptic lands its tail then.
     */
    fun dotsPoppedMillis(scale: Float): Long? {
        if (scene != Scene.DeleteFlick || dots == 0 || scale <= 0f || batches == 0) return null
        if (batches == 1 && fromHold) return null
        val count = batchCount[batches - 1]
        val last = (count - 1).toFloat() / count
        return ((last * POP_SPREAD + POP_LENGTH) * FLICK_IN * scale).toLong()
    }

    /**
     * When each of the dots lands in the caret, nearest the caret first, in ms after they started: a single flick's, or a hold's
     * at its lift. At Android's animator duration [scale]. Empty when no dots show now, or with no motion. The dots land haptic
     * ticks on these.
     */
    fun dotLandings(scale: Float): List<Long> {
        if (scene != Scene.DeleteFlick || dots == 0 || scale <= 0f || batches == 0) return emptyList()
        plan(scale)
        // Every dot still to land after the latest delete came, in ms after it came: a join lays the whole queue again, so its
        // ticks all go again too. A dot that lands within 30 ms of the last tick lands without one, so the ticks stay apart when
        // the dots speed up; a whole stream is counted, so the ticks keep clear of the ones already played.
        val at = batchAt[batches - 1]
        val ticks = ArrayList<Long>()
        var lastTick = Float.NEGATIVE_INFINITY
        // Each counted from the first to come, so their steps round as the dots' own steps do.
        var first = Float.NaN
        for (index in 0 until dots) {
            val land = dotLand[index]
            if (land - lastTick < DOT_LAND_GAP - .5f) continue
            lastTick = land
            if (land <= at) continue
            if (first.isNaN()) first = land
            ticks += (first - at).roundToLong() + (land - first).roundToLong()
        }
        return ticks
    }

    /** How far dot [index] has gone on its way into the caret: 0 before it sets off, 1 as it lands, and more after. */
    fun dotTravel(index: Int): Float {
        if (still || index >= dots || planned.isNaN()) return 0f
        return (streamAt - (dotLand[index] - DOT_TRAVEL * planned)) / (DOT_TRAVEL * planned)
    }

    /** When the dots that show now started, an uptime, or null when none show. Tells one flick's or hold's dots from the next. */
    val dotsAt get() = if (scene == Scene.DeleteFlick) flourishAt else null

    /**
     * The dots for words of [letters], nearest the caret first: a dot for each character while they come to [MAX_DOTS] or fewer.
     * Past that each word shrinks to its share of [MAX_DOTS], and keeps one dot at least, so every word that went shows: a long
     * hold's row is longer than the bar, and slides along as its dots land.
     */
    private fun setGroups(letters: List<Int>) {
        val total = letters.sumOf { it.coerceAtLeast(1) }
        groups = IntArray(letters.size) { index ->
            val count = letters[index].coerceAtLeast(1)
            if (total <= MAX_DOTS) count else (count * MAX_DOTS.toFloat() / total).roundToInt().coerceIn(1, MAX_DOTS)
        }
        dots = groups.sum()
    }

    /** New input ends a flourish at once: it is only a picture, and nothing waits for it. */
    fun interrupt() {
        if (!flourishing) return
        clear()
        changed()
    }

    /**
     * The hold lifted. Its lines shrink and turn into a dot for each character it deleted, grouped by word, which then go into the
     * caret as a single flick's do. A hold that deleted nothing just goes. True when the dots start.
     */
    fun deleteEnded(now: Long = lastFrame): Boolean {
        if (scene != Scene.DeleteHold || leaving) return false
        if (held.isEmpty()) {
            leave()
            return false
        }
        setGroups(held)
        dotPace = topPace
        if (glowHeld) fadeGlow(now)
        val from = shownDeleted
        val beat = flow
        // The lines keep their real lengths as they turn into dots.
        val known = holdKnown
        val more = holdMore
        val next = deleted
        resetPieces()
        holdKnown = known
        holdMore = more
        morphNext = next
        scene = Scene.DeleteFlick
        flourishAt = now
        lastFrame = now
        revealedAtStart = reveal
        fromHold = true
        morphFrom = from
        morphFlow = beat
        dotsInMillis = MORPH_IN
        startStream()
        changed()
        return true
    }

    /**
     * A double tap, or the third touch of a triple, selected the word at the cursor. While that touch is [held] down, the mark
     * stays over the word and the selected text waits under it; it goes only after [pickLifted], at its usual pace.
     */
    fun wordSelected(now: Long, held: Boolean = false) {
        startFlourish(Scene.WordSelected, now)
        if (scene != Scene.WordSelected) return
        pickHeld = held
    }

    /** The touch that picked a word lifted: its mark goes as usual, and the selected text shows. A held pick's glow fades. */
    fun pickLifted(now: Long = lastFrame) {
        if (pickGlow && glowHeld) {
            fadeGlow(now)
            changed()
        }
        if (!pickHeld) return
        pickHeld = false
        changed()
    }

    /**
     * The third press of a triple stayed down long enough to count as held, before any slide: the glow grows to the whole bar as
     * steering's does, and stays until the lift, every time, whether or not the finger then slides to take more words.
     */
    fun pickHeldGlow(now: Long) {
        if (!allowed || glowHeld) return
        holdGlow(now)
        pickGlow = true
        changed()
    }

    /**
     * The word swipe reached touchpad point [x], [y]. The first point of a swipe starts the mini path, also while the last one
     * still fades. Points close to the last one are skipped, and a long path keeps every other point, so it never runs out of room.
     */
    fun swipePoint(x: Float, y: Float, step: Int, now: Long) {
        if (scene != Scene.Swipe || leaving) {
            if (live && scene != Scene.Swipe || !begin(Scene.Swipe, now)) return
            pathSize = 0
            thickness.reset()
        }
        // The thickness follows every point's size, also one too close to draw, so it stays live.
        val width = thickness.next(x, y, step)
        if (pathSize > 0 && abs(x - pathX[pathSize - 1]) + abs(y - pathY[pathSize - 1]) < PATH_STEP) {
            if (abs(width - pathWidth[pathSize - 1]) < WIDTH_STEP) return
            pathWidth[pathSize - 1] = width
            changed()
            return
        }
        if (pathSize == MAX_PATH) {
            for (index in 0 until MAX_PATH / 2) {
                pathX[index] = pathX[index * 2]
                pathY[index] = pathY[index * 2]
                pathWidth[index] = pathWidth[index * 2]
            }
            pathSize = MAX_PATH / 2
        }
        pathX[pathSize] = x
        pathY[pathSize] = y
        pathWidth[pathSize] = width
        pathSize++
        changed()
    }

    /**
     * The swipe lifted: the path fades and the words come back with what it typed. It waits [carryMillis] first, the time in which a
     * slip or a short lift may still carry the swipe on: see [swipeCarriesOn].
     */
    fun swipeEnded(now: Long = 0L, carryMillis: Long = 0L) {
        if (scene != Scene.Swipe || leaving) return
        swipeFadeAt = now + carryMillis
        leave()
    }

    /**
     * A new touch carries on the swipe that just lifted, as the trail joins it after a slip: the path stays and goes on as one line.
     * True if the path was still there to carry on.
     */
    fun swipeCarriesOn(): Boolean {
        if (scene != Scene.Swipe || !leaving) return false
        leaving = false
        changed()
        return true
    }

    /** True while a swipe's path shows and has not lifted, so the next points add to it. */
    val swiping get() = scene == Scene.Swipe && !leaving

    /** The touch turned out to be no word swipe, such as a delete flick: the path goes at once. A path that already fades stays. */
    fun swipeCancelled() {
        if (scene != Scene.Swipe || leaving) return
        clear()
        changed()
    }

    /**
     * A Space tap counted as tap [taps] of a series. We can't know how many will come, so each one grows the glow from where it is:
     * small and soft for one, wider and brighter for two, and full under the middle word with a quick pulse for three.
     */
    fun spaceTapped(taps: Int, now: Long) {
        if (!allowed || taps !in 1..3) return
        if (glowFading) endGlow()
        glowFromWidth = glowWidth
        glowFromAlpha = glowAlpha
        glowTaps = taps
        glowAt = now
        changed()
    }

    /** A key press: the glow goes at once, since those taps did not make a gesture. */
    fun glowOff() {
        if (glowTaps == 0 && !glowFading && !pickGlow) return
        endGlow()
        changed()
    }

    private fun endGlow() {
        glowHeld = false
        glowFading = false
        glowDelete = false
        flickReach = Float.NaN
        reachEasing = false
        pickGlow = false
        glowCentre = .5f
        centreTarget = .5f
        glowTaps = 0
        glowWidth = 0f
        glowBase = 0f
        glowPulse = 0f
    }

    /** Moves the glow on to [now]. True while it still shows. */
    private fun advanceGlow(now: Long, scale: Float): Boolean {
        if (glowHeld) return advanceHeldGlow(now, scale)
        val motion = scale > 0f
        if (glowFading) {
            // A late flick glow's fade starts after the lift, and until then it holds at full.
            val t = if (motion) ((now - glowAt) / (GLOW_FADE * scale)).coerceAtLeast(0f) else 1f
            if (t >= 1f) {
                endGlow()
                return false
            }
            glowBase = glowFromAlpha * (1f - easeOut(t))
            return true
        }
        if (glowTaps == 0) return false
        val elapsed = (now - glowAt) / if (motion) scale else 1f
        if (elapsed >= GLOW_GROW + GLOW_FADE) {
            endGlow()
            return false
        }
        val width = GLOW_WIDTHS[glowTaps]
        val alpha = GLOW_ALPHAS[glowTaps]
        if (!motion) {
            glowWidth = width
            glowBase = alpha
            glowPulse = 0f
            return true
        }
        val grow = easeOut((elapsed / GLOW_GROW).coerceIn(0f, 1f))
        // It fades slowly at first, so it is still there when the next tap of a series lifts and grows it further.
        val fade = ((elapsed - GLOW_GROW) / GLOW_FADE).coerceIn(0f, 1f)
        glowWidth = glowFromWidth + (width - glowFromWidth) * grow
        glowBase = (glowFromAlpha + (alpha - glowFromAlpha) * grow) * (1f - fade * fade)
        glowPulse = if (glowTaps == 3) 1f - (elapsed / GLOW_PULSE).coerceIn(0f, 1f) else 0f
        return true
    }

    /**
     * A tap's glow carries on into the steer or select that a held touch after it starts: from where it is, it grows to the full
     * width and stays as part of that display, and it goes with it. So there is no gap or jump between the taps and the hold.
     */
    private fun holdGlow(now: Long, width: Float = GLOW_HELD_WIDTH, alpha: Float = GLOW_HELD_ALPHA) {
        glowFromWidth = if (glowFading) 0f else glowWidth
        glowFromAlpha = if (glowFading) 0f else glowAlpha
        glowFading = false
        glowHeld = true
        glowTaps = 0
        glowPulse = 0f
        glowAt = now
        glowFrame = now
        heldWidth = width
        heldAlpha = alpha
        glowDelete = false
        flickReach = Float.NaN
        reachEasing = false
        pickGlow = false
        glowCentre = .5f
        centreTarget = .5f
    }

    private fun advanceHeldGlow(now: Long, scale: Float): Boolean {
        val motion = scale > 0f
        if (glowDelete) return advanceDeleteGlow(now, motion)
        val grow = if (motion) easeOut(((now - glowAt) / (GLOW_HOLD_GROW * scale)).coerceIn(0f, 1f)) else 1f
        glowWidth = glowFromWidth + (heldWidth - glowFromWidth) * grow
        glowBase = (glowFromAlpha + (heldAlpha - glowFromAlpha) * grow)
        glowCentre = ease(glowCentre, centreTarget, max(0L, now - glowFrame).toFloat(), EASE_MILLIS, motion)
        glowFrame = now
        return motion && (grow < 1f || abs(glowCentre - centreTarget) > .001f)
    }

    /**
     * A held delete's glow: it grows in from the taps' glow, if any, then follows the pace smoothly, stronger and on the bottom
     * longer as the hold speeds up, and fades out while the finger rests in the pause zone.
     */
    private fun advanceDeleteGlow(now: Long, motion: Boolean): Boolean {
        val elapsed = max(0L, now - glowFrame).toFloat()
        glowFrame = now
        shownPace = ease(shownPace, deletePace, elapsed, DELETE_GLOW_MILLIS, motion)
        val target = if (deletePaused) 0f else heldAlpha
        glowBase = ease(glowBase, target, elapsed, if (target < glowBase) DELETE_GLOW_FADE else DELETE_GLOW_MILLIS, motion)
        // A flick's reaches to the finger and follows it closely; a hold's reach follows its pace, and the change from one to
        // the other eases, so the glow never jumps as a flick becomes a hold.
        val flick = !flickReach.isNaN()
        if (flick) flickFramed = true
        val reach = if (flick) flickReach else DELETE_REACH_LEAST + (DELETE_REACH_MOST - DELETE_REACH_LEAST) * shownPace
        if (flick || reachEasing) {
            glowWidth = ease(glowWidth, reach, elapsed, if (flick) EASE_MILLIS else DELETE_GLOW_MILLIS, motion)
            if (!flick && abs(glowWidth - reach) < .002f) reachEasing = false
        } else glowWidth = reach
        return motion && (abs(shownPace - deletePace) > .002f || abs(glowBase - target) > .002f || abs(glowWidth - reach) > .002f)
    }

    /** A held delete or a held pick lifted: its glow stays where it is and fades over [GLOW_FADE]. */
    private fun fadeGlow(now: Long) {
        pickGlow = false
        glowHeld = false
        glowFading = true
        glowFromAlpha = glowBase
        glowAt = now
    }

    /** Moves the picture on to [now]. [scale] is Android's animator duration scale, and 0 means no motion. True asks for another frame. */
    fun advance(now: Long, scale: Float): Boolean {
        val glowing = advanceGlow(now, scale)
        if (scene == Scene.None) return glowing
        val elapsed = max(0L, now - lastFrame).toFloat()
        lastFrame = now
        val motion = scale > 0f
        if (flourishing) {
            if (scene == Scene.DeleteFlick && motion && dots > 0) plan(scale)
            // A pick whose touch is still down stays at the end of its hold, so the mark keeps showing until the lift.
            if (scene == Scene.WordSelected && pickHeld) {
                val stay = if (motion) (PICK_IN + PICK_HOLD) * scale else STILL_MILLIS / 2
                if (now - flourishAt > stay) flourishAt = now - stay.toLong()
            }
            val length = if (!motion) STILL_MILLIS else when (scene) {
                Scene.DeleteFlick -> if (dots > 0) (doneAt + FLICK_BACK * scale) / scale else FLICK_MILLIS
                Scene.WordSelected -> PICK_MILLIS
                else -> FLOURISH_MILLIS
            } * scale
            val t = ((now - flourishAt) / length).coerceAtLeast(0f)
            if (t >= 1f) {
                clear()
                return glowing
            }
            flourish = if (motion) easeOut(t) else 1f
            reveal = if (!motion) 1f
                else if (scene == Scene.DeleteFlick) flickReveal((now - flourishAt).toFloat(), scale)
                else if (scene == Scene.WordSelected) pickReveal((now - flourishAt) / scale)
                else min(revealedAtStart + (1f - revealedAtStart) * t / FLOURISH_IN, (1f - t) / FLOURISH_OUT).coerceIn(0f, 1f)
        } else {
            // A lifted swipe's path stays while the swipe may still carry on, then fades.
            val target = if (leaving && !(scene == Scene.Swipe && now < swipeFadeAt)) 0f else 1f
            val hide = if (scene == Scene.Swipe) SWIPE_FADE else HIDE_MILLIS
            reveal = if (!motion) target else toward(reveal, target, elapsed / ((if (target > reveal) SHOW_MILLIS else hide) * scale))
            if (leaving && reveal == 0f) {
                clear()
                return glowing
            }
            // A swipe's path moves only when a point comes, so it needs frames only while it comes in or fades.
            if (scene == Scene.Swipe) return motion && reveal != target || leaving && now < swipeFadeAt || glowing
        }
        val seconds = min(elapsed, MAX_FRAME_MILLIS) / 1000f
        when (scene) {
            Scene.Cursor -> {
                intensity = ease(intensity, speedTarget, elapsed, EASE_MILLIS, motion)
                steerLean = ease(steerLean, leaning * (LEAN_BASE + (1f - LEAN_BASE) * intensity), elapsed, LEAN_MILLIS, motion)
                shownStep = ease(shownStep, stepped.toFloat(), elapsed, STEP_MILLIS, motion)
                // Steering's caret never blinks.
                caretShown = true
                if (motion) {
                    val next = flow + leaning * flowRate(intensity) * seconds
                    letters = (letters + floor(next).toInt()).mod(PATTERN_LETTERS)
                    flow = next.mod(1f)
                }
            }
            Scene.WordDrag -> {
                intensity = ease(intensity, speedTarget, elapsed, EASE_MILLIS, motion)
                // The glow follows the hand as steering's does.
                steerLean = ease(steerLean, leaning * (LEAN_BASE + (1f - LEAN_BASE) * intensity), elapsed, LEAN_MILLIS, motion)
                shownLeft = ease(shownLeft, wordsLeft.toFloat(), elapsed, STEP_MILLIS, motion)
                shownRight = ease(shownRight, wordsRight.toFloat(), elapsed, STEP_MILLIS, motion)
                leadingRight = !(wordsLeft > 0 || (wordsRight == 0 && leaning < 0))
                val pull = leaning * intensity * WORD_PULL
                spanStart = -.5f - shownLeft + if (leadingRight) 0f else pull
                spanEnd = .5f + shownRight + if (leadingRight) pull else 0f
                camera = ease(camera, cameraTarget(), elapsed, EASE_MILLIS, motion)
            }
            Scene.DeleteHold -> {
                shownDeleted = ease(shownDeleted, deleted.toFloat(), elapsed, min(deleteMillis * DELETE_EASE, MAX_DELETE_EASE_MILLIS), motion)
                if (motion && !deletePaused) flow = (flow + min(FLOW_FASTEST, 1000f / deleteMillis) * seconds).mod(1f)
                // In the pause zone the caret blinks; it is the only caret that does. With motion off it stays on.
                if (deletePaused && pausedAt < 0) pausedAt = now
                caretShown = !motion || !deletePaused || (now - pausedAt) / BLINK_MILLIS % 2 == 0L
            }
            Scene.DeleteFlick -> {
                // The text is already gone; only the picture moves. The dots come in, stay a while, and go into the caret.
                still = !motion
                streamAt = (now - flourishAt).toFloat()
                // A lifted hold's dots stay on the hold's full cover until they have all gone in, so its row of dots never lies
                // over the suggestions, as it once crossed out a word (owner, 2026-11-12); the words then come back as usual.
                solid = if (fromHold) 1f else 0f
                val sinceStart = if (motion) streamAt / scale else dotsInMillis
                dotsIn = (sinceStart / dotsInMillis).coerceIn(0f, 1f)
                dotsOut = if (motion && dots > 0) {
                    val from = dotLand[0] - DOT_TRAVEL * scale
                    ((streamAt - from) / (doneAt - from)).coerceIn(0f, 1f)
                } else 0f
                // A stream that deletes joined, or a row too long for the bar, moves along like a queue: as each dot lands, the ones
                // behind it close up towards the caret, so the newest never wait far off. A single delete's row stays put.
                if ((batches > 1 || places() > MAX_ROW) && motion && !planned.isNaN()) {
                    var next = 0
                    while (next < dots - 1 && dotLand[next] - DOT_TRAVEL * scale <= streamAt) next++
                    baseTarget = max(baseTarget, dotPlace[next].toFloat())
                    unitsTarget = (places() - baseTarget).coerceIn(1f, MAX_ROW)
                }
                shownBase = ease(shownBase, baseTarget, elapsed, SLIDE_MILLIS, motion)
                shownUnits = ease(shownUnits, unitsTarget, elapsed, SLIDE_MILLIS, motion)
                shownDeleted = 0f
                flow = 0f
            }
            Scene.WordSelected -> {
                // The mark grows over the word, stays a beat so it can be seen, and goes with the cover.
                val grow = if (motion) easeOut((((now - flourishAt) / scale) / PICK_IN).coerceIn(0f, 1f)) else 1f
                spanStart = -.5f * grow
                spanEnd = .5f * grow
                leadingRight = true
            }
            Scene.Swipe, Scene.None -> Unit
        }
        return motion || flourishing || glowing
    }

    /** A selection that fits stays in the middle. A longer one keeps its moving end in view. */
    private fun cameraTarget(): Float {
        val span = wordsLeft + wordsRight + 1f
        return when {
            span <= WORDS_IN_VIEW -> (wordsRight - wordsLeft) / 2f
            wordsRight > 0 -> wordsRight + .5f - WORDS_IN_VIEW / 2f
            else -> -wordsLeft - .5f + WORDS_IN_VIEW / 2f
        }
    }

    private fun begin(next: Scene, now: Long): Boolean {
        if (!allowed) return false
        if (scene != next) {
            resetPieces()
            scene = next
            lastFrame = now
            if (next == Scene.Cursor || next == Scene.WordDrag) {
                // A held pick's glow carries on as the select's.
                if (!glowHeld || glowDelete) holdGlow(now) else pickGlow = false
            } else if (glowHeld && !(glowDelete && (next == Scene.Swipe || next == Scene.DeleteHold))) endGlow()
        }
        leaving = false
        return true
    }

    private fun leave() {
        leaving = true
        changed()
    }

    private fun startFlourish(next: Scene, now: Long) {
        if (!allowed || live) return
        pickHeld = false
        resetPieces()
        scene = next
        leaving = false
        flourishAt = now
        lastFrame = now
        revealedAtStart = reveal
        changed()
    }

    /**
     * The dots' cover, which fades the words behind them: the words fade out over [FLICK_FADE] from where they were, stay faded
     * while the dots go into the caret, and fade back in over [FLICK_BACK], both easing out, so they never switch.
     */
    private fun flickReveal(real: Float, scale: Float): Float {
        val back = ((real - doneAt) / (FLICK_BACK * scale)).coerceIn(0f, 1f)
        if (back > 0f) return 1f - easeOut(back)
        return revealedAtStart + (1f - revealedAtStart) * easeOut((real / scale / FLICK_FADE).coerceIn(0f, 1f))
    }

    /** A picked word's cover: in quickly from where it was, so the suggestions never show through, it holds, then it fades out. */
    private fun pickReveal(elapsed: Float): Float {
        val out = ((elapsed - PICK_IN - PICK_HOLD) / PICK_OUT).coerceIn(0f, 1f)
        if (out > 0f) return 1f - out * out * (3 - 2 * out)
        return revealedAtStart + (1f - revealedAtStart) * (elapsed / FLICK_COVER_IN).coerceIn(0f, 1f)
    }

    private fun clear() {
        scene = Scene.None
        // A flick's glow lives with its stroke, not with the path that the stroke cancels.
        if (glowHeld && (!glowDelete || flickReach.isNaN())) endGlow()
        fromHold = false
        pickHeld = false
        solid = 0f
        batches = 0
        dots = 0
        groups = IntArray(0)
        chained = false
        still = false
        planned = Float.NaN
        reveal = 0f
        leaving = false
        resetPieces()
        line = null
        caretAt = 0
        stepped = 0
        shownStep = 0f
        anchorLetters = null
        leftWords = IntArray(0)
        rightWords = IntArray(0)
        wordsMoreBefore = false
        wordsMoreAfter = false
    }

    private fun resetPieces() {
        pathSize = 0
        leaning = 0
        speedTarget = 0f
        intensity = 0f
        steerLean = 0f
        caretShown = true
        flow = 0f
        letters = 0
        wordsLeft = 0
        wordsRight = 0
        shownLeft = 0f
        shownRight = 0f
        spanStart = -.5f
        spanEnd = .5f
        leadingRight = true
        camera = 0f
        deleted = 0
        shownDeleted = 0f
        held.clear()
        morphNext = 0
        deletePaused = false
        pausedAt = -1L
        holdKnown = -1
        holdMore = false
        // A delete glow still showing keeps its strength through a change of scene, such as a flick's dots after its stroke.
        if (!glowDelete) {
            deletePace = 0f
            shownPace = 0f
        }
        topPace = 0f
        flourish = 0f
        bandStart = .5f
        bandEnd = .5f
    }

    private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

    private fun toward(value: Float, target: Float, step: Float) =
        if (value < target) min(value + step, target) else max(value - step, target)

    private fun ease(value: Float, target: Float, elapsed: Float, millis: Float, motion: Boolean) =
        if (!motion) target else target + (value - target) * exp(-elapsed / millis)

    companion object {
        /** How many words fit across the strip. */
        const val VISIBLE_WORDS = 7f
        /** The letters of each word in the line of text that the cursor moves through, with one space after each word. */
        val WORD_LETTERS = intArrayOf(3, 5, 2, 4, 6, 3, 4, 2, 5)
        val PATTERN_LETTERS = WORD_LETTERS.sum() + WORD_LETTERS.size
        private const val WORDS_IN_VIEW = VISIBLE_WORDS - 1f

        private const val COLUMNS = 10f
        private const val SHOW_MILLIS = 80f
        private const val HIDE_MILLIS = 140f
        /** How quickly a value catches up with a new one. */
        private const val EASE_MILLIS = 70f
        /** A lean at the slowest speed already brightens its side this much; the fastest brightens it fully. */
        const val LEAN_BASE = .45f
        /** The underglow's side follows the lean a little slower than the speed, so a flick across Space doesn't flash. */
        private const val LEAN_MILLIS = 110f
        private const val STEP_MILLIS = 60f
        /** A held delete's paused caret blinks as Android's text cursor does: this long on, then this long off. */
        const val BLINK_MILLIS = 530L
        /** A frame that comes late must not make the text jump. */
        private const val MAX_FRAME_MILLIS = 48f
        /** Letters a second past the caret: enough to read one way at low speed, and still clearly one way at top speed. */
        private const val FLOW_SLOWEST = 1.5f
        private const val FLOW_FASTEST = 9f
        /** How far the moving end of a word drag leans towards the next word, in words. */
        private const val WORD_PULL = .3f
        private const val DELETE_EASE = .4f
        private const val MAX_DELETE_EASE_MILLIS = 90f
        /** A flourish is short enough to end before the next word starts, and it does not wait for anything. */
        private const val FLOURISH_MILLIS = 260f
        /**
         * A single flick's dots come in, stay a while so you can see them, go into the caret one by one, and then the words fade back
         * in. It is only a picture, so nothing waits for it, and any new input ends it.
         */
        const val FLICK_IN = 120f
        const val FLICK_HOLD = 200f
        /**
         * A wait tried in TitanKeys Debug's Haptics lab, or null for [FLICK_HOLD]. It is only set from [Haptics.readFlickHold],
         * which every other build returns null from, so they always have [FLICK_HOLD].
         */
        @Volatile
        var triedFlickHold: Float? = null
        /**
         * How long a single flick's dots sit on the bar before they go into the caret. A lifted hold's dots wait [FLICK_HOLD]: the
         * lab's slider tries a flick's.
         */
        fun flickHoldMillis() = triedFlickHold ?: FLICK_HOLD
        const val FLICK_OUT = 450f
        /** The words behind the dots fade out over [FLICK_FADE] and back in over [FLICK_BACK], easing out (owner, 2026-11-11). */
        const val FLICK_FADE = 150f
        const val FLICK_BACK = 200f
        const val FLICK_MILLIS = FLICK_IN + FLICK_HOLD + FLICK_OUT + FLICK_BACK
        /**
         * The dots pop in one after another over [FLICK_IN]: a dot whose place in the row is from 0 to 1 starts at that much of
         * [POP_SPREAD] and takes [POP_LENGTH] of it.
         */
        const val POP_SPREAD = .6f
        const val POP_LENGTH = .4f
        /**
         * On their way into the caret over [FLICK_OUT], the farthest of a few dots sets off this much of the way later than the
         * nearest, and each dot's own way takes the rest, [DOT_TRAVEL].
         */
        const val DOT_STAGGER = .55f
        const val DOT_TRAVEL = FLICK_OUT * (1 - DOT_STAGGER)
        /**
         * The least time between two dots landing, in real ms, so each one's haptic tick stays its own on the Titan's motor. A few
         * dots keep the owner's 450 ms way in. More dots stretch it, so 30 of them land over about 900 ms, one after another.
         */
        const val DOT_LAND_GAP = 30f
        /**
         * The experiment "Dots speed up when long": from the [DOT_SPEED_FROM]th dot on, each lands this much sooner after the one
         * before than that one did, down to [DOT_FASTEST_GAP] real ms, so a long delete whooshes in at the end. A dot that lands
         * within 30 ms of the last tick lands without one.
         */
        const val DOT_SPEED_FROM = 3
        const val DOT_SPEED_UP = .92f
        const val DOT_FASTEST_GAP = 10f
        /**
         * The most places, dots and word gaps, that the row fits on the bar at once. A longer row keeps the dots their size and
         * slides along as they land, the rest waiting past the far end.
         */
        const val MAX_ROW = 60f
        /** The row slides along this smoothly when a delete joins the queue. */
        private const val SLIDE_MILLIS = 90f

        /** How far apart [dots] land in the caret, in real ms at animator [scale]: their share of the way in, or [DOT_LAND_GAP]. */
        fun dotStepMillis(dots: Int, scale: Float) = max(FLICK_OUT * DOT_STAGGER * scale / dots, DOT_LAND_GAP)

        /**
         * When a lifted hold's dots land, in ms after the lift, for a hold whose words had [letters] each, as the bar plans them.
         * The haptics lab's simulated hold ticks on these.
         */
        fun holdLandings(letters: List<Int>, scale: Float = 1f, speedUp: Boolean = false): List<Long> {
            val display = GestureDisplay()
            display.speedUp = speedUp
            display.deleteHeld(0, 1)
            for (count in letters) display.wordDeleted(1, count)
            if (!display.deleteEnded(1)) return emptyList()
            return display.dotLandings(scale)
        }

        /**
         * When a single flick of a word of [letters] pops its last dot, and when each of its dots lands, in ms after the flick, as
         * the bar plans them. The haptics lab's Play for the flick's dots plays these.
         */
        fun flickTimes(letters: Int, scale: Float = 1f, speedUp: Boolean = true): Pair<Long?, List<Long>> {
            val display = GestureDisplay()
            display.speedUp = speedUp
            display.wordDeleted(1, letters)
            display.advance(1, scale)
            return display.dotsPoppedMillis(scale) to display.dotLandings(scale)
        }

        /**
         * How long [dots] take to go into the caret, in real ms at animator [scale]: the last one's step plus its own way. That's
         * max(450 × scale, 202.5 × scale + 30 × dots), so the owner's 450 ms stays for up to eight dots.
         */
        fun outMillis(dots: Int, scale: Float) = DOT_TRAVEL * scale + dots * dotStepMillis(dots, scale)
        /** A lifted hold's lines shrink into the dots over this long, in the place of the dots' pop. */
        const val MORPH_IN = 240f

        /**
         * A word picked with no slide, by a double or triple tap: the mark grows over the word, holds a beat so you can see what was
         * picked, then goes cleanly before the suggestions return (owner, 2026-11-11). Any key or touch ends it at once.
         */
        const val PICK_IN = 90f
        const val PICK_HOLD = 400f
        const val PICK_OUT = 300f
        const val PICK_MILLIS = PICK_IN + PICK_HOLD + PICK_OUT
        /** The cover comes in this quickly, so the old words never show through it. */
        private const val FLICK_COVER_IN = 60f
        /** The most points a swipe's path keeps, and the least a point must move from the last, in touchpad space. */
        const val MAX_PATH = 256
        private const val PATH_STEP = .004f
        /** A swipe's mini path fades into what it typed over this long. */
        const val SWIPE_FADE = 300f
        /** A path's thickness changes by at least this much before a point that has not moved is drawn again. */
        private const val WIDTH_STEP = .03f
        /** The glow grows to the full width this quickly when a hold after the taps starts to steer or select. */
        const val GLOW_HOLD_GROW = 120f
        /** A held delete's glow follows the pace this smoothly, and fades out in the pause zone over about this long. */
        const val DELETE_GLOW_MILLIS = 90f
        const val DELETE_GLOW_FADE = 140f
        /** How strong a single flick's glow is, as a hold's pace: about a hold's 4 keys out. */
        const val FLICK_GLOW_PACE = .6f
        /** Every flick's glow shows at full strength for at least this long, even one that lifted before it drew, then fades. */
        const val FLICK_GLOW_SEEN = 120L
        /** How far a held delete's glow along the bottom reaches left from the corner, from the slowest to the top-speed line. */
        const val DELETE_REACH_LEAST = .2f
        const val DELETE_REACH_MOST = .65f
        const val GLOW_HELD_WIDTH = 1f
        const val GLOW_HELD_ALPHA = .8f
        /**
         * The Space glow grows to each tap's size quickly, then fades over [GLOW_FADE] unless another tap comes. Its width and
         * strength for one, two and three taps, as in the owner's mockup; the third pulses for [GLOW_PULSE].
         */
        const val GLOW_GROW = 70f
        const val GLOW_FADE = 250f
        const val GLOW_PULSE = 120f
        val GLOW_WIDTHS = floatArrayOf(0f, .17f, .39f, .71f)
        val GLOW_ALPHAS = floatArrayOf(0f, .35f, .55f, .8f)
        /**
         * The most dots for one word, and about the most for a hold: a hold of more letters than this gives each word its share,
         * one dot at least. The spacing shrinks to fit them.
         */
        const val MAX_DOTS = 30

        /** The characters of the last word in [before], not counting spaces: what a delete flick takes. "don't" gives 5. */
        fun letters(before: CharSequence?): Int {
            val text = before?.trimEnd() ?: return 1
            var count = 0
            for (i in text.indices.reversed()) { if (text[i].isWhitespace()) break; count++ }
            return count.coerceAtLeast(1)
        }
        private const val FLOURISH_IN = .25f
        private const val FLOURISH_OUT = .35f
        /** With motion off, a flourish stays this long as a still picture and then goes. */
        private const val STILL_MILLIS = 320f

        private fun flowRate(level: Float) = FLOW_SLOWEST + (FLOW_FASTEST - FLOW_SLOWEST) * level

        /** Characters of real text read on each side of the caret, more than the strip can show. */
        const val LINE_REACH = 64
        /** Read the text again when the cursor comes this close to the end of what was read. */
        private const val REREAD_MARGIN = 24
        /** Made-up word widths, for a field that gives no text. */
        private val MADE_UP_WIDTHS = floatArrayOf(.62f, .82f, .5f, .74f, .88f, .56f, .7f)
        /** A real word fills from [SHORTEST_WORD] of its place for one letter to all of it at [LONG_WORD] letters. */
        private const val SHORTEST_WORD = .3f
        private const val LONG_WORD = 10

        /** How much of its place a word of [letters] fills. */
        fun widthFor(letters: Int) = SHORTEST_WORD + (1f - SHORTEST_WORD) * (letters.coerceIn(1, LONG_WORD) - 1) / (LONG_WORD - 1)

        /** The letter counts of the words in [text], in order, split at spaces and line ends. */
        fun wordLengths(text: CharSequence): IntArray {
            val lengths = ArrayList<Int>()
            var run = 0
            for (char in text) {
                if (char.isWhitespace()) {
                    if (run > 0) lengths += run
                    run = 0
                } else run++
            }
            if (run > 0) lengths += run
            return lengths.toIntArray()
        }

        /**
         * How fast the cursor goes, from 0 to 1. [CursorMode] speeds grow by even factors, so this goes by factors too. A word
         * drag's speed is the square root of the letter speed that it comes from.
         */
        internal fun speedLevel(speed: Float, words: Boolean): Float {
            val letters = if (words) speed * speed else speed
            if (letters <= 0f) return 0f
            return (ln(letters / CursorMode.SLOWEST_PER_SECOND) / ln(CursorMode.FASTEST_PER_SECOND / CursorMode.SLOWEST_PER_SECOND)).coerceIn(0f, 1f)
        }
    }
}
