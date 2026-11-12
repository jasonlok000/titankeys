package com.atlas.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import kotlin.math.hypot
import kotlin.math.max

/**
 * The pattern families. Each meaning has one rhythm, the same everywhere, and every family is our own waveform: the phone's
 * ready-made effects all felt generic. [pattern] is on and off in ms, starting with on. See the rulebook in docs/haptics.md.
 */
enum class Family(val id: String, val label: String, val pattern: List<Long>) {
    /** One light tick for fast, repeating things: keys crossed, cursor letters, words going in a hold, marks moving. */
    Texture("texture", "Texture", listOf(17)),
    /** One firmer tick where a word ends: a space the cursor passes, a word the selection grabs, a new page or group. */
    Boundary("boundary", "Boundary", listOf(30)),
    /** Short then long, rising: done. */
    Confirm("confirm", "Confirm", listOf(15, 40, 35)),
    /** Two quick light pulses: something changed by itself. */
    Change("change", "Change", listOf(20, 35, 20)),
    /** Two equal firm pulses: it stays. */
    Lock("lock", "Lock", listOf(30, 50, 30)),
    /** One pulse: it no longer stays. */
    Release("release", "Release", listOf(30)),
    /** Long then short, falling: a word flicked away. The tail lands as the dots pop. */
    GoneFlick("gone_flick", "Gone flick", listOf(50, 35, 20)),
    /** One long pulse: gone. */
    Gone("gone", "Gone", listOf(60)),
    /** Long then short, falling: undone, back as it was. */
    Back("back", "Back", listOf(35, 40, 15)),
    /** Three quick hard pulses: no, nothing happened. */
    No("no", "No", listOf(25, 40, 25, 40, 25)),
    /**
     * A hit that bounces and settles, each pulse shorter: a gesture hit a wall, the end of what it can do (owner, 2026-11-12).
     * The owner's start was 40, 30, 20, 20, 12; the gaps are 35 so each bounce still reads on its own.
     */
    Wall("wall", "Wall", listOf(40, 35, 20, 35, 12)),
    /**
     * The word menu's ladder (owner, 2026-11-12): each step up rises, and each step down falls, heavier the further down it goes.
     * Up: Saved, then Favourite, ending on the strongest hit. Down: Unfavourite, light; Unsaved, medium; Removed, the heaviest.
     * Short, short, long: two light taps settle into a firm one. The word menu kept the word.
     */
    Saved("saved", "Saved", listOf(20, 40, 20, 40, 50)),
    /** Long, short, short: a firm hit that trails off, Saved's mirror. The word menu took the word away. */
    Removed("removed", "Removed", listOf(50, 40, 20, 40, 20)),
    /**
     * Two tiny pulses close together: a grain, felt as one gritty tick, for keys crossed while a stroke is clearly a delete
     * flick, and for each word a held delete takes (owner, 2026-11-12). Its gap is under rule 4's 35 ms on purpose, so the two
     * read as one rough tick, not two. Each pulse is 9 ms, the least this motor shows well (see Held delete fade's floor).
     */
    Crunch("crunch", "Crunch", listOf(9, 12, 9)),
    /** Saved, ending on a stronger hit: the word menu made a saved word a favourite. */
    Favourite("favourite", "Favourite", listOf(20, 40, 20, 40, 80)),
    /** A light fall, the lightest step down: the word is no longer a favourite, but stays saved. */
    Unfavourite("unfavourite", "Unfavourite", listOf(30, 40, 20, 40, 12)),
    /** A medium fall, between Unfavourite's and Removed's: the word left Saved words. */
    Unsaved("unsaved", "Unsaved", listOf(40, 40, 20, 40, 15)),
    /** A rising run of three: the guide's celebrations. */
    Celebrate("celebrate", "Celebrate", listOf(15, 40, 22, 40, 35));

    /**
     * Texture and Boundary come in fast runs, so they keep their own pace and never block a message. Every other family is a
     * message: something happened.
     */
    val texture get() = this == Texture || this == Boundary || this == Crunch

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** The groups on the Haptics settings page, in its order. */
enum class HapticGroup { Swipe, Delete, Space, Changes, Bar, Modifiers, Panels, Other }

/**
 * Every action that plays a haptic, each with its own switch in Settings > Haptics. [id] is stored, so it never changes.
 * The labels are on the settings page, lib/haptics.ts.
 */
enum class Haptic(val id: String, val group: HapticGroup, val family: Family) {
    SwipeStart("swipe_start", HapticGroup.Swipe, Family.Texture),
    SwipeKey("swipe_key", HapticGroup.Swipe, Family.Texture),
    /** A light touch on the touchpad typed a key, which a key press would have clicked. It can come fast, so it's texture. */
    TapKey("tap_key", HapticGroup.Swipe, Family.Texture),

    DeleteFlick("delete_flick", HapticGroup.Delete, Family.GoneFlick),
    /** A flick's dots, or a hold's at its lift, going into the caret: a light tick as each one lands. */
    DeleteDotsLand("delete_dots_land", HapticGroup.Delete, Family.Texture),
    DeleteWord("delete_word", HapticGroup.Delete, Family.Crunch),
    /** Each key a stroke crosses while it is clearly a delete flick: a crunch, in the place of swiping's tick. See [FlickCrunch]. */
    DeleteCrunch("delete_crunch", HapticGroup.Delete, Family.Crunch),
    /** A stroke that crunched stopped being a flick: one firm tick, and swiping's ticks from there. */
    DeleteBreak("delete_break", HapticGroup.Delete, Family.Boundary),
    /** A flick with nothing at all to delete: nothing happened. */
    DeleteNothing("delete_nothing", HapticGroup.Delete, Family.Wall),
    /** A held delete took the last word: it hit the start of the text. It plays once, and the hold stays quiet after it. */
    DeleteRanOut("delete_ran_out", HapticGroup.Delete, Family.Wall),
    /**
     * A held delete's finger went into the pause zone, or reached the top-speed line: one firm tick, once each time it gets
     * there. The Haptics lab's experiment "Tick at delete zones" turns it off.
     */
    DeleteZone("delete_zone", HapticGroup.Delete, Family.Boundary),

    /** A Space tap that the gesture code counted, so each tap of a double or triple tap is felt as it lands. */
    SpaceTap("space_tap", HapticGroup.Space, Family.Texture),
    Steer("steer", HapticGroup.Space, Family.Confirm),
    /** The finger came back within the slip time, and steering picks up again instead of typing. */
    SteerResumed("steer_resumed", HapticGroup.Space, Family.Texture),
    Select("select", HapticGroup.Space, Family.Lock),
    /** A double or triple tap on Space picked the word at the cursor. It is selected, so it feels like selecting. */
    WordPicked("word_picked", HapticGroup.Space, Family.Lock),
    /**
     * Taps on or above Space found nothing to work on: a double or triple tap with no word to pick, or steering with no text.
     * Nothing happens, and a no says why. In the guide's practice field it also plays for two taps above Space, which pick nothing.
     */
    NothingToPick("nothing_to_pick", HapticGroup.Space, Family.No),
    CursorLetter("cursor_letter", HapticGroup.Space, Family.Texture),
    CursorSpace("cursor_space", HapticGroup.Space, Family.Boundary),
    SelectionWord("selection_word", HapticGroup.Space, Family.Boundary),
    /** Steering hit the start or end of the text. Once each time it gets there, as [CursorSteps] has it. */
    CursorEnd("cursor_end", HapticGroup.Space, Family.Wall),
    /** Selecting reached the start or end of the text. Once each time it gets there, as [WallHits] has it. */
    SelectionEnd("selection_end", HapticGroup.Space, Family.Wall),

