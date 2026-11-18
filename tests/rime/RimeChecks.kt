package com.titankeys.rime

import com.titankeys.keyboard.ChineseKeys
import com.titankeys.keyboard.ChineseSetup
import com.titankeys.keyboard.EngineState
import com.titankeys.keyboard.InputMethod
import java.io.File
import java.security.MessageDigest

fun main(args: Array<String>) {
    eachVariantSetsExactlyOneOption()
    aMissingSettingIsNoVariant()
    theVariantIsSetAgainAfterASchemaSwitch()
    lettersGoToRime()
    spaceTypesTheFirstChoice()
    withNoCodeTheKeyboardKeepsItsKeys()
    enterTypesTheCodeAsLetters()
    backspaceDeletesFromTheCode()
    aCapitalTypesTheCodeFirst()
    punctuationGoesThroughRime()
    punctuationWithACodeTypesTheFirstChoiceFirst()
    quotesFollowTheVariant()
    plainPunctuationIsTheKeyboards()
    digitsStayPlain()
    choicesReadAcrossPages()
    pickingAndPaging()
    nothingBeforeStart()
    onlyARimeInputMethodStarts()
    theDataInstallsBeforeRimeOpens()
    withNoVariantNothingChineseIsTyped()
    withNoVariantTheChinesePageOpensOnce()
    switchingInputMethodsKeepsRimeOpen()
    anInputMethodTurnedOnWhileRimeIsOpenStarts()
    theWarmEngineHasYaleReady()
    theChineseInputMethodsAreRimes(args.firstOrNull())
    theBackupTakesRimesSnapshots()
    aRestoredBackupMergesInAtTheNextStart()
    aSyncWhileTypingSelectsTheSchemaAgain()
    theDataCopiesOnceAndChecksEachFile()
    aBadCopyKeepsTheOldData()
    onlyTheInputMethodsOnAreCopied()
    anOlderInstallCarriesOn()
    aManifestCannotLeaveItsFolder()
    nextWordSuggestionsFollowACommit()
    spacePicksTheFirstSuggestion()
    otherKeysClearTheSuggestions()
    aLetterStartsANewCode()
    aNewFieldStartsWithNoSuggestions()
    noSuggestionsBeforeAVariant()
    theBarsWordsShowAsChoices()
    aPickedBarWordBringsItsNextWords()
    theBarsWordsNeverCoverACode()
    aFlickKeepsWhatWasPicked()
    println("Rime checks passed")
}

private fun check(ok: Boolean, what: String) {
    if (!ok) throw AssertionError(what)
}

private fun <T> same(expected: T, actual: T, what: String) {
    if (expected != actual) throw AssertionError("$what\n  expected: $expected\n  actual:   $actual")
}

/**
 * A stand-in for Rime: letters build a code, Space or a pick commits a choice made from the code, Return commits the
 * code itself, Backspace deletes a letter, and Rime's punctuator turns ',' into '，'. It keeps every call.
 */
private class FakeRime : RimeCalls {
    val calls = mutableListOf<String>()
    val options = mutableMapOf<String, Boolean>()
    var code = ""
    var page = 0
    private var commit: String? = null
    var opened = true
    /** The schemas whose files are installed now, and the ones Rime had when it opened, which it can select: null is all. */
    var installed: Set<String>? = null
    private var deployed: Set<String>? = null
    /**
     * librime-predict's suggestions: after a commit from a code or a suggestion, the next words, "<word>+0" and on; none at the
     * start. Escape and Backspace clear them, as its predictor does, and clearing the composition alone doesn't: the predictor
     * offers them again.
     */
    var suggestions = emptyList<String>()

    private fun committed(text: String) {
        commit = text
        suggestions = (0 until 3).map { "$text+$it" }
    }

    fun choices() = if (code.isEmpty()) suggestions else (0 until 8).map { "${code.uppercase()}${page * 8 + it}" }

    override fun open(shared: String, user: String): Boolean {
        calls += "open"
        deployed = installed
        return opened
    }

    override fun close() {
        calls += "close"
    }

    override fun schema(id: String): Boolean {
        calls += "schema $id"
        // Selecting a schema resets its switches: the variant switch's reset is zh_hk.
        options.clear()
        options["zh_hk"] = true
        return id.startsWith("tk_") && deployed?.contains(id) != false
    }

    override fun option(name: String, on: Boolean) {
        calls += "option $name $on"
        options[name] = on
    }

    override fun option(name: String) = options[name] == true

    override fun process(keycode: Int, mask: Int): Boolean {
        calls += "key $keycode"
        return when (keycode) {
            in 'a'.code..'z'.code -> { code += keycode.toChar(); page = 0; suggestions = emptyList(); true }
            RimeKeys.SPACE -> if (code.isEmpty()) false else { committed(choices()[0]); code = ""; true }
            RimeKeys.RETURN -> if (code.isEmpty()) false else { commit = code; code = ""; true }
            RimeKeys.ESCAPE -> { val had = suggestions.isNotEmpty(); suggestions = emptyList(); had }
            RimeKeys.BACKSPACE -> if (code.isEmpty()) { val had = suggestions.isNotEmpty(); suggestions = emptyList(); had }
                else { code = code.dropLast(1); true }
            RimeKeys.PAGE_DOWN -> { page++; true }
            RimeKeys.PAGE_UP -> { if (page > 0) page--; true }
            ','.code, '"'.code -> if (options["ascii_punct"] == true) false
                else { commit = (commit ?: "") + if (keycode == ','.code) "，" else "“"; true }
            else -> false
        }
    }

    /** Every choice, across the three pages. */
    private fun all() = if (code.isEmpty()) suggestions else (0 until 24).map { "${code.uppercase()}$it" }

    override fun selectAt(index: Int): Boolean {
        val c = all()
        if (index !in c.indices) return false
        committed(c[index])
        code = ""
        page = 0
        return true
    }

    override fun choices(start: Int, count: Int): List<String> = all().drop(start).take(count)

