package com.atlas.keyboard

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.min

/*
 * Checks the real keys' geometry ([KeyGeometry]) and how Key calibration's two edges move it ([KeyAlignment]): the defaults are the
 * measured Titan 2 Elite, the touchpad lands on the keys' middles, the edges move everything, and the guide's zones sit on the
 * keys. Set CALIBRATION_PNG to a folder to draw the calibration screen there, and KEYS_PNG to a folder to draw the guide's keys.
 */
fun main() {
    measuredDefaults()
    touchpadOnKeys()
    edgesMoveEverything()
    zonesOnKeys()
    slidersRoundTrip()
    System.getenv("CALIBRATION_PNG")?.let { folder ->
        File(folder).mkdirs()
        drawCalibration(KeyAlignment.DEFAULT, File(folder, "calibration-default.png"))
        drawCalibration(KeyAlignment(.03f, .96f), File(folder, "calibration-moved.png"))
        println("Drew the calibration screen in $folder")
    }
    System.getenv("KEYS_PNG")?.let { folder ->
        File(folder).mkdirs()
        drawKeys(KeyAlignment.DEFAULT, Scene.Plain, File(folder, "keys-default.png"))
        drawKeys(KeyAlignment.DEFAULT, Scene.Lit, File(folder, "keys-lit.png"))
        drawKeys(KeyAlignment.DEFAULT, Scene.Word, File(folder, "keys-word.png"))
        drawKeys(KeyAlignment.DEFAULT, Scene.Taps, File(folder, "keys-taps.png"))
        drawKeys(KeyAlignment.DEFAULT, Scene.Hold, File(folder, "keys-hold.png"))
        drawKeys(KeyAlignment(.03f, .96f), Scene.Word, File(folder, "keys-word-moved.png"))
        println("Drew the guide's keys in $folder")
    }
    println("Key alignment checks passed")
}

/** At 1080 px with the defaults, the keys are the ones measured from Unihertz's drawing. */
private fun measuredDefaults() {
    check(KeyAlignment.DEFAULT.left == 0f && KeyAlignment.DEFAULT.right == 1f) { "the defaults span the screen" }
    check(KeyAlignment.current == KeyAlignment.DEFAULT) { "nothing is calibrated until someone calibrates" }
    val keys = KeyGeometry(1080f, KeyAlignment.DEFAULT)
    val q = keys.boxes.first { it.name == "Q" }
    val p = keys.boxes.first { it.name == "P" }
    near(q.left, 0f, "Q's left edge is the screen's")
    near(p.right, 1080f, "P's right edge is the screen's")
    near(keys.gap / keys.key, 24f / 84f, "gap to key")
    near(keys.rowPitch / keys.keyHeight, 123f / 107f, "row to key height")
    near(keys.keyHeight / keys.key, 107f / 84f, "a key's shape")
    // 1056 px of measured keys fill 1080, so each is a little bigger, in the same proportions.
    near(keys.key, 84f * 1080f / 1056f, "a key's width")
    near(keys.height, 3 * keys.rowPitch + keys.keyHeight, "four rows")
    // The three letter rows sit in the same columns, ten keys each, a key apart.
    for (row in 0..2) {
        val boxes = keys.boxes.filter { it.row == row }
        same(boxes.size, 10, "keys on row $row")
        for ((column, box) in boxes.withIndex()) {
            near(box.left, keys.boxes[column].left, "row $row column $column lines up with the top row")
            near(box.width, keys.key, "row $row column $column's width")
            near(box.height, keys.keyHeight, "row $row column $column's height")
            near(box.top, row * keys.rowPitch, "row $row's top")
        }
    }
    // The bottom row spans the same edges with the same gaps, and its keys keep Layout's widths.
    val bottom = keys.boxes.filter { it.row == 3 }
    same(bottom.map { it.name }, Layout.rows[3], "the bottom row's keys")
    near(bottom.first().left, q.left, "the bottom row starts under Q")
    near(bottom.last().right, p.right, "and ends under P")
    for (index in 1 until bottom.size) near(bottom[index].left - bottom[index - 1].right, keys.gap, "the bottom row's gap $index")
    near(bottom[3].width / bottom[0].width, 2.8f / 1.2f, "Space is as much wider as Layout says")
    near(bottom.last().bottom, keys.height, "the bottom row ends the drawing")
    near(KeyGeometry.heightFor(1080f, KeyAlignment.DEFAULT), keys.height, "heightFor")
    // Each column of the menu and the Sym panel runs from the middle of one gap to the next, and holds its key in the middle.
    for (column in 0..9) {
        val (left, right) = KeyAlignment.column(1080f, column)
        near(right - left, keys.pitch, "column $column's width")
        near((left + right) / 2, keys.boxes[column].centreX, "column $column's middle")
    }
}

