package com.atlas.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.RectF

/**
 * The stored [KeyAlignment], which the Key calibration screen in TitanKeys Debug sets. Only that screen writes it, so in every other
 * build it stays at the defaults and nothing moves.
 */
object KeyCalibration {
    private const val FILE = "key_calibration"
    const val SCALE = "scale"
    const val OFFSET = "offset"
    /** The offset of each row, from the top row down: row0 to row3. */
    fun rowKey(row: Int) = "row$row"

    private var prefs: SharedPreferences? = null
    /** Kept here, as SharedPreferences holds its listeners only weakly. */
    private val changed = SharedPreferences.OnSharedPreferenceChangeListener { stored, _ -> KeyAlignment.current = read(stored) }

    /** The alignment now, read once and then kept up to date as the screen changes it. */
    @Synchronized
    fun load(context: Context): KeyAlignment {
        if (prefs == null) {
            val stored = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            stored.registerOnSharedPreferenceChangeListener(changed)
            KeyAlignment.current = read(stored)
            prefs = stored
        }
        return KeyAlignment.current
    }

    fun save(context: Context, alignment: KeyAlignment) {
        load(context)
        prefs!!.edit().apply {
            putFloat(SCALE, alignment.scale)
            putFloat(OFFSET, alignment.offset)
            for (row in 0 until KeyAlignment.ROWS) putFloat(rowKey(row), alignment.rowOffsets[row])
        }.apply()
        KeyAlignment.current = alignment
    }

    fun reset(context: Context) {
        load(context)
        prefs!!.edit().clear().apply()
        KeyAlignment.current = KeyAlignment.DEFAULT
    }

    private fun read(stored: SharedPreferences) = KeyAlignment(
        stored.getFloat(SCALE, 1f),
        stored.getFloat(OFFSET, 0f),
        FloatArray(KeyAlignment.ROWS) { stored.getFloat(rowKey(it), 0f) },
    )

    /**
     * Moves the boxes of a drawing [width] pixels wide that has a box for each key of [KeyAlignment.keys], in that order, to the
     * real keys. With the defaults it leaves them as they are.
     */
    fun align(context: Context, boxes: List<RectF>, width: Float) {
        val alignment = load(context)
        if (alignment.identity) return
        for ((index, key) in KeyAlignment.keys.withIndex()) {
            val box = boxes.getOrNull(index) ?: return
            val span = alignment.span(key.left, key.right, key.row)
            box.left = span.left * width
            box.right = span.right * width
        }
    }
}
