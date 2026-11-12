package com.atlas.keyboard

import com.atlas.keyboard.KeyboardEvent.CursorStarted
import com.atlas.keyboard.KeyboardEvent.DeletedWord
import com.atlas.keyboard.KeyboardEvent.DoubleTapped
import com.atlas.keyboard.KeyboardEvent.EmptyKey
import com.atlas.keyboard.KeyboardEvent.FieldChanged
import com.atlas.keyboard.KeyboardEvent.Fixed
import com.atlas.keyboard.KeyboardEvent.HeldSymbol
import com.atlas.keyboard.KeyboardEvent.ModifierState
import com.atlas.keyboard.KeyboardEvent.Pushed
import com.atlas.keyboard.KeyboardEvent.Recognised
import com.atlas.keyboard.KeyboardEvent.WordMenu
import com.atlas.keyboard.Modifiers.Level
import com.atlas.keyboard.Modifiers.Panel
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/*
 * Desktop drawings of the guide's slides (v5), to look at without a phone: each slide at its start, its hint, a blocked try, a
 * real mistake's chip, the pass's wash and its calm done state with the big Next; then the finale's frames and the end page. They
 * follow the slide's real words, chips and field, from the guide's own steps, laid out as the guide lays them out, at the Titan 2
 * Elite's size. The fonts are the desktop's, not the phone's, so widths are close, not exact. Set SLIDES_PNG to a folder.
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
    /** The real panel that's open below, if any: the drawing of the keys stays, only hinted on. */
    val panel: String? = null,
    /** What the drawing hints on some keys, under their letters. See [GuideStage.labels]. */
    val labels: Map<String, String>? = null,
    /** What the field marks in orange to aim at. See [GuideMemory.target]. */
    val target: IntRange? = null,
    /** Only underlined, as on the steer slide. */
    val underline: Boolean = false,
    /** Where a symbol slide's gap is, or -1. */
    val gap: Int = -1,
    /** The key that the slide's spot lights in this frame, and the keys held, steady. See [GuideCue.Spot]. */
    val spot: String? = null,
    val held: List<String> = emptyList(),
    /** How much of a hunt's bar is left under the instruction, from 1 to 0, or null for no bar. */
    val hunt: Float? = null,
    /** The steer slide's word, found as the field wraps; [SteerAim.Entered] draws the cursor in it. */
    val steer: SteerAim = SteerAim.None,
    /** The pass's wash over everything, from 0 to 1. */
    val wash: Float = 0f,
    /** The hold slide's zones on the drawing. */
    val zones: Boolean = false,
)

private enum class SteerAim { None, Start, Entered }

/** A mistake for each slide, as the keyboard reports it, so a drawing shows the chip that it gets. */
private val MISTAKES: Map<String, List<KeyboardEvent>> = mapOf(
    "type" to listOf(Pushed(29), Fixed("Space", "Space")),
    "swipe" to listOf(Pushed(29), Pushed(62)),
    "menu" to listOf(KeyboardEvent.Chosen("bar")),
    "hold" to listOf(KeyboardEvent.DeleteHoldStarted, DeletedWord(held = true), KeyboardEvent.Moved(40f, lifted = true)),
    "steer" to listOf(DoubleTapped(2)),
    "selectmore" to listOf(CursorStarted(false)),
    "alt" to listOf(ModifierState(Level.Off, Level.Once, Panel.Closed, false), FieldChanged(gapSeed(ALT_LINE, ALT_SYMBOL) + "#", 1, 1)),
    "holdsym" to listOf(HeldSymbol("#")),
    "symmain" to listOf(ModifierState(Level.Off, Level.Off, Panel.Symbols, false), EmptyKey),
    "symgroups" to listOf(KeyboardEvent.GroupPicked(2)),
    "clown" to listOf(ModifierState(Level.Off, Level.Off, Panel.Emoji, false), FieldChanged(gapSeed(CLOWN_LINE, CLOWN) + "😀", 2, 2)),
    "paste" to listOf(KeyboardEvent.Copied("x")),
    "clipboard" to listOf(ModifierState(Level.Off, Level.Off, Panel.Symbols, false), ModifierState(Level.Off, Level.Off, Panel.Clipboard, false),
        ModifierState(Level.Off, Level.Off, Panel.Closed, false)),
)

/** What each slide's lock blocks, for its blocked frame: the thing most likely tried by chance there. */
private val BLOCKED: Map<String, GuideLesson> = mapOf(
    "type" to GuideLesson.BarTap, "swipe" to GuideLesson.BarHold, "menu" to GuideLesson.Swipe,
    "backspace" to GuideLesson.Flick, "flick" to GuideLesson.DeleteHold, "hold" to GuideLesson.Backspace, "steer" to GuideLesson.Typing,
    "selectmore" to GuideLesson.Typing, "alt" to GuideLesson.Typing, "holdsym" to GuideLesson.Typing, "symmain" to GuideLesson.Alt,
    "symgroups" to GuideLesson.Typing, "clown" to GuideLesson.Typing, "paste" to GuideLesson.Typing, "clipboard" to GuideLesson.Ctrl,
)

private fun at(step: GuideStep, text: String, start: Int = text.length, end: Int = start, ghost: String = "") =
    Frame("", step.chapter, "", "", field = text, start = start, end = end, ghost = ghost)

