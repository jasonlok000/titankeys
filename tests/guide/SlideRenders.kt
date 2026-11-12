package com.atlas.keyboard

import com.atlas.keyboard.KeyboardEvent.CursorStarted
import com.atlas.keyboard.KeyboardEvent.DeletedWord
import com.atlas.keyboard.KeyboardEvent.DoubleTapped
import com.atlas.keyboard.KeyboardEvent.EmptyKey
import com.atlas.keyboard.KeyboardEvent.FieldChanged
import com.atlas.keyboard.KeyboardEvent.HeldSymbol
import com.atlas.keyboard.KeyboardEvent.ModifierState
import com.atlas.keyboard.KeyboardEvent.Pushed
import com.atlas.keyboard.KeyboardEvent.Released
import com.atlas.keyboard.KeyboardEvent.TwoTapsAbove
import com.atlas.keyboard.Modifiers.Level
import com.atlas.keyboard.Modifiers.Panel
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
 * Desktop drawings of the guide's slides, to look at without a phone: each slide at its start, with a chip for a real mistake or
 * its next instruction, and passed. They follow the slide's real words, chips and field, from the guide's own steps, laid out as
 * the guide lays them out (one top bar, a one-line title, a one-line instruction or chip, a two-line field with its ghost), at the
 * Titan 2 Elite's size. The fonts are the desktop's, not the phone's, so widths are close, not exact. Set SLIDES_PNG to a folder.
 */

/** One state of a slide, as the screen shows it. */
private data class Frame(
    val name: String,
    val chapter: GuideChapter,
    val title: String,
    val line: String,
    val chip: String? = null,
    val passed: Boolean = false,
    val field: String = "",
    val start: Int = field.length,
    val end: Int = start,
    val ghost: String = "",
    val panel: String? = null,
    /** What the drawing of the keys shows on some keys, in place of their letters. See [GuideStage.labels]. */
    val labels: Map<String, String>? = null,
    /** What the field marks in orange to aim at. See [GuideMemory.target]. */
    val target: IntRange? = null,
)

/** A mistake for each slide, as the keyboard reports it, so a drawing shows the chip that it gets. */
private val MISTAKES: Map<String, List<KeyboardEvent>> = mapOf(
    "type" to listOf(Pushed(62)),
    "flick" to listOf(KeyboardEvent.DeleteHoldStarted),
    "hold" to listOf(KeyboardEvent.DeleteHoldStarted, DeletedWord(held = true), KeyboardEvent.Moved(40f, lifted = true)),
    "steer" to listOf(DoubleTapped(2)),
    "selectmore" to listOf(CursorStarted(false)),
    "alt" to listOf(ModifierState(Level.Off, Level.Once, Panel.Closed, false), FieldChanged("#", 1, 1)),
    "holdsym" to listOf(HeldSymbol("#")),
    "symmain" to listOf(ModifierState(Level.Off, Level.Off, Panel.Symbols, false), EmptyKey),
    "symgroups" to listOf(FieldChanged("&", 1, 1)),
    "clown" to listOf(ModifierState(Level.Off, Level.Off, Panel.Emoji, false), FieldChanged("😀", 2, 2)),
    "paste" to listOf(KeyboardEvent.Copied("x")),
    "clipboard" to listOf(ModifierState(Level.Off, Level.Off, Panel.Symbols, false), ModifierState(Level.Off, Level.Off, Panel.Closed, false)),
)