    /** Autocorrect changed a word, or shorthand was spelled out, which goes the same way. */
    Autocorrect("autocorrect", HapticGroup.Changes, Family.Change),
    /** Backspace right after a change put back what was typed. */
    AutocorrectUndone("autocorrect_undone", HapticGroup.Changes, Family.Back),
    HeldSymbol("held_symbol", HapticGroup.Changes, Family.Change),
    /** A :name: became its emoji, or a snippet's shortcut its text. */
    Expansion("expansion", HapticGroup.Changes, Family.Change),
    /** Ctrl + C with nothing selected copies nothing: it says so, always. */
    CopyNothing("copy_nothing", HapticGroup.Changes, Family.No),

    BarWord("bar_word", HapticGroup.Bar, Family.Confirm),
    MenuOpen("menu_open", HapticGroup.Bar, Family.Confirm),
    MenuMark("menu_mark", HapticGroup.Bar, Family.Texture),
    /** Back closed the word menu, by P or the strip, with nothing picked. */
    MenuClosed("menu_closed", HapticGroup.Bar, Family.Release),
    /** Save in the word menu, by I or the strip. It has its own family, so it never feels like the menu opening. */
    WordSaved("word_saved", HapticGroup.Bar, Family.Saved),
    /** Forget in the word menu, by O or the strip. */
    WordForgotten("word_forgotten", HapticGroup.Bar, Family.Removed),
    /** Favourite in the word menu, by I on a saved word or the strip. */
    WordFavourited("word_favourited", HapticGroup.Bar, Family.Favourite),
    /** Unfavourite in the word menu, by O on a favourite or the strip. */
    WordUnfavourited("word_unfavourited", HapticGroup.Bar, Family.Unfavourite),
    /** Unsave in the word menu: one step down, out of Saved words. */
    WordUnsaved("word_unsaved", HapticGroup.Bar, Family.Unsaved),
    /** I on a favourite, the top of the ladder: nothing more to do. */
    MenuSpent("menu_spent", HapticGroup.Bar, Family.No),
    /** Undo right after Forget: the word is back. */
    ForgetUndone("forget_undone", HapticGroup.Bar, Family.Back),
    /** Save went wrong and the word isn't saved. */
    WordNotSaved("word_not_saved", HapticGroup.Bar, Family.No),
    ClipOffer("clip_offer", HapticGroup.Bar, Family.Confirm),
    /** A key on exact typing's bar, or Type clip. */
    ExactKey("exact_key", HapticGroup.Bar, Family.Confirm),
    /** A long press locked a chip on exact typing's bar. */
    ExactLock("exact_lock", HapticGroup.Bar, Family.Lock),
    /** A tap on a chip of exact typing's bar turned it on for the next key, or off. */
    ExactChip("exact_chip", HapticGroup.Bar, Family.Confirm),
    /** A tap on a locked chip of exact typing's bar unlocked it. */
    ExactUnlock("exact_unlock", HapticGroup.Bar, Family.Release),

    ModifierLocked("modifier_locked", HapticGroup.Modifiers, Family.Lock),
    ModifierUnlocked("modifier_unlocked", HapticGroup.Modifiers, Family.Release),

    PanelMark("panel_mark", HapticGroup.Panels, Family.Texture),
    PanelPage("panel_page", HapticGroup.Panels, Family.Boundary),
    PanelGroup("panel_group", HapticGroup.Panels, Family.Boundary),
    QuickSetting("quick_setting", HapticGroup.Panels, Family.Confirm),
    ClipPasted("clip_pasted", HapticGroup.Panels, Family.Confirm),
    ClipPinned("clip_pinned", HapticGroup.Panels, Family.Lock),
    ClipUnpinned("clip_unpinned", HapticGroup.Panels, Family.Release),
    ClipboardCleared("clipboard_cleared", HapticGroup.Panels, Family.Gone),

    BugRecording("bug_recording", HapticGroup.Other, Family.Confirm),
    GuideCelebration("guide_celebration", HapticGroup.Other, Family.Celebrate),
    /** Next pressed in the guide. */
    GuideNext("guide_next", HapticGroup.Other, Family.Confirm),
    /** The guide's slide blocked something, and says so. */
    GuideBlocked("guide_blocked", HapticGroup.Other, Family.No),
    /** The guide showed a hint or a scenario chip. */
    GuideHint("guide_hint", HapticGroup.Other, Family.Change);

    /** Its switch in the "swipe_haptics" preferences. */
    val key get() = "haptic_$id"

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }

        /** What a short tap on an exact typing chip plays: Release when it unlocks a locked chip, and Confirm otherwise. */
        fun chipTapped(wasLocked: Boolean) = if (wasLocked) ExactUnlock else ExactChip

        /** What a double or triple tap plays: Lock when it picked a word, and Nothing to pick when there was none. */
        fun picked(found: Boolean) = if (found) WordPicked else NothingToPick

        /** What a held Space gesture plays as it registers: steer, select, or Nothing to pick where it has nothing to work on. */
        fun registered(words: Boolean, nothing: Boolean) = when {
            nothing -> NothingToPick
            words -> Select
            else -> Steer
        }
    }
}

/** The rulebook's numbers, and the debug build's tried patterns. It has no Android parts, so the checks can read it. */
object HapticPalette {
    /** A texture tick. The owner found 17 ms very good for swipes and cursor letters. */
    const val TEXTURE_MILLIS = 17L
    /** The least gap between two pulses, so each reads on its own. */
    const val MIN_GAP_MILLIS = 35L
    /** The shortest single pulse that still reads as a message. */
    const val MESSAGE_PULSE_MILLIS = 30L
    /** What the debug lab's sliders allow: pulses from 4 to 120 ms, gaps from 35 to 200 ms, so no gap breaks the rulebook. */
    val PULSE_MILLIS = 4L..120L
    val GAP_MILLIS = MIN_GAP_MILLIS..200L
    /** A crunch's gap, under rule 4 on purpose: its two pulses are one grain. The lab's slider keeps it in this range. */
    val GRAIN_GAP_MILLIS = 4L..30L

    /** What [family]'s gaps may be: rule 4's, or a crunch's grain. */
    fun gapRange(family: Family) = if (family == Family.Crunch) GRAIN_GAP_MILLIS else GAP_MILLIS

    /** How long [pattern] runs, in ms. */
    fun millis(pattern: List<Long>) = pattern.sum()

