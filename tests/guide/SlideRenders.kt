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
        "clown" -> CLOWN_HUNT.slow(37)
        "symgroups" -> groupsPraise(1)
        else -> step.praise
    }
    frames += frame("4-pass-wash", step.done, praise, passed = true, from = passed).copy(wash = .6f, steer = frames.last().steer.let {
        if (it == SteerAim.None) it else SteerAim.Entered })
    frames += frame("4-passed", step.done, praise, passed = true, from = passed).copy(steer = if (step.id == "steer") SteerAim.Entered else SteerAim.None)
    if (step.id == "hold") frames += frame("5-frozen", step.done, regretsFinal(11), passed = true, from = passed)
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

/** The top bar: the chapters' segments at a fixed width, the chapter's name in the room of the longest, the pill, and ✕. */
private fun topBar(g: Graphics2D, chapter: GuideChapter, pillText: String?, filled: Boolean, onOrange: Boolean, y: Int) {
    val side = dp(Layout.KEY_SIDE_DP)
    val bar = dp(32)
    val strong = if (onOrange) ink else cream
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(13))
    val chapters = GuideChapter.entries.filter { it != GuideChapter.End }
    val labelRoom = chapters.maxOf { g.fontMetrics.stringWidth(it.label.uppercase()) }
    val pillWidth = (pillText?.let { g.fontMetrics.stringWidth(it) } ?: g.fontMetrics.stringWidth("Skip")) + dp(20)
    val closeWidth = dp(32)
    val segmentsEnd = WIDTH - side - closeWidth - dp(4) - pillWidth - dp(10) - labelRoom - dp(10)
    if (chapter != GuideChapter.End) {
        val segment = (segmentsEnd - side) / chapters.size
        val current = chapters.indexOf(chapter)
        for ((index, _) in chapters.withIndex()) {
            val x = side + index * segment
            g.color = rgb(Paint.ORANGE, .25f)
            g.fill(RoundRectangle2D.Float(x.toFloat(), y + bar / 2f - dp(2), segment - dp(3f).toFloat(), dp(4).toFloat(), dp(4).toFloat(), dp(4).toFloat()))
            val fill = when {
                index < current -> 1f
                index == current -> .45f
                else -> 0f
            }
            if (fill > 0f) {
                g.color = orange
                g.fill(RoundRectangle2D.Float(x.toFloat(), y + bar / 2f - dp(2), (segment - dp(3)) * fill, dp(4).toFloat(), dp(4).toFloat(), dp(4).toFloat()))
            }
        }
        g.color = rgb(Paint.CREAM, .55f)
        g.drawString(chapter.label.uppercase(), segmentsEnd + dp(10), y + bar / 2 + g.fontMetrics.ascent / 2 - dp(1))
    }
    var x = segmentsEnd + dp(10) + labelRoom + dp(10)
    if (pillText != null) {
        val pill = RoundRectangle2D.Float(x.toFloat(), y + dp(3).toFloat(), pillWidth.toFloat(), bar - dp(6).toFloat(), bar.toFloat(), bar.toFloat())
        if (filled) {
            g.color = orange
            g.fill(pill)
            g.color = ink
        } else {
            g.color = if (onOrange) ink else rgb(Paint.CREAM, .6f)
            g.stroke = BasicStroke(dp(1).toFloat())
            g.draw(pill)
        }
        g.drawString(pillText, x + dp(10), y + bar / 2 + g.fontMetrics.ascent / 2 - dp(1))
    }
    x += pillWidth + dp(4)
    g.color = strong
    g.stroke = BasicStroke(dp(1.6f).toFloat())
    val c = dp(6)
    g.drawLine(x + bar / 2 - c, y + bar / 2 - c, x + bar / 2 + c, y + bar / 2 + c)
    g.drawLine(x + bar / 2 + c, y + bar / 2 - c, x + bar / 2 - c, y + bar / 2 + c)
}

