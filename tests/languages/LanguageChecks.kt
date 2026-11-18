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
    barLogLines()
    theFileReads()
    howToReads()
    badFilesSayWhy()
    keysTypeTheirLetters()
    accentsKeepTheirOrderAndCase()
    englishIsAlwaysThereAndFirst()
    turningOnAndOff()
    theOverlaySpansTwoKeysWhenFewAreOn()
    aTapPicksTheTileUnderIt()
    languagesGroupTheirInputMethods()
    theAccentsSitOverTheHeldKey()
    englishTypingRows()
    wordsCompleteIgnoringAccents()
    anEngineFitsTheInterface()
    hangulBuildsSyllables()
    keysWaitForALoadingEngine()
    aRestoredInputMethodTypes()
    thaiHoldsAndBreaks()
    hindiRulesType()
    hebrewRightToLeft()
    aShiftedLetterIsTheKeyboards()
    aHeldSymWaits()
    overlaysKeepTheTouchpad()
    symWorksAfterTheSwitcher()
    symClearsACodeFirst()
    heldKeysShowAsPlainLetters()
    androidsListFollowsTheOnes()
    telexMarksAndTones()
    barWordsAndLearning()
    damagedTablesFailAtLoad()
    koreanChoices()
    if (args.isNotEmpty()) everyInputMethodHasStarters(File(args[0]))
    if (args.isNotEmpty()) everyInputMethodInTheFolder(File(args[0]))
    if (args.isNotEmpty()) everyTextShows(File(args[0]))
    if (args.isNotEmpty()) shiftShowsWhereItMatters(File(args[0]))
    if (args.isNotEmpty()) tablesTakeTheListsSpelling(File(args[0]))
    if (args.isNotEmpty()) nextWordsCost(File(args[0]))
    if (args.isNotEmpty()) completionsMatchTheFold(File(args[0]))
    if (args.isNotEmpty()) turkishTablesLowerTheTurkishWay(File(args[0]))
    if (args.isNotEmpty()) switchingKeepsOneList(File(args[0]))
    if (args.isNotEmpty()) eachLanguageHasItsFullStop(File(args[0]))
    if (args.isNotEmpty()) thaiHebrewAndHindiNextWords(File(args[0]))
    if (args.isNotEmpty()) cyrillicHoldsReachEveryLetter(File(args[0]))
    if (args.isNotEmpty()) holdHints(File(args[0]))
    if (args.isNotEmpty()) everyGuideIsComplete(File(args[0]))
    if (args.isNotEmpty()) noBlockedWordIsOffered(File(args[0]))
    if (args.isNotEmpty()) onlyTheOnesOnStayWarm(File(args[0]))
    if (args.isNotEmpty()) variantsArePicked(File(args[0]))
    if (failures > 0) {
        System.err.println("$failures failed")
        kotlin.system.exitProcess(1)
    }
    println("languages: all passed")
}

/** An input method's how-to: an optional list of lines in its im.json and the index. */
private fun howToReads() {
    val method = InputMethod.parse(SPANISH.trimEnd().removeSuffix("}") + ""","howTo":["Hold a letter for its accents.","Shift gives the capital one."]}""")
    same("an im.json's how-to", method.howTo, listOf("Hold a letter for its accents.", "Shift gives the capital one."))
    same("none: empty", InputMethod.parse(SPANISH).howTo, emptyList<String>())
    val index = InputMethodIndex.parse("""[{"id":"es","name":"Español","nameEnglish":"Spanish","label":"ES","locale":"es","script":"Latn","howTo":["Hold N for ñ."]}]""")
    same("the index carries it", index["es"]?.howTo, listOf("Hold N for ñ."))
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
    // A held letter with no accents types once in an input method with accents (owner, 2026-11-17): Spanish's W holds nothing,
    // so TitanKeysIme.accentlessRepeat takes its repeats, while N still opens its accents.
    val es = InputMethod.parse(SPANISH)
    check("Spanish has accents", es.hasAccents)
    same("Spanish w holds nothing", es.accentsOf('w', false), emptyList<String>())
    same("Spanish n holds ñ", es.accentsOf('n', false), listOf("ñ"))
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
    val six = InputMethodIndex.parse("[" + listOf("es", "fr", "de", "it", "pt").joinToString(",") { id ->
        """{"id":"$id","name":"$id","nameEnglish":"$id","label":"${id.uppercase()}","locale":"$id","script":"Latn"}"""
    } + "]")
    same("a setting with more than four keeps them all", six.on("en,es,fr,de,it,pt").size, 6)
    same("move up", InputMethodIndex.move(listOf("en", "es", "fr"), "fr", up = true), listOf("en", "fr", "es"))
    same("the first can't move up", InputMethodIndex.move(listOf("en", "es"), "en", up = true), listOf("en", "es"))
}

/** A finger's tap on the screen picks what a key under it picks: each tile's span is its keys', edge to edge. */
private fun aTapPicksTheTileUnderIt() {
    val key = 108f
    val cell = { column: Int -> column * key to (column + 1) * key }
    for (count in 1..4) {
        val columns = SwitchOverlay.columns(count)
        for ((tile, span) in columns.withIndex()) {
            val middle = (span.first + span.last + 1) * key / 2
            same("$count tiles: a tap in tile $tile's middle picks it", Picker.itemAt(middle, columns, count, cell), tile)
            for (letter in listOf(Layout.rows[0][span.first], Layout.rows[1][span.first]))
                same("$count tiles: the key $letter picks the same tile", SwitchOverlay.choice(letter, count), tile)
        }
    }
    same("Q's place picks nothing", Picker.itemAt(key / 2, SwitchOverlay.columns(2), 2, cell), -1)
    same("P's place picks nothing", Picker.itemAt(9.5f * key, SwitchOverlay.columns(2), 2, cell), -1)
    same("with three, W's place picks nothing", Picker.itemAt(1.5f * key, SwitchOverlay.columns(3), 3, cell), -1)
    same("a column past the items picks nothing", Picker.itemAt(7.5f * key, SwitchOverlay.columns(4), 2, cell), -1)
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
    // The mark at Q: the language's where it has one input method on, the input method's where it has more.
    val zh = InputMethodIndex.parse("[" + listOf(entry("yue-yale", "zh"), entry("zh-pinyin", "zh"), entry("es", "es")).joinToString(",") + "]")
    same("one Chinese on: the language's mark", SwitchOverlay.markLabel(zh.on("en,yue-yale,es"), "yue-yale", "耶"), "ZH")
    same("two Chinese on: the input method's", SwitchOverlay.markLabel(zh.on("en,yue-yale,zh-pinyin"), "yue-yale", "耶"), "耶")
    same("a language of one input method: the same either way", SwitchOverlay.markLabel(zh.on("en,es"), "es", "ES"), "ES")
    fun serbian(id: String, label: String, script: String) = """{"id":"$id","name":"$id","nameEnglish":"$id","label":"$label",""" +
        """"locale":"sr","script":"$script","language":{"id":"sr","name":"Српски","nameEnglish":"Serbian","label":"СР",""" +
        """"script":"Cyrl","experimental":true}}"""
    val serbianIndex = InputMethodIndex.parse("[" + serbian("sr", "СР", "Cyrl") + "," + serbian("sr-latn", "SR", "Latn") + "]")
    same("Serbian in Latin letters alone: its own SR, not the language's Cyrillic СР",
        SwitchOverlay.markLabel(serbianIndex.on("en,sr-latn"), "sr-latn", "SR"), "SR")
    same("Serbian in Cyrillic alone: the language's СР", SwitchOverlay.markLabel(serbianIndex.on("en,sr"), "sr", "СР"), "СР")
    val serbianOnly = index.languagesOn("sr,sr-latn")
    same("one language with two: straight to its row", SwitchOverlay.opening(serbianOnly), serbianOnly[0])
    same("an index without languages: each its own", InputMethodIndex.parse(SPANISH_INDEX).languages.map { it.id }, listOf("en", "es"))
}

private const val SPANISH_INDEX = """[{"id":"es","name":"Español","nameEnglish":"Spanish","label":"ES","locale":"es","script":"Latn"}]"""

