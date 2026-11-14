package com.atlas.keyboard

import java.io.File
import javax.imageio.ImageIO

/**
 * The bar's frames that move or change with the keys, on the line, in [BarLine]'s render of the phone's screen (owner, 2026-11-13,
 * on build #13: "With the illusion shift, the modifier bar is not done well… The position and scaling of the word menu effects
 * seems inconsistent. Like the book seems to be much higher up than the heart animation"):
 * - every state of the modifier bar: Shift once, held and locked (Caps), Alt once and held, Ctrl and its shortcuts, together, and
 *   with a finger resting on a key, laid out as Android lays out Strip's modifier row ([AndroidRow]);
 * - the same states laid out as build #13 had the row, which the phone cut in half: the check must fail on them;
 * - keyframes of the word menu's six effects, on the whole screen, as the strip and its effects layer draw them.
 */
internal object BarFrames {
    private val SHORTCUTS = "A all  C copy  X cut  V paste  Z undo  Y redo"

    /** Every modifier bar state: which chips show, filled for a held key, and the hint. */
    fun modifierStates(): List<BarLine.State> {
        fun state(name: String, caption: String, chips: List<Pair<String, Boolean>>, hint: String) =
            BarLine.State(name, caption) { look, g -> BarLine.chips(look, g, chips, hint) }
        return listOf(
            state("mod-shift-once", "Shift pushed once", listOf("Shift" to false), ""),
            state("mod-shift-held", "Shift held", listOf("Shift" to true), ""),
            state("mod-caps", "Shift locked: Caps", listOf("Caps" to false), ""),
            state("mod-alt-once", "Alt pushed once", listOf("Alt" to false), ""),
            state("mod-alt-held", "Alt held", listOf("Alt" to true), ""),
            state("mod-ctrl", "Ctrl held, and its shortcuts", listOf("Ctrl" to true), SHORTCUTS),
            state("mod-ctrl-shift", "Ctrl held with Shift on once, and the shortcuts", listOf("Ctrl" to true, "Shift" to false), SHORTCUTS),
            state("mod-ctrl-resting", "Ctrl held, a finger resting on C", listOf("Ctrl" to true), "C Copy"),
            state("mod-shift-alt", "Shift and Alt pushed once", listOf("Shift" to false, "Alt" to false), ""),
            state("mod-caps-alt", "Caps, and Alt held", listOf("Caps" to false, "Alt" to true), ""),
            state("mod-shift-resting", "Shift once, a finger resting on G: the key's preview", listOf("Shift" to false), "G"),
            state("mod-alt-resting", "Alt once, a finger resting on a key: its symbol", listOf("Alt" to false), "|"),
            state("mod-caps-resting", "Caps, a finger resting on J", listOf("Caps" to false), "J"),
        )
    }

    /**
     * The guide's moving help on the bar, each a frame of its motion: the ghost finger's hold on the middle word, the fix demo's
     * word rising into its place (from no lower than its tails may go), and a pointed word bobbing up from its line. The words
     * that move aren't on the line mid-motion, so only their tails and the finger's ring are checked, against the screen's edge.
     */
    fun guideStates(): List<BarLine.State> {
        val shown = listOf("going", "your", "jumpy")
        val states = ArrayList<BarLine.State>()
        for (hold in listOf(0f, .5f, 1f)) states += BarLine.State("guide-finger-%03d".format((hold * 100).toInt()),
            "The guide's ghost finger on the middle word, its hold %d%% run".format((hold * 100).toInt())) { look, g ->
            BarLine.words(look, g, shown)
            BarLine.finger(look, g, hold)
        }
        for (done in listOf(0f, .5f, 1f)) states += BarLine.State("guide-fix-%03d".format((done * 100).toInt()),
            "The guide's fix rising into its place, %d%% of the way, fading in".format((done * 100).toInt())) { look, g ->
            // As Strip's fix demo: from at most 8 dp under its place, and no lower than its tails may go.
            val foot = look.baseline(look.wordPx) + look.wordPx * StripFit.DESCENT
            val rise = if (look.after) minOf(look.dp(8).toFloat(), look.floor - foot).coerceAtLeast(0f) else look.dp(8).toFloat()
            BarLine.words(look, g, listOf("", "typing", ""), measured = done == 1f, middle = rise * (1 - done), alpha = done)
        }
        for (phase in listOf(.25f, .5f)) states += BarLine.State("guide-point-%03d".format((phase * 100).toInt()),
            "The guide pointing at the middle word, %d%% through a bob: up from its line, never under it".format((phase * 100).toInt())) { look, g ->
            val reach = look.dp(3).toFloat()
            val y = if (look.after) -reach * (1f - kotlin.math.cos(phase * 2 * Math.PI).toFloat()) else -reach * kotlin.math.sin(phase * 2 * Math.PI).toFloat()
            BarLine.words(look, g, shown, measured = false, middle = y)
        }
        return states
    }

