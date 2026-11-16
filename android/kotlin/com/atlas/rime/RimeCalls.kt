package com.atlas.rime

/** What [RimeEngine] needs from Rime: [RimeNative] on the phone, a stand-in in the JVM checks (tests/rime). */
interface RimeCalls {
    fun open(shared: String, user: String): Boolean
    fun close()
    fun schema(id: String): Boolean
    fun option(name: String, on: Boolean)
    fun option(name: String): Boolean
    /** One key, as an X11 keysym with Rime's modifier [mask] ([RimeKeys]). True if Rime took it. */
    fun process(keycode: Int, mask: Int): Boolean
    /** Picks a choice on the current page, from 0. */
    fun select(index: Int): Boolean
    fun reset()
    fun read(): Raw?

    /**
     * Rime's state after a call: the text it let go of since the last read ([commit], once), the letters typed so far
     * ([input]), how it shows them ([preedit], with tones or Yale spelling), and the current page of choices.
     */
    data class Raw(
        val commit: String?,
        val input: String,
        val preedit: String?,
        val page: Int,
        val lastPage: Boolean,
        val highlighted: Int,
        val choices: List<String>,
        val comments: List<String>,
    ) {
        companion object {
            /** From rime_jni.cc's state(): { commit, input, preedit, int[] { page, lastPage, highlighted, caret }, choices, comments }. */
            fun of(a: Array<Any?>): Raw {
                val numbers = a[3] as IntArray
                @Suppress("UNCHECKED_CAST")
                return Raw(
                    commit = a[0] as String?,
                    input = a[1] as String? ?: "",
                    preedit = a[2] as String?,
                    page = numbers[0],
                    lastPage = numbers[1] != 0,
                    highlighted = numbers[2],
                    choices = (a[4] as Array<String>).toList(),
                    comments = (a[5] as Array<String>).toList(),
                )
            }
        }
    }
}

/** Rime's key codes (X11 keysyms) and modifier masks, for the keys the keyboard sends it. */
object RimeKeys {
    const val SPACE = 0x20
    const val BACKSPACE = 0xff08
    const val RETURN = 0xff0d
    const val ESCAPE = 0xff1b
    const val PAGE_UP = 0xff55
    const val PAGE_DOWN = 0xff56
    const val SHIFT_MASK = 1
}