private fun theAccentsSitOverTheHeldKey() {
    same("one accent over E", Picker.accentColumns(1, Picker.anyColumn("E")), listOf(2))
    same("two over U", Picker.accentColumns(2, Picker.anyColumn("U")), listOf(6, 7))
    // Never over Q's or P's column: W to O, the switcher's range (owner, 2026-11-17).
    same("four over A move in to W", Picker.accentColumns(4, Picker.anyColumn("A")), listOf(1, 2, 3, 4))
    same("four over P move in to end at O", Picker.accentColumns(4, Picker.anyColumn("P")), listOf(5, 6, 7, 8))
    same("one over Q sits over W", Picker.accentColumns(1, Picker.anyColumn("Q")), listOf(1))
    same("three over Q start at W", Picker.accentColumns(3, Picker.anyColumn("Q")), listOf(1, 2, 3))
    same("one over P sits over O", Picker.accentColumns(1, Picker.anyColumn("P")), listOf(8))
    same("three over P end at O", Picker.accentColumns(3, Picker.anyColumn("P")), listOf(6, 7, 8))
    same("eight fill W to O", Picker.accentColumns(8, Picker.anyColumn("G")), (1..8).toList())
    same("eight over Q still fill W to O", Picker.accentColumns(8, Picker.anyColumn("Q")), (1..8).toList())
    same("more than eight are cut to eight", Picker.accentColumns(14, 3), (1..8).toList())
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
    check("the panel open: a hold still opens the switcher, a tap closes the panel (Master, 2026-11-17)", why(panel = true) == null)
    same("a tap with a code pending clears it", SymHold.tap(codePending = true), SymHold.Tap.ClearCode)
    same("a tap with none is Sym as usual", SymHold.tap(codePending = false), SymHold.Tap.Usual)
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
    // The bar shows what Space types first (#98): the word as Space writes it, then the list's.
    val hiWords = java.util.Locale.forLanguageTag("hi")
    val listed = RulesComposer(rules, WordList.read(sequenceOf(" word=नमस्ते,f=200", " word=अगरबत्ती,f=100"), hiWords))
    same("The row starts with the word as Space writes it", "agar".map { listed.key(it) }.last().choices.first(), "अगर")
    same("Picking it writes it as Space does", listed.pick(0).commit, "अगर ")
    // A slipped key: the word one slip away, on the bar only. Space writes what was typed.
    val slipped = "namastr".map { listed.key(it) }.last().choices
    same("A neighbouring key's word is offered after the word as typed", slipped.drop(1), listOf("नमस्ते"))
    same("Space writes what was typed", listed.space().commit, rules.spell("namastr "))
    for (c in "namastee") engine.key(c)
    same("Backspace takes the last key off", engine.backspace().code, "नमस्ते")
    same("a mark no rule takes ends the word", engine.key('!').commit, "नमस्ते")
    // The bigger menu, opened with a swipe up: the word list's words past the bar's eight, and a pick from any of them.
    val hi = java.util.Locale.forLanguageTag("hi")
    val code = RulesComposer(rules).apply { "nama".forEach { key(it) } }.code()
    val many = RulesComposer(rules, WordList.read("कखगचजटडतदपबमयरलवसह".map { "$code$it" }.asSequence()
        .mapIndexed { i, w -> " word=$w,f=${1000 - i}" }, hi))
    val bar = "nama".map { many.key(it) }.last().choices
    same("the bar has eight", bar.size, RulesComposer.CHOICES)
    same("the menu's first page is the bar's, in order", many.choices(0, 8), bar)
    same("the menu reads past them", many.choices(8, 4).size, 4)
    val ninth = many.choices(9, 1).single()
    same("a pick from the menu, past the bar's eight", many.pickAt(9).commit, "$ninth ")
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
    // The bar shows what Space types first (#98): the word as typed, then the list's.
    same("Unmarked code offers the marked word, after the word as typed", "tieng".map { engine.key(it) }.last().choices,
        listOf("tieng", "tiếng"))
    same("Picking writes it with a space", engine.pick(1).commit, "tiếng ")
    "tieng".forEach { engine.key(it) }
    same("Picking the first writes the word as typed, as Space does", engine.pick(0).commit, "tieng ")
    // A slipped key: the word one slip away, on the bar only. Space writes what was typed.
    val slips = TelexComposer(WordList.read(sequenceOf(" word=xin,f=200"), vi))
    same("A neighbouring key's word is offered after the word as typed", "xon".map { slips.key(it) }.last().choices, listOf("xon", "xin"))
    same("Space writes what was typed", slips.space().commit, "xon")
    // The bigger menu, opened with a swipe up: the word list's words past the bar's eight, and a pick from any of them.
    val many = TelexComposer(WordList.read(("bcdgklmnp".flatMap { c -> "aeiou".map { v -> "ta$c$v" } }).asSequence()
        .mapIndexed { i, w -> " word=$w,f=${1000 - i}" }, vi))
    val bar = "ta".map { many.key(it) }.last().choices
    same("The bar has eight", bar.size, TelexComposer.CHOICES)
    same("The menu's first page is the bar's, in order", many.choices(0, 8), bar)
    same("The menu reads past them", many.choices(8, 4).size, 4)
    val ninth = many.choices(9, 1).single()
    same("A pick from the menu, past the bar's eight", many.pickAt(9).commit, "$ninth ")
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
 * Every character of every input method's own text shows: its how-to and every other text in its im.json, in the app's fonts,
 * since Android's fonts don't reach Ink on the phone (owner, 2026-11-17: Korean's how-to had blanks for its letters). A character
 * with no glyph fails here, with the input method and the character, so tools/im/fonts.py runs again.
 */
private fun everyTextShows(folder: File) {
    fun walk(value: Any?, out: MutableList<String>) {
        when (value) {
            is String -> out += value
            is List<*> -> for (item in value) walk(item, out)
            is Map<*, *> -> for (item in value.values) walk(item, out)
        }
    }
    for (dir in folder.listFiles().orEmpty().filter { File(it, "im.json").isFile }) {
        val json = Json.parse(File(dir, "im.json").readText()) as Map<*, *>
        val texts = ArrayList<String>()
        walk(json["howTo"], texts)
        // A Devanagari name shows in English in Settings (UNSHAPED), so only its how-to's letters need the fonts.
        if (json["script"] !in UNSHAPED) for (key in listOf("name", "nameEnglish")) (json[key] as? String)?.let(texts::add)
        val missing = texts.joinToString(" ").codePoints().toArray().filter { code ->
            !Character.isWhitespace(code) && code > 0x7F && textFonts.any { !it.canDisplay(code) }
        }.distinct()
        check("${dir.name}: every character of its texts has a glyph in the app's fonts", missing.isEmpty()) {
            missing.joinToString(" ") { "U+%04X %s".format(it, String(Character.toChars(it))) }
        }
    }
}

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
    same("the input methods without Shift on the bar", without,
        listOf("he", "ja", "yue-jyutping", "yue-yale", "zh-cangjie", "zh-pinyin", "zh-quick", "zh-zhuyin"))
    check("English shows Shift", InputMethod.ENGLISH.shiftMatters)
}

/** Collects garbage until the weakly held word lists settle. */
private fun collect() = repeat(4) {
    System.gc()
    Thread.sleep(50)
}

private fun usedMb(): Long = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / 1_048_576 }

/**
 * Turkish and Azerbaijani lower İ to i and I to ı, in the tables and in the lookup (E's review, 2026-11-17): Python's and
 * Kotlin's own rules would give i with a combining dot for İ, and i for I, which no Turkish word list holds.
 */
private fun turkishTablesLowerTheTurkishWay(folder: File) {
    for (dir in folder.listFiles { file -> file.isDirectory }.orEmpty()) {
        for (file in dir.listFiles { file -> file.name.startsWith("pairs") && file.name.endsWith(".bin") }.orEmpty()) {
            var dotted = 0
            file.inputStream().use { NextWords.readBinary(it) }.forEach { first, next, _ ->
                if ('̇' in first || '̇' in next) dotted++
            }
            check("${dir.name}/${file.name}: no word with a combining dot", dotted == 0) { "$dotted pairs" }
        }
    }
    val turkish = File(folder, "tr/pairs.bin").inputStream().use { NextWords.readBinary(it, turkic = true) }
    same("tr: İstanbul finds its next words", turkish.after("İstanbul").isNotEmpty() && turkish.after("İstanbul") == turkish.after("istanbul"), true)
    same("tr: Işık, with I, finds ışık's", turkish.after("Işık").isNotEmpty() && turkish.after("Işık") == turkish.after("ışık"), true)
    same("tr: İlk at a sentence's start", turkish.after("İlk") == turkish.after("ilk"), true)
    same("the Turkish rule", NextWords.lower("IŞIK İLK", turkic = true), "ışık ilk")
    same("other languages keep Kotlin's", NextWords.lower("Isla", turkic = false), "isla")
}

