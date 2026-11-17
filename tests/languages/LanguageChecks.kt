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
    languagesGroupTheirInputMethods()
    theAccentsSitOverTheHeldKey()
    wordsCompleteIgnoringAccents()
    anEngineFitsTheInterface()
    hangulBuildsSyllables()
    aRestoredInputMethodTypes()
    thaiHoldsAndBreaks()
    hindiRulesType()
    hebrewRightToLeft()
    aShiftedLetterIsTheKeyboards()
    aHeldSymWaits()
    overlaysKeepTheTouchpad()
    symWorksAfterTheSwitcher()
    androidsListFollowsTheOnes()
    telexMarksAndTones()
    if (args.isNotEmpty()) everyInputMethodInTheFolder(File(args[0]))
    if (args.isNotEmpty()) shiftShowsWhereItMatters(File(args[0]))
    if (args.isNotEmpty()) switchingKeepsOneList(File(args[0]))
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
    // At most four languages, and four input methods within one (owner, 2026-11-17), with no other total.
    val language = { id: String -> when { id.startsWith("yue") || id.startsWith("zh") -> "zh"; id.startsWith("sr") -> "sr"; else -> id } }
    val tests = listOf("en", "vi", "yue-yale", "yue-jyutping", "zh-pinyin", "ja")
    same("A's largest set, six in four languages, stays on", tests.fold(listOf<String>()) { on, id -> InputMethodIndex.toggle(on, id, true, language)!! }, tests)
    same("a fifth language is refused", InputMethodIndex.toggle(tests, "es", true, language), null)
    same("another input method of a language on is fine", InputMethodIndex.toggle(listOf("en", "es", "ru", "zh-pinyin"), "yue-yale", true, language),
        listOf("en", "es", "ru", "zh-pinyin", "yue-yale"))
    same("a fifth in one language is refused", InputMethodIndex.toggle(listOf("zh1", "zh2", "zh3", "zh4"), "zh5", true) { "zh" }, null)
    same("a setting with more than four keeps them all", INDEX.on("en,es,fr,de,it,pt").size, 6)
    same("move up", InputMethodIndex.move(listOf("en", "es", "fr"), "fr", up = true), listOf("en", "fr", "es"))
    same("the first can't move up", InputMethodIndex.move(listOf("en", "es"), "en", up = true), listOf("en", "es"))
}

private fun theOverlaySpansTwoKeysWhenFewAreOn() {
    // Centred within W to O, every tile the same whole number of keys (owner, 2026-11-17): 1 = W–O, 2 = four keys each,
    // 3 = two each over E–I, 4 = two each. W is column 1 and O column 8, so the margins left of W and right of O are equal.
    same("one: W to O", SwitchOverlay.columns(1), listOf(1..8))
    same("two: four keys each", SwitchOverlay.columns(2), listOf(1..4, 5..8))
    same("three: two each over E to I", SwitchOverlay.columns(3), listOf(2..3, 4..5, 6..7))
    same("four: two each", SwitchOverlay.columns(4), listOf(1..2, 3..4, 5..6, 7..8))
    for (count in 1..4) {
        val columns = SwitchOverlay.columns(count)
        same("$count: centred, as many keys left of the first as right of the last", columns.first().first - 1, 8 - columns.last().last)
        same("$count: every tile as wide", columns.map { it.last - it.first }.distinct().size, 1)
    }
    same("more than four: the first four", SwitchOverlay.columns(6).size, 4)
    same("W picks nothing with three", SwitchOverlay.choice("W", 3), -1)
    same("E picks the first of three", SwitchOverlay.choice("E", 3), 0)
    same("D, under E, too", SwitchOverlay.choice("D", 3), 0)
    same("Y picks the second of three", SwitchOverlay.choice("Y", 3), 1)
    same("W picks the first of two", SwitchOverlay.choice("W", 2), 0)
    same("R picks the first of two too", SwitchOverlay.choice("R", 2), 0)
    same("T picks the first of two", SwitchOverlay.choice("T", 2), 0)
    same("Y picks the second of two", SwitchOverlay.choice("Y", 2), 1)
    same("O picks the second of two", SwitchOverlay.choice("O", 2), 1)
    same("S, on the second row, picks as W does", SwitchOverlay.choice("S", 2), 0)
    same("Q picks nothing", SwitchOverlay.choice("Q", 2), -1)
    same("I picks the third of three", SwitchOverlay.choice("I", 3), 2)
    same("O picks nothing with three", SwitchOverlay.choice("O", 3), -1)
    same("Z's row picks nothing", SwitchOverlay.choice("X", 2), -1)
    same("O picks the fourth of four", SwitchOverlay.choice("O", 4), 3)
}

