package com.vandam.ink

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.ResultReceiver
import android.view.Gravity
import android.widget.TextView
import com.titankeys.engine.SwipeTyping
import com.titankeys.engine.TypingSettings
import com.titankeys.engine.WordPairBackup
import com.titankeys.keyboard.AppBuild
import com.titankeys.keyboard.BackupFile
import com.titankeys.keyboard.FavouriteWords
import com.titankeys.keyboard.FixedScale
import com.titankeys.keyboard.FreshStartActivity
import com.titankeys.keyboard.HapticsLab
import com.titankeys.keyboard.R
import com.titankeys.keyboard.Snippets
import com.titankeys.connected.Json
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.CountDownLatch

/**
 * Settings > Your words > Back up everything and Restore (#65). Owns the Android file picker's result and keeps its document
 * grant alive while it works. What goes in and how a restore merges are in [BackupFile].
 *
 * Restore also takes Export words' and Export snippets' files from before 3.0, as Import words and Import snippets did. A whole
 * backup ends by restarting TitanKeys' process, as Reset literally everything does, so the keyboard, the engine and the screens
 * read the restored files fresh: never a force-stop, which would turn the Accessibility service off.
 */
class BackupActivity : Activity() {
    /** The same sizes at any display size or font size (FixedScale). */
    override fun attachBaseContext(base: Context) = super.attachBaseContext(FixedScale.wrap(base))

