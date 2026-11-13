package com.atlas.keyboard

import android.graphics.Canvas
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.min

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
        "1-steer-rest" to { d -> steer(d, "The quick", text.drop(9), direction = 0, speed = 0f, until = 400L) },
        "2-steer-left" to { d -> steer(d, "The quick", text.drop(9), direction = -1, speed = CursorMode.SLOWEST_PER_SECOND * 3) },
        "3-steer-right" to { d -> steer(d, "The quick", text.drop(9), direction = 1, speed = CursorMode.FASTEST_PER_SECOND / 2) },
        "4-steer-paused-on" to { d -> pause(d, text, after = 300L) },
        "5-steer-paused-off" to { d -> pause(d, text, after = 300L + GestureDisplay.BLINK_MILLIS) },
        "6-steer-text-start" to { d -> steer(d, "", text, direction = -1, speed = CursorMode.SLOWEST_PER_SECOND * 4) },
        "7-steer-text-end" to { d -> steer(d, text, "", direction = 1, speed = CursorMode.SLOWEST_PER_SECOND * 4) },
        "8-select" to ::select,
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
            drawing.draw(Canvas(image), display, SIDE * DENSITY, WIDTH - SIDE * DENSITY, strip, look,
                StripFit.LINE_DP * DENSITY, strip - StripFit.EDGE_DP * DENSITY, 20 * DENSITY)
            ImageIO.write(image, "png", File(folder, "${moment.first}$suffix.png"))
            val out = sheet.createGraphics()
            out.color = java.awt.Color(look.paper, true)
            out.fillRect(0, index * (strip + gap), WIDTH, strip + gap)
            out.drawImage(image, 0, index * (strip + gap), null)
            // A faint line between the moments, so each strip's own edges show.
            out.color = java.awt.Color(0x80808080.toInt(), true)
            out.fillRect(0, index * (strip + gap) + strip + gap / 2, WIDTH, 1)
            out.dispose()
            // How strong the underglow is a quarter of the way in from each side, and how many times stronger the right is.
            val leftGlow = glowAt(image, WIDTH / 4, strip, dark)
            val rightGlow = glowAt(image, WIDTH * 3 / 4, strip, dark)
            println("${moment.first}$suffix: lean ${"%.2f".format(display.steerLean)}, underglow left ${"%.0f".format(leftGlow)}, " +
                "right ${"%.0f".format(rightGlow)}, right/left ${"%.2f".format(rightGlow / leftGlow)}, " +
                "caret ${if (display.caretShown) "on" else "off"} ${"%.2f".format(display.stepped - display.shownStep)} letters from the middle")
        }
        ImageIO.write(sheet, "png", File(folder, "all$suffix.png"))
    }
    println("Drawn to $folder")
}

/** How far the bottom of the strip at [x] is from the paper towards the accent, 0 to 255, over a few pixels. */
private fun glowAt(image: BufferedImage, x: Int, strip: Int, dark: Boolean): Float {
    var sum = 0f
    var count = 0
    for (dx in -4..4) for (y in strip - (3 * DENSITY).toInt()..strip - 1) {
        val rgb = image.getRGB(x + dx, y)
        // Orange is mostly red: on black the red rises, and on white the blue falls.
        sum += if (dark) (rgb shr 16 and 0xFF).toFloat() else (255 - (rgb and 0xFF)).toFloat()
        count++
    }
    return sum / count
}

/**
 * A tap, then a held touch that steers through real text from between [before] and [after], [speed] characters a second, until
 * [until] ms. The cursor stops at either end of the text, as the real one does. Returns when it got to.
 */
private fun steer(display: GestureDisplay, before: String, after: String, direction: Int, speed: Float, until: Long = 700L): Long {
    display.spaceTapped(1, 0L)
    display.advance(100L, 1f)
    display.cursorFrame(5f, 0, 0f, false, 150L)
    display.cursorText(before, after)
    var now = 150L
    var owed = 0f
    var at = before.length
    while (now < until) {
        now += 16
        display.cursorFrame(if (direction == 0) 5f else 5f + direction * 3.5f, direction, speed, false, now)
        owed += speed * 16 / 1000
        while (owed >= 1f && at + direction in 0..before.length + after.length) {
            owed -= 1f
            at += direction
            display.cursorStepped(direction)
        }
        display.advance(now, 1f)
    }
    return now
}

/** Steering to the right through [text], then back on Space for [after] ms. */
private fun pause(display: GestureDisplay, text: String, after: Long) {
    var now = steer(display, text.take(9), text.drop(9), direction = 1, speed = CursorMode.SLOWEST_PER_SECOND * 4)
    val end = now + after
    while (now < end) {
        now = min(now + 16, end)
        display.cursorFrame(5f, 0, 0f, false, now)
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
