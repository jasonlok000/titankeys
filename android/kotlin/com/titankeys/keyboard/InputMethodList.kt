package com.titankeys.keyboard

import android.content.Context
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import com.titankeys.engine.TypingSettings
import java.util.Locale

/**
 * The input methods on the phone: the index and each one's files from `android/assets/im`, and their entries in Android's
 * keyboard list (docs/languages.md, "How an input method declares itself"). The keyboard and Settings both use it.
 */
internal object InputMethodList {
    private const val TAG = "InputMethods"
    private const val FOLDER = "im"
    /** What each keyboard-list entry carries, to say which input method it is. */
    private const val EXTRA = "im="

    @Volatile private var index: InputMethodIndex? = null

    /** Every input method the app has, English first. An app without the folder has English alone. */
    fun index(context: Context): InputMethodIndex = index ?: runCatching {
        context.assets.open("$FOLDER/index.json").bufferedReader().use { InputMethodIndex.parse(it.readText()) }
    }.getOrElse {
        Log.w(TAG, "No input method index", it)
        InputMethodIndex(emptyList())
    }.also { index = it }

    /** An input method's file, or English. Null when its file is missing or wrong, which the bar then says. */
    fun load(context: Context, id: String): InputMethod? {
        if (id == InputMethod.ENGLISH_ID) return InputMethod.ENGLISH
        return runCatching {
            context.assets.open("$FOLDER/$id/im.json").bufferedReader().use { InputMethod.parse(it.readText()) }
        }.onFailure { Log.w(TAG, "Input method $id didn't load", it) }.getOrNull()
    }

    /** [method]'s next-word table (pairs.bin, or pairs-<variant>.bin), or null where it has none. Off the main thread. */
    fun nextWords(context: Context, method: InputMethod, variant: String?): NextWords? {
        val file = if (variant != null && method.variants.any { it.id == variant }) "pairs-$variant.bin" else "pairs.bin"
        return runCatching {
            context.assets.open("$FOLDER/${method.id}/$file").use { NextWords.readBinary(it) }
        }.getOrNull()
    }

    /** [method]'s word list, for its spelling [variant] when it has variants. It can take a second: call it off the main thread. */
    fun words(context: Context, method: InputMethod, variant: String?): WordList? {
        val file = method.variants.firstOrNull { it.id == variant }?.words ?: method.words ?: return null
        return runCatching {
            context.assets.open("$FOLDER/${method.id}/$file").bufferedReader().useLines { WordList.read(it, method.javaLocale) }
        }.onFailure { Log.w(TAG, "Word list of ${method.id} didn't load", it) }.getOrNull()
    }

    /** The input method a keyboard-list entry stands for, or null for an entry that isn't one of ours. */
    fun idOf(subtype: InputMethodSubtype?): String? =
        subtype?.extraValue?.split(',')?.firstOrNull { it.startsWith(EXTRA) }?.removePrefix(EXTRA)

    /** TitanKeys' own entry for input method [id] in Android's keyboard list. */
    fun subtype(context: Context, id: String): InputMethodSubtype? {
        val info = own(context) ?: return null
        return (0 until info.subtypeCount).map(info::getSubtypeAt).firstOrNull { idOf(it) == id }
    }

    /**
     * Makes Android's list of enabled keyboard-list entries the same as Settings > Languages' [ids], so the two never disagree
     * (API 34). Anything that goes wrong only leaves Android's list as it was.
     */
    fun enable(context: Context, ids: List<String>) {
        runCatching {
            val info = own(context) ?: return
            val hashes = (0 until info.subtypeCount).map(info::getSubtypeAt).filter { idOf(it) in ids }.map { it.hashCode() }
            context.getSystemService(InputMethodManager::class.java).setExplicitlyEnabledInputMethodSubtypes(info.id, hashes.toIntArray())
            // Read back, so a list Android didn't take as given shows in the log (German was once missing).
            val taken = enabledIds(context)
            if (EnabledEntries.differ(ids, taken)) Log.w(LOG_TAG, "Android's keyboard list has $taken after setting $ids")
            else Log.i(LOG_TAG, "Android's keyboard list set to $ids")
        }.onFailure { Log.w(LOG_TAG, "Android's keyboard list didn't take the input methods $ids", it) }
    }

    /** The input methods of TitanKeys' entries that Android's keyboard list has on, or null when Android can't say. */
    fun enabledIds(context: Context): List<String>? = runCatching {
        val info = own(context) ?: return null
        context.getSystemService(InputMethodManager::class.java).getEnabledInputMethodSubtypeList(info, false).mapNotNull(::idOf)
    }.getOrNull()

    /**
     * The Rime schemas of the input methods that are on (Settings > Languages), `"engine": { "rime": "<schema>" }`: what
     * Rime's data copy keeps (RimeAssets.schemasOn; docs/studies/3.0-storage.md).
     */
    fun rimeSchemasOn(context: Context): Set<String> = index(context).on(TypingSettings.inputMethods(context))
        .filter { it.engine == com.titankeys.rime.RimeEngine.ENGINE }
        .mapNotNull { entry -> load(context, entry.id)?.engineArg }
        .toSet()

    /** TitanKeys' own id in Android's keyboard list. */
    fun ownId(context: Context): String? = own(context)?.id

    private fun own(context: Context) = context.getSystemService(InputMethodManager::class.java)?.inputMethodList
        ?.firstOrNull { it.packageName == context.packageName }

    /** The locale an input method types in, for capitals. */
    fun locale(method: InputMethod): Locale = method.javaLocale
}
