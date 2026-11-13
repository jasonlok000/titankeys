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
    columnsStay()
    drawnAtSixty()
    touchpadOnKeys()
    edgesMoveEverything()
    zonesOnKeys()
    slidersRoundTrip()
    System.getenv("CALIBRATION_PNG")?.let { folder ->
        File(folder).mkdirs()
        drawCalibration(KeyAlignment.DEFAULT, File(folder, "calibration-default.png"))
        drawCalibration(KeyAlignment(8f / WIDTH, 83f / WIDTH, 26f / WIDTH), File(folder, "calibration-moved.png"))
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

/**
 * At 1076 px with the defaults, the keys are the owner's calibration and shape: a column every 108 px, centred, square keys with
 * no gap between keys, and rows apart by the hardware's ratio, 10.5 px for a 107.5 px column (owner, 2026-11-12 and 2026-11-13).
 */
private fun measuredDefaults() {
    check(KeyAlignment.current == KeyAlignment.DEFAULT) { "nothing is calibrated until someone calibrates" }
    near(KeyAlignment.DEFAULT.centre, .5f, "the defaults are centred")
    val keys = KeyGeometry(1076f, KeyAlignment.DEFAULT)
    val q = keys.boxes.first { it.name == "Q" }
    val p = keys.boxes.first { it.name == "P" }
    near(keys.pitch, 108f, "the owner's spacing")
    near(q.left, 1076f - p.right, "as far in from each side")
    // The real keyboard is a touch wider than the screen: the outer keys reach 2 px past each edge (owner's calibration, 2026-11-13).
    near(q.left, -2f, "Q reaches 2 px past the left edge")
    near(keys.gap, 0f, "no gap between keys")
    near(keys.key, 108f, "a key takes its whole column")
    near(keys.keyHeight, keys.key, "the keys are square")
    near(keys.rowPitch - keys.keyHeight, 108f * 10.5f / 107.5f, "the hardware's row gap, at this spacing")
    near(keys.right - keys.left, 1080f, "ten columns")
    // The spacing grows from the centre, and the shift moves the centre.
    val wider = KeyAlignment.DEFAULT.spaced(KeyAlignment.DEFAULT.key + KeyAlignment.DEFAULT.gap + .01f)
    near(wider.centre, .5f, "spacing keeps the row centred")
    near(wider.gap, KeyAlignment.DEFAULT.gap, "spacing keeps the gap")
    near(KeyAlignment.DEFAULT.centredAt(.52f).centre, .52f, "shift moves the centre")
    near(KeyAlignment.DEFAULT.centredAt(.52f).key, KeyAlignment.DEFAULT.key, "shift keeps the keys")
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
    // The bottom row spans the same edges, with no gaps either, and its keys keep Layout's widths.
    val bottom = keys.boxes.filter { it.row == 3 }
    same(bottom.map { it.name }, Layout.rows[3], "the bottom row's keys")
    near(bottom.first().left, q.left, "the bottom row starts under Q")
    near(bottom.last().right, p.right, "and ends under P")
    for (index in 1 until bottom.size) near(bottom[index].left - bottom[index - 1].right, keys.gap, "the bottom row's gap $index")
    near(bottom[3].width / bottom[0].width, 2.8f / 1.2f, "Space is as much wider as Layout says")
    near(bottom.last().bottom, keys.height, "the bottom row ends the drawing")
    near(KeyGeometry.heightFor(1076f, KeyAlignment.DEFAULT), keys.height, "heightFor")
    // Each column of the menu and the Sym panel runs from the middle of one gap to the next, and holds its key in the middle.
    for (column in 0..9) {
        val (left, right) = KeyAlignment.column(1076f, column)
        near(right - left, keys.pitch, "column $column's width")
        near((left + right) / 2, keys.boxes[column].centreX, "column $column's middle")
    }
}

/**
 * The keys losing their gap moves no column and no key's middle (owner: never change touchpad or input code for this): each
 * column still runs from the middle of the calibration's gap on its left to the middle of the one on its right, and each key's
 * middle is still the calibration's key's middle, so the menu's buttons, the Sym panel and the touchpad stay where they were.
 */
private fun columnsStay() {
    for (alignment in listOf(KeyAlignment.DEFAULT, KeyAlignment(.04f, .93f), KeyAlignment(6f / 1080f, 83f / 1080f, 25.5f / 1080f))) {
        val width = 1080f
        val calibratedLeft = alignment.left * width
        val calibratedKey = alignment.key * width
        val calibratedGap = alignment.gap * width
        val keys = KeyGeometry(width, alignment)
        for (column in 0..9) {
            val start = calibratedLeft + column * (calibratedKey + calibratedGap)
            val (left, right) = keys.cell(column)
            near(left, start - calibratedGap / 2, "column $column starts half a gap left of its calibrated key")
            near(right, start + calibratedKey + calibratedGap / 2, "and ends half a gap right of it")
            near(keys.boxes[column].centreX, start + calibratedKey / 2, "key $column's middle is the calibrated key's")
            near(keys.x((column + .5f) / 10), start + calibratedKey / 2, "the touchpad's column $column lands there")
        }
    }
}

/**
 * The guide's and the playground's drawing (owner, 2026-11-12): the same width and columns as the real keys, and 60% of their
 * height, every row and the space between rows alike.
 */
private fun drawnAtSixty() {
    same(KeyGeometry.DRAWN, .6f, "60% of the real height")
    val real = KeyGeometry(1076f, KeyAlignment.DEFAULT)
    val drawn = KeyGeometry(1076f, KeyAlignment.DEFAULT, KeyGeometry.DRAWN)
    near(drawn.height, real.height * .6f, "the whole drawing")
    near(drawn.keyHeight, real.keyHeight * .6f, "a key")
    near(drawn.rowPitch - drawn.keyHeight, (real.rowPitch - real.keyHeight) * .6f, "between rows")
    near(KeyGeometry.heightFor(1076f, KeyAlignment.DEFAULT, KeyGeometry.DRAWN), drawn.height, "heightFor at 60%")
    for ((index, box) in drawn.boxes.withIndex()) {
        near(box.left, real.boxes[index].left, "${box.name} keeps its left")
        near(box.right, real.boxes[index].right, "${box.name} keeps its right")
    }
    // Edge to edge, on the calibration: at 108 px the outer keys reach 2 px past each side, as the real ones do (owner, 2026-11-13).
    check(drawn.left > -3f && drawn.left <= 0f && drawn.right >= 1076f && drawn.right < 1079f) { "edge to edge: ${drawn.left} to ${drawn.right}" }
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
    // Keys spanning the whole width, and the same with Q's left edge moved in.
    val base = KeyGeometry(1080f, KeyAlignment(0f, 1f))
    val moved = KeyGeometry(1080f, KeyAlignment(.05f, 1f))
    val half = KeyAlignment(.05f, 1f).gap * 1080f / 2
    near(moved.boxes.first().left, 54f - half, "Q's column follows the left edge, half a gap out")
    near(moved.boxes[9].right, 1080f + half, "P's stays, half a gap out")
    for (index in 1 until 10) check(moved.boxes[index].left > base.boxes[index].left) { "column $index moves right" }
    check(abs(moved.boxes.last().right - moved.boxes[9].right) < 1e-2f && moved.boxes[30].left > base.boxes[30].left) { "the bottom row follows" }
    check(moved.x(.5f) > base.x(.5f)) { "the touchpad follows" }
    near(moved.key / base.key, .95f, "a narrower span makes smaller keys")
    near(moved.height / base.height, .95f, "and a shorter keyboard")
    val right = KeyGeometry(1080f, KeyAlignment(0f, .9f))
    val rightHalf = KeyAlignment(0f, .9f).gap * 1080f / 2
    near(right.boxes[9].right, 972f + rightHalf, "P's column follows the right edge")
    near(right.boxes[0].left, -rightHalf, "Q's stays")
    val defaults = KeyGeometry(1080f, KeyAlignment.DEFAULT)
    near(KeyAlignment.column(1080f, 0).first, defaults.left, "with the defaults, Q's column is Q")
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
        near(pause.top, box(Layout.BACKSPACE).top, "the pause box is on the Backspace row")
        near(pause.bottom, box(Layout.BACKSPACE).bottom, "from its top to its bottom")
        check(pause.left > box("L").left && pause.left < box("L").right) { "it starts on L" }
        near(pause.right, box(Layout.BACKSPACE).centreX, "and ends at Backspace's middle, where the touchpad stops")
        val fast = keys.onRow(1, AREA_LEFT, HOLD_TOP_X)
        near(fast.left, box("A").centreX, "top speed starts at A's middle")
        near(fast.right, box("F").right, "and reaches the line between F and G")
        near(box("F").right, box("G").left, "where F meets G")
    }
}

