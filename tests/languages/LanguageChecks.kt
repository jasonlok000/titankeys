package com.titankeys.keyboard

import com.titankeys.connected.Json
import java.io.File
import java.util.Locale

// The language framework without a phone: the input method file, the registry, the switch overlay, the accent picker, the
// word lists and the engine interface (docs/languages.md). With a folder argument, every input method in it is checked too.

private var failures = 0

private fun check(name: String, ok: Boolean, detail: () -> String = { "" }) {
    if (ok) return
    failures++
    System.err.println("FAIL $name ${detail()}")
}

private fun <T> same(name: String, actual: T, expected: T) = check(name, actual == expected) { "got $actual, wanted $expected" }

private val SPANISH = """
{
  "id": "es", "name": "Español", "nameEnglish": "Spanish", "label": "ES", "locale": "es", "script": "Latn",
  "keys": "qwerty",
  "accents": { "a": "á", "e": "é", "i": "í", "n": "ñ", "o": "ó", "u": "úü" },
  "words": "words.combined", "variants": null,
  "rules": { "capitalise": true, "sentenceEnd": ".?!", "openingMarks": "¿¡", "apostrophes": false },
  "engine": null,
  "source": { "letters": "CLDR", "words": "AOSP" }
}
"""

private val RUSSIAN_KEYS = mapOf(
    'q' to "й", 'w' to "ц", 'e' to "у", 'r' to "к", 't' to "е", 'y' to "н", 'u' to "г", 'i' to "ш", 'o' to "щ", 'p' to "з",
    'a' to "ф", 's' to "ы", 'd' to "в", 'f' to "а", 'g' to "п", 'h' to "р", 'j' to "о", 'k' to "л", 'l' to "д",
    'z' to "я", 'x' to "ч", 'c' to "с", 'v' to "м", 'b' to "и", 'n' to "т", 'm' to "ь")

fun main(args: Array<String>) {
    theFileReads()
    badFilesSayWhy()
    keysTypeTheirLetters()
    accentsKeepTheirOrderAndCase()
    englishIsAlwaysThereAndFirst()
    turningOnAndOff()
    theOverlaySpansTwoKeysWhenFewAreOn()
    theAccentsSitOverTheHeldKey()
    wordsCompleteIgnoringAccents()
    anEngineFitsTheInterface()
    hangulBuildsSyllables()
    aRestoredInputMethodTypes()
    aShiftedLetterIsTheKeyboards()
    aHeldSymWaits()
    androidsListFollowsTheOnes()
    telexMarksAndTones()
    if (args.isNotEmpty()) everyInputMethodInTheFolder(File(args[0]))
    if (failures > 0) {
        System.err.println("$failures failed")
        kotlin.system.exitProcess(1)
    }
    println("languages: all passed")
}

private fun theFileReads() {
    val es = InputMethod.parse(SPANISH)
    same("id", es.id, "es")
    same("label", es.label, "ES")
    same("qwerty has no key map", es.keys, null)
    same("accents of n", es.accents['n'], listOf("ñ"))
    same("accents of u", es.accents['u'], listOf("ú", "ü"))
    same("opening marks", es.rules.openingMarks, "¿¡")
    same("no engine", es.engine, null)
    val yale = InputMethod.parse("""{"id":"yue-yale","name":"粵語 耶魯","nameEnglish":"Cantonese (Yale)","label":"粵",
        "locale":"yue-Hant","script":"Hani","engine":{"rime":"tk_yale"}}""")
    same("engine name", yale.engine, "rime")
    same("engine argument", yale.engineArg, "tk_yale")
    same("a three-letter label of two characters", InputMethod.parse(SPANISH.replace("\"ES\"", "\"粵拼\"")).label, "粵拼")
}

