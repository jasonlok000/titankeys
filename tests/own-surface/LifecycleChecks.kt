package com.titankeys.keyboard.lifecycle

import com.titankeys.keyboard.OwnCapturePolicy
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.UUID

private const val APP = "com.titankeys.debug"
private const val IME = "$APP/com.titankeys.keyboard.TitanKeysIme"
private const val FILE = "own-surface.png"

private class Context(val cacheDir: File, val packageName: String = APP, val kind: String = "debug")
private object AppBuild { fun kind(context: Context) = context.kind }
private data class Component(val className: String, val packageName: String)
private class Intent(val args: Map<String, Any>, val component: Component?) {
    fun getStringExtra(key: String) = args[key] as? String
    fun getIntExtra(key: String, default: Int) = args[key] as? Int ?: default
}

private object SystemClock {
    var now = 100L
    fun elapsedRealtime() = now
}
private object main {
    private val callbacks = mutableMapOf<Runnable, Long>()
    fun removeCallbacks(task: Runnable) { callbacks.remove(task) }
    fun postDelayed(task: Runnable, delay: Long) { callbacks[task] = SystemClock.now + delay }
    fun advance(delay: Long) {
        SystemClock.now += delay
        for ((task, at) in callbacks.toMap()) {
            if (at <= SystemClock.now && callbacks.remove(task) != null) task.run()
        }
    }
}

private class JSONObject {
    private val values = mutableMapOf<String, Any?>()
    fun put(key: String, value: Any?) = apply { values[key] = value }
    override fun toString() = values.entries.joinToString(";") { "${it.key}=${it.value}" }
}
private object Base64 {
    const val NO_WRAP = 1
    fun encodeToString(bytes: ByteArray, flags: Int) = java.util.Base64.getEncoder().encodeToString(bytes)
}
private class Bitmap(val width: Int, val height: Int) {
    enum class Config { ARGB_8888 }
    enum class CompressFormat { PNG }
    var recycled = false
    fun recycle() { check(!recycled); recycled = true }
    fun compress(format: CompressFormat, quality: Int, output: OutputStream): Boolean {
        check(!recycled)
        output.write(bytes)
        return true
    }
    companion object {
        val bytes = ByteArray(OwnCapturePolicy.CHUNK + 10) { it.toByte() }
        var last: Bitmap? = null
        fun createBitmap(width: Int, height: Int, config: Config) = Bitmap(width, height).also { last = it }
    }
}
private class Canvas(bitmap: Bitmap)
private open class View {
    val width = 100
    val height = 100
    fun getLocationOnScreen(at: IntArray) { at[0] = 5; at[1] = 6 }
    fun draw(canvas: Canvas) = Unit
}
private class SurfaceView : View()
private object PixelCopy {
    const val SUCCESS = 0
    var callback: ((Int) -> Unit)? = null
    fun request(view: SurfaceView, bitmap: Bitmap, done: (Int) -> Unit, handler: main) {
        check(callback == null)
        callback = done
    }
    fun complete(status: Int = SUCCESS) {
        val done = checkNotNull(callback)
        callback = null
        done(status)
    }
}
private class Lease(target: View) {
    val view = WeakReference(target)
    var owned = true
    var listener: (() -> Unit)? = null
    fun valid() = owned
    fun watch(lost: () -> Unit): () -> Unit {
        listener = lost
        return { listener = null }
    }
    fun lose() { owned = false; listener?.invoke() }
    companion object {
        var target: View? = null
        var last: Lease? = null
        fun acquire(context: Context, key: String): Lease? {
            target = if (key == "settings") SurfaceView() else View()
            return Lease(checkNotNull(target)).also { last = it }
        }
    }
}

private open class Receiver {
    var resultData = ""
    var isOrderedBroadcast = true
    var broadcast: Result? = null
    class Result {
        var resultData = ""
        var finishCount = 0
        fun finish() { check(++finishCount == 1) }
    }
    fun goAsync() = Result().also { broadcast = it }
    open fun onReceive(context: Context, intent: Intent) = Unit
}

