package com.atlas.keyboard

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.min

/*
 * Checks how Key calibration moves the drawings of the keys ([KeyAlignment]): the defaults move nothing, the scale and the offsets
 * do what they say, and a key pushed past the edge keeps its centre and shrinks its label. Set CALIBRATION_PNG to a folder to also
 * draw the calibration screen's grid, at the defaults and calibrated, as KeyCalibrationActivity's KeyGrid draws it.
 */
fun main() {
    defaultsMoveNothing()
    scaleAndOffsets()
    trailFollowsRows()
    tightKeysShrink()
    slidersRoundTrip()
    System.getenv("CALIBRATION_PNG")?.let { folder ->
        File(folder).mkdirs()
        draw(KeyAlignment.DEFAULT, File(folder, "calibration-default.png"))
        draw(KeyAlignment(1.04f, .2f, floatArrayOf(0f, .1f, -.15f, .3f)), File(folder, "calibration-moved.png"))
        println("Drew the calibration grid in $folder")
    }
    println("Key alignment checks passed")
}

private fun defaultsMoveNothing() {
    val alignment = KeyAlignment()
    check(alignment.identity) { "the defaults should be identity" }
    check(KeyAlignment.DEFAULT.identity && KeyAlignment.current.identity) { "nothing is calibrated until someone calibrates" }
    for (row in 0 until KeyAlignment.ROWS) for (x in listOf(0f, .05f, .3f, .5f, .77f, 1f)) same(alignment.x(x, row), x, "x $x on row $row")
    for (key in KeyAlignment.keys) {
        val span = alignment.span(key.left, key.right, key.row)
        same(span.left, key.left, "${key.name}'s left")
        same(span.right, key.right, "${key.name}'s right")
        same(span.fit, 1f, "${key.name}'s label")
    }
    val point = floatArrayOf(.33f, .6f, 1.4f)
    check(alignment.point(point).contentEquals(point)) { "a trail point moves with the defaults" }
}

private fun scaleAndOffsets() {
    // The scale stretches about the middle, so the middle stays.
    val wide = KeyAlignment(scale = 1.1f)
    near(wide.x(.5f, 0), .5f, "the middle under a scale")
    near(wide.x(.05f, 0), .5f - .45f * 1.1f, "Q's centre under a scale")
    near(wide.x(.95f, 2), .5f + .45f * 1.1f, "Enter's centre under a scale")
    // An offset is in key widths: one key is a tenth of the keys.
    val moved = KeyAlignment(offset = .5f)
    near(moved.x(.25f, 1), .3f, "half a key to the right")
    // A row's offset moves only that row, and adds to the offset of all.
    val row = KeyAlignment(offset = .1f, rowOffsets = floatArrayOf(0f, 0f, -.3f, 0f))
    near(row.x(.45f, 0), .46f, "the top row takes only the offset of all")
    near(row.x(.45f, 2), .43f, "the bottom letter row takes its own too")
    check(!row.identity) { "a moved row is not identity" }
    // Its width scales with the columns.
    val span = wide.span(.4f, .5f, 0)
    near(span.right - span.left, .11f, "a key's width under a scale")
    near(span.fit, 1f, "a key in the middle keeps its label")
}

private fun trailFollowsRows() {
    val alignment = KeyAlignment(rowOffsets = floatArrayOf(.2f, .4f, 0f, 0f))
    // On a row's middle, the trail takes that row's offset; halfway between two rows, half of each.
    near(alignment.point(floatArrayOf(.5f, .125f))[0], .52f, "on the top row")
    near(alignment.point(floatArrayOf(.5f, .375f))[0], .54f, "on the middle row")
    near(alignment.point(floatArrayOf(.5f, .25f))[0], .53f, "between the top two rows")
    near(alignment.point(floatArrayOf(.5f, 0f))[0], .52f, "above the top row's middle")
    val point = alignment.point(floatArrayOf(.5f, .375f, 1.7f))
    same(point[1], .375f, "a trail point's height")
    same(point[2], 1.7f, "a trail point's thickness")
}

