package com.atlas.keyboard

import com.atlas.autocorrect.ApostropheContext
import com.atlas.autocorrect.Autocorrect
import com.atlas.autocorrect.EndedWord
import com.atlas.autocorrect.FixKind
import com.atlas.autocorrect.TypedCorrector
import com.atlas.engine.KeptWords
import com.atlas.engine.Swipe
import com.atlas.engine.SwipeTyping
import com.atlas.engine.TypedPredictions
import com.atlas.engine.TypedWords
import com.atlas.engine.WordCase
import java.util.concurrent.atomic.AtomicBoolean

/** The text around the cursor, and the edits the keyboard makes there. */
interface Editor {
    data class Identity(val connection: Any, val packageName: String?, val fieldId: Int)
    data class State(val before: String, val after: String, val selectionStart: Int, val selectionEnd: Int)
    enum class Enter { Newline, Next, Previous, Action, Rejected }

    fun identity(): Identity?
    /** A bounded query acknowledged by fresh selection metadata from the app. Null means unverified. */
    fun state(result: (State?) -> Unit)
    /** Null means that the editor cannot supply this text, rather than an empty field. */
    fun before(): String?
    fun after(): String?
    /** Deletes around the cursor, then types over any selection. False may include a partial edit. */
    fun replace(count: Int, text: String, after: Int = 0): Boolean
    /** Characters deleted before the selection; null means that the resulting edit is unverified. */
    fun delete(word: Boolean, selectionStart: Int, selectionEnd: Int): Int?
    fun enter(press: EnterKey.Press): Enter

    companion object {
        const val CONTEXT_LENGTH = 2048
    }
}

/** The word engine: swipe recognition, completions and learning. */
interface Words {
    /** [margin] is how far the best word leads the next one. A larger margin means the swipe was read more clearly. */
    class Recognition(val words: List<String>, val token: Any?, val prediction: SwipeTyping.Prediction? = null,
                      val margin: Float? = prediction?.margin())

    fun recognise(swipe: Swipe, context: String, personalise: Boolean, result: (Recognition) -> Unit)
    fun complete(before: String, cancelled: AtomicBoolean, sentenceCapitalisation: Boolean, result: (List<String>) -> Unit)
    fun prepare(text: String, cancelled: AtomicBoolean)
    fun lookAlike(context: String, word: String, next: String, result: (String?) -> Unit)
    fun allowed(word: String): Boolean
    /** Whether autocorrect may change the letters [typed] to [fix]. Holding a fix on the strip refuses it. */
    fun fixAllowed(typed: String, fix: String): Boolean = true
    fun known(word: String): Boolean
    /** The commonest ways to start a sentence, best first. It answers at once, and is empty until the word lists have loaded. */
    fun sentenceStarts(): List<String> = emptyList()
    fun typed(word: String, rememberCase: Boolean = true)
    /** Takes back the last [typed] use, because the user deleted that word. */
    fun untyped(word: String)
    /** The user kept [next] after [previous]. [previous] is null if [next] starts a sentence. */
    fun keptPair(previous: String?, next: String)
    fun chosen(swipe: Swipe, context: String, recognition: Recognition, word: String)
    fun corrected(swipe: Swipe, context: String, recognition: Recognition, word: String)
    fun kept(swipe: Swipe, context: String, recognition: Recognition, word: String)
    /**
     * The user left the field with this swiped word still there as it was typed, the last word, which [kept] never hears
     * about. Only data sharing listens: the keyboard learns nothing from it, as before.
     */
    fun keptAtEnd(swipe: Swipe, context: String, recognition: Recognition, word: String) = Unit
    /** Runs [work], which may take a while, away from the keyboard's thread when it can, and hands its answer back on that thread. */
    fun background(work: () -> String?, result: (String?) -> Unit) = result(work())
}

/** Told what the keyboard does to the text, only while the raw input log records the field. */
interface EditLog {
    fun recognised(recognition: Words.Recognition, resumed: Boolean)
    /** [deleted] is -1 if the editor can't say how much it deleted. */
    fun edited(cause: EditCause, deleted: Int, inserted: String, deletedAfter: Int, accepted: Boolean)
    fun picked(index: Int, word: String, offered: List<String>)
}