    override fun select(index: Int): Boolean {
        val c = choices()
        if (index !in c.indices) return false
        committed(c[index])
        code = ""
        page = 0
        return true
    }

    override fun reset() {
        calls += "reset"
        code = ""
        pickedCode = ""
        pickedText = ""
    }

    /** What was picked of the code so far: its letters, and the text Rime shows for them. */
    var pickedCode = ""
    var pickedText = ""

    override fun picked(): Pair<String, Int> = pickedText to pickedCode.length

    /** As tk_predict.h's DropLast: the code pending after a pick goes; false with nothing picked or nothing pending. */
    override fun dropLast(): Boolean {
        calls += "dropLast"
        if (pickedCode.isEmpty() || code.isEmpty()) return false
        code = ""
        return true
    }

    /** As tk_predict.h's PredictAfter: the word counts as a commit, so its next words come, though nothing is typed. */
    override fun predictAfter(text: String): Boolean {
        calls += "after $text"
        if (code.isNotEmpty()) return false
        suggestions = (0 until 3).map { "$text+$it" }
        return true
    }

    /** As Rime's sync: it closes the session, so the schema and options are gone until selected again. */
    override fun sync(): Boolean {
        calls += "sync"
        code = ""
        options.clear()
        return true
    }

    override fun read(): RimeCalls.Raw {
        val preedit = if (code.isEmpty()) pickedText.ifEmpty { null } else pickedText + code.replace("ou", "o-u")
        val out = RimeCalls.Raw(commit, pickedCode + code, preedit, page, page >= 2, 0, choices(), choices().map { "" }, 8, if (code.isEmpty()) 0 else 24)
        commit = null
        return out
    }
}

private fun method(schema: String, engine: String = "rime") = InputMethod.parse(
    """{ "id": "yue-test", "name": "粵", "nameEnglish": "Cantonese", "label": "粵", "locale": "yue", "script": "Hani",
    "keys": "qwerty", "engine": { "$engine": "$schema" } }""",
)

private val dataDir = kotlin.io.path.createTempDirectory("tk-rime-data").toFile().apply { deleteOnExit() }

private fun started(
    schema: RimeEngine.Schema = RimeEngine.Schema.Yale,
    variant: ChineseVariant = ChineseVariant.HongKong,
): Pair<RimeEngine, FakeRime> {
    val rime = FakeRime()
    val engine = RimeEngine(rime) { true }
    engine.variant(variant)
    check(engine.start(method(schema.id), dataDir), "it starts on ${schema.id}")
    return engine to rime
}

private fun type(engine: RimeEngine, text: String) = text.map { engine.key(it) }.last()

fun eachVariantSetsExactlyOneOption() {
    for (variant in ChineseVariant.entries) {
        val on = ChineseVariant.options(variant).filter { it.second }.map { it.first }
        same(listOf(variant.option), on, "$variant turns on its own option only")
        same(3, ChineseVariant.options(variant).size, "$variant sets all three options")
    }
    same(listOf("zh_hk", "zh_tw", "zh_hans"), ChineseVariant.entries.map { it.setting }, "the settings' values, as the schemas' options")
    same(ChineseVariant.Taiwan, ChineseVariant.of("zh_tw"), "the setting zh_tw is Taiwan")
}

fun aMissingSettingIsNoVariant() {
    same(null, ChineseVariant.of(null), "no setting is no variant: there's no default")
    same(null, ChineseVariant.of(""), "an empty setting is no variant")
    same(null, ChineseVariant.of("zh_cn"), "an unknown setting is no variant")
}

fun theVariantIsSetAgainAfterASchemaSwitch() {
    val (engine, rime) = started(RimeEngine.Schema.Pinyin, ChineseVariant.Simplified)
    same(true, rime.options["zh_hans"], "Simplified is on")
    same(false, rime.options["zh_hk"], "the schema's own reset to Hong Kong is undone")
    check(engine.start(method("tk_yale"), dataDir), "a second Chinese input method starts")
    same(true, rime.options["zh_hans"], "after a switch to Yale, Simplified is still on")
    same(false, rime.options["zh_hk"], "and Hong Kong off")
    same(1, rime.calls.count { it == "schema tk_yale" }, "Yale was selected once")
    same(1, rime.calls.count { it == "open" }, "Rime opened once for both")
    engine.variant(ChineseVariant.Taiwan)
    same(1, rime.calls.count { it == "schema tk_yale" }, "a variant change doesn't select the schema again")
    same(listOf("zh_tw"), rime.options.filter { it.value }.keys.toList(), "only Taiwan is on")
}

fun lettersGoToRime() {
    val (engine, _) = started()
    val state = type(engine, "neihou")
    check(state.handled, "a letter is Rime's")
    same("", state.commit, "nothing is typed yet")
    same("neiho-u", state.code, "the code shows as Rime spells it")
    same(8, state.choices.size, "a page of choices")
}

fun spaceTypesTheFirstChoice() {
    val (engine, _) = started()
    type(engine, "nei")
    val state = engine.space()
    check(state.handled, "Space is taken while a code is typed")
    same("NEI0", state.commit, "Space types the first choice")
    same("", state.code, "and the code is gone")
}

fun withNoCodeTheKeyboardKeepsItsKeys() {
    val (engine, _) = started()
    for ((key, state) in listOf("Space" to engine.space(), "Backspace" to engine.backspace(), "Enter" to engine.enter())) {
        check(!state.handled, "with no code, $key is the keyboard's")
        same("", state.commit, "and nothing is typed for it")
    }
    same(EngineState.UNHANDLED, engine.page(true), "with no code, there's no page to turn")
}

fun enterTypesTheCodeAsLetters() {
    val (engine, _) = started()
    type(engine, "ngo")
    val state = engine.enter()
    check(state.handled, "Enter is taken while a code is typed")
    same("ngo", state.commit, "Enter types the code as typed letters")
    same("", state.code, "and clears it: nothing stays pending")
    check(state.choices.isEmpty(), "and no choices are left on the bar")
    same(EngineState.UNHANDLED, engine.enter(), "with nothing pending, Enter is the keyboard's: the field gets it")
}

