package com.vandam.ink

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.titankeys.autocorrect.FixKind
import com.titankeys.autocorrect.Shorthand
import com.titankeys.engine.EnglishSpelling
import com.titankeys.engine.SwipeTyping
import com.titankeys.engine.TypingSettings
import com.titankeys.keyboard.Accent
import com.titankeys.keyboard.AppBuild
import com.titankeys.keyboard.BackDisclosureActivity
import com.titankeys.keyboard.BackService
import com.titankeys.keyboard.EnterKey
import com.titankeys.keyboard.CrashReport
import com.titankeys.keyboard.ExactTyping
import com.titankeys.keyboard.FixedScale
import com.titankeys.keyboard.GuideActivity
import com.titankeys.keyboard.Haptic
import com.titankeys.keyboard.HapticGroup
import com.titankeys.keyboard.Haptics
import com.titankeys.keyboard.HapticsLab
import com.titankeys.keyboard.InputMethodIndex
import com.titankeys.keyboard.InputMethodList
import com.titankeys.keyboard.InkPress
import com.titankeys.keyboard.KeyCalibrationActivity
import com.titankeys.keyboard.KeyCapture
import com.titankeys.keyboard.Keys
import com.titankeys.keyboard.Layout
import com.titankeys.keyboard.PhoneNameSource
import com.titankeys.keyboard.PhoneChecks
import com.titankeys.keyboard.PhoneSetup
import com.titankeys.keyboard.RawLog
import com.titankeys.keyboard.FreshStart
import com.titankeys.keyboard.SettingsReset
import com.titankeys.keyboard.SetupPart
import com.titankeys.keyboard.Expansions
import com.titankeys.keyboard.Snippets
import com.titankeys.keyboard.Trace
import com.titankeys.keyboard.TouchpadPreview
import com.titankeys.keyboard.UpdateNotice
import org.json.JSONArray
import org.json.JSONObject

/** Ink's activity keeps TitanKeys' sizes at any display size or font size, like every other TitanKeys window (FixedScale). */
internal fun appBaseContext(base: Context): Context = FixedScale.wrap(base)

/**
 * Ink's screens stay at Normal: Bar text size grows only the keyboard's bar (owner, 2026-11-13: "the settings become a bit
 * unusable after making it bigger"). The patch's hook stays, for Ink's own scale, at 1.
 */
internal fun appUserScale(@Suppress("UNUSED_PARAMETER") context: Context): Float = FixedScale.UNSCALED

/** Settings > Orange accents: with it off, Ink's screens take the cream that stands in for orange everywhere (Accent). */
internal fun appAccent(context: Context): Int = if (Accent.on) 0 else Accent.colour

/**
 * Ink's press haptic is Android's stock click, which TitanKeys never plays (docs/haptics.md, rule 5). Every press in Ink's pages
 * comes here, ✕ and ← in the header too, and plays Press, unless the control plays its own through lib/app-haptics.tsx: see
 * [InkPress]. Always true, so Ink's own click never plays.
 */
internal fun appPressHaptic(view: android.view.View): Boolean {
    inkPress?.pressed()
    return true
}

/** The live Settings screen's [InkPress], while it lives. */
private var inkPress: InkPress? = null

internal fun createAppNativeAdapters(activity: MainActivity): Map<String, NativeAdapter> =
    mapOf("titankeys" to TitanKeysAdapter(activity), "connected" to ConnectedAdapter(activity))

