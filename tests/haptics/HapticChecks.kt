package com.atlas.keyboard

import java.io.File

/**
 * The keyboard's haptics (docs/haptics.md): the rulebook's families and their patterns, the haptic that plays each one, the
 * gate that keeps a fast run calm, and the switches. These run without a phone.
 */
fun main() {
    changeIsAQuietSwell()
    eachHapticPlaysItsFamily()
    eachFamilyPlaysItsPattern()
    noReadyMadeEffects()
    everyGapReadsApart()
    textureIsOneShortTick()
    messagesAreRhythmsOrLongPulses()
    theRhythmsMeanTheSameEverywhere()
    everyFamilyHasItsOwnPattern()
    theWordMenuHasItsOwnFeels()
    aMessageCantRepeat()
    textureKeepsItsOwnPace()
    messagesBeatTexture()
    aCountedTapNeverBlocksTheGesture()
    theGateSaysWhy()
    theFeedOnlyRecordsWhenOn()
    theOwnersFourAdditions()
    tapsThatFindNothingSayNo()
    theDotsTickAsTheyLand()
    aCutDropsTheTicksToCome()
    aHoldsDotsTickAtTheLift()
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
    aHeldWordHitsLikeAFlick()
    theHitShortensAsTheHoldSpeedsUp()
    theHitKeepsItsQuietAndItsFloor()
    everyHeldWordPlays()
    aHitEvery80MsPassesTheGate()
    theWallsQuietSilencesTheHit()
    theFadeIsGone()
    aNewHoldCountsFromOneAgain()
    theTimelineShowsEachHeldWord()
    aHoldAndItsDotsAreOneSequence()
    aDotNearAWordIsSkipped()
    theHoldFollowsItsPause()
    theLabShowsTheDotsOfAHold()
    theHoldPausesFor400()
    aWallBouncesAndSettles()
    allThreeGesturesHitAWallTheSameWay()
    aFlicksDotsWaitAsTheLabTries()
    shortcutsStayQuietButACopyOfNothing()
    aDeleteWallIsTheSameAndClean()
    nothingFoundIsANoAndRunningOutIsAWall()
    switchesLockOnAndReleaseOff()
    aFlickCrunches()
    thePlaygroundsFiveMoments()
    eachMomentPlaysOnceABurst()
    theSwitchSilencesThePlayground()
    aPressInInkClicksUnlessItsControlMeansSomething()
    println("Haptic checks passed")
}

/**
 * Every press in Ink's pages plays Press, a light tick, unless its control plays its own haptic in time: a switch only locks,
 * never clicks then locks (owner, 2026-11-13: "Add haptics to every touch screen press").
 */
private fun aPressInInkClicksUnlessItsControlMeansSomething() {
    check(Haptic.AppPress.family == Family.Texture && Haptic.AppPress.group == HapticGroup.App) { "Press is a texture tick in the app's group" }
    var now = 0L
    val waiting = HashMap<Runnable, Long>()
    var played = 0
    val press = InkPress({ run, millis -> waiting[run] = now + millis }, { waiting.remove(it) }) { played++ }
    fun advance(millis: Long) {
        now += millis
        waiting.filterValues { it <= now }.keys.forEach { waiting.remove(it)?.let { _ -> it.run() } }
    }
    // A plain press: Press plays once its wait is over, not before.
    press.pressed()
    advance(InkPress.WAIT_MILLIS - 1)
    check(played == 0) { "Press waits for a meaning" }
    advance(1)
    check(played == 1) { "then plays" }
    // A switch: its Lock comes in time, and no Press plays.
    press.pressed()
    advance(20)
    press.meaning()
    advance(200)
    check(played == 1) { "A control's own haptic takes the place of Press" }
    // Two quick presses: one Press each, as texture may repeat. The second starts its own wait.
    press.pressed()
    advance(InkPress.WAIT_MILLIS)
    press.pressed()
    advance(InkPress.WAIT_MILLIS)
    check(played == 3) { "Each press clicks: $played" }
    check(InkPress.WAIT_MILLIS in 30L..80L) { "Press is near the press itself: ${InkPress.WAIT_MILLIS} ms" }
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
    Haptic.DeleteDotsLand to Family.Texture,
    Haptic.DeleteWord to Family.Hit,
    Haptic.DeleteCrunch to Family.Crunch,
    Haptic.DeleteBreak to Family.Boundary,
    Haptic.DeleteNothing to Family.No,
    Haptic.DeleteRanOut to Family.Wall,
    Haptic.DeleteZone to Family.Boundary,
    Haptic.SpaceTap to Family.Texture,
    Haptic.Steer to Family.Confirm,
    Haptic.SteerResumed to Family.Texture,
    Haptic.Select to Family.Lock,
    Haptic.WordPicked to Family.Lock,
    Haptic.NothingToPick to Family.No,
    Haptic.CursorLetter to Family.Texture,
    Haptic.CursorSpace to Family.Boundary,
    Haptic.SelectionWord to Family.Boundary,
    Haptic.CursorEnd to Family.Wall,
    Haptic.SelectionEnd to Family.Wall,
    Haptic.Autocorrect to Family.Change,
    Haptic.AutocorrectUndone to Family.Back,
    Haptic.HeldSymbol to Family.Change,
    Haptic.Expansion to Family.Change,
    Haptic.CopyNothing to Family.No,
    Haptic.BarWord to Family.Confirm,
    Haptic.MenuOpen to Family.Confirm,
    Haptic.MenuMark to Family.Texture,
    Haptic.MenuPress to Family.Texture,
    Haptic.MenuClosed to Family.Release,
    Haptic.WordSaved to Family.Saved,
    Haptic.WordBlocked to Family.Removed,
    Haptic.WordFavourited to Family.Favourite,
    Haptic.WordUnfavourited to Family.Unfavourite,
    Haptic.WordUnsaved to Family.Unsaved,
    Haptic.MenuSpent to Family.No,
    Haptic.WordUnblocked to Family.Unblocked,
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
    Haptic.QuickSettingOn to Family.Lock,
    Haptic.QuickSettingOff to Family.Release,
    Haptic.ClipPasted to Family.Confirm,
    Haptic.ClipPinned to Family.Lock,
    Haptic.ClipUnpinned to Family.Release,
    Haptic.ClipboardCleared to Family.Gone,
    Haptic.BugRecordingStarted to Family.Lock,
    Haptic.BugRecordingStopped to Family.Release,
    Haptic.GuideCelebration to Family.Celebrate,
    Haptic.GuideBlocked to Family.No,
    Haptic.GuideHint to Family.Change,
    // TitanKeys' own screens (docs/haptics.md): any plain press clicks, on locks, off lets go, a step ticks, a pick is done, a delete
    // is gone, a refusal is no.
    Haptic.AppPress to Family.Texture,
    Haptic.AppSwitchOn to Family.Lock,
    Haptic.AppSwitchOff to Family.Release,
    Haptic.AppStep to Family.Texture,
    Haptic.AppPicked to Family.Confirm,
    Haptic.AppDeleted to Family.Gone,
    Haptic.AppRefused to Family.No,
    // The playground's five moments (owner, 2026-11-13).
    Haptic.PlaygroundConfetti to Family.Celebrate,
    Haptic.PlaygroundRain to Family.No,
    Haptic.PlaygroundShake to Family.Texture,
    Haptic.PlaygroundLightning to Family.Hit,
    Haptic.PlaygroundSprout to Family.Change,
)

