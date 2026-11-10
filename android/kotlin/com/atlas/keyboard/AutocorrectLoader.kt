package com.atlas.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.atlas.autocorrect.Autocorrect
import com.atlas.autocorrect.CorrectionData
import com.atlas.autocorrect.KeptOnceStore
import com.atlas.autocorrect.Lexicon
import com.atlas.autocorrect.TypedCorrector
import com.atlas.engine.SwipeTyping
import com.atlas.engine.TypedPredictions
import java.io.IOException
import java.io.InputStreamReader

/**
 * Keeps autocorrect's word data in memory only while the user types. About 470,000 word pairs share the keyboard
 * process with the swipe models, so they load on their own thread at the first typed key and go after a pause.
 */
class AutocorrectLoader(private val context: Context, private val typing: SwipeTyping) {
    private val main = Handler(Looper.getMainLooper())
    private var loading = false
    private var failed = false
    private var generation = 0
    private val forget = Runnable {
        generation++
        autocorrect.corrector = null
    }

    val autocorrect = Autocorrect(
        KeptOnceStore(context.getSharedPreferences(KeptOnceStore.PREFS_NAME, Context.MODE_PRIVATE)),
        { word -> typing.addWord(word) { error -> if (error != null) Log.w("Atlas", "Could not save a kept word: $error") } },
        { typing.learningEnabled() },
        ::using,
    )

    private fun using() {
        main.removeCallbacks(forget)
        main.postDelayed(forget, IDLE_MILLIS)
        if (loading || failed || autocorrect.corrector != null) return
        val words = typing.typedPredictions() ?: return
        loading = true
        val request = ++generation
        Thread({ load(words, request) }, "autocorrect-data").apply {
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    private fun load(words: TypedPredictions, request: Int) {
        val lexicon = PhoneLexicon(typing, words)
        val data = try {
            CorrectionData.read(lexicon) { name -> InputStreamReader(context.assets.open("autocorrect/$name"), Charsets.UTF_8) }
        } catch (error: IOException) {
            Log.w("Atlas", "Could not read the autocorrect data", error)
            null
        } catch (error: RuntimeException) {
            Log.w("Atlas", "Could not read the autocorrect data", error)
            null
        } catch (error: OutOfMemoryError) {
            Log.w("Atlas", "Not enough memory for the autocorrect data", error)
            null
        }
        main.post {
            loading = false
            if (data == null) failed = true
            else if (request == generation) autocorrect.corrector = TypedCorrector(lexicon, data, autocorrect.rejected, autocorrect.recent,
                nextWordChance = typing::nextWordChance)
        }
    }

    /** Frees the word data straight away, as when the setting is turned off. */
    fun release() {
        main.removeCallbacks(forget)
        forget.run()
    }

    private class PhoneLexicon(private val typing: SwipeTyping, private val words: TypedPredictions) : Lexicon {
        override fun known(word: String) = words.contains(word) || typing.personalWord(word)
        override fun frequency(word: String) = words.frequencyOf(word)
        override fun personal(word: String) = typing.personalWord(word)
        override fun name(word: String): String? = typing.properName(word)
        override fun preferredSpelling(word: String): String = typing.preferredSpelling(word)
        override fun forEachWord(action: (String, Int) -> Unit) = words.forEachWord { word, frequency -> action(word, frequency) }
    }

    private companion object {
        const val IDLE_MILLIS = 300_000L
    }
}