    /** The pulses of [pattern], without its gaps. */
    fun pulses(pattern: List<Long>) = pattern.filterIndexed { index, _ -> index % 2 == 0 }

    /** The gaps of [pattern]. */
    fun gaps(pattern: List<Long>) = pattern.filterIndexed { index, _ -> index % 2 == 1 }

    /** "15 on, 40 off, 35 on". */
    fun describe(pattern: List<Long>) =
        pattern.mapIndexed { index, millis -> "$millis ${if (index % 2 == 0) "on" else "off"}" }.joinToString(", ")

    /**
     * Whether [pattern] can stand in for [family]'s: the same number of pulses and gaps, each pulse and gap in the sliders'
     * range. A tried pattern that doesn't fit is ignored.
     */
    fun fits(family: Family, pattern: List<Long>) = pattern.size == family.pattern.size &&
        pulses(pattern).all { it in PULSE_MILLIS } && gaps(pattern).all { it in gapRange(family) }

    /** A stored pattern, "15,40,35", or null if it isn't one. */
    fun parse(text: String?): List<Long>? {
        if (text.isNullOrBlank()) return null
        return text.split(',').map { it.trim().toLongOrNull() ?: return null }
    }

    fun format(pattern: List<Long>) = pattern.joinToString(",")

    /**
     * What [family] plays: its rulebook pattern, or the one [stored] for it in the debug lab. Only a [debug] build reads a
     * tried pattern, so every other build always plays the rulebook's.
     */
    fun pattern(family: Family, debug: Boolean, stored: String?): List<Long> {
        if (!debug) return family.pattern
        return parse(stored)?.takeIf { fits(family, it) } ?: family.pattern
    }
}

/**
 * How a held delete's ticks fade, so a long hold doesn't turn into one constant buzz. The Titan has no strength control, so
 * the fade is by length. The first tick plays the start length. Each tick after it is shorter, in even steps, until tick
 * words + 1 plays the floor. From there only one tick in every [Settings.thinEvery] plays. See docs/haptics.md. It has no
 * Android parts, so the checks can run it.
 */
object HeldDeleteFade {
    /** The four numbers the lab can tune. Lengths are in ms. */
    data class Settings(val start: Long, val floor: Long, val words: Int, val thinEvery: Int) {
        /** Each number inside its slider's range, and the floor never above the start. */
        fun fitted() = Settings(start.coerceIn(MILLIS), floor.coerceIn(MILLIS).coerceAtMost(start.coerceIn(MILLIS)),
            words.coerceIn(WORDS), thinEvery.coerceIn(THIN_EVERY))
    }

    /**
     * Word 1 at 17 ms, Texture's tick, down to 9 ms by word 9, then every other one. The phone's vibration log shows 8 ms
     * buzzes barely register on the Titan's motor, so the floor stays at 9 ms.
     */
    val DEFAULT = Settings(start = HapticPalette.TEXTURE_MILLIS, floor = 9, words = 8, thinEvery = 2)

    /** What the lab's sliders allow. */
    val MILLIS = 4L..40L
    val WORDS = 1..20
    val THIN_EVERY = 1..4

    /** How many ticks the lab's strip shows, and how many words its Play holds for. */
    const val STRIP_TICKS = 16
    const val PLAY_WORDS = 10

    /** How long the tick for [word] of a hold plays, in ms, counting from 1. Null when thinning leaves it out. */
    /**
     * A held word's crunch as the fade has it: each of [crunch]'s pulses shortened as the fade's [millis] is from its [start],
     * never under 6 ms, with its grain's gap as it is.
     */
    fun crunch(crunch: List<Long>, millis: Long, start: Long): List<Long> = crunch.mapIndexed { index, part ->
        if (index % 2 == 1) part else maxOf(6L, Math.round(part.toDouble() * millis / start.coerceAtLeast(1)))
    }

    fun lengthFor(word: Int, settings: Settings): Long? {
        require(word >= 1) { "Words count from 1" }
        val fade = settings.fitted()
        if (word <= fade.words) return fade.start - Math.round((fade.start - fade.floor) * (word - 1).toDouble() / fade.words)
        return if ((word - fade.words - 1) % fade.thinEvery == 0) fade.floor else null
    }

    /**
     * How long [word]'s tick plays with the lab's switch: faded by [settings] when [fading], or else the plain [texture] tick
     * on every word, as before the fade.
     */
    fun lengthFor(word: Int, settings: Settings, fading: Boolean, texture: Long = HapticPalette.TEXTURE_MILLIS): Long? =
        if (fading) lengthFor(word, settings) else texture

    /** The first [count] ticks of a hold, null where one is thinned. */
    fun ticks(settings: Settings, count: Int = STRIP_TICKS, fading: Boolean = true) = (1..count).map { lengthFor(it, settings, fading) }

    /**
     * Whether a held delete fades. It is on in every build (owner, 2026-11-12). Only a [debug] build can turn it off, with the
     * lab's switch; every other build fades whatever is [stored].
     */
    fun fading(debug: Boolean, stored: Boolean?) = !debug || (stored ?: true)

    /** "17 down to 9 ms over 8 words, then 1 in 2". */
    fun describe(settings: Settings): String {
        val fade = settings.fitted()
        // No-break spaces keep each number with its word, so a line never ends on "1 in" with the 2 alone below it.
        val then = if (fade.thinEvery == 1) "then all" else "then 1 in ${fade.thinEvery}"
        return "${fade.start} down to ${fade.floor} ms over ${fade.words} ${if (fade.words == 1) "word" else "words"}, $then"
    }

    /** Stored as "17,9,8,2". */
    fun format(settings: Settings) = settings.fitted().run { "$start,$floor,$words,$thinEvery" }

    /**
     * What a hold plays: the defaults, or the settings [stored] in the debug lab. Only a [debug] build reads them, so every
     * other build always plays the defaults. Stored numbers outside the sliders' ranges are ignored.
     */
    fun settings(debug: Boolean, stored: String?): Settings {
        if (!debug) return DEFAULT
        val numbers = HapticPalette.parse(stored)?.takeIf { it.size == 4 } ?: return DEFAULT
        val (start, floor, words, thinEvery) = numbers
        if (start !in MILLIS || floor !in MILLIS || words !in WORDS.toLongRange() || thinEvery !in THIN_EVERY.toLongRange()) return DEFAULT
        return Settings(start, floor, words.toInt(), thinEvery.toInt()).fitted()
    }

    private fun IntRange.toLongRange() = first.toLong()..last.toLong()
}

/** Counts a held delete's words, so each tick knows its place in the fade. A new hold starts again from word 1. */
class HeldDeleteCount {
    var word = 0
        private set

    /** The next word of this hold. */
    fun next() = ++word

    /** The hold ended or was cut. */
    fun reset() {
        word = 0
    }
}

/**
 * Which haptics play: the master switch, then each haptic's own. [read] gives a stored switch, or the default it is given.
 * Every switch is on until it is turned off. It has no Android parts, so the checks can run it.
 */
