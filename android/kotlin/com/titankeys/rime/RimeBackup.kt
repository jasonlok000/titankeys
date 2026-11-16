package com.titankeys.rime

import java.io.File

/**
 * Chinese learned words in Back up everything (#65). Rime keeps them in a database per dictionary (jyut6ping3 for Yale and
 * Jyutping, luna_pinyin for Pinyin), and backs them up itself: a sync writes each one as a text snapshot,
 * `<dictionary>.userdb.txt`, and merges in any other snapshot it finds. So the backup holds the snapshots, and a restore puts
 * them where a sync merges them in, as Rime merges its own: words from both, the counts combined. Restore restarts TitanKeys;
 * when a Chinese input method next starts, [RimeEngine] runs that sync for the dictionaries it can ([mergeable]).
 *
 * Nothing here needs Android, so tests/rime checks it on the JVM; the keyboard's Rime is reached through [RimeEngine].
 */
object RimeBackup {
    /** A snapshot's name: one Rime dictionary's, nothing that could leave the folder. */
    private val NAME = Regex("[a-z0-9_]{1,64}\\.userdb\\.txt")

    /** A snapshot is a few hundred kilobytes at most. Anything far bigger isn't one. */
    private const val MAX_CHARS = 16 * 1024 * 1024

    fun valid(name: String) = NAME.matches(name)

    /** The snapshots Rime last wrote, by name. */
    fun snapshots(data: RimeData): Map<String, String> =
        data.snapshots.listFiles().orEmpty().filter { it.isFile && valid(it.name) }.sortedBy { it.name }
            .associate { it.name to it.readText() }

    /**
     * Brings the snapshots up to date and returns them: through the keyboard's Rime if a Chinese input method has it open,
     * else by opening Rime for a moment, if its data was ever installed. With no Chinese ever typed, there's nothing.
     */
    fun export(dataDir: File, rime: RimeCalls = RimeNative): Map<String, String> {
        val data = RimeData(dataDir)
        if (data.installed) RimeEngine.sync(rime, data)
        return snapshots(data)
    }

    /** Puts a backup's snapshots where the next sync merges them in. Names that aren't a snapshot's are skipped. */
    fun restore(dataDir: File, words: Map<String, String>) {
        val data = RimeData(dataDir)
        val kept = words.filter { (name, text) -> valid(name) && text.length <= MAX_CHARS }
        if (kept.isEmpty()) return
        data.restored.mkdirs()
        for ((name, text) in kept) File(data.restored, name).writeText(text)
    }

    /**
     * A restored backup's snapshots that a sync can merge in now: Rime merges a snapshot only into a dictionary the phone
     * already has (`<dictionary>.userdb` in the user folder), which appears once an input method using it is first used. The
     * others wait for theirs.
     */
    fun mergeable(data: RimeData): List<File> = data.restored.listFiles().orEmpty()
        .filter { valid(it.name) && File(data.user, it.name.removeSuffix(".txt")).exists() }

    /** Whether a restored backup's snapshot waits to be merged in, and can be now. */
    fun pending(data: RimeData) = mergeable(data).isNotEmpty()
}
