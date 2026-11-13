package com.atlas.keyboard

import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws every slide's decoration with the real GuideDecor on a 1076×1200 frame (the Titan 2 Elite's), behind a stand-in of the
 * slide's content, and checks §7's rules on it:
 * - nothing is drawn inside a keep-out rect or within 8 dp of one, or outside the area;
 * - at most 12 shapes at once, and no layer stronger than its level (orange 6–12%, cream 5%, ink 8% on the pass screen);
 * - the same moment draws the same picture, every loop comes round exactly, and the picture moves;
 * - a frame takes GuideDecor under 2 ms on this computer.
 * Usage: GuideDecorRenderKt <out folder> <font folder>. It writes sheet.png (as faint as on the phone), sheet-bright.png (the
 * same, brightened to judge the drawing), and one PNG for each slide.
 */
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "guide-decor" }).apply { mkdirs() }
    val fonts = File(args.getOrElse(1) { "android/src/main/res/font" })
    val bold = Font.createFont(Font.TRUETYPE_FONT, File(fonts, "inter_bold.otf"))
    val medium = Font.createFont(Font.TRUETYPE_FONT, File(fonts, "inter_medium.otf"))
    val decor = GuideDecor(Typeface(bold))
    var failures = 0
    fun check(ok: Boolean, what: () -> String) {
        if (!ok) {
            failures++
            println("FAIL: ${what()}")
        }
    }

    // The rules for every look.
    for ((slide, look) in GuideDecor.LOOKS) {
        check(look.colour == GuideDecor.ORANGE && look.level in .06f..0.1201f || look.colour == GuideDecor.CREAM && look.level == .05f) {
            "$slide is orange at 6-12% or cream at 5%"
        }
        check(look.loop in 6_000L..20_000L) { "$slide loops in 6 to 20 s" }
    }
    check(GuideDecor.INK_LEVEL == .08f) { "Ink at 8% on the pass screen" }
    check(decor.draw(Canvas(), "nothing", 1000, RectF(0f, 0f, W, H), emptyList(), false, DP) == 0) { "An unknown slide draws nothing" }

    val rows = ArrayList<Pair<String, List<BufferedImage>>>()
    val brightRows = ArrayList<Pair<String, List<BufferedImage>>>()
    for (slide in SLIDES) {
        val screen = screenFor(slide)
        val look = GuideDecor.LOOKS.getValue(slide)
        val loop = look.loop
        val start = if (slide == GuideDecor.FINALE) 4_500L else 0L

        // Keep-out, shapes and strength, over 48 moments of a loop, and on the pass screen.
        for (onOrange in listOf(false, true)) for (k in 0 until 48) {
            val t = (if (slide == GuideDecor.FINALE && k < 12) k * 400L else start + loop * k / 48)
            val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
            val shapes = decor.draw(Canvas(image), slide, t, screen.area, screen.keepOut, onOrange, DP)
            check(shapes <= GuideDecor.MAX_SHAPES) { "$slide draws $shapes shapes at $t ms" }
            // The grass sets its own three strengths, the front layer strongest (owner, 2026-11-13).
            val level = if (slide == GuideDecor.FINALE) GuideDecor.GRASS_STRONGEST else if (onOrange) GuideDecor.INK_LEVEL else look.level
            var strongest = 0
            for (y in 0 until HEIGHT) for (x in 0 until WIDTH) {
                val a = image.getRGB(x, y) ushr 24
                if (a == 0) continue
                strongest = max(strongest, a)
                val inside = screen.keepOut.firstOrNull { r ->
                    x >= r.left - CLEAR + 1 && x < r.right + CLEAR - 1 && y >= r.top - CLEAR + 1 && y < r.bottom + CLEAR - 1
                }
                check(inside == null) { "$slide draws at $x, $y at $t ms, inside a keep-out rect or its 8 dp" }
                check(x >= screen.area.left && x < screen.area.right && y >= screen.area.top && y < screen.area.bottom) {
                    "$slide draws at $x, $y at $t ms, outside its area"
                }
                if (inside != null) break
            }
            check(strongest <= (level * 255).roundToInt() + 1) { "$slide reaches alpha $strongest at $t ms, over ${level * 100}%" }
            check(k > 3 || slide == GuideDecor.FINALE || strongest > 0 || slide == "flick" || slide == "swipe" || slide == "symmain") {
                "$slide draws nothing at $t ms"
            }
        }

        // The same moment draws the same picture, a loop later too, and the picture moves. Some rest between their moves, such
        // as the swipes, so it is enough that most of a loop's moments differ from the one before.
        for (t in listOf(start + 700L, start + 3_100L)) {
            val a = render(decor, slide, screen, t)
            check(same(a, render(decor, slide, screen, t))) { "$slide draws the same at $t ms" }
            check(same(a, render(decor, slide, screen, t + loop))) { "$slide comes round exactly after its ${loop} ms loop, from $t ms" }
        }
        val samples = (0 until 8).map { render(decor, slide, screen, start + loop * it / 8) }
        val moves = samples.indices.count { !same(samples[it], samples[(it + 1) % samples.size]) }
        check(moves >= 4) { "$slide moves: only $moves of 8 moments differ from the next" }
        // A fade of nothing draws nothing.
        check(decor.draw(Canvas(), slide, 1000, screen.area, screen.keepOut, false, DP, 0f) == 0) { "$slide at fade 0 draws nothing" }

        // Frame time, GuideDecor's own work: a canvas that draws nothing, after a warm-up.
        val blank = Canvas()
        for (i in 0 until 300) decor.draw(blank, slide, i * 33L, screen.area, screen.keepOut, false, DP)
        val frames = 600
        val began = System.nanoTime()
        for (i in 0 until frames) decor.draw(blank, slide, 10_000L + i * 33L, screen.area, screen.keepOut, false, DP)
        val millis = (System.nanoTime() - began) / 1e6 / frames
        check(millis < 2.0) { "$slide takes ${"%.3f".format(millis)} ms a frame" }
        println("$slide: ${"%.3f".format(millis)} ms a frame")

        val moments = if (slide == GuideDecor.FINALE) listOf(1_300L, 3_200L, 7_000L) else listOf(loop * 15 / 100, loop * 45 / 100, loop * 75 / 100)
        val orangeEnd = slide == GuideDecor.FINALE
        val frames3 = moments.map { frame(decor, slide, screen, it, bold, medium, false, orangeEnd) }
        val bright3 = moments.map { frame(decor, slide, screen, it, bold, medium, true, orangeEnd) }
        rows += slide to frames3
        brightRows += slide to bright3
        ImageIO.write(strip(bright3), "png", File(out, "$slide.png"))
    }
    // The pass screen: three slides on orange.
    val orange = listOf("type", "alt", "clipboard").map { slide ->
        val screen = screenFor(slide)
        frame(decor, slide, screen, GuideDecor.stillMillis(slide), bold, medium, false, onOrange = true)
    }
    rows += "on orange" to orange
    brightRows += "on orange" to orange
    ImageIO.write(sheet(rows, bold), "png", File(out, "sheet.png"))
    ImageIO.write(sheet(brightRows, bold), "png", File(out, "sheet-bright.png"))
    if (failures > 0) {
        println("$failures checks failed")
        kotlin.system.exitProcess(1)
    }
    println("PASS: guide decoration checks, sheets in ${out.path}")
}

