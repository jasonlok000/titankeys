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
 * Draws the word menu of the approved ladder v4 (owner, 2026-11-13) to look at without the phone: every rung at rest and hovered,
 * frames of each of the six effects, the hover before and after each press, and a favourite's quiet hearts. The word, its grey
 * previews and every effect are the real [MenuScene], drawn on [AwtMenuCanvas] in Inter Bold; the buttons, the pills and the icons
 * are drawn as Strip draws them, with the real [MenuKeys] placing them over the calibrated keys, its sizes and the app's own vector
 * drawables. The app shows above the bar, where the effects rise. Each sheet is a PNG in the folder given, dark and light.
 *
 * It checks two of the owner's corrections in pixels too, and fails if either breaks: a hovered button's pill covers the buttons to
 * its left (hovering Unsave covers Favourite), and the book is opaque once it shuts, so the word's ink can't be seen through it.
 */
fun main(args: Array<String>) {
    val folder = File(args.getOrElse(0) { "menu-render" }).apply { mkdirs() }
    val drawables = File(args.getOrElse(1) { "android/src/main/res/drawable" })
    val strings = File(args.getOrElse(2) { "android/src/main/res/values/strings.xml" }).readText()
    val fonts = File(args.getOrElse(3) { "android/src/main/res/font" })
    fun font(name: String) = runCatching { Font.createFont(Font.TRUETYPE_FONT, File(fonts, name)) }.getOrElse { Font(Font.SANS_SERIF, Font.PLAIN, 1) }
    val regular = font("inter_regular.otf")
    val medium = font("inter_medium.otf")
    val bold = font("inter_bold.otf")
    MEDIUM_FONT = medium
    val looks = Looks(bold, medium, strings, drawables)
    val (blocked, new, saved, favourite) = MenuKeys.Step.entries
    val (up, down, back) = Triple(MenuKeys.UP, MenuKeys.DOWN, MenuKeys.BACK)
    val moved = KeyAlignment(.05f, .93f)
    val word = "whatever"
    val states = listOf(
        Still("1-blocked", "Blocked: the outline in grey, struck through. I unblocks; O has nothing", word, blocked),
        Still("2-blocked-hover-i", "Blocked, hovering I: Unblock fades the strike", word, blocked, up),
        Still("3-not-saved", "Not saved: a plain outline, no fill, no dots. I saves, O blocks", word, new),
        Still("4-not-saved-hover-i", "Not saved, hovering I: Save shows the word solid, in grey", word, new, up),
        Still("5-not-saved-hover-o", "Not saved, hovering O: Block shows a grey strike", word, new, down),
        Still("6-saved", "Saved: solid, with a faint book above the bar. I favourites, O unsaves", word, saved),
        Still("7-saved-hover-i", "Saved, hovering I: a grey heart", word, saved, up),
        Still("8-saved-hover-o", "Saved, hovering O: Unsave shows the outline, and its pill covers Favourite", word, saved, down),
        Still("9-favourite", "Favourite: solid, the filled heart orange, I spent", word, favourite),
        Still("10-favourite-hover-i", "Favourite, hovering I: already a favourite", word, favourite, up),
        Still("11-favourite-hover-o", "Favourite, hovering O: Unfavourite shows the heart cracked", word, favourite, down),
        Still("12-hover-p", "Hovering P (or Backspace): Back has no pill", word, saved, back),
        Still("13-long-hover-o", "Long word, hovering O: the pill slides over its fading end", "pneumonoultramicroscopicsilicovolcanoconiosis", saved, down),
        Still("14-moved-calibration", "A moved calibration (Q at 5%, P at 93%): the buttons follow the keys", word, saved, alignment = moved),
        Still("15-moved-hover-o", "Moved calibration, hovering O", word, saved, down, alignment = moved),
    ).map { it.frame(looks) }
    // Each effect: the resting finger's preview, then the press and its frames, with the haptic's frame marked in the caption.
    val moves = listOf(
        Triple(MenuKeys.Button.Save, new, up), Triple(MenuKeys.Button.Favourite, saved, up), Triple(MenuKeys.Button.Unfavourite, favourite, down),
        Triple(MenuKeys.Button.Unsave, saved, down), Triple(MenuKeys.Button.Block, new, down), Triple(MenuKeys.Button.Unblock, blocked, up),
    )
    val times = listOf(0, 80, 160, 260, 380, 520, 700, 950, 1300)
    val effects = moves.flatMap { (button, from, column) ->
        val scene = looks.scene(word, from, KeyAlignment.DEFAULT)
        var hapticAt = -1
        var pressedAt = 0f
        scene.onHaptic = { _, label -> hapticAt = ((scene.time - pressedAt) * 1000).toInt(); lastHaptic = label }
        scene.hover(button)
        repeat(48) { scene.step(1 / 120f) }
        val frames = mutableListOf(Frame("${button.name.lowercase()}-hover", "${button.name}: the resting finger's preview", scene.snapshot(), from, column,
            above = MenuScene.ABOVE.toInt()))
        pressedAt = scene.time
        scene.press(button)
        var now = 0
        for (t in times) {
            while (now < t) { scene.step(1 / 120f); now += 1000 / 120 }
            val note = if (hapticAt in 0..t && hapticAt > t - 120) " · haptic at $hapticAt ms: $lastHaptic" else ""
            frames += Frame("${button.name.lowercase()}-$t", "${button.name} +$t ms$note", scene.snapshot(), MenuScene.next(button)!!, column,
                above = MenuScene.ABOVE.toInt())
        }
        frames
    }
    // A favourite at rest: now and then a faint heart rises.
    val ambient = run {
        val scene = looks.scene(word, favourite, KeyAlignment.DEFAULT)
        var now = 0
        listOf(0, 1000, 1400, 1900, 2500, 3200).map { t ->
            while (now < t) { scene.step(1 / 120f); now += 1000 / 120 }
            Frame("ambient-$t", "Favourite at rest, $t ms: a faint heart now and then", scene.snapshot(), favourite, -1, above = 80)
        }
    }
    val sheets = listOf("menu-all" to states, "menu-effects" to effects, "menu-ambient" to ambient, "menu-press" to pressRows(looks))
    for (dark in listOf(true, false)) {
        val suffix = if (dark) "" else "-light"
        for ((sheet, frames) in sheets) {
            val drawn = frames.map { looks.draw(it, dark) }
            val image = BufferedImage(WIDTH, drawn.sumOf { it.height }, BufferedImage.TYPE_INT_RGB)
            var y = 0
            for ((index, single) in drawn.withIndex()) {
                image.createGraphics().apply { drawImage(single, 0, y, null); dispose() }
                y += single.height
                if (sheet == "menu-all" || sheet == "menu-press") ImageIO.write(single, "png", File(folder, "menu-${frames[index].name}$suffix.png"))
            }
            ImageIO.write(image, "png", File(folder, "$sheet$suffix.png"))
        }
    }
    thePillCoversTheButtonsToItsLeft(looks)
    theShutBookHidesTheWord(looks, folder)
    // The best word in each weight the owner could have, to pick the one that reads as bold without looking heavy.
    barSheet(folder, regular, medium, "Medium", -668 to 2272, drawables)
    barSheet(folder, regular, bold, "Bold", -685 to 2279, drawables)
    println("Drawn to $folder")
}

