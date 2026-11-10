package com.atlas.autocorrect

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.atlas.engine.PhoneWords
import com.atlas.engine.Swipe
import com.atlas.engine.SwipeTyping
import com.atlas.keyboard.Composer
import com.atlas.keyboard.Editor
import com.atlas.keyboard.EnterKey
import com.atlas.keyboard.Field
import com.atlas.keyboard.HapticPreset
import com.atlas.keyboard.Words
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

private val failures = ArrayList<String>()

private fun must(condition: Boolean, message: String) {
    if (!condition) failures += message
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) failures += "$message\n  expected: $expected\n  actual:   $actual"
}

private const val START = PairCounts.SENTENCE_START
private const val END = PairCounts.SENTENCE_END
private const val SEED = 7
private const val SLIP_RATE = 0.12
private const val MISSED_SPACE = "missed space"
private const val BOUNDARY_PUNCTUATION = ".,!?;:"

private val near: (Char, Char) -> Boolean = { a, b -> KeyNeighbours.near(a, b) }

/** The word list and the user's own words, as AutocorrectLoader gives them. British spelling is the setting's default. */
private class Lists(val words: PhoneWords, val habits: PhoneWords.Habits = PhoneWords.Habits(),
                    val spelling: (String) -> String = { PhoneWords.spelled(it, false) }) : Lexicon {
    override fun known(word: String) = words.contains(word) || habits.active(word)
    override fun frequency(word: String) = words.frequency(word)
    override fun personal(word: String) = habits.active(word)
    override fun name(word: String): String? = words.name(word)
    override fun preferredSpelling(word: String) = spelling(word)
    override fun forEachWord(action: (String, Int) -> Unit) = words.forEachWord { word, frequency -> action(word, frequency) }
}

private class Realistic {
    val words = PhoneWords(Path.of("android/assets/swipe/hungry_jellyfish/vocab.txt"), Path.of("android/assets/swipe/english.combined"))
    val lists = Lists(words)
    val heapBefore = used()
    val loadStart = System.nanoTime()
    val data = CorrectionData.read(lists) { File("android/assets/autocorrect/$it").reader(Charsets.UTF_8) }
    val loadMillis = (System.nanoTime() - loadStart) / 1_000_000
    val heapAfter = used()
    val learned = ArrayList<String>()
    fun autocorrect(lexicon: Lexicon = lists): Autocorrect = wired(Autocorrect(KeptOnceStore.inMemory(), { learned += it }), lexicon)
    fun wired(autocorrect: Autocorrect, lexicon: Lexicon = lists): Autocorrect = autocorrect.also {
        it.corrector = TypedCorrector(lexicon, data, it.rejected, it.recent)
    }

    companion object {
        fun used(): Long {
            repeat(3) { System.gc(); Thread.sleep(50) }
            return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        }
    }
}

private fun corrector(world: Realistic, autocorrect: Autocorrect = world.autocorrect()) = autocorrect.corrector!!

private class FakeEditor : Editor {
    private val owner = Any()
    var text = ""
    var enters = Editor.Enter.Newline
    var enterPresses = 0
    /** Cursor positions that the app has yet to report. On a phone they arrive after the key's handler returns. */
    val reports = ArrayDeque<Int>()
    private var reported = 0
    var fieldId = 1
    /** Where the user put the cursor or selection. Null means that the cursor follows the end of the text. */
    var cursor: Pair<Int, Int>? = null
    /** The app doesn't say what is in its field. */
    var unreadable = false
    /** The app refuses each edit. */
    var refuses = false
    private val start get() = cursor?.first ?: text.length
    private val end get() = cursor?.second ?: text.length

    private fun report() {
        if (start != reported) reports += start
        reported = start
    }

    override fun identity() = Editor.Identity(owner, "test", fieldId)
    override fun state(result: (Editor.State?) -> Unit) = result(if (unreadable) null else Editor.State(before()!!, after()!!, start, end))
    override fun before() = if (unreadable) null else text.take(start).takeLast(Editor.CONTEXT_LENGTH)
    override fun after() = if (unreadable) null else text.drop(end).take(32)
    override fun enter(press: EnterKey.Press): Editor.Enter {
        enterPresses++
        return enters
    }

    override fun replace(count: Int, text: String, after: Int): Boolean {
        if (refuses) return false
        val kept = start - count
        this.text = this.text.take(kept) + text + this.text.drop(end + after)
        cursor = cursor?.let { (kept + text.length).let { at -> at to at } }
        report()
        return true
    }

    override fun delete(word: Boolean, selectionStart: Int, selectionEnd: Int): Int {
        val removed = if (word) {
            val trimmed = text.trimEnd()
            text.length - trimmed.dropLastWhile { !it.isWhitespace() }.length
        } else minOf(1, text.length)
        text = text.dropLast(removed)
        report()
        return removed
    }
}

private class FakeWords(private val lists: Lists) : Words {
    val typed = ArrayList<String>()
    val untyped = ArrayList<String>()
    val corrected = ArrayList<String>()
    /** Each pair the next-word predictor was told to learn. "^" stands for the start of a sentence. */
    val pairs = ArrayList<String>()
    /** An empty word means that the swipe found nothing. */
    var swiped = ""
    var lookAlike: String? = null
    var starts = listOf("I", "the", "hello", "you")
    /** What completion answers. Null means that the models are still loading, so the answer waits in [unanswered]. */
    var completions: List<String>? = emptyList()
    val unanswered = ArrayDeque<(List<String>) -> Unit>()
    /** Swipes wait in [unrecognised] until the test lets them finish. */
    var holdSwipes = false
    val unrecognised = ArrayDeque<() -> Unit>()
    val blocked = HashSet<String>()
    /** Fixes refused by holding them on the strip, as "typed>fix" in lower case. */
    val refusedFixes = HashSet<String>()

    /** The next swipe readings, each a word and how clearly it was read. When this is empty, a swipe reads as [swiped]. */
    val readings = ArrayDeque<Pair<String, Float?>>()

    override fun recognise(swipe: Swipe, context: String, personalise: Boolean, result: (Words.Recognition) -> Unit) {
        val reading = readings.removeFirstOrNull()
        val word = reading?.first ?: swiped
        val finish = { result(Words.Recognition(listOfNotNull(word.takeIf { it.isNotEmpty() }), null, null, reading?.second)) }
        if (holdSwipes) unrecognised += finish else finish()
    }
    override fun sentenceStarts() = starts
    override fun complete(before: String, cancelled: AtomicBoolean, sentenceCapitalisation: Boolean, result: (List<String>) -> Unit) {
        val words = completions
        if (words == null) unanswered += result else result(words)
    }
    override fun prepare(text: String, cancelled: AtomicBoolean) = Unit
    override fun lookAlike(context: String, word: String, next: String, result: (String?) -> Unit) = result(lookAlike)
    override fun allowed(word: String) = word.lowercase() !in blocked
    override fun fixAllowed(typed: String, fix: String) = "${typed.lowercase()}>${fix.lowercase()}" !in refusedFixes
    override fun known(word: String) = lists.known(word) || lists.habits.seen(word)
    override fun typed(word: String, rememberCase: Boolean) {
        typed += word
        if (!lists.words.contains(word)) lists.habits.typed(word)
    }
    override fun untyped(word: String) { untyped += word }
    override fun keptPair(previous: String?, next: String) { pairs += "${previous ?: "^"} $next" }
    override fun chosen(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) = Unit
    override fun corrected(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) { corrected += word }
    override fun kept(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) = Unit
}

/** Types into a real Composer, the way the keyboard does for each key. */
private class Typist(world: Realistic, val autocorrect: Autocorrect? = world.autocorrect(), val field: Field = FIELD,
                     lists: Lists = Lists(world.words), autoCapitalise: Boolean = false, val words: FakeWords = FakeWords(lists)) {
    val editor = FakeEditor()
    /** The words on the suggestion bar. */
    var bar = emptyList<String>()
    private val posted = ArrayDeque<() -> Unit>()
    private var time = 0L
    /** How many app reports the keyboard took for a change that it did not make. Each one drops its undo and queued keys. */
    var externalChanges = 0
    /** How many times the keyboard was told to vibrate because a word changed by itself. */
    var vibrations = 0
    val composer = Composer(editor, words, { _, action -> posted += action }, {}, { autoCapitalise }, { bar = it })

    init {
        composer.autocorrect = autocorrect
        composer.onAutocorrect = { vibrations++ }
        composer.start(field, 0, 0)
        settle()
    }

    fun settle() {
        var budget = 1000
        while (budget-- > 0) {
            val report = editor.reports.removeFirstOrNull()
            if (report != null) {
                if (composer.selectionChanged(report, report)) externalChanges++
                continue
            }
            (posted.removeFirstOrNull() ?: return)()
        }
    }

    /** A second Space, soon enough after the last key to make a full stop. */
    fun quickSpace(): Typist {
        composer.space(false, time - 800)
        settle()
        return this
    }

    /** A tap on a letter key, which types that letter as a word. */
    fun tap(letter: String): Typist {
        composer.type(letter, true)
        time += 1000
        settle()
        return this
    }

    fun type(text: String): Typist {
        for (character in text) {
            when (character) {
                ' ' -> composer.space(false, time)
                '\n' -> composer.enter(EnterKey.Press.NewLine)
                else -> composer.type(character.toString())
            }
            time += 1000
            settle()
        }
        return this
    }

    fun backspace(): Typist {
        composer.backspace(false)
        settle()
        return this
    }

    fun swipe(word: String): Typist {
        words.swiped = word
        val stroke = Swipe(floatArrayOf(.1f, .4f, .8f), floatArrayOf(.2f, .3f, .4f), floatArrayOf(0f, 100f, 200f))
        composer.swipe(stroke, time, time + 200, 0)
        time += 1000
        settle()
        return this
    }

    /** A swipe that starts [gap] ms after the last one ended. */
    fun swipeAfter(gap: Long): Typist {
        val stroke = Swipe(floatArrayOf(.1f, .4f, .8f), floatArrayOf(.2f, .3f, .4f), floatArrayOf(0f, 100f, 200f))
        val start = time - 800 + gap
        composer.swipe(stroke, start, start + 200, 0)
        time = start + 1000
        settle()
        return this
    }

    /** A touch on a word of the suggestion bar. */
    fun choose(index: Int): Typist {
        composer.choose(index)
        settle()
        return this
    }

    /** The user puts the cursor at [start], or selects up to [end]. */
    fun select(start: Int, end: Int = start): Typist {
        editor.cursor = start to end
        composer.selectionChanged(start, end)
        settle()
        return this
    }

    val text get() = editor.text

    companion object {
        val FIELD = Field.of(InputType.TYPE_CLASS_TEXT, 0)
    }
}

