package com.atlas.keyboard

import java.io.File

/**
 * How a horizontal LinearLayout places its children up and down, as Android's LinearLayout does it, so the bar's renders put the
 * modifier chips where the phone does (build #13 showed them whole while the phone cut them in half).
 *
 * - With the row's gravity at the top, a child sits at its top margin, and while the row lines up baselines (Android's default)
 *   it moves down by the lowest baseline among the row's children less its own. A child as tall as the row (MATCH_PARENT) doesn't
 *   move, but its baseline still counts (measureHorizontal counts every child's baseline; layoutHorizontal moves only the others).
 * - Centred, a child sits in the middle, its top margin added and its bottom margin taken off; baselines play no part.
 */
internal object AndroidRow {
    enum class Gravity { Top, CenterVertical }

    /** A child: its height, its baseline under its own top (-1 for none), its top margin, and whether it matches the row's height. */
    class Child(val height: Int, val baseline: Int, val topMargin: Int = 0, val matchParent: Boolean = false)

    /** How a row is set up: its children's gravity, and whether it lines up their baselines. */
    data class Setup(val gravity: Gravity, val baselineAligned: Boolean)

    /** Each child's top, in px under the top of a row [height] px tall, set up as [setup]. */
    fun tops(height: Int, setup: Setup, children: List<Child>): List<Int> {
        val lowest = if (setup.baselineAligned) children.filter { it.baseline >= 0 }.maxOfOrNull { it.baseline } ?: -1 else -1
        return children.map { child ->
            when (setup.gravity) {
                Gravity.Top -> child.topMargin +
                    if (setup.baselineAligned && !child.matchParent && child.baseline >= 0 && lowest >= 0) lowest - child.baseline else 0
                Gravity.CenterVertical -> (height - child.height) / 2 + child.topMargin
            }
        }
    }

    /**
     * How Strip.kt sets up its modifier row (`private val modifierRow = LinearLayout(context).apply { … }`): its gravity, and whether
     * it turns off Android's baseline alignment, which is on unless the row says `isBaselineAligned = false`. Read from the source,
     * so the render lays the row out as the app does; it fails if the row can't be found or has a gravity it doesn't know.
     */
    fun modifierRow(strip: File): Setup {
        val source = strip.readText()
        val start = source.indexOf("private val modifierRow = LinearLayout(context).apply {")
        check(start >= 0) { "Strip.kt's modifier row isn't where the render reads it" }
        val block = source.substring(start, source.indexOf("\n    }\n", start))
        val gravity = when (Regex("""gravity = Gravity\.(\w+)""").find(block)?.groupValues?.get(1)) {
            "TOP" -> Gravity.Top
            "CENTER_VERTICAL" -> Gravity.CenterVertical
            else -> error("Strip.kt's modifier row has a gravity the render doesn't lay out: $block")
        }
        return Setup(gravity, baselineAligned = !block.contains("isBaselineAligned = false"))
    }
}