private var lastHaptic = ""

/** A rung at rest, hovered on [hover] or not, as a frame: the preview has faded in. */
private class Still(val name: String, val caption: String, val word: String, val step: MenuKeys.Step, val hover: Int = -1,
                    val alignment: KeyAlignment = KeyAlignment.DEFAULT) {
    fun frame(looks: Looks): Frame {
        val scene = looks.scene(word, step, alignment)
        MenuKeys.choices(step).let { if (hover == MenuKeys.UP) it.i else if (hover == MenuKeys.DOWN) it.o else null }?.let { scene.hover(it) }
        repeat(60) { scene.step(1 / 120f) }
        return Frame(name, caption, scene.snapshot(), step, hover, alignment, above = 40)
    }
}

/**
 * What one frame shows: the scene as it was ([Snapshot]), the rung the buttons show, the hovered column, the keys' calibration, and
 * how much of the app shows above the bar, in dp.
 */
private class Frame(val name: String, val caption: String, val scene: Snapshot, val step: MenuKeys.Step, val hover: Int,
                    val alignment: KeyAlignment = KeyAlignment.DEFAULT, val above: Int = 40)

/** The scene's drawing as it was, in the dark look and the light one: its word, and its effects. */
private class Snapshot(val darkWord: Recording, val darkEffects: Recording, val lightWord: Recording, val lightEffects: Recording) {
    fun draw(canvas: MenuCanvas, dark: Boolean, word: Boolean) =
        (if (dark) (if (word) darkWord else darkEffects) else (if (word) lightWord else lightEffects)).replay(canvas)
}

