package com.titankeys.keyboard

/**
 * How the Sym and fn keys' settings are judged (KeyCheck, #116). The owner's fn key was on Programmable key with Sym key as its
 * function, and Setup said Done (2026-11-24): the mode alone isn't enough.
 */
private var failures = 0

private fun check(ok: Boolean, what: String) {
    if (!ok) {
        failures++
        println("FAIL: $what")
    }
}

private data class Row(val sym: Boolean, val programmable: Boolean, val function: Int?, val problem: String?)

private val TABLE = listOf(
    // Sym: right only on Programmable key > Sym key.
    Row(sym = true, programmable = true, function = KeyCheck.SYM, problem = null),
    Row(sym = true, programmable = true, function = KeyCheck.NONE, problem = "Sym types nothing"),
    Row(sym = true, programmable = true, function = KeyCheck.CTRL, problem = "Sym types Ctrl"),
    Row(sym = true, programmable = true, function = KeyCheck.TAB, problem = "Sym types Tab"),
    Row(sym = true, programmable = true, function = 3, problem = "Sym types another key"),
    Row(sym = true, programmable = false, function = KeyCheck.SYM, problem = "Sym isn't a Programmable key"),
    Row(sym = true, programmable = true, function = null, problem = null),
    // fn: right on Programmable key > Ctrl key, or None (KEYCODE_FUNCTION, which TitanKeys treats as Ctrl).
    Row(sym = false, programmable = true, function = KeyCheck.CTRL, problem = null),
    Row(sym = false, programmable = true, function = KeyCheck.NONE, problem = null),
    Row(sym = false, programmable = true, function = KeyCheck.SYM, problem = "fn types Sym"),
    Row(sym = false, programmable = true, function = KeyCheck.TAB, problem = "fn types Tab"),
    Row(sym = false, programmable = true, function = KeyCheck.SHIFT, problem = "fn types Shift"),
    Row(sym = false, programmable = true, function = KeyCheck.ALT, problem = "fn types Alt"),
    Row(sym = false, programmable = true, function = 42, problem = "fn types another key"),
    Row(sym = false, programmable = false, function = null, problem = "fn isn't a Programmable key"),
    Row(sym = false, programmable = false, function = KeyCheck.CTRL, problem = "fn isn't a Programmable key"),
    Row(sym = false, programmable = true, function = null, problem = null),
)

fun main() {
    for (row in TABLE) {
        val result = KeyCheck.of(row.sym, row.programmable, row.function)
        check(result.problem == row.problem, "$row gave ${result.problem}")
        check(result.ok == (row.problem == null), "$row ok should be ${row.problem == null}")
    }

    // At most once a day while the key is wrong.
    val day = 24L * 60 * 60 * 1000
    check(KeyCheck.warningDue(null, 1_000L), "Never warned: due")
    check(!KeyCheck.warningDue(1_000L, 1_000L + day - 1), "Within the day: not due")
    check(KeyCheck.warningDue(1_000L, 1_000L + day), "A day on: due again")
    check(KeyCheck.warningDue(5_000L, 1_000L), "The clock went back: due")

    if (failures > 0) {
        println("$failures key check(s) failed")
        kotlin.system.exitProcess(1)
    }
    println("Key checks passed")
}
