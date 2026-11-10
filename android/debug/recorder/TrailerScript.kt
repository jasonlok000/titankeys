package com.atlas.keyboard

import java.text.BreakIterator

/**
 * The demo screen's script, played one input at a time: whatever key, swipe or touch comes next, the script decides what it
 * types. Beats are what a person writes; [steps] are the single inputs that play them. The text is kept as graphemes, so an
 * emoji is one piece, and every change comes out as [Edit]s that the screen animates. It needs no Android.
 */
internal class TrailerScript(beats: List<Beat>) {
    /** One part of the script, as it is written in script.json. */
    sealed interface Beat {
        /** Typed key by key. A word in [slips] is typed with that slip, and the Space after it fixes it. */
        data class Type(val text: String, val slips: Map<String, String> = emptyMap()) : Beat
        /** Swiped word by word. Marks after a word, such as a full stop, are typed with a key. */
        data class Swipe(val text: String) : Beat
        /** Picked from the strip, word by word and mark by mark. [bars] are left, middle and right for each pick. */
        data class Tap(val text: String, val bars: List<List<String>> = emptyList()) : Beat
        /** Deleted with the delete flick. */
        data class Flick(val word: String) : Beat
        /** Selected with taps on Space and a slide. What comes next replaces it. */
        data class Select(val span: String) : Beat
        /** From the Sym panel: [presses] of Sym. */
        data class Sym(val emoji: String, val presses: Int = 2) : Beat
        /** The name, in one swipe, with a landing of its own. */
        data class Brand(val word: String) : Beat
    }

    enum class Input { Key, Space, Swipe, Tap, Flick, Select, Sym }
    enum class Style { Typed, Swiped, Fixed, Emoji, Brand }
    enum class Removal { Quiet, Shatter, Morph }

    /** Replace graphemes [start] to [end] of the text with [insert]. */
    data class Edit(val start: Int, val end: Int, val insert: List<String>, val removal: Removal, val style: Style)
    /** What one input changed: edits in order, and the selection after them, if any. */
    data class Change(val edits: List<Edit>, val selection: IntRange?)

    sealed interface Step {
        val input: Input
        data class Key(val text: String) : Step { override val input = Input.Key }
        data class Space(val fix: Pair<String, String>?) : Step { override val input = Input.Space }
        data class Swipe(val word: String, val brand: Boolean) : Step { override val input = Input.Swipe }
        data class Tap(val token: String, val bar: List<String>) : Step { override val input = Input.Tap }
        data class Flick(val word: String) : Step { override val input = Input.Flick }
        data class Select(val span: String) : Step { override val input = Input.Select }
        data class Sym(val emoji: String, val presses: Int) : Step { override val input = Input.Sym }
    }

    val steps: List<Step> = beats.flatMap(::expand)

    private val text = ArrayList<String>()
    private var cursor = 0
    private var selection: IntRange? = null
    private var at = 0
    private var symPresses = 0

    /** The text as it stands. */
    val shown get() = text.joinToString("")
    /** Where the next letter goes, in graphemes. */
    val cursorAt get() = cursor
    val done get() = at >= steps.size
    /** The next step, or null at the end. */
    val next get() = steps.getOrNull(at)

    /** What the strip shows for the next pick: left, middle and right. Empty when the next step is not a pick. */
    val bar: List<String> get() = (next as? Step.Tap)?.bar.orEmpty()

    fun reset() {
        text.clear()
        cursor = 0
        selection = null
        at = 0
        symPresses = 0
    }

    /** An input came. If it is what the next step takes, the step plays and the change comes back. Otherwise null, and nothing moves. */
    fun input(input: Input): Change? {
        val step = next ?: return null
        if (step.input != input) return null
        if (step is Step.Sym && ++symPresses < step.presses) return Change(emptyList(), selection)
        at++
        symPresses = 0
        val edits = ArrayList<Edit>()
        when (step) {
            is Step.Key -> key(step.text, edits)
            is Step.Space -> space(step.fix, edits)
            is Step.Swipe -> word(step.word, if (step.brand) Style.Brand else Style.Swiped, edits)
            is Step.Tap -> if (isMark(step.token)) key(step.token, edits) else word(step.token, Style.Swiped, edits)
            is Step.Flick -> flick(step.word, edits)
            is Step.Select -> select(step.span)
            is Step.Sym -> edit(cursor, cursor, graphemes(step.emoji), Removal.Quiet, Style.Emoji, edits)
        }
        return Change(edits, selection)
    }

    private fun key(key: String, edits: MutableList<Edit>) {
        val chosen = selection
        if (chosen != null) {
            selection = null
            edit(chosen.first, chosen.last + 1, graphemes(key), Removal.Morph, Style.Typed, edits)
            cursor = chosen.first + graphemes(key).size
            return
        }
        // A mark hugs the word before it, in front of the space that a swipe or a pick left.
        if (isMark(key) && cursor > 0 && text[cursor - 1] == " ") {
            edit(cursor - 1, cursor, graphemes(key) + " ", Removal.Quiet, Style.Typed, edits)
            return
        }
        edit(cursor, cursor, graphemes(key), Removal.Quiet, Style.Typed, edits)
    }

