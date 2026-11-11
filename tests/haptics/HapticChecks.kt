package com.atlas.keyboard

import java.io.File

/**
 * The keyboard's haptics (docs/haptics.md): the rulebook's families and their patterns, the haptic that plays each one, the
 * gate that keeps a fast run calm, and the switches. These run without a phone.
 */
fun main() {
    eachHapticPlaysItsFamily()
    eachFamilyPlaysItsPattern()
    noReadyMadeEffects()
    everyGapReadsApart()
    textureIsOneShortTick()
    messagesAreRhythmsOrLongPulses()
    theRhythmsMeanTheSameEverywhere()
    aMessageCantRepeat()
    textureKeepsItsOwnPace()
    messagesBeatTexture()
    aCountedTapNeverBlocksTheGesture()
    theGateSaysWhy()
    theFeedOnlyRecordsWhenOn()
    theOwnersFourAdditions()
    nothingCutsAPlayingMessage()
    triedPatternsOnlyInDebug()
    everyHapticIsOnByDefault()
    theMasterSilencesEverything()
    oneToggleSilencesOnlyItsHaptic()
    steeringFeelsSpacesAndLetters()
    theEndsOfTheTextPlayOncePerHit()
    theLabSaysWhatEachPlays()
    theGraphDrawsEachPatternToScale()
    theLiveTimelineHasWordsForEach()
    println("Haptic checks passed")
}

private fun check(condition: Boolean, message: () -> String) {
    if (!condition) throw AssertionError(message())
}

/** The owner's table, written out again so that a change to Haptics.kt has to agree with it. */
private val table = mapOf(
    Haptic.SwipeStart to Family.Texture,
    Haptic.SwipeKey to Family.Texture,
    Haptic.TapKey to Family.Texture,
    Haptic.DeleteFlick to Family.GoneFlick,
    Haptic.DeleteWord to Family.Texture,
    Haptic.DeleteNothing to Family.No,
    Haptic.SpaceTap to Family.Texture,
    Haptic.Steer to Family.Confirm,
    Haptic.SteerResumed to Family.Texture,
    Haptic.Select to Family.Lock,
    Haptic.WordPicked to Family.Lock,
    Haptic.TwoTapsAbove to Family.No,
    Haptic.CursorLetter to Family.Texture,
    Haptic.CursorSpace to Family.Boundary,
    Haptic.SelectionWord to Family.Boundary,
    Haptic.CursorEnd to Family.No,
    Haptic.Autocorrect to Family.Change,
    Haptic.AutocorrectUndone to Family.Back,
    Haptic.HeldSymbol to Family.Change,
    Haptic.Expansion to Family.Change,
    Haptic.BarWord to Family.Confirm,
    Haptic.MenuOpen to Family.Confirm,
    Haptic.MenuMark to Family.Texture,
    Haptic.WordSaved to Family.Confirm,
    Haptic.WordForgotten to Family.Gone,
    Haptic.ForgetUndone to Family.Back,
    Haptic.WordNotSaved to Family.No,
    Haptic.ClipOffer to Family.Confirm,
    Haptic.ExactKey to Family.Confirm,
    Haptic.ExactLock to Family.Lock,
    Haptic.ExactChip to Family.Confirm,
    Haptic.ExactUnlock to Family.Release,
    Haptic.ModifierLocked to Family.Lock,
    Haptic.ModifierUnlocked to Family.Release,
    Haptic.PanelMark to Family.Texture,
    Haptic.PanelPage to Family.Boundary,
    Haptic.PanelGroup to Family.Boundary,
    Haptic.QuickSetting to Family.Confirm,
    Haptic.ClipPasted to Family.Confirm,
    Haptic.ClipPinned to Family.Lock,
    Haptic.ClipUnpinned to Family.Release,
    Haptic.ClipboardCleared to Family.Gone,
    Haptic.BugRecording to Family.Confirm,
    Haptic.GuideCelebration to Family.Celebrate,
)

