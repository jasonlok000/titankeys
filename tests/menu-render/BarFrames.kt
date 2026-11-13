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