/** The rulebook's patterns: on and off in ms, starting with on. */
private val patterns = mapOf(
    Family.Texture to listOf(17L),
    Family.Boundary to listOf(30L),
    Family.Confirm to listOf(15L, 40L, 35L),
    Family.Change to listOf(12L, 35L, 22L, 35L, 12L),
    Family.Lock to listOf(30L, 50L, 30L),
    Family.Release to listOf(40L),
    // The owner's table said 50/30/20. The gap is 35 so it keeps the rule that each pulse reads on its own.
    Family.GoneFlick to listOf(50L, 35L, 20L),
    Family.Gone to listOf(60L),
    Family.Back to listOf(35L, 40L, 15L),
    Family.No to listOf(25L, 40L, 25L, 40L, 25L),
    Family.Wall to listOf(40L, 35L, 20L, 35L, 12L),
    Family.Crunch to listOf(9L, 12L, 9L),
    // The delete flick's first pulse, alone.
    Family.Hit to listOf(50L),
    Family.Celebrate to listOf(15L, 40L, 22L, 40L, 35L),
    Family.Saved to listOf(20L, 40L, 20L, 40L, 50L),
    Family.Removed to listOf(50L, 40L, 20L, 40L, 20L),
    Family.Favourite to listOf(20L, 40L, 20L, 40L, 80L),
    Family.Unfavourite to listOf(30L, 40L, 20L, 40L, 12L),
    Family.Unsaved to listOf(40L, 40L, 20L, 40L, 15L),
    Family.Unblocked to listOf(15L, 40L, 15L, 40L, 30L),
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
    check(patterns.keys == Family.entries.toSet()) { "The table misses ${Family.entries - patterns.keys}" }
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
    // A crunch's grain is the one gap under 35 ms, on purpose: its two pulses are felt as one gritty tick.
    for (family in Family.entries - Family.Crunch) for (gap in HapticPalette.gaps(family.pattern))
        check(gap >= 35) { "$family has a $gap ms gap, under 35 ms" }
    check(HapticPalette.gaps(Family.Crunch.pattern).all { it in HapticPalette.GRAIN_GAP_MILLIS }) { "A crunch's gap is a grain's" }
    check(HapticPalette.gapRange(Family.Texture) == HapticPalette.GAP_MILLIS) { "Every other family keeps rule 4 in the lab" }
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
    check(pulses(Family.Change).let { it.size == 3 && it[0] == it[2] && it[1] > it[0] && it.max() < pulses(Family.Lock)[0] }) {
        "Change is a quiet swell: light, fuller, light, all lighter than a lock"
    }
    check(pulses(Family.Gone).size == 1 && pulses(Family.Gone)[0] > pulses(Family.Release)[0]) { "Gone is one long pulse" }
    check(pulses(Family.No).let { it.size == 3 && it.toSet().size == 1 }) { "No is three equal pulses" }
    check(pulses(Family.Celebrate).zipWithNext().all { (a, b) -> a < b } && pulses(Family.Celebrate).size == 3) { "Celebrate rises over three" }
    // Every pattern feels different from every other: one meaning, one pattern (bug hunt 2.1, #35: Boundary and Release were both
    // a single 30 ms pulse).
    for (family in Family.entries) for (other in Family.entries.filter { it != family })
        check(family.pattern != other.pattern) { "$family and $other share the pattern ${family.pattern}" }
    check(pulses(Family.Release).size == 1 && pulses(Family.Release)[0] > pulses(Family.Boundary)[0]) {
        "A release is one pulse, firmer than a boundary's tick"
    }
    // Saved rises at the end and Removed falls from the start, each over three pulses.
    check(pulses(Family.Saved).let { it.size == 3 && it[0] == it[1] && it[1] < it[2] }) { "Saved is short, short, long" }
    check(pulses(Family.Removed) == pulses(Family.Saved).reversed()) { "Removed is Saved's mirror: long, short, short" }
}

/**
 * Every family has its own pattern, so no two meanings feel the same. Release shares Boundary's single pulse on purpose, but
 * Boundary is texture, so among messages each pattern, and each list of pulses without its gaps, is its own.
 */
private fun everyFamilyHasItsOwnPattern() {
    val messages = Family.entries.filter { !it.texture }
    check(messages.map { it.pattern }.toSet().size == messages.size) { "Each message has its own pattern: ${messages.map { it.pattern }}" }
    check(messages.map { HapticPalette.pulses(it.pattern) }.toSet().size == messages.size) { "Each message has its own pulses" }
    for (family in ladder) for (other in Family.entries.filter { it != family })
        check(HapticPalette.pulses(family.pattern) != HapticPalette.pulses(other.pattern)) { "$family feels different from $other" }
}

/** The word menu's Save and Block each have a family of their own, used by nothing else, and so does Unblock. */
private fun theWordMenuHasItsOwnFeels() {
    check(Haptic.WordSaved.family == Family.Saved && Haptic.WordBlocked.family == Family.Removed) { "Save is Saved, Block is Removed" }
    check(Haptic.entries.filter { it.family == Family.Saved } == listOf(Haptic.WordSaved)) { "Only Save plays Saved" }
    check(Haptic.entries.filter { it.family == Family.Removed } == listOf(Haptic.WordBlocked)) { "Only Block plays Removed" }
    check(Haptic.entries.filter { it.family == Family.Unblocked } == listOf(Haptic.WordUnblocked)) { "Only Unblock plays Unblocked" }
    check(Haptic.MenuOpen.family != Haptic.WordSaved.family) { "Save never shares the menu opening's family, so the repeat rule can't drop it" }
    check(Haptic.WordNotSaved.family == Family.No) { "A failed save is no" }
    // A press ticks at once, before its step's own haptic (owner, 2026-11-13): texture, so the step's message still plays on its
    // frame, even right after the tick, and nothing waits for it.
    check(Haptic.MenuPress.family == Family.Texture) { "A menu press is a light tick" }
    for (step in listOf(Haptic.WordSaved, Haptic.WordUnsaved, Haptic.WordFavourited, Haptic.WordUnfavourited, Haptic.WordBlocked, Haptic.WordUnblocked)) {
        val clock = Clock()
        val gate = HapticGate { clock.now }
        check(gate.admit(Haptic.MenuPress.family)) { "The press ticks" }
        clock.now += 1
        check(gate.admit(step.family)) { "$step plays on its frame right after the press's tick" }
    }
    check(Haptic.entries.none { it.id == "forget_undone" || it.id == "word_forgotten" }) { "Forget and its Undo are gone (owner, 2026-11-13)" }
    for (family in ladder) check(HapticPalette.gaps(family.pattern).all { it == 40L }) { "$family's gaps are 40 ms" }
    // Favourite is Saved with a stronger last hit; Unfavourite is never Removed's fall.
    check(Haptic.WordFavourited.family == Family.Favourite && Haptic.WordUnfavourited.family == Family.Unfavourite) { "Favourite and Unfavourite have their own" }
    check(Haptic.entries.filter { it.family == Family.Favourite } == listOf(Haptic.WordFavourited)) { "Only Favourite plays Favourite" }
    check(Haptic.entries.filter { it.family == Family.Unfavourite } == listOf(Haptic.WordUnfavourited)) { "Only Unfavourite plays Unfavourite" }
    check(Haptic.entries.filter { it.family == Family.Unsaved } == listOf(Haptic.WordUnsaved)) { "Only Unsave plays Unsaved" }
    val saved = HapticPalette.pulses(Family.Saved.pattern)
    val favourite = HapticPalette.pulses(Family.Favourite.pattern)
    check(favourite.dropLast(1) == saved.dropLast(1) && favourite.last() > saved.last()) { "Favourite is Saved ending stronger: $favourite" }
    theLadderRisesAndFalls()
    // Saved, then Favourite straight after, as I pressed twice: both play.
    val pressed = Clock()
    val twice = HapticGate { pressed.now }
    check(twice.admit(Haptic.WordSaved.family)) { "Save plays" }
    pressed.now += millis(Haptic.WordSaved.family) + HapticPalette.MIN_GAP_MILLIS
    check(twice.admit(Haptic.WordFavourited.family)) { "Favourite right after Save plays" }
    // Opened, then saved straight away: Save still plays.
    val clock = Clock()
    val gate = HapticGate { clock.now }
    check(gate.admit(Haptic.MenuOpen.family)) { "The menu opens" }
    clock.now += millis(Haptic.MenuOpen.family) + HapticPalette.MIN_GAP_MILLIS
    check(gate.admit(Haptic.WordSaved.family)) { "Save right after the menu opens plays" }
}

/** The word menu's ladder: its steps' families, and each one's step. */
private val ladder = listOf(Family.Unblocked, Family.Saved, Family.Removed, Family.Favourite, Family.Unfavourite, Family.Unsaved)

/**
 * The word menu's haptics are one system (owner, 2026-11-12): a step up rises, ending on its strongest pulse, and Favourite's last
 * hit is the strongest; a step down falls, starting on its strongest pulse, and the further down, the heavier. The spent heart
 * says no. Each button plays the family of how far it moves the word: see [MenuKeys.move].
 */
