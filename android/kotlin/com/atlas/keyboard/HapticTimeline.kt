package com.atlas.keyboard

/**
 * The motor on from [fromMillis] to [toMillis]. [ours] is a buzz whose length we set. Otherwise it is one of the phone's own
 * effects, whose length is fixed. [dropped] is a play the gate didn't let through, so the motor stayed off.
 */
data class Buzz(val fromMillis: Long, val toMillis: Long, val ours: Boolean, val dropped: Boolean = false)

/** One play in a run: [haptic], [atMillis] after the run starts. */
data class RunBeat(val atMillis: Long, val haptic: Haptic)

/**
 * The debug Haptics lab's timeline: when the motor is on for each feel, and for a fast run. It has no Android parts, so the
 * checks can run it.
 */
object HapticTimeline {
    /**
     * How long the Titan's own effects run, from its vibrator log. Its click is Done, its heavy click Gone, and its double
     * click Locked.
     */
    const val CLICK_MILLIS = 64L
    const val HEAVY_CLICK_MILLIS = 98L
    const val DOUBLE_CLICK_MILLIS = 105L

    /** A feel's row runs this long. No's third pulse ends at 136 ms, so it still fits. */
    const val FEEL_SPAN_MILLIS = 140L
    const val FEEL_GRID_MILLIS = 20L
    const val RUN_SPAN_MILLIS = 400L
    const val RUN_GRID_MILLIS = 50L

    /** The feels in the graph's order, each with a word on where you feel it. */
    val feels = listOf(
        Feel.Moving to "swipe keys, cursor letters",
        Feel.Step to "spaces, bar taps, autocorrect",
        Feel.Done to "click: picked, pasted, saved",
        Feel.Gone to "heavy click: deleted, forgotten",
        Feel.Locked to "double click: Shift lock, pin",
        Feel.No to "three short pulses: can't",
    )

    /** The haptic a tap on a feel's row plays. */
    fun sample(feel: Feel) = when (feel) {
        Feel.Moving -> Haptic.SwipeKey
        Feel.Step -> Haptic.CursorSpace
        Feel.Done -> Haptic.Steer
        Feel.Gone -> Haptic.DeleteFlick
        Feel.Locked -> Haptic.ModifierLocked
        Feel.No -> Haptic.DeleteNothing
    }

    /** When the motor is on for [feel], starting at [atMillis], with the swipe tick and a step at these lengths. */
    fun buzzes(feel: Feel, movingMillis: Long, stepMillis: Long, atMillis: Long = 0, dropped: Boolean = false): List<Buzz> {
        val pattern = HapticPalette.of(feel, movingMillis, stepMillis)
        pattern.effect?.let { return listOf(Buzz(atMillis, atMillis + effectMillis(it), ours = false, dropped = dropped)) }
        // The timings take turns: a buzz, a gap, a buzz.
        val buzzes = ArrayList<Buzz>()
        var at = atMillis
        for ((index, millis) in pattern.fallback.timings.withIndex()) {
            if (index % 2 == 0) buzzes += Buzz(at, at + millis, ours = true, dropped = dropped)
            at += millis
        }
        return buzzes
    }

    /** How long one of the phone's own effects runs on the Titan. */
    fun effectMillis(effect: Int) = when (effect) {
        HapticPalette.EFFECT_CLICK -> CLICK_MILLIS
        HapticPalette.EFFECT_HEAVY_CLICK -> HEAVY_CLICK_MILLIS
        HapticPalette.EFFECT_DOUBLE_CLICK -> DOUBLE_CLICK_MILLIS
        else -> throw IllegalArgumentException("The lab hasn't measured effect $effect")
    }

    /** A run plays this many, this far apart, as the lab always has. */
    const val RUN = 10
    const val RUN_MILLIS = 40L

    /** A fast swipe: a key crossed every [RUN_MILLIS]. */
    val swipeRun = List(RUN) { RunBeat(it * RUN_MILLIS, Haptic.SwipeKey) }

    /** The text that fast steering crosses, one character every [RUN_MILLIS]. */
    const val STEERING_TEXT = "the cat is"

    /** Fast steering over [STEERING_TEXT]: letters are moving and spaces are a step, as [CursorSteps] decides. */
    val steeringRun: List<RunBeat> = CursorSteps().let { steps ->
        STEERING_TEXT.mapIndexed { index, letter -> RunBeat(index * RUN_MILLIS, requireNotNull(steps.step(letter))) }
    }

    /**
     * The ticks [run] would play through a fresh gate, each marked dropped if the gate wouldn't let it through. The lab draws
     * these before a run plays, and then what really played.
     */
    fun ticks(run: List<RunBeat>, movingMillis: Long, stepMillis: Long): List<Buzz> {
        var now = 0L
        val gate = HapticGate { now }
        return run.map { beat ->
            now = beat.atMillis
            val feel = beat.haptic.feel
            val admitted = gate.admit(feel, HapticPalette.of(feel, movingMillis, stepMillis).millis)
            tick(beat.haptic, beat.atMillis, movingMillis, stepMillis, admitted)
        }
    }

    /** One tick of a run: [haptic] at [atMillis], hollow if it wasn't [admitted]. Every run is moving and steps, one buzz each. */
    fun tick(haptic: Haptic, atMillis: Long, movingMillis: Long, stepMillis: Long, admitted: Boolean) =
        buzzes(haptic.feel, movingMillis, stepMillis, atMillis, dropped = !admitted).first()
}