/**
 * WordList stores each word's folded length, so complete() ranks without folding the words again (2026-11-17): the same answers
 * as the way before, which folded every match inside its sort, for every 1 to 4 letter start of the commonest 3,000 words,
 * lower case and capitalised, in several scripts. Prints how long a completion takes.
 */
private fun completionsMatchTheFold(folder: File) {
    for (id in listOf("ka", "ko", "ru", "de", "vi", "hi")) {
        val dir = File(folder, id)
        val method = runCatching { InputMethod.parse(File(dir, "im.json").readText()) }.getOrNull() ?: continue
        val file = File(dir, method.words ?: continue)
        val locale = method.javaLocale
        val lines = file.readLines()
        val list = WordList.read(lines.asSequence(), locale)
        // The way before, step for step: matches in folded order, at most 20,000, ranked by folding each one again.
        val words = ArrayList<String>()
        val counts = ArrayList<Int>()
        for (line in lines) {
            if (!line.startsWith(" word=")) continue
            val fields = line.trim().split(',')
            val word = fields.firstOrNull { it.startsWith("word=") }?.removePrefix("word=") ?: continue
            if (word.isEmpty() || fields.any { it == "not_a_word=true" }) continue
            words += word
            counts += fields.firstOrNull { it.startsWith("f=") }?.removePrefix("f=")?.toIntOrNull() ?: 0
        }
        val folded = words.map { WordList.fold(it, locale) }
        val order = folded.indices.sortedBy { folded[it] }
        val sortedFolds = order.map { folded[it] }
        fun before(prefix: String): List<String> {
            val key = WordList.fold(prefix, locale)
            var low = 0
            var high = sortedFolds.size
            while (low < high) { val mid = (low + high) ushr 1; if (sortedFolds[mid] < key) low = mid + 1 else high = mid }
            val found = ArrayList<Int>()
            while (low < sortedFolds.size && sortedFolds[low].startsWith(key) && found.size < 20_000) found += order[low++]
            val ranked = found.sortedWith(compareBy<Int>({ WordList.fold(words[it], locale).length != key.length }, { -counts[it] }))
            val picked = LinkedHashSet<String>()
            for (i in ranked) {
                if (picked.size >= 3) break
                val w = words[i]
                picked += when {
                    prefix.length > 1 && prefix.all { !it.isLetter() || it.isUpperCase() } -> w.uppercase(locale)
                    prefix.first().isUpperCase() -> w.replaceFirstChar { it.titlecase(locale) }
                    else -> w
                }
            }
            return picked.toList()
        }
        val prefixes = words.take(3000).flatMap { w -> (1..minOf(4, w.length)).map { w.take(it) } }.distinct()
            .flatMap { listOf(it, it.replaceFirstChar { c -> c.titlecase(locale) }) }.distinct()
        val differ = prefixes.count { list.complete(it) != before(it) }
        check("$id: completions are the same as before", differ == 0) { "$differ of ${prefixes.size} differ" }
        fun micros(starts: List<String>, f: (String) -> List<String>): Double {
            repeat(2) { starts.forEach { f(it) } }
            val started = System.nanoTime()
            repeat(3) { starts.forEach { f(it) } }
            return (System.nanoTime() - started) / 1000.0 / (3 * starts.size)
        }
        val one = prefixes.filter { it.length == 1 }
        println("completions: $id: ${prefixes.size} prefixes, $differ differ; ${"%.0f".format(micros(prefixes) { list.complete(it) })} us each " +
            "(before ${"%.0f".format(micros(prefixes, ::before))}), one letter ${"%.0f".format(micros(one) { list.complete(it) })} us " +
            "(before ${"%.0f".format(micros(one, ::before))})")
    }
}

private fun usedBytes(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

/**
 * What one input method's word list and next-word table take on the heap, each read by the app's own reader, and how long each
 * load takes (the first load, then a second once the JIT has warmed up). The budget: 40 MB for both, 300 ms for the table.
 */
/** The shipped tables through their word lists: German's nouns get their capitals, and Turkish has no doubly dotted i left. */
private fun tablesTakeTheListsSpelling(folder: File) {
    fun load(id: String): Pair<WordList, NextWords> {
        val dir = File(folder, id)
        val method = InputMethod.parse(File(dir, "im.json").readText())
        val list = File(dir, method.words!!).bufferedReader().useLines { WordList.read(it, method.javaLocale) }
        return list to File(dir, "pairs.bin").inputStream().use { NextWords.readBinary(it) }
    }
    val (germanList, germanTable) = load("de")
    // Every next word the table has after "das", as the bar spells them: Land, Team and Spiel are nouns the list holds.
    val afterDas = germanTable.after("das").map(germanList::spelling)
    same("German: after das, Land, Team and Spiel keep their capitals: $afterDas", listOf("Land", "Team", "Spiel").all { it in afterDas }, true)
    val (turkishList, turkishTable) = load("tr")
    val afterAbdi = BarWords.next("abdi", null, emptyList(), capitals = true, table = turkishTable, spelling = turkishList::spelling)
    same("Turkish: after abdi, no doubly dotted i: $afterAbdi", afterAbdi.none { '\u0307' in it }, true)
}

private fun nextWordsCost(folder: File) {
    for (id in listOf("ka", "ko", "sr", "el", "ru")) {
        val dir = File(folder, id)
        val method = runCatching { InputMethod.parse(File(dir, "im.json").readText()) }.getOrNull() ?: continue
        val listFile = File(dir, method.words ?: continue)
        val tableFile = File(dir, "pairs.bin")
        if (!tableFile.isFile) continue
        // The table alone first, on a collected heap, then the list beside it.
        collect()
        val base = usedBytes()
        var started = System.nanoTime()
        var table: NextWords? = tableFile.inputStream().use { NextWords.readBinary(it) }
        val firstMillis = (System.nanoTime() - started) / 1_000_000
        collect()
        val withTable = usedBytes()
        started = System.nanoTime()
        val again = tableFile.inputStream().use { NextWords.readBinary(it) }
        val secondMillis = (System.nanoTime() - started) / 1_000_000
        started = System.nanoTime()
        var list: WordList? = WordList.read(listFile.useLines { it.toList() }.asSequence(), method.javaLocale)
        val listMillis = (System.nanoTime() - started) / 1_000_000
        collect()
        val withBoth = usedBytes() - again.bytes
        val tableMb = (withTable - base) / 1_048_576.0
        val listMb = (withBoth - withTable) / 1_048_576.0
        println("next words: $id: table ${"%.1f".format(tableFile.length() / 1e6)} MB as a file, ${table!!.size} first words, " +
            "arrays ${"%.1f".format(table.bytes / 1_048_576.0)} MB; heap: table ${"%.1f".format(tableMb)} MB " +
            "(${firstMillis} ms first, ${secondMillis} ms again), list ${"%.1f".format(listMb)} MB (${listMillis} ms); " +
            "together ${"%.1f".format(listMb + tableMb)} MB")
        check("$id: list and table under 40 MB of heap", listMb + tableMb <= 40) { "%.1f MB".format(listMb + tableMb) }
        check("$id: the table loads in under 300 ms", secondMillis <= 300) { "$secondMillis ms" }
        check("$id: a reload reads the same table", again.size == table.size)
        list = null
        table = null
        if (list == null && table == null) collect()
    }    // English's own table, read by its own reader (PairCounts), for comparison: it isn't one of these, and loads with the engine.
    val english = File(folder.parentFile, "autocorrect/en_pairs.bin")
    if (english.isFile) {
        collect()
        val base = usedBytes()
        var started = System.nanoTime()
        var pairs: com.titankeys.autocorrect.PairCounts? = english.inputStream().use { com.titankeys.autocorrect.PairCounts.readBinary(it) }
        val firstMillis = (System.nanoTime() - started) / 1_000_000
        collect()
        val heap = (usedBytes() - base) / 1_048_576.0
        started = System.nanoTime()
        val again = english.inputStream().use { com.titankeys.autocorrect.PairCounts.readBinary(it) }
        val secondMillis = (System.nanoTime() - started) / 1_000_000
        println("next words: en (PairCounts): ${"%.1f".format(english.length() / 1e6)} MB as a file, ${pairs!!.size} pairs, " +
            "arrays ${"%.1f".format(pairs.bytes / 1_048_576.0)} MB; heap ${"%.1f".format(heap)} MB (${firstMillis} ms first, ${secondMillis} ms again)")
        check("en: its pairs load in under 300 ms", secondMillis <= 300) { "$secondMillis ms" }
        same("en: a known pair's count", pairs.count("of", "the") > 0, true)
        check("en: a reload reads the same pairs", again.size == pairs.size)
        pairs = null
        if (pairs == null) collect()
    }
}

/**
 * The phone's OutOfMemoryError after 18 switches (2026-11-17): 30 switches through the input methods with word lists, loaded
 * as the keyboard loads them ([WordListSlot]), must keep one list at most, and the heap must not grow with them.
 */
private fun switchingKeepsOneList(folder: File) {
    val methods = folder.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }.mapNotNull { dir ->
        runCatching { InputMethod.parse(File(dir, "im.json").readText()) }.getOrNull()
            ?.takeIf { it.words != null && it.engine == null }?.let { Triple(it, File(dir, it.words!!), File(dir, "pairs.bin")) }
    }
    check("enough input methods with word lists for 30 switches", methods.size >= 15) { "${methods.size}" }
    check("most of them have next-word tables", methods.count { it.third.isFile } >= 15)
    val slot = WordListSlot()
    // The table follows Languages.use: dropped as a switch to another input method begins, never loaded for a switch that a
    // later one overtook before its turn, and kept only when its load is the latest switch's.
    var table: NextWords? = null
    collect()
    val before = usedMb()
    var most = 0
    var mostTables = 0
    var peak = 0L
    var after5 = 0L
    repeat(30) { switch ->
        val (method, file, pairs) = methods[switch % methods.size]
        val ticket = slot.begin(method.id)
        table = null
        // A switch that a later one overtakes is dropped as its list arrives; its table is never read.
        if (switch % 7 == 3) {
            val overtaken = WordList.read(file.useLines { it.toList() }.asSequence(), method.javaLocale)
            val next = methods[(switch + 1) % methods.size].first
            val later = slot.begin(next.id)
            check("an overtaken load is dropped", !slot.done(ticket, overtaken))
            slot.done(later, null)
            slot.begin(method.id)
        }
        val list = WordList.read(file.useLines { it.toList() }.asSequence(), method.javaLocale)
        val loaded = if (pairs.isFile) pairs.inputStream().use { NextWords.readBinary(it) } else null
        peak = maxOf(peak, usedMb())
        if (slot.done(slot.begin(method.id), list)) table = loaded
        collect()
        most = maxOf(most, WordList.alive().size)
        mostTables = maxOf(mostTables, NextWords.alive().size)
        peak = maxOf(peak, usedMb())
        if (switch == 4) after5 = usedMb()
    }
    val after30 = usedMb()
    println("switching: 30 switches through ${methods.size} input methods, at most $most word list(s) and $mostTables table(s) held, " +
        "heap ${before} MB before, $after5 MB after 5, $after30 MB after 30, peak $peak MB")
    check("one word list at most in memory after each switch", most <= 1) { "$most" }
    check("one next-word table at most in memory after each switch", mostTables <= 1) { "$mostTables" }
    check("the heap doesn't grow with switches", after30 - after5 <= 40) { "$after5 MB after 5, $after30 MB after 30" }
    slot.clear()
    table = null
    collect()
    same("no word list once the keyboard goes", WordList.alive().size, 0)
    same("no next-word table once the keyboard goes", NextWords.alive().size + (if (table == null) 0 else 1), 0)
}