private val typos = linkedMapOf(
    "missed key" to listOf(
        "wrld" to "world", "thnks" to "thanks", "becase" to "because", "peple" to "people",
        "somthing" to "something", "probaly" to "probably", "diferent" to "different",
        "realy" to "really", "finaly" to "finally", "intresting" to "interesting",
        "enviroment" to "environment", "recomend" to "recommend", "mondy" to "monday",
        "frday" to "friday", "yesterdy" to "yesterday", "mesage" to "message",
        "adress" to "address", "goverment" to "government", "beautful" to "beautiful",
        "evrything" to "everything", "anythng" to "anything", "togther" to "together",
        "remmber" to "remember", "tomrrow" to "tomorrow", "importnt" to "important",
        "mothe" to "mother", "ubject" to "subject", "th" to "the", "nd" to "and",
    ),
    "missed double letter" to listOf(
        "helo" to "hello", "sory" to "sorry", "beter" to "better", "leter" to "letter",
        "coffe" to "coffee", "ocur" to "occur", "sucess" to "success", "acount" to "account",
        "aparently" to "apparently", "tomorow" to "tomorrow", "imediately" to "immediately",
        "necesary" to "necessary", "comunity" to "community", "diference" to "difference",
        "sugest" to "suggest", "realy" to "really", "dinner" to "dinner", "runing" to "running",
        "geting" to "getting", "stoped" to "stopped",
    ),
    "swapped letters" to listOf(
        "teh" to "the", "adn" to "and", "hte" to "the", "thier" to "their",
        "recieve" to "receive", "wierd" to "weird", "freind" to "friend",
        "becuase" to "because", "jsut" to "just", "waht" to "what", "taht" to "that",
        "whcih" to "which", "wiht" to "with", "siad" to "said", "agian" to "again",
        "thnig" to "thing", "liek" to "like", "knwo" to "know", "beleive" to "believe",
        "peopel" to "people",
    ),
    "neighbor key" to listOf(
        "thw" to "the", "wirk" to "work", "si" to "is", "ot" to "to", "hapoy" to "happy", "goid" to "good",
        "helli" to "hello", "lpve" to "love", "nicw" to "nice", "grwat" to "great",
        "meetinf" to "meeting", "workinh" to "working", "thinj" to "think", "juat" to "just",
        "wirld" to "world", "amd" to "and", "tge" to "the", "thr" to "the",
        "yhe" to "the", "rhe" to "the", "qnd" to "and", "sometjing" to "something",
    ),
    "extra letter" to listOf(
        "thhe" to "the", "helpp" to "help", "tthe" to "the", "untill" to "until",
        "andd" to "and", "frriend" to "friend", "whatt" to "what", "becausee" to "because",
        "juust" to "just", "thiss" to "this", "worldd" to "world", "aand" to "and",
        "pleasse" to "please", "thannks" to "thanks", "goodd" to "good",
        "willk" to "will", "ticketsx" to "tickets",
    ),
    "shift held too long" to listOf(
        "THe" to "The", "HEllo" to "Hello", "WHat" to "What", "THis" to "This",
        "OKay" to "Okay", "THanks" to "Thanks", "WEll" to "Well", "SOrry" to "Sorry",
    ),
    "badly mangled" to listOf(
        "instalaltion" to "installation", "accomodatoin" to "accommodation", "intresitng" to "interesting",
        "defintiely" to "definitely", "convesration" to "conversation", "appointmnet" to "appointment",
        "imporatnt" to "important", "diffrenet" to "different",
    ),
    "two mistakes" to listOf(
        "tommorow" to "tomorrow", "definately" to "definitely", "seperate" to "separate",
        "occured" to "occurred", "accomodate" to "accommodate", "embarass" to "embarrass",
        "existance" to "existence", "independant" to "independent", "neccessary" to "necessary",
        "recieved" to "received",
    ),
)

/** Real words and acronyms: must never be changed. */
private val knownWords = listOf(
    "form", "from", "there", "their", "than", "then", "were", "where", "quite", "quiet",
    "lose", "loose", "affect", "effect", "bat", "hat", "two", "advice", "advise", "its",
    "IDs", "TVs", "USA", "NASA", "VMware", "OK", "WiFi",
    // Real words beyond the word list, contractions and possessives.
    "bothers", "nosy", "congrats", "drooped", "curt", "who're", "waiter's", "Where's",
)

/** Names and slang the dictionary may not know: ideally left alone. */
private val unknownButValid = listOf(
    "Obtainium", "OpenBubbles", "Ximena", "Unihertz", "Plektra", "SwiftKey", "gonna", "wanna",
    "tbh", "imo", "btw", "yall", "lol", "omw", "Niagara", "Sami", "Mennad", "bf", "xo", "ur",
)

private fun typoBenchmark(world: Realistic) {
    fun correct(word: String) = corrector(world).check(word, "", null)?.word ?: word
    val report = StringBuilder("\nTYPO BENCHMARK\n")
    var total = 0
    var fixed = 0
    typos.forEach { (group, cases) ->
        var groupFixed = 0
        val misses = ArrayList<String>()
        cases.forEach { (typo, expected) ->
            val result = correct(typo)
            if (if (group == "shift held too long") result == expected else result.equals(expected, ignoreCase = true)) groupFixed++
            else misses += "$typo→$result (want $expected)"
        }
        total += cases.size
        fixed += groupFixed
        report.append("  %-22s %2d/%2d  %s\n".format(group, groupFixed, cases.size, misses.joinToString(", ")))
    }
    val changedKnown = knownWords.mapNotNull { word -> correct(word).takeIf { it != word }?.let { "$word→$it" } }
    val changedUnknown = unknownButValid.mapNotNull { word -> correct(word).takeIf { it != word }?.let { "$word→$it" } }
    report.append("  TOTAL typos fixed       $fixed/$total\n")
    report.append("  known words changed     ${changedKnown.size}/${knownWords.size} $changedKnown\n")
    report.append("  names/slang changed     ${changedUnknown.size}/${unknownButValid.size} $changedUnknown\n")
    println(report)
    same(changedKnown, emptyList<String>(), "Known words must never be changed")
    same(changedUnknown, emptyList<String>(), "Names and slang must be left alone")
    must(fixed >= MIN_FIXED, "Benchmark regressed: $fixed/$total")
}

private fun sentenceAware(world: Realistic) {
    val corrector = corrector(world)
    fun review(before: String, word: String, after: String): String? {
        if (after == END) {
            val text = (if (before == START) "" else "$before ") + word + "."
            return corrector.review(text, false, null, word, true)?.fix
        }
        val text = (if (before == START) "" else "$before ") + "$word $after "
        return corrector.review(text, false, EndedWord(word, null), after, true)?.fix
    }
    val fixes = listOf(
        Triple("get", "dome", "food") to "some", Triple("bring", "dome", "water") to "some",
        Triple("have", "dome", "coffee") to "some", Triple("need", "dome", "help") to "some",
        Triple("letter", "form", "the") to "from", Triple("away", "form", "home") to "from",
        Triple("came", "form", "the") to "from", Triple("email", "form", "my") to "from",
        Triple("i", "lime", "it") to "like", Triple("i", "lime", "this") to "like",
        Triple("very", "food", "idea") to "good", Triple("so", "food", "morning") to "good",
        Triple("a", "food", "job") to "good", Triple("at", "hone", "now") to "home",
        Triple("go", "hone", "and") to "home", Triple("the", "sane", "time") to "same",
        Triple("a", "food", "time") to "good", Triple("from", "hone", "and") to "home",
        Triple(START, "Dome", "food") to "Some", Triple("get", "dome", END) to "some",
        Triple("go", "hone", END) to "home", Triple("be", "herr", END) to "here",
        Triple("at", "wor", END) to "work", Triple("some", "foo", END) to "food",
        Triple("wagged", "it's", "tail") to "its", Triple(START, "Its", "raining") to "It's",
        Triple("lost", "it's", "way") to "its",
    )
    // Correct sentences full of near-miss words: must never be touched.
    val untouched = listOf(
        Triple("the", "dome", "is"), Triple("to", "tie", "the"), Triple("i", "bet", "some"),
        Triple("the", "form", "today"), Triple("take", "off", "the"), Triple("a", "bit", "of"),
        Triple("good", "food", "court"), Triple("i", "live", "here"), Triple("and", "then", "the"),
        Triple("the", "war", "is"), Triple("fast", "food", "is"), Triple("to", "come", "home"),
        Triple("i", "like", "it"), Triple("get", "some", "food"), Triple("from", "the", "store"),
        Triple("at", "home", "now"), Triple("the", "same", "time"), Triple("what", "time", "is"),
        Triple("a", "good", "job"), Triple("fill", "the", "form"), Triple("a", "dome", "shaped"),
        Triple("key", "lime", "pie"), Triple("the", "lime", "is"), Triple("lime", "and", "salt"),
        Triple("hone", "your", "skills"), Triple("the", "hood", "of"), Triple("his", "fork", "and"),
        Triple("the", "word", "is"), Triple("a", "fine", "time"), Triple("the", "sane", "choice"),
        Triple("tone", "of", "voice"), Triple("the", "wood", "is"), Triple("lose", "the", "game"),
        Triple("a", "tame", "cat"), Triple("we", "dine", "at"), Triple("the", "mood", "is"),
        Triple("their", "home", "is"), Triple("my", "food", "is"), Triple("good", "for", "you"),
        Triple(START, "Some", "food"),
        Triple(START, "They", "end"), Triple(START, "They", "first"), Triple("i", "gave", "some"),
        Triple("we", "gave", "the"), Triple("how", "they", "end"), Triple("for", "them", "last"),
        Triple(START, "Call", "the"), Triple(START, "Won", "the"), Triple(START, "Win", "the"),
        Triple(START, "Hey", "want"), Triple(START, "Yet", "some"), Triple(START, "Hand", "the"),
        Triple(START, "Then", "next"), Triple("to", "tie", END), Triple("the", "dome", END),
        Triple("we", "won", END), Triple("i", "gave", END), Triple("they", "came", END),
        // Technical and literary text where a rarer word is meant.
        Triple("current", "directory", "to"), Triple(START, "Directory", "on"), Triple("screen", "width", "to"),
        Triple("option", "off", "causes"), Triple("and", "hooks", "are"), Triple("the", "locale", "to"),
        Triple(START, "Ids", "when"), Triple("the", "debug", "and"), Triple("for", "parity", "with"),
        Triple("be", "handy", "if"), Triple("had", "dined", "that"), Triple("yet", "borne", "a"),
        Triple("village", "inn", "over"), Triple("business", "nor", "anything"),
        // Its / it's used correctly.
        Triple("of", "its", "own"), Triple(START, "It's", "a"), Triple("think", "it's", "time"),
        Triple("and", "its", "products"),
        // Slang is meant as typed.
        Triple("good", "tho", END), Triple("was", "meh", END), Triple("nice", "tho", "lol"),
    )
    val wrong = fixes.mapNotNull { (case, expected) ->
        val got = review(case.first, case.second, case.third)
        if (got == expected) null else "${case.first} ${case.second} ${case.third} -> $got (want $expected)"
    }
    val changed = untouched.mapNotNull { case -> review(case.first, case.second, case.third)?.let { "${case.first} ${case.second} ${case.third} -> $it" } }
    println("\nSENTENCE-AWARE: fixed ${fixes.size - wrong.size}/${fixes.size} $wrong; correct sentences changed ${changed.size}/${untouched.size} $changed")
    // Numen's test had no noisy channel. With it, as in Numen's real pipeline, a word with no pairs to show it fits can lose.
    must(changed.size <= MAX_SENTENCE_CHANGES, "Correct sentences must not change: $changed")
    must(fixes.size - wrong.size >= MIN_SENTENCE_FIXES, "Sentence-aware fixes regressed: $wrong")
}

