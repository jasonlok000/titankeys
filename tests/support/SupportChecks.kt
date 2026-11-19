package com.titankeys.support

fun main() {
    val firstUse = 1_000L
    val day14 = firstUse + SupportCard.WAIT_MILLIS
    check(!SupportCard.due(day14 - 1, firstUse, false))
    check(SupportCard.due(day14, firstUse, false))
    check(!SupportCard.due(day14, firstUse, true))
    check(!SupportCard.due(firstUse - 1, firstUse, false))
    check(!SupportCard.due(day14, 0, false))
    check(SupportCard.displayed(longArrayOf(0, 1, 2, 3, 4)).map { it.index } == listOf(1, 2, 3))
    check(SupportCard.displayed(longArrayOf(0, 0, 0, 0, 1)).single() == IndexedValue(4, 1L))
    check(SupportCard.displayed(LongArray(5)).isEmpty())

    val ids = SupportProduct.entries.map { it.id }
    check(ids.distinct().size == 12)
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
    check(SupportProduct.TipFive.value(1, 4_000_000, "EUR") == 500L)
    check(SupportProduct.MonthlySmall.value(1, null, null) == 100L)
    check(sha256("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    println("Support checks passed")
}
