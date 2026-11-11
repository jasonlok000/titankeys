package com.atlas.keyboard

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * What the strip shows while a special gesture runs: the cursor drag, the word drag, a held delete flick, and a short flourish
 * for one deleted word or one selected word. An ordinary word swipe has its own trail, so it shows nothing here.
 *
 * The gesture code gives this class the numbers it already has, and [GestureDrawing] reads the numbers that come out. They are
 * all plain values kept in this one object, so a frame makes no garbage, and a test can run it with no clock and no phone.
 * [changed] asks for a new frame.
 */
class GestureDisplay(private val changed: () -> Unit = {}) {
    enum class Scene { None, Cursor, WordDrag, DeleteHold, DeleteFlick, WordSelected }

    /** The dead zone in the middle of Space, from 0 to 1 across the keyboard. */
    val spaceStart = CursorMode.DEAD_LEFT / COLUMNS
    val spaceEnd = CursorMode.DEAD_RIGHT / COLUMNS

    /** False where the strip does not show, such as a field with no suggestions. Then nothing shows here. */
    var allowed = true
        set(value) {
            if (field == value) return
            field = value
            if (!value) clear()
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

    private var leaving = false
    private var lastFrame = 0L
    private var speedTarget = 0f
    private var shownLeft = 0f
    private var shownRight = 0f
    private var deleteMillis = 200L
    private var flourishAt = 0L
    private var revealedAtStart = 0f

    private val live get() = !leaving && (scene == Scene.Cursor || scene == Scene.WordDrag || scene == Scene.DeleteHold)
    private val flourishing get() = scene == Scene.DeleteFlick || scene == Scene.WordSelected

    /**
     * A single flick: only the orange part shows, a dot for each character that went and the caret, where a hold's words run
     * straight into the caret.
     */
    val softDelete get() = scene == Scene.DeleteFlick

    /** How many dots a single flick shows: the characters it deleted, not counting spaces, at most [MAX_DOTS]. */
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
    /** How long the dots take to come in: a flick's pop, or a lifted hold's morph. */
    private var dotsInMillis = FLICK_IN
    /** How far the dots have come in, from 0 to 1, and how far their way into the caret has gone at the end. */
    var dotsIn = 0f
        private set
    var dotsOut = 0f
        private set

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

    /** The words around a word drag or a picked word: [before] it, the word itself of [letters], and [after] it. */
    fun wordText(before: CharSequence, letters: Int, after: CharSequence) {
        anchorLetters = letters
        leftWords = wordLengths(before).reversedArray()
        rightWords = wordLengths(after)
        changed()
    }

    /**
     * How much of a word's place the word at [index] fills, in a word drag: 0 is the first word, below 0 the words before it.
     * Real words get a width from their length. Past the end of the text there is no word, which is 0. With no text known, a
     * made-up row reads as a line of text.
     */
    fun wordWidth(index: Int): Float {
        val first = anchorLetters ?: return MADE_UP_WIDTHS[index.mod(MADE_UP_WIDTHS.size)]
        val letters = when {
            index == 0 -> first
            index < 0 -> leftWords.getOrNull(-index - 1) ?: return 0f
            else -> rightWords.getOrNull(index - 1) ?: return 0f
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

    /** The finger stayed on a delete flick, and more words will go, one each [repeatMillis]. */
    fun deleteHeld(repeatMillis: Long, now: Long) {
        if (!begin(Scene.DeleteHold, now)) return
        deleteMillis = repeatMillis
        changed()
    }

    /** One word of [letters] went. In a hold that is one more word; a flick that lifted gets a brief flourish. */
    fun wordDeleted(now: Long, letters: Int = 1) {
        if (scene == Scene.DeleteHold && !leaving) {
            deleted++
            held += letters.coerceAtLeast(1)
            changed()
        } else {
            if (!allowed || live) return
            setGroups(listOf(letters))
            startFlourish(Scene.DeleteFlick, now)
            fromHold = false
            dotsInMillis = FLICK_IN
        }
    }

    /** The dots for words of [letters], nearest the caret first, cut at [MAX_DOTS] in all. */
    private fun setGroups(letters: List<Int>) {
        val out = ArrayList<Int>()
        var room = MAX_DOTS
        for (count in letters) {
            if (room <= 0) break
            val take = min(count.coerceAtLeast(1), room)
            out += take
            room -= take
        }
        groups = out.toIntArray()
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
     * caret as a single flick's do. A hold that deleted nothing just goes.
     */
    fun deleteEnded(now: Long = lastFrame) {
        if (scene != Scene.DeleteHold || leaving) return
        if (held.isEmpty()) { leave(); return }
        setGroups(held)
        val from = shownDeleted
        val beat = flow
        resetPieces()
        scene = Scene.DeleteFlick
        flourishAt = now
        lastFrame = now
        revealedAtStart = reveal
        fromHold = true
        morphFrom = from
        morphFlow = beat
        dotsInMillis = MORPH_IN
        changed()
    }

    /** A double tap selected the word at the cursor. */
    fun wordSelected(now: Long) = startFlourish(Scene.WordSelected, now)

    /** Moves the picture on to [now]. [scale] is Android's animator duration scale, and 0 means no motion. True asks for another frame. */
    fun advance(now: Long, scale: Float): Boolean {
        if (scene == Scene.None) return false
        val elapsed = max(0L, now - lastFrame).toFloat()
        lastFrame = now
        val motion = scale > 0f
        if (flourishing) {
            val length = if (!motion) STILL_MILLIS else when (scene) {
                Scene.DeleteFlick -> flickMillis
                Scene.WordSelected -> PICK_MILLIS
                else -> FLOURISH_MILLIS
            } * scale
            val t = ((now - flourishAt) / length).coerceAtLeast(0f)
            if (t >= 1f) {
                clear()
                return false
            }
            flourish = if (motion) easeOut(t) else 1f
            reveal = if (!motion) 1f
                else if (scene == Scene.DeleteFlick) flickReveal((now - flourishAt) / scale)
                else if (scene == Scene.WordSelected) pickReveal((now - flourishAt) / scale)
                else min(revealedAtStart + (1f - revealedAtStart) * t / FLOURISH_IN, (1f - t) / FLOURISH_OUT).coerceIn(0f, 1f)
        } else {
            val target = if (leaving) 0f else 1f
            reveal = if (!motion) target else toward(reveal, target, elapsed / ((if (target > reveal) SHOW_MILLIS else HIDE_MILLIS) * scale))
            if (leaving && reveal == 0f) {
                clear()
                return false
            }
        }
        val seconds = min(elapsed, MAX_FRAME_MILLIS) / 1000f
        when (scene) {
            Scene.Cursor -> {
                intensity = ease(intensity, speedTarget, elapsed, EASE_MILLIS, motion)
                shownStep = ease(shownStep, stepped.toFloat(), elapsed, STEP_MILLIS, motion)
                if (motion) {
                    val next = flow + leaning * flowRate(intensity) * seconds
                    letters = (letters + floor(next).toInt()).mod(PATTERN_LETTERS)
                    flow = next.mod(1f)
                }
            }
            Scene.WordDrag -> {
                intensity = ease(intensity, speedTarget, elapsed, EASE_MILLIS, motion)
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
                if (motion) flow = (flow + min(FLOW_FASTEST, 1000f / deleteMillis) * seconds).mod(1f)
            }
            Scene.DeleteFlick -> {
                // The text is already gone; only the picture moves. The dots come in, stay a while, and go into the caret.
                val elapsed = if (motion) (now - flourishAt) / scale else dotsInMillis
                dotsIn = (elapsed / dotsInMillis).coerceIn(0f, 1f)
                dotsOut = if (motion) ((elapsed - dotsInMillis - FLICK_HOLD) / FLICK_OUT).coerceIn(0f, 1f) else 0f
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
            Scene.None -> Unit
        }
        return motion || flourishing
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
        resetPieces()
        scene = next
        leaving = false
        flourishAt = now
        lastFrame = now
        revealedAtStart = reveal
        changed()
    }

    /**
     * A single flick's cover: in quickly, from where it was, so the old words never show through; it stays while the dots go into
     * the caret, and then the words fade back in.
     */
    private fun flickReveal(elapsed: Float): Float {
        val back = ((elapsed - dotsInMillis - FLICK_HOLD - FLICK_OUT) / FLICK_BACK).coerceIn(0f, 1f)
        if (back > 0f) return 1f - back * back * (3 - 2 * back)
        return revealedAtStart + (1f - revealedAtStart) * (elapsed / FLICK_COVER_IN).coerceIn(0f, 1f)
    }

    /** How long the dots play in all: in, a while, into the caret, and the words back. */
    val flickMillis get() = dotsInMillis + FLICK_HOLD + FLICK_OUT + FLICK_BACK

    /** A picked word's cover: in quickly from where it was, so the suggestions never show through, it holds, then it fades out. */
    private fun pickReveal(elapsed: Float): Float {
        val out = ((elapsed - PICK_IN - PICK_HOLD) / PICK_OUT).coerceIn(0f, 1f)
        if (out > 0f) return 1f - out * out * (3 - 2 * out)
        return revealedAtStart + (1f - revealedAtStart) * (elapsed / FLICK_COVER_IN).coerceIn(0f, 1f)
    }

    private fun clear() {
        scene = Scene.None
        fromHold = false
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
    }

    private fun resetPieces() {
        leaning = 0
        speedTarget = 0f
        intensity = 0f
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
        private const val STEP_MILLIS = 60f
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
        const val FLICK_OUT = 450f
        const val FLICK_BACK = 150f
        const val FLICK_MILLIS = FLICK_IN + FLICK_HOLD + FLICK_OUT + FLICK_BACK
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
        /** The most dots the bar shows, for a long word or a long hold; the spacing shrinks to fit them. */
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