fun backspaceDeletesFromTheCode() {
    val (engine, _) = started()
    type(engine, "nei")
    val state = engine.backspace()
    check(state.handled, "Backspace is taken while a code is typed")
    same("ne", state.code, "it deletes from the code")
    engine.backspace()
    engine.backspace()
    check(!engine.backspace().handled, "once the code is empty, Backspace deletes from the text")
    val (cleared, _) = started()
    type(cleared, "nei")
    same("", cleared.reset().code, "a reset drops the code")
    same("", cleared.state().commit, "with nothing typed")
}

fun aCapitalTypesTheCodeFirst() {
    val (engine, _) = started()
    type(engine, "hou")
    val state = engine.key('A')
    check(!state.handled, "the keyboard types the capital itself")
    same("HOU0", state.commit, "after the code's first choice")
    val (idle, _) = started()
    same(EngineState.UNHANDLED, idle.key('A'), "with no code, a capital is simply the keyboard's")
    val (accent, _) = started()
    type(accent, "nei")
    same("NEI0", accent.key('é').commit, "a character Rime has no key for types after the code's first choice")
}

fun punctuationGoesThroughRime() {
    val (engine, _) = started()
    val state = engine.key(',')
    check(state.handled, "a comma is Rime's")
    same("，", state.commit, "and comes out as a Chinese comma")
}

fun punctuationWithACodeTypesTheFirstChoiceFirst() {
    val (engine, _) = started()
    type(engine, "nei")
    val state = engine.key(',')
    check(state.handled, "a comma with a code typed is Rime's")
    same("NEI0，", state.commit, "the code's first choice, then the mark")
    same("", state.code, "and the code is gone")
}

fun quotesFollowTheVariant() {
    same("「", started().first.key('"').commit, "Hong Kong quotes with 「」")
    same("「", started(variant = ChineseVariant.Taiwan).first.key('"').commit, "so does Taiwan")
    same("“", started(variant = ChineseVariant.Simplified).first.key('"').commit, "Simplified keeps “”")
    same("「」『』", com.titankeys.keyboard.ChineseKeys.marks("“”‘’", "zh_hk"), "every quote")
    same("你好", com.titankeys.keyboard.ChineseKeys.marks("你好", "zh_hk"), "nothing else changes")
}

fun plainPunctuationIsTheKeyboards() {
    val (engine, _) = started()
    engine.option(com.titankeys.keyboard.ChineseKeys.PLAIN_PUNCTUATION, true)
    val state = engine.key(',')
    check(!state.handled, "with plain punctuation, the keyboard types the comma as it is")
    same("", state.commit, "and Rime types nothing")
    type(engine, "nei")
    val coded = engine.key(',')
    check(!coded.handled, "with a code typed, the comma is still the keyboard's")
    same("NEI0", coded.commit, "after the code's first choice")
}

fun digitsStayPlain() {
    val (engine, _) = started()
    same(EngineState.UNHANDLED, engine.key('1'), "with no code, a digit is the keyboard's, plain")
    type(engine, "nei")
    val state = engine.key('2')
    check(!state.handled, "with a code typed, the keyboard types the digit")
    same("NEI0", state.commit, "after the code's first choice, never the second choice Rime's 2 would pick")
}

fun choicesReadAcrossPages() {
    val (engine, _) = started()
    type(engine, "sik")
    same(List(10) { "SIK$it" }, engine.choices(0, 10), "the row and the menu read past Rime's 8 a page")
    same(listOf("SIK20", "SIK21", "SIK22", "SIK23"), engine.choices(20, 10), "up to the last")
    val picked = engine.pickAt(9)
    check(picked.handled, "a pick across pages is taken")
    same("SIK9", picked.commit, "and types that choice")
    same(listOf("SIK9+0", "SIK9+1", "SIK9+2"), engine.choices(0, 10), "with no code, the choices are the next-word suggestions")
    engine.reset()
    same(emptyList<String>(), engine.choices(0, 10), "and once they're cleared, none")
    val (none, _) = started()
    none.option("zh_hk", false)
    type(none, "nei")
    same(listOf(RimeEngine.NO_VARIANT), none.choices(0, 10), "with no variant, only the line that asks for one")
    val raw = RimeCalls.Raw.of(arrayOf(null, "world", "world", intArrayOf(0, 1, 0, 5, 8, 8, 1), arrayOf("喎"), arrayOf("")))
    check(raw.partial, "rime_jni.cc says when the choices cover only part of the code")
    check(!RimeCalls.Raw.of(arrayOf(null, "nei", "nei", intArrayOf(0, 1, 0, 3, 8, 8), arrayOf("你"), arrayOf(""))).partial,
        "an older state without it reads as whole")
    type(engine, "hello")
    same("hello", engine.state().typed, "the letters as typed")
}

fun pickingAndPaging() {
    val (engine, rime) = started()
    type(engine, "sik")
    same("SIK8", engine.page(true).choices.first(), "forward turns to the next page")
    same("SIK0", engine.page(false).choices.first(), "back turns back")
    engine.page(true)
    val picked = engine.pick(2)
    check(picked.handled, "a pick on the page is taken")
    same("SIK10", picked.commit, "the pick is from the page showing")
    check(!engine.pick(-1).handled, "no choice -1")
    check(rime.calls.contains("key ${RimeKeys.PAGE_DOWN}"), "paging is Rime's Page Down")
    type(engine, "sik")
    same(3, engine.state().pages, "24 choices at 8 a page are 3 pages")
    same("1/3", engine.state().pageMark, "and the bar says so")
    // rime_jni.cc's state(), as it comes: Rime counts the choices up to ten pages' worth, -1 past that.
    val raw = RimeCalls.Raw.of(arrayOf("你", "nei", "nei", intArrayOf(1, 0, 0, 3, 8, 17), arrayOf("你", "妳"), arrayOf("", "")))
    same(listOf(1, 3, 8, 17), listOf(raw.page, raw.pages, raw.pageSize, raw.total), "the page, its pages, their size and the count")
    same(false, raw.lastPage, "and whether more follow")
    same(0, raw.copy(total = -1).pages, "too many to count is no page count")
}