class HapticSwitches(private val read: (String, Boolean) -> Boolean) {
    val master get() = read(MASTER, true)

    fun on(haptic: Haptic) = read(haptic.key, true)

    fun allows(haptic: Haptic) = master && on(haptic)

    /** Why [haptic] can't play, or null when its switches let it. */
    fun stops(haptic: Haptic) = if (!master) HapticResult.MasterOff else if (!on(haptic)) HapticResult.SwitchedOff else null

    companion object {
        /** The master switch's key. It is the old Vibration setting's, so turning that off before still counts. */
        const val MASTER = "enabled"
    }
}

/**
 * When the delete dots tick as they land in the caret, a flick's or a hold's at its lift: one tick per dot, as each lands. The dots land at least 30 ms apart
 * (see GestureDisplay.DOT_LAND_GAP), so every tick stays its own. A tick near the flick's own tail is left out, so the two never
 * land on top of each other. It has no Android parts, so the checks can run it.
 */
object DotTicks {
    /**
     * The ticks, in ms after the dots started, for dots that land at [landings], leaving out any within 35 ms of [near]: the
     * flick's tail, or for a hold the last haptic before its lift, which is at 0 or before.
     */
    fun times(landings: List<Long>, near: Long?) =
        landings.filter { near == null || kotlin.math.abs(it - near) >= HapticPalette.MIN_GAP_MILLIS }
}

/**
 * A held delete's ticks as one sequence: the flick that takes the first word, a fading tick for each word after it, then at the
 * lift a tick as each dot lands. Times are in ms after the hold started, when its first word went.
 *
 * The rule: a word's tick and a dot's tick never play within 35 ms of each other ([HapticPalette.MIN_GAP_MILLIS]). The word wins,
 * as it's what the hold just did, and the dot's tick is skipped. A thinned word plays nothing, so a dot next to it still ticks.
 * Neither stops the other: every dot that isn't that close ticks. The dots keep their own spacing, 30 ms apart or more
 * (GestureDisplay.DOT_LAND_GAP), as before. It has no Android parts, so the checks can run it.
 */
object HoldTicks {
    enum class Kind { Flick, Word, Dot }

    /**
     * One tick, [at] ms after the hold started: the flick, or held word or dot [number], each from 1. [millis] is how long it
     * plays, and [skipped] why it doesn't, or null when it does.
     */
    data class Tick(val at: Long, val kind: Kind, val number: Int, val millis: Long, val skipped: String? = null) {
        val plays get() = skipped == null
    }

    /** Why a tick doesn't play: the fade left it out, or it fell too close to a word's tick. */
    const val THINNED = "thinned"
    const val NEAR_WORD = "near a word"

    /** How many words the lab's Play holds for after the flick, and the letters of each word it deletes, for its dots. */
    const val PLAY_WORDS = HeldDeleteFade.PLAY_WORDS
    private val PLAY_LETTERS = listOf(4, 3, 5, 2, 6, 4, 3, 5, 4, 3, 2)

    /** The letters of the flick's word and of [words] words after it, as the lab's Play deletes them. */
    fun playLetters(words: Int = PLAY_WORDS) = List(words + 1) { PLAY_LETTERS[it % PLAY_LETTERS.size] }

    /** The lab's Play lifts this long after its last word, before the next one would go. */
    const val PLAY_LIFT_MILLIS = 150L

    /**
     * The ticks of dots that land at [landings], each skipped if it is within 35 ms of a word's tick that played at [words]. Both
     * are in ms from the same moment, whatever it is.
     */
    fun dots(landings: List<Long>, words: List<Long>, millis: Long = HapticPalette.TEXTURE_MILLIS) = landings.mapIndexed { index, at ->
        val near = words.any { kotlin.math.abs(it - at) < HapticPalette.MIN_GAP_MILLIS }
        Tick(at, Kind.Dot, index + 1, millis, NEAR_WORD.takeIf { near })
    }

    /** The hold's [words] ticks and its dots landing at [landings], in one list in time order. A word comes before a dot at the same time. */
    fun merge(words: List<Tick>, landings: List<Long>, millis: Long = HapticPalette.TEXTURE_MILLIS): List<Tick> =
        (words + dots(landings, words.filter { it.plays }.map { it.at }, millis)).sortedBy { it.at }

    /**
     * A hold for the lab: the finger stays still [pauseMillis] after its flick, then the flick takes the first word, playing for
     * [flickMillis]. [words] more go [repeatMillis] apart, with [fade]'s ticks while [fading], or else [texture] on each. The finger
     * lifts [liftMillis] after the last word, and the dots land [landings] ms after the lift.
     */
    fun plan(words: Int, pauseMillis: Long, repeatMillis: Long, liftMillis: Long, fade: HeldDeleteFade.Settings, fading: Boolean,
             texture: Long, flickMillis: Long, landings: List<Long>): Plan {
        val held = listOf(Tick(0, Kind.Flick, 0, flickMillis)) + (1..words).map { word ->
            val millis = HeldDeleteFade.lengthFor(word, fade, fading, texture)
            Tick(word * repeatMillis, Kind.Word, word, millis ?: fade.fitted().floor, THINNED.takeIf { millis == null })
        }
        val liftAt = words * repeatMillis + liftMillis
        return Plan(pauseMillis, liftAt, merge(held, landings.map { liftAt + it }, texture))
    }

    /** A planned hold. Each tick's time is from the hold's start; [fromStill] gives it from when the finger stopped. */
    class Plan(val pauseMillis: Long, val liftAt: Long, val ticks: List<Tick>) {
        fun fromStill(tick: Tick) = pauseMillis + tick.at
        /** When the last tick ends, from when the finger stopped. */
        val endsAt get() = ticks.maxOfOrNull { fromStill(it) + it.millis } ?: pauseMillis
    }
}

/**
 * Ticks waiting to play, which a cut drops all at once. [post] runs a tick after a delay and [remove] takes one back, as a
 * Handler does. It has no Android parts, so the checks can run it with their own.
 */
class PendingTicks(private val post: (Runnable, Long) -> Unit, private val remove: (Runnable) -> Unit) {
    private val waiting = ArrayList<Runnable>()

    /** Drops any ticks still waiting, then [play]s once at each of [times], in ms from now. */
    fun schedule(times: List<Long>, play: () -> Unit) {
        cut()
        add(times, play)
    }

    /** [play]s once at each of [times], in ms from now, as well as the ticks still waiting: more dots joined the stream. */
    fun add(times: List<Long>, play: () -> Unit) {
        for (at in times) {
            lateinit var tick: Runnable
            tick = Runnable {
                waiting.remove(tick)
                play()
            }
            waiting += tick
            post(tick, at)
        }
    }

    /** Drops every tick still waiting, so none plays after this. */
    fun cut() {
        for (tick in waiting) remove(tick)
        waiting.clear()
    }

    val pending get() = waiting.size
}

