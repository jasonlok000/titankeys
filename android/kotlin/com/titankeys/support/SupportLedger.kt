package com.titankeys.support

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * Purchases stay out of Android backup and app exports. All callers are on the app's main thread; the file is written on a
 * thread of its own, in order, so a tap never waits for the disk. A write that must land first (a credit before Play is told)
 * says when it has, through [record]'s callback.
 */
internal class SupportLedger(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE))
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "SupportLedger").apply { isDaemon = true } }
    /** The file couldn't be read: it was moved aside, and buying waits, as the history it holds is unknown. */
    var broken = false
        private set
    private var saved = load()

    private fun load(): JSONObject = try {
        JSONObject(file.readFully().toString(Charsets.UTF_8))
    } catch (_: FileNotFoundException) {
        JSONObject()
    } catch (_: Exception) {
        // A JSONException or a read error. Its message can quote the file, which holds private Play proof, so neither the message
        // nor the file is logged. The bad file goes aside, out of the way of the next write, and Support starts empty.
        broken = true
        runCatching { file.baseFile.renameTo(File(file.baseFile.parentFile, "$FILE.bad-${System.currentTimeMillis()}")) }
        JSONObject()
    }

    /** Changes the history now, and writes it on the ledger's thread. [done] hears whether it reached the disk, off the main thread. */
    private fun change(done: ((Boolean) -> Unit)? = null, write: (JSONObject) -> Unit) {
        val next = JSONObject(saved.toString())
        write(next)
        saved = next
        val data = next.toString().toByteArray(Charsets.UTF_8)
        writer.execute {
            val ok = runCatching { persist(data) }.isSuccess
            done?.invoke(ok)
        }
    }

    private fun persist(data: ByteArray) {
        val stream = file.startWrite()
        try {
            stream.write(data)
            stream.fd.sync()
        } catch (error: IOException) {
            file.failWrite(stream)
            throw error
        }
        file.finishWrite(stream)
        if (!file.readFully().contentEquals(data)) throw IOException("The support history couldn't be saved.")
    }

    fun accountKey(): String? = saved.optString("accountKey").takeIf { it.matches(ACCOUNT_KEY) }

    fun accountId(): String {
        accountKey()?.let { return sha256(it) }
        val key = ByteArray(32).also(SecureRandom()::nextBytes).toHex()
        change { it.put("accountKey", key) }
        return sha256(key)
    }

    fun quote(product: SupportProduct, micros: Long, currency: String) {
        change { state ->
            val quotes = state.optJSONObject("quotes") ?: JSONObject()
            state.put("quotes", quotes.put(product.id, JSONObject().put("micros", micros).put("currency", currency)))
        }
    }

    val cents get() = saved.optLong("totalCents")
    val receipt get() = saved.optJSONArray("receipts")?.optJSONObject(0)
    val boardJoined get() = saved.optBoolean("boardJoined")

    fun boardJoined(joined: Boolean) {
        if (joined != boardJoined) change { it.put("boardJoined", joined) }
    }

    /**
     * Records the credit, and calls [saved] once it's on the disk (true) or couldn't be written (false): only then may the caller
     * acknowledge or consume it. A replay can retry Play without re-crediting. A subscription is keyed by each renewal's [orderId]
     * ([supportKey]), so every paid month counts once. False if it can't be recorded at all.
     */
    fun record(token: String, orderId: String?, product: SupportProduct, quantity: Int, accountId: String?, micros: Long?,
        currency: String?, notify: Boolean, saved: (Boolean) -> Unit): Boolean {
        if (token.isBlank() || broken) return false
        val key = supportKey(token, orderId, product.monthly)
        val processed = this.saved.optJSONObject("processed")?.keys()?.asSequence()?.toSet().orEmpty()
        if (sha256(key) in processed) {
            saved(true)
            return true
        }
        if (keyedByOldToken(processed, token, key)) {
            change(saved) { state ->
                val seen = state.optJSONObject("processed") ?: JSONObject()
                seen.remove(sha256(token))
                state.put("processed", seen.put(sha256(key), true))
            }
            return true
        }
        val attributable = accountKey()?.let(::sha256)?.let { it == accountId } == true
        val quote = this.saved.optJSONObject("quotes")?.optJSONObject(product.id)?.takeIf { attributable }
        val credit = supportCredit(processed, cents, key, product, quantity,
            quote?.optLong("micros") ?: micros, quote?.optString("currency") ?: currency) ?: return false
        change(saved) { state ->
            state.put("totalCents", credit.totalCents)
            val seen = state.optJSONObject("processed") ?: JSONObject()
            state.put("processed", seen.put(credit.id, true))
            if (notify) {
                val receipts = state.optJSONArray("receipts") ?: JSONArray()
                state.put("receipts", receipts.put(JSONObject()
                    .put("id", credit.id).put("productId", credit.productId).put("quantity", credit.quantity)
                    .put("previousCents", credit.previousCents).put("totalCents", credit.totalCents)))
            }
            if (attributable) {
                val receipts = state.optJSONArray("proofs") ?: JSONArray()
                if (receipts.length() == MAX_PROOFS) receipts.remove(0)
                // A subscription's token is the same each month: one proof is enough, as the server credits every paid order.
                if (product.monthly) for (index in receipts.length() - 1 downTo 0)
                    if (receipts.optJSONObject(index)?.optString("purchaseToken") == token) receipts.remove(index)
                state.put("proofs", receipts.put(JSONObject().put("purchaseToken", token)
                    .put("productId", product.id).put("kind", if (product.monthly) "subscription" else "tip")))
            }
        }
        return true
    }

    fun receiptSeen(id: String) {
        if (receipt?.optString("id") != id) return
        change { it.optJSONArray("receipts")?.remove(0) }
    }

    /** Only the user-initiated native board join may read the private receipts. */
    fun boardProofs(): JSONArray = saved.optJSONArray("proofs") ?: JSONArray()

    private companion object {
        const val FILE = "support-ledger.json"
        const val MAX_PROOFS = 10
        val ACCOUNT_KEY = Regex("[0-9a-f]{64}")
    }
}