fun nothingBeforeStart() {
    val rime = FakeRime().apply { opened = false }
    val engine = RimeEngine(rime) { true }
    engine.variant(ChineseVariant.HongKong)
    same(EngineState.UNHANDLED, engine.key('a'), "before start, keys stay the keyboard's")
    check(!engine.start(method("tk_yale"), dataDir), "a failed start says so")
    same(EngineState.UNHANDLED, engine.key('a'), "keys stay the keyboard's")
    same(EngineState.UNHANDLED, engine.space(), "Space too")
    same(listOf("open"), rime.calls, "nothing else reached Rime")
}

fun onlyARimeInputMethodStarts() {
    val rime = FakeRime()
    val engine = RimeEngine(rime) { true }
    check(!engine.start(method("tk_yale", engine = "hangul"), dataDir), "another engine's input method isn't Rime's")
    check(!engine.start(method("luna_pinyin"), dataDir), "a schema TitanKeys doesn't ship doesn't start")
    same(null, engine.schema, "and no schema is current")
    check(engine.start(method("tk_jyutping"), dataDir), "Jyutping starts")
    same("tk_jyutping", engine.schema, "and is current")
    engine.stop()
    same(listOf("close"), rime.calls.takeLast(1), "stop closes Rime")
    same(EngineState.UNHANDLED, engine.key('a'), "and keys are the keyboard's again")
}

fun theDataInstallsBeforeRimeOpens() {
    val rime = FakeRime()
    var installedTo: File? = null
    val engine = RimeEngine(rime) { data -> installedTo = data.shared; false }
    check(!engine.start(method("tk_yale"), dataDir), "with the data missing, Rime doesn't start")
    same(File(dataDir, "shared"), installedTo, "the data goes under the folder the keyboard gives")
    same(emptyList<String>(), rime.calls, "and Rime isn't opened without it")
    val opened = RimeEngine(rime) { true }
    check(opened.start(method("tk_yale"), dataDir), "with the data there, it starts")
    same("open", rime.calls.first(), "Rime opens")
}

fun withNoVariantNothingChineseIsTyped() {
    val rime = FakeRime()
    val engine = RimeEngine(rime) { true }
    // The keyboard sets every variant off until one is picked.
    for (option in listOf("zh_hk", "zh_tw", "zh_hans")) engine.option(option, false)
    check(engine.start(method("tk_yale"), dataDir), "it starts with no variant")
    check(!engine.variantPicked, "no variant is picked")
    val typed = type(engine, "nei")
    same("nei", typed.code, "the code still builds")
    same(listOf(RimeEngine.NO_VARIANT), typed.choices, "and the only choice says to pick a variant")
    val space = engine.space()
    check(space.handled, "Space is taken")
    same("", space.commit, "but types nothing")
    same("", engine.pick(0).commit, "nor does picking the choice")
    same("", engine.key('1').commit, "nor a digit")
    same("", engine.page(true).commit, "and there are no pages")
    same("nei", engine.enter().commit, "Enter still types the code as letters")
    engine.variant(ChineseVariant.Taiwan)
    check(engine.variantPicked, "once one is picked")
    type(engine, "nei")
    same("NEI0", engine.space().commit, "Space types the first choice again")
}

fun switchingInputMethodsKeepsRimeOpen() {
    val rime = FakeRime()
    val yale = RimeEngine(rime) { true }
    check(yale.start(method("tk_yale"), dataDir), "Yale starts")
    // The keyboard starts the next engine, then stops the last one.
    val pinyin = RimeEngine(rime) { true }
    pinyin.variant(ChineseVariant.Simplified)
    check(pinyin.start(method("tk_pinyin"), dataDir), "Pinyin starts")
    yale.stop()
    same(0, rime.calls.count { it == "close" }, "Rime stays open for Pinyin")
    same("SIK0", type(pinyin, "sik").choices.first(), "and Pinyin types")
    pinyin.stop()
    same(1, rime.calls.count { it == "close" }, "it closes when the last engine stops")
    same(1, rime.calls.count { it == "open" }, "having opened once")
}

/**
 * An input method turned on while Rime is open (Yale's warm engine keeps it open): its files are copied, and Rime opens again
 * with them, when it's switched to. Before, the copy waited for Rime's next start, which a warm engine never lets come, so
 * Cangjie turned on beside Yale typed English until the keyboard restarted.
 */
fun anInputMethodTurnedOnWhileRimeIsOpenStarts() {
    val rime = FakeRime()
    val on = mutableSetOf("tk_yale")
    rime.installed = emptySet()
    val install = { _: RimeData -> rime.installed = on.toSet(); true }
    val warm = RimeEngine(rime, install)
    check(warm.warm(method("tk_yale"), dataDir), "Yale's engine warms Rime up")
    val yale = RimeEngine(rime, install)
    check(yale.start(method("tk_yale"), dataDir), "Yale starts")
    // Cangjie turned on in Settings, then switched to: the keyboard starts its engine, then stops Yale's.
    on += "tk_cangjie"
    val cangjie = RimeEngine(rime, install)
    check(cangjie.start(method("tk_cangjie"), dataDir), "Cangjie starts")
    yale.stop()
    same("tk_cangjie", cangjie.schema, "with its schema")
    check(rime.calls.count { it == "close" } >= 1 && rime.calls.last { it == "open" || it == "close" } == "open",
        "Rime opened again with Cangjie's files, and stays open")
    warm.stop()
    cangjie.stop()
    same(rime.calls.count { it == "open" }, rime.calls.count { it == "close" }, "each open has its close once the engines stop")
}