/** Languages, then input methods: Serbian and Chinese group theirs, and the switcher's second row shows them. */
private fun languagesGroupTheirInputMethods() {
    fun entry(id: String, language: String, name: String = id) =
        """{"id":"$id","name":"$name","nameEnglish":"$name","label":"${id.take(2).uppercase()}","locale":"$id","script":"Latn",""" +
            """"language":{"id":"$language","name":"$language","nameEnglish":"$language","label":"${language.uppercase()}","script":"Latn",""" +
            """"experimental":${language != "zh"}}}"""
    val index = InputMethodIndex.parse("[" + listOf(entry("es", "es"), entry("sr-latn", "sr"), entry("sr", "sr"),
        entry("yue-yale", "zh"), entry("zh-pinyin", "zh")).joinToString(",") + "]")
    same("English is a language, not experimental", index.languages.first(),
        InputMethodIndex.Language("en", "English", "English", "EN", "Latn", false))
    same("Serbian is one language", index.methodsOf("sr").map { it.id }, listOf("sr-latn", "sr"))
    same("languages in the index's order", index.languages.map { it.id }, listOf("en", "es", "sr", "zh"))
    check("Spanish is experimental", index.languages.first { it.id == "es" }.experimental)
    check("Chinese isn't", !index.languages.first { it.id == "zh" }.experimental)
    val on = index.languagesOn("en,sr,es,yue-yale,sr-latn,zh-pinyin")
    same("languages on, in the order each first comes", on.map { it.language.id }, listOf("en", "sr", "es", "zh"))
    same("each with its own on, in the setting's order", on[1].methods.map { it.id }, listOf("sr", "sr-latn"))
    same("a language with one picks its input method", SwitchOverlay.pick(on, null, 2), SwitchPick.To("es"))
    same("a language with two opens its row", SwitchOverlay.pick(on, null, 1), SwitchPick.Row(on[1]))
    same("its row picks an input method", SwitchOverlay.pick(on, on[1], 1), SwitchPick.To("sr-latn"))
    same("an empty place picks nothing", SwitchOverlay.pick(on, null, 5), null)
    same("several languages: the switcher opens on them", SwitchOverlay.opening(on), null)
    // Two languages on: a held Sym goes straight to the other, to its input method last used.
    val two = index.languagesOn("es,yue-yale,zh-pinyin")
    same("two languages: Spanish to Chinese, its first", SwitchOverlay.toggle(two, "es", emptyMap()), "yue-yale")
    same("to the one last used there", SwitchOverlay.toggle(two, "es", mapOf("zh" to "zh-pinyin")), "zh-pinyin")
    same("from Chinese back to Spanish", SwitchOverlay.toggle(two, "zh-pinyin", mapOf("es" to "es")), "es")
    same("a last used that's off now: the first", SwitchOverlay.toggle(two, "es", mapOf("zh" to "yue-jyutping")), "yue-yale")
    same("three languages: the switcher", SwitchOverlay.toggle(on, "es", emptyMap()), null)
    same("one language with two: the switcher's row", SwitchOverlay.toggle(index.languagesOn("sr,sr-latn"), "sr", emptyMap()), null)
    // A tap on the mark: another input method of the language typing, never another language.
    same("a tap on Serbian's mark: its other input method", SwitchOverlay.markTap(on, "sr"), SwitchPick.To("sr-latn"))
    same("and back", SwitchOverlay.markTap(on, "sr-latn"), SwitchPick.To("sr"))
    same("one input method: nothing", SwitchOverlay.markTap(on, "es"), null)
    val threeChinese = InputMethodIndex.parse("[" + listOf(entry("yue-yale", "zh"), entry("yue-jyutping", "zh"),
        entry("zh-pinyin", "zh")).joinToString(",") + "]").languagesOn("en,yue-yale,yue-jyutping,zh-pinyin")
    same("three: the language's row in the switcher", SwitchOverlay.markTap(threeChinese, "yue-yale"), SwitchPick.Row(threeChinese[1]))
    val serbianOnly = index.languagesOn("sr,sr-latn")
    same("one language with two: straight to its row", SwitchOverlay.opening(serbianOnly), serbianOnly[0])
    same("an index without languages: each its own", InputMethodIndex.parse(SPANISH_INDEX).languages.map { it.id }, listOf("en", "es"))
}

