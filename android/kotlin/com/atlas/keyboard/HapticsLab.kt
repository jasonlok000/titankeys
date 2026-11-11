package com.atlas.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.io.File

/**
 * The Haptics lab in TitanKeys Debug's Settings. It plays each haptic as the keyboard does, tries the lengths of the swipe tick
 * and a step in its graph ([HapticsLabActivity]), and keeps the owner's comments in a file that adb can pull. Only a package
 * that ends in ".debug" has it.
 */
class HapticsLab(private val context: Context) {
    // Settings has no keyboard to borrow from, so the lab has its own Haptics, with its own gate.
    private val haptics = Haptics(context)
    private val main = Handler(Looper.getMainLooper())

    /** Plays [id] through the switches and the gate, as the keyboard does. The guide's celebration plays as the guide does. */
    fun play(id: String) {
        val haptic = requireNotNull(Haptic.of(id)) { "Unknown haptic: $id" }
        if (haptic == Haptic.GuideCelebration) haptics.celebrate() else haptics.play(haptic)
    }

    /** Plays [haptic] through the switches and the gate, and says whether it played. */
    fun play(haptic: Haptic) = haptics.play(haptic)

    /**
     * Plays [run], each beat at its time after [start] (an uptime), like a fast swipe or steer. The gate still decides which ones
     * play. [played] hears each beat: its index, when it really played after [start], and whether the gate let it through. A
     * beat at 0 plays now, in this call, so the graph's playhead starts with it.
     */
    fun playRun(run: List<RunBeat>, start: Long, played: (index: Int, atMillis: Long, admitted: Boolean) -> Unit) {
        stop()
        for ((index, beat) in run.withIndex()) {
            val play = Runnable { played(index, SystemClock.uptimeMillis() - start, haptics.play(beat.haptic)) }
            if (beat.atMillis == 0L) play.run() else main.postAtTime(play, start + beat.atMillis)
        }
    }

    fun stop() = main.removeCallbacksAndMessages(null)

    /** Tries a length for moving or a step, in ms. */
    fun length(feel: Feel, millis: Long) = when (feel) {
        Feel.Moving -> Haptics.tryMovingMillis(context, millis)
        Feel.Step -> Haptics.tryStepMillis(context, millis)
        else -> throw IllegalArgumentException("Only moving and a step have a length to try, not $feel")
    }

    /** Opens the graph, with the sliders, the timeline and the runs. [labels] are Settings' names for the haptics, as JSON. */
    fun showGraph(labels: String) = context.startActivity(HapticsLabActivity.intent(context, labels))

    /** The lengths in use now, and each haptic's feel and what it plays with them. */
    fun state(): JSONObject {
        val moving = Haptics.movingMillis(context)
        val step = Haptics.stepMillis(context)
        val haptics = JSONObject()
        for (haptic in Haptic.entries)
            haptics.put(haptic.id, JSONObject().put("feel", haptic.feel.name).put("plays", HapticPalette.describe(haptic, moving, step)))
        return JSONObject().put("movingMillis", moving).put("stepMillis", step).put("haptics", haptics)
    }

    /** The saved comments file, or {} if there is none yet. */
    fun comments(): String = file().takeIf { it.exists() }?.readText() ?: "{}"

    /** Replaces the comments file with [text]. It goes to a new file first, so a write that fails never leaves half a file. */
    fun saveComments(text: String) {
        JSONObject(text)
        val file = file()
        val written = File(file.parentFile, "${file.name}.new")
        written.writeText(text)
        check(written.renameTo(file)) { "Couldn't save the comments." }
    }

    /** /sdcard/Android/data/<package>/files/haptics-lab/comments.json, which adb can pull. */
    private fun file() = File(requireNotNull(context.getExternalFilesDir("haptics-lab")) { "No storage for the comments." }, "comments.json")

    companion object {
        fun available(context: Context) = context.packageName.endsWith(".debug")
    }
}