private fun fieldReports(world: Realistic) {
    fun typed(text: String): String {
        val typist = Typist(world)
        return typist.type("$text ").text.trimEnd()
    }
    val cases = listOf(
        "meet at 3pm ok" to "meet at 3pm ok",
        "see you at 6pm, bye" to "see you at 6pm, bye",
        "only 5km left" to "only 5km left",
        "#nofilter today" to "#nofilter today",
        "#tbt again" to "#tbt again",
        "mail jonh.smith@gmail.com now" to "mail jonh.smith@gmail.com now",
        "open teh.txt please" to "open teh.txt please",
        "rename my_varible later" to "rename my_varible later",
        // A "." that ends the sentence still gets its fix.
        "I went hme. Then" to "I went home. Then",
        // Field reports: a slip on the first letter, and a slip into a day of the week.
        "Vool plan" to "Cool plan",
        "see you on fridat then" to "see you on Friday then",
        // The word before picks between equally likely slips.
        "can you pick up some kilk on the way" to "can you pick up some milk on the way",
        "I will be able fo come" to "I will be able to come",
        // A name written twice in the conversation: a slip on it is fixed.
        "Ximena is here. I told Ximena. Ximwna said hi and I told Ximwna thanks" to
            "Ximena is here. I told Ximena. Ximena said hi and I told Ximena thanks",
        // A capitalized slip on a day, an extra letter on a sentence's first word, short words.
        "See you next Sudnay then" to "See you next Sunday then",
        "Thefy made it" to "They made it",
        "I don't know wat happened" to "I don't know what happened",
        "Why do people ike it" to "Why do people like it",
        // Well-known misspellings have one fix.
        "I did not recieve it" to "I did not receive it",
        "Your arguement is fine" to "Your argument is fine",
        // A first name stays, even one slip from a common word.
        "Ali drank tea" to "Ali drank tea",
        // A missed space next to a name; brand names and code stay.
        "andMary went home" to "and Mary went home",
        "Tomis here" to "Tom is here",
        "my iPhone works" to "my iPhone works",
        "myVariable is set" to "myVariable is set",
        // Field report: texting shorthand, and a far-key swap is no slip.
        "my pref would be tacos" to "my pref would be tacos",
        // Slang and words held for emphasis stay; one extra key is still a slip.
        "ngl im tryna lowkey yeet it rn" to "ngl I'm tryna lowkey yeet it rn",
        "lmk if u wanna hmu tmrw" to "lmk if u wanna hmu tmrw",
        "that was sooo good pleaseee come" to "that was sooo good pleaseee come",
        "we alll went" to "we all went",
        // Spelling mistakes: one vowel for another.
        "we need a sepereate permit" to "we need a separate permit",
        "that is definatly true" to "that is definitely true",
        // Words run together, from the common misspellings list.
        "alot of people like it aswell now" to "a lot of people like it as well now",
        // Doubled letters left out of a long word; a split only where the two words go together.
        "the acomodation was nice" to "the accommodation was nice",
        // A short non-word that both neighbours explain; unclear ones and real words stay.
        "the cat hs fur" to "the cat has fur",
        "I hs a car" to "I hs a car",
        "return the ewe to her" to "return the ewe to her",
        // Contractions follow the sentence.
        "ill be there soon" to "I'll be there soon",
        "ok ill call you" to "ok I'll call you",
        "its raining again" to "it's raining again",
        "dont worry" to "don't worry",
        "thanks for your projects ill quit soon" to "thanks for your projects I'll quit soon",
        "Ok, ill call you" to "Ok, I'll call you",
        "I ll quit now" to "I'll quit now",
        "I don t know" to "I don't know",
        "she fell ill and stayed home" to "she fell ill and stayed home",
        "id like that a lot" to "I'd like that a lot",
        "show your id card please" to "show your id card please",
        "it's messy and its mostly on there" to "it's messy and it's mostly on there",
        "it was it's own thing" to "it was its own thing",
        "the dog wagged its tail" to "the dog wagged its tail",
        "He was very ill" to "He was very ill",
        "Have you got your ID" to "Have you got your ID",
        "an old storage shed" to "an old storage shed",
        // From the issue.
        "a letter form my mom" to "a letter from my mom",
        "get dome food" to "get some food",
        "she is he best" to "she is the best",
        "the dog wagged it's tail" to "the dog wagged its tail",
        "hellohow are you" to "hello how are you",
    )
    val wrong = cases.mapNotNull { (typed, expected) -> typed(typed).takeIf { it != expected }?.let { "$typed -> $it (want $expected)" } }
    println("\nFIELD REPORTS: ${cases.size - wrong.size}/${cases.size} passed")
    wrong.forEach { println("  $it") }
    must(wrong.size <= MAX_FIELD_REPORT_MISSES, "Too many field reports failed: ${wrong.size}")
}