/** What the field holds on a slide, before anything is done: its text, the cursor or selection, the ghost, and what it marks. */
private fun fieldFor(step: GuideStep): Frame = when (step.id) {
    "type" -> at(step, "", ghost = TYPE_TYPOS)
    "swipe" -> at(step, SWIPE_LEAD.trimEnd(), ghost = " " + SWIPE_LINE.removePrefix(SWIPE_LEAD))
    "menu" -> at(step, "$MENU_LINE ")
    "backspace" -> at(step, "$BACKSPACE_SEED ", BACKSPACE_SEED.length).copy(target = BACKSPACE_SEED.length - 1..BACKSPACE_SEED.length - 1)
    "flick" -> at(step, "$FLICK_SEED ", flickCursor(FLICK_SEED)).copy(target = flickTarget(FLICK_SEED))
    "hold" -> at(step, "$HOLD_SEED ", holdCursor(HOLD_SEED)).copy(target = holdTarget(HOLD_SEED, holdCursor(HOLD_SEED)), zones = true)
    "steer" -> steeringText(step).let { text -> at(step, text, text.length - 1).copy(underline = true) }
    "selectmore" -> steeringText(step).let { text -> grabTarget(text, 0)!!.let { at(step, text, it.first).copy(target = it) } }
    "paste" -> PASTE_SEED.indexOf(PASTE_OVER).let { at(step, "$PASTE_SEED ", it, it + PASTE_OVER.length) }
    "clipboard" -> at(step, "${CLIP_LEAD.trimEnd()} ")
    else -> if (step.symbol != null) {
        val seed = gapSeed(step.line, step.symbol)
        at(step, seed, gapCursor(step.line, step.symbol)).copy(gap = gapCursor(step.line, step.symbol))
    } else at(step, END_TEXT)
}

/** The steering slides' field, as GuideActivity seeds it: the last few lines finished so far, then the slide's own. */
private fun steeringText(step: GuideStep) =
    (GUIDE_STEPS.takeWhile { it.id != step.id }.map { it.line }.filter { it.isNotEmpty() }.takeLast(4) + step.seed).joinToString("\n") + " "

/** The field once the slide is passed: its line, finished. */
private fun passedField(step: GuideStep, before: Frame): Frame = when (step.id) {
    "type", "swipe", "menu", "backspace", "clipboard" -> at(step, step.line + " ")
    "flick" -> at(step, "$FLICK_LINE ", FLICK_LINE.length - " word".length)
    "hold" -> at(step, "$HOLD_LINE ", "Delete ".length)
    "steer" -> before.copy(start = before.start - 40, end = before.start - 40)
    "selectmore" -> before.copy(start = before.target!!.first, end = before.target.last + 1)
    "paste" -> at(step, "$PASTE_LINE ", PASTE_LINE.length)
    else -> if (step.symbol != null) at(step, step.line, gapCursor(step.line, step.symbol) + step.symbol.length) else before
}

