package com.titankeys.keyboard

import com.titankeys.autocorrect.KeyNeighbours

/**
 * A slip in the keys of an input method whose engine builds its words from them: Hindi by sound ([RulesComposer]) and Vietnamese
 * with Telex ([TelexComposer]). Their choices are the word list's words that start with the word being built, so one wrong key
 * (tirng for tieng) leaves nothing to pick. This tries the keys with one slip undone (a neighbouring key, a key too many, two keys
 * swapped), spells each through the engine, and offers the commonest that is a word in the list: one choice on the bar, never
 * put in by Space, which writes the word as typed (owner, 2026-11-17: conservative).
 *
 * Plain Kotlin, checked on the JVM (tests/corrections).
 */
object EngineSlips {
    /**
     * The commonest word of [list] that [keys], typed with one slip, would have spelled through [spell], or null. [spell] gives
     * the word as Space would write it. Only letters' keys are tried.
     */
    fun nearest(keys: String, list: WordList, spell: (String) -> String): String? {
        if (keys.length < 2) return null
        val tried = HashSet<String>()
        var best: String? = null
        var bestFrequency = -1
        fun consider(variant: String) {
            if (variant.isEmpty() || !tried.add(variant)) return
            val word = spell(variant)
            if (word.isEmpty() || !list.has(word)) return
            val frequency = list.frequency(word)
            if (frequency > bestFrequency) {
                best = word
                bestFrequency = frequency
            }
        }
        for (at in keys.indices) {
            val key = keys[at]
            val lower = key.lowercaseChar()
            if (lower !in 'a'..'z') continue
            // A neighbouring key in its place, in the same case.
            for (other in 'a'..'z') if (KeyNeighbours.near(lower, other))
                consider(keys.substring(0, at) + (if (key.isUpperCase()) other.uppercaseChar() else other) + keys.substring(at + 1))
            // A key too many.
            consider(keys.removeRange(at, at + 1))
            // Two keys swapped.
            if (at + 1 < keys.length) consider(keys.substring(0, at) + keys[at + 1] + keys[at] + keys.substring(at + 2))
        }
        // A key left out: one next to, or the same as, a key beside the gap, as a finger misses a key it was close to.
        for (at in 0..keys.length) {
            val beside = listOfNotNull(keys.getOrNull(at - 1), keys.getOrNull(at)).map { it.lowercaseChar() }.filter { it in 'a'..'z' }
            for (other in 'a'..'z') if (beside.any { it == other || KeyNeighbours.near(it, other) })
                consider(keys.substring(0, at) + other + keys.substring(at))
        }
        return best
    }
}