private class TitanKeysAdapter(private val activity: MainActivity) : NativeAdapter, Application.ActivityLifecycleCallbacks {
    private val typing by lazy { SwipeTyping.get(activity) }
    private var stopWatching: (() -> Unit)? = null
    private val previewController = lazy { TouchpadPreview(activity) }
    private var watchingRequested = false
    private var previewRequested = false
    // Adapters can be created before the live activity's window gains focus.
    private var visible = true
    private var transferring = false
    private val keyCapture = lazy { KeyCapture(activity) { key -> activity.emit(JSONObject().put("type", "titankeys-key").put("key", key).toString()) } }
    private var keyCaptureRequested = false
    private val appNames = HashMap<String, String>()
    private val hapticsLab = lazy { HapticsLab(activity) }
    /** Plays the app's own controls' haptics, through the same switches and gate as the keyboard's. */
    private val haptics by lazy { Haptics(activity) }
    private val pressHandler = Handler(Looper.getMainLooper())
    /** Press for each press in Ink's pages, which a control's own haptic takes the place of. */
    private val press = InkPress({ run, millis -> pressHandler.postDelayed(run, millis) }, pressHandler::removeCallbacks) {
        haptics.play(Haptic.AppPress)
    }
    /** The listed shorthand that has a full word, with that word, in the list's order, for its switches in Settings. */
    private val listedShorthand by lazy {
        activity.assets.open("autocorrect/en_shorthand.txt").bufferedReader().useLines(Shorthand::parse)
            .mapNotNull { (shorthand, full) -> full?.let { shorthand to it } }
    }
    private val windowFocus = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus) activity.window.decorView.post {
            // The front page opens the playground whenever the app's own window is in front, as when the playground was closed.
            if (visible && activity.hasWindowFocus()) activity.emit(JSONObject().put("type", "titankeys-window").toString())
            if (visible && watchingRequested && activity.hasWindowFocus()) {
                // Ink may have blurred the field while the window was away; ask the page to autofocus again.
                activity.emit(JSONObject().put("type", "titankeys-focus").toString())
                val editor = previewEditor(activity.window.decorView)
                if (editor != null && !editor.hasFocus()) {
                    editor.requestFocus()
                    val input = activity.getSystemService(InputMethodManager::class.java)
                    input.restartInput(editor)
                    input.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }
    private val keyboardSettings = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = publishState()
    }

    private fun publishState() = activity.emit(JSONObject().put("type", "titankeys-state").toString())

    private fun previewEditor(view: View): EditText? = when {
        view is EditText && view.isShown -> view
        view is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { previewEditor(view.getChildAt(it)) }
        else -> null
    }

    init {
        inkPress = press
        activity.application.registerActivityLifecycleCallbacks(this)
        activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(windowFocus)
        // Enabling and choosing the keyboard happen outside the app; the screens follow as they change.
        for (setting in listOf(Settings.Secure.ENABLED_INPUT_METHODS, Settings.Secure.DEFAULT_INPUT_METHOD,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
            activity.contentResolver.registerContentObserver(Settings.Secure.getUriFor(setting), false, keyboardSettings)
        // Settings load without waiting for the swipe engine; tell them when it finishes loading.
        typing.whenReady { _, _, _ -> publishState() }
        offerCrashReport()
        UpdateNotice.ran(activity)
    }

    /** The crash or freeze report waiting for its offer, and the dialog showing it on the screen in front. */
    private var crashOffer: String? = null
    private var crashDialog: android.app.Dialog? = null

    /**
     * The crash offer (#74, #97), once, as the app opens after a crash or freeze: send the report through the form, where it's
     * attached, or let it go. Nothing is ever sent by itself.
     *
     * It shows on whichever TitanKeys screen is in front, so it's never under the playground, which is its own activity over
     * this one (phone check, 2026-11-16). When another of the app's screens comes in front, the offer moves to it.
     */
    private fun offerCrashReport() {
        Thread {
            CrashReport.forgetLent(activity)
            val report = CrashReport.pending(activity) ?: return@Thread
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                crashOffer = report
                showCrashOffer(frontScreen ?: activity)
            }
        }.start()
    }

    /** The app's screen in front: the last one resumed. One the offer doesn't show on waits for one it does. */
    private var frontScreen: Activity? = null

    private fun showCrashOffer(host: Activity) {
        val report = crashOffer ?: return
        if (crashDialog?.ownerActivity === host && crashDialog?.isShowing == true) return
        if (host.isFinishing || host.isDestroyed || !CrashOfferHosts.shows(host)) return
        // Moving it to another screen isn't an answer: dismiss() keeps the report, only a choice or Back clears it.
        crashDialog?.dismiss()
        // TitanKeys' own card, big enough to read and tap (owner, 2026-11-16), with F's words (CrashOffer.kt).
        crashDialog = com.titankeys.keyboard.CrashOffer.dialog(host, CrashReport.froze(report),
            send = {
                answeredCrashOffer()
                CrashReport.offered(activity)
                host.startActivity(com.titankeys.connected.Connected.pageIntent(activity, REPORT_PAGE))
            },
            no = {
                answeredCrashOffer()
                CrashReport.clear(activity)
            })
        crashDialog!!.show()
    }

    private fun answeredCrashOffer() {
        crashOffer = null
        crashDialog = null
    }

    private fun syncPreview() {
        if (visible && watchingRequested) {
            if (stopWatching == null) stopWatching = Trace.listen { activity.emit(it.put("type", "titankeys-trace").toString()) }
        } else {
            stopWatching?.invoke()
            stopWatching = null
        }
        if (visible && previewRequested) previewController.value.start()
        else if (previewController.isInitialized()) previewController.value.stop()
        if (visible && keyCaptureRequested) keyCapture.value.start()
        else if (keyCapture.isInitialized()) keyCapture.value.stop()
    }

    override fun onActivityResumed(target: Activity) {
        frontScreen = target
        if (crashOffer != null) showCrashOffer(target)
        if (target !== activity) return
        visible = true
        syncPreview()
        publishState()
    }

    override fun onActivityPaused(target: Activity) {
        if (target !== activity) return
        visible = false
        syncPreview()
    }

    override fun onActivityStopped(target: Activity) = onActivityPaused(target)

    override fun onActivityDestroyed(target: Activity) {
        if (frontScreen === target) frontScreen = null
        // A dialog can't outlive its screen. The report stays for the next screen in front, which shows it as it resumes.
        if (crashDialog?.ownerActivity === target) {
            crashDialog?.dismiss()
            crashDialog = null
        }
        if (target !== activity) return
        activity.window.decorView.viewTreeObserver.removeOnWindowFocusChangeListener(windowFocus)
        visible = false
        syncPreview()
        watchingRequested = false
        previewRequested = false
        keyCaptureRequested = false
        if (hapticsLab.isInitialized()) hapticsLab.value.stop()
        activity.contentResolver.unregisterContentObserver(keyboardSettings)
        activity.application.unregisterActivityLifecycleCallbacks(this)
        // The static press holds this screen's haptics and so the activity: it goes with it (#85). A newer screen's stays.
        if (inkPress === press) inkPress = null
        pressHandler.removeCallbacksAndMessages(null)
        // The playground's answer and its wait hold this screen too.
        GuideActivity.appScreenClosed(activity)
    }

    override fun onActivityCreated(target: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(target: Activity) = Unit
    override fun onActivitySaveInstanceState(target: Activity, state: Bundle) = Unit

    override fun execute(requestId: Long, operation: String, payload: String, complete: NativeResultHandler) {
        try {
            val data = JSONObject(payload)
            // Asynchronous engine operations complete through their own outcome callback.
            val result: String? = when (operation) {
                "state" -> state().toString()
                // A control on a TitanKeys screen (lib/app-haptics.ts). Only the app's own haptics: the keyboard's play from the keyboard.
                "haptic" -> {
                    val haptic = Haptic.of(data.getString("id"))
                    require(haptic?.group == HapticGroup.App) { "Not an app haptic: ${data.getString("id")}" }
                    // The control's own haptic, for what it means, takes the place of the press's click.
                    press.meaning()
                    haptics.play(haptic!!)
                    "null"
                }
                "set" -> {
                    set(data.getString("key"), data.get("value"))
                    "null"
                }
                "setEnterApp" -> {
                    val method = requireNotNull(EnterKey.SendMethod.of(data.getString("method"))) { "Unknown send method" }
                    TypingSettings.enterApps(activity, EnterKey.savedApps(TypingSettings.enterApps(activity), data.getString("app"), method))
                    "null"
                }
                "removeEnterApp" -> {
                    TypingSettings.enterApps(activity, EnterKey.savedApps(TypingSettings.enterApps(activity), data.getString("app"), null))
                    "null"
                }
                "setListedApp" -> {
                    val app = data.getString("app")
                    val on = data.getBoolean("on")
                    when (val list = data.getString("list")) {
                        "exact" -> ExactTyping.apps(activity, ExactTyping.apps(activity).toMutableSet().apply { if (on) add(app) else remove(app) })
                        "hiddenBar" -> TypingSettings.hiddenBarApps(activity,
                            TypingSettings.hiddenBarApps(activity).toMutableSet().apply { if (on) add(app) else remove(app) })
                        else -> throw IllegalArgumentException("Unknown app list: $list")
                    }
                    "null"
                }
                "installedApps" -> {
                    // Reading every app's name can take a moment, so it happens away from the main thread.
                    Thread {
                        val apps = installedApps()
                        activity.runOnUiThread { complete(NativeResult.Success(apps.toString())) }
                    }.start()
                    null
                }
                "putSnippet" -> {
                    Snippets.put(activity, data.getString("shortcut").trim(), data.getString("text"),
                        data.optString("previous").takeIf { it.isNotEmpty() })
                    "null"
                }
                "putShorthand" -> {
                    val shorthand = data.getString("shorthand").trim()
                    val full = data.getString("full").trim()
                    Shorthand.problem(shorthand, full)?.let { throw IllegalArgumentException(it) }
                    val previous = data.optString("previous").takeIf { it.isNotEmpty() }
                    if (Expansions.taken(Shorthand.key(shorthand), previous?.let(Shorthand::key), TypingSettings.ownShorthand(activity).keys))
                        throw IllegalArgumentException("You already have ${Shorthand.key(shorthand)}. Change that one, or pick another shorthand.")
                    previous?.let { TypingSettings.ownShorthand(activity, Shorthand.key(it), null) }
                    TypingSettings.ownShorthand(activity, Shorthand.key(shorthand), full)
                    "null"
                }
                "removeShorthand" -> {
                    TypingSettings.ownShorthand(activity, Shorthand.key(data.getString("shorthand")), null)
                    "null"
                }
                "removeSnippet" -> {
                    Snippets.remove(activity, data.getString("shortcut"))
                    "null"
                }
                "addWord" -> {
                    typing.addWord(data.getString("word")) { error -> operationResult(error, complete) }
                    null
                }
                "removeWord" -> {
                    typing.removeWord(data.getString("word")) { error -> operationResult(error, complete) }
                    null
                }
                "blockWord", "unblockWord" -> {
                    typing.setWordBlocked(data.getString("word"), operation == "blockWord") { error -> operationResult(error, complete) }
                    null
                }
                "clearWords" -> {
                    typing.clearDictionary { error -> operationResult(error, complete) }
                    null
                }
                // Settings > Your words > Back up everything and Restore (#65). A whole restore restarts TitanKeys, so it never answers.
                "backup" -> {
                    check(!transferring) { "A backup or restore is already open." }
                    val mode = data.getString("mode")
                    require(mode == "backup" || mode == "restore")
                    transferring = true
                    try {
                        BackupActivity.start(activity, mode == "backup") { done, error ->
                            transferring = false
                            if (error == null) complete(NativeResult.Success(JSONObject.quote(done ?: "")))
                            else operationResult(error, complete)
                        }
                    } catch (error: Exception) {
                        transferring = false
                        throw error
                    }
                    null
                }
                "copyText" -> {
                    activity.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText(data.getString("label"), data.getString("text")))
                    "null"
                }
                // Send feedback's Crash TitanKeys, so the crash offer can be tested. TitanKeys Debug only.
                "crash" -> {
                    check(HapticsLab.available(activity)) { "Crash TitanKeys is only in TitanKeys Debug" }
                    pressHandler.post { throw IllegalStateException("Crash TitanKeys, from Send feedback in TitanKeys Debug") }
                    "null"
                }
                "exportRawLog" -> {
                    check(RawLog.available(activity)) { "The typing log is only in TitanKeys Debug" }
                    check(!transferring) { "An export or import is already open." }
                    transferring = true
                    try {
                        RawLogExportActivity.start(activity) { exported, error ->
                            transferring = false
                            if (error == null) complete(NativeResult.Success(exported.toString()))
                            else operationResult(error, complete)
                        }
                    } catch (error: Exception) {
                        transferring = false
                        throw error
                    }
                    null
                }
                "deleteRawLog" -> {
                    Thread {
                        RawLog.deleteAll(activity)
                        activity.runOnUiThread { complete(NativeResult.Success("null")) }
                    }.start()
                    null
                }
                // Settings > Reset most things. Settings shows it at once; the keyboard and the guide as they next draw.
                "resetSettings" -> {
                    SettingsReset.reset(activity)
                    activity.applyAccent()
                    publishState()
                    "null"
                }
                // Settings > Reset literally everything: wipes it all and restarts TitanKeys on its front page (FreshStart).
                "wipeEverything" -> {
                    SettingsReset.reset(activity)
                    FreshStart.wipe(activity)
                    "null"
                }
                "phoneWarningSeen" -> {
                    appPreferences().edit().putBoolean(PHONE_WARNING_SEEN, true).apply()
                    "null"
                }
                // TitanKeys Debug only. Turning it on shows the warning once more, so it can be checked again.
                "fakeOtherPhone" -> {
                    require(AppBuild.kind(activity) == "debug") { "Only TitanKeys Debug can fake another phone" }
                    val on = data.getBoolean("on")
                    appPreferences().edit().putBoolean(FAKE_OTHER_PHONE, on).apply { if (on) remove(PHONE_WARNING_SEEN) }.apply()
                    "null"
                }
                "notesSeen" -> {
                    appPreferences().edit().putString(SEEN_NOTES, installedVersion()).apply()
                    "null"
                }
                "guide" -> {
                    GuideActivity.start(activity)
                    "null"
                }
                // Back from a page that the guide's end opened: the end again, as it was left.
                "guideEnd" -> {
                    GuideActivity.startAtEnd(activity)
                    "null"
                }
                "playground", "playgroundFromEnd" -> {
                    // The answer comes as a message when the playground closes. A request held open that long would time out.
                    GuideActivity.playground(activity, fromEnd = operation == "playgroundFromEnd") { action ->
                        activity.emit(JSONObject().put("type", "titankeys-playground").put("action", action).toString())
                    }
                    "null"
                }
                "playgroundCancel" -> {
                    GuideActivity.cancelPlayground()
                    "null"
                }
                "enable", "overlay", "scrollAssistant", "backService" -> {
                    val part = when (operation) {
                        "enable" -> SetupPart.Enable
                        "overlay" -> SetupPart.Overlay
                        "scrollAssistant" -> SetupPart.ScrollAssistant
                        else -> SetupPart.Back
                    }
                    // Accessibility opens only after its own disclosure, as Google Play asks.
                    if (part == SetupPart.Back) activity.startActivity(BackDisclosureActivity.forSettings(activity))
                    else PhoneSetup.open(activity, part, activity::startActivity)
                    "null"
                }
                "choose" -> {
                    PhoneSetup.chooseKeyboard(activity)
                    "null"
                }
                // Opens the phone's own page. TitanKeys never changes these settings itself.
                "shortcutKeys" -> {
                    activity.startActivity(PhoneChecks.shortcutKeysPage(activity))
                    "null"
                }
                "watch" -> {
                    watchingRequested = true
                    syncPreview()
                    "null"
                }
                "unwatch" -> {
                    watchingRequested = false
                    syncPreview()
                    // Give Android a non-editor destination instead of leaving the hidden preview served.
                    val root = activity.window.decorView as ViewGroup
                    val descendants = root.descendantFocusability
                    root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    try {
                        root.isFocusableInTouchMode = true
                        root.requestFocus()
                    } finally {
                        root.descendantFocusability = descendants
                    }
                    "null"
                }
                "preview" -> {
                    previewRequested = true
                    syncPreview()
                    "null"
                }
                "unpreview" -> {
                    previewRequested = false
                    if (previewController.isInitialized()) previewController.value.stop()
                    "null"
                }
                "captureKey" -> {
                    keyCaptureRequested = true
                    syncPreview()
                    "null"
                }
                "releaseKey" -> {
                    keyCaptureRequested = false
                    syncPreview()
                    "null"
                }
                "labGraph" -> {
                    lab().showGraph(data.getJSONArray("haptics").toString())
                    "null"
                }
                "keyCalibration" -> {
                    check(HapticsLab.available(activity)) { "Key calibration is only in TitanKeys Debug" }
                    activity.startActivity(KeyCalibrationActivity.intent(activity))
                    "null"
                }
                "labExperiment" -> {
                    lab().experiment(data.getString("id"), data.getBoolean("on"))
                    "null"
                }
                "labFlickHold" -> {
                    lab().tryFlickHold(if (data.isNull("millis")) null else data.getLong("millis"))
                    "null"
                }
                "labSelectionEnd" -> {
                    lab().trySelectionEnd(if (data.isNull("id")) null else data.getString("id"))
                    "null"
                }
                "labPlayFlick" -> {
                    lab().playFlick()
                    // The lab's flick is what's being judged: no Press on top of it.
                    press.meaning()
                    "null"
                }
                else -> throw IllegalArgumentException("Unknown TitanKeys operation: $operation")
            }
            if (result != null) complete(NativeResult.Success(result))
        } catch (error: Exception) {
            complete(NativeResult.Failure(NativeErrorKind.UNEXPECTED, error.message ?: "Something went wrong. Try again.", false))
        }
    }

    override fun cancel(requestId: Long) = Unit

    /** The Haptics lab, which only TitanKeys Debug has. Every other build refuses each of its operations. */
    private fun lab(): HapticsLab {
        check(HapticsLab.available(activity)) { "The Haptics lab is only in TitanKeys Debug" }
        return hapticsLab.value
    }

    private fun operationResult(error: String?, complete: NativeResultHandler) {
        complete(if (error == null) NativeResult.Success("null")
            else NativeResult.Failure(NativeErrorKind.UNEXPECTED, error, true))
    }

    private fun state(): JSONObject {
        val learned = typing.learnedWords()
        val words = JSONArray()
        for ((index, word) in learned.words.withIndex())
            words.put(JSONObject().put("word", word).put("description", learned.swipeStatuses[index]))
        return JSONObject()
            .put("version", installedVersion())
            .put("whatsNew", whatsNew())
            .put("guideDue", GuideActivity.due(activity))
            .put("phone", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("android", Build.VERSION.RELEASE)
            .put("enabled", PhoneSetup.enabled(activity))
            .put("selected", PhoneSetup.selected(activity))
            .put("overlay", PhoneSetup.overlay(activity))
            .put("scrollAssistant", PhoneSetup.scrollAssistant())
            .put("backService", BackService.enabled(activity))
            .put("backDeclined", BackDisclosureActivity.declined(activity))
            // The front page's setup decision is the playground's own, from one definition (PhoneSetup.toDo, lib/front.ts).
            .put("setupToDo", PhoneSetup.toDo(activity))
            // The front page decides from these whether this is a Titan 2 Elite (lib/front.ts).
            .put("touchpadFunction", systemProperty("ro.agui.touchpad_function"))
            .put("device", Build.DEVICE ?: "")
            .put("fakeOtherPhone", AppBuild.kind(activity) == "debug" && appPreferences().getBoolean(FAKE_OTHER_PHONE, false))
            .put("phoneWarningSeen", appPreferences().getBoolean(PHONE_WARNING_SEEN, false))
            .put("overlayOnList", PhoneSetup.onList(activity, SetupPart.Overlay))
            .put("backOnList", PhoneSetup.onList(activity, SetupPart.Back))
            .put("symKey", PhoneChecks.programmable(activity, PhoneChecks.Key.Sym))
            .put("fnKey", PhoneChecks.programmable(activity, PhoneChecks.Key.Fn))
            .put("outsideSlidingMode", JSONArray(PhoneChecks.outsideSlidingMode(activity)))
            .put("learning", typing.learningEnabled())
            .put("contactNames", PhoneNameSource.contactsAllowed(activity))
            .put("tapKeys", TypingSettings.tapKeys(activity))
            .put("holdSymbols", TypingSettings.holdSymbols(activity))
            .put("holdSymbolMillis", TypingSettings.holdSymbolMillis(activity))
            .put("fnAsCtrl", TypingSettings.fnAsCtrl(activity))
            .put("altBackspaceForward", TypingSettings.altBackspaceForward(activity))
            .put("shiftBackspaceWord", TypingSettings.shiftBackspaceWord(activity))
            .put("altSymNextKeyboard", TypingSettings.altSymNextKeyboard(activity))
            .put("doublePressLocks", TypingSettings.doublePressLocks(activity))
            .put("autoCapitalise", TypingSettings.autoCapitalise(activity))
            .put("autoPunctuation", TypingSettings.autoPunctuation(activity))
            .put("sentenceSpaces", TypingSettings.sentenceSpaces(activity))
            .put("commaSpace", TypingSettings.commaSpace(activity))
            .put("autocorrect", TypingSettings.autocorrect(activity))
            .put("fixToggles", JSONObject(FixKind.entries.associate { it.id to TypingSettings.fixOn(activity, it.id) }))
            .put("fixOnlyWhenSure", TypingSettings.fixOnlyWhenSure(activity))
            .put("suggestionBar", TypingSettings.suggestionBar(activity))
            .put("launcherHidesBar", TypingSettings.launcherHidesBar(activity))
            .put("cursorControl", TypingSettings.cursorControl(activity))
            .put("swipeTyping", TypingSettings.swipeTyping(activity))
            .put("touchpadOnlyInFields", TypingSettings.touchpadOnlyInFields(activity))
            .put("orangeAccents", TypingSettings.orangeAccents(activity))
            .put("barTextSize", FixedScale.size(activity).id)
            .put("enterKey", TypingSettings.enterKey(activity))
            .also(::putEnterApps)
            .also(::putExactApps)
            .put("snippetStart", TypingSettings.snippetStart(activity).toString())
            .put("altKeys", altKeys())
            .put("shortcodes", TypingSettings.shortcodes(activity))
            .put("expandShorthand", TypingSettings.expandShorthand(activity))
            .also(::putShorthand)
            .put("snippets", JSONArray(Snippets.load(activity).map { (shortcut, text) ->
                JSONObject().put("shortcut", shortcut).put("text", text)
            }))
            .put("liftOff", TypingSettings.liftOffGrace(activity))
            .put("liftOffMillis", TypingSettings.liftOffDurationMillis(activity))
            .put("haptics", Haptics.enabled(activity))
            .put("hapticToggles", JSONObject(Haptics.toggles(activity)))
            .put("hapticsLab", if (HapticsLab.available(activity)) hapticsLab.value.state() else JSONObject.NULL)
            .put("spelling", TypingSettings.spelling(activity).value())
            .put("inputMethods", JSONArray(TypingSettings.inputMethods(activity).split(',').filter { it.isNotBlank() }))
            .put("availableInputMethods", JSONArray(inputMethodIndex().entries.map {
                JSONObject().put("id", it.id).put("name", it.name).put("nameEnglish", it.nameEnglish).put("label", it.label)
                    .put("script", it.script).put("engine", it.engine ?: JSONObject.NULL)
                    .put("language", JSONObject().put("id", it.language.id).put("name", it.language.name)
                        .put("nameEnglish", it.language.nameEnglish).put("label", it.language.label).put("script", it.language.script)
                        .put("experimental", it.language.experimental))
            }))
            .put("chineseVariant", TypingSettings.chineseVariant(activity))
            .put("rawLogAvailable", RawLog.available(activity))
            .put("rawLog", RawLog.on(activity))
            .put("build", AppBuild.kind(activity))
            .put("rawLogBytes", RawLog.size(activity))
            .put("words", words)
            .put("blockedWords", JSONArray(typing.blockedWords().toList()))
    }

    private fun inputMethodIndex() = InputMethodList.index(activity)

    private fun putShorthand(state: JSONObject) {
        val off = TypingSettings.shorthandOff(activity)
        state.put("shorthand", JSONArray(listedShorthand.map { (shorthand, full) ->
            JSONObject().put("shorthand", shorthand).put("full", full).put("on", shorthand !in off)
        }))
        state.put("ownShorthand", JSONArray(TypingSettings.ownShorthand(activity).map { (shorthand, full) ->
            JSONObject().put("shorthand", shorthand).put("full", full)
        }))
    }

    /** The letter key that types each symbol with Alt, read from the phone's own keyboard, such as "/" to "G". */
    private fun altKeys() = JSONObject().also { keys ->
        for (key in Layout.letterKeys) Keys.letter(key, KeyEvent.META_ALT_ON)?.let { if (!keys.has(it)) keys.put(it, key) }
    }

    private fun putEnterApps(state: JSONObject) {
        val added = EnterKey.apps(TypingSettings.enterApps(activity))
        val builtInNames = EnterKey.BUILT_IN.associate { it.packageName to it.name }
        state.put("enterApps", JSONArray(added.entries.sortedBy { it.key }.map { (app, method) ->
            enterApp(app, builtInNames[app] ?: appName(app), method)
        }))
        state.put("enterAppsBuiltIn", JSONArray(EnterKey.BUILT_IN.filter { it.packageName !in added }.map {
            enterApp(it.packageName, it.name, it.method)
        }))
        state.put("recentEnterApps", JSONArray(TypingSettings.recentEnterApps(activity)
            .filter { it !in added && it !in builtInNames }
            .map { JSONObject().put("app", it).put("name", appName(it)) }))
    }

    private fun putExactApps(state: JSONObject) {
        state.put("exactApps", listedApps(ExactTyping.apps(activity)))
        state.put("exactRealAlt", ExactTyping.realAlt(activity))
        state.put("hiddenBarApps", listedApps(TypingSettings.hiddenBarApps(activity)))
        state.put("recentApps", JSONArray(TypingSettings.recentApps(activity)
            .map { JSONObject().put("app", it).put("name", appName(it)) }))
    }

    /** Apps on the phone first, by name. An app that is not on the phone, such as a default, still shows, so that it can be removed. */
    private fun listedApps(apps: Set<String>) =
        JSONArray(apps.map { JSONObject().put("app", it).put("name", appName(it)).put("installed", installed(it)) }
            .sortedWith(compareBy({ !it.getBoolean("installed") }, { it.getString("name").lowercase() })))

    private fun enterApp(app: String, name: String, method: EnterKey.SendMethod) =
        JSONObject().put("app", app).put("name", name).put("method", method.id)

    private fun installed(app: String) = try {
        activity.packageManager.getApplicationInfo(app, 0)
        true
    } catch (error: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * The apps with a screen in the launcher, for the exact typing list. The manifest asks to see only these, which Google Play
     * allows without the permission to see every app.
     */
    private fun installedApps(): JSONArray {
        val packages = activity.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return JSONArray(packages.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packages).toString() }
            .filter { (app, _) -> app != activity.packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
            .map { (app, name) -> JSONObject().put("app", app).put("name", name) })
    }

    /** Android lets a keyboard see the apps that it has typed in. If it cannot see one now, the package name is shown. */
    private fun appName(app: String) = appNames.getOrPut(app) {
        try {
            val packages = activity.packageManager
            packages.getApplicationLabel(packages.getApplicationInfo(app, 0)).toString()
        } catch (error: PackageManager.NameNotFoundException) {
            app
        }
    }

    /** The Titan's Scroll assistant switch powers the keyboard touchpad, and it ships off. Phones without one pass. */
    private fun installedVersion(): String = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""

    /** Release notes show once after each update. A fresh install has nothing new, so it starts as seen. */
    private fun whatsNew(): Boolean {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        val preferences = appPreferences()
        val seen = preferences.getString(SEEN_NOTES, null) ?: if (info.firstInstallTime == info.lastUpdateTime) {
            preferences.edit().putString(SEEN_NOTES, info.versionName).apply()
            info.versionName
        } else null
        return seen != info.versionName
    }

    private fun appPreferences() = activity.getSharedPreferences("app", Context.MODE_PRIVATE)

    /** A system property, or empty where it's missing or can't be read. */
    private fun systemProperty(name: String): String = try {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as? String ?: ""
    } catch (error: ReflectiveOperationException) {
        ""
    }

    private fun set(key: String, value: Any) {
        when (key) {
            "learning" -> typing.learningEnabled(value as Boolean)
            // Turning it on asks Android first, from a screen of its own. It is on only once Android allows it.
            "contactNames" -> if (value as Boolean && !PhoneNameSource.contactsAllowed(activity)) ContactsPermissionActivity.start(activity)
                else TypingSettings.contactNames(activity, value)
            "tapKeys" -> TypingSettings.tapKeys(activity, value as String)
            "holdSymbols" -> TypingSettings.holdSymbols(activity, value as Boolean)
            "holdSymbolMillis" -> TypingSettings.holdSymbolMillis(activity, (value as Number).toInt())
            "fnAsCtrl" -> TypingSettings.fnAsCtrl(activity, value as Boolean)
            "altBackspaceForward" -> TypingSettings.altBackspaceForward(activity, value as Boolean)
            "shiftBackspaceWord" -> TypingSettings.shiftBackspaceWord(activity, value as Boolean)
            "altSymNextKeyboard" -> TypingSettings.altSymNextKeyboard(activity, value as Boolean)
            "doublePressLocks" -> TypingSettings.doublePressLocks(activity, value as Boolean)
            "autoCapitalise" -> TypingSettings.autoCapitalise(activity, value as Boolean)
            "autoPunctuation" -> TypingSettings.autoPunctuation(activity, value as Boolean)
            "sentenceSpaces" -> TypingSettings.sentenceSpaces(activity, value as String)
            "commaSpace" -> TypingSettings.commaSpace(activity, value as Boolean)
            "autocorrect" -> TypingSettings.autocorrect(activity, value as Boolean)
            // Only the switches that came, as for haptics.
            "fixToggles" -> (value as JSONObject).let { toggles ->
                for (id in toggles.keys()) FixKind.of(id)?.let { TypingSettings.fixOn(activity, it.id, toggles.getBoolean(id)) }
            }
            "fixOnlyWhenSure" -> TypingSettings.fixOnlyWhenSure(activity, value as Boolean)
            "suggestionBar" -> TypingSettings.suggestionBar(activity, value as Boolean)
            "launcherHidesBar" -> TypingSettings.launcherHidesBar(activity, value as Boolean)
            "cursorControl" -> TypingSettings.cursorControl(activity, value as Boolean)
            "swipeTyping" -> TypingSettings.swipeTyping(activity, value as Boolean)
            "touchpadOnlyInFields" -> TypingSettings.touchpadOnlyInFields(activity, value as Boolean)
            "orangeAccents" -> {
                TypingSettings.orangeAccents(activity, value as Boolean)
                // Everything follows at once: Settings now, and every other window as it next draws.
                Accent.on = value
                activity.applyAccent()
            }
            // A pick sticks, even one that matches the phone's text size. A keyboard that is up makes its bar again at once
            // (TitanKeysIme.followBarSize); one that isn't takes it the next time it shows.
            "barTextSize" -> FixedScale.size(activity, FixedScale.Size.of(value as String))
            "enterKey" -> TypingSettings.enterKey(activity, value as String)
            "snippetStart" -> TypingSettings.snippetStart(activity, value as String)
            "shortcodes" -> TypingSettings.shortcodes(activity, value as Boolean)
            "exactRealAlt" -> ExactTyping.realAlt(activity, value as Boolean)
            "expandShorthand" -> TypingSettings.expandShorthand(activity, value as Boolean)
            // Only the switches that came, as for haptics.
            "shorthandToggles" -> (value as JSONObject).let { toggles ->
                for (shorthand in toggles.keys()) TypingSettings.shorthandOn(activity, Shorthand.key(shorthand), toggles.getBoolean(shorthand))
            }
            "liftOff" -> TypingSettings.liftOffGrace(activity, value as Boolean)
            "liftOffMillis" -> TypingSettings.liftOffDurationMillis(activity, (value as Number).toInt())
            "haptics" -> Haptics.enabled(activity, value as Boolean)
            // Only the switches that came: one toggle on the page sends just its own.
            "hapticToggles" -> (value as JSONObject).let { toggles ->
                for (id in toggles.keys()) Haptics.toggle(activity, id, toggles.getBoolean(id))
            }
            "spelling" -> TypingSettings.spelling(activity, EnglishSpelling.Variant.of(value as String))
            // The order is the overlay's. Unknown ids and repeats are dropped, and English comes back when nothing is on.
            "inputMethods" -> (value as JSONArray).let { list ->
                val ids = inputMethodIndex().on(InputMethodIndex.setting(List(list.length()) { list.getString(it) })).map { it.id }
                TypingSettings.inputMethods(activity, InputMethodIndex.setting(ids))
                // Android's list has one TitanKeys entry, the input method typing: still on, or now the first that is.
                InputMethodList.enable(activity, listOf(inputMethodIndex().current(InputMethodIndex.setting(ids),
                    TypingSettings.currentInputMethod(activity)).id))
            }
            "chineseVariant" -> TypingSettings.chineseVariant(activity, (value as String).takeIf { it in CHINESE_VARIANTS }.orEmpty())
            "rawLog" -> RawLog.enabled(activity, value as Boolean)
            else -> throw IllegalArgumentException("Unknown setting: $key")
        }
    }
}

/** The Chinese variants Settings can set: Hong Kong, Taiwan and Simplified (docs/languages.md). */
private val CHINESE_VARIANTS = setOf("zh_hk", "zh_tw", "zh_hans")

private const val SEEN_NOTES = FreshStart.SEEN_NOTES
/** The report form (#97), which attaches a kept crash report by itself. */
private const val REPORT_PAGE = "/settings/report"

/** The screens the crash offer shows on: the app's pages and the playground. Not the quick ones in between, as a disclosure. */
internal object CrashOfferHosts {
    fun shows(host: Activity) = host is MainActivity || host is com.titankeys.keyboard.GuideActivity
}

/** The other-phone warning has shown once (#75). */
private const val PHONE_WARNING_SEEN = "phone_warning_seen"
/** TitanKeys Debug's switch that fakes another phone (#75). */
private const val FAKE_OTHER_PHONE = "fake_other_phone"