private fun framesFor(step: GuideStep): List<Frame> {
    val field = fieldFor(step)
    fun frame(name: String, title: String, line: String, chip: String? = null, passed: Boolean = false, from: Frame = field,
              stage: GuideStage? = step.stages.firstOrNull()): Frame {
        val spot = stage?.cue as? GuideCue.Spot ?: step.cue as? GuideCue.Spot
        val panel = if (stage != null && step.usesPanel && step.stages.indexOf(stage) > 0 && step.id != "alt")
            if (step.id == "clown") "the emoji panel" else if (step.id == "clipboard" && step.stages.indexOf(stage) == 2) "the clipboard" else "the Sym panel"
            else null
        return from.copy(name = "${step.id}-$name", title = title, line = line, chip = chip, passed = passed, panel = if (passed) null else panel,
            labels = if (passed) null else stage?.labels, held = if (passed) emptyList() else spot?.held.orEmpty(),
            spot = if (passed) null else spot?.keys?.firstOrNull(), zones = field.zones)
    }
    val frames = ArrayList<Frame>()
    val first = step.stages.firstOrNull()?.text ?: step.body
    frames += frame("1-start", step.title, first)
    // The first slide's hint: the keys of the word, one after the other, then Space. Here S is lit, typed t would go on from i.
    if (step.id == "type") frames += frame("2-hint", step.title, first).copy(spot = TYPE_TYPOS.take(1).uppercase())
    // Each later instruction, as the slide moves on to it: its hint on the keys.
    for ((index, stage) in step.stages.withIndex().drop(1)) {
        frames += frame("2-hint${index + 1}", step.title, stage.text, stage = stage)
    }
    // A blocked try: the chip says it's blocked, and why.
    BLOCKED[step.id]?.let { lesson -> frames += frame("3-blocked", step.title, first, chip = blockedChip(step, lesson.name)) }
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
        if (said != null) frames += frame("3-chip", step.title, flow.instruction, chip = said, stage = step.stages.getOrNull(flow.stage))
    }
    if (step.id == "swipe") frames += frame("2-count", step.title, swipesLeft(2))
    if (step.id == "steer") {
        frames.replaceAll { it.copy(steer = SteerAim.Start) }
        frames += frame("2-hint2-entered", step.title, step.stages[1].text, stage = step.stages[1]).copy(steer = SteerAim.Entered)
    }
    // The hunts: the bar full, half gone, then the count-up with the mockery, the hint, and leave to skip.
    step.hunt?.let { hunt ->
        val stage = step.stages[1]
        frames.removeAll { it.name.endsWith("-2-hint2") }
        frames += frame("2-hunt0s", step.title, stage.text, stage = stage).copy(hunt = 1f)
        frames += frame("2-hunt10s", step.title, stage.text, stage = stage).copy(hunt = .5f)
        frames += frame("2-hunt${HUNT_DRAIN_SECONDS}s", step.title, huntLine(stage.text, HUNT_DRAIN_SECONDS), chip = hunt.mock, stage = stage)
        frames += frame("2-hunt${HUNT_HINT_SECONDS}s", step.title, huntLine(stage.text, HUNT_HINT_SECONDS), chip = hunt.hint, stage = stage)
        frames += frame("2-hunt${HUNT_SKIP_SECONDS}s", step.title, huntLine(stage.text, HUNT_SKIP_SECONDS), chip = hunt.skip, stage = stage)
    }
    if (step.id == "selectmore") {
        val aim = field.target!!
        frames += frame("2-grab", step.title, step.stages[1].text, from = field.copy(start = aim.first, end = field.field.indexOf(' ', aim.first + 4)),
            stage = step.stages[1])
        val greedy = field.copy(start = aim.first, end = field.field.indexOf(' ', aim.last + 30))
        frames += frame("3-chip-greedy", step.title, step.stages[1].text, chip = "Easy. Just my heart. Slide back a bit.", from = greedy,
            stage = step.stages[1])
    }
    if (step.id == "clipboard") frames += frame("3-chip-rude", step.title, step.stages[2].text,
        chip = "That one's rude. Took it back. Find the nice one.", stage = step.stages[2])
    if (step.id == "hold") {
        val one = field.copy(field = field.field.replace("that tattoo, ", ""), start = field.start - "that tattoo, ".length)
        frames += frame("2-count1", step.title, regretsLine(1), from = one.copy(target = holdTarget(one.field, one.start)))
    }
    val passed = passedField(step, field)
    val praise = when (step.id) {
        "hold" -> regretsLine(REGRETS_TO_PASS)
        "clown", "symgroups" -> step.hunt!!.slow(37)
        else -> step.praise
    }
    frames += frame("4-pass-wash", step.done, praise, passed = true, from = passed).copy(wash = .6f, steer = frames.last().steer.let {
        if (it == SteerAim.None) it else SteerAim.Entered })
    frames += frame("4-passed", step.done, praise, passed = true, from = passed).copy(steer = if (step.id == "steer") SteerAim.Entered else SteerAim.None)
    if (step.id == "hold") frames += frame("5-frozen", step.done, regretsFinal(11), passed = true, from = passed)
    step.hunt?.let { hunt ->
        // The roast goes by the time it took (v5.2): the frames above are a slow find; this one is quick, with its chip.
        frames.replaceAll { if (it.name.endsWith("-4-passed")) it.copy(name = it.name + "-slow", line = hunt.slow(37)) else it }
        frames += frame("4-passed-fast", step.done, if (step.id == "symgroups") groupsPraise(1) else step.praise, chip = hunt.fast,
            passed = true, from = passed)
    }
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

private fun rgb(argb: Int, alpha: Float = 1f) = Color((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, (alpha.coerceIn(0f, 1f) * 255).toInt())
private val ink = rgb(Paint.INK)
private val cream = rgb(Paint.CREAM)
private val orange = rgb(Paint.ORANGE)

private fun image(): Pair<BufferedImage, Graphics2D> {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    return image to g
}

/**
 * The top bar (v5.2): the chapters across the whole width, a tick for each slide, the ones passed filled and the one on the screen
 * half lit; under it, the chapter's name on the left, and the pill and ✕ on the right. Returns its height.
 */
private fun topBar(g: Graphics2D, chapter: GuideChapter, pillText: String?, filled: Boolean, onOrange: Boolean, y: Int,
                   current: String? = null, passed: Boolean = false): Int {
    val side = dp(Layout.KEY_SIDE_DP)
    val strong = if (onOrange) ink else cream
    val accent = if (onOrange) ink else orange
    val tickRow = dp(4 + 2 * 4)
    if (chapter != GuideChapter.End) {
        val chapters = GuideChapter.entries.filter { it != GuideChapter.End }
        val ids = chapters.map { c -> GUIDE_STEPS.filter { it.chapter == c }.map { it.id } }
        val ticks = ids.sumOf { it.size }
        val room = WIDTH - 2 * side - dp(6) * (chapters.size - 1) - dp(2) * (ticks - chapters.size)
        val each = room.toFloat() / ticks
        val at = GUIDE_STEPS.indexOfFirst { it.id == current }
        var x = side.toFloat()
        for (slides in ids) {
            for ((index, id) in slides.withIndex()) {
                val order = GUIDE_STEPS.indexOfFirst { it.id == id }
                g.color = when {
                    order < at || order == at && passed -> accent
                    order == at -> rgb(if (onOrange) Paint.INK else Paint.ORANGE, .55f)
                    else -> rgb(if (onOrange) Paint.INK else Paint.ORANGE, .25f)
                }
                g.fill(RoundRectangle2D.Float(x, y + tickRow / 2f - dp(2), each, dp(4).toFloat(), dp(4).toFloat(), dp(4).toFloat()))
                x += each + if (index < slides.lastIndex) dp(2) else dp(6)
            }
        }
    }
    val rowTop = y + tickRow
    val bar = dp(44)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(13))
    if (chapter != GuideChapter.End) {
        g.color = rgb(if (onOrange) Paint.INK else Paint.CREAM, .55f)
        g.drawString(chapter.label.uppercase(), side, rowTop + bar / 2 + g.fontMetrics.ascent / 2 - dp(1))
    }
    val close = dp(44)
    var x = WIDTH - side - close
    if (pillText != null) {
        val pillWidth = g.fontMetrics.stringWidth(pillText) + dp(20)
        val pillX = x - dp(4) - pillWidth
        val pill = RoundRectangle2D.Float(pillX.toFloat(), rowTop + dp(9).toFloat(), pillWidth.toFloat(), bar - dp(18).toFloat(), bar.toFloat(), bar.toFloat())
        if (filled) {
            g.color = orange
            g.fill(pill)
            g.color = ink
        } else {
            g.color = if (onOrange) ink else rgb(Paint.CREAM, .6f)
            g.stroke = BasicStroke(dp(1).toFloat())
            g.draw(pill)
        }
        g.drawString(pillText, pillX + dp(10), rowTop + bar / 2 + g.fontMetrics.ascent / 2 - dp(1))
    }
    g.color = strong
    g.stroke = BasicStroke(dp(2f).toFloat())
    val c = dp(8)
    val cx = x + close / 2
    val cy = rowTop + bar / 2
    g.drawLine(cx - c, cy - c, cx + c, cy + c)
    g.drawLine(cx + c, cy - c, cx - c, cy + c)
    return tickRow + bar
}