private const val SPANISH_INDEX = """[{"id":"es","name":"Español","nameEnglish":"Spanish","label":"ES","locale":"es","script":"Latn"}]"""

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
/**
 * The phone run's bug: after the switcher, the next Sym did nothing. A held Sym opens it, Android repeats the key while it's
 * held, it's let go, then Sym is pressed again: that press must wait for a hold as any first one does.
 */
private fun symWorksAfterTheSwitcher() {
    val sym = SymPress()
    var open = false
    same("Sym down waits for a hold", sym.press(0, open) { true }, SymPress.Press.Wait)
    same("its repeats before the hold time", sym.press(1, open) { true }, SymPress.Press.Swallow)
    check("the hold opens the switcher", sym.held())
    open = true
    for (repeat in 2..20) same("its repeat $repeat with the switcher open", sym.press(repeat, open) { true }, SymPress.Press.Swallow)
    same("its release keeps the switcher", sym.release(), SymPress.Release.Kept)
    check("nothing waits after the release", !sym.waiting)
    // A pick closes the switcher; then Sym again.
    open = false
    same("the next Sym waits again", sym.press(0, open) { true }, SymPress.Press.Wait)
    same("let go soon, it's Sym as usual", sym.release(), SymPress.Release.Usual)
    same("and the one after too", sym.press(0, open) { true }, SymPress.Press.Wait)
    check("held, it opens the switcher", sym.held())
    open = true
    same("released, the switcher stays", sym.release(), SymPress.Release.Kept)
    same("Sym with the switcher open closes it", sym.press(0, open) { true }, SymPress.Press.Close)
    open = false
    same("that press's repeats", sym.press(1, open) { true }, SymPress.Press.Swallow)
    same("its release", sym.release(), SymPress.Release.Kept)
    same("then Sym waits again", sym.press(0, open) { true }, SymPress.Press.Wait)
    sym.release()
    same("with one on, Sym is Sym", sym.press(0, open) { false }, SymPress.Press.Usual)
    same("its release isn't the switcher's", sym.release(), SymPress.Release.NotMine)
    same("a held Sym with one on repeats as usual", sym.press(3, open) { false }, SymPress.Press.Usual)
    sym.press(0, open) { true }
    sym.reset()
    same("a new field forgets a waiting press", sym.release(), SymPress.Release.NotMine)
}

/** The owner's bug: with the switcher open on English, a swipe still drew its trail and typed a word underneath. */
private fun overlaysKeepTheTouchpad() {
    for (open in listOf(Overlays.Open.Switcher, Overlays.Open.SwitcherRow, Overlays.Open.Accents, Overlays.Open.ChoicesMenu)) {
        check("$open: a swipe neither draws nor types", Overlays.stopsSwipes(open))
        check("$open: the touchpad only marks, as on the Sym panel", Overlays.paging(open, panelOpen = false))
    }
    check("nothing open: swipes type", !Overlays.stopsSwipes(Overlays.Open.None))
    check("nothing open: the touchpad swipes as usual", !Overlays.paging(Overlays.Open.None, panelOpen = false))
    check("the bar's choices keep their flick and page swipes", !Overlays.stopsSwipes(Overlays.Open.Choices))
    check("the bar's choices page the touchpad", Overlays.paging(Overlays.Open.Choices, panelOpen = false))
    check("the Sym panel pages it, overlay or not", Overlays.paging(Overlays.Open.None, panelOpen = true))
}

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