private fun badFilesSayWhy() {
    fun fails(name: String, text: String) = check(name, runCatching { InputMethod.parse(text) }.exceptionOrNull() is Json.Error)
    fails("an upper-case id", SPANISH.replace("\"id\": \"es\"", "\"id\": \"ES\""))
    fails("a long label", SPANISH.replace("\"ES\"", "\"SPAN\""))
    fails("a key that isn't a to z", SPANISH.replace("\"a\": \"á\"", "\"?\": \"¿\""))
    fails("too many accents", SPANISH.replace("\"úü\"", "\"úüùûũūŭůűųx\""))
    fails("no name", SPANISH.replace("\"name\": \"Español\",", ""))
}

private fun keysTypeTheirLetters() {
    val ru = InputMethod.ENGLISH.copy(id = "ru", locale = "ru", keys = RUSSIAN_KEYS.mapValues { InputMethod.KeyLetters(it.value) })
    same("P types з", ru.letter('p', shift = false), "з")
    same("Shift+P types З", ru.letter('P', shift = true), "З")
    same("English keys type what they show", InputMethod.ENGLISH.letter('a', false), null)
    val tr = InputMethod.ENGLISH.copy(id = "tr", locale = "tr", keys = mapOf('i' to InputMethod.KeyLetters("i")))
    same("Turkish capital i has its dot", tr.letter('i', shift = true), "İ")
    val ka = InputMethod.ENGLISH.copy(id = "ka", locale = "ka", keys = mapOf('w' to InputMethod.KeyLetters("წ", "ჭ")))
    same("Georgian Shift gives another letter", ka.letter('w', shift = true), "ჭ")
    val kaRules = ka.copy(keys = mapOf('w' to InputMethod.KeyLetters("წ", "ჭ"), 'a' to InputMethod.KeyLetters("ა")),
        rules = InputMethod.Rules(capitalise = false))
    same("Georgian Shift keeps a letter with no Shift letter, never Mtavruli", kaRules.letter('a', shift = true), "ა")
    same("Georgian Shift letter still typed", kaRules.letter('w', shift = true), "ჭ")
}

private fun accentsKeepTheirOrderAndCase() {
    val de = InputMethod.parse(SPANISH.replace("\"accents\": { \"a\": \"á\", \"e\": \"é\", \"i\": \"í\", \"n\": \"ñ\", \"o\": \"ó\", \"u\": \"úü\" }",
        "\"accents\": { \"a\": \"ä\", \"o\": \"ö\", \"s\": \"ß\", \"u\": \"ü\" }").replace("\"id\": \"es\"", "\"id\": \"de\"").replace("\"locale\": \"es\"", "\"locale\": \"de\""))
    same("s holds ß", de.accentsOf('s', shift = false), listOf("ß"))
    same("capital ä", de.accentsOf('A', shift = true), listOf("Ä"))
    same("a key with nothing held", de.accentsOf('q', false), emptyList<String>())
    check("German has accents", de.hasAccents)
    check("English has none", !InputMethod.ENGLISH.hasAccents)
    same("a letter with a combining mark stays one", InputMethod.graphemes("éa"), listOf("é", "a"))
}

private val INDEX = InputMethodIndex.parse("""[
  {"id":"es","name":"Español","nameEnglish":"Spanish","label":"ES","locale":"es","script":"Latn"},
  {"id":"fr","name":"Français","nameEnglish":"French","label":"FR","locale":"fr","script":"Latn"},
  {"id":"de","name":"Deutsch","nameEnglish":"German","label":"DE","locale":"de","script":"Latn"}
]""")

private fun englishIsAlwaysThereAndFirst() {
    same("English first", INDEX.ids.first(), "en")
    same("nothing on means English", INDEX.on("").map { it.id }, listOf("en"))
    same("unknown ids drop", INDEX.on("es,xx,en").map { it.id }, listOf("es", "en"))
    same("repeats drop", INDEX.on("en,es,en").map { it.id }, listOf("en", "es"))
    same("the current one when it's on", INDEX.current("en,es", "es").id, "es")
    same("the first one when it's off", INDEX.current("en,es", "fr").id, "en")
}

