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
 * frames of each of the six effects, the hover before and after each press, and a favourite's quiet hearts. The word, its
 * previews and every effect are the real [MenuScene], drawn on [AwtMenuCanvas] in Inter Bold; the buttons, the pills and the icons
 * are drawn as Strip draws them, with the real [MenuKeys] placing them over the calibrated keys, its sizes and the app's own vector
 * drawables. The app shows above the bar, where the effects rise. Each sheet is a PNG in the folder given, dark and light.
 *
 * It checks three of the owner's corrections in pixels too, and fails if any breaks: a hovered button's pill covers the buttons to
 * its left (hovering Unsave covers Favourite), the book is opaque once it shuts, so the word's ink can't be seen through it, and
 * every rung's word is solid in its own colour, the status, with each preview about 60% of the way to the next, with Orange accents
 * on and off. The rungs at rest and hovered are drawn with accents off too (menu-all-accent-off), the four statuses side by side
 * in both looks with accents on and off (menu-statuses), where a favourite must stand apart from Saved and Not saved, and the
 * guide's light under its word (Strip.lit) on the bar and through the word's menu.
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
    val looks = Looks(bold, medium, strings, drawables)
    val (blocked, new, saved, favourite) = MenuKeys.Step.entries
    val (up, down, back) = Triple(MenuKeys.UP, MenuKeys.DOWN, MenuKeys.BACK)
    val moved = KeyAlignment(.05f, .93f)
    val word = "whatever"
    fun stills() = listOf(
        Still("1-blocked", "Blocked: the word in the dim grey, struck through in the muted grey. I unblocks; O has nothing", word, blocked),
        Still("2-blocked-hover-i", "Blocked, hovering I: Unblock fades the strike to 30% and lightens the word 60% toward grey", word, blocked, up),
        Still("3-not-saved", "Not saved: the word solid, in the muted grey. I saves, O blocks", word, new),
        Still("4-not-saved-hover-i", "Not saved, hovering I: Save brightens the word 60% toward white", word, new, up),
        Still("5-not-saved-hover-o", "Not saved, hovering O: Block shows a grey strike and darkens the word 60% toward the dim grey", word, new, down),
        Still("6-saved", "Saved: solid white, and nothing else. I favourites, O unsaves", word, saved),
        Still("7-saved-hover-i", "Saved, hovering I: Favourite tints the word 60% toward the accent", word, saved, up),
        Still("8-saved-hover-o", "Saved, hovering O: Unsave dims the word 60% toward grey, and its pill covers Favourite", word, saved, down),
        Still("9-favourite", "Favourite: the word in the accent, and nothing else. I spent", word, favourite),
        Still("10-favourite-hover-i", "Favourite, hovering I: already a favourite, so the word stays", word, favourite, up),
        Still("11-favourite-hover-o", "Favourite, hovering O: Unfavourite fades the word 60% toward white", word, favourite, down),
        Still("12-hover-p", "Hovering P (or Backspace): Back has no pill", word, saved, back),
        Still("13-long-hover-o", "Long word, hovering O: it ends before the widest pill, so no pill covers it", "pneumonoultramicroscopicsilicovolcanoconiosis", saved, down),
        Still("14-moved-calibration", "A moved calibration (Q at 5%, P at 93%): the buttons follow the keys", word, saved, alignment = moved),
        Still("15-moved-hover-o", "Moved calibration, hovering O", word, saved, down, alignment = moved),
        Still("16-long-favourite", "A long favourite, hovering O", "pneumonoultramicroscopicsilicovolcanoconiosis", favourite, down),
        Still("17-lit-bar", "The guide's light (Strip.lit): TitanKeys alone on the bar, underlined in the accent", "TitanKeys", new, lit = true,
            bar = true),
        Still("18-lit-not-saved", "Lit, its menu open: the line stays under the word", "TitanKeys", new, lit = true),
        Still("19-lit-hover-i", "Lit, hovering I: Save's preview", "TitanKeys", new, up, lit = true),
        Still("20-lit-saved", "Lit, saved: white", "TitanKeys", saved, lit = true),
        Still("21-lit-favourite", "Lit, a favourite: the accent", "TitanKeys", favourite, lit = true),
        Still("22-lit-favourite-hover-i", "Lit, a favourite, hovering I: Already a favourite clears the word and its line", "TitanKeys", favourite, up, lit = true),
        Still("23-lit-favourite-hover-o", "Lit, a favourite, hovering O: Unfavourite clears it too", "TitanKeys", favourite, down, lit = true),
        Still("24-long-favourite-hover-i", "A long favourite, hovering I", "pneumonoultramicroscopicsilicovolcanoconiosis", favourite, up),
    ).map { it.frame(looks) }
    val states = stills()
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
    val sheets = mutableListOf("menu-all" to states, "menu-effects" to effects, "menu-press" to pressRows(looks))
    // With Orange accents off: a favourite in the darker cream, apart from the white and the grey.
    val accents = Accent.on
    Accent.on = false
    val off = stills()
    Accent.on = accents
    for (dark in listOf(true, false)) {
        val suffix = if (dark) "" else "-light"
        for ((sheet, frames) in sheets + ("menu-all-accent-off" to off)) {
            if (sheet == "menu-all-accent-off") Accent.on = false
            val drawn = frames.map { looks.draw(it, dark) }
            val image = BufferedImage(WIDTH, drawn.sumOf { it.height }, BufferedImage.TYPE_INT_RGB)
            var y = 0
            for ((index, single) in drawn.withIndex()) {
                image.createGraphics().apply { drawImage(single, 0, y, null); dispose() }
                y += single.height
                if (sheet == "menu-all" || sheet == "menu-press") ImageIO.write(single, "png", File(folder, "menu-${frames[index].name}$suffix.png"))
            }
            ImageIO.write(image, "png", File(folder, "$sheet$suffix.png"))
            Accent.on = accents
        }
    }
    thePillCoversTheButtonsToItsLeft(looks)
    theWordIsNeverCovered(looks)
    theShutBookHidesTheWord(looks, folder)
    theWordIsSolid(looks)
    theStatusesStandApart(looks, folder)
    // The best word in each weight the owner could have, to pick the one that reads as bold without looking heavy.
    barSheet(folder, regular, medium, "Medium", -668 to 2272, drawables)
    barSheet(folder, regular, bold, "Bold", -685 to 2279, drawables)
    barFits()
    // The bar on its line, at the phone's own size: every state at every Bar text size, before and after (tests/menu-render/BarLine.kt).
    BarLine.render(folder, fonts, drawables, strings)
    println("Drawn to $folder")
}