private const val WIDTH = 1076
private const val HEIGHT = 1200
private const val W = WIDTH.toFloat()
private const val H = HEIGHT.toFloat()
/** The Titan 2 Elite's dp under FixedScale: the screen's width ÷ 688.6. */
private const val DP = 1076f / 688.6f
private const val CLEAR = GuideDecor.CLEAR_DP * DP

private val SLIDES = listOf("type", "swipe", "menu", "backspace", "flick", "hold", "steer", "selectmore", "alt", "holdsym",
    "symmain", "symgroups", "clown", "paste", "clipboard", GuideDecor.FINALE, GuideDecor.SETUP)

/** A slide's stand-in content: its texts, where they sit, and the keep-out rects they make. */
private class Screen(val area: RectF, val keepOut: List<RectF>, val texts: List<Triple<String, RectF, Int>>, val boxes: List<RectF>)

private val TITLES = mapOf(
    "type" to "MAKE A TYPO. WE'LL FIX IT.", "swipe" to "SWIPE IT. ONE HAND, NO QUESTIONS.", "menu" to "SAVE A WORD. THEN UNSAVE IT.",
    "backspace" to "DELETE THE EXTRA E. THE OLD WAY.", "flick" to "FLICK AWAY THE STUPID WORD.", "hold" to "HOLD THE FLICK. DELETE IN BULK.",
    "steer" to "STEER TO THE UNDERLINED WORD.", "selectmore" to "GRAB MY WHOLE HEART. THREE TAPS.",
    "alt" to "ADD THE *, THE TRADITIONAL WAY.", "holdsym" to "ADD THE ?. HOLD V FOR IT.", "symmain" to "ADD THE &. IT'S BEHIND SYM.",
    "symgroups" to "FIND THE WEIRD ONE. IT'S FURTHER IN.", "clown" to "SIGN IT WITH A CLOWN.", "paste" to "PASTE OVER THE ORANGE WORDS.",
    "clipboard" to "PASTE THE ONE NICE CLIP.",
)

