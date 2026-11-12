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
 * Draws the word menu in every state of its ladder, with and without a hover, on the default keys and on a moved calibration,
 * frames of each animation and of each icon's change, to look at without the phone. The real [MenuKeys] picks and places the
 * buttons over the calibrated keys ([KeyGeometry]), sizes the pill and shortens the word, the real [MenuMotion] draws the
 * animations and the icons' changes, and the icons are the app's own vector drawables, in Ink's font. The sizes are Strip's, at
 * the density of the owner's screenshots. Each sheet is a PNG in the folder given, dark and light.
 */
fun main(args: Array<String>) {
    val folder = File(args.getOrElse(0) { "menu-render" }).apply { mkdirs() }
    val drawables = File(args.getOrElse(1) { "android/src/main/res/drawable" })
    val strings = File(args.getOrElse(2) { "android/src/main/res/values/strings.xml" }).readText()
    val fonts = File(args.getOrElse(3) { "android/src/main/res/font" })
    fun font(name: String) = runCatching { Font.createFont(Font.TRUETYPE_FONT, File(fonts, name)) }.getOrElse { Font(Font.SANS_SERIF, Font.PLAIN, 1) }
    val regular = font("inter_regular.otf")
    val medium = font("inter_medium.otf")
    fun state(saved: Boolean = false, favourite: Boolean = false, learned: Boolean = false) =
        MenuKeys.state(saved, favourite, false, learned, false)
    val (i, o, p) = Triple(MenuKeys.SAVE, MenuKeys.FORGET, MenuKeys.BACK)
    val moved = KeyAlignment(.05f, .93f)
    val states = listOf(
        Row("1-not-saved", "Not saved (any word, \"hello\" too): I saves, O has nothing", "Titankeys", state()),
        Row("2-not-saved-hover-i", "Not saved, hovering I (or K)", "Titankeys", state(), i),
        Row("3-learned", "Learned from typing, not saved: O forgets it", "Titankeys", state(learned = true)),
        Row("4-learned-hover-o", "Learned, hovering O (or L)", "Titankeys", state(learned = true), o),
        Row("5-hover-p", "Hovering P (or Backspace): Back has no pill", "Titankeys", state(learned = true), p),
        Row("6-saved", "Saved: I favourites, O unsaves", "Titankeys", state(saved = true)),
        Row("7-saved-hover-i", "Saved, hovering I", "Titankeys", state(saved = true), i),
        Row("8-saved-hover-o", "Saved, hovering O: the broken bookmark", "Titankeys", state(saved = true), o),
        Row("9-favourite", "Favourite: the filled heart is orange, I spent", "Titankeys", state(saved = true, favourite = true)),
        Row("10-favourite-hover-i", "Favourite, hovering I: already a favourite", "Titankeys", state(saved = true, favourite = true), i),
        Row("11-favourite-hover-o", "Favourite, hovering O: Unfavourite, back to saved", "Titankeys", state(saved = true, favourite = true), o),
        Row("12-one-letter", "One letter: the same ladder, never Forget", "I", state(learned = true).copy(learned = false)),
        Row("13-long-hover-o", "Long word, hovering O: the pill slides over its fading end", "pneumonoultramicroscopicsilicovolcanoconiosis",
            state(saved = true), o),
        Row("14-moved-calibration", "A moved calibration (Q at 5%, P at 93%): the buttons follow the keys", "Titankeys",
            state(saved = true), alignment = moved),
        Row("15-moved-hover-o", "Moved calibration, hovering O", "Titankeys", state(saved = true), o, alignment = moved),
    )
    // Frames of each animation over the word, and of each icon's change on its button.
    val motions = MenuKeys.Motion.entries.flatMap { motion ->
        val total = MenuMotion.millis(motion)
        val (from, to) = when (motion) {
            MenuKeys.Motion.Save -> state() to state(saved = true)
            MenuKeys.Motion.Favourite -> state(saved = true) to state(saved = true, favourite = true)
            MenuKeys.Motion.Unfavourite -> state(saved = true, favourite = true) to state(saved = true)
            MenuKeys.Motion.Unsave -> state(saved = true) to state()
            MenuKeys.Motion.Forget -> state(learned = true) to state(learned = true)
            MenuKeys.Motion.Echo -> state(saved = true, favourite = true) to state(saved = true, favourite = true)
        }
        val frames = if (motion == MenuKeys.Motion.Unfavourite) 10 else 8
        (0 until frames).map { index ->
            val millis = total * index / frames
            Row("$motion-${millis}ms", "$motion at $millis of $total ms", "Titankeys", to, motion = motion to millis, from = from)
        }
    }
    for (dark in listOf(true, false)) {
        val suffix = if (dark) "" else "-light"
        for ((sheet, rows) in listOf("menu-all" to states, "menu-motion" to motions)) {
            val image = BufferedImage(WIDTH, rows.size * (ROW + CAPTION), BufferedImage.TYPE_INT_RGB)
            for ((index, row) in rows.withIndex()) {
                val single = draw(row, dark, regular, medium, strings, drawables)
                image.createGraphics().apply { drawImage(single, 0, index * (ROW + CAPTION), null); dispose() }
                if (sheet == "menu-all") ImageIO.write(single, "png", File(folder, "menu-${row.name}$suffix.png"))
            }
            ImageIO.write(image, "png", File(folder, "$sheet$suffix.png"))
        }
    }
    println("Drawn to $folder")
}