private fun turningOnAndOff() {
    same("on goes last", InputMethodIndex.toggle(listOf("en"), "es", true), listOf("en", "es"))
    same("the only one can't go off", InputMethodIndex.toggle(listOf("en"), "en", false), null)
    same("English can go off with another on", InputMethodIndex.toggle(listOf("en", "es"), "en", false), listOf("es"))
    val eight = List(8) { "x$it" }
    same("a ninth is refused", InputMethodIndex.toggle(eight, "es", true), null)
    same("move up", InputMethodIndex.move(listOf("en", "es", "fr"), "fr", up = true), listOf("en", "fr", "es"))
    same("the first can't move up", InputMethodIndex.move(listOf("en", "es"), "en", up = true), listOf("en", "es"))
}

private fun theOverlaySpansTwoKeysWhenFewAreOn() {
    same("two on", SwitchOverlay.columns(2), listOf(1..2, 3..4))
    same("four on", SwitchOverlay.columns(4), listOf(1..2, 3..4, 5..6, 7..8))
    same("five on", SwitchOverlay.columns(5), listOf(1..1, 2..2, 3..3, 4..4, 5..5))
    same("eight on reach O", SwitchOverlay.columns(8).last(), 8..8)
    same("W picks the first of two", SwitchOverlay.choice("W", 2), 0)
    same("E picks the first of two too", SwitchOverlay.choice("E", 2), 0)
    same("R picks the second of two", SwitchOverlay.choice("R", 2), 1)
    same("S, on the second row, picks as W does", SwitchOverlay.choice("S", 2), 0)
    same("Q picks nothing", SwitchOverlay.choice("Q", 2), -1)
    same("T picks the second of two", SwitchOverlay.choice("T", 2), 1)
    same("Y picks nothing with two on", SwitchOverlay.choice("Y", 2), -1)
    same("Z's row picks nothing", SwitchOverlay.choice("X", 2), -1)
    same("O picks the eighth of eight", SwitchOverlay.choice("O", 8), 7)
}

private fun theAccentsSitOverTheHeldKey() {
    same("one accent over E", Picker.accentColumns(1, Picker.anyColumn("E")), listOf(2))
    same("two over U", Picker.accentColumns(2, Picker.anyColumn("U")), listOf(6, 7))
    same("four over A move in from the edge", Picker.accentColumns(4, Picker.anyColumn("A")), listOf(0, 1, 2, 3))
    same("four over P move in from the edge", Picker.accentColumns(4, Picker.anyColumn("P")), listOf(6, 7, 8, 9))
    same("more than ten are cut", Picker.accentColumns(14, 3).size, 10)
    val columns = Picker.accentColumns(2, Picker.anyColumn("U"))
    same("U picks the first, over itself", Picker.choice("U", columns), 0)
    same("I picks the second", Picker.choice("I", columns), 1)
    same("J, under U, picks the first too", Picker.choice("J", columns), 0)
    same("A key past them picks nothing", Picker.choice("P", columns), -1)
    same("Z's row never picks", Picker.choice("N", columns), -1)
    same("a page of five from E", Picker.pageColumns(5), listOf(2, 3, 4, 5, 6))
    same("at most eight, E to P", Picker.pageColumns(12).last(), 9)
    same("one page has no mark", EngineState(page = 0, lastPage = true).pageMark, "")
    same("Rime's pages: the page and an arrow", EngineState(page = 1, lastPage = false).pageMark, "2›")
    same("the last of Rime's pages", EngineState(page = 2, lastPage = true).pageMark, "3")
    same("a count when the engine knows it", EngineState(page = 0, lastPage = false, pages = 3).pageMark, "1/3")
    // The choices turn as the Sym panel's pages do: up is the next page (owner: "swipe up on the keyboard ... for the next page").
    check("a swipe up goes forward", com.titankeys.engine.EmojiPages.forward(-1f))
    check("a swipe down goes back", !com.titankeys.engine.EmojiPages.forward(1f))
}