private fun theLadderRisesAndFalls() {
    val family = mapOf(MenuKeys.Button.Unblock to Haptic.WordUnblocked, MenuKeys.Button.Save to Haptic.WordSaved,
        MenuKeys.Button.Favourite to Haptic.WordFavourited, MenuKeys.Button.Favourited to Haptic.MenuSpent,
        MenuKeys.Button.Unfavourite to Haptic.WordUnfavourited, MenuKeys.Button.Unsave to Haptic.WordUnsaved,
        MenuKeys.Button.Block to Haptic.WordBlocked)
    check(family.keys == MenuKeys.Button.entries.toSet()) { "Every menu button has its haptic" }
    val up = family.filterKeys { MenuKeys.move(it) > 0 }.entries.sortedBy { MenuKeys.move(it.key) }.map { HapticPalette.pulses(it.value.family.pattern) }
    val down = family.filterKeys { MenuKeys.move(it) < 0 }.entries.sortedBy { -MenuKeys.move(it.key) }.map { HapticPalette.pulses(it.value.family.pattern) }
    for (pulses in up) check(pulses.last() == pulses.max() && pulses.last() > pulses.first()) { "A step up rises: $pulses" }
    for (pulses in down) check(pulses.first() == pulses.max() && pulses.first() > pulses.last()) { "A step down falls: $pulses" }
    check(up.last().last() == ladder.maxOf { HapticPalette.pulses(it.pattern).max() }) { "Favourite ends on the menu's strongest hit" }
    check(up.zipWithNext().all { (a, b) -> b.last() > a.last() }) { "Each step up ends stronger: $up" }
    check(down.zipWithNext().all { (a, b) -> b.first() > a.first() && b.sum() > a.sum() }) { "Each step further down is heavier: $down" }
    check(Haptic.MenuSpent.family == Family.No) { "The spent heart says no" }
    check(Haptic.entries.map { it.id }.containsAll(listOf("word_unsaved", "menu_spent"))) { "Unsave and the spent heart have their own switches" }
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
    check(steps.step(null) == Haptic.CursorEnd) { "The end of the text is a wall" }
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
    check(gate.check(Family.Confirm) == HapticResult.Played) { "It plays" }
    clock.now += 10
    check(gate.check(Family.No) == HapticResult.Busy) { "Another while it plays is busy" }
    check(gate.check(Family.Texture) == HapticResult.Busy) { "Texture while it plays is busy too" }
    clock.now += 200
    check(gate.check(Family.Confirm) == HapticResult.TooSoon) { "The same family within 300 ms is too soon" }
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
    check(Haptic.SteerResumed.group == HapticGroup.Space && Haptic.NothingToPick.group == HapticGroup.Space) { "Space gestures" }
    check(Haptic.ExactChip.group == HapticGroup.Bar && Haptic.ExactUnlock.group == HapticGroup.Bar) { "Exact typing's bar" }
    check(Haptic.chipTapped(wasLocked = false) == Haptic.ExactChip) { "A tap on a chip that isn't locked is confirm" }
    check(Haptic.chipTapped(wasLocked = true) == Haptic.ExactUnlock) { "A tap that unlocks a locked chip is release" }
    check(Haptic.ExactLock.family == Family.Lock) { "The long press still locks" }
    // A resumed steer is texture, so it never holds up the steering's own messages, and steering's ticks keep their pace after it.
    val clock = Clock()
    val gate = HapticGate { clock.now }
    check(gate.admit(Haptic.SteerResumed.family)) { "The tick on landing again" }
    check(gate.admit(Haptic.CursorLetter.family)) { "The next letter still ticks" }
    check(gate.check(Haptic.CursorEnd.family) == HapticResult.Played) { "And an end still plays its wall" }
    // Its plain words for the lab.
    for (haptic in listOf(Haptic.SteerResumed, Haptic.NothingToPick, Haptic.ExactChip, Haptic.ExactUnlock))
        check(HapticTimeline.trigger(haptic).isNotEmpty()) { "$haptic has a trigger" }
}

/**
 * A flick's dots, as the bar shows them for a word of [letters] at animation speed [scale], and when each lands. With the
 * speed-up off, the plain rule: the gesture display checks the speed-up.
 */
private fun landings(letters: Int, scale: Float = 1f): List<Long> {
    val display = GestureDisplay().apply { speedUp = false }
    display.wordDeleted(1000, letters)
    return display.dotLandings(scale)
}

/** The into-caret phase at [scale], in real ms after the flick: from the end of the hold until the last dot's way ends. */
private fun phase(dots: Int, scale: Float = 1f): ClosedFloatingPointRange<Float> {
    val start = (GestureDisplay.FLICK_IN + GestureDisplay.FLICK_HOLD) * scale
    return start..start + GestureDisplay.outMillis(dots, scale) + 1
}

private fun about(actual: Float, expected: Float) = kotlin.math.abs(actual - expected) < .01f

private fun theDotsTickAsTheyLand() {
    check(Haptic.DeleteDotsLand.group == HapticGroup.Delete && Haptic.DeleteDotsLand.family == Family.Texture) { "Delete, texture" }
    // The way in: the owner's 450 ms for a few dots, stretched to 30 ms a dot for more, and kept at 30 ms a dot when faster
    // animations would squeeze it.
    check(about(GestureDisplay.outMillis(1, 1f), 450f)) { "One dot: 450 ms" }
    check(about(GestureDisplay.outMillis(8, 1f), 450f)) { "Eight dots just fit in 450 ms" }
    check(GestureDisplay.dotStepMillis(8, 1f) >= GestureDisplay.DOT_LAND_GAP) { "and land at least 30 ms apart" }
    check(GestureDisplay.outMillis(9, 1f) > 450f && GestureDisplay.dotStepMillis(9, 1f) == 30f) { "Nine dots stretch it" }
    check(about(GestureDisplay.outMillis(30, 1f), 202.5f + 900f)) { "Thirty dots land over 900 ms: ${GestureDisplay.outMillis(30, 1f)}" }
    check(about(GestureDisplay.outMillis(1, .5f), 225f)) { "Half speed halves a short way in" }
    check(GestureDisplay.dotStepMillis(5, .5f) == 30f) { "but never below 30 ms a dot" }

    val tail = 105L
    for ((letters, scale) in listOf(1 to 1f, 3 to 1f, 6 to 1f, 8 to 1f, 9 to 1f, 30 to 1f, 40 to 1f, 6 to .5f, 30 to .5f, 30 to 2f)) {
        val lands = landings(letters, scale)
        val dots = minOf(letters, GestureDisplay.MAX_DOTS)
        val ticks = DotTicks.times(lands, tail)
        check(ticks == lands && ticks.size == dots) { "$letters letters at $scale: a tick for each of $dots dots: $ticks" }
        check(ticks.all { it.toFloat() in phase(dots, scale) }) { "$letters letters at $scale: every tick lands in the phase: $ticks" }
        check(ticks.zipWithNext().all { (a, b) -> b - a >= 30 }) { "$letters letters at $scale: 30 ms apart or more: $ticks" }
    }
    // One dot lands as its way ends, 450 ms into the way in, so at 120 + 200 + 202.5 ms.
    check(landings(1) == listOf(523L)) { "One dot: ${landings(1)}" }
    // Three keep the owner's timing: 82.5 ms apart.
    check(landings(3) == listOf(523L, 606L, 688L)) { "Three dots: ${landings(3)}" }
    // Thirty land 30 ms apart, the last about 900 ms after the first.
    val thirty = landings(30)
    check(thirty.first() == 523L && thirty.last() == 523L + 29 * 30) { "Thirty dots: ${thirty.first()} to ${thirty.last()}" }
    // With no motion the dots don't move, so nothing ticks.
    check(landings(5, 0f).isEmpty() && DotTicks.times(landings(5, 0f), tail).isEmpty()) { "None with animations off" }
    // A tick within 35 ms of the flick's tail is left out, and only those.
    val six = landings(6)
    check(DotTicks.times(six, six[2]) == six - six[2]) { "The tick on the tail goes" }
    check(DotTicks.times(six, six[2] + 20) == six - six[2] - six[3]) { "Both within 35 ms of it go" }
    check(DotTicks.times(six, six[0] - 35) == six) { "One 35 ms away stays" }
    // The ticks are texture: they never hold up or cut the flick's message, and a busy flick drops a tick instead.
    val clock = Clock()
    val gate = HapticGate { clock.now }
    check(gate.admit(Family.GoneFlick, 140)) { "The flick plays" }
    clock.now += 100
    check(gate.check(Family.Texture) == HapticResult.Busy) { "A tick while the flick plays drops, so it never cuts it" }
    clock.now += 40
    check(gate.admit(Family.Texture)) { "Once the flick ends, a tick plays" }
}

/** Any key or touch cuts the dots, and no tick plays after the cut. */
private fun aCutDropsTheTicksToCome() {
    val posted = ArrayList<Pair<Runnable, Long>>()
    val ticks = PendingTicks({ tick, at -> posted += tick to at }, { tick -> posted.removeAll { it.first === tick } })
    var played = 0
    ticks.schedule(listOf(523L, 553L, 583L)) { played++ }
    check(posted.map { it.second } == listOf(523L, 553L, 583L) && ticks.pending == 3) { "Three ticks wait: $posted" }
    posted.removeAt(0).first.run()
    check(played == 1 && ticks.pending == 2) { "The first lands" }
    val left = posted.toList()
    ticks.cut()
    check(posted.isEmpty() && ticks.pending == 0) { "A cut takes back the rest" }
    for ((tick) in left) if (posted.any { it.first === tick }) tick.run()
    check(played == 1) { "No tick plays after the cut" }
    // A new flick drops the last one's ticks first.
    ticks.schedule(listOf(10L)) { played++ }
    ticks.schedule(listOf(20L)) { played++ }
    check(posted.map { it.second } == listOf(20L)) { "Only the new flick's ticks wait: $posted" }
    // A flick that joins dots still playing adds its ticks to theirs.
    ticks.add(listOf(70L, 100L)) { played++ }
    check(posted.map { it.second } == listOf(20L, 70L, 100L) && ticks.pending == 3) { "The stream's ticks all wait: $posted" }
    ticks.cut()
    check(posted.isEmpty()) { "and a cut drops them all" }
}