/** The touchpad's column and row middles land on the keys' middles, and the mapping goes both ways. */
private fun touchpadOnKeys() {
    for (alignment in listOf(KeyAlignment.DEFAULT, KeyAlignment(.04f, .93f), KeyAlignment(-.05f, 1.06f))) {
        val keys = KeyGeometry(1080f, alignment)
        for (row in 0..2) for (column in 0..9) {
            val box = keys.boxes[row * 10 + column]
            near(keys.x((column + .5f) / 10), box.centreX, "column $column's middle")
            near(keys.y((row + .5f) / 4), box.centreY, "row $row's middle")
        }
        near(keys.y(3.5f / 4), keys.boxes.last().centreY, "the bottom row's middle")
        for (letter in Layout.LETTERS) {
            val (x, y) = Layout.centre(letter)
            val box = keys.boxes.first { it.name == letter.uppercase() }
            near(keys.x(x), box.centreX, "$letter's middle across")
            near(keys.y(y), box.centreY, "$letter's middle down")
        }
        for (t in listOf(0f, .13f, .5f, .97f, 1f)) {
            near(keys.touchX(keys.x(t)), t, "back across from $t")
            near(keys.touchY(keys.y(t)), t, "back down from $t")
        }
    }
}

/** Moving an edge moves every key, the bottom row and the touchpad with it, and scales the heights. */
private fun edgesMoveEverything() {
    val base = KeyGeometry(1080f, KeyAlignment.DEFAULT)
    val moved = KeyGeometry(1080f, KeyAlignment(.05f, 1f))
    near(moved.boxes.first().left, 54f, "Q's left follows the left edge")
    near(moved.boxes[9].right, 1080f, "P's right stays")
    for (index in 1 until 10) check(moved.boxes[index].left > base.boxes[index].left) { "column $index moves right" }
    check(moved.boxes.last().right == base.boxes.last().right && moved.boxes[30].left > base.boxes[30].left) { "the bottom row follows" }
    check(moved.x(.5f) > base.x(.5f)) { "the touchpad follows" }
    near(moved.key / base.key, .95f, "a narrower span makes smaller keys")
    near(moved.height / base.height, .95f, "and a shorter keyboard")
    val right = KeyGeometry(1080f, KeyAlignment(0f, .9f))
    near(right.boxes[9].right, 972f, "P's right follows the right edge")
    near(right.boxes[0].left, 0f, "Q stays")
    near(KeyAlignment.column(1080f, 0).first, -base.gap / 2, "with the defaults, Q's column starts half a gap past the screen")
    // Every column moves when the edges do, as the menu and the Sym panel read them.
    val saved = KeyAlignment.current
    KeyAlignment.current = KeyAlignment(.05f, .95f)
    try {
        val keys = KeyGeometry(1080f)
        for (column in 0..9) near((KeyAlignment.column(1080f, column).let { (l, r) -> (l + r) / 2 }), keys.boxes[column].centreX, "column $column calibrated")
    } finally {
        KeyAlignment.current = saved
    }
}

// The guide's zones, as GuideKeyboardView has them in touchpad space. run.sh checks that these are still its numbers.
private const val AREA_LEFT = .05f
private const val AREA_RIGHT = .95f
private const val AREA_TOP = .5f / 4
private const val AREA_BOTTOM = 3.54f / 4
private const val ZONE_LEFT = .36f
private const val ZONE_TOP = .5f
private const val ZONE_RIGHT = .64f
private const val ZONE_BOTTOM = .885f
private const val ZONE_TAP_ROW = .72f
private const val HOLD_PAUSE_X = .95f - .8f / 10f
private const val HOLD_TOP_X = .95f - 5.5f / 10f

