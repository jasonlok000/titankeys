package com.atlas.keyboard

import android.content.Context
import android.graphics.Canvas
import android.widget.TextView

/**
 * One line of text that draws the middle of its small letters on a [line], in px under its own top, at whatever size it takes: it
 * may shrink to fit and still sit on the bar's line ([StripFit.LINE_DP]). Only the text and its icons move as they're drawn; the
 * view's box, its background and its touch area stay where Android lays them out. Its tails never reach under [floor], in px
 * under its top: it rises just enough. With no line it's a plain TextView.
 */
internal open class LineText(context: Context) : TextView(context) {
    var line = Float.NaN
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    var floor = Float.MAX_VALUE
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** How far the text is drawn under where Android put it, in whole px, so that its small letters sit on [line]. */
    fun shift(): Float {
        if (line.isNaN() || layout == null) return 0f
        return Math.round(StripFit.baseline(line, textSize, floor) - baseline).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        val shift = shift()
        if (shift == 0f) return super.onDraw(canvas)
        canvas.save()
        canvas.translate(0f, shift)
        super.onDraw(canvas)
        canvas.restore()
    }
}