private fun draw(frame: Frame, file: File) {
    val (image, g) = image()
    g.color = ink
    g.fillRect(0, 0, WIDTH, HEIGHT)
    val side = dp(Layout.KEY_SIDE_DP)
    val inner = WIDTH - 2 * side
    var y = dp(40)
    val bar = dp(32)
    // Passed, the small pill steps aside: the big Next above the keys is the one Next.
    topBar(g, frame.chapter, if (frame.passed) null else "Skip", false, false, y)
    y += bar + dp(18)
    // The title: one line at one size, with the orange check once passed.
    val titleFont = calibrated(g, Font.BOLD, "Type like a normal person.", 540 * 32 / 28)
    g.font = titleFont
    val titleLine = dp(40)
    g.color = cream
    val titleShown = fit(g, frame.title, inner)
    g.drawString(titleShown, side, y + titleLine - dp(9))
    if (frame.passed) {
        val at = side + g.fontMetrics.stringWidth(titleShown + " ")
        val size = titleFont.size2D * .5f
        val base = (y + titleLine - dp(9)).toFloat()
        g.color = orange
        g.stroke = BasicStroke(dp(2.8f).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(Path2D.Float().apply {
            moveTo(at.toFloat(), base - size * .45f)
            lineTo(at + size * .36f, base - size * .08f)
            lineTo(at + size, base - size * .86f)
        })
    }
    if (g.fontMetrics.stringWidth(frame.title) + (if (frame.passed) dp(20) else 0) > inner) mark(g, "TITLE TOO LONG", y)
    y += titleLine + dp(18)
    // The instruction, or a chip over it.
    val bodyFont = calibrated(g, Font.PLAIN, "Back for a refresher? Pick a chapter. No judgement. Some judgement.", 875 * 18 / 16)
    val lineHeight = dp(25) + 2 * dp(3)
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
    frame.hunt?.let { left ->
        g.color = orange
        g.fillRect(side, y + lineHeight - dp(3), Math.round(inner * left), dp(3))
    }
    y += lineHeight + dp(18)
    // The field: three lines, on ink, with what's typed in cream and the ghost after it in grey.
    val fieldFont = calibrated(g, Font.PLAIN, "Tihs keybaord fxies yuor typos, sp yuo cna kepe", 807 * 21 / 18)
    g.font = fieldFont
    val pad = dp(12)
    val fieldLine = g.fontMetrics.height
    val fieldHeight = FIELD_LINES * fieldLine + 2 * pad
    g.color = blend(Paint.INK, Paint.CREAM, .08f)
    g.fill(RoundRectangle2D.Float(side.toFloat(), y.toFloat(), inner.toFloat(), fieldHeight.toFloat(), dp(12).toFloat(), dp(12).toFloat()))
    drawField(g, frame, side + pad, y + pad, inner - 2 * pad, fieldLine)
    y += fieldHeight
    // The keys at the bottom, above the keyboard's own bar; Next's place, kept on every slide, halfway between.
    val keysTop = HEIGHT - BAR_ROOM - 4 * keyRow()
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(20))
    val nextHeight = g.fontMetrics.height + dp(24)
    val gap = (keysTop - y - nextHeight) / 2
    if (gap < dp(8)) mark(g, "NO ROOM BETWEEN FIELD AND KEYS", keysTop)
    frame.panel?.let { panel ->
        // Only a note on the drawing, for review: the phone shows the real panel below the keys, never these words.
        g.color = Color(0x66, 0xCC, 0xFF)
        g.font = Font(Font.SANS_SERIF, Font.ITALIC, dp(11))
        g.drawString("render note: $panel is open below the keys", side, HEIGHT - dp(12))
    }
    if (frame.passed) {
        // The calm done state: Next, big, full width, orange, in its place.
        g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(20))
        val height = nextHeight
        val top = y + gap
        g.color = orange
        g.fill(RoundRectangle2D.Float(side.toFloat(), top.toFloat(), inner.toFloat(), height.toFloat(), dp(24).toFloat(), dp(24).toFloat()))
        g.color = ink
        g.drawString("Next", WIDTH / 2 - g.fontMetrics.stringWidth("Next") / 2, top + height / 2 + g.fontMetrics.ascent / 2 - dp(2))
        if (top < y + dp(8)) mark(g, "NEXT OVERLAPS THE FIELD", top)
    }
    drawKeys(g, keysTop, side, frame)
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

/** The drawing of the keys: the real layout only, lit for a spot, steady for a held key, with a hint under a letter. */
/** The drawing's row height: its height is .44 of its width on the slides, as GuideActivity's KEYS_ASPECT. */
private fun keyRow() = Math.round((WIDTH - 2 * dp(Layout.KEY_SIDE_DP)) * .44f / 4)
private const val FIELD_LINES = 3
/** Room at the bottom for the keyboard's own bar, under the drawing. */
private val BAR_ROOM = dp(60)

