package com.titankeys.rime

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Puts Rime's data where Rime can open it. Rime maps its tables as files, so they can't be read from inside the APK:
 * on the first use of a Chinese input method, the app copies assets/rime/ to files/rime/shared (40 MB), in the
 * background (docs/languages.md). The list of files and their SHA-256 is assets/rime.sha256, written by
 * native/rime/data.sh; a copy of it beside the data says which data is installed, so an update with new data copies
 * again, and nothing else does. The user's learned words, in files/rime/user, are kept.
 */
class RimeData(private val root: File) {
    val shared = File(root, "shared")
    val user = File(root, "user")
    private val marker = File(shared, ".installed")

    /** One line of the list: a file under assets/rime/ and its SHA-256. */
    data class Entry(val path: String, val sha256: String)

    /** Whether any data is installed, so Rime can open. */
    val installed get() = marker.isFile

    /** Rime's own snapshots of the learned words ([RimeBackup]), in its sync folder, named by [INSTALLATION]. */
    val snapshots get() = File(user, "sync/$INSTALLATION")

    /** Snapshots from a restored backup, which the next sync merges in. */
    val restored get() = File(user, "sync/$RESTORED")

    /**
     * Names this install [INSTALLATION] before Rime first opens, so its snapshots have a folder we know. Rime keeps an
     * installation.yaml that already exists.
     */
    fun prepareUser() {
        user.mkdirs()
        val file = File(user, "installation.yaml")
        if (!file.exists()) file.writeText("installation_id: \"$INSTALLATION\"\n")
    }

    /** Whether the data [manifest] lists is the data installed now. */
    fun current(manifest: String) = marker.isFile && marker.readText() == manifest

    /**
     * Copies every file in [manifest] from [open] (a path under assets/rime/), checking each one's SHA-256, into a new
     * folder that then takes the old one's place, so a copy cut short never leaves half the data. False if a file is
     * missing or wrong; the old data stays as it was.
     */
    fun install(manifest: String, open: (String) -> InputStream): Boolean {
        if (current(manifest)) return true
        val entries = parse(manifest) ?: return false
        val fresh = File(root, "shared.new")
        fresh.deleteRecursively()
        for (entry in entries) {
            val target = File(fresh, entry.path)
            if (!target.canonicalPath.startsWith(fresh.canonicalPath + File.separator)) return fail(fresh)
            target.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            val copied = runCatching {
                open(entry.path).use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            digest.update(buffer, 0, n)
                            output.write(buffer, 0, n)
                        }
                    }
                }
            }.isSuccess
            if (!copied || hex(digest.digest()) != entry.sha256) return fail(fresh)
        }
        File(fresh, ".installed").writeText(manifest)
        shared.deleteRecursively()
        if (!fresh.renameTo(shared)) return fail(fresh)
        // What an older version's Rime compiled on the phone, if anything, is stale now. Learned words stay.
        File(user, "build").deleteRecursively()
        user.mkdirs()
        return true
    }

    private fun fail(fresh: File): Boolean {
        fresh.deleteRecursively()
        return false
    }

    companion object {
        /** The asset that lists the data. */
        const val MANIFEST = "rime.sha256"
        /** The folder under assets/ that holds it. */
        const val ASSETS = "rime"
        /** This install's name in Rime's sync folder. */
        const val INSTALLATION = "titankeys"
        /** The sync folder's folder for a restored backup's snapshots. */
        const val RESTORED = "restored"

        /** sha256sum's lines, "<hash>  <path>", or null if any line isn't one. */
        fun parse(manifest: String): List<Entry>? {
            val lines = manifest.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return null
            return lines.map { line ->
                val hash = line.substringBefore("  ")
                val path = line.substringAfter("  ", "")
                if (hash.length != 64 || path.isEmpty() || path.startsWith("/") || ".." in path.split('/')) return null
                Entry(path, hash)
            }
        }

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}
