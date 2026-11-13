package com.vandam.ink

import android.app.Activity
import android.app.Application
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
import com.atlas.autocorrect.FixKind
import com.atlas.autocorrect.Shorthand
import com.atlas.engine.EnglishSpelling
import com.atlas.engine.SwipeTyping
import com.atlas.engine.TypingSettings
import com.atlas.keyboard.Accent
import com.atlas.keyboard.AppBuild
import com.atlas.keyboard.BackDisclosureActivity
import com.atlas.keyboard.BackService
import com.atlas.keyboard.EnterKey
import com.atlas.keyboard.ExactTyping
import com.atlas.keyboard.FixedScale
import com.atlas.keyboard.GuideActivity
import com.atlas.keyboard.Haptic
import com.atlas.keyboard.HapticGroup
import com.atlas.keyboard.Haptics
import com.atlas.keyboard.HapticsLab
import com.atlas.keyboard.InkPress
import com.atlas.keyboard.KeyCalibrationActivity
import com.atlas.keyboard.KeyCapture
import com.atlas.keyboard.Keys
import com.atlas.keyboard.Layout
import com.atlas.keyboard.PhoneNameSource
import com.atlas.keyboard.PhoneChecks
import com.atlas.keyboard.PhoneSetup
import com.atlas.keyboard.RawLog
import com.atlas.keyboard.FreshStart
import com.atlas.keyboard.SettingsReset
import com.atlas.keyboard.SetupPart
import com.atlas.keyboard.Snippets
import com.atlas.keyboard.Trace
import com.atlas.keyboard.TouchpadPreview
import org.json.JSONArray
import org.json.JSONObject

/** Ink's activity keeps TitanKeys' sizes at any display size or font size, like every other TitanKeys window (FixedScale). */
internal fun appBaseContext(base: Context): Context = FixedScale.wrap(base)

/**
 * Ink's screens stay at Normal: Bar text size grows only the keyboard's bar (owner, 2026-11-13: "the settings become a bit
 * unusable after making it bigger"). The patch's hook stays, for Ink's own scale, at 1.
 */
internal fun appUserScale(@Suppress("UNUSED_PARAMETER") context: Context): Float = FixedScale.NORMAL

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
    mapOf("atlas" to AtlasAdapter(activity), "connected" to ConnectedAdapter(activity))

private class AtlasAdapter(private val activity: MainActivity) : NativeAdapter, Application.ActivityLifecycleCallbacks {
    private val typing by lazy { SwipeTyping.get(activity) }
    private var stopWatching: (() -> Unit)? = null
    private val previewController = lazy { TouchpadPreview(activity) }
    private var watchingRequested = false
    private var previewRequested = false
    // Adapters can be created before the live activity's window gains focus.
    private var visible = true
    private var transferring = false
    private val keyCapture = lazy { KeyCapture(activity) { key -> activity.emit(JSONObject().put("type", "atlas-key").put("key", key).toString()) } }
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
            if (visible && activity.hasWindowFocus()) activity.emit(JSONObject().put("type", "atlas-window").toString())
            if (visible && watchingRequested && activity.hasWindowFocus()) {
                // Ink may have blurred the field while the window was away; ask the page to autofocus again.
                activity.emit(JSONObject().put("type", "atlas-focus").toString())
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

    private fun publishState() = activity.emit(JSONObject().put("type", "atlas-state").toString())

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
    }

    private fun syncPreview() {
        if (visible && watchingRequested) {
            if (stopWatching == null) stopWatching = Trace.listen { activity.emit(it.put("type", "atlas-trace").toString()) }
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
                    data.optString("previous").takeIf { it.isNotEmpty() }?.let { TypingSettings.ownShorthand(activity, Shorthand.key(it), null) }
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
                "transferSnippets" -> {
                    check(!transferring) { "A transfer is already open." }
                    val mode = data.getString("mode")
                    require(mode == "import" || mode == "export")
                    transferring = true
                    try {
                        LearningTransferActivity.start(activity, mode == "export", snippets = true) { transferred, error ->
                            transferring = false
                            if (error == null) complete(NativeResult.Success(transferred.toString()))
                            else operationResult(error, complete)
                        }
                    } catch (error: Exception) {
                        transferring = false
                        throw error
                    }
                    null
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
                "transferLearning" -> {
                    check(!transferring) { "An export or import is already open." }
                    val mode = data.getString("mode")
                    require(mode == "import" || mode == "export")
                    transferring = true
                    try {
                        LearningTransferActivity.start(activity, mode == "export") { transferred, error ->
                            transferring = false
                            if (error == null) complete(NativeResult.Success(transferred.toString()))
                            else operationResult(error, complete)
                        }
                    } catch (error: Exception) {
                        transferring = false
                        throw error
                    }
                    null
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
                "nerdSeen" -> {
                    appPreferences().edit().putBoolean(NERD_SEEN, true).apply()
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
                "playground" -> {
                    // The answer comes as a message when the playground closes. A request held open that long would time out.
                    GuideActivity.playground(activity) { action ->
                        activity.emit(JSONObject().put("type", "atlas-playground").put("action", action).toString())
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
                    else PhoneSetup.page(activity, part)?.let(activity::startActivity)
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
                else -> throw IllegalArgumentException("Unknown Atlas operation: $operation")
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
            .put("nerdSeen", appPreferences().getBoolean(NERD_SEEN, false))
            .put("phone", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("android", Build.VERSION.RELEASE)
            .put("enabled", PhoneSetup.enabled(activity))
            .put("selected", PhoneSetup.selected(activity))
            .put("overlay", PhoneSetup.overlay(activity))
            .put("scrollAssistant", PhoneSetup.scrollAssistant())
            .put("backService", BackService.enabled(activity))
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
            .put("barTextSizePicked", FixedScale.picked(activity) != null)
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
            .put("rawLogAvailable", RawLog.available(activity))
            .put("rawLog", RawLog.on(activity))
            .put("build", AppBuild.kind(activity))
            .put("rawLogBytes", RawLog.size(activity))
            .put("words", words)
            .put("blockedWords", JSONArray(typing.blockedWords().toList()))
    }

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
            // A pick sticks, even one that matches the phone's text size. The bar takes it the next time it shows.
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
            "rawLog" -> RawLog.enabled(activity, value as Boolean)
            else -> throw IllegalArgumentException("Unknown setting: $key")
        }
    }
}

private const val SEEN_NOTES = "seen_release_notes"
/** The boring guide's nerd screen has shown once. Only clearing the app's data brings it back. */
private const val NERD_SEEN = "nerd_screen_seen"
