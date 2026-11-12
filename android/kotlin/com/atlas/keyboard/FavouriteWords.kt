package com.atlas.keyboard

import android.content.Context
import android.content.SharedPreferences

/**
 * The words made favourites in the word menu, kept in lowercase, so a favourite counts in any case. They are kept beside Blocked
 * words, and go with the words when they are exported or imported: see LearningTransferActivity. See [Favourites].
 */
class FavouriteWords private constructor(private val preferences: SharedPreferences) {
    @Volatile
    private var words: Set<String> = preferences.getStringSet(KEY, emptySet()).orEmpty().toSet()

    fun contains(word: String) = word.lowercase() in words

    fun all(): Set<String> = words

    /** Each of these returns false if the change could not be kept. */
    fun add(word: String) = change(words + word.lowercase())

    fun remove(word: String) = change(words - word.lowercase())

    /** Favourites from an imported file join the ones here. */
    fun merge(more: Collection<String>) = change(words + more.map { it.trim().lowercase() }.filter { it.isNotEmpty() })

    @Synchronized
    private fun change(next: Set<String>): Boolean {
        if (next == words) return true
        if (!preferences.edit().putStringSet(KEY, next).commit()) return false
        words = next
        return true
    }

    companion object {
        /** The name of the favourites in an exported words file. */
        const val EXPORT_KEY = "favourites"
        private const val PREFERENCES = "favourite_words"
        private const val KEY = "words"

        @Volatile
        private var instance: FavouriteWords? = null

        fun get(context: Context): FavouriteWords = instance ?: synchronized(this) {
            instance ?: FavouriteWords(context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)).also { instance = it }
        }
    }
}
