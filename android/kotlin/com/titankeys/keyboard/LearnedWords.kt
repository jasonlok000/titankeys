package com.titankeys.keyboard

import android.content.Context
import android.content.SharedPreferences

/**
 * What each input method but English learned and saved ([LanguageLearning]), one copy for the whole app: the keyboard learns
 * into it as words are typed, and Settings' Saved words shows and removes them, so neither overwrites the other. Kept in the
 * `language_learning` store, one key per input method: on the phone only, in the backup (Back up everything), kept by Reset
 * most things and cleared by Reset literally everything, as English's learned words are.
 */
internal object LearnedWords {
    private val cache = HashMap<String, LanguageLearning>()

    private const val STORE = "language_learning"

    private fun store(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    /** Input method [id]'s learning, read once. */
    fun of(context: Context, id: String): LanguageLearning = of(store(context), id)

    @Synchronized
    internal fun of(store: SharedPreferences, id: String): LanguageLearning =
        cache.getOrPut(id) { LanguageLearning.decode(store.getString(id, null)) }

    /** Writes input method [id]'s learning. */
    fun save(context: Context, id: String) = save(store(context), id)

    @Synchronized
    internal fun save(store: SharedPreferences, id: String) {
        val learning = cache[id] ?: return
        store.edit().putString(id, learning.encode()).apply()
    }

    /** Every input method that learned or saved a word, with its words, for Settings. */
    @Synchronized
    fun all(context: Context): Map<String, LanguageLearning> =
        (store(context).all.keys + cache.keys).associateWith { of(context, it) }.filterValues { it.ownWords().isNotEmpty() }

    /** Settings' remove: input method [id] forgets [word], as the word menu's Unsave does to a word it learned or saved. */
    @Synchronized
    fun remove(context: Context, id: String, word: String) {
        of(context, id).unsave(word, listed = false)
        save(context, id)
    }

    /** Remove learned words: what every input method learned from typing goes; the words saved from the menu stay. */
    fun forgetLearned(context: Context) = forgetLearned(store(context))

    @Synchronized
    internal fun forgetLearned(store: SharedPreferences) {
        // The keyboard writes every few words, so what it learned since is only in [cache]: that goes too.
        for (id in store.all.keys + cache.keys) {
            of(store, id).forgetLearned()
            save(store, id)
        }
    }

    /** Remove all learned words: every input method forgets every word it learned or saved, as English does. */
    @Synchronized
    fun forgetAll(context: Context) {
        // Emptied in place: the keyboard holds the same copies, and would write them back.
        for (learning in cache.values) learning.forgetAll()
        store(context).edit().clear().apply()
    }

    /**
     * [word] is input method [id]'s from now on, as the word menu's Save makes it: D's fixes call this when a word is kept
     * after its fix was taken back, English's rule. Known at once, never fades, shown in Saved words.
     */
    @Synchronized
    fun learn(context: Context, id: String, word: String) {
        of(context, id).save(word)
        save(context, id)
    }

    /** Reset literally everything cleared the store itself: the copies go too. */
    @Synchronized
    fun clear() = cache.clear()
}
