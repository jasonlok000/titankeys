package com.titankeys.keyboard

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.text.AttributedString
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Draws the crash offer (CrashOffer.kt) to PNGs on the Titan 2 Elite's 1076×1200 frame, with the sizes and words read from its
 * source and the app's Inter. TitanKeys' dp is the width ÷ 688.6 (FixedScale), and the system font size never changes it, so one
 * picture stands for every font size. It also checks, in pixels, that the card is most of the screen wide and that its words
 * and buttons fit on the screen. Usage: CrashOfferRender <out> <CrashOffer.kt> <font folder>.
 */
private const val WIDTH = 1076
private const val HEIGHT = 1200
private const val DP = WIDTH / 688.6f

fun main(args: Array<String>) {
    val out = File(args[0]).apply { mkdirs() }
    val source = File(args[1]).readText()
    fun size(name: String) = Regex("const val $name = ([0-9.]+)f?").find(source)!!.groupValues[1].toFloat()
    fun words(start: String) = Regex("\"(${Regex.escape(start)}[^\"]*)\"").find(source)!!.groupValues[1]
    val text = Regex("""const val TEXT = "([^"]*)" \+\s+"([^"]*)"""").find(source)!!.groupValues.let { it[1] + it[2] }
    val fonts = File(args[2])
    val heavy = Font.createFont(Font.TRUETYPE_FONT, File(fonts, "inter_bold.otf"))
    val medium = Font.createFont(Font.TRUETYPE_FONT, File(fonts, "inter_medium.otf"))
    for ((name, title) in listOf("crashed" to words("TitanKeys crashed"), "froze" to words("TitanKeys froze"))) {
        val image = draw(title, text, words("Send"), words("No thanks"), heavy, medium, ::size)
        ImageIO.write(image, "png", File(out, "crash-offer-$name.png"))
        println("Drew crash-offer-$name.png")
    }
}

private fun draw(title: String, body: String, send: String, no: String, heavy: Font, medium: Font, size: (String) -> Float): BufferedImage {
    val px = { dp: Float -> dp * DP }
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    // A stand-in app screen behind, dimmed as the window dims it.
    g.color = Color.BLACK
    g.fillRect(0, 0, WIDTH, HEIGHT)
    g.font = heavy.deriveFont(px(54f))
    g.color = Color(0xFF, 0x7A, 0x2F)
    g.drawString("TITANKEYS", px(24f), px(80f))
    g.color = Color(0, 0, 0, (size("DIM") * 255).roundToInt())
    g.fillRect(0, 0, WIDTH, HEIGHT)

    val frc = FontRenderContext(null, true, true)
    val left = px(size("MARGIN"))
    val cardWidth = WIDTH - 2 * left
    val inner = cardWidth - 2 * px(size("PADDING"))
    fun lines(value: String, font: Font): List<TextLayout> {
        val attributed = AttributedString(value).apply { addAttribute(TextAttribute.FONT, font) }
        val measurer = LineBreakMeasurer(attributed.iterator, frc)
        return buildList { while (measurer.position < value.length) add(measurer.nextLayout(inner)) }
    }
    val titleLines = lines(title, heavy.deriveFont(px(size("TITLE"))))
    val bodyLines = lines(body, medium.deriveFont(px(size("BODY"))))
    fun height(layouts: List<TextLayout>) = layouts.sumOf { (it.ascent + it.descent + it.leading).toDouble() }.toFloat()
    val sendHeight = px(size("SEND_HEIGHT"))
    val noHeight = px(size("NO_HEIGHT"))
    val cardHeight = px(size("PADDING")) + height(titleLines) + px(size("TITLE_GAP")) + height(bodyLines) +
        px(size("BUTTONS_GAP")) + sendHeight + px(8f) + noHeight + px(size("PADDING") - 8)
    check(cardWidth / WIDTH >= 0.9f) { "The card is only ${cardWidth / WIDTH} of the screen's width" }
    check(cardHeight <= HEIGHT - 2 * left) { "The card is $cardHeight px tall, more than the screen" }
    val top = (HEIGHT - cardHeight) / 2
    g.color = Color(0x16, 0x12, 0x10)
    val card = RoundRectangle2D.Float(left, top, cardWidth, cardHeight, 2 * px(size("CORNER")), 2 * px(size("CORNER")))
    g.fill(card)
    g.color = Color(0x2A, 0x26, 0x23)
    g.stroke = BasicStroke(px(1f))
    g.draw(card)

    var y = top + px(size("PADDING"))
    val x = left + px(size("PADDING"))
    g.color = Color(0xFF, 0xF1, 0xE6)
    fun write(layouts: List<TextLayout>) = layouts.forEach { y += it.ascent; it.draw(g, x, y); y += it.descent + it.leading }
    write(titleLines)
    y += px(size("TITLE_GAP"))
    write(bodyLines)
    y += px(size("BUTTONS_GAP"))
    g.color = Color(0xFF, 0x7A, 0x2F)
    g.fill(RoundRectangle2D.Float(x, y, inner, sendHeight, sendHeight, sendHeight))
    centred(g, send, heavy.deriveFont(px(size("BUTTON"))), Color(0x16, 0x0A, 0x04), x, y, inner, sendHeight)
    y += sendHeight + px(8f)
    centred(g, no, heavy.deriveFont(px(size("BUTTON"))), Color(0x99, 0x91, 0x8A), x, y, inner, noHeight)
    g.dispose()
    println("Card ${cardWidth.roundToInt()}×${cardHeight.roundToInt()} px of $WIDTH×$HEIGHT; title ${px(size("TITLE")).roundToInt()} px, " +
        "body ${px(size("BODY")).roundToInt()} px, buttons ${px(size("BUTTON")).roundToInt()} px; Send it ${sendHeight.roundToInt()} px tall")
    return image
}

private fun centred(g: java.awt.Graphics2D, value: String, font: Font, colour: Color, x: Float, y: Float, width: Float, height: Float) {
    val layout = TextLayout(value, font, g.fontRenderContext)
    g.color = colour
    layout.draw(g, x + (width - layout.advance) / 2, y + (height + layout.ascent - layout.descent) / 2)
}
