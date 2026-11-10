package com.atlas.connected

import android.content.Context
import android.os.Build
import android.util.Log
import com.atlas.engine.Swipe
import com.atlas.engine.SwipeTyping
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Data contributions: with the user's consent only, labelled swipe examples go to the TitanKeys project to train the
 * swipe models. Off until the user agrees on the disclosure screen. Examples wait in a capped queue on the phone, then
 * go in sealed batches on Wi-Fi to a write-only server that keeps only the sealed files. Stopping deletes what still
 * waits; "Delete my contributions" also asks the server to delete everything sent with this contributor ID.
 */
object Contributions {
    private const val TAG = "TitanKeysContribute"
    private const val ENABLED = "contribute"
    private const val OFFER = "contribute_offer"
    private const val CONTRIBUTOR = "contributor"
    private const val PENDING_DELETE = "contributor_to_delete"
    private const val SENT = "contributions_sent"
    private const val SENT_AT = "contributions_sent_at"
    /** About 1000 swipes. */
    const val QUEUE_CAP_BYTES = 4L * 1024 * 1024
    /** Examples in one batch, before gzip. The server takes at most 320 KB, sealed. */
    private const val BATCH_BYTES = 256 * 1024
    /** A batch always holds at least one example, so no example may be larger than a batch. Real ones are a few KB. */
    private const val MAX_EXAMPLE_BYTES = 128 * 1024
    private const val BATCHES_PER_RUN = 16

    private var queue: ContributionQueue? = null

    internal fun queue(context: Context): ContributionQueue = synchronized(this) {
        queue ?: ContributionQueue(File(context.applicationContext.noBackupFilesDir, "contributions"), QUEUE_CAP_BYTES).also { queue = it }
    }

    fun enabled(context: Context) = Connected.preferences(context).getBoolean(ENABLED, false)

    /** Turns contributions on after the user agreed, or off. Off deletes the examples that still wait on the phone. */
    fun enabled(context: Context, on: Boolean) {
        val preferences = Connected.preferences(context)
        val edit = preferences.edit().putBoolean(ENABLED, on).putString(OFFER, ANSWERED)
        if (on && preferences.getString(CONTRIBUTOR, null) == null) edit.putString(CONTRIBUTOR, UUID.randomUUID().toString())
        edit.apply()
        TouchSizes.recording = on
        if (!on) {
            queue(context).clear()
            TouchSizes.clear()
        }
        Connected.schedule(context)
    }

    /** The guide offers contributions at its end until the user answers. This marks the offer as due for the app too. */
    fun guideEnded(context: Context) {
        val preferences = Connected.preferences(context)
        if (preferences.getString(OFFER, null) == null) preferences.edit().putString(OFFER, DUE).apply()
    }

    fun offerDue(context: Context) = Connected.preferences(context).getString(OFFER, null) == DUE

    /** The user has neither agreed nor said no yet, so the guide's last slide offers it. */
    fun unanswered(context: Context) = !enabled(context) && Connected.preferences(context).getString(OFFER, null) != ANSWERED

    /** The disclosure page, with "I agree" and "No thanks". */
    const val PAGE = "/settings/contribute"

    /** "No thanks": the offer does not come back. Settings still has the switch. */
    fun declined(context: Context) {
        Connected.preferences(context).edit().putString(OFFER, ANSWERED).apply()
    }

    fun queued(context: Context) = queue(context).count()
    fun queuedBytes(context: Context) = queue(context).bytes()
    fun sent(context: Context) = Connected.preferences(context).getInt(SENT, 0)
    fun sentAt(context: Context) = Connected.preferences(context).getLong(SENT_AT, 0)
    /** The random contributor ID, shown in Settings so that the user can still ask for deletion after removing the app. */
    fun contributorId(context: Context): String? = Connected.preferences(context).getString(CONTRIBUTOR, null)
    fun deletionPending(context: Context) = Connected.preferences(context).getString(PENDING_DELETE, null) != null

    /** Deletes the examples that wait on the phone, and nothing else. */
    fun deleteQueued(context: Context) = queue(context).clear()

    /**
     * "Delete my contributions": deletes what waits on the phone, and asks the server to delete everything sent with
     * this contributor ID. Further contributions get a new ID. The server request is tried again until it succeeds.
     */
    fun deleteAll(context: Context) {
        val preferences = Connected.preferences(context)
        queue(context).clear()
        val id = preferences.getString(CONTRIBUTOR, null)
        val edit = preferences.edit().putInt(SENT, 0).putLong(SENT_AT, 0)
        // Asked even if nothing seems sent: a batch may have arrived just before the phone lost its answer.
        if (id != null && Connected.CONTRIBUTIONS_URL.isNotEmpty()) edit.putString(PENDING_DELETE, id)
        edit.putString(CONTRIBUTOR, if (enabled(context)) UUID.randomUUID().toString() else null).apply()
        Connected.schedule(context)
    }