/** A long delete's lines turn into dots at the lift, which tick as each lands, the same way a flick's do. */
private fun aHoldsDotsTickAtTheLift() {
    val held = GestureDisplay().apply { speedUp = false }
    held.deleteHeld(200, 1000)
    held.wordDeleted(1100, 4)
    held.wordDeleted(1300, 3)
    check(held.deleteEnded(1400)) { "The lift starts the dots" }
    check(!held.deleteEnded(1500)) { "A second lift starts nothing" }
    check(held.dotsAt == 1400L) { "The dots started at the lift" }
    val lands = held.dotLandings(1f)
    // The lines shrink into the dots over 240 ms, stay 200 ms, then each dot takes its 202.5 ms way, 30 ms or more apart.
    val first = (GestureDisplay.MORPH_IN + GestureDisplay.FLICK_HOLD + GestureDisplay.DOT_TRAVEL).let { kotlin.math.round(it + .001f).toLong() }
    check(lands.size == 7 && lands.first() == first) { "A tick for each of the seven dots, the first at $first: $lands" }
    check(lands.zipWithNext().all { (a, b) -> b - a >= 30 }) { "30 ms apart or more: $lands" }
    check(held.dotLandings(0f).isEmpty()) { "None with animations off" }
    // They're scheduled at the landing times, and a cut drops them all.
    val posted = ArrayList<Pair<Runnable, Long>>()
    val ticks = PendingTicks({ tick, at -> posted += tick to at }, { tick -> posted.removeAll { it.first === tick } })
    var played = 0
    ticks.schedule(DotTicks.times(lands, -20)) { played++ }
    check(posted.map { it.second } == lands) { "One tick per dot, at the landings: $posted" }
    val left = posted.toList()
    ticks.cut()
    for ((tick) in left) if (posted.any { it.first === tick }) tick.run()
    check(played == 0 && posted.isEmpty()) { "None after the cut" }
    // The hold's last word or its no may have just played. A first dot within 35 ms of it is left out.
    check(DotTicks.times(listOf(30L, 60L, 90L), 0) == listOf(60L, 90L)) { "A tick 30 ms after the last haptic goes, one 60 ms after stays" }
    check(DotTicks.times(listOf(30L, 60L), -10) == listOf(30L, 60L)) { "One 40 ms after stays" }
    check(DotTicks.times(lands, -20) == lands) { "At normal speed the dots land long after it" }
}

/** Taps that find no word, or steering with no text, play Nothing to pick: no, as a word that can't be saved does, never a lock. */
private fun tapsThatFindNothingSayNo() {
    check(Haptic.NothingToPick.id == "nothing_to_pick") { "Its own switch" }
    check(Haptic.NothingToPick.group == HapticGroup.Space) { "in the Space gestures group" }
    check(Haptic.NothingToPick.family == Family.No && Haptic.WordNotSaved.family == Family.No) { "The same no as a failed save" }
    check(Haptic.NothingToPick.family.pattern == listOf(25L, 40L, 25L, 40L, 25L)) { "25, 40, 25, 40, 25" }
    check(HapticTimeline.trigger(Haptic.NothingToPick) == "Taps found no word") { "The lab's trigger text" }
    check(Haptic.of("two_taps_above") == null) { "Two taps above Space is now part of Nothing to pick" }
    check(Haptic.picked(found = true) == Haptic.WordPicked) { "A tap that picks a word locks" }
    check(Haptic.picked(found = false) == Haptic.NothingToPick) { "A tap that finds none says no" }
    check(Haptic.registered(words = false, nothing = false) == Haptic.Steer) { "A steer confirms" }
    check(Haptic.registered(words = true, nothing = false) == Haptic.Select) { "A select locks" }
    for (words in listOf(false, true))
        check(Haptic.registered(words, nothing = true) == Haptic.NothingToPick) { "Nothing to work on says no, never a lock, words=$words" }
    // Nothing to pick is a message, so it is never cut short or held up by the glow's ticks before it.
    val clock = Clock()
    val gate = HapticGate { clock.now }
    check(gate.admit(Haptic.SpaceTap.family)) { "A counted tap ticks" }
    check(gate.check(Haptic.NothingToPick.family) == HapticResult.Played) { "and the no after it still plays" }
}

/** A hold's repeat at [pace], as DeleteSwipe.kt works it out: 350 ms a word at pace 0, down to 80 at pace 1. */
private fun repeatAt(pace: Float): Long {
    val slow = deleteSwipeTuning.getValue("deleteRepeatSlowMillis") as Long
    val fast = deleteSwipeTuning.getValue("deleteRepeatFastMillis") as Long
    return Math.round(slow + (fast - slow) * pace.toDouble())
}

private val hit = Family.Hit.pattern.single()

/**
 * Each word a held delete takes is a hit as firm as a flick's (owner, 2026-11-12): one pulse, the delete flick's first. The crunch
 * stays only for the keys a flick crosses.
 */
private fun aHeldWordHitsLikeAFlick() {
    check(Family.Hit.pattern == listOf(Family.GoneFlick.pattern.first())) { "Hit is the flick's first pulse, alone: ${Family.Hit.pattern}" }
    check(hit == 50L) { "50 ms" }
    check(Haptic.DeleteWord.family == Family.Hit) { "A held word hits" }
    check(Haptic.entries.filter { it.family == Family.Crunch } == listOf(Haptic.DeleteCrunch)) { "Only a flick's crossed keys crunch" }
    check(HoldHit.millis(hit, 0f, repeatAt(0f)) == hit) { "At pace 0 it's the whole hit" }
    check(repeatAt(0f) == REPEAT_SLOW_MILLIS && repeatAt(1f) == 80L) { "350 ms a word down to 80" }
    check(Family.Hit.texture) { "Texture, with its own pace, as the words come fast" }
    check(HapticPalette.fits(Family.Hit, Family.Hit.pattern)) { "The lab's editor can edit it" }
    check(HapticTimeline.meaning(Family.Hit).isNotEmpty()) { "The lab says what it means" }
    check(HoldHit.describe(hit) == "50 ms, down to 30 as the hold speeds up") { HoldHit.describe(hit) }
}

/** The hit gets shorter as the hold's pace speeds up, so hits don't blur at the top pace: 50, 40 at half, 30 at the top. */
private fun theHitShortensAsTheHoldSpeedsUp() {
    check(HoldHit.millis(hit, .5f, repeatAt(.5f)) == 40L) { "Half pace: ${HoldHit.millis(hit, .5f, repeatAt(.5f))}" }
    check(HoldHit.millis(hit, 1f, repeatAt(1f)) == HapticPalette.MESSAGE_PULSE_MILLIS) { "The top pace: 30 ms" }
    check(repeatAt(1f) - HoldHit.millis(hit, 1f, repeatAt(1f)) == 50L) { "30 on, 50 off at the top pace" }
    val paces = (0..20).map { it / 20f }
    val lengths = paces.map { HoldHit.millis(hit, it, repeatAt(it)) }
    check(lengths.zipWithNext().all { (a, b) -> b <= a }) { "Never longer as the pace goes up: $lengths" }
    check(lengths.first() > lengths.last()) { "and shorter at the top: $lengths" }
    check(HoldHit.millis(hit, -1f, 350) == hit && HoldHit.millis(hit, 2f, 80) == 30L) { "A pace outside 0 to 1 is held to it" }
}

/** For every hit the lab's slider allows, every pace and repeat: 35 ms of quiet before the next word, and never under 30 ms. */
private fun theHitKeepsItsQuietAndItsFloor() {
    for (pulse in HapticPalette.PULSE_MILLIS) for (step in 0..20) {
        val pace = step / 20f
        for (repeat in listOf(repeatAt(pace), 65L, 80L, 100L, 150L, 350L)) {
            val millis = HoldHit.millis(pulse, pace, repeat)
            check(millis >= HapticPalette.MESSAGE_PULSE_MILLIS) { "$pulse ms at $pace, $repeat ms a word: $millis is under 30" }
            check(millis <= repeat - HapticPalette.MIN_GAP_MILLIS) { "$pulse ms at $pace, $repeat ms a word: $millis leaves no 35 ms" }
            check(millis <= maxOf(pulse, HapticPalette.MESSAGE_PULSE_MILLIS)) { "$pulse ms at $pace: $millis is longer than the hit" }
        }
    }
}

/** Every word of a hold plays: nothing thins them, and how many have gone never shortens them. Only the pace does. */
private fun everyHeldWordPlays() {
    val count = HeldDeleteCount()
    for (pace in listOf(0f, .5f, 1f)) {
        val lengths = (1..60).map { count.next(); HoldHit.millis(hit, pace, repeatAt(pace)) }
        check(lengths.toSet().size == 1) { "At pace $pace every word is the same: $lengths" }
    }
    check(HapticResult.entries.none { it.name == "Thinned" }) { "Nothing is thinned any more" }
}

