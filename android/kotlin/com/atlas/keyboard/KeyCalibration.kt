package com.atlas.keyboard

import android.content.Context
import android.content.SharedPreferences

/**
 * The stored [KeyAlignment]: where Q's left edge and P's right edge sit, which the Key calibration screen in TitanKeys Debug sets.
 * Only that screen writes it, so in every other build it stays at the defaults, the measured keyboard.
 */
object KeyCalibration {
    private const val FILE = "key_calibration"
    const val LEFT = "left_edge"
    const val RIGHT = "right_edge"
    /** What the screen stored before it had two edges: a scale, a shift and a shift for each row. They mean nothing now. */
    private val OLD = listOf("scale", "offset", "row0", "row1", "row2", "row3")

    private var prefs: SharedPreferences? = null
    /** Kept here, as SharedPreferences holds its listeners only weakly. */
    private val changed = SharedPreferences.OnSharedPreferenceChangeListener { stored, _ -> KeyAlignment.current = read(stored) }

    /** The alignment now, read once and then kept up to date as the screen changes it. */
    @Synchronized
    fun load(context: Context): KeyAlignment {
        if (prefs == null) {
            val stored = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            // The old values measured a different drawing, so they go, and the keys start from the measured layout.
            if (OLD.any(stored::contains)) stored.edit().apply { OLD.forEach(::remove) }.apply()
            stored.registerOnSharedPreferenceChangeListener(changed)
            KeyAlignment.current = read(stored)
            prefs = stored
        }
        return KeyAlignment.current
    }

    fun save(context: Context, alignment: KeyAlignment) {
        load(context)
        prefs!!.edit().putFloat(LEFT, alignment.left).putFloat(RIGHT, alignment.right).apply()
        KeyAlignment.current = alignment
    }

    fun reset(context: Context) {
        load(context)
        prefs!!.edit().clear().apply()
        KeyAlignment.current = KeyAlignment.DEFAULT
    }

    private fun read(stored: SharedPreferences) =
        KeyAlignment(stored.getFloat(LEFT, KeyAlignment.DEFAULT_LEFT), stored.getFloat(RIGHT, KeyAlignment.DEFAULT_RIGHT))

    /** The keys on a screen [width] pixels wide, as calibrated now. */
    fun geometry(context: Context, width: Float) = KeyGeometry(width, load(context))
}