/** What the field holds on a slide, before anything is done: its text, the cursor or selection, and the ghost after it. */
private fun fieldFor(step: GuideStep): Frame {
    fun at(text: String, start: Int = text.length, end: Int = start, ghost: String = "") =
        Frame("", step.chapter, "", "", field = text, start = start, end = end, ghost = ghost)
    return when (step.chapter) {
        GuideChapter.Typing -> at("", ghost = step.ghost.ifEmpty { TYPING_TEXT })
        GuideChapter.Delete -> if (step.id == "backspace") (DELETE_STRAY_TEXT + " ").let { at(it, strayCursor(it)).copy(target = strayTarget(it)) }
            else (DELETE_TEXT + " ").let { at(it).copy(target = if (step.id == "flick") lastWordTarget(it, it.length) else null) }
        GuideChapter.Steering -> {
            val text = looping(STEER_TEXT) + " "
            val cursor = text.indexOf(' ', text.length / 2) + 1
            at(text, cursor)
        }
        GuideChapter.Symbols -> {
            val index = SYMBOL_PARTS.indexOfFirst { it.first == step.symbol }
            val typed = SYMBOL_PARTS.take(index).joinToString("") { it.first + it.second }
            at(typed, ghost = SYMBOL_PARTS.drop(index).joinToString("") { it.first + it.second }.trimEnd())
        }
        GuideChapter.Clipboard -> {
            val text = CLIP_TEXT + " "
            val end = CLIP_TEXT.length
            val over = CLIP_TEXT.indexOf(PASTE_OVER)
            if (step.preselect) at(text, over, over + PASTE_OVER.length) else at(text, end, end)
        }
        GuideChapter.End -> at(END_TEXT)
    }
}

/** The field once the slide is passed. */
private fun passedField(step: GuideStep, before: Frame): Frame {
    fun at(text: String, start: Int = text.length, end: Int = start, ghost: String = "") =
        Frame("", step.chapter, "", "", field = text, start = start, end = end, ghost = ghost)
    return when (step.id) {
        "type" -> at("This keyboard fixes ", ghost = "your typos, so you can keep pretending you never make any.")
        "backspace" -> (DELETE_TEXT + " ").let { at(it, it.lastIndexOf(STRAY_FIXED) + STRAY_FIXED.length - 1) }
        "flick", "hold" -> at(DELETE_TEXT.substringBeforeLast(" It knows") + " ")
        "steer" -> at(before.field, before.start - 40)
        "selectmore" -> {
            val from = before.start
            val to = before.field.indexOf(' ', from + if (step.id == "selectmore") 20 else 1)
            at(before.field, from, to)
        }
        "paste" -> CLIP_TEXT.replace(PASTE_OVER, PASTE_TEXT).let { at(it + " ", CLIP_TEXT.indexOf(PASTE_OVER) + PASTE_TEXT.length) }
        "clipboard" -> at(CLIP_TEXT + " " + GUIDE_CLIPS.first() + " ")
        else -> if (step.chapter == GuideChapter.Symbols) {
            val index = SYMBOL_PARTS.indexOfFirst { it.first == step.symbol }
            val picked = if (step.symbol == ANY_SYMBOL) "※" else step.symbol!!
            val typed = SYMBOL_PARTS.take(index).joinToString("") { it.first + it.second } + picked + SYMBOL_PARTS[index].second
            at(typed, ghost = SYMBOL_PARTS.drop(index + 1).joinToString("") { it.first + it.second }.trimEnd())
        } else before
    }
}

private fun framesFor(step: GuideStep): List<Frame> {
    val field = fieldFor(step)
    fun frame(name: String, title: String, line: String, chip: String? = null, passed: Boolean = false, from: Frame = field,
        panel: String? = null) = Frame("${step.id}-$name", step.chapter, title, line, chip, passed, from.field, from.start, from.end,
        from.ghost, panel, target = from.target)
    val frames = ArrayList<Frame>()
    val first = step.stages.firstOrNull()?.text ?: step.body
    frames += frame("1-start", step.title, first)
    // Each later instruction, as the slide moves on to it.
    for ((index, stage) in step.stages.withIndex().drop(1)) {
        val panel = if (step.usesPanel && index > 0 && step.id != "alt") "Sym panel open" else null
        frames += frame("2-stage${index + 1}", step.title, stage.text, panel = panel).copy(labels = stage.labels)
    }
    // A real mistake: its chip over the instruction.
    MISTAKES[step.id]?.let { events ->
        val flow = GuideFlow({ GuideSettings(holdSymbols = true, fnAsCtrl = true) })
        flow.start(step.id)
        flow.seed(field.field, field.start)
        flow.aim(field.target)
        var said: String? = null
        for (event in events) {
            val verdict = flow.event(event)
            if (verdict is GuideVerdict.Hint && !verdict.passes) said = verdict.title ?: verdict.text
        }
        if (said != null) frames += frame("3-chip", step.title, flow.instruction, chip = said)
    }
    // Mass deletion counts words in the instruction's place from the first one: one gone, then passed at eight, then frozen.
    if (step.id == "hold") {
        val one = field.copy(field = DELETE_TEXT.substringBeforeLast(" did.") + " ", start = DELETE_TEXT.substringBeforeLast(" did.").length + 1)
        frames += frame("2-count1", step.title, regretsLine(1), from = one)
        val eight = passedField(step, field)
        frames += frame("4-passed", step.done, regretsLine(REGRETS_TO_PASS), passed = true, from = eight)
        frames += frame("5-frozen", step.done, regretsFinal(11), passed = true, from = eight)
        return frames
    }
    frames += frame("4-passed", step.done, step.praise, passed = true, from = passedField(step, field))
    return frames
}

