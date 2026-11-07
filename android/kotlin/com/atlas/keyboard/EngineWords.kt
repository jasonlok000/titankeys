package com.atlas.keyboard

import com.atlas.engine.Swipe
import com.atlas.engine.SwipeTyping
import java.util.concurrent.atomic.AtomicBoolean

/** [Words] backed by the FUTO swipe engine and its learned habits. */
class EngineWords(private val typing: SwipeTyping) : Words {
    /** What the engine needs to learn from a recognised swipe later. */
    private class Learning(val prediction: SwipeTyping.Prediction, val generation: Long)

    override fun recognise(swipe: Swipe, context: String, personalise: Boolean, result: (Words.Recognition) -> Unit) {
        val generation = typing.learningGeneration()
        typing.recognise(swipe, context, personalise, object : SwipeTyping.Callback {
            override fun result(words: Array<String>, elapsed: Long, error: String?) =
                result(Words.Recognition(words.toList(), null))

            override fun result(words: Array<String>, elapsed: Long, error: String?, prediction: SwipeTyping.Prediction) =
                result(Words.Recognition(words.toList(), if (personalise) Learning(prediction, generation) else null))
        })
    }

    override fun complete(before: String, cancelled: AtomicBoolean, result: (List<String>) -> Unit) =
        typing.complete(before, cancelled) { words, _, _ -> if (!cancelled.get()) result(words.toList()) }

    override fun typed(word: String, rememberCase: Boolean) = typing.typed(word, typing.learningGeneration(), rememberCase)

    override fun chosen(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) {
        val learning = recognition.token as? Learning ?: return
        typing.confirm(swipe, context, word, learning.generation, learning.prediction, true)
    }

    override fun corrected(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) {
        val learning = recognition.token as? Learning ?: return
        typing.confirm(swipe, context, word, learning.generation, learning.prediction, false)
    }

    override fun kept(swipe: Swipe, context: String, recognition: Words.Recognition, word: String) {
        val learning = recognition.token as? Learning ?: return
        typing.retained(swipe, context, word, learning.generation, learning.prediction)
    }
}
