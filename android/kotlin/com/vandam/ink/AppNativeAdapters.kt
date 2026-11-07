package com.vandam.ink

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.database.ContentObserver
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
import com.atlas.engine.Calibration
import com.atlas.engine.EnglishSpelling
import com.atlas.engine.SwipeTyping
import com.atlas.engine.TypingSettings
import com.atlas.keyboard.AtlasIme
import com.atlas.keyboard.CalibrationSession
import com.atlas.keyboard.HapticPreset
import com.atlas.keyboard.Haptics
import com.atlas.keyboard.KeyCapture
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
    private var calibration: CalibrationSession? = null
    private var calibrationRequested = false
    private val windowFocus = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus) activity.window.decorView.post {
            if (visible && watchingRequested && activity.hasWindowFocus()) {
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
        for (setting in listOf(Settings.Secure.ENABLED_INPUT_METHODS, Settings.Secure.DEFAULT_INPUT_METHOD))
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
        if (visible && calibrationRequested) calibration?.start() else calibration?.stop()
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
        calibrationRequested = false
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
                "enable" -> {
                    activity.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    "null"
                }
                "choose" -> {
                    activity.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
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
                "calibrationBegin" -> {
                    calibration?.stop()
                    Calibration.init(activity)
                    calibration = CalibrationSession(activity) { activity.emit(it.put("type", "atlas-calibration").toString()) }
                    syncPreview()
                    "null"
                }
                "calibrationListen" -> {
                    calibrationRequested = true
                    syncPreview()
                    session().snapshot().toString()
                }
                "calibrationUnlisten" -> {
                    calibrationRequested = false
                    syncPreview()
                    "null"
                }
                "calibrationRepeatCheck" -> {
                    session().repeatCheck()
                    "null"
                }
                "calibrationRestart" -> {
                    session().restart()
                    "null"
                }
                "calibrationSave" -> {
                    session().save(data.getString("name"))
                    "null"
                }
                "deleteProfile" -> {
                    Calibration.delete(data.getString("id"))
                    "null"
                }
                else -> throw IllegalArgumentException("Unknown Atlas operation: $operation")
            }
            if (result != null) complete(NativeResult.Success(result))
        } catch (error: Exception) {
            complete(NativeResult.Failure(NativeErrorKind.UNEXPECTED, error.message ?: "Atlas request failed", false))
        }
    }

    override fun cancel(requestId: Long) = Unit

    private fun session() = checkNotNull(calibration) { "Calibration has not started" }

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
        val profiles = JSONArray()
        for (profile in Calibration.profiles()) profiles.put(JSONObject().put("id", profile.id).put("name", profile.name))
        return JSONObject()
            .put("version", activity.packageManager.getPackageInfo(activity.packageName, 0).versionName)
            .put("phone", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("android", Build.VERSION.RELEASE)
            .put("enabled", enabled)
            .put("selected", selected)
            .put("learning", typing.learningEnabled())
            .put("calibration", Calibration.selectedId())
            .put("profiles", profiles)
            .put("tapKeys", TypingSettings.tapKeys(activity))
            .put("autoCapitalise", TypingSettings.autoCapitalise(activity))
            .put("suggestionBar", TypingSettings.suggestionBar(activity))
            .put("liftOff", TypingSettings.liftOffGrace(activity))
            .put("liftOffMillis", TypingSettings.liftOffDurationMillis(activity))
            .put("haptics", Haptics.enabled(activity))
            .put("hapticPreset", Haptics.preset(activity).id)
            .put("spelling", if (TypingSettings.spelling(activity) == EnglishSpelling.Variant.AMERICAN) "american" else "british")
            .put("words", words)
            .put("blockedWords", JSONArray(typing.blockedWords().toList()))
    }

    private fun set(key: String, value: Any) {
        when (key) {
            "learning" -> typing.learningEnabled(value as Boolean)
            "calibration" -> Calibration.select(value as String)
            "tapKeys" -> TypingSettings.tapKeys(activity, value as String)
            "autoCapitalise" -> TypingSettings.autoCapitalise(activity, value as Boolean)
            "suggestionBar" -> TypingSettings.suggestionBar(activity, value as Boolean)
            "liftOff" -> TypingSettings.liftOffGrace(activity, value as Boolean)
            "liftOffMillis" -> TypingSettings.liftOffDurationMillis(activity, (value as Number).toInt())
            "haptics" -> Haptics.select(activity, Haptics.preset(activity), value as Boolean)
            "hapticPreset" -> Haptics.select(activity, HapticPreset.of(value as String), Haptics.enabled(activity))
            "spelling" -> TypingSettings.spelling(activity,
                if (value == "american") EnglishSpelling.Variant.AMERICAN else EnglishSpelling.Variant.BRITISH)
            else -> throw IllegalArgumentException("Unknown setting: $key")
        }
    }
}