private fun wordsCompleteIgnoringAccents() {
    val list = WordList.read(sequenceOf(
        "dictionary=main:es,locale=es",
        " word=café,f=180", " word=cafetería,f=120", " word=cafe,f=10", " word=calle,f=200",
        "  bigram=con,f=3", " word=niño,f=150", " word=ninguno,f=170", " word=xx,f=0,not_a_word=true"), Locale("es"))
    same("words read, next-word lines skipped", list.size, 6)
    same("cafe offers café first, then the plain form", list.complete("cafe"), listOf("café", "cafe", "cafetería"))
    same("nino offers niño", list.complete("nino").first(), "niño")
    same("a capital stays", list.complete("Cal"), listOf("Calle"))
    same("all capitals stay", list.complete("CAL"), listOf("CALLE"))
    same("nothing typed, nothing offered", list.complete(""), emptyList<String>())
    check("knows café", list.contains("Café"))
    same("folding keeps ß", WordList.fold("Straße", Locale.GERMAN), "straße")
}

/** A made-up engine that types each key in capitals once Space is pressed, to show the interface is enough to build one. */
private class EchoEngine : InputEngine {
    private var code = ""
    override fun start(method: InputMethod, dataDir: File) = true
    override fun key(char: Char) = state(code + char)
    override fun space() = EngineState(commit = code.uppercase()).also { code = "" }
    override fun enter() = EngineState(commit = code).also { code = "" }
    override fun backspace() = if (code.isEmpty()) EngineState.UNHANDLED else state(code.dropLast(1))
    override fun pick(index: Int) = space()
    override fun page(forward: Boolean) = state(code)
    override fun reset() = state("")
    override fun option(name: String, on: Boolean) = Unit
    override fun stop() = Unit
    private fun state(next: String): EngineState {
        code = next
        return EngineState(code = next, choices = if (next.isEmpty()) emptyList() else listOf(next.uppercase()))
    }
}

private fun anEngineFitsTheInterface() {
    val engine = EchoEngine()
    check("starts", engine.start(InputMethod.ENGLISH, File(".")))
    engine.key('n')
    val built = engine.key('i')
    check("builds", built.composing && built.choices == listOf("NI"))
    same("Space commits", engine.space().commit, "NI")
    check("Backspace with nothing built goes to the field", !engine.backspace().handled)
}

/** Types [keys] into a fresh Korean engine ("<" is Backspace): what's in the field after, and the syllable still being built. */
/**
 * What types as the keyboard starts: the setting, unless Android's keyboard list picked another since the keyboard last set it.
 * [android] is Android's entry as the keyboard starts and [synced] the one it last left Android on.
 */
private fun startsWith(index: InputMethodIndex, setting: String, current: String, android: String?, synced: String?): String {
    return InputMethodChoice.atFieldStart(index, setting, current, KeyboardListSync(synced).heard(android)).id
}

/** The keyboard sets Android's list again whenever it isn't exactly the input methods on: batch 2 of the phone run. */
private fun androidsListFollowsTheOnes() {
    val on = listOf("en", "es", "de", "el", "yue-yale")
    check("German missing: set again", EnabledEntries.differ(on, listOf("en", "es", "el", "ru", "sr", "kk", "yue-yale")))
    check("an old one left over: set again", EnabledEntries.differ(on, on + "ru"))
    check("nothing on in Android's list: set again", EnabledEntries.differ(on, emptyList()))
    check("the same in another order: left alone", !EnabledEntries.differ(on, on.reversed()))
    check("Android can't say: left alone", !EnabledEntries.differ(on, null))
}

/** A held Sym opens the overlay in any field with two or more input methods on; each reason it doesn't is said. */
private fun aHeldSymWaits() {
    fun why(on: Int = 3, english: Boolean = false, panel: Boolean = false, alt: Boolean = false, guide: Boolean = false,
            exact: Boolean = false, waiting: Int = 0) = SymHold.whyNot(on, english, panel, alt, guide, exact, waiting)
    same("en, ru and es on, the panel closed: Sym waits", why(), null)
    same("two on: Sym waits", why(on = 2), null)
    check("one on: Sym as usual", why(on = 1) != null)
    check("the playground: Sym as usual", why(english = true) != null)
    check("the panel open: Sym closes it", why(panel = true) != null)
    check("Alt on: Alt+Sym is Android's keyboard list", why(alt = true) != null)
    check("keys waiting for the app: Sym keeps its place", why(waiting = 1) != null)
}