/** What became of a haptic: it played, or why it didn't. */
enum class HapticResult(val words: String) {
    Played("played"),
    /** A message was still playing, or had only just ended. */
    Busy("busy"),
    /** The same family played less than [HapticGate.REPEAT_MILLIS] ago. */
    TooSoon("too soon"),
    /** Its own switch is off. */
    SwitchedOff("switched off"),
    /** All haptics is off. */
    MasterOff("all off"),
    /** A held delete's tick that the fade's thinning leaves out. */
    Thinned("thinned"),
    /** A hold's dot that landed within 35 ms of a word's tick, which plays instead. See [HoldTicks]. */
    NearWord("within 35 ms of a word's tick");

    val played get() = this == Played
}

/**
 * Keeps fast runs calm. It has no Android parts, so the checks can run it with their own [clock].
 * - Nothing cuts a playing message. Until it ends, nothing else plays, and the next message waits a gap more.
 * - A message can't repeat: one of the same family doesn't play within [REPEAT_MILLIS] of the last one's start. Two of the same
 *   rhythm that close would run together into one longer pattern.
 * - Texture keeps its own pace. It never blocks a message, so a message plays even right on top of a tick: messages beat texture.
 */
class HapticGate(private val clock: () -> Long) {
    private var busyUntil = Long.MIN_VALUE / 2
    private var quietUntil = Long.MIN_VALUE / 2
    private val lastAt = HashMap<Family, Long>()
    /** When the last texture that played ends, and until when no texture plays, around a wall. */
    private var textureUntil = Long.MIN_VALUE / 2
    private var textureQuietUntil = Long.MIN_VALUE / 2

    /** Whether [family], lasting [millis], plays now, or why not. A message that plays is the busy one from now on. */
    fun check(family: Family, millis: Long = HapticPalette.millis(family.pattern)): HapticResult {
        val now = clock()
        if (now < busyUntil) return HapticResult.Busy
        if (family.texture) {
            // Around a wall, nothing light plays: see [reserveWall].
            if (now < textureQuietUntil) return HapticResult.Busy
            textureUntil = max(textureUntil, now + millis)
            return HapticResult.Played
        }
        if (now < quietUntil) return HapticResult.Busy
        val last = lastAt[family]
        if (last != null && now - last < REPEAT_MILLIS) return HapticResult.TooSoon
        lastAt[family] = now
        busyUntil = now + millis
        quietUntil = busyUntil + HapticPalette.MIN_GAP_MILLIS
        if (family == Family.Wall) textureQuietUntil = max(textureQuietUntil, busyUntil + WALL_CLEAR_MILLIS)
        return HapticResult.Played
    }

    /**
     * A delete's wall, lasting [millis], is about to play: how long it waits first, so it starts [WALL_CLEAR_MILLIS] after the
     * last texture ended. From now until that long after it ends, no texture plays: no crunch, zone tick or dot's tick, so the
     * wall is felt clean, as Space's is (owner, 2026-11-12).
     */
    fun reserveWall(millis: Long): Long {
        val now = clock()
        val wait = max(0L, textureUntil + WALL_CLEAR_MILLIS - now)
        textureQuietUntil = max(textureQuietUntil, now + wait + millis + WALL_CLEAR_MILLIS)
        return wait
    }

    /** Whether [family], lasting [millis], plays now. */
    fun admit(family: Family, millis: Long = HapticPalette.millis(family.pattern)) = check(family, millis).played

    companion object {
        const val REPEAT_MILLIS = 300L
        /** A delete's wall has this long of quiet before it and after it. */
        const val WALL_CLEAR_MILLIS = 100L
    }
}

/**
 * One haptic the keyboard tried to play, for the debug Haptics lab: when (an uptime), what, its pattern as played, and the result.
 * [word] is a held delete's word, from 1, and null for everything else. [fading] says if that word played the fade. [dot] is a
 * hold's dot, from 1, of [dots], as each lands at the lift.
 */
data class HapticEvent(
    val atMillis: Long, val haptic: Haptic, val pattern: List<Long>, val result: HapticResult, val word: Int? = null,
    val fading: Boolean = true, val dot: Int? = null, val dots: Int? = null,
)

/**
 * The debug Haptics lab's live feed. The keyboard and the lab share one process, so this is a plain object: the keyboard's
 * [Haptics] records each play here, and the lab listens while it shows. It keeps the last [KEEP] events, so the lab shows recent
 * ones as it opens. Only TitanKeys Debug turns it on ([Haptics] does, from its package name). Off, recording does nothing.
 */
object HapticFeed {
    @Volatile
    var enabled = false
    private val recent = ArrayDeque<HapticEvent>()
    private var listener: ((HapticEvent) -> Unit)? = null

    fun record(event: HapticEvent) {
        if (!enabled) return
        val heard = synchronized(this) {
            recent.addLast(event)
            while (recent.size > KEEP) recent.removeFirst()
            listener
        }
        heard?.invoke(event)
    }

    /** The events kept, oldest first. */
    fun recent(): List<HapticEvent> = synchronized(this) { recent.toList() }

    /** Hears each new event, until [stop]. One listener at a time: the lab's screen. */
    fun listen(heard: (HapticEvent) -> Unit) = synchronized(this) { listener = heard }

    fun stop() = synchronized(this) { listener = null }

    const val KEEP = 64
}

/**
 * What a Ctrl shortcut plays. Only Ctrl + C with nothing selected plays anything: it copies nothing, so it says no. The other
 * shortcuts stay quiet: the owner tried a confirm on each, and on Alt's and the Sym panel's symbols, and found it annoying
 * (2026-11-12).
 */
object ShortcutFeel {
    /** The haptic for Ctrl with [letter], or null. [selected] is null when the field hasn't said what is selected. */
    fun of(letter: Char?, selected: Boolean?): Haptic? = if (letter == 'C' && selected == false) Haptic.CopyNothing else null
}

/**
 * The rule for hitting a wall, the same in all three gestures: the first time a gesture meets its limit it plays [Haptic]s of
 * the Wall family, and while the finger keeps pushing it stays quiet. Only after it has moved away does the next hit play again.
 */
class WallHits {
    private var hit = false

    /** True from the hit until the gesture moves away: a held delete that ran out stays quiet, zone ticks too, until then. */
    val atWall get() = hit

    /** The gesture met its limit: true the first time, false while it stays there. */
    fun reached(): Boolean {
        if (hit) return false
        hit = true
        return true
    }

    /** The gesture moved away from its limit, so the next time it gets there plays again. */
    fun left() {
        hit = false
    }
}

/**
 * What the cursor plays as it steps while steering. A space or a line end is a boundary between words, and every other
 * character is texture. Punctuation gets no tick of its own: it almost always sits next to a space, whose tick already marks
 * the end of the word. At the start or end of the text the cursor can't move, so it hits a wall, once each time it gets there.
 */
class CursorSteps {
    private var atEnd = false

    /** A new steer: the next end it reaches plays again. */
    fun reset() {
        atEnd = false
    }