/** The rulebook's patterns: on and off in ms, starting with on. */
private val patterns = mapOf(
    Family.Texture to listOf(17L),
    Family.Boundary to listOf(30L),
    Family.Confirm to listOf(15L, 40L, 35L),
    Family.Change to listOf(20L, 35L, 20L),
    Family.Lock to listOf(30L, 50L, 30L),
    Family.Release to listOf(30L),
    // The owner's table said 50/30/20. The gap is 35 so it keeps the rule that each pulse reads on its own.
    Family.GoneFlick to listOf(50L, 35L, 20L),
    Family.Gone to listOf(60L),
    Family.Back to listOf(35L, 40L, 15L),
    Family.No to listOf(25L, 40L, 25L, 40L, 25L),
    Family.Celebrate to listOf(15L, 40L, 22L, 40L, 35L),
)

private fun eachHapticPlaysItsFamily() {
    check(table.keys == Haptic.entries.toSet()) { "The table misses ${Haptic.entries - table.keys}" }
    for ((haptic, family) in table) check(haptic.family == family) { "$haptic plays ${haptic.family}, not $family" }
    val ids = Haptic.entries.map { it.id }
    check(ids.toSet().size == ids.size) { "Each haptic has its own id: $ids" }
    for (haptic in Haptic.entries) check(Haptic.of(haptic.id) == haptic) { "$haptic is found by its id" }
    for (family in Family.entries) check(Family.of(family.id) == family) { "$family is found by its id" }
}

private fun eachFamilyPlaysItsPattern() {
    check(patterns.keys == Family.entries.toSet()) { "Eleven families: ${Family.entries}" }
    for ((family, pattern) in patterns) check(family.pattern == pattern) { "$family plays ${family.pattern}, not $pattern" }
    // And so each haptic plays its family's pattern exactly.
    for ((haptic, family) in table) check(HapticPalette.pattern(haptic.family, false, null) == patterns.getValue(family)) {
        "$haptic plays its family's pattern"
    }
}

/** The phone's ready-made effects all felt generic, so nothing uses one: no effect ids, no predefined effect, no composition. */
private fun noReadyMadeEffects() {
    val source = File("android/kotlin/com/atlas/keyboard/Haptics.kt").readText()
    for (word in listOf("createPredefined", "EFFECT_", "startComposition", "PRIMITIVE_", "areAllEffectsSupported"))
        check(word !in source) { "Haptics.kt uses $word" }
    check("VibrationEffect.createWaveform(" in source) { "Patterns play as our own waveforms" }
    // The Titan has no strength control, so there are no strengths to set.
    check("hasAmplitudeControl" !in source) { "Strength is length only" }
}

private fun everyGapReadsApart() {
    for (family in Family.entries) for (gap in HapticPalette.gaps(family.pattern))
        check(gap >= 35) { "$family has a $gap ms gap, under 35 ms" }
    check(HapticPalette.MIN_GAP_MILLIS == 35L) { "The least gap is 35 ms" }
    check(HapticPalette.GAP_MILLIS.first >= 35) { "The lab's gap sliders can't go under 35 ms" }
    for (family in Family.entries) check(family.pattern.size % 2 == 1) { "$family starts and ends with a pulse" }
}

private fun textureIsOneShortTick() {
    check(Family.Texture.pattern == listOf(HapticPalette.TEXTURE_MILLIS)) { "Texture is one pulse" }
    check(HapticPalette.TEXTURE_MILLIS == 17L) { "Texture is 17 ms, as the owner liked it" }
    check(Family.Texture.texture && Family.Boundary.texture) { "Texture and boundaries keep their own pace" }
    check(Family.Boundary.pattern.single() > HapticPalette.TEXTURE_MILLIS) { "A boundary is stronger than a texture tick" }
    check(Family.Boundary.pattern.single() == 30L) { "A boundary is about 30 ms" }
}

private fun messagesAreRhythmsOrLongPulses() {
    for (family in Family.entries.filter { !it.texture }) {
        val pulses = HapticPalette.pulses(family.pattern)
        check(pulses.size >= 2 || pulses.single() >= HapticPalette.MESSAGE_PULSE_MILLIS) {
            "$family is a message: two pulses or more, or one of 30 ms or more"
        }
    }
}