/** No default variant: the first Chinese switch with none picked opens Settings' Chinese page, once, and never in the guide. */
fun withNoVariantTheChinesePageOpensOnce() {
    val yale = method("tk_yale")
    check(ChineseSetup.opensPage(yale, variant = "", shown = false, typing = true), "the first switch with no variant opens the page")
    check(!ChineseSetup.opensPage(yale, variant = "", shown = true, typing = true), "only once")
    check(!ChineseSetup.opensPage(yale, variant = "zh_hk", shown = false, typing = true), "not once a variant is picked")
    check(!ChineseSetup.opensPage(yale, variant = "", shown = false, typing = false), "not outside a field, or in the guide")
    check(!ChineseSetup.opensPage(method("x", engine = "other"), variant = "", shown = false, typing = true), "not for another engine")
    same(RimeEngine.ENGINE, "rime", "the engine ChineseSetup knows as Chinese is Rime's")
    check(ChineseSetup.PAGE == "/settings/chinese", "the page is Settings' Chinese characters")
}

/** The input method files the keyboard reads: each Chinese one names a schema RimeEngine knows, Yale first. */
fun theChineseInputMethodsAreRimes(folder: String?) {
    val root = File(folder ?: return)
    val schemas = RimeEngine.Schema.entries.map { it.id }
    val chinese = root.listFiles()!!.filter { File(it, "im.json").isFile }.map { InputMethod.parse(File(it, "im.json").readText()) }
        .filter { it.engine == RimeEngine.ENGINE }
    same(schemas.sorted(), chinese.map { it.engineArg }.sortedBy { it }, "one input method per schema")
    for (im in chinese) {
        same("Hani", im.script, "${im.id} is in Chinese characters")
        same(null, im.words, "${im.id} has no word list of its own")
        check(!im.hasAccents, "${im.id} holds no accents")
    }
    val order = Regex("\"id\": \"([^\"]+)\"").findAll(File(root, "index.json").readText()).map { it.groupValues[1] }.toList()
    val ids = chinese.associate { it.engineArg to it.id }
    check(order.indexOf(ids["tk_yale"]) < order.indexOf(ids["tk_jyutping"]), "Yale comes before Jyutping")
    check(order.indexOf(ids["tk_jyutping"]) < order.indexOf(ids["tk_pinyin"]), "and Jyutping before Pinyin")
}

fun theBackupTakesRimesSnapshots() {
    val dir = kotlin.io.path.createTempDirectory("rime-backup").toFile()
    try {
        val rime = FakeRime()
        same(emptyMap<String, String>(), RimeBackup.export(dir, rime), "with Chinese never used, there's nothing")
        same(emptyList<String>(), rime.calls, "and Rime isn't opened for it")
        val data = RimeData(dir)
        File(data.shared, ".installed").apply { parentFile.mkdirs(); writeText("x") }
        data.snapshots.mkdirs()
        File(data.snapshots, "jyut6ping3.userdb.txt").writeText("nei hou \t你好\tc=3\n")
        File(data.snapshots, "notes.txt").writeText("not Rime's")
        val words = RimeBackup.export(dir, rime)
        same(listOf("open", "sync", "close"), rime.calls, "with Rime closed, it opens for the sync and closes again")
        same(mapOf("jyut6ping3.userdb.txt" to "nei hou \t你好\tc=3\n"), words, "only Rime's snapshots go in")
        same("installation_id: \"titankeys\"\n", File(data.user, "installation.yaml").readText(), "under the name we know")
        // With an engine using Rime, the sync goes through it, and Rime stays open.
        val engine = RimeEngine(rime) { true }
        check(engine.start(method("tk_yale"), dir), "an engine starts")
        rime.calls.clear()
        RimeBackup.export(dir, rime)
        same(listOf("sync"), rime.calls, "the open Rime syncs, and nothing opens or closes")
    } finally {
        dir.deleteRecursively()
    }
}

fun aRestoredBackupMergesInAtTheNextStart() {
    val dir = kotlin.io.path.createTempDirectory("rime-restore").toFile()
    try {
        val data = RimeData(dir)
        RimeBackup.restore(dir, mapOf(
            "luna_pinyin.userdb.txt" to "ni hao \t你好\n", "jyut6ping3.userdb.txt" to "nei hou \t你好\n",
            "../escape.userdb.txt" to "x", "a.txt" to "y",
        ))
        same(listOf("jyut6ping3.userdb.txt", "luna_pinyin.userdb.txt"), data.restored.list()!!.sorted(),
            "only a snapshot's name is written, and inside the folder")
        // Rime merges a snapshot only into a dictionary the phone has: one appears once its input method is first used.
        check(!RimeBackup.pending(data), "with no Chinese used on this phone yet, nothing can merge")
        File(data.user, "luna_pinyin.userdb").mkdirs()
        check(RimeBackup.pending(data), "once Pinyin's dictionary is there, its snapshot can")
        val rime = FakeRime()
        val engine = RimeEngine(rime) { true }
        check(engine.start(method("tk_pinyin"), dir), "Pinyin starts")
        same(listOf("open", "schema tk_pinyin", "sync", "schema tk_pinyin"),
            rime.calls.filter { it == "open" || it == "sync" || it.startsWith("schema") },
            "Rime opens Pinyin's dictionary, merges the backup in, then selects Pinyin again")
        same(listOf("jyut6ping3.userdb.txt"), data.restored.list()!!.toList(), "Pinyin's snapshot is merged, so it isn't merged again")
        check(!RimeBackup.pending(data), "Cantonese's waits for its dictionary, without a sync on every start")
        same("ni", type(engine, "ni").code, "and Pinyin types")
        RimeBackup.restore(dir, emptyMap())
        same(listOf("jyut6ping3.userdb.txt"), data.restored.list()!!.toList(), "a backup with no Chinese words adds nothing")
    } finally {
        dir.deleteRecursively()
    }
}