/** The bar's light look, as the scene's colours. */
private val LIGHT = MenuScene.Palette(0x000000, 0x444444, 0xEF804E, 0xFFFFFF, 0x000000)

/** The scene as it is now, recorded in both looks, to draw later: the scene moves on. */
private fun MenuScene.snapshot(): Snapshot {
    val was = palette
    palette = MenuScene.Palette.DARK
    val darkWord = Recording().also { paintWord(it) }
    val darkEffects = Recording().also { paintEffects(it) }
    palette = LIGHT
    val lightWord = Recording().also { paintWord(it) }
    val lightEffects = Recording().also { paintEffects(it) }
    palette = was
    return Snapshot(darkWord, darkEffects, lightWord, lightEffects)
}

/** A [MenuCanvas] that keeps every call, to play them again on another canvas. */
private class Recording : MenuCanvas {
    private val calls = ArrayList<(MenuCanvas) -> Unit>()
    fun replay(canvas: MenuCanvas) = calls.forEach { it(canvas) }
    override fun save() { calls += { it.save() } }
    override fun restore() { calls += { it.restore() } }
    override fun translate(x: Float, y: Float) { calls += { it.translate(x, y) } }
    override fun rotate(radians: Float) { calls += { it.rotate(radians) } }
    override fun scale(x: Float, y: Float) { calls += { it.scale(x, y) } }
    override fun clip(path: MenuPath) { calls += { it.clip(path) } }
    override fun fill(path: MenuPath, colour: Int) { calls += { it.fill(path, colour) } }
    override fun stroke(path: MenuPath, width: Float, colour: Int) { calls += { it.stroke(path, width, colour) } }
    override fun circle(x: Float, y: Float, radius: Float, colour: Int) { calls += { it.circle(x, y, radius, colour) } }
    override fun rect(left: Float, top: Float, right: Float, bottom: Float, colour: Int) { calls += { it.rect(left, top, right, bottom, colour) } }
    override fun text(text: String, x: Float, y: Float, size: Float, fill: Int, outline: Int, outlineWidth: Float, centred: Boolean) {
        calls += { it.text(text, x, y, size, fill, outline, outlineWidth, centred) }
    }
}

/**
 * The hover before and after each press, up the ladder on I and down it on O, as the real [SteadyKey] keeps it through the
 * touchpad's events in the order the owner's phone sends them (tests/word-menu, [MenuBar]): the key down, up 86 ms later, the
 * touchpad's lift for the press 124 ms after that, and then nothing while the finger stays still. Each frame's pill is the one the
 * strip draws from the mark that the events leave, over the scene as the press left it.
 */