private class Row(val name: String, val caption: String, val word: String, val state: MenuKeys.State, val hover: Int = -1,
                  val motion: Pair<MenuKeys.Motion, Long>? = null, val from: MenuKeys.State? = null,
                  val alignment: KeyAlignment = KeyAlignment.DEFAULT)

private fun draw(row: Row, dark: Boolean, regular: Font, medium: Font, strings: String, drawables: File): BufferedImage {
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
    g.font = regular.deriveFont(22f)
    g.drawString(row.caption, 24, CAPTION - 14)
    g.color = paper
    g.fillRect(0, CAPTION, WIDTH, ROW)
    g.translate(0, CAPTION)
    val icon = 22 * DENSITY
    val step = row.state.step
    val slots = listOf(MenuKeys.first(step), MenuKeys.second(step, row.state.learned), null)
    fun buttonAt(column: Int) = slots[column - MenuKeys.SAVE]
    val hovered = row.hover.takeIf { MenuKeys.pills(it, step, row.state.learned) } ?: -1
    // The real keys' left edge, then the word placed once in the room left of the buttons, in Ink's font, never another colour.
    g.font = regular.deriveFont(17 * DENSITY)
    val wordLeft = MenuKeys.slot(WIDTH.toFloat(), 0, row.alignment).first.coerceAtLeast(8 * DENSITY)
    val wordRight = MenuKeys.wordRight(WIDTH.toFloat(), 8 * DENSITY, row.alignment)
    val shown = MenuKeys.shorten(row.word) { g.fontMetrics.stringWidth(it) <= wordRight - wordLeft }
    val textWidth = g.fontMetrics.stringWidth(shown)
    val centre = MenuKeys.wordCentre(wordLeft, wordRight)
    val left = centre - textWidth / 2
    val baseline = ROW / 2f + (g.fontMetrics.ascent - g.fontMetrics.descent) / 2f
    val middle = baseline - (g.fontMetrics.ascent - g.fontMetrics.descent) / 2f
    val frame = row.motion?.let { (motion, millis) ->
        MenuMotion.frame(motion, millis, MenuMotion.Word(left / DENSITY, (left + textWidth) / DENSITY, middle / DENSITY, MenuMotion.Word.seed(row.word)))
    }
    val orange = Color(ORANGE)
    frame?.sprites?.filter { it.behind }?.forEach { drawSprite(g, drawables, it, orange, paper) }
    val saved = g.transform
    frame?.let {
        g.translate((it.wordShift * DENSITY).toDouble(), 0.0)
        g.translate(centre.toDouble(), middle.toDouble())
        g.scale(it.wordScale.toDouble(), it.wordScale.toDouble())
        g.translate(-centre.toDouble(), -middle.toDouble())
    }
    val alpha = frame?.wordAlpha ?: 1f
    g.color = Color(ink.red, ink.green, ink.blue, (alpha * 255).toInt())
    g.drawString(shown, left, baseline)
    g.transform = saved
    frame?.sprites?.filterNot { it.behind }?.forEach { drawSprite(g, drawables, it, orange, paper) }
    // The hovered pill: its name in Ink's medium weight, kept by its icon. It covers what it slides over.
    var pill: Pair<Float, Float>? = null
    val spent = hovered == MenuKeys.SAVE && buttonAt(hovered) == MenuKeys.Button.Favourited
    if (hovered >= 0) {
        val name = name(strings, buttonAt(hovered))
        g.font = medium.deriveFont(17 * DENSITY * .88f)
        val label = g.fontMetrics.stringWidth(name).toFloat()
        pill = MenuKeys.pill(WIDTH.toFloat(), hovered, label, icon, 18 * DENSITY, 12 * DENSITY, 11 * DENSITY, row.alignment)
        val height = 42 * DENSITY
        val shape = RoundRectangle2D.Float(pill.first, (ROW - height) / 2, pill.second - pill.first, height, height, height)
        if (left + textWidth > pill.first - 20 * DENSITY) {
            val fade = 20 * DENSITY
            g.paint = java.awt.GradientPaint(pill.first - fade, 0f, Color(paper.red, paper.green, paper.blue, 0), pill.first + height / 2, 0f, paper)
            g.fill(java.awt.geom.Rectangle2D.Float(pill.first - fade, 0f, fade + height / 2, ROW.toFloat()))
            g.color = paper
            g.fill(shape)
        }
        g.color = faded(ORANGE, if (spent) .12f else .24f, paper)
        g.fill(shape)
        g.color = faded(ORANGE, if (spent) .6f else 1f, paper)
        g.drawString(name, MenuKeys.pillLabel(pill.second, label, icon, 12 * DENSITY, 11 * DENSITY),
            ROW / 2f + (g.fontMetrics.ascent - g.fontMetrics.descent) / 2f)
    }
    // The icons over their keys: white at rest, the filled heart orange, orange in a pill, grey beside one. While the word's
    // step changes they morph from the last step's icons.
    val fromSlots = row.from?.let { listOf(MenuKeys.first(it.step), MenuKeys.second(it.step, it.learned), null) }
    for ((index, button) in slots.withIndex()) {
        val column = MenuKeys.SAVE + index
        val (l, r) = MenuKeys.slot(WIDTH.toFloat(), column, row.alignment)
        val middleX = (l + r) / 2
        val inPill = column == hovered
        if (pill != null && !inPill && middleX in pill.first..pill.second) continue
        val elapsed = row.motion?.second ?: MenuMotion.ICON_MILLIS
        val layers = if (fromSlots != null && index < 2 && row.motion?.first != MenuKeys.Motion.Echo)
            MenuMotion.icon(fromSlots[index], button, elapsed) else listOf(MenuMotion.Layer(button, 1f, orange = MenuMotion.restingOrange(button)))
        for (layer in layers) {
            if (layer.button == null && index < 2) continue
            val rest = if (pill != null && !inPill) muted else ink
            val colour = if (inPill) orange else mix(rest, orange, layer.orange)
            val size = icon * layer.scale
            val x = middleX - size / 2
            val y = (ROW - size) / 2 + layer.drop * DENSITY
            val clip = g.clip
            if (layer.fill < 1f) g.clip(java.awt.geom.Rectangle2D.Float(x, y + size * (1f - layer.fill), size, size * layer.fill))
            drawVector(g, File(drawables, "${drawable(layer.button)}.xml").readText(), x, y, size, mix(paper, colour, layer.alpha))
            g.clip = clip
        }
    }
    g.dispose()
    return image
}

