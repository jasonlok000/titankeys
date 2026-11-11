package com.atlas.keyboard

import android.graphics.Canvas
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Draws the strip's steering and selecting displays to PNGs with the real [GestureDrawing], over a stand-in Canvas that draws
 * with Java2D, so they can be looked at without the phone. The density and width match the owner's screenshots of the phone.
 * Each moment gets its own PNG in the folder given, dark and light, and all.png and all-light.png put them in one sheet.
 */
fun main(args: Array<String>) {
    val folder = File(args.firstOrNull() ?: "gesture-render")
    folder.mkdirs()
    val strip = (56 * DENSITY).toInt()
    val gap = (8 * DENSITY).toInt()
    val text = "The quick brown fox jumps over the lazy dog"
    val moments = listOf<Pair<String, (GestureDisplay) -> Unit>>(
        "1-steer-rest" to { d -> steer(d, "The quick", text.drop(9), direction = 0, speed = 0f) },
        "2-steer-left" to { d -> steer(d, "The quick", text.drop(9), direction = -1, speed = CursorMode.SLOWEST_PER_SECOND * 1.5f) },
        "3-steer-right" to { d -> steer(d, "The quick", text.drop(9), direction = 1, speed = CursorMode.FASTEST_PER_SECOND) },
        "4-steer-text-start" to { d -> steer(d, "", text, direction = -1, speed = CursorMode.SLOWEST_PER_SECOND * 4) },
        "5-steer-text-end" to { d -> steer(d, text, "", direction = 1, speed = CursorMode.SLOWEST_PER_SECOND * 4) },
        "6-select" to ::select,
    )
    for (dark in listOf(true, false)) {
        val look = Look(dark, orange = true)
        val drawing = GestureDrawing(DENSITY, 16 * DENSITY)
        val suffix = if (dark) "" else "-light"
        val sheet = BufferedImage(WIDTH, moments.size * (strip + gap), BufferedImage.TYPE_INT_ARGB)
        for ((index, moment) in moments.withIndex()) {
            val display = GestureDisplay()
            moment.second(display)
            val image = BufferedImage(WIDTH, strip, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            g.color = java.awt.Color(look.paper, true)
            g.fillRect(0, 0, WIDTH, strip)
            g.dispose()
            drawing.draw(Canvas(image), display, SIDE * DENSITY, WIDTH - SIDE * DENSITY, strip, look)
            ImageIO.write(image, "png", File(folder, "${moment.first}$suffix.png"))
            val out = sheet.createGraphics()
            out.color = java.awt.Color(look.paper, true)
            out.fillRect(0, index * (strip + gap), WIDTH, strip + gap)
            out.drawImage(image, 0, index * (strip + gap), null)
            // A faint line between the moments, so each strip's own edges show.
            out.color = java.awt.Color(0x80808080.toInt(), true)
            out.fillRect(0, index * (strip + gap) + strip + gap / 2, WIDTH, 1)
            out.dispose()
            println("${moment.first}$suffix: intensity ${display.intensity}, lean ${display.steerLean}, glow ${display.glowWidth} at ${display.glowAlpha}")
        }
        ImageIO.write(sheet, "png", File(folder, "all$suffix.png"))
    }
    println("Drawn to $folder")
}

/** A tap, then a held touch that steers through real text, with the caret between [before] and [after]. */
private fun steer(display: GestureDisplay, before: String, after: String, direction: Int, speed: Float) {
    display.spaceTapped(1, 0L)
    display.advance(100L, 1f)
    display.cursorFrame(5f, 0, 0f, false, 150L)
    display.cursorText(before, after)
    var now = 150L
    while (now < 700L) {
        now += 16
        display.cursorFrame(if (direction == 0) 5f else 5f + direction * 3.5f, direction, speed, false, now)
        display.advance(now, 1f)
    }
}

/** Two taps, then a held touch that selects "quick". */
private fun select(display: GestureDisplay) {
    display.spaceTapped(1, 0L)
    display.spaceTapped(2, 150L)
    display.advance(250L, 1f)
    display.cursorFrame(5f, 0, 0f, true, 300L)
    display.wordText("The ", 5, " brown fox jumps over the lazy dog")
    var now = 300L
    while (now < 900L) {
        now += 16
        display.cursorFrame(5f, 0, 0f, true, now)
        display.advance(now, 1f)
    }
}

/** As on the owner's screenshots: 1076 px across at about 1.55 px to a dp, with the strip's rounded-corner margin. */
private const val DENSITY = 1.55f
private const val WIDTH = 1076
private const val SIDE = 16f