/** [text] in at most [lines] lines at [font]'s size, smaller down to [smallest] if it needs to be: as the phone's auto-size does. */
private fun shrinkToFit(g: Graphics2D, text: String, font: Font, smallest: Float, width: Int, lines: Int): Pair<Font, List<String>> {
    var size = font.size2D
    while (true) {
        val tried = font.deriveFont(size)
        g.font = tried
        val wrapped = wrap(g, text, width)
        if (wrapped.size <= lines || size <= smallest) return tried to wrapped
        size -= .5f
    }
}

private fun draw(frame: Frame, file: File) {
    val (image, g) = image()
    g.color = ink
    g.fillRect(0, 0, WIDTH, HEIGHT)
    val side = dp(Layout.KEY_SIDE_DP)
    val inner = WIDTH - 2 * side
    var y = dp(40)
    // Passed, the small pill steps aside: Next, by the keys, is the one Next.
    y += topBar(g, frame.chapter, if (frame.passed) null else "Skip", false, false, y, frame.name.substringBefore('-'), frame.passed) + dp(14)
    // The title: one line at 36 sp, smaller if it needs to be, with the orange check once passed (v5.2).
    val titleFont = calibrated(g, Font.BOLD, "Type like a normal person.", 540 * 36 / 28)
    val titleLine = dp(45)
    val (titleShown, titleLines) = shrinkToFit(g, frame.title + if (frame.passed) " ✓" else "", titleFont, titleFont.size2D * 24 / 36, inner, 1)
    g.font = titleShown
    g.color = cream
    val titleRoom = titleLine
    val titleTop = y + (titleRoom - titleLines.size * g.fontMetrics.height) / 2
    for ((index, line) in titleLines.withIndex()) {
        val base = titleTop + (index + 1) * g.fontMetrics.height - g.fontMetrics.descent
        if (frame.passed && index == titleLines.lastIndex && line.endsWith("✓")) {
            val words = line.dropLast(1)
            g.drawString(words, side, base)
            val at = side + g.fontMetrics.stringWidth(words)
            val size = titleShown.size2D * .5f
            g.color = orange
            g.stroke = BasicStroke(dp(2.8f).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(Path2D.Float().apply {
                moveTo(at.toFloat(), base - size * .45f)
                lineTo(at + size * .36f, base - size * .08f)
                lineTo(at + size, base - size * .86f)
            })
            g.color = cream
        } else g.drawString(line, side, base)
    }
    if (titleLines.size > 1) mark(g, "TITLE OVER ONE LINE", y)
    y += titleRoom + dp(14)
    // The instruction, one line at 20 sp, smaller if it needs to be, or a chip over it.
    val bodyFont = calibrated(g, Font.PLAIN, "Back for a refresher? Pick a chapter. No judgement. Some judgement.", 875 * 20 / 16)
    val bodyLine = dp(28)
    val lineHeight = bodyLine + 2 * dp(3)
    if (frame.chip != null) {
        g.font = calibrated(g, Font.PLAIN, "Oh, look who's back. Didn't know everything after all?", 672 * 20 / 18)
        val shown = fit(g, frame.chip, inner - dp(24))
        val width = g.fontMetrics.stringWidth(shown) + dp(24)
        val chipHeight = g.fontMetrics.height + dp(12)
        val chipTop = y + (lineHeight - chipHeight) / 2
        g.color = cream
        g.fill(RoundRectangle2D.Float(side.toFloat(), chipTop.toFloat(), width.toFloat(), chipHeight.toFloat(), chipHeight.toFloat(), chipHeight.toFloat()))
        g.color = ink
        g.drawString(shown, side + dp(12), chipTop + chipHeight / 2 + g.fontMetrics.ascent / 2 - dp(2))
        if (g.fontMetrics.stringWidth(frame.chip) + dp(24) > inner) mark(g, "CHIP TOO LONG", y)
    } else {
        val (shownFont, lines) = shrinkToFit(g, frame.line, bodyFont, bodyFont.size2D * 15 / 20, inner, 1)
        g.font = shownFont
        g.color = rgb(Paint.CREAM, .86f)
        val top = y + (lineHeight - lines.size * g.fontMetrics.height) / 2
        for ((index, line) in lines.withIndex()) g.drawString(line, side, top + (index + 1) * g.fontMetrics.height - g.fontMetrics.descent)
        if (lines.size > 1) mark(g, "INSTRUCTION OVER ONE LINE", y)
    }
    frame.hunt?.let { left ->
        g.color = orange
        g.fillRect(side, y + lineHeight - dp(3), Math.round(inner * left), dp(3))
    }
    y += lineHeight + dp(14)
    // The field: three lines, on ink, with what's typed in cream and the ghost after it in grey.
    val fieldFont = calibrated(g, Font.PLAIN, "Tihs keybaord fxies yuor typos, sp yuo cna kepe", 807 * 23 / 18)
    g.font = fieldFont
    val pad = dp(12)
    val fieldLine = g.fontMetrics.height
    val fieldHeight = FIELD_LINES * fieldLine + 2 * pad
    g.color = blend(Paint.INK, Paint.CREAM, .08f)
    g.fill(RoundRectangle2D.Float(side.toFloat(), y.toFloat(), inner.toFloat(), fieldHeight.toFloat(), dp(12).toFloat(), dp(12).toFloat()))
    drawField(g, frame, side + pad, y + pad, inner - 2 * pad, fieldLine)
    y += fieldHeight
    // The keys at the bottom, the whole width, at the real keyboard's size, above the keyboard's own bar (v5.2).
    val keysTop = HEIGHT - BAR_ROOM - Math.ceil(GEOMETRY.height.toDouble()).toInt()
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(17))
    val nextHeight = g.fontMetrics.height + dp(20)
    val gap = (keysTop - y - nextHeight) / 2
    if (gap < dp(8)) mark(g, "NO ROOM BETWEEN FIELD AND KEYS", keysTop)
    if (frame.passed) {
        // Next: a normal button, on the right, in its place (v5.2).
        val top = y + gap
        val wide = g.fontMetrics.stringWidth("Next") + 2 * dp(28)
        g.color = orange
        g.fill(RoundRectangle2D.Float((WIDTH - side - wide).toFloat(), top.toFloat(), wide.toFloat(), nextHeight.toFloat(), nextHeight.toFloat(), nextHeight.toFloat()))
        g.color = ink
        g.drawString("Next", WIDTH - side - wide / 2 - g.fontMetrics.stringWidth("Next") / 2, top + nextHeight / 2 + g.fontMetrics.ascent / 2 - dp(2))
        if (top < y + dp(8)) mark(g, "NEXT OVERLAPS THE FIELD", top)
    }
    frame.panel?.let { panel ->
        // A panel is open: the drawing of the keys hides, so it never covers the real panel (v5.2). Only a note, for review.
        g.color = Color(0x66, 0xCC, 0xFF)
        g.font = Font(Font.SANS_SERIF, Font.ITALIC, dp(12))
        g.drawString("render note: $panel is open; the drawn keys hide under it", side, keysTop + dp(24))
    }
    if (frame.panel == null) drawKeys(g, keysTop, side, frame)
    if (frame.wash > 0f) {
        g.color = rgb(Paint.ORANGE, .85f * frame.wash)
        g.fillRect(0, 0, WIDTH, HEIGHT)
    }
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
    g.drawString(what, WIDTH - dp(160), y)
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
    val starts = ArrayList<Int>()
    var at = 0
    while (at < all.length) {
        starts += at
        var end = at
        var lastBreak = -1
        var newline = false
        while (end < all.length && g.fontMetrics.stringWidth(all.substring(at, end + 1)) <= width) {
            if (all[end] == '\n') {
                newline = true
                break
            }
            if (all[end] == ' ') lastBreak = end + 1
            end++
        }
        at = if (newline) end + 1 else if (end >= all.length) all.length else if (lastBreak > at) lastBreak else end
    }
    if (starts.isEmpty()) starts += 0
    val cursorLine = starts.indexOfLast { it <= frame.start.coerceAtMost(all.length) }.coerceAtLeast(0)
    val first = when {
        frame.ghost.isNotEmpty() || frame.field.isEmpty() -> cursorLine
        // The steering slides show the cursor's line last, with the lines finished before it above.
        frame.chapter == GuideChapter.Steering -> cursorLine - (FIELD_LINES - 1)
        else -> (cursorLine - 1).coerceAtLeast(0)
    }.coerceAtMost((starts.size - FIELD_LINES).coerceAtLeast(0)).coerceAtLeast(0)
    var start = frame.start
    var target = frame.target
    if (frame.steer != SteerAim.None) {
        val other = (cursorLine - 1).coerceAtLeast(0)
        val otherEnd = (starts.getOrNull(other + 1) ?: all.length) - 1
        fun x(at: Int): Float {
            val line = starts.indexOfLast { it <= at }
            return g.fontMetrics.stringWidth(all.substring(starts[line], at)).toFloat()
        }
        target = steerTarget(all, starts[other]..otherEnd, x(frame.start)) { x(it) }
        if (target == null) mark(g, "NO STEER TARGET", top)
        if (frame.steer == SteerAim.Entered && target != null) start = target.first + 2
    }
    val shown = frame.copy(start = if (frame.passed && frame.steer == SteerAim.None) frame.start else start,
        end = if (frame.passed && frame.steer == SteerAim.None) frame.end else start, target = if (frame.passed) null else target)
    for (row in 0 until FIELD_LINES) {
        val index = first + row
        if (index >= starts.size) break
        val from = starts[index]
        val to = starts.getOrNull(index + 1) ?: all.length
        val base = top + row * line + g.fontMetrics.ascent
        var x = left
        for (offset in from..to) {
            if (offset == shown.gap && !frame.passed) {
                // The gap: an orange box the width of a letter, where the symbol goes.
                val wide = g.fontMetrics.stringWidth("M")
                g.color = rgb(Paint.ORANGE, .35f)
                g.fillRoundRect(x + dp(2), top + row * line + dp(2), wide, line - dp(4), dp(4), dp(4))
                g.color = orange
                g.fillRect(x + dp(2), top + (row + 1) * line - dp(3), wide, dp(2))
            }
            if (offset >= to) break
            val char = all[offset].toString().replace("\n", "")
            val w = g.fontMetrics.stringWidth(char)
            val aim = shown.target
            if (aim != null && offset in aim) {
                if (!frame.underline) {
                    g.color = rgb(Paint.ORANGE, .35f)
                    g.fillRect(x, top + row * line, w, line)
                }
                g.color = orange
                g.fillRect(x, top + (row + 1) * line - dp(if (frame.underline) 3 else 2), w, dp(if (frame.underline) 3 else 2))
            }
            if (offset in shown.start until shown.end) {
                g.color = rgb(Paint.ORANGE, .45f)
                g.fillRect(x, top + row * line, w, line)
            }
            g.color = if (offset < frame.field.length) cream else rgb(Paint.CREAM, .38f)
            g.drawString(char, x, base)
            if (offset == shown.start && shown.start == shown.end) {
                g.color = orange
                g.fillRect(x - dp(1), top + row * line + dp(2), dp(2), line - dp(4))
            }
            x += w
        }
        if (shown.start == shown.end && shown.start == to && to == all.length) {
            g.color = orange
            g.fillRect(x, top + row * line + dp(2), dp(2), line - dp(4))
        }
    }
}