/** A hit every 80 ms at the top pace, or every 350 ms, plays every time: it's texture, so the 300 ms repeat rule never drops it. */
private fun aHitEvery80MsPassesTheGate() {
    for (pace in listOf(0f, .5f, 1f)) {
        val clock = Clock()
        val gate = HapticGate { clock.now }
        val repeat = repeatAt(pace)
        for (word in 1..40) {
            check(gate.check(Family.Hit, HoldHit.millis(hit, pace, repeat)) == HapticResult.Played) { "Pace $pace: word $word plays" }
            clock.now += repeat
        }
    }
    // A message still plays right on top of a hit.
    val clock = Clock()
    val gate = HapticGate { clock.now }
    check(gate.admit(Family.Hit, 30)) { "A hit" }
    clock.now += 5
    check(gate.check(Family.Confirm) == HapticResult.Played) { "and a message on top of it" }
}

/** A wall's quiet silences the hit too, so the wall at the start of the text is felt clean. */
private fun theWallsQuietSilencesTheHit() {
    val clock = Clock(1_000)
    val gate = HapticGate { clock.now }
    check(gate.check(Family.Hit, 30) == HapticResult.Played) { "A hit plays" }
    val wall = millis(Family.Wall)
    val wait = gate.reserveWall(wall)
    check(wait == 30 + HapticGate.WALL_CLEAR_MILLIS) { "The wall waits until 100 ms after the hit ends: $wait" }
    clock.now += 80
    check(gate.check(Family.Hit, 30) == HapticResult.Busy) { "No hit while the wall waits" }
    clock.now = 1_000 + wait
    check(gate.check(Family.Wall) == HapticResult.Played) { "The wall plays" }
    clock.now += wall + HapticGate.WALL_CLEAR_MILLIS - 1
    check(gate.check(Family.Hit, 30) == HapticResult.Busy) { "No hit until 100 ms after it" }
    clock.now += 1
    check(gate.check(Family.Hit, 30) == HapticResult.Played) { "then hits go on" }
}

/** The held delete fade is retired: nothing reads its settings, so an old stored one does nothing. */
private fun theFadeIsGone() {
    for (name in listOf("Haptics.kt", "HapticsLab.kt", "HapticsLabActivity.kt", "HapticTimeline.kt", "AtlasIme.kt")) {
        val source = File("android/kotlin/com/atlas/keyboard/$name").readText()
        for (word in listOf("debug_pattern_held_delete_fade", "debug_held_delete_fade_on", "heldDeleteFading", "fun fadeOn("))
            check(word !in source) { "$name still has $word" }
    }
    val ime = File("android/kotlin/com/atlas/keyboard/AtlasIme.kt").readText()
    check("haptics.heldDeleteWord(word, deleteHold.pace, deleteHold.repeatMillis)" in ime) { "A held word hits at the hold's own pace" }
}

/** A new hold, or one that ended or was cut, counts from word 1 again, for the lab's numbers. */
private fun aNewHoldCountsFromOneAgain() {
    val count = HeldDeleteCount()
    repeat(12) { count.next() }
    check(count.word == 12) { "Twelve words went" }
    count.reset()
    check(count.next() == 1) { "The next hold starts at word 1" }
}

/** The live timeline shows each held delete hit with its word and the length it played. */
private fun theTimelineShowsEachHeldWord() {
    val played = HapticEvent(1, Haptic.DeleteWord, listOf(40), HapticResult.Played, word = 5)
    check(HapticTimeline.title(played, "Each word while you hold") == "Held delete word 5 · Hit 40 ms") { HapticTimeline.title(played, "") }
    check(HapticTimeline.outcome(played) == HapticTimeline.trigger(Haptic.DeleteWord)) { "It says what set it off" }
    val other = HapticEvent(1, Haptic.SwipeKey, Family.Texture.pattern, HapticResult.Busy)
    check(HapticTimeline.title(other, "Swipe key") == "Swipe key · Texture") { "Every other haptic keeps its name" }
    check(HapticTimeline.outcome(other) == "dropped: busy") { "and a dropped one its reason" }
}

/** The lab's hold: the flick and 10 words 350 ms apart, a lift, and the dots of their letters at normal speed. */
private fun playPlan(pause: Long = deleteHoldPauseMillis(), scale: Float = 1f, speedUp: Boolean = false, pace: Float = 0f) =
    HoldTicks.plan(HoldTicks.PLAY_WORDS, pause, repeatAt(pace), pace, HoldTicks.PLAY_LIFT_MILLIS, hit, HapticPalette.TEXTURE_MILLIS,
        HapticPalette.millis(Family.GoneFlick.pattern), GestureDisplay.holdLandings(HoldTicks.playLetters(), scale, speedUp))

/** No two ticks of a word and a dot within 35 ms, and the dots no closer than their own 30 ms. */
private fun checkApart(ticks: List<HoldTicks.Tick>, what: String) {
    val played = ticks.filter { it.plays }
    check(played.zipWithNext().all { (a, b) -> b.at >= a.at }) { "$what: in time order" }
    for (word in played.filter { it.kind != HoldTicks.Kind.Dot }) for (dot in played.filter { it.kind == HoldTicks.Kind.Dot })
        check(kotlin.math.abs(word.at - dot.at) >= HapticPalette.MIN_GAP_MILLIS) { "$what: word ${word.number} at ${word.at} and dot ${dot.number} at ${dot.at}" }
    for ((a, b) in played.filter { it.kind == HoldTicks.Kind.Dot }.zipWithNext())
        check(b.at - a.at >= GestureDisplay.DOT_LAND_GAP.toLong()) { "$what: dots ${a.number} and ${b.number} are ${b.at - a.at} ms apart" }
    for ((a, b) in played.filter { it.kind != HoldTicks.Kind.Dot }.zipWithNext())
        check(b.at - a.at >= HapticPalette.MIN_GAP_MILLIS) { "$what: words ${a.number} and ${b.number} are ${b.at - a.at} ms apart" }
}

/**
 * A hold of 10 words and the dots at its lift make one list: the flick, a hit for every word at its pace, and a tick for every
 * dot. Nothing within 35 ms of anything else of the other kind.
 */
private fun aHoldAndItsDotsAreOneSequence() {
    for (scale in listOf(1f, .5f, .25f)) for (speedUp in listOf(false, true)) for (pace in listOf(0f, 1f)) {
        val plan = playPlan(scale = scale, speedUp = speedUp, pace = pace)
        val repeat = repeatAt(pace)
        val what = "scale $scale, speed-up $speedUp, $repeat ms a word"
        checkApart(plan.ticks, what)
        val flick = plan.ticks.first()
        check(flick.kind == HoldTicks.Kind.Flick && flick.at == 0L && flick.plays) { "$what: the hold starts with its flick at 0" }
        // Every word in order plays its hit, at the length its pace gives.
        val words = plan.ticks.filter { it.kind == HoldTicks.Kind.Word }
        check(words.map { it.number } == (1..HoldTicks.PLAY_WORDS).toList()) { "$what: every word: ${words.map { it.number }}" }
        check(words.all { it.plays && it.millis == HoldHit.millis(hit, pace, repeat) }) { "$what: every word hits: $words" }
        check(words.all { it.at == it.number * repeat }) { "$what: a word every $repeat ms from the hold's start" }
        // Every dot is there, after the lift, and each one not near a word ticks.
        val landings = GestureDisplay.holdLandings(HoldTicks.playLetters(), scale, speedUp)
        val dots = plan.ticks.filter { it.kind == HoldTicks.Kind.Dot }
        check(dots.size == landings.size && dots.map { it.at } == landings.map { plan.liftAt + it }) { "$what: a tick per dot: $dots" }
        // Each word gets its share of about 30 dots, one at least, so all 11 words show.
        check(dots.size in GestureDisplay.MAX_DOTS - 2..GestureDisplay.MAX_DOTS + 2 || speedUp) { "$what: the 11 words' 41 letters share about 30 dots: ${dots.size}" }
        check(dots.all { it.at > plan.liftAt }) { "$what: the dots land after the lift" }
        check(dots.all { it.plays }) { "$what: none is near a word, as they land after the lift: $dots" }
    }
}

