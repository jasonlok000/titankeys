package com.atlas.keyboard

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/*
 * Desktop drawings of the front page (the playground), on the black and orange theme (owner, v5.3, option B), for the owner to
 * review before it's built: the header with Guide and Settings, the line to type along to, the dark text box with its orange
 * accent, and the whole real keyboard at its real size with the touchpad's zone as an orange dashed outline. Ink's font, Inter,
 * as the phone draws it. The size is the owner's phone screenshots'.
 */

private object Theme {
    // lib/brand.ts, agreed with F (v5.3): one accent, one secondary text colour, one divider.
    val BLACK = Color(0x00, 0x00, 0x00)
    val ORANGE = Color(0xEF, 0x80, 0x4E)
    val CREAM = Color(0xFF, 0xF1, 0xE6)
    val MUTED = Color(0x99, 0x91, 0x8A)
    val DIVIDER = Color(0x2A, 0x26, 0x23)
    val INK = Color(0x16, 0x0A, 0x04)
}

private const val W = 1076
private const val H = 1200
private const val D = 1.56f
private fun px(dp: Number) = Math.round(dp.toFloat() * D)

/** Ink's font, from the repository: Inter Regular, and Medium, which the titles embolden. Falls back to the desktop's sans. */
private val INTER: Font by lazy { load("assets/fonts/Inter-Regular.otf") }
private val INTER_MEDIUM: Font by lazy { load("assets/fonts/Inter-Medium.otf") }

private fun load(path: String): Font = try {
    File(path).inputStream().use { Font.createFont(Font.TRUETYPE_FONT, it) }
} catch (error: Exception) {
    println("  front page: $path not loaded (${error.message}), using sans")
    Font(Font.SANS_SERIF, Font.PLAIN, 1)
}

private fun regular(sp: Number) = INTER.deriveFont(sp.toFloat() * D)
private fun medium(sp: Number) = INTER_MEDIUM.deriveFont(sp.toFloat() * D)
/** The titles: Inter Medium, emboldened, as Android does for a weight the font file doesn't have. */
private fun heavy(sp: Number) = INTER_MEDIUM.deriveFont(Font.BOLD, sp.toFloat() * D)

private fun alpha(colour: Color, amount: Float) = Color(colour.red, colour.green, colour.blue, Math.round(amount.coerceIn(0f, 1f) * 255))

/** Words wrapped at [width]: whole words only, and a sentence is never cut (v5.3: the prompt was cut mid-word). */
private fun wrapped(g: Graphics2D, text: String, width: Int): List<String> {
    val lines = ArrayList<String>()
    var line = ""
    for (word in text.split(' ')) {
        val tried = if (line.isEmpty()) word else "$line $word"
        if (g.fontMetrics.stringWidth(tried) <= width || line.isEmpty()) line = tried else {
            lines += line
            line = word
        }
    }
    if (line.isNotEmpty()) lines += line
    return lines
}

/**
 * The front page. [typed] is what's in the text box, [wpm] the speed when typing (the name turns into it, as it does now), and
 * [sentence] the prompt, whole.
 */
