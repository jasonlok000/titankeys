package com.atlas.keyboard

/**
 * The keyboard's haptics (docs/haptics.md): six feels with one meaning each, the action that plays each one, the rules that
 * keep a fast run calm, and the switches. These run without a phone.
 */
fun main() {
    eachHapticPlaysItsFeel()
    feelsUseTheirEffects()
    noIsThreeShortPulses()
    theGatePlaysOnlyTheFirst()
    movingKeepsItsOwnPace()
    aHigherFeelWaitsForTheFirstToFinish()
    nothingCutsAPlayingFeel()
    everyHapticIsOnByDefault()
    theMasterSilencesEverything()
    oneToggleSilencesOnlyItsHaptic()
    steeringFeelsSpacesAndLetters()
    theEndsOfTheTextPlayOncePerHit()
    theCelebrationRisesIntoAClick()
    println("Haptic checks passed")
}

private fun check(condition: Boolean, message: () -> String) {
    if (!condition) throw AssertionError(message())
}

/** The owner's table, written out again so that a change to Haptics.kt has to agree with it. */
private val table = mapOf(
    Haptic.SwipeStart to Feel.Moving,
    Haptic.SwipeKey to Feel.Moving,
    Haptic.TapKey to Feel.Step,
    Haptic.DeleteFlick to Feel.Gone,
    Haptic.DeleteWord to Feel.Step,
    Haptic.DeleteNothing to Feel.No,
    Haptic.Steer to Feel.Done,
    Haptic.Select to Feel.Locked,
    Haptic.WordPicked to Feel.Locked,
    Haptic.CursorLetter to Feel.Moving,
    Haptic.CursorSpace to Feel.Step,
    Haptic.SelectionWord to Feel.Step,
    Haptic.CursorEnd to Feel.Gone,
    Haptic.Autocorrect to Feel.Step,
    Haptic.HeldSymbol to Feel.Done,
    Haptic.Expansion to Feel.Step,
    Haptic.BarWord to Feel.Step,
    Haptic.MenuOpen to Feel.Done,
    Haptic.MenuMark to Feel.Moving,
    Haptic.WordSaved to Feel.Done,
    Haptic.WordForgotten to Feel.Gone,
    Haptic.WordNotSaved to Feel.No,
    Haptic.ClipOffer to Feel.Done,
    Haptic.ModifierLocked to Feel.Locked,
    Haptic.ModifierUnlocked to Feel.Step,
    Haptic.PanelMark to Feel.Moving,
    Haptic.PanelPage to Feel.Step,
    Haptic.PanelGroup to Feel.Step,
    Haptic.QuickSetting to Feel.Done,
    Haptic.ClipPasted to Feel.Done,
    Haptic.ClipPinned to Feel.Locked,
    Haptic.ClipUnpinned to Feel.Step,
    Haptic.ClipboardCleared to Feel.Gone,
    Haptic.BugRecording to Feel.Done,
    Haptic.GuideCelebration to Feel.Done,
)

private fun eachHapticPlaysItsFeel() {
    check(table.keys == Haptic.entries.toSet()) { "The table misses ${Haptic.entries - table.keys}" }
    for ((haptic, feel) in table) check(haptic.feel == feel) { "$haptic plays ${haptic.feel}, not $feel" }
    val ids = Haptic.entries.map { it.id }
    check(ids.toSet().size == ids.size) { "Each haptic has its own id: $ids" }
    for (haptic in Haptic.entries) check(Haptic.of(haptic.id) == haptic) { "$haptic is found by its id" }
}

