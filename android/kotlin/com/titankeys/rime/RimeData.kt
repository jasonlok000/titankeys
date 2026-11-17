package com.titankeys.rime

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Puts Rime's data where Rime can open it. Rime maps its tables as files, so they can't be read from inside the APK:
 * on the first use of a Chinese input method, the app copies what the input methods that are on need from assets/rime/
 * to files/engines/rime/shared, in the background (docs/languages.md; docs/studies/3.0-storage.md). The list of files and
 * their SHA-256 is assets/rime.sha256, and which files each schema needs is assets/rime.schemas, both written by
 * native/rime/data.sh; the files installed and their SHA-256, beside the data, say what's there, so an update copies only
 * the files that changed, and nothing else does. The user's learned words, in files/engines/rime/user, are kept.
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

    /** The files installed now, from the marker: each one's path and SHA-256. */
    private fun installedFiles(): MutableMap<String, String> =
        (if (marker.isFile) parse(marker.readText()) else null).orEmpty().associateTo(LinkedHashMap()) { it.path to it.sha256 }

    /** Records [files] as installed, whole or not at all: a new marker, then swapped in. */
    private fun record(files: Map<String, String>) {
        val fresh = File(shared, ".installed.new")
        fresh.writeText(files.entries.joinToString("") { (path, hash) -> "$hash  $path\n" })
        if (!fresh.renameTo(marker)) {
            marker.delete()
            fresh.renameTo(marker)
        }
    }

    /**
     * Puts the files of [manifest] that are wanted in place, from [open] (a path under the assets' folder), file by file
     * (#85 storage, docs/studies/3.0-storage.md):
     * - wanted: every file [groups] gives to no schema, and the files of the [schemas] in use (Rime's input methods that
     *   are on). Without [groups], every file;
     * - a file whose SHA-256 is already installed stays as it is;
     * - any other is copied beside its place, checked, and swapped in, then recorded, so a copy cut short never leaves a
     *   half file, and an update holds no more than the data and one file at once, never two whole copies;
     * - a file no longer wanted, or no longer in the data, goes.
     * If anything changed, what an older version's Rime compiled on the phone goes too. Learned words stay. False if a
     * file is missing or wrong: what was installed before it stays.
     */
    fun install(manifest: String, groups: Map<String, Set<String>>? = null, schemas: Set<String> = emptySet(),
                open: (String) -> InputStream): Boolean {
        val entries = parse(manifest) ?: return false
        val grouped = groups?.values?.flatten()?.toSet().orEmpty()
        val chosen = schemas.flatMap { groups?.get(it).orEmpty() }.toSet()
        val wanted = entries.filter { it.path !in grouped || it.path in chosen }
        // The whole-folder copy of the versions before: a half copy left by one goes.
        File(root, "shared.new").deleteRecursively()
        shared.mkdirs()
        val installed = installedFiles()
        installed.keys.retainAll { File(shared, it).isFile }
        var changed = false
        // What an older version's Rime compiled from the data goes before the first file changes, not after the last: an
        // update cut short (a later file fails, or the process dies) mustn't leave it to outlive the data it came from.
        fun changing() {
            if (!changed) File(user, "build").deleteRecursively()
            changed = true
        }
        for (entry in wanted) {
            if (installed[entry.path] == entry.sha256) continue
            val target = File(shared, entry.path)
            if (!target.canonicalPath.startsWith(shared.canonicalPath + File.separator)) return false
            target.parentFile?.mkdirs()
            val fresh = File(target.parentFile, ".${target.name}.new")
            val digest = MessageDigest.getInstance("SHA-256")
            val copied = runCatching {
                open(entry.path).use { input ->
                    fresh.outputStream().use { output ->
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
            if (!copied || hex(digest.digest()) != entry.sha256) {
                fresh.delete()
                return false
            }
            changing()
            if (!fresh.renameTo(target)) {
                fresh.delete()
                return false
            }
            installed[entry.path] = entry.sha256
            record(installed)
        }
        val keep = wanted.map { it.path }.toSet()
        for (path in installed.keys.filter { it !in keep }) {
            changing()
            File(shared, path).delete()
            installed.remove(path)
            record(installed)
        }
        user.mkdirs()
        return true
    }

    companion object {
        /** The asset that lists the data. */
        const val MANIFEST = "rime.sha256"
        /** The asset that says which files each schema needs: "schema<TAB>path" lines. A file in none is everyone's. */
        const val SCHEMAS = "rime.schemas"

        /** [SCHEMAS]'s lines, as each schema's files. */
        fun groups(text: String): Map<String, Set<String>> = text.lines().filter { '\t' in it }
            .groupBy({ it.substringBefore('\t') }, { it.substringAfter('\t') }).mapValues { it.value.toSet() }
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