private fun composerFlow(world: Realistic) {
    fun typed(text: String, field: Field = Typist.FIELD) = Typist(world, field = field).type(text)

    // First check.
    same(typed("teh ").text, "the ", "A slip is fixed at Space")
    same(typed("teh,").text, "the,", "A slip is fixed at punctuation")
    same(typed("teh\n").text, "the\n", "A slip is fixed before a new line")
    same(typed("Teh ").text, "The ", "The fix keeps the capital")
    same(typed("THe ").text, "The ", "Shift held too long is fixed")
    run {
        val refusing = FakeWords(Lists(world.words)).also { it.refusedFixes += "teh>the" }
        same(Typist(world, words = refusing).type("teh ").text, "teh ", "A fix held on the strip to forget it is not made again")
        same(Typist(world, words = refusing).type("adn ").text, "and ", "Refusing one fix leaves the other fixes")
        same(Typist(world, words = refusing).type("Teh ").text, "Teh ", "A refused fix stays refused in capitals")
    }
    same(typed("doesnt ").text, "doesn't ", "A missing apostrophe is fixed")

    // Auto-punctuation.
    same(typed("hello ,").text, "hello,", "A comma moves in front of a typed space")
    same(typed("hello  ,").text, "hello  ,", "Only the space straight after the word moves")
    fun noPunctuation() = Typist(world).also { it.composer.autoPunctuation = { false } }
    same(noPunctuation().type("hello ,").text, "hello ,", "With the setting off, a typed space stays")
    same(typed("hello ").quickSpace().text, "hello. ", "Two quick spaces type a full stop")
    same(noPunctuation().type("hello ").quickSpace().text, "hello  ", "With the setting off, two quick spaces stay spaces")

    // Capitals. A full stop ends a sentence only with a space after it, and a word that adds its own space counts that space.
    run {
        val sentences = Field.of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 0)
        fun capitals() = Typist(world, autocorrect = null, field = sentences, autoCapitalise = true)
        same(capitals().type("hi. there").text, "Hi. There", "A letter after a full stop and a space is a capital")
        same(capitals().type("see atlas.com").text, "See atlas.com", "A letter straight after a full stop stays in lowercase")
        same(capitals().type("see file.txt").text, "See file.txt", "A file name stays in lowercase")
        same(capitals().type("use e.g").text, "Use e.g", "The letter after the first full stop of e.g stays in lowercase")
        same(capitals().type("use e.g. this").text, "Use e.g. this", "A word after e.g. stays in lowercase")
        same(capitals().type("use i.e. this").text, "Use i.e. this", "A word after i.e. stays in lowercase")
        same(capitals().type("Hi.").swipe("there").text, "Hi. There ", "A swiped word that adds its space after a full stop is a capital")
        same(capitals().type("use e.g.").swipe("this").text, "Use e.g. this ", "A swiped word that adds its space after e.g. stays in lowercase")
        same(capitals().type("Hi.").tap("a").text, "Hi. A ", "A tap that adds its space after a full stop is a capital")
        same(capitals().type("use e.g.").tap("a").text, "Use e.g. a ", "A tap that adds its space after e.g. stays in lowercase")
    }
    same(SwipeTyping.capitalise("there", SwipeTyping.beforeSuggestion("Hi.", ""), 0, false, true), "There",
        "A pick straight after a full stop gets a space, so it is a capital")
    same(SwipeTyping.capitalise("this", SwipeTyping.beforeSuggestion("use e.g.", ""), 0, false, true), "this",
        "A pick straight after e.g. gets a space and stays in lowercase")
    same(SwipeTyping.capitalise("there", SwipeTyping.beforeSuggestion("Hi. ", ""), 0, false, true), "There",
        "A pick after a typed space is a capital")
    same(SwipeTyping.capitalise("com", SwipeTyping.beforeSuggestion("atlas.", "co"), 0, false, true), "com",
        "A pick that finishes letters after a full stop adds no space and stays in lowercase")

    // A word deleted straight after it was typed is not learned.
    typed("blorptastic ").also {
        it.backspace()
        same(it.words.typed.contains("blorptastic"), false, "Deleting only the space keeps the word waiting")
        it.type(" next ")
        must(it.words.typed.contains("blorptastic"), "A word that is still whole after its space came back is learned")
    }
    typed("blorptastic ").also {
        it.backspace().backspace().type(" next ")
        must(!it.words.typed.contains("blorptastic"), "A held word whose letters were deleted must not be learned")
    }
    Typist(world, autocorrect = null).type("blorptastic ").also {
        must(it.words.typed.contains("blorptastic"), "Without autocorrect the word is learned at its space")
        it.backspace()
        same(it.words.untyped.isEmpty(), true, "Deleting only the space takes nothing back")
        it.backspace()
        same(it.words.untyped, listOf("blorptastic"), "Deleting into the word takes back its use")
    }

    // Backspace undo.
    same(typed("teh ").backspace().text, "teh", "Backspace straight after a fix restores what was typed")
    same(typed("teh, ").also { it.backspace() }.text, "the,", "Backspace after more keys is a normal Backspace")
    same(typed("teh,").backspace().text, "teh,", "Undo keeps the punctuation")
    same(typed("teh ").backspace().type(" ").text, "teh ", "The restored word stays when it ends again")
    same(typed("teh ").backspace().type(" and teh ").text, "teh and teh ", "An undone word stays uncorrected for the session")
    same(typed("get dome food ").backspace().text, "get dome food ", "Undo of the second check restores all that was typed")
    typed("get dome food ").also {
        same(it.externalChanges, 0, "The app's report of a second-check fix is not taken for an outside change")
        same(it.quickSpace().text, "get some food. ", "A quick second Space after a second-check fix makes a full stop")
    }
    typed("she is he best ").also {
        same(it.text, "she is the best ", "A second-check fix can change the length of the text")
        same(it.externalChanges, 0, "A report for a second-check fix that moved the cursor is not an outside change")
        same(it.backspace().text, "she is he best ", "Backspace restores a second-check fix that moved the cursor")
    }
    run {
        val typist = Typist(world).type("recieve ").backspace().swipe("it").type("then recieve ")
        same(typist.text, "recieve it then recieve ", "A restored word stays uncorrected when the next word is swiped")
        val tapped = Typist(world).type("recieve ").backspace().tap("a").type("then recieve ")
        same(tapped.text, "recieve a then recieve ", "A restored word stays uncorrected when the next word is a tap")
        same(Typist(world).type("recieve ").backspace().backspace().swipe("it").type("then recieve ").text,
            "reciev it then receive ", "A restored word that the user changed gets no protection")
    }
    run {
        val first = Typist(world)
        first.type("teh ").backspace().type(" ")
        same(world.learned.isEmpty(), true, "A word kept once is not learned yet")
        val autocorrect = first.autocorrect!!
        autocorrect.newField()
        val second = Typist(world, autocorrect)
        second.type("teh ").backspace().type(" ")
        same(world.learned.toList(), listOf("teh"), "A word kept on two occasions is learned")
        world.learned.clear()
    }
    run {
        val typist = typed("teh ")
        typist.backspace().backspace().type("n ")
        same(typist.text, "ten ", "After undo, the word can be edited")
        same(world.learned.toList(), emptyList<String>(), "An undo and then an edit keeps nothing")
        same(typist.type("teh ").text, "ten the ", "An edited word gets no protection")
    }

    // Learning.
    same(typed("teh recieve ").words.typed.toList(), listOf("the", "receive"), "The fixed word is learned, not the typo")
    same(typed("get dome food ").words.typed.toList().contains("dome"), true, "A real word is learned as before")
    typed("ths is the zorblax now ").also {
        same(it.text, "this is the zorblax now ", "The next word can fix a made-up word")
        same(it.words.typed.contains("this"), true, "The fixed word is learned when the second check changes it")
        same(it.words.typed.contains("ths"), false, "A typo that the next word fixes is not learned")
        same(it.words.typed.contains("zorblax"), true, "A made-up word that stays is learned once the next word ends")
    }
    Typist(world).type("ths").swipe("is").also {
        same(it.text, "this is ", "A swiped word can fix the typed word before it")
        same(it.words.typed.contains("ths"), false, "A typo that a swiped word fixes is not learned")
        same(it.words.typed.contains("this"), true, "The fixed word is learned when a swiped word changes it")
    }
    Typist(world).type("zorblax").swipe("is").type("now ").also {
        same(it.text, "zorblax is now ", "A made-up word before a swipe stays")
        same(it.words.typed.contains("zorblax"), true, "A made-up word before a swipe is learned once the next word ends")
    }
    run {
        val lists = Lists(world.words)
        Typist(world, null, lists = lists).type("ths ")
        val typist = Typist(world, world.autocorrect(lists), lists = lists).type("ths is the zorblax now ")
        same(typist.text, "this is the zorblax now ", "A word seen once can still be fixed by the next word")
        same(typist.words.typed.contains("ths"), false, "A word seen once waits for the next word before it is learned")
    }

    // A swipe that lands right after another one may be its other half.
    fun firstSwipe(word: String, margin: Float?) = Typist(world, null).also {
        it.composer.liftOffMillis = 90
        it.words.readings += word to margin
        it.swipe(word)
    }
    firstSwipe("so", 5f).also {
        it.words.readings += "soup" to 20f
        it.swipeAfter(100)
        same(it.text, "soup ", "A close second stroke joins the first when that reads more clearly")
    }
    firstSwipe("the", 30f).also {
        it.words.readings += "theatre" to 2f
        it.words.readings += "and" to 9f
        it.swipeAfter(100)
        same(it.text, "the and ", "A close second stroke stays a new word when joining reads less clearly")
        same(it.words.readings.size, 0, "Both readings of a close second stroke are made")
    }
    firstSwipe("the", 30f).also {
        it.words.readings += "theatre" to 30f
        it.words.readings += "and" to 9f
        it.swipeAfter(100)
        same(it.text, "the and ", "Joining must read more clearly, not just as clearly")
    }
    firstSwipe("the", 5f).also {
        it.words.readings += "" to 40f
        it.words.readings += "and" to 9f
        it.swipeAfter(100)
        same(it.text, "the and ", "A joined reading that finds no word does not replace the first word")
    }
    firstSwipe("the", 5f).also {
        it.words.readings += "and" to 9f
        it.swipeAfter(151)
        same(it.text, "the and ", "A second stroke after the check window stays a new word")
        same(it.words.readings.size, 0, "A stroke after the check window is read only once")
    }
    firstSwipe("the", null).also {
        it.words.readings += "and" to 9f
        it.swipeAfter(100)
        same(it.text, "the and ", "A first word with no clarity score cannot be joined")
        same(it.words.readings.size, 0, "A stroke that cannot be joined is read only once")
    }
    firstSwipe("the", 5f).also {
        it.composer.liftOffMillis = 0
        it.words.readings += "and" to 9f
        it.swipeAfter(100)
        same(it.text, "the and ", "With slip recovery off, strokes never join")
        same(it.words.readings.size, 0, "With slip recovery off, a close stroke is read only once")
    }

    // What the next-word predictor learns.
    fun pairs(typist: Typist) = typist.words.pairs.toList()
    same(pairs(typed("one two three four ")), listOf("^ one", "one two"), "A pair counts once two more words have ended")
    same(pairs(typed("good morning good morning good morning good ")).count { it == "good morning" }, 2, "Repeats count each time")
    same(pairs(typed("see teh cat sat down now ")), listOf("^ see", "see the", "the cat", "cat sat"), "A fix is learned, not the typo")
    same(pairs(typed("get dome food and more ")), listOf("^ get", "get some", "some food"), "A second-check fix is learned, not the typo")
    same(pairs(typed("see teh ").backspace().type(" cat sat down ")), listOf("^ see", "see teh", "teh cat"), "A word the user put back is the word they kept")
    same(pairs(typed("one two zzz").backspace().backspace().backspace().type("three four five ")),
        listOf("^ one", "one two", "two three"), "A word the user deleted is not learned")
    same(pairs(Typist(world).swipe("one").swipe("two").swipe("three").swipe("four")), listOf("^ one", "one two"), "Swiped words are learned")
    same(pairs(Typist(world).type("one ").swipe("two").type("three four ")), listOf("^ one", "one two"), "Typed and swiped words go together")
    same(pairs(Typist(world).also { it.words.lookAlike = "we're" }.swipe("were").swipe("going").also { it.words.lookAlike = null }.swipe("to").swipe("go")),
        listOf("^ we're", "we're going"), "A look-alike that changed is learned as it stands")
    run {
        // No word follows in these, so only the edit itself can tell the learner about the new text.
        fun lookAlike() = Typist(world).also { it.words.lookAlike = "we're" }.swipe("were").swipe("going")
        val left = lookAlike()
        same(left.text, "we're going ", "The look-alike changed behind the swiped word")
        left.composer.stop()
        same(pairs(left), listOf("^ we're", "we're going"), "Leaving the field learns the look-alike behind a swiped word as it stands")

        val sent = lookAlike()
        sent.editor.text = ""
        sent.composer.selectionChanged(0, 0)
        sent.settle()
        same(pairs(sent), listOf("^ we're", "we're going"), "An app that sends the text learns the look-alike behind a swiped word as it stands")

        val reviewed = Typist(world).type("get dome ").swipe("food")
        same(reviewed.text, "get some food ", "The second check changed the word behind the swiped word")
        reviewed.composer.stop()
        same(pairs(reviewed), listOf("^ get", "get some", "some food"), "A second-check fix behind a swiped word is learned, not the typo")

        val undone = Typist(world).type("get dome ").swipe("food").backspace()
        same(undone.text, "get dome food ", "Backspace put the typed word back")
        undone.composer.stop()
        same(pairs(undone), listOf("^ get", "get dome", "dome food"), "A word put back behind a swiped word is the word the user kept")
    }
    run {
        val privateField = Field.of(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        val typist = typed("one two three four five ", privateField)
        typist.composer.stop()
        same(pairs(typist), emptyList<String>(), "Nothing is learned from a private field")
        val swiping = Typist(world, field = privateField).swipe("one").swipe("two").swipe("three").swipe("four")
        swiping.editor.enters = Editor.Enter.Action
        swiping.composer.enter(EnterKey.Press.Send)
        swiping.settle()
        same(pairs(swiping), emptyList<String>(), "Nothing swiped or sent from a private field is learned")
        val password = Field.of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, 0)
        same(pairs(typed("one two three four five ", password)), emptyList<String>(), "Nothing is learned from a password field")
    }
    run {
        val send = Typist(world)
        send.editor.enters = Editor.Enter.Action
        send.type("good morning")
        send.composer.enter(EnterKey.Press.Send)
        send.settle()
        same(pairs(send), listOf("^ good", "good morning"), "Enter that sends counts every word, the last one too")

        val left = Typist(world).type("good morning ")
        same(pairs(left), emptyList<String>(), "The last two words wait")
        left.composer.stop()
        same(pairs(left), listOf("^ good", "good morning"), "Leaving the field counts the words that waited")

        val emptied = Typist(world).type("good morning ")
        emptied.editor.text = ""
        emptied.composer.selectionChanged(0, 0)
        emptied.settle()
        same(pairs(emptied), listOf("^ good", "good morning"), "An app that empties its field has sent the words")

        val moved = Typist(world).type("good mornin ")
        // The fake editor has no text after the cursor, so a cursor moved back shows as shorter text.
        moved.editor.text = "good"
        moved.composer.selectionChanged(4, 4)
        moved.settle()
        same(pairs(moved), emptyList<String>(), "Moving the cursor back may be to change a word, so the waiting words do not count")
        moved.composer.stop()
        same(pairs(moved), emptyList<String>(), "Words dropped by a cursor move do not come back")

        val pasted = Typist(world)
        pasted.composer.command(true, true) {
            pasted.editor.text = "words from somewhere else entirely "
            true
        }
        pasted.settle()
        pasted.type("and then more words ")
        same(pairs(pasted), listOf("and then"), "Text that the user did not type does not count")
    }

    // Words that count as the user's own.
    run {
        val lists = Lists(world.words)
        fun corrected(text: String) = Typist(world, world.autocorrect(lists), lists = lists).type(text).text
        Typist(world, null, lists = lists).type("teh ")
        same(corrected("teh "), "the ", "A word typed once where no fix runs is not learned, so it is still fixed")
        repeat(2) { Typist(world, null, lists = lists).type("teh ") }
        same(corrected("teh "), "teh ", "A word used three times is the user's own")
        same(lists.habits.active("TEH"), true, "The user's own words count in any case")
        same(corrected("get dome food "), "get some food ", "Only a learned word is protected from the second check")
        lists.habits.add("dome")
        same(corrected("get dome food "), "get dome food ", "A word that the user added stays in the second check")
    }

    // The Learn my words setting.
    run {
        val store = KeptOnceStore.inMemory()
        val saved = ArrayList<String>()
        val autocorrect = world.wired(Autocorrect(store, { saved += it }, learning = { false }))
        val first = Typist(world, autocorrect).type("teh ").backspace().type(" ")
        same(store.contains("teh"), false, "With learning off, a kept word is not noted")
        same(first.type("and teh ").text, "teh and teh ", "With learning off, an undone word still stays for the session")
        autocorrect.newField()
        Typist(world, autocorrect).type("teh ").backspace().type(" ")
        same(saved.toList(), emptyList<String>(), "With learning off, a word kept on two occasions is not saved")
    }

    // The English spelling setting.
    run {
        val american = world.autocorrect(Lists(world.words, spelling = { PhoneWords.spelled(it, true) }))
        fun inAmerican(text: String) = Typist(world, american).type(text)
        same(typed("recogize ").text, "recognise ", "A fix follows the British setting")
        same(typed("Recogize ").text, "Recognise ", "A respelled fix keeps the capital")
        same(typed("travling ").text, "travelling ", "A scored fix follows the British setting")
        same(typed("orginize ").text, "organise ", "A known misspelling stays British on the British setting")
        same(inAmerican("orginize ").text, "organize ", "A fix follows the American setting")
        same(inAmerican("recogize ").text, "recognize ", "A known misspelling stays American on the American setting")
        same(inAmerican("travling ").text, "traveling ", "A scored fix follows the American setting")
        same(typed("recogize ").words.typed.toList(), listOf("recognise"), "The word learned is the one written")
        // Spelling a fix the user's way can give back what was typed. That is no fix.
        val circular = world.autocorrect(Lists(world.words, spelling = { if (it == "the") "teh" else if (it == "some") "dome" else it }))
        same(Typist(world, circular).type("teh ").text, "teh ", "A first check that gives back the word is no fix")
        same(Typist(world, circular).type("get dome food ").backspace().text, "get dome food", "A second check that gives back the word offers no undo")
    }
    same(typed("teh ", Field.of(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)).text, "the ", "A private field is corrected")
    same(typed("teh recieve ", Field.of(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)).words.typed.toList(), emptyList<String>(), "Nothing is learned from a private field")

    // Where not to correct.
    for (variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS, InputType.TYPE_TEXT_VARIATION_PERSON_NAME)) {
        same(typed("teh ", Field.of(InputType.TYPE_CLASS_TEXT or variation, 0)).text, "teh ", "Variation $variation must not be corrected")
    }
    same(typed("mail alex@domian ").text, "mail alex@domian ", "An email address in normal text stays")
    same(typed("see https://exmple.com/wrld ").text, "see https://exmple.com/wrld ", "A link in normal text stays")

    // Enter.
    run {
        val send = Typist(world)
        send.editor.enters = Editor.Enter.Action
        send.type("teh")
        send.composer.enter(EnterKey.Press.Send)
        send.settle()
        same(send.text, "teh", "Nothing is changed before an app acts on Enter")
        same(send.editor.enterPresses, 1, "Enter reaches the app")
    }
    same(typed("get dome\nfood ").text, "get dome\nfood ", "The second check does not reach over a new line")

    // Off, and not loaded.
    same(Typist(world, null).type("teh ").text, "teh ", "With the setting off, nothing changes")
    val waiting = world.autocorrect().also { it.corrector = null }
    same(Typist(world, waiting).type("teh ").text, "teh ", "Before the data loads, nothing changes")

    // Swiped words.
    same(Typist(world).swipe("teh").type(" ").text, "teh ", "A swiped word is never changed")
    same(Typist(world).swipe("dome").type(" food ").text, "dome food ", "A swiped word is not a word to review")
    same(Typist(world).type("get ").swipe("dome").type("food ").text, "get dome food ", "A swiped word between typed words stays")
    same(Typist(world).type("get dome ").swipe("food").text, "get some food ", "A swiped word is context for the typed word before it")
    run {
        val typist = Typist(world).type("get dome ").swipe("food")
        typist.backspace()
        same(typist.text, "get dome food ", "Backspace after that fix restores the typed word and keeps the swiped word")
    }
    run {
        val typist = Typist(world).swipe("hello")
        typist.composer.backspace(false)
        typist.settle()
        typist.backspace()
        typist.type("p ")
        same(typist.words.corrected.toList(), listOf("hellp"), "Fixing a swiped word by hand still teaches the swipe engine")
        same(typist.text, "hellp ", "A word being fixed by hand is not corrected")
    }

    // Vibration.
    same(typed("teh ").vibrations, 1, "A fix at Space vibrates")
    same(typed("teh,").vibrations, 1, "A fix at punctuation vibrates")
    same(typed("teh\n").vibrations, 1, "A fix before a new line vibrates")
    same(typed("teh").also { it.composer.symbol("."); it.settle() }.vibrations, 1, "A fix at a symbol vibrates")
    same(typed("get dome food ").vibrations, 1, "A second-check fix vibrates")
    same(typed("hello there ").vibrations, 0, "A word that stays does not vibrate")
    same(typed("teh ").backspace().vibrations, 1, "Backspace that restores a word does not vibrate")
    same(Typist(world, null).type("teh ").vibrations, 0, "With the setting off, nothing vibrates")
    same(Typist(world).type("ths").swipe("is").also { same(it.text, "this is ", "A swiped word fixes the word before it") }.vibrations, 0,
        "A fix that lands as a swipe ends does not vibrate")
    same(Typist(world).type("get dome ").swipe("food").also { same(it.text, "get some food ", "A swiped word is context") }.vibrations, 0,
        "A second-check fix that lands as a swipe ends does not vibrate")
    run {
        fun swipedLookAlike() = Typist(world).also { it.words.lookAlike = "we're" }.swipe("were")
        swipedLookAlike().type("going ").also {
            same(it.text, "we're going ", "A typed word can change the swiped look-alike before it")
            same(it.vibrations, 1, "A look-alike that a typed word changes vibrates")
        }
        swipedLookAlike().swipe("going").also {
            same(it.text, "we're going ", "A swiped word can change the swiped look-alike before it")
            same(it.vibrations, 0, "A look-alike that changes as a swipe ends does not vibrate")
        }
    }
}

