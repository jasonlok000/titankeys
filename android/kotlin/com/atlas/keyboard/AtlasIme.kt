package com.atlas.keyboard

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.hardware.display.DisplayManager
import android.inputmethodservice.InputMethodService
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
import com.atlas.engine.Calibration
import com.atlas.engine.EmojiPages
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
    private lateinit var editor: ConnectionEditor
    private lateinit var overlay: TouchpadOverlay
    private val contact = Contact()
    private val cursor = CursorMode()
    /** The finger's path through cursor mode, drawn on the home screen's keyboard like a swipe. */
    private var cursorPath = ArrayList<Sample>()
    private var cursorControl = true
    /** When cursor mode last ended; a press on Space just after selects the word at the cursor. */
    private var cursorEndedAt = Long.MIN_VALUE
    /** Cursor mode has moved the cursor, so a push on Space selects instead of typing a space. */
    private var cursorMoved = false
    /** Space was pressed to select a word, so its repeats and release type nothing. */
    private var selectingWithSpace = false
    /** The selection start the app last reported, for apps that don't say where their text sits. */
    private var lastSelectionStart = -1
    private val modifiers = Modifiers()
    private var strip: Strip? = null
    private var emoji: EmojiPages? = null
    private var field = Field.None
    private var editing = false
    private var typedInField = false
    private var passingBack = false
    private var inLauncher = false
    private val consumedKeys = HashSet<Int>()
    private var deadKeyPending = false
    private var tapKeys = ""
    private class PendingInput(var ready: Boolean, var action: (() -> Unit)?, val event: KeyEvent? = null,
                               val settlement: Contact.Settlement? = null)
    private val pendingInput = ArrayDeque<PendingInput>()
    private var inputGeneration = 0
    private var drainScheduled = false
    private val settlements = LinkedHashMap<Contact.Settlement, Runnable>()
    private val pieces = SwipePieces({ composer.liftOffMillis.toLong() }, { composer.canContinueSwipe }, ::isTapKey)
    private var lastTouch: List<Sample>? = null
    private val showStrip = Runnable {
        if (!editing || currentInputBinding == null) return@Runnable
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

    // Apps on Android's newer back handling ignore a back key from a keyboard, so a system back goes first.
    private val sendBack = Runnable { if (!BackService.back() && editing) sendDownUpKeyEvents(KeyEvent.KEYCODE_BACK) }

    private val startCursor = Runnable {
        if (!cursor.rest(SystemClock.uptimeMillis())) return@Runnable
        cursorMoved = false
        // The contact now steers the cursor, so it must not also type when it lifts.
        contact.cancelActive()
        Trace.touch(cursorPath, false)
        haptics.click()
        stepCursor.reset()
        stepCursor.run()
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
            { delay, action -> main.postDelayed(action, delay) })
        composer = Composer(editor, EngineWords(SwipeTyping.get(this)),
            { delay, action -> main.postDelayed(action, delay) }, ::clearSettlements,
            { TypingSettings.autoCapitalise(this) }) { strip?.words = it }
        composer.onStroke = Trace::stroke
        // The firmware hands touchpad events to the topmost window titled "InputMethod", which is this
        // keyboard's window. They arrive at the window, not at any view, so catch them there.
        val window = window.window!!
        window.callback = TouchpadWindowCallback(window.callback) { event ->
            (editing && touchpad.accepts(event)).also { if (it) motion(event) }
        }
        overlay = TouchpadOverlay(this) { event ->
            (editing && touchpad.accepts(event)).also { if (it) motion(event) }
        }
        loadEmoji()
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
            main.post { emoji = EmojiPages(groups, recents).also { strip?.emoji = it } }
        }.start()
    }

    override fun onCreateInputView(): View = Strip(this, { index -> haptics.tap(); enqueue { composer.choose(index) } },
        ::symbol, ::pickEmoji).also {
        strip = it
        it.emoji = emoji
        it.showSuggestions = showSuggestions()
        showModifiers()
    }

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        preserveDestinationInput()
        editor.cancelState()
        super.onStartInput(info, restarting)
        editing = info.inputType != EditorInfo.TYPE_NULL
        if (!restarting) typedInField = false
        field = if (editing) Field.of(info.inputType, info.imeOptions) else Field.None
        inLauncher = editing && info.packageName == launcherPackage()
        contact.releaseKeys()
        clearSettlements()
        modifiers.clear()
        deadKeyPending = false
        showModifiers()
        composer.liftOffMillis = TypingSettings.liftOffGraceMillis(this)
        tapKeys = TypingSettings.tapKeys(this)
        cursorControl = TypingSettings.cursorControl(this)
        strip?.showSuggestions = showSuggestions()
        main.removeCallbacks(showStrip)
        if (editing) {
            composer.start(field, info.initialSelStart, info.initialSelEnd)
            main.post(showStrip)
            overlay.show()
        } else {
            composer.stop()
            overlay.hide()
            try {
                hideWindow()
                requestHideSelf(0)
            } catch (error: RuntimeException) {
                Log.w("Atlas", "Could not hide the suggestion strip", error)
            }
        }
    }

    // Launchers like Niagara keep a search field focused on the home screen, where the bar only gets in the way.
    private fun showSuggestions() = TypingSettings.suggestionBar(this) && !inLauncher

    private fun launcherPackage(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Back belongs to the app; the strip comes and goes with the text field instead.
        backDisposition = BACK_DISPOSITION_WILL_NOT_DISMISS
    }

    override fun onWindowShown() {
        super.onWindowShown()
        // Android draws its hide and switch-keyboard buttons in this bar; the strip has no room for them.
        window.window?.insetsController?.hide(WindowInsets.Type.captionBar())
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        // A delayed hide from the previous app can arrive after this field has started. Once you have
        // typed, a hide is the app's own, such as a Done key closing a field it then removes.
        main.removeCallbacks(showStrip)
        if (passingBack) {
            // Now that the strip is gone, the app's own back can run. If its field stays, so does the strip.
            passingBack = false
            main.post(sendBack)
            if (editing) main.postDelayed(showStrip, BACK_SETTLE_MILLIS)
        } else if (editing && !typedInField) {
            main.post(showStrip)
        }
    }

    override fun onUnbindInput() {
        main.removeCallbacks(showStrip)
        main.removeCallbacks(sendBack)
        super.onUnbindInput()
    }

    override fun onFinishInput() {
        preserveDestinationInput()
        // The old connection is still owned here; only a verified final explicit choice is learned.
        composer.stop(terminal = true)
        editor.cancelState()
        editing = false
        field = Field.None
        main.removeCallbacks(showStrip)
        clearSettlements()
        contact.cancel()
        endCursor()
        selectingWithSpace = false
        deadKeyPending = false
        overlay.hide()
        super.onFinishInput()
    }

    override fun onEvaluateInputViewShown() = editing

    override fun onShowInputRequested(flags: Int, configChange: Boolean) = editing

    override fun onEvaluateFullscreenMode() = false

    override fun onUpdateSelection(oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int, composingStart: Int, composingEnd: Int) {
        super.onUpdateSelection(oldStart, oldEnd, newStart, newEnd, composingStart, composingEnd)
        lastSelectionStart = newStart
        if (editing && composer.selectionChanged(newStart, newEnd)) clearSettlements()
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
        if (!field.words) return
        typedInField = true
        val samples = touchpad.samples(event)
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
                endCursor()
                // Alt and Sym turn swipes into strip pages, so the cursor waits until they are off.
                // A finger the contact ignored landed while typing, so it must not start the cursor.
                if (cursorControl && !stripPaging && contact.travel != null && cursor.down(first)) {
                    cursor.move(samples.drop(1))
                    cursorPath = ArrayList(samples.filter { it.valid })
                    main.postAtTime(startCursor, first.time + CursorMode.REST_MILLIS)
                }
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
                val travel = contact.travel
                val first = contact.points.firstOrNull()
                // Wait until a configured tap key's contact is a swipe, so short taps pulse only on acceptance.
                if (!stripPaging && travel != null && first != null && (travel >= Contact.SWIPE_KEYS
                    || !isTapKey(Layout.keyAt(first.x, first.y)))) haptics.move(samples)
                Trace.touch(contact.points, false)
            }
            MotionEvent.ACTION_UP -> {
                if (cursor.active) {
                    cursorEndedAt = SystemClock.uptimeMillis()
                    main.removeCallbacks(stepCursor)
                    cursor.lift()
                    val slips = composer.liftOffMillis > 0
                    // Through a slip the path stays open, so a finger that comes back carries on drawing it.
                    traceCursor(samples, ended = !slips)
                    if (slips) main.postDelayed(endSlippedCursor, composer.liftOffMillis.toLong()) else endCursor()
                    return
                }
                endCursor()
                val points = contact.points
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
                        val result = if (pending.cancelled || stripPaging) settled else pieces.settle(pending, settled)
                        input.action = { result?.let(::outcome) }
                        input.ready = true
                        drainInput()
                    }
                    settlements[pending] = settle
                    main.postAtTime(settle, pending.deadline)
                }
                Trace.touch(if (points.size <= Swipe.MAX_POINTS && samples.all { it.valid }) points else emptyList(), true)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                contact.cancelActive()
                endCursor()
                Trace.touch(emptyList(), true)
            }
        }
    }

    private val endSlippedCursor = Runnable { endCursor() }

    private fun traceCursor(samples: List<Sample>, ended: Boolean) {
        val added = samples.filter { it.valid }
        // The preview draws at most a swipe's worth of points; a long session starts a fresh path.
        if (cursorPath.size + added.size > Swipe.MAX_POINTS) cursorPath = ArrayList(listOfNotNull(cursorPath.lastOrNull()))
        cursorPath.addAll(added)
        Trace.touch(cursorPath, ended)
    }

    private fun endCursor() {
        if (cursor.active || cursor.lifted) Trace.touch(cursorPath, true)
        main.removeCallbacks(endSlippedCursor)
        main.removeCallbacks(startCursor)
        main.removeCallbacks(stepCursor)
        cursor.end()
    }

    /** Sends one arrow key press, in order with the rest of the typing. */
    private fun moveCursor(direction: Int) {
        if (direction == 0) return
        cursorMoved = true
        val time = SystemClock.uptimeMillis()
        if (Haptics.enabled(this)) haptics.pulse()
        val code = if (direction > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        val arrow = KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD)
        enqueue { command(arrow) }
    }

    /**
     * Pressing Space presses the finger into the touchpad, which lifts it, so either may arrive first.
     * A thumb that only rested on Space is about to type a space, so the cursor must have moved first.
     */
    private fun selectsWord(event: KeyEvent) = event.keyCode == KeyEvent.KEYCODE_SPACE && event.repeatCount == 0
        && cursorMoved && (cursor.active || cursor.lifted || event.eventTime - cursorEndedAt in 0..SELECT_AFTER_CURSOR_MILLIS)

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

    private fun symbol(symbol: String) {
        enqueue {
            composer.symbol(symbol)
            modifiers.consume()
            showModifiers()
        }
    }

    private fun pickEmoji(picked: String) {
        enqueue {
            composer.symbol(picked)
            emoji?.use(picked)?.let { recents ->
                getSharedPreferences(EMOJI_PREFERENCES, MODE_PRIVATE).edit().putString(RECENT_EMOJI, recents.joinToString(" ")).apply()
            }
            modifiers.consume()
            showModifiers()
        }
    }

    private val stripPaging get() = modifiers.alt != Modifiers.Level.Off || modifiers.sym != Modifiers.Level.Off

    private fun clearSettlements() {
        inputGeneration++
        drainScheduled = false
        settlements.values.forEach(main::removeCallbacks)
        settlements.clear()
        pieces.clear()
        lastTouch = null
        val releases =pendingInput.filter { it.event?.action == KeyEvent.ACTION_UP }
        pendingInput.clear()
        contact.cancel()
        // Discarded typing must still release physically held modifiers.
        releases.forEach { it.action?.invoke() }
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
                event.isCtrlPressed || event.isMetaPressed || KeyEvent.isModifierKey(event.keyCode) -> return
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
        when (outcome) {
            is Contact.Outcome.Swipe -> {
                if (stripPaging) {
                    // Sideways swipes page through the symbols or emoji on the strip. The pages follow the finger.
                    val dx = (outcome.points.last().x - outcome.points.first().x) * 10
                    val dy = (outcome.points.last().y - outcome.points.first().y) * 4
                    if (modifiers.sym != Modifiers.Level.Off && abs(dy) > abs(dx) && abs(dy) >= Contact.PAGE_KEYS) {
                        // Up and down swipes, measured in key rows, jump between emoji groups.
                        strip?.jump(if (dy < 0) 1 else -1)
                        haptics.pulse(HapticPreset.Strong)
                    } else if (abs(dx) >= Contact.PAGE_KEYS) {
                        strip?.turn(if (dx > 0) -1 else 1)
                        haptics.pulse(HapticPreset.Strong)
                    }
                    return
                }
                composer.swipe(letterSpace(outcome.points), outcome.startedAt, outcome.endedAt, modifiers.metaState)
                modifiers.consume()
            }
            is Contact.Outcome.Tap -> {
                if (!isTapKey(outcome.key)) return
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

    private fun isTapKey(key: String): Boolean {
        if (stripPaging) return false
        return if (key == Layout.SPACE) ' ' in tapKeys else Layout.isLetter(key) && key[0] in tapKeys
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (KeyCapture.waiting?.invoke(event) == true) return true
        // Android hands back to a shown keyboard first, which would only close the strip.
        if (keyCode == KeyEvent.KEYCODE_BACK && editing && isInputViewShown) return true
        if (!editing || keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        typedInField = true
        if (touchpad.isGestureKey(event)) return true
        contact.key(keyCode, true, event.eventTime)
        if (keyCode == KeyEvent.KEYCODE_SPACE && selectingWithSpace) return true
        if (selectsWord(event)) {
            endCursor()
            cursorEndedAt = Long.MIN_VALUE
            selectingWithSpace = true
            consumedKeys += keyCode
            enqueue { selectWord() }
            return true
        }
        // Any other key press means the finger was pressing, not resting on the touchpad.
        endCursor()
        if (pendingInput.isNotEmpty()) {
            val captured = KeyEvent(event)
            consumedKeys += keyCode
            enqueue(captured) {
                key(captured)
                showModifiers()
            }
            return true
        }
        val handled = key(event)
        if (handled) consumedKeys += keyCode
        showModifiers()
        return handled || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (KeyCapture.waiting?.invoke(event) == true) return true
        if (keyCode == KeyEvent.KEYCODE_BACK && editing && isInputViewShown) {
            passingBack = true
            requestHideSelf(0)
            return true
        }
        if (!editing || keyCode == KeyEvent.KEYCODE_BACK) {
            return consumedKeys.remove(keyCode) || super.onKeyUp(keyCode, event)
        }
        if (touchpad.isGestureKey(event)) return true
        contact.key(keyCode, false, event.eventTime)
        if (keyCode == KeyEvent.KEYCODE_SPACE && selectingWithSpace) {
            selectingWithSpace = false
            consumedKeys.remove(keyCode)
            return true
        }
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

    /** Captures each app-handled key as a paired command in the composer's ordered input queue. */
    private fun key(event: KeyEvent): Boolean {
        if (Modifiers.isModifier(event.keyCode)) {
            modifiers.key(event)
            return true
        }
        if (event.isCtrlPressed && event.keyCode != KeyEvent.KEYCODE_DEL || event.isMetaPressed) {
            modifiers.useHeld()
            command(event)
            return true
        }
        if (KeyEvent.isModifierKey(event.keyCode)) {
            modifiers.useHeld()
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
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { composer.enter(); modifiers.useHeld() }
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
        strip?.showSymbols = modifiers.alt != Modifiers.Level.Off
        strip?.showEmoji = modifiers.sym != Modifiers.Level.Off
        contact.paging = stripPaging
    }

    override fun onDestroy() {
        editing = false
        main.removeCallbacksAndMessages(null)
        settlements.clear()
        pendingInput.clear()
        contact.releaseKeys()
        composer.stop()
        editor.cancelState()
        overlay.hide()
        super.onDestroy()
    }

    companion object {
        private const val MAX_PENDING_INPUT = 128
        private const val EMOJI_PREFERENCES = "emoji"
        private const val RECENT_EMOJI = "recent"
        private const val BACK_SETTLE_MILLIS = 300L
        private const val SELECT_AFTER_CURSOR_MILLIS = 250L
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
        private val FIELD_SHORTCUTS = setOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_Y)
        private val DESTRUCTIVE_SHORTCUTS = setOf(KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_Y)
    }
}