/** Shift + a letter in an input method with its own letters is the keyboard's, never the app's (Georgian's Shift + W). */
private fun aShiftedLetterIsTheKeyboards() {
    check("Shift + W in Georgian: kept", KeysMapKeys.keeps(hasKeys = true, letterKey = true, alt = false, ctrl = false, meta = false))
    check("Ctrl + W in Georgian: the app's shortcut", !KeysMapKeys.keeps(hasKeys = true, letterKey = true, alt = false, ctrl = true, meta = false))
    check("Alt + W: its symbol, as always", !KeysMapKeys.keeps(hasKeys = true, letterKey = true, alt = true, ctrl = false, meta = false))
    check("Shift + W in English: as always", !KeysMapKeys.keeps(hasKeys = false, letterKey = true, alt = false, ctrl = false, meta = false))
}

private fun aRestoredInputMethodTypes() {
    val index = InputMethodIndex(listOf(
        InputMethodIndex.Entry("ru", "Русский", "Russian", "RU", "ru", "Cyrl"),
        InputMethodIndex.Entry("de", "Deutsch", "German", "DE", "de", "Latn"),
    ))
    // The phone run's bug: a restore made Russian current, and Android still had English, as the keyboard last left it.
    same("A restored input method types over Android's old entry", startsWith(index, "en,ru", "ru", "en", "en"), "ru")
    same("So does one chosen in Settings while the keyboard was off", startsWith(index, "en,ru,de", "de", "ru", "ru"), "de")
    same("The first start with nothing synced: the setting", startsWith(index, "en,ru", "ru", "en", null), "ru")
    same("Picked in Android's list while the keyboard was off: Android's", startsWith(index, "en,ru", "en", "ru", "en"), "ru")
    same("Android's pick of one that's off changes nothing", startsWith(index, "en,ru", "en", "de", "en"), "en")
    same("A current that's off: the first on", startsWith(index, "en,ru", "de", "en", "en"), "en")
    // The phone re-test's exact path: Reset literally everything (nothing synced), a restore of en,ru with ru current, then the
    // keyboard starts with Android still on English's entry, or on none of TitanKeys' entries, and Android says it before the
    // first field and again as that field starts.
    val afterReset = KeyboardListSync(null)
    same("After Reset, Android's entry said as the keyboard starts is no pick", afterReset.heard("en"), null)
    same("Said again as the first field starts, still no pick", afterReset.heard("en"), null)
    same("The first field after the restore types the restored one, and says why",
        InputMethodChoice.atFieldStart(index, "en,ru", "ru", null), InputMethodChoice("ru", "the saved setting"))
    same("With none of TitanKeys' entries in Android's list", startsWith(index, "en,ru", "ru", null, null), "ru")
    same("A restored current that isn't on: the first that is",
        InputMethodChoice.atFieldStart(index, "en,ru", "de", null), InputMethodChoice("en", "the saved de isn't on, so the first that is"))
    same("A pick in Android's list says so", InputMethodChoice.atFieldStart(index, "en,ru", "en", "ru"),
        InputMethodChoice("ru", "picked in Android's keyboard list"))
    val sync = KeyboardListSync("en")
    same("Android's old entry, said again as the keyboard starts", sync.heard("en"), null)
    sync.told("ru")
    same("Android's answer to the keyboard's own switch", sync.heard("ru"), null)
    same("A pick in Android's list while typing", sync.heard("en"), "en")
    same("Said twice, it's one pick", sync.heard("en"), null)
    same("Android with none of TitanKeys' entries", sync.heard(null), null)
    val fresh = KeyboardListSync()
    same("Nothing synced: the first entry heard is no pick", fresh.heard("en"), null)
    same("but the next change is", fresh.heard("ru"), "ru")
}

