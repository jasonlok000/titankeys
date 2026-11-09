package com.vandam.ink

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.atlas.engine.EnglishSpelling
import com.atlas.engine.SwipeTyping
import com.atlas.engine.TypingSettings
import com.atlas.keyboard.AtlasIme
import com.atlas.keyboard.BackService
import com.atlas.keyboard.EnterKey
import com.atlas.keyboard.GuideActivity
import com.atlas.keyboard.HapticPreset
import com.atlas.keyboard.Haptics
import com.atlas.keyboard.KeyCapture
import com.atlas.keyboard.RawLog
import com.atlas.keyboard.Trace
import com.atlas.keyboard.TouchpadPreview
import org.json.JSONArray
import org.json.JSONObject

internal fun createAppNativeAdapters(activity: MainActivity): Map<String, NativeAdapter> =
    mapOf("atlas" to AtlasAdapter(activity))

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
    private val windowFocus = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus) activity.window.decorView.post {
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
                    check(!transferring) { "A learning transfer is already open." }
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
                    check(!transferring) { "A transfer is already open." }
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
                "notesSeen" -> {
                    appPreferences().edit().putString(SEEN_NOTES, installedVersion()).apply()
                    "null"
                }
                "guide" -> {
                    GuideActivity.start(activity)
                    "null"
                }
                "enable" -> {
                    activity.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    "null"
                }
                "choose" -> {
                    activity.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                    "null"
                }
                "overlay" -> {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")))
                    "null"
                }
                "scrollAssistant" -> {
                    activity.startActivity(Intent().setClassName("com.agui.settings",
                        "com.agui.settings.touchpad.ScrollAssistantActivity"))
                    "null"
                }
                "backService" -> {
                    // The page for a single service is closed to apps, so the list opens with Atlas highlighted.
                    val service = ComponentName(activity, BackService::class.java).flattenToString()
                    activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .putExtra(":settings:fragment_args_key", service)
                        .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", service) }))
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
                else -> throw IllegalArgumentException("Unknown Atlas operation: $operation")
            }
            if (result != null) complete(NativeResult.Success(result))
        } catch (error: Exception) {
            complete(NativeResult.Failure(NativeErrorKind.UNEXPECTED, error.message ?: "TitanKeys request failed", false))
        }
    }

    override fun cancel(requestId: Long) = Unit

    private fun operationResult(error: String?, complete: NativeResultHandler) {
        complete(if (error == null) NativeResult.Success("null")
            else NativeResult.Failure(NativeErrorKind.UNEXPECTED, error, true))
    }

    private fun state(): JSONObject {
        val keyboard = ComponentName(activity, AtlasIme::class.java)
        val enabled = activity.getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.component == keyboard }
        val selected = Settings.Secure.getString(activity.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString) == keyboard
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
            .put("enabled", enabled)
            .put("selected", selected)
            .put("overlay", Settings.canDrawOverlays(activity))
            .put("scrollAssistant", scrollAssistant())
            .put("backService", BackService.enabled(activity))
            .put("learning", typing.learningEnabled())
            .put("tapKeys", TypingSettings.tapKeys(activity))
            .put("holdSymbols", TypingSettings.holdSymbols(activity))
            .put("holdSymbolMillis", TypingSettings.holdSymbolMillis(activity))
            .put("fnAsCtrl", TypingSettings.fnAsCtrl(activity))
            .put("doublePressLocks", TypingSettings.doublePressLocks(activity))
            .put("autoCapitalise", TypingSettings.autoCapitalise(activity))
            .put("autoPunctuation", TypingSettings.autoPunctuation(activity))
            .put("autocorrect", TypingSettings.autocorrect(activity))
            .put("suggestionBar", TypingSettings.suggestionBar(activity))
            .put("launcherHidesBar", TypingSettings.launcherHidesBar(activity))
            .put("cursorControl", TypingSettings.cursorControl(activity))
            .put("swipeTyping", TypingSettings.swipeTyping(activity))
            .put("touchpadOnlyInFields", TypingSettings.touchpadOnlyInFields(activity))
            .put("enterKey", TypingSettings.enterKey(activity))
            .also(::putEnterApps)
            .put("liftOff", TypingSettings.liftOffGrace(activity))
            .put("liftOffMillis", TypingSettings.liftOffDurationMillis(activity))
            .put("haptics", Haptics.enabled(activity))
            .put("hapticPreset", Haptics.preset(activity).id)
            .put("spelling", if (TypingSettings.spelling(activity) == EnglishSpelling.Variant.AMERICAN) "american" else "british")
            .put("rawLog", TypingSettings.rawLog(activity))
            .put("rawLogBytes", RawLog.size(activity))
            .put("words", words)
            .put("blockedWords", JSONArray(typing.blockedWords().toList()))
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

    private fun enterApp(app: String, name: String, method: EnterKey.SendMethod) =
        JSONObject().put("app", app).put("name", name).put("method", method.id)

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
    private fun scrollAssistant(): Boolean {
        val get = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
        if (get.invoke(null, "ro.agui.touchpad_function", "") != "yes") return true
        return get.invoke(null, "persist.sys.scroll_assistant", "0") == "1"
    }

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
            "tapKeys" -> TypingSettings.tapKeys(activity, value as String)
            "holdSymbols" -> TypingSettings.holdSymbols(activity, value as Boolean)
            "holdSymbolMillis" -> TypingSettings.holdSymbolMillis(activity, (value as Number).toInt())
            "fnAsCtrl" -> TypingSettings.fnAsCtrl(activity, value as Boolean)
            "doublePressLocks" -> TypingSettings.doublePressLocks(activity, value as Boolean)
            "autoCapitalise" -> TypingSettings.autoCapitalise(activity, value as Boolean)
            "autoPunctuation" -> TypingSettings.autoPunctuation(activity, value as Boolean)
            "autocorrect" -> TypingSettings.autocorrect(activity, value as Boolean)
            "suggestionBar" -> TypingSettings.suggestionBar(activity, value as Boolean)
            "launcherHidesBar" -> TypingSettings.launcherHidesBar(activity, value as Boolean)
            "cursorControl" -> TypingSettings.cursorControl(activity, value as Boolean)
            "swipeTyping" -> TypingSettings.swipeTyping(activity, value as Boolean)
            "touchpadOnlyInFields" -> TypingSettings.touchpadOnlyInFields(activity, value as Boolean)
            "enterKey" -> TypingSettings.enterKey(activity, value as String)
            "liftOff" -> TypingSettings.liftOffGrace(activity, value as Boolean)
            "liftOffMillis" -> TypingSettings.liftOffDurationMillis(activity, (value as Number).toInt())
            "haptics" -> Haptics.select(activity, Haptics.preset(activity), value as Boolean)
            "hapticPreset" -> Haptics.select(activity, HapticPreset.of(value as String), Haptics.enabled(activity))
            "spelling" -> TypingSettings.spelling(activity,
                if (value == "american") EnglishSpelling.Variant.AMERICAN else EnglishSpelling.Variant.BRITISH)
            "rawLog" -> RawLog.enabled(activity, value as Boolean)
            else -> throw IllegalArgumentException("Unknown setting: $key")
        }
    }
}

private const val SEEN_NOTES = "seen_release_notes"
