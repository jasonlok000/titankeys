package com.atlas.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The guide's brand colours. They are the values in lib/brand.ts and the app icon. */
internal object GuideColours {
    const val ORANGE = 0xFFEF804E.toInt()
    /** Only as text, or a button, on orange: no background is ink any more (owner, v5.3: pure black and orange). */
    const val INK = 0xFF160A04.toInt()
    const val CREAM = 0xFFFFF1E6.toInt()
    /** Every background (v5.3). */
    const val BLACK = 0xFF000000.toInt()
    /** Secondary text, and hairlines: lib/brand.ts's MUTED and DIVIDER. */
    const val MUTED = 0xFF99918A.toInt()
    const val DIVIDER = 0xFF2A2623.toInt()
    /** Fields and cards, and the drawn keys at half strength (design v2). */
    const val SURFACE = 0xFF161210.toInt()
}

/**
 * Ink's font, Inter, for everything the guide and its screens draw (owner, v5.3: no Roboto): Regular, Medium, and the real Bold
 * for titles and bold text, from the official Inter 4.1 release (OFL). Loaded once, from any context.
 */
internal object GuideFonts {
    @Volatile private var regular: android.graphics.Typeface? = null
    @Volatile private var medium: android.graphics.Typeface? = null
    @Volatile private var heavy: android.graphics.Typeface? = null

    fun load(context: Context) {
        if (heavy != null) return
        val res = context.applicationContext.resources
        regular = runCatching { res.getFont(R.font.inter_regular) }.getOrNull()
        medium = runCatching { res.getFont(R.font.inter_medium) }.getOrNull()
        heavy = runCatching { res.getFont(R.font.inter_bold) }.getOrNull()
            ?: android.graphics.Typeface.create(medium ?: android.graphics.Typeface.DEFAULT, 700, false)
    }

    fun regular(context: Context) = load(context).let { regular ?: android.graphics.Typeface.DEFAULT }
    fun medium(context: Context) = load(context).let { medium ?: android.graphics.Typeface.DEFAULT }
    fun heavy(context: Context) = load(context).let { heavy ?: android.graphics.Typeface.DEFAULT_BOLD }
    /** The same, once [load] has run: for code with no context at hand. */
    val loadedMedium get() = medium ?: android.graphics.Typeface.DEFAULT
    val loadedHeavy get() = heavy ?: android.graphics.Typeface.DEFAULT_BOLD
}

/** Time for the guide's motion. It follows Android's animator duration setting, and with animations off every motion ends at once. */
internal object GuideMotion {
    val enabled get() = ValueAnimator.areAnimatorsEnabled() && ValueAnimator.getDurationScale() > 0f

    fun now() = SystemClock.uptimeMillis()

    /** How far a motion that began at [start] has run, from 0 to 1. */
    fun progress(start: Long, millis: Long): Float {
        if (!enabled) return 1f
        return ((now() - start) / (millis * ValueAnimator.getDurationScale())).coerceIn(0f, 1f)
    }

    fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

    fun blend(from: Int, to: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val a = from shr shift and 0xFF
            val b = to shr shift and 0xFF
            return (a + (b - a) * amount + .5f).toInt().coerceIn(0, 255) shl shift
        }
        return channel(24) or channel(16) or channel(8) or channel(0)
    }

    fun faded(colour: Int, alpha: Float) = colour and 0x00FFFFFF or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
}

/**
 * A drawing of the Titan 2 Elite's keyboard, from [Layout]. The keys light up and the trail follows your real finger, and a
 * ghost finger shows each gesture before you try it. Everything that sets how it looks is in the numbers at the end of this file.
 */
internal class GuideKeyboardView(context: Context) : View(context) {
    private class Key(val name: String, val letter: Boolean) {
        val box = RectF()
        /** How lit the key is, from 0 to 1. It fades by itself. */
        var heat = 0f
        /** How lit the key is because a finger is over it, from 0 to 1. It eases in and out, so the light never flickers. */
        var glow = 0f
        /** What the key shows for a moment after its letter became a symbol. */
        var symbol: String? = null
        var flipStart = -1L
    }