    /**
     * The modifier states laid out as build #13 had the row, gravity at the top with Android's baseline alignment on: the phone put
     * the chips' text on the hint's baseline and cut their boxes in half. The render's check must catch it at every size, or it
     * isn't modelling the phone. Drawn to build13/ beside the states as they are now.
     */
    fun build13(out: File): List<String> {
        val caught = ArrayList<String>()
        for (size in FixedScale.Size.entries) {
            val dir = File(out, "build13/${size.name.lowercase()}").apply { mkdirs() }
            var cut = 0
            for (state in modifierStates()) {
                val look = BarLine.Look(size, BarLine.Era.After).apply { row = AndroidRow.Setup(AndroidRow.Gravity.Top, true) }
                val image = BarLine.picture(look, state)
                ImageIO.write(image, "png", File(dir, "${state.name}.png"))
                val lowest = look.boxes.maxOf { it.bottom }
                if (lowest > BarLine.BAR - look.edge) cut++
                if (state.name == "mod-shift-once") caught += "${size.name}: build #13's Shift chip from ${(lowest - look.chipHeight).toInt()} to ${lowest.toInt()} px in the 88 px bar"
            }
            check(cut == modifierStates().size) { "${size.name}: laid out as build #13, only $cut of ${modifierStates().size} modifier states break the check" }
        }
        return caught
    }