private fun screenFor(slide: String): Screen {
    val area = RectF(0f, 110f, W, 1110f)
    val bold = Font.createFont(Font.TRUETYPE_FONT, File("android/src/main/res/font/inter_bold.otf").takeIf { it.exists() }
        ?: File(System.getProperty("decor.fonts", "android/src/main/res/font"), "inter_bold.otf"))
    fun width(text: String, size: Float) = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().run {
        getFontMetrics(bold.deriveFont(size)).stringWidth(text).toFloat()
    }
    val margin = 16 * DP
    val right = W - margin
    return when (slide) {
        GuideDecor.FINALE -> {
            val title = RectF(margin, 430f, right, 430f + 2 * 46 * DP)
            val line = RectF(margin, title.bottom + 24 * DP, margin + width("You know it all now.", 16 * DP), title.bottom + 24 * DP + 24 * DP)
            val button = RectF(margin, 960f, right, 960f + 52 * DP)
            Screen(area, listOf(title, line, button), listOf(Triple("GO TOUCH\nGRASS NOW", title, 46), Triple("You know it all now.", line, 16)),
                listOf(button))
        }
        GuideDecor.SETUP -> {
            val title = RectF(margin, 214f, margin + width("SET UP THE PHONE", 30 * DP), 214f + 30 * DP)
            val body = RectF(margin, title.bottom + 16 * DP, right, title.bottom + 16 * DP + 48 * DP)
            val list = RectF(margin, 430f, right, 430f + 4 * 64 * DP)
            val button = RectF(margin, 960f, right, 960f + 52 * DP)
            Screen(area, listOf(title, body, list, button), listOf(Triple("SET UP THE PHONE", title, 30),
                Triple("Two of the phone's own settings, then you're in.", body, 16)), listOf(list, button))
        }
        else -> {
            val label = RectF(margin, 150f, margin + width("TYPING", 12 * DP) * 1.1f, 150f + 12 * DP)
            val controls = RectF(W - margin - 220 * DP, 140f, right, 140f + 40 * DP)
            val titleText = TITLES.getValue(slide)
            val title = RectF(margin, 214f, margin + minOf(width(titleText, 30 * DP), right - margin), 214f + 30 * DP)
            val instructionText = "Do it on the keys. The line shows what's next."
            val instruction = RectF(margin, title.bottom + 16 * DP, margin + width(instructionText, 16 * DP) * .9f, title.bottom + 16 * DP + 24 * DP)
            val field = RectF(margin, instruction.bottom + 16 * DP, right, instruction.bottom + 16 * DP + 96 * DP)
            val keys = RectF(28f, 690f, 1048f, 1100f)
            Screen(area, listOf(label, controls, title, instruction, field, keys),
                listOf(Triple("TYPING", label, 12), Triple(titleText, title, 30), Triple(instructionText, instruction, 16)), listOf(controls, field, keys))
        }
    }
}

private fun render(decor: GuideDecor, slide: String, screen: Screen, t: Long): BufferedImage {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    decor.draw(Canvas(image), slide, t, screen.area, screen.keepOut, false, DP)
    return image
}

