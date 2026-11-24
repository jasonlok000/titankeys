package com.titankeys.support

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.titankeys.keyboard.AppBuild
import com.titankeys.keyboard.FixedScale
import com.titankeys.keyboard.GuideMotion
import com.titankeys.keyboard.Haptic
import com.titankeys.keyboard.Haptics
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale

/**
 * The support page, one bundled offline HTML page (android/assets/support/) in a full-screen WebView: "page" mode from the
 * Settings card and the front page, "popup" mode once, after about ten typing days, over the front page. Its rules and messages
 * are in [SupportWebProtocol]; the work is SupportBilling's, SupportLedger's, SupportBoard's and SupportStats'.
 *
 * The WebView loads nothing but those assets: every other request gets an empty 403, file and content access are off, there's no
 * third-party content and no network, and its debugging is on only in TitanKeys Debug. TitanKeys Debug's support preview
 * (com.titankeys.debug.supportPreview) answers through the same bridge with sample prices, a sample board and a test checkout.
 */
class SupportWebActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var web: WebView
    private var mode = "page"
    /** TitanKeys Debug's sample support: no Play, no server. */
    private var preview = false
    private var billing: SupportBilling? = null
    private var lastReceipt: String? = null
    private var pageReady = false
    /** The page has finished loading, so its script can play a receipt's thank-you. See [billingChanged]. */
    private var pageLoaded = false
    private val haptics by lazy { Haptics(this) }
    /** The camera cutout's height at the top of the screen, in phone pixels; the page keeps its controls below it. */
    private var cutTop = 0

    /** Tells the page the cutout's height ([cutTop]): it sets --cut, in its own CSS pixels. */
    private fun sendCut() {
        if (!pageReady) return
        web.evaluateJavascript("window.titankeysCut && window.titankeysCut($cutTop)", null)
    }

    /** The same sizes at any display or font size, as every TitanKeys screen ([FixedScale]). The page also zooms itself to
     * its 690px design width (android/assets/support/index.html), and WebSettings.textZoom is pinned. */
    override fun attachBaseContext(base: Context) = super.attachBaseContext(FixedScale.wrap(base))

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = if (intent.getStringExtra(MODE) == "popup") "popup" else "page"
        // The page is an opaque window; only the popup sees through to the front page.
        if (mode != "popup") {
            setTranslucent(false)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        }
        preview = AppBuild.kind(this) == "debug" && intent.getBooleanExtra(PREVIEW_FIXTURE, false)
        if (!preview) billing = SupportBilling(this, ::billingChanged).also { it.refresh() }
        WebView.setWebContentsDebuggingEnabled(AppBuild.kind(this) == "debug")
        web = WebView(this).apply {
            // See-through only for the popup over the front page; the page itself is opaque, as a transparent WebView drew
            // its layered parts (the pay button, the thank-you) squeezed.
            setBackgroundColor(if (mode == "popup") Color.TRANSPARENT else Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                blockNetworkLoads = true
                blockNetworkImage = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                setGeolocationEnabled(false)
                cacheMode = WebSettings.LOAD_NO_CACHE
                setSupportZoom(false)
                builtInZoomControls = false
                textZoom = 100
            }
            // No overscroll stretch: the page scrolls inside its own screens, and the stretch stayed, squashing the button.
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            // The WebView's own drawing, no software layer: software drew a changed part at 1/1.5625 its size until the next
            // full redraw, as the toggle's labels (2026-11-20). What once drew squeezed in the GPU's layers was the page's
            // transform animations and opacity transitions, now gone (android/assets/support/index.html).
            webViewClient = Client()
            webChromeClient = null
            addJavascriptInterface(Bridge(), BRIDGE)
        }
        // Full screen from the first frame (the Fullscreen theme, and the bars hidden before the page is added): hiding them
        // after the WebView had laid out left it drawing its layered parts for the old, shorter window, squeezed.
        window.setDecorFitsSystemWindows(false)
        // The page fills the screen to its top edge, the camera cutout's band included, so the orange thank-you and the black
        // pages reach the top; only the page's own controls keep below the camera, by --cut (owner, 2026-11-20: "we shdn't
        // have elements there ... we shdn't leave the top of the screen empty"). The squeezing that once came with the band was
        // the transform-animated layers, now gone.
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        // The decor first: before setContentView it may not exist yet, and the insets controller then crashed the page as it
        // opened (dropbox, 2026-11-20 11:21: DecorView.getWindowInsetsController() on null).
        window.decorView
        window.insetsController?.apply {
            hide(android.view.WindowInsets.Type.systemBars())
            systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val frame = android.widget.FrameLayout(this).apply {
            setBackgroundColor(if (mode == "popup") Color.TRANSPARENT else Color.BLACK)
            addView(web, android.widget.FrameLayout.LayoutParams(-1, -1))
            setOnApplyWindowInsetsListener { _, insets ->
                cutTop = insets.displayCutout?.safeInsetTop ?: 0
                sendCut()
                insets
            }
        }
        setContentView(frame)
        web.loadUrl(SupportWebAssets.start(mode))
    }

    /** Back goes through the page first: a sheet closes, a sub-screen returns to the support page; only the main page closes. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!pageReady) return finish()
        web.evaluateJavascript("!!(window.titankeysSupport && window.titankeysSupport.back && window.titankeysSupport.back())") {
            if (it != "true" && !isFinishing) finish()
        }
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        runCatching { billing?.onResume() }
    }

    override fun onPause() {
        inFront = false
        super.onPause()
    }

    override fun onDestroy() {
        billing?.close()
        web.removeJavascriptInterface(BRIDGE)
        web.destroy()
        super.onDestroy()
    }

    /** Serves the assets under the virtual origin, and refuses every other load. */
    private inner class Client : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
            val path = SupportWebAssets.assetPath(request.url.toString()) ?: return refused()
            return try {
                WebResourceResponse(SupportWebAssets.mime(path), "utf-8", assets.open(path))
                    .apply { responseHeaders = mapOf("Cache-Control" to "no-store", "Content-Security-Policy" to CSP) }
            } catch (_: IOException) {
                refused()
            }
        }

        /** No navigation away from the page: links go nowhere. */
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
            SupportWebAssets.assetPath(request.url.toString()) == null

        // Ready as soon as our own page starts: its script asks for its state while it is still loading, and a message
        // dropped then would leave the page blank for good.
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            pageReady = SupportWebAssets.assetPath(url) != null
            takeEarly()
        }

        override fun onPageFinished(view: WebView, url: String) {
            takeEarly()
            sendCut()
            pageLoaded = pageReady
            // A receipt that came while the page was still loading plays now. See [billingChanged].
            if (pageLoaded && !preview) runCatching { billingChanged() }
        }

        private fun refused() = WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    /** The page's one way in. Every message is read and checked ([SupportWebProtocol.parse]) before anything native runs. */
    private inner class Bridge {
        @JavascriptInterface
        fun post(message: String?) {
            main.post { take(message.orEmpty()) }
        }
    }

    /**
     * The page's script asks for its board and state while it is still loading, and its message can reach the main thread before
     * onPageStarted does: dropped, it left the page blank for good, 6 opens in 15 on the phone (2026-11-20). A message from before
     * the page counts as ours waits, a few at most, and goes once it does; a page that isn't ours never gets them.
     */
    private val early = ArrayDeque<String>()

    private fun take(message: String) {
        if (isFinishing) return
        // Only the page itself, under its own origin, may ask.
        if (!pageReady || SupportWebAssets.assetPath(web.url.orEmpty()) == null) {
            if (early.size < EARLY_MAX) early.addLast(message)
            return
        }
        val request = try {
            SupportWebProtocol.parse(message)
        } catch (error: SupportBadRequest) {
            send(SupportWebProtocol.failure(error.id, error.code))
            return
        }
        handle(request)
    }

    /** The page is ours now, or isn't: the waiting messages go, or are dropped. */
    private fun takeEarly() {
        if (early.isEmpty()) return
        val waiting = early.toList()
        early.clear()
        if (!pageReady || SupportWebAssets.assetPath(web.url.orEmpty()) == null) return
        waiting.forEach(::take)
    }

    private fun handle(request: SupportRequest) {
        val id = request.id
        fun answer(result: JSONObject) = send(SupportWebProtocol.reply(id, result.toString()))
        when (val call = request.call) {
            SupportCall.State -> answer(state())
            is SupportCall.PreviewRank -> if (preview) answer(sampleRank(call.cents)) else {
                val key = billing?.ledger?.accountKey()
                if (key == null) answer(JSONObject().put("available", false))
                else SupportBoard.preview(this, key, call.cents) { answer(it) }
            }
            is SupportCall.Purchase -> {
                val result = if (preview) "preview" else billing?.purchase(call.productId) ?: "unavailable"
                answer(JSONObject().put("result", result).put("quantity", call.quantity))
                // The test checkout: the receipt that a real purchase would bring, so the page plays its thank-you.
                if (preview) event("receipt", sampleReceipt(call.productId, call.quantity))
            }
            is SupportCall.JoinBoard -> if (preview) {
                answer(JSONObject().put("available", true).put("rank", 14).put("tier", "top100").put("messageLimit", 40).put("joined", true))
                event("state", state())
            } else {
                val ledger = billing?.ledger
                val key = ledger?.accountKey()
                val proofs = ledger?.boardProofs()
                if (key == null || proofs == null || proofs.length() == 0) answer(JSONObject().put("available", false))
                else SupportBoard.join(this, key, proofs, call.anonymous, call.name, call.message) { joined(it, true, ::answer) }
            }
            SupportCall.RemoveMe -> if (preview) {
                answer(JSONObject().put("available", true).put("joined", false))
                event("state", state())
            } else {
                val key = billing?.ledger?.accountKey()
                if (key == null) answer(JSONObject().put("available", false))
                else SupportBoard.remove(this, key) { joined(it, false, ::answer) }
            }
            is SupportCall.Report -> if (preview) answer(JSONObject().put("available", true).put("ok", true))
                else SupportBoard.report(this, call.publicId, null) { answer(it.put("ok", it.optBoolean("available"))) }
            is SupportCall.Board -> if (preview) answer(sampleBoard(call.page)) else board(call.page, ::answer)
            SupportCall.DismissPopup -> {
                SupportStats.dismiss(this)
                answer(JSONObject().put("ok", true))
            }
            SupportCall.Close -> {
                answer(JSONObject().put("ok", true))
                finish()
            }
            // No answer: the page doesn't wait on a haptic, so each one lands with its frame.
            is SupportCall.Haptic -> haptics.play(when (call.name) {
                "tick" -> Haptic.AppStep
                "pick" -> Haptic.AppPicked
                "join" -> Haptic.AppSwitchOn
                "celebrate" -> Haptic.GuideCelebration
                else -> Haptic.AppPress
            })
        }
    }

    /** A page of the board, with this phone's place and its own entry marked, as the app's adapter gives it. */
    private fun board(page: Int, done: (JSONObject) -> Unit) {
        val ledger = billing?.ledger
        val key = ledger?.accountKey()?.takeIf { ledger.boardJoined }
        SupportBoard.read(this, page * PAGE, PAGE) { board ->
            if (key == null || !board.optBoolean("available")) return@read done(board.put("myRank", JSONObject.NULL))
            SupportBoard.preview(this, key, 0) { mine ->
                val myId = mine.optString("myId").takeIf { mine.optBoolean("available") && it.isNotEmpty() && it != "null" }
                board.put("myRank", if (myId != null && mine.has("myRank")) mine.get("myRank") else JSONObject.NULL)
                val entries = board.optJSONArray("entries") ?: JSONArray()
                for (index in 0 until entries.length()) entries.optJSONObject(index)?.let { if (it.optString("id") == myId) it.put("you", true) }
                done(board)
            }
        }
    }

    private fun joined(response: JSONObject, joined: Boolean, done: (JSONObject) -> Unit) {
        val ledger = billing?.ledger
        if (ledger != null && response.optBoolean("available")) runCatching { ledger.boardJoined(joined) }
        done(response.put("joined", ledger?.boardJoined ?: false))
        event("state", state())
    }

    /** Everything the page shows: prices and tiers, cups, rank, the receipt, the supporter flag and the typing-day stats. */
    private fun state(): JSONObject {
        val base = if (preview) sampleState() else billing!!.state().put("boardJoined", billing!!.ledger.boardJoined)
        val cents = base.optLong("totalCents")
        val products = base.optJSONArray("products") ?: JSONArray()
        val tiers = JSONObject()
            .put("monthly", JSONArray(SupportProduct.entries.filter { it.monthly }.map { it.id }))
            .put("oneTime", JSONArray(SupportProduct.entries.filter { !it.monthly && it != SupportProduct.TipUnit }.map { it.id }))
            .put("custom", SupportProduct.TipUnit.id)
        return base.put("mode", mode).put("preview", preview).put("products", products).put("tiers", tiers)
            .put("cups", Math.round(cents / 50.0) / 10.0).put("supporter", cents > 0)
            .put("reduceMotion", !GuideMotion.enabled)
            .put("stats", SupportStats.totals(this, preview))
    }

    private fun billingChanged() {
        val state = state()
        event("state", state)
        // A new receipt: its own event, once, so the page plays the thank-you, and only to a page that has loaded and can play it.
        // It's then marked seen, so opening the page again doesn't play the thank-you again (owner, 2026-11-24: the pour came
        // back on every visit, as only the old app screen ever marked a receipt seen).
        val receipt = state.optJSONObject("receipt")
        val id = receipt?.optString("id")
        if (receipt != null && id != null && id != lastReceipt && pageLoaded) {
            lastReceipt = id
            event("receipt", receipt)
            runCatching { billing?.ledger?.receiptSeen(id) }
        }
        state.optString("purchaseResult").takeIf { it.isNotEmpty() && it != "null" }?.let { event("purchase", JSONObject().put("result", it)) }
    }

    private fun event(name: String, data: JSONObject) = send(SupportWebProtocol.event(name, data.toString()))

    private fun send(message: String) {
        if (isFinishing || isDestroyed) return
        web.evaluateJavascript(SupportWebProtocol.deliver(message), null)
    }

    private fun sampleState(): JSONObject {
        val currency = NumberFormat.getCurrencyInstance(Locale.CANADA)
        return JSONObject().put("billing", "ready").put("purchaseResult", JSONObject.NULL)
            .put("products", JSONArray(SupportProduct.entries.map { product ->
                JSONObject().put("id", product.id).put("price", "${currency.format(product.cents / 100.0)} CAD")
                    .put("amountMicros", product.cents * 10_000).put("currency", "CAD").put("baseCents", product.cents)
            }))
            .put("totalCents", SAMPLE_MINE).put("receipt", JSONObject.NULL).put("boardJoined", true)
    }

    private fun sampleReceipt(productId: String, quantity: Int): JSONObject {
        val product = SupportProduct.find(productId) ?: SupportProduct.TipFull
        val added = product.cents * quantity
        return JSONObject().put("id", "preview-$productId-$quantity").put("productId", productId).put("quantity", quantity)
            .put("previousCents", SAMPLE_MINE).put("totalCents", SAMPLE_MINE + added)
    }

    /** Eighteen sample supporters; this phone is 14th. Labelled as samples, with no real names. */
    private fun sampleBoard(page: Int): JSONObject {
        val entries = JSONArray()
        if (page == 0) for (rank in 1..SAMPLE_TOTALS.size) {
            val limit = when { rank <= 3 -> 120; rank <= 10 -> 80; rank <= 100 -> 40; else -> 0 }
            val message = "Sample message $rank".takeIf { limit > 0 && rank % 3 != 0 }
            entries.put(JSONObject().put("rank", rank).put("id", "00000000-0000-4000-8000-%012d".format(rank))
                .put("name", if (rank % 4 == 0) JSONObject.NULL else "Sample $rank").put("message", message ?: JSONObject.NULL)
                .apply { if (rank == 14) put("you", true) })
        }
        return JSONObject().put("available", true).put("postingReady", true).put("joined", true).put("entries", entries)
            .put("total", SAMPLE_TOTALS.size).put("nextOffset", JSONObject.NULL).put("myRank", 14)
    }

    private fun sampleRank(cents: Long): JSONObject {
        val reached = SAMPLE_MINE + cents
        val rank = SAMPLE_TOTALS.withIndex().count { (index, total) -> index != 13 && total > reached } + 1
        val limit = when { rank <= 3 -> 120; rank <= 10 -> 80; rank <= 100 -> 40; else -> 0 }
        val tier = when (limit) { 120 -> "top3"; 80 -> "top10"; 40 -> "top100"; else -> "name" }
        // As the server answers: the place now too, for "up from #M".
        return JSONObject().put("available", true).put("rank", rank).put("tier", tier).put("messageLimit", limit).put("myRank", 14)
    }

    companion object {
        /**
         * The page is in front. The keyboard runs in this same process, and the page's text boxes answer the keyboard on this same
         * main thread, so a question the keyboard asks them while a box starts waits for Android's timeout: on 2026-11-24 the
         * name box on "join the board" froze for 16 s (an ANR) while auto-capitalisation asked for the text before the cursor.
         * While this is true, the keyboard reads that text from what Android handed it with the field instead.
         */
        @Volatile @JvmStatic var inFront = false
            private set

        private const val MODE = "com.titankeys.support.MODE"
        const val PREVIEW_FIXTURE = "com.titankeys.debug.supportPreview"
        private const val BRIDGE = "TitanKeysSupport"
        private const val PAGE = 20
        /** Messages from before the page counts as ours that wait for it: the start's board and state, with room to spare. */
        private const val EARLY_MAX = 8
        /** The page's own content only: no scripts, styles, images or fonts from anywhere else, and no requests out. */
        private const val CSP = "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data:; font-src 'self'; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"
        private const val SAMPLE_MINE = 900L
        private val SAMPLE_TOTALS = longArrayOf(9000, 7000, 6000, 4500, 4000, 3500, 3000, 2600, 2200, 2000, 1800, 1500, 1200,
            SAMPLE_MINE, 800, 500, 300, 100)

        /** Opens the page: "page" from Settings and the front page, "popup" over the front page. */
        fun intent(context: Context, mode: String, preview: Boolean = false): Intent =
            Intent(context, SupportWebActivity::class.java).putExtra(MODE, mode)
                .apply { if (preview) putExtra(PREVIEW_FIXTURE, true) }
    }
}
