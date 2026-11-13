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
 * Desktop drawings of the front page (the playground) in the v2 look (docs/design-v2.md §3), and the drawing kit the slides'
 * renders share: one dp is the screen's width ÷ 688.6, Inter in the type scale, the colours, and the keys at 60% of the real
 * height, edge to edge. The size is the Titan 2 Elite's screen.
 */

/** Design v2, as the phone draws it. */
internal object V2 {
    const val W = 1076
    const val H = 1200
    /** One dp: the screen's width ÷ 688.6 (design v2 §1). */
    const val D = 1076f / 688.6f
    fun dp(value: Number) = Math.round(value.toFloat() * D)

    val BLACK = Color(0x00, 0x00, 0x00)
    val ORANGE = Color(0xEF, 0x80, 0x4E)
    val CREAM = Color(0xFF, 0xF1, 0xE6)
    val MUTED = Color(0x99, 0x91, 0x8A)
    val SURFACE = Color(0x16, 0x12, 0x10)
    val DIVIDER = Color(0x2A, 0x26, 0x23)
    val INK = Color(0x16, 0x0A, 0x04)

    fun alpha(colour: Color, amount: Float) = Color(colour.red, colour.green, colour.blue, Math.round(amount.coerceIn(0f, 1f) * 255))

    fun blend(from: Color, to: Color, amount: Float): Color {
        fun mix(x: Int, y: Int) = Math.round(x + (y - x) * amount)
        return Color(mix(from.red, to.red), mix(from.green, to.green), mix(from.blue, to.blue))
    }

    /** Inter from the repository, as the phone has it. Falls back to the desktop's sans. */
    private val MEDIUM_FACE: Font by lazy { load("android/src/main/res/font/inter_medium.otf") }
    private val BOLD_FACE: Font by lazy { load("android/src/main/res/font/inter_bold.otf") }

    private fun load(path: String): Font = try {
        File(path).inputStream().use { Font.createFont(Font.TRUETYPE_FONT, it) }
    } catch (error: Exception) {
        println("  v2: $path not loaded (${error.message}), using sans")
        Font(Font.SANS_SERIF, Font.PLAIN, 1)
    }

    private fun tracked(font: Font, em: Float): Font =
        font.deriveFont(mapOf(java.awt.font.TextAttribute.TRACKING to em))

    // The type scale (design v2 §1), in dp.
    fun display(size: Float = 36f) = tracked(BOLD_FACE.deriveFont(size * D), -.01f)
    fun title() = BOLD_FACE.deriveFont(24f * D)
    fun body() = MEDIUM_FACE.deriveFont(19f * D)
    fun field() = MEDIUM_FACE.deriveFont(24f * D)
    fun label() = tracked(BOLD_FACE.deriveFont(14f * D), .08f)
    /** For the keys' labels: Inter Bold at a size in pixels. */
    fun keyFont(pixels: Float) = BOLD_FACE.deriveFont(pixels)

    fun image(): Pair<BufferedImage, Graphics2D> {
        val image = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        return image to g
    }

    /** The status bar's room at the top, and the keyboard's own bar's at the bottom, which the phone draws. */
    val STATUS = dp(26)
    val BAR = dp(40)
    const val SIDE = 24
    const val TOP_BAR = 48
    const val HEADER = 56

    /** The drawn keys (owner): the real keys' width and columns, 60% of their height. */
    val KEYS = KeyGeometry(W.toFloat(), KeyAlignment.DEFAULT, KeyGeometry.DRAWN)
    val keysTop get() = H - BAR - Math.ceil(KEYS.height.toDouble()).toInt()

    /** The height of one line of [font], as the phone lays it out with no font padding. */
    fun lineHeight(g: Graphics2D, font: Font) = g.getFontMetrics(font).let { it.ascent + it.descent } + dp(2)

    /** [text] at [font], shrunk to fit [width] down to 80% (design v2), and whether it still doesn't fit. */
    fun fitted(g: Graphics2D, text: String, font: Font, width: Int): Pair<Font, Boolean> {
        var size = font.size2D
        while (g.getFontMetrics(font.deriveFont(size)).stringWidth(text) > width && size > font.size2D * .8f) size -= .25f
        val shown = font.deriveFont(size)
        return shown to (g.getFontMetrics(shown).stringWidth(text) > width)
    }

