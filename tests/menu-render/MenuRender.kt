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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Draws the word menu in every state of its ladder, with and without a hover, on the default keys and on a moved calibration,
 * frames of each animation and of each icon's change, to look at without the phone. The real [MenuKeys] picks and places the
 * buttons over the calibrated keys ([KeyGeometry]), sizes the pill and shortens the word, the real [MenuMotion] draws the
 * animations and the icons' changes, and the icons are the app's own vector drawables, in Ink's font. The sizes are Strip's, at
 * the density of the owner's screenshots. Each sheet is a PNG in the folder given, dark and light.
 */
fun main(args: Array<String>) {
    // ACCENT=off draws everything as with Settings > Orange accents off: Accent's cream in place of orange.
    if (System.getenv("ACCENT") == "off") Accent.on = false
    val folder = File(args.getOrElse(0) { "menu-render" }).apply { mkdirs() }
    val drawables = File(args.getOrElse(1) { "android/src/main/res/drawable" })
    val strings = File(args.getOrElse(2) { "android/src/main/res/values/strings.xml" }).readText()
    val fonts = File(args.getOrElse(3) { "android/src/main/res/font" })
    fun font(name: String) = runCatching { Font.createFont(Font.TRUETYPE_FONT, File(fonts, name)) }.getOrElse { Font(Font.SANS_SERIF, Font.PLAIN, 1) }
    val regular = font("inter_regular.otf")
    val medium = font("inter_medium.otf")
    val bold = font("inter_bold.otf")
    MEDIUM_FONT = medium
    fun state(saved: Boolean = false, favourite: Boolean = false, learned: Boolean = false, builtIn: Boolean = false,
              oneLetter: Boolean = false) = MenuKeys.state(saved, favourite, false, learned, oneLetter, builtIn)
    val (i, o, p) = Triple(MenuKeys.SAVE, MenuKeys.FORGET, MenuKeys.BACK)
    val moved = KeyAlignment(.05f, .93f)
    val states = listOf(
        Row("1-not-saved", "Not saved, never learned: I saves, O has nothing", "Titankeys", state()),
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
        Row("12-one-letter", "One letter in the dictionary: saved, never taken out", "I", state(builtIn = true, oneLetter = true)),
        Row("12b-dictionary", "A dictionary word counts as saved: O takes it out of the dictionary", "and", state(builtIn = true)),
        Row("12c-dictionary-hover-o", "Dictionary word, hovering O: Forget, with Undo", "and", state(builtIn = true), o),
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
            MenuKeys.Motion.SavedEcho -> state(saved = true) to state(saved = true)
        }
        val frames = 10
        (0 until frames).map { index ->
            val millis = total * index / frames
            Row("$motion-${millis}ms", "$motion at $millis of $total ms", "Titankeys", to, motion = motion to millis, from = from)
        }
    }
    // The ambient status at rest: a favourite's faint hearts, then a saved word's swaying ribbon.
    // The first mark waits a whole cycle after the menu comes to rest, so nothing follows the open's echo or a step's burst.
    val ambient = listOf(0L, 1200L, 2500L + 400L, 2500L + 800L, 2500L + 1200L, 5000L + 400L).map { millis ->
        Row("ambient-favourite-${millis}ms", "Ambient, favourite, at $millis ms", "Titankeys", state(saved = true, favourite = true), ambient = millis)
    } + listOf(0L, 2000L, 3500L + 150L, 3500L + 300L, 3500L + 450L, 3500L + 600L).map { millis ->
        Row("ambient-saved-${millis}ms", "Ambient, saved, at $millis ms", "Titankeys", state(saved = true), ambient = millis)
    }
    for (dark in listOf(true, false)) {
        val suffix = if (dark) "" else "-light"
        for ((sheet, rows) in listOf("menu-all" to states, "menu-motion" to motions, "menu-ambient" to ambient, "menu-press" to pressRows())) {
            val image = BufferedImage(WIDTH, rows.size * (ROW + CAPTION), BufferedImage.TYPE_INT_RGB)
            for ((index, row) in rows.withIndex()) {
                val single = draw(row, dark, regular, medium, strings, drawables)
                image.createGraphics().apply { drawImage(single, 0, index * (ROW + CAPTION), null); dispose() }
                if (sheet == "menu-all" || sheet == "menu-press") ImageIO.write(single, "png", File(folder, "menu-${row.name}$suffix.png"))
            }
            ImageIO.write(image, "png", File(folder, "$sheet$suffix.png"))
        }
    }
    // The best word in each weight the owner could have, to pick the one that reads as bold without looking heavy.
    barSheet(folder, regular, medium, "Medium", -668 to 2272, drawables)
    barSheet(folder, regular, bold, "Bold", -685 to 2279, drawables)
    println("Drawn to $folder")
}