/** The owner, 2026-11-16: "each language should have a list of starting words". Its own, in its own script; never English's. */
private fun everyInputMethodHasStarters(folder: File) {
    val english = setOf("the", "I", "and", "to", "a", "of")
    for (dir in folder.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }) {
        val method = runCatching { InputMethod.parse(File(dir, "im.json").readText()) }.getOrNull() ?: continue
        check("${method.id}: has starters", method.starters.size in 5..12) { "${method.starters.size}" }
        check("${method.id}: its starters are words", method.starters.all { word -> word.any { it.isLetter() } })
        if (method.script != "Latn") check("${method.id}: no English among its starters", method.starters.none { it in english }) {
            method.starters.joinToString(" ")
        }
        if (method.script == "Hani") same("${method.id}: Simplified starters too", method.startersSimplified.size, method.starters.size)
    }
}

/** Korean's choices from its word list: the word so far, the syllable still being built included (하 → 하고, 한국). */
private fun koreanChoices() {
    val file = File("android/assets/im/ko/words.combined")
    if (!file.isFile) return
    val words = WordList.read(file.useLines { it.toList() }.asSequence(), java.util.Locale.KOREAN)
    val korean = HangulComposer(words)
    korean.key('g')
    val initial = korean.key('k').choices
    check("하 offers 하고", "하고" in initial) { initial.joinToString(" ") }
    check("하 offers words that go on to 한", initial.any { it.startsWith("한") }) { initial.joinToString(" ") }
    korean.reset()
    same("ㅎ alone: ㅎ as typed, then words with ᄒ", HangulComposer(words).key('g').choices.let { it.first() == "ㅎ" && it.size > 1 } &&
        HangulComposer(words).key('g').choices.drop(1).all { it.startsWith("하") || it.first() in '하'..'힣' }, true)
    for (c in "gksr") korean.key(c)
    val after = korean.key('n')
    check("한 written, 구 being built: 한국", after.choices.any { it.startsWith("한국") }) { after.choices.joinToString(" ") }
    val pick = korean.pick(after.choices.indexOfFirst { it.startsWith("한국") })
    check("a pick writes the rest of the word, after 한", pick.commit.startsWith("국") && pick.commit.endsWith(" ")) { pick.commit }
    same("then nothing is being built", korean.code(), "")

    // The whole loop: 안녕하세요 typed jamo by jamo, the field getting each syllable as the next one starts.
    val loop = HangulComposer(words)
    val field = StringBuilder()
    fun type(keys: String) { for (c in keys) loop.key(c).commit.let(field::append) }
    type("dkssud")
    same("안 written, 녕 on the bar", field.toString() + "|" + loop.code(), "안|녕")
    same("Backspace takes 녕's last letter: 녀", loop.backspace().code, "녀")
    type("dgk")
    same("녕 again, then 하 being built", field.toString() + "|" + loop.code(), "안녕|하")
    val row = loop.key('t').choices
    check("안녕하 + ㅅ offers 안녕하세요", "안녕하세요" in row || loop.choices(0, 40).contains("안녕하세요")) {
        (row + loop.choices(0, 40)).joinToString(" ")
    }
    same("the row's first is the word as typed, what Space writes (#98)", row.firstOrNull(), "안녕핫")
    val menu = loop.choices(0, 40)
    check("the bigger menu has more than the row", menu.size >= row.size) { "${menu.size} ${row.size}" }
    val at = menu.indexOf("안녕하세요")
    if (at >= 0) {
        val picked = loop.pickAt(at).commit
        same("a pick from the menu writes the rest after 안녕", picked, "하세요 ")
    }
    // Picking the word as typed writes it as Space does: the syllable being built, then the space.
    val asTyped = HangulComposer(words)
    for (c in "gks") asTyped.key(c)
    same("a pick of the first, the word as typed: 한 and a space", asTyped.pickAt(0).commit, "한 ")
    // A held letter's symbol: its jamo comes off the syllable, the syllable is written, the symbol goes on as usual.
    val held = HangulComposer(words)
    held.key('g'); held.key('k'); held.key('s')
    same("한 being built", held.code(), "한")
    held.backspace()
    val ended = held.key('!')
    same("the hold's symbol: 하 written first, the symbol goes on", ended.commit + "|" + ended.handled, "하|false")
}

/**
 * The first key or touch in an input method other than English (owner, 2026-11-17): while it loads, at a field start or a
 * switch, keys and touches wait, also with the keyboard hidden, and go on once the load ends. Only the last load counts.
 */