private const val FIELD_LINES = 3
/** Room at the bottom for the keyboard's own bar, under the drawing. */
private val BAR_ROOM = dp(60)
/** The keys, as the phone draws them: the measured keyboard across the whole width, at the default calibration. */
private val GEOMETRY = KeyGeometry(WIDTH.toFloat(), KeyAlignment.DEFAULT)

/** The drawing of the keys: the real keyboard's size and proportions, thin outlines, lit for a spot, steady for a held key. */
private fun drawKeys(g: Graphics2D, top: Int, side: Int, frame: Frame?) {
    for (box in GEOMETRY.boxes) {
        val key = box.name
        val x = box.left
        val y = top + box.top
        val hint = frame?.labels?.get(key)
        val lit = frame != null && (key == frame.spot || hint != null && key == frame.labels?.keys?.first())
        val held = frame != null && key in frame.held
        val shape = RoundRectangle2D.Float(x, y, box.width, box.height, dp(8).toFloat(), dp(8).toFloat())
        g.color = when {
            lit -> orange
            held -> rgb(Paint.ORANGE, .8f)
            else -> rgb(Paint.CREAM, .04f)
        }
        g.fill(shape)
        g.color = if (lit || held) orange else rgb(Paint.CREAM, .4f)
        g.stroke = BasicStroke(dp(1).toFloat())
        g.draw(shape)
        g.color = if (lit || held) ink else rgb(Paint.CREAM, .7f)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(19))
        val text = if (key.length == 1) key else key.take(3)
        val lift = if (hint != null) dp(7) else 0
        g.drawString(text, x + box.width / 2 - g.fontMetrics.stringWidth(text) / 2, y + box.height / 2f + dp(7) - lift)
        if (hint != null) {
            g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(10))
            if (g.fontMetrics.stringWidth(hint) > box.width - dp(4)) mark(g, "KEY HINT TOO LONG", y.toInt())
            g.drawString(hint, x + box.width / 2 - g.fontMetrics.stringWidth(hint) / 2, y + box.height - dp(8).toFloat())
        }
    }
    if (frame?.zones == true) {
        // The held delete's zones: back on Backspace pauses; further out, a lighter box, is top speed. No line, no label for it.
        val fast = GEOMETRY.onRow(1, .05f, .95f - REPEAT_LONG_KEYS_COPY / 10f)
        g.color = rgb(Paint.CREAM, .1f)
        g.fill(RoundRectangle2D.Float(fast.left, top + fast.top, fast.width, fast.height, dp(8).toFloat(), dp(8).toFloat()))
        val pause = GEOMETRY.onRow(1, .95f - PAUSE_KEYS_COPY / 10f, .95f)
        g.color = rgb(Paint.CREAM, .2f)
        g.fill(RoundRectangle2D.Float(pause.left, top + pause.top, pause.width, pause.height, dp(8).toFloat(), dp(8).toFloat()))
        g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(10))
        g.color = rgb(Paint.CREAM, .85f)
        g.drawString("pause", pause.left + dp(4), top + pause.bottom - dp(6))
    }
}