/** Meaning comes from rhythm: rising is done, falling is back, equal is locked, and so on. */
private fun theRhythmsMeanTheSameEverywhere() {
    fun pulses(family: Family) = HapticPalette.pulses(family.pattern)
    check(pulses(Family.Confirm).let { it.size == 2 && it[0] < it[1] }) { "Confirm rises" }
    for (falling in listOf(Family.Back, Family.GoneFlick))
        check(pulses(falling).let { it.size == 2 && it[0] > it[1] }) { "$falling falls" }
    check(pulses(Family.Lock).let { it.size == 2 && it[0] == it[1] }) { "Lock is two equal pulses" }
    check(pulses(Family.Change).let { it.size == 2 && it[0] == it[1] && it[0] < pulses(Family.Lock)[0] }) {
        "Change is two equal pulses, lighter than a lock"
    }
    check(pulses(Family.Gone).size == 1 && pulses(Family.Gone)[0] > pulses(Family.Release)[0]) { "Gone is one long pulse" }
    check(pulses(Family.No).let { it.size == 3 && it.toSet().size == 1 }) { "No is three equal pulses" }
    check(pulses(Family.Celebrate).zipWithNext().all { (a, b) -> a < b } && pulses(Family.Celebrate).size == 3) { "Celebrate rises over three" }
    // Every pattern feels different from every other.
    check(Family.entries.map { it.pattern }.toSet().size == Family.entries.size - 1) { "Only Boundary and Release share a pattern" }
    check(Family.Boundary.pattern == Family.Release.pattern) { "A release is a single firm pulse, like a boundary, as the table has it" }
}

/** A clock the checks move by hand. */
private class Clock(var now: Long = 1000)

private fun millis(family: Family) = HapticPalette.millis(family.pattern)

/** The repeat rule is by family: two of the same rhythm that close would run together into one longer pattern. */
private fun aMessageCantRepeat() {
    val clock = Clock()
    val gate = HapticGate { clock.now }
    check(gate.admit(Family.Confirm)) { "The first confirm plays" }
    clock.now += 299
    check(!gate.admit(Family.Confirm)) { "Another confirm at 299 ms doesn't" }
    clock.now += 1
    check(gate.admit(Family.Confirm)) { "At 300 ms it plays again" }

    // Two haptics of the same family count as one: a word tapped right after a setting switched is the same rhythm.
    clock.now += 1000
    check(gate.admit(Family.Confirm)) { "A setting switched" }
    clock.now += 150
    check(!gate.admit(Haptic.BarWord.family)) { "A word tapped 150 ms later is the same family, so it doesn't play" }
    check(gate.admit(Family.Lock)) { "Another family plays, once the first has ended" }

    // A dropped play doesn't count as played: the 300 ms run from the last one that played.
    clock.now += 1000
    check(gate.admit(Family.Confirm)) { "A quick setting" }
    clock.now += 150
    check(!gate.admit(Family.Confirm)) { "Another 150 ms later drops" }
    clock.now += 150
    check(gate.admit(Family.Confirm)) { "A third 300 ms after the first plays" }
}

/** Texture is exempt: a tick per key crossed, every 40 ms, and a message never stops it for long. */
private fun textureKeepsItsOwnPace() {
    val clock = Clock()
    val gate = HapticGate { clock.now }
    repeat(10) {
        check(gate.admit(Family.Texture)) { "Texture tick ${it + 1} plays, 40 ms after the last" }
        clock.now += 40
    }
    repeat(3) {
        check(gate.admit(Family.Boundary)) { "A boundary plays 40 ms after the last" }
        clock.now += 40
    }
    // The repeat rule is for messages only: texture straight after texture always plays.
    check(gate.admit(Family.Texture) && gate.admit(Family.Texture)) { "Texture isn't blocked by the repeat rule" }
    check(gate.admit(Family.Confirm)) { "Texture never blocks a message" }
}

private fun messagesBeatTexture() {
    val clock = Clock()
    val gate = HapticGate { clock.now }
    check(gate.admit(Family.Texture)) { "A tick plays" }
    check(gate.admit(Family.No)) { "A message plays right on top of it" }
    clock.now += 20
    check(!gate.admit(Family.Texture)) { "Texture waits while the message plays" }
    clock.now += millis(Family.No) - 20
    check(gate.admit(Family.Texture)) { "Then it plays again at once" }
}

