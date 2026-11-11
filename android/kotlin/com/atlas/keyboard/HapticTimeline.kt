package com.atlas.keyboard

/** The motor on from [fromMillis] to [toMillis]. [dropped] is a play the gate didn't let through, so the motor stayed off. */
data class Buzz(val fromMillis: Long, val toMillis: Long, val dropped: Boolean = false)

/**
 * The debug Haptics lab's timeline: when the motor is on for each pattern, and the words for what set each haptic off. It has no
 * Android parts, so the checks can run it.
 */
object HapticTimeline {
    /** A pattern's bars run at least this long, so short ones are drawn to the same scale. No ends at 155 ms, so it fits. */
    const val SPAN_MILLIS = 200L
    const val GRID_MILLIS = 20L

    /** What each family means, in the lab's words. */
    fun meaning(family: Family) = when (family) {
        Family.Texture -> "One light tick. Swipe keys, cursor letters, words in a hold, marks."
        Family.Boundary -> "One firmer tick. Spaces, a word grabbed, a new page or group."
        Family.Confirm -> "Short then long. Done: a word picked, a setting switched, pasted."
        Family.Change -> "Two quick ones. Something changed a word for you."
        Family.Lock -> "Two equal ones. It stays: selecting, Shift lock, a pin."
        Family.Release -> "One pulse. It doesn't stay any more."
        Family.GoneFlick -> "Long then short. The tail lands as the dots pop."
        Family.Gone -> "One long pulse. A word forgotten, the clipboard cleared."
        Family.Back -> "Long then short. Undone, back as it was."
        Family.No -> "Three hard ones. Nothing happened."
        Family.Celebrate -> "A rising three. The guide's celebrations."
    }

    /** What set off [haptic], in a few plain words, for the lab's live timeline. */
    fun trigger(haptic: Haptic) = when (haptic) {
        Haptic.SwipeStart -> "A swipe started"
        Haptic.SwipeKey -> "The swipe crossed a key"
        Haptic.TapKey -> "A light touch typed a key"
        Haptic.DeleteFlick -> "A flick deleted a word"
        Haptic.DeleteDotsLand -> "A delete's dots landed"
        Haptic.DeleteWord -> "The hold deleted a word"
        Haptic.DeleteNothing -> "Nothing left to delete"
        Haptic.SpaceTap -> "A Space tap counted"
        Haptic.Steer -> "Steering started"
        Haptic.SteerResumed -> "Steering picked up after a slip"
        Haptic.Select -> "Selecting started"
        Haptic.WordPicked -> "Taps picked a word"
        Haptic.NothingToPick -> "Taps found no word"
        Haptic.CursorLetter -> "The cursor passed a letter"
        Haptic.CursorSpace -> "The cursor passed a space"
        Haptic.SelectionWord -> "The selection moved a word"
        Haptic.CursorEnd -> "The cursor hit an end"
        Haptic.Autocorrect -> "Space fixed a word"
        Haptic.AutocorrectUndone -> "Backspace undid a change"
        Haptic.HeldSymbol -> "A held letter became its symbol"
        Haptic.Expansion -> "An emoji or snippet came in"
        Haptic.BarWord -> "You tapped a word on the bar"
        Haptic.MenuOpen -> "The word menu opened"
        Haptic.MenuMark -> "The menu mark moved"
        Haptic.WordSaved -> "You saved a word"
        Haptic.WordForgotten -> "You forgot a word"
        Haptic.ForgetUndone -> "You undid Forget"
        Haptic.WordNotSaved -> "The word couldn't be saved"
        Haptic.ClipOffer -> "You pasted the offered clip"
        Haptic.ExactKey -> "An exact typing key"
        Haptic.ExactLock -> "An exact typing key locked"
        Haptic.ExactChip -> "An exact typing chip switched"
        Haptic.ExactUnlock -> "An exact typing chip unlocked"
        Haptic.ModifierLocked -> "Shift or Alt locked"
        Haptic.ModifierUnlocked -> "Shift or Alt unlocked"
        Haptic.PanelMark -> "The panel mark moved"
        Haptic.PanelPage -> "A new page"
        Haptic.PanelGroup -> "A new group"
        Haptic.QuickSetting -> "A quick setting switched"
        Haptic.ClipPasted -> "You pasted a clip"
        Haptic.ClipPinned -> "You pinned a clip"
        Haptic.ClipUnpinned -> "You unpinned a clip"
        Haptic.ClipboardCleared -> "You cleared the clipboard"
        Haptic.BugRecording -> "Record a bug started or stopped"
        Haptic.GuideCelebration -> "The guide celebrated"
    }

    /** The live timeline shows the last 5 s, and the list under it the last 15 haptics. */
    const val LIVE_MILLIS = 5000L
    const val RECENT = 15

    /** How long a row of bars runs: [SPAN_MILLIS], or longer in steps of 50 ms when a tried pattern is longer. */
    fun span(longest: Long) = maxOf(SPAN_MILLIS, (longest + 49) / 50 * 50)

    /** A gridline every 20 ms, or fewer on a longer row, about ten in all. */
    fun grid(span: Long) = if (span <= SPAN_MILLIS) GRID_MILLIS else maxOf(50L, span / 10 / 50 * 50)

    /** When the motor is on for [pattern], starting at [atMillis]. The timings take turns: a pulse, a gap, a pulse. */
    fun buzzes(pattern: List<Long>, atMillis: Long = 0, dropped: Boolean = false): List<Buzz> {
        val buzzes = ArrayList<Buzz>()
        var at = atMillis
        for ((index, millis) in pattern.withIndex()) {
            if (index % 2 == 0) buzzes += Buzz(at, at + millis, dropped)
            at += millis
        }
        return buzzes
    }
}