/**
 * The hover before and after each press, up the ladder on I (and K) and down it on O (and L), as the real [SteadyKey] keeps it
 * through the touchpad's events in the order the owner's phone sends them (tests/word-menu, [MenuBar]): the key down, up 86 ms
 * later, the touchpad's lift for the press 124 ms after that, and then nothing while the finger stays still. Each frame's pill
 * is the one the strip draws from the mark that the events leave.
 */
private fun pressRows(): List<Row> {
    val rows = mutableListOf<Row>()
    var count = 0
    fun frame(bar: MenuBar, caption: String, from: MenuKeys.State? = null, motion: Pair<MenuKeys.Motion, Long>? = null) {
        count++
        rows += Row("press-%02d".format(count), caption, "Titankeys", bar.state, bar.mark, motion, from)
    }
    fun press(bar: MenuBar, key: String, time: Long) {
        val from = bar.state
        bar.keyDown(key, time)
        bar.keyUp(key, time + 86)
        val motion = MenuKeys.motion(from.step, bar.state.step)
        fun playing(later: Long) = motion?.takeIf { later < MenuMotion.millis(it) }?.let { it to later }
        bar.at(time + 1)
        frame(bar, "$key pressed, 1 ms: ${bar.state.step}", from, playing(1))
        bar.lift(time + 210)
        bar.at(time + 400)
        frame(bar, "$key, 400 ms: the touchpad lifted at 210 ms (before the fix, the pill went at 360)", from, playing(400))
        bar.at(time + 1_500)
        frame(bar, "$key, 1.5 s: the finger still on $key, the touchpad quiet")
    }
    for ((up, down) in listOf("I" to "O", "K" to "L")) {
        val bar = MenuBar(MenuKeys.State(MenuKeys.Step.New, true))
        bar.touch(up, 0)
        frame(bar, "Resting on $up: learned, not saved")
        press(bar, up, 1_000)
        press(bar, up, 3_000)
        press(bar, up, 5_000)
        bar.touch(down, 7_000)
        frame(bar, "Moved to $down")
        press(bar, down, 8_000)
        press(bar, down, 10_000)
    }
    return rows
}