/** Thai's third layer (a held key types the Kedmanee key the Titan lacks) and finding the word being typed with no spaces. */
private fun thaiHoldsAndBreaks() {
    val thai = InputMethod.parse("""{"id":"th","name":"ไทย","nameEnglish":"Thai","label":"TH","locale":"th","script":"Thai",
        "keys":{"q":["ๆ","๐","ๅ","+"],"w":["ไ","\"","/","๑"],"k":["า","ษ"]},"accents":{},"words":null,"variants":null,
        "rules":{"capitalise":false,"wordBreak":true},"engine":null,"source":{}}""")
    same("Q types ๆ", thai.letter('q', false), "ๆ")
    same("Shift + Q types ๐, not a capital", thai.letter('q', true), "๐")
    same("held Q types ๅ (the 1 key)", thai.held('q', false), "ๅ")
    same("held Shift + Q types +", thai.held('q', true), "+")
    same("a slash can be a letter in a list", thai.held('w', false), "/")
    same("K has no third", thai.held('k', false), null)
    check("Thai breaks words", thai.rules.wordBreak)
    val words = setOf("ที่", "การ", "กิน", "ข้าว", "ไป", "ไหน")
    fun last(run: String) = WordBreak.last(run, { it in words }, { prefix -> words.any { it.startsWith(prefix) } })
    same("one word", last("กิน"), "กิน")
    same("the word after a whole one", last("กินข้"), "ข้")
    same("three words", last("ไปกินข้าว"), "ข้าว")
    same("a word started", last("ไปไห"), "ไห")
    same("a mark stays with its letter", last("กินขุ"), "ขุ")
    same("nothing", last(""), "")
    same("a fold keeps Thai's marks", WordList.fold("กิน", java.util.Locale("th")), "กิน")
    same("a fold still drops Latin accents", WordList.fold("Café", java.util.Locale.FRENCH), "cafe")
}

/** Hindi's rules, as jquery.ime runs them, and the keyboard's engine on them: a word, Space, Backspace and a choice. */
private fun hindiRulesType() {
    val file = File("android/assets/im/hi/rules.json")
    if (!file.isFile) return
    val rules = Rules.parse(file.readText())
    for ((keys, word) in listOf("namaste" to "नमस्ते", "raama" to "राम", "namaH" to "नमः", "madhumaChiyoM" to "मधुमक्खियों",
            "agar " to "अगर ", "agar~ " to "अगर् ", "//" to "ऽ", "raam," to "राम,")) same("rules: $keys", rules.spell(keys), word)
    val engine = RulesComposer(rules)
    check("starts with its rules", engine.start(InputMethod.ENGLISH, File(".")))
    for (c in "agar") engine.key(c)
    same("the word on the bar keeps its virama until Space", engine.code(), "अगर्")
    same("Space writes it without, and the space", engine.space().commit, "अगर ")
    for (c in "namastee") engine.key(c)
    same("Backspace takes the last key off", engine.backspace().code, "नमस्ते")
    same("a mark no rule takes ends the word", engine.key('!').commit, "नमस्ते")
}

/** Jquery.ime's own test cases for an input method, when $HI_FIXTURES names their JSON ([{input, output}]): how many pass. */
private fun rulesPassTheirFixtures(method: InputMethod, rules: Rules) {
    val path = System.getenv("HI_FIXTURES")?.takeIf { method.id == "hi" } ?: return
    @Suppress("UNCHECKED_CAST")
    val cases = com.titankeys.connected.Json.parse(File(path).readText()) as List<Map<String, Any?>>
    val failed = cases.filter { rules.spell(it["input"] as String) != it["output"] }
    println("${method.id}: jquery.ime's test cases: ${cases.size - failed.size} of ${cases.size} pass" +
        failed.take(5).joinToString("") { "; not ${it["input"]} -> ${it["output"]} (got ${rules.spell(it["input"] as String)})" })
}

/**
 * The commonest 300 words of an input method typed through rules (Hindi), as someone typing them by sound would: a search
 * over the keys for a spelling that gives each word, written by Space. Says the share found and the keys per letter.
 */
