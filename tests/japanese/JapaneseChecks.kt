package com.titankeys.mozc

import com.titankeys.keyboard.ChineseKeys
import com.titankeys.keyboard.EngineState
import com.titankeys.keyboard.InputMethod
import java.io.File

/**
 * Japanese through Mozc (docs/languages.md, "Japanese"): the engine's keys on the bar, with a stand-in for Mozc, and the
 * romaji the readings turn into. The real Mozc is checked by native/mozc/build.sh and the corpus test.
 */
fun main() {
    lettersBuildTheText()
    spaceTypesTheFirstChoice()
    enterTypesTheRomaji()
    backspaceTakesOffALetter()
    aPartPickedLeavesTheRest()
    choicesReadAcrossTheList()
    punctuationIsFullWidth()
    digitsStayPlain()
    mozcStaysOpenWhileAnEngineUsesIt()
    japaneseUsesTheChineseBar()
    kanaToRomaji()
    nextWordsAfterAPick()
    println("Japanese checks passed")
}

private fun check(ok: Boolean, what: String) {
    if (!ok) throw AssertionError(what)
}

private fun <T> same(expected: T, actual: T, what: String) {
    if (expected != actual) throw AssertionError("$what\n  expected: $expected\n  actual:   $actual")
}

/**
 * A stand-in for Mozc: letters build a preedit (the letters in capitals), with twelve choices made from it; picking choice 1
 * takes a part ("PART") and leaves か; any other pick takes its choice and ends the text. With [suggests] on, a pick that ends
 * the text offers three next words, as Mozc's zero query does ("NEXT0" to "NEXT2"), and picking one offers three more.
 */
private class FakeMozc : MozcCalls {
    val calls = mutableListOf<String>()
    var opened = 0
    var preedit = ""
    var suggests = false
    private var next = emptyList<String>()
    private var commit: String? = null

    fun choices() = if (preedit.isEmpty()) next else List(12) { "${preedit}$it" }

    override fun open(data: String, profile: String): Boolean { opened++; calls += "open"; return true }
    override fun close() { calls += "close" }
    override fun key(char: Char): Boolean { calls += "key $char"; next = emptyList(); preedit += char.uppercaseChar(); return true }
    override fun pick(index: Int): Boolean {
        calls += "pick $index"
        val c = choices()
        if (index !in c.indices) return false
        if (index == 1 && preedit.isNotEmpty()) { commit = (commit ?: "") + "PART"; preedit = "か"; return true }
        commit = (commit ?: "") + c[index]
        preedit = ""
        next = if (suggests) List(3) { "NEXT$it" } else emptyList()
        return true
    }
    override fun reset() { calls += "reset"; preedit = ""; next = emptyList() }
    override fun read(): MozcCalls.Raw {
        val out = MozcCalls.Raw(commit, preedit, choices())
        commit = null
        return out
    }
}

private val dataDir = File(System.getProperty("java.io.tmpdir"), "tk-japanese-checks").apply { deleteRecursively(); mkdirs() }

private val japanese = InputMethod.parse(
    """{ "id": "ja", "name": "日本語", "nameEnglish": "Japanese", "label": "あ", "locale": "ja", "script": "Jpan",
    "keys": "qwerty", "engine": { "mozc": "oss" } }""",
)

private fun started(mozc: FakeMozc = FakeMozc()): Pair<MozcEngine, FakeMozc> {
    val engine = MozcEngine(mozc) { true }
    check(engine.start(japanese, dataDir), "it starts")
    return engine to mozc
}

private fun type(engine: MozcEngine, text: String) = text.map { engine.key(it) }.last()

fun lettersBuildTheText() {
    val (engine, mozc) = started()
    val state = type(engine, "nihon")
    check(state.handled, "a letter is Mozc's")
    same("NIHON", state.code, "the line shows Mozc's text being built")
    same("nihon", state.typed, "and the romaji as typed")
    same(8, state.choices.size, "a page of choices in the state")
    same("key n", mozc.calls.first { it.startsWith("key") }, "letters go to Mozc one by one")
    same("nihon", type(started().first, "NIHON").typed, "capitals type as letters: Japanese has none")
    engine.stop()
}

fun spaceTypesTheFirstChoice() {
    val (engine, _) = started()
    same(EngineState.UNHANDLED, engine.space(), "with nothing typed, Space is the keyboard's")
    type(engine, "ne")
    val state = engine.space()
    check(state.handled, "Space is taken")
    same("NE0", state.commit, "and types the first choice")
    same("", state.code, "and the text is done")
    engine.stop()
}

fun enterTypesTheRomaji() {
    val (engine, mozc) = started()
    type(engine, "konnichiha")
    val state = engine.enter()
    same("konnichiha", state.commit, "Enter types the romaji as typed")
    same("", state.code, "and nothing is left")
    check("reset" in mozc.calls, "Mozc drops its text")
    same(EngineState.UNHANDLED, engine.enter(), "with nothing typed, Enter is the keyboard's")
    engine.stop()
}

fun backspaceTakesOffALetter() {
    val (engine, mozc) = started()
    type(engine, "nihon")
    mozc.calls.clear()
    val state = engine.backspace()
    same("niho", state.typed, "the last romaji letter goes")
    same("NIHO", state.code, "Mozc starts again from the letters left")
    same(listOf("reset", "key n", "key i", "key h", "key o"), mozc.calls, "a letter, never a whole kana")
    engine.backspace(); engine.backspace(); engine.backspace()
    val last = engine.backspace()
    same("", last.code, "the last letter gone, nothing is built")
    same(EngineState.UNHANDLED, engine.backspace(), "then Backspace is the keyboard's")
    engine.stop()
}