    private fun space(fix: Pair<String, String>?, edits: MutableList<Edit>) {
        if (fix != null) {
            val wrong = graphemes(fix.first)
            val start = cursor - wrong.size
            if (start >= 0 && text.subList(start, cursor) == wrong) edit(start, cursor, graphemes(fix.second), Removal.Morph, Style.Fixed, edits)
        }
        when {
            cursor < text.size && text[cursor] == " " -> cursor++
            cursor > 0 && text[cursor - 1] == " " -> Unit
            else -> edit(cursor, cursor, listOf(" "), Removal.Quiet, Style.Typed, edits)
        }
    }

    /** A swiped or picked word: a space before it if it needs one, and one after it, as the keyboard leaves. */
    private fun word(word: String, style: Style, edits: MutableList<Edit>) {
        val before = if (cursor > 0 && text[cursor - 1] != " ") listOf(" ") else emptyList()
        val after = cursor < text.size && text[cursor] == " "
        edit(cursor, cursor, before + graphemes(word) + if (after) emptyList() else listOf(" "), Removal.Quiet, style, edits)
        if (after) cursor++
    }

    /** The flick takes the word before the cursor, and keeps one space before where it was. */
    private fun flick(word: String, edits: MutableList<Edit>) {
        var end = cursor
        while (end > 0 && text[end - 1] == " ") end--
        val gone = graphemes(word)
        val start = end - gone.size
        if (start < 0 || text.subList(start, end) != gone) return
        edit(start, cursor, if (start > 0 && text[start - 1] == " ") emptyList() else listOf(" "), Removal.Shatter, Style.Typed, edits)
    }

    private fun select(span: String) {
        val wanted = graphemes(span)
        for (start in cursor - wanted.size downTo 0) if (text.subList(start, start + wanted.size) == wanted) {
            selection = start until start + wanted.size
            return
        }
    }

    private fun edit(start: Int, end: Int, insert: List<String>, removal: Removal, style: Style, edits: MutableList<Edit>) {
        for (i in start until end) text.removeAt(start)
        text.addAll(start, insert)
        // The cursor after the edited part moves with it, and one inside it goes to the end of what came in.
        if (cursor >= end) cursor += insert.size - (end - start) else if (cursor > start) cursor = start + insert.size
        edits += Edit(start, end, insert, removal, style)
    }

    companion object {
        private const val MARKS = ".,!?;:"

        fun isMark(token: String) = token.isNotEmpty() && token.all { it in MARKS }

        /** The pieces of [text] that a person sees as one character each. */
        fun graphemes(text: String): List<String> {
            val pieces = ArrayList<String>()
            val breaks = BreakIterator.getCharacterInstance()
            breaks.setText(text)
            var start = breaks.first()
            var end = breaks.next()
            while (end != BreakIterator.DONE) {
                pieces += text.substring(start, end)
                start = end
                end = breaks.next()
            }
            return pieces
        }

        /** A word and the marks after it, as separate tokens: "shit." is "shit" and ".". */
        private fun tokens(text: String) = text.split(' ').filter { it.isNotEmpty() }.flatMap { word ->
            val marks = word.takeLastWhile { it in MARKS }
            listOfNotNull(word.dropLast(marks.length).takeIf { it.isNotEmpty() }) + marks.map { it.toString() }
        }

        private fun expand(beat: Beat): List<Step> = when (beat) {
            is Beat.Type -> {
                val steps = ArrayList<Step>()
                var word = StringBuilder()
                var typed = beat.text
                for ((right, slip) in beat.slips) typed = typed.replace(right, slip)
                for (piece in graphemes(typed)) {
                    if (piece == " ") {
                        val fix = beat.slips.entries.firstOrNull { it.value == word.toString() }?.let { it.value to it.key }
                        steps += Step.Space(fix)
                        word = StringBuilder()
                    } else {
                        steps += Step.Key(piece)
                        if (!isMark(piece)) word.append(piece)
                    }
                }
                steps
            }
            is Beat.Swipe -> tokens(beat.text).map { if (isMark(it)) Step.Key(it) else Step.Swipe(it, false) }
            is Beat.Tap -> tokens(beat.text).mapIndexed { index, token -> Step.Tap(token, beat.bars.getOrElse(index) { listOf("", token, "") }) }
            is Beat.Flick -> listOf(Step.Flick(beat.word))
            is Beat.Select -> listOf(Step.Select(beat.span))
            is Beat.Sym -> listOf(Step.Sym(beat.emoji, beat.presses))
            is Beat.Brand -> listOf(Step.Swipe(beat.word, true))
        }
    }
}