private fun rulesTypeTheList(method: InputMethod, dir: File, list: File) {
    val rules = Rules.parse(File(dir, method.engineArg ?: "rules.json").readText())
    rulesPassTheirFixtures(method, rules)
    val common = list.useLines { lines -> lines.filter { it.startsWith(" word=") }.map { it.removePrefix(" word=").substringBefore(',') }.take(300).toList() }
    var found = 0
    var keys = 0
    var letters = 0
    val missed = ArrayList<String>()
    for (word in common) {
        val spelling = keysFor(rules, word)
        if (spelling == null) missed += word else {
            found++
            keys += spelling.length
            letters += word.length
        }
    }
    val share = 100.0 * found / common.size
    println("${method.id}: typed by sound: %d of %d common words (%.1f%%), %.2f keys a letter; not: %s".format(found, common.size, share,
        keys.toDouble() / maxOf(letters, 1), missed.take(10).joinToString(" ")))
    check("${method.id}: its common words can be typed", share >= 90.0) { "%.1f%%".format(share) }
}

private val RULE_KEYS = ('a'..'z') + ('A'..'Z') + listOf('~', '.', '^', '_')

/** Keys that the rules turn into [word], with Space after: a beam search, keeping the spellings closest to it. */
private fun keysFor(rules: Rules, word: String): String? {
    var beam = listOf("" to Rules.State("", ""))
    repeat(word.length * 3 + 2) {
        val next = ArrayList<Triple<String, Rules.State, Int>>()
        for ((keys, state) in beam) for (k in RULE_KEYS) {
            val after = rules.step(state, k)
            if (rules.step(after, ' ').text.removeSuffix(" ") == word) return keys + k
            val common = after.text.commonPrefixWith(word).length
            if (after.text.length - common > rules.maxKeyLength) continue
            next += Triple(keys + k, after, common * 10 - keys.length)
        }
        beam = next.sortedByDescending { it.third }.distinctBy { it.second }.take(24).map { it.first to it.second }
        if (beam.isEmpty()) return null
    }
    return null
}

/** Hebrew: its final letters held on their own, and the bar and steering right to left. */
private fun hebrewRightToLeft() {
    val hebrew = InputMethod.parse("""{"id":"he","name":"עברית","nameEnglish":"Hebrew","label":"HE","locale":"he","script":"Hebr",
        "keys":{"q":["ת","ת","/","/"],"p":["פ","פ","ף","ף"],"e":"ק"},"accents":{},"words":null,"variants":null,
        "rules":{"capitalise":false},"engine":null,"source":{}}""")
    same("Q types ת", hebrew.letter('q', false), "ת")
    same("Shift types the same letter: Hebrew has no capitals", hebrew.letter('e', true), "ק")
    same("held P types the final ף", hebrew.held('p', false), "ף")
    check("Hebrew reads right to left", hebrew.rightToLeft)
    check("Spanish doesn't", !InputMethod.parse(SPANISH).rightToLeft)
    same("the bar left to right: second, best, third", BarOrder.of(false), listOf(1, 0, 2))
    same("right to left: the second best on the right", BarOrder.of(true), listOf(2, 0, 1))
    check("a Hebrew paragraph is right to left", RightToLeft.paragraph("שלום ", "עולם"))
    check("an English one isn't", !RightToLeft.paragraph("hello ", "world"))
    check("its first letter decides: Hebrew, then English", RightToLeft.paragraph("שלום hello", ""))
    check("a new line starts a new paragraph", !RightToLeft.paragraph("שלום\nhello", ""))
    check("digits and spaces decide nothing: the letter after does", RightToLeft.paragraph("12 ", "שלום"))
    check("a step right goes forward in English", RightToLeft.forward(1, false))
    check("a step right goes back in Hebrew", !RightToLeft.forward(1, true))
    check("a step left goes forward in Hebrew", RightToLeft.forward(-1, true))
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
/** Scripts Settings names in English, as Ink can't shape them yet (app/settings/languages.tsx). */
private val UNSHAPED = setOf("Deva")

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
    // Devanagari's names show in English in Settings (app/settings/languages.tsx, UNSHAPED): Ink can't shape them yet.
    val missing = if (method.script in UNSHAPED) emptyList() else name.codePoints().toArray().filter { code -> textFonts.any { !it.canDisplay(code) } }
    check("${method.id}: the app's fonts have its name's letters", missing.isEmpty()) {
        missing.joinToString(" ") { String(Character.toChars(it)) }
    }
}