private fun nothingCutsAPlayingMessage() {
    val clock = Clock()
    val gate = HapticGate { clock.now }
    check(gate.admit(Family.Lock)) { "A lock plays" }
    clock.now += millis(Family.Lock) - 1
    check(!gate.admit(Family.No)) { "No doesn't cut a lock that is still playing" }
    clock.now += 1
    check(!gate.admit(Family.No)) { "The next message waits a gap after the lock ends" }
    clock.now += HapticPalette.MIN_GAP_MILLIS
    check(gate.admit(Family.No)) { "Then it plays" }
    // A longer message, like the flick timed to its dots, is busy for all of it.
    clock.now += 1000
    check(gate.admit(Family.GoneFlick, 190)) { "The flick plays, busy until its tail ends" }
    clock.now += 189
    check(!gate.admit(Family.Texture)) { "Nothing cuts its tail" }
}

/** The lab's tried patterns: only a debug build plays one, and only one that fits. */
private fun triedPatternsOnlyInDebug() {
    check(HapticPalette.pattern(Family.Confirm, debug = false, stored = "20,40,60") == listOf(15L, 40L, 35L)) {
        "A build that isn't debug ignores a tried pattern"
    }
    check(HapticPalette.pattern(Family.Confirm, debug = true, stored = "20,40,60") == listOf(20L, 40L, 60L)) {
        "The debug build plays a tried pattern"
    }
    check(HapticPalette.pattern(Family.Confirm, debug = true, stored = null) == Family.Confirm.pattern) { "With none tried, the rulebook's" }
    check(HapticPalette.pattern(Family.Confirm, debug = true, stored = "20,30,60") == Family.Confirm.pattern) { "A gap under 35 ms is ignored" }
    check(HapticPalette.pattern(Family.Confirm, debug = true, stored = "20,40") == Family.Confirm.pattern) { "A pattern of another shape is ignored" }
    check(HapticPalette.pattern(Family.Texture, debug = true, stored = "200") == Family.Texture.pattern) { "A pulse over 120 ms is ignored" }
    check(HapticPalette.pattern(Family.Texture, debug = true, stored = "x") == Family.Texture.pattern) { "Nonsense is ignored" }
    check(HapticPalette.parse(HapticPalette.format(listOf(15L, 40L, 35L))) == listOf(15L, 40L, 35L)) { "A pattern is stored and read back" }
    for (family in Family.entries) check(HapticPalette.fits(family, family.pattern)) { "$family's own pattern fits the sliders" }
}

private fun switches(stored: Map<String, Boolean>) = HapticSwitches { key, default -> stored[key] ?: default }

private fun everyHapticIsOnByDefault() {
    val switches = switches(emptyMap())
    check(switches.master) { "All haptics is on by default" }
    for (haptic in Haptic.entries) check(switches.allows(haptic)) { "$haptic is on by default" }
}

private fun theMasterSilencesEverything() {
    val switches = switches(mapOf(HapticSwitches.MASTER to false))
    for (haptic in Haptic.entries) check(!switches.allows(haptic)) { "$haptic plays with All haptics off" }
    check(!switches.allows(Haptic.GuideCelebration)) { "The guide's celebration is silent too" }
    check(switches.on(Haptic.SwipeKey)) { "Each switch keeps its own setting while the master is off" }
}

private fun oneToggleSilencesOnlyItsHaptic() {
    val switches = switches(mapOf(Haptic.CursorLetter.key to false))
    check(!switches.allows(Haptic.CursorLetter)) { "A haptic that is off doesn't play" }
    for (haptic in Haptic.entries - Haptic.CursorLetter) check(switches.allows(haptic)) { "$haptic still plays" }
    check(Haptic.CursorLetter.key == "haptic_cursor_letter") { "A switch is stored as haptic_ and its id" }
}

/** Steering: a space or a line end is a boundary, every other character is texture. */
private fun steeringFeelsSpacesAndLetters() {
    val steps = CursorSteps()
    check(steps.step(' ') == Haptic.CursorSpace) { "A space is a boundary" }
    check(steps.step('\n') == Haptic.CursorSpace) { "A line end is a boundary" }
    check(steps.step('a') == Haptic.CursorLetter) { "A letter is texture" }
    check(steps.step('.') == Haptic.CursorLetter) { "Punctuation is a letter's tick: none of its own" }
}