private fun tightKeysShrink() {
    // Moved right, so P passes the right edge.
    val alignment = KeyAlignment(offset = .3f)
    val p = KeyAlignment.keys.first { it.name == "P" }
    val span = alignment.span(p.left, p.right, p.row)
    near(span.centre, alignment.x(.95f, 0), "P's centre stays over the real P")
    check(span.right <= 1f + 1e-6f) { "P stays inside the drawing: ${span.right}" }
    check(span.fit < 1f) { "P's label shrinks: ${span.fit}" }
    near(span.right - span.left, .1f * span.fit, "P narrows as much as its label")
    // Q, on the other side, has room.
    val q = KeyAlignment.keys.first { it.name == "Q" }
    near(alignment.span(q.left, q.right, q.row).fit, 1f, "Q keeps its label")
    // A key whose centre is past the edge has no room at all.
    same(KeyAlignment(offset = .5f, rowOffsets = floatArrayOf(.5f, 0f, 0f, 0f)).span(p.left, p.right, 0).fit, 0f, "P past the edge")
}

private fun slidersRoundTrip() {
    same(KeyAlignment.scaleAt(KeyAlignment.scaleProgress(1f)), 1f, "the scale slider's default")
    same(KeyAlignment.offsetAt(KeyAlignment.offsetProgress(0f)), 0f, "an offset slider's default")
    same(KeyAlignment.scaleAt(0), KeyAlignment.SCALE_MIN, "the narrowest scale")
    same(KeyAlignment.scaleAt(KeyAlignment.SCALE_STEPS), KeyAlignment.SCALE_MAX, "the widest scale")
    same(KeyAlignment.offsetAt(0), -KeyAlignment.OFFSET_MAX, "the leftmost offset")
    same(KeyAlignment.offsetAt(KeyAlignment.OFFSET_STEPS), KeyAlignment.OFFSET_MAX, "the rightmost offset")
    for (progress in 0..KeyAlignment.SCALE_STEPS) same(KeyAlignment.scaleProgress(KeyAlignment.scaleAt(progress)), progress, "scale step $progress")
    for (progress in 0..KeyAlignment.OFFSET_STEPS) same(KeyAlignment.offsetProgress(KeyAlignment.offsetAt(progress)), progress, "offset step $progress")
}

private fun same(actual: Any?, expected: Any?, message: String) = check(actual == expected) { "$message: expected $expected, got $actual" }

private fun near(actual: Float, expected: Float, message: String) =
    check(abs(actual - expected) < 1e-5f) { "$message: expected $expected, got $actual" }

/** The size of the owner's phone screenshots, whose 18 dp gutter is 28 px, as in the guide's slide drawings. */
private const val WIDTH = 1076
private const val DP = 1.56f
private const val ROW_HEIGHT = 44
private fun dp(value: Number) = value.toFloat() * DP
private fun rgb(argb: Long) = Color(argb.toInt(), true)

/**
 * The calibration screen as its KeyGrid draws it, with the screen's text and sliders in the same order above: each key faint where
 * it was and solid where [alignment] puts it, with its centre marked in orange. The desktop's fonts are close, not exact.
 */
