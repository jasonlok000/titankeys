package com.titankeys.keyboard

import android.content.Context
import android.util.Log

/**
 * Finds the engine an input method names (`"engine": {"rime": "tk_yale"}` in its `im.json`). An engine lives in its own
 * package and is found by its class name, so the keyboard needs no change to take one: the class implements [InputEngine]
 * and has a constructor that takes a [Context] (docs/languages.md, "How Rime plugs in").
 */
internal object InputEngines {
    /** A new engine for [name], or null when the app has none by that name or it can't be made. */
    fun create(context: Context, name: String): InputEngine? {
        val className = EngineClasses.BY_NAME[name] ?: return null
        return runCatching {
            Class.forName(className).getConstructor(Context::class.java).newInstance(context.applicationContext) as InputEngine
        }.onFailure { Log.w("InputEngines", "No engine $name", it) }.getOrNull()
    }
}