private fun theEndsOfTheTextPlayOncePerHit() {
    val steps = CursorSteps()
    check(steps.step('a') == Haptic.CursorLetter) { "A letter on the way" }
    check(steps.step(null) == Haptic.CursorEnd) { "The end of the text is no" }
    check(steps.step(null) == null) { "Pushing on at the end plays nothing more" }
    check(steps.step(null) == null) { "Still nothing" }
    check(steps.step('b') == Haptic.CursorLetter) { "Moving away is a letter again" }
    check(steps.step(null) == Haptic.CursorEnd) { "Coming back to an end plays again" }
    steps.reset()
    check(steps.step(null) == Haptic.CursorEnd) { "A new steer that starts at an end plays it" }
}

/** The debug Haptics lab's words for each pattern. */
private fun theLabSaysWhatEachPlays() {
    check(HapticPalette.describe(Family.Confirm.pattern) == "15 on, 40 off, 35 on") { HapticPalette.describe(Family.Confirm.pattern) }
    check(HapticPalette.describe(Family.Texture.pattern) == "17 on") { HapticPalette.describe(Family.Texture.pattern) }
    check(HapticPalette.describe(Family.No.pattern) == "25 on, 40 off, 25 on, 40 off, 25 on") { HapticPalette.describe(Family.No.pattern) }
    for (family in Family.entries) check(HapticTimeline.meaning(family).isNotEmpty()) { "$family says what it means" }
}

/** The lab's graph: when the motor is on for each pattern, to scale. */
private fun theGraphDrawsEachPatternToScale() {
    check(HapticTimeline.buzzes(Family.Texture.pattern) == listOf(Buzz(0, 17))) { "Texture is one bar" }
    // The gaps come after each pulse, so No's pulses start 65 ms apart.
    check(HapticTimeline.buzzes(Family.No.pattern) == listOf(Buzz(0, 25), Buzz(65, 90), Buzz(130, 155))) {
        "No draws ${HapticTimeline.buzzes(Family.No.pattern)}"
    }
    check(HapticTimeline.buzzes(Family.Confirm.pattern, atMillis = 40, dropped = true) == listOf(Buzz(40, 55, true), Buzz(95, 130, true))) {
        "A dropped play is drawn hollow where it would have been"
    }
    for (family in Family.entries)
        check(HapticTimeline.buzzes(family.pattern).last().toMillis <= HapticTimeline.SPAN_MILLIS) { "$family fits in its row" }
    // A longer tried pattern widens every row, in steps of 50 ms.
    check(HapticTimeline.span(150) == 200L && HapticTimeline.span(201) == 250L && HapticTimeline.span(1400) == 1400L) { "Spans" }
    check(HapticTimeline.grid(200) == 20L && HapticTimeline.grid(1400) == 100L) { "Gridlines" }
}

/** Each haptic has a few plain words on what set it off, for the live timeline. */
private fun theLiveTimelineHasWordsForEach() {
    val words = Haptic.entries.map { HapticTimeline.trigger(it) }
    check(words.all { it.isNotEmpty() && it.length <= 32 }) { "Each trigger is short: $words" }
    check(words.toSet().size == words.size) { "Each trigger is its own" }
    check(HapticTimeline.trigger(Haptic.Autocorrect) == "Space fixed a word") { "Autocorrect's trigger" }
    check(HapticTimeline.LIVE_MILLIS == 5000L && HapticTimeline.RECENT == 15) { "The last 5 s, and the last 15" }
    check(HapticFeed.KEEP >= HapticTimeline.RECENT) { "The feed keeps enough for the list" }
}

/** A counted Space tap is texture, so it never holds up the steer or select that starts right after it. */
private fun aCountedTapNeverBlocksTheGesture() {
    check(Haptic.SpaceTap.family == Family.Texture) { "A counted tap is a texture tick" }
    check(Haptic.SpaceTap.group == HapticGroup.Space) { "It's in the Space gestures group" }
    for (start in listOf(Haptic.Steer, Haptic.Select, Haptic.WordPicked)) {
        val clock = Clock()
        val gate = HapticGate { clock.now }
        check(gate.admit(Haptic.SpaceTap.family)) { "Tap 1 ticks" }
        clock.now += 90
        check(gate.admit(Haptic.SpaceTap.family)) { "Tap 2 ticks" }
        check(gate.check(start.family) == HapticResult.Played) { "$start plays right on top of a counted tap" }
    }
}