fun aSyncWhileTypingSelectsTheSchemaAgain() {
    val (engine, rime) = started(RimeEngine.Schema.Pinyin, ChineseVariant.Taiwan)
    check(RimeEngine.sync(rime, RimeData(dataDir)), "a backup syncs while Pinyin is in use")
    same(emptyMap<String, Boolean>(), rime.options, "the sync closed the session")
    type(engine, "ni")
    same(2, rime.calls.count { it == "schema tk_pinyin" }, "the next key selects Pinyin again")
    same(true, rime.options["zh_tw"], "with the variant")
    same("ni", engine.state().code, "and types")
}

private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun manifest(files: Map<String, ByteArray>) = files.entries.joinToString("") { "${sha(it.value)}  ${it.key}\n" }

fun theDataCopiesOnceAndChecksEachFile() {
    val root = kotlin.io.path.createTempDirectory("rime").toFile()
    try {
        val files = mapOf("default.yaml" to "a".toByteArray(), "build/jyut6ping3.table.bin" to ByteArray(200_000) { it.toByte() })
        val list = manifest(files)
        val data = RimeData(root)
        var opened = 0
        check(data.install(list) { opened++; files.getValue(it).inputStream() }, "the data installs")
        same(2, opened, "every file was copied")
        check(files.all { (path, bytes) -> File(data.shared, path).readBytes().contentEquals(bytes) }, "byte for byte")
        File(data.user, "learned.userdb").apply { parentFile.mkdirs(); writeText("mine") }
        check(data.install(list) { opened++; files.getValue(it).inputStream() }, "installed again")
        same(2, opened, "the same data isn't copied twice")
        val newer = files + ("opencc/t2s.json" to "{}".toByteArray())
        File(data.user, "build").mkdirs()
        check(data.install(manifest(newer)) { newer.getValue(it).inputStream() }, "new data installs")
        check(File(data.shared, "opencc/t2s.json").isFile, "with its new file")
        check(!File(data.user, "build").exists(), "what Rime compiled from the old data is gone")
        same("mine", File(data.user, "learned.userdb").readText(), "learned words stay")
    } finally {
        root.deleteRecursively()
    }
}

fun aBadCopyKeepsTheOldData() {
    val root = kotlin.io.path.createTempDirectory("rime").toFile()
    try {
        val data = RimeData(root)
        val good = mapOf("default.yaml" to "a".toByteArray())
        check(data.install(manifest(good)) { good.getValue(it).inputStream() }, "the first data installs")
        val list = manifest(mapOf("default.yaml" to "b".toByteArray()))
        check(!data.install(list) { "corrupt".byteInputStream() }, "a file whose hash is wrong fails")
        same("a", File(data.shared, "default.yaml").readText(), "the old data stays")
        check(!File(root, "shared.new").exists(), "and the half copy is gone")
        check(!data.install(list) { throw java.io.FileNotFoundException(it) }, "a missing file fails")
        same(null, RimeData.parse("not a manifest"), "a list that isn't sha256sum's isn't read")
        // An update cut short after a file was swapped in (a later file failed, or the process died before the end): what
        // Rime compiled from the old data goes as the first file changes, not at the end, so it never outlives the data.
        val two = mapOf("default.yaml" to "a".toByteArray(), "tk_yale.schema.yaml" to "y".toByteArray())
        check(data.install(manifest(two)) { two.getValue(it).inputStream() }, "two files install")
        File(data.user, "build").mkdirs()
        val newer = mapOf("default.yaml" to "c".toByteArray(), "tk_yale.schema.yaml" to "z".toByteArray())
        check(!data.install(manifest(newer)) { path -> if (path == "default.yaml") newer.getValue(path).inputStream() else
            throw java.io.FileNotFoundException(path) }, "the update fails at its second file")
        same("c", File(data.shared, "default.yaml").readText(), "its first file is in")
        check(!File(data.user, "build").exists(), "and Rime's build from the old data is already gone")
    } finally {
        root.deleteRecursively()
    }
}

/**
 * Only the files of the Chinese input methods that are on are copied, and the files no schema claims; turning one on copies
 * its files, turning one off removes them, and an update copies only what changed, one file at a time, so the phone never
 * holds two copies of the data (docs/studies/3.0-storage.md).
 */
fun onlyTheInputMethodsOnAreCopied() {
    val root = kotlin.io.path.createTempDirectory("rime").toFile()
    try {
        val common = "default.yaml"
        val cantonese = "build/jyut6ping3.table.bin"
        val grammar = "tk_yue.gram"
        val mandarin = "build/luna_pinyin.table.bin"
        val files = mapOf(common to "a".toByteArray(), cantonese to ByteArray(1000) { 1 }, grammar to ByteArray(500) { 2 },
            mandarin to ByteArray(800) { 3 })
        val groups = RimeData.groups("tk_yale\t$cantonese\ntk_yale\t$grammar\ntk_jyutping\t$cantonese\ntk_pinyin\t$mandarin\n")
        same(setOf(cantonese, grammar), groups["tk_yale"], "the schemas file reads")
        val data = RimeData(root)
        val opened = mutableListOf<String>()
        val open = { path: String -> opened += path; files.getValue(path).inputStream() }
        check(data.install(manifest(files), groups, setOf("tk_yale"), open), "Yale's data installs")
        same(setOf(common, cantonese, grammar), opened.toSet(), "the shared files and Yale's, only")
        check(!File(data.shared, mandarin).exists(), "Pinyin's table isn't copied")
        opened.clear()
        check(data.install(manifest(files), groups, setOf("tk_yale", "tk_pinyin"), open), "Pinyin turned on")
        same(listOf(mandarin), opened, "copies only Pinyin's files")
        opened.clear()
        check(data.install(manifest(files), groups, setOf("tk_pinyin"), open), "Yale turned off")
        same(emptyList<String>(), opened, "copies nothing")
        check(!File(data.shared, cantonese).exists() && !File(data.shared, grammar).exists(), "and Yale's files go")
        check(File(data.shared, mandarin).isFile && File(data.shared, common).isFile, "Pinyin's and the shared ones stay")
        // An update: only the changed file is copied, beside its place, then swapped in.
        val newer = files + (mandarin to ByteArray(800) { 4 })
        opened.clear()
        File(data.user, "build").mkdirs()
        check(data.install(manifest(newer), groups, setOf("tk_pinyin")) { path -> opened += path; newer.getValue(path).inputStream() },
            "new data installs")
        same(listOf(mandarin), opened, "only the file that changed is copied")
        check(File(data.shared, mandarin).readBytes().contentEquals(newer.getValue(mandarin)), "byte for byte")
        check(!File(root, "shared.new").exists() && data.shared.walk().none { it.name.endsWith(".new") },
            "and no second copy is left anywhere")
        check(!File(data.user, "build").exists(), "what Rime compiled from the old data is gone")
        // A file that's gone from the data goes from the phone too.
        check(data.install(manifest(newer - common), groups, setOf("tk_pinyin")) { newer.getValue(it).inputStream() },
            "data without a file installs")
        check(!File(data.shared, common).exists(), "and that file is gone")
    } finally {
        root.deleteRecursively()
    }
}

