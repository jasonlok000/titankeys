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
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.KeyCharacterMap
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.SurroundingText
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.atlas.connected.Connected
import com.atlas.connected.TouchSizes
import com.atlas.engine.Calibration
import com.atlas.engine.EmojiPages
import com.atlas.engine.HoldEngine
import com.atlas.engine.Swipe
import com.atlas.engine.SwipeTyping
import com.atlas.engine.WordCase
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
    /** Bar text size when the bar was made. */
    private var viewSize = FixedScale.Size.Normal
    private lateinit var haptics: Haptics
    /** The cursor plays gone once each time it reaches an end of the text. */
    private val cursorSteps = CursorSteps()
    /** Whether the stroke crunches as a delete flick: see [FlickCrunch]. */
    private val flickCrunch = FlickCrunch()
    /** Selecting and a held delete hit their walls by the same rule as steering's ends: see [WallHits]. */
    private val selectionWall = WallHits()
    private val deleteWall = WallHits()
    /** Where the cursor was before a Space gesture selected words, so a double space can put it back, or null. */
    private var cursorBeforeSelect: Int? = null
    private lateinit var composer: Composer
    private lateinit var autocorrect: AutocorrectLoader
    private lateinit var phoneNames: PhoneNameSource
    private lateinit var editor: ConnectionEditor
    private lateinit var overlay: TouchpadOverlay
    private val contact = Contact()
    private val deleteHold = DeleteHold(contact)
    /** The held delete's words, so the lab can number each one's hit. */
    private val heldWords = HeldDeleteCount()
    private val cursor = CursorMode()
    private val cursorGesture = CursorGesture()
    /** The finger's path through cursor mode, drawn on the home screen's keyboard like a swipe. */
    private var cursorPath = ArrayList<Sample>()
    /** The third touch of a triple tap is still down: its pick's mark stays on the bar until it lifts. See [pickOnLand]. */
    private var tripleDown = false
    /** The touch of a Space gesture registered before any slide, so the strip shows it and its feel has played. */
    private var gestureRegistered = false
    /** This touch's Space gesture found nothing to work on, played its no, and does nothing more. See [nothingToPick]. */
    private var gestureEmpty = false
    /** This touch's gesture was refused by the guide's slide, which has said so: the rest of the touch says nothing more. */
    private var refused = false
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
    private var lastSelectionEnd = -1
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
    /** In Smart Launcher's search field the Back service hears the keys first and takes them for the keyboard. See [updateKeyWatch]. */
    private var watchingKeys = false
    private val panelKeys = PanelKeyFilter()
    /** The keys taken from Smart Launcher's search field for the keyboard. See [updateKeyWatch]. */
    private val takenKeys = TakenKeys()
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

    // Apps on Android's newer back handling ignore a back key from a keyboard, so a system back goes first.
    private val sendBack = Runnable { if (!BackService.back() && editing) sendDownUpKeyEvents(KeyEvent.KEYCODE_BACK) }

    private val confirmDoubleTap = Runnable {
        if (!cursorGesture.confirm()) return@Runnable
        Recording.sink?.gesture("double", SystemClock.uptimeMillis(), "")
        finishHeldTaps(type = false)
        // A guide slide that doesn't teach it yet: the taps glowed as they counted, and now the slide says it's blocked.
        if (inGuide && !guideAllows(GuideLesson.Cursor)) {
            guideBlocked(GuideLesson.Cursor)
            return@Runnable
        }
        KeyboardEvents.publish { KeyboardEvent.DoubleTapped(2) }
        // With text selected, a double space puts the cursor back, as steering would start from there.
        if (collapseSelection()) {
            haptics.play(Haptic.Steer)
            return@Runnable
        }
        rememberCursor()
        GestureDisplay.selectionEnd = SelectionEnd.stored(Haptics.selectionEndId(this))
        // A terminal or a remote view has no text to select here.
        if (field.exact) haptics.play(Haptic.WordPicked) else enqueue { selectWord() }
    }

    private val checkHeldTaps = Runnable { settleHeldTaps() }

    /**
     * Three taps picked the word as the third landed; once no fourth touch follows, the taps are over: none of them types, and
     * the next touch is a first tap again.
     */
    private val confirmTriple = Runnable {
        if (cursorGesture.confirmTriple()) finishHeldTaps(type = false)
    }

    /** Two taps above Space pick nothing; the guide says so, once no third tap follows. Only in its own field. */
    private val confirmTwoAbove = Runnable {
        if (!inGuide || !cursorGesture.confirmTwoAbove()) return@Runnable
        // Where the slide doesn't teach Space gestures yet, they're blocked, and the slide says so.
        if (guideBlocked(GuideLesson.Cursor)) return@Runnable
        // A no, so the taps that did nothing are felt as nothing. Everywhere else they stay quiet.
        haptics.play(Haptic.NothingToPick)
        KeyboardEvents.publish { KeyboardEvent.TwoTapsAbove }
    }

    /** Deletes the next word of a held delete flick. Only one edit is on its way at a time, so a lift leaves no words waiting. */
    private val stepDeleteHold = Runnable {
        val free = pendingInput.isEmpty() && !composer.hasPendingInput
        val next = deleteHold.step(SystemClock.uptimeMillis(), free, ::deleteHeldWord)
        if (next != null) nextDeleteHoldStep(next) else deleteDots()
    }

    /** The hold ended: its lines turn into dots, which tick as each lands in the caret while those same dots are on the bar. */
    private fun deleteDots() {
        val gesture = strip?.gesture ?: return
        gesture.speedUp = Haptics.dotsSpeedUp(this)
        if (!gesture.deleteEnded(SystemClock.uptimeMillis())) return
        val scale = if (GuideMotion.enabled) ValueAnimator.getDurationScale() else 0f
        val dotsAt = gesture.dotsAt
        haptics.holdDots(gesture.dotLandings(scale)) { dotsAt != null && strip?.gesture?.dotsAt == dotsAt }
    }

    private fun nextDeleteHoldStep(at: Long) {
        main.removeCallbacks(stepDeleteHold)
        main.postAtTime(stepDeleteHold, at)
    }

    /** The finger moved, so the wait for a hold starts again, or ends if the finger is no longer on a delete flick. */
    private fun followDeleteHold() {
        val start = contact.points.firstOrNull()
        val at = deleteHold.moved(!altOrPanel && start != null && pieces.canDelete(start.time))
        main.removeCallbacks(blockDeleteHold)
        when {
            at == null -> main.removeCallbacks(stepDeleteHold)
            // A guide slide that doesn't teach the hold, or the flick, yet: the hold never starts, its glow shows it was felt, and
            // the guide says it's blocked as it would have started (owner, 2026-11-13: blocked, not ignored). A flick the slide
            // teaches still deletes its one word at the lift.
            (!guideAllows(GuideLesson.Flick) || !guideAllows(GuideLesson.DeleteHold)) && !contact.deleting -> {
                main.removeCallbacks(stepDeleteHold)
                main.postAtTime(blockDeleteHold, at)
            }
            else -> nextDeleteHoldStep(at)
        }
    }

    /** A held delete that the guide's slide blocks: said once each time the finger stays, as the flick's if that's locked too. */
    private val blockDeleteHold = Runnable { refuse(if (guideAllows(GuideLesson.Flick)) GuideLesson.DeleteHold else GuideLesson.Flick) }

    private fun endDeleteHold() {
        main.removeCallbacks(stepDeleteHold)
        main.removeCallbacks(blockDeleteHold)
        deleteHold.reset()
        heldWords.reset()
        deleteDots()
    }

    /** Each word goes the way of a normal delete flick. [flick] is the first word's flick, and is null for the words after it. */
    private fun deleteHeldWord(flick: Contact.Outcome.Swipe?) {
        if (flick != null) {
            // A new hold, so its words count from 1 again.
            heldWords.reset()
            // A hold takes the bar from any dots still playing, and their ticks stop.
            haptics.cutDots()
            // The contact was a delete and not part of a word, as it would be at a lift.
            pieces.clear()
            lastTouch = null
            Trace.touch(emptyList(), true)
            enqueue {
                // With nothing to delete, no hold starts: no words on the bar, no dots, only the flick's wall (owner, 2026-11-12).
                val before = currentInputConnection?.getTextBeforeCursor(FLICK_REACH, 0)
                if (before?.isEmpty() != true) {
                    strip?.gesture?.deleteHeld(deleteHold.repeatMillis, SystemClock.uptimeMillis(), deleteHold.pace, before)
                    // The dots at the lift keep clear of this hold's ticks.
                    haptics.holdStarted()
                    // The guide wants to know that this delete stays down, before its first word goes.
                    KeyboardEvents.publish { KeyboardEvent.DeleteHoldStarted }
                }
                outcome(flick)
            }
            return
        }
        val word = heldWords.next()
        enqueue {
            // At the start of the text the hold hits a wall: the same wall as a flick with nothing to delete, played clean, once,
            // and the hold stays quiet while the finger stays, with no words left on the bar, only the caret.
            if (currentInputConnection?.getTextBeforeCursor(1, 0)?.isEmpty() == true) {
                if (deleteWall.reached()) haptics.wall(Haptic.deleteFoundNothing(tookAny = true))
                strip?.gesture?.deleteText("")
                return@enqueue
            }
            deleteWall.left()
            haptics.heldDeleteWord(word, deleteHold.pace, deleteHold.repeatMillis)
            // A dot for each character of the word, for the dots that play when the hold lifts, and the real words for the bar.
            val before = currentInputConnection?.getTextBeforeCursor(FLICK_REACH, 0)
            before?.let { strip?.gesture?.deleteText(it) }
            composer.backspace(true)
            strip?.gesture?.wordDeleted(SystemClock.uptimeMillis(), GestureDisplay.letters(before))
            KeyboardEvents.publish { KeyboardEvent.DeletedWord(held = true) }
        }
    }

    private fun startCursor(drag: CursorGesture.Drag, samples: List<Sample>) {
        if (strip?.gesture?.softDelete == true) interruptGesture()
        if (inGuide && !guideAllows(GuideLesson.Cursor)) {
            refuseSpaceGesture()
            return
        }
        // A slide before the hold registered still steers nothing where there is no text. Selecting was decided on landing.
        if (!gestureEmpty && !gestureRegistered && !drag.words && nothingHere(words = false)) nothingToPick()
        if (gestureEmpty) {
            contact.cancelActive()
            return
        }
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
        stepCursor.reset(drag.words)
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
        // A triple press registered as it landed, in [pickOnLand]; once it has stayed down this long it glows as held, every time,
        // whether or not a slide follows (owner, 2026-11-12).
        if (taps >= 3 && tripleDown && !gestureEmpty) strip?.gesture?.pickHeldGlow(now)
        if (taps == 0 || gestureRegistered || gestureEmpty) return
        val words = taps >= 3
        // A guide slide that doesn't teach steering or selecting yet: the bar shows it registered, then the slide refuses it.
        if (inGuide && !guideAllows(GuideLesson.Cursor)) {
            if (words) readWordsAroundCursor() else readCursorText()
            strip?.gesture?.cursorFrame((gestureLanded?.x ?: .5f) * 10, 0, 0f, words, now)
            refuseSpaceGesture()
            return
        }
        // Selecting was decided as the touch landed, in [pickOnLand]. Steering with no text at all does nothing but say no.
        if (!words && nothingHere(words = false)) {
            nothingToPick()
            return
        }
        gestureRegistered = true
        Recording.sink?.gesture("registered", now, "taps=$taps")
        haptics.play(Haptic.registered(words, nothing = false))
        // With text selected, steering starts from where the cursor was before the select, or the selection's end.
        if (!words && collapseSelection()) enqueue { readCursorText() }
        else if (words) readWordsAroundCursor() else readCursorText()
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

    /**
     * Two quick taps, then a third touch lands: the word in front of the cursor is selected at once, with no wait for a lift or a
     * slide (owner, 2026-11-11). A slide from there takes more words, starting from this one.
     */
    private fun pickOnLand() {
        Recording.sink?.gesture("triple", SystemClock.uptimeMillis(), "")
        // A guide slide that doesn't teach it yet: the taps glowed as they counted, and the third is refused as it lands.
        if (inGuide && !guideAllows(GuideLesson.Cursor)) {
            refuseSpaceGesture()
            return
        }
        // The mark at the selection's moving end, as the Haptics lab tried it in TitanKeys Debug.
        GestureDisplay.selectionEnd = SelectionEnd.stored(Haptics.selectionEndId(this))
        gestureRegistered = true
        rememberCursor()
        // The bar keeps the pick's mark while this touch stays down, and shows the selected text after the lift.
        tripleDown = true
        main.removeCallbacks(confirmDoubleTap)
        finishHeldTaps(type = false)
        KeyboardEvents.publish { KeyboardEvent.DoubleTapped(3) }
        when {
            field.exact -> haptics.play(Haptic.WordPicked)
            // No word in front of the cursor: no selection, no word drag, only a no.
            nothingHere(words = true) -> nothingToPick()
            else -> {
                // Typing still on its way decides it later: with no word then, the rest of this touch does nothing too.
                val landed = gestureLanded
                enqueue { selectWord(inFront = true) { if (gestureLanded === landed) nothingLater() } }
            }
        }
    }

    /**
     * True where a Space gesture has nothing to work on: no word to pick for [words], or no text at all to steer through. It
     * reads the field now, as the feel plays now. With typing still on its way, or a field that doesn't answer, there is text.
     */
    private fun nothingHere(words: Boolean): Boolean {
        if (field.exact || pendingInput.isNotEmpty() || composer.hasPendingInput) return false
        val connection = currentInputConnection ?: return false
        // With text selected, a double space puts the cursor back and steers from there, even when the selection is all the
        // text: that is steering starting, never a no (owner, 2026-11-12).
        if (!words && lastSelectionStart >= 0 && lastSelectionStart != lastSelectionEnd) return false
        if (!words) {
            val before = connection.getTextBeforeCursor(1, 0) ?: return false
            val after = connection.getTextAfterCursor(1, 0) ?: return false
            return !WordSelection.anyToSteer(before, after)
        }
        val around = connection.getSurroundingText(WORD_REACH, WORD_REACH, 0) ?: return false
        return !WordSelection.anyToPick(around.text, around.selectionStart, around.selectionEnd, inFront = true)
    }

    /**
     * Taps on or above Space found nothing to work on. Nothing happens, not even typing, and the glow goes: only the no plays,
     * once, so you know it didn't fire because there's nothing there (owner, 2026-11-11).
     */
    private fun nothingToPick() {
        haptics.play(Haptic.NothingToPick)
        nothingLater()
    }

    /** The rest of a touch that found nothing does nothing. Its no has played. */
    private fun nothingLater() {
        Recording.sink?.gesture("nothing", SystemClock.uptimeMillis(), "")
        gestureEmpty = true
        gestureRegistered = true
        main.removeCallbacks(registerGestureLater)
        contact.cancelActive()
        finishHeldTaps(type = false)
        strip?.gesture?.glowOff()
        if (cursor.active) endCursor()
    }

    /** Space set as a tap key would type the first tap of a double tap, so a tap waits until it can't be one. */
    private fun holdTap(finish: (Boolean) -> Unit) {
        heldTaps += finish
        main.removeCallbacks(checkHeldTaps)
        main.postDelayed(checkHeldTaps, CursorGesture.CONFIRM_MILLIS)
    }

    /** Types the held taps once they can't be a gesture any more, or looks again soon, so a held tap never waits for the next touch. */
    private fun settleHeldTaps() {
        if (heldTaps.isEmpty()) return
        if (!cursorGesture.deciding(SystemClock.uptimeMillis())) return finishHeldTaps(type = true)
        main.removeCallbacks(checkHeldTaps)
        main.postDelayed(checkHeldTaps, HELD_TAP_LOOK_MILLIS)
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

        /** A fresh lean of the cursor steps at once. A word drag starts from the picked word and waits for its first lean. */
        fun reset(words: Boolean = false) {
            owed = if (words) 0f else 1f
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
        // The word menu, the Sym panel and the swipe's path all line up with the keys as Key calibration says.
        KeyCalibration.load(this)
        Connected.start(this)
        // A public release has no typing log: an old one from 2.0 goes, as nothing can show or export it any more.
        Thread {
            runCatching { RawLog.removeLeftovers(this) }
            // The word menu's first-time hint is gone (owner, 2026-11-12), and so is the count it kept.
            runCatching { deleteSharedPreferences("word_menu") }
        }.start()
        haptics = Haptics(this)
        loadClips()
        clipboard?.addPrimaryClipChangedListener(clipChanged)
        touchpad = Touchpad(getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY))
        editor = ConnectionEditor(::getCurrentInputConnection, ::getCurrentInputEditorInfo,
            { delay, action -> main.postDelayed(action, delay) }, { enterApps })
        composer = Composer(editor, EngineWords(SwipeTyping.get(this), FavouriteWords.get(this)::contains),
            { delay, action -> main.postDelayed(action, delay) }, ::clearSettlements,
            // The automatic Shift decides a sentence's capital, so the composer adds none of its own on top. See [updateAutoShift].
            { modifiers.autoShifted }) { words ->
                ownBarWords = words
                showBarWords()
            }
        composer.autoPunctuation = { TypingSettings.autoPunctuation(this) }
        composer.sentenceSpaces = {
            when (TypingSettings.sentenceSpaces(this)) {
                "two" -> 2
                "none" -> 0
                else -> 1
            }
        }
        composer.commaSpace = { TypingSettings.commaSpace(this) }
        composer.sentenceCapitals = { TypingSettings.autoCapitalise(this) }
        composer.fixOn = { kind -> TypingSettings.fixOn(this, kind.id) }
        composer.onStroke = Trace::stroke
        // Backspace putting a word back plays nothing: the key press is felt already.
        composer.onAutocorrect = { haptics.play(Haptic.Autocorrect) }
        composer.onFixed = { typed, word -> KeyboardEvents.publish { KeyboardEvent.Fixed(typed, word) } }
        composer.onExpanded = { haptics.play(Haptic.Expansion) }
        composer.nearMiss = { right -> GuideScript.slip(right).takeIf { GuideHooks.takeNearMiss(currentInputEditorInfo?.privateImeOptions) } }
        composer.intended = { before -> GuideHooks.intended(currentInputEditorInfo?.privateImeOptions, before) }
        composer.forceFix = { GuideHooks.forceFix && currentInputEditorInfo?.privateImeOptions?.contains(GuideHooks.FIELD) == true }
        composer.guidePlaced = { place, word ->
            GuideHooks.rightPlace = place
            GuideHooks.rightWord = word
        }
        // The guide's slides show the bar's side words, or only its middle one, and point at a word wherever it sits.
        GuideHooks.barSideRequests = { show -> main.post { if (!show && !inGuide) return@post else strip?.sides = show } }
        GuideHooks.stripPointWords = { word -> main.post { if (word == null || inGuide) strip?.pointWord(word) } }
        // The word menu slide's bar: only its word, to hold. Null gives the bar its own words back.
        GuideHooks.barWordRequests = { word -> main.post {
            guideBarWord = word
            showBarWords()
        } }
        // The word menu slide's word, underlined the whole slide. Posted after the word, so the light finds it on the bar.
        GuideHooks.barLitRequests = { index -> main.post {
            guideLit = index
            showBarWords()
        } }
        composer.onRestored = {
            haptics.play(Haptic.AutocorrectUndone)
            KeyboardEvents.publish { KeyboardEvent.Restored }
        }
        // The guide asks whether the word menu is open, so its demos keep off it, and closes it as a slide changes.
        GuideHooks.menuOpen = { inGuide && menuOpen }
        // A slide changing closes the menu, and its words start over on the next slide.
        GuideHooks.menuCloses = { main.post { if (inGuide) {
            closeMenu()
            guideMenus.clear()
        } } }
        // The guide asks whether the bar has words to hold, for its word menu slide. Only in its own field.
        GuideHooks.barCount = { if (inGuide) barWords.size else -1 }
        GuideHooks.barHolds = { millis -> main.post { if (inGuide) strip?.holdWords(millis) } }
        // The interactive guide acts out a gesture on this bar, only in its own field. See [Strip.playDemo].
        GuideHooks.stripDemos = { demo ->
            main.post {
                if (demo == null) strip?.stopDemo()
                else if (editing && currentInputEditorInfo?.privateImeOptions?.contains(GuideHooks.FIELD) == true) strip?.playDemo(demo)
            }
        }
        // The guide's clipboard lesson shows its own clips, in a sandbox: the user's clips don't show, and nothing is saved or
        // pinned. Only in its own field. Empty ends the sandbox.
        GuideHooks.clipSeeds = { clips ->
            main.post {
                if (clips.isEmpty()) {
                    guideClips = null
                    guideFlashClip = null
                } else {
                    val now = System.currentTimeMillis()
                    guideClips = ClipboardHistory().apply { for (text in clips.reversed()) add(text, now) }
                    // The right one to paste, the last, pulses to show which one to pick (owner, 2026-11-13).
                    guideFlashClip = clips.lastOrNull()
                }
                showClips()
            }
        }
        GuideHooks.clipDismissals = { main.post { if (inGuide) dismissClipOffer() } }
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
            registerReceiver(panelTrigger, IntentFilter(PANEL_ACTION), RECEIVER_EXPORTED)
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

    // The idle games on the bar (an Easter egg, see IdlePlayer): an idle period starts with each key, touch or swipe, a new field or
    // the keyboard opening, and the screen coming back on. Exactly three minutes in, while plain words show, it rolls once, and
    // that's all for the period, however long it lasts (owner, 2026-11-13: "it triggers only on the 3 min mark of idle. Not
    // resetting."). Any input ends a game at once. It stays off with exact typing, in apps where the bar hides, in fields with no
    // suggestions, with the bar or the screen off, with the Sym panel open, and with Android's animations off.

    /**
     * The idle period's one roll, at [IDLE_BAR_MILLIS]: most times nothing plays, and nothing more rolls until the next period; see
     * [IdlePlayer.PLAY_CHANCE] and [IdleSchedule] (no game in the period after one that played, never the same game twice running).
     */
    private val idleBar = Runnable {
        if (!idleClock.roll(SystemClock.uptimeMillis())) return@Runnable
        val random = Random(System.nanoTime())
        if (idleSchedule.trigger(random)) startIdleBar(random = random, schedule = idleSchedule)
    }
    private val idleClock = IdleClock()
    private val idleSchedule = IdleSchedule()
    private var idleBarTriggerRegistered = false
    /**
     * The debug build's trigger, from adb. It always starts a game, with no roll of the chance, and it doesn't count toward
     * [IdleSchedule]'s rules: a demo neither keeps the next period from playing nor keeps its game from playing next. With no
     * extra the game is picked by rarity among the ones the letters fit. The extra "game" picks one by name or by the demo's
     * number (1 drift, 2 pong, 3 bowling, 5 rope, 6 firework, 7 race, 8 juggle), if the letters fit it; the extra "return" sends the
     * running game home by one way (shell, firework, drop, exit), as coming back would.
     */
    private val idleBarTrigger = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val game = intent.getStringExtra("game")?.lowercase()
            val way = intent.getStringExtra("return")?.lowercase()
            main.post {
                if (way != null) {
                    val chosen = IdlePlayer.Way.entries.firstOrNull { it.name.lowercase() == way || it.number.toString() == way && it.number > 0 }
                    val sent = chosen != null && strip?.comeBackFromIdle(chosen) == true
                    Log.i("Atlas", if (sent) "Idle bar: going home by $chosen" else "Idle bar: no game is playing, or no way home is called $way")
                    return@post
                }
                val kind = game?.let { name ->
                    IdlePlayer.Kind.entries.firstOrNull { it.name.lowercase() == name || it.number.toString() == name || name == "rope" && it == IdlePlayer.Kind.JumpRope }
                }
                if (game != null && kind == null) {
                    Log.i("Atlas", "Idle bar: no game called $game. Shell (4) is a way home: use the extra return")
                    return@post
                }
                // A game that plays now starts again as the one asked for.
                strip?.stopIdle()
                val started = startIdleBar(kind)
                Log.i("Atlas", if (started) "Idle bar: started ${kind ?: "a game picked by rarity"}"
                    else "Idle bar: not started, as no plain words show, the letters do not fit ${kind ?: "any game"}, or it is off here")
            }
        }
    }
    /** The modifier keys that the debug build's panel check holds down, to let go at the next check. */
    private val debugHeld = ArrayList<Int>()

    /**
     * The debug build's panel check, from adb, for screenshots of what adb cannot press: Sym, a resting finger, a held modifier.
     * While a field has the keyboard up, the extra "page" opens symbols, emoji, clipboard or quick, and "none" closes the panel.
     * The extra "mark" is a key code (29 for A to 54 for Z, 67 Backspace, 66 Enter, 57 Alt, 63 Sym, 62 Space): the key that a
     * resting finger marks, drawn as a real one is, on the panel and in the bar. The extra "mod" holds ctrl, shift or alt down
     * until the next check. Each check first lets go of what the last one held. With the panel open, Shift and Alt stay off, as on
     * the real keyboard.
     */
    private val panelTrigger = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val page = intent.getStringExtra("page")?.lowercase()
            val mark = if (intent.hasExtra("mark")) intent.getIntExtra("mark", -1) else null
            val mod = intent.getStringExtra("mod")?.lowercase()
            main.post {
                if (!editing || !isInputViewShown) {
                    Log.i("Atlas", "Panel check: no field has the keyboard up")
                    return@post
                }
                val now = SystemClock.uptimeMillis()
                for (code in debugHeld) modifiers.up(code, now)
                debugHeld.clear()
                when (page) {
                    null -> Unit
                    "none", "closed" -> closePanel()
                    "symbols", "sym" -> { modifiers.openPanel(Modifiers.Panel.Symbols); showModifiers() }
                    "emoji" -> { modifiers.openPanel(Modifiers.Panel.Emoji); showModifiers() }
                    "quick", "settings" -> { modifiers.openPanel(Modifiers.Panel.Settings); showModifiers() }
                    "clipboard", "clip" -> {
                        if (!panelOpen) modifiers.openPanel(Modifiers.Panel.Symbols)
                        openClipboardPage()
                    }
                    else -> {
                        Log.i("Atlas", "Panel check: no page called $page. Use symbols, emoji, clipboard, quick or none")
                        return@post
                    }
                }
                val held = when (mod) {
                    null, "none" -> null
                    "ctrl" -> KeyEvent.KEYCODE_CTRL_LEFT
                    "shift" -> KeyEvent.KEYCODE_SHIFT_LEFT
                    "alt" -> KeyEvent.KEYCODE_ALT_LEFT
                    else -> { Log.i("Atlas", "Panel check: no modifier called $mod. Use ctrl, shift or alt"); null }
                }
                if (held != null) {
                    modifiers.down(held, now)
                    debugHeld += held
                }
                val key = mark?.let { Layout.keyForCode(it) }
                if (mark != null && key == null) Log.i("Atlas", "Panel check: key code $mark is no key of the layout")
                showModifiers()
                // A resting finger on [key], as the touchpad would report it: the panel's mark and the bar's. After the page is
                // open, since a new page starts with no mark.
                restingKey = key.orEmpty()
                panel?.hover = if (panelOpen) key.orEmpty() else ""
                showModifierBar()
                Log.i("Atlas", "Panel check: page ${modifiers.panel}, mark ${key ?: "none"}, held ${mod ?: "none"}")
            }
        }
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = sleepIdleBar()
    }

    // Exact typing and an app where the bar hides (both from Settings) keep it off, as do fields with no suggestions or fixes.
    private fun idleBarAllowed() = editing && isInputViewShown && !panelOpen && !field.exact && !barHidden && field.suggestions &&
        field.autocorrect && showSuggestions() && ValueAnimator.areAnimatorsEnabled() && getSystemService(PowerManager::class.java).isInteractive

    private fun startIdleBar(kind: IdlePlayer.Kind? = null, random: Random = Random(System.nanoTime()), schedule: IdleSchedule? = null) =
        idleBarAllowed() && strip?.startIdle(random, kind, schedule) == true

    /**
     * Input starts a new idle period. Coming back, with a finger on the touchpad or a touch on the bar, sends the idle game's letters
     * home; typing or swiping ([cut]) puts the words back at once. The period's one roll is [IDLE_BAR_MILLIS] from now.
     */
    private fun wakeIdleBar(cut: Boolean = true) {
        main.removeCallbacks(idleBar)
        if (cut) strip?.stopIdle() else strip?.comeBackFromIdle()
        // A demonstration from the guide gives the bar back as soon as you act.
        strip?.stopDemo()
        waitIdleBar()
    }

    /** A new idle period starts now: its one roll is exactly [IDLE_BAR_MILLIS] away, with no random extra. */
    private fun waitIdleBar() {
        main.removeCallbacks(idleBar)
        idleClock.input(SystemClock.uptimeMillis())
        if (idleBarAllowed()) main.postDelayed(idleBar, IDLE_BAR_MILLIS)
    }

    private fun sleepIdleBar() {
        main.removeCallbacks(idleBar)
        idleClock.stop()
        strip?.stopIdle()
    }

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            hold.screenOn()
            // The screen coming back on starts a new idle period, as the keyboard opening would.
            waitIdleBar()
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
    /** The guide's field is the one that this keyboard serves now. */
    private val inGuide get() = editing && currentInputEditorInfo?.privateImeOptions?.contains(GuideHooks.FIELD) == true
    /** The guide's field or the playground's: both draw the keys, so both see every touch and a key let go. Nothing else. */
    private val inPractice get() = editing && GuideHooks.practice(currentInputEditorInfo?.privateImeOptions)
    private var guideEmoji: EmojiPages? = null

    /** In the guide, the panel has its emoji to start from and keeps O shut; anywhere else it's as always. */
    private fun showGuideExtras() {
        val guide = inGuide
        panel?.blockedKeys = if (guide) setOf(QuickSetting.OpenSettings.key) else emptySet()
        val pages = if (guide) guideEmoji ?: emoji else emoji
        if (pages != null && panel?.emoji !== pages) panel?.emoji = pages
    }

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
            main.post {
                emoji = EmojiPages(groups, recents).also { panel?.emoji = it }
                // The guide's Recent is its own, cheeky and never with the clown in it, so the clown has to be found (owner, v5).
                guideEmoji = EmojiPages(groups, GUIDE_RECENT_EMOJI)
                showGuideExtras()
            }
        }.start()
    }

    override fun onCreateInputView(): View {
        // The bar, the panels and exact typing's bar are the same size at any display size or font size (FixedScale). Android
        // makes this view again when those settings change, so each time takes the screen as it is now.
        viewSize = FixedScale.size(this)
        val ui = FixedScale.themed(this, android.R.style.Theme_DeviceDefault_InputMethod)
        // Bar text size grows only what's in the bar, and the word menu's effects with it. The bar keeps its height.
        val barUi = FixedScale.themed(this, android.R.style.Theme_DeviceDefault_InputMethod, viewSize.factor)
        val words = Strip(barUi, { index ->
            // The guide's own word only opens its menu: a tap never types a word that the bar doesn't show.
            if (!guideBlocked(GuideLesson.BarTap) && shownGuideWord() == null) {
                haptics.play(Haptic.BarWord)
                enqueue { composer.choose(index) }
            }
        }, ::holdWord,
            { action -> enqueue { stripAction(action) } }, { enqueue { pasteOfferedClip() } }, { wakeIdleBar(cut = false) })
        // A game that ended by itself, or was sent home, gives the bar back: the next wait starts fresh, with the same roll.
        val keys = SymPanel(ui,
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
        words.lit(shownGuideLit())
        // Exact typing's bar starts hidden and is made before the first check, so a normal field never shows it.
        val exact = ExactBar(ui, ::exactChip, ::exactBarKey).apply { visibility = View.GONE }
        exactBar = exact
        words.showSuggestions = showSuggestions()
        showModifiers()
        val keyboard = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            addView(keys)
            addView(exact)
            addView(words)
            inputRoot = this
            roundTopCorners(this)
        }
        // The word menu's effects rise above the bar, over the app, in a see-through layer that takes no touch (ladder v4).
        val effects = MenuEffects(barUi, words)
        words.effects = effects
        words.menuBeat = ::menuBeat
        words.menuPress = { haptics.play(Haptic.MenuPress) }
        // The keyboard sits on the bottom of a window as tall as the screen. See [fillScreen].
        return FrameLayout(ui).apply {
            addView(keyboard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            addView(effects, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    /**
     * The keyboard's window is as tall as the screen and never changes size; the keyboard is drawn on its bottom edge. When the
     * window itself grew and shrank with the Sym panel, Android moved it a frame after the keyboard had drawn at its new height,
     * so for one frame the bar floated about 200 px up with nothing under it as the panel closed, and dropped below the screen
     * as it opened (owner, 2026-11-12). Now the panel and the bar change in one drawing, and [onComputeInsets] tells the app and
     * the touch screen where the keyboard is: the app gets only the keyboard's height, and touches above it reach the app.
     */
    private fun fillScreen() {
        val window = window.window ?: return
        if (window.attributes.height != ViewGroup.LayoutParams.MATCH_PARENT)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // Android's own frames around the keyboard wrap it; they must fill the window too, so it can sit on the bottom.
        val area = window.findViewById<View>(android.R.id.inputArea) ?: return
        fillHeight(area)
        fillHeight(area.parent as? View)
        fillHeight(inputRoot?.parent as? View)
    }

    private fun fillHeight(view: View?) {
        val params = view?.layoutParams ?: return
        if (params.height == ViewGroup.LayoutParams.MATCH_PARENT) return
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        view.layoutParams = params
    }

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        fillScreen()
    }

    override fun setInputView(view: View) {
        super.setInputView(view)
        fillScreen()
    }

    private val keyboardAt = IntArray(2)

    /** Only the keyboard counts, not the empty window above it: see [fillScreen]. */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val keyboard = inputRoot ?: return
        val decor = window.window?.decorView ?: return
        if (!isInputViewShown || !keyboard.isAttachedToWindow) return
        val top = if (keyboard.isShown && keyboard.height > 0) {
            keyboard.getLocationInWindow(keyboardAt)
            keyboardAt[1]
        } else decor.height
        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, top, decor.width, decor.height)
    }

    /**
     * The keyboard's top corners take the medium radius, the same as everything else in TitanKeys. The outline only clips what is
     * drawn, so nothing moves. It reaches past the bottom, where the screen's own corners do the rounding.
     */
    private fun roundTopCorners(view: View) {
        val radius = Radii.px(view.resources, Radii.MEDIUM)
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) =
                outline.setRoundRect(0, 0, view.width, view.height + radius.toInt(), radius)
        }
        view.clipToOutline = true
    }

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        main.post { showGuideExtras() }
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
        // TitanKeys's own Settings fields hold snippets and words to add, which must not be learned. The guide's field is for
        // practice, so it learns nothing either: the guide never changes your real words (owner, 2026-11-12).
        val ownSettings = info.packageName == packageName && !GuideHooks.practice(info.privateImeOptions)
        field = if (exact) Field.Exact else if (editing) Field.of(info.inputType, info.imeOptions) else Field.None
        if (info.packageName == packageName) field = field.copy(learning = false)
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
        if (!restarting) {
            sticky.clear()
        }
        updateExactBar()
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
        modifiers.altSymSwitches = TypingSettings.altSymNextKeyboard(this)
        cursorControl = TypingSettings.cursorControl(this)
        exactRealAlt = ExactTyping.realAlt(this)
        // A word gesture could send the wrong keys to a terminal or a remote computer, or a word into a password.
        contact.swipes = TypingSettings.swipeTyping(this) && field.words
        enterApps = EnterKey.apps(TypingSettings.enterApps(this))
        val corrects = TypingSettings.autocorrect(this)
        // Spell out shorthand is its own setting: with Fix typos off, the word data spells out shorthand alone (bug hunt #13).
        val shorthandAlone = !corrects && TypingSettings.expandShorthand(this)
        composer.autocorrect = autocorrect.autocorrect.takeIf { corrects }
        composer.shorthandOnly = autocorrect.autocorrect.takeIf { shorthandAlone }
        // The word data takes a moment to load, so it starts as a field opens: the first word typed is fixed too.
        if (!corrects && !shorthandAlone) autocorrect.release() else if (editing) autocorrect.prepare()
        strip?.showSuggestions = showSuggestions()
        readExpansions()
        updateKeyWatch()
    }

    /**
     * Smart Launcher takes every key press in its search field before the keyboard hears it, so the launcher typed its own letters
     * there and nothing of TitanKeys ran: no fixes, no Shift + Backspace, no Sym panel (owner, 2026-11-13). The Back service hears
     * the keys first in that field and takes them from the app, so the keyboard types there as in any other field. See [TakenKeys].
     * It also hears them while the Sym panel is open, and until the release of a key that the panel took. See [PanelKeyFilter].
     * After a first push of Back let go of the touchpad, it hears the second push go through. See [TouchpadHold].
     */
    private fun updateKeyWatch() {
        val launcher = editing && currentInputEditorInfo?.packageName == SMART_LAUNCHER
        val forPanel = panelOpen || panelKeys.holding
        val running = BackService.watchKeys(if (launcher || forPanel || takenKeys.holding || hold.lettingGo) ::watchedKey else null)
        watchingKeys = running && launcher
        if (!running) {
            panelKeys.clear()
            takenKeys.clear()
        }
    }

    /**
     * Hears a key before Smart Launcher does, and takes it from the launcher for the keyboard: [onKeyDown] and [onKeyUp] get it, as
     * they get a key in any other field. Every key waits for this answer.
     *
     * While the Sym panel is open, Enter and Backspace are kept from every app and go to the panel, the same as a key that the app
     * passed on to the keyboard.
     */
    private fun watchedKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        if (event.keyCode == KeyEvent.KEYCODE_SYM) symLog("service heard Sym ${if (down) "down" else "up"}: watching $watchingKeys, panel ${modifiers.panel}")
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
        val code = event.keyCode
        // The app takes Back before the keyboard, so an open panel must close here.
        if (watchingKeys && code == KeyEvent.KEYCODE_BACK && isInputViewShown && backKey(event, down)) return true
        if (!takenKeys.takes(code, down, watchingKeys, event.isSystem)) {
            if (code == KeyEvent.KEYCODE_SYM) symLog("Sym went on to the app")
            return false
        }
        if (code == KeyEvent.KEYCODE_SYM) symLog("Sym taken from the app for the keyboard")
        if (down) onKeyDown(code, event) else onKeyUp(code, event)
        // A key let go after its field closed: nothing is taken any more, so the service stops hearing keys.
        if (!watchingKeys && !takenKeys.holding) updateKeyWatch()
        return true
    }

    /**
     * TitanKeys Debug only: what happened to a press of Sym, for an app where the panel doesn't open. Never any text, only where the
     * press got to.
     */
    private fun symLog(line: String) {
        if (AppBuild.kind(this) == "debug") Log.i("TitanKeysSym", "$line (app ${currentInputEditorInfo?.packageName}, editing $editing)")
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
    private fun showSuggestions() = barAllowed() && !field.exact

    /** The bar may show here: the setting, the empty home search and the apps where the user hid it. */
    private fun barAllowed() = TypingSettings.suggestionBar(this) && (!inLauncher || launcherFieldHasText) && !barHidden

    private fun launcherPackage(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Bar text size changed since the bar was made, by a pick or the phone's text size, so it's made again at the new size.
        if (FixedScale.size(this) != viewSize) setInputView(onCreateInputView())
        // The bar may only now be set up for this field, so the wait starts once it is.
        main.post { wakeIdleBar() }
        offerClip()
        // Settings > Setup lists the apps whose Scroll assistant isn't in Sliding Mode 1. The system sets the touchpad's mode
        // for the app in front as it comes in, so this looks a moment later, if the keyboard is still in that app.
        info.packageName?.let { app ->
            main.postDelayed({ if (currentInputEditorInfo?.packageName == app) PhoneChecks.noteSlidingMode(this, app) }, SLIDING_CHECK_MILLIS)
        }
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
        panelHover.keysReleased()
        panelPress = -1
        barHover.keysReleased()
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
    /** The guide's own clips, shown in its field in place of the user's while its clipboard slide is open. See [GuideHooks.seedClips]. */
    private var guideClips: ClipboardHistory? = null
    private val sandboxed get() = inGuide && guideClips != null
    /** The clips that the clipboard page shows: the guide's own in its sandbox, or the user's. */
    private val shownClips get() = guideClips?.takeIf { inGuide } ?: clipHistory
    private val clipFile by lazy { AtomicFile(java.io.File(noBackupFilesDir, CLIP_FILE)) }
    /** The guide's clip that pulses on the clipboard page until it is pasted: the right one to pick. */
    private var guideFlashClip: String? = null
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
        // What the guide copies stays in the guide: its own clip to paste is never kept in the user's clipboard.
        if (inGuide) {
            guideClips?.add(text, System.currentTimeMillis())
            showClips()
            return
        }
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
        // Earlier guides pinned their clips in the user's clipboard. They go, now that the guide has its own.
        if (clipHistory.removeTexts(OLD_GUIDE_CLIPS)) saveClips()
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
        panel?.clipSandbox = sandboxed
        panel?.clips = shownClips.clips.toList()
        panel?.flashClip = guideFlashClip.takeIf { inGuide }
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
                // The guide's clips can't be cleared or pinned: they're its own, and it has no Pin.
                if (sandboxed || clipHistory.clips.none { !it.pinned }) return
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
                if (sandboxed || clipHistory.clips.isEmpty()) return
                clipClearing = false
                clipPinning = !clipPinning
                showClips()
                return
            }
            else -> {
                val index = panel?.clipIndex(key) ?: return
                val clip = shownClips.clips.getOrNull(index) ?: return
                clipClearing = false
                if (clipPinning) pin(index) else {
                    haptics.play(Haptic.ClipPasted)
                    // The guide swaps one clip for a prank, and says so; anywhere else a clip pastes as it is.
                    composer.paste(if (inGuide) GuideHooks.swapClip(clip.text) else clip.text)
                    // A rude one is taken back out, so the pulse stays until the right one is pasted.
                    if (clip.text == guideFlashClip) guideFlashClip = null
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
        if (sandboxed) return
        pin(panel?.clipIndex(key) ?: return)
        showClips()
    }

    /** Pinning locks the clip; unpinning is a step, as unlocking Shift is. */
    private fun pin(index: Int) {
        if (clipHistory.togglePin(index)) saveClips()
        haptics.play(if (clipHistory.clips.getOrNull(index)?.pinned == true) Haptic.ClipPinned else Haptic.ClipUnpinned)
    }

    // The word menu (#43): hold a word on the strip, or select one word in the field, to move it on its ladder (ladder v4, 2026-11-13).

    /** A word whose menu is open: [typed] is the letters it would replace on the bar, and empty for a selected word. */
    private class HeldWord(val word: String, val typed: String)
    private var menuWord: HeldWord? = null

    /** Holding a word on the strip opens its menu. */
    private fun holdWord(index: Int): Boolean {
        // A guide slide that doesn't teach the menu yet: the hold opens nothing, and the guide says it's blocked, with a no.
        if (guideBlocked(GuideLesson.BarHold)) return false
        // The guide's own word, alone in the middle place: its show-only menu.
        shownGuideWord()?.let { word ->
            if (index == 0) return openMenu(HeldWord(word, ""))
            // An empty place beside it: nothing opens, and the guide points at the middle.
            KeyboardEvents.publish { KeyboardEvent.EmptyBarHeld }
            return false
        }
        val (word, typed) = composer.shownWord(index) ?: return false
        return openMenu(HeldWord(word, typed))
    }

    /**
     * Opens [held]'s menu. One-letter words, symbols, emoji and numbers have no ladder, so they open none (owner, 2026-11-13).
     * Returns whether it opened.
     */
    private fun openMenu(held: HeldWord): Boolean {
        if (!MenuKeys.opens(held.word)) return false
        // The guide's menu is only for show, but it is the real menu: a word starts not saved, and a word it just saved opens saved
        // again, never back at Save (owner, v5.3).
        val menu = if (inGuide) guideMenus[held.word.lowercase()]?.copy(word = held.word, pressed = null) ?: Strip.Notice.Menu(held.word)
            else menuFor(held.word)
        menuWord = held
        strip?.menuMark = -1
        haptics.play(Haptic.MenuOpen)
        strip?.notice = menu
        KeyboardEvents.publish { KeyboardEvent.WordMenu(held.word) }
        return true
    }

    /**
     * The menu for [word], on the rung where it stands now (owner, 2026-11-13): blocked; a favourite; saved, as a word you saved,
     * one TitanKeys learned from your typing and still suggests, or a dictionary word you didn't take out; or not saved.
     *
     * The engine changes the lists in the background, so [pendingWords] keeps where the menu last moved each word until the
     * engine has done it too. A menu opened again straight away shows the rung you left it on, never an older one.
     */
    private fun menuFor(word: String): Strip.Notice.Menu {
        val typing = SwipeTyping.get(this)
        val step = pendingWords[word.lowercase()] ?: MenuKeys.step(
            blocked = typing.blockedWord(word),
            favourite = FavouriteWords.get(this).contains(word),
            saved = typing.addedWord(word),
            learned = typing.learnedWord(word),
            builtIn = typing.builtInWord(word),
            unsaved = typing.unsavedWord(word))
        return Strip.Notice.Menu(word, step)
    }

    /** Where the menu last moved each word, in lowercase, until the engine has done it too. See [menuFor]. */
    private val pendingWords = HashMap<String, MenuKeys.Step>()

    /** The engine has done what the menu asked for [word]: its own lists say where the word stands again. */
    private fun settled(word: String, step: MenuKeys.Step) = main.post {
        if (pendingWords[word.lowercase()] == step) pendingWords.remove(word.lowercase())
    }

    /**
     * The engine couldn't do what the menu asked for [word]: it says no, and the menu, if it is still open on the word, shows where
     * the word really stands.
     */
    private fun failed(held: HeldWord, what: String, error: String) {
        Log.w("Atlas", "Could not $what a word: $error")
        main.post {
            pendingWords.remove(held.word.lowercase())
            haptics.play(Haptic.WordNotSaved)
            if (menuWord === held) strip?.notice = menuFor(held.word)
        }
    }

    /** The open menu shows [held] as [button] left it, one rung up or down, and stays open, with its effect. See [MenuKeys.press]. */
    private fun menuPressed(held: HeldWord, button: MenuKeys.Button) {
        val menu = strip?.notice as? Strip.Notice.Menu ?: return
        if (menuWord !== held) return
        val next = MenuKeys.press(menu.step, button)
        val pressed = Strip.Notice.Menu(held.word, next, button)
        strip?.notice = pressed
        if (inGuide) guideMenus[held.word.lowercase()] = pressed
    }

    /** The composer's words, and the guide's own word that shows alone in their place on its slide. See [GuideHooks.barWord]. */
    private var ownBarWords = emptyList<String>()
    private var guideBarWord: String? = null

    /** The place on the bar that the guide lights, or null. See [GuideHooks.barLit]. */
    private var guideLit: Int? = null

    /** The guide's word, while it shows: only in the guide's own field. */
    private fun shownGuideWord() = guideBarWord?.takeIf { inGuide }

    /** The guide's lit place, while it shows: only in the guide's own field, so no other app's bar is ever underlined. */
    private fun shownGuideLit() = guideLit?.takeIf { inGuide }

    /**
     * The bar shows the composer's words, or only the guide's word, in the middle place, with its side places empty but there, so
     * a hold on one is heard. Nothing typed, selected or moved changes it.
     */
    private fun showBarWords() {
        barWords = shownGuideWord()?.let { listOf(it, "", "") } ?: ownBarWords
        strip?.words = barWords
        strip?.lit(shownGuideLit())
    }

    /** Where each word stands in the guide's for-show menu, until the slide changes. Nothing reaches the word lists. */
    private val guideMenus = HashMap<String, Strip.Notice.Menu>()

    private fun closeMenu() {
        if (menuWord == null) return
        menuWord = null
        strip?.menuMark = -1
        if (strip?.notice is Strip.Notice.Menu) strip?.notice = null
    }

    /**
     * The word menu is open on the strip. It stays until you close it: the touchpad only marks its buttons then, and the top two
     * rows of keys pick them. See [MenuKeys].
     */
    private val menuOpen get() = menuWord != null && strip?.notice is Strip.Notice.Menu

    /** The key that picked a menu button, whose release must not reach the app. */
    private var menuKey = -1

    /**
     * The menu's button that [key] picks: I moves the word one rung up, O one rung down, and P, or Backspace below it, goes back.
     * Null for a key that picks none.
     */
    private fun menuAction(key: String): Strip.Action? {
        val menu = strip?.notice as? Strip.Notice.Menu ?: return null
        return when (MenuKeys.hovered(key)) {
            MenuKeys.UP -> menu.first
            MenuKeys.DOWN -> menu.second
            MenuKeys.BACK -> Strip.Action.Close
            else -> null
        }
    }

    // The guide's lesson lock: in its own field, a slide lets through only what it teaches. Nothing else reaches the field.

    /** Keys whose press the lock kept, so their release is kept too. */
    private val lockedKeys = HashSet<Int>()

    private fun guideAllows(lesson: GuideLesson) = !inGuide || GuideHooks.allows(lesson)

    /** True if the guide's slide locks [lesson], and tells the guide, which says so with its chip and a no. */
    private fun guideBlocked(lesson: GuideLesson): Boolean {
        if (guideAllows(lesson)) return false
        KeyboardEvents.publish { KeyboardEvent.Blocked(lesson.name) }
        return true
    }

    /**
     * A gesture the guide's slide doesn't teach yet: it has shown that it registered, as it always does, and now the slide refuses
     * it (owner, 2026-11-13: "blocked not ignored"). The guide says so once for the touch, nothing reaches the field, and the
     * rest of the touch does nothing. True if it is refused.
     */
    private fun refuse(lesson: GuideLesson): Boolean {
        if (guideAllows(lesson)) return false
        if (!refused) {
            refused = true
            guideBlocked(lesson)
        }
        return true
    }

    /** A Space gesture refused: the rest of the touch neither steers, nor selects, nor types. Its glow goes with the lift. */
    private fun refuseSpaceGesture() {
        refuse(GuideLesson.Cursor)
        gestureEmpty = true
        gestureRegistered = true
        main.removeCallbacks(registerGestureLater)
        main.removeCallbacks(confirmDoubleTap)
        contact.cancelActive()
        finishHeldTaps(type = false)
    }

    /**
     * What a key belongs to, for the lock. Alt and Sym are their own, and Enter too. On the open panel every key is part of Sym. fn
     * and Ctrl are shortcuts, and so is a key pressed with them. A key pressed with Alt on is Alt's. Space is its own,
     * [GuideLesson.Space], even where the slide teaches steering: the taps that steer are touches, which a press ends. A letter is a
     * held symbol where the slide teaches holding. Anything else that types is typing. Other keys, like volume, aren't locked.
     */
    private fun guideLesson(event: KeyEvent): GuideLesson? {
        val keyCode = event.keyCode
        return when {
            keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT -> GuideLesson.Alt
            keyCode == KeyEvent.KEYCODE_SYM -> GuideLesson.Sym
            panelOpen && (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || event.getUnicodeChar(KeyEvent.META_ALT_ON) > 0 || event.isPrintingKey) -> GuideLesson.Sym
            keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER -> GuideLesson.Enter
            keyCode == Modifiers.FN || keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT -> GuideLesson.Ctrl
            Modifiers.isModifier(keyCode) -> null
            keyCode == KeyEvent.KEYCODE_DEL -> GuideLesson.Backspace
            !event.isPrintingKey && keyCode != KeyEvent.KEYCODE_SPACE -> null
            modifiers.ctrlHeld || event.isCtrlPressed -> GuideLesson.Ctrl
            modifiers.alt != Modifiers.Level.Off || event.isAltPressed -> GuideLesson.Alt
            // A pressed Space types, here as anywhere: a key press ends the touchpad's taps, so it is never part of a steer.
            keyCode == KeyEvent.KEYCODE_SPACE -> GuideLesson.Space
            HoldEngine.isLetter(keyCode) && GuideHooks.allows(GuideLesson.HoldSymbol) -> GuideLesson.HoldSymbol
            else -> GuideLesson.Typing
        }
    }

    /**
     * On a slide that teaches holding but not typing, a letter let go before it became its symbol takes itself back: only the
     * symbol reaches the field. True if it did.
     */
    private fun takeBackUnheldLetter(keyCode: Int): Boolean {
        if (!inGuide || guideAllows(GuideLesson.Typing) || !guideAllows(GuideLesson.HoldSymbol)) return false
        if (!HoldEngine.isLetter(keyCode) || heldLetter?.keyCode != keyCode) return false
        heldLetter = null
        if (pendingInput.isNotEmpty()) enqueue { composer.replaceLetter("") } else composer.replaceLetter("")
        return true
    }

    /**
     * A resting finger hovers the button over its key, or over the key above it: its pill shows the button's name. The mark keeps
     * its column while the finger stays, so after a press the pill shows that button's new action. Back has no pill and no mark:
     * P and Backspace only pick it (owner, 2026-11-12). A [quiet] mark plays nothing, as after a press, which has its own haptic.
     */
    private fun markMenu(key: String, quiet: Boolean = false) {
        val strip = strip ?: return
        val column = if (menuOpen) MenuKeys.hovered(key) else -1
        val mark = if (column == MenuKeys.UP || column == MenuKeys.DOWN) column else -1
        if (!quiet && mark >= 0 && mark != strip.menuMark) haptics.play(Haptic.MenuMark)
        strip.menuMark = mark
    }

    /**
     * Each step's haptic, which the strip plays on the effect's own frame (ladder v4): the book's clap for Save, the rip for Unsave,
     * the hearts' release for Favourite, the crack for Unfavourite, the strike landing for Block and the strike gone for Unblock.
     * The press itself plays [Haptic.MenuPress] at once, a light tick, since each of these lands well after it. With motion off,
     * the step's haptic plays as the press lands, with no tick.
     */
    private fun menuBeat(button: MenuKeys.Button) = haptics.play(when (button) {
        MenuKeys.Button.Save -> Haptic.WordSaved
        MenuKeys.Button.Unsave -> Haptic.WordUnsaved
        MenuKeys.Button.Favourite -> Haptic.WordFavourited
        MenuKeys.Button.Favourited -> Haptic.MenuSpent
        MenuKeys.Button.Unfavourite -> Haptic.WordUnfavourited
        MenuKeys.Button.Block -> Haptic.WordBlocked
        MenuKeys.Button.Unblock -> Haptic.WordUnblocked
    })

    private fun stripAction(action: Strip.Action) {
        val held = menuWord
        if (held != null && action != Strip.Action.OpenSelected) KeyboardEvents.publish { KeyboardEvent.MenuPicked(action.name) }
        // In the guide, the menu climbs its ladder as it really does, with its effects and haptics, but nothing reaches the word
        // lists: the guide's words are only for show (owner, v5, with the ladder).
        if (inGuide && held != null && action in MENU_CHOICES) {
            val button = MenuKeys.Button.entries.first { it.action == action }
            if (button == MenuKeys.Button.Favourited) haptics.play(Haptic.MenuSpent) else menuPressed(held, button)
            return
        }
        when (action) {
            // A selected word's menu is the same lesson as holding a bar word: a slide that doesn't teach it blocks it, and says so.
            Strip.Action.OpenSelected -> if (!guideBlocked(GuideLesson.BarHold)) selectedWord?.let { openMenu(HeldWord(it, "")) }
            Strip.Action.Close -> {
                // Back closed the menu by hand. It closing for any other reason, such as typing, plays nothing.
                if (held != null) haptics.play(Haptic.MenuClosed)
                closeMenu()
            }
            Strip.Action.Save -> if (held != null) saveWord(held)
            Strip.Action.Unsave -> if (held != null) unsaveWord(held)
            Strip.Action.Favourite -> if (held != null) favouriteWord(held)
            // I on a favourite, the top step, is spent: it says no, and does nothing.
            Strip.Action.Favourited -> if (held != null) haptics.play(Haptic.MenuSpent)
            Strip.Action.Unfavourite -> if (held != null) unfavouriteWord(held)
            Strip.Action.Block -> if (held != null) blockWord(held)
            Strip.Action.Unblock -> if (held != null) unblockWord(held)
        }
    }

    /**
     * Save, from not saved: TitanKeys knows the word now. A dictionary word you took out comes back, in the dictionary's own case;
     * any other word goes into Saved words in its own case ([keptCase]). The menu stays open, and I is Favourite now.
     */
    private fun saveWord(held: HeldWord) {
        val typing = SwipeTyping.get(this)
        pendingWords[held.word.lowercase()] = MenuKeys.Step.Saved
        val done: (String?) -> Unit = { error -> if (error != null) failed(held, "save", error) else settled(held.word, MenuKeys.Step.Saved) }
        if (typing.builtInWord(held.word)) typing.restoreWord(held.word, done) else typing.addWord(keptCase(held), done)
        composer.unhide(held.word)
        menuPressed(held, MenuKeys.Button.Save)
    }

    /**
     * Unsave, from saved, for every kind of word: TitanKeys stops knowing it, without blocking it. A word you saved leaves Saved
     * words, a learned one is forgotten, and a dictionary word is taken out, so it's never suggested; typing it twice learns it
     * back, and Save brings it back at once. It leaves the bar's words in this field too.
     */
    private fun unsaveWord(held: HeldWord) {
        pendingWords[held.word.lowercase()] = MenuKeys.Step.New
        SwipeTyping.get(this).unsaveWord(held.word) { error ->
            if (error != null) failed(held, "unsave", error) else settled(held.word, MenuKeys.Step.New)
        }
        composer.hide(held.word)
        menuPressed(held, MenuKeys.Button.Unsave)
    }

    /** Makes the word a favourite: first among similar words. The menu stays open, with I spent. */
    private fun favouriteWord(held: HeldWord) {
        if (!FavouriteWords.get(this).add(held.word)) {
            haptics.play(Haptic.WordNotSaved)
            return
        }
        pendingWords.remove(held.word.lowercase())
        composer.unhide(held.word)
        menuPressed(held, MenuKeys.Button.Favourite)
    }

    /**
     * The word is no longer a favourite: one rung down, always to saved (owner, 2026-11-12). A favourite that TitanKeys didn't
     * otherwise know, as an imported one can be, goes into Saved words, so the menu tells the truth. The menu stays open.
     */
    private fun unfavouriteWord(held: HeldWord) {
        if (!FavouriteWords.get(this).remove(held.word)) {
            haptics.play(Haptic.WordNotSaved)
            return
        }
        val typing = SwipeTyping.get(this)
        val known = typing.addedWord(held.word) || typing.learnedWord(held.word) ||
            typing.builtInWord(held.word) && !typing.unsavedWord(held.word)
        if (known) pendingWords.remove(held.word.lowercase())
        else {
            pendingWords[held.word.lowercase()] = MenuKeys.Step.Saved
            typing.addWord(keptCase(held)) { error ->
                if (error != null) failed(held, "save", error) else settled(held.word, MenuKeys.Step.Saved)
            }
        }
        menuPressed(held, MenuKeys.Button.Unfavourite)
    }

    /**
     * [held]'s word in the case it is saved in: its own, never one that the sentence start, Shift or Caps Lock gave it (owner,
     * 2026-11-13: "why is And kept on auto correcting to cap? And AUTO too?"). The text before it says whether it stood at a
     * sentence start; a word on the bar still has its typed letters before the cursor. See [WordCase].
     */
    private fun keptCase(held: HeldWord): String {
        val before = currentInputConnection?.getTextBeforeCursor(SENTENCE_REACH + held.typed.length, 0)?.toString()
        return WordCase.kept(held.word, before?.dropLast(held.typed.length), modifiers.shift != Modifiers.Level.Off)
    }

    /**
     * Block, from not saved: the word goes on Blocked words, so it's never suggested, learned or fixed into. If it was a fix for the
     * letters typed, autocorrect stops making that fix too. It leaves the bar's words in this field. The menu stays open, and I is
     * Unblock.
     */
    private fun blockWord(held: HeldWord) {
        pendingWords[held.word.lowercase()] = MenuKeys.Step.Blocked
        SwipeTyping.get(this).blockWord(held.word, held.typed) { error ->
            if (error != null) failed(held, "block", error) else settled(held.word, MenuKeys.Step.Blocked)
        }
        composer.hide(held.word)
        menuPressed(held, MenuKeys.Button.Block)
    }

    /** Unblock, from blocked: the word comes off Blocked words, back to not saved, and autocorrect may fix to it again. */
    private fun unblockWord(held: HeldWord) {
        pendingWords[held.word.lowercase()] = MenuKeys.Step.New
        SwipeTyping.get(this).setWordBlocked(held.word, false) { error ->
            if (error != null) failed(held, "unblock", error) else settled(held.word, MenuKeys.Step.New)
        }
        composer.unhide(held.word)
        menuPressed(held, MenuKeys.Button.Unblock)
    }

    /** One word selected in the field shows alone on the strip, so that its menu can save or forget it. */
    private fun showSelectedWord(start: Int, end: Int) {
        // One rule, kept in step with the selection (owner, 2026-11-12): while text is selected the bar shows it alone, and with
        // nothing selected it shows the suggestions. Only a single word opens its menu. See [WordSelection.shown].
        val length = abs(end - start)
        val text = if (start < 0 || length == 0) null else currentInputConnection?.let { connection ->
            connection.getSelectedText(0) ?: connection.getSurroundingText(0, 0, 0)?.text?.takeIf { it.length == length }
        }
        val shown = WordSelection.shown(text, length)
        selectedWord = shown?.takeIf { WordSelection.isWord(it) }
        if (selectedWord == null && menuWord?.typed == "" && strip?.notice is Strip.Notice.Menu) closeMenu()
        strip?.selected = shown
    }

    /** The selected word, when the selection is one word, which its menu can save or forget; null for anything else. */
    private var selectedWord: String? = null

    override fun onFinishInput() {
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
        lastSelectionEnd = newEnd
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

    /** New input ends a flourish at once, and a flick's dot ticks still to come with it. */
    private fun interruptGesture() {
        strip?.gesture?.interrupt()
        haptics.cutDots()
    }

    private fun motion(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            clearTeamsEgg()
            // A flourish is only a picture, so the next touch ends it. A delete's dots wait for what the touch turns out to be: another
            // delete joins their queue, and anything else cuts them (see [outcome]).
            if (strip?.gesture?.softDelete != true) interruptGesture()
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
        // While the word menu is open the touchpad only marks its buttons: no swipe, trail, flick or cursor starts.
        if (menuOpen && !panelOpen) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                endDeleteHold()
                stopCursor()
                contact.cancelActive()
            }
            // The guide's drawing of the keys still shows the finger, as it hovers the menu's buttons (owner, v5.2: every swipe).
            if (inPractice) traceMenuFinger(event, samples)
            return
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val first = samples.firstOrNull() ?: Sample(Float.NaN, Float.NaN, event.eventTime)
                haptics.begin(samples.firstOrNull())
                // Each stroke hits its own wall.
                deleteWall.left()
                // Only where a delete flick would count, and with something to delete, can the stroke crunch and glow.
                // On a guide slide that doesn't teach the flick, it still glows, so it's seen to register before it's refused.
                flickCrunch.begin(!altOrPanel && field.words && pieces.canDelete(first.time) && somethingToDelete(first))
                refused = false
                endDeleteHold()
                holdTrail.clear()
                contact.down(first)
                if (cursor.resume(first, composer.liftOffMillis.toLong())) {
                    // Back within the slip recovery period: still steering the cursor, never typing. A tick says so.
                    haptics.play(Haptic.SteerResumed)
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
                // On a guide slide that doesn't teach them yet, Space gestures still count and glow, and are refused as they act.
                val gestureCounts = cursorControl && !altOrPanel && contact.travel != null
                Recording.sink?.gesture("down", first.time, "counts=$gestureCounts cursorControl=$cursorControl " +
                    "altOrPanel=$altOrPanel contact=${contact.travel != null}")
                cursorGesture.down(first, gestureCounts)
                swipeShown = 0
                gestureRegistered = false
                gestureEmpty = false
                main.removeCallbacks(registerGestureLater)
                if (cursorGesture.touchPending) {
                    gestureLanded = first
                    if (cursorGesture.tapsBefore == 2) pickOnLand()
                    main.postDelayed(registerGestureLater, CursorGesture.REGISTER_MILLIS)
                }
                cursorGesture.move(samples.drop(1))?.let {
                    startCursor(it, samples.drop(1))
                    return
                }
                settleHeldTaps()
                // A touch that may be the last swipe carrying on keeps that swipe on the trail until it is settled.
                val carries = contact.points.isNotEmpty() && lastTouch?.let {
                    it.isNotEmpty() && letterSpace(it).couldContinue(first.x, first.y * 4 / 3, first.time - it.last().time, composer.liftOffMillis.toLong())
                } == true
                // The bar's mini path joins it too, as one line.
                swipeCarried = carries && !inPractice && strip?.gesture?.swipeCarriesOn() == true
                Trace.touch(contact.points, false, if (carries && Trace.listening) lastTouch.orEmpty() else emptyList())
            }
            MotionEvent.ACTION_MOVE -> {
                cursor.move(samples)
                if (cursor.active) {
                    traceCursor(samples, ended = false)
                    return
                }
                cursorPath.addAll(samples.filter { it.valid })
                contact.move(samples)
                // A held delete flick waits for the finger to lift, and goes faster the further the finger is from where it started.
                if (contact.deleting) {
                    // The guide's drawing of the keys follows a held delete too (owner, v5.2: some slides showed no trail): the
                    // flick's path and the finger since, as the contact keeps no points once it deletes (build #10).
                    Trace.touch(holdTrail.follow(contact, samples), false)
                    strip?.gesture?.swipeCancelled()
                    samples.lastOrNull { it.valid }?.let { finger ->
                        val next = deleteHold.follow(finger)
                        // Back near where the flick started, the hold pauses: its words on the bar stand still, its glow fades out
                        // and its caret blinks.
                        strip?.gesture?.deletePaused(deleteHold.paused)
                        // Going into the pause zone, or reaching the top-speed line, ticks once. After the wall, nothing does.
                        if (deleteHold.crossed() != null && Haptics.zoneTicks(this) && !deleteWall.atWall) haptics.play(Haptic.DeleteZone)
                        next?.let {
                            nextDeleteHoldStep(it)
                            if (strip?.gesture?.scene == GestureDisplay.Scene.DeleteHold) {
                                strip?.gesture?.deleteHeld(deleteHold.repeatMillis, finger.time, deleteHold.pace)
                            }
                        }
                    }
                    return
                }
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
                    || !isTapKey(Layout.keyAt(first.x, first.y)))) {
                    // Clearly a delete flick, the keys it crosses crunch; once it stops being one, a break, then swiping's ticks.
                    // On a guide slide only what the slide teaches is felt: a locked one glows, and its feel is the no at the end.
                    val felt = guideAllows(if (flickCrunch.crunching) GuideLesson.Flick else GuideLesson.Swipe)
                    if (flickCrunch.move(contact.points)) {
                        if (felt) haptics.play(Haptic.DeleteBreak)
                        strip?.gesture?.flickGlowEnded(SystemClock.uptimeMillis())
                    }
                    if (guideAllows(if (flickCrunch.crunching) GuideLesson.Flick else GuideLesson.Swipe)) haptics.move(samples, flickCrunch.crunching)
                    // While it is clearly a flick, the delete glow reaches from the corner to the finger and follows it.
                    if (flickCrunch.crunching) samples.lastOrNull { it.valid }?.let { strip?.gesture?.flickGlow(it.x, it.time) }
                }
                Trace.touch(holdTrail.follow(contact, samples), false)
                // A guide slide that doesn't teach swiping shows no word growing on the bar either.
                if (guideAllows(GuideLesson.Swipe)) showSwipe()
            }
            MotionEvent.ACTION_UP -> lift(event, samples, cancelled = false)
            // The system cancels a gesture when its window goes, such as the window above the shade. Like a short lift, the
            // swipe so far still counts, and the next touch can carry it on. See [SwipePieces].
            MotionEvent.ACTION_CANCEL -> lift(event, samples, cancelled = true)
            MotionEvent.ACTION_POINTER_DOWN -> {
                contact.cancelActive()
                strip?.gesture?.swipeCancelled()
                endDeleteHold()
                endCursor()
                Trace.touch(emptyList(), true)
            }
        }
    }

    /** Points of this touch already on the strip's mini path. */
    private var swipeShown = 0
    /** This touch carries on the last swipe, so its points add to that swipe's path from the start. */
    private var swipeCarried = false

    /**
     * The strip shows a word swipe's path as it goes. A touch that hasn't gone far enough to be a swipe shows nothing, and neither
     * does a delete flick, nor a touch that can't type, such as with Alt on or the panel open. The guide has its own trail, so the
     * bar shows none there. A touch that carries on the last swipe after a slip adds to its path at once.
     */
    private fun showSwipe() {
        val gesture = strip?.gesture ?: return
        // While a delete's dots play, the next stroke draws no path: it may be another delete, which joins them.
        if (gesture.softDelete) return
        val points = contact.points
        val travel = contact.travel
        val first = points.firstOrNull()
        val far = swipeCarried && gesture.swiping || travel != null && travel >= Contact.SWIPE_KEYS
        if (inPractice || !contact.swipes || altOrPanel || travel == null || !far || first == null
            || startsDeleteFlick(first.x, first.y) && (isDeleteWordSwipe(points) || flickCrunch.mayBeFlick)) {
            gesture.swipeCancelled()
            swipeShown = 0
            return
        }
        if (swipeShown > points.size) swipeShown = 0
        val now = SystemClock.uptimeMillis()
        for (index in swipeShown until points.size) {
            val point = points[index]
            if (point.valid) gesture.swipePoint(point.x, point.y, point.step, now)
        }
        swipeShown = points.size
    }

    /**
     * A Space tap that the gesture code counted grows the glow under the middle word, and ticks, so each tap is felt as it
     * counts. A first tap on a key that types by tapping may become that key, so it shows nothing and plays that key's own tick;
     * a key press puts the glow out (see [onKeyDown]).
     */
    private fun glowSpaceTap(cancelled: Boolean) {
        val taps = cursorGesture.tapped
        if (cancelled || taps == 0) return
        val landed = cursorGesture.tappedLanded
        if (taps == 1 && landed != null && isTapKey(Layout.keyAt(landed.x, landed.y))) return
        haptics.play(Haptic.SpaceTap)
        strip?.gesture?.spaceTapped(taps, SystemClock.uptimeMillis())
    }

    /** A finger lifted, or the system cancelled the gesture, which counts as a lift but never as a tap of a double tap. */
    private fun lift(event: MotionEvent, samples: List<Sample>, cancelled: Boolean) {
        // Every delete flick shows its glow, even one that lifted before it drew; a hold's glow fades with its dots.
        val stroke = (contact.points + samples).filter { it.valid }
        val flicked = !cancelled && !contact.deleting && flickCrunch.lifted(stroke)
        endDeleteHold()
        if (flicked) strip?.gesture?.flickLifted(stroke.last().x, SystemClock.uptimeMillis())
        else strip?.gesture?.flickGlowEnded(SystemClock.uptimeMillis())
        tripleDown = false
        strip?.gesture?.pickLifted(SystemClock.uptimeMillis())
        val doubleTapped = if (cancelled) {
            cursorGesture.cancel(event.eventTime)
            false
        } else cursorGesture.up(samples, event.eventTime)
        Recording.sink?.gesture("up", event.eventTime, "doubleTapped=$doubleTapped" + if (cancelled) " cancelled=true" else "")
        main.removeCallbacks(registerGestureLater)
        strip?.gesture?.swipeEnded(SystemClock.uptimeMillis(), composer.liftOffMillis.toLong())
        glowSpaceTap(cancelled)
        // Three quick taps pick the word at the cursor, with no slide, once no fourth touch follows.
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
        else if (inGuide && cursorGesture.twoAbove) main.postAtTime(confirmTwoAbove, event.eventTime + CursorGesture.CONFIRM_MILLIS)
        else if (cursorGesture.tripled) main.postAtTime(confirmTriple, event.eventTime + CursorGesture.CONFIRM_MILLIS)
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
        // A lift whose last samples were off the touchpad still ends the trail the guide drew, and doesn't wipe it: the guide shows
        // every swipe (owner, v5.2). Trace itself leaves out the points the touchpad didn't read.
        // A held delete's trail ends with its own path, flick and hold together, and fades as a swipe's does.
        val traced = if (holdTrail.held) holdTrail.points.toList() else points
        Trace.touch(if (traced.size <= Swipe.MAX_POINTS && (inPractice || holdTrail.held || samples.all { it.valid })) traced else emptyList(), true)
        holdTrail.clear()
    }

    /** A held delete's path, for the guide's drawing: see [HoldTrail]. */
    private val holdTrail = HoldTrail()

    /** The finger's path while the word menu is open, for the guide's drawing only: nothing else reads it. */
    private val menuFinger = ArrayList<Sample>()

    private fun traceMenuFinger(event: MotionEvent, samples: List<Sample>) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) menuFinger.clear()
        menuFinger.addAll(samples.filter { it.valid }.take((Swipe.MAX_POINTS - menuFinger.size).coerceAtLeast(0)))
        val lifted = event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
        Trace.touch(menuFinger, lifted)
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
        main.removeCallbacks(confirmTriple)
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
            command(arrow, steer = true)
            if (next?.isEmpty() != true) rereadCursorText = strip?.gesture?.cursorStepped(direction) == true
        }
    }

    /**
     * Selects the word at the cursor, or does nothing between words but say no, then calls [nothing]. [inFront] also takes the
     * word before the cursor past marks and spaces, for a triple tap. See [WordSelection.atOrBefore].
     */
    private fun selectWord(inFront: Boolean = false, nothing: () -> Unit = {}) = changeSelection { text, start, end, _ ->
        val word = if (inFront) WordSelection.atOrBefore(text, start, end) else WordSelection.at(text, start, end)
        haptics.play(Haptic.picked(found = word != null))
        if (word == null) {
            nothing()
            return@changeSelection null
        }
        showWordText(text, start, end, word)
        strip?.gesture?.wordSelected(SystemClock.uptimeMillis(), held = inFront && tripleDown)
        word
    }

    /** Selects the word at the cursor, the part that a word drag keeps selected. Between words the drag starts from the cursor. */
    private fun startWordDrag() {
        GestureDisplay.selectionEnd = SelectionEnd.stored(Haptics.selectionEndId(this))
        wordAnchor = null
        selectionWall.left()
        rememberCursor()
        changeSelection { text, start, end, offset ->
            val word = WordSelection.at(text, start, end) ?: (start to end)
            wordAnchor = offset + word.first to offset + word.second
            showWordText(text, start, end, word)
            word
        }
    }

    /** Extends the selection by one word, in order with the rest of the typing. */
    private fun extendWords(direction: Int) = enqueue {
        changeSelection { text, start, end, offset ->
            val anchor = wordAnchor ?: return@changeSelection null
            val range = WordSelection.extend(text, start, end, anchor.first - offset, anchor.second - offset, direction)
            if (range == start to end) {
                // Nothing more that way: the selection reached an end of the text. It plays once, until it moves away.
                if (selectionWall.reached()) haptics.play(Haptic.SelectionEnd)
                return@changeSelection null
            }
            selectionWall.left()
            // The word that this step took or gave back.
            val moved = if (range.second != end) text.subSequence(minOf(end, range.second), maxOf(end, range.second))
                else text.subSequence(minOf(start, range.first), maxOf(start, range.first))
            haptics.play(Haptic.SelectionWord)
            // The words around the anchor are read again from here, so on long text the pills never run out.
            showWordText(text, start, end, anchor.first - offset to anchor.second - offset)
            strip?.gesture?.wordStep(direction)
            range
        }
    }

    /**
     * Shows the words around [anchor] on the bar, from [text], the text read around the selection from [start] to [end]. Where
     * that text is cut off at its reach, more lies beyond it.
     */
    private fun showWordText(text: CharSequence, start: Int, end: Int, anchor: Pair<Int, Int>) {
        if (anchor.first !in 0..text.length || anchor.second !in anchor.first..text.length) return
        strip?.gesture?.wordText(text.subSequence(0, anchor.first), anchor.second - anchor.first,
            text.subSequence(anchor.second, text.length), moreBefore = start >= WORD_REACH, moreAfter = text.length - end >= WORD_REACH)
    }

    /** What a Ctrl shortcut plays, as [ShortcutFeel] has it: only Ctrl + C with nothing selected, a no. A key held down plays once. */
    private fun shortcutFeel(event: KeyEvent) {
        if (!event.isCtrlPressed || event.isMetaPressed || event.repeatCount > 0) return
        val letter = if (event.keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) 'A' + (event.keyCode - KeyEvent.KEYCODE_A) else null
        val selected = if (lastSelectionStart < 0 || lastSelectionEnd < 0) null else lastSelectionStart != lastSelectionEnd
        ShortcutFeel.of(letter, selected)?.let(haptics::play)
    }

    /**
     * Whether a stroke from [first] may have something to delete: always, unless it starts where a delete flick does and the
     * field has nothing before the cursor. Typing still on its way, or a field that doesn't answer, counts as something.
     */
    private fun somethingToDelete(first: Sample): Boolean {
        if (!startsDeleteFlick(first.x, first.y) || pendingInput.isNotEmpty() || composer.hasPendingInput) return true
        return currentInputConnection?.getTextBeforeCursor(1, 0)?.isEmpty() != true
    }

    /** Keeps where the cursor is, when nothing is selected, so a double space after a select can put it back there. */
    private fun rememberCursor() {
        if (lastSelectionStart >= 0 && lastSelectionStart == lastSelectionEnd) cursorBeforeSelect = lastSelectionStart
    }

    /**
     * With text selected, puts the cursor back where it was before a Space gesture selected it, if that is known and still in the
     * selection, or else at the selection's end, in order with the rest of the typing. False with nothing selected.
     */
    private fun collapseSelection(): Boolean {
        val start = minOf(lastSelectionStart, lastSelectionEnd)
        val end = maxOf(lastSelectionStart, lastSelectionEnd)
        if (field.exact || start < 0 || start == end) return false
        val at = WordSelection.collapseTo(start, end, cursorBeforeSelect)
        cursorBeforeSelect = null
        enqueue {
            composer.command(destructive = false, fieldLocal = true) {
                val connection = currentInputConnection ?: return@command false
                connection.finishComposingText()
                connection.setSelection(at, at)
            }
        }
        return true
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

    /** The key that toggled a quick setting, whose release must not start the mark's wait: see [keepPanelHover]. */
    private var panelPress = -1

    /**
     * A key that toggled a quick setting under the finger: the finger is on it, so the page's mark stays there, whatever the touchpad
     * reports around the press, as the word menu's hover does (owner, 2026-11-13: the page was the model, so it must really stay).
     * The press's own lift comes after the key on the phone, and the plain wait after it cleared the mark. See [SteadyKey.press].
     * Only a page that stays open keeps it: Open, Kbd and Clip leave it.
     */
    private fun keepPanelHover(key: String, keyCode: Int) {
        if (modifiers.panel != Modifiers.Panel.Settings) return
        panelHover.press(key)
        panelPress = keyCode
        panel?.hover = panelHover.key
    }

    /** Pushing a key on the quick settings page. The change applies at once and the page stays open. Only the key that opens Settings leaves. */
    private fun useQuickSetting(item: QuickSetting) {
        // In the guide, O is off limits. The slide says so, and a key press plays no haptic.
        if (inGuide && item == QuickSetting.OpenSettings) return
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
                haptics.play(Haptic.quickSetting(on = quickSettingOn(item)))
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
        val queued = pendingInput.toList()
        pendingInput.clear()
        contact.cancel()
        // Discarded typing must still apply Shift, Alt and Sym presses and all releases, in order. A dropped key still uses up the
        // Shift or Alt it was pushed with: otherwise Alt + Z with Z dropped left Alt on, and the next key typed its symbol.
        for (pending in queued) {
            val event = pending.event ?: continue
            if (Modifiers.survivesDiscard(event.keyCode, event.action == KeyEvent.ACTION_DOWN)) pending.action?.invoke()
            else if (Modifiers.dropUsesModifiers(event.keyCode, event.repeatCount)) modifiers.consume()
        }
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
                Modifiers.isModifier(event.keyCode) -> {
                    modifiers.key(event)
                    switchKeyboardIfAsked()
                }
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
        // A touch that is no delete flick cuts a delete's dots, which waited for it.
        if (!(outcome is Contact.Outcome.Swipe && outcome.deletesWord) && strip?.gesture?.softDelete == true) interruptGesture()
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
                // A guide slide that doesn't teach this yet: the swipe or the flick does nothing, and the slide says so, once.
                if (refuse(if (outcome.deletesWord) GuideLesson.Flick else GuideLesson.Swipe)) return
                if (outcome.deletesWord) {
                    val before = currentInputConnection?.getTextBeforeCursor(FLICK_REACH, 0)
                    val nothingLeft = before?.isEmpty() == true
                    // Nothing to delete: nothing happened, so a no, once for the stroke. A hold with nothing to delete never starts,
                    // and plays just this; its steps after stay quiet, as the stroke has had its say (see [deleteWall]).
                    if (nothingLeft && deleteWall.reached()) haptics.play(Haptic.deleteFoundNothing(tookAny = false))
                    // A hold's first word: the bar's words are the real ones before it goes.
                    if (before != null) strip?.gesture?.deleteText(before)
                    composer.backspace(true)
                    if (!nothingLeft) {
                        // A dot for each character of the word that went, not counting spaces. The haptic's tail lands as they pop.
                        val gesture = strip?.gesture
                        gesture?.speedUp = Haptics.dotsSpeedUp(this)
                        // How long the dots wait on the bar, which the Haptics lab can try live in TitanKeys Debug.
                        GestureDisplay.triedFlickHold = Haptics.readFlickHold(this)?.toFloat()
                        gesture?.wordDeleted(SystemClock.uptimeMillis(), GestureDisplay.letters(before))
                        val scale = if (GuideMotion.enabled) ValueAnimator.getDurationScale() else 0f
                        // The dots tick as they land in the caret, only while this flick's dots are still on the bar.
                        val dotsAt = gesture?.dotsAt
                        // A flick while dots still play adds its dots to theirs, and the whole queue's ticks go again with its wait.
                        haptics.flick(gesture?.dotsPoppedMillis(scale), gesture?.dotLandings(scale).orEmpty(),
                            { dotsAt != null && strip?.gesture?.dotsAt == dotsAt })
                    }
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
                // A light tap on Space that types, once it can't be a steer's tap any more: a slide that locks spaces says so.
                if (key == " " && guideBlocked(GuideLesson.Space)) return
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
        if (!holdSymbols || !editing || !HoldEngine.isLetter(event.keyCode) || deadKeyPending || !guideAllows(GuideLesson.HoldSymbol)) return false
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
        } else if (pendingInput.isNotEmpty()) enqueue { composer.replaceLetter(text) } else composer.replaceLetter(text)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SYM) symLog("keyboard heard Sym down, repeat ${event.repeatCount}: shown $isInputViewShown, " +
            "panel ${modifiers.panel}, guide $inGuide, menu $menuOpen, waiting ${pendingInput.size}")
        // The panel's mark of the key under a resting finger stays while a key is down, as when it toggles a quick setting.
        if (!touchpad.isGestureKey(event)) {
            panelHover.keyDown(keyCode)
            // The same for the bar's: a press on a word menu button keeps its pill, which then shows that button's next step.
            barHover.keyDown(keyCode)
        }
        clearTeamsEgg()
        interruptGesture()
        strip?.gesture?.glowOff()
        wakeIdleBar()
        // A guide slide that doesn't teach this key yet: it does nothing at all, and its release is kept too.
        // With the word menu open, the keys press its buttons, never type: the menu's own handling below decides.
        val locked = if (editing && inGuide && !event.isSystem && !menuOpen) guideLesson(event) else null
        if (locked != null && !guideAllows(locked)) {
            if (keyCode == KeyEvent.KEYCODE_SYM) symLog("Sym kept by the guide's slide")
            if (event.repeatCount == 0) guideBlocked(locked)
            lockedKeys += keyCode
            return true
        }
        // A key held on Alt's slide types one symbol, not a row of them: the guide never repeats a typing key.
        if (locked == GuideLesson.Alt && event.repeatCount > 0 && !Modifiers.isModifier(keyCode)) return true
        // A letter held on a slide that doesn't teach holding yet: it stays the letter it typed, its repeats type nothing, and the
        // guide says holding is for later, once for the hold (owner, 2026-11-13: blocked, not ignored).
        if (editing && inGuide && holdSymbols && event.repeatCount > 0 && HoldEngine.isLetter(keyCode) &&
            !guideAllows(GuideLesson.HoldSymbol)) {
            if (event.repeatCount == 1) guideBlocked(GuideLesson.HoldSymbol)
            return true
        }
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
        if (!editing || keyCode == KeyEvent.KEYCODE_BACK) {
            if (keyCode == KeyEvent.KEYCODE_SYM) symLog("Sym outside a field went on to Android")
            return super.onKeyDown(keyCode, event)
        }
        typedInField = true
        if (touchpad.isGestureKey(event)) return true
        // Keys like volume reach the keyboard too, but they are not typing.
        if (!event.isSystem) showStripAfterBack()
        // An exact typing app can hide the keyboard by itself, as AnyDesk's own keyboard button does. A key brings the bar back.
        if (field.exact && !event.isSystem && !isInputViewShown && !hiddenByBack) {
            logExact("bar back for a key")
            main.post(showStrip)
        }
        // With the word menu open, the top two rows of letters pick its buttons, as the keys pick symbols on the Sym panel, and
        // Backspace, under P, goes back.
        if (keyCode == menuKey && event.repeatCount > 0) return true
        if (!event.isSystem && menuOpen && (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z || keyCode == KeyEvent.KEYCODE_DEL)) {
            val pressed = if (keyCode == KeyEvent.KEYCODE_DEL) Layout.BACKSPACE else ('A' + (keyCode - KeyEvent.KEYCODE_A)).toString()
            val action = menuAction(pressed)
            if (action != null) {
                if (event.repeatCount == 0) {
                    menuKey = keyCode
                    stripAction(action)
                    // The finger that pressed the key is on it, so its pill stays there and shows the button's next step, whatever
                    // the touchpad reports around the press (owner, 2026-11-12). See [SteadyKey.press].
                    if (menuOpen) {
                        barHover.press(pressed)
                        restingKey = barHover.key
                        markMenu(restingKey, quiet = true)
                    }
                }
                return true
            }
        }
        // In the guide's show-only menu on its own word, any other key does nothing, and the guide says so; the menu stays open.
        if (!event.isSystem && menuOpen && shownGuideWord() != null && !Modifiers.isModifier(keyCode)) {
            if (event.repeatCount == 0) KeyboardEvents.publish { KeyboardEvent.MenuMissed }
            return true
        }
        // Typing on closes the menu, and the strip shows words again. The newest clip goes from the strip too.
        if (!event.isSystem && !Modifiers.isModifier(keyCode)) {
            closeMenu()
            dismissClipOffer()
        }
        // The touchpad hears the physical press now, before the key is typed.
        contact.key(keyCode, true, event.eventTime)
        val carried = carryCtrl(event)
        val repeatOfHold = trackHold(carried)
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
        // The last key let go: the mark goes as after a lift, unless the touchpad shows the finger again. A key that toggled a quick
        // setting keeps it: the touchpad's lift for the press comes after the key, and is the press's own. See [keepPanelHover].
        val panelRelease = keyCode == panelPress
        if (panelRelease) {
            panelHover.release(keyCode, event.eventTime)
            panelPress = -1
        }
        val panelLast = !panelRelease && panelHover.keyUp(keyCode, event.eventTime) && panelOpen
        // A key that picked a word menu button keeps the hover when it is let go: the finger is still on it, and its pill now
        // names the button's next step (owner, 2026-11-12). The touchpad's lift for the press mostly comes after the key, and is
        // the press's own: the hover goes only when the touchpad shows the finger on another key, or lifting after that. See
        // [SteadyKey.press].
        val menuRelease = keyCode == menuKey && menuOpen
        if (menuRelease) barHover.release(keyCode, event.eventTime)
        val barLast = !menuRelease && barHover.keyUp(keyCode, event.eventTime) && !panelOpen
        if (panelLast || barLast) {
            main.removeCallbacks(expireHover)
            main.postDelayed(expireHover, SteadyKey.LIFT_MILLIS)
        }
        if (keyCode == KeyEvent.KEYCODE_SYM) symLog("keyboard heard Sym up: panel ${modifiers.panel}")
        if (keyCode == KeyEvent.KEYCODE_DEL) modifiers.backspaceReleased()
        if (lockedKeys.remove(keyCode)) return true
        // Published before the take-back, so the guide hears the letter let go too soon.
        val takeBack = inGuide && editing && heldLetter?.keyCode == keyCode
        Recording.sink?.key(event, touchpad.isGestureKey(event))
        inputLog()?.key(event, touchpad.isGestureKey(event))
        // The guide tells Alt pressed and let go from Alt held, and a letter let go before its symbol, and the playground's drawing
        // lets go of a held key. Only in their fields.
        if (inPractice && (Modifiers.isShiftOrAlt(keyCode) || HoldEngine.isLetter(keyCode))) KeyboardEvents.publish { KeyboardEvent.Released(keyCode) }
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
        if (takeBack) takeBackUnheldLetter(keyCode)
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
     * Alt + Sym, with the panel closed: the next keyboard, as Key Mapper's switch did, not the picker. With no other keyboard
     * nothing happens. Never in the guide's field, so the guide keeps its keyboard. Plays no haptic: the keyboard changes on
     * screen (docs/haptics.md, Shortcuts).
     */
    private fun switchKeyboardIfAsked() {
        if (!modifiers.takeKeyboardSwitch() || inGuide) return
        main.post { switchToNextInputMethod(false) }
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
            if (event.keyCode == KeyEvent.KEYCODE_SYM && event.action == KeyEvent.ACTION_DOWN)
                symLog("Sym handled: panel now ${modifiers.panel}, shown $isInputViewShown")
            switchKeyboardIfAsked()
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
            // One press, one paste: a held shortcut's repeats do nothing.
            if (event.repeatCount > 0 && !Modifiers.shortcutRepeats(event.keyCode)) return true
            modifiers.useHeld()
            shortcutFeel(event)
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
                // Ctrl or a held Shift with Backspace deletes the previous word, and holding it goes on by word. Alt with Backspace
                // deletes forward, like Delete on a PC, or with that setting off goes to the app as Android's own Alt + Backspace,
                // which deletes the line (owner, 2026-11-13). See [Modifiers.backspacePress].
                when (modifiers.backspacePress(event.repeatCount, event.metaState, TypingSettings.altBackspaceForward(this),
                    TypingSettings.shiftBackspaceWord(this))) {
                    Modifiers.Backspace.Forward -> command(withKey(event, KeyEvent.KEYCODE_FORWARD_DEL), dropMeta = KeyEvent.META_ALT_MASK)
                    Modifiers.Backspace.AltToApp -> command(withMeta(event, KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON))
                    Modifiers.Backspace.Word -> composer.backspace(true)
                    else -> composer.backspace(false)
                }
                modifiers.afterBackspace()
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
            keepPanelHover(letter, event.keyCode)
        } else if (modifiers.panel == Modifiers.Panel.Clipboard) {
            pickedKey = event.keyCode
            clipKey(letter)
        } else if (letter == Layout.CLIPBOARD_KEY) {
            pickedKey = event.keyCode
            openClipboardPage()
        } else panel?.item(letter)?.let { item ->
            pickedKey = event.keyCode
            typePanelItem(item)
        } ?: run { if (inGuide) KeyboardEvents.publish { KeyboardEvent.EmptyKey } }
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
        val meta = KeyEvent.normalizeMetaState(ExactTyping.meta(event.metaState or modifiers.metaState, modifiers.ctrlHeld, sticky.meta))
        val plan = ExactTyping.plan(event.keyCode, event.repeatCount, meta, altSymbols = !exactRealAlt, charFor = event::getUnicodeChar)
        logExact("key ${event.keyCode} repeat ${event.repeatCount} meta 0x${Integer.toHexString(meta)}" +
            if (plan.text != null) " as text from Alt" else "")
        plan.text?.let(::commitExact)
        sendExact(event, plan.events)
        sticky.used()
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

    private fun command(event: KeyEvent, textKey: Boolean = false, dropMeta: Int = 0, steer: Boolean = false) {
        // A Shift that came on by itself, or Caps, is for a letter: Ctrl + C, Tab or an arrow must not become Ctrl + Shift + C or a
        // selection. A tapped Shift on a steer is for the next letter too. See [Modifiers.commandMeta].
        val sticky = modifiers.commandMeta(textKey, steer)
        // [dropMeta]: modifiers that chose the command and aren't part of it, as Alt for a forward delete.
        val meta = KeyEvent.normalizeMetaState((event.metaState or sticky) and dropMeta.inv())
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

    /** [event] as another key, with the same times, meta state and repeat: Alt + Backspace's forward delete. */
    private fun withKey(event: KeyEvent, code: Int) = KeyEvent(event.downTime, event.eventTime, event.action, code, event.repeatCount,
        event.metaState, event.deviceId, event.scanCode, event.flags, event.source)

    /** [event] with [meta] added: Alt + Backspace keeps its Alt through its repeats, after a tapped Alt is used up. */
    private fun withMeta(event: KeyEvent, meta: Int) = KeyEvent(event.downTime, event.eventTime, event.action, event.keyCode,
        event.repeatCount, event.metaState or meta, event.deviceId, event.scanCode, event.flags, event.source)

    /** Shift and Alt as last shown, so locking one plays its feel once. */
    private var shownShift = Modifiers.Level.Off
    private var shownAlt = Modifiers.Level.Off

    /** Exact typing's own bar: half the strip, worked by touch, always up. See [ExactBar]. It follows the settings that hide the bar. */
    private var exactBar: ExactBar? = null
    private val sticky = ExactTyping.Sticky()
    /** The physical Alt sends Alt in exact typing apps, not symbols. See [ExactTyping.realAlt]. */
    private var exactRealAlt = true

    private fun updateExactBar() {
        val bar = exactBar ?: return
        val shown = ExactTyping.barShown(editing, field.exact, barAllowed(), panelOpen)
        bar.visibility = if (shown) View.VISIBLE else View.GONE
        if (!shown) return
        bar.show { key ->
            val own = sticky.level(key)
            val held = when (key) {
                ExactTyping.BarKey.Shift -> modifiers.shift
                ExactTyping.BarKey.Alt -> modifiers.alt
                ExactTyping.BarKey.Ctrl -> if (modifiers.ctrlHeld) Modifiers.Level.Locked else Modifiers.Level.Off
            }
            when {
                own == ExactTyping.Level.Locked || held == Modifiers.Level.Locked -> ExactTyping.Level.Locked
                own == ExactTyping.Level.Once || held == Modifiers.Level.Once -> ExactTyping.Level.Once
                else -> ExactTyping.Level.Off
            }
        }
    }

    /** A tap on a chip turns it on for the next key, or off; a long press locks it. */
    private fun exactChip(key: ExactTyping.BarKey, long: Boolean) {
        if (long) { if (sticky.lock(key)) haptics.play(Haptic.ExactLock) } else {
            haptics.play(Haptic.chipTapped(wasLocked = sticky.level(key) == ExactTyping.Level.Locked))
            sticky.tap(key)
        }
        updateExactBar()
    }

    /** A key on the bar goes to the app as a key event, with the modifiers that are on, which it then uses up. */
    private fun exactBarKey(code: Int) {
        val time = SystemClock.uptimeMillis()
        val like = KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD)
        val meta = KeyEvent.normalizeMetaState(ExactTyping.meta(modifiers.metaState, modifiers.ctrlHeld, sticky.meta))
        haptics.exactKey()
        sendExact(like, ExactTyping.plan(code, 0, meta) { 0 }.events)
        sticky.used()
        modifiers.consume()
        showModifiers()
    }

    private fun showModifiers() {
        showGuideExtras()
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
        updateExactBar()
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
        editing = false
        GuideHooks.panelRequests = null
        GuideHooks.stripDemos = null
        GuideHooks.stripPoints = null
        GuideHooks.barSideRequests = null
        GuideHooks.stripPointWords = null
        GuideHooks.barWordRequests = null
        GuideHooks.barLitRequests = null
        GuideHooks.clipSeeds = null
        GuideHooks.barCount = null
        GuideHooks.barHolds = null
        GuideHooks.clipDismissals = null
        // A held status must not outlive the keyboard.
        if (hold.held) hideHeldWindow()
        unregisterReceiver(screenOn)
        unregisterReceiver(screenOff)
        if (idleBarTriggerRegistered) {
            unregisterReceiver(idleBarTrigger)
            unregisterReceiver(panelTrigger)
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
        /** While taps may still be a gesture, a held tap looks again this often, so it types as soon as they can't be one. */
        private const val HELD_TAP_LOOK_MILLIS = 30L
        /** How long with no input before the idle scene starts on the bar. */
        private const val IDLE_BAR_MILLIS = IdleClock.MILLIS
        /** The debug build starts the idle scene at once on this broadcast, from adb. */
        private const val IDLE_BAR_ACTION = "com.titankeys.debug.IDLE_BAR"
        private const val PANEL_ACTION = "com.titankeys.debug.PANEL"
        private const val DEBUG_PACKAGE_SUFFIX = ".debug"
        /** The app opens the page in this extra when it starts. tools/checks/about.sh uses it to open About. */
        private const val OPEN_PAGE_EXTRA = "com.vandam.ink.notification.HREF"
        /** How long after the keyboard shows in an app that its touchpad mode is read. */
        private const val SLIDING_CHECK_MILLIS = 600L
        private const val SMART_LAUNCHER = "ginlemon.flowerfree"
        /** Characters of a typed clip sent each frame. */
        private const val CLIP_CHUNK = 40
        /** Characters read before the cursor to tell a sentence start. A sentence ends within a few spaces of the next one. */
        private const val SENTENCE_REACH = 64
        private const val MAX_PENDING_INPUT = 128
        private const val EMOJI_PREFERENCES = "emoji"
        private const val RECENT_EMOJI = "recent"
        /** The guide's recent emoji when you have none yet. No clown: the guide has you find that one. */
        private val GUIDE_RECENT_EMOJI = listOf("🍑", "🍆", "🤏", "💦", "😏", "🥵", "🫦", "👀", "💅", "🙈", "🔥", "🫠")
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
        /** How far back a delete flick looks to count the characters of the word it takes. */
        private const val FLICK_REACH = 64
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
        /** The word menu's choices, which the guide only shows. */
        private val MENU_CHOICES = setOf(Strip.Action.Save, Strip.Action.Unsave, Strip.Action.Favourite, Strip.Action.Favourited,
            Strip.Action.Unfavourite, Strip.Action.Block, Strip.Action.Unblock)
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