/** The touch area, the tap zone and the held delete's boxes sit on the keys they name, wherever the edges are. */
private fun zonesOnKeys() {
    for (alignment in listOf(KeyAlignment.DEFAULT, KeyAlignment(.04f, .93f))) {
        val keys = KeyGeometry(1080f, alignment)
        fun box(name: String) = keys.boxes.first { it.name == name }
        val area = keys.area(AREA_LEFT, AREA_TOP, AREA_RIGHT, AREA_BOTTOM)
        near(area.left, box("Q").centreX, "the touch area starts at Q's middle")
        near(area.right, box("P").centreX, "and ends at P's")
        near(area.top, box("Q").centreY, "from the top row's middle")
        check(area.bottom > box(Layout.SPACE).top && area.bottom < box(Layout.SPACE).bottom) { "to inside the bottom row" }
        val tap = keys.area(ZONE_LEFT, ZONE_TOP, ZONE_RIGHT, ZONE_BOTTOM)
        // As wide as the Space bar itself (owner, 2026-11-12), within a third of a key of its ends.
        val third = (box("C").right - box("C").left) / 3
        check(kotlin.math.abs(tap.left - box(Layout.SPACE).left) < third) { "the tap zone starts where Space does: ${tap.left} against ${box(Layout.SPACE).left}" }
        check(kotlin.math.abs(tap.right - box(Layout.SPACE).right) < third) { "and ends where it does: ${tap.right} against ${box(Layout.SPACE).right}" }
        check(tap.top > box("A").bottom && tap.top < box("Z").top) { "from between the middle and bottom letter rows" }
        check(tap.bottom > box(Layout.SPACE).top && tap.bottom < box(Layout.SPACE).bottom) { "down onto Space" }
        val tapY = keys.y(ZONE_TAP_ROW)
        check(tapY > box("V").top && tapY < box("V").bottom) { "the taps land on the bottom letter row" }
        val pause = keys.onRow(1, HOLD_PAUSE_X, AREA_RIGHT)
        same(pause.top, box(Layout.BACKSPACE).top, "the pause box is on the Backspace row")
        same(pause.bottom, box(Layout.BACKSPACE).bottom, "from its top to its bottom")
        check(pause.left > box("L").left && pause.left < box("L").right) { "it starts on L" }
        near(pause.right, box(Layout.BACKSPACE).centreX, "and ends at Backspace's middle, where the touchpad stops")
        val fast = keys.onRow(1, AREA_LEFT, HOLD_TOP_X)
        near(fast.left, box("A").centreX, "top speed starts at A's middle")
        check(fast.right > box("F").right && fast.right < box("G").left) { "and reaches the gap between F and G: ${fast.right}" }
    }
}

private fun slidersRoundTrip() {
    same(KeyAlignment.leftAt(KeyAlignment.leftProgress(0f)), 0f, "the left slider's default")
    same(KeyAlignment.rightAt(KeyAlignment.rightProgress(1f)), 1f, "the right slider's default")
    same(KeyAlignment.leftProgress(0f), KeyAlignment.EDGE_STEPS / 2, "the left slider starts in the middle")
    near(KeyAlignment.leftAt(0), -KeyAlignment.EDGE_REACH, "the leftmost left edge")
    near(KeyAlignment.rightAt(KeyAlignment.EDGE_STEPS), 1f + KeyAlignment.EDGE_REACH, "the rightmost right edge")
    for (progress in 0..KeyAlignment.EDGE_STEPS) {
        same(KeyAlignment.leftProgress(KeyAlignment.leftAt(progress)), progress, "left step $progress")
        same(KeyAlignment.rightProgress(KeyAlignment.rightAt(progress)), progress, "right step $progress")
    }
}

private fun same(actual: Any?, expected: Any?, message: String) = check(actual == expected) { "$message: expected $expected, got $actual" }

private fun near(actual: Float, expected: Float, message: String) =
    check(abs(actual - expected) < 1e-3f) { "$message: expected $expected, got $actual" }

/** The phone's screen: 1080 px wide at 2.625 px a dp. */
private const val WIDTH = 1080
private const val DP = 2.625f
private fun dp(value: Number) = value.toFloat() * DP
private fun rgb(argb: Long) = Color(argb.toInt(), true)
private fun faded(argb: Long, alpha: Float) = Color(((argb and 0xFFFFFF) or ((alpha * 255).toLong() shl 24)).toInt(), true)

/**
 * The calibration screen as KeyCalibrationActivity lays it out, with its KeyGrid at the bottom edge: each letter key a thin cream
 * outline, each gap a faint orange stripe, and Q's left edge and P's right edge in orange. The desktop's fonts are close, not exact.
 */
