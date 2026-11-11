package com.atlas.keyboard

import com.atlas.connected.Contributions
import com.atlas.engine.Swipe
import com.atlas.engine.SwipeTyping
import java.util.concurrent.atomic.AtomicBoolean

/** [Words] backed by the FUTO swipe engine and its learned habits. */
class EngineWords(private val typing: SwipeTyping) : Words {
    override fun allowed(word: String) = typing.wordAllowed(word)
    override fun fixAllowed(typed: String, fix: String) = typing.fixAllowed(typed, fix)
    override fun known(word: String) = typing.knownWord(word)
    override fun sentenceStarts(): List<String> = typing.sentenceStarts()

    /** What the engine needs to learn from a recognised swipe later. */
    private class Learning(val prediction: SwipeTyping.Prediction, val generation: Long)

    override fun recognise(swipe: Swipe, context: String, personalise: Boolean, result: (Words.Recognition) -> Unit) {
        val generation = typing.learningGeneration()
        typing.recognise(swipe, context, personalise, object : SwipeTyping.Callback {
            override fun result(words: Array<String>, elapsed: Long, error: String?) =
                result(Words.Recognition(words.toList(), null))

            override fun result(words: Array<String>, elapsed: Long, error: String?, prediction: SwipeTyping.Prediction) =
                result(Words.Recognition(words.toList(), if (personalise) Learning(prediction, generation) else null, prediction))
        })
    }

    override fun complete(before: String, cancelled: AtomicBoolean, sentenceCapitalisation: Boolean, result: (List<String>) -> Unit) =
        typing.complete(before, cancelled, sentenceCapitalisation) { words, _, _ -> if (!cancelled.get()) result(words.toList()) }

    override fun prepare(text: String, cancelled: AtomicBoolean) = typing.prepare(text, cancelled)

    override fun lookAlike(context: String, word: String, next: String, result: (String?) -> Unit) =
        typing.lookAlike(context, word, next) { result(it) }

    override fun typed(word: String, rememberCase: Boolean) = typing.typed(word, typing.learningGeneration(), rememberCase)
    override fun untyped(word: String) = typing.untyped(word, typing.learningGeneration())

    override fun keptPair(previous: String?, next: String) = typing.keptPair(previous, next)

    override fun chosen(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) {
        val learning = recognition.token as? Learning ?: return
        typing.confirm(swipe, context, word, learning.generation, learning.prediction, true)
        Contributions.record(typing, swipe, word, "chosen", learning.prediction)
    }

    override fun corrected(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) {
        val learning = recognition.token as? Learning ?: return
        typing.confirm(swipe, context, word, learning.generation, learning.prediction, false)
        Contributions.record(typing, swipe, word, "corrected", learning.prediction)
    }

    override fun kept(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) {
        val learning = recognition.token as? Learning ?: return
        typing.retained(swipe, context, word, learning.generation, learning.prediction)
        Contributions.record(typing, swipe, word, "kept", learning.prediction)
    }
}