/** The held delete's zones, as DeleteSwipe.kt has them (it isn't compiled for these checks): the same numbers, copied. */
private const val PAUSE_KEYS_COPY = .8f
private const val REPEAT_LONG_KEYS_COPY = 5.5f

/** The end's words that live in GuideActivity, which isn't compiled for these checks: the same text, copied. */
private const val SHARE_PLAIN = "Off until you agree. Then we get examples of your swipes to make swiping better. Change it " +
    "any time in Settings > Share swipes.\nTrust me bro."
private const val SHARE_ANSWERED = "Trust me bro."

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

/** The finale's box on the end screen: under the top bar, to where the buttons start, as the phone measures it (v5.2). */
private val FINALE_TOP = (dp(40) + dp(12) + dp(44) + dp(16)).toFloat()
private var finaleBottom = HEIGHT * .68f

/** Draws the finale at [at] ms, on the end's orange, from [GuideFinale]: the same frames as the phone. */
private fun drawFinale(g: Graphics2D, at: Long) {
    val measure = GuideFinale.Measure { text, size, bold -> g.getFontMetrics(Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(size)).stringWidth(text).toFloat() }
    for (piece in GuideFinale.frame(at, SUMMARY_LINES, WIDTH.toFloat(), FINALE_TOP, finaleBottom, measure)) when (piece) {
        is GuideFinale.Text -> {
            g.font = Font(Font.SANS_SERIF, if (piece.bold) Font.BOLD else Font.PLAIN, 1).deriveFont(piece.size)
            g.color = rgb(piece.colour, piece.alpha)
            if (piece.spacing > 0f) {
                var x = piece.x
                for (char in piece.text) {
                    g.drawString(char.toString(), x, piece.y)
                    x += g.fontMetrics.stringWidth(char.toString()) + piece.spacing
                }
            } else g.drawString(piece.text, piece.x, piece.y)
        }
        is GuideFinale.Mark -> {
            val scale = piece.side / (1080f * GuideFinale.MARK_SHAPE)
            val transform = java.awt.geom.AffineTransform().apply {
                translate((piece.x - 540f * scale).toDouble(), (piece.y - 540f * scale).toDouble())
                scale(scale.toDouble(), scale.toDouble())
            }
            for ((data, colour) in listOf(GuideFinale.MARK_PATH to Paint.INK, GuideFinale.LINE_PATH to Paint.CREAM)) {
                val path = Path2D.Float()
                for (shape in GuideFinale.shapes(data)) {
                    shape.forEachIndexed { index, (x, y) -> if (index == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    path.closePath()
                }
                g.color = rgb(colour, piece.alpha)
                g.fill(transform.createTransformedShape(path))
            }
        }
    }
}

/** A frame of the finale, alone on the end's orange, as it plays before the buttons come in. */
private fun drawFinaleFrame(file: File, at: Long) {
    val (image, g) = image()
    g.color = orange
    g.fillRect(0, 0, WIDTH, HEIGHT)
    topBar(g, GuideChapter.End, null, false, true, dp(40))
    drawFinale(g, at)
    g.dispose()
    ImageIO.write(image, "png", file)
}

/**
 * The end page, once the finale is over: the logo and its name at the top, then the big button to go type, the share switch with
 * its line, and the way to start again. The header has only the boring guide and ✕. [answered] draws it after the share offer
 * has an answer.
 */
private fun drawEnd(file: File, answered: Boolean) {
    val (image, g) = image()
    g.color = orange
    g.fillRect(0, 0, WIDTH, HEIGHT)
    val side = dp(Layout.KEY_SIDE_DP)
    val inner = WIDTH - 2 * side
    topBar(g, GuideChapter.End, "The boring guide", false, true, dp(40))
    // The buttons, from the bottom up, as the column lays them out under the spacer; the finale ends 20 dp above them.
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(24))
    val goHeight = g.fontMetrics.height + dp(28)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(20))
    val shareHeight = g.fontMetrics.height + dp(28)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(17))
    val againHeight = g.fontMetrics.height + dp(22)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, dp(15))
    val under = wrap(g, if (answered) SHARE_ANSWERED else SHARE_PLAIN, inner)
    if (under.size > 4) mark(g, "SHARE TEXT OVER FOUR LINES", HEIGHT - dp(60))
    val underHeight = under.size * g.fontMetrics.height
    var y = HEIGHT - dp(16) - againHeight - dp(12) - underHeight - dp(4) - shareHeight - dp(12) - goHeight
    finaleBottom = y - dp(20).toFloat()
    drawFinale(g, GuideFinale.total(SUMMARY_LINES))
    if (finaleBottom - FINALE_TOP < dp(220)) mark(g, "FINALE SQUEEZED", y)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(24))
    g.color = ink
    g.fill(RoundRectangle2D.Float(side.toFloat(), y.toFloat(), inner.toFloat(), goHeight.toFloat(), dp(24).toFloat(), dp(24).toFloat()))
    g.color = cream
    g.drawString("Go type something", WIDTH / 2 - g.fontMetrics.stringWidth("Go type something") / 2, y + goHeight / 2 + g.fontMetrics.ascent / 2 - dp(2))
    y += goHeight + dp(12)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(20))
    g.color = ink
    g.stroke = BasicStroke(dp(1).toFloat())
    g.draw(RoundRectangle2D.Float(side.toFloat(), y.toFloat(), inner.toFloat(), shareHeight.toFloat(), dp(24).toFloat(), dp(24).toFloat()))
    val share = "Share my swipes: off"
    g.drawString(share, WIDTH / 2 - g.fontMetrics.stringWidth(share) / 2, y + shareHeight / 2 + g.fontMetrics.ascent / 2 - dp(2))
    y += shareHeight + dp(4)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, dp(15))
    g.color = rgb(Paint.INK, .86f)
    for (line in under) {
        y += g.fontMetrics.height
        g.drawString(line, WIDTH / 2 - g.fontMetrics.stringWidth(line) / 2, y - g.fontMetrics.descent)
    }
    y += dp(12)
    // Start again: a real button, outlined, not fine print (v5.2).
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(17))
    g.color = rgb(Paint.INK, .6f)
    g.draw(RoundRectangle2D.Float(side.toFloat(), y.toFloat(), inner.toFloat(), againHeight.toFloat(), dp(24).toFloat(), dp(24).toFloat()))
    g.color = ink
    g.drawString("Start the guide again", WIDTH / 2 - g.fontMetrics.stringWidth("Start the guide again") / 2, y + againHeight / 2 + g.fontMetrics.ascent / 2 - dp(2))
    g.dispose()
    ImageIO.write(image, "png", file)
}

