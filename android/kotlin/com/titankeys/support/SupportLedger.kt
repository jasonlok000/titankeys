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

/** Purchases stay out of Android backup and app exports. All callers are on the app's main thread. */
internal class SupportLedger(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "support-ledger.json"))
    private var saved = load()

    private fun load() = try {
        JSONObject(file.readFully().toString(Charsets.UTF_8))
    } catch (_: FileNotFoundException) {
        JSONObject()
    } catch (_: JSONException) {
        // A parse error can quote a piece of the file, which contains private Play proof.
        throw IOException("The support history couldn't be read.")
    }

    private fun change(write: (JSONObject) -> Unit) {
        val next = JSONObject(saved.toString())
        write(next)
        val data = next.toString().toByteArray(Charsets.UTF_8)
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
        saved = next
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

    /** Writes the credit before the caller acknowledges or consumes it. A replay can retry Play without re-crediting. */
    fun record(token: String, product: SupportProduct, quantity: Int, accountId: String?, micros: Long?, currency: String?,
        notify: Boolean): Boolean {
        if (token.isBlank()) return false
        val processed = saved.optJSONObject("processed")?.keys()?.asSequence()?.toSet().orEmpty()
        if (sha256(token) in processed) return true
        val attributable = accountKey()?.let(::sha256)?.let { it == accountId } == true
        val quote = saved.optJSONObject("quotes")?.optJSONObject(product.id)?.takeIf { attributable }
        val credit = supportCredit(processed, cents, token, product, quantity,
            quote?.optLong("micros") ?: micros, quote?.optString("currency") ?: currency) ?: return false
        change { state ->
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
        const val MAX_PROOFS = 10
        val ACCOUNT_KEY = Regex("[0-9a-f]{64}")
    }
}