/**
 * In a script with no spaces (Thai): after one of the list's 2,000 commonest words, another of them is typed, and the word
 * being typed must be found, as the bar asks. Says the share found for any start of the word (a letter or two can also
 * continue the word before, as แม้ + น is แม้น) and for the whole word; at least 80% and 90% pass.
 */
private fun wordBreakFindsTheWord(method: InputMethod, read: WordList, list: File) {
    val common = list.useLines { lines -> lines.filter { it.startsWith(" word=") }.map { it.removePrefix(" word=").substringBefore(',') }.take(2_000).toList() }
    val random = java.util.Random(56)
    var started = 0
    var whole = 0
    val tries = 5_000
    repeat(tries) {
        val first = common[random.nextInt(common.size)]
        val second = common[random.nextInt(common.size)]
        // A start of the second word that doesn't end before a mark, as typing it would show.
        val cuts = (1..second.length).filter { it == second.length || Character.getType(second[it]).let { type ->
            type != Character.NON_SPACING_MARK.toInt() && type != Character.COMBINING_SPACING_MARK.toInt() } }
        val typing = second.substring(0, cuts[random.nextInt(cuts.size)])
        if (WordBreak.last(first + typing, read::has, read::hasPrefix) == typing) started++
        if (WordBreak.last(first + second, read::has, read::hasPrefix) == second) whole++
    }
    val startShare = 100.0 * started / tries
    val wholeShare = 100.0 * whole / tries
    println("${method.id}: the word being typed found after another: %.1f%% of starts, %.1f%% of whole words (%d tries)".format(startShare, wholeShare, tries))
    check("${method.id}: a word's start is found", startShare >= 80.0) { "%.1f%%".format(startShare) }
    check("${method.id}: a whole word is found", wholeShare >= 90.0) { "%.1f%%".format(wholeShare) }
}

/**
 * Shift and Caps show only where Shift changes what a key types (owner, 2026-11-17): every input method but Hebrew (Shift
 * types the same letter) and the Chinese and Japanese ones (Shift opens the bigger menu). Korean, Hindi, Thai and Georgian
 * keep it: their Shift types other letters.
 */
private fun shiftShowsWhereItMatters(folder: File) {
    val without = folder.listFiles { file -> file.isDirectory }.orEmpty().mapNotNull { dir ->
        runCatching { InputMethod.parse(File(dir, "im.json").readText()) }.getOrNull()
    }.filter { !it.shiftMatters }.map { it.id }.sorted()
    same("the input methods without Shift on the bar", without, listOf("he", "ja", "yue-jyutping", "yue-yale", "zh-pinyin"))
    check("English shows Shift", InputMethod.ENGLISH.shiftMatters)
}

/** Collects garbage until the weakly held word lists settle. */
private fun collect() = repeat(4) {
    System.gc()
    Thread.sleep(50)
}

private fun usedMb(): Long = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / 1_048_576 }

/**
 * The phone's OutOfMemoryError after 18 switches (2026-11-17): 30 switches through the input methods with word lists, loaded
 * as the keyboard loads them ([WordListSlot]), must keep one list at most, and the heap must not grow with them.
 */
