package com.titankeys.support

import java.security.MessageDigest

internal enum class SupportProduct(val id: String, val monthly: Boolean, val cents: Long) {
    MonthlySip("support_monthly_sip", true, 50),
    MonthlySmall("support_monthly_small", true, 100),
    MonthlyFull("support_monthly_full", true, 250),
    TipSip("support_tip_sip", false, 50),
    TipSmall("support_tip_small", false, 100),
    TipFull("support_tip_full", false, 250),
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
    /** Days the keyboard typed something, counted once each (owner, support flow v4: "after about 10 typing days"). */
    const val TYPING_DAYS = 10
    fun due(typingDays: Int, seen: Boolean) = !seen && typingDays >= TYPING_DAYS

    /** The typing-day count after a counted event on [today] (a local epoch day), where [lastDay] was the last one counted. */
    fun typingDays(days: Int, lastDay: Long, today: Long) = if (today == lastDay) days else days + 1

    /** Up to four nonzero counts, in order, for the card's tiles. */
    fun displayed(counts: LongArray) = counts.withIndex().filter { it.value > 0 }.take(4)
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
