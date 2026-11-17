package com.titankeys.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.titankeys.autocorrect.Autocorrect
import com.titankeys.autocorrect.KeptOnceStore
import com.titankeys.engine.SwipeTyping

/**
 * The current input method's corrections on the phone ([LanguageCorrections]), for every input method other than English that
 * has a word list. A word kept once after its fix was taken back is noted in English's kept-once store, which is backed up and
 * reset with it (such words rarely clash across languages, and they fade after a few days); kept again, it's learned for that
 * language, so it's never fixed again, as in English. The corrector reads the whole list, so it's made on its own thread as
 * the input method comes in, and goes with it.
 *
 * The learned words stay in memory here until C's LearnedWords (claude/m3-bar-words) is on main: then [learn] saves to it,
 * shown in Settings > Saved words, and the corrector asks LanguageWords' known, usable and favourite.
 */
class LanguageCorrection(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val typing = SwipeTyping.get(context)
    private var id: String? = null
    private var list: WordList? = null
    private var generation = 0

    /** The current language's autocorrect, its corrector set once made; null for English and input methods without a list. */
    var autocorrect: Autocorrect? = null
        private set

    /** The autocorrect for [method] with its [list], made the first time: the same one while the input method stays. */
    fun use(method: InputMethod, list: WordList?): Autocorrect? {
        if (list == null || !LanguageCorrections.corrects(method)) {
            clear()
            return null
        }
        if (method.id == id && list === this.list) return autocorrect
        id = method.id
        this.list = list
        val learned = learned(method.id)
        val made = Autocorrect(
            KeptOnceStore(context.getSharedPreferences(KeptOnceStore.PREFS_NAME, Context.MODE_PRIVATE)),
            { word -> learn(method.id, word) },
            { typing.learningEnabled() },
        )
        autocorrect = made
        val request = ++generation
        Thread({
            val corrector = try {
                // Blocked words and favourites are one shared set each, for every language, as on the bar.
                val favourites = FavouriteWords.get(context)
                LanguageCorrections.corrector(method, list, { learned.contains(it.lowercase()) }, made.rejected, made.recent,
                    typos = { typing.fixOn(com.titankeys.autocorrect.FixKind.TYPOS.id) },
                    usable = { !typing.blockedWord(it.lowercase()) }, favourite = favourites::contains)
            } catch (error: OutOfMemoryError) {
                Log.w("TitanKeys", "Not enough memory for ${method.id}'s corrections", error)
                null
            }
            main.post { if (request == generation) made.corrector = corrector }
        }, "corrections-${method.id}").apply {
            priority = Thread.MIN_PRIORITY
            start()
        }
        return made
    }

    /** The input method went: its corrections go with it. */
    fun clear() {
        generation++
        id = null
        list = null
        autocorrect = null
    }

    /** The words learned in language [id] while the keyboard runs, in lower case: a set the corrector reads, and [learn] adds to. */
    private val learnedWords = HashMap<String, MutableSet<String>>()

    private fun learned(id: String): MutableSet<String> =
        learnedWords.getOrPut(id) { java.util.Collections.synchronizedSet(HashSet()) }

    private fun learn(id: String, word: String) {
        learned(id) += word.lowercase()
    }
}