private fun pressRows(looks: Looks): List<Frame> {
    val rows = mutableListOf<Frame>()
    var count = 0
    val word = "whatever"
    val bar = MenuBar(MenuKeys.Step.New)
    val scene = looks.scene(word, MenuKeys.Step.New, KeyAlignment.DEFAULT)
    var sceneAt = 0L
    fun until(time: Long) {
        while (sceneAt < time) { scene.step(1 / 120f); sceneAt += 1000L / 120 }
        bar.at(time)
    }
    fun frame(caption: String) {
        count++
        rows += Frame("press-%02d".format(count), caption, scene.snapshot(), bar.state, if (bar.pill != null) bar.mark else -1, above = 40)
    }
    fun rest(key: String, time: Long) {
        until(time)
        bar.touch(key, time)
        bar.pill?.second?.let { scene.hover(it) }
        until(time + 400)
        frame("Resting on $key: ${bar.state}")
    }
    fun press(key: String, time: Long) {
        until(time)
        val before = bar.state
        bar.keyDown(key, time)
        bar.keyUp(key, time + 86)
        bar.picked.lastOrNull()?.let { picked -> if (bar.state != before) scene.press(MenuKeys.Button.valueOf(picked)) }
        until(time + 1)
        frame("$key pressed, 1 ms: ${bar.state}")
        bar.lift(time + 210)
        until(time + 400)
        frame("$key, 400 ms: the touchpad lifted at 210 ms (before the fix, the pill went at 360)")
        until(time + 1_500)
        frame("$key, 1.5 s: the finger still on $key, the touchpad quiet")
    }
    rest("I", 0)
    press("I", 1_000)
    press("I", 3_000)
    rest("O", 5_000)
    press("O", 6_000)
    press("O", 8_000)
    press("O", 10_000)
    return rows
}