private fun drawCalibration(alignment: KeyAlignment, file: File) {
    val keys = KeyGeometry(WIDTH.toFloat(), alignment)
    // The text, the sliders and Reset, then the grid below them, as the screen scrolls them over it.
    val height = (dp(560) + keys.letterHeight).toInt()
    val image = BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = rgb(0xFF160A04)
    g.fillRect(0, 0, WIDTH, height)
    val pad = dp(16)
    var y = dp(28) + pad
    g.color = rgb(0xFFFFF1E6)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(24).toInt())
    y += dp(28)
    g.drawString("Key calibration", pad, y)
    g.color = rgb(0xFFA2958C)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, dp(15).toInt())
    for (line in listOf("Line the drawing at the bottom up with the real", "keys under the screen. Move the left edge to Q's", "left edge and the right edge to P's right edge.",
        "It saves as you go.")) {
        y += dp(22)
        g.drawString(line, pad, y)
    }
    y += dp(16)
    val sliderHeight = dp(64)
    g.color = rgb(0xFF21140C)
    g.fill(RoundRectangle2D.Float(pad, y, WIDTH - 2 * pad, 2 * sliderHeight + dp(20), dp(24), dp(24)))
    y += dp(12)
    val sliders = listOf(
        Triple("Q's left edge", KeyCalibrationText.edge(Math.round(alignment.left * WIDTH)), KeyAlignment.leftProgress(alignment.left)),
        Triple("P's right edge", KeyCalibrationText.edge(Math.round((1 - alignment.right) * WIDTH)), KeyAlignment.rightProgress(alignment.right)),
    )
    for ((name, value, progress) in sliders) {
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, dp(15).toInt())
        g.color = rgb(0xFFFFF1E6)
        g.drawString(name, pad + dp(16), y + dp(18))
        g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(15).toInt())
        g.color = rgb(0xFFEF804E)
        g.drawString(value, WIDTH - pad - dp(16) - g.fontMetrics.stringWidth(value), y + dp(18))
        val trackLeft = pad + dp(28)
        val trackRight = WIDTH - pad - dp(28)
        val trackY = y + dp(42)
        g.color = rgb(0xFF3A2A20)
        g.fill(RoundRectangle2D.Float(trackLeft, trackY - dp(2), trackRight - trackLeft, dp(4), dp(4), dp(4)))
        val thumb = trackLeft + (trackRight - trackLeft) * progress / KeyAlignment.EDGE_STEPS
        g.color = rgb(0xFFEF804E)
        g.fill(RoundRectangle2D.Float(trackLeft, trackY - dp(2), thumb - trackLeft, dp(4), dp(4), dp(4)))
        g.fill(Ellipse2D.Float(thumb - dp(8), trackY - dp(8), dp(16), dp(16)))
        y += sliderHeight
    }
    y += dp(32)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(15).toInt())
    val resetWidth = g.fontMetrics.stringWidth("Reset") + dp(40)
    g.color = rgb(0xFFEF804E)
    g.fill(RoundRectangle2D.Float(pad, y, resetWidth, dp(48), dp(48), dp(48)))
    g.color = rgb(0xFF160A04)
    g.drawString("Reset", pad + dp(20), y + dp(30))
    // The grid, at the very bottom, as KeyGrid draws it.
    val top = height - keys.letterHeight
    val stroke = DP
    g.stroke = BasicStroke(stroke)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(keys.key * .3f)
    for (box in keys.boxes.filter { it.row <= 2 }) {
        g.color = rgb(0xFFFFF1E6)
        g.draw(Rectangle2D.Float(box.left + stroke / 2, top + box.top + stroke / 2, box.width - stroke, box.height - stroke))
        g.color = rgb(0x80FFF1E6)
        val metrics = g.fontMetrics
        g.drawString(box.name, box.centreX - metrics.stringWidth(box.name) / 2f, top + box.centreY + (metrics.ascent - metrics.descent) / 2f)
    }
    g.color = rgb(0x40EF804E)
    for (row in 0..2) for (column in 0 until 9) {
        g.fill(Rectangle2D.Float(keys.keyLeft(column) + keys.key, top + keys.rowTop(row), keys.gap, keys.keyHeight))
    }
    g.color = rgb(0xFFEF804E)
    g.fill(Rectangle2D.Float(keys.left, top, dp(2), keys.letterHeight))
    g.fill(Rectangle2D.Float(keys.right - dp(2), top, dp(2), keys.letterHeight))
    g.dispose()
    ImageIO.write(image, "png", file)
}

/** KeyCalibrationActivity.edgeText, which needs Android to compile with the rest of the screen. run.sh checks it is the same. */
private object KeyCalibrationText {
    fun edge(pixels: Int) = when {
        pixels == 0 -> "on the edge"
        pixels > 0 -> "$pixels px in"
        else -> "${-pixels} px out"
    }
}

private enum class Scene { Plain, Lit, Word, Taps, Hold }

/**
 * The guide's keys as GuideKeyboardView draws them on ink, full width: thin outlines over a faint fill, the dimmed part outside
 * the touch area, and a scene's zones, lit keys or ghost path on top.
 */
