package com.titankeys.support

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import org.json.JSONArray
import org.json.JSONObject

/** Prices load from the Support page. A previous purchase attempt is reconciled when the app returns. */
internal class SupportBilling(private val activity: Activity, private val changed: () -> Unit) {
    val ledger by lazy { SupportLedger(activity) }
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var connecting = false
    private var querying = false
    private var offersRequested = false
    private var result: String? = null
    private var flowOpen = false
    private var launchedOffer: Offer? = null
    private val finalizing = mutableSetOf<String>()
    private val offers = linkedMapOf<String, Offer>()

    private val clientSource = lazy {
        BillingClient.newBuilder(activity.applicationContext)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .setListener { response, purchases -> post { updated(response, purchases.orEmpty()) } }
            .build()
    }
    private val client by clientSource

    private data class Offer(val product: SupportProduct, val details: ProductDetails, val token: String?,
        val price: String, val micros: Long, val currency: String)

    fun state() = JSONObject()
        // A history that couldn't be read: buying waits rather than risk a credit counted twice or lost.
        .put("billing", if (offers.isEmpty() || ledger.broken) "unavailable" else "ready")
        .put("purchaseResult", result ?: JSONObject.NULL)
        .put("products", JSONArray(offers.values.map { offer ->
            JSONObject().put("id", offer.product.id).put("price", offer.price)
                .put("amountMicros", offer.micros).put("currency", offer.currency).put("baseCents", offer.product.cents)
        }))
        .put("totalCents", ledger.cents)
        .put("receipt", ledger.receipt ?: JSONObject.NULL)

    private fun post(action: () -> Unit) {
        main.post { if (!closed) action() }
    }

    fun refresh() {
        offersRequested = true
        connect()
    }