private fun feelsUseTheirEffects() {
    val effects = mapOf(
        Feel.Moving to null,
        Feel.Step to null,
        Feel.Done to HapticPalette.EFFECT_CLICK,
        Feel.Locked to HapticPalette.EFFECT_DOUBLE_CLICK,
        Feel.Gone to HapticPalette.EFFECT_HEAVY_CLICK,
        Feel.No to null,
    )
    check(effects.keys == Feel.entries.toSet()) { "Six feels" }
    for ((feel, effect) in effects) check(HapticPalette.of(feel).effect == effect) { "$feel plays effect ${HapticPalette.of(feel).effect}, not $effect" }
    val effectIds = listOf(HapticPalette.EFFECT_CLICK, HapticPalette.EFFECT_DOUBLE_CLICK, HapticPalette.EFFECT_TICK,
        HapticPalette.EFFECT_HEAVY_CLICK, HapticPalette.EFFECT_TEXTURE_TICK)
    check(effectIds == listOf(0, 1, 2, 5, 21)) { "The VibrationEffect ids are Android's: $effectIds" }
    // Moving is one short buzz, shorter than the old Normal tick's 16 ms, with no ready-made effect: the Titan's last 55 ms or more.
    check(HapticPalette.of(Feel.Moving).fallback == HapticPalette.Buzzes(listOf(8L), listOf(76))) { "Moving is one 8 ms buzz" }
    check(HapticPalette.moving(12).millis == 12L) { "A tried length is one buzz of that length" }
    // A step is one buzz twice a tick's: on the Titan the order is 8, 16, then the click's 63 ms and up.
    check(HapticPalette.of(Feel.Step).fallback == HapticPalette.Buzzes(listOf(16L), listOf(140))) { "A step is one 16 ms buzz" }
    check(HapticPalette.of(Feel.Moving).millis < HapticPalette.of(Feel.Step).millis) { "A step is longer than a tick" }
    check(HapticPalette.step(10).millis == 10L) { "A tried step length is one buzz of that length" }
    check(6L in HapticPalette.MOVING_TRY_MILLIS && 12L in HapticPalette.MOVING_TRY_MILLIS) { "The debug build can try 6 to 12 ms" }
    // Phones without an effect fall back to timed buzzes, which must be distinct too.
    val fallbacks = Feel.entries.map { HapticPalette.of(it).fallback.timings }
    check(fallbacks.toSet().size == Feel.entries.size) { "Each feel's fallback must feel different: $fallbacks" }
    for (feel in Feel.entries) for (strength in HapticPalette.of(feel).fallback.strengths)
        check(strength in 0..255) { "$feel's strengths stay in the vibrator's range" }
    // Priority: gone and no beat a step, which beats moving, and clicks sit between.
    check(Feel.Moving.priority < Feel.Step.priority) { "A step beats moving" }
    check(Feel.Step.priority < Feel.Done.priority && Feel.Step.priority < Feel.Locked.priority) { "Clicks beat a step" }
    check(Feel.Done.priority < Feel.Gone.priority && Feel.Locked.priority < Feel.No.priority) { "Gone and no beat clicks" }
}

private fun noIsThreeShortPulses() {
    val no = HapticPalette.of(Feel.No).fallback
    check(no.timings == listOf(12L, 50L, 12L, 50L, 12L)) { "No is three 12 ms pulses 50 ms apart: ${no.timings}" }
    check(no.strengths.filterIndexed { index, _ -> index % 2 == 1 }.all { it == 0 }) { "The gaps are silent" }
    check(no.strengths.filterIndexed { index, _ -> index % 2 == 0 }.all { it > 0 }) { "The pulses buzz" }
}

/** A clock the checks move by hand. */
private class Clock(var now: Long = 1000)

private fun theGatePlaysOnlyTheFirst() {
    val clock = Clock()
    val gate = HapticGate { clock.now }
    check(gate.admit(Feel.Step)) { "The first feel plays" }
    clock.now += 30
    check(!gate.admit(Feel.Step)) { "An equal feel within 80 ms doesn't" }
    check(!gate.admit(Feel.Moving)) { "A lower feel within 80 ms doesn't" }
    clock.now += 49
    check(!gate.admit(Feel.Step)) { "Still within 80 ms at 79 ms" }
    clock.now += 1
    check(gate.admit(Feel.Moving)) { "After 80 ms any feel plays again" }
}

