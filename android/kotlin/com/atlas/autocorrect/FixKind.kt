package com.atlas.autocorrect

/**
 * Each kind of fix autocorrect makes, with its own switch in Settings > What gets fixed. All are on by default, which is autocorrect
 * as it always was. [id] names the switch in the settings and in the app's pages, so it never changes.
 */
enum class FixKind(val id: String) {
    /** "teh" to "the": a slip on a word's letters, a well-known misspelling, or a badly mangled long word. */
    TYPOS("typos"),
    /** "dont" to "don't", "whats" to "what's", "todays" to "today's": the form without its apostrophe is no word anyone means. */
    APOSTROPHES("apostrophes"),
    /** "its going" to "it's going": the next-word model reads the words around a real word, see [ApostropheContext]. */
    CONTEXT_APOSTROPHES("contextApostrophes"),
    /** "don t" to "don't": a space typed for the apostrophe. */
    SPACED_APOSTROPHES("spacedApostrophes"),
    /** "i" to "I", and its contractions: "i'm" to "I'm". */
    CAPITAL_I("capitalI"),
    /** "monday" to "Monday", "iphone" to "iPhone", "mr" to "Mr": a word whose only spelling has capitals. */
    NAME_CAPITALS("nameCapitals"),
    /** "THe" to "The": Shift held a letter too long. */
    SHIFT_HELD("shiftHeld"),
    /** "yeahh" to "yeah": the last letter typed twice. */
    DOUBLE_LETTERS("doubleLetters"),
    /** "hellohow" to "hello how", "andMary" to "and Mary": a missed space. */
    MISSING_SPACES("missingSpaces"),
    /** "Sudnay" to "Sunday": a slip on a common name. */
    NAME_SLIPS("nameSlips"),
    /** "Ximwna" to "Ximena", once "Ximena" has come up twice in the conversation. */
    RECENT_WORDS("recentWords"),
    /** "get dome food" to "get some food": a second look at a word once the next one is typed. */
    SECOND_LOOK("secondLook");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}
