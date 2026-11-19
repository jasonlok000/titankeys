package com.titankeys.keyboard

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ScrollView
import org.json.JSONObject
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.UUID

/** DUMP-protected Debug-only PNG capture/readback. Never captures the display compositor. */
class OwnSurfaceCapture : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isOrderedBroadcast || intent.component?.className != javaClass.name || intent.component?.packageName != APP) return
        if (context.packageName != APP || AppBuild.kind(context) != "debug") return answer(error("not-debug"))
        val file = File(context.cacheDir, FILE)
        // Remove previous-process orphans and expired data before interpreting a request.
        if (record == null && !pending && !removeFile(file)) return answer(error("cleanup-failed"))
        if (record?.let { SystemClock.elapsedRealtime() >= it.expiry } == true && !discard()) return answer(error("cleanup-failed"))
        when (intent.getStringExtra("op")) {
            "capture" -> capture(context, intent.getStringExtra("target"), file)
            "read" -> read(intent)
            "delete" -> {
                val id = intent.getStringExtra("id")
                if (!OwnCapturePolicy.id(id)) return answer(error("bad-id"))
                if (record != null && record?.id != id) return answer(error("wrong-id"))
                if (!discard()) return answer(error("cleanup-failed"))
                answer(JSONObject().put("ok", true).put("deleted", true))
            }
            else -> answer(error("bad-op"))
        }
    }

    private fun capture(context: Context, target: String?, file: File) {
        if (!OwnCapturePolicy.target(target)) return answer(error("bad-target"))
        if (pending || record != null) return answer(error("busy"))
        val lease = Lease.acquire(context, target!!) ?: return answer(error("unavailable"))
        val view = lease.view.get() ?: return answer(error("unavailable"))
        val at = IntArray(2).also(view::getLocationOnScreen)
        if (!OwnCapturePolicy.size(view.width, view.height) || at.any { it !in 0..4096 }) return answer(error("bounds"))
        val bitmap = try { Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888) }
            catch (_: OutOfMemoryError) { return answer(error("allocation-failed")) }
        val result = goAsync()
        pending = true
        val deadline = SystemClock.elapsedRealtime() + OwnCapturePolicy.TIMEOUT
        var finished = false
        var unwatch: (() -> Unit)? = null
        lateinit var timeout: Runnable
        fun fail(code: String) {
            if (finished) return
            finished = true
            main.removeCallbacks(timeout)
            unwatch?.let { runCatching(it) }
            removeFile(file)
            result.resultData = error(code).toString()
            result.finish()
            // PixelCopy may still own the bitmap. It retains the slot until its callback recycles it.
        }
        timeout = Runnable { fail("timeout") }
        fun complete(success: Boolean, drawStarted: Long = 0, drawFinished: Long = 0) {
            try {
                if (finished) return
                if (!success) return fail("copy-failed")
                val captured = SystemClock.elapsedRealtime()
                if (captured >= deadline) return fail("timeout")
                if (!lease.valid()) return fail("ownership-lost")
                LimitedOutput(file.outputStream()).use { stream -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
                val bytes = file.length()
                if (bytes !in 24..OwnCapturePolicy.MAX_BYTES.toLong()) return fail("capture-rejected")
                val id = UUID.randomUUID().toString().replace("-", "")
                val expiry = captured + OwnCapturePolicy.TTL
                val digest = file.inputStream().use { input ->
                    val hash = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        hash.update(buffer, 0, count)
                    }
                    hash.digest().joinToString("") { "%02x".format(it) }
                }
                if (!lease.valid()) return fail("ownership-lost")
                val now = SystemClock.elapsedRealtime()
                if (now >= deadline) return fail("timeout")
                val metadata = JSONObject().put("ok", true).put("id", id).put("target", target)
                    .put("guard", "own-surface-v1").put("owner", APP).put("selectedIme", IME)
                    .put("capturedElapsedMs", captured).put("expiresElapsedMs", expiry)
                    .put("kind", if (target == "settings") "pixelcopy" else "view-draw")
                    .put("width", bitmap.width).put("height", bitmap.height).put("x", at[0]).put("y", at[1])
                    .put("bytes", bytes).put("sha256", digest)
                if (target == "playground-keys") {
                    metadata.put("drawStartedUptimeMs", drawStarted).put("drawFinishedUptimeMs", drawFinished)
                }
                val response = metadata.toString()
                record = Record(id, file, expiry, lease, unwatch)
                main.removeCallbacks(timeout)
                main.postDelayed({ if (record?.id == id) discard() }, expiry - now)
                finished = true
                result.resultData = response
                result.finish()
            } catch (_: OutOfMemoryError) {
                fail("allocation-failed")
            } catch (_: Exception) {
                fail("write-failed")
            } finally {
                bitmap.recycle()
                pending = false
            }
        }
        val drawStarted: Long
        val drawFinished: Long
        try {
            unwatch = lease.watch {
                if (record?.lease === lease) discard() else fail("ownership-lost")
            }
            if (unwatch == null || !lease.valid()) {
                fail("ownership-lost")
                complete(false)
                return
            }
            main.postDelayed(timeout, OwnCapturePolicy.TIMEOUT)
            if (target == "settings") {
                PixelCopy.request(view as SurfaceView, bitmap, { status -> complete(status == PixelCopy.SUCCESS) }, main)
                return
            }
            val canvas = Canvas(bitmap)
            drawStarted = SystemClock.uptimeMillis()
            view.draw(canvas)
            drawFinished = SystemClock.uptimeMillis()
        } catch (_: OutOfMemoryError) { return complete(false) }
          catch (_: RuntimeException) { return complete(false) }
        complete(true, drawStarted, drawFinished)
    }

    private fun read(intent: Intent) {
        val saved = record ?: return answer(error("absent"))
        if (saved.id != intent.getStringExtra("id")) return answer(error("wrong-id"))
        val offset = intent.getIntExtra("offset", -1)
        val total = saved.file.length()
        if (total !in 24..OwnCapturePolicy.MAX_BYTES.toLong()) { discard(); return answer(error("size")) }
        val size = total.toInt()
        if (!OwnCapturePolicy.readable(offset, size, SystemClock.elapsedRealtime(), saved.expiry)) return answer(error("bad-offset-or-expired"))
        if (!saved.lease.valid()) { discard(); return answer(error("ownership-lost")) }
        try {
            val bytes = ByteArray(minOf(OwnCapturePolicy.CHUNK, size - offset))
            RandomAccessFile(saved.file, "r").use { it.seek(offset.toLong()); it.readFully(bytes) }
            if (!saved.lease.valid()) { discard(); return answer(error("ownership-lost")) }
            if (SystemClock.elapsedRealtime() >= saved.expiry) { discard(); return answer(error("expired")) }
            answer(JSONObject().put("ok", true).put("id", saved.id).put("offset", offset).put("total", size)
                .put("eof", offset + bytes.size == size).put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)))
        } catch (_: Exception) { discard(); answer(error("read-failed")) }
    }

    private fun answer(value: JSONObject) { resultData = value.toString() }

    private class Lease(val context: Context, window: View, target: View, val kind: String, val editor: FeedEditor?) {
        val root = WeakReference(window)
        val view = WeakReference(target)
        private val token = WeakReference(window.windowToken)
        private val targetRoot = WeakReference(target.rootView)
        private val targetToken = WeakReference(target.windowToken)
        private val width = target.width
        private val height = target.height
        private val location = IntArray(2).also(target::getLocationOnScreen)
        private val surface = (target as? SurfaceView)?.holder?.surface?.let { WeakReference(it) }
        private val panelAtCapture = if (kind == "panel") panelState(target) else null
        private var lost = false
        fun valid(): Boolean = runCatching {
            val window = root.get() ?: return false
            val target = view.get() ?: return false
            val ownedTargetRoot = targetRoot.get() ?: return false
            val display = window.display ?: return false
            !lost && selected(context) && window.context.packageName == APP && window.hasWindowFocus() &&
                window.isAttachedToWindow && window.windowToken === token.get() && target.context.packageName == APP &&
                target.isAttachedToWindow && target.isShown && target.width == width && target.height == height &&
                target.rootView === ownedTargetRoot && target.windowToken === targetToken.get() && target.display?.displayId == display.displayId &&
                validTarget(window, target, ownedTargetRoot) &&
                IntArray(2).also(target::getLocationOnScreen).contentEquals(location) &&
                (surface == null || (target is SurfaceView && target.holder.surface.isValid && target.holder.surface === surface.get()))
        }.getOrDefault(false)
        private fun validTarget(window: View, target: View, targetRoot: View): Boolean = when (kind) {
            "settings" -> targetRoot === window && settingsSurface(window) === target
            "strip" -> isImeWindow(targetRoot) && spaceEditor(window) != null && editor?.valid() == true
            "panel" -> isImeWindow(targetRoot) && spaceEditor(window) != null && editor?.valid() == true &&
                panelAtCapture != null && panelView(targetRoot) === target && panelState(target) == panelAtCapture
            "editor" -> targetRoot === window && spaceEditor(window) === target && editor?.valid() == true
            "guide" -> targetRoot === window && guideKeys(window, GuideHooks.FIELD) === target
            "playground-keys" -> targetRoot === window && editor?.valid() == true && guideKeys(window, GuideHooks.PLAYGROUND) === target
            else -> false
        }
        fun watch(cancel: () -> Unit): (() -> Unit)? {
            val window = root.get() ?: return null
            val target = view.get() ?: return null
            val tree = window.viewTreeObserver.takeIf { it.isAlive } ?: return null
            val lost = { this.lost = true; cancel() }
            val focus = ViewTreeObserver.OnWindowFocusChangeListener { if (!it) lost() }
            val detach = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit
                override fun onViewDetachedFromWindow(view: View) = lost()
            }
            val setting = object : ContentObserver(main) { override fun onChange(selfChange: Boolean) = lost() }
            val layout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (!valid()) lost() }
            val surface = (target as? SurfaceView)?.holder
            val surfaceChanges = object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = lost()
                override fun surfaceDestroyed(holder: SurfaceHolder) = lost()
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = lost()
            }
            val cleanups = mutableListOf<() -> Unit>()
            val unwatch = { cleanups.asReversed().forEach { runCatching(it) }; cleanups.clear() }
            return try {
                tree.addOnWindowFocusChangeListener(focus)
                cleanups.add { if (tree.isAlive) tree.removeOnWindowFocusChangeListener(focus) }
                window.addOnAttachStateChangeListener(detach)
                cleanups.add { window.removeOnAttachStateChangeListener(detach) }
                target.addOnAttachStateChangeListener(detach)
                cleanups.add { target.removeOnAttachStateChangeListener(detach) }
                target.addOnLayoutChangeListener(layout)
                cleanups.add { target.removeOnLayoutChangeListener(layout) }
                context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, setting)
                cleanups.add { context.contentResolver.unregisterContentObserver(setting) }
                if (surface != null) {
                    surface.addCallback(surfaceChanges)
                    cleanups.add { surface.removeCallback(surfaceChanges) }
                }
                if (editor != null) cleanups.add(editor.watch(lost) ?: run { unwatch(); return null })
                unwatch
            } catch (_: RuntimeException) {
                unwatch()
                null
            }
        }
        companion object {
            fun acquire(context: Context, target: String): Lease? = runCatching {
                if (!selected(context)) return null
                val roots = WindowInspector.getGlobalWindowViews().filter { it.context.packageName == APP && it.isAttachedToWindow }
                val front = roots.singleOrNull { it.hasWindowFocus() } ?: return null
                val editor = when (target) {
                    "strip", "panel", "editor", "playground-keys" -> FeedEditor.capture(APP) ?: return null
                    else -> null
                }
                val view = when (target) {
                    "settings" -> settingsSurface(front)
                    "strip" -> {
                        if (spaceEditor(front) == null) return null
                        roots.asSequence().filter(::isImeWindow).flatMap(::descendants)
                            .filterIsInstance<Strip>().singleOrNull { it.isShown }
                    }
                    "panel" -> {
                        if (spaceEditor(front) == null) return null
                        roots.asSequence().filter(::isImeWindow).flatMap(::descendants).singleOrNull { panelState(it) != null }
                    }
                    "editor" -> spaceEditor(front)
                    "guide" -> guideKeys(front, GuideHooks.FIELD)
                    "playground-keys" -> guideKeys(front, GuideHooks.PLAYGROUND)
                    else -> null
                } ?: return null
                Lease(context.applicationContext, front, view, target, editor).takeIf { it.valid() }
            }.getOrNull()
        }
    }

    companion object {
        private const val APP = "com.titankeys.debug"
        private const val IME = "$APP/com.titankeys.keyboard.TitanKeysIme"
        private const val FILE = "own-surface.png"
        private val main = Handler(Looper.getMainLooper())
        private var pending = false
        private data class Record(val id: String, val file: File, val expiry: Long, val lease: Lease, val unwatch: (() -> Unit)?)
        private var record: Record? = null
        private class LimitedOutput(output: OutputStream) : FilterOutputStream(output) {
            private var count = 0
            private fun reserve(n: Int) { if (n < 0 || count.toLong()+n > OwnCapturePolicy.MAX_BYTES) throw IOException("capture limit"); count += n }
            override fun write(value: Int) { reserve(1); out.write(value) }
            override fun write(bytes: ByteArray, offset: Int, length: Int) { reserve(length); out.write(bytes, offset, length) }
        }
        private fun removeFile(file: File): Boolean = runCatching { file.delete() || !file.exists() }.getOrDefault(false)
        private fun discard(): Boolean {
            val old = record ?: return true
            record = null
            old.unwatch?.let { runCatching(it) }
            return removeFile(old.file)
        }
        private fun error(code: String) = JSONObject().put("ok", false).put("error", code)
        private fun selected(context: Context): Boolean = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) == IME &&
                context.getSystemService(InputMethodManager::class.java)?.currentInputMethodInfo?.id == IME
        }.getOrDefault(false)
        private fun activity(context: Context): Activity? {
            var at = context
            repeat(12) { if (at is Activity) return at as Activity; at = (at as? ContextWrapper)?.baseContext ?: return null }
            return null
        }
        private fun isImeWindow(root: View): Boolean = root.context.packageName == APP && root.isAttachedToWindow &&
            (root.layoutParams as? WindowManager.LayoutParams)?.type == WindowManager.LayoutParams.TYPE_INPUT_METHOD

        private fun ownedContent(window: View, name: String): View? {
            val content = window.findViewById<ViewGroup>(android.R.id.content) ?: return null
            if (content.childCount != 1) return null
            val view = content.getChildAt(0)
            val owner = activity(view.context) ?: return null
            if (owner.javaClass.name != name || owner.window.decorView !== window || owner.packageName != APP) return null
            return view
        }

        // MainActivity installs Ink's fill-parent render surface as the first child of its sole content frame.
        // The native renderer's View subclass is renamed in the minified Debug APK.
        private fun settingsSurface(window: View): SurfaceView? {
            val frame = ownedContent(window, "com.vandam.ink.MainActivity") as? FrameLayout ?: return null
            val view = frame.getChildAt(0) as? SurfaceView ?: return null
            val layout = view.layoutParams ?: return null
            if (layout.width != ViewGroup.LayoutParams.MATCH_PARENT || layout.height != ViewGroup.LayoutParams.MATCH_PARENT ||
                view.width != frame.width || view.height != frame.height || !view.holder.surface.isValid) return null
            return view
        }

        private fun spaceEditor(window: View): EditText? {
            val content = ownedContent(window, "com.titankeys.keyboard.SpaceGestureActivity") as? ScrollView ?: return null
            val field = descendants(content).filterIsInstance<EditText>().singleOrNull() ?: return null
            if (window.findFocus() !== field || field.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0) return null
            return field
        }

        private fun guideKeys(window: View, marker: String): GuideKeyboardView? {
            val content = ownedContent(window, "com.titankeys.keyboard.GuideActivity") as? FrameLayout ?: return null
            val field = descendants(content).filterIsInstance<EditText>().singleOrNull() ?: return null
            // GuideActivity hosts both routes. Identify their editors without inspecting field text.
            if (field.privateImeOptions != marker) return null
            val keys = descendants(content).filterIsInstance<GuideKeyboardView>().singleOrNull() ?: return null
            if (marker == GuideHooks.PLAYGROUND && (window.findFocus() !== field || !fullyVisible(keys))) return null
            return keys
        }

        private fun panelView(window: View): View? = descendants(window).singleOrNull { panelState(it) != null }

        // The expanded menu takes the strip's place. Only the Switcher uses a picker notice with a Back cue.
        private fun panelState(view: View): Any? {
            val state = when (view) {
                is ChoicesMenu -> if (view.isShown && view.page.cells.isNotEmpty()) view.revision else return null
                is Strip -> (view.notice as? Strip.Notice.Picker)?.takeIf { it.back && it.items.isNotEmpty() } ?: return null
                else -> return null
            }
            return state.takeIf { fullyVisible(view) }
        }

        private fun fullyVisible(view: View): Boolean {
            val visible = Rect()
            return view.isShown && view.isAttachedToWindow && view.context.packageName == APP && view.alpha > 0f &&
                view.width > 0 && view.height > 0 && view.getLocalVisibleRect(visible) &&
                visible.left == 0 && visible.top == 0 && visible.right == view.width && visible.bottom == view.height
        }

        private fun descendants(view: View): Sequence<View> = sequence {
            yield(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
        }
    }
}