private class CaptureReceiver : Receiver() {
    private var pending = false
    private var record: Record? = null
    // RECEIVER
}

fun lifecycle(temp: File) {
    val receiver = CaptureReceiver()
    val context = Context(temp)
    val component = Component(receiver.javaClass.name, APP)
    val file = File(temp, FILE)
    fun send(vararg args: Pair<String, Any>): Map<String, String> {
        receiver.resultData = ""
        receiver.broadcast = null
        receiver.onReceive(context, Intent(mapOf(*args), component))
        return parse(receiver.resultData)
    }
    fun capture(target: String) = send("op" to "capture", "target" to target)
    fun read(id: String, offset: Int = 0) = send("op" to "read", "id" to id, "offset" to offset)
    fun delete(id: String) = send("op" to "delete", "id" to id)
    fun result() = checkNotNull(receiver.broadcast)
    val unknownId = "0".repeat(32)

    file.writeBytes(byteArrayOf(1))
    check(capture("screen")["error"] == "bad-target" && !file.exists())
    capture("settings")
    val expiredRequest = result()
    val expiredBitmap = checkNotNull(Bitmap.last)
    main.advance(OwnCapturePolicy.TIMEOUT)
    check(expiredRequest.finishCount == 1 && parse(expiredRequest.resultData)["error"] == "timeout")
    check(!expiredBitmap.recycled && !file.exists())
    check(capture("settings")["error"] == "busy")
    PixelCopy.complete()
    check(expiredBitmap.recycled && expiredRequest.finishCount == 1 && !file.exists())

    capture("settings")
    val lostRequest = result()
    Lease.last!!.lose()
    check(parse(lostRequest.resultData)["error"] == "ownership-lost")
    PixelCopy.complete()
    check(lostRequest.finishCount == 1 && !file.exists())

    capture("settings")
    val readyRequest = result()
    PixelCopy.complete()
    val settings = parse(readyRequest.resultData)
    val id = settings.getValue("id")
    check(readyRequest.finishCount == 1 && file.exists())
    check(settings["kind"] == "pixelcopy" && settings["owner"] == APP && settings["selectedIme"] == IME)
    check(settings["guard"] == "own-surface-v1" && settings["capturedElapsedMs"] == SystemClock.now.toString())
    check(settings["expiresElapsedMs"] == (SystemClock.now + OwnCapturePolicy.TTL).toString())
    check(read(unknownId)["error"] == "wrong-id" && delete(unknownId)["error"] == "wrong-id")
    check(read(id, 1)["error"] == "bad-offset-or-expired")
    val first = read(id)
    val last = read(id, OwnCapturePolicy.CHUNK)
    check(first["eof"] == "false" && last["eof"] == "true")
    val decoder = java.util.Base64.getDecoder()
    check((decoder.decode(first["data"]) + decoder.decode(last["data"])).contentEquals(Bitmap.bytes))
    check(delete(id)["deleted"] == "true" && !file.exists())
    check(delete(id)["deleted"] == "true")

    capture("strip")
    val strip = parse(result().resultData)
    check(strip["kind"] == "view-draw" && file.exists())
    Lease.last!!.owned = false
    check(read(strip.getValue("id"))["error"] == "ownership-lost" && !file.exists())
    capture("strip")
    val expired = parse(result().resultData).getValue("id")
    main.advance(OwnCapturePolicy.TTL)
    check(!file.exists() && read(expired)["error"] == "absent")
    println("PASS receiver lifecycle, timeout, late callback, ownership revocation, chunk transfer, delete and expiry")
}

private fun parse(value: String): Map<String, String> = value.split(';').mapNotNull {
    val pair = it.split('=', limit = 2)
    if (pair.size == 2) pair[0] to pair[1] else null
}.toMap()