/** An install from before (the whole data, one marker of every file) carries on without copying anything again. */
fun anOlderInstallCarriesOn() {
    val root = kotlin.io.path.createTempDirectory("rime").toFile()
    try {
        val files = mapOf("default.yaml" to "a".toByteArray(), "build/luna_pinyin.table.bin" to ByteArray(10) { 3 })
        val data = RimeData(root)
        data.shared.mkdirs()
        for ((path, bytes) in files) File(data.shared, path).apply { parentFile.mkdirs(); writeBytes(bytes) }
        File(data.shared, ".installed").writeText(manifest(files))
        var opened = 0
        check(data.install(manifest(files)) { opened++; files.getValue(it).inputStream() }, "the older install is current")
        same(0, opened, "nothing is copied again")
    } finally {
        root.deleteRecursively()
    }
}

fun aManifestCannotLeaveItsFolder() {
    val hash = "0".repeat(64)
    same(null, RimeData.parse("$hash  ../evil"), "no way out of the folder")
    same(null, RimeData.parse("$hash  /etc/x"), "no absolute paths")
    same(listOf(RimeData.Entry("build/a.bin", hash)), RimeData.parse("$hash  build/a.bin\n"), "a plain line reads")
}

// The next-word suggestions (librime-predict, #56; docs/languages.md, "Next-word suggestions").

private fun committedNei(): Pair<RimeEngine, FakeRime> {
    val (engine, rime) = started()
    type(engine, "nei")
    engine.space()
    return engine to rime
}

fun nextWordSuggestionsFollowACommit() {
    val (engine, _) = started()
    type(engine, "nei")
    val state = engine.space()
    same("NEI0", state.commit, "Space types the code's first choice")
    same(listOf("NEI0+0", "NEI0+1", "NEI0+2"), state.choices, "and the words that may come next are its choices")
    same("", state.code, "with nothing typed")
    check(!state.composing, "which isn't a code being built")
    same(listOf("NEI0+0", "NEI0+1", "NEI0+2"), engine.choices(0, 9), "the bar's row reads them as it reads a code's choices")
}

/** Space never picks a next word (owner, 2026-11-17: "space shdnt type the next word"): it's a space, and the words go. */
fun spacePicksTheFirstSuggestion() {
    val (engine, _) = committedNei()
    val state = engine.space()
    check(!state.handled, "Space with only next words is the keyboard's: a plain space")
    same("", state.commit, "and types no next word")
    same(emptyList<String>(), state.choices, "and the next words go")
    val (other, _) = committedNei()
    same("NEI0+2", other.pickAt(2).commit, "a tap or the bigger menu picks any of them")
}

fun otherKeysClearTheSuggestions() {
    for ((key, press) in listOf<Pair<String, (RimeEngine) -> EngineState>>(
        "Backspace" to { it.backspace() }, "Enter" to { it.enter() }, "a digit" to { it.key('1') },
        "a capital" to { it.key('A') }, "a page key" to { it.page(true) },
    )) {
        val (engine, rime) = committedNei()
        val state = press(engine)
        check(!state.handled, "$key is the keyboard's own with suggestions on the bar")
        same("", state.commit, "and types nothing from Rime")
        same(emptyList<String>(), state.choices, "and the suggestions go, so the bar closes")
        check(rime.calls.contains("key ${RimeKeys.ESCAPE}"), "cleared with Escape, Rime's way: $key")
    }
    val (engine, _) = committedNei()
    val state = engine.key(',')
    same("，", state.commit, "a mark goes through Rime as it does with nothing typed")
    same(emptyList<String>(), state.choices, "and the suggestions go first")
}

fun aLetterStartsANewCode() {
    val (engine, _) = committedNei()
    val state = engine.key('h')
    same("h", state.typed, "a letter starts a new code")
    same("", state.commit, "and types none of the suggestions")
    same("H0", state.choices.first(), "the bar shows the code's choices instead")
}

fun aNewFieldStartsWithNoSuggestions() {
    val (fresh, _) = started()
    same(emptyList<String>(), fresh.state().choices, "nothing typed yet: the bar stays empty (owner)")
    val (engine, rime) = committedNei()
    val state = engine.reset()
    same(emptyList<String>(), state.choices, "a new field, or Back, clears them")
    check(rime.calls.contains("key ${RimeKeys.ESCAPE}"), "with Escape, since clearing alone brings them back")
}

fun noSuggestionsBeforeAVariant() {
    val (engine, rime) = committedNei()
    for (variant in ChineseVariant.entries) engine.option(variant.option, false)
    same(emptyList<String>(), engine.state().choices, "before a variant is picked they'd be Rime's Traditional: none show")
    check(!engine.space().handled, "and Space stays the keyboard's")
    check(rime.suggestions.isNotEmpty(), "they stay in Rime until a variant is picked")
}

/**
 * A switch to Yale with Rime warm loads nothing (owner, 2026-11-17: "I'm still seeing loading for Yale"): the warm engine
 * selects Yale's schema as it warms, and the switch starts that same engine, which then has nothing left to do. A warm-up never
 * takes the session from an engine that has selected its own.
 */