    /**
     * The Sym panel, its three rows of keys over the band, as SymPanel lays them out, before (rows 46 dp apart, the band's text on
     * the bar's line under them) and after (the four evenly spaced, the panel's height, the top row and the band's text where they
     * were: SymPanel.spaceRows), on the symbol, clipboard and quick settings pages, with the bar under the panel and with the band
     * at the screen's edge. Drawn to panel/<page>-<where>-<before|after>.png. Returns the rows' spacing, to print.
     */
    fun panel(out: File): List<String> {
        val dir = File(out, "panel").apply { mkdirs() }
        val unit = BarLine.UNIT
        val rowHeight = Math.round(46 * unit)
        val bandHeight = Math.round(32 * unit)
        val total = 3 * rowHeight
        val said = ArrayList<String>()
        val pages = mapOf(
            "symbols" to listOf(listOf("", "~", "`", "|", "•", "√", "π", "÷", "×", "1/2"), listOf("", "€", "£", "¥", "₩", "^", "°", "=", "{", ""),
                listOf("Shift", "\\", "©", "®", "™", "✓", "[", "]", "", "")),
            "clipboard" to emptyList(),
            "quick" to listOf(listOf("", "Haptic", "Sound", "Swipe", "Fixes", "Caps", "Shorth", "Snip", "Exact", ""),
                listOf("", "Words", "Clip", "Theme", "Size", "Orange", "Idle", "Guide", "Back", ""), listOf("", "", "", "", "", "", "", "", "", "")),
        )
        for ((page, rows) in pages) for (overBar in listOf(true, false)) for (even in listOf(false, true)) {
            val look = BarLine.Look(FixedScale.Size.Normal, BarLine.Era.After)
            val state = BarLine.State("$page-${if (overBar) "over-bar" else "at-edge"}-${if (even) "after" else "before"}",
                "The Sym panel's $page page, ${if (overBar) "the bar under it" else "the band at the screen's edge"}, ${if (even) "rows evenly spaced" else "rows as they were"}") { l, g ->
                if (overBar) BarLine.chips(l, g, emptyList(), "")
                val bottom = if (overBar) 0 else BarLine.BAR
                val bandTop = bottom - bandHeight
                val gridTop = bandTop - total
                // SymPanel.bandLine and spaceRows.
                val line = if (overBar) bandHeight / 2f else bandHeight - StripFit.LINE_ABOVE_DP * unit
                val step = (total + line - rowHeight / 2f) / 3
                val height = if (even) Math.round(step) else rowHeight
                val padTop = if (even) Math.round(rowHeight / 2f - height / 2f) else 0
                val middles = (0..2).map { gridTop + padTop + height / 2f + it * height }
                val keys = KeyGeometry(BarLine.WIDTH.toFloat())
                for ((index, middle) in middles.withIndex()) {
                    if (page == "clipboard") {
                        // Two wide fields over each half of the row, as the clipboard page has them, 3 dp in from the row.
                        for (half in 0..1) {
                            val left = keys.cell(1 + half * 4).first + 2 * unit
                            val right = keys.cell(4 + half * 4).second - 2 * unit
                            g.color = java.awt.Color(0x33, 0x2A, 0x24)
                            g.fill(java.awt.geom.RoundRectangle2D.Float(left, middle - height / 2f + 3 * unit, right - left, height - 6 * unit, 32f, 32f))
                            BarLine.text(g, BarLine.regular, 15 * unit, "Clip ${index * 2 + half + 1}: meet at six", left + 8 * unit,
                                middle + 15 * unit * StripFit.X_HEIGHT / 2, java.awt.Color.WHITE)
                        }
                        continue
                    }
                    for ((column, label) in rows[index].withIndex()) {
                        if (label.isEmpty()) continue
                        val size = (if (page == "quick") 13f else 22f) * unit
                        val (l, r) = keys.cell(column)
                        val wide = BarLine.regular.deriveFont(size).getStringBounds(label, java.awt.font.FontRenderContext(null, true, true)).width.toFloat()
                        BarLine.text(g, BarLine.regular, size, label, (l + r) / 2 - wide / 2, middle + size * StripFit.X_HEIGHT / 2, java.awt.Color.WHITE)
                    }
                }
                BarLine.band(l, g, if (page == "quick") "quick" else if (page == "clipboard") "clipboard" else "first", overBar)
                val bandText = bandTop + line
                val gaps = (middles + bandText).zipWithNext { a, b -> b - a }
                if (!overBar || page == "symbols") said += "Panel, $page, ${if (overBar) "bar under it" else "at the edge"}, ${if (even) "after" else "before"}: " +
                    "row to row ${gaps.joinToString(", ") { "%.1f".format(it) }} px"
            }
            ImageIO.write(BarLine.picture(look, state, 300), "png", File(dir, "${state.name}.png"))
        }
        return said.distinct()
    }

    /**
     * The clip offer and the automatic capital's arrow laid out as build #19 had them (see [BarLine.Look.build19]), beside the states
     * as they are now: build19/<size>/<state>.png. A lone clip with tails must come out cut at every size, as on the phone, or the
     * render's check isn't modelling it. Returns what it found, to print.
     */
    fun build19(out: File, states: List<BarLine.State>): List<String> {
        val said = ArrayList<String>()
        val names = listOf("clip", "clip-phone", "clip-jpg", "clip-hint", "capital", "capital-clip")
        for (size in FixedScale.Size.entries) {
            val dir = File(out, "build19/${size.name.lowercase()}").apply { mkdirs() }
            for (state in states.filter { it.name in names }) {
                val look = BarLine.Look(size, BarLine.Era.After).apply { build19 = true }
                ImageIO.write(BarLine.picture(look, state), "png", File(dir, "${state.name}.png"))
                if (state.name in listOf("clip-phone", "clip-jpg", "capital-clip")) {
                    check(look.cut.isNotEmpty()) { "${size.name}, ${state.name}: laid out as build #19, the clip must come out cut" }
                    said += "${size.name}, ${state.name}, as build #19: ${look.cut.joinToString()}"
                }
            }
        }
        return said
    }