/** Everything a frame is drawn with: the fonts, the bar's texts and icons, and the scene made for a word as Strip makes it. */
private class Looks(val bold: Font, val medium: Font, val strings: String, val drawables: File) {
    private val probe = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    }
    private val measure = AwtMenuCanvas(probe, bold)

    /** The room left of the buttons, in px, as Strip has it (without the screen's corners, which this flat render lacks). */
    fun room(alignment: KeyAlignment): Pair<Float, Float> {
        val left = maxOf(8 * DENSITY, MenuKeys.slot(WIDTH.toFloat(), 0, alignment).first)
        return left to MenuKeys.wordRight(WIDTH.toFloat(), MENU_WORD_GAP * DENSITY, alignment)
    }

    /** The scene for [word] at [step], placed and shortened as Strip.drawMenu does it. */
    fun scene(word: String, step: MenuKeys.Step, alignment: KeyAlignment): MenuScene {
        val (left, right) = room(alignment)
        val text = MenuKeys.shorten(word) { measure.measure(it) * DENSITY <= right - left }
        return MenuScene(text, MenuScene.WordLayout.of(text) { measure.measure(it) }, MenuKeys.wordCentre(left, right) / DENSITY,
            StripFit.baseline(MenuScene.CY, MenuScene.FONT_DP), step)
    }

    fun label(button: MenuKeys.Button) = string(strings, "strip_" + button.name.lowercase())

    fun draw(frame: Frame, dark: Boolean): BufferedImage {
        val top = CAPTION + Math.round(frame.above * DENSITY)
        val image = BufferedImage(WIDTH, top + ROW, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        val paper = if (dark) Color.BLACK else Color.WHITE
        val ink = if (dark) Color.WHITE else Color.BLACK
        val muted = if (dark) Color.GRAY else Color.DARK_GRAY
        val app = if (dark) Color(0x16, 0x12, 0x10) else Color(0xEE, 0xEA, 0xE6)
        g.color = if (dark) Color(0x22, 0x22, 0x22) else Color(0xDD, 0xDD, 0xDD)
        g.fillRect(0, 0, WIDTH, CAPTION)
        g.color = Color.GRAY
        g.font = medium.deriveFont(20f)
        g.drawString(frame.caption, 24, CAPTION - 14)
        g.color = app
        g.fillRect(0, CAPTION, WIDTH, top - CAPTION)
        g.color = paper
        g.fillRect(0, top, WIDTH, ROW)
        g.translate(0, top)
        val canvas = AwtMenuCanvas(g, bold)
        // The scene draws in dp, in the frame's colours.
        fun scene(word: Boolean) {
            val saved = g.transform
            g.scale(DENSITY.toDouble(), DENSITY.toDouble())
            frame.scene.draw(canvas, dark, word)
            g.transform = saved
        }
        scene(true)
        // The pill, as Strip draws it: it grows left from its key's column and covers what it slides over.
        val icon = MENU_ICON_DP * DENSITY
        val step = frame.step
        val choices = MenuKeys.choices(step)
        val slots = listOf(choices.i, choices.o, null)
        val hovered = frame.hover.takeIf { MenuKeys.pills(it, step) } ?: -1
        var pill: Pair<Float, Float>? = null
        if (hovered >= 0) {
            val button = slots[hovered - MenuKeys.UP]!!
            val name = label(button)
            g.font = medium.deriveFont(20 * DENSITY * MENU_PILL_TEXT)
            val width = g.fontMetrics.stringWidth(name).toFloat()
            pill = MenuKeys.pill(WIDTH.toFloat(), hovered, width, icon, MENU_PILL_LEFT * DENSITY, MENU_PILL_GAP * DENSITY, MENU_PILL_RIGHT * DENSITY, frame.alignment)
            val height = MENU_PILL_DP * DENSITY
            val shape = RoundRectangle2D.Float(pill.first, (ROW - height) / 2, pill.second - pill.first, height, height, height)
            val fade = MENU_PILL_FADE * DENSITY
            g.paint = java.awt.GradientPaint(pill.first - fade, 0f, Color(paper.red, paper.green, paper.blue, 0), pill.first + height / 2, 0f, paper)
            g.fill(java.awt.geom.Rectangle2D.Float(pill.first - fade, 0f, fade + height / 2, ROW.toFloat()))
            g.color = paper
            g.fill(shape)
            val spent = button == MenuKeys.Button.Favourited
            g.color = faded(ORANGE, if (spent) .12f else .24f, paper)
            g.fill(shape)
            g.color = faded(ORANGE, if (spent) .6f else 1f, paper)
            g.drawString(name, MenuKeys.pillLabel(pill.second, width, icon, MENU_PILL_GAP * DENSITY, MENU_PILL_RIGHT * DENSITY),
                StripFit.baseline(ROW / 2f, 20 * DENSITY * MENU_PILL_TEXT))
        }
        // The icons over their keys: white at rest, the filled heart orange, orange in a pill, grey beside one. A pill covers the
        // buttons to its left. Back is panel_back.
        for ((index, button) in slots.withIndex()) {
            val column = MenuKeys.UP + index
            val (l, r) = MenuKeys.slot(WIDTH.toFloat(), column, frame.alignment)
            val middle = (l + r) / 2
            val inPill = column == hovered
            if (pill != null && !inPill && middle in pill.first..pill.second) continue
            if (button == null && index < 2) continue
            val name = button?.let(::drawable) ?: "panel_back"
            val colour = when {
                inPill || button == MenuKeys.Button.Favourited -> Color(ORANGE)
                pill != null -> muted
                else -> ink
            }
            drawVector(g, File(drawables, "$name.xml").readText(), middle - icon / 2, (ROW - icon) / 2, icon, colour)
        }
        scene(false)
        g.dispose()
        return image
    }
}

private fun drawable(button: MenuKeys.Button) = "menu_" + button.name.lowercase()

private fun string(strings: String, name: String) =
    Regex("<string name=\"$name\">([^<]*)</string>").find(strings)!!.groupValues[1].replace("\\'", "'")