/** The guide's colours, as GuideColours has them (it isn't compiled for these checks). */
private object Paint {
    const val ORANGE = 0xFFEF804E.toInt()
    const val INK = 0xFF160A04.toInt()
    const val CREAM = 0xFFFFF1E6.toInt()
}

/** The size of the owner's phone screenshots (UAT c1-01.png), whose 18 dp gutter is 28 px. */
private const val WIDTH = 1076
private const val HEIGHT = 1200
private const val DP = 1.56f

/**
 * A font sized so that [sample] is [pixels] wide, as measured on the phone's own screenshot: the desktop's fonts are wider than
 * the phone's, so sizes are set by width, which is what a one-line check needs.
 */
private fun calibrated(g: Graphics2D, style: Int, sample: String, pixels: Int): Font {
    var size = 10f
    while (g.getFontMetrics(Font(Font.SANS_SERIF, style, 1).deriveFont(size + .5f)).stringWidth(sample) <= pixels) size += .5f
    return Font(Font.SANS_SERIF, style, 1).deriveFont(size)
}
private fun dp(value: Number) = Math.round(value.toFloat() * DP)

private fun rgb(argb: Int, alpha: Float = 1f) = Color((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, (alpha * 255).toInt())
private val ink = rgb(Paint.INK)
private val cream = rgb(Paint.CREAM)
private val orange = rgb(Paint.ORANGE)

private fun draw(frame: Frame, file: File) {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = ink
    g.fillRect(0, 0, WIDTH, HEIGHT)
    val side = dp(Layout.KEY_SIDE_DP)
    val inner = WIDTH - 2 * side
    var y = dp(40)
    // The top bar: the chapters' segments, the chapter's name, Skip or Next, and ✕.
    val bar = dp(32)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(13))
    val label = frame.chapter.label.uppercase()
    val pillText = if (frame.passed) "Next" else "Skip"
    val pillWidth = g.fontMetrics.stringWidth(pillText) + dp(20)
    val closeWidth = dp(32)
    val labelWidth = g.fontMetrics.stringWidth(label)
    val segmentsEnd = WIDTH - side - closeWidth - dp(4) - pillWidth - dp(10) - labelWidth - dp(10)
    val chapters = GuideChapter.entries.filter { it != GuideChapter.End }
    val segment = (segmentsEnd - side) / chapters.size
    val current = chapters.indexOf(frame.chapter)
    for ((index, _) in chapters.withIndex()) {
        val x = side + index * segment
        g.color = rgb(Paint.CREAM, .25f)
        g.fill(RoundRectangle2D.Float(x.toFloat(), y + bar / 2f - dp(2), segment - dp(3f).toFloat(), dp(4).toFloat(), dp(4).toFloat(), dp(4).toFloat()))
        val fill = when {
            index < current -> 1f
            index == current -> if (frame.passed) .6f else .4f
            else -> 0f
        }
        if (fill > 0f) {
            g.color = cream
            g.fill(RoundRectangle2D.Float(x.toFloat(), y + bar / 2f - dp(2), (segment - dp(3)) * fill, dp(4).toFloat(), dp(4).toFloat(), dp(4).toFloat()))
        }
    }
    g.color = rgb(Paint.CREAM, .7f)
    var x = segmentsEnd + dp(10)
    g.drawString(label, x, y + bar / 2 + g.fontMetrics.ascent / 2 - dp(1))
    x += labelWidth + dp(10)
    val pill = RoundRectangle2D.Float(x.toFloat(), y + dp(3).toFloat(), pillWidth.toFloat(), bar - dp(6).toFloat(), bar.toFloat(), bar.toFloat())
    if (frame.passed) {
        g.color = orange
        g.fill(pill)
        g.color = ink
    } else {
        g.color = rgb(Paint.CREAM, .6f)
        g.stroke = BasicStroke(dp(1).toFloat())
        g.draw(pill)
    }
    g.drawString(pillText, x + dp(10), y + bar / 2 + g.fontMetrics.ascent / 2 - dp(1))
    x += pillWidth + dp(4)
    g.color = cream
    g.stroke = BasicStroke(dp(1.6f).toFloat())
    val c = dp(6)
    g.drawLine(x + bar / 2 - c, y + bar / 2 - c, x + bar / 2 + c, y + bar / 2 + c)
    g.drawLine(x + bar / 2 + c, y + bar / 2 - c, x + bar / 2 - c, y + bar / 2 + c)
    y += bar + dp(10)
    // The title: one line at one size, with the orange check once passed.
    val titleFont = calibrated(g, Font.BOLD, "Type like a normal person.", 540)
    g.font = titleFont
    val titleLine = dp(36)
    g.color = cream
    val titleShown = fit(g, frame.title, inner)
    g.drawString(titleShown, side, y + titleLine - dp(9))
    if (frame.passed) {
        val at = side + g.fontMetrics.stringWidth(titleShown + " ")
        val size = titleFont.size2D * .5f
        val base = (y + titleLine - dp(9)).toFloat()
        g.color = orange
        g.stroke = BasicStroke(dp(2.8f).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(java.awt.geom.Path2D.Float().apply {
            moveTo(at.toFloat(), base - size * .45f)
            lineTo(at + size * .36f, base - size * .08f)
            lineTo(at + size, base - size * .86f)
        })
    }
    if (g.fontMetrics.stringWidth(frame.title) + (if (frame.passed) dp(20) else 0) > inner) mark(g, "TITLE TOO LONG", y)
    y += titleLine + dp(10)
    // The instruction, or a chip over it.
    val bodyFont = calibrated(g, Font.PLAIN, "Back for a refresher? Pick a chapter. No judgement. Some judgement.", 875)
    val lineHeight = dp(22) + 2 * dp(3)
    g.font = bodyFont
    if (frame.chip != null) {
        g.font = calibrated(g, Font.PLAIN, "Oh, look who's back. Didn't know everything after all?", 672)
        val shown = fit(g, frame.chip, inner - dp(24))
        val width = g.fontMetrics.stringWidth(shown) + dp(24)
        g.color = cream
        g.fill(RoundRectangle2D.Float(side.toFloat(), y.toFloat(), width.toFloat(), lineHeight.toFloat(), lineHeight.toFloat(), lineHeight.toFloat()))
        g.color = ink
        g.drawString(shown, side + dp(12), y + lineHeight / 2 + g.fontMetrics.ascent / 2 - dp(2))
        if (g.fontMetrics.stringWidth(frame.chip) + dp(24) > inner) mark(g, "CHIP TOO LONG", y)
    } else {
        g.color = rgb(Paint.CREAM, .82f)
        val shown = fit(g, frame.line, inner)
        g.drawString(shown, side, y + lineHeight / 2 + g.fontMetrics.ascent / 2 - dp(2))
        if (g.fontMetrics.stringWidth(frame.line) > inner) mark(g, "LINE TOO LONG", y)
    }
    y += lineHeight + dp(10)
    // The field: exactly two lines, on ink, with what's typed in cream and the ghost after it in grey.
    val fieldFont = calibrated(g, Font.PLAIN, "Tihs keybaord fxies yuor typos, sp yuo cna kepe", 807)
    g.font = fieldFont
    val pad = dp(12)
    val fieldLine = g.fontMetrics.height
    val fieldHeight = 2 * fieldLine + 2 * pad
    g.color = blend(Paint.INK, Paint.CREAM, .08f)
    g.fill(RoundRectangle2D.Float(side.toFloat(), y.toFloat(), inner.toFloat(), fieldHeight.toFloat(), dp(12).toFloat(), dp(12).toFloat()))
    drawField(g, frame, side + pad, y + pad, inner - 2 * pad, fieldLine)
    y += fieldHeight
    // The keys, or the panel, at the bottom.
    val keysTop = HEIGHT - dp(4 * 50 + 70)
    g.color = rgb(Paint.CREAM, .35f)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, dp(12))
    g.drawString("(free room)", side, (y + keysTop) / 2)
    drawKeys(g, keysTop, side, frame.panel, frame.labels)
    g.dispose()
    ImageIO.write(image, "png", file)
}

