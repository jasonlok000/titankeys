package com.titankeys.support

import java.security.MessageDigest

internal enum class SupportProduct(val id: String, val monthly: Boolean, val cents: Long) {
    MonthlySip("support_monthly_sip", true, 50),
    MonthlySmall("support_monthly_small", true, 100),
    MonthlyFull("support_monthly_full", true, 200),
    TipSip("support_tip_sip", false, 50),
    TipSmall("support_tip_small", false, 100),
    TipFull("support_tip_full", false, 200),
    TipUnit("support_tip_unit", false, 100),
    TipThree("support_tip_3", false, 300),
    TipFive("support_tip_5", false, 500),
    TipTen("support_tip_10", false, 1_000),
    TipTwenty("support_tip_20", false, 2_000),
    TipFifty("support_tip_50", false, 5_000);

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
    fun due(now: Long, firstRun: Long, seen: Boolean) =
        !seen && firstRun > 0 && now >= firstRun && now - firstRun >= WAIT_MILLIS
}

internal data class SupportCredit(val id: String, val productId: String, val quantity: Int,
    val previousCents: Long, val totalCents: Long)

/** A Play token counts once, even if it's delivered again after a crash or while the purchase is reconciled. */
internal fun supportCredit(processed: Set<String>, totalCents: Long, token: String, product: SupportProduct, quantity: Int,
    micros: Long? = null, currency: String? = null): SupportCredit? {
    if (token.isBlank() || quantity !in 1..10_000) return null
    val id = sha256(token)
    if (id in processed) return null
    return SupportCredit(id, product.id, quantity, totalCents, totalCents + product.value(quantity, micros, currency))
}