/** The rule: a dot within 35 ms of a word's hit is skipped, the word plays, and every other dot still ticks. */
private fun aDotNearAWordIsSkipped() {
    val words = listOf(HoldTicks.Tick(0, HoldTicks.Kind.Flick, 0, 105), HoldTicks.Tick(350, HoldTicks.Kind.Word, 1, 50),
        HoldTicks.Tick(700, HoldTicks.Kind.Word, 2, 50))
    val merged = HoldTicks.merge(words, listOf(640, 670, 700, 735, 765, 1040, 1070))
    checkApart(merged, "made up")
    val dots = merged.filter { it.kind == HoldTicks.Kind.Dot }
    check(dots.filter { !it.plays }.map { it.at } == listOf(670L, 700L)) { "The two dots within 35 ms of word 2 are skipped: $dots" }
    check(dots.filter { !it.plays }.all { it.skipped == HoldTicks.NEAR_WORD }) { "with the reason" }
    check(dots.filter { it.plays }.map { it.at } == listOf(640L, 735L, 765L, 1040L, 1070L)) { "The rest tick, 35 ms or more from it: $dots" }
    check(merged.filter { it.kind != HoldTicks.Kind.Dot } == words) { "The words are untouched: every hit plays" }
    check(dots.map { it.number } == (1..7).toList()) { "Each dot keeps its number" }
    check(merged.map { it.at } == merged.map { it.at }.sorted()) { "One list in time order" }
    check(merged.indexOfFirst { it.at == 700L && it.kind == HoldTicks.Kind.Word } < merged.indexOfFirst { it.at == 700L && it.kind == HoldTicks.Kind.Dot }) {
        "At the same time, the word comes first"
    }
    // Exactly 35 ms away still ticks.
    check(HoldTicks.dots(listOf(735), listOf(700)).single().plays) { "35 ms apart is apart" }
    check(!HoldTicks.dots(listOf(734), listOf(700)).single().plays) { "34 ms isn't" }
    // At the real lift: the last haptic just before it, as a no at the start of the text.
    check(HoldTicks.dots(listOf(30, 60, 90), listOf(0)).map { it.plays } == listOf(false, true, true)) { "The first dot near the no is skipped" }
    // The lab shows a skipped dot as skipped, and why.
    val skipped = HapticEvent(1, Haptic.DeleteDotsLand, listOf(17), HapticResult.NearWord, dot = 3, dots = 12)
    check(HapticTimeline.title(skipped, "") == "Hold's dot 3 of 12 · Texture 17 ms") { HapticTimeline.title(skipped, "") }
    check(HapticTimeline.outcome(skipped) == "skipped: within 35 ms of a word's tick") { HapticTimeline.outcome(skipped) }
    check(HapticTimeline.dotLabel(3, HapticResult.NearWord) == "3 skipped: near a word") { HapticTimeline.dotLabel(3, HapticResult.NearWord) }
    check(HapticTimeline.dotLabel(4, HapticResult.Played) == "4") { "A dot that ticks shows its number" }
    check(!HapticResult.NearWord.played) { "A skipped dot doesn't play" }
}

/**
 * The lab's hold is timed from its start, when the flick takes the first word. The pause before it is the hold's own, from
 * DeleteSwipe.kt, so a shorter pause moves the whole hold sooner after the finger stops, and changes nothing after its start.
 */
private fun theHoldFollowsItsPause() {
    check(playPlan().pauseMillis == deleteHoldPauseMillis()) { "The lab reads the hold's own pause" }
    val now = playPlan(pause = 400)
    val shorter = playPlan(pause = 100)
    check(now.ticks == shorter.ticks && now.liftAt == shorter.liftAt) { "From the hold's start, nothing changes" }
    check(now.ticks.zip(shorter.ticks).all { (a, b) -> now.fromStill(a) - shorter.fromStill(b) == 300L }) {
        "From when the finger stopped, every tick, word and dot, comes 300 ms sooner"
    }
    check(shorter.fromStill(shorter.ticks.first()) == 100L) { "The flick goes as the pause ends" }
    check(now.endsAt - shorter.endsAt == 300L) { "and the hold ends 300 ms sooner" }
}

/** The lab's strip and Play show the dots of a hold with its words, and say when there are none. */
private fun theLabShowsTheDotsOfAHold() {
    val plan = playPlan()
    val words = HapticTimeline.planWords(plan)
    val dots = plan.ticks.count { it.kind == HoldTicks.Kind.Dot }
    check(words.startsWith("Still ${deleteHoldPauseMillis()} ms, then the flick and 10 words, the lift, and $dots dots from ")) { words }
    check(words.endsWith("0 dots skipped near a word.")) { words }
    val still = playPlan(scale = 0f)
    check(still.ticks.none { it.kind == HoldTicks.Kind.Dot }) { "With animations off a hold has no dots" }
    check("animations are off" in HapticTimeline.planWords(still)) { HapticTimeline.planWords(still) }
    check(HapticTimeline.isDot(Haptic.DeleteDotsLand) && !HapticTimeline.isDot(Haptic.DeleteWord)) { "Dots have their own look" }
    val dot = HapticEvent(1, Haptic.DeleteDotsLand, listOf(17), HapticResult.Played, dot = 1, dots = 30)
    check(HapticTimeline.outcome(dot) == HapticTimeline.trigger(Haptic.DeleteDotsLand)) { "A dot that ticks says what set it off" }
    // The lab's Play plays the plan and feeds the timeline, so it plays and shows the dots with the words.
    val source = File("android/kotlin/com/atlas/keyboard/Haptics.kt").readText()
    check("fun previewHold(plan: HoldTicks.Plan)" in source) { "Play takes the whole hold" }
    val lab = File("android/kotlin/com/atlas/keyboard/HapticsLab.kt").readText()
    check("deleteHoldPauseMillis()" in lab && "GestureDisplay.holdLandings(" in lab) { "The lab's hold uses the real pause and the bar's own dots" }
}

/** The owner tried a 100 ms pause before a held delete and went back to 400 (2026-11-12), with no slider for it. */
private fun theHoldPausesFor400() {
    check(deleteHoldPauseMillis() == 400L) { "A held delete waits 400 ms: ${deleteHoldPauseMillis()}" }
    check(deleteSwipeTuning["deleteHoldMillis"] == 400L) { "The raw log has the same pause" }
}

/**
 * Wall: a dot hitting a wall with energy, a hit and rebounds that die away (owner, 2026-11-12). Three pulses, each shorter, with
 * gaps no shorter than rule 4's 35 ms, and a feel of its own.
 */
private fun aWallBouncesAndSettles() {
    val pulses = HapticPalette.pulses(Family.Wall.pattern)
    check(pulses.size == 3 && pulses.zipWithNext().all { (a, b) -> a > b }) { "Each bounce is shorter: $pulses" }
    check(pulses.first() >= HapticPalette.MESSAGE_PULSE_MILLIS) { "The hit is a firm one" }
    check(HapticPalette.gaps(Family.Wall.pattern).all { it >= HapticPalette.MIN_GAP_MILLIS }) { "Every gap reads apart" }
    check(pulses != HapticPalette.pulses(Family.No.pattern)) { "A wall is not no's three equal pulses" }
    check(HapticPalette.millis(Family.Wall.pattern) <= HapticTimeline.SPAN_MILLIS) { "It fits the lab's graph" }
    check(!Family.Wall.texture) { "It is a message" }
    check(HapticPalette.fits(Family.Wall, Family.Wall.pattern)) { "The lab's editor can edit it" }
    // The walls, and only they, play it: a gesture that did something and ran out: steering or selecting into an end of the
    // text, and a held delete that took the last word.
    check(Haptic.entries.filter { it.family == Family.Wall }.toSet() == setOf(Haptic.CursorEnd, Haptic.SelectionEnd, Haptic.DeleteRanOut)) {
        "Steering, selecting and a held delete that ran out hit the wall"
    }
    // No is for nothing happening at all: Space taps or a steer with no text, a delete with nothing to delete, a failed save, a
    // copy of nothing, the word menu's spent heart, the guide's blocks, and something the app refuses. And one joke, which the owner
    // approved (2026-11-13): the playground's confetti turning into rain is a "nope".
    check(Haptic.entries.filter { it.family == Family.No }.toSet() == setOf(Haptic.NothingToPick, Haptic.DeleteNothing,
            Haptic.WordNotSaved, Haptic.CopyNothing, Haptic.GuideBlocked, Haptic.MenuSpent, Haptic.AppRefused, Haptic.PlaygroundRain)) {
        "No is for gestures that found nothing, and the rest that did nothing"
    }
    check(HapticTimeline.meaning(Family.Wall).isNotEmpty()) { "The lab says what it means" }
    for (haptic in listOf(Haptic.SelectionEnd, Haptic.DeleteRanOut, Haptic.DeleteZone)) check(HapticTimeline.trigger(haptic).isNotEmpty()) { "$haptic" }
}

/**
 * The same rule in all three gestures: the first time a gesture meets its limit it plays the wall, then nothing while the finger
 * keeps pushing, and again only after it has moved away. Steering keeps it in [CursorSteps]; selecting and a held delete in
 * [WallHits].
 */
private fun allThreeGesturesHitAWallTheSameWay() {
    val wall = WallHits()
    check(wall.reached()) { "The first hit plays" }
    check(!wall.reached() && !wall.reached()) { "Pushing on plays nothing more" }
    wall.left()
    check(wall.reached()) { "After moving away, the next hit plays again" }
    check(!wall.reached()) { "Then quiet again" }
    // Steering, the same way, through its steps.
    val steps = CursorSteps()
    val heard = listOf<Char?>(null, null, null, 'a', null, null).map { steps.step(it) == Haptic.CursorEnd }
    check(heard == listOf(true, false, false, false, true, false)) { "Steering: once per hit, $heard" }
    // Selecting and a held delete reach their limits through their own haptics, both walls.
    check(Haptic.SelectionEnd.family == Haptic.CursorEnd.family && Haptic.DeleteRanOut.family == Haptic.CursorEnd.family) {
        "All three feel the same"
    }
    // The ticks while pushing are silent too: a wall in a run of them leaves the next hit to play only after a move.
    val gate = HapticGate { 1_000L }
    check(gate.check(Family.Wall) == HapticResult.Played) { "A wall plays through the gate" }
}