    /** The haptic for a step past [next], the character it crosses, or null for an end of the text. */
    fun step(next: Char?): Haptic? {
        if (next == null) {
            if (atEnd) return null
            atEnd = true
            return Haptic.CursorEnd
        }
        atEnd = false
        return if (next.isWhitespace()) Haptic.CursorSpace else Haptic.CursorLetter
    }
}

/** Plays the keyboard's haptics ([Haptic]), only while its switch, the master switch and Android's touch feedback are on. */
class Haptics(private val context: Context) {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val switches = switches(context)
    private val gate = HapticGate(SystemClock::uptimeMillis)
    private val ticks = SwipeTicks()
    private val main = Handler(Looper.getMainLooper())
    private var tail: Runnable? = null
    /** When the last haptic played, an uptime. */
    private var lastPlayedAt = Long.MIN_VALUE / 2
    private val dotTicks = PendingTicks({ tick, at -> main.postDelayed(tick, at) }, main::removeCallbacks)
    private val previewTicks = PendingTicks({ tick, at -> main.postDelayed(tick, at) }, main::removeCallbacks)
    /** When each tick of the hold under way played, as uptimes: its flick and its words. A dot's tick keeps clear of them. */
    private val holdPlayed = ArrayList<Long>()
    private val feeds = debug(context)

    init {
        // Only TitanKeys Debug feeds the lab. Every other build records nothing.
        if (feeds) HapticFeed.enabled = true
    }

    /** Plays [haptic], and says whether it played: false when a switch is off or the gate dropped it. */
    fun play(haptic: Haptic) = attempt(haptic).played

    /** Plays [haptic], and says what became of it. */
    fun attempt(haptic: Haptic) = attempt(haptic, pattern(context, haptic.family))

    /**
     * Plays [pattern] for [haptic] through its switches and the gate. A [thinned] tick of a held delete doesn't play, and the
     * gate never hears of it. [word] is the held delete's word, and [dot] a hold's dot of [dots], for the lab.
     */
    private fun attempt(haptic: Haptic, pattern: List<Long>, word: Int? = null, thinned: Boolean = false, fading: Boolean = true,
                        dot: Int? = null, dots: Int? = null): HapticResult {
        val result = switches.stops(haptic)
            ?: if (thinned) HapticResult.Thinned else gate.check(haptic.family, HapticPalette.millis(pattern))
        if (result.played) {
            vibrate(pattern)
            lastPlayedAt = SystemClock.uptimeMillis()
            // A held word's tick, which the dots at the lift keep clear of.
            if (haptic == Haptic.DeleteWord) holdPlayed += lastPlayedAt
        }
        report(haptic, pattern, result, word, fading, dot, dots)
        return result
    }

    /** A hold took its first word, so its ticks start again: the dots at its lift keep clear of this hold's ticks alone. */
    fun holdStarted() {
        holdPlayed.clear()
        holdPlayed += SystemClock.uptimeMillis()
    }

    /**
     * [word] of a held delete, counting from 1: its tick, shorter as more words go, or nothing where the fade thins it out
     * (see [HeldDeleteFade]). A thinned tick shows in the lab at the floor's length. With the fade off, and in every build
     * but debug, each word plays plain texture, as before the fade.
     */
    fun heldDeleteWord(word: Int): HapticResult {
        val crunch = pattern(context, Family.Crunch)
        if (!heldDeleteFading(context)) return attempt(Haptic.DeleteWord, crunch, word, fading = false)
        val fade = heldDeleteFade(context)
        val millis = HeldDeleteFade.lengthFor(word, fade)
        return attempt(Haptic.DeleteWord, HeldDeleteFade.crunch(crunch, millis ?: fade.fitted().floor, fade.start), word, thinned = millis == null)
    }

    private fun report(haptic: Haptic, pattern: List<Long>, result: HapticResult, word: Int? = null, fading: Boolean = true,
                       dot: Int? = null, dots: Int? = null) {
        if (feeds) HapticFeed.record(HapticEvent(SystemClock.uptimeMillis(), haptic, pattern, result, word, fading, dot, dots))
    }

    /** Plays [pattern] alone, with no switch or gate, for the lab's editor. It still needs Android's touch feedback. */
    fun preview(pattern: List<Long>) = vibrate(pattern)

    /**
     * Plays a held delete as [plan] has it, from the hold's start: the flick, each word's fading tick, and the dots' ticks at
     * the lift, with no switch or gate, for the lab's editor. Each shows on the lab's live timeline as it comes, a skipped one
     * hollow. A new Play drops the ticks of the last one still to come.
     */
    fun previewHold(plan: HoldTicks.Plan) {
        val ticks = plan.ticks
        val dots = ticks.count { it.kind == HoldTicks.Kind.Dot }
        val fading = heldDeleteFading(context)
        var next = 0
        previewTicks.schedule(ticks.map { it.at }) {
            val tick = ticks[next++]
            val pattern = when (tick.kind) {
                HoldTicks.Kind.Flick -> pattern(context, Family.GoneFlick)
                // A held word crunches, shortened as the fade has it.
                HoldTicks.Kind.Word -> HeldDeleteFade.crunch(pattern(context, Family.Crunch), tick.millis,
                    if (fading) heldDeleteFade(context).start else tick.millis)
                HoldTicks.Kind.Dot -> listOf(tick.millis)
            }
            if (tick.plays) vibrate(pattern)
            val result = when (tick.skipped) {
                null -> HapticResult.Played
                HoldTicks.THINNED -> HapticResult.Thinned
                else -> HapticResult.NearWord
            }
            when (tick.kind) {
                HoldTicks.Kind.Flick -> report(Haptic.DeleteFlick, pattern, result)
                HoldTicks.Kind.Word -> report(Haptic.DeleteWord, pattern, result, tick.number, fading)
                HoldTicks.Kind.Dot -> report(Haptic.DeleteDotsLand, pattern, result, dot = tick.number, dots = dots)
            }
        }
    }

    /**
     * The delete flick, timed to its dots: the first pulse now, and the tail as the dots pop, [poppedMillis] from now. With no
     * dots to time it to (null), or a pop sooner than the pattern's own gap, it plays as one waveform. The gate takes both parts
     * as one message, busy until the tail ends. Then a light tick as each dot lands in the caret, at [landings] (see [DotTicks]),
     * each only while [showing] says the same flick's dots are still on the bar. [cutDots] drops the ticks still to come.
     */
    fun flick(poppedMillis: Long?, landings: List<Long> = emptyList(), showing: () -> Boolean = { false }): Boolean {
        val pattern = pattern(context, Family.GoneFlick)
        val head = pattern[0]
        val tailAt = poppedMillis?.coerceIn(head + pattern[1], head + HapticPalette.GAP_MILLIS.last)
        val played = flickNow(pattern, tailAt)
        landTicks(landings, if (played) tailAt ?: (head + pattern[1]) else null, showing)
        return played
    }

    /**
     * The ticks for dots that land at [landings]. A flick that joins a stream still playing starts the whole queue's wait again,
     * so [landings] has every dot still to land, and its ticks take the place of the ones still to come.
     */
    private fun landTicks(landings: List<Long>, tailAt: Long?, showing: () -> Boolean) {
        val times = DotTicks.times(landings, tailAt)
        dotTicks.schedule(times) { if (showing()) play(Haptic.DeleteDotsLand) }
    }