private fun drawKeys(alignment: KeyAlignment, scene: Scene, file: File) {
    val keys = KeyGeometry(WIDTH.toFloat(), alignment)
    val height = Math.ceil(keys.height.toDouble()).toInt()
    val image = BufferedImage(WIDTH, height + 2 * dp(24).toInt(), BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = rgb(0xFF160A04)
    g.fillRect(0, 0, image.width, image.height)
    // A band above and below, so the drawing's own top and bottom show.
    g.translate(0.0, dp(24).toDouble())
    val radius = dp(6) * 2
    val stroke = dp(1)
    val lit = when (scene) {
        Scene.Lit -> setOf("H", "E", Layout.SPACE)
        else -> emptySet()
    }
    for (box in keys.boxes) {
        val on = box.name in lit
        val shape = RoundRectangle2D.Float(box.left + stroke / 2, box.top + stroke / 2, box.width - stroke, box.height - stroke, radius, radius)
        g.color = if (on) rgb(0xFFEF804E) else faded(0xFFFFF1E6, .04f)
        g.fill(shape)
        g.stroke = BasicStroke(stroke)
        g.color = if (on) rgb(0xFFEF804E) else faded(0xFFFFF1E6, .4f)
        g.draw(shape)
        val label = if (on) rgb(0xFF160A04) else faded(0xFFFFF1E6, .85f)
        g.color = label
        val text = when (box.name) {
            Layout.SPACE -> "␣"
            else -> box.name
        }
        val size = min(box.width, box.height) * (if (Layout.isLetter(box.name) || text.length == 1) .52f else .36f)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(size)
        val metrics = g.fontMetrics
        g.drawString(text, box.centreX - metrics.stringWidth(text) / 2f, box.centreY + (metrics.ascent - metrics.descent) / 2f)
    }
    // Outside the touch area, the keys are dimmed.
    val area = keys.area(AREA_LEFT, AREA_TOP, AREA_RIGHT, AREA_BOTTOM)
    val outside = java.awt.geom.Area(Rectangle2D.Float(0f, 0f, WIDTH.toFloat(), height.toFloat()))
    outside.subtract(java.awt.geom.Area(RoundRectangle2D.Float(area.left, area.top, area.width, area.height, dp(12) * 2, dp(12) * 2)))
    g.color = faded(0xFF160A04, .5f)
    g.fill(outside)
    val corner = dp(6) * 2
    when (scene) {
        Scene.Taps -> {
            val zone = keys.area(ZONE_LEFT, ZONE_TOP, ZONE_RIGHT, ZONE_BOTTOM)
            g.color = faded(0xFFEF804E, .2f)
            g.fill(RoundRectangle2D.Float(zone.left, zone.top, zone.width, zone.height, corner, corner))
            g.color = faded(0xFFEF804E, .6f)
            g.stroke = BasicStroke(dp(2.5f))
            val x = keys.x(.5f)
            val y = keys.y(ZONE_TAP_ROW)
            g.draw(Ellipse2D.Float(x - dp(14), y - dp(14), dp(28), dp(28)))
            g.color = rgb(0xFFFFF1E6)
            g.fill(Ellipse2D.Float(x - dp(7), y - dp(7), dp(14), dp(14)))
        }
        Scene.Hold -> {
            val fast = keys.onRow(1, AREA_LEFT, HOLD_TOP_X)
            g.color = faded(0xFFFFF1E6, .1f)
            g.fill(RoundRectangle2D.Float(fast.left, fast.top, fast.width, fast.height, corner, corner))
            val pause = keys.onRow(1, HOLD_PAUSE_X, AREA_RIGHT)
            g.color = faded(0xFFFFF1E6, .2f)
            g.fill(RoundRectangle2D.Float(pause.left, pause.top, pause.width, pause.height, corner, corner))
            g.color = faded(0xFFFFF1E6, .85f)
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(min(keys.key, keys.keyHeight) * .24f)
            g.drawString("pause", pause.centreX - g.fontMetrics.stringWidth("pause") / 2f, pause.bottom - dp(3))
        }
        Scene.Word -> {
            // The ghost's path for "hello", from letter middle to letter middle.
            val path = Path2D.Float()
            for ((index, letter) in "hello".withIndex()) {
                val (x, y) = Layout.centre(letter)
                if (index == 0) path.moveTo(keys.x(x), keys.y(y)) else path.lineTo(keys.x(x), keys.y(y))
            }
            g.color = rgb(0xFFEF804E)
            g.stroke = BasicStroke(dp(4), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(path)
            val (x, y) = Layout.centre('o')
            g.color = rgb(0xFFFFF1E6)
            g.fill(Ellipse2D.Float(keys.x(x) - dp(7), keys.y(y) - dp(7), dp(14), dp(14)))
        }
        else -> Unit
    }
    g.dispose()
    ImageIO.write(image, "png", file)
}