private fun hangul(keys: String): Pair<String, String> {
    val engine = HangulComposer()
    val field = StringBuilder()
    var code = ""
    for (key in keys) {
        val state = when (key) {
            ' ' -> engine.space()
            '<' -> engine.backspace().also { if (!it.handled && field.isNotEmpty()) field.setLength(field.length - 1) }
            else -> engine.key(key)
        }
        field.append(state.commit)
        if (!state.handled && key != '<') field.append(key)
        code = if (state.handled) state.code else ""
    }
    return field.toString() to code
}

private fun hangulBuildsSyllables() {
    same("ㄱ alone", hangul("r"), "" to "ㄱ")
    same("가", hangul("rk"), "" to "가")
    same("각", hangul("rkr"), "" to "각")
    same("A vowel after a final takes it: 가가", hangul("rkrk"), "가" to "가")
    same("한국어", hangul("gksrnrdj "), "한국어 " to "")
    same("Two finals: 닭", hangul("ekfr"), "" to "닭")
    same("The second of two finals moves on: 달가", hangul("ekfrk"), "달" to "가")
    same("Two vowels join: 과", hangul("rhk"), "" to "과")
    same("Shift's tense consonant and vowel: 썌", hangul("TO"), "" to "썌")
    same("Shift on a tense key: 까", hangul("Rk"), "" to "까")
    same("Shift on a key with no tense letter types it as usual: 가", hangul("rK"), "" to "가")
    same("ㅖ with Shift on P", hangul("rP"), "" to "계")
    same("Backspace takes the last jamo off: 과 to 고", hangul("rhk<"), "" to "고")
    same("Backspace to nothing, then the field's", hangul("rk<<<"), "" to "")
    same("A mark writes the syllable first", hangul("rk."), "가." to "")
    same("ㄸ can't end a syllable", hangul("rkE"), "가" to "ㄸ")
    same("Two consonants stay apart", hangul("rs"), "ㄱ" to "ㄴ")
    same("A lone vowel", hangul("k"), "" to "ㅏ")
    same("Nothing built: Space is the keyboard's", HangulComposer().space().handled, false)
    same("No choices to pick", HangulComposer().pick(0).handled, false)
}

private fun telex(keys: String) = TelexComposer.spell(keys)

private fun telexMarksAndTones() {
    same("aa", telex("aa"), "â")
    same("aw", telex("aw"), "ă")
    same("ee oo", telex("ee") + telex("oo"), "êô")
    same("ow uw", telex("ow") + telex("uw"), "ơư")
    same("uow", telex("nguowif"), "người")
    same("uơ at a word's end: thuở", telex("thuowr"), "thuở")
    same("ươ before a final: đường", telex("dduowngf"), "đường")
    same("dd", telex("ddi"), "đi")
    same("tones s f r x j", listOf("as", "af", "ar", "ax", "aj").map(::telex), listOf("á", "à", "ả", "ã", "ạ"))
    same("A repeated tone key undoes it", telex("ass"), "as")
    same("A repeated hat undoes it", telex("aaa"), "aa")
    same("A repeated dd undoes it", telex("ddd"), "dd")
    same("A repeated w undoes it", telex("aww"), "aw")
    same("z takes the tone off", telex("asz"), "a")
    same("A new tone replaces the old", telex("asf"), "à")
    same("The tone goes on the hat: tiếng", telex("tieengs"), "tiếng")
    same("Hat and tone can come at the end", telex("tiengse"), "tiếng")
    same("Before a final consonant, the last vowel: hoàn", telex("hoanf"), "hoàn")
    same("An open syllable of two: the first, hòa", telex("hoaf"), "hòa")
    same("Of three: the middle, ngoài", telex("ngoaif"), "ngoài")
    same("qu's u is the consonant's: quá", telex("quas"), "quá")
    same("gi's i is the consonant's: giá", telex("gias"), "giá")
    same("gì", telex("gif"), "gì")
    same("Capitals keep their case", telex("Vieetj"), "Việt")
    same("w alone is ư", telex("w"), "ư")
    same("A tone key with no vowel is a letter", telex("sao"), "sao")
    val vi = java.util.Locale.forLanguageTag("vi")
    val engine = TelexComposer(WordList.read(sequenceOf(" word=tiếng,f=200", " word=tiến,f=150"), vi))
    "tieeng".forEach { engine.key(it) }
    same("The tone key marks the code", engine.key('s').code, "tiếng")
    same("Backspace takes the last key off", engine.backspace().code, "tiêng")
    engine.reset()
    same("Unmarked code offers the marked word", "tieng".map { engine.key(it) }.last().choices, listOf("tiếng"))
    same("Picking writes it with a space", engine.pick(0).commit, "tiếng ")
    val ended = TelexComposer().apply { "xin".forEach { key(it) } }.space()
    same("Space writes the word and goes on", ended.commit to ended.handled, "xin" to false)
}

