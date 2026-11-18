package com.titankeys.keyboard

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import android.widget.EditText
import org.json.JSONObject
import java.lang.ref.WeakReference

/** Keeps only an awake, focused Debug test window on during a bounded phone run. */
class TestScreenLease : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isOrderedBroadcast || intent.component?.packageName != APP || intent.component?.className != javaClass.name) return
        if (context.packageName != APP || AppBuild.kind(context) != "debug") return answer(error("not-debug"))
        if (lease?.expired() == true) end()
        when (intent.getStringExtra("op")) {
            "probe" -> answer(status("available"))
            "end" -> {
                end()
                answer(status("ended"))
            }
            "begin" -> {
                val millis = duration(intent.getIntExtra("seconds", 0)) ?: return answer(error("bad-duration"))
                val existing = lease
                if (existing != null) {
                    if (!existing.renew(millis)) return answer(error("unavailable"))
                    return answer(status("leased").put("expiresElapsedMs", existing.expires))
                }
                val anchor = fixture(context) ?: return answer(error("unavailable"))
                val started = Lease(anchor.application, anchor)
                lease = started
                if (!started.start(millis)) {
                    end()
                    return answer(error("unavailable"))
                }
                answer(status("leased").put("expiresElapsedMs", started.expires))
            }
            else -> answer(error("bad-op"))
        }
    }

    private fun answer(result: JSONObject) { resultData = result.toString() }

    private class Lease(private val app: Application, fixture: Activity) : Application.ActivityLifecycleCallbacks {
        private val anchor = WeakReference(fixture)
        private val task = fixture.taskId
        private var watched: Activity? = null
        private var removeWatch: (() -> Unit)? = null
        private val flag = AddedFlag<Window>(
            { it.attributes.flags and FLAG_KEEP_SCREEN_ON != 0 },
            { it.addFlags(FLAG_KEEP_SCREEN_ON) },
            { it.clearFlags(FLAG_KEEP_SCREEN_ON) },
        )
        var expires = 0L
            private set
        private val timeout = Runnable { if (lease === this && expired()) end() }
        private val screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF && lease === this@Lease) end()
            }
        }
        private var listening = false

        fun start(millis: Long): Boolean = runCatching {
            val fixture = anchor.get() ?: return false
            app.registerActivityLifecycleCallbacks(this)
            app.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
            listening = true
            watch(fixture)
            renew(millis)
        }.getOrDefault(false)

        fun expired(): Boolean = SystemClock.elapsedRealtime() >= expires

        fun renew(millis: Long): Boolean {
            val activity = watched ?: return false
            if (!covered(activity) || !awakeAndFocused(activity)) {
                flag.show(null)
                return false
            }
            expires = SystemClock.elapsedRealtime() + millis
            main.removeCallbacks(timeout)
            main.postDelayed(timeout, millis)
            flag.show(activity.window)
            return true
        }

        private fun covered(activity: Activity): Boolean {
            val fixture = anchor.get() ?: return false
            if (!sameTask(task, fixture.taskId, !fixture.isDestroyed && !fixture.isFinishing, activity.taskId) ||
                activity.application !== app || activity.packageName != APP || activity.isFinishing || activity.isDestroyed) return false
            return when {
                activity === fixture -> true
                activity.javaClass == GuideActivity::class.java -> guideLesson(activity.window.decorView)
                else -> activity.javaClass.name == "com.vandam.ink.MainActivity"
            }
        }

        private fun watch(activity: Activity) {
            unwatch()
            val decor = activity.window.decorView
            val focus = ViewTreeObserver.OnWindowFocusChangeListener { update() }
            val detach = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit
                override fun onViewDetachedFromWindow(view: View) = unwatch()
            }
            watched = activity
            decor.viewTreeObserver.addOnWindowFocusChangeListener(focus)
            decor.addOnAttachStateChangeListener(detach)
            removeWatch = {
                decor.viewTreeObserver.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(focus)
                decor.removeOnAttachStateChangeListener(detach)
            }
        }

        private fun update() {
            if (lease !== this) return
            if (expired()) return end()
            val activity = watched
            flag.show(activity?.takeIf { covered(it) && awakeAndFocused(it) }?.window)
        }

        private fun unwatch() {
            flag.show(null)
            watched = null
            removeWatch?.invoke()
            removeWatch = null
        }

        fun close() {
            main.removeCallbacks(timeout)
            unwatch()
            app.unregisterActivityLifecycleCallbacks(this)
            if (listening) app.unregisterReceiver(screenOff)
        }

        override fun onActivityResumed(activity: Activity) {
            if (lease !== this) return
            if (expired()) return end()
            if (!covered(activity)) return unwatch()
            watch(activity)
            update()
        }

        override fun onActivityPrePaused(activity: Activity) { if (watched === activity) unwatch() }
        override fun onActivityPaused(activity: Activity) { if (watched === activity) unwatch() }
        override fun onActivityDestroyed(activity: Activity) {
            if (activity === anchor.get() && lease === this) end()
            else if (watched === activity) unwatch()
        }
        override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    }

    private class AddedFlag<W : Any>(val has: (W) -> Boolean, val add: (W) -> Unit, val clear: (W) -> Unit) {
        private var added: W? = null
        fun show(window: W?) {
            if (added === window) return
            added?.let(clear)
            added = null
            if (window != null && !has(window)) {
                add(window)
                added = window
            }
        }
    }

    companion object {
        private const val APP = "com.titankeys.debug"
        private const val IME = "$APP/com.titankeys.keyboard.TitanKeysIme"
        private const val GUARD = "test-screen-lease-v1"
        private val main = Handler(Looper.getMainLooper())
        private var lease: Lease? = null

        private fun duration(seconds: Int): Long? = seconds.takeIf { it in 1..900 }?.times(1_000L)
        private fun sameTask(expected: Int, anchor: Int, anchorAlive: Boolean, candidate: Int): Boolean =
            expected >= 0 && anchorAlive && anchor == expected && candidate == expected

        private fun end() {
            val previous = lease
            lease = null
            previous?.close()
        }

        private fun status(value: String) = JSONObject().put("ok", true).put("guard", GUARD).put("status", value)
        private fun error(value: String) = JSONObject().put("ok", false).put("guard", GUARD).put("error", value)

        private fun fixture(context: Context): Activity? = runCatching {
            if (context.getSystemService(InputMethodManager::class.java)?.currentInputMethodInfo?.id != IME) return null
            val root = WindowInspector.getGlobalWindowViews().singleOrNull { it.hasWindowFocus() && it.isAttachedToWindow } ?: return null
            val field = root.findFocus() as? EditText ?: return null
            val activity = activity(field.context) ?: return null
            if (activity.javaClass != SpaceGestureActivity::class.java || activity.packageName != APP || activity.taskId < 0 ||
                activity.window.decorView !== root || !awakeAndFocused(activity) || FeedEditor.capture(APP) == null) return null
            activity.takeUnless { it.isFinishing || it.isDestroyed }
        }.getOrNull()

        private fun awakeAndFocused(activity: Activity): Boolean = runCatching {
            val decor = activity.window.decorView
            val display = decor.display ?: return false
            decor.isAttachedToWindow && decor.isShown && decor.hasWindowFocus() && display.displayId == Display.DEFAULT_DISPLAY &&
                display.state == Display.STATE_ON && activity.getSystemService(PowerManager::class.java)?.isInteractive == true &&
                activity.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == false
        }.getOrDefault(false)

        private fun activity(context: Context): Activity? {
            var current = context
            repeat(12) {
                if (current is Activity) return current as Activity
                current = (current as? ContextWrapper)?.baseContext ?: return null
            }
            return null
        }

        private fun guideLesson(root: View): Boolean = descendants(root).filterIsInstance<EditText>()
            .singleOrNull()?.privateImeOptions == GuideHooks.FIELD

        private fun descendants(view: View): Sequence<View> = sequence {
            yield(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
    }
}
