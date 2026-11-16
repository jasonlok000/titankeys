package com.atlas.keyboard

import com.atlas.connected.Json
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
    check("a swipe up goes forward", com.atlas.engine.EmojiPages.forward(-1f))
    check("a swipe down goes back", !com.atlas.engine.EmojiPages.forward(1f))
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

/** Every `im.json` in [folder]: it reads, its label is unique, its word list reads, and its accents' letters are in it or held. */
private fun everyInputMethodInTheFolder(folder: File) {
    val methods = folder.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }
    val labels = HashMap<String, String>()
    for (dir in methods) {
        val file = File(dir, "im.json")
        val parsed = runCatching { InputMethod.parse(file.readText()) }
        val method = parsed.getOrNull()
        if (method == null) {
            check("${dir.name} reads", false) { parsed.exceptionOrNull()?.message.orEmpty() }
            continue
        }
        same("${dir.name}: the folder is its id", method.id, dir.name)
        labels.put(method.label, method.id)?.let { check("${method.id}: label ${method.label} is also $it's", false) }
        check("${method.id}: has a NOTICE", File(dir, "NOTICE").isFile)
        if (method.keys != null) same("${method.id}: every key has a letter", method.keys.keys, ('a'..'z').toSet())
        val words = method.words ?: continue
        val list = File(dir, words)
        check("${method.id}: its word list is there", list.isFile)
        if (!list.isFile) continue
        val read = WordList.read(list.useLines { it.toList() }.asSequence(), method.javaLocale)
        check("${method.id}: its word list has words", read.size > 1000) { "${read.size}" }
        check("${method.id}: at most 100,000 words", read.size <= 100_000) { "${read.size}" }
        check("${method.id}: at most 4 MB", list.length() <= 4_000_000) { "${list.length()}" }
        // Every letter the list uses often can be typed: from a key, or held on one.
        val typable = HashSet<String>()
        for (key in 'a'..'z') {
            typable += method.letter(key, false) ?: key.toString()
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
