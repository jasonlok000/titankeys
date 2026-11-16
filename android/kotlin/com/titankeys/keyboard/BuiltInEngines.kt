package com.titankeys.keyboard

import android.content.Context

/** Korean's engine, made by [InputEngines] by name ("hangul"): [HangulComposer], which needs nothing of Android. */
class HangulEngine(@Suppress("UNUSED_PARAMETER") context: Context) : HangulComposer()

/** Vietnamese's engine ("telex"): [TelexComposer], with the input method's own word list for its choices. */
class TelexEngine(context: Context) : TelexComposer() {
    private val context = context.applicationContext

    override fun start(method: InputMethod, dataDir: java.io.File): Boolean {
        words = InputMethodList.words(context, method, null)
        return true
    }

    override fun stop() {
        super.stop()
        words = null
    }
}
