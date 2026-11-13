package com.atlas.keyboard

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The word menu's word and its effects, ported from the owner's approved mockup (.dev/mockups/ladder-v4.html, 2026-11-13) one to
 * one: its springs and air, its six effects, its timing and its haptic frames. The owner's corrections on top:
 * - A word's rung is only its colour (owner, 2026-11-13: "remove the statuses for the heart and book etc. keep it simple.
 *   favourited = orange. saved = white. neutral = grey. blocked = grey + striked through"). Every look is a solid word, with no
 *   outline (none read on the phone, at any width) and no mark over it, at rest or over the bar.
 * - A resting finger's preview moves the word about [LEAN] of the way to its next colour, and each effect's letters go the rest of
 *   the way as it plays.
 * - Save's book is opaque: its pages are filled with the bar's own colour under the orange line, so the letters that fly into
 *   it can't be seen through it once it shuts. The ink is drawn under the book for the same reason.
 * - Save's flying book sits wholly above the bar, its bottom well over the bar's top edge, never across it.
 *
 * Places are in dp: x from the bar's left, y from the bar's top, so that effects above the bar have a negative y. The strip draws
 * [paintWord] on the bar, under the hovered button's pill, and its effects layer draws [paintEffects] over the bar and up to
 * [ABOVE] dp over the app. The scene is plain Kotlin, so the checks drive and draw the very same thing without a phone.
 *
 * [word] is the word as shown, and [layout] where its letters are in the menu's font at [FONT_DP]. The word is centred on [cx],
 * on the baseline [baseline]. [onHaptic] is called on each effect's one haptic frame, with the step that played it.
 */