private fun keysWaitForALoadingEngine() {
    val wait = EngineWait()
    same("nothing loading: keys go on", wait.waiting, false)
    wait.loading(1, waits = true)
    same("Yale loading at a field start: keys wait", wait.waiting, true)
    same("an older load's end lets nothing go", wait.loaded(0), false)
    same("still waiting", wait.waiting, true)
    same("its own load's end: the keys go on", wait.loaded(1), true)
    same("and nothing waits after", wait.waiting, false)
    wait.loading(2, waits = false)
    same("English: nothing waits", wait.waiting, false)
    wait.loading(3, waits = true)
    wait.loading(4, waits = true)
    same("a switch overtook the load: its end doesn't count", wait.loaded(3), false)
    same("the newer one's does", wait.loaded(4), true)
    wait.loading(5, waits = true)
    wait.loading(6, waits = false)
    same("a switch to English overtook it: nothing waits", wait.waiting, false)
    wait.loading(7, waits = true)
    wait.stop()
    same("the keyboard going: nothing waits", wait.waiting, false)

    // By input method: every one but English waits, with an engine or its own letters, so a first key is never English's.
    for (id in listOf("ru", "el", "yue-yale", "ko", "es")) {
        wait.loading(10, id)
        same("$id loading: the first key waits", wait.waiting, true)
        wait.loaded(10)
    }
    wait.loading(11, InputMethod.ENGLISH_ID)
    same("English loading: nothing waits", wait.waiting, false)
    // Russian's first key while it loads: it waits behind the stop, and then it's Russian's й, not q.
    val russian = InputMethod.parse(File("android/assets/im/ru/im.json").readText())
    same("and after the load, Russian's Q is й", russian.letter('Q', shift = false), "й")
    // Greek's first key the same: its A is α once loaded, where it would have been English's a.
    val greek = InputMethod.parse(File("android/assets/im/el/im.json").readText())
    same("and Greek's A is α", greek.letter('A', shift = false), "α")

    // The queue's stop, keys and touches alike, in the order typed.
    var made = 0
    val gate = LoadGate { "stop${++made}" }
    val queue = ArrayDeque<String>()
    same("nothing loading: no stop", gate.before(false, queue), false)
    queue += "a typed before the load"
    same("the first key while loading puts the stop in", gate.before(true, queue), true)
    queue += "key n"
    same("a touch after it doesn't put another", gate.before(true, queue), false)
    queue += "touch: a tap on g"
    same("the queue: what came first, the stop, then the key and the touch", queue.toList(),
        listOf("a typed before the load", "stop1", "key n", "touch: a tap on g"))
    same("still loading (a field start's new load): the stop stays shut", gate.open(true, queue), null)
    same("the load ends: the stop opens", gate.open(false, queue), "stop1")
    same("and only once", gate.open(false, queue), null)
    queue.clear()
    gate.before(true, queue)
    queue.clear()
    gate.clear()
    same("the queue emptied at a field start: a new touch puts a new stop in", gate.before(true, queue), true)
    same("a fresh stop", queue.toList(), listOf("stop3"))
    queue.clear()
    same("a stop the queue dropped never opens", gate.open(false, queue), null)
}

/** The bar's words in another input method, and what it learns from the user. */
/**
 * A damaged table fails as it loads, where InputMethodList and the autocorrect loader catch it, never later while typing (review,
 * 2026-11-17: an offset past the end loaded, and then threw as a word was looked up). Tables made by hand, as the tools lay them out.
 */
private fun damagedTablesFailAtLoad() {
    fun nextWords(wordStarts: IntArray, nexts: IntArray, cut: Int = 0): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val data = java.io.DataOutputStream(out)
        data.write("TKNW".toByteArray())
        for (n in intArrayOf(1, wordStarts.size - 1, 1, 3, nexts.size)) data.writeInt(n)
        for (n in wordStarts) data.writeInt(n)
        data.write("abc".toByteArray())
        for (n in intArrayOf(0, nexts.size)) data.writeInt(n)
        for (n in nexts) data.writeInt(n)
        return out.toByteArray().let { it.copyOf(it.size - cut) }
    }
    fun loads(bytes: ByteArray) = runCatching { NextWords.readBinary(bytes.inputStream()) }
    same("a sound table: after a, bc", loads(nextWords(intArrayOf(0, 1, 3), intArrayOf(1))).getOrNull()?.after("a"), listOf("bc"))
    same("a word's start past the end fails as it loads", loads(nextWords(intArrayOf(0, 1, 9), intArrayOf(1))).isFailure, true)
    same("a next word that isn't one fails as it loads", loads(nextWords(intArrayOf(0, 1, 3), intArrayOf(7))).isFailure, true)
    same("a cut-short file fails as it loads", loads(nextWords(intArrayOf(0, 1, 3), intArrayOf(1), cut = 2)).isFailure, true)

    fun pairs(key: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val data = java.io.DataOutputStream(out)
        data.write("TKPC".toByteArray())
        for (n in intArrayOf(1, 2, 3, 1)) data.writeInt(n)
        for (n in intArrayOf(0, 1, 3)) data.writeInt(n)
        data.write("abc".toByteArray())
        data.writeLong(key)
        data.writeInt(5)
        return out.toByteArray()
    }
    fun pairsLoad(bytes: ByteArray) = runCatching { com.titankeys.autocorrect.PairCounts.readBinary(bytes.inputStream()) }
    same("a sound pair table: a bc 5", pairsLoad(pairs((0L shl 20) or 1L)).getOrNull()?.count("a", "bc"), 5)
    same("a pair with a word that isn't one fails as it loads", pairsLoad(pairs((0L shl 20) or 9L)).isFailure, true)
}

private fun barWordsAndLearning() {
    val starters = listOf("de", "la", "en", "el", "y")
    same("a sentence starts: the starters, with a capital", BarWords.next(null, null, starters, capitals = true), listOf("De", "La", "En"))
    same("no capitals in a script without them", BarWords.next(null, null, listOf("我", "你", "佢"), capitals = false), listOf("我", "你", "佢"))
    same("after a word, nothing learned: the starters", BarWords.next("hola", null, starters, capitals = true), listOf("de", "la", "en"))
    same("while a word is typed: the list's", BarWords.complete("ho", null, listOf("hola", "hoy")), listOf("hola", "hoy"))
    same("nothing to offer: empty, never English", BarWords.next(null, null, emptyList(), capitals = true), emptyList<String>())

    // J's next-word tables: English's en_pairs.txt format.
    val table = NextWords.read(sequenceOf("# Leipzig, CC BY 4.0", "<s> hola	40", "<s> el	90", "buenos días	50",
        "buenos aires	70", "bad line", "buenos noches	0"))
    same("a table's sentence starts, commonest first", table.after(null), listOf("el", "hola"))
    same("its next words, commonest first, a count of 0 skipped", table.after("Buenos"), listOf("aires", "días"))
    same("the bar takes them before the starters", BarWords.next("buenos", null, starters, capitals = true, table = table),
        listOf("aires", "días", "de"))
    same("at a sentence's start, the starters first, then the table's, with capitals",
        BarWords.next(null, null, listOf("de"), capitals = true, table = table), listOf("De", "El", "Hola"))

    // The tables keep every word in lower case (tools/im/next_words.py): the bar gives each the word list's own spelling, so
    // German's nouns and the names keep their capitals (review, 2026-11-17: "das " offered "unternehmen", "land", "team").
    val german = WordList.read(sequenceOf(" word=Unternehmen,f=200", " word=unternehmen,f=120", " word=Land,f=180",
        " word=weg,f=210", " word=Weg,f=150", " word=ist,f=255"), java.util.Locale.GERMAN)
    same("a noun gets its capital", german.spelling("land"), "Land")
    same("of two spellings, the commonest: the noun", german.spelling("unternehmen"), "Unternehmen")
    same("and the adverb where it's commoner", german.spelling("weg"), "weg")
    same("a word in lower case stays", german.spelling("ist"), "ist")
    same("a word the list doesn't hold stays", german.spelling("xyz"), "xyz")
    val germanTable = NextWords.read(sequenceOf("das ist\t90", "das unternehmen\t80", "das land\t70"))
    same("the bar after das, with capitals", BarWords.next("das", null, emptyList(), capitals = true, table = germanTable,
        spelling = german::spelling), listOf("ist", "Unternehmen", "Land"))
    // Turkish: Python lowered İ to i with a combining dot, which showed as a doubly dotted i; the list's spelling mends it.
    val turkish = WordList.read(sequenceOf(" word=İstanbul,f=200", " word=İbrahim,f=150"), java.util.Locale.forLanguageTag("tr"))
    same("Turkish: the table's i̇stanbul is İstanbul", turkish.spelling("i\u0307stanbul"), "İstanbul")
    same("and i̇brahim is İbrahim", turkish.spelling("i\u0307brahim"), "İbrahim")

    val learning = LanguageLearning()
    learning.kept(null, "hola", known = true)
    same("a known word kept once starts sentences", BarWords.next(null, learning, starters, capitals = true).first(), "Hola")
    learning.kept("buenos", "días", known = true)
    learning.kept("buenos", "días", known = true)
    same("a learned next word rises", BarWords.next("buenos", learning, starters, capitals = true), listOf("días", "de", "la"))
    learning.kept("es", "Titankeys", known = false)
    same("a word the list lacks, typed once, isn't offered (a typo)", BarWords.complete("Tit", learning, emptyList()), emptyList<String>())
    learning.kept("es", "Titankeys", known = false)
    same("typed twice, it rises above the list's", BarWords.complete("tit", learning, listOf("título")), listOf("Titankeys", "título"))
    learning.forget("Titankeys")
    check("taken back, it's under twice again", !learning.knows("Titankeys"))
    // Blocked words and Favourites, shared by every language, and the word menu's Save and Unsave in this one.
    val rules = WordRules({ it == "la" }, { it == "en" })
    same("a blocked word never shows; a favourite comes first", BarWords.next("x", null, starters, capitals = false, rules = rules),
        listOf("en", "de", "el"))
    same("in completions too", BarWords.complete("e", null, listOf("el", "en", "es"), rules), listOf("en", "el", "es"))
    learning.save("Zaragoza")
    check("a saved word is known at once", learning.knows("Zaragoza"))
    same("and offered", BarWords.complete("zar", learning, emptyList()), listOf("Zaragoza"))
    learning.unsave("de", listed = true)
    check("an unsaved list word is hidden", learning.isHidden("de"))
    check("and never offered", "de" !in BarWords.next("x", learning, starters, capitals = false))
    learning.save("de")
    check("saved again, it's back", !learning.isHidden("de"))
    val copy = LanguageLearning.decode(learning.encode())
    check("saved words kept as text", copy.isSaved("Zaragoza"))
    same("kept as text and read back", copy.next("buenos"), listOf("días"))
    same("its sentence starts too", copy.sentenceStarts(), listOf("hola"))
    same("a damaged line is skipped", LanguageLearning.decode("w\thola\tx\nw\tadiós\t3").completions("adi"), listOf("adiós"))
    val many = LanguageLearning()
    for (i in 0 until LanguageLearning.MAX_WORDS + 50) many.kept(null, "w$i", known = true)
    check("its words are bounded", many.encode().lines().count { it.startsWith("w\t") } <= LanguageLearning.MAX_WORDS)
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
        // The picker shows at most eight accents, W to O: a letter with more would lose some (owner, 2026-11-17).
        val crowded = method.accents.filterValues { it.size > Picker.ACCENTS }
        check("${method.id}: no letter has more than ${Picker.ACCENTS} accents", crowded.isEmpty()) { crowded.keys.joinToString(" ") }
        typingRowsFit(method)
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
        // Korean's syllables are built by its engine from the jamo on the keys: koreanChoices checks its list.
        if (method.engine == "hangul") continue
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

/**
 * What a double Space types is each language's own full stop (owner, 2026-11-16): 。 with no space in Chinese and Japanese,
 * "। " in Hindi, none in Thai, and ". " everywhere else, English included.
 */
private fun eachLanguageHasItsFullStop(folder: File) {
    val own = mapOf("zh-pinyin" to "。", "yue-yale" to "。", "yue-jyutping" to "。", "zh-cangjie" to "。",
        "zh-quick" to "。", "zh-zhuyin" to "。", "ja" to "。", "hi" to "। ", "th" to "")
    for (dir in folder.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }) {
        val method = runCatching { InputMethod.parse(File(dir, "im.json").readText()) }.getOrNull() ?: continue
        val expected = own[method.id] ?: InputMethod.FULL_STOP
        check("${method.id}'s full stop", method.rules.fullStop == expected) { "'${method.rules.fullStop}', expected '$expected'" }
    }
    check("English's full stop", InputMethod.ENGLISH.rules.fullStop == ". ")
}