private fun blend(from: Int, to: Int, amount: Float): Color {
    val a = rgb(from)
    val b = rgb(to)
    fun mix(x: Int, y: Int) = Math.round(x + (y - x) * amount)
    return Color(mix(a.red, b.red), mix(a.green, b.green), mix(a.blue, b.blue))
}

private var drawing = ""

private fun mark(g: Graphics2D, what: String, y: Int) {
    println("  $drawing: $what")
    val font = g.font
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(10))
    g.color = Color.RED
    g.drawString(what, WIDTH - dp(120), y)
    g.font = font
}

/** [text] cut with an ellipsis to fit [width], as a one-line TextView ends it. */
private fun fit(g: Graphics2D, text: String, width: Int): String {
    if (g.fontMetrics.stringWidth(text) <= width) return text
    var end = text.length
    while (end > 0 && g.fontMetrics.stringWidth(text.substring(0, end) + "…") > width) end--
    return text.substring(0, end) + "…"
}

/** The field's text and ghost, wrapped at [width], showing the two lines with the cursor, the selection in orange. */
private fun drawField(g: Graphics2D, frame: Frame, left: Int, top: Int, width: Int, line: Int) {
    val all = frame.field + frame.ghost
    // Wrap at spaces.
    val starts = ArrayList<Int>()
    var at = 0
    while (at < all.length) {
        starts += at
        var end = at
        var lastBreak = -1
        while (end < all.length && g.fontMetrics.stringWidth(all.substring(at, end + 1)) <= width) {
            if (all[end] == ' ') lastBreak = end + 1
            end++
        }
        at = if (end >= all.length) all.length else if (lastBreak > at) lastBreak else end
    }
    if (starts.isEmpty()) starts += 0
    val cursorLine = starts.indexOfLast { it <= frame.start.coerceAtMost(all.length) }.coerceAtLeast(0)
    val first = when {
        frame.ghost.isNotEmpty() || frame.field.isEmpty() -> cursorLine
        else -> (cursorLine - 1).coerceAtLeast(0)
    }.coerceAtMost((starts.size - 2).coerceAtLeast(0))
    for (row in 0..1) {
        val index = first + row
        if (index >= starts.size) break
        val from = starts[index]
        val to = starts.getOrNull(index + 1) ?: all.length
        val base = top + row * line + g.fontMetrics.ascent
        var x = left
        for (offset in from until to) {
            val char = all[offset].toString()
            val w = g.fontMetrics.stringWidth(char)
            val aim = frame.target
            if (aim != null && offset in aim) {
                g.color = rgb(Paint.ORANGE, .35f)
                g.fillRect(x, top + row * line, w, line)
                g.color = orange
                g.fillRect(x, top + (row + 1) * line - dp(2), w, dp(2))
            }
            if (offset in frame.start until frame.end) {
                g.color = rgb(Paint.ORANGE, .45f)
                g.fillRect(x, top + row * line, w, line)
            }
            g.color = if (offset < frame.field.length) cream else rgb(Paint.CREAM, .38f)
            g.drawString(char, x, base)
            if (offset == frame.start && frame.start == frame.end) {
                g.color = orange
                g.fillRect(x - dp(1), top + row * line + dp(2), dp(2), line - dp(4))
            }
            x += w
        }
        if (frame.start == frame.end && frame.start == to && to == all.length) {
            g.color = orange
            g.fillRect(x, top + row * line + dp(2), dp(2), line - dp(4))
        }
    }
}