fun theWarmEngineHasYaleReady() {
    val rime = FakeRime()
    val warm = RimeEngine(rime) { true }
    check(warm.warm(method("tk_yale"), dataDir), "Yale's engine warms up")
    same(listOf("open", "schema tk_yale"), rime.calls.filter { it == "open" || it.startsWith("schema") }, "opening Rime and selecting Yale")
    check("selected in" in warm.timing, "and says what that took: ${warm.timing}")
    rime.calls.clear()
    check(warm.start(method("tk_yale"), dataDir), "the switch to Yale starts the warm engine")
    same(emptyList<String>(), rime.calls.filter { it == "open" || it.startsWith("schema") }, "which opens and selects nothing")
    same("Rime open already, tk_yale selected already", warm.timing, "and says so")
    check(warm.start(method("tk_pinyin"), dataDir), "a switch to Pinyin starts it too")
    same(listOf("schema tk_pinyin"), rime.calls.filter { it.startsWith("schema") }, "selecting Pinyin's schema")
    warm.stop()
    // Another engine typing before the warm-up ends keeps the session: the warm-up only opens.
    val other = FakeRime()
    val typing = RimeEngine(other) { true }
    check(typing.start(method("tk_pinyin"), dataDir), "Pinyin types before the warm-up is done")
    val late = RimeEngine(other) { true }
    check(late.warm(method("tk_yale"), dataDir), "the warm-up still warms")
    same(listOf("schema tk_pinyin"), other.calls.filter { it.startsWith("schema") }, "without selecting Yale over Pinyin")
    same("tk_pinyin", typing.schema, "Pinyin keeps typing")
    late.stop()
    typing.stop()
}

/**
 * The bar's words (starters, learned next words) show as Rime's choices while it has none, as many as are offered, not three
 * (owner, 2026-11-17: "there shd be more than 3 words for chinese for suggestion").
 */
fun theBarsWordsShowAsChoices() {
    val (engine, _) = started()
    val words = (1..6).map { "w$it" }
    val state = engine.offer(words)
    check(state.handled, "the bar's words are taken with nothing typed")
    same(words, state.choices, "and show as the choices, all of them")
    same(words.drop(2).take(3), engine.choices(2, 3), "read across as Rime's are")
    same("", state.code, "with no code")
}

/**
 * A pick of a bar word types it with no space, and Rime's next words for it follow (owner, 2026-11-17: "chinese suggestion
 * (first word) doesn't get updated after pressed and types a space").
 */
fun aPickedBarWordBringsItsNextWords() {
    for (how in listOf("tap")) {
        val (engine, rime) = started()
        engine.offer(listOf("你好", "我"))
        val state = engine.pickAt(0)
        same("你好", state.commit, "a $how types the word, with no space")
        check("after 你好" in rime.calls, "and Rime hears of it ($how)")
        same(listOf("你好+0", "你好+1", "你好+2"), state.choices, "so its next words show ($how)")
        same(emptyList<String>(), engine.choices(0, 9).filter { it == "我" }, "and the offer is gone ($how)")
    }
}

/** Never over a code, or Rime's own suggestions; a letter ends the offer and starts a code. */
fun theBarsWordsNeverCoverACode() {
    val (engine, _) = started()
    type(engine, "nei")
    same(EngineState.UNHANDLED, engine.offer(listOf("w")), "not while a code is typed")
    engine.space()
    val topped = engine.offer(listOf("w", "NEI0+1"))
    same(listOf("NEI0+0", "NEI0+1", "NEI0+2", "w"), topped.choices,
        "after Rime's own next words, the bar's that aren't among them: the row is never short (owner: only 3 suggestions)")
    same("w", engine.pickAt(3).commit, "and a tap on one of the bar's picks it")
    engine.reset()
    engine.offer(listOf("w"))
    val state = engine.key('n')
    same("n", state.typed, "a letter starts a code")
    check("w" !in state.choices, "and the offer goes")
    same(EngineState.UNHANDLED, started().first.let { e -> e.offer(listOf("w")); e.enter() }, "Enter with an offer is the keyboard's")
    val space = started().first.let { e -> e.offer(listOf("w")); e.space() }
    check(!space.handled && space.commit.isEmpty(), "Space with the bar's words is a plain space, picking none")
}

/**
 * A flick with part of a code picked (頂你個 picked, "fai" pending) takes only the pending code. The next types what was
 * picked less its last character, so one flick is one character (owner, 2026-11-17), cleared rather than committed in Rime, so
 * it learns nothing. With nothing picked, the code goes whole.
 */
fun aFlickKeepsWhatWasPicked() {
    val (engine, rime) = started()
    type(engine, "fai")
    rime.pickedCode = "dingneigo"
    rime.pickedText = "頂你個"
    val first = engine.flick()
    same("", first.commit, "the first flick types nothing")
    same("頂你個", first.code, "and the picked part stays on the bar")
    val second = engine.flick()
    same("頂你", second.commit, "the next types what was picked, less its last character")
    same("", second.code, "and nothing is left being built")
    check("reset" in rime.calls, "cleared, never committed: Rime learns nothing from it")
    rime.calls.clear()
    // Enter with part picked types the picked words, then the letters still pending (owner, 2026-11-17).
    type(engine, "fai")
    rime.pickedCode = "dingneigo"
    rime.pickedText = "頂你個"
    val entered = engine.enter()
    same("頂你個fai", entered.commit, "Enter types what was picked, then the pending letters")
    same("", entered.code, "and clears the bar")
    rime.calls.clear()
    type(engine, "nei")
    val whole = engine.flick()
    same("", whole.commit, "with nothing picked the code goes whole, typing nothing")
    same("", whole.code, "and leaves nothing")
    same("𠮩", ChineseKeys.dropLastCharacter("𠮩𠮩"), "a character outside the BMP counts as one")
}