/**
 * The typing settings a language's page shows once a second language is on (owner, 2026-11-17): only the ones that work in it,
 * from its own rules (LanguageSettings).
 */
private fun typingRowsFit(method: InputMethod) {
    val rows = LanguageSettings.rows(method)
    val expected = when (method.id) {
        "fr" -> listOf("fixes", "capitals", "doubleSpace", "sentenceSpaces", "commaSpace")
        // A held letter's symbol goes through the engine as its own mark, so the hold row stays.
        "zh-pinyin", "yue-yale", "yue-jyutping", "ja" -> listOf("doubleSpace", "hold")
        "th" -> listOf("fixes", "commaSpace")
        "ka" -> listOf("fixes", "doubleSpace", "sentenceSpaces", "commaSpace", "hold")
        "id" -> listOf("fixes", "capitals", "doubleSpace", "sentenceSpaces", "commaSpace", "hold")
        else -> null
    }
    if (expected != null) same("${method.id}: its page's typing settings", rows, expected)
    check("${method.id}: only English has swiping and shorthand", listOf("swipe", "shorthand").none { it in rows })
    // Fix typos shows where the language corrects (LanguageCorrections.corrects: a list and no engine), and nowhere else.
    same("${method.id}: Fix typos where it corrects", "fixes" in rows, method.words != null && method.engine == null)
    // Hindi's and Vietnamese's engines build the words: their slipped-key choice is only ever a suggestion on the bar, never put in
    // by Space, so it isn't under Fix typos, and their pages don't show it (D, 2026-11-17).
    if (method.id == "hi" || method.id == "vi") check("${method.id}: no Fix typos row", "fixes" !in rows)
    check("${method.id}: an accent language has no hold row", !method.hasAccents || "hold" !in rows)
    check("${method.id}: no capitals row without capitals", method.rules.capitalise || "capitals" !in rows)
    // The quick settings panel hides the same ones while this input method types (QuickSetting.hiddenIn).
    val hidden = QuickSetting.hiddenIn(rows)
    for ((item, row) in listOf(QuickSetting.Autocorrect to "fixes", QuickSetting.SwipeTyping to "swipe",
            QuickSetting.AutoCapitalise to "capitals", QuickSetting.AutoPunctuation to "doubleSpace", QuickSetting.HoldSymbols to "hold"))
        same("${method.id}: the quick ${item.label} key is hidden where its row is", item in hidden, row !in rows)
    if (method.id == "zh-pinyin") same("Chinese: the panel leaves out Fix, Swipe and Caps", hidden,
        setOf(QuickSetting.Autocorrect, QuickSetting.SwipeTyping, QuickSetting.AutoCapitalise))
}

private fun englishTypingRows() {
    same("English: every typing setting on its page", LanguageSettings.rows(InputMethod.ENGLISH),
        listOf("fixes", "capitals", "doubleSpace", "sentenceSpaces", "commaSpace", "hold", "swipe", "shorthand"))
    same("English's Double Space types a full stop", LanguageSettings.stop(InputMethod.ENGLISH), ".")
    same("English: the quick settings panel hides nothing", QuickSetting.hiddenIn(LanguageSettings.rows(InputMethod.ENGLISH)),
        emptySet<QuickSetting>())
}

/**
 * Thai, Hebrew and Hindi on the bar (the Orchestrator, 2026-11-17). Thai has no spaces between words, so its next words and
 * learning read a phrase's words with the keyboard's own split, the one tools/im/next_words.py counted the table with.
 * Hebrew and Hindi look up their next words by the word before, Hindi's vowel signs and all.
 */
private fun thaiHebrewAndHindiNextWords(folder: File) {
    fun list(id: String) = WordList.read(File(folder, "$id/words.combined").useLines { it.toList() }.asSequence(),
        java.util.Locale.forLanguageTag(id))
    fun table(id: String) = File(folder, "$id/pairs.bin").inputStream().use { NextWords.readBinary(it) }
    val thai = list("th")
    val isWord = { piece: String -> WordBreak.word(piece, thai::has) }
    same("Thai: ก is in the list, but one letter is never a word", isWord("ก"), false)
    same("a phrase's words: ฉัน ไป โรงเรียน", WordBreak.words("ฉันไปโรงเรียน", isWord), listOf("ฉัน", "ไป", "โรงเรียน"))
    same("the word a next word follows is the phrase's last", WordBreak.lastWord("ฉันไปโรงเรียน", isWord), "โรงเรียน")
    val th = table("th")
    check("and the table has words after it", th.after("โรงเรียน").isNotEmpty()) { th.after("โรงเรียน").joinToString(" ") }
    check("no single letter starts a Thai sentence in the table", th.after(null).none { it.length == 1 }) {
        th.after(null).joinToString(" ")
    }
    // Learning a kept phrase: each word after the one before it, never the phrase as one word.
    val learning = LanguageLearning()
    var before: String? = null
    var paired = true
    for (piece in WordBreak.words("ฉันไปโรงเรียน", isWord)) {
        if (piece != null) learning.kept(before, piece, known = true, paired = paired)
        before = piece
        paired = piece != null
    }
    same("Thai learns ไป after ฉัน", learning.next("ฉัน"), listOf("ไป"))
    same("and โรงเรียน after ไป", learning.next("ไป"), listOf("โรงเรียน"))
    same("the phrase started with ฉัน", learning.sentenceStarts(), listOf("ฉัน"))
    same("the phrase isn't a word", learning.knows("ฉันไปโรงเรียน"), false)
    val alone = LanguageLearning()
    alone.kept(null, "ไป", known = true, paired = false)
    same("a word after a stray letter is known, but starts no sentence", alone.knows("ไป") to alone.sentenceStarts(),
        true to emptyList<String>())

    // Hebrew: next words by the word before, in its own letters; the bar's best word in the middle, the second on the right.
    check("Hebrew: words follow את", table("he").after("את").isNotEmpty())
    same("Hebrew's bar: third, best, second from left to right", BarOrder.of(true), listOf(2, 0, 1))
    same("a drag to the right goes back in Hebrew text", RightToLeft.forward(1, rightToLeft = true), false)
    // Hindi: the word before keeps its vowel signs and nasal mark (में), as the table does.
    val hi = table("hi")
    check("Hindi: words follow कि", hi.after("कि").isNotEmpty())
    check("and में, with its vowel sign and bindu", hi.after("में").isNotEmpty()) { hi.after("में").joinToString(" ") }
}