/** Where suggestions are allowed the bar always has words, and popular sentence starts fill it when nothing else can. */
/** In the interactive guide, a word that comes out different has the guide's word on the strip, one touch away. */
private fun theGuideOffersItsWord(world: Realistic) {
    fun guide(miss: Boolean = false) = Typist(world, null).also {
        it.composer.intended = { "to" }
        it.composer.nearMiss = { miss }
    }
    val swiped = guide().swipe("tp")
    same(swiped.bar.take(2), listOf("tp", "to"), "A swipe that came out different has the guide's word second")
    same(swiped.choose(1).text, "to ", "One touch puts the guide's word in")
    same(guide().swipe("to").bar, listOf("to"), "A swipe that came out right has nothing added")
    same(guide(miss = true).swipe("to").bar, listOf("to"), "A near miss needs a second guess to swap with")
    same(guide().type("tp").bar.firstOrNull(), "to", "Typed letters that are not the guide's word have it on the strip")
    same(guide().type("tp").choose(0).text, "to ", "One touch puts it in place of the letters")
    same(guide().type("to").bar.contains("to"), false, "Letters that are the guide's word need nothing more")
    same(Typist(world, null).swipe("tp").bar, listOf("tp"), "Outside the guide nothing is added")
}

private fun suggestionBar(world: Realistic) {
    val sentences = Field.of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 0)
    val capital = listOf("I", "The", "Hello", "You")
    val inside = listOf("I", "the", "hello", "you")
    fun fakeWords() = FakeWords(Lists(world.words))
    fun typist(words: FakeWords = fakeWords(), field: Field = sentences, capitals: Boolean = true) =
        Typist(world, null, field, autoCapitalise = capitals, words = words)

    // The models are still loading, so completions wait.
    run {
        val words = fakeWords().also { it.completions = null }
        val loading = typist(words)
        same(loading.bar, capital, "While the models load, an empty field shows starts at once")
        loading.type("he")
        same(loading.bar, inside, "While the models load, typing keeps starts on the bar")
        loading.choose(1)
        same(loading.text, "He the ", "A start can be chosen while the models load")
        words.completions = listOf("real")
        loading.type("x")
        same(loading.bar, listOf("real"), "Real words take over once the models answer")
        val answered = typist(fakeWords().also { it.completions = null })
        answered.words.unanswered.removeLast()(listOf("real", "words"))
        same(answered.bar, listOf("real", "words"), "A completion that was waiting replaces the starts")
    }
    same(typist(fakeWords().also { it.starts = emptyList() }).bar, listOf("I", "The", "Hello"), "Before the word lists load, the few starts show")

    // No word fits the text.
    run {
        val typist = typist().type("Xqzv")
        same(typist.bar, inside, "A typed word with no suggestions shows starts")
        typist.choose(1)
        same(typist.text, "Xqzv the ", "A start goes after the typed word and keeps it")
        same(typist.bar, inside, "The bar goes on with starts after the choice")
    }

    // Selected text.
    run {
        val typist = typist().type("Hello world. ").select(6, 11)
        same(typist.bar, inside, "Selected text shows starts, in the case the text before it asks for")
        typist.choose(1)
        same(typist.text, "Hello the. ", "A start replaces the selected word and adds no space before a full stop")
        same(typist().type("Hello there").select(0, 11).also { same(it.bar, capital, "A selection at the start is a sentence start") }.choose(1).text, "The ",
            "A start replaces all the selected text")
    }

    // The cursor is against a word.
    run {
        val words = fakeWords().also { it.completions = listOf("wrong") }
        val typist = typist(words).type("Good morning")
        same(typist.select(5).bar, inside, "A cursor just before a word shows starts")
        typist.choose(1)
        same(typist.text, "Good the morning", "A start goes in with a space on each side of it where needed")
        val inWord = typist(words).type("Good").select(2)
        same(inWord.bar, inside, "A cursor inside a word shows starts")
        same(inWord.choose(0).text, "Go I od", "A start splits the word instead of joining it")
    }

    // The app doesn't say what is in its field.
    run {
        val typist = typist()
        typist.editor.unreadable = true
        typist.composer.start(typist.field, -1, -1)
        typist.settle()
        same(typist.bar, capital, "A field that hides its text still shows starts")
        same(typist.choose(1).text, "The ", "A start goes into a field that hides its text")
        same(typist.bar, capital, "The bar stays full after that")
    }

    // A swipe that found nothing.
    run {
        val words = fakeWords().also { it.completions = listOf("real") }
        val typist = typist(words).type("Hello ")
        words.completions = emptyList()
        same(typist.swipe("").bar, inside, "A swipe that finds nothing leaves starts on the bar")
    }

    // Words stay up while a swipe is read and after the keyboard has finished a key.
    run {
        val words = fakeWords().also { it.completions = listOf("real") }
        val typist = typist(words).type("Hello ")
        words.holdSwipes = true
        same(typist.swipe("there").bar, listOf("real"), "The bar keeps its words while a swipe is read")
        words.unrecognised.removeFirst()()
        typist.settle()
        same(typist.bar, listOf("there"), "A swiped word then takes over")
    }

    // Enter sent the message, but nothing else happened.
    run {
        val words = fakeWords().also { it.completions = listOf("real") }
        val typist = typist(words).type("Hello")
        typist.editor.enters = Editor.Enter.Action
        typist.composer.enter(EnterKey.Press.Send)
        words.completions = emptyList()
        typist.settle()
        same(typist.bar, inside, "When Enter changes nothing, the bar is not left empty")
    }

    // A reset that nothing follows.
    run {
        val words = fakeWords()
        val typist = typist(words).type("Hello ")
        words.holdSwipes = true
        typist.swipe("there")
        typist.editor.text += "x"
        words.unrecognised.removeFirst()()
        typist.settle()
        same(typist.bar, inside, "A swipe that the text outran leaves words on the bar")
        val refused = typist(fakeWords()).type("Hello ")
        refused.editor.refuses = true
        refused.composer.type("a")
        refused.settle()
        same(refused.bar, inside, "An edit that the app refused leaves words on the bar")
    }

    // A word held on the strip to forget it.
    run {
        val typist = typist(fakeWords())
        val shown = typist.bar
        same(typist.composer.shownWord(1), shown[1] to "", "The held place names the word under the finger")
        typist.composer.hide(shown[0])
        same(typist.bar, shown.drop(1), "A held word leaves the bar while its Undo shows")
        typist.composer.unhide(shown[0])
        same(typist.bar, shown, "Undo puts the word back in its place")
        typist.composer.hide(shown[0])
        same(typist.choose(0).text, shown[1] + " ", "A touch after a held word picks the word now in that place")
    }
    run {
        val words = fakeWords().also { it.completions = listOf("the", "then") }
        val typist = typist(words).type("teh")
        same(typist.composer.shownWord(0), "the" to "Teh", "A held fix names the letters it would replace")
    }

    // Every start was blocked, or the search words were.
    same(typist(fakeWords().also { it.blocked += "the" }).bar, listOf("I", "Hello", "You"), "A blocked word is not offered")
    run {
        val search = Field.of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, 0)
        same(typist(field = search).bar, listOf("What", "How", "Where"), "An empty search bar keeps its own starters")
        same(typist(field = search, words = fakeWords().also { it.blocked += listOf("what", "how", "where") }).bar, listOf("I", "the", "hello", "you"),
            "With the search starters blocked, the bar still has words")
    }

    // A new field does not show what the last one offered.
    run {
        val words = fakeWords().also { it.completions = listOf("old") }
        val typist = typist(words)
        same(typist.bar, listOf("old"), "The first field shows its own words")
        words.completions = null
        typist.composer.start(sentences, 0, 0)
        same(typist.bar, listOf("old"), "A restart of the same field keeps its words until new ones arrive")
        typist.editor.fieldId = 2
        typist.composer.start(sentences, 0, 0)
        same(typist.bar, capital, "A new field shows starts, not the last field's words")
    }

    // Capitals.
    same(typist().bar, capital, "An empty field starts a sentence")
    same(typist().type("Hi. ").bar, capital, "A full stop starts a sentence")
    same(typist().type("Hi\n").bar, capital, "A new line starts a sentence")
    same(typist().type("well ").bar, inside, "Mid-sentence, only I keeps its capital")
    same(typist(fakeWords().also { it.starts = listOf("I'm", "I've", "the") }).type("well ").bar, listOf("I'm", "I've", "the"), "Mid-sentence, I'm keeps its capital")
    same(typist(field = Field.of(InputType.TYPE_CLASS_TEXT, 0)).bar, inside, "A field without sentence capitals gets none")
    same(typist(capitals = false).bar, inside, "With the setting off, nothing gets a capital")

    // Fields where suggestions are off.
    val noBar = listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
    for (variation in noBar) {
        val field = Field.of(InputType.TYPE_CLASS_TEXT or variation, 0)
        val words = fakeWords().also { it.completions = null }
        val typist = typist(words, field = field)
        same(typist.bar, emptyList<String>(), "Variation $variation shows nothing in an empty field")
        typist.type("abc").select(1, 2)
        same(typist.bar, emptyList<String>(), "Variation $variation shows nothing for a selection")
        same(typist.select(1).bar, emptyList<String>(), "Variation $variation shows nothing inside a word")
        same(typist.choose(0).text, "abc", "Variation $variation has no words to touch")
    }
    val addressBar = Field.of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 0)
    same(addressBar.suggestions, true, "An address bar that asks for no suggestions must still get the bar")
    same(typist(field = addressBar).bar, inside, "An empty address bar shows words")
    same(typist(field = Field.None).bar, emptyList<String>(), "A field with no text words shows nothing")
    same(typist(field = Field.of(InputType.TYPE_CLASS_NUMBER, 0)).bar, emptyList<String>(), "A number field shows nothing")

    // Touching a start.
    run {
        val words = fakeWords()
        val typist = typist(words).choose(1)
        same(typist.text, "The ", "A start goes in with the space after it")
        same(words.typed.toList(), emptyList<String>(), "A start teaches no saved word")
        same(typist.type("cat ").text, "The cat ", "Typing carries on after a start")
    }
    run {
        val words = fakeWords()
        val typist = typist(words)
        typist.composer.choose(1)
        typist.type(",")
        same(typist.text, "The,", "Punctuation takes the place of a start's space, as it does for any word")
    }
    run {
        val words = fakeWords()
        val typist = typist(words).swipe("hello")
        typist.backspace()
        typist.backspace()
        same(typist.choose(1).text, "Hell the ", "A start can follow a swiped word that is being fixed")
        same(words.corrected.toList(), emptyList<String>(), "A start says nothing about the swipe before it")
        same(words.typed.toList(), emptyList<String>(), "A start does not save what was being fixed")
    }
    run {
        val typist = typist().type("Xqzv")
        same(typist.choose(1).words.typed.toList(), emptyList<String>(), "A start does not save the word typed before it")
    }
    run {
        val typist = typist()
        typist.editor.text = "changed"
        same(typist.choose(1).text, "changed", "A start offered for other text does nothing")
    }
    run {
        val typist = typist()
        val before = typist.bar
        must(before.isNotEmpty(), "Starts are on the bar")
        same(typist.type("H").text, "H", "A key types as it does with any other words on the bar")
    }
}

