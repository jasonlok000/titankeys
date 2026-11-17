package com.titankeys.keyboard

import android.content.Context

/** Korean's engine, made by [InputEngines] by name ("hangul"): [HangulComposer], with Korean's word list for its choices. */
class HangulEngine(context: Context) : HangulComposer() {
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

/**
 * Hindi's engine ("rules"): [RulesComposer], with the rule file the input method names (`"engine": {"rules": "rules.json"}`,
 * in its own folder) and its word list for the choices.
 */
class RulesEngine(context: Context) : RulesComposer() {
    private val context = context.applicationContext

    override fun start(method: InputMethod, dataDir: java.io.File): Boolean {
        val file = method.engineArg ?: return false
        rules = runCatching {
            context.assets.open("im/${method.id}/$file").bufferedReader().use { Rules.parse(it.readText()) }
        }.getOrNull() ?: return false
        words = InputMethodList.words(context, method, null)
        return true
    }

    override fun stop() {
        super.stop()
        rules = null
        words = null
    }
}
