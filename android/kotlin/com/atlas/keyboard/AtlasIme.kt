package com.atlas.keyboard

import android.content.BroadcastReceiver
import android.animation.ValueAnimator
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Outline
import android.graphics.Paint
import android.hardware.display.DisplayManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.AtomicFile
import android.util.Log
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.KeyCharacterMap
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.SurroundingText
import android.widget.LinearLayout
import com.atlas.connected.Connected
import com.atlas.connected.TouchSizes
import com.atlas.engine.Calibration
import com.atlas.engine.EmojiPages
import com.atlas.engine.ForgetWords
import com.atlas.engine.HoldEngine
import com.atlas.engine.Swipe
import com.atlas.engine.SwipeTyping
import com.atlas.engine.TypingSettings
import java.io.IOException
import kotlin.math.abs
import kotlin.random.Random

/**
 * Atlas: swipe typing on the Titan 2 keyboard's touchpad, with a suggestion strip for the physical
 * keyboard. The keyboard window stays open while editing so the firmware can route touchpad events.
 */
class AtlasIme : InputMethodService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var touchpad: Touchpad
    private lateinit var haptics: Haptics
    /** The cursor plays gone once each time it reaches an end of the text. */
    private val cursorSteps = CursorSteps()
    private lateinit var composer: Composer
    private lateinit var autocorrect: AutocorrectLoader
    private lateinit var phoneNames: PhoneNameSource
    private lateinit var editor: ConnectionEditor
    private lateinit var overlay: TouchpadOverlay
    private val contact = Contact()
    private val deleteHold = DeleteHold(contact)
    private val cursor = CursorMode()
    private val cursorGesture = CursorGesture()
    /** The finger's path through cursor mode, drawn on the home screen's keyboard like a swipe. */
    private var cursorPath = ArrayList<Sample>()
    /** The touch of a Space gesture registered before any slide, so the strip shows it and its feel has played. */
    private var gestureRegistered = false
    /** Where that touch landed, for the strip's track. */
    private var gestureLanded: Sample? = null
    /** The cursor has moved far from where its text was read, so the next step reads it again. */
    private var rereadCursorText = false
    private var cursorControl = true
    /** Taps on a tap key that may be part of a double tap. Each call gives its input, typed if the argument is true. */
    private val heldTaps = ArrayList<(Boolean) -> Unit>()
    /** The word that the word drag started from, as its start and end in the field. */
    private var wordAnchor: Pair<Int, Int>? = null
    /** The selection start the app last reported, for apps that don't say where their text sits. */
    private var lastSelectionStart = -1
    private val modifiers = Modifiers()
    private var strip: Strip? = null
    /** The words the composer last put on the bar, for a strip that is made after that. */
    private var barWords = emptyList<String>()
    private var panel: SymPanel? = null
    private var emoji: EmojiPages? = null
    /** The key that last picked from the panel or turned its page, so its repeats type nothing once the panel has closed. */
    private var pickedKey = 0
    /** The current touch landed on the bottom row with categories on the screen, so it picks a category and is nothing else. */
    private var pickingCategory = false
    private var categoryLandedAt = 0f
    /** Where the finger on the bottom row is now, from 0 to 1 across. */
    private var categoryAt = 0f
    private var categoryMoved = false
    private val panelHover = SteadyKey { x, y ->
        if (modifiers.panel == Modifiers.Panel.Settings) Layout.restingKeyAt(x, y) else Layout.keyAt(x, y)
    }
    private val barHover = SteadyKey(Layout::keyAt)
    private var field = Field.None
    /** The user chose to hide the bar in this app. The window stays, one pixel high. See [showSuggestions]. */
    private var barHidden = false
    /** Each emoji by its shortcode name, once the list has loaded. */
    private var shortcodes: Map<String, String> = emptyMap()
    /** The apps that the user added to the Enter key setting, read when a field starts. */
    private var enterApps = emptyMap<String, EnterKey.SendMethod>()
    /** The raw input log for this field. It is null when the setting is off or the field must not be recorded. */
    private var rawLog: RawLog.Session? = null
    private var editing = false
    private var typedInField = false
    private var fieldStartedAt = 0L
    private var passingBack = false
    private val backPush = BackPush()
    /** Back hid the strip, so it stays hidden in this field until you tap the field or type. */
    private var hiddenByBack = false
    private var inLauncher = false
    private var launcherFieldHasText = false
    private val consumedKeys = HashSet<Int>()
    private var deadKeyPending = false
    private val hold = TouchpadHold()
    /** A process that died with the status on left the firmware's status as it was, so the first field start clears it. */
    private var statusReported = false
    private var tapKeys = ""
    private var holdSymbols = false
    private val holdEngine = HoldEngine(HoldEngine.Output { replaceHeldLetter() })
    /** The press that can still become its symbol. Its letter is already typed. */
    private var heldLetter: KeyEvent? = null
    /** In this app the Back service hears the keys first, because the app takes key presses before the keyboard. */
    private var watchingKeys = false
    private val panelKeys = PanelKeyFilter()
    /** The key under a finger on the touchpad, for the modifier bar, or an empty string. */
    private var restingKey = ""
    private class PendingInput(var ready: Boolean, var action: (() -> Unit)?, val event: KeyEvent? = null,
                               val settlement: Contact.Settlement? = null)
    private val pendingInput = ArrayDeque<PendingInput>()
    private var inputGeneration = 0
    private var drainScheduled = false
    private val settlements = LinkedHashMap<Contact.Settlement, Runnable>()
    private val pieces = SwipePieces({ composer.liftOffMillis.toLong() }, { composer.canContinueSwipe }, ::isTapKey)
    private var lastTouch: List<Sample>? = null
    /** When the window last hid, to tell a hide that a show follows at once. */
    private var windowHiddenAt = Long.MIN_VALUE / 2
    /** The keyboard hid itself for [recycle], so the show that follows is its own. */
    private var recycling = false
    /** [recycle] asked for its hide, and the show waits until the window has gone. */
    private var recycleAwaitsHide = false
    /** The strip and the Sym panel, which a [nudge] makes one pixel taller for a moment. */
    private var inputRoot: View? = null

    /**
     * A tap on the message box in Microsoft Teams, while the strip shows, makes Teams hide the keyboard and show it again within a
     * second. Android then has the strip up, but Teams leaves its box under the strip and keeps asking for a show that does
     * nothing. Other apps may do the same, so after such a quick hide and show in any app, the strip grows one pixel and goes back
     * once things settle. The change of height plays a new animation in the app, which no one can see.
     */
    private val nudge = Runnable {
        val root = inputRoot ?: return@Runnable
        if (!isInputViewShown) return@Runnable
        root.setPadding(0, 0, 0, 1)
        main.postDelayed(unNudge, NUDGE_MILLIS)
    }

    private val unNudge = Runnable {
        inputRoot?.setPadding(0, 0, 0, 0)
    }

    /**
     * In Teams the nudge is not enough. A real hide with a gap and then a show puts the box back above the strip, as Back and a tap
     * on the box do, so in the apps in [BOUNCE_APPS] the keyboard does that by itself, once. The strip flickers once.
     */
    private val recycle = Runnable {
        if (!isInputViewShown || !canShowStrip()) return@Runnable
        recycling = true
        recycleAwaitsHide = true
        try {
            requestHideSelf(0)
        } catch (error: RuntimeException) {
            recycling = false
            recycleAwaitsHide = false
            Log.w("Atlas", "Could not hide the suggestion strip", error)
            return@Runnable
        }
        // The app animates the hide first, about 350 ms in Teams. If the window does not go, the strip comes back anyway.
        main.postDelayed(recycleShow, RECYCLE_HIDE_TIMEOUT_MILLIS)
    }

    private val recycleShow = Runnable {
        showStrip.run()
    }

    /**
     * The owner's word on Teams, steady and in the middle of the bar for half a second, the first time the strip comes back from
     * [recycle] after the phone starts, and never again until it starts again. Never over a word's menu.
     */
    private fun showTeamsEgg() {
        val strip = strip ?: return
        if (strip.notice != null) return
        val boot = currentBoot()
        if (!OncePerBoot.due(lastEggBoot(), boot)) return
        rememberEggBoot(boot)
        strip.notice = Strip.Notice.TeamsEgg
        main.removeCallbacks(endTeamsEgg)
        main.postDelayed(endTeamsEgg, TEAMS_EGG_MILLIS)
    }

    private val endTeamsEgg = Runnable { clearTeamsEgg() }

    /** Any key or touch clears it at once, so it never stands in the way of typing. */
    private fun clearTeamsEgg() {
        main.removeCallbacks(endTeamsEgg)
        if (strip?.notice == Strip.Notice.TeamsEgg) strip?.notice = null
    }

    /** Android's boot count, where an app can read it, and the time this boot started. Neither changes when TitanKeys restarts. */
    private fun currentBoot(): OncePerBoot.Boot {
        val count = try {
            Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 }
        } catch (error: RuntimeException) {
            null
        }
        return OncePerBoot.Boot(count, System.currentTimeMillis() - SystemClock.elapsedRealtime())
    }

    /** The boot that the word last showed in. Storage can be locked just after a start, which counts as never. */
    private fun lastEggBoot(): OncePerBoot.Boot? = try {
        val saved = getSharedPreferences(EGG_PREFERENCES, MODE_PRIVATE)
        if (!saved.contains(EGG_BOOT_STARTED)) null
        else OncePerBoot.Boot(saved.getInt(EGG_BOOT_COUNT, -1).takeIf { it >= 0 }, saved.getLong(EGG_BOOT_STARTED, 0L))
    } catch (error: IllegalStateException) {
        null
    }

    private fun rememberEggBoot(boot: OncePerBoot.Boot) {
        try {
            getSharedPreferences(EGG_PREFERENCES, MODE_PRIVATE).edit()
                .putInt(EGG_BOOT_COUNT, boot.count ?: -1).putLong(EGG_BOOT_STARTED, boot.startedAt).apply()
        } catch (error: IllegalStateException) {
            Log.w("Atlas", "Could not keep the boot of the Teams word", error)
        }
    }

    private fun cancelRecycle() {
        main.removeCallbacks(recycle)
        main.removeCallbacks(recycleShow)
        recycling = false
        recycleAwaitsHide = false
    }

    /**
     * Asks Android to show the strip, so that the app hears that the keyboard is back and makes room for it. An app that has
     * already handled a hide, such as Microsoft Teams, otherwise leaves its message box under the strip.
     */
    private val showStrip = Runnable {
        if (!canShowStrip()) {
            if (field.exact) logExact("bar not shown: back $hiddenByBack editing $editing bound ${currentInputBinding != null}")
            return@Runnable
        }
        if (field.exact) logExact("bar shown")
        try {
            requestShowSelf(0)
        } catch (error: RuntimeException) {
            // Titan firmware can drop its current client before Android reports the editor gone.
            Log.w("Atlas", "Could not show the suggestion strip", error)
        }
        main.removeCallbacks(forceStrip)
        main.postDelayed(forceStrip, SHOW_FALLBACK_MILLIS)
    }

    /** A cancelled show request can leave the client waiting for a window surface, so the window shows itself if Android's did not. */
    private val forceStrip = Runnable {
        if (!isInputViewShown && canShowStrip()) {
            try {
                showWindow(true)
                requestShowSelf(0)
            } catch (error: RuntimeException) {
                Log.w("Atlas", "Could not show the suggestion strip", error)
            }
        }
        // A panel that could not show must not take the next key.
        if (panelOpen && !isInputViewShown) closePanel()
    }

    // Apps can drop a field yet still start input on it, so only a field that answers gets the strip.
    // A view with exact typing, such as a remote desktop's, holds no text to answer with, and its busy main thread can let the
    // question time out, which used to leave its bar hidden. Its app chose it by being on the list, so it gets the bar without asking.
    private fun canShowStrip() = !hiddenByBack && editing && currentInputBinding != null
        && (field.exact || currentInputConnection?.getTextBeforeCursor(1, 0) != null)

    private fun cancelShowStrip() {
        main.removeCallbacks(showStrip)
        main.removeCallbacks(forceStrip)
    }

    private val hideStrip = Runnable {
        // A held status has no strip to hide, and hiding the window would give the touchpad back to the app.
        if (editing || hold.held) return@Runnable
        try {
            hideWindow()
            requestHideSelf(0)
        } catch (error: RuntimeException) {
            Log.w("Atlas", "Could not hide the suggestion strip", error)
        }
    }

    /**
     * An app's own hide, such as from a Done key, leaves the window closed, so a panel that opens then must show it. If the
     * window has not shown by the time of the fallback in [forceStrip], the panel closes there.
     */
    private val showPanel = Runnable {
        if (!panelOpen || isInputViewShown) return@Runnable
        if (canShowStrip()) showStrip.run() else closePanel()
    }

    private val refreshModifiers = Runnable { showModifiers() }

    // Apps on Android's newer back handling ignore a back key from a keyboard, so a system back goes first.
    private val sendBack = Runnable { if (!BackService.back() && editing) sendDownUpKeyEvents(KeyEvent.KEYCODE_BACK) }

    private val confirmDoubleTap = Runnable {
        if (!cursorGesture.confirm()) return@Runnable
        Recording.sink?.gesture("double", SystemClock.uptimeMillis(), "")
        finishHeldTaps(type = false)
        KeyboardEvents.publish { KeyboardEvent.DoubleTapped }
        // A terminal or a remote view has no text to select here.
        if (field.exact) haptics.play(Haptic.WordPicked) else enqueue { selectWord() }
    }

    private val checkHeldTaps = Runnable { settleHeldTaps() }

    /** Deletes the next word of a held delete flick. Only one edit is on its way at a time, so a lift leaves no words waiting. */
    private val stepDeleteHold = Runnable {
        val free = pendingInput.isEmpty() && !composer.hasPendingInput
        val next = deleteHold.step(SystemClock.uptimeMillis(), free, ::deleteHeldWord)
        if (next != null) nextDeleteHoldStep(next) else strip?.gesture?.deleteEnded()
    }

    private fun nextDeleteHoldStep(at: Long) {
        main.removeCallbacks(stepDeleteHold)
        main.postAtTime(stepDeleteHold, at)
    }

    /** The finger moved, so the wait for a hold starts again, or ends if the finger is no longer on a delete flick. */
    private fun followDeleteHold() {
        val start = contact.points.firstOrNull()
        val at = deleteHold.moved(!altOrPanel && start != null && pieces.canDelete(start.time))
        if (at != null) nextDeleteHoldStep(at) else main.removeCallbacks(stepDeleteHold)
    }

    private fun endDeleteHold() {
        main.removeCallbacks(stepDeleteHold)
        deleteHold.reset()
        strip?.gesture?.deleteEnded()
    }

    /** Each word goes the way of a normal delete flick. [flick] is the first word's flick, and is null for the words after it. */
    private fun deleteHeldWord(flick: Contact.Outcome.Swipe?) {
        if (flick != null) {
            strip?.gesture?.deleteHeld(deleteHold.repeatMillis, SystemClock.uptimeMillis())
            // The contact was a delete and not part of a word, as it would be at a lift.
            pieces.clear()
            lastTouch = null
            Trace.touch(emptyList(), true)
            enqueue { outcome(flick) }
            return
        }
        enqueue {
            // At the start of the text nothing is left to delete, so the hold ends with a no.
            if (currentInputConnection?.getTextBeforeCursor(1, 0)?.isEmpty() == true) {
                contact.cancelActive()
                haptics.play(Haptic.DeleteNothing)
                return@enqueue
            }
            haptics.play(Haptic.DeleteWord)
            composer.backspace(true)
            strip?.gesture?.wordDeleted(SystemClock.uptimeMillis())
            KeyboardEvents.publish { KeyboardEvent.DeletedWord(held = true) }
        }
    }

    private fun startCursor(drag: CursorGesture.Drag, samples: List<Sample>) {
        Recording.sink?.gesture("drag", samples.lastOrNull()?.time ?: SystemClock.uptimeMillis(), "words=${drag.words}")
        KeyboardEvents.publish { KeyboardEvent.CursorStarted(drag.words) }
        cursor.start(drag.landed, drag.words)
        cursorSteps.reset()
        cursor.move(samples)
        // The contact now steers the cursor, so it must not also type when it lifts.
        contact.cancelActive()
        finishHeldTaps(type = false)
        Trace.touch(cursorPath, false)
        if (!gestureRegistered) {
            gestureRegistered = true
            haptics.play(if (drag.words) Haptic.Select else Haptic.Steer)
        }
        if (!drag.words) readCursorText()
        if (drag.words && !field.exact) enqueue { startWordDrag() }
        stepCursor.reset()
        stepCursor.run()
    }

    private val registerGestureLater = Runnable { registerGesture(SystemClock.uptimeMillis()) }

    /**
     * A second or third touch of a Space gesture has stayed down long enough to count, before any slide: the strip shows the
     * gesture and its feel plays now. A tap then a held touch steers (a click), and two taps then a held touch pick words (a
     * double click). If the touch lifts without a slide, the strip lets the gesture go.
     */
    private fun registerGesture(now: Long) {
        val taps = cursorGesture.held(now)
        if (taps == 0 || gestureRegistered) return
        gestureRegistered = true
        val words = taps >= 3
        Recording.sink?.gesture("registered", now, "taps=$taps")
        haptics.play(if (words) Haptic.Select else Haptic.Steer)
        if (words) readWordsAroundCursor() else readCursorText()
        strip?.gesture?.cursorFrame((gestureLanded?.x ?: .5f) * 10, 0, 0f, words, now)
    }

    /** The real text around the cursor, for the strip's line of letters while steering. */
    private fun readCursorText() {
        rereadCursorText = false
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(GestureDisplay.LINE_REACH, 0) ?: return
        val after = connection.getTextAfterCursor(GestureDisplay.LINE_REACH, 0) ?: return
        strip?.gesture?.cursorText(before, after)
    }

    /** The real words around the cursor, for the strip's row of words before a word drag has selected anything. */
    private fun readWordsAroundCursor() {
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(GestureDisplay.LINE_REACH, 0) ?: return
        val after = connection.getTextAfterCursor(GestureDisplay.LINE_REACH, 0) ?: return
        val head = before.takeLastWhile { !it.isWhitespace() }
        val tail = after.takeWhile { !it.isWhitespace() }
        strip?.gesture?.wordText(before.dropLast(head.length), head.length + tail.length, after.drop(tail.length))
    }

    /** Three quick taps on Space: the word at the cursor, as soon as no fourth touch has followed. */
    private val confirmTripleTap = Runnable {
        if (!cursorGesture.confirmTriple()) return@Runnable
        Recording.sink?.gesture("triple", SystemClock.uptimeMillis(), "")
        main.removeCallbacks(confirmDoubleTap)
        finishHeldTaps(type = false)
        KeyboardEvents.publish { KeyboardEvent.DoubleTapped }
        if (field.exact) haptics.play(Haptic.WordPicked) else enqueue { selectWord() }
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
     * away, then letters build up at the current speed. A word drag moves its end one word at a time: leaning
     * back the other way gives the words back one by one before it goes past the word that it began with.
     */
    private val stepCursor = object : Runnable {
        private var owed = 1f
        private var last = 0L

        override fun run() {
            if (!cursor.active) return
            val now = SystemClock.uptimeMillis()
            val direction = cursor.direction()
            val speed = if (direction != 0 && last != 0L) cursor.speed(now) else 0f
            if (direction == 0) owed = 1f
            else owed += speed * (now - last) / 1000
            if (direction != 0 && owed >= 1f) {
                owed = (owed - 1f).coerceAtMost(1f)
                if (cursor.words && !field.exact) extendWords(direction) else moveCursor(direction)
            }
            last = now
            strip?.gesture?.cursorFrame(cursor.column, direction, speed, cursor.words, now)
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
        Connected.start(this)
        // A public release has no typing log: an old one from 2.0 goes, as nothing can show or export it any more.
        Thread { runCatching { RawLog.removeLeftovers(this) } }.start()
        haptics = Haptics(this)
        loadClips()
        clipboard?.addPrimaryClipChangedListener(clipChanged)
        touchpad = Touchpad(getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY))
        editor = ConnectionEditor(::getCurrentInputConnection, ::getCurrentInputEditorInfo,
            { delay, action -> main.postDelayed(action, delay) }, { enterApps })
        composer = Composer(editor, EngineWords(SwipeTyping.get(this)),
            { delay, action -> main.postDelayed(action, delay) }, ::clearSettlements,
            // The automatic Shift decides a sentence's capital, so the composer adds none of its own on top. See [updateAutoShift].
            { modifiers.autoShifted }) { barWords = it; strip?.words = it }
        composer.autoPunctuation = { TypingSettings.autoPunctuation(this) }
        composer.onStroke = Trace::stroke
        // Backspace putting a word back plays nothing: the key press is felt already.
        composer.onAutocorrect = { haptics.play(Haptic.Autocorrect) }
        composer.onExpanded = { haptics.play(Haptic.Expansion) }
        composer.nearMiss = { right -> GuideScript.slip(right).takeIf { GuideHooks.takeNearMiss(currentInputEditorInfo?.privateImeOptions) } }
        composer.intended = { before -> GuideHooks.intended(currentInputEditorInfo?.privateImeOptions, before) }
        // The interactive guide acts out a gesture on this bar, only in its own field. See [Strip.playDemo].
        GuideHooks.stripDemos = { demo ->
            main.post {
                if (demo == null) strip?.stopDemo()
                else if (editing && currentInputEditorInfo?.privateImeOptions?.contains(GuideHooks.FIELD) == true) strip?.playDemo(demo)
            }
        }
        // The guide's clipboard lesson pins a few clips, so that the clipboard has something to show. Only in its own field.
        GuideHooks.clipSeeds = { clips ->
            main.post {
                if (!editing || currentInputEditorInfo?.privateImeOptions?.contains(GuideHooks.FIELD) != true) return@post
                val now = System.currentTimeMillis()
                for (text in clips.reversed()) {
                    if (clipHistory.clips.any { it.text == text }) continue
                    if (!clipHistory.add(text, now)) continue
                    clipHistory.togglePin(0)
                }
                saveClips()
                showClips()
            }
        }
        GuideHooks.stripPoints = { index ->
            main.post {
                if (index == null) strip?.point(null)
                else if (editing && currentInputEditorInfo?.privateImeOptions?.contains(GuideHooks.FIELD) == true) strip?.point(index)
            }
        }
        // The interactive guide puts the keyboard in the menu that a step needs, only in its own field.
        GuideHooks.panelRequests = { target ->
            main.post {
                if (!editing || currentInputEditorInfo?.privateImeOptions?.contains(GuideHooks.FIELD) != true) return@post
                if (target == modifiers.panel) return@post
                modifiers.openPanel(target)
                showModifiers()
                if (panelOpen && !isInputViewShown) main.post(showPanel)
            }
        }
        autocorrect = AutocorrectLoader(this, SwipeTyping.get(this))
        phoneNames = PhoneNameSource(this, SwipeTyping.get(this)).also { it.start() }
        // The firmware hands touchpad events to the topmost window titled "InputMethod", which is this
        // keyboard's window. They arrive at the window, not at any view, so catch them there.
        val window = window.window!!
        window.callback = TouchpadWindowCallback(window.callback) { event ->
            (editing && touchpad.accepts(event)).also { if (it) motion(event) }
        }
        overlay = TouchpadOverlay(this, touchpad = overlayEvent)
        TypingSettings.listen(this, overlaySetting)
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), RECEIVER_NOT_EXPORTED)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
        // The debug build can start the idle scene at once, so that a phone check need not wait three minutes. That build is not
        // marked debuggable, so its own package name tells it apart.
        if (packageName.endsWith(DEBUG_PACKAGE_SUFFIX)) {
            registerReceiver(idleBarTrigger, IntentFilter(IDLE_BAR_ACTION), RECEIVER_EXPORTED)
            registerReceiver(movingTry, IntentFilter(MOVING_ACTION), RECEIVER_EXPORTED)
            registerReceiver(stepTry, IntentFilter(STEP_ACTION), RECEIVER_EXPORTED)
            idleBarTriggerRegistered = true
        }
        updateOverlay()
        loadEmoji()
        loadShortcodes()
    }

    /** Reads the emoji shortcode list away from the main thread. See [Expansions]. */
    private fun loadShortcodes() {
        Thread {
            val names = try {
                assets.open("shortcodes.txt").bufferedReader().useLines { Expansions.readShortcodes(it) }
            } catch (error: IOException) {
                Log.w("Atlas", "Could not read the emoji shortcodes", error)
                return@Thread
            }
            main.post {
                shortcodes = names
                readExpansions()
            }
        }.start()
    }

    /** Snippets and shortcodes for the composer, from the saved settings. Null when there is nothing to expand. */
    private fun readExpansions() {
        val snippets = Snippets.load(this)
        val emoji = if (TypingSettings.shortcodes(this)) shortcodes else emptyMap()
        composer.expansions = if (snippets.isEmpty() && emoji.isEmpty()) null
            else Expansions(snippets, TypingSettings.snippetStart(this), emoji)
    }

    /** The settings screen is open when this changes, so the keyboard is idle and no field start would apply it. */
    private val overlaySetting = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != TypingSettings.TOUCHPAD_ONLY_IN_FIELDS) return@OnSharedPreferenceChangeListener
        updateOverlay()
        updateHold()
    }

    // The idle games on the bar (an Easter egg, see IdlePlayer): after three minutes or more with no key, touch or swipe, while plain words
    // show. Any input ends it at once. It stays off with exact typing, in apps where the bar hides, in fields with no suggestions,
    // with the bar or the screen off, with the Sym panel open, and with Android's animations off.

    private val idleBar = Runnable { startIdleBar() }
    private var idleBarTriggerRegistered = false
    /** The debug build's swipe tick length to try, from adb: the extra "ms", or 0 to go back to the usual length. It plays one. */
    private val movingTry = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Haptics.tryMovingMillis(context, intent.getIntExtra("ms", 0).toLong())
            Log.i("Atlas", "Swipe tick: ${Haptics.movingMillis(context)} ms")
            main.post { haptics.play(Haptic.SwipeKey) }
        }
    }

    /** The same for a step (a cursor passing a space, a bar tap): the extra "ms", or 0 for the usual length. It plays one. */
    private val stepTry = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Haptics.tryStepMillis(context, intent.getIntExtra("ms", 0).toLong())
            Log.i("Atlas", "Step: ${Haptics.stepMillis(context)} ms")
            main.post { haptics.play(Haptic.CursorSpace) }
        }
    }

    private val idleBarTrigger = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            main.post {
                val started = startIdleBar()
                Log.i("Atlas", if (started) "Idle bar: started" else "Idle bar: not started, as no plain words show or it is off here")
            }
        }
    }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = sleepIdleBar()
    }

    // Exact typing and an app where the bar hides (both from Settings) keep it off, as do fields with no suggestions or fixes.
    private fun idleBarAllowed() = editing && isInputViewShown && !panelOpen && !field.exact && !barHidden && field.suggestions &&
        field.autocorrect && showSuggestions() && ValueAnimator.areAnimatorsEnabled() && getSystemService(PowerManager::class.java).isInteractive

    private fun startIdleBar() = idleBarAllowed() && strip?.startIdle(Random(System.nanoTime())) == true

    /**
     * Input starts the wait again. Coming back, with a finger on the touchpad or a touch on the bar, sends the idle game's letters
     * home; typing or swiping ([cut]) puts the words back at once. The next game waits a random while, at least [IDLE_BAR_MILLIS].
     */
    private fun wakeIdleBar(cut: Boolean = true) {
        main.removeCallbacks(idleBar)
        if (cut) strip?.stopIdle() else strip?.comeBackFromIdle()
        // A demonstration from the guide gives the bar back as soon as you act.
        strip?.stopDemo()
        if (idleBarAllowed()) main.postDelayed(idleBar, IDLE_BAR_MILLIS + Random.nextLong(IDLE_BAR_MILLIS / 2))
    }

    private fun sleepIdleBar() {
        main.removeCallbacks(idleBar)
        strip?.stopIdle()
    }

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            hold.screenOn()
            updateKeyWatch()
            updateHold()
        }
    }

    /** With no field active, a touchpad event only stops here, so that the app under the overlay does not scroll. */
    private val overlayEvent: (MotionEvent) -> Boolean = { event ->
        touchpad.accepts(event).also { if (it && editing) motion(event) }
    }

    /**
     * The Back service also puts a window above the notification shade, so that a pop-up notification cannot take the touchpad.
     * It stays for [HIGH_GRACE_MILLIS] after the field ends: until Android hears that the keyboard has gone, the firmware still
     * sends the touchpad to the windows, and a swipe that ends as the keyboard goes, or the gap as one field hands over to
     * the next, would reach the pop-up. With no field it only drops the touchpad events, as the keyboard's own overlay does.
     */
    private fun updateOverlay() {
        val wanted = editing || TypingSettings.touchpadOnlyInFields(this)
        if (wanted) overlay.show() else overlay.hide()
        main.removeCallbacks(releaseHighTouchpad)
        if (wanted) BackService.touchpad(overlayEvent) else main.postDelayed(releaseHighTouchpad, HIGH_GRACE_MILLIS)
    }

    private val releaseHighTouchpad = Runnable { BackService.touchpad(null) }

    private fun holdWanted() =
        TouchpadHold.wanted(TypingSettings.touchpadOnlyInFields(this), editing, overlay.up, BackService.enabled(this), hold.lettingGo)

    /** Puts the status that the firmware reads where the setting and the field need it. See [TouchpadHold]. */
    private fun updateHold() {
        if (holdWanted()) {
            // A strip that is still up keeps the status on until it hides, and its hide leaves the status on.
            if (hold.held || isInputViewShown || main.hasCallbacks(showHold)) return
            main.postDelayed(showHold, hold.delay(SystemClock.uptimeMillis()))
            return
        }
        main.removeCallbacks(showHold)
        val wasHeld = hold.held
        hold.hidden()
        // With a field, the strip takes over the window that is already up.
        if (wasHeld && !editing) hideHeldWindow()
    }

    private val showHold = Runnable {
        if (!holdWanted() || hold.held || isInputViewShown || currentInputBinding == null) return@Runnable
        try {
            backDisposition = BACK_DISPOSITION_WILL_NOT_DISMISS
            showWindow(false)
            // Only the status is wanted. Before the first frame, this takes the window back off the screen.
            holdWindow()
        } catch (error: RuntimeException) {
            Log.w("Atlas", "Could not report the keyboard status", error)
        }
    }

    private fun holdWindow() {
        window.hide()
        hold.shown()
    }

    /** With no field, a hide takes only the window away. Android's own would also turn the status off. See [TouchpadHold]. */
    override fun hideWindow() {
        if (!TouchpadHold.keepsStatus(holdWanted(), isInputViewShown)) {
            super.hideWindow()
            return
        }
        // A panel that nobody can see must not take the next key.
        closePanel()
        holdWindow()
    }

    /** Ends the held status and Android's back handler with it. A hide from Android keeps them, so this does not go through [hideWindow]. */
    private fun hideHeldWindow() {
        try {
            super.hideWindow()
        } catch (error: RuntimeException) {
            Log.w("Atlas", "Could not clear the keyboard status", error)
        }
    }

    /**
     * What Back does while the status is held. The first push lets go of the touchpad and stays on the screen. The Back service
     * then hears the next push go through to the app, and the hold comes back after it. See [TouchpadHold].
     */
    private fun heldBack(event: KeyEvent, down: Boolean): Boolean = when (TouchpadHold.backStep(TouchpadHold.fromHandler(event.deviceId, event.flags), down)) {
        TouchpadHold.Back.ToApp -> false
        TouchpadHold.Back.Keep -> true
        TouchpadHold.Back.KeepAndLetGo -> {
            hold.letGo(currentInputEditorInfo?.packageName)
            main.removeCallbacks(showHold)
            hideHeldWindow()
            hold.hidden()
            updateKeyWatch()
            true
        }
    }

    /** The second push of Back has gone on to the app, so the hold comes back once the app has had it. */
    private val backWentThrough = Runnable {
        hold.backWentThrough(SystemClock.uptimeMillis())
        updateKeyWatch()
        updateHold()
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
        val words = Strip(this, { index -> haptics.play(Haptic.BarWord); enqueue { composer.choose(index) } }, ::holdWord,
            { action -> enqueue { stripAction(action) } }, { enqueue { pasteOfferedClip() } }, { wakeIdleBar(cut = false) })
        val keys = SymPanel(this,
            pick = { item -> enqueue { typePanelItem(item) } },
            useQuickSetting = { item -> enqueue { useQuickSetting(item) } },
            quickSettingOn = ::quickSettingOn,
            pressAlt = { enqueue { modifiers.toggleSettings(); showModifiers() } },
            pressSym = { enqueue { modifiers.pressSym(); showModifiers() } },
            pageChanged = { haptics.play(Haptic.PanelPage) },
            groupChanged = { haptics.play(Haptic.PanelGroup) },
            marked = { haptics.play(Haptic.PanelMark) },
            clipKey = { key -> enqueue { clipKey(key) } },
            holdClip = { key -> enqueue { holdClip(key) } },
            openClipboard = { enqueue { openClipboardPage() } },
        )
        strip = words
        panel = keys
        keys.emoji = emoji
        // Android can make this view after the field has started, and again when the screen settings change.
        words.words = barWords
        words.showSuggestions = showSuggestions()
        showModifiers()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(keys)
            addView(words)
            inputRoot = this
            roundTopCorners(this)
        }
    }

    /**
     * The keyboard's top corners take the medium radius, the same as everything else in TitanKeys. The outline only clips what is
     * drawn, so nothing moves. It reaches past the bottom, where the screen's own corners do the rounding.
     */
    private fun roundTopCorners(view: View) {
        val radius = Radii.px(resources, Radii.MEDIUM)
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) =
                outline.setRoundRect(0, 0, view.width, view.height + radius.toInt(), radius)
        }
        view.clipToOutline = true
    }

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        // A press from the field before must not change a letter here.
        holdEngine.cancel()
        heldLetter = null
        preserveDestinationInput()
        editor.cancelState()
        super.onStartInput(info, restarting)
        val exact = ExactTyping.applies(info.packageName, ExactTyping.apps(this))
        if (exact || info.inputType == EditorInfo.TYPE_NULL) logExact("start ${info.packageName} type 0x${Integer.toHexString(info.inputType)}" +
            " options 0x${Integer.toHexString(info.imeOptions)} restarting $restarting exact $exact")
        editing = info.inputType != EditorInfo.TYPE_NULL || exact
        Connected.field(this, editing)
        if (!restarting) {
            typedInField = false
            fieldStartedAt = SystemClock.uptimeMillis()
            // Apps also restart input on the field that stays, so only a new start ends what Back did.
            hiddenByBack = false
        }
        // TitanKeys's own Settings fields hold snippets and words to add, which must not be learned. The guide's field is for practice.
        val ownSettings = info.packageName == packageName && info.privateImeOptions?.contains(GuideHooks.FIELD) != true
        field = if (exact) Field.Exact else if (editing) Field.of(info.inputType, info.imeOptions) else Field.None
        if (ownSettings) field = field.copy(learning = false)
        // Android only calls onFinishInput first when the input is not restarting.
        rawLog?.finish()
        // Exact typing is for commands and remote computers, which are no typing to learn from.
        rawLog = if (editing && !exact && !ownSettings) RawLog.start(this, info, restarting) else null
        composer.log = rawLog
        inLauncher = editing && TypingSettings.launcherHidesBar(this) && info.packageName == launcherPackage()
        barHidden = info.packageName in TypingSettings.hiddenBarApps(this)
        launcherFieldHasText = inLauncher && info.initialSelStart > 0
        contact.releaseKeys()
        clearSettlements()
        modifiers.fieldStarted(restarting, editing)
        deadKeyPending = false
        showModifiers()
        readSettings()
        if (editing && !restarting) rememberEnterApp(info)
        if (!restarting) rememberApp(info)
        cancelShowStrip()
        cancelRecycle()
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
        hold.started(info.packageName, editing)
        if (!statusReported) {
            statusReported = true
            // A new back disposition sends the current status, which clears a status that a dead process left on.
            backDisposition = BACK_DISPOSITION_WILL_NOT_DISMISS
        }
        updateHold()
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
        // A word gesture could send the wrong keys to a terminal or a remote computer, or a word into a password.
        contact.swipes = TypingSettings.swipeTyping(this) && field.words
        enterApps = EnterKey.apps(TypingSettings.enterApps(this))
        val corrects = TypingSettings.autocorrect(this)
        composer.autocorrect = autocorrect.autocorrect.takeIf { corrects }
        // The word data takes a moment to load, so it starts as a field opens: the first word typed is fixed too.
        if (!corrects) autocorrect.release() else if (editing) autocorrect.prepare()
        strip?.showSuggestions = showSuggestions()
        readExpansions()
        updateKeyWatch()
    }

    /**
     * Smart Launcher takes every key press in its search field before the keyboard hears it, so a press there cannot start a
     * hold or count towards Shift and Alt. The Back service hears them first, only in this one app and only when a setting needs them.
     * It also hears them while the Sym panel is open, and until the release of a key that the panel took. See [PanelKeyFilter].
     * After a first push of Back let go of the touchpad, it hears the second push go through. See [TouchpadHold].
     */
    private fun updateKeyWatch() {
        val launcher = editing && currentInputEditorInfo?.packageName == SMART_LAUNCHER && (holdSymbols || !modifiers.doublePressLocks)
        val forPanel = panelOpen || panelKeys.holding
        val running = BackService.watchKeys(if (launcher || forPanel || hold.lettingGo) ::watchedKey else null)
        watchingKeys = running && launcher
        if (!running) panelKeys.clear()
    }

    /**
     * Hears a key before Smart Launcher does. The launcher still types its own letters, so this only times holds and
     * follows Shift and Alt. True takes the key from the app, which is only ever a repeat of a letter that can become a symbol,
     * or a second push of Shift or Alt. Every key waits for this answer, so slow work goes to the main looper.
     *
     * While the Sym panel is open, Enter and Backspace are kept from every app and go to the panel, the same as a key that the app
     * passed on to the keyboard.
     */
    private fun watchedKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        // The second push of Back goes on to the app. Its release says that the app has it.
        if (event.keyCode == KeyEvent.KEYCODE_BACK && hold.lettingGo && !editing) {
            if (!down) main.post(backWentThrough)
            return false
        }
        if (panelKeys.takes(event.keyCode, down, event.repeatCount, modifiers.panel)) {
            // Once the panel has closed, a key that it took only waits for its release.
            if (!down) onKeyUp(event.keyCode, event) else if (panelOpen) onKeyDown(event.keyCode, event)
            updateKeyWatch()
            return true
        }
        if (!watchingKeys) return false
        main.post(refreshModifiers)
        val code = event.keyCode
        // The app takes Back before the keyboard, so an open panel must close here.
        if (code == KeyEvent.KEYCODE_BACK && isInputViewShown && backKey(event, event.action == KeyEvent.ACTION_DOWN)) return true
        if (event.action != KeyEvent.ACTION_DOWN) {
            holdEngine.up(code, event.deviceId, event.eventTime)
            return Modifiers.isShiftOrAlt(code) && modifiers.appRelease(code, event.eventTime)
        }
        if (Modifiers.isShiftOrAlt(code)) {
            val push = modifiers.appPush(code, event.eventTime, event.repeatCount)
            if (push == Modifiers.AppPush.HideAndClear) {
                val meta = if (code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_SHIFT_RIGHT) KeyEvent.META_SHIFT_ON else KeyEvent.META_ALT_ON
                main.post { currentInputConnection?.clearMetaKeyStates(meta) }
            }
            return push != Modifiers.AppPush.Pass
        }
        val repeatOfHold = trackHold(event)
        // A shortcut types nothing, so like the keyboard's own typing, only a character uses up a press of Shift or Alt.
        if (event.repeatCount == 0 && !event.isSystem && !KeyEvent.isModifierKey(code)
            && !event.isCtrlPressed && !event.isMetaPressed) modifiers.consume()
        return repeatOfHold
    }

    /** Only the package is kept, so the Enter key setting can offer apps without asking to see every installed app. */
    private fun rememberEnterApp(info: EditorInfo) {
        val app = info.packageName ?: return
        if (app == packageName || !EnterKey.recordsApp(info.inputType, info.imeOptions)) return
        val recent = TypingSettings.recentEnterApps(this)
        val updated = EnterKey.recentApps(recent, app)
        if (updated != recent) TypingSettings.recentEnterApps(this, updated)
    }

    /** Only the package is kept, so the exact typing setting can offer apps without asking to see every installed app. */
    private fun rememberApp(info: EditorInfo) {
        val app = info.packageName ?: return
        if (app == packageName) return
        val recent = TypingSettings.recentApps(this)
        val updated = ExactTyping.recentApps(recent, app)
        if (updated != recent) TypingSettings.recentApps(this, updated)
    }

    // Launchers like Niagara keep a search field focused on the home screen, where the bar only gets in the way until you type.
    // Password, email and number fields and exact typing get no suggestions, but the bar still shows, with its word places empty,
    // so that Shift, Alt and Ctrl show on it and the gestures draw there (issue 45).
    // In an app where the user hid the bar, the strip draws nothing and keeps one pixel, so the keyboard's window stays up: keys,
    // holds, Alt and the panel work as before, and the app never sees the keyboard go away. The panel opens over it as usual.
    private fun showSuggestions() = TypingSettings.suggestionBar(this) && (!inLauncher || launcherFieldHasText) && !barHidden

    private fun launcherPackage(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // The bar may only now be set up for this field, so the wait starts once it is.
        main.post { wakeIdleBar() }
        offerClip()
        // Back belongs to the app, even though it also hides the strip on the way.
        backDisposition = BACK_DISPOSITION_WILL_NOT_DISMISS
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (field.exact) logExact("window shown")
        if (recycling) {
            recycling = false
            showTeamsEgg()
        } else if (editing && SystemClock.uptimeMillis() - windowHiddenAt < BOUNCE_MILLIS) {
            val recycles = currentInputEditorInfo?.packageName in BOUNCE_APPS
            main.removeCallbacks(recycle)
            main.removeCallbacks(nudge)
            if (recycles) main.postDelayed(recycle, RECYCLE_DELAY_MILLIS) else main.postDelayed(nudge, NUDGE_DELAY_MILLIS)
        }
        // Android draws its hide and switch-keyboard buttons in this bar; the strip has no room for them.
        window.window?.insetsController?.hide(WindowInsets.Type.captionBar())
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        sleepIdleBar()
        if (field.exact) logExact("window hidden: back $passingBack typed $typedInField")
        windowHiddenAt = SystemClock.uptimeMillis()
        main.removeCallbacks(recycle)
        if (recycleAwaitsHide) {
            recycleAwaitsHide = false
            main.removeCallbacks(recycleShow)
            main.postDelayed(recycleShow, RECYCLE_GAP_MILLIS)
        }
        main.removeCallbacks(nudge)
        main.removeCallbacks(unNudge)
        inputRoot?.setPadding(0, 0, 0, 0)
        // Android's hide also turned the status off.
        hold.hidden()
        // A panel that nobody can see must not take the next key.
        closePanel()
        // A delayed hide from the previous app arrives just after this field starts. A later hide, or one
        // after you have typed, is the app's own, such as a tap outside the field or a Done key.
        cancelShowStrip()
        if (passingBack) {
            // Now that the strip is gone, the app's own back can run. If its field stays, the strip stays hidden.
            passingBack = false
            hiddenByBack = editing
            main.post(sendBack)
        } else if (editing && !typedInField && SystemClock.uptimeMillis() - fieldStartedAt < STALE_HIDE_MILLIS) {
            main.post(showStrip)
        }
        updateHold()
    }

    /** A key or swipe in a field that Back left hidden brings the strip back. */
    private fun showStripAfterBack() {
        if (!hiddenByBack) return
        hiddenByBack = false
        main.post(showStrip)
    }

    override fun onUnbindInput() {
        cancelShowStrip()
        cancelRecycle()
        main.removeCallbacks(sendBack)
        // The app has lost the keyboard, so coming back to it counts as a new start.
        hiddenByBack = false
        super.onUnbindInput()
    }

    // Clipboard history (#46). Since Android 10 only the keyboard in use, or the app in front, can read the clipboard. TitanKeys
    // is the keyboard in use, so it hears each copy, also while it is hidden. It keeps no clip that the app marks as sensitive,
    // and none copied while a password field has the keyboard.

    private val clipboard by lazy { getSystemService(ClipboardManager::class.java) }
    private var clipHistory = ClipboardHistory()
    private val clipFile by lazy { AtomicFile(java.io.File(noBackupFilesDir, CLIP_FILE)) }
    private var clipPinning = false
    private var clipClearing = false
    /** The time of the clip that the strip last offered and that was then pasted or passed over, so it is not offered again. */
    private var clipOfferDone = -1L
    private val endClipOffer = Runnable { dismissClipOffer() }
    private val clipChanged = ClipboardManager.OnPrimaryClipChangedListener { readClip() }

    private fun readClip() {
        val clip = try {
            clipboard?.primaryClip
        } catch (error: RuntimeException) {
            Log.w("Atlas", "Could not read the clipboard", error)
            null
        } ?: return
        if (clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true) return
        if (editing && field.secret) return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).text?.toString() ?: return
        expireClips()
        if (!clipHistory.add(text, System.currentTimeMillis())) return
        saveClips()
        showClips()
        offerClip()
    }

    private fun loadClips() {
        clipHistory = try {
            ClipboardHistory.decode(String(clipFile.readFully(), Charsets.UTF_8))
        } catch (error: java.io.IOException) {
            ClipboardHistory()
        }
        expireClips()
    }

    private fun saveClips() {
        val stream = try {
            clipFile.startWrite()
        } catch (error: java.io.IOException) {
            Log.w("Atlas", "Could not save the clipboard history", error)
            return
        }
        try {
            stream.write(clipHistory.encode().toByteArray(Charsets.UTF_8))
            clipFile.finishWrite(stream)
        } catch (error: java.io.IOException) {
            clipFile.failWrite(stream)
            Log.w("Atlas", "Could not save the clipboard history", error)
        }
    }

    private fun expireClips() {
        if (clipHistory.expire(System.currentTimeMillis())) saveClips()
    }

    /** Gives the clipboard page the clips as they are now. */
    private fun showClips() {
        panel?.clipPinning = clipPinning
        panel?.clipClearing = clipClearing
        panel?.clips = clipHistory.clips.toList()
        panel?.render()
    }

    /** Right after a copy, the strip shows the newest clip until you paste it or type on. */
    private fun offerClip() {
        val newest = clipHistory.clips.firstOrNull()
        val age = newest?.let { System.currentTimeMillis() - it.time }
        if (newest == null || age == null || newest.time == clipOfferDone || age !in 0 until CLIP_OFFER_MILLIS || field.secret) {
            strip?.clip = null
            return
        }
        strip?.clip = newest.text
        // The first offer ever also says where the older clips are, once.
        val preferences = getSharedPreferences(CLIP_PREFERENCES, MODE_PRIVATE)
        if (!preferences.getBoolean(CLIP_HINT_SHOWN, false)) {
            strip?.clipHint = true
            preferences.edit().putBoolean(CLIP_HINT_SHOWN, true).apply()
        }
        main.removeCallbacks(endClipOffer)
        main.postDelayed(endClipOffer, CLIP_OFFER_MILLIS - age)
    }

    private fun dismissClipOffer() {
        main.removeCallbacks(endClipOffer)
        if (strip?.clip == null) return
        clipOfferDone = clipHistory.clips.firstOrNull()?.time ?: -1L
        strip?.clip = null
        strip?.clipHint = false
    }

    private fun pasteOfferedClip() {
        val text = strip?.clip ?: return
        dismissClipOffer()
        haptics.play(Haptic.ClipOffer)
        composer.paste(text)
    }

    /** A push or touch of a key on the clipboard page: a letter pastes its clip or, while pinning, pins it. */
    private fun clipKey(key: String) {
        when (key) {
            Layout.BACKSPACE -> {
                if (clipHistory.clips.none { !it.pinned }) return
                clipPinning = false
                if (clipClearing) {
                    clipClearing = false
                    if (clipHistory.clear()) saveClips()
                    haptics.play(Haptic.ClipboardCleared)
                } else {
                    // The first press only arms the clear, which the page shows. The press is felt already.
                    clipClearing = true
                }
                showClips()
                return
            }
            Layout.ENTER -> {
                if (clipHistory.clips.isEmpty()) return
                clipClearing = false
                clipPinning = !clipPinning
                showClips()
                return
            }
            else -> {
                val index = panel?.clipIndex(key) ?: return
                val clip = clipHistory.clips.getOrNull(index) ?: return
                clipClearing = false
                if (clipPinning) pin(index) else {
                    haptics.play(Haptic.ClipPasted)
                    composer.paste(clip.text)
                    closePanel()
                    return
                }
            }
        }
        showClips()
    }

    /** Opens the clipboard page: from A on the symbols or emoji, or X on the quick settings page. */
    private fun openClipboardPage() {
        expireClips()
        clipPinning = false
        clipClearing = false
        panel?.resetClipPages()
        showClips()
        modifiers.openClipboard()
        showModifiers()
    }

    /** A touch held on a clip pins or unpins it, without the Pin key. */
    private fun holdClip(key: String) {
        pin(panel?.clipIndex(key) ?: return)
        showClips()
    }

    /** Pinning locks the clip; unpinning is a step, as unlocking Shift is. */
    private fun pin(index: Int) {
        if (clipHistory.togglePin(index)) saveClips()
        haptics.play(if (clipHistory.clips.getOrNull(index)?.pinned == true) Haptic.ClipPinned else Haptic.ClipUnpinned)
    }

    // The word menu (#43): hold a word on the strip, or select one word in the field, to save or forget it.

    /** A word whose menu is open: [typed] is the letters it would replace on the bar, and empty for a selected word. */
    private class HeldWord(val word: String, val typed: String)
    private var menuWord: HeldWord? = null
    /** A forgotten word, which waits out its Undo before the engine forgets it. */
    private var pendingForget: HeldWord? = null
    private val forgetNow = Runnable { commitForget() }
    private val endNotice = Runnable { strip?.notice = null }

    /** Holding a word on the strip opens its menu. */
    private fun holdWord(index: Int): Boolean {
        val (word, typed) = composer.shownWord(index) ?: return false
        openMenu(HeldWord(word, typed))
        return true
    }

    private fun openMenu(held: HeldWord) {
        commitForget()
        main.removeCallbacks(endNotice)
        menuWord = held
        strip?.menuMark = -1
        haptics.play(Haptic.MenuOpen)
        strip?.notice = Strip.Notice.Menu(held.word, canForget(held))
        KeyboardEvents.publish { KeyboardEvent.WordMenu(held.word) }
    }

    private fun canForget(held: HeldWord) =
        SwipeTyping.get(this).forgetKind(held.word) != ForgetWords.Kind.NONE || ForgetWords.fix(held.typed, held.word)

    private fun closeMenu() {
        if (menuWord == null) return
        menuWord = null
        strip?.menuMark = -1
        if (strip?.notice is Strip.Notice.Menu) strip?.notice = null
    }

    /**
     * The word menu is open on the strip. It stays until you pick a button or close it: the touchpad only marks its buttons then,
     * and the top two rows of keys pick them. See [MenuKeys].
     */
    private val menuOpen get() = menuWord != null && strip?.notice is Strip.Notice.Menu

    /** The key that picked a menu button, whose release must not reach the app. */
    private var menuKey = -1

    /** The menu's button that [key] picks, or null for a key that picks none. */
    private fun menuAction(key: String): Strip.Action? {
        val strip = strip ?: return null
        val actions = strip.noticeActions(strip.notice)
        return actions.getOrNull(MenuKeys.button(key, actions.size))
    }

    /** A resting finger marks the button over its key, as on the Sym panel. */
    private fun markMenu(key: String) {
        val strip = strip ?: return
        val actions = strip.noticeActions(strip.notice)
        val mark = if (menuOpen) MenuKeys.button(key, actions.size) else -1
        if (mark >= 0 && mark != strip.menuMark) haptics.play(Haptic.MenuMark)
        strip.menuMark = mark
    }

    private fun stripAction(action: Strip.Action) {
        val held = menuWord
        if (held != null && action != Strip.Action.OpenSelected) KeyboardEvents.publish { KeyboardEvent.MenuPicked(action.name) }
        when (action) {
            Strip.Action.OpenSelected -> strip?.selected?.let { openMenu(HeldWord(it, "")) }
            Strip.Action.Close -> closeMenu()
            Strip.Action.Undo -> undoForget()
            Strip.Action.Save -> if (held != null) saveWord(held)
            Strip.Action.Forget -> if (held != null && canForget(held)) forgetWord(held)
        }
    }

    /** Puts the word in Saved words. A blocked word is let back in first, since Save is a clear wish for it. */
    private fun saveWord(held: HeldWord) {
        menuWord = null
        val typing = SwipeTyping.get(this)
        val add = {
            typing.addWord(held.word) { error ->
                if (error != null) {
                    Log.w("Atlas", "Could not save a word: $error")
                    main.post { haptics.play(Haptic.WordNotSaved) }
                }
            }
        }
        haptics.play(Haptic.WordSaved)
        if (!typing.wordAllowed(held.word)) typing.setWordBlocked(held.word, false) { add() } else add()
        composer.unhide(held.word)
        strip?.notice = Strip.Notice.Saved(held.word)
        main.postDelayed(endNotice, SAVED_NOTICE_MILLIS)
    }

    /** Forget leaves the word off the bar at once, and the engine forgets it once the Undo has had its time. */
    private fun forgetWord(held: HeldWord) {
        menuWord = null
        pendingForget = held
        composer.hide(held.word)
        if (strip?.selected.equals(held.word, ignoreCase = true)) strip?.selected = null
        strip?.notice = Strip.Notice.Forgot(held.word)
        haptics.play(Haptic.WordForgotten)
        main.postDelayed(forgetNow, FORGET_UNDO_MILLIS)
    }

    private fun undoForget() {
        val pending = pendingForget ?: return
        pendingForget = null
        haptics.play(Haptic.WordSaved)
        main.removeCallbacks(forgetNow)
        strip?.notice = null
        composer.unhide(pending.word)
    }

    /** The word stays off the bar in this field, since the bar can still hold words from before. */
    private fun commitForget() {
        val pending = pendingForget ?: return
        pendingForget = null
        main.removeCallbacks(forgetNow)
        if (strip?.notice is Strip.Notice.Forgot) strip?.notice = null
        SwipeTyping.get(this).forgetWord(pending.word, pending.typed) { error ->
            if (error != null) Log.w("Atlas", "Could not forget a word: $error")
        }
    }

    /** One word selected in the field shows alone on the strip, so that its menu can save or forget it. */
    private fun showSelectedWord(start: Int, end: Int) {
        val length = end - start
        val word = if (start < 0 || length !in 1..MAX_SELECTED_WORD) null
            else currentInputConnection?.getSelectedText(0)?.toString()?.takeIf { SELECTED_WORD.matches(it) }
        if (word == null && menuWord?.typed == "" && strip?.notice is Strip.Notice.Menu) closeMenu()
        strip?.selected = word
    }

    override fun onFinishInput() {
        // A word waiting to be forgotten is forgotten now, before the next field can offer it.
        commitForget()
        closeMenu()
        strip?.selected = null
        // The letter is already typed, so a press that is still down can only stay a letter.
        holdEngine.cancel()
        heldLetter = null
        preserveDestinationInput()
        // The old connection is still owned here; only a verified final explicit choice is learned.
        composer.stop(terminal = true)
        editor.cancelState()
        editing = false
        Connected.field(this, false)
        field = Field.None
        updateKeyWatch()
        rawLog?.finish()
        rawLog = null
        composer.log = null
        cancelShowStrip()
        cancelRecycle()
        clearSettlements()
        contact.cancel()
        endCursor()
        deadKeyPending = false
        closePanel()
        updateOverlay()
        updateHold()
        super.onFinishInput()
    }

    override fun onEvaluateInputViewShown() = editing

    /** Android calls this when you tap the field, so the tap also brings the strip back after Back. */
    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        if (field.exact || currentInputEditorInfo?.inputType == EditorInfo.TYPE_NULL)
            logExact("show asked by ${currentInputEditorInfo?.packageName} editing $editing")
        hiddenByBack = false
        return editing
    }

    override fun onEvaluateFullscreenMode() = false

    /**
     * Teams sometimes takes a tap on its message box while the strip shows as if the keyboard were hidden: it asks for a show,
     * which does nothing, and moves its box down. The keyboard hears nothing else of that tap, but a text field tells the
     * keyboard about each tap on it, so in the apps in [BOUNCE_APPS] a tap starts the hide and show of [recycle] too.
     */
    @Deprecated("Android still calls it for a tap on a text field")
    override fun onViewClicked(focusChanged: Boolean) {
        @Suppress("DEPRECATION")
        super.onViewClicked(focusChanged)
        if (!editing || !isInputViewShown || recycling) return
        if (currentInputEditorInfo?.packageName !in BOUNCE_APPS) return
        main.removeCallbacks(recycle)
        main.postDelayed(recycle, RECYCLE_DELAY_MILLIS)
    }

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
        // Deleting back to a sentence start, or moving the cursor there, turns the automatic Shift on again.
        if (updateAutoShift()) showModifiers()
        if (editing && field.suggestions) showSelectedWord(newStart, newEnd)
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
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            clearTeamsEgg()
            // A finger landing is you coming back; the letters go home. A tap or swipe that types cuts to the words: see [outcome].
            wakeIdleBar(cut = false)
        }
        // The panel and the cursor work in every field. Where the touchpad types no words, swipes and taps type nothing: see [outcome].
        // With exact typing the touchpad sends nothing, so a resting finger never reaches a terminal or a remote computer.
        if (field.exact && !panelOpen) return
        typedInField = true
        val samples = touchpad.samples(event)
        TouchSizes.record(event)
        Recording.sink?.motion(event, samples)
        inputLog()?.motion(event, samples)
        if (panelOpen && hover(event, samples)) return
        if (!panelOpen) followRestingKey(event, samples)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val first = samples.firstOrNull() ?: Sample(Float.NaN, Float.NaN, event.eventTime)
                haptics.begin(samples.firstOrNull())
                endDeleteHold()
                contact.down(first)
                if (cursor.resume(first, composer.liftOffMillis.toLong())) {
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
                main.removeCallbacks(confirmTripleTap)
                cursorPath = ArrayList(samples.filter { it.valid })
                // With Alt on or the panel open the touchpad does not type, so the cursor waits until they are off.
                // A finger the contact ignored landed while typing, so it must not start the cursor.
                val gestureCounts = cursorControl && !altOrPanel && contact.travel != null
                Recording.sink?.gesture("down", first.time, "counts=$gestureCounts cursorControl=$cursorControl " +
                    "altOrPanel=$altOrPanel contact=${contact.travel != null}")
                cursorGesture.down(first, gestureCounts)
                gestureRegistered = false
                main.removeCallbacks(registerGestureLater)
                if (cursorGesture.touchPending) {
                    gestureLanded = first
                    main.postDelayed(registerGestureLater, CursorGesture.REGISTER_MILLIS)
                }
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
                // A held delete flick only waits for the finger to lift.
                if (contact.deleting) return
                samples.lastOrNull()?.let { registerGesture(it.time) }
                cursorGesture.move(samples)?.let {
                    startCursor(it, samples)
                    return
                }
                settleHeldTaps()
                followDeleteHold()
                val travel = contact.travel
                val first = contact.points.firstOrNull()
                // Wait until a configured tap key's contact is a swipe, so short taps pulse only on acceptance.
                if (contact.swipes && !altOrPanel && travel != null && first != null && (travel >= Contact.SWIPE_KEYS
                    || !isTapKey(Layout.keyAt(first.x, first.y)))) haptics.move(samples)
                Trace.touch(contact.points, false)
            }
            MotionEvent.ACTION_UP -> lift(event, samples, cancelled = false)
            // The system cancels a gesture when its window goes, such as the window above the shade. Like a short lift, the
            // swipe so far still counts, and the next touch can carry it on. See [SwipePieces].
            MotionEvent.ACTION_CANCEL -> lift(event, samples, cancelled = true)
            MotionEvent.ACTION_POINTER_DOWN -> {
                contact.cancelActive()
                endDeleteHold()
                endCursor()
                Trace.touch(emptyList(), true)
            }
        }
    }

    /** A finger lifted, or the system cancelled the gesture, which counts as a lift but never as a tap of a double tap. */
    private fun lift(event: MotionEvent, samples: List<Sample>, cancelled: Boolean) {
        endDeleteHold()
        val doubleTapped = if (cancelled) {
            cursorGesture.cancel(event.eventTime)
            false
        } else cursorGesture.up(samples, event.eventTime)
        Recording.sink?.gesture("up", event.eventTime, "doubleTapped=$doubleTapped" + if (cancelled) " cancelled=true" else "")
        main.removeCallbacks(registerGestureLater)
        // Three quick taps pick the word at the cursor, with no slide, once no fourth touch follows.
        if (!cancelled && cursorGesture.tripled) main.postAtTime(confirmTripleTap, event.eventTime + CursorGesture.CONFIRM_MILLIS)
        if (cursor.active) {
            main.removeCallbacks(stepCursor)
            cursor.move(samples)
            cursor.lift()
            strip?.gesture?.cursorPaused()
            val slips = composer.liftOffMillis > 0
            // Through a slip the path stays open, so a finger that comes back carries on drawing it.
            traceCursor(samples, ended = !slips)
            if (slips) main.postDelayed(endSlippedCursor, cursor.holdMillis(composer.liftOffMillis.toLong())) else stopCursor()
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

    /**
     * A finger that rests on a key shows that key in the panel, held steady as [SteadyKey] says. A finger that lands on the bottom
     * row while the groups show opens the group at its place when it moves along the row, and that follows the finger. A finger
     * that stays there for [GROUP_REST_MILLIS] opens it too, so a brief touch does not. Returns true for such a touch, which is then
     * not also a swipe.
     */
    private fun hover(event: MotionEvent, samples: List<Sample>): Boolean {
        val last = samples.lastOrNull { it.valid }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            pickingCategory = last != null && Layout.isBottomRow(last.y) && panel?.hasCategories == true
            categoryLandedAt = last?.x ?: 0f
            categoryAt = categoryLandedAt
            categoryMoved = false
            main.removeCallbacks(restOnCategory)
            if (pickingCategory) main.postDelayed(restOnCategory, GROUP_REST_MILLIS)
        }
        val picking = pickingCategory
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            main.removeCallbacks(restOnCategory)
            panelHover.lift(event.eventTime)
            main.postDelayed(expireHover, SteadyKey.LIFT_MILLIS)
            pickingCategory = false
            return picking
        }
        if (last == null) return picking
        if (!picking) {
            panelHover.touch(last.x, last.y)
            panel?.hover = panelHover.key
            return false
        }
        categoryAt = last.x
        categoryMoved = categoryMoved || Layout.movedAlongBottomRow(categoryLandedAt, last.x)
        if (categoryMoved && panel?.pickCategory(last.x) == true) haptics.play(Haptic.PanelGroup)
        return true
    }

    /** A finger that stayed on the bottom row opens the group under it. A key press lifts the finger first, which calls this off. */
    private val restOnCategory = Runnable {
        if (pickingCategory && panelOpen && panel?.pickCategory(categoryAt) == true) haptics.play(Haptic.PanelGroup)
    }

    /** A finger that has stayed off the touchpad no longer marks a key. */
    private val expireHover = Runnable {
        val now = SystemClock.uptimeMillis()
        if (panelHover.expire(now)) panel?.hover = ""
        if (barHover.expire(now)) {
            restingKey = ""
            markMenu("")
            showModifierBar()
        }
    }

    /** A finger that rests on a key shows in the modifier bar what that key gives, held steady as [SteadyKey] says. */
    private fun followRestingKey(event: MotionEvent, samples: List<Sample>) {
        val last = samples.lastOrNull { it.valid }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            barHover.lift(event.eventTime)
            main.postDelayed(expireHover, SteadyKey.LIFT_MILLIS)
            return
        }
        if (last == null || !barHover.touch(last.x, last.y)) return
        restingKey = barHover.key
        markMenu(restingKey)
        showModifierBar()
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
        Recording.sink?.gesture("cancel", SystemClock.uptimeMillis(), "")
        stopCursor()
        main.removeCallbacks(confirmDoubleTap)
        main.removeCallbacks(confirmTripleTap)
        main.removeCallbacks(registerGestureLater)
        cursorGesture.cancel(SystemClock.uptimeMillis())
        settleHeldTaps()
    }

    private fun stopCursor() {
        if (cursor.active || cursor.lifted) Trace.touch(cursorPath, true)
        main.removeCallbacks(endSlippedCursor)
        main.removeCallbacks(stepCursor)
        cursor.end()
        strip?.gesture?.cursorEnded()
    }

    /** Closes an open panel, or uses up the rest of the push that closed it. False is an ordinary Back that does not concern a panel. */
    private fun backKey(event: KeyEvent, down: Boolean): Boolean = when (backPush.event(down, panelOpen, event.eventTime, event.downTime)) {
        BackPush.Action.ClosePanel -> {
            closePanel()
            true
        }
        BackPush.Action.UseUp -> true
        BackPush.Action.Normal -> false
    }

    /** Sends one arrow key press, in order with the rest of the typing. */
    private fun moveCursor(direction: Int) {
        if (direction == 0) return
        val time = SystemClock.uptimeMillis()
        val code = if (direction > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        val arrow = KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD)
        enqueue {
            // Going past a space feels different from a letter, so that you can count words without looking. A cursor at the
            // start or end of the text can't move, so it plays gone, once each time it gets there. See [CursorSteps].
            val connection = currentInputConnection
            val next = if (direction > 0) connection?.getTextAfterCursor(1, 0) else connection?.getTextBeforeCursor(1, 0)
            // An app that does not answer still gets the light tick.
            cursorSteps.step(if (next == null) 'a' else next.firstOrNull())?.let(haptics::play)
            if (rereadCursorText) readCursorText()
            command(arrow)
            if (next?.isEmpty() != true) rereadCursorText = strip?.gesture?.cursorStepped(direction) == true
        }
    }

    /** Selects the word at the cursor, or does nothing between words. */
    private fun selectWord() = changeSelection { text, start, end, _ ->
        val word = WordSelection.at(text, start, end)
        haptics.play(Haptic.WordPicked)
        if (word == null) return@changeSelection null
        strip?.gesture?.wordText(text.subSequence(0, word.first), word.second - word.first, text.subSequence(word.second, text.length))
        strip?.gesture?.wordSelected(SystemClock.uptimeMillis())
        word
    }

    /** Selects the word at the cursor, the part that a word drag keeps selected. Between words the drag starts from the cursor. */
    private fun startWordDrag() {
        wordAnchor = null
        changeSelection { text, start, end, offset ->
            val word = WordSelection.at(text, start, end) ?: (start to end)
            wordAnchor = offset + word.first to offset + word.second
            strip?.gesture?.wordText(text.subSequence(0, word.first), word.second - word.first, text.subSequence(word.second, text.length))
            word
        }
    }

    /** Extends the selection by one word, in order with the rest of the typing. */
    private fun extendWords(direction: Int) = enqueue {
        changeSelection { text, start, end, offset ->
            val anchor = wordAnchor ?: return@changeSelection null
            val range = WordSelection.extend(text, start, end, anchor.first - offset, anchor.second - offset, direction)
            if (range == start to end) return@changeSelection null
            // The word that this step took or gave back.
            val moved = if (range.second != end) text.subSequence(minOf(end, range.second), maxOf(end, range.second))
                else text.subSequence(minOf(start, range.first), maxOf(start, range.first))
            haptics.play(Haptic.SelectionWord)
            strip?.gesture?.wordStep(direction)
            range
        }
    }

    /**
     * Selects the range that [choose] picks from the text around the selection, or leaves it if [choose] returns null.
     * [choose] gets the text, the selection in that text, and where that text starts in the field.
     */
    private fun changeSelection(choose: (CharSequence, Int, Int, Int) -> Pair<Int, Int>?) {
        composer.command(destructive = false, fieldLocal = true) {
            val connection = currentInputConnection ?: return@command false
            connection.finishComposingText()
            val around = connection.getSurroundingText(WORD_REACH, WORD_REACH, 0) ?: return@command false
            val offset = fieldOffset(connection, around) ?: return@command false
            val (start, end) = choose(around.text, around.selectionStart, around.selectionEnd, offset) ?: return@command true
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

    /** Types a symbol or emoji from the panel, then goes back to normal typing. */
    private fun typePanelItem(item: String) {
        if (field.exact) commitExact(item) else composer.symbol(item)
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
        QuickSetting.ScrollAssistant -> !TypingSettings.touchpadOnlyInFields(this)
        QuickSetting.OpenSettings, QuickSetting.ChangeKeyboard, QuickSetting.Clipboard -> true
    }

    private fun writeQuickSetting(item: QuickSetting, on: Boolean) = when (item) {
        QuickSetting.Autocorrect -> TypingSettings.autocorrect(this, on)
        QuickSetting.SwipeTyping -> TypingSettings.swipeTyping(this, on)
        QuickSetting.SuggestionBar -> TypingSettings.suggestionBar(this, on)
        QuickSetting.AutoCapitalise -> TypingSettings.autoCapitalise(this, on)
        QuickSetting.AutoPunctuation -> TypingSettings.autoPunctuation(this, on)
        // Only the master switch: each haptic's own switch stays as it is, for when it is on again.
        QuickSetting.Vibration -> Haptics.enabled(this, on)
        QuickSetting.HoldSymbols -> TypingSettings.holdSymbols(this, on)
        QuickSetting.CursorControl -> TypingSettings.cursorControl(this, on)
        QuickSetting.DoublePressLocks -> TypingSettings.doublePressLocks(this, on)
        QuickSetting.ScrollAssistant -> TypingSettings.touchpadOnlyInFields(this, !on)
        QuickSetting.OpenSettings, QuickSetting.ChangeKeyboard, QuickSetting.Clipboard -> Unit
    }

    /** Pushing a key on the quick settings page. The change applies at once and the page stays open. Only the key that opens Settings leaves. */
    private fun useQuickSetting(item: QuickSetting) {
        when (item) {
            QuickSetting.OpenSettings -> {
                openSettings()
                closePanel()
            }
            QuickSetting.ChangeKeyboard -> {
                showKeyboardPicker()
                closePanel()
            }
            QuickSetting.Clipboard -> openClipboardPage()
            else -> {
                writeQuickSetting(item, !quickSettingOn(item))
                readSettings()
                panel?.render()
                KeyboardEvents.publish { KeyboardEvent.QuickSettingUsed(item, quickSettingOn(item)) }
                // Opening Settings, the keyboard list or the clipboard opens something you see, so only a switch plays.
                haptics.play(Haptic.QuickSetting)
            }
        }
    }

    /**
     * Shows Android's list of keyboards. The list can take the release of a key that is still down and the field stays
     * open, so nothing else would end that key. A key that stays down would stop the touchpad from typing.
     */
    private fun showKeyboardPicker() {
        contact.releaseKeys()
        modifiers.clear()
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    /** Opens the app at its Settings page, with the same extra that its notifications use to open a page. */
    private fun openSettings() {
        val app = packageManager.getLaunchIntentForPackage(packageName) ?: return
        startActivity(app.putExtra(OPEN_PAGE_EXTRA, "/settings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private val panelOpen get() = modifiers.panel != Modifiers.Panel.Closed

    /** The touchpad types nothing while Alt is on or a panel is on screen. */
    private val altOrPanel get() = modifiers.alt != Modifiers.Level.Off || panelOpen

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
        // You typed or swiped: the idle game's way home ends at once, and the words are back.
        if (strip?.idling == true) wakeIdleBar()
        Recording.sink?.outcome(outcome)
        inputLog()?.outcome(outcome)
        // The word menu keeps the touchpad for itself: a finger there only marks its buttons, and nothing types.
        if (menuOpen && !altOrPanel) return
        if (!altOrPanel) {
            dismissClipOffer()
            closeMenu()
        }
        when (outcome) {
            is Contact.Outcome.Swipe -> {
                showStripAfterBack()
                if (altOrPanel) {
                    if (panelOpen) swipePanel(outcome.points)
                    return
                }
                if (!field.words) return
                if (outcome.deletesWord) {
                    val nothingLeft = currentInputConnection?.getTextBeforeCursor(1, 0)?.isEmpty() == true
                    haptics.play(if (nothingLeft) Haptic.DeleteNothing else Haptic.DeleteFlick)
                    composer.backspace(true)
                    if (!nothingLeft) strip?.gesture?.wordDeleted(SystemClock.uptimeMillis())
                    KeyboardEvents.publish { KeyboardEvent.DeletedWord(held = false) }
                    return
                }
                composer.swipe(letterSpace(outcome.points).also { TouchSizes.attach(it, outcome.points) }, outcome.startedAt,
                    outcome.endedAt, modifiers.metaState)
                modifiers.consume()
            }
            is Contact.Outcome.Tap -> {
                KeyboardEvents.publish { KeyboardEvent.Tapped(outcome.key) }
                if (!isTapKey(outcome.key) || !field.words) return
                showStripAfterBack()
                val key = if (outcome.key == Layout.SPACE) " " else outcome.key
                // A touch that typed a key: a step, as light as a tap keyboard's, since the touchpad has no click of its own.
                haptics.play(Haptic.TapKey)
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
        if (maxOf(abs(across), abs(down)) >= Contact.PAGE_KEYS && panel?.swipe(across, down) == true) haptics.play(Haptic.PanelPage)
    }

    private fun isTapKey(key: String): Boolean {
        if (altOrPanel) return false
        return if (key == Layout.SPACE) ' ' in tapKeys else Layout.isLetter(key) && key[0] in tapKeys
    }

    /** Only a plain letter from the phone's own keyboard can become its symbol. With Shift, Alt, Sym or a shortcut key it stays a letter. */
    private fun canHold(event: KeyEvent): Boolean {
        // Every field that the keyboard types in has this, also password, number and exact typing fields.
        if (!holdSymbols || !editing || !HoldEngine.isLetter(event.keyCode) || deadKeyPending) return false
        if (!HoldEngine.arrivedInTime(event.downTime, SystemClock.uptimeMillis())) return false
        // A Shift that came on by itself still lets the first letter of a sentence become its symbol.
        if (modifiers.shift != Modifiers.Level.Off && !modifiers.autoShifted || altOrPanel || !event.hasNoModifiers() || event.isCapsLockOn) return false
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
        haptics.play(Haptic.HeldSymbol)
        KeyboardEvents.publish { KeyboardEvent.HeldSymbol(text) }
        if (field.exact) {
            // The letter went to the app as a key, so a Backspace key takes it back.
            logExact("hold ${letter.keyCode} gives its symbol")
            sendExact(letter, ExactTyping.takeBack)
            commitExact(text)
        } else if (watchingKeys) {
            val typed = letter.getUnicodeChar(letter.metaState)
            main.post { replaceAppLetter(typed, text) }
        } else if (pendingInput.isNotEmpty()) enqueue { composer.replaceLetter(text) } else composer.replaceLetter(text)
    }

    /** The app typed the letter itself, so the composer does not know it. It stays as it is unless it is still the last thing typed. */
    private fun replaceAppLetter(letter: Int, symbol: String) {
        if (!editing || !watchingKeys) return
        composer.command(destructive = true, fieldLocal = true) {
            val before = currentInputConnection?.getTextBeforeCursor(1, 0) ?: return@command false
            before.length != 1 || before[0].code != letter || editor.replace(1, symbol, 0)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        clearTeamsEgg()
        wakeIdleBar()
        if (event.repeatCount == 0) KeyboardEvents.publish { KeyboardEvent.Pushed(keyCode) }
        Recording.sink?.key(event, touchpad.isGestureKey(event))
        inputLog()?.key(event, touchpad.isGestureKey(event))
        if (KeyCapture.waiting?.invoke(event) == true) return true
        if (keyCode == KeyEvent.KEYCODE_BACK && hold.held && !editing) return heldBack(event, down = true)
        // Android hands back to a shown keyboard first, which would only close the strip.
        if (keyCode == KeyEvent.KEYCODE_BACK && editing && isInputViewShown) {
            backKey(event, down = true)
            return true
        }
        if (!editing || keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        typedInField = true
        if (touchpad.isGestureKey(event)) return true
        // Keys like volume reach the keyboard too, but they are not typing.
        if (!event.isSystem) showStripAfterBack()
        // An exact typing app can hide the keyboard by itself, as AnyDesk's own keyboard button does. A key brings the bar back.
        if (field.exact && !event.isSystem && !isInputViewShown && !hiddenByBack) {
            logExact("bar back for a key")
            main.post(showStrip)
        }
        // With the word menu open, the top two rows of letters pick its buttons, as the keys pick symbols on the Sym panel.
        if (!event.isSystem && menuOpen && keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val action = menuAction(('A' + (keyCode - KeyEvent.KEYCODE_A)).toString())
            if (action != null) {
                if (event.repeatCount == 0) {
                    menuKey = keyCode
                    stripAction(action)
                }
                return true
            }
        }
        // Typing on means the forgotten word can go, and the strip shows words again. The newest clip goes from the strip too.
        if (!event.isSystem && !Modifiers.isModifier(keyCode)) {
            closeMenu()
            commitForget()
            dismissClipOffer()
        }
        // The touchpad hears the physical press now, before the key is typed.
        contact.key(keyCode, true, event.eventTime)
        val carried = carryCtrl(event)
        // A key that gets here while the keys are watched has already told the hold timer about its press.
        val repeatOfHold = !watchingKeys && trackHold(carried)
        // A key press means the finger was pressing, not resting on the touchpad.
        endCursor()
        if (repeatOfHold) {
            if (field.exact) logExact("key $keyCode repeat ${event.repeatCount} kept by hold")
            return true
        }
        if (ctrlKey(event)) {
            consumedKeys += keyCode
            return true
        }
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
        // The release of a key that picked a menu button belongs to that pick.
        if (keyCode == menuKey) {
            menuKey = -1
            return true
        }
        if (KeyCapture.waiting?.invoke(event) == true) return true
        if (keyCode == KeyEvent.KEYCODE_BACK && hold.held && !editing) return heldBack(event, down = false)
        if (keyCode == KeyEvent.KEYCODE_BACK && editing && isInputViewShown) {
            // Back closes an open panel and stays in the app.
            if (!backKey(event, down = false)) {
                passingBack = true
                requestHideSelf(0)
            }
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
            if (field.exact) logModifiers(event)
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
        modifiers.key(event)
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
            if (field.exact) logModifiers(event)
            if (panelOpen && !isInputViewShown) main.post(showPanel)
            return true
        }
        if (panelKey(event)) return true
        if (field.exact) return exactKey(event)
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

    /**
     * With the panel on screen, a letter key types the item that it shows, and a key with no item, such as P, types nothing. Backspace and Enter
     * turn the page of symbols or emoji, do nothing on the quick settings page, and never reach the text. Space and fn can pick a category. Other
     * keys stay as they are.
     */
    private fun panelKey(event: KeyEvent): Boolean {
        if (event.repeatCount > 0 && event.keyCode == pickedKey) return true
        pickedKey = 0
        if (!panelOpen) return false
        if (modifiers.panel == Modifiers.Panel.Clipboard && isInputViewShown) {
            val key = when (event.keyCode) {
                KeyEvent.KEYCODE_DEL -> Layout.BACKSPACE
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> Layout.ENTER
                else -> null
            }
            if (key != null) {
                pickedKey = event.keyCode
                if (event.repeatCount == 0) clipKey(key)
                return true
            }
        }
        Modifiers.panelScroll(event.keyCode, modifiers.panel)?.let { rows ->
            pickedKey = event.keyCode
            if (event.repeatCount == 0 && rows != 0f && panel?.swipe(0f, rows) == true) haptics.play(Haptic.PanelPage)
            return true
        }
        if (!isInputViewShown || event.isCtrlPressed || event.isMetaPressed) return false
        if (!HoldEngine.isLetter(event.keyCode)) return categoryKey(event)
        if (event.repeatCount > 0) return true
        val letter = ('A' + (event.keyCode - KeyEvent.KEYCODE_A)).toString()
        if (modifiers.panel == Modifiers.Panel.Settings) QuickSetting.on(letter)?.let { item ->
            pickedKey = event.keyCode
            useQuickSetting(item)
        } else if (modifiers.panel == Modifiers.Panel.Clipboard) {
            pickedKey = event.keyCode
            clipKey(letter)
        } else if (letter == Layout.CLIPBOARD_KEY) {
            pickedKey = event.keyCode
            openClipboardPage()
        } else panel?.item(letter)?.let { item ->
            pickedKey = event.keyCode
            typePanelItem(item)
        }
        return true
    }

    /** With categories on the screen, Space and fn open the category at their place on the bottom row, and type nothing. */
    private fun categoryKey(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_SPACE -> Layout.SPACE
            Modifiers.FN -> Layout.FN
            else -> return false
        }
        if (panel?.hasCategories != true) return false
        if (event.repeatCount == 0 && panel?.pickCategoryUnder(key) == true) haptics.play(Haptic.PanelGroup)
        return true
    }

    /** False for any other key, and for a Ctrl + Enter that stays a shortcut for the app. */
    private fun enter(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_ENTER && event.keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) return false
        val info = currentInputEditorInfo
        // A locked Shift is Caps Lock, so Enter still sends.
        // A Shift that came on by itself at a sentence start is not Shift and Enter.
        val shift = event.isShiftPressed || modifiers.shift == Modifiers.Level.Once && !modifiers.autoShifted
        // The Enter key setting is postponed (issue 26) and has no row in Settings, so each field decides for now.
        val press = EnterKey.AppDecides.press(info?.inputType ?: 0, info?.imeOptions ?: 0,
            info?.actionId ?: 0, info?.packageName, shift, event.isCtrlPressed, enterApps)
        if (press == EnterKey.Press.FieldDecides && event.isCtrlPressed) return false
        // A held Enter must not send again before the app has cleared the field.
        val sends = press == EnterKey.Press.Send || press == EnterKey.Press.ShiftSend
        if (!sends || event.repeatCount == 0) composer.enter(press)
        if (press == EnterKey.Press.ShiftNewLine || press == EnterKey.Press.ShiftSend) modifiers.consume() else modifiers.useHeld()
        return true
    }

    /**
     * Exact typing: the key goes to the app as a key event, with the Shift and Alt pushed before it and Ctrl from fn.
     * A symbol that Alt types goes as text. Keys of the phone itself, such as volume, stay with Android. See [ExactTyping].
     */
    private fun exactKey(event: KeyEvent): Boolean {
        if (event.isSystem) return false
        val meta = KeyEvent.normalizeMetaState(event.metaState or modifiers.metaState)
        val plan = ExactTyping.plan(event.keyCode, event.repeatCount, meta, event::getUnicodeChar)
        logExact("key ${event.keyCode} repeat ${event.repeatCount} meta 0x${Integer.toHexString(meta)}" +
            if (plan.text != null) " as text from Alt" else "")
        plan.text?.let(::commitExact)
        sendExact(event, plan.events)
        modifiers.consume()
        return true
    }

    /** Sends [events] to the app, with the device, flags and source of the key [like]. */
    private fun sendExact(like: KeyEvent, events: List<ExactTyping.Event>) {
        val connection = currentInputConnection ?: return
        try {
            for (sent in events) {
                logExact("sent ${sent.code} ${if (sent.down) "down" else "up"} repeat ${sent.repeat} meta 0x${Integer.toHexString(sent.meta)}")
                connection.sendKeyEvent(KeyEvent(like.downTime, like.eventTime,
                    if (sent.down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, sent.code, sent.repeat, sent.meta, like.deviceId,
                    if (sent.code == like.keyCode) like.scanCode else 0, like.flags, like.source))
            }
        } catch (error: RuntimeException) {
            Log.w("Atlas", "Could not send an exact key", error)
        }
    }

    private fun commitExact(text: String) {
        try {
            currentInputConnection?.commitText(text, 1)
        } catch (error: RuntimeException) {
            Log.w("Atlas", "Could not send exact text", error)
        }
    }

    /** Only TitanKeys Debug writes these lines, to find faults in exact typing. They hold key codes and states, never text. */
    private val exactLogging by lazy { packageName.endsWith(".debug") }

    private fun logExact(line: String) {
        if (exactLogging) Log.i("AtlasExact", line)
    }

    /** Shift, Alt and Sym stay with the keyboard in exact typing. This says what they left on. */
    private fun logModifiers(event: KeyEvent) = logExact("modifier ${event.keyCode} ${if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"}" +
        " kept: shift ${modifiers.shift} alt ${modifiers.alt} panel ${modifiers.panel}")

    private fun command(event: KeyEvent, textKey: Boolean = false) {
        // A Shift that came on by itself is for a letter. Ctrl + C, Tab or an arrow must not become Ctrl + Shift + C or a selection.
        val sticky = if (modifiers.autoShifted && !textKey) modifiers.metaState and KeyEvent.META_SHIFT_MASK.inv() else modifiers.metaState
        val meta = KeyEvent.normalizeMetaState(event.metaState or sticky)
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

    /** Shift and Alt as last shown, so locking one plays its feel once. */
    private var shownShift = Modifiers.Level.Off
    private var shownAlt = Modifiers.Level.Off

    private fun showModifiers() {
        updateAutoShift()
        // Locked plays its feel, and so does unlocking, as a press alone doesn't tell you which it did. Once and off are key
        // presses, which the keys already click.
        val locked = { now: Modifiers.Level, shown: Modifiers.Level -> now == Modifiers.Level.Locked && shown != Modifiers.Level.Locked }
        val unlocked = { now: Modifiers.Level, shown: Modifiers.Level -> now != Modifiers.Level.Locked && shown == Modifiers.Level.Locked }
        if (locked(modifiers.shift, shownShift) || locked(modifiers.alt, shownAlt)) haptics.play(Haptic.ModifierLocked)
        else if (unlocked(modifiers.shift, shownShift) || unlocked(modifiers.alt, shownAlt)) haptics.play(Haptic.ModifierUnlocked)
        shownShift = modifiers.shift
        shownAlt = modifiers.alt
        strip?.visibility = if (panelOpen) View.GONE else View.VISIBLE
        showModifierBar()
        panel?.mode = modifiers.panel
        contact.paging = panelOpen
        updateKeyWatch()
        KeyboardEvents.publish { KeyboardEvent.ModifierState(modifiers.shift, modifiers.alt, modifiers.panel, modifiers.ctrlHeld) }
    }

    /**
     * Turns Shift on by itself where the next letter starts a sentence, only in fields that ask for sentence capitals and while the
     * setting is on. It looks like one push of Shift on the bar, and the first letter typed or swiped uses it up. Returns true if
     * Shift changed. While input still waits to reach the field, the text is not final, so nothing changes until it has.
     */
    private fun updateAutoShift(): Boolean {
        if (composer.hasPendingInput || pendingInput.isNotEmpty()) return false
        val before = modifiers.shift
        modifiers.autoShift(sentenceStartHere())
        return modifiers.shift != before
    }

    private fun sentenceStartHere(): Boolean {
        if (!editing || field.exact || !field.sentenceCapitalisation || panelOpen || !TypingSettings.autoCapitalise(this)) return false
        val before = currentInputConnection?.getTextBeforeCursor(SENTENCE_REACH, 0) ?: return false
        return SwipeTyping.startsSentence(before)
    }

    private fun showModifierBar() {
        strip?.modifiers = ModifierBar.of(modifiers, restingKey) { Keys.letter(it, KeyEvent.META_ALT_ON) }
    }

    override fun onDestroy() {
        commitForget()
        editing = false
        GuideHooks.panelRequests = null
        GuideHooks.stripDemos = null
        GuideHooks.stripPoints = null
        GuideHooks.clipSeeds = null
        // A held status must not outlive the keyboard.
        if (hold.held) hideHeldWindow()
        unregisterReceiver(screenOn)
        unregisterReceiver(screenOff)
        if (idleBarTriggerRegistered) {
            unregisterReceiver(idleBarTrigger)
            unregisterReceiver(movingTry)
            unregisterReceiver(stepTry)
        }
        sleepIdleBar()
        clipboard?.removePrimaryClipChangedListener(clipChanged)
        phoneNames.stop()
        modifiers.closePanel()
        panelKeys.clear()
        updateKeyWatch()
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
        BackService.touchpad(null)
        rawLog?.finish()
        rawLog = null
        composer.log = null
        super.onDestroy()
    }

    companion object {
        /** How long with no input before the idle scene starts on the bar. */
        private const val IDLE_BAR_MILLIS = 3 * 60 * 1000L
        /** The debug build starts the idle scene at once on this broadcast, from adb. */
        private const val IDLE_BAR_ACTION = "com.titankeys.debug.IDLE_BAR"
        private const val MOVING_ACTION = "com.titankeys.debug.MOVING_MILLIS"
        private const val STEP_ACTION = "com.titankeys.debug.STEP_MILLIS"
        private const val DEBUG_PACKAGE_SUFFIX = ".debug"
        /** The app opens the page in this extra when it starts. tools/checks/about.sh uses it to open About. */
        private const val OPEN_PAGE_EXTRA = "com.vandam.ink.notification.HREF"
        private const val SMART_LAUNCHER = "ginlemon.flowerfree"
        /** Characters read before the cursor to tell a sentence start. A sentence ends within a few spaces of the next one. */
        private const val SENTENCE_REACH = 64
        private const val MAX_PENDING_INPUT = 128
        private const val EMOJI_PREFERENCES = "emoji"
        private const val RECENT_EMOJI = "recent"
        /** Delayed hides from the previous app arrived within 25 ms of the new field starting. */
        private const val STALE_HIDE_MILLIS = 500L
        /** How long Android has to show the strip before the keyboard shows its window itself. */
        private const val SHOW_FALLBACK_MILLIS = 300L
        /** A show this soon after a hide is the app's quick hide and show. Teams took 160 to 530 ms. */
        private const val BOUNCE_MILLIS = 1000L
        /**
         * How long the hide and show waits after a tap, or after the app's own quick hide and show. Teams cancels its own show at
         * once. When Teams hides and shows the keyboard by itself, its hide calls this wait off and its show starts it again.
         * Tuned on the phone: 300 ms worked every time, as did everything up to 500 ms.
         */
        private const val RECYCLE_DELAY_MILLIS = 300L
        /** How long the strip stays hidden, from the moment its window has gone. Tuned on the phone: none is needed. */
        private const val RECYCLE_GAP_MILLIS = 0L
        /** How long the strip shows its word on Teams. */
        /** How long the word on Teams shows. */
        private const val TEAMS_EGG_MILLIS = 500L
        private const val EGG_PREFERENCES = "easter_egg"
        private const val EGG_BOOT_COUNT = "teams_boot_count"
        private const val EGG_BOOT_STARTED = "teams_boot_started"
        /** If the window has not gone by then, the strip comes back anyway. */
        private const val RECYCLE_HIDE_TIMEOUT_MILLIS = 1000L
        /**
         * Apps where a quick hide and show leaves the message box under the strip even after the nudge, so the strip hides and shows
         * again once. Add an app here when the nudge alone is seen not to help in it.
         */
        private val BOUNCE_APPS = setOf("com.microsoft.teams")
        /** The nudge waits until the app's own requests after its show are done. Teams asked again after 900 ms to 1.4 s. */
        private const val NUDGE_DELAY_MILLIS = 1600L
        /** How long the strip stays one pixel taller. */
        private const val NUDGE_MILLIS = 300L
        /** How long the window above the notification shade stays after a field ends. */
        private const val HIGH_GRACE_MILLIS = 2000L
        private const val CLIP_FILE = "clipboard-history"
        private const val CLIP_PREFERENCES = "clipboard"
        private const val CLIP_HINT_SHOWN = "hint_shown"
        /** How long after a copy the strip offers the newest clip. */
        private const val CLIP_OFFER_MILLIS = 60_000L
        /** How long "Saved" shows on the strip after Save in a word's menu. */
        private const val SAVED_NOTICE_MILLIS = 2000L
        /** A selection longer than this is no single word. */
        private const val MAX_SELECTED_WORD = 32
        /** One word: letters, and apostrophes between them, as in "Lok" or "don't". */
        private val SELECTED_WORD = Regex("\\p{L}+(['’]\\p{L}+)*")
        /** How long the strip offers Undo after you hold a word to forget it. */
        private const val FORGET_UNDO_MILLIS = 4000L
        /** The field of a returning window started within 30 ms of its empty start. */
        private const val NO_FIELD_HIDE_MILLIS = 200L
        private const val FRAME_MILLIS = 16L
        /** How long a finger must stay on the bottom row to open the group under it. A resting finger's touches last about 40 ms. */
        private const val GROUP_REST_MILLIS = 250L
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