    private val backingUp get() = intent.getBooleanExtra(BACKING_UP, false)
    private val typing by lazy { SwipeTyping.get(this) }
    private val debug get() = HapticsLab.available(this)

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            gravity = Gravity.CENTER
            // Shown behind the file picker while it works.
            text = if (backingUp) "Backing up…" else "Restoring…"
        })
        if (savedInstanceState != null) return
        val picker = Intent(if (backingUp) Intent.ACTION_CREATE_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(if (backingUp) "application/json" else "*/*")
        if (backingUp) LocalDate.now().let { picker.putExtra(Intent.EXTRA_TITLE, BackupFile.name(it.year, it.monthValue, it.dayOfMonth)) }
        try {
            startActivityForResult(picker, PICK_DOCUMENT)
        } catch (_: ActivityNotFoundException) {
            complete(null, "Couldn't open the file picker.")
        }
    }

    @Deprecated("Android activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_DOCUMENT) return
        if (resultCode != RESULT_OK) return complete(null)
        val uri = data?.data ?: return complete(null, "Couldn't open that file.")
        Thread {
            try {
                if (backingUp) backUp(uri) else restore(uri)
            } catch (error: BackupFile.Problem) {
                runOnUiThread { complete(null, error.message) }
            } catch (error: Exception) {
                android.util.Log.w("TitanKeys", "Backup or restore failed", error)
                runOnUiThread { complete(null, if (backingUp) "Couldn't save the backup. Try again." else BackupFile.DAMAGED) }
            }
        }.start()
    }

    // Back up everything

    private fun backUp(uri: Uri) {
        val words = engineWords()
        val preferences = BackupFile.STORES.keys.associateWith { getSharedPreferences(it, MODE_PRIVATE).all }
        val pairs = WordPairBackup.all(this).map { BackupFile.WordPair(it.first, it.next, it.count, it.lastUsed) }
        val contents = BackupFile.Contents(version(), AppBuild.kind(this), LocalDate.now().toString(),
            BackupFile.included(preferences, debug), words, pairs)
        val output = contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the backup file")
        output.use { it.write(BackupFile.write(contents).toByteArray()) }
        runOnUiThread { complete(DONE_BACKUP) }
    }

    /** The engine's own words export, through a file of ours, so the words go out exactly as Export words wrote them. */
    @Suppress("UNCHECKED_CAST")
    private fun engineWords(): Map<String, Any?> {
        val file = File(cacheDir, WORDS_FILE)
        try {
            engine { done -> typing.exportConfig(Uri.fromFile(file), done) }?.let { throw BackupFile.Problem(it) }
            return Json.parse(file.readText()) as Map<String, Any?>
        } finally {
            file.delete()
        }
    }

    // Restore

    private fun restore(uri: Uri) {
        val text = read(uri)
        when (val picked = BackupFile.read(text)) {
            BackupFile.Picked.OldWords -> restoreOldWords(uri, text)
            BackupFile.Picked.OldSnippets -> {
                Snippets.import(this, uri)
                runOnUiThread { complete(DONE_SNIPPETS) }
            }
            is BackupFile.Picked.Backup -> restoreAll(picked.contents)
        }
    }

    private fun read(uri: Uri): String {
        val input = contentResolver.openInputStream(uri) ?: throw IOException("Could not open the file")
        val bytes = input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > BackupFile.MAX_BYTES) throw BackupFile.Problem(BackupFile.NOT_A_BACKUP)
            }
            out.toByteArray()
        }
        return bytes.decodeToString()
    }

    /** Export words' file: the words through the engine, and the favourites beside them, as Import words did. */
    private fun restoreOldWords(uri: Uri, text: String) {
        val favourites = try {
            (Json.parse(text) as Map<*, *>)[FavouriteWords.EXPORT_KEY] as? List<*>
        } catch (_: Exception) {
            null
        }.orEmpty().mapNotNull { (it as? String)?.takeIf(String::isNotBlank) }
        engine { done -> typing.importConfig(uri, done) }?.let { throw BackupFile.Problem(it) }
        if (!FavouriteWords.get(this).merge(favourites)) throw BackupFile.Problem(getString(R.string.favourites_not_imported))
        runOnUiThread { complete(DONE_WORDS) }
    }

    /**
     * The words first, through the engine's own import, as they're the part most likely to fail; then the settings and lists;
     * then the word pairs; then Contacts' permission if the backup had the names on; then the restart.
     */
    private fun restoreAll(contents: BackupFile.Contents) {
        contents.words?.let { words ->
            val file = File(cacheDir, WORDS_FILE)
            try {
                file.writeText(BackupFile.jsonText(words))
                engine { done -> typing.importConfig(Uri.fromFile(file), done) }?.let { throw BackupFile.Problem(it) }
            } finally {
                file.delete()
            }
        }
        val phone = BackupFile.STORES.keys.associateWith { getSharedPreferences(it, MODE_PRIVATE).all }
        val restored = BackupFile.restored(contents.preferences, phone, debug)
        // The Contacts permission belongs to this phone, so the switch comes back only once Android allows it again.
        val wantsNames = restored[TYPING]?.get(TypingSettings.CONTACT_NAMES) == true && !contactsAllowed()
        for ((name, values) in restored) {
            val editor = getSharedPreferences(name, MODE_PRIVATE).edit()
            for ((key, value) in values) when (value) {
                is Boolean -> editor.putBoolean(key, if (name == TYPING && key == TypingSettings.CONTACT_NAMES && wantsNames) false else value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
            }
            // commit(), so it's on disk before the restart.
            if (!editor.commit()) throw IOException("Could not write $name")
        }
        WordPairBackup.put(this, contents.pairs.map { WordPairBackup.Pair(it.first, it.next, it.count, it.lastUsed) })
        runOnUiThread { if (wantsNames) requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), ASK_CONTACTS) else restart() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != ASK_CONTACTS) return
        // commit(), not TypingSettings.contactNames' apply(): the restart ends this process at once, and could lose the write.
        TypingSettings.settings(this).edit()
            .putBoolean(TypingSettings.CONTACT_NAMES, grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED).commit()
        restart()
    }

    private fun contactsAllowed() = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Ends TitanKeys' process and opens Restore's result page in a new one, as Reset literally everything does (FreshStart). */
    private fun restart() {
        startActivity(Intent(this, FreshStartActivity::class.java)
            .putExtra(FreshStartActivity.PID, Process.myPid())
            .putExtra(FreshStartActivity.PAGE, RESTORED_PAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        finish()
    }

    /** Runs an engine operation and waits for it, off the main thread. Returns its error, or null. */
    private fun engine(start: (SwipeTyping.OperationCallback) -> Unit): String? {
        val done = CountDownLatch(1)
        var failure: String? = null
        start(SwipeTyping.OperationCallback { error -> failure = error; done.countDown() })
        done.await()
        return failure
    }

    private fun version() = packageManager.getPackageInfo(packageName, 0).versionName ?: ""

    private fun complete(done: String?, error: String? = null) {
        val result = Bundle().apply { putString(ERROR, error); putString(DONE, done) }
        intent.getParcelableExtra(RECEIVER, ResultReceiver::class.java)?.send(if (done != null) RESULT_OK else RESULT_CANCELED, result)
        finish()
    }

    companion object {
        private const val PICK_DOCUMENT = 1
        private const val ASK_CONTACTS = 2
        private const val RECEIVER = "receiver"
        private const val BACKING_UP = "backing_up"
        private const val ERROR = "error"
        private const val DONE = "done"
        private const val TYPING = "typing_settings"
        private const val WORDS_FILE = "backup-words.json"
        const val DONE_BACKUP = "backup"
        const val DONE_WORDS = "words"
        const val DONE_SNIPPETS = "snippets"
        const val RESTORED_PAGE = "/settings/data/restored"

        /** [complete] gets what was done ([DONE_BACKUP], [DONE_WORDS] or [DONE_SNIPPETS]), or null and an error if any. */
        internal fun start(activity: Activity, backingUp: Boolean, complete: (String?, String?) -> Unit) {
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    complete(resultData?.getString(DONE), resultData?.getString(ERROR))
                }
            }
            activity.startActivity(Intent(activity, BackupActivity::class.java)
                .putExtra(BACKING_UP, backingUp).putExtra(RECEIVER, receiver))
        }
    }
}
