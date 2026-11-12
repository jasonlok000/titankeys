package com.atlas.keyboard

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Draws the word menu on the bar at each step of Save and Favourite, to look at without the phone: the real [MenuKeys] picks the
 * buttons and places them over their keys, and the icons are the app's own vector drawables. The sizes are the bar's, at the
 * density of the owner's screenshots. Each state gets a PNG in the folder given, dark and light, and all.png puts them together.
 */
fun main(args: Array<String>) {
    val folder = File(args.getOrElse(0) { "menu-render" }).apply { mkdirs() }
    val drawables = File(args.getOrElse(1) { "android/src/main/res/drawable" })
    val strings = File(args.getOrElse(2) { "android/src/main/res/values/strings.xml" }).readText()
    val states = listOf(
        Triple("1-not-saved", "Not saved", MenuKeys.step(known = false, favourite = false)),
        Triple("2-saved", "Saved", MenuKeys.step(known = true, favourite = false)),
        Triple("3-favourite", "Favourite", MenuKeys.step(known = true, favourite = true)),
        Triple("4-built-in", "A word the dictionary knows", MenuKeys.step(known = true, favourite = false)),
    )
    val words = mapOf("1-not-saved" to "Titankeys", "2-saved" to "Titankeys", "3-favourite" to "Titankeys", "4-built-in" to "hello")
    for (dark in listOf(true, false)) {
        val suffix = if (dark) "" else "-light"
        val sheet = BufferedImage(WIDTH, states.size * (ROW + CAPTION), BufferedImage.TYPE_INT_RGB)
        for ((index, state) in states.withIndex()) {
            val (name, caption, step) = state
            val image = BufferedImage(WIDTH, ROW + CAPTION, BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val paper = if (dark) Color.BLACK else Color.WHITE
            val ink = if (dark) Color.WHITE else Color.BLACK
            g.color = if (dark) Color(0x22, 0x22, 0x22) else Color(0xEE, 0xEE, 0xEE)
            g.fillRect(0, 0, WIDTH, CAPTION)
            g.color = Color.GRAY
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 26)
            val buttons = listOfNotNull(MenuKeys.first(step), MenuKeys.second(step, forget = true))
            val labels = buttons.joinToString("   ") { "${keyOf(it)}: ${label(strings, it)}" } + "   P: ${string(strings, "strip_back")}"
            g.drawString("$caption.   $labels", 24, CAPTION - 18)
            g.color = paper
            g.fillRect(0, CAPTION, WIDTH, ROW)
            g.translate(0, CAPTION)
            // The word, centred in the room left of the buttons, as the bar draws it.
            g.color = ink
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, (17 * DENSITY).toInt())
            val word = "“${words.getValue(name)}”"
            val side = Layout.KEY_SIDE_DP * DENSITY
            val right = MenuKeys.wordRight(WIDTH.toFloat(), side, 8 * DENSITY)
            val baseline = ROW / 2 + g.fontMetrics.ascent / 2 - g.fontMetrics.descent / 2
            g.drawString(word, ((side + right) / 2 - g.fontMetrics.stringWidth(word) / 2f).toInt(), baseline)
            // The buttons: an icon, 22 dp, centred over each key's column.
            val icon = 22 * DENSITY
            for ((slot, file) in listOf(MenuKeys.first(step), MenuKeys.second(step, forget = true)).map { it?.let(::drawable) }
                .plus("panel_back").withIndex()) {
                if (file == null) continue
                val (left, end) = MenuKeys.slot(WIDTH.toFloat(), side, MenuKeys.SAVE + slot)
                val x = (left + end) / 2 - icon / 2
                val y = (ROW - icon) / 2
                drawVector(g, File(drawables, "$file.xml").readText(), x, y, icon, ink)
            }
            g.dispose()
            ImageIO.write(image, "png", File(folder, "menu-$name$suffix.png"))
            sheet.createGraphics().apply { drawImage(image, 0, index * (ROW + CAPTION), null); dispose() }
        }
        ImageIO.write(sheet, "png", File(folder, "menu-all$suffix.png"))
    }
    println("Drawn to $folder")
}

/** The key that picks [button]: I for the first, O for the second. */
private fun keyOf(button: MenuKeys.Button) = if (button == MenuKeys.Button.Unfavourite || button == MenuKeys.Button.Forget) "O" else "I"