/** Turns taps, swipes and keys into edits and suggestions for one text field. */
class Composer(
    private val editor: Editor,
    private val words: Words,
    private val later: (Long, () -> Unit) -> Unit,
    private val externalChange: () -> Unit,
    private val autoCapitalise: () -> Boolean,
    private val showWords: (List<String>) -> Unit,
) {
    private data class Selection(val start: Int, val end: Int)
    private data class Snapshot(val before: String?, val after: String?, val selection: Selection, val selected: Boolean) {
        val available get() = before != null && after != null
        val knownSelection get() = selection.start >= 0 && selection.end >= 0
    }

    private sealed interface Composition {
        val alternatives: List<String>

        data object None : Composition {
            override val alternatives = emptyList<String>()
        }

        data class Swiped(
            val word: String,
            val leadingSpace: String,
            val trailingSpace: Boolean,
            val swipe: Swipe,
            val context: String,
            val recognition: Words.Recognition,
            val endedAt: Long,
            val continuable: Boolean,
            val snapshot: Snapshot,
            val chosen: Boolean,
            override val alternatives: List<String>,
        ) : Composition {
            val tail get() = word + if (trailingSpace) " " else ""
            val inserted get() = leadingSpace + tail
            var finalised = false
        }

        class Typed(val prefix: String, val trailingSpace: Boolean, val snapshot: Snapshot,
                    override val alternatives: List<String>) : Composition

        /** Popular sentence starts, which stand in when there is nothing better to show. */
        class Starts(val snapshot: Snapshot, override val alternatives: List<String>) : Composition

        /** Snippets or emoji for the code that ends the text in [snapshot]. See [Expansions]. */
        class Expanding(val snapshot: Snapshot, val choices: List<Expansions.Choice>) : Composition {
            override val alternatives = choices.map { it.label }
        }

        /** The cursor is in an earlier swiped word, [before] and [after] letters from its ends. */
        data class Recalled(val word: Remembered, val before: Int, val after: Int, val snapshot: Snapshot) : Composition {
            override val alternatives get() = word.alternatives
        }
    }

    /** A swiped word still in the field, with the suggestions it offered. [start] follows the keyboard's own edits. */
    private data class Remembered(val start: Int, val word: String, val alternatives: List<String>, val swipe: Swipe,
                                  val context: String, val recognition: Words.Recognition) {
        val end get() = start + word.length
    }

    private sealed interface PendingInput
    private data class PendingSwipe(val swipe: Swipe, val startedAt: Long, val endedAt: Long, val metaState: Int) : PendingInput
    private class PendingText(var text: String, val letterTap: Boolean) : PendingInput
    private class PendingEdit(val action: () -> Unit, val transferable: Boolean = false, val leaves: Boolean = false) : PendingInput
    private class FullStop(val time: Long, val snapshot: Snapshot)
    /**
     * One Backspace puts [restored] back in place of [replaced], which ends the text in [snapshot]. [spacing] marks the spaces
     * that went in after a mark, which Backspace takes out again.
     */
    private class Undo(val replaced: String, val restored: String, val word: String, val keptAsTyped: Boolean, val snapshot: Snapshot,
                       val expansion: Boolean = false, val spacing: Boolean = false)
    /**
     * [spaces] went in after [mark] when the letter after it was typed. [at] is where the mark ends. The letter was typed as
     * [typed] and went in as [put], with any capital. [corrected] is the fix of the word before the mark, if it had one.
     */
    private class Spaced(val at: Int, val mark: Char, val spaces: String, val typed: String, val put: String, val corrected: Undo?)
    /** The last letter a key typed, in [text], and what typing it changed. Holding the key can still replace it. */
    private class TypedKey(val text: String, val after: Snapshot, val automaticSpace: Snapshot?, val fullStop: FullStop?, val undo: Undo?)
    /** Whether autocorrect changed the text when a typed word ended, and which word to learn now. */
    private class Ended(val changed: Boolean, val learn: String?)
    private class Barrier(val identity: Editor.Identity, val navigation: Boolean = false, val command: Boolean = false,
                          val leaves: Boolean = false)

    private var field = Field.None
    private var composition: Composition = Composition.None
    private var epoch = 0
    private var selection = Selection(-1, -1)
    private var selectionConfirmed = false
    private val expectedSelections = ArrayDeque<Selection>()
    private val pendingInput = ArrayDeque<PendingInput>()
    private var recognising = false
    private var executingQueued = false
    private var draining = false
    private var drainScheduled = false
    private var drainTicket = 0
    private var barrier: Barrier? = null
    private var identity: Editor.Identity? = null
    private var recognitionSnapshot: Snapshot? = null
    private var completion: AtomicBoolean? = null
    private var completionSnapshot: Snapshot? = null
    private var editSnapshot: Snapshot? = null
    private var automaticSpace: Snapshot? = null
    private var fullStop: FullStop? = null
    /** The text just after a typed mark, whose next letter may bring a space. */
    private var typedMark: Snapshot? = null
    /** The spaces that went in after the last mark, while the word after them is typed. */
    private var spaced: Spaced? = null
    private val manualWord = TypedWords()
    private val keptWords = KeptWords { previous, next -> words.keptPair(previous, next) }
    private var correcting: Composition.Swiped? = null
    private var awaitingNextWord: Composition.Swiped? = null
    private var lookAlikeChangedAt = -1
    private val remembered = ArrayList<Remembered>()
    private var rememberedField: Pair<String?, Int>? = null
    private var undo: Undo? = null
    /** The text up to a mark that took the place of the space before it, so that a snippet or a shortcode can still start there. */
    private var markAfterSpace: String? = null
    private var typedKey: TypedKey? = null
    /** The last typed word to end, until the word after it has ended too. */
    private var endedWord: EndedWord? = null
    /** A typed word waiting for a swipe that may still continue, because the swiped word decides whether to change it. */
    private var endedBeforeSwipe: EndedWord? = null
    /** A word that Backspace put back, until the next word shows whether the user kept it. */
    private var restoredWord: String? = null
    /** A typed word that only the words after it can give its apostrophe ("its" for "it's"). It starts at [start] in the field. */
    private class ApostropheTarget(val word: String, var start: Int)
    /** Typed words waiting for the words after them, oldest first. */
    private val apostropheTargets = ArrayList<ApostropheTarget>()
    /** An unknown typed word. The next word can still change it, so it is learned after that. */
    private var heldWord: String? = null
    /** The typed word that was just learned or held, which ends at [end]. Deleting into it takes that back. */
    private class Learned(val word: String, val end: Int, val snapshot: Snapshot, val held: Boolean)
    private var lastLearned: Learned? = null
    /** The bar holds nothing, or words that came from no text, so new starts can go up without waiting for the models. */
    private var barIdle = true
    /** The words on the bar were for text in the field. Once the field is empty they are out of date. */
    private var barForText = false
    /** Words held on the strip to forget, in lower case. The bar leaves them out while their Undo shows. */
    private val hidden = mutableSetOf<String>()
    /** Which of the composition's alternatives each place on the bar shows, since hidden and blocked words leave gaps. */
    private var shownAlternatives = emptyList<Int>()

    var liftOffMillis = 0
    /** Two quick spaces type a full stop, and punctuation typed after a space moves in front of that space. */
    var autoPunctuation: () -> Boolean = { true }
    /** How many spaces go in after a typed . ? or ! once a letter follows it: 0, 1 or 2. */
    var sentenceSpaces: () -> Int = { 1 }
    /** A space goes in after a typed , ; or : once a letter follows it. */
    var commaSpace: () -> Boolean = { true }
    /**
     * Capital letter to start a sentence is on. A letter that brings its spaces after a full stop gets its capital here,
     * since the automatic Shift can't turn on before those spaces are in.
     */
    var sentenceCapitals: () -> Boolean = { false }
    /** Told the whole path behind each recognised word, including any pieces joined to it. */
    var onStroke: ((Swipe) -> Unit)? = null
    /** Told when a word changes by itself, unless that lands as a swipe ends, where a vibration would run into the swipe's own. */
    var onAutocorrect: (() -> Unit)? = null
    /**
     * Told how a typed word ended, from [typed] to [word], for the guide, which waits for a real fix: the same word twice when
     * nothing was fixed.
     */
    var onFixed: ((typed: String, word: String) -> Unit)? = null
    /** Told when an automatic change is undone and the typed word is back. */
    var onRestored: (() -> Unit)? = null
    /** Told when a :name: becomes its emoji, or a snippet's shortcut its text. */
    var onExpanded: (() -> Unit)? = null
    /**
     * A wrong word for the right one, when the next swipe should come out wrong on purpose, which only the interactive guide asks
     * for, or null. See [GuideHooks].
     */
    var nearMiss: (String) -> String? = { null }
    /** In the guide's first slide, any typed word becomes the guide's word, however far off it is. */
    var forceFix: () -> Boolean = { false }
    /**
     * The interactive guide's word for a word that starts after this text, or null outside the guide. A word that comes out
     * different gets the guide's word on the strip, so that one touch puts it right. See [GuideHooks].
     */
    var intended: (String) -> String? = { null }
    /** Told where the guide's word went among a swipe's choices, as they rank, so that the guide can point at it. */
    var guidePlaced: (place: Int, word: String) -> Unit = { _, _ -> }
    /** Whether a kind of fix is on, in Settings > What gets fixed. "i" uses it even before autocorrect's word data loads. */
    var fixOn: (FixKind) -> Boolean = { true }
    /** Only the keyboard sets this, so replaying a recording never adds to the raw input log. */
    var log: EditLog? = null
    /** Only the keyboard sets this, and only while the setting is on, so replaying a recording never changes a word. */
    var autocorrect: Autocorrect? = null
    /**
     * Set only while Fix typos is off and Spell out shorthand is on: its word data spells out shorthand and does nothing else, as
     * Spell out shorthand is its own setting (bug hunt #13).
     */
    var shorthandOnly: Autocorrect? = null
    /** Snippets and emoji shortcodes, or null when there are none. */
    var expansions: Expansions? = null
    /**
     * The text up to and including an "i" whose capital Backspace took back. That one "i" stays as typed, but the next one gets
     * its capital again: a chat app keeps one field for a whole conversation, so taking the capital back for good would take it
     * from every later "i" there (owner, 2026-11-12).
     */
    private var lowercaseI: String? = null

    val hasPendingInput get() = recognising || barrier != null || drainScheduled || pendingInput.isNotEmpty()
    /** Whether a touch arriving now could still join the last swipe. A recognition in progress may yet allow it. */
    val canContinueSwipe get() = liftOffMillis > 0 && (hasPendingInput || (composition as? Composition.Swiped)?.continuable == true)
    val awaitingDestination get() = barrier?.navigation == true
    /** Focus may be moving to another field that Android has not reported yet, so input now may belong to that field. */
    val mayLeaveField get() = barrier?.leaves == true || pendingInput.any { it is PendingEdit && it.leaves }

    fun start(field: Field, selectionStart: Int, selectionEnd: Int) {
        keptWords.settle()
        val next = editor.identity()
        val transition = barrier
        val carries = transition?.navigation == true && next != null
            && !next.packageName.isNullOrEmpty() && next.packageName == transition.identity.packageName
            && (next.connection !== transition.identity.connection || next.fieldId != transition.identity.fieldId)
        val carried = if (carries) pendingInput.takeWhile {
            it is PendingText || it is PendingEdit && it.transferable
        } else emptyList()
        this.field = field
        // A word forgotten in the field before is gone from the engine by now, or Settings has given it back.
        hidden.clear()
        selection = Selection(selectionStart, selectionEnd)
        selectionConfirmed = selectionStart >= 0 && selectionEnd >= 0
        reset(predicts = false)
        lookAlikeChangedAt = -1
        identity = next
        // Another field, or a password, starts with nothing remembered.
        val owner = next?.let { it.packageName to it.fieldId }
        if (owner != rememberedField) {
            (autocorrect ?: shorthandOnly)?.newField()
            lowercaseI = null
            // Words from the last field mean nothing here. A restart of this field keeps its words until new ones arrive.
            show(emptyList())
        }
        if (!field.learning || owner != rememberedField) remembered.clear()
        rememberedField = owner
        pendingInput.addAll(carried)
        recogniseNext()
        predict()
    }

    fun stop(terminal: Boolean = false) {
        keptWords.settle()
        if (terminal && sameIdentity(identity, editor.identity())) {
            val swiped = composition as? Composition.Swiped
            if (swiped?.chosen == true) leave()
            else if (swiped != null) leaveKept(swiped)
        }
        field = Field.None
        if (barrier?.navigation == true) {
            composition = Composition.None
            automaticSpace = null
            fullStop = null
            typedMark = null
            spaced = null
            editSnapshot = null
            manualWord.clear()
            correcting = null
            awaitingNextWord = null
            expectedSelections.clear()
            settleTypedWords()
            show(emptyList())
            return
        }
        reset(predicts = false)
        show(emptyList())
    }

    private fun invalidate(discardInput: Boolean = true) {
        epoch++
        undo = null
        completion?.set(true)
        completion = null
        completionSnapshot = null
        if (discardInput) {
            barrier = null
            drainTicket++
            drainScheduled = false
            pendingInput.clear()
            recognising = false
            recognitionSnapshot = null
        }
    }

    /** Starts over. The words on the bar stay until [predict] replaces them, so that the bar does not flash empty. */
    private fun reset(predicts: Boolean = true) {
        invalidate()
        typedKey = null
        composition = Composition.None
        automaticSpace = null
        fullStop = null
        typedMark = null
        spaced = null
        editSnapshot = null
        manualWord.clear()
        keptWords.clear()
        correcting = null
        awaitingNextWord = null
        expectedSelections.clear()
        settleTypedWords()
        apostropheTargets.clear()
        if (predicts) predict()
    }

    private fun closesWord(next: Char?) = next != null && (next.isWhitespace() || next in WORD_CLOSERS)

    private fun snapshot() = Snapshot(editor.before(), editor.after(), selection,
        selection.start >= 0 && selection.end >= 0 && selection.start != selection.end)

    private fun sameIdentity(first: Editor.Identity?, second: Editor.Identity?) = first != null && second != null
        && first.connection === second.connection && first.packageName == second.packageName && first.fieldId == second.fieldId

    /** Own selection notifications may be coalesced, or arrive after a later successful edit. */
    fun selectionChanged(start: Int, end: Int): Boolean {
        val waiting = barrier
        // Outgoing-field callbacks cannot resolve a navigation action; start() or its timeout does.
        if (waiting?.navigation == true) return false
        if (waiting?.command == true && sameIdentity(editor.identity(), waiting.identity)) {
            // The ordered state query acknowledges all effects of this app-handled command together.
            return false
        }
        val updated = Selection(start, end)
        val anchor = recognitionSnapshot ?: when (val current = composition) {
            is Composition.Swiped -> current.snapshot
            is Composition.Typed -> current.snapshot
            is Composition.Recalled -> current.snapshot
            is Composition.Starts -> current.snapshot
            is Composition.Expanding -> current.snapshot
            Composition.None -> completionSnapshot ?: automaticSpace ?: fullStop?.snapshot ?: editSnapshot
        }
        if (updated != selection && anchor != null && snapshot() == anchor) {
            confirmState(anchor, notifyExternal = true)
            return false
        }
        val ours = expectedSelections.indexOf(updated)
        if (ours >= 0 && updated == selection && anchor != null && snapshot() == anchor) {
            repeat(ours + 1) { expectedSelections.removeFirst() }
            selectionConfirmed = true
            return false
        }
        if (updated == selection && expectedSelections.isEmpty() && (anchor == null || snapshot() == anchor)) return false
        // An app empties its whole field when it sends a message: a last swiped word, still as swiped, went with it. The
        // cursor must be at the start with nothing selected, as selecting everything also leaves no text around it.
        if (start == 0 && end == 0 && editor.before()?.isEmpty() == true && editor.after()?.isEmpty() == true) sentWithField()
        selection = updated
        selectionConfirmed = start >= 0 && end >= 0
        // An app empties its field when it sends a message. Any other move may be the user going back to change a word.
        if (editor.before()?.isEmpty() == true) keptWords.settle()
        reset()
        return true
    }

    fun swipe(swipe: Swipe, startedAt: Long, endedAt: Long, metaState: Int) {
        if (!field.words) return
        if (!hasPendingInput) {
            invalidate()
            fullStop = null
        }
        if (enqueue(PendingSwipe(swipe, startedAt, endedAt, metaState))) recogniseNext()
    }

    private fun defer(transferable: Boolean = false, leaves: Boolean = false, action: () -> Unit): Boolean {
        if (executingQueued || !hasPendingInput) return false
        enqueue(PendingEdit(action, transferable, leaves))
        return true
    }

    private fun enqueue(input: PendingInput): Boolean {
        if (pendingInput.size >= MAX_PENDING_INPUT) {
            // Preserve accepted ordinary keys. Under overload, shed only a surplus unstarted swipe.
            val surplus = pendingInput.lastOrNull { it is PendingSwipe && it !== pendingInput.firstOrNull() }
            if (input is PendingSwipe || surplus == null) return false
            pendingInput.remove(surplus)
        }
        pendingInput += input
        return true
    }

    private fun enqueueText(text: String, letterTap: Boolean) {
        val units = pendingInput.sumOf { if (it is PendingText) it.text.length else 0 }
        if (units + text.length > MAX_PENDING_TEXT) return
        val last = pendingInput.lastOrNull() as? PendingText
        if (!letterTap && last != null && !last.letterTap && last.text.length + text.length <= MAX_TEXT_CHUNK) {
            last.text += text
        } else enqueue(PendingText(text, letterTap))
    }

    private fun scheduleDrain() {
        if (drainScheduled) return
        drainScheduled = true
        val ticket = ++drainTicket
        later(0) {
            if (ticket != drainTicket) return@later
            drainScheduled = false
            recogniseNext()
        }
    }

    /** Resolve real swipes in order, using the context produced by the preceding accepted edit. */
    private fun recogniseNext() {
        if (recognising || barrier != null || draining || drainScheduled) return
        draining = true
        val started = System.nanoTime()
        try {
            var edits = 0
            while (pendingInput.isNotEmpty() && pendingInput.first() !is PendingSwipe) {
                if (edits++ == DRAIN_BUDGET || System.nanoTime() - started >= DRAIN_NANOS) {
                    scheduleDrain()
                    return
                }
                val pending = pendingInput.removeFirst()
                executingQueued = true
                try {
                    when (pending) {
                        is PendingEdit -> pending.action()
                        is PendingText -> type(pending.text, pending.letterTap)
                        is PendingSwipe -> Unit
                    }
                } finally {
                    executingQueued = false
                }
                if (barrier != null || recognising) return
            }
        } finally {
            draining = false
        }
        val pending = pendingInput.firstOrNull() as? PendingSwipe ?: return
        if (!selectionConfirmed) {
            confirmState(editSnapshot.takeIf { correcting != null })
            return
        }
        fullStop = null
        val anchor = snapshot()
        val before = anchor.before
        if (!anchor.available || !anchor.knownSelection || before == null) {
            reset()
            return
        }
        val correction = correcting
        settleRestoredWord(anchor)
        val typedBefore = if (correction == null) typedWordAt(anchor) else null
        val completed = if (correction != null || startsNewWord(before)) completeManualWord(anchor) else null
        correcting = null
        manualWord.clear()
        if (typedBefore != null) endedWord = EndedWord(typedBefore, null)
        val previous = composition as? Composition.Swiped
        val gap = if (previous != null) pending.startedAt - previous.endedAt else -1L
        val joinable = previous != null && previous.continuable && liftOffMillis > 0
            && anchor == previous.snapshot && before.endsWith(previous.inserted) && !anchor.selected
        val sameStroke = joinable && previous!!.swipe.sameStroke(pending.swipe, gap, liftOffMillis.toLong())
        val joined = if (joinable && (sameStroke || gap in 0..JOIN_CHECK_MILLIS)) appended(previous!!.swipe, pending.swipe, gap) else null

        /** Reads [path], or uses the reading already made of it, and types the result. */
        fun read(resumes: Boolean, path: Swipe, known: Words.Recognition? = null) {
            if (!resumes) {
                leave()
                awaitingNextWord = null
                holdForNextWord(previous, anchor)
                endedBeforeSwipe = null
            }
            composition = Composition.None
            val resumed = previous.takeIf { resumes }
            val context = resumed?.context ?: before
            val replaced = resumed?.inserted?.length ?: 0
            val leadingSpace = resumed?.leadingSpace ?: if (anchor.selected) "" else wordSpace(context)
            // Mid-sentence the next character may already be a space or punctuation, so another space would double up.
            val trailingSpace = resumed?.trailingSpace ?: (!anchor.selected && !closesWord(anchor.after?.firstOrNull()))
            val current = epoch
            recognising = true
            recognitionSnapshot = anchor
            val finish: (Words.Recognition) -> Unit = finish@{ recognition ->
                if (current != epoch || pendingInput.firstOrNull() !== pending) return@finish
                if (snapshot() != anchor) {
                    reset()
                    return@finish
                }
                recognising = false
                recognitionSnapshot = null
                pendingInput.removeFirst()
                Recording.sink?.recognised(recognition.words, resumes)
                KeyboardEvents.publish { KeyboardEvent.Recognised(recognition.words) }
                log?.recognised(recognition, resumes)
                onStroke?.invoke(path)
                if (recognition.words.isNotEmpty()) {
                    val sentenceCase = field.sentenceCapitalisation && autoCapitalise()
                    val guessed = recognition.words.map {
                        SwipeTyping.capitalise(it, context + leadingSpace, pending.metaState, false, sentenceCase)
                    }
                    val wanted = intended(context + leadingSpace)
                        ?.let { SwipeTyping.capitalise(it, context + leadingSpace, pending.metaState, false, sentenceCase) }
                    val choices = guideChoices(guessed, wanted)
                    val word = choices.first()
                    val inserted = leadingSpace + word + if (trailingSpace) " " else ""
                    if (!edit(EditCause.Swipe, replaced, inserted)) return@finish
                    val accepted = snapshot()
                    automaticSpace = accepted.takeIf { trailingSpace }
                    val swiped = Composition.Swiped(word, leadingSpace, trailingSpace, path, context, recognition, pending.endedAt,
                        true, accepted, false, choices)
                    composition = swiped
                    remember(swiped)
                    confirmState(accepted) { confirmed ->
                        if (confirmed) {
                            // The swiped word can still change the typed word before it.
                            val reviewer = corrector().takeIf { typedBefore != null }
                            if (reviewer == null || !hold(completed, reviewer)) learnWord(completed, correction)
                            show(choices)
                            // A swiped word shows its own choices, so no next-word prediction comes along to read the text.
                            // The look-alike check comes after this because it can change the text, and that must be the last read.
                            readKeptWords(accepted)
                            checkLookAlike()
                            words.prepare(context + inserted, AtomicBoolean(false))
                            reviewBeforeSwipe(swiped)
                        }
                    }
                    return@finish
                }
                learnWord(completed, correction)
                recogniseNext()
                predict()
            }
            if (known != null) finish(known) else words.recognise(path, context, field.learning, finish)
        }

        val firstMargin = previous?.recognition?.margin
        when {
            joined != null && sameStroke -> read(true, joined)
            joined != null && firstMargin != null -> {
                // A touch this soon can be a lost finger coming back or the next word. Only a clearer reading as one word can tell.
                val current = epoch
                recognising = true
                recognitionSnapshot = anchor
                words.recognise(joined, previous!!.context, field.learning) { reading ->
                    if (current != epoch || pendingInput.firstOrNull() !== pending) return@recognise
                    if (snapshot() != anchor) {
                        reset()
                        return@recognise
                    }
                    val margin = reading.margin
                    if (reading.words.isNotEmpty() && margin != null && margin > firstMargin) read(true, joined, reading)
                    else read(false, pending.swipe)
                }
            }
            else -> read(false, pending.swipe)
        }
    }

    private fun appended(first: Swipe, next: Swipe, gapMillis: Long) = try {
        first.append(next, gapMillis)
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Keys wait for pending swipes, retaining the order in which the user entered them. */
    fun type(text: String, letterTap: Boolean = false) {
        // A light touch types a word, as a swipe does, so it types nothing where words are off.
        if (text.isEmpty() || letterTap && !field.words) return
        if (!executingQueued && hasPendingInput) {
            enqueueText(text, letterTap)
            return
        }
        val corrected = undo
        val spaceBefore = automaticSpace
        val fullStopBefore = fullStop
        leave()
        invalidate(false)
        val typedSpace = fullStop?.snapshot
        fullStop = null
        var anchor = snapshot()
        if (!letterTap && text == ":" && expandShortcode(anchor)) return
        val letter = !letterTap && text.codePointCount(0, text.length) == 1 && Character.isLetterOrDigit(text.codePointAt(0))
        if (!letter) {
            if (!unspaceAddress(text)) return
            anchor = snapshot()
        }
        val spaces = if (letter) spacesAfterMark(text, anchor) else ""
        holdForNextWord(composition as? Composition.Swiped, anchor)
        val correction = correcting
        if (correction != null && anchor != editSnapshot) {
            correcting = null
            manualWord.clear()
        }
        // A full stop inside an address or file name ended no sentence, so the word before it goes back as typed.
        if (spaces.isEmpty() && corrected != null && anchor == corrected.snapshot && !letterTap
            && continuesAddress(corrected.replaced, text)) {
            if (!edit(EditCause.AutocorrectUndo, corrected.replaced.length, corrected.restored)) return
            anchor = snapshot()
            manualWord.clear()
            endedWord = null
        }
        if (letterTap) settleRestoredWord(anchor)
        val ending = if (!letterTap && text.first() in TYPED_WORD_ENDS) endingWord(anchor, correction) else null
        val completesCorrection = correcting != null && !letterTap && text.first() in ".,!?;:)]}…"
        val finishesWord = letterTap && (correction != null || startsNewWord(anchor.before.orEmpty()))
        val completed = if (completesCorrection || finishesWord) completeManualWord(anchor) else null
        if (completesCorrection || letterTap) correcting = null
        val fix = ending?.let { findFix(it, anchor) }
        if (fix != null) {
            if (!edit(EditCause.Autocorrect, fix.typed.length, fix.word)) return
            anchor = snapshot()
            manualWord.clear()
        }
        // Like a swiped word's space, a space typed after a word moves behind the punctuation.
        val dropsSpace = (automaticSpace == anchor || typedSpace == anchor && autoPunctuation()) && anchor.before?.endsWith(" ") == true
            && text[0] in ".,!?;:)]}…"
        val before = anchor.before
        val prefix = if (letterTap && !anchor.selected) wordSpace(before.orEmpty()) else spaces
        val capitals = autoCapitalise() || spaces.isNotEmpty() && sentenceCapitals()
        val sentenceCased = if (before != null && field.sentenceCapitalisation && capitals) {
            SwipeTyping.capitalise(text, before + prefix)
        } else text
        // A tapped "i" is a word, so it gets its capital as a typed one does.
        val cased = if (letterTap) capitalI(sentenceCased, before.orEmpty() + prefix)?.word ?: sentenceCased else sentenceCased
        val typed = if (letterTap) "$prefix$cased " else cased
        val cause = if (dropsSpace) EditCause.DropSpace else if (letterTap) EditCause.Tap else EditCause.Key
        if (spaces.isNotEmpty() && !edit(EditCause.MarkSpace, 0, spaces)) return
        if (!edit(cause, if (dropsSpace) 1 else 0, typed)) return
        if (dropsSpace) markAfterSpace = editor.before()
        automaticSpace = if (letterTap) snapshot() else null
        // Holding the key takes the spaces out with the letter, so its symbol lands straight after the mark.
        typedKey = if (letterTap) null else TypedKey(spaces + typed.substring(typed.offsetByCodePoints(typed.length, -1)), snapshot(),
            spaceBefore, fullStopBefore, corrected)
        typedMark = if (!letterTap && text.length == 1 && text[0] in SPACED_MARKS) snapshot() else null
        if (spaces.isNotEmpty()) {
            val accepted = snapshot()
            spaced = Spaced(accepted.selection.end - typed.length - spaces.length, before.orEmpty().last(), spaces, text, typed,
                corrected)
            undo = Undo(spaces + typed, text, "", false, accepted, spacing = true)
        }
        composition = Composition.None
        if (letterTap) manualWord.clear()
        else if (!completesCorrection) observe(text)
        val ended = if (ending != null) wordEnded(ending, fix, typed, null) ?: return else null
        if (!completed.isNullOrEmpty() || ended?.changed == true) {
            confirmState(snapshot()) { confirmed -> if (confirmed) learnWord(completed, correction) }
            return
        }
        predictLater()
    }

    /**
     * The key that typed the last letter stayed down, so [symbol] takes the letter's place, as if the symbol had been
     * typed instead. If the letter is no longer the last thing in the text, it stays as it is.
     */
    fun replaceLetter(symbol: String) {
        if (defer { replaceLetter(symbol) }) return
        val letter = typedKey ?: return
        typedKey = null
        val anchor = snapshot()
        if (anchor != letter.after || !anchor.available || anchor.before?.endsWith(letter.text) != true) return
        manualWord.edit("\b", null, false)
        if (!edit(EditCause.Backspace, letter.text.length, "")) return
        automaticSpace = letter.automaticSpace
        fullStop = letter.fullStop
        undo = letter.undo
        type(symbol)
        typedKey = null
    }

    fun symbol(symbol: String) {
        if (defer { symbol(symbol) }) return
        leave()
        invalidate(false)
        fullStop = null
        if (symbol == ":" && expandShortcode(snapshot())) return
        if (!unspaceAddress(symbol)) return
        val anchor = snapshot()
        val ending = if (symbol.firstOrNull()?.let { it in TYPED_WORD_ENDS } == true) endingWord(anchor, correcting) else null
        val fix = ending?.let { findFix(it, anchor) }
        composition = Composition.None
        manualWord.clear()
        correcting = null
        awaitingNextWord = null
        if (fix != null && !edit(EditCause.Autocorrect, fix.typed.length, fix.word)) return
        val before = editor.before().orEmpty()
        val after = editor.after().orEmpty()
        // Spaces around a symbol are for prose. A password or an address gets the symbol alone.
        val around = field.words && (symbol == "&" || symbol == "=")
        val trailing = field.words && (around || symbol == ";" || symbol == "…" || symbol == "•" && (before.isEmpty() || before.endsWith("\n")))
        val leading = around && before.isNotEmpty() && !Character.isWhitespace(before.codePointBefore(before.length))
        val reuseSpace = trailing && after.startsWith(" ")
        val space = reuseSpace || trailing && (after.isEmpty() || !after[0].isWhitespace())
        val text = (if (leading) " " else "") + symbol + if (space) " " else ""
        if (!edit(EditCause.Symbol, 0, text, if (reuseSpace) 1 else 0)) return
        automaticSpace = null
        typedMark = if (text.length == 1 && text[0] in SPACED_MARKS) snapshot() else null
        val ended = wordEnded(ending, fix, text, null) ?: return
        if (ended.changed) {
            confirmState(snapshot())
            return
        }
        predictLater()
    }

    /** Puts in [text] just as it is, as a paste does: no fix of the word before, and no space added. */
    fun paste(text: String) {
        if (defer { paste(text) }) return
        leave()
        invalidate(false)
        fullStop = null
        composition = Composition.None
        manualWord.clear()
        correcting = null
        awaitingNextWord = null
        if (!edit(EditCause.Paste, 0, text)) return
        automaticSpace = null
        spaced = null
        predictLater()
    }

    fun space(repeated: Boolean, time: Long) {
        if (defer(transferable = true) { space(repeated, time) }) return
        leave()
        invalidate(false)
        if (!repeated && expandSnippet(snapshot())) return
        if (!unspaceAddress(" ")) return
        val anchor = snapshot()
        holdForNextWord(composition as? Composition.Swiped, anchor)
        composition = Composition.None
        val before = anchor.before.orEmpty()
        val correction = correcting
        val ending = endingWord(anchor, correction)
        val completed = completeManualWord(anchor)
        correcting = null
        val fix = ending?.let { findFix(it, anchor) }
        if (fix != null && !edit(EditCause.Autocorrect, fix.typed.length, fix.word)) return
        val pending = fullStop
        fullStop = null
        when {
            !field.fullStops || anchor.selected -> if (!edit(EditCause.Space, 0, " ")) return
            automaticSpace == anchor && before.endsWith(" ") -> {
                if (!repeated && endsWord(before.dropLast(1))) fullStop = FullStop(time, anchor)
            }
            !repeated && pending != null && time - pending.time in 0..FULL_STOP_MILLIS && autoPunctuation()
                && anchor.available && anchor == pending.snapshot -> {
                // Two spaces after a sentence give this full stop two spaces as well.
                val spaces = if (field.markSpaces && sentenceSpaces() == 2) "  " else " "
                if (!edit(EditCause.FullStop, 1, ".$spaces")) return
            }
            else -> {
                if (!edit(EditCause.Space, 0, " ")) return
                // Read the bounded editor window after insertion; appending would exceed its limit.
                if (!repeated && endsWord(before)) fullStop = FullStop(time, snapshot())
            }
        }
        automaticSpace = null
        val ended = wordEnded(ending, fix, " ", completed) ?: return
        val learned = ended.learn
        if (ended.changed || field.learning && !learned.isNullOrEmpty()) {
            confirmState(snapshot()) { confirmed -> if (confirmed) learnWord(learned, correction) }
            return
        }
        predictLater()
    }

    fun backspace(word: Boolean) {
        if (defer { backspace(word) }) return
        if (!sameIdentity(identity, editor.identity())) {
            reset()
            return
        }
        if (!word && undoCorrection()) return
        // The words after a waiting word are being changed, so it isn't looked at again.
        apostropheTargets.clear()
        val typed = lastLearned?.takeIf { snapshot() == it.snapshot }
        lastLearned = null
        // Deleting or cancelling a swipe is not evidence that the word was kept.
        val source = if (word || !field.learning) null else correcting ?: composition as? Composition.Swiped
        val expected = if (correcting != null) editSnapshot else source?.snapshot
        val anchor = source?.let { snapshot().takeIf { it == expected && !it.selected } }
        invalidate(false)
        composition = Composition.None
        automaticSpace = null
        fullStop = null
        awaitingNextWord = null
        if (word || correcting != null || source != null) manualWord.clear()
        else manualWord.edit("\b", null, false)
        correcting = null
        val previous = selection
        val removed = editor.delete(word, previous.start, previous.end)
        log?.edited(if (word) EditCause.BackspaceWord else EditCause.Backspace, removed ?: -1, "", 0, removed != null)
        if (removed != null) {
            if (previous.start >= 0 && previous.end >= 0) {
                val start = minOf(previous.start, previous.end)
                val end = maxOf(previous.start, previous.end)
                shiftRemembered(start - removed, end, -(end - start + removed))
            } else remembered.clear()
            expectSelection(previous, removed, 0)
            editSnapshot = snapshot()
            if (source != null && anchor != null && removed > 0) trackCorrection(source, anchor, removed)
            typed?.let(::forgetIfDeleted)
        } else {
            // The app may have delegated or partly applied deletion. Do not replay dependent input.
            reset()
            return
        }
        // Keep the old words until the new ones arrive, so the bar does not flash empty for each deleted letter.
        predictLater()
    }

    private fun trackCorrection(source: Composition.Swiped, beforeDelete: Snapshot, removed: Int) {
        val afterDelete = editSnapshot ?: return
        val before = beforeDelete.before ?: return
        val actual = afterDelete.before ?: return
        val start = source.snapshot.selection.end - source.tail.length
        val remaining = afterDelete.selection.end - start
        if (remaining !in 1..actual.length || removed > before.length || beforeDelete.after != afterDelete.after) return
        val expected = before.dropLast(removed)
        if (if (before.length < Editor.CONTEXT_LENGTH) actual != expected else !actual.endsWith(expected)) return
        val prefix = actual.takeLast(remaining)
        if (prefix.length != TypedPredictions.prefix(prefix).length) return
        manualWord.edit(prefix, null, false)
        correcting = source
    }

    fun enter(press: EnterKey.Press) {
        if (defer(leaves = true) { enter(press) }) return
        leave()
        invalidate(false)
        composition = Composition.None
        fullStop = null
        awaitingNextWord = null
        if (!unspaceAddress("\n")) return
        val anchor = snapshot()
        val correction = correcting
        val ending = endingWord(anchor, correction)
        val completed = completeManualWord(anchor)
        correcting = null
        // "i" gets its capital before the Enter, which may send it: a chat app sends "I", not "i".
        val capital = ending?.let { word -> anchor.before?.let { capitalI(word, it.dropLast(word.length)) } }
            ?.takeIf { anchor.before?.endsWith(it.typed) == true && allowedFix(it) }
        if (capital != null && !edit(EditCause.Autocorrect, capital.typed.length, capital.word)) return
        if (capital == null && automaticSpace == anchor && anchor.before?.endsWith(" ") == true && !edit(EditCause.DropSpace, 1, "")) return
        automaticSpace = null
        val owner = editor.identity()
        if (!sameIdentity(identity, owner)) {
            reset()
            return
        }
        val current = epoch
        val result = editor.enter(press)
        if (current != epoch || !sameIdentity(owner, editor.identity())) return
        when (result) {
            Editor.Enter.Newline -> {
                // Only a new line stays in this field. An app may act on any other Enter before a fix could land.
                val fix = capital ?: ending?.let { findFix(it, anchor) }
                if (fix != null && capital == null && !edit(EditCause.Autocorrect, fix.typed.length, fix.word)) return
                if (!edit(EditCause.Newline, 0, "\n")) return
                val ended = wordEnded(ending, fix, "\n", completed) ?: return
                val learned = ended.learn
                if (ended.changed || !learned.isNullOrEmpty()) {
                    confirmState(snapshot()) { confirmed -> if (confirmed) learnWord(learned, correction) }
                    return
                }
            }
            Editor.Enter.Next, Editor.Enter.Previous -> {
                learnWord(completed, correction)
                if (mayLearnFrom(anchor)) keptWords.sent(anchor.before, anchor.selection.end)
                waitForDestination(owner, navigation = true)
            }
            Editor.Enter.Action -> {
                learnWord(completed, correction)
                if (mayLearnFrom(anchor)) keptWords.sent(anchor.before, anchor.selection.end)
                waitForDestination(owner, navigation = false)
            }
            Editor.Enter.Rejected -> reset()
        }
        predictLater()
    }

    private fun waitForDestination(owner: Editor.Identity?, navigation: Boolean) {
        if (owner == null || !sameIdentity(identity, owner)) {
            reset()
            return
        }
        val waiting = Barrier(owner, navigation, leaves = true)
        barrier = waiting
        if (!navigation) pendingInput.clear()
        later(DESTINATION_TIMEOUT_MILLIS) {
            if (barrier === waiting) reset()
        }
    }

    /** App-handled keys share the edit queue and fence dependent input with an ordered app query. */
    fun command(destructive: Boolean, fieldLocal: Boolean, action: () -> Boolean) {
        if (defer(leaves = !fieldLocal) { command(destructive, fieldLocal, action) }) return
        if (!destructive) leave()
        invalidate(false)
        composition = Composition.None
        automaticSpace = null
        fullStop = null
        manualWord.clear()
        // A paste or a cut is the app's change, so what the text says afterwards is not what the user typed.
        keptWords.clear()
        correcting = null
        awaitingNextWord = null
        expectedSelections.clear()
        val owner = editor.identity()
        if (owner == null || !sameIdentity(identity, owner)) {
            reset()
            return
        }
        val waiting = Barrier(owner, command = true, leaves = !fieldLocal)
        barrier = waiting
        // The app makes this change itself, so all the log can say is what caused the selection lines that follow.
        if (destructive) log?.edited(EditCause.Command, 0, "", 0, true)
        if (!action()) {
            reset()
            return
        }
        if (barrier !== waiting) return
        if (!fieldLocal) {
            pendingInput.clear()
            later(DESTINATION_TIMEOUT_MILLIS) { if (barrier === waiting) reset() }
            return
        }
        barrier = null
        confirmState()
    }

    /** [predictNow] skips the wait that batches fast typing, for an edit such as a tapped word that ends a burst. */
    private fun confirmState(expected: Snapshot? = null, notifyExternal: Boolean = false, predictNow: Boolean = false,
                             result: (Boolean) -> Unit = {}) {
        val owner = editor.identity()
        if (owner == null || !sameIdentity(identity, owner)) {
            reset()
            result(false)
            return
        }
        val waiting = Barrier(owner, command = true)
        val current = epoch
        barrier = waiting
        editor.state { state ->
            if (barrier !== waiting || current != epoch) return@state
            barrier = null
            if (state == null || !sameIdentity(owner, editor.identity())) {
                selection = Selection(-1, -1)
                selectionConfirmed = false
                reset()
                if (notifyExternal) externalChange()
                result(false)
                return@state
            }
            selection = Selection(state.selectionStart, state.selectionEnd)
            selectionConfirmed = true
            val actual = Snapshot(state.before, state.after, selection, selection.start != selection.end)
            if (expected != null && actual != expected) {
                reset()
                if (notifyExternal) externalChange()
                result(false)
                return@state
            }
            editSnapshot = actual
            expectedSelections.clear()
            result(true)
            recogniseNext()
            if (predictNow) predict() else predictLater()
        }
    }

    /** Puts in the word at place [shown] on the bar. */
    fun choose(shown: Int) {
        chooseAlternative(shownAlternatives.getOrNull(shown) ?: return)
    }

    /** The word at place [shown] on the bar, and the typed letters it would replace, which are empty when there are none. */
    fun shownWord(shown: Int): Pair<String, String>? {
        val word = composition.alternatives.getOrNull(shownAlternatives.getOrNull(shown) ?: return null) ?: return null
        val typed = (composition as? Composition.Typed)?.prefix.orEmpty()
        return word to typed
    }

    /** Leaves [word] off the bar until [unhide], while it waits to be forgotten. */
    fun hide(word: String) {
        if (hidden.add(word.lowercase())) show(composition.alternatives)
    }

    fun unhide(word: String) {
        if (hidden.remove(word.lowercase())) show(composition.alternatives)
    }

    private fun chooseAlternative(index: Int) {
        val chosen = composition.alternatives.getOrNull(index) ?: return
        (composition as? Composition.Expanding)?.let { offered ->
            if (defer { if (composition === offered) choose(index) }) return
            val anchor = snapshot()
            val expansion = offered.choices[index].expansion
            if (anchor != offered.snapshot || anchor.selected || anchor.before?.endsWith(expansion.code) != true) return
            log?.picked(index, chosen, offered.alternatives)
            invalidate(false)
            expand(expansion, predictNow = true)
            return
        }
        if (!words.allowed(chosen) || chosen.lowercase() in hidden) return
        val offered = composition
        if (defer {
            val current = composition
            if (current === offered || current is Composition.Swiped && offered is Composition.Swiped
                && current.recognition === offered.recognition) chooseAlternative(index)
        }) return
        log?.picked(index, chosen, offered.alternatives)
        KeyboardEvents.publish { KeyboardEvent.Chosen(chosen) }
        val anchor = snapshot()
        if (offered is Composition.Starts) {
            chooseStart(offered, chosen, anchor)
            return
        }
        if (!anchor.available || !anchor.knownSelection) return
        val before = anchor.before ?: return
        when (val current = composition) {
            is Composition.Swiped -> {
                if (anchor != current.snapshot || !before.endsWith(current.inserted) || anchor.selected) return
                invalidate(false)
                val tail = chosen + if (current.trailingSpace) " " else ""
                if (!edit(EditCause.Choose, current.tail.length, tail)) return
                val accepted = snapshot()
                val swiped = current.copy(word = chosen, continuable = false, snapshot = accepted, chosen = true)
                composition = swiped
                remember(swiped)
                automaticSpace = accepted.takeIf { current.trailingSpace }
                // A chosen word is settled, so the strip moves straight on to what comes next.
                confirmState(accepted, predictNow = true) { confirmed ->
                    if (!confirmed || composition !== swiped) return@confirmState
                    leave()
                    composition = Composition.None
                }
            }
            is Composition.Typed -> {
                val tail = current.prefix + if (current.trailingSpace) " " else ""
                // A starter word replaces a selection. Any other word would leave it behind.
                if (anchor != current.snapshot || !before.endsWith(tail) || anchor.selected && tail.isNotEmpty()) return
                invalidate(false)
                val space = if (current.prefix.isEmpty()) wordSpace(before) else ""
                // A tapped word is whole, so it gets the capitals step that a typed word gets at its end ("WhatsApp").
                val word = corrector()?.capitals(chosen, before.dropLast(tail.length) + space) ?: chosen
                if (!edit(EditCause.Choose, tail.length, "$space$word ")) return
                composition = Composition.None
                automaticSpace = snapshot()
                val correction = correcting?.takeIf { anchor == editSnapshot }
                manualWord.clear()
                correcting = null
                // Keep the old words until the next words arrive, so the bar does not flash empty.
                confirmState(snapshot(), predictNow = true) { confirmed ->
                    if (confirmed) learnWord(chosen, correction, rememberCase = false)
                }
            }
            is Composition.Recalled -> {
                if (anchor != current.snapshot || anchor.selected) return
                invalidate(false)
                val start = current.word.start
                if (!edit(EditCause.Choose, current.before, chosen, current.after)) return
                val word = current.word.copy(start = start, word = chosen)
                add(word)
                manualWord.clear()
                correcting = null
                val accepted = snapshot()
                composition = current.copy(word = word, before = chosen.length, after = 0, snapshot = accepted)
                // Moving the cursor away is how most people leave this word, so learn the choice now.
                confirmState(accepted) { confirmed ->
                    if (confirmed && field.learning && chosen != current.word.word)
                        words.chosen(word.swipe, word.context, word.recognition, chosen)
                }
            }
            is Composition.Starts, is Composition.Expanding, Composition.None -> Unit
        }
    }

    /**
     * A start goes in as a swiped word does: after whatever is there, or in place of a selection. It has no tie to the
     * letters before the cursor, so it replaces none of them, and the tap teaches nothing about those letters or any swipe.
     */
    private fun chooseStart(offered: Composition.Starts, chosen: String, anchor: Snapshot) {
        if (anchor != offered.snapshot) return
        invalidate(false)
        val space = if (anchor.selected) "" else wordSpace(anchor.before.orEmpty())
        val trailingSpace = !closesWord(anchor.after?.firstOrNull())
        if (!edit(EditCause.Choose, 0, space + chosen + if (trailingSpace) " " else "")) return
        composition = Composition.None
        automaticSpace = snapshot().takeIf { trailingSpace }
        manualWord.clear()
        correcting = null
        confirmState(snapshot(), predictNow = true)
    }

    private fun remember(swiped: Composition.Swiped) {
        val end = swiped.snapshot.selection.end
        if (!field.learning || end < 0 || swiped.snapshot.selected) return
        add(Remembered(end - swiped.tail.length, swiped.word, swiped.alternatives, swiped.swipe, swiped.context, swiped.recognition))
    }

    private fun add(word: Remembered) {
        remembered.removeAll { it.start < word.end && word.start < it.end }
        remembered += word
        if (remembered.size > MAX_REMEMBERED) remembered.removeAt(0)
    }

    /** Moves remembered words after an edit that replaced [start, end), and forgets the words it touched. */
    private fun shiftRemembered(start: Int, end: Int, change: Int) {
        if (remembered.isEmpty() || change == 0 && start == end) return
        val iterator = remembered.listIterator()
        while (iterator.hasNext()) {
            val word = iterator.next()
            when {
                word.end <= start -> Unit
                word.start >= end -> iterator.set(word.copy(start = word.start + change))
                else -> iterator.remove()
            }
        }
    }

    /** Shows the suggestions again when the cursor returns to a swiped word that is unchanged. */
    private fun recall(anchor: Snapshot): Boolean {
        val before = anchor.before ?: return false
        val after = anchor.after ?: return false
        if (before.isEmpty() && after.isEmpty()) {
            remembered.clear()
            return false
        }
        if (remembered.isEmpty()) return false
        val prefix = TypedPredictions.prefix(before)
        val suffix = after.takeWhile { it.isLetter() || it == '\'' || it == '’' }
        if (prefix.isEmpty() && suffix.isEmpty()) return false
        val start = anchor.selection.start - prefix.length
        val word = remembered.lastOrNull { it.start == start && it.word == prefix + suffix } ?: return false
        composition = Composition.Recalled(word, prefix.length, suffix.length, anchor)
        show(word.alternatives)
        return true
    }

    /** Only a swiped word the user has not chosen themselves can change once the next word arrives. */
    private fun holdForNextWord(swiped: Composition.Swiped?, anchor: Snapshot) {
        if (swiped == null) return
        val held = !swiped.chosen && swiped.trailingSpace && anchor == swiped.snapshot && !anchor.selected
            && anchor.knownSelection && anchor.before?.endsWith(swiped.inserted) == true
        awaitingNextWord = swiped.takeIf { held }
    }

    /** Once the next word is finished, its fit decides between look-alikes such as "we're" and "were". */
    private fun checkLookAlike() {
        val earlier = awaitingNextWord ?: return
        val anchor = snapshot()
        val before = anchor.before
        val followed = anchor.selection.end - earlier.snapshot.selection.end
        if (followed == 0 && !anchor.selected) return
        if (before == null || anchor.selected || followed !in 1..before.length) {
            awaitingNextWord = null
            return
        }
        val following = before.takeLast(followed)
        val next = following.trimEnd(' ', '.', ',', '!', '?', ';', ':')
        if (next == following) {
            if (TypedPredictions.prefix(next) != next) awaitingNextWord = null
            return
        }
        awaitingNextWord = null
        val start = earlier.snapshot.selection.end - earlier.tail.length
        if (next.isEmpty() || TypedPredictions.prefix(next) != next || start == lookAlikeChangedAt
            || !before.dropLast(followed).endsWith(earlier.inserted)) return
        val current = epoch
        words.lookAlike(earlier.context, earlier.word, next) { replacement ->
            if (replacement != null && current == epoch && !hasPendingInput && snapshot() == anchor) {
                replaceLookAlike(earlier, cased(replacement, earlier), following, start, anchor)
            }
        }
    }

    private fun cased(replacement: String, original: Composition.Swiped): String {
        val word = original.word
        if (word.length > 2 && word == word.uppercase() && word != word.lowercase()) return replacement.uppercase()
        val shifted = word.first().isUpperCase() && !word.lowercase().startsWith("i'")
        val sentenceCase = field.sentenceCapitalisation && autoCapitalise()
        return SwipeTyping.capitalise(replacement, original.context, 0, shifted, sentenceCase)
    }

    private fun replaceLookAlike(earlier: Composition.Swiped, replacement: String, following: String, start: Int, anchor: Snapshot) {
        val showing = composition
        invalidate(false)
        val previous = remembered.lastOrNull { it.start == start && it.word == earlier.word }
        if (!edit(EditCause.LookAlike, earlier.tail.length + following.length, "$replacement $following")) return
        previous?.let { add(it.copy(word = replacement)) }
        lookAlikeChangedAt = start
        val accepted = snapshot()
        readKeptWords(accepted)
        if (automaticSpace == anchor) automaticSpace = accepted
        if (typedMark == anchor) typedMark = accepted
        fullStop = fullStop?.let { if (it.snapshot == anchor) FullStop(it.time, accepted) else it }
        if (showing is Composition.Swiped) {
            val context = accepted.before?.removeSuffix(showing.inserted) ?: showing.context
            val swiped = showing.copy(context = context, snapshot = accepted)
            composition = swiped
            remember(swiped)
        } else {
            // A swiped word shows only while its swipe has just ended, so this change came from typing.
            onAutocorrect?.invoke()
            composition = Composition.None
        }
        confirmState(accepted)
    }

    private fun edit(cause: EditCause, count: Int, text: String, after: Int = 0): Boolean {
        if (!sameIdentity(identity, editor.identity())) {
            reset()
            return false
        }
        val previous = selection
        val anchor = snapshot()
        val current = epoch
        val owner = identity
        val accepted = editor.replace(count, text, after)
        log?.edited(cause, count, text, after, accepted)
        if (current != epoch || !sameIdentity(owner, editor.identity())) return false
        if (!accepted) {
            // A deletion may have succeeded before commitText failed. Never replay the transaction.
            remembered.clear()
            reset()
            return false
        }
        val actualBefore = editor.before()
        val actualAfter = editor.after()
        if (current != epoch || !sameIdentity(owner, editor.identity())) return false
        val requestedBefore = anchor.before?.let { (it.dropLast(count) + text).takeLast(Editor.CONTEXT_LENGTH) }
        val requestedAfter = anchor.after?.drop(after)
        val beforeMatches = requestedBefore == null || actualBefore == null ||
            if ((anchor.before?.length ?: 0) < Editor.CONTEXT_LENGTH || requestedBefore.length == Editor.CONTEXT_LENGTH)
                actualBefore == requestedBefore else actualBefore.endsWith(requestedBefore)
        val afterMatches = requestedAfter == null || actualAfter == null ||
            if ((anchor.after?.length ?: 0) < AFTER_CONTEXT_LENGTH) actualAfter == requestedAfter
            else actualAfter.startsWith(requestedAfter)
        if (!beforeMatches || !afterMatches) {
            // Filters may have accepted only part of the requested text. Retain the actual edit,
            // discard its evidence and dependent input, and recover the app's real cursor.
            remembered.clear()
            reset(predicts = false)
            selection = Selection(-1, -1)
            selectionConfirmed = false
            confirmState()
            return false
        }
        if (!anchor.available || actualBefore == null || actualAfter == null) {
            // Explicit typing still works in editors without readable context; do not invent a cursor.
            remembered.clear()
            selection = Selection(-1, -1)
            selectionConfirmed = false
            expectedSelections.clear()
            editSnapshot = snapshot()
            return true
        }
        if (previous.start >= 0 && previous.end >= 0) {
            val start = minOf(previous.start, previous.end)
            val end = maxOf(previous.start, previous.end)
            shiftRemembered(start - count, end + after, text.length - (end - start + count + after))
        } else remembered.clear()
        expectSelection(previous, count, text.length)
        editSnapshot = snapshot()
        return true
    }

    private fun expectSelection(previous: Selection, count: Int, inserted: Int) {
        if (previous.start >= 0 && previous.end >= 0) {
            val cursor = minOf(previous.start, previous.end) - count + inserted
            selection = Selection(cursor, cursor)
            selectionConfirmed = false
            if (expectedSelections.size == MAX_PENDING_INPUT) expectedSelections.removeFirst()
            expectedSelections += selection
        }
    }

    /** Submit one final choice for this occurrence, including the latest tentative alternative. */
    private fun leave() {
        val swiped = composition as? Composition.Swiped ?: return
        if (swiped.finalised) return
        swiped.finalised = true
        if (!stillAsSwiped(swiped)) return
        if (swiped.chosen) words.chosen(swiped.swipe, swiped.context, swiped.recognition, swiped.word)
        else words.kept(swiped.swipe, swiped.context, swiped.recognition, swiped.word)
    }

    /** The field is closing with a swiped word that was not chosen from the strip: only data sharing hears of it. */
    private fun leaveKept(swiped: Composition.Swiped) {
        if (swiped.finalised) return
        swiped.finalised = true
        if (stillAsSwiped(swiped)) words.keptAtEnd(swiped.swipe, swiped.context, swiped.recognition, swiped.word)
    }

    /**
     * The app emptied the field, as it does when it sends. Every other change from outside, such as the user selecting
     * text to delete or cut it, has already ended the swiped word, so a swiped word still here was the last thing in the
     * field, just as it was swiped, when the field was sent. Only data sharing hears of it, as in [leaveKept].
     */
    private fun sentWithField() {
        val swiped = composition as? Composition.Swiped ?: return
        if (swiped.finalised || swiped.chosen) return
        swiped.finalised = true
        val asSwiped = field.learning && selectionConfirmed && selection == swiped.snapshot.selection && swiped.snapshot.available
            && !swiped.snapshot.selected && swiped.snapshot.before?.endsWith(swiped.inserted) == true
        if (asSwiped) words.keptAtEnd(swiped.swipe, swiped.context, swiped.recognition, swiped.word)
    }

    /** The field allows learning, and the text is just as the swipe left it: the word was kept. */
    private fun stillAsSwiped(swiped: Composition.Swiped) = field.learning && selectionConfirmed && snapshot() == swiped.snapshot
        && swiped.snapshot.available && !swiped.snapshot.selected && swiped.snapshot.before?.endsWith(swiped.inserted) == true

    private fun observe(text: String) {
        val corrects = field.autocorrect && autocorrect != null
        val shorthand = field.autocorrect && shorthandOnly != null
        if (corrects) autocorrect?.using?.invoke() else if (shorthand) shorthandOnly?.using?.invoke()
        if (!field.learning && !corrects && !shorthand && !capitalsOn() || selection.start < 0 || selection.end < 0) {
            manualWord.clear()
            return
        }
        manualWord.edit(text, null, false)
    }

    /** Swipes and tap keys join the text with no space after some characters, so the word before them is not finished. */
    private fun startsNewWord(before: String) = SwipeTyping.prefix(before).isNotEmpty()

    private fun completeManualWord(anchor: Snapshot): String? {
        val continuesWord = anchor.after?.firstOrNull()?.let { it.isLetter() || it == '\'' || it == '’' } == true
        if (!field.learning || !anchor.knownSelection || anchor.selected
            || correcting != null && (anchor != editSnapshot || continuesWord)) {
            manualWord.clear()
            return null
        }
        val before = anchor.before
        return manualWord.edit(" ", before, before != null && before.length < Editor.CONTEXT_LENGTH)
    }

    /** A deleted space only waits. Once a letter of the word goes, the word was a mistake. */
    private fun forgetIfDeleted(typed: Learned) {
        val after = editSnapshot ?: return
        if (!after.knownSelection || after.selected) return
        if (after.selection.end >= typed.end) lastLearned = Learned(typed.word, typed.end, after, typed.held)
        else if (!typed.held) words.untyped(typed.word)
        else if (heldWord == typed.word) heldWord = null
    }

    /** Notes where [word] ends in the text, so that deleting it straight away can take it back. */
    private fun rememberLearned(word: String, held: Boolean) {
        lastLearned = null
        val anchor = snapshot()
        val before = anchor.before ?: return
        if (!anchor.knownSelection || anchor.selected) return
        val index = before.lastIndexOf(word)
        if (index < 0) return
        val tail = before.substring(index + word.length)
        if (tail.length > 2 || tail.any { it.isLetterOrDigit() || it == '\'' || it == '’' }) return
        lastLearned = Learned(word, anchor.selection.end - tail.length, anchor, held)
    }

    private fun typeWord(word: String, rememberCase: Boolean) {
        // A word is learned in its own case, never the one a sentence start or Caps Lock gave it: see [WordCase]. The text before
        // it says whether it stood at a sentence start.
        val before = snapshot().before
        val index = before?.lastIndexOf(word) ?: -1
        words.typed(WordCase.kept(word, if (index < 0) null else before!!.substring(0, index), false), rememberCase)
        rememberLearned(word, held = false)
    }

    private fun learnWord(word: String?, correction: Composition.Swiped?, rememberCase: Boolean = true) {
        if (!field.learning || word.isNullOrEmpty()) return
        if (correction == null) typeWord(word, rememberCase)
        else if (!word.equals(correction.word, ignoreCase = true)) {
            words.corrected(correction.swipe, correction.context, correction.recognition, word)
            // The engine only counts a correction that resembles the swipe; the word was still typed.
            typeWord(word, rememberCase)
        } else if (correction.chosen) {
            words.chosen(correction.swipe, correction.context, correction.recognition, word)
        }
    }

    private fun corrector(): TypedCorrector? = autocorrect?.takeIf { field.autocorrect }?.corrector

    /** The word data for the shorthand step alone, while Fix typos is off. See [shorthandOnly]. */
    private fun shorthandCorrector(): TypedCorrector? = shorthandOnly?.takeIf { field.autocorrect }?.corrector

    /**
     * Whether "i" and its contractions get their capital here. That's any field of plain text, even where typo fixes are off
     * or before their word data has loaded, but never in a password, an address or exact typing: the fields that get a
     * typed mark's space ([Field.markSpaces]). Autocorrect or automatic capitals must be on.
     */
    private fun capitalsOn() = field.markSpaces && !field.exact && (autocorrect != null || autoCapitalise())

    /** "i" as "I", or "i'm" as "I'm", after [before], unless Backspace took this one's capital back. */
    private fun capitalI(word: String, before: String): TypedCorrector.Fix? {
        if (!capitalsOn() || !fixOn(FixKind.CAPITAL_I) || TypedCorrector.isInsideAddress(before + word) || keptLowercase(word, before)) return null
        return TypedCorrector.capitalI(word)?.let { TypedCorrector.Fix(word, it) }
    }

    /** Whether [word] after [before] is the "i" whose capital Backspace took back, see [lowercaseI]. */
    private fun keptLowercase(word: String, before: String) =
        TypedCorrector.capitalI(word) != null && lowercaseI == (before + word).takeLast(Editor.CONTEXT_LENGTH)

    /** The typed word that ends at the cursor, as it stands in the text. Null if the text there is anything else. */
    private fun typedWordAt(anchor: Snapshot): String? {
        val before = anchor.before
        if (corrector() == null && shorthandCorrector() == null && !capitalsOn() || before == null || !anchor.available || !anchor.knownSelection || anchor.selected) return null
        if (anchor.after?.firstOrNull()?.let { it.isLetter() || it == '\'' || it == '’' } == true) return null
        val raw = manualWord.current()
        val start = before.length - raw.length
        if (raw.isEmpty() || start < 0 || !before.regionMatches(start, raw, 0, raw.length, ignoreCase = true)) return null
        if (if (start == 0) before.length >= Editor.CONTEXT_LENGTH else !before[start - 1].isWhitespace()) return null
        val word = before.substring(start).trimStart(*WORD_OPENERS)
        return word.takeIf { it.isNotEmpty() && it.all { letter -> letter.isLetterOrDigit() || letter == '\'' || letter == '’' } }
    }

    /** The typed word that this space or mark ends, if autocorrect may look at it. */
    private fun endingWord(anchor: Snapshot, correction: Composition.Swiped?): String? {
        settleRestoredWord(anchor)
        return if (correction == null) typedWordAt(anchor) else null
    }

    /** A word that Backspace put back counts as kept if it stays as it was when the next word starts or ends. */
    private fun settleRestoredWord(anchor: Snapshot) {
        val word = restoredWord ?: return
        restoredWord = null
        val before = anchor.before.orEmpty()
        val start = before.length - word.length
        if (start >= 0 && before.endsWith(word) && (start == 0 || !before[start - 1].isLetterOrDigit())) {
            if (field.learning) autocorrect?.keep(word, before.take(start))
        } else autocorrect?.notKept(word)
    }

    private fun findFix(word: String, anchor: Snapshot): TypedCorrector.Fix? {
        val before = anchor.before ?: return null
        guideFix(word, before)?.let { return it }
        val corrector = corrector()
        if (keptLowercase(word, before.dropLast(word.length))) return null
        // Without the word data, only "i" changes. With Fix typos off, shorthand still spells out.
        val fix = (if (corrector != null) corrector.check(word, before.dropLast(word.length), endedWord?.word)
            else shorthandCorrector()?.shorthandFix(word, before.dropLast(word.length)) ?: capitalI(word, before.dropLast(word.length)))
            ?: return null
        return fix.takeIf { before.endsWith(it.typed) && allowedFix(it) }
    }

    /**
     * Whether [fix] may go in: its words must not be blocked, and the fix not refused. A fix that only changes the capitals puts in
     * no new word, so a blocked word can't stop it: Blocked words keys "I" as "i", and with "i" blocked, a lone "i" stayed small
     * while "i'm" became "I'm" (owner, 2026-11-12). Blocking a word keeps it off the bar; it never stops what you type being fixed.
     */
    private fun allowedFix(fix: TypedCorrector.Fix) =
        (fix.word.equals(fix.typed, ignoreCase = true) || fix.word.split(' ').all(words::allowed)) && words.fixAllowed(fix.typed, fix.word)

    /**
     * In the guide's typing text, a typo close to the word that the text has next always becomes that word: its first lesson is that
     * Space fixes typos, so it never misses one (owner, 2026-11-11). Close is up to half the word's letters wrong. Only in the
     * guide's field, where [intended] knows the word.
     */
    private fun guideFix(word: String, before: String): TypedCorrector.Fix? {
        if (word.isEmpty() || !before.endsWith(word) || !word.all(Char::isLetter)) return null
        val wanted = intended(before.dropLast(word.length)) ?: return null
        if (word.equals(wanted, ignoreCase = true)) {
            // The slide about the bar: a word typed right comes out wrong, on purpose, and the right one waits on the bar, where
            // the guide points at it (owner, v5). The typed word is unknown, so the bar offers words for it: see [predict].
            val slip = nearMiss(word) ?: return null
            guidePlaced(GUIDE_PLACE, wanted)
            return TypedCorrector.Fix(word, slip)
        }
        // The first slide: whatever was typed becomes the word, however far off (owner, v5).
        if (!forceFix() && TypedCorrector.editDistance(word, wanted) > maxOf(1, wanted.length / 2)) return null
        val fixed = if (word.first().isUpperCase()) wanted.replaceFirstChar(Char::uppercaseChar) else wanted
        return TypedCorrector.Fix(word, fixed)
    }

    /**
     * Runs once the space or mark [mark] that ended the typed word [word] is in the text. It offers [fix] for undo or,
     * if the first check found none, checks again with the word on each side known. Then a word that waits for the words after
     * it to get its apostrophe may be ready. Returns null if an edit failed.
     */
    private fun wordEnded(word: String?, fix: TypedCorrector.Fix?, mark: String, completed: String?): Ended? {
        val ended = endWord(word, fix, mark, completed) ?: return null
        if (word != null && fix == null) awaitApostrophe(word, mark)
        checkApostrophes()
        return ended
    }

    private fun endWord(word: String?, fix: TypedCorrector.Fix?, mark: String, completed: String?): Ended? {
        val autocorrect = autocorrect
        val corrector = corrector()
        // A fix is offered for undo even without the word data, as "i" gets its capital then too.
        if (fix != null && word != null) {
            onAutocorrect?.invoke()
            onFixed?.invoke(fix.typed, fix.word)
            val typedMark = mark.trimEnd()
            offerUndo(fix.word + mark, fix.typed + typedMark, fix.typed, typedMark.isNotEmpty())
            val last = fix.word.substringAfterLast(' ')
            endedWord = EndedWord(last, fix.typed.takeIf { ' ' !in fix.word && ' ' !in it })
            learnHeldWord()
            if (field.learning) {
                autocorrect?.recent?.record(last)
                words.typed(fix.word, false)
            }
            return Ended(true, null)
        }
        if (autocorrect == null || corrector == null || word == null) {
            endedWord = null
            learnHeldWord()
            return Ended(false, completed)
        }
        val anchor = snapshot()
        val text = anchor.before.orEmpty()
        val review = corrector.review(text.takeLast(REVIEW_LENGTH), text.length >= REVIEW_LENGTH, endedWord, word, true)
            ?.takeIf { anchor.available && !anchor.selected && text.endsWith(it.original) && words.allowed(it.fix)
                && words.fixAllowed(it.word, it.fix) }
        if (review != null) {
            if (!replaceBehind(Composition.None, anchor, EditCause.Autocorrect, review.original.length, review.replaced)) return null
            onAutocorrect?.invoke()
            onFixed?.invoke(review.original, review.replaced)
            offerUndo(review.replaced, review.original, review.word, true)
            if (review.word.equals(heldWord, ignoreCase = true)) heldWord = null
            if (field.learning) words.typed(review.fix, false)
        } else onFixed?.invoke(word, word)
        learnHeldWord()
        val latest = if (review?.reviewedLast == true) review.fix else word
        endedWord = EndedWord(latest, null)
        if (field.learning) autocorrect.recent.record(latest)
        if (review?.reviewedLast == true) return Ended(true, null)
        return Ended(review != null, completed.takeUnless { hold(it, corrector) })
    }

    /**
     * The typed [word] that [mark] just ended may wait for the words after it: "its" is "it's" in "its going", which only the
     * next-word model can tell once those words are in (owner's apostrophe study, 2026-11-11). Typed words only: a swiped word picks
     * its own form.
     */
    private fun awaitApostrophe(word: String, mark: String) {
        val corrector = corrector() ?: return
        val anchor = snapshot()
        val before = anchor.before ?: return
        if (!anchor.available || anchor.selected || !anchor.knownSelection || !before.endsWith(mark)) return
        val end = before.length - mark.length
        val start = end - word.length
        if (start < 0 || !before.regionMatches(start, word, 0, word.length) || start > 0 && !before[start - 1].isWhitespace()) return
        val textBefore = before.substring(0, start)
        if (intended(textBefore) != null || !corrector.awaitsApostrophe(word, textBefore)) return
        apostropheTargets += ApostropheTarget(word, anchor.selection.end - (before.length - start))
        if (apostropheTargets.size > MAX_APOSTROPHE_TARGETS) apostropheTargets.removeAt(0)
    }

    /**
     * Asks the model about the first waiting word whose words after it are in: two words, or fewer where a . ! ? or line break ends
     * the sentence. The answer comes back from the engine's thread, and goes in only if nothing changed meanwhile. A word whose
     * text moved or changed is let go.
     */
    private fun checkApostrophes() {
        if (apostropheTargets.isEmpty()) return
        val corrector = corrector()
        val anchor = snapshot()
        val before = anchor.before
        if (corrector == null || before == null || !anchor.available || anchor.selected || !anchor.knownSelection) {
            apostropheTargets.clear()
            return
        }
        for (target in apostropheTargets.toList()) {
            val offset = anchor.selection.end - target.start
            val tail = if (offset > target.word.length && offset <= before.length) before.takeLast(offset) else null
            if (tail == null || !tail.startsWith(target.word) || tail.length < before.length && !before[before.length - offset - 1].isWhitespace()) {
                apostropheTargets.remove(target)
                continue
            }
            val following = corrector.apostropheFollowing(tail.substring(target.word.length))
            if (following == ApostropheContext.Following.Wait) continue
            apostropheTargets.remove(target)
            if (following !is ApostropheContext.Following.Ready) continue
            val textBefore = before.dropLast(offset)
            val current = epoch
            words.background({ corrector.contextApostrophe(textBefore, target.word, following.after) }) { fixed ->
                if (current != epoch || hasPendingInput || snapshot() != anchor) return@background
                if (fixed != null && fixed != target.word && words.allowed(fixed) && words.fixAllowed(target.word, fixed)
                    && corrector() != null) addApostrophe(target, fixed, tail, anchor)
                checkApostrophes()
            }
            return
        }
    }

    /** Puts [fixed] in place of [target]'s word at the start of [tail], the text up to the cursor. Backspace straight after puts it back. */
    private fun addApostrophe(target: ApostropheTarget, fixed: String, tail: String, anchor: Snapshot) {
        val replaced = fixed + tail.substring(target.word.length)
        invalidate(false)
        if (!replaceBehind(composition, anchor, EditCause.Autocorrect, tail.length, replaced)) return
        onAutocorrect?.invoke()
        offerUndo(replaced, tail, target.word, true)
        if (field.learning) words.typed(fixed, false)
        val change = fixed.length - target.word.length
        for (later in apostropheTargets) if (later.start > target.start) later.start += change
        confirmState(snapshot())
    }

    /** The next word can still change a word that the list does not hold, so it is held to learn after that. True if held. */
    private fun hold(word: String?, corrector: TypedCorrector): Boolean {
        if (word.isNullOrEmpty() || corrector.listed(word)) return false
        learnHeldWord()
        heldWord = word
        rememberLearned(word, held = true)
        return true
    }

    private fun offerUndo(replaced: String, restored: String, word: String, keptAsTyped: Boolean) {
        undo = Undo(replaced, restored, word, keptAsTyped, snapshot())
    }

    private fun learnHeldWord() {
        heldWord?.let { words.typed(it) }
        heldWord = null
    }

    /** Nothing more can happen to the typed words so far. */
    private fun settleTypedWords() {
        endedWord = null
        endedBeforeSwipe = null
        restoredWord = null
        lastLearned = null
        learnHeldWord()
    }

    /** Backspace straight after a fix puts back what the user typed. That word then stays as it is for this session. */
    /**
     * Whether [before], the editor's window of text before the cursor, ends with [text]. A snippet can be longer than the window
     * ([Editor.CONTEXT_LENGTH]), and then the whole window is its end: one over 2,048 characters couldn't be undone (bug hunt #30).
     */
    private fun endsWithWhole(before: String?, text: String) = before != null &&
        (before.endsWith(text) || before.length >= Editor.CONTEXT_LENGTH && text.endsWith(before))

    private fun undoCorrection(): Boolean {
        val pending = undo ?: return false
        undo = null
        val anchor = snapshot()
        if (anchor != pending.snapshot || anchor.selected || !endsWithWhole(anchor.before, pending.replaced)) return false
        val showing = composition
        invalidate(false)
        if (showing !is Composition.Swiped) {
            automaticSpace = null
            fullStop = null
        }
        manualWord.clear()
        correcting = null
        awaitingNextWord = null
        heldWord = null
        endedWord = null
        endedBeforeSwipe = null
        val cause = when {
            pending.spacing -> EditCause.MarkSpaceUndo
            pending.expansion -> EditCause.ExpandUndo
            else -> EditCause.AutocorrectUndo
        }
        if (!replaceBehind(showing, anchor, cause, pending.replaced.length, pending.restored)) return true
        if (pending.spacing) {
            // The user wants the letter straight after the mark, so it stays there.
            typedMark = null
            spaced = null
            confirmState(snapshot())
            return true
        }
        onRestored?.invoke()
        if (pending.expansion) {
            confirmState(snapshot())
            return true
        }
        val autocorrect = autocorrect
        val accepted = snapshot()
        // Only this "i" keeps its small letter. Any other word that Backspace put back stays as typed for the field.
        if (TypedCorrector.capitalI(pending.word)?.let(pending.replaced::startsWith) == true && pending.restored.startsWith(pending.word)) {
            val before = accepted.before.orEmpty()
            lowercaseI = before.dropLast(pending.restored.length - pending.word.length).takeLast(Editor.CONTEXT_LENGTH)
            confirmState(accepted)
            return true
        }
        (autocorrect ?: shorthandOnly)?.undone(pending.word)
        if (pending.keptAsTyped) {
            val before = accepted.before.orEmpty().dropLast(pending.restored.length)
            if (field.learning) autocorrect?.keep(pending.word, before)
        } else restoredWord = pending.word
        confirmState(accepted)
        return true
    }

    /** Edits text behind the cursor, and keeps a swiped word that is showing as it was. False if the edit failed. */
    private fun replaceBehind(showing: Composition, anchor: Snapshot, cause: EditCause, count: Int, text: String): Boolean {
        if (!edit(cause, count, text)) return false
        val accepted = snapshot()
        readKeptWords(accepted)
        if (automaticSpace == anchor) automaticSpace = accepted
        if (typedMark == anchor) typedMark = accepted
        fullStop = fullStop?.let { if (it.snapshot == anchor) FullStop(it.time, accepted) else it }
        if (showing is Composition.Swiped) {
            val context = accepted.before?.removeSuffix(showing.inserted) ?: showing.context
            val swiped = showing.copy(context = context, snapshot = accepted)
            composition = swiped
            remember(swiped)
        } else composition = Composition.None
        return true
    }

    /** The swiped word shows what fits around the typed word before it. That is checked once no touch can add to the swipe. */
    private fun reviewBeforeSwipe(swiped: Composition.Swiped) {
        val autocorrect = autocorrect ?: return
        if (field.learning && field.autocorrect) autocorrect.recent.record(swiped.word)
        val earlier = endedWord ?: endedBeforeSwipe
        endedWord = null
        endedBeforeSwipe = earlier
        if (earlier == null || corrector() == null || !swiped.trailingSpace) return
        val current = epoch
        later(liftOffMillis.toLong()) {
            if (current != epoch || composition !== swiped || hasPendingInput || endedBeforeSwipe !== earlier) return@later
            endedBeforeSwipe = null
            val anchor = snapshot()
            val before = anchor.before ?: return@later
            if (anchor != swiped.snapshot || anchor.selected) return@later
            val review = corrector()?.review(before.takeLast(REVIEW_LENGTH), before.length >= REVIEW_LENGTH, earlier, swiped.word, false)
                ?.takeIf { before.endsWith(it.original) && words.allowed(it.fix) && words.fixAllowed(it.word, it.fix) } ?: return@later
            invalidate(false)
            if (!replaceBehind(swiped, anchor, EditCause.Autocorrect, review.original.length, review.replaced)) return@later
            offerUndo(review.replaced, review.original, review.word, true)
            if (review.word.equals(heldWord, ignoreCase = true)) heldWord = null
            if (field.learning) words.typed(review.fix, false)
            confirmState(snapshot())
        }
    }

    private fun continuesAddress(replaced: String, text: String) = replaced.endsWith(".") && text.length == 1
        && (text[0].isLetterOrDigit() || text[0] in "@_-")

    private fun predictLater() {
        val current = epoch
        later(PREDICTION_DELAY_MILLIS) { if (current == epoch) predict() }
    }

    /**
     * A swipe's choices in the interactive guide. When the swipe typed another word than [wanted], the guide's word goes second,
     * where one touch on the strip puts it in. For a near miss on purpose a wrong word always goes in first, whatever was swiped:
     * the swipe's next guess, or the right word with a slip in it when there is no other guess.
     */
    private fun guideChoices(guessed: List<String>, wanted: String?): List<String> {
        if (wanted == null) return guessed
        val at = guessed.indexOfFirst { sameLetters(it, wanted) }
        val right = guessed.getOrNull(at) ?: wanted
        val others = guessed.filterIndexed { index, _ -> index != at }
        // A slide where nothing can go wrong: whatever was swiped, however badly, becomes the word the line has next (owner, v5.2).
        if (forceFix()) return listOf(right) + others
        nearMiss(right)?.let { slip ->
            // The right word takes a random place after the wrong one, so it has to be looked for. The guide points at it.
            val rest = others.drop(1)
            val place = 1 + kotlin.random.Random.nextInt(minOf(rest.size, GUIDE_RIGHT_PLACES - 1) + 1)
            guidePlaced(place, right)
            return (listOf(others.firstOrNull() ?: slip) + rest).toMutableList().apply { add(place, right) }
        }
        if (others.isEmpty() || at == 0) return guessed
        guidePlaced(1, right)
        return listOf(others[0], right) + others.drop(1)
    }

    /** Typed suggestions in the interactive guide: while a word is not the guide's word yet, the guide's word has a place. */
    private fun guideSuggestions(found: List<String>, prefix: String, wanted: String?): List<String> {
        if (wanted == null || prefix.isEmpty() || sameLetters(prefix, wanted)) return found
        val at = found.indexOfFirst { sameLetters(it, wanted) }
        if (at in 0..GUIDE_PLACE) return found
        val others = found.filterIndexed { index, _ -> index != at }
        return others.take(GUIDE_PLACE) + (found.getOrNull(at) ?: wanted) + others.drop(GUIDE_PLACE)
    }

    /**
     * The owner's rule: the bar's middle word is what Space will put in. That is autocorrect's fix for the typed word, or the
     * typed word itself when there is nothing to fix, so it goes first, and the other words follow without it.
     */
    private fun spaceFirst(found: List<String>, anchor: Snapshot): List<String> {
        val word = typedWordAt(anchor) ?: return found
        val fix = findFix(word, anchor)
        // Without the word data the bar stays as it was, unless Space would give "i" its capital.
        if (fix == null && corrector() == null) return found
        val space = fix?.word ?: word
        return listOf(space) + found.filterNot { it.equals(space, ignoreCase = true) }
    }

    private fun sameLetters(a: String, b: String) = a.filter(Char::isLetter).equals(b.filter(Char::isLetter), ignoreCase = true)

    private fun show(suggestions: List<String>) {
        barIdle = suggestions.isEmpty()
        // The cursor says it without asking the app: anywhere but the start, or not known, there may be text.
        barForText = selection.start != 0 || selection.end != 0
        shownAlternatives = suggestions.indices.filter { suggestions[it].lowercase() !in hidden && words.allowed(suggestions[it]) }
        showWords(shownAlternatives.map(suggestions::get))
    }

    private fun predict() {
        if (composition !is Composition.None && composition !is Composition.Starts || hasPendingInput) return
        val anchor = snapshot()
        // Predictions must belong to the last verified owned edit.
        if (editSnapshot != null && anchor != editSnapshot) return
        if (!anchor.available || !anchor.knownSelection) {
            offerStarts(anchor)
            return
        }
        val before = anchor.before ?: return
        val after = anchor.after ?: return
        // Browsers select the whole address when you tap it, and typing replaces it, so offer starters then too.
        val starters = if (field.starters && before.isEmpty() && after.isEmpty()) STARTERS.filter(words::allowed) else emptyList()
        if (starters.isNotEmpty()) {
            composition = Composition.Typed("", false, anchor, starters)
            show(starters)
            barIdle = true
            return
        }
        // Typing or a swipe replaces a selection, and so does a start. Nothing but a start fits one.
        if (anchor.selected) {
            offerStarts(anchor)
            return
        }
        checkLookAlike()
        if (!field.suggestions) {
            show(emptyList())
            return
        }
        readKeptWords(anchor)
        if (recall(anchor)) return
        if (after.firstOrNull()?.let { it.isLetter() || it == '\'' || it == '’' } == true) {
            offerStarts(anchor)
            return
        }
        if (offerExpansions(anchor, before)) return
        val lastWord = TypedPredictions.prefix(before.dropLast(1))
        val trailingSpace = before.endsWith(" ") && lastWord.isNotEmpty() && !words.known(lastWord)
        val context = if (trailingSpace) before.dropLast(1) else before
        // Completions wait for the models, which can take seconds to load. An emptied field shows its starts at once, as a chat
        // app's does after it sends: the last message's words must not stay on the bar meanwhile (owner, 2026-11-12).
        if (barIdle || barForText && before.isEmpty() && after.isEmpty()) offerStarts(anchor)
        val current = epoch
        completion?.set(true)
        val cancelled = AtomicBoolean(false)
        completion = cancelled
        completionSnapshot = anchor
        words.complete(context, cancelled, field.sentenceCapitalisation && autoCapitalise()) { found ->
            if (cancelled.get() || current != epoch || snapshot() != anchor) return@complete
            val prefix = TypedPredictions.prefix(context)
            val guided = guideSuggestions(found, prefix, intended(context.dropLast(prefix.length)))
            val suggestions = if (trailingSpace) guided else spaceFirst(guided, anchor)
            if (suggestions.isEmpty()) {
                offerStarts(anchor)
                return@complete
            }
            composition = Composition.Typed(prefix, trailingSpace, anchor, suggestions)
            show(suggestions)
            // The first word is the one most likely to be picked, so have what follows it ready.
            suggestions.firstOrNull()?.let {
                words.prepare(context.dropLast(TypedPredictions.prefix(context).length) + " " + it + " ", cancelled)
            }
        }
    }

    /** Space after a snippet's shortcut puts in its text. Not in a password or a field with no words, nor over a selection. */
    private fun expandSnippet(anchor: Snapshot): Boolean {
        val before = anchor.before
        if (!field.words || before == null || anchor.selected) return false
        val (text, spaces) = codeText(anchor)
        val expansion = expansions?.snippet(text, freeMark(text)) ?: return false
        expand(withSpaces(expansion, spaces))
        return true
    }

    /** A colon that closes a shortcode such as :smile: puts in its emoji in place of the code, with no colon. */
    private fun expandShortcode(anchor: Snapshot): Boolean {
        val before = anchor.before
        if (!field.words || before == null || anchor.selected) return false
        val (text, spaces) = codeText(anchor)
        val expansion = expansions?.shortcode(text, freeMark(text)) ?: return false
        expand(withSpaces(expansion, spaces))
        return true
    }

    /**
     * Puts [expansion] in place of its code, and lets Backspace put the code back. What it puts in is the user's own text, not
     * typing, so none of it is learned: the word being typed, the words kept so far and a word on hold all end here.
     */
    private fun expand(expansion: Expansions.Expansion, predictNow: Boolean = false) {
        composition = Composition.None
        manualWord.clear()
        keptWords.clear()
        correcting = null
        awaitingNextWord = null
        automaticSpace = null
        fullStop = null
        typedKey = null
        spaced = null
        settleTypedWords()
        if (!edit(EditCause.Expand, expansion.code.length, expansion.text)) return
        onExpanded?.invoke()
        val accepted = snapshot()
        undo = Undo(expansion.text, expansion.restored, "", false, accepted, expansion = true)
        confirmState(accepted, predictNow = predictNow)
    }

    /** Where in [before] the last mark that took the place of a space is, or -1. See [Expansions]. */
    private fun freeMark(before: String): Int {
        val mark = markAfterSpace ?: return -1
        return if (before.length > mark.length && before.startsWith(mark)) mark.length - 1 else -1
    }

    /** While a snippet's shortcut or a shortcode is typed, the strip offers what it can become. */
    private fun offerExpansions(anchor: Snapshot, before: String): Boolean {
        if (!field.words) return false
        val (text, spaces) = codeText(anchor)
        val choices = expansions?.choices(text, EXPANSION_CHOICES, freeMark(text)).orEmpty()
            .map { it.copy(expansion = withSpaces(it.expansion, spaces)) }
        if (choices.isEmpty()) return false
        val offered = Composition.Expanding(anchor, choices)
        composition = offered
        show(offered.alternatives)
        return true
    }

    /** Puts popular sentence starts on the bar, so that it is not blank. Where suggestions are off, the bar stays empty and hidden. */
    private fun offerStarts(anchor: Snapshot) {
        val sentenceCase = field.sentenceCapitalisation && autoCapitalise()
        val starts = if (!field.suggestions) emptyList() else words.sentenceStarts().ifEmpty { FEW_STARTS }.filter(words::allowed)
            .map { SwipeTyping.capitalise(it, anchor.before.orEmpty(), 0, false, sentenceCase) }
        composition = if (starts.isEmpty()) Composition.None else Composition.Starts(anchor, starts)
        show(starts)
        barIdle = true
    }

    private fun mayLearnFrom(anchor: Snapshot) = field.learning && anchor.available && anchor.knownSelection && !anchor.selected

    /** [predict] does not read the text while a swiped word shows, so an edit that can happen then must call this itself. */
    private fun readKeptWords(anchor: Snapshot) {
        if (mayLearnFrom(anchor)) keptWords.saw(anchor.before, anchor.selection.end)
    }

    /**
     * The spaces that go in before [letter], typed straight after a mark. None after a mark that is not the last thing typed,
     * or one inside a number, an address, an ellipsis or an abbreviation such as Mr. or e.g.
     */
    private fun spacesAfterMark(letter: String, anchor: Snapshot): String {
        if (!field.markSpaces || anchor != typedMark || !anchor.available || anchor.selected) return ""
        if (!Character.isLetter(letter.codePointAt(0))) return ""
        val before = anchor.before ?: return ""
        val mark = before.lastOrNull() ?: return ""
        val count = when (mark) {
            in SENTENCE_MARKS -> sentenceSpaces()
            in SPACED_MARKS -> if (commaSpace()) 1 else 0
            else -> 0
        }
        val word = before.dropLast(1)
        // A mark after a space or another mark, as in "..." or "?!", ends no word.
        if (count <= 0 || !endsWord(word)) return ""
        val token = word.takeLastWhile { !it.isWhitespace() }.trimStart(*WORD_OPENERS).lowercase()
        if (address(token, mark)) return ""
        if (mark == '.' && (token.length == 1 && token[0].isLetter() || token in NO_SPACE_ABBREVIATIONS)) return ""
        return " ".repeat(count)
    }

    /** Whether [token], which [mark] follows, is part of an address, a path or a dotted abbreviation such as e.g. */
    private fun address(token: String, mark: Char) = token.any { it in "@/\\." } || token.startsWith("www")
        || mark == ':' && token in URL_SCHEMES

    /**
     * The word typed so far after [record]'s spaces, as it stands at the end of [anchor]. Null if the text there is
     * anything else.
     */
    private fun spacedWord(record: Spaced, anchor: Snapshot): String? {
        val before = anchor.before ?: return null
        if (!anchor.available || !anchor.knownSelection || anchor.selected) return null
        val word = before.takeLastWhile { it.isLetterOrDigit() }
        val start = before.length - word.length - record.spaces.length - 1
        if (start < 0 || !word.startsWith(record.put) || anchor.selection.end - word.length - record.spaces.length != record.at)
            return null
        return word.takeIf { before[start] == record.mark && before.regionMatches(start + 1, record.spaces, 0, record.spaces.length) }
    }

    /**
     * The word after spaces that went in after a full stop ends with [next]. An address or a file name, such as atlas.com, or a
     * name before @, gets its mark back with no spaces, and the letters as they were typed. A fix of the word before the
     * mark is taken back too, as an address ends no sentence. False if an edit failed.
     */
    private fun unspaceAddress(next: String): Boolean {
        val record = spaced?.takeIf { it.mark == '.' }
        spaced = null
        if (record == null) return true
        val anchor = snapshot()
        val word = spacedWord(record, anchor) ?: return true
        val ends = next.firstOrNull() ?: return true
        val address = ends == '@' || (ends.isWhitespace() || ends in ADDRESS_FOLLOWERS) && word.lowercase() in ADDRESS_ENDINGS
        if (!address) return true
        val typed = record.typed + word.substring(record.put.length)
        val spacedText = record.spaces + word
        val fix = record.corrected?.takeIf { anchor.before?.endsWith(it.replaced + spacedText) == true }
        val count = (fix?.replaced?.length ?: 1) + spacedText.length
        if (!edit(EditCause.MarkSpaceUndo, count, (fix?.restored ?: record.mark.toString()) + typed)) return false
        manualWord.clear()
        if (fix != null) endedWord = null
        return true
    }

    /**
     * The text that a snippet's shortcut or a shortcode is looked for in. A mark that took the place of a space can start one,
     * so spaces that went in after it are left out, as in "Note:" then smile: for an emoji. Also says how many it left out.
     */
    private fun codeText(anchor: Snapshot): Pair<String, Int> {
        val before = anchor.before.orEmpty()
        val record = spaced ?: return before to 0
        val word = spacedWord(record, anchor) ?: return before to 0
        val joined = before.dropLast(word.length + record.spaces.length) + word
        return if (freeMark(joined) == joined.length - word.length - 1) joined to record.spaces.length else before to 0
    }

    /** [expansion] with its code widened by the [spaces] that [codeText] left out after its mark. */
    private fun withSpaces(expansion: Expansions.Expansion, spaces: Int) = if (spaces == 0) expansion
        else expansion.copy(code = expansion.code.take(1) + " ".repeat(spaces) + expansion.code.drop(1))

    /** The space before a word that a swipe, a tap or a pick puts in. After a sentence, Two spaces gives two. */
    private fun wordSpace(before: String): String {
        val space = SwipeTyping.prefix(before)
        val sentence = space.isNotEmpty() && field.markSpaces && sentenceSpaces() == 2 && before.last() in SENTENCE_MARKS
            && SwipeTyping.startsSentence(before + space)
        return if (sentence) "  " else space
    }

    private fun endsWord(text: String): Boolean {
        if (text.isEmpty()) return false
        val last = text.codePointBefore(text.length)
        return Character.isLetterOrDigit(last) || ")]}\"'’”".indexOf(last.toChar()) >= 0
    }

    companion object {
        private const val MAX_PENDING_INPUT = 128
        private const val MAX_PENDING_TEXT = 8192
        private const val MAX_TEXT_CHUNK = 256
        private const val DRAIN_BUDGET = 8
        private const val DRAIN_NANOS = 4_000_000L
        private const val DESTINATION_TIMEOUT_MILLIS = 500L
        private const val AFTER_CONTEXT_LENGTH = 32
        /** Where the interactive guide's word goes among typed suggestions: second, after the best of the rest. */
        private const val GUIDE_PLACE = 1
        /** How many places after the wrong word the right one may take, on the guide's slide about the bar. */
        private const val GUIDE_RIGHT_PLACES = 2
        private const val FULL_STOP_MILLIS = 400L
        private const val PREDICTION_DELAY_MILLIS = 60L
        /** A swipe this soon after the last one may be its other half, so it is also read joined to it. */
        private const val JOIN_CHECK_MILLIS = 150L
        private val STARTERS = listOf("What", "How", "Where")
        /** For the time before the word lists load, and if they can't. Written as inside a sentence. */
        private val FEW_STARTS = listOf("I", "the", "hello")
        private const val MAX_REMEMBERED = 1000
        /** As many as the strip shows. */
        private const val EXPANSION_CHOICES = 3
        /** Characters that end a word, so a swiped word right before one needs no space after it. */
        private const val WORD_CLOSERS = ".,!?;:)]}…"
        /** Marks that end a typed word, which autocorrect then checks. */
        private const val TYPED_WORD_ENDS = "$WORD_CLOSERS\"”"
        private val WORD_OPENERS = charArrayOf('"', '\'', '(', '[', '{', '“', '‘')
        /** Marks that end a sentence, which Space after a sentence follows. */
        private const val SENTENCE_MARKS = ".?!"
        /** Marks whose next letter may bring a space: the sentence marks, and those that Space after , ; : follows. */
        private const val SPACED_MARKS = "$SENTENCE_MARKS,;:"
        /** Abbreviations that a full stop doesn't end, such as Mr. Single letters, as in e.g. and J. Smith, count as well. */
        private val NO_SPACE_ABBREVIATIONS = setOf("mr", "mrs", "ms", "mx", "dr", "prof", "st", "jr", "sr", "vs", "cf", "viz",
            "eg", "ie", "approx", "fig")
        /** What comes before a colon in a link, as in http: or mailto: */
        private val URL_SCHEMES = setOf("http", "https", "ftp", "sftp", "file", "mailto", "tel", "sms", "geo", "ssh")
        /**
         * Last parts of web addresses and file names that are no common word. A spaced word that turns out to be one of them
         * goes back against its full stop, as in atlas.com and notes.txt.
         */
        private val ADDRESS_ENDINGS = setOf("com", "org", "net", "edu", "gov", "mil", "io", "co", "eu", "de",
            "fr", "nl", "ca", "au", "nz", "jp", "cn", "ru", "ch", "se", "dk", "pl", "info", "biz", "xyz",
            "txt", "pdf", "jpg", "jpeg", "png", "gif", "webp", "heic", "svg", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "csv", "zip", "md", "html", "htm", "js", "ts", "kt", "kts", "java", "py", "json", "xml", "yml", "yaml", "apk", "mp3",
            "mp4", "mov", "wav", "exe")
        /** What can come right after an address: the end of a sentence, a path, a port or a closing bracket. */
        private const val ADDRESS_FOLLOWERS = ".,!?;:/)]"
        /** How much text the second check reads. Numen tuned it on this much. */
        private const val REVIEW_LENGTH = 96
        /** Typed words that wait for their apostrophe at once: "if its your turn" has two. */
        private const val MAX_APOSTROPHE_TARGETS = 3
    }
}