private fun faded(colour: Int, alpha: Float, paper: Color): Color {
    val c = Color(colour)
    fun mix(a: Int, b: Int) = (a * alpha + b * (1 - alpha)).toInt().coerceIn(0, 255)
    return Color(mix(c.red, paper.red), mix(c.green, paper.green), mix(c.blue, paper.blue))
}

/**
 * Hovering O's button grows its pill over the buttons to its left (owner, 2026-11-13: hovering Unsave covers Favourite), for every
 * name O's button has, at the bar's real sizes, on the default keys and a moved calibration.
 */
private fun thePillCoversTheButtonsToItsLeft(looks: Looks) {
    val g = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics()
    g.font = looks.medium.deriveFont(20 * DENSITY * MENU_PILL_TEXT)
    for (alignment in listOf(KeyAlignment.DEFAULT, KeyAlignment(.05f, .93f))) for (step in MenuKeys.Step.entries) {
        val o = MenuKeys.choices(step).o ?: continue
        val width = g.fontMetrics.stringWidth(looks.label(o)).toFloat()
        val (left, right) = MenuKeys.pill(WIDTH.toFloat(), MenuKeys.DOWN, width, MENU_ICON_DP * DENSITY, MENU_PILL_LEFT * DENSITY,
            MENU_PILL_GAP * DENSITY, MENU_PILL_RIGHT * DENSITY, alignment)
        check(MenuKeys.covers(left, right, MenuKeys.UP, WIDTH.toFloat(), alignment)) { "Hovering $o covers I's button: ${left.toInt()} to ${right.toInt()}" }
        check(!MenuKeys.covers(left, right, MenuKeys.BACK, WIDTH.toFloat(), alignment)) { "Hovering $o leaves Back be" }
    }
    println("Hovering O's button covers I's, for every name it has")
}

/**
 * Save's book, once shut, is opaque (owner, 2026-11-13: "the words could be seen through it"): over a light app, the middle of the
 * shut book is the bar's own black under the orange, not the app showing through. Drawn to menu-book-shut.png too.
 */
