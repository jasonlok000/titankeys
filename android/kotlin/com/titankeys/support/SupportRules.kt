package com.titankeys.support

import java.security.MessageDigest

internal enum class SupportProduct(val id: String, val monthly: Boolean, val cents: Long) {
    MonthlySip("support_monthly_sip", true, 50),
    MonthlySmall("support_monthly_small", true, 100),
    MonthlyFull("support_monthly_full", true, 200),
    TipSip("support_tip_sip", false, 50),
    TipSmall("support_tip_small", false, 100),
    TipFull("support_tip_full", false, 200),
    /** Custom: $1, bought in any quantity (owner, 2026-11-19: no fixed ladder; monthly has its three tiers only). */
    TipUnit("support_tip_unit", false, 100);

    fun value(quantity: Int, micros: Long?, currency: String?): Long {
        val unit = if (currency == "USD" && micros != null) (micros + 5_000) / 10_000 else cents
        return unit * quantity
    }

    companion object {
        fun find(id: String) = entries.firstOrNull { it.id == id }
    }
}

internal fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).toHex()

internal fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal object SupportCard {
    const val WAIT_MILLIS = 14L * 24 * 60 * 60 * 1000
    fun due(now: Long, firstUse: Long, seen: Boolean) =
        !seen && firstUse > 0 && now >= firstUse && now - firstUse >= WAIT_MILLIS

    /** The first available count leads. At most two other counts follow in the same order. */
    fun displayed(counts: LongArray) = counts.withIndex().filter { it.value > 0 }.take(3)
}

internal data class SupportCredit(val id: String, val productId: String, val quantity: Int,
    val previousCents: Long, val totalCents: Long)

/**
 * What a credit is keyed by. A tip's token is new each time. A subscription keeps one token for every month, so each renewal is
 * keyed by its own order (GPA.…, then GPA.…..0, ..1): each paid month counts once. Without an order ID, the token.
 */
internal fun supportKey(token: String, orderId: String?, monthly: Boolean) =
    if (monthly && !orderId.isNullOrBlank()) "order:$orderId" else token

/**
 * A subscription a ledger from before [supportKey] credited by its token: that credit was for the month it was bought in, so the
 * order Play shows now takes it over, without a second credit. The token's own entry then goes, so the next renewal counts.
 */
internal fun keyedByOldToken(processed: Set<String>, token: String, key: String) =
    key != token && sha256(token) in processed && sha256(key) !in processed

/** A key ([supportKey]) counts once, even if it's delivered again after a crash or while the purchase is reconciled. */
internal fun supportCredit(processed: Set<String>, totalCents: Long, token: String, product: SupportProduct, quantity: Int,
    micros: Long? = null, currency: String? = null): SupportCredit? {
    if (token.isBlank() || quantity !in 1..10_000) return null
    val id = sha256(token)
    if (id in processed) return null
    return SupportCredit(id, product.id, quantity, totalCents, totalCents + product.value(quantity, micros, currency))
}
