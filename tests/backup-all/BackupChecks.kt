package com.atlas.keyboard

import com.atlas.keyboard.BackupFile.Merge
import com.atlas.keyboard.BackupFile.Picked

/** Back up everything (#65): a round trip of every part, old words and snippets files, a newer file, and how restore merges. */
fun main() {
    val failures = ArrayList<String>()
    fun same(actual: Any?, expected: Any?, message: String) {
        if (actual != expected) failures += "$message: expected $expected, got $actual"
    }
    fun problem(text: String): String? = try {
        BackupFile.read(text)
        null
    } catch (error: BackupFile.Problem) {
        error.message
    }

    // A value of every type in every store that's backed up, so each part goes out and comes back.
    val phone = LinkedHashMap<String, Map<String, Any?>>()
    for ((name, store) in BackupFile.STORES) {
        val values = linkedMapOf<String, Any?>(
            "on" to true, "count" to 7, "since" to 1_763_000_000_123L, "scale" to 0.3f, "choice" to "two",
            "words" to setOf("ain't", "naïve", "tab\there", "quote\"d", "emoji 🍑"),
        )
        if (store is BackupFile.Store.Backed) store.only?.forEach { values[it] = true }
        store.let { if (it is BackupFile.Store.Backed) it.lists.keys.forEach { key -> values.putIfAbsent(key, setOf("a")) } }
        phone[name] = values
    }
    phone["snippets"] = mapOf("list" to "{\"snippets\":[{\"shortcut\":\"addr\",\"text\":\"1 Main St\\nFlat 2\"}]}")
    phone["emoji"] = mapOf("recent" to "🍑 🔥 😏")
    val words = mapOf("format" to "physical-swipe-habits", "version" to 2.0,
        "words" to mapOf("words" to listOf(mapOf("word" to "titankeys", "spelling" to "TitanKeys", "uses" to 3.0, "day" to 20400.0, "weight" to 0.25))))
    val pairs = listOf(BackupFile.WordPair("good", "morning", 4, 1_763_000_000_000L), BackupFile.WordPair("i'm", "here", 1, 5L))
    val included = BackupFile.included(phone, debug = true)
    // Rime's snapshots of Chinese learned words: tab-separated, with its own header lines.
    val chinese = mapOf("jyut6ping3.userdb.txt" to "# Rime user dictionary\n#@/db_name\tjyut6ping3.userdb\nnei hou \t你好\tc=3 d=0.9 t=12\n",
        "luna_pinyin.userdb.txt" to "ni hao \t你好\tc=1 d=1 t=2\n")
    val contents = BackupFile.Contents("3.0.0", "debug", "2026-11-16", included, words, pairs, chinese)
    val text = BackupFile.write(contents)
    val back = BackupFile.read(text)
    same(back, Picked.Backup(contents), "Every part comes back as it went out")
    val noChinese = text.substringBefore("  \"chineseWords\"").trimEnd().removeSuffix(",") + "\n}\n"
    same((BackupFile.read(noChinese) as Picked.Backup).contents.chineseWords, emptyMap<String, String>(),
        "A backup from before Chinese has no Chinese words, and still restores")
    same(text.contains("\"format\": \"titankeys-backup\""), true, "The file names its format")
    same(text.contains("\"version\": 1"), true, "The file has its version")
    same(BackupFile.name(2026, 11, 6), "titankeys-backup-2026-11-06.json", "The file's name has the date")

    // What's in and what's out.
    for ((name, store) in BackupFile.STORES) when (store) {
        is BackupFile.Store.Backed -> same(included.containsKey(name), true, "$name is in the backup")
        is BackupFile.Store.Left -> same(included.containsKey(name), false, "$name is left out: ${store.why}")
    }
    same(included["connected"]?.keys, setOf("announcements"), "Only the News switch goes from connected, not Share swipes' ID")
    same(included["app"]?.keys, setOf("guide_shown"), "Only whether the guide was seen goes from app")
    same(BackupFile.included(phone, debug = false).containsKey("key_calibration"), false, "Key calibration goes only from TitanKeys Debug")
    same(BackupFile.restored(included, emptyMap(), debug = false).containsKey("key_calibration"), false,
        "Key calibration comes back only in TitanKeys Debug")
    same(BackupFile.included(mapOf("clipboard" to mapOf("hint_shown" to true)), debug = true), emptyMap<String, Any>(), "The clipboard never goes in")

    // A crafted or newer file: unknown stores, left-out stores and unknown types are skipped, never written.
    val crafted = mapOf("connected" to mapOf<String, Any>("contributor" to "someone", "announcements" to false),
        "clipboard" to mapOf<String, Any>("hint_shown" to true), "from_the_future" to mapOf<String, Any>("x" to true))
    same(BackupFile.restored(crafted, emptyMap(), debug = true), mapOf("connected" to mapOf("announcements" to false)),
        "Restore writes only what this version backs up")
    val futureType = text.replace("\"type\": \"boolean\"", "\"type\": \"colour\"")
    val skipped = (BackupFile.read(futureType) as Picked.Backup).contents.preferences
    same(skipped.values.none { values -> values.values.any { it is Boolean } }, true, "A setting of a type this version doesn't know is skipped")

    // Restore: settings take the file's values, lists merge with the file winning a clash, nothing here is deleted.
    val file = mapOf("typing_settings" to mapOf<String, Any>(
        "autocorrect" to false,
        "enter_apps" to setOf("com.whatsapp=key", "org.thoughtcrime.securesms=action"),
        "exact_apps" to setOf("com.termux"),
        "own_shorthand" to setOf("hw\thomework", "brb\tbe right back"),
        "recent_apps" to "a,b,c",
    ), "blocked_words" to mapOf<String, Any>("words" to setOf("ducking")),
        "autocorrect_learning" to mapOf<String, Any>("kept_once_words" to setOf("teh|20400", "yall|20401")),
        "snippets" to mapOf<String, Any>("list" to "{\"snippets\":[{\"shortcut\":\"addr\",\"text\":\"New St\"},{\"shortcut\":\"sig\",\"text\":\"J\"}]}"),
        "emoji" to mapOf<String, Any>("recent" to "🔥 😀"))
    val here = mapOf("typing_settings" to mapOf<String, Any?>(
        "autocorrect" to true, "swipe_typing" to false,
        "enter_apps" to setOf("com.whatsapp=action", "com.slack=key"),
        "exact_apps" to setOf("com.example"),
        "own_shorthand" to setOf("hw\thardware", "idk\tI don't know"),
        "recent_apps" to "d,a",
    ), "blocked_words" to mapOf<String, Any?>("words" to setOf("shirt")),
        "autocorrect_learning" to mapOf<String, Any?>("kept_once_words" to setOf("teh|20300", "gonna|20390")),
        "snippets" to mapOf<String, Any?>("list" to "{\"snippets\":[{\"shortcut\":\"addr\",\"text\":\"Old St\"},{\"shortcut\":\"home\",\"text\":\"H\"}]}"),
        "emoji" to mapOf<String, Any?>("recent" to "😀 🍑 😏"))
    val restored = BackupFile.restored(file, here, debug = false)
    val typing = restored["typing_settings"]!!
    same(typing["autocorrect"], false, "A setting takes the backup's value")
    same(typing.containsKey("swipe_typing"), false, "A setting the backup doesn't have stays as it is")
    same(typing["enter_apps"], setOf("com.whatsapp=key", "org.thoughtcrime.securesms=action", "com.slack=key"),
        "Enter key apps merge, the backup's send method winning")
    same(typing["exact_apps"], setOf("com.termux", "com.example"), "Exact typing apps merge")
    same(typing["own_shorthand"], setOf("hw\thomework", "brb\tbe right back", "idk\tI don't know"), "Shorthand merges, the backup's word winning")
    same(typing["recent_apps"], "a,b,c", "Recent apps: the backup's first, then the phone's, as many as the longer had")
    same(restored["blocked_words"]!!["words"], setOf("ducking", "shirt"), "Blocked words merge")
    same(restored["autocorrect_learning"]!!["kept_once_words"], setOf("teh|20400", "yall|20401", "gonna|20390"),
        "Autocorrect learning merges, the backup's day winning")
    same(restored["snippets"]!!["list"].toString().let { (com.atlas.connected.Json.parse(it) as Map<*, *>)["snippets"] },
        listOf(mapOf("shortcut" to "addr", "text" to "New St"), mapOf("shortcut" to "home", "text" to "H"), mapOf("shortcut" to "sig", "text" to "J")),
        "Snippets merge, the backup's text winning")
    same(restored["emoji"]!!["recent"], "🔥 😀 🍑", "Emoji recents: the backup's first")
    same(BackupFile.merge(Merge.UNION, null, setOf("x")), setOf("x"), "A list the phone doesn't have takes the backup's")
    same(BackupFile.merge(Merge.SNIPPETS, "not json", "{\"snippets\":[]}"), "{\"snippets\":[]}", "A broken list here takes the backup's")

    // Export words' and Export snippets' files from before 3.0 still restore.
    same(BackupFile.read("{\n  \"format\": \"physical-swipe-habits\",\n  \"version\": 2,\n  \"words\": {\"words\": []},\n  \"favourites\": [\"x\"]\n}\n"),
        Picked.OldWords, "An Export words file restores as words")
    same(BackupFile.read("{\n  \"snippets\": [\n    {\"shortcut\": \"addr\", \"text\": \"1 Main St\"}\n  ]\n}"), Picked.OldSnippets,
        "An Export snippets file restores as snippets")

    // A newer file says so instead of half-restoring; anything else says what's wrong.
    same(problem(text.replace("\"version\": 1,", "\"version\": 2,")), BackupFile.TOO_NEW, "A backup from a newer TitanKeys says so")
    same(problem(text.substring(0, text.length / 2)), BackupFile.DAMAGED, "A backup cut short is damaged")
    same(problem(text.replace("\"wordPairs\": [", "\"wordPairs\": [\"nope\", ")), BackupFile.DAMAGED, "A backup with a broken part is damaged")
    same(problem("{\"format\": \"something-else\"}"), BackupFile.NOT_A_BACKUP, "Another app's JSON is not a backup")
    same(problem("\u0089PNG"), BackupFile.NOT_A_BACKUP, "A picture is not a backup")

    // The crash report: where it happened, never a message, which could hold typed text.
    val crash = IllegalStateException("secret typed words", IllegalArgumentException("more typed words"))
    val report = CrashReport.stack(crash)
    same(report.contains("typed words"), false, "A crash report has no exception message")
    same(report.startsWith("java.lang.IllegalStateException\n\tat "), true, "A crash report names the exception and where")
    same(report.contains("Caused by: java.lang.IllegalArgumentException"), true, "A crash report names the cause")
    val trace = sequenceOf("----- pid 1 -----", "\"Signal Catcher\" daemon", "  at x", "", "\"main\" prio=5 tid=1 Sleeping",
        "  at java.lang.Thread.sleep", "  at com.atlas.keyboard.AtlasIme.onKey", "", "\"other\"", "  at y")
    same(CrashReport.mainThread(trace), "\"main\" prio=5 tid=1 Sleeping\n  at java.lang.Thread.sleep\n  at com.atlas.keyboard.AtlasIme.onKey",
        "A freeze's report is the main thread's stack")

    if (failures.isNotEmpty()) {
        failures.forEach(System.err::println)
        System.exit(1)
    }
    println("Backup everything checks passed")
}