private fun draw(alignment: KeyAlignment, file: File) {
    val rowNames = listOf("Top row", "Middle row", "Bottom letter row", "Bottom row")
    val sliders = listOf("Width" to String.format("%.3f×", alignment.scale), "Shift" to String.format("%+.2f key", alignment.offset)) +
        rowNames.indices.map { rowNames[it] to String.format("%+.2f key", alignment.rowOffsets[it]) }
    val progress = listOf(KeyAlignment.scaleProgress(alignment.scale) / KeyAlignment.SCALE_STEPS.toFloat(),
        KeyAlignment.offsetProgress(alignment.offset) / KeyAlignment.OFFSET_STEPS.toFloat()) +
        alignment.rowOffsets.map { KeyAlignment.offsetProgress(it) / KeyAlignment.OFFSET_STEPS.toFloat() }
    val gridHeight = dp(KeyAlignment.ROWS * ROW_HEIGHT)
    val sliderHeight = dp(64)
    val height = (dp(16 + 36 + 48) + sliders.size * sliderHeight + dp(16 * 3 + 20 + 48 + 24) + gridHeight).toInt()
    val image = BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = rgb(0xFF160A04)
    g.fillRect(0, 0, WIDTH, height)
    val pad = dp(16)
    var y = pad
    g.color = rgb(0xFFFFF1E6)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(24).toInt())
    y += dp(28)
    g.drawString("Key calibration", pad, y)
    g.color = rgb(0xFFA2958C)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, dp(14).toInt())
    y += dp(28)
    g.drawString("Move the sliders until each drawn key sits right over its real key below the screen.", pad, y)
    y += dp(20)
    g.drawString("It saves as you go.", pad, y)
    for ((index, slider) in sliders.withIndex()) {
        if (index == 0 || index == 2) {
            y += dp(16)
            val count = if (index == 0) 2 else 4
            g.color = rgb(0xFF21140C)
            g.fill(RoundRectangle2D.Float(pad, y, WIDTH - 2 * pad, count * sliderHeight + dp(20), dp(24), dp(24)))
            y += dp(12)
        }
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, dp(15).toInt())
        g.color = rgb(0xFFFFF1E6)
        g.drawString(slider.first, pad + dp(16), y + dp(18))
        g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(15).toInt())
        g.color = rgb(0xFFEF804E)
        g.drawString(slider.second, WIDTH - pad - dp(16) - g.fontMetrics.stringWidth(slider.second), y + dp(18))
        val trackLeft = pad + dp(28)
        val trackRight = WIDTH - pad - dp(28)
        val trackY = y + dp(42)
        g.color = rgb(0xFF3A2A20)
        g.fill(RoundRectangle2D.Float(trackLeft, trackY - dp(2), trackRight - trackLeft, dp(4), dp(4), dp(4)))
        val thumb = trackLeft + (trackRight - trackLeft) * progress[index]
        g.color = rgb(0xFFEF804E)
        g.fill(RoundRectangle2D.Float(trackLeft, trackY - dp(2), thumb - trackLeft, dp(4), dp(4), dp(4)))
        g.fill(java.awt.geom.Ellipse2D.Float(thumb - dp(8), trackY - dp(8), dp(16), dp(16)))
        y += sliderHeight
        if (index == 1) y += dp(8)
    }
    y += dp(24)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(15).toInt())
    val resetWidth = g.fontMetrics.stringWidth("Reset") + dp(40)
    g.color = rgb(0xFFEF804E)
    g.fill(RoundRectangle2D.Float(pad, y, resetWidth, dp(48), dp(48), dp(48)))
    g.color = rgb(0xFF160A04)
    g.drawString("Reset", pad + dp(20), y + dp(30))
    // The grid, at the bottom, as KeyGrid draws it.
    val top0 = height - gridHeight
    val side = dp(Layout.KEY_SIDE_DP)
    val across = WIDTH - 2 * side
    val rowHeight = gridHeight / KeyAlignment.ROWS
    val gap = dp(2)
    val radius = dp(6) * 2
    for (key in KeyAlignment.keys) {
        val top = top0 + key.row * rowHeight + gap
        val bottom = top0 + (key.row + 1) * rowHeight - gap
        g.color = rgb(0x66A2958C)
        g.stroke = BasicStroke(DP)
        g.draw(RoundRectangle2D.Float(side + key.left * across + gap, top, (key.right - key.left) * across - 2 * gap, bottom - top, radius, radius))
        val span = alignment.span(key.left, key.right, key.row)
        val left = side + span.left * across
        val right = side + span.right * across
        g.color = rgb(0x33FFF1E6)
        g.fill(RoundRectangle2D.Float(left + gap, top, right - left - 2 * gap, bottom - top, radius, radius))
        val centre = side + span.centre * across
        g.color = rgb(0xFFEF804E)
        g.fill(java.awt.geom.Rectangle2D.Float(centre - DP / 2, top, DP, (bottom - top) * .22f))
        g.fill(java.awt.geom.Rectangle2D.Float(centre - DP / 2, bottom - (bottom - top) * .22f, DP, (bottom - top) * .22f))
        g.color = rgb(0xFFFFF1E6)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(min(rowHeight, (key.right - key.left) * across) * .42f * span.fit + .01f)
        val metrics = g.fontMetrics
        g.drawString(key.name, centre - metrics.stringWidth(key.name) / 2f, (top + bottom) / 2 + (metrics.ascent - metrics.descent) / 2f)
    }
    g.dispose()
    ImageIO.write(image, "png", file)
}