    /**
     * A hold's dots at its lift: a light tick as each lands in the caret, at [landings], each only while [showing] says those
     * dots are still on the bar. A dot within 35 ms of one of this hold's ticks, or of the last haptic, such as its no, is
     * skipped (see [HoldTicks]). The lab's timeline shows a skipped dot hollow, with its reason.
     */
    fun holdDots(landings: List<Long>, showing: () -> Boolean) {
        val now = SystemClock.uptimeMillis()
        val near = (holdPlayed + lastPlayedAt).map { it - now }
        holdPlayed.clear()
        val ticks = HoldTicks.dots(landings, near, pattern(context, Family.Texture).first())
        // Only the lab hears of a skipped dot.
        val scheduled = if (feeds) ticks else ticks.filter { it.plays }
        var next = 0
        dotTicks.schedule(scheduled.map { it.at }) {
            val tick = scheduled[next++]
            if (!showing()) return@schedule
            if (tick.plays) attempt(Haptic.DeleteDotsLand, pattern(context, Family.Texture), dot = tick.number, dots = ticks.size)
            else report(Haptic.DeleteDotsLand, listOf(tick.millis), HapticResult.NearWord, dot = tick.number, dots = ticks.size)
        }
    }

    /**
     * Plays a flick as [flick] would, its tail at [poppedMillis] and a tick at each of [landings], with no switch or gate, for the
     * lab's Play. Each shows on the lab's live timeline as it comes. A new Play drops the ticks of the last one still to come.
     */
    fun previewFlick(poppedMillis: Long?, landings: List<Long>) {
        val pattern = pattern(context, Family.GoneFlick)
        val head = pattern[0]
        val tailAt = poppedMillis?.coerceIn(head + pattern[1], head + HapticPalette.GAP_MILLIS.last) ?: (head + pattern[1])
        val played = listOf(head, tailAt - head) + pattern.drop(2)
        vibrate(played)
        report(Haptic.DeleteFlick, played, HapticResult.Played)
        val texture = pattern(context, Family.Texture)
        previewTicks.schedule(DotTicks.times(landings, tailAt)) {
            vibrate(texture)
            report(Haptic.DeleteDotsLand, texture, HapticResult.Played)
        }
    }

    /** Any key or touch cuts the dots at once, and with them the ticks still to come. */
    fun cutDots() = dotTicks.cut()

    private var pendingWall: Runnable? = null

    /**
     * A delete's wall: a flick with nothing to delete, or a held delete that ran out. The same Wall as steering's, played clean:
     * the dots' ticks still to come are cut, it waits until [HapticGate.WALL_CLEAR_MILLIS] after the last texture, and nothing
     * light plays until that long after it. See [HapticGate.reserveWall].
     */
    fun wall(haptic: Haptic) {
        check(haptic.family == Family.Wall) { "$haptic is no wall" }
        if (switches.stops(haptic) != null) {
            attempt(haptic)
            return
        }
        val pattern = pattern(context, haptic.family)
        dotTicks.cut()
        val wait = gate.reserveWall(HapticPalette.millis(pattern))
        pendingWall?.let(main::removeCallbacks)
        pendingWall = null
        if (wait <= 0L) attempt(haptic, pattern)
        else pendingWall = Runnable {
            pendingWall = null
            attempt(haptic, pattern)
        }.also { main.postDelayed(it, wait) }
    }

    private fun flickNow(pattern: List<Long>, tailAt: Long?): Boolean {
        val head = pattern[0]
        if (tailAt == null || tailAt == head + pattern[1]) return play(Haptic.DeleteFlick)
        val tailPulse = pattern.drop(2)
        val played = listOf(head, tailAt - head) + tailPulse
        val result = switches.stops(Haptic.DeleteFlick) ?: gate.check(Family.GoneFlick, HapticPalette.millis(played))
        report(Haptic.DeleteFlick, played, result)
        if (!result.played) return false
        vibrate(listOf(head))
        tail?.let(main::removeCallbacks)
        tail = Runnable { vibrate(tailPulse) }.also { main.postDelayed(it, tailAt) }
        return true
    }

    /** A finished part of the interactive guide: a rising run of three. */
    fun celebrate() = play(Haptic.GuideCelebration)

    fun begin(sample: Sample?) = ticks.begin(sample)

    /** The swipe moved. While it is clearly a delete flick, [crunch], a key it crosses crunches instead of ticking. */
    fun move(samples: List<Sample>, crunch: Boolean = false) {
        ticks.move(samples)?.let { play(if (crunch && it == Haptic.SwipeKey) Haptic.DeleteCrunch else it) }
    }