    private val keys = Layout.rows.flatMap { row -> row.map { Key(it, Layout.isLetter(it)) } }
    private val byName = keys.associateBy { it.name }
    /**
     * Where the real keys are on this width, as calibrated: the boxes of [keys], and where a touchpad point is drawn. The view is
     * as wide as the screen, so the keys line up with the real ones below it. See [heightFor].
     */
    private var geometry = KeyGeometry(0f)
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = GuideFonts.heavy(context)
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val altPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = GuideFonts.medium(context) }
    /** The symbol that Alt types on each letter key, read from the phone's own keyboard, as it is printed on the key. */
    private val altSymbols = keys.filter { it.letter }.mapNotNull { key ->
        Keys.letter(key.name, android.view.KeyEvent.META_ALT_ON)?.let { key.name to it }
    }.toMap()
    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val areaPath = Path()
    private val path = Path()

    var tone = GuideTone.Ink
        set(value) {
            field = value
            invalidate()
        }

    /**
     * What the keys show while the Sym panel is open, by key name, as the keyboard reports it, or null for the plain keys. With a
     * face, each letter key shows its symbol, emoji or setting instead of its letter, and an empty key shows nothing.
     */
    var face: Map<String, String>? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** The gesture that the ghost finger shows now. */
    var cue: GuideCue = GuideCue.Free
        private set
    private var ghost: List<FloatArray> = emptyList()
    private var cueStart = 0L

    /** The finger's path in touchpad space, from 0 to 1 each way: each point's x, y and thickness, 1 for a usual touch. */
    private val trail = ArrayList<FloatArray>()
    /** The key under a real finger, held steady at key edges and through short dropouts of contact. */
    private val hover = KeyHover()
    private var hovered: String? = null
    private var liftedAt = -1L
    /** When a real finger was last on the keys. The ghost waits while one is there, so it never gets in its way. */
    private var touchedAt = -1L
    private var lastFrame = 0L
    private var appearStart = -1L
    private var waveStart = -1L

    fun show(next: GuideCue) {
        cue = next
        cueStart = GuideMotion.now()
        ghost = ghostFor(next)
        invalidate()
    }

    /**
     * The Alt symbols printed in the letter keys' corners: only on the slides about holding a key for its symbol (design v2), as
     * everywhere else they're noise.
     */
    var corners = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * What's behind the drawing, when it isn't the tone's own black or orange: the playground's warming background. The part the
     * touchpad doesn't read is dimmed towards it, so the dimming never shows as lighter patches.
     */
    var shade: Int? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Lets the keys arrive one after the other. */
    fun appear() {
        appearStart = GuideMotion.now()
        invalidate()
    }

    /** A wave of light across the keys, when something works. */
    fun celebrate() {
        waveStart = GuideMotion.now()
        invalidate()
    }

    /** There are two Shift keys, so a name can light more than one key. */
    fun light(name: String) {
        for (key in keys) if (key.name == name) key.heat = 1f
        invalidate()
    }

    /**
     * A real key pressed: only that key lights, the [nth] of its name for the two Shifts (owner, v5.3: both lit for one press), and
     * the finger's light goes, since the touchpad can read a finger on Sym as being on Enter beside it.
     */
    fun press(name: String, nth: Int = 0) {
        keys.filter { it.name == name }.getOrNull(nth)?.heat = 1f
        pressedAt = GuideMotion.now()
        invalidate()
    }

    /** When a real key was last pressed: the finger's light stays off for a moment after. */
    private var pressedAt = -1L

    /** The key that [name] names turns over and shows its [symbol]. */
    fun flip(name: String, symbol: String) {
        val key = byName[name] ?: return
        key.symbol = symbol
        key.flipStart = GuideMotion.now()
        key.heat = 1f
        invalidate()
    }

    /** Adds to the live trail. [reset] starts a new path. */
    fun trace(points: List<FloatArray>, reset: Boolean, lifted: Boolean) {
        if (reset) trail.clear()
        trail.addAll(points)
        // A long steer kept every point, and the trail built into a solid smear (owner, v5.3): only the newest stay.
        if (trail.size > TRAIL_KEEP) trail.subList(0, trail.size - TRAIL_KEEP).clear()
        for (point in points) hover.point(point[0], point[1])
        liftedAt = if (lifted) GuideMotion.now() else -1
        if (lifted) {
            hover.lift(GuideMotion.now())
            // Once the dropout's grace is over, the light fades out.
            postInvalidateDelayed(KeyHover.DROPOUT_MILLIS + FRAME_MILLIS.toLong())
        }
        touched()
    }

    /** The ghost finger shows its gesture only during a demonstration, from the start of the gesture. */
    private var demoing = false

    fun demo(on: Boolean) {
        demoing = on
        if (on) cueStart = GuideMotion.now()
        invalidate()
    }

    /** How long one run of the ghost's gesture takes. */
    fun ghostMillis() = (cycleMillis() * GHOST_SLOW).toLong()

    /** A real finger is on the keys, resting or moving, so the ghost finger stops until it has been away for a moment. */
    fun touched() {
        touchedAt = GuideMotion.now()
        invalidate()
    }

    /** The ghost shows only while no real finger is on the keys, and comes back from the start of its gesture. */
    private fun ghostWaits(now: Long): Boolean {
        if (touchedAt < 0) return false
        if (liftedAt < 0 && trail.isNotEmpty() || now - touchedAt < GHOST_PAUSE_MILLIS) return true
        touchedAt = -1
        cueStart = now
        return false
    }

    /**
     * How tall the drawing is when it is [width] pixels wide: the real keyboard's four rows at the screen's width, and at
     * [KeyGeometry.DRAWN] of their real height (owner, 2026-11-12: same width, 60% height).
     */
    fun heightFor(width: Int): Int = KeyGeometry(width.toFloat(), KeyCalibration.load(context), KeyGeometry.DRAWN).let {
        ceil(if (lettersOnly) it.letterHeight else it.height).toInt()
    }

    /**
     * Only the three letter rows, when the screen is too short for the whole keyboard at its real size: the bottom row goes,
     * cleanly, rather than being cut by the keyboard's bar (owner, v5.3). The keys keep their real size and place.
     */
    var lettersOnly = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = place(w)

    /** Puts each key where the real one is, on [w] pixels, as Key calibration in TitanKeys Debug says. */
    private fun place(w: Int) {
        geometry = KeyGeometry(w.toFloat(), KeyCalibration.load(context), KeyGeometry.DRAWN)
        for ((key, box) in keys.zip(geometry.boxes)) key.box.set(box.left, box.top, box.right, box.bottom)
    }

    override fun onDraw(canvas: Canvas) {
        // Key calibration can change while the drawing shows: it follows at once.
        if (geometry.width != width.toFloat() || geometry.alignment != KeyAlignment.current) place(width)
        val now = GuideMotion.now()
        val elapsed = if (lastFrame == 0L) 0L else now - lastFrame
        lastFrame = now
        val frameCount = if (GuideMotion.enabled) elapsed / FRAME_MILLIS else 99f
        var moving = cue != GuideCue.Free && GuideMotion.enabled && isShown
        hovered = if (pressedAt >= 0 && now - pressedAt < PRESS_QUIET_MILLIS) null else hover.current(now)
        for (key in keys) if (!lettersOnly || key.box.top < geometry.letterHeight) moving = drawKey(canvas, key, now, frameCount) || moving
        drawTouchArea(canvas)
        drawTapZone(canvas)
        drawDeleteZone(canvas)
        drawHoldZones(canvas)
        drawGhost(canvas, now)
        moving = drawTrail(canvas, now) || moving
        if (moving) postInvalidateOnAnimation() else lastFrame = 0
    }

    private fun drawKey(canvas: Canvas, key: Key, now: Long, frames: Float): Boolean {
        var moving = false
        val onOrange = tone == GuideTone.Orange
        // Idle (design v2): the surface at half strength, a cream outline at 16%, the label cream at 70%. On the pass's orange, ink.
        val idleFill = if (onOrange) GuideMotion.faded(GuideColours.INK, KEY_FILL_ORANGE) else GuideMotion.faded(GuideColours.SURFACE, KEY_FILL_INK)
        val idleOutline = GuideMotion.faded(if (onOrange) GuideColours.INK else GuideColours.CREAM,
            if (onOrange) KEY_OUTLINE_ORANGE else KEY_OUTLINE_INK)
        val idleLabel = if (onOrange) GuideColours.INK else GuideMotion.faded(GuideColours.CREAM, KEY_LABEL_INK)
        val litFill = if (onOrange) GuideColours.INK else GuideColours.ORANGE
        val litLabel = if (onOrange) GuideColours.ORANGE else GuideColours.INK

        var heat = key.heat
        if (key.heat > 0f) {
            key.heat = if (GuideMotion.enabled) max(0f, key.heat - HEAT_FADE_PER_FRAME * frames) else 0f
            moving = true
        }
        // The key under the finger eases in, and out once the finger has left it.
        val target = if (key.name == hovered) 1f else 0f
        if (key.glow != target) {
            val step = frames * FRAME_MILLIS / HOVER_FADE_MILLIS
            key.glow = if (!GuideMotion.enabled) target
                else if (target > key.glow) min(target, key.glow + step) else max(target, key.glow - step)
            moving = true
        }
        heat = max(heat, key.glow)
        val spot = cue as? GuideCue.Spot
        if (spot != null) {
            // A held modifier stays lit, steady, as a held key does: never flashing (owner, v5).
            if (key.name in spot.held) heat = max(heat, HELD_HEAT)
            val light = spotLight(spot, key.name, now)
            heat = max(heat, light)
            // Only the rounds move: once they're over the keys are steady, and the drawing rests.
            if (spotRunning(spot, now)) moving = true
        }
        // The wave moves over the keys from the left and from the top.
        var bump = 0f
        if (waveStart >= 0) {
            val delay = (key.box.centerX() + key.box.centerY()) / max(1f, (width + height).toFloat()) * WAVE_SPREAD_MILLIS
            val t = GuideMotion.progress(waveStart + delay.toLong(), WAVE_MILLIS)
            if (GuideMotion.progress(waveStart, (WAVE_SPREAD_MILLIS + WAVE_MILLIS).toLong()) < 1f) {
                bump = sin(t * PI).toFloat()
                heat = max(heat, bump)
                moving = true
            }
        }
        var appear = 1f
        if (appearStart >= 0) {
            val delay = (key.box.centerX() + key.box.centerY()) / max(1f, (width + height).toFloat()) * APPEAR_SPREAD_MILLIS
            val t = GuideMotion.progress(appearStart + delay.toLong(), APPEAR_MILLIS)
            appear = GuideMotion.easeOut(t)
            if (t < 1f) moving = true
        }

        // The boxes are the real keys, which have no gap between them: each is drawn [KEY_INSET] of its width in from either side,
        // so neighbours read as separate keys, and the outline sits just inside that.
        val stroke = dp(KEY_OUTLINE)
        val inset = stroke / 2
        val side = key.box.width().coerceAtMost(geometry.pitch) * KEY_INSET
        val pressed = 1f + bump * WAVE_SCALE - heat * PRESS_DEPTH
        val centreX = key.box.centerX()
        val centreY = key.box.centerY()
        canvas.save()
        canvas.scale(pressed * appear, pressed * appear, centreX, centreY)
        val faced = face
        var label = key.name
        val caption = faced?.get(key.name)
        var flip = 1f
        // A flip that has run its time is over before this frame draws: its last frame would squash the key to a line and ask for
        // no frame after it, so where nothing else moves (a passed slide), the key stayed a line (owner, 2026-11-11: P went).
        if (key.flipStart >= 0 && GuideMotion.progress(key.flipStart, FLIP_MILLIS) >= 1f) {
            key.flipStart = -1
            key.symbol = null
        }
        if (key.flipStart >= 0) {
            val t = GuideMotion.progress(key.flipStart, FLIP_MILLIS)
            // The key turns over to show the symbol on its back, stays there, and turns back.
            flip = when {
                t < FLIP_TURN -> 1f - t / FLIP_TURN
                t < 2 * FLIP_TURN -> (t - FLIP_TURN) / FLIP_TURN
                t < 1f - FLIP_TURN -> 1f
                else -> (1f - t) / FLIP_TURN
            }
            canvas.scale(1f, max(.02f, flip), centreX, centreY)
            if (t >= FLIP_TURN && t < 1f - FLIP_TURN / 2) label = key.symbol ?: key.name
            if (t >= 1f) {
                key.flipStart = -1
                key.symbol = null
            } else moving = true
        }
        val radius = min(Radii.px(resources, Radii.SMALL), min(key.box.width(), key.box.height()) / 2)
        val emptyKey = false
        // Faded from the key's own fill, so an empty key is darker than the others, never brighter.
        val emptyFill = GuideMotion.faded(idleFill, (idleFill ushr 24) / 255f * EMPTY_KEY)
        // An idle key is a thin outline over a very faint fill, as the real keys read from above; a lit key fills, and its
        // outline goes into the fill.
        boxPaint.color = if (emptyKey) emptyFill else GuideMotion.blend(idleFill, litFill, heat.coerceIn(0f, 1f))
        canvas.drawRoundRect(key.box.left + side + inset, key.box.top + inset, key.box.right - side - inset, key.box.bottom - inset, radius, radius, boxPaint)
        outlinePaint.color = GuideMotion.blend(idleOutline, litFill, heat.coerceIn(0f, 1f))
        outlinePaint.strokeWidth = stroke
        canvas.drawRoundRect(key.box.left + side + inset, key.box.top + inset, key.box.right - side - inset, key.box.bottom - inset, radius, radius, outlinePaint)
        val labelColour = GuideMotion.blend(idleLabel, litLabel, heat.coerceIn(0f, 1f))
        if (caption != null) {
            // The hint for this key: the letter moves up a little, and what it does now is written small under it.
            canvas.save()
            canvas.translate(0f, -key.box.height() * CAPTION_LIFT)
            drawLabel(canvas, key, label, labelColour)
            canvas.restore()
            labelPaint.textSize = min(key.box.width(), key.box.height()) * CAPTION_SIZE
            val room = key.box.width() * LABEL_ROOM
            if (labelPaint.measureText(caption) > room) labelPaint.textSize *= room / labelPaint.measureText(caption)
            labelPaint.color = labelColour
            canvas.drawText(caption, key.box.centerX(), key.box.bottom - key.box.height() * CAPTION_BOTTOM, labelPaint)
        } else drawLabel(canvas, key, label, labelColour)
        // Like the real keys, each letter has its Alt symbol printed small in its top left corner.
        altSymbols[key.name]?.takeIf { corners && label == key.name && caption == null }?.let { symbol ->
            // A slide that spots letter keys, as Alt's does with A for its *, fades the other corners, so the one to find stands out.
            val spotted = (cue as? GuideCue.Spot)?.keys?.takeIf { keys -> keys.all(Layout::isLetter) }
            val alpha = if (spotted != null && key.name !in spotted) ALT_ALPHA * ALT_FADED else ALT_ALPHA
            altPaint.color = GuideMotion.faded(labelColour, alpha)
            altPaint.textSize = min(key.box.width(), key.box.height()) * ALT_SIZE
            canvas.drawText(symbol, key.box.left + side + inset + key.box.width() * ALT_INSET, key.box.top + inset - altPaint.ascent() + key.box.height() * ALT_INSET / 2, altPaint)
        }
        canvas.restore()
        return moving
    }

    /**
     * How lit [name] is by [spot] at [now]: the keys light one after the other, each for [SPOT_STEP_MILLIS], easing in and out,
     * then a pause, for the spot's rounds, and then they stay lit, steady (owner, v5: Space blinked forever, and the menu's keys
     * flashed; design v2: the slide's keys are orange).
     */
    private fun spotLight(spot: GuideCue.Spot, name: String, now: Long): Float {
        val at = spot.keys.indexOf(name)
        if (at < 0) return 0f
        if (!GuideMotion.enabled) return 1f
        val round = spot.keys.size * SPOT_STEP_MILLIS + SPOT_REST_MILLIS
        val elapsed = now - cueStart
        if (elapsed < 0) return 0f
        // Once the rounds have shown the order, the slide's keys stay orange, steady (design v2): never blinking (owner, v5).
        if (elapsed >= round * spot.rounds) return 1f
        val local = (elapsed % round - at * SPOT_STEP_MILLIS).toFloat() / SPOT_STEP_MILLIS
        if (local < 0f) return 0f
        // Past its turn, in this round, a key stays softly lit: the hint reads in order, from the first key, in any frame.
        if (local >= 1f) return if (elapsed % round < spot.keys.size * SPOT_STEP_MILLIS) SPOT_DONE else 0f
        return max(SPOT_DONE, sin(local * PI).toFloat()).takeIf { local > .5f } ?: sin(local * PI).toFloat()
    }

    /** The spot's rounds aren't over yet, so the drawing keeps going. */
    private fun spotRunning(spot: GuideCue.Spot, now: Long) =
        GuideMotion.enabled && spot.keys.isNotEmpty() && now - cueStart < (spot.keys.size * SPOT_STEP_MILLIS + SPOT_REST_MILLIS) * spot.rounds

    /**
     * The part of the keys that the touchpad feels stays bright, and the rest is dimmed: its sensor stops at the middle of the outer
     * columns, of the top row and of the bottom row, so a finger there is not read well. A line would cross the letters, so the
     * edge of the dimmed part is the box.
     */
    private fun drawTouchArea(canvas: Canvas) {
        areaPaint.color = GuideMotion.faded(shade ?: if (tone == GuideTone.Orange) GuideColours.ORANGE else GuideColours.INK, AREA_SHADE)
        areaPath.rewind()
        areaPath.fillType = Path.FillType.EVEN_ODD
        areaPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        val area = geometry.area(AREA_LEFT, AREA_TOP, AREA_RIGHT, AREA_BOTTOM)
        areaPath.addRoundRect(area.left, area.top, area.right, area.bottom,
            Radii.px(resources, Radii.MEDIUM), Radii.px(resources, Radii.MEDIUM), Path.Direction.CW)
        canvas.drawPath(areaPath, areaPaint)
    }

    private fun drawLabel(canvas: Canvas, key: Key, label: String, colour: Int) {
        val box = key.box
        val size = min(box.width(), box.height()) * (if (key.letter || label.length == 1) LABEL_LETTER else LABEL_SMALL)
        labelPaint.color = colour
        labelPaint.textSize = size
        iconPaint.color = colour
        iconPaint.strokeWidth = max(1.5f, size * .14f)
        val x = box.centerX()
        val y = box.centerY()
        val r = size * .42f
        when (label) {
            Layout.SPACE -> {
                val halfSpan = box.width() * .22f
                canvas.drawLine(x - halfSpan, y + r * .35f, x + halfSpan, y + r * .35f, iconPaint)
                canvas.drawLine(x - halfSpan, y + r * .35f, x - halfSpan, y - r * .1f, iconPaint)
                canvas.drawLine(x + halfSpan, y + r * .35f, x + halfSpan, y - r * .1f, iconPaint)
            }
            Layout.BACKSPACE -> {
                path.rewind()
                path.moveTo(x - r * 1.2f, y)
                path.lineTo(x - r * .4f, y - r)
                path.lineTo(x + r * 1.2f, y - r)
                path.lineTo(x + r * 1.2f, y + r)
                path.lineTo(x - r * .4f, y + r)
                path.close()
                canvas.drawPath(path, iconPaint)
                canvas.drawLine(x, y - r * .35f, x + r * .6f, y + r * .35f, iconPaint)
                canvas.drawLine(x + r * .6f, y - r * .35f, x, y + r * .35f, iconPaint)
            }
            Layout.ENTER -> {
                path.rewind()
                path.moveTo(x + r, y - r)
                path.lineTo(x + r, y + r * .3f)
                path.lineTo(x - r, y + r * .3f)
                path.moveTo(x - r * .4f, y - r * .35f)
                path.lineTo(x - r, y + r * .3f)
                path.lineTo(x - r * .4f, y + r * .95f)
                canvas.drawPath(path, iconPaint)
            }
            "⇧" -> {
                path.rewind()
                path.moveTo(x, y - r)
                path.lineTo(x + r, y)
                path.lineTo(x + r * .45f, y)
                path.lineTo(x + r * .45f, y + r)
                path.lineTo(x - r * .45f, y + r)
                path.lineTo(x - r * .45f, y)
                path.lineTo(x - r, y)
                path.close()
                canvas.drawPath(path, iconPaint)
            }
            "○" -> canvas.drawCircle(x, y, r * .8f, iconPaint)
            "◁" -> {
                path.rewind()
                path.moveTo(x + r * .7f, y - r * .8f)
                path.lineTo(x - r * .7f, y)
                path.lineTo(x + r * .7f, y + r * .8f)
                path.close()
                canvas.drawPath(path, iconPaint)
            }
            // The key next to Space shows one square, the recent apps mark.
            "▦" -> canvas.drawRoundRect(x - r * .75f, y - r * .75f, x + r * .75f, y + r * .75f, r * .15f, r * .15f, iconPaint)
            "" -> Unit
            else -> {
                // A setting's name or a clip is longer than a letter, so it shrinks to fit its key.
                val room = box.width() * LABEL_ROOM
                val wide = labelPaint.measureText(label)
                if (wide > room) labelPaint.textSize = size * room / wide
                canvas.drawText(label, x, y - (labelPaint.ascent() + labelPaint.descent()) / 2, labelPaint)
            }
        }
    }

    private fun drawTrail(canvas: Canvas, now: Long): Boolean {
        if (trail.size < 2) {
            if (liftedAt >= 0) {
                trail.clear()
                liftedAt = -1
            }
            return false
        }
        var alpha = 1f
        var moving = false
        if (liftedAt >= 0) {
            val t = GuideMotion.progress(liftedAt, TRAIL_FADE_MILLIS)
            alpha = 1f - t
            if (t >= 1f) {
                trail.clear()
                liftedAt = -1
                return false
            }
            moving = true
        }
        // Each pass is drawn whole and then faded, so where its pieces overlap it does not darken.
        trailPaint.color = if (tone == GuideTone.Orange) GuideColours.INK else GuideColours.ORANGE
        if (inPauseZone()) alpha *= PAUSED_TRAIL
        var layer = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (TRAIL_SHADE * alpha * 255).roundToInt())
        drawTrailPieces(canvas, dp(TRAIL_WIDTH) * TRAIL_UNDER)
        canvas.restoreToCount(layer)
        trailPaint.color = GuideColours.CREAM
        dotPaint.color = GuideColours.CREAM
        layer = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (alpha * 255).roundToInt())
        drawTrailPieces(canvas, dp(TRAIL_WIDTH))
        val last = trail[trail.size - 1]
        canvas.drawCircle(geometry.x(last[0]), geometry.y(last[1]), dp(TRAIL_WIDTH) * .9f * thickness(last), dotPaint)
        canvas.restoreToCount(layer)
        return moving
    }

    /**
     * The trail as one smooth curve, drawn a piece at a time so each piece takes its own thickness: thicker where the
     * touch was larger than usual for that spot, thinner where it was lighter. Round ends join the pieces seamlessly.
     */
    private fun drawTrailPieces(canvas: Canvas, width: Float) {
        var startX = geometry.x(trail[0][0])
        var startY = geometry.y(trail[0][1])
        for (i in 1..trail.size) {
            val corner = trail[i - 1]
            val cx = geometry.x(corner[0])
            val cy = geometry.y(corner[1])
            // Each piece runs from the middle before a point to the middle after it, curving through the point.
            val (endX, endY) = if (i < trail.size) (cx + geometry.x(trail[i][0])) / 2 to (cy + geometry.y(trail[i][1])) / 2 else cx to cy
            path.rewind()
            path.moveTo(startX, startY)
            path.quadTo(cx, cy, endX, endY)
            trailPaint.strokeWidth = width * thickness(corner)
            // The oldest part fades out, so a long trail tapers away behind the finger and never piles up.
            val colour = trailPaint.color
            val age = (trail.size - i).toFloat() / TRAIL_KEEP
            if (age > TRAIL_FADE_FROM) trailPaint.color = GuideMotion.faded(colour, (colour ushr 24) / 255f * ((1f - age) / (1f - TRAIL_FADE_FROM)))
            canvas.drawPath(path, trailPaint)
            trailPaint.color = colour
            startX = endX
            startY = endY
        }
    }

    private fun thickness(point: FloatArray) = if (point.size > 2) point[2] else 1f

    /** The ghost finger: thin, dashed by the time it lasts, then a pause, then again. */
    private fun drawGhost(canvas: Canvas, now: Long) {
        if (cue == GuideCue.Free || cue is GuideCue.Spot || !demoing) return
        if (ghostWaits(now)) return
        val colour = if (tone == GuideTone.Orange) GuideColours.INK else GuideColours.ORANGE
        val cycle = ghostMillis()
        val t = if (GuideMotion.enabled) ((now - cueStart).coerceAtMost(cycle - 1).toFloat() / cycle) else 1f

        if (cue is GuideCue.Taps) {
            drawTaps(canvas, cue as GuideCue.Taps, t, colour)
            return
        }
        if (ghost.size < 2) return
        drawGhostPath(canvas, ghost, t, colour)
    }

    private fun drawGhostPath(canvas: Canvas, points: List<FloatArray>, t: Float, colour: Int) {
        val hold = (cue as? GuideCue.Flick)?.hold == true
        // A flick snaps: fast out, easing at the end, and the finger lifts at once. A hold travels, then stays and pulses.
        val snap = cue is GuideCue.Flick && !hold
        val travel = when {
            hold -> t / .5f
            snap -> 1f - (1f - (t / FLICK_SNAP).coerceIn(0f, 1f)).let { it * it * it }
            else -> t / .7f
        }.coerceIn(0f, 1f)
        val alpha = when {
            snap && t > FLICK_SNAP -> 1f - (t - FLICK_SNAP) / FLICK_FADE
            t > .9f -> (1f - t) / .1f
            else -> 1f
        }.coerceIn(0f, 1f)
        // All the ghost's path, thin and faint, so the shape shows at once.
        ghostPaint.color = GuideMotion.faded(colour, GHOST_LINE * alpha)
        ghostPaint.strokeWidth = dp(GHOST_WIDTH)
        linePath(points, 1f)
        canvas.drawPath(path, ghostPaint)
        // The part that the ghost has done, heavier, with the ghost finger at its end.
        ghostPaint.color = GuideMotion.faded(colour, alpha)
        ghostPaint.strokeWidth = dp(GHOST_DONE_WIDTH)
        val head = linePath(points, travel)
        canvas.drawPath(path, ghostPaint)
        dotPaint.color = GuideMotion.faded(GuideColours.CREAM, alpha)
        canvas.drawCircle(head[0], head[1], dp(GHOST_FINGER), dotPaint)
        if (snap && t in FLICK_SNAP..FLICK_SNAP + FLICK_FADE) {
            // The lift: one ring pops out from where the finger left.
            val pop = (t - FLICK_SNAP) / FLICK_FADE
            ringPaint.color = GuideMotion.faded(GuideColours.CREAM, 1f - pop)
            ringPaint.strokeWidth = dp(2f)
            canvas.drawCircle(head[0], head[1], dp(GHOST_FINGER) * (1f + pop * 2f), ringPaint)
        }
        if (hold && t in .5f..0.9f) {
            // A flick that stays: the finger pulses in place, and words would keep going.
            val pulse = ((t - .5f) * 6f) % 1f
            ringPaint.color = GuideMotion.faded(GuideColours.CREAM, (1f - pulse) * alpha)
            ringPaint.strokeWidth = dp(2f)
            canvas.drawCircle(head[0], head[1], dp(GHOST_FINGER) * (1f + pulse * 2.5f), ringPaint)
        }
    }

    /** Fills [path] with the first [portion] of [points], and returns where it ends. */
    private fun linePath(points: List<FloatArray>, portion: Float): FloatArray {
        var total = 0f
        for (i in 1 until points.size) total += distance(points[i - 1], points[i])
        val limit = total * portion
        var walked = 0f
        path.rewind()
        path.moveTo(geometry.x(points[0][0]), geometry.y(points[0][1]))
        var end = floatArrayOf(geometry.x(points[0][0]), geometry.y(points[0][1]))
        for (i in 1 until points.size) {
            val step = distance(points[i - 1], points[i])
            val share = if (step <= 0f) 1f else ((limit - walked) / step).coerceIn(0f, 1f)
            end = floatArrayOf(
                geometry.x(points[i - 1][0] + (points[i][0] - points[i - 1][0]) * share),
                geometry.y(points[i - 1][1] + (points[i][1] - points[i - 1][1]) * share),
            )
            path.lineTo(end[0], end[1])
            walked += step
            if (walked >= limit) break
        }
        return end
    }

    private fun distance(a: FloatArray, b: FloatArray) = hypot(geometry.x(b[0]) - geometry.x(a[0]), geometry.y(b[1]) - geometry.y(a[1]))

    /**
     * A soft patch over the part of the keys where the steer and select taps are easiest to land: from the top of the
     * bottom letter row to as far down Space as the touchpad feels, across C, V, B and N ([CursorGesture.inZone]).
     */
    private fun drawTapZone(canvas: Canvas) {
        if (cue !is GuideCue.Taps) return
        val corner = Radii.px(resources, Radii.SMALL)
        zonePaint.color = GuideMotion.faded(if (tone == GuideTone.Orange) GuideColours.INK else GuideColours.ORANGE, ZONE_ALPHA)
        val zone = geometry.area(CursorGesture.ZONE_LEFT, CursorGesture.ZONE_TOP, CursorGesture.ZONE_RIGHT, ZONE_BOTTOM)
        canvas.drawRoundRect(zone.left, zone.top, zone.right, zone.bottom, corner, corner, zonePaint)
    }

    /**
     * A soft patch, like the steer zone, over where to flick from to delete ([guideDeleteBox]): one rectangle over the right edge,
     * from I and O down to M and Sym. It stops where the touchpad stops feeling. The ghost's flick starts inside it.
     */
    private fun drawDeleteZone(canvas: Canvas) {
        if (cue !is GuideCue.Flick) return
        val corner = Radii.px(resources, Radii.SMALL)
        zonePaint.color = GuideMotion.faded(if (tone == GuideTone.Orange) GuideColours.INK else GuideColours.ORANGE, ZONE_ALPHA)
        deletePath.rewind()
        for ((left, top, right, bottom) in listOf(guideDeleteBox)) {
            val box = Path()
            val zone = geometry.area(left, maxOf(top, AREA_TOP), minOf(right, AREA_RIGHT), minOf(bottom, AREA_BOTTOM))
            box.addRoundRect(zone.left, zone.top, zone.right, zone.bottom, corner, corner, Path.Direction.CW)
            deletePath.op(box, Path.Op.UNION)
        }
        canvas.drawPath(deletePath, zonePaint)
    }

    private val deletePath = Path()

    /**
     * The held delete's zones, on its slide, on the Backspace row: back on Backspace pauses, and from between F and G leftwards is
     * top speed, a lighter box. They are where the hold really has them: [PAUSE_KEYS] key widths from where a flick on Backspace
     * starts, and [REPEAT_LONG_KEYS].
     */
    private fun drawHoldZones(canvas: Canvas) {
        if ((cue as? GuideCue.Flick)?.hold != true) return
        val colour = if (tone == GuideTone.Orange) GuideColours.INK else GuideColours.CREAM
        val corner = Radii.px(resources, Radii.SMALL)
        val fast = geometry.onRow(1, AREA_LEFT, HOLD_TOP_X)
        zonePaint.color = GuideMotion.faded(colour, ZONE_ALPHA * FAST_ZONE)
        canvas.drawRoundRect(fast.left, fast.top, fast.right, fast.bottom, corner, corner, zonePaint)
        val pause = geometry.onRow(1, HOLD_PAUSE_X, AREA_RIGHT)
        zonePaint.color = GuideMotion.faded(colour, ZONE_ALPHA)
        canvas.drawRoundRect(pause.left, pause.top, pause.right, pause.bottom, corner, corner, zonePaint)
        altPaint.color = GuideMotion.faded(colour, .85f)
        altPaint.textSize = min(geometry.key, geometry.keyHeight) * ZONE_LABEL
        val word = "pause"
        canvas.drawText(word, pause.centreX - altPaint.measureText(word) / 2, pause.bottom - dp(3f), altPaint)
    }

    /** The hold's finger is back in the pause zone: its trail dims, as the bar's glow fades there. */
    private fun inPauseZone() = (cue as? GuideCue.Flick)?.hold == true && trail.isNotEmpty() && liftedAt < 0 && trail.last()[0] >= HOLD_PAUSE_X

    private fun drawTaps(canvas: Canvas, cue: GuideCue.Taps, t: Float, colour: Int) {
        // Rings for each tap, one after the other, inside the zone where the taps count, then a sideways drag if there is one.
        val tapsEnd = if (cue.drag) .45f else .8f
        val x = geometry.x(SPACE_CENTRE)
        val y = geometry.y(ZONE_TAP_ROW)
        ringPaint.strokeWidth = dp(2.5f)
        for (tap in 0 until cue.count) {
            val start = tap * tapsEnd / cue.count
            val local = ((t - start) / (tapsEnd / cue.count)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue
            ringPaint.color = GuideMotion.faded(colour, 1f - local)
            canvas.drawCircle(x, y, dp(GHOST_FINGER) * (1f + local * 3f), ringPaint)
        }
        dotPaint.color = GuideMotion.faded(GuideColours.CREAM, if (t < .95f) 1f else (1f - t) / .05f)
        var fingerX = x
        var fingerY = y
        if (cue.drag && t > tapsEnd) {
            // The finger stays down and slides sideways to the left: back over the words that the cursor moves through or
            // that the selection takes, as the field shows. Above Space only a sideways slide counts, so the ghost shows that.
            val travel = ((t - tapsEnd) / (1f - tapsEnd - .08f)).coerceIn(0f, 1f)
            fingerX = x - (x - geometry.x(SPACE_CENTRE - DRAG_REACH)) * travel
            // The drag is the part to copy, so it is as strong as the part of a word that the ghost has done.
            ghostPaint.color = colour
            ghostPaint.strokeWidth = dp(GHOST_DONE_WIDTH)
            canvas.drawLine(x, y, fingerX, fingerY, ghostPaint)
        }
        canvas.drawCircle(fingerX, fingerY, dp(GHOST_FINGER), dotPaint)
    }

    private fun cycleMillis() = when (cue) {
        is GuideCue.Word -> 3000L
        is GuideCue.Flick -> if ((cue as GuideCue.Flick).hold) 3400L else 2000L
        GuideCue.SlideRow -> 2200L
        is GuideCue.Taps -> if ((cue as GuideCue.Taps).drag) 3200L else 1800L
        else -> 2400L
    }

    private fun ghostFor(cue: GuideCue): List<FloatArray> = when (cue) {
        is GuideCue.Word -> cue.word.map { letter ->
            val (x, y) = Layout.centre(letter)
            floatArrayOf(x, y)
        }
        is GuideCue.Flick -> listOf(GUIDE_FLICK_START, floatArrayOf(GUIDE_FLICK_END_X, GUIDE_FLICK_START[1]))
        GuideCue.SlideRow -> listOf(floatArrayOf(.08f, .875f), floatArrayOf(.92f, .875f))
        else -> emptyList()
    }

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private companion object {
        // Keys. Sizes are in dp unless they say otherwise. Change these to adjust the look.
        const val FRAME_MILLIS = 16f
        /** How long the finger's light stays off after a real key press, so the pressed key is the one lit. */
        const val PRESS_QUIET_MILLIS = 400L
        /** The most points the trail keeps, and from how far back, as a part of that, it fades out. */
        const val TRAIL_KEEP = 120
        const val TRAIL_FADE_FROM = .45f
        /**
         * A key's thin outline, and how strong it is, and the fill inside it, on orange and on black: on black, a cream outline at
         * 16% over the surface at 50%, with labels cream at 70% (design v2).
         */
        const val KEY_OUTLINE = 1f
        const val KEY_OUTLINE_ORANGE = .5f
        const val KEY_OUTLINE_INK = .16f
        const val KEY_FILL_ORANGE = .05f
        const val KEY_FILL_INK = .5f
        const val KEY_LABEL_INK = .7f
        /** How far each key is drawn in from its sides, as a part of its width: the real keys touch, the drawn ones don't. */
        const val KEY_INSET = .03f
        const val LABEL_LETTER = .52f
        const val LABEL_SMALL = .36f
        /** How much of a key's fill an empty key of the panel keeps. */
        const val EMPTY_KEY = .35f
        /** The part of a key's width that a label may take. */
        const val LABEL_ROOM = .86f
        // The Alt symbol in the corner of a letter key, as parts of the key's size.
        const val ALT_SIZE = .26f
        const val ALT_INSET = .1f
        const val ALT_ALPHA = .6f
        /** How much of a lit key's size goes away, so that it looks pushed in. */
        const val PRESS_DEPTH = .06f
        const val HEAT_FADE_PER_FRAME = .055f
        /** How long the light under a finger takes to come on, and to go once the finger has left. */
        const val HOVER_FADE_MILLIS = 100f
        /** A spotted key's turn to light, the rest after each round, and how many rounds, before the spot goes quiet. */
        const val SPOT_STEP_MILLIS = 700L
        /** How lit a spotted key stays once its turn in the round is over. */
        const val SPOT_DONE = .35f
        const val SPOT_REST_MILLIS = 900L
        /** How lit a held modifier stays, steady. */
        const val HELD_HEAT = .85f
        /** A hint on a key: how much its letter moves up, the hint's size, and how far from the key's bottom. */
        const val CAPTION_LIFT = .14f
        const val CAPTION_SIZE = .24f
        const val CAPTION_BOTTOM = .12f
        /** A flick's ghost snaps to its end in this part of the demo, then lifts and fades in this part. */
        const val FLICK_SNAP = .25f
        const val FLICK_FADE = .2f

        // The wave that runs over the keys when something works.
        const val WAVE_MILLIS = 360L
        const val WAVE_SPREAD_MILLIS = 380f
        const val WAVE_SCALE = .1f
        const val APPEAR_MILLIS = 280L
        const val APPEAR_SPREAD_MILLIS = 340f
        const val FLIP_MILLIS = 1500L
        /** How much the corner symbols fade, beside a spotted letter key's. */
        const val ALT_FADED = .35f
        /** The part of the time that one turn of the key takes. */
        const val FLIP_TURN = .12f

        // The real trail under your finger.
        const val TRAIL_WIDTH = 5f
        const val TRAIL_UNDER = 1.9f
        const val TRAIL_SHADE = .38f
        const val TRAIL_FADE_MILLIS = 700L

        // The ghost finger that shows each gesture.
        const val GHOST_WIDTH = 2f
        const val GHOST_DONE_WIDTH = 4f
        const val GHOST_LINE = .34f
        const val GHOST_FINGER = 7f
        /** How long the ghost waits after a real finger leaves the keys before it shows the gesture again. */
        const val GHOST_PAUSE_MILLIS = 2000L
        /** The ghost goes this many times slower than a real gesture, so that it is easy to follow. */
        const val GHOST_SLOW = 1.5f
        const val SPACE_CENTRE = .5f
        const val DRAG_REACH = .32f
        /** Where the touchpad stops feeling, halfway down Space: the shaded zone ends there. */
        const val ZONE_BOTTOM = .885f
        /** Where the ghost taps: just above the line between V and B and Space. */
        const val ZONE_TAP_ROW = .72f
        const val ZONE_ALPHA = .2f
        /** The held delete's zones as parts of the drawing's width: a flick from Backspace's middle (.95) back [PAUSE_KEYS] keys
         *  pauses, and [REPEAT_LONG_KEYS] keys out is top speed, between F and G. */
        const val HOLD_PAUSE_X = .95f - PAUSE_KEYS / 10f
        const val HOLD_TOP_X = .95f - REPEAT_LONG_KEYS / 10f
        const val ZONE_LABEL = .24f
        /** The top-speed box is lighter than the pause box: this much of its strength. */
        const val FAST_ZONE = .5f
        /** How much the trail dims while the hold is paused. */
        const val PAUSED_TRAIL = .3f

        // The box round the part of the keys that the touchpad feels, as parts of the drawing's width and height.
        const val AREA_LEFT = .05f
        const val AREA_RIGHT = .95f
        /** The middle of the top row. */
        const val AREA_TOP = .5f / 4
        /** The touchpad reports its last point at row 3.54 of the 4 rows. */
        const val AREA_BOTTOM = 3.54f / 4
        /** How strongly the part that the touchpad does not read is dimmed. */
        const val AREA_SHADE = .5f
    }
}
