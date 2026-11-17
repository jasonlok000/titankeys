package com.titankeys.keyboard

import com.titankeys.autocorrect.RecentWords
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

/**
 * The owner's memory bug (2026-11-17): TitanKeys Debug ran out of its 256 MB about seven minutes into the phone's languages
 * batch, which switches through every input method. Offline: corrector builds go one at a time, the newest only
 * ([LatestOnly], as [LanguageCorrection] builds them now; a thread each built them all at once), and three passes through every
 * input method with a word list, each loaded as Languages and LanguageCorrection load it and only the current one kept, keep
 * the heap flat.
 */
fun main(args: Array<String>) {
    val folder = File(args.firstOrNull() ?: "android/assets/im")
    var failed = 0
    fun check(what: String, ok: Boolean, detail: () -> String = { "" }) {
        if (!ok) {
            failed++
            println("FAIL $what ${detail()}")
        }
    }

    // One at a time, the newest only: five switches while a build runs start one more build, the last one.
    val builds = LatestOnly("test")
    val running = AtomicInteger()
    var most = 0
    val ran = mutableListOf<Int>()
    val first = CountDownLatch(1)
    val started = CountDownLatch(1)
    val done = CountDownLatch(2)
    fun job(n: Int): () -> Unit = {
        val now = running.incrementAndGet()
        synchronized(ran) { most = maxOf(most, now); ran += n }
        if (n == 1) {
            started.countDown()
            first.await()
        }
        running.decrementAndGet()
        done.countDown()
    }
    builds.submit(job(1))
    // The others come while the first builds, as switches come while a corrector is made.
    check("the first build starts", started.await(10, java.util.concurrent.TimeUnit.SECONDS))
    for (n in 2..5) builds.submit(job(n))
    first.countDown()
    check("the builds end", done.await(10, java.util.concurrent.TimeUnit.SECONDS))
    Thread.sleep(100)
    check("one build at a time", most == 1) { "$most at once" }
    check("the first and the newest run, the ones overtaken never", synchronized(ran) { ran.toList() } == listOf(1, 5)) { "$ran" }

    // Three passes through every input method with a word list, as the phone's batch does, only the current one kept.
    val methods = folder.listFiles { file -> File(file, "im.json").isFile }.orEmpty().sortedBy { it.name }.mapNotNull { dir ->
        runCatching { InputMethod.parse(File(dir, "im.json").readText()) }.getOrNull()?.takeIf { LanguageCorrections.corrects(it) }
    }
    var current: Any? = null
    val kept = mutableListOf<Long>()
    for (pass in 1..3) {
        for (method in methods) {
            val dir = File(folder, method.id)
            val variant = method.variants.firstOrNull()
            val list = File(dir, variant?.words ?: method.words!!).useLines { WordList.read(it, method.javaLocale) }
            val table = File(dir, if (variant != null) "pairs-${variant.id}.bin" else "pairs.bin").takeIf { it.isFile }
                ?.inputStream()?.use { NextWords.readBinary(it, method.id in NextWords.TURKIC) }
            val corrector = LanguageCorrections.corrector(method, list, { false }, HashSet(), RecentWords(),
                pairs = table?.let(LanguageCorrections::pairs))
            // The switch: the last input method's list, table and corrector go.
            current = listOf(list, table, corrector)
        }
        current = null
        System.gc(); Thread.sleep(200); System.gc()
        val rt = Runtime.getRuntime()
        kept += (rt.totalMemory() - rt.freeMemory()) / (1 shl 20)
        println("pass $pass: ${methods.size} input methods, ${kept.last()} MB kept after GC")
    }
    check("the heap stays flat across the passes", kept.last() - kept.first() <= 16) { "$kept MB" }
    if (current != null) println(current)
    if (failed > 0) {
        println("$failed memory checks failed")
        exitProcess(1)
    }
    println("Memory checks passed")
}
