package com.atlas.rime

import com.atlas.keyboard.EngineState
import com.atlas.keyboard.InputMethod
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
    pickingAndPaging()
    nothingBeforeStart()
    onlyARimeInputMethodStarts()
    theDataInstallsBeforeRimeOpens()
    withNoVariantNothingChineseIsTyped()
    switchingInputMethodsKeepsRimeOpen()
    theChineseInputMethodsAreRimes(args.firstOrNull())
    theBackupTakesRimesSnapshots()
    aRestoredBackupMergesInAtTheNextStart()
    aSyncWhileTypingSelectsTheSchemaAgain()
    theDataCopiesOnceAndChecksEachFile()
    aBadCopyKeepsTheOldData()
    aManifestCannotLeaveItsFolder()
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

    fun choices() = if (code.isEmpty()) emptyList() else (0 until 8).map { "${code.uppercase()}${page * 8 + it}" }

    override fun open(shared: String, user: String): Boolean {
        calls += "open"
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
        return id.startsWith("tk_")
    }

    override fun option(name: String, on: Boolean) {
        calls += "option $name $on"
        options[name] = on
    }

    override fun option(name: String) = options[name] == true

    override fun process(keycode: Int, mask: Int): Boolean {
        calls += "key $keycode"
        return when (keycode) {
            in 'a'.code..'z'.code -> { code += keycode.toChar(); page = 0; true }
            RimeKeys.SPACE -> if (code.isEmpty()) false else { commit = choices()[0]; code = ""; true }
            RimeKeys.RETURN -> if (code.isEmpty()) false else { commit = code; code = ""; true }
            RimeKeys.BACKSPACE -> if (code.isEmpty()) false else { code = code.dropLast(1); true }
            RimeKeys.PAGE_DOWN -> { page++; true }
            RimeKeys.PAGE_UP -> { if (page > 0) page--; true }
            ','.code -> { commit = "，"; true }
            else -> false
        }
    }

    override fun select(index: Int): Boolean {
        val c = choices()
        if (index !in c.indices) return false
        commit = c[index]
        code = ""
        page = 0
        return true
    }

    override fun reset() {
        calls += "reset"
        code = ""
    }

    /** As Rime's sync: it closes the session, so the schema and options are gone until selected again. */
    override fun sync(): Boolean {
        calls += "sync"
        code = ""
        options.clear()
        return true
    }

    override fun read(): RimeCalls.Raw {
        val preedit = if (code.isEmpty()) null else code.replace("ou", "o-u")
        val out = RimeCalls.Raw(commit, code, preedit, page, page >= 2, 0, choices(), choices().map { "" }, 8, if (code.isEmpty()) 0 else 24)
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
        check(files.keys.all { File(data.shared, it).lastModified() == RimeData.SOURCE_TIME * 1000 }, "each at the time Rime compiled them with")
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