private var lastHaptic = ""

/**
 * A rung at rest, hovered on [hover] or not, as a frame: the preview has faded in. [lit] underlines the word as Strip.lit does, and
 * [bar] shows the word alone on the bar, before its menu opens.
 */
private class Still(val name: String, val caption: String, val word: String, val step: MenuKeys.Step, val hover: Int = -1,
                    val alignment: KeyAlignment = KeyAlignment.DEFAULT, val lit: Boolean = false, val bar: Boolean = false) {
    fun frame(looks: Looks): Frame {
        val scene = looks.scene(word, step, alignment)
        MenuKeys.choices(step).let { if (hover == MenuKeys.UP) it.i else if (hover == MenuKeys.DOWN) it.o else null }?.let { scene.hover(it) }
        repeat(60) { scene.step(1 / 120f) }
        val line = if (lit) floatArrayOf(scene.wordLeft * DENSITY, (scene.wordLeft + scene.wordWidth) * DENSITY, scene.baseline * DENSITY) else null
        return Frame(name, caption, scene.snapshot(), step, hover, alignment, above = 40, lit = line, barWord = if (bar) word else null)
    }
}

/**
 * What one frame shows: the scene as it was ([Snapshot]), the rung the buttons show, the hovered column, the keys' calibration, and
 * how much of the app shows above the bar, in dp.
 */
private class Frame(val name: String, val caption: String, val scene: Snapshot, val step: MenuKeys.Step, val hover: Int,
                    val alignment: KeyAlignment = KeyAlignment.DEFAULT, val above: Int = 40, val lit: FloatArray? = null,
                    val barWord: String? = null)

/** The scene's drawing as it was, in the dark look and the light one: its word, and its effects. */
private class Snapshot(val darkWord: Recording, val darkEffects: Recording, val lightWord: Recording, val lightEffects: Recording) {
    fun draw(canvas: MenuCanvas, dark: Boolean, word: Boolean) =
        (if (dark) (if (word) darkWord else darkEffects) else (if (word) lightWord else lightEffects)).replay(canvas)
}

/** The bar's light look, with Accent's colour: orange, or cream with Orange accents off. */
private val LIGHT get() = MenuScene.Palette.LIGHT
/** The bar's dark look, with Accent's colour. */
private val DARK get() = MenuScene.Palette.DARK