/**
 * The Cyrillic layouts' held letters (the Orchestrator, 2026-11-17): every letter of the alphabet can be typed, from a key or
 * a hold, none twice, and each key's held letters come commonest first, by the input method's own word list.
 */
private fun cyrillicHoldsReachEveryLetter(folder: File) {
    val alphabets = mapOf(
        "ru" to "абвгдеёжзийклмнопрстуфхцчшщъыьэюя", "uk" to "абвгґдеєжзиіїйклмнопрстуфхцчшщьюя",
        "be" to "абвгдеёжзійклмнопрстуўфхцчшыьэюя", "bg" to "абвгдежзийклмнопрстуфхцчшщъьюя",
        "mk" to "абвгдѓежзѕијклљмнњопрстќуфхцчџш", "sr" to "абвгдђежзијклљмнњопрстћуфхцчџш",
        "kk" to "аәбвгғдеёжзийкқлмнңоөпрстуұүфхһцчшщъыіьэюя")
    for ((id, alphabet) in alphabets) {
        val method = InputMethod.parse(File(folder, "$id/im.json").readText())
        val typed = ('a'..'z').mapNotNull { method.letter(it, shift = false) } + method.accents.values.flatten()
        val missing = alphabet.filter { letter -> typed.none { it == letter.toString() } }
        check("$id: every letter can be typed", missing.isEmpty()) { "missing $missing" }
        val twice = typed.groupBy { it }.filter { it.value.size > 1 }.keys
        check("$id: no letter in two places", twice.isEmpty()) { "twice $twice" }
        // Each letter's weight in the list: its words' frequencies, a step of 25 in f being a factor of e, as make.py ranks.
        val weight = HashMap<Char, Double>()
        File(folder, "$id/words.combined").useLines { lines ->
            lines.filter { it.startsWith(" word=") }.take(50_000).forEach { line ->
                val fields = line.trim().split(",")
                val f = fields.getOrNull(1)?.removePrefix("f=")?.toIntOrNull() ?: 0
                for (c in fields[0].removePrefix("word=").lowercase()) weight.merge(c, Math.exp(f / 25.0), Double::plus)
            }
        }
        for ((key, held) in method.accents) {
            // The letters commonest first; a mark held with them (Ukrainian's and Belarusian's apostrophe) after them.
            val order = held.sortedWith(compareBy<String>({ !it[0].isLetter() }, { -(weight[it[0]] ?: 0.0) }))
            check("$id: ${key.uppercaseChar()}'s held letters commonest first", held == order) { "$held, by use $order" }
        }
        // The apostrophe is part of Ukrainian's and Belarusian's words (сім'я, з'ява), next to х on their PC layouts: held on P.
        if (id == "uk" || id == "be") same("$id: P holds the apostrophe, after its letters", method.accents['p']?.last(), "'")
    }
}

/** The hold hint each input method says once (HoldHint): accents, Cyrillic's missing letters, or nothing. */
private fun holdHints(folder: File) {
    fun hint(id: String) = HoldHint.of(InputMethod.parse(File(folder, "$id/im.json").readText()))
    for (id in listOf("be", "bg", "kk", "mk", "ru", "sr", "uk")) same("$id: a hold finds a missing letter", hint(id), HoldHint.MISSING)
    for (id in listOf("es", "fr", "el", "sr-latn", "tr")) same("$id: a hold gives accents", hint(id), HoldHint.ACCENTS)
    for (id in listOf("th", "he", "id", "ka")) same("$id: no hint, nothing held on the bar", hint(id), null)
    same("English: no hint", HoldHint.of(InputMethod.ENGLISH), null)
    // It shows as a field starts too, not only as the input method changes in one (review, 2026-11-17: one picked in Settings, or
    // loaded with the keyboard away, never changed in a field, so its hint never showed). Once ever, as TypingSettings keeps.
    val ime = File("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt").readText()
    val from = ime.indexOf("override fun onStartInputView(")
    val start = ime.substring(from, ime.indexOf("backDisposition = BACK_DISPOSITION_WILL_NOT_DISMISS", from))
    same("the hint is offered as a field starts", "main.post { if (editing) showHoldHint() }" in start, true)
    same("and still once ever", "if (TypingSettings.holdHintShown(this, method.id)) return" in ime, true)
}

/**
 * Spelling variants (Portuguese's Brasil and Portugal, E's find 2026-11-17): the index lists them with their names, Brasil
 * first; the setting picks one per input method, the first by default, and a variant an input method doesn't have is its first.
 */
private fun variantsArePicked(folder: File) {
    val index = InputMethodIndex.parse(File(folder, "index.json").readText())
    val pt = index.entries.first { it.id == "pt" }
    same("Portuguese's variants, Brasil first", pt.variants, listOf(VariantName("pt-BR", "Brasil"), VariantName("pt-PT", "Portugal")))
    same("an input method without variants lists none", index.entries.first { it.id == "es" }.variants, emptyList<VariantName>())
    val ids = pt.variants.map { it.id }
    same("nothing picked: Brasil", InputMethodVariants.chosen("", "pt", ids), "pt-BR")
    val portugal = InputMethodVariants.with("", "pt", "pt-PT")
    same("Portugal picked", InputMethodVariants.chosen(portugal, "pt", ids), "pt-PT")
    same("another's pick leaves it alone", InputMethodVariants.chosen(InputMethodVariants.with(portugal, "xx", "x-1"), "pt", ids), "pt-PT")
    same("a variant it doesn't have: its first", InputMethodVariants.chosen("pt=pt-AO", "pt", ids), "pt-BR")
    same("none for an input method without variants", InputMethodVariants.chosen(portugal, "es", emptyList()), null)
    same("picking again replaces", InputMethodVariants.read(InputMethodVariants.with(portugal, "pt", "pt-BR")), mapOf("pt" to "pt-BR"))
    // Each variant has its own list and next words in the folder, as the keyboard loads them.
    for (variant in ids) check("$variant's list and table are there",
        File(folder, "pt/words-$variant.combined").isFile && File(folder, "pt/pairs-$variant.bin").isFile)
}

/** TitanKeys Debug's bar log (BarLog), in the format tools/dev/touch/interactions_batch.py reads: the middle is the first word. */
private fun barLogLines() {
    same("three words: the second left, the first in the middle", BarLog.line("en", BarLog.places(listOf("did", "do", "different"))),
        "bar: en do | did | different")
    same("an empty place is -", BarLog.line("yue-yale", BarLog.places(listOf("你"))), "bar: yue-yale - | 你 | -")
    same("an empty bar", BarLog.line("ko", BarLog.places(emptyList())), "bar: ko - | - | -")
    same("a notice", BarLog.line("yue-yale", "notice: Warming up Yale…"), "bar notice: Warming up Yale…")
}

/**
 * Sym with a code being built clears it and does nothing more: its repeats and release are swallowed. The next Sym is Sym as
 * always (owner, 2026-11-17: "pressing the sym button should remove the keycode stuff first").
 */
