package com.titankeys.support

import com.titankeys.connected.Json

/**
 * The support page's offline web view (owner's choice, 2026-11-19: one bundled HTML page in a full-screen WebView). This file
 * holds its rules, with nothing from Android, so tests/support checks them on a computer: which loads are allowed, and the
 * bridge's messages, each validated before anything native runs.
 *
 * The page is served from the app's own assets under a virtual HTTPS origin ([SupportWebAssets]); every other load is refused,
 * so the page has no network and no third-party content.
 *
 * Bridge (agreed with E, tools/dev/preview/pages/support-app.html): the page calls
 * `TitanKeysSupport.post(JSON.stringify({id, op, args}))`, and the app answers by calling `window.titankeysSupport.receive(text)`
 * with `{"id":…, "ok":true, "result":…}` or `{"id":…, "ok":false, "error":"code"}`. Events come the same way, with no id:
 * `{"event":"state"|"purchase"|"receipt", "data":…}`. The ops are [SupportCall]'s.
 */
object SupportWebAssets {
    /** The virtual origin Android documents for app assets (WebViewAssetLoader's). Nothing is fetched from it. */
    const val ORIGIN = "https://appassets.androidplatform.net"
    const val PREFIX = "/support/"
    /** The page, with its mode after the #: "page" or "popup". */
    fun start(mode: String) = "$ORIGIN${PREFIX}index.html#" + (if (mode == "popup") "popup" else "page")

    private val NAME = Regex("[A-Za-z0-9_-]+(\\.[A-Za-z0-9]+)?")

    /**
     * The asset under `support/` that [url] names, or null when the load must be refused: another origin or scheme, a path
     * outside support/, `..`, or a name with anything but letters, digits, `-`, `_` and one dot.
     */
    fun assetPath(url: String): String? {
        val bare = url.substringBefore('#').substringBefore('?')
        if (!bare.startsWith(ORIGIN + PREFIX)) return null
        val path = bare.removePrefix(ORIGIN + PREFIX).ifEmpty { "index.html" }
        val parts = path.split('/')
        if (parts.size > 3 || parts.any { it.isEmpty() || !NAME.matches(it) }) return null
        return "support/$path"
    }

    fun mime(path: String) = when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html"
        "js" -> "text/javascript"
        "css" -> "text/css"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "woff2" -> "font/woff2"
        "otf" -> "font/otf"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }
}

/** What the page can ask, each with its arguments already checked. */
sealed interface SupportCall {
    /** Prices, tiers, rank, cups, the receipt, the supporter flag and the typing stats. */
    data object State : SupportCall
    /** "This puts you at #N" with [cents] more. */
    data class PreviewRank(val cents: Long) : SupportCall
    /** Opens Play for [productId]. [quantity] is a hint for the $1 unit; Play's own cart sets the real quantity. */
    data class Purchase(val productId: String, val quantity: Int) : SupportCall
    data class JoinBoard(val name: String?, val message: String?, val anonymous: Boolean) : SupportCall
    data object RemoveMe : SupportCall
    data class Report(val publicId: String) : SupportCall
    /** A page of the board, 20 places each, from 0. */
    data class Board(val page: Int) : SupportCall
    /** The popup was answered or closed: it doesn't come back. */
    data object DismissPopup : SupportCall
    /** The page asks to close the screen. */
    data object Close : SupportCall
}

data class SupportRequest(val id: Long, val call: SupportCall)

class SupportBadRequest(val id: Long?, val code: String) : Exception(code)

object SupportWebProtocol {
    /** A message larger than this is refused unread. */
    const val MAX_MESSAGE = 4096
    const val MAX_NAME = 80
    const val MAX_TEXT = 120
    const val MAX_QUANTITY = 100
    const val MAX_CENTS = 1_000_000L
    const val MAX_PAGE = 10_000
    private val PUBLIC_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

    /** Reads one message from the page. Throws [SupportBadRequest] with the reply's id, if it could be read, and a code. */
    fun parse(text: String): SupportRequest {
        if (text.length > MAX_MESSAGE) throw SupportBadRequest(null, "too_large")
        val root = try {
            Json.parse(text) as? Map<*, *>
        } catch (_: Exception) {
            null
        } ?: throw SupportBadRequest(null, "bad_json")
        val id = wholeNumber(root["id"])?.takeIf { it in 0..Int.MAX_VALUE.toLong() } ?: throw SupportBadRequest(null, "bad_id")
        val op = root["op"] as? String ?: throw SupportBadRequest(id, "bad_op")
        val args = when (val value = root["args"]) {
            null -> emptyMap<Any?, Any?>()
            is Map<*, *> -> value
            else -> throw SupportBadRequest(id, "bad_args")
        }
        fun bad(): Nothing = throw SupportBadRequest(id, "bad_args")
        fun only(vararg keys: String) { if (!args.keys.all { it in keys }) bad() }
        fun number(key: String, range: LongRange): Long = wholeNumber(args[key])?.takeIf { it in range } ?: bad()
        fun text(key: String, max: Int): String? = when (val value = args[key]) {
            null -> null
            is String -> value.trim().takeIf { it.codePointCount(0, it.length) <= max } ?: bad()
            else -> bad()
        }?.ifEmpty { null }
        val call = when (op) {
            "state" -> { only(); SupportCall.State }
            "previewRank" -> { only("cents"); SupportCall.PreviewRank(number("cents", 0..MAX_CENTS)) }
            "purchase" -> {
                only("productId", "quantity")
                val product = (args["productId"] as? String)?.let(SupportProduct::find) ?: bad()
                val quantity = if (args["quantity"] == null) 1 else number("quantity", 1L..MAX_QUANTITY).toInt()
                if (quantity > 1 && product != SupportProduct.TipUnit) bad()
                SupportCall.Purchase(product.id, quantity)
            }
            "joinBoard" -> {
                only("name", "message", "anonymous")
                val anonymous = args["anonymous"] as? Boolean ?: bad()
                SupportCall.JoinBoard(text("name", MAX_NAME), text("message", MAX_TEXT), anonymous)
            }
            "removeMe" -> { only(); SupportCall.RemoveMe }
            "report" -> {
                only("publicId")
                val publicId = (args["publicId"] as? String)?.takeIf { PUBLIC_ID.matches(it) } ?: bad()
                SupportCall.Report(publicId)
            }
            "board" -> { only("page"); SupportCall.Board(if (args["page"] == null) 0 else number("page", 0L..MAX_PAGE).toInt()) }
            "dismissPopup" -> { only(); SupportCall.DismissPopup }
            "close" -> { only(); SupportCall.Close }
            else -> throw SupportBadRequest(id, "unknown_op")
        }
        return SupportRequest(id, call)
    }

    /** The answer to request [id]: [resultJson] is already JSON text. */
    fun reply(id: Long, resultJson: String) = "{\"id\":$id,\"ok\":true,\"result\":$resultJson}"

    fun failure(id: Long?, code: String) = "{\"id\":${id ?: "null"},\"ok\":false,\"error\":${Json.quote(code)}}"

    fun event(name: String, dataJson: String) = "{\"event\":${Json.quote(name)},\"data\":$dataJson}"

    /** The script that hands [message] to the page: the message goes in as a JSON string literal, never as code. */
    fun deliver(message: String) = "window.titankeysSupport && window.titankeysSupport.receive(${Json.quote(message)})"

    private fun wholeNumber(value: Any?): Long? {
        val number = value as? Double ?: return null
        if (number.isNaN() || number != Math.floor(number) || number > 9.0E15 || number < -9.0E15) return null
        return number.toLong()
    }
}