/** The Telex keys for [word]: each mark as its keys, and the tone's key at the end. */
private fun telexKeysFor(word: String): String {
    val keys = StringBuilder()
    var tone = ""
    for (ch in java.text.Normalizer.normalize(word, java.text.Normalizer.Form.NFD)) {
        when (ch) {
            '\u0301' -> tone = "s"
            '\u0300' -> tone = "f"
            '\u0309' -> tone = "r"
            '\u0303' -> tone = "x"
            '\u0323' -> tone = "j"
            '\u0302' -> keys.append(keys.last())
            '\u0306', '\u031B' -> keys.append('w')
            'đ' -> keys.append("dd")
            'Đ' -> keys.append("Dd")
            else -> keys.append(ch)
        }
    }
    return keys.toString() + tone
}

/**
 * Vietnamese: Telex can type the word list's common words. Where the tone sits is a style (hòa or hoà), so a word counts as
 * typed when Telex spells its letters and its tone, wherever the tone sits.
 */
private fun telexTypesTheList(method: InputMethod, list: File) {
    val words = list.useLines { lines -> lines.filter { it.startsWith(" word=") }.map { it.removePrefix(" word=").substringBefore(',') }.toList() }
    check("${method.id}: its word list has words", words.size > 1000) { "${words.size}" }
    check("${method.id}: at most 4 MB", list.length() <= 4_000_000) { "${list.length()}" }
    val letters = Regex("[a-zàáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]+", RegexOption.IGNORE_CASE)
    val common = words.take(5000).filter { letters.matches(it) }
    fun tones(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).filter { it in "\u0301\u0300\u0309\u0303\u0323" }
    fun noTone(s: String) = java.text.Normalizer.normalize(
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).filter { it !in "\u0301\u0300\u0309\u0303\u0323" },
        java.text.Normalizer.Form.NFC)
    var exact = 0
    val missed = ArrayList<String>()
    for (word in common) {
        val typed = TelexComposer.spell(telexKeysFor(word))
        if (typed == word) exact++
        else if (noTone(typed) != noTone(word) || tones(typed) != tones(word)) missed += word
    }
    check("${method.id}: Telex types the common words' letters and tones", missed.size * 100 <= common.size) {
        "${missed.size} of ${common.size}: ${missed.take(15).joinToString(" ")}"
    }
    println("${method.id}: Telex spells $exact of ${common.size} common words exactly; ${missed.size} not: ${missed.joinToString(" ")}")
}

/** Every `im.json` in [folder]: it reads, its label is unique, its word list reads, and its accents' letters are in it or held. */
/** The app's text fonts (theme.ts), which Settings > Input methods draws the names with. */
private val textFonts by lazy {
    listOf("assets/fonts/Inter-Regular.otf", "assets/fonts/Inter-Medium.otf").map { java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, File(it)) }
}

/** What CLDR's own name for an input method's language is recorded as, by tools/im/make.py, which checks it against CLDR. */
private const val CLDR_NAME = "CLDR 49 localeDisplayNames: "

/**
 * An input method's own name, as Settings > Input methods shows it: there, the same in the index, CLDR's name for the
 * language in that language (accents and all), and in the app's fonts, Hangul and Chinese included: Android's CJK font
 * didn't reach Ink on the phone (tools/im/fonts.py). Chinese input methods are named for their spelling.
 */