/** The calibration by gaps (owner, 2026-11-12): a key's width, a gap's and the shift, each in half pixels either side of measured. */
private fun slidersRoundTrip() {
    val wide = WIDTH.toFloat()
    val measured = KeyAlignment.DEFAULT
    same(measured, KeyAlignment(KeyAlignment.DEFAULT_LEFT, KeyAlignment.DEFAULT_RIGHT), "measured is the owner's calibration")
    near(measured.left + measured.right, 1f, "centred on the screen")
    for ((default, reach) in listOf(measured.key * wide to KeyAlignment.KEY_REACH, measured.gap * wide to KeyAlignment.GAP_REACH,
        measured.left * wide to KeyAlignment.SHIFT_REACH)) {
        same(KeyAlignment.progressOf(default, reach, default), KeyAlignment.steps(reach) / 2, "a slider starts in the middle")
        for (progress in 0..KeyAlignment.steps(reach))
            same(KeyAlignment.progressOf(default, reach, KeyAlignment.pixelsAt(default, reach, progress)), progress, "step $progress")
        near(KeyAlignment.pixelsAt(default, reach, 1) - KeyAlignment.pixelsAt(default, reach, 0), KeyAlignment.STEP, "half a pixel a step")
    }
    // Columns are offset + i × (key + gap), every key the same width; the face's keys fill them, with no gap.
    val tuned = KeyAlignment(6f / wide, 83f / wide, 25.5f / wide)
    val keys = KeyGeometry(wide, tuned)
    for (column in 0 until 10) near(keys.keyLeft(column), 6f - 25.5f / 2 + column * (83f + 25.5f), "column $column follows the three")
    near(tuned.right * wide, 6f + 10 * 83f + 9 * 25.5f, "P's calibrated right edge follows")
    near(keys.right, tuned.right * wide + 25.5f / 2, "and the face's right edge is half a gap past it")
    same(KeyAlignment.pixelText(measured.gap * wide, measured.gap * wide).endsWith("measured"), true, "the measured gap says so")
    same(KeyAlignment.pixelText(25.5f, 24.5f), "25.5 px, +1", "a moved value says by how much")
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
 * The calibration screen as KeyCalibrationActivity lays it out (owner, 2026-11-12): the words, two sliders (spacing and shift)
 * with − and +, Reset to measured, and at the very bottom one-pixel orange lines at each gap's centre and at the outer edges, rising
 * from the screen's bottom edge, with the columns' letters. The desktop's fonts are close, not exact.
 */
private fun drawCalibration(alignment: KeyAlignment, file: File) {
    val keys = KeyGeometry(WIDTH.toFloat(), alignment)
    val bands = dp(72)
    val height = (dp(620) + bands).toInt()
    val image = BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = rgb(0xFF000000)
    g.fillRect(0, 0, WIDTH, height)
    val pad = dp(16)
    var y = dp(28) + pad
    g.color = rgb(0xFFFFF1E6)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(24).toInt())
    y += dp(28)
    g.drawString("Key calibration", pad, y)
    g.color = rgb(0xFF99918A)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, dp(15).toInt())
    for (line in listOf("Put each thin orange line at the bottom in the middle", "of a dark gap between the real keys below. Set the",
        "spacing first, then shift the row. Every key is the", "same width. It saves as you go.")) {
        y += dp(22)
        g.drawString(line, pad, y)
    }
    y += dp(16)
    val sliderHeight = dp(84)
    g.color = rgb(0xFF141312)
    g.fill(RoundRectangle2D.Float(pad, y, WIDTH - 2 * pad, 2 * sliderHeight + dp(20), dp(24), dp(24)))
    y += dp(12)
    val wide = WIDTH.toFloat()
    val measured = KeyAlignment.DEFAULT
    val sliders = listOf(
        Triple("Spacing", (alignment.key + alignment.gap) * wide to (measured.key + measured.gap) * wide, KeyAlignment.KEY_REACH),
        Triple("Shift", alignment.left * wide to measured.left * wide, KeyAlignment.SHIFT_REACH),
    )
    for ((name, values, reach) in sliders) {
        val (value, default) = values
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, dp(15).toInt())
        g.color = rgb(0xFFFFF1E6)
        g.drawString(name, pad + dp(16), y + dp(18))
        g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(15).toInt())
        g.color = rgb(0xFFEF804E)
        val text = KeyAlignment.pixelText(value, default)
        g.drawString(text, WIDTH - pad - dp(16) - g.fontMetrics.stringWidth(text), y + dp(18))
        // − and +, round, either side of the track.
        val rowY = y + dp(50)
        g.stroke = BasicStroke(dp(1.2f))
        g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(20).toInt())
        for ((x, mark) in listOf(pad + dp(16) to "−", WIDTH - pad - dp(16) - dp(44) to "+")) {
            g.draw(Ellipse2D.Float(x, rowY - dp(22), dp(44), dp(44)))
            g.drawString(mark, x + dp(22) - g.fontMetrics.stringWidth(mark) / 2f, rowY + dp(7))
        }
        val trackLeft = pad + dp(16) + dp(44) + dp(14)
        val trackRight = WIDTH - pad - dp(16) - dp(44) - dp(14)
        g.color = rgb(0xFF2A2623)
        g.fill(RoundRectangle2D.Float(trackLeft, rowY - dp(2), trackRight - trackLeft, dp(4), dp(4), dp(4)))
        val progress = KeyAlignment.progressOf(default, reach, value)
        val thumb = trackLeft + (trackRight - trackLeft) * progress / KeyAlignment.steps(reach)
        g.color = rgb(0xFFEF804E)
        g.fill(RoundRectangle2D.Float(trackLeft, rowY - dp(2), thumb - trackLeft, dp(4), dp(4), dp(4)))
        g.fill(Ellipse2D.Float(thumb - dp(8), rowY - dp(8), dp(16), dp(16)))
        y += sliderHeight
    }
    y += dp(32)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(15).toInt())
    val reset = "Reset to measured"
    val resetWidth = g.fontMetrics.stringWidth(reset) + dp(40)
    g.color = rgb(0xFFEF804E)
    g.fill(RoundRectangle2D.Float(pad, y, resetWidth, dp(48), dp(48), dp(48)))
    g.color = rgb(0xFF160A04)
    g.drawString(reset, pad + dp(20), y + dp(30))
    // The lines, at the very bottom, as KeyGrid draws them: the outside of the edges dimmed, then one pixel at each gap's centre
    // and at each outer edge, snapped to whole pixels, then the letters.
    val top = height - bands
    val left = alignment.left * WIDTH
    val right = alignment.right * WIDTH
    g.color = rgb(0x33EF804E)
    g.fill(Rectangle2D.Float(0f, top, left, bands))
    g.fill(Rectangle2D.Float(right, top, WIDTH - right, bands))
    g.color = rgb(0xFFEF804E)
    val lines = (0 until 9).map { keys.cell(it).second } + listOf(left, right)
    for (x in lines) g.fillRect(Math.round(x - 0.5f), top.toInt(), 1, bands.toInt())
    g.color = rgb(0x80FFF1E6)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, dp(12).toInt())
    for ((column, name) in Layout.rows[0].withIndex()) {
        val centre = keys.keyLeft(column) + keys.key / 2
        g.drawString(name, centre - g.fontMetrics.stringWidth(name) / 2f, top + dp(6) + g.fontMetrics.ascent)
    }
    g.dispose()
    ImageIO.write(image, "png", file)
}