/** The lab's flick dots wait: only a single flick's dots wait the tried time, and its Play times the flick as the bar does. */
private fun aFlicksDotsWaitAsTheLabTries() {
    try {
        val (popped, before) = GestureDisplay.flickTimes(5)
        check(popped != null && before.size == 5) { "A five-letter flick pops its dots and lands five: $popped, $before" }
        GestureDisplay.triedFlickHold = 500f
        val (_, later) = GestureDisplay.flickTimes(5)
        check(later.zip(before).all { (a, b) -> a - b == 300L }) { "Waiting 300 ms longer lands every dot 300 ms later: $before, $later" }
        GestureDisplay.triedFlickHold = 0f
        val (_, sooner) = GestureDisplay.flickTimes(5)
        check(sooner.zip(before).all { (a, b) -> b - a == 200L }) { "No wait lands every dot 200 ms sooner: $before, $sooner" }
        // A lifted hold's dots keep the default wait whatever the lab tries.
        GestureDisplay.triedFlickHold = null
        val hold = GestureDisplay.holdLandings(listOf(3, 4))
        GestureDisplay.triedFlickHold = 600f
        check(GestureDisplay.holdLandings(listOf(3, 4)) == hold) { "A hold's dots wait as before" }
    } finally {
        GestureDisplay.triedFlickHold = null
    }
    check(GestureDisplay.FLICK_HOLD == 200f && GestureDisplay.flickHoldMillis() == 200f) { "The default is 200 ms" }
    check(Haptics.FLICK_HOLD_RANGE == 0L..600L) { "The lab tries 0 to 600 ms" }
}

/**
 * Shortcuts stay quiet: the owner tried a confirm for Ctrl's copy, cut, paste, undo and select all, and change for Alt's and the
 * Sym panel's symbols, and found it annoying (2026-11-12). Only Ctrl + C with nothing selected plays: a no, as it copies nothing.
 */
private fun shortcutsStayQuietButACopyOfNothing() {
    for (letter in "XVZAY") check(ShortcutFeel.of(letter, true) == null && ShortcutFeel.of(letter, false) == null) { "Ctrl + $letter is quiet" }
    check(ShortcutFeel.of('C', true) == null) { "Copy with something selected is quiet" }
    check(ShortcutFeel.of('C', false) == Haptic.CopyNothing) { "Copy with nothing selected says no" }
    check(ShortcutFeel.of('C', null) == null) { "A field that hasn't said what is selected gets no no" }
    check(Haptic.of("alt_symbol") == null && Haptic.of("panel_symbol") == null && Haptic.of("ctrl_shortcut") == null) { "Their haptics are gone" }
    check(HapticTimeline.trigger(Haptic.CopyNothing).isNotEmpty()) { "The lab has words for the no" }
}

/**
 * A delete's wall: a flick with nothing to delete and a held delete that runs out play the same wall as steering's end. It is
 * played clean: it waits until 100 ms after the last crunch or tick, nothing light plays while it plays or for 100 ms after, and
 * then texture goes on (owner, 2026-11-12).
 */
private fun aDeleteWallIsTheSameAndClean() {
    check(Haptic.DeleteRanOut.family == Haptic.CursorEnd.family) { "A held delete that ran out hits the same wall as steering" }
    check(HapticGate.WALL_CLEAR_MILLIS == 100L) { "100 ms of quiet each side" }
    val clock = Clock(1_000)
    val gate = HapticGate { clock.now }
    val crunch = millis(Family.Crunch)
    val wall = millis(Family.Wall)
    check(gate.check(Family.Crunch) == HapticResult.Played) { "A crunch plays" }
    clock.now += 10
    val wait = gate.reserveWall(wall)
    check(wait == crunch + 100 - 10) { "The wall waits until 100 ms after the crunch ends: $wait" }
    clock.now += 40
    check(gate.check(Family.Crunch) == HapticResult.Busy) { "No crunch while it waits" }
    check(gate.check(Family.Boundary) == HapticResult.Busy && gate.check(Family.Texture) == HapticResult.Busy) { "No zone tick or dot's tick either" }
    clock.now = 1_000 + crunch + 100
    check(gate.check(Family.Wall) == HapticResult.Played) { "Then the wall plays" }
    clock.now += wall + 99
    check(gate.check(Family.Texture) == HapticResult.Busy) { "Nothing light until 100 ms after it" }
    clock.now += 1
    check(gate.check(Family.Texture) == HapticResult.Played) { "then texture goes on" }
    // With nothing before it, it plays at once.
    val fresh = HapticGate { 5_000L }
    check(fresh.reserveWall(wall) == 0L) { "No wait with nothing before it" }
    // A steering wall through the gate keeps its quiet after too.
    val steer = Clock(10_000)
    val steering = HapticGate { steer.now }
    check(steering.check(Family.Wall) == HapticResult.Played) { "Steering's wall plays" }
    steer.now += wall + 50
    check(steering.check(Family.Texture) == HapticResult.Busy) { "and nothing light right after" }
}

/**
 * A crunch: two tiny pulses close together, felt as one gritty tick, for keys a clear flick crosses. It is texture, so it keeps its
 * own pace, and the lab's editor can change it within its grain's range.
 */
private fun aFlickCrunches() {
    val pulses = HapticPalette.pulses(Family.Crunch.pattern)
    check(pulses.size == 2 && pulses.all { it <= HapticPalette.TEXTURE_MILLIS }) { "Two tiny pulses: $pulses" }
    check(pulses.all { it >= 9 }) { "Each still shows on this motor" }
    check(Family.Crunch.texture) { "Texture, with its own pace" }
    check(HapticPalette.fits(Family.Crunch, Family.Crunch.pattern)) { "The lab can edit it" }
    check(HapticPalette.fits(Family.Crunch, listOf(8L, 20L, 8L)) && !HapticPalette.fits(Family.Crunch, listOf(8L, 40L, 8L))) {
        "Its gap stays a grain's"
    }
    check(Family.entries.filter { it != Family.Crunch }.none { it.pattern == Family.Crunch.pattern }) { "A pattern of its own" }
    check(Haptic.DeleteCrunch.family == Family.Crunch) { "A flick's keys crunch" }
    check(Haptic.DeleteWord.family != Family.Crunch) { "A hold's words don't" }
    check(Haptic.DeleteBreak.family == Family.Boundary) { "The break is one firm tick" }
    for (haptic in listOf(Haptic.DeleteCrunch, Haptic.DeleteBreak)) check(HapticTimeline.trigger(haptic).isNotEmpty()) { "$haptic" }
    check(HapticTimeline.meaning(Family.Crunch).isNotEmpty()) { "The lab says what a crunch means" }
}

/**
 * One rule for every gesture (owner, 2026-11-12): a gesture that finds nothing at all plays a no; one that did something and
 * then ran out plays the wall. A flick with nothing to delete, and a hold with nothing to delete, which never starts, play the
 * same no; a hold that took the last word plays the wall. Taps or a steer with no text are a no; steering or selecting into an
 * end of the text is a wall.
 */
private fun nothingFoundIsANoAndRunningOutIsAWall() {
    check(Haptic.deleteFoundNothing(tookAny = false) == Haptic.DeleteNothing && Haptic.DeleteNothing.family == Family.No) {
        "A flick or a hold with nothing to delete: a no"
    }
    check(Haptic.deleteFoundNothing(tookAny = true) == Haptic.DeleteRanOut && Haptic.DeleteRanOut.family == Family.Wall) {
        "A hold that took the last word: the wall"
    }
    check(Haptic.registered(words = false, nothing = true).family == Family.No) { "A steer in a field with no text: a no" }
    check(Haptic.registered(words = true, nothing = true).family == Family.No) { "A triple with no text: a no" }
    check(Haptic.picked(found = false).family == Family.No) { "A double or triple tap with no word: a no" }
    check(Haptic.CursorEnd.family == Family.Wall && Haptic.SelectionEnd.family == Haptic.CursorEnd.family) {
        "Steering or selecting into an end of the text: the wall"
    }
    // The no and the wall are told apart: a no is three equal pulses, a wall bounces and settles.
    check(Family.No.pattern != Family.Wall.pattern) { "They feel different" }
    // A wall still plays once per hit; a delete's no once per stroke, through the same guard, so a hold that never started is
    // quiet after its no.
    val stroke = WallHits()
    val heard = listOf(stroke.reached(), stroke.reached(), stroke.reached())
    check(heard == listOf(true, false, false)) { "A stroke with nothing to delete says no once: $heard" }
}