/** Moving is outside the 80 ms window: a tick per key crossed, every 40 ms, as before. It never blocks a heavier feel. */
private fun movingKeepsItsOwnPace() {
    val clock = Clock()
    val gate = HapticGate { clock.now }
    repeat(5) {
        check(gate.admit(Feel.Moving)) { "Moving tick ${it + 1} plays, 40 ms after the last" }
        clock.now += 40
    }
    check(gate.admit(Feel.Gone)) { "A heavier feel right after a moving tick still plays" }
    clock.now += HapticPalette.of(Feel.Gone).millis
    check(!gate.admit(Feel.Moving)) { "Moving waits out a heavier feel's window" }
    clock.now += HapticGate.WINDOW_MILLIS
    check(gate.admit(Feel.Moving)) { "Then it plays again" }
}

private fun aHigherFeelWaitsForTheFirstToFinish() {
    val clock = Clock()
    val gate = HapticGate { clock.now }
    check(gate.admit(Feel.Moving)) { "Moving plays" }
    clock.now += HapticPalette.of(Feel.Moving).millis
    check(gate.admit(Feel.Gone)) { "Gone beats moving inside the window, once moving has finished" }
    clock.now += HapticPalette.of(Feel.Gone).millis
    check(!gate.admit(Feel.No)) { "No doesn't beat gone: they are equal" }
    check(!gate.admit(Feel.Done)) { "A click doesn't beat gone" }

    val steps = HapticGate { clock.now }
    clock.now += 1000
    check(steps.admit(Feel.Step)) { "A step plays" }
    clock.now += HapticPalette.of(Feel.Step).millis
    check(steps.admit(Feel.Done)) { "A click beats a step" }
}

private fun nothingCutsAPlayingFeel() {
    val clock = Clock()
    val gate = HapticGate { clock.now }
    check(gate.admit(Feel.Locked)) { "A double click plays" }
    clock.now += HapticPalette.of(Feel.Locked).millis - 1
    check(!gate.admit(Feel.Gone)) { "Gone doesn't cut a double click that is still playing" }
    clock.now += 1
    check(gate.admit(Feel.Gone)) { "Once it has finished, gone plays inside the window" }
    // A long feel is busy past the window: nothing plays until it ends.
    clock.now += 1000
    check(gate.admit(Feel.No)) { "No plays" }
    clock.now += HapticGate.WINDOW_MILLIS + 10
    check(HapticPalette.of(Feel.No).millis > HapticGate.WINDOW_MILLIS + 10) { "No lasts longer than the window" }
    check(!gate.admit(Feel.Moving)) { "Nothing cuts no, even after the window" }
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

/** Steering: a space or a line end is a step, every other character is moving. */
private fun steeringFeelsSpacesAndLetters() {
    val steps = CursorSteps()
    check(steps.step(' ') == Haptic.CursorSpace) { "A space is a step" }
    check(steps.step('\n') == Haptic.CursorSpace) { "A line end is a step" }
    check(steps.step('a') == Haptic.CursorLetter) { "A letter is moving" }
    check(steps.step('.') == Haptic.CursorLetter) { "Punctuation is a letter's feel: no feel of its own" }
}

private fun theEndsOfTheTextPlayOncePerHit() {
    val steps = CursorSteps()
    check(steps.step('a') == Haptic.CursorLetter) { "A letter on the way" }
    check(steps.step(null) == Haptic.CursorEnd) { "The end of the text is gone" }
    check(steps.step(null) == null) { "Pushing on at the end plays nothing more" }
    check(steps.step(null) == null) { "Still nothing" }
    check(steps.step('b') == Haptic.CursorLetter) { "Moving away is a letter again" }
    check(steps.step(null) == Haptic.CursorEnd) { "Coming back to an end plays again" }
    steps.reset()
    check(steps.step(null) == Haptic.CursorEnd) { "A new steer that starts at an end plays it" }
}

private fun theCelebrationRisesIntoAClick() {
    val beats = HapticPalette.celebration
    check(beats.map { it.feel } == listOf(Feel.Step, Feel.Step, Feel.Step, Feel.Done)) { "Three ticks, then a click: $beats" }
    check(beats.zipWithNext().all { (a, b) -> a.atMillis < b.atMillis && a.scale < b.scale }) { "It rises: $beats" }
    check(beats.first().atMillis == 0L) { "It starts at once" }
    check(HapticPalette.celebrationMillis < 400) { "It stays short: ${HapticPalette.celebrationMillis} ms" }
}
