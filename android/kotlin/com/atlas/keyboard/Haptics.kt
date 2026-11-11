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
    /** A rising run of three: the guide's celebrations. */
    Celebrate("celebrate", "Celebrate", listOf(15, 40, 22, 40, 35));

    /**
     * Texture and Boundary come in fast runs, so they keep their own pace and never block a message. Every other family is a
     * message: something happened.
     */
    val texture get() = this == Texture || this == Boundary

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
    DeleteWord("delete_word", HapticGroup.Delete, Family.Texture),
    DeleteNothing("delete_nothing", HapticGroup.Delete, Family.No),

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
    CursorEnd("cursor_end", HapticGroup.Space, Family.No),

    /** Autocorrect changed a word, or shorthand was spelled out, which goes the same way. */
    Autocorrect("autocorrect", HapticGroup.Changes, Family.Change),
    /** Backspace right after a change put back what was typed. */
    AutocorrectUndone("autocorrect_undone", HapticGroup.Changes, Family.Back),
    HeldSymbol("held_symbol", HapticGroup.Changes, Family.Change),
    /** A :name: became its emoji, or a snippet's shortcut its text. */
    Expansion("expansion", HapticGroup.Changes, Family.Change),

    BarWord("bar_word", HapticGroup.Bar, Family.Confirm),
    MenuOpen("menu_open", HapticGroup.Bar, Family.Confirm),
    MenuMark("menu_mark", HapticGroup.Bar, Family.Texture),
    WordSaved("word_saved", HapticGroup.Bar, Family.Confirm),
    WordForgotten("word_forgotten", HapticGroup.Bar, Family.Gone),
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
    GuideCelebration("guide_celebration", HapticGroup.Other, Family.Celebrate);

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
        pulses(pattern).all { it in PULSE_MILLIS } && gaps(pattern).all { it in GAP_MILLIS }

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
 * Ticks waiting to play, which a cut drops all at once. [post] runs a tick after a delay and [remove] takes one back, as a
 * Handler does. It has no Android parts, so the checks can run it with their own.
 */
class PendingTicks(private val post: (Runnable, Long) -> Unit, private val remove: (Runnable) -> Unit) {
    private val waiting = ArrayList<Runnable>()

    /** Drops any ticks still waiting, then [play]s once at each of [times], in ms from now. */
    fun schedule(times: List<Long>, play: () -> Unit) {
        cut()
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
    MasterOff("all off");

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

    /** Whether [family], lasting [millis], plays now, or why not. A message that plays is the busy one from now on. */
    fun check(family: Family, millis: Long = HapticPalette.millis(family.pattern)): HapticResult {
        val now = clock()
        if (now < busyUntil) return HapticResult.Busy
        if (family.texture) return HapticResult.Played
        if (now < quietUntil) return HapticResult.Busy
        val last = lastAt[family]
        if (last != null && now - last < REPEAT_MILLIS) return HapticResult.TooSoon
        lastAt[family] = now
        busyUntil = now + millis
        quietUntil = busyUntil + HapticPalette.MIN_GAP_MILLIS
        return HapticResult.Played
    }

    /** Whether [family], lasting [millis], plays now. */
    fun admit(family: Family, millis: Long = HapticPalette.millis(family.pattern)) = check(family, millis).played

    companion object {
        const val REPEAT_MILLIS = 300L
    }
}

/** One haptic the keyboard tried to play, for the debug Haptics lab: when (an uptime), what, its pattern as played, and the result. */
data class HapticEvent(val atMillis: Long, val haptic: Haptic, val pattern: List<Long>, val result: HapticResult)

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
 * What the cursor plays as it steps while steering. A space or a line end is a boundary between words, and every other
 * character is texture. Punctuation gets no tick of its own: it almost always sits next to a space, whose tick already marks
 * the end of the word. At the start or end of the text the cursor can't move, so it plays no, once each time it gets there.
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
    private val feeds = debug(context)

    init {
        // Only TitanKeys Debug feeds the lab. Every other build records nothing.
        if (feeds) HapticFeed.enabled = true
    }

    /** Plays [haptic], and says whether it played: false when a switch is off or the gate dropped it. */
    fun play(haptic: Haptic) = attempt(haptic).played

    /** Plays [haptic], and says what became of it. */
    fun attempt(haptic: Haptic): HapticResult {
        val pattern = pattern(context, haptic.family)
        val result = switches.stops(haptic) ?: gate.check(haptic.family, HapticPalette.millis(pattern))
        if (result.played) {
            vibrate(pattern)
            lastPlayedAt = SystemClock.uptimeMillis()
        }
        report(haptic, pattern, result)
        return result
    }

    private fun report(haptic: Haptic, pattern: List<Long>, result: HapticResult) {
        if (feeds) HapticFeed.record(HapticEvent(SystemClock.uptimeMillis(), haptic, pattern, result))
    }

    /** Plays [pattern] alone, with no switch or gate, for the lab's editor. It still needs Android's touch feedback. */
    fun preview(pattern: List<Long>) = vibrate(pattern)

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

    private fun landTicks(landings: List<Long>, tailAt: Long?, showing: () -> Boolean) =
        dotTicks.schedule(DotTicks.times(landings, tailAt)) { if (showing()) play(Haptic.DeleteDotsLand) }

    /**
     * A hold's dots at its lift: a light tick as each lands in the caret, at [landings], each only while [showing] says those
     * dots are still on the bar. The hold's last word or its no may have just played, so a tick within 35 ms of it is left out.
     */
    fun holdDots(landings: List<Long>, showing: () -> Boolean) =
        landTicks(landings, lastPlayedAt - SystemClock.uptimeMillis(), showing)

    /** Any key or touch cuts the dots at once, and with them the ticks still to come. */
    fun cutDots() = dotTicks.cut()

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

    fun move(samples: List<Sample>) {
        ticks.move(samples)?.let(::play)
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
