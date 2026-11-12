package com.vandam.ink

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.view.Gravity
import android.widget.TextView
import com.atlas.engine.SwipeTyping
import com.atlas.keyboard.FavouriteWords
import com.atlas.keyboard.R
import com.atlas.keyboard.Snippets
import org.json.JSONArray
import org.json.JSONObject

/**
 * Owns the Android file picker's result and keeps its document grant alive during the transfer. It moves the learned words,
 * or with [SNIPPETS] the snippets.
 */
class LearningTransferActivity : Activity() {
    private val exporting get() = intent.getBooleanExtra(EXPORTING, false)
    private val snippets get() = intent.getBooleanExtra(SNIPPETS, false)

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            gravity = Gravity.CENTER
            text = when {
                snippets -> if (exporting) "Exporting snippets…" else "Importing snippets…"
                else -> if (exporting) "Exporting words…" else "Importing words…"
            }
        })
        if (savedInstanceState != null) return
        val picker = Intent(if (exporting) Intent.ACTION_CREATE_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(if (exporting) "application/json" else "*/*")
        if (exporting) picker.putExtra(Intent.EXTRA_TITLE, if (snippets) "titankeys-snippets.json" else "titankeys-words.json")
        try {
            startActivityForResult(picker, PICK_DOCUMENT)
        } catch (_: ActivityNotFoundException) {
            complete(false, "Couldn't open the file picker.")
        }
    }

    @Deprecated("Android activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_DOCUMENT) return
        if (resultCode != RESULT_OK) {
            complete(false)
            return
        }
        val uri = data?.data
        if (uri == null) {
            complete(false, "Couldn't open that file.")
            return
        }
        if (snippets) {
            transferSnippets(uri)
            return
        }
        val typing = SwipeTyping.get(this)
        if (exporting) typing.exportConfig(uri) { error ->
            if (error != null) complete(false, error) else Thread { addFavourites(uri) }.start()
        } else Thread {
            val favourites = readFavourites(uri)
            runOnUiThread {
                typing.importConfig(uri) { error ->
                    if (error == null && !FavouriteWords.get(this).merge(favourites)) complete(false, getString(R.string.favourites_not_imported))
                    else complete(error == null, error)
                }
            }
        }.start()
    }

    /**
     * The favourites go in the words file, beside the words that the engine wrote. The engine's import skips them, and
     * [readFavourites] reads them back.
     */
    private fun addFavourites(uri: android.net.Uri) {
        val error = try {
            val file = contentResolver.openInputStream(uri)?.use { JSONObject(it.readBytes().decodeToString()) }
                ?: throw java.io.IOException("Could not open the export file")
            file.put(FavouriteWords.EXPORT_KEY, JSONArray(FavouriteWords.get(this).all().sorted()))
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(file.toString().toByteArray()) }
                ?: throw java.io.IOException("Could not open the export file")
            null
        } catch (error: Exception) {
            android.util.Log.w("Atlas", "Could not export the favourites", error)
            getString(R.string.favourites_not_exported)
        }
        runOnUiThread { complete(error == null, error) }
    }

    /** The favourites in a words file, or none: a file from before favourites, or one that can't be read, has none. */
    private fun readFavourites(uri: android.net.Uri): List<String> = try {
        val file = contentResolver.openInputStream(uri)?.use { JSONObject(it.readBytes().decodeToString()) }
        val list = file?.optJSONArray(FavouriteWords.EXPORT_KEY)
        if (list == null) emptyList() else (0 until list.length()).mapNotNull { list.optString(it).takeIf(String::isNotBlank) }
    } catch (error: Exception) {
        emptyList()
    }

    /** Snippets are small, but a file provider can still be slow, so this runs away from the main thread. */
    private fun transferSnippets(uri: android.net.Uri) {
        Thread {
            val error = try {
                if (exporting) Snippets.export(this, uri) else Snippets.import(this, uri)
                null
            } catch (error: Exception) {
                error.message ?: "Couldn't read that file."
            }
            runOnUiThread { complete(error == null, error) }
        }.start()
    }

    private fun complete(transferred: Boolean, error: String? = null) {
        val result = Bundle().apply { putString(ERROR, error) }
        intent.getParcelableExtra(RECEIVER, ResultReceiver::class.java)?.send(
            if (transferred) RESULT_OK else RESULT_CANCELED, result,
        )
        finish()
    }

    companion object {
        private const val PICK_DOCUMENT = 1
        private const val RECEIVER = "receiver"
        private const val EXPORTING = "exporting"
        private const val ERROR = "error"
        private const val SNIPPETS = "snippets"

        internal fun start(activity: Activity, exporting: Boolean, snippets: Boolean = false, complete: (Boolean, String?) -> Unit) {
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    complete(resultCode == RESULT_OK, resultData?.getString(ERROR))
                }
            }
            activity.startActivity(Intent(activity, LearningTransferActivity::class.java)
                .putExtra(EXPORTING, exporting).putExtra(SNIPPETS, snippets).putExtra(RECEIVER, receiver))
        }
    }
}