/** The drawable of each button, as Strip.icon has it: run.sh checks that it still does. */
private fun drawable(button: MenuKeys.Button) = when (button) {
    MenuKeys.Button.Save -> "menu_save"
    MenuKeys.Button.Favourite -> "menu_favourite"
    MenuKeys.Button.Favourited -> "menu_favourited"
    MenuKeys.Button.Unfavourite -> "menu_unfavourite"
    MenuKeys.Button.Forget -> "menu_forget"
}

private fun label(strings: String, button: MenuKeys.Button) = string(strings, when (button) {
    MenuKeys.Button.Save -> "strip_save"
    MenuKeys.Button.Favourite -> "strip_favourite"
    MenuKeys.Button.Favourited -> "strip_favourited"
    MenuKeys.Button.Unfavourite -> "strip_unfavourite"
    MenuKeys.Button.Forget -> "strip_forget"
})

private fun string(strings: String, name: String) =
    Regex("<string name=\"$name\">([^<]*)</string>").find(strings)?.groupValues?.get(1) ?: error("No string $name")

/** A vector drawable's paths, filled or stroked as it says, in [colour], scaled from its 24 by 24 viewport into [size]. */
private fun drawVector(g: java.awt.Graphics2D, xml: String, x: Float, y: Float, size: Float, colour: Color) {
    val scale = size / 24f
    val transform = AffineTransform(scale.toDouble(), 0.0, 0.0, scale.toDouble(), x.toDouble(), y.toDouble())
    g.color = colour
    for (element in Regex("<path([^>]*)/>", RegexOption.DOT_MATCHES_ALL).findAll(xml)) {
        val attributes = element.groupValues[1]
        val data = Regex("android:pathData=\"([^\"]*)\"").find(attributes)!!.groupValues[1]
        val shape = transform.createTransformedShape(parsePath(data))
        val stroke = Regex("android:strokeWidth=\"([^\"]*)\"").find(attributes)?.groupValues?.get(1)?.toFloat()
        if (stroke != null) {
            g.stroke = BasicStroke(stroke * scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(shape)
        } else g.fill(shape)
    }
}

/** Android's path data: the commands the app's icons use, absolute and relative, with repeated arguments. */
private fun parsePath(data: String): Path2D.Float {
    val path = Path2D.Float(Path2D.WIND_NON_ZERO)
    val tokens = Regex("[MmLlHhVvCcSsZz]|-?[0-9]*\\.?[0-9]+(?:e-?[0-9]+)?").findAll(data).map { it.value }.toList()
    var i = 0
    var command = 'M'
    var x = 0f
    var y = 0f
    var startX = 0f
    var startY = 0f
    var controlX = 0f
    var controlY = 0f
    fun number() = tokens[i++].toFloat()
    while (i < tokens.size) {
        if (tokens[i][0].isLetter()) command = tokens[i++][0]
        val relative = command.isLowerCase()
        val ox = if (relative) x else 0f
        val oy = if (relative) y else 0f
        when (command.uppercaseChar()) {
            'M' -> {
                x = ox + number(); y = oy + number(); path.moveTo(x, y); startX = x; startY = y
                // Pairs after a move are lines.
                command = if (relative) 'l' else 'L'
            }
            'L' -> { x = ox + number(); y = oy + number(); path.lineTo(x, y) }
            'H' -> { x = ox + number(); path.lineTo(x, y) }
            'V' -> { y = oy + number(); path.lineTo(x, y) }
            'C' -> {
                val x1 = ox + number(); val y1 = oy + number(); val x2 = ox + number(); val y2 = oy + number()
                x = ox + number(); y = oy + number()
                path.curveTo(x1, y1, x2, y2, x, y); controlX = x2; controlY = y2
            }
            'S' -> {
                val x1 = 2 * x - controlX; val y1 = 2 * y - controlY
                val x2 = ox + number(); val y2 = oy + number()
                x = ox + number(); y = oy + number()
                path.curveTo(x1, y1, x2, y2, x, y); controlX = x2; controlY = y2
            }
            'Z' -> { path.closePath(); x = startX; y = startY }
        }
        if (command.uppercaseChar() != 'C' && command.uppercaseChar() != 'S') { controlX = x; controlY = y }
    }
    return path
}

/** As on the owner's screenshots: 1076 px across at about 1.55 px to a dp. The bar is 56 dp. */
private const val DENSITY = 1.55f
private const val WIDTH = 1076
private val ROW = (56 * DENSITY).toInt()
private const val CAPTION = 48