private fun smallerChecks() {
    // A changed word must feel different from a swipe tick, at each vibration setting.
    for (preset in HapticPreset.entries) {
        must(preset.wordTimings.sum() >= preset.durationMillis * 2, "A changed word lasts at least twice a tick: $preset")
        must(preset.wordAmplitudes.first() > preset.amplitude, "A changed word starts stronger than a tick: $preset")
        must(preset.wordAmplitudes.all { it in 1..255 }, "A changed word stays in the vibrator's range: $preset")
    }

    // NoisyChannel.
    must(NoisyChannel.cost("vrown", "brown", near) < NoisyChannel.cost("prown", "brown", near), "A neighbouring key is a cheaper slip")
    must(NoisyChannel.cost("teh", "the", near) <= 0.5, "Swapped letters are cheap")
    must(NoisyChannel.cost("acomodation", "accommodation", near) < 1.0, "Missed doubles are cheap")
    run {
        val pairs = mapOf("some milk" to 0, "some kill" to 0, "up some" to 50, "some" to 0, "buy milk" to 90)
        val channel = NoisyChannel({ listOf("milk", "kill", "silk") }, near, { a, b -> pairs["$a $b"] ?: 0 }, { a, b, c -> if ("$a $b $c" == "up some milk") 60 else 0 })
        same(channel.fixForUnknown("kilk", "some", "up"), "milk", "The words around pick between equally likely slips")
    }
    same(NoisyChannel({ listOf("cat", "car") }, near, { _, _ -> 0 }, { _, _, _ -> 0 }).fixForUnknown("cax", "the", null), null, "No clear winner means no fix")
    run {
        val pairs = mapOf("from my" to 400, "a letter" to 80, "letter from" to 60)
        val channel = NoisyChannel({ listOf("from", "foam") }, near, { a, b -> pairs["$a $b"] ?: 0 }, { _, _, _ -> 0 })
        same(channel.fixForRealWord("form", "letter", "a", "my"), "from", "A real word needs the next word to clearly prefer the fix")
        same(channel.fixForRealWord("form", "the", null, "is"), null, "A real word that fits stays")
    }
    run {
        val pairs = mapOf("it doesn't" to 300, "doesn't matter" to 200, "we were" to 500, "we're here" to 400)
        val channel = NoisyChannel({ typed -> if (typed == "deosnt") listOf("doesn't", "doesn") else listOf("we're", "wire") }, near, { a, b -> pairs["$a $b"] ?: 0 }, { _, _, _ -> 0 })
        must(NoisyChannel.cost("deosnt", "doesn't", near) <= 0.7, "Leaving out the apostrophe costs little")
        same(channel.fixForUnknown("deosnt", "it", null, "matter"), "doesn't", "A misspelled contraction is fixed")
        same(channel.fixForRealWord("were", "we", null, "here"), null, "A real word never becomes a contraction")
    }
    run {
        val pairs = mapOf("this is" to 900, "the is" to 0, "the cat" to 800, "this cat" to 20)
        val channel = NoisyChannel({ listOf("the", "this", "thus") }, near, { a, b -> pairs["$a $b"] ?: 0 }, { _, _, _ -> 0 })
        same(channel.reconsider("thes", "the", null, "is"), "this", "An earlier fix is revisited")
        same(channel.reconsider("thes", "the", null, "cat"), null, "An earlier fix that fits stays")
    }

    // ContextCorrector.
    val frequencies = mapOf("some" to 169, "dome" to 115, "come" to 160, "get" to 175, "food" to 144, "the" to 222,
        "tie" to 120, "they" to 174, "end" to 161, "soome" to 90, "locale" to 92, "local" to 159,
        "to" to 210, "ids" to 81, "is" to 200, "when" to 190, "tho" to 100, "though" to 170)
    val neighbours = setOf("ds", "sd", "dc", "cd", "rt", "tr", "gh", "hg")
    fun corrector(counts: Map<String, Int>, protected: Set<String> = emptySet(), rejected: MutableSet<String> = HashSet()) = ContextCorrector(
        frequencyOf = { frequencies[it] ?: 0 },
        isNeighborKey = { a, b -> "$a$b" in neighbours },
        pairCount = { a, b -> counts["$a $b"] ?: 0 },
        isProtected = { it.lowercase() in protected },
        rejected = rejected,
    )
    run {
        val counts = mapOf("get some" to 60, "some food" to 46)
        same(corrector(counts).review("get", "dome", "food"), "some", "A real word seen nowhere is fixed when the slip fits both sides")
        same(corrector(mapOf("$START some" to 9, "some food" to 46)).review(START, "Dome", "food"), "Some", "A sentence start keeps its capital")
        same(corrector(mapOf("get some" to 60, "some $END" to 66)).review("get", "dome", END), "some", "The end of a sentence counts as a side")
        same(corrector(mapOf("get some" to 60, "some food" to 46, "get dome" to 1)).review("get", "dome", "food"), null, "A pair seen before stays")
        same(corrector(mapOf("get some" to 60)).review("get", "dome", "food"), null, "One side is not enough")
        same(corrector(mapOf("$START the" to 1000, "the end" to 8718)).review(START, "They", "end"), null, "Very common words are not second-guessed")
        val rejected = HashSet<String>()
        val correctorWithRejected = corrector(counts, rejected = rejected)
        same(correctorWithRejected.review("get", "DOME", "food"), null, "Capitals stay")
        rejected += "dome"
        same(correctorWithRejected.review("get", "dome", "food"), null, "Undone words stay")
        same(corrector(counts, protected = setOf("dome")).review("get", "dome", "food"), null, "The user's own words stay")
    }
    run {
        val counts = mapOf("its tail" to 253, "its own" to 3241, "it's own" to 16, "it's raining" to 507,
            "$START its" to 9972, "$START it's" to 50935, "of its" to 7755, "of it's" to 61, "it's a" to 8921, "its a" to 43)
        val choose = corrector(counts)
        same(choose.review("wagged", "it's", "tail"), "its", "it's becomes its by usage")
        same(choose.review("wagged", "it’s", "tail"), "its", "A curly apostrophe counts")
        same(choose.review(START, "Its", "raining"), "It's", "its becomes it's by usage")
        same(choose.review("of", "its", "own"), null, "Right as typed")
        same(choose.review("the", "its", "zebra"), null, "No evidence, no change")
    }
    fun find(text: String, truncated: Boolean = false) = ContextCorrector.findTrailing(text, truncated)
    same(find("Let's get dome food "), ContextCorrector.Trailing("get", "dome", "food", " ", "Let's"), "Finds the words around the boundary")
    same(find("Dome food "), ContextCorrector.Trailing(START, "Dome", "food", " "), "Finds a sentence start")
    same(find("Hi\ndome food "), ContextCorrector.Trailing(START, "dome", "food", " "), "A new line starts a sentence")
    same(find("mail alex@dome food "), null, "An address is unclear")
    same(find("get dome  food "), null, "Two spaces are unclear")
    same(find("get dome food\n"), null, "A new line is not reviewed")
    same(find("ey end ", truncated = true), null, "A cut-off first word is unclear")
    run {
        val confusions = ConfusionTable.parse(sequenceOf("he\tthe\t-1.7\t1000\t5000"))
        val pairs = mapOf("is the" to 900, "the best" to 800, "is he" to 40, "he best" to 0, "said he" to 500, "he was" to 900)
        val context = ContextCorrector(frequencyOf = { 100 }, isNeighborKey = { _, _ -> false }, pairCount = { a, b -> pairs["$a $b"] ?: 0 }, confusions = confusions)
        same(context.review("is", "he", "best"), "the", "A real word typed for another is fixed when both sides agree")
        same(context.review("said", "he", "was"), null, "A real word that fits stays")
        same(context.review("is", "He", "best"), null, "Capitals say too little")
        same(context.review("is", "he", END), null, "The end of a sentence says too little")
    }
    run {
        val pairs = mapOf("going to" to 900, "to the" to 800, "going on" to 5, "on the" to 600, "so tired" to 300, "am so" to 200)
        val context = ContextCorrector(frequencyOf = { 0 }, isNeighborKey = { _, _ -> false }, pairCount = { a, b -> pairs["$a $b"] ?: 0 },
            shortWords = mapOf("to" to 5000, "on" to 3000, "so" to 2000, "of" to 6000))
        same(context.review("going", "o", "the"), "to", "A lone letter becomes the short word that fits")
        same(context.review("love", "u", "too"), null, "Texting shorthand stays")
    }

    // Counts and lists.
    run {
        val pairs = PairCounts.parse(sequenceOf("# header", "get some\t60", "some food\t46", "<s> won\t58", "broken line", "a b\tx", ""))
        same(pairs.size, 3, "Pairs parse")
        same(pairs.count("get", "some"), 60, "Pair count")
        same(pairs.count(START, "won"), 58, "Sentence-start count")
        same(pairs.count("some", "get"), 0, "Unknown pair")
        val triples = TripleCounts.parse(sequenceOf("# header", "up some milk\t12", "<s> i think\t300", "two words\t5", "a b c\tx", ""))
        same(triples.size, 2, "Triples parse")
        same(triples.count("up", "some", "milk"), 12, "Triple count")
        same(TripleCounts.parse(sequenceOf("<s> i am\t100000")).count(START, "i", "am"), 511, "Huge counts are capped")
        val known = KnownWords.parse(sequenceOf("# header", "nosy", "bothers", "", "congrats", "who're", "a"))
        same(known.size, 5, "Known words parse")
        must("CONGRATS" in known && "who're" in known, "Known words are found in any case")
        must("nos" !in known && "x" !in KnownWords.EMPTY, "Other words are not known")
    }
    run {
        var clock = 0L
        val recent = RecentWords(now = { clock }, capacity = 3, idleResetMs = 1_000)
        val oneLetterOff: (String, String) -> Boolean = { typed, word -> typed.length == word.length && typed.lowercase().zip(word).count { (a, b) -> a != b } == 1 }
        recent.record("Ximena")
        same(recent.slipTarget("Ximwna", oneLetterOff), null, "One use is not enough")
        recent.record("Ximena")
        same(recent.slipTarget("Ximwna", oneLetterOff), "Ximena", "A word used twice is a slip target")
        same(recent.slipTarget("ximena", oneLetterOff), null, "A word as written is not a slip")
        clock += 1_001
        same(recent.count("Ximena"), 0, "Recent words go after a pause")
    }
    run {
        val spell = SymSpell()
        spell.addWord("hallo", 100)
        spell.addWord("halle", 10)
        must(spell.lookup("hxllo").any { it.term == "hallo" && it.distance == 1 }, "SymSpell finds a substitution")
        must(spell.lookup("hllo").any { it.term == "hallo" && it.distance == 1 }, "SymSpell finds a deletion")
        must(spell.lookup("halol").any { it.term == "hallo" && it.distance == 1 }, "SymSpell finds a swap")
        same(spell.lookup("hallx").first().term, "hallo", "SymSpell ranks by frequency")
    }
    run {
        var day = 1000L
        val store = KeptOnceStore(null, today = { day })
        store.add("Wrld")
        must(store.contains("wrld"), "A kept word is noted")
        day += 31
        must(!store.contains("wrld"), "The note goes after thirty days")
    }

    // Helpers.
    val known = setOf("autocorrect", "keycap", "word", "story", "again")
    val frequency = mapOf("again" to 156, "word" to 150)
    fun plural(word: String) = TypedCorrector.isPluralOfKnownWord(word, { it in known }, { frequency[it] ?: 0 })
    must(plural("autocorrects") && plural("keycaps"), "A known word plus s is meant")
    must(!plural("wordss") && !plural("storys") && !plural("agains"), "Those plurals are slips")
    same(CasingHelper.applyCasing("Monday", "MONDY"), "MONDAY", "All capitals stay")
    same(CasingHelper.applyCasing("Monday", "mondy"), "Monday", "A name keeps its capital")
    must(TypedCorrector.isInsideAddress("mail alex@domian"), "Before @ is an address")
    must(TypedCorrector.isInsideAddress("see https://github"), "A link is an address")
    must(TypedCorrector.isInsideAddress("open www.exmple"), "www. is an address")
    must(TypedCorrector.isInsideAddress("site.com/wrld"), "A path is an address")
    must(!TypedCorrector.isInsideAddress("hello wrld"), "Plain text is no address")
    must(TypedCorrector.isCapitalizedName("Dropbox", "saved in "), "A capital mid-sentence is a name")
    must(!TypedCorrector.isCapitalizedName("Hellohow", "Yes. "), "A capital at a sentence start is not")
    must(!TypedCorrector.isCapitalizedName("hellohow", "said "), "Lowercase is not a name")
    must(TypedCorrector.isStretchedForEmphasis("sooo") { it == "so" }, "Stretched words are deliberate")
    must(!TypedCorrector.isStretchedForEmphasis("alll") { it == "all" || it == "al" }, "One extra key is a slip")
    must(TypedCorrector.isWordWithClitic("Sami's", { false }, rootMayBeName = true), "A name with a clitic is meant")
    must(!TypedCorrector.isWordWithClitic("tehy're", { it == "they" }), "A slip with a clitic is not")
    same(TypedCorrector.findWordSplit("hellow", { mapOf("hell" to 110, "ow" to 20)[it] ?: 0 }), null, "Rare fragments never split")
    same(TypedCorrector.shiftHeldTooLongFix("THe") { if (it.lowercase() == "the") 222 else 0 }, "The", "Shift held too long is fixed")
    same(TypedCorrector.shiftHeldTooLongFix("IDs") { 0 }, null, "Capitals before one letter usually mean something")
    same(TypedCorrector.twoLetterFix("si", { mapOf("is" to 210, "as" to 200, "his" to 190)[it] ?: 0 }, near), "is", "A two-letter slip becomes the clear word")
    must(TypedCorrector.typoLikelihood("thhe", "the", near) == 1.0, "A doubled letter is the likeliest slip")
    must(KeyNeighbours.near('v', 'b') && KeyNeighbours.near('z', 's') && !KeyNeighbours.near('a', 'x'), "Neighbours come from the Titan's keys")
}

