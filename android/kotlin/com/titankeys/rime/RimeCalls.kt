package com.titankeys.rime

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
    /** Picks a choice counted across every page, from 0. */
    fun selectAt(index: Int): Boolean
    /** Up to [count] choices from choice [start], across every page. */
    fun choices(start: Int, count: Int): List<String>
    fun reset()
    /** A word typed outside Rime (a pick from the bar): Rime offers its next words. True if any came. */
    fun predictAfter(text: String): Boolean = false
    /** A flick: the code still pending goes and what was picked of it stays; with none pending, the last picked word goes. */
    fun dropLast(): Boolean = false
    fun read(): Raw?
    /**
     * Rime's own backup of the learned words: merges every snapshot in the user folder's sync/ and writes its own there.
     * It closes the session, so the schema is selected again after it.
     */
    fun sync(): Boolean

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
        /** Choices a page, and how many there are in all: -1 when there are more than Rime counts (rime_jni.cc). */
        val pageSize: Int = 0,
        val total: Int = -1,
        /** The choices cover only part of the code: Rime couldn't read the rest. */
        val partial: Boolean = false,
    ) {
        /** How many pages of choices there are, or 0 when there are too many to count. */
        val pages get() = if (total > 0 && pageSize > 0) (total + pageSize - 1) / pageSize else 0

        companion object {
            /**
             * From rime_jni.cc's state(): { commit, input, preedit, int[] { page, lastPage, highlighted, caret, pageSize,
             * total, partial }, choices, comments }.
             */
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
                    pageSize = numbers[4],
                    total = numbers[5],
                    partial = numbers.size > 6 && numbers[6] != 0,
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