class MenuScene(
    val word: String,
    val layout: WordLayout,
    var cx: Float,
    val baseline: Float,
    step: MenuKeys.Step,
    var palette: Palette = Palette.DARK,
    var reducedMotion: Boolean = false,
    var onHaptic: (MenuKeys.Button, String) -> Unit = { _, _ -> },
) {
    /**
     * Where each letter of a word starts and has its middle, from the word's left, the word's width, and how high its tallest letter
     * reaches over the baseline, in dp.
     */
    class WordLayout(val xs: FloatArray, val cs: FloatArray, val width: Float, val top: Float = MenuScene.TALL * MenuScene.FONT_DP) {
        companion object {
            /** [word] laid out by [measure], the width of a piece of it in the menu's font at [FONT_DP], reaching [top] over the baseline. */
            fun of(word: String, top: Float = MenuScene.TALL * MenuScene.FONT_DP, measure: (String) -> Float) = WordLayout(
                FloatArray(word.length) { measure(word.substring(0, it)) },
                FloatArray(word.length) { (measure(word.substring(0, it)) + measure(word.substring(0, it + 1))) / 2 },
                measure(word), top)
        }
    }

    /**
     * The colours, as RGB: the word's ink, for Saved; Not saved's grey, which is the strike's too; Blocked's dimmer grey; the accent;
     * the bar's own colour; the cream of the quiet marks; and a favourite's colour. The greys stay the same with Settings > Orange
     * accents off, and the favourite's colour stays apart from the white and the grey.
     */
    data class Palette(val ink: Int, val grey: Int, val dim: Int, val accent: Int, val paper: Int, val cream: Int, val favourite: Int) {
        companion object {
            /**
             * The bar's dark look, with Accent's colour: orange, or cream with Orange accents off. Saved is white, Not saved the muted
             * #99918A, Blocked #5E5853 and Favourite [Accent.favourite] (owner, 2026-11-13).
             */
            val DARK get() = Palette(0xFFFFFF, 0x99918A, 0x5E5853, Accent.colour and 0xFFFFFF, 0x000000, 0xFFF1E6, Accent.favourite and 0xFFFFFF)
            /**
             * The bar's light look: black ink, and the same warm greys mirrored, as far from black as the dark look's are from white.
             * A favourite is orange, or with Orange accents off, the darker cream's own hue at L* 21, halfway between Saved's black
             * and Not saved's grey (L* 41): half as far again from the ink as the dark look's cream is from white, 14 L*.
             */
            val LIGHT get() = Palette(0x000000, 0x66615C, 0xA1978E, Accent.colour and 0xFFFFFF, 0xFFFFFF, 0x000000,
                if (Accent.on) Accent.colour and 0xFFFFFF else 0x35312E)
        }
    }

    /** A spring: mass, stiffness and damping. A second press moves its target and keeps its speed, so nothing starts over. */
    class Spring(var x: Float, var cfg: Cfg) {
        data class Cfg(val m: Float, val k: Float, val c: Float)
        var v = 0f
        var t = x
        fun to(target: Float, with: Cfg? = null) = apply { t = target; if (with != null) cfg = with }
        fun set(value: Float) = apply { x = value; t = value; v = 0f }
        fun kick(speed: Float) = apply { v += speed }
        fun step(dt: Float) {
            v += (-cfg.k * (x - t) - cfg.c * v) / cfg.m * dt
            x += v * dt
        }
        val busy get() = abs(x - t) > 4e-4f || abs(v) > 4e-3f
    }

    /** A value eased from where it is to where it's going over a time. */
    class Tween(var x: Float) {
        private var a = x
        var b = x
            private set
        private var p = 1f
        private var d = 1f
        private var e: (Float) -> Float = ::easeOut
        fun to(target: Float, millis: Float, easing: (Float) -> Float = ::easeOut) = apply {
            if (target == b && p < 1f) return@apply
            a = x; b = target; p = if (a == target) 1f else 0f; d = max(1f, millis) / 1000f; e = easing
        }
        fun set(value: Float) = apply { x = value; a = value; b = value; p = 1f }
        fun step(dt: Float) {
            if (p >= 1f) return
            p = min(1f, p + dt / d)
            x = lerp(a, b, e(p))
        }
        val busy get() = p < 1f
    }

    enum class Kind { Dot, Heart, Puff, Fleck, Shard, Half, Ink, Leaf }

    /**
     * One particle: forces, linear air drag, spin with its own drag, a pop-in spring and a fade-out that always ends before it dies.
     * [back] ones are small and faint, drawn first.
     */
    open class Part(val kind: Kind) {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var ax = 0f; var ay = 0f; var drag = 0f
        var rot = 0f; var w = 0f; var wdrag = 0f; var age = 0f; var life = 1f; var fadeIn = .05f; var fadeOut = .35f
        var alpha = 1f; var size = 1f; var s0 = 1f
        var swayA = 0f; var swayF = 0f; var swayP = 0f
        var back = false; var side = 0; var ch = ""; var colour: Int? = null
        var home: (() -> Triple<Float, Float, Float>)? = null
        val sc by lazy { Spring(s0, POP).to(1f) }

        open fun step(dt: Float) {
            if (age < 0f) { age += dt; return }
            var fx = ax
            var fy = ay
            if (swayA != 0f) fx += sin(age * swayF + swayP) * swayA
            home?.invoke()?.let { (hx, hy, k) -> fx += k * (hx - x); fy += k * (hy - y) }
            vx += (fx - drag * vx) * dt
            vy += (fy - drag * vy) * dt
            x += vx * dt
            y += vy * dt
            w -= wdrag * w * dt
            rot += w * dt
            sc.step(dt)
            age += dt
        }

        val a: Float get() {
            if (age < 0f) return 0f
            val f = min(clamp(age / fadeIn), 1f - easeInOut(clamp((age - (life - fadeOut)) / fadeOut)))
            return alpha * f
        }
        val dead get() = age >= life
    }

    /** A letter's ink flying up into the book; it ends as it reaches it. */
    class Ink(private val tx: () -> Float, private val ty: () -> Float) : Part(Kind.Ink) {
        var d0 = 1f
        private var closing = false
        fun targetX() = tx()
        fun targetY() = ty()
        override fun step(dt: Float) {
            super.step(dt)
            if (!closing && hypot(x - tx(), y - ty()) < 5f) {
                closing = true
                life = age + .06f
                fadeOut = .06f
            }
        }
    }

    /**
     * A torn page falling through air: a damped pendulum for its tilt, lift that turns its fall sideways along the tilt, and drag
     * that is strongest when it lies flat to the air. It swings and drifts down like paper.
     */
    class Leaf : Part(Kind.Leaf) {
        var th = .1f; var thw = 4f
        private val k = 36f; private val damp = .5f; private val lift = 14f; private val kd = 5f; private val g = 520f
        override fun step(dt: Float) {
            val drag = kd * (.35f + .65f * abs(cos(th)))
            vx += (lift * sin(th) * max(vy, 0f) - drag * vx) * dt
            vy += (g - drag * vy) * dt
            thw += (-k * th - damp * thw) * dt
            th += thw * dt
            x += vx * dt
            y += vy * dt
            age += dt
            sc.step(dt)
        }
    }

    /** Save's and Unsave's book. [phi] turns the left cover over the spine: 0 open as the icon, π shut on the right page. */
    inner class Book {
        val x get() = cx
        val y = Spring(CY - 92f, DRIFT)
        val size = 40f
        val a = Tween(0f)
        val s = Spring(0f, POP)
        val rot = Spring(0f, WOBBLE)
        val sx = Spring(1f, POP)
        var phi = 0f
        var phiV = 0f
        var falling = false
        val phiS = Spring(0f, SNAP)
        val tug = Spring(0f, SNAP)
        val tear = Tween(0f)
        var torn = false
        var gone = false
        var landed = false
        var onLand: (() -> Unit)? = null
    }

    private val letters = word.map { it.toString() }
    private val xs = layout.xs
    private val cs = layout.cs
    /** The word's width, and its left end. */
    val wordWidth = layout.width
    val wordLeft get() = cx - wordWidth / 2

    /** Where the word stands: its rung. A press moves it, and the look follows with the effect. */
    var rung = step
        private set
    var time = 0f
        private set

    /** Each letter's look, as a [tone]: 0 Blocked's dim grey, 1 Not saved's grey, 2 Saved's white, 3 Favourite's colour. */
    private val tones = Array(letters.size) { Spring(0f, SNAP) }
    /** How far a resting finger's preview has moved each letter toward its next look, in the same steps: [LEAN] of one, up or down. */
    private val leans = Array(letters.size) { Tween(0f) }
    /** Block's preview: its grey strike. */
    private val strikePreview = Tween(0f)
    /** What the resting finger's press would do, previewed on the word. */
    var ghost: MenuKeys.Button? = null
        private set
    private var ghostOwned = false
    private var penOn = false
    private val sx = Spring(1f, POP)
    private val sy = Spring(1f, POP)
    private val dy = Spring(0f, POP)
    private val strikeEnd = Spring(0f, PEN)
    private val strikeHot = Tween(0f)
    private val strikeAlpha = Tween(1f)
    private val heartAlpha = Tween(0f)
    private val heartScale = Spring(1f, POP)
    private val heartHot = Tween(0f)
    private val heartCrack = Tween(0f)
    var book: Book? = null
        private set
    val parts = ArrayList<Part>()
    private class Cue(val at: Float?, val pred: (() -> Boolean)?, val fn: () -> Unit)
    private var cues = ArrayList<Cue>()
    private var tracks = ArrayList<(Float) -> Boolean>()
    private var pressedAt = 0f
    /** Reduced motion: the look as it was, fading out over [crossfade]. */
    private var fadeFrom: MenuScene? = null
    private var crossfade: Tween? = null

    init {
        setLook(step)
    }

    /** The word at rest at [step], with nothing playing. */
    fun setLook(step: MenuKeys.Step) {
        rung = step
        val struck = if (step == MenuKeys.Step.Blocked) 1f else 0f
        tones.forEach { it.set(tone(step)) }
        leans.forEach { it.set(0f) }
        strikePreview.set(0f)
        ghost = null
        ghostOwned = false
        penOn = false
        sx.set(1f); sy.set(1f); dy.set(0f)
        strikeEnd.set(struck); strikeHot.set(0f); strikeAlpha.set(1f)
        heartAlpha.set(0f); heartCrack.set(0f); heartHot.set(0f); heartScale.set(1f)
        book = null
        parts.clear()
        cues = ArrayList()
        tracks = ArrayList()
    }

    // The resting finger: the preview of what the press will do.

    /** A finger rests on the button that does [button] now, or on none ([unhover]). */
    fun hover(button: MenuKeys.Button) {
        if (button == MenuKeys.Button.Favourited) return unhover()
        val current = ghost
        if (current == MenuKeys.Button.Unblock && button != MenuKeys.Button.Unblock) strikeAlpha.to(1f, PREVIEW)
        ghost = button
        ghostOwned = false
        // The letters lean toward the colour the press would give them. Another button's lean turns into this one's, never a jump.
        leans.forEach { it.to(lean(button), PREVIEW) }
        strikePreview.to(if (button == MenuKeys.Button.Block) 1f else 0f, PREVIEW)
        if (button == MenuKeys.Button.Unblock) strikeAlpha.to(.3f, PREVIEW)
    }

    fun unhover() {
        // The press has taken the preview over; its effect fades it.
        if (ghostOwned) return
        leans.forEach { it.to(0f, PREVIEW) }
        strikePreview.to(0f, PREVIEW)
        if (ghost == MenuKeys.Button.Unblock && rung == MenuKeys.Step.Blocked) strikeAlpha.to(1f, PREVIEW)
    }

    /** Save and Unsave: the steps with the book. */
    private fun withBook(button: MenuKeys.Button) = button == MenuKeys.Button.Save || button == MenuKeys.Button.Unsave
    /** How far [button]'s preview leans the letters: a step up the ladder's colours, a step down, or, for the spent heart, not at all. */
    private fun lean(button: MenuKeys.Button) = when (button) {
        MenuKeys.Button.Save, MenuKeys.Button.Unblock, MenuKeys.Button.Favourite -> LEAN
        MenuKeys.Button.Unsave, MenuKeys.Button.Block, MenuKeys.Button.Unfavourite -> -LEAN
        MenuKeys.Button.Favourited -> 0f
    }

    // The press.

    /** [button] was pressed: the word moves to its next rung, with the effect. A second press carries on from where things are. */
    fun press(button: MenuKeys.Button) {
        val to = next(button) ?: return
        pressedAt = time
        // The old effect's choreography stops; whatever is moving carries on from where it is.
        cues = ArrayList()
        tracks = ArrayList()
        if (reducedMotion) {
            val from = MenuScene(word, layout, cx, baseline, rung, palette, true)
            setLook(to)
            fadeFrom = from
            crossfade = Tween(0f).to(1f, REDUCED, ::easeInOut)
            onHaptic(button, "end state")
            return
        }
        // A preview for some other step fades; the press's own preview is handed to its effect.
        if (ghost != button) {
            leans.forEach { it.to(0f, FADE) }
        } else for (i in letters.indices) {
            // The letters stay where the preview leaned them, and the effect takes them on from there, never back first.
            tones[i].x += leans[i].x; tones[i].t += leans[i].x; leans[i].set(0f)
        }
        if (ghost == MenuKeys.Button.Unblock && button != MenuKeys.Button.Unblock) strikeAlpha.to(1f, FADE)
        ghostOwned = true
        penOn = false
        // Props the new step doesn't use leave cleanly: they rise and fade.
        val b = book
        if (b != null && !withBook(button)) {
            b.onLand = null; b.gone = true; b.a.to(0f, EXIT, ::easeInOut); b.y.to(b.y.x - 20f)
        }
        if (button != MenuKeys.Button.Favourite && button != MenuKeys.Button.Unfavourite && heartAlpha.b > 0f) heartAlpha.to(0f, FADE)
        if (button != MenuKeys.Button.Block && button != MenuKeys.Button.Unblock && strikeHot.b > 0f) strikeHot.to(0f, SETTLE)
        rung = to
        val r = random(seedOf(button.name.lowercase()))
        when (button) {
            MenuKeys.Button.Save -> save(r)
            MenuKeys.Button.Unsave -> unsave(r)
            MenuKeys.Button.Favourite -> favourite(r)
            MenuKeys.Button.Unfavourite -> unfavourite(r)
            MenuKeys.Button.Block -> block(r)
            MenuKeys.Button.Unblock -> unblock(r)
            MenuKeys.Button.Favourited -> Unit
        }
    }

    private fun haptic(button: MenuKeys.Button, label: String) = onHaptic(button, label)

    private fun at(millis: Float, fn: () -> Unit) { cues += Cue(time + millis / 1000f, null, fn) }
    private fun whenever(pred: () -> Boolean, fn: () -> Unit) { cues += Cue(null, pred, fn) }

    private fun squash(kick: Float? = null) {
        val anticipate = ANTICIPATE
        sx.to(1f + (1f - anticipate) / 2, SNAP); sy.to(anticipate, SNAP)
        val v = kick ?: (KICK * .4f)
        at(SQUASH) { sx.to(1f, POP).kick(v); sy.to(1f, POP).kick(v) }
    }

    private fun pop(v: Float) { sx.kick(v); sy.kick(v) }

    /** The ink front: a spring from one end of the word to the other, passing each letter's centre in turn. */
    private fun sweep(dir: Int, onPass: (Int) -> Unit) {
        val n = letters.size
        val front = Spring(if (dir > 0) -.02f else 1.02f, SWEEP).to(if (dir > 0) 1.06f else -.06f)
        val passed = HashSet<Int>()
        tracks += { dt ->
            front.step(dt)
            for (i in 0 until n) {
                if (i in passed) continue
                val c = cs[i] / wordWidth
                if (if (dir > 0) front.x >= c else front.x <= c) { passed += i; onPass(i) }
            }
            passed.size < n
        }
    }

    private fun ensureBook(): Book = book ?: Book().also { book = it }

    // The six effects, from v4.

    private fun save(r: () -> Float) {
        val n = letters.size
        val b = ensureBook()
        squash()
        b.gone = false; b.landed = false; b.torn = false; b.onLand = null; b.tear.set(0f); b.tug.set(0f)
        b.falling = false; b.phiS.set(b.phi).to(0f); b.a.to(1f, FADE); b.y.to(CY - 92f)
        if (b.s.x < .05f) b.s.set(.3f)
        b.s.to(1f, POP)
        at(110f) {
            sweep(1) { i ->
                // The letter goes from grey to white as its ink flies up into the book.
                tones[i].to(2f)
                val x = wordLeft + cs[i]
                val y = baseline - 7f
                val p = Ink({ book?.x ?: x }, { book?.let { it.y.x + 1f } ?: (y - 80f) }).apply {
                    ch = letters[i]; this.x = x; this.y = y; vx = (r() - .5f) * 40f; vy = -120f - r() * 50f; drag = 14f
                    life = .7f; fadeIn = .03f; fadeOut = .2f
                }
                p.d0 = hypot(p.targetX() - x, p.targetY() - y)
                p.home = { Triple(p.targetX(), p.targetY(), 170f) }
                parts += p
                if (i == n - 1) at(150f) { b.falling = true; b.phiV = 2f }
            }
        }
        b.onLand = {
            haptic(MenuKeys.Button.Save, "the book claps shut")
            b.sx.set(1.12f).to(1f); b.rot.kick(-1.6f)
            for (side in intArrayOf(-1, 1)) for (j in 0 until 5) {
                val d = if (j < 3) 1f else .6f
                parts += Part(Kind.Puff).apply {
                    back = d < 1f; x = b.x + side * 9f; y = b.y.x + 10f - j * 2f; vx = side * (90f + r() * 90f) * d; vy = -(10f + r() * 50f) * d
                    drag = 6.5f; ay = -40f; size = (1.3f + r() * 1.2f) * d; alpha = .75f * d; life = .45f + r() * .25f; fadeIn = .02f; fadeOut = .4f
                }
            }
            pop(KICK * .6f)
            at(240f) { b.y.to(CY - 118f); b.a.to(0f, EXIT, ::easeInOut); b.gone = true }
        }
    }

    private fun unsave(r: () -> Float) {
        val b = ensureBook()
        squash()
        b.gone = false; b.torn = false; b.landed = false; b.onLand = null; b.tear.set(0f); b.tug.set(0f)
        b.falling = false; b.phiS.set(b.phi).to(0f); b.a.to(1f, FADE); b.y.to(CY - 92f)
        if (b.s.x < .05f) b.s.set(.3f)
        b.s.to(1f, POP)
        at(250f) { b.tug.to(1f, WOBBLE); b.tear.to(1f, 150f, ::easeInOut) }
        at(420f) {
            haptic(MenuKeys.Button.Unsave, "the page rips free")
            val s = b.size / 24f * b.s.x
            val tug = b.tug.x
            b.torn = true; b.tug.set(0f); b.rot.kick(-2.4f); b.sx.kick(-.8f)
            parts += Leaf().apply {
                x = b.x + 5f * s + tug * 1.3f; y = b.y.x - tug * 1.2f; vx = 70f; vy = -90f; th = -.1f * tug; thw = 4f
                size = b.size * b.s.x; life = 2.3f; fadeIn = .001f; fadeOut = .6f
            }
            // The letters drain from white to grey, right to left, as the page tears.
            sweep(-1) { i -> tones[i].to(1f) }
            at(280f) { b.y.to(CY - 112f); b.a.to(0f, EXIT, ::easeInOut); b.gone = true }
        }
    }

    private fun favourite(r: () -> Float) {
        squash()
        heartAlpha.to(1f, 60f); heartHot.to(1f, 90f); heartCrack.set(0f)
        ghost = null
        heartScale.to(ANTICIPATE, SNAP)
        at(SQUASH) { heartScale.to(1f, POP).kick(7f); pop(KICK) }
        at(160f) {
            haptic(MenuKeys.Button.Favourite, "the hearts release")
            heartScale.kick(9f); heartAlpha.to(0f, 90f, ::easeOut)
            // The hearts burst from just over the word and rise over the app, as a warm wash goes over the letters, left to right.
            val hx = cx
            val hy = heartMiddle()
            sweep(1) { i -> tones[i].to(3f) }
            for (i in 0 until 14) {
                val isBack = i >= 10
                val d = if (isBack) .55f else 1f
                val ang = (-PI / 2).toFloat() + ((if (isBack) (i - 10 + r()) / 4f else (i + r()) / 10f) - .5f) * 2.4f
                val sp = (190f + r() * 260f) * (if (isBack) .7f else 1f)
                parts += Part(Kind.Heart).apply {
                    back = isBack; age = -r() * .05f; x = hx + cos(ang) * 4f; y = hy + sin(ang) * 4f; vx = cos(ang) * sp; vy = sin(ang) * sp
                    drag = AIR_LIGHT; ay = BUOYANT * d * (.6f + .7f * r()); swayA = 30f * d; swayF = 2.4f + r() * 2.2f; swayP = r() * 6f
                    rot = (r() - .5f) * .6f; w = (r() - .5f) * 3.2f; wdrag = 1.6f
                    size = (7f + r() * 8f) * d; alpha = if (isBack) .45f else .95f; life = 1.5f + r() * .9f; fadeIn = .04f; fadeOut = .6f; s0 = .35f
                }
            }
        }
    }

    private fun unfavourite(r: () -> Float) {
        squash()
        heartAlpha.to(1f, 60f); heartHot.to(1f, 90f); heartCrack.set(0f); heartScale.set(1f)
        at(60f) { heartCrack.to(1f, 120f, ::linear) }
        whenever({ heartCrack.x >= 1f }) {
            haptic(MenuKeys.Button.Unfavourite, "the heart cracks")
            heartAlpha.set(0f); ghost = null
            val hx = cx
            val hy = heartMiddle()
            // The favourite's colour drains back to white, right to left, as the heart breaks.
            sweep(-1) { i -> tones[i].to(2f) }
            for (side in intArrayOf(-1, 1)) parts += Part(Kind.Half).apply {
                this.side = side; x = hx + side * .5f; y = hy; vx = side * (46f + r() * 14f); vy = -(70f + r() * 15f); ay = 620f; drag = .8f
                w = side * (1.6f + r() * .6f); wdrag = .4f; size = 24f * HEART_SCALE; life = .95f; fadeIn = .001f; fadeOut = .5f
            }
            for (j in 0 until 4) {
                val side = if (j % 2 == 1) 1 else -1
                parts += Part(Kind.Shard).apply {
                    back = j > 1; x = hx + side * 2f; y = hy - 2f + j * 3f; vx = side * (70f + r() * 70f); vy = -(130f + r() * 90f); ay = 900f; drag = 1f
                    rot = r() * 6f; w = side * (8f + r() * 6f); size = 1.6f + r() * 1.1f; alpha = if (j > 1) .6f else 1f; life = .55f + r() * .2f
                    fadeIn = .001f; fadeOut = .3f
                }
            }
            dy.to(0f, POP).kick(80f)
        }
    }

    private fun block(r: () -> Float) {
        squash(.3f)
        strikeHot.set(1f); strikeAlpha.set(1f)
        penOn = true
        val x0 = wordLeft - 3f
        val span = wordWidth + 6f
        val n = letters.size
        at(if (strikeEnd.busy) 0f else 50f) {
            strikeEnd.to(1f, PEN)
            var lastX = x0 + span * strikeEnd.x
            var acc = 0f
            tracks += { dt ->
                val x = x0 + span * strikeEnd.x
                // Each letter darkens to Blocked's grey as the pen passes it.
                for (i in 0 until n) if (wordLeft + cs[i] <= x && tones[i].t > 0f) tones[i].to(0f)
                acc += max(0f, x - lastX)
                val v = (x - lastX) / max(dt, 1e-4f)
                lastX = x
                while (acc > 6f) {
                    acc -= 6f
                    val isBack = r() < .35f
                    val d = if (isBack) .55f else 1f
                    parts += Part(Kind.Fleck).apply {
                        back = isBack; this.x = x - 1f; y = baseline - 5.6f + (r() - .5f) * 1.2f; vx = v * .1f * d + (r() - .5f) * 50f; vy = -(30f + r() * 70f) * d
                        ay = 640f; drag = 3f; rot = r() * 6f; w = (r() - .5f) * 24f; size = (1f + r() * .9f) * d; alpha = .9f * d
                        life = .3f + r() * .28f; fadeIn = .01f; fadeOut = .2f
                    }
                }
                true
            }
            whenever({ strikeEnd.x >= .985f }) {
                haptic(MenuKeys.Button.Block, "the strike lands"); penOn = false; dy.kick(26f)
                tracks = ArrayList(); tones.forEach { it.to(0f) }
                strikePreview.set(0f); ghost = null
                at(300f) { strikeHot.to(0f, SETTLE, ::easeInOut) }
            }
        }
    }

    private fun unblock(r: () -> Float) {
        squash(.3f)
        strikeHot.to(1f, 90f); strikeAlpha.to(1f, 120f)
        penOn = true
        val x0 = wordLeft - 3f
        val span = wordWidth + 6f
        val n = letters.size
        at(if (strikeEnd.busy) 0f else 50f) {
            strikeEnd.to(0f, PEN)
            var lastX = x0 + span * strikeEnd.x
            var acc = 0f
            tracks += {
                val x = x0 + span * strikeEnd.x
                // Each letter lightens to Not saved's grey as the strike pulls back off it.
                for (i in 0 until n) if (wordLeft + cs[i] >= x && tones[i].t < 1f) tones[i].to(1f)
                acc += max(0f, lastX - x)
                lastX = x
                while (acc > 10f) {
                    acc -= 10f
                    parts += Part(Kind.Fleck).apply {
                        colour = palette.cream; this.x = x + 1f; y = baseline - 5.6f; vx = (r() - .5f) * 30f; vy = -(25f + r() * 45f); ay = -20f; drag = 3f
                        rot = r() * 6f; w = (r() - .5f) * 10f; size = .9f + r() * .6f; alpha = .7f; life = .45f + r() * .3f; fadeIn = .02f; fadeOut = .3f
                    }
                }
                true
            }
            whenever({ strikeEnd.x <= .015f }) {
                haptic(MenuKeys.Button.Unblock, "the strike is gone"); penOn = false; strikeEnd.set(0f); strikeHot.set(0f); strikeAlpha.set(1f)
                tracks = ArrayList(); tones.forEach { it.to(1f) }; ghost = null
                pop(KICK * .5f)
            }
        }
    }

    // Per frame.

    /** Moves everything on by [dt] seconds: the cues, then the springs in steps of at most 1/480 s, then the tweens. */
    fun step(dt: Float) {
        time += dt
        // Cues that fall due, in order. One may add more, which run in the same pass if they are due too.
        val queue = cues
        var i = 0
        while (i < queue.size) {
            val c = queue[i]
            if (if (c.at != null) time >= c.at else c.pred!!()) { queue.removeAt(i); c.fn() } else i++
        }
        // Each running track moves on, and stays while it says so. A cue that ended them all (the strike landing) already has.
        tracks = ArrayList(tracks.filter { it(dt) })
        val sub = max(1, ceil(dt / (1f / 480f)).toInt())
        val h = dt / sub
        repeat(sub) {
            tones.forEach { it.step(h) }
            sx.step(h); sy.step(h); dy.step(h); strikeEnd.step(h); heartScale.step(h)
            book?.let { b ->
                b.y.step(h); b.s.step(h); b.rot.step(h); b.sx.step(h); b.tug.step(h)
                if (b.falling) {
                    // The cover falls shut under its own weight and bounces once on the page.
                    b.phiV += 150f * h
                    b.phi += b.phiV * h
                    if (b.phi >= PI.toFloat()) {
                        b.phi = PI.toFloat()
                        if (!b.landed) { b.landed = true; b.onLand?.invoke() }
                        b.phiV = if (abs(b.phiV) > 2f) -b.phiV * .16f else 0f
                    }
                } else {
                    b.phiS.step(h)
                    b.phi = b.phiS.x
                }
            }
            for (k in 0 until parts.size) parts[k].step(h)
        }
        leans.forEach { it.step(dt) }
        strikePreview.step(dt); strikeHot.step(dt); strikeAlpha.step(dt)
        heartAlpha.step(dt); heartHot.step(dt); heartCrack.step(dt)
        book?.let { b ->
            b.a.step(dt); b.tear.step(dt)
            if (b.gone && !b.a.busy && b.a.x <= .001f) book = null
        }
        parts.removeAll { it.dead }
        crossfade?.let { fade ->
            fade.step(dt)
            if (!fade.busy) { crossfade = null; fadeFrom = null }
        }
    }

    /** Whether anything still moves, so the bar must draw again. At rest nothing does: the word's colour is its status. */
    val busy: Boolean
        get() = cues.isNotEmpty() || tracks.isNotEmpty() || parts.isNotEmpty() || crossfade != null || book != null ||
            tones.any { it.busy } || leans.any { it.busy } || strikePreview.busy ||
            sx.busy || sy.busy || dy.busy || strikeEnd.busy || strikeHot.busy || strikeAlpha.busy ||
            heartAlpha.busy || heartScale.busy || heartHot.busy || heartCrack.busy

    // Drawing.

    private var fade = 1f
    private fun rgba(rgb: Int, a: Float) = (clamp(a * fade) * 255).toInt().coerceIn(0, 255) shl 24 or (rgb and 0xFFFFFF)

    /** The word, its strike and its previews: on the bar, under the pill. */
    fun paintWord(c: MenuCanvas) {
        val from = fadeFrom
        val p = crossfade?.x
        if (from != null && p != null) {
            from.fade = 1f - p; from.paintWordOnly(c); from.fade = 1f
            fade = p; paintWordOnly(c); fade = 1f
        } else paintWordOnly(c)
    }

    /** Everything over the word, up to [ABOVE] dp over the bar: the pressed heart, the book and the particles. */
    fun paintEffects(c: MenuCanvas) {
        val from = fadeFrom
        val p = crossfade?.x
        if (from != null && p != null) {
            from.fade = 1f - p; from.paintEffectsOnly(c); from.fade = 1f
            fade = p; paintEffectsOnly(c); fade = 1f
        } else paintEffectsOnly(c)
    }

    private fun paintWordOnly(c: MenuCanvas) {
        c.save()
        c.translate(cx, baseline + dy.x); c.scale(sx.x, sy.x); c.translate(-cx, -baseline)
        // Every letter solid, in its look's colour, leaned toward the next look while a finger rests on a button.
        for (i in letters.indices) c.text(letters[i], wordLeft + xs[i], baseline, FONT_DP, rgba(letterColour(i), 1f))
        val x0 = wordLeft - 3f
        val x1 = wordLeft + wordWidth + 3f
        val sy = baseline - 5.6f
        val e = strikeEnd.x
        if (strikePreview.x > 0f) strokeLine(c, x0 + (x1 - x0) * clamp(e), x1, sy, palette.grey, strikePreview.x, false)
        if (e > .002f) {
            val xe = x0 + (x1 - x0) * clamp(e, 0f, 1.02f)
            strokeLine(c, x0, xe, sy, mix(palette.grey, palette.accent, strikeHot.x), strikeAlpha.x, true)
            if (penOn) c.circle(xe, sy, 1.8f, rgba(palette.accent, strikeAlpha.x))
        }
        c.restore()
    }

    private fun paintEffectsOnly(c: MenuCanvas) {
        // Depth: the small, faint ones behind, and the ink under the book, which hides it once it shuts.
        for (p in parts) if (p.back || p.kind == Kind.Ink) part(c, p)
        if (heartAlpha.x > 0f) {
            // The pressed heart, just over the word: it pops about its tip, which stays put.
            val k = HEART_SCALE * max(0f, heartScale.x)
            drawHeartOn(c, cx, heartFoot(), k, mix(palette.grey, palette.accent, heartHot.x), heartAlpha.x)
            drawCrackOn(c, cx, heartFoot(), k, heartCrack.x, palette.paper, heartAlpha.x)
        }
        book?.let { drawBook(c, it, palette.accent) }
        for (p in parts) if (!p.back && p.kind != Kind.Ink) part(c, p)
    }

    /**
     * Letter [i]'s colour now: Blocked's dim grey at tone 0, Not saved's grey at 1, Saved's white at 2, Favourite's colour at 3, and
     * in between on the way.
     */
    fun letterColour(i: Int): Int {
        val tone = tones[i].x + leans[i].x
        return when {
            tone <= 1f -> mix(palette.dim, palette.grey, clamp(tone))
            tone <= 2f -> mix(palette.grey, palette.ink, clamp(tone - 1f))
            else -> mix(palette.ink, palette.favourite, clamp(tone - 2f))
        }
    }

    private fun drawHeart(c: MenuCanvas, x: Float, y: Float, s: Float, rot: Float, colour: Int, a: Float) {
        if (a <= .002f || s <= 0f) return
        c.save(); c.translate(x, y); c.rotate(rot); c.scale(s / 24f, s / 24f); c.translate(-12f, -12.4f)
        c.fill(HEART, rgba(colour, a)); c.restore()
    }

    private fun drawCrack(c: MenuCanvas, x: Float, y: Float, s: Float, p: Float, colour: Int, a: Float) {
        if (p <= 0f || a <= .002f) return
        c.save(); c.translate(x, y); c.scale(s / 24f, s / 24f); c.translate(-12f, -12.4f)
        c.stroke(partOf(CRACK, p), 2.2f, rgba(colour, a)); c.restore()
    }

    /** A heart standing on its tip at [x], [bottom], [s] dp to a unit of its 24 grid. */
    private fun drawHeartOn(c: MenuCanvas, x: Float, bottom: Float, s: Float, colour: Int, a: Float) =
        drawHeart(c, x, bottom - HEART_BELOW * s, 24f * s, 0f, colour, a)

    private fun drawCrackOn(c: MenuCanvas, x: Float, bottom: Float, s: Float, p: Float, colour: Int, a: Float) =
        drawCrack(c, x, bottom - HEART_BELOW * s, 24f * s, p, colour, a)

    /** Where the pressed heart stands, its tip [HEART_GAP] dp over the word's tallest letter, centred on the word. */
    fun heartFoot(): Float = baseline - max(TALL * FONT_DP, layout.top) - HEART_GAP

    /** The pressed heart's middle, where a favourite's burst and an unfavourite's crack start from. */
    fun heartMiddle(): Float = heartFoot() - HEART_BELOW * HEART_SCALE

    private fun pageLines(c: MenuCanvas, side: Int, a: Float, colour: Int) {
        val lines = MenuPath()
        for (y in floatArrayOf(9.4f, 12.2f, 15f)) {
            val x0 = if (side > 0) 14.4f else 9.6f
            val x1 = if (side > 0) 19.6f - (if (y > 14f) 2f else 0f) else 4.4f + (if (y > 14f) 2f else 0f)
            lines.moveTo(x0, y).lineTo(x1, y)
        }
        c.stroke(lines, .9f, rgba(colour, a * .55f))
    }

    /** The book, from the icon. Its pages are the bar's own colour under the line, so nothing shows through it. */
    private fun drawBook(c: MenuCanvas, b: Book, colour: Int) {
        val a = b.a.x * clamp(b.s.x * 4f)
        if (a <= .002f) return
        val s = max(0f, b.s.x)
        c.save()
        c.translate(b.x, b.y.x + 13f); c.rotate(b.rot.x); c.scale(b.sx.x, 2f - b.sx.x); c.translate(0f, -13f)
        c.scale(s * b.size / 24f, s * b.size / 24f); c.translate(-12f, -12.5f)
        val line = rgba(colour, a)
        if (!b.torn) {
            c.save()
            val tug = b.tug.x
            if (tug != 0f) { c.translate(12f, 13.5f); c.rotate(-tug * .07f); c.translate(tug * .8f, -tug * .7f); c.translate(-12f, -13.5f) }
            c.fill(PAGE_R, rgba(palette.paper, a)); c.fill(PAGE_R, rgba(colour, a * .16f)); c.stroke(PAGE_R, 2f, line); pageLines(c, 1, a, colour)
            if (b.tear.x > 0f) c.stroke(partOf(TEAR.reversed(), b.tear.x), 1.3f, line)
            c.restore()
        } else c.stroke(partOf(TEAR, 1f), 1.3f, rgba(colour, a * .8f))
        c.save()
        val cosPhi = cos(b.phi)
        c.translate(12f, 0f); c.scale(if (abs(cosPhi) < .02f) .02f * (if (cosPhi < 0f) -1f else 1f) else cosPhi, 1f); c.translate(-12f, 0f)
        c.fill(PAGE_L, rgba(palette.paper, a)); c.fill(PAGE_L, rgba(colour, a * (.16f + .12f * clamp(-cosPhi)))); c.stroke(PAGE_L, 2f, line)
        if (cosPhi > .3f) pageLines(c, -1, a * cosPhi, colour)
        c.restore()
        c.stroke(SPINE, 2f, line)
        c.restore()
    }

    /** The strike: a pen's taper where it starts, a round end where it stops. */
    private fun strokeLine(c: MenuCanvas, xa: Float, xb: Float, y: Float, colour: Int, a: Float, taper: Boolean) {
        if (xb - xa < .3f || a <= .002f) return
        val hw = 1.05f
        val shape = MenuPath()
        if (taper) {
            val t = min(9f, xb - xa)
            shape.moveTo(xa, y).lineTo(xa + t, y - hw).lineTo(xb, y - hw).lineTo(xb, y + hw).lineTo(xa + t, y + hw).close()
        } else shape.moveTo(xa, y - hw).lineTo(xb, y - hw).lineTo(xb, y + hw).lineTo(xa, y + hw).close()
        c.fill(shape, rgba(colour, a))
        c.circle(xb, y, hw, rgba(colour, a))
        if (!taper) c.circle(xa, y, hw, rgba(colour, a))
    }

    private fun part(c: MenuCanvas, p: Part) {
        var a = p.a
        if (a <= .002f) return
        // Fade at the layer's edges, never cut.
        a *= clamp((p.y + ABOVE) / 28f) * clamp((BAR + 4f - p.y) / 20f)
        if (a <= .002f) return
        val s = p.sc.x
        val accent = palette.accent
        when (p.kind) {
            Kind.Heart -> drawHeart(c, p.x, p.y, p.size * s, p.rot, accent, a)
            Kind.Puff -> c.circle(p.x, p.y, p.size * (1f + 1.2f * p.age) * s, rgba(accent, a))
            Kind.Fleck -> {
                c.save(); c.translate(p.x, p.y); c.rotate(p.rot)
                c.rect(-p.size / 2, -p.size * .3f, p.size / 2, p.size * .3f, rgba(p.colour ?: accent, a)); c.restore()
            }
            Kind.Shard -> {
                c.save(); c.translate(p.x, p.y); c.rotate(p.rot)
                c.fill(MenuPath.poly(listOf(0f to -p.size, p.size * .8f to p.size * .7f, -p.size * .7f to p.size * .5f), true), rgba(accent, a))
                c.restore()
            }
            Kind.Half -> {
                c.save(); c.translate(p.x, p.y); c.rotate(p.rot); c.scale(p.size / 24f, p.size / 24f); c.translate(-12f, -12.4f)
                c.clip(if (p.side < 0) HALF_LEFT else HALF_RIGHT); c.fill(HEART, rgba(accent, a)); c.restore()
            }
            Kind.Ink -> {
                val ink = p as Ink
                val k = .2f + .5f * clamp(hypot(p.x - ink.targetX(), p.y - ink.targetY()) / ink.d0)
                c.text(p.ch, p.x, p.y, FONT_DP * k, rgba(accent, a * (.3f + .5f * k)), centred = true)
            }
            Kind.Leaf -> {
                val leaf = p as Leaf
                c.save(); c.translate(p.x, p.y); c.rotate(leaf.th * .55f)
                c.scale(1f - .12f * abs(sin(leaf.th)), .62f + .38f * cos(leaf.th * 1.5f))
                c.scale(p.size / 24f, p.size / 24f); c.translate(-17f, -12.5f)
                c.fill(LEAF, rgba(palette.paper, a)); c.fill(LEAF, rgba(accent, a * .2f)); c.stroke(LEAF, 2f, rgba(accent, a))
                pageLines(c, 1, a, accent); c.restore()
            }
            Kind.Dot -> c.circle(p.x, p.y, p.size, rgba(accent, a))
        }
    }

    // Read by the checks.

    /** The book's box in dp as it is drawn now, or null: for the check that it never sits across the bar's top edge. */
    fun bookBox(): FloatArray? {
        val b = book ?: return null
        if (b.a.x * clamp(b.s.x * 4f) <= .002f) return null
        val s = max(0f, b.s.x) * b.size / 24f
        val sy = 2f - b.sx.x
        // The pages run from 4.6 to 20.4 on the icon's grid, about its middle at 12.5, and the book turns about 13 dp below its centre.
        val top = b.y.x + 13f + sy * (-13f + (4.6f - 12.5f) * s)
        val bottom = b.y.x + 13f + sy * (-13f + (20.4f - 12.5f) * s)
        return floatArrayOf(b.x - 12f * s * b.sx.x, top - 2f, b.x + 12f * s * b.sx.x, bottom + 2f)
    }

    /** Whether the pressed heart shows now: only while Favourite's or Unfavourite's effect plays, never at rest or in a preview. */
    fun heartShown(): Boolean = heartAlpha.x * fade > .002f

    /** How far the book's cover has turned shut, 0 open to 1 shut. */
    fun bookShut(): Float = book?.let { (it.phi / PI.toFloat()).coerceIn(0f, 1f) } ?: 0f

    companion object {
        /** The menu word's size: as the mockup, 20 dp in Inter Bold. */
        const val FONT_DP = 20f
        /** How far a resting finger's preview moves the word toward its next look: about 60% of the way (owner, 2026-11-13). */
        const val LEAN = .6f
        /** The bar's height, and how far over it the effects may rise. */
        const val BAR = 56f
        const val ABOVE = 200f
        const val CY = BAR / 2
        /** How far over the bar's top Save's flying book keeps its bottom, at the least: it never crosses the edge (owner, 2026-11-13). */
        const val CLEAR = 8f
        /** How high the menu font's tallest small letters reach over the baseline, in em: Inter's i and j dots, 1572 of 2048. Capitals reach 1490. */
        const val TALL = 1572f / 2048f
        /**
         * Favourite's and Unfavourite's pressed heart, part of their effects only: [HEART_SCALE] dp to a unit of its 24 grid, about
         * 12 dp across, its tip [HEART_GAP] dp over the word's tallest letter.
         */
        const val HEART_SCALE = .6f
        const val HEART_GAP = 3f
        /** How far the heart reaches below its middle on its 24 grid: 21.35, its tip, less 12.4. */
        const val HEART_BELOW = 21.35f - 12.4f
        /** How tall the heart is on its grid: from its top at 3 to its tip at 21.35. */
        const val HEART_TALL = 21.35f - 3f

        // The motion language, as v4's tokens (ms; springs as mass, stiffness, damping).
        const val PREVIEW = 170f
        const val SQUASH = 70f
        const val FADE = 220f
        const val SETTLE = 520f
        const val EXIT = 380f
        const val REDUCED = 200f
        const val ANTICIPATE = .98f
        const val KICK = 2f
        const val AIR_LIGHT = 3.2f
        const val BUOYANT = -240f
        val POP = Spring.Cfg(1f, 520f, 16f)
        val SNAP = Spring.Cfg(1f, 600f, 49f)
        val PEN = Spring.Cfg(1f, 420f, 41f)
        val SWEEP = Spring.Cfg(1f, 150f, 24.5f)
        val WOBBLE = Spring.Cfg(1f, 180f, 7f)
        val DRIFT = Spring.Cfg(1f, 40f, 12.6f)

        /** A rung's look, as a letter's tone: Blocked 0, Not saved 1, Saved 2, Favourite 3. */
        fun tone(step: MenuKeys.Step): Float = when (step) {
            MenuKeys.Step.Blocked -> 0f
            MenuKeys.Step.New -> 1f
            MenuKeys.Step.Saved -> 2f
            MenuKeys.Step.Favourite -> 3f
        }

        /** The rung that [button] takes a word to. */
        fun next(button: MenuKeys.Button): MenuKeys.Step? = when (button) {
            MenuKeys.Button.Save -> MenuKeys.Step.Saved
            MenuKeys.Button.Unsave -> MenuKeys.Step.New
            MenuKeys.Button.Favourite -> MenuKeys.Step.Favourite
            MenuKeys.Button.Unfavourite -> MenuKeys.Step.Saved
            MenuKeys.Button.Block -> MenuKeys.Step.Blocked
            MenuKeys.Button.Unblock -> MenuKeys.Step.New
            MenuKeys.Button.Favourited -> null
        }

        // The icons' shapes, on their 24 grid: the app's hearts, the book and the tear.
        val HEART = MenuPath.svg("M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z")
        val PAGE_L = MenuPath.svg("M12,6.6C10.3,5.3 8,4.6 5.5,4.6c-1.3,0 -2.5,0.2 -3.5,0.6V19c1,-0.4 2.2,-0.6 3.5,-0.6 2.5,0 4.8,0.7 6.5,2")
        val PAGE_R = MenuPath.svg("M12,6.6c1.7,-1.3 4,-2 6.5,-2 1.3,0 2.5,0.2 3.5,0.6V19c-1,-0.4 -2.2,-0.6 -3.5,-0.6 -2.5,0 -4.8,0.7 -6.5,2")
        val SPINE = MenuPath.svg("M12,6.6V20.4")
        /** The page that tears out: the right page, closed along the spine by the tear, bottom to top. */
        val TEAR = listOf(12f to 20.4f, 12.9f to 18.3f, 11.6f to 16.2f, 12.9f to 14.1f, 11.6f to 12f, 12.9f to 9.9f, 11.6f to 7.8f, 12f to 6.6f)
        val LEAF = MenuPath.svg("M12,6.6c1.7,-1.3 4,-2 6.5,-2 1.3,0 2.5,0.2 3.5,0.6V19c-1,-0.4 -2.2,-0.6 -3.5,-0.6 -2.5,0 -4.8,0.7 -6.5,2" +
            TEAR.drop(1).joinToString("") { (x, y) -> "L$x,$y" } + "Z")
        /** The heart's crack, top to bottom, and the two halves it leaves. */
        val CRACK = listOf(12.2f to 4.4f, 10.5f to 9f, 13.4f to 12.3f, 11.2f to 16.4f, 12f to 21.6f)
        val HALF_LEFT = half(-1)
        val HALF_RIGHT = half(1)

        private fun half(side: Int): MenuPath {
            val e = if (side < 0) -2f else 26f
            return MenuPath.poly(listOf(e to -2f, 12.2f to -2f) + CRACK + listOf(12f to 26f, e to 26f), true)
        }

        /** The first [p] of the line through [points], as a path to stroke. */
        fun partOf(points: List<Pair<Float, Float>>, p: Float): MenuPath {
            val path = MenuPath().moveTo(points[0].first, points[0].second)
            val n = points.size - 1
            val u = clamp(p) * n
            for (i in 1..n) {
                if (i <= u) path.lineTo(points[i].first, points[i].second)
                else {
                    val f = u - (i - 1)
                    if (f > 0f) path.lineTo(lerp(points[i - 1].first, points[i].first, f), lerp(points[i - 1].second, points[i].second, f))
                    break
                }
            }
            return path
        }

        fun easeOut(t: Float) = 1f - (1f - t).pow(3)
        fun easeInOut(t: Float) = if (t < .5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
        fun linear(t: Float) = t
        fun clamp(v: Float, a: Float = 0f, b: Float = 1f) = max(a, min(b, v))
        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        /** [a] turned towards [b] by [t], channel by channel, as RGB. */
        fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(shift: Int) = lerp(((a shr shift) and 0xFF).toFloat(), ((b shr shift) and 0xFF).toFloat(), t).toInt().coerceIn(0, 255) shl shift
            return ch(16) or ch(8) or ch(0)
        }

        /** v4's seeded random numbers, so a word always gets the same layout. */
        fun random(seed: Int): () -> Float {
            var s = seed
            return {
                s += 0x6D2B79F5
                var t = s
                t = (t xor (t ushr 15)) * (t or 1)
                t = t xor (t + (t xor (t ushr 7)) * (t or 61))
                (((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0).toFloat()
            }
        }

        fun seedOf(text: String): Int = text.fold(-2128831035) { h, ch -> (h xor ch.code) * 16777619 }
    }
}
