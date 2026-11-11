package com.atlas.keyboard

import android.app.Activity
import android.text.StaticLayout
import android.text.style.UnderlineSpan
import android.animation.ValueAnimator
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.atlas.connected.Connected
import com.atlas.connected.Contributions
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

    private var wordmark: Wordmark? = null

    /** The two halves of a name, the [left] one in from the left side and the [right] one in from the right. */
    private class Wordmark(val left: String, val right: String, val paint: Paint, val x: Float, val baseline: Float,
                           val start: Long, val locked: () -> Unit)

    /**
     * [left] flies in from the left side and [right] from the right side, and they lock together at [x] and [baseline], where
     * the title shows the whole name with this [paint]. Then [locked] runs, and the title takes over from the drawing.
     */
    fun flyIn(left: String, right: String, paint: Paint, x: Float, baseline: Float, locked: () -> Unit) {
        if (!GuideMotion.enabled) {
            locked()
            return
        }
        wordmark = Wordmark(left, right, Paint(paint), x, baseline, GuideMotion.now(), locked)
        invalidate()
    }

    /** True while the halves are still on their way. */
    private fun drawWordmark(canvas: Canvas, mark: Wordmark): Boolean {
        val t = GuideMotion.progress(mark.start, WORDMARK_MILLIS)
        if (t >= 1f) {
            wordmark = null
            // Views change when the halves lock, so that waits until this drawing is done.
            post { mark.locked() }
            return false
        }
        // Fast at first and slow at the end, so that the halves land softly, then click together.
        val left = 1f - GuideMotion.easeOut(t)
        val travel = width * WORDMARK_TRAVEL
        val colour = mark.paint.color
        mark.paint.color = GuideMotion.faded(colour, min(1f, t * WORDMARK_FADE_IN))
        canvas.drawText(mark.left, mark.x - travel * left, mark.baseline, mark.paint)
        canvas.drawText(mark.right, mark.x + mark.paint.measureText(mark.left) + travel * left, mark.baseline, mark.paint)
        mark.paint.color = colour
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val reach = hypot(width.toFloat(), height.toFloat())
        val cx = width / 2f
        val cy = height / 2f
        var moving = false
        if (markStart >= 0) moving = drawMark(canvas, reach, cx, cy)
        wordmark?.let { if (drawWordmark(canvas, it)) moving = true }
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
        /** How long the two halves of the name take to fly together. */
        const val WORDMARK_MILLIS = 900L
        /** How far the halves start out, against the width of the screen. */
        const val WORDMARK_TRAVEL = .9f
        /** The halves are fully there after this part of the flight, divided into one. */
        const val WORDMARK_FADE_IN = 3f
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
/** One chapter in the bar at the top: a rounded line that fills from the left as far as [paint] says. */
private class GuideSegment(context: Context) : View(context) {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    private var on = 0
    private var off = 0
    private var fill = 0f

    fun paint(on: Int, off: Int, fill: Float) {
        this.on = on
        this.off = off
        this.fill = fill.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = height / 2f
        pen.color = off
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, pen)
        if (fill <= 0f) return
        pen.color = on
        canvas.drawRoundRect(0f, 0f, maxOf(height.toFloat(), width * fill), height.toFloat(), radius, radius, pen)
    }
}

private class GuideField(context: Context) : EditText(context) {
    /** Android calls [onSelectionChanged] while it builds the view, so this has to work before it is set. */
    var changed: (() -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        changed?.invoke()
    }

