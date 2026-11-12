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
 * Draws the word menu in every state of its ladder, with and without a hover, long words under a pill, and frames of each step's
 * animation, to look at without the phone. The real [MenuKeys] picks the buttons, places them over their keys, sizes the hovered
 * pill and shortens the word, the real [MenuMotion] draws the animations, and the icons are the app's own vector drawables. The
 * sizes are Strip's, at the density of the owner's screenshots. Each sheet is a PNG in the folder given, dark and light.
 */
fun main(args: Array<String>) {
    val folder = File(args.getOrElse(0) { "menu-render" }).apply { mkdirs() }
    val drawables = File(args.getOrElse(1) { "android/src/main/res/drawable" })
    val strings = File(args.getOrElse(2) { "android/src/main/res/values/strings.xml" }).readText()
    val font = File(args.getOrElse(3) { "android/src/main/res/font/inter_regular.otf" })
    val base = runCatching { Font.createFont(Font.TRUETYPE_FONT, font) }.getOrElse { Font(Font.SANS_SERIF, Font.PLAIN, 1) }
    fun state(saved: Boolean = false, builtIn: Boolean = false, favourite: Boolean = false, learned: Boolean = false) =
        MenuKeys.state(saved, builtIn, favourite, false, learned, false)
    val (i, o, p) = Triple(MenuKeys.SAVE, MenuKeys.FORGET, MenuKeys.BACK)
    val long = "supercalifragilisticexpialidocious"
    val longer = "pneumonoultramicroscopicsilicovolcanoconiosis"
    val states = listOf(
        Row("1-not-saved", "Not saved, never learned: I saves, O has nothing", "Titankeys", state()),
        Row("2-not-saved-hover-i", "Not saved, hovering I (or K)", "Titankeys", state(), i),
        Row("3-learned", "Learned from typing, not saved: O forgets it", "Titankeys", state(learned = true)),
        Row("4-learned-hover-o", "Learned, hovering O (or L): the pill covers I", "Titankeys", state(learned = true), o),
        Row("5-hover-p", "Hovering P (or Backspace): Back has no pill, just its arrow", "Titankeys", state(learned = true), p),
        Row("6-saved", "Saved: I favourites, O unsaves", "Titankeys", state(saved = true)),
        Row("7-saved-hover-i", "Saved, hovering I", "Titankeys", state(saved = true), i),
        Row("8-saved-hover-o", "Saved, hovering O: Unsave, the broken bookmark", "Titankeys", state(saved = true), o),
        Row("9-favourite", "Favourite: I spent", "Titankeys", state(saved = true, favourite = true)),
        Row("10-favourite-hover-i", "Favourite, hovering I: spent and dimmer", "Titankeys", state(saved = true, favourite = true), i),
        Row("11-favourite-hover-o", "Favourite, hovering O: one step down", "Titankeys", state(saved = true, favourite = true), o),
        Row("12-built-in", "A dictionary word: Favourite, and nothing on O", "hello", state(builtIn = true)),
        Row("13-built-in-hover-i", "Dictionary word, hovering I", "hello", state(builtIn = true), i),
        Row("14-long-word", "Long word, nothing hovered: placed once, never moves", long, state(saved = true)),
        Row("15-long-word-hover-o", "Long word, hovering O: the pill slides over its end, which fades", long, state(saved = true), o),
        Row("16-longer-hover-i", "Longest pill over a longer word", longer, state(saved = true, favourite = true), i),
    )
    // Frames of each step's animation, past the word.
    val motions = MenuKeys.Motion.entries.flatMap { motion ->
        val total = MenuMotion.millis(motion)
        val after = when (motion) {
            MenuKeys.Motion.BookmarkDrop, MenuKeys.Motion.BookmarkFlick -> state(saved = true)
            MenuKeys.Motion.HeartBurst, MenuKeys.Motion.HeartDrift -> state(saved = true, favourite = true)
            MenuKeys.Motion.HeartCrack -> state(saved = true)
            MenuKeys.Motion.BookmarkTear -> state()
        }
        listOf(0f, .2f, .4f, .6f, .8f).map { part ->
            val millis = (total * part).toLong()
            Row("$motion-${millis}ms", "$motion at $millis of $total ms", "Titankeys", after, motion = motion to millis)
        }
    }
    for (dark in listOf(true, false)) {
        val suffix = if (dark) "" else "-light"
        for ((sheet, rows) in listOf("menu-all" to states, "menu-motion" to motions)) {
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

private class Row(val name: String, val caption: String, val word: String, val state: MenuKeys.State, val hover: Int = -1,
                  val motion: Pair<MenuKeys.Motion, Long>? = null)

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
    val step = row.state.step
    val slots = listOf(MenuKeys.first(step), MenuKeys.second(step, row.state.forget), null)
    fun buttonAt(column: Int) = slots[column - MenuKeys.SAVE]
    val hovered = row.hover.takeIf { MenuKeys.pills(it, step, row.state.forget) } ?: -1
    // The word, placed once in the room left of the buttons, shortened in the middle to fit it. No quotes.
    g.font = base.deriveFont(17 * DENSITY)
    val wordLeft = side
    val wordRight = MenuKeys.wordRight(WIDTH.toFloat(), side, 8 * DENSITY)
    val shown = MenuKeys.shorten(row.word) { g.fontMetrics.stringWidth(it) <= wordRight - wordLeft }
    val textWidth = g.fontMetrics.stringWidth(shown)
    val left = MenuKeys.wordCentre(wordLeft, wordRight) - textWidth / 2
    val baseline = ROW / 2f + (g.fontMetrics.ascent - g.fontMetrics.descent) / 2f
    g.color = ink
    g.drawString(shown, left, baseline)
    // A step's animation, past the word's end.
    row.motion?.let { (motion, millis) ->
        for (sprite in MenuMotion.frame(motion, millis, (left + textWidth) / DENSITY + 10f, (baseline - g.fontMetrics.ascent * .5f) / DENSITY))
            drawSprite(g, drawables, sprite, if (sprite.shape.name.startsWith("Heart")) Color(ORANGE) else ink, paper)
    }
    // The hovered pill: the button's name in it, then its icon. It covers what it slides over; a long word fades under its edge.
    var pill: Pair<Float, Float>? = null
    val spent = hovered == MenuKeys.SAVE && buttonAt(hovered) == MenuKeys.Button.Favourited
    if (hovered >= 0) {
        val name = name(strings, buttonAt(hovered))
        g.font = base.deriveFont(Font.BOLD, 17 * DENSITY * .88f)
        pill = MenuKeys.pill(WIDTH.toFloat(), side, hovered, g.fontMetrics.stringWidth(name).toFloat(), icon, 18 * DENSITY,
            12 * DENSITY, 11 * DENSITY)
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
        g.drawString(name, pill.first + 18 * DENSITY, ROW / 2f + (g.fontMetrics.ascent - g.fontMetrics.descent) / 2f)
    }
    // The icons over their keys. The pill covers the ones to its left; the others go grey while it shows.
    for ((index, button) in slots.withIndex()) {
        val column = MenuKeys.SAVE + index
        if (index == 1 && button == null) continue
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

/** One shape of an animation, as Strip.drawSprite draws it: a half is cut along the broken heart's crack. */
private fun drawSprite(g: Graphics2D, drawables: File, sprite: MenuMotion.Sprite, colour: Color, paper: Color) {
    val heart = sprite.shape.name.startsWith("Heart")
    val size = sprite.size * DENSITY
    val x = sprite.x * DENSITY
    val y = sprite.y * DENSITY
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
    fun mix(a: Int, b: Int) = (a * sprite.alpha + b * (1 - sprite.alpha)).toInt().coerceIn(0, 255)
    drawVector(g, File(drawables, (if (heart) "menu_favourited" else "menu_mark_saved") + ".xml").readText(), left, top, size,
        Color(mix(colour.red, paper.red), mix(colour.green, paper.green), mix(colour.blue, paper.blue)))
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