private fun switchingKeepsOneList(folder: File) {
    val methods = folder.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }.mapNotNull { dir ->
        runCatching { InputMethod.parse(File(dir, "im.json").readText()) }.getOrNull()
            ?.takeIf { it.words != null && it.engine == null }?.let { it to File(dir, it.words!!) }
    }
    check("enough input methods with word lists for 30 switches", methods.size >= 15) { "${methods.size}" }
    val slot = WordListSlot()
    collect()
    val before = usedMb()
    var most = 0
    var after5 = 0L
    repeat(30) { switch ->
        val (method, file) = methods[switch % methods.size]
        val ticket = slot.begin(method.id)
        // A switch that a later one overtakes is dropped as its list arrives.
        if (switch % 7 == 3) {
            val overtaken = WordList.read(file.useLines { it.toList() }.asSequence(), method.javaLocale)
            val next = methods[(switch + 1) % methods.size].first
            val later = slot.begin(next.id)
            check("an overtaken load is dropped", !slot.done(ticket, overtaken))
            slot.done(later, null)
            slot.begin(method.id)
        }
        val list = WordList.read(file.useLines { it.toList() }.asSequence(), method.javaLocale)
        slot.done(slot.begin(method.id), list)
        collect()
        most = maxOf(most, WordList.alive().size)
        if (switch == 4) after5 = usedMb()
    }
    val after30 = usedMb()
    println("switching: 30 switches through ${methods.size} input methods, at most $most word list(s) held, heap ${before} MB before, " +
        "$after5 MB after 5, $after30 MB after 30")
    check("one word list at most in memory after each switch", most <= 1) { "$most" }
    check("the heap doesn't grow with switches", after30 - after5 <= 40) { "$after5 MB after 5, $after30 MB after 30" }
    slot.clear()
    collect()
    same("no word list once the keyboard goes", WordList.alive().size, 0)
}

private fun everyInputMethodInTheFolder(folder: File) {
    val methods = folder.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }
    val labels = HashMap<String, String>()
    @Suppress("UNCHECKED_CAST")
    val index = (com.titankeys.connected.Json.parse(File(folder, "index.json").readText()) as List<Map<String, Any?>>)
        .associate { it["id"] as String to it["name"] as String? }
    @Suppress("UNCHECKED_CAST")
    val languageNames = (com.titankeys.connected.Json.parse(File(folder, "index.json").readText()) as List<Map<String, Any?>>)
        .mapNotNull { (it["language"] as? Map<String, Any?>)?.takeIf { language -> language["script"] !in UNSHAPED }?.get("name") as? String }.toSet()
    for (name in languageNames) {
        val missing = name.codePoints().toArray().filter { code -> textFonts.any { !it.canDisplay(code) } }
        check("language $name: the app's fonts have its letters", missing.isEmpty()) { missing.joinToString(" ") { String(Character.toChars(it)) } }
    }
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
        if (method.engine == "rules") {
            rulesTypeTheList(method, dir, File(dir, words))
            continue
        }
        if (method.engine == "telex") {
            telexTypesTheList(method, File(dir, words))
            continue
        }
        val list = File(dir, words)
        check("${method.id}: its word list is there", list.isFile)
        if (!list.isFile) continue
        val read = WordList.read(list.useLines { it.toList() }.asSequence(), method.javaLocale)
        if (method.rules.wordBreak) wordBreakFindsTheWord(method, read, list)
        check("${method.id}: its word list has words", read.size > 1000) { "${read.size}" }
        check("${method.id}: at most 100,000 words", read.size <= 100_000) { "${read.size}" }
        check("${method.id}: at most 4 MB", list.length() <= 4_000_000) { "${list.length()}" }
        // Every letter the list uses often can be typed: from a key, with Shift where Shift gives another (Georgian), or held on one.
        val typable = HashSet<String>()
        for (key in 'a'..'z') {
            typable += method.letter(key, false) ?: key.toString()
            method.keys?.get(key)?.shift?.let { typable += it }
            typable += method.accentsOf(key, false)
            // Thai's third layer: held, and held with Shift.
            method.held(key, false)?.let { typable += it }
            method.held(key, true)?.let { typable += it }
        }
        val counts = HashMap<String, Int>()
        list.useLines { lines ->
            lines.filter { it.startsWith(" word=") }.take(50_000).forEach { line ->
                val word = line.removePrefix(" word=").substringBefore(',').lowercase(method.javaLocale)
                // Thai types its vowels and tones as keys of their own, so each counts by itself, not with its consonant.
                val letters = if (method.rules.wordBreak) word.codePoints().toArray().map { String(Character.toChars(it)) }
                    else InputMethod.graphemes(word)
                for (letter in letters) if (letter.first().isLetter() || method.rules.wordBreak) counts.merge(letter, 1, Int::plus)
            }
        }
        val missing = counts.filter { (letter, count) -> count >= 20 && letter !in typable }.keys.sorted()
        check("${method.id}: every common letter can be typed", missing.isEmpty()) { missing.joinToString(" ") }
    }
    println("languages: ${methods.size} input methods checked")
}