private fun same(a: BufferedImage, b: BufferedImage): Boolean {
    for (y in 0 until HEIGHT) for (x in 0 until WIDTH) if (a.getRGB(x, y) != b.getRGB(x, y)) return false
    return true
}

/** The slide as it would look: black, the decoration, then the stand-in content. Bright multiplies the decoration's strength. */
private fun frame(decor: GuideDecor, slide: String, screen: Screen, t: Long, bold: Font, medium: Font, bright: Boolean,
                  onOrange: Boolean = false): BufferedImage {
    val layer = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    decor.draw(Canvas(layer), slide, t, screen.area, screen.keepOut, onOrange, DP)
    if (bright) {
        val boost = if (slide == GuideDecor.FINALE) 1f else .55f / (if (onOrange) GuideDecor.INK_LEVEL else GuideDecor.LOOKS.getValue(slide).level)
        for (y in 0 until HEIGHT) for (x in 0 until WIDTH) {
            val p = layer.getRGB(x, y)
            val a = ((p ushr 24) * boost).roundToInt().coerceAtMost(255)
            layer.setRGB(x, y, (p and 0xFFFFFF) or (a shl 24))
        }
    }
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = if (onOrange) Color(0xEF804E) else Color.BLACK
    g.fillRect(0, 0, WIDTH, HEIGHT)
    g.drawImage(layer, 0, 0, null)
    val ink = if (onOrange) Color(0x160A04) else Color(0xFFF1E6)
    for (box in screen.boxes) {
        g.color = if (onOrange) Color(0, 0, 0, 30) else Color(0x161210)
        g.fillRoundRect(box.left.toInt(), box.top.toInt(), box.width().toInt(), box.height().toInt(), 24, 24)
    }
    // The keyboard drawing's keys, as outlines.
    screen.keepOut.lastOrNull()?.takeIf { it.top >= 600f && it.height() > 300f && slide != GuideDecor.SETUP && slide != GuideDecor.FINALE }?.let { k ->
        g.color = if (onOrange) Color(0x22, 0x10, 0x08, 60) else Color(0x2A2623)
        g.stroke = BasicStroke(2f)
        for (row in 0 until 4) for (col in 0 until 10) {
            val kw = k.width() / 10
            val kh = k.height() / 4
            g.drawRoundRect((k.left + col * kw + 5).toInt(), (k.top + row * kh + 6).toInt(), (kw - 10).toInt(), (kh - 12).toInt(), 18, 18)
        }
    }
    for ((text, r, size) in screen.texts) {
        g.color = if (size == 12) Color(0x99918A) else ink
        g.font = (if (size >= 30) bold else medium).deriveFont(size * DP)
        val lines = text.split("\n")
        val fm = g.fontMetrics
        for ((i, line) in lines.withIndex()) g.drawString(line, r.left, r.top + fm.ascent * .92f + i * size * DP * 1.05f)
    }
    g.dispose()
    return image
}

private fun strip(frames: List<BufferedImage>): BufferedImage {
    val gap = 16
    val image = BufferedImage(frames.size * (WIDTH + gap) - gap, HEIGHT, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.color = Color(0x40, 0x40, 0x40)
    g.fillRect(0, 0, image.width, image.height)
    for ((i, f) in frames.withIndex()) g.drawImage(f, i * (WIDTH + gap), 0, null)
    g.dispose()
    return image
}

private fun sheet(rows: List<Pair<String, List<BufferedImage>>>, bold: Font): BufferedImage {
    val scale = 3
    val cw = WIDTH / scale
    val ch = HEIGHT / scale
    val label = 150
    val gap = 8
    val image = BufferedImage(label + 3 * (cw + gap), rows.size * (ch + gap), BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = Color(0x30, 0x30, 0x30)
    g.fillRect(0, 0, image.width, image.height)
    g.font = bold.deriveFont(20f)
    for ((r, row) in rows.withIndex()) {
        g.color = Color.WHITE
        g.drawString(row.first, 10, r * (ch + gap) + ch / 2)
        for ((c, f) in row.second.withIndex()) g.drawImage(f, label + c * (cw + gap), r * (ch + gap), cw, ch, null)
    }
    g.composite = AlphaComposite.SrcOver
    g.dispose()
    return image
}
