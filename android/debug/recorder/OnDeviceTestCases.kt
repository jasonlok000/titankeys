package com.titankeys.keyboard

internal object OnDeviceTestCases {
    data class Case(val name: String, val seed: String, val keys: String, val text: String, val cursor: Int = text.length)

    const val SETTLE_MILLIS = 1_600L

    val entries = listOf(
        Case("Single letter", "", "D 0 Q;U 60 Q", "q"),
        Case("Shift and letter", "", "D 0 SHIFT_RIGHT;D 80 Q;U 140 Q;U 200 SHIFT_RIGHT", "Q"),
        Case("Type a word", "", taps("H", "E", "L", "L", "O"), "hello"),
        Case("Commit with Space", "", taps("H", "E", "L", "L", "O", "SPACE"), "hello "),
        Case("Delete a letter", "abcd", taps("DEL"), "abc"),
        Case("Move the cursor left", "abcd", taps("DPAD_LEFT", "DPAD_LEFT"), "abcd", 2),
        Case("Move and insert", "abcd", taps("DPAD_LEFT", "Q"), "abcqd", 4),
    )

    private fun taps(vararg keys: String) = keys.mapIndexed { index, key ->
        "D ${index * 125} $key;U ${index * 125 + 60} $key"
    }.joinToString(";")
}