private class Word(val intended: String, val typed: String, val kind: String?)

private class Score {
    var cleanWords = 0
    val falseChanges = ArrayList<String>()
    val total = LinkedHashMap<String, Int>()
    val fixed = LinkedHashMap<String, Int>()
    val wrongFixes = ArrayList<String>()
    val missed = ArrayList<String>()
    val misaligned = ArrayList<String>()

    fun slip(kind: String, outcome: String?, line: String) {
        total[kind] = (total[kind] ?: 0) + 1
        when {
            outcome == null -> fixed[kind] = (fixed[kind] ?: 0) + 1
            outcome.startsWith("missed") -> missed += "[$kind] $line"
            else -> wrongFixes += "[$kind] $line"
        }
    }
}

private val KEY_POSITIONS: Map<Char, Pair<Int, Int>> = buildMap {
    "qwertyuiop".forEachIndexed { i, c -> put(c, 0 to i) }
    "asdfghjkl".forEachIndexed { i, c -> put(c, 1 to i) }
    "zxcvbnm".forEachIndexed { i, c -> put(c, 2 to i + 1) }
}

private fun neighbours(key: Char): List<Char> {
    val (row, column) = KEY_POSITIONS[key] ?: return emptyList()
    return KEY_POSITIONS.filter { (other, position) ->
        other != key && Math.hypot((position.first - row).toDouble(), (position.second - column).toDouble()) <= 1.5
    }.keys.toList()
}

