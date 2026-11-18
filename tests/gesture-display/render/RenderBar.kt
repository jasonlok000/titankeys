// Renders the delete's dots on the bar to pictures, to look at by eye: one strip of frames for each case. render.sh runs it.
package com.titankeys.keyboard

import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private const val WIDTH = 1076
private const val DENSITY = 2.6f
private val HEIGHT = (56 * DENSITY).toInt()
private const val SIDE = 16f

/** One frame of the bar: the suggestions as the strip shows them, then the display over them. */
private fun frame(display: GestureDisplay, look: Look, label: String): BufferedImage {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = java.awt.Color(look.paper, true)
    g.fillRect(0, 0, WIDTH, HEIGHT)
    g.color = java.awt.Color(look.ink, true)
    for ((index, word) in listOf("going", "good", "gonna").withIndex()) {
        // As the bar has them: all three one size, the best word in the middle bold.
        g.font = Font(Font.SANS_SERIF, if (index == 1) Font.BOLD else Font.PLAIN, (20 * DENSITY).toInt())
        val x = WIDTH / 6 * (2 * index + 1) - g.fontMetrics.stringWidth(word) / 2
        g.drawString(word, x, HEIGHT / 2 + g.fontMetrics.ascent / 3)
    }
    GestureDrawing(DENSITY, 16 * DENSITY).draw(android.graphics.Canvas(image), display, SIDE, WIDTH - SIDE, HEIGHT, look,
        StripFit.LINE_DP * DENSITY, HEIGHT - StripFit.EDGE_DP * DENSITY, 20 * DENSITY)
    g.color = java.awt.Color(0x88, 0x88, 0x88)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, 22)
    g.drawString(label, 8, 24)
    g.dispose()
    return image
}

/** Plays [setup]'s deletes on a display and keeps a frame each [every] ms, until the dots are over. */
private fun sheet(name: String, out: File, every: Long, setup: (GestureDisplay, Long) -> Unit) {
    val look = Look(orange = true)
    val display = GestureDisplay()
    val frames = ArrayList<BufferedImage>()
    var now = 1_000L
    setup(display, now)
    while (now < 6_000 && (display.scene != GestureDisplay.Scene.None || now == 1_000L)) {
        setup(display, now)
        display.advance(now, 1f)
        if ((now - 1_000) % every == 0L) frames += frame(display, look, "$name  ${now - 1_000} ms  dots ${display.dots}")
        now += 16
        // The frames fall on the step, so step to the next one exactly.
        now = 1_000 + ((now - 1_000 + 15) / 16) * 16
    }
    val sheet = BufferedImage(WIDTH, HEIGHT * frames.size + 4 * frames.size, BufferedImage.TYPE_INT_ARGB)
    val g = sheet.createGraphics()
    for ((index, image) in frames.withIndex()) g.drawImage(image, 0, index * (HEIGHT + 4), null)
    g.dispose()
    ImageIO.write(sheet, "png", File(out, "$name.png"))
    println("$name: ${frames.size} frames")
}

fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "render-out").apply { mkdirs() }
    // Comparison (#89): a hold of ten words over real words, then its lift, the same in every version.
    sheet("compare-hold", out, 64) { display, now ->
        if (now == 1_000L) {
            display.deleteHeld(200, now, .4f)
            display.deleteText("one two three four five six seven eight nine ten eleven twelve ")
        }
        if (now in 1_000L..2_728L && (now - 1_000L) % 192L == 0L) display.wordDeleted(now, 5)
        if (now == 2_920L) display.deleteEnded(now)
    }
    // One flick of five letters, as before: the words faded behind, not covered.
    sheet("one-flick", out, 80) { display, now -> if (now == 1_000L) display.wordDeleted(now, 5) }
    // Three quick flicks: one stream that grows, and never starts over.
    sheet("three-flicks", out, 80) { display, now ->
        when (now) {
            1_000L -> display.wordDeleted(now, 5)
            1_256L -> display.wordDeleted(now, 4)
            1_512L -> display.wordDeleted(now, 7)
        }
    }
    // A long delete with the experiment "Dots speed up when long" on.
    sheet("speed-up", out, 64) { display, now ->
        if (now == 1_000L) { display.speedUp = true; display.wordDeleted(now, 30) }
    }
    // A short hold, a short delete: its words go orange and turn into dots at the lift, as always (#89).
    sheet("hold-short", out, 64) { display, now ->
        if (now == 1_000L) display.deleteHeld(200, now, .4f)
        if (now in 1_000L..1_960L && (now - 1_000L) % 192L == 0L) display.wordDeleted(now, 5)
        if (now == 2_008L) display.deleteEnded(now)
    }
    // A flick at the end of a long line: a row the bar's width, not 2,000 dots.
    sheet("long-flick", out, 80) { display, now -> if (now == 1_000L) display.wordDeleted(now, 2_000) }
}