private fun theGateSaysWhy() {
    val clock = Clock()
    val gate = HapticGate { clock.now }
    check(gate.check(Family.Change) == HapticResult.Played) { "It plays" }
    clock.now += 10
    check(gate.check(Family.No) == HapticResult.Busy) { "Another while it plays is busy" }
    check(gate.check(Family.Texture) == HapticResult.Busy) { "Texture while it plays is busy too" }
    clock.now += 200
    check(gate.check(Family.Change) == HapticResult.TooSoon) { "The same family within 300 ms is too soon" }
    check(switches(mapOf(HapticSwitches.MASTER to false)).stops(Haptic.SwipeKey) == HapticResult.MasterOff) { "All off" }
    check(switches(mapOf(Haptic.SwipeKey.key to false)).stops(Haptic.SwipeKey) == HapticResult.SwitchedOff) { "Its switch off" }
    check(switches(emptyMap()).stops(Haptic.SwipeKey) == null) { "Nothing stops it" }
    check(HapticResult.entries.filter { it.played } == listOf(HapticResult.Played)) { "Only Played played" }
    check(HapticResult.TooSoon.words == "too soon" && HapticResult.SwitchedOff.words == "switched off") { "The lab's words" }
}

/** The lab's feed: off unless the debug build turns it on, and it keeps only the last few. */
private fun theFeedOnlyRecordsWhenOn() {
    val event = HapticEvent(1, Haptic.SwipeKey, Family.Texture.pattern, HapticResult.Played)
    HapticFeed.enabled = false
    var heard = 0
    HapticFeed.listen { heard++ }
    HapticFeed.record(event)
    check(HapticFeed.recent().isEmpty() && heard == 0) { "Off, nothing is recorded or sent" }
    HapticFeed.enabled = true
    repeat(HapticFeed.KEEP + 5) { HapticFeed.record(event.copy(atMillis = it.toLong())) }
    check(heard == HapticFeed.KEEP + 5) { "The listener hears each one" }
    check(HapticFeed.recent().size == HapticFeed.KEEP && HapticFeed.recent().first().atMillis == 5L) { "It keeps the last ${HapticFeed.KEEP}" }
    HapticFeed.stop()
    HapticFeed.record(event)
    check(heard == HapticFeed.KEEP + 5) { "Once stopped, the listener hears no more" }
    HapticFeed.enabled = false
}

/** Four from the audit the owner approved: a steer picked up after a slip, exact typing's chips, and two taps above Space in the guide. */
private fun theOwnersFourAdditions() {
    check(Haptic.SteerResumed.group == HapticGroup.Space && Haptic.TwoTapsAbove.group == HapticGroup.Space) { "Space gestures" }
    check(Haptic.ExactChip.group == HapticGroup.Bar && Haptic.ExactUnlock.group == HapticGroup.Bar) { "Exact typing's bar" }
    check(Haptic.chipTapped(wasLocked = false) == Haptic.ExactChip) { "A tap on a chip that isn't locked is confirm" }
    check(Haptic.chipTapped(wasLocked = true) == Haptic.ExactUnlock) { "A tap that unlocks a locked chip is release" }
    check(Haptic.ExactLock.family == Family.Lock) { "The long press still locks" }
    // A resumed steer is texture, so it never holds up the steering's own messages, and steering's ticks keep their pace after it.
    val clock = Clock()
    val gate = HapticGate { clock.now }
    check(gate.admit(Haptic.SteerResumed.family)) { "The tick on landing again" }
    check(gate.admit(Haptic.CursorLetter.family)) { "The next letter still ticks" }
    check(gate.check(Haptic.CursorEnd.family) == HapticResult.Played) { "And an end still plays its no" }
    // Its plain words for the lab.
    for (haptic in listOf(Haptic.SteerResumed, Haptic.TwoTapsAbove, Haptic.ExactChip, Haptic.ExactUnlock))
        check(HapticTimeline.trigger(haptic).isNotEmpty()) { "$haptic has a trigger" }
}