private fun drawKeys(g: Graphics2D, top: Int, side: Int, panel: String?, labels: Map<String, String>? = null) {
    val width = (WIDTH - 2 * side) / 10f
    val row = dp(50)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(16))
    for ((index, keys) in Layout.rows.withIndex()) {
        val y = top + index * row
        if (index < 3) for ((column, key) in keys.withIndex()) {
            val x = side + column * width
            // With labels, a labelled key shows its label, lit, and a letter without one is an empty, faint key.
            val labelled = labels?.get(key)
            val faint = labels != null && labelled == null && Layout.isLetter(key)
            g.color = if (labelled != null) orange else rgb(Paint.CREAM, if (faint) .04f else .09f)
            g.fill(RoundRectangle2D.Float(x + dp(2), y + dp(2).toFloat(), width - dp(4), row - dp(4).toFloat(), dp(8).toFloat(), dp(8).toFloat()))
            g.color = if (labelled != null) ink else rgb(Paint.CREAM, .6f)
            val font = g.font
            if (labelled != null) {
                g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(11))
                if (g.fontMetrics.stringWidth(labelled) > width - dp(6)) mark(g, "KEY LABEL TOO LONG", y)
            }
            val text = labelled ?: if (faint) "" else if (key.length == 1) key else key.take(3)
            g.drawString(text, x + width / 2 - g.fontMetrics.stringWidth(text) / 2, y + row / 2f + dp(6))
            g.font = font
        }
    }
    if (panel != null) {
        g.color = Color(0, 0, 0, 170)
        g.fillRect(0, top, WIDTH, 3 * row)
        g.color = orange
        g.drawString(panel, WIDTH / 2 - g.fontMetrics.stringWidth(panel) / 2, top + 3 * row / 2)
    }
}

