package com.atlas.keyboard

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Draws the word menu in every state of its ladder, with and without a hover, a long word, and the first-time hint's frames, to
 * look at without the phone. The real [MenuKeys] picks the buttons, places them over their keys, sizes the hovered pill and
 * shortens the word, and the icons are the app's own vector drawables. The sizes are Strip's, at the density of the owner's
 * screenshots. Each sheet is a PNG in the folder given, dark and light.
 */
fun main(args: Array<String>) {
    val folder = File(args.getOrElse(0) { "menu-render" }).apply { mkdirs() }
    val drawables = File(args.getOrElse(1) { "android/src/main/res/drawable" })
    val strings = File(args.getOrElse(2) { "android/src/main/res/values/strings.xml" }).readText()
    val font = File(args.getOrElse(3) { "android/src/main/res/font/inter_regular.otf" })
    val base = runCatching { Font.createFont(Font.TRUETYPE_FONT, font) }.getOrElse { Font(Font.SANS_SERIF, Font.PLAIN, 1) }
    val new = MenuKeys.Step.New
    val saved = MenuKeys.Step.Saved
    val favourite = MenuKeys.Step.Favourite
    val builtIn = MenuKeys.Step.BuiltIn
    val (i, o, p) = Triple(MenuKeys.SAVE, MenuKeys.FORGET, MenuKeys.BACK)
    val states = listOf(
        Row("1-not-saved", "Not saved, nothing hovered", "Titankeys", new),
        Row("2-not-saved-hover-i", "Not saved, hovering I (or K)", "Titankeys", new, i),
        Row("3-not-saved-hover-o", "Not saved, hovering O (or L): the pill covers I", "Titankeys", new, o),
        Row("4-not-saved-hover-p", "Not saved, hovering P (or Backspace)", "Titankeys", new, p),
        Row("5-saved", "Saved, after Save: the bookmark badge, the menu stays open", "Titankeys", saved),
        Row("6-saved-hover-i", "Saved, hovering I: after the press it says the next step", "Titankeys", saved, i),
        Row("7-saved-hover-o", "Saved, hovering O: one step down", "Titankeys", saved, o),
        Row("8-favourite", "Favourite: the heart badge, I spent", "Titankeys", favourite),
        Row("9-favourite-hover-i", "Favourite, hovering I: the top step, spent and dimmer", "Titankeys", favourite, i),
        Row("10-favourite-hover-o", "Favourite, hovering O: one step down", "Titankeys", favourite, o),
        Row("11-built-in", "A word the dictionary knows, nothing hovered", "hello", builtIn),
        Row("12-built-in-hover-o", "Dictionary word, hovering O", "hello", builtIn, o),
        Row("13-long-word", "Long word, hovering O: shortened in the middle to fit", "supercalifragilisticexpialidocious", saved, o),
        Row("14-long-word-rest", "Long word, nothing hovered", "supercalifragilisticexpialidocious", saved),
        Row("15-long-word-spent", "Longer favourite, hovering I: the longest pill, so the word loses its middle", "pneumonoultramicroscopicsilicovolcanoconiosis", favourite, i),
    )
    // The hint's frames, the first 3 times the menu opens, at times along its run.
    val slots = listOf(i, o, p)
    val each = MenuKeys.HINT_SHOW_MILLIS + MenuKeys.HINT_REST_MILLIS
    val hint = listOf(0L, 700L, MenuKeys.HINT_SHOW_MILLIS + 100, each + 700, 2 * each + 700, MenuKeys.hintMillis(3) + 100).map { millis ->
        Row("hint-${millis}ms", "First-time hint at ${"%.1f".format(millis / 1000f)} s", "Titankeys", new, MenuKeys.hint(millis, slots))
    }
    for (dark in listOf(true, false)) {
        val suffix = if (dark) "" else "-light"
        for ((sheet, rows) in listOf("menu-all" to states, "menu-hint" to hint)) {
            val image = BufferedImage(WIDTH, rows.size * (ROW + CAPTION), BufferedImage.TYPE_INT_RGB)
            for ((index, row) in rows.withIndex()) {
                val single = draw(row, dark, base, strings, drawables)
                image.createGraphics().apply { drawImage(single, 0, index * (ROW + CAPTION), null); dispose() }
                if (sheet == "menu-all") ImageIO.write(single, "png", File(folder, "menu-${row.name}$suffix.png"))
            }
            ImageIO.write(image, "png", File(folder, "$sheet$suffix.png"))
        }
    }
    println("Drawn to $folder")
}

private class Row(val name: String, val caption: String, val word: String, val step: MenuKeys.Step, val hover: Int = -1)