    /**
     * Android's text view crashes on a touch while it has no layout for its text, which it can lack for a moment while the guide
     * changes the text or the screen under the finger: it places the cursor at -1. A touch then does nothing, and the next one works.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (layout == null) return true
        return try {
            super.onTouchEvent(event)
        } catch (error: IndexOutOfBoundsException) {
            true
        }
    }

    /** The same for a long press, which needs a selection handle that a text view without a layout has not made. */
    override fun performLongClick(): Boolean {
        if (layout == null) return true
        return try {
            super.performLongClick()
        } catch (error: NullPointerException) {
            true
        }
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
    /**
     * The app's front page: the same screen with no steps, a free place to type and swipe. The line above the field still
     * suggests words, and the drawing of the keys still follows your finger. See [playground].
     */
    private val playground by lazy { intent.getBooleanExtra(PLAYGROUND, false) }
    private val preferences by lazy { getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }
    private val flow by lazy { GuideFlow(::readSettings) }
    private val haptics by lazy { Haptics(this) }
    private val input by lazy { getSystemService(InputMethodManager::class.java) }
    private val clipboard by lazy { getSystemService(ClipboardManager::class.java) }
    /** A copy, from the keyboard's fn and C or from anything else, as the copy slides wait for. */
    private val clipChanged = ClipboardManager.OnPrimaryClipChangedListener {
        val text = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull().orEmpty()
        handle(KeyboardEvent.Copied(text))
    }

    private lateinit var root: FrameLayout
    private lateinit var backdrop: GuideBackdrop
    private lateinit var overlay: GuideOverlay
    /** The playground's show around its speed, behind the words. See [WpmEffectsView]. */
    private lateinit var effects: WpmEffectsView
    private lateinit var column: LinearLayout
    private lateinit var segments: LinearLayout
    private lateinit var next: TextView
    private lateinit var previous: TextView
    private lateinit var again: TextView
    /** The playground's ways out, to the written guide and to Settings. */
    private lateinit var guideAction: TextView
    private lateinit var settingsAction: TextView
    private lateinit var close: ImageView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var hint: TextView
    private lateinit var offer: TextView
    private lateinit var field: GuideField
    private lateinit var keys: GuideKeyboardView
    private lateinit var chapter: TextView
    /** The line above the field that suggests what to type. */
    private lateinit var script: TextView
    /** Over the field: what a demonstration would do, in grey, without typing it. */
    private lateinit var phantom: TextView

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
    /** The step is done, so the screen is orange and Next is filled. */
    private var passed = false
    /** Between two steps, Next does nothing. */
    private var changing = false
    /** After the line that lands, the step's words come back, so you can read them while you try more. */
    private val settleReaction = Runnable {
        if (!passed) return@Runnable
        swapIn(title, flow.step.title, 0)
        body.animate().alpha(1f).setDuration(FADE_MILLIS).start()
    }

    // Slide zero: setup. While a part is missing, it comes before the story. The guide opens the page for a part, watches for its
    // switch, closes the page and comes back by itself, then opens the next part after a short beat. See [SetupPart].

    /** The part of setup on the screen, or null when the guide is not setting up. */
    private var setupPart: SetupPart? = null
    /** The part whose Settings page is open now, which the guide watches. */
    private var openedPart: SetupPart? = null
    /** A part that opens by itself after a short beat, because the part before it was just done. */
    private var autoPart: SetupPart? = null
    /** Between a part that is done and the next one, nothing else moves setup on. */
    private var setupChanging = false
    private var visible = false
    private val settingUp get() = setupPart != null

    private val watchSetup = object : Runnable {
        override fun run() {
            val part = openedPart ?: return
            if (!PhoneSetup.done(this@GuideActivity, part)) {
                main.postDelayed(this, SETUP_POLL_MILLIS)
                return
            }
            openedPart = null
            // The page was opened from here, so closing it brings the guide back. A page in a task of its own needs the guide sent for.
            finishActivity(SETUP_REQUEST)
            main.postDelayed({ if (!visible) bringBack() }, SETUP_RETURN_MILLIS)
            checkKeyboard()
        }
    }

    private val openAuto = Runnable {
        val part = autoPart ?: return@Runnable
        autoPart = null
        if (visible && setupPart == part && openedPart == null) openPart(part)
    }

    private var helpShown = false
    private val showHelp = Runnable {
        helpShown = true
        flow.step.help?.let(::showHint)
        offerFor(flow.step)
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
        if (playground) {
            playgrounds += this
            playgroundStarting = -PLAYGROUND_QUIET_MILLIS
        } else guidesOpen++
        // During setup another keyboard may still be the phone's keyboard, and it would cover the slide.
        if (PhoneSetup.missing(this).isEmpty()) field.requestFocus() else keyboardShown(false)
        field.changed = ::fieldChanged
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = fieldChanged()
        })
        flow.start(preferences.getString(RESUME, null))
        applyTone(GuideTone.Ink)
        backdrop.show(backgroundOf(tone))
        for (view in listOf(title, body, hint, offer, next, previous, chapter, again, close, segments, script, field, keys)) view.alpha = 0f
    }

    override fun onResume() {
        super.onResume()
        visible = true
        // Back from a Settings page with its part still not done: say what to do there, and wait for the button.
        openedPart?.let { part ->
            openedPart = null
            main.removeCallbacks(watchSetup)
            if (!PhoneSetup.done(this, part)) part.help?.let(::showHint)
        }
        if (autoPart != null) {
            main.removeCallbacks(openAuto)
            main.postDelayed(openAuto, SETUP_AUTO_MILLIS)
        }
        if (stopEvents == null) stopEvents = KeyboardEvents.listen { event -> runOnUiThread { handle(event) } }
        if (stopTrace == null) stopTrace = Trace.listen { message -> trace(message) }
        // A word that comes out different gets the script's word on the strip, so the line above is always one touch away.
        // The playground is free typing, so there the strip keeps to your own words.
        if (!playground) GuideHooks.script = { before -> GuideScript.letters(passage.wordAt(passage.track(typedSince(before)).next)) }
        contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, keyboardChanges)
        clipboard.addPrimaryClipChangedListener(clipChanged)
        // Back from the disclosure: the end screen shows the answer, or keeps asking.
        if (ready && flow.finished && !playground) showEndChoices()
    }

    override fun onPause() {
        super.onPause()
        visible = false
        stopEvents?.invoke()
        stopEvents = null
        stopTrace?.invoke()
        stopTrace = null
        GuideHooks.script = null
        contentResolver.unregisterContentObserver(keyboardChanges)
        clipboard.removePrimaryClipChangedListener(clipChanged)
        endDemo()
        // A hidden playground keeps no clock going; its speed starts over when you type again.
        main.removeCallbacks(wpmTick)
        if (playground) calmWpm()
    }

    override fun onDestroy() {
        if (playground) playgrounds -= this
        if (playground) Log.d(TAG, "playground closed: finishing $isFinishing, told ${playgroundExit != null}")
        // Closed by something else, such as a link that opens the app's own screen over it: the front page opens it again.
        if (playground && isFinishing) {
            playgroundExit?.invoke(PLAYGROUND_CLOSED)
            playgroundExit = null
        }
        // The front page asked for the playground while this guide was open, so it opens now that the guide has closed.
        if (!playground) {
            guidesOpen--
            if (guidesOpen == 0 && isFinishing) waitingPlayground?.let { open ->
                waitingPlayground = null
                // Not [main], whose messages this guide drops as it closes.
                Handler(Looper.getMainLooper()).post { runCatching(open) }
            }
        }
        GuideHooks.nearMiss = false
        GuideHooks.stripDemo(null)
        GuideHooks.stripPoint(null)
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        // The keyboard list can close without a change, so this checks each time the guide comes back.
        checkKeyboard()
        // During setup there is nothing to type yet, and another keyboard would cover the slide.
        if (settingUp) return
        field.requestFocus()
        input.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
    }

    @Deprecated("Back reaches an activity here, because the app does not opt in to predictive back")
    override fun onBackPressed() {
        // Back goes to the step before, like the Back button. From the first step or the ending it leaves the guide.
        if (changing) return
        // The playground is the front page, so Back leaves the app, as it does from any app's front page.
        if (playground && !settingUp) {
            playgroundExit = null
            finishAffinity()
        } else if (settingUp) leave()
        else if (ready && flow.canGoBack && flow.step.chapter != GuideChapter.End) moveOn(forward = false) else leave()
    }

    // The screen.

    private fun build(): View {
        root = FrameLayout(this)
        backdrop = GuideBackdrop(this)
        overlay = GuideOverlay(this)
        effects = WpmEffectsView(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        keys = GuideKeyboardView(this)
        title = TextView(this).apply {
            typeface = HEAVY
            gravity = Gravity.BOTTOM or Gravity.START
            includeFontPadding = false
            letterSpacing = -.02f
            maxLines = 2
            setAutoSizeTextTypeUniformWithConfiguration(TITLE_SMALLEST, TITLE_LARGEST, 2, TypedValue.COMPLEX_UNIT_DIP)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body = TextView(this).apply {
            typeface = MEDIUM
            gravity = Gravity.TOP or Gravity.START
            includeFontPadding = false
            maxLines = 5
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
                cornerRadius = Radii.px(resources, FIELD_RADIUS)
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(18f), dp(24).toFloat()))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            minLines = 1
            maxLines = 2
            hint = FIELD_HINT
            // Lets the keyboard know this field, for the one step that asks it for a near miss. See [GuideHooks].
            privateImeOptions = GuideHooks.FIELD
            isVerticalScrollBarEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        // The same text settings as the field, so that the grey words line up with the real ones.
        phantom = TextView(this).apply {
            typeface = field.typeface
            setTextSize(TypedValue.COMPLEX_UNIT_PX, field.textSize)
            setPadding(field.paddingLeft, field.paddingTop, field.paddingRight, field.paddingBottom)
            gravity = field.gravity
            includeFontPadding = field.includeFontPadding
            setLineSpacing(field.lineSpacingExtra, field.lineSpacingMultiplier)
            letterSpacing = field.letterSpacing
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        script = TextView(this).apply {
            typeface = MEDIUM
            includeFontPadding = false
            // Exactly two lines, always: the passage moves a whole line at a time. See [showScript]. It has its own box, so it
            // reads as the text to type and not as more instructions.
            setLines(2)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(GuideMotion.faded(GuideColours.CREAM, PASSAGE_BOX))
                cornerRadius = dp(10).toFloat()
            }
            isVerticalScrollBarEnabled = false
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(16f), dp(22).toFloat()))
            contentDescription = "Text to type"
        }
        chapter = TextView(this).apply {
            typeface = HEAVY
            includeFontPadding = false
            isAllCaps = true
            letterSpacing = .12f
            maxLines = 1
            setTextSize(TypedValue.COMPLEX_UNIT_PX, min(sp(11f), dp(15).toFloat()))
        }
        segments = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        for (chapter in flow.barChapters) segments.addView(GuideSegment(this),
            LinearLayout.LayoutParams(0, dp(SEGMENT_HEIGHT), 1f).apply { marginEnd = dp(3) })
        again = topAction("Start again") { startAgain() }.apply { visibility = View.GONE }
        guideAction = topAction("Guide") {
            if (playground) leavePlayground(PLAYGROUND_GUIDE) else {
                startActivity(Connected.pageIntent(this, BORING_GUIDE))
                finish()
            }
        }.apply { visibility = View.GONE }
        settingsAction = topAction("Settings") { leavePlayground(PLAYGROUND_SETTINGS) }.apply { visibility = View.GONE }
        close = ImageView(this).apply {
            setImageResource(R.drawable.panel_close)
            contentDescription = "Close"
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { leave() }
        }
        val topBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(TOP_ACTION)
            addView(segments, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(again, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(guideAction, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(settingsAction, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(close, LinearLayout.LayoutParams(dp(TOP_ACTION), dp(TOP_ACTION)))
        }
        // Back always on the left and Next always on the right, so a thumb finds them in the same place on every step.
        // The offer to turn on a setting, if a step needs one, goes between them.
        val actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(previous, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(offer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(next, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(chapter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, TITLE_SHARE).apply { topMargin = dp(6) })
            addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, BODY_SHARE).apply { topMargin = dp(4) })
            addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            addView(script, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(FrameLayout(context).apply {
                addView(field, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(phantom, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            })
            addView(keys, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { topMargin = dp(8) })
        }
        root.addView(backdrop, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(effects, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            // The content nearest the screen's rounded corners, such as Back and Next at the bottom and the chapter at the top,
            // keeps clear of them. The camera hole is in the status bar, which bars.top already leaves out.
            val top = bars.top + dp(4)
            val bottom = bars.bottom + dp(8)
            val corners = Corners.of(insets, display)
            val corner = maxOf(corners.inset(bottom.toFloat()), corners.topInset(top.toFloat()))
            column.setPadding(maxOf(dp(GUTTER) + bars.left, corner), top, maxOf(dp(GUTTER) + bars.right, corner), bottom)
            fitKeys()
            insets
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitKeys() }
        effects.shaking = { tip, dx, dy ->
            title.rotation = tip
            column.translationX = dx
            column.translationY = dy
        }
        return root
    }

    private fun topAction(text: String, press: () -> Unit) = TextView(this).apply {
        this.text = text
        typeface = HEAVY
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        setPadding(dp(10), dp(6), dp(10), dp(6))
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
        guideAction.setTextColor(strong)
        settingsAction.setTextColor(strong)
        close.imageTintList = ColorStateList.valueOf(strong)
        chapter.setTextColor(GuideMotion.faded(strong, CHAPTER_ALPHA))
        keys.tone = next
        paintNext()
        paintSegments(flow.stepIndex)
        val appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (onInk) 0 else appearance, appearance)
        if (::field.isInitialized && ::script.isInitialized) showScript()
    }

    /** Next is an outline while the step waits, and filled once the step is done. Back is always a quiet outline. */
    private fun paintNext() {
        val strong = if (tone == GuideTone.Ink) GuideColours.CREAM else GuideColours.INK
        previous.setTextColor(GuideMotion.faded(strong, NEXT_WAITING))
        previous.background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1).coerceAtLeast(2), GuideMotion.faded(strong, BACK_OUTLINE))
            cornerRadius = Radii.px(resources, OFFER_RADIUS)
        }
        if (passed) {
            next.setTextColor(if (tone == GuideTone.Ink) GuideColours.INK else GuideColours.CREAM)
            next.background = rounded(strong, OFFER_RADIUS)
        } else {
            next.setTextColor(GuideMotion.faded(strong, NEXT_WAITING))
            next.background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), GuideMotion.faded(strong, NEXT_WAITING))
                cornerRadius = Radii.px(resources, OFFER_RADIUS)
            }
        }
    }

    /** A fill with one of the shared [Radii]. */
    private fun rounded(colour: Int, radius: Int) = GradientDrawable().apply {
        setColor(colour)
        cornerRadius = Radii.px(resources, radius)
    }

    /**
     * The bar at the top shows the chapters: those behind you are full, the one you're in fills up slide by slide, and the
     * rest wait. [done] is not used: the flow knows where it is.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun paintSegments(done: Int) {
        val strong = if (tone == GuideTone.Ink) GuideColours.ORANGE else GuideColours.INK
        val current = flow.barChapters.indexOf(flow.step.chapter).let { if (it < 0) flow.barChapters.size else it }
        val within = (flow.stepInChapter + 1f) / flow.stepsInChapter.coerceAtLeast(1)
        for (index in 0 until segments.childCount) (segments.getChildAt(index) as GuideSegment).paint(
            on = strong,
            off = GuideMotion.faded(strong, SEGMENT_OFF),
            fill = when {
                index < current -> 1f
                index == current -> within
                else -> 0f
            },
        )
    }

    // The story.

    private fun begin() {
        if (started) return
        started = true
        if (playground) {
            showPlayground()
            return
        }
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
        if (playground) {
            showPlayground()
            return
        }
        keys.appear()
        for (view in listOf(field, keys, next, previous, chapter, again, close, segments, script)) view.animate().alpha(1f).setDuration(FADE_MILLIS).start()
        enter(step)
    }

    private fun enter(step: GuideStep) {
        lastPoint = null
        travel = 0f
        helpShown = false
        passed = false
        // At the end Next keeps its room, so that the offer to start typing stays in the middle.
        next.visibility = if (step.chapter == GuideChapter.End) View.INVISIBLE else View.VISIBLE
        // Back keeps its room on the first step, so that Next does not move.
        previous.visibility = if (flow.canGoBack) View.VISIBLE else View.INVISIBLE
        chapter.text = step.chapter.label
        GuideHooks.nearMiss = step.nearMiss
        // A slide with nothing to try is done as it opens.
        passed = step.info
        next.text = "Next"
        paintNext()
        offerFor(step)
        // The whole guide is one text. A step that works on text gets more of the script if the text is too short, and a step
        // with words to practise on gets them, once.
        val words = field.text.split(' ', '\n').count { it.isNotEmpty() }
        if (step.needsWords > words) addText(Passage(step.text).words.take(step.needsWords - words).joinToString(" ") + ".")
        step.passage?.let { if (!field.text.contains(it)) addText(it) }
        setUpLesson(step)
        flow.seed(field.text.toString())
        startPassage(step.text)
        swapIn(title, step.title, 0)
        // Only the instruction that is needed now. The slide moves on, or back, as the keyboard changes. See [showStage].
        swapIn(body, flow.instruction, ENTER_STAGGER_MILLIS)
        hideHint()
        // Everything that the slides leave out is in the written guide, and the ending says where.

        if (step.offersShare) Contributions.guideEnded(this)
        // The keyboard starts the step in the menu that it needs: the Sym panel for Alt and the emoji, closed for typing.
        step.setup?.let(GuideHooks::openPanel)
        showScript()
        paintSegments(flow.stepIndex)
        main.removeCallbacks(showHelp)
        if (step.help != null) main.postDelayed(showHelp, HELP_AFTER_MILLIS)
        if (step.chapter == GuideChapter.End) {
            preferences.edit().remove(RESUME).apply()
            // The end of the guide offers to share swipes until the user answers: here, and in the app if they leave.
            Contributions.guideEnded(this)
        } else preferences.edit().putString(RESUME, flow.resumeId).apply()
        busy = false
        // A new slide shows its gesture at once, as soon as its words are in.
        acted(DEMO_ENTER_MILLIS)
        // The stop between the parts is a win already: it shows as done, with the offer to start typing.
        if (step.pause) {
            busy = true
            passed = true
            backdrop.wipeTo(GuideColours.ORANGE)
            overlay.sweep()
            main.postDelayed({ if (passed) applyTone(GuideTone.Orange) }, WIPE_MILLIS)
        }
    }

    /**
     * The line above the field: the script from the next word on, with that word in the accent. The ghost finger on the drawing
     * of the keys follows the same word on the steps that swipe.
     */
    private fun showScript() {
        showPassage()
        val shown = ghostCue(if (playground) GuideCue.Free else flow.cue)
        if (shown != keys.cue) keys.show(shown)
    }

    // The passage above the field, as a typing race shows it: always exactly two lines of the script, cut where its lines end, with
    // the word you're on underlined wherever it is on the line, the words behind you dimmed, and anything typed wrong boxed in red
    // until it's fixed. When you finish a line, the lines slide up by one.

    /** The script laid out at the passage's width, and that width. */
    private var passageLayout: StaticLayout? = null
    private var passageWidth = -1
    /** The line that the passage shows on top, and its slide up, if one runs. */
    private var passageLine = -1
    private var passageSlide: ValueAnimator? = null

    /** The text to type along to now, and where in the field it began: what's typed since then is matched against it. */
    private var passage = Passage(GuideScript.TEXT)
    private var passageFrom = 0
    private var passageText = ""

    /** Starts [text] as the text to type along to, from the end of the field. */
    private fun startPassage(text: String) {
        if (text.isEmpty()) return
        passage = Passage(text)
        passageFrom = field.length()
        passageLayout = null
        passageLine = -1
        passageText = text
    }

    /** What's been typed since the passage began, out of [text]: if text before it was deleted, from wherever it ends now. */
    private fun typedSince(text: String): String {
        if (text.length < passageFrom) passageFrom = text.length
        return text.substring(passageFrom)
    }

    private fun showPassage() {
        val width = script.width - script.totalPaddingLeft - script.totalPaddingRight
        if (width <= 0) {
            // Before the first layout there's no width to cut lines at, so it waits for one.
            script.post { if (script.width > 0) showPassage() }
            return
        }
        val text = passage.text
        val layout = passageLayout?.takeIf { passageWidth == width }
            ?: StaticLayout.Builder.obtain(text, 0, text.length, script.paint, width).setIncludePad(false).build()
                .also { passageLayout = it; passageWidth = width; passageLine = -1 }
        val track = passage.track(typedSince(field.text.toString()))
        val count = passage.words.size
        val next = track.next.mod(count)
        val cycle = track.next - next
        val line = layout.getLineForOffset(passage.ranges[next].first)
        val styled = passageSpans(track, next, cycle)
        // The line after the last is the first again, as the script starts over.
        fun lineText(at: Int): CharSequence {
            val wrapped = at.mod(layout.lineCount)
            val start = layout.getLineStart(wrapped)
            var end = layout.getLineEnd(wrapped)
            while (end > start && text[end - 1].isWhitespace()) end--
            return if (at >= layout.lineCount) plain(text.substring(start, end)) else styled.subSequence(start, end)
        }
        fun lines(from: Int, many: Int) = SpannableStringBuilder().apply {
            for (at in from until from + many) {
                if (at > from) append("\n")
                append(lineText(at))
            }
        }
        val forward = passageLine >= 0 && line == passageLine + 1
        passageLine = line
        if (passageSlide?.isRunning == true) passageSlide?.cancel()
        if (!forward || !GuideMotion.enabled) {
            script.scrollTo(0, 0)
            script.text = lines(line, 2)
            return
        }
        // Only the lines move: the line you finished goes up and out, and the next comes up into its place.
        script.text = lines(line - 1, 3)
        val lineHeight = script.lineHeight
        passageSlide = ValueAnimator.ofInt(0, lineHeight).apply {
            duration = PASSAGE_SLIDE_MILLIS
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { script.scrollTo(0, it.animatedValue as Int) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    script.scrollTo(0, 0)
                    script.text = lines(line, 2)
                }
            })
            start()
        }
    }

    /** The whole passage, coloured for where typing is: right words grey, skipped ones boxed, the word you're on underlined. */
    private fun passageSpans(track: Passage.Track, next: Int, cycle: Int): SpannableStringBuilder {
        val strong = if (tone == GuideTone.Ink) GuideColours.CREAM else GuideColours.INK
        val accent = if (tone == GuideTone.Ink) GuideColours.ORANGE else GuideColours.INK
        val wrong = GuideMotion.faded(PASSAGE_WRONG, PASSAGE_WRONG_ALPHA)
        val built = SpannableStringBuilder(passage.text)
        fun span(what: Any, range: IntRange) = built.setSpan(what, range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        built.setSpan(ForegroundColorSpan(strong), 0, built.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        for ((index, range) in passage.ranges.withIndex()) {
            when {
                index < next -> {
                    span(ForegroundColorSpan(GuideMotion.faded(strong, PASSAGE_DONE)), range)
                    // A word skipped over keeps a faint box, so you can see what you missed.
                    if (cycle + index !in track.done) span(BackgroundColorSpan(GuideMotion.faded(PASSAGE_WRONG, PASSAGE_SKIPPED_ALPHA)), range)
                }
                index == next -> {
                    span(ForegroundColorSpan(accent), range)
                    span(UnderlineSpan(), range)
                    // The letters typed so far: each one that doesn't match is boxed, and so is anything past the word's end.
                    val expected = passage.text.substring(range.first, range.last + 1)
                    var letter = 0
                    for ((offset, char) in expected.withIndex()) {
                        if (!char.isLetter()) continue
                        if (letter >= track.typed.length) break
                        if (!track.typed[letter].equals(char, ignoreCase = true)) span(BackgroundColorSpan(wrong), (range.first + offset)..(range.first + offset))
                        letter++
                    }
                    if (track.typed.length > GuideScript.letters(expected).length) span(BackgroundColorSpan(wrong), range)
                }
            }
        }
        return built
    }

    /** A line of the script as it is, before typing reaches it. */
    private fun plain(text: String) = SpannableStringBuilder(text).apply {
        val strong = if (tone == GuideTone.Ink) GuideColours.CREAM else GuideColours.INK
        setSpan(ForegroundColorSpan(strong), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** What the drawing of the keys can draw for [cue]: the next word of the script is turned into that word's letters. */
    private fun ghostCue(cue: GuideCue): GuideCue {
        if (cue != GuideCue.NextWord) return cue
        val word = passage.wordAt(passage.track(typedSince(field.text.toString())).next)
        return GuideCue.Word(GuideScript.letters(word).ifEmpty { "titan" })
    }

    // Demonstrations. A slide shows its gesture by itself, in grey over the field, again and again until you take over. It types
    // nothing. Anything that you do stops it at once, and it starts again after a quiet while.

    private var demoStart = -1L
    private var demoLength = 0L
    private var demoKeys = 0
    private var demoTurned = false
    private var demo: GuideDemo? = null
    /** The bar's own demonstration has started in this showing. */
    private var demoBarPlayed = false
    /** The pushes of the word that this demonstration types or swipes, worked out on its first frame. */
    private var demoPushes: List<DemoPush>? = null
    /** Which letter key types each mark with Alt, read from the phone's own keyboard. */
    private val altKeys by lazy {
        val found = HashMap<Char, String>()
        for (key in Layout.letterKeys) Keys.letter(key, KeyEvent.META_ALT_ON)?.singleOrNull()?.let { found.putIfAbsent(it, key) }
        found
    }
    private val startDemo = Runnable { beginDemo() }
    private val demoTick = Runnable { stepDemo() }

    /** Something the user did: a demonstration stops, and the next one waits for a quiet moment, or [wait] on a new slide. */
    private fun acted(wait: Long = DEMO_IDLE_MILLIS) {
        endDemo()
        main.removeCallbacks(startDemo)
        if (!passed && ready && !changing) main.postDelayed(startDemo, wait)
    }

    private fun beginDemo() {
        val step = flow.step
        if (busy || passed || changing || step.pause || step.chapter == GuideChapter.End) return
        val stageCue = flow.cue
        val hasGhost = stageCue != GuideCue.Free && stageCue !is GuideCue.Spot
        if (step.demo == null && step.bar == null && !hasGhost) return
        demo = step.demo
        val cue = ghostCue(stageCue)
        if (cue != keys.cue) keys.show(cue)
        demoStart = GuideMotion.now()
        demoLength = if (hasGhost) maxOf(keys.ghostMillis(), DEMO_MILLIS) else DEMO_MILLIS
        demoKeys = 0
        demoTurned = false
        demoPushes = null
        demoBarPlayed = false
        // The word menu is a gesture on the bar itself, so the bar acts it out.
        if (step.bar == GuideBar.Menu) {
            demoBarPlayed = true
            GuideHooks.stripDemo(GuideHooks.StripDemo.Menu(emptyList()))
        }
        keys.demo(true)
        // The grey words would sit on top of the empty field's hint.
        field.hint = null
        main.post(demoTick)
    }

    private fun stepDemo() {
        if (demoStart < 0) return
        val t = (GuideMotion.now() - demoStart).toFloat() / demoLength
        if (t >= 1f) {
            endDemo()
            main.postDelayed(startDemo, DEMO_GAP_MILLIS)
            return
        }
        demo?.let { showPhantom(it, t) }
        main.postDelayed(demoTick, DEMO_FRAME_MILLIS)
    }

    private fun endDemo() {
        main.removeCallbacks(demoTick)
        if (demoBarPlayed) {
            demoBarPlayed = false
            GuideHooks.stripDemo(null)
        }
        if (demoStart < 0 && phantom.text.isEmpty()) return
        demoStart = -1
        keys.demo(false)
        phantom.text = ""
        field.hint = FIELD_HINT
    }

    /**
     * Draws [demo] at [t], from 0 to 1, over the field. The field's own text is there too, but clear, so that what is added
     * lines up with it. Added text is grey; a word that goes is covered in the field's colour; a selection is washed in orange.
     */
    private fun showPhantom(demo: GuideDemo, t: Float) {
        val text = field.text.toString()
        val grey = GuideMotion.faded(GuideColours.INK, PHANTOM_ALPHA)
        val built = SpannableStringBuilder(text)
        // Android logs an error for a span with no length, and this runs for every frame.
        if (text.isNotEmpty()) built.setSpan(ForegroundColorSpan(Color.TRANSPARENT), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val gap = if (text.isEmpty() || text.endsWith(' ')) "" else " "
        val next = passage.wordAt(passage.track(typedSince(text)).next)
        fun add(added: String) {
            if (added.isEmpty()) return
            val start = built.length
            built.append(added)
            built.setSpan(ForegroundColorSpan(grey), start, built.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val lastStart = text.trimEnd().lastIndexOf(' ') + 1
        val lastEnd = text.trimEnd().length
        val typing = demo == GuideDemo.TypeNext || demo == GuideDemo.SwipeNext || demo == GuideDemo.FixNext || demo == GuideDemo.TypeSlip
        if (typing && GuideScript.isMarks(next)) {
            // Marks go right after their word: the keys light one push at a time, and then the marks show in place.
            val pushes = markPushes(next)
            val shown = ((t / TYPE_SHARE) * pushes.size).toInt().coerceIn(0, pushes.size)
            while (demoKeys < shown) pushes[demoKeys++].forEach(keys::light)
            if (t > TYPE_SHARE) {
                built.delete(text.trimEnd().length, built.length)
                add(next + " ")
            }
        } else when (demo) {
            GuideDemo.TypeNext -> {
                val pushes = pushesFor(next, swiped = false)
                val shown = ((t / TYPE_SHARE) * pushes.size).toInt().coerceIn(0, pushes.size)
                lightPushes(pushes, shown)
                if (shown > 0) add(gap + pushes[shown - 1].text)
            }
            // The word lands at the end of the ghost's path, and its marks follow one push at a time.
            GuideDemo.SwipeNext -> if (t > SWIPE_LANDS) {
                val pushes = pushesFor(next, swiped = true)
                val marks = ((t - SWIPE_LANDS) / (MARKS_DONE - SWIPE_LANDS) * pushes.size).toInt()
                val shown = (1 + marks).coerceIn(1, pushes.size)
                lightPushes(pushes, shown)
                add(gap + pushes[shown - 1].text)
            }
            GuideDemo.FixNext -> when {
                t > FIX_LANDS -> add(gap + next)
                t > SWIPE_LANDS / 2 -> add(gap + GuideScript.slip(next))
            }
            GuideDemo.DeleteLast -> if (lastEnd > lastStart) {
                val cover = ((t - .3f) / .5f).coerceIn(0f, 1f)
                built.setSpan(BackgroundColorSpan(GuideMotion.faded(GuideColours.CREAM, cover)), lastStart, lastEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            GuideDemo.MoveCursor -> if (lastEnd > 0) {
                val back = ((t - TAPS_SHARE).coerceAtLeast(0f) / (1f - TAPS_SHARE) * (lastEnd - lastStart + 1)).toInt()
                val at = (lastEnd - back).coerceIn(1, lastEnd)
                built.setSpan(BackgroundColorSpan(GuideMotion.faded(GuideColours.ORANGE, CARET_ALPHA)), at - 1, at, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            GuideDemo.SelectLast -> if (lastEnd > lastStart && t > TAPS_SHARE) {
                val wash = ((t - TAPS_SHARE) / .2f).coerceIn(0f, 1f) * SELECT_ALPHA
                built.setSpan(BackgroundColorSpan(GuideMotion.faded(GuideColours.ORANGE, wash)), lastStart, lastEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            is GuideDemo.HoldLetter -> {
                val symbol = Keys.letter(demo.key, KeyEvent.META_ALT_ON) ?: demo.key
                if (t < HOLD_TURNS) {
                    keys.light(demo.key)
                    add(gap + demo.key.lowercase())
                } else {
                    if (!demoTurned) {
                        demoTurned = true
                        keys.flip(demo.key, symbol)
                    }
                    add(gap + symbol)
                }
            }
            // A slip typed key by key; then Space lights, the bar shows the fix swapping in, and the right word takes its place.
            GuideDemo.TypeSlip -> {
                val slip = GuideScript.slip(next)
                val pushes = pushesFor(slip, swiped = false)
                val shown = ((t / SLIP_TYPED) * pushes.size).toInt().coerceIn(0, pushes.size)
                lightPushes(pushes, shown)
                if (t >= SLIP_TYPED && !demoBarPlayed) {
                    demoBarPlayed = true
                    GuideHooks.stripDemo(GuideHooks.StripDemo.Fix(slip, next))
                }
                if (t < SLIP_FIXED) {
                    if (shown > 0) add(gap + pushes[shown - 1].text)
                } else {
                    if (!demoTurned) {
                        demoTurned = true
                        keys.light(Layout.SPACE)
                    }
                    add(gap + next + " ")
                }
            }
            is GuideDemo.AltLetter -> {
                val symbol = Keys.letter(demo.key, KeyEvent.META_ALT_ON) ?: demo.key
                val pushed = listOf(Layout.ALT, demo.key)
                val due = (t / ALT_KEYS_SHARE * pushed.size).toInt().coerceAtMost(pushed.size)
                while (demoKeys < due) keys.light(pushed[demoKeys++])
                if (t > ALT_KEYS_SHARE) add(gap + symbol)
            }
            GuideDemo.CopyLast -> if (lastEnd > lastStart && t > TAPS_SHARE) {
                val wash = ((t - TAPS_SHARE) / .2f).coerceIn(0f, 1f) * SELECT_ALPHA
                built.setSpan(BackgroundColorSpan(GuideMotion.faded(GuideColours.ORANGE, wash)), lastStart, lastEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (t > COPY_KEYS && demoKeys == 0) {
                    demoKeys = 1
                    keys.light(Layout.FN)
                    keys.light("C")
                }
            }
            is GuideDemo.Insert -> {
                val pushed = (t / INSERT_SHARE * demo.keys.size).toInt().coerceAtMost(demo.keys.size)
                if (pushed > demoKeys) {
                    keys.light(demo.keys[pushed - 1])
                    demoKeys = pushed
                }
                if (t > INSERT_SHARE) add(gap + demo.text)
            }
        }
        phantom.text = built
        phantom.scrollTo(0, field.scrollY)
    }

    /**
     * What a lesson needs before it starts: something on the clipboard to paste, a word already selected so that the paste goes in
     * its place, and clips pinned in the keyboard's clipboard so that it has something to show.
     */
    private fun setUpLesson(step: GuideStep) {
        step.clip?.let { text ->
            runCatching { clipboard.setPrimaryClip(android.content.ClipData.newPlainText("TitanKeys guide", text)) }
        }
        if (step.preselect && field.selectionStart == field.selectionEnd) {
            val text = field.text.toString()
            val end = text.trimEnd().length
            val start = text.substring(0, end).lastIndexOf(' ') + 1
            if (end > start) field.setSelection(start, end)
        }
        if (step.pinnedClips.isNotEmpty()) GuideHooks.seedClips(step.pinnedClips)
    }

    private fun markPushes(marks: String) = GuideScript.markKeys(marks, TypingSettings.autoPunctuation(this)) { altKeys[it] }

    private fun pushesFor(word: String, swiped: Boolean) = demoPushes
        ?: GuideScript.pushes(word, swiped, TypingSettings.autoPunctuation(this)) { altKeys[it] }.also { demoPushes = it }

    /** Lights the keys of every push up to [shown] that has not lit yet. */
    private fun lightPushes(pushes: List<DemoPush>, shown: Int) {
        while (demoKeys < shown) pushes[demoKeys++].keys.forEach(keys::light)
    }

    /** Adds words to the end of the text, as if typed, without a step taking them as done. */
    private fun addText(words: String) {
        seeding = true
        val text = field.text.toString()
        val joined = if (text.isEmpty() || text.endsWith(' ')) words else " $words"
        field.text.append(joined)
        field.setSelection(field.text.length)
        seeding = false
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
            step.offersShare && Contributions.unanswered(this) -> showOffer(SHARE_OFFER)
            step.chapter == GuideChapter.End -> offer.visibility = View.GONE
            step.pause -> showOffer("Go type something")
            !ready -> showOffer("Pick TitanKeys")
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
        setupPart?.let {
            if (!setupChanging) openPart(it)
            return
        }
        val step = flow.step
        when {
            // The app shows the full disclosure, with "I agree" and "No thanks". Nothing is shared before that.
            step.offersShare && Contributions.unanswered(this) -> startActivity(Connected.pageIntent(this, Contributions.PAGE))
            step.pause -> leave()
            !ready -> input.showInputMethodPicker()
            else -> flow.missing?.let { need ->
                writeNeed(need)
                // The keyboard reads its settings when the field starts.
                input.restartInput(field)
                offerFor(step)
                if (need == GuideNeed.FnAsCtrl) showHint("Now hold fn.")
                haptics.confirm()
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
        shareUnanswered = Contributions.unanswered(this),
    )

    /**
     * Decides between setup and the story. Setup shows before the story while any part is missing, and again during the story if
     * the keyboard stops being TitanKeys, as the story needs it. A part that is done moves setup on.
     */
    private fun checkKeyboard() {
        if (setupChanging) return
        val missing = PhoneSetup.missing(this)
        val shown = setupPart
        if (shown != null && shown !in missing) {
            // The part on the screen is done, by the guide's page or by hand.
            partDone(missing.firstOrNull())
            return
        }
        if (missing.isNotEmpty() && (!started || shown != null || SetupPart.Enable in missing || SetupPart.Choose in missing)) {
            if (missing.first() != shown) setUp(missing.first(), auto = false)
            return
        }
        if (keyboardChecked && ready) return
        keyboardChecked = true
        ready = true
        if (!started) begin() else reveal(flow.step)
    }

    /** Shows a part of setup, with the button that opens its page. [auto] opens the page by itself after a short beat. */
    private fun setUp(part: SetupPart, auto: Boolean) {
        if (setupPart == null) {
            ready = false
            keyboardChecked = false
            busy = true
            main.removeCallbacks(startDemo)
            endDemo()
            main.removeCallbacks(showHelp)
            keyboardShown(false)
            for (view in listOf(field, keys, script, next, previous, again, segments)) {
                view.animate().cancel()
                view.alpha = 0f
                view.visibility = View.INVISIBLE
            }
            for (view in listOf(chapter, close)) view.animate().alpha(1f).setStartDelay(0).setDuration(FADE_MILLIS).start()
        }
        setupPart = part
        chapter.text = SetupPart.LABEL
        hideHint()
        swapIn(title, part.title, 0)
        swapIn(body, part.body, ENTER_STAGGER_MILLIS)
        showOffer(part.action)
        main.removeCallbacks(openAuto)
        autoPart = part.takeIf { auto }
        if (auto && visible) main.postDelayed(openAuto, SETUP_AUTO_MILLIS)
    }

    private fun openPart(part: SetupPart) {
        main.removeCallbacks(openAuto)
        autoPart = null
        hideHint()
        val page = PhoneSetup.page(this, part)
        if (page == null) {
            PhoneSetup.chooseKeyboard(this)
            return
        }
        try {
            startActivityForResult(page, SETUP_REQUEST)
        } catch (error: RuntimeException) {
            showHint(SETUP_NO_PAGE)
            return
        }
        openedPart = part
        awaitingAccessibility = part == SetupPart.Back
        main.removeCallbacks(watchSetup)
        main.postDelayed(watchSetup, SETUP_POLL_MILLIS)
    }

    /** A part of setup is done: a pulse and a line, then the [next] part, which opens by itself. After the last, the story starts. */
    private fun partDone(next: SetupPart?) {
        setupChanging = true
        main.removeCallbacks(openAuto)
        autoPart = null
        hideHint()
        offer.visibility = View.GONE
        haptics.guideChapter()
        overlay.sweep()
        showReaction(if (next == null) SetupPart.ALL_DONE else SetupPart.DONE)
        main.postDelayed({
            setupChanging = false
            if (next != null) setUp(next, auto = true) else setupFinished()
        }, if (next == null) SETUP_FINISH_MILLIS else SETUP_BEAT_MILLIS)
    }

    private fun setupFinished() {
        setupPart = null
        title.animate().cancel()
        title.scaleX = 1f
        title.scaleY = 1f
        for (view in listOf(field, keys, script, segments)) view.visibility = View.VISIBLE
        again.visibility = View.GONE
        keyboardChecked = true
        ready = true
        keyboardShown(true)
        if (!started) begin() else reveal(flow.step)
    }

    /** The keyboard stays up for the story and the playground, and stays down during setup. */
    private fun keyboardShown(shown: Boolean) {
        val state = if (shown) WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE else WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        window.setSoftInputMode(state or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (shown) {
            field.requestFocus()
            input.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        } else {
            field.clearFocus()
            input.hideSoftInputFromWindow(field.windowToken, 0)
        }
    }

    /** The playground: the name, a line to follow if you like, the field and the keys. Nothing is checked, and nothing waits. */
    private fun showPlayground() {
        busy = true
        passed = false
        keys.appear()
        // The bar keeps its room, so that Guide and Settings stay at the right.
        segments.visibility = View.INVISIBLE
        for (view in listOf(next, previous, again, close, offer)) view.visibility = View.GONE
        hideHint()
        guideAction.visibility = View.VISIBLE
        settingsAction.visibility = View.VISIBLE
        chapter.text = PLAYGROUND_LABEL
        // The playground's own text, made for speed, in a new order each time.
        startPassage(PlaygroundText.make(kotlin.random.Random(SystemClock.uptimeMillis())))
        swapIn(title, PLAYGROUND_TITLE, 0)
        swapIn(body, PLAYGROUND_BODY, ENTER_STAGGER_MILLIS)
        for (view in listOf(field, keys, chapter, script, guideAction, settingsAction))
            view.animate().alpha(1f).setStartDelay(0).setDuration(FADE_MILLIS).start()
        showScript()
        field.requestFocus()
    }

    /** The playground's Guide and Settings buttons: the app opens that page, and the playground opens again when you come back. */
    private fun leavePlayground(action: String) {
        playgroundExit?.invoke(action)
        playgroundExit = null
        finish()
    }

    /** Sends for the guide when a Settings page opened in a task of its own, which closing it from here cannot reach. */
    private fun bringBack() {
        try {
            startActivity(Intent(this, GuideActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (error: RuntimeException) {
            // Android can refuse this from the background. Then the guide waits until you come back to it.
        }
    }

    private fun fieldChanged() {
        if (!::field.isInitialized || !::script.isInitialized) return
        showScript()
        if (seeding) return
        val start = min(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        val end = max(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        handle(KeyboardEvent.FieldChanged(field.text.toString(), start, end))
    }

    private fun trace(message: JSONObject) {
        val path = message.getJSONArray("points")
        val points = (0 until path.length()).map { index ->
            val point = path.getJSONArray(index)
            floatArrayOf(point.getDouble(0).toFloat(), point.getDouble(1).toFloat(), point.optDouble(2, 1.0).toFloat())
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
        acted()
        if (!busy && ready) judge(KeyboardEvent.Moved(travel, lifted))
    }

    // The playground's speed: while you type, the name turns into words a minute, the screen warms towards orange, and from 40
    // words a minute the playground comments on it and puts on a show: see [WpmComments] and [WpmEffects]. Five seconds with
    // nothing, and the number counts back down to nothing, and the show winds down with it.

    private val wpm = WpmMeter()
    /** The speed that shows: it counts up and down towards the real one, so that nothing ever jumps or cuts off. */
    private var shownWpm = 0f
    private var showingWpm = false
    /** The comment's step that shows, from [WpmComments.STEPS], or -1 for none. */
    private var commentStep = -1
    /** When that comment came, for [WpmMeter.COMMENT_MIN_MILLIS]. */
    private var commentAt = 0L
    private val comments by lazy { resources.getStringArray(R.array.playground_comments) }
    private val wpmTick = object : Runnable {
        override fun run() = showWpm()
    }

    private fun countWpm(event: KeyboardEvent) {
        val now = SystemClock.uptimeMillis()
        when (event) {
            // Shift, Alt, Sym, fn and Ctrl type nothing, so they don't count.
            is KeyboardEvent.Pushed -> if (WpmMeter.counts(event.code)) wpm.input(now, field.length()) else return
            is KeyboardEvent.Tapped, is KeyboardEvent.Recognised, is KeyboardEvent.DeletedWord,
            is KeyboardEvent.Chosen -> wpm.input(now, field.length())
            is KeyboardEvent.FieldChanged -> wpm.text(event.text.length)
            else -> return
        }
        if (wpm.active(now) && !showingWpm) showWpm()
    }

    private fun showWpm() {
        main.removeCallbacks(wpmTick)
        val now = SystemClock.uptimeMillis()
        val speed = wpm.wpm(now)
        val target = (speed ?: 0).toFloat()
        shownWpm = when {
            !GuideMotion.enabled -> target
            target > shownWpm -> shownWpm + (target - shownWpm) * WPM_RISE
            // Down, it counts at a steady pace at least, so that it reaches nothing in a few seconds, not in a long tail.
            else -> max(target, shownWpm - max((shownWpm - target) * WPM_FALL, WPM_FALL_LEAST))
        }
        val shown = Math.round(shownWpm)
        val heat = WpmMeter.heat(shown)
        if (speed != null || shown > 0) {
            showingWpm = true
            title.text = SpannableStringBuilder("$shown").apply {
                val start = length
                append(getString(R.string.playground_wpm))
                setSpan(RelativeSizeSpan(WPM_LABEL_SIZE), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else if (showingWpm) {
            showingWpm = false
            title.text = PLAYGROUND_TITLE
        }
        showComment(WpmComments.step(shown))
        backdrop.show(GuideMotion.blend(backgroundOf(tone), GuideColours.ORANGE, heat * WPM_MOST_ORANGE))
        title.post { effects.update(WpmEffects.at(shown), commentStep, numberBounds("$shown")) }
        // It stops ticking once the burst is over and the number is back to nothing.
        if (speed != null || shown > 0) main.postDelayed(wpmTick, WPM_TICK_MILLIS)
        else {
            // The number is back to nothing, so the playground's own line returns, however long the last comment has shown.
            showComment(-1, force = true)
            shownWpm = 0f
            backdrop.show(backgroundOf(tone))
        }
    }

    /**
     * The comment for [step], in the place of the playground's line, from its step until the next one. Going up, it slides in and
     * the number gives a small pulse; counting down, it steps back quietly, and below the first step the playground's line returns.
     */
    private fun showComment(step: Int, force: Boolean = false) {
        if (step == commentStep) return
        // Each comment stays long enough to read, whichever way the speed goes next.
        val now = SystemClock.uptimeMillis()
        if (!force && commentStep >= 0 && now - commentAt < WpmMeter.COMMENT_MIN_MILLIS) return
        commentAt = now
        val rose = step > commentStep
        commentStep = step
        val line = comments.getOrNull(step) ?: PLAYGROUND_BODY
        if (rose && GuideMotion.enabled) {
            swapIn(body, line, 0)
            title.animate().cancel()
            title.scaleX = WPM_PULSE
            title.scaleY = WPM_PULSE
            title.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(WPM_PULSE_MILLIS)
                .setInterpolator(OvershootInterpolator(ENTER_OVERSHOOT)).start()
        } else body.text = line
    }

    /** Where the number is, in the show's own pixels, and the point that it pulses and wobbles around. */
    private fun numberBounds(number: String): RectF? {
        val layout = title.layout ?: return null
        val at = IntArray(2)
        val origin = IntArray(2)
        title.getLocationInWindow(at)
        effects.getLocationInWindow(origin)
        val inside = title.totalPaddingLeft + layout.getLineLeft(0)
        val baseline = title.totalPaddingTop + layout.getLineBaseline(0).toFloat()
        val wide = title.paint.measureText(number)
        title.pivotX = inside + wide / 2
        title.pivotY = baseline
        val left = at[0] - origin[0] + inside
        val top = at[1] - origin[1] + baseline
        return RectF(left, top + title.paint.ascent() * .8f, left + wide, top + title.paint.descent() * .2f)
    }

    /** The playground went away: the speed starts over, and the show stops at once, with everything back in place. */
    private fun calmWpm() {
        shownWpm = 0f
        if (showingWpm) title.text = PLAYGROUND_TITLE
        showingWpm = false
        backdrop.show(backgroundOf(tone))
        if (commentStep >= 0) body.text = PLAYGROUND_BODY
        commentStep = -1
        effects.update(WpmEffects.NONE, -1, null)
        title.rotation = 0f
        column.translationX = 0f
        column.translationY = 0f
    }

    private fun handle(event: KeyboardEvent) {
        if (playground) countWpm(event)
        // The keyboard reports its modifiers whenever it redraws them, as when a slide opens a panel. That is not you acting.
        if (::phantom.isInitialized && event !is KeyboardEvent.ModifierState) acted()
        when (event) {
            is KeyboardEvent.Pushed -> keyName(event.code)?.let {
                lastKey = it
                keys.light(it)
            }
            is KeyboardEvent.Resting -> if (event.key.isNotEmpty()) {
                keys.light(event.key)
                keys.touched()
            }
            is KeyboardEvent.HeldSymbol -> lastKey?.let { keys.flip(it, event.symbol) }
            is KeyboardEvent.ModifierState -> {
                panelOpen = event.panel != Modifiers.Panel.Closed
                if (!panelOpen) keys.face = null
                modifiersOff = !panelOpen && event.alt == Modifiers.Level.Off
                if (modifiersOff) modifiersWentOff()
            }
            // The drawing of the keys shows the panel as it is, so the slide mirrors the keyboard.
            is KeyboardEvent.PanelFace -> keys.face = event.labels.takeIf { event.panel != Modifiers.Panel.Closed }
            // On the slide about the bar, every swipe comes out wrong, so that there is always something to fix with one tap.
            is KeyboardEvent.Recognised -> if (!playground && flow.step.nearMiss) {
                GuideHooks.nearMiss = true
                if (passed) main.post { GuideHooks.stripPoint(1) }
            }
            // Save and Forget are only for show here, and the guide owns up to it.
            is KeyboardEvent.MenuPicked -> if (!playground) when (event.action) {
                "Save" -> main.postDelayed({ showHint(MENU_SAVED) }, MENU_HINT_MILLIS)
                "Forget" -> main.postDelayed({ showHint(MENU_FORGOT) }, MENU_HINT_MILLIS)
            }
            else -> Unit
        }
        if (!busy && ready) judge(event)
    }

    private fun judge(event: KeyboardEvent) {
        when (val verdict = flow.event(event)) {
            GuideVerdict.Done, GuideVerdict.Finished -> stepDone()
            is GuideVerdict.Stage -> showStage()
            // A hint about Alt or the panel waits a moment, to be sure that they are really still on: the keyboard reports its
            // modifiers on every redraw, and a passing state must not leave a hint about something that is no longer true.
            is GuideVerdict.Hint -> if (event is KeyboardEvent.ModifierState) {
                main.removeCallbacks(modifierHint)
                pendingModifierHint = verdict.text
                main.postDelayed(modifierHint, MODIFIER_HINT_MILLIS)
            } else showHint(verdict.text)
            GuideVerdict.Wait -> Unit
        }
    }

    /** A hint about Alt or the panel, waiting to be sure, and the one that shows, which goes as soon as it is no longer true. */
    private var pendingModifierHint: String? = null
    private var shownModifierHint: String? = null
    private var modifiersOff = true
    private val modifierHint = Runnable {
        val text = pendingModifierHint ?: return@Runnable
        pendingModifierHint = null
        if (modifiersOff || busy) return@Runnable
        showHint(text)
        shownModifierHint = text
    }

    /** Alt and the panel are off now: a hint about them, waiting or shown, is no longer true. */
    private fun modifiersWentOff() {
        main.removeCallbacks(modifierHint)
        pendingModifierHint = null
        if (shownModifierHint != null && hint.text == shownModifierHint) hideHint()
        shownModifierHint = null
    }

    /** The slide moved on, or back, to another instruction: only that one shows, with a tick, and the ghost follows it. */
    private fun showStage() {
        hideHint()
        haptics.tick()
        swapIn(body, flow.instruction, 0)
        showScript()
        // The swipe came out wrong on purpose: the right word, second on the bar, wiggles to show where to tap.
        GuideHooks.stripPoint(if (flow.step.nearMiss && flow.stage > 0) 1 else null)
        main.removeCallbacks(showHelp)
        if (flow.step.help != null) main.postDelayed(showHelp, HELP_AFTER_MILLIS)
    }

    /**
     * The real gesture happened. The screen sweeps to orange, the phone gives a pulse, and a line lands. The step stays, so you can
     * try it more: the keys still answer, and only the checks stop. Next is filled now.
     */
    private fun stepDone() {
        GuideHooks.stripPoint(null)
        busy = true
        passed = true
        main.removeCallbacks(startDemo)
        endDemo()
        main.removeCallbacks(showHelp)
        hideHint()
        haptics.guideChapter()
        keys.celebrate()
        backdrop.wipeTo(GuideColours.ORANGE)
        overlay.sweep()
        main.postDelayed({ if (passed) applyTone(GuideTone.Orange) }, WIPE_MILLIS)
        showReaction(flow.step.done)
        flow.step.rain?.let { effects.burst(it, RAIN_PIECES) }
        startPassage(flow.step.after)
        showScript()
        if (flow.chapterEnds) effects.confetti()
        main.removeCallbacks(settleReaction)
        main.postDelayed(settleReaction, REACTION_MILLIS)
    }

    private fun nextPressed() {
        if (changing || !ready) return
        // From the end, the next screen is the playground, the app's front page.
        if (flow.finished) {
            preferences.edit().remove(RESUME).apply()
            startActivity(Connected.pageIntent(this, "/"))
            finish()
            return
        }
        moveOn()
    }

    private fun backPressed() {
        if (changing || !ready || !flow.canGoBack) return
        moveOn(forward = false)
    }

    /**
     * The line that lands takes the title's place for a moment, with a small pop from its bottom left corner. It is in the title's
     * own box, so it fits there at any font size and covers nothing.
     */
    private fun showReaction(text: String) {
        title.animate().cancel()
        title.text = text
        title.translationX = 0f
        title.pivotX = 0f
        title.pivotY = title.height.toFloat()
        title.alpha = 0f
        title.scaleX = REACTION_FROM
        title.scaleY = REACTION_FROM
        title.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(REACTION_IN_MILLIS)
            .setInterpolator(OvershootInterpolator(REACTION_OVERSHOOT)).start()
        body.animate().alpha(REACTION_DIM).setDuration(FADE_MILLIS).start()
    }

    /** The story goes on, or back a step: to ink for a step behind the cream line, or orange for the ending. */
    private fun moveOn(forward: Boolean = true) {
        GuideHooks.stripPoint(null)
        changing = true
        busy = true
        passed = false
        main.removeCallbacks(startDemo)
        endDemo()
        again.visibility = View.GONE
        main.removeCallbacks(showHelp)
        main.removeCallbacks(settleReaction)
        hideHint()
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

    /**
     * The high note: lines cross the screen one after the other, then "Titan" flies in from the left and "Keys" from the right.
     * They lock together into the name with a click, the keys answer, and the slogan follows.
     */
    private fun finale(end: GuideStep) {
        again.visibility = View.VISIBLE
        again.text = "Again"
        haptics.guideFinale()
        for (sweep in 0 until FINALE_SWEEPS) overlay.sweep(delay = sweep * FINALE_STAGGER_MILLIS, thickness = BEAT_LINE + sweep * 2f)
        keys.celebrate()
        // The text stays: it is what you typed through the whole guide.
        enter(end)
        // The name and slogan wait for the halves to land.
        for (view in listOf(title, body, chapter)) {
            view.animate().cancel()
            view.alpha = 0f
            view.translationX = 0f
        }
        title.post { landName(end.title) }
        // No keys on the last screen: just the name, and the one boring thing.
        keyboardShown(false)
        for (view in listOf(field, phantom, keys, script)) view.animate().alpha(0f).setDuration(FADE_MILLIS)
            .withEndAction { view.visibility = View.INVISIBLE }.start()
        showEndChoices()
    }

    /**
     * The last screen's one boring thing: the offer to share swipes, in plain words, until it has an answer. Then Next goes to the
     * playground.
     */
    private fun showEndChoices() {
        guideAction.text = BORING_GUIDE_LABEL
        guideAction.visibility = View.VISIBLE
        next.visibility = View.VISIBLE
        next.text = END_NEXT
        val asking = Contributions.unanswered(this)
        showHint(if (asking) SHARE_PLAIN else END_HINT)
        offerFor(flow.step)
        if (!asking) offer.visibility = View.GONE
    }

    /** Flies the two halves of the name to where the title shows it, then shows the title in their place. */
    private fun landName(name: String) {
        val layout = title.layout
        val split = name.indexOf(NAME_SECOND_HALF)
        if (layout == null || split <= 0 || overlay.width == 0) {
            nameLanded()
            return
        }
        val at = IntArray(2)
        val origin = IntArray(2)
        title.getLocationInWindow(at)
        overlay.getLocationInWindow(origin)
        val baseline = title.getLineBounds(0, null).toFloat()
        val x = at[0] - origin[0] + title.compoundPaddingLeft + layout.getLineLeft(0)
        val paint = Paint(title.paint).apply { color = title.currentTextColor }
        overlay.flyIn(name.substring(0, split), name.substring(split), paint, x, at[1] - origin[1] + baseline, ::nameLanded)
    }

    private fun nameLanded() {
        haptics.guideChapter()
        overlay.sweep(thickness = LINE)
        keys.celebrate()
        title.alpha = 1f
        // The name clicks into place: a small push out from its corner, then back.
        title.pivotX = 0f
        title.pivotY = title.height.toFloat()
        title.scaleX = NAME_CLICK
        title.scaleY = NAME_CLICK
        title.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(NAME_CLICK_MILLIS)
            .setInterpolator(OvershootInterpolator(REACTION_OVERSHOOT)).start()
        body.animate().alpha(1f).setStartDelay(SLOGAN_DELAY_MILLIS).setDuration(ENTER_MILLIS).start()
        chapter.animate().alpha(1f).setStartDelay(SLOGAN_DELAY_MILLIS * 2).setDuration(ENTER_MILLIS).start()
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
        for (view in listOf(title, body)) view.animate().alpha(0f).setDuration(FADE_MILLIS).start()
        // The ending hides the line above the title until its name lands, so it comes back here.
        chapter.animate().alpha(1f).setStartDelay(0).setDuration(FADE_MILLIS).start()
        backdrop.wipeTo(backgroundOf(GuideTone.Ink))
        overlay.sweep()
        main.postDelayed({
            changing = false
            applyTone(GuideTone.Ink)
            seeding = true
            field.setText("")
            seeding = false
            for (view in listOf(field, phantom, keys, script)) {
                view.visibility = View.VISIBLE
                view.animate().alpha(1f).setDuration(FADE_MILLIS).start()
            }
            keyboardShown(true)
            enter(flow.step)
        }, WIPE_MILLIS)
    }

    /** Leaves at any time. The guide remembers where you stopped, and opens there from the guide page. */
    private fun leave() {
        // The playground's setup slide closes to the app's setup page, which waits for a touch to start again.
        if (playground && settingUp) leavePlayground(PLAYGROUND_SETUP) else finish()
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

        // Setup, in ms. These are safe to change.
        private const val SETUP_REQUEST = 7
        /** How often the guide looks at the part whose page is open. Reading these settings is cheap. */
        private const val SETUP_POLL_MILLIS = 400L
        /** How long the guide waits to be in front again after it closes a page, before it sends for itself. */
        private const val SETUP_RETURN_MILLIS = 350L
        /** The beat after a part is done, before the next part shows, and the wait before its page opens by itself. */
        private const val SETUP_BEAT_MILLIS = 900L
        private const val SETUP_AUTO_MILLIS = 1300L
        /** How long "All set." stays before the story starts. */
        private const val SETUP_FINISH_MILLIS = 1400L
        private const val SETUP_NO_PAGE = "This phone doesn't have that page. Find it in Settings, then come back."

        private const val FIELD_HINT = "Type here. Or swipe."
        private const val TAG = "TitanKeysGuide"
        private const val PLAYGROUND = "playground"
        private const val PLAYGROUND_LABEL = "Playground"
        /** The share slide's offer (#34), while it is unanswered. It opens the full disclosure. */
        private const val SHARE_OFFER = "Share my swipes"
        /** The ending points to the written guide, which explains everything that the slides leave out. */
        private const val END_HINT = "Everything else is in Settings > Guide > The boring guide."
        /** The one boring thing at the end, in plain words: it is about consent, so no jokes here. */
        private const val SHARE_PLAIN = "One more thing. If you agree, TitanKeys sends us examples of your swipes so we can make " +
            "swiping better. It's off until you agree, and you'll see exactly what's sent first. Everything else is in " +
            "Settings > Guide > The boring guide."
        private const val END_NEXT = "Go type something"
        /** How many things the paste lesson rains down. */
        private const val RAIN_PIECES = 26
        private const val PLAYGROUND_TITLE = "TitanKeys"
        /** The playground's speed updates this often while you type, and its colour eases this much of the way each time. */
        private const val WPM_TICK_MILLIS = 120L
        /** How much of the way to the real speed the number goes each tick, up and down, and the least it counts down by. */
        private const val WPM_RISE = .35f
        private const val WPM_FALL = .12f
        private const val WPM_FALL_LEAST = 1.5f
        /** The number's pulse at each new step of the comments. */
        private const val WPM_PULSE = 1.14f
        private const val WPM_PULSE_MILLIS = 320L
        private const val WPM_LABEL_SIZE = .35f
        /** At full speed the screen is this much of the way to orange, so that the words stay easy to read. */
        private const val WPM_MOST_ORANGE = .85f
        private const val PLAYGROUND_BODY = "Type or swipe whatever you like. Follow the line above, or ignore it."

        // The look, in dp. These are safe to change. Words fit their box at any system font size, because the boxes set their size.
        private val HEAVY: Typeface = Typeface.create(Typeface.DEFAULT, 900, false)
        private val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        /** The same as the key columns' side, so that the drawn keys line up with the Sym panel's labels. */
        private const val GUTTER = Layout.KEY_SIDE_DP
        private const val TOP_ACTION = 32
        private const val TITLE_SMALLEST = 22
        private const val TITLE_LARGEST = 76
        private const val BODY_SMALLEST = 11
        private const val BODY_LARGEST = 20
        private const val CHAPTER_ALPHA = .55f
        private const val TITLE_SHARE = 2.2f
        private const val BODY_SHARE = 1.4f
        private const val FIELD_RADIUS = Radii.MEDIUM
        private const val HINT_RADIUS = Radii.MEDIUM
        private const val OFFER_RADIUS = Radii.LARGE
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
        private const val REACTION_DIM = .3f
        /** The size that the line that lands grows from. */
        private const val REACTION_FROM = .86f
        /** How long a finished step's line stays before the step's words come back. */
        private const val REACTION_MILLIS = 1400L
        /** When the new background has reached the middle of the screen. */
        private const val WIPE_MILLIS = 300L
        private const val BEAT_LINE = 3f
        private const val FINALE_SWEEPS = 6
        private const val FINALE_STAGGER_MILLIS = 110L
        /** The ending's name is "Titan" and "Keys", which fly in from the two sides and lock together. */
        private const val NAME_SECOND_HALF = "Keys"
        /** How far the name pushes out when it locks, and how long it takes to settle. */
        private const val NAME_CLICK = 1.08f
        private const val NAME_CLICK_MILLIS = 420L
        /** The slogan comes after the name has clicked into place, and the line above it after that. */
        private const val SLOGAN_DELAY_MILLIS = 260L
        private const val HELP_AFTER_MILLIS = 9000L
        /** How long Alt or the panel must stay on before the guide says so. */
        private const val MODIFIER_HINT_MILLIS = 350L
        /** How strong the words after the next one are in that line. */
        private const val SCRIPT_REST = .55f
        /** The passage: how dim the words behind you are, the box round a mistake, and how long a line takes to slide up. */
        private const val PASSAGE_DONE = .4f
        private const val PASSAGE_WRONG = 0xFFFF4D4D.toInt()
        private const val PASSAGE_WRONG_ALPHA = .55f
        private const val PASSAGE_SLIDE_MILLIS = 260L
        private const val PASSAGE_SKIPPED_ALPHA = .22f
        private const val PASSAGE_BOX = .08f
        /** What the guide says when Save or Forget is pressed, as they're only for show here. */
        private const val MENU_SAVED = "Srsly? Relax, we didn't actually save it. It's a guide."
        private const val MENU_FORGOT = "Srsly? Relax, we didn't actually forget it. It's a guide."
        private const val MENU_HINT_MILLIS = 120L
        private const val BORING_GUIDE = "/settings/guide-boring"
        private const val BORING_GUIDE_LABEL = "The boring guide"

        // Demonstrations: back to back until you act. These are safe to change.
        /** How long nobody acts before a slide shows its gesture again after you act. */
        private const val DEMO_IDLE_MILLIS = 3000L
        /** When a new slide shows its gesture: as soon as the slide's words have slid in. */
        private const val DEMO_ENTER_MILLIS = ENTER_STAGGER_MILLIS + ENTER_MILLIS
        /** The pause between one showing and the next. */
        private const val DEMO_GAP_MILLIS = 1000L
        /** How long a demonstration with no ghost finger takes. One with a ghost takes as long as the ghost. */
        private const val DEMO_MILLIS = 4200L
        private const val DEMO_FRAME_MILLIS = 33L
        /** How strong the grey words are. */
        private const val PHANTOM_ALPHA = .38f
        /** The part of the demonstration that typing a word takes, and when a swiped word lands, at the end of the ghost's path. */
        private const val TYPE_SHARE = .8f
        private const val SWIPE_LANDS = .72f
        /** When the last mark after a swiped word is in. */
        private const val MARKS_DONE = .94f
        /** When the strip puts the right word in place of the slip. */
        private const val FIX_LANDS = .62f
        /** The part that the ghost's taps take, before the cursor moves or the selection shows. */
        private const val TAPS_SHARE = .45f
        private const val CARET_ALPHA = .7f
        private const val SELECT_ALPHA = .4f
        /** When a held letter turns into its symbol. */
        private const val HOLD_TURNS = .55f
        /** The part that pushing the keys takes, before the text from the panel appears. */
        private const val INSERT_SHARE = .6f
        /** When a slip is typed and the bar starts to fix it, and when Space puts the right word in, after the bar's swap. */
        private const val SLIP_TYPED = .5f
        private const val SLIP_FIXED = .64f
        /** The part that Alt and the letter take, before the symbol appears. */
        private const val ALT_KEYS_SHARE = .5f
        /** When fn and C light, once the word is selected. */
        private const val COPY_KEYS = .72f
        /** How many words the bar's demonstrations show. */
        private const val BAR_WORDS = 3

        /** True until the guide has opened once, so it opens by itself after setup. */
        fun due(context: Context) = !context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(SHOWN, false)

        fun start(activity: Activity) {
            activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(SHOWN, true).apply()
            activity.startActivity(Intent(activity, GuideActivity::class.java))
        }

        /** The guide opened Accessibility for setup and waits for TitanKeys to be turned on there. */
        @Volatile private var awaitingAccessibility = false

        /**
         * TitanKeys was turned on in Accessibility. If the guide asked for it, the guide comes to the front again: Accessibility
         * opens its page for TitanKeys over its list, which the guide cannot close, and Android lets the service bring it back.
         */
        fun accessibilityOn(context: Context) {
            if (!awaitingAccessibility) return
            awaitingAccessibility = false
            try {
                context.startActivity(Intent(context, GuideActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            } catch (error: RuntimeException) {
                // Then the guide waits until you come back to it.
            }
        }

        const val PLAYGROUND_GUIDE = "guide"
        const val PLAYGROUND_SETTINGS = "settings"
        const val PLAYGROUND_CLOSED = "closed"
        const val PLAYGROUND_SETUP = "setup"
        /** Told where to go when the playground closes. Only one playground is open at a time. */
        private var playgroundExit: ((String) -> Unit)? = null

        /** Interactive guides that are open now. Only the main thread changes this. */
        private var guidesOpen = 0
        /** The playground asked for while a guide was open, which opens once the guide closes. */
        private var waitingPlayground: (() -> Unit)? = null

        /**
         * Opens the playground, the app's front page. [exit] gets [PLAYGROUND_GUIDE] or [PLAYGROUND_SETTINGS] from its buttons,
         * [PLAYGROUND_SETUP] when it closes from its setup slide, or [PLAYGROUND_CLOSED] when something else closed it.
         * The front page can ask for it while the guide is open, as when setup ends in the guide, so then it waits for the guide.
         */
        fun playground(activity: Activity, exit: (String) -> Unit) {
            playgroundExit = exit
            // The front page asks each time its window is in front, so one that is open, or that a button just closed, is enough.
            // A playground that Android is closing counts as closed: it can close after the app's window is in front. And with
            // the app's window in front, no playground is: the app's window is at the bottom of the app's screens.
            val showing = playgrounds.any { !it.isFinishing } && !activity.hasWindowFocus()
            val now = SystemClock.uptimeMillis()
            val quiet = now - playgroundStarting < PLAYGROUND_QUIET_MILLIS
            Log.d(TAG, "playground asked: open ${playgrounds.size}, showing $showing, quiet $quiet, guides $guidesOpen")
            if (showing || quiet) return
            val open = {
                // One that is starting does not count as open yet, so a second ask in the meantime is left out.
                playgroundStarting = SystemClock.uptimeMillis()
                activity.startActivity(Intent(activity, GuideActivity::class.java).putExtra(PLAYGROUND, true))
            }
            if (guidesOpen > 0) waitingPlayground = open else open()
        }

        /** The front page went away, so a playground that waits for a guide to close is no longer wanted. */
        fun cancelPlayground() {
            waitingPlayground = null
        }

        /** Playgrounds that are open now. Main thread only. */
        private val playgrounds = ArrayList<GuideActivity>()
        /** When a playground was asked to start, until it has started. */
        private var playgroundStarting = -PLAYGROUND_QUIET_MILLIS
        /** A playground takes a moment to start. A second ask in this time is left out. */
        private const val PLAYGROUND_QUIET_MILLIS = 1500L
    }
}