    private fun connect() {
        if (closed || connecting || querying) return
        if (client.isReady) {
            ready()
            return
        }
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(response: BillingResult) = post {
                connecting = false
                if (response.responseCode == BillingResponseCode.OK) ready() else {
                    offers.clear()
                    offersRequested = false
                    changed()
                }
            }

            override fun onBillingServiceDisconnected() = post {
                connecting = false
            }
        })
    }

    private fun ready() {
        if (offersRequested) queryProducts() else reconcile()
    }

    fun onResume() {
        if (clientSource.isInitialized() && client.isReady) reconcile()
        else if (ledger.accountKey() != null) connect()
    }

    fun close() {
        closed = true
        main.removeCallbacksAndMessages(null)
        if (clientSource.isInitialized()) client.endConnection()
    }

    private fun queryProducts() {
        offersRequested = false
        querying = true
        val incoming = linkedMapOf<String, Offer>()
        var remaining = 2
        for (monthly in listOf(true, false)) {
            val type = if (monthly) ProductType.SUBS else ProductType.INAPP
            val products = SupportProduct.entries.filter { it.monthly == monthly }.map { product ->
                QueryProductDetailsParams.Product.newBuilder().setProductId(product.id).setProductType(type).build()
            }
            val request = QueryProductDetailsParams.newBuilder().setProductList(products).build()
            client.queryProductDetailsAsync(request) { response, details -> post {
                if (response.responseCode == BillingResponseCode.OK) {
                    for (item in details.productDetailsList) offer(item)?.let { incoming[it.product.id] = it }
                }
                if (--remaining == 0) {
                    offers.clear()
                    offers.putAll(incoming)
                    querying = false
                    reconcile()
                    changed()
                }
            } }
        }
    }

    private fun offer(details: ProductDetails): Offer? {
        val product = SupportProduct.find(details.productId) ?: return null
        if (product.monthly) {
            val option = details.subscriptionOfferDetails.orEmpty().firstOrNull { it.offerId == null &&
                it.pricingPhases.pricingPhaseList.lastOrNull()?.let { price ->
                    price.billingPeriod == "P1M" && price.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING
                } == true } ?: return null
            val price = option.pricingPhases.pricingPhaseList.last()
            return Offer(product, details, option.offerToken, price.formattedPrice, price.priceAmountMicros, price.priceCurrencyCode)
        }
        val option = details.oneTimePurchaseOfferDetailsList.orEmpty().firstOrNull {
            it.offerId == null && it.rentalDetails == null && it.preorderDetails == null
        } ?: return null
        return Offer(product, details, option.offerToken, option.formattedPrice, option.priceAmountMicros, option.priceCurrencyCode)
    }

    fun purchase(productId: String): String {
        if (flowOpen) return "pending"
        if (ledger.broken) return "unavailable"
        val offer = offers[productId] ?: return "unavailable"
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(offer.details)
            .apply { offer.token?.let(::setOfferToken) }.build()
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product))
            .setObfuscatedAccountId(ledger.accountId()).build()
        ledger.quote(offer.product, offer.micros, offer.currency)
        // Play's cart sets a unit purchase's quantity. There is no quantity input on BillingFlowParams.
        return when (client.launchBillingFlow(activity, params).responseCode) {
            BillingResponseCode.OK -> {
                result = null
                flowOpen = true
                launchedOffer = offer
                "launched"
            }
            BillingResponseCode.USER_CANCELED -> "cancelled"
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                reconcile()
                "unavailable"
            }
            else -> "unavailable"
        }
    }

    private fun updated(response: BillingResult, purchases: List<Purchase>) {
        flowOpen = false
        result = when (response.responseCode) {
            BillingResponseCode.OK -> if (purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }) "pending" else null
            BillingResponseCode.USER_CANCELED -> "cancelled"
            else -> "unavailable"
        }
        if (response.responseCode == BillingResponseCode.OK) purchases.forEach { credit(it, fromFlow = true) }
        if (purchases.none { it.purchaseState == Purchase.PurchaseState.PENDING }) launchedOffer = null
        changed()
    }

    private fun reconcile() {
        for (type in listOf(ProductType.INAPP, ProductType.SUBS)) {
            val query = QueryPurchasesParams.newBuilder().setProductType(type).build()
            client.queryPurchasesAsync(query) { response, purchases -> post {
                if (response.responseCode == BillingResponseCode.OK) purchases.forEach { credit(it, fromFlow = false) }
            } }
        }
    }

    private fun credit(purchase: Purchase, fromFlow: Boolean) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val product = purchase.products.singleOrNull()?.let(SupportProduct::find) ?: return
        val token = purchase.purchaseToken
        val offer = launchedOffer?.takeIf { it.product == product } ?: offers[product.id]
        // The credit goes to the disk on the ledger's own thread; Play hears of it only once it's there.
        val recorded = runCatching {
            ledger.record(token, purchase.orderId, product, purchase.quantity, purchase.accountIdentifiers?.obfuscatedAccountId,
                offer?.micros, offer?.currency, notify = fromFlow || !purchase.isAcknowledged) { saved ->
                post { finish(purchase, product, saved) }
            }
        }.getOrDefault(false)
        if (!recorded) {
            result = "unavailable"
            changed()
            return
        }
        result = null
        changed()
    }

    /** The credit is on the disk ([saved]), or couldn't be written: then Play isn't told, and the next reconcile tries again. */
    private fun finish(purchase: Purchase, product: SupportProduct, saved: Boolean) {
        val token = purchase.purchaseToken
        if (!saved) {
            result = "unavailable"
            changed()
            return
        }
        if (product.monthly && purchase.isAcknowledged || !finalizing.add(token)) return
        if (product.monthly) {
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
            client.acknowledgePurchase(params) { post { finalizing.remove(token) } }
        } else {
            val params = ConsumeParams.newBuilder().setPurchaseToken(token).build()
            client.consumeAsync(params) { _, _ -> post { finalizing.remove(token) } }
        }
    }
}
