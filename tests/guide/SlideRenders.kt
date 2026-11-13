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
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
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
 * Desktop drawings of the guide's slides in the v2 look (docs/design-v2.md §2), to look at without a phone: each slide at its
 * start, its hint, a blocked try, a real mistake's chip, the pass's wash and its done state with Next; then the finale's frames,
 * the end page and the leaving pop-up. They follow the slide's real words, chips and field, from the guide's own steps, laid out
 * as the guide lays them out, at the Titan 2 Elite's size, in Inter from the repository ([V2]). Set SLIDES_PNG to a folder.
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
    val ORANGE get() = Accent.colour
    const val INK = 0xFF160A04.toInt()
    const val CREAM = 0xFFFFF1E6.toInt()
}

/** The size of the owner's phone screenshots (UAT c1-01.png), whose 18 dp gutter is 28 px. */
private const val WIDTH = V2.W
private const val HEIGHT = V2.H
private const val DP = V2.D

private fun dp(value: Number) = Math.round(value.toFloat() * DP)

private fun rgb(argb: Int, alpha: Float = 1f) = Color((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, (alpha.coerceIn(0f, 1f) * 255).toInt())
private val ink = rgb(Paint.INK)
private val cream = rgb(Paint.CREAM)
private val orange get() = rgb(Paint.ORANGE)

private fun image(): Pair<BufferedImage, Graphics2D> {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    return image to g
}

/**
 * The top bar (design v2): 48 dp, the chapter's name in cream and its place muted, in the Label style, on the left (`DELETE ·
 * 2/4`), and ✕ on the right. No ticks, and nothing else. Returns its height.
 */
private fun topBar(g: Graphics2D, chapter: GuideChapter, onOrange: Boolean, y: Int, current: String? = null): Int {
    val side = dp(V2.SIDE)
    val bar = dp(V2.TOP_BAR)
    val mid = y + bar / 2
    if (chapter != GuideChapter.End) {
        g.font = V2.label()
        val name = chapter.label.uppercase()
        val base = mid + (g.fontMetrics.ascent - g.fontMetrics.descent) / 2
        g.color = if (onOrange) V2.INK else V2.CREAM
        g.drawString(name, side, base)
        val ids = GUIDE_STEPS.filter { it.chapter == chapter }.map { it.id }
        val at = ids.indexOf(current)
        if (at >= 0 && ids.size > 1) {
            g.color = if (onOrange) V2.alpha(V2.INK, .6f) else V2.MUTED
            g.drawString(" · ${at + 1}/${ids.size}", side + g.fontMetrics.stringWidth(name), base)
        }
    }
    V2.close(g, mid, if (onOrange) V2.INK else V2.CREAM)
    return bar
}

/**
 * A slide (design v2 §2): the top bar; the content block centred in the room between it and the keys, with at least 24 dp above
 * and below: the title in Display, upper case, one line; 12 dp; the instruction in Body, muted, one line, or the chip in its place;
 * 24 dp; the field, exactly two lines; 16 dp; Skip or Next on the right. The keys at the bottom, edge to edge, at 60%.
 */
private fun draw(frame: Frame, file: File, decorated: Boolean = true, bright: Float = 1f) {
    val (image, base) = image()
    base.color = Color.BLACK
    base.fillRect(0, 0, WIDTH, HEIGHT)
    // The content on a layer of its own, so the decoration can go behind it, clear of everything the guide keeps it out of.
    val content = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    val g = content.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    val keep = ArrayList<RectF>()
    val side = dp(V2.SIDE)
    val inner = WIDTH - 2 * side
    val barBottom = V2.STATUS + topBar(g, frame.chapter, false, V2.STATUS, frame.name.substringBefore('-'))
    keep += RectF(0f, V2.STATUS.toFloat(), WIDTH.toFloat(), barBottom.toFloat())
    val keysTop = V2.keysTop
    val titleLine = V2.lineHeight(g, V2.display())
    val bodyLine = maxOf(V2.lineHeight(g, V2.body()), V2.lineHeight(g, V2.body()) + 2 * dp(7) - dp(2))
    val fieldLine = V2.lineHeight(g, V2.field())
    val pad = dp(16)
    val fieldHeight = FIELD_LINES * fieldLine + 2 * pad
    val action = dp(44)
    val blockHeight = titleLine + dp(12) + bodyLine + dp(24) + fieldHeight + dp(16) + action
    val room = keysTop - barBottom
    if (room < blockHeight + 2 * dp(24)) mark(g, "NO ROOM: block ${blockHeight + 2 * dp(24)} in $room", barBottom)
    var y = barBottom + (room - blockHeight) / 2

    // The title: Display, upper case, cream, one line, shrinking to 80% before it would wrap.
    val title = frame.title.uppercase()
    val (titleFont, over) = V2.fitted(g, title, V2.display(), inner - if (frame.passed) dp(28) else 0)
    if (over) mark(g, "TITLE OVER ONE LINE", y)
    g.font = titleFont
    g.color = V2.CREAM
    val titleBase = y + (titleLine + g.fontMetrics.ascent - g.fontMetrics.descent) / 2 - dp(1)
    g.drawString(title, side, titleBase)
    // A plain text keeps out only its words, as GuideDecorView measures it; the check after a pass is in the same view.
    keep += RectF(side.toFloat(), y.toFloat(), side + g.fontMetrics.stringWidth(title) + if (frame.passed) dp(36).toFloat() else 0f,
        (y + titleLine).toFloat())
    if (frame.passed) {
        // The check that draws in after the success line, orange: what just happened.
        val at = side + g.fontMetrics.stringWidth(title) + dp(8)
        val size = titleFont.size2D * .5f
        g.color = V2.ORANGE
        g.stroke = BasicStroke(dp(2.8f).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(Path2D.Float().apply {
            moveTo(at.toFloat(), titleBase - size * .45f)
            lineTo(at + size * .36f, titleBase - size * .08f)
            lineTo(at + size, titleBase - size * .86f)
        })
    }
    y += titleLine + dp(12)

    // The instruction in Body, muted, or the chip in its place at the same size.
    if (frame.chip != null) {
        g.font = V2.body()
        val shown = fit(g, frame.chip, inner - 2 * dp(14))
        val width = g.fontMetrics.stringWidth(shown) + 2 * dp(14)
        val chipHeight = bodyLine
        g.color = V2.DIVIDER
        g.fill(RoundRectangle2D.Float(side.toFloat(), y.toFloat(), width.toFloat(), chipHeight.toFloat(), chipHeight.toFloat(), chipHeight.toFloat()))
        g.color = V2.CREAM
        g.drawString(shown, side + dp(14), y + (chipHeight + g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
        if (g.fontMetrics.stringWidth(frame.chip) + 2 * dp(14) > inner) mark(g, "CHIP TOO LONG", y)
        keep += RectF(side.toFloat(), y.toFloat(), (side + width).toFloat(), (y + chipHeight).toFloat())
    } else {
        val (bodyFont, bodyOver) = V2.fitted(g, frame.line, V2.body(), inner)
        if (bodyOver) mark(g, "INSTRUCTION OVER ONE LINE", y)
        g.font = bodyFont
        g.color = V2.MUTED
        g.drawString(frame.line, side, y + (bodyLine + g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
        keep += RectF(side.toFloat(), y.toFloat(), (side + g.fontMetrics.stringWidth(frame.line)).toFloat(), (y + bodyLine).toFloat())
    }
    frame.hunt?.let { left ->
        // A hunt's time left: a thin bar, muted, never orange (design v2).
        g.color = V2.MUTED
        g.fillRect(side, y + bodyLine - dp(3), Math.round(inner * left), dp(3))
    }
    y += bodyLine + dp(24)

    // The field: the surface, 16 dp corners and padding, exactly two lines; typed text cream, the rest muted.
    g.color = V2.SURFACE
    g.fill(RoundRectangle2D.Float(side.toFloat(), y.toFloat(), inner.toFloat(), fieldHeight.toFloat(), dp(32).toFloat(), dp(32).toFloat()))
    keep += RectF(side.toFloat(), y.toFloat(), (WIDTH - side).toFloat(), (y + fieldHeight).toFloat())
    g.font = V2.field()
    drawField(g, frame, side + pad, y + pad, inner - 2 * pad, fieldLine)
    y += fieldHeight + dp(16)

    // Skip, quiet, once there's been a hint; Next, an orange pill, once the slide is passed. Right-aligned, under the field. Its
    // place is kept clear even before it shows, as GuideDecorView keeps an invisible view's room.
    g.font = V2.label()
    val actionWide = if (frame.passed) g.fontMetrics.stringWidth("NEXT") + 2 * dp(20) else g.fontMetrics.stringWidth("SKIP") + 2 * dp(16)
    keep += RectF((WIDTH - side - actionWide).toFloat(), y.toFloat(), (WIDTH - side).toFloat(), (y + action).toFloat())
    if (frame.passed) {
        val wide = g.fontMetrics.stringWidth("NEXT") + 2 * dp(20)
        val left = WIDTH - side - wide
        g.color = V2.ORANGE
        g.fill(RoundRectangle2D.Float(left.toFloat(), y.toFloat(), wide.toFloat(), action.toFloat(), action.toFloat(), action.toFloat()))
        g.color = V2.INK
        g.drawString("NEXT", left + dp(20), y + (action + g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
    } else if (frame.chip != null) {
        val wide = g.fontMetrics.stringWidth("SKIP") + 2 * dp(16)
        g.color = V2.MUTED
        g.drawString("SKIP", WIDTH - side - wide + dp(16), y + (action + g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
    }
    frame.panel?.let { panel ->
        // A panel is open: the drawing of the keys hides, so it never covers the real panel. Only a note, for review.
        V2.note(g, "render note: $panel is open; the drawn keys hide under it", side, keysTop + dp(24))
    }
    if (frame.panel == null) {
        drawKeys(g, keysTop, side, frame)
        keep += RectF(0f, keysTop.toFloat(), WIDTH.toFloat(), keysTop + V2.KEYS.height)
    }
    if (frame.wash > 0f) {
        g.color = rgb(Paint.ORANGE, .85f * frame.wash)
        g.fillRect(0, 0, WIDTH, HEIGHT)
    }
    V2.note(g, "render note: the keyboard's own bar is here", side, HEIGHT - V2.BAR / 2)
    g.dispose()
    val slide = frame.name.substringBefore('-')
    if (decorated) base.drawImage(decorLayer(slide, GuideDecor.stillMillis(slide), keep, false, bright), 0, 0, null)
    base.drawImage(content, 0, 0, null)
    base.dispose()
    ImageIO.write(image, "png", file)
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
                // The gap: an orange box its symbol's width, and the text after it moves over to make room (v5.3), as the
                // field's GapRoom does.
                val symbol = GUIDE_STEPS.firstOrNull { it.id == frame.name.substringBefore('-') }?.symbol ?: "M"
                val wide = maxOf(g.fontMetrics.stringWidth(symbol), g.fontMetrics.stringWidth("n"))
                g.color = rgb(Paint.ORANGE, .35f)
                g.fillRoundRect(x + dp(3), top + row * line + dp(2), wide, line - dp(4), dp(4), dp(4))
                g.color = orange
                g.fillRect(x + dp(3), top + (row + 1) * line - dp(3), wide, dp(2))
                if (offset == shown.start && shown.start == shown.end) g.fillRect(x - dp(1), top + row * line + dp(2), dp(2), line - dp(4))
                x += wide + dp(6)
            }
            if (offset >= to) break
            val char = all[offset].toString().replace("\n", "")
            val w = g.fontMetrics.stringWidth(char)
            val aim = shown.target
            if (aim != null && offset in aim) {
                // The word to act on, underlined in orange (design v2).
                g.color = orange
                g.fillRect(x, top + (row + 1) * line - dp(3), w, dp(3))
            }
            if (offset in shown.start until shown.end) {
                g.color = rgb(Paint.ORANGE, .45f)
                g.fillRect(x, top + row * line, w, line)
            }
            g.color = if (offset < frame.field.length) cream else V2.MUTED
            g.drawString(char, x, base)
            if (offset == shown.start && shown.start == shown.end && (offset != shown.gap || frame.passed)) {
                g.color = orange
                g.fillRect(x - dp(1), top + row * line + dp(2), dp(2), line - dp(4))
            }
            x += w
        }
        if (shown.start == shown.end && shown.start == to && to == all.length && (shown.start != shown.gap || frame.passed)) {
            g.color = orange
            g.fillRect(x, top + row * line + dp(2), dp(2), line - dp(4))
        }
    }
}

private const val FIELD_LINES = 2
/** How much stronger the brightened renders draw the decoration. */
private const val BRIGHT = 5f

/** The drawing of the keys (design v2), lit for a spot, steady for a held key, with corner symbols on the slides about them. */
private fun drawKeys(g: Graphics2D, top: Int, side: Int, frame: Frame?) {
    val id = frame?.name?.substringBefore('-')
    val corners = id == "alt" || id == "holdsym"
    V2.keys(g, top, lit = setOfNotNull(frame?.spot), held = frame?.held?.toSet() ?: emptySet(), hints = frame?.labels,
        corners = corners, zones = frame?.zones == true)
    if (corners) V2.note(g, "render note: the corner symbols here are approximate; the phone reads its own", side, top - dp(6))
    // Each key's label must fit in its key.
    frame?.labels?.values?.forEach { hint ->
        g.font = V2.keyFont(minOf(V2.KEYS.key, V2.KEYS.keyHeight) * .3f)
        if (g.fontMetrics.stringWidth(hint) > V2.KEYS.key * .86f) mark(g, "KEY HINT TOO LONG", top)
    }
}

/** The held delete's zones, as DeleteSwipe.kt has them (it isn't compiled for these checks): the same numbers, copied. */
private const val PAUSE_KEYS_COPY = .8f
private const val REPEAT_LONG_KEYS_COPY = 5.5f

/** The end's words that live in GuideActivity, which isn't compiled for these checks: the same text, copied. */
private const val SHARE_PLAIN = "Off until you agree. Then we get examples of your swipes to make swiping better. Change it " +
    "any time in Settings > Share swipes."
private const val SHARE_ANSWERED = ""

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

/** The finale's box on the end screen: under the top bar, down to the grass at the bottom edge, as GuideActivity measures it. */
private val FINALE_TOP = (V2.STATUS + dp(V2.TOP_BAR) + dp(16)).toFloat()
private val FINALE_BOTTOM = HEIGHT - GuideFinale.GRASS_DP * DP

private fun finaleMeasure(g: Graphics2D) =
    GuideFinale.Measure { text, size, bold -> g.getFontMetrics(Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(size)).stringWidth(text).toFloat() }

/** Draws the finale at [at] ms, on the end's orange, from [GuideFinale]: the same frames as the phone, the name where the end's block puts it. */
private fun drawFinale(g: Graphics2D, at: Long, answered: Boolean = false) {
    val measure = finaleMeasure(g)
    val nameBase = endPlace(g, answered).first
    for (piece in GuideFinale.frame(at, SUMMARY_LINES, WIDTH.toFloat(), FINALE_TOP, FINALE_BOTTOM, measure, DP, nameBase)) when (piece) {
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
            // The whole mark, or only its T or its K, as GuideFinaleView clips it.
            val clip = when (piece.part) {
                GuideFinale.MarkPart.T -> GuideFinale.T_CLIP
                GuideFinale.MarkPart.K -> GuideFinale.K_CLIP
                GuideFinale.MarkPart.Whole -> null
            }?.let { points ->
                java.awt.geom.Area(transform.createTransformedShape(Path2D.Float().apply {
                    moveTo(points[0], points[1])
                    for (at in 2 until points.size step 2) lineTo(points[at], points[at + 1])
                    closePath()
                }))
            }
            for ((data, colour, alpha) in listOf(Triple(GuideFinale.MARK_PATH, Paint.INK, piece.alpha), Triple(GuideFinale.LINE_PATH, Paint.CREAM, piece.line))) {
                if (alpha <= 0f) continue
                val path = Path2D.Float()
                for (shape in GuideFinale.shapes(data)) {
                    shape.forEachIndexed { index, (x, y) -> if (index == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    path.closePath()
                }
                val area = java.awt.geom.Area(transform.createTransformedShape(path))
                if (clip != null) area.intersect(clip)
                g.color = rgb(colour, alpha)
                g.fill(area)
            }
        }
    }
}

/** A frame of the finale, alone on the end's orange, as it plays before the buttons come in. */
private fun drawFinaleFrame(file: File, at: Long, bright: Float = 1f) {
    val (image, g) = image()
    g.color = orange
    g.fillRect(0, 0, WIDTH, HEIGHT)
    // The grass grows from the bottom edge as the finale plays; the buttons wait unseen, so only the words keep it down.
    g.drawImage(decorLayer(GuideDecor.FINALE, at, endKeepOut(g, false).dropLast(1), true, bright), 0, 0, null)
    topBar(g, GuideChapter.End, true, V2.STATUS)
    drawFinale(g, at)
    g.dispose()
    ImageIO.write(image, "png", file)
}

/**
 * The end page, once the finale is over: the logo and its name at the top, then the big button to go type, the share switch with
 * its line, and the way to start again. The header has only the boring guide and ✕. [answered] draws it after the share offer
 * has an answer.
 */
private fun drawEnd(file: File, answered: Boolean, bright: Float = 1f) {
    val (image, g) = image()
    g.color = orange
    g.fillRect(0, 0, WIDTH, HEIGHT)
    g.drawImage(decorLayer(GuideDecor.FINALE, GuideFinale.total(SUMMARY_LINES).toLong() + 20_000L, endKeepOut(g, answered), true, bright), 0, 0, null)
    val side = dp(V2.SIDE)
    val inner = WIDTH - 2 * side
    topBar(g, GuideChapter.End, true, V2.STATUS)
    // The buttons, from the bottom up, as the column lays them out under the spacer; the finale ends 20 dp above them.
    g.font = V2.title()
    val goHeight = g.fontMetrics.ascent + g.fontMetrics.descent + 2 * dp(16)
    val shareHeight = goHeight
    g.font = V2.body()
    val againHeight = g.fontMetrics.ascent + g.fontMetrics.descent + 2 * dp(12)
    val under = wrap(g, if (answered) SHARE_ANSWERED else SHARE_PLAIN, inner)
    if (under.size > 4) mark(g, "SHARE TEXT OVER FOUR LINES", HEIGHT - dp(60))
    val underHeight = under.size * g.fontMetrics.height
    val boring = dp(44)
    var y = endTop(g, answered)
    drawFinale(g, GuideFinale.total(SUMMARY_LINES), answered)
    if (y + endButtonsHeight(g, answered) > FINALE_BOTTOM) mark(g, "BUTTONS IN THE GRASS", y)
    // Every text on the end's orange is ink (design v2): the filled button is cream, with ink words. With Orange accents off the
    // end is cream, and the button is Accent's raised cream, so it still shows.
    g.font = V2.title()
    g.color = rgb(Accent.raised)
    g.fill(RoundRectangle2D.Float(side.toFloat(), y.toFloat(), inner.toFloat(), goHeight.toFloat(), goHeight.toFloat(), goHeight.toFloat()))
    g.color = ink
    g.drawString("Go type something", WIDTH / 2 - g.fontMetrics.stringWidth("Go type something") / 2, y + (goHeight + g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
    y += goHeight + dp(12)
    g.color = ink
    g.stroke = BasicStroke(dp(1).toFloat())
    g.draw(RoundRectangle2D.Float(side.toFloat(), y.toFloat(), inner.toFloat(), shareHeight.toFloat(), shareHeight.toFloat(), shareHeight.toFloat()))
    val share = "Share my swipes: off"
    g.drawString(share, WIDTH / 2 - g.fontMetrics.stringWidth(share) / 2, y + (shareHeight + g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
    y += shareHeight + dp(4)
    g.font = V2.body()
    g.color = ink
    for (line in under) {
        y += g.fontMetrics.height
        g.drawString(line, WIDTH / 2 - g.fontMetrics.stringWidth(line) / 2, y - g.fontMetrics.descent)
    }
    y += dp(12)
    // Start again: a real button, outlined, not fine print (v5.2).
    g.font = V2.body().deriveFont(java.awt.Font.BOLD)
    g.color = rgb(Paint.INK, .6f)
    g.draw(RoundRectangle2D.Float(side.toFloat(), y.toFloat(), inner.toFloat(), againHeight.toFloat(), againHeight.toFloat(), againHeight.toFloat()))
    g.color = ink
    g.drawString("Start the guide again", WIDTH / 2 - g.fontMetrics.stringWidth("Start the guide again") / 2, y + (againHeight + g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
    y += againHeight + dp(4)
    // The way to the boring guide: a quiet line under the buttons, never in the top bar (design v2).
    g.font = V2.label()
    val boringText = "THE BORING GUIDE"
    g.drawString(boringText, WIDTH / 2 - g.fontMetrics.stringWidth(boringText) / 2, y + (boring + g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
    g.dispose()
    ImageIO.write(image, "png", file)
}

/** How tall the end's buttons are, as the column lays them out: the two big ones, the share line, Start again and the boring guide. */
private fun endButtonsHeight(g: Graphics2D, answered: Boolean): Int {
    val inner = WIDTH - 2 * dp(V2.SIDE)
    g.font = V2.title()
    val goHeight = g.fontMetrics.ascent + g.fontMetrics.descent + 2 * dp(16)
    g.font = V2.body()
    val againHeight = g.fontMetrics.ascent + g.fontMetrics.descent + 2 * dp(12)
    val underHeight = wrap(g, if (answered) SHARE_ANSWERED else SHARE_PLAIN, inner).size * g.fontMetrics.height
    return goHeight + dp(12) + goHeight + dp(4) + underHeight + dp(12) + againHeight + dp(4) + dp(44)
}

/** The end's block, as GuideActivity lays it out with [GuideFinale.endBlock]: the name's baseline and the buttons' top. */
private fun endPlace(g: Graphics2D, answered: Boolean): Pair<Float, Float> {
    val measure = finaleMeasure(g)
    return GuideFinale.endBlock(FINALE_TOP, FINALE_BOTTOM, endButtonsHeight(g, answered).toFloat(), GuideFinale.nameSize(WIDTH.toFloat(), measure, DP), DP)
}

/** Where the end's buttons start. */
private fun endTop(g: Graphics2D, answered: Boolean) = Math.round(endPlace(g, answered).second)

/** What the end keeps the grass out of: the top bar, the finale's words and the buttons. */
private fun endKeepOut(g: Graphics2D, answered: Boolean) = listOf(
    RectF(0f, V2.STATUS.toFloat(), WIDTH.toFloat(), (V2.STATUS + dp(V2.TOP_BAR)).toFloat()),
    RectF(0f, FINALE_TOP, WIDTH.toFloat(), FINALE_BOTTOM),
    RectF(dp(V2.SIDE).toFloat(), endTop(g, answered).toFloat(), (WIDTH - dp(V2.SIDE)).toFloat(), (endTop(g, answered) + endButtonsHeight(g, answered)).toFloat()),
)

/** The real decoration, from GuideDecor, as the phone draws it; or [bright]er, to judge the drawing. */
private val DECOR by lazy {
    GuideDecor(Typeface(Font.createFont(Font.TRUETYPE_FONT, File("android/src/main/res/font/inter_bold.otf"))))
}

/**
 * [slide]'s decoration at [millis], on a layer of its own, in the guide's root (the screen down to the keyboard's bar), clear of
 * [keep], in ink when [onOrange]. [bright] multiplies its strength, for a render that shows the drawing plainly.
 */
private fun decorLayer(slide: String, millis: Long, keep: List<RectF>, onOrange: Boolean, bright: Float): BufferedImage {
    val layer = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    // Nothing under the status bar, as GuideDecorView. The end has no keyboard, so its decoration reaches the bottom edge.
    val bottom = if (slide == GuideDecor.FINALE) HEIGHT else HEIGHT - V2.BAR
    DECOR.draw(Canvas(layer), slide, millis, RectF(0f, V2.STATUS.toFloat(), WIDTH.toFloat(), bottom.toFloat()), keep, onOrange, DP)
    if (bright != 1f) for (y in 0 until HEIGHT) for (x in 0 until WIDTH) {
        val argb = layer.getRGB(x, y)
        val alpha = argb ushr 24
        if (alpha > 0) layer.setRGB(x, y, (minOf(255, Math.round(alpha * bright)) shl 24) or (argb and 0xFFFFFF))
    }
    return layer
}

/**
 * "Leaving already?" (design v2): a card of the surface over the dimmed slide, its title in the Title style, and Keep going in
 * orange, then Start again and Leave, outlined.
 */
private fun drawLeave(file: File, under: Frame) {
    // No decoration under the pop-up: it fades away as the pop-up opens.
    draw(under, file, decorated = false)
    val image = ImageIO.read(file)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    g.color = rgb(0xFF000000.toInt(), .88f)
    g.fillRect(0, 0, WIDTH, HEIGHT)
    val margin = dp(V2.SIDE)
    val width = WIDTH - 2 * margin
    val pad = dp(24)
    g.font = V2.body()
    val body = wrap(g, "You haven't finished the guide. Leave anyway?", width - 2 * pad)
    val bodyHeight = body.size * g.fontMetrics.height
    val button = g.fontMetrics.ascent + g.fontMetrics.descent + 2 * dp(12)
    g.font = V2.title()
    val titleHeight = g.fontMetrics.ascent + g.fontMetrics.descent
    val height = pad + titleHeight + dp(12) + bodyHeight + dp(24) + 3 * button + 2 * dp(8) + pad
    val top = (HEIGHT - height) / 2
    g.color = V2.SURFACE
    val card = RoundRectangle2D.Float(margin.toFloat(), top.toFloat(), width.toFloat(), height.toFloat(), dp(32).toFloat(), dp(32).toFloat())
    g.fill(card)
    g.color = V2.DIVIDER
    g.stroke = BasicStroke(dp(1).toFloat())
    g.draw(card)
    var y = top + pad
    g.color = V2.CREAM
    g.drawString("Leaving already?", margin + pad, y + g.fontMetrics.ascent)
    y += titleHeight + dp(12)
    g.font = V2.body()
    g.color = V2.MUTED
    for (line in body) {
        y += g.fontMetrics.height
        g.drawString(line, margin + pad, y - g.fontMetrics.descent)
    }
    y += dp(24)
    val buttonFont = V2.body().deriveFont(java.awt.Font.BOLD)
    for ((index, label) in listOf("Keep going", "Start again", "I already know everything").withIndex()) {
        g.font = buttonFont
        val shape = RoundRectangle2D.Float((margin + pad).toFloat(), y.toFloat(), (width - 2 * pad).toFloat(), button.toFloat(), button.toFloat(), button.toFloat())
        if (index == 0) {
            g.color = V2.ORANGE
            g.fill(shape)
            g.color = V2.INK
        } else {
            g.color = V2.DIVIDER
            g.draw(shape)
            g.color = V2.CREAM
        }
        g.drawString(label, WIDTH / 2 - g.fontMetrics.stringWidth(label) / 2, y + (button + g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
        y += button + dp(8)
    }
    g.dispose()
    ImageIO.write(image, "png", file)
}

/** Draws every slide in [chapters], in each of its states, to PNGs in [folder], and the finale's frames and the end. */
internal fun drawSlides(folder: String, chapters: Set<GuideChapter>) {
    val out = File(folder).apply { mkdirs() }
    // The decoration brightened, to judge its drawing: the slides' starts, the grass and the end.
    val bright = File(out, "bright").apply { mkdirs() }
    var count = 0
    for ((index, step) in GUIDE_STEPS.withIndex()) {
        if (step.chapter !in chapters || step.chapter == GuideChapter.End) continue
        for (frame in framesFor(step)) {
            drawing = frame.name
            draw(frame, File(out, "%02d-%s.png".format(index + 1, frame.name)))
            count++
            if (frame.name.endsWith("-1-start")) draw(frame, File(bright, "%02d-%s.png".format(index + 1, frame.name)), bright = BRIGHT)
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
            if (name == "d-lit" || name == "j-titankeys") drawFinaleFrame(File(bright, "%02d-finale-%s.png".format(GUIDE_STEPS.size, name)), at, BRIGHT)
            count++
        }
        for (answered in listOf(false, true)) {
            drawing = "end-" + if (answered) "answered" else "asking"
            drawEnd(File(out, "%02d-%s.png".format(GUIDE_STEPS.size, drawing)), answered)
            if (!answered) drawEnd(File(bright, "%02d-%s.png".format(GUIDE_STEPS.size, drawing)), answered, BRIGHT)
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
