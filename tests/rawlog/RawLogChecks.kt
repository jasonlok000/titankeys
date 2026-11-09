package com.atlas.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch

private const val TEXT = InputType.TYPE_CLASS_TEXT
private const val NO_LEARNING = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
private const val REFERENCE = "com.titankeys.rawlog.reference"
private const val HEADER = """{"k":"header"}"""

fun main() {
    fieldRule()
    lineFormat()
    rotationAndCap()
    deleteAndExport()
    writerOrderAndFlush()
    writerOverflow()
    writerSkip()
    writerDiscardAndClose()
    println("Raw log checks passed")
}

private fun must(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private fun temporary() = Files.createTempDirectory("rawlog").toFile().also { it.deleteOnExit() }

private fun logFiles(directory: File) = directory.listFiles().orEmpty().sortedBy { it.name }

private fun fieldRule() {
    must(Field.logged(TEXT, 0, null), "An ordinary text field must be logged")
    must(Field.logged(TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, 0, "other.option"), "A message field must be logged")
    must(!Field.logged(TEXT, NO_LEARNING, null), "A field that asks for no learning must not be logged")
    must(!Field.logged(TEXT, NO_LEARNING, "other.option"), "Another private option must not make a field logged")
    must(!Field.logged(TEXT, NO_LEARNING, "com.titankeys.rawlog"), "Part of the reference option must not count")
    must(Field.logged(TEXT, NO_LEARNING, REFERENCE), "A reference field must be logged although it asks for no learning")
    must(Field.logged(TEXT, NO_LEARNING, "nm,$REFERENCE"), "The reference option can sit among others")
    must(Field.logged(TEXT, 0, REFERENCE), "A reference field that allows learning must be logged")
    for (password in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) {
        must(!Field.logged(TEXT or password, 0, null), "A password field must not be logged")
        must(!Field.logged(TEXT or password, 0, REFERENCE), "A password field must not be logged even as a reference")
        must(!Field.logged(TEXT or password, NO_LEARNING, REFERENCE), "A private password field must not be logged as a reference")
    }
    val numberPassword = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    must(!Field.logged(numberPassword, 0, REFERENCE), "A number password must not be logged even as a reference")
    for (address in listOf(InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)) {
        must(!Field.logged(TEXT or address, 0, null), "An address field must not be logged")
        must(!Field.logged(TEXT or address, NO_LEARNING, REFERENCE), "The reference option only overrides the request for no learning")
    }
    for (other in listOf(InputType.TYPE_NULL, InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME)) {
        must(!Field.logged(other, 0, null), "A field without text must not be logged")
        must(!Field.logged(other, NO_LEARNING, REFERENCE), "A field without text must not be logged as a reference")
    }
}

private fun lineFormat() {
    same(RawLogLines.motion(0, 89851452, 598f, 661.25f, 1, { 598f }, { 661.25f }, { 89851452 },
        1, { .54051f }, { .53714f }, { 89851452 }),
        """{"k":"m","a":0,"t":89851452,"rx":598,"ry":661.25,"p":[[598,661.25,89851452]],"s":[[0.54051,0.53714,89851452]]}""",
        "A motion line must match the recorder")
    same(RawLogLines.motion(3, 5, Float.NaN, Float.POSITIVE_INFINITY, 2, { it.toFloat() }, { -.5f }, { it.toLong() },
        1, { Float.NaN }, { Float.NaN }, { 5 }),
        """{"k":"m","a":3,"t":5,"rx":null,"ry":null,"p":[[0,-0.5,0],[1,-0.5,1]],"s":[[null,null,5]]}""",
        "A position that isn't finite must be null")
    same(RawLogLines.key(29, true, 1000, false, 193, 2),
        """{"k":"key","c":29,"d":true,"t":1000,"g":false,"meta":193,"repeat":2}""", "A key line must add meta state and repeat count")
    same(RawLogLines.swipe(12, 100, 200, false, 230),
        """{"k":"out","type":"swipe","n":12,"start":100,"end":200,"t":230}""", "A swipe line must match the recorder")
    same(RawLogLines.swipe(12, 100, 200, true, 230),
        """{"k":"out","type":"swipe","n":12,"start":100,"end":200,"deletes":true,"t":230}""", "A delete swipe must be marked")
    same(RawLogLines.tap("A", 200, 230), """{"k":"out","type":"tap","key":"A","end":200,"t":230}""", "A tap line must match the recorder")
    same(RawLogLines.recognised(listOf("hello", "hallo"), true, null, 9),
        """{"k":"rec","words":["hello","hallo"],"resumed":true,"t":9}""", "A recognition line must match the recorder")
    same(RawLogLines.recognised(listOf("hello"), false, listOf(RawLogScore("hello", 1.5f, 1f, .5f)), 9),
        """{"k":"rec","words":["hello"],"resumed":false,"scores":[["hello",1.5,1,0.5]],"t":9}""", "Scores must follow the words")
    same(RawLogLines.edit(EditCause.Swipe, 2, "hello ", 0, true, 7),
        """{"k":"text","t":7,"cause":"commit","via":"swipe","auto":false,"del":2,"ins":"hello ","aft":0,"ok":true}""", "A swipe is a commit")
    same(RawLogLines.edit(EditCause.Backspace, 1, "", 0, true, 7),
        """{"k":"text","t":7,"cause":"backspace","via":"letter","auto":false,"del":1,"ins":"","aft":0,"ok":true}""", "A backspace has its own cause")
    same(RawLogLines.edit(EditCause.Choose, 4, "word ", 1, false, 7),
        """{"k":"text","t":7,"cause":"choose","auto":false,"del":4,"ins":"word ","aft":1,"ok":false}""", "A pick has its own cause")
    for (automatic in listOf(EditCause.LookAlike, EditCause.FullStop, EditCause.DropSpace))
        must(automatic.automatic, "${automatic.name} must be marked as automatic")
    for (user in listOf(EditCause.Swipe, EditCause.Tap, EditCause.Key, EditCause.Symbol, EditCause.Space, EditCause.Newline,
            EditCause.Backspace, EditCause.BackspaceWord, EditCause.Choose, EditCause.Command))
        must(!user.automatic, "${user.name} must not be marked as automatic")
    same(RawLogLines.edit(EditCause.LookAlike, 5, "were ", 0, true, 7),
        """{"k":"text","t":7,"cause":"lookalike","auto":true,"del":5,"ins":"were ","aft":0,"ok":true}""", "A look-alike must be automatic")
    same(RawLogLines.picked(1, "word", listOf("work", "word"), 7),
        """{"k":"pick","t":7,"index":1,"word":"word","offered":["work","word"]}""", "A pick line must hold the words offered")
    same(RawLogLines.selection(5, 6, -1, 3, "hello", 7),
        """{"k":"sel","t":7,"start":5,"end":6,"cs":-1,"ce":3,"before":"hello"}""", "A selection line must hold the text before the cursor")
    same(RawLogLines.selection(5, 5, -1, -1, null, 7),
        """{"k":"sel","t":7,"start":5,"end":5,"cs":-1,"ce":-1,"before":null}""", "Text that can't be read must be null")
    same(RawLogLines.fieldStart(1, 2, true, false, 3, 4, "a", 7),
        """{"k":"start","t":7,"inputType":1,"imeOptions":2,"reference":true,"restarting":false,"selStart":3,"selEnd":4,"before":"a"}""",
        "A field start must hold the field type")
    same(RawLogLines.fieldEnd(7), """{"k":"end","t":7}""", "A field end must hold its time")
    same(RawLogLines.gap("queue", 5, 7), """{"k":"gap","t":7,"why":"queue","n":5}""", "A gap must say why")
    same(RawLogLines.mark("step", "two", 7), """{"k":"mark","t":7,"kind":"step","label":"two"}""", "A mark must hold its kind and label")
    same(RawLogLines.header(7, 8, "1.2.0", "Titan 2", "16", 90, linkedMapOf("liftOff" to true, "tapKeys" to "AI "),
        linkedMapOf("swipeKeys" to .6f, "settleMillis" to 30L, "evidence" to 3.0)),
        """{"k":"header","format":1,"t":7,"wall":8,"atlas":"1.2.0","model":"Titan 2","android":"16","slipMillis":90,""" +
            """"settings":{"liftOff":true,"tapKeys":"AI "},"tuning":{"swipeKeys":0.6,"settleMillis":30,"evidence":3}}""",
        "A header must hold what the recorder's does, with the settings and tuning values")
    same(RawLogLines.edit(EditCause.Key, 0, "a\"b\\c\nd\re\tf\u0001g", 0, true, 7),
        "{\"k\":\"text\",\"t\":7,\"cause\":\"commit\",\"via\":\"key\",\"auto\":false,\"del\":0," +
            "\"ins\":\"a\\\"b\\\\c\\nd\\re\\tf\\u0001g\",\"aft\":0,\"ok\":true}", "Special characters must be escaped")
    same(RawLogLines.mark("😀é", "\uD83D", 7), "{\"k\":\"mark\",\"t\":7,\"kind\":\"😀é\",\"label\":\"\\ud83d\"}",
        "Whole characters must stay as they are, and half of a pair must be escaped")
    same(RawLogLines.mark(null, null, 7), """{"k":"mark","t":7,"kind":null,"label":null}""", "A missing extra must be null")
    must('\n' !in RawLogLines.mark("a\nb", "\u2028\n", 7), "A line must never hold a line break")
}

private fun rotationAndCap() {
    val directory = temporary()
    val store = RawLogStore(directory, 1000, 200) { HEADER }
    fun line(index: Int) = """{"k":"end","t":$index}"""
    for (index in 0 until 300) {
        must(store.append(line(index)), "A line must be accepted")
        must(store.size() <= 1000, "The folder must stay within the cap: ${store.size()}")
        must(logFiles(directory).sumOf { it.length() } <= 1000, "The files must stay within the cap")
    }
    must(store.flush(), "The lines must flush")
    val files = logFiles(directory)
    must(files.size > 1, "The log must rotate")
    must(files.all { it.length() <= 200 }, "Every file must stay within its limit")
    must(files.all { it.readLines().first() == HEADER }, "Every file must start with a header")
    val kept = files.flatMap { it.readLines().drop(1) }
    same(kept.last(), line(299), "The newest line must be kept")
    same(kept, kept.indices.map { line(300 - kept.size + it) }, "Only the oldest lines may go, and all that remain must be in order")
    must(kept.size < 300, "The oldest lines must have gone")

    same(store.size(), files.sumOf { it.length() }, "The size must match the files")
    must(store.append(line(300)), "A line must be accepted")
    must(store.size() > files.sumOf { it.length() }, "The size must count lines that are waiting to be written")

    store.flush()
    val before = logFiles(directory).last().name
    val next = RawLogStore(directory, 1000, 200) { HEADER }
    must(next.append(line(301)), "A line must be accepted after a restart")
    next.flush()
    must(logFiles(directory).last().name > before, "Each process must start a new file after the old ones")
    same(logFiles(directory).last().readLines(), listOf(HEADER, line(301)), "A new file must start with a header")

    val rotating = temporary()
    val other = RawLogStore(rotating, 1000, 200) { HEADER }
    other.append(line(0))
    other.rotate()
    other.append(line(1))
    other.flush()
    same(logFiles(rotating).map { it.readLines() }, listOf(listOf(HEADER, line(0)), listOf(HEADER, line(1))), "Rotate must start a new file")

    val capped = temporary()
    must(!RawLogStore(capped, 1000, 200) { HEADER }.append("x".repeat(150)), "A line too long for a file must be refused")
    same(logFiles(capped).size, 0, "A refused line must not create a file")
}

private fun deleteAndExport() {
    val directory = temporary()
    val store = RawLogStore(directory, 100_000, 300) { HEADER }
    val lines = List(40) { """{"k":"end","t":$it}""" }
    lines.forEach { store.append(it) }
    File(directory, "notes.txt").writeText("not part of the log")

    val out = ByteArrayOutputStream()
    store.export(out)
    val exported = out.toString(Charsets.UTF_8).lines().filter { it.isNotEmpty() }
    same(exported.filter { it != HEADER }, lines, "Export must include lines still waiting, oldest first")
    same(exported.first(), HEADER, "Export must start with a header")
    val count = logFiles(directory).size
    store.append("""{"k":"end","t":99}""")
    store.flush()
    same(logFiles(directory).size, count + 1, "Lines after an export must go to a new file, so an export never changes during its copy")

    store.append("""{"k":"end","t":100}""")
    store.deleteAll()
    same(store.size(), 0L, "Delete all must empty the log")
    same(logFiles(directory).map { it.name }, listOf("notes.txt"), "Delete all must remove only the log's files")
    store.append("""{"k":"end","t":101}""")
    store.flush()
    same(logFiles(directory).last().readLines(), listOf(HEADER, """{"k":"end","t":101}"""), "The log must work after Delete all")
}

private fun waitFor(message: String, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 10_000
    while (!condition()) {
        must(System.currentTimeMillis() < deadline, message)
        Thread.sleep(5)
    }
}

private fun writerOrderAndFlush() {
    val directory = temporary()
    val store = RawLogStore(directory, 1 shl 24, 1 shl 20) { HEADER }
    val writer = RawLogWriter(store, 8192) { 1L }
    val lines = List(5000) { """{"k":"end","t":$it}""" }
    lines.forEach(writer::add)
    writer.flush()
    waitFor("A flush must reach the disk without closing") {
        logFiles(directory).flatMap { it.readLines() }.lastOrNull() == lines.last()
    }
    same(logFiles(directory).flatMap { it.readLines() }.filter { it != HEADER }, lines, "Lines must stay in order")
    writer.close()
    writer.join(5000)
}

/** While another thread holds the disk, the keyboard's side must carry on and a gap marker must take the place of what is lost. */
private fun writerOverflow() {
    val directory = temporary()
    val store = RawLogStore(directory, 1 shl 24, 1 shl 20) { HEADER }
    val holding = CountDownLatch(1)
    val release = CountDownLatch(1)
    val holder = Thread {
        synchronized(store) {
            holding.countDown()
            release.await()
        }
    }.apply { start() }
    holding.await()
    val writer = RawLogWriter(store, 4) { 42L }
    val started = System.nanoTime()
    repeat(20_000) { writer.add("""{"k":"end","t":$it}""") }
    val elapsed = (System.nanoTime() - started) / 1_000_000
    must(elapsed < 5000, "Adding lines must not wait for the disk: $elapsed ms")
    release.countDown()
    holder.join()
    waitFor("The queue must drain") { writer.backlog == 0 }
    writer.add("""{"k":"end","t":"after"}""")
    writer.close()
    writer.join(5000)

    val written = logFiles(directory).flatMap { it.readLines() }
    same(written.first(), HEADER, "The file must start with a header")
    val body = written.drop(1).dropLast(1)
    same(written.last(), """{"k":"end","t":"after"}""", "The next line must be written after the gap")
    must(body.last().contains("\"gap\""), "A gap marker must come before the next line")
    // A slot can open while the disk is held, so one run of lost lines can be split across several gap markers.
    var position = 0L
    for (line in body) {
        val gap = Regex(""""why":"queue","n":(\d+)""").find(line)
        if (gap != null) position += gap.groupValues[1].toLong()
        else same(line, """{"k":"end","t":$position}""".also { position++ }, "Each kept line must be at its place, after the lines the gaps stand for")
    }
    same(position, 20_000L, "Kept lines and the numbers in the gap markers must add up to every line")
    must(body.size < 20, "Only what fits in the queue can be kept: ${body.size}")
}

/** Input that may belong to the next field must leave one counted marker at its place, and none of its own lines. */
private fun writerSkip() {
    val directory = temporary()
    val store = RawLogStore(directory, 1 shl 24, 1 shl 20) { HEADER }
    val writer = RawLogWriter(store, 64) { 9L }
    val before = """{"k":"end","t":"before"}"""
    val after = """{"k":"end","t":"after"}"""
    writer.add(before)
    repeat(3) { writer.skip() }
    writer.add(after)
    writer.close()
    writer.join(5000)
    same(logFiles(directory).flatMap { it.readLines() }, listOf(HEADER, before, RawLogLines.gap("leaving", 3, 9), after),
        "One gap marker must stand for the lines left out, in their place")
}

private fun writerDiscardAndClose() {
    val directory = temporary()
    val store = RawLogStore(directory, 1 shl 24, 1 shl 20) { HEADER }
    val writer = RawLogWriter(store, 64) { 1L }
    synchronized(store) {
        repeat(10) { writer.add("""{"k":"end","t":$it}""") }
        writer.discard()
        store.deleteAll()
    }
    writer.add("""{"k":"end","t":"kept"}""")
    writer.close()
    writer.join(5000)
    must(writer.closed, "The writer must say that it closed")
    writer.add("""{"k":"end","t":"late"}""")
    val written = logFiles(directory).flatMap { it.readLines() }
    same(written, listOf(HEADER, """{"k":"end","t":"kept"}"""), "Delete all must remove waiting lines, and a closed writer must take no more")

    // The usual case: the writer is idle when Delete all runs, and the next line must not be lost.
    val idleDirectory = temporary()
    val idleStore = RawLogStore(idleDirectory, 1 shl 24, 1 shl 20) { HEADER }
    val idle = RawLogWriter(idleStore, 64) { 1L }
    idle.add("""{"k":"end","t":"old"}""")
    idle.flush()
    waitFor("A flush must reach the disk") { logFiles(idleDirectory).flatMap { it.readLines() }.contains("""{"k":"end","t":"old"}""") }
    Thread.sleep(50)
    synchronized(idleStore) {
        idle.discard()
        idleStore.deleteAll()
    }
    idle.add("""{"k":"end","t":"next"}""")
    idle.close()
    idle.join(5000)
    same(logFiles(idleDirectory).flatMap { it.readLines() }, listOf(HEADER, """{"k":"end","t":"next"}"""),
        "The first line after Delete all must be kept")
}