private class Row(val name: String, val caption: String, val word: String, val state: MenuKeys.State, val hover: Int = -1,
                  val motion: Pair<MenuKeys.Motion, Long>? = null, val from: MenuKeys.State? = null,
                  val alignment: KeyAlignment = KeyAlignment.DEFAULT, val ambient: Long? = null)

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
    val slots = MenuKeys.choices(row.state).let { listOf(it.i, it.o, null) }
    fun buttonAt(column: Int) = slots[column - MenuKeys.SAVE]
    val hovered = row.hover.takeIf { MenuKeys.pills(it, row.state) } ?: -1
    // The real keys' left edge, then the word placed once in the room left of the buttons, in Ink's font, never another colour.
    g.font = regular.deriveFont(20 * DENSITY)
    val wordLeft = MenuKeys.slot(WIDTH.toFloat(), 0, row.alignment).first.coerceAtLeast(8 * DENSITY)
    val wordRight = MenuKeys.wordRight(WIDTH.toFloat(), 8 * DENSITY, row.alignment)
    val shown = MenuKeys.shorten(row.word) { g.fontMetrics.stringWidth(it) <= wordRight - wordLeft }
    val textWidth = g.fontMetrics.stringWidth(shown)
    val centre = MenuKeys.wordCentre(wordLeft, wordRight)
    val left = centre - textWidth / 2
    // As Strip has it: the middle of the small letters on the bar's centre line.
    val baseline = StripFit.baseline(ROW / 2f, 20 * DENSITY)
    val middle = baseline - (g.fontMetrics.ascent - g.fontMetrics.descent) / 2f
    val frame = row.motion?.let { (motion, millis) ->
        MenuMotion.frame(motion, millis, MenuMotion.Word(left / DENSITY, (left + textWidth) / DENSITY, middle / DENSITY, MenuMotion.Word.seed(row.word)))
    }
    val orange = Color(ORANGE)
    frame?.sprites?.filter { it.behind }?.forEach { drawSprite(g, drawables, it, orange, paper) }
    val saved = g.transform
    frame?.let {
        g.translate((it.wordShift * DENSITY).toDouble(), (it.wordDrop * DENSITY).toDouble())
        g.translate(centre.toDouble(), middle.toDouble())
        g.scale(it.wordScale.toDouble(), it.wordScale.toDouble())
        g.translate(-centre.toDouble(), -middle.toDouble())
    }
    val alpha = frame?.wordAlpha ?: 1f
    g.color = Color(ink.red, ink.green, ink.blue, (alpha * 255).toInt())
    g.drawString(shown, left, baseline)
    g.transform = saved
    frame?.sprites?.filterNot { it.behind }?.forEach { drawSprite(g, drawables, it, orange, paper) }
    row.ambient?.let { millis ->
        val box = MenuMotion.Word(left / DENSITY, (left + textWidth) / DENSITY, middle / DENSITY, MenuMotion.Word.seed(row.word))
        MenuMotion.ambient(step, millis, box, false).forEach { drawSprite(g, drawables, it, orange, paper) }
    }
    // The hovered pill: its name in Ink's medium weight, kept by its icon. It covers what it slides over.
    var pill: Pair<Float, Float>? = null
    val spent = hovered == MenuKeys.SAVE && buttonAt(hovered) == MenuKeys.Button.Favourited
    if (hovered >= 0) {
        val name = name(strings, buttonAt(hovered))
        g.font = medium.deriveFont(20 * DENSITY * .88f)
        val label = g.fontMetrics.stringWidth(name).toFloat()
        pill = MenuKeys.pill(WIDTH.toFloat(), hovered, label, icon, 18 * DENSITY, 12 * DENSITY, 11 * DENSITY, row.alignment)
        // As Strip: the pill grows with Settings > Size, but never past its share of the bar, which keeps its height.
        val height = minOf(42 * DENSITY, ROW * 42f / 56f)
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
            StripFit.baseline(ROW / 2f, 20 * DENSITY * .88f))
    }
    // The icons over their keys: white at rest, the filled heart orange, orange in a pill, grey beside one. While the word's
    // step changes they morph from the last step's icons.
    val fromSlots = row.from?.let { from -> MenuKeys.choices(from).let { listOf(it.i, it.o, null) } }
    for ((index, button) in slots.withIndex()) {
        val column = MenuKeys.SAVE + index
        val (l, r) = MenuKeys.slot(WIDTH.toFloat(), column, row.alignment)
        val middleX = (l + r) / 2
        val inPill = column == hovered
        if (pill != null && !inPill && middleX in pill.first..pill.second) continue
        val elapsed = row.motion?.second ?: MenuMotion.ICON_MILLIS
        val layers = if (fromSlots != null && index < 2 && row.motion?.first != MenuKeys.Motion.Echo &&
            row.motion?.first != MenuKeys.Motion.SavedEcho)
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
    if (sprite.shape == MenuMotion.Shape.Crack) {
        // The crack drawn down the heart, as far as it has got: a gap in the bar's colour.
        val crack = listOf(12.3f to 0f, 12.3f to 6.6f, 10.4f to 10.4f, 13.6f to 12.6f, 11.6f to 17.4f, 11.6f to 24f)
        val left = x - size / 2
        val top = y - size / 2
        val bottom = top + size * sprite.amount
        val path = Path2D.Float()
        val points = crack.map { (px, py) -> left + px / 24f * size to top + py / 24f * size }
        path.moveTo(points[0].first, points[0].second)
        for (index in 1 until points.size) {
            val (px, py) = points[index]
            val (qx, qy) = points[index - 1]
            if (py <= bottom) path.lineTo(px, py)
            else {
                if (bottom > qy) path.lineTo(qx + (px - qx) * (bottom - qy) / (py - qy), bottom)
                break
            }
        }
        g.color = paper
        g.stroke = BasicStroke(size * .1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(path)
        return
    }
    if (sprite.shape == MenuMotion.Shape.Shard) {
        val saved = g.transform
        g.rotate(Math.toRadians(sprite.turn.toDouble()), x.toDouble(), y.toDouble())
        val path = Path2D.Float()
        path.moveTo(x, y - size * .7f)
        path.lineTo(x + size * .45f, y + size * .4f)
        path.lineTo(x - size * .45f, y + size * .3f)
        path.closePath()
        g.color = shown
        g.fill(path)
        g.transform = saved
        return
    }
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
    val file = when {
        heart -> "menu_favourited"
        sprite.shape.name.startsWith("Bookmark") -> "menu_mark_saved"
        else -> "menu_sparkle"
    }
    drawVector(g, File(drawables, "$file.xml").readText(), left, top, size, shown)
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
/** The owner's screenshots' density, times Settings > Size's factor when one is given (MENU_SIZE: normal, bigger, biggest). */
private val DENSITY = 1.55f * when (System.getenv("MENU_SIZE")) {
    "bigger" -> FixedScale.BIGGER
    "biggest" -> FixedScale.BIGGEST
    else -> FixedScale.NORMAL
}
private const val WIDTH = 1076
/** The bar: 56 dp at Normal, the same height at every Settings > Size (Strip.BAR_DP). */
private val ROW = (56 * 1.55f).toInt()
private const val CAPTION = 44
/** The accent, from Accent: orange, or cream with Orange accents off (ACCENT=off). */
private val ORANGE get() = Accent.colour and 0xFFFFFF

/**
 * The bar's own texts, at TitanKeys' fixed scale: the three words, all 20 dp with the best one in the middle in [best]'s weight
 * (owner, 2026-11-13: "I want it consistent but bolded is a nice touch"), long words fitted by the real [StripFit], the clip
 * offer and its hint, the modifier bar's chip and hint, the automatic capital's arrow, the Forget notice, and the panel's labels.
 * Each word is placed where Android puts it in Strip's 56 dp view, and the middle of its small letters is measured against the
 * bar's centre line, which the short marks at each side show: it must be within 1 px. Sizes in dp, at 1.5625 px to a dp.
 */
private fun barSheet(folder: File, regular: Font, best: Font, bestName: String, bestBox: Pair<Int, Int>, drawables: File) {
    // The text grows with Settings > Size (MENU_SIZE), and the bar keeps Normal's height (Strip.BAR_DP).
    val scale = 1.5625f * DENSITY / 1.55f
    val width = 1076
    val bar = Math.round(56 * 1.5625f)
    val caption = 44
    val side = 8 * scale
    val pad = (6 + 2) * scale
    val regularBox = -660 to 2269
    data class BarRow(val caption: String, val draw: (Graphics2D, Color, Color, Color) -> Unit)
    val measured = ArrayList<Float>()
    /**
     * Where Android puts the baseline of one line of Inter at [size] px in Strip's view, [bar] px tall. With [fontPadding], gravity
     * centred on the font's whole box from its lowest to its highest point ([box], in its 2048 units), as the words first were.
     * With [ownLine], centred on its own small letters, as they were until a shrunk word floated up. Otherwise as now: on the line
     * of words [lineSize] px big, at [StripFit.top].
     */
    fun baseline(size: Float, fontPadding: Boolean, box: Pair<Int, Int>, ownLine: Boolean = false, lineSize: Float = size): Float {
        val ascent = -size * 1984 / 2048f
        val descent = size * 494 / 2048f
        if (!fontPadding && !ownLine) return (StripFit.top(bar, lineSize, floor(ascent).toInt()) - floor(ascent).toInt()).toFloat()
        val top = if (fontPadding) -size * box.second / 2048f else ascent
        val bottom = if (fontPadding) -size * box.first / 2048f else descent
        val lift = if (fontPadding) 0 else Math.round(-(ascent + descent) - size * StripFit.X_HEIGHT)
        val textTop = floor(top).toInt()
        val textHeight = ceil(bottom).toInt() - textTop
        return ((bar - lift - textHeight) shr 1) - textTop.toFloat()
    }
    /** Every word's baseline in the rows as they are now, with its scale: a shrunk one must share the full-size words' line. */
    val lines = ArrayList<Pair<Float, Float>>()
    /** The words a row shrank to fit. */
    val shrunk = ArrayList<String>()
    /** How far the middle of the small letters sits below the bar's centre line, in px. */
    fun off(baseline: Float, size: Float) = baseline - size * StripFit.X_HEIGHT / 2 - bar / 2f
    fun words(g: Graphics2D, ink: Color, shown: List<String>, sizes: List<Float>, fonts: List<Pair<Font, Pair<Int, Int>>>,
              fontPadding: Boolean, ownLine: Boolean = false): List<Float> {
        val room = width - 2 * side
        val fits = StripFit.fit(shown, room, pad, { text ->
            g.font = best.deriveFont(20 * scale); g.fontMetrics.stringWidth(text).toFloat()
        }, lend = false)
        var left = side
        val offs = ArrayList<Float>()
        for ((index, slot) in fits.withIndex()) {
            val size = sizes[index] * scale * slot.scale
            g.font = fonts[index].first.deriveFont(size)
            g.color = ink
            val textWidth = g.fontMetrics.stringWidth(slot.text)
            val line = baseline(size, fontPadding, fonts[index].second, ownLine, sizes[index] * scale)
            // Only a full-size word's letters must be centred: a shrunk one sits on their line, a little lower.
            if (slot.text.isNotEmpty() && slot.scale == 1f) offs += off(line, size)
            if (slot.text.isNotEmpty() && !fontPadding && !ownLine) lines += line to slot.scale
            if (slot.scale < 1f) shrunk += slot.text
            g.drawString(slot.text, left + slot.width / 2 - textWidth / 2, line)
            left += slot.width
        }
        return offs
    }
    fun ladder(g: Graphics2D, ink: Color, shown: List<String>): List<Float> {
        val offs = words(g, ink, shown, listOf(20f, 20f, 20f), listOf(regular to regularBox, best to bestBox, regular to regularBox), false)
        measured += offs
        return offs
    }
    fun said(offs: List<Float>) = offs.joinToString(", ") { "%+.1f".format(it) } + " px"
    /** The baselines of a row's words, top to bottom, for its caption. */
    fun baselines(g: Graphics2D, ink: Color, shown: List<String>, ownLine: Boolean): String {
        val before = lines.size
        val fonts = listOf(regular to regularBox, best to bestBox, regular to regularBox)
        if (ownLine) {
            val room = width - 2 * side
            val fits = StripFit.fit(shown, room, pad, { text -> g.font = best.deriveFont(20 * scale); g.fontMetrics.stringWidth(text).toFloat() }, lend = false)
            val drawn = fits.mapIndexed { index, slot -> baseline(20 * scale * slot.scale, false, fonts[index].second, ownLine = true) }
            words(g, ink, shown, listOf(20f, 20f, 20f), fonts, false, ownLine = true)
            return "baselines at " + drawn.joinToString(", ") { "%.0f".format(it) } + " px"
        }
        words(g, ink, shown, listOf(20f, 20f, 20f), fonts, false)
        return "baselines at " + lines.drop(before).joinToString(", ") { "%.0f".format(it.first) } + " px"
    }
    val orange = Color(ORANGE)
    val captions = ArrayList<String>()
    val rows = listOf(
        BarRow("Before: 20 Medium, sides 18, each box centred. Letters below the centre: ") { g, ink, _, _ ->
            val offs = words(g, ink, listOf("going", "good", "gonna"), listOf(18f, 20f, 18f),
                listOf(regular to regularBox, MEDIUM_FONT!! to (-668 to 2272), regular to regularBox), true)
            captions += said(offs)
        },
        BarRow("Now: all 20 dp, the best in $bestName. Letters off the centre: ") { g, ink, _, _ ->
            captions += said(ladder(g, ink, listOf("going", "good", "gonna")))
        },
        BarRow("Now: ") { g, ink, _, _ -> captions += said(ladder(g, ink, listOf("I'm", "the", "you"))) },
        BarRow("Now: ") { g, ink, _, _ -> captions += said(ladder(g, ink, listOf("thanks", "Thanks", "thank"))) },
        BarRow("Long words shrink in their own places, then lose what they share: ") { g, ink, _, _ ->
            captions += said(ladder(g, ink, listOf("recommendations", "recommend", "recommended")))
        },
        BarRow("A very long best word: ") { g, ink, _, _ -> captions += said(ladder(g, ink, listOf("a", "internationalization", "of"))) },
        BarRow("Before: a shrunk best word centred on its own letters, ") { g, ink, _, _ ->
            captions += baselines(g, ink, listOf("I", "antidisestablishmentarian", "is"), ownLine = true)
        },
        BarRow("Now: on the full-size words' line, ") { g, ink, _, _ -> captions += baselines(g, ink, listOf("I", "antidisestablishmentarian", "is"), false) },
        BarRow("Before: a shrunk side word centred on its own letters, ") { g, ink, _, _ ->
            captions += baselines(g, ink, listOf("counterrevolutionaries", "the", "you"), ownLine = true)
        },
        BarRow("Now: on the full-size words' line, ") { g, ink, _, _ -> captions += baselines(g, ink, listOf("counterrevolutionaries", "the", "you"), false) },
        BarRow("The clip offer, at the words' size, and its hint at 13 dp, both shrunk alike if the bar is too short") { g, ink, muted, _ ->
            captions += ""
            // As Strip's fitClipLines: the clip's line with Android's font padding, the hint's without, shrunk to fit the bar.
            val clipFont = regular.deriveFont(20 * scale)
            val hintFont = regular.deriveFont(13 * scale)
            val clipLine = g.getFontMetrics(clipFont).let { it.maxAscent + it.maxDescent }.toFloat()
            val hintLine = g.getFontMetrics(hintFont).let { it.ascent + it.descent }.toFloat()
            val fit = minOf(1f, bar / (clipLine + hintLine))
            val top = (bar - (clipLine + hintLine) * fit) / 2
            g.font = clipFont.deriveFont(20 * scale * fit)
            val icon = (20 * scale * fit * 1.1f)
            val text = "Meet at the corner of King and Bay at 6"
            val textWidth = g.fontMetrics.stringWidth(text)
            val gap = 8 * scale
            val start = width / 2f - (icon + gap + textWidth) / 2
            val clipMiddle = top + clipLine * fit / 2
            drawVector(g, File(drawables, "clipboard.xml").readText(), start, clipMiddle - icon / 2, icon, orange)
            g.color = ink
            g.drawString(text, start + icon + gap, clipMiddle + (g.fontMetrics.ascent - g.fontMetrics.descent) / 2f)
            g.font = hintFont.deriveFont(13 * scale * fit)
            g.color = muted
            val hint = "Older clips: press Sym, then A"
            g.drawString(hint, width / 2f - g.fontMetrics.stringWidth(hint) / 2, top + clipLine * fit + g.fontMetrics.ascent)
        },
        BarRow("Ctrl held: its chip at 14 dp, and what each key does at 18 dp") { g, ink, _, _ ->
            captions += ""
            g.font = regular.deriveFont(14 * scale)
            val chip = "Ctrl"
            val chipWidth = g.fontMetrics.stringWidth(chip) + 16 * scale
            val chipHeight = g.fontMetrics.height + 6 * scale
            val x = side + 4 * scale
            val y = (bar - chipHeight) / 2f
            g.color = orange
            g.fill(RoundRectangle2D.Float(x, y, chipWidth, chipHeight, 8 * scale, 8 * scale))
            g.color = Color.BLACK
            g.drawString(chip, x + 8 * scale, y + 3 * scale + g.fontMetrics.ascent)
            g.font = regular.deriveFont(18 * scale)
            g.color = ink
            val hint = "A all  C copy  X cut  V paste  Z undo  Y redo"
            val room = width - x - chipWidth - 6 * scale - side
            g.drawString(hint, x + chipWidth + 6 * scale + (room - g.fontMetrics.stringWidth(hint)) / 2, bar / 2f + (g.fontMetrics.ascent - g.fontMetrics.descent) / 2f)
        },
        BarRow("A sentence starts: Shift's arrow at the left, clear of the left word") { g, ink, _, _ ->
            captions += ""
            ladder(g, ink, listOf("The", "I", "This"))
            capitalArrow(g, side, bar, scale, small = false)
        },
        BarRow("A left word long enough to reach under it: the arrow goes small into the corner") { g, ink, _, _ ->
            captions += ""
            ladder(g, ink, listOf("Unfortunately", "I", "This"))
            capitalArrow(g, side, bar, scale, small = true)
        },
        BarRow("Forget's notice and its Undo at 18 dp, centred the same way: ") { g, ink, _, _ ->
            val size = 18 * scale
            g.font = regular.deriveFont(size)
            g.color = ink
            val line = baseline(size, false, regularBox)
            g.drawString("Forgot “utun”", side + 8 * scale, line)
            g.color = orange
            val undo = "Undo"
            g.drawString(undo, width - side - 14 * scale - g.fontMetrics.stringWidth(undo), line)
            measured += off(line, size)
            captions += said(listOf(off(line, size)))
        },
    )
    for (dark in listOf(true, false)) {
        val paper = if (dark) Color.BLACK else Color.WHITE
        val ink = if (dark) Color.WHITE else Color.BLACK
        val muted = if (dark) Color.GRAY else Color.DARK_GRAY
        val image = BufferedImage(width, rows.size * (bar + caption), BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        captions.clear()
        for ((index, row) in rows.withIndex()) {
            val top = index * (bar + caption)
            g.color = paper
            g.fillRect(0, top + caption, width, bar)
            val saved = g.transform
            g.translate(0, top + caption)
            row.draw(g, ink, muted, paper)
            // The bar's centre line, as short marks at each side, to judge the letters against.
            g.color = orange
            g.fillRect(0, bar / 2, (side / 2).toInt(), 1)
            g.fillRect(width - (side / 2).toInt(), bar / 2, (side / 2).toInt(), 1)
            g.transform = saved
            g.color = if (dark) Color(0x22, 0x22, 0x22) else Color(0xEE, 0xEE, 0xEE)
            g.fillRect(0, top, width, caption)
            g.color = Color.GRAY
            g.font = regular.deriveFont(22f)
            g.drawString(row.caption + captions[index], 24, top + caption - 14)
        }
        g.dispose()
        ImageIO.write(image, "png", File(folder, "bar-even-${bestName.lowercase()}${if (dark) "" else "-light"}.png"))
    }
    // One bar baseline (owner, 2026-11-13): every word sits on the full-size words' line, to half a pixel.
    val line = lines.first { it.second == 1f }.first
    val apart = lines.maxOf { abs(it.first - line) }
    println("Bar words in $bestName: every baseline within ${"%.2f".format(apart)} px of the full-size words' line; shrunk: ${shrunk.distinct()}")
    check(shrunk.isNotEmpty()) { "The sheet must show a shrunk word beside full-size ones" }
    check(apart <= .5f) { "Every word must sit on the full-size words' line, not $apart px off" }
    val worst = measured.maxOf { abs(it) }
    println("Bar words in $bestName: letters at most ${"%.2f".format(worst)} px off the bar's centre line")
    check(worst <= 1f) { "The bar's words must sit within 1 px of its centre line, not $worst" }
}

/** Inter Medium, for the "before" row. */
private var MEDIUM_FONT: Font? = null

/** Shift's arrow as Strip draws it for an automatic capital: at the left, the chip's size at 14 dp, or small in the corner. */
private fun capitalArrow(g: Graphics2D, side: Float, bar: Int, scale: Float, small: Boolean) {
    val height = (14 * 1.21f + 6) * scale
    val arrow = height - 8 * scale
    val width = height - 8 * scale + 10 * scale
    var left = side
    var top = (bar - height) / 2
    var size = arrow
    if (small) {
        left = side / 2 + 2 * scale
        top = 4 * scale
        size = arrow * .6f
    }
    val boxWidth = if (small) width * .6f else width
    val boxHeight = if (small) height * .6f else height
    val x = left + boxWidth / 2 - size / 2
    val y = top + boxHeight / 2 - size / 2
    val path = Path2D.Float()
    path.moveTo(x + size / 2, y)
    path.lineTo(x + size, y + size * .52f)
    path.lineTo(x + size * .73f, y + size * .52f)
    path.lineTo(x + size * .73f, y + size)
    path.lineTo(x + size * .27f, y + size)
    path.lineTo(x + size * .27f, y + size * .52f)
    path.lineTo(x, y + size * .52f)
    path.closePath()
    g.color = Color(ORANGE)
    g.stroke = BasicStroke(1.3f * scale * (if (small) .6f else 1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(path)
}
