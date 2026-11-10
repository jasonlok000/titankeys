package com.atlas.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.atlas.engine.TypingSettings
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** The heavy diagonal of the logo's swipe line, in degrees. A line at this angle rises towards the right. */
private const val SWEEP_ANGLE = -40.5f

/** The full-bleed background. A new colour comes in behind the cream line, at the angle of the logo. */
internal class GuideBackdrop(context: Context) : View(context) {
    private val paint = Paint()
    private var current = GuideColours.ORANGE
    private var next = GuideColours.ORANGE
    private var start = -1L

    fun show(colour: Int) {
        current = colour
        next = colour
        start = -1
        invalidate()
    }

    fun wipeTo(colour: Int) {
        if (colour == next && start < 0) return
        current = next
        next = colour
        start = GuideMotion.now()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(current)
        if (start < 0) return
        val t = GuideMotion.progress(start, SWEEP_MILLIS)
        if (t >= 1f) {
            canvas.drawColor(next)
            current = next
            start = -1
            return
        }
        val reach = hypot(width.toFloat(), height.toFloat())
        val cx = width / 2f
        val cy = height / 2f
        val front = -reach / 2 + reach * GuideMotion.easeOut(t)
        canvas.save()
        canvas.rotate(SWEEP_ANGLE, cx, cy)
        paint.color = next
        canvas.drawRect(-reach, -reach, reach * 2, cy + front, paint)
        canvas.restore()
        postInvalidateOnAnimation()
    }

    companion object {
        const val SWEEP_MILLIS = 620L
    }
}

/** What crosses in front of everything: the cream line, the mark and the flourish at the end. It takes no touches. */
internal class GuideOverlay(context: Context) : View(context) {
    private class Sweep(val start: Long, val thickness: Float)

    private val sweeps = ArrayList<Sweep>()
    private val cream = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GuideColours.CREAM }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mark = logoPath(MARK)
    private val line = logoPath(LINE)
    private var markStart = -1L
    private var markEnd = -1L
    private var markColour = GuideColours.INK

    init {
        isClickable = false
        isFocusable = false
    }

    /** Sends the cream line across the screen, after [delay] ms. */
    fun sweep(delay: Long = 0, thickness: Float = THICKNESS) {
        if (!GuideMotion.enabled) return
        sweeps += Sweep(GuideMotion.now() + delay, dp(thickness))
        invalidate()
    }

    /** The logo appears in the wake of a line. */
    fun showMark(colour: Int) {
        markColour = colour
        markStart = GuideMotion.now()
        markEnd = -1
        sweep(thickness = THICKNESS * 1.6f)
        invalidate()
    }

    fun hideMark() {
        if (markStart < 0) return
        markEnd = GuideMotion.now()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val reach = hypot(width.toFloat(), height.toFloat())
        val cx = width / 2f
        val cy = height / 2f
        var moving = false
        if (markStart >= 0) moving = drawMark(canvas, reach, cx, cy)
        val each = sweeps.iterator()
        while (each.hasNext()) {
            val sweep = each.next()
            val t = GuideMotion.progress(sweep.start, GuideBackdrop.SWEEP_MILLIS)
            if (t >= 1f) {
                each.remove()
                continue
            }
            moving = true
            if (GuideMotion.now() < sweep.start) continue
            val front = -reach / 2 + reach * GuideMotion.easeOut(t)
            canvas.save()
            canvas.rotate(SWEEP_ANGLE, cx, cy)
            canvas.drawRect(-reach, cy + front - sweep.thickness / 2, reach * 2, cy + front + sweep.thickness / 2, cream)
            canvas.restore()
        }
        if (moving) postInvalidateOnAnimation()
    }

    private fun drawMark(canvas: Canvas, reach: Float, cx: Float, cy: Float): Boolean {
        val t = GuideMotion.progress(markStart, GuideBackdrop.SWEEP_MILLIS)
        val out = if (markEnd < 0) 0f else GuideMotion.progress(markEnd, MARK_OUT_MILLIS)
        if (out >= 1f) {
            markStart = -1
            markEnd = -1
            return false
        }
        // The artwork fills its 1080 square with room around it. This makes the shape itself about half of the screen.
        val side = min(width, height) * MARK_SCALE * (1f + out * MARK_OUT_GROWTH)
        val scale = side / 1080f
        canvas.save()
        // The mark is uncovered by the line, so it clips to what the line has passed.
        canvas.rotate(SWEEP_ANGLE, cx, cy)
        canvas.clipRect(-reach, -reach, reach * 2, cy + (-reach / 2 + reach * GuideMotion.easeOut(t)))
        canvas.rotate(-SWEEP_ANGLE, cx, cy)
        canvas.translate(cx - side / 2, cy - side / 2)
        canvas.scale(scale, scale)
        markPaint.color = GuideMotion.faded(markColour, 1f - out)
        canvas.drawPath(mark, markPaint)
        markPaint.color = GuideMotion.faded(GuideColours.CREAM, 1f - out)
        canvas.drawPath(line, markPaint)
        canvas.restore()
        return t < 1f || markEnd >= 0
    }

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private companion object {
        const val THICKNESS = 5f
        /** The size of the mark's whole artwork, against the shorter side of the screen. */
        const val MARK_SCALE = 1.15f
        const val MARK_OUT_MILLIS = 340L
        const val MARK_OUT_GROWTH = .18f
        // From android/src/main/res/drawable/atlas_icon_foreground.xml.
        const val MARK = "M757.7,328.4 L760.8,328.4 L602.1,540 L760.8,751.6 L657.8,751.6 L523.4,572.2 L523.4,751.6 L438.8,751.6 L438.8,600.9 Z " +
            "M382.7,328.4 L632,328.4 L568.5,413 L523.4,413 L523.4,507.8 L657.8,328.4 L760.8,328.4 L607.4,532.9 L538.3,592 L523.4,572.2 L523.4,604.7 L438.8,677 L438.8,413 L319.2,413 Z"
        const val LINE = "M289.8,756.2 L303.6,767.2 L808.6,335.8 L794.8,324.7 Z"

        /** Reads the simple moves and lines of the logo's path data. */
        fun logoPath(data: String) = Path().apply {
            for (part in data.split(' ')) {
                if (part == "Z") {
                    close()
                    continue
                }
                val (x, y) = part.substring(1).split(',').map { it.toFloat() }
                if (part[0] == 'M') moveTo(x, y) else lineTo(x, y)
            }
        }
    }
}

