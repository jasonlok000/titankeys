package com.titankeys.keyboard

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import java.lang.ref.WeakReference

/** A feed is bound to one focused editor in this process. It only weakly holds the editor, window and input hook. */
internal class FeedEditor private constructor(window: View, editor: View, private val packageName: String) {
    private val windowRef = WeakReference(window)
    private val editorRef = WeakReference(editor)
    private val windowToken = WeakReference(window.windowToken)
    private val observer = WeakReference(window.viewTreeObserver)

    fun valid(): Boolean {
        val window = windowRef.get() ?: return false
        val editor = editorRef.get() ?: return false
        val token = windowToken.get() ?: return false
        return window.windowToken === token && editor.windowToken === token &&
            window.viewTreeObserver === observer.get() && window.findFocus() === editor && owns(window, editor, packageName)
    }

    fun watch(lost: () -> Unit): (() -> Unit)? {
        val editor = editorRef.get() ?: return null
        val tree = observer.get()?.takeIf { it.isAlive } ?: return null
        val focus = ViewTreeObserver.OnGlobalFocusChangeListener { _, next ->
            if (next !== editorRef.get()) lost()
        }
        val window = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (!focused) lost() }
        val attachment = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) = lost()
        }
        tree.addOnGlobalFocusChangeListener(focus)
        tree.addOnWindowFocusChangeListener(window)
        editor.addOnAttachStateChangeListener(attachment)
        return {
            observer.get()?.takeIf { it.isAlive }?.let {
                it.removeOnGlobalFocusChangeListener(focus)
                it.removeOnWindowFocusChangeListener(window)
            }
            editorRef.get()?.removeOnAttachStateChangeListener(attachment)
        }
    }

    companion object {
        fun capture(packageName: String): FeedEditor? = runCatching {
            if (!packageName.endsWith(".debug")) return null
            WindowInspector.getGlobalWindowViews().mapNotNull { window ->
                val editor = window.findFocus() ?: return@mapNotNull null
                if (owns(window, editor, packageName)) FeedEditor(window, editor, packageName) else null
            }.singleOrNull()
        }.getOrNull()

        private fun owns(window: View, editor: View, packageName: String): Boolean {
            if (window.context.packageName != packageName || editor.context.packageName != packageName ||
                !window.isAttachedToWindow || !editor.isAttachedToWindow || editor.rootView !== window ||
                !window.hasWindowFocus() || !editor.isFocused || !editor.isShown || !editor.onCheckIsTextEditor()) return false
            val ime = editor.context.getSystemService(InputMethodManager::class.java) ?: return false
            return ime.currentInputMethodInfo?.packageName == packageName && ime.isActive(editor) && ime.isAcceptingText
        }
    }
}

/** Records even a transient focus loss between events, and checks the same window, editor and hook at dispatch time. */
internal class FeedBurst<Dispatch : Any>(
    private val name: String,
    private val editor: FeedEditor,
    dispatch: Dispatch,
    currentDispatch: () -> Dispatch?,
    private val clearHeldState: () -> Unit,
) {
    private val lease = FeedFocusLease(dispatch, currentDispatch, editor::valid)
    private val main = Handler(Looper.getMainLooper())
    private var unwatch: (() -> Unit)? = null

    fun begin(): Boolean {
        unwatch = editor.watch { finish(cancelled = true) }
        if (unwatch != null && lease.checkedDispatch() != null) return true
        finish(cancelled = true)
        return false
    }

    fun post(time: Long, last: Boolean, run: (Dispatch) -> Unit) {
        if (lease.closed) return
        if (!main.postAtTime({
                if (lease.closed) return@postAtTime
                val dispatch = lease.checkedDispatch()
                if (dispatch == null) {
                    finish(cancelled = true)
                    return@postAtTime
                }
                try {
                    run(dispatch)
                    if (last) finish(cancelled = false)
                } catch (error: RuntimeException) {
                    Log.w(TAG, "$name feed: event failed", error)
                    finish(cancelled = true)
                }
            }, this, time)
        ) finish(cancelled = true)
    }

    private fun finish(cancelled: Boolean) {
        if (!lease.close()) return
        main.removeCallbacksAndMessages(this)
        unwatch?.let { remove ->
            runCatching(remove).onFailure { Log.w(TAG, "$name feed: focus listener cleanup failed", it) }
        }
        unwatch = null
        if (!cancelled) return
        Log.w(TAG, "$name feed: focus-guard-v1 cancelled")
        // No key-up or lift goes through dispatch after loss. Clear only state owned by this burst in its original IME.
        if (lease.sameDispatch() != null) {
            runCatching(clearHeldState).onFailure { Log.w(TAG, "$name feed: cleanup failed", it) }
        }
    }

    companion object {
        const val VERIFY_ONLY = "verify_focus_guard"
        const val READY = "focus-guard-v1 ready"

        fun probeAtUptime(ready: Boolean): String {
            val status = if (ready) READY else "focus-guard-v1 available"
            return "$status; uptime=${SystemClock.uptimeMillis()}"
        }

        private const val TAG = "TitanKeysDebug"

        fun startTime(intent: Intent, maxDelay: Long): Long? {
            val now = SystemClock.uptimeMillis()
            if (intent.hasExtra("start_uptime"))
                return intent.getLongExtra("start_uptime", -1).takeIf { it in now..now + maxDelay }
            return now + intent.getLongExtra("delay", 0).coerceIn(0L, maxDelay)
        }
    }
}