private fun neighbourKey(word: String, random: Random): String? {
    val index = random.nextInt(word.length)
    val options = neighbours(word[index].lowercaseChar())
    if (options.isEmpty()) return null
    val key = options.random(random).let { if (word[index].isUpperCase()) it.uppercaseChar() else it }
    return word.replaceRange(index, index + 1, key.toString())
}

private fun extraLetter(word: String, random: Random): String {
    val index = random.nextInt(word.length)
    val base = word[index].lowercaseChar()
    val extra = if (random.nextBoolean()) base else neighbours(base).randomOrNull(random) ?: base
    return word.substring(0, index + 1) + extra + word.substring(index + 1)
}

private fun swap(word: String, random: Random): String? {
    val index = random.nextInt(word.length - 1)
    if (word[index] == word[index + 1] || word[index].isUpperCase()) return null
    return word.substring(0, index) + word[index + 1] + word[index] + word.substring(index + 2)
}

private fun injectSlips(tokens: List<String>, random: Random, world: Realistic): List<Word> {
    val words = tokens.map { Word(it, it, null) }.toMutableList()
    var i = 0
    while (i < words.size) {
        val token = tokens[i]
        val core = token.trimEnd { it in BOUNDARY_PUNCTUATION }
        val tail = token.substring(core.length)
        if (core.length < 2 || !core.all { it.isLetter() } || random.nextDouble() >= SLIP_RATE) {
            i++
            continue
        }
        val roll = random.nextDouble()
        val next = tokens.getOrNull(i + 1)?.trimEnd { it in BOUNDARY_PUNCTUATION }
        if (roll < 0.08 && tail.isEmpty() && next != null && next.length >= 2 && next.all { it.isLetter() }) {
            // The space was missed: "\u0000" marks where it was, so the typed text joins the words.
            words[i] = Word(token, "$token\u0000", MISSED_SPACE)
            words[i + 1] = Word(tokens[i + 1], tokens[i + 1], MISSED_SPACE)
            i += 2
            continue
        }
        val (kind, typedCore) = when {
            roll < 0.14 && core.length >= 3 && core[0].isUpperCase() && core[1].isLowerCase() ->
                "shift held too long" to core[0] + core[1].uppercase() + core.substring(2)
            roll < 0.44 -> "neighbour key" to neighbourKey(core, random)
            roll < 0.69 && core.length >= 3 -> "missed letter" to core.removeRange(random.nextInt(core.length).let { it..it })
            roll < 0.84 -> "extra letter" to extraLetter(core, random)
            else -> "swapped letters" to swap(core, random)
        }
        if (typedCore == null || typedCore == core) {
            i++
            continue
        }
        val label = if (world.lists.frequency(typedCore) > 0) "$kind (real word)" else kind
        words[i] = Word(token, typedCore + tail, label)
        i++
    }
    return words
}

private fun tally(word: Word, got: String, line: String, score: Score, describe: (String) -> String) {
    if (word.kind == null) {
        score.cleanWords++
        if (got != word.intended) score.falseChanges += "${word.intended} -> $got [${describe(word.intended)}] | $line"
    } else {
        val outcome = when (got) {
            word.intended -> null
            word.typed -> "missed"
            else -> "worse"
        }
        score.slip(word.kind, outcome, "${word.typed} -> $got (want ${word.intended}) [${describe(word.typed)}] | $line")
    }
}

private fun score(words: List<Word>, sentence: String, typed: String, output: String, score: Score, describe: (String) -> String) {
    val line = "$sentence\n      typed: $typed\n      got:   $output"
    val intended = sentence.split(' ')
    val got = output.split(' ')
    // A missed space typed two intended words as one token (the first one carries the marker).
    val typedTokens = ArrayList<Pair<String, List<Int>>>()
    var i = 0
    while (i < words.size) {
        if (words[i].kind == MISSED_SPACE) {
            typedTokens += words[i].typed.removeSuffix("\u0000") + words[i + 1].typed to listOf(i, i + 1)
            i += 2
        } else {
            typedTokens += words[i].typed to listOf(i)
            i++
        }
    }
    when (got.size) {
        intended.size -> words.forEachIndexed { index, word ->
            if (word.kind == MISSED_SPACE && index > 0 && words[index - 1].kind == MISSED_SPACE && words[index - 1].typed.endsWith("\u0000")) {
                return@forEachIndexed // Second half of a missed space: scored with the first.
            }
            if (word.kind == MISSED_SPACE) {
                val ok = got[index] == intended[index] && got[index + 1] == intended[index + 1]
                score.slip(MISSED_SPACE, if (ok) null else "worse", "${intended[index]} ${intended[index + 1]} -> ${got[index]} ${got[index + 1]} | $line")
            } else tally(word, got[index], line, score, describe)
        }
        typedTokens.size -> typedTokens.forEachIndexed { index, (token, indices) ->
            if (indices.size == 2) score.slip(MISSED_SPACE, if (got[index] == token) "missed" else "worse", "$token -> ${got[index]} | $line")
            else tally(words[indices[0]], got[index], line, score, describe)
        }
        else -> score.misaligned += line
    }
}

private fun simulation(world: Realistic) {
    val summary = StringBuilder("\nCHAT SIMULATION (seed $SEED, ${(SLIP_RATE * 100).toInt()}% of words slipped)\n")
    // The sentences mix British and American spelling, so fixes are scored as the word list spells them.
    val autocorrect = world.autocorrect(Lists(world.words, spelling = { it }))
    for (name in listOf("chat_messages_en.txt", "tatoeba_heldout_en.txt")) {
        val lines = File("tests/autocorrect/$name").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val random = Random(SEED)
        val score = Score()
        for (sentence in lines) {
            val words = injectSlips(sentence.split(' '), random, world)
            val typed = words.joinToString(" ") { it.typed }.replace("\u0000 ", "")
            autocorrect.recent.clear()
            val output = Typist(world, autocorrect).type(typed).text.trimEnd()
            score(words, sentence, typed, output, score) { word ->
                val core = word.trim { !it.isLetter() && it != '\'' }
                val frequency = world.lists.frequency(core)
                if (frequency > 0) "known f=$frequency" else "unknown"
            }
        }
        val slips = score.total.values.sum()
        val fixed = score.fixed.values.sum()
        summary.append("== $name (${lines.size} sentences)\n")
        summary.append("  correct words changed  ${score.falseChanges.size}/${score.cleanWords}\n")
        summary.append("  slips fixed            $fixed/$slips (${if (slips > 0) fixed * 100 / slips else 0}%)\n")
        score.total.forEach { (kind, n) -> summary.append("    %-26s %4d/%-4d\n".format(kind, score.fixed[kind] ?: 0, n)) }
        summary.append("  slips made worse       ${score.wrongFixes.size}\n")
        summary.append("  sentences misaligned   ${score.misaligned.size}\n")
        println(summary)
        summary.clear()
        println("--- CORRECT WORDS CHANGED (${score.falseChanges.size})")
        score.falseChanges.take(40).forEach(::println)
        println("--- SLIPS MADE WORSE (${score.wrongFixes.size})")
        score.wrongFixes.take(25).forEach(::println)
        println("--- SLIPS MISSED (${score.missed.size})")
        score.missed.take(15).forEach(::println)
        must(score.falseChanges.size * 1000 <= score.cleanWords * MAX_FALSE_CHANGES_PER_THOUSAND,
            "$name: correct words changed ${score.falseChanges.size}/${score.cleanWords}")
        must(fixed * 100 >= slips * MIN_FIXED_PERCENT, "$name: slips fixed $fixed/$slips")
    }
}

private fun speed(world: Realistic) {
    val corrector = corrector(world)
    val samples = listOf("teh", "instalaltion", "wrld", "hellohow", "form", "somewhere", "doesnt", "the", "xqzpt", "recieve")
    val started = System.nanoTime()
    var count = 0
    repeat(2000) {
        for (sample in samples) {
            corrector.check(sample, "I think that ", "that")
            count++
        }
    }
    val check = (System.nanoTime() - started) / count
    val reviewStarted = System.nanoTime()
    repeat(20000) { corrector.review("I think get dome food ", false, EndedWord("dome", null), "food", true) }
    val review = (System.nanoTime() - reviewStarted) / 20000
    println("\nDATA: load ${world.loadMillis} ms on this computer; heap ${(world.heapAfter - world.heapBefore) / 1_048_576} MB\n" +
        "SPEED on this computer: first check ${check / 1000} us, second check ${review / 1000} us")
}

// As measured on this word list; raise as accuracy improves and never lower without a reason.
private const val MIN_FIXED = 115
private const val MIN_SENTENCE_FIXES = 23
private const val MAX_SENTENCE_CHANGES = 2
// "Vool" and "Ok, ill" need Numen's older rules, which the issue leaves out.
private const val MAX_FIELD_REPORT_MISSES = 2
private const val MAX_FALSE_CHANGES_PER_THOUSAND = 1
private const val MIN_FIXED_PERCENT = 65

/** The next-word model's say in the channel: it only breaks ties it can see, and does nothing while it is not ready. */
private fun nextWordChecks() {
    val channel = NoisyChannel({ listOf("abd", "abe") }, { _, _ -> false }, { _, _ -> 0 }, { _, _, _ -> 0 }, contextWeight = 0.3)
    same(channel.pick("abc", null, null, null)?.word, "abd", "With no next-word chances, equal slips keep their order")
    same(channel.pick("abc", null, null, null, chance = { Double.NaN })?.word, "abd", "Chances not worked out yet must change nothing")
    val favoured = channel.pick("abc", null, null, null, chance = { if (it == "abe") -1.0 else -9.0 })
    same(favoured?.word, "abe", "The next-word model must break a tie between equal slips")
    same(favoured?.score, channel.pick("abc", null, null, null)?.score, "The best fit adds nothing, so the score limits keep their meaning")
    val off = NoisyChannel({ listOf("abd", "abe") }, { _, _ -> false }, { _, _ -> 0 }, { _, _, _ -> 0 }, contextWeight = 0.0)
    same(off.pick("abc", null, null, null, chance = { if (it == "abe") -1.0 else -9.0 })?.word, "abd", "Weight 0 turns the model off")
}

fun main() {
    val world = Realistic()
    smallerChecks()
    nextWordChecks()
    composerFlow(world)
    suggestionBar(world)
    theGuideOffersItsWord(world)
    typoBenchmark(world)
    sentenceAware(world)
    fieldReports(world)
    simulation(world)
    speed(world)
    if (failures.isNotEmpty()) {
        System.err.println("\nFAILED ${failures.size} check(s):")
        failures.forEach { System.err.println("- $it") }
        throw AssertionError("Autocorrect checks failed")
    }
    println("Autocorrect checks passed")
}