    /** ✕, a 24 dp icon, its right edge on the side margin, centred on [cy]. */
    fun close(g: Graphics2D, cy: Int, colour: Color) {
        g.color = colour
        g.stroke = BasicStroke(dp(2).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val c = dp(7)
        val cx = W - dp(SIDE) - dp(12)
        g.drawLine(cx - c, cy - c, cx + c, cy + c)
        g.drawLine(cx + c, cy - c, cx - c, cy + c)
    }

    /**
     * The keys as GuideKeyboardView draws them (design v2): each key 3% of its width in from its sides, a cream outline at 16% over
     * the surface at 50%, labels cream at 70%; the slide's keys orange with ink labels; corner symbols only when [corners]; the part
     * the touchpad doesn't read dimmed.
     */
    fun keys(g: Graphics2D, top: Int, lit: Set<String> = emptySet(), held: Set<String> = emptySet(), hints: Map<String, String>? = null,
             corners: Boolean = false, onOrange: Boolean = false, zones: Boolean = false, shade: Color? = null, marks: Boolean = false) {
        val keys = KEYS
        // Each key's label as drawn, for the touch zone's brackets to pass behind.
        val labels = java.util.IdentityHashMap<Any, java.awt.geom.Rectangle2D>()
        for (box in keys.boxes) {
            val name = box.name
            val hint = hints?.get(name)
            val on = name in lit || hint != null && name == hints.keys.first()
            val steady = name in held
            val side = minOf(box.width, keys.pitch) * .03f
            val radius = minOf(dp(6).toFloat(), box.height / 2) * 2
            val shape = RoundRectangle2D.Float(box.left + side + .5f, top + box.top + .5f, box.width - 2 * side - 1f, box.height - 1f, radius, radius)
            val idleFill = if (onOrange) alpha(INK, .05f) else alpha(SURFACE, .5f)
            val idleLine = if (onOrange) alpha(INK, .5f) else alpha(CREAM, .16f)
            g.color = when {
                on -> if (onOrange) INK else ORANGE
                steady -> alpha(ORANGE, .85f)
                else -> idleFill
            }
            g.fill(shape)
            g.stroke = BasicStroke(dp(1).toFloat())
            g.color = if (on || steady) (if (onOrange) INK else ORANGE) else idleLine
            g.draw(shape)
            val labelColour = when {
                on || steady -> if (onOrange) ORANGE else INK
                onOrange -> INK
                else -> alpha(CREAM, .7f)
            }
            g.color = labelColour
            val text = when {
                name == Layout.SPACE -> ""
                name.length == 1 -> name
                else -> name.take(3)
            }
            if (name == Layout.SPACE) {
                // Space's mark, a little cup, as the phone draws it.
                val r = minOf(box.width, box.height) * .52f * .42f
                val half = box.width * .22f
                val cx = box.centreX
                val cy = top + box.centreY
                g.stroke = BasicStroke(maxOf(1.5f, r / .42f * .14f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val cup = java.awt.geom.Path2D.Float().apply {
                    moveTo(cx - half, cy - r * .1f)
                    lineTo(cx - half, cy + r * .35f)
                    lineTo(cx + half, cy + r * .35f)
                    lineTo(cx + half, cy - r * .1f)
                }
                g.draw(cup)
                labels[box] = g.stroke.createStrokedShape(cup).bounds2D
            }
            val size = minOf(box.width, box.height) * (if (Layout.isLetter(name) || text.length == 1) .52f else .36f)
            g.font = if (keyFont(size).canDisplayUpTo(text) == -1) keyFont(size) else Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(size)
            val fm = g.fontMetrics
            val lift = if (hint != null) box.height * .14f else 0f
            val textX = box.centreX - fm.stringWidth(text) / 2f
            val textY = top + box.centreY + (fm.ascent - fm.descent) / 2f - lift
            g.drawString(text, textX, textY)
            if (text.isNotEmpty()) labels[box] = g.font.createGlyphVector(g.fontRenderContext, text).visualBounds
                .let { java.awt.geom.Rectangle2D.Float(textX + it.x.toFloat(), textY + it.y.toFloat(), it.width.toFloat(), it.height.toFloat()) }
            if (hint != null) {
                g.font = keyFont(maxOf(13f * D, minOf(box.width, box.height) * .3f))
                g.drawString(hint, box.centreX - g.fontMetrics.stringWidth(hint) / 2f, top + box.bottom - box.height * .12f)
            }
            if (corners && Layout.isLetter(name) && hint == null) {
                CORNER[name]?.let { symbol ->
                    g.font = keyFont(maxOf(13f * D, minOf(box.width, box.height) * .3f))
                    g.color = alpha(labelColour, .6f)
                    g.drawString(symbol, box.left + side + box.width * .1f, top + box.top + g.fontMetrics.ascent + box.height * .05f)
                }
            }
        }
        val area = keys.area(.05f, .5f / 4, .95f, 3.54f / 4)
        if (marks) {
            // The playground's touch zone (owner): corner brackets at its real corners, clear of the corner keys' labels, and
            // "TOUCH ZONE" over the top right one, as GuideKeyboardView's drawZoneMarks.
            val colour = if (onOrange) INK else ORANGE
            g.color = colour
            g.stroke = BasicStroke(2.5f * D, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            // Every label an arm reaches is cut out of it, 4 dp clear, and each arm shows 20 dp past its last gap (Master,
            // 2026-11-13), as drawZoneMarks.
            val stroke = 2.5f * D
            val clearing = 4f * D
            val shown = 20f * D
            for ((cornerX, cornerY, dx, dy) in listOf(listOf(area.left, area.top, 1f, 1f), listOf(area.right, area.top, -1f, 1f),
                listOf(area.left, area.bottom, 1f, -1f), listOf(area.right, area.bottom, -1f, -1f))) {
                val x = cornerX
                val y = top + cornerY
                var across = 26f * D
                var along = 26f * D
                var gaps = emptyList<java.awt.geom.Rectangle2D>()
                for (round in 0..1) {
                    val reach = java.awt.geom.Rectangle2D.Float(minOf(x, x + dx * across) - stroke / 2, minOf(y, y + dy * along) - stroke / 2,
                        across + stroke, along + stroke)
                    gaps = keys.boxes.filter { reach.intersects(it.left.toDouble(), (top + it.top).toDouble(), it.width.toDouble(), it.height.toDouble()) }
                        .mapNotNull { labels[it] }
                        .map { java.awt.geom.Rectangle2D.Double(it.x - clearing, it.y - clearing, it.width + 2 * clearing, it.height + 2 * clearing) }
                    if (round > 0) break
                    across = maxOf(26f * D, (gaps.filter { it.minY <= y + stroke / 2 && it.maxY >= y - stroke / 2 && (if (dx > 0) it.minX - x else x - it.maxX) < across }
                        .maxOfOrNull { (if (dx > 0) it.maxX - x else x - it.minX).toFloat() } ?: 0f) + shown)
                    along = maxOf(26f * D, (gaps.filter { it.minX <= x + stroke / 2 && it.maxX >= x - stroke / 2 && (if (dy > 0) it.minY - y else y - it.maxY) < along }
                        .maxOfOrNull { (if (dy > 0) it.maxY - y else y - it.minY).toFloat() } ?: 0f) + shown)
                }
                val clear = java.awt.geom.Area(java.awt.Rectangle(0, 0, W, H))
                for (gap in gaps) clear.subtract(java.awt.geom.Area(gap))
                g.clip = clear
                val path = java.awt.geom.Path2D.Float()
                path.moveTo(x + dx * across, y)
                path.lineTo(x, y)
                path.lineTo(x, y + dy * along)
                g.draw(path)
                g.clip = null
            }
            g.font = label()
            g.drawString("TOUCH ZONE", area.right - g.fontMetrics.stringWidth("TOUCH ZONE"), top - 6f * D)
            return
        }
        // The part the touchpad doesn't read, dimmed, as GuideKeyboardView's drawTouchArea.
        val outside = java.awt.geom.Area(java.awt.Rectangle(0, top, W, Math.ceil(keys.height.toDouble()).toInt()))
        outside.subtract(java.awt.geom.Area(RoundRectangle2D.Float(area.left, top + area.top, area.width, area.height, dp(12) * 2f, dp(12) * 2f)))
        g.color = alpha(shade ?: if (onOrange) ORANGE else BLACK, .5f)
        g.fill(outside)
        if (zones) {
            // The held delete's zones: back on Backspace pauses; further out, a lighter box, is top speed.
            val fast = keys.onRow(1, .05f, .95f - 5.5f / 10f)
            g.color = alpha(CREAM, .1f)
            g.fill(RoundRectangle2D.Float(fast.left, top + fast.top, fast.width, fast.height, dp(8).toFloat(), dp(8).toFloat()))
            val pause = keys.onRow(1, .95f - .8f / 10f, .95f)
            g.color = alpha(CREAM, .2f)
            g.fill(RoundRectangle2D.Float(pause.left, top + pause.top, pause.width, pause.height, dp(8).toFloat(), dp(8).toFloat()))
            g.font = keyFont(maxOf(13f * D, minOf(keys.key, keys.keyHeight) * .24f))
            g.color = alpha(CREAM, .85f)
            g.drawString("pause", pause.left + dp(4), top + pause.bottom - dp(4))
        }
    }

    /** The Alt symbols printed on the Titan 2 Elite's letter keys, for the corner symbols (the app reads them from the phone). */
    private val CORNER = mapOf("Q" to "#", "W" to "1", "E" to "2", "R" to "3", "T" to "(", "Y" to ")", "U" to "_", "I" to "-", "O" to "+",
        "P" to "@", "A" to "*", "S" to "4", "D" to "5", "F" to "6", "G" to "/", "H" to ":", "J" to ";", "K" to "'", "L" to "\"",
        "Z" to "7", "X" to "8", "C" to "9", "V" to "?", "B" to "!", "N" to ",", "M" to ".")

    /** A note on a render, for review only, in blue. */
    fun note(g: Graphics2D, text: String, x: Int, y: Int) {
        g.font = Font(Font.SANS_SERIF, Font.ITALIC, dp(10))
        g.color = Color(0x66, 0xCC, 0xFF)
        g.drawString(text, x, y)
    }
}

/** What follows the front page's sentence in the renders. */
private const val FOLLOWING = "the sound of real keys is the only applause you will get today"

/** [sentence] then [following], wrapped at [width] by whole words, with [gap] in place of a space where they meet. */
private fun wrappedWithGap(g: Graphics2D, sentence: String, following: String, width: Int, gap: Int): List<List<String>> {
    val words = sentence.split(' ') + following.split(' ')
    val first = sentence.split(' ').size
    val space = g.fontMetrics.stringWidth(" ")
    val lines = ArrayList<List<String>>()
    var line = ArrayList<String>()
    var used = 0
    for ((index, word) in words.withIndex()) {
        val join = if (line.isEmpty()) 0 else if (index == first) gap else space
        val w = g.fontMetrics.stringWidth(word)
        if (line.isNotEmpty() && used + join + w > width) {
            lines += line
            line = ArrayList()
            used = 0
        }
        used += (if (line.isEmpty()) 0 else join) + w
        line += word
    }
    if (line.isNotEmpty()) lines += line
    return lines
}

/** Words wrapped at [width], whole words only. */
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

private var frontProblems = 0

private fun problem(what: String) {
    println("  front page: $what")
    frontProblems++
}

/**
 * The front page (design v2 §3). [typed] is what's in the text box; [wpm] the speed, or null before a real word is typed;
 * [heat] how warm the background is, from 0 to 1; [comment] the line under the header; [passage] the line to type along to, with
 * [done] words typed.
 */
private fun drawFront(file: File, typed: String, wpm: Int?, heat: Float, comment: String, passage: String, done: Int,
                      following: String = FOLLOWING) {
    val (image, g) = V2.image()
    val warmth = V2.blend(V2.BLACK, V2.ORANGE, heat * .85f)
    val warmthArgb = (0xFF shl 24) or (warmth.red shl 16) or (warmth.green shl 8) or warmth.blue
    // Ink once cream drops below 4.5:1, as WpmMeter.inkOn has it.
    val ink = WpmMeter.inkOn(warmthArgb, inkNow = false)
    val strong = if (ink) V2.INK else V2.CREAM
    val quiet = if (ink) V2.alpha(V2.INK, .6f) else Color(WpmMeter.quietOn(warmthArgb), true)
    g.color = warmth
    g.fillRect(0, 0, V2.W, V2.H)
    val side = V2.dp(V2.SIDE)
    val inner = V2.W - 2 * side
    val headerTop = V2.STATUS
    val header = V2.dp(V2.HEADER)
    val mid = headerTop + header / 2

    // The header: the wordmark, Title, "Titan" cream and "Keys" orange; the words a minute in the middle; Guide and Settings as
    // outline pills with muted outlines (design v2).
    g.font = V2.title()
    val fm = g.fontMetrics
    val base = mid + (fm.ascent - fm.descent) / 2
    g.color = strong
    g.drawString("Titan", side, base)
    g.color = if (ink) V2.INK else V2.ORANGE
    g.drawString("Keys", side + fm.stringWidth("Titan"), base)
    val wordmarkRight = side + fm.stringWidth("TitanKeys")
    g.font = V2.label()
    val pill = V2.dp(44)
    var right = V2.W - side
    var pillsLeft = right
    for (label in listOf("SETTINGS", "GUIDE")) {
        val wide = g.fontMetrics.stringWidth(label) + 2 * V2.dp(16)
        val left = right - wide
        val shape = RoundRectangle2D.Float(left.toFloat(), (mid - pill / 2).toFloat(), wide.toFloat(), pill.toFloat(), pill.toFloat(), pill.toFloat())
        g.color = if (ink) V2.alpha(V2.INK, .5f) else V2.MUTED
        g.stroke = BasicStroke(maxOf(2, V2.dp(1)).toFloat())
        g.draw(shape)
        g.color = strong
        g.drawString(label, left + V2.dp(16), mid + (g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
        pillsLeft = left
        right = left - V2.dp(8)
    }
    if (wpm != null) {
        val number = "$wpm"
        val unit = " WPM"
        val wide = g.fontMetrics.stringWidth(number + unit)
        val x = V2.W / 2 - wide / 2
        if (x < wordmarkRight + V2.dp(8) || x + wide > pillsLeft - V2.dp(8)) problem("the words a minute don't fit between the wordmark and the pills")
        g.color = strong
        g.drawString(number, x, mid + (g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
        g.color = quiet
        g.drawString(unit, x + g.fontMetrics.stringWidth(number), mid + (g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
    }
    val headerBottom = headerTop + header

    // The block: the comment, one line of Body; 12 dp; the line to type along to, two lines of Body; 24 dp; the text box, three
    // lines of Field. Centred between the header and the keys, with at least 24 dp above and below.
    val bodyLine = V2.lineHeight(g, V2.body())
    val fieldLine = V2.lineHeight(g, V2.field())
    val pad = V2.dp(16)
    val boxHeight = 3 * fieldLine + 2 * pad
    val blockHeight = bodyLine + V2.dp(12) + 2 * bodyLine + V2.dp(24) + boxHeight
    // The playground's keys keep room above them for the touch zone's label.
    val keysTop = V2.keysTop
    val room = keysTop - V2.dp(24) - headerBottom
    if (room < blockHeight + 2 * V2.dp(24)) problem("NO ROOM: the block needs ${blockHeight + 2 * V2.dp(24)} px, has $room")
    var y = headerBottom + (room - blockHeight) / 2

    g.font = V2.body()
    g.color = quiet
    g.drawString(comment, side, y + g.fontMetrics.ascent)
    if (g.fontMetrics.stringWidth(comment) > inner) problem("the comment is over one line")
    y += bodyLine + V2.dp(12)

    // The line to type along to: typed words cream, the rest muted, the word you're on orange and underlined. Always two full lines,
    // carrying on into the next sentence, as GuideActivity's showPassage: an en space away and, on black, at half the muted
    // strength, so the join reads as a break.
    val sentenceWords = passage.split(' ').size
    val gap = g.fontMetrics.stringWidth(" ")
    val lines = wrappedWithGap(g, passage, following, inner, gap).take(2)
    if (lines.last().size < 2) problem("the line to type along to ends on a lone word")
    val next = if (ink) quiet else V2.alpha(quiet, .5f)
    var count = 0
    for ((index, line) in lines.withIndex()) {
        var x = side.toFloat()
        val lineBase = y + index * bodyLine + g.fontMetrics.ascent
        for (word in line) {
            if (count == sentenceWords && x > side) x += gap - g.fontMetrics.stringWidth(" ")
            val w = g.fontMetrics.stringWidth(word)
            g.color = when {
                count < done -> strong
                count == done -> if (ink) V2.INK else if (WpmMeter.quietOn(warmthArgb) == 0xFF99918A.toInt()) V2.ORANGE else strong
                count >= sentenceWords -> next
                else -> quiet
            }
            g.drawString(word, x, lineBase.toFloat())
            if (count == done) g.fillRect(x.toInt(), lineBase + V2.dp(3), w, V2.dp(2))
            x += g.fontMetrics.stringWidth("$word ")
            count++
        }
    }
    y += 2 * bodyLine + V2.dp(24)

    // The text box: the surface, 16 dp corners and padding, three lines; on orange, a faint ink wash.
    val box = RoundRectangle2D.Float(side.toFloat(), y.toFloat(), inner.toFloat(), boxHeight.toFloat(), V2.dp(32).toFloat(), V2.dp(32).toFloat())
    g.color = if (ink) V2.alpha(V2.INK, .1f) else V2.SURFACE
    g.fill(box)
    // Its personality (owner): a thin orange bar down its left.
    g.color = if (ink) V2.INK else V2.ORANGE
    g.fill(RoundRectangle2D.Float(side + 6 * V2.D, y + 14 * V2.D, 3 * V2.D, boxHeight - 28 * V2.D, 4 * V2.D, 4 * V2.D))
    g.font = V2.field()
    val shown = if (typed.isEmpty()) emptyList() else wrapped(g, typed, inner - 2 * pad).takeLast(3)
    g.color = if (ink) V2.INK else V2.CREAM
    for ((index, line) in shown.withIndex()) g.drawString(line, side + pad, y + pad + index * fieldLine + g.fontMetrics.ascent)
    val caretX = side + pad + (shown.lastOrNull()?.let { g.fontMetrics.stringWidth(it) + V2.dp(1) } ?: 0)
    val caretLine = (shown.size - 1).coerceAtLeast(0)
    g.color = if (ink) V2.INK else V2.ORANGE
    g.fillRect(caretX, y + pad + caretLine * fieldLine + V2.dp(2), V2.dp(2), fieldLine - V2.dp(4))

    V2.keys(g, keysTop, onOrange = ink, shade = warmth, marks = true)
    V2.note(g, "render note: the keyboard's own bar is here", side, V2.H - V2.BAR / 2)
    g.dispose()
    ImageIO.write(image, "png", file)
}

/** The playground's real comments on your speed, from the app's strings, one for each of [WpmComments.STEPS]. */
private val COMMENTS: List<String> by lazy {
    val xml = File("android/src/main/res/values/strings.xml").readText()
    val array = xml.substringAfter("<string-array name=\"playground_comments\">").substringBefore("</string-array>")
    Regex("<item>\"?(.*?)\"?</item>").findAll(array).map { it.groupValues[1].replace("\\'", "'") }.toList()
}

private fun comment(wpm: Int) = COMMENTS.getOrNull(WpmComments.step(wpm)) ?: IDLE

private const val IDLE = "Type or swipe whatever you like. Follow the line below, or ignore it."

/** The front page's renders: idle, typing, warm, and fully orange, where every text turns ink. */
internal fun drawFrontPage(folder: String) {
    val out = File(folder).apply { mkdirs() }
    frontProblems = 0
    val passage = "titankeys fixes your typos so you can keep pretending you never make any"
    drawFront(File(out, "front-1-idle.png"), "", null, 0f, IDLE, passage, 0)
    drawFront(File(out, "front-2-typing.png"), "titankeys fixes your typos so", 34, WpmMeter.heat(34), comment(34), passage, 5)
    drawFront(File(out, "front-3-warm.png"), "titankeys fixes your typos so you can keep pretending", 62, WpmMeter.heat(62),
        comment(62), passage, 9)
    drawFront(File(out, "front-4-orange.png"), "titankeys fixes your typos so you can keep pretending you never make any", 96,
        WpmMeter.heat(96), comment(96), passage, 12)
    println("front page drawn to $folder" + if (frontProblems > 0) " with $frontProblems problems" else "")
}
