package com.titankeys.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.titankeys.autocorrect.Autocorrect
import com.titankeys.autocorrect.KeptOnceStore
import com.titankeys.engine.SwipeTyping
import com.titankeys.engine.TypingSettings

/**
 * The current input method's corrections on the phone ([LanguageCorrections]), for every input method other than English that
 * has a word list. A word kept once after its fix was taken back is noted in English's kept-once store, which is backed up and
 * reset with it (such words rarely clash across languages, and they fade after a few days); kept again, it's saved for that
 * language ([LearnedWords], as the word menu's Save), so it's never fixed again, as in English. The corrector asks [words] what
 * the language knows (its list, starters and the user's own words), what a fix may put in (not a blocked word, nor one unsaved
 * in it) and what's a favourite. It reads the whole list, so it's made on its own thread as the input method comes in.
 */
class LanguageCorrection(private val context: Context, private val words: LanguageWords) {
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
        val made = Autocorrect(
            KeptOnceStore(context.getSharedPreferences(KeptOnceStore.PREFS_NAME, Context.MODE_PRIVATE),
                key = KeptOnceStore.languageKey(method.id)),
            { word -> LearnedWords.learn(context, method.id, word) },
            { typing.learningEnabled() },
        )
        autocorrect = made
        val request = ++generation
        Thread({
            val corrector = try {
                LanguageCorrections.corrector(method, list, words::known, made.rejected, made.recent,
                    typos = { typing.fixOn(com.titankeys.autocorrect.FixKind.TYPOS.id) }, usable = words::usable,
                    favourite = words::favourite, spaceFixes = method.id !in barOnly, pairs = pairs(method))
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

    /** The input methods whose fixes Space never puts in, only the bar shows ([LanguageCorrections.barOnly]). */
    private val barOnly: Set<String> by lazy {
        runCatching {
            context.assets.open("im/${LanguageCorrections.BAR_ONLY_FILE}").bufferedReader().useLines(LanguageCorrections::barOnly)
        }.getOrDefault(emptySet())
    }

    /**
     * Whether the input method typing corrects at all: its [language]'s own Fix typos switch, on its page in Settings > Languages,
     * or Settings' single one until it has its own (TypingSettings.PER_LANGUAGE). The one place to read it.
     */
    fun on(language: String): Boolean = TypingSettings.autocorrectIn(context, language)

    /** The input methods whose pairs added wrong changes, so their corrector goes without ([LanguageCorrections.NO_PAIRS_FILE]). */
    private val noPairs: Set<String> by lazy {
        runCatching {
            context.assets.open("im/${LanguageCorrections.NO_PAIRS_FILE}").bufferedReader().useLines(LanguageCorrections::barOnly)
        }.getOrDefault(emptySet())
    }

    /** [method]'s word pairs, its next-word table, for the corrector's context; null where it has none, or they don't help. */
    private fun pairs(method: InputMethod): com.titankeys.autocorrect.PairCounts? = if (method.id in noPairs) null else runCatching {
        context.assets.open("im/${method.id}/pairs.txt").bufferedReader().useLines { com.titankeys.autocorrect.PairCounts.parse(it) }
    }.getOrNull()

    /** The input method went: its corrections go with it. */
    fun clear() {
        generation++
        id = null
        list = null
        autocorrect = null
    }
}
