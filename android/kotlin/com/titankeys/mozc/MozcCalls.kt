package com.titankeys.mozc

/** What [MozcEngine] needs from Mozc: [MozcNative] on the phone, a stand-in in the JVM checks (tests/japanese). */
interface MozcCalls {
    /** Opens Mozc on its data file, with the user's history in [profile]. */
    fun open(data: String, profile: String): Boolean
    fun close()
    /** One printable key through Mozc's romaji table. */
    fun key(char: Char): Boolean
    /** Picks the choice at [index] among [Raw.choices]: what it covers is typed, the rest stays. */
    fun pick(index: Int): Boolean
    /** Drops the text being built. */
    fun reset()
    fun read(): Raw?

    /** Mozc's state after a call: the text it let go of since the last read ([commit], once), the text being built, every choice. */
    data class Raw(val commit: String?, val preedit: String, val choices: List<String>) {
        companion object {
            /** From tk_jni.cc's state(): { commit, preedit, choices }. */
            fun of(a: Array<Any?>): Raw {
                @Suppress("UNCHECKED_CAST")
                return Raw(a[0] as String?, a[1] as String? ?: "", (a[2] as Array<String>).toList())
            }
        }
    }
}

/** The JNI layer over Mozc, native/mozc/tk/tk_jni.cc, in libmozc_tk.so. One Mozc session for the whole keyboard. */
object MozcNative : MozcCalls {
    @Volatile private var loaded = false

    fun load(): Boolean {
        if (loaded) return true
        loaded = runCatching { System.loadLibrary("mozc_tk") }.isSuccess
        return loaded
    }

    @JvmStatic private external fun start(data: String, profile: String): Boolean
    @JvmStatic private external fun stop()
    @JvmStatic private external fun key(code: Int): Boolean
    @JvmStatic private external fun pickAt(index: Int): Boolean
    @JvmStatic private external fun clear()
    @JvmStatic private external fun state(): Array<Any?>?

    override fun open(data: String, profile: String) = load() && start(data, profile)
    override fun close() = stop()
    override fun key(char: Char) = key(char.code)
    override fun pick(index: Int) = pickAt(index)
    override fun reset() = clear()
    override fun read(): MozcCalls.Raw? = state()?.let { MozcCalls.Raw.of(it) }
}