    /** A step of an effect: the button, the rung it's pressed from, and the column the finger rests on. */
    private class Move(val button: MenuKeys.Button, val from: MenuKeys.Step, val column: Int)

    /**
     * Keyframes of the six effects at every size, on the whole screen: the resting finger's preview, then the press and nine
     * frames to 1.4 s, as the strip plays them (the finger stays on its key, so the pill shows the next rung's button). Each frame
     * is effects/<size>/<effect>-<nn>.png, with its caption in effects/captions.txt.
     */
    fun effects(out: File) {
        val moves = listOf(
            Move(MenuKeys.Button.Save, MenuKeys.Step.New, MenuKeys.UP),
            Move(MenuKeys.Button.Unsave, MenuKeys.Step.Saved, MenuKeys.DOWN),
            Move(MenuKeys.Button.Favourite, MenuKeys.Step.Saved, MenuKeys.UP),
            Move(MenuKeys.Button.Unfavourite, MenuKeys.Step.Favourite, MenuKeys.DOWN),
            Move(MenuKeys.Button.Block, MenuKeys.Step.New, MenuKeys.DOWN),
            Move(MenuKeys.Button.Unblock, MenuKeys.Step.Blocked, MenuKeys.UP),
        )
        val times = listOf(0, 80, 160, 260, 380, 520, 700, 1000, 1400)
        val captions = ArrayList<String>()
        for (size in FixedScale.Size.entries) {
            val dir = File(out, "effects/${size.name.lowercase()}").apply { mkdirs() }
            for (move in moves) {
                val effect = move.button.name.lowercase()
                val next = MenuScene.next(move.button)!!
                var haptic = -1
                fun frame(index: Int, caption: String, step: MenuKeys.Step, play: (MenuScene) -> Unit) {
                    val look = BarLine.Look(size, BarLine.Era.After)
                    val state = BarLine.State("$effect-%02d".format(index), caption) { l, g ->
                        BarLine.menu(l, g, "whatever", step, move.column, play = play)
                    }
                    ImageIO.write(BarLine.picture(look, state, BarLine.SCREEN - BarLine.BAR), "png", File(dir, "${state.name}.png"))
                    captions += "${size.name.lowercase()}/${state.name}\t${size.name}, ${move.button.name}: $caption"
                }
                fun rest(scene: MenuScene) {
                    scene.hover(move.button)
                    repeat(48) { scene.step(1 / 120f) }
                }
                frame(0, "the resting finger's preview", move.from) { rest(it) }
                for ((index, t) in times.withIndex()) frame(index + 1, "+$t ms", next) { scene ->
                    rest(scene)
                    val pressed = scene.time
                    scene.onHaptic = { _, label -> if (label != MenuScene.PRESS) haptic = ((scene.time - pressed) * 1000).toInt() }
                    scene.press(move.button)
                    var now = 0
                    while (now < t) { scene.step(1 / 120f); now += 1000 / 120 }
                }
                // Mark the frame at or just after the effect's own haptic.
                val at = times.indexOfFirst { it >= haptic }
                if (haptic >= 0 && at >= 0) {
                    val key = "${size.name.lowercase()}/$effect-%02d".format(at + 1)
                    val line = captions.indexOfFirst { it.startsWith("$key\t") }
                    if (line >= 0) captions[line] = captions[line] + " (its haptic at $haptic ms)"
                }
            }
        }
        File(out, "effects/captions.txt").writeText(captions.joinToString("\n") + "\n")
    }
}