/** "Leaving already?" in the guide's own look (v5.2): a cream card over the dimmed slide, Stay in orange, leaving outlined. */
private fun drawLeave(file: File, under: Frame) {
    draw(under, file)
    val image = ImageIO.read(file)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = rgb(Paint.INK, .62f)
    g.fillRect(0, 0, WIDTH, HEIGHT)
    val margin = dp(Layout.KEY_SIDE_DP + 8)
    val width = WIDTH - 2 * margin
    val pad = dp(24)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, dp(17))
    val body = wrap(g, "You haven't finished the guide. Leave anyway?", width - 2 * pad)
    val bodyHeight = body.size * g.fontMetrics.height
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(20))
    val button = g.fontMetrics.height + dp(28)
    val height = pad + dp(34) + dp(10) + bodyHeight + dp(20) + button + dp(10) + button + dp(20)
    val top = (HEIGHT - height) / 2
    g.color = cream
    g.fill(RoundRectangle2D.Float(margin.toFloat(), top.toFloat(), width.toFloat(), height.toFloat(), dp(24).toFloat(), dp(24).toFloat()))
    var y = top + pad
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(28))
    g.color = ink
    g.drawString("Leaving already?", margin + pad, y + g.fontMetrics.ascent)
    y += dp(34) + dp(10)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, dp(17))
    g.color = rgb(Paint.INK, .86f)
    for (line in body) {
        y += g.fontMetrics.height
        g.drawString(line, margin + pad, y - g.fontMetrics.descent)
    }
    y += dp(20)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(20))
    g.color = orange
    g.fill(RoundRectangle2D.Float((margin + pad).toFloat(), y.toFloat(), (width - 2 * pad).toFloat(), button.toFloat(), dp(24).toFloat(), dp(24).toFloat()))
    g.color = ink
    g.drawString("Stay", WIDTH / 2 - g.fontMetrics.stringWidth("Stay") / 2, y + button / 2 + g.fontMetrics.ascent / 2 - dp(2))
    y += button + dp(10)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(16))
    g.color = rgb(Paint.INK, .6f)
    g.stroke = BasicStroke(dp(1).toFloat())
    g.draw(RoundRectangle2D.Float((margin + pad).toFloat(), y.toFloat(), (width - 2 * pad).toFloat(), button.toFloat(), dp(24).toFloat(), dp(24).toFloat()))
    g.color = ink
    val leave = "I already know everything"
    g.drawString(leave, WIDTH / 2 - g.fontMetrics.stringWidth(leave) / 2, y + button / 2 + g.fontMetrics.ascent / 2 - dp(2))
    g.dispose()
    ImageIO.write(image, "png", file)
}