/** A real text field that says when its selection changes. */
private class GuideField(context: Context) : EditText(context) {
    /** Android calls [onSelectionChanged] while it builds the view, so this has to work before it is set. */
    var changed: (() -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        changed?.invoke()
    }
}

/**
 * The interactive guide. It opens after setup and sells the keyboard by having you use it: each step waits for the real gesture
 * on the real keyboard, in a real text field. Everything that a step says and checks is in [GUIDE_STEPS].
 *
 * A step starts on ink. When you do it, the screen sweeps to orange, the phone gives a pulse and a short line lands. The step then
 * stays, so you can try the gesture more, until you push Next. Next also moves on from a step that you do not want to do.
 *
 * Practice text teaches the keyboard nothing: the field asks for no personalised learning, like the recorder's field.
 */
class GuideActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val preferences by lazy { getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }
    private val flow by lazy { GuideFlow(::readSettings) }
    private val haptics by lazy { Haptics(this) }
    private val input by lazy { getSystemService(InputMethodManager::class.java) }

    private lateinit var root: FrameLayout
    private lateinit var backdrop: GuideBackdrop
    private lateinit var overlay: GuideOverlay
    private lateinit var column: LinearLayout
    private lateinit var segments: LinearLayout
    private lateinit var next: TextView
    private lateinit var previous: TextView
    private lateinit var again: TextView
    private lateinit var close: ImageView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var hint: TextView
    private lateinit var offer: TextView
    private lateinit var field: GuideField
    private lateinit var keys: GuideKeyboardView
    private lateinit var reaction: TextView

    private val keyboardChanges = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = checkKeyboard()
    }
    private var stopEvents: (() -> Unit)? = null
    private var stopTrace: (() -> Unit)? = null
    /** Between a finished step and the next, events have no step to go to. */
    private var busy = true
    private var ready = false
    private var keyboardChecked = false
    private var started = false
    private var seeding = false
    private var panelOpen = false
    private var lastKey: String? = null
    private var travel = 0f
    private var lastPoint: FloatArray? = null
    private var tone = GuideTone.Ink
    private var bottomInset = 0
    /** The step is done, so the screen is orange and Next is filled. */
    private var passed = false
    /** Between two steps, Next does nothing. */
    private var changing = false
    /** After the line that lands, the step's words come back, so you can read them while you try more. */
    private val settleReaction = Runnable {
        hideReaction()
        for (view in listOf(title, body)) view.animate().alpha(1f).setDuration(FADE_MILLIS).start()
    }

    private var helpShown = false
    private val showHelp = Runnable {
        helpShown = true
        flow.step.help?.let(::showHint)
        offerFor(flow.step)
    }
    private val reseed = Runnable {
        val seed = flow.step.seed
        if (!busy && !seed.isNullOrEmpty() && field.text.isEmpty()) putSeed(seed)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        window.setBackgroundDrawable(ColorDrawable(GuideColours.INK))
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContentView(build())
        field.requestFocus()
        field.changed = ::fieldChanged
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = fieldChanged()
        })
        flow.start(preferences.getString(RESUME, null))
        applyTone(GuideTone.Ink)
        backdrop.show(backgroundOf(tone))
        for (view in listOf(title, body, hint, offer, next, again, close, segments, field, keys)) view.alpha = 0f
    }

    override fun onResume() {
        super.onResume()
        if (stopEvents == null) stopEvents = KeyboardEvents.listen { event -> runOnUiThread { handle(event) } }
        if (stopTrace == null) stopTrace = Trace.listen { message -> trace(message) }
        contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, keyboardChanges)
    }

    override fun onPause() {
        super.onPause()
        stopEvents?.invoke()
        stopEvents = null
        stopTrace?.invoke()
        stopTrace = null
        contentResolver.unregisterContentObserver(keyboardChanges)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        // The keyboard list can close without a change, so this checks each time the guide comes back.
        checkKeyboard()
        field.requestFocus()
        input.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
    }

    @Deprecated("Back reaches an activity here, because the app does not opt in to predictive back")
    override fun onBackPressed() {
        // Back goes to the step before, like the Back button. From the first step or the ending it leaves the guide.
        if (changing) return
        if (ready && flow.canGoBack && flow.step.chapter != GuideChapter.End) moveOn(forward = false) else leave()
    }

    // The screen.

    private fun build(): View {
        root = FrameLayout(this)
        backdrop = GuideBackdrop(this)
        overlay = GuideOverlay(this)
        keys = GuideKeyboardView(this)
        title = TextView(this).apply {
            typeface = HEAVY
            gravity = Gravity.BOTTOM or Gravity.START
            includeFontPadding = false
            letterSpacing = -.02f
            maxLines = 3
            setAutoSizeTextTypeUniformWithConfiguration(TITLE_SMALLEST, TITLE_LARGEST, 2, TypedValue.COMPLEX_UNIT_DIP)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body = TextView(this).apply {
            typeface = MEDIUM
            gravity = Gravity.TOP or Gravity.START
            includeFontPadding = false
            maxLines = 4
            setAutoSizeTextTypeUniformWithConfiguration(BODY_SMALLEST, BODY_LARGEST, 1, TypedValue.COMPLEX_UNIT_DIP)
        }
        hint = TextView(this).apply {
            typeface = MEDIUM
            includeFontPadding = false
            maxLines = 3
            visibility = View.GONE
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(14f), dp(18).toFloat()))
        }
        offer = TextView(this).apply {
            typeface = HEAVY
            gravity = Gravity.CENTER
            maxLines = 2
            visibility = View.GONE
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(15f), dp(20).toFloat()))
            setOnClickListener { offerPressed() }
        }
        next = TextView(this).apply {
            text = "Next"
            typeface = HEAVY
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(22), dp(10), dp(22), dp(10))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(15f), dp(20).toFloat()))
            setOnClickListener { nextPressed() }
        }
        previous = TextView(this).apply {
            text = "Back"
            typeface = HEAVY
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(15f), dp(20).toFloat()))
            setOnClickListener { backPressed() }
        }
        field = GuideField(this).apply {
            typeface = MEDIUM
            setTextColor(GuideColours.INK)
            setHintTextColor(GuideMotion.faded(GuideColours.INK, .45f))
            highlightColor = GuideMotion.faded(GuideColours.ORANGE, .5f)
            setTextCursorDrawable(GradientDrawable().apply {
                setColor(GuideColours.ORANGE)
                setSize(dp(3), dp(20))
            })
            background = GradientDrawable().apply {
                setColor(GuideColours.CREAM)
                cornerRadius = dp(FIELD_RADIUS).toFloat()
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(18f), dp(24).toFloat()))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            minLines = 1
            maxLines = 2
            isVerticalScrollBarEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        reaction = TextView(this).apply {
            typeface = HEAVY
            gravity = Gravity.CENTER
            alpha = 0f
            isClickable = false
            maxLines = 3
            setPadding(dp(GUTTER), 0, dp(GUTTER), 0)
            setAutoSizeTextTypeUniformWithConfiguration(REACTION_SMALLEST, REACTION_LARGEST, 2, TypedValue.COMPLEX_UNIT_DIP)
        }
        segments = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        for (index in 0 until flow.chapterCount) segments.addView(View(this),
            LinearLayout.LayoutParams(0, dp(SEGMENT_HEIGHT), 1f).apply { marginEnd = dp(4) })
        again = topAction("Start again") { startAgain() }.apply { visibility = View.GONE }
        close = ImageView(this).apply {
            setImageResource(R.drawable.panel_close)
            contentDescription = "Close"
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { leave() }
        }
        val topBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(segments, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(again, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(close, LinearLayout.LayoutParams(dp(TOP_ACTION), dp(TOP_ACTION)))
        }
        // Back to the step before, the offer to turn on a setting if a step needs one, and Next.
        val actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(previous, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) })
            addView(offer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) })
            addView(next, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(TOP_ACTION)))
            addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, TITLE_SHARE))
            addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, BODY_SHARE).apply { topMargin = dp(4) })
            addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            })
            addView(keys, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { topMargin = dp(8) })
        }
        root.addView(backdrop, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(reaction, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(REACTION_HEIGHT), Gravity.CENTER))
        root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            bottomInset = bars.bottom
            column.setPadding(dp(GUTTER) + bars.left, bars.top + dp(4), dp(GUTTER) + bars.right, bars.bottom + dp(8))
            fitKeys()
            insets
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitKeys() }
        return root
    }

    private fun topAction(text: String, press: () -> Unit) = TextView(this).apply {
        this.text = text
        typeface = HEAVY
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(13f), dp(17).toFloat()))
        setOnClickListener { press() }
    }

    /**
     * Gives the drawing of the keys what room there is. With the Sym panel open the screen is short, so the keys go away.
     * The panel shows the same keys, right above your finger.
     */
    private fun fitKeys() {
        val free = root.height - column.paddingTop - column.paddingBottom
        if (root.width == 0 || free <= 0) return
        val compact = free < dp(COMPACT_HEIGHT)
        // The line that lands stays in the room above the keyboard, also with the Sym panel open.
        reaction.translationY = -bottomInset / 2f
        val reactionSpace = reaction.layoutParams
        val reactionHeight = min(dp(REACTION_HEIGHT), free)
        if (reactionSpace.height != reactionHeight) {
            reactionSpace.height = reactionHeight
            root.post { reaction.requestLayout() }
        }
        val target = if (compact) 0 else min((root.width - column.paddingLeft - column.paddingRight) * KEYS_ASPECT, free * KEYS_SHARE).toInt()
        val params = keys.layoutParams
        if (params.height == target) return
        params.height = target
        keys.visibility = if (target == 0) View.GONE else View.VISIBLE
        root.post { keys.requestLayout() }
    }

    private fun backgroundOf(tone: GuideTone) = if (tone == GuideTone.Orange) GuideColours.ORANGE else GuideColours.INK

    /** The words take the colours of the background: cream on ink while you try, ink on orange once it is done. */
    private fun applyTone(next: GuideTone) {
        tone = next
        val onInk = next == GuideTone.Ink
        val strong = if (onInk) GuideColours.CREAM else GuideColours.INK
        title.setTextColor(strong)
        body.setTextColor(GuideMotion.faded(strong, BODY_ALPHA))
        // A hint says what happened, calmly: it is not a mistake that counts.
        hint.setTextColor(strong)
        hint.background = rounded(GuideMotion.faded(strong, HINT_FILL), HINT_RADIUS)
        offer.setTextColor(if (onInk) GuideColours.INK else GuideColours.CREAM)
        offer.background = rounded(if (onInk) GuideColours.ORANGE else GuideColours.INK, OFFER_RADIUS)
        again.setTextColor(strong)
        close.imageTintList = ColorStateList.valueOf(strong)
        reaction.setTextColor(strong)
        keys.tone = next
        paintNext()
        paintSegments(flow.chapterIndex)
        val appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (onInk) 0 else appearance, appearance)
    }

    /** Next is an outline while the step waits, and filled once the step is done. Back is always a quiet outline. */
    private fun paintNext() {
        val strong = if (tone == GuideTone.Ink) GuideColours.CREAM else GuideColours.INK
        previous.setTextColor(GuideMotion.faded(strong, NEXT_WAITING))
        previous.background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1).coerceAtLeast(2), GuideMotion.faded(strong, BACK_OUTLINE))
            cornerRadius = dp(OFFER_RADIUS).toFloat()
        }
        if (passed) {
            next.setTextColor(if (tone == GuideTone.Ink) GuideColours.INK else GuideColours.CREAM)
            next.background = rounded(strong, OFFER_RADIUS)
        } else {
            next.setTextColor(GuideMotion.faded(strong, NEXT_WAITING))
            next.background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), GuideMotion.faded(strong, NEXT_WAITING))
                cornerRadius = dp(OFFER_RADIUS).toFloat()
            }
        }
    }

    private fun rounded(colour: Int, radius: Int) = GradientDrawable().apply {
        setColor(colour)
        cornerRadius = dp(radius).toFloat()
    }

    private fun paintSegments(done: Int) {
        val strong = if (tone == GuideTone.Ink) GuideColours.ORANGE else GuideColours.INK
        for (index in 0 until segments.childCount) segments.getChildAt(index).background =
            rounded(if (index <= done) strong else GuideMotion.faded(strong, SEGMENT_OFF), SEGMENT_HEIGHT)
    }

    // The story.

    private fun begin() {
        if (started) return
        started = true
        val resumed = preferences.getString(RESUME, null) != null && flow.step.id != GUIDE_STEPS.first().id
        again.visibility = if (resumed) View.VISIBLE else View.GONE
        if (resumed || !GuideMotion.enabled) {
            reveal(flow.step)
            return
        }
        // The first thing you see: the line crosses the screen and leaves the mark behind.
        overlay.showMark(GuideColours.ORANGE)
        main.postDelayed({
            overlay.hideMark()
            reveal(flow.step)
        }, INTRO_MILLIS)
    }

    private fun reveal(step: GuideStep) {
        keys.appear()
        for (view in listOf(field, keys, next, again, close, segments)) view.animate().alpha(1f).setDuration(FADE_MILLIS).start()
        enter(step)
    }

    private fun enter(step: GuideStep) {
        lastPoint = null
        travel = 0f
        helpShown = false
        passed = false
        next.visibility = if (step.chapter == GuideChapter.End) View.GONE else View.VISIBLE
        previous.visibility = if (flow.canGoBack) View.VISIBLE else View.GONE
        paintNext()
        keys.show(step.cue)
        offerFor(step)
        swapIn(title, step.title, 0)
        swapIn(body, step.body, ENTER_STAGGER_MILLIS)
        hideHint()
        val seed = step.seed
        if (seed != null) putSeed(seed) else if (panelOpen && step.chapter != GuideChapter.Symbols && step.chapter != GuideChapter.QuickSettings) {
            // A skip can leave the panel up where the next step needs the keys.
            input.restartInput(field)
        }
        step.panelHint?.takeIf { !panelOpen }?.let(::showHint)
        paintSegments(flow.chapterIndex)
        main.removeCallbacks(showHelp)
        if (step.help != null) main.postDelayed(showHelp, HELP_AFTER_MILLIS)
        field.hint = step.placeholder.orEmpty()
        if (step.chapter == GuideChapter.End) preferences.edit().remove(RESUME).apply()
        else preferences.edit().putString(RESUME, flow.resumeId).apply()
        busy = false
    }

    private fun swapIn(view: TextView, text: String, delay: Long) {
        view.animate().cancel()
        view.text = text
        view.alpha = 0f
        view.translationX = dp(ENTER_OFFSET).toFloat()
        view.animate().alpha(1f).translationX(0f).setStartDelay(delay).setDuration(ENTER_MILLIS)
            .setInterpolator(OvershootInterpolator(ENTER_OVERSHOOT)).start()
    }

    private fun showHint(text: String) {
        hint.text = text
        if (hint.visibility != View.VISIBLE) {
            hint.visibility = View.VISIBLE
            hint.alpha = 0f
            hint.translationY = dp(8).toFloat()
        }
        hint.animate().alpha(1f).translationY(0f).setDuration(ENTER_MILLIS).setInterpolator(OvershootInterpolator(ENTER_OVERSHOOT)).start()
    }

    private fun hideHint() {
        hint.animate().cancel()
        hint.visibility = View.GONE
    }

    private fun offerFor(step: GuideStep) {
        val missing = flow.missing
        when {
            step.chapter == GuideChapter.End -> showOffer("Start typing")
            !ready -> showOffer("Choose TitanKeys")
            missing != null && (!missing.optional || helpShown) -> showOffer(missing.offer)
            else -> offer.visibility = View.GONE
        }
    }

    private fun showOffer(text: String) {
        offer.text = text
        offer.visibility = View.VISIBLE
        offer.alpha = 1f
    }

    private fun offerPressed() {
        val step = flow.step
        when {
            step.chapter == GuideChapter.End -> leave()
            !ready -> input.showInputMethodPicker()
            else -> flow.missing?.let { need ->
                writeNeed(need)
                // The keyboard reads its settings when the field starts.
                input.restartInput(field)
                offerFor(step)
                if (need == GuideNeed.FnAsCtrl) showHint("Now hold fn.")
                haptics.wordChanged()
            }
        }
    }

    private fun writeNeed(need: GuideNeed) = when (need) {
        GuideNeed.HoldSymbols -> TypingSettings.holdSymbols(this, true)
        GuideNeed.CursorControl -> TypingSettings.cursorControl(this, true)
        GuideNeed.SwipeTyping -> TypingSettings.swipeTyping(this, true)
        GuideNeed.FnAsCtrl -> TypingSettings.fnAsCtrl(this, true)
    }

    private fun readSettings() = GuideSettings(
        holdSymbols = TypingSettings.holdSymbols(this),
        cursorControl = TypingSettings.cursorControl(this),
        swipeTyping = TypingSettings.swipeTyping(this),
        fnAsCtrl = TypingSettings.fnAsCtrl(this),
        panelOpen = panelOpen,
    )

    private fun checkKeyboard() {
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true
        if (keyboardChecked && selected == ready) return
        keyboardChecked = true
        ready = selected
        if (!selected) {
            busy = true
            title.text = "Choose TitanKeys."
            body.text = "This guide needs TitanKeys as your keyboard."
            hideHint()
            offerFor(flow.step)
            for (view in listOf(title, body, close)) {
                view.animate().cancel()
                view.alpha = 1f
                view.translationX = 0f
            }
            return
        }
        if (!started) begin() else reveal(flow.step)
    }

    private fun putSeed(seed: String) {
        seeding = true
        field.setText(seed)
        field.setSelection(seed.length)
        seeding = false
    }

    private fun fieldChanged() {
        if (seeding || !::field.isInitialized) return
        val start = min(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        val end = max(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        handle(KeyboardEvent.FieldChanged(field.text.toString(), start, end))
        if (field.text.isEmpty() && !flow.step.seed.isNullOrEmpty()) {
            main.removeCallbacks(reseed)
            main.postDelayed(reseed, RESEED_MILLIS)
        }
    }

    private fun trace(message: JSONObject) {
        val path = message.getJSONArray("points")
        val points = (0 until path.length()).map { index ->
            val point = path.getJSONArray(index)
            floatArrayOf(point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
        }
        val reset = message.getBoolean("reset")
        val lifted = message.getString("event") == "lift"
        if (reset) {
            travel = 0f
            lastPoint = null
        }
        for (point in points) {
            lastPoint?.let { travel += hypot((point[0] - it[0]) * 10, (point[1] - it[1]) * 4) }
            lastPoint = point
        }
        keys.trace(points, reset, lifted)
        if (!busy && ready) judge(KeyboardEvent.Moved(travel, lifted))
    }

    private fun handle(event: KeyboardEvent) {
        when (event) {
            is KeyboardEvent.Pushed -> keyName(event.code)?.let {
                lastKey = it
                keys.light(it)
            }
            is KeyboardEvent.Resting -> if (event.key.isNotEmpty()) keys.light(event.key)
            is KeyboardEvent.HeldSymbol -> lastKey?.let { keys.flip(it, event.symbol) }
            is KeyboardEvent.ModifierState -> panelOpen = event.panel != Modifiers.Panel.Closed
            else -> Unit
        }
        if (!busy && ready) judge(event)
    }

    private fun judge(event: KeyboardEvent) {
        when (val verdict = flow.event(event)) {
            GuideVerdict.Done -> stepDone()
            is GuideVerdict.Hint -> showHint(verdict.text)
            GuideVerdict.Wait -> Unit
        }
    }

    /**
     * The real gesture happened. The screen sweeps to orange, the phone gives a pulse, and a line lands. The step stays, so you can
     * try it more: the keys still answer, and only the checks stop. Next is filled now.
     */
    private fun stepDone() {
        busy = true
        passed = true
        main.removeCallbacks(showHelp)
        hideHint()
        haptics.guideChapter()
        keys.celebrate()
        backdrop.wipeTo(GuideColours.ORANGE)
        overlay.sweep()
        main.postDelayed({ if (passed) applyTone(GuideTone.Orange) }, WIPE_MILLIS)
        showReaction(flow.step.done)
        main.removeCallbacks(settleReaction)
        main.postDelayed(settleReaction, REACTION_MILLIS)
    }

    private fun nextPressed() {
        if (changing || !ready || flow.finished) return
        moveOn()
    }

    private fun backPressed() {
        if (changing || !ready || !flow.canGoBack) return
        moveOn(forward = false)
    }

    private fun showReaction(text: String) {
        reaction.text = text
        reaction.animate().cancel()
        reaction.alpha = 0f
        reaction.scaleX = .78f
        reaction.scaleY = .78f
        reaction.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(REACTION_IN_MILLIS)
            .setInterpolator(OvershootInterpolator(REACTION_OVERSHOOT)).start()
        for (view in listOf(title, body)) view.animate().alpha(REACTION_DIM).setDuration(FADE_MILLIS).start()
    }

    /** The story goes on, or back a step: to ink for a step behind the cream line, or orange for the ending. */
    private fun moveOn(forward: Boolean = true) {
        changing = true
        busy = true
        passed = false
        again.visibility = View.GONE
        main.removeCallbacks(showHelp)
        main.removeCallbacks(settleReaction)
        hideHint()
        hideReaction()
        if (forward) flow.advance() else flow.back()
        val coming = flow.step
        val target = if (coming.chapter == GuideChapter.End) GuideTone.Orange else GuideTone.Ink
        val away = (if (forward) -dp(ENTER_OFFSET) else dp(ENTER_OFFSET)).toFloat()
        for (view in listOf(title, body)) view.animate().alpha(0f).translationX(away).setDuration(FADE_MILLIS).start()
        if (target != tone) backdrop.wipeTo(backgroundOf(target))
        overlay.sweep(thickness = if (target != tone) LINE else BEAT_LINE)
        main.postDelayed({
            changing = false
            applyTone(target)
            if (coming.chapter == GuideChapter.End) finale(coming) else enter(coming)
        }, WIPE_MILLIS)
    }

    private fun hideReaction() {
        reaction.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setDuration(FADE_MILLIS).start()
    }

    /** The high note: lines cross the screen one after the other, the mark lands, and the keys answer. */
    private fun finale(end: GuideStep) {
        next.visibility = View.GONE
        again.visibility = View.VISIBLE
        again.text = "Again"
        haptics.guideFinale()
        for (sweep in 0 until FINALE_SWEEPS) overlay.sweep(delay = sweep * FINALE_STAGGER_MILLIS, thickness = BEAT_LINE + sweep * 2f)
        overlay.showMark(GuideColours.INK)
        keys.celebrate()
        main.postDelayed({
            overlay.hideMark()
            keys.celebrate()
            seeding = true
            field.setText("")
            seeding = false
            enter(end)
        }, FINALE_MARK_MILLIS)
    }

    private fun startAgain() {
        main.removeCallbacksAndMessages(null)
        busy = true
        passed = false
        changing = true
        flow.start()
        preferences.edit().remove(RESUME).apply()
        again.visibility = View.GONE
        again.text = "Start again"
        hideReaction()
        for (view in listOf(title, body)) view.animate().alpha(0f).setDuration(FADE_MILLIS).start()
        backdrop.wipeTo(backgroundOf(GuideTone.Ink))
        overlay.sweep()
        main.postDelayed({
            changing = false
            applyTone(GuideTone.Ink)
            seeding = true
            field.setText("")
            seeding = false
            enter(flow.step)
        }, WIPE_MILLIS)
    }

    /** Leaves at any time. The guide remembers where you stopped, and opens there from the guide page. */
    private fun leave() {
        finish()
    }

    private fun keyName(code: Int): String? = when (code) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (code - KeyEvent.KEYCODE_A)).toString()
        KeyEvent.KEYCODE_DEL -> Layout.BACKSPACE
        KeyEvent.KEYCODE_ENTER -> Layout.ENTER
        KeyEvent.KEYCODE_SPACE -> Layout.SPACE
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> Layout.ALT
        KeyEvent.KEYCODE_SYM -> Layout.SYM
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> "⇧"
        // On this phone fn can also arrive as Ctrl.
        KeyEvent.KEYCODE_FUNCTION, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> Layout.FN
        else -> null
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    companion object {
        private const val PREFERENCES = "app"
        private const val SHOWN = "guide_shown"
        private const val RESUME = "guide_resume"

        // The look, in dp. These are safe to change. Words fit their box at any system font size, because the boxes set their size.
        private val HEAVY: Typeface = Typeface.create(Typeface.DEFAULT, 900, false)
        private val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private const val GUTTER = 18
        private const val TOP_ACTION = 32
        private const val TITLE_SMALLEST = 22
        private const val TITLE_LARGEST = 76
        private const val BODY_SMALLEST = 11
        private const val BODY_LARGEST = 20
        private const val REACTION_SMALLEST = 24
        private const val REACTION_LARGEST = 88
        private const val REACTION_HEIGHT = 190
        private const val TITLE_SHARE = 2.6f
        private const val BODY_SHARE = 1f
        private const val FIELD_RADIUS = 14
        private const val HINT_RADIUS = 10
        private const val OFFER_RADIUS = 22
        private const val BODY_ALPHA = .86f
        private const val HINT_FILL = .14f
        /** How strong Next is while the step waits, and how strong Back's outline is. */
        private const val NEXT_WAITING = .6f
        private const val BACK_OUTLINE = .35f
        private const val LINE = 5f
        private const val SEGMENT_HEIGHT = 4
        private const val SEGMENT_OFF = .25f
        /** Below this much height the screen is too short for the drawing of the keys. */
        private const val COMPACT_HEIGHT = 240
        /** The drawing's height against its width, and against the room there is. */
        private const val KEYS_ASPECT = .31f
        private const val KEYS_SHARE = .36f

        // The motion, in ms. Android's animator duration setting also scales the parts that draw themselves.
        private const val INTRO_MILLIS = 1250L
        private const val FADE_MILLIS = 140L
        private const val ENTER_MILLIS = 340L
        private const val ENTER_STAGGER_MILLIS = 60L
        private const val ENTER_OFFSET = 30
        private const val ENTER_OVERSHOOT = 1.4f
        private const val REACTION_IN_MILLIS = 300L
        private const val REACTION_OVERSHOOT = 2.2f
        private const val REACTION_DIM = .08f
        /** How long a finished step's line stays before the step's words come back. */
        private const val REACTION_MILLIS = 1400L
        /** When the new background has reached the middle of the screen. */
        private const val WIPE_MILLIS = 300L
        private const val BEAT_LINE = 3f
        private const val FINALE_SWEEPS = 6
        private const val FINALE_STAGGER_MILLIS = 110L
        private const val FINALE_MARK_MILLIS = 1500L
        private const val HELP_AFTER_MILLIS = 9000L
        private const val RESEED_MILLIS = 700L

        /** True until the guide has opened once, so it opens by itself after setup. */
        fun due(context: Context) = !context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(SHOWN, false)

        fun start(activity: Activity) {
            activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(SHOWN, true).apply()
            activity.startActivity(Intent(activity, GuideActivity::class.java))
        }
    }
}