private fun theShutBookHidesTheWord(looks: Looks, folder: File) {
    val scene = looks.scene("whatever", MenuKeys.Step.New, KeyAlignment.DEFAULT)
    scene.press(MenuKeys.Button.Save)
    var steps = 0
    while (scene.bookShut() < .99f && steps < 240) { scene.step(1 / 120f); steps++ }
    check(scene.bookShut() >= .99f) { "Save's book shuts" }
    val box = scene.bookBox()!!
    val scale = 3f
    val image = BufferedImage((360 * scale).toInt(), ((MenuScene.ABOVE + MenuScene.BAR) * scale).toInt(), BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(0xF4, 0xF4, 0xF4)
    g.fillRect(0, 0, image.width, image.height)
    g.scale(scale.toDouble(), scale.toDouble())
    g.translate((180 - scene.cx).toDouble(), MenuScene.ABOVE.toDouble())
    g.color = Color.BLACK
    g.fillRect(-1000, 0, 4000, MenuScene.BAR.toInt())
    val canvas = AwtMenuCanvas(g, looks.bold)
    scene.paintWord(canvas)
    scene.paintEffects(canvas)
    g.dispose()
    ImageIO.write(image, "png", File(folder, "menu-book-shut.png"))
    // The middle of the shut book's page, a little right of the spine.
    val x = ((180 - scene.cx + scene.cx + (box[2] - box[0]) * .2f) * scale).toInt()
    val y = (((box[1] + box[3]) / 2 + MenuScene.ABOVE) * scale).toInt()
    val pixel = Color(image.getRGB(x, y))
    check(pixel.red + pixel.green + pixel.blue < 200) { "The shut book is opaque: its page is $pixel over a light app" }
    println("The shut book is opaque: its page is ${pixel.red}, ${pixel.green}, ${pixel.blue} over a light app")
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
/** Strip's word menu sizes, in dp: its icons, its pill and the gap left of I's button. */
private const val MENU_ICON_DP = 22
private const val MENU_PILL_DP = 42
private const val MENU_PILL_TEXT = .88f
private const val MENU_PILL_LEFT = 18
private const val MENU_PILL_RIGHT = 11
private const val MENU_PILL_GAP = 12
private const val MENU_PILL_FADE = 20
private const val MENU_WORD_GAP = 8

/**
 * The bar's own texts, at TitanKeys' fixed scale: the three words, all 20 dp with the best one in the middle in [best]'s weight
 * (owner, 2026-11-13: "I want it consistent but bolded is a nice touch"), long words fitted by the real [StripFit], the clip
 * offer and its hint, the modifier bar's chip and hint, the automatic capital's arrow, the Forget notice, and the panel's labels.
 * Each word is placed where Android puts it in Strip's 56 dp view, and the middle of its small letters is measured against the
 * bar's centre line, which the short marks at each side show: it must be within 1 px. Sizes in dp, at 1.5625 px to a dp.
 */
private fun barSheet(folder: File, regular: Font, best: Font, bestName: String, bestBox: Pair<Int, Int>, drawables: File) {
    val scale = 1.5625f
    val width = 1076
    val bar = Math.round(56 * scale)
    val caption = 44
    val side = 8 * scale
    val pad = (6 + 2) * scale
    val regularBox = -660 to 2269
    data class BarRow(val caption: String, val draw: (Graphics2D, Color, Color, Color) -> Unit)
    val measured = ArrayList<Float>()
    /**
     * Where Android puts the baseline of one line of Inter at [size] px, gravity centred in a view [height] px tall: with
     * [fontPadding], centring the font's whole box from its lowest to its highest point ([box], in its 2048 units), as the words
     * were; without, centring ascent to descent with Strip's [StripFit.lift] under it, as they are now.
     */
    fun baseline(size: Float, fontPadding: Boolean, box: Pair<Int, Int>): Float {
        val ascent = -size * 1984 / 2048f
        val descent = size * 494 / 2048f
        val top = if (fontPadding) -size * box.second / 2048f else ascent
        val bottom = if (fontPadding) -size * box.first / 2048f else descent
        val lift = if (fontPadding) 0 else Math.round(StripFit.lift(ascent, descent, size))
        val textTop = floor(top).toInt()
        val textHeight = ceil(bottom).toInt() - textTop
        return ((bar - lift - textHeight) shr 1) - textTop.toFloat()
    }
    /** How far the middle of the small letters sits below the bar's centre line, in px. */
    fun off(baseline: Float, size: Float) = baseline - size * StripFit.X_HEIGHT / 2 - bar / 2f
    fun words(g: Graphics2D, ink: Color, shown: List<String>, sizes: List<Float>, fonts: List<Pair<Font, Pair<Int, Int>>>,
              fontPadding: Boolean): List<Float> {
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
            val line = baseline(size, fontPadding, fonts[index].second)
            if (slot.text.isNotEmpty()) offs += off(line, size)
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
        BarRow("The clip offer, at the words' size, and its hint at 13 dp") { g, ink, muted, _ ->
            captions += ""
            val icon = (20 * scale * 1.1f)
            g.font = regular.deriveFont(20 * scale)
            val text = "Meet at the corner of King and Bay at 6"
            val textWidth = g.fontMetrics.stringWidth(text)
            val gap = 8 * scale
            val start = width / 2f - (icon + gap + textWidth) / 2
            val top = bar / 2f - 10 * scale
            drawVector(g, File(drawables, "clipboard.xml").readText(), start, top - icon / 2 + 6 * scale, icon, orange)
            g.color = ink
            g.drawString(text, start + icon + gap, top + (g.fontMetrics.ascent - g.fontMetrics.descent) / 2f + 6 * scale)
            g.font = regular.deriveFont(13 * scale)
            g.color = muted
            val hint = "Older clips: press Sym, then A"
            g.drawString(hint, width / 2f - g.fontMetrics.stringWidth(hint) / 2, top + 6 * scale + 22 * scale)
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