/** The end's words that live in GuideActivity, which isn't compiled for these checks: the same text, copied. */
private const val BORING_LINE = "Every feature in words. For nerds, and for people who skipped half of this. So, you."
private const val SHARE_PLAIN = "If you agree, TitanKeys sends us examples of your swipes so we can make swiping better. It's off " +
    "until you agree, and you'll see exactly what's sent first."
private const val SHARE_ANSWERED = "Thanks. You can change this any time in Settings > Share swipes. Trust me bro."

/** [text] wrapped at [width], line by line. */
private fun wrap(g: Graphics2D, text: String, width: Int): List<String> {
    val lines = ArrayList<String>()
    for (paragraph in text.split("\n")) {
        var line = ""
        for (word in paragraph.split(' ')) {
            val tried = if (line.isEmpty()) word else "$line $word"
            if (g.fontMetrics.stringWidth(tried) <= width || line.isEmpty()) line = tried else {
                lines += line
                line = word
            }
        }
        lines += line
    }
    return lines
}

/**
 * The end, as it wraps: the name, the line under it and the slogan, the two big buttons with their lines, the field with the
 * end's words waiting in it, and the keys. [answered] draws it after the share offer has an answer.
 */
private fun drawEnd(file: File, answered: Boolean) {
    val end = GUIDE_STEPS.last()
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = ink
    g.fillRect(0, 0, WIDTH, HEIGHT)
    val side = dp(Layout.KEY_SIDE_DP)
    val inner = WIDTH - 2 * side
    var y = dp(40)
    val bar = dp(32)
    // The top bar at the end: the chapter's name, Again, Next as "Go type something", and ✕.
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(13))
    g.color = rgb(Paint.CREAM, .7f)
    g.drawString("Again", side, y + bar / 2 + dp(4))
    val next = "Go type something"
    val pillWidth = g.fontMetrics.stringWidth(next) + dp(20)
    val pillX = WIDTH - side - dp(36) - pillWidth
    g.color = orange
    g.fill(RoundRectangle2D.Float(pillX.toFloat(), y + dp(3).toFloat(), pillWidth.toFloat(), bar - dp(6).toFloat(), bar.toFloat(), bar.toFloat()))
    g.color = ink
    g.drawString(next, pillX + dp(10), y + bar / 2 + dp(4))
    y += bar + dp(10)
    // The name: the logo, as it lands.
    g.font = calibrated(g, Font.BOLD, "Type like a normal person.", 540).deriveFont(dp(40).toFloat())
    g.color = cream
    g.drawString(end.title, side, y + g.fontMetrics.ascent)
    y += g.fontMetrics.height
    // The line under it and the slogan, wrapped.
    g.font = calibrated(g, Font.PLAIN, "Back for a refresher? Pick a chapter. No judgement. Some judgement.", 875)
    g.color = rgb(Paint.CREAM, .82f)
    for (line in wrap(g, end.body + "\n" + end.done, inner)) {
        y += g.fontMetrics.height
        g.drawString(line, side, y)
    }
    y += dp(14)
    // The two big buttons, each with its line under it. Sharing is a switch, off until the user agrees.
    fun button(text: String, filled: Boolean, under: String) {
        g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(20))
        val height = g.fontMetrics.height + dp(28)
        val shape = RoundRectangle2D.Float(side.toFloat(), y.toFloat(), inner.toFloat(), height.toFloat(), dp(24).toFloat(), dp(24).toFloat())
        if (filled) {
            g.color = orange
            g.fill(shape)
            g.color = ink
        } else {
            g.color = rgb(Paint.CREAM, .6f)
            g.stroke = BasicStroke(dp(1).toFloat())
            g.draw(shape)
            g.color = cream
        }
        g.drawString(text, WIDTH / 2 - g.fontMetrics.stringWidth(text) / 2, y + height / 2 + g.fontMetrics.ascent / 2 - dp(2))
        y += height + dp(4)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, dp(13))
        g.color = rgb(Paint.CREAM, .82f)
        for (line in wrap(g, under, inner)) {
            y += g.fontMetrics.height
            g.drawString(line, WIDTH / 2 - g.fontMetrics.stringWidth(line) / 2, y)
        }
        y += dp(12)
    }
    button("The boring guide", true, BORING_LINE)
    button("Share my swipes: off", false, if (answered) SHARE_ANSWERED else SHARE_PLAIN)
    // The field, two lines, with the end's words waiting in it.
    g.font = calibrated(g, Font.PLAIN, "Tihs keybaord fxies yuor typos, sp yuo cna kepe", 807)
    val pad = dp(12)
    val fieldHeight = 2 * g.fontMetrics.height + 2 * pad
    g.color = cream
    g.fill(RoundRectangle2D.Float(side.toFloat(), y.toFloat(), inner.toFloat(), fieldHeight.toFloat(), dp(12).toFloat(), dp(12).toFloat()))
    g.color = rgb(Paint.INK, .45f)
    for ((index, line) in wrap(g, END_TEXT, inner - 2 * pad).take(2).withIndex())
        g.drawString(line, side + pad, y + pad + g.fontMetrics.ascent + index * g.fontMetrics.height)
    y += fieldHeight
    val keysTop = HEIGHT - dp(4 * 50 + 70)
    if (y > keysTop) mark(g, "OVERLAPS THE KEYS BY ${y - keysTop} PX", keysTop)
    drawKeys(g, keysTop, side, null)
    g.dispose()
    ImageIO.write(image, "png", file)
}

/** Draws every slide in [chapters], in each of its states, to PNGs in [folder]. */
internal fun drawSlides(folder: String, chapters: Set<GuideChapter>) {
    val out = File(folder).apply { mkdirs() }
    var count = 0
    for ((index, step) in GUIDE_STEPS.withIndex()) {
        if (step.chapter !in chapters || step.chapter == GuideChapter.End) continue
        for (frame in framesFor(step)) {
            drawing = frame.name
            draw(frame, File(out, "%02d-%s.png".format(index + 1, frame.name)))
            count++
        }
    }
    if (GuideChapter.End in chapters) for (answered in listOf(false, true)) {
        drawing = "end-" + if (answered) "answered" else "asking"
        drawEnd(File(out, "%02d-%s.png".format(GUIDE_STEPS.size, drawing)), answered)
        count++
    }
    println("$count slide states drawn to $folder")
}