    /**
     * Our own waveform, never one of the phone's ready-made effects. The Titan has no strength control, so every pulse is at
     * the default strength, and only its length counts. One pulse is a one-shot.
     */
    private fun vibrate(pattern: List<Long>) {
        if (!vibrator.hasVibrator()) return
        if (Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
        val effect = if (pattern.size == 1) VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE)
            // A waveform starts with a wait, so a 0 comes first.
            else VibrationEffect.createWaveform(longArrayOf(0) + pattern.toLongArray(), -1)
        // Hardware feedback keeps ticking while another app is in the foreground.
        vibrator.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK).build())
    }

    companion object {
        private fun preferences(context: Context) = context.getSharedPreferences("swipe_haptics", Context.MODE_PRIVATE)

        /** The switches as they are stored now, read again on each use, so a change in Settings counts at once. */
        fun switches(context: Context): HapticSwitches {
            val preferences = preferences(context)
            return HapticSwitches { key, default -> preferences.getBoolean(key, default) }
        }

        private fun debug(context: Context) = context.packageName.endsWith(".debug")

        /**
         * What [family] plays now. Only the debug build reads a pattern tried in the Haptics lab. Every other build plays the
         * rulebook's, without even looking.
         */
        fun pattern(context: Context, family: Family): List<Long> {
            if (!debug(context)) return family.pattern
            return HapticPalette.pattern(family, true, preferences(context).getString(patternKey(family), null))
        }

        /** Tries [pattern] for [family], or puts the rulebook's back with null. Debug build only, and only a pattern that fits. */
        fun tryPattern(context: Context, family: Family, pattern: List<Long>?) {
            check(debug(context)) { "Only TitanKeys Debug can try a pattern" }
            preferences(context).edit().apply {
                if (pattern == null || pattern == family.pattern || !HapticPalette.fits(family, pattern)) remove(patternKey(family))
                else putString(patternKey(family), HapticPalette.format(pattern))
            }.apply()
        }

        private fun patternKey(family: Family) = "debug_pattern_${family.id}"

        /**
         * The Haptics lab's experiment "Dots speed up when long": after the first few, each of a delete's dots lands a little
         * sooner, and ticks keep 30 ms apart. On by default (owner, 2026-11-11). Only TitanKeys Debug reads the lab's switch, and
         * every other build has it on without looking.
         */
        fun dotsSpeedUp(context: Context) = !debug(context) || preferences(context).getBoolean(DOTS_SPEED_UP, true)

        fun tryDotsSpeedUp(context: Context, on: Boolean) {
            check(debug(context)) { "Only TitanKeys Debug has the lab's experiments" }
            preferences(context).edit().putBoolean(DOTS_SPEED_UP, on).apply()
        }

        private const val DOTS_SPEED_UP = "debug_dots_speed_up"

        /**
         * The Haptics lab's experiment "Tick at delete zones": a held delete ticks once as its finger goes into the pause zone,
         * and once as it reaches the top-speed line. On by default (owner, 2026-11-12). Only TitanKeys Debug reads the lab's
         * switch, and every other build has it on without looking.
         */
        fun zoneTicks(context: Context) = !debug(context) || preferences(context).getBoolean(ZONE_TICKS, true)

        fun tryZoneTicks(context: Context, on: Boolean) {
            check(debug(context)) { "Only TitanKeys Debug has the lab's experiments" }
            preferences(context).edit().putBoolean(ZONE_TICKS, on).apply()
        }

        private const val ZONE_TICKS = "debug_delete_zone_ticks"

        /**
         * How long a single flick's dots sit on the bar, as the Haptics lab tried it, in ms, or null for the default. Only
         * TitanKeys Debug looks: every other build returns null without looking. The keyboard hands it to the bar.
         */
        fun readFlickHold(context: Context): Long? {
            if (!debug(context)) return null
            return preferences(context).getLong(FLICK_HOLD_KEY, -1L).takeIf { it >= 0 }?.coerceIn(FLICK_HOLD_RANGE)
        }

        /** Tries [millis] as how long a flick's dots sit on the bar, or puts the default back with null. Debug build only. */
        fun tryFlickHold(context: Context, millis: Long?) {
            check(debug(context)) { "Only TitanKeys Debug can try how long the dots wait" }
            preferences(context).edit().apply {
                if (millis == null) remove(FLICK_HOLD_KEY) else putLong(FLICK_HOLD_KEY, millis.coerceIn(FLICK_HOLD_RANGE))
            }.commit()
        }

        /** The waits that the Haptics lab can try for a single flick's dots, in ms. */
        val FLICK_HOLD_RANGE = 0L..600L

        private const val FLICK_HOLD_KEY = "debug_flick_dots_wait"

        /**
         * How a held delete fades now. Only the debug build reads settings tried in the Haptics lab. Every other build plays
         * the defaults, without even looking.
         */
        fun heldDeleteFade(context: Context): HeldDeleteFade.Settings {
            if (!debug(context)) return HeldDeleteFade.DEFAULT
            return HeldDeleteFade.settings(true, preferences(context).getString(FADE_KEY, null))
        }

        /** Tries [fade] for a held delete, or puts the defaults back with null. Debug build only. */
        fun tryFade(context: Context, fade: HeldDeleteFade.Settings?) {
            check(debug(context)) { "Only TitanKeys Debug can try a fade" }
            preferences(context).edit().apply {
                if (fade == null || fade.fitted() == HeldDeleteFade.DEFAULT) remove(FADE_KEY)
                else putString(FADE_KEY, HeldDeleteFade.format(fade))
            }.apply()
        }

        /**
         * Whether a held delete fades. Every build fades, at the defaults (owner, 2026-11-12); only the debug build can turn it
         * off, with the lab's switch.
         */
        fun heldDeleteFading(context: Context): Boolean {
            if (!debug(context)) return true
            val preferences = preferences(context)
            return HeldDeleteFade.fading(true, if (preferences.contains(FADE_ON_KEY)) preferences.getBoolean(FADE_ON_KEY, true) else null)
        }

        /** Turns the held delete fade on or off, to compare the two. Debug build only. */
        fun fadeOn(context: Context, on: Boolean) {
            check(debug(context)) { "Only TitanKeys Debug can switch the fade" }
            preferences(context).edit().apply { if (on) remove(FADE_ON_KEY) else putBoolean(FADE_ON_KEY, false) }.apply()
        }

        /** Stored like a family's tried pattern, as "17,9,8,2". */
        private const val FADE_KEY = "debug_pattern_held_delete_fade"
        /** The lab's switch for the fade, stored only while it's off. */
        private const val FADE_ON_KEY = "debug_held_delete_fade_on"

        /** The master switch: All haptics in Settings, and Vibration on the quick settings page. */
        fun enabled(context: Context) = switches(context).master

        fun enabled(context: Context, on: Boolean) {
            preferences(context).edit().putBoolean(HapticSwitches.MASTER, on).apply()
        }

        /** Each haptic's own switch, by its id. */
        fun toggles(context: Context): Map<String, Boolean> {
            val switches = switches(context)
            return Haptic.entries.associate { it.id to switches.on(it) }
        }

        fun toggle(context: Context, id: String, on: Boolean) {
            val haptic = Haptic.of(id) ?: return
            preferences(context).edit().putBoolean(haptic.key, on).apply()
        }
    }
}

/** When a swipe ticks. It has no Android parts, so the touchpad checks can run it. */
class SwipeTicks {
    private var lastTick = -1L
    private var travel = 0f
    private var origin: Sample? = null
    private var last: Sample? = null

    fun begin(sample: Sample?) {
        lastTick = -1
        travel = 0f
        origin = sample
        last = sample
    }

    /**
     * What these new points of the contact play, at most once: [Haptic.SwipeStart] when the contact first goes as far as a
     * swipe, [Haptic.SwipeKey] for each later tick, or null.
     */
    fun move(samples: List<Sample>): Haptic? {
        val start = origin ?: return null
        var ticked: Haptic? = null
        for (sample in samples) {
            last?.let { travel += distance(it, sample) }
            last = sample
            if (!Layout.isLetter(Layout.keyAt(sample.x, sample.y))) {
                travel = 0f
                continue
            }
            // The first tick waits until the contact has gone far enough to be a swipe, so a finger that only brushes the
            // keys, or slides a little while pushing one, stays quiet (#30). A slow start waits no longer: the owner felt the
            // first ticks go missing (Nov 11). The phantom-buzz numbers are in the swipe baseline's phantom-ticks report.
            val first = lastTick < 0
            val ready = if (first) distance(start, sample) >= Contact.SWIPE_KEYS
                else travel >= SPACING_KEYS && sample.time - lastTick >= INTERVAL_MILLIS
            if (!ready || ticked != null) continue
            lastTick = sample.time
            travel = 0f
            ticked = if (first) Haptic.SwipeStart else Haptic.SwipeKey
        }
        return ticked
    }

    private fun distance(a: Sample, b: Sample) = hypot((b.x - a.x) * 10, (b.y - a.y) * 4)

    companion object {
        /** Travel between ticks, in key widths. */
        const val SPACING_KEYS = .7f
        /** The least time between ticks. */
        const val INTERVAL_MILLIS = 40L
    }
}
