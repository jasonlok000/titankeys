package com.titankeys.support

fun main() {
    // About ten typing days, each counted once; never again once seen.
    check(!SupportCard.due(9, false))
    check(SupportCard.due(10, false))
    check(!SupportCard.due(30, true))
    check(SupportCard.typingDays(3, 100, 100) == 3)
    check(SupportCard.typingDays(3, 100, 101) == 4)
    check(SupportCard.typingDays(0, -1, 100) == 1)
    check(SupportCard.displayed(longArrayOf(0, 1, 2, 3, 4)).map { it.index } == listOf(1, 2, 3, 4))
    check(SupportCard.displayed(longArrayOf(0, 0, 0, 0, 1)).single() == IndexedValue(4, 1L))
    check(SupportCard.displayed(LongArray(5)).isEmpty())

    val ids = SupportProduct.entries.map { it.id }
    check(ids.distinct().size == 7)
    check(ids.none { it.matches(Regex("support_tip_[0-9]+")) })
    check(SupportProduct.entries.count { it.monthly } == 3)
    val unit = SupportProduct.TipUnit
    val actual = checkNotNull(supportCredit(emptySet(), 480, "test-purchase", unit, 7, 1_000_000, "USD"))
    check(actual.previousCents == 480L && actual.totalCents == 1_180L)
    check(actual.quantity == 7 && actual.productId == "support_tip_unit")
    check(actual.id == sha256("test-purchase") && actual.id.length == 64)
    check(supportCredit(setOf(actual.id), actual.totalCents, "test-purchase", unit, 7) == null)
    check(supportCredit(emptySet(), 0, "", unit, 1) == null)
    check(supportCredit(emptySet(), 0, "test-pending", unit, 0) == null)
    check(SupportProduct.TipSip.value(1, 490_000, "USD") == 49L)
    check(SupportProduct.TipFull.value(1, 4_000_000, "EUR") == 250L)
    check(SupportProduct.TipUnit.value(7, 1_000_000, "USD") == 700L)
    check(SupportProduct.MonthlySmall.value(1, null, null) == 100L)
    check(sha256("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")

    // Each paid month of a subscription counts once, keyed by its order; a tip by its token.
    val monthly = SupportProduct.MonthlyFull
    val month0 = supportKey("sub-token", "GPA.1234", monthly = true)
    val month1 = supportKey("sub-token", "GPA.1234..0", monthly = true)
    check(month0 != month1 && month0 == "order:GPA.1234")
    check(supportKey("tip-token", "GPA.9", monthly = false) == "tip-token")
    check(supportKey("sub-token", null, monthly = true) == "sub-token")
    val first = checkNotNull(supportCredit(emptySet(), 0, month0, monthly, 1))
    val second = checkNotNull(supportCredit(setOf(first.id), first.totalCents, month1, monthly, 1))
    check(second.totalCents == 500L)
    check(supportCredit(setOf(first.id, second.id), second.totalCents, month1, monthly, 1) == null)
    // A ledger from before: the token's credit becomes this month's order, without a second credit; the next renewal counts.
    check(keyedByOldToken(setOf(sha256("sub-token")), "sub-token", month1))
    check(!keyedByOldToken(setOf(sha256(month1)), "sub-token", month1))
    check(!keyedByOldToken(emptySet(), "sub-token", month1))
    check(!keyedByOldToken(setOf(sha256("tip-token")), "tip-token", "tip-token"))
    supportWebChecks()
    println("Support checks passed")
}