/** The scene as it is now, recorded in both looks, to draw later: the scene moves on. */
private fun MenuScene.snapshot(): Snapshot {
    val was = palette
    palette = DARK
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
    override fun text(text: String, x: Float, y: Float, size: Float, colour: Int, centred: Boolean) {
        calls += { it.text(text, x, y, size, colour, centred) }
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

    /**
     * The room left of the buttons, in px, as Strip has it (without the screen's corners, which this flat render lacks): its left,
     * its right, and where the word must end, before the widest pill, at [density] (Bar text size's).
     */
    fun room(alignment: KeyAlignment, density: Float = DENSITY): Triple<Float, Float, Float> {
        val left = maxOf(8 * density, MenuKeys.slot(WIDTH.toFloat(), 0, alignment).first)
        val end = MenuKeys.wordEnd(WIDTH.toFloat(), MENU_WORD_GAP * density, { labelWidth(it, density) }, MENU_ICON_DP * density,
            MENU_PILL_LEFT * density, MENU_PILL_GAP * density, MENU_PILL_RIGHT * density, alignment)
        return Triple(left, MenuKeys.wordRight(WIDTH.toFloat(), MENU_WORD_GAP * density, alignment), end)
    }

    /** A button's name's width in the pill, in px, at [density], as Strip measures it: Inter Medium at the bar words' size, times .88. */
    fun labelWidth(button: MenuKeys.Button, density: Float = DENSITY): Float {
        probe.font = medium.deriveFont(20 * density * MENU_PILL_TEXT)
        return probe.fontMetrics.stringWidth(label(button)).toFloat()
    }

    /** The word's width in px at [density]. */
    fun wordWidth(text: String, density: Float = DENSITY) = measure.measure(text) * density

    /** Where the word shows for [word], as Strip places it: its text, shortened to fit, and its left and right, in px, at [density]. */
    fun placed(word: String, alignment: KeyAlignment, density: Float = DENSITY): Triple<String, Float, Float> {
        val (left, right, end) = room(alignment, density)
        val text = MenuKeys.shorten(word) { wordWidth(it, density) <= end - left }
        val width = wordWidth(text, density)
        val centre = MenuKeys.wordCentre(left, right, end, width)
        return Triple(text, centre - width / 2, centre + width / 2)
    }

    /**
     * The scene for [word] at [step], placed and shortened as Strip.drawMenu does it: on the baseline of the bar's own height, which
     * keeps its unscaled height at every Bar text size.
     */
    fun scene(word: String, step: MenuKeys.Step, alignment: KeyAlignment): MenuScene {
        val (text, left, right) = placed(word, alignment)
        return MenuScene(text, MenuScene.WordLayout.of(text, measure.top(text)) { measure.measure(it) }, (left + right) / 2 / DENSITY,
            StripFit.baseline(LINE, MenuScene.FONT_DP * DENSITY, FLOOR) / DENSITY, step)
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
        // The guide's light, as Strip.underline draws it: the accent, or the ink with Orange accents off, as the bar's look has it.
        fun lit(left: Float, right: Float, baseline: Float) {
            g.color = if (Accent.on) Color(ORANGE) else ink
            val top = baseline + LIT_GAP_DP * DENSITY
            g.fill(java.awt.geom.Rectangle2D.Float(left, top, right - left, LIT_DP * DENSITY))
        }
        // The word alone on the bar, in its middle place, before its menu opens: bold, the bar's ink.
        frame.barWord?.let { alone ->
            g.font = bold.deriveFont(MenuScene.FONT_DP * DENSITY)
            g.color = ink
            val wide = g.fontMetrics.stringWidth(alone).toFloat()
            val baseline = StripFit.baseline(LINE, MenuScene.FONT_DP * DENSITY, FLOOR)
            g.drawString(alone, (WIDTH - wide) / 2, baseline)
            if (frame.lit != null) lit((WIDTH - wide) / 2, (WIDTH + wide) / 2, baseline)
            g.dispose()
            return image
        }
        val canvas = AwtMenuCanvas(g, bold)
        // The scene draws in dp, in the frame's colours.
        fun scene(word: Boolean) {
            val saved = g.transform
            g.scale(DENSITY.toDouble(), DENSITY.toDouble())
            frame.scene.draw(canvas, dark, word)
            g.transform = saved
        }
        scene(true)
        frame.lit?.let { (left, right, baseline) -> lit(left, right, baseline) }
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
            // As Strip: the pill's middle is on the bar's line, and it grows with Bar text size only as far as fits round the line.
            val height = minOf(MENU_PILL_DP * DENSITY, StripFit.around(LINE, ROW.toFloat(), INSIDE))
            val shape = RoundRectangle2D.Float(pill.first, LINE - height / 2, pill.second - pill.first, height, height, height)
            val spent = button == MenuKeys.Button.Favourited
            g.color = faded(ORANGE, if (spent) .12f else .24f, paper)
            g.fill(shape)
            g.color = faded(ORANGE, if (spent) .6f else 1f, paper)
            g.drawString(name, MenuKeys.pillLabel(pill.second, width, icon, MENU_PILL_GAP * DENSITY, MENU_PILL_RIGHT * DENSITY),
                StripFit.baseline(LINE, 20 * DENSITY * MENU_PILL_TEXT, FLOOR))
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
            // On the line, and Back rises as Strip lifts it, to keep its arrow clear of the screen's corner.
            val top = minOf(LINE - icon / 2, ROW - INSIDE - icon).let { top ->
                if (button != null) top else top - MenuKeys.rise(MenuKeys.BACK_ARROW, icon, middle, top) { x, y ->
                    Corners.clear(100f, x, ROW - y, WIDTH.toFloat(), StripFit.EDGE_DP * 1.55f)
                }
            }
            drawVector(g, File(drawables, "$name.xml").readText(), middle - icon / 2, top, icon, colour)
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
 * No pill and no icon ever covers the menu's word, or the guide's underline under it, which is as wide as the word (owner,
 * 2026-11-13: the guide's TitanKeys was cut to "TitanKe" under "Already a favourite" at Bar text size Bigger). For the guide's
 * TitanKeys, a short word, long real words, at every Bar text size, on the default keys and a moved calibration: on every rung,
 * hovering I, O or nothing, the word is whole in its room and ends at least the word's gap before the pill and every icon. TitanKeys
 * and the short word show whole, never shortened.
 */
private fun theWordIsNeverCovered(looks: Looks) {
    // Every problem, so a failure shows them all.
    val problems = ArrayList<String>()
    fun check(ok: Boolean, problem: () -> String) { if (!ok) problems += problem() }
    for ((size, factor) in listOf("Smaller" to FixedScale.SMALLER, "Normal" to FixedScale.NORMAL, "Bigger" to FixedScale.BIGGER)) {
        val density = 1.55f * factor
        val gap = MENU_WORD_GAP * density
        val icon = MENU_ICON_DP * density
        for (alignment in listOf(KeyAlignment.DEFAULT, KeyAlignment(.05f, .93f))) {
            for (word in listOf("TitanKeys", "whatever", "pneumonoultramicroscopicsilicovolcanoconiosis", "antidisestablishmentarianism")) {
                val (text, left, right) = looks.placed(word, alignment, density)
                val (roomLeft, _, _) = looks.room(alignment, density)
                val how = "$size, ${if (alignment == KeyAlignment.DEFAULT) "default keys" else "moved keys"}, \"$text\""
                if (word.length < 12) check(text == word) { "$how: shows whole, not shortened from $word" }
                check(left >= roomLeft - .5f) { "$how: starts in its room, at $left, not before $roomLeft" }
                for (column in MenuKeys.UP..MenuKeys.BACK) {
                    val (a, b) = MenuKeys.slot(WIDTH.toFloat(), column, alignment)
                    check(right + gap <= (a + b) / 2 - icon / 2 + .5f) { "$how: ends ${(a + b) / 2 - icon / 2 - right} px before the icon of column $column" }
                }
                for (step in MenuKeys.Step.entries) for (column in listOf(MenuKeys.UP, MenuKeys.DOWN)) {
                    val button = MenuKeys.choices(step).let { if (column == MenuKeys.UP) it.i else it.o } ?: continue
                    val (pillLeft, _) = MenuKeys.pill(WIDTH.toFloat(), column, looks.labelWidth(button, density), icon, MENU_PILL_LEFT * density,
                        MENU_PILL_GAP * density, MENU_PILL_RIGHT * density, alignment)
                    check(right + gap <= pillLeft + .5f) { "$how, $step, hovering $button: the pill starts ${pillLeft - right} px after the word ends, under its gap of $gap" }
                }
            }
        }
        val (text, left, right) = looks.placed("TitanKeys", KeyAlignment.DEFAULT, density)
        println("$size: \"$text\" from ${left.toInt()} to ${right.toInt()} px; the room ends at ${looks.room(KeyAlignment.DEFAULT, density).third.toInt()} px")
    }
    kotlin.check(problems.isEmpty()) { "A pill or an icon covers the menu's word:\n" + problems.joinToString("\n") }
    println("No pill or icon covers the menu's word, at every Bar text size, on every rung and hover")
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

/**
 * Every rung's word is solid in its own colour, the status, in the dark look and the light one, with Orange accents on and off
 * (owner, 2026-11-13: "favourited = orange. saved = white. neutral = grey. blocked = grey + striked through"). Saved is the ink,
 * Favourite its own colour, Not saved the muted grey, Blocked the dim grey; a resting finger's preview moves the word 60% of the
 * way to the next. The word is drawn without smoothing, so each letter's pixels are its one colour: away from the strike, every
 * pixel of the word must be the expected colour, to within 2 a channel, and there must be a word's worth of them.
 */
private fun theWordIsSolid(looks: Looks) {
    fun toward(from: Int, to: Int, t: Float): Int {
        fun ch(shift: Int) = Math.round(((from shr shift) and 0xFF) + (((to shr shift) and 0xFF) - ((from shr shift) and 0xFF)) * t) shl shift
        return ch(16) or ch(8) or ch(0)
    }
    fun near(a: Int, b: Int) = (0..16 step 8).all { abs(((a shr it) and 0xFF) - ((b shr it) and 0xFF)) <= 2 }
    val (blocked, new, saved, favourite) = MenuKeys.Step.entries
    val accents = Accent.on
    for ((look, palette) in listOf(true, false).flatMap { on ->
        Accent.on = on
        val with = if (on) "accents on" else "accents off"
        listOf("dark, $with" to MenuScene.Palette.DARK, "light, $with" to MenuScene.Palette.LIGHT)
    }) {
        val p = palette
        val cases = listOf(
            Triple(blocked, null, p.dim), Triple(new, null, p.grey), Triple(saved, null, p.ink), Triple(favourite, null, p.favourite),
            Triple(blocked, MenuKeys.Button.Unblock, toward(p.dim, p.grey, .6f)),
            Triple(new, MenuKeys.Button.Save, toward(p.grey, p.ink, .6f)),
            Triple(new, MenuKeys.Button.Block, toward(p.grey, p.dim, .6f)),
            Triple(saved, MenuKeys.Button.Unsave, toward(p.ink, p.grey, .6f)),
            Triple(saved, MenuKeys.Button.Favourite, toward(p.ink, p.favourite, .6f)),
            Triple(favourite, MenuKeys.Button.Unfavourite, toward(p.favourite, p.ink, .6f)),
        )
        for ((step, hover, expected) in cases) {
            val scene = looks.scene("whatever", step, KeyAlignment.DEFAULT)
            scene.palette = palette
            hover?.let { scene.hover(it) }
            repeat(60) { scene.step(1 / 120f) }
            val scale = 4f
            val left = scene.wordLeft - 6f
            val image = BufferedImage(((scene.wordWidth + 12f) * scale).toInt(), (MenuScene.BAR * scale).toInt(), BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            g.color = Color(palette.paper)
            g.fillRect(0, 0, image.width, image.height)
            g.scale(scale.toDouble(), scale.toDouble())
            g.translate(-left.toDouble(), 0.0)
            scene.paintWord(AwtMenuCanvas(g, looks.bold))
            g.dispose()
            // The strike runs 5.6 dp over the baseline, 2.1 dp thick: its rows are left out.
            val strike = (scene.baseline - 5.6f - 1.6f) * scale..(scene.baseline - 5.6f + 1.6f) * scale
            var count = 0
            val wrong = HashSet<Int>()
            for (y in 0 until image.height) {
                if (y.toFloat() in strike) continue
                for (x in 0 until image.width) {
                    val rgb = image.getRGB(x, y) and 0xFFFFFF
                    if (rgb == palette.paper) continue
                    count++
                    if (!near(rgb, expected)) wrong += rgb
                }
            }
            val how = "$look, $step${hover?.let { ", hovering $it" } ?: ""}"
            check(wrong.isEmpty()) { "$how: the word must be solid #%06X, but has %s".format(expected, wrong.take(6).joinToString { "#%06X".format(it) }) }
            check(count > scene.wordWidth * scale * scale) { "$how: a word's worth of solid letters, not $count px" }
        }
    }
    Accent.on = accents
    println("Every rung's word is solid in its own colour, each preview 60% of the way to the next, dark and light, accents on and off")
}

/** [rgb]'s CIE L*a*b*, from sRGB under D65. */
private fun lab(rgb: Int): DoubleArray {
    fun lin(c: Int): Double { val v = c / 255.0; return if (v <= .04045) v / 12.92 else Math.pow((v + .055) / 1.055, 2.4) }
    val r = lin(rgb shr 16 and 0xFF)
    val g = lin(rgb shr 8 and 0xFF)
    val b = lin(rgb and 0xFF)
    fun f(t: Double) = if (t > 216.0 / 24389) Math.cbrt(t) else (24389.0 / 27 * t + 16) / 116
    val x = f((.4124 * r + .3576 * g + .1805 * b) / .95047)
    val y = f(.2126 * r + .7152 * g + .0722 * b)
    val z = f((.0193 * r + .1192 * g + .9505 * b) / 1.08883)
    return doubleArrayOf(116 * y - 16, 500 * (x - y), 200 * (y - z))
}

/** How far apart two colours look, as CIE76's ΔE: with no hue between them, their L* gap. */
private fun deltaE(a: Int, b: Int) = lab(a).zip(lab(b)).sumOf { (x, y) -> (x - y) * (x - y) }.let(Math::sqrt)

/**
 * The four statuses side by side, the word's colour alone (owner, 2026-11-13: "favourited = orange. saved = white. neutral = grey.
 * blocked = grey + striked through"), in the dark look and the light one, with Orange accents on and off: menu-statuses.png, each
 * row's caption giving every colour's L*. In every row a favourite stands apart from both its neighbours on the ladder: from Saved's
 * ink by at least the dark look's white-to-cream gap (about 14 L*), and from Not saved's grey by at least 15, as ΔE, which is the
 * L* gap where there's no hue.
 */
private fun theStatusesStandApart(looks: Looks, folder: File) {
    val accents = Accent.on
    // The dark look's gap between Saved's white and the favourite's cream with accents off, about 14 L*.
    Accent.on = false
    val least = deltaE(0xFFFFFF, MenuScene.Palette.DARK.favourite)
    val names = listOf("Blocked", "Not saved", "Saved", "Favourite")
    val rows = ArrayList<BufferedImage>()
    for (on in listOf(true, false)) for (dark in listOf(true, false)) {
        Accent.on = on
        val p = if (dark) MenuScene.Palette.DARK else MenuScene.Palette.LIGHT
        val colours = listOf(p.dim, p.grey, p.ink, p.favourite)
        val look = "${if (dark) "Dark" else "Light"}, accents ${if (on) "on" else "off"}"
        check(deltaE(p.favourite, p.ink) >= least - .05) {
            "$look: a favourite, #%06X, stands at least %.1f from Saved's ink, not %.1f".format(p.favourite, least, deltaE(p.favourite, p.ink))
        }
        check(deltaE(p.favourite, p.grey) >= 15) {
            "$look: a favourite, #%06X, stands well apart from Not saved's grey, not %.1f".format(p.favourite, deltaE(p.favourite, p.grey))
        }
        val image = BufferedImage(WIDTH, CAPTION + ROW, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.color = if (dark) Color(0x22, 0x22, 0x22) else Color(0xDD, 0xDD, 0xDD)
        g.fillRect(0, 0, WIDTH, CAPTION)
        g.color = Color.GRAY
        g.font = looks.medium.deriveFont(16f)
        g.drawString("$look: " + names.zip(colours).joinToString(" · ") { (name, colour) -> "%s #%06X L* %.0f".format(name, colour, lab(colour)[0]) },
            16, CAPTION - 14)
        g.color = Color(p.paper)
        g.fillRect(0, CAPTION, WIDTH, ROW)
        g.translate(0, CAPTION)
        g.scale(DENSITY.toDouble(), DENSITY.toDouble())
        for ((column, step) in MenuKeys.Step.entries.withIndex()) {
            val scene = looks.scene("whatever", step, KeyAlignment.DEFAULT)
            scene.cx = WIDTH * (2 * column + 1) / 8f / DENSITY
            scene.palette = p
            scene.paintWord(AwtMenuCanvas(g, looks.bold))
        }
        g.dispose()
        rows += image
        println("$look: favourite #%06X, %.1f from Saved's ink, %.1f from Not saved's grey".format(p.favourite, deltaE(p.favourite, p.ink),
            deltaE(p.favourite, p.grey)))
    }
    Accent.on = accents
    val sheet = BufferedImage(WIDTH, rows.sumOf { it.height }, BufferedImage.TYPE_INT_RGB)
    var y = 0
    for (row in rows) { sheet.createGraphics().apply { drawImage(row, 0, y, null); dispose() }; y += row.height }
    ImageIO.write(sheet, "png", File(folder, "menu-statuses.png"))
}

/** A vector drawable's paths, filled or stroked as it says, in [colour], scaled from its 24 by 24 viewport into [size]. */
internal fun drawVector(g: Graphics2D, xml: String, x: Float, y: Float, size: Float, colour: Color) {
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
internal fun parsePath(data: String): Path2D.Float {
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

/**
 * Everything in the bar fits inside its 56 dp at this MENU_SIZE (owner, 2026-11-13: Bar text size grows what's in the bar, not
 * its height), on the bar's line, 39 dp under its top ([StripFit.LINE_DP]). Each element's height comes from Strip's own sizes,
 * grown by the size's factor, with Inter's lines as Android lays them out: a TextView with its font padding spans Inter's whole box,
 * -685 to 2279 of 2048, and one without it, or drawn text, its ascent and descent, 1984 and 494. What shows clearly keeps
 * [StripFit.EDGE_DP] over the bar's bottom, the screen's edge; the pill and the icons' boxes at least [StripFit.INSIDE_DP]. Sizes
 * in unscaled dp, as the bar's 56 is.
 */
private fun barFits() {
    val factor = DENSITY / 1.55f
    val bar = StripFit.BAR_DP
    val line = StripFit.LINE_DP
    val floor = bar - StripFit.EDGE_DP
    val padded = (2279 + 685) / 2048f
    val tight = (1984 + 494) / 2048f
    val fits = ArrayList<String>()
    fun fits(what: String, top: Float, bottom: Float, lowest: Float = floor) {
        check(top >= -.01f && bottom <= lowest + .01f) {
            "At ${"%.1f".format(factor)}, $what reaches %.1f to %.1f dp in a %.0f dp bar, past %.1f".format(top, bottom, bar, lowest)
        }
        fits += "$what %.1f to %.1f".format(top, bottom)
    }
    /** One line of text at [size] dp, its small letters on the line: from its ascent to its descent. */
    fun text(what: String, size: Float): Float {
        val baseline = StripFit.baseline(line, size, floor)
        fits(what, baseline - size * 1984 / 2048f, baseline + size * 494 / 2048f)
        return baseline
    }
    // The three words, 20 sp, and the menu's word, MenuScene.FONT_DP, their small letters on the line.
    text("the words", 20f * factor)
    text("the menu's word", MenuScene.FONT_DP * factor)
    // The pill: its middle on the line, as tall as fits round it (Strip: StripFit.around), its icon and label inside it.
    val pill = minOf(MENU_PILL_DP * factor, StripFit.around(line, bar, StripFit.INSIDE_DP))
    fits("the menu's pill", line - pill / 2, line + pill / 2, bar - StripFit.INSIDE_DP)
    val icon = MENU_ICON_DP * factor
    check(icon <= pill) { "At $factor the menu's icons, $icon dp, are taller than its pill, $pill dp" }
    val iconTop = minOf(line - icon / 2, bar - StripFit.INSIDE_DP - icon)
    fits("the menu's icons", iconTop, iconTop + icon, bar - StripFit.INSIDE_DP)
    val label = 20f * MENU_PILL_TEXT * factor
    val labelLine = text("the pill's label", label)
    // Its capitals' tops (1490 of 2048) and its descent stay inside the pill, which clips it.
    check(labelLine - label * 1490 / 2048f >= line - pill / 2 && labelLine + label * 494 / 2048f <= line + pill / 2) {
        "At $factor the pill's label leaves its pill"
    }
    // The Shift, Alt and Ctrl chips: 14 dp, no font padding, 3 dp above and below, their text on the line, rising only if their
    // box would reach under the floor. Automatic capital's arrow is as tall, in the same place.
    val chip = 14f * factor
    val chipHeight = chip * tight + 2 * 3 * factor
    val chipTop = StripFit.boxTop(line, 3 * factor + chip * 1984 / 2048f - chip * StripFit.X_HEIGHT / 2, chipHeight, floor)
    fits("the chips", chipTop, chipTop + chipHeight)
    // A notice's label, 18 sp, without font padding (centreLetters), and the modifiers' hint, at most 18 sp, drawn on the line.
    text("a notice", 18f * factor)
    text("the hint", 18f * factor)
    // The clip offer and its hint stack over the floor, and both shrink alike to fit when they'd be taller (Strip.fitClipLines).
    val clip = 20f * factor * padded
    val clipHint = 13f * factor * tight
    val shrink = minOf(1f, floor / (clip + clipHint))
    fits("the clip and its hint", (floor - (clip + clipHint) * shrink) / 2, (floor + (clip + clipHint) * shrink) / 2)
    println("Bar text size ${"%.1f".format(factor)}: everything fits the 56 dp bar on its line, in dp: ${fits.joinToString(", ")}")
}

/** As on the owner's screenshots: 1076 px across at about 1.55 px to a dp. The bar is 56 dp. */
/**
 * The owner's screenshots' density, times Bar text size's factor: MENU_SIZE smaller, normal or bigger, and Normal, which everyone
 * starts at, when none is given.
 */
private val DENSITY = 1.55f * when (System.getenv("MENU_SIZE")) {
    "smaller" -> FixedScale.SMALLER
    "bigger" -> FixedScale.BIGGER
    else -> FixedScale.NORMAL
}
private const val WIDTH = 1076
/** The bar: 56 dp at Normal, the same height at every Bar text size (Strip.BAR_DP). */
private val ROW = (56 * 1.55f).toInt()
/** The bar's line at this density: its small letters' middle, 39 dp under its top (StripFit.LINE_DP). */
private val LINE = StripFit.LINE_DP * 1.55f
/** How low what shows may reach, and how far the pill and the icons keep inside the bar, as Strip has them. */
private val FLOOR = ROW - StripFit.EDGE_DP * 1.55f
private val INSIDE = StripFit.INSIDE_DP * 1.55f
private const val CAPTION = 44
/** The accent, from Accent: orange, or cream with Orange accents off (ACCENT=off). */
private val ORANGE get() = Accent.colour and 0xFFFFFF
/** Strip's word menu sizes, in dp: its icons, its pill and the gap left of I's button. */
private const val MENU_ICON_DP = 22
private const val MENU_PILL_DP = 42
private const val MENU_PILL_TEXT = .88f
private const val MENU_PILL_LEFT = 18
private const val MENU_PILL_RIGHT = 11
private const val MENU_PILL_GAP = 12
private const val MENU_WORD_GAP = 8
/** Strip's light under a word for the guide (Strip.lit), in dp: its thickness, and its gap under the baseline. */
private const val LIT_DP = 2f
private const val LIT_GAP_DP = 3f

/**
 * The bar's own texts, at TitanKeys' fixed scale: the three words, all 20 dp with the best one in the middle in [best]'s weight
 * (owner, 2026-11-13: "I want it consistent but bolded is a nice touch"), long words fitted by the real [StripFit], the clip
 * offer and its hint, the modifier bar's chip and hint, the automatic capital's arrow, the Forget notice, and the panel's labels.
 * Each word is placed where Android puts it in Strip's 56 dp view, and the middle of its small letters is measured against the
 * bar's line, 39 dp under its top (StripFit.LINE_DP), which the short marks at each side show: it must be within half a pixel.
 * Sizes in dp, at 1.5625 px to a dp.
 */
private fun barSheet(folder: File, regular: Font, best: Font, bestName: String, bestBox: Pair<Int, Int>, drawables: File) {
    // The text grows with Bar text size (MENU_SIZE), and the bar keeps its unscaled height (Strip.BAR_DP).
    val scale = 1.5625f * DENSITY / 1.55f
    val width = 1076
    val bar = Math.round(56 * 1.5625f)
    /** The bar's line: its small letters' middle, 39 dp under its top (StripFit.LINE_DP). */
    val line = StripFit.LINE_DP * 1.5625f
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
        if (!fontPadding && !ownLine) return (StripFit.top(line, lineSize, floor(ascent).toInt(), bar - StripFit.EDGE_DP * 1.5625f) -
            floor(ascent).toInt()).toFloat()
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
    /** How far the middle of the small letters sits below the bar's line, in px. */
    fun off(baseline: Float, size: Float) = baseline - size * StripFit.X_HEIGHT / 2 - line
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
            g.drawString(hint, x + chipWidth + 6 * scale + (room - g.fontMetrics.stringWidth(hint)) / 2, StripFit.baseline(line, 18 * scale))
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
            // The bar's line, as short marks at each side, to judge the letters against.
            g.color = orange
            g.fillRect(0, line.toInt(), (side / 2).toInt(), 1)
            g.fillRect(width - (side / 2).toInt(), line.toInt(), (side / 2).toInt(), 1)
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
    val full = lines.first { it.second == 1f }.first
    val apart = lines.maxOf { abs(it.first - full) }
    println("Bar words in $bestName: every baseline within ${"%.2f".format(apart)} px of the full-size words' line; shrunk: ${shrunk.distinct()}")
    check(shrunk.isNotEmpty()) { "The sheet must show a shrunk word beside full-size ones" }
    check(apart <= .5f) { "Every word must sit on the full-size words' line, not $apart px off" }
    val worst = measured.maxOf { abs(it) }
    println("Bar words in $bestName: letters at most ${"%.2f".format(worst)} px off the bar's line, 39 dp under its top")
    check(worst <= .5f) { "The bar's words must sit within half a pixel of its line, 39 dp under its top, not $worst" }
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
