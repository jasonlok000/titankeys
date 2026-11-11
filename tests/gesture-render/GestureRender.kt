package com.atlas.keyboard

import android.graphics.Canvas
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Draws the strip's steering and selecting displays to a PNG with the real [GestureDrawing], over a stand-in Canvas that draws
 * with Java2D, so they can be looked at without the phone. The density and width match the owner's screenshots of the phone.
 * Each row is one moment: steering on Space, leaning right fast, leaning left slow, and selecting for comparison.
 */
fun main(args: Array<String>) {
    val path = args.firstOrNull() ?: "gesture-render.png"
    val density = DENSITY
    val width = WIDTH
    val strip = (56 * density).toInt()
    val gap = (8 * density).toInt()
    val rows = listOf<Pair<String, (GestureDisplay) -> Unit>>(
        "Steering, on Space" to { d -> steer(d, direction = 0, speed = 0f) },
        "Steering right, fast" to { d -> steer(d, direction = 1, speed = CursorMode.FASTEST_PER_SECOND) },
        "Steering left, slow" to { d -> steer(d, direction = -1, speed = CursorMode.SLOWEST_PER_SECOND * 1.5f) },
        "Selecting" to ::select,
    )
    for (dark in listOf(true, false)) {
        val look = Look(dark, orange = true)
        val image = BufferedImage(width, rows.size * (strip + gap), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = java.awt.Color(look.paper, true)
        g.fillRect(0, 0, image.width, image.height)
        g.dispose()
        val drawing = GestureDrawing(density, 16 * density)
        for ((index, row) in rows.withIndex()) {
            val display = GestureDisplay()
            row.second(display)
            val top = index * (strip + gap)
            val layer = BufferedImage(width, strip, BufferedImage.TYPE_INT_ARGB)
            drawing.draw(Canvas(layer), display, SIDE * density, width - SIDE * density, strip, look)
            val out = image.createGraphics()
            out.drawImage(layer, 0, top, null)
            // A faint line between the rows, so each strip's own edges show.
            out.color = java.awt.Color(0x80808080.toInt(), true)
            out.fillRect(0, top + strip + gap / 2, width, 1)
            out.dispose()
            println("${row.first}: intensity ${display.intensity}, lean ${display.steerLean}, glow ${display.glowWidth} at ${display.glowAlpha}")
        }
        val file = File(if (dark) path else path.replace(".png", "-light.png"))
        ImageIO.write(image, "png", file)
        println("Drawn to $file")
    }
}

/** A tap, then a held touch that steers through real text, with the caret after "quick". */
private fun steer(display: GestureDisplay, direction: Int, speed: Float) {
    display.spaceTapped(1, 0L)
    display.advance(100L, 1f)
    display.cursorFrame(5f, 0, 0f, false, 150L)
    display.cursorText("The quick", " brown fox jumps over the lazy dog")
    var now = 150L
    while (now < 700L) {
        now += 16
        display.cursorFrame(if (direction == 0) 5f else 5f + direction * 3.5f, direction, speed, false, now)
        display.advance(now, 1f)
    }
}

/** Two taps, then a held touch that selects "quick" and leans right. */
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