private enum class Scene { Plain, Lit, Word, Taps, Hold }

/**
 * The guide's keys as GuideKeyboardView draws them on black (design v2): full width, 60% of the real height, each key 3% of its
 * width in from its sides, a cream outline at 16% over the surface at 50%, labels cream at 70%, the dimmed part outside the touch
 * area, and a scene's zones, lit keys or ghost path on top.
 */
private fun drawKeys(alignment: KeyAlignment, scene: Scene, file: File) {
    val keys = KeyGeometry(WIDTH.toFloat(), alignment, KeyGeometry.DRAWN)
    val height = Math.ceil(keys.height.toDouble()).toInt()
    val image = BufferedImage(WIDTH, height + 2 * dp(24).toInt(), BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = rgb(0xFF000000)
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
        val side = min(box.width, keys.pitch) * .03f
        val shape = RoundRectangle2D.Float(box.left + side + stroke / 2, box.top + stroke / 2, box.width - 2 * side - stroke, box.height - stroke,
            radius, radius)
        g.color = if (on) rgb(0xFFEF804E) else faded(0xFF161210, .5f)
        g.fill(shape)
        g.stroke = BasicStroke(stroke)
        g.color = if (on) rgb(0xFFEF804E) else faded(0xFFFFF1E6, .16f)
        g.draw(shape)
        val label = if (on) rgb(0xFF160A04) else faded(0xFFFFF1E6, .7f)
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
