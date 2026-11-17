package com.titankeys.keyboard

/**
 * What the bar shows, logged for the phone checks in TitanKeys Debug only, under the tag [TAG], and only while
 * `adb shell setprop log.tag.TitanKeysBar D` is set: the owner's own typing logs nothing. The lines
 * (tools/dev/touch/interactions_batch.py reads them):
 *
 *     bar: IM LEFT | MIDDLE | RIGHT     each time the bar's words change, "-" for an empty place
 *     bar notice: TEXT                  a line on the bar, as "Warming up Yale…"
 *
 * IM is the input method's id (en, yue-yale, ja, ko). The middle is what Space types (#98).
 */
object BarLog {
    const val TAG = "TitanKeysBar"

    /** [ranked], best first, as the bar places them: the second on the left, the first in the middle, the third on the right. */
    fun places(ranked: List<String>): String = listOf(1, 0, 2).joinToString(" | ") { ranked.getOrNull(it)?.takeIf(String::isNotEmpty) ?: "-" }

    /** The log line for [shown], a [Strip.log] line, typed in input method [id]. */
    fun line(id: String, shown: String): String =
        if (shown.startsWith(NOTICE)) "bar notice: ${shown.removePrefix(NOTICE)}" else "bar: $id $shown"

    private const val NOTICE = "notice: "
}