/** Draws every slide in [chapters], in each of its states, to PNGs in [folder], and the finale's frames and the end. */
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
    if (GuideChapter.End in chapters) {
        val lines = SUMMARY_LINES
        val moments = listOf(
            "a-summary-landing" to GuideFinale.summaryEnd(lines) / 2,
            "b-summary" to GuideFinale.summaryEnd(lines) + 200,
            "c-lighting" to GuideFinale.lightStart(lines) + (GuideFinale.lightEnd(lines) - GuideFinale.lightStart(lines)) / 2,
            "d-lit" to GuideFinale.lightEnd(lines) + 100,
            "e-gathering" to GuideFinale.gatherStart(lines) + GuideFinale.GATHER / 2,
            "f-acrostic" to GuideFinale.gatherStart(lines) + GuideFinale.GATHER + 400,
            "g-mark" to GuideFinale.markStart(lines) + GuideFinale.MARK_IN,
            "h-tk" to GuideFinale.growStart(lines),
            "i-growing" to GuideFinale.growStart(lines) + GuideFinale.GROW / 2,
            "j-titankeys" to GuideFinale.total(lines),
        )
        for ((name, at) in moments) {
            drawing = "finale-$name"
            drawFinaleFrame(File(out, "%02d-finale-%s.png".format(GUIDE_STEPS.size, name)), at)
            count++
        }
        for (answered in listOf(false, true)) {
            drawing = "end-" + if (answered) "answered" else "asking"
            drawEnd(File(out, "%02d-%s.png".format(GUIDE_STEPS.size, drawing)), answered)
            count++
        }
    }
    // The leaving pop-up, over a slide.
    GUIDE_STEPS.firstOrNull { it.id == "swipe" && it.chapter in chapters }?.let { step ->
        drawing = "leaving"
        drawLeave(File(out, "00-leaving.png"), framesFor(step).first())
        count++
    }
    println("$count slide states drawn to $folder")
}
