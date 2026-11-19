package com.titankeys.support

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.titankeys.connected.Connected
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** The public board and verified Play posting share a fixed HTTPS origin. */
internal object SupportBoard {
    private const val ORIGIN = "https://titankeys-support.everyones-niche.workers.dev"
    private const val BOARD_PATH = "/v1/leaderboard"
    private const val POST_PATH = "/v1/support"
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    // Only read and changed on the board's single executor.
    private var postingReady = false

    /** A page of the ranked board: place, public id, name and the message the place shows. Never an amount. */
    fun read(context: Context, offset: Int, limit: Int, done: (JSONObject) -> Unit) =
        request("$BOARD_PATH?offset=${offset.coerceAtLeast(0)}&limit=${limit.coerceIn(1, 50)}", null, done)

    /** "This puts you at #N": the place this account would reach with [addCents] more. The server limits how often. */
    fun preview(context: Context, accountKey: String, addCents: Long, done: (JSONObject) -> Unit) =
        request("/v1/rank-preview", JSONObject().put("accountKey", accountKey).put("addCents", addCents), done)

    /** Reports an entry to the owner. Nothing is hidden by itself. */
    fun report(context: Context, publicId: String, reason: String?, done: (JSONObject) -> Unit) =
        request("/v1/report", JSONObject().put("id", publicId).put("reason", reason ?: JSONObject.NULL), done)

    fun join(context: Context, accountKey: String, purchases: JSONArray, anonymous: Boolean,
             name: String?, message: String?, done: (JSONObject) -> Unit) {
        val payload = JSONObject().put("accountKey", accountKey).put("optIn", true)
            .put("purchases", purchases).put("anonymous", anonymous)
        if (!anonymous && name != null) payload.put("name", name)
        if (message != null) payload.put("message", message)
        request(POST_PATH, payload, done)
    }

    fun remove(context: Context, accountKey: String, done: (JSONObject) -> Unit) =
        request("/v1/remove", JSONObject().put("accountKey", accountKey), done)

    private fun request(path: String, body: JSONObject?, done: (JSONObject) -> Unit) {
        io.execute {
            val result = runCatching {
                check(path != POST_PATH || postingReady)
                val connection = Connected.open(ORIGIN + path, if (body == null) "GET" else "POST")
                try {
                    if (body != null) {
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    }
                    check(Connected.ok(connection))
                    val bytes = connection.inputStream.use { Connected.readLimited(it, 32 * 1024) }
                    val data = if (bytes.isEmpty()) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8))
                    if (path.startsWith(BOARD_PATH)) {
                        postingReady = connection.getHeaderField("X-TitanKeys-Posting") == "ready"
                        data.put("postingReady", postingReady)
                    }
                    data.put("available", true)
                } finally {
                    connection.disconnect()
                }
            }.getOrElse {
                if (path.startsWith(BOARD_PATH)) postingReady = false
                JSONObject().put("available", false).put("entries", JSONArray())
            }
            main.post { done(result) }
        }
    }
}