fun aPartPickedLeavesTheRest() {
    val (engine, _) = started()
    type(engine, "kyouha")
    val state = engine.pickAt(1)
    same("PART", state.commit, "the part is typed")
    same("か", state.code, "the rest stays")
    same("ka", state.typed, "and Enter would type the rest as romaji")
    same("ka", engine.enter().commit, "it does")
    engine.stop()
}

fun choicesReadAcrossTheList() {
    val (engine, _) = started()
    type(engine, "ai")
    same(listOf("AI8", "AI9", "AI10", "AI11"), engine.choices(8, 10), "the row and the menu read past the state's page")
    same("AI10", engine.pickAt(10).commit, "and pick by place")
    same(emptyList<String>(), engine.choices(0, 10), "with nothing typed, no choices")
    engine.stop()
}

fun punctuationIsFullWidth() {
    val (engine, _) = started()
    same("。", engine.key('.').commit, "a full stop alone: 。")
    same("、", engine.key(',').commit, "a comma: 、")
    type(engine, "desu")
    same("DESU0。", engine.key('.').commit, "with text built, its first choice, then the mark")
    same("「」・〜ー？！", "[]/~-?!".map { JapaneseMarks.of(it) }.joinToString(""), "the other marks")
    type(engine, "ra")
    same("RA-", engine.key('-').code, "- inside a word is ー, Mozc's own")
    engine.stop()
}

fun digitsStayPlain() {
    val (engine, _) = started()
    same(EngineState.UNHANDLED, engine.key('1'), "with nothing typed, the keyboard types the digit, plain")
    type(engine, "ni")
    val state = engine.key('2')
    check(!state.handled, "with text built, the keyboard types the digit")
    same("NI0", state.commit, "after the first choice")
    engine.stop()
}

fun mozcStaysOpenWhileAnEngineUsesIt() {
    val mozc = FakeMozc()
    val (first, _) = started(mozc)
    val (second, _) = started(mozc)
    same(1, mozc.opened, "Mozc opens once for the app")
    first.stop()
    check("close" !in mozc.calls, "the old engine's stop leaves it open for the new one")
    second.stop()
    check("close" in mozc.calls, "the last one closes it")
}

fun japaneseUsesTheChineseBar() {
    check(ChineseKeys.usesBar(japanese), "Japanese types through the bar of choices, as Chinese does")
    check(!ChineseKeys.isChinese(japanese), "but isn't Chinese: no English word in its row")
}

fun kanaToRomaji() {
    same("kyouhaiitennkidesune", KanaRomaji.of("きょうはいいてんきですね"), "a sentence: ん as nn")
    same("gakkou", KanaRomaji.of("がっこう"), "っ doubles the consonant")
    same("chotto", KanaRomaji.of("ちょっと"), "before ch")
    same("shinnbunn", KanaRomaji.of("しんぶん"), "ん is nn, always safe")
    same("konnpyu-ta-", KanaRomaji.of("コンピューター"), "katakana, ー as -")
    same("axtu", KanaRomaji.of("あっ"), "a small tsu at the end")
    same("dhisuko", KanaRomaji.of("ディスコ"), "Mozc's own spellings: dhi")
    same("wo,ha.", KanaRomaji.of("を、は。"), "を, and the marks")
    same(null, KanaRomaji.of("漢字"), "kanji aren't kana")
}

fun nextWordsAfterAPick() {
    val (engine, mozc) = started(FakeMozc().apply { suggests = true })
    type(engine, "ne")
    val picked = engine.space()
    same("NE0", picked.commit, "Space types the first choice")
    same("", picked.code, "nothing is typed after it")
    same(listOf("NEXT0", "NEXT1", "NEXT2"), picked.choices, "and Mozc's next words are the bar's choices")
    same(listOf("NEXT1", "NEXT2"), engine.choices(1, 8), "the bigger menu reads them too")
    // Space picks the first suggestion, and a pick offers the next ones.
    val next = engine.space()
    same("NEXT0", next.commit, "Space picks the first suggestion")
    same(3, next.choices.size, "and the next ones come")
    same("NEXT2", engine.pickAt(2).commit, "a tap or the menu picks any")
    // A letter starts new text over them; the suggestions don't come back with it.
    val letter = engine.key('k')
    same("K", letter.code, "a letter starts new text")
    check(letter.choices.none { it.startsWith("NEXT") }, "without the suggestions")
    engine.reset()
    // Any other key clears them, and is the keyboard's own.
    type(engine, "ne"); engine.space()
    mozc.calls.clear()
    val mark = engine.key('.')
    same("。", mark.commit, "a mark is typed")
    same(emptyList<String>(), mark.choices, "and the suggestions are gone")
    check("reset" in mozc.calls, "in Mozc too, so they don't come back")
    type(engine, "ne"); engine.space()
    val back = engine.backspace()
    check(!back.handled && back.choices.isEmpty(), "Backspace clears them, then deletes as usual")
    same(EngineState.UNHANDLED, engine.backspace(), "and after that it's the keyboard's")
    type(engine, "ne"); engine.space()
    val enter = engine.enter()
    check(!enter.handled && enter.choices.isEmpty(), "Enter clears them, then is the keyboard's")
    type(engine, "ne"); engine.space()
    same(EngineState.UNHANDLED, engine.key('5'), "a digit clears them and is the keyboard's")
    same(EngineState.UNHANDLED, engine.space(), "after which Space is the keyboard's again")
    // A new field starts with none.
    type(engine, "ne"); engine.space()
    same(emptyList<String>(), engine.reset().choices, "a new field starts with none")
    engine.stop()
}
