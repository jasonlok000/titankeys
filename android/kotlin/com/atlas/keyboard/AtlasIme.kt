package com.atlas.keyboard

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Paint
import android.hardware.display.DisplayManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.KeyCharacterMap
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.SurroundingText
import android.widget.LinearLayout
import com.atlas.engine.Calibration
import com.atlas.engine.EmojiPages
import com.atlas.engine.HoldEngine
import com.atlas.engine.Swipe
import com.atlas.engine.SwipeTyping
import com.atlas.engine.TypingSettings
import java.io.IOException
import kotlin.math.abs

/**
 * Atlas: swipe typing on the Titan 2 keyboard's touchpad, with a suggestion strip for the physical
 * keyboard. The keyboard window stays open while editing so the firmware can route touchpad events.
 */
class AtlasIme : InputMethodService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var touchpad: Touchpad
    private lateinit var haptics: Haptics
    private lateinit var composer: Composer
    private lateinit var autocorrect: AutocorrectLoader
    private lateinit var editor: ConnectionEditor
    private lateinit var overlay: TouchpadOverlay
    private val contact = Contact()
    private val cursor = CursorMode()
    private val cursorGesture = CursorGesture()
    /** The finger's path through cursor mode, drawn on the home screen's keyboard like a swipe. */
    private var cursorPath = ArrayList<Sample>()
    private var cursorControl = true
    /** Taps on a tap key that may be part of a double tap. Each call gives its input, typed if the argument is true. */
    private val heldTaps = ArrayList<(Boolean) -> Unit>()
    /** The selection start the app last reported, for apps that don't say where their text sits. */
    private var lastSelectionStart = -1
    private val modifiers = Modifiers()
    private var strip: Strip? = null
    private var panel: SymPanel? = null
    private var emoji: EmojiPages? = null
    /** The key that last picked from the panel, so its repeats type nothing once the panel has closed. */
    private var pickedKey = 0
    private var field = Field.None
    /** The apps that the user added to the Enter key setting, read when a field starts. */
    private var enterApps = emptyMap<String, EnterKey.SendMethod>()
    /** The raw input log for this field. It is null when the setting is off or the field must not be recorded. */
    private var rawLog: RawLog.Session? = null
    private var editing = false
    private var typedInField = false
    private var fieldStartedAt = 0L
    private var passingBack = false
    /** Back hid the strip, so it stays hidden in this field until you tap the field or type. */
    private var hiddenByBack = false
    private var inLauncher = false
    private var launcherFieldHasText = false
    private val consumedKeys = HashSet<Int>()
    private var deadKeyPending = false
    private var tapKeys = ""
    private var holdSymbols = false
    private val holdEngine = HoldEngine(HoldEngine.Output { replaceHeldLetter() })
    /** The press that can still become its symbol. Its letter is already typed. */
    private var heldLetter: KeyEvent? = null
    private class PendingInput(var ready: Boolean, var action: (() -> Unit)?, val event: KeyEvent? = null,
                               val settlement: Contact.Settlement? = null)
    private val pendingInput = ArrayDeque<PendingInput>()
    private var inputGeneration = 0
    private var drainScheduled = false
    private val settlements = LinkedHashMap<Contact.Settlement, Runnable>()
    private val pieces = SwipePieces({ composer.liftOffMillis.toLong() }, { composer.canContinueSwipe }, ::isTapKey)
    private var lastTouch: List<Sample>? = null
    private val showStrip = Runnable {
        if (hiddenByBack || !editing || currentInputBinding == null) return@Runnable
        // Apps can drop a field yet still start input on it, so only a field that answers gets the strip.
        if (currentInputConnection?.getTextBeforeCursor(1, 0) == null) return@Runnable
        try {
            // A cancelled show request can leave the client waiting for a window surface.
            showWindow(true)
            requestShowSelf(0)
        } catch (error: RuntimeException) {
            // Titan firmware can drop its current client before Android reports the editor gone.
            Log.w("Atlas", "Could not show the suggestion strip", error)
        }
    }

    private val hideStrip = Runnable {
        if (editing) return@Runnable
        try {
            hideWindow()
            requestHideSelf(0)
        } catch (error: RuntimeException) {
            Log.w("Atlas", "Could not hide the suggestion strip", error)
        }
    }

    /** An app's own hide, such as from a Done key, leaves the window closed, so a panel that opens then must show it. */
    private val showPanel = Runnable {
        if (!panelShown || isInputViewShown) return@Runnable
        showStrip.run()
        // It could not show, so it must not take the next key.
        if (!isInputViewShown) closePanel()
    }

    private val showCtrlOverlay = Runnable {
        modifiers.ctrlWaitOver(SystemClock.uptimeMillis())
        showModifiers()
        if (modifiers.ctrlOverlay && !isInputViewShown) main.post(showPanel)
    }

    // Apps on Android's newer back handling ignore a back key from a keyboard, so a system back goes first.
    private val sendBack = Runnable { if (!BackService.back() && editing) sendDownUpKeyEvents(KeyEvent.KEYCODE_BACK) }

    private val confirmDoubleTap = Runnable {
        if (!cursorGesture.confirm()) return@Runnable
        finishHeldTaps(type = false)
        haptics.click()
        enqueue { selectWord() }
    }

    private val checkHeldTaps = Runnable { settleHeldTaps() }

    private fun startCursor(from: Sample, samples: List<Sample>) {
        cursor.start(from)
        cursor.move(samples)
        // The contact now steers the cursor, so it must not also type when it lifts.
        contact.cancelActive()
        finishHeldTaps(type = false)
        Trace.touch(cursorPath, false)
        haptics.click()
        stepCursor.reset()
        stepCursor.run()
    }

    /** Space set as a tap key would type the first tap of a double tap, so a tap waits until it can't be one. */
    private fun holdTap(finish: (Boolean) -> Unit) {
        heldTaps += finish
        main.removeCallbacks(checkHeldTaps)
        main.postDelayed(checkHeldTaps, CursorGesture.CONFIRM_MILLIS)
    }

    private fun settleHeldTaps() {
        if (heldTaps.isNotEmpty() && !cursorGesture.deciding(SystemClock.uptimeMillis())) finishHeldTaps(type = true)
    }

    private fun finishHeldTaps(type: Boolean) {
        main.removeCallbacks(checkHeldTaps)
        val taps = heldTaps.toList()
        heldTaps.clear()
        taps.forEach { it(type) }
    }

    /**
     * Checks the lean every frame so speed follows the finger at once. Leaning in moves one letter straight
     * away, then letters build up at the current speed.
     */
    private val stepCursor = object : Runnable {
        private var owed = 1f
        private var last = 0L

        override fun run() {
            if (!cursor.active) return
            val now = SystemClock.uptimeMillis()
            val direction = cursor.direction()
            if (direction == 0) owed = 1f
            else if (last != 0L) owed += cursor.speed(now) * (now - last) / 1000
            if (direction != 0 && owed >= 1f) {
                owed = (owed - 1f).coerceAtMost(1f)
                moveCursor(direction)
            }
            last = now
            main.postDelayed(this, FRAME_MILLIS)
        }

        fun reset() {
            owed = 1f
            last = 0L
        }

        /** Carries on after a slip, without the extra first step a fresh lean gets. */
        fun resume() {
            last = 0L
            main.removeCallbacks(this)
            run()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Calibration.init(this)
        haptics = Haptics(this)
        touchpad = Touchpad(getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY))
        editor = ConnectionEditor(::getCurrentInputConnection, ::getCurrentInputEditorInfo,
            { delay, action -> main.postDelayed(action, delay) }, { enterApps })
        composer = Composer(editor, EngineWords(SwipeTyping.get(this)),
            { delay, action -> main.postDelayed(action, delay) }, ::clearSettlements,
            { TypingSettings.autoCapitalise(this) }) { strip?.words = it }
        composer.autoPunctuation = { TypingSettings.autoPunctuation(this) }
        composer.onStroke = Trace::stroke
        composer.onAutocorrect = haptics::wordChanged
        autocorrect = AutocorrectLoader(this, SwipeTyping.get(this))
        // The firmware hands touchpad events to the topmost window titled "InputMethod", which is this
        // keyboard's window. They arrive at the window, not at any view, so catch them there.
        val window = window.window!!
        window.callback = TouchpadWindowCallback(window.callback) { event ->
            (editing && touchpad.accepts(event)).also { if (it) motion(event) }
        }
        // With no field active, a touchpad event only stops here, so that the app under the overlay does not scroll.
        overlay = TouchpadOverlay(this) { event ->
            touchpad.accepts(event).also { if (it && editing) motion(event) }
        }
        TypingSettings.listen(this, overlaySetting)
        updateOverlay()
        loadEmoji()
    }

    /** The settings screen is open when this changes, so the keyboard is idle and no field start would apply it. */
    private val overlaySetting = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == TypingSettings.TOUCHPAD_ONLY_IN_FIELDS) updateOverlay()
    }

    private fun updateOverlay() {
        if (editing || TypingSettings.touchpadOnlyInFields(this)) overlay.show() else overlay.hide()
    }

    /** Reads the emoji list away from the main thread, which also loads the emoji font before Sym is first pressed. */
    private fun loadEmoji() {
        Thread {
            val recents = getSharedPreferences(EMOJI_PREFERENCES, MODE_PRIVATE).getString(RECENT_EMOJI, "").orEmpty().split(" ")
            val paint = Paint()
            val groups = try {
                assets.open("emoji.txt").reader().use { EmojiPages.parse(it, paint::hasGlyph) }
            } catch (error: IOException) {
                Log.w("Atlas", "Could not read the emoji list", error)
                return@Thread
            }
            main.post { emoji = EmojiPages(groups, recents).also { panel?.emoji = it } }
        }.start()
    }

    override fun onCreateInputView(): View {
        val words = Strip(this) { index -> haptics.wordChanged(); enqueue { composer.choose(index) } }
        val keys = SymPanel(this,
            pick = { item -> enqueue { typePanelItem(item) } },
            useQuickSetting = { item -> enqueue { useQuickSetting(item) } },
            quickSettingOn = ::quickSettingOn,
            pressAlt = { enqueue { modifiers.toggleSettings(); showModifiers() } },
        )
        strip = words
        panel = keys
        keys.emoji = emoji
        words.showSuggestions = showSuggestions()
        showModifiers()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(keys)
            addView(words)
        }
    }

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        // A press from the field before must not change a letter here.
        holdEngine.cancel()
        heldLetter = null
        preserveDestinationInput()
        editor.cancelState()
        super.onStartInput(info, restarting)
        editing = info.inputType != EditorInfo.TYPE_NULL
        if (!restarting) {
            typedInField = false
            fieldStartedAt = SystemClock.uptimeMillis()
            // Apps also restart input on the field that stays, so only a new start ends what Back did.
            hiddenByBack = false
        }
        field = if (editing) Field.of(info.inputType, info.imeOptions) else Field.None
        // Android only calls onFinishInput first when the input is not restarting.
        rawLog?.finish()
        rawLog = if (editing) RawLog.start(this, info, restarting) else null
        composer.log = rawLog
        inLauncher = editing && TypingSettings.launcherHidesBar(this) && info.packageName == launcherPackage()
        launcherFieldHasText = inLauncher && info.initialSelStart > 0
        contact.releaseKeys()
        clearSettlements()
        modifiers.fieldStarted(restarting, editing)
        deadKeyPending = false
        showModifiers()
        readSettings()
        if (editing && !restarting) rememberEnterApp(info)
        main.removeCallbacks(showStrip)
        main.removeCallbacks(hideStrip)
        if (editing) {
            composer.start(field, info.initialSelStart, info.initialSelEnd)
            main.post(showStrip)
        } else {
            composer.stop()
            // A returning window starts with no field just before its own field takes focus. A strip that
            // hides for that moment tells the app the keyboard was closed, and the app then drops its field.
            main.postDelayed(hideStrip, NO_FIELD_HIDE_MILLIS)
        }
        updateOverlay()
    }

    /** Puts the saved settings to use. A field start does this, and so does a change that must apply in the field that is open. */
    private fun readSettings() {
        composer.liftOffMillis = TypingSettings.liftOffGraceMillis(this)
        tapKeys = TypingSettings.tapKeys(this)
        holdSymbols = TypingSettings.holdSymbols(this)
        holdEngine.threshold(TypingSettings.holdSymbolMillis(this))
        modifiers.fnIsCtrl = TypingSettings.fnAsCtrl(this)
        modifiers.doublePressLocks = TypingSettings.doublePressLocks(this)
        cursorControl = TypingSettings.cursorControl(this)
        contact.swipes = TypingSettings.swipeTyping(this)
        enterApps = EnterKey.apps(TypingSettings.enterApps(this))
        val corrects = TypingSettings.autocorrect(this)
        composer.autocorrect = autocorrect.autocorrect.takeIf { corrects }
        if (!corrects) autocorrect.release()
        strip?.showSuggestions = showSuggestions()
    }

    /** Only the package is kept, so the Enter key setting can offer apps without asking to see every installed app. */
    private fun rememberEnterApp(info: EditorInfo) {
        val app = info.packageName ?: return
        if (app == packageName || !EnterKey.recordsApp(info.inputType, info.imeOptions)) return
        val recent = TypingSettings.recentEnterApps(this)
        val updated = EnterKey.recentApps(recent, app)
        if (updated != recent) TypingSettings.recentEnterApps(this, updated)
    }

    // Launchers like Niagara keep a search field focused on the home screen, where the bar only gets in the way until you type.
    // Password, email and number fields get no suggestions, so the bar would only be an empty strip.
    private fun showSuggestions() = TypingSettings.suggestionBar(this) && (!inLauncher || launcherFieldHasText) && field.suggestions

    private fun launcherPackage(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Back belongs to the app, even though it also hides the strip on the way.
        backDisposition = BACK_DISPOSITION_WILL_NOT_DISMISS
    }

    override fun onWindowShown() {
        super.onWindowShown()
        // Android draws its hide and switch-keyboard buttons in this bar; the strip has no room for them.
        window.window?.insetsController?.hide(WindowInsets.Type.captionBar())
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        // A panel that nobody can see must not take the next key.
        closePanel()
        // A delayed hide from the previous app arrives just after this field starts. A later hide, or one
        // after you have typed, is the app's own, such as a tap outside the field or a Done key.
        main.removeCallbacks(showStrip)
        if (passingBack) {
            // Now that the strip is gone, the app's own back can run. If its field stays, the strip stays hidden.
            passingBack = false
            hiddenByBack = editing
            main.post(sendBack)
        } else if (editing && !typedInField && SystemClock.uptimeMillis() - fieldStartedAt < STALE_HIDE_MILLIS) {
            main.post(showStrip)
        }
    }

    /** A key or swipe in a field that Back left hidden brings the strip back. */
    private fun showStripAfterBack() {
        if (!hiddenByBack) return
        hiddenByBack = false
        main.post(showStrip)
    }

    override fun onUnbindInput() {
        main.removeCallbacks(showStrip)
        main.removeCallbacks(sendBack)
        // The app has lost the keyboard, so coming back to it counts as a new start.
        hiddenByBack = false
        super.onUnbindInput()
    }

    override fun onFinishInput() {
        // The letter is already typed, so a press that is still down can only stay a letter.
        holdEngine.cancel()
        heldLetter = null
        preserveDestinationInput()
        // The old connection is still owned here; only a verified final explicit choice is learned.
        composer.stop(terminal = true)
        editor.cancelState()
        editing = false
        field = Field.None
        rawLog?.finish()
        rawLog = null
        composer.log = null
        main.removeCallbacks(showStrip)
        clearSettlements()
        contact.cancel()
        endCursor()
        deadKeyPending = false
        closePanel()
        updateOverlay()
        super.onFinishInput()
    }

    override fun onEvaluateInputViewShown() = editing

    /** Android calls this when you tap the field, so the tap also brings the strip back after Back. */
    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        hiddenByBack = false
        return editing
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onUpdateSelection(oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int, composingStart: Int, composingEnd: Int) {
        super.onUpdateSelection(oldStart, oldEnd, newStart, newEnd, composingStart, composingEnd)
        inputLog()?.selection(newStart, newEnd, composingStart, composingEnd,
            currentInputConnection?.getTextBeforeCursor(RawLog.BEFORE_LENGTH, 0))
        lastSelectionStart = newStart
        if (inLauncher && (newStart > 0) != launcherFieldHasText) {
            launcherFieldHasText = newStart > 0
            strip?.showSuggestions = showSuggestions()
        }
        if (editing && composer.selectionChanged(newStart, newEnd)) clearSettlements()
    }

    /**
     * The raw log for input that arrives now. The keyboard hears that the field changed only after the app has moved,
     * so input after a key that can move to another field may belong to a password. A gap marker takes its place.
     */
    private fun inputLog(): RawLog.Session? {
        val log = rawLog ?: return null
        val leavingKey = pendingInput.any { it.event?.action == KeyEvent.ACTION_DOWN && it.event.keyCode in FIELD_LEAVING_KEYS }
        if (!leavingKey && !composer.mayLeaveField) return log
        log.skip()
        return null
    }

    /** Any app with a focused field can send a command, so a bad one must not stop the keyboard. */
    override fun onAppPrivateCommand(action: String?, data: Bundle?) {
        super.onAppPrivateCommand(action, data)
        if (action != RawLog.MARK_ACTION) return
        val log = rawLog ?: return
        try {
            log.mark(data?.getString("kind"), data?.getString("label"))
        } catch (error: RuntimeException) {
            Log.w("Atlas", "Could not read a raw log mark", error)
        }
    }

    override fun onUpdateCursorAnchorInfo(info: CursorAnchorInfo) {
        super.onUpdateCursorAnchorInfo(info)
        editor.cursorAnchor(info)
    }

    /** While the strip is hidden, the touchpad reaches the keyboard through the focused app. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!editing || !touchpad.accepts(event)) return super.onGenericMotionEvent(event)
        motion(event)
        return true
    }

    private fun motion(event: MotionEvent) {
        // The panel works in every field, also where the touchpad types no words.
        if (!field.words && !panelShown) return
        typedInField = true
        val samples = touchpad.samples(event)
        Recording.sink?.motion(event, samples)
        inputLog()?.motion(event, samples)
        if (panelShown) hover(event, samples)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val first = samples.firstOrNull() ?: Sample(Float.NaN, Float.NaN, event.eventTime)
                haptics.begin(samples.firstOrNull())
                contact.down(first)
                if (cursor.resume(first)) {
                    // Back within the slip recovery period: still steering the cursor, never typing.
                    main.removeCallbacks(endSlippedCursor)
                    contact.cancelActive()
                    cursor.move(samples.drop(1))
                    traceCursor(samples, ended = false)
                    stepCursor.resume()
                    return
                }
                contact.move(samples.drop(1))
                stopCursor()
                main.removeCallbacks(confirmDoubleTap)
                cursorPath = ArrayList(samples.filter { it.valid })
                // With Alt on or the panel open the touchpad does not type, so the cursor waits until they are off.
                // A finger the contact ignored landed while typing, so it must not start the cursor.
                cursorGesture.down(first, cursorControl && !altOrPanel && contact.travel != null)
                cursorGesture.move(samples.drop(1))?.let {
                    startCursor(it, samples.drop(1))
                    return
                }
                settleHeldTaps()
                // A touch that may be the last swipe carrying on keeps that swipe on the trail until it is settled.
                val carried = lastTouch?.takeIf {
                    Trace.listening && contact.points.isNotEmpty()
                        && letterSpace(it).couldContinue(first.x, first.y * 4 / 3, first.time - it.last().time, composer.liftOffMillis.toLong())
                }.orEmpty()
                Trace.touch(contact.points, false, carried)
            }
            MotionEvent.ACTION_MOVE -> {
                cursor.move(samples)
                if (cursor.active) {
                    traceCursor(samples, ended = false)
                    return
                }
                cursorPath.addAll(samples.filter { it.valid })
                contact.move(samples)
                cursorGesture.move(samples)?.let {
                    startCursor(it, samples)
                    return
                }
                settleHeldTaps()
                val travel = contact.travel
                val first = contact.points.firstOrNull()
                // Wait until a configured tap key's contact is a swipe, so short taps pulse only on acceptance.
                if (contact.swipes && !altOrPanel && travel != null && first != null && (travel >= Contact.SWIPE_KEYS
                    || !isTapKey(Layout.keyAt(first.x, first.y)))) haptics.move(samples)
                Trace.touch(contact.points, false)
            }
            MotionEvent.ACTION_UP -> {
                val doubleTapped = cursorGesture.up(samples, event.eventTime)
                if (cursor.active) {
                    main.removeCallbacks(stepCursor)
                    cursor.lift()
                    val slips = composer.liftOffMillis > 0
                    // Through a slip the path stays open, so a finger that comes back carries on drawing it.
                    traceCursor(samples, ended = !slips)
                    if (slips) main.postDelayed(endSlippedCursor, composer.liftOffMillis.toLong()) else stopCursor()
                    return
                }
                stopCursor()
                val points = contact.points
                if (doubleTapped) main.postAtTime(confirmDoubleTap, event.eventTime + CursorGesture.CONFIRM_MILLIS)
                contact.up(samples, SystemClock.uptimeMillis())?.let { pending ->
                    lastTouch = pending.points
                    if (settlements.size == Contact.MAX_PENDING_CONTACTS || !makeRoom()) {
                        contact.settle(pending, pending.deadline)
                        return@let
                    }
                    val input = PendingInput(false, null, settlement = pending)
                    pendingInput.addLast(input)
                    val settle = Runnable {
                        settlements.remove(pending)
                        val settled = contact.settle(pending, SystemClock.uptimeMillis())
                        val result = if (pending.cancelled || altOrPanel) settled else pieces.settle(pending, settled)
                        val finish = { type: Boolean ->
                            input.action = { if (type) result?.let(::outcome) }
                            input.ready = true
                            drainInput()
                        }
                        if (result is Contact.Outcome.Tap && isTapKey(result.key) && cursorGesture.deciding(SystemClock.uptimeMillis())) holdTap(finish)
                        else finish(true)
                    }
                    settlements[pending] = settle
                    main.postAtTime(settle, pending.deadline)
                }
                settleHeldTaps()
                Trace.touch(if (points.size <= Swipe.MAX_POINTS && samples.all { it.valid }) points else emptyList(), true)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                contact.cancelActive()
                endCursor()
                Trace.touch(emptyList(), true)
            }
        }
    }

    /** A finger that rests on a key shows that key in the panel, until the finger lifts. */
    private fun hover(event: MotionEvent, samples: List<Sample>) {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            panel?.hover = ""
            return
        }
        val last = samples.lastOrNull { it.valid } ?: return
        panel?.hover = Layout.keyAt(last.x, last.y)
    }

    private val endSlippedCursor = Runnable { stopCursor() }

    private fun traceCursor(samples: List<Sample>, ended: Boolean) {
        val added = samples.filter { it.valid }
        // The preview draws at most a swipe's worth of points; a long session starts a fresh path.
        if (cursorPath.size + added.size > Swipe.MAX_POINTS) cursorPath = ArrayList(listOfNotNull(cursorPath.lastOrNull()))
        cursorPath.addAll(added)
        Trace.touch(cursorPath, ended)
    }

    /** A key press or something else interrupted the touchpad, so this ends cursor mode and calls off a double tap. */
    private fun endCursor() {
        stopCursor()
        main.removeCallbacks(confirmDoubleTap)
        cursorGesture.cancel(SystemClock.uptimeMillis())
        settleHeldTaps()
    }

    private fun stopCursor() {
        if (cursor.active || cursor.lifted) Trace.touch(cursorPath, true)
        main.removeCallbacks(endSlippedCursor)
        main.removeCallbacks(stepCursor)
        cursor.end()
    }

    /** Sends one arrow key press, in order with the rest of the typing. */
    private fun moveCursor(direction: Int) {
        if (direction == 0) return
        val time = SystemClock.uptimeMillis()
        val code = if (direction > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        val arrow = KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD)
        enqueue {
            // A cursor at the start or end of the text cannot move, so it stays quiet. An app that does not answer still gets the pulse.
            val connection = currentInputConnection
            val next = if (direction > 0) connection?.getTextAfterCursor(1, 0) else connection?.getTextBeforeCursor(1, 0)
            if (next?.isEmpty() != true && Haptics.enabled(this)) haptics.pulse()
            command(arrow)
        }
    }

    /** Selects the word at the cursor, or does nothing between words. */
    private fun selectWord() {
        composer.command(destructive = false, fieldLocal = true) {
            val connection = currentInputConnection ?: return@command false
            connection.finishComposingText()
            val around = connection.getSurroundingText(WORD_REACH, WORD_REACH, 0) ?: return@command false
            val text = around.text
            val cursorAt = around.selectionStart
            var start = cursorAt
            var end = around.selectionEnd
            while (start > 0 && isWordCharacter(text[start - 1])) start--
            while (end < text.length && isWordCharacter(text[end])) end++
            if (start == end) return@command true
            val offset = fieldOffset(connection, around) ?: return@command false
            connection.setSelection(offset + start, offset + end)
        }
    }

    /**
     * Where [around] starts in the field. Apps that don't supply it, such as many built with Jetpack
     * Compose, report -1; used as is, that shifts the selection a character to the left. The full text
     * then gives the cursor's place, and failing that, the app's last reported selection.
     */
    private fun fieldOffset(connection: InputConnection, around: SurroundingText): Int? {
        if (around.offset >= 0) return around.offset
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
        val cursor = if (extracted != null && extracted.startOffset >= 0 && extracted.selectionStart >= 0)
            extracted.startOffset + extracted.selectionStart else lastSelectionStart
        return if (cursor >= around.selectionStart) cursor - around.selectionStart else null
    }

    private fun isWordCharacter(character: Char) = Character.isLetterOrDigit(character) || character == '\'' || character == '’'

    /** Types a symbol or emoji from the panel, then goes back to normal typing. */
    private fun typePanelItem(item: String) {
        composer.symbol(item)
        if (modifiers.panel == Modifiers.Panel.Emoji) emoji?.use(item)?.let { recents ->
            getSharedPreferences(EMOJI_PREFERENCES, MODE_PRIVATE).edit().putString(RECENT_EMOJI, recents.joinToString(" ")).apply()
        }
        closePanel()
    }

    private fun closePanel() {
        modifiers.closePanel()
        showModifiers()
    }

    private fun quickSettingOn(item: QuickSetting) = when (item) {
        QuickSetting.Autocorrect -> TypingSettings.autocorrect(this)
        QuickSetting.SwipeTyping -> TypingSettings.swipeTyping(this)
        QuickSetting.SuggestionBar -> TypingSettings.suggestionBar(this)
        QuickSetting.AutoCapitalise -> TypingSettings.autoCapitalise(this)
        QuickSetting.AutoPunctuation -> TypingSettings.autoPunctuation(this)
        QuickSetting.Vibration -> Haptics.enabled(this)
        QuickSetting.HoldSymbols -> TypingSettings.holdSymbols(this)
        QuickSetting.CursorControl -> TypingSettings.cursorControl(this)
        QuickSetting.DoublePressLocks -> TypingSettings.doublePressLocks(this)
        QuickSetting.TouchpadOnlyInFields -> TypingSettings.touchpadOnlyInFields(this)
        QuickSetting.OpenSettings -> true
    }

    private fun writeQuickSetting(item: QuickSetting, on: Boolean) = when (item) {
        QuickSetting.Autocorrect -> TypingSettings.autocorrect(this, on)
        QuickSetting.SwipeTyping -> TypingSettings.swipeTyping(this, on)
        QuickSetting.SuggestionBar -> TypingSettings.suggestionBar(this, on)
        QuickSetting.AutoCapitalise -> TypingSettings.autoCapitalise(this, on)
        QuickSetting.AutoPunctuation -> TypingSettings.autoPunctuation(this, on)
        // The preset is saved apart from this, so turning vibration on again gives the same strength.
        QuickSetting.Vibration -> Haptics.select(this, Haptics.preset(this), on)
        QuickSetting.HoldSymbols -> TypingSettings.holdSymbols(this, on)
        QuickSetting.CursorControl -> TypingSettings.cursorControl(this, on)
        QuickSetting.DoublePressLocks -> TypingSettings.doublePressLocks(this, on)
        QuickSetting.TouchpadOnlyInFields -> TypingSettings.touchpadOnlyInFields(this, on)
        QuickSetting.OpenSettings -> Unit
    }

    /** Pushing a key on the quick settings page. The change applies at once, and then the panel goes back to normal typing. */
    private fun useQuickSetting(item: QuickSetting) {
        if (item == QuickSetting.OpenSettings) openSettings()
        else {
            writeQuickSetting(item, !quickSettingOn(item))
            readSettings()
        }
        haptics.settingChanged()
        closePanel()
    }

    /** Opens the app at its Settings page, with the same extra that its notifications use to open a page. */
    private fun openSettings() {
        val app = packageManager.getLaunchIntentForPackage(packageName) ?: return
        startActivity(app.putExtra(OPEN_PAGE_EXTRA, "/settings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private val panelOpen get() = modifiers.panel != Modifiers.Panel.Closed

    /** The Sym panel or the Ctrl overlay is on screen. */
    private val panelShown get() = panelOpen || modifiers.ctrlOverlay

    /** The touchpad types nothing while Alt is on or a panel is on screen. */
    private val altOrPanel get() = modifiers.alt != Modifiers.Level.Off || panelShown

    private fun clearSettlements() {
        inputGeneration++
        drainScheduled = false
        settlements.values.forEach(main::removeCallbacks)
        settlements.clear()
        heldTaps.clear()
        main.removeCallbacks(checkHeldTaps)
        pieces.clear()
        lastTouch = null
        val kept = pendingInput.filter { pending ->
            pending.event?.let { Modifiers.survivesDiscard(it.keyCode, it.action == KeyEvent.ACTION_DOWN) } == true
        }
        pendingInput.clear()
        contact.cancel()
        // Discarded typing must still apply Shift, Alt and Sym presses and all releases, in order.
        kept.forEach { it.action?.invoke() }
    }

    /** Move only an interpretable ready key prefix into the existing navigation barrier. */
    private fun preserveDestinationInput() {
        if (!composer.awaitingDestination || deadKeyPending) return
        repeat(MAX_PENDING_INPUT + MODIFIER_RELEASE_SLOTS) {
            val pending = pendingInput.firstOrNull() ?: return
            if (!pending.ready || pending.settlement != null) return
            val event = pending.event ?: return
            when {
                event.action == KeyEvent.ACTION_UP -> {
                    if (Modifiers.isModifier(event.keyCode)) modifiers.key(event)
                }
                Modifiers.isModifier(event.keyCode) -> modifiers.key(event)
                panelOpen || event.isCtrlPressed || event.isMetaPressed || KeyEvent.isModifierKey(event.keyCode) -> return
                event.keyCode == KeyEvent.KEYCODE_SPACE -> {
                    composer.space(event.repeatCount > 0, event.eventTime)
                    modifiers.useHeld()
                }
                else -> {
                    val typed = event.getUnicodeChar(event.metaState or modifiers.metaState)
                    if (typed <= 0 || !Character.isValidCodePoint(typed) || Character.isISOControl(typed)) return
                    composer.type(String(Character.toChars(typed)))
                    modifiers.consume()
                }
            }
            pendingInput.removeFirst()
        }
    }

    private fun makeRoom(): Boolean {
        while (pendingInput.size >= MAX_PENDING_INPUT) {
            val surplus = pendingInput.firstOrNull { it.settlement != null } ?: return false
            pendingInput.remove(surplus)
            surplus.settlement?.let {
                settlements.remove(it)?.let(main::removeCallbacks)
                contact.settle(it, it.deadline)
            }
        }
        return true
    }

    private fun enqueue(event: KeyEvent? = null, action: () -> Unit) {
        // Retain accepted keys under overload; shed a contact first, otherwise refuse only the new item.
        if (!makeRoom()) {
            if (event?.action != KeyEvent.ACTION_UP || !Modifiers.isModifier(event.keyCode)) return
            val previous = pendingInput.lastOrNull { it.event?.keyCode == event.keyCode }?.event
            val held = if (previous == null) modifiers.isHeld(event.keyCode) else previous.action == KeyEvent.ACTION_DOWN
            // Reserve one ordered release for each physical Shift/Alt key, never ahead of its down.
            if (!held || pendingInput.size >= MAX_PENDING_INPUT + MODIFIER_RELEASE_SLOTS) return
        }
        pendingInput.addLast(PendingInput(true, action, event))
        drainInput()
    }

    private fun drainInput() {
        if (drainScheduled) return
        val started = System.nanoTime()
        var actions = 0
        while (pendingInput.firstOrNull()?.ready == true) {
            if (actions++ == DRAIN_BUDGET || System.nanoTime() - started >= DRAIN_NANOS) {
                drainScheduled = true
                val generation = inputGeneration
                main.post {
                    if (generation != inputGeneration) return@post
                    drainScheduled = false
                    drainInput()
                }
                return
            }
            pendingInput.removeFirst().action?.invoke()
        }
    }

    private fun outcome(outcome: Contact.Outcome) {
        Recording.sink?.outcome(outcome)
        inputLog()?.outcome(outcome)
        when (outcome) {
            is Contact.Outcome.Swipe -> {
                showStripAfterBack()
                if (altOrPanel) {
                    if (panelOpen) swipePanel(outcome.points)
                    return
                }
                if (outcome.deletesWord) {
                    haptics.tap()
                    composer.backspace(true)
                    return
                }
                composer.swipe(letterSpace(outcome.points), outcome.startedAt, outcome.endedAt, modifiers.metaState)
                modifiers.consume()
            }
            is Contact.Outcome.Tap -> {
                if (!isTapKey(outcome.key)) return
                showStripAfterBack()
                val key = if (outcome.key == Layout.SPACE) " " else outcome.key
                haptics.tap()
                if (key == " ") {
                    composer.space(repeated = false, time = outcome.endedAt)
                } else {
                    val word = if ((key == "I" && TypingSettings.autoCapitalise(this)) || modifiers.shift != Modifiers.Level.Off) key else key.lowercase()
                    composer.type(word, letterTap = true)
                }
                modifiers.consume()
            }
        }
        showModifiers()
    }

    /** Measures a swipe in keys across and rows down, and moves the panel if it went far enough. */
    private fun swipePanel(points: List<Sample>) {
        val across = (points.last().x - points.first().x) * 10
        val down = (points.last().y - points.first().y) * 4
        if (maxOf(abs(across), abs(down)) >= Contact.PAGE_KEYS && panel?.swipe(across, down) == true) haptics.pageChanged()
    }

    private fun isTapKey(key: String): Boolean {
        if (altOrPanel) return false
        return if (key == Layout.SPACE) ' ' in tapKeys else Layout.isLetter(key) && key[0] in tapKeys
    }

    /** Only a plain letter from the phone's own keyboard can become its symbol. With Shift, Alt, Sym or a shortcut key it stays a letter. */
    private fun canHold(event: KeyEvent): Boolean {
        if (!holdSymbols || !field.words || !HoldEngine.isLetter(event.keyCode) || deadKeyPending) return false
        if (!HoldEngine.arrivedInTime(event.downTime, SystemClock.uptimeMillis())) return false
        if (modifiers.shift != Modifiers.Level.Off || altOrPanel || !event.hasNoModifiers() || event.isCapsLockOn) return false
        // A modifier still in the queue has not changed the levels above yet.
        if (pendingInput.any { it.event?.keyCode?.let(Modifiers::isModifier) == true }) return false
        val device = event.device ?: return false
        if (device.isVirtual || device.isExternal || !event.isFromSource(InputDevice.SOURCE_KEYBOARD)) return false
        val symbol = event.getUnicodeChar(KeyEvent.META_ALT_ON)
        return symbol > 0 && !Character.isISOControl(symbol)
    }

    /** Starts the hold timer for a letter that can become its symbol. True if this is a repeat of such a key, which types nothing. */
    private fun trackHold(event: KeyEvent): Boolean {
        val earlier = holdEngine.pending()
        val consumed = holdEngine.down(event.keyCode, event.deviceId, event.downTime, event.repeatCount > 0, canHold(event))
        val press = holdEngine.pending()
        if (press != null && press !== earlier) {
            heldLetter = KeyEvent(event)
            // Typing the letter before this one can take a long time, so this reads the clock after that.
            main.postAtTime({ holdTimer(press) }, holdEngine.timerAt(press, SystemClock.uptimeMillis()))
        }
        return consumed
    }

    /** The app sends each key's release to the keyboard, so the timer first asks if the app is free to do that. */
    private fun holdTimer(press: HoldEngine.Press) {
        if (holdEngine.pending() !== press) return
        val asked = SystemClock.uptimeMillis()
        currentInputConnection?.getTextBeforeCursor(0, 0)
        holdEngine.holdDue(press.id, SystemClock.uptimeMillis() - asked)
    }

    /**
     * The key stayed down, so its letter becomes what the key types with Alt on: its printed symbol. The press's own flags
     * decide the symbol, so a modifier pushed since then does not change it. It stays in order with other input.
     */
    private fun replaceHeldLetter() {
        val letter = heldLetter ?: return
        heldLetter = null
        if (!editing) return
        val symbol = letter.getUnicodeChar(letter.metaState or KeyEvent.META_ALT_ON)
        if (symbol <= 0 || !Character.isValidCodePoint(symbol) || Character.isISOControl(symbol)) return
        val text = String(Character.toChars(symbol))
        if (pendingInput.isNotEmpty()) enqueue { composer.replaceLetter(text) } else composer.replaceLetter(text)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        Recording.sink?.key(event, touchpad.isGestureKey(event))
        inputLog()?.key(event, touchpad.isGestureKey(event))
        if (KeyCapture.waiting?.invoke(event) == true) return true
        // Android hands back to a shown keyboard first, which would only close the strip.
        if (keyCode == KeyEvent.KEYCODE_BACK && editing && isInputViewShown) return true
        if (!editing || keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        typedInField = true
        if (touchpad.isGestureKey(event)) return true
        // Keys like volume reach the keyboard too, but they are not typing.
        if (!event.isSystem) showStripAfterBack()
        // The touchpad hears the physical press now, before the key is typed.
        contact.key(keyCode, true, event.eventTime)
        val carried = carryCtrl(event)
        val repeatOfHold = trackHold(carried)
        // A key press means the finger was pressing, not resting on the touchpad.
        endCursor()
        if (repeatOfHold) return true
        if (ctrlKey(event)) {
            consumedKeys += keyCode
            return true
        }
        if (event.repeatCount == 0) modifiers.keyWithCtrl(event.eventTime)
        if (pendingInput.isNotEmpty()) {
            val captured = KeyEvent(carried)
            consumedKeys += keyCode
            enqueue(captured) {
                key(captured)
                showModifiers()
            }
            return true
        }
        val handled = key(carried)
        if (handled) consumedKeys += keyCode
        showModifiers()
        return handled || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        Recording.sink?.key(event, touchpad.isGestureKey(event))
        inputLog()?.key(event, touchpad.isGestureKey(event))
        if (KeyCapture.waiting?.invoke(event) == true) return true
        if (keyCode == KeyEvent.KEYCODE_BACK && editing && isInputViewShown) {
            // Back closes an open panel and stays in the app.
            if (panelOpen) {
                closePanel()
                return true
            }
            passingBack = true
            requestHideSelf(0)
            return true
        }
        if (!editing || keyCode == KeyEvent.KEYCODE_BACK) {
            val heldRelease = holdEngine.up(keyCode, event.deviceId, event.eventTime)
            return consumedKeys.remove(keyCode) || heldRelease || super.onKeyUp(keyCode, event)
        }
        if (touchpad.isGestureKey(event)) return true
        contact.key(keyCode, false, event.eventTime)
        if (holdEngine.up(keyCode, event.deviceId, event.eventTime)) {
            consumedKeys.remove(keyCode)
            return true
        }
        if (ctrlKey(event)) return consumedKeys.remove(keyCode) || super.onKeyUp(keyCode, event)
        if (pendingInput.isNotEmpty()) {
            val captured = KeyEvent(event)
            consumedKeys.remove(keyCode)
            enqueue(captured) {
                if (Modifiers.isModifier(keyCode)) modifiers.key(captured)
                showModifiers()
            }
            return true
        }
        if (Modifiers.isModifier(keyCode)) {
            modifiers.key(event)
            showModifiers()
        }
        return consumedKeys.remove(keyCode) || super.onKeyUp(keyCode, event)
    }

    /**
     * Follows a key that counts as Ctrl, and returns false for any other key. Keys carry Ctrl from the moment they arrive,
     * so Ctrl does not wait in the input queue.
     */
    private fun ctrlKey(event: KeyEvent): Boolean {
        if (!modifiers.isCtrl(event.keyCode)) return false
        val shown = modifiers.ctrlOverlay
        modifiers.key(event)
        // A finger that rested on the overlay must not type when it lifts.
        if (shown && !modifiers.ctrlOverlay) contact.cancel()
        showModifiers()
        return true
    }

    /** A key pushed while Ctrl is held carries Ctrl, also when that Ctrl is fn, which the phone reports as its own modifier. */
    private fun carryCtrl(event: KeyEvent): KeyEvent {
        if (!modifiers.ctrlHeld || event.isCtrlPressed && !event.isFunctionPressed) return event
        return KeyEvent(event.downTime, event.eventTime, event.action, event.keyCode, event.repeatCount,
            Modifiers.withCtrl(event.metaState), event.deviceId, event.scanCode, event.flags, event.source)
    }

    /** Captures each app-handled key as a paired command in the composer's ordered input queue. */
    private fun key(event: KeyEvent): Boolean {
        if (Modifiers.isModifier(event.keyCode)) {
            modifiers.key(event)
            if (panelOpen && !isInputViewShown) main.post(showPanel)
            return true
        }
        if (panelKey(event)) return true
        // Enter comes before the Ctrl shortcuts, because the Enter key setting can use Ctrl + Enter.
        if (!event.isMetaPressed && enter(event)) return true
        // A lone Ctrl or Meta press is not a shortcut. Each shortcut key carries its own meta state.
        if (KeyEvent.isModifierKey(event.keyCode)) {
            modifiers.useHeld()
            return true
        }
        if (event.isCtrlPressed && event.keyCode != KeyEvent.KEYCODE_DEL || event.isMetaPressed) {
            modifiers.useHeld()
            command(event)
            return true
        }
        if (deadKeyPending && (event.keyCode == KeyEvent.KEYCODE_SPACE || event.keyCode == KeyEvent.KEYCODE_DEL)) {
            command(event, textKey = true)
            if (event.keyCode == KeyEvent.KEYCODE_SPACE) modifiers.consume() else modifiers.useHeld()
            deadKeyPending = false
            return true
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                // Ctrl or Alt with Backspace deletes the previous word.
                val alt = modifiers.alt != Modifiers.Level.Off
                composer.backspace(event.isCtrlPressed || alt)
                if (alt) modifiers.consume() else modifiers.useHeld()
            }
            KeyEvent.KEYCODE_SPACE -> { composer.space(event.repeatCount > 0, event.eventTime); modifiers.useHeld() }
            KeyEvent.KEYCODE_TAB -> { modifiers.useHeld(); command(event) }
            else -> {
                val typed = event.getUnicodeChar(event.metaState or modifiers.metaState)
                val dead = typed and KeyCharacterMap.COMBINING_ACCENT != 0
                if (dead || deadKeyPending || typed <= 0 || !Character.isValidCodePoint(typed) || Character.isISOControl(typed)) {
                    val textKey = dead || typed > 0 && Character.isValidCodePoint(typed) && !Character.isISOControl(typed)
                    command(event, textKey = textKey)
                    if (textKey) modifiers.consume() else modifiers.useHeld()
                    if (dead || typed > 0) deadKeyPending = dead
                    return true
                }
                val text = String(Character.toChars(typed))
                modifiers.consume()
                // Controlled insertion avoids unacknowledged app key updates overtaking later input.
                composer.type(text)
            }
        }
        return true
    }

    /** With the panel on screen, a letter key types the item that it shows. A blank key types nothing. Other keys stay as they are. */
    private fun panelKey(event: KeyEvent): Boolean {
        if (event.repeatCount > 0 && event.keyCode == pickedKey) return true
        pickedKey = 0
        if (!panelOpen || !isInputViewShown || !HoldEngine.isLetter(event.keyCode) || event.isCtrlPressed || event.isMetaPressed) return false
        if (event.repeatCount > 0) return true
        val letter = ('A' + (event.keyCode - KeyEvent.KEYCODE_A)).toString()
        if (modifiers.panel == Modifiers.Panel.Settings) QuickSetting.on(letter)?.let { item ->
            pickedKey = event.keyCode
            useQuickSetting(item)
        } else panel?.item(letter)?.let { item ->
            pickedKey = event.keyCode
            typePanelItem(item)
        }
        return true
    }

    /** False for any other key, and for a Ctrl + Enter that stays a shortcut for the app. */
    private fun enter(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_ENTER && event.keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) return false
        val info = currentInputEditorInfo
        // A locked Shift is Caps Lock, so Enter still sends.
        val shift = event.isShiftPressed || modifiers.shift == Modifiers.Level.Once
        val press = EnterKey.of(TypingSettings.enterKey(this)).press(info?.inputType ?: 0, info?.imeOptions ?: 0,
            info?.actionId ?: 0, info?.packageName, shift, event.isCtrlPressed, enterApps)
        if (press == EnterKey.Press.FieldDecides && event.isCtrlPressed) return false
        // A held Enter must not send again before the app has cleared the field.
        val sends = press == EnterKey.Press.Send || press == EnterKey.Press.ShiftSend
        if (!sends || event.repeatCount == 0) composer.enter(press)
        if (press == EnterKey.Press.ShiftNewLine || press == EnterKey.Press.ShiftSend) modifiers.consume() else modifiers.useHeld()
        return true
    }

    private fun command(event: KeyEvent, textKey: Boolean = false) {
        val meta = KeyEvent.normalizeMetaState(event.metaState or modifiers.metaState)
        val down = KeyEvent(event.downTime, event.eventTime, KeyEvent.ACTION_DOWN, event.keyCode,
            event.repeatCount, meta, event.deviceId, event.scanCode, event.flags, event.source)
        val up = KeyEvent(event.downTime, event.eventTime, KeyEvent.ACTION_UP, event.keyCode,
            0, meta, event.deviceId, event.scanCode, event.flags, event.source)
        val navigation = event.keyCode in NAVIGATION_KEYS
        val shortcut = event.isCtrlPressed && !event.isMetaPressed && event.keyCode in FIELD_SHORTCUTS
        val destructive = textKey || event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL
            || event.keyCode in DESTRUCTIVE_SHORTCUTS || !navigation && !shortcut
        composer.command(destructive, textKey || navigation || shortcut || event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
            val connection = currentInputConnection
            if (connection == null) false else try {
                var released = false
                val sent = try {
                    connection.sendKeyEvent(down)
                } finally {
                    // Always release a dispatched key, including when the down result is ambiguous.
                    released = connection.sendKeyEvent(up)
                }
                sent && released
            } catch (error: RuntimeException) {
                Log.w("Atlas", "Could not forward an ordered key", error)
                false
            }
        }
    }

    private fun showModifiers() {
        main.removeCallbacks(showCtrlOverlay)
        modifiers.ctrlOverlayAt?.let { main.postAtTime(showCtrlOverlay, it) }
        strip?.visibility = if (panelShown) View.GONE else View.VISIBLE
        panel?.mode = modifiers.panel
        panel?.ctrl = modifiers.ctrlOverlay
        contact.paging = panelShown
    }

    override fun onDestroy() {
        editing = false
        main.removeCallbacksAndMessages(null)
        settlements.clear()
        pendingInput.clear()
        heldLetter = null
        contact.releaseKeys()
        composer.stop()
        autocorrect.release()
        editor.cancelState()
        TypingSettings.stopListening(this, overlaySetting)
        overlay.hide()
        rawLog?.finish()
        rawLog = null
        composer.log = null
        super.onDestroy()
    }

    companion object {
        /** The app opens the page in this extra when it starts. tools/checks/about.sh uses it to open About. */
        private const val OPEN_PAGE_EXTRA = "com.vandam.ink.notification.HREF"
        private const val MAX_PENDING_INPUT = 128
        private const val EMOJI_PREFERENCES = "emoji"
        private const val RECENT_EMOJI = "recent"
        /** Delayed hides from the previous app arrived within 25 ms of the new field starting. */
        private const val STALE_HIDE_MILLIS = 500L
        /** The field of a returning window started within 30 ms of its empty start. */
        private const val NO_FIELD_HIDE_MILLIS = 200L
        private const val FRAME_MILLIS = 16L
        /** Characters read on each side of the cursor to find the word it is in. */
        private const val WORD_REACH = 64
        /** One for each physical Shift, Alt and Sym key. */
        private const val MODIFIER_RELEASE_SLOTS = 5
        private const val DRAIN_BUDGET = 8
        private const val DRAIN_NANOS = 4_000_000L
        private val NAVIGATION_KEYS = setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MOVE_HOME,
            KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN)
        private val FIELD_LEAVING_KEYS = setOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_TAB)
        private val FIELD_SHORTCUTS = Modifiers.CTRL_ACTIONS.keys.map { KeyEvent.KEYCODE_A + (it[0] - 'A') }.toSet()
        private val DESTRUCTIVE_SHORTCUTS = setOf(KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_Y)
    }
}