private fun symClearsACodeFirst() {
    val sym = SymPress()
    sym.cleared()
    for (repeat in 1..5) same("the clearing press's repeat $repeat", sym.press(repeat, false) { true }, SymPress.Press.Swallow)
    same("its release does nothing more", sym.release(), SymPress.Release.Kept)
    same("the next Sym waits as usual", sym.press(0, false) { true }, SymPress.Press.Wait)
    same("and let go soon it opens the panel", sym.release(), SymPress.Release.Usual)
    same("with one input method on, the next Sym is Sym", sym.press(0, false) { false }, SymPress.Press.Usual)
}

/** While an input method loads, the bar shows the waiting keys' letters, plain, never a line about warming up. */
private fun heldKeysShowAsPlainLetters() {
    fun push(code: Int) = listOf(Triple(true, 0, code), Triple(false, 0, code))
    val n = 29 + 13; val e = 29 + 4; val i = 29 + 8
    same("n, e, i typed while it loads", HeldLetters.of(push(n) + push(e) + push(i)), "nei")
    same("a held letter's repeats add nothing", HeldLetters.of(push(n) + listOf(Triple(true, 3, n))), "n")
    same("Backspace takes the last off", HeldLetters.of(push(n) + push(e) + push(67)), "n")
    same("nothing typed, nothing shown", HeldLetters.of(emptyList()), "")
    same("Space and the rest show nothing", HeldLetters.of(push(62)), "")
}

/** The owner's rule: an input method that's off loads nothing. With English and Yale on, only Rime stays warm. */
private fun onlyTheOnesOnStayWarm(folder: File) {
    val index = InputMethodIndex.parse(File(folder, "index.json").readText())
    same("English and Yale on: Rime alone is warm", WarmEngines.wanted(index.on("en,yue-yale")).keys, setOf("rime"))
    same("English alone: no engine", WarmEngines.wanted(index.on("en")).keys, emptySet<String>())
    same("Japanese and Korean on: Mozc and Hangul", WarmEngines.wanted(index.on("en,ja,ko")).keys, setOf("mozc", "hangul"))
}

/**
 * The language pages' guide data (make.py write_guides, agreed with E): every input method's guide is complete, and each
 * language's settings in languages.json say available exactly where LanguageSettings.rows has the row.
 */
private fun everyGuideIsComplete(folder: File) {
    val roles = setOf("shift", "space", "enter", "backspace", "sym")
    val index = Json.parse(File(folder, "index.json").readText()) as List<*>
    val indexed = index.associate { entry -> (entry as Map<*, *>)["id"] as String to entry["guide"] }
    val byLanguage = HashMap<String, MutableList<InputMethod>>()
    for (dir in folder.listFiles { file -> File(file, "im.json").isFile }.orEmpty().sortedBy { it.name }) {
        val root = Json.parse(File(dir, "im.json").readText()) as Map<*, *>
        val guide = root["guide"] as? Map<*, *>
        check("${dir.name}: has a guide", guide != null)
        guide ?: continue
        check("${dir.name}: its status", guide["status"] in setOf("ready", "experimental"))
        check("${dir.name}: its family", (guide["family"] as? String).orEmpty().isNotEmpty())
        for (field in listOf("line", "practice")) check("${dir.name}: its $field", guide[field] is String)
        check("${dir.name}: its points", guide["points"] is List<*>)
        val keys = guide["keys"] as? Map<*, *>
        val listed = (keys?.get("roles") as? List<*>).orEmpty().map { (it as Map<*, *>)["key"] }.toSet()
        check("${dir.name}: a role for each special key", listed == roles) { "$listed" }
        val method = InputMethod.parse(File(dir, "im.json").readText())
        // Keys that type their own script draw what each types: all 26 (not the ones typing by sound in Latin letters: Hindi,
        // Japanese, Yale, Jyutping, Pinyin).
        val ownKeys = method.keys != null || dir.name in setOf("ko", "zh-cangjie", "zh-quick", "zh-zhuyin")
        if (ownKeys) check("${dir.name}: what all 26 keys type", (keys?.get("map") as? Map<*, *>)?.size == 26) {
            "${(keys?.get("map") as? Map<*, *>)?.size}"
        }
        check("${dir.name}: the index carries it", indexed[dir.name] == guide)
        val language = (index.first { (it as Map<*, *>)["id"] == dir.name } as Map<*, *>)["language"] as? Map<*, *>
        byLanguage.getOrPut(language?.get("id") as? String ?: dir.name) { mutableListOf() } += method
    }
    val settings = Json.parse(File(folder, "languages.json").readText()) as Map<*, *>
    for ((language, methods) in byLanguage) {
        val rows = (settings[language] as Map<*, *>)["settings"] as List<*>
        val available = rows.map { it as Map<*, *> }.filter { it["available"] == true }.map { it["id"] }.toSet()
        val shown = methods.flatMap { LanguageSettings.rows(it) }.toSet()
        // The page rows LanguageSettings decides: they agree. Spelling, characters and variants are their own pages.
        val decided = setOf("fixes", "capitals", "doubleSpace", "sentenceSpaces", "commaSpace", "hold", "swipe", "shorthand")
        same("$language: its settings as LanguageSettings shows them", available.filter { it in decided }.toSet(), shown)
        check("$language: every row not available says why", rows.map { it as Map<*, *> }.all { it["available"] == true || it["reason"] is String })
    }
    val zh = (settings["zh"] as Map<*, *>)["groups"] as List<*>
    same("Chinese's groups", zh.map { (it as Map<*, *>)["title"] }, listOf("Cantonese", "Mandarin", "By shape"))
}

/**
 * The native review (2026-11-17): the bar never offers a blocked word (profanity, slurs, sexual violence, self-harm;
 * tools/im/blocklist.py), as a starter, a sentence start or a next word, in any input method with a blocked.txt; nor
 * Korean's bound endings or Thai's fragments and rare names. Words the user typed and learned may come back.
 */
private fun noBlockedWordIsOffered(folder: File) {
    var checked = 0
    for (dir in folder.listFiles { file -> File(file, "blocked.txt").isFile }.orEmpty().sortedBy { it.name }) {
        val method = InputMethod.parse(File(dir, "im.json").readText())
        val blocked = File(dir, "blocked.txt").useLines { BlockedWords.read(it, method.script) }
        val rules = WordRules({ blocked.blocks(it) || WordNoise.fragment(method.id, it) }, { false })
        same("${method.id}: no starter is blocked", method.starters.filter(blocked::blocks), emptyList<String>())
        val table = File(dir, "pairs.bin").takeIf { it.isFile }?.inputStream()?.use { NextWords.readBinary(it, method.id in NextWords.TURKIC) }
            ?: continue
        val list = method.words?.let { name -> File(dir, name).takeIf { it.isFile }?.useLines { WordList.read(it, method.javaLocale) } }
        val nextWord = { word: String -> WordNoise.nextWord(method.id, word) { list?.frequency(it) ?: Int.MAX_VALUE } }
        val previous = listOf<String?>(null) + method.starters + table.after(null) +
            (list?.let { l -> File(dir, method.words!!).useLines { lines -> lines.filter { it.startsWith(" word=") }.take(300)
                .map { it.trim().removePrefix("word=").substringBefore(',') }.toList() } }.orEmpty())
        for (word in previous.distinct()) {
            val offered = BarWords.next(word, null, method.starters, capitals = false, table = table, rules = rules, shown = 8,
                nextWord = nextWord)
            val bad = offered.filter { blocked.blocks(it) || WordNoise.fragment(method.id, it) }
            if (bad.isNotEmpty()) same("${method.id}: after ${word ?: "a sentence's start"}, nothing blocked", bad, emptyList<String>())
            checked++
        }
    }
    check("the next words of every input method with a blocklist were checked", checked > 1000) { "$checked" }
    // The review's own: Thai's swears are blocked, Korean's endings and Thai's rare names after คุณ aren't offered.
    val th = File(folder, "th/blocked.txt").useLines { BlockedWords.read(it, "Thai") }
    for (word in listOf("ควย", "เหี้ย", "เย็ด", "หี", "สัส", "แม่ง")) check("Thai blocks $word", th.blocks(word))
    check("but not หีบ, which holds หี", !th.blocks("หีบ"))
    val zh = File(folder, "yue-yale/blocked.txt").useLines { BlockedWords.read(it, "Hani") }
    check("Cantonese blocks 仆街 and 自殺 inside a phrase", zh.blocks("仆街") && zh.blocks("想自殺"))
    check("but not 奶茶 or 一致性, which hold one-character entries", !zh.blocks("奶茶") && !zh.blocks("一致性"))
    check("Korean's 고 is a fragment", WordNoise.fragment("ko", "고"))
}