private fun drawFront(file: File, typed: String, wpm: Int?, sentence: String, doneWords: Int) {
    val image = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    g.color = Theme.BLACK
    g.fillRect(0, 0, W, H)
    val side = px(18)
    val inner = W - 2 * side
    var y = px(36)

    // The header: the name in orange, big, on the left; Guide and Settings as two finger-sized buttons on the right (v5.3).
    val headerHeight = px(52)
    val button = px(44)
    g.font = medium(15)
    val labels = listOf("Guide", "Settings")
    var right = W - side
    val buttonTops = y + (headerHeight - button) / 2
    for ((index, label) in labels.reversed().withIndex()) {
        val wide = g.fontMetrics.stringWidth(label) + px(32)
        val left = right - wide
        val shape = RoundRectangle2D.Float(left.toFloat(), buttonTops.toFloat(), wide.toFloat(), button.toFloat(), button.toFloat(), button.toFloat())
        // Settings outlined in orange; Guide filled, as the one to try first.
        if (index == 1) {
            g.color = Theme.ORANGE
            g.fill(shape)
            g.color = Theme.INK
        } else {
            g.color = Theme.ORANGE
            g.stroke = BasicStroke(px(1.5f).toFloat())
            g.draw(shape)
        }
        g.drawString(label, left + px(16), buttonTops + button / 2 + g.fontMetrics.ascent / 2 - px(2))
        right = left - px(8)
    }
    val title = if (wpm != null) "$wpm wpm" else "TitanKeys"
    g.font = heavy(30)
    g.color = Theme.ORANGE
    g.drawString(title, side, y + headerHeight / 2 + g.fontMetrics.ascent / 2 - px(3))
    y += headerHeight + px(6)
    // Under the name, the one line that says what this is, in the secondary colour.
    g.font = regular(15)
    g.color = Theme.MUTED
    val tagline = if (wpm != null) "Faster than you look." else "Type or swipe anything. The line below is a suggestion."
    g.drawString(tagline, side, y + g.fontMetrics.ascent)
    y += g.fontMetrics.height + px(18)

    // The prompt: whole sentences, wrapped by words, the words done faded, the next one in orange (v5.3).
    g.font = regular(19)
    val words = sentence.split(' ')
    val lines = wrapped(g, sentence, inner)
    var count = 0
    for (line in lines) {
        var x = side.toFloat()
        val base = y + g.fontMetrics.ascent
        for (word in line.split(' ')) {
            g.color = when {
                count < doneWords -> alpha(Theme.CREAM, .35f)
                count == doneWords -> Theme.ORANGE
                else -> Theme.CREAM
            }
            g.drawString(word, x, base.toFloat())
            x += g.fontMetrics.stringWidth("$word ")
            count++
        }
        y += g.fontMetrics.height
    }
    if (count != words.size) println("  front page: prompt lost words")
    y += px(14)

    // The text box: dark and warm, an orange bar down its left, an orange outline while it has the cursor (v5.3, option B).
    g.font = regular(21)
    val pad = px(14)
    val boxLines = 4
    val boxHeight = boxLines * g.fontMetrics.height + 2 * pad
    val box = RoundRectangle2D.Float(side.toFloat(), y.toFloat(), inner.toFloat(), boxHeight.toFloat(), px(16).toFloat(), px(16).toFloat())
    g.color = Color(0x14, 0x0F, 0x0C)
    g.fill(box)
    g.color = Theme.ORANGE
    g.stroke = BasicStroke(px(1.5f).toFloat())
    g.draw(box)
    g.fill(RoundRectangle2D.Float(side.toFloat(), y.toFloat() + px(10), px(4).toFloat(), boxHeight - 2f * px(10), px(4).toFloat(), px(4).toFloat()))
    val textLeft = side + pad + px(8)
    val shown = if (typed.isEmpty()) listOf("Start anywhere.") else wrapped(g, typed, inner - 2 * pad - px(8))
    g.color = if (typed.isEmpty()) Theme.MUTED else Theme.CREAM
    for ((index, line) in shown.take(boxLines).withIndex()) g.drawString(line, textLeft, y + pad + index * g.fontMetrics.height + g.fontMetrics.ascent)
    // The caret, orange, after the text.
    val last = shown.take(boxLines).last()
    val caretX = if (typed.isEmpty()) textLeft else textLeft + g.fontMetrics.stringWidth(last) + px(2)
    val caretLine = if (typed.isEmpty()) 0 else shown.take(boxLines).size - 1
    g.color = Theme.ORANGE
    g.fillRect(caretX, y + pad + caretLine * g.fontMetrics.height + px(3), px(2.5f), g.fontMetrics.height - px(6))

    // The keyboard: every key of the real one, the bottom row too, at its real size, above the keyboard's own bar.
    val geometry = KeyGeometry(W.toFloat(), KeyAlignment.DEFAULT)
    val bar = px(60)
    val top = H - bar - Math.ceil(geometry.height.toDouble()).toInt()
    if (top < y + boxHeight + px(12)) println("  front page: NO ROOM FOR THE KEYS")
    // Where the labels are, so the zone's dashes stop short of them and every letter stays clean.
    val labelBoxes = java.awt.geom.Area()
    for (key in geometry.boxes) {
        val shape = RoundRectangle2D.Float(key.left, top + key.top, key.width, key.height, px(8).toFloat(), px(8).toFloat())
        g.color = alpha(Theme.CREAM, .04f)
        g.fill(shape)
        g.color = Theme.DIVIDER.brighter()
        g.stroke = BasicStroke(px(1).toFloat())
        g.draw(shape)
        val label = when (key.name) {
            Layout.SPACE -> ""
            "○", "◁", "▦" -> key.name
            else -> if (key.name.length == 1) key.name else key.name.take(3)
        }
        // Inter as the app ships it has no ⌫, ↵ or ⇧, which the phone draws as shapes; the desktop's sans stands in for them.
        g.font = if (medium(17).canDisplayUpTo(label) == -1) medium(17) else Font(Font.SANS_SERIF, Font.PLAIN, px(17))
        g.color = alpha(Theme.CREAM, .72f)
        val labelX = key.left + key.width / 2 - g.fontMetrics.stringWidth(label) / 2
        val labelY = top + key.centreY + g.fontMetrics.ascent / 2 - px(2)
        g.drawString(label, labelX, labelY)
        if (label.isNotEmpty()) labelBoxes.add(java.awt.geom.Area(java.awt.Rectangle((labelX - px(5)).toInt(),
            (labelY - g.fontMetrics.ascent - px(3)).toInt(), g.fontMetrics.stringWidth(label) + px(10), g.fontMetrics.ascent + px(8))))
        if (key.name == Layout.SPACE) {
            // Space's mark, a little cup, as on the phone.
            val cx = key.centreX
            val cy = top + key.centreY
            val half = key.width * .2f
            g.stroke = BasicStroke(px(2).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.drawLine((cx - half).toInt(), (cy + px(5)).toInt(), (cx + half).toInt(), (cy + px(5)).toInt())
            g.drawLine((cx - half).toInt(), (cy + px(5)).toInt(), (cx - half).toInt(), (cy - px(2)).toInt())
            g.drawLine((cx + half).toInt(), (cy + px(5)).toInt(), (cx + half).toInt(), (cy - px(2)).toInt())
            labelBoxes.add(java.awt.geom.Area(java.awt.Rectangle((cx - half - px(6)).toInt(), (cy - px(6)).toInt(), (2 * half + px(12)).toInt(), px(16))))
        }
    }
    // The touchpad's zone, where it really is (GuideKeyboardView's AREA_*), as an orange dashed outline: no key is two-toned.
    val zone = geometry.area(.05f, .5f / 4, .95f, 3.54f / 4)
    g.color = alpha(Theme.ORANGE, .9f)
    g.stroke = BasicStroke(px(1.5f).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(px(6).toFloat(), px(5).toFloat()), 0f)
    val clear = java.awt.geom.Area(java.awt.Rectangle(0, 0, W, H)).apply { subtract(labelBoxes) }
    g.clip = clear
    g.draw(RoundRectangle2D.Float(zone.left, top + zone.top, zone.width, zone.height, px(14).toFloat(), px(14).toFloat()))
    g.clip = null
    g.font = medium(11)
    val note = "TOUCHPAD"
    g.color = Theme.BLACK
    val noteWidth = g.fontMetrics.stringWidth(note) + px(10)
    g.fillRect((zone.right - noteWidth - px(14)).toInt(), (top + zone.top - g.fontMetrics.height / 2).toInt(), noteWidth, g.fontMetrics.height)
    g.color = Theme.ORANGE
    g.drawString(note, zone.right - noteWidth - px(9), top + zone.top + g.fontMetrics.ascent / 2 - px(1))
    // A note for review only, in the keyboard's bar room: the phone shows the keyboard's own bar there.
    g.font = regular(11)
    g.color = Color(0x66, 0xCC, 0xFF)
    g.drawString("render note: the keyboard's own bar is here, pure black", side, H - bar / 2)
    g.dispose()
    ImageIO.write(image, "png", file)
}

/** The front page's review renders: empty, typing, and fast. */
internal fun drawFrontPage(folder: String) {
    val out = File(folder).apply { mkdirs() }
    val sentence = "titankeys fixes your typos so you can keep pretending you never make any. swipe a word and lift and titankeys types it before you even look."
    drawFront(File(out, "front-1-empty.png"), "", null, sentence, 0)
    drawFront(File(out, "front-2-typing.png"), "titankeys fixes your typos so you can", null, sentence, 7)
    drawFront(File(out, "front-3-fast.png"), "titankeys fixes your typos so you can keep pretending you never make any swipe a word",
        64, sentence, 17)
    println("front page drawn to $folder")
}