    /**
     * A swipe's word was confirmed in a field that allows learning. If contributions are on and the word is in the
     * dictionary, its example joins the queue. Password, email, web address and private fields never get here.
     */
    fun record(typing: SwipeTyping, swipe: Swipe, word: String, how: String, prediction: SwipeTyping.Prediction?) {
        val context = Connected.context ?: return
        if (!enabled(context) || prediction == null) return
        if (!Example.wordAllowed(word, inDictionary = true)) return
        val app = context.applicationContext
        val sizes = TouchSizes.of(swipe)
        typing.contributionRows(word, prediction) { rows, kept ->
            try {
                val example = Example(how, word, swipe.x.copyOf(), swipe.y.copyOf(), swipe.time.copyOf(), sizes, rows.toList(), kept,
                    Connected.version(app), models(app), Build.MODEL ?: "", Connected.build(app))
                val line = example.line()
                if (enabled(app) && line.length <= MAX_EXAMPLE_BYTES) queue(app).add(line)
            } catch (error: Exception) {
                Log.w(TAG, "Example skipped: ${error.javaClass.simpleName}")
            }
        }
    }

    /** Sends the queue in sealed batches. Runs only from the Wi-Fi job. */
    fun upload(context: Context): Boolean {
        if (!enabled(context) || Connected.CONTRIBUTIONS_URL.isEmpty()) return true
        val preferences = Connected.preferences(context)
        val id = preferences.getString(CONTRIBUTOR, null) ?: return true
        val queue = queue(context)
        repeat(BATCHES_PER_RUN) {
            val lines = queue.oldest(BATCH_BYTES)
            if (lines.isEmpty()) return true
            val sealed = Seal.seal(Example.batch(id, lines).toByteArray(Charsets.UTF_8), Connected.key(Connected.CONTRIBUTION_KEY))
            val connection = Connected.open("${Connected.CONTRIBUTIONS_URL}/v1/batch", "POST")
            try {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.setRequestProperty("X-Contributor", folder(id))
                connection.setFixedLengthStreamingMode(sealed.size)
                connection.outputStream.use { it.write(sealed) }
                // Busy (429) or full (503): stop, keep the queue, and try at the next run.
                if (!Connected.ok(connection)) {
                    Log.w(TAG, "The server answered ${connection.responseCode}")
                    return false
                }
            } catch (error: Exception) {
                Log.w(TAG, "Upload failed: ${error.javaClass.simpleName}")
                return false
            } finally {
                connection.disconnect()
            }
            // Only if the ID is still the same: "Delete my contributions" may have run meanwhile.
            if (preferences.getString(CONTRIBUTOR, null) != id || !enabled(context)) return true
            queue.removeOldest(lines.size)
            preferences.edit().putInt(SENT, preferences.getInt(SENT, 0) + lines.size).putLong(SENT_AT, System.currentTimeMillis()).apply()
            Log.d(TAG, "Sent ${lines.size} examples in ${sealed.size} sealed bytes")
        }
        return true
    }

    /** Asks the server to delete everything sent with the old contributor ID. */
    fun sendDeletion(context: Context): Boolean {
        val preferences = Connected.preferences(context)
        val id = preferences.getString(PENDING_DELETE, null) ?: return true
        if (Connected.CONTRIBUTIONS_URL.isEmpty()) return true
        val connection = Connected.open("${Connected.CONTRIBUTIONS_URL}/v1/delete", "POST")
        return try {
            val body = "{\"contributor\":${Json.quote(id)}}".toByteArray(Charsets.UTF_8)
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (Connected.ok(connection)) {
                preferences.edit().remove(PENDING_DELETE).apply()
                true
            } else false
        } catch (error: Exception) {
            Log.w(TAG, "Deletion request failed: ${error.javaClass.simpleName}")
            false
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The server files batches under a hash of the contributor ID, not the ID itself. Only the phone knows the ID, so
     * only the phone can ask to delete them.
     */
    internal fun folder(id: String): String =
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** A short name for the bundled models: the start of the hash of their manifest. */
    private var modelsName: String? = null
    private fun models(context: Context): String = modelsName ?: try {
        val manifest = context.assets.open("swipe2/manifest.sha256").use { it.readBytes() }
        MessageDigest.getInstance("SHA-256").digest(manifest).take(6).joinToString("") { "%02x".format(it) }
    } catch (error: Exception) {
        "unknown"
    }.also { modelsName = it }

    private const val DUE = "due"
    private const val ANSWERED = "answered"
}
