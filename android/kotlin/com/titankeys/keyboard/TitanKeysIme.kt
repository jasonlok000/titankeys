package com.titankeys.keyboard

import android.app.KeyguardManager
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
import android.view.inputmethod.InputMethodSubtype
import android.view.inputmethod.SurroundingText
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.titankeys.connected.Connected
import com.titankeys.connected.Counts
import com.titankeys.connected.TouchSizes
import com.titankeys.engine.Calibration
import com.titankeys.engine.EmojiPages
import com.titankeys.engine.HoldEngine
import com.titankeys.engine.Swipe
import com.titankeys.engine.SwipeTyping
import com.titankeys.engine.WordCase
import com.titankeys.engine.TypingSettings
import java.io.IOException
import kotlin.math.abs
import kotlin.random.Random

/**
 * TitanKeys: swipe typing on the Titan 2 keyboard's touchpad, with a suggestion strip for the physical
 * keyboard. The keyboard window stays open while editing so the firmware can route touchpad events.
 */
class TitanKeysIme : InputMethodService() {
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
    /** The field's selection as the app last said, and where the cursor was before a Space gesture selected words: see [FieldSelection]. */
    private val selection = FieldSelection()
    /** Where the cursor was before a Space gesture selected words, so a double space can put it back, or null. */
    private var cursorBeforeSelect: Int?
        get() = selection.cursorBefore
        set(value) { selection.cursorBefore = value }
    private lateinit var composer: Composer
    private lateinit var autocorrect: AutocorrectLoader
    /** Corrections for the input methods other than English that have a word list (LanguageCorrections). */
    private lateinit var languageCorrection: LanguageCorrection
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
    private var lastSelectionStart: Int
        get() = selection.start
        set(value) { selection.start = value }
    private var lastSelectionEnd: Int
        get() = selection.end
        set(value) { selection.end = value }
    private val modifiers = Modifiers()
    private var strip: Strip? = null
    /** The words the composer last put on the bar, for a strip that is made after that. */
    private var barWords = emptyList<String>()
    /** TitanKeys Debug's last bar log line ([BarLog]), so only a change is logged. */
    private var barLogged: String? = null
    /** The units picked in this field, Chinese's and Japanese's words for the gestures and the bar ([PickedWords]). */
    private val pickedWords = PickedWords()
    /** A held Backspace's repeats, where Android sends none ([BackspaceRepeat]), and its first press, which they copy. */
    private val backspaceRepeat = BackspaceRepeat()
    private var heldBackspace: KeyEvent? = null
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
    /** Whether Shift was on for [heldLetter], so its accents show in capitals. */
    private var heldShift = false
    /** The input methods that are on and the one typing now (docs/languages.md). */
    private lateinit var languages: Languages
    /** What the bar's picker shows while it's open: a held letter's accents, or the input methods on a held Sym. */
    private var picker: LanguagePicker? = null
    /** The key that picked from the picker, whose repeats and release belong to that pick. */
    private var pickerKey = -1
    /**
     * A Sym press held back while it may still become a hold, which opens the switch overlay. Let go sooner, it does what Sym
     * always does. Only with two or more input methods on, so an English-only keyboard's Sym is as it was.
     */
    private var symDown: KeyEvent? = null
    /** The held Sym opened the overlay, so its release does nothing more. */
    /** Where the current Sym press is: waiting for a hold, or the switcher it opened or closes ([SymPress]). */
    private val symPress = SymPress()
    private val symHeld = Runnable { openSwitchOverlay() }
    /** In Smart Launcher's search field the Back service hears the keys first and takes them for the keyboard. See [updateKeyWatch]. */
    private var watchingKeys = false
    private val panelKeys = PanelKeyFilter()
    /** The keys taken from Smart Launcher's search field for the keyboard. See [updateKeyWatch]. */
    private val takenKeys = TakenKeys()
    /** The key under a finger on the touchpad, for the modifier bar, or an empty string. */
    private var restingKey = ""
    private class PendingInput(var ready: Boolean, var action: (() -> Unit)?, val event: KeyEvent? = null,
                               val settlement: Contact.Settlement? = null, val heldFor: String? = null)
    private val pendingInput = ArrayDeque<PendingInput>()
    private var engineEditor: EngineEditor? = null
    private var replayedCode = false
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
            Log.w("TitanKeys", "Could not hide the suggestion strip", error)
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

    /** Any key or touch clears it at once, so it never stands in the way of typing, and a [Strip.Notice.Line] with it. */
    private fun clearTeamsEgg() {
        main.removeCallbacks(endTeamsEgg)
        if (strip?.notice == Strip.Notice.TeamsEgg || strip?.notice is Strip.Notice.Line) strip?.notice = null
    }

    /**
     * A swipe the engine couldn't take, as it failed to load: the bar says so for a moment, and what happens next, never over a
     * word's menu (bug hunt 2.1, #22). The next key or touch clears it, as it does the Teams egg.
     */
    private fun showSwipeUnavailable() {
        val strip = strip ?: return
        val typing = SwipeTyping.get(this)
        rawLog?.engine("swipe", typing.readiness().toString())
        val text = SwipeLoad.notice(typing.readiness(), typing.loadingRetries()) ?: return
        if (strip.notice is Strip.Notice.Menu) return
        strip.notice = Strip.Notice.Line(text)
        main.removeCallbacks(endTeamsEgg)
        main.postDelayed(endTeamsEgg, SwipeLoad.NOTICE_MILLIS)
    }

    /**
     * The first time an input method with held letters types, the bar says what a hold does there ([HoldHint]), once ever for
     * it, for a few seconds or until the next key or touch, as the swipe notice does. Never over a word's menu or a picker.
     */
    private fun showHoldHint() {
        val strip = strip ?: return
        val method = languages.current
        val text = HoldHint.of(method) ?: return
        if (TypingSettings.holdHintShown(this, method.id)) return
        if (strip.notice != null && strip.notice !is Strip.Notice.Line) return
        TypingSettings.holdHintShown(this, method.id, true)
        strip.notice = Strip.Notice.Line(text)
        main.removeCallbacks(endTeamsEgg)
        main.postDelayed(endTeamsEgg, HOLD_HINT_MILLIS)
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
            Log.w("TitanKeys", "Could not keep the boot of the Teams word", error)
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
            Log.w("TitanKeys", "Could not show the suggestion strip", error)
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
                Log.w("TitanKeys", "Could not show the suggestion strip", error)
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
            Log.w("TitanKeys", "Could not hide the suggestion strip", error)
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
        tally("gesture.doubleSpace")
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
        if (cursorGesture.confirmTriple()) {
            finishHeldTaps(type = false)
            tally("gesture.tripleSpace")
        }
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
        deleteLog("delete animation: dots ${gesture.dots}")
        val scale = if (GuideMotion.enabled) ValueAnimator.getDurationScale() else 0f
        val dotsAt = gesture.dotsAt
        haptics.holdDots(gesture.dotLandings(scale)) { dotsAt != null && strip?.gesture?.dotsAt == dotsAt }
    }

    private fun nextDeleteHoldStep(at: Long) {
        main.removeCallbacks(stepDeleteHold)
        main.postAtTime(stepDeleteHold, at)
    }

    /** The short gap after a word swipe belongs to that word. In other languages every flick stands on its own. */
    private fun canStartFlick(time: Long) = contact.deleteFlicks && (!contact.wordSwipes || pieces.canDelete(time))

