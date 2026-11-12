package com.atlas.keyboard

/**
 * Favourite words (owner, 2026-11-12). A word made a favourite in its menu comes first among similar words, so it wins ties in
 * suggestions, swipe candidates and autocorrect. This is the part that needs no phone: the store is [FavouriteWords].
 */
object Favourites {
    /** How far down a list of similar words a favourite still counts as tied with the first: the bar shows three. */
    const val REACH = 3

    /** [words] with the first favourite among the top [REACH] moved to the front, unless the first is a favourite already. */
    fun first(words: List<String>, favourite: (String) -> Boolean): List<String> {
        if (words.size < 2 || favourite(words[0])) return words
        val index = (1 until minOf(REACH, words.size)).firstOrNull { favourite(words[it]) } ?: return words
        return listOf(words[index]) + words.filterIndexed { other, _ -> other != index }
    }
}
