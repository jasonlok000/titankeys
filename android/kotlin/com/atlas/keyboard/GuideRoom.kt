package com.atlas.keyboard

import kotlin.math.max

/**
 * Where a slide's or the playground's content block sits, panel open or not, as plain arithmetic on pixel sizes, so tests/guide can
 * check it without Android. The guide's column is the top bar (the playground's header), the room over the block, the block (title,
 * instruction, field, and on a slide Skip or Next), the room under it, then the drawing of the keys on the column's bottom padding,
 * the keyboard's own room. The two rooms share what's free equally, as LinearLayout's weights do, the upper one rounded down.
 *
 * Opening a panel (Sym's symbols, the emoji, the clipboard, the quick settings) moves nothing, on a slide and on the playground:
 * there a panel is "still". On build #10 a slide's title, instruction and field dropped 51 px as Sym opened, and rose as it closed
 * (Master, 2026-11-13), because the drawing of the keys went and the bottom padding grew to the panel's room. On build #12 the
 * playground did the same, and for one frame at every opening and closing it showed 139 px lower than closed: the keyboard says a
 * panel is open, and its window takes the panel's room, at two different moments, and in between the drawing of the keys had
 * gone while the padding was still the keyboard's own. Now the bottom padding stays the keyboard's own, and the drawing hides
 * under the panel but keeps its room, whichever comes first. See [keys], [padding] and [room].
 *
 * Only a panel whose top would come within [Column.gap] of the block makes the block rise, and only that far. On the Titan 2
 * Elite it never does, on a slide or on the playground: every page of the panel is the same height, under 210 px over the
 * keyboard's bar, and the drawing of the keys and the 24 dp over it take over 310 px.
 */
object GuideRoom {
    /** A slide's or the playground's column, in px. */
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
        /** The drawing of the keys' height, which it keeps under a panel; 0 when it has none or it's gone. */
        val keys: Int,
        /** The column's bottom padding: the keyboard's own bottom inset, without a panel, where a panel moves nothing. */
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

    /** The drawing of the keys: shown, hidden but keeping its room, or gone. */
    enum class Keys { Shown, Hidden, Gone }

    /**
     * The drawing of the keys, [height] tall, with [panel] whether the keyboard says a panel is open or its window has a panel's
     * room, whichever comes first. With no panel it shows; under one it hides, as the panel shows the same keys itself (owner, v5.2),
     * so its rows never peek over the panel or get cut by it. Where a panel moves nothing ([still]) it keeps its room; elsewhere it
     * goes. At [end], or 0 tall, it's gone.
     */
    fun keys(height: Int, still: Boolean, panel: Boolean, end: Boolean): Keys = when {
        height == 0 || end -> Keys.Gone
        !panel -> Keys.Shown
        still -> Keys.Hidden
        else -> Keys.Gone
    }

    /**
     * The column's bottom padding: the keyboard's own, [keyboard], where a panel moves nothing ([still]); elsewhere the panel's
     * room, [panel], while the keyboard says a panel is open.
     */
    fun padding(still: Boolean, panelOpen: Boolean, keyboard: Int, panel: Int): Int = if (still || !panelOpen) keyboard else panel

    /**
     * The keyboard's room at the bottom, from the window's bottom inset. [steady] is the most room the keyboard itself took while
     * it shows here, kept so that the keys never jump when its bar hides for a moment as a slide changes (owner, 2026-11-11).
     * [sized]: the inset is more than [extra] over that, a panel's room, which can come before the keyboard says a panel is open.
     * [keyboard] is the keyboard's own room, never a panel's (owner, v5: keeping a panel's room after it closed squashed the keys);
     * [panel] the inset while a panel is open or its room has come, else 0.
     */
    class Room(val steady: Int, val keyboard: Int, val panel: Int, val sized: Boolean)

    /** [Room] for a bottom [inset], with [panelOpen] what the keyboard last said, and [keep] whether [steady] is kept here. */
    fun room(inset: Int, panelOpen: Boolean, steady: Int, extra: Int, keep: Boolean): Room {
        val sized = steady > 0 && inset > steady + extra
        val panel = panelOpen || sized
        val held = if (!keep) 0 else if (panel) steady else max(steady, inset)
        return Room(held, max(held, if (panel) 0 else inset), if (panel) max(inset, held) else 0, sized)
    }
}