/** [from] turned towards [to] by [part], from 0 to 1. */
private fun mix(from: Color, to: Color, part: Float): Color {
    val p = part.coerceIn(0f, 1f)
    return Color((from.red + (to.red - from.red) * p).toInt(), (from.green + (to.green - from.green) * p).toInt(),
        (from.blue + (to.blue - from.blue) * p).toInt())
}

/** One particle, as Strip.drawSprite draws it, always orange: a half is cut along the broken heart's crack, dust is a dot. */
private fun drawSprite(g: Graphics2D, drawables: File, sprite: MenuMotion.Sprite, colour: Color, paper: Color) {
    val size = sprite.size * DENSITY
    val x = sprite.x * DENSITY
    val y = sprite.y * DENSITY
    val shown = mix(paper, colour, sprite.alpha)
    if (sprite.shape == MenuMotion.Shape.Dust) {
        g.color = shown
        g.fill(java.awt.geom.Ellipse2D.Float(x - size / 2, y - size / 2, size, size))
        return
    }
    val heart = sprite.shape.name.startsWith("Heart")
    val saved = g.transform
    val clip = g.clip
    g.rotate(Math.toRadians(sprite.turn.toDouble()), x.toDouble(), y.toDouble())
    val left = x - size / 2
    val top = y - size / 2
    val crack = listOf(12.3f to 0f, 12.3f to 6.6f, 10.4f to 10.4f, 13.6f to 12.6f, 11.6f to 17.4f, 11.6f to 24f)
    val half = when {
        sprite.shape.name.endsWith("Left") -> listOf(0f to 0f) + crack + listOf(0f to 24f)
        sprite.shape.name.endsWith("Right") -> listOf(24f to 0f) + crack + listOf(24f to 24f)
        else -> null
    }
    if (half != null) {
        val path = Path2D.Float()
        half.forEachIndexed { index, (px, py) ->
            if (index == 0) path.moveTo(left + px / 24f * size, top + py / 24f * size) else path.lineTo(left + px / 24f * size, top + py / 24f * size)
        }
        path.closePath()
        g.clip(path)
    }
    drawVector(g, File(drawables, (if (heart) "menu_favourited" else "menu_mark_saved") + ".xml").readText(), left, top, size, shown)
    g.clip = clip
    g.transform = saved
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
