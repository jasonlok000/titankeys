package com.atlas.keyboard

import kotlin.math.max

/**
 * Where a slide's content block sits, panel open or not, as plain arithmetic on pixel sizes, so tests/guide can check it without
 * Android. The guide's column is the top bar, the room over the block, the block (title, instruction, field, Skip or Next), the
 * room under it, then the drawing of the keys on the column's bottom padding, the keyboard's own room. The two rooms share what's
 * free equally, as LinearLayout's weights do, the upper one rounded down.
 *
 * Opening a panel (Sym's symbols, the emoji, the clipboard, the quick settings) moves nothing on a slide (Master, 2026-11-13,
 * build #10: the title, the instruction and the field dropped 33 dp as Sym opened, and rose as it closed, because the drawing of
 * the keys went and the bottom padding grew to the panel's room). The bottom padding stays the keyboard's own, and the drawing
 * hides under the panel but keeps its room. Only a panel whose top would come within [Column.gap] of the block makes the block
 * rise, and only that far. On the Titan 2 Elite it never does: every page of the panel is the same height, about 200 px over the
 * keyboard's bar, and the drawing of the keys and the 24 dp over it take about 340 px.
 */
object GuideRoom {
    /** A slide's column, in px. */
    class Column(
        /** The column's height, the screen's. */
        val height: Int,
        /** Its top padding: the status bar. */
        val top: Int,
        /** Everything over the room above the block, with their margins: the top bar, and the room's 24 dp. */
        val above: Int,
        /** The block's height. */
        val block: Int,
        /** Everything between the room under the block and the drawing of the keys, with their margins: the room's 24 dp. */
        val below: Int,
        /** The drawing of the keys' height, which it keeps under a panel; 0 when it has none. */
        val keys: Int,
        /** The keyboard's own bottom inset, without a panel: the column's bottom padding on a slide. */
        val keyboard: Int,
        /** The bottom inset with a panel open, or 0 with none. */
        val panel: Int,
        /** How far the block keeps over an open panel's top: 24 dp. */
        val gap: Int,
    )

    /** The room over the block with the panel closed: half of what's free, rounded down, as LinearLayout shares it. */
    fun centred(c: Column): Int = max(0, c.height - c.top - c.keyboard - c.above - c.block - c.below - c.keys) / 2

    /**
     * The room over the block: as with the panel closed, or, if an open panel's top would come within [Column.gap] of the block,
     * only as much less as the block must rise to stay clear of it.
     */
    fun over(c: Column): Int {
        val centred = centred(c)
        if (c.panel <= c.keyboard) return centred
        val clash = c.top + c.above + centred + c.block + c.gap - (c.height - c.panel)
        return max(0, centred - max(0, clash))
    }

    /** Where the block's top sits. */
    fun blockTop(c: Column): Int = c.top + c.above + over(c)
}