    /** The finger moved, so the wait for a hold starts again, or ends if the finger is no longer on a delete flick. */
    private fun followDeleteHold() {
        val start = contact.points.firstOrNull()
        val at = deleteHold.moved(!altOrPanel && start != null && canStartFlick(start.time))
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
                if (altOrPanel || !contact.deleteFlicks) return@enqueue
                // With nothing to delete, no hold starts: no words on the bar, no dots, only the flick's wall (owner, 2026-11-12).
                val before = currentInputConnection?.getTextBeforeCursor(FLICK_REACH, 0)
                val selected = selectionToDelete()
                if (!GestureDisplay.nothingToDelete(before, selected)) {
                    strip?.gesture?.deleteHeld(deleteHold.repeatMillis, SystemClock.uptimeMillis(), deleteHold.pace,
                        before, selected, wordBreaker())
                    // The dots at the lift keep clear of this hold's ticks.
                    haptics.holdStarted()
                    // The guide wants to know that this delete stays down, before its first word goes.
                    KeyboardEvents.publish { KeyboardEvent.DeleteHoldStarted }
                    tally("gesture.deleteHold")
                }
                outcome(flick)
            }
            return
        }
        val word = heldWords.next()
        enqueue {
            if (altOrPanel || !contact.deleteFlicks) return@enqueue
            // A code still being built goes first, as a flick takes it: the pending code, then what was picked, typed less its last
            // character (owner, 2026-11-17: "the same shd work for holding for delete too").
            val engine = languages.engine
            if (engine != null && (openPicker() as? LanguagePicker.Choices)?.state?.composing == true) {
                haptics.heldDeleteWord(word, deleteHold.pace, deleteHold.repeatMillis)
                engineState(engine.flick())
                strip?.gesture?.wordDeleted(SystemClock.uptimeMillis(), 1)
                KeyboardEvents.publish { KeyboardEvent.DeletedWord(held = true) }
                return@enqueue
            }
            // At the start of the text the hold hits a wall: the same wall as a flick with nothing to delete, played clean, once,
            // and the hold stays quiet while the finger stays, with no words left on the bar, only the caret.
            val selected = selectionToDelete()
            if (GestureDisplay.nothingToDelete(currentInputConnection?.getTextBeforeCursor(1, 0), selected)) {
                if (deleteWall.reached()) haptics.wall(Haptic.deleteFoundNothing(tookAny = true))
                strip?.gesture?.deleteText("")
                return@enqueue
            }
            deleteWall.left()
            haptics.heldDeleteWord(word, deleteHold.pace, deleteHold.repeatMillis)
            // A dot for each character of the word, for the dots that play when the hold lifts, and the real words for the bar.
            val before = currentInputConnection?.getTextBeforeCursor(FLICK_REACH, 0)
            strip?.gesture?.deleteText(before, selected, wordBreaker())
            val deletes = editor.deletes
            composer.backspace(true)
            strip?.gesture?.wordDeleted(SystemClock.uptimeMillis(), deleteDots(deletes, before, selected))
            KeyboardEvents.publish { KeyboardEvent.DeletedWord(held = true) }
        }
    }

    private fun startCursor(drag: CursorGesture.Drag, samples: List<Sample>) {
        if (strip?.gesture?.softDelete == true) interruptGesture()
        if (inGuide && !guideAllows(GuideLesson.Cursor)) {
            refuseSpaceGesture()
            return
        }
        if (drag.words && inGuide && !guideAllows(GuideLesson.Select)) {
            refuseSpaceGesture(GuideLesson.Select)
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
        tally(if (drag.words) "gesture.selectWords" else "gesture.steer")
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
        if (words && inGuide && !guideAllows(GuideLesson.Select)) {
            refuseSpaceGesture(GuideLesson.Select)
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
        val text = before.toString() + after
        val caret = before.length
        val breaks = wordBreaker()
        val word = WordSelection.at(text, caret, caret, breaks) ?: (caret to caret)
        strip?.gesture?.wordText(text, word, breaks,
            moreBefore = caret >= GestureDisplay.LINE_REACH, moreAfter = after.length >= GestureDisplay.LINE_REACH)
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
        // The cursor slide teaches two taps only: three, which select, are refused there (owner, 2026-11-16: "for double tap space
        // it shd ban triple space").
        if (inGuide && !guideAllows(GuideLesson.Select)) {
            refuseSpaceGesture(GuideLesson.Select)
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
        // Rime's data copy keeps only the Chinese input methods that are on (docs/studies/3.0-storage.md).
        com.titankeys.rime.RimeAssets.schemasOn = InputMethodList::rimeSchemasOn
        // The word menu, the Sym panel and the swipe's path all line up with the keys as Key calibration says.
        KeyCalibration.load(this)
        Connected.start(this)
        UpdateNotice.ran(this)
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
        languages = Languages(this, EngineWords(SwipeTyping.get(this), FavouriteWords.get(this)::contains),
            { InputMethodList.idOf(currentInputMethodSubtypeOrNull()) }) { inputMethodChanged(it) }
        languages.preparing = { showPreparing(it) }
        languages.warmAtStart()
        composer = Composer(editor, languages.words,
            { delay, action -> main.postDelayed(action, delay) }, ::clearSettlements,
            // The automatic Shift decides a sentence's capital, so the composer adds none of its own on top. See [updateAutoShift].
            { modifiers.autoShifted }) { barWords = it; strip?.words = it; rawLog?.bar(it); offerBarWords(it) }
        languages.words.shown = { if (ChineseKeys.usesBar(languages.current)) ChineseRow.ASK else BarWords.SHOWN }
        composer.autoPunctuation = { TypingSettings.autoPunctuationIn(this, languages.languageId) }
        composer.sentenceSpaces = {
            when (TypingSettings.sentenceSpacesIn(this, languages.languageId)) {
                "two" -> 2
                "none" -> 0
                else -> 1
            }
        }
        composer.commaSpace = { TypingSettings.commaSpaceIn(this, languages.languageId) }
        // Each language's own full stop for a double space, and its own words for the gestures that take one (owner, 2026-11-16).
        composer.fullStopMark = { languages.current.rules.fullStop }
        editor.wordBreaker = ::wordBreaker
        composer.sentenceCapitals = { TypingSettings.autoCapitaliseIn(this, languages.languageId) && languages.current.rules.capitalise }
        // Every fix is English's, "i" to "I" included: Italian's "i" is a word (docs/languages.md).
        composer.fixOn = { kind -> languages.current.isEnglish && TypingSettings.fixOn(this, kind.id) }
        composer.onStroke = Trace::stroke
        // Backspace putting a word back plays nothing: the key press is felt already.
        composer.onAutocorrect = { haptics.play(Haptic.Autocorrect) }
        composer.onFixed = { typed, word -> KeyboardEvents.publish { KeyboardEvent.Fixed(typed, word) } }
        // Sharing's daily totals (#96): the composer counts only in fields that allow learning; the guide's own fields never count.
        composer.count = { key -> if (!inGuide) Counts.add(key) }
        composer.swipeRead = { millis -> if (!inGuide) Counts.add("speed.swipe." + Counts.swipeBand(millis)) }
        composer.uptime = SystemClock::uptimeMillis
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
        languageCorrection = LanguageCorrection(this, languages.words)
        // Another input method's likeliest word for a slip shows on the bar, even where Space won't put it in.
        languages.words.suggest = { word, before -> languageCorrection.autocorrect?.corrector?.suggestion(word, before) }
        phoneNames = PhoneNameSource(this, SwipeTyping.get(this)).also { it.start() }
        // The firmware hands touchpad events to the topmost window titled "InputMethod", which is this
        // keyboard's window. They arrive at the window, not at any view, so catch them there.
        val window = window.window!!
        window.callback = TouchpadWindowCallback(window.callback) { event ->
            (editing && touchpad.accepts(event)).also { if (it) motion(event) }
        }
        overlay = TouchpadOverlay(this, touchpad = overlayEvent)
        TypingSettings.listen(this, overlaySetting)
        barSize = FixedScale.listen(this) { main.post(::followBarSize) }
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), RECEIVER_NOT_EXPORTED)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
        // The debug build can start the idle scene at once, so that a phone check need not wait three minutes. That build is not
        // marked debuggable, so its own package name tells it apart.
        if (packageName.endsWith(DEBUG_PACKAGE_SUFFIX)) {
            registerReceiver(idleBarTrigger, IntentFilter(IDLE_BAR_ACTION), RECEIVER_EXPORTED)
            registerReceiver(panelTrigger, IntentFilter(PANEL_ACTION), RECEIVER_EXPORTED)
            idleBarTriggerRegistered = true
            // The touch simulator's events enter where the firmware's do: this window's touchpad path (TouchpadFeedHook).
            var feedContact: Pair<Long, EditorInfo>? = null
            var feeding = false
            window.callback = TouchpadWindowCallback(window.callback) { event ->
                if (!feeding && touchpad.accepts(event)) feedContact = null
                false
            }
            TouchpadFeedHook.dispatch = { event ->
                val info = checkNotNull(currentInputEditorInfo?.takeIf { editing && it.packageName == packageName })
                if (event.actionMasked == MotionEvent.ACTION_DOWN) feedContact = event.downTime to info
                feeding = true
                try {
                    window.callback?.dispatchGenericMotionEvent(event) == true
                } finally {
                    feeding = false
                    if (event.actionMasked == MotionEvent.ACTION_UP) feedContact = null
                }
            }
            TouchpadFeedHook.cancelContact = { downTime ->
                val original = feedContact
                if (original?.first == downTime) {
                    feedContact = null
                    if (currentInputEditorInfo === original.second) {
                        endDeleteHold()
                        contact.cancelActive()
                        stopCursor()
                        main.removeCallbacks(confirmDoubleTap)
                        main.removeCallbacks(confirmTriple)
                        main.removeCallbacks(confirmTwoAbove)
                        main.removeCallbacks(registerGestureLater)
                        cursorGesture.cancel(SystemClock.uptimeMillis())
                    }
                }
            }
            // Its keys enter where Android's do for the keyboard: onKeyDown and onKeyUp. One the keyboard doesn't take goes on
            // to the app, as Android would send it (KeyFeedHook).
            val feedKeys = HashMap<Int, Pair<Long, EditorInfo>>()
            KeyFeedHook.dispatch = { event ->
                val code = event.keyCode
                val info = checkNotNull(currentInputEditorInfo?.takeIf { editing && it.packageName == packageName })
                val connection = checkNotNull(currentInputConnection)
                val down = event.action == KeyEvent.ACTION_DOWN
                if (down && event.repeatCount == 0) feedKeys[code] = event.downTime to info
                val taken = if (down) onKeyDown(code, event) else onKeyUp(code, event)
                // If handling the key ended input, it must never fall through to a replacement connection.
                if (!taken && currentInputEditorInfo === info && currentInputConnection === connection) connection?.sendKeyEvent(event)
                if (!down) feedKeys.remove(code)
                taken
            }
            KeyFeedHook.cancelHeld = { downTimes ->
                val current = currentInputEditorInfo
                val held = downTimes.filter { (code, time) ->
                    val original = feedKeys[code]
                    if (original?.first != time) false else {
                        feedKeys.remove(code)
                        current === original.second
                    }
                }
                if (held.isNotEmpty()) {
                    fun owns(event: KeyEvent?) = event?.let { held[it.keyCode] == it.downTime } == true
                    if (owns(heldBackspace)) {
                        backspaceRepeat.up()
                        main.removeCallbacks(repeatBackspace)
                        modifiers.backspaceReleased()
                        heldBackspace = null
                    }
                    if (owns(heldLetter)) {
                        holdEngine.cancel()
                        heldLetter = null
                    }
                    if (owns(symDown)) {
                        main.removeCallbacks(symHeld)
                        symDown = null
                        symPress.reset()
                    }
                    pendingInput.removeAll { owns(it.event) }
                    modifiers.cancelFeedKeys(held)
                    val now = SystemClock.uptimeMillis()
                    for ((code, downTime) in held) {
                        // A real press of this key may have superseded the feed's down.
                        contact.cancelFeedKey(code, downTime, now)
                    }
                    showModifiers()
                }
            }
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
                Log.w("TitanKeys", "Could not read the emoji shortcodes", error)
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
        // Whether the bar may play is asked as the roll comes, not as the period starts: a period that started with the Sym panel
        // open still gets its roll once the panel is closed (bug hunt #33). A period whose roll finds the bar can't play doesn't
        // count, as one that input ended doesn't.
        if (!idleBarAllowed()) return@Runnable
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
                    Log.i("TitanKeys", if (sent) "Idle bar: going home by $chosen" else "Idle bar: no game is playing, or no way home is called $way")
                    return@post
                }
                val kind = game?.let { name ->
                    IdlePlayer.Kind.entries.firstOrNull { it.name.lowercase() == name || it.number.toString() == name || name == "rope" && it == IdlePlayer.Kind.JumpRope }
                }
                if (game != null && kind == null) {
                    Log.i("TitanKeys", "Idle bar: no game called $game. Shell (4) is a way home: use the extra return")
                    return@post
                }
                // A game that plays now starts again as the one asked for.
                strip?.stopIdle()
                val started = startIdleBar(kind)
                Log.i("TitanKeys", if (started) "Idle bar: started ${kind ?: "a game picked by rarity"}"
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
                    Log.i("TitanKeys", "Panel check: no field has the keyboard up")
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
                        Log.i("TitanKeys", "Panel check: no page called $page. Use symbols, emoji, clipboard, quick or none")
                        return@post
                    }
                }
                val held = when (mod) {
                    null, "none" -> null
                    "ctrl" -> KeyEvent.KEYCODE_CTRL_LEFT
                    "shift" -> KeyEvent.KEYCODE_SHIFT_LEFT
                    "alt" -> KeyEvent.KEYCODE_ALT_LEFT
                    else -> { Log.i("TitanKeys", "Panel check: no modifier called $mod. Use ctrl, shift or alt"); null }
                }
                if (held != null) {
                    modifiers.down(held, now)
                    debugHeld += held
                }
                val key = mark?.let { Layout.keyForCode(it) }
                if (mark != null && key == null) Log.i("TitanKeys", "Panel check: key code $mark is no key of the layout")
                showModifiers()
                // A resting finger on [key], as the touchpad would report it: the panel's mark and the bar's. After the page is
                // open, since a new page starts with no mark.
                restingKey = key.orEmpty()
                panel?.hover = if (panelOpen) key.orEmpty() else ""
                showModifierBar()
                Log.i("TitanKeys", "Panel check: page ${modifiers.panel}, mark ${key ?: "none"}, held ${mod ?: "none"}")
            }
        }
    }

    /** The screen is on and unlocked. Smart Launcher's taken keys stop at once otherwise. See [watchedKey]. */
    private fun screenInUse() = getSystemService(PowerManager::class.java).isInteractive &&
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true

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
        main.postDelayed(idleBar, IDLE_BAR_MILLIS)
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
    /**
     * The touchpad overlays: this keyboard's own, and the Back service's above the shade. Only while this keyboard is Android's
     * current one and alive. Android's own onDestroy finishes the input (doFinishInput, so onFinishInput and this run) after
     * ours has taken the overlays down: without [destroyed], a keyboard on its way out put its overlay up again, with the
     * Back service's window pointing at it, on every switch to another keyboard. The firmware gives the touchpad to the topmost
     * window titled "InputMethod", so those dead windows took it from whichever keyboard came next (2.1 left 96 on the
     * owner's phone, 2026-11-16).
     */
    private fun updateOverlay(raise: Boolean = false) {
        val wanted = !destroyed && PhoneSetup.selected(this) && (editing || TypingSettings.touchpadOnlyInFields(this))
        if (wanted) overlay.show() else overlay.hide()
        // At a field start, back on top of any other app's leftover window of the same kind: see TouchpadOverlay.raise.
        if (wanted && raise) overlay.raise()
        main.removeCallbacks(releaseHighTouchpad)
        when {
            wanted -> BackService.touchpad(overlayEvent)
            // Gone now, not after the grace: and only its own, never a newer keyboard's.
            destroyed -> BackService.releaseTouchpad(overlayEvent)
            else -> main.postDelayed(releaseHighTouchpad, HIGH_GRACE_MILLIS)
        }
    }

    /** onDestroy has begun: nothing may put the touchpad's windows up again. See [updateOverlay]. */
    private var destroyed = false

    private val releaseHighTouchpad = Runnable { BackService.releaseTouchpad(overlayEvent) }

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
            Log.w("TitanKeys", "Could not report the keyboard status", error)
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
            Log.w("TitanKeys", "Could not clear the keyboard status", error)
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

    /** The quick settings that can't work in [method]'s language leave the panel while it types (QuickSetting.hiddenIn). */
    private fun showQuickSettingsFor(method: InputMethod) {
        panel?.hiddenKeys = QuickSetting.hiddenIn(LanguageSettings.rows(method)).map { it.key }.toSet()
        panel?.render()
    }

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
                Log.w("TitanKeys", "Could not read the emoji list", error)
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
            if (!guideBlocked(GuideLesson.BarTap)) {
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
        // TitanKeys Debug logs what the bar shows while log.tag.TitanKeysBar is D, for the phone checks (BarLog).
        if (packageName.endsWith(DEBUG_PACKAGE_SUFFIX)) words.log = { shown ->
            if (android.util.Log.isLoggable(BarLog.TAG, android.util.Log.DEBUG)) {
                val id = if (::languages.isInitialized) languages.current.id else InputMethod.ENGLISH_ID
                // Only a change: the same bar in another input method is a change too.
                val line = BarLog.line(id, shown)
                if (line != barLogged) android.util.Log.d(BarLog.TAG, line)
                barLogged = line
            }
        }
        if (::languages.isInitialized) showInputMark()
        if (::languages.isInitialized) words.rightToLeft = languages.current.rightToLeft
        panel = keys
        if (::languages.isInitialized) keys.hiddenKeys = QuickSetting.hiddenIn(LanguageSettings.rows(languages.current)).map { it.key }.toSet()
        keys.emoji = emoji
        // Android can make this view after the field has started, and again when the screen settings change.
        words.words = barWords
        words.selected = barSelected
        // Exact typing's bar starts hidden and is made before the first check, so a normal field never shows it.
        val exact = ExactBar(ui, ::exactChip, ::exactBarKey).apply { visibility = View.GONE }
        exactBar = exact
        words.showSuggestions = showSuggestions()
        showModifiers()
        // Chinese's bigger menu, that holding P opens, in the Sym panel's place (docs/languages.md, "Chinese on the bar").
        val choices = ChoicesMenu(ui) { index -> enqueue { pickFromMenu(index) } }.apply { visibility = View.GONE }
        choicesMenu = choices
        // A tap on the screen on one of the picker's items picks it, as its key would.
        words.moreTap = { enqueue { if (openPicker() is LanguagePicker.Choices && !choicesMenuOpen) openChoicesMenu() } }
        words.markTap = { enqueue { tapMark() } }
        words.pickerTap = { index ->
            enqueue {
                openPicker()?.let { open ->
                    // The row skips a choice too wide for the room left: the tap's place is the item's place in the row.
                    val choice = if (open is LanguagePicker.Choices) open.itemAt(index) else index
                    if (choice >= 0 && open is LanguagePicker.Choices) tallyChoices("pick.tap")
                    if (choice >= 0) pick(open, choice)
                }
            }
        }
        val keyboard = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            addView(keys)
            addView(choices)
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
        // The keyboard sits on the bottom of a window as tall as the screen. See [fillScreen]. Its root feels every tap on the
        // screen that plays nothing of its own ([TapHaptics]).
        return TapHaptics(ui) { downTime ->
            val own = haptics.playedSince(downTime)
            if (!own) haptics.play(Haptic.ScreenTap)
            if (AppBuild.kind(this) == "debug") Log.i("TitanKeysTap", if (own) "the tap played its own haptic" else "screen_tap played")
        }.apply {
            if (AppBuild.kind(this@TitanKeysIme) == "debug") log = { line -> Log.i("TitanKeysTap", line) }
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
        val exact = ExactTyping.applies(info.packageName, ExactTyping.apps(this))
        val nextEditor = EngineEditor(info.packageName, info.fieldId, info.inputType, info.imeOptions,
            info.privateImeOptions, currentInputConnection)
        val samePlace = nextEditor.continues(engineEditor, restarting, lastSelectionStart, lastSelectionEnd,
            info.initialSelStart, info.initialSelEnd) && editing && !exact && !field.exact && !field.secret && field != Field.None &&
            !composer.mayLeaveField && !deadKeyPending && !panelOpen && modifiers.alt == Modifiers.Level.Off &&
            (modifiers.shift == Modifiers.Level.Off || modifiers.autoShifted)
        engineEditor = nextEditor
        val target = languages.target
        val held = if (samePlace) loadGate.takeKeys(pendingInput) { it.heldFor == target } else emptyList()
        val code = (picker as? LanguagePicker.Choices)?.takeIf {
            samePlace && replayedCode && it.state.composing && !languages.loadingNow
        }
        // Another field starts a new delete streak: words deleted somewhere else don't count here (#89).
        // A press from the field before must not change a letter here.
        holdEngine.cancel()
        heldLetter = null
        main.removeCallbacks(symHeld)
        symDown = null
        symPress.reset()
        closePicker()
        // A subtype restart can follow replay before Space commits its syllable.
        if (code == null) languages.engine?.reset()
        // The units picked belong to the field before ([PickedWords]), unless the same one restarts.
        if (!restarting) pickedWords.clear()
        preserveDestinationInput()
        editor.cancelState()
        super.onStartInput(info, restarting)
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
        // Every field gets a start line with its app; only ones whose typing may be recorded get a session (RawLog.start).
        rawLog = if (editing) RawLog.start(this, info, restarting, textAllowed = !exact && !ownSettings) else null
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
        // A swipe engine that failed to load tries again on a new text box, a few times at most (bug hunt 2.1, #22).
        if (editing && !restarting) SwipeTyping.get(this).let { typing ->
            if (SwipeLoad.retryNow(typing.readiness(), typing.loadingRetries())) typing.retryLoading { }
        }
        // This field's own selection, nothing of the last one's (bug hunt 2.1, #17), and the bar shows it if it starts with one:
        // Android tells of no selection change for it (the guide's word menu slide starts with TitanKeys selected).
        selection.started(info.initialSelStart, info.initialSelEnd)
        selectedWord = null
        if (editing && field.suggestions) showSelectedWord(info.initialSelStart, info.initialSelEnd) else strip?.selected = null
        if (editing) {
            composer.start(field, info.initialSelStart, info.initialSelEnd)
            if (code != null && !languages.loadingNow && languages.current.id == target) {
                showPicker(code)
                replayedCode = true
            }
            if (held.isNotEmpty() && languages.target == target && !languages.englishOnly) {
                loadGate.before(languages.loadingNow, pendingInput)
                pendingInput.addAll(held)
                drainInput()
                showHeldLetters()
            }
            main.post(showStrip)
        } else {
            composer.stop()
            // A returning window starts with no field just before its own field takes focus. A strip that
            // hides for that moment tells the app the keyboard was closed, and the app then drops its field.
            main.postDelayed(hideStrip, NO_FIELD_HIDE_MILLIS)
        }
        updateOverlay(raise = editing)
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
        // The guide's lessons teach English, so they type it whatever input method is on. The playground is free practice: it
        // types in the input methods that are on, and a held Sym opens the switcher there (owner, 2026-11-17: "long press for
        // sym doesn't trigger the language selection in the playground").
        languages.englishOnly = inGuide
        languages.refresh()
        // Which input methods are on may have changed, and with them the mark at Q.
        if (::languages.isInitialized) showInputMark()
        languages.variant()
        val english = languages.current.isEnglish
        // Each input method's own sentence ends and opening marks, for capitals and spaces (Greek's ;, Hindi's ।, Spanish's ¿¡).
        com.titankeys.engine.SentenceCase.ends(languages.current.rules.sentenceEnd, languages.current.rules.openingMarks)
        com.titankeys.autocorrect.TypedCorrector.sentenceEnd = { com.titankeys.engine.SentenceCase.isEnd(it.code) }
        composer.liftOffMillis = TypingSettings.liftOffGraceMillis(this)
        tapKeys = TypingSettings.tapKeys(this)
        holdSymbols = TypingSettings.holdSymbolsIn(this, languages.languageId)
        holdEngine.threshold(TypingSettings.holdSymbolMillis(this))
        modifiers.fnIsCtrl = TypingSettings.fnAsCtrl(this)
        modifiers.doublePressLocks = TypingSettings.doublePressLocks(this)
        modifiers.altSymSwitches = TypingSettings.altSymNextKeyboard(this)
        cursorControl = TypingSettings.cursorControl(this)
        exactRealAlt = ExactTyping.realAlt(this)
        // A word gesture could send the wrong keys to a terminal or a remote computer, or a word into a password.
        contact.swipes = when {
            !field.words || !TypingSettings.swipeTyping(this) -> Contact.Swipes.Off
            english -> Contact.Swipes.WordsAndDeletes
            else -> Contact.Swipes.DeletesOnly
        }
        enterApps = EnterKey.apps(TypingSettings.enterApps(this))
        // English's autocorrect and shorthand are English's own. Other input methods with a word list fix slips on their keys,
        // conservatively, with the same corrector on their own list (LanguageCorrections; owner, 2026-11-17).
        val corrects = TypingSettings.autocorrectIn(this, languages.languageId) && english
        val language = if (!english && languageCorrection.on(languages.languageId)) languageCorrection.use(languages.current, languages.list)
            else null.also { languageCorrection.clear() }
        // Spell out shorthand is its own setting: with Fix typos off, the word data spells out shorthand alone (bug hunt #13).
        val shorthandAlone = !corrects && TypingSettings.expandShorthand(this) && english
        composer.autocorrect = autocorrect.autocorrect.takeIf { corrects } ?: language
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
    /**
     * An engine is building text: a code pending on the bar, or Chinese's bigger menu open. Enter and Backspace are then the
     * keyboard's, kept from apps that take them first (PanelKeyFilter), as on the Sym panel: Enter types the code, or turns the
     * menu's page, and never sends.
     */
    private val engineBuilding get() = choicesMenuOpen || (picker as? LanguagePicker.Choices)?.state?.composing == true

    private fun updateKeyWatch() {
        val launcher = editing && currentInputEditorInfo?.packageName == SMART_LAUNCHER
        val forPanel = panelOpen || panelKeys.holding || engineBuilding
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
        if (panelKeys.takes(event.keyCode, down, event.repeatCount, modifiers.panel, engineBuilding)) {
            // Once the panel has closed, a key that it took only waits for its release.
            if (!down) onKeyUp(event.keyCode, event) else if (panelOpen || engineBuilding) onKeyDown(event.keyCode, event)
            updateKeyWatch()
            return true
        }
        val code = event.keyCode
        // The app takes Back before the keyboard, so an open panel must close here.
        if (watchingKeys && code == KeyEvent.KEYCODE_BACK && isInputViewShown && backKey(event, down)) return true
        // Locked or asleep, the keys are the lock screen's, such as a PIN, even if Android left the launcher's field open.
        if (!takenKeys.takes(code, down, watchingKeys && screenInUse())) {
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
    /**
     * TitanKeys Debug only: what a delete's animation shows, as counts, never text, for the phone checks of #89:
     * "delete animation: dots N". adb logcat -s TitanKeysDelete
     */
    private fun deleteLog(line: String) {
        if (AppBuild.kind(this) == "debug") Log.i("TitanKeysDelete", line)
    }

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

    /** Hears Bar text size picked in Settings while the keyboard is up, as on its own page's field (#86). */
    private var barSize: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /**
     * Makes the bar again at once at a new Bar text size, so a pick shows while you type. The bar keeps its words and selection
     * ([onCreateInputView]). A keyboard that isn't up, or one in exact typing, catches up as the next field starts instead.
     */
    private fun followBarSize() {
        if (!isInputViewShown || field.exact) return
        if (FixedScale.size(this) != viewSize) setInputView(onCreateInputView())
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) timeOpening()
        // Bar text size changed since the bar was made, by a pick or the phone's text size, so it's made again at the new size.
        if (FixedScale.size(this) != viewSize) setInputView(onCreateInputView())
        // The bar may only now be set up for this field, so the wait starts once it is. A restart of the same field is no input: it
        // keeps the period and any game, unless none is running, as after the keyboard was hidden (bug hunt #34).
        if (!restarting || !idleClock.running) main.post { wakeIdleBar() }
        // The hold hint also shows as a field starts, not only as the input method changes in one: one picked in Settings, or
        // loaded with the keyboard away, is never changed in a field, and its hint never showed. Once ever, as before.
        main.post { if (editing) showHoldHint() }
        offerClip()
        // Settings > Setup lists the apps whose Scroll assistant isn't in Sliding Mode 1. The system sets the touchpad's mode
        // for the app in front as it comes in, so this reads it a moment later and again seconds later, if the keyboard is
        // still in that field. SlidingModeList decides what to trust.
        slidingField = info.packageName?.let { app ->
            SlidingField(app).also { field ->
                main.postDelayed({ readSlidingMode(field, late = false) }, SLIDING_CHECK_MILLIS)
                main.postDelayed({ readSlidingMode(field, late = true) }, SLIDING_CONFIRM_MILLIS)
            }
        }
        // Back belongs to the app, even though it also hides the strip on the way.
        backDisposition = BACK_DISPOSITION_WILL_NOT_DISMISS
    }

    /**
     * Sharing's speed bands (#96): how long the keys take to draw for a new field, from Android asking for them to the next
     * draw. Only while Sharing is on, in the same fields as every count.
     */
    private fun timeOpening() {
        if (!Counts.on) return
        val started = SystemClock.uptimeMillis()
        val view = window.window?.decorView ?: return
        val observer = view.viewTreeObserver
        observer.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(this)
                tally("speed.open." + Counts.openBand(SystemClock.uptimeMillis() - started))
                return true
            }
        })
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
        // A panel that nobody can see must not take the next key, nor a word menu.
        closePanel()
        if (MenuKeys.closes(altOrPanel, hidden = true, guideShowOnly = inGuide)) closeMenu()
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
            Log.w("TitanKeys", "Could not read the clipboard", error)
            null
        } ?: return
        if (clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true) return
        if (editing && field.secret) return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).text?.toString() ?: return
        // The guide's own clip to paste is never kept in the user's clipboard, even when it lands as the keyboard serves another
        // field (bug hunt 2.1, #31).
        if (clip.description?.label == GuideHooks.CLIP_LABEL && !inGuide) return
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
            Log.w("TitanKeys", "Could not save the clipboard history", error)
            return
        }
        try {
            stream.write(clipHistory.encode().toByteArray(Charsets.UTF_8))
            clipFile.finishWrite(stream)
        } catch (error: java.io.IOException) {
            clipFile.failWrite(stream)
            Log.w("TitanKeys", "Could not save the clipboard history", error)
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
        // Another language: the word's place is in its own list and what it learned; Blocked words and Favourites are shared.
        otherLearning()?.let { learning ->
            val saved = learning.isSaved(word)
            return Strip.Notice.Menu(word, MenuKeys.step(
                blocked = typing.blockedWord(word),
                favourite = FavouriteWords.get(this).contains(word),
                saved = saved,
                learned = !saved && learning.knows(word),
                builtIn = languages.words.listed(word),
                unsaved = learning.isHidden(word)))
        }
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
        Log.w("TitanKeys", "Could not $what a word: $error")
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

    /**
     * A Space gesture refused: the rest of the touch neither steers, nor selects, nor types. Its glow goes with the lift. [lesson]
     * is what the slide locks: the Space taps, or only the three that select ([GuideLesson.Select]).
     */
    private fun refuseSpaceGesture(lesson: GuideLesson = GuideLesson.Cursor) {
        refuse(lesson)
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
        if (choicesMenuOpen) {
            val menu = choicesMenu ?: return
            if (!quiet && Layout.isLetter(key) && key != menu.hover && menu.choice(key) >= 0) haptics.play(Haptic.MenuMark)
            menu.hover = key
            return
        }
        val strip = strip ?: return
        openPicker()?.let { open ->
            // The choices' row shows no hover: only the bigger menu does (owner, 2026-11-17).
            if (open is LanguagePicker.Choices) {
                strip.pickerMark = -1
                return
            }
            val column = Picker.column(key)
            val mark = column.takeIf { c -> open.notice.columns.any { c in it } } ?: -1
            // The tick plays as the mark moves to another tile, as the panels' mark does, not for another key under the same
            // tile: the switcher's tiles are two to eight keys wide (owner, 2026-11-17).
            val tile = open.notice.columns.indexOfFirst { mark in it }
            val before = open.notice.columns.indexOfFirst { strip.pickerMark in it }
            if (!quiet && tile >= 0 && tile != before) haptics.play(Haptic.MenuMark)
            strip.pickerMark = mark
            return
        }
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
    /** What the input method typing now learned, when it isn't English; null for English, whose engine keeps its own. */
    private fun otherLearning() = if (::languages.isInitialized && languages.words.other) languages.words.learning else null

    private fun saveWord(held: HeldWord) {
        otherLearning()?.let { learning ->
            learning.save(keptCase(held))
            languages.learningChanged()
            composer.unhide(held.word)
            menuPressed(held, MenuKeys.Button.Save)
            return
        }
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
        otherLearning()?.let { learning ->
            learning.unsave(held.word, listed = languages.words.listed(held.word))
            languages.learningChanged()
            composer.hide(held.word)
            menuPressed(held, MenuKeys.Button.Unsave)
            return
        }
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
        // Another language: a favourite it didn't otherwise know goes into its saved words.
        otherLearning()?.let { learning ->
            if (!learning.knows(held.word) && (!languages.words.listed(held.word) || learning.isHidden(held.word))) {
                learning.save(keptCase(held))
                languages.learningChanged()
            }
            menuPressed(held, MenuKeys.Button.Unfavourite)
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
        val text = currentInputConnection?.getTextBeforeCursor(SENTENCE_REACH + held.word.length + held.typed.length + 2, 0)
        return WordCase.kept(held.word, WordCase.before(text, held.word, held.typed.length), modifiers.shift != Modifiers.Level.Off)
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
        SwipeTyping.get(this).unblockWord(held.word) { error ->
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
        barSelected = shown
        strip?.selected = shown
    }

    /** The selected word, when the selection is one word, which its menu can save or forget; null for anything else. */
    private var selectedWord: String? = null

    /** The selection the bar shows, for a strip that is made after it, as [barWords] is. */
    private var barSelected: String? = null

    override fun onFinishInput() {
        engineEditor = null
        // A held Backspace stops with its field.
        backspaceRepeat.up()
        main.removeCallbacks(repeatBackspace)
        closeMenu()
        barSelected = null
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
            Log.w("TitanKeys", "Could not read a raw log mark", error)
        }
    }

    override fun onUpdateCursorAnchorInfo(info: CursorAnchorInfo) {
        super.onUpdateCursorAnchorInfo(info)
        editor.cursorAnchor(info)
    }

    /** While the strip is hidden, the touchpad reaches the keyboard through the focused app. */
    /** The field the Sliding Mode readings are for, with what they've seen so far. A new field starts a new one. */
    private class SlidingField(val app: String) {
        var early: String? = null
        var mode1Touch = false
    }

    private var slidingField: SlidingField? = null

    private fun readSlidingMode(field: SlidingField, late: Boolean) {
        val sameField = slidingField === field && currentInputEditorInfo?.packageName == field.app && isInputViewShown
        val reading = if (sameField) PhoneChecks.slidingReading(this, field.app) else null
        if (AppBuild.kind(this) == "debug")
            Log.i("TitanKeysSliding", "${field.app} ${if (late) "late" else "early"}: $reading (same field $sameField, mode 1 touch ${field.mode1Touch})")
        if (!sameField) return
        if (reading == SlidingModeList.MODE_1) PhoneChecks.mode1(this, field.app)
        if (!late) field.early = reading
        else if (SlidingModeList.trustworthy(field.early, reading, sameField, field.mode1Touch)) PhoneChecks.outsideReading(this, field.app)
    }

    /** A touchpad touch only Sliding Mode 1 sends takes the app off Setup's list at once, once per field. */
    private fun noteSlidingTouch(event: MotionEvent) {
        val field = slidingField ?: return
        if (field.mode1Touch || event.actionMasked != MotionEvent.ACTION_DOWN || !touchpad.sentAsScreenTouch(event)) return
        if (currentInputEditorInfo?.packageName != field.app) return
        field.mode1Touch = true
        PhoneChecks.mode1(this, field.app)
    }

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
        noteSlidingTouch(event)
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
                flickCrunch.begin(!altOrPanel && canStartFlick(first.time) && somethingToDelete(first))
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
                val carries = contact.wordSwipes && contact.points.isNotEmpty() && lastTouch?.let {
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
                // Only the latest swipe's worth of points: one long stream of moves with no DOWN between (a resting finger, or
                // Debug's TouchFeed) grew this until the heap ran out (OutOfMemoryError, 2026-11-17, the phone run's languages batch).
                cursorPath.addAll(samples.filter { it.valid })
                if (cursorPath.size > Swipe.MAX_POINTS) cursorPath.subList(0, cursorPath.size - Swipe.MAX_POINTS).clear()
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
                if (contact.deleteFlicks && !altOrPanel && travel != null && first != null && (travel >= Contact.SWIPE_KEYS
                    || !isTapKey(Layout.keyAt(first.x, first.y)))) {
                    // Clearly a delete flick, the keys it crosses crunch; once it stops being one, a break, then swiping's ticks.
                    // On a guide slide only what the slide teaches is felt: a locked one glows, and its feel is the no at the end.
                    val felt = guideAllows(if (flickCrunch.crunching) GuideLesson.Flick else GuideLesson.Swipe)
                    if (flickCrunch.move(contact.points)) {
                        if (felt) haptics.play(Haptic.DeleteBreak)
                        strip?.gesture?.flickGlowEnded(SystemClock.uptimeMillis())
                    }
                    if ((contact.wordSwipes || flickCrunch.crunching) && guideAllows(if (flickCrunch.crunching) GuideLesson.Flick else GuideLesson.Swipe)) {
                        haptics.move(samples, flickCrunch.crunching)
                    }
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
        val points = contact.points
        val travel = contact.travel
        val first = points.firstOrNull()
        val far = swipeCarried && gesture.swiping || travel != null && travel >= Contact.SWIPE_KEYS
        if (inPractice || !contact.wordSwipes || altOrPanel || travel == null || !far || first == null
            || startsDeleteFlick(first.x, first.y) && (isDeleteWordSwipe(points) || flickCrunch.mayBeFlick)) {
            gesture.swipeCancelled()
            swipeShown = 0
            return
        }
        if (swipeShown > points.size) swipeShown = 0
        // While a delete's dots play, a stroke that may be another delete draws nothing, as it would join them; above, it went by.
        // One that is clearly a swipe takes the bar (#93): the dots land at once and their ticks stop, and the trail draws whole.
        if (gesture.softDelete) {
            interruptGesture()
            swipeShown = 0
        }
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
        val flicked = !cancelled && !altOrPanel && contact.deleteFlicks && !contact.deleting && flickCrunch.lifted(stroke)
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
            // A tap or a swipe while the input method loads waits behind the same stop as a key ([waitForLoad]).
            waitForLoad("A touch")
            if (settlements.size == Contact.MAX_PENDING_CONTACTS || !makeRoom()) {
                contact.settle(pending, pending.deadline)
                return@let
            }
            val input = PendingInput(false, null, settlement = pending)
            pendingInput.addLast(input)
            val settle = Runnable {
                settlements.remove(pending)
                val settled = contact.settle(pending, SystemClock.uptimeMillis())
                val page = settled is Contact.Outcome.Swipe && settled.forPaging
                val result = if (pending.cancelled || altOrPanel || page || !contact.wordSwipes) settled else pieces.settle(pending, settled)
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
            main.removeCallbacks(showRadical)
            strip?.nextKey = ""
            return
        }
        if (last == null || !barHover.touch(last.x, last.y)) return
        restingKey = barHover.key
        markMenu(restingKey)
        showModifierBar()
        // Cangjie and Quick: a finger that stays still on a key previews its radical in the code's tab, faded ([RadicalPreview]).
        strip?.nextKey = ""
        main.removeCallbacks(showRadical)
        if (RadicalPreview.of(languages.current, restingKey) != null && TypingSettings.radicalPreview(this, languages.current.id))
            main.postDelayed(showRadical, RadicalPreview.REST_MILLIS)
    }

    /** The rested key's radical, faded at the end of the code's tab, once the finger has stayed [RadicalPreview.REST_MILLIS]. */
    private val showRadical = Runnable {
        strip?.nextKey = if (editing && !field.exact && modifiers.alt == Modifiers.Level.Off)
            RadicalPreview.of(languages.current, restingKey).orEmpty() else ""
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
            // Android moves the cursor visually, so in a right-to-left paragraph a step right goes back in the text.
            val forward = RightToLeft.forward(direction, connection != null && RightToLeft.paragraph(
                connection.getTextBeforeCursor(RTL_REACH, 0)?.toString().orEmpty(), connection.getTextAfterCursor(RTL_REACH, 0)?.toString().orEmpty()))
            val next = if (forward) connection?.getTextAfterCursor(1, 0) else connection?.getTextBeforeCursor(1, 0)
            // An app that does not answer still gets the light tick.
            cursorSteps.step(if (next == null) 'a' else next.firstOrNull())?.let(haptics::play)
            if (rereadCursorText) readCursorText()
            if (CursorSteps.sendsArrow(next, lastSelectionStart != lastSelectionEnd)) command(arrow, steer = true)
            if (next?.isEmpty() != true) rereadCursorText = strip?.gesture?.cursorStepped(direction) == true
        }
    }

    /**
     * Selects the word at the cursor, or does nothing between words but say no, then calls [nothing]. [inFront] also takes the
     * word before the cursor past marks and spaces, for a triple tap. See [WordSelection.atOrBefore].
     */
    private fun selectWord(inFront: Boolean = false, nothing: () -> Unit = {}) = changeSelection { text, start, end, _ ->
        // In Chinese, Japanese or Thai a run of letters can be a sentence: the language's word breaks pick one word in it.
        val breaks = wordBreaker()
        val word = if (inFront) WordSelection.atOrBefore(text, start, end, breaks) else WordSelection.at(text, start, end, breaks)
        haptics.play(Haptic.picked(found = word != null))
        if (word == null) {
            nothing()
            return@changeSelection null
        }
        showWordText(text, start, end, word, breaks)
        strip?.gesture?.wordSelected(SystemClock.uptimeMillis(), held = inFront && tripleDown)
        word
    }

    /**
     * The word breaks every word gesture uses (three taps, a slide, a flick, Ctrl + Backspace): in Chinese, one character
     * ([PickedWords.CHARACTERS], owner); in Japanese, the units as picked, then ICU's ([PickedWords]); elsewhere ICU's, which only
     * narrow a run of a script without spaces ([WordBreaks]).
     */
    private fun wordBreaker(): WordBreaker {
        if (::languages.isInitialized && ChineseKeys.isChinese(languages.current)) return PickedWords.CHARACTERS
        val icu = IcuWords.of(languages.current.javaLocale)
        return if (::languages.isInitialized && languages.current.id == "ja") pickedWords.breaker(icu) else icu
    }

    /** The word that ends at the end of [before], as the gestures take it: the bar's word in Chinese and Japanese (B's call). */
    fun chineseLastWord(before: String): String? = pickedWords.lastWord(before, IcuWords.of(languages.current.javaLocale))

    /** Selects the word at the cursor, the part that a word drag keeps selected. Between words the drag starts from the cursor. */
    private fun startWordDrag() {
        GestureDisplay.selectionEnd = SelectionEnd.stored(Haptics.selectionEndId(this))
        wordAnchor = null
        selectionWall.left()
        rememberCursor()
        changeSelection { text, start, end, offset ->
            // The same word three taps take ([wordBreaker]): in Chinese a unit, not the whole run.
            val breaks = wordBreaker()
            val word = WordSelection.at(text, start, end, breaks) ?: (start to end)
            wordAnchor = offset + word.first to offset + word.second
            showWordText(text, start, end, word, breaks)
            word
        }
    }

    /** Extends the selection by one word, in order with the rest of the typing. */
    private fun extendWords(direction: Int) = enqueue {
        changeSelection { text, start, end, offset ->
            val anchor = wordAnchor ?: return@changeSelection null
            val breaks = wordBreaker()
            val range = WordSelection.extend(text, start, end, anchor.first - offset, anchor.second - offset, direction, breaks)
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
            showWordText(text, start, end, anchor.first - offset to anchor.second - offset, breaks)
            strip?.gesture?.wordStep(direction)
            range
        }
    }

    /**
     * Shows the words around [anchor] on the bar, from [text], the text read around the selection from [start] to [end]. Where
     * that text is cut off at its reach, more lies beyond it.
     */
    private fun showWordText(text: CharSequence, start: Int, end: Int, anchor: Pair<Int, Int>, breaker: WordBreaker) {
        if (anchor.first !in 0..text.length || anchor.second !in anchor.first..text.length) return
        strip?.gesture?.wordText(text, anchor, breaker, moreBefore = start >= WORD_REACH, moreAfter = text.length - end >= WORD_REACH)
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
        return !GestureDisplay.nothingToDelete(currentInputConnection?.getTextBeforeCursor(1, 0), selectionToDelete())
    }

    /**
     * The field's selection, which a delete takes first and whole, or null with nothing selected. Read from the field each time,
     * as the last selection update can lag behind a delete that just took it; an app that won't read it out counts as none.
     */
    private fun selectionToDelete(): CharSequence? {
        if (lastSelectionStart < 0 || lastSelectionStart == lastSelectionEnd) return null
        // A long selection that the app won't hand over is still something to delete, and goes whole: its text is then unknown,
        // and empty here (#89).
        return currentInputConnection?.getSelectedText(0)?.takeIf { it.isNotEmpty() } ?: ""
    }

    /**
     * A delete's dots: a dot for each character it really took, not counting spaces (#89), however many. [deletes] is the
     * editor's count before the delete. A delete still waiting behind other input hasn't taken anything yet, so its dots come
     * from the text read before it, [before] and [selected], as they always did.
     */
    private fun deleteDots(deletes: Int, before: CharSequence?, selected: CharSequence?): Int =
        if (editor.deletes != deletes) GestureDisplay.dotsFor(editor.removed, editor.removedLength)
        else GestureDisplay.deleteTakes(before, selected)

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
        // A code being typed goes first, as its first choice; suggestions with no code are cleared, not picked.
        val choices = openPicker() as? LanguagePicker.Choices
        if (!field.exact && choices != null && ChineseKeys.usesBar(languages.current))
            languages.engine?.let { engineState(if (choices.state.composing) it.space() else it.reset()) }
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
        QuickSetting.Autocorrect -> TypingSettings.autocorrectIn(this, languages.languageId)
        QuickSetting.SwipeTyping -> TypingSettings.swipeTyping(this)
        QuickSetting.SuggestionBar -> TypingSettings.suggestionBar(this)
        QuickSetting.AutoCapitalise -> TypingSettings.autoCapitaliseIn(this, languages.languageId)
        QuickSetting.AutoPunctuation -> TypingSettings.autoPunctuationIn(this, languages.languageId)
        QuickSetting.Vibration -> Haptics.enabled(this)
        QuickSetting.HoldSymbols -> TypingSettings.holdSymbolsIn(this, languages.languageId)
        QuickSetting.CursorControl -> TypingSettings.cursorControl(this)
        QuickSetting.DoublePressLocks -> TypingSettings.doublePressLocks(this)
        QuickSetting.ScrollAssistant -> !TypingSettings.touchpadOnlyInFields(this)
        QuickSetting.OpenSettings, QuickSetting.ChangeKeyboard, QuickSetting.Clipboard -> true
    }

    private fun writeQuickSetting(item: QuickSetting, on: Boolean) = when (item) {
        QuickSetting.Autocorrect -> languageSetting({ TypingSettings.autocorrectIn(this, it, on) }) { TypingSettings.autocorrect(this, on) }
        QuickSetting.SwipeTyping -> TypingSettings.swipeTyping(this, on)
        QuickSetting.SuggestionBar -> TypingSettings.suggestionBar(this, on)
        QuickSetting.AutoCapitalise -> languageSetting({ TypingSettings.autoCapitaliseIn(this, it, on) }) { TypingSettings.autoCapitalise(this, on) }
        QuickSetting.AutoPunctuation -> languageSetting({ TypingSettings.autoPunctuationIn(this, it, on) }) { TypingSettings.autoPunctuation(this, on) }
        // Only the master switch: each haptic's own switch stays as it is, for when it is on again.
        QuickSetting.Vibration -> Haptics.enabled(this, on)
        QuickSetting.HoldSymbols -> languageSetting({ TypingSettings.holdSymbolsIn(this, it, on) }) { TypingSettings.holdSymbols(this, on) }
        QuickSetting.CursorControl -> TypingSettings.cursorControl(this, on)
        QuickSetting.DoublePressLocks -> TypingSettings.doublePressLocks(this, on)
        QuickSetting.ScrollAssistant -> TypingSettings.touchpadOnlyInFields(this, !on)
        QuickSetting.OpenSettings, QuickSetting.ChangeKeyboard, QuickSetting.Clipboard -> Unit
    }

    /**
     * A quick setting that each language keeps for itself sets the typing language's own value. While English is the only
     * language on, Settings' main page shows the setting as English's, so its plain value follows too, as the page writes it
     * (TypingSettings.PER_LANGUAGE; app/settings/languages typingPerLanguage). With another language alone on, the plain value
     * stays: it's every language's own until it sets one, so writing it would change English's and every other's too.
     */
    private fun languageSetting(own: (String) -> Unit, plain: () -> Unit) {
        own(languages.languageId)
        if (languages.on.all { it.language.id == InputMethod.ENGLISH_ID }) plain()
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
        replayedCode = false
        inputGeneration++
        loadGate.clear()
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

    /** The queue's stop while an input method loads ([EngineWait]): the keys and touches typed meanwhile wait behind it. */
    private val loadGate = LoadGate { PendingInput(false, null, heldFor = languages.target) }

    /**
     * A key or a touch on the touchpad while the input method is still loading, as a field starts or after a switch: it and
     * whatever follows wait in the queue until it's ready, so the first letter goes into the engine's code (or is the input
     * method's own letter) rather than into the field as English. Shown keyboard or not. Not in exact typing, which types
     * English anyway.
     */
    private fun waitForLoad(what: String) {
        if (field.exact || !::languages.isInitialized || !languages.loadingNow || loadGate.closed(pendingInput) || !makeRoom()) return
        if (loadGate.before(true, pendingInput)) Log.i(LOG_TAG, "$what waits: the input method is still loading")
    }

    /** The load ended, ready or not: what waited goes on, to the engine if it started. */
    private fun loadEnded() {
        // The held keys' letters go with the wait, whichever way it ended: the keys themselves go on to the input method.
        preparingNotice?.let { if (strip?.notice == it) strip?.notice = null }
        preparingNotice = null
        rawLog?.engine(languages.current.id, "loaded")
        val stop = loadGate.open(languages.loadingNow, pendingInput) ?: return
        Log.i(LOG_TAG, "What waited goes to ${languages.current.id}")
        stop.ready = true
        drainInput()
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
        // A same-field restart can carry a plain letter or Space that still waits for its language. Commands and touch actions
        // depend on the old editor state. A burst containing one follows the usual discard path as a whole.
        val plain = event != null && !event.isCtrlPressed && !event.isMetaPressed && !event.isAltPressed &&
            (event.keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z || event.keyCode == KeyEvent.KEYCODE_SPACE)
        val heldFor = when {
            !plain -> null
            loadGate.closed(pendingInput) -> languages.target
            else -> pendingInput.lastOrNull()?.heldFor
        }
        pendingInput.addLast(PendingInput(true, action, event, heldFor = heldFor))
        drainInput()
        if (event != null && loadGate.closed(pendingInput)) showHeldLetters()
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
            val input = pendingInput.removeFirst()
            input.action?.invoke()
            if (input.heldFor != null && input.heldFor == languages.current.id && input.event?.action == KeyEvent.ACTION_DOWN &&
                (picker as? LanguagePicker.Choices)?.state?.composing == true) {
                replayedCode = true
            }
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
                if (!altOrPanel && !outcome.deletesWord && pageChoices(outcome.points)) return
                // The switcher, the accents or the bigger menu is open: a swipe there types and deletes nothing.
                if (Overlays.stopsSwipes(openOverlay)) return
                if (altOrPanel) {
                    if (panelOpen) swipePanel(outcome.points)
                    return
                }
                // A page gesture can settle after its overlay closes. It still belongs to that overlay.
                if (outcome.forPaging || !field.words) return
                val allowed = if (outcome.deletesWord) contact.deleteFlicks else contact.wordSwipes
                if (!allowed) return
                // A guide slide that doesn't teach this yet: the swipe or the flick does nothing, and the slide says so, once.
                if (refuse(if (outcome.deletesWord) GuideLesson.Flick else GuideLesson.Swipe)) return
                if (outcome.deletesWord) {
                    // A code being built (Chinese, Japanese, Korean's syllable, Telex's word) goes, and the field stays as it is
                    // (owner: "a flick should also delete the entire key code too"); in Rime only what's pending goes, then a picked
                    // word a flick. Once nothing is built, a flick deletes from the field.
                    // Next-word suggestions, with nothing typed, just go, and the flick deletes as usual.
                    val choices = openPicker() as? LanguagePicker.Choices
                    if (choices != null) {
                        // A code with part of it picked loses only what's pending; each flick after takes a picked word.
                        val engine = languages.engine
                        if (choices.state.composing && engine != null) engineState(engine.flick()) else {
                            engine?.reset()
                            closePicker()
                        }
                        if (choices.state.composing) {
                            haptics.play(Haptic.DeleteFlick)
                            KeyboardEvents.publish { KeyboardEvent.DeletedWord(held = false) }
                            return
                        }
                    }
                    val before = currentInputConnection?.getTextBeforeCursor(FLICK_REACH, 0)
                    // A selection is something to delete, and goes whole (bug hunt 2.1, #7).
                    val selected = selectionToDelete()
                    val nothingLeft = GestureDisplay.nothingToDelete(before, selected)
                    // Nothing to delete: nothing happened, so a no, once for the stroke. A hold with nothing to delete never starts,
                    // and plays just this; its steps after stay quiet, as the stroke has had its say (see [deleteWall]).
                    if (nothingLeft && deleteWall.reached()) haptics.play(Haptic.deleteFoundNothing(tookAny = false))
                    // A hold's first word: the bar's words are the real ones before it goes.
                    strip?.gesture?.deleteText(before, selected, wordBreaker())
                    val deletes = editor.deletes
                    composer.backspace(true)
                    if (!nothingLeft) {
                        // A dot for each character of the word that went, not counting spaces. The haptic's tail lands as they pop.
                        val gesture = strip?.gesture
                        gesture?.speedUp = Haptics.dotsSpeedUp(this)
                        // How long the dots wait on the bar, which the Haptics lab can try live in TitanKeys Debug.
                        GestureDisplay.triedFlickHold = Haptics.readFlickHold(this)?.toFloat()
                        gesture?.wordDeleted(SystemClock.uptimeMillis(), deleteDots(deletes, before, selected))
                        gesture?.let { deleteLog("delete animation: dots ${it.dots}") }
                        val scale = if (GuideMotion.enabled) ValueAnimator.getDurationScale() else 0f
                        // The dots tick as they land in the caret, only while this flick's dots are still on the bar.
                        val dotsAt = gesture?.dotsAt
                        // A flick while dots still play adds its dots to theirs, and the whole queue's ticks go again with its wait.
                        haptics.flick(gesture?.dotsPoppedMillis(scale), gesture?.dotLandings(scale).orEmpty(),
                            { dotsAt != null && strip?.gesture?.dotsAt == dotsAt })
                    }
                    KeyboardEvents.publish { KeyboardEvent.DeletedWord(held = false) }
                    tally("gesture.flickDelete")
                    return
                }
                // An engine that failed to load takes no swipe: the bar says why, instead of the swipe vanishing without a word.
                showSwipeUnavailable()
                composer.swipe(letterSpace(outcome.points).also { TouchSizes.attach(it, outcome.points) }, outcome.startedAt,
                    outcome.endedAt, modifiers.metaState, endContact(outcome.points))
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
                val engine = languages.engine
                // A Space touch never picks: only a press does, so the taps of a steer or a select leave the code on the bar (owner,
                // 2026-11-17). A later pick goes in at the cursor, over a selection if there is one.
                if (key == " " && engine != null && (openPicker() as? LanguagePicker.Choices)?.state?.composing == true) return
                if (engine != null) {
                    // A tap goes to the engine as a key would: Space picks, a letter builds the code. Chinese has no capitals.
                    val shifted = modifiers.shift != Modifiers.Level.Off && !ChineseKeys.usesBar(languages.current)
                    val state = if (key == " ") engine.space() else engine.key(languages.current.letter(key[0], shifted)?.first() ?: if (shifted) key[0] else key[0].lowercaseChar())
                    if (state.handled) {
                        engineState(state)
                        modifiers.consume()
                        showModifiers()
                        return
                    }
                    // The engine wrote what it had (Korean's syllable) and the key goes on as usual.
                    if (state.commit.isNotEmpty()) engineState(state)
                }
                if (key == " ") {
                    composer.space(repeated = false, time = outcome.endedAt)
                } else {
                    val shifted = modifiers.shift != Modifiers.Level.Off
                    // An input method with its own letters types its letter (Russian's й on Q), in its own capitals.
                    val word = languages.current.letter(key[0], shifted)
                        ?: if ((key == "I" && TypingSettings.autoCapitaliseIn(this, languages.languageId) && languages.current.isEnglish) || shifted) key else key.lowercase()
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
        if (languages.current.hasAccents) return canHoldAccent(event)
        if (heldLetterOf(event, false) != null) return canHoldThird(event)
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

    /** What holding [event]'s key types on the current input method's third layer (Thai), or null. */
    private fun heldLetterOf(event: KeyEvent, shift: Boolean): String? =
        if (event.keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) languages.current.held(printedLetter(event.keyCode), shift) else null

    /**
     * A key with a letter on the third layer (Thai's Kedmanee keys the Titan doesn't have): holding it types that letter, with
     * Shift too, whatever hold for symbols is set to, as accents do. Never in exact typing, or with Alt, Ctrl or a panel on.
     */
    private fun canHoldThird(event: KeyEvent): Boolean {
        if (!editing || field.exact || deadKeyPending || !guideAllows(GuideLesson.HoldSymbol)) return false
        if (!HoldEngine.arrivedInTime(event.downTime, SystemClock.uptimeMillis())) return false
        if (altOrPanel || event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) return false
        if (pendingInput.any { it.event?.keyCode?.let(Modifiers::isModifier) == true }) return false
        val device = event.device ?: return false
        return !device.isVirtual && !device.isExternal && event.isFromSource(InputDevice.SOURCE_KEYBOARD)
    }

    /**
     * In an input method with accents, holding a letter shows its accents on the bar, and hold for symbols is off on every key,
     * so a hold always means one thing (docs/languages.md). Shift is fine: the accents show in capitals. Never in exact typing,
     * which sends keys, or with Alt or a panel on.
     */
    private fun canHoldAccent(event: KeyEvent): Boolean {
        if (!editing || field.exact || !HoldEngine.isLetter(event.keyCode) || deadKeyPending || !guideAllows(GuideLesson.HoldSymbol)) return false
        if (!HoldEngine.arrivedInTime(event.downTime, SystemClock.uptimeMillis())) return false
        if (altOrPanel || event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) return false
        if (pendingInput.any { it.event?.keyCode?.let(Modifiers::isModifier) == true }) return false
        val device = event.device ?: return false
        if (device.isVirtual || device.isExternal || !event.isFromSource(InputDevice.SOURCE_KEYBOARD)) return false
        return languages.current.accentsOf(printedLetter(event.keyCode), false).isNotEmpty()
    }

    /**
     * In an input method with accents, a held letter that has none types once, and its repeats do nothing: no letters, symbol,
     * picker or haptic (owner, 2026-11-17), as a held letter does in Chinese. Not in exact typing, which sends keys, nor with
     * Alt, Ctrl or a panel on.
     */
    private fun accentlessRepeat(event: KeyEvent): Boolean =
        event.repeatCount > 0 && languages.current.hasAccents && editing && !field.exact && HoldEngine.isLetter(event.keyCode) &&
            !altOrPanel && !event.isAltPressed && !event.isCtrlPressed && !event.isMetaPressed &&
            languages.current.accentsOf(printedLetter(event.keyCode), false).isEmpty()

    /** The letter printed on a letter key, in lower case. */
    private fun printedLetter(keyCode: Int) = 'a' + (keyCode - KeyEvent.KEYCODE_A)

    /** Whether a key types a capital: Shift (held, tapped or automatic) or Caps, but not both. */
    private fun shiftOn(event: KeyEvent): Boolean {
        val meta = event.metaState or modifiers.metaState
        return (meta and KeyEvent.META_SHIFT_MASK != 0) != (meta and KeyEvent.META_CAPS_LOCK_ON != 0)
    }

    /** What a letter key types in an input method with its own letters (Russian's й on Q), or null where it types what it shows. */
    private fun inputMethodLetter(event: KeyEvent): String? {
        if (!keysMapKey(event)) return null
        return languages.current.letter(printedLetter(event.keyCode), shiftOn(event))
    }

    /** A letter key the current input method's own letters type, so the app never gets it: see [KeysMapKeys]. */
    private fun keysMapKey(event: KeyEvent): Boolean {
        val meta = event.metaState or modifiers.metaState
        return KeysMapKeys.keeps(languages.current.keys != null, event.keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z,
            meta and KeyEvent.META_ALT_MASK != 0, meta and KeyEvent.META_CTRL_MASK != 0, meta and KeyEvent.META_META_MASK != 0)
    }

    /** Starts the hold timer for a letter that can become its symbol. True if this is a repeat of such a key, which types nothing. */
    private fun trackHold(event: KeyEvent): Boolean {
        val earlier = holdEngine.pending()
        val consumed = holdEngine.down(event.keyCode, event.deviceId, event.downTime, event.repeatCount > 0, canHold(event))
        val press = holdEngine.pending()
        if (press != null && press !== earlier) {
            heldLetter = KeyEvent(event)
            heldShift = shiftOn(event)
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
        // In an input method with accents, the hold shows the letter's accents on the bar instead (docs/languages.md).
        if (languages.current.hasAccents) {
            showAccents(letter.keyCode)
            return
        }
        // Thai: the key's letter on the third layer, with Shift its other one.
        val text = heldLetterOf(letter, heldShift) ?: letter.getUnicodeChar(letter.metaState or KeyEvent.META_ALT_ON).let { symbol ->
            if (symbol <= 0 || !Character.isValidCodePoint(symbol) || Character.isISOControl(symbol)) return
            String(Character.toChars(symbol))
        }
        haptics.play(Haptic.HeldSymbol)
        KeyboardEvents.publish { KeyboardEvent.HeldSymbol(text) }
        // Any engine's held letter is in the code it's building, not the field (Korean's syllable, Telex's and Hindi's word): it
        // comes off there, what was built is written, then the symbol. Replacing the field's last letter would take an earlier one.
        val engine = languages.engine
        if (!field.exact && engine != null) {
            if (pendingInput.isNotEmpty()) enqueue { heldSymbolThroughEngine(engine, text) } else heldSymbolThroughEngine(engine, text)
            return
        }
        if (field.exact) {
            // The letter went to the app as a key, so a Backspace key takes it back.
            logExact("hold ${letter.keyCode} gives its symbol")
            sendExact(letter, ExactTyping.takeBack)
            commitExact(text)
        } else if (pendingInput.isNotEmpty()) enqueue { composer.replaceLetter(text) } else composer.replaceLetter(text)
    }

    /**
     * A held letter in Chinese or Japanese: the press typed its letter into the code, so it goes again, and the symbol goes
     * through the engine, as Alt's does: a mark as the language writes it (，。), the code's first choice before anything else,
     * a digit plain (owner, 2026-11-17: hold for symbols as in every language).
     */
    private fun heldSymbolThroughEngine(engine: InputEngine, text: String) {
        engineState(engine.backspace())
        val state = if (text.length == 1) engine.key(text[0]) else engine.space()
        if (state.handled || state.commit.isNotEmpty()) engineState(state)
        if (!(text.length == 1 && state.handled)) composer.symbol(text)
    }

    // Input methods (docs/languages.md): the picker on the bar, accents on hold, and the switch overlay on a held Sym.

    /** The picker, if it's the bar's notice now. Anything else that took the bar, as a word's menu, closed it. */
    private fun openPicker(): LanguagePicker? {
        val open = picker ?: return null
        // Equal, not only the same object: the strip keeps the notice it has when an equal one comes, so a picker shown again as it
        // was (an engine state that didn't change) is still the one on the bar, and Enter still types its code.
        if (strip?.notice == open.notice) return open
        picker = null
        setPaging()
        return null
    }

    private fun showPicker(open: LanguagePicker) {
        if (open !is LanguagePicker.Choices || !open.state.composing || open.state.commit.isNotEmpty()) replayedCode = false
        closeMenu()
        picker = open
        strip?.pickerMark = -1
        strip?.notice = open.notice
        updateKeyWatch()
        setPaging()
    }

    /** Swipes on the keys go to the panel or the bar's overlay while one is open, a page turn's length or more. */
    private fun setPaging() {
        val open = openOverlay
        contact.paging = when {
            !panelOpen && open == Overlays.Open.Choices -> Contact.Paging.Choices
            Overlays.paging(open, panelOpen) -> Contact.Paging.PagesOnly
            else -> Contact.Paging.None
        }
        contact.pageLog = if (picker is LanguagePicker.Choices && AppBuild.kind(this) == "debug") { line -> choicesLog(line) } else null
    }

    private fun closePicker() {
        replayedCode = false
        closeChoicesMenu()
        val open = picker ?: return
        picker = null
        strip?.pickerMark = -1
        if (strip?.notice == open.notice) strip?.notice = null
        setPaging()
        updateKeyWatch()
    }

    /** An engine's choices are on the bar: swipes on the keys are theirs, short ones too, as on the Sym panel. */
    private val choicesOpen get() = picker is LanguagePicker.Choices

    /** Which picking overlay is open, for the touchpad ([Overlays]). */
    private val openOverlay: Overlays.Open get() = when (val open = picker) {
        is LanguagePicker.Switch -> if (open.opened != null) Overlays.Open.SwitcherRow else Overlays.Open.Switcher
        is LanguagePicker.Accents -> Overlays.Open.Accents
        is LanguagePicker.Choices -> if (choicesMenuOpen) Overlays.Open.ChoicesMenu else Overlays.Open.Choices
        null -> Overlays.Open.None
    }

    /**
     * A swipe on the keys with an engine's choices on the bar, a code's or suggestions', in any input method that has them:
     * Chinese, Japanese, Vietnamese and Hindi (docs/languages.md, "Chinese on the bar"). A swipe up opens the bigger menu, as
     * Shift and the more cue do, and with the menu open, up and down turn its pages
     * ([ChineseKeys.swipe]). The row itself never pages, and nothing on it is picked by a swipe (owner, 2026-11-17: "Abandon
     * the flick to select concept"). True if it was taken.
     */
    private fun pageChoices(points: List<Sample>): Boolean {
        val open = openPicker() as? LanguagePicker.Choices ?: return false
        val down = (points.last().y - points.first().y) * 4
        val across = (points.last().x - points.first().x) * 10
        val swipe = ChineseKeys.swipe(across, down, choicesMenuOpen, choicesMenu?.onFirstPage == true)
        choicesLog("swipe: %.2f keys across, %.2f rows down, %d ms: %s%s".format(across, down,
            points.last().time - points.first().time, swipe, if (open.items.isEmpty()) ", but no choices to show" else ""))
        when (swipe) {
            // Korean's syllable has no choices: nothing to open.
            ChineseKeys.Swipe.OpenMenu -> if (open.more) openChoicesMenu()
            ChineseKeys.Swipe.NextPage -> if (choicesMenu?.next() == true) haptics.play(Haptic.PanelPage)
            ChineseKeys.Swipe.PageBefore -> if (choicesMenu?.previous() == true) haptics.play(Haptic.PanelPage)
            ChineseKeys.Swipe.CloseMenu -> closeChoicesMenu()
            ChineseKeys.Swipe.Nothing -> {}
        }
        return true
    }

    /** A held letter's accents, on the bar over the keys around it. The letter it typed stays until one is picked. */
    private fun showAccents(keyCode: Int) {
        val printed = printedLetter(keyCode)
        val accents = languages.current.accentsOf(printed, heldShift)
        if (accents.isEmpty()) return
        haptics.play(Haptic.HeldSymbol)
        showPicker(LanguagePicker.Accents(accents, printed.uppercase()))
    }

    /**
     * A key while the picker is open, or a Sym that may become a hold. True when the key is taken: it picked, or closed the
     * picker, or is a Sym held back to see if it's held.
     * - A letter in the top two rows under a choice picks it.
     * - Backspace closes the picker. Any other key closes the accents and types as usual; the overlay takes it and closes.
     * - Sym, with two or more input methods on, waits: let go before the hold time it's Sym as always, held it opens the
     *   overlay. Sym with the overlay open closes it.
     */
    private fun languageKey(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (event.isSystem) return false
        if (keyCode == pickerKey && event.repeatCount > 0) return true
        val open = openPicker()
        // A code being built, with no switcher to wait for (one input method on): Sym clears it at once, and the choices with
        // it, and types nothing. The next Sym opens the panel (owner, 2026-11-17: "pressing the sym button should remove the
        // keycode stuff first"). With a switcher, Sym waits as always: a tap clears the code ([symReleased]), a hold switches.
        if (keyCode == KeyEvent.KEYCODE_SYM && event.repeatCount == 0 && codePending() && symWhyNot() != null) {
            symPress.cleared()
            clearCode()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_SYM) {
            // Sym is never the picker's key: a held Sym's repeats and its release belong to [symPress] (the next Sym stuck).
            val press = symPress.press(event.repeatCount, open is LanguagePicker.Switch) {
                val whyNot = symWhyNot()
                if (whyNot != null) symLog("Sym won't wait for a hold: $whyNot")
                whyNot == null
            }
            when (press) {
                SymPress.Press.Close -> closePicker()
                SymPress.Press.Swallow -> {}
                SymPress.Press.Usual -> return false
                SymPress.Press.Wait -> {
                    symDown = KeyEvent(event)
                    val millis = TypingSettings.holdSymbolMillis(this).toLong()
                    symLog("Sym waits ${millis} ms for a hold, ${languages.on.size} input methods on")
                    main.postDelayed(symHeld, millis)
                }
            }
            return true
        }
        if (open == null || event.repeatCount > 0 || Modifiers.isModifier(keyCode)) return false
        // An engine's choices stay while its text is built: its keys go to the engine, in [engineKey].
        if (open is LanguagePicker.Choices) return false
        val letter = if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) ('A' + (keyCode - KeyEvent.KEYCODE_A)).toString() else null
        val choice = when (open) {
            is LanguagePicker.Accents -> letter?.let(open::choice)
            is LanguagePicker.Switch -> letter?.let(open::choice)
            is LanguagePicker.Choices -> null
        } ?: -1
        if (choice >= 0) {
            pickerKey = keyCode
            pick(open, choice)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DEL || open is LanguagePicker.Switch) {
            closePicker()
            pickerKey = keyCode
            return true
        }
        closePicker()
        return false
    }

    private fun pick(open: LanguagePicker, choice: Int) {
        closePicker()
        haptics.play(Haptic.MenuPress)
        when (open) {
            is LanguagePicker.Accents -> {
                val accent = open.accents[choice]
                if (pendingInput.isNotEmpty()) enqueue { composer.replaceLetter(accent) } else composer.replaceLetter(accent)
            }
            is LanguagePicker.Choices -> pickChoice(open, choice)
            is LanguagePicker.Switch -> {
                val entry = when (val next = SwitchOverlay.pick(open.languages, open.opened, choice)) {
                    // A language with several input methods on: its own row, the one typing now marked.
                    is SwitchPick.Row -> {
                        showPicker(LanguagePicker.Switch(open.languages, next.language, languages.current.id))
                        return
                    }
                    is SwitchPick.To -> languages.on.firstOrNull { it.id == next.id } ?: return
                    null -> return
                }
                switchLanguage(entry)
            }
        }
    }

    /**
     * A key for the current input method's engine (Rime): letters, punctuation, Space, Enter and Backspace go to it first, and
     * only what it commits reaches the field. Space picks the choice a resting finger marks, or else the first. A key the
     * engine doesn't take, as Backspace with nothing being built, goes on as usual. Shortcuts with Ctrl, Alt or Meta never go
     * to it.
     */
    private fun engineKey(engine: InputEngine, event: KeyEvent): Boolean {
        val meta = event.metaState or modifiers.metaState
        if (meta and (KeyEvent.META_CTRL_MASK or KeyEvent.META_META_MASK) != 0) return false
        val chinese = ChineseKeys.usesBar(languages.current)
        // Alt's symbols are the keyboard's, but in Chinese Rime turns their punctuation into Chinese marks, and a code typed
        // goes first (docs/languages.md, "Chinese on the bar").
        val alt = meta and KeyEvent.META_ALT_MASK != 0
        if (alt && !chinese) return false
        val letter = event.keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z
        // A held letter types nothing more into the code: its hold gives its symbol (replaceHeldLetter).
        if (chinese && letter && !alt && event.repeatCount > 0) return true
        val state = when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> if (alt) return false else engine.backspace()
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (alt) return false else engine.enter()
            // Space picks the first choice: a resting finger doesn't change that (owner: "no marking first").
            KeyEvent.KEYCODE_SPACE -> if (alt) return false else engine.space().also {
                if (it.commit.isNotEmpty() && openPicker() is LanguagePicker.Choices) tallyChoices("pick.space")
            }
            // Chinese and Japanese have no capitals: a letter is the letter, Shift or not.
            else -> if (chinese && letter && !alt) engine.key(printedLetter(event.keyCode)) else {
                val text = (if (alt) null else inputMethodLetter(event)) ?: event.getUnicodeChar(meta).takeIf { it > 0 &&
                    Character.isValidCodePoint(it) && !Character.isISOControl(it) && it and KeyCharacterMap.COMBINING_ACCENT == 0 }
                    ?.let { String(Character.toChars(it)) }
                if (text == null || text.length != 1) return false
                engine.key(text[0])
            }
        }
        if (!state.handled) {
            // What the engine had goes first (Korean's syllable, Telex's word, Rime's code before a capital), then the key as usual.
            // Rime's next-word suggestions go with a key that's the keyboard's own (Backspace, Enter, a digit), and the bar with them.
            if (state.commit.isNotEmpty() || !state.composing && openPicker() is LanguagePicker.Choices) engineState(state)
            return false
        }
        if (event.keyCode == KeyEvent.KEYCODE_SPACE) modifiers.useHeld() else modifiers.consume()
        engineState(state)
        return true
    }

    /**
     * Writes what the engine committed, and shows its choices on the bar while text is being built: as many as the row fits,
     * read across the engine's pages, the letters as an English word when [EnglishOffer] says so, and the input method's mark
     * over Q (docs/languages.md, "Chinese on the bar").
     */
    private fun engineState(state: EngineState) {
        if (state.commit.isNotEmpty()) {
            val text = state.commit
            rawLog?.committed(languages.current.engine, text)
            if (pendingInput.isNotEmpty()) enqueue { composer.paste(text) } else composer.paste(text)
            // What was picked stays one word for every gesture and the bar ([PickedWords]).
            pickedWords.picked(text)
        }
        if (state.composing || state.choices.isNotEmpty()) {
            val listed = languages.engine?.choices(0, ChineseRow.ASK + 1).orEmpty().ifEmpty { state.choices }
            showPicker(LanguagePicker.Choices(state, listed, englishFor(state, listed), markLabel()))
        } else closePicker()
    }

    /**
     * Chinese and Japanese: the bar's words go to the engine's wide row, as many as fit, and a pick types no space and brings
     * the engine's next words ([InputEngine.offer]). Never over a code or the engine's own suggestions.
     */
    private fun offerBarWords(words: List<String>) {
        if (!ChineseKeys.usesBar(languages.current) || field.exact) return
        val engine = languages.engine ?: return
        val open = openPicker()
        if (open != null && (open !is LanguagePicker.Choices || open.state.composing)) return
        val state = engine.offer(words)
        if (state.handled) engineState(state)
    }

    /** The letters typed, as an English word to offer second, or null: see [EnglishOffer]. Chinese only. */
    private fun englishFor(state: EngineState, listed: List<String>): String? {
        if (!ChineseKeys.isChinese(languages.current) || listed == listOf(com.titankeys.rime.RimeEngine.NO_VARIANT)) return null
        return state.typed.takeIf { typed ->
            EnglishOffer.offers(typed, state.code, state.partial, listed.size) { languages.words.englishKnown(it) }
        }
    }

    /** Picks row item [item]: the English word types as typed, and the code goes; anything else is the engine's. */
    private fun pickChoice(open: LanguagePicker.Choices, item: Int) {
        val engine = languages.engine ?: return
        if (open.isEnglish(item)) {
            engine.reset()
            engineState(EngineState(commit = open.english.orEmpty()))
            return
        }
        engineState(engine.pickAt(open.engineIndex(item)))
    }

    /**
     * The input method's mark over Q (耶, 粵, あ, ES) whenever one that isn't English types, the idle bar included (owner,
     * 2026-11-17: "The Yale Chinese indicator is not showing in the suggestion").
     */
    private fun showInputMark() {
        strip?.inputMark = if (languages.current.isEnglish) "" else markLabel()
    }

    /** The mark at Q: the language's where it has one input method on, else the input method's ([SwitchOverlay.markLabel]). */
    private fun markLabel() = SwitchOverlay.markLabel(languages.on, languages.current.id, languages.current.label)

    /** Chinese's and Japanese's bigger menu, that Shift opens, in the Sym panel's place: see [ChoicesMenu]. */
    private var choicesMenu: ChoicesMenu? = null
    private val choicesMenuOpen get() = choicesMenu?.visibility == View.VISIBLE

    /** Shift, a swipe up or a tap on the more cue, with choices on the bar: the bigger menu opens, on its first page. */
    private fun openChoicesMenu() {
        val open = openPicker() as? LanguagePicker.Choices ?: return
        if (!open.more) return
        val engine = languages.engine ?: return
        val menu = choicesMenu ?: return
        haptics.play(Haptic.PanelPage)
        menu.open(open.state.code) { start, count -> engine.choices(start, count).ifEmpty { if (start == 0) open.state.choices else emptyList() } }
        menu.hover = ""
        menu.visibility = View.VISIBLE
        showModifiers()
        updateKeyWatch()
        tallyChoices("bigMenu")
    }

    private fun closeChoicesMenu() {
        val menu = choicesMenu ?: return
        if (menu.visibility != View.VISIBLE) return
        menu.visibility = View.GONE
        menu.hover = ""
        showModifiers()
        updateKeyWatch()
    }

    /** A pick from the bigger menu: its place in the engine's whole list. The menu closes. */
    private fun pickFromMenu(index: Int) {
        if (!choicesMenuOpen) return
        closeChoicesMenu()
        haptics.play(Haptic.MenuPress)
        tallyChoices("pick.menu")
        languages.engine?.let { engineState(it.pickAt(index)) }
    }

    /**
     * A key with the bigger menu open: the letter under a choice picks it, Enter and Backspace turn the pages, Sym is the ✕,
     * Space picks the row's first choice. Every other key does nothing while it's open. Its release is the menu's too.
     */
    private fun choicesMenuKey(event: KeyEvent): Boolean {
        val menu = choicesMenu ?: return false
        val keyCode = event.keyCode
        pickerKey = keyCode
        if (event.repeatCount > 0) return true
        choicesLog("menu key $keyCode (scan ${event.scanCode}, flags ${event.flags}): " + when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "next page"
            KeyEvent.KEYCODE_DEL -> "page before"
            KeyEvent.KEYCODE_SYM -> "close"
            else -> "pick or nothing"
        })
        when (keyCode) {
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                val index = menu.choice(('A' + (keyCode - KeyEvent.KEYCODE_A)).toString())
                if (index >= 0) pickFromMenu(index)
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (menu.next()) haptics.play(Haptic.PanelPage)
            KeyEvent.KEYCODE_DEL -> if (menu.previous()) haptics.play(Haptic.PanelPage)
            KeyEvent.KEYCODE_SYM -> closeChoicesMenu()
            KeyEvent.KEYCODE_SPACE -> {
                closeChoicesMenu()
                tallyChoices("pick.space")
                languages.engine?.let { engineState(it.space()) }
            }
        }
        return true
    }

    /** The Shift whose press opened or closed the bigger menu: its repeats and its release are the menu's too. */
    private var menuShift = -1

    /**
     * Chinese's and Japanese's own keys, before anything else (docs/languages.md, "Chinese on the bar"). Either Shift opens the
     * bigger menu while choices are on the bar, a code's or suggestions', and closes it (owner, 2026-11-17: "use the original
     * shift as the plan instead"); with the menu open, every key is the menu's. Sym is always the Sym panel. True if the key is
     * taken.
     */
    private fun chineseKey(event: KeyEvent): Boolean {
        if (event.isSystem || inGuide) return false
        // The bigger menu, open in any input method a swipe up opened it in (Vietnamese, Hindi), takes every key.
        if (!ChineseKeys.usesBar(languages.current) && !choicesMenuOpen) return false
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (event.repeatCount > 0) return menuShift == keyCode
            val overflow = !choicesMenuOpen && (openPicker() as? LanguagePicker.Choices)?.more == true
            if (!choicesMenuOpen) choicesLog("Shift row overflow=$overflow")
            when {
                choicesMenuOpen -> closeChoicesMenu()
                // Shift opens it only in Chinese and Japanese: elsewhere it types capitals, or Hindi's own sounds.
                overflow && ChineseKeys.usesBar(languages.current) -> openChoicesMenu()
                else -> return false
            }
            choicesLog("Shift ($keyCode): the bigger menu ${if (choicesMenuOpen) "opens" else "closes"}")
            menuShift = keyCode
            return true
        }
        if (choicesMenuOpen) return choicesMenuKey(event)
        return false
    }

    /** A key let go: the Shift that opened or closed the menu keeps its release, so Shift itself never comes on. */
    private fun chineseKeyUp(keyCode: Int): Boolean {
        if (keyCode != menuShift) return false
        menuShift = -1
        return true
    }

    /**
     * Sharing's daily totals (#96): one more [key], only in a field that allows learning, never in the guide's own fields, and
     * only while Sharing is on ([Counts.add] checks).
     */
    private fun tally(key: String) {
        if (editing && field.learning && !inGuide) Counts.add(key)
    }

    /** A count for Chinese's or Japanese's choices, under "zh." or "ja.". Other input methods' choices aren't counted. */
    private fun tallyChoices(key: String) {
        val language = when (languages.current.engine) {
            ChineseKeys.ENGINE -> "zh"
            "mozc" -> "ja"
            else -> return
        }
        tally("$language.$key")
    }

    /** The input method the log last named, so an `im` line says where it switched from. */
    private var loggedInputMethod: String? = null

    /** TitanKeys Debug's owner mark: see [OwnerMark]. */
    private val ownerMark = OwnerMark()
    private var markShift = Modifiers.Level.Off
    private var markAlt = Modifiers.Level.Off
    private var markMenu = false
    private var markAutoShifted = false
    /** The Shift whose release the mark keeps, or -1. */
    private var ownerMarkKey = -1

    /**
     * In TitanKeys Debug, while the log records this field: three quick Shift presses write a MARK with the time into the log and
     * play a short tick. Shift, Alt and the bigger menu go back to how they were before the first press. True if this press was
     * the mark's third, which the keyboard then keeps.
     */
    private fun ownerMarked(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        // In any field, recorded or not (Maps' search, Chrome's address bar): the mark needs only the log to be on.
        if (!editing || !RawLog.on(this) || event.repeatCount > 0) return false
        if (keyCode != KeyEvent.KEYCODE_SHIFT_LEFT && keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT) {
            ownerMark.other()
            return false
        }
        if (!ownerMark.shift(event.eventTime)) {
            if (ownerMark.first) {
                markShift = modifiers.shift
                markAlt = modifiers.alt
                markAutoShifted = modifiers.autoShifted
                markMenu = choicesMenuOpen
            }
            return false
        }
        modifiers.restore(markShift, markAlt, markAutoShifted)
        if (choicesMenuOpen && !markMenu) closeChoicesMenu() else if (!choicesMenuOpen && markMenu) openChoicesMenu()
        showModifiers()
        ownerMarkKey = keyCode
        val info = currentInputEditorInfo
        RawLog.ownerMark(this, java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.ROOT).format(java.util.Date()),
            info?.packageName, info?.inputType ?: 0)
        haptics.play(Haptic.PanelPage)
        Log.i(LOG_TAG, "Owner MARK written to the log")
        return true
    }

    /** The bar's choices and the bigger menu, in TitanKeys Debug's log: `adb logcat -s TitanKeysChoices`. */
    private fun choicesLog(line: String) {
        if (AppBuild.kind(this) == "debug") Log.i(CHOICES_TAG, line)
    }

    /** Sym held for the hold time: the overlay, with every input method that's on over W to O. */
    private fun openSwitchOverlay() {
        if (!editing) {
            symLog("Sym held, but no overlay: not editing")
            return
        }
        if (!symPress.held()) {
            symLog("Sym held, but no overlay: already let go")
            return
        }
        // A hold opens the switcher from anywhere (Master, 2026-11-17): a code pending is cleared on the way, the panel closed.
        if (codePending()) clearCode()
        if (panelOpen) closePanel()
        haptics.play(Haptic.HeldSymbol)
        val on = SwitchOverlay.shown(InputMethodIndex.group(languages.on))
        // Two languages: the other one at once, no switcher.
        SwitchOverlay.toggle(on, languages.current.id, languages.lastUsed)?.let { id ->
            symLog("Sym held: two languages, so $id types now")
            languages.on.firstOrNull { it.id == id }?.let(::switchLanguage)
            return
        }
        symLog("Sym held: the input method overlay opens")
        showPicker(LanguagePicker.Switch(on, SwitchOverlay.opening(on), languages.current.id))
    }

    /**
     * A tap on the language mark: the language's other input method, or its row in the switcher with three or more
     * ([SwitchOverlay.markTap]). One input method: nothing, no haptic.
     */
    private fun tapMark() {
        if (!editing || !::languages.isInitialized) return
        val on = SwitchOverlay.shown(InputMethodIndex.group(languages.on))
        when (val pick = SwitchOverlay.markTap(on, languages.current.id)) {
            is SwitchPick.To -> {
                haptics.play(Haptic.MenuPress)
                languages.on.firstOrNull { it.id == pick.id }?.let(::switchLanguage)
            }
            is SwitchPick.Row -> {
                haptics.play(Haptic.MenuPress)
                showPicker(LanguagePicker.Switch(on, pick.language, languages.current.id))
            }
            null -> {}
        }
    }

    /**
     * Switches to input method [entry]. The language mark on the bar shows which types now; the bar says nothing more (owner,
     * 2026-11-16: "I don't like the XX it is wording"). A screen reader hears it: "Typing in Spanish". F: draft.
     */
    private fun switchLanguage(entry: InputMethodIndex.Entry) {
        if (entry.id == languages.current.id) return
        languages.switchTo(entry.id)
        strip?.announceForAccessibility("Typing in ${entry.nameEnglish}")
    }

    /**
     * Sym let go. True if it was held back: held long enough, it opened the overlay and its release does nothing more; let go
     * sooner, its press now goes on as Sym always does, and then its release.
     */
    /** Why a Sym press won't wait for a hold to open the switcher, or null when it will ([SymHold]). */
    private fun symWhyNot() = SymHold.whyNot(languages.on.size, languages.englishOnly, panelOpen, modifiers.alt != Modifiers.Level.Off,
        inGuide, field.exact, pendingInput.size)

    /** An engine's code is being built on the bar. */
    private fun codePending() = (openPicker() as? LanguagePicker.Choices)?.state?.composing == true

    /** Clears the engine's code and its choices, typing nothing. */
    private fun clearCode() {
        closeChoicesMenu()
        languages.engine?.let { engineState(it.reset()) } ?: closePicker()
    }

    private fun symReleased(): Boolean {
        val release = symPress.release()
        if (release == SymPress.Release.NotMine) return false
        val down = symDown
        symDown = null
        main.removeCallbacks(symHeld)
        symLog("Sym let go${down?.let { " after ${SystemClock.uptimeMillis() - it.downTime} ms" }.orEmpty()}: " +
            if (release == SymPress.Release.Kept) "the switcher's" else "Sym as usual")
        if (release == SymPress.Release.Kept || down == null) return true
        // A tap steps back: with a code pending it clears the code and opens nothing (owner, 2026-11-17).
        if (SymHold.tap(codePending()) == SymHold.Tap.ClearCode) {
            clearCode()
            return true
        }
        // Taken as a key that the keyboard handled, so its release stays here too, as any Sym's does.
        consumedKeys += down.keyCode
        if (pendingInput.isNotEmpty()) enqueue(KeyEvent(down)) {
            key(down)
            showModifiers()
        } else {
            key(down)
            showModifiers()
        }
        return false
    }

    /** Another input method types now: autocorrect, swiping and the bar follow it, and Android's keyboard list says it (#76). */
    /** The line on the bar while an input method gets ready, until [inputMethodChanged] takes it away. */
    private var preparingNotice: Strip.Notice.Line? = null

    /**
     * An input method still getting ready (Rime copying its data the first time): the bar never says so (owner, 2026-11-17:
     * "there shd be no warm up visible to the user"). The keys typed meanwhile wait ([EngineWait]), and the bar shows their
     * letters, plain, until the engine takes them. Never over a word's menu.
     */
    private fun showPreparing(entry: InputMethodIndex.Entry) {
        rawLog?.engine(entry.id, "preparing")
        showHeldLetters()
    }

    /** The letters waiting for an input method that is still loading, plain, as the code they will become. */
    private fun showHeldLetters() {
        val strip = strip ?: return
        if (!::languages.isInitialized || !languages.loadingNow) return
        if (strip.notice != null && strip.notice !is Strip.Notice.Line) return
        val letters = HeldLetters.of(pendingInput.mapNotNull { it.event }.map { Triple(it.action == KeyEvent.ACTION_DOWN, it.repeatCount, it.keyCode) })
        if (letters.isEmpty()) {
            preparingNotice?.let { if (strip.notice == it) strip.notice = null }
            preparingNotice = null
            return
        }
        val notice = Strip.Notice.Line(letters)
        preparingNotice = notice
        strip.notice = notice
    }

    private fun inputMethodChanged(method: InputMethod) {
        rawLog?.inputMethod(loggedInputMethod, method.id)
        loggedInputMethod = method.id
        showInputMark()
        showQuickSettingsFor(languages.current)
        preparingNotice?.let { if (strip?.notice == it) strip?.notice = null }
        preparingNotice = null
        closePicker()
        strip?.rightToLeft = languages.current.rightToLeft
        if (editing) readSettings()
        // The bar's words were the last input method's (its choices, starters, next words, or a late answer): they go, and the
        // new one's come (Composer.wordsChanged). Before the keys that waited for the load go on, so theirs are the new one's.
        if (editing) composer.wordsChanged()
        loadEnded()
        if (editing) showHoldHint()
        openChinesePage(method)
        val id = InputMethodList.ownId(this) ?: return
        val subtype = InputMethodList.subtype(this, method.id) ?: return
        // Android's list has one entry for TitanKeys, the input method typing, under its name (#76; owner, 2026-11-16).
        languages.toldAndroid(method.id)
        InputMethodList.enable(this, listOf(method.id))
        if (InputMethodList.idOf(currentInputMethodSubtypeOrNull()) != method.id) runCatching { switchInputMethod(id, subtype) }
    }

    /**
     * The first switch to a Chinese input method with no variant picked opens Settings' Chinese page, once (ChineseSetup).
     * As before Android's keyboard list, any key still down is let go first, so none stays down while the app is in front.
     */
    private fun openChinesePage(method: InputMethod) {
        val typing = editing && !languages.englishOnly && !inGuide
        if (!ChineseSetup.opensPage(method, TypingSettings.chineseVariant(this), TypingSettings.chineseVariantShown(this), typing)) return
        TypingSettings.chineseVariantShown(this, true)
        contact.releaseKeys()
        modifiers.clear()
        runCatching { startActivity(Connected.pageIntent(this, ChineseSetup.PAGE)) }
            .onFailure { Log.w("TitanKeys", "Couldn't open the Chinese page", it) }
    }

    private fun currentInputMethodSubtypeOrNull() =
        runCatching { getSystemService(InputMethodManager::class.java)?.currentInputMethodSubtype }.getOrNull()

    /**
     * Android's keyboard list switched to another of TitanKeys' entries: it types from now on, if it's on. Android also says its
     * old entry as the keyboard starts, which mustn't undo a restore or Settings (Languages.androidSays).
     */
    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        val id = InputMethodList.idOf(newSubtype) ?: return
        if (::languages.isInitialized) languages.androidSays(id)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_DEL) return keyDown(keyCode, event)
        // A held Backspace repeats, as on a PC: Android's own repeats where they come, else TitanKeys' ([BackspaceRepeat]).
        val handled = keyDown(keyCode, event)
        if (backspaceRepeat.down(event.repeatCount, handled && editing)) {
            main.removeCallbacks(repeatBackspace)
            main.postDelayed(repeatBackspace, android.view.ViewConfiguration.getKeyRepeatTimeout().toLong())
        } else if (event.repeatCount > 0 && !backspaceRepeat.sending) main.removeCallbacks(repeatBackspace)
        return handled
    }

    /** The held Backspace's next repeat, through the same path as Android's ([BackspaceRepeat]). */
    private val repeatBackspace: Runnable = Runnable {
        val count = backspaceRepeat.next()
        if (count < 0 || !editing) return@Runnable
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0)
        backspaceRepeat.send { onKeyDown(KeyEvent.KEYCODE_DEL, KeyEvent.changeTimeRepeat(heldBackspace ?: down, now, count)) }
        main.postDelayed(repeatBackspace, android.view.ViewConfiguration.getKeyRepeatDelay().toLong())
    }

    private fun keyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL && event.repeatCount == 0 && !backspaceRepeat.sending) heldBackspace = KeyEvent(event)
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
        if (ownerMarked(event)) return true
        if (event.repeatCount == 0) KeyboardEvents.publish { KeyboardEvent.Pushed(keyCode) }
        Recording.sink?.key(event, touchpad.isGestureKey(event))
        inputLog()?.key(event, touchpad.isGestureKey(event))
        if (KeyCapture.waiting?.invoke(event) == true) return true
        // Back closes the bigger menu, and the choices stay on the bar.
        if (keyCode == KeyEvent.KEYCODE_BACK && choicesMenuOpen) {
            if (event.repeatCount == 0) closeChoicesMenu()
            pickerKey = keyCode
            return true
        }
        // Back closes the picker, and its release goes with it.
        if (keyCode == KeyEvent.KEYCODE_BACK && openPicker() != null) {
            if (event.repeatCount == 0) {
                // An engine's code goes with its choices.
                if (picker is LanguagePicker.Choices) languages.engine?.reset()
                closePicker()
            }
            pickerKey = keyCode
            return true
        }
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
        if (chineseKey(event)) return true
        if (languageKey(event)) return true
        // With the word menu open, the top two rows of letters pick its buttons, as the keys pick symbols on the Sym panel, and
        // Backspace, under P, goes back.
        if (keyCode == menuKey && event.repeatCount > 0) return true
        if (!event.isSystem && MenuKeys.takesKeys(menuOpen, altOrPanel) && (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ||
                keyCode == KeyEvent.KEYCODE_DEL)) {
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
        // In the guide's show-only menu, on a slide that locks typing (the word menu slide's selected TitanKeys), any other key does
        // nothing, and the guide says so; the menu stays open, where typing would close it and then be blocked.
        if (!event.isSystem && menuOpen && inGuide && !guideAllows(GuideLesson.Typing) && !Modifiers.isModifier(keyCode)) {
            if (event.repeatCount == 0) KeyboardEvents.publish { KeyboardEvent.MenuMissed }
            return true
        }
        // Typing on closes the menu, and the strip shows words again. The newest clip goes from the strip too.
        if (!event.isSystem && !Modifiers.isModifier(keyCode)) {
            closeMenu()
            dismissClipOffer()
        }
        // The touchpad hears the physical press now, before the key is typed.
        contact.key(keyCode, true, event.eventTime, event.downTime)
        val carried = carryCtrl(event)
        val repeatOfHold = trackHold(carried)
        // A key press means the finger was pressing, not resting on the touchpad.
        endCursor()
        if (repeatOfHold) {
            if (field.exact) logExact("key $keyCode repeat ${event.repeatCount} kept by hold")
            return true
        }
        if (accentlessRepeat(carried)) return true
        // With the panel's groups on the screen, fn picks the one at its place, also as Ctrl, which would take it first (bug hunt #37).
        if (keyCode == Modifiers.FN && panelOpen && isInputViewShown && categoryKey(event)) {
            consumedKeys += keyCode
            return true
        }
        if (ctrlKey(event)) {
            consumedKeys += keyCode
            return true
        }
        if (!carried.isSystem) waitForLoad("Key ${carried.keyCode}")
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
        // In an input method with its own letters, a letter key is the keyboard's, also if nothing above typed it.
        val kept = !handled && keysMapKey(carried)
        if (kept && AppBuild.kind(this) == "debug") Log.w(LOG_TAG, "A letter key in ${languages.current.id} wasn't typed: kept from the app " +
            "(Shift ${shiftOn(carried)})")
        if (handled || kept) consumedKeys += keyCode
        showModifiers()
        return handled || kept || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // The third Shift of an owner mark keeps its release too, so it changes nothing.
        if (keyCode == ownerMarkKey) {
            ownerMarkKey = -1
            return true
        }
        // The last key let go: the mark goes as after a lift, unless the touchpad shows the finger again. A key that toggled a quick
        // setting keeps it: the touchpad's lift for the press comes after the key, and is the press's own. See [keepPanelHover].
        val panelRelease = keyCode == panelPress
        if (panelRelease) {
            panelHover.release(keyCode, event.eventTime)
            panelPress = -1
        }
        val panelLast = !panelRelease && panelHover.keyUp(keyCode, event.eventTime) && panelOpen
        // A key that picked a word menu button keeps the hover when it is let go: the finger is still on it, and its pill and the
        // word's colour now show the button's next step (#84, rule (a), as the quick settings page does). The touchpad's lift for
        // the press mostly comes after the key, and is the press's own; a finger that really leaves then can't be seen, so the
        // pill stays until the touchpad next shows the finger: on another key it follows, and a lift after that ends it. See
        // [SteadyKey.press].
        val menuRelease = keyCode == menuKey && menuOpen
        if (menuRelease) barHover.release(keyCode, event.eventTime)
        val barLast = !menuRelease && barHover.keyUp(keyCode, event.eventTime) && !panelOpen
        if (panelLast || barLast) {
            main.removeCallbacks(expireHover)
            main.postDelayed(expireHover, SteadyKey.LIFT_MILLIS)
        }
        if (keyCode == KeyEvent.KEYCODE_SYM) symLog("keyboard heard Sym up: panel ${modifiers.panel}")
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            modifiers.backspaceReleased()
            backspaceRepeat.up()
            heldBackspace = null
            main.removeCallbacks(repeatBackspace)
        }
        if (lockedKeys.remove(keyCode)) return true
        if (editing && chineseKeyUp(keyCode)) return true
        // Sym's release always ends its press first, so the next Sym is never swallowed ([SymPress]).
        if (keyCode == KeyEvent.KEYCODE_SYM && symReleased()) return true
        // The release of a key that picked from the picker, or closed it, belongs to that.
        if (keyCode == pickerKey) {
            pickerKey = -1
            return true
        }
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
        // With text being built, Enter is the engine's: it types the code as letters and clears it (docs/languages.md). Only
        // with nothing pending does Enter reach the field: before, the field's Enter came first, so a code stayed pending
        // and a new line went in (phone check, 2026-11-16).
        if (enteringCode(event)) languages.engine?.let { engine -> if (engineKey(engine, event)) return true }
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
        languages.engine?.let { engine -> if (engineKey(engine, event)) return true }
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
                inputMethodLetter(event)?.let { text ->
                    modifiers.consume()
                    composer.type(text)
                    return true
                }
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
        if (modifiers.panel == Modifiers.Panel.Settings) QuickSetting.on(letter)?.takeIf { letter !in panel?.hiddenKeys.orEmpty() }?.let { item ->
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

    /** Enter, without Ctrl or Meta, while an engine has text being built: it goes to the engine, never the field. */
    private fun enteringCode(event: KeyEvent) = (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) &&
        !event.isMetaPressed && !event.isCtrlPressed && openPicker() is LanguagePicker.Choices

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
            Log.w("TitanKeys", "Could not send an exact key", error)
        }
    }

    private fun commitExact(text: String) {
        try {
            currentInputConnection?.commitText(text, 1)
        } catch (error: RuntimeException) {
            Log.w("TitanKeys", "Could not send exact text", error)
        }
    }

    /** Only TitanKeys Debug writes these lines, to find faults in exact typing. They hold key codes and states, never text. */
    private val exactLogging by lazy { packageName.endsWith(".debug") }

    private fun logExact(line: String) {
        if (exactLogging) Log.i("TitanKeysExact", line)
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
                Log.w("TitanKeys", "Could not forward an ordered key", error)
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
        // A panel or Alt takes the menu's keys, so the menu closes rather than be pressed unseen.
        if (menuOpen && MenuKeys.closes(altOrPanel, hidden = false, guideShowOnly = inGuide)) closeMenu()
        if (panelOpen && choicesMenuOpen) choicesMenu?.visibility = View.GONE
        strip?.visibility = if (panelOpen || choicesMenuOpen) View.GONE else View.VISIBLE
        updateExactBar()
        showModifierBar()
        panel?.mode = modifiers.panel
        setPaging()
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
        if (!editing || field.exact || !field.sentenceCapitalisation || panelOpen || !TypingSettings.autoCapitaliseIn(this, languages.languageId) ||
            !languages.current.rules.capitalise) return false
        val before = currentInputConnection?.getTextBeforeCursor(SENTENCE_REACH, 0) ?: return false
        return SwipeTyping.startsSentence(before)
    }

    private fun showModifierBar() {
        // Shift and Caps show only where Shift changes what a key types: not in Hebrew, Chinese or Japanese.
        val shiftShows = !::languages.isInitialized || languages.current.shiftMatters
        strip?.modifiers = ModifierBar.of(modifiers, restingKey, shiftShows) { Keys.letter(it, KeyEvent.META_ALT_ON) }
    }

    override fun onDestroy() {
        destroyed = true
        editing = false
        // It holds this keyboard, which must go with it (#85).
        TouchpadFeedHook.dispatch = null
        TouchpadFeedHook.cancelContact = null
        KeyFeedHook.dispatch = null
        KeyFeedHook.cancelHeld = null
        GuideHooks.panelRequests = null
        GuideHooks.stripDemos = null
        GuideHooks.stripPoints = null
        GuideHooks.barSideRequests = null
        GuideHooks.stripPointWords = null
        GuideHooks.clipSeeds = null
        GuideHooks.barHolds = null
        GuideHooks.clipDismissals = null
        // They hold this keyboard, which must go with it (#85).
        GuideHooks.menuOpen = null
        GuideHooks.menuCloses = null
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
        // Its word list and engines go with it, the warm ones too.
        if (::languages.isInitialized) languages.stop()
        editor.cancelState()
        TypingSettings.stopListening(this, overlaySetting)
        barSize?.let { FixedScale.stopListening(this, it) }
        overlay.hide()
        BackService.releaseTouchpad(overlayEvent)
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
        /** The late Sliding Mode reading, long after any screen opening has finished. */
        private const val SLIDING_CONFIRM_MILLIS = 3000L
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
        /** How long the hold hint stays on the bar, unless a key or touch takes it first. */
        private const val HOLD_HINT_MILLIS = 6_000L
        /** How long the bar says which input method types, after a switch in the overlay. */
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
        /** How far each way the cursor's paragraph is read for its direction. */
        private const val RTL_REACH = 200
        private val NAVIGATION_KEYS = setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MOVE_HOME,
            KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN)
        private val FIELD_LEAVING_KEYS = setOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_TAB)
        private val FIELD_SHORTCUTS = Modifiers.CTRL_ACTIONS.keys.map { KeyEvent.KEYCODE_A + (it[0] - 'A') }.toSet()
        private val DESTRUCTIVE_SHORTCUTS = setOf(KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_Y)
    }
}