private fun drawKeys(g: Graphics2D, top: Int, side: Int, frame: Frame?) {
    val width = (WIDTH - 2 * side) / 10f
    val row = keyRow()
    for ((index, keys) in Layout.rows.withIndex()) {
        val y = top + index * row
        if (index < 3) for ((column, key) in keys.withIndex()) {
            val x = side + column * width
            val hint = frame?.labels?.get(key)
            val lit = frame != null && (key == frame.spot || hint != null)
            val held = frame != null && key in frame.held
            g.color = when {
                lit -> orange
                held -> rgb(Paint.ORANGE, .8f)
                else -> rgb(Paint.CREAM, .09f)
            }
            g.fill(RoundRectangle2D.Float(x + dp(2), y + dp(2).toFloat(), width - dp(4), row - dp(4).toFloat(), dp(8).toFloat(), dp(8).toFloat()))
            g.color = if (lit || held) ink else rgb(Paint.CREAM, .6f)
            g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(19))
            val text = if (key.length == 1) key else key.take(3)
            val lift = if (hint != null) dp(7) else 0
            g.drawString(text, x + width / 2 - g.fontMetrics.stringWidth(text) / 2, y + row / 2f + dp(7) - lift)
            if (hint != null) {
                g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(10))
                if (g.fontMetrics.stringWidth(hint) > width - dp(6)) mark(g, "KEY HINT TOO LONG", y)
                g.drawString(hint, x + width / 2 - g.fontMetrics.stringWidth(hint) / 2, y + row - dp(8).toFloat())
            }
        }
    }
    if (frame?.zones == true) {
        // The held delete's zones: back on Backspace pauses; between F and G is top speed.
        val rowTop = top + row
        val pauseX = side + (WIDTH - 2 * side) * (.95f - PAUSE_KEYS_COPY / 10f)
        g.color = rgb(Paint.CREAM, .2f)
        g.fill(RoundRectangle2D.Float(pauseX, rowTop + dp(2).toFloat(), side + (WIDTH - 2 * side) * .95f - pauseX, row - dp(4).toFloat(), dp(8).toFloat(), dp(8).toFloat()))
        val lineX = side + (WIDTH - 2 * side) * (.95f - REPEAT_LONG_KEYS_COPY / 10f)
        g.color = orange
        g.fillRect(Math.round(lineX) - dp(1.5f), rowTop - dp(2), dp(3), row + dp(4))
        g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(10))
        g.drawString("top speed", lineX - g.fontMetrics.stringWidth("top speed") / 2, rowTop - dp(4).toFloat())
        g.color = rgb(Paint.CREAM, .85f)
        g.drawString("pause", pauseX + dp(4), rowTop + row - dp(6).toFloat())
    }
}

/** The held delete's zones, as DeleteSwipe.kt has them (it isn't compiled for these checks): the same numbers, copied. */
private const val PAUSE_KEYS_COPY = .8f
private const val REPEAT_LONG_KEYS_COPY = 5.5f

/** The end's words that live in GuideActivity, which isn't compiled for these checks: the same text, copied. */
private const val SHARE_PLAIN = "Off until you agree. Then we get examples of your swipes to make swiping better.\n" +
    "Trust me bro. You can change this any time in Settings > Share swipes"
private const val SHARE_ANSWERED = "Trust me bro. You can change this any time in Settings > Share swipes"

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

/** The finale's box on the end screen: under the top bar, to where the buttons start. */
private val FINALE_TOP = (dp(40) + dp(32) + dp(16)).toFloat()
private const val FINALE_BOTTOM = HEIGHT * .68f

/** Draws the finale at [at] ms, on the end's orange, from [GuideFinale]: the same frames as the phone. */
private fun drawFinale(g: Graphics2D, at: Long) {
    val measure = GuideFinale.Measure { text, size, bold -> g.getFontMetrics(Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(size)).stringWidth(text).toFloat() }
    for (piece in GuideFinale.frame(at, SUMMARY_LINES, WIDTH.toFloat(), FINALE_TOP, FINALE_BOTTOM, measure)) when (piece) {
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
    drawFinale(g, GuideFinale.total(SUMMARY_LINES))
    // The buttons, from the bottom up, as the column lays them out under the spacer.
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(24))
    val goHeight = g.fontMetrics.height + dp(28)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, dp(20))
    val shareHeight = g.fontMetrics.height + dp(28)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, dp(15))
    val under = wrap(g, if (answered) SHARE_ANSWERED else SHARE_PLAIN, inner)
    if (under.size > 2) mark(g, "SHARE TEXT OVER TWO LINES", HEIGHT - dp(60))
    val underHeight = under.size * g.fontMetrics.height
    val again = g.fontMetrics.height + dp(14)
    var y = HEIGHT - dp(16) - again - underHeight - dp(4) - shareHeight - dp(12) - goHeight
    if (y < FINALE_BOTTOM) mark(g, "BUTTONS OVERLAP THE LOGO BY ${(FINALE_BOTTOM - y).toInt()} PX", y)
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
        g.drawString(line, WIDTH / 2 - g.fontMetrics.stringWidth(line) / 2, y)
    }
    y += dp(14) + g.fontMetrics.height
    g.drawString("Start the guide again", WIDTH / 2 - g.fontMetrics.stringWidth("Start the guide again") / 2, y)
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
    println("$count slide states drawn to $folder")
}