private fun theNameShows(method: InputMethod, inIndex: String?) {
    val name = method.name
    check("${method.id}: has a name", name.isNotBlank())
    check("${method.id}: has an English name", method.nameEnglish.isNotBlank())
    same("${method.id}: its name is NFC", java.text.Normalizer.isNormalized(name, java.text.Normalizer.Form.NFC), true)
    same("${method.id}: the index has the same name", inIndex, name)
    if (method.script != "Hani") same("${method.id}: its name is CLDR's", method.source["name"], CLDR_NAME + name)
    val missing = name.codePoints().toArray().filter { code -> textFonts.any { !it.canDisplay(code) } }
    check("${method.id}: the app's fonts have its name's letters", missing.isEmpty()) {
        missing.joinToString(" ") { String(Character.toChars(it)) }
    }
}

private fun everyInputMethodInTheFolder(folder: File) {
    val methods = folder.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }
    val labels = HashMap<String, String>()
    @Suppress("UNCHECKED_CAST")
    val index = (com.titankeys.connected.Json.parse(File(folder, "index.json").readText()) as List<Map<String, Any?>>)
        .associate { it["id"] as String to it["name"] as String? }
    for (dir in methods) {
        val file = File(dir, "im.json")
        val parsed = runCatching { InputMethod.parse(file.readText()) }
        val method = parsed.getOrNull()
        if (method == null) {
            check("${dir.name} reads", false) { parsed.exceptionOrNull()?.message.orEmpty() }
            continue
        }
        same("${dir.name}: the folder is its id", method.id, dir.name)
        method.engine?.let { engine ->
            check("${method.id}: the app has its engine, $engine", engine in EngineClasses.BY_NAME) { EngineClasses.BY_NAME.keys.toString() }
        }
        labels.put(method.label, method.id)?.let { check("${method.id}: label ${method.label} is also $it's", false) }
        check("${method.id}: has a NOTICE", File(dir, "NOTICE").isFile)
        theNameShows(method, index[method.id])
        if (method.keys != null) {
            same("${method.id}: every key has a letter", method.keys.keys, ('a'..'z').toSet())
            val unshifted = ('a'..'z').filter { method.letter(it, true).isNullOrEmpty() }
            check("${method.id}: Shift + every letter key types a letter", unshifted.isEmpty()) { unshifted.joinToString(" ") }
        }
        val words = method.words ?: continue
        if (method.engine == "telex") {
            telexTypesTheList(method, File(dir, words))
            continue
        }
        val list = File(dir, words)
        check("${method.id}: its word list is there", list.isFile)
        if (!list.isFile) continue
        val read = WordList.read(list.useLines { it.toList() }.asSequence(), method.javaLocale)
        check("${method.id}: its word list has words", read.size > 1000) { "${read.size}" }
        check("${method.id}: at most 100,000 words", read.size <= 100_000) { "${read.size}" }
        check("${method.id}: at most 4 MB", list.length() <= 4_000_000) { "${list.length()}" }
        // Every letter the list uses often can be typed: from a key, with Shift where Shift gives another (Georgian), or held on one.
        val typable = HashSet<String>()
        for (key in 'a'..'z') {
            typable += method.letter(key, false) ?: key.toString()
            method.keys?.get(key)?.shift?.let { typable += it }
            typable += method.accentsOf(key, false)
        }
        val counts = HashMap<String, Int>()
        list.useLines { lines ->
            lines.filter { it.startsWith(" word=") }.take(50_000).forEach { line ->
                val word = line.removePrefix(" word=").substringBefore(',').lowercase(method.javaLocale)
                for (letter in InputMethod.graphemes(word)) if (letter.first().isLetter()) counts.merge(letter, 1, Int::plus)
            }
        }
        val missing = counts.filter { (letter, count) -> count >= 20 && letter !in typable }.keys.sorted()
        check("${method.id}: every common letter can be typed", missing.isEmpty()) { missing.joinToString(" ") }
    }
    println("languages: ${methods.size} input methods checked")
}
