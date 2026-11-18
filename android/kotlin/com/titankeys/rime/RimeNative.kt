package com.titankeys.rime

/**
 * The JNI layer over Rime, native/rime/rime_jni.cc, in librime_tk.so. One Rime session for the whole keyboard. Every call
 * holds the same lock in C++, so [start] can run on a background thread. [RimeEngine] uses it through [RimeCalls].
 */
object RimeNative : RimeCalls {
    @Volatile private var loaded = false

    /** Loads the library once. False if it can't load (no arm64 library on this device, say). */
    fun load(): Boolean {
        if (loaded) return true
        loaded = runCatching { System.loadLibrary("rime_tk") }.isSuccess
        return loaded
    }

    @JvmStatic private external fun start(shared: String, user: String): Boolean
    @JvmStatic private external fun stop()
    @JvmStatic private external fun selectSchema(schema: String): Boolean
    @JvmStatic private external fun setOption(name: String, on: Boolean)
    @JvmStatic private external fun getOption(name: String): Boolean
    @JvmStatic private external fun key(keycode: Int, mask: Int): Boolean
    @JvmStatic private external fun pick(index: Int): Boolean
    @JvmStatic private external fun pickAt(index: Int): Boolean
    @JvmStatic private external fun choiceList(start: Int, count: Int): Array<String>?
    @JvmStatic private external fun clear()
    @JvmStatic private external fun predictAfter0(text: String): Boolean
    @JvmStatic private external fun dropLast0(): Boolean
    @JvmStatic private external fun picked0(): String?
    @JvmStatic private external fun sync0(): Boolean
    @JvmStatic private external fun state(): Array<Any?>?

    override fun open(shared: String, user: String) = load() && start(shared, user)
    override fun close() = stop()
    override fun schema(id: String) = selectSchema(id)
    override fun option(name: String, on: Boolean) = setOption(name, on)
    override fun option(name: String) = getOption(name)
    override fun process(keycode: Int, mask: Int) = key(keycode, mask)
    override fun select(index: Int) = pick(index)
    override fun selectAt(index: Int) = pickAt(index)
    override fun choices(start: Int, count: Int): List<String> = choiceList(start, count)?.toList().orEmpty()
    override fun reset() = clear()
    override fun predictAfter(text: String) = predictAfter0(text)
    override fun dropLast() = dropLast0()
    override fun picked(): Pair<String, Int> {
        val both = picked0() ?: return "" to 0
        val tab = both.lastIndexOf('	')
        return if (tab < 0) "" to 0 else both.substring(0, tab) to (both.substring(tab + 1).toIntOrNull() ?: 0)
    }
    override fun sync() = sync0()
    override fun read(): RimeCalls.Raw? = state()?.let { RimeCalls.Raw.of(it) }
}