/** A switch says which way it went (owner, 2026-11-12): on locks in, off lets go, for quick settings and Record a bug alike. */
private fun switchesLockOnAndReleaseOff() {
    check(Haptic.quickSetting(on = true).family == Family.Lock && Haptic.quickSetting(on = false).family == Family.Release) {
        "A quick setting: Lock on, Release off"
    }
    check(Haptic.bugRecording(started = true).family == Family.Lock && Haptic.bugRecording(started = false).family == Family.Release) {
        "Record a bug: Lock as it starts, Release as it stops"
    }
    // The ones that turn on keep the old ids, so a switch already turned off in Settings stays off.
    check(Haptic.QuickSettingOn.id == "quick_setting" && Haptic.BugRecordingStarted.id == "bug_recording") { "Old ids kept" }
    check(Haptic.of("quick_setting_off") == Haptic.QuickSettingOff && Haptic.of("bug_recording_stopped") == Haptic.BugRecordingStopped) {
        "The new ones have their own ids"
    }
    // Exact typing's bar keys stay Confirm; only the lab's experiment tries texture.
    check(Haptic.ExactKey.family == Family.Confirm) { "Exact typing keys still confirm by default" }
    for (haptic in listOf(Haptic.QuickSettingOff, Haptic.BugRecordingStopped)) check(HapticTimeline.trigger(haptic).isNotEmpty()) { "$haptic" }
}

/**
 * Change is a quiet swell (owner, 2026-11-12): light, fuller, light, one soft rounded nudge for something that changed by
 * itself. It never blocks or delays another haptic and isn't held to the repeat rule, as texture; its gaps keep rule 4.
 */
private fun changeIsAQuietSwell() {
    check(Family.Change.pattern == listOf(12L, 35L, 22L, 35L, 12L)) { "12, 35, 22, 35, 12" }
    check(HapticPalette.gaps(Family.Change.pattern).all { it >= HapticPalette.MIN_GAP_MILLIS }) { "Its gaps keep rule 4" }
    check(Family.Change.texture) { "The gate takes it as texture" }
    check(Family.entries.none { it != Family.Change && it.pattern == Family.Change.pattern }) { "No other family plays it" }
    // The changes that happen by themselves play it; undoing one stays Back.
    for (haptic in listOf(Haptic.Autocorrect, Haptic.Expansion, Haptic.HeldSymbol, Haptic.GuideHint))
        check(haptic.family == Family.Change) { "$haptic swells" }
    check(Haptic.AutocorrectUndone.family == Family.Back && Family.Back.pattern == listOf(35L, 40L, 15L)) { "Undoing a fix is Back" }
    // It never holds up a message, and it can come again at once.
    val clock = Clock(1_000)
    val gate = HapticGate { clock.now }
    check(gate.check(Family.Change) == HapticResult.Played) { "It plays" }
    clock.now += 5
    check(gate.check(Family.Confirm) == HapticResult.Played) { "A message plays right on top of it" }
    clock.now += 200
    check(gate.check(Family.Change) == HapticResult.Played) { "Another swell 205 ms later plays: no repeat rule" }
    check(gate.check(Family.Change, HapticPalette.millis(Family.Change.pattern)) == HapticResult.Played) { "and again" }
}

/** The playground's show plays only five big moments, each the haptic the owner approved (2026-11-13). */
private fun thePlaygroundsFiveMoments() {
    val expected = mapOf(
        PlaygroundMoment.Confetti to Haptic.PlaygroundConfetti, PlaygroundMoment.Rain to Haptic.PlaygroundRain,
        PlaygroundMoment.Shake to Haptic.PlaygroundShake, PlaygroundMoment.Lightning to Haptic.PlaygroundLightning,
        PlaygroundMoment.Sprout to Haptic.PlaygroundSprout,
    )
    check(PlaygroundMoment.entries.size == 5) { "Five moments, no more" }
    for ((moment, haptic) in expected) check(Haptic.of(moment.haptic) == haptic) { "$moment plays $haptic" }
    check(Haptic.PlaygroundConfetti.family == Haptic.GuideCelebration.family) { "Confetti is the guide's celebration" }
    check(Haptic.PlaygroundRain.family == Family.No) { "Rain is the no, as the joke" }
    check(Haptic.PlaygroundLightning.family == Haptic.DeleteWord.family) { "Lightning is the held delete's hit" }
    check(Haptic.PlaygroundSprout.family == Family.Change) { "The plant is the quiet swell" }
    // The shake is one short run of texture ticks as it starts: under 400 ms, each gap keeping rule 4.
    val rumble = PlaygroundMoments.rumble()
    check(Haptic.PlaygroundShake.family == Family.Texture) { "The shake is built from texture" }
    check(rumble.first() == 0L && rumble.last() + HapticPalette.TEXTURE_MILLIS in 300L..400L) { "The rumble lasts 300 to 400 ms: $rumble" }
    for ((a, b) in rumble.zipWithNext()) check(b - a - HapticPalette.TEXTURE_MILLIS >= HapticPalette.MIN_GAP_MILLIS) { "Rule 4 between ticks" }
    // Nothing else in the show plays: no step, no comment.
    val guide = File("android/kotlin/com/atlas/keyboard/GuideActivity.kt").readText()
    val comment = guide.substring(guide.indexOf("private fun showComment("), guide.indexOf("private fun playMoment("))
    check("haptics." !in comment) { "A comment plays nothing" }
    val view = File("android/kotlin/com/atlas/keyboard/WpmEffectsView.kt").readText()
    check("Haptic" !in view) { "The show itself plays nothing: only the playground's moments do" }
}

/** Each moment plays once a burst, and again only after the playground has calmed down and the show reaches it anew. */
private fun eachMomentPlaysOnceABurst() {
    val moments = PlaygroundMoments()
    fun at(wpm: Int) = moments.reach(WpmEffects.at(wpm))
    check(at(30) == null) { "Nothing below the first confetti" }
    check(at(40) == PlaygroundMoment.Confetti) { "40: the first confetti pop" }
    check(at(45) == null && at(40) == null) { "Once: a bigger pop, or the same again, plays nothing" }
    check(at(100) == PlaygroundMoment.Rain && at(120) == null) { "100: the rain, once" }
    check(at(150) == PlaygroundMoment.Shake && at(170) == null) { "150: the shake, once" }
    check(at(200) == PlaygroundMoment.Sprout && at(200) == null) { "The top: the plant, once" }
    // Slowing down to nothing while still typing isn't calm: going back up plays nothing again.
    check(at(10) == null && at(40) == null && at(150) == null) { "No replay without calming down" }
    moments.calm()
    check(at(40) == PlaygroundMoment.Confetti) { "After calming down, the confetti plays again" }
    // A jump past several at once counts them all, and plays only the highest, so two never run together.
    val jump = PlaygroundMoments()
    check(jump.reach(WpmEffects.at(160)) == PlaygroundMoment.Shake) { "A jump to 160 plays the shake alone" }
    check(jump.reach(WpmEffects.at(110)) == null) { "and the rain it passed counts as reached" }
    // Each lightning flash plays, while there's a storm.
    check(moments.flash(WpmEffects.at(180)) == PlaygroundMoment.Lightning) { "A flash at 180 hits" }
    check(moments.flash(WpmEffects.at(185)) == PlaygroundMoment.Lightning) { "and the next one too" }
    check(moments.flash(WpmEffects.at(150)) == null && moments.flash(WpmEffects.at(200)) == null) { "No storm, no hit" }
    // The playground resets the moments as it calms: five quiet seconds with the number at nothing, or when it closes.
    val guide = File("android/kotlin/com/atlas/keyboard/GuideActivity.kt").readText()
    check(Regex("""showComment\(-1, force = true\)\s+// [^\n]+\n\s+moments\.calm\(\)""").containsMatchIn(guide)) { "Calm resets them" }
    check(Regex("""private fun calmWpm\(\) \{\s+moments\.calm\(\)""").containsMatchIn(guide)) { "and so does the playground closing" }
}

/** The five share one switch, Playground effects, on to start with and under All haptics. */
private fun theSwitchSilencesThePlayground() {
    val playground = listOf(Haptic.PlaygroundConfetti, Haptic.PlaygroundRain, Haptic.PlaygroundShake, Haptic.PlaygroundLightning,
        Haptic.PlaygroundSprout)
    for (haptic in playground) {
        check(haptic.switch == "playground_effects" && haptic.key == "haptic_playground_effects") { "$haptic is under Playground effects" }
        check(haptic.group == HapticGroup.App) { "$haptic is with TitanKeys' own screens" }
    }
    check(Haptic.entries.filter { it.switch == "playground_effects" } == playground) { "Only the five share it" }
    check(Haptic.entries.filter { it !in playground }.all { it.switch == it.id }) { "Every other haptic has its own switch" }
    check(playground.all { switches(emptyMap()).allows(it) }) { "On to start with" }
    val off = switches(mapOf("haptic_playground_effects" to false))
    for (haptic in playground) check(!off.allows(haptic)) { "Playground effects off silences $haptic" }
    for (haptic in Haptic.entries - playground.toSet()) check(off.allows(haptic)) { "$haptic still plays" }
    val master = switches(mapOf(HapticSwitches.MASTER to false))
    for (haptic in playground) check(!master.allows(haptic)) { "All haptics off silences $haptic" }
}