private fun draw(row: Row, dark: Boolean, base: Font, strings: String, drawables: File): BufferedImage {
    val image = BufferedImage(WIDTH, ROW + CAPTION, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    val paper = if (dark) Color.BLACK else Color.WHITE
    val ink = if (dark) Color.WHITE else Color.BLACK
    val muted = if (dark) Color.GRAY else Color.DARK_GRAY
    g.color = if (dark) Color(0x22, 0x22, 0x22) else Color(0xEE, 0xEE, 0xEE)
    g.fillRect(0, 0, WIDTH, CAPTION)
    g.color = Color.GRAY
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, 24)
    g.drawString(row.caption, 24, CAPTION - 16)
    g.color = paper
    g.fillRect(0, CAPTION, WIDTH, ROW)
    g.translate(0, CAPTION)
    val side = Layout.KEY_SIDE_DP * DENSITY
    val icon = 22 * DENSITY
    val slots = listOf(MenuKeys.first(row.step), MenuKeys.second(row.step, forget = true), null)
    fun buttonAt(column: Int) = slots[column - MenuKeys.SAVE]
    val hovered = row.hover
    val word = base.deriveFont(17 * DENSITY)
    val bold = base.deriveFont(Font.BOLD, 17 * DENSITY * .88f)
    // The hovered pill: the button's name in it, then its icon. The spent heart's is dimmer.
    var freeRight = MenuKeys.wordRight(WIDTH.toFloat(), side, 8 * DENSITY)
    var pill: Pair<Float, Float>? = null
    val spent = hovered == MenuKeys.SAVE && buttonAt(hovered) == MenuKeys.Button.Favourited
    if (hovered >= 0) {
        val name = name(strings, buttonAt(hovered))
        g.font = bold
        pill = MenuKeys.pill(WIDTH.toFloat(), side, hovered, g.fontMetrics.stringWidth(name).toFloat(), icon, 18 * DENSITY,
            12 * DENSITY, 11 * DENSITY)
        freeRight = pill.first - 8 * DENSITY
        val height = 42 * DENSITY
        g.color = faded(ORANGE, if (spent) .12f else .24f, paper)
        g.fill(RoundRectangle2D.Float(pill.first, (ROW - height) / 2, pill.second - pill.first, height, height, height))
        g.color = faded(ORANGE, if (spent) .6f else 1f, paper)
        g.drawString(name, pill.first + 18 * DENSITY, ROW / 2f + (g.fontMetrics.ascent - g.fontMetrics.descent) / 2f)
    }
    // The word and its badge, centred in the room left of the buttons or of the pill, shortened in the middle to fit.
    g.font = word
    val mark = MenuKeys.mark(row.step)
    val badge = if (mark == null) 0f else (14 + 8) * DENSITY
    val wordLeft = side
    val room = freeRight - wordLeft - badge
    val shown = "“" + MenuKeys.shorten(row.word) { g.fontMetrics.stringWidth("“$it”") <= room } + "”"
    val block = g.fontMetrics.stringWidth(shown) + badge
    val left = MenuKeys.wordCentre(wordLeft, freeRight) - block / 2
    val baseline = ROW / 2f + (g.fontMetrics.ascent - g.fontMetrics.descent) / 2f
    g.color = ink
    g.drawString(shown, left, baseline)
    if (mark != null) {
        val size = 14 * DENSITY
        val x = left + block - size / 2
        val y = baseline - g.fontMetrics.ascent * .55f
        drawVector(g, File(drawables, (if (mark == MenuKeys.Mark.Heart) "menu_favourited" else "menu_mark_saved") + ".xml").readText(),
            x - size / 2, y - size / 2, size, if (mark == MenuKeys.Mark.Heart) Color(ORANGE) else muted)
    }
    // The icons over their keys. The pill covers the ones to its left; the others go grey while it shows.
    for ((index, button) in slots.withIndex()) {
        val column = MenuKeys.SAVE + index
        val (l, r) = MenuKeys.slot(WIDTH.toFloat(), side, column)
        val centre = (l + r) / 2
        val inPill = column == hovered
        if (pill != null && !inPill && centre in pill.first..pill.second) continue
        val colour = when {
            inPill -> faded(ORANGE, if (spent) .6f else 1f, paper)
            button == MenuKeys.Button.Favourited -> faded(ORANGE, .35f, paper)
            pill != null -> muted
            else -> ink
        }
        drawVector(g, File(drawables, "${drawable(button)}.xml").readText(), centre - icon / 2, (ROW - icon) / 2, icon, colour)
    }
    g.dispose()
    return image
}

/** The drawable of each button, as Strip.icon has it: run.sh checks that it still does. Null is Back. */
private fun drawable(button: MenuKeys.Button?) = when (button) {
    MenuKeys.Button.Save -> "menu_save"
    MenuKeys.Button.Favourite -> "menu_favourite"
    MenuKeys.Button.Favourited -> "menu_favourited"
    MenuKeys.Button.Unfavourite -> "menu_unfavourite"
    MenuKeys.Button.Unsave -> "menu_unsave"
    MenuKeys.Button.Forget -> "menu_forget"
    null -> "panel_back"
}

private fun name(strings: String, button: MenuKeys.Button?) = string(strings, when (button) {
    MenuKeys.Button.Save -> "strip_save"
    MenuKeys.Button.Favourite -> "strip_favourite"
    MenuKeys.Button.Favourited -> "strip_favourited"
    MenuKeys.Button.Unfavourite -> "strip_unfavourite"
    MenuKeys.Button.Unsave -> "strip_unsave"
    MenuKeys.Button.Forget -> "strip_forget"
    null -> "strip_back"
})

private fun string(strings: String, name: String) =
    Regex("<string name=\"$name\">([^<]*)</string>").find(strings)?.groupValues?.get(1) ?: error("No string $name")

/** [colour] at [alpha] over [paper], as GuideMotion.faded draws it on the bar. */
private fun faded(colour: Int, alpha: Float, paper: Color): Color {
    val c = Color(colour)
    fun mix(a: Int, b: Int) = (a * alpha + b * (1 - alpha)).toInt().coerceIn(0, 255)
    return Color(mix(c.red, paper.red), mix(c.green, paper.green), mix(c.blue, paper.blue))
}

/** A vector drawable's paths, filled or stroked as it says, in [colour], scaled from its 24 by 24 viewport into [size]. */
private fun drawVector(g: Graphics2D, xml: String, x: Float, y: Float, size: Float, colour: Color) {
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
private const val CAPTION = 44
/** The orange accent, GuideColours.ORANGE. */
private const val ORANGE = 0xEF804E
